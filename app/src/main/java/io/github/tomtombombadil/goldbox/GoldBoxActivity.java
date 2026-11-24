package io.github.tomtombombadil.goldbox;

import android.content.Intent;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * The game: SDL's activity running the port's front end and the engine
 * (libmain.so), told where the games, the settings and the log are - the
 * folder screen (SetupActivity) settled the folder.
 */
public class GoldBoxActivity extends SDLActivity {

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
