// Experiment (exp/wayland-turnip): the Turnip driver bundled with -PwaylandTurnip=true.
package com.termux.app.wayland;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Puts the bundled Turnip driver where libadrenotools loads it from: {@code files/turnip/}, an
 * app-private directory (the driver is dlopen'ed from there, which targetSdk 28 allows). The
 * driver ships as an asset, not in {@code lib/}, so the system never loads it as an app library;
 * it is copied again only when the asset's sha256 (written by scripts/fetch-turnip-prebuilt.sh)
 * differs from the one recorded beside the copy.
 */
final class TurnipDriver {

    private static final String LOG_TAG = "TLWayland";
    static final String NAME = "libvulkan_freedreno.so";
    private static final String ASSET = "turnip/" + NAME;
    private static final String ASSET_SHA = ASSET + ".sha256";
    private static final String STAMP = ".sha256";

    private TurnipDriver() {}

    /**
     * The driver directory, ending in '/', with the current driver in it; null (logged) when this
     * APK has no driver or the copy failed.
     */
    @WorkerThread
    @Nullable
    static String prepare(Context context) {
        AssetManager assets = context.getAssets();
        String sha = readAsset(assets, ASSET_SHA);
        if (sha == null) {
            Log.w(LOG_TAG, "direct load: no driver asset in this APK (build with -Pwayland=true -PwaylandTurnip=true)");
            return null;
        }
        File dir = new File(context.getFilesDir(), "turnip");
        File driver = new File(dir, NAME);
        File stamp = new File(dir, STAMP);
        if (driver.isFile() && sha.equals(readFile(stamp))) {
            Log.i(LOG_TAG, "direct driver file: " + driver + " current (sha256 " + sha + ")");
            return dir.getAbsolutePath() + "/";
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(LOG_TAG, "direct load: cannot create " + dir);
            return null;
        }
        File tmp = new File(dir, NAME + ".tmp");
        long bytes = 0;
        try (InputStream in = assets.open(ASSET); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                bytes += n;
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, "direct load: cannot copy the driver asset: " + e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        if (!tmp.renameTo(driver)) {
            Log.w(LOG_TAG, "direct load: cannot move the driver into place at " + driver);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        try (OutputStream out = new FileOutputStream(stamp)) {
            out.write(sha.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            Log.w(LOG_TAG, "direct driver file: cannot write the stamp (copied again next time): " + e);
        }
        Log.i(LOG_TAG, "direct driver file: copied " + bytes + " bytes to " + driver + " (sha256 " + sha + ")");
        return dir.getAbsolutePath() + "/";
    }

    @Nullable
    private static String readAsset(AssetManager assets, String name) {
        try (InputStream in = assets.open(name)) {
            return readSmall(in);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private static String readFile(File file) {
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            return readSmall(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readSmall(InputStream in) throws IOException {
        byte[] buf = new byte[128];
        int len = 0;
        int n;
        while (len < buf.length && (n = in.read(buf, len, buf.length - len)) > 0) len += n;
        return new String(buf, 0, len, StandardCharsets.US_ASCII).trim();
    }
}
