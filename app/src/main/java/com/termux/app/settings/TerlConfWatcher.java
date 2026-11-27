package com.termux.app.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.app.notice.AppNoticeItem;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.registry.TerlConf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Watches {@code ~/.config/terl/terl.conf} and applies it once, all or nothing ({@link TerlConf}).
 * At rest it costs one inotify watch on the nearest folder that exists, so a missing folder or
 * file is waited for, never polled. A change is read after a short quiet spell, on the main
 * thread (the file is small); a failure shows one notice naming the line. Terl never writes the
 * file. The registry is built only when a differing file is found.
 */
public final class TerlConfWatcher {

    private static final String PREFS = "terl_conf";
    private static final String KEY_APPLIED = "applied_fingerprint";
    private static final long QUIET_MS = 400L;
    /** A settings file is a few KB; anything bigger is not one. */
    private static final long MAX_BYTES = 1L << 20;

    private static TerlConfWatcher sInstance;

    @NonNull private final Context mContext;
    @NonNull private final Handler mMain = new Handler(Looper.getMainLooper());
    @NonNull private final File mFile;
    @Nullable private FileObserver mObserver;
    @NonNull private final Runnable mSync = this::sync;

    private TerlConfWatcher(@NonNull Context app) {
        mContext = app;
        mFile = file();
    }

    /** {@code ~/.config/terl/terl.conf}. */
    @NonNull
    public static File file() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".config/terl/terl.conf");
    }

    /** Starts watching, once per process, and checks the file for the next-start case. */
    public static synchronized void start(@NonNull Context context) {
        if (sInstance != null) return;
        Context app = context.getApplicationContext() != null ? context.getApplicationContext()
            : context;
        sInstance = new TerlConfWatcher(app);
        sInstance.arm();
        sInstance.mMain.post(sInstance.mSync);
    }

    /** Watches the deepest folder on the file's path that exists; null when none does. */
    private synchronized void arm() {
        if (mObserver != null) mObserver.stopWatching();
        mObserver = null;
        File dir = mFile.getParentFile();
        while (dir != null && !dir.isDirectory()) dir = dir.getParentFile();
        if (dir == null) return;
        // the next path component under dir: the file itself once dir is its folder
        String next = dir.equals(mFile.getParentFile()) ? mFile.getName()
            : mFile.getAbsolutePath().substring(dir.getAbsolutePath().length() + 1).split("/")[0];
        int mask = FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE
            | FileObserver.DELETE | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF;
        mObserver = new FileObserver(dir.getAbsolutePath(), mask) {
            @Override public void onEvent(int event, @Nullable String path) {
                boolean self = (event & (FileObserver.DELETE_SELF | FileObserver.MOVE_SELF)) != 0;
                if (!self && !next.equals(path)) return;
                mMain.removeCallbacks(mSync);
                mMain.postDelayed(mSync, QUIET_MS);
            }
        };
        mObserver.startWatching();
    }

    private void sync() {
        arm(); // a folder may have appeared or gone; one watch moves to the deepest that exists
        String text = read();
        if (text == null && mFile.isFile()) {
            notice(mContext.getString(R.string.terl_conf_too_large));
            return;
        }
        if (text == null) return;
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        TerlConf.Record record = new TerlConf.Record() {
            @Nullable @Override public String applied() {
                return prefs.getString(KEY_APPLIED, null);
            }
            @Override public void remember(@NonNull String fingerprint) {
                prefs.edit().putString(KEY_APPLIED, fingerprint).apply();
            }
        };
        try {
            TerlConf.Outcome outcome = TerlConf.sync(text, record,
                LauncherSettings.registry(mContext, c -> LauncherSettingsAdapters.forDevice(c,
                    () -> TerminalActionDispatcher.getInstance().redressChrome())),
                LauncherSettings.applier());
            if (outcome.kind == TerlConf.Kind.FAILED) {
                notice(String.join("\n", outcome.errors));
            }
        } catch (RuntimeException e) {
            notice(mContext.getString(R.string.terl_conf_not_applied));
        }
    }

    private void notice(@NonNull String detail) {
        AppNotice.show(mContext, AppNoticeItem.Kind.ERROR,
            mContext.getString(R.string.terl_conf_not_applied), detail, true);
    }

    /** The file's text, or null when it is missing, unreadable or over the size cap. */
    @Nullable
    private String read() {
        if (!mFile.isFile() || mFile.length() > MAX_BYTES) return null;
        try (InputStream in = new FileInputStream(mFile)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
