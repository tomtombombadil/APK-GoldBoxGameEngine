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

    /** The Companion's pictures as the app placed them (view 4): groups of 7 - which, x, y, w, h, top row, rows. */
    static native void nativePictures(int[] v);

    private Shell shell;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (mBrokenLibraries || mLayout == null) return;
        shell = new Shell(this);
        updateBack();
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

    // ---- Predictive Back (Android 13 and later; the manifest's enableOnBackInvokedCallback): at the chooser with
    // nothing over it Back closes the app - Android's own gesture, its preview of the home screen; anywhere else it
    // is the game's B (a journal entry's close), so the app takes it
    private Object backCallback;        // (an OnBackInvokedCallback - kept as Object for Android 12 and older)
    private boolean backTaken;
    private long lastBack;

    /** The Back gesture's owner, after anything that changes what Back does (Shell, the drawer). */
    void updateBack() {
        if (Build.VERSION.SDK_INT < 33 || shell == null) return;
        final boolean take = !shell.backLeaves();
        if (take == backTaken) return;
        final android.window.OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (backCallback == null)
            backCallback = (android.window.OnBackInvokedCallback) this::back;
        if (take) d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                (android.window.OnBackInvokedCallback) backCallback);
        else d.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
        backTaken = take;
    }

    /** Back as the app takes it: a journal entry closed, else the game's B (as the Back key's). */
    private void back() {
        final long now = android.os.SystemClock.uptimeMillis();
        if (now - lastBack < 150) return;           // (a Back key and its callback both: once)
        lastBack = now;
        if (shell == null) return;
        if (shell.backLeaves()) {
            finish();
            return;
        }
        if (shell.pdf().isUp()) {
            shell.pdf().close();
            return;
        }
        if (shell.journal().isUp()) {
            shell.journal().back();
            return;
        }
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_BACK);
        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_BACK);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // (Back at the game chooser closes the app - Tom, 2026-11-24; elsewhere it is the game's B)
        if (shell != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && shell.backLeaves()) {
            if (event.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        // (a controller in use: no pad - a touch brings it back)
        if (shell != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() != KeyEvent.KEYCODE_BACK
                && fromController(event.getDevice(), event.getSource())) shell.controller(true);
        // (a PDF or a journal entry up: Back, B and the rest are its own - not the game's)
        if (shell != null && shell.pdf().key(event)) return true;
        if (shell != null && shell.journal().key(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (shell != null && fromController(event.getDevice(), event.getSource())) shell.controller(true);
        if (shell != null && fromController(event.getDevice(), event.getSource()) && shell.journal().motion(event)) return true;
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
        final String game = i != null ? i.getStringExtra(SetupActivity.EXTRA_GAME) : null;
        if (game != null && !game.isEmpty())
            return new String[] {"--games", games, "--conf", conf, "--log", log, "--app-version", appVersion(),
                    "--game-folder", game};
        return new String[] {"--games", games, "--conf", conf, "--log", log, "--app-version", appVersion()};
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** The Journal list's Documents: a game's PDF (its device path), a page at a time (the front end calls this). */
    public void openPdf(String path, String title) {
        runOnUiThread(() -> { if (shell != null) shell.pdf().open(path, title); });
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

    /**
     * A home-screen shortcut tapped while the app runs (singleInstance: the running one gets it).
     * At the chooser with nothing over it, nothing is lost: the app starts again on that game
     * (the folder screen, in its own process, waits for this one to end - SetupActivity.play).
     * In a game, the game in play comes back as it was - a shortcut never ends a game unsaved.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final String game = intent != null ? intent.getStringExtra(SetupActivity.EXTRA_GAME) : null;
        if (game == null || game.isEmpty() || shell == null || !shell.backLeaves()) return;
        final Intent i = new Intent(this, SetupActivity.class);
        i.putExtra(SetupActivity.EXTRA_GAME, game);
        i.putExtra(SetupActivity.EXTRA_RESTART, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
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
