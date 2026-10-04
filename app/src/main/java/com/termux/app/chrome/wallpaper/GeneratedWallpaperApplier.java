package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.FancierGlassPolicy;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The shared plumbing under the wallpaper slots: the single apply worker, the main-thread result
 * post, the listeners the activity's live host registers, the Home slot's record of a living still
 * ({@link #recordLiving}) and its {@link #clear}. A living still's photo is the system still, so
 * nothing is rendered here. Shared by the in-app picker and {@code POST /v1/wallpaper}. No view
 * is touched.
 */
public final class GeneratedWallpaperApplier {

    /** Result of an apply, always on the main thread. */
    public interface Callback {
        void onDone(boolean ok, @Nullable String error);
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "generated-wallpaper-apply");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Nullable
    private static volatile AnimatedWallpaperStatus sStatusProvider;

    @Nullable
    private static volatile Runnable sChangedListener;

    private GeneratedWallpaperApplier() {}

    /**
     * Registered by the activity's live host: run on the main thread after the stored background or
     * its colours changed ({@link #recordLiving}, {@link #clear}), so it can pick the new ones up. Null
     * unregisters.
     */
    public static void setChangedListener(@Nullable Runnable listener) {
        sChangedListener = listener;
    }

    static void notifyChanged() {
        MAIN.post(() -> {
            Runnable listener = sChangedListener;
            if (listener != null) listener.run();
        });
    }

    /** Registered by the activity so {@code GET /v1/wallpaper} can say whether frames are playing. Null unregisters. */
    public static void setStatusProvider(@Nullable AnimatedWallpaperStatus provider) {
        sStatusProvider = provider;
    }

    /** The registered provider, or null. */
    @Nullable
    public static AnimatedWallpaperStatus statusProvider() {
        return sStatusProvider;
    }

    /**
     * Whether living stills are offered (picker row, launcherctl): the still needs API 34
     * and the live frames need Fancier Glass to be active.
     */
    public static boolean offered(int sdkInt, boolean fancierGlassActive) {
        return sdkInt >= 34 && fancierGlassActive;
    }

    /** Why a living still is not offered: {@code api}, {@code fancier_glass_off}, or null when it is. */
    @Nullable
    public static String notOfferedReason(int sdkInt, boolean fancierGlassActive) {
        if (sdkInt < 34) return "api";
        return fancierGlassActive ? null : "fancier_glass_off";
    }

    /** Runs {@code job} on the apply worker, behind any render already queued. */
    static void onWorker(@NonNull Runnable job) {
        WORKER.execute(job);
    }

    /** Posts {@code cb}'s result to the main thread. */
    static void post(@Nullable Callback cb, boolean ok, @Nullable String error) {
        finish(cb, ok, error);
    }

    /**
     * Records the Home slot as the living still {@code livingId} whose photo was just set: the id
     * stays stored and the live host plays it over the photo, with its own palette.
     */
    static void recordLiving(@NonNull Context ctx, @NonNull String livingId) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(ctx.getApplicationContext(), false);
        if (prefs == null) return;
        prefs.setManagedWallpaperAnimatedId(livingId);
        notifyChanged();
    }

    /** Forgets the living still; a photo (or a {@code path} set) is the wallpaper now. */
    public static void clear(@NonNull Context ctx) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(ctx.getApplicationContext(), false);
        if (prefs == null) return;
        prefs.setManagedWallpaperAnimatedId(null);
        notifyChanged();
    }

    private static void finish(@Nullable Callback cb, boolean ok, @Nullable String error) {
        if (cb == null) return;
        MAIN.post(() -> cb.onDone(ok, error));
    }

    /** Whether the switch-and-phone rule says living stills run, given the stored switch. */
    public static boolean offeredFor(int sdkInt, boolean fancierGlassSwitchOn) {
        // A living still is a managed wallpaper by construction, so the "managed on screen"
        // leg of FancierGlassPolicy.active holds once it is applied.
        return offered(sdkInt, FancierGlassPolicy.active(sdkInt, fancierGlassSwitchOn, true));
    }
}
