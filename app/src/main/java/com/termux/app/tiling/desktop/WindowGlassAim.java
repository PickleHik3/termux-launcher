package com.termux.app.tiling.desktop;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.WindowLayer;

/**
 * Keeps the windows' glass aimed where the windows are (ADR 0029 section 10), the one owner of it,
 * as {@code SlabAim} is for a pane slab. A window's glass reads its place on screen only when the
 * window is drawn, and Terl's pages and strips move windows by a transform alone (a slide in
 * navigation mode, its settle, a focus that scrolls the strip, the move through places), which
 * draws nothing. Registered with the window's glass service while the desktop is attached, the
 * glass commit (the frame table's last step, on every frame that draws) asks the desktop to draw
 * again each window that moved since it was drawn ({@link TilingDesktopView#aimGlass}). At rest
 * nothing draws, so nothing runs; nothing is pushed from where a transform is written.
 */
final class WindowGlassAim implements GlassEntry.Frame {

    /** What the commit asks: draw again what moved; how many windows were drawn. */
    interface Windows {
        int aimGlass();
    }

    @NonNull private final View mView;
    @NonNull private final Windows mWindows;
    @Nullable private GlassService mGlass;
    @Nullable private String mElement;

    WindowGlassAim(@NonNull View view, @NonNull Windows windows) {
        mView = view;
        mWindows = windows;
    }

    /** Registers with the window's glass service; nothing when the window has none (a test, a preview). */
    void attach() {
        if (mGlass != null) return;
        GlassService glass = GlassService.of(mView);
        if (glass == null) return;
        String element = "terl-windows@" + Integer.toHexString(System.identityHashCode(mView));
        // Wallpaper, behind widgets and Floats: it never takes a Live slot, it only asks each frame.
        if (glass.register(new GlassEntry(element, mView, GlassKind.WALLPAPER,
            GlassEntry.PRIORITY_WIDGET, WindowLayer.PAGES, this), null) == null) return;
        mGlass = glass;
        mElement = element;
    }

    void detach() {
        GlassService glass = mGlass;
        String element = mElement;
        mGlass = null;
        mElement = null;
        if (glass != null && element != null) glass.unregister(element);
    }

    @Override
    public void onFrame() {
        mWindows.aimGlass();
    }
}
