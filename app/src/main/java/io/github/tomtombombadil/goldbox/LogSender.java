package io.github.tomtombombadil.goldbox;

import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Menu › System › Send Logs in the app (Tom, 2026-11-28): the logs posted to the developer's log relay as the
 * handhelds' tools/sendlogs.sh posts them (the PM repo; plain form fields, file:&lt;name&gt; = its text) - log.txt,
 * the run before's, settings.cfg and the engine's games/_CYD notes (never a journal's BOOK.TXT, the scan's marks,
 * game files or saves). The answer, in sendlogs.sh's words ("SENT n [thanks]" / "ERROR why"), goes to
 * conf/sendlogs.result, which the front end reads and shows.
 */
final class LogSender {
    // (the relay's address - the same as sendlogs.sh's RELAY)
    static final String RELAY =
            "https://script.google.com/macros/s/AKfycbwzwY0ThckpiiYTi0npBbaCzLvE5ppzWmilPtgH-w4uclPy28K9YXqqs8jolAJIm3bh/exec";

    private LogSender() {}

    static void start(String games, String conf, String log, String reply, String version, String screen) {
        new Thread(() -> {
            String result;
            try {
                result = send(games, conf, log, reply, version, screen);
            } catch (Exception e) {
                result = "ERROR Couldn't reach the log service. Is the device online? (" + e.getClass().getSimpleName() + ")";
            }
            answer(conf, result);
        }, "send-logs").start();
    }

    static void fail(String conf, String why) {
        if (conf != null) answer(conf, "ERROR " + why);
    }

    /** The answer for the front end: written whole, then put in place (it looks for the file each frame). */
    private static void answer(String conf, String text) {
        final File tmp = new File(conf, "sendlogs.result.part");
        try (OutputStream o = new FileOutputStream(tmp)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        if (!tmp.renameTo(new File(conf, "sendlogs.result"))) tmp.delete();
    }

    /** -> "SENT n [thanks]" or "ERROR what to tell the player" (as sendlogs.sh answers). */
    private static String send(String games, String conf, String log, String reply, String version, String screen)
            throws Exception {
        final StringBuilder body = new StringBuilder();
        field(body, "app", "goldbox");
        field(body, "version", version);
        field(body, "device", Build.MANUFACTURER + " " + Build.MODEL);
        field(body, "firmware", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        field(body, "portmaster", "none (the Android app)");
        field(body, "screen", screen);
        field(body, "reply", reply);
        int n = 0;
        // (sendlogs.sh's files, names and limits: this run's log, the run before, the settings, the engine's notes)
        final File logFile = new File(log);
        n += add(body, logFile, "log.txt", 600000, n);
        n += add(body, new File(logFile.getParentFile(), "log.prev.txt"), "log.prev.txt", 300000, n);
        n += add(body, new File(conf, "settings.cfg"), "settings.cfg", 8000, n);
        final File cyd = new File(games, "_CYD");
        final List<File> notes = new ArrayList<>();
        final File[] top = cyd.listFiles();
        if (top != null) {
            java.util.Arrays.sort(top);
            for (File f : top) if (f.isFile()) notes.add(f);
            for (File d : top) {
                if (!d.isDirectory()) continue;
                final File[] in = d.listFiles();
                if (in == null) continue;
                java.util.Arrays.sort(in);
                for (File f : in) if (f.isFile()) notes.add(f);
            }
        }
        final String base = cyd.getPath() + "/";
        for (File f : notes) {
            final String nm = f.getName();
            if (!nm.endsWith(".TXT") || nm.equals("BOOK.TXT") || nm.equals("PMSCAN.TXT")) continue;
            final String rel = f.getPath().substring(base.length());
            n += add(body, f, "engine_" + rel.replaceAll("[^A-Za-z0-9._-]", "_"), 60000, n);
        }
        if (n == 0) return "ERROR There are no logs to send yet.";
        // (Apps Script answers a POST with a redirect to where its answer is read: followed, as curl -L does)
        HttpURLConnection c = (HttpURLConnection) new URL(RELAY).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(10000);
        c.setReadTimeout(120000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        final byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(data.length);
        try (OutputStream o = c.getOutputStream()) {
            o.write(data);
        }
        int code = c.getResponseCode();
        for (int hops = 0; hops < 5 && code / 100 == 3; ++hops) {
            final String to = c.getHeaderField("Location");
            c.disconnect();
            if (to == null) break;
            c = (HttpURLConnection) new URL(new URL(RELAY), to).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10000);
            c.setReadTimeout(120000);
            code = c.getResponseCode();
        }
        final String out = read(code < 400 ? c.getInputStream() : c.getErrorStream()).trim();
        c.disconnect();
        if (out.startsWith("OK ")) return "SENT " + n + (out.contains("thanks") ? " thanks" : "");
        if (out.startsWith("ERROR ")) return "ERROR The log service said: " + out.substring(6);
        return "ERROR The log service gave an answer the app didn't expect. Try again later.";
    }

    /** A file's end (at most max bytes) as one form field, when it has something in it and fewer than 12 have gone. */
    private static int add(StringBuilder b, File f, String name, int max, int n) throws Exception {
        if (n >= 12 || !f.isFile() || f.length() == 0) return 0;
        field(b, "file:" + name, tail(f, max));
        return 1;
    }

    private static void field(StringBuilder b, String k, String v) throws Exception {
        if (b.length() > 0) b.append('&');
        b.append(URLEncoder.encode(k, "UTF-8")).append('=').append(URLEncoder.encode(v == null ? "" : v, "UTF-8"));
    }

    /** A file's text, its end when it's longer than max bytes. */
    private static String tail(File f, int max) throws Exception {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            final long n = r.length();
            final long from = n > max ? n - max : 0;
            final byte[] b = new byte[(int) (n - from)];
            r.seek(from);
            r.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream i = in) {
            final ByteArrayOutputStream o = new ByteArrayOutputStream();
            final byte[] b = new byte[4096];
            for (int k; (k = i.read(b)) > 0;) o.write(b, 0, k);
            return o.toString("UTF-8");
        }
    }
}
