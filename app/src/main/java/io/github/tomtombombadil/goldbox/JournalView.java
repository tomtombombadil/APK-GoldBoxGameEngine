package io.github.tomtombombadil.goldbox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A journal entry, Android's own (Tom, 2026-11-25 afternoon - part of the glow-up): the game's "X Width  B Back"
 * line and its entry number / % line are gone. A text entry: Android's type (Display › Journal Font) on a page in a
 * scrolling pop-up. A picture entry: an image viewer - it opens fitted whole; pinch to zoom, drag to pan, double-tap
 * to zoom in and back to fit. X (fit / the width) and B (close) as round buttons, shown only with a controller.
 *
 * The front end's channel 5: "journal\t<title>\t<font>\n", then a line a piece - "T\t<text, line breaks as \x1f>"
 * or "P\t<path>\t<w>\t<h>" (a picture it saved); "" closes. Closing here: nativeMenu op 15 (its B).
 */
final class JournalView extends FrameLayout {

    static final int OP_CLOSE = 15;
    private static final int PAPER = 0xFFF3EAD3, INK = 0xFF2A2116;
    // Display › Journal Font: Journal, Book, Serif, Plain (assets/fonts; Plain is Android's own Roboto)
    private static final String[] FONTS = {"fonts/Fondamento-Italic.ttf", "fonts/Alegreya-Italic.ttf",
            "fonts/LiberationSerif-Regular.ttf", null};

    private final float dp;
    private final TextView title;
    private final FrameLayout body;
    private final LinearLayout hints;
    private boolean up, controller;
    private String last = "";
    private ScrollView scroll;          // a text entry (or mixed): its scroller
    private ZoomImage zoom;             // a picture entry: its viewer (or a picture opened from a mixed entry)
    private boolean zoomOverScroll;     // (that picture opened over the scroller: B / Back go back to it)
    private final List<Bitmap> bitmaps = new ArrayList<>();

    JournalView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setVisibility(GONE);
        setBackgroundColor(0xFF060A18);
        setClickable(true);             // (the game under it gets nothing)

        final LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The bar: the entry's name, the close button
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
        final TextView close = new TextView(c);
        close.setText("✕");
        close.setTextColor(0xFFE8B840);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close the journal entry");
        close.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            back();
        });
        bar.addView(close, new LinearLayout.LayoutParams(px(48), px(48)));
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        body = new FrameLayout(c);
        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // X and B, round - a controller's only
        hints = new LinearLayout(c);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        hints.setGravity(Gravity.CENTER_VERTICAL);
        hints.setPadding(px(12), px(8), px(12), px(8));
        hints.setBackground(round(0xE00B1024, px(24)));
        final LayoutParams hl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        hl.setMargins(px(16), px(16), px(16), px(16));
        addView(hints, hl);
        hints.setVisibility(GONE);
    }

    private int px(float v) { return Math.round(v * dp); }

    private static GradientDrawable round(int colour, float r) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(r);
        return d;
    }

    boolean isUp() { return up; }

    /** A controller in use (its X / B shown) or a touch. */
    void controller(boolean on) {
        controller = on;
        showHints();
    }

    private void showHints() {
        hints.removeAllViews();
        if (!up || !controller) {
            hints.setVisibility(GONE);
            return;
        }
        if (zoom != null) hint("X", zoomFitted() ? "Width" : "Fit");
        hint("B", zoomOverScroll ? "Back" : "Close");
        hints.setVisibility(VISIBLE);
    }

    private void hint(String key, String words) {
        final TextView k = new TextView(getContext());
        k.setText(key);
        k.setTextColor(0xFF0B1024);
        k.setTypeface(Typeface.DEFAULT_BOLD);
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        k.setGravity(Gravity.CENTER);
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0xFFE8B840);
        k.setBackground(d);
        final LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(30), px(30));
        kl.leftMargin = hints.getChildCount() > 0 ? px(16) : 0;
        hints.addView(k, kl);
        final TextView w = new TextView(getContext());
        w.setText(words);
        w.setTextColor(0xFFF0E6C8);
        w.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        w.setPadding(px(8), 0, 0, 0);
        hints.addView(w);
    }

    /** The front end's text: an entry, or "" (closed there). */
    void update(byte[] t) {
        final String s = new String(t, StandardCharsets.UTF_8);
        if (s.isEmpty()) {
            hide();
            return;
        }
        if (s.equals(last) && up) return;
        last = s;
        clear();
        final String[] lines = s.split("\n");
        final String[] head = lines[0].split("\t", -1);
        title.setText(head.length > 1 ? head[1] : "Journal");
        int font = 0;
        try {
            if (head.length > 2) font = Integer.parseInt(head[2].trim());
        } catch (NumberFormatException ignored) {
            font = 0;
        }
        final Typeface face = typeface(font);
        int texts = 0;
        final List<String[]> parts = new ArrayList<>();
        for (int i = 1; i < lines.length; ++i) {
            final String[] f = lines[i].split("\t", -1);
            if (f.length >= 2 && f[0].equals("T")) {
                parts.add(f);
                ++texts;
            } else if (f.length >= 4 && f[0].equals("P")) {
                parts.add(f);
            }
        }
        if (texts == 0 && parts.size() == 1) {
            // (one picture: the viewer, fitted whole)
            final Bitmap b = load(parts.get(0)[1]);
            if (b != null) {
                zoom = new ZoomImage(getContext(), b);
                body.addView(zoom, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        } else {
            // (text - and any pictures with it - on a page that scrolls; a picture's tap opens it in the viewer)
            scroll = new ScrollView(getContext());
            scroll.setFillViewport(true);
            final LinearLayout page = new LinearLayout(getContext());
            page.setOrientation(LinearLayout.VERTICAL);
            page.setBackground(round(PAPER, px(6)));
            page.setPadding(px(22), px(18), px(22), px(26));
            for (String[] f : parts) {
                if (f[0].equals("T")) {
                    final TextView tv = new TextView(getContext());
                    tv.setText(paragraphs(f[1].replace('\u001f', '\n').trim(), px(28)));
                    tv.setTextColor(INK);
                    tv.setTypeface(face);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, font == 0 ? 19 : 18);
                    tv.setLineSpacing(0, 1.2f);
                    tv.setTextIsSelectable(false);
                    page.addView(tv);
                } else {
                    final Bitmap b = load(f[1]);
                    if (b == null) continue;
                    final ImageView iv = new ImageView(getContext());
                    iv.setImageBitmap(b);
                    iv.setAdjustViewBounds(true);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setContentDescription("A picture from the journal: tap to zoom");
                    iv.setOnClickListener(v -> openPicture(b));
                    final LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    il.topMargin = il.bottomMargin = px(10);
                    page.addView(iv, il);
                }
            }
            final FrameLayout holder = new FrameLayout(getContext());
            holder.setPadding(px(12), px(12), px(12), px(12));
            // (a readable measure: no wider than about 70 characters of the type)
            final LayoutParams pl = new LayoutParams(Math.min(px(720), getResources().getDisplayMetrics().widthPixels - px(24)),
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
            holder.addView(page, pl);
            scroll.addView(holder);
            body.addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        up = true;
        setVisibility(VISIBLE);
        bringToFront();
        showHints();
    }

    /**
     * A text piece as a book sets it (Tom, 2026-11-27: "indent the paragraphs and put a slight space between
     * paragraphs"): a line a paragraph, its first line indented, half a line between paragraphs; a line of
     * capitals (the entry's title, a heading) centred, not indented. A sentence the scan broke over two lines
     * (one ending mid-sentence, the next starting in lower case) is one paragraph again.
     */
    static CharSequence paragraphs(String text, int indent) {
        final List<String> paras = new ArrayList<>();
        for (String line : text.split("\n")) {
            final String l = line.trim();
            if (l.isEmpty()) continue;
            if (!paras.isEmpty()) {
                final String prev = paras.get(paras.size() - 1);
                final char last = prev.charAt(prev.length() - 1);
                final boolean open = Character.isLetter(last) || last == ',' || last == ';' || last == '-';
                if (open && Character.isLowerCase(l.charAt(0)) && !heading(prev)) {
                    paras.set(paras.size() - 1, last == '-' ? prev + l : prev + " " + l);
                    continue;
                }
            }
            paras.add(l);
        }
        // (spans set once the text is whole: a paragraph span must end at a line's end)
        final android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        final int[] from = new int[paras.size()], to = new int[paras.size()];
        for (int i = 0; i < paras.size(); ++i) {
            from[i] = b.length();
            b.append(paras.get(i));
            if (i + 1 < paras.size()) b.append('\n');
            to[i] = b.length();
            if (i + 1 < paras.size()) b.append('\n');      // (the space between: an empty line, its type small)
        }
        for (int i = 0; i < paras.size(); ++i) {
            final String p = paras.get(i);
            if (i + 1 < paras.size())
                b.setSpan(new android.text.style.RelativeSizeSpan(0.45f), to[i], to[i] + 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (heading(p)) {
                b.setSpan(new android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
                        from[i], to[i], android.text.Spanned.SPAN_PARAGRAPH);
            } else if (i > 0 && !heading(paras.get(i - 1))) {
                // (the first paragraph, and the first after a heading, start flush, as a book's)
                b.setSpan(new android.text.style.LeadingMarginSpan.Standard(indent, 0), from[i], to[i],
                        android.text.Spanned.SPAN_PARAGRAPH);
            }
        }
        return b;
    }

    /** A heading: short, its letters capitals ("INTRODUCTION", "BJORN'S TALE"). */
    static boolean heading(String p) {
        if (p.length() > 70) return false;
        int up = 0, low = 0;
        for (int i = 0; i < p.length(); ++i) {
            final char c = p.charAt(i);
            if (Character.isUpperCase(c)) ++up;
            else if (Character.isLowerCase(c)) ++low;
        }
        return up >= 3 && low * 6 <= up;
    }

    private Typeface typeface(int font) {
        final String f = font >= 0 && font < FONTS.length ? FONTS[font] : FONTS[0];
        if (f == null) return Typeface.create("sans-serif", Typeface.NORMAL);
        try {
            return Typeface.createFromAsset(getContext().getAssets(), f);
        } catch (RuntimeException e) {
            return Typeface.SERIF;
        }
    }

    private Bitmap load(String path) {
        try {
            final BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            // (a texture's limit, and memory: at most 4096 on a side)
            while (Math.max(o.outWidth, o.outHeight) / sample > 4096) sample *= 2;
            final BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            final Bitmap b = BitmapFactory.decodeFile(path, d);
            if (b != null) bitmaps.add(b);
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private void openPicture(Bitmap b) {
        zoom = new ZoomImage(getContext(), b);
        zoom.setBackgroundColor(0xFF060A18);
        zoomOverScroll = true;
        body.addView(zoom, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        showHints();
    }

    /** Back, B or the close button: a picture opened from the page back to the page; else the entry closed. */
    void back() {
        if (zoomOverScroll && zoom != null) {
            body.removeView(zoom);
            zoom = null;
            zoomOverScroll = false;
            showHints();
            return;
        }
        hide();
        GoldBoxActivity.nativeMenu(OP_CLOSE, 0, 0);
    }

    private void hide() {
        if (!up) return;
        up = false;
        last = "";
        setVisibility(GONE);
        clear();
        showHints();
    }

    private void clear() {
        body.removeAllViews();
        scroll = null;
        zoom = null;
        zoomOverScroll = false;
        for (Bitmap b : bitmaps) b.recycle();
        bitmaps.clear();
    }

    private boolean zoomFitted() { return zoom == null || zoom.fitted(); }

    /** A controller's key while the entry is up (true: taken). */
    boolean key(KeyEvent e) {
        if (!up) return false;
        final int k = e.getKeyCode();
        final boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (k) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_ESCAPE:
                if (!down && !e.isCanceled()) back();
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (down && e.getRepeatCount() == 0 && zoom != null) {
                    zoom.toggleWidth();
                    showHints();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (down && zoom != null) zoom.zoomBy(k == KeyEvent.KEYCODE_BUTTON_R1 ? 1.25f : 0.8f);
                if (down) showHints();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    final float step = 90 * dp;
                    final float dx = k == KeyEvent.KEYCODE_DPAD_LEFT ? -step : k == KeyEvent.KEYCODE_DPAD_RIGHT ? step : 0;
                    final float dy = k == KeyEvent.KEYCODE_DPAD_UP ? -step : k == KeyEvent.KEYCODE_DPAD_DOWN ? step : 0;
                    if (zoom != null) zoom.panBy(-dx, -dy);
                    else if (scroll != null) scroll.smoothScrollBy(0, Math.round(dy * 2));
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                // (A: a page down; at the end, nothing - B closes)
                if (down && scroll != null) scroll.smoothScrollBy(0, Math.round(scroll.getHeight() * 0.85f));
                return true;
            default:
                // (the rest of a controller's buttons: nothing while the entry is up)
                return KeyEvent.isGamepadButton(k);
        }
    }

    /** A controller's stick while the entry is up: pans / scrolls. */
    boolean motion(MotionEvent e) {
        if (!up) return false;
        final float x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y);
        final float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        final float ax = Math.abs(x) > 0.2f ? x : hx, ay = Math.abs(y) > 0.2f ? y : hy;
        if (Math.abs(ax) < 0.2f && Math.abs(ay) < 0.2f) return true;
        final float step = 24 * dp;
        if (zoom != null) zoom.panBy(-ax * step, -ay * step);
        else if (scroll != null) scroll.scrollBy(0, Math.round(ay * step * 2));
        return true;
    }

    /**
     * A picture: fitted whole at first; pinch to zoom (about the fingers), drag to pan, double-tap to zoom in there
     * and again back to fit. Never smaller than fitted, at most 6x a scan pixel.
     */
    static final class ZoomImage extends View {
        private final Bitmap bmp;
        private final Matrix m = new Matrix();
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector scaler;
        private final GestureDetector gestures;
        private float fit = 1, scale = 1;
        private boolean placed;
        private android.animation.ValueAnimator anim;
        /** A page turner (PdfView): a sideways swipe while fitted whole turns the page. */
        interface Pager { void turn(int dir); }
        Pager pager;

        ZoomImage(Context c, Bitmap b) {
            super(c);
            bmp = b;
            setContentDescription("A picture from the journal: pinch to zoom, drag to move it, double-tap to zoom");
            scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    zoomAbout(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                    return true;
                }
            });
            gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    if (anim != null) anim.cancel();
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                    panBy(-dx, -dy);
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                    if (pager == null || !fitted() || Math.abs(vx) < 2 * Math.abs(vy) || Math.abs(vx) < 600) return false;
                    pager.turn(vx < 0 ? 1 : -1);
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (scale > fit * 1.05f) animateTo(fit, getWidth() / 2f, getHeight() / 2f, true);
                    else animateTo(Math.min(max(), fit * 2.5f), e.getX(), e.getY(), false);
                    return true;
                }
            });
        }

        private float max() { return Math.max(fit, 6f); }

        boolean fitted() { return scale <= fit * 1.02f; }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w <= 0 || h <= 0) return;
            final boolean wasFitted = !placed || fitted();
            fit = Math.min((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
            if (wasFitted) toFit();
            else clampPan();
            placed = true;
        }

        private void toFit() {
            scale = fit;
            m.reset();
            m.postScale(scale, scale);
            m.postTranslate((getWidth() - bmp.getWidth() * scale) / 2f, (getHeight() - bmp.getHeight() * scale) / 2f);
            invalidate();
        }

        /** X: fitted whole <-> the picture's width filling the screen (from its top). */
        void toggleWidth() {
            final float wide = Math.max(fit, (float) getWidth() / bmp.getWidth());
            if (fitted() && wide > fit * 1.02f) {
                scale = wide;
                m.reset();
                m.postScale(scale, scale);
                m.postTranslate((getWidth() - bmp.getWidth() * scale) / 2f, 0);
                clampPan();
                invalidate();
            } else {
                toFit();
            }
        }

        void zoomBy(float f) { zoomAbout(f, getWidth() / 2f, getHeight() / 2f); }

        private void zoomAbout(float f, float fx, float fy) {
            final float to = Math.max(fit, Math.min(max(), scale * f));
            f = to / scale;
            scale = to;
            m.postScale(f, f, fx, fy);
            clampPan();
            invalidate();
        }

        void panBy(float dx, float dy) {
            m.postTranslate(dx, dy);
            clampPan();
            invalidate();
        }

        /** Never past the picture's edges (smaller than the screen that way: centred). */
        private void clampPan() {
            final RectF r = new RectF(0, 0, bmp.getWidth(), bmp.getHeight());
            m.mapRect(r);
            float dx = 0, dy = 0;
            if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
            else if (r.left > 0) dx = -r.left;
            else if (r.right < getWidth()) dx = getWidth() - r.right;
            if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
            else if (r.top > 0) dy = -r.top;
            else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
            m.postTranslate(dx, dy);
        }

        private void animateTo(float to, float fx, float fy, boolean centre) {
            if (anim != null) anim.cancel();
            final float from = scale;
            anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(220);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            final float[] prev = {from};
            anim.addUpdateListener(a -> {
                final float t = (float) a.getAnimatedValue();
                final float s = from + (to - from) * t;
                final float f = s / prev[0];
                prev[0] = s;
                scale = s;
                m.postScale(f, f, fx, fy);
                clampPan();
                invalidate();
            });
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator a) {
                    if (centre) toFit();
                }
            });
            anim.start();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            scaler.onTouchEvent(e);
            gestures.onTouchEvent(e);
            if (e.getPointerCount() > 1 || scaler.isInProgress()) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!bmp.isRecycled()) c.drawBitmap(bmp, m, paint);
        }
    }
}
