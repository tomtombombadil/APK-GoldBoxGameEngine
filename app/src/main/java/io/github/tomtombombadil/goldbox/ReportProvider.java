package io.github.tomtombombadil.goldbox;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Report a Problem's zip for the email app the player picks: Android lends another app a file only by a content://
 * address. Serves the files of the app's cache folder `reports` alone, read-only, to an app the share intent granted
 * (the provider isn't exported). Our own in a few lines rather than androidx's FileProvider: nothing added to the
 * build.
 */
public final class ReportProvider extends ContentProvider {

    static Uri uri(android.content.Context c, File f) {
        return Uri.parse("content://" + c.getPackageName() + ".reports/" + Uri.encode(f.getName()));
    }

    static File folder(android.content.Context c) {
        final File d = new File(c.getCacheDir(), "reports");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    private File file(Uri u) throws FileNotFoundException {
        final String name = u.getLastPathSegment();
        if (getContext() == null || name == null || name.contains("/") || name.startsWith("."))
            throw new FileNotFoundException(String.valueOf(u));
        final File f = new File(folder(getContext()), name);
        if (!f.isFile()) throw new FileNotFoundException(String.valueOf(u));
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        if (mode != null && mode.contains("w")) throw new FileNotFoundException("read-only");
        return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri u, String[] cols, String sel, String[] args, String order) {
        // (the name and size an email app asks for to show the attachment)
        final File f;
        try {
            f = file(u);
        } catch (FileNotFoundException e) {
            return null;
        }
        if (cols == null) cols = new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        final MatrixCursor c = new MatrixCursor(cols, 1);
        final Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; ++i) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override public String getType(Uri u) { return "application/zip"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
