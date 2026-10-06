package com.termux.app.chrome;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.PaneRetroEffect;
import com.termux.app.terminal.PaneRetroStyle;
import com.termux.app.terminal.PreDrawHook;
import com.termux.app.terminal.RetroEffectBinder;
import com.termux.app.terminal.inappkeyboard.FloatingKeyboardFrame;
import com.termux.app.terminal.inappkeyboard.KeyPopupOverlayView;

import java.util.ArrayList;
import java.util.List;

/**
 * The terminal effect on the launcher's own surfaces: the status bar, the dock and the bars off
 * it, the keyboard docked or floating, and the Docked frame's glass, so the whole home screen
 * reads as one CRT or TFT display with the panes.
 *
 * <p>The effect is drawn on a short, fixed list of containers rather than on each bar. A place
 * moves bars between the four edge stacks, the plank and the strip under the keyboard; every one
 * of those lives inside one of {@link #ROOT_IDS}, so whatever the user's layout, Style or keyboard
 * form, every surface is drawn through exactly one effect and none through two. The containers
 * clip nothing themselves: each bar keeps the outline the shape model gives it, and the effect
 * keeps every pixel's coverage, so a Floating card's rounded corners and a Docked slice's square
 * ones come out as they went in.
 *
 * <p>The chrome is drawn flat — no barrel bend, which would move keys and icons away from where
 * they take a touch — and without a vignette, which would darken every bar's edges into a row of
 * separate monitors. Scanlines and the TFT grid take their rows from the screen (see
 * {@code RetroUniforms#phase}), so they run straight on from a bar into the pane beside it.
 *
 * <p>Two surfaces come and go outside those containers and are adopted while they are in the
 * window: the floating keyboard's card and the pressed-key popup.
 */
public final class ChromeRetroEffect {

    /**
     * The containers the effect is drawn on. None is inside another, the panes are inside none,
     * and every bar a place can stand is inside one.
     */
    static final int[] ROOT_IDS = {
        // The joined Docked frame's glass, behind every Docked surface and the gutter.
        R.id.docked_frame_glass,
        // The strip behind the system status bar that continues the top surface up.
        R.id.terminal_status_bar_background,
        // The top and side edge stacks: the status bar, the alphabets bar, the off-dock plank,
        // pinned apps and extra keys, wherever the place puts them.
        R.id.place_edge_stack_top,
        R.id.place_edge_stack_left,
        R.id.place_edge_stack_right,
        // The dock's glass and rows (the bottom edge stack), the docked keyboard with its
        // suggestion strip, and the bands under the keyboard.
        R.id.accessory_stack_container,
        // The alphabets bar's scrub label, drawn over the canvas while the finger is down.
        R.id.apps_bar_az_label_overlay,
        // The scrollback find strip standing above the dock.
        R.id.terminal_find_bar_host,
    };

    @NonNull private final View mRoot;
    @Nullable private final ViewGroup mFloatingKeyboardHost;
    @NonNull private final List<RetroEffectBinder> mFixed = new ArrayList<>();
    /** Binders for the surfaces adopted while they are in the window. */
    @NonNull private final List<RetroEffectBinder> mAdopted = new ArrayList<>();
    @NonNull private final PreDrawHook mScan;
    @NonNull private PaneRetroStyle mStyle = PaneRetroStyle.NONE;

    /** @param root the activity's root view, which holds every id in {@link #ROOT_IDS} */
    public ChromeRetroEffect(@NonNull View root) {
        mRoot = root;
        for (int id : ROOT_IDS) {
            View view = root.findViewById(id);
            if (view != null) mFixed.add(RetroEffectBinder.flat(view));
        }
        View floating = root.findViewById(R.id.floating_keyboard_host);
        mFloatingKeyboardHost = floating instanceof ViewGroup ? (ViewGroup) floating : null;
        mScan = new PreDrawHook(root, this::adoptTransientSurfaces);
    }

    /**
     * The look every surface is drawn through, from the next frame on. NONE, or a phone below
     * API 33, takes every effect this put on off again and stops watching the window.
     */
    public void setStyle(@Nullable PaneRetroStyle style) {
        PaneRetroStyle next = style == null || !PaneRetroEffect.available()
            ? PaneRetroStyle.NONE : style;
        mStyle = next;
        for (RetroEffectBinder binder : mFixed) binder.setStyle(next);
        adoptTransientSurfaces();
        mScan.setOn(next != PaneRetroStyle.NONE);
    }

    @NonNull
    public PaneRetroStyle style() {
        return mStyle;
    }

    /** The views the effect is drawn on now, fixed and adopted; for tests. */
    @NonNull
    List<View> targets() {
        List<View> views = new ArrayList<>();
        for (RetroEffectBinder binder : mFixed) views.add(binder.view());
        for (RetroEffectBinder binder : mAdopted) views.add(binder.view());
        return views;
    }

    /**
     * Drops the surfaces that left the window and adopts the ones that came: the floating
     * keyboard's card (built the first time the keyboard floats) and the pressed-key popup
     * (in the window only while the setting is on). Runs before each frame while an effect is on;
     * a handful of child reads, and nothing allocated unless a surface arrived.
     */
    private void adoptTransientSurfaces() {
        for (int i = mAdopted.size() - 1; i >= 0; i--) {
            RetroEffectBinder binder = mAdopted.get(i);
            if (mStyle != PaneRetroStyle.NONE && binder.view().isAttachedToWindow()) continue;
            binder.release();
            mAdopted.remove(i);
        }
        if (mStyle == PaneRetroStyle.NONE) return;
        adoptChildren(mFloatingKeyboardHost, FloatingKeyboardFrame.class);
        // The popup floats in the window's content view, beside this root, so that a top-row
        // popup is not clipped by the keyboard (KeyPopupController.findPopupHost).
        ViewParent content = mRoot.getParent();
        adoptChildren(content instanceof ViewGroup ? (ViewGroup) content : null,
            KeyPopupOverlayView.class);
    }

    private void adoptChildren(@Nullable ViewGroup parent, @NonNull Class<? extends View> type) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (type.isInstance(child) && !isAdopted(child)) {
                RetroEffectBinder binder = RetroEffectBinder.flat(child);
                mAdopted.add(binder);
                binder.setStyle(mStyle);
            }
        }
    }

    private boolean isAdopted(@NonNull View view) {
        for (RetroEffectBinder binder : mAdopted)
            if (binder.view() == view) return true;
        return false;
    }
}
