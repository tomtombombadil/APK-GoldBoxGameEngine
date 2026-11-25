package io.github.tomtombombadil.goldbox;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;

/**
 * The port's own small screens in Android's views (Tom's redesign, stage 3): a
 * notice or message as a dialog card, the scan's page. Their words come from the
 * front end (main.cpp android_notice_text / android_scan_text); their buttons are
 * the front end's A and B, as a controller's would be.
 */
final class Screens {

    private Screens() {}

    static final int NAVY = Shell.NAVY, SURFACE = Shell.SURFACE, KEY = Shell.KEY, EDGE = Shell.EDGE,
            GOLD = Shell.GOLD, CREAM = Shell.CREAM, MUTED = Shell.MUTED, PANEL = 0xFF1A2244;

    static float dp(Context c) { return c.getResources().getDisplayMetrics().density; }

    static TextView text(Context c, float sp, int colour, boolean medium) {
        final TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    static GradientDrawable round(int colour, float radius) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(radius);
        return d;
    }

    /** A Material button: filled (gold) or outlined. */
    static TextView button(Context c, String label, boolean filled, View.OnClickListener l) {
        final float dp = dp(c);
        final TextView b = text(c, 15, filled ? NAVY : CREAM, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Math.round(48 * dp));
        b.setPadding(Math.round(22 * dp), 0, Math.round(22 * dp), 0);
        final GradientDrawable d = round(filled ? GOLD : 0x00000000, 24 * dp);
        if (!filled) d.setStroke(Math.round(1.2f * dp), EDGE);
        b.setBackground(d);
        b.setClickable(true);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            l.onClick(v);
        });
        return b;
    }

    static void press(int button) {
        GoldBoxActivity.nativeButton(button, true);
        GoldBoxActivity.nativeButton(button, false);
    }

    /** A notice (a question, a result, Please wait) or a message: a card over a dimmed screen. */
    static final class NoticeView extends FrameLayout {
        private final TextView title, body, wait;
        private final ProgressBar spin;
        private final LinearLayout buttons;
        private ScrollView bodyBox;
        private boolean up = false;
        private String last = "";

        NoticeView(Context c) {
            super(c);
            final float dp = dp(c);
            setVisibility(GONE);
            setBackgroundColor(0xA604060E);
            setClickable(true);         // (the game under it gets nothing)

            final LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(round(PANEL, 28 * dp));
            final int p = Math.round(24 * dp);
            card.setPadding(p, p, p, Math.round(16 * dp));
            final LayoutParams lp = new LayoutParams(Math.round(400 * dp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            lp.leftMargin = lp.rightMargin = Math.round(20 * dp);
            addView(card, lp);

            title = text(c, 20, CREAM, false);
            card.addView(title);
            final ScrollView sv = new ScrollView(c);
            body = text(c, 15, 0xFFC8D0EA, false);
            body.setLineSpacing(0, 1.15f);
            body.setPadding(0, Math.round(14 * dp), 0, Math.round(8 * dp));
            sv.addView(body);
            card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            bodyBox = sv;

            final LinearLayout waitRow = new LinearLayout(c);
            waitRow.setOrientation(LinearLayout.HORIZONTAL);
            waitRow.setGravity(Gravity.CENTER_VERTICAL);
            spin = new ProgressBar(c);
            spin.setIndeterminate(true);
            waitRow.addView(spin, new LinearLayout.LayoutParams(Math.round(28 * dp), Math.round(28 * dp)));
            wait = text(c, 14, MUTED, false);
            wait.setText("Please wait…");
            wait.setPadding(Math.round(12 * dp), 0, 0, 0);
            waitRow.addView(wait);
            card.addView(waitRow);

            buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            buttons.setPadding(0, Math.round(12 * dp), 0, 0);
            card.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        boolean isUp() { return up; }

        @Override
        protected void onMeasure(int w, int h) {
            // (a long message scrolls inside the card: the card at most the screen's height less a margin)
            final int max = MeasureSpec.getSize(h) - Math.round(220 * dp(getContext()));
            if (bodyBox != null && max > 0) {
                bodyBox.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(w), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                final ViewGroup.LayoutParams lp = bodyBox.getLayoutParams();
                final int want = bodyBox.getMeasuredHeight() > max ? max : ViewGroup.LayoutParams.WRAP_CONTENT;
                if (lp.height != want) lp.height = want;
            }
            super.onMeasure(w, h);
        }

        void update(byte[] bytes) {
            final String s = new String(bytes, StandardCharsets.UTF_8);
            if (s.equals(last)) return;
            last = s;
            final int nl = s.indexOf('\n');
            final String[] head = (nl < 0 ? s : s.substring(0, nl)).split("\t", -1);
            if (!head[0].equals("1")) {
                up = false;
                setVisibility(GONE);
                return;
            }
            final String yes = head.length > 2 ? head[2] : "", no = head.length > 3 ? head[3] : "";
            final boolean waiting = head.length > 4 && head[4].equals("1");
            title.setText(head.length > 1 ? head[1] : "");
            body.setText(nl < 0 ? "" : s.substring(nl + 1));
            final Context c = getContext();
            final float dp = dp(c);
            buttons.removeAllViews();
            spin.setVisibility(waiting ? VISIBLE : GONE);
            wait.setVisibility(waiting ? VISIBLE : GONE);
            if (!waiting) {
                // (Confirm = the front end's A, Back = its B; a notice with one word: either closes it)
                if (!no.isEmpty()) {
                    final TextView b = button(c, no, yes.isEmpty(), v -> press(yes.isEmpty() ? Shell.A : Shell.B));
                    buttons.addView(b);
                }
                if (!yes.isEmpty()) {
                    final TextView b = button(c, yes, true, v -> press(Shell.A));
                    final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.leftMargin = Math.round(10 * dp);
                    buttons.addView(b, lp);
                }
            }
            if (!up) {
                up = true;
                setVisibility(VISIBLE);
                setAlpha(0f);
                animate().alpha(1f).setDuration(150).start();
            }
        }
    }

    /** The scan's page (finding the games, Clean Up Game Files): its lines, then Continue. */
    static final class ScanView extends LinearLayout {
        private final TextView title, foot;
        private final LinearLayout list;
        private final ScrollView scroll;
        private final FrameLayout footBox;
        private TextView go;
        private boolean up = false;
        private String last = "";

        ScanView(Context c) {
            super(c);
            final float dp = dp(c);
            setOrientation(VERTICAL);
            setBackgroundColor(NAVY);
            setVisibility(GONE);
            setClickable(true);

            final LinearLayout bar = new LinearLayout(c);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Math.round(24 * dp), 0, Math.round(24 * dp), 0);
            title = text(c, 22, CREAM, false);
            bar.addView(title);
            addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(64 * dp)));

            scroll = new ScrollView(c);
            list = new LinearLayout(c);
            list.setOrientation(VERTICAL);
            list.setPadding(Math.round(24 * dp), 0, Math.round(24 * dp), Math.round(12 * dp));
            scroll.addView(list);
            addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            footBox = new FrameLayout(c);
            footBox.setPadding(Math.round(24 * dp), Math.round(10 * dp), Math.round(24 * dp), Math.round(18 * dp));
            foot = text(c, 14, MUTED, false);
            foot.setText("Working - this can take minutes.");
            footBox.addView(foot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            addView(footBox, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        boolean isUp() { return up; }

        void update(byte[] bytes) {
            final String s = new String(bytes, StandardCharsets.UTF_8);
            if (s.equals(last)) return;
            last = s;
            final String[] lines = s.split("\n", -1);
            final String[] head = lines[0].split("\t", -1);
            if (!head[0].equals("scan")) {
                up = false;
                setVisibility(GONE);
                return;
            }
            up = true;
            setVisibility(VISIBLE);
            title.setText(head.length > 1 ? head[1] : "");
            final boolean done = head.length > 2 && head[2].equals("1");
            final Context c = getContext();
            final float dp = dp(c);
            // (the lines as the scan wrote them: "Found ..." gold, what is made for it under it, indented)
            final int have = list.getChildCount();
            for (int i = 1; i < lines.length; ++i) {
                final String l = lines[i];
                int indent = 0;
                while (indent < l.length() && l.charAt(indent) == ' ') ++indent;
                final boolean found = l.startsWith("Found");
                TextView t;
                if (i - 1 < have) {
                    t = (TextView) list.getChildAt(i - 1);
                } else {
                    t = text(c, 14, CREAM, false);
                    list.addView(t);
                }
                t.setText(l.trim());
                t.setTextColor(found ? GOLD : indent >= 4 ? MUTED : CREAM);
                t.setTypeface(Typeface.create(found ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, found ? 16 : 14);
                t.setPadding(Math.round(indent * 6 * dp), Math.round((found ? 12 : 2) * dp), 0, Math.round(2 * dp));
            }
            for (int k = list.getChildCount() - 1; k >= lines.length - 1; --k) list.removeViewAt(k);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            if (done && go == null) {
                foot.setVisibility(GONE);
                go = button(c, "Continue", true, v -> press(Shell.A));
                footBox.addView(go, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END));
            } else if (!done && go != null) {
                footBox.removeView(go);
                go = null;
                foot.setVisibility(VISIBLE);
            }
        }
    }
}
