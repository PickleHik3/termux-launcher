package com.termux.app.fonts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs one font install at a time on a background thread and publishes progress to whichever
 * listeners are currently attached.
 *
 * <p>Process-scoped singleton on purpose: the download must outlive the settings fragment that
 * started it, so rotating the device (or leaving the screen and coming back) reattaches to the
 * same install instead of restarting or losing it. Listeners are held for the lifetime of the
 * screen and removed in {@code onPause}; the install keeps going either way.
 *
 * <p>The heavy work lives in {@link FontDownloader} and {@link FontInstaller}. This class only
 * owns the thread, the single-flight guard, the cancel flag, and the last progress snapshot.
 */
public final class FontInstallCoordinator {

    /** Notified on the main thread. */
    public interface Listener {
        void onFontInstallProgress(@NonNull FontDownloader.Progress progress);
    }

    private static FontInstallCoordinator instance;

    @NonNull private final Context appContext;
    @NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @NonNull private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @NonNull private final List<Listener> listeners = new ArrayList<>();
    @NonNull private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** Non-null while an install is in flight. Guarded by {@code this}. */
    @Nullable private String activeFamilyId;
    /** Last published snapshot, so a freshly attached screen can render immediately. */
    @Nullable private FontDownloader.Progress lastProgress;

    private FontInstallCoordinator(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    public static synchronized FontInstallCoordinator getInstance(@NonNull Context context) {
        if (instance == null) instance = new FontInstallCoordinator(context);
        return instance;
    }

    public synchronized void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public synchronized void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** Family id currently installing, or {@code ""} when idle. */
    @NonNull
    public synchronized String getActiveFamilyId() {
        return activeFamilyId == null ? "" : activeFamilyId;
    }

    @Nullable
    public synchronized FontDownloader.Progress getLastProgress() {
        return lastProgress;
    }

    public synchronized boolean isBusy() {
        return activeFamilyId != null;
    }

    /** Asks the running install to stop at the next buffer boundary. */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Queues an install. Returns false when another install is already running — the caller
     * should tell the user rather than silently queueing a second multi-megabyte download.
     */
    public synchronized boolean start(@NonNull FontCatalog.Family family,
                                      @NonNull FontInstaller.Options options) {
        if (activeFamilyId != null) return false;
        activeFamilyId = family.id;
        cancelled.set(false);
        publish(new FontDownloader.Progress(family.id, FontDownloader.State.DOWNLOADING,
            0L, family.downloadBytes, "", ""));
        executor.execute(() -> run(family, options));
        return true;
    }

    /**
     * Rewrites the managed config for an already-installed family with new toggles.
     *
     * <p>No network, no file copies — just the config plus a reload — so flipping icons or
     * dragging the weight slider stays instant and needs no download at all.
     *
     * @return false when the family's faces are not on disk, i.e. it must be installed first.
     */
    public boolean reapply(@NonNull FontCatalog.Family family, @NonNull FontInstaller.Options options) {
        FontInstaller installer = new FontInstaller();
        if (!installer.isInstalled(family)) return false;
        FontCatalog.SymbolFont symbolFont = FontCatalog.load(appContext).symbolFont;
        FontInstaller.Options effective = options;
        if (effective.nerdIcons && symbolFont != null) {
            try {
                installer.ensureSymbolsInstalled(appContext, symbolFont);
            } catch (IOException e) {
                effective = effective.withNerdIcons(false);
            }
        } else if (symbolFont == null) {
            effective = effective.withNerdIcons(false);
        }
        try {
            // Goes through the installer rather than buildManagedConfig directly, so the symbols
            // file is re-checked here too: this is the path a toggle flip takes, long after the
            // download, and ~/.termux/fonts/symbols/ may have been deleted in between.
            installer.writeManagedConfig(family, effective, symbolFont);
        } catch (IOException e) {
            return false;
        }
        new FontSettings(appContext).setActive(family, effective);
        requestTerminalReload(appContext);
        return true;
    }

    /** Removes the managed config and forgets the active family. Cheap, so it runs inline. */
    public boolean uninstallManagedConfig() {
        FontInstaller installer = new FontInstaller();
        boolean removed = installer.uninstallManagedConfig();
        new FontSettings(appContext).clearActive();
        requestTerminalReload(appContext);
        return removed;
    }

    private void run(@NonNull FontCatalog.Family family, @NonNull FontInstaller.Options options) {
        FontInstaller installer = new FontInstaller();
        File staging = new File(appContext.getCacheDir(), "fonts-staging/" + family.id);
        try {
            FontCatalog.SymbolFont symbolFont = FontCatalog.load(appContext).symbolFont;
            FontInstaller.Options effective = options;
            if (effective.nerdIcons && symbolFont != null) {
                installer.ensureSymbolsInstalled(appContext, symbolFont);
            } else if (symbolFont == null) {
                // No bundled symbols face means no icon coverage to promise; drop the toggle
                // rather than write a symbol_map line pointing at a file that is not there.
                effective = effective.withNerdIcons(false);
            }
            FontDownloader downloader = new FontDownloader(
                FontDownloader.ANDROID_TYPEFACE_PROBE, cancelled::get);
            Map<FontCatalog.FaceSlot, File> staged =
                downloader.stageFamily(family, staging, this::publish);

            publish(new FontDownloader.Progress(family.id, FontDownloader.State.INSTALLING,
                family.downloadBytes, family.downloadBytes, "", ""));
            installer.install(family, staged, symbolFont, effective);
            new FontSettings(appContext).setActive(family, effective);
            FontDownloader.deleteRecursively(staging);
            requestTerminalReload(appContext);
            publish(new FontDownloader.Progress(family.id, FontDownloader.State.INSTALLED,
                family.downloadBytes, family.downloadBytes, "", ""));
        } catch (FontDownloader.CancelledException e) {
            FontDownloader.deleteRecursively(staging);
            publish(new FontDownloader.Progress(family.id, FontDownloader.State.CANCELLED,
                0L, family.downloadBytes, "", ""));
        } catch (IOException | RuntimeException e) {
            FontDownloader.deleteRecursively(staging);
            String message = e.getMessage();
            publish(new FontDownloader.Progress(family.id, FontDownloader.State.FAILED,
                0L, family.downloadBytes, "", message == null ? e.toString() : message));
        } finally {
            synchronized (this) {
                activeFamilyId = null;
            }
        }
    }

    private void publish(@NonNull FontDownloader.Progress progress) {
        List<Listener> snapshot;
        synchronized (this) {
            lastProgress = progress;
            snapshot = new ArrayList<>(listeners);
        }
        if (snapshot.isEmpty()) return;
        mainHandler.post(() -> {
            for (Listener listener : snapshot) listener.onFontInstallProgress(progress);
        });
    }

    /**
     * Asks the terminal to re-read its styling, which is what {@code termux-reload-settings}
     * does. {@code recreateActivity=false} is enough: the reload path calls
     * {@code checkForFontAndColors()}, so the new faces land without a visible activity rebuild.
     * <p/>
     * The picker always runs from the settings activity, so TermuxActivity is stopped and has
     * unregistered its reload receiver — a plain broadcast is dropped and the pick looks inert
     * until the next cold start. Requesting the reload for the next resume leaves the pending flag
     * behind, which is what every other settings screen does.
     */
    public static void requestTerminalReload(@NonNull Context context) {
        try {
            com.termux.app.TermuxActivity.requestTermuxActivityStylingOnNextResume(
                context.getApplicationContext(), false);
        } catch (RuntimeException e) {
            // Nothing is listening when the terminal is not running; the next start reads the
            // config from disk anyway, so this is never worth failing an install over.
        }
    }

    /**
     * Whether the active data connection is metered. Used to warn before a multi-megabyte
     * download; a null ConnectivityManager is treated as metered, because the honest answer to
     * "is this going to cost the user money" is "assume yes".
     */
    public static boolean isConnectionMetered(@NonNull Context context) {
        ConnectivityManager manager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return true;
        try {
            return manager.isActiveNetworkMetered();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
