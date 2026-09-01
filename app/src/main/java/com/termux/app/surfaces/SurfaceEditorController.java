package com.termux.app.surfaces;

import android.animation.ValueAnimator;
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
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import com.termux.R;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.notice.AppNotice;
import com.termux.app.notice.AppNoticeItem;
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.app.surfaces.SurfaceEditorProperties.Control;
import com.termux.app.surfaces.SurfaceEditorProperties.Kind;
import com.termux.app.terminal.Motion;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The surface editor: one card over the live home screen.
 *
 * <p>It opens on the shared layer — the presets, the two style pills, and the few numbers that move
 * every surface at once — with the surfaces it can edit outlined in a slow accent glow. Touching one
 * of those outlines slides the card to it and swaps the body for that surface's own rows; the header
 * then names the surface and is the way back. The card never covers the surface it is editing, and
 * never overlaps the status bar, the dock or the keyboard: it lives in the free room between them
 * and scrolls inside its own height cap when a raised keyboard shortens that room.
 *
 * <p>Every panel is the same list in the same order — opacity, blur, grain, corners, margin, then
 * whatever else that surface owns. A row the current state makes inert is dropped rather than drawn
 * dead, and the rows below close up into its place, so a property is always found in the same
 * position relative to its neighbours. {@link SurfaceEditorProperties} is that table.
 *
 * <p>The editor writes through to preferences live, so the preview is the real thing; only ✓ commits,
 * and the ✕ and back both route through {@link #requestClose()} against the snapshot taken on entry.
 * The activity keeps the render pipeline; everything the editor needs from it crosses {@link Host},
 * which is the seam that keeps this class free of the activity's fifteen thousand lines.
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
         * The rect the terminal's own frame is drawn at, in window coordinates as
         * {@code {left, top, right, bottom}}, or null while it cannot be measured. The canvas has
         * no view of its own, and its frame moves with the margin knobs, so the editor's outline
         * for it has to come from the same numbers that lay the frame out — anything derived
         * separately drifts the moment a margin changes.
         */
        @Nullable int[] terminalFrameRectInWindow();
        /** The corner that frame actually draws with: the Docked knob, or the capsule's cap. */
        float terminalFrameCornerRadiusPx();
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The activity resumed with the editor open: the status pane the editor is holding at a shape
     * of its own must keep it until the editor closes and hands it back.
     */
    public void collapseStatusPaneIfLeftExpanded() {
        if (!mSurfaceEditorOpen)
            return;
        applyStatusPaneForSelection(false);
    }

    // ------------------------------------------------------------------------------ session state

    private boolean mSurfaceEditorOpen;
    /** Whether the status pane was collapsed when the editor opened; restored on the way out. */
    private boolean mEntryStatusCollapsed;
    private boolean mHasEntryStatusCollapsed;
    /** Editor state as it was on entry; anything different from this is unsaved. */
    @Nullable private String mSurfaceEditorEntrySignature;
    /** Puts that entry state back. Held for the ↺ glyph and for the close gate's Discard. */
    @Nullable private Runnable mSurfaceEditorRevert;
    private ViewTreeObserver.OnGlobalLayoutListener mSurfaceEditorLayoutListener;
    /** Last anchor geometry the layout listener acted on; layouts that move nothing are skipped. */
    private long mSurfaceEditorAnchorSignature = Long.MIN_VALUE;
    private final int[] mTmpAnchorLocation = new int[2];

    /** The surface the card is pointing at, or null for the shared layer it opens on. */
    @Nullable private SurfaceSlot mSelectedSlot;
    /** True while a toggle group is being restated in code, so a restate is not read as a pick. */
    private boolean mRestatingToggles;

    // The editor's own keyboard-height drag state; adjust mode keeps a separate copy in the
    // activity, and the two gestures can never run at once.
    private float mInAppKeyboardHeightDragStartY;
    private float mInAppKeyboardHeightDragStartScale;
    private float mInAppKeyboardUnscaledDragHeight;
    private float mSurfaceTuningInsetDragStartX;
    private float mSurfaceTuningInsetDragStartY;
    private int mSurfaceTuningInsetDragStartDp;
    private boolean mSurfaceTuningDragMoved;

    /** Card opacity while a surface's own drag gesture is running under it. */
    private static final float SURFACE_TUNING_PEEK_ALPHA = 0.28f;
    private static final long SURFACE_TUNING_PEEK_OUT_MS = 90;
    private static final long SURFACE_TUNING_PEEK_IN_MS = 170;
    private static final long SURFACE_TUNING_FADE_DURATION_MS = 200;
    /** How long the card takes to travel to a newly selected surface's park position. */
    private static final long SURFACE_EDITOR_PARK_DURATION_MS = 200;
    private static final long SURFACE_EDITOR_RING_DURATION_MS = 150;
    /** The constant gap between the card and whatever bounds the room it lives in. */
    private static final float SURFACE_EDITOR_STANDOFF_DP = 14f;
    private static final float SURFACE_TUNING_INSET_DRAG_GAIN = 0.5f;
    /** How far the capture groups reach above their surface so the border handle is inside. */
    private static final int SURFACE_TUNING_HANDLE_OVERHANG_DP = 14;
    /**
     * The ring's stroke widths, and how far outside its surface the ring view reaches so the
     * strokes can be centred on the surface's own edge rather than pushed inside it.
     */
    private static final int SURFACE_EDITOR_RING_GLOW_DP = 6;
    private static final int SURFACE_EDITOR_RING_LINE_DP = 2;
    private static final int SURFACE_EDITOR_RING_BLEED_DP = 4;
    /** One full breath of the idle glow. Slow on purpose: it invites, it does not flash. */
    private static final long SURFACE_EDITOR_GLOW_PERIOD_MS = 2400;

    // ------------------------------------------------------------------------------- the card

    /** The card's fixed views, found once per process. Its body is generated per target. */
    private static final class Panel {
        final View host;
        final LinearLayout root;
        final View header;
        final TextView title;
        final ImageView save;
        final ImageView reset;
        final ImageView done;
        final ImageView close;
        final ViewGroup presets;
        final View pills;
        final MaterialButtonToggleGroup shape;
        final MaterialButtonToggleGroup material;
        final ViewGroup rowsHost;

        /** Last heading pushed in; a restate that changes nothing skips its layout pass. */
        String shownTitle;

        Panel(View host, LinearLayout root) {
            this.host = host;
            this.root = root;
            header = root.findViewById(R.id.surface_editor_pill_header);
            title = root.findViewById(R.id.surface_editor_pill_title);
            save = root.findViewById(R.id.surface_editor_pill_save);
            reset = root.findViewById(R.id.surface_editor_pill_reset);
            done = root.findViewById(R.id.surface_editor_pill_done);
            close = root.findViewById(R.id.surface_editor_pill_close);
            presets = root.findViewById(R.id.surface_editor_pill_presets);
            pills = root.findViewById(R.id.surface_editor_pill_pills);
            shape = root.findViewById(R.id.surface_editor_pill_shape);
            material = root.findViewById(R.id.surface_editor_pill_material);
            rowsHost = root.findViewById(R.id.surface_editor_pill_rows_host);
        }

        boolean complete() {
            return header != null && title != null && save != null && reset != null && done != null
                && close != null && presets != null && pills != null && shape != null
                && material != null && rowsHost != null;
        }
    }

    @Nullable private Panel mPanel;
    /** The body's scroller, height-capped so the card never grows past the room it lives in. */
    @Nullable private ScrollView mRowsScroller;
    @Nullable private LinearLayout mRows;
    private int mRowsMaxHeightPx;
    /** Restatements for the rows currently on the card, rebuilt with them. */
    @NonNull private List<Runnable> mRowSyncs = new ArrayList<>();
    /** Which set of rows the body is built for; a change in what is editable rebuilds it. */
    private long mShownRowSignature = Long.MIN_VALUE;

    /** Inflates the card into its host, once. */
    @Nullable
    private Panel panel() {
        if (mPanel != null)
            return mPanel;
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
        Panel panel = new Panel(host, root);
        if (!panel.complete())
            return null;
        mPanel = panel;
        bindPanel(panel);
        return panel;
    }

    // ------------------------------------------------------------------------------------ entry

    public void enter() {
        // No section asked for: the editor opens on the shared layer, every surface outlined.
        enter(null);
    }

    public void enter(@Nullable String initialSection) {
        if (mHost.isFullStatusBarEngaged()) return;
        if (prefs() == null)
            return;
        Panel panel = panel();
        if (panel == null) {
            mSurfaceEditorOpen = false;
            return;
        }
        // Re-entry with the editor already open (a second tuning intent, say) must not re-baseline:
        // the snapshot below is what "unsaved" is measured against, and recapturing it mid-session
        // would quietly adopt the user's in-progress edits as the thing Discard returns to.
        final boolean freshEditorSession = !mSurfaceEditorOpen;
        if (freshEditorSession) {
            mEntryStatusCollapsed = prefs().isTopPaneClockCollapsed();
            mHasEntryStatusCollapsed = true;
        }
        mSurfaceEditorOpen = true;
        panel.host.setVisibility(View.VISIBLE);
        panel.root.setAlpha(1f);

        if (freshEditorSession || mSurfaceEditorEntrySignature == null) {
            mSurfaceEditorRevert = captureEntryState();
            mSurfaceEditorEntrySignature = surfaceEditorStateSignature();
        }

        selectTarget(slotForSectionKey(initialSection), false);
        bindSurfaceTuningGestures();
        panel.host.bringToFront();
        setSurfaceTuningGestureOverlayVisible(true);
        registerSurfaceEditorLayoutListener(panel.host);
        panel.host.post(() -> {
            applyRowsCap();
            parkPanel(false);
            positionSelectionRings(false);
            syncGlow();
        });
    }

    /**
     * The surface a settings deep link targets, or null for a plain open — which is also the shared
     * layer. The section names are the ones the deep links have always sent; "sessions" and "other"
     * are what older callers and stored intents said before the sessions demotion and the terminal
     * rename.
     */
    @Nullable
    private static SurfaceSlot slotForSectionKey(@Nullable String section) {
        if (section == null)
            return null;
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
     * Everything the editor can move, captured so ↺ and Discard can put it back exactly.
     *
     * <p>Raw values and the link shape rather than resolved numbers: a surface that was detached at
     * the same number as Base must come back detached, not quietly folded in. Base itself is
     * restored last, once the links are back in their entry shape — the legacy setters write through
     * whichever link is attached, so a property every surface had detached would otherwise leave the
     * shared layer unrestored.
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
            prefs().setTerminalCornerRadius(initialTerminalCornerRadius);
            prefs().setTerminalPaneGap(initialTerminalGap);
            prefs().setWallpaperBackdropDim(initialWallpaperDim);
            for (SurfaceProperty property : SurfaceProperty.values())
                prefs().setSurfaceBaseValue(property, initialBase[property.ordinal()]);
            prefs().setSurfaceMaterial(initialMaterial);
            prefs().setSurfaceMaterialIntensity(initialMaterialIntensity);
            mHost.refreshPaneLayout();
            mHost.applyTerminalSurfaceAppearance();
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
        };
    }

    // ------------------------------------------------------------------------- the card's wiring

    private void bindPanel(@NonNull Panel panel) {
        // Before the first layout pass, so the card's first frame is never bare text over the
        // wallpaper.
        panel.root.setBackground(buildPanelBackground());
        setIcon(panel.save, R.drawable.ic_symbol_save, false);
        setIcon(panel.reset, R.drawable.ic_symbol_restart, false);
        setIcon(panel.done, R.drawable.ic_symbol_check, true);
        setIcon(panel.close, R.drawable.ic_symbol_close, false);

        // The heading is the way back out of a surface; on the shared layer it is only a label.
        panel.title.setOnClickListener(view -> {
            if (mSelectedSlot != null)
                selectTarget(null, true);
        });
        panel.save.setOnClickListener(view -> saveCurrentLook());
        panel.reset.setOnClickListener(view -> revertToEntryState());
        panel.reset.setOnLongClickListener(view -> {
            resetEverythingToDefaults();
            return true;
        });
        panel.done.setOnClickListener(view -> exitSurfaceEditor());
        // ✓ is the only commit. The ✕ and the back press both route through the unsaved-changes
        // gate, so the two agree with each other without the close glyph silently throwing work
        // away.
        panel.close.setOnClickListener(view -> requestClose());

        panel.shape.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mRestatingToggles || prefs() == null)
                return;
            String style = checkedId == R.id.surface_editor_pill_shape_floating
                ? SegmentedPillPreference.VALUE_ROUNDED : SegmentedPillPreference.VALUE_DEFAULT;
            if (style.equals(prefs().getAppLauncherDockStyle()))
                return;
            prefs().setAppLauncherDockStyle(style);
            applySurfaceEditorStructuralPreview();
            // Docked or Floating decides which rows exist on every panel.
            syncPanel();
        });
        panel.material.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mRestatingToggles || prefs() == null)
                return;
            applyBaseMaterial(materialForButton(checkedId,
                R.id.surface_editor_pill_material_solid,
                R.id.surface_editor_pill_material_frost));
            syncPanel();
        });
    }

    /** A tinted glyph in one of the header's action buttons. */
    private void setIcon(@NonNull ImageView view, @DrawableRes int drawableRes, boolean onAccent) {
        Drawable icon = androidx.core.content.ContextCompat.getDrawable(
            mHost.context(), drawableRes);
        if (icon == null)
            return;
        icon = icon.mutate();
        icon.setTint(onAccent
            ? mHost.themeColor(com.termux.shared.R.attr.termuxColorOnAccentContainer,
                R.color.termux_on_accent_container)
            : mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary));
        view.setImageDrawable(icon);
    }

    // ---------------------------------------------------------------------------- the selection

    /**
     * Points the card at a target: its heading, its rows, its ring and its park position.
     *
     * @param slot    the surface to edit, or null for the shared layer
     * @param animate a real pick, which travels the card to the new park rather than jumping there
     */
    private void selectTarget(@Nullable SurfaceSlot slot, boolean animate) {
        if (mPanel == null)
            return;
        boolean changed = mSelectedSlot != slot;
        mSelectedSlot = slot;
        // Status is the one surface whose content is hidden by the shape the editor holds it in on
        // entry, so selecting it opens it back up — the clock has to be visible to be chosen.
        applyStatusPaneForSelection(changed);
        rebuildRows();
        syncPanel();
        positionSelectionRings(animate && changed);
        positionClockHandle(mHost.findView(R.id.terminal_window_bar_host));
        parkPanel(animate && changed);
        syncGlow();
    }

    /**
     * The status pane's shape while the editor is open: expanded only while it is the surface being
     * edited, so its clock and its chips are there to be judged, and out of the card's way
     * otherwise.
     */
    private void applyStatusPaneForSelection(boolean animate) {
        if (prefs() == null || !mSurfaceEditorOpen || mHost.isFullStatusBarEngaged())
            return;
        boolean collapsed = mSelectedSlot != SurfaceSlot.STATUS;
        if (prefs().isTopPaneClockCollapsed() != collapsed)
            mHost.setTopStatusBarCollapsed(collapsed, animate);
    }

    // ------------------------------------------------------------------------------- the body

    /** The editable set behind the body, folded to one number so restates can skip a rebuild. */
    private long rowSignature() {
        long signature = mSelectedSlot == null ? -1 : mSelectedSlot.ordinal();
        for (Control control : SurfaceEditorProperties.rowsFor(mSelectedSlot))
            signature = signature * 31 + (isAvailable(mSelectedSlot, control) ? 1 : 0);
        return signature;
    }

    /** Regenerates the card's body for the current target, dropping every inert row. */
    private void rebuildRows() {
        Panel panel = mPanel;
        if (panel == null)
            return;
        ensureRowViews(panel);
        LinearLayout rows = mRows;
        if (rows == null)
            return;
        mShownRowSignature = rowSignature();
        rows.removeAllViews();
        List<Runnable> syncs = new ArrayList<>();
        mRowSyncs = syncs;
        for (Control control : SurfaceEditorProperties.rowsFor(mSelectedSlot)) {
            if (isAvailable(mSelectedSlot, control))
                addControlRow(mHost.context(), rows, control, mSelectedSlot, syncs);
        }
        if (mRowsScroller != null)
            mRowsScroller.scrollTo(0, 0);
        applyRowsCap();
    }

    /** The body's one scroller, created on first use: wrap up to the cap, then scroll inside. */
    private void ensureRowViews(@NonNull Panel panel) {
        if (mRowsScroller != null)
            return;
        Context context = mHost.context();
        mRowsScroller = new ScrollView(context) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                    Math.max(dp(80), mRowsMaxHeightPx), View.MeasureSpec.AT_MOST));
            }
        };
        mRowsScroller.setVerticalScrollBarEnabled(false);
        mRowsScroller.setClipToPadding(false);
        // A cramped region caps the list short of its last row or two. The fade is the only thing
        // that says so — a list that simply stops at the card's edge reads as the whole list.
        mRowsScroller.setVerticalFadingEdgeEnabled(true);
        mRowsScroller.setFadingEdgeLength(dp(18));
        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        mRowsScroller.addView(mRows, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.rowsHost.addView(mRowsScroller, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * How tall the body may grow: the room the region has left once the header, the presets and the
     * pills have taken theirs. The cap is what keeps the card off the surfaces bounding it — a list
     * too long for the room scrolls rather than pushing the card over the dock.
     */
    private void applyRowsCap() {
        Panel panel = mPanel;
        if (panel == null || mRowsScroller == null)
            return;
        int[] region = pillRegion();
        int scrollerPx = mRowsScroller.getHeight();
        int chromePx = Math.max(0, panel.root.getHeight() - scrollerPx);
        int capped = SurfaceEditorPillMetrics.bodyCapPx(region[1] - region[0], chromePx,
            dp(SURFACE_EDITOR_STANDOFF_DP), dp(80), dp(360));
        if (capped == mRowsMaxHeightPx)
            return;
        mRowsMaxHeightPx = capped;
        mRowsScroller.requestLayout();
    }

    /**
     * One row: label, its control, its number, and its link back to Base where it has one. The
     * shared row layouts, so every row on every panel reads as the same kind of control.
     */
    private void addControlRow(@NonNull Context context, @NonNull ViewGroup into,
                               @NonNull Control control, @Nullable SurfaceSlot slot,
                               @NonNull List<Runnable> syncs) {
        if (control.kind == Kind.ACTION) {
            View action = LayoutInflater.from(context)
                .inflate(R.layout.surface_editor_action_row, into, false);
            ((TextView) action.findViewById(R.id.surface_editor_row_label))
                .setText(control.labelRes);
            action.setOnClickListener(view -> openAction(control));
            into.addView(action);
            return;
        }
        if (control.kind == Kind.SWITCH) {
            View row = LayoutInflater.from(context)
                .inflate(R.layout.surface_editor_switch_row, into, false);
            ((TextView) row.findViewById(R.id.surface_editor_row_label)).setText(control.labelRes);
            MaterialSwitch toggle = row.findViewById(R.id.surface_editor_row_switch);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (mRestatingToggles || prefs() == null)
                    return;
                if (control.read(prefs()) == (checked ? 1 : 0))
                    return;
                writeControl(slot, control, checked ? 1 : 0);
                // The frame decides whether the terminal's blur and grain rows exist at all.
                syncPanel();
            });
            syncs.add(() -> {
                if (prefs() == null)
                    return;
                boolean on = control.read(prefs()) != 0;
                if (toggle.isChecked() != on) {
                    mRestatingToggles = true;
                    try {
                        toggle.setChecked(on);
                    } finally {
                        mRestatingToggles = false;
                    }
                }
            });
            into.addView(row);
            syncs.get(syncs.size() - 1).run();
            return;
        }

        View rowView = LayoutInflater.from(context)
            .inflate(R.layout.surface_editor_row, into, false);
        TextView label = rowView.findViewById(R.id.surface_editor_row_label);
        SeekBar slider = rowView.findViewById(R.id.surface_editor_row_slider);
        TextView value = rowView.findViewById(R.id.surface_editor_row_value);
        TextView link = rowView.findViewById(R.id.surface_editor_row_chip);
        label.setText(control.labelRes);
        slider.setContentDescription(getString(control.labelRes));

        Runnable sync = () -> {
            if (prefs() == null)
                return;
            int max = maxOf(control);
            if (slider.getMax() != max)
                slider.setMax(max);
            int shown = shownValueOf(slot, control);
            if (slider.getProgress() != shown)
                slider.setProgress(shown);
            value.setText(valueText(control, shown));
            boolean own = slot != null && control.cell != null && hasOwnValue(slot, control);
            link.setVisibility(own ? View.VISIBLE : View.INVISIBLE);
            link.setClickable(own);
            link.setFocusable(own);
            if (own)
                link.setContentDescription(getString(
                    R.string.termux_surface_tuning_link_detached_description,
                    getString(SurfaceEditorRows.slotLabel(slot))));
        };
        link.setOnClickListener(view -> {
            if (prefs() == null || slot == null || control.cell == null
                || prefs().isSurfaceInheriting(slot, control.cell.property))
                return;
            prefs().setSurfaceInheriting(slot, control.cell.property, true);
            applySurfaceEditorStructuralPreview();
            syncPanel();
        });
        slider.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override void onSliderChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText(valueText(control, progress));
                if (!fromUser)
                    return;
                writeControl(slot, control, progress);
                link.setVisibility(slot != null && control.cell != null
                    && hasOwnValue(slot, control) ? View.VISIBLE : View.INVISIBLE);
            }
        });
        into.addView(rowView);
        syncs.add(sync);
        sync.run();
    }

    /** The two rows that leave the editor for a screen of their own. */
    private void openAction(@NonNull Control control) {
        if (SurfaceEditorProperties.ID_KEYBOARD_COLORS.equals(control.id))
            mHost.openKeyboardColors();
    }

    /**
     * Restates the whole card from preferences: heading, header actions, the two pills, and every
     * row. Called once per previewed frame, so each read-out skips a value that has not moved.
     */
    private void syncPanel() {
        Panel panel = mPanel;
        if (panel == null || prefs() == null || !mSurfaceEditorOpen)
            return;

        // A structural change (dock style, terminal frame) can add or remove rows.
        if (rowSignature() != mShownRowSignature)
            rebuildRows();

        boolean shared = mSelectedSlot == null;
        String title = shared
            ? getString(R.string.termux_surface_tuning_presets_section)
            : getString(SurfaceEditorRows.slotLabel(mSelectedSlot));
        if (!title.equals(panel.shownTitle)) {
            panel.shownTitle = title;
            panel.title.setText(title);
            panel.title.setCompoundDrawablesRelative(shared ? null : backGlyph(), null, null, null);
            panel.title.setClickable(!shared);
            panel.title.setFocusable(!shared);
            panel.title.setBackgroundResource(shared ? 0 : selectableItemBackground());
            panel.title.setContentDescription(shared ? title
                : getString(R.string.termux_surface_editor_title_back_description, title));
        }

        int sharedVisibility = shared ? View.VISIBLE : View.GONE;
        if (panel.presets.getVisibility() != sharedVisibility)
            panel.presets.setVisibility(sharedVisibility);
        if (panel.pills.getVisibility() != sharedVisibility)
            panel.pills.setVisibility(sharedVisibility);
        if (shared) {
            if (panel.presets.getChildCount() == 0)
                buildPresetsStrip(mHost.context(), panel.presets);
            syncPresetSelection();
            syncShapeGroup(panel);
            syncBaseMaterialGroup(panel.material);
        }

        for (Runnable sync : mRowSyncs)
            sync.run();
        syncDirtyActions();
    }

    @Nullable
    private Drawable backGlyph() {
        Drawable icon = androidx.core.content.ContextCompat.getDrawable(
            mHost.context(), R.drawable.ic_symbol_arrow_back);
        if (icon == null)
            return null;
        icon = icon.mutate();
        icon.setTint(mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
            R.color.termux_primary));
        int size = dp(16);
        icon.setBounds(0, 0, size, size);
        return icon;
    }

    private int selectableItemBackground() {
        TypedValue value = new TypedValue();
        return mHost.context().getTheme().resolveAttribute(
            android.R.attr.selectableItemBackground, value, true) ? value.resourceId : 0;
    }

    /** Whether the selected surface has taken its own value for this row. */
    private boolean hasOwnValue(@NonNull SurfaceSlot slot, @NonNull Control control) {
        return prefs() != null && control.cell != null
            && !prefs().isSurfaceInheriting(slot, control.cell.property);
    }

    /**
     * Whether a row can act at all right now, and therefore whether it renders.
     *
     * <p>Docked surfaces are flush with the screen edges by definition, so their margin has no
     * number to give; the terminal's own corner radius is the Docked frame's, since Floating takes
     * the dock capsule's shape instead; and the terminal's glass has nothing to live inside until
     * its frame is on. A row the state makes inert is dropped rather than drawn dead — a dead slider
     * is clutter, not signage — and the control that brings it back (the shared layer's style pill,
     * the terminal's own Frame switch) is one tap away.
     */
    private boolean isAvailable(@Nullable SurfaceSlot slot, @NonNull Control control) {
        if (slot == null)
            return true;
        if (control.cell != null && control.cell.property == SurfaceProperty.SIDE_GAP)
            return mHost.isFloatingDock();
        if (slot != SurfaceSlot.CANVAS)
            return true;
        if (SurfaceEditorProperties.ID_CORNERS.equals(control.id))
            return !mHost.isFloatingDock();
        if (SurfaceEditorProperties.ID_BLUR.equals(control.id)
            || SurfaceEditorProperties.ID_GRAIN.equals(control.id))
            return prefs() != null && prefs().isTerminalBorderEnabled();
        return true;
    }

    /**
     * A row's ceiling. Only the shared margin's moves: while Docked it is the terminal's own margin
     * and nothing else, so its track ends where that number does rather than running on into dp the
     * screen-edge gap would have used.
     */
    private int maxOf(@NonNull Control control) {
        if (SurfaceEditorProperties.ID_ALL_MARGIN.equals(control.id) && !mHost.isFloatingDock())
            return SurfaceEditorProperties.MAX_TERMINAL_MARGIN_DP;
        return control.max;
    }

    /** Where a row's slider should sit: the resolved number, capped to its own track. */
    private int shownValueOf(@Nullable SurfaceSlot slot, @NonNull Control control) {
        if (prefs() == null)
            return 0;
        if (SurfaceEditorProperties.ID_CHIP_RADIUS.equals(control.id))
            return clamp(shownIndicatorRadius(), 0, control.max);
        int value;
        if (control.cell != null && slot != null) {
            value = surfaceEditorSliderValue(slot, control.cell.property,
                prefs().isSurfaceInheriting(slot, control.cell.property)
                    ? prefs().getSurfaceBaseValue(control.cell.property)
                    : prefs().getSurfaceOverrideValue(slot, control.cell.property));
        } else if (SurfaceEditorProperties.ID_ALL_CORNERS.equals(control.id)) {
            value = surfaceEditorSliderValue(null, SurfaceProperty.CORNER_RADIUS,
                control.read(prefs()));
        } else {
            value = control.read(prefs());
        }
        return clamp(value, 0, maxOf(control));
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

    /** One row's number in its own unit. */
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

    // ------------------------------------------------------------------- one writer per control
    //
    // Every row writes through its own accessors: a cell of the inheritance model through the
    // preference setter that owns its clamp — inherit-aware, so the row never needs to know whether
    // it is moving Base or one detached surface — and everything outside the cascade through its
    // own. A moved cell leaves Base first, so the change lands on that surface alone rather than
    // dragging every other surface with it, which is the whole point of the model.

    private void writeControl(@Nullable SurfaceSlot slot, @NonNull Control control, int value) {
        if (prefs() == null || !isAvailable(slot, control))
            return;
        if (control.cell != null && slot != null)
            detachSurfaceRowForEdit(slot, control.cell.property);
        control.write(prefs(), value);
        afterWrite(slot, control);
        requestSurfaceEditorPreview(control.previewScopes);
    }

    /**
     * The few rows whose live preview is not the glass pipeline.
     *
     * <p>The keyboard's own metrics are previewed on the view rather than re-rendered, so its
     * geometry tracks the drag without a layout re-parse per tick; the status row restyles both its
     * chips from one place so they cannot drift apart; and the pane gap is laid out by the split
     * tree, so it needs a re-render rather than a restyle.
     */
    private void afterWrite(@Nullable SurfaceSlot slot, @NonNull Control control) {
        switch (control.id) {
            case SurfaceEditorProperties.ID_KEYBOARD_SPACING:
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyMarginScale(
                        prefs().getInAppKeyboardKeyMarginScale());
                syncDirtyActions();
                break;
            case SurfaceEditorProperties.ID_KEYBOARD_KEY_RADIUS:
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyCornerRadiusDp(
                        prefs().getInAppKeyboardKeyCornerRadiusDp());
                syncDirtyActions();
                break;
            case SurfaceEditorProperties.ID_KEYBOARD_KEY_OPACITY:
                if (keyboard() != null)
                    keyboard().previewSurfaceEditorKeyOpacity(
                        prefs().getInAppKeyboardKeyOpacity());
                syncDirtyActions();
                break;
            case SurfaceEditorProperties.ID_CHIP_RADIUS:
                mHost.refreshTerminalWindowBar();
                syncDirtyActions();
                break;
            case SurfaceEditorProperties.ID_ALL_MARGIN:
                mHost.refreshPaneLayout();
                break;
            case SurfaceEditorProperties.ID_MARGIN:
                if (slot == SurfaceSlot.CANVAS)
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

    /** Every link flag as one string, so undo can restore the shape as well as the numbers. */
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

    // ------------------------------------------------------------------------- the two pills

    /** Suppresses the toggle listeners while sync is restating a group programmatically. */
    @NonNull
    private String materialForButton(int buttonId, int solidId, int frostId) {
        if (buttonId == solidId)
            return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_SOLID;
        if (buttonId == frostId)
            return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_FROST;
        return TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_GLASS;
    }

    private int materialButtonId(@NonNull String material, int solidId, int glassId, int frostId) {
        if (TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_SOLID.equals(material))
            return solidId;
        if (TermuxPreferenceConstants.TERMUX_APP.SURFACE_MATERIAL_FROST.equals(material))
            return frostId;
        return glassId;
    }

    /**
     * The material pill writes the shared triple as a set: a family at the intensity the shared
     * layer already carries. Moving one of the three numbers by hand afterwards simply leaves no
     * family able to claim the result, and the pill goes quiet rather than lying.
     */
    private void applyBaseMaterial(@NonNull String material) {
        if (prefs() == null)
            return;
        int intensity = prefs().getSurfaceMaterialIntensity();
        prefs().setSurfaceMaterial(material);
        int previousBlur = prefs().getSurfaceBaseValue(SurfaceProperty.BLUR);
        int[] triple = SurfaceMaterials.triple(material, intensity);
        prefs().setSurfaceBaseValue(SurfaceProperty.BLUR, triple[SurfaceMaterials.BLUR]);
        prefs().setSurfaceBaseValue(SurfaceProperty.OPACITY, triple[SurfaceMaterials.OPACITY]);
        prefs().setSurfaceBaseValue(SurfaceProperty.GRAIN, triple[SurfaceMaterials.GRAIN]);
        int scopes = SurfaceEditorProperties.PREVIEW_GLASS | SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD;
        if (triple[SurfaceMaterials.BLUR] != previousBlur)
            scopes |= SurfaceEditorProperties.PREVIEW_BLUR;
        requestSurfaceEditorPreview(scopes);
    }

    /** Restates the family segments; a triple no family reproduces deselects rather than lying. */
    private void syncBaseMaterialGroup(@NonNull MaterialButtonToggleGroup group) {
        if (prefs() == null)
            return;
        String material = prefs().getSurfaceMaterial();
        int[] expected = SurfaceMaterials.triple(material, prefs().getSurfaceMaterialIntensity());
        boolean matches =
            expected[SurfaceMaterials.BLUR] == prefs().getSurfaceBaseValue(SurfaceProperty.BLUR)
                && expected[SurfaceMaterials.OPACITY]
                    == prefs().getSurfaceBaseValue(SurfaceProperty.OPACITY)
                && expected[SurfaceMaterials.GRAIN]
                    == prefs().getSurfaceBaseValue(SurfaceProperty.GRAIN);
        mRestatingToggles = true;
        try {
            if (matches) {
                int buttonId = materialButtonId(material,
                    R.id.surface_editor_pill_material_solid,
                    R.id.surface_editor_pill_material_glass,
                    R.id.surface_editor_pill_material_frost);
                if (group.getCheckedButtonId() != buttonId)
                    group.check(buttonId);
            } else if (group.getCheckedButtonId() != View.NO_ID) {
                group.clearChecked();
            }
        } finally {
            mRestatingToggles = false;
        }
    }

    private void syncShapeGroup(@NonNull Panel panel) {
        if (prefs() == null)
            return;
        int buttonId = mHost.isFloatingDock()
            ? R.id.surface_editor_pill_shape_floating : R.id.surface_editor_pill_shape_docked;
        if (panel.shape.getCheckedButtonId() == buttonId)
            return;
        mRestatingToggles = true;
        try {
            panel.shape.check(buttonId);
        } finally {
            mRestatingToggles = false;
        }
    }

    // ------------------------------------------------------------------------ shape and placement

    @NonNull
    private Drawable buildPanelBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high));
        background.setCornerRadius(dpToPx(24));
        background.setStroke(Math.max(1, dp(1)), mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant));
        return background;
    }

    /**
     * The band the card lives in, in the host's coordinate space: from just under the launcher's own
     * status chrome down to the accessory stack.
     *
     * <p>Both bounds are read defensively, because the span they describe is not always real. The
     * stack is laid out only while it is visible, and while the dock rides above the system IME on
     * insets it is moved by translation, which {@code getTop()} does not report; the status inset and
     * the window bar are measured against the window, while the host starts below the inset.
     */
    @NonNull
    private int[] pillRegion() {
        View host = mPanel == null ? null : mPanel.host;
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

    /** A surface's own rect in the host's space, or null when it is not on screen. */
    @Nullable
    private int[] anchorRect(@Nullable SurfaceSlot slot) {
        View host = mPanel == null ? null : mPanel.host;
        if (host == null || slot == null)
            return null;
        View surface = anchorViewFor(slot);
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
     * Travels the card to its park position.
     *
     * <p>Placement is computed from the selected surface's anchor and a constant standoff, never
     * from what else is on screen: raising the keyboard moves the ring's neighbours but must not
     * move a card parked against the status bar. The card translates rather than fading out and in,
     * so a pick reads as the same card moving.
     */
    private void parkPanel(boolean animate) {
        Panel panel = mPanel;
        if (panel == null || !mSurfaceEditorOpen)
            return;
        int height = panel.root.getHeight();
        if (height <= 0) {
            panel.root.post(() -> parkPanel(false));
            return;
        }
        int[] region = pillRegion();
        int standoff = dp(SURFACE_EDITOR_STANDOFF_DP);
        int[] anchor = anchorRect(mSelectedSlot);
        int top;
        if (anchor == null) {
            // The shared layer and the canvas are the region rather than a band inside it, and a
            // surface that is off screen has no edge to stand off from; all of them sit at the
            // region's foot, which leaves the terminal above them free in one piece to be touched.
            top = SurfaceEditorPillMetrics.parkRegionFootTopPx(height, standoff, region[0],
                region[1]);
        } else {
            top = SurfaceEditorPillMetrics.parkTopPx(anchor[1], anchor[3],
                mSelectedSlot == SurfaceSlot.STATUS, height, standoff, region[0], region[1]);
        }
        ViewGroup.LayoutParams params = panel.root.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams))
            return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        if (margins.topMargin == top)
            return;
        if (!animate) {
            margins.topMargin = top;
            panel.root.setLayoutParams(margins);
            panel.root.setTranslationY(0f);
            return;
        }
        float delta = margins.topMargin - top;
        margins.topMargin = top;
        panel.root.setLayoutParams(margins);
        panel.root.setTranslationY(delta);
        panel.root.animate().cancel();
        panel.root.animate().translationY(0f)
            .setDuration(SURFACE_EDITOR_PARK_DURATION_MS)
            .setInterpolator(Motion.settle())
            .start();
    }

    // ----------------------------------------------------------------------- the outlines

    /** The ring view for each surface, in the order the overlay declares them. */
    private static final int[] RING_IDS = {
        R.id.surface_editor_ring_status,
        R.id.surface_editor_ring_dock,
        R.id.surface_editor_ring_keyboard,
        R.id.surface_editor_ring_canvas};

    private static final SurfaceSlot[] RING_SLOTS = {
        SurfaceSlot.STATUS, SurfaceSlot.DOCK, SurfaceSlot.KEYBOARD, SurfaceSlot.CANVAS};

    /** Ring geometry the last build used; rebuilding a drawable per layout pass is not free. */
    private final long[] mRingSignatures = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE,
        Long.MIN_VALUE};

    /**
     * Outlines the surfaces the editor can act on.
     *
     * <p>On the shared layer all of them are drawn, breathing together — that pulse is the whole
     * invitation to touch one. With a surface picked, its outline alone stands, steady, and is what
     * identifies the target; the card's own position never has to.
     *
     * <p>Positioned from the surfaces, not from their capture groups: a group deliberately overhangs
     * upward so the drag handle falls inside it, and a ring drawn on that would sit a finger's width
     * above the thing it is identifying. The canvas has no view of its own, so it takes the free
     * region inset by a hair.
     */
    private void positionSelectionRings(boolean animate) {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null)
            return;
        for (int i = 0; i < RING_IDS.length; i++) {
            View ring = mHost.findView(RING_IDS[i]);
            if (ring == null)
                continue;
            SurfaceSlot slot = RING_SLOTS[i];
            boolean wanted = mSurfaceEditorOpen
                && (mSelectedSlot == null || mSelectedSlot == slot);
            int[] rect = slot == SurfaceSlot.CANVAS ? canvasRingRect() : anchorRect(slot);
            if (!wanted || rect == null) {
                if (ring.getVisibility() != View.GONE) {
                    ring.animate().cancel();
                    ring.setVisibility(View.GONE);
                }
                continue;
            }
            layoutRing(ring, overlay, rect);
            long signature = mixAnchor(slot.ordinal(), ringRadiusDp(slot));
            signature = mixAnchor(signature, mHost.isFloatingDock() ? 1 : 0);
            if (signature != mRingSignatures[i]) {
                mRingSignatures[i] = signature;
                ring.setBackground(buildSelectionRing(slot));
            }
            boolean appearing = ring.getVisibility() != View.VISIBLE;
            if (appearing)
                ring.setVisibility(View.VISIBLE);
            if (mSelectedSlot == null) {
                // The glow owns alpha here and writes it every frame; a fade-in would fight it.
                ring.animate().cancel();
                ring.setAlpha(1f);
            } else if (appearing || animate) {
                ring.animate().cancel();
                ring.setAlpha(appearing ? 0f : 0.4f);
                ring.animate().alpha(1f).setDuration(SURFACE_EDITOR_RING_DURATION_MS)
                    .setInterpolator(Motion.settle()).start();
            }
        }
    }

    /**
     * The terminal's own frame, in the host's space. Read from the activity rather than derived
     * from the free region: the frame is inset by the margin knobs, and a ring measured any other
     * way slid away from the border it was outlining every time one of them moved.
     */
    @Nullable
    private int[] canvasRingRect() {
        View host = mPanel == null ? null : mPanel.host;
        int[] frame = mHost.terminalFrameRectInWindow();
        if (host == null || frame == null
            || frame[2] - frame[0] < dp(24) || frame[3] - frame[1] < dp(24))
            return null;
        host.getLocationInWindow(mTmpAnchorLocation);
        return new int[] {
            frame[0] - mTmpAnchorLocation[0], frame[1] - mTmpAnchorLocation[1],
            frame[2] - mTmpAnchorLocation[0], frame[3] - mTmpAnchorLocation[1]};
    }

    /**
     * Lays the ring view over its surface, reaching {@link #SURFACE_EDITOR_RING_BLEED_DP} outside
     * it on every side. The bleed is what lets the strokes be centred on the surface's own edge:
     * a {@code GradientDrawable} strokes inward from its bounds, so a ring sized to the surface
     * exactly draws its accent line a few dp inside the border it is supposed to be tracing.
     * Margins go negative at a screen-flush surface, which is correct — the window clips the half
     * that falls outside, exactly as the surface's own edge does.
     */
    private void layoutRing(@NonNull View ring, @NonNull View overlay, @NonNull int[] rect) {
        int bleed = dp(SURFACE_EDITOR_RING_BLEED_DP);
        int left = rect[0] - bleed;
        int top = rect[1] - bleed;
        int width = Math.max(1, (rect[2] - rect[0]) + (2 * bleed));
        int height = Math.max(1, (rect[3] - rect[1]) + (2 * bleed));
        ViewGroup.LayoutParams params = ring.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams))
            return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        int right = overlay.getWidth() - (left + width);
        if (margins.leftMargin == left && margins.rightMargin == right
            && margins.topMargin == top && margins.height == height)
            return;
        margins.leftMargin = left;
        margins.rightMargin = right;
        margins.topMargin = top;
        margins.height = height;
        ring.setLayoutParams(margins);
    }

    /** The radius a ring wears: the surface's own, so the ring reads as that surface's edge. */
    private int ringRadiusDp(@NonNull SurfaceSlot slot) {
        if (prefs() == null)
            return 16;
        switch (slot) {
            case DOCK:
                return clamp(surfaceEditorSliderValue(SurfaceSlot.DOCK,
                    SurfaceProperty.CORNER_RADIUS, prefs().getAppLauncherDockCornerRadius()), 0, 40);
            case STATUS:
                return clamp(surfaceEditorSliderValue(SurfaceSlot.STATUS,
                    SurfaceProperty.CORNER_RADIUS, prefs().getStatusBarCornerRadius()), 0, 40);
            case KEYBOARD:
                return 20;
            default:
                // What the frame is actually drawing with — Floating rounds it by the dock's
                // capsule, not by the terminal's own knob.
                return clamp(Math.round(pxToDp(mHost.terminalFrameCornerRadiusPx())), 0, 40);
        }
    }

    /**
     * An accent hairline inside a soft glow, both centred on the surface's own edge.
     *
     * <p>Two strokes rather than one: a 2dp line alone disappears against a busy wallpaper, and a
     * thick one reads as a border the surface has grown. Each layer is inset so that the stroke
     * {@code GradientDrawable} draws — half a stroke width inside the layer's bounds — lands on the
     * edge itself. Same corner radius on both, because an arc struck at radius R from a rect inset
     * by any amount still shares its centre with the surface's own corner.
     */
    @NonNull
    private Drawable buildSelectionRing(@NonNull SurfaceSlot slot) {
        int accent = mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary,
            R.color.termux_primary);
        float radiusPx = dpToPx(ringRadiusDp(slot));
        // The keyboard is only rounded where it leaves the screen edge, so its ring is too.
        boolean topOnly = slot == SurfaceSlot.KEYBOARD;

        int glowWidth = dp(SURFACE_EDITOR_RING_GLOW_DP);
        int lineWidth = Math.max(1, dp(SURFACE_EDITOR_RING_LINE_DP));
        GradientDrawable glow = new GradientDrawable();
        glow.setColor(0);
        glow.setStroke(glowWidth, withAlpha(accent, 36));
        GradientDrawable line = new GradientDrawable();
        line.setColor(0);
        line.setStroke(lineWidth, accent);
        if (topOnly) {
            float[] corners = {radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0};
            glow.setCornerRadii(corners);
            line.setCornerRadii(corners);
        } else {
            glow.setCornerRadius(radiusPx);
            line.setCornerRadius(radiusPx);
        }
        int bleed = dp(SURFACE_EDITOR_RING_BLEED_DP);
        int glowInset = Math.max(0, bleed - Math.round(glowWidth / 2f));
        int lineInset = Math.max(0, bleed - Math.round(lineWidth / 2f));
        LayerDrawable ring = new LayerDrawable(new Drawable[] {glow, line});
        ring.setLayerInset(0, glowInset, glowInset, glowInset, glowInset);
        ring.setLayerInset(1, lineInset, lineInset, lineInset, lineInset);
        return ring;
    }

    private static int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------------------- the glow

    @Nullable private ValueAnimator mGlow;

    /**
     * The one animation the editor runs, and only while something is waiting to be touched: the
     * outlines breathe on the shared layer, and the keyboard's resize handle breathes while the
     * keyboard is the surface being edited. It drives alpha on a handful of childless views, and it
     * stops the moment neither is true.
     */
    private void syncGlow() {
        boolean wanted = mSurfaceEditorOpen && (mSelectedSlot == null
            || (mSelectedSlot == SurfaceSlot.KEYBOARD && mHost.isInAppKeyboardShown()));
        if (!wanted) {
            if (mGlow != null) {
                mGlow.cancel();
                mGlow = null;
            }
            applyGlow(1f);
            return;
        }
        if (mGlow != null)
            return;
        mGlow = ValueAnimator.ofFloat(0f, 1f);
        mGlow.setDuration(SURFACE_EDITOR_GLOW_PERIOD_MS / 2);
        mGlow.setRepeatCount(ValueAnimator.INFINITE);
        mGlow.setRepeatMode(ValueAnimator.REVERSE);
        mGlow.setInterpolator(new LinearInterpolator());
        mGlow.addUpdateListener(animation -> applyGlow((Float) animation.getAnimatedValue()));
        mGlow.start();
    }

    private void applyGlow(float phase) {
        float breath = eased(phase);
        float ringAlpha = mSelectedSlot == null ? 0.34f + 0.66f * breath : 1f;
        for (int ringId : RING_IDS) {
            View ring = mHost.findView(ringId);
            if (ring != null && ring.getVisibility() == View.VISIBLE)
                ring.setAlpha(ringAlpha);
        }
        View handle = mHost.findView(R.id.surface_tuning_keyboard_height_grip);
        if (handle != null) {
            handle.setAlpha(mSelectedSlot == SurfaceSlot.KEYBOARD
                ? 0.45f + 0.55f * breath : 1f);
        }
    }

    /** Smoothstep, so the breath has no corner at either end of its travel. */
    private static float eased(float phase) {
        float t = Math.max(0f, Math.min(1f, phase));
        return t * t * (3f - 2f * t);
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
        syncPanel();
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
                selectTarget(slot, true);
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
                        // A margin drag is the one gesture whose surface the card may be crowding.
                        if (canDragMargin(slot))
                            setPanelPeek(true);
                    }
                    if (!mSurfaceTuningDragMoved || !canDragMargin(slot))
                        return true;
                    int insetDp = TermuxAppSharedPreferences.clampSurfaceHorizontalInset(
                        Math.round(mSurfaceTuningInsetDragStartDp
                            + (pxToDp(travelX) * SURFACE_TUNING_INSET_DRAG_GAIN)));
                    if (insetDp != surfaceTuningInsetDp(slot)) {
                        if (mSelectedSlot != slot)
                            selectTarget(slot, true);
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
                    setPanelPeek(false);
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
                    if (mSelectedSlot != SurfaceSlot.KEYBOARD)
                        selectTarget(SurfaceSlot.KEYBOARD, true);
                    setPanelPeek(true);
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
                    syncDirtyActions();
                    setPanelPeek(false);
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    /** The ▾ beside the live clock: the status bar's one control that is a look, not a number. */
    private void bindClockHandle() {
        View handle = mHost.findView(R.id.surface_tuning_status_clock_handle);
        if (handle == null)
            return;
        handle.setContentDescription(getString(R.string.termux_surface_tuning_clock_open));
        handle.setOnClickListener(this::showClockDropdown);
    }

    private void bindSurfaceTuningGestures() {
        bindSurfaceTouch(R.id.surface_tuning_dock_gesture_group, SurfaceSlot.DOCK);
        bindSurfaceTouch(R.id.surface_tuning_keyboard_gesture_group, SurfaceSlot.KEYBOARD);
        bindSurfaceTouch(R.id.surface_tuning_status_gesture_group, SurfaceSlot.STATUS);
        bindSurfaceTouch(R.id.surface_tuning_canvas_gesture_group, SurfaceSlot.CANVAS);
        bindSurfaceTuningKeyboardHeightGesture();
        bindClockHandle();
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
        positionClockHandle(statusSurface);
        positionSelectionRings(false);
    }

    /**
     * Parks the clock's ▾ against the live clock's own trailing edge, so the control that changes
     * the face stands beside the face. Only while the status bar is the surface being edited —
     * which is also the only time the bar is open far enough to show the clock at all.
     */
    private void positionClockHandle(@Nullable View statusSurface) {
        View handle = mHost.findView(R.id.surface_tuning_status_clock_handle);
        if (handle == null)
            return;
        View clock = mHost.findView(R.id.terminal_clock_widget);
        boolean wanted = mSurfaceEditorOpen && mSelectedSlot == SurfaceSlot.STATUS
            && statusSurface != null && clock != null && clock.getVisibility() == View.VISIBLE
            && clock.getWidth() > 0 && clock.getHeight() > 0;
        if (!wanted) {
            if (handle.getVisibility() != View.GONE)
                handle.setVisibility(View.GONE);
            return;
        }
        View group = mHost.findView(R.id.surface_tuning_status_gesture_group);
        if (group == null)
            return;
        int[] groupLocation = new int[2];
        int[] clockLocation = new int[2];
        group.getLocationInWindow(groupLocation);
        clock.getLocationInWindow(clockLocation);
        int size = dp(28);
        // The clock view fills the slot and paints inside it, so its own right edge is nowhere near
        // the clock's; the widget is the only thing that knows where the digits stop.
        int paintedRight = clock instanceof TerminalClockWidget
            ? Math.round(((TerminalClockWidget) clock).paintedRightPx()) : clock.getWidth();
        int left = clamp((clockLocation[0] - groupLocation[0]) + paintedRight + dp(2),
            0, Math.max(0, group.getWidth() - size));
        int top = Math.max(0,
            (clockLocation[1] - groupLocation[1]) + (clock.getHeight() - size) / 2);
        ViewGroup.LayoutParams params = handle.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            if (margins.leftMargin != left || margins.topMargin != top
                || margins.width != size || margins.height != size) {
                margins.leftMargin = left;
                margins.topMargin = top;
                margins.width = size;
                margins.height = size;
                handle.setLayoutParams(margins);
            }
        }
        if (handle.getVisibility() != View.VISIBLE)
            handle.setVisibility(View.VISIBLE);
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
            // Global layout fires for every text change a slider tick causes; the card and the
            // gesture overlay only care when one of their anchors — the stack, the status inset, the
            // window bar, a surface host — actually moved. Anything else is skipped whole,
            // including the walk that would re-derive the same geometry.
            long signature = computeSurfaceEditorAnchorSignature();
            if (signature == mSurfaceEditorAnchorSignature)
                return;
            mSurfaceEditorAnchorSignature = signature;
            positionSurfaceTuningGestureTargets();
            applyRowsCap();
            parkPanel(false);
            syncGlow();
        };
        host.getViewTreeObserver().addOnGlobalLayoutListener(mSurfaceEditorLayoutListener);
    }

    /** Everything the card's placement and the gesture-target positions read, as one number. */
    private long computeSurfaceEditorAnchorSignature() {
        View stack = mHost.findView(R.id.accessory_stack_container);
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        View host = mPanel == null ? null : mPanel.host;
        int parentHeight = host != null && host.getParent() instanceof View
            ? ((View) host.getParent()).getHeight() : 0;
        long signature = mHost.statusBarInsetTop();
        signature = mixAnchor(signature, parentHeight);
        signature = mixAnchor(signature,
            stack != null ? surfaceEditorStackTopPx(stack, parentHeight) : -1);
        signature = mixAnchor(signature, overlay != null ? overlay.getWidth() : -1);
        signature = mixAnchor(signature, mPanel == null ? -1 : mPanel.root.getHeight());
        signature = mixAnchor(signature,
            anchorRectSignature(mHost.findView(R.id.terminal_window_bar_host)));
        signature = mixAnchor(signature,
            anchorRectSignature(mHost.findView(R.id.accessory_surface_host)));
        signature = mixAnchor(signature, anchorRectSignature(mHost.isInAppKeyboardShown()
            ? mHost.findView(R.id.inapp_keyboard_view_host) : null));
        signature = mixAnchor(signature, anchorRectSignature(
            mHost.findView(R.id.terminal_clock_widget)));
        int[] frame = mHost.terminalFrameRectInWindow();
        for (int edge : frame == null ? new int[] {-1} : frame)
            signature = mixAnchor(signature, edge);
        signature = mixAnchor(signature, mHost.isFloatingDock() ? 1 : 0);
        return mixAnchor(signature, mSelectedSlot == null ? -1 : mSelectedSlot.ordinal());
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
        if (mPanel != null)
            mPanel.host.getViewTreeObserver()
                .removeOnGlobalLayoutListener(mSurfaceEditorLayoutListener);
        mSurfaceEditorLayoutListener = null;
    }

    // ------------------------------------------------------------------------- the header actions

    /** Pins the live look into the Custom preset slot. */
    private void saveCurrentLook() {
        if (prefs() == null)
            return;
        SurfacePresets.saveCustom(prefs());
        refreshPresetPreviews();
        syncPresetSelection();
        AppNotice.success(mHost.context(), getString(R.string.termux_surface_preset_saved));
    }

    /** ↺: everything back to how it looked when the editor opened, without leaving the editor. */
    private void revertToEntryState() {
        Runnable revert = mSurfaceEditorRevert;
        if (revert == null)
            return;
        revert.run();
        syncEditorAfterBulkWrite();
    }

    /** ↺ held: one page, one reset — the shipped defaults for everything the editor owns. */
    private void resetEverythingToDefaults() {
        resetEverything();
        syncEditorAfterBulkWrite();
        AppNotice.success(mHost.context(), getString(R.string.termux_surface_editor_reset_done));
    }

    /**
     * Shipped defaults for everything the editor owns. Every surface goes back on Base first, then
     * Base itself takes the shipped numbers — the fresh-install state — so no legacy per-surface key
     * needs writing at all: an attached link never reads its raw key, and writing one through the
     * link would move Base twice.
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
    }

    // ------------------------------------------------------------------------------- the presets
    //
    // Complete looks, one tap each, on the shared layer only: a preset overwrites every surface,
    // detached overrides included (that is what "complete" means), which is not an answer to a
    // question asked while editing one of them. Each card is a mini device mock drawn from the
    // preset's own numbers, wearing the live glass recipe, and the card whose values exactly match
    // the live preferences wears a ring. One Undo puts back the exact raw values and link shape.

    /** Preview frame and name per preset id, for the selection ring. */
    private final Map<String, Pair<View, TextView>> mPresetItems = new LinkedHashMap<>();

    private void buildPresetsStrip(@NonNull Context context, @NonNull ViewGroup container) {
        container.removeAllViews();
        mPresetItems.clear();
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
        container.addView(strip);
        refreshPresetPreviews();
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
        syncEditorAfterBulkWrite();
        // The confirmation goes to the app's own notice chip, not a snackbar: a snackbar lands
        // bottom-centre — on top of the dock, under the soft keyboard, into the display cutouts, in
        // Material's palette rather than this app's, with no swipe to get rid of it. The chip sits
        // in the top-trailing corner the rest of the app's notices use, and its tap is the Undo.
        AppNotice.undoable(mHost.context(),
            getString(R.string.termux_surface_preset_applied, getString(preset.nameRes)),
            getString(R.string.termux_surface_preset_undo_hint),
            () -> {
                undo.run();
                syncEditorAfterBulkWrite();
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

    /** Restates everything after a bulk write: a preset, its Undo, ↺, or Reset. */
    private void syncEditorAfterBulkWrite() {
        if (prefs() == null)
            return;
        mHost.refreshPaneLayout();
        mHost.applyTerminalSurfaceAppearance();
        mHost.refreshTerminalWindowBar();
        if (keyboard() != null)
            keyboard().onPreferencesReloaded();
        applySurfaceEditorStructuralPreview();
        refreshPresetPreviews();
        rebuildRows();
        syncPanel();
        positionSelectionRings(false);
    }

    // ------------------------------------------------------------------------- the clock face
    //
    // The status bar's one control that is a look rather than a number, so it does not sit on the
    // card as a row: it is a ▾ beside the live clock, and it drops the six faces under itself drawn
    // as themselves. Picking one applies it the way every other editor control writes — live, and
    // gated by ✓ like the rest.

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

    @Nullable private PopupWindow mClockDropdown;

    /** The ▾'s drop-down: the six faces, drawn as themselves, right under the clock they replace. */
    private void showClockDropdown(@NonNull View anchor) {
        if (prefs() == null)
            return;
        dismissClockDropdown();
        Context context = mHost.context();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), dp(6), dp(12), dp(6));
        ScrollView scroller = new ScrollView(context);
        scroller.addView(column);

        // The app's own panel fill: six faces of thin digits judged over whatever the terminal
        // happens to be printing is not a fair look at any of them.
        GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(dpToPx(16));
        field.setColor(mHost.themeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high));
        field.setStroke(Math.max(1, dp(1)), mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOutlineVariant, R.color.termux_outline_variant));

        int width = Math.min(dp(300),
            getResources().getDisplayMetrics().widthPixels - dp(32));
        PopupWindow popup = new PopupWindow(scroller, width,
            ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(field);
        popup.setElevation(dpToPx(12));
        popup.setOutsideTouchable(true);
        popup.setOnDismissListener(() -> mClockDropdown = null);

        String current = prefs().getTopPaneClockStyle();
        for (String style : CLOCK_STYLES) {
            final String picked = style;
            column.addView(clockFaceRow(context, style, current, () -> {
                pickClockStyle(picked);
                popup.dismiss();
            }));
        }
        mClockDropdown = popup;
        popup.showAsDropDown(anchor, 0, dp(4), Gravity.START);
    }

    private void dismissClockDropdown() {
        if (mClockDropdown == null)
            return;
        mClockDropdown.dismiss();
        mClockDropdown = null;
    }

    /** One face in the drop-down: its name, the face itself, and a tick on the one in use. */
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

    /** Live like every other editor control: written through, previewed, and gated by ✓. */
    private void pickClockStyle(@NonNull String style) {
        if (prefs() == null || style.equals(prefs().getTopPaneClockStyle()))
            return;
        prefs().setTopPaneClockStyle(style);
        // One place re-reads face, alignment, 12-hour and lazy mode onto the live widget.
        mHost.refreshTerminalWindowBar();
        syncDirtyActions();
    }

    // -------------------------------------------------------------------------------- the peek
    //
    // The two gestures that live on the surfaces themselves — walking a margin, dragging the
    // keyboard's height — happen under the card, so the card gets out of the way for their duration
    // and the number being changed is echoed over the surface instead. Purely visual: the card keeps
    // its position and stays hit-testable. The panel's own sliders do not peek; their row already
    // prints the number under the finger.

    private void setPanelPeek(boolean peek) {
        Panel panel = mPanel;
        if (panel == null || !mSurfaceEditorOpen)
            return;
        panel.root.animate().cancel();
        // The whole drag happens behind a translucent card, and the thumb invalidates it on every
        // moved pixel. A hardware layer for the duration turns each of those frames into a cached-
        // texture composite instead of an offscreen alpha pass over the full control tree.
        if (peek && panel.root.getLayerType() != View.LAYER_TYPE_HARDWARE)
            panel.root.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        panel.root.animate()
            .alpha(peek ? SURFACE_TUNING_PEEK_ALPHA : 1f)
            .setDuration(peek ? SURFACE_TUNING_PEEK_OUT_MS : SURFACE_TUNING_PEEK_IN_MS)
            .setInterpolator(Motion.settle())
            .withEndAction(() -> {
                if (!peek) panel.root.setLayerType(View.LAYER_TYPE_NONE, null);
            })
            .start();
        if (!peek) hideSurfaceTuningPeekReadout();
    }

    /** The faded card's number, over the surface, so peeking does not trade one blindness for another. */
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

    /**
     * Save and ↺ exist only once there is something to save or put back — which is also how the
     * header says the edit is unsaved, without spending a word on it. Settled once on release, not
     * per dragged frame.
     */
    private void syncDirtyActions() {
        if (mSliderDragActive) {
            mDirtyBadgeDeferred = true;
            return;
        }
        if (mPanel == null)
            return;
        int target = mSurfaceEditorOpen && isSurfaceEditorDirty() ? View.VISIBLE : View.GONE;
        if (mPanel.save.getVisibility() != target)
            mPanel.save.setVisibility(target);
        if (mPanel.reset.getVisibility() != target)
            mPanel.reset.setVisibility(target);
    }

    /**
     * The editor's only exit that is not a commit. ✓ commits; the ✕ and the back press come here,
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
                    exitSurfaceEditor();
                })
            .setPositiveButton(R.string.termux_surface_tuning_unsaved_save,
                (dialog, which) -> exitSurfaceEditor())
            .show();
    }

    private void exitSurfaceEditor() {
        // Cleared before the flag drops: the peek helpers no-op once mSurfaceEditorOpen is false,
        // and a drag interrupted by ✓ would otherwise leave the card stuck at peek alpha.
        if (mPanel != null) {
            mPanel.root.animate().cancel();
            mPanel.root.setAlpha(1f);
            mPanel.root.setLayerType(View.LAYER_TYPE_NONE, null);
            mPanel.save.setVisibility(View.GONE);
            mPanel.reset.setVisibility(View.GONE);
        }
        dismissClockDropdown();
        hideSurfaceTuningPeekReadout();
        mSurfaceEditorEntrySignature = null;
        mSurfaceEditorRevert = null;
        mSelectedSlot = null;
        mSurfaceEditorOpen = false;
        syncGlow();
        setSurfaceTuningGestureOverlayVisible(false);
        unregisterSurfaceEditorLayoutListener();
        for (int ringId : RING_IDS) {
            View ring = mHost.findView(ringId);
            if (ring != null) {
                ring.animate().cancel();
                ring.setVisibility(View.GONE);
                ring.setAlpha(1f);
            }
        }
        View clockHandle = mHost.findView(R.id.surface_tuning_status_clock_handle);
        if (clockHandle != null)
            clockHandle.setVisibility(View.GONE);
        if (mPanel != null)
            mPanel.host.setVisibility(View.GONE);
        restoreExpandedStatusAfterSurfaceEditor();
        mHasEntryStatusCollapsed = false;
    }

    /** Hands the status pane back the shape it had before the editor borrowed it. */
    public void restoreExpandedStatusAfterSurfaceEditor() {
        if (prefs() == null || !mHasEntryStatusCollapsed)
            return;
        // Only the editor's own temporary change is undone here. onStop() also calls this, and
        // without the guard an expanded pane was collapsed — and the collapse persisted — every time
        // the user left the app, so the clock never came back.
        if (prefs().isTopPaneClockCollapsed() != mEntryStatusCollapsed)
            mHost.setTopStatusBarCollapsed(mEntryStatusCollapsed, false);
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
    /** Whether a drag skipped the header's dirty actions, owed one restatement on release. */
    private boolean mDirtyBadgeDeferred;

    private void requestSurfaceEditorPreview(int scopes) {
        if (scopes == 0) {
            syncDirtyActions();
            return;
        }
        if (mSliderDragActive) {
            if ((scopes & SurfaceEditorProperties.PREVIEW_GEOMETRY) != 0)
                mDragTouchedGeometry = true;
            // The blur curve moves whole dp rarely, and mid-drag a re-blur is the frame the editor
            // can least afford. The release settles it once, like geometry.
            if ((scopes & SurfaceEditorProperties.PREVIEW_BLUR) != 0) {
                mDragTouchedBlur = true;
                scopes &= ~SurfaceEditorProperties.PREVIEW_BLUR;
            }
        }
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
        // Mid-drag the card is showing the number under the finger already, and the rings do not
        // move; a full restatement per frame is CPU spent on pixels nobody is reading.
        if (mSurfaceEditorOpen && !mSliderDragActive)
            positionSelectionRings(false);
        syncDirtyActions();
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
        switch (clamp(index, 0, DockLayoutPolicy.sizePresetCount() - 1)) {
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
     * <p>Subclasses that override the tracking callbacks must call through, or the deferred work a
     * drag piles up is never settled.
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
            seekBar.getParent().requestDisallowInterceptTouchEvent(true);
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
            if (mSurfaceEditorOpen)
                syncPanel();
            if (mDirtyBadgeDeferred) {
                mDirtyBadgeDeferred = false;
                syncDirtyActions();
            }
        }
    }
}
