package io.github.tomtombombadil.goldbox;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The folder screen: where the player's games are.
 *
 * The first start shows it; after that the app goes straight to the game
 * list unless the folder is gone or can't be read - or the player chose
 * Start - System - Games Folder in the game (GoldBoxActivity.chooseFolder).
 *
 * Tom, 2026-11-23: "Where do the game files go? It says GoldBox/games but that
 * doesn't tell me where that folder is on the device" - so the folder is named
 * as the phone's file manager and a Windows PC over USB show it ("Internal
 * storage > GoldBox > games"); and "It needs to allow the USER to point the app
 * to the goldbox folder of their own creation" - Choose Folder.
 *
 * Three kinds of folder:
 *   default - "GoldBox" on the device's own storage
 *   chosen  - any folder the player picks (the device's storage or an SD card)
 *   private - the app's own folder (no permission; gone with the app)
 * The first two need All Files Access (Android 11 on; storage permission
 * before): the games are read by the engine's ordinary file calls.
 *
 * Runs in its own process (":setup", the manifest): the game's process
 * ends when the game does (GoldBoxActivity) and must not take this with it.
 */
public class SetupActivity extends Activity {

    static final String PREFS = "goldbox";
    static final String KIND = "kind";          // "default", "chosen", "private"
    static final String FOLDER = "folder";      // the chosen folder's path
    static final String EXTRA_CHANGE = "change";
    static final String EXTRA_GAMES = "games", EXTRA_CONF = "conf", EXTRA_LOG = "log";

    private static final int ASK_ACCESS = 1, ASK_STORAGE = 2, PICK_FOLDER = 3;
    private static final int NAVY = 0xFF101838, GOLD = 0xFFE8B840, CREAM = 0xFFF0E6C8,
        MUTED = 0xFF9AA0B8, WARN = 0xFFE86A50, LIT = 0xFF96701E, KEY = 0xFF202A50;

    private Runnable afterAccess;       // what was waiting for All Files Access
    private TextView status;
    private String problem;             // a message about the last choice (null: none)

    // ---- Where things are (also read by GoldBoxActivity) ----------------------

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    static File defaultFolder() {
        return new File(Environment.getExternalStorageDirectory(), "GoldBox");
    }

    static File privateFolder(Context c) {
        File own = c.getExternalFilesDir(null);
        return own != null ? own : c.getFilesDir();
    }

    /** The Gold Box folder: games/, conf/ and log.txt are in it. */
    static File folder(Context c) {
        final SharedPreferences p = prefs(c);
        switch (p.getString(KIND, "")) {
            case "chosen": return new File(p.getString(FOLDER, defaultFolder().getPath()));
            case "private": return privateFolder(c);
            default: return defaultFolder();
        }
    }

    static boolean needsAccess(Context c) {
        return !"private".equals(prefs(c).getString(KIND, ""));
    }

    /**
     * The games folder in a Gold Box folder: its "games" folder - or, when the
     * player chose the folder that holds the games themselves, that folder.
     */
    static File games(File folder) {
        final File sub = new File(folder, "games");
        if (sub.isDirectory()) return sub;
        if (folder.getName().equalsIgnoreCase("games") || countGames(folder) > 0) return folder;
        return sub;
    }

    /**
     * The game folders in a games folder - as the front end's scan finds them (its library.cpp): the game's files
     * (*.DAX) in the folder or up to two folders down (a GOG install: one; a Steam / SNEG one: <folder>/GAME/<SHORT>
     * - Tom, 2026-11-25: all nine Steam games were "No games in it yet"), else a Krynn's *.TLB / *.GLB up to three
     * down. Not the engine's own folders (_CYD ...), conf, nor the Steam collection's Launcher and GBC folders.
     */
    static int countGames(File games) {
        final File[] dirs = games.listFiles(File::isDirectory);
        if (dirs == null) return 0;
        int n = 0;
        for (File d : dirs) {
            final String name = d.getName();
            if (name.startsWith("_") || name.equalsIgnoreCase("conf") || name.equalsIgnoreCase("Launcher")
                    || name.equalsIgnoreCase("GBC"))
                continue;
            if (has(d, 2, ".dax") || has(d, 3, ".tlb", ".glb")) ++n;
        }
        return n;
    }

    /** A file ending so in dir or up to depth folders below it. */
    private static boolean has(File dir, int depth, String... ends) {
        final File[] in = dir.listFiles();
        if (in == null) return false;
        for (File f : in) {
            if (!f.isFile()) continue;
            final String n = f.getName().toLowerCase(Locale.ROOT);
            for (String e : ends)
                if (n.endsWith(e)) return true;
        }
        if (depth > 0)
            for (File f : in)
                if (f.isDirectory() && has(f, depth - 1, ends)) return true;
        return false;
    }

    static boolean haveAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23)
            return c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    /** A path as the phone's file manager and a PC over USB name it. */
    static String shown(String path) {
        final String own = Environment.getExternalStorageDirectory().getPath();
        if (path.startsWith(own)) return "Internal storage" + path.substring(own.length()).replace("/", " › ");
        if (path.startsWith("/storage/")) {
            final int end = path.indexOf('/', 9);
            return "SD card" + (end < 0 ? "" : path.substring(end).replace("/", " › "));
        }
        return path;
    }

    // ---- The screen ----------------------------------------------------------

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        final boolean change = getIntent() != null && getIntent().getBooleanExtra(EXTRA_CHANGE, false);
        if (!change && ready()) {
            play();
            return;
        }
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) build();
    }

    /** Chosen before, and usable now. */
    private boolean ready() {
        if (!prefs(this).contains(KIND)) return false;
        if (needsAccess(this) && !haveAccess(this)) return false;
        final File f = folder(this);
        return f.isDirectory() || f.mkdirs();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(LinearLayout into, String s, int colour, int sp, boolean bold, int top) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        into.addView(t, lp);
        return t;
    }

    private void button(LinearLayout into, String s, boolean main, View.OnClickListener go) {
        final Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, main ? 20 : 16);
        b.setTextColor(main ? NAVY : CREAM);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(main ? GOLD : KEY);
        bg.setStroke(dp(1), main ? GOLD : LIT);
        bg.setCornerRadius(dp(6));
        b.setBackground(bg);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setOnClickListener(go);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(main ? 20 : 10);
        into.addView(b, lp);
    }

    private void build() {
        final ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(NAVY);
        scroll.setFillViewport(true);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(24), dp(24), dp(24));
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(col);

        text(col, "Gold Box Game Engine", GOLD, 26, true, 0);
        text(col, "Plays SSI's Gold Box games from your own copies (GOG or Steam). Nothing from the games comes "
            + "with the app.", CREAM, 15, false, 8);

        final boolean chosen = prefs(this).contains(KIND);
        final File folder = folder(this);
        final File games = games(folder);
        text(col, chosen ? "Your Gold Box folder" : "Where your games go", GOLD, 18, true, 24);
        text(col, shown(folder.getPath()), CREAM, 18, false, 4);
        text(col, "Copy each game's whole folder into:", MUTED, 14, false, 12);
        text(col, shown(games.getPath()), CREAM, 16, false, 2);
        text(col, "one folder a game, for example … › games › Pool of Radiance. With the Steam "
            + "release, its Launcher folder too (box art).", MUTED, 14, false, 4);

        status = text(col, "", WARN, 15, false, 16);
        String say;
        if (problem != null) say = problem;
        else if (needsAccess(this) && !haveAccess(this))
            say = "The app needs All Files Access to read this folder: it asks when you press Play. It reads and "
                + "writes only in the folder you give it.";
        else if (!games.isDirectory()) say = "The games folder will be made when you press Play.";
        else {
            final int n = countGames(games);
            say = n == 0 ? "No games in it yet." : n == 1 ? "1 game folder found." : n + " game folders found.";
            status.setTextColor(n == 0 ? WARN : GOLD);
        }
        status.setText(say);

        button(col, "Play", true, v -> start());
        button(col, "Choose Folder…", false, v -> withAccess(this::pick));
        button(col, "Use the Default Folder (" + shown(defaultFolder().getPath()) + ")", false, v -> {
            problem = null;
            prefs(this).edit().putString(KIND, "default").apply();
            build();
        });
        button(col, "Use the App's Private Folder", false, v -> {
            problem = null;
            prefs(this).edit().putString(KIND, "private").apply();
            build();
        });

        text(col, "From a Windows PC: connect the phone by USB and choose File Transfer on the phone; then in File "
            + "Explorer open the phone, " + shown(games.getPath()) + ", and copy the game folders in.",
            MUTED, 13, false, 24);
        text(col, "The App's Private Folder needs no permission, but most phones' file managers can't open it, "
            + "and Android deletes it - saved games too - when the app is uninstalled. On Android 10 and older "
            + "the folder must be on the device's own storage, not an SD card.", MUTED, 13, false, 8);
        setContentView(scroll);
    }

    // ---- Doing it -------------------------------------------------------------

    private void start() {
        if (!prefs(this).contains(KIND)) prefs(this).edit().putString(KIND, "default").apply();
        if (needsAccess(this) && !haveAccess(this)) {
            withAccess(this::start);
            return;
        }
        final File folder = folder(this);
        final File games = games(folder);
        if (!games.isDirectory() && !games.mkdirs()) {
            problem = "That folder can't be written: choose another one (or the Default Folder).";
            build();
            return;
        }
        play();
    }

    /** Straight to the game: the folders made, a README for the player. */
    private void play() {
        final File folder = folder(this);
        final File games = games(folder);
        games.mkdirs();
        new File(folder, "conf").mkdirs();
        final File readme = new File(games, "README.txt");
        if (!readme.exists() && countGames(games) == 0) {
            try (FileOutputStream o = new FileOutputStream(readme)) {
                o.write(("Copy each Gold Box game's whole folder (GOG or Steam) in here, one folder a game,\r\n"
                    + "keeping the game's name in the folder's name - for example:\r\n\r\n"
                    + "    games\\Pool of Radiance\\\r\n"
                    + "    games\\Curse of the Azure Bonds\\\r\n\r\n"
                    + "With the Steam release, copy its Launcher folder here too (games\\Launcher\\).\r\n"
                    + "Then open the app, or tap the game list's screen (Look Again).\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        final Intent i = new Intent(this, GoldBoxActivity.class);
        i.putExtra(EXTRA_GAMES, games.getAbsolutePath());
        i.putExtra(EXTRA_CONF, new File(folder, "conf").getAbsolutePath());
        i.putExtra(EXTRA_LOG, new File(folder, "log.txt").getAbsolutePath());
        startActivity(i);
        finish();
    }

    private void withAccess(Runnable then) {
        if (haveAccess(this)) {
            then.run();
            return;
        }
        afterAccess = then;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())), ASK_ACCESS);
            } catch (ActivityNotFoundException e) {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), ASK_ACCESS);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, ASK_STORAGE);
        }
    }

    private void accessAnswered() {
        final Runnable then = afterAccess;
        afterAccess = null;
        if (haveAccess(this)) {
            problem = null;
            if (then != null) then.run();
        } else {
            problem = "Without All Files Access the app can only use its private folder (below).";
            build();
        }
    }

    private void pick() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
        } catch (ActivityNotFoundException e) {
            problem = "This device has no folder chooser. Use the Default Folder, and copy the games into it.";
            build();
        }
    }

    /** The folder chooser's answer, as a path the engine can open. */
    private void picked(Uri tree) {
        String path = null;
        if (tree != null && "com.android.externalstorage.documents".equals(tree.getAuthority())) {
            // "primary:Games/GoldBox" -> the device's storage; "1A2B-3C4D:GoldBox" -> that SD card
            final String id = DocumentsContract.getTreeDocumentId(tree);
            final int colon = id.indexOf(':');
            final String volume = colon < 0 ? id : id.substring(0, colon);
            final String rel = colon < 0 ? "" : id.substring(colon + 1);
            final String base = volume.equalsIgnoreCase("primary")
                ? Environment.getExternalStorageDirectory().getPath() : "/storage/" + volume;
            path = rel.isEmpty() ? base : base + "/" + rel;
        }
        if (path == null) {
            problem = "Choose a folder on the device's storage or its SD card (not Downloads' shortcuts or a "
                + "cloud drive).";
        } else if (!new File(path).isDirectory()) {
            problem = "The app can't open " + shown(path) + ".";
        } else {
            final File conf = new File(path, "conf");
            boolean writable = conf.isDirectory() || conf.mkdirs();
            if (writable) {
                try {
                    final File probe = new File(conf, ".write-test");
                    new FileOutputStream(probe).close();
                    probe.delete();
                } catch (Exception e) {
                    writable = false;
                }
            }
            if (!writable) {
                problem = "Android doesn't let the app write in " + shown(path) + " (the scan keeps what it makes "
                    + "beside the games). Choose a folder on the device's own storage.";
            } else {
                problem = null;
                prefs(this).edit().putString(KIND, "chosen").putString(FOLDER, path).apply();
            }
        }
        build();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ASK_ACCESS) accessAnswered();
        else if (request == PICK_FOLDER && result == RESULT_OK && data != null) picked(data.getData());
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] perms, int[] results) {
        super.onRequestPermissionsResult(request, perms, results);
        if (request == ASK_STORAGE) accessAnswered();
    }
}
