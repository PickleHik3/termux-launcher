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
import com.termux.shared.termux.settings.registry.SettingsRegistry;
import com.termux.shared.termux.settings.registry.TerlConf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Watches {@code ~/.config/terl/terl.conf} and applies it once, all or nothing ({@link TerlConf}).
 * At rest it costs one inotify watch on the nearest folder that exists, so a missing folder or
 * file is waited for, never polled. A change is read after a short quiet spell, on one
 * work thread; a failure shows one notice naming the line. Terl never writes the
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
    /** One background thread does every read, parse and apply; it exits when idle. */
    @NonNull private final ThreadPoolExecutor mWork = new ThreadPoolExecutor(0, 1, 10L,
        TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "terl-conf");
            thread.setDaemon(true);
            return thread;
        });
    @NonNull private final java.util.List<FileObserver> mObservers = new java.util.ArrayList<>();
    @NonNull private final Runnable mSync = () -> mWork.execute(this::sync);

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
        sInstance.mWork.execute(sInstance::sync);
    }

    /**
     * Watches the file's folder, or the deepest folder on its path that exists, and the same for
     * the target when the file is a symlink (a dotfiles checkout), so an edit of either is seen.
     */
    private synchronized void arm() {
        for (FileObserver each : mObservers) each.stopWatching();
        mObservers.clear();
        watch(mFile);
        try {
            File real = mFile.getCanonicalFile();
            if (!real.equals(mFile.getAbsoluteFile())) watch(real);
        } catch (IOException | SecurityException ignored) {
            // an unresolvable link: the link's own folder is watched
        }
    }

    private void watch(@NonNull File target) {
        File dir = target.getParentFile();
        while (dir != null && !dir.isDirectory()) dir = dir.getParentFile();
        if (dir == null) return;
        boolean inFolder = dir.equals(target.getParentFile());
        // the next path component under dir: the file itself once dir is its folder
        String next = inFolder ? target.getName()
            : target.getAbsolutePath().substring(dir.getAbsolutePath().length() + 1).split("/")[0];
        // A file being written is not ready until it is closed: only the file's own CLOSE_WRITE,
        // MOVED_TO or DELETE schedules a sync. While a missing folder is awaited, its appearing
        // only moves the watch down (a folder moved in whole also syncs, its file being complete).
        int mask = FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.DELETE
            | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF
            | (inFolder ? 0 : FileObserver.CREATE);
        FileObserver observer = new FileObserver(dir.getAbsolutePath(), mask) {
            @Override public void onEvent(int event, @Nullable String path) {
                boolean self = (event & (FileObserver.DELETE_SELF | FileObserver.MOVE_SELF)) != 0;
                if (self) {
                    mWork.execute(TerlConfWatcher.this::arm);
                } else if (next.equals(path)) {
                    if (inFolder) {
                        mMain.removeCallbacks(mSync);
                        mMain.postDelayed(mSync, QUIET_MS);
                    } else if ((event & FileObserver.CREATE) != 0) {
                        mWork.execute(TerlConfWatcher.this::arm);
                    } else {
                        mWork.execute(TerlConfWatcher.this::arm);
                        mMain.removeCallbacks(mSync);
                        mMain.postDelayed(mSync, QUIET_MS);
                    }
                }
            }
        };
        mObservers.add(observer);
        observer.startWatching();
    }

    /** On the work thread: re-arm, read, check and apply; only the notice and refreshes touch the UI. */
    private void sync() {
        arm(); // a folder may have appeared or gone; the watch moves to the deepest that exists
        if (mFile.isFile() && mFile.length() > MAX_BYTES) {
            notice(mContext.getString(R.string.terl_conf_too_large));
            return;
        }
        String text = read();
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
            SettingsRegistry registry = LauncherSettings.registry(mContext,
                c -> LauncherSettingsAdapters.forDevice(c,
                    () -> TerminalActionDispatcher.getInstance().redressChrome()));
            TerlConf.Prepared prepared = TerlConf.prepare(text, record, registry);
            TerlConf.Outcome early = prepared.early();
            if (early != null) {
                if (early.kind == TerlConf.Kind.FAILED) notice(String.join("\n", early.errors));
                return;
            }
            // One task on main saves the whole batch and rolls it back inside the same task, so
            // an edit made on a Settings screen cannot interleave with it.
            mMain.post(() -> {
                try {
                    TerlConf.Outcome outcome = prepared.commit(record, registry,
                        LauncherSettings.applier());
                    if (outcome.kind == TerlConf.Kind.FAILED) {
                        notice(String.join("\n", outcome.errors));
                    }
                } catch (RuntimeException e) {
                    notice(mContext.getString(R.string.terl_conf_not_applied));
                }
            });
        } catch (RuntimeException e) {
            notice(mContext.getString(R.string.terl_conf_not_applied));
        }
    }

    private void notice(@NonNull String detail) {
        mMain.post(() -> AppNotice.show(mContext, AppNoticeItem.Kind.ERROR,
            mContext.getString(R.string.terl_conf_not_applied), detail, true));
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
