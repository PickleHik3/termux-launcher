package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.termux.R;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * The surface editor: one page over the live home screen — the shape pill and Base sliders up
 * top, the per-surface override groups behind a "Per-surface fine-tuning" fold — plus the
 * edge-drag pills and height handles on the surfaces themselves, the per-property inheritance
 * chips, and the entry-snapshot dirty tracking behind the unsaved-changes gate. The fold's shared
 * rows are generated from {@link SurfaceEditorRows}, so a new surface or property is a table row,
 * not a layout.
 *
 * <p>Extracted from {@code TermuxActivity} as-is: the editor writes through to preferences live,
 * only Done commits, and the ✕ and back both route through {@link #requestClose()}. The activity
 * keeps the render pipeline; everything the editor needs from it crosses {@link Host}, which is
 * the seam that keeps this class free of the activity's fifteen thousand lines.
 */
public final class SurfaceEditorController {

    /** What the editor needs from the activity: its views, its prefs, and its render pipeline. */
    public interface Host {
        @NonNull Context context();
        @Nullable <T extends View> T findView(int viewId);
        @Nullable TermuxAppSharedPreferences preferences();
        @Nullable TermuxInAppKeyboard inAppKeyboard();
        @Nullable View attachedInAppKeyboardView();
        boolean isInAppKeyboardShown();
        boolean isRoundedDockStyle();
        boolean isFullStatusBarEngaged();
        void setTopStatusBarCollapsed(boolean collapsed, boolean animate);
        /** The window's top status inset, as last delivered to the activity. */
        int statusBarInsetTop();
        int themeColor(int attr, int fallbackRes);
        void refreshPaneLayout();
        void applyTerminalSurfaceAppearance();
        void refreshTerminalWindowBar();
        /** Re-applies the sessions panel background at its stored opacity. */
        void applySessionsSurfaceBackground();
        /** Dock geometry changed: bar height, toolbar height, immediate chrome apply. */
        void applyGeometryPreview();
        /** The coalesced glass re-render; {@code blurChanged} also drops the blur cache. */
        void applyGlassPreview(boolean blurChanged);
        void openKeyboardColors();
    }

    @NonNull
    private final Host mHost;

    public SurfaceEditorController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isActive() {
        return mDockTuningMode;
    }

    @Nullable
    private TermuxAppSharedPreferences prefs() {
        return mHost.preferences();
    }

    @Nullable
    private TermuxInAppKeyboard keyboard() {
        return mHost.inAppKeyboard();
    }

    private String getString(@StringRes int res, Object... args) {
        return mHost.context().getString(res, args);
    }

    private android.content.res.Resources getResources() {
        return mHost.context().getResources();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float pxToDp(float px) {
        return px / getResources().getDisplayMetrics().density;
    }

    /**
     * The activity resumed with the editor open: the pane the editor collapsed out of its way on
     * entry must stay collapsed until the editor closes and gives it back.
     */
    public void collapseStatusPaneIfLeftExpanded() {
        if (!mDockTuningMode || !mDockTuningRestoreExpandedStatus)
            return;
        if (prefs() != null && !prefs().isTopPaneClockCollapsed())
            mHost.setTopStatusBarCollapsed(true, false);
    }

    // The editor's own keyboard-height drag state; adjust mode keeps a separate copy in the
    // activity, and the two gestures can never run at once.
    private float mInAppKeyboardHeightDragStartY;
    private float mInAppKeyboardHeightDragStartScale;
    private float mInAppKeyboardUnscaledDragHeight;
    private float mSurfaceTuningInsetDragStartX;
    private int mSurfaceTuningInsetDragStartDp;
    private float mSurfaceTuningDockHeightDragStartY;
    private float mSurfaceTuningDockHeightDragStartScale;
    private boolean mDockTuningMode;
    private boolean mDockTuningRestoreExpandedStatus;
    /** Editor state as it was on entry; anything different from this is unsaved. */
    @Nullable private String mSurfaceEditorEntrySignature;
    /** Restores that entry state. Held so the close gate can offer Discard. */
    @Nullable private Runnable mSurfaceEditorRevert;
    private ViewTreeObserver.OnGlobalLayoutListener mDockTuningLayoutListener;
    /**
     * Height the slider region is heading for. Compared against instead of the live layout height
     * so the resize animation's own layout passes do not each look like a fresh change.
     */
    private int mSurfaceEditorScrollTarget;
    @Nullable private android.animation.ValueAnimator mSurfaceEditorScrollAnimator;

    public void enter() {
        // No section asked for: one page, so there is nowhere else to reopen to.
        enter(null);
    }

    public void enter(@Nullable String initialSection) {
        if (mHost.isFullStatusBarEngaged()) return;
        if (prefs() == null)
            return;
        ensureGeneratedRows();
        View controls = mHost.findView(R.id.dock_tuning_controls);
        View advanced = mHost.findView(R.id.surface_editor_advanced);
        View keyboardColors = mHost.findView(R.id.surface_tuning_keyboard_colors);
        SeekBar blur = mHost.findView(R.id.dock_tuning_blur_slider);
        SeekBar opacity = mHost.findView(R.id.dock_tuning_opacity_slider);
        SeekBar grain = mHost.findView(R.id.dock_tuning_grain_slider);
        SeekBar dockRadius = mHost.findView(R.id.dock_tuning_radius_slider);
        SeekBar terminal = mHost.findView(R.id.dock_tuning_terminal_slider);
        com.google.android.material.materialswitch.MaterialSwitch terminalBorder =
            mHost.findView(R.id.dock_tuning_terminal_border_switch);
        View terminalGlassGroup = mHost.findView(R.id.dock_tuning_terminal_glass_group);
        SeekBar terminalGlassBlur = mHost.findView(R.id.dock_tuning_terminal_blur_slider);
        SeekBar terminalGlassGrain = mHost.findView(R.id.dock_tuning_terminal_grain_slider);
        TextView terminalGlassBlurValue = mHost.findView(R.id.dock_tuning_terminal_blur_value);
        TextView terminalGlassGrainValue = mHost.findView(R.id.dock_tuning_terminal_grain_value);
        SeekBar terminalGap = mHost.findView(R.id.dock_tuning_terminal_gap_slider);
        TextView terminalGapValue = mHost.findView(R.id.dock_tuning_terminal_gap_value);
        SeekBar wallpaperOpacity = mHost.findView(R.id.dock_tuning_wallpaper_opacity_slider);
        TextView wallpaperOpacityValue = mHost.findView(R.id.dock_tuning_wallpaper_opacity_value);
        SeekBar size = mHost.findView(R.id.dock_tuning_size_slider);
        SeekBar icons = mHost.findView(R.id.dock_tuning_icons_slider);
        SeekBar keyboardHeight = mHost.findView(R.id.surface_tuning_keyboard_height_slider);
        SeekBar keyboardSpacing = mHost.findView(R.id.surface_tuning_keyboard_spacing_slider);
        SeekBar keyboardRadius = mHost.findView(R.id.surface_tuning_keyboard_radius_slider);
        SeekBar keyboardKeyOpacity = mHost.findView(R.id.surface_tuning_keyboard_key_opacity_slider);
        SeekBar keyboardBgOpacity = mHost.findView(R.id.surface_tuning_keyboard_bg_opacity_slider);
        SeekBar statusBlur = mHost.findView(R.id.surface_tuning_status_blur_slider);
        SeekBar statusOpacity = mHost.findView(R.id.surface_tuning_status_opacity_slider);
        SeekBar statusGrain = mHost.findView(R.id.surface_tuning_status_grain_slider);
        SeekBar statusRadius = mHost.findView(R.id.surface_tuning_status_radius_slider);
        TextView blurValue = mHost.findView(R.id.dock_tuning_blur_value);
        TextView opacityValue = mHost.findView(R.id.dock_tuning_opacity_value);
        TextView grainValue = mHost.findView(R.id.dock_tuning_grain_value);
        TextView dockRadiusValue = mHost.findView(R.id.dock_tuning_radius_value);
        TextView terminalValue = mHost.findView(R.id.dock_tuning_terminal_value);
        TextView sizeValue = mHost.findView(R.id.dock_tuning_size_value);
        TextView iconsValue = mHost.findView(R.id.dock_tuning_icons_value);
        TextView keyboardHeightValue = mHost.findView(R.id.surface_tuning_keyboard_height_value);
        TextView keyboardSpacingValue = mHost.findView(R.id.surface_tuning_keyboard_spacing_value);
        TextView keyboardRadiusValue = mHost.findView(R.id.surface_tuning_keyboard_radius_value);
        TextView keyboardKeyOpacityValue = mHost.findView(R.id.surface_tuning_keyboard_key_opacity_value);
        TextView keyboardBgOpacityValue = mHost.findView(R.id.surface_tuning_keyboard_bg_opacity_value);
        TextView statusBlurValue = mHost.findView(R.id.surface_tuning_status_blur_value);
        TextView statusOpacityValue = mHost.findView(R.id.surface_tuning_status_opacity_value);
        TextView statusGrainValue = mHost.findView(R.id.surface_tuning_status_grain_value);
        TextView statusRadiusValue = mHost.findView(R.id.surface_tuning_status_radius_value);
        MaterialButtonToggleGroup styleGroup = mHost.findView(R.id.dock_tuning_style_group);
        View confirm = mHost.findView(R.id.dock_tuning_confirm);
        View reset = mHost.findView(R.id.surface_tuning_reset);
        View dismiss = mHost.findView(R.id.dock_tuning_dismiss);
        if (controls == null || advanced == null || keyboardColors == null
            || blur == null || opacity == null || grain == null || dockRadius == null
            || terminal == null || size == null || icons == null
            || keyboardHeight == null || keyboardSpacing == null || keyboardRadius == null
            || keyboardKeyOpacity == null || keyboardKeyOpacityValue == null
            || keyboardBgOpacity == null || keyboardBgOpacityValue == null
            || statusBlur == null || statusOpacity == null || statusGrain == null
            || statusRadius == null
            || blurValue == null || opacityValue == null || grainValue == null
            || dockRadiusValue == null
            || terminalValue == null || sizeValue == null
            || iconsValue == null || keyboardHeightValue == null || keyboardSpacingValue == null
            || keyboardRadiusValue == null || statusBlurValue == null
            || statusOpacityValue == null || statusGrainValue == null
            || statusRadiusValue == null || styleGroup == null || confirm == null
            || reset == null) {
            mDockTuningMode = false;
            return;
        }
        // Re-entry with the editor already open (a second tuning intent, say) must not re-baseline:
        // the snapshot below is what "unsaved" is measured against, and recapturing it mid-session
        // would quietly adopt the user's in-progress edits as the thing Discard returns to.
        final boolean freshEditorSession = !mDockTuningMode;
        if (!mDockTuningMode) {
            mDockTuningRestoreExpandedStatus = !prefs().isTopPaneClockCollapsed();
            if (mDockTuningRestoreExpandedStatus) mHost.setTopStatusBarCollapsed(true, false);
        }
        mDockTuningMode = true;
        controls.setVisibility(View.VISIBLE);
        final int initialBlur = prefs().getExtraKeysBlurRadius();
        final int initialOpacity = prefs().getAppBarOpacity();
        final int initialGrain = prefs().getDockGlassGrain();
        final int initialDockRadius = prefs().getAppLauncherDockCornerRadius();
        final int initialTerminal = prefs().getTerminalBackgroundOpacity();
        final boolean initialTerminalBorder = prefs().isTerminalBorderEnabled();
        final int initialTerminalGlassBlur = prefs().getTerminalGlassBlurRadius();
        final int initialTerminalGlassGrain = prefs().getTerminalGlassGrain();
        final int initialTerminalCornerRadius = prefs().getTerminalCornerRadius();
        final int initialTerminalGap = prefs().getTerminalPaneGap();
        final int initialWallpaperDim = prefs().getWallpaperBackdropDim();
        final float initialBarHeight = prefs().getAppLauncherBarHeightScale();
        final int initialSizeIndex = DockLayoutPolicy.nearestSizePresetIndex(initialBarHeight);
        final int initialButtonCount = prefs().getAppLauncherButtonCount();
        final String initialStyle = prefs().getAppLauncherDockStyle();
        final float initialKeyboardHeight = prefs().getInAppKeyboardHeightScale();
        final float initialKeyboardSpacing = prefs().getInAppKeyboardKeyMarginScale();
        final float initialKeyboardRadius = prefs().getInAppKeyboardKeyCornerRadiusDp();
        // The stored value may be the -1 "theme-defined" sentinel; the slider always shows the
        // effective percent, while dismiss restores the raw stored value.
        final int initialKeyboardKeyOpacity = prefs().getInAppKeyboardKeyOpacity();
        final int initialKeyboardKeyOpacityEffective = keyboard() != null
            ? keyboard().getEffectiveKeyOpacityPercent()
            : Math.max(0, initialKeyboardKeyOpacity);
        final int initialKeyboardBgOpacity = prefs().getInAppKeyboardBackgroundOpacity();
        final int initialStatusBlur = prefs().getStatusBarBlurRadius();
        final int initialStatusOpacity = prefs().getStatusBarOpacity();
        final int initialStatusGrain = prefs().getStatusBarGrain();
        final int initialStatusRadius = prefs().getStatusBarCornerRadius();
        final int initialDockInset = prefs().getDockHorizontalInset();
        final int initialKeyboardInset = prefs().getInAppKeyboardHorizontalInset();
        final int initialStatusInset = prefs().getStatusBarHorizontalInset();
        // Captured too, so "Revert all" really means all. These are written straight to
        // preferences by the keyboard colour sub-screen rather than through the sliders, and
        // leaving them out left a half-reverted state behind.
        final String initialLinks = surfaceEditorLinkSignature();
        final String initialKeyboardColorScheme = prefs().getInAppKeyboardColorScheme();
        final String initialKeyboardTheme = prefs().getInAppKeyboardTheme();
        final String initialMaterial = prefs().getSurfaceMaterial();
        final int initialMaterialIntensity = prefs().getSurfaceMaterialIntensity();
        final int[] initialBase = new int[TermuxAppSharedPreferences.SurfaceProperty.values().length];
        for (TermuxAppSharedPreferences.SurfaceProperty property
                : TermuxAppSharedPreferences.SurfaceProperty.values())
            initialBase[property.ordinal()] = prefs().getSurfaceBaseValue(property);

        blur.setProgress(initialBlur);
        opacity.setProgress(initialOpacity);
        grain.setProgress(initialGrain);
        dockRadius.setProgress(editorRadius(TermuxAppSharedPreferences.SurfaceSlot.DOCK, initialDockRadius));
        terminal.setProgress(initialTerminal);
        if (terminalBorder != null) {
            terminalBorder.setOnCheckedChangeListener(null);
            terminalBorder.setChecked(prefs().isTerminalBorderEnabled());
        }
        if (terminalGlassGroup != null) {
            terminalGlassGroup.setVisibility(
                prefs().isTerminalBorderEnabled() ? View.VISIBLE : View.GONE);
        }
        if (terminalGlassBlur != null) terminalGlassBlur.setProgress(initialTerminalGlassBlur);
        if (terminalGlassGrain != null) terminalGlassGrain.setProgress(initialTerminalGlassGrain);
        if (terminalGlassBlurValue != null) terminalGlassBlurValue.setText(
            getString(R.string.termux_dock_tuning_value_dp, initialTerminalGlassBlur));
        if (terminalGlassGrainValue != null) terminalGlassGrainValue.setText(
            getString(R.string.termux_dock_tuning_value_percent, initialTerminalGlassGrain));
        if (terminalGap != null) terminalGap.setProgress(initialTerminalGap);
        if (terminalGapValue != null) terminalGapValue.setText(
            getString(R.string.termux_dock_tuning_value_dp, initialTerminalGap));
        if (wallpaperOpacity != null) wallpaperOpacity.setProgress(initialWallpaperDim);
        if (wallpaperOpacityValue != null) wallpaperOpacityValue.setText(
            getString(R.string.termux_dock_tuning_value_percent, initialWallpaperDim));
        size.setProgress(initialSizeIndex);
        icons.setProgress(Math.max(1, Math.min(20, initialButtonCount)));
        keyboardHeight.setProgress(keyboardEditorProgress(initialKeyboardHeight,
            TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
        keyboardSpacing.setProgress(keyboardEditorProgress(initialKeyboardSpacing,
            TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
        keyboardRadius.setProgress(Math.round(initialKeyboardRadius * 10f));
        keyboardKeyOpacity.setProgress(initialKeyboardKeyOpacityEffective);
        keyboardBgOpacity.setProgress(initialKeyboardBgOpacity);
        statusBlur.setProgress(initialStatusBlur);
        statusOpacity.setProgress(initialStatusOpacity);
        statusGrain.setProgress(initialStatusGrain);
        statusRadius.setProgress(editorRadius(TermuxAppSharedPreferences.SurfaceSlot.STATUS, initialStatusRadius));
        blurValue.setText(getString(R.string.termux_dock_tuning_value_dp, initialBlur));
        opacityValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialOpacity));
        grainValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialGrain));
        dockRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp,
            editorRadius(TermuxAppSharedPreferences.SurfaceSlot.DOCK, initialDockRadius)));
        terminalValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialTerminal));
        sizeValue.setText(dockSizePresetLabel(initialSizeIndex));
        iconsValue.setText(Integer.toString(Math.max(1, initialButtonCount)));
        keyboardHeightValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            keyboardHeight.getProgress()));
        keyboardSpacingValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            keyboardSpacing.getProgress()));
        keyboardRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp,
            Math.round(initialKeyboardRadius)));
        keyboardKeyOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            initialKeyboardKeyOpacityEffective));
        keyboardBgOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            initialKeyboardBgOpacity));
        statusBlurValue.setText(getString(R.string.termux_dock_tuning_value_dp, initialStatusBlur));
        statusOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            initialStatusOpacity));
        statusGrainValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            initialStatusGrain));
        statusRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp,
            editorRadius(TermuxAppSharedPreferences.SurfaceSlot.STATUS, initialStatusRadius)));
        styleGroup.clearOnButtonCheckedListeners();
        styleGroup.check(SegmentedPillPreference.VALUE_ROUNDED.equals(initialStyle)
            ? R.id.dock_tuning_style_capsule : R.id.dock_tuning_style_default);
        bindAdvancedToggle();
        // A settings deep link ("Customize keyboard appearance") lands on that surface's group
        // inside the fold; opened plainly, the fold starts closed and the shared page is the editor.
        TermuxAppSharedPreferences.SurfaceSlot deepLinkSlot = slotForSectionKey(initialSection);
        if (freshEditorSession)
            setAdvancedExpanded(deepLinkSlot != null);
        else if (deepLinkSlot != null)
            setAdvancedExpanded(true);
        if (deepLinkSlot != null)
            scrollToSlotGroup(deepLinkSlot);

        blur.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                blurValue.setText(getString(R.string.termux_dock_tuning_value_dp, progress));
                if (fromUser) {
                    detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(seekBar.getId()));
                    writeSurfaceBlur(SURFACE_TUNING_TARGET_DOCK, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_BLUR);
                }
            }
        });
        opacity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacityValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(seekBar.getId()));
                    writeSurfaceOpacity(SURFACE_TUNING_TARGET_DOCK, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_GLASS);
                }
            }
        });
        grain.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                grainValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(seekBar.getId()));
                    writeSurfaceGrain(SURFACE_TUNING_TARGET_DOCK, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_GLASS);
                }
            }
        });
        dockRadius.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                dockRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp, progress));
                if (fromUser) {
                    detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(seekBar.getId()));
                    writeSurfaceCornerRadius(SURFACE_TUNING_TARGET_DOCK, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_GEOMETRY | TUNING_PREVIEW_SURFACES);
                }
            }
        });
        terminal.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener(
            R.string.termux_surface_tuning_peek_terminal_opacity) {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                terminalValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    peekReadout(terminalValue.getText());
                    detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(seekBar.getId()));
                    writeSurfaceOpacity(SURFACE_TUNING_TARGET_TERMINAL, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_SURFACES | TUNING_PREVIEW_KEYBOARD);
                }
            }
        });
        if (terminalBorder != null) {
            terminalBorder.setOnCheckedChangeListener((button, isChecked) -> {
                prefs().setTerminalBorderEnabled(isChecked);
                if (terminalGlassGroup != null) {
                    terminalGlassGroup.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
                applyDockTuningStructuralPreview();
            });
        }
        if (terminalGlassBlur != null) {
            terminalGlassBlur.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener(
                R.string.termux_surface_tuning_peek_terminal_blur) {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (terminalGlassBlurValue != null) terminalGlassBlurValue.setText(
                        getString(R.string.termux_dock_tuning_value_dp, progress));
                    if (fromUser) {
                        peekReadout(getString(R.string.termux_dock_tuning_value_dp, progress));
                        detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(seekBar.getId()));
                        writeSurfaceBlur(SURFACE_TUNING_TARGET_TERMINAL, progress);
                        requestDockTuningPreview(TUNING_PREVIEW_SURFACES);
                    }
                }
            });
        }
        if (terminalGlassGrain != null) {
            terminalGlassGrain.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener(
                R.string.termux_surface_tuning_peek_terminal_grain) {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (terminalGlassGrainValue != null) terminalGlassGrainValue.setText(
                        getString(R.string.termux_dock_tuning_value_percent, progress));
                    if (fromUser) {
                        peekReadout(getString(R.string.termux_dock_tuning_value_percent, progress));
                        detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(seekBar.getId()));
                        writeSurfaceGrain(SURFACE_TUNING_TARGET_TERMINAL, progress);
                        requestDockTuningPreview(TUNING_PREVIEW_SURFACES);
                    }
                }
            });
        }
        if (terminalGap != null) {
            terminalGap.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener(
                R.string.termux_surface_tuning_peek_terminal_gap) {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (terminalGapValue != null) terminalGapValue.setText(
                        getString(R.string.termux_dock_tuning_value_dp, progress));
                    if (fromUser) {
                        setSurfaceTuningPeekReadout(getString(terminalGapPeekLabelRes()),
                            getString(R.string.termux_dock_tuning_value_dp, progress));
                        prefs().setTerminalPaneGap(progress);
                        // The gap is laid out by the split tree, so it needs a re-render rather
                        // than a restyle; the panes and their shells are reused across it.
                        mHost.refreshPaneLayout();
                        // On the default surface the same knob is the terminal's outer air too.
                        requestDockTuningPreview(TUNING_PREVIEW_SURFACES);
                    }
                }
            });
        }
        if (wallpaperOpacity != null) {
            wallpaperOpacity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener(
                R.string.termux_surface_tuning_peek_wallpaper_opacity) {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (wallpaperOpacityValue != null) wallpaperOpacityValue.setText(
                        getString(R.string.termux_dock_tuning_value_percent, progress));
                    if (fromUser) {
                        peekReadout(getString(R.string.termux_dock_tuning_value_percent, progress));
                        prefs().setWallpaperBackdropDim(progress);
                        requestDockTuningPreview(TUNING_PREVIEW_SURFACES);
                    }
                }
            });
        }
        size.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener(
            R.string.termux_surface_tuning_peek_dock_size) {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int index = Math.max(0, Math.min(DockLayoutPolicy.sizePresetCount() - 1, progress));
                sizeValue.setText(dockSizePresetLabel(index));
                if (fromUser) {
                    peekReadout(sizeValue.getText());
                    prefs().setAppLauncherBarHeightScale(DockLayoutPolicy.sizePreset(index));
                    requestDockTuningPreview(TUNING_PREVIEW_GEOMETRY);
                }
            }
        });
        icons.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener(
            R.string.termux_surface_tuning_peek_dock_icons) {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int count = Math.max(1, progress);
                iconsValue.setText(Integer.toString(count));
                if (fromUser) {
                    peekReadout(Integer.toString(count));
                    prefs().setAppLauncherButtonCount(count);
                    requestDockTuningPreview(TUNING_PREVIEW_GEOMETRY);
                }
            }
        });
        keyboardColors.setOnClickListener(view -> mHost.openKeyboardColors());
        keyboardHeight.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener(
            R.string.termux_surface_tuning_peek_keyboard_height) {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardHeightValue.setText(getString(R.string.termux_dock_tuning_value_percent,
                    progress));
                if (!fromUser) return;
                peekReadout(keyboardHeightValue.getText());
                float scale = keyboardEditorValue(progress,
                    TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE);
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorHeightScale(scale);
                prefs().setInAppKeyboardHeightScale(scale);
                updateSurfaceEditorDirtyBadge();
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                super.onStopTrackingTouch(seekBar);
                prefs().setInAppKeyboardHeightScale(keyboardEditorValue(seekBar.getProgress(),
                    TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
            }
        });
        keyboardSpacing.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardSpacingValue.setText(getString(R.string.termux_dock_tuning_value_percent,
                    progress));
                if (!fromUser) return;
                float margin = keyboardEditorValue(progress,
                    TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                    TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE);
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyMarginScale(margin);
                prefs().setInAppKeyboardKeyMarginScale(margin);
                updateSurfaceEditorDirtyBadge();
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs().setInAppKeyboardKeyMarginScale(keyboardEditorValue(seekBar.getProgress(),
                    TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                    TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
            }
        });
        keyboardRadius.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp,
                    Math.round(progress / 10f)));
                if (!fromUser) return;
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyCornerRadiusDp(progress / 10f);
                prefs().setInAppKeyboardKeyCornerRadiusDp(progress / 10f);
                updateSurfaceEditorDirtyBadge();
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs().setInAppKeyboardKeyCornerRadiusDp(seekBar.getProgress() / 10f);
            }
        });
        keyboardKeyOpacity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardKeyOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
                    progress));
                // Scoped preview: repaints only the keyboard view, never the glass pipeline.
                if (!fromUser) return;
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyOpacity(progress);
                prefs().setInAppKeyboardKeyOpacity(progress);
                updateSurfaceEditorDirtyBadge();
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs().setInAppKeyboardKeyOpacity(seekBar.getProgress());
            }
        });
        keyboardBgOpacity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardBgOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
                    progress));
                // The render path reads this pref, so write-then-re-render is the live preview.
                // Leaving 100 also flips the keyboard off the unified dock material, which the
                // coalesced glass re-render (backdrop dirty + accessory sync) already handles.
                if (fromUser) {
                    detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(seekBar.getId()));
                    prefs().setInAppKeyboardBackgroundOpacity(progress);
                    requestDockTuningPreview(TUNING_PREVIEW_GLASS);
                }
            }
        });
        styleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked)
                return;
            String style = checkedId == R.id.dock_tuning_style_capsule
                ? SegmentedPillPreference.VALUE_ROUNDED : SegmentedPillPreference.VALUE_DEFAULT;
            if (!style.equals(prefs().getAppLauncherDockStyle())) {
                prefs().setAppLauncherDockStyle(style);
                syncSurfaceTuningStyleDependentControls();
                syncSurfaceInheritanceUi();
                applyDockTuningStructuralPreview();
            }
        });
        bindStatusSeekBar(statusBlur, statusBlurValue, true,
            TUNING_PREVIEW_BLUR | TUNING_PREVIEW_SURFACES,
            value -> writeSurfaceBlur(SURFACE_TUNING_TARGET_STATUS, value));
        bindStatusSeekBar(statusOpacity, statusOpacityValue, false,
            TUNING_PREVIEW_SURFACES,
            value -> writeSurfaceOpacity(SURFACE_TUNING_TARGET_STATUS, value));
        bindStatusSeekBar(statusGrain, statusGrainValue, false,
            TUNING_PREVIEW_SURFACES,
            value -> writeSurfaceGrain(SURFACE_TUNING_TARGET_STATUS, value));
        // Radius also reshapes the dock capsule when "match all surfaces" is on.
        bindStatusSeekBar(statusRadius, statusRadiusValue, true,
            TUNING_PREVIEW_SURFACES | TUNING_PREVIEW_GEOMETRY,
            value -> writeSurfaceCornerRadius(SURFACE_TUNING_TARGET_STATUS, value));
        bindSurfaceTuningGestures();
        bindMaterialMacro();
        bindSurfaceInheritanceChips();
        bindSurfaceReattachAll();
        bindTerminalRadiusRow();
        syncTerminalRadiusRow();
        syncTerminalGapLabel();
        reset.setOnClickListener(view -> {
            // One page, one reset: shipped defaults for everything the editor owns. Every surface
            // goes back on Base first, then Base itself takes the shipped numbers — the fresh
            // install state — so no legacy per-surface key needs writing at all: an attached link
            // never reads its raw key, and writing one through the link would move Base twice.
            for (TermuxAppSharedPreferences.SurfaceSlot slot
                    : TermuxAppSharedPreferences.SurfaceSlot.values())
                prefs().reattachSurface(slot);
            prefs().setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.BLUR,
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR);
            prefs().setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.OPACITY,
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY);
            prefs().setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.GRAIN,
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN);
            prefs().setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.CORNER_RADIUS,
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS);
            prefs().setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.SIDE_GAP,
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP);
            // The shipped triple above is glass at 50, so the macro keys agree with it by taking
            // their own defaults.
            prefs().setSurfaceMaterial(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_MATERIAL);
            prefs().setSurfaceMaterialIntensity(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_MATERIAL_INTENSITY);
            // The rest is outside the cascade: geometry, shape and the keyboard's own metrics.
            prefs().setAppLauncherBarHeightScale(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT);
            prefs().setAppLauncherButtonCount(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_BUTTON_COUNT);
            prefs().setAppLauncherDockStyle(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE);
            prefs().setInAppKeyboardHeightScale(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE);
            prefs().setInAppKeyboardKeyMarginScale(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE);
            prefs().setInAppKeyboardKeyCornerRadiusDp(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP);
            prefs().setInAppKeyboardKeyOpacity(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
            if (keyboard() != null) {
                keyboard().previewSurfaceEditorHeightScale(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE);
                keyboard().previewSurfaceEditorKeyOpacity(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
            }
            prefs().setTerminalBorderEnabled(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_TERMINAL_BORDER_ENABLED);
            prefs().setTerminalCornerRadius(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_CORNER_RADIUS);
            syncTerminalRadiusRow();
            prefs().setWallpaperBackdropDim(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM);
            prefs().setTerminalPaneGap(
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP);
            mHost.refreshPaneLayout();
            mHost.applyTerminalSurfaceAppearance();
            blur.setProgress(prefs().getExtraKeysBlurRadius());
            opacity.setProgress(prefs().getAppBarOpacity());
            grain.setProgress(prefs().getDockGlassGrain());
            dockRadius.setProgress(editorRadius(TermuxAppSharedPreferences.SurfaceSlot.DOCK, prefs().getAppLauncherDockCornerRadius()));
            size.setProgress(DockLayoutPolicy.nearestSizePresetIndex(prefs().getAppLauncherBarHeightScale()));
            icons.setProgress(prefs().getAppLauncherButtonCount());
            styleGroup.check(SegmentedPillPreference.VALUE_ROUNDED.equals(
                prefs().getAppLauncherDockStyle())
                ? R.id.dock_tuning_style_capsule : R.id.dock_tuning_style_default);
            keyboardHeight.setProgress(keyboardEditorProgress(
                prefs().getInAppKeyboardHeightScale(),
                TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
            keyboardSpacing.setProgress(keyboardEditorProgress(
                prefs().getInAppKeyboardKeyMarginScale(),
                TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
            keyboardRadius.setProgress(Math.round(prefs().getInAppKeyboardKeyCornerRadiusDp() * 10f));
            keyboardKeyOpacity.setProgress(keyboard() != null
                ? keyboard().getEffectiveKeyOpacityPercent()
                : Math.max(0, prefs().getInAppKeyboardKeyOpacity()));
            keyboardBgOpacity.setProgress(prefs().getInAppKeyboardBackgroundOpacity());
            statusBlur.setProgress(prefs().getStatusBarBlurRadius());
            statusOpacity.setProgress(prefs().getStatusBarOpacity());
            statusGrain.setProgress(prefs().getStatusBarGrain());
            statusRadius.setProgress(editorRadius(TermuxAppSharedPreferences.SurfaceSlot.STATUS, prefs().getStatusBarCornerRadius()));
            terminal.setProgress(prefs().getTerminalBackgroundOpacity());
            if (terminalBorder != null)
                terminalBorder.setChecked(prefs().isTerminalBorderEnabled());
            if (terminalGlassGroup != null) terminalGlassGroup.setVisibility(
                prefs().isTerminalBorderEnabled() ? View.VISIBLE : View.GONE);
            if (terminalGlassBlur != null)
                terminalGlassBlur.setProgress(prefs().getTerminalGlassBlurRadius());
            if (terminalGlassGrain != null)
                terminalGlassGrain.setProgress(prefs().getTerminalGlassGrain());
            if (terminalGap != null)
                terminalGap.setProgress(prefs().getTerminalPaneGap());
            if (wallpaperOpacity != null)
                wallpaperOpacity.setProgress(prefs().getWallpaperBackdropDim());
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_DOCK);
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_KEYBOARD);
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_STATUS);
            syncSurfaceInheritanceUi();
            applyDockTuningStructuralPreview();
        });
        confirm.setOnClickListener(view -> exitDockTuningMode());
        // Done is the only commit. The ✕ and the back press both route through the unsaved-changes
        // gate, so the two agree with each other - which was the original defect - without the
        // close glyph silently throwing work away, which was the other half of it.
        if (dismiss != null) {
            dismiss.setOnClickListener(view -> requestClose());
        }
        final Runnable revertAll = () -> {
                // Restores the values captured when tuning began.
                restoreSurfaceEditorLinks(initialLinks);
                prefs().setExtraKeysBlurRadius(initialBlur);
                prefs().setAppBarOpacity(initialOpacity);
                prefs().setDockGlassGrain(initialGrain);
                prefs().setAppLauncherDockCornerRadius(initialDockRadius);
                prefs().setTerminalBackgroundOpacity(initialTerminal);
                prefs().setTerminalBorderEnabled(initialTerminalBorder);
                prefs().setTerminalGlassBlurRadius(initialTerminalGlassBlur);
                prefs().setTerminalGlassGrain(initialTerminalGlassGrain);
                if (prefs().getTerminalCornerRadius() != initialTerminalCornerRadius) {
                    prefs().setTerminalCornerRadius(initialTerminalCornerRadius);
                    mHost.applyTerminalSurfaceAppearance();
                }
                prefs().setWallpaperBackdropDim(initialWallpaperDim);
                if (prefs().getTerminalPaneGap() != initialTerminalGap) {
                    prefs().setTerminalPaneGap(initialTerminalGap);
                    mHost.refreshPaneLayout();
                    mHost.applyTerminalSurfaceAppearance();
                }
                prefs().setAppLauncherBarHeightScale(initialBarHeight);
                prefs().setAppLauncherButtonCount(initialButtonCount);
                prefs().setAppLauncherDockStyle(initialStyle);
                prefs().setInAppKeyboardHeightScale(initialKeyboardHeight);
                prefs().setInAppKeyboardKeyMarginScale(initialKeyboardSpacing);
                prefs().setInAppKeyboardKeyCornerRadiusDp(initialKeyboardRadius);
                prefs().setInAppKeyboardKeyOpacity(initialKeyboardKeyOpacity);
                prefs().setInAppKeyboardBackgroundOpacity(initialKeyboardBgOpacity);
                prefs().setStatusBarBlurRadius(initialStatusBlur);
                prefs().setStatusBarOpacity(initialStatusOpacity);
                prefs().setStatusBarGrain(initialStatusGrain);
                prefs().setStatusBarCornerRadius(initialStatusRadius);
                prefs().setDockHorizontalInset(initialDockInset);
                prefs().setInAppKeyboardHorizontalInset(initialKeyboardInset);
                prefs().setStatusBarHorizontalInset(initialStatusInset);
                prefs().setInAppKeyboardColorScheme(initialKeyboardColorScheme);
                prefs().setInAppKeyboardTheme(initialKeyboardTheme);
                // The legacy setters above restore Base through whichever links were attached, but
                // a property every surface had detached leaves Base itself unrestored - and the
                // macro writes Base directly - so the shared layer is put back explicitly, last,
                // when the links are already back in their entry shape.
                for (TermuxAppSharedPreferences.SurfaceProperty property
                        : TermuxAppSharedPreferences.SurfaceProperty.values())
                    prefs().setSurfaceBaseValue(property, initialBase[property.ordinal()]);
                prefs().setSurfaceMaterial(initialMaterial);
                prefs().setSurfaceMaterialIntensity(initialMaterialIntensity);
                if (keyboard() != null) {
                    keyboard().previewSurfaceEditorHeightScale(initialKeyboardHeight);
                    keyboard().previewSurfaceEditorKeyOpacity(initialKeyboardKeyOpacity);
                    // The colour scheme and theme are read at render time, so the keyboard has to
                    // be told to re-read them; the preview calls above only touch geometry.
                    keyboard().onPreferencesReloaded();
                }
                applyDockTuningStructuralPreview();
                exitDockTuningMode();
        };
        if (freshEditorSession || mSurfaceEditorEntrySignature == null) {
            mSurfaceEditorRevert = revertAll;
            mSurfaceEditorEntrySignature = surfaceEditorStateSignature();
        }
        syncSurfaceInheritanceUi();
        updateSurfaceEditorDirtyBadge();
        controls.bringToFront();
        setSurfaceTuningGestureOverlayVisible(true);
        registerDockTuningLayoutListener(controls);
        controls.post(this::adjustDockTuningCardHeight);
    }

    /**
     * The surface a settings deep link targets, or null for a plain open. The section names are
     * the ones the deep links have always sent; "sessions" and "other" are what older callers and
     * stored intents said before the sessions demotion and the terminal rename.
     */
    @Nullable
    private TermuxAppSharedPreferences.SurfaceSlot slotForSectionKey(@Nullable String section) {
        if ("dock".equals(section))
            return TermuxAppSharedPreferences.SurfaceSlot.DOCK;
        if ("keyboard".equals(section))
            return TermuxAppSharedPreferences.SurfaceSlot.KEYBOARD;
        if ("status".equals(section) || "sessions".equals(section))
            return TermuxAppSharedPreferences.SurfaceSlot.STATUS;
        if ("terminal".equals(section) || "other".equals(section))
            return TermuxAppSharedPreferences.SurfaceSlot.CANVAS;
        return null;
    }

    /**
     * Inflates the shared rows from {@link SurfaceEditorRows} into their surface groups, once.
     * Each inflated slider, value and chip takes the row's historical view id, so every binding
     * and sync path keeps finding its controls by id exactly as it did when they were hand-laid.
     */
    private void ensureGeneratedRows() {
        ViewGroup probe = mHost.findView(R.id.surface_editor_rows_dock);
        if (probe == null || probe.getChildCount() > 0)
            return;
        android.view.LayoutInflater inflater =
            android.view.LayoutInflater.from(mHost.context());
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            ViewGroup container = generatedRowContainer(row);
            if (container == null)
                continue;
            View rowView = inflater.inflate(R.layout.surface_editor_row, container, false);
            TextView label = rowView.findViewById(R.id.surface_editor_row_label);
            SeekBar slider = rowView.findViewById(R.id.surface_editor_row_slider);
            TextView value = rowView.findViewById(R.id.surface_editor_row_value);
            TextView chip = rowView.findViewById(R.id.surface_editor_row_chip);
            slider.setId(row.sliderId);
            value.setId(row.valueId);
            chip.setId(row.chipId);
            slider.setMax(row.max);
            label.setText(row.labelRes);
            label.setLabelFor(row.sliderId);
            container.addView(rowView);
        }
    }

    /**
     * Where a shared row renders. The terminal's blur and grain sit inside the border-revealed
     * glass group so the Border switch keeps hiding them; its opacity stands above the switch.
     */
    @Nullable
    private ViewGroup generatedRowContainer(@NonNull SurfaceEditorRows.Row row) {
        switch (row.slot) {
            case DOCK:
                return mHost.findView(R.id.surface_editor_rows_dock);
            case KEYBOARD:
                return mHost.findView(R.id.surface_editor_rows_keyboard);
            case STATUS:
                return mHost.findView(R.id.surface_editor_rows_status);
            default:
                return row.property == TermuxAppSharedPreferences.SurfaceProperty.OPACITY
                    ? mHost.findView(R.id.surface_editor_rows_canvas_top)
                    : mHost.findView(R.id.surface_editor_rows_canvas_glass);
        }
    }

    private void bindAdvancedToggle() {
        View toggle = mHost.findView(R.id.surface_editor_advanced_toggle);
        if (toggle == null)
            return;
        toggle.setOnClickListener(view -> {
            View advanced = mHost.findView(R.id.surface_editor_advanced);
            setAdvancedExpanded(advanced != null && advanced.getVisibility() != View.VISIBLE);
        });
    }

    private void setAdvancedExpanded(boolean expanded) {
        View advanced = mHost.findView(R.id.surface_editor_advanced);
        TextView chevron = mHost.findView(R.id.surface_editor_advanced_chevron);
        if (advanced == null)
            return;
        advanced.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (chevron != null)
            chevron.setText(expanded
                ? R.string.termux_surface_tuning_advanced_chevron_open
                : R.string.termux_surface_tuning_advanced_chevron_closed);
        ScrollView scroll = mHost.findView(R.id.dock_tuning_scroll);
        if (scroll != null)
            scroll.post(this::adjustDockTuningCardHeight);
    }

    private int slotGroupId(@NonNull TermuxAppSharedPreferences.SurfaceSlot slot) {
        switch (slot) {
            case KEYBOARD: return R.id.surface_editor_group_keyboard;
            case STATUS: return R.id.surface_editor_group_status;
            case CANVAS: return R.id.surface_editor_group_terminal;
            default: return R.id.surface_editor_group_dock;
        }
    }

    /** Brings a deep-linked surface's group to the top of the scroll once it has a layout. */
    private void scrollToSlotGroup(@NonNull TermuxAppSharedPreferences.SurfaceSlot slot) {
        ScrollView scroll = mHost.findView(R.id.dock_tuning_scroll);
        View group = mHost.findView(slotGroupId(slot));
        if (scroll == null || group == null)
            return;
        scroll.post(() -> {
            View scrollChild = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
            if (scrollChild == null)
                return;
            int top = 0;
            View walk = group;
            while (walk != null && walk != scrollChild) {
                top += walk.getTop();
                android.view.ViewParent parent = walk.getParent();
                walk = parent instanceof View ? (View) parent : null;
            }
            scroll.smoothScrollTo(0, Math.max(0, top));
        });
    }

    private int editorRadius(TermuxAppSharedPreferences.SurfaceSlot slot, int value) {
        if (value < 0)
            return TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(slot, mHost.isRoundedDockStyle());
        return Math.min(40, value);
    }

    public static int keyboardEditorProgress(float value, float minValue, float maxValue) {
        if (Float.isNaN(value) || Float.isInfinite(value) || maxValue <= minValue)
            return 0;
        float normalized = (value - minValue) / (maxValue - minValue);
        return Math.max(0, Math.min(100, Math.round(normalized * 100f)));
    }

    public static float keyboardEditorValue(int progress, float minValue, float maxValue) {
        int normalizedProgress = Math.max(0, Math.min(100, progress));
        return minValue + ((maxValue - minValue) * normalizedProgress / 100f);
    }

    private interface StatusValueSetter {
        void set(int value);
    }

    private void bindStatusSeekBar(SeekBar seekBar, TextView valueView, boolean dp,
                                   int previewScopes, StatusValueSetter setter) {
        seekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueView.setText(getString(dp ? R.string.termux_dock_tuning_value_dp
                    : R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(bar.getId()));
                    setter.set(progress);
                    requestDockTuningPreview(previewScopes);
                }
            }
        });
    }

    private static final int SURFACE_TUNING_TARGET_DOCK = 0;
    private static final int SURFACE_TUNING_TARGET_KEYBOARD = 1;
    private static final int SURFACE_TUNING_TARGET_STATUS = 2;
    private static final int SURFACE_TUNING_TARGET_TERMINAL = 3;
    /** The terminal glass slider's own ceiling; the dock's blur range runs further. */
    private static final int TERMINAL_GLASS_MAX_BLUR_DP = 30;

    // ---------------------------------------------------- surface glass, one writer per control
    //
    // The dock and the status bar keep separate preferences for the same five glass properties,
    // which is what lets them be tuned apart. Every editor control funnels through the writers
    // below so "match all surfaces" is a single branch per property instead of a rule duplicated
    // at each slider. The in-app keyboard has no blur/opacity/grain/radius of its own — it renders
    // on the dock's glass — so only padding fans out to all three.

    /** Every link flag as one string, so the undo path can restore the shape as well as the numbers. */
    @NonNull
    private String surfaceEditorLinkSignature() {
        if (prefs() == null)
            return "";
        StringBuilder out = new StringBuilder(64);
        for (TermuxAppSharedPreferences.SurfaceSlot slot
                : TermuxAppSharedPreferences.SurfaceSlot.values()) {
            for (TermuxAppSharedPreferences.SurfaceProperty property
                    : TermuxAppSharedPreferences.SurfaceProperty.values()) {
                out.append(prefs().isSurfaceInheriting(slot, property) ? '1' : '0');
            }
        }
        return out.toString();
    }

    /** Puts the links back the way {@link #surfaceEditorLinkSignature()} found them. */
    private void restoreSurfaceEditorLinks(@Nullable String signature) {
        if (prefs() == null || signature == null)
            return;
        int index = 0;
        for (TermuxAppSharedPreferences.SurfaceSlot slot
                : TermuxAppSharedPreferences.SurfaceSlot.values()) {
            for (TermuxAppSharedPreferences.SurfaceProperty property
                    : TermuxAppSharedPreferences.SurfaceProperty.values()) {
                if (index >= signature.length())
                    return;
                prefs().setSurfaceInheriting(slot, property,
                    signature.charAt(index++) == '1');
            }
        }
    }


    /**
     * The Docked terminal frame's own radius. Floating derives the frame from the capsule, so the
     * row only shows in Docked — the one mode where this number acts. Lives outside the Base
     * cascade for the same reason.
     */
    private void bindTerminalRadiusRow() {
        SeekBar slider = mHost.findView(R.id.dock_tuning_terminal_radius_slider);
        if (slider == null)
            return;
        slider.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                TextView value = mHost.findView(R.id.dock_tuning_terminal_radius_value);
                if (value != null)
                    value.setText(getString(R.string.termux_dock_tuning_value_dp, progress));
                if (fromUser && prefs() != null) {
                    prefs().setTerminalCornerRadius(progress);
                    // The radius reshapes the terminal's field, its frame line and its pane clip
                    // together, so it needs the surface re-apply, not just the border pass.
                    requestDockTuningPreview(TUNING_PREVIEW_SURFACES);
                }
            }
        });
    }

    /**
     * The gap knob answers to two names because it does two jobs. Docked spends it on the
     * terminal's outer air as well as on the space between tiled panes, so there it is a margin;
     * Floating insets the frame from the dock's capsule instead and the knob only gaps the panes.
     */
    @StringRes
    private int terminalGapLabelRes() {
        return mHost.isRoundedDockStyle() ? R.string.termux_dock_tuning_terminal_inner_padding
            : R.string.termux_dock_tuning_terminal_margin;
    }

    @StringRes
    private int terminalGapPeekLabelRes() {
        return mHost.isRoundedDockStyle() ? R.string.termux_surface_tuning_peek_terminal_gap
            : R.string.termux_surface_tuning_peek_terminal_margin;
    }

    private void syncTerminalGapLabel() {
        TextView label = mHost.findView(R.id.dock_tuning_terminal_gap_label);
        if (label != null)
            label.setText(terminalGapLabelRes());
    }

    private void syncTerminalRadiusRow() {
        View row = mHost.findView(R.id.dock_tuning_terminal_radius_row);
        if (row == null || prefs() == null)
            return;
        row.setVisibility(mHost.isRoundedDockStyle() ? View.GONE : View.VISIBLE);
        int current = prefs().getTerminalCornerRadius();
        setSeekBarProgress(R.id.dock_tuning_terminal_radius_slider, current);
        TextView value = mHost.findView(R.id.dock_tuning_terminal_radius_value);
        if (value != null)
            value.setText(getString(R.string.termux_dock_tuning_value_dp, current));
    }

    private void setSeekBarProgress(int sliderId, int progress) {
        SeekBar slider = mHost.findView(sliderId);
        if (slider != null && slider.getProgress() != progress)
            slider.setProgress(progress);
    }

    private void writeSurfaceBlur(int target, int value) {
        if (prefs() == null) return;
        if (target == SURFACE_TUNING_TARGET_DOCK)
            prefs().setExtraKeysBlurRadius(value);
        else if (target == SURFACE_TUNING_TARGET_STATUS)
            prefs().setStatusBarBlurRadius(value);
        else if (target == SURFACE_TUNING_TARGET_TERMINAL)
            prefs().setTerminalGlassBlurRadius(
                Math.min(value, TERMINAL_GLASS_MAX_BLUR_DP));
    }

    /**
     * "Match all surfaces" means every surface, which now includes the ones that were left out:
     * the terminal's own tint, the keyboard's background and the sessions panel. Leaving them out
     * is what made the switch feel unreliable — a slider moved two surfaces, and only toggling the
     * switch off and on levelled the rest, because the toggle re-levels everything through these
     * same writers while a slider only wrote its own target.
     */
    private void writeSurfaceOpacity(int target, int value) {
        if (prefs() == null) return;
        if (target == SURFACE_TUNING_TARGET_DOCK)
            prefs().setAppBarOpacity(value);
        else if (target == SURFACE_TUNING_TARGET_STATUS)
            prefs().setStatusBarOpacity(value);
        else if (target == SURFACE_TUNING_TARGET_TERMINAL)
            prefs().setTerminalBackgroundOpacity(value);
    }

    private void writeSurfaceGrain(int target, int value) {
        if (prefs() == null) return;
        if (target == SURFACE_TUNING_TARGET_DOCK)
            prefs().setDockGlassGrain(value);
        else if (target == SURFACE_TUNING_TARGET_STATUS)
            prefs().setStatusBarGrain(value);
        else if (target == SURFACE_TUNING_TARGET_TERMINAL)
            prefs().setTerminalGlassGrain(value);
    }

    private void writeSurfaceCornerRadius(int target, int value) {
        if (prefs() == null) return;
        if (target == SURFACE_TUNING_TARGET_DOCK)
            prefs().setAppLauncherDockCornerRadius(value);
        else if (target == SURFACE_TUNING_TARGET_STATUS)
            prefs().setStatusBarCornerRadius(value);
    }

    /** A finger travel of 1dp moves a surface edge half a dp, so the 0..48dp span needs ~96dp. */
    /**
     * Every preference the editor can move, in one string. Compared against the value captured on
     * entry to answer "is there anything to lose here?" - cheaper and far harder to get wrong than
     * thirty hand-written field comparisons, and it only has to be kept in step in one place.
     */
    @NonNull
    private String surfaceEditorStateSignature() {
        if (prefs() == null)
            return "";
        return new StringBuilder(256)
            .append(prefs().getExtraKeysBlurRadius()).append('|')
            .append(prefs().getAppBarOpacity()).append('|')
            .append(prefs().getDockGlassGrain()).append('|')
            .append(prefs().getAppLauncherDockCornerRadius()).append('|')
            .append(prefs().getAppLauncherBarHeightScale()).append('|')
            .append(prefs().getAppLauncherButtonCount()).append('|')
            .append(prefs().getAppLauncherDockStyle()).append('|')
            .append(prefs().getDockHorizontalInset()).append('|')
            .append(prefs().getInAppKeyboardHeightScale()).append('|')
            .append(prefs().getInAppKeyboardKeyMarginScale()).append('|')
            .append(prefs().getInAppKeyboardKeyCornerRadiusDp()).append('|')
            .append(prefs().getInAppKeyboardKeyOpacity()).append('|')
            .append(prefs().getInAppKeyboardBackgroundOpacity()).append('|')
            .append(prefs().getInAppKeyboardHorizontalInset()).append('|')
            .append(prefs().getInAppKeyboardColorScheme()).append('|')
            .append(prefs().getInAppKeyboardTheme()).append('|')
            .append(prefs().getStatusBarBlurRadius()).append('|')
            .append(prefs().getStatusBarOpacity()).append('|')
            .append(prefs().getStatusBarGrain()).append('|')
            .append(prefs().getStatusBarCornerRadius()).append('|')
            .append(prefs().getStatusBarHorizontalInset()).append('|')
            .append(prefs().getTerminalBackgroundOpacity()).append('|')
            .append(prefs().isTerminalBorderEnabled()).append('|')
            .append(prefs().getTerminalGlassBlurRadius()).append('|')
            .append(prefs().getTerminalGlassGrain()).append('|')
            .append(prefs().getTerminalCornerRadius()).append('|')
            .append(prefs().getTerminalPaneGap()).append('|')
            .append(prefs().getWallpaperBackdropDim()).append('|')
            .append(surfaceEditorLinkSignature())
            .append('|')
            .append(prefs().getSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.BLUR))
            .append('|')
            .append(prefs().getSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.OPACITY))
            .append('|')
            .append(prefs().getSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.GRAIN))
            .append('|')
            .append(prefs().getSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.CORNER_RADIUS))
            .append('|')
            .append(prefs().getSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.SIDE_GAP))
            .append('|')
            .append(prefs().getSurfaceMaterial()).append('|')
            .append(prefs().getSurfaceMaterialIntensity())
            .toString();
    }

    private boolean isSurfaceEditorDirty() {
        return mSurfaceEditorEntrySignature != null
            && !mSurfaceEditorEntrySignature.equals(surfaceEditorStateSignature());
    }

    /** Keeps the header badge in step with the snapshot. Cheap enough to call on every preview. */
    private void updateSurfaceEditorDirtyBadge() {
        View badge = mHost.findView(R.id.surface_tuning_dirty_badge);
        if (badge == null)
            return;
        boolean show = mDockTuningMode && isSurfaceEditorDirty();
        int target = show ? View.VISIBLE : View.GONE;
        if (badge.getVisibility() != target)
            badge.setVisibility(target);
    }

    /**
     * The editor's only exit that is not a commit. Done commits; the ✕ and the back press come
     * here, and when there is something to lose they ask rather than silently choosing for the
     * user - the live write-through means "leave" would otherwise mean "keep" by accident.
     */
    public void requestClose() {
        if (!mDockTuningMode)
            return;
        if (!isSurfaceEditorDirty()) {
            exitDockTuningMode();
            return;
        }
        final Runnable revert = mSurfaceEditorRevert;
        new MaterialAlertDialogBuilder(mHost.context())
            .setTitle(R.string.termux_surface_tuning_unsaved_title)
            .setMessage(R.string.termux_surface_tuning_unsaved_message)
            .setNeutralButton(R.string.termux_surface_tuning_unsaved_keep_editing, null)
            .setNegativeButton(R.string.termux_surface_tuning_unsaved_discard,
                (dialog, which) -> {
                    if (revert != null) revert.run();
                    else exitDockTuningMode();
                })
            .setPositiveButton(R.string.termux_surface_tuning_unsaved_save,
                (dialog, which) -> exitDockTuningMode())
            .show();
    }

    // ------------------------------------------------------------------ surface inheritance UI
    //
    // The model lives in TermuxAppSharedPreferences: every render path already reads a resolved
    // number. What is left here is showing the link and letting the user break or restore it.

    private static final java.util.EnumMap<TermuxAppSharedPreferences.SurfaceSlot, Integer>
        SURFACE_SLOT_REATTACH_CHIPS =
            new java.util.EnumMap<>(TermuxAppSharedPreferences.SurfaceSlot.class);

    static {
        SURFACE_SLOT_REATTACH_CHIPS.put(TermuxAppSharedPreferences.SurfaceSlot.DOCK,
            R.id.surface_editor_reattach_dock);
        SURFACE_SLOT_REATTACH_CHIPS.put(TermuxAppSharedPreferences.SurfaceSlot.KEYBOARD,
            R.id.surface_editor_reattach_keyboard);
        SURFACE_SLOT_REATTACH_CHIPS.put(TermuxAppSharedPreferences.SurfaceSlot.STATUS,
            R.id.surface_editor_reattach_status);
        SURFACE_SLOT_REATTACH_CHIPS.put(TermuxAppSharedPreferences.SurfaceSlot.CANVAS,
            R.id.surface_editor_reattach_canvas);
    }

    /**
     * Where a slider should sit for a stored value. Corner radius carries a "theme-defined"
     * sentinel below zero, which is not a position on a 0-40 track; the slider shows the number the
     * surface will actually use instead - the capsule's own radius while Floating, a straight edge
     * while Docked - so the control is never parked somewhere the surface is not.
     */
    private int surfaceEditorSliderValue(@Nullable TermuxAppSharedPreferences.SurfaceSlot slot,
                                         TermuxAppSharedPreferences.SurfaceProperty property,
                                         int stored) {
        if (property != TermuxAppSharedPreferences.SurfaceProperty.CORNER_RADIUS || stored >= 0)
            return stored;
        return TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(slot, mHost.isRoundedDockStyle());
    }

    /** Formats one row's number in its own unit. */
    private String surfaceRowValueText(@NonNull SurfaceEditorRows.Row row, int value) {
        return getString(row.dp ? R.string.termux_dock_tuning_value_dp
            : R.string.termux_dock_tuning_value_percent, value);
    }

    /**
     * A shared control was moved. While the surface is still following Base this detaches it first,
     * so the change lands on that surface alone rather than dragging every other surface with it -
     * "the same everywhere, except this one thing" is the whole point of the model.
     */
    private void detachSurfaceRowForEdit(@Nullable SurfaceEditorRows.Row row) {
        if (row == null || prefs() == null)
            return;
        if (prefs().isSurfaceInheriting(row.slot, row.property)) {
            prefs().detachSurfaceValue(row.slot, row.property,
                prefs().getSurfaceOverrideValue(row.slot, row.property));
        }
    }

    /** Chip taps put one property back on Base. Dragging is what takes it off. */
    private void bindSurfaceInheritanceChips() {
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            TextView chip = mHost.findView(row.chipId);
            if (chip == null)
                continue;
            chip.setOnClickListener(view -> {
                if (prefs() == null
                    || prefs().isSurfaceInheriting(row.slot, row.property))
                    return;
                prefs().setSurfaceInheriting(row.slot, row.property, true);
                syncSurfaceInheritanceUi();
                applyDockTuningStructuralPreview();
            });
        }
    }

    /** Pushes the resolved numbers and the link state back onto every shared row. */
    private void syncSurfaceInheritanceUi() {
        if (prefs() == null)
            return;
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            boolean inheriting = prefs().isSurfaceInheriting(row.slot, row.property);
            int resolved = surfaceEditorSliderValue(row.slot, row.property, inheriting
                ? prefs().getSurfaceBaseValue(row.property)
                : prefs().getSurfaceOverrideValue(row.slot, row.property));
            resolved = Math.max(0, Math.min(row.max, resolved));

            SeekBar slider = mHost.findView(row.sliderId);
            if (slider != null && slider.getProgress() != resolved)
                slider.setProgress(resolved);
            TextView value = mHost.findView(row.valueId);
            if (value != null)
                value.setText(surfaceRowValueText(row, resolved));

            TextView chip = mHost.findView(row.chipId);
            if (chip == null)
                continue;
            // Both states render: ↗ while the row follows Base — muted, because it is a statement,
            // not a control — and ↺ once the row has its own value, as the way back. Making the
            // attached state visible is what lets "is this slider mine or shared?" be answered
            // before touching anything.
            String slotName = getString(SurfaceEditorRows.slotLabel(row.slot));
            chip.setVisibility(View.VISIBLE);
            chip.setClickable(!inheriting);
            chip.setFocusable(!inheriting);
            chip.setText(inheriting
                ? R.string.termux_surface_tuning_link_inherited
                : R.string.termux_surface_tuning_link_detached);
            chip.setTextColor(getSurfaceLinkChipColor(inheriting));
            chip.setAlpha(inheriting ? 0.6f : 1f);
            chip.setContentDescription(getString(inheriting
                ? R.string.termux_surface_tuning_link_inherited_description
                : R.string.termux_surface_tuning_link_detached_description, slotName));
        }
        syncSurfaceBaseSliders();
        syncSurfaceReattachAllVisibility();
    }

    private int getSurfaceLinkChipColor(boolean inheriting) {
        // Muted while following Base, accented once the row is carrying its own value - the chip is
        // the only place that distinction is visible on a per-row basis.
        return inheriting
            ? mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                R.color.termux_on_surface_variant)
            : mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
                R.color.termux_primary);
    }

    /** The primary section's own controls, plus the "who is still following" line under them. */
    private void syncSurfaceBaseSliders() {
        if (prefs() == null)
            return;
        syncMaterialMacro();
        bindOrSyncBaseSlider(R.id.surface_tuning_base_radius_slider,
            R.id.surface_tuning_base_radius_value,
            TermuxAppSharedPreferences.SurfaceProperty.CORNER_RADIUS, true, 40);
        bindOrSyncBaseSlider(R.id.surface_tuning_base_gap_slider,
            R.id.surface_tuning_base_gap_value,
            TermuxAppSharedPreferences.SurfaceProperty.SIDE_GAP, true, 48);

        TextView followers = mHost.findView(R.id.surface_tuning_base_followers);
        if (followers == null)
            return;
        int detached = 0;
        for (TermuxAppSharedPreferences.SurfaceSlot slot
                : TermuxAppSharedPreferences.SurfaceSlot.values())
            detached += prefs().surfaceOverrideCount(slot);
        followers.setText(detached == 0
            ? getString(R.string.termux_surface_tuning_followers_all)
            : getResources().getQuantityString(
                R.plurals.termux_surface_tuning_followers_some, detached, detached));
    }

    private void bindOrSyncBaseSlider(int sliderId, int valueId,
                                      TermuxAppSharedPreferences.SurfaceProperty property,
                                      boolean dp, int max) {
        SeekBar slider = mHost.findView(sliderId);
        TextView value = mHost.findView(valueId);
        if (slider == null || prefs() == null)
            return;
        int current = Math.max(0, Math.min(max,
            surfaceEditorSliderValue(null, property, prefs().getSurfaceBaseValue(property))));
        if (slider.getTag(R.id.surface_tuning_base_panel) == null) {
            slider.setTag(R.id.surface_tuning_base_panel, Boolean.TRUE);
            slider.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (value != null) value.setText(getString(dp
                        ? R.string.termux_dock_tuning_value_dp
                        : R.string.termux_dock_tuning_value_percent, progress));
                    if (!fromUser || prefs() == null)
                        return;
                    prefs().setSurfaceBaseValue(property, progress);
                    // Everything still following Base moves with it, so the whole editor restates.
                    syncSurfaceInheritanceUi();
                    requestDockTuningPreview(TUNING_PREVIEW_ALL);
                }
            });
        }
        if (slider.getProgress() != current)
            slider.setProgress(current);
        if (value != null) value.setText(getString(dp
            ? R.string.termux_dock_tuning_value_dp
            : R.string.termux_dock_tuning_value_percent, current));
    }

    // ------------------------------------------------------------------------ the material macro
    //
    // Blur, opacity and grain as one decision: a family (Solid / Glass / Frost) and an intensity.
    // The macro writes the mapped triple through the Base setters, so followers move and detached
    // surfaces keep their overrides exactly as any Base edit would; the two macro keys only
    // remember which point the triple came from. A triple no point reproduces renders as Custom -
    // nothing selected, the intensity echoing "Custom" - and is never snapped on entry: touching
    // the macro is the one gesture that re-applies a curve.

    /** Suppresses the toggle listener while sync is restating the group programmatically. */
    private boolean mSyncingMaterialMacro;

    private void bindMaterialMacro() {
        MaterialButtonToggleGroup group = mHost.findView(R.id.surface_tuning_material_group);
        SeekBar intensity = mHost.findView(R.id.surface_tuning_material_intensity_slider);
        if (group == null || intensity == null)
            return;
        group.clearOnButtonCheckedListeners();
        group.addOnButtonCheckedListener((buttons, checkedId, isChecked) -> {
            if (!isChecked || mSyncingMaterialMacro || prefs() == null)
                return;
            applyMaterialMacro(materialForButton(checkedId), prefs().getSurfaceMaterialIntensity());
        });
        intensity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || prefs() == null)
                    return;
                applyMaterialMacro(prefs().getSurfaceMaterial(), progress);
            }
        });
    }

    @NonNull
    private String materialForButton(int buttonId) {
        if (buttonId == R.id.surface_tuning_material_solid)
            return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_SOLID;
        if (buttonId == R.id.surface_tuning_material_frost)
            return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_FROST;
        return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_GLASS;
    }

    private int materialButtonId(@NonNull String material) {
        if (TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_SOLID.equals(material))
            return R.id.surface_tuning_material_solid;
        if (TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_FROST.equals(material))
            return R.id.surface_tuning_material_frost;
        return R.id.surface_tuning_material_glass;
    }

    private void applyMaterialMacro(@NonNull String material, int intensity) {
        if (prefs() == null)
            return;
        prefs().setSurfaceMaterial(material);
        prefs().setSurfaceMaterialIntensity(intensity);
        int[] triple = SurfaceMaterials.triple(material, intensity);
        prefs().setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.BLUR,
            triple[SurfaceMaterials.BLUR]);
        prefs().setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.OPACITY,
            triple[SurfaceMaterials.OPACITY]);
        prefs().setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.GRAIN,
            triple[SurfaceMaterials.GRAIN]);
        // Everything still following Base moves with it, so the whole editor restates. Blur is in
        // the triple, so the pre-blurred wallpaper cache has to go too.
        syncSurfaceInheritanceUi();
        requestDockTuningPreview(TUNING_PREVIEW_ALL);
    }

    /**
     * Restates the macro from the prefs. The stored point is shown only while it still explains
     * the Base triple; a triple something else wrote - an upgrade's hand-tuned numbers, a raw
     * value restored by Discard - deselects the family and says Custom instead of lying.
     */
    private void syncMaterialMacro() {
        MaterialButtonToggleGroup group = mHost.findView(R.id.surface_tuning_material_group);
        SeekBar intensity = mHost.findView(R.id.surface_tuning_material_intensity_slider);
        TextView intensityValue = mHost.findView(R.id.surface_tuning_material_intensity_value);
        if (group == null || intensity == null || prefs() == null)
            return;
        String material = prefs().getSurfaceMaterial();
        int storedIntensity = prefs().getSurfaceMaterialIntensity();
        int[] expected = SurfaceMaterials.triple(material, storedIntensity);
        boolean matches =
            expected[SurfaceMaterials.BLUR] == prefs().getSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.BLUR)
            && expected[SurfaceMaterials.OPACITY] == prefs().getSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.OPACITY)
            && expected[SurfaceMaterials.GRAIN] == prefs().getSurfaceBaseValue(
                TermuxAppSharedPreferences.SurfaceProperty.GRAIN);
        mSyncingMaterialMacro = true;
        try {
            if (matches) {
                int buttonId = materialButtonId(material);
                if (group.getCheckedButtonId() != buttonId)
                    group.check(buttonId);
            } else if (group.getCheckedButtonId() != View.NO_ID) {
                group.clearChecked();
            }
        } finally {
            mSyncingMaterialMacro = false;
        }
        if (intensity.getProgress() != storedIntensity)
            intensity.setProgress(storedIntensity);
        intensity.setAlpha(matches ? 1f : SURFACE_TUNING_DISABLED_ALPHA);
        if (intensityValue != null) {
            intensityValue.setText(matches
                ? getString(R.string.termux_dock_tuning_value_percent, storedIntensity)
                : getString(R.string.termux_surface_tuning_material_custom));
            intensityValue.setAlpha(matches ? 1f : SURFACE_TUNING_DISABLED_ALPHA);
        }
    }

    /** Every group header carries its own whole-surface ↺; a tap puts that surface back on Base. */
    private void bindSurfaceReattachAll() {
        for (java.util.Map.Entry<TermuxAppSharedPreferences.SurfaceSlot, Integer> entry
                : SURFACE_SLOT_REATTACH_CHIPS.entrySet()) {
            View reattach = mHost.findView(entry.getValue());
            if (reattach == null)
                continue;
            final TermuxAppSharedPreferences.SurfaceSlot slot = entry.getKey();
            reattach.setOnClickListener(view -> {
                if (prefs() == null)
                    return;
                prefs().reattachSurface(slot);
                syncSurfaceInheritanceUi();
                applyDockTuningStructuralPreview();
            });
        }
    }

    /** A group's ↺ shows only while that surface has something detached to put back. */
    private void syncSurfaceReattachAllVisibility() {
        if (prefs() == null)
            return;
        for (java.util.Map.Entry<TermuxAppSharedPreferences.SurfaceSlot, Integer> entry
                : SURFACE_SLOT_REATTACH_CHIPS.entrySet()) {
            View reattach = mHost.findView(entry.getValue());
            if (reattach == null)
                continue;
            boolean show = prefs().surfaceOverrideCount(entry.getKey()) > 0;
            reattach.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /** Dimming for an editor control the current dock style makes inapplicable. */
    private static final float SURFACE_TUNING_DISABLED_ALPHA = 0.38f;
    /** Length of the slide when a section needs the card a different height. */
    private static final long SURFACE_TUNING_RESIZE_DURATION_MS = 140L;
    /** Card opacity while a peeking control is being dragged. */
    private static final float SURFACE_TUNING_PEEK_ALPHA = 0.28f;
    private static final long SURFACE_TUNING_PEEK_OUT_MS = 90;
    private static final long SURFACE_TUNING_PEEK_IN_MS = 170;

    /**
     * Fades the editor card out of the way while a control whose surface it covers is being
     * dragged. Purely visual: the card keeps its position and stays hit-testable, so the finger
     * already on the thumb goes on working.
     */
    private void setSurfaceTuningCardPeek(boolean peek) {
        View controls = mHost.findView(R.id.dock_tuning_controls);
        if (controls == null || !mDockTuningMode)
            return;
        controls.animate().cancel();
        controls.animate()
            .alpha(peek ? SURFACE_TUNING_PEEK_ALPHA : 1f)
            .setDuration(peek ? SURFACE_TUNING_PEEK_OUT_MS : SURFACE_TUNING_PEEK_IN_MS)
            .setInterpolator(com.termux.app.terminal.Motion.settle())
            .start();
        if (!peek) hideSurfaceTuningPeekReadout();
    }

    /** The faded card's number, shown over the surface so peeking does not trade one blindness for another. */
    private void setSurfaceTuningPeekReadout(@NonNull CharSequence label, @NonNull CharSequence value) {
        TextView readout = mHost.findView(R.id.surface_tuning_peek_readout);
        if (readout == null || !mDockTuningMode)
            return;
        readout.setText(getString(R.string.termux_surface_tuning_peek_readout, label, value));
        if (readout.getVisibility() != View.VISIBLE) {
            readout.setAlpha(0f);
            readout.setVisibility(View.VISIBLE);
            readout.animate().alpha(1f).setDuration(SURFACE_TUNING_PEEK_OUT_MS).start();
        }
    }

    private void hideSurfaceTuningPeekReadout() {
        TextView readout = mHost.findView(R.id.surface_tuning_peek_readout);
        if (readout == null || readout.getVisibility() != View.VISIBLE)
            return;
        readout.animate().alpha(0f).setDuration(SURFACE_TUNING_PEEK_IN_MS)
            .withEndAction(() -> readout.setVisibility(View.GONE)).start();
    }

    private static final float SURFACE_TUNING_INSET_DRAG_GAIN = 0.5f;
    /** Finger travel that walks the dock across its whole preset height range. */
    private static final float SURFACE_TUNING_DOCK_HEIGHT_DRAG_SPAN_DP = 40f;
    /** How far the capture groups reach above their surface so the border handle is inside. */
    private static final int SURFACE_TUNING_HANDLE_OVERHANG_DP = 14;
    private static final long SURFACE_TUNING_FADE_DURATION_MS = 200;

    private int surfaceTuningInsetDp(int target) {
        if (prefs() == null)
            return TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET;
        switch (target) {
            case SURFACE_TUNING_TARGET_KEYBOARD:
                return prefs().getInAppKeyboardHorizontalInset();
            case SURFACE_TUNING_TARGET_STATUS:
                return prefs().getStatusBarHorizontalInset();
            default:
                return prefs().getDockHorizontalInset();
        }
    }

    private void setSurfaceTuningInsetDp(int target, int insetDp) {
        if (prefs() == null)
            return;
        switch (target) {
            case SURFACE_TUNING_TARGET_KEYBOARD:
                prefs().setInAppKeyboardHorizontalInset(insetDp);
                break;
            case SURFACE_TUNING_TARGET_STATUS:
                prefs().setStatusBarHorizontalInset(insetDp);
                break;
            default:
                prefs().setDockHorizontalInset(insetDp);
                break;
        }
        syncSurfaceTuningInsetSlider(target);
        applyDockTuningStructuralPreview();
    }

    private int surfaceTuningInsetSliderId(int target) {
        switch (target) {
            case SURFACE_TUNING_TARGET_KEYBOARD:
                return R.id.surface_tuning_keyboard_inset_slider;
            case SURFACE_TUNING_TARGET_STATUS:
                return R.id.surface_tuning_status_inset_slider;
            default:
                return R.id.surface_tuning_dock_inset_slider;
        }
    }

    private int surfaceTuningInsetValueId(int target) {
        switch (target) {
            case SURFACE_TUNING_TARGET_KEYBOARD:
                return R.id.surface_tuning_keyboard_inset_value;
            case SURFACE_TUNING_TARGET_STATUS:
                return R.id.surface_tuning_status_inset_value;
            default:
                return R.id.surface_tuning_dock_inset_value;
        }
    }

    private void syncSurfaceTuningInsetSlider(int target) {
        int insetDp = surfaceTuningInsetDp(target);
        SeekBar slider = mHost.findView(surfaceTuningInsetSliderId(target));
        TextView value = mHost.findView(surfaceTuningInsetValueId(target));
        boolean available = mHost.isRoundedDockStyle();
        if (slider != null) {
            if (slider.getProgress() != insetDp)
                slider.setProgress(insetDp);
            slider.setEnabled(available);
            slider.setAlpha(available ? 1f : SURFACE_TUNING_DISABLED_ALPHA);
        }
        if (value != null) {
            // Docked is flush by definition, so the stored number would be a lie here. Showing the
            // dash rather than a frozen "12 dp" is what tells the user the control is inert, not
            // stuck. The value survives untouched and comes back with Floating.
            value.setText(available
                ? getString(R.string.termux_dock_tuning_value_dp, insetDp)
                : getString(R.string.termux_surface_tuning_value_not_applicable));
            value.setAlpha(available ? 1f : SURFACE_TUNING_DISABLED_ALPHA);
        }
    }

    /** Re-reads the dock style for every control whose availability depends on it. */
    private void syncSurfaceTuningStyleDependentControls() {
        syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_DOCK);
        syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_KEYBOARD);
        syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_STATUS);
        // Both of these are Docked-only readings of a shared knob, so switching the shape has to
        // move them with it rather than waiting for the editor to be reopened.
        syncTerminalRadiusRow();
        syncTerminalGapLabel();
    }

    private void bindSurfaceTuningInsetSeekBar(int target) {
        SeekBar slider = mHost.findView(surfaceTuningInsetSliderId(target));
        if (slider == null)
            return;
        slider.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                TextView value = mHost.findView(surfaceTuningInsetValueId(target));
                if (value != null)
                    value.setText(getString(R.string.termux_dock_tuning_value_dp, progress));
                if (fromUser && prefs() != null && mHost.isRoundedDockStyle()
                    && progress != surfaceTuningInsetDp(target)) {
                    detachSurfaceRowForEdit(SurfaceEditorRows.forSlider(bar.getId()));
                    setSurfaceTuningInsetDp(target, progress);
                }
            }
        });
        syncSurfaceTuningInsetSlider(target);
    }

    /**
     * Horizontal drag anywhere over a surface walks its symmetric screen-edge inset: right widens
     * both edges, left narrows them. Previews land in preferences immediately like the card's own
     * sliders, so Done keeps them and Close restores the values captured on entry.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void bindSurfaceTuningInsetGesture(int groupId, int target) {
        View group = mHost.findView(groupId);
        if (group == null)
            return;
        group.setOnTouchListener((view, event) -> {
            // Docked surfaces are flush with the screen edges, so there is no gap to drag. Falling
            // through rather than consuming keeps the surface underneath usable.
            if (!mDockTuningMode || prefs() == null || !mHost.isRoundedDockStyle())
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mSurfaceTuningInsetDragStartX = event.getRawX();
                    mSurfaceTuningInsetDragStartDp = surfaceTuningInsetDp(target);
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float travelDp = pxToDp(event.getRawX() - mSurfaceTuningInsetDragStartX);
                    int insetDp = TermuxAppSharedPreferences.clampSurfaceHorizontalInset(
                        Math.round(mSurfaceTuningInsetDragStartDp
                            + (travelDp * SURFACE_TUNING_INSET_DRAG_GAIN)));
                    if (insetDp != surfaceTuningInsetDp(target))
                        setSurfaceTuningInsetDp(target, insetDp);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    /** Vertical drag on the dock's top-border pill walks the preset height range continuously. */
    @SuppressLint("ClickableViewAccessibility")
    private void bindSurfaceTuningDockHeightGesture() {
        View handle = mHost.findView(R.id.surface_tuning_dock_height_handle);
        if (handle == null)
            return;
        handle.setOnTouchListener((view, event) -> {
            if (!mDockTuningMode || prefs() == null)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mSurfaceTuningDockHeightDragStartY = event.getRawY();
                    mSurfaceTuningDockHeightDragStartScale =
                        prefs().getAppLauncherBarHeightScale();
                    setSurfaceTuningCardPeek(true);
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float minScale = DockLayoutPolicy.minSizePreset();
                    float maxScale = DockLayoutPolicy.maxSizePreset();
                    float travelDp = pxToDp(mSurfaceTuningDockHeightDragStartY - event.getRawY());
                    float scale = mSurfaceTuningDockHeightDragStartScale
                        + ((travelDp / SURFACE_TUNING_DOCK_HEIGHT_DRAG_SPAN_DP)
                            * (maxScale - minScale));
                    scale = Math.max(minScale, Math.min(maxScale, scale));
                    if (Float.compare(scale, prefs().getAppLauncherBarHeightScale()) != 0) {
                        prefs().setAppLauncherBarHeightScale(scale);
                        syncSurfaceTuningDockHeightSlider();
                        applyDockTuningStructuralPreview();
                    }
                    setSurfaceTuningPeekReadout(
                        getString(R.string.termux_surface_tuning_peek_dock_size),
                        dockSizePresetLabel(DockLayoutPolicy.nearestSizePresetIndex(scale)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setSurfaceTuningCardPeek(false);
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    /** Vertical drag on the keyboard's top-border pill, on the same 1:1 mapping as the old handle. */
    @SuppressLint("ClickableViewAccessibility")
    private void bindSurfaceTuningKeyboardHeightGesture() {
        View handle = mHost.findView(R.id.surface_tuning_keyboard_height_handle);
        if (handle == null)
            return;
        handle.setOnTouchListener((view, event) -> {
            if (!mDockTuningMode || prefs() == null || keyboard() == null)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mInAppKeyboardHeightDragStartY = event.getRawY();
                    mInAppKeyboardHeightDragStartScale = keyboard().getHeightScale();
                    int renderedHeight = mHost.attachedInAppKeyboardView() == null
                        ? 0 : mHost.attachedInAppKeyboardView().getMeasuredHeight();
                    mInAppKeyboardUnscaledDragHeight = Math.max(1f,
                        renderedHeight / Math.max(0.01f, mInAppKeyboardHeightDragStartScale));
                    setSurfaceTuningCardPeek(true);
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float scale = TermuxInAppKeyboard.calculateHeightScaleForDrag(
                        mInAppKeyboardHeightDragStartScale,
                        event.getRawY() - mInAppKeyboardHeightDragStartY,
                        mInAppKeyboardUnscaledDragHeight);
                    keyboard().previewSurfaceEditorHeightScale(scale);
                    syncSurfaceTuningKeyboardHeightSlider(keyboard().getHeightScale());
                    setSurfaceTuningPeekReadout(
                        getString(R.string.termux_surface_tuning_peek_keyboard_height),
                        getString(R.string.termux_dock_tuning_value_percent, keyboardEditorProgress(
                            keyboard().getHeightScale(),
                            TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    prefs().setInAppKeyboardHeightScale(keyboard().getHeightScale());
                    updateSurfaceEditorDirtyBadge();
                    setSurfaceTuningCardPeek(false);
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void syncSurfaceTuningDockHeightSlider() {
        if (prefs() == null)
            return;
        int index = DockLayoutPolicy.nearestSizePresetIndex(prefs().getAppLauncherBarHeightScale());
        SeekBar slider = mHost.findView(R.id.dock_tuning_size_slider);
        TextView value = mHost.findView(R.id.dock_tuning_size_value);
        if (slider != null && slider.getProgress() != index)
            slider.setProgress(index);
        if (value != null)
            value.setText(dockSizePresetLabel(index));
    }

    private void syncSurfaceTuningKeyboardHeightSlider(float heightScale) {
        int progress = keyboardEditorProgress(heightScale,
            TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE);
        SeekBar slider = mHost.findView(R.id.surface_tuning_keyboard_height_slider);
        TextView value = mHost.findView(R.id.surface_tuning_keyboard_height_value);
        if (slider != null && slider.getProgress() != progress)
            slider.setProgress(progress);
        if (value != null)
            value.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
    }

    private void bindSurfaceTuningGestures() {
        bindSurfaceTuningInsetSeekBar(SURFACE_TUNING_TARGET_DOCK);
        bindSurfaceTuningInsetSeekBar(SURFACE_TUNING_TARGET_KEYBOARD);
        bindSurfaceTuningInsetSeekBar(SURFACE_TUNING_TARGET_STATUS);
        bindSurfaceTuningInsetGesture(R.id.surface_tuning_dock_gesture_group,
            SURFACE_TUNING_TARGET_DOCK);
        bindSurfaceTuningInsetGesture(R.id.surface_tuning_keyboard_gesture_group,
            SURFACE_TUNING_TARGET_KEYBOARD);
        bindSurfaceTuningInsetGesture(R.id.surface_tuning_status_gesture_group,
            SURFACE_TUNING_TARGET_STATUS);
        bindSurfaceTuningDockHeightGesture();
        bindSurfaceTuningKeyboardHeightGesture();
    }

    private void setSurfaceTuningGestureOverlayVisible(boolean visible) {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null)
            return;
        overlay.animate().cancel();
        if (visible) {
            positionSurfaceTuningGestureTargets();
            overlay.setAlpha(0f);
            overlay.setVisibility(View.VISIBLE);
            overlay.animate().alpha(1f).setDuration(SURFACE_TUNING_FADE_DURATION_MS)
                .setInterpolator(surfaceTuningFadeInterpolator()).start();
            return;
        }
        overlay.animate().alpha(0f).setDuration(SURFACE_TUNING_FADE_DURATION_MS)
            .setInterpolator(surfaceTuningFadeInterpolator())
            .withEndAction(() -> {
                overlay.setVisibility(View.GONE);
                overlay.setAlpha(1f);
            }).start();
    }

    private android.view.animation.Interpolator surfaceTuningFadeInterpolator() {
        return com.termux.app.terminal.Motion.settle();
    }

    private void positionSurfaceTuningGestureTargets() {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null || !mDockTuningMode || overlay.getWidth() <= 0)
            return;
        View statusSurface = mHost.findView(R.id.terminal_window_bar_host);
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_status_gesture_group, overlay,
            statusSurface);
        resizeStatusTuningPills(statusSurface);
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_dock_gesture_group, overlay,
            mHost.findView(R.id.accessory_surface_host));
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_keyboard_gesture_group, overlay,
            mHost.isInAppKeyboardShown() ? mHost.findView(R.id.inapp_keyboard_view_host) : null);
        // Docked surfaces are flush with the screen edges: the margin drag is inert there (see
        // bindSurfaceTuningInsetGesture), so the side pills advertising it must not render either.
        boolean sideDrag = mHost.isRoundedDockStyle();
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_status_pill_left, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_status_pill_right, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_dock_pill_left, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_dock_pill_right, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_keyboard_pill_left, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_keyboard_pill_right, sideDrag);
    }

    private void setSurfaceTuningSidePillVisible(int pillId, boolean visible) {
        View pill = mHost.findView(pillId);
        if (pill != null)
            pill.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Tracks one surface's measured rect with its capture group, reaching
     * {@link #SURFACE_TUNING_HANDLE_OVERHANG_DP} further up so the pill centred on the top border
     * still falls inside the group's hit area.
     */
    private void positionSurfaceTuningGestureGroup(int groupId, @NonNull View overlay,
                                                   @Nullable View surface) {
        View group = mHost.findView(groupId);
        if (group == null)
            return;
        if (surface == null || surface.getVisibility() != View.VISIBLE
            || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
            group.setVisibility(View.GONE);
            return;
        }
        int[] overlayLocation = new int[2];
        int[] surfaceLocation = new int[2];
        overlay.getLocationInWindow(overlayLocation);
        surface.getLocationInWindow(surfaceLocation);
        int surfaceTop = surfaceLocation[1] - overlayLocation[1];
        int top = Math.max(0, surfaceTop - Math.round(dpToPx(SURFACE_TUNING_HANDLE_OVERHANG_DP)));
        int left = Math.max(0, surfaceLocation[0] - overlayLocation[0]);
        // Pin both margins against a match_parent width so the group never depends on how the
        // overlay resolves an absent horizontal gravity.
        int right = Math.max(0, overlay.getWidth() - (left + surface.getWidth()));
        int height = Math.max(1, (surfaceTop + surface.getHeight()) - top);
        ViewGroup.LayoutParams layoutParams = group.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) layoutParams;
            if (params.leftMargin != left || params.rightMargin != right
                || params.topMargin != top || params.height != height) {
                params.leftMargin = left;
                params.rightMargin = right;
                params.topMargin = top;
                params.height = height;
                group.setLayoutParams(params);
            }
        }
        group.setVisibility(View.VISIBLE);
    }

    /**
     * The status pane can collapse to a ~32dp compact bar, where the fixed 28dp side pills read
     * as oversized bars instead of edge handles. Scale them to a bit over half the pane height,
     * capped at the shared 28dp; the capsule drawable keeps proper arc ends at any height.
     */
    private void resizeStatusTuningPills(@Nullable View statusSurface) {
        if (statusSurface == null || statusSurface.getHeight() <= 0)
            return;
        int target = Math.round(Math.min(dpToPx(28),
            Math.max(dpToPx(12), statusSurface.getHeight() * 0.55f)));
        int[] pillIds = {R.id.surface_tuning_status_pill_left, R.id.surface_tuning_status_pill_right};
        for (int pillId : pillIds) {
            View pill = mHost.findView(pillId);
            if (pill == null)
                continue;
            ViewGroup.LayoutParams params = pill.getLayoutParams();
            if (params != null && params.height != target) {
                params.height = target;
                pill.setLayoutParams(params);
            }
        }
    }


    private void registerDockTuningLayoutListener(@NonNull View controls) {
        if (mDockTuningLayoutListener != null)
            return;
        mDockTuningLayoutListener = () -> {
            adjustDockTuningCardHeight();
            positionSurfaceTuningGestureTargets();
        };
        controls.getViewTreeObserver().addOnGlobalLayoutListener(mDockTuningLayoutListener);
    }

    private void unregisterDockTuningLayoutListener() {
        if (mDockTuningLayoutListener == null)
            return;
        View controls = mHost.findView(R.id.dock_tuning_controls);
        if (controls != null)
            controls.getViewTreeObserver().removeOnGlobalLayoutListener(mDockTuningLayoutListener);
        mDockTuningLayoutListener = null;
    }

    /**
     * Sizes the editor card against the room it actually has.
     *
     * <p>The card is pinned above the accessory stack, and how much room that leaves changes while
     * the editor is open: hiding the in-app keyboard frees hundreds of pixels, and a card that kept
     * its old height sat in the bottom corner with a band of empty terminal above it. So the height
     * is recomputed on every layout — {@link SurfaceEditorCardMetrics} decides how much of the
     * space to spend — and the scroll region takes the height its current section needs, up to that
     * ceiling. Header, tabs and the action row stay on screen at every size.
     */
    private void adjustDockTuningCardHeight() {
        if (!mDockTuningMode)
            return;
        View controls = mHost.findView(R.id.dock_tuning_controls);
        ScrollView scroll = mHost.findView(R.id.dock_tuning_scroll);
        View headerRow = mHost.findView(R.id.dock_tuning_header_row);
        View actions = mHost.findView(R.id.surface_tuning_actions);
        View stack = mHost.findView(R.id.accessory_stack_container);
        if (controls == null || scroll == null || headerRow == null
            || actions == null || stack == null)
            return;
        if (controls.getVisibility() != View.VISIBLE)
            return;
        View scrollChild = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        if (scrollChild == null)
            return;
        // Before the first measure the chrome reports zero height, and a ceiling computed from that
        // is tall enough to push the card's own header up behind the launcher's status bar. The next
        // layout pass calls back with real numbers.
        if (headerRow.getHeight() <= 0 || actions.getHeight() <= 0)
            return;

        // The card may grow until just under the launcher's own status bar — a small seam, not a
        // band of empty terminal. Both bounds are in the card parent's coordinate space: the status
        // inset and the window bar are measured against the window, and the parent starts below the
        // inset, so each is converted rather than compared raw. Skipping the conversion cost the
        // card a status bar's worth of height it was entitled to.
        int parentTopInWindow = 0;
        if (controls.getParent() instanceof View) {
            int[] location = new int[2];
            ((View) controls.getParent()).getLocationInWindow(location);
            parentTopInWindow = location[1];
        }
        int statusBottom = Math.max(0, mHost.statusBarInsetTop() - parentTopInWindow);
        View windowBar = mHost.findView(R.id.terminal_window_bar_host);
        if (windowBar != null && windowBar.getVisibility() == View.VISIBLE
            && windowBar.getHeight() > 0) {
            int[] location = new int[2];
            windowBar.getLocationInWindow(location);
            statusBottom = Math.max(statusBottom,
                location[1] + windowBar.getHeight() - parentTopInWindow);
        }
        int topLimit = statusBottom + Math.round(dpToPx(6));
        int cardMarginBottom = Math.round(dpToPx(10));
        int availableCard = (stack.getTop() - cardMarginBottom) - topLimit;
        // Chrome outside the scroll region: card top/bottom padding (10 + 12), the action row's top
        // margin (6), plus the measured header and action row.
        int chrome = Math.round(dpToPx(10 + 12 + 6)) + headerRow.getHeight()
            + actions.getHeight();
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float pxPerDp = dpToPx(1);
        int target = SurfaceEditorCardMetrics.scrollHeightPx(availableCard, chrome,
            scrollChild.getMeasuredHeight(), screenHeight, pxPerDp);
        setSurfaceEditorScrollHeight(scroll, target);
    }

    /**
     * Moves the slider region to {@code target}, animating anything but the first pass.
     *
     * <p>The card is anchored to its bottom edge, so a resize moves its header and tabs. Snapping
     * them mid-gesture — the section tab that was just tapped jumping out from under the finger —
     * is what a fixed-height card was avoiding; a short slide reads as the panel fitting itself to
     * the section instead, and costs none of the empty glass a fixed height left under the short
     * sections.
     */
    private void setSurfaceEditorScrollHeight(@NonNull ScrollView scroll, int target) {
        boolean animating = mSurfaceEditorScrollAnimator != null
            && mSurfaceEditorScrollAnimator.isRunning();
        // The animation lays the card out on every frame, and each of those passes lands back here.
        // Restarting the slide from wherever it had got to stretched a 140ms move over half a
        // second, so a run already heading for this target is left alone.
        if (target == mSurfaceEditorScrollTarget
            && (animating || scroll.getLayoutParams().height == target))
            return;
        mSurfaceEditorScrollTarget = target;
        if (mSurfaceEditorScrollAnimator != null) {
            mSurfaceEditorScrollAnimator.cancel();
            mSurfaceEditorScrollAnimator = null;
        }
        ViewGroup.LayoutParams lp = scroll.getLayoutParams();
        int from = lp.height;
        // No animation into the first measured height, or from one: there is nothing on screen yet
        // to slide, and the pass that discovers the height must not leave the card half-sized.
        if (from <= 0 || Math.abs(target - from) < Math.round(dpToPx(4))) {
            lp.height = target;
            scroll.setLayoutParams(lp);
            return;
        }
        android.animation.ValueAnimator animator =
            android.animation.ValueAnimator.ofInt(from, target);
        animator.setDuration(SURFACE_TUNING_RESIZE_DURATION_MS);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(update -> {
            if (!mDockTuningMode)
                return;
            ViewGroup.LayoutParams params = scroll.getLayoutParams();
            params.height = (int) update.getAnimatedValue();
            scroll.setLayoutParams(params);
        });
        mSurfaceEditorScrollAnimator = animator;
        animator.start();
    }


    // Live-preview scopes for the surface editor. Sliders fire onProgressChanged far faster than
    // a full re-apply fits in a frame, so requests carry only the scopes their control touches and
    // are coalesced to a single apply per animation frame. GLASS (the accessory re-render in
    // applyDockTuningPreview) runs on every apply; BLUR additionally throws away the shared
    // pre-blurred wallpaper bitmap, which is the single most expensive thing a slider can cause —
    // only radius controls may request it.
    private static final int TUNING_PREVIEW_GLASS = 1;
    private static final int TUNING_PREVIEW_BLUR = 1 << 1;
    private static final int TUNING_PREVIEW_GEOMETRY = 1 << 2;
    private static final int TUNING_PREVIEW_SURFACES = 1 << 3;
    private static final int TUNING_PREVIEW_KEYBOARD = 1 << 4;
    private static final int TUNING_PREVIEW_ALL = TUNING_PREVIEW_GLASS | TUNING_PREVIEW_BLUR
        | TUNING_PREVIEW_GEOMETRY | TUNING_PREVIEW_SURFACES | TUNING_PREVIEW_KEYBOARD;

    private int mPendingTuningPreviewScopes;
    private boolean mTuningPreviewScheduled;
    private final Runnable mTuningPreviewRunnable = this::runPendingTuningPreview;

    private void requestDockTuningPreview(int scopes) {
        mPendingTuningPreviewScopes |= scopes | TUNING_PREVIEW_GLASS;
        if (mTuningPreviewScheduled)
            return;
        View root = mHost.findView(R.id.activity_termux_root_view);
        if (root == null) {
            runPendingTuningPreview();
            return;
        }
        mTuningPreviewScheduled = true;
        root.postOnAnimation(mTuningPreviewRunnable);
    }

    private void runPendingTuningPreview() {
        mTuningPreviewScheduled = false;
        int scopes = mPendingTuningPreviewScopes;
        mPendingTuningPreviewScopes = 0;
        if (scopes == 0 || prefs() == null)
            return;
        if ((scopes & TUNING_PREVIEW_GEOMETRY) != 0)
            mHost.applyGeometryPreview();
        if ((scopes & TUNING_PREVIEW_SURFACES) != 0) {
            mHost.applyTerminalSurfaceAppearance();
            mHost.refreshTerminalWindowBar();
            mHost.applySessionsSurfaceBackground();
        }
        if ((scopes & TUNING_PREVIEW_KEYBOARD) != 0 && keyboard() != null)
            keyboard().onPreferencesReloaded();
        mHost.applyGlassPreview((scopes & TUNING_PREVIEW_BLUR) != 0);
        if (mDockTuningMode) syncSurfaceInheritanceUi();
        updateSurfaceEditorDirtyBadge();
    }

    /** Broader live re-apply for controls that change dock geometry, terminal, or sessions surfaces. */
    private void applyDockTuningStructuralPreview() {
        requestDockTuningPreview(TUNING_PREVIEW_ALL);
    }

    @NonNull
    private String dockSizePresetLabel(int index) {
        switch (Math.max(0, Math.min(DockLayoutPolicy.sizePresetCount() - 1, index))) {
            case 0:
                return getString(R.string.termux_dock_preset_smallest);
            case 1:
                return getString(R.string.termux_dock_preset_small);
            case 2:
                return getString(R.string.termux_dock_preset_default);
            default:
                return getString(R.string.termux_dock_preset_large);
        }
    }

    private void exitDockTuningMode() {
        // Cleared before the flag drops: both helpers no-op once mDockTuningMode is false, and a
        // drag interrupted by Done would otherwise leave the card stuck at peek alpha.
        View peeked = mHost.findView(R.id.dock_tuning_controls);
        if (peeked != null) {
            peeked.animate().cancel();
            peeked.setAlpha(1f);
        }
        hideSurfaceTuningPeekReadout();
        mSurfaceEditorEntrySignature = null;
        mSurfaceEditorRevert = null;
        View dirtyBadge = mHost.findView(R.id.surface_tuning_dirty_badge);
        if (dirtyBadge != null) dirtyBadge.setVisibility(View.GONE);
        mDockTuningMode = false;
        setSurfaceTuningGestureOverlayVisible(false);
        unregisterDockTuningLayoutListener();
        if (mSurfaceEditorScrollAnimator != null) {
            mSurfaceEditorScrollAnimator.cancel();
            mSurfaceEditorScrollAnimator = null;
        }
        mSurfaceEditorScrollTarget = 0;
        ScrollView scroll = mHost.findView(R.id.dock_tuning_scroll);
        if (scroll != null) {
            ViewGroup.LayoutParams lp = scroll.getLayoutParams();
            if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                scroll.setLayoutParams(lp);
            }
        }
        View controls = mHost.findView(R.id.dock_tuning_controls);
        if (controls != null)
            controls.setVisibility(View.GONE);
        restoreExpandedStatusAfterSurfaceEditor();
        mDockTuningRestoreExpandedStatus = false;
    }

    public void restoreExpandedStatusAfterSurfaceEditor() {
        if (prefs() == null)
            return;
        // Only the editor's own temporary change is undone here. onStop() also calls this, and
        // without the guard an expanded pane was collapsed — and the collapse persisted — every
        // time the user left the app, so the clock never came back.
        if (!mDockTuningRestoreExpandedStatus)
            return;
        if (prefs().isTopPaneClockCollapsed())
            mHost.setTopStatusBarCollapsed(false, false);
    }

    /**
     * Base for every editor slider. A slider whose surface sits underneath the card - or whose drag
     * moves the card, because the card is anchored above the accessory stack - passes a label and
     * "peeks": the card fades while the thumb is down so the thing being edited stays visible, and
     * the value it is changing is echoed over the surface instead.
     *
     * <p>Subclasses that override the tracking callbacks must call through, or the card never fades
     * back in.
     */
    private abstract class SimpleSeekBarChangeListener
        implements SeekBar.OnSeekBarChangeListener {
        private final int peekLabelRes;

        SimpleSeekBarChangeListener() {
            this(0);
        }

        SimpleSeekBarChangeListener(int peekLabelRes) {
            this.peekLabelRes = peekLabelRes;
        }

        /** Echoes the value over the surface while the card is faded. No-op for non-peek sliders. */
        final void peekReadout(CharSequence value) {
            if (peekLabelRes != 0)
                setSurfaceTuningPeekReadout(getString(peekLabelRes), value);
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) {
            if (peekLabelRes != 0) setSurfaceTuningCardPeek(true);
        }

        @Override public void onStopTrackingTouch(SeekBar seekBar) {
            if (peekLabelRes != 0) setSurfaceTuningCardPeek(false);
        }
    }
}
