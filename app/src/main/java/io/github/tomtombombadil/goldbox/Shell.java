package io.github.tomtombombadil.goldbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.Collections;

/**
 * The app's own controls round the game (Tom, 2026-11-24, the redesign - CLAUDE.md
 * "The redesign"): Android's views over SDL's surface. The front end draws the game
 * and the Companion's pictures in the rectangles this gives it (GoldBoxActivity.
 * nativeLayout; the front end's src/shell.*); everything else here is the app's.
 *
 * - The pad: 8 ways (the corners only in a fight), OK in its centre, Back, a pin.
 * - The Companion: the front end's pictures in the room given; a tap flips the map
 *   and the party when only one fits; a pin.
 * - Handles on the edges: left - the menu, right - the Companion, bottom - the pad.
 *   (Android's own Back / Home gestures own the edges: the handles are where a
 *   swipe is ours - setSystemGestureExclusionRects - and a tap does as well.)
 * - Defaults by the device's shape and how it is held: a phone (16:9 and wider)
 *   sideways has the game at the left and a column beside it - the Companion over
 *   the pad; upright, and a tablet upright: the game, the Companion, the pad at the
 *   foot; a tablet (near 4:3) sideways: the game alone, the rest on demand.
 * - Pinned: out until unpinned, and remembered (for this shape and way of holding).
 *   Unpinned: swiped back, or put away by a touch on the game.
 * - A controller in use: no pad (a touch brings it back).
 */
final class Shell {

    // The front end's buttons (main.cpp's Btn order)
    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3, A = 4, B = 5, START = 12;
    // What the front end has on screen (shell.h State)
    static final int PAGES = 0, TITLES = 1, PLAY = 2, FIGHT = 3;

    static final int NAVY = 0xFF0B1024, SURFACE = 0xFF141B36, KEY = 0xFF26305A, EDGE = 0xFF3A4A86,
            GOLD = 0xFFE8B840, CREAM = 0xFFF0E6C8, MUTED = 0xFF9AA3C2;

    private final GoldBoxActivity act;
    private final float dp;
    private final SharedPreferences prefs;
    final Root root;
    private final PadView pad;
    private final CompView comp;
    private final Handle hMenu, hComp, hPad;
    private final MenuDrawer drawer;

    private int state = PAGES;
    private boolean controller = false;
    private String key = "";
    private boolean compOut, padOut, compPin, padPin;
    private int compView = 1;               // 1: the map (a fight: the battlefield), 2: the party
    private final int[] sent = new int[9];
    private boolean sentAny = false;
    private boolean column = false;         // sideways: the Companion and the pad in a column beside the game

    Shell(GoldBoxActivity a) {
        act = a;
        dp = a.getResources().getDisplayMetrics().density;
        prefs = a.getSharedPreferences("shell", Context.MODE_PRIVATE);
        root = new Root(a);
        comp = new CompView(a);
        pad = new PadView(a);
        hMenu = new Handle(a, Handle.LEFT_EDGE, "MENU");
        hComp = new Handle(a, Handle.RIGHT_EDGE, "COMPANION");
        hPad = new Handle(a, Handle.BOTTOM_EDGE, "CONTROLS");
        root.addView(comp, new FrameLayout.LayoutParams(0, 0));
        root.addView(pad, new FrameLayout.LayoutParams(0, 0));
        root.addView(hMenu, new FrameLayout.LayoutParams(0, 0));
        root.addView(hComp, new FrameLayout.LayoutParams(0, 0));
        root.addView(hPad, new FrameLayout.LayoutParams(0, 0));
        drawer = new MenuDrawer(a);
        root.addView(drawer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** The front end's menu, for the drawer. */
    void menu(byte[] state) { drawer.update(state); }

    // ---- From the front end (the UI thread)

    void setState(int s) {
        state = s;
        pad.invalidate();           // (the corners: a fight's)
        relayout(true);
    }

    /** Y on a controller: the Companion out / away. */
    void toggleCompanion() {
        compOut = !compOut;
        if (compPin) prefs.edit().putBoolean("comp_" + key, compOut).apply();
        relayout(false);
    }

    /** A controller's button or stick (true) / a touch (false). */
    void controller(boolean on) {
        if (controller == on) return;
        controller = on;
        relayout(false);
    }

    // ---- The layout

    private boolean defaultOut(String k) { return !k.equals("tablet_land"); }

    private void load(String k) {
        key = k;
        final boolean d = defaultOut(k);
        compPin = prefs.getBoolean("pin_comp_" + k, d);
        padPin = prefs.getBoolean("pin_pad_" + k, d);
        compOut = compPin && prefs.getBoolean("comp_" + k, d);
        padOut = padPin && prefs.getBoolean("pad_" + k, d);
    }

    private void setCompOut(boolean on) {
        compOut = on;
        if (compPin) prefs.edit().putBoolean("comp_" + key, on).apply();
        relayout(false);
    }

    private void setPadOut(boolean on) {
        padOut = on;
        if (padPin) prefs.edit().putBoolean("pad_" + key, on).apply();
        relayout(false);
    }

    private void togglePin(boolean ofPad) {
        if (ofPad) {
            padPin = !padPin;
            prefs.edit().putBoolean("pin_pad_" + key, padPin).putBoolean("pad_" + key, padOut).apply();
        } else {
            compPin = !compPin;
            prefs.edit().putBoolean("pin_comp_" + key, compPin).putBoolean("comp_" + key, compOut).apply();
        }
        Toast.makeText(act, (ofPad ? padPin : compPin) ? "Pinned: it stays out" : "Unpinned: swipe it away, or touch the game",
                Toast.LENGTH_SHORT).show();
        pad.invalidate();
        comp.invalidate();
    }

    private boolean playing() { return state == PLAY || state == FIGHT; }

    private static void place(View v, int x, int y, int w, int h) {
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (w <= 0 || h <= 0) {
            v.setVisibility(View.GONE);
            return;
        }
        v.setVisibility(View.VISIBLE);
        if (lp.leftMargin != x || lp.topMargin != y || lp.width != w || lp.height != h) {
            lp.leftMargin = x;
            lp.topMargin = y;
            lp.width = w;
            lp.height = h;
            v.setLayoutParams(lp);
        }
    }

    /** Lays everything out for the screen as it is now; tells the front end. */
    void relayout(boolean force) {
        final int W = root.getWidth(), H = root.getHeight();
        if (W <= 0 || H <= 0) return;
        final int[] ins = act.cutoutInsets();
        final Rect area = new Rect(ins[0], ins[1], W - ins[2], H - ins[3]);
        final boolean land = W >= H;
        final boolean phone = (float) Math.max(W, H) / Math.min(W, H) >= 1.5f;
        final String k = (phone ? "phone" : "tablet") + (land ? "_land" : "_port");
        if (!k.equals(key)) load(k);

        final boolean showComp = compOut && playing();
        final boolean showPad = padOut && !controller && state != PAGES;
        column = land;

        // The pad's size: a thumb's, within the screen's means
        int btn = Math.round(Math.max(44 * dp, Math.min(64 * dp, Math.min(W, H) * 0.13f)));
        final int gap = Math.round(6 * dp), side = Math.round(56 * dp), m = Math.round(8 * dp);
        int padW = 3 * btn + 2 * gap + 2 * side, padH = 3 * btn + 2 * gap + 2 * m + Math.round(12 * dp);

        final Rect game = new Rect(area), cr = new Rect(), pr = new Rect();
        if (state == PAGES) {
            // (the front end's own pages: the whole screen)
        } else if (land) {
            if (showComp || showPad) {
                int colW = Math.max(area.width() - area.height() * 4 / 3, showPad ? padW : Math.round(180 * dp));
                if (colW > area.width() / 2) colW = area.width() / 2;
                fit43(game, area.left, area.top, area.width() - colW, area.height(), false);
                final int x0 = game.right;
                colW = area.right - x0;
                if (showPad) {
                    if (padW > colW) {      // (a narrow column: smaller keys)
                        btn = Math.max(Math.round(36 * dp), (colW - 2 * gap - 2 * side) / 3);
                        padW = 3 * btn + 2 * gap + 2 * side;
                        padH = 3 * btn + 2 * gap + 2 * m + Math.round(12 * dp);
                    }
                    final int pw = Math.min(padW, colW);
                    pr.set(x0 + (colW - pw) / 2, area.bottom - padH, x0 + (colW - pw) / 2 + pw, area.bottom);
                }
                if (showComp) cr.set(x0, area.top, area.right, showPad ? pr.top - m : area.bottom);
            } else {
                fit43(game, area.left, area.top, area.width(), area.height(), true);
            }
        } else {
            final int gh = Math.min(area.width() * 3 / 4, area.height());
            game.set(area.left, area.top, area.left + gh * 4 / 3, area.top + gh);
            if (showPad) pr.set(area.left, area.bottom - padH, area.right, area.bottom);
            if (showComp) cr.set(area.left, game.bottom + m, area.right, (showPad ? pr.top : area.bottom) - m);
            if (!showComp && !showPad) fit43(game, area.left, area.top, area.width(), area.height(), true);
        }
        if (cr.height() < 48 * dp || cr.width() < 48 * dp) cr.setEmpty();

        // The Companion: one picture (the tap flips it) beside the pad, else both
        final int view = !cr.isEmpty() && land && showPad ? compView : 0;
        comp.single = view != 0;
        pad.btn = btn;
        pad.tray = !land;
        place(comp, cr.left, cr.top, cr.width(), cr.height());
        place(pad, pr.left, pr.top, pr.width(), pr.height());

        // The handles: where a swipe is ours
        final int hw = Math.round(16 * dp), hl = Math.round(96 * dp);
        place(hMenu, area.left, area.top + (area.height() - hl) / 2, hw, hl);
        if (playing() && cr.isEmpty()) place(hComp, area.right - hw, area.top + (area.height() - hl) / 2, hw, hl);
        else place(hComp, 0, 0, 0, 0);
        if (state != PAGES && pr.isEmpty() && !controller) {
            final int bx = (land && !cr.isEmpty()) ? game.left + (game.width() - hl) / 2 : area.left + (area.width() - hl) / 2;
            place(hPad, bx, area.bottom - hw - Math.round(4 * dp), hl, hw);
        } else {
            place(hPad, 0, 0, 0, 0);
        }

        final int[] v = {game.left, game.top, game.width(), game.height(),
                cr.isEmpty() ? 0 : cr.left, cr.isEmpty() ? 0 : cr.top, cr.width(), cr.height(), view};
        boolean same = sentAny;
        for (int i = 0; i < 9 && same; ++i) same = v[i] == sent[i];
        if (same && !force) return;
        System.arraycopy(v, 0, sent, 0, 9);
        sentAny = true;
        GoldBoxActivity.nativeLayout(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
    }

    /** The largest 4:3 in w x h at (x, y): at the left and middle, or centred. */
    private static void fit43(Rect r, int x, int y, int w, int h, boolean centre) {
        int gw = w, gh = w * 3 / 4;
        if (gh > h) {
            gh = h;
            gw = h * 4 / 3;
        }
        final int ox = centre ? x + (w - gw) / 2 : x, oy = y + (h - gh) / 2;
        r.set(ox, oy, ox + gw, oy + gh);
    }

    private void buttonTap(int b) {
        GoldBoxActivity.nativeButton(b, true);
        GoldBoxActivity.nativeButton(b, false);
    }

    private static Paint paint(int colour) {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colour);
        return p;
    }

    /** A pin: filled gold when pinned, an outline when not. */
    private void drawPin(Canvas c, float cx, float cy, float r, boolean pinned) {
        final Paint bg = paint(pinned ? GOLD : KEY);
        c.drawCircle(cx, cy, r, bg);
        final Paint ink = paint(pinned ? NAVY : CREAM);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(2 * dp);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        final float u = r / 12f;
        final Path p = new Path();
        p.moveTo(cx - 3 * u, cy - 8 * u);
        p.lineTo(cx + 3 * u, cy - 8 * u);
        p.lineTo(cx + 2 * u, cy - 2 * u);
        p.lineTo(cx + 6 * u, cy + 2 * u);
        p.lineTo(cx - 6 * u, cy + 2 * u);
        p.lineTo(cx - 2 * u, cy - 2 * u);
        p.close();
        c.drawPath(p, ink);
        c.drawLine(cx, cy + 2 * u, cx, cy + 9 * u, ink);
        if (!pinned) c.drawLine(cx - 8 * u, cy - 8 * u, cx + 8 * u, cy + 8 * u, ink);
    }

    // ---- The views

    /** The whole screen over SDL's: a touch nobody here takes is the game's. */
    final class Root extends FrameLayout {
        Root(Context c) { super(c); }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            post(() -> relayout(false));
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !drawer.isOpen()) {
                controller(false);
                // (a touch on the game puts away what isn't pinned)
                final int x = (int) e.getX(), y = (int) e.getY();
                if (!inside(pad, x, y) && !inside(comp, x, y) && !inside(hMenu, x, y) && !inside(hComp, x, y)
                        && !inside(hPad, x, y)) {
                    boolean changed = false;
                    if (padOut && !padPin && pad.getVisibility() == VISIBLE) { padOut = false; changed = true; }
                    if (compOut && !compPin && comp.getVisibility() == VISIBLE) { compOut = false; changed = true; }
                    if (changed) {
                        relayout(false);
                        return true;        // (this touch only put them away)
                    }
                }
            }
            return super.dispatchTouchEvent(e);
        }
    }

    private static boolean inside(View v, int x, int y) {
        return v.getVisibility() == View.VISIBLE && x >= v.getLeft() && x < v.getRight() && y >= v.getTop()
                && y < v.getBottom();
    }

    /** A handle on an edge: a tap or a swipe inwards brings its panel out. */
    final class Handle extends View {
        static final int LEFT_EDGE = 0, RIGHT_EDGE = 1, BOTTOM_EDGE = 2;
        final int edge;
        final String label;
        float downX, downY;

        Handle(Context c, int edge, String label) {
            super(c);
            this.edge = edge;
            this.label = label;
            setContentDescription(edge == LEFT_EDGE ? "Open the menu" : edge == RIGHT_EDGE ? "Open the Companion"
                    : "Open the controls");
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (Build.VERSION.SDK_INT >= 29) setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, r - l, b - t)));
        }

        @Override
        protected void onDraw(Canvas c) {
            final float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2;
            final Paint bg = paint(0x99E8B840);      // (see-through: it lies over the game's edge)
            final RectF box = edge == LEFT_EDGE ? new RectF(-r, 0, w, h) : edge == RIGHT_EDGE ? new RectF(0, 0, w + r, h)
                    : new RectF(0, 0, w, h);
            c.drawRoundRect(box, r, r, bg);
            final Paint t = paint(NAVY);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            t.setTextSize(9 * dp);
            t.setLetterSpacing(0.08f);
            t.setTextAlign(Paint.Align.CENTER);
            c.save();
            c.translate(w / 2, h / 2);
            if (edge == LEFT_EDGE) c.rotate(-90);
            if (edge == RIGHT_EDGE) c.rotate(90);
            c.drawText(label, 0, 3.2f * dp, t);
            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    final float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    final boolean in = edge == LEFT_EDGE ? dx > 24 * dp : edge == RIGHT_EDGE ? dx < -24 * dp : dy < -24 * dp;
                    if (in) {
                        open();
                        downX = Float.NaN;
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (!Float.isNaN(downX)) open();
                    return true;
                default:
                    return true;
            }
        }

        void open() {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (edge == LEFT_EDGE) buttonTap(START);
            else if (edge == RIGHT_EDGE) setCompOut(true);
            else setPadOut(true);
        }
    }

    /**
     * Over the Companion's pictures (the front end draws them under this): its pin,
     * a tap flips the map and the party (one shown), a swipe puts it away.
     */
    final class CompView extends View {
        boolean single;
        float downX, downY;
        boolean moved;

        CompView(Context c) {
            super(c);
            setContentDescription("Companion: tap to switch between the map and the party");
        }

        private float pinR() { return 16 * dp; }

        @Override
        protected void onDraw(Canvas c) {
            final float r = pinR();
            drawPin(c, getWidth() - r - 4 * dp, r + 4 * dp, r, compPin);
            if (single) {
                final Paint t = paint(MUTED);
                t.setTextSize(11 * dp);
                t.setTextAlign(Paint.Align.CENTER);
                c.drawText(compView == 1 ? "tap: the party" : "tap: the map", getWidth() / 2f, getHeight() - 4 * dp, t);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.abs(e.getX() - downX) > 48 * dp) {
                        moved = true;
                        if (compPin) Toast.makeText(act, "The Companion is pinned - tap its pin first", Toast.LENGTH_SHORT).show();
                        else setCompOut(false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) return true;
                    final float r = pinR();
                    if (Math.hypot(e.getX() - (getWidth() - r - 4 * dp), e.getY() - (r + 4 * dp)) < r + 8 * dp) {
                        togglePin(false);
                    } else if (single) {
                        compView = compView == 1 ? 2 : 1;
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        invalidate();
                        relayout(false);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    /** The pad: 8 ways (corners in a fight), OK in the middle, Back, a pin. */
    final class PadView extends View {
        int btn = 56;
        boolean tray;               // at the foot of an upright screen (rounded at the top only)
        // pointer id -> the key it holds (-1 none, else 0..8 the grid, 9 Back, 10 the pin, 11 the surface)
        private final SparseIntArray held = new SparseIntArray();
        float downX, downY;
        int downPointer = -1;

        // The grid's keys: up-left, up, up-right, left, OK, right, down-left, down, down-right
        private final int[][] keyButtons = {{UP, LEFT}, {UP}, {UP, RIGHT}, {LEFT}, {A}, {RIGHT}, {DOWN, LEFT}, {DOWN}, {DOWN, RIGHT}};
        private final int[] keyAngle = {-45, 0, 45, -90, 0, 90, -135, 180, 135};

        PadView(Context c) {
            super(c);
            setContentDescription("Controls");
        }

        private int gap() { return Math.round(6 * dp); }
        private int gridW() { return 3 * btn + 2 * gap(); }
        private float gx() { return (getWidth() - gridW()) / 2f; }
        private float gy() { return 8 * dp + Math.round(12 * dp); }

        private boolean diagonals() { return state == FIGHT; }

        private RectF keyRect(int i) {
            final float x = gx() + (i % 3) * (btn + gap()), y = gy() + (i / 3) * (btn + gap());
            return new RectF(x, y, x + btn, y + btn);
        }

        private RectF backRect() {
            final float w = Math.min(52 * dp, gx() - 8 * dp), h = 40 * dp;
            final float cx = gx() / 2;
            return new RectF(cx - w / 2, gy(), cx + w / 2, gy() + h);
        }

        private float pinX() { return gx() + gridW() + (getWidth() - gx() - gridW()) / 2f; }
        private float pinY() { return gy() + 20 * dp; }

        private int hit(float x, float y) {
            for (int i = 0; i < 9; ++i) {
                final RectF r = keyRect(i);
                r.inset(-gap() / 2f, -gap() / 2f);
                if (r.contains(x, y)) return (i % 2 == 0 && i != 4 && !diagonals()) ? 11 : i;
            }
            final RectF b = backRect();
            b.inset(-6 * dp, -6 * dp);
            if (b.contains(x, y)) return 9;
            if (Math.hypot(x - pinX(), y - pinY()) < 26 * dp) return 10;
            return 11;
        }

        @Override
        protected void onDraw(Canvas c) {
            final float w = getWidth(), h = getHeight(), rad = 24 * dp;
            final Paint surface = paint(SURFACE);
            if (tray) {
                c.drawRoundRect(new RectF(0, 0, w, h + rad), rad, rad, surface);
            } else {
                c.drawRoundRect(new RectF(0, 0, w, h), rad, rad, surface);
            }
            // (the drag handle: swipe it away)
            final Paint grip = paint(EDGE);
            c.drawRoundRect(new RectF(w / 2 - 18 * dp, 8 * dp, w / 2 + 18 * dp, 12 * dp), 2 * dp, 2 * dp, grip);

            final Paint keyBg = paint(KEY), okBg = paint(GOLD), lit = paint(0xFF3A4680);
            final Paint ink = paint(CREAM);
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(2.4f * dp);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeJoin(Paint.Join.ROUND);
            final float kr = 12 * dp;
            for (int i = 0; i < 9; ++i) {
                final RectF r = keyRect(i);
                final boolean down = isHeld(i);
                final boolean off = i % 2 == 0 && i != 4 && !diagonals();
                if (i == 4) {
                    c.drawRoundRect(r, kr, kr, down ? paint(0xFFF5D27A) : okBg);
                    final Paint t = paint(NAVY);
                    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    t.setTextSize(15 * dp);
                    t.setTextAlign(Paint.Align.CENTER);
                    c.drawText("OK", r.centerX(), r.centerY() + 5 * dp, t);
                    continue;
                }
                keyBg.setAlpha(off ? 90 : 255);
                c.drawRoundRect(r, kr, kr, down ? lit : keyBg);
                ink.setAlpha(off ? 70 : 255);
                c.save();
                c.translate(r.centerX(), r.centerY());
                c.rotate(keyAngle[i]);
                final float u = btn / 24f * 0.9f;
                final Path p = new Path();
                p.moveTo(-6 * u, 3 * u);
                p.lineTo(0, -3 * u);
                p.lineTo(6 * u, 3 * u);
                c.drawPath(p, ink);
                c.restore();
            }
            // Back
            final RectF b = backRect();
            final Paint outline = paint(isHeld(9) ? KEY : EDGE);
            outline.setStyle(isHeld(9) ? Paint.Style.FILL : Paint.Style.STROKE);
            outline.setStrokeWidth(1.2f * dp);
            c.drawRoundRect(b, b.height() / 2, b.height() / 2, outline);
            final Paint t = paint(CREAM);
            t.setTextSize(13 * dp);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            c.drawText("Back", b.centerX(), b.centerY() + 4.5f * dp, t);
            drawPin(c, pinX(), pinY(), 20 * dp, padPin);
        }

        private boolean isHeld(int k) {
            for (int i = 0; i < held.size(); ++i)
                if (held.valueAt(i) == k) return true;
            return false;
        }

        private void press(int k, boolean down) {
            if (k < 0 || k > 9) return;
            if (k == 9) {
                GoldBoxActivity.nativeButton(B, down);
            } else {
                for (int b : keyButtons[k]) GoldBoxActivity.nativeButton(b, down);
            }
            if (down) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            final int ai = e.getActionIndex();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    final int id = e.getPointerId(ai);
                    final int k = hit(e.getX(ai), e.getY(ai));
                    held.put(id, k);
                    if (k == 11 && downPointer < 0) {
                        downPointer = id;
                        downX = e.getX(ai);
                        downY = e.getY(ai);
                    }
                    press(k, true);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); ++i) {
                        final int id = e.getPointerId(i);
                        final int was = held.get(id, -1);
                        if (id == downPointer) {
                            final float dx = e.getX(i) - downX, dy = e.getY(i) - downY;
                            final boolean away = tray ? dy > 48 * dp : dx > 48 * dp || dy > 48 * dp;
                            if (away) {
                                downPointer = -1;
                                if (padPin) Toast.makeText(act, "The controls are pinned - tap the pin first", Toast.LENGTH_SHORT).show();
                                else setPadOut(false);
                            }
                            continue;
                        }
                        if (was < 0 || was > 8) continue;       // (Back and the pin don't slide)
                        final int now = hit(e.getX(i), e.getY(i));
                        if (now != was && now <= 8) {
                            press(was, false);
                            held.put(id, now);
                            press(now, true);
                            invalidate();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    final int id = e.getPointerId(ai);
                    final int k = held.get(id, -1);
                    held.delete(id);
                    if (id == downPointer) downPointer = -1;
                    press(k, false);
                    if (k == 10) togglePin(true);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    for (int i = 0; i < held.size(); ++i) press(held.valueAt(i), false);
                    held.clear();
                    downPointer = -1;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }
}
