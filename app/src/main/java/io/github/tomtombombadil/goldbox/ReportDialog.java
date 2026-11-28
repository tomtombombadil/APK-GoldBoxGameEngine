package io.github.tomtombombadil.goldbox;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Report a Problem (Tom, 2026-11-28 - the mock-up he approved): the game and where in it filled in by the app, the
 * player's answers, a box to include a save of the game as it is (made for the report alone), what is sent said
 * plainly - then Send via Email (Report.send). Android's own window over the game: its keyboard, a controller's D-pad
 * between the fields, Back closes it.
 */
final class ReportDialog {
    private ReportDialog() {}

    static void show(Activity a, Report r) {
        final float dp = Screens.dp(a);
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setOnCancelListener(x -> r.clearSave());

        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        final int p = Math.round(24 * dp);
        card.setPadding(p, p, p, Math.round(16 * dp));

        final TextView title = Screens.text(a, 20, Screens.CREAM, false);
        title.setText("Report a Problem");
        card.addView(title);
        final TextView intro = Screens.text(a, 14, 0xFFC8D0EA, false);
        intro.setLineSpacing(0, 1.15f);
        intro.setPadding(0, Math.round(8 * dp), 0, Math.round(14 * dp));
        intro.setText("Your answers help the developer find it. Answer what you can - every one is optional, but a few "
                + "words on what happened help most.");
        card.addView(intro);

        // The game (the open one, else the chooser's) and where in it
        final LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView game = field(a, false);
        game.setText((r.game.isEmpty() ? "None" : r.game) + "  ▾");
        game.setCompoundDrawablePadding(Math.round(6 * dp));
        game.setFocusable(true);
        game.setOnClickListener(v -> {
            final PopupMenu m = new PopupMenu(new android.view.ContextThemeWrapper(a, android.R.style.Theme_Material), v);
            m.getMenu().add(0, 0, 0, "None");
            for (int i = 0; i < r.games.length; ++i) m.getMenu().add(0, i + 1, i + 1, r.games[i]);
            m.setOnMenuItemClickListener(it -> {
                r.game = it.getItemId() == 0 ? "" : r.games[it.getItemId() - 1];
                game.setText((r.game.isEmpty() ? "None" : r.game) + "  ▾");
                return true;
            });
            m.show();
        });
        final EditText where = (EditText) field(a, true);
        where.setSingleLine(true);
        where.setHint("An area, a town, a square");
        where.setText(r.whereGuess());
        row.addView(labelled(a, "Game", game), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        wl.leftMargin = Math.round(10 * dp);
        row.addView(labelled(a, "Where in the game", where), wl);
        card.addView(row);

        final EditText happened = area(a, "The fight froze after the second round ...", 3);
        card.addView(labelled(a, "What happened?", happened));
        final EditText expected = area(a, "", 2);
        card.addView(labelled(a, "What should have happened instead?", expected));
        final EditText other = area(a, "What you did just before, how often it happens", 2);
        card.addView(labelled(a, "Anything else?", other));

        // The save of the game as it is
        final CheckBox save = new CheckBox(a);
        save.setText(r.saveAvailable() ? "Include a Save of the Current Game"
                : "Include a Save of the Current Game (no game in play)");
        save.setTextColor(r.saveAvailable() ? Screens.CREAM : Screens.MUTED);
        save.setTextSize(14);
        save.setButtonTintList(ColorStateList.valueOf(Screens.GOLD));
        save.setEnabled(r.saveAvailable());
        save.setChecked(r.saveAvailable());
        save.setPadding(Math.round(4 * dp), 0, 0, 0);
        final TextView saveNote = Screens.text(a, 12, Screens.MUTED, false);
        saveNote.setText("Made just now for this report - none of your save slots is used or changed.");
        saveNote.setPadding(Math.round(36 * dp), 0, 0, Math.round(10 * dp));
        card.addView(save);
        if (r.saveAvailable()) card.addView(saveNote);

        // What is sent, said plainly
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Screens.round(Screens.SURFACE, 12 * dp));
        final int bp = Math.round(12 * dp);
        box.setPadding(bp, Math.round(10 * dp), bp, Math.round(10 * dp));
        final TextView what = Screens.text(a, 13, 0xFFC8D0EA, false);
        what.setLineSpacing(0, 1.15f);
        what.setText("No personal information is sent. Your email app opens with your answers and one zip: the app's "
                + "log, its settings, the engine's notes and the device's details (make and model, Android version, "
                + "screen, memory, app versions). Never your game files or saves - only the save above, if ticked.");
        box.addView(what);
        final TextView see = Screens.text(a, 13, Screens.GOLD, true);
        see.setText("See What's Sent");
        see.setPadding(0, Math.round(8 * dp), 0, 0);
        see.setFocusable(true);
        final TextView list = Screens.text(a, 12, Screens.MUTED, false);
        list.setVisibility(View.GONE);
        list.setPadding(0, Math.round(6 * dp), 0, 0);
        see.setOnClickListener(v -> {
            if (list.getVisibility() == View.VISIBLE) {
                list.setVisibility(View.GONE);
                see.setText("See What's Sent");
                return;
            }
            r.withSave = save.isChecked();
            final StringBuilder b = new StringBuilder("REPORT.TXT - your answers\nDEVICE.TXT - the device's details and "
                    + "the log's last lines\n");
            for (String[] f : r.files()) b.append(f[1]).append('\n');
            list.setText(b.toString().trim());
            list.setVisibility(View.VISIBLE);
            see.setText("Hide the List");
        });
        box.addView(see);
        box.addView(list);
        card.addView(box);

        final TextView nudge = Screens.text(a, 13, 0xFFE8B840, false);
        nudge.setPadding(Math.round(4 * dp), Math.round(10 * dp), 0, 0);
        nudge.setText("A few words on what happened help the most - or tap Send via Email again to send without.");
        nudge.setVisibility(View.GONE);
        card.addView(nudge);

        final LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, Math.round(16 * dp), 0, 0);
        buttons.addView(Screens.button(a, "Cancel", false, v -> d.cancel()));
        final TextView[] send = new TextView[1];
        send[0] = Screens.button(a, "Send via Email", true, v -> {
            if (happened.getText().toString().trim().isEmpty() && nudge.getVisibility() != View.VISIBLE) {
                nudge.setVisibility(View.VISIBLE);
                happened.requestFocus();
                return;
            }
            r.where = where.getText().toString().trim();
            r.happened = happened.getText().toString().trim();
            r.expected = expected.getText().toString().trim();
            r.other = other.getText().toString().trim();
            r.withSave = save.isChecked();
            send[0].setEnabled(false);
            send[0].setText("Preparing…");
            new Thread(() -> {
                File zip = null;
                try {
                    zip = r.makeZip(a);
                } catch (Exception e) {
                    android.util.Log.w("goldbox", "report zip", e);
                }
                final File z = zip;
                a.runOnUiThread(() -> {
                    d.dismiss();
                    final String say = z == null ? "The report couldn't be put together (the app's storage may be full)."
                            : r.send(a, z);
                    if (say != null) message(a, say);
                });
            }, "report").start();
        });
        final LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.leftMargin = Math.round(10 * dp);
        buttons.addView(send[0], sl);
        card.addView(buttons);

        final ScrollView sv = new ScrollView(a);
        sv.setBackground(Screens.round(Screens.PANEL, 28 * dp));
        sv.addView(card);
        d.setContentView(sv);
        final Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            final android.util.DisplayMetrics m = a.getResources().getDisplayMetrics();
            w.setLayout(Math.min(Math.round(520 * dp), m.widthPixels - Math.round(32 * dp)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        d.show();
    }

    private static TextView field(Activity a, boolean edit) {
        final float dp = Screens.dp(a);
        final TextView f = edit ? new EditText(a) : Screens.text(a, 15, Screens.CREAM, false);
        f.setTextColor(Screens.CREAM);
        f.setHintTextColor(0xFF7A84AC);
        f.setTextSize(15);
        final GradientDrawable box = Screens.round(Screens.SURFACE, 12 * dp);
        box.setStroke(Math.round(1.2f * dp), Screens.EDGE);
        f.setBackground(box);
        final int fp = Math.round(12 * dp);
        f.setPadding(fp, Math.round(10 * dp), fp, Math.round(10 * dp));
        f.setMinHeight(Math.round(44 * dp));
        f.setGravity(Gravity.CENTER_VERTICAL);
        return f;
    }

    private static EditText area(Activity a, String hint, int lines) {
        final EditText e = (EditText) field(a, true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setHint(hint);
        return e;
    }

    private static View labelled(Activity a, String label, View v) {
        final float dp = Screens.dp(a);
        final LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, 0, 0, Math.round(12 * dp));
        final TextView t = Screens.text(a, 12, 0xFF9AA3C8, false);
        t.setText(label);
        t.setPadding(0, 0, 0, Math.round(4 * dp));
        l.addView(t);
        l.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** What became of it, when no app took it (the zip saved instead, or nothing to be done). */
    static void message(Activity a, String text) {
        final float dp = Screens.dp(a);
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Screens.round(Screens.PANEL, 28 * dp));
        final int p = Math.round(24 * dp);
        card.setPadding(p, p, p, Math.round(16 * dp));
        final TextView t = Screens.text(a, 20, Screens.CREAM, false);
        t.setText("Report a Problem");
        card.addView(t);
        final TextView b = Screens.text(a, 15, 0xFFC8D0EA, false);
        b.setLineSpacing(0, 1.15f);
        b.setPadding(0, Math.round(14 * dp), 0, Math.round(8 * dp));
        b.setText(text);
        card.addView(b);
        final LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.END);
        row.addView(Screens.button(a, "Close", true, v -> d.dismiss()));
        card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(card);
        final Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(Math.min(Math.round(420 * dp), a.getResources().getDisplayMetrics().widthPixels
                    - Math.round(40 * dp)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }
}
