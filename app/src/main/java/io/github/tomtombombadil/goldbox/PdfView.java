package io.github.tomtombombadil.goldbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A game's PDF - its journal, manual, rules, cluebook - a page at a time (Tom, 2026-11-27: "view the journal PDF
 * directly, single page at a time (not the double page layout that it defaults to)"). Opened from the Journal
 * list's Documents (the front end's android_open_pdf -> GoldBoxActivity.openPdf). A scanned book's spread - a
 * PDF page about one and a half times as wide as it is tall - is shown as its two pages, left then right.
 * Pinch to zoom, drag, double-tap (JournalView.ZoomImage); a swipe or the arrows turn the page; a controller's
 * left / right, L1 / R1 too, B or Back close it. The page last read is kept for each file.
 */
final class PdfView extends FrameLayout {

    private final float dp;
    private final TextView title, count;
    private final FrameLayout body;
    private final ProgressBar busy;
    private final TextView prev, next;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();   // (PdfRenderer: one thread)
    private final SharedPreferences prefs;

    private boolean up;
    private String path = "";
    private PdfRenderer renderer;           // (the worker's)
    private ParcelFileDescriptor fd;
    private final List<int[]> views = new ArrayList<>();    // a shown page: {PDF page, part: -1 whole, 0 left, 1 right}
    private int at = -1;
    private int generation;                 // (a newer open or turn: an older render is dropped)
    private JournalView.ZoomImage zoom;
    private Bitmap shown;

    PdfView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        prefs = c.getSharedPreferences("pdf", Context.MODE_PRIVATE);
        setVisibility(GONE);
        setBackgroundColor(0xFF060A18);
        setClickable(true);

        final LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF0B1024);
        bar.setPadding(px(20), px(6), px(8), px(6));
        title = new TextView(c);
        title.setTextColor(0xFFF0E6C8);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        count = new TextView(c);
        count.setTextColor(0xFF9AA0B8);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        count.setPadding(px(12), 0, px(8), 0);
        bar.addView(count, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView close = new TextView(c);
        close.setText("✕");
        close.setTextColor(0xFFE8B840);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close the PDF");
        close.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            close();
        });
        bar.addView(close, new LinearLayout.LayoutParams(px(48), px(48)));
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        body = new FrameLayout(c);
        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        busy = new ProgressBar(c);
        busy.setIndeterminate(true);
        addView(busy, new LayoutParams(px(40), px(40), Gravity.CENTER));
        busy.setVisibility(GONE);

        prev = arrow(c, "‹", "The page before");
        prev.setOnClickListener(v -> turn(-1));
        final LayoutParams pl = new LayoutParams(px(44), px(64), Gravity.START | Gravity.CENTER_VERTICAL);
        pl.leftMargin = px(6);
        addView(prev, pl);
        next = arrow(c, "›", "The next page");
        next.setOnClickListener(v -> turn(1));
        final LayoutParams nl = new LayoutParams(px(44), px(64), Gravity.END | Gravity.CENTER_VERTICAL);
        nl.rightMargin = px(6);
        addView(next, nl);
    }

    private TextView arrow(Context c, String s, String what) {
        final TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(0xFFE8B840);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(what);
        final GradientDrawable d = new GradientDrawable();
        d.setColor(0xB00B1024);
        d.setCornerRadius(px(12));
        t.setBackground(d);
        return t;
    }

    private int px(float v) { return Math.round(v * dp); }

    boolean isUp() { return up; }

    /** Opens the file (its device path) at the page last read in it. */
    void open(String file, String name) {
        close();
        path = file;
        title.setText(name);
        count.setText("");
        up = true;
        setVisibility(VISIBLE);
        bringToFront();
        busy.setVisibility(VISIBLE);
        final int gen = ++generation;
        worker.execute(() -> {
            final List<int[]> v = new ArrayList<>();
            String error = null;
            try {
                fd = ParcelFileDescriptor.open(new File(file), ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(fd);
                for (int i = 0; i < renderer.getPageCount(); ++i) {
                    final PdfRenderer.Page p = renderer.openPage(i);
                    final float aspect = (float) p.getWidth() / Math.max(1, p.getHeight());
                    p.close();
                    if (aspect > 1.2f && aspect < 1.7f) {
                        v.add(new int[] {i, 0});
                        v.add(new int[] {i, 1});
                    } else {
                        v.add(new int[] {i, -1});
                    }
                }
            } catch (Exception | OutOfMemoryError e) {
                error = "This PDF can't be opened here.";
            }
            final String err = error;
            ui.post(() -> {
                if (gen != generation) return;
                if (err != null || v.isEmpty()) {
                    busy.setVisibility(GONE);
                    count.setText(err != null ? err : "No pages");
                    return;
                }
                views.clear();
                views.addAll(v);
                show(Math.max(0, Math.min(views.size() - 1, prefs.getInt(path, 0))));
            });
        });
    }

    /** Closes it (the Journal list under it again). */
    void close() {
        ++generation;
        if (up && at >= 0 && !path.isEmpty()) prefs.edit().putInt(path, at).apply();
        up = false;
        setVisibility(GONE);
        body.removeAllViews();
        zoom = null;
        if (shown != null) shown.recycle();
        shown = null;
        views.clear();
        at = -1;
        worker.execute(() -> {
            try {
                if (renderer != null) renderer.close();
                if (fd != null) fd.close();
            } catch (Exception ignored) {
                // (closing)
            }
            renderer = null;
            fd = null;
        });
    }

    private void turn(int dir) {
        if (views.isEmpty()) return;
        final int to = at + dir;
        if (to < 0 || to >= views.size()) return;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        show(to);
    }

    private void show(int index) {
        at = index;
        count.setText((index + 1) + " / " + views.size());
        prev.setVisibility(index > 0 ? VISIBLE : INVISIBLE);
        next.setVisibility(index + 1 < views.size() ? VISIBLE : INVISIBLE);
        busy.setVisibility(VISIBLE);
        final int[] v = views.get(index);
        final int gen = ++generation;
        // (sharp enough to zoom: about twice the screen, at most ~12 million pixels)
        final int W = Math.max(1, body.getWidth() > 0 ? body.getWidth() : getWidth());
        final int H = Math.max(1, body.getHeight() > 0 ? body.getHeight() : getHeight());
        worker.execute(() -> {
            Bitmap b = null;
            try {
                final PdfRenderer.Page p = renderer.openPage(v[0]);
                final float pw = v[1] < 0 ? p.getWidth() : p.getWidth() / 2f, ph = p.getHeight();
                float s = 2f * Math.min(W / pw, H / ph);
                if (pw * s * ph * s > 12e6f) s = (float) Math.sqrt(12e6f / (pw * ph));
                final int bw = Math.max(1, Math.round(pw * s)), bh = Math.max(1, Math.round(ph * s));
                b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.WHITE);
                final Matrix m = new Matrix();
                m.postScale(s, s);
                if (v[1] == 1) m.postTranslate(-pw * s, 0);
                p.render(b, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                p.close();
            } catch (Exception | OutOfMemoryError e) {
                if (b != null) b.recycle();
                b = null;
            }
            final Bitmap done = b;
            ui.post(() -> {
                if (gen != generation || !up) {
                    if (done != null) done.recycle();
                    return;
                }
                busy.setVisibility(GONE);
                body.removeAllViews();
                if (shown != null) shown.recycle();
                shown = done;
                if (done == null) {
                    count.setText((index + 1) + " / " + views.size() + " - this page can't be shown");
                    return;
                }
                zoom = new JournalView.ZoomImage(getContext(), done);
                zoom.setContentDescription("Page " + (index + 1) + ": pinch to zoom, swipe for the next page");
                zoom.pager = this::turn;
                body.addView(zoom, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            });
        });
    }

    /** A controller's keys (and Back): its own while it is up. */
    boolean key(KeyEvent e) {
        if (!up) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_PAGE_UP:
                turn(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_BUTTON_A:
                turn(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (zoom != null) zoom.panBy(0, px(80));
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (zoom != null) zoom.panBy(0, -px(80));
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (zoom != null) zoom.toggleWidth();
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                close();
                return true;
            default:
                return true;
        }
    }
}
