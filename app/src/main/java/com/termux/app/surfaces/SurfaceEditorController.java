package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.chrome.GlassMotion;
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.PlaceLayout;
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.app.surfaces.AppearanceLooks.Target;
import com.termux.app.terminal.Motion;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;


/**
 * The Appearance and Layout editor (appearance-layout-editor SPEC §3.1–3.6): the launcher itself,
 * live, scaled into a frame at the device's corner radius, over a bottom area that never scrolls.
 * The mode pill picks what the frame and the bottom area are for: Appearance tunes the live
 * render; Layout cross-fades the frame to the layout canvas at the frame's full size (Layout is
 * not a miniature) with the orientation toggle and the restore tray below it.
 *
 * <p>The frame is {@code terminal_root_container} scaled about a top-centre pivot
 * ({@link AppearanceEditorFrame}); the bottom area is an M3 sheet outside it
 * ({@link AppearanceEditorPanel}). While the editor is up the terminal's own touches are taken by
 * the gesture overlay — one tap target per element, and the bare wallpaper under all of them — the
 * in-app keyboard is raised so it can be tapped, and the pane wall's paging is held.</p>
 *
 * <p>Row 1 is the Look slider — Clear · Mist · Tint · Solid · Custom. A stop applies its Look
 * live. At Custom a tap on an element opens row 2 with that element's controls
 * ({@link AppearanceLooks}); a tap at a Look stop moves the slider to Custom first, seeded from
 * the Look it left. Sliding from Custom back to a Look applies it with an Undo-able notice. Done
 * at Custom saves the Custom look, so its stop comes back.</p>
 *
 * <p>Layout mode's bottom area carries what no Look sets: the Style toggle and the global Corners
 * and Margin (SPEC §3.5). Corners and Margin are Floating's and are hidden under Docked, which
 * spends neither (SPEC §3.7). They write through like every other control, and the one Undo, dirty
 * state and Discard cover them. The layout canvas is told the Style, Corners and Margin and draws
 * every shape from the shape model.</p>
 *
 * <p>Everything writes through to preferences live, so the frame is the real thing; Undo and
 * Discard put back the state at open ({@link AppearanceSnapshot}) and the arrangement at open
 * ({@link LayoutEditorController}, whose session opens and closes with the editor). One dirty
 * state, one Undo, one Done and one unsaved-changes question cover both modes; switching modes
 * never asks. The activity keeps the render pipeline; what the editor needs from it crosses
 * {@link Host}.</p>
 */
public final class SurfaceEditorController {

    /** What the editor needs from the activity: its views, its prefs, and its render pipeline. */
    public interface Host {
        @NonNull Context context();
        @Nullable <T extends View> T findView(int viewId);
        @Nullable TermuxAppSharedPreferences preferences();
        /** How the place on screen is arranged: which surfaces the frame has to offer. */
        @NonNull PlaceLayout placeLayout();
        @Nullable TermuxInAppKeyboard inAppKeyboard();
        boolean isInAppKeyboardShown();
        /**
         * Raises the in-app keyboard for the editor, so it can be tapped in the frame. False where
         * it cannot be raised — switched off, or no keyboard — and nothing was changed.
         */
        boolean showInAppKeyboardForEditor();
        /** Takes down a keyboard {@link #showInAppKeyboardForEditor()} raised. */
        void hideInAppKeyboardForEditor();
        boolean isFloatingDock();
        void setTopStatusBarCollapsed(boolean collapsed, boolean animate);
        /** Whether the status bar is resting compact for the place it is showing for. */
        boolean isTopStatusBarCollapsed();
        /** The window's top status inset, as last delivered to the activity. */
        int statusBarInsetTop();
        int themeColor(int attr, int fallbackRes);
        void refreshPaneLayout();
        /** Hold the pane wall's gestures while the editor is up, or hand them back. */
        void holdPaneWall(boolean held);
        void applyTerminalSurfaceAppearance();
        /** Dresses all the chrome again from the current state, the way a launch does. */
        void redressChrome();
        void refreshTerminalWindowBar();
        /** Rebuilds the terminal palette if the contrast level moved (Legibility drives both). */
        void refreshTerminalPalette();
        /** Dock geometry changed; with {@code commit} the terminal is also resized. */
        void applyGeometryPreview(boolean commit);
        /** The coalesced glass re-render. */
        void applyGlassPreview();
        /**
         * The rect the terminal's own frame is drawn at, in {@code terminal_root_container}'s own
         * unscaled coordinates, as {@code {left, top, right, bottom}}, or null while it cannot be
         * measured.
         */
        @Nullable int[] terminalFrameRectInRoot();
        /** The corner that frame actually draws with. */
        float terminalFrameCornerRadiusPx();
        /** The corner the keyboard surface itself is clipped to. */
        float keyboardSurfaceCornerRadiusPx();
        /** Layout mode's model and wiring, which the editor lends its canvas and tray to. */
        @Nullable LayoutEditorController layoutEditor();
        /**
         * The wallpaper for the editor to paint inside the frame, or null where the launcher
         * already draws its own (not in passthrough mode) or cannot read the system's. Called off
         * the main thread.
         */
        @Nullable Drawable readEditorWallpaper();
        /** The dim the window's root paints over the system wallpaper. */
        int editorWallpaperDimColor();
        /**
         * Gives the window an opaque background for the editor's lifetime, so nothing of the
         * system wallpaper shows around the scaled frame; false hands passthrough back.
         */
        void setEditorWindowOpaque(boolean opaque);
    }

    @NonNull private final Host mHost;

    public SurfaceEditorController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isActive() {
        return mOpen;
    }

    /**
     * A view's top-left in {@code root}'s own unscaled coordinates — the space the editor's tap
     * targets are laid out in, which the frame's scale does not change. For the activity's half
     * of {@link Host#terminalFrameRectInRoot()}.
     */
    public static boolean offsetInRoot(@NonNull View view, @NonNull View root,
                                       @NonNull int[] out) {
        return AppearanceEditorFrame.offsetIn(view, root, out);
    }

    @Nullable
    private TermuxAppSharedPreferences prefs() {
        return mHost.preferences();
    }

    @Nullable
    private TermuxInAppKeyboard keyboard() {
        return mHost.inAppKeyboard();
    }

    @NonNull
    private SurfaceEditorScene scene() {
        return SurfaceEditorScene.of(mHost.placeLayout(), mHost.isInAppKeyboardShown(),
            mHost.isFloatingDock());
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------------------ session state

    private boolean mOpen;
    /** Whether the status pane was collapsed when the editor opened; restored on the way out. */
    private boolean mEntryStatusCollapsed;
    private boolean mHasEntryStatusCollapsed;
    /** The state at open: what Undo and Discard return to, and what "unsaved" is measured against. */
    @Nullable private AppearanceSnapshot mEntry;
    @Nullable private String mEntrySignature;
    private int mEntryStop = AppearanceLooks.CUSTOM_STOP;
    /** The Look slider's stop. */
    private int mStop = AppearanceLooks.CUSTOM_STOP;
    /** What a tap in the frame selected, or null; only ever set at the Custom stop. */
    @Nullable private Target mTarget;
    /** Whether the editor raised the in-app keyboard itself, and so owes taking it down. */
    private boolean mRaisedKeyboard;

    @Nullable private AppearanceEditorFrame mFrame;
    @Nullable private AppearanceEditorPanel mPanel;
    private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;
    private long mLayoutSignature = Long.MIN_VALUE;
    /** The panel's height at rest, and with row 2 up; recomputed with the frame. */
    private int mRestHeightPx;
    /** The panel's height in Layout mode: at least the resting one; recomputed with the frame. */
    private int mLayoutHeightPx;
    private int mNavInsetPx;

    /** The gap between the frame and the status inset above it, and the bottom area below it. */
    private static final int FRAME_GAP_DP = 8;
    /** The selection outline's stroke. */
    private static final int OUTLINE_STROKE_DP = 2;
    private static final long PANEL_MS = 240L;

    /**
     * The activity resumed with the editor open: the status pane keeps the shape the editor is
     * holding it in until the editor closes and hands it back.
     */
    public void collapseStatusPaneIfLeftExpanded() {
        if (!mOpen)
            return;
        applyStatusPaneForSelection(false);
    }

    // ------------------------------------------------------------------------------------ entry

    public void enter() {
        enter(null);
    }

    /**
     * Opens the editor in Layout mode on {@code place} (the place on screen for null): the Layout
     * glyph, the long-press sheet's Layout item, the Settings row and the Layout intent. Already
     * open, it moves to Layout mode and to that place, keeping the session.
     */
    public void enterLayout(@Nullable PaneWallPage place) {
        if (mOpen) {
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout != null) {
                pushLayoutShape(layout);
                layout.begin(place);
            }
            if (mFramed) setLayoutMode(true, true);
            else mOpenInLayout = true;
            return;
        }
        enter(null, true, place);
    }

    /** Whether the frame is showing the layout canvas (Layout mode) rather than the live render. */
    public boolean isLayoutMode() {
        return mOpen && mLayoutMode;
    }

    /**
     * Opens the editor over the place on screen. {@code initialSection} may name a surface (a
     * settings deep link); at the Custom stop that surface is selected, at a Look it is ignored so
     * that opening the editor never changes the look.
     */
    public void enter(@Nullable String initialSection) {
        enter(initialSection, false, null);
    }

    private void enter(@Nullable String initialSection, boolean layoutMode,
                       @Nullable PaneWallPage place) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        if (mOpen) {
            if (mLayoutMode) setLayoutMode(false, true);
            selectSection(initialSection);
            return;
        }
        View rootContainer = mHost.findView(R.id.terminal_root_container);
        ViewGroup content = mHost.findView(android.R.id.content);
        if (rootContainer == null || content == null)
            return;
        mOpen = true;
        mEntryStatusCollapsed = mHost.isTopStatusBarCollapsed();
        mHasEntryStatusCollapsed = true;
        mHost.holdPaneWall(true);
        mEntry = AppearanceSnapshot.capture(prefs);
        mEntrySignature = mEntry.signature();
        mStop = matchingStop();
        mEntryStop = mStop;
        mTarget = null;
        // The keyboard is one of the things the frame offers, so it has to be on screen to be
        // tapped. Raised once; the editor takes down only a keyboard it raised itself.
        mRaisedKeyboard = !mHost.isInAppKeyboardShown() && mHost.showInAppKeyboardForEditor();

        if (mFrame == null || mFrame.root() != rootContainer)
            mFrame = new AppearanceEditorFrame(rootContainer);
        if (mPanel == null) {
            mPanel = AppearanceEditorPanel.inflate(mHost.context(), content);
            mPanel.setListener(new PanelListener());
        }
        View panelView = mPanel.view();
        if (panelView.getParent() != content) {
            if (panelView.getParent() instanceof ViewGroup)
                ((ViewGroup) panelView.getParent()).removeView(panelView);
            content.addView(panelView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));
        }
        mLayoutMode = false;
        mOpenInLayout = layoutMode;
        mPanel.showAppearanceMode();
        mPanel.hideRow2();
        syncPanel();
        panelView.setVisibility(View.INVISIBLE);

        // The layout session opens with the editor, whichever mode it opens in, so the one Undo
        // and the one dirty state are measured from the same moment for both.
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null) {
            attachLayoutViews(content, layout);
            layout.setOnChangedListener(this::syncDirty);
            // The canvas draws its first frame under the Style and the Corners and Margin the
            // user already has.
            pushLayoutShape(layout);
            layout.begin(place);
        }

        bindTargets();
        setOverlayVisible(true);
        registerLayoutListener(content);
        mFramed = false;
        loadEditorWallpaper(rootContainer);
        content.post(() -> {
            if (!mOpen)
                return;
            frameSizeChanged();
            layoutFrame(true);
            mFramed = true;
            revealPanel();
            positionTargets();
            if (mOpenInLayout) {
                mOpenInLayout = false;
                // The canvas arrives once the live render has settled into the frame, so the
                // cross-fade reads as the same frame changing what it shows.
                setLayoutMode(true, true, AppearanceEditorFrame.ENTER_MS);
            } else {
                selectSection(initialSection);
            }
        });
    }

    // ------------------------------------------------------------------------- Layout mode

    /** Whether the frame is showing the layout canvas. */
    private boolean mLayoutMode;
    /** A Layout door opened the editor; Layout mode is shown once the frame is placed. */
    private boolean mOpenInLayout;
    /** The layout canvas's host: the frame's rect, over the scaled launcher. */
    @Nullable private FrameLayout mLayoutFrame;
    /** The views lent to Layout mode, built with the frame host. */
    @Nullable private LayoutEditorController.Views mLayoutViews;
    /** The frame's rect in the content view, as {@code {left, top, right, bottom}}. */
    @Nullable private int[] mFrameRectInContent;
    /** The frame's corner on screen: the device radius at the frame's scale. */
    private float mFrameCornerPx;
    private static final long LAYOUT_FADE_MS = 200L;

    /**
     * Lends Layout mode its views: the canvas in a frame host of its own over the scaled launcher,
     * and the bottom area's toggle and tray.
     */
    private void attachLayoutViews(@NonNull ViewGroup content,
                                   @NonNull LayoutEditorController layout) {
        AppearanceEditorPanel panel = mPanel;
        if (panel == null)
            return;
        FrameLayout frame = mLayoutFrame;
        if (frame == null) {
            View inflated = LayoutInflater.from(mHost.context())
                .inflate(R.layout.layout_editor_frame, content, false);
            if (!(inflated instanceof FrameLayout))
                return;
            frame = (FrameLayout) inflated;
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mFrameCornerPx);
                }
            });
            frame.setClipToOutline(true);
            mLayoutFrame = frame;
        }
        if (frame.getParent() != content) {
            if (frame.getParent() instanceof ViewGroup)
                ((ViewGroup) frame.getParent()).removeView(frame);
            // Over the activity's root, under the bottom area.
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(0, 0,
                Gravity.TOP | Gravity.START);
            int panelIndex = content.indexOfChild(panel.view());
            if (panelIndex >= 0) content.addView(frame, panelIndex, params);
            else content.addView(frame, params);
        }
        frame.animate().cancel();
        frame.setAlpha(1f);
        frame.setVisibility(View.GONE);
        if (mLayoutViews == null) {
            LayoutCanvasView canvas = frame.findViewById(R.id.layout_editor_canvas);
            ChipGroup forms = frame.findViewById(R.id.layout_editor_keyboard_forms);
            if (canvas == null || forms == null)
                return;
            // One set of views per process, like the panel: the controller binds them once.
            mLayoutViews = new LayoutEditorController.Views(frame, canvas, forms,
                panel.orientationToggle(), panel.tray(), panel.trayTrash(),
                panel.trayBadge());
        }
        layout.attach(mLayoutViews);
        positionLayoutFrame();
    }

    /** Stands the canvas's host exactly over the frame's rect, at the frame's corner. */
    private void positionLayoutFrame() {
        FrameLayout frame = mLayoutFrame;
        int[] rect = mFrameRectInContent;
        if (frame == null || rect == null)
            return;
        ViewGroup.LayoutParams raw = frame.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams))
            return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
        int width = Math.max(1, rect[2] - rect[0]);
        int height = Math.max(1, rect[3] - rect[1]);
        if (params.width != width || params.height != height || params.leftMargin != rect[0]
            || params.topMargin != rect[1]) {
            params.width = width;
            params.height = height;
            params.leftMargin = rect[0];
            params.topMargin = rect[1];
            params.gravity = Gravity.TOP | Gravity.START;
            frame.setLayoutParams(params);
        }
        frame.invalidateOutline();
        LayoutCanvasView canvas = frame.findViewById(R.id.layout_editor_canvas);
        if (canvas != null)
            canvas.setFrameCornerRadiusPx(mFrameCornerPx);
    }

    private void setLayoutMode(boolean layout, boolean animate) {
        setLayoutMode(layout, animate, 0L);
    }

    /**
     * Swaps what the frame and the bottom area are for. The frame stays put: the canvas's host
     * is the frame's own rect, so the cross-fade changes the picture and nothing moves.
     */
    private void setLayoutMode(boolean layout, boolean animate, long delayMs) {
        AppearanceEditorPanel panel = mPanel;
        if (!mOpen || panel == null)
            return;
        mLayoutMode = layout;
        panel.setMode(layout);
        applyPanelHeight();
        LayoutEditorController editor = mHost.layoutEditor();
        if (layout) {
            dismissClockDropdown();
            positionLayoutFrame();
            if (editor != null) {
                pushLayoutShape(editor);
                editor.sync();
            }
        } else {
            positionTargets();
        }
        fadeLayoutFrame(layout, animate, delayMs);
        syncDirty();
    }

    private void fadeLayoutFrame(boolean shown, boolean animate, long delayMs) {
        FrameLayout frame = mLayoutFrame;
        if (frame == null)
            return;
        frame.animate().cancel();
        boolean instant = !animate || ReducedMotion.isEnabled(mHost.context());
        if (shown) {
            boolean wasShown = frame.getVisibility() == View.VISIBLE;
            frame.setVisibility(View.VISIBLE);
            if (instant) {
                frame.setAlpha(1f);
                return;
            }
            if (!wasShown) frame.setAlpha(0f);
            frame.animate().alpha(1f).setStartDelay(delayMs).setDuration(LAYOUT_FADE_MS)
                .setInterpolator(Motion.settle()).start();
            return;
        }
        if (instant || frame.getVisibility() != View.VISIBLE) {
            frame.setAlpha(1f);
            frame.setVisibility(View.GONE);
            return;
        }
        frame.animate().alpha(0f).setStartDelay(0L).setDuration(LAYOUT_FADE_MS)
            .setInterpolator(Motion.settle())
            .withEndAction(() -> {
                if (mLayoutMode && mOpen)
                    return;
                frame.setVisibility(View.GONE);
                frame.setAlpha(1f);
            }).start();
    }

    // ------------------------------------------------------------------ the editor's wallpaper

    /** Which wallpaper load is current; bumped on every open and close. */
    private int mWallpaperToken;
    /** The picture the frame is painting for this session, or null for none. */
    @Nullable private Drawable mEditorWallpaper;

    /**
     * In passthrough mode the system draws the wallpaper behind the window, outside the frame's
     * scale. The picture is read off the main thread and painted inside the container for the
     * editor's lifetime, and only then is the window made opaque, so there is never a frame of
     * bare surface behind the translucent launcher.
     */
    private void loadEditorWallpaper(@NonNull View rootContainer) {
        final int token = ++mWallpaperToken;
        Thread reader = new Thread(() -> {
            Drawable wallpaper;
            try {
                wallpaper = mHost.readEditorWallpaper();
            } catch (RuntimeException e) {
                wallpaper = null;
            }
            if (wallpaper == null)
                return;
            final Drawable picture = wallpaper;
            rootContainer.post(() -> {
                if (!mOpen || token != mWallpaperToken)
                    return;
                mEditorWallpaper = picture;
                showEditorWallpaper(picture);
            });
        }, "appearance-editor-wallpaper");
        reader.setDaemon(true);
        reader.start();
    }

    private void showEditorWallpaper(@NonNull Drawable picture) {
        AppearanceEditorFrame frame = mFrame;
        if (frame == null)
            return;
        View root = frame.root();
        View decor = root.getRootView();
        int[] parentInDecor = new int[2];
        if (decor == null || !(root.getParent() instanceof View)
            || !AppearanceEditorFrame.offsetIn((View) root.getParent(), decor, parentInDecor))
            return;
        int rootLeft = parentInDecor[0] + root.getLeft();
        int rootTop = parentInDecor[1] + root.getTop();
        frame.showWallpaper(picture, mHost.editorWallpaperDimColor(), -rootLeft, -rootTop,
            decor.getWidth(), decor.getHeight());
        mHost.setEditorWindowOpaque(true);
    }

    private void selectSection(@Nullable String section) {
        Target target = Target.forSlot(slotForSectionKey(section));
        if (target != null && AppearanceLooks.isCustomStop(mStop))
            select(target);
    }

    /**
     * The surface a settings deep link targets, or null for a plain open. "sessions" and "other"
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

    /** The stop the live look sits on: the Look it matches, or Custom. */
    private int matchingStop() {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return AppearanceLooks.CUSTOM_STOP;
        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            if (SurfacePresets.matches(prefs, preset))
                return AppearanceLooks.stopForPresetId(preset.id);
        }
        return AppearanceLooks.CUSTOM_STOP;
    }

    // ------------------------------------------------------------------------------- the frame

    /**
     * Places the frame and sizes the bottom area: the bottom area at a fifth of the window (or
     * what its content needs, if more), the frame scaled to fit between the status inset and it.
     */
    private void layoutFrame(boolean animate) {
        AppearanceEditorFrame frame = mFrame;
        AppearanceEditorPanel panel = mPanel;
        ViewGroup content = mHost.findView(android.R.id.content);
        if (frame == null || panel == null || content == null || content.getHeight() <= 0)
            return;
        View root = frame.root();
        if (root.getHeight() <= 0 || !(root.getParent() instanceof View))
            return;
        int windowHeight = content.getHeight();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        Insets bars = insets == null ? Insets.NONE
            : insets.getInsets(WindowInsetsCompat.Type.systemBars());
        // The insets are the window's; the content view may already stand clear of either bar
        // (a window that fits them), so only the part of each bar over the content counts.
        int[] contentInWindow = new int[2];
        content.getLocationInWindow(contentInWindow);
        int decorHeight = content.getRootView().getHeight();
        mNavInsetPx = Math.max(0, contentInWindow[1] + windowHeight
            - (decorHeight - bars.bottom));
        int statusInset = Math.max(0, Math.max(bars.top, mHost.statusBarInsetTop())
            - contentInWindow[1]);
        View panelView = panel.view();
        panelView.setPadding(panelView.getPaddingLeft(), panelView.getPaddingTop(),
            panelView.getPaddingRight(), dp(12) + mNavInsetPx);
        panel.setNarrow(content.getWidth() < dp(AppearanceEditorPanel.NARROW_DP));
        // The resting height is Appearance's at rest, whichever mode is showing: the frame does
        // not move when the mode pill does. Layout mode's two rows may need a little more; that
        // grows the card upward over the frame's foot, as row 2 does, and never moves the frame.
        boolean layoutMode = panel.isLayoutMode();
        if (layoutMode) panel.setMode(false);
        boolean row2 = panel.isRow2Shown();
        if (row2) panel.hideRow2();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(content.getWidth(),
            View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        panelView.measure(widthSpec, heightSpec);
        int restMeasured = panelView.getMeasuredHeight();
        panel.setMode(true);
        panelView.measure(widthSpec, heightSpec);
        int layoutMeasured = panelView.getMeasuredHeight();
        panel.setMode(false);
        if (row2) panel.showRow2(nameOf(mTarget));
        if (layoutMode) panel.setMode(true);
        mRestHeightPx = Math.max(Math.round(windowHeight * 0.2f), restMeasured);
        mLayoutHeightPx = Math.max(mRestHeightPx, layoutMeasured);
        applyPanelHeight();

        int[] parentOffset = new int[2];
        if (!AppearanceEditorFrame.offsetIn((View) root.getParent(), content, parentOffset))
            return;
        int containerTop = parentOffset[1] + root.getTop();
        int containerLeft = parentOffset[0] + root.getLeft();
        int frameTop = Math.max(containerTop, statusInset) + dp(FRAME_GAP_DP);
        int frameBottom = windowHeight - mRestHeightPx - dp(FRAME_GAP_DP);
        float scale = AppearanceEditorFrame.fitScale(root.getHeight(), frameTop, frameBottom);
        frame.show(scale, frameTop - containerTop, animate);
        // Where the scaled container lands (pivot at its top centre): the rect Layout mode's
        // canvas stands in, at the same corner, so nothing jumps between the two modes.
        float scaledLeft = containerLeft + root.getWidth() * (1f - scale) / 2f;
        mFrameRectInContent = new int[] {Math.round(scaledLeft), frameTop,
            Math.round(scaledLeft + root.getWidth() * scale),
            Math.round(frameTop + root.getHeight() * scale)};
        mFrameCornerPx = AppearanceEditorFrame.deviceCornerRadiusPx(root) * scale;
        positionLayoutFrame();
        // A rotation moved the decor around the container: the picture is cropped to it again.
        if (mEditorWallpaper != null)
            showEditorWallpaper(mEditorWallpaper);
    }

    /**
     * The bottom area's one fixed height at rest, its taller state with row 2 up, and Layout
     * mode's own (its toggles and tray over Corners and Margin).
     */
    private void applyPanelHeight() {
        AppearanceEditorPanel panel = mPanel;
        if (panel == null || mRestHeightPx <= 0)
            return;
        int height = mLayoutMode ? Math.max(mRestHeightPx, mLayoutHeightPx)
            : mRestHeightPx + (panel.isRow2Shown() ? dp(panel.row2HeightDp()) : 0);
        ViewGroup.LayoutParams params = panel.view().getLayoutParams();
        if (params == null || params.height == height)
            return;
        params.height = height;
        panel.view().setLayoutParams(params);
    }

    private void revealPanel() {
        AppearanceEditorPanel panel = mPanel;
        if (panel == null)
            return;
        View view = panel.view();
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        int height = view.getLayoutParams() != null && view.getLayoutParams().height > 0
            ? view.getLayoutParams().height : mRestHeightPx;
        view.setTranslationY(height);
        view.animate().translationY(0f).setDuration(PANEL_MS)
            .setInterpolator(Motion.settle()).start();
    }

    // ------------------------------------------------------------------------ the bottom area

    /** Restates the bottom area from preferences and the editor's own state. */
    private void syncPanel() {
        AppearanceEditorPanel panel = mPanel;
        TermuxAppSharedPreferences prefs = prefs();
        if (panel == null || prefs == null)
            return;
        panel.setStop(mStop);
        panel.setFloating(mHost.isFloatingDock());
        syncLayoutControls();
        if (mTarget != null && AppearanceLooks.isCustomStop(mStop)) {
            showRow2(mTarget);
        } else {
            panel.hideRow2();
        }
        applyPanelHeight();
        syncDirty();
    }

    private void syncDirty() {
        if (mPanel == null)
            return;
        if (mSliderDragActive) {
            mDirtyDeferred = true;
            return;
        }
        mPanel.setDirty(isDirty());
    }

    @StringRes
    private static int nameOf(@Nullable Target target) {
        if (target == null)
            return R.string.appearance_editor_target_wallpaper;
        switch (target) {
            case STATUS: return R.string.appearance_editor_target_status;
            case TERMINAL: return R.string.appearance_editor_target_terminal;
            case DOCK: return R.string.appearance_editor_target_dock;
            case KEYBOARD: return R.string.appearance_editor_target_keyboard;
            default: return R.string.appearance_editor_target_wallpaper;
        }
    }

    /**
     * Row 2 for one element: its name and its controls, at the stored values. The terminal has
     * three — Darkness, Legibility, Blur; the status bar and the dock Blur alone; the keyboard Key
     * corners and Blur; the wallpaper Soft and Dim.
     */
    private void showRow2(@NonNull Target target) {
        AppearanceEditorPanel panel = mPanel;
        TermuxAppSharedPreferences prefs = prefs();
        if (panel == null || prefs == null)
            return;
        panel.showRow2(nameOf(target));
        switch (target) {
            case TERMINAL: {
                int darkness = prefs.getTerminalBackgroundOpacity();
                panel.setFirstSlider(getString(R.string.appearance_editor_darkness, darkness),
                    darkness, 100);
                break;
            }
            case KEYBOARD: {
                int corners = AppearanceLooks.keyCornersValueFor(
                    prefs.getInAppKeyboardKeyCornerRadiusDp());
                panel.setFirstSlider(getString(R.string.appearance_editor_key_corners, corners),
                    corners, AppearanceLooks.KEY_CORNERS_MAX_DP);
                break;
            }
            case WALLPAPER:
                panel.setSoft(getString(R.string.appearance_editor_soft),
                    SoftWallpaper.isOn(prefs));
                break;
            default:
                panel.hideFirst();
                break;
        }
        if (target.hasLegibility()) {
            // The palette Legibility changes is the Material one; with wallpaper colours off the
            // terminal wears a scheme file, which no contrast level moves (as in Settings).
            boolean palette = prefs.isTerminalDynamicColorsEnabled();
            int stop = AppearanceLooks.legibilityIndex(prefs.getTerminalContrastLevel());
            panel.setLegibility(palette ? panel.legibilityLabel(stop)
                    : getString(R.string.appearance_editor_legibility_unavailable),
                stop, palette);
        } else {
            panel.hideLegibility();
        }
        if (target == Target.WALLPAPER) {
            boolean soft = SoftWallpaper.isOn(prefs);
            int dim = AppearanceLooks.dimSliderValue(soft, prefs.getWallpaperBackdropDim());
            panel.setSecondSlider(getString(R.string.appearance_editor_dim, dim), dim,
                AppearanceLooks.dimSliderMax(soft));
        } else {
            int blur = AppearanceLooks.blurDp(prefs.getSurfaceBaseValue(SurfaceProperty.BLUR));
            panel.setSecondSlider(getString(R.string.appearance_editor_blur, blur), blur,
                AppearanceLooks.BLUR_MAX_DP);
        }
    }

    /** The bottom area's events, turned into writes. */
    private final class PanelListener implements AppearanceEditorPanel.Listener {
        @Override public void onModeChanged(boolean layout) {
            // One session for both modes: switching never asks and never resets anything.
            if (layout != mLayoutMode)
                setLayoutMode(layout, true);
        }

        @Override public void onUndo() {
            revertToEntry();
        }

        @Override public void onDone() {
            commitAndExit();
        }

        @Override public void onLookStop(int stop) {
            moveToStop(stop);
        }

        @Override public void onStyle(boolean floating) {
            TermuxAppSharedPreferences prefs = prefs();
            if (prefs == null)
                return;
            String style = floating ? TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_FLOATING
                : TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DOCKED;
            if (style.equals(prefs.getAppLauncherDockStyle()))
                return;
            // Style belongs to no Look: it never moves the slider.
            prefs.setAppLauncherDockStyle(style);
            // The same dress a launch gives: nothing decided under the old Style is carried over.
            mHost.redressChrome();
            applyStructuralPreview();
            // Corners and Margin show under both Styles and read the same keys.
            if (mPanel != null)
                mPanel.setFloating(floating);
            syncLayoutControls();
            // The canvas is told the new Style and morphs from the shape it stood in to the new
            // one (or jumps, with reduced motion).
            syncLayoutCanvas();
            syncDirty();
        }

        @Override public void onCorners(int value, boolean dragging) {
            beginDrag(dragging);
            writeCorners(value);
        }

        @Override public void onMargin(int value, boolean dragging) {
            beginDrag(dragging);
            writeMargin(value);
        }

        @Override public void onFirstSlider(int value, boolean dragging) {
            beginDrag(dragging);
            if (mTarget == Target.TERMINAL) writeDarkness(value);
            else if (mTarget == Target.KEYBOARD) writeKeyCorners(value);
        }

        @Override public void onFirstSegment(int index) {
            if (mTarget == Target.WALLPAPER) writeSoft(index == 1);
        }

        @Override public void onLegibility(int index) {
            if (mTarget != null && mTarget.hasLegibility()) writeLegibility(index);
        }

        @Override public void onSecondSlider(int value, boolean dragging) {
            beginDrag(dragging);
            if (mTarget == Target.WALLPAPER) writeDim(value);
            else if (mTarget != null) writeBlur(value);
        }

        @Override public void onSliderReleased() {
            endDrag();
        }
    }

    // ------------------------------------------------------------------------------ the slider

    /**
     * The Look slider landed on a stop. A Look applies at once; leaving Custom for one offers the
     * Custom values back. Custom brings back the saved Custom look, or, with none saved, keeps the
     * Look it was reached from as its seed.
     */
    private void moveToStop(int stop) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || stop == mStop)
            return;
        boolean leavingCustom = AppearanceLooks.isCustomStop(mStop);
        SurfacePresets.Preset look = AppearanceLooks.presetForStop(SurfacePresets.presets(), stop);
        mTarget = null;
        applyStatusPaneForSelection(true);
        if (look != null) {
            final AppearanceSnapshot replaced = leavingCustom ? AppearanceSnapshot.capture(prefs)
                : null;
            SurfacePresets.apply(prefs, look);
            mStop = stop;
            syncAfterBulkWrite();
            if (replaced != null && !replaced.signature().equals(
                    AppearanceSnapshot.signatureOf(prefs)))
                offerCustomBack(replaced);
            return;
        }
        SurfacePresets.Preset custom = SurfacePresets.custom(prefs);
        if (custom != null)
            SurfacePresets.apply(prefs, custom);
        mStop = AppearanceLooks.CUSTOM_STOP;
        syncAfterBulkWrite();
    }

    /** "Custom look replaced", with Undo putting the Custom values and the Custom stop back. */
    private void offerCustomBack(@NonNull AppearanceSnapshot replaced) {
        AppearanceEditorPanel panel = mPanel;
        if (panel == null)
            return;
        Snackbar snackbar = Snackbar.make(panel.view(), R.string.appearance_editor_custom_replaced,
            Snackbar.LENGTH_LONG);
        snackbar.setAnchorView(panel.view());
        snackbar.setAction(R.string.appearance_editor_undo, view -> {
            TermuxAppSharedPreferences prefs = prefs();
            if (!mOpen || prefs == null)
                return;
            replaced.restore(prefs);
            mStop = AppearanceLooks.CUSTOM_STOP;
            mTarget = null;
            syncAfterBulkWrite();
        });
        snackbar.show();
    }

    // ---------------------------------------------------------------------------- the controls

    /** Darkness: the terminal's own opacity, and the tint deepening past half. */
    private void writeDarkness(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int opacity = AppearanceLooks.darknessOpacity(value);
        // The terminal's own value: it leaves Base, so the other surfaces keep theirs.
        prefs.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, opacity);
        prefs.setTerminalBackgroundOpacity(opacity);
        prefs.setSurfaceGlassTint(AppearanceLooks.darknessTint(value));
        if (mPanel != null)
            mPanel.setFirstLabel(getString(R.string.appearance_editor_darkness, opacity));
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    /** Blur: one value for every surface. */
    private void writeBlur(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int blur = AppearanceLooks.blurDp(value);
        for (SurfaceSlot slot : SurfaceSlot.values())
            prefs.setSurfaceInheriting(slot, SurfaceProperty.BLUR, true);
        prefs.setInAppKeyboardBlurRadiusRaw(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BLUR_RADIUS);
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, blur);
        if (mPanel != null)
            mPanel.setSecondLabel(getString(R.string.appearance_editor_blur, blur));
        requestPreview(SurfaceEditorProperties.PREVIEW_BLUR
            | SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    /**
     * Legibility: the terminal palette's contrast, rebuilt and repainted at once so the panes'
     * colours change on the tap. It is also the one global multiplier every chrome band's veil is
     * bought against (SPEC §2), which the surfaces pass picks up.
     */
    private void writeLegibility(int index) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        String level = AppearanceLooks.legibilityAt(index).value;
        if (level.equals(prefs.getTerminalContrastLevel().value))
            return;
        prefs.setTerminalContrastLevel(level);
        mHost.refreshTerminalPalette();
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    /** Key corners: the key caps' radius, and nothing else. */
    private void writeKeyCorners(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int radius = AppearanceLooks.keyCornersDp(value);
        prefs.setInAppKeyboardKeyCornerRadiusDp(radius);
        TermuxInAppKeyboard keyboard = keyboard();
        if (keyboard != null)
            keyboard.previewSurfaceEditorKeyCornerRadiusDp(radius);
        if (mPanel != null)
            mPanel.setFirstLabel(getString(R.string.appearance_editor_key_corners, radius));
        requestPreview(SurfaceEditorProperties.PREVIEW_KEYBOARD);
    }

    /**
     * Corners: one radius for every surface and the terminal, as the old "All surfaces" Corners
     * wrote it. The terminal rounds by its own knob in either style, so the shared radius carries
     * it too — otherwise "round everything" leaves one square hole in the middle of the screen.
     */
    private void writeCorners(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int corners = AppearanceLooks.cornersDp(value);
        prefs.setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS, corners);
        prefs.setTerminalCornerRadius(corners);
        if (mPanel != null)
            mPanel.setCornersLabel(getString(R.string.appearance_editor_corners, corners));
        requestGeometryPreview();
        syncLayoutCanvas();
    }

    /**
     * Margin: one number for all the air on screen, as the old shared Margin wrote it. Under
     * Floating it is the air round the cards; under Docked it is the gutter of frame glass round
     * the insert. Both read the same side gap, so flipping Style keeps the number.
     */
    private void writeMargin(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int margin = AppearanceLooks.marginDp(value);
        prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, margin);
        prefs.setTerminalPaneGap(AppearanceLooks.terminalMarginDp(margin));
        if (mPanel != null)
            mPanel.setMarginLabel(getString(R.string.appearance_editor_margin, margin));
        requestGeometryPreview();
        syncLayoutCanvas();
    }

    /**
     * The shape pass for Corners and Margin: everything but a re-blur. Mid-drag the terminal is
     * not resized (a SIGWINCH per tick); the release commits it once.
     */
    private void requestGeometryPreview() {
        int scopes = SurfaceEditorProperties.PREVIEW_ALL & ~SurfaceEditorProperties.PREVIEW_BLUR;
        if (mSliderDragActive) mDragTouchedGeometry = true;
        else scopes |= SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT;
        requestPreview(scopes);
    }

    /** Layout mode's Corners and Margin, restated from preferences. */
    private void syncLayoutControls() {
        AppearanceEditorPanel panel = mPanel;
        TermuxAppSharedPreferences prefs = prefs();
        if (panel == null || prefs == null)
            return;
        int corners = AppearanceLooks.cornersDp(
            prefs.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
        panel.setCorners(getString(R.string.appearance_editor_corners, corners), corners,
            AppearanceLooks.CORNERS_MAX_DP);
        int margin = AppearanceLooks.marginValueFor(mHost.isFloatingDock(),
            prefs.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP), prefs.getTerminalPaneGap());
        panel.setMargin(getString(R.string.appearance_editor_margin, margin), margin,
            AppearanceLooks.MARGIN_MAX_DP);
    }

    /**
     * The layout canvas re-read, while it is showing: it draws every shape from the shape model,
     * asked under the stored Style with the user's Corners and Margin ({@link #pushLayoutShape}).
     */
    private void syncLayoutCanvas() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (mOpen && mLayoutMode && layout != null) {
            pushLayoutShape(layout);
            layout.sync();
        }
    }

    /**
     * Tells Layout mode the Style, Corners and Margin it draws its shapes from, as preferences
     * hold them now. Margin is the side gap, spent as air under Floating and as the gutter under
     * Docked.
     */
    private void pushLayoutShape(@NonNull LayoutEditorController layout) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        layout.setShape(prefs.getLayoutStyle(),
            AppearanceLooks.cornersDp(prefs.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS)),
            AppearanceLooks.marginDp(prefs.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP)));
    }

    /** Soft wallpaper: its fixed dim under whatever Dim adds, and its fixed blur. */
    private void writeSoft(boolean on) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        boolean was = SoftWallpaper.isOn(prefs);
        if (was == on)
            return;
        int extra = AppearanceLooks.dimSliderValue(was, prefs.getWallpaperBackdropDim());
        SoftWallpaper.set(prefs, on);
        prefs.setWallpaperBackdropDim(AppearanceLooks.storedDim(on, extra));
        SoftWallpaper.apply(mHost.findView(R.id.wallpaper_backdrop), on);
        if (mPanel != null) {
            int dim = AppearanceLooks.dimSliderValue(on, prefs.getWallpaperBackdropDim());
            mPanel.setSecondSlider(getString(R.string.appearance_editor_dim, dim), dim,
                AppearanceLooks.dimSliderMax(on));
        }
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    /** Dim: the wallpaper's dim, counted above Soft's own share while Soft is on. */
    private void writeDim(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        boolean soft = SoftWallpaper.isOn(prefs);
        prefs.setWallpaperBackdropDim(AppearanceLooks.storedDim(soft, value));
        if (mPanel != null)
            mPanel.setSecondLabel(getString(R.string.appearance_editor_dim,
                AppearanceLooks.dimSliderValue(soft, prefs.getWallpaperBackdropDim())));
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    // ------------------------------------------------------------------------- the selection

    /**
     * A tap in the frame. At a Look the slider moves to Custom first, seeded from that Look — the
     * live values are the Look's, so nothing is written — and then the element opens in row 2.
     */
    private void select(@NonNull Target target) {
        if (!mOpen)
            return;
        if (target.slot != null && !scene().offersSurface(target.slot))
            return;
        if (!AppearanceLooks.isCustomStop(mStop))
            mStop = AppearanceLooks.CUSTOM_STOP;
        mTarget = target;
        applyStatusPaneForSelection(true);
        syncPanel();
        positionOutline();
        positionClockHandle();
    }

    /**
     * The status pane's shape while the editor is open: opened while it is the element being
     * tuned, so its clock and its ▾ are there to be reached; otherwise as it was at open.
     */
    private void applyStatusPaneForSelection(boolean animate) {
        if (prefs() == null || !mOpen || !mHasEntryStatusCollapsed)
            return;
        boolean collapsed = mTarget != Target.STATUS && mEntryStatusCollapsed;
        if (mHost.isTopStatusBarCollapsed() != collapsed)
            mHost.setTopStatusBarCollapsed(collapsed, animate);
    }

    // ---------------------------------------------------------------------- the frame's targets

    private static final int[] GROUP_IDS = {
        R.id.surface_tuning_wallpaper_gesture_group,
        R.id.surface_tuning_canvas_gesture_group,
        R.id.surface_tuning_status_gesture_group,
        R.id.surface_tuning_dock_gesture_group,
        R.id.surface_tuning_keyboard_gesture_group};

    private static final Target[] GROUP_TARGETS = {
        Target.WALLPAPER, Target.TERMINAL, Target.STATUS, Target.DOCK, Target.KEYBOARD};

    /**
     * Every group consumes its touches, so nothing under the frame — the terminal, the dock's
     * apps, the keyboard's keys — receives them while the editor is up; a tap selects. The
     * wallpaper group fills the frame under the rest, so a touch between elements lands on it.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void bindTargets() {
        for (int i = 0; i < GROUP_IDS.length; i++) {
            View group = mHost.findView(GROUP_IDS[i]);
            if (group == null)
                continue;
            final Target target = GROUP_TARGETS[i];
            group.setContentDescription(getString(R.string.appearance_editor_target_description,
                getString(nameOf(target))));
            group.setOnClickListener(view -> select(target));
            group.setOnTouchListener((view, event) -> {
                if (!mOpen)
                    return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_UP:
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        float x = event.getX();
                        float y = event.getY();
                        if (x >= 0 && y >= 0 && x <= view.getWidth() && y <= view.getHeight())
                            view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    default:
                        return true;
                }
            });
        }
        View handle = mHost.findView(R.id.surface_tuning_status_clock_handle);
        if (handle != null) {
            handle.setContentDescription(getString(R.string.termux_surface_tuning_clock_open));
            handle.setOnClickListener(this::showClockDropdown);
        }
    }

    private void setOverlayVisible(boolean visible) {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null)
            return;
        overlay.animate().cancel();
        if (visible) {
            overlay.setAlpha(1f);
            overlay.setVisibility(View.VISIBLE);
            return;
        }
        overlay.animate().alpha(0f)
            .setDuration(SurfaceEditorMotion.overlayFadeMs(GlassMotion.of(prefs()), false))
            .setInterpolator(Motion.settle())
            .withEndAction(() -> {
                overlay.setVisibility(View.GONE);
                overlay.setAlpha(1f);
            }).start();
    }

    /** Where an element stands in the overlay's space, or null when it is not on screen. */
    @Nullable
    private int[] rectOf(@NonNull Target target, @NonNull View overlay) {
        View root = mHost.findView(R.id.terminal_root_container);
        if (root == null)
            return null;
        int[] overlayOffset = new int[2];
        if (!AppearanceEditorFrame.offsetIn(overlay, root, overlayOffset))
            return null;
        if (target == Target.WALLPAPER)
            return new int[] {0, 0, overlay.getWidth(), overlay.getHeight()};
        if (target == Target.TERMINAL) {
            int[] frame = mHost.terminalFrameRectInRoot();
            if (frame == null || frame[2] - frame[0] < dp(24) || frame[3] - frame[1] < dp(24))
                return null;
            return new int[] {frame[0] - overlayOffset[0], frame[1] - overlayOffset[1],
                frame[2] - overlayOffset[0], frame[3] - overlayOffset[1]};
        }
        View surface = anchorViewFor(target.slot);
        if (surface == null || surface.getVisibility() != View.VISIBLE
            || surface.getWidth() <= 0 || surface.getHeight() <= 0)
            return null;
        int[] offset = new int[2];
        if (!AppearanceEditorFrame.offsetIn(surface, root, offset))
            return null;
        int left = offset[0] - overlayOffset[0];
        int top = offset[1] - overlayOffset[1];
        return new int[] {left, top, left + surface.getWidth(), top + surface.getHeight()};
    }

    @Nullable
    private View anchorViewFor(@Nullable SurfaceSlot slot) {
        if (slot == null || !scene().offersSurface(slot))
            return null;
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

    /** Lays every tap target over its element, then the outline and the clock's ▾. */
    private void positionTargets() {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null || !mOpen || overlay.getWidth() <= 0)
            return;
        for (int i = 0; i < GROUP_IDS.length; i++) {
            View group = mHost.findView(GROUP_IDS[i]);
            if (group == null)
                continue;
            int[] rect = rectOf(GROUP_TARGETS[i], overlay);
            if (rect == null) {
                group.setVisibility(View.GONE);
                continue;
            }
            placeInOverlay(group, overlay, rect);
            group.setVisibility(View.VISIBLE);
        }
        positionOutline();
        positionClockHandle();
    }

    private static void placeInOverlay(@NonNull View view, @NonNull View overlay,
                                       @NonNull int[] rect) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams))
            return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        int left = rect[0];
        int top = rect[1];
        int height = Math.max(1, rect[3] - rect[1]);
        int right = overlay.getWidth() - rect[2];
        if (margins.leftMargin == left && margins.rightMargin == right
            && margins.topMargin == top && margins.height == height)
            return;
        margins.leftMargin = left;
        margins.rightMargin = right;
        margins.topMargin = top;
        margins.height = height;
        view.setLayoutParams(margins);
    }

    /**
     * The selection: one 2dp colorPrimary outline on the tapped element, drawn just outside its
     * edge. Nothing else glows.
     */
    private void positionOutline() {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        View outline = mHost.findView(R.id.surface_editor_selection_outline);
        if (overlay == null || outline == null)
            return;
        Target target = mOpen && AppearanceLooks.isCustomStop(mStop) ? mTarget : null;
        int[] rect = target == null ? null : rectOf(target, overlay);
        if (rect == null) {
            outline.setVisibility(View.GONE);
            return;
        }
        int stroke = Math.max(1, dp(OUTLINE_STROKE_DP));
        // The wallpaper's outline traces the frame just inside its clip; every other one stands
        // just outside its element, so the stroke never covers what it points at.
        int grow = target == Target.WALLPAPER ? -stroke : stroke;
        int[] grown = {rect[0] - grow, rect[1] - grow, rect[2] + grow, rect[3] + grow};
        placeInOverlay(outline, overlay, grown);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0);
        ring.setStroke(stroke, mHost.themeColor(com.google.android.material.R.attr.colorPrimary,
            R.color.termux_primary));
        ring.setCornerRadius(Math.max(0f, outlineRadiusPx(target) + grow));
        outline.setBackground(ring);
        outline.setVisibility(View.VISIBLE);
    }

    /** The radius the outline wears: the element's own, so it reads as that element's edge. */
    private float outlineRadiusPx(@NonNull Target target) {
        TermuxAppSharedPreferences prefs = prefs();
        switch (target) {
            case STATUS:
                return prefs == null ? 0f : dpToPx(clamp(prefs.getStatusBarCornerRadius(), 0, 40));
            case DOCK:
                return prefs == null ? 0f
                    : dpToPx(clamp(prefs.getAppLauncherDockCornerRadius(), 0, 40));
            case KEYBOARD:
                return mHost.keyboardSurfaceCornerRadiusPx();
            case TERMINAL:
                return mHost.terminalFrameCornerRadiusPx();
            default: {
                View root = mHost.findView(R.id.terminal_root_container);
                return root == null ? 0f : AppearanceEditorFrame.deviceCornerRadiusPx(root);
            }
        }
    }

    /**
     * Lays the clock's tap target over the live clock, with the ▾ at its trailing edge, while the
     * status bar is the element being tuned — the only time the bar is open far enough to show it.
     */
    private void positionClockHandle() {
        View handle = mHost.findView(R.id.surface_tuning_status_clock_handle);
        if (handle == null)
            return;
        View clock = mHost.findView(R.id.terminal_clock_widget);
        View group = mHost.findView(R.id.surface_tuning_status_gesture_group);
        boolean wanted = mOpen && mTarget == Target.STATUS && group != null && clock != null
            && clock.getVisibility() == View.VISIBLE && clock.getWidth() > 0
            && clock.getHeight() > 0;
        View root = mHost.findView(R.id.terminal_root_container);
        int[] groupOffset = new int[2];
        int[] clockOffset = new int[2];
        if (wanted && (root == null || !AppearanceEditorFrame.offsetIn(group, root, groupOffset)
                || !AppearanceEditorFrame.offsetIn(clock, root, clockOffset)))
            wanted = false;
        if (!wanted) {
            if (handle.getVisibility() != View.GONE)
                handle.setVisibility(View.GONE);
            return;
        }
        // The clock view fills its slot; the widget alone knows where the digits start and stop.
        int paintedLeft = clock instanceof TerminalClockWidget
            ? Math.round(((TerminalClockWidget) clock).paintedLeftPx()) : 0;
        int paintedRight = clock instanceof TerminalClockWidget
            ? Math.round(((TerminalClockWidget) clock).paintedRightPx()) : clock.getWidth();
        int chevronPx = dp(CLOCK_HANDLE_CHEVRON_DP);
        int height = Math.max(dp(CLOCK_HANDLE_MIN_HEIGHT_DP), clock.getHeight());
        int left = clamp((clockOffset[0] - groupOffset[0]) + paintedLeft, 0,
            Math.max(0, group.getWidth() - chevronPx));
        int right = clamp((clockOffset[0] - groupOffset[0]) + paintedRight + chevronPx,
            left + chevronPx, group.getWidth());
        int top = Math.max(0, (clockOffset[1] - groupOffset[1]) + (clock.getHeight() - height) / 2);
        ViewGroup.LayoutParams params = handle.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            int width = right - left;
            if (margins.leftMargin != left || margins.topMargin != top
                || margins.width != width || margins.height != height) {
                margins.leftMargin = left;
                margins.topMargin = top;
                margins.width = width;
                margins.height = height;
                handle.setLayoutParams(margins);
            }
        }
        if (handle.getVisibility() != View.VISIBLE)
            handle.setVisibility(View.VISIBLE);
    }

    /** Room the ▾ takes past the clock's trailing edge, inside the tap target. */
    private static final int CLOCK_HANDLE_CHEVRON_DP = 28;
    /** The tap target never gets shorter than the ▾ glyph's 28dp box. */
    private static final int CLOCK_HANDLE_MIN_HEIGHT_DP = 28;

    private void registerLayoutListener(@NonNull View host) {
        if (mLayoutListener != null)
            return;
        mLayoutSignature = Long.MIN_VALUE;
        mLayoutListener = () -> {
            if (!mOpen)
                return;
            // Global layout fires for every text change a slider tick causes; the targets only
            // care when something they are laid over actually moved.
            long signature = computeLayoutSignature();
            if (signature == mLayoutSignature)
                return;
            mLayoutSignature = signature;
            // Refit only once the opening animation has placed the frame, and only when the
            // frame's own size moved: a rotation or a window resize.
            if (mFramed && frameSizeChanged())
                layoutFrame(false);
            // A rotation or a place change can take the selected element off the screen.
            if (mTarget != null && mTarget.slot != null && !scene().offersSurface(mTarget.slot)) {
                mTarget = null;
                syncPanel();
            }
            positionTargets();
        };
        host.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
    }

    /** Whether the opening pass has placed the frame; the layout listener refits only after. */
    private boolean mFramed;
    private int mLastRootWidth;
    private int mLastRootHeight;
    private int mLastContentHeight;

    /** Whether the frame's own size moved (a rotation, a window resize), so it is refit. */
    private boolean frameSizeChanged() {
        View root = mHost.findView(R.id.terminal_root_container);
        View content = mHost.findView(android.R.id.content);
        if (root == null || content == null)
            return false;
        boolean changed = root.getWidth() != mLastRootWidth || root.getHeight() != mLastRootHeight
            || content.getHeight() != mLastContentHeight;
        mLastRootWidth = root.getWidth();
        mLastRootHeight = root.getHeight();
        mLastContentHeight = content.getHeight();
        return changed;
    }

    private long computeLayoutSignature() {
        View root = mHost.findView(R.id.terminal_root_container);
        View content = mHost.findView(android.R.id.content);
        long signature = root == null ? -1 : root.getWidth();
        signature = mix(signature, root == null ? -1 : root.getHeight());
        signature = mix(signature, content == null ? -1 : content.getHeight());
        signature = mix(signature, rectSignature(mHost.findView(R.id.terminal_window_bar_host)));
        signature = mix(signature, rectSignature(mHost.findView(R.id.accessory_surface_host)));
        signature = mix(signature, rectSignature(mHost.isInAppKeyboardShown()
            ? mHost.findView(R.id.inapp_keyboard_view_host) : null));
        signature = mix(signature, rectSignature(mHost.findView(R.id.terminal_clock_widget)));
        int[] frame = mHost.terminalFrameRectInRoot();
        for (int edge : frame == null ? new int[] {-1} : frame)
            signature = mix(signature, edge);
        signature = mix(signature, mHost.isFloatingDock() ? 1 : 0);
        return mix(signature, scene().signature());
    }

    private final int[] mTmpOffset = new int[2];

    private long rectSignature(@Nullable View view) {
        View root = mHost.findView(R.id.terminal_root_container);
        if (view == null || root == null || view.getVisibility() != View.VISIBLE
            || !AppearanceEditorFrame.offsetIn(view, root, mTmpOffset))
            return -1;
        long signature = mTmpOffset[0];
        signature = mix(signature, mTmpOffset[1]);
        signature = mix(signature, view.getWidth());
        return mix(signature, view.getHeight());
    }

    private static long mix(long signature, long value) {
        return signature * 1_000_003L + value;
    }

    private void unregisterLayoutListener() {
        if (mLayoutListener == null)
            return;
        View content = mHost.findView(android.R.id.content);
        if (content != null)
            content.getViewTreeObserver().removeOnGlobalLayoutListener(mLayoutListener);
        mLayoutListener = null;
    }

    // ------------------------------------------------------------------------- the clock face
    //
    // The status bar's one control that is a look rather than a number: the live clock itself,
    // marked with a ▾, dropping the six faces under itself drawn as themselves, with the face's
    // position beneath them. Live like every other editor control, and gated by Done.

    /** Package-private so a test can hold it against the settings list's own entry values. */
    static final String[] CLOCK_STYLES = {
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB};

    /** Package-private so a test can hold it against the settings list's own segment values. */
    static final String[] CLOCK_ALIGNMENTS = {
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT};

    /** Same fallback the widget itself applies to an unknown stored value. */
    @StringRes
    static int clockAlignmentLabel(@Nullable String alignment) {
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER.equals(alignment))
            return R.string.settings_clock_alignment_center;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT.equals(alignment))
            return R.string.settings_clock_alignment_right;
        return R.string.settings_clock_alignment_left;
    }

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
        View divider = new View(context);
        divider.setBackgroundColor(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOutlineVariant, R.color.termux_outline_variant));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        dividerParams.setMargins(0, dp(4), 0, dp(4));
        divider.setLayoutParams(dividerParams);
        column.addView(divider);
        column.addView(clockPositionRow(context));
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
                              @NonNull String current, @NonNull Runnable onPicked) {
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

    /**
     * Where the face sits in the pane — left, centre or right — as three pills under the faces.
     * The row stays open for a second look instead of dismissing like a face pick does.
     */
    @NonNull
    private View clockPositionRow(@NonNull Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(0, dp(4), 0, dp(4));
        row.setContentDescription(getString(R.string.settings_clock_alignment_title));
        final TextView[] pills = new TextView[CLOCK_ALIGNMENTS.length];
        for (int i = 0; i < CLOCK_ALIGNMENTS.length; i++) {
            final String alignment = CLOCK_ALIGNMENTS[i];
            TextView pill = new TextView(context);
            pill.setText(clockAlignmentLabel(alignment));
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pill.setGravity(Gravity.CENTER);
            pill.setMaxLines(1);
            pill.setEllipsize(TextUtils.TruncateAt.END);
            pill.setMinimumHeight(dp(36));
            pill.setPadding(dp(8), 0, dp(8), 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(i == 0 ? 0 : dp(6), 0, 0, 0);
            pill.setLayoutParams(params);
            pill.setClickable(true);
            pill.setFocusable(true);
            pill.setContentDescription(getString(
                R.string.termux_surface_tuning_clock_position_description,
                getString(clockAlignmentLabel(alignment))));
            pill.setOnClickListener(view -> {
                pickClockAlignment(alignment);
                for (int j = 0; j < pills.length; j++)
                    styleClockPositionPill(pills[j], CLOCK_ALIGNMENTS[j].equals(alignment));
            });
            pills[i] = pill;
            row.addView(pill);
        }
        String current = prefs() == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_ALIGNMENT
            : prefs().getTopPaneClockAlignment();
        for (int i = 0; i < pills.length; i++)
            styleClockPositionPill(pills[i], CLOCK_ALIGNMENTS[i].equals(current));
        return row;
    }

    /** A pill is filled with the accent container when chosen and outlined when not. */
    private void styleClockPositionPill(@NonNull TextView pill, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dpToPx(18));
        if (selected) {
            shape.setColor(mHost.themeColor(
                com.termux.shared.R.attr.termuxColorAccentContainer,
                R.color.termux_accent_container));
            pill.setTextColor(mHost.themeColor(
                com.termux.shared.R.attr.termuxColorOnAccentContainer,
                R.color.termux_on_accent_container));
        } else {
            shape.setColor(0);
            shape.setStroke(Math.max(1, dp(1)), mHost.themeColor(
                com.termux.shared.R.attr.termuxColorOutlineVariant,
                R.color.termux_outline_variant));
            pill.setTextColor(mHost.themeColor(
                com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface));
        }
        pill.setBackground(shape);
        final boolean isSelected = selected;
        androidx.core.view.ViewCompat.setAccessibilityDelegate(pill,
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
    }

    private void pickClockAlignment(@NonNull String alignment) {
        if (prefs() == null || alignment.equals(prefs().getTopPaneClockAlignment()))
            return;
        prefs().setTopPaneClockAlignment(alignment);
        mHost.refreshTerminalWindowBar();
        syncDirty();
    }

    private void pickClockStyle(@NonNull String style) {
        if (prefs() == null || style.equals(prefs().getTopPaneClockStyle()))
            return;
        prefs().setTopPaneClockStyle(style);
        mHost.refreshTerminalWindowBar();
        syncDirty();
    }

    // ------------------------------------------------------------------------- unsaved and exit

    /** One dirty state for the whole editor: the look or the arrangement moved since open. */
    private boolean isDirty() {
        TermuxAppSharedPreferences prefs = prefs();
        boolean look = prefs != null && mEntrySignature != null
            && !mEntrySignature.equals(AppearanceSnapshot.signatureOf(prefs));
        LayoutEditorController layout = mHost.layoutEditor();
        return look || (layout != null && layout.isDirty());
    }

    /** Undo: everything — the look and the arrangement — back to the state at open. */
    private void revertToEntry() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null && layout.isDirty())
            layout.revert();
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || mEntry == null)
            return;
        mEntry.restore(prefs);
        mStop = mEntryStop;
        mTarget = null;
        applyStatusPaneForSelection(true);
        syncAfterBulkWrite();
    }

    /** Done: at the Custom stop the look is saved as Custom, so the stop brings it back. */
    private void commitAndExit() {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs != null && AppearanceLooks.isCustomStop(mStop))
            SurfacePresets.saveCustom(prefs);
        exitEditor();
    }

    /**
     * The back press, in either mode. Nothing to lose: the editor closes. Otherwise it asks —
     * Keep editing / Discard / Save — once for the look and the arrangement together, rather than
     * choosing for the user; the live write-through means "leave" would otherwise mean "keep" by
     * accident.
     */
    public void requestClose() {
        if (!mOpen)
            return;
        if (!isDirty()) {
            exitEditor();
            return;
        }
        new MaterialAlertDialogBuilder(mHost.context())
            .setTitle(R.string.termux_surface_tuning_unsaved_title)
            .setMessage(R.string.termux_layout_editor_unsaved_message)
            .setNeutralButton(R.string.termux_surface_tuning_unsaved_keep_editing, null)
            .setNegativeButton(R.string.termux_surface_tuning_unsaved_discard,
                (dialog, which) -> {
                    revertToEntry();
                    exitEditor();
                })
            .setPositiveButton(R.string.termux_surface_tuning_unsaved_save,
                (dialog, which) -> commitAndExit())
            .show();
    }

    /** Leaves the editor from outside a Back press — a HOME press — through the same rule. */
    public void requestExit() {
        requestClose();
    }

    /** The phone turned: Layout mode's canvas default and next write turn with it. */
    public void onPlaceOrientationChanged() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (mOpen && layout != null)
            layout.onPlaceOrientationChanged();
    }

    private void exitEditor() {
        if (!mOpen)
            return;
        dismissClockDropdown();
        mOpen = false;
        mSliderDragActive = false;
        mEntry = null;
        mEntrySignature = null;
        mTarget = null;
        mOpenInLayout = false;
        mWallpaperToken++;
        mEditorWallpaper = null;
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null) {
            layout.end();
            layout.setOnChangedListener(null);
        }
        boolean wasLayout = mLayoutMode;
        mLayoutMode = false;
        fadeLayoutFrame(false, wasLayout, 0L);
        mHost.holdPaneWall(false);
        setOverlayVisible(false);
        unregisterLayoutListener();
        View outline = mHost.findView(R.id.surface_editor_selection_outline);
        if (outline != null)
            outline.setVisibility(View.GONE);
        View clockHandle = mHost.findView(R.id.surface_tuning_status_clock_handle);
        if (clockHandle != null)
            clockHandle.setVisibility(View.GONE);
        if (mRaisedKeyboard)
            mHost.hideInAppKeyboardForEditor();
        mRaisedKeyboard = false;
        final AppearanceEditorPanel panel = mPanel;
        if (panel != null) {
            View view = panel.view();
            view.animate().cancel();
            view.animate().translationY(Math.max(view.getHeight(), mRestHeightPx))
                .setDuration(AppearanceEditorFrame.EXIT_MS).setInterpolator(Motion.settle())
                .withEndAction(() -> {
                    if (mOpen)
                        return;
                    view.setVisibility(View.GONE);
                    view.setTranslationY(0f);
                    if (view.getParent() instanceof ViewGroup)
                        ((ViewGroup) view.getParent()).removeView(view);
                })
                .start();
        }
        if (mFrame != null) {
            final AppearanceEditorFrame frame = mFrame;
            // The editor's wallpaper and the opaque window stay until the launcher is back at
            // full size, where the system's wallpaper lines up with it again.
            frame.hide(true, () -> {
                if (mOpen)
                    return;
                frame.hideWallpaper();
                mHost.setEditorWindowOpaque(false);
                // Whatever the session did, the chrome leaves it dressed as a launch would — and
                // only now, with the root back at identity: a pass run while it was still scaled
                // aimed every glass surface at the scaled frame, and nothing draws them again.
                mHost.redressChrome();
                frame.repaintAll();
            });
        } else {
            mHost.redressChrome();
        }
        restoreExpandedStatusAfterSurfaceEditor();
        mHasEntryStatusCollapsed = false;
    }

    /** Hands the status pane back the shape it had before the editor borrowed it. */
    public void restoreExpandedStatusAfterSurfaceEditor() {
        if (prefs() == null || !mHasEntryStatusCollapsed)
            return;
        if (mHost.isTopStatusBarCollapsed() != mEntryStatusCollapsed)
            mHost.setTopStatusBarCollapsed(mEntryStatusCollapsed, false);
    }

    // -------------------------------------------------------------------------- the preview pass
    //
    // Sliders fire far faster than a full re-apply fits in a frame, so requests carry only the
    // scopes their control touches and are coalesced to a single apply per animation frame.

    private int mPendingPreviewScopes;
    private boolean mPreviewScheduled;
    private final Runnable mPreviewRunnable = this::runPendingPreview;
    /** True while a row-2 slider thumb is down; heavy per-tick work waits for the release. */
    private boolean mSliderDragActive;
    private boolean mDragTouchedBlur;
    private boolean mDragTouchedKeyboard;
    /** A Corners or Margin drag moved the shape; the release resizes the terminal once. */
    private boolean mDragTouchedGeometry;
    private boolean mDirtyDeferred;

    private void beginDrag(boolean dragging) {
        mSliderDragActive = dragging;
    }

    private void endDrag() {
        mSliderDragActive = false;
        if (mDragTouchedGeometry) {
            mDragTouchedGeometry = false;
            requestPreview(SurfaceEditorProperties.PREVIEW_GEOMETRY
                | SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT);
        }
        if (mDragTouchedBlur) {
            mDragTouchedBlur = false;
            requestPreview(SurfaceEditorProperties.PREVIEW_BLUR);
        }
        if (mDragTouchedKeyboard) {
            mDragTouchedKeyboard = false;
            requestPreview(SurfaceEditorProperties.PREVIEW_KEYBOARD);
        }
        if (mDirtyDeferred) {
            mDirtyDeferred = false;
            syncDirty();
        }
    }

    private void requestPreview(int scopes) {
        if (scopes == 0) {
            syncDirty();
            return;
        }
        if (mSliderDragActive && (scopes & SurfaceEditorProperties.PREVIEW_BLUR) != 0) {
            // A re-blur is the frame a drag can least afford; the release settles it once.
            mDragTouchedBlur = true;
            scopes &= ~SurfaceEditorProperties.PREVIEW_BLUR;
        }
        mPendingPreviewScopes |= scopes | SurfaceEditorProperties.PREVIEW_GLASS;
        if (mPreviewScheduled)
            return;
        View root = mHost.findView(R.id.activity_termux_root_view);
        if (root == null) {
            runPendingPreview();
            return;
        }
        mPreviewScheduled = true;
        root.postOnAnimation(mPreviewRunnable);
    }

    private void runPendingPreview() {
        mPreviewScheduled = false;
        int scopes = mPendingPreviewScopes;
        mPendingPreviewScopes = 0;
        if (scopes == 0 || prefs() == null)
            return;
        if ((scopes & (SurfaceEditorProperties.PREVIEW_GEOMETRY
                | SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT)) != 0)
            mHost.applyGeometryPreview(
                (scopes & SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT) != 0);
        if ((scopes & SurfaceEditorProperties.PREVIEW_SURFACES) != 0) {
            mHost.applyTerminalSurfaceAppearance();
            mHost.refreshTerminalWindowBar();
        }
        if ((scopes & SurfaceEditorProperties.PREVIEW_KEYBOARD) != 0 && keyboard() != null) {
            if (mSliderDragActive) mDragTouchedKeyboard = true;
            else keyboard().onPreferencesReloaded();
        }
        mHost.applyGlassPreview();
        if (mOpen && !mSliderDragActive)
            positionOutline();
        syncDirty();
    }

    /** The broad re-apply for a change of Style, a Look, or an Undo. */
    private void applyStructuralPreview() {
        requestPreview(SurfaceEditorProperties.PREVIEW_ALL
            | SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT);
    }

    /** Restates everything after a bulk write: a Look, Custom, an Undo. */
    private void syncAfterBulkWrite() {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        mHost.refreshPaneLayout();
        mHost.applyTerminalSurfaceAppearance();
        mHost.refreshTerminalWindowBar();
        mHost.refreshTerminalPalette();
        SoftWallpaper.apply(mHost.findView(R.id.wallpaper_backdrop), prefs);
        if (keyboard() != null)
            keyboard().onPreferencesReloaded();
        applyStructuralPreview();
        syncPanel();
        // An Undo can move Style or Corners under a showing canvas.
        syncLayoutCanvas();
        positionOutline();
        positionClockHandle();
    }
}
