package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import com.termux.R;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.notice.AppNotice;
import com.termux.app.notice.AppNoticeItem;
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.app.surfaces.SurfaceEditorPillMetrics.Mode;
import com.termux.app.surfaces.SurfaceEditorProperties.Control;
import com.termux.app.surfaces.SurfaceEditorProperties.Kind;
import com.termux.app.surfaces.SurfaceEditorProperties.Unit;
import com.termux.app.terminal.Motion;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The surface editor: an attached pill over the live home screen.
 *
 * <p>You pick a surface by touching it — the dock, the keyboard, the launcher's status bar or the
 * terminal canvas — and a small pill parks next to it carrying one property at a time. There is no
 * tab row, no shared-versus-per-surface split and no scroll region: the chip row names what the
 * selected surface owns, the single row under it is whichever chip is open, and the footnote says in
 * place whether that number is the surface's own or Base's. The pill never covers the surface it
 * edits; the accent ring, not the pill's position, is what identifies the target.
 *
 * <p>Three things sit behind it, all transient and all unfolding inside the same card — the editor
 * is one surface, never a stack of windows. Look folds blur, opacity and grain into a material
 * family plus one intensity, with the raw triple still reachable under Fine. Each surface's ⋯
 * panel holds the rest of what it owns — the dock's apps per page, the keyboard's own key metrics,
 * the terminal's frame. The Looks panel holds complete presets, the shared Base layer every
 * surface inherits, and Reset.
 *
 * <p>The editor writes through to preferences live, so the preview is the real thing; only Done
 * commits, and the ✕ and back both route through {@link #requestClose()} against the snapshot taken
 * on entry. The activity keeps the render pipeline; everything the editor needs from it crosses
 * {@link Host}, which is the seam that keeps this class free of the activity's fifteen thousand
 * lines.
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
        boolean isFloatingDock();
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
        /**
         * Dock geometry changed: bar height, toolbar height, immediate chrome apply. With
         * {@code commit} the terminal is also resized to the new geometry — a shell reflow worth
         * paying once per gesture, on release, not per tick.
         */
        void applyGeometryPreview(boolean commit);
        /** The coalesced glass re-render; {@code blurChanged} also drops the blur cache. */
        void applyGlassPreview(boolean blurChanged);
        void openKeyboardColors();
        /**
         * A small center-cropped copy of the blurred wallpaper for the preset mocks, or null when
         * no frame is available (fallback: a neutral gradient). A copy, so the blur cache recycling
         * a frame never pulls the bitmap out from under a card.
         */
        @Nullable Bitmap wallpaperPreviewThumb(int widthPx, int heightPx);
        /**
         * The live glass recipe at caller-supplied opacity/grain — what makes a preset card show
         * the material the preset would actually render, not a sketch of it.
         */
        @NonNull Drawable presetGlassSurface(
            float barAlpha, int grainPercent, float cornerRadiusPx, boolean withRim);
    }

    @NonNull
    private final Host mHost;

    public SurfaceEditorController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isActive() {
        return mSurfaceEditorOpen;
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

    private Resources getResources() {
        return mHost.context().getResources();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private int dp(float value) {
        return Math.round(dpToPx(value));
    }

    private float pxToDp(float px) {
        return px / getResources().getDisplayMetrics().density;
    }

    /**
     * The activity resumed with the editor open: the pane the editor collapsed out of its way on
     * entry must stay collapsed until the editor closes and gives it back.
     */
    public void collapseStatusPaneIfLeftExpanded() {
        if (!mSurfaceEditorOpen || !mSurfaceEditorRestoreExpandedStatus)
            return;
        if (prefs() != null && !prefs().isTopPaneClockCollapsed())
            mHost.setTopStatusBarCollapsed(true, false);
    }

    // ------------------------------------------------------------------------------ session state

    private boolean mSurfaceEditorOpen;
    private boolean mSurfaceEditorRestoreExpandedStatus;
    /** Editor state as it was on entry; anything different from this is unsaved. */
    @Nullable private String mSurfaceEditorEntrySignature;
    /** Restores that entry state. Held so the close gate can offer Discard. */
    @Nullable private Runnable mSurfaceEditorRevert;
    private ViewTreeObserver.OnGlobalLayoutListener mSurfaceEditorLayoutListener;
    /** Last anchor geometry the layout listener acted on; layouts that move nothing are skipped. */
    private long mSurfaceEditorAnchorSignature = Long.MIN_VALUE;
    private final int[] mTmpAnchorLocation = new int[2];

    /** The surface the pill is pointing at. Kept across editor sessions. */
    @NonNull private SurfaceSlot mSelectedSlot = SurfaceSlot.DOCK;
    /**
     * Which chip each surface was left on. Remembered for the session, so coming back to the dock
     * comes back to the property you were tuning rather than to the first one.
     */
    private final Map<SurfaceSlot, String> mOpenControlId = new EnumMap<>(SurfaceSlot.class);
    @NonNull private Mode mLayoutMode = Mode.FULL;
    /** True while the chip row is being restated in code, so a restate is not read as a pick. */
    private boolean mRestatingChips;

    // The editor's own keyboard-height drag state; adjust mode keeps a separate copy in the
    // activity, and the two gestures can never run at once.
    private float mInAppKeyboardHeightDragStartY;
    private float mInAppKeyboardHeightDragStartScale;
    private float mInAppKeyboardUnscaledDragHeight;
    private float mSurfaceTuningInsetDragStartX;
    private float mSurfaceTuningInsetDragStartY;
    private int mSurfaceTuningInsetDragStartDp;
    private boolean mSurfaceTuningDragMoved;
    private float mSurfaceTuningDockHeightDragStartY;
    private float mSurfaceTuningDockHeightDragStartScale;

    /** Dimming for an editor control the current state makes inapplicable. */
    private static final float SURFACE_TUNING_DISABLED_ALPHA = 0.38f;
    /** Pill opacity while a peeking control is being dragged. */
    private static final float SURFACE_TUNING_PEEK_ALPHA = 0.28f;
    private static final long SURFACE_TUNING_PEEK_OUT_MS = 90;
    private static final long SURFACE_TUNING_PEEK_IN_MS = 170;
    private static final long SURFACE_TUNING_FADE_DURATION_MS = 200;
    /** How long the pill takes to travel to a newly selected surface's park position. */
    private static final long SURFACE_EDITOR_PARK_DURATION_MS = 160;
    private static final long SURFACE_EDITOR_RING_DURATION_MS = 150;
    /** The constant gap between the pill and the surface it is editing. */
    private static final float SURFACE_EDITOR_STANDOFF_DP = 14f;
    private static final float SURFACE_TUNING_INSET_DRAG_GAIN = 0.5f;
    /** Finger travel that walks the dock across its whole preset height range. */
    private static final float SURFACE_TUNING_DOCK_HEIGHT_DRAG_SPAN_DP = 40f;
    /** How far the capture groups reach above their surface so the border handle is inside. */
    private static final int SURFACE_TUNING_HANDLE_OVERHANG_DP = 14;

    // -------------------------------------------------------------------------------- the pill

    /**
     * The pill's controls, found once per process. The pill is one slider pointed at whichever cell
     * the open chip names, so this is the whole view surface of the editor.
     */
    private static final class Pill {
        final View host;
        final LinearLayout root;
        final View header;
        final TextView title;
        final TextView dirty;
        final TextView looks;
        final TextView done;
        final TextView close;
        final ChipGroup chips;
        final View look;
        final MaterialButtonToggleGroup material;
        final TextView fine;
        final MaterialButtonToggleGroup shape;
        final View row;
        final TextView property;
        final SeekBar slider;
        final MaterialSwitch toggle;
        final TextView value;
        final TextView reset;
        final TextView footnote;
        final ViewGroup panel;

        /** Last text pushed into each read-out; a restate that changes nothing skips its layout. */
        String shownTitle;
        String shownValue;
        String shownFootnote;

        Pill(View host, LinearLayout root) {
            this.host = host;
            this.root = root;
            header = root.findViewById(R.id.surface_editor_pill_header);
            title = root.findViewById(R.id.surface_editor_pill_title);
            dirty = root.findViewById(R.id.surface_editor_pill_dirty);
            looks = root.findViewById(R.id.surface_editor_pill_looks);
            done = root.findViewById(R.id.surface_editor_pill_done);
            close = root.findViewById(R.id.surface_editor_pill_close);
            chips = root.findViewById(R.id.surface_editor_pill_chips);
            look = root.findViewById(R.id.surface_editor_pill_look);
            material = root.findViewById(R.id.surface_editor_pill_material);
            fine = root.findViewById(R.id.surface_editor_pill_look_fine);
            shape = root.findViewById(R.id.surface_editor_pill_shape);
            row = root.findViewById(R.id.surface_editor_pill_row);
            property = root.findViewById(R.id.surface_editor_pill_property);
            slider = root.findViewById(R.id.surface_editor_pill_slider);
            toggle = root.findViewById(R.id.surface_editor_pill_switch);
            value = root.findViewById(R.id.surface_editor_pill_value);
            reset = root.findViewById(R.id.surface_editor_pill_reset);
            footnote = root.findViewById(R.id.surface_editor_pill_footnote);
            panel = root.findViewById(R.id.surface_editor_pill_panel);
        }

        boolean complete() {
            return title != null && chips != null && slider != null && value != null
                && reset != null && footnote != null && done != null && row != null
                && look != null && material != null && toggle != null && property != null
                && panel != null;
        }
    }

    @Nullable private Pill mPill;

    /** Inflates the pill into its host, once. */
    @Nullable
    private Pill pill() {
        if (mPill != null)
            return mPill;
        ViewGroup host = mHost.findView(R.id.surface_editor_pill_host);
        if (host == null)
            return null;
        LinearLayout root = host.findViewById(R.id.surface_editor_pill);
        if (root == null) {
            LayoutInflater.from(mHost.context())
                .inflate(R.layout.surface_editor_pill, host, true);
            root = host.findViewById(R.id.surface_editor_pill);
        }
        if (root == null)
            return null;
        Pill pill = new Pill(host, root);
        if (!pill.complete())
            return null;
        mPill = pill;
        bindPill(pill);
        return pill;
    }

    // ------------------------------------------------------------------------------------ entry

    public void enter() {
        // No section asked for: the pill remembers the surface it was left on.
        enter(null);
    }

    public void enter(@Nullable String initialSection) {
        if (mHost.isFullStatusBarEngaged()) return;
        if (prefs() == null)
            return;
        Pill pill = pill();
        if (pill == null) {
            mSurfaceEditorOpen = false;
            return;
        }
        // Re-entry with the editor already open (a second tuning intent, say) must not re-baseline:
        // the snapshot below is what "unsaved" is measured against, and recapturing it mid-session
        // would quietly adopt the user's in-progress edits as the thing Discard returns to.
        final boolean freshEditorSession = !mSurfaceEditorOpen;
        if (freshEditorSession) {
            mSurfaceEditorRestoreExpandedStatus = !prefs().isTopPaneClockCollapsed();
            if (mSurfaceEditorRestoreExpandedStatus) mHost.setTopStatusBarCollapsed(true, false);
        }
        mSurfaceEditorOpen = true;
        pill.host.setVisibility(View.VISIBLE);
        pill.root.setAlpha(1f);

        if (freshEditorSession || mSurfaceEditorEntrySignature == null) {
            mSurfaceEditorRevert = captureEntryState();
            mSurfaceEditorEntrySignature = surfaceEditorStateSignature();
        }

        SurfaceSlot deepLinkSlot = slotForSectionKey(initialSection);
        selectSurface(deepLinkSlot != null ? deepLinkSlot : mSelectedSlot, false);
        bindSurfaceTuningGestures();
        syncPill();
        pill.host.bringToFront();
        setSurfaceTuningGestureOverlayVisible(true);
        registerSurfaceEditorLayoutListener(pill.host);
        pill.host.post(() -> {
            applyLayoutMode(true);
            parkPill(false);
        });
    }

    /**
     * The surface a settings deep link targets, or null for a plain open. The section names are
     * the ones the deep links have always sent; "sessions" and "other" are what older callers and
     * stored intents said before the sessions demotion and the terminal rename.
     */
    @Nullable
    private static SurfaceSlot slotForSectionKey(@Nullable String section) {
        if ("sessions".equals(section))
            return SurfaceSlot.STATUS;
        if ("terminal".equals(section) || "other".equals(section))
            return SurfaceSlot.CANVAS;
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (slot.key.equals(section))
                return slot;
        }
        return null;
    }

    /**
     * Everything the editor can move, captured so Discard can put it back exactly.
     *
     * <p>Raw values and the link shape rather than resolved numbers: a surface that was detached at
     * the same number as Base must come back detached, not quietly folded in. Base itself is
     * restored last, once the links are back in their entry shape — the legacy setters above write
     * through whichever link is attached, so a property every surface had detached would otherwise
     * leave the shared layer unrestored.
     */
    @NonNull
    private Runnable captureEntryState() {
        final TermuxAppSharedPreferences prefs = prefs();
        final String links = surfaceEditorLinkSignature();
        final int initialBlur = prefs.getExtraKeysBlurRadius();
        final int initialOpacity = prefs.getAppBarOpacity();
        final int initialGrain = prefs.getDockGlassGrain();
        final int initialDockRadius = prefs.getAppLauncherDockCornerRadius();
        final int initialDockInset = prefs.getDockHorizontalInset();
        final float initialBarHeight = prefs.getAppLauncherBarHeightScale();
        final int initialButtonCount = prefs.getAppLauncherButtonCount();
        final String initialStyle = prefs.getAppLauncherDockStyle();
        final float initialKeyboardHeight = prefs.getInAppKeyboardHeightScale();
        final float initialKeyboardSpacing = prefs.getInAppKeyboardKeyMarginScale();
        final float initialKeyboardRadius = prefs.getInAppKeyboardKeyCornerRadiusDp();
        final int initialKeyboardKeyOpacity = prefs.getInAppKeyboardKeyOpacity();
        final int initialKeyboardBgOpacity = prefs.getInAppKeyboardBackgroundOpacity();
        final int initialKeyboardInset = prefs.getInAppKeyboardHorizontalInset();
        final String initialKeyboardColorScheme = prefs.getInAppKeyboardColorScheme();
        final String initialKeyboardTheme = prefs.getInAppKeyboardTheme();
        final int initialStatusBlur = prefs.getStatusBarBlurRadius();
        final int initialStatusOpacity = prefs.getStatusBarOpacity();
        final int initialStatusGrain = prefs.getStatusBarGrain();
        final int initialStatusRadius = prefs.getStatusBarCornerRadius();
        final int initialStatusInset = prefs.getStatusBarHorizontalInset();
        final String initialClockStyle = prefs.getTopPaneClockStyle();
        final int initialIndicatorRadius = prefs.getStatusIndicatorCornerRadius();
        final int initialTerminal = prefs.getTerminalBackgroundOpacity();
        final boolean initialTerminalBorder = prefs.isTerminalBorderEnabled();
        final int initialTerminalGlassBlur = prefs.getTerminalGlassBlurRadius();
        final int initialTerminalGlassGrain = prefs.getTerminalGlassGrain();
        final int initialTerminalCornerRadius = prefs.getTerminalCornerRadius();
        final int initialTerminalGap = prefs.getTerminalPaneGap();
        final int initialWallpaperDim = prefs.getWallpaperBackdropDim();
        final String initialMaterial = prefs.getSurfaceMaterial();
        final int initialMaterialIntensity = prefs.getSurfaceMaterialIntensity();
        final int[] initialBase = new int[SurfaceProperty.values().length];
        for (SurfaceProperty property : SurfaceProperty.values())
            initialBase[property.ordinal()] = prefs.getSurfaceBaseValue(property);

        return () -> {
            if (prefs() == null)
                return;
            restoreSurfaceEditorLinks(links);
            prefs().setExtraKeysBlurRadius(initialBlur);
            prefs().setAppBarOpacity(initialOpacity);
            prefs().setDockGlassGrain(initialGrain);
            prefs().setAppLauncherDockCornerRadius(initialDockRadius);
            prefs().setDockHorizontalInset(initialDockInset);
            prefs().setAppLauncherBarHeightScale(initialBarHeight);
            prefs().setAppLauncherButtonCount(initialButtonCount);
            prefs().setAppLauncherDockStyle(initialStyle);
            prefs().setInAppKeyboardHeightScale(initialKeyboardHeight);
            prefs().setInAppKeyboardKeyMarginScale(initialKeyboardSpacing);
            prefs().setInAppKeyboardKeyCornerRadiusDp(initialKeyboardRadius);
            prefs().setInAppKeyboardKeyOpacity(initialKeyboardKeyOpacity);
            prefs().setInAppKeyboardBackgroundOpacity(initialKeyboardBgOpacity);
            prefs().setInAppKeyboardHorizontalInset(initialKeyboardInset);
            prefs().setInAppKeyboardColorScheme(initialKeyboardColorScheme);
            prefs().setInAppKeyboardTheme(initialKeyboardTheme);
            prefs().setStatusBarBlurRadius(initialStatusBlur);
            prefs().setStatusBarOpacity(initialStatusOpacity);
            prefs().setStatusBarGrain(initialStatusGrain);
            prefs().setStatusBarCornerRadius(initialStatusRadius);
            prefs().setStatusBarHorizontalInset(initialStatusInset);
            prefs().setTopPaneClockStyle(initialClockStyle);
            prefs().setStatusIndicatorCornerRadius(initialIndicatorRadius);
            prefs().setTerminalBackgroundOpacity(initialTerminal);
            prefs().setTerminalBorderEnabled(initialTerminalBorder);
            prefs().setTerminalGlassBlurRadius(initialTerminalGlassBlur);
            prefs().setTerminalGlassGrain(initialTerminalGlassGrain);
            if (prefs().getTerminalCornerRadius() != initialTerminalCornerRadius) {
                prefs().setTerminalCornerRadius(initialTerminalCornerRadius);
                mHost.applyTerminalSurfaceAppearance();
            }
            if (prefs().getTerminalPaneGap() != initialTerminalGap) {
                prefs().setTerminalPaneGap(initialTerminalGap);
                mHost.refreshPaneLayout();
                mHost.applyTerminalSurfaceAppearance();
            }
            prefs().setWallpaperBackdropDim(initialWallpaperDim);
            for (SurfaceProperty property : SurfaceProperty.values())
                prefs().setSurfaceBaseValue(property, initialBase[property.ordinal()]);
            prefs().setSurfaceMaterial(initialMaterial);
            prefs().setSurfaceMaterialIntensity(initialMaterialIntensity);
            // One place re-reads the clock's face, alignment, 12-hour and lazy mode — and restyles
            // the row's chips.
            mHost.refreshTerminalWindowBar();
            if (keyboard() != null) {
                keyboard().previewSurfaceEditorHeightScale(initialKeyboardHeight);
                keyboard().previewSurfaceEditorKeyOpacity(initialKeyboardKeyOpacity);
                // The colour scheme and theme are read at render time, so the keyboard has to be
                // told to re-read them; the preview calls above only touch geometry.
                keyboard().onPreferencesReloaded();
            }
            applySurfaceEditorStructuralPreview();
            exitSurfaceEditor();
        };
    }

    // ------------------------------------------------------------------------- the pill's wiring

    private void bindPill(@NonNull Pill pill) {
        // Before the first layout pass has chosen a shape, so the pill's first frame is never drawn
        // as bare text over the wallpaper.
        pill.root.setBackground(buildPillBackground(false));
        // The title is the surface switcher; an outlined capsule is what says so before a touch.
        pill.title.setBackground(buildSelectorBackground());
        setLeadingIcon(pill.looks, R.drawable.ic_symbol_palette);
        setLeadingIcon(pill.fine, R.drawable.ic_symbol_tune);
        pill.title.setOnClickListener(view -> togglePanel(PANEL_SURFACES));
        pill.property.setOnClickListener(view -> showPropertyPicker());
        pill.looks.setOnClickListener(view -> togglePanel(PANEL_LOOKS));
        pill.fine.setOnClickListener(view -> togglePanel(PANEL_FINE));
        pill.done.setOnClickListener(view -> exitSurfaceEditor());
        // Done is the only commit. The ✕ and the back press both route through the unsaved-changes
        // gate, so the two agree with each other without the close glyph silently throwing work
        // away.
        pill.close.setOnClickListener(view -> requestClose());
        pill.reset.setOnClickListener(view -> reattachOpenControl());
        pill.chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (mRestatingChips || checkedIds.isEmpty())
                return;
            Chip chip = group.findViewById(checkedIds.get(0));
            Object tag = chip == null ? null : chip.getTag();
            if (tag instanceof String)
                openControl((String) tag, true);
        });
        pill.slider.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override void onSliderChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Control control = openControl();
                if (control == null)
                    return;
                String text = valueText(control, progress);
                setValueText(pill, text);
                if (!fromUser)
                    return;
                peek(controlLabel(control), text);
                if (control.kind == Kind.LOOK)
                    applyLookIntensity(mSelectedSlot, progress);
                else
                    writeControl(mSelectedSlot, control, progress);
            }
        });
        pill.toggle.setOnCheckedChangeListener((button, checked) -> {
            Control control = openControl();
            if (control == null || control.kind != Kind.SWITCH || prefs() == null)
                return;
            if (control.read(prefs()) == (checked ? 1 : 0))
                return;
            writeControl(mSelectedSlot, control, checked ? 1 : 0);
            // The border decides whether the terminal's blur and grain rows exist at all, so an
            // open Fine panel restates too.
            syncOpenPanel();
            syncPill();
        });
        pill.material.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mSyncingMaterialMacro || prefs() == null)
                return;
            String material = materialForButton(checkedId,
                R.id.surface_editor_pill_material_solid, R.id.surface_editor_pill_material_frost);
            // Remembered before the write, so the derivation that follows reads the family back as
            // the one just picked rather than as another curve crossing the same numbers.
            mChosenFamily.put(mSelectedSlot, material);
            applyLook(mSelectedSlot, material, lookIntensity(mSelectedSlot));
        });
        pill.shape.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mRestatingChips || prefs() == null)
                return;
            String style = checkedId == R.id.surface_editor_pill_shape_floating
                ? SegmentedPillPreference.VALUE_ROUNDED : SegmentedPillPreference.VALUE_DEFAULT;
            if (style.equals(prefs().getAppLauncherDockStyle()))
                return;
            prefs().setAppLauncherDockStyle(style);
            applySurfaceEditorStructuralPreview();
            // Docked or Floating decides which margin rows exist, on the chips and in the panels.
            syncOpenPanel();
            syncPill();
        });
    }

    // ---------------------------------------------------------------------------- the selection

    /**
     * Points the pill at a surface: its chips, its open property, its ring and its park position.
     *
     * @param animate a real pick, which slides the ring in and travels the pill to the new park
     *                rather than fading it out and in; false while restating on entry
     */
    private void selectSurface(@NonNull SurfaceSlot slot, boolean animate) {
        Pill pill = mPill;
        if (pill == null)
            return;
        boolean changed = mSelectedSlot != slot;
        mSelectedSlot = slot;
        rebuildChips(pill, slot);
        openControl(openControlId(slot), false);
        // The ⋯ and Fine panels hold one surface's rows, so a new pick rebuilds them in place —
        // or closes them, for a surface with nothing to fill them with. Looks is global and stays.
        if (changed && (mOpenPanel == PANEL_MORE || mOpenPanel == PANEL_FINE))
            openPanel(mOpenPanel);
        syncPill();
        positionSelectionRing(animate && changed);
        parkPill(animate && changed);
    }

    /** The chip this surface was left on, defaulting to the first one it offers. */
    @NonNull
    private String openControlId(@NonNull SurfaceSlot slot) {
        String remembered = mOpenControlId.get(slot);
        if (remembered != null && SurfaceEditorProperties.find(slot, remembered) != null)
            return remembered;
        List<Control> chips = SurfaceEditorProperties.chips(slot);
        return chips.isEmpty() ? SurfaceEditorProperties.ID_LOOK : chips.get(0).id;
    }

    @Nullable
    private Control openControl() {
        return SurfaceEditorProperties.find(mSelectedSlot, mOpenControlId.get(mSelectedSlot));
    }

    /** The editable set behind the chip row, folded to one number so restates can skip a rebuild. */
    private long chipAvailabilitySignature(@NonNull SurfaceSlot slot) {
        long signature = slot.ordinal();
        for (Control control : SurfaceEditorProperties.chips(slot))
            signature = signature * 31 + (isAvailable(slot, control) ? 1 : 0);
        return signature;
    }

    /** What the chip row is currently built for; rebuilt when the editable set moves. */
    private long mShownChipSignature = Long.MIN_VALUE;

    /**
     * Rebuilds the chip row from the table, plus the trailing ⋯ where a surface has more. Only
     * editable properties render: a chip whose control the current state makes inert — the dock's
     * margin while it sits flush in Docked — is left out rather than shown dead, and comes back
     * the moment the state that hid it changes.
     */
    private void rebuildChips(@NonNull Pill pill, @NonNull SurfaceSlot slot) {
        mShownChipSignature = chipAvailabilitySignature(slot);
        pill.chips.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mHost.context());
        for (Control control : SurfaceEditorProperties.chips(slot)) {
            if (!isAvailable(slot, control))
                continue;
            Chip chip = (Chip) inflater.inflate(
                R.layout.surface_editor_pill_chip, pill.chips, false);
            chip.setId(View.generateViewId());
            chip.setText(control.chipLabelRes);
            chip.setTag(control.id);
            pill.chips.addView(chip);
        }
        if (SurfaceEditorProperties.more(slot).isEmpty())
            return;
        Chip more = (Chip) inflater.inflate(R.layout.surface_editor_pill_chip, pill.chips, false);
        more.setId(View.generateViewId());
        more.setText(R.string.termux_surface_editor_more);
        more.setCheckable(false);
        more.setContentDescription(getString(R.string.termux_surface_editor_more_description,
            getString(SurfaceEditorRows.slotLabel(slot))));
        more.setOnClickListener(view -> togglePanel(PANEL_MORE));
        pill.chips.addView(more);
    }

    /** Opens one property in the pill's single row. */
    private void openControl(@NonNull String id, boolean fromUser) {
        Control control = SurfaceEditorProperties.find(mSelectedSlot, id);
        if (control == null)
            return;
        mOpenControlId.put(mSelectedSlot, id);
        if (fromUser)
            syncPill();
    }

    /**
     * Restates the whole pill from preferences: title, chips, the Look panel, the open row and the
     * unsaved badge. Called once per previewed frame, so every read-out skips a text that has not
     * changed — the pill is small, but a setText on a wrap_content row still costs a layout pass.
     */
    private void syncPill() {
        Pill pill = mPill;
        if (pill == null || prefs() == null || !mSurfaceEditorOpen)
            return;
        String slotName = getString(SurfaceEditorRows.slotLabel(mSelectedSlot));
        String title = getString(R.string.termux_surface_editor_pill_title, slotName);
        if (!title.equals(pill.shownTitle)) {
            pill.shownTitle = title;
            pill.title.setText(title);
            pill.title.setContentDescription(getString(
                R.string.termux_surface_editor_pill_title_description, slotName));
        }

        // A structural change (dock style, terminal border) can add or remove chips: only editable
        // properties render, so the row is rebuilt whenever the editable set moves — and an open
        // control that just left the row falls back to the surface's first chip.
        long chipSignature = chipAvailabilitySignature(mSelectedSlot);
        if (chipSignature != mShownChipSignature) {
            mShownChipSignature = chipSignature;
            rebuildChips(pill, mSelectedSlot);
        }
        Control control = openControl();
        if (control != null && !isAvailable(mSelectedSlot, control)) {
            mOpenControlId.remove(mSelectedSlot);
            openControl(openControlId(mSelectedSlot), false);
            control = openControl();
        }
        if (control == null)
            return;

        mRestatingChips = true;
        try {
            for (int i = 0; i < pill.chips.getChildCount(); i++) {
                View child = pill.chips.getChildAt(i);
                if (!(child instanceof Chip))
                    continue;
                Chip chip = (Chip) child;
                if (!chip.isCheckable())
                    continue;
                boolean shouldCheck = control.id.equals(chip.getTag());
                if (chip.isChecked() != shouldCheck)
                    chip.setChecked(shouldCheck);
            }
            // One row has no title of its own, so the surface rides on the property dropdown:
            // "which surface" must stay answerable at every size.
            pill.property.setText(getString(R.string.termux_surface_editor_property_title,
                slotName, getString(control.chipLabelRes)));
            syncShapeGroup(pill);
        } finally {
            mRestatingChips = false;
        }

        boolean look = control.kind == Kind.LOOK;
        boolean toggle = control.kind == Kind.SWITCH;
        boolean shape = control.kind == Kind.SHAPE;
        pill.look.setVisibility(look && mLayoutMode != Mode.ONE_ROW ? View.VISIBLE : View.GONE);
        pill.slider.setVisibility(toggle || shape ? View.GONE : View.VISIBLE);
        pill.toggle.setVisibility(toggle ? View.VISIBLE : View.GONE);
        if (look)
            syncLookMaterial(pill);

        boolean available = isAvailable(mSelectedSlot, control);
        int shown = shownValueOf(mSelectedSlot, control);
        if (shape) {
            setValueText(pill, "");
        } else if (toggle) {
            pill.toggle.setText(control.labelRes);
            if (pill.toggle.isChecked() != (shown != 0))
                pill.toggle.setChecked(shown != 0);
            pill.toggle.setEnabled(available);
            setValueText(pill, "");
        } else {
            if (pill.slider.getMax() != control.max)
                pill.slider.setMax(control.max);
            if (pill.slider.getProgress() != shown)
                pill.slider.setProgress(shown);
            pill.slider.setEnabled(available);
            pill.slider.setAlpha(available ? 1f : SURFACE_TUNING_DISABLED_ALPHA);
            // A triple no point on any curve reproduces reads as Custom rather than as a percent
            // that would not put it back: the intensity track is still live, and moving it is what
            // adopts a curve again.
            boolean custom = look && lookMaterial(mSelectedSlot) == null;
            setValueText(pill, !available
                ? getString(R.string.termux_surface_tuning_value_not_applicable)
                : custom ? getString(R.string.termux_surface_tuning_material_custom)
                    : valueText(control, shown));
            pill.value.setAlpha(available ? 1f : SURFACE_TUNING_DISABLED_ALPHA);
        }

        syncResetGlyph(pill, control);
        setFootnote(pill, footnoteFor(mSelectedSlot, control, available, slotName));
        updateSurfaceEditorDirtyBadge();
    }

    private void setValueText(@NonNull Pill pill, @NonNull String text) {
        if (text.equals(pill.shownValue))
            return;
        pill.shownValue = text;
        pill.value.setText(text);
    }

    private void setFootnote(@NonNull Pill pill, @Nullable String text) {
        String resolved = text == null ? "" : text;
        if (resolved.equals(pill.shownFootnote))
            return;
        pill.shownFootnote = resolved;
        pill.footnote.setText(resolved);
        pill.footnote.setVisibility(
            resolved.isEmpty() || mLayoutMode != Mode.FULL ? View.GONE : View.VISIBLE);
    }

    /**
     * The ↺ next to the open row. It shows in both states deliberately: muted while the value is
     * Base's, accented once the surface has taken its own, which is the only place that distinction
     * is visible without moving something.
     */
    private void syncResetGlyph(@NonNull Pill pill, @NonNull Control control) {
        if (mLayoutMode == Mode.ONE_ROW) {
            pill.reset.setVisibility(View.GONE);
            return;
        }
        boolean linked = isLinkedControl(control);
        if (!linked) {
            pill.reset.setVisibility(View.INVISIBLE);
            pill.reset.setClickable(false);
            return;
        }
        boolean own = hasOwnValue(mSelectedSlot, control);
        pill.reset.setVisibility(View.VISIBLE);
        pill.reset.setClickable(own);
        pill.reset.setFocusable(own);
        pill.reset.setAlpha(own ? 1f : SURFACE_TUNING_DISABLED_ALPHA);
        pill.reset.setContentDescription(getString(own
                ? R.string.termux_surface_tuning_link_detached_description
                : R.string.termux_surface_tuning_link_inherited_description,
            getString(SurfaceEditorRows.slotLabel(mSelectedSlot))));
    }

    /** Whether this control is a cell of the inheritance model, so it has a link to show. */
    private boolean isLinkedControl(@NonNull Control control) {
        return control.cell != null
            || (control.kind == Kind.LOOK
                && !SurfaceEditorProperties.fine(mSelectedSlot).isEmpty());
    }

    /** Whether the selected surface has taken its own value for this control. */
    private boolean hasOwnValue(@NonNull SurfaceSlot slot, @NonNull Control control) {
        if (prefs() == null)
            return false;
        if (control.cell != null)
            return !prefs().isSurfaceInheriting(slot, control.cell.property);
        if (control.kind != Kind.LOOK)
            return false;
        for (Control fine : SurfaceEditorProperties.fine(slot)) {
            if (fine.cell != null && !prefs().isSurfaceInheriting(slot, fine.cell.property))
                return true;
        }
        return false;
    }

    /** The ↺ was tapped: this control's cells go back on Base. */
    private void reattachOpenControl() {
        Control control = openControl();
        if (control == null || prefs() == null || !hasOwnValue(mSelectedSlot, control))
            return;
        if (control.cell != null) {
            prefs().setSurfaceInheriting(mSelectedSlot, control.cell.property, true);
        } else {
            for (Control fine : SurfaceEditorProperties.fine(mSelectedSlot)) {
                if (fine.cell != null)
                    prefs().setSurfaceInheriting(mSelectedSlot, fine.cell.property, true);
            }
        }
        applySurfaceEditorStructuralPreview();
        syncPill();
    }

    /**
     * The inheritance story, said per row and in place. Both halves matter: a surface following
     * Base has to advertise that moving the control is what detaches it, and a detached one has to
     * name the number ↺ would return to. The counted sentence this replaced ("1 property has its
     * own value") could say neither.
     */
    @Nullable
    private String footnoteFor(@NonNull SurfaceSlot slot, @NonNull Control control,
                               boolean available, @NonNull String slotName) {
        if (!available)
            return unavailableReason(slot, control);
        if (!isLinkedControl(control))
            return null;
        boolean own = hasOwnValue(slot, control);
        if (control.kind == Kind.LOOK)
            return getString(own
                ? R.string.termux_surface_editor_footnote_look_own
                : R.string.termux_surface_editor_footnote_look_base, slotName);
        String noun = getString(control.nounRes());
        if (!own)
            return getString(R.string.termux_surface_editor_footnote_base, slotName);
        return getString(R.string.termux_surface_editor_footnote_own, slotName, noun,
            valueText(control, baseValueOf(control)));
    }

    @Nullable
    private String unavailableReason(@NonNull SurfaceSlot slot, @NonNull Control control) {
        if (control.cell != null && control.cell.property == SurfaceProperty.SIDE_GAP)
            return getString(R.string.termux_surface_editor_unavailable_gap);
        if (slot == SurfaceSlot.CANVAS)
            return getString(R.string.termux_surface_editor_unavailable_border);
        return null;
    }

    /**
     * Whether a control can act at all right now.
     *
     * <p>Docked surfaces are flush with the screen edges by definition, so their margin has no
     * number to give; the terminal's glass has nothing to live inside until its border is on. A
     * control the state makes inert does not render at all — a dead slider is clutter, not
     * signage — and the control that re-enables it (the dock's Style, the terminal's Border) is
     * standing on the same card, so the way back stays visible.
     */
    private boolean isAvailable(@Nullable SurfaceSlot slot, @NonNull Control control) {
        if (SurfaceEditorProperties.ID_BASE_GAP.equals(control.id))
            return mHost.isFloatingDock();
        if (slot == null)
            return true;
        if (control.cell != null && control.cell.property == SurfaceProperty.SIDE_GAP)
            return mHost.isFloatingDock();
        if (SurfaceEditorProperties.ID_TERMINAL_RADIUS.equals(control.id))
            return !mHost.isFloatingDock();
        if (slot == SurfaceSlot.CANVAS && prefs() != null && !prefs().isTerminalBorderEnabled()) {
            if (SurfaceEditorProperties.ID_FINE_BLUR.equals(control.id)
                || SurfaceEditorProperties.ID_FINE_GRAIN.equals(control.id))
                return false;
        }
        return true;
    }

    /** Where a control's slider should sit: the resolved number, capped to its own track. */
    private int shownValueOf(@NonNull SurfaceSlot slot, @NonNull Control control) {
        if (prefs() == null)
            return 0;
        if (control.kind == Kind.LOOK)
            return lookIntensity(slot);
        if (SurfaceEditorProperties.ID_CHIP_RADIUS.equals(control.id))
            return shownIndicatorRadius();
        int value = control.cell != null
            ? surfaceEditorSliderValue(slot, control.cell.property,
                prefs().isSurfaceInheriting(slot, control.cell.property)
                    ? prefs().getSurfaceBaseValue(control.cell.property)
                    : prefs().getSurfaceOverrideValue(slot, control.cell.property))
            : control.read(prefs());
        return Math.max(0, Math.min(control.max, value));
    }

    /** The number ↺ would put back: Base's own, resolved and capped like the row's. */
    private int baseValueOf(@NonNull Control control) {
        if (prefs() == null || control.cell == null)
            return 0;
        return Math.max(0, Math.min(control.max, surfaceEditorSliderValue(null,
            control.cell.property, prefs().getSurfaceBaseValue(control.cell.property))));
    }

    /**
     * Where a slider should sit for a stored value. Corner radius carries a "theme-defined"
     * sentinel below zero, which is not a position on a 0-40 track; the slider shows the number the
     * surface will actually use instead — the capsule's own radius while Floating, a straight edge
     * while Docked — so the control is never parked somewhere the surface is not.
     */
    private int surfaceEditorSliderValue(@Nullable SurfaceSlot slot, SurfaceProperty property,
                                         int stored) {
        if (property != SurfaceProperty.CORNER_RADIUS || stored >= 0)
            return stored;
        return TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(slot, mHost.isFloatingDock());
    }

    /**
     * The stored {@code -1} — "still following the bar" — shown as the shape the bar is actually
     * giving the chips right now, so the slider starts where the eye says it should.
     */
    private int shownIndicatorRadius() {
        int stored = prefs().getStatusIndicatorCornerRadius();
        if (stored >= 0)
            return stored;
        if (!mHost.isFloatingDock())
            return 0;
        return Math.min(TermuxPreferenceConstants.TERMUX_APP.MAX_STATUS_INDICATOR_CORNER_RADIUS,
            Math.min(40, surfaceEditorSliderValue(SurfaceSlot.STATUS,
                SurfaceProperty.CORNER_RADIUS, prefs().getStatusBarCornerRadius())));
    }

    /** One control's number in its own unit. */
    @NonNull
    private String valueText(@NonNull Control control, int value) {
        switch (control.unit) {
            case DP:
                return getString(R.string.termux_dock_tuning_value_dp, value);
            case DP_TENTHS:
                return getString(R.string.termux_dock_tuning_value_dp, Math.round(value / 10f));
            case PERCENT:
                return getString(R.string.termux_dock_tuning_value_percent, value);
            case DOCK_SIZE:
                return dockSizePresetLabel(value);
            case COUNT:
                return Integer.toString(Math.max(1, value));
            default:
                return "";
        }
    }

    /** The label a control announces over the surface while the pill peeks. */
    @NonNull
    private String controlLabel(@NonNull Control control) {
        if (SurfaceEditorProperties.ID_TERMINAL_GAP.equals(control.id))
            return getString(terminalGapLabelRes());
        return getString(control.labelRes);
    }

    // ------------------------------------------------------------------- one writer per control
    //
    // Every control writes through its own accessors: a cell of the inheritance model through the
    // preference setter that owns its clamp — inherit-aware, so the control never needs to know
    // whether it is moving Base or one detached surface — and everything outside the cascade
    // through its own. A moved cell leaves Base first, so the change lands on that surface alone
    // rather than dragging every other surface with it, which is the whole point of the model.

    private void writeControl(@NonNull SurfaceSlot slot, @NonNull Control control, int value) {
        if (prefs() == null || !isAvailable(slot, control))
            return;
        if (SurfaceEditorProperties.ID_BASE_INTENSITY.equals(control.id)) {
            applyBaseMacro(prefs().getSurfaceMaterial(), value);
            return;
        }
        if (control.cell != null)
            detachSurfaceRowForEdit(slot, control.cell.property);
        control.write(prefs(), value);
        afterWrite(control, value);
        requestSurfaceEditorPreview(control.previewScopes);
    }

    /**
     * The few controls whose live preview is not the glass pipeline.
     *
     * <p>The keyboard's own metrics are previewed on the view rather than re-rendered, so its
     * geometry tracks the drag without a layout re-parse per tick; the status row restyles both its
     * chips from one place so they cannot drift apart; and the pane gap is laid out by the split
     * tree, so it needs a re-render rather than a restyle.
     */
    private void afterWrite(@NonNull Control control, int value) {
        switch (control.id) {
            case SurfaceEditorProperties.ID_KEYBOARD_HEIGHT:
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorHeightScale(keyboardEditorValue(value,
                        TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                        TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
                updateSurfaceEditorDirtyBadge();
                break;
            case SurfaceEditorProperties.ID_KEYBOARD_SPACING:
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyMarginScale(keyboardEditorValue(value,
                        TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                        TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
                updateSurfaceEditorDirtyBadge();
                break;
            case SurfaceEditorProperties.ID_KEYBOARD_KEY_RADIUS:
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyCornerRadiusDp(value / 10f);
                updateSurfaceEditorDirtyBadge();
                break;
            case SurfaceEditorProperties.ID_KEYBOARD_KEY_OPACITY:
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyOpacity(value);
                updateSurfaceEditorDirtyBadge();
                break;
            case SurfaceEditorProperties.ID_CHIP_RADIUS:
                mHost.refreshTerminalWindowBar();
                updateSurfaceEditorDirtyBadge();
                break;
            case SurfaceEditorProperties.ID_TERMINAL_GAP:
                mHost.refreshPaneLayout();
                break;
            case SurfaceEditorProperties.ID_BORDER:
                applySurfaceEditorStructuralPreview();
                break;
            default:
                break;
        }
    }

    /** A moved cell leaves Base if it still followed it, keeping the number it was showing. */
    private void detachSurfaceRowForEdit(@NonNull SurfaceSlot slot,
                                         @NonNull SurfaceProperty property) {
        if (prefs() == null || !prefs().isSurfaceInheriting(slot, property))
            return;
        prefs().detachSurfaceValue(slot, property,
            prefs().getSurfaceOverrideValue(slot, property));
    }

    /** Every link flag as one string, so the undo path can restore the shape as well as the numbers. */
    @NonNull
    private String surfaceEditorLinkSignature() {
        if (prefs() == null)
            return "";
        StringBuilder out = new StringBuilder(64);
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values())
                out.append(prefs().isSurfaceInheriting(slot, property) ? '1' : '0');
        }
        return out.toString();
    }

    /** Puts the links back the way {@link #surfaceEditorLinkSignature()} found them. */
    private void restoreSurfaceEditorLinks(@Nullable String signature) {
        if (prefs() == null || signature == null)
            return;
        int index = 0;
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (index >= signature.length())
                    return;
                prefs().setSurfaceInheriting(slot, property, signature.charAt(index++) == '1');
            }
        }
    }

    // ------------------------------------------------------------------------------------- Look
    //
    // Blur, opacity and grain as one decision: a material family and an intensity. On a surface the
    // macro writes that surface's own triple through the same cell setters a raw slider would, so a
    // Look moved on the dock detaches the dock and leaves everything else alone. On Base it writes
    // the shared layer, and the two stored macro keys remember which point the shared triple came
    // from — a triple no point on any curve reproduces simply is Custom, and is never snapped.

    /** Suppresses the toggle listeners while sync is restating a group programmatically. */
    private boolean mSyncingMaterialMacro;

    @NonNull
    private String materialForButton(int buttonId, int solidId, int frostId) {
        if (buttonId == solidId)
            return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_SOLID;
        if (buttonId == frostId)
            return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_FROST;
        return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_GLASS;
    }

    private static final String[] MATERIAL_FAMILIES = {
        TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_GLASS,
        TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_FROST,
        TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_SOLID};

    /**
     * The family the user last picked on a surface, for this session only.
     *
     * <p>The design adds no persisted state and a surface's triple is already the whole truth, so
     * the point behind a Look is derived from the numbers. Derivation is ambiguous where a surface
     * owns fewer than three of them, though — the keyboard owns opacity alone, and one opacity sits
     * on all three curves — so a family picked by hand is remembered long enough that the segmented
     * control does not answer back with a different one. Forgetting it between sessions costs
     * nothing: the numbers are what render, and the derived family is only a label for them.
     */
    private final Map<SurfaceSlot, String> mChosenFamily = new EnumMap<>(SurfaceSlot.class);

    /** The last derived point per surface, keyed by the triple it was derived from. */
    private final Map<SurfaceSlot, int[]> mLookCacheTriple = new EnumMap<>(SurfaceSlot.class);
    private final Map<SurfaceSlot, Object[]> mLookCachePoint = new EnumMap<>(SurfaceSlot.class);

    /**
     * The glass triple a surface is actually rendering, with {@code -1} for a property it does not
     * own or cannot use. Three preference reads, so the curve scan below costs no more.
     */
    @NonNull
    private int[] resolvedTriple(@NonNull SurfaceSlot slot) {
        int[] have = {-1, -1, -1};
        for (Control fine : SurfaceEditorProperties.fine(slot)) {
            if (fine.cell == null || !isAvailable(slot, fine))
                continue;
            have[materialIndex(fine.cell.property)] = shownValueOf(slot, fine);
        }
        return have;
    }

    /**
     * The (family, intensity) point that explains a surface's current triple, or null for Custom.
     *
     * <p>Three candidates in order, and the order is the whole point: the family the user picked on
     * this surface, then Base's own stored point — so a surface following Base reads as the look
     * Base is wearing rather than as whichever curve happens to cross it — and only then a scan of
     * the curves. A triple no point reproduces is Custom, and is never snapped: touching the macro
     * is the one gesture that re-applies a curve.
     */
    @Nullable
    private Object[] deriveLook(@NonNull SurfaceSlot slot) {
        if (prefs() == null)
            return null;
        int[] have = resolvedTriple(slot);
        int[] cachedFor = mLookCacheTriple.get(slot);
        if (cachedFor != null && cachedFor[0] == have[0] && cachedFor[1] == have[1]
            && cachedFor[2] == have[2])
            return mLookCachePoint.get(slot);
        Object[] point = scanLook(slot, have);
        mLookCacheTriple.put(slot, have);
        if (point == null) mLookCachePoint.remove(slot);
        else mLookCachePoint.put(slot, point);
        return point;
    }

    @Nullable
    private Object[] scanLook(@NonNull SurfaceSlot slot, @NonNull int[] have) {
        String chosen = mChosenFamily.get(slot);
        if (chosen != null) {
            int intensity = intensityOn(chosen, have);
            if (intensity >= 0)
                return new Object[] {chosen, intensity};
        }
        String baseMaterial = prefs().getSurfaceMaterial();
        int baseIntensity = prefs().getSurfaceMaterialIntensity();
        if (matchesTriple(SurfaceMaterials.triple(baseMaterial, baseIntensity), have))
            return new Object[] {baseMaterial, baseIntensity};
        for (String family : MATERIAL_FAMILIES) {
            int intensity = intensityOn(family, have);
            if (intensity >= 0)
                return new Object[] {family, intensity};
        }
        return null;
    }

    /** The lowest intensity on one curve that reproduces the numbers, or -1 for none. */
    private static int intensityOn(@NonNull String family, @NonNull int[] have) {
        for (int intensity = 0; intensity <= 100; intensity++) {
            if (matchesTriple(SurfaceMaterials.triple(family, intensity), have))
                return intensity;
        }
        return -1;
    }

    /** Whether a curve's triple reproduces every number the surface actually has. */
    private static boolean matchesTriple(@NonNull int[] curve, @NonNull int[] have) {
        for (int i = 0; i < have.length; i++) {
            if (have[i] >= 0 && have[i] != curve[i])
                return false;
        }
        return true;
    }

    private static int materialIndex(@NonNull SurfaceProperty property) {
        switch (property) {
            case BLUR: return SurfaceMaterials.BLUR;
            case GRAIN: return SurfaceMaterials.GRAIN;
            default: return SurfaceMaterials.OPACITY;
        }
    }

    /** The intensity the Look slider sits at: the derived point's, or the shared one for Custom. */
    private int lookIntensity(@NonNull SurfaceSlot slot) {
        Object[] point = deriveLook(slot);
        if (point != null)
            return (Integer) point[1];
        return prefs() == null ? 50 : prefs().getSurfaceMaterialIntensity();
    }

    @Nullable
    private String lookMaterial(@NonNull SurfaceSlot slot) {
        Object[] point = deriveLook(slot);
        return point == null ? null : (String) point[0];
    }

    private void applyLookIntensity(@NonNull SurfaceSlot slot, int intensity) {
        String material = lookMaterial(slot);
        applyLook(slot, material != null ? material
            : TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_GLASS, intensity);
    }

    /** Writes one surface's glass triple from a point on a curve. */
    private void applyLook(@NonNull SurfaceSlot slot, @NonNull String material, int intensity) {
        if (prefs() == null)
            return;
        int[] triple = SurfaceMaterials.triple(material, intensity);
        boolean blurMoved = false;
        for (Control fine : SurfaceEditorProperties.fine(slot)) {
            if (fine.cell == null || !isAvailable(slot, fine))
                continue;
            int target = triple[materialIndex(fine.cell.property)];
            if (fine.cell.property == SurfaceProperty.BLUR
                && shownValueOf(slot, fine) != target)
                blurMoved = true;
            detachSurfaceRowForEdit(slot, fine.cell.property);
            fine.cell.write.accept(prefs(), target);
        }
        // The blur curve moves a whole dp only every few intensity ticks — and mid-drag even those
        // do not re-blur the wallpaper, the frame the editor can least afford. The release settles
        // it once, like geometry.
        int scopes = SurfaceEditorProperties.PREVIEW_GLASS | SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD;
        if (blurMoved) {
            if (mSliderDragActive) mDragTouchedBlur = true;
            else scopes |= SurfaceEditorProperties.PREVIEW_BLUR;
        }
        requestSurfaceEditorPreview(scopes);
        syncPill();
    }

    /** The shared layer's macro: the same curves, written through the Base setters. */
    private void applyBaseMacro(@NonNull String material, int intensity) {
        if (prefs() == null)
            return;
        prefs().setSurfaceMaterial(material);
        prefs().setSurfaceMaterialIntensity(intensity);
        int previousBlur = prefs().getSurfaceBaseValue(SurfaceProperty.BLUR);
        int[] triple = SurfaceMaterials.triple(material, intensity);
        prefs().setSurfaceBaseValue(SurfaceProperty.BLUR, triple[SurfaceMaterials.BLUR]);
        prefs().setSurfaceBaseValue(SurfaceProperty.OPACITY, triple[SurfaceMaterials.OPACITY]);
        prefs().setSurfaceBaseValue(SurfaceProperty.GRAIN, triple[SurfaceMaterials.GRAIN]);
        int scopes = SurfaceEditorProperties.PREVIEW_GLASS | SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD;
        if (triple[SurfaceMaterials.BLUR] != previousBlur) {
            if (mSliderDragActive) mDragTouchedBlur = true;
            else scopes |= SurfaceEditorProperties.PREVIEW_BLUR;
        }
        requestSurfaceEditorPreview(scopes);
    }

    /** Restates the pill's family segments; a Custom triple deselects rather than lying. */
    private void syncLookMaterial(@NonNull Pill pill) {
        String material = lookMaterial(mSelectedSlot);
        mSyncingMaterialMacro = true;
        try {
            if (material == null) {
                if (pill.material.getCheckedButtonId() != View.NO_ID)
                    pill.material.clearChecked();
            } else {
                int buttonId = materialButtonId(material,
                    R.id.surface_editor_pill_material_solid,
                    R.id.surface_editor_pill_material_glass,
                    R.id.surface_editor_pill_material_frost);
                if (pill.material.getCheckedButtonId() != buttonId)
                    pill.material.check(buttonId);
            }
        } finally {
            mSyncingMaterialMacro = false;
        }
    }

    private int materialButtonId(@NonNull String material, int solidId, int glassId, int frostId) {
        if (TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_SOLID.equals(material))
            return solidId;
        if (TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_FROST.equals(material))
            return frostId;
        return glassId;
    }

    /** Shape is the dock's own decision; its segments fill the open row while Style is the chip. */
    private void syncShapeGroup(@NonNull Pill pill) {
        Control control = openControl();
        boolean shown = control != null && control.kind == Kind.SHAPE;
        pill.shape.setVisibility(shown ? View.VISIBLE : View.GONE);
        if (!shown || prefs() == null)
            return;
        int buttonId = mHost.isFloatingDock()
            ? R.id.surface_editor_pill_shape_floating : R.id.surface_editor_pill_shape_docked;
        if (pill.shape.getCheckedButtonId() != buttonId)
            pill.shape.check(buttonId);
    }

    // ------------------------------------------------------------------------ shape and placement

    /**
     * Applies the shape the room allows: rows are dropped rather than clipped, and the chip row's
     * job moves into the property dropdown when it goes.
     */
    private void applyLayoutMode(boolean force) {
        Pill pill = mPill;
        if (pill == null)
            return;
        int[] region = pillRegion();
        Mode mode = SurfaceEditorPillMetrics.modeFor(region[1] - region[0],
            dp(FULL_PILL_HEIGHT_DP + 2 * SURFACE_EDITOR_STANDOFF_DP),
            dp(COMPACT_PILL_HEIGHT_DP + 2 * SURFACE_EDITOR_STANDOFF_DP));
        if (mode == mLayoutMode && !force)
            return;
        mLayoutMode = mode;
        boolean oneRow = mode == Mode.ONE_ROW;
        boolean full = mode == Mode.FULL;
        // One row cannot afford an expansion; the panel's jobs fall back to the property dropdown.
        if (oneRow)
            closePanel();
        pill.root.setBackground(buildPillBackground(oneRow));
        int vertical = dp(oneRow ? 4 : full ? 12 : 10);
        pill.root.setPadding(dp(14), vertical, dp(10), vertical);
        pill.header.setVisibility(oneRow ? View.GONE : View.VISIBLE);
        pill.chips.setVisibility(oneRow ? View.GONE : View.VISIBLE);
        pill.property.setVisibility(oneRow ? View.VISIBLE : View.GONE);
        pill.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, full ? 15 : 14);
        pill.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, oneRow ? 13.5f : 15);
        pill.value.setMinWidth(dp(oneRow ? 42 : 54));
        pill.done.setMinWidth(dp(oneRow ? 56 : 66));
        // One row spends every dp it has on the slider, and the ↺ is the only control on the row
        // that has an alternative — the property's own footnote is gone at this size anyway, so
        // reattaching is done from the surface's Look or its sheet instead.
        pill.reset.setVisibility(oneRow ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams rowParams = pill.row.getLayoutParams();
        int rowHeight = dp(oneRow ? 48 : 44);
        if (rowParams.height != rowHeight) {
            rowParams.height = rowHeight;
            pill.row.setLayoutParams(rowParams);
        }
        // One row has no header, so Done rides in the row itself — the pill may lose everything
        // else, but never its slider and never the way out.
        if (oneRow && pill.done.getParent() == pill.header) {
            ((ViewGroup) pill.header).removeView(pill.done);
            ((ViewGroup) pill.row).addView(pill.done);
        } else if (!oneRow && pill.done.getParent() == pill.row) {
            ((ViewGroup) pill.row).removeView(pill.done);
            ((ViewGroup) pill.header).addView(pill.done);
        }
        pill.shownFootnote = null;
        syncPill();
    }

    /** What the full shape measures, near enough to choose a mode before it is laid out. */
    private static final float FULL_PILL_HEIGHT_DP = 172f;
    private static final float COMPACT_PILL_HEIGHT_DP = 130f;

    /** The outlined capsule the surface switcher wears, so the title reads as tappable. */
    @NonNull
    private Drawable buildSelectorBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0);
        background.setCornerRadius(dpToPx(18));
        background.setStroke(Math.max(1, dp(1)), mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant));
        return background;
    }

    /**
     * A small tinted glyph before a text action's label. Every editor action carries both: a bare
     * glyph asks the user to guess, and a bare word buries the action in the reading order.
     */
    private void setLeadingIcon(@NonNull TextView view, int drawableRes) {
        Drawable icon = androidx.core.content.ContextCompat.getDrawable(
            mHost.context(), drawableRes);
        if (icon == null)
            return;
        icon = icon.mutate();
        icon.setTint(mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
            R.color.termux_primary));
        int size = dp(17);
        icon.setBounds(0, 0, size, size);
        view.setCompoundDrawablesRelative(icon, null, null, null);
    }

    @NonNull
    private Drawable buildPillBackground(boolean oneRow) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high));
        background.setCornerRadius(dpToPx(oneRow ? 18 : 24));
        background.setStroke(Math.max(1, dp(1)), mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant));
        return background;
    }

    /**
     * The band the pill lives in, in the host's coordinate space: from just under the launcher's own
     * status chrome down to the accessory stack.
     *
     * <p>Both bounds are read defensively, because the span they describe is not always real. The
     * stack is laid out only while it is visible, and while the dock rides above the system IME on
     * insets it is moved by translation, which {@code getTop()} does not report; the status inset and
     * the window bar are measured against the window, while the host starts below the inset.
     */
    @NonNull
    private int[] pillRegion() {
        View host = mPill == null ? null : mPill.host;
        if (host == null)
            return new int[] {0, 0};
        int parentTopInWindow = 0;
        if (host.getParent() instanceof View) {
            ((View) host.getParent()).getLocationInWindow(mTmpAnchorLocation);
            parentTopInWindow = mTmpAnchorLocation[1];
        }
        int top = Math.max(0, mHost.statusBarInsetTop() - parentTopInWindow);
        View windowBar = mHost.findView(R.id.terminal_window_bar_host);
        if (windowBar != null && windowBar.getVisibility() == View.VISIBLE
            && windowBar.getHeight() > 0) {
            windowBar.getLocationInWindow(mTmpAnchorLocation);
            top = Math.max(top,
                mTmpAnchorLocation[1] + windowBar.getHeight() - parentTopInWindow);
        }
        int parentHeight = host.getParent() instanceof View
            ? ((View) host.getParent()).getHeight() : host.getHeight();
        View stack = mHost.findView(R.id.accessory_stack_container);
        int bottom = stack == null ? parentHeight
            : surfaceEditorStackTopPx(stack, parentHeight);
        return new int[] {top, Math.max(top, bottom)};
    }

    /**
     * Where the accessory stack's top edge is, in the host's coordinate space.
     *
     * <p>{@code getTop()} is the laid-out position and nothing else: a {@code GONE} stack was
     * skipped by the last layout pass and reports wherever it was before that, and the inset-driven
     * dock lift moves the stack with a translation that leaves {@code getTop()} untouched. A hidden
     * stack occupies no room at all, so the region runs to the parent's bottom edge.
     */
    private static int surfaceEditorStackTopPx(@NonNull View stack, int parentHeight) {
        if (stack.getVisibility() != View.VISIBLE || stack.getHeight() <= 0)
            return parentHeight;
        return stack.getTop() + Math.round(stack.getTranslationY());
    }

    /** The selected surface's own rect in the host's space, or null when it is not on screen. */
    @Nullable
    private int[] selectedAnchorRect() {
        View host = mPill == null ? null : mPill.host;
        if (host == null)
            return null;
        View surface = anchorViewFor(mSelectedSlot);
        if (surface == null || surface.getVisibility() != View.VISIBLE
            || surface.getHeight() <= 0)
            return null;
        host.getLocationInWindow(mTmpAnchorLocation);
        int hostTop = mTmpAnchorLocation[1];
        int hostLeft = mTmpAnchorLocation[0];
        surface.getLocationInWindow(mTmpAnchorLocation);
        return new int[] {
            mTmpAnchorLocation[0] - hostLeft,
            mTmpAnchorLocation[1] - hostTop,
            mTmpAnchorLocation[0] - hostLeft + surface.getWidth(),
            mTmpAnchorLocation[1] - hostTop + surface.getHeight()};
    }

    @Nullable
    private View anchorViewFor(@NonNull SurfaceSlot slot) {
        switch (slot) {
            case STATUS:
                return mHost.findView(R.id.terminal_window_bar_host);
            case KEYBOARD:
                return mHost.isInAppKeyboardShown()
                    ? mHost.findView(R.id.inapp_keyboard_view_host) : null;
            case DOCK:
                return mHost.findView(R.id.accessory_surface_host);
            default:
                return null;
        }
    }

    /**
     * Travels the pill to its park position.
     *
     * <p>Placement is computed from the selected surface's anchor and a constant standoff, never
     * from what else is on screen: raising the keyboard moves the ring's neighbours but must not
     * move a pill parked against the status bar. The pill translates rather than fading out and in,
     * so a pick reads as the same pill moving.
     */
    private void parkPill(boolean animate) {
        Pill pill = mPill;
        if (pill == null || !mSurfaceEditorOpen)
            return;
        int height = pill.root.getHeight();
        if (height <= 0) {
            pill.root.post(() -> parkPill(false));
            return;
        }
        int[] region = pillRegion();
        int standoff = dp(SURFACE_EDITOR_STANDOFF_DP);
        int[] anchor = selectedAnchorRect();
        int top;
        if (anchor == null) {
            // The canvas is the region rather than a band inside it, and a surface that is off
            // screen has no edge to stand off from either; both land in the middle of the room.
            top = SurfaceEditorPillMetrics.parkCenteredTopPx(height, region[0], region[1]);
        } else {
            top = SurfaceEditorPillMetrics.parkTopPx(anchor[1], anchor[3],
                mSelectedSlot == SurfaceSlot.STATUS, height, standoff, region[0], region[1]);
        }
        ViewGroup.LayoutParams params = pill.root.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            if (margins.topMargin != top) {
                if (animate) {
                    float delta = margins.topMargin - top;
                    margins.topMargin = top;
                    pill.root.setLayoutParams(margins);
                    pill.root.setTranslationY(delta);
                    pill.root.animate().cancel();
                    pill.root.animate().translationY(0f)
                        .setDuration(SURFACE_EDITOR_PARK_DURATION_MS)
                        .setInterpolator(Motion.settle())
                        .start();
                } else {
                    margins.topMargin = top;
                    pill.root.setLayoutParams(margins);
                    pill.root.setTranslationY(0f);
                }
            }
        }
    }

    // ------------------------------------------------------------------------ the selection ring

    /** Ring geometry the last build used; rebuilding a drawable per layout pass is not free. */
    private long mRingSignature = Long.MIN_VALUE;

    /**
     * Draws the ring on the selected surface's own rect.
     *
     * <p>Positioned from the surface, not from its capture group: the group deliberately overhangs
     * upward so the border handle falls inside it, and a ring drawn on that would sit a finger's
     * width above the thing it is identifying. The canvas has no view of its own, so it takes the
     * free region inset by a hair.
     */
    private void positionSelectionRing(boolean animate) {
        View ring = mHost.findView(R.id.surface_editor_selection_ring);
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (ring == null || overlay == null)
            return;
        if (!mSurfaceEditorOpen) {
            ring.setVisibility(View.GONE);
            return;
        }
        int[] rect = selectedAnchorRect();
        if (rect == null) {
            int[] region = pillRegion();
            int inset = dp(8);
            if (region[1] - region[0] < dp(48)) {
                ring.setVisibility(View.GONE);
                return;
            }
            rect = new int[] {inset, region[0] + inset,
                Math.max(inset + 1, overlay.getWidth() - inset), region[1] - inset};
        }
        int left = Math.max(0, rect[0]);
        int top = Math.max(0, rect[1]);
        int width = Math.max(1, rect[2] - rect[0]);
        int height = Math.max(1, rect[3] - rect[1]);
        ViewGroup.LayoutParams params = ring.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            int right = Math.max(0, overlay.getWidth() - (left + width));
            if (margins.leftMargin != left || margins.rightMargin != right
                || margins.topMargin != top || margins.height != height) {
                margins.leftMargin = left;
                margins.rightMargin = right;
                margins.topMargin = top;
                margins.height = height;
                ring.setLayoutParams(margins);
            }
        }
        long signature = mSelectedSlot.ordinal();
        signature = mixAnchor(signature, ringRadiusDp());
        signature = mixAnchor(signature, mHost.isFloatingDock() ? 1 : 0);
        if (signature != mRingSignature) {
            mRingSignature = signature;
            ring.setBackground(buildSelectionRing());
        }
        if (ring.getVisibility() != View.VISIBLE) {
            ring.setVisibility(View.VISIBLE);
            ring.setAlpha(0f);
            ring.animate().alpha(1f).setDuration(SURFACE_EDITOR_RING_DURATION_MS)
                .setInterpolator(Motion.settle()).start();
        } else if (animate) {
            ring.animate().cancel();
            ring.setAlpha(0.4f);
            ring.animate().alpha(1f).setDuration(SURFACE_EDITOR_RING_DURATION_MS)
                .setInterpolator(Motion.settle()).start();
        }
    }

    /** The radius the ring wears: the surface's own, so the ring reads as that surface's edge. */
    private int ringRadiusDp() {
        if (prefs() == null)
            return 16;
        switch (mSelectedSlot) {
            case DOCK:
                return Math.max(0, Math.min(40, surfaceEditorSliderValue(SurfaceSlot.DOCK,
                    SurfaceProperty.CORNER_RADIUS, prefs().getAppLauncherDockCornerRadius())));
            case STATUS:
                return Math.max(0, Math.min(40, surfaceEditorSliderValue(SurfaceSlot.STATUS,
                    SurfaceProperty.CORNER_RADIUS, prefs().getStatusBarCornerRadius())));
            case KEYBOARD:
                return 20;
            default:
                return Math.max(0, Math.min(40, prefs().getTerminalCornerRadius()));
        }
    }

    /**
     * An accent hairline inside a soft glow. Two strokes rather than one: a 2dp line alone
     * disappears against a busy wallpaper, and a thick one reads as a border the surface has grown.
     */
    @NonNull
    private Drawable buildSelectionRing() {
        int accent = mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
            R.color.termux_primary);
        float radiusPx = dpToPx(ringRadiusDp());
        // The keyboard is only rounded where it leaves the screen edge, so its ring is too.
        boolean topOnly = mSelectedSlot == SurfaceSlot.KEYBOARD;

        GradientDrawable glow = new GradientDrawable();
        glow.setColor(0);
        glow.setStroke(dp(6), withAlpha(accent, 36));
        GradientDrawable line = new GradientDrawable();
        line.setColor(0);
        line.setStroke(Math.max(1, dp(2)), accent);
        if (topOnly) {
            float[] corners = {radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0};
            glow.setCornerRadii(corners);
            line.setCornerRadii(corners);
        } else {
            glow.setCornerRadius(radiusPx);
            line.setCornerRadius(radiusPx);
        }
        LayerDrawable ring = new LayerDrawable(new Drawable[] {glow, line});
        ring.setLayerInset(1, dp(2), dp(2), dp(2), dp(2));
        return ring;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    // ---------------------------------------------------------------------- surfaces as controls

    /** The margin cell of a surface, or null where the surface has no margin — the canvas. */
    @Nullable
    private static SurfaceEditorRows.Row insetRow(@NonNull SurfaceSlot slot) {
        return SurfaceEditorRows.forCell(slot, SurfaceProperty.SIDE_GAP);
    }

    /**
     * Whether a surface has a margin to drag at all.
     *
     * <p>Two separate reasons it might not, and both have to hold: the canvas owns no margin cell —
     * it is the room the others are inset from — and a docked surface is flush with the screen edges
     * by definition, so there is no gap to walk even where the cell exists.
     */
    private boolean canDragMargin(@NonNull SurfaceSlot slot) {
        return insetRow(slot) != null && mHost.isFloatingDock();
    }

    private int surfaceTuningInsetDp(@NonNull SurfaceSlot slot) {
        SurfaceEditorRows.Row row = insetRow(slot);
        if (prefs() == null || row == null)
            return TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET;
        return row.read.applyAsInt(prefs());
    }

    private void setSurfaceTuningInsetDp(@NonNull SurfaceSlot slot, int insetDp) {
        SurfaceEditorRows.Row row = insetRow(slot);
        if (prefs() == null || row == null)
            return;
        detachSurfaceRowForEdit(slot, SurfaceProperty.SIDE_GAP);
        row.write.accept(prefs(), insetDp);
        applySurfaceEditorStructuralPreview();
        syncPill();
    }

    /**
     * A touch on a surface, which is both gestures the editor puts on the surfaces themselves: a
     * tap selects it, and a horizontal drag walks its symmetric screen-edge margin.
     *
     * <p>The two cannot be told apart on the way down, so the down is always consumed and the
     * decision is made by how far the finger travelled. Not every surface has a margin to walk —
     * the canvas owns no margin cell at all, and a docked surface is flush with the screen edges by
     * definition — but all four are selectable, so the drag half does nothing there rather than the
     * whole listener falling through. Nothing on this path may reach for a cell without checking it
     * exists: this is a touch handler on the home screen, and it once did.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void bindSurfaceTouch(int groupId, @NonNull SurfaceSlot slot) {
        View group = mHost.findView(groupId);
        if (group == null)
            return;
        final int slop = ViewConfiguration.get(mHost.context()).getScaledTouchSlop();
        // The touch listener consumes the gesture, so onTouchEvent never runs and no click is
        // synthesised. The listener calls performClick() for a tap instead, which is also the node
        // TalkBack activates — without this the surfaces would be selectable by finger only.
        group.setContentDescription(getString(R.string.termux_surface_editor_select_description,
            getString(SurfaceEditorRows.slotLabel(slot))));
        group.setOnClickListener(view -> {
            if (mSelectedSlot != slot)
                selectSurface(slot, true);
        });
        group.setOnTouchListener((view, event) -> {
            if (!mSurfaceEditorOpen || prefs() == null)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mSurfaceTuningInsetDragStartX = event.getRawX();
                    mSurfaceTuningInsetDragStartY = event.getRawY();
                    mSurfaceTuningInsetDragStartDp = surfaceTuningInsetDp(slot);
                    mSurfaceTuningDragMoved = false;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float travelX = event.getRawX() - mSurfaceTuningInsetDragStartX;
                    float travelY = event.getRawY() - mSurfaceTuningInsetDragStartY;
                    if (!mSurfaceTuningDragMoved
                        && Math.max(Math.abs(travelX), Math.abs(travelY)) > slop) {
                        mSurfaceTuningDragMoved = true;
                        // A margin drag is the one gesture whose surface the pill may be covering.
                        if (canDragMargin(slot))
                            setPillPeek(true);
                    }
                    if (!mSurfaceTuningDragMoved || !canDragMargin(slot))
                        return true;
                    int insetDp = TermuxAppSharedPreferences.clampSurfaceHorizontalInset(
                        Math.round(mSurfaceTuningInsetDragStartDp
                            + (pxToDp(travelX) * SURFACE_TUNING_INSET_DRAG_GAIN)));
                    if (insetDp != surfaceTuningInsetDp(slot)) {
                        if (mSelectedSlot != slot)
                            selectSurface(slot, true);
                        setSurfaceTuningInsetDp(slot, insetDp);
                    }
                    setSurfaceTuningPeekReadout(
                        getString(R.string.termux_surface_tuning_edges),
                        getString(R.string.termux_dock_tuning_value_dp, insetDp));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    setPillPeek(false);
                    // Tapping the surface already selected does nothing, deliberately: the click
                    // listener is the one place that decides, so finger and TalkBack agree.
                    if (!mSurfaceTuningDragMoved
                        && event.getActionMasked() == MotionEvent.ACTION_UP)
                        view.performClick();
                    mSurfaceTuningDragMoved = false;
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
            if (!mSurfaceEditorOpen || prefs() == null)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mSurfaceTuningDockHeightDragStartY = event.getRawY();
                    mSurfaceTuningDockHeightDragStartScale =
                        prefs().getAppLauncherBarHeightScale();
                    setPillPeek(true);
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
                        // Preview only while the finger is down; the terminal resize settles once
                        // on release rather than reflowing the shell on every travelled pixel.
                        requestSurfaceEditorPreview(SurfaceEditorProperties.PREVIEW_ALL);
                    }
                    setSurfaceTuningPeekReadout(
                        getString(R.string.termux_surface_tuning_peek_dock_size),
                        dockSizePresetLabel(DockLayoutPolicy.nearestSizePresetIndex(scale)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    requestSurfaceEditorPreview(
                        SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT);
                    setPillPeek(false);
                    syncPill();
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
            if (!mSurfaceEditorOpen || prefs() == null || keyboard() == null)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mInAppKeyboardHeightDragStartY = event.getRawY();
                    mInAppKeyboardHeightDragStartScale = keyboard().getHeightScale();
                    int renderedHeight = mHost.attachedInAppKeyboardView() == null
                        ? 0 : mHost.attachedInAppKeyboardView().getMeasuredHeight();
                    mInAppKeyboardUnscaledDragHeight = Math.max(1f,
                        renderedHeight / Math.max(0.01f, mInAppKeyboardHeightDragStartScale));
                    setPillPeek(true);
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float scale = TermuxInAppKeyboard.calculateHeightScaleForDrag(
                        mInAppKeyboardHeightDragStartScale,
                        event.getRawY() - mInAppKeyboardHeightDragStartY,
                        mInAppKeyboardUnscaledDragHeight);
                    keyboard().previewSurfaceEditorHeightScale(scale);
                    setSurfaceTuningPeekReadout(
                        getString(R.string.termux_surface_tuning_peek_keyboard_height),
                        getString(R.string.termux_dock_tuning_value_percent,
                            keyboardEditorProgress(keyboard().getHeightScale(),
                                TermuxPreferenceConstants.TERMUX_APP
                                    .MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                                TermuxPreferenceConstants.TERMUX_APP
                                    .MAX_IN_APP_KEYBOARD_HEIGHT_SCALE)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    prefs().setInAppKeyboardHeightScale(keyboard().getHeightScale());
                    updateSurfaceEditorDirtyBadge();
                    setPillPeek(false);
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void bindSurfaceTuningGestures() {
        bindSurfaceTouch(R.id.surface_tuning_dock_gesture_group, SurfaceSlot.DOCK);
        bindSurfaceTouch(R.id.surface_tuning_keyboard_gesture_group, SurfaceSlot.KEYBOARD);
        bindSurfaceTouch(R.id.surface_tuning_status_gesture_group, SurfaceSlot.STATUS);
        bindSurfaceTouch(R.id.surface_tuning_canvas_gesture_group, SurfaceSlot.CANVAS);
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

    private Interpolator surfaceTuningFadeInterpolator() {
        return Motion.settle();
    }

    private void positionSurfaceTuningGestureTargets() {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null || !mSurfaceEditorOpen || overlay.getWidth() <= 0)
            return;
        View statusSurface = mHost.findView(R.id.terminal_window_bar_host);
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_status_gesture_group, overlay,
            statusSurface);
        resizeStatusTuningPills(statusSurface);
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_dock_gesture_group, overlay,
            mHost.findView(R.id.accessory_surface_host));
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_keyboard_gesture_group, overlay,
            mHost.isInAppKeyboardShown() ? mHost.findView(R.id.inapp_keyboard_view_host) : null);
        positionCanvasGestureGroup(overlay);
        // Docked surfaces are flush with the screen edges: the margin drag is inert there, so the
        // side pills advertising it must not render either.
        boolean sideDrag = mHost.isFloatingDock();
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_status_pill_left, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_status_pill_right, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_dock_pill_left, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_dock_pill_right, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_keyboard_pill_left, sideDrag);
        setSurfaceTuningSidePillVisible(R.id.surface_tuning_keyboard_pill_right, sideDrag);
        positionSelectionRing(false);
    }

    /** The canvas takes the whole free region, so a tap on the terminal selects the terminal. */
    private void positionCanvasGestureGroup(@NonNull View overlay) {
        View group = mHost.findView(R.id.surface_tuning_canvas_gesture_group);
        if (group == null)
            return;
        int[] region = pillRegion();
        int height = region[1] - region[0];
        if (height <= 0) {
            group.setVisibility(View.GONE);
            return;
        }
        ViewGroup.LayoutParams params = group.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            if (margins.topMargin != region[0] || margins.height != height) {
                margins.topMargin = region[0];
                margins.height = height;
                group.setLayoutParams(margins);
            }
        }
        group.setVisibility(View.VISIBLE);
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
        int top = Math.max(0, surfaceTop - dp(SURFACE_TUNING_HANDLE_OVERHANG_DP));
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
        int[] pillIds = {R.id.surface_tuning_status_pill_left,
            R.id.surface_tuning_status_pill_right};
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

    private void registerSurfaceEditorLayoutListener(@NonNull View host) {
        if (mSurfaceEditorLayoutListener != null)
            return;
        mSurfaceEditorAnchorSignature = Long.MIN_VALUE;
        mSurfaceEditorLayoutListener = () -> {
            // Global layout fires for every text change a slider tick causes; the pill and the
            // gesture overlay only care when one of their anchors — the stack, the status inset, the
            // window bar, a surface host — actually moved. Anything else is skipped whole,
            // including the walk that would re-derive the same geometry.
            long signature = computeSurfaceEditorAnchorSignature();
            if (signature == mSurfaceEditorAnchorSignature)
                return;
            mSurfaceEditorAnchorSignature = signature;
            positionSurfaceTuningGestureTargets();
            applyLayoutMode(false);
            parkPill(false);
        };
        host.getViewTreeObserver().addOnGlobalLayoutListener(mSurfaceEditorLayoutListener);
    }

    /** Everything the pill's placement and the gesture-target positions read, folded to one number. */
    private long computeSurfaceEditorAnchorSignature() {
        View stack = mHost.findView(R.id.accessory_stack_container);
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        View host = mPill == null ? null : mPill.host;
        int parentHeight = host != null && host.getParent() instanceof View
            ? ((View) host.getParent()).getHeight() : 0;
        long signature = mHost.statusBarInsetTop();
        signature = mixAnchor(signature, parentHeight);
        signature = mixAnchor(signature,
            stack != null ? surfaceEditorStackTopPx(stack, parentHeight) : -1);
        signature = mixAnchor(signature, overlay != null ? overlay.getWidth() : -1);
        signature = mixAnchor(signature, mPill == null ? -1 : mPill.root.getHeight());
        signature = mixAnchor(signature,
            anchorRectSignature(mHost.findView(R.id.terminal_window_bar_host)));
        signature = mixAnchor(signature,
            anchorRectSignature(mHost.findView(R.id.accessory_surface_host)));
        signature = mixAnchor(signature, anchorRectSignature(mHost.isInAppKeyboardShown()
            ? mHost.findView(R.id.inapp_keyboard_view_host) : null));
        signature = mixAnchor(signature, mHost.isFloatingDock() ? 1 : 0);
        return mixAnchor(signature, mSelectedSlot.ordinal());
    }

    private long anchorRectSignature(@Nullable View view) {
        if (view == null || view.getVisibility() != View.VISIBLE)
            return -1;
        view.getLocationInWindow(mTmpAnchorLocation);
        long signature = mTmpAnchorLocation[0];
        signature = mixAnchor(signature, mTmpAnchorLocation[1]);
        signature = mixAnchor(signature, view.getWidth());
        return mixAnchor(signature, view.getHeight());
    }

    private static long mixAnchor(long signature, long value) {
        return signature * 1_000_003L + value;
    }

    private void unregisterSurfaceEditorLayoutListener() {
        if (mSurfaceEditorLayoutListener == null)
            return;
        if (mPill != null)
            mPill.host.getViewTreeObserver()
                .removeOnGlobalLayoutListener(mSurfaceEditorLayoutListener);
        mSurfaceEditorLayoutListener = null;
    }

    // ------------------------------------------------------------------------------- the pickers

    /** The one-row shape's ▾, which does the chip row's job when there is no room for chips. */
    private void showPropertyPicker() {
        final List<Control> controls = new ArrayList<>();
        for (Control control : SurfaceEditorProperties.chips(mSelectedSlot)) {
            if (isAvailable(mSelectedSlot, control))
                controls.add(control);
        }
        for (Control control : SurfaceEditorProperties.more(mSelectedSlot)) {
            if (isAvailable(mSelectedSlot, control))
                controls.add(control);
        }
        CharSequence[] names = new CharSequence[controls.size()];
        for (int i = 0; i < controls.size(); i++)
            names[i] = controlLabel(controls.get(i));
        new MaterialAlertDialogBuilder(mHost.context())
            .setTitle(R.string.termux_surface_editor_property_picker_title)
            .setItems(names, (dialog, which) -> {
                if (which < 0 || which >= controls.size())
                    return;
                Control picked = controls.get(which);
                if (picked.kind == Kind.PICKER) {
                    openPicker(picked);
                    return;
                }
                openControl(picked.id, true);
            })
            .show();
    }

    // --------------------------------------------------------------------------------- the panel
    //
    // The card's one expansion, where everything that once opened as a dialog window over the pill
    // now unfolds in place: the rest of one surface's own controls (⋯), the raw triple behind its
    // Look (Fine), the complete looks with the shared layer under them, and the surface list. One
    // panel at a time, toggled by the control that owns it, height-capped to the room the region
    // leaves so the card never grows past the surfaces it is editing — taller content scrolls
    // inside it. Everything in it writes live exactly like the pill's own row.

    private static final int PANEL_NONE = 0;
    private static final int PANEL_LOOKS = 1;
    private static final int PANEL_MORE = 2;
    private static final int PANEL_FINE = 3;
    private static final int PANEL_SURFACES = 4;

    private int mOpenPanel = PANEL_NONE;
    @Nullable private ScrollView mPanelScroller;
    @Nullable private LinearLayout mPanelContent;
    private int mPanelMaxHeightPx;

    private void togglePanel(int which) {
        if (mOpenPanel == which)
            closePanel();
        else
            openPanel(which);
    }

    private void openPanel(int which) {
        Pill pill = mPill;
        if (pill == null || prefs() == null || mLayoutMode == Mode.ONE_ROW)
            return;
        ensurePanelViews(pill);
        LinearLayout content = mPanelContent;
        if (content == null)
            return;
        content.removeAllViews();
        mPresetItems.clear();
        Context context = mHost.context();
        List<Runnable> syncs = new ArrayList<>();
        switch (which) {
            case PANEL_LOOKS:
                buildLooksPanel(context, content, syncs);
                break;
            case PANEL_MORE: {
                List<Control> controls = SurfaceEditorProperties.more(mSelectedSlot);
                if (controls.isEmpty()) {
                    closePanel();
                    return;
                }
                for (Control control : controls)
                    addControlRow(context, content, control, mSelectedSlot, syncs);
                break;
            }
            case PANEL_FINE: {
                List<Control> controls = SurfaceEditorProperties.fine(mSelectedSlot);
                if (controls.isEmpty()) {
                    closePanel();
                    return;
                }
                for (Control control : controls)
                    addControlRow(context, content, control, mSelectedSlot, syncs);
                break;
            }
            case PANEL_SURFACES:
                buildSurfacesPanel(context, content);
                break;
            default:
                closePanel();
                return;
        }
        mOpenPanel = which;
        mPanelSyncs = syncs;
        mPanelMaxHeightPx = panelMaxHeightPx(pill);
        if (mPanelScroller != null)
            mPanelScroller.scrollTo(0, 0);
        pill.panel.setVisibility(View.VISIBLE);
        syncOpenPanel();
    }

    private void closePanel() {
        mOpenPanel = PANEL_NONE;
        mPanelSyncs = null;
        mPresetItems.clear();
        if (mPanelContent != null)
            mPanelContent.removeAllViews();
        if (mPill != null && mPill.panel.getVisibility() != View.GONE)
            mPill.panel.setVisibility(View.GONE);
    }

    /** The panel's one scroller, created on first use: wrap up to the cap, then scroll inside. */
    private void ensurePanelViews(@NonNull Pill pill) {
        if (mPanelScroller != null)
            return;
        Context context = mHost.context();
        mPanelScroller = new ScrollView(context) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                    Math.max(dp(96), mPanelMaxHeightPx), View.MeasureSpec.AT_MOST));
            }
        };
        mPanelScroller.setVerticalScrollBarEnabled(false);
        mPanelContent = new LinearLayout(context);
        mPanelContent.setOrientation(LinearLayout.VERTICAL);
        mPanelScroller.addView(mPanelContent, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pill.panel.addView(mPanelScroller, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * How tall the panel may grow: the room the region has left after the card's standing rows,
     * never less than a usable strip. The pill re-parks itself as the card's height changes, so
     * the cap is what keeps the grown card from being pinned over the surface it is editing.
     */
    private int panelMaxHeightPx(@NonNull Pill pill) {
        int[] region = pillRegion();
        int openPanelPx = pill.panel.getVisibility() == View.VISIBLE && mPanelScroller != null
            ? mPanelScroller.getHeight() : 0;
        int basePx = Math.max(0, pill.root.getHeight() - openPanelPx);
        int available = (region[1] - region[0]) - basePx
            - dp(2 * SURFACE_EDITOR_STANDOFF_DP);
        return Math.max(dp(120), Math.min(dp(340), available));
    }

    /**
     * The title's ▾: the same four surfaces touching them selects, for reachability and for a
     * surface that is not on screen to be picked — the keyboard while it is down.
     */
    private void buildSurfacesPanel(@NonNull Context context, @NonNull ViewGroup into) {
        ChipGroup group = new ChipGroup(context);
        group.setChipSpacingHorizontal(dp(6));
        group.setChipSpacingVertical(dp(2));
        LayoutInflater inflater = LayoutInflater.from(context);
        for (SurfaceEditorRows.Page page : SurfaceEditorRows.pages()) {
            Chip chip = (Chip) inflater.inflate(
                R.layout.surface_editor_pill_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(page.labelRes);
            chip.setChecked(page.slot == mSelectedSlot);
            final SurfaceSlot slot = page.slot;
            chip.setOnClickListener(view -> {
                closePanel();
                if (slot != mSelectedSlot)
                    selectSurface(slot, true);
            });
            group.addView(chip);
        }
        into.addView(group);
    }

    /** The open panel's row restatements, so a live write squares its own rows up. */
    @Nullable private List<Runnable> mPanelSyncs;

    private void syncOpenPanel() {
        if (mPanelSyncs == null)
            return;
        for (Runnable sync : mPanelSyncs)
            sync.run();
    }

    /**
     * One row in the panel: label, its control, its number, and its link back to Base where it has
     * one. The shared row layout, so a panel row and the pill's own row read as the same control.
     */
    private void addControlRow(@NonNull Context context, @NonNull ViewGroup into,
                               @NonNull Control control, @Nullable SurfaceSlot slot,
                               @NonNull List<Runnable> syncs) {
        View rowView = LayoutInflater.from(context)
            .inflate(R.layout.surface_editor_row, into, false);
        TextView label = rowView.findViewById(R.id.surface_editor_row_label);
        SeekBar slider = rowView.findViewById(R.id.surface_editor_row_slider);
        TextView value = rowView.findViewById(R.id.surface_editor_row_value);
        TextView chip = rowView.findViewById(R.id.surface_editor_row_chip);
        label.setText(controlLabel(control));

        if (control.kind == Kind.PICKER) {
            slider.setVisibility(View.GONE);
            value.setVisibility(View.GONE);
            chip.setText(R.string.termux_surface_tuning_clock_chevron);
            chip.setTextColor(mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
                R.color.termux_primary));
            rowView.setClickable(true);
            rowView.setFocusable(true);
            into.addView(rowView);
            if (SurfaceEditorProperties.ID_KEYBOARD_COLORS.equals(control.id)) {
                // Leaves the editor for its own screen, so the panel does not stay behind it.
                rowView.setOnClickListener(view -> {
                    closePanel();
                    mHost.openKeyboardColors();
                });
                return;
            }
            if (!SurfaceEditorProperties.ID_CLOCK.equals(control.id))
                return;
            // The clock row draws the live face rather than naming it; the editor collapses the
            // status pane on entry, so this is the only place the choice can be seen while it is
            // being made. The row unfolds the six faces right under itself — in the card, like
            // everything else.
            TerminalClockWidget preview = new TerminalClockWidget(context, null);
            preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ((ViewGroup) rowView).addView(preview, 1,
                new LinearLayout.LayoutParams(0, dp(28), 1f));
            Runnable sync = () -> {
                if (prefs() == null)
                    return;
                applyClockPreview(preview, prefs().getTopPaneClockStyle());
                rowView.setContentDescription(getString(
                    R.string.termux_surface_tuning_clock_row_description,
                    getString(clockStyleLabel(prefs().getTopPaneClockStyle()))));
            };
            syncs.add(sync);
            sync.run();
            LinearLayout faces = new LinearLayout(context);
            faces.setOrientation(LinearLayout.VERTICAL);
            faces.setVisibility(View.GONE);
            into.addView(faces);
            rowView.setOnClickListener(view -> {
                if (faces.getVisibility() == View.VISIBLE) {
                    faces.setVisibility(View.GONE);
                    return;
                }
                buildClockFaces(context, faces);
                faces.setVisibility(View.VISIBLE);
            });
            return;
        }

        slider.setMax(control.max);
        Runnable sync = () -> {
            if (prefs() == null)
                return;
            // A row the state makes inert leaves the panel rather than rendering dead; it comes
            // back through the same sync when the state that hid it changes.
            boolean available = isAvailable(slot, control);
            if ((rowView.getVisibility() == View.VISIBLE) != available)
                rowView.setVisibility(available ? View.VISIBLE : View.GONE);
            if (!available)
                return;
            int shown = slot == null ? sheetValueOf(control) : shownValueOf(slot, control);
            if (slider.getProgress() != shown)
                slider.setProgress(shown);
            value.setText(valueText(control, shown));
            if (slot == null || control.cell == null) {
                chip.setVisibility(View.INVISIBLE);
                return;
            }
            boolean own = hasOwnValue(slot, control);
            chip.setVisibility(View.VISIBLE);
            chip.setClickable(own);
            chip.setFocusable(own);
            chip.setText(own ? R.string.termux_surface_tuning_link_detached
                : R.string.termux_surface_tuning_link_inherited);
            chip.setTextColor(own
                ? mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
                    R.color.termux_primary)
                : mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                    R.color.termux_on_surface_variant));
            chip.setAlpha(own ? 1f : 0.6f);
            chip.setContentDescription(getString(own
                    ? R.string.termux_surface_tuning_link_detached_description
                    : R.string.termux_surface_tuning_link_inherited_description,
                getString(SurfaceEditorRows.slotLabel(slot))));
        };
        chip.setOnClickListener(view -> {
            if (prefs() == null || slot == null || control.cell == null
                || prefs().isSurfaceInheriting(slot, control.cell.property))
                return;
            prefs().setSurfaceInheriting(slot, control.cell.property, true);
            applySurfaceEditorStructuralPreview();
            syncOpenPanel();
            syncPill();
        });
        slider.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override void onSliderChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText(valueText(control, progress));
                if (!fromUser)
                    return;
                if (slot == null)
                    writeSheetControl(control, progress);
                else
                    writeControl(slot, control, progress);
                syncPill();
            }
        });
        into.addView(rowView);
        syncs.add(sync);
        sync.run();
    }

    /** A Base row's own value, resolved and capped like a surface row's. */
    private int sheetValueOf(@NonNull Control control) {
        if (prefs() == null)
            return 0;
        int value = SurfaceEditorProperties.ID_BASE_INTENSITY.equals(control.id)
            ? prefs().getSurfaceMaterialIntensity()
            : surfaceEditorSliderValue(null,
                SurfaceEditorProperties.ID_BASE_CORNERS.equals(control.id)
                    ? SurfaceProperty.CORNER_RADIUS : SurfaceProperty.SIDE_GAP,
                control.read(prefs()));
        return Math.max(0, Math.min(control.max, value));
    }

    private void writeSheetControl(@NonNull Control control, int value) {
        if (prefs() == null)
            return;
        if (SurfaceEditorProperties.ID_BASE_INTENSITY.equals(control.id)) {
            applyBaseMacro(prefs().getSurfaceMaterial(), value);
            return;
        }
        if (SurfaceEditorProperties.ID_BASE_GAP.equals(control.id) && !mHost.isFloatingDock())
            return;
        control.write(prefs(), value);
        requestSurfaceEditorPreview(control.previewScopes);
    }

    /** The two rows that open something which picks for itself. */
    private void openPicker(@NonNull Control control) {
        if (SurfaceEditorProperties.ID_KEYBOARD_COLORS.equals(control.id)) {
            mHost.openKeyboardColors();
            return;
        }
        if (SurfaceEditorProperties.ID_CLOCK.equals(control.id))
            showClockFacePicker();
    }

    // -------------------------------------------------------------------------- the Looks panel

    /** Complete looks, the shared layer behind them, and Reset, unfolded into the card's panel. */
    private void buildLooksPanel(@NonNull Context context, @NonNull ViewGroup into,
                                 @NonNull List<Runnable> syncs) {
        View content = LayoutInflater.from(context)
            .inflate(R.layout.surface_editor_panel_looks, into, false);
        ViewGroup presets = content.findViewById(R.id.surface_editor_looks_presets);
        ViewGroup rows = content.findViewById(R.id.surface_editor_looks_rows);
        MaterialButtonToggleGroup material =
            content.findViewById(R.id.surface_editor_looks_material);
        TextView save = content.findViewById(R.id.surface_editor_looks_save);
        TextView reset = content.findViewById(R.id.surface_editor_looks_reset);
        if (presets == null || rows == null || material == null)
            return;
        if (save != null)
            setLeadingIcon(save, R.drawable.ic_symbol_save);
        if (reset != null)
            setLeadingIcon(reset, R.drawable.ic_symbol_restart);
        into.addView(content);

        buildPresetsStrip(context, presets);
        for (Control control : SurfaceEditorProperties.base())
            addControlRow(context, rows, control, null, syncs);
        syncs.add(() -> {
            syncBaseMaterialGroup(material);
            syncPresetSelection();
        });
        material.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mSyncingMaterialMacro || prefs() == null)
                return;
            applyBaseMacro(materialForButton(checkedId,
                    R.id.surface_editor_looks_material_solid,
                    R.id.surface_editor_looks_material_frost),
                prefs().getSurfaceMaterialIntensity());
            syncOpenPanel();
            syncPill();
        });
        if (save != null) {
            save.setOnClickListener(view -> {
                if (prefs() == null)
                    return;
                SurfacePresets.saveCustom(prefs());
                refreshPresetPreviews();
                syncPresetSelection();
                AppNotice.success(mHost.context(), getString(R.string.termux_surface_preset_saved));
            });
        }
        if (reset != null) {
            reset.setOnClickListener(view -> {
                resetEverything();
                syncOpenPanel();
                syncPill();
            });
        }
        refreshPresetPreviews();
    }

    private void syncBaseMaterialGroup(@NonNull MaterialButtonToggleGroup group) {
        if (prefs() == null)
            return;
        String material = prefs().getSurfaceMaterial();
        int storedIntensity = prefs().getSurfaceMaterialIntensity();
        int[] expected = SurfaceMaterials.triple(material, storedIntensity);
        boolean matches =
            expected[SurfaceMaterials.BLUR] == prefs().getSurfaceBaseValue(SurfaceProperty.BLUR)
                && expected[SurfaceMaterials.OPACITY]
                    == prefs().getSurfaceBaseValue(SurfaceProperty.OPACITY)
                && expected[SurfaceMaterials.GRAIN]
                    == prefs().getSurfaceBaseValue(SurfaceProperty.GRAIN);
        mSyncingMaterialMacro = true;
        try {
            if (matches) {
                int buttonId = materialButtonId(material,
                    R.id.surface_editor_looks_material_solid,
                    R.id.surface_editor_looks_material_glass,
                    R.id.surface_editor_looks_material_frost);
                if (group.getCheckedButtonId() != buttonId)
                    group.check(buttonId);
            } else if (group.getCheckedButtonId() != View.NO_ID) {
                // A triple something else wrote — an upgrade's hand-tuned numbers, a raw value
                // restored by Discard — deselects the family instead of lying about it.
                group.clearChecked();
            }
        } finally {
            mSyncingMaterialMacro = false;
        }
    }


    /**
     * One page, one reset: shipped defaults for everything the editor owns. Every surface goes back
     * on Base first, then Base itself takes the shipped numbers — the fresh-install state — so no
     * legacy per-surface key needs writing at all: an attached link never reads its raw key, and
     * writing one through the link would move Base twice.
     */
    private void resetEverything() {
        if (prefs() == null)
            return;
        for (SurfaceSlot slot : SurfaceSlot.values())
            prefs().reattachSurface(slot);
        prefs().setSurfaceBaseValue(SurfaceProperty.BLUR,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR);
        prefs().setSurfaceBaseValue(SurfaceProperty.OPACITY,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY);
        prefs().setSurfaceBaseValue(SurfaceProperty.GRAIN,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN);
        prefs().setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS);
        prefs().setSurfaceBaseValue(SurfaceProperty.SIDE_GAP,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP);
        // The shipped triple above is glass at 50, so the macro keys agree with it by taking their
        // own defaults.
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
        prefs().setWallpaperBackdropDim(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM);
        // The clock face is a look the editor owns, so one page, one reset covers it too.
        prefs().setTopPaneClockStyle(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE);
        prefs().setStatusIndicatorCornerRadius(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_INDICATOR_CORNER_RADIUS);
        prefs().setTerminalPaneGap(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP);
        mHost.refreshPaneLayout();
        mHost.applyTerminalSurfaceAppearance();
        mHost.refreshTerminalWindowBar();
        applySurfaceEditorStructuralPreview();
    }

    // ------------------------------------------------------------------------------- the presets
    //
    // Complete looks, one tap each. A preset overwrites everything it defines, detached overrides
    // included (that is what "complete" means), against one Undo that puts back the exact raw values
    // and link shape it found. Each card is a mini device mock drawn from the preset's own numbers,
    // wearing the live glass recipe, and the card whose values exactly match the live preferences
    // wears a ring.

    /** Preview frame and name per preset id, for the selection ring. */
    private final Map<String, Pair<View, TextView>> mPresetItems = new LinkedHashMap<>();

    private void buildPresetsStrip(@NonNull Context context, @NonNull ViewGroup container) {
        container.removeAllViews();
        mPresetItems.clear();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        android.widget.HorizontalScrollView strip = new android.widget.HorizontalScrollView(context);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(2));
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            addPresetCard(context, row, preset.id, preset.nameRes, () -> applyPreset(preset));
        // Custom is last and always present, saved or not: an empty slot that says where a saved
        // look would go is what makes saving one discoverable.
        addPresetCard(context, row, SurfacePresets.CUSTOM_ID,
            R.string.termux_surface_preset_custom, this::applyCustomPreset);
        strip.addView(row);
        column.addView(strip);
        container.addView(column);
    }

    /** One card in the strip: the mock, its name, and the node that applies the look. */
    private void addPresetCard(@NonNull Context context, @NonNull ViewGroup row,
                               @NonNull String id, @StringRes int nameRes,
                               @NonNull Runnable onApply) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.rightMargin = dp(10);
        item.setLayoutParams(itemParams);

        View preview = new View(context);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
            dp(SurfaceEditorPresetPreview.CARD_WIDTH_DP),
            dp(SurfaceEditorPresetPreview.CARD_HEIGHT_DP)));
        float cardCornerPx = dpToPx(SurfaceEditorPresetPreview.CARD_CORNER_DP);
        preview.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardCornerPx);
            }
        });
        preview.setClipToOutline(true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(preview);

        TextView name = new TextView(context);
        name.setText(nameRes);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(3);
        name.setLayoutParams(nameParams);
        item.addView(name);

        // The card is the control that applies the look, so it carries the full accessibility node:
        // a name, a button role and a spoken click action. A preview draws no text TalkBack could
        // read on its own.
        item.setContentDescription(getString(
            R.string.termux_surface_preset_card_description, getString(nameRes)));
        item.setFocusable(true);
        androidx.core.view.ViewCompat.setAccessibilityDelegate(item,
            new androidx.core.view.AccessibilityDelegateCompat() {
                @Override public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                        @NonNull androidx.core.view.accessibility
                            .AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(Button.class.getName());
                    info.addAction(new androidx.core.view.accessibility
                        .AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            androidx.core.view.accessibility.AccessibilityNodeInfoCompat
                                .ACTION_CLICK,
                            getString(R.string.termux_surface_preset_apply_action)));
                }
            });
        item.setOnClickListener(view -> onApply.run());
        row.addView(item);
        mPresetItems.put(id, Pair.create(preview, name));
    }

    @Nullable
    private SurfacePresets.Preset customPreset() {
        return prefs() == null ? null : SurfacePresets.custom(prefs());
    }

    /** The Custom card applies the saved look, or explains where a saved look comes from. */
    private void applyCustomPreset() {
        SurfacePresets.Preset custom = customPreset();
        if (custom == null) {
            AppNotice.show(mHost.context(), AppNoticeItem.Kind.INFO,
                getString(R.string.termux_surface_editor_save_look),
                getString(R.string.termux_surface_preset_custom_empty),
                getString(R.string.termux_surface_preset_custom_empty_hint), true);
            return;
        }
        applyPreset(custom);
    }

    /** An empty Custom slot: the card's outline, dashed, with nothing wearing it yet. */
    @NonNull
    private Drawable buildEmptyPresetCard() {
        GradientDrawable empty = new GradientDrawable();
        empty.setColor(withAlpha(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant), 20));
        empty.setCornerRadius(dpToPx(SurfaceEditorPresetPreview.CARD_CORNER_DP));
        empty.setStroke(Math.max(1, dp(1)),
            withAlpha(mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                R.color.termux_on_surface_variant), 110), dpToPx(3), dpToPx(3));
        return empty;
    }

    /** Re-renders every preset card against the current wallpaper and theme. */
    private void refreshPresetPreviews() {
        if (mPresetItems.isEmpty())
            return;
        int widthPx = dp(SurfaceEditorPresetPreview.CARD_WIDTH_DP);
        int heightPx = dp(SurfaceEditorPresetPreview.CARD_HEIGHT_DP);
        // One thumb shared by every card: the wallpaper is the same behind all five looks.
        Bitmap thumb = mHost.wallpaperPreviewThumb(widthPx, heightPx);
        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            Pair<View, TextView> item = mPresetItems.get(preset.id);
            if (item != null)
                item.first.setBackground(buildPresetPreview(preset, thumb, widthPx, heightPx));
        }
        Pair<View, TextView> customItem = mPresetItems.get(SurfacePresets.CUSTOM_ID);
        if (customItem != null) {
            SurfacePresets.Preset custom = customPreset();
            customItem.first.setBackground(custom == null
                ? buildEmptyPresetCard()
                : buildPresetPreview(custom, thumb, widthPx, heightPx));
            customItem.first.setAlpha(custom == null ? 0.6f : 1f);
        }
    }

    /**
     * A mini device mock wearing the preset: the blurred wallpaper behind the terminal field, the
     * status pill and the dock/keyboard slab — the latter two rendered by the live glass recipe at
     * the preset's own opacity and grain, placed by {@link SurfaceEditorPresetPreview}. Docked runs
     * the slab flush to the card's edges; Floating pulls it in and rounds it, so the one decision
     * the presets disagree on most is the one the cards show most clearly.
     */
    @NonNull
    private Drawable buildPresetPreview(@NonNull SurfacePresets.Preset preset,
                                        @Nullable Bitmap wallpaperThumb, int widthPx,
                                        int heightPx) {
        int radiusDp = presetInt(preset,
            TermuxPreferenceConstants.TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS, 24);
        int opacity = presetInt(preset,
            TermuxPreferenceConstants.TERMUX_APP.KEY_SURFACE_BASE_OPACITY, 34);
        int grain = presetInt(preset,
            TermuxPreferenceConstants.TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 0);
        int sideGapDp = presetInt(preset,
            TermuxPreferenceConstants.TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP, 10);
        int terminalRadiusDp = presetInt(preset,
            TermuxPreferenceConstants.TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_CORNER_RADIUS);
        int paneGapDp = presetInt(preset,
            TermuxPreferenceConstants.TERMUX_APP.KEY_TERMINAL_PANE_GAP,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP);
        boolean floating = SegmentedPillPreference.VALUE_ROUNDED.equals(preset.values.get(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE));
        boolean border = Boolean.TRUE.equals(preset.values.get(
            TermuxPreferenceConstants.TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED));

        float density = dpToPx(1);

        Drawable wallpaper;
        if (wallpaperThumb != null && !wallpaperThumb.isRecycled()) {
            wallpaper = new BitmapDrawable(getResources(), wallpaperThumb);
        } else {
            wallpaper = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
                withAlpha(mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
                    R.color.termux_primary), 70),
                withAlpha(mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                    R.color.termux_on_surface_variant), 40)});
        }

        GradientDrawable terminal = new GradientDrawable();
        terminal.setColor(withAlpha(Color.BLACK, 120));
        terminal.setCornerRadius(
            SurfaceEditorPresetPreview.terminalRadiusPx(density, terminalRadiusDp));
        if (border) {
            terminal.setStroke(Math.max(1, Math.round(density)),
                withAlpha(mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                    R.color.termux_on_surface_variant), 90));
        }

        float glassRadiusPx =
            SurfaceEditorPresetPreview.surfaceRadiusPx(density, radiusDp, floating);
        Drawable status = mHost.presetGlassSurface(opacity / 100f, grain, glassRadiusPx, floating);
        Drawable slab = mHost.presetGlassSurface(opacity / 100f, grain, glassRadiusPx, floating);

        LayerDrawable layers = new LayerDrawable(
            new Drawable[] {wallpaper, terminal, status, slab});
        int[] terminalInsets = SurfaceEditorPresetPreview.terminalInsets(
            widthPx, heightPx, density, paneGapDp, terminalRadiusDp);
        int[] statusInsets = SurfaceEditorPresetPreview.statusInsets(
            widthPx, heightPx, density, sideGapDp);
        int[] slabInsets = SurfaceEditorPresetPreview.bottomSlabInsets(
            widthPx, heightPx, density, sideGapDp, floating);
        layers.setLayerInset(1,
            terminalInsets[0], terminalInsets[1], terminalInsets[2], terminalInsets[3]);
        layers.setLayerInset(2, statusInsets[0], statusInsets[1], statusInsets[2], statusInsets[3]);
        layers.setLayerInset(3, slabInsets[0], slabInsets[1], slabInsets[2], slabInsets[3]);
        return layers;
    }

    private static int presetInt(@NonNull SurfacePresets.Preset preset, @NonNull String key,
                                 int fallback) {
        Object value = preset.values.get(key);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    /** The ring follows whichever preset the live preferences exactly are — usually none. */
    private void syncPresetSelection() {
        if (prefs() == null || mPresetItems.isEmpty())
            return;
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            setPresetCardSelected(preset.id, SurfacePresets.matches(prefs(), preset));
        SurfacePresets.Preset custom = customPreset();
        setPresetCardSelected(SurfacePresets.CUSTOM_ID,
            custom != null && SurfacePresets.matches(prefs(), custom));
    }

    private void setPresetCardSelected(@NonNull String id, boolean selected) {
        Pair<View, TextView> item = mPresetItems.get(id);
        if (item == null)
            return;
        if (selected == Boolean.TRUE.equals(item.first.getTag()))
            return;
        item.first.setTag(selected);
        item.first.setForeground(selected ? buildPresetRing() : null);
        item.second.setTextColor(selected
            ? mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary)
            : mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                R.color.termux_on_surface_variant));
        // The ring is visual only; the card's node carries the same state for TalkBack.
        if (item.first.getParent() instanceof View) {
            View card = (View) item.first.getParent();
            card.setSelected(selected);
            androidx.core.view.ViewCompat.setStateDescription(card, selected
                ? getString(R.string.termux_surface_preset_current_look) : null);
        }
    }

    @NonNull
    private Drawable buildPresetRing() {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0);
        ring.setCornerRadius(dpToPx(9));
        ring.setStroke(dp(2),
            mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary));
        return ring;
    }

    private void applyPreset(@NonNull SurfacePresets.Preset preset) {
        if (prefs() == null)
            return;
        final Runnable undo = capturePresetUndo();
        SurfacePresets.apply(prefs(), preset);
        syncEditorAfterPresetWrite();
        // The confirmation goes to the app's own notice chip, not a snackbar: a snackbar lands
        // bottom-centre — on top of the dock, under the soft keyboard, into the display cutouts, in
        // Material's palette rather than this app's, with no swipe to get rid of it. The chip sits
        // in the top-trailing corner the rest of the app's notices use, and its tap is the Undo.
        AppNotice.undoable(mHost.context(),
            getString(R.string.termux_surface_preset_applied, getString(preset.nameRes)),
            getString(R.string.termux_surface_preset_undo_hint),
            () -> {
                undo.run();
                syncEditorAfterPresetWrite();
            });
    }

    /**
     * Everything a preset can write, captured for the one Undo. Raw values and the link signature
     * rather than resolved numbers, so the restore is exact: a surface that was detached at the same
     * number as Base comes back detached, not quietly folded in.
     */
    @NonNull
    private Runnable capturePresetUndo() {
        final String links = surfaceEditorLinkSignature();
        final SurfaceProperty[] properties = SurfaceProperty.values();
        final int[] base = new int[properties.length];
        for (SurfaceProperty property : properties)
            base[property.ordinal()] = prefs().getSurfaceBaseValue(property);
        final List<SurfaceEditorRows.Row> rows = SurfaceEditorRows.rows();
        final int[] raws = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++)
            raws[i] = prefs().getSurfaceOverrideValue(rows.get(i).slot, rows.get(i).property);
        final String material = prefs().getSurfaceMaterial();
        final int intensity = prefs().getSurfaceMaterialIntensity();
        final String dockStyle = prefs().getAppLauncherDockStyle();
        final boolean border = prefs().isTerminalBorderEnabled();
        final int terminalRadius = prefs().getTerminalCornerRadius();
        final int paneGap = prefs().getTerminalPaneGap();
        return () -> {
            if (prefs() == null)
                return;
            for (SurfaceProperty property : properties)
                prefs().setSurfaceBaseValue(property, base[property.ordinal()]);
            for (int i = 0; i < rows.size(); i++)
                prefs().setSurfaceRawValue(rows.get(i).slot, rows.get(i).property, raws[i]);
            restoreSurfaceEditorLinks(links);
            prefs().setSurfaceMaterial(material);
            prefs().setSurfaceMaterialIntensity(intensity);
            prefs().setAppLauncherDockStyle(dockStyle);
            prefs().setTerminalBorderEnabled(border);
            prefs().setTerminalCornerRadius(terminalRadius);
            prefs().setTerminalPaneGap(paneGap);
        };
    }

    /** Restates everything after a bulk write: a preset apply, its Undo, or Reset. */
    private void syncEditorAfterPresetWrite() {
        if (prefs() == null)
            return;
        mHost.refreshPaneLayout();
        mHost.refreshTerminalWindowBar();
        applySurfaceEditorStructuralPreview();
        syncOpenPanel();
        syncPill();
        positionSelectionRing(false);
        updateSurfaceEditorDirtyBadge();
    }

    // ------------------------------------------------------------------------- the clock face
    //
    // Status's other rows are numbers on a slider; the clock is a look, and looks pick badly from a
    // list of words. The picker draws all six as themselves, at the compact size the pane uses when
    // it is collapsed — which matters more than it sounds, because the editor collapses the status
    // pane out of its own way on entry, so these previews are the only place the choice can be seen
    // while it is being made.

    /** Package-private so a test can hold it against the settings list's own entry values. */
    static final String[] CLOCK_STYLES = {
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB};

    /** Same fallback the widget itself applies to an unknown stored value. */
    @StringRes
    static int clockStyleLabel(@Nullable String style) {
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(style))
            return R.string.termux_top_pane_clock_style_lcd;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(style))
            return R.string.termux_top_pane_clock_style_minimal;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED.equals(style))
            return R.string.termux_top_pane_clock_style_led;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE.equals(style))
            return R.string.termux_top_pane_clock_style_tape;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB.equals(style))
            return R.string.termux_top_pane_clock_style_slab;
        return R.string.termux_top_pane_clock_style_flip;
    }

    /**
     * A face at the size the collapsed pane draws it, honouring the 12-hour and lazy-mode
     * preferences so a preview never animates in a build where the real clock does not.
     */
    private void applyClockPreview(@NonNull TerminalClockWidget widget, @NonNull String style) {
        widget.setForm(TopPaneClockForm.COMPACT);
        widget.setStyle(style);
        if (prefs() == null)
            return;
        widget.setUseAmPm(prefs().isTopPaneClockAmPmEnabled());
        widget.setLazyMode(prefs().isLazyModeEnabled());
    }

    private void showClockFacePicker() {
        if (prefs() == null)
            return;
        Context context = mHost.context();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(6), dp(16), dp(6));
        // The app's dialog panel is translucent, and six faces of thin digits over whatever the
        // editor and the keyboard are drawing behind it is not a fair look at any of them. The list
        // carries its own solid field so every face is judged against the same ground.
        GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(dpToPx(14));
        field.setColor(mHost.themeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high));
        column.setBackground(field);
        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.addView(column);

        // Created before the rows so each row's tap can dismiss the sheet it lives in: a picked face
        // is applied and the picker is done, the way a dropdown behaves.
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_surface_tuning_clock_picker_title)
            .setView(scroller)
            .setNegativeButton(R.string.termux_dock_tuning_cancel, null)
            .create();

        String current = prefs().getTopPaneClockStyle();
        for (String style : CLOCK_STYLES) {
            final String picked = style;
            column.addView(clockFaceRow(context, style, current, () -> {
                pickClockStyle(picked);
                dialog.dismiss();
            }));
        }

        TextView elsewhere = new TextView(context);
        elsewhere.setText(R.string.termux_surface_tuning_clock_elsewhere);
        elsewhere.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        elsewhere.setTextColor(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant));
        elsewhere.setPadding(dp(4), dp(10), dp(4), 0);
        column.addView(elsewhere);

        dialog.show();
    }

    /** The clock row's inline face list, rebuilt on every open so the tick is always current. */
    private void buildClockFaces(@NonNull Context context, @NonNull LinearLayout faces) {
        if (prefs() == null)
            return;
        faces.removeAllViews();
        String current = prefs().getTopPaneClockStyle();
        for (String style : CLOCK_STYLES) {
            final String picked = style;
            faces.addView(clockFaceRow(context, style, current, () -> {
                pickClockStyle(picked);
                buildClockFaces(context, faces);
            }));
        }
        TextView elsewhere = new TextView(context);
        elsewhere.setText(R.string.termux_surface_tuning_clock_elsewhere);
        elsewhere.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        elsewhere.setTextColor(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant));
        elsewhere.setPadding(dp(4), dp(6), dp(4), dp(4));
        faces.addView(elsewhere);
    }

    /** One face in the picker: its name, the face itself, and a tick on the one in use. */
    @NonNull
    private View clockFaceRow(@NonNull Context context, @NonNull String style,
                              @NonNull String current,
                              @NonNull Runnable onPicked) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, ripple, true))
            row.setBackgroundResource(ripple.resourceId);
        boolean selected = style.equals(current);

        TextView name = new TextView(context);
        name.setText(clockStyleLabel(style));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        // Two lines, so a large font scale wraps "LED matrix" instead of clipping it — the column
        // stays a fixed width either way, which is what keeps the six faces vertically aligned.
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(selected
            ? mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary)
            : mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurface,
                R.color.termux_on_surface));
        name.setLayoutParams(new LinearLayout.LayoutParams(
            dp(84), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(name);

        TerminalClockWidget preview = new TerminalClockWidget(context, null);
        applyClockPreview(preview, style);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.setLayoutParams(new LinearLayout.LayoutParams(0, dp(30), 1f));
        row.addView(preview);

        TextView tick = new TextView(context);
        tick.setText(R.string.termux_surface_tuning_clock_selected);
        tick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tick.setGravity(Gravity.CENTER);
        tick.setTextColor(mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
            R.color.termux_primary));
        tick.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        tick.setLayoutParams(new LinearLayout.LayoutParams(
            dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(tick);

        // The row is the control, so it carries the node: the face's name, a button role and the
        // selected state — a preview draws no text TalkBack could read.
        row.setContentDescription(getString(R.string.termux_surface_tuning_clock_face_description,
            getString(clockStyleLabel(style))));
        row.setClickable(true);
        row.setFocusable(true);
        final boolean isSelected = selected;
        androidx.core.view.ViewCompat.setAccessibilityDelegate(row,
            new androidx.core.view.AccessibilityDelegateCompat() {
                @Override public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                        @NonNull androidx.core.view.accessibility
                            .AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(Button.class.getName());
                    info.setCheckable(true);
                    info.setChecked(isSelected);
                }
            });
        row.setOnClickListener(view -> onPicked.run());
        return row;
    }

    /** Live like every other editor control: written through, previewed, and gated by Done. */
    private void pickClockStyle(@NonNull String style) {
        if (prefs() == null || style.equals(prefs().getTopPaneClockStyle()))
            return;
        prefs().setTopPaneClockStyle(style);
        // One place re-reads face, alignment, 12-hour and lazy mode onto the live widget.
        mHost.refreshTerminalWindowBar();
        syncOpenPanel();
        updateSurfaceEditorDirtyBadge();
    }

    // -------------------------------------------------------------------------------- the peek
    //
    // Every slider peeks: the pill fades while the thumb is down, because what it is editing is
    // always a surface the pill may be crowding, and the value being changed is echoed over the
    // surface instead. Purely visual — the pill keeps its position and stays hit-testable, so the
    // finger already on the thumb goes on working.

    private void setPillPeek(boolean peek) {
        Pill pill = mPill;
        if (pill == null || !mSurfaceEditorOpen)
            return;
        pill.root.animate().cancel();
        // The whole drag happens behind a translucent pill, and the thumb invalidates it on every
        // moved pixel. A hardware layer for the duration turns each of those frames into a cached-
        // texture composite instead of an offscreen alpha pass over the full control tree.
        if (peek && pill.root.getLayerType() != View.LAYER_TYPE_HARDWARE)
            pill.root.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        pill.root.animate()
            .alpha(peek ? SURFACE_TUNING_PEEK_ALPHA : 1f)
            .setDuration(peek ? SURFACE_TUNING_PEEK_OUT_MS : SURFACE_TUNING_PEEK_IN_MS)
            .setInterpolator(Motion.settle())
            .withEndAction(() -> {
                if (!peek) pill.root.setLayerType(View.LAYER_TYPE_NONE, null);
            })
            .start();
        if (!peek) hideSurfaceTuningPeekReadout();
    }

    /** The faded pill's number, over the surface, so peeking does not trade one blindness for another. */
    private void setSurfaceTuningPeekReadout(@NonNull CharSequence label,
                                            @NonNull CharSequence value) {
        TextView readout = mHost.findView(R.id.surface_tuning_peek_readout);
        if (readout == null || !mSurfaceEditorOpen)
            return;
        String text = getString(R.string.termux_surface_tuning_peek_readout, label, value);
        // setText on the wrap_content pill costs a layout pass; repeated ticks at one value don't.
        if (!text.contentEquals(readout.getText()))
            readout.setText(text);
        if (readout.getVisibility() != View.VISIBLE) {
            readout.setAlpha(0f);
            readout.setVisibility(View.VISIBLE);
            readout.animate().alpha(1f).setDuration(SURFACE_TUNING_PEEK_OUT_MS).start();
        }
    }

    private void peek(@NonNull CharSequence label, @NonNull CharSequence value) {
        setSurfaceTuningPeekReadout(label, value);
    }

    private void hideSurfaceTuningPeekReadout() {
        TextView readout = mHost.findView(R.id.surface_tuning_peek_readout);
        if (readout == null || readout.getVisibility() != View.VISIBLE)
            return;
        readout.animate().alpha(0f).setDuration(SURFACE_TUNING_PEEK_IN_MS)
            .withEndAction(() -> readout.setVisibility(View.GONE)).start();
    }

    // ------------------------------------------------------------------------- unsaved and exit

    /**
     * Every preference the editor can move, in one string. Compared against the value captured on
     * entry to answer "is there anything to lose here?" — cheaper and far harder to get wrong than
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
            .append(prefs().getTopPaneClockStyle()).append('|')
            .append(prefs().getStatusIndicatorCornerRadius()).append('|')
            .append(prefs().getTerminalBackgroundOpacity()).append('|')
            .append(prefs().isTerminalBorderEnabled()).append('|')
            .append(prefs().getTerminalGlassBlurRadius()).append('|')
            .append(prefs().getTerminalGlassGrain()).append('|')
            .append(prefs().getTerminalCornerRadius()).append('|')
            .append(prefs().getTerminalPaneGap()).append('|')
            .append(prefs().getWallpaperBackdropDim()).append('|')
            .append(surfaceEditorLinkSignature()).append('|')
            .append(prefs().getSurfaceBaseValue(SurfaceProperty.BLUR)).append('|')
            .append(prefs().getSurfaceBaseValue(SurfaceProperty.OPACITY)).append('|')
            .append(prefs().getSurfaceBaseValue(SurfaceProperty.GRAIN)).append('|')
            .append(prefs().getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS)).append('|')
            .append(prefs().getSurfaceBaseValue(SurfaceProperty.SIDE_GAP)).append('|')
            .append(prefs().getSurfaceMaterial()).append('|')
            .append(prefs().getSurfaceMaterialIntensity())
            .toString();
    }

    private boolean isSurfaceEditorDirty() {
        return mSurfaceEditorEntrySignature != null
            && !mSurfaceEditorEntrySignature.equals(surfaceEditorStateSignature());
    }

    /** Keeps the badge in step with the snapshot. Settled once on release, not per dragged frame. */
    private void updateSurfaceEditorDirtyBadge() {
        // The dirty test re-derives the whole editor state signature — too much for every tick of a
        // drag, and the badge is under the peek fade anyway.
        if (mSliderDragActive) {
            mDirtyBadgeDeferred = true;
            return;
        }
        if (mPill == null)
            return;
        boolean show = mSurfaceEditorOpen && isSurfaceEditorDirty();
        int target = show ? View.VISIBLE : View.GONE;
        if (mPill.dirty.getVisibility() != target)
            mPill.dirty.setVisibility(target);
    }

    /**
     * The editor's only exit that is not a commit. Done commits; the ✕ and the back press come here,
     * and when there is something to lose they ask rather than silently choosing for the user — the
     * live write-through means "leave" would otherwise mean "keep" by accident.
     */
    public void requestClose() {
        if (!mSurfaceEditorOpen)
            return;
        if (!isSurfaceEditorDirty()) {
            exitSurfaceEditor();
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
                    else exitSurfaceEditor();
                })
            .setPositiveButton(R.string.termux_surface_tuning_unsaved_save,
                (dialog, which) -> exitSurfaceEditor())
            .show();
    }

    private void exitSurfaceEditor() {
        // Cleared before the flag drops: the peek helpers no-op once mSurfaceEditorOpen is false,
        // and a drag interrupted by Done would otherwise leave the pill stuck at peek alpha.
        if (mPill != null) {
            mPill.root.animate().cancel();
            mPill.root.setAlpha(1f);
            mPill.root.setLayerType(View.LAYER_TYPE_NONE, null);
            mPill.dirty.setVisibility(View.GONE);
        }
        hideSurfaceTuningPeekReadout();
        mSurfaceEditorEntrySignature = null;
        mSurfaceEditorRevert = null;
        closePanel();
        mSurfaceEditorOpen = false;
        setSurfaceTuningGestureOverlayVisible(false);
        unregisterSurfaceEditorLayoutListener();
        View ring = mHost.findView(R.id.surface_editor_selection_ring);
        if (ring != null) {
            ring.animate().cancel();
            ring.setVisibility(View.GONE);
            ring.setAlpha(1f);
        }
        if (mPill != null)
            mPill.host.setVisibility(View.GONE);
        restoreExpandedStatusAfterSurfaceEditor();
        mSurfaceEditorRestoreExpandedStatus = false;
    }

    public void restoreExpandedStatusAfterSurfaceEditor() {
        if (prefs() == null)
            return;
        // Only the editor's own temporary change is undone here. onStop() also calls this, and
        // without the guard an expanded pane was collapsed — and the collapse persisted — every time
        // the user left the app, so the clock never came back.
        if (!mSurfaceEditorRestoreExpandedStatus)
            return;
        if (prefs().isTopPaneClockCollapsed())
            mHost.setTopStatusBarCollapsed(false, false);
    }

    // -------------------------------------------------------------------------- the preview pass
    //
    // Sliders fire onProgressChanged far faster than a full re-apply fits in a frame, so requests
    // carry only the scopes their control touches and are coalesced to a single apply per animation
    // frame.

    private int mPendingTuningPreviewScopes;
    private boolean mTuningPreviewScheduled;
    private final Runnable mTuningPreviewRunnable = this::runPendingTuningPreview;
    /** Effective blur inputs the last BLUR-scoped apply saw; an unchanged set skips the re-blur. */
    private long mLastPreviewBlurSignature = Long.MIN_VALUE;
    /** True while a slider thumb is down; heavy per-tick syncs wait for the release. */
    private boolean mSliderDragActive;
    /** Whether the active drag previewed geometry, so the release knows to commit it. */
    private boolean mDragTouchedGeometry;
    /** Whether the active drag moved a blur input it did not preview, owed one re-blur on release. */
    private boolean mDragTouchedBlur;
    /** Whether the active drag skipped the full keyboard reload, owed one on release. */
    private boolean mDragTouchedKeyboard;
    /** Whether a drag skipped dirty-badge updates, owed one restatement on release. */
    private boolean mDirtyBadgeDeferred;

    private void requestSurfaceEditorPreview(int scopes) {
        if (scopes == 0)
            return;
        if (mSliderDragActive && (scopes & SurfaceEditorProperties.PREVIEW_GEOMETRY) != 0)
            mDragTouchedGeometry = true;
        mPendingTuningPreviewScopes |= scopes | SurfaceEditorProperties.PREVIEW_GLASS;
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
        if ((scopes & (SurfaceEditorProperties.PREVIEW_GEOMETRY
                | SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT)) != 0)
            mHost.applyGeometryPreview(
                (scopes & SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT) != 0);
        if ((scopes & SurfaceEditorProperties.PREVIEW_SURFACES) != 0) {
            mHost.applyTerminalSurfaceAppearance();
            mHost.refreshTerminalWindowBar();
            mHost.applySessionsSurfaceBackground();
        }
        // A full keyboard reload re-parses the layout ring; mid-drag its backdrop is already kept
        // live by the glass pass, so the reload waits for the release like geometry does.
        if ((scopes & SurfaceEditorProperties.PREVIEW_KEYBOARD) != 0 && keyboard() != null) {
            if (mSliderDragActive) mDragTouchedKeyboard = true;
            else keyboard().onPreferencesReloaded();
        }
        // A BLUR request only really re-blurs when a blur input moved: the blur slider ticks far
        // more often than its integer value changes, and Undo/preset restores ask broadly. The
        // resolved per-surface radii are the whole input set, so comparing them is exact.
        boolean blurChanged = false;
        if ((scopes & SurfaceEditorProperties.PREVIEW_BLUR) != 0) {
            long blurSignature = currentBlurSignature();
            blurChanged = blurSignature != mLastPreviewBlurSignature;
            mLastPreviewBlurSignature = blurSignature;
        }
        mHost.applyGlassPreview(blurChanged);
        // Mid-drag the pill sits faded at peek alpha, so restating it every frame is CPU spent on
        // pixels nobody can read; the release restates once.
        if (mSurfaceEditorOpen && !mSliderDragActive) {
            syncPill();
            positionSelectionRing(false);
        }
        updateSurfaceEditorDirtyBadge();
    }

    /** Every resolved blur radius the glass pipeline reads, folded to one number. */
    private long currentBlurSignature() {
        TermuxAppSharedPreferences preferences = prefs();
        if (preferences == null)
            return Long.MIN_VALUE;
        long signature = preferences.getExtraKeysBlurRadius();
        signature = signature * 1_000_003L + preferences.getStatusBarBlurRadius();
        return signature * 1_000_003L + preferences.getTerminalGlassBlurRadius();
    }

    /** Broader live re-apply for controls that change dock geometry, terminal, or sessions surfaces. */
    private void applySurfaceEditorStructuralPreview() {
        requestSurfaceEditorPreview(SurfaceEditorProperties.PREVIEW_ALL
            | SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT);
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

    /**
     * The gap knob answers to two names because it does two jobs. Docked spends it on the terminal's
     * outer air as well as on the space between tiled panes, so there it is a margin; Floating insets
     * the frame from the dock's capsule instead and the knob only gaps the panes.
     */
    @StringRes
    private int terminalGapLabelRes() {
        return mHost.isFloatingDock() ? R.string.termux_dock_tuning_terminal_inner_padding
            : R.string.termux_dock_tuning_terminal_margin;
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

    /**
     * Base for every editor slider.
     *
     * <p>Subclasses that override the tracking callbacks must call through, or the pill never fades
     * back in.
     */
    private abstract class SimpleSeekBarChangeListener
        implements SeekBar.OnSeekBarChangeListener {

        /** The slider moved; same contract as {@code onProgressChanged}. */
        abstract void onSliderChanged(SeekBar seekBar, int progress, boolean fromUser);

        @Override public final void onProgressChanged(SeekBar seekBar, int progress,
                                                     boolean fromUser) {
            onSliderChanged(seekBar, progress, fromUser);
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) {
            mSliderDragActive = true;
            mDragTouchedGeometry = false;
            setPillPeek(true);
        }

        @Override public void onStopTrackingTouch(SeekBar seekBar) {
            mSliderDragActive = false;
            // The drag previewed geometry per tick without the terminal resize; the release is where
            // the shell reflow is worth paying for, once.
            if (mDragTouchedGeometry) {
                mDragTouchedGeometry = false;
                requestSurfaceEditorPreview(SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT);
            }
            // A blur input the drag moved without previewing settles here, once.
            if (mDragTouchedBlur) {
                mDragTouchedBlur = false;
                requestSurfaceEditorPreview(SurfaceEditorProperties.PREVIEW_BLUR);
            }
            if (mDragTouchedKeyboard) {
                mDragTouchedKeyboard = false;
                requestSurfaceEditorPreview(SurfaceEditorProperties.PREVIEW_KEYBOARD);
            }
            if (mSurfaceEditorOpen) {
                syncPill();
                syncOpenPanel();
            }
            if (mDirtyBadgeDeferred) {
                mDirtyBadgeDeferred = false;
                updateSurfaceEditorDirtyBadge();
            }
            setPillPeek(false);
        }
    }
}
