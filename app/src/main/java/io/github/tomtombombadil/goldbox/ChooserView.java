package io.github.tomtombombadil.goldbox;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The game chooser (Tom, 2026-11-24, the redesign's stage 3), in Android's views over
 * the front end's list (main.cpp android_chooser_text; its taps: GoldBoxActivity.
 * nativeMenu ops 10 play, 11 look again, 12 the chosen one). Shaped for the device:
 * a phone held upright - one game a card, its art upright, the next ones peeking,
 * swiped sideways; a phone held sideways - the art wide at the left, the game's name
 * and Play at the right; a tablet - every game at once, three across. The pictures
 * are the ones the scan made (APPART.IMG); this only reads them.
 */
final class ChooserView extends FrameLayout {

    static final int OP_PLAY = 10, OP_LOOK_AGAIN = 11, OP_SELECT = 12;
    private static final int NAVY = Shell.NAVY, SURFACE = Shell.SURFACE, KEY = Shell.KEY, EDGE = Shell.EDGE,
            GOLD = Shell.GOLD, CREAM = Shell.CREAM, MUTED = Shell.MUTED;

    static final class Game {
        String name, shortName, art, release;
        boolean playable;
        int kind;           // 2 box art, 1 logo, 0 none
    }

    private final float dp;
    private final List<Game> games = new ArrayList<>();
    private String mode = "off", folder = "";
    private int sel = 0;
    private String built = "";
    private boolean up = false;

    private final Map<String, Bitmap> pictures = new HashMap<>();
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    // (the phone's views, kept to change the chosen game without building anew)
    private Card card, cardPrev, cardNext;
    private TextView nameView, releaseView;
    private TextView playButton;
    private LinearLayout dots;
    private final List<Card> grid = new ArrayList<>();

    ChooserView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(NAVY);
        setVisibility(GONE);
        setClickable(true);
    }

    boolean isUp() { return up; }

    private int px(float d) { return Math.round(d * dp); }

    // ---- What the front end sends

    void update(byte[] bytes) {
        final String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
        final String[] head = lines[0].split("\t", -1);
        mode = head[0];
        if (mode.equals("off")) {
            up = false;
            setVisibility(GONE);
            return;
        }
        games.clear();
        if (mode.equals("games")) {
            sel = head.length > 1 ? parse(head[1]) : 0;
            for (int i = 1; i < lines.length; ++i) {
                final String[] f = lines[i].split("\t", -1);
                if (f.length < 6) continue;
                final Game g = new Game();
                g.name = f[0];
                g.shortName = f[1];
                g.playable = f[2].equals("1");
                g.art = f[3];
                g.kind = parse(f[4]);
                g.release = f[5];
                games.add(g);
            }
            if (sel < 0 || sel >= games.size()) sel = 0;
        } else {
            folder = head.length > 1 ? head[1] : "";
        }
        up = true;
        setVisibility(VISIBLE);
        rebuild(false);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        post(() -> rebuild(true));
    }

    // ---- The layouts

    private void rebuild(boolean force) {
        final int W = getWidth(), H = getHeight();
        if (W <= 0 || H <= 0 || !up) return;
        final boolean phone = (float) Math.max(W, H) / Math.min(W, H) >= 1.5f;
        final boolean land = W >= H;
        final StringBuilder key = new StringBuilder(mode).append(W).append('x').append(H).append(folder);
        for (Game g : games) key.append('|').append(g.name).append(g.art).append(g.playable);
        if (!force && key.toString().equals(built)) {
            showSel();
            return;
        }
        built = key.toString();
        removeAllViews();
        card = cardPrev = cardNext = null;
        grid.clear();
        final int[] ins = ((GoldBoxActivity) getContext()).cutoutInsets();
        final LinearLayout page = new LinearLayout(getContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(ins[0], ins[1], ins[2], ins[3]);
        addView(page, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(appBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(60)));
        final FrameLayout body = new FrameLayout(getContext());
        page.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (!mode.equals("games")) buildEmpty(body);
        else if (!phone) buildGrid(body, land);
        else if (land) buildWide(body);
        else buildCards(body);
        showSel();
    }

    private View appBar() {
        final Context c = getContext();
        final LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(20), 0, px(8), 0);
        final TextView t = Screens.text(c, 22, CREAM, false);
        t.setText("Gold Box");
        bar.addView(t);
        final TextView v = Screens.text(c, 13, MUTED, false);
        v.setText("  v" + version());
        bar.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final Glyph folderB = new Glyph(c, Glyph.FOLDER);
        folderB.setContentDescription("Games folder");
        folderB.setOnClickListener(x -> chooseFolder());
        bar.addView(folderB, new LinearLayout.LayoutParams(px(48), px(48)));
        final Glyph menuB = new Glyph(c, Glyph.SETTINGS);
        menuB.setContentDescription("Menu and settings");
        menuB.setOnClickListener(x -> {
            x.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            Screens.press(Shell.START);
        });
        bar.addView(menuB, new LinearLayout.LayoutParams(px(48), px(48)));
        return bar;
    }

    private String version() {
        try {
            return getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void chooseFolder() {
        final GoldBoxActivity a = (GoldBoxActivity) getContext();
        a.chooseFolder();
        ((Activity) a).finish();        // (as the menu's Games Folder: the folder screen starts it anew)
    }

    /** No games (or no folder): where to put them, Look Again, another folder. */
    private void buildEmpty(FrameLayout body) {
        final Context c = getContext();
        final LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(px(28), px(12), px(28), px(12));
        final TextView h = Screens.text(c, 24, CREAM, false);
        h.setText(mode.equals("nofolder") ? "No games folder" : "No games found");
        h.setGravity(Gravity.CENTER);
        col.addView(h);
        final TextView w = Screens.text(c, 15, 0xFFC8D0EA, false);
        w.setGravity(Gravity.CENTER);
        w.setLineSpacing(0, 1.2f);
        w.setPadding(0, px(14), 0, px(10));
        w.setText("This app plays SSI's Gold Box games from your own copies. Copy each game's whole folder (from GOG "
                + "or Steam) into the games folder:");
        col.addView(w);
        final TextView f = Screens.text(c, 16, GOLD, true);
        f.setGravity(Gravity.CENTER);
        f.setText(folder.replace("/", " › ").replaceAll(" › $", ""));
        col.addView(f);
        final TextView e = Screens.text(c, 14, MUTED, false);
        e.setGravity(Gravity.CENTER);
        e.setPadding(0, px(10), 0, px(22));
        e.setText("For example: games › Curse of the Azure Bonds. A PC shows it the same way over USB.");
        col.addView(e);
        final LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER);
        row.addView(Screens.button(c, "Choose Folder", false, x -> chooseFolder()));
        final TextView again = Screens.button(c, "Look Again", true, x -> GoldBoxActivity.nativeMenu(OP_LOOK_AGAIN, 0, 0));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = px(12);
        row.addView(again, lp);
        col.addView(row);
        body.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
    }

    /** A tablet: every game at once, three across - a tap plays. */
    private void buildGrid(FrameLayout body, boolean land) {
        final Context c = getContext();
        final int n = games.size(), cols = n <= 4 ? Math.max(1, Math.min(n, 2)) : 3;
        final int rows = (n + cols - 1) / cols;
        final GridLayout g = new GridLayout(c);
        g.setColumnCount(cols);
        g.setRowCount(rows);
        final int pad = px(20), gap = px(16);
        g.setPadding(pad, px(4), pad, pad);
        body.addView(g, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.post(() -> {
            final int bw = body.getWidth() - 2 * pad, bh = body.getHeight() - pad - px(4);
            final int cw = (bw - (cols - 1) * gap) / cols, ch = (bh - (rows - 1) * gap) / rows;
            for (int i = 0; i < n; ++i) {
                final Card k = new Card(c, games.get(i), i, Card.TILE);
                final GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = cw;
                lp.height = ch;
                lp.rightMargin = i % cols == cols - 1 ? 0 : gap;
                lp.bottomMargin = i / cols == rows - 1 ? 0 : gap;
                g.addView(k, lp);
                grid.add(k);
                final int idx = i;
                k.setOnClickListener(v -> play(idx));
            }
            showSel();
        });
    }

    /** A phone held upright: a card a game, the next ones peeking; swiped sideways. */
    private void buildCards(FrameLayout body) {
        final Context c = getContext();
        body.post(() -> {
            final int bw = body.getWidth(), bh = body.getHeight();
            final int foot = px(150);
            int cw = Math.round(bw * 0.74f), chh = bh - foot - px(16);
            // (the card: the art upright, 4:5 - the Steam pictures are wide, so a 4:5 window keeps the heart of
            // them - and its name under it)
            final int artH = Math.min(chh - px(76), cw * 5 / 4);
            cw = Math.min(cw, artH * 4 / 5);
            final int cardH = artH + px(76);
            final int x0 = (bw - cw) / 2, y0 = px(8);
            cardPrev = new Card(c, null, -1, Card.PEEK);
            cardNext = new Card(c, null, -1, Card.PEEK);
            card = new Card(c, null, -1, Card.CARD);
            final int peekH = cardH - px(24);
            body.addView(cardPrev, frame(x0 - cw - px(14), y0 + px(12), cw, peekH));
            body.addView(cardNext, frame(x0 + cw + px(14), y0 + px(12), cw, peekH));
            body.addView(card, frame(x0, y0, cw, cardH));
            card.artH = artH;
            card.setOnClickListener(v -> play(sel));
            final Swipe sw = new Swipe();
            body.setOnTouchListener(sw);
            card.setOnTouchListener(sw);

            dots = new LinearLayout(c);
            dots.setGravity(Gravity.CENTER);
            body.addView(dots, frame(0, y0 + cardH + px(14), bw, px(10)));
            final LinearLayout acts = new LinearLayout(c);
            acts.setOrientation(LinearLayout.VERTICAL);
            acts.setGravity(Gravity.CENTER_HORIZONTAL);
            playButton = Screens.button(c, "Play", true, v -> play(sel));
            acts.addView(playButton, new LinearLayout.LayoutParams(Math.min(bw - px(48), px(340)), px(56)));
            final TextView hint = Screens.text(c, 12, MUTED, false);
            hint.setText("Swipe the card sideways for the other games");
            hint.setPadding(0, px(10), 0, 0);
            hint.setGravity(Gravity.CENTER);
            acts.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            body.addView(acts, frame(0, y0 + cardH + px(40), bw, ViewGroup.LayoutParams.WRAP_CONTENT));
            showSel();
        });
    }

    /** A phone held sideways: the art wide at the left, the name and Play at the right. */
    private void buildWide(FrameLayout body) {
        final Context c = getContext();
        body.post(() -> {
            final int bw = body.getWidth(), bh = body.getHeight();
            final int panel = Math.max(px(280), bw * 36 / 100);
            card = new Card(c, null, -1, Card.WIDE);
            body.addView(card, frame(0, 0, bw - panel + px(60), bh));
            card.setOnClickListener(v -> play(sel));
            final Swipe sw = new Swipe();
            card.setOnTouchListener(sw);
            final LinearLayout side = new LinearLayout(c);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setPadding(px(24), px(4), px(24), px(16));
            nameView = Screens.text(c, 24, CREAM, false);
            nameView.setMaxLines(3);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            side.addView(nameView);
            releaseView = Screens.text(c, 13, MUTED, false);
            releaseView.setPadding(0, px(6), 0, 0);
            side.addView(releaseView);
            side.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1));
            playButton = Screens.button(c, "Play", true, v -> play(sel));
            side.addView(playButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)));
            final LinearLayout nav = new LinearLayout(c);
            nav.setGravity(Gravity.CENTER_VERTICAL);
            nav.setPadding(0, px(12), 0, 0);
            final Glyph prev = new Glyph(c, Glyph.PREV);
            prev.setContentDescription("The game before");
            prev.setOnClickListener(v -> step(-1));
            final Glyph next = new Glyph(c, Glyph.NEXT);
            next.setContentDescription("The next game");
            next.setOnClickListener(v -> step(1));
            dots = new LinearLayout(c);
            dots.setGravity(Gravity.CENTER);
            nav.addView(prev, new LinearLayout.LayoutParams(px(44), px(44)));
            nav.addView(dots, new LinearLayout.LayoutParams(0, px(10), 1));
            nav.addView(next, new LinearLayout.LayoutParams(px(44), px(44)));
            side.addView(nav);
            final GradientDrawable fade = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[] {0x000B1024, NAVY, NAVY});
            side.setBackground(fade);
            body.addView(side, frame(bw - panel, 0, panel, bh));
            showSel();
        });
    }

    private static FrameLayout.LayoutParams frame(int x, int y, int w, int h) {
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    // ---- The chosen game

    private void showSel() {
        final int n = games.size();
        if (n == 0) return;
        if (card != null) {
            card.set(games.get(sel), sel);
            if (cardPrev != null) {
                cardPrev.set(n > 1 ? games.get((sel + n - 1) % n) : null, -1);
                cardNext.set(n > 1 ? games.get((sel + 1) % n) : null, -1);
                cardPrev.setVisibility(n > 1 ? VISIBLE : INVISIBLE);
                cardNext.setVisibility(n > 2 ? VISIBLE : INVISIBLE);
            }
        }
        final Game g = games.get(sel);
        if (nameView != null) nameView.setText(g.name);
        if (releaseView != null) releaseView.setText(g.release.isEmpty() ? "" : g.release + (g.playable ? "" : " · not playable"));
        if (playButton != null) {
            playButton.setText(g.playable ? "Play" : "Not playable");
            playButton.setEnabled(g.playable);
            playButton.setAlpha(g.playable ? 1f : 0.45f);
        }
        if (dots != null) {
            dots.removeAllViews();
            for (int i = 0; i < n && n > 1; ++i) {
                final View d = new View(getContext());
                d.setBackground(Screens.round(i == sel ? GOLD : EDGE, px(3)));
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(i == sel ? px(18) : px(6), px(6));
                lp.leftMargin = lp.rightMargin = px(3);
                dots.addView(d, lp);
            }
        }
        for (Card k : grid) k.setChosen(k.index == sel);
    }

    private void step(int d) {
        final int n = games.size();
        if (n < 2) return;
        sel = (sel + d + n) % n;
        GoldBoxActivity.nativeMenu(OP_SELECT, sel, 0);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        showSel();
    }

    private void play(int i) {
        if (i < 0 || i >= games.size() || !games.get(i).playable) return;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        GoldBoxActivity.nativeMenu(OP_PLAY, i, 0);
    }

    /** A sideways swipe steps through the games; a tap is the card's. */
    final class Swipe implements OnTouchListener {
        float x0, y0;
        boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = e.getRawX();
                    y0 = e.getRawY();
                    moved = false;
                    return v == card ? false : true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.abs(e.getRawX() - x0) > px(40) && Math.abs(e.getRawX() - x0) > Math.abs(e.getRawY() - y0)) {
                        moved = true;
                        step(e.getRawX() < x0 ? 1 : -1);
                    }
                    return moved;
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        moved = false;
                        return true;    // (not a tap)
                    }
                    return false;
                default:
                    return false;
            }
        }
    }

    // ---- The pictures (the scan's files, read off the UI thread)

    private void picture(String path, int want, Card into) {
        if (path == null || path.isEmpty()) return;
        final Bitmap have = pictures.get(path);
        if (have != null) {
            into.setPicture(have, path);
            return;
        }
        reader.execute(() -> {
            Bitmap b = null;
            try {
                final BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= Math.max(want, 256)) sample *= 2;
                final BitmapFactory.Options d = new BitmapFactory.Options();
                d.inSampleSize = sample;
                b = BitmapFactory.decodeFile(path, d);
            } catch (Exception | OutOfMemoryError ignored) {
            }
            final Bitmap got = b;
            ui.post(() -> {
                if (got == null) return;
                pictures.put(path, got);
                into.setPicture(got, path);
            });
        });
    }

    /**
     * A game's picture and name: CARD (the phone's, upright), PEEK (a neighbour, dim),
     * WIDE (sideways: the art across, fading into the panel), TILE (a tablet's grid).
     */
    final class Card extends View {
        static final int CARD = 0, PEEK = 1, WIDE = 2, TILE = 3;
        final int style;
        int index, artH;
        Game game;
        Bitmap bmp;
        String bmpFor;
        boolean chosen;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        Card(Context c, Game g, int index, int style) {
            super(c);
            this.style = style;
            setClickable(style != PEEK);
            set(g, index);
        }

        void set(Game g, int i) {
            game = g;
            index = i;
            setContentDescription(g == null ? null : g.name + (g.playable ? "" : ", not playable"));
            if (g == null || g.art == null || g.art.isEmpty()) {
                bmp = null;
                bmpFor = null;
            } else if (!g.art.equals(bmpFor)) {
                bmp = null;
                picture(g.art, Math.max(getWidth(), getHeight()), this);
            }
            invalidate();
        }

        void setPicture(Bitmap b, String path) {
            if (game == null || !path.equals(game.art)) return;
            bmp = b;
            bmpFor = path;
            invalidate();
        }

        void setChosen(boolean on) {
            if (chosen == on) return;
            chosen = on;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (game != null && bmp == null) picture(game.art, Math.max(w, h), this);
        }

        @Override
        protected void onDraw(Canvas c) {
            final float w = getWidth(), h = getHeight();
            final float r = style == WIDE ? 0 : 24 * dp;
            final float nameH = style == CARD ? 76 * dp : style == TILE ? 54 * dp : 0;
            final float ah = style == CARD && artH > 0 ? artH : h - nameH;
            final Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            p.setShader(null);
            p.setColor(style == PEEK ? 0xFF1A2244 : SURFACE);
            c.drawRect(0, 0, w, h, p);
            if (game != null) {
                // The art: box art fills its room (cropped to the device's shape); a logo sits whole on a
                // soft backdrop - the game's own picture either way, never stretched
                final RectF room = new RectF(0, 0, w, ah);
                p.setShader(new LinearGradient(0, 0, w, ah, 0xFF2C3766, SURFACE, Shader.TileMode.CLAMP));
                c.drawRect(room, p);
                p.setShader(null);
                if (bmp != null) {
                    final float bw = bmp.getWidth(), bh = bmp.getHeight();
                    final boolean logo = game.kind == 1;
                    float s = logo ? Math.min(room.width() * 0.62f / bw, room.height() * 0.62f / bh)
                                   : Math.max(room.width() / bw, room.height() / bh);
                    final float dw = bw * s, dh = bh * s;
                    final RectF to = new RectF(room.centerX() - dw / 2, logo ? room.centerY() - dh / 2 : room.top + Math.min(0, (room.height() - dh) / 4),
                            room.centerX() + dw / 2, 0);
                    to.bottom = to.top + dh;
                    p.setAlpha(style == PEEK ? 110 : game.playable ? 255 : 120);
                    c.save();
                    c.clipRect(room);       // (the art in its room: the name's band stays the card's)
                    c.drawBitmap(bmp, null, to, p);
                    c.restore();
                    p.setAlpha(255);
                } else if (style != PEEK) {
                    final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                    t.setColor(MUTED);
                    t.setTextSize(14 * dp);
                    t.setTextAlign(Paint.Align.CENTER);
                    c.drawText(game.kind == 0 && (game.art == null || game.art.isEmpty()) ? "No picture" : "",
                            room.centerX(), room.centerY(), t);
                }
                if (style == WIDE) {
                    // (fading into the side panel at the right, and darker at the foot)
                    p.setShader(new LinearGradient(w * 0.55f, 0, w, 0, 0x000B1024, NAVY, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                }
                if (nameH > 0) {
                    final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                    t.setColor(game.playable ? CREAM : MUTED);
                    t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                    t.setTextSize((style == CARD ? 18 : 15) * dp);
                    final float x = 16 * dp;
                    final String name = TextUtils.ellipsize(game.name, new android.text.TextPaint(t), w - 2 * x,
                            TextUtils.TruncateAt.END).toString();
                    c.drawText(name, x, ah + (style == CARD ? 32 : 24) * dp, t);
                    t.setColor(MUTED);
                    t.setTypeface(android.graphics.Typeface.DEFAULT);
                    t.setTextSize((style == CARD ? 13 : 12) * dp);
                    c.drawText(game.release + (game.playable ? "" : " · not playable"), x, ah + (style == CARD ? 56 : 43) * dp, t);
                }
            }
            c.restore();
            if (chosen && style == TILE) {
                final Paint o = new Paint(Paint.ANTI_ALIAS_FLAG);
                o.setStyle(Paint.Style.STROKE);
                o.setStrokeWidth(3 * dp);
                o.setColor(GOLD);
                c.drawRoundRect(new RectF(1.5f * dp, 1.5f * dp, w - 1.5f * dp, h - 1.5f * dp), r, r, o);
            }
        }
    }

    /** The app bar's and the arrows' small drawn icons. */
    static final class Glyph extends View {
        static final int FOLDER = 0, SETTINGS = 1, PREV = 2, NEXT = 3;
        final int kind;

        Glyph(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        @Override
        protected void onDraw(Canvas c) {
            final float dp = getResources().getDisplayMetrics().density;
            final float cx = getWidth() / 2f, cy = getHeight() / 2f, u = dp;
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (kind == PREV || kind == NEXT) {
                p.setColor(KEY);
                c.drawCircle(cx, cy, Math.min(cx, cy) - 2 * dp, p);
            }
            p.setColor(CREAM);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * dp);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            final Path path = new Path();
            switch (kind) {
                case FOLDER:
                    path.moveTo(cx - 9 * u, cy - 6 * u);
                    path.lineTo(cx - 3 * u, cy - 6 * u);
                    path.lineTo(cx - 1 * u, cy - 4 * u);
                    path.lineTo(cx + 9 * u, cy - 4 * u);
                    path.lineTo(cx + 9 * u, cy + 7 * u);
                    path.lineTo(cx - 9 * u, cy + 7 * u);
                    path.close();
                    break;
                case SETTINGS:
                    c.drawCircle(cx, cy, 3.5f * u, p);
                    for (int k = 0; k < 8; ++k) {
                        final double a = Math.PI * k / 4;
                        path.moveTo(cx + (float) Math.cos(a) * 6.5f * u, cy + (float) Math.sin(a) * 6.5f * u);
                        path.lineTo(cx + (float) Math.cos(a) * 9.5f * u, cy + (float) Math.sin(a) * 9.5f * u);
                    }
                    c.drawCircle(cx, cy, 6.5f * u, p);
                    break;
                case PREV:
                    path.moveTo(cx + 2.5f * u, cy - 6 * u);
                    path.lineTo(cx - 3.5f * u, cy);
                    path.lineTo(cx + 2.5f * u, cy + 6 * u);
                    break;
                case NEXT:
                    path.moveTo(cx - 2.5f * u, cy - 6 * u);
                    path.lineTo(cx + 3.5f * u, cy);
                    path.lineTo(cx - 2.5f * u, cy + 6 * u);
                    break;
                default:
                    break;
            }
            c.drawPath(path, p);
        }
    }
}
