package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.X11AppSettings;
import com.termux.app.x11.X11AppSettingsResolver;
import com.termux.app.x11.X11AppSettingsStore;
import com.termux.app.x11.X11LinuxAppRunner;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Whether the Paste key is Ctrl+Shift+V in a Linux window, by the window's class: the app's own
 * Paste shortcut over the display's, the same per-app key X11 windows use. The class to app id
 * lookup (a catalogue scan) runs once per class on the executor and the answer is kept per class,
 * so a key press never reads files or parses settings. Until a class has resolved the display's
 * shortcut answers and nothing is kept. {@link #invalidate} drops everything when settings change.
 */
public final class LinuxPasteShortcuts {

    /** Where the answers come from. */
    interface Source {
        /** The display's paste shortcut. */
        @NonNull String globalShortcut();

        /** The app's own paste shortcut, or null to follow the display. */
        @Nullable String overrideOf(@NonNull String appId);

        /** Whether this app's windows follow the display (it is the running desktop itself). */
        boolean followsDisplay(@NonNull String appId);

        /** The app id a window class belongs to, or null for none. Slow: called off the main thread. */
        @Nullable String appIdFor(@NonNull String wmClass);
    }

    /** The live source: the settings store, the app catalogue and the running desktop. */
    @NonNull
    public static LinuxPasteShortcuts over(@NonNull X11AppSettingsStore store) {
        return new LinuxPasteShortcuts(new Source() {
            @NonNull @Override public String globalShortcut() {
                return store.globalPasteShortcut();
            }

            @Nullable @Override public String overrideOf(@NonNull String appId) {
                return store.pasteShortcut(appId);
            }

            @Override public boolean followsDisplay(@NonNull String appId) {
                return X11LinuxAppRunner.isDesktopRunning(appId);
            }

            @Nullable @Override public String appIdFor(@NonNull String wmClass) {
                return X11AppSettingsResolver.appIdForWindowClass(
                    LinuxAppCatalog.scan(LinuxAppCatalog.roots()), wmClass);
            }
        }, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "linux-paste-shortcut");
            t.setDaemon(true);
            return t;
        }));
    }

    @NonNull private final Source source;
    @NonNull private final Executor executor;
    /** Class to answer, for classes already resolved. */
    @NonNull private final ConcurrentHashMap<String, Boolean> answers = new ConcurrentHashMap<>();
    @NonNull private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private volatile int generation;

    LinuxPasteShortcuts(@NonNull Source source, @NonNull Executor executor) {
        this.source = source;
        this.executor = executor;
    }

    /** Whether the Paste key adds Shift in a window of class {@code wmClass}. */
    public boolean withShift(@NonNull String wmClass) {
        Boolean known = answers.get(wmClass);
        if (known != null) return known;
        resolveLater(wmClass);
        return X11AppSettings.PASTE_CTRL_SHIFT_V.equals(source.globalShortcut());
    }

    /** Settings or the installed apps changed: every answer is worked out again. */
    public void invalidate() {
        generation++;
        answers.clear();
    }

    private void resolveLater(@NonNull String wmClass) {
        if (!pending.add(wmClass)) return;
        final int mine = generation;
        try {
            executor.execute(() -> {
                try {
                    String appId = source.appIdFor(wmClass);
                    boolean follow = appId != null && source.followsDisplay(appId);
                    String override = appId == null || follow ? null : source.overrideOf(appId);
                    boolean shift = X11AppSettingsResolver.pasteWithShift(source.globalShortcut(),
                        override, false);
                    if (mine == generation) answers.put(wmClass, shift);
                } catch (RuntimeException ignored) {
                    // Not kept: the display's shortcut answers and the next press asks again.
                } finally {
                    pending.remove(wmClass);
                }
            });
        } catch (RuntimeException e) {
            pending.remove(wmClass);
        }
    }
}
