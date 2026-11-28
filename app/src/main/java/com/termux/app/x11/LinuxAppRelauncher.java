package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Linux apps launched again for loaded saved Workspaces (#431). The app is found in the Linux app
 * catalogue itself ({@link LinuxAppCatalog#scan}), not the drawer's list, so it is found on a cold
 * start before the drawer has loaded and whether or not the drawer shows Linux apps: by the
 * catalogue key it was launched from, else by its windows' class
 * ({@link X11WindowIconResolver#match}). One scan per batch, on one thread of its own.
 *
 * <p>It keeps which keys it launched and when ({@link #claim}): a window counts as a relaunch's only
 * while a launch of its key is outstanding, once, so a window the user opens later is left alone.
 * A user's launch of the same app before the relaunched one shows cannot be told apart (same key).
 *
 * <p>Process-wide ({@link #shared}), so a recreated activity still knows what it launched.
 * Main-thread state; no Android views.
 */
public final class LinuxAppRelauncher {

    /** How long a launch waits for its window to be claimed; the desktop's tile waits as long. */
    public static final long OUTSTANDING_MS = 60_000L;

    /** One app to launch again: {@code token} comes back in the answer (the desktop's window id). */
    public static final class Request {
        public final long token;
        @Nullable public final String key;
        @NonNull public final String appId;

        public Request(long token, @Nullable String key, @Nullable String appId) {
            this.token = token;
            this.key = key;
            this.appId = appId == null ? "" : appId;
        }
    }

    /** How each request went, on the main thread. */
    public interface Answer {
        /** Launched from catalogue key {@code key}; its window is claimed by that key. */
        void launched(long token, @NonNull String key);

        /** No such app in the catalogue, or it would not start. */
        void notFound(long token);
    }

    /** Starts {@code app} as a window of its own; false when it could not. */
    public interface Launcher {
        boolean launch(@NonNull LinuxAppCatalog.LinuxApp app);
    }

    @Nullable private static LinuxAppRelauncher sShared;

    @NonNull private final Executor mFiles;
    @NonNull private final Executor mMain;
    @NonNull private final Supplier<List<LinuxAppCatalog.LinuxApp>> mCatalogue;
    @NonNull private final LongFunction<String> mKeyOf;
    @NonNull private final LongSupplier mClock;
    /** Each key launched again to the times of its launches still waiting for a window, oldest first. */
    @NonNull private final Map<String, Deque<Long>> mOutstanding = new HashMap<>();

    @VisibleForTesting
    LinuxAppRelauncher(@NonNull Executor files, @NonNull Executor main,
                       @NonNull Supplier<List<LinuxAppCatalog.LinuxApp>> catalogue,
                       @NonNull LongFunction<String> keyOf, @NonNull LongSupplier clock) {
        mFiles = files;
        mMain = main;
        mCatalogue = catalogue;
        mKeyOf = keyOf;
        mClock = clock;
    }

    /**
     * The process's relauncher. {@code keyOf} gives the catalogue key a Linux host window was
     * launched from (the Wayland runner's match); the first call's is kept.
     */
    @NonNull
    public static LinuxAppRelauncher shared(@NonNull LongFunction<String> keyOf) {
        if (sShared == null) {
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            sShared = new LinuxAppRelauncher(Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "linux-app-relaunch");
                thread.setDaemon(true);
                return thread;
            }), main::post, () -> LinuxAppCatalog.scan(LinuxAppCatalog.roots()), keyOf,
                android.os.SystemClock::uptimeMillis);
        }
        return sShared;
    }

    /** Finds each request's app off the main thread, then launches it through {@code launcher}. */
    public void relaunch(@NonNull List<Request> requests, @NonNull Launcher launcher, @NonNull Answer answer) {
        if (requests.isEmpty()) return;
        List<Request> batch = new ArrayList<>(requests);
        mFiles.execute(() -> {
            List<LinuxAppCatalog.LinuxApp> apps;
            try {
                apps = mCatalogue.get();
            } catch (RuntimeException e) {
                apps = null;
            }
            if (apps == null) apps = Collections.emptyList();
            List<LinuxAppCatalog.LinuxApp> found = new ArrayList<>(batch.size());
            for (Request request : batch) found.add(resolve(apps, request.key, request.appId));
            mMain.execute(() -> {
                for (int i = 0; i < batch.size(); i++) {
                    Request request = batch.get(i);
                    LinuxAppCatalog.LinuxApp app = found.get(i);
                    if (app == null || !launcher.launch(app)) {
                        answer.notFound(request.token);
                        continue;
                    }
                    Deque<Long> times = mOutstanding.get(app.id);
                    if (times == null) {
                        times = new ArrayDeque<>();
                        mOutstanding.put(app.id, times);
                    }
                    times.addLast(mClock.getAsLong());
                    answer.launched(request.token, app.id);
                }
            });
        });
    }

    /**
     * The catalogue key of Linux host window {@code linuxId} when a relaunch of that key is still
     * outstanding, which this window then answers; null for any other window.
     */
    @Nullable
    public String claim(long linuxId) {
        String key = mKeyOf.apply(linuxId);
        Deque<Long> times = key == null ? null : mOutstanding.get(key);
        if (times == null) return null;
        long now = mClock.getAsLong();
        while (!times.isEmpty() && now - times.peekFirst() > OUTSTANDING_MS) times.removeFirst();
        if (times.isEmpty()) {
            mOutstanding.remove(key);
            return null;
        }
        times.removeFirst();
        if (times.isEmpty()) mOutstanding.remove(key);
        return key;
    }

    /** The app a saved window names: by its catalogue key, else by its windows' class. */
    @Nullable
    static LinuxAppCatalog.LinuxApp resolve(@NonNull List<LinuxAppCatalog.LinuxApp> apps, @Nullable String key,
                                            @NonNull String appId) {
        LinuxAppCatalog.LinuxApp app = key == null || key.isEmpty() ? null : LinuxAppCatalog.find(apps, key);
        if (app == null && !appId.isEmpty()) app = X11WindowIconResolver.match(apps, appId);
        return app;
    }
}
