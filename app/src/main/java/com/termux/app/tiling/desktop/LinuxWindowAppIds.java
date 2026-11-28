package com.termux.app.tiling.desktop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.X11AppSettingsResolver;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The catalogue app a Linux window's class belongs to (#423), so a window finds its app's own
 * Opacity. The lookup scans the catalogue, so it runs once per class off the main thread; until it
 * has answered, a class has no app and the global Opacity applies. {@code onResolved} is told on
 * the executor's thread when a class has an answer.
 */
public final class LinuxWindowAppIds {

    @NonNull private final Function<String, String> mScan;
    @NonNull private final Executor mExecutor;
    @NonNull private final Runnable mOnResolved;
    @NonNull private final ConcurrentHashMap<String, String> mAnswers = new ConcurrentHashMap<>();
    @NonNull private final Set<String> mPending = ConcurrentHashMap.newKeySet();
    @NonNull private final ConcurrentHashMap<String, Long> mEmptyAt = new ConcurrentHashMap<>();
    @NonNull private final LongSupplier mClock;

    /** How long a class no app has is believed before it is looked up again (an app may be installed meanwhile). */
    static final long EMPTY_MS = 60_000L;

    /** One thread for every instance: the lookups are rare and slow, and an activity must not leave one behind. */
    private static final Executor SHARED = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "linux-window-app");
        t.setDaemon(true);
        return t;
    });

    public LinuxWindowAppIds(@NonNull Function<String, String> scan, @NonNull Executor executor,
                             @NonNull Runnable onResolved) {
        this(scan, executor, onResolved, System::currentTimeMillis);
    }

    LinuxWindowAppIds(@NonNull Function<String, String> scan, @NonNull Executor executor,
                      @NonNull Runnable onResolved, @NonNull LongSupplier clock) {
        mScan = scan;
        mExecutor = executor;
        mOnResolved = onResolved;
        mClock = clock;
    }

    /** The live lookup, on the shared thread. */
    @NonNull
    public static LinuxWindowAppIds live(@NonNull Runnable onResolved) {
        return new LinuxWindowAppIds(catalogue(), SHARED, onResolved);
    }

    /** The live scan: the catalogue's app for a window class, or null. */
    @NonNull
    public static Function<String, String> catalogue() {
        return wmClass -> X11AppSettingsResolver.appIdForWindowClass(
            LinuxAppCatalog.scan(LinuxAppCatalog.roots()), wmClass);
    }

    /** The app id of windows of class {@code wmClass}; empty while unknown or when no app has it. */
    @NonNull
    public String of(@Nullable String wmClass) {
        if (wmClass == null || wmClass.isEmpty()) return "";
        String known = mAnswers.get(wmClass);
        if (known != null) {
            Long at = known.isEmpty() ? mEmptyAt.get(wmClass) : null;
            if (at == null || mClock.getAsLong() - at < EMPTY_MS) return known;
        }
        if (mPending.add(wmClass)) {
            mExecutor.execute(() -> {
                String id = null;
                try {
                    id = mScan.apply(wmClass);
                } catch (RuntimeException ignored) {
                    // A failed scan reads as no app.
                }
                mAnswers.put(wmClass, id == null ? "" : id);
                if (id == null) mEmptyAt.put(wmClass, mClock.getAsLong());
                mPending.remove(wmClass);
                mOnResolved.run();
            });
        }
        return known == null ? "" : known;
    }

    /** Forgets every answer: the catalogue changed. */
    public void invalidate() {
        mAnswers.clear();
        mEmptyAt.clear();
    }
}
