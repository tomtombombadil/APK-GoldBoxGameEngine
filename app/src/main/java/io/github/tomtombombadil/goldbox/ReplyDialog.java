package io.github.tomtombombadil.goldbox;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Menu › System › Reply Address, and Send Logs' first time: Android's own box and keyboard for the player's email
 * address (the handhelds' on-screen keyboard is the front end's; here the app's views, never SDL's). Its words go
 * back by nativeText - op 16 (just the address) or 17 (the address, then the logs go). Empty is "No Thanks" /
 * "Clear", as the handhelds' keyboard has it; Cancel or Back changes nothing.
 */
final class ReplyDialog {
    static final int OP_REPLY = 16, OP_REPLY_SEND = 17;

    private ReplyDialog() {}

    static void show(Activity a, String now, boolean thenSend) {
        final float dp = Screens.dp(a);
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Screens.round(Screens.PANEL, 28 * dp));
        final int p = Math.round(24 * dp);
        card.setPadding(p, p, p, Math.round(16 * dp));

        final TextView title = Screens.text(a, 20, Screens.CREAM, false);
        title.setText("Reply Address");
        card.addView(title);
        final TextView body = Screens.text(a, 15, 0xFFC8D0EA, false);
        body.setLineSpacing(0, 1.15f);
        body.setPadding(0, Math.round(14 * dp), 0, Math.round(14 * dp));
        body.setText(thenSend
                ? "Your email address goes with the logs, so the developer can write back. It's optional - leave it "
                        + "empty for No Thanks."
                : "Your email address, sent with Send Logs so the developer can write back. Optional - leave it "
                        + "empty to clear it.");
        card.addView(body);

        final EditText field = new EditText(a);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setHint("name@example.com");
        field.setTextColor(Screens.CREAM);
        field.setHintTextColor(Screens.MUTED);
        field.setTextSize(17);
        final GradientDrawable box = Screens.round(Screens.SURFACE, 12 * dp);
        box.setStroke(Math.round(1.2f * dp), Screens.EDGE);
        field.setBackground(box);
        final int fp = Math.round(14 * dp);
        field.setPadding(fp, fp, fp, fp);
        field.setText(now == null ? "" : now);
        field.setSelection(field.getText().length());
        card.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView note = Screens.text(a, 13, 0xFFE07A6A, false);
        note.setPadding(Math.round(4 * dp), Math.round(6 * dp), 0, 0);
        note.setText("That isn't an email address yet.");
        note.setVisibility(TextView.INVISIBLE);
        card.addView(note);

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, Math.round(12 * dp), 0, 0);
        final Runnable done = () -> {
            final String t = field.getText().toString().trim();
            if (!t.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(t).matches()) {
                note.setVisibility(TextView.VISIBLE);
                return;
            }
            d.dismiss();
            GoldBoxActivity.nativeText(thenSend ? OP_REPLY_SEND : OP_REPLY, t);
        };
        final TextView cancel = Screens.button(a, "Cancel", false, v -> d.dismiss());
        buttons.addView(cancel);
        final TextView ok = Screens.button(a, okLabel(now, thenSend), true, v -> done.run());
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.round(10 * dp);
        buttons.addView(ok, lp);
        card.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                note.setVisibility(TextView.INVISIBLE);
                ok.setText(okLabel(s.toString().trim(), thenSend));
            }
        });
        field.setOnEditorActionListener((v, id, ev) -> {
            if (id != EditorInfo.IME_ACTION_DONE) return false;
            done.run();
            return true;
        });

        d.setContentView(card);
        final Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(Math.min(Math.round(420 * dp), a.getResources().getDisplayMetrics().widthPixels
                    - Math.round(40 * dp)), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        field.requestFocus();
    }

    /** The gold button's word: Send (Send Logs' first time) / Save; with nothing typed, No Thanks / Clear. */
    private static String okLabel(String t, boolean thenSend) {
        final boolean empty = t == null || t.isEmpty();
        if (thenSend) return empty ? "No Thanks" : "Send";
        return empty ? "Clear" : "Save";
    }
}
