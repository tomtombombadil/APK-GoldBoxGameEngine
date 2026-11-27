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
 * PDF page about one and a half times as wide as it is tall - is shown as its two pages; sideways two pages side by
 * side as an open book, the cover alone; a scan's cover, front and back as one page, put right (makeBook).
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

    // ---- The book: its pages in reading order, and the screens they're shown on ----
    //
    // A book page: {PDF page, part: -1 the whole page, 0 its left half, 1 its right half, alone: 1 shown alone}.
    // A PDF page 1.2 - 1.7 times as wide as tall is a scanned spread: its two halves. A first page that is a
    // spread is the cover, front and back, scanned open (Tom, 2026-11-27): its right half - the front - first, its
    // left half - the back - last, each alone.
    private final List<int[]> book = new ArrayList<>();
    // A screen: the book pages it shows - one upright; sideways two, as an open book (Tom: "in landscape mode we
    // should display two pages side by side"), the cover alone, a wide page alone.
    private final List<int[]> screens = new ArrayList<>();
    private boolean twoUp;
    private float[] aspects = new float[0];   // (each PDF page's width / height)

    private static boolean spread(float aspect) { return aspect > 1.2f && aspect < 1.7f; }

    private void makeBook() {
        book.clear();
        final int n = aspects.length;
        final boolean cover = n > 1 && spread(aspects[0]);
        if (cover) book.add(new int[] {0, 1, 1});
        for (int i = cover ? 1 : 0; i < n; ++i) {
            if (spread(aspects[i])) {
                book.add(new int[] {i, 0, 0});
                book.add(new int[] {i, 1, 0});
            } else {
                // (the first page, a cover; a page wider than tall - a map, a table - alone)
                book.add(new int[] {i, -1, (i == 0 || aspects[i] > 1f) ? 1 : 0});
            }
        }
        if (cover) book.add(new int[] {0, 0, 1});
    }

    private void makeScreens() {
        screens.clear();
        for (int b = 0; b < book.size(); ) {
            final int[] p = book.get(b);
            if (!twoUp || p[2] == 1 || b + 1 >= book.size() || book.get(b + 1)[2] == 1) {
                screens.add(new int[] {b});
                b += 1;
            } else {
                screens.add(new int[] {b, b + 1});
                b += 2;
            }
        }
    }

    private int screenOf(int bookPage) {
        for (int i = 0; i < screens.size(); ++i)
            for (int b : screens.get(i)) if (b == bookPage) return i;
        return 0;
    }

    // ---- The pages in memory (Tom: "there should be plenty of memory to load the whole PDF into memory for
    // quick paging"): each PDF page rendered once, whole, kept while there's room; the screens either side
    // rendered ahead while one is read ----
    private final android.util.LruCache<Integer, Bitmap> cache = new android.util.LruCache<Integer, Bitmap>(budget()) {
        @Override
        protected int sizeOf(Integer k, Bitmap b) { return Math.max(1, b.getByteCount() / 1024); }
    };
    private float renderH;                      // (a page's height in pixels: 1.5 times the room's)

    private static int budget() {
        // (kilobytes: Android 8+ keeps bitmaps outside the app's heap - a generous share there; before, a quarter
        // of the heap)
        final long heap = Runtime.getRuntime().maxMemory() / 1024;
        return (int) (android.os.Build.VERSION.SDK_INT >= 26 ? Math.max(heap / 2, 256 * 1024) : heap / 4);
    }

    /** The PDF page, rendered whole (on the worker). */
    private Bitmap page(int i) {
        Bitmap b = cache.get(i);
        if (b != null) return b;
        final PdfRenderer.Page p = renderer.openPage(i);
        try {
            final float pw = p.getWidth(), ph = p.getHeight();
            float s = renderH / ph;
            if (pw * s * ph * s > 16e6f) s = (float) Math.sqrt(16e6f / (pw * ph));
            b = Bitmap.createBitmap(Math.max(1, Math.round(pw * s)), Math.max(1, Math.round(ph * s)), Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            final Matrix m = new Matrix();
            m.postScale(s, s);
            p.render(b, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        } finally {
            p.close();
        }
        cache.put(i, b);
        return b;
    }

    /** A screen's picture: its page, or its two side by side (on the worker; a copy - the cache's stay its own). */
    private Bitmap compose(int[] screen) {
        final Bitmap[] src = new Bitmap[screen.length];
        final android.graphics.Rect[] part = new android.graphics.Rect[screen.length];
        int w = 0, h = 0;
        for (int k = 0; k < screen.length; ++k) {
            final int[] bp = book.get(screen[k]);
            src[k] = page(bp[0]);
            final int sw = src[k].getWidth(), sh = src[k].getHeight();
            part[k] = bp[1] < 0 ? new android.graphics.Rect(0, 0, sw, sh)
                    : bp[1] == 0 ? new android.graphics.Rect(0, 0, sw / 2, sh) : new android.graphics.Rect(sw / 2, 0, sw, sh);
            h = Math.max(h, sh);
        }
        // (each page at the screen's height)
        final int[] dw = new int[screen.length];
        for (int k = 0; k < screen.length; ++k) {
            dw[k] = Math.round(part[k].width() * (float) h / part[k].height());
            w += dw[k];
        }
        final Bitmap out = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        out.eraseColor(Color.WHITE);
        final android.graphics.Canvas c = new android.graphics.Canvas(out);
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        int x = 0;
        for (int k = 0; k < screen.length; ++k) {
            c.drawBitmap(src[k], part[k], new android.graphics.Rect(x, 0, x + dw[k], h), paint);
            x += dw[k];
        }
        if (screen.length == 2) {
            // (the book's gutter: a faint fold between the pages)
            final android.graphics.Paint fold = new android.graphics.Paint();
            fold.setColor(0x30000000);
            c.drawRect(dw[0] - 1, 0, dw[0] + 1, h, fold);
        }
        return out;
    }

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
            float[] a = null;
            try {
                fd = ParcelFileDescriptor.open(new File(file), ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(fd);
                a = new float[renderer.getPageCount()];
                for (int i = 0; i < a.length; ++i) {
                    final PdfRenderer.Page p = renderer.openPage(i);
                    a[i] = (float) p.getWidth() / Math.max(1, p.getHeight());
                    p.close();
                }
            } catch (Exception | OutOfMemoryError e) {
                a = null;
            }
            final float[] got = a;
            ui.post(() -> {
                if (gen != generation) return;
                if (got == null || got.length == 0) {
                    busy.setVisibility(GONE);
                    count.setText(got == null ? "This PDF can't be opened here." : "No pages");
                    return;
                }
                aspects = got;
                makeBook();
                layoutBook(Math.max(0, Math.min(book.size() - 1, prefs.getInt(path + "#b", 0))));
            });
        });
    }

    /** The screens for the room it has now (sideways two pages, upright one), at the book page given. */
    private void layoutBook(int bookPage) {
        final int W = Math.max(1, body.getWidth() > 0 ? body.getWidth() : getWidth());
        final int H = Math.max(1, body.getHeight() > 0 ? body.getHeight() : getHeight());
        twoUp = W > H;
        final float h = 1.5f * H;
        if (Math.abs(h - renderH) > 1f) {
            // (another size: the pages rendered again for it)
            renderH = h;
            final int gen = generation;
            worker.execute(() -> { if (gen == generation) cache.evictAll(); });
        }
        makeScreens();
        show(screenOf(bookPage));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // (turned round: one page or two)
        if (up && !screens.isEmpty() && at >= 0 && (w > 0 && h > 0)) {
            final int first = screens.get(Math.min(at, screens.size() - 1))[0];
            post(() -> { if (up) layoutBook(first); });
        }
    }

    /** Closes it (the Journal list under it again). */
    void close() {
        ++generation;
        if (up && at >= 0 && at < screens.size()) prefs.edit().putInt(path + "#b", screens.get(at)[0]).apply();
        up = false;
        setVisibility(GONE);
        body.removeAllViews();
        zoom = null;
        if (shown != null) shown.recycle();
        shown = null;
        book.clear();
        screens.clear();
        at = -1;
        renderH = 0;
        worker.execute(() -> {
            cache.evictAll();
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
        if (screens.isEmpty()) return;
        final int to = at + dir;
        if (to < 0 || to >= screens.size()) return;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        show(to);
    }

    private String pages(int index) {
        final int[] sc = screens.get(index);
        final int a = sc[0] + 1;
        return (sc.length == 2 ? a + "-" + (a + 1) : String.valueOf(a)) + " / " + book.size();
    }

    private void show(int index) {
        at = index;
        count.setText(pages(index));
        prev.setVisibility(index > 0 ? VISIBLE : INVISIBLE);
        next.setVisibility(index + 1 < screens.size() ? VISIBLE : INVISIBLE);
        final int[] sc = screens.get(index);
        boolean ready = true;
        for (int b : sc) ready &= cache.get(book.get(b)[0]) != null;
        if (!ready) busy.setVisibility(VISIBLE);
        final int gen = ++generation;
        worker.execute(() -> {
            if (gen != generation) return;      // (turned on already: this one's never seen)
            Bitmap b = null;
            try {
                b = compose(sc);
            } catch (Exception | OutOfMemoryError e) {
                if (b != null) b.recycle();
                b = null;
                cache.evictAll();
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
                    count.setText(pages(index) + " - this page can't be shown");
                    return;
                }
                zoom = new JournalView.ZoomImage(getContext(), done);
                zoom.setContentDescription("Page " + pages(index) + ": pinch to zoom, swipe for the next page");
                zoom.pager = this::turn;
                body.addView(zoom, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            });
            // (the next two screens and the one before, rendered ahead - unless turned on meanwhile)
            for (int d : new int[] {1, 2, -1}) {
                final int j = index + d;
                if (j < 0 || j >= screens.size()) continue;
                for (int bp : screens.get(j)) {
                    if (gen != generation) return;
                    try {
                        page(book.get(bp)[0]);
                    } catch (Exception | OutOfMemoryError e) {
                        return;
                    }
                }
            }
        });
    }

    /** A controller's keys (and Back): its own while it is up. Back and B close it when let go - the key's
     * release is its own too (it had reached the game and taken the menu back past the Journal list). */
    boolean key(KeyEvent e) {
        if (!up) return false;
        final boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (!down && !e.isCanceled()) close();
                return true;
            default:
                break;
        }
        if (!down) return true;
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
            default:
                return true;
        }
    }
}
