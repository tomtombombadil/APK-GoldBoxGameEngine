package io.github.tomtombombadil.goldbox;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * The game: SDL's activity running the port's front end and the engine
 * (libmain.so), told where the games, the settings and the log are - the
 * folder screen (SetupActivity) settled the folder.
 */
public class GoldBoxActivity extends SDLActivity {

    // ---- The app's own controls round the game (Shell; the front end's src/shell.*)

    /** A pad button down / up (the front end's Btn numbers). */
    static native void nativeButton(int button, boolean down);
    /** Where the game goes and the Companion (w 0: none), in the surface's pixels; its view 0 both, 1 map, 2 party. */
    static native void nativeLayout(int gx, int gy, int gw, int gh, int cx, int cy, int cw, int ch, int view);

    /** The menu drawer's taps (MenuDrawer: CLOSE, CHOOSE, CHANGE, BACK, SET). */
    static native void nativeMenu(int op, int index, int value);

    private Shell shell;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (mBrokenLibraries || mLayout == null) return;
        shell = new Shell(this);
        mLayout.addView(shell.root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** The front end: what it has on screen changed (its game thread). */
    public void onShellState(int state) {
        runOnUiThread(() -> { if (shell != null) shell.setState(state); });
    }

    /** The front end: what one of its own screens shows (Shell.text's channels; its game thread). */
    public void onShellText(int channel, byte[] text) {
        runOnUiThread(() -> { if (shell != null) shell.text(channel, text); });
    }

    /**
     * The scan's step for the game chooser (its game thread, while the scan's page is up): the
     * game's box art (the launcher's JPEG, decoded whole here) or its logo (rgba, w x h, from the
     * engine's icon reader) saved as out - the chooser only reads it later.
     */
    public static boolean saveArt(String jpeg, byte[] rgba, int w, int h, String out) {
        try {
            Bitmap b = null;
            boolean photo = false;
            if (jpeg != null && !jpeg.isEmpty()) {
                final BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(jpeg, o);
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1200) sample *= 2;
                final BitmapFactory.Options d = new BitmapFactory.Options();
                d.inSampleSize = sample;
                b = BitmapFactory.decodeFile(jpeg, d);
                photo = b != null;
            }
            if (b == null && rgba != null && w > 0 && h > 0 && rgba.length >= w * h * 4) {
                b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                b.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(rgba));       // (R G B A bytes, as ARGB_8888 keeps them)
            }
            if (b == null) return false;
            final File f = new File(out);
            final File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
                b.compress(photo ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, 90, os);
            }
            b.recycle();
            return true;
        } catch (Exception | OutOfMemoryError e) {
            return false;
        }
    }

    /** The front end: Controls - Button Size / Opacity for the pad (its game thread). */
    public void onShellPad(int size, int alpha) {
        runOnUiThread(() -> { if (shell != null) shell.padStyle(size, alpha); });
    }

    /** The front end: Y on a controller - the Companion out / away (its game thread). */
    public void onShellCompanion() {
        runOnUiThread(() -> { if (shell != null) shell.toggleCompanion(); });
    }

    private static boolean fromController(InputDevice d, int source) {
        final int s = d != null ? d.getSources() : source;
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (s & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD && d != null && !d.isVirtual();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // (a controller in use: no pad - a touch brings it back)
        if (shell != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() != KeyEvent.KEYCODE_BACK
                && fromController(event.getDevice(), event.getSource())) shell.controller(true);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (shell != null && fromController(event.getDevice(), event.getSource())) shell.controller(true);
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {"c++_shared", "SDL2", "main"};
    }

    @Override
    protected String[] getArguments() {
        final Intent i = getIntent();
        String games = i != null ? i.getStringExtra(SetupActivity.EXTRA_GAMES) : null;
        String conf = i != null ? i.getStringExtra(SetupActivity.EXTRA_CONF) : null;
        String log = i != null ? i.getStringExtra(SetupActivity.EXTRA_LOG) : null;
        if (games == null || conf == null || log == null) {
            // (started again by Android without the folder screen: the same folder)
            final File folder = SetupActivity.folder(this);
            games = SetupActivity.games(folder).getAbsolutePath();
            conf = new File(folder, "conf").getAbsolutePath();
            log = new File(folder, "log.txt").getAbsolutePath();
        }
        return new String[] {"--games", games, "--conf", conf, "--log", log};
    }

    /**
     * Start - System - Games Folder (the front end calls this, then ends):
     * the folder screen opens to choose another folder.
     */
    public void chooseFolder() {
        runOnUiThread(() -> {
            final Intent i = new Intent(this, SetupActivity.class);
            i.putExtra(SetupActivity.EXTRA_CHANGE, true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
    }

    /**
     * The camera cutout's / notch's safe margins - left, top, right, bottom in
     * pixels (0s without one). The front end asks about once a second and
     * keeps the game, the on-screen buttons and the Companion out of them
     * (Tom's Pixel 10a, 2026-11-23: the cutout sat in the top centre of the
     * game in portrait). SDL draws into the cutout's edge (SHORT_EDGES), so
     * these are in the same pixels as its surface.
     */
    public int[] cutoutInsets() {
        final int[] r = new int[4];
        if (Build.VERSION.SDK_INT >= 28 && getWindow() != null) {
            final View v = getWindow().getDecorView();
            final WindowInsets wi = v != null ? v.getRootWindowInsets() : null;
            final DisplayCutout dc = wi != null ? wi.getDisplayCutout() : null;
            if (dc != null) {
                r[0] = dc.getSafeInsetLeft();
                r[1] = dc.getSafeInsetTop();
                r[2] = dc.getSafeInsetRight();
                r[3] = dc.getSafeInsetBottom();
            }
        }
        return r;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The front end keeps its state in statics, as a program does: a fresh
        // process for the next start, not this one's leftovers (the folder
        // screen runs in a process of its own, so it stays)
        if (isFinishing()) System.exit(0);
    }
}
