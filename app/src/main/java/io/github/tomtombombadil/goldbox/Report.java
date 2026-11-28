package io.github.tomtombombadil.goldbox;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Report a Problem (Tom, 2026-11-28; the Android app's alone - the handhelds keep Send Logs by the relay): what the
 * front end knows (ReportDialog fills its fields from it), the zip - the log, settings, the engine's notes,
 * DEVICE.TXT (what the relay's email body holds on a handheld), REPORT.TXT (the player's answers), and the save made
 * for the report if ticked - and the email intent: email apps only, none - Android's Share menu, nothing at all - the
 * zip in the GoldBox folder.
 */
final class Report {
    static final String TO = "cyd.classic.games.logs@gmail.com";

    String game = "", area = "", square = "", save = "", front = "?", engine = "?", app = "?";
    String[] games = new String[0];
    String gamesDir, confDir, logFile;

    // the player's answers (ReportDialog)
    String where = "", happened = "", expected = "", other = "";
    boolean withSave;

    static Report parse(String info, String games, String conf, String log, String app) {
        final Report r = new Report();
        r.gamesDir = games;
        r.confDir = conf;
        r.logFile = log;
        r.app = app;
        for (String line : (info == null ? "" : info).split("\n")) {
            final int t = line.indexOf('\t');
            if (t < 0) continue;
            final String k = line.substring(0, t), v = line.substring(t + 1);
            switch (k) {
                case "game": r.game = v; break;
                case "games": r.games = v.isEmpty() ? new String[0] : v.split("\u001f"); break;
                case "area": r.area = v; break;
                case "square": r.square = v; break;
                case "save": r.save = v; break;
                case "front": r.front = v; break;
                case "engine": r.engine = v; break;
                default: break;
            }
        }
        return r;
    }

    boolean saveAvailable() { return !save.isEmpty() && new File(save).isDirectory(); }

    /** "Where in the game" as first filled in: the area and the square. */
    String whereGuess() {
        if (square.isEmpty()) return area;
        return area.isEmpty() ? square : area + ", " + square;
    }

    // ---- What goes in the zip

    /** The files the zip takes from the device (shown by See What's Sent too), with the names they go under. */
    List<String[]> files() {
        final List<String[]> out = new ArrayList<>();
        if (logFile != null) {
            final File log = new File(logFile);
            if (log.isFile()) out.add(new String[] {log.getPath(), "log.txt"});
        }
        if (confDir != null) {
            final File cfg = new File(confDir, "settings.cfg");
            if (cfg.isFile()) out.add(new String[] {cfg.getPath(), "settings.cfg"});
        }
        if (gamesDir != null) {
            // (the engine's notes: games/_CYD/*.TXT and one folder down - never a journal's BOOK.TXT, the scan's
            // marks or anything of the report's own folder; as sendlogs.sh takes them)
            final File cyd = new File(gamesDir, "_CYD");
            final File[] top = cyd.listFiles();
            if (top != null) {
                Arrays.sort(top);
                for (File f : top) if (f.isFile() && note(f)) out.add(new String[] {f.getPath(), "engine/" + f.getName()});
                for (File d : top) {
                    if (!d.isDirectory() || d.getName().startsWith("_")) continue;
                    final File[] in = d.listFiles();
                    if (in == null) continue;
                    Arrays.sort(in);
                    for (File f : in)
                        if (f.isFile() && note(f)) out.add(new String[] {f.getPath(), "engine/" + d.getName() + "/" + f.getName()});
                }
            }
        }
        if (withSave && saveAvailable()) {
            final File[] in = new File(save).listFiles();
            if (in != null) {
                Arrays.sort(in);
                for (File f : in) if (f.isFile()) out.add(new String[] {f.getPath(), "save/" + f.getName()});
            }
        }
        return out;
    }

    private static boolean note(File f) {
        final String n = f.getName().toUpperCase(Locale.ROOT);
        return n.endsWith(".TXT") && !n.equals("BOOK.TXT") && !n.equals("PMSCAN.TXT");
    }

    /** DEVICE.TXT: what a handheld's report email says about the device (the relay's body), and the log's end. */
    String deviceText(Context c) {
        final StringBuilder b = new StringBuilder();
        b.append("Gold Box Game Engine for Android - problem report\n");
        b.append("Made: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm Z", Locale.US).format(new Date())).append('\n');
        b.append("App: ").append(app).append('\n');
        b.append("Front end (PM-GoldBoxGameEngine): ").append(front).append('\n');
        b.append("Engine (CYD-GoldBoxGameEngine): ").append(engine).append('\n');
        b.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(")\n");
        b.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("Processor: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?").append('\n');
        final DisplayMetrics m = c.getResources().getDisplayMetrics();
        b.append("Screen: ").append(m.widthPixels).append(" x ").append(m.heightPixels).append(" pixels, ")
                .append(m.densityDpi).append(" dpi, smallest width ")
                .append(c.getResources().getConfiguration().smallestScreenWidthDp).append(" dp\n");
        final ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            b.append("Memory: ").append(mi.totalMem >> 20).append(" MB, ").append(mi.availMem >> 20)
                    .append(" MB free").append(mi.lowMemory ? " (low)" : "").append('\n');
        }
        b.append("App memory limit: ").append(Runtime.getRuntime().maxMemory() >> 20).append(" MB\n");
        if (gamesDir != null) {
            try {
                final StatFs st = new StatFs(gamesDir);
                b.append("Storage free (games folder): ").append(st.getAvailableBytes() >> 20).append(" MB\n");
            } catch (Exception ignored) {
            }
            b.append("Games folder: ").append(folderKind(c)).append('\n');
        }
        b.append("Games found: ").append(games.length == 0 ? "none" : android.text.TextUtils.join(", ", games)).append('\n');
        b.append("Game: ").append(game.isEmpty() ? "none" : game).append('\n');
        if (!area.isEmpty() || !square.isEmpty()) b.append("Where (the app's): ").append(whereGuess()).append('\n');
        b.append("Save of the game included: ").append(withSave && saveAvailable() ? "yes (save/, slot A's files)" : "no")
                .append('\n');
        b.append("\nThe log's last 40 lines:\n");
        b.append(lastLines(logFile, 40));
        return b.toString();
    }

    /** The games folder named as the player sees it - no user names (Android has none in these paths anyway). */
    private String folderKind(Context c) {
        final File priv = c.getExternalFilesDir(null);
        if (priv != null && gamesDir.startsWith(priv.getPath())) return "the app's private folder";
        final String root = android.os.Environment.getExternalStorageDirectory().getPath();
        if (gamesDir.startsWith(root)) return "Internal storage" + gamesDir.substring(root.length()).replace("/", " › ");
        return "a folder of the player's (SD card or other)";
    }

    String reportText() {
        final StringBuilder b = new StringBuilder();
        b.append("Game: ").append(game.isEmpty() ? "(none chosen)" : game).append('\n');
        b.append("Where in the game: ").append(where.isEmpty() ? "(not given)" : where).append('\n');
        b.append("\nWhat happened?\n").append(happened.isEmpty() ? "(not given)" : happened).append('\n');
        b.append("\nWhat should have happened instead?\n").append(expected.isEmpty() ? "(not given)" : expected).append('\n');
        b.append("\nAnything else?\n").append(other.isEmpty() ? "(not given)" : other).append('\n');
        return b.toString();
    }

    String subject() {
        return "Gold Box Game Engine problem report - app " + app + ", " + Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE;
    }

    /** The zip, made in the app's cache (ReportProvider serves it); the report's save folder emptied after. */
    File makeZip(Context c) throws Exception {
        final File dir = ReportProvider.folder(c);
        final File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();       // (an earlier report's - mailed or not, it's done)
        final String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date());
        final File zip = new File(dir, "GoldBox-Report-" + stamp + ".zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            put(z, "REPORT.TXT", reportText().getBytes(StandardCharsets.UTF_8));
            put(z, "DEVICE.TXT", deviceText(c).getBytes(StandardCharsets.UTF_8));
            for (String[] f : files()) {
                final File src = new File(f[0]);
                z.putNextEntry(new ZipEntry(f[1]));
                if (f[1].equals("log.txt") && src.length() > 2_000_000) {
                    z.write(tail(src, 2_000_000));                   // (a very long log: its end, as sendlogs.sh)
                } else {
                    try (InputStream in = new FileInputStream(src)) {
                        final byte[] buf = new byte[16384];
                        for (int n; (n = in.read(buf)) > 0;) z.write(buf, 0, n);
                    }
                }
                z.closeEntry();
            }
        }
        clearSave();
        return zip;
    }

    void clearSave() {
        if (save.isEmpty()) return;
        final File[] in = new File(save).listFiles();
        if (in != null) for (File f : in) f.delete();
    }

    private static void put(ZipOutputStream z, String name, byte[] b) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(b);
        z.closeEntry();
    }

    private static byte[] tail(File f, int max) throws Exception {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            final long from = Math.max(0, r.length() - max);
            final byte[] b = new byte[(int) (r.length() - from)];
            r.seek(from);
            r.readFully(b);
            return b;
        }
    }

    private static String lastLines(String path, int n) {
        if (path == null) return "(no log)\n";
        try {
            final String s = new String(tail(new File(path), 64 * 1024), StandardCharsets.UTF_8);
            final String[] l = s.split("\n");
            final StringBuilder b = new StringBuilder();
            for (int i = Math.max(0, l.length - n); i < l.length; ++i) b.append(l[i]).append('\n');
            return b.toString();
        } catch (Exception e) {
            return "(the log couldn't be read)\n";
        }
    }

    // ---- Sending

    /** The email's body: the player's answers (the zip holds them too, and everything else). */
    String body() {
        return reportText() + "\nThe logs and the device's details are in the attached zip.\n";
    }

    /**
     * The player's email app with the report (To, Subject, the answers, the zip); none - Android's Share menu; nothing
     * that takes it - the zip copied into the GoldBox folder. Returns what to tell the player, or null (an app opened).
     */
    String send(Context c, File zip) {
        final Uri u = ReportProvider.uri(c, zip);
        final Intent mail = new Intent(Intent.ACTION_SEND);
        mail.setType("application/zip");
        mail.putExtra(Intent.EXTRA_EMAIL, new String[] {TO});
        mail.putExtra(Intent.EXTRA_SUBJECT, subject());
        mail.putExtra(Intent.EXTRA_TEXT, body());
        mail.putExtra(Intent.EXTRA_STREAM, u);
        mail.setClipData(ClipData.newRawUri(zip.getName(), u));
        mail.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        // (email apps only: the mailto selector - the apps that send email - with the attachment kept)
        final Intent only = new Intent(mail);
        only.setSelector(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")));
        try {
            c.startActivity(only);
            return null;
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
        try {
            final Intent share = Intent.createChooser(mail, "Send the Report With");
            share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(share);
            return null;
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
        // (nothing to send it with: the zip where the player's files are)
        try {
            final File where = gamesDir != null ? new File(gamesDir).getParentFile() : null;
            if (where == null) throw new Exception();
            final File out = new File(where, zip.getName());
            try (InputStream in = new FileInputStream(zip); FileOutputStream o = new FileOutputStream(out)) {
                final byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0;) o.write(buf, 0, n);
            }
            return "This device has no email app or other app to send it with. The report was saved as "
                    + shown(c, out) + " - attach it to an email to " + TO + " from another device.";
        } catch (Exception e) {
            return "This device has no email app or other app to send it with, and the report couldn't be saved.";
        }
    }

    private static String shown(Context c, File f) {
        final String root = android.os.Environment.getExternalStorageDirectory().getPath();
        final String p = f.getPath();
        return p.startsWith(root) ? "Internal storage" + p.substring(root.length()).replace("/", " › ") : p;
    }
}
