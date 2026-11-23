package io.github.tomtombombadil.goldbox;

import android.content.Intent;

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The front end keeps its state in statics, as a program does: a fresh
        // process for the next start, not this one's leftovers (the folder
        // screen runs in a process of its own, so it stays)
        if (isFinishing()) System.exit(0);
    }
}
