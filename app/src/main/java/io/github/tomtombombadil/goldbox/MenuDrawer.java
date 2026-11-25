package io.github.tomtombombadil.goldbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The menu drawer (Tom, 2026-11-24, the redesign's stage 2): the front end's menu
 * shown in Android's views, sliding in from the left. The front end's menu still
 * decides everything - its pages, its lines, and a controller's buttons moving
 * about it (the highlight shown here follows); this sends it the taps
 * (GoldBoxActivity.nativeMenu) and shows what it sends back (onShellMenu - the
 * format is main.cpp's android_menu_tick). The volume: a slider, - and +, mute.
 */
final class MenuDrawer extends FrameLayout {

    // shell.h MenuOp
    static final int CLOSE = 0, CHOOSE = 1, CHANGE = 2, BACK = 3, SET = 4;

    private static final int NAVY = Shell.NAVY, KEY = Shell.KEY, GOLD = Shell.GOLD, CREAM = Shell.CREAM,
            MUTED = Shell.MUTED, PANEL = 0xFF1A2244, LIT = 0xFF2C3766;

    private final float dp;
    private final SharedPreferences prefs;
    private final View scrim;
    private final LinearLayout panel, list;
    private final ScrollView scroll;
    private final TextView title;
    private final Icon back;

    private boolean shown = false;
    private String shape = "";            // the page's title and lines (kind + name): the rows are rebuilt when it changes
    private int sel = -1;
    private final List<Row> rows = new ArrayList<>();
    private boolean dragging = false;     // (the slider: no rebuild under the finger)

    static final class Item {
        char kind;
        String name, value, help;
    }

    final class Row {
        int index;
        Item item;
        LinearLayout box;
        TextView value, help;
        Toggle toggle;
        SeekBar bar;
        Icon mute;
    }

    MenuDrawer(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        prefs = c.getSharedPreferences("shell", Context.MODE_PRIVATE);
        setVisibility(GONE);

        scrim = new View(c);
        scrim.setBackgroundColor(0x9E04060E);
        scrim.setOnClickListener(v -> GoldBoxActivity.nativeMenu(CLOSE, 0, 0));
        scrim.setContentDescription("Close the menu");
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new SwipePanel(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        final float r = 24 * dp;
        bg.setCornerRadii(new float[] {0, 0, r, r, r, r, 0, 0});
        panel.setBackground(bg);
        panel.setClickable(true);
        panel.setPadding(px(10), px(10), px(10), px(10));
        addView(panel, new LayoutParams(px(340), ViewGroup.LayoutParams.MATCH_PARENT));

        final LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        back = new Icon(c, Icon.BACK);
        back.setContentDescription("Back a page");
        back.setOnClickListener(v -> GoldBoxActivity.nativeMenu(BACK, 0, 0));
        head.addView(back, new LinearLayout.LayoutParams(px(44), px(44)));
        title = text(c, 18, CREAM, true);
        title.setPadding(px(8), 0, 0, 0);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final Icon close = new Icon(c, Icon.CLOSE);
        close.setContentDescription("Close the menu");
        close.setOnClickListener(v -> GoldBoxActivity.nativeMenu(CLOSE, 0, 0));
        head.addView(close, new LinearLayout.LayoutParams(px(44), px(44)));
        panel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private int px(float d) { return Math.round(d * dp); }

    private TextView text(Context c, float sp, int colour, boolean medium) {
        final TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    boolean isOpen() { return shown; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit(w);
    }

    /** The panel's width and padding for the screen as it is (the cutout's side changes with a half turn). */
    private void fit(int w) {
        if (w <= 0) return;
        // (a camera cutout / notch at the left or the top - a phone sideways, or upright: kept clear, the panel
        // reaching to the screen's edge behind it; Tom, 2026-11-24)
        final int[] ins = ((GoldBoxActivity) getContext()).cutoutInsets();
        final int pw = Math.min(px(360), Math.round(w * 0.88f)) + ins[0];
        panel.setPadding(px(10) + ins[0], px(10) + ins[1], px(10), px(10) + ins[3]);
        final LayoutParams lp = (LayoutParams) panel.getLayoutParams();
        if (lp.width != pw) {
            lp.width = pw;
            post(() -> panel.setLayoutParams(lp));
        }
    }

    // ---- What the front end sends (the UI thread)

    void update(byte[] bytes) {
        final String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
        final String[] head = lines[0].split("\t", -1);
        if (!head[0].equals("open")) {
            // closed, or busy - the port's own screen over the menu (the Button Guide, a notice ...): out of its way
            slide(false);
            if (head[0].equals("closed")) shape = "";
            return;
        }
        final String pageTitle = head.length > 1 ? head[1] : "Menu";
        final int newSel = head.length > 2 ? parse(head[2], -1) : -1;
        final boolean above = head.length > 3 && head[3].equals("1");
        final List<Item> items = new ArrayList<>();
        final StringBuilder sh = new StringBuilder(pageTitle);
        for (int i = 1; i < lines.length; ++i) {
            final String[] f = lines[i].split("\t", -1);
            final Item it = new Item();
            it.kind = f[0].isEmpty() ? 'X' : f[0].charAt(0);
            it.name = f.length > 1 ? f[1] : "";
            it.value = f.length > 2 ? f[2] : "";
            it.help = f.length > 3 ? f[3] : "";
            items.add(it);
            sh.append('\n').append(it.kind).append(it.name);
        }
        title.setText(pageTitle.equals("Menu") ? "Gold Box" : pageTitle);
        back.setVisibility(above ? VISIBLE : INVISIBLE);
        final boolean rebuild = !sh.toString().equals(shape);
        shape = sh.toString();
        if (rebuild) build(items);
        else if (!dragging) refresh(items);
        select(newSel, rebuild);
        slide(true);
    }

    private static int parse(String s, int d) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private void slide(boolean in) {
        if (in == shown) return;
        shown = in;
        panel.animate().cancel();
        scrim.animate().cancel();
        if (in) {
            fit(getRootView() != null ? getRootView().getWidth() : getWidth());
            setVisibility(VISIBLE);
            panel.setTranslationX(-panel.getWidth() > 0 ? -panel.getWidth() : -px(360));
            scrim.setAlpha(0f);
            panel.animate().translationX(0).setDuration(200).start();
            scrim.animate().alpha(1f).setDuration(200).start();
        } else {
            panel.animate().translationX(-panel.getWidth()).setDuration(160).withEndAction(() -> {
                if (!shown) setVisibility(GONE);
            }).start();
            scrim.animate().alpha(0f).setDuration(160).start();
        }
    }

    // ---- The rows

    private void build(List<Item> items) {
        list.removeAllViews();
        rows.clear();
        scroll.scrollTo(0, 0);
        sel = -1;
        final Context c = getContext();
        for (int i = 0; i < items.size(); ++i) {
            final Item it = items.get(i);
            if (it.kind == 'X') continue;
            final Row r = new Row();
            r.index = i;
            r.item = it;
            r.box = new LinearLayout(c);
            r.box.setOrientation(LinearLayout.VERTICAL);
            r.box.setPadding(px(14), px(10), px(10), px(10));
            r.box.setMinimumHeight(px(48));
            r.box.setGravity(Gravity.CENTER_VERTICAL);

            final LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            final TextView name = text(c, 15, CREAM, false);
            name.setText(it.name);
            if (it.kind == 'T') {
                // (a line of text: the name - a button, a device's detail - and its words beside it)
                name.setTextColor(GOLD);
                name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                line.addView(name, new LinearLayout.LayoutParams(px(118), ViewGroup.LayoutParams.WRAP_CONTENT));
                r.value = text(c, 14, CREAM, false);
                r.value.setText(it.value);
                line.addView(r.value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                line.setGravity(Gravity.TOP);
                r.box.setMinimumHeight(px(36));
                r.box.setPadding(px(14), px(6), px(10), px(6));
            } else if (it.kind == 'H') {
                name.setTextColor(MUTED);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                line.addView(name);
                r.box.setMinimumHeight(px(36));
                r.box.setPadding(px(14), px(14), px(10), px(2));
            } else {
                line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            final int idx = i;
            switch (it.kind) {
                case 'P': {
                    final Icon chev = new Icon(c, Icon.NEXT);
                    chev.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                    line.addView(chev, new LinearLayout.LayoutParams(px(24), px(24)));
                    break;
                }
                case 'S': {
                    r.toggle = new Toggle(c);
                    r.toggle.on = it.value.equals("On");
                    line.addView(r.toggle, new LinearLayout.LayoutParams(px(44), px(26)));
                    break;
                }
                case 'C': {
                    final Icon prev = new Icon(c, Icon.PREV);
                    prev.setContentDescription(it.name + ": the one before");
                    prev.setOnClickListener(v -> tap(v, CHANGE, idx, -1));
                    r.value = text(c, 13, GOLD, true);
                    r.value.setGravity(Gravity.CENTER);
                    r.value.setMinWidth(px(64));
                    r.value.setText(it.value);
                    final Icon next = new Icon(c, Icon.NEXT);
                    next.setContentDescription(it.name + ": the next");
                    next.setOnClickListener(v -> tap(v, CHANGE, idx, 1));
                    line.addView(prev, new LinearLayout.LayoutParams(px(36), px(36)));
                    line.addView(r.value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    line.addView(next, new LinearLayout.LayoutParams(px(36), px(36)));
                    break;
                }
                case 'L': {
                    r.value = text(c, 13, MUTED, false);
                    r.value.setText(level(it) == 0 ? "Muted" : level(it) + "%");
                    line.addView(r.value);
                    break;
                }
                default:
                    break;
            }
            r.box.addView(line);

            if (it.kind == 'L') {
                // Volume: mute, -, the slider, +
                final LinearLayout ctl = new LinearLayout(c);
                ctl.setOrientation(LinearLayout.HORIZONTAL);
                ctl.setGravity(Gravity.CENTER_VERTICAL);
                ctl.setPadding(0, px(6), 0, 0);
                r.mute = new Icon(c, Icon.SPEAKER);
                r.mute.filled = true;
                r.mute.setOnClickListener(v -> {
                    final int now = level(r.item);
                    if (now > 0) {
                        prefs.edit().putInt("unmute_level", now).apply();
                        tap(v, SET, idx, 0);
                    } else {
                        tap(v, SET, idx, Math.max(5, prefs.getInt("unmute_level", 70)));
                    }
                });
                final Icon minus = new Icon(c, Icon.MINUS);
                minus.filled = true;
                minus.setContentDescription("Quieter");
                minus.setOnClickListener(v -> tap(v, SET, idx, Math.max(0, level(r.item) - 5)));
                r.bar = new SeekBar(c);
                r.bar.setMax(100);
                r.bar.setContentDescription("Volume");
                r.bar.setProgressTintList(ColorStateList.valueOf(GOLD));
                r.bar.setThumbTintList(ColorStateList.valueOf(GOLD));
                r.bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3A4A86));
                r.bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar b, int p, boolean user) {
                        if (user) GoldBoxActivity.nativeMenu(SET, idx, p);
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar b) { dragging = true; }
                    @Override
                    public void onStopTrackingTouch(SeekBar b) {
                        dragging = false;
                        GoldBoxActivity.nativeMenu(SET, idx, b.getProgress());
                    }
                });
                final Icon plus = new Icon(c, Icon.PLUS);
                plus.filled = true;
                plus.setContentDescription("Louder");
                plus.setOnClickListener(v -> tap(v, SET, idx, Math.min(100, level(r.item) + 5)));
                ctl.addView(r.mute, new LinearLayout.LayoutParams(px(40), px(40)));
                ctl.addView(minus, new LinearLayout.LayoutParams(px(40), px(40)));
                ctl.addView(r.bar, new LinearLayout.LayoutParams(0, px(40), 1));
                ctl.addView(plus, new LinearLayout.LayoutParams(px(40), px(40)));
                r.box.addView(ctl);
                setLevel(r);
            }

            r.help = text(c, 12, MUTED, false);
            r.help.setText(it.help);
            r.help.setPadding(0, px(4), 0, 0);
            r.help.setVisibility(GONE);
            r.box.addView(r.help);

            if (it.kind == 'C') r.box.setOnClickListener(v -> tap(v, CHANGE, idx, 1));
            else if (it.kind != 'L' && it.kind != 'T' && it.kind != 'H') r.box.setOnClickListener(v -> tap(v, CHOOSE, idx, 0));
            r.box.setContentDescription(it.name + (it.value.isEmpty() ? "" : ": " + it.value));
            list.addView(r.box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rows.add(r);
        }
    }

    private static int level(Item it) { return parse(it.value, 0); }

    private void setLevel(Row r) {
        final int v = level(r.item);
        if (r.bar != null && r.bar.getProgress() != v) r.bar.setProgress(v);
        if (r.value != null) r.value.setText(v == 0 ? "Muted" : v + "%");
        if (r.mute != null) {
            r.mute.muted = v == 0;
            r.mute.setContentDescription(v == 0 ? "Unmute" : "Mute");
            r.mute.invalidate();
        }
    }

    private void refresh(List<Item> items) {
        for (Row r : rows) {
            if (r.index >= items.size()) continue;
            final Item it = items.get(r.index);
            r.item = it;
            if (r.toggle != null && r.toggle.on != it.value.equals("On")) {
                r.toggle.on = it.value.equals("On");
                r.toggle.invalidate();
            }
            if (it.kind == 'L') setLevel(r);
            else if (r.value != null) r.value.setText(it.value);
            r.help.setText(it.help);
            r.box.setContentDescription(it.name + (it.value.isEmpty() ? "" : ": " + it.value));
        }
    }

    private void select(int index, boolean rebuilt) {
        if (index == sel && !rebuilt) return;
        sel = index;
        Row lit = null;
        for (Row r : rows) {
            final boolean on = r.index == index;
            if (on) {
                final GradientDrawable d = new GradientDrawable();
                d.setColor(LIT);
                d.setCornerRadius(18 * dp);
                r.box.setBackground(d);
                lit = r;
            } else {
                r.box.setBackground(null);
            }
            // (the volume's controls say it all - its words are a controller's)
            r.help.setVisibility(on && r.item.kind != 'L' && !r.help.getText().toString().isEmpty() ? VISIBLE : GONE);
        }
        if (lit != null && !rebuilt) {
            final View v = lit.box;
            v.post(() -> {
                final int top = v.getTop(), bottom = v.getBottom(), y = scroll.getScrollY(), h = scroll.getHeight();
                if (top < y) scroll.smoothScrollTo(0, top);
                else if (bottom > y + h) scroll.smoothScrollTo(0, bottom - h);
            });
        }
    }

    private void tap(View v, int op, int index, int value) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        GoldBoxActivity.nativeMenu(op, index, value);
    }

    // ---- Small views of its own

    /** The panel: a swipe to the left puts the drawer away. */
    final class SwipePanel extends LinearLayout {
        float downX, downY;
        boolean away;

        SwipePanel(Context c) { super(c); }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    away = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && downX - e.getX() > 48 * dp && Math.abs(e.getY() - downY) < 40 * dp) {
                        away = true;
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (away && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                away = false;
                GoldBoxActivity.nativeMenu(CLOSE, 0, 0);
            }
            return true;
        }
    }

    /** A switch, Material's shape. */
    final class Toggle extends View {
        boolean on;

        Toggle(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas c) {
            final float w = getWidth(), h = getHeight(), r = h / 2;
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(on ? GOLD : 0xFF3A4A86);
            c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
            p.setColor(on ? NAVY : 0xFF9AA3C2);
            c.drawCircle(on ? w - r : r, r, r - 4 * dp, p);
        }
    }

    /** A drawn icon: back, close, next, previous, minus, plus, speaker (muted: crossed). */
    final class Icon extends View {
        static final int BACK = 0, CLOSE = 1, NEXT = 2, PREV = 3, MINUS = 4, PLUS = 5, SPEAKER = 6;
        final int kind;
        boolean filled, muted;

        Icon(Context c, int kind) {
            super(c);
            this.kind = kind;
            setClickable(false);        // (a listener makes it a button)
        }

        @Override
        protected void onDraw(Canvas c) {
            final float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (filled) {
                p.setColor(muted ? GOLD : KEY);
                c.drawCircle(cx, cy, Math.min(w, h) / 2 - 2 * dp, p);
            }
            p.setColor(filled && muted ? NAVY : CREAM);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * dp);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            final float u = 4 * dp;
            final Path path = new Path();
            switch (kind) {
                case BACK:
                    path.moveTo(cx + u, cy - 2 * u);
                    path.lineTo(cx - u, cy);
                    path.lineTo(cx + u, cy + 2 * u);
                    break;
                case PREV:
                    path.moveTo(cx + u * 0.6f, cy - 1.4f * u);
                    path.lineTo(cx - u * 0.8f, cy);
                    path.lineTo(cx + u * 0.6f, cy + 1.4f * u);
                    break;
                case NEXT:
                    if (!isClickable()) p.setColor(MUTED);
                    path.moveTo(cx - u * 0.6f, cy - 1.4f * u);
                    path.lineTo(cx + u * 0.8f, cy);
                    path.lineTo(cx - u * 0.6f, cy + 1.4f * u);
                    break;
                case CLOSE:
                    path.moveTo(cx - 1.5f * u, cy - 1.5f * u);
                    path.lineTo(cx + 1.5f * u, cy + 1.5f * u);
                    path.moveTo(cx + 1.5f * u, cy - 1.5f * u);
                    path.lineTo(cx - 1.5f * u, cy + 1.5f * u);
                    break;
                case MINUS:
                    path.moveTo(cx - 1.5f * u, cy);
                    path.lineTo(cx + 1.5f * u, cy);
                    break;
                case PLUS:
                    path.moveTo(cx - 1.5f * u, cy);
                    path.lineTo(cx + 1.5f * u, cy);
                    path.moveTo(cx, cy - 1.5f * u);
                    path.lineTo(cx, cy + 1.5f * u);
                    break;
                case SPEAKER:
                    path.moveTo(cx - 2.2f * u, cy - 0.8f * u);
                    path.lineTo(cx - 1.2f * u, cy - 0.8f * u);
                    path.lineTo(cx + 0.2f * u, cy - 2f * u);
                    path.lineTo(cx + 0.2f * u, cy + 2f * u);
                    path.lineTo(cx - 1.2f * u, cy + 0.8f * u);
                    path.lineTo(cx - 2.2f * u, cy + 0.8f * u);
                    path.close();
                    if (muted) {
                        path.moveTo(cx + 1f * u, cy - 1f * u);
                        path.lineTo(cx + 2.4f * u, cy + 1f * u);
                        path.moveTo(cx + 2.4f * u, cy - 1f * u);
                        path.lineTo(cx + 1f * u, cy + 1f * u);
                    } else {
                        path.moveTo(cx + 1f * u, cy - 0.9f * u);
                        path.quadTo(cx + 1.9f * u, cy, cx + 1f * u, cy + 0.9f * u);
                    }
                    break;
                default:
                    break;
            }
            c.drawPath(path, p);
        }
    }
}
