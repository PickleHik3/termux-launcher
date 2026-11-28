package com.termux.app.tiling.desktop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.X11AppSettingsResolver;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

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

    public LinuxWindowAppIds(@NonNull Function<String, String> scan, @NonNull Executor executor,
                             @NonNull Runnable onResolved) {
        mScan = scan;
        mExecutor = executor;
        mOnResolved = onResolved;
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
        if (known != null) return known;
        if (mPending.add(wmClass)) {
            mExecutor.execute(() -> {
                String id = null;
                try {
                    id = mScan.apply(wmClass);
                } catch (RuntimeException ignored) {
                    // A failed scan reads as no app.
                }
                mAnswers.put(wmClass, id == null ? "" : id);
                mPending.remove(wmClass);
                mOnResolved.run();
            });
        }
        return "";
    }

    /** Forgets every answer: the catalogue changed. */
    public void invalidate() {
        mAnswers.clear();
    }
}
