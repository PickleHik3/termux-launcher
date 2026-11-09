package com.termux.app.terminal;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import com.termux.terminal.GhosttyMemory;

/**
 * The app's side of the Ghostty core's fail-safes: memory pressure forwarded to every Ghostty pane.
 * Installed once from
 * {@code TermuxApplication.onCreate} in the main process, before any session is created.
 */
public final class GhosttyFailSafes {

    private GhosttyFailSafes() {}

    public static void install(@NonNull Application app) {
        app.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(int level) {
                GhosttyMemory.onTrimMemory(level);
            }

            @Override
            public void onConfigurationChanged(@NonNull Configuration newConfig) {}

            @Override
            public void onLowMemory() {
                GhosttyMemory.onTrimMemory(TRIM_MEMORY_COMPLETE);
            }
        });
    }
}
