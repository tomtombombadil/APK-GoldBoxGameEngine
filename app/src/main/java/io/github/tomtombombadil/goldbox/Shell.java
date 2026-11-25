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
    private final ChooserView chooser;
    private final Screens.ScanView scan;
    private final Screens.NoticeView notice;

    private int state = PAGES;
    private boolean controller = false;
    private String key = "";
    private boolean compOut, padOut, compPin, padPin;
    private float padScale = 1f;            // Controls - Button Size (the front end's setting)
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
        hMenu = new Handle(a, Handle.RIGHT_EDGE, "MENU");
        hComp = new Handle(a, Handle.RIGHT_EDGE, "COMPANION");
        hPad = new Handle(a, Handle.BOTTOM_EDGE, "CONTROLS");
        root.addView(comp, new FrameLayout.LayoutParams(0, 0));
        root.addView(pad, new FrameLayout.LayoutParams(0, 0));
        root.addView(hMenu, new FrameLayout.LayoutParams(0, 0));
        root.addView(hComp, new FrameLayout.LayoutParams(0, 0));
        root.addView(hPad, new FrameLayout.LayoutParams(0, 0));
        chooser = new ChooserView(a);
        root.addView(chooser, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        scan = new Screens.ScanView(a);
        root.addView(scan, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        drawer = new MenuDrawer(a);
        root.addView(drawer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        notice = new Screens.NoticeView(a);
        root.addView(notice, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** Controls - Button Size (0 Small - 3 Huge) and Button Opacity (20 - 100 %), the front end's settings. */
    void padStyle(int size, int alpha) {
        final float[] k = {0.8f, 1f, 1.2f, 1.4f};
        padScale = k[Math.max(0, Math.min(3, size))];
        pad.setAlpha(Math.max(0.2f, Math.min(1f, alpha / 100f)));
        relayout(false);
        pad.invalidate();
    }

    /** One of the front end's own screens (its channels: 0 drawer, 1 notice, 2 chooser, 3 scan). */
    void text(int channel, byte[] t) {
        switch (channel) {
            case 0: drawer.update(t); break;
            case 1: notice.update(t); break;
            case 2: chooser.update(t); relayout(false); break;
            case 3: scan.update(t); relayout(false); break;
            default: break;
        }
    }

    /** Android's Back here leaves the app (Tom, 2026-11-24): the chooser up, nothing over it. */
    boolean backLeaves() { return chooser.isUp() && !scan.isUp() && !drawer.isOpen() && !notice.isUp(); }

    /** The app's own page is up over the game (the chooser, the scan's page): no handles. */
    private boolean page() { return chooser.isUp() || scan.isUp(); }

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

    /** This shape's and way of holding's pins and what is out; d: out by default. */
    private void load(String k, boolean d) {
        key = k;
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

    /**
     * Which hand the controls are for (Tom, 2026-11-24 - "embrace handedness", as the CYD's): right - the pad
     * and its column at the right, left - mirrored. One for every shape; the swap arrow turns it.
     */
    private boolean leftHand() { return prefs.getBoolean("hand_left", false); }

    void swapHands() {
        prefs.edit().putBoolean("hand_left", !leftHand()).apply();
        relayout(false);
        pad.invalidate();
        comp.invalidate();
    }

    /** How far in from a screen corner a small button must sit (a phone's rounded corners). */
    private int cornerPad() {
        int r = 0;
        if (Build.VERSION.SDK_INT >= 31 && root.getRootWindowInsets() != null) {
            final android.view.WindowInsets wi = root.getRootWindowInsets();
            for (int pos = 0; pos < 4; ++pos) {
                final android.view.RoundedCorner rc = wi.getRoundedCorner(pos);
                if (rc != null) r = Math.max(r, rc.getRadius());
            }
        } else {
            r = Math.round(16 * dp);
        }
        return Math.max(Math.round(6 * dp), Math.round(r * 0.45f));
    }

    /**
     * A tablet by Android's own measure - the screen's smaller side 600dp or more (Tom, 2026-11-25: a Galaxy Tab S8+,
     * 2800 x 1752 - 16:10 - was taken for a phone by its shape); a phone else. In split screen: the window's.
     */
    static boolean tablet(Context c) { return c.getResources().getConfiguration().smallestScreenWidthDp >= 600; }

    // The Companion's pictures: 208 x 200 at rows 1.2 tall (video.h kSideW, kSideH) - height / width
    private static int picH(int w) { return w * 200 * 6 / (208 * 5); }

    /** Lays everything out for the screen as it is now; tells the front end. */
    void relayout(boolean force) {
        final int W = root.getWidth(), H = root.getHeight();
        if (W <= 0 || H <= 0) return;
        final int[] ins = act.cutoutInsets();
        final Rect area = new Rect(ins[0], ins[1], W - ins[2], H - ins[3]);
        final boolean land = W >= H;
        final boolean phone = !tablet(act);
        // (a tablet near 4:3 sideways: the game maximised, the rest on demand; a wider one - 16:10 - has room
        // beside the game: the column out, as a phone's)
        final boolean roomy = (float) Math.max(W, H) / Math.min(W, H) >= 1.5f;
        final String k = (phone ? "phone" : "tablet") + (land ? "_land" : "_port");
        if (!k.equals(key)) load(k, phone || !land || roomy);
        final boolean left = leftHand();

        final boolean showComp = compOut && playing();
        final boolean showPad = padOut && !controller && state != PAGES;

        // The pad: a thumb's keys within the screen's means; beside them a column of small buttons (the pin,
        // Back, put away); no drag bar
        int btn = Math.round(Math.max(46 * dp, Math.min(68 * dp, Math.min(W, H) * 0.14f)) * padScale);
        final int gap = Math.round(6 * dp), sideW = Math.round(52 * dp), m = Math.round(8 * dp);
        int padW = 3 * btn + 2 * gap + sideW + 3 * m, padH = 3 * btn + 2 * gap + 2 * m;
        // The Companion's own strip at its outer edge: its pin, the swap arrow, the menu (and on a phone held
        // sideways the map / party words, turned)
        final int stripW = Math.round(36 * dp), band = Math.round(24 * dp);

        final Rect game = new Rect(area), cr = new Rect(), pr = new Rect();
        boolean stripLeft = false;      // (the Companion's strip at its left edge)
        if (state == PAGES) {
            // (the front end's own pages: the whole screen)
        } else if (land) {
            if (showComp || showPad) {
                // (the column as wide with the pad put away as with it out - Tom, 2026-11-25: "Making the map
                // smaller isn't a good look")
                int colW = Math.max(area.width() - area.height() * 4 / 3, padW);
                if (colW > area.width() / 2) colW = area.width() / 2;
                // (the game opposite the hand; its column at the hand's side)
                if (left) {
                    fit43(game, area.left + colW, area.top, area.width() - colW, area.height(), false);
                    game.offsetTo(area.right - game.width(), game.top);
                } else {
                    fit43(game, area.left, area.top, area.width() - colW, area.height(), false);
                }
                final int c0 = left ? area.left : game.right, c1 = left ? game.left : area.right;
                colW = c1 - c0;
                if (padW > colW) {      // (a narrow column: smaller keys)
                    btn = Math.max(Math.round(36 * dp), (colW - 2 * gap - sideW - 3 * m) / 3);
                    padW = 3 * btn + 2 * gap + sideW + 3 * m;
                    padH = 3 * btn + 2 * gap + 2 * m;
                }
                if (showPad) pr.set(c0, area.bottom - padH, c1, area.bottom);
                if (showComp) cr.set(c0, area.top, c1, showPad ? pr.top - m : area.bottom);
                stripLeft = left;
            } else {
                fit43(game, area.left, area.top, area.width(), area.height(), true);
            }
        } else {
            final int gh = Math.min(area.width() * 3 / 4, area.height());
            game.set(area.left, area.top, area.left + gh * 4 / 3, area.top + gh);
            if (phone) {
                // (a phone upright: the pad across the foot, its keys at the hand's side; the Companion between)
                if (showPad) pr.set(area.left, area.bottom - padH, area.right, area.bottom);
                if (showComp) cr.set(area.left, game.bottom + m, area.right, (showPad ? pr.top : area.bottom) - m);
            } else {
                // (a tablet upright: the pad in the hand's corner, the Companion opposite it - the room's full
                // height, larger)
                if (showPad) {
                    final int pw = Math.min(padW, area.width() / 2);
                    if (left) pr.set(area.left, area.bottom - padH, area.left + pw, area.bottom);
                    else pr.set(area.right - pw, area.bottom - padH, area.right, area.bottom);
                }
                if (showComp) {
                    if (!showPad) cr.set(area.left, game.bottom + m, area.right, area.bottom - m);
                    else if (left) cr.set(pr.right + m, game.bottom + m, area.right, area.bottom - m);
                    else cr.set(area.left, game.bottom + m, pr.left - m, area.bottom - m);
                }
            }
            stripLeft = !phone && showPad ? !left : left;
            if (!showComp && !showPad) fit43(game, area.left, area.top, area.width(), area.height(), true);
        }
        if (cr.height() < 64 * dp || cr.width() < 64 * dp) cr.setEmpty();

        // The Companion (Tom, 2026-11-25: "smart"). Sideways, in the column: the map as wide as the column, at its
        // top; the party under it when there is room for it (four fifths of the map's size or more) - else one
        // of them, a tap flipping them. The map is never made smaller for the party. Upright: both, the front
        // end setting them side by side or stacked, whichever shows them larger.
        int view = 0;
        final Rect pic = new Rect(cr);
        if (!cr.isEmpty()) {
            if (stripLeft) pic.left += stripW;
            else pic.right -= stripW;
            if (land) {
                final int mh = picH(pic.width()), g = mh / 40;
                final int ph = Math.min(mh, pic.height() - mh - g);
                if (mh <= pic.height() && ph >= mh * 4 / 5) {
                    view = 3;
                    pic.bottom = pic.top + mh + g + ph;
                } else {
                    view = compView;
                    if (!phone) pic.top += band;        // (a tablet: the map / party words above the picture)
                    pic.bottom = Math.min(cr.bottom, pic.top + mh);
                }
            }
        }
        final boolean single = view == 1 || view == 2;
        comp.single = single;
        comp.stripLeft = stripLeft;
        comp.stripW = stripW;
        comp.band = single && !phone ? band : 0;
        comp.turnedHint = single && phone;
        comp.topPad = cr.top <= area.top + 1 ? cornerPad() : Math.round(6 * dp);
        // (the menu's button in the strip, under the swap arrow - unless a phone's turned words then won't fit
        // along it: then in the pad's column)
        boolean menuInStrip = true;
        if (comp.turnedHint && showPad) {
            final Paint t = paint(MUTED);
            t.setTextSize(11 * dp);
            final float need = comp.topPad + 3 * (26 * dp + 12 * dp) + t.measureText("tap for the party") + 8 * dp;
            menuInStrip = cr.height() >= need;
        }
        comp.menuHere = menuInStrip;
        pad.menuHere = !cr.isEmpty() && !menuInStrip;
        pad.btn = btn;
        pad.tray = !land;
        // (its small buttons: sideways at the screen's edge, the keys toward the game; upright the keys at the
        // hand's side, the small buttons inside them)
        pad.sideLeft = land ? left : !left;
        pad.keysLeft = !pad.sideLeft;
        pad.swapHere = cr.isEmpty();
        place(comp, cr.left, cr.top, cr.width(), cr.height());
        place(pad, pr.left, pr.top, pr.width(), pr.height());
        comp.invalidate();
        pad.invalidate();

        // The handles: where a swipe is ours. The menu's and the Companion's at the hand's edge, together (Tom,
        // 2026-11-25: the menu's had blended into the game's frame at the other side); with the Companion out
        // the menu is its strip's button.
        final int hw = Math.round(20 * dp), hl = Math.round(96 * dp), hg = Math.round(14 * dp);
        final int edgeX = left ? area.left : area.right - hw;
        hMenu.edge = hComp.edge = left ? Handle.LEFT_EDGE : Handle.RIGHT_EDGE;
        final boolean menuH = !page() && cr.isEmpty(), compH = playing() && cr.isEmpty();
        // (their middle: beside the game; sideways with only the pad in the column, the room above it)
        final int cy;
        if (state == PAGES) cy = area.centerY();
        else if (land && !pr.isEmpty()) cy = (area.top + pr.top) / 2;
        else cy = game.centerY();
        final int n = (menuH ? 1 : 0) + (compH ? 1 : 0), y0 = cy - (n * hl + (n - 1) * hg) / 2;
        if (menuH) place(hMenu, edgeX, y0, hw, hl);
        else place(hMenu, 0, 0, 0, 0);
        if (compH) place(hComp, edgeX, menuH ? y0 + hl + hg : y0, hw, hl);
        else place(hComp, 0, 0, 0, 0);
        hMenu.invalidate();
        hComp.invalidate();
        if (state != PAGES && pr.isEmpty() && !controller) {
            // (where the controls will come out: the hand's side - sideways under the Companion's column, upright
            // the hand's corner; Tom, 2026-11-24: "not centered")
            int bx;
            if (land && !cr.isEmpty()) bx = cr.left + (cr.width() - hl) / 2;
            else if (land) bx = left ? area.left + Math.round(24 * dp) : area.right - hl - Math.round(24 * dp);
            else bx = left ? area.left + Math.round(40 * dp) : area.right - hl - Math.round(40 * dp);
            place(hPad, bx, area.bottom - hw - Math.round(4 * dp), hl, hw);
        } else {
            place(hPad, 0, 0, 0, 0);
        }

        final boolean noComp = cr.isEmpty() || pic.width() <= 0 || pic.height() <= 0;
        final int[] v = {game.left, game.top, game.width(), game.height(),
                noComp ? 0 : pic.left, noComp ? 0 : pic.top, noComp ? 0 : pic.width(), noComp ? 0 : pic.height(), view};
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

    /** A pin, subdued: pinned - a gold pin in a faint ring; unpinned - a muted pin, struck through. */
    private void drawPin(Canvas c, float cx, float cy, float r, boolean pinned) {
        final Paint ring = paint(pinned ? 0x40E8B840 : 0x33465C96);
        c.drawCircle(cx, cy, r, ring);
        final Paint ink = paint(pinned ? GOLD : MUTED);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(1.6f * dp);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        final float u = r / 12f;
        final Path p = new Path();
        p.moveTo(cx - 3 * u, cy - 7 * u);
        p.lineTo(cx + 3 * u, cy - 7 * u);
        p.lineTo(cx + 2 * u, cy - 1.5f * u);
        p.lineTo(cx + 5.5f * u, cy + 2 * u);
        p.lineTo(cx - 5.5f * u, cy + 2 * u);
        p.lineTo(cx - 2 * u, cy - 1.5f * u);
        p.close();
        c.drawPath(p, ink);
        c.drawLine(cx, cy + 2 * u, cx, cy + 8 * u, ink);
        if (!pinned) c.drawLine(cx - 7 * u, cy - 7 * u, cx + 7 * u, cy + 7 * u, ink);
    }

    /** A small round button's glyph: the swap arrows (0), down - put away (1), the menu (2, gold). */
    private void drawRound(Canvas c, float cx, float cy, float r, int kind, boolean down) {
        c.drawCircle(cx, cy, r, paint(down ? 0xFF3A4680 : 0x33465C96));
        if (kind == 2) {
            final Paint rim = paint(GOLD);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.2f * dp);
            c.drawCircle(cx, cy, r, rim);
        }
        final Paint ink = paint(kind == 2 ? GOLD : CREAM);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(1.8f * dp);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        final float u = r / 10f;
        final Path p = new Path();
        if (kind == 0) {
            // (swap sides: an arrow each way)
            p.moveTo(cx - 6 * u, cy - 2.5f * u);
            p.lineTo(cx + 6 * u, cy - 2.5f * u);
            p.moveTo(cx + 3.5f * u, cy - 5 * u);
            p.lineTo(cx + 6 * u, cy - 2.5f * u);
            p.lineTo(cx + 3.5f * u, cy);
            p.moveTo(cx + 6 * u, cy + 2.5f * u);
            p.lineTo(cx - 6 * u, cy + 2.5f * u);
            p.moveTo(cx - 3.5f * u, cy);
            p.lineTo(cx - 6 * u, cy + 2.5f * u);
            p.lineTo(cx - 3.5f * u, cy + 5 * u);
        } else if (kind == 2) {
            // (the menu: three lines)
            for (int i = -1; i <= 1; ++i) {
                p.moveTo(cx - 5.5f * u, cy + i * 3.5f * u);
                p.lineTo(cx + 5.5f * u, cy + i * 3.5f * u);
            }
        } else {
            // (put away: down)
            p.moveTo(cx - 5 * u, cy - 2 * u);
            p.lineTo(cx, cy + 3 * u);
            p.lineTo(cx + 5 * u, cy - 2 * u);
        }
        c.drawPath(p, ink);
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
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !drawer.isOpen() && !page() && !notice.isUp()) {
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
        int edge;
        final String label;
        float downX, downY;

        Handle(Context c, int edge, String label) {
            super(c);
            this.edge = edge;
            this.label = label;
            setContentDescription(label.equals("MENU") ? "Open the menu" : label.equals("COMPANION") ? "Open the Companion"
                    : "Open the controls");
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (Build.VERSION.SDK_INT >= 29) setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, r - l, b - t)));
        }

        @Override
        protected void onDraw(Canvas c) {
            // (the app's navy with a gold edge and gold words: plain against any of the games' frames - Tom,
            // 2026-11-25: the see-through gold had blended into them)
            final float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2, sw = 1.5f * dp;
            final RectF box = edge == LEFT_EDGE ? new RectF(-r, sw / 2, w - sw / 2, h - sw / 2)
                    : edge == RIGHT_EDGE ? new RectF(sw / 2, sw / 2, w + r, h - sw / 2)
                    : new RectF(sw / 2, sw / 2, w - sw / 2, h + r);
            c.drawRoundRect(box, r, r, paint(0xF20B1024));
            final Paint rim = paint(GOLD);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(sw);
            c.drawRoundRect(box, r, r, rim);
            final Paint t = paint(GOLD);
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
            if (label.equals("MENU")) buttonTap(START);
            else if (label.equals("COMPANION")) setCompOut(true);
            else setPadOut(true);
        }
    }

    /**
     * Over the Companion's pictures (the front end draws them under this, in the room left of its strip). Its
     * strip at the outer edge: the pin (subdued), the swap arrow (Tom: "a left/right arrow ... would swap the
     * controls / companion area to the opposite side"), and on a phone held sideways the map / party words
     * turned along it; on a tablet those words stand above the picture. A tap on the picture flips the map and
     * the party (one shown); a sideways swipe puts it away (unpinned).
     */
    final class CompView extends View {
        boolean single, stripLeft, turnedHint, menuHere = true;
        int stripW, band, topPad;
        float downX, downY;
        boolean moved;

        CompView(Context c) {
            super(c);
            setContentDescription("Companion: tap to switch between the map and the party");
        }

        private float r() { return 13 * dp; }
        private float sx() { return stripLeft ? stripW / 2f : getWidth() - stripW / 2f; }
        private float pinY() { return topPad + r(); }
        private float swapY() { return pinY() + 2 * r() + 12 * dp; }
        private float menuY() { return swapY() + 2 * r() + 12 * dp; }

        @Override
        protected void onDraw(Canvas c) {
            drawPin(c, sx(), pinY(), r(), compPin);
            drawRound(c, sx(), swapY(), r(), 0, false);
            if (menuHere) drawRound(c, sx(), menuY(), r(), 2, false);
            if (!single) return;
            final Paint t = paint(MUTED);
            t.setTextSize(11 * dp);
            t.setTextAlign(Paint.Align.CENTER);
            final String words = compView == 1 ? "tap for the party" : "tap for the map";
            if (turnedHint) {
                // (turned along the strip, under the pin and the arrow)
                final float y = (menuHere ? menuY() : swapY()) + r() + 12 * dp + t.measureText(words) / 2;
                if (y + t.measureText(words) / 2 > getHeight()) return;
                c.save();
                c.translate(sx() + 4 * dp * (stripLeft ? -1 : 1), y);
                c.rotate(stripLeft ? -90 : 90);
                c.drawText(words, 0, 0, t);
                c.restore();
            } else if (band > 0) {
                final float x0 = stripLeft ? stripW : 0, w = getWidth() - stripW;
                c.drawText(words, x0 + w / 2, band - 7 * dp, t);
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
                case MotionEvent.ACTION_UP: {
                    if (moved) return true;
                    final float x = e.getX(), y = e.getY(), hit = r() + 8 * dp;
                    if (Math.hypot(x - sx(), y - pinY()) < hit) {
                        togglePin(false);
                    } else if (Math.hypot(x - sx(), y - swapY()) < hit) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        swapHands();
                    } else if (menuHere && Math.hypot(x - sx(), y - menuY()) < hit) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        buttonTap(START);
                    } else if (single && (stripLeft ? x > stripW : x < getWidth() - stripW)) {
                        compView = compView == 1 ? 2 : 1;
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        invalidate();
                        relayout(false);
                    }
                    return true;
                }
                default:
                    return true;
            }
        }
    }

    /**
     * The pad: 8 ways (corners in a fight), OK in the middle; beside the keys a column of small buttons - the
     * pin, Back, put away (and the swap arrow when no Companion is out). No drag bar (Tom, 2026-11-24): the keys
     * larger; a swipe still puts it away.
     */
    final class PadView extends View {
        int btn = 56;
        boolean tray;               // at the foot of an upright screen (rounded at the top only)
        boolean sideLeft, keysLeft, swapHere, menuHere;
        // pointer id -> the key it holds (-1 none, 0..8 the grid, 9 Back, 10 the pin, 11 the surface, 12 away, 13 swap,
        // 14 the menu)
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
        private float sideW() { return 52 * dp; }
        private float m() { return 8 * dp; }

        /** The keys' left edge: toward the game (sideways) or the hand's side (upright), the side column beside. */
        private float gx() {
            final float w = getWidth(), sw = sideW(), gw = gridW();
            if (!tray) return sideLeft ? w - m() - gw : m();
            // (upright: keys at the hand's side, the side column next to them, inside)
            return keysLeft ? m() : w - m() - gw;
        }
        private float gy() { return (getHeight() - gridW()) / 2f; }
        private float sideX() {
            final float gw = gridW();
            if (!tray) return sideLeft ? m() + sideW() / 2 : getWidth() - m() - sideW() / 2;
            return keysLeft ? gx() + gw + m() + sideW() / 2 : gx() - m() - sideW() / 2;
        }

        private boolean diagonals() { return state == FIGHT; }

        private RectF keyRect(int i) {
            final float x = gx() + (i % 3) * (btn + gap()), y = gy() + (i / 3) * (btn + gap());
            return new RectF(x, y, x + btn, y + btn);
        }

        // The side column's buttons, top to bottom: the pin, Back, put away (+ swap)
        private int items() { return 3 + (swapHere ? 1 : 0) + (menuHere ? 1 : 0); }
        private float itemY(int k) {
            final float h = getHeight(), top = m() + 4 * dp, span = h - 2 * top;
            return top + span * (k + 0.5f) / items();
        }
        private float smallR() { return Math.min(16 * dp, (getHeight() - 2 * m()) / items() / 2 - 3 * dp); }
        private RectF backRect() {
            final float w = Math.min(48 * dp, sideW() - 4 * dp), h = Math.min(34 * dp, smallR() * 2 + 4 * dp);
            return new RectF(sideX() - w / 2, itemY(1) - h / 2, sideX() + w / 2, itemY(1) + h / 2);
        }

        private int hit(float x, float y) {
            for (int i = 0; i < 9; ++i) {
                final RectF r = keyRect(i);
                r.inset(-gap() / 2f, -gap() / 2f);
                if (r.contains(x, y)) return (i % 2 == 0 && i != 4 && !diagonals()) ? 11 : i;
            }
            final float hr = smallR() + 8 * dp;
            if (Math.hypot(x - sideX(), y - itemY(0)) < hr) return 10;
            final RectF b = backRect();
            b.inset(-6 * dp, -6 * dp);
            if (b.contains(x, y)) return 9;
            if (Math.hypot(x - sideX(), y - itemY(2)) < hr) return 12;
            if (swapHere && Math.hypot(x - sideX(), y - itemY(3)) < hr) return 13;
            if (menuHere && Math.hypot(x - sideX(), y - itemY(items() - 1)) < hr) return 14;
            return 11;
        }

        @Override
        protected void onDraw(Canvas c) {
            final float w = getWidth(), h = getHeight(), rad = 24 * dp;
            final Paint surface = paint(SURFACE);
            if (tray) c.drawRoundRect(new RectF(0, 0, w, h + rad), rad, rad, surface);
            else c.drawRoundRect(new RectF(0, 0, w, h), rad, rad, surface);

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
                    t.setTextSize(16 * dp);
                    t.setTextAlign(Paint.Align.CENTER);
                    c.drawText("OK", r.centerX(), r.centerY() + 5.5f * dp, t);
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
            // The side column: the pin, Back, put away (+ swap)
            drawPin(c, sideX(), itemY(0), smallR(), padPin);
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
            drawRound(c, sideX(), itemY(2), smallR(), 1, isHeld(12));
            if (swapHere) drawRound(c, sideX(), itemY(3), smallR(), 0, isHeld(13));
            if (menuHere) drawRound(c, sideX(), itemY(items() - 1), smallR(), 2, isHeld(14));
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
                            final boolean away = tray ? dy > 48 * dp : Math.abs(dx) > 48 * dp || dy > 48 * dp;
                            if (away) {
                                downPointer = -1;
                                if (padPin) Toast.makeText(act, "The controls are pinned - tap the pin first", Toast.LENGTH_SHORT).show();
                                else setPadOut(false);
                            }
                            continue;
                        }
                        if (was < 0 || was > 8) continue;       // (the small buttons don't slide)
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
                    if (k == 12) {
                        // (put away, pinned or not: the button says so - the pin keeps its state)
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        padOut = false;
                        prefs.edit().putBoolean("pad_" + key, false).apply();
                        relayout(false);
                    }
                    if (k == 13) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        swapHands();
                    }
                    if (k == 14) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        buttonTap(START);
                    }
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
