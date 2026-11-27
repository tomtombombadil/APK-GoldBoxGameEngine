package io.github.tomtombombadil.goldbox;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.widget.Toast;

/**
 * A game on the home screen (Tom, 2026-11-26: "No homescreen shortcuts for each game - at least not
 * automatically. But a menu option to add a game to the home screen would be alright"): the chooser's long-press
 * menu, Add to Home Screen - Android's own pin request (the launcher asks the player). The shortcut opens the
 * folder screen as the app's icon does, with the game's folder: straight into that game, as Play.
 */
final class Shortcuts {

    private Shortcuts() {}

    /** Android 8 and later, with a launcher that takes them. */
    static boolean can(Context c) {
        if (Build.VERSION.SDK_INT < 26) return false;
        final ShortcutManager m = c.getSystemService(ShortcutManager.class);
        return m != null && m.isRequestPinShortcutSupported();
    }

    /** Asks the launcher to pin a shortcut to the game (its picture the icon when there is one). */
    static void add(Context c, String folder, String name, String shortName, Bitmap art) {
        if (Build.VERSION.SDK_INT < 26 || folder == null || folder.isEmpty()) return;
        final ShortcutManager m = c.getSystemService(ShortcutManager.class);
        if (m == null || !m.isRequestPinShortcutSupported()) {
            Toast.makeText(c, "This home screen can't take shortcuts", Toast.LENGTH_SHORT).show();
            return;
        }
        final Intent open = new Intent(c, SetupActivity.class);
        open.setAction(Intent.ACTION_MAIN);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        open.putExtra(SetupActivity.EXTRA_GAME, folder);
        final String label = shortName != null && !shortName.isEmpty() ? shortName : name;
        final ShortcutInfo s = new ShortcutInfo.Builder(c, "game:" + folder)
                .setShortLabel(label.length() > 25 ? label.substring(0, 25) : label)
                .setLongLabel(name != null && !name.isEmpty() ? name : label)
                .setIcon(icon(c, art))
                .setIntent(open)
                .build();
        try {
            m.requestPinShortcut(s, null);
        } catch (IllegalStateException e) {
            Toast.makeText(c, "The home screen couldn't take the shortcut now", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * An adaptive icon from the game's picture: its middle, square, filling the 108dp layer (the launcher's mask
     * shows the inner 72dp); no picture - the app's own icon.
     */
    private static Icon icon(Context c, Bitmap art) {
        if (art == null || art.isRecycled() || art.getWidth() < 8 || art.getHeight() < 8)
            return Icon.createWithResource(c, R.drawable.ic_launcher);
        final int size = Math.round(108 * c.getResources().getDisplayMetrics().density);
        final Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        final Canvas k = new Canvas(out);
        k.drawColor(0xFF0B1024);
        final int w = art.getWidth(), h = art.getHeight(), side = Math.min(w, h);
        // (box art: its top third holds the title - the square from there; a logo: its middle)
        final int top = h > w ? Math.min((h - side) / 3, h - side) : (h - side) / 2;
        final Rect from = new Rect((w - side) / 2, top, (w - side) / 2 + side, top + side);
        k.drawBitmap(art, from, new Rect(0, 0, size, size), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return Icon.createWithAdaptiveBitmap(out);
    }
}
