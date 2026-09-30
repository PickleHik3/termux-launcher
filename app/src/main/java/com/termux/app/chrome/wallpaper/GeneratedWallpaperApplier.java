package com.termux.app.chrome.wallpaper;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.app.chrome.FancierGlassPolicy;
import com.termux.app.chrome.ManagedWallpaper;
import com.termux.app.wall.WallParallax;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The "choose and apply" half of a generated background: render its rest-pose still with the
 * chosen palette at the size the photo cropper writes (one and a half portrait screens wide), hand
 * it to {@link ManagedWallpaper#apply} through the same pending file the cropper uses (so the
 * launcher's exact copy, stored wallpaper id and the system's screen-sized centre all follow), and
 * remember which background and colours it was. Shared by the in-app picker and
 * {@code POST /v1/wallpaper}. No view is touched.
 */
public final class GeneratedWallpaperApplier {

    /** Result of {@link #apply} and {@link #recaptureIfMaterial}, always on the main thread. */
    public interface Callback {
        void onDone(boolean ok, @Nullable String error);
    }

    private static final String LOG_TAG = "GeneratedWallpaperApplier";
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
     * its colours changed ({@link #apply}, {@link #clear}), so it can pick the new ones up. Null
     * unregisters.
     */
    public static void setChangedListener(@Nullable Runnable listener) {
        sChangedListener = listener;
    }

    private static void notifyChanged() {
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
     * Whether generated backgrounds are offered (picker row, launcherctl): the still needs API 34
     * and the live frames need Fancier Glass to be active.
     */
    public static boolean offered(int sdkInt, boolean fancierGlassActive) {
        return sdkInt >= 34 && fancierGlassActive;
    }

    /** Why a background is not offered: {@code api}, {@code fancier_glass_off}, or null when it is. */
    @Nullable
    public static String notOfferedReason(int sdkInt, boolean fancierGlassActive) {
        if (sdkInt < 34) return "api";
        return fancierGlassActive ? null : "fancier_glass_off";
    }

    /**
     * Renders and applies {@code w} for {@code target} ({@code home}, {@code lock} or {@code both}).
     * {@code paletteMode} is {@code material} or {@code own}. Call it on the main thread with a
     * themed context (the activity) so Material roles resolve to the launcher's scheme there; the
     * render and set run on a worker that holds only the application context. {@code cb} is
     * called on the main thread.
     */
    @RequiresApi(34)
    public static void apply(@NonNull Context ctx, @NonNull AnimatedWallpaper w, @Nullable String paletteMode,
                             @NonNull String target, @Nullable Callback cb) {
        final Context app = ctx.getApplicationContext();
        final String mode = WallpaperPaletteCapture.MODE_OWN.equals(paletteMode)
            ? WallpaperPaletteCapture.MODE_OWN : WallpaperPaletteCapture.MODE_MATERIAL;
        final int flags = ManagedWallpaper.flagsForTarget(target);
        if (flags == 0) {
            finish(cb, false, "target must be home, lock or both");
            return;
        }
        final int[] palette;
        try {
            palette = WallpaperPaletteCapture.resolve(ctx, w, mode);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Palette resolve failed", e);
            finish(cb, false, "palette_failed");
            return;
        }
        WORKER.execute(() -> {
            String error = renderAndSet(app, w, palette, flags);
            if (error != null) {
                finish(cb, false, error);
                return;
            }
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
            if (prefs != null) {
                prefs.setManagedWallpaperAnimatedId(w.id());
                prefs.setManagedWallpaperAnimatedPalette(mode);
                prefs.setManagedWallpaperAnimatedColors(palette);
                prefs.setManagedWallpaperAnimatedTarget(ManagedWallpaper.targetName(flags));
            }
            notifyChanged();
            finish(cb, true, null);
        });
    }

    /** Forgets the generated background; a photo (or a {@code path} set) is the wallpaper now. */
    public static void clear(@NonNull Context ctx) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(ctx.getApplicationContext(), false);
        if (prefs == null) return;
        prefs.setManagedWallpaperAnimatedId(null);
        prefs.setManagedWallpaperAnimatedColors(null);
        notifyChanged();
    }

    /**
     * For a launcher colour-scheme change: when a generated background is stored in Material mode,
     * resolve the palette again and, if the four colours differ from the stored ones, re-render
     * and re-apply the still to the target it was applied to. Call it on the main thread with the
     * themed context (the palette is read there). Otherwise {@code cb} gets {@code (true, null)}
     * with nothing done.
     *
     * <p>Never call this from an {@code OnColorsChangedListener}: our own still becomes the system
     * wallpaper, the system re-derives its colours from it, the listener fires again, and the
     * loop never ends. Call it only from the launcher's own scheme-change path.</p>
     */
    @RequiresApi(34)
    public static void recaptureIfMaterial(@NonNull Context ctx, @Nullable Callback cb) {
        final Context app = ctx.getApplicationContext();
        final int[] palette;
        try {
            palette = WallpaperPaletteCapture.material(ctx);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Palette resolve failed", e);
            finish(cb, false, "palette_failed");
            return;
        }
        WORKER.execute(() -> {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
            if (prefs == null) {
                finish(cb, true, null);
                return;
            }
            String id = prefs.getManagedWallpaperAnimatedId();
            AnimatedWallpaper w = AnimatedWallpapers.byId(id);
            if (w == null || !WallpaperPaletteCapture.MODE_MATERIAL.equals(prefs.getManagedWallpaperAnimatedPalette())) {
                finish(cb, true, null);
                return;
            }
            if (Arrays.equals(palette, prefs.getManagedWallpaperAnimatedColors())) {
                finish(cb, true, null);
                return;
            }
            int flags = ManagedWallpaper.flagsForTarget(prefs.getManagedWallpaperAnimatedTarget());
            String error = renderAndSet(app, w, palette, flags == 0 ? ManagedWallpaper.FLAGS_HOME : flags);
            if (error != null) {
                finish(cb, false, error);
                return;
            }
            prefs.setManagedWallpaperAnimatedColors(palette);
            finish(cb, true, null);
        });
    }

    /** Blocking. Returns null on success, else a short error code. */
    @RequiresApi(34)
    @Nullable
    private static String renderAndSet(@NonNull Context app, @NonNull AnimatedWallpaper w,
                                       @NonNull int[] palette, int flags) {
        int[] portrait = ManagedWallpaper.portraitSize(app);
        int width = WallParallax.pickerWidthPx(portrait[0]);
        int height = portrait[1];
        Bitmap still = null;
        try {
            still = AnimatedWallpaperStill.render(w, palette, width, height);
            if (still == null) return "render_failed";
            // The cropper's own pending file: apply() promotes it to the exact copy.
            File pending = ManagedWallpaper.tempFile(app);
            if (pending.exists()) pending.delete();
            try (FileOutputStream out = new FileOutputStream(pending, false)) {
                if (!still.compress(Bitmap.CompressFormat.PNG, 100, out)) return "encode_failed";
                out.flush();
            }
            boolean ok = ManagedWallpaper.apply(app, WallpaperManager.getInstance(app), Uri.fromFile(pending),
                flags, portrait[0], portrait[1], TermuxAppSharedPreferences.build(app, false));
            return ok ? null : "wallpaper_failed";
        } catch (Exception | OutOfMemoryError e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Applying a generated background failed", e);
            return "wallpaper_failed";
        } finally {
            if (still != null) still.recycle();
        }
    }

    private static void finish(@Nullable Callback cb, boolean ok, @Nullable String error) {
        if (cb == null) return;
        MAIN.post(() -> cb.onDone(ok, error));
    }

    /** Whether the switch-and-phone rule says generated backgrounds run, given the stored switch. */
    public static boolean offeredFor(int sdkInt, boolean fancierGlassSwitchOn) {
        // A generated background is a managed wallpaper by construction, so the "managed on screen"
        // leg of FancierGlassPolicy.active holds once it is applied.
        return offered(sdkInt, FancierGlassPolicy.active(sdkInt, fancierGlassSwitchOn, true));
    }
}
