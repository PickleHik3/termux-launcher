package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.BaseAdapter;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.LabelFormatter;

import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.KeyboardColorSchemeFragment;
import com.termux.app.chrome.GlassMotion;
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.layouteditor.EditorM3;
import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.PlaceLayout;
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.app.surfaces.AppearanceLooks.Control;
import com.termux.app.surfaces.AppearanceLooks.Door;
import com.termux.app.surfaces.AppearanceLooks.Target;
import com.termux.app.terminal.Motion;
import com.termux.app.terminal.PaneRetroEffect;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.settings.preferences.SharedPreferencesPreview;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * The Appearance and Layout editor (appearance-layout-editor SPEC §3.1–3.6): the launcher itself,
 * live, scaled into a frame at the device's corner radius, over a bottom area that never scrolls.
 * The mode pill picks what the frame and the bottom area are for: Appearance tunes the live
 * render; Layout cross-fades the frame to the layout canvas at the frame's full size (Layout is
 * not a miniature) with the orientation toggle, the Style toggle and eye-off below it.
 *
 * <p>The frame is {@code terminal_root_container} scaled about a top-centre pivot
 * ({@link AppearanceEditorFrame}); the bottom area is an M3 sheet outside it
 * ({@link AppearanceEditorPanel}). While the editor is up the terminal's own touches are taken by
 * the gesture overlay — one tap target per element, and the bare wallpaper under all of them — the
 * in-app keyboard is raised so it can be tapped, and the pane wall's paging is held.</p>
 *
 * <p>Row 1 is the Look slider — Clear · Mist · Tint · Solid · Custom. A stop applies its Look
 * live. At Custom, row 2 is a row of vertical sliders: the global set with nothing tapped, or the
 * tapped element's own ({@link AppearanceLooks#controls}); a tap on a bare area of the frame
 * brings the global set back, and a tap at a Look stop moves the slider to Custom first, seeded
 * from the Look it left. While the Look page is showing the status bar is held expanded. Sliding from Custom back to a Look applies it; the unsaved Custom values stay for the way back. Done
 * at Custom saves the Custom look, so its stop comes back.</p>
 *
 * <p>Layout mode's bottom area carries what no Look sets: the Style toggle and the global Corners
 * and Margin (SPEC §3.5), shown under both Styles (SPEC §3.7). They write through like every other
 * control, and the one Undo, dirty state and Discard cover them. The layout canvas is told the
 * Style, Corners and Margin and draws every shape from the shape model. Layout editor v2
 * (DECISIONS items 2 to 15): eye-off in the sheet is the only hide target and opens the hidden
 * tiles; the hide zone is gone and its room is the frame's again; Key radius is Layout's, beside
 * the keyboard's type chips. At the Custom stop with nothing tapped the global Blur, Grain and
 * Opacity each re-attach every surface to the base; the keyboard's row carries a "Keyboard theme"
 * door into Settings, from which Back returns to the same session.</p>
 *
 * <p>Everything writes through to preferences live, so the frame is the real thing; Undo and
 * Discard put back the state at open ({@link AppearanceSnapshot}) and the arrangement at open
 * ({@link LayoutEditorController}, whose session opens and closes with the editor). One dirty
 * state, one Undo, one Done and one unsaved-changes question cover both modes; switching modes
 * never asks. The activity keeps the render pipeline; what the editor needs from it crosses
 * {@link Host}.</p>
 */
public final class SurfaceEditorController implements AppearanceSurfaceController.Editor {

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
        /**
         * Holds the top status bar expanded for the editor, or lets it rest as it is stored: a
         * transient state that animates the bar's height like a fold does but never writes the
         * stored compact/expanded choice. A side-edge bar cannot expand, so there it does nothing.
         */
        void setTopStatusBarExpandedForEditor(boolean expanded, boolean animate);
        /** The dock now shows {@code count} app buttons: its row is rebuilt for them, live. */
        void applyDockButtonCount(int count);
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
        /**
         * Pushes the cursor-trail style and retro terminal effect preferences into the panes
         * (the Trail and Effect menus drive both).
         */
        void applyTerminalMotionLook();
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
         * Whether the frame has to paint the wallpaper itself: only in passthrough mode, where the
         * system draws it behind a translucent window. Otherwise {@code wallpaper_backdrop} is the
         * wallpaper and scales with the container, and nothing is read or painted for the editor.
         */
        boolean editorPaintsWallpaper();
        /**
         * Icons mode's content for the sheet: the pack tiles and the Pinned-only switch, built
         * once per session (shown, hidden and released with the mode). Null where there is none.
         */
        @Nullable
        default AppearanceSurfaceController.Page createIconsContent() {
            return null;
        }
    }

    @NonNull private final Host mHost;

    public SurfaceEditorController(@NonNull Host host) {
        mHost = host;
    }

    /**
     * Whether the editor's session is open: the Appearance surface is up, on whatever page. The
     * editor's own controls are only on screen while it is {@link #isPresented presented}.
     */
    public boolean isActive() {
        return mSession;
    }

    /** Whether the editor's frame and sheet are on screen (the Look or Layout page is showing). */
    @Override
    public boolean isPresented() {
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
    /** The state at open: what Undo and Discard return to, and what "unsaved" is measured against. */
    @Nullable private AppearanceSnapshot mEntry;
    @Nullable private String mEntrySignature;
    /** The Custom values of this session, kept while a Look is showing; null when they are the saved Custom. */
    @Nullable private AppearanceSnapshot mSessionCustom;
    private int mEntryStop = AppearanceLooks.CUSTOM_STOP;
    /** The Look slider's stop. */
    private int mStop = AppearanceLooks.CUSTOM_STOP;
    /** What a tap in the frame selected, or null; only ever set at the Custom stop. */
    @Nullable private Target mTarget;
    /** Whether the editor raised the in-app keyboard itself, and so owes taking it down. */
    private boolean mRaisedKeyboard;
    /**
     * Where the next presentation of its page opens, as the surface remembered it (the person
     * left from there within the window); used by that presentation and then forgotten.
     */
    @Nullable private AppearanceReturnState mRestore;
    /**
     * A remembered selection the frame did not offer yet: the keyboard is raised only once the
     * frame has settled, so its selection is made then.
     */
    @Nullable private Target mRestoreTarget;

    @Nullable private AppearanceEditorFrame mFrame;
    @Nullable private AppearanceEditorPanel mPanel;
    private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;
    private long mLayoutSignature = Long.MIN_VALUE;
    /**
     * The tallest the sheet gets in each mode (AppearanceEditorPanel.measureTallest: Appearance
     * with Row B up), and the taller of the two, which the frame stands above so no sheet covers
     * it and the frame never moves when Row B comes and goes. Recomputed with the frame. The
     * sheet's own height follows what shows (applyPanelHeight).
     */
    private int mAppearanceHeightPx;
    private int mLayoutHeightPx;
    private int mRestHeightPx;
    private int mNavInsetPx;
    /** The content width the panel was last measured at, and its running height animation. */
    private int mPanelWidthPx;
    @Nullable private android.animation.ValueAnimator mPanelAnimator;
    private int mPanelTargetPx;

    /** The gap between the frame and the status inset above it, and the bottom area below it. */
    private static final int FRAME_GAP_DP = 8;
    /** The selection outline's stroke. */
    private static final int OUTLINE_STROKE_DP = 2;
    private static final long PANEL_MS = 240L;

    /**
     * The activity resumed with the editor open: the status pane keeps the shape the editor is
     * holding it in (expanded on the Look page) until the editor closes and hands it back.
     */
    public void collapseStatusPaneIfLeftExpanded() {
        applyStatusExpansion(false);
    }

    /**
     * The activity came back to the front with the editor still open — from the Keyboard theme
     * page, say (DECISIONS item 15). The session is the same one: nothing is reverted or closed,
     * and the Undo still measures from the moment the editor opened. What the other page changed
     * (the keyboard's colours and typeface) is read again so the frame shows it, and the bottom
     * area is restated from preferences.
     */
    public void onActivityStarted() {
        if (!mOpen)
            return;
        applyStatusExpansion(false);
        TermuxInAppKeyboard keyboard = keyboard();
        if (keyboard != null)
            keyboard.onPreferencesReloaded();
        syncPanel();
        if (mLayoutMode) syncLayoutCanvas();
        else positionTargets();
    }

    // ----------------------------------------------------------------------------------- session
    //
    // One session spans the Appearance surface, whichever page it shows: the held wall, the
    // editor's wallpaper (decoded when the surface opens, painted inside the launcher's container
    // and kept under an opaque window for the whole session), the status pane's borrowed shape.
    // Look and Layout are the editor's presentation: the frame, the sheet, the tap targets, one
    // Undo baseline. Presenting and dismissing it moves between the pages without ending the
    // session, so a hop from the Overview does no setup that is not about the picture on screen.

    /** Whether the session is open: the surface is up, on any page. */
    private boolean mSession;
    /** Set by the Appearance surface: Done hands the page change to it. */
    @Nullable private Runnable mOnDone;
    /** The editor wallpaper's load has finished: a picture, none to paint, or a failure. */
    private boolean mWallpaperSettled;
    @Nullable private Runnable mWallpaperWaiter;
    /** Bumped by every present and dismiss, so a late post of the other one does nothing. */
    private int mPresentToken;
    /** The sheet's reveal is running; a height change in that time is made without animation. */
    private boolean mPanelRevealing;

    /**
     * The surface's Done: it takes the editor off screen itself (to the Overview, or the launcher
     * for a surface opened straight into an editor). Without one Done only saves.
     */
    @Override
    public void setOnDone(@Nullable Runnable onDone) {
        mOnDone = onDone;
    }

    /**
     * Opens the session: holds the wall, remembers the status pane and starts reading the
     * editor's wallpaper now, so it is painted before any page change needs it.
     */
    @Override
    public void beginSession() {
        if (mSession || prefs() == null)
            return;
        View rootContainer = mHost.findView(R.id.terminal_root_container);
        ViewGroup content = mHost.findView(android.R.id.content);
        if (rootContainer == null || content == null)
            return;
        mSession = true;
        mHost.holdPaneWall(true);
        if (mFrame == null || mFrame.root() != rootContainer)
            mFrame = new AppearanceEditorFrame(rootContainer);
        mWallpaperSettled = false;
        if (mHost.editorPaintsWallpaper()) {
            loadEditorWallpaper(rootContainer);
        } else {
            // Self-drawn: the real wallpaper_backdrop scales inside the container; nothing to load.
            mWallpaperSettled = true;
        }
    }

    /**
     * Runs {@code ready} once the editor's wallpaper has been read and painted (or has none to
     * paint), at most {@link #WALLPAPER_WAIT_MS} from now: a hop that starts before it would scale
     * the launcher over the system's unscaled wallpaper.
     */
    @Override
    public void awaitWallpaper(@NonNull Runnable ready) {
        ViewGroup content = mHost.findView(android.R.id.content);
        if (mWallpaperSettled || !mSession || content == null) {
            ready.run();
            return;
        }
        mWallpaperWaiter = ready;
        content.postDelayed(() -> {
            if (mWallpaperWaiter != ready)
                return;
            mWallpaperWaiter = null;
            ready.run();
        }, WALLPAPER_WAIT_MS);
    }

    private static final long WALLPAPER_WAIT_MS = 600L;

    /**
     * Runs {@code ready} once the launcher's container and the content view are laid out with
     * nothing pending, so the frame is measured from real sizes; {@code failed} when there is no
     * container, or it is still not laid out {@link #FRAME_WAIT_MS} from now. A cold start
     * delivers the Appearance door before the first layout pass, and a frame placed then is
     * placed from a zero-size container.
     */
    @Override
    public void awaitFrame(@NonNull Runnable ready, @NonNull Runnable failed) {
        final View root = mHost.findView(R.id.terminal_root_container);
        final ViewGroup content = mHost.findView(android.R.id.content);
        if (root == null || content == null) {
            failed.run();
            return;
        }
        if (isLaidOut(root) && isLaidOut(content)) {
            ready.run();
            return;
        }
        final boolean[] answered = {false};
        final ViewTreeObserver.OnGlobalLayoutListener[] listener = {null};
        final Runnable stopListening = () -> {
            ViewTreeObserver observer = content.getViewTreeObserver();
            if (listener[0] != null && observer.isAlive())
                observer.removeOnGlobalLayoutListener(listener[0]);
            listener[0] = null;
        };
        listener[0] = () -> {
            if (answered[0] || !isLaidOut(root) || !isLaidOut(content))
                return;
            answered[0] = true;
            stopListening.run();
            ready.run();
        };
        content.getViewTreeObserver().addOnGlobalLayoutListener(listener[0]);
        content.postDelayed(() -> {
            if (answered[0])
                return;
            answered[0] = true;
            stopListening.run();
            // A pass may be pending again by now (a clock's text, say): sizes are what count.
            if (hasSize(root) && hasSize(content))
                ready.run();
            else
                failed.run();
        }, FRAME_WAIT_MS);
    }

    private static final long FRAME_WAIT_MS = 1000L;

    /** Laid out at a real size, with no pass pending (the host the surface just added, say). */
    private static boolean isLaidOut(@NonNull View view) {
        return hasSize(view) && !view.isLayoutRequested();
    }

    private static boolean hasSize(@NonNull View view) {
        return view.isLaidOut() && view.getWidth() > 0 && view.getHeight() > 0;
    }

    private void wallpaperSettled() {
        mWallpaperSettled = true;
        Runnable waiter = mWallpaperWaiter;
        mWallpaperWaiter = null;
        if (waiter != null)
            waiter.run();
    }

    /**
     * Shows the editor over the launcher: the frame, the sheet, the tap targets. Layout mode when
     * {@code mode} is Layout, on {@code place} (the place on screen for null); Icons shows the real
     * dock in the frame over the pack controls, with no tap targets, no status-bar expansion and
     * no raised keyboard; {@code initialSection}
     * may name a surface (a settings deep link), selected at the Custom stop and ignored at a Look
     * so that opening the editor never changes the look. With {@code fromScale} above zero the
     * frame starts at that scale and offset (the Overview's Home card) and settles into place;
     * otherwise it grows from full size. The in-app keyboard is raised once the frame has
     * settled, and {@code onSettled} runs then. Already presented, it moves to that mode.
     */
    @Override
    public void present(@NonNull EditorMode mode, @Nullable PaneWallPage place,
                        @Nullable String initialSection, float fromScale, float fromTranslationY,
                        @Nullable Runnable onSettled) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        beginSession();
        if (!mSession)
            return;
        final boolean layoutMode = mode == EditorMode.LAYOUT;
        if (mOpen) {
            LayoutEditorController layout = mHost.layoutEditor();
            if (layoutMode) {
                if (layout != null) {
                    pushLayoutShape(layout);
                    layout.begin(place);
                }
                if (mFramed) setModeInternal(EditorMode.LAYOUT, true, 0L);
                else {
                    mOpenInLayout = true;
                    if (mIconsMode) setModeInternal(EditorMode.LOOK, false, 0L);
                }
            } else if (mode == EditorMode.ICONS) {
                mOpenInLayout = false;
                if (!mIconsMode) setModeInternal(EditorMode.ICONS, true, 0L);
            } else {
                if (mLayoutMode || mIconsMode) setModeInternal(EditorMode.LOOK, true, 0L);
                selectSection(initialSection);
            }
            if (onSettled != null)
                onSettled.run();
            return;
        }
        View rootContainer = mHost.findView(R.id.terminal_root_container);
        ViewGroup content = mHost.findView(android.R.id.content);
        if (rootContainer == null || content == null)
            return;
        final int token = ++mPresentToken;
        // Where the person left this page, when the surface remembers it: the Custom stop and
        // the selection (Look), the place (Layout), the row's scroll (Icons).
        AppearanceReturnState restore = mRestore;
        mRestore = null;
        if (restore != null && restore.editorMode() != mode)
            restore = null;
        if (restore != null && layoutMode && place == null)
            place = placeNamed(restore.place);
        mOpen = true;
        // No Look drag is under way at an open: a preview left behind by one that never saw its
        // lift (a recreated activity) must not stand between the launcher and its stored look.
        if (mLookDrag == null)
            SharedPreferencesPreview.clear();
        mEntry = AppearanceSnapshot.capture(prefs);
        mEntrySignature = mEntry.signature();
        mSessionCustom = null;
        mStop = matchingStop();
        // At the Custom stop as it was left: the slider moves there without writing anything,
        // as a tap on an element at a Look stop does; Undo comes back to it.
        if (restore != null && restore.custom)
            mStop = AppearanceLooks.CUSTOM_STOP;
        mEntryStop = mStop;
        mTarget = null;
        mRestoreTarget = restore == null ? null : targetNamed(restore.target);
        // The in-app keyboard is one of the things the frame offers; it is raised after the frame
        // has settled (the relayout and the terminal's resize would land on the animation).
        mRaisedKeyboard = false;
        mKeyboardOwed = false;

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
        mIconsMode = mode == EditorMode.ICONS;
        mOpenInLayout = layoutMode;
        // Icons' content is built now, whatever the mode, while the sheet is still hidden: its
        // pack listing loads behind the other modes, and a pill switch into Icons later finds
        // the tiles there and measured instead of building and loading them at the tap.
        ensureIconsContent();
        if (restore != null && mIconsMode && mIconsPage != null)
            mIconsPage.restoreScrollPosition(restore.iconsScrollPx);
        mPanel.setMode(mIconsMode ? EditorMode.ICONS : EditorMode.LOOK);
        mPanel.fadeInRows(0L);
        mPanel.hideRow2();
        syncPanel();
        if (mIconsMode) showIconsPage(true);
        // The bar's title is the mode the page opens in, so it never reads Look for a frame.
        if (mPageListener != null)
            mPageListener.onModeChanged(mIconsMode ? EditorMode.ICONS : EditorMode.LOOK);
        notifyDirty();
        panelView.setVisibility(View.INVISIBLE);

        // The layout session opens with the presentation, whichever mode it opens in, so the one
        // Undo and the one dirty state are measured from the same moment for both.
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
        if (fromScale > 0f) {
            pushDisplayInsets(content);
            mFrame.prime(fromScale, fromTranslationY);
        }
        content.post(() -> {
            if (!mOpen || token != mPresentToken)
                return;
            frameSizeChanged();
            layoutFrame(true);
            mFramed = true;
            revealPanel();
            // The Look page holds the status bar open, animating with the sheet's reveal; the
            // Layout and Icons pages leave it as it is stored.
            if (!mOpenInLayout && !mIconsMode)
                applyStatusExpansion(true);
            positionTargets();
            if (mOpenInLayout) {
                mOpenInLayout = false;
                // The canvas fades in with the frame (not after it): the cross-fade reads as the
                // same frame changing what it shows.
                setModeInternal(EditorMode.LAYOUT, true, 0L);
            } else if (!mIconsMode) {
                selectSection(initialSection);
                selectRestoredTarget();
            }
            long wait = ReducedMotion.isEnabled(mHost.context()) ? 0L
                : AppearanceEditorFrame.ENTER_MS + 20L;
            content.postDelayed(() -> {
                if (!mOpen || token != mPresentToken)
                    return;
                // Icons shows the dock only: the keyboard comes when the editor leaves that mode.
                if (mIconsMode)
                    mKeyboardOwed = true;
                else if (!mHost.isInAppKeyboardShown())
                    mRaisedKeyboard = mHost.showInAppKeyboardForEditor();
                // A remembered keyboard selection is offered only now; past here it is dropped.
                selectRestoredTarget();
                mRestoreTarget = null;
                if (onSettled != null)
                    onSettled.run();
            }, wait);
        });
    }

    // ------------------------------------------------------------------------- Layout mode

    /** Whether the frame is showing the layout canvas. */
    private boolean mLayoutMode;

    /** The session is on the Icons page: the real dock in the frame over the pack controls. */
    private boolean mIconsMode;
    /** The keyboard was not raised for Icons mode: it is raised when another mode shows. */
    private boolean mKeyboardOwed;
    /** Icons mode's content, built on first use and released with the session. */
    @Nullable private AppearanceSurfaceController.Page mIconsPage;

    /** The mode the editor shows. */
    @Override
    @NonNull
    public EditorMode mode() {
        return mIconsMode ? EditorMode.ICONS : mLayoutMode ? EditorMode.LAYOUT : EditorMode.LOOK;
    }
    /** A Layout door opened the editor; Layout mode is shown once the frame is placed. */
    private boolean mOpenInLayout;
    /** The layout canvas's host: the frame's rect, over the scaled launcher. */
    @Nullable private FrameLayout mLayoutFrame;
    /** The views lent to Layout mode, built with the frame host. */
    @Nullable private LayoutEditorController.Views mLayoutViews;
    /** The move control, out of the frame into the editor round it (placeMoveControl). */
    @Nullable private MaterialButton mLayoutMove;
    /** The frame's rect in the content view, as {@code {left, top, right, bottom}}. */
    @Nullable private int[] mFrameRectInContent;
    /** The frame's corner on screen: the device radius at the frame's scale. */
    private float mFrameCornerPx;
    private static final long LAYOUT_FADE_MS = 200L;

    /**
     * Lends Layout mode its views: the canvas in a frame host of its own over the scaled launcher,
     * with the move control and the keyboard's tools over it, and the bottom area's toggle, eye-off
     * and hidden tiles.
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
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        mFrameCornerPx + RING_PX);
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
                Gravity.TOP | Gravity.LEFT);
            int panelIndex = content.indexOfChild(panel.view());
            if (panelIndex >= 0) content.addView(frame, panelIndex, params);
            else content.addView(frame, params);
        }
        placeMoveControl(content, frame);
        frame.animate().cancel();
        frame.setAlpha(1f);
        frame.setVisibility(View.GONE);
        LayoutCanvasView liftCanvas = frame.findViewById(R.id.layout_editor_canvas);
        if (liftCanvas != null)
            // The lifted copy follows the finger over the whole editor, the sheet included.
            liftCanvas.setLiftOverlayHost(content);
        LayoutCanvasView shapeCanvas = frame.findViewById(R.id.layout_editor_canvas);
        if (shapeCanvas != null)
            shapeCanvas.setOnModelResizedListener(this::onModelShapeChanged);
        if (mLayoutViews == null) {
            LayoutCanvasView canvas = frame.findViewById(R.id.layout_editor_canvas);
            MaterialButton move = mLayoutMove;
            if (canvas == null || move == null)
                return;
            // One set of views per process, like the panel: the controller binds them once. The
            // keyboard's tools are the sheet's: they take its Row B while the keyboard is selected.
            mLayoutViews = new LayoutEditorController.Views(frame, canvas, panel.keyboardTools(),
                panel.keyboardForms(), panel.keyRadiusLabel(), panel.keyRadius(), move,
                panel.orientationToggle(), panel.hiddenControl(), panel.hiddenHighlight(),
                panel.hiddenTileGroup(), new LayoutEditorController.TilesHost() {
                    @Override public void setHiddenTilesOpen(boolean open) {
                        SurfaceEditorController.this.setHiddenTilesOpen(open);
                    }

                    @Override public void setKeyboardToolsShown(boolean shown) {
                        if (mPanel != null)
                            mPanel.setKeyboardToolsShown(shown);
                    }
                });
        }
        layout.attach(mLayoutViews);
        layout.setKeyRadius(mKeyRadius);
        positionLayoutFrame();
    }

    /**
     * The move control out of the frame, which clips to the phone's outline, into the editor round
     * it, just above the frame: so it can stand in the gutter outside the phone and never covers
     * the canvas (DECISIONS items 4 and 5). It stays under the bottom area.
     */
    private void placeMoveControl(@NonNull ViewGroup content, @NonNull FrameLayout frame) {
        MaterialButton move = mLayoutMove;
        if (move == null) {
            move = frame.findViewById(R.id.layout_editor_move);
            if (move == null)
                return;
            mLayoutMove = move;
        }
        if (move.getParent() == content
            && content.indexOfChild(move) == content.indexOfChild(frame) + 1)
            return;
        ViewGroup.LayoutParams old = move.getLayoutParams();
        int width = old != null ? old.width : ViewGroup.LayoutParams.WRAP_CONTENT;
        int height = old != null ? old.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (move.getParent() instanceof ViewGroup)
            ((ViewGroup) move.getParent()).removeView(move);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height,
            Gravity.TOP | Gravity.LEFT);
        content.addView(move, content.indexOfChild(frame) + 1, params);
    }

    /** The bottom area's Row B swapped for the hidden tiles, or back (DECISIONS item 3). */
    private void setHiddenTilesOpen(boolean open) {
        if (mPanel != null)
            mPanel.setHiddenTilesOpen(open);
    }

    /**
     * Key radius, as Layout mode shows it beside the keyboard's type chips (DECISIONS item 6): the
     * key caps' radius alone, written through with the keyboard's live preview, and covered by
     * the one Undo. Like Corners and Margin it belongs to no Look, so it never moves the slider.
     */
    private final LayoutEditorController.KeyRadius mKeyRadius =
        new LayoutEditorController.KeyRadius() {
            @Override public int value() {
                TermuxAppSharedPreferences prefs = prefs();
                return prefs == null ? 0
                    : AppearanceLooks.keyCornersValueFor(prefs.getInAppKeyboardKeyCornerRadiusDp());
            }

            @Override public int max() {
                return AppearanceLooks.KEY_CORNERS_MAX_DP;
            }

            @NonNull @Override public String label(int value) {
                return getString(R.string.appearance_editor_key_corners,
                    AppearanceLooks.keyCornersDp(value));
            }

            @Override public void onChanged(int value, boolean dragging) {
                beginDrag(dragging);
                writeKeyCorners(value);
            }

            @Override public void onReleased() {
                endDrag();
            }
        };

    // ---- the other orientation's phone model ------------------------------------------------------

    private static final long MODEL_MS = 250L;
    private boolean mModelShape;
    private float mModelProgress;
    @Nullable private android.animation.ValueAnimator mModelAnimator;

    /**
     * Layout mode shows the other orientation (landscape in a portrait editor): the canvas draws
     * a landscape phone model of its own, so the portrait host's ground and the live launcher
     * under it, which cannot show landscape, fade away beneath it. Cross-fades over 250ms; with
     * reduced motion it jumps.
     */
    private void applyModelShape(boolean resized, boolean animate) {
        if (resized == mModelShape && mModelAnimator == null && mModelProgress == (resized ? 1f : 0f))
            return;
        mModelShape = resized;
        FrameLayout host = mLayoutFrame;
        if (mModelAnimator != null) {
            mModelAnimator.cancel();
            mModelAnimator = null;
        }
        final float to = resized ? 1f : 0f;
        if (!animate || ReducedMotion.isEnabled(mHost.context()) || host == null || !mOpen) {
            setModelProgress(to);
            return;
        }
        android.animation.ValueAnimator animator =
            android.animation.ValueAnimator.ofFloat(mModelProgress, to);
        animator.setDuration(MODEL_MS);
        animator.setInterpolator(Motion.settle());
        animator.addUpdateListener(a -> setModelProgress((Float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (mModelAnimator == animator)
                    mModelAnimator = null;
            }
        });
        mModelAnimator = animator;
        animator.start();
    }

    /** 0 is the host's own phone over the launcher, 1 the free-standing model. */
    private void setModelProgress(float progress) {
        mModelProgress = progress;
        AppearanceEditorFrame frame = mFrame;
        if (frame != null)
            frame.root().setAlpha(1f - progress);
        FrameLayout host = mLayoutFrame;
        if (host != null && host.getBackground() != null)
            host.getBackground().mutate().setAlpha(Math.round(255f * (1f - progress)));
    }

    /** The canvas changed between the host's own shape and the other orientation's model. */
    private void onModelShapeChanged() {
        if (!mOpen || !mLayoutMode || mLayoutFrame == null)
            return;
        LayoutCanvasView canvas = mLayoutFrame.findViewById(R.id.layout_editor_canvas);
        if (canvas == null)
            return;
        applyModelShape(canvas.isModelResized(), true);
        if (!ReducedMotion.isEnabled(mHost.context())) {
            // The model itself morphs in: the new outline fades up as the old one leaves.
            canvas.animate().cancel();
            canvas.setAlpha(0f);
            canvas.animate().alpha(1f).setDuration(MODEL_MS).setInterpolator(Motion.settle())
                .start();
        }
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
        // One pixel past the scaled launcher on every side: two anti-aliased edges (the launcher's
        // clip and this host's) never add up to full cover, so the host reaches past instead.
        int left = rect[0] - RING_PX;
        int top = rect[1] - RING_PX;
        int width = Math.max(1, rect[2] - rect[0] + 2 * RING_PX);
        int height = Math.max(1, rect[3] - rect[1] + 2 * RING_PX);
        if (params.width != width || params.height != height || params.leftMargin != left
            || params.topMargin != top) {
            params.width = width;
            params.height = height;
            params.leftMargin = left;
            params.topMargin = top;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            frame.setLayoutParams(params);
        }
        frame.invalidateOutline();
        LayoutCanvasView canvas = frame.findViewById(R.id.layout_editor_canvas);
        if (canvas != null)
            canvas.setFrameCornerRadiusPx(mFrameCornerPx + RING_PX);
    }

    /**
     * The page bar's mode pill moved. One session for both modes: switching never asks and never
     * resets anything.
     */
    @Override
    public void setMode(@NonNull EditorMode mode) {
        if (mode != mode())
            setModeInternal(mode, true, 0L);
    }

    /** Builds Icons mode's content once per session and hands it to the sheet. */
    private void ensureIconsContent() {
        AppearanceEditorPanel panel = mPanel;
        if (panel == null || mIconsPage != null)
            return;
        AppearanceSurfaceController.Page page = mHost.createIconsContent();
        if (page == null)
            return;
        mIconsPage = page;
        panel.setIconsContent(page.root());
        page.setOnContentChanged(this::onIconsContentChanged);
    }

    /**
     * The tile row was built again for a listing that landed with other packs: while Icons shows,
     * the sheet takes the content's new height (a two-line pack name is never cut off), and the
     * frame follows on the sheet's clock. Another mode measures it on the way in.
     */
    private void onIconsContentChanged() {
        if (mOpen && mIconsMode && mFramed)
            applyPanelHeight(true);
    }

    /**
     * Icons shows the dock alone, as it does when it opens from the Overview: a keyboard the
     * editor raised goes down with the switch, and comes back when another mode shows. One the
     * person had up already stays, as it does on that path.
     */
    private void lowerRaisedKeyboardForIcons() {
        if (!mRaisedKeyboard)
            return;
        mHost.hideInAppKeyboardForEditor();
        mRaisedKeyboard = false;
        mKeyboardOwed = true;
    }

    /** The content is on screen (true) or has left it: it re-reads the choice, or stops loading. */
    private void showIconsPage(boolean shown) {
        AppearanceSurfaceController.Page page = mIconsPage;
        if (page == null)
            return;
        if (shown) page.onShown();
        else page.onHidden();
    }

    /**
     * Swaps what the frame and the bottom area are for. The frame stays put: the canvas's host
     * is the frame's own rect, so the cross-fade changes the picture and nothing moves.
     *
     * <p>Into or out of Icons nothing covers the frame (Look and Layout hide their change under
     * the canvas's fade), so the switch lands the state the Overview's hop into Icons lands (the
     * status pane as stored, no keyboard the editor raised, the frame over the Icons sheet) in
     * one move: the status pane's fold, the keyboard, the sheet's height and the frame's refit
     * all start from this call, the sheet's new rows fade in over it, and the Icons content was
     * built and loaded while the sheet was hidden ({@link #present}).</p>
     */
    private void setModeInternal(@NonNull EditorMode mode, boolean animate, long delayMs) {
        AppearanceEditorPanel panel = mPanel;
        if (!mOpen || panel == null)
            return;
        final boolean layout = mode == EditorMode.LAYOUT;
        final boolean wasIcons = mIconsMode;
        mLayoutMode = layout;
        mIconsMode = mode == EditorMode.ICONS;
        final boolean iconsSwitch = wasIcons != mIconsMode;
        if (mIconsMode) ensureIconsContent();
        panel.setMode(mode);
        if (iconsSwitch)
            showIconsPage(mIconsMode);
        if (mPageListener != null)
            mPageListener.onModeChanged(mode);
        // Corners and Margin are the Custom row's too: each page reads what the other wrote.
        syncLayoutControls();
        if (mode == EditorMode.LOOK)
            syncPanel();
        if (mIconsMode && !wasIcons && mFramed)
            lowerRaisedKeyboardForIcons();
        if (!mIconsMode && mKeyboardOwed && mFramed) {
            mKeyboardOwed = false;
            if (!mHost.isInAppKeyboardShown())
                mRaisedKeyboard = mHost.showInAppKeyboardForEditor();
        }
        applyStatusExpansion(animate);
        applyPanelHeight(animate);
        if (iconsSwitch)
            panel.fadeInRows(animate && mFramed && !ReducedMotion.isEnabled(mHost.context())
                ? PANEL_MS : 0L);
        LayoutEditorController editor = mHost.layoutEditor();
        if (layout) {
            dismissClockDropdown();
            positionLayoutFrame();
            if (editor != null) {
                pushLayoutShape(editor);
                editor.sync();
            }
            LayoutCanvasView shown = mLayoutFrame == null ? null
                : mLayoutFrame.findViewById(R.id.layout_editor_canvas);
            applyModelShape(shown != null && shown.isModelResized(), animate);
        } else {
            applyModelShape(false, animate);
            positionTargets();
            // The move control stands outside the frame, so the frame's fade does not take it.
            if (mLayoutMove != null)
                mLayoutMove.setVisibility(View.GONE);
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
     * editor's lifetime; the surface's colorSurface scrim (never a window swap) covers what shows
     * around the scaled frame. Not called in self-drawn mode.
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
            final Drawable picture = wallpaper;
            rootContainer.post(() -> {
                if (!mSession || token != mWallpaperToken)
                    return;
                if (picture != null) {
                    mEditorWallpaper = picture;
                    showEditorWallpaper(picture);
                } else if (mPageListener != null) {
                    mPageListener.onFrameWallpaperMissing();
                }
                wallpaperSettled();
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
    }

    private void selectSection(@Nullable String section) {
        Target target = Target.forSlot(slotForSectionKey(section));
        if (target != null && AppearanceLooks.isCustomStop(mStop))
            select(target);
    }

    /**
     * The remembered selection, once the frame offers it, at the Custom stop on the Look page;
     * nothing when a deep link already chose one or the person has moved on.
     */
    private void selectRestoredTarget() {
        Target target = mRestoreTarget;
        if (target == null || !mOpen || mLayoutMode || mIconsMode || mTarget != null
            || !AppearanceLooks.isCustomStop(mStop))
            return;
        select(target);
        if (mTarget == target)
            mRestoreTarget = null;
    }

    @Nullable
    private static Target targetNamed(@Nullable String name) {
        if (name == null)
            return null;
        for (Target target : Target.values()) {
            if (target.name().equals(name))
                return target;
        }
        return null;
    }

    @Nullable
    private static PaneWallPage placeNamed(@Nullable String name) {
        if (name == null)
            return null;
        for (PaneWallPage page : PaneWallPage.values()) {
            if (page.name().equals(name))
                return page;
        }
        return null;
    }

    @Override
    public void restoreNext(@Nullable AppearanceReturnState state) {
        mRestore = state;
    }

    /** Where the editor stands on its page, for the surface to remember. */
    @Override
    public void describe(@NonNull AppearanceReturnState.Builder out) {
        if (!mOpen)
            return;
        if (mIconsMode) {
            AppearanceSurfaceController.Page icons = mIconsPage;
            out.iconsScroll(icons == null ? 0 : icons.scrollPosition());
        } else if (mLayoutMode) {
            LayoutEditorController layout = mHost.layoutEditor();
            PaneWallPage place = layout == null ? null : layout.editedPlace();
            out.place(place == null ? null : place.name());
        } else {
            out.look(mTarget == null ? null : mTarget.name(), AppearanceLooks.isCustomStop(mStop));
        }
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
     * Places the frame and sizes the bottom area: the bottom area at the height its rows need in
     * each mode, the frame scaled to fit between the status inset and the taller of the two.
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
        panel.setNavInset(mNavInsetPx);
        // Side insets (landscape navigation bar, camera cutout) over the sheet's content, from
        // the window's own insets and only where they overlap this content view.
        Insets sides = insets == null ? Insets.NONE : insets.getInsets(
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        int decorWidth = content.getRootView().getWidth();
        panel.setSideInsets(
            Math.max(0, sides.left - contentInWindow[0]),
            Math.max(0, sides.right - (decorWidth - (contentInWindow[0] + content.getWidth()))));
        // The sheet's height follows its content, but the frame stands above the tallest it gets
        // (Appearance with Row B up, or Layout), so it never moves when Row B comes and goes or
        // when the mode pill does, and no sheet ever covers it.
        mPanelWidthPx = content.getWidth();
        mAppearanceHeightPx = panel.measureTallest(EditorMode.LOOK, mPanelWidthPx);
        mLayoutHeightPx = panel.measureTallest(EditorMode.LAYOUT, mPanelWidthPx);
        mRestHeightPx = Math.max(mAppearanceHeightPx, mLayoutHeightPx);
        mInLayoutFrame = true;
        try {
            applyPanelHeight(false);
        } finally {
            mInLayoutFrame = false;
        }

        int[] parentOffset = new int[2];
        if (!AppearanceEditorFrame.offsetIn((View) root.getParent(), content, parentOffset))
            return;
        int containerTop = parentOffset[1] + root.getTop();
        int containerLeft = parentOffset[0] + root.getLeft();
        // The page bar (back, title, Undo, Done) stands between the status inset and the frame.
        int frameTop = Math.max(containerTop, statusInset + dp(AppearanceEditorPage.BAR_DP))
            + dp(FRAME_GAP_DP);
        // Nothing stands between the frame and the sheet any more (DECISIONS item 2): the hide
        // zone's 56dp are the frame's.
        mFrameCtxValid = true;
        mWindowHeightPx = windowHeight;
        mFrameTopPx = frameTop;
        mContainerTopPx = containerTop;
        mContainerLeftPx = containerLeft;
        cancelFrameAnimator();
        // The frame fits the room the sheet leaves NOW (a Look stop's one row, or Custom's
        // sliders), not the tallest it gets: refitFrame follows every later change of the sheet.
        int frameBottom = windowHeight - panel.measureFor(mode(), mPanelWidthPx)
            - dp(FRAME_GAP_DP);
        float scale = AppearanceEditorFrame.fitScale(root.getHeight(), frameTop, frameBottom,
            AppearanceEditorFrame.MAX_SCALE);
        float ty = frameTop - containerTop;
        pushDisplayInsets(content);
        frame.show(scale, ty, animate);
        mTargetScale = scale;
        mTargetTy = ty;
        setFrameRect(scale);
        positionLayoutFrame();
        // A rotation moved the decor around the container: the picture is cropped to it again.
        if (mEditorWallpaper != null)
            showEditorWallpaper(mEditorWallpaper);
    }

    /**
     * Tells the frame where the display's edges stand around the container, from the window's own
     * geometry: the container's rect in the decor against the decor's size, so the status bar
     * above it, the navigation bar below it and any side bar are all counted. The clip's arc is
     * then the display's, not the container's. Unknown geometry leaves the insets at 0.
     */
    private void pushDisplayInsets(@NonNull ViewGroup content) {
        AppearanceEditorFrame frame = mFrame;
        if (frame == null)
            return;
        View root = frame.root();
        int[] parentOffset = new int[2];
        if (root.getWidth() <= 0 || !(root.getParent() instanceof View)
            || !AppearanceEditorFrame.offsetIn((View) root.getParent(), content, parentOffset)) {
            frame.setDisplayInsets(0, 0, 0, 0);
            return;
        }
        int[] contentInWindow = new int[2];
        content.getLocationInWindow(contentInWindow);
        View decor = content.getRootView();
        int left = contentInWindow[0] + parentOffset[0] + root.getLeft();
        int top = contentInWindow[1] + parentOffset[1] + root.getTop();
        mDisplayInsetLeftPx = Math.max(0, left);
        mDisplayInsetTopPx = Math.max(0, top);
        frame.setDisplayInsets(left, top, decor.getWidth() - (left + root.getWidth()),
            decor.getHeight() - (top + root.getHeight()));
    }

    /** The display's left and top edges' distance from the container, for the ring. */
    private int mDisplayInsetLeftPx;
    private int mDisplayInsetTopPx;

    // ---- the frame follows the sheet -------------------------------------------------------------

    private boolean mFrameCtxValid;
    private boolean mInLayoutFrame;
    private int mWindowHeightPx;
    private int mFrameTopPx;
    private int mContainerTopPx;
    private int mContainerLeftPx;
    private float mTargetScale = -1f;
    private float mTargetTy;
    @Nullable private android.animation.ValueAnimator mFrameAnimator;
    /** The layout canvas host reaches this far past the scaled launcher, so no seam shows. */
    private static final int RING_PX = 1;

    private void cancelFrameAnimator() {
        android.animation.ValueAnimator animator = mFrameAnimator;
        mFrameAnimator = null;
        if (animator != null)
            animator.cancel();
    }

    /** The scaled container's rect in the content view, widened to whole pixels. */
    private void setFrameRect(float scale) {
        AppearanceEditorFrame frame = mFrame;
        if (frame == null)
            return;
        View root = frame.root();
        float scaledLeft = mContainerLeftPx + root.getWidth() * (1f - scale) / 2f;
        mFrameRectInContent = new int[] {(int) Math.floor(scaledLeft), mFrameTopPx,
            (int) Math.ceil(scaledLeft + root.getWidth() * scale),
            (int) Math.ceil(mFrameTopPx + root.getHeight() * scale)};
        mFrameCornerPx = AppearanceEditorFrame.visibleCornerRadiusPx(
            AppearanceEditorFrame.deviceCornerRadiusPx(root), mDisplayInsetLeftPx,
            mDisplayInsetTopPx) * scale;
    }

    /**
     * The sheet took a new height: the frame grows into the room a shorter sheet gives it, or
     * shrinks out of a taller one's way, on the sheet's own clock (same duration and curve), the
     * layout canvas and the tap targets following every tick.
     */
    private void refitFrame(int sheetPx, boolean animate) {
        AppearanceEditorFrame frame = mFrame;
        if (frame == null || !mFrameCtxValid || mInLayoutFrame || !mOpen)
            return;
        View root = frame.root();
        if (root.getHeight() <= 0)
            return;
        int bottom = mWindowHeightPx - sheetPx - dp(FRAME_GAP_DP);
        float scale = AppearanceEditorFrame.fitScale(root.getHeight(), mFrameTopPx, bottom,
            AppearanceEditorFrame.MAX_SCALE);
        float ty = mFrameTopPx - mContainerTopPx;
        if (scale == mTargetScale && ty == mTargetTy)
            return;
        mTargetScale = scale;
        mTargetTy = ty;
        cancelFrameAnimator();
        if (!mFramed) {
            // Still arriving: the opening hop re-aims at the new pose.
            frame.show(scale, ty, !ReducedMotion.isEnabled(mHost.context()));
            setFrameRect(scale);
            positionLayoutFrame();
            return;
        }
        if (!animate || ReducedMotion.isEnabled(mHost.context())) {
            frame.show(scale, ty, false);
            setFrameRect(scale);
            positionLayoutFrame();
            positionTargets();
            return;
        }
        root.animate().cancel();
        final float fromScale = root.getScaleX();
        final float fromTy = root.getTranslationY();
        final float toScale = scale;
        final float toTy = ty;
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(PANEL_MS);
        animator.setInterpolator(Motion.settle());
        animator.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            float s = fromScale + (toScale - fromScale) * f;
            frame.setPose(s, fromTy + (toTy - fromTy) * f);
            setFrameRect(s);
            positionLayoutFrame();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(android.animation.Animator a) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (mFrameAnimator == animator)
                    mFrameAnimator = null;
                if (mCancelled || !mOpen)
                    return;
                frame.setPose(toScale, toTy);
                setFrameRect(toScale);
                positionLayoutFrame();
                positionTargets();
            }
        });
        mFrameAnimator = animator;
        animator.start();
    }

    /**
     * The bottom area's height for what shows now, as measured by the panel: Row A alone until an
     * element is tapped, then Row A + Row B; it does not change when Undo comes and goes or when
     * Style flips. A change animates (a jump with reduced motion), the sheet growing up from the
     * screen's foot while the frame stays where it is.
     */
    private void applyPanelHeight(boolean animate) {
        AppearanceEditorPanel panel = mPanel;
        if (panel == null || mRestHeightPx <= 0 || mPanelWidthPx <= 0)
            return;
        int height = panel.measureFor(mode(), mPanelWidthPx);
        View view = panel.view();
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null)
            return;
        if (mPanelAnimator != null) {
            if (mPanelTargetPx == height) {
                refitFrame(height, animate);
                return;
            }
            // A change cut short lands where it was going; the new one starts from there.
            finishPanelAnimation(view);
        }
        if (params.height == height) {
            refitFrame(height, animate);
            return;
        }
        int from = params.height > 0 ? params.height : view.getHeight();
        boolean instant = !animate || from <= 0 || !view.isAttachedToWindow()
            || view.getVisibility() != View.VISIBLE || mPanelRevealing
            || ReducedMotion.isEnabled(mHost.context());
        if (instant) {
            params.height = height;
            view.setLayoutParams(params);
            refitFrame(height, false);
            return;
        }
        refitFrame(height, true);
        // One layout pass, then the sheet's top edge travels by translation: growing, the sheet
        // takes its new height at once and starts pushed down by the difference; shrinking, it
        // keeps its height and slides down by the difference, and takes the new one at the end,
        // where the two are the same picture. Nothing is laid out per frame.
        final boolean grow = height > from;
        final float startY = grow ? (height - from) : 0f;
        final float endY = grow ? 0f : (from - height);
        if (grow) {
            params.height = height;
            view.setLayoutParams(params);
        }
        view.setTranslationY(startY);
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(PANEL_MS);
        animator.setInterpolator(Motion.settle());
        animator.addUpdateListener(a ->
            view.setTranslationY(startY + (endY - startY) * (Float) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(android.animation.Animator a) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (mPanelAnimator == animator)
                    mPanelAnimator = null;
                if (mCancelled)
                    return;
                if (!grow) {
                    ViewGroup.LayoutParams live = view.getLayoutParams();
                    if (live != null) {
                        live.height = height;
                        view.setLayoutParams(live);
                    }
                }
                view.setTranslationY(0f);
            }
        });
        mPanelAnimator = animator;
        mPanelTargetPx = height;
        animator.start();
    }

    /** Puts the sheet where its running height change was going, and stops it. */
    private void finishPanelAnimation(@NonNull View view) {
        android.animation.ValueAnimator animator = mPanelAnimator;
        mPanelAnimator = null;
        if (animator != null)
            animator.cancel();
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != mPanelTargetPx) {
            params.height = mPanelTargetPx;
            view.setLayoutParams(params);
        }
        view.setTranslationY(0f);
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
        mPanelRevealing = true;
        view.animate().translationY(0f).setDuration(PANEL_MS)
            .setInterpolator(Motion.settle())
            .withEndAction(() -> mPanelRevealing = false).start();
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
        switch (AppearanceLooks.rowFor(mStop, mTarget)) {
            case ELEMENT:
            case GLOBAL:
                showCustomRow();
                break;
            case NONE:
            default:
                panel.hideRow2();
                break;
        }
        applyPanelHeight(mFramed);
        syncDirty();
    }

    /**
     * The Custom row: the heading, the buttons the selection has, and its vertical sliders at the
     * stored values. The set is {@link AppearanceLooks#controls}: the global one with nothing
     * tapped, or the element's own.
     */
    private void showCustomRow() {
        AppearanceEditorPanel panel = mPanel;
        TermuxAppSharedPreferences prefs = prefs();
        if (panel == null || prefs == null)
            return;
        panel.showRow2(mTarget == null ? R.string.appearance_editor_target_all : nameOf(mTarget));
        // The palette Contrast changes is the Material one; with wallpaper colours off the
        // terminal wears a scheme file, which no contrast level moves (as in Settings).
        boolean palette = prefs.isTerminalDynamicColorsEnabled();
        List<AppearanceEditorPanel.SliderState> states = new ArrayList<>();
        for (Control control : AppearanceLooks.controls(mTarget)) {
            boolean enabled = control != Control.CONTRAST || palette;
            states.add(new AppearanceEditorPanel.SliderState(control, valueOf(control, prefs),
                enabled, legendFor(control, enabled)));
        }
        panel.setSliders(states);
        List<Door> doors = new ArrayList<>(AppearanceLooks.doors(mTarget));
        if (!PaneRetroEffect.available())
            doors.remove(Door.EFFECT);
        panel.setDoors(doors);
        if (mTarget == Target.TERMINAL)
            panel.setTerminalLooks(prefs.getTerminalCursorTrailStyle(),
                prefs.getTerminalRetroEffect());
    }

    /** What a control's slider stands at now, in the control's own units. */
    private int valueOf(@NonNull Control control, @NonNull TermuxAppSharedPreferences prefs) {
        switch (control) {
            case BLUR:
                return AppearanceLooks.blurDp(mTarget == null
                    ? prefs.getSurfaceBaseValue(SurfaceProperty.BLUR) : blurOf(prefs, mTarget));
            case GRAIN:
                return AppearanceLooks.grainPercent(mTarget == null
                    ? prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN)
                    : grainOf(prefs, mTarget.slot));
            case OPACITY:
                return AppearanceLooks.opacityPercent(mTarget == null
                    ? prefs.getSurfaceBaseValue(SurfaceProperty.OPACITY)
                    : opacityOf(prefs, mTarget));
            case TINT:
                return AppearanceLooks.tintPercent(mTarget == null
                    ? prefs.getSurfaceBaseValue(SurfaceProperty.TINT)
                    : tintOf(prefs, mTarget.slot));
            case MARGIN:
                return AppearanceLooks.marginValueFor(mHost.isFloatingDock(),
                    prefs.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP),
                    prefs.getTerminalPaneGap());
            case CORNER_RADIUS:
                return AppearanceLooks.cornersDp(
                    prefs.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
            case KEY_RADIUS:
                return AppearanceLooks.keyCornersValueFor(
                    prefs.getInAppKeyboardKeyCornerRadiusDp());
            case KEY_SPACING:
                return AppearanceLooks.keySpacingValueFor(prefs.getInAppKeyboardKeyMarginScale());
            case DOCK_SIZE: {
                LayoutEditorController layout = mHost.layoutEditor();
                float scale = layout == null ? -1f : layout.dockHeightScale();
                return AppearanceLooks.dockSizeValueFor(
                    scale > 0f ? scale : prefs.getAppLauncherBarHeightScale());
            }
            case APP_ICONS:
                return AppearanceLooks.appIconsValueFor(prefs.getAppLauncherButtonCount());
            case CONTRAST:
            default:
                return AppearanceLooks.legibilityIndex(prefs.getTerminalContrastLevel());
        }
    }

    /**
     * A control's legend at a slider value: its name and the number, as the slider draws it inside
     * its track and as accessibility announces the value.
     */
    @NonNull
    private LabelFormatter legendFor(@NonNull Control control, boolean enabled) {
        return value -> legendText(control, Math.round(value), enabled);
    }

    @NonNull
    private String legendText(@NonNull Control control, int value, boolean enabled) {
        switch (control) {
            case BLUR: return getString(R.string.appearance_editor_blur, value);
            case GRAIN: return getString(R.string.appearance_editor_grain, value);
            case OPACITY: return getString(R.string.appearance_editor_opacity, value);
            case TINT: return getString(R.string.appearance_editor_tint, value);
            case MARGIN: return getString(R.string.appearance_editor_margin, value);
            case CORNER_RADIUS: return getString(R.string.appearance_editor_corners, value);
            case KEY_RADIUS: return getString(R.string.appearance_editor_key_corners, value);
            case KEY_SPACING:
                return getString(R.string.appearance_editor_key_spacing,
                    String.format(Locale.getDefault(), "%.1f", value / 10f));
            case DOCK_SIZE: return getString(R.string.appearance_editor_dock_size, value);
            case APP_ICONS: return getString(R.string.appearance_editor_app_icons, value);
            case CONTRAST:
            default:
                if (!enabled)
                    return getString(R.string.appearance_editor_legibility_unavailable);
                return getString(value <= 0 ? R.string.appearance_editor_legibility_low
                    : value == 1 ? R.string.appearance_editor_legibility_normal
                    : R.string.appearance_editor_legibility_high);
        }
    }

    private void syncDirty() {
        if (mPanel == null)
            return;
        if (mSliderDragActive) {
            mDirtyDeferred = true;
            return;
        }
        notifyDirty();
    }

    // ------------------------------------------------------------------- the page bar's wiring

    /** What the surface's page bar over the frame needs to know of the editor. */
    @Nullable private AppearanceSurfaceController.PageListener mPageListener;

    @Override
    public void setPageListener(@Nullable AppearanceSurfaceController.PageListener listener) {
        mPageListener = listener;
        if (listener != null) {
            listener.onModeChanged(mode());
            listener.onDirtyChanged(mOpen && isDirty());
        }
    }

    private void notifyDirty() {
        AppearanceSurfaceController.PageListener listener = mPageListener;
        boolean dirty = mOpen && isDirty();
        if (listener != null)
            listener.onDirtyChanged(dirty);
    }

    /** The bar's Undo: everything back to the state at open. */
    @Override
    public void undo() {
        if (mOpen)
            revertToEntry();
    }

    /** The bar's Done: saves the look and hands the page change to the surface. */
    @Override
    public void done() {
        if (mOpen)
            commitAndExit();
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
     * The status bar is open for the whole of the Look page, so its clock and its media are there
     * to be tuned; the Layout page leaves it as stored. Transient: nothing is written.
     */
    private void applyStatusExpansion(boolean animate) {
        if (prefs() == null || !mOpen)
            return;
        mHost.setTopStatusBarExpandedForEditor(mode() == EditorMode.LOOK, animate);
    }

    /** The bottom area's events, turned into writes. */
    private final class PanelListener implements AppearanceEditorPanel.Listener {
        @Override public void onLookStop(int stop, boolean dragging) {
            if (dragging) {
                previewStop(stop);
                return;
            }
            // A drag still held lands first, so the stop below moves from what is stored.
            settleLookDrag();
            TermuxAppSharedPreferences prefs = prefs();
            if (prefs != null && moveToStop(prefs, stop))
                syncAfterBulkWrite();
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

        @Override public void onSlider(@NonNull Control control, int value, boolean dragging) {
            beginDrag(dragging);
            writeControl(control, value);
        }

        @Override public void onDoor(@NonNull Door door, @NonNull View anchor) {
            if (door == Door.KEYBOARD_THEME && mTarget == Target.KEYBOARD)
                openKeyboardTheme();
            else if (door == Door.CLOCK && mTarget == Target.STATUS)
                showClockDropdown(anchor);
        }

        @Override public void onTrailStyle(@NonNull String id) {
            if (mTarget == Target.TERMINAL) writeTrailStyle(id);
        }

        @Override public void onRetroEffect(@NonNull String id) {
            if (mTarget == Target.TERMINAL) writeRetroEffect(id);
        }

        @Override public void onSliderReleased() {
            endDrag();
        }
    }

    /**
     * One of the Custom row's sliders moved. Blur, Grain and Opacity are the shared glass with
     * nothing tapped and the element's own values with one tapped; Margin and Corner radius are
     * Layout mode's own writes; the rest are the element's.
     */
    private void writeControl(@NonNull Control control, int value) {
        switch (control) {
            case BLUR:
                if (mTarget == null) writeBlur(value);
                else writeElement(mTarget, SurfaceProperty.BLUR, value);
                break;
            case GRAIN:
                if (mTarget == null) writeGlobal(SurfaceProperty.GRAIN, value);
                else writeElement(mTarget, SurfaceProperty.GRAIN, value);
                break;
            case OPACITY:
                if (mTarget == null) writeGlobal(SurfaceProperty.OPACITY, value);
                else writeElement(mTarget, SurfaceProperty.OPACITY, value);
                break;
            case TINT:
                if (mTarget == null) writeGlobal(SurfaceProperty.TINT, value);
                else writeElement(mTarget, SurfaceProperty.TINT, value);
                break;
            case MARGIN: writeMargin(value); break;
            case CORNER_RADIUS: writeCorners(value); break;
            case KEY_RADIUS: writeKeyCorners(value); break;
            case KEY_SPACING: writeKeySpacing(value); break;
            case DOCK_SIZE: writeDockSize(value); break;
            case APP_ICONS: writeAppIcons(value); break;
            case CONTRAST: writeLegibility(value); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------------------ the slider

    /**
     * The Look slider landed on a stop: its values written into {@code into}, which is the store
     * itself, or a drag's batch over it. A Look applies at once; leaving Custom keeps the Custom
     * values of this session, so returning to Custom brings them back even when Done has not
     * saved them. With none kept, Custom brings back the saved Custom look, or, with none saved,
     * keeps the Look it was reached from as its seed. The caller restates the launcher; false
     * when the slider was already there and nothing was written.
     */
    private boolean moveToStop(@NonNull TermuxAppSharedPreferences into, int stop) {
        if (stop == mStop)
            return false;
        boolean leavingCustom = AppearanceLooks.isCustomStop(mStop);
        SurfacePresets.Preset look = AppearanceLooks.presetForStop(SurfacePresets.presets(), stop);
        mTarget = null;
        if (look != null) {
            if (leavingCustom) {
                SurfacePresets.Preset saved = SurfacePresets.custom(into);
                mSessionCustom = saved != null && SurfacePresets.matches(into, saved)
                    ? null : AppearanceSnapshot.capture(into);
            }
            SurfacePresets.apply(into, look);
            mStop = stop;
            return true;
        }
        AppearanceSnapshot session = mSessionCustom;
        if (session != null) {
            session.restore(into);
        } else {
            SurfacePresets.Preset custom = SurfacePresets.custom(into);
            if (custom != null)
                SurfacePresets.apply(into, custom);
        }
        mStop = AppearanceLooks.CUSTOM_STOP;
        return true;
    }

    // ---- the Look slider under a finger -------------------------------------------------------
    //
    // A drag crosses the Looks faster than one can be stored and the terminal reflowed for it.
    // While the finger is down every stop it crosses walks the same transition a tap makes (the
    // session's Custom values included) into one batch over the store, and the batch is shown
    // through a SharedPreferencesPreview: every surface draws the Look under the finger from it,
    // and nothing is stored. The release stores the batch as one editor and restates the launcher
    // once. Undo and Discard compare against the snapshot taken on entry, which the preview never
    // touches; a revert drops the batch unstored.

    /** The drag's writes, held over the store; null while no Look drag is under way. */
    @Nullable private SurfacePresets.Batch mLookDrag;
    /** The preferences' setters and getters over {@link #mLookDrag}. */
    @Nullable private TermuxAppSharedPreferences mLookDragPrefs;

    /** A finger on the Look slider crossed onto {@code stop}: shown, not stored. */
    private void previewStop(int stop) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || stop == mStop)
            return;
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null) {
            // Nothing to lay a preview over: the stop is stored, as a tap would.
            if (moveToStop(prefs, stop))
                syncAfterBulkWrite();
            return;
        }
        beginDrag(true);
        if (mLookDrag == null || mLookDragPrefs == null) {
            mLookDrag = new SurfacePresets.Batch(store);
            mLookDragPrefs = SurfacePresets.writingInto(prefs, mLookDrag);
        }
        if (!moveToStop(mLookDragPrefs, stop))
            return;
        SharedPreferencesPreview.show(store, mLookDrag.frozen());
        previewLook();
    }

    /**
     * What {@link #syncAfterBulkWrite} restates, for the Look the drag is over, minus the two
     * things that wait for the release: the pane layout pass and the terminal's resize. A Look's
     * stops are few and each is a different picture, so the re-blur and the keyboard's glass run
     * at every stop rather than waiting for the release the way a slider's ticks do.
     */
    private void previewLook() {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        mHost.refreshTerminalPalette();
        mHost.applyTerminalMotionLook();
        SoftWallpaper.apply(mHost.findView(R.id.wallpaper_backdrop), prefs);
        requestPreview(SurfaceEditorProperties.PREVIEW_ALL);
        syncPanel();
        positionOutline();
    }

    /** The finger left the Look slider: what it showed is stored, once, and restated once. */
    private void settleLookDrag() {
        SurfacePresets.Batch batch = mLookDrag;
        mLookDrag = null;
        mLookDragPrefs = null;
        if (batch == null)
            return;
        // Stored before the preview goes, so no read in between finds the Look the drag left.
        batch.applyToStore();
        SharedPreferencesPreview.clear();
        syncAfterBulkWrite();
    }

    /** The drag's batch dropped unstored: a revert is putting the entry state back. */
    private void dropLookDrag() {
        if (mLookDrag == null)
            return;
        mLookDrag = null;
        mLookDragPrefs = null;
        SharedPreferencesPreview.clear();
    }

    // ---------------------------------------------------------------------------- the controls

    /** The grain a surface draws with now, in percent. */
    private static int grainOf(@NonNull TermuxAppSharedPreferences prefs, @Nullable SurfaceSlot slot) {
        if (slot == null)
            return 0;
        switch (slot) {
            case STATUS: return AppearanceLooks.grainPercent(prefs.getStatusBarGrain());
            case CANVAS: return AppearanceLooks.grainPercent(prefs.getTerminalGlassGrain());
            case KEYBOARD: return AppearanceLooks.grainPercent(prefs.getInAppKeyboardGrain());
            case DOCK: return AppearanceLooks.grainPercent(prefs.getDockGlassGrain());
            default: return 0;
        }
    }

    /** The tint strength a surface draws with now, in percent. */
    private static int tintOf(@NonNull TermuxAppSharedPreferences prefs, @Nullable SurfaceSlot slot) {
        if (slot == null)
            return AppearanceLooks.TINT_MAX;
        switch (slot) {
            case STATUS: return AppearanceLooks.tintPercent(prefs.getStatusBarTintStrength());
            case CANVAS: return AppearanceLooks.tintPercent(prefs.getTerminalTintStrength());
            case KEYBOARD: return AppearanceLooks.tintPercent(prefs.getInAppKeyboardTintStrength());
            case DOCK: return AppearanceLooks.tintPercent(prefs.getDockTintStrength());
            default: return AppearanceLooks.TINT_MAX;
        }
    }

    /** The blur a surface draws with now, in dp. */
    private static int blurOf(@NonNull TermuxAppSharedPreferences prefs, @NonNull Target target) {
        switch (target.slot) {
            case STATUS: return AppearanceLooks.blurDp(prefs.getStatusBarBlurRadius());
            case CANVAS: return AppearanceLooks.blurDp(prefs.getTerminalGlassBlurRadius());
            case KEYBOARD: return AppearanceLooks.blurDp(prefs.getInAppKeyboardBlurRadius());
            case DOCK: return AppearanceLooks.blurDp(prefs.getExtraKeysBlurRadius());
            default: return 0;
        }
    }

    /** The opacity a surface draws with now, in percent. */
    private static int opacityOf(@NonNull TermuxAppSharedPreferences prefs, @NonNull Target target) {
        switch (target.slot) {
            case STATUS: return AppearanceLooks.opacityPercent(prefs.getStatusBarOpacity());
            case CANVAS: return AppearanceLooks.opacityPercent(prefs.getTerminalBackgroundOpacity());
            case KEYBOARD:
                return AppearanceLooks.opacityPercent(prefs.getInAppKeyboardBackgroundOpacity());
            case DOCK: return AppearanceLooks.opacityPercent(prefs.getAppBarOpacity());
            default: return 0;
        }
    }

    /**
     * Blur, Grain or Opacity on one element: the element's own value, which leaves the shared base
     * so the other surfaces keep theirs. The terminal's Opacity writes opacity alone: the glass
     * tint is the Look's and no slider moves it.
     */
    private void writeElement(@NonNull Target target, @NonNull SurfaceProperty property,
                              int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int written;
        int scopes = SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD;
        switch (property) {
            case BLUR:
                written = AppearanceLooks.blurDp(value);
                scopes |= SurfaceEditorProperties.PREVIEW_BLUR;
                break;
            case GRAIN:
                written = AppearanceLooks.grainPercent(value);
                break;
            case TINT:
                written = AppearanceLooks.tintPercent(value);
                break;
            default:
                written = AppearanceLooks.opacityPercent(value);
                break;
        }
        prefs.detachSurfaceValue(target.slot, property, written);
        if (target == Target.TERMINAL && property == SurfaceProperty.OPACITY) {
            // The terminal's opacity also keeps the copy the wallpaper mode restores from.
            prefs.setTerminalBackgroundOpacity(written);
        }
        requestPreview(scopes);
    }

    /**
     * Blur with nothing tapped: one value for every surface, every surface re-attached to it.
     */
    private void writeBlur(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        GlobalGlass.write(prefs, SurfaceProperty.BLUR, value);
        requestPreview(SurfaceEditorProperties.PREVIEW_BLUR
            | SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    /**
     * The global Opacity and Grain (DECISIONS item 14): the base value, every surface, terminal
     * included, re-attached to it, as Blur has always done. An element's own Opacity or Grain
     * detaches it again afterwards.
     */
    private void writeGlobal(@NonNull SurfaceProperty property, int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        GlobalGlass.write(prefs, property, value);
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD);
    }

    /**
     * Key spacing: the key margin scale (0.0 to 8.0), previewed live on the keyboard the way the
     * height-adjust overlay's slider does.
     */
    private void writeKeySpacing(int tenths) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        float scale = AppearanceLooks.keySpacingScaleFor(tenths);
        prefs.setInAppKeyboardKeyMarginScale(scale);
        TermuxInAppKeyboard keyboard = keyboard();
        if (keyboard != null)
            keyboard.previewSurfaceEditorKeyMarginScale(scale);
        requestPreview(SurfaceEditorProperties.PREVIEW_KEYBOARD);
    }

    /**
     * Dock size: the dock height scale Layout mode's dock handle writes, through the layout
     * session, so its Undo and dirty state cover it. Applied live as the handle's drag is.
     */
    private void writeDockSize(int percent) {
        float scale = AppearanceLooks.dockScaleFor(percent);
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null) {
            layout.setDockHeightScale(scale);
        } else if (prefs() != null) {
            prefs().setAppLauncherBarHeightScale(scale);
            requestGeometryPreview();
        }
        syncDirty();
    }

    /** App icons: how many app buttons the dock shows, rebuilt live. */
    private void writeAppIcons(int count) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int written = Control.APP_ICONS.clamp(count);
        if (written == prefs.getAppLauncherButtonCount())
            return;
        prefs.setAppLauncherButtonCount(written);
        mHost.applyDockButtonCount(written);
        requestGeometryPreview();
    }

    /**
     * The keyboard's "Keyboard theme" door (DECISIONS item 15): the Keyboard theme page in
     * Settings, over the editor. The session stays open under it — a stop is not a close
     * (TermuxActivity's overlay registry keeps the editor across STOP) — and Back returns to it,
     * where {@link #onActivityStarted} reads the page's changes into the frame. The editor's Undo
     * does not cover what is changed on that page.
     */
    private void openKeyboardTheme() {
        Context context = mHost.context();
        try {
            context.startActivity(SettingsActivity.createFragmentIntent(context,
                KeyboardColorSchemeFragment.class, R.string.appearance_editor_keyboard_theme));
        } catch (RuntimeException e) {
            // No Settings to open (a stripped build): the editor stays as it is.
        }
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

    /** Trail: the cursor-trail style, the same preference Settings writes, applied on the tap. */
    private void writeTrailStyle(@NonNull String id) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || id.equals(prefs.getTerminalCursorTrailStyle()))
            return;
        prefs.setTerminalCursorTrailStyle(id);
        mHost.applyTerminalMotionLook();
    }

    /** Effect: the retro terminal effect, the same preference Settings writes. */
    private void writeRetroEffect(@NonNull String id) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || id.equals(prefs.getTerminalRetroEffect()))
            return;
        prefs.setTerminalRetroEffect(id);
        mHost.applyTerminalMotionLook();
    }

    /** Key radius: the key caps' radius, and nothing else; Layout mode's (DECISIONS item 6). */
    private void writeKeyCorners(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int radius = AppearanceLooks.keyCornersDp(value);
        prefs.setInAppKeyboardKeyCornerRadiusDp(radius);
        TermuxInAppKeyboard keyboard = keyboard();
        if (keyboard != null)
            keyboard.previewSurfaceEditorKeyCornerRadiusDp(radius);
        // Its label is Layout mode's, beside the type chips, and is restated there.
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

    // ------------------------------------------------------------------------- the selection

    /**
     * A tap in the frame. At a Look the slider moves to Custom first, seeded from that Look — the
     * live values are the Look's, so nothing is written — and then the element opens in row 2.
     */
    private void select(@NonNull Target target) {
        if (!mOpen)
            return;
        if (!scene().offersSurface(target.slot))
            return;
        if (!AppearanceLooks.isCustomStop(mStop))
            mStop = AppearanceLooks.CUSTOM_STOP;
        mTarget = target;
        afterSelection();
    }

    /**
     * A tap on a bare area of the frame (the wallpaper group): at the Custom stop it deselects
     * and the global set comes back; at a Look stop it does nothing.
     */
    private void tapWallpaper() {
        if (!mOpen || !AppearanceLooks.wallpaperTapDeselects(mStop))
            return;
        mTarget = null;
        afterSelection();
    }

    private void afterSelection() {
        syncPanel();
        positionOutline();
    }

    // ---------------------------------------------------------------------- the frame's targets

    private static final int[] GROUP_IDS = {
        R.id.surface_tuning_wallpaper_gesture_group,
        R.id.surface_tuning_canvas_gesture_group,
        R.id.surface_tuning_status_gesture_group,
        R.id.surface_tuning_dock_gesture_group,
        R.id.surface_tuning_keyboard_gesture_group};

    /** The element each group selects; null is the wallpaper group, which selects none. */
    private static final Target[] GROUP_TARGETS = {
        null, Target.TERMINAL, Target.STATUS, Target.DOCK, Target.KEYBOARD};

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
            group.setOnClickListener(view -> {
                // Icons mode has no targets: the launcher is only to be looked at.
                if (mIconsMode)
                    return;
                if (target == null) tapWallpaper();
                else select(target);
            });
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
    private int[] rectOf(@Nullable Target target, @NonNull View overlay) {
        View root = mHost.findView(R.id.terminal_root_container);
        if (root == null)
            return null;
        int[] overlayOffset = new int[2];
        if (!AppearanceEditorFrame.offsetIn(overlay, root, overlayOffset))
            return null;
        if (target == null)
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

    /** Lays every tap target over its element, then the outline. */
    private void positionTargets() {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null || !mOpen || overlay.getWidth() <= 0)
            return;
        for (int i = 0; i < GROUP_IDS.length; i++) {
            View group = mHost.findView(GROUP_IDS[i]);
            if (group == null)
                continue;
            // Icons mode: the bare-wallpaper group alone stands over the whole frame as a touch
            // sink, so the launcher takes no tap; no element is a target.
            if (mIconsMode && GROUP_TARGETS[i] != null) {
                group.setVisibility(View.GONE);
                continue;
            }
            int[] rect = rectOf(GROUP_TARGETS[i], overlay);
            if (rect == null) {
                group.setVisibility(View.GONE);
                continue;
            }
            placeInOverlay(group, overlay, rect);
            group.setVisibility(View.VISIBLE);
        }
        positionOutline();
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
        Target target = mOpen && !mIconsMode && AppearanceLooks.isCustomStop(mStop) ? mTarget : null;
        int[] rect = target == null ? null : rectOf(target, overlay);
        if (rect == null) {
            outline.setVisibility(View.GONE);
            return;
        }
        int stroke = Math.max(1, dp(OUTLINE_STROKE_DP));
        // The outline stands just outside its element, so the stroke never covers what it points at.
        int grow = stroke;
        int[] grown = {rect[0] - grow, rect[1] - grow, rect[2] + grow, rect[3] + grow};
        placeInOverlay(outline, overlay, grown);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0);
        ring.setStroke(stroke, mHost.themeColor(androidx.appcompat.R.attr.colorPrimary,
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
            default:
                return mHost.terminalFrameCornerRadiusPx();
        }
    }

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
            if (mTarget != null && !scene().offersSurface(mTarget.slot)) {
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
    // The status bar's one control that is a look rather than a number: the Clock button above
    // the sliders, opening the six faces upward, drawn as themselves, with the face's position
    // beneath them. Live like every other editor control, and gated by Done.

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

    @Nullable private ListPopupWindow mClockDropdown;

    /**
     * The drop-down's rows: the six faces, then one row for the face's place in the pane. A real
     * list popup, so the theme's popup style gives it its surface, shape and elevation.
     */
    private final class ClockDropdownAdapter extends BaseAdapter {
        private final String mCurrent;

        ClockDropdownAdapter(@NonNull String current) {
            mCurrent = current;
        }

        @Override public int getCount() { return CLOCK_STYLES.length + 1; }

        @Override public Object getItem(int position) {
            return position < CLOCK_STYLES.length ? CLOCK_STYLES[position] : null;
        }

        @Override public long getItemId(int position) { return position; }

        @Override public int getViewTypeCount() { return 2; }

        @Override public int getItemViewType(int position) {
            return position < CLOCK_STYLES.length ? 0 : 1;
        }

        @Override public boolean areAllItemsEnabled() { return false; }

        /** The position row is made of controls of its own; the list only takes the faces. */
        @Override public boolean isEnabled(int position) { return position < CLOCK_STYLES.length; }

        @Override public View getView(int position, @Nullable View convertView,
                                      @NonNull ViewGroup parent) {
            Context context = parent.getContext();
            if (position >= CLOCK_STYLES.length)
                return clockPositionRow(context);
            return clockFaceRow(context, CLOCK_STYLES[position], mCurrent);
        }
    }

    /**
     * The Clock button's popup: the six faces drawn as themselves, over the frame. The button
     * stands in the sheet at the foot of the screen, so the popup opens upward from its top edge
     * (a negative vertical offset of the anchor's height plus the popup's own), aligned to the
     * button's end, and never taller than the room above the button.
     */
    private void showClockDropdown(@NonNull View anchor) {
        if (prefs() == null)
            return;
        dismissClockDropdown();
        Context context = mHost.context();
        ListPopupWindow popup = new ListPopupWindow(context);
        popup.setAnchorView(anchor);
        popup.setModal(true);
        int windowWidth = Math.min(anchor.getRootView().getWidth(),
            getResources().getDisplayMetrics().widthPixels);
        popup.setWidth(windowWidth * 3 / 4);
        popup.setDropDownGravity(Gravity.END);
        int[] anchorAt = new int[2];
        anchor.getLocationInWindow(anchorAt);
        int room = Math.max(dp(CLOCK_POPUP_MIN_HEIGHT_DP), anchorAt[1] - dp(FRAME_GAP_DP));
        int height = Math.min(dp(CLOCK_POPUP_HEIGHT_DP), room);
        popup.setHeight(height);
        popup.setVerticalOffset(-(anchor.getHeight() + height));
        popup.setOnDismissListener(() -> mClockDropdown = null);
        popup.setAdapter(new ClockDropdownAdapter(prefs().getTopPaneClockStyle()));
        popup.setOnItemClickListener((parent, view, position, id) -> {
            if (position < CLOCK_STYLES.length) {
                pickClockStyle(CLOCK_STYLES[position]);
                popup.dismiss();
            }
        });
        popup.setBackgroundDrawable(EditorM3.surface(anchor,
            com.google.android.material.R.attr.shapeAppearanceCornerMedium,
            com.google.android.material.R.attr.colorSurfaceContainerHigh));
        mClockDropdown = popup;
        popup.show();
    }

    /** Six 48dp face rows and the position row, as the popup wants to stand. */
    private static final int CLOCK_POPUP_HEIGHT_DP = 6 * 48 + 56;
    private static final int CLOCK_POPUP_MIN_HEIGHT_DP = 160;

    private void dismissClockDropdown() {
        if (mClockDropdown == null)
            return;
        mClockDropdown.dismiss();
        mClockDropdown = null;
    }

    /** Gives a text view the theme's text appearance behind {@code attr}. */
    private static void applyTextAppearance(@NonNull TextView view, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        if (view.getContext().getTheme().resolveAttribute(attr, value, true)
            && value.resourceId != 0)
            view.setTextAppearance(value.resourceId);
    }

    /** One face in the drop-down: its name, the face itself, and a tick on the one in use. */
    @NonNull
    private View clockFaceRow(@NonNull Context context, @NonNull String style,
                              @NonNull String current) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPaddingRelative(dp(16), 0, dp(16), 0);
        boolean selected = style.equals(current);

        TextView name = new TextView(context);
        name.setText(clockStyleLabel(style));
        applyTextAppearance(name, com.google.android.material.R.attr.textAppearanceLabelLarge);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(EditorM3.color(row, selected
            ? androidx.appcompat.R.attr.colorPrimary
            : com.google.android.material.R.attr.colorOnSurface));
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
        applyTextAppearance(tick, com.google.android.material.R.attr.textAppearanceTitleMedium);
        tick.setGravity(Gravity.CENTER);
        tick.setTextColor(EditorM3.color(row, androidx.appcompat.R.attr.colorPrimary));
        tick.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        tick.setLayoutParams(new LinearLayout.LayoutParams(
            dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(tick);

        row.setContentDescription(getString(R.string.termux_surface_tuning_clock_face_description,
            getString(clockStyleLabel(style))));
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
        return row;
    }

    /**
     * Where the face sits in the pane, left, centre or right, as the theme's segmented buttons
     * under the faces. The popup stays open for a second look instead of dismissing like a face
     * pick does.
     */
    @NonNull
    private View clockPositionRow(@NonNull Context context) {
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(context);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.setContentDescription(getString(R.string.settings_clock_alignment_title));
        String current = prefs() == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_ALIGNMENT
            : prefs().getTopPaneClockAlignment();
        final java.util.Map<Integer, String> alignmentOf = new java.util.HashMap<>();
        int currentId = View.NO_ID;
        for (String alignment : CLOCK_ALIGNMENTS) {
            MaterialButton button = (MaterialButton) android.view.LayoutInflater.from(context)
                .inflate(R.layout.segment_button, group, false);
            button.setId(View.generateViewId());
            button.setText(clockAlignmentLabel(alignment));
            button.setMaxLines(1);
            button.setContentDescription(getString(
                R.string.termux_surface_tuning_clock_position_description,
                getString(clockAlignmentLabel(alignment))));
            button.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            group.addView(button);
            alignmentOf.put(button.getId(), alignment);
            if (alignment.equals(current))
                currentId = button.getId();
        }
        if (currentId != View.NO_ID)
            group.check(currentId);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            String alignment = alignmentOf.get(checkedId);
            if (isChecked && alignment != null)
                pickClockAlignment(alignment);
        });
        FrameLayout holder = new FrameLayout(context);
        holder.setPadding(dp(16), dp(4), dp(16), dp(4));
        holder.addView(group, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
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
        dropLookDrag();
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null && layout.isDirty())
            layout.revert();
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || mEntry == null)
            return;
        mEntry.restore(prefs);
        mSessionCustom = null;
        mStop = mEntryStop;
        mTarget = null;
        syncAfterBulkWrite();
    }

    /** Saves the look: at the Custom stop it is kept as Custom, so the stop brings it back. */
    private void commitLook() {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs != null && AppearanceLooks.isCustomStop(mStop))
            SurfacePresets.saveCustom(prefs);
    }

    /** Done: the look is saved, and the surface takes the editor off screen. */
    private void commitAndExit() {
        commitLook();
        Runnable onDone = mOnDone;
        if (onDone != null)
            onDone.run();
    }

    /**
     * Leaving the editor page, by Back or by a HOME press. Nothing to lose: {@code proceed} runs
     * at once. Otherwise it asks, once for the look and the arrangement together — Keep editing
     * (nothing runs) / Discard / Save — rather than choosing for the user; the live write-through
     * means "leave" would otherwise mean "keep" by accident. Back with a bar in the air cancels
     * the lift and nothing else (DECISIONS item 4); {@code proceed} does not run then either.
     */
    @Override
    public void requestLeave(@NonNull Runnable proceed) {
        if (!mOpen) {
            proceed.run();
            return;
        }
        LayoutEditorController layoutEditor = mHost.layoutEditor();
        if (mLayoutMode && layoutEditor != null && layoutEditor.cancelGesture())
            return;
        if (!isDirty()) {
            proceed.run();
            return;
        }
        showUnsavedDialog(() -> {
            commitLook();
            proceed.run();
        }, proceed);
    }

    /**
     * Leaving with something besides the editor unsaved (the Overview's pending wallpaper): the
     * one question, asked whether or not the editor is dirty. Discard reverts the editor when it
     * is up; Save keeps the look.
     */
    @Override
    public void confirmLeave(@NonNull Runnable onSave, @NonNull Runnable onDiscard) {
        LayoutEditorController layoutEditor = mHost.layoutEditor();
        if (mOpen && mLayoutMode && layoutEditor != null && layoutEditor.cancelGesture())
            return;
        showUnsavedDialog(() -> {
            if (mOpen)
                commitLook();
            onSave.run();
        }, onDiscard);
    }

    /** Keep editing (nothing runs) / Discard (the editor back to how it was at open, then {@code discard}) / Save. */
    private void showUnsavedDialog(@NonNull Runnable save, @NonNull Runnable discard) {
        new MaterialAlertDialogBuilder(mHost.context())
            .setTitle(R.string.termux_surface_tuning_unsaved_title)
            .setMessage(R.string.termux_layout_editor_unsaved_message)
            .setNeutralButton(R.string.termux_surface_tuning_unsaved_keep_editing, null)
            .setNegativeButton(R.string.termux_surface_tuning_unsaved_discard,
                (dialog, which) -> {
                    if (mOpen)
                        revertToEntry();
                    discard.run();
                })
            .setPositiveButton(R.string.termux_surface_tuning_unsaved_save,
                (dialog, which) -> save.run())
            .show();
    }

    /** The phone turned: Layout mode's canvas default and next write turn with it. */
    public void onPlaceOrientationChanged() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (mOpen && layout != null)
            layout.onPlaceOrientationChanged();
    }

    /**
     * Takes the editor off screen and keeps the session. The frame goes to {@code toScale} and
     * {@code toTranslationY} (the Overview's Home card) when {@code toScale} is above zero, else to
     * full size; {@code onHidden} runs when it has arrived. The launcher is left at that pose and
     * the keyboard up: {@link #restoreLauncher} puts both right once the Overview covers it.
     */
    @Override
    public void dismiss(float toScale, float toTranslationY, @Nullable Runnable onHidden) {
        if (!mOpen) {
            if (onHidden != null)
                onHidden.run();
            return;
        }
        dismissClockDropdown();
        // A drag cut short by the leave keeps what it showed, as every editor write is kept.
        if (mSliderDragActive)
            endDrag();
        // The bar goes back to its stored shape as the editor leaves, from the same moment.
        mHost.setTopStatusBarExpandedForEditor(false, true);
        mOpen = false;
        mFramed = false;
        mPresentToken++;
        mSliderDragActive = false;
        mEntry = null;
        mEntrySignature = null;
        mTarget = null;
        mRestoreTarget = null;
        mOpenInLayout = false;
        mPanelRevealing = false;
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null) {
            layout.end();
            layout.setOnChangedListener(null);
        }
        boolean wasLayout = mLayoutMode;
        if (mIconsMode)
            showIconsPage(false);
        mLayoutMode = false;
        mIconsMode = false;
        cancelFrameAnimator();
        mFrameCtxValid = false;
        mTargetScale = -1f;
        applyModelShape(false, false);
        fadeLayoutFrame(false, wasLayout, 0L);
        setOverlayVisible(false);
        unregisterLayoutListener();
        View outline = mHost.findView(R.id.surface_editor_selection_outline);
        if (outline != null)
            outline.setVisibility(View.GONE);
        final AppearanceEditorPanel panel = mPanel;
        if (panel != null) {
            View view = panel.view();
            view.animate().cancel();
            if (mPanelAnimator != null)
                finishPanelAnimation(view);
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
        AppearanceEditorFrame frame = mFrame;
        if (frame == null) {
            if (onHidden != null)
                onHidden.run();
            return;
        }
        if (toScale > 0f)
            frame.moveTo(toScale, toTranslationY, onHidden);
        else
            frame.hide(true, onHidden);
    }

    /**
     * The launcher back at full size and repainted, the keyboard the editor raised taken down:
     * run while the Overview fully covers it, never under the hop. The chrome is not dressed again
     * here; only the end of the session does that.
     */
    @Override
    public void restoreLauncher() {
        if (mOpen || !mSession)
            return;
        AppearanceEditorFrame frame = mFrame;
        if (frame != null) {
            frame.hide(false, null);
            frame.repaintAll();
        }
        if (mRaisedKeyboard)
            mHost.hideInAppKeyboardForEditor();
        mRaisedKeyboard = false;
    }

    /**
     * Ends the session: the editor goes down first when it is up, then the editor's wallpaper and
     * the opaque window go, and the chrome is dressed as a launch would — only now, with the root
     * at identity: a pass run while it was scaled aimed every glass surface at the scaled frame,
     * and nothing draws them again.
     */
    @Override
    public void endSession() {
        if (!mSession)
            return;
        if (mOpen) {
            dismiss(-1f, 0f, this::finishSession);
            return;
        }
        finishSession();
    }

    private void finishSession() {
        if (mOpen || !mSession)
            return;
        mSession = false;
        mRestore = null;
        mWallpaperToken++;
        mWallpaperSettled = false;
        mWallpaperWaiter = null;
        mEditorWallpaper = null;
        mKeyboardOwed = false;
        releaseIconsContent();
        AppearanceEditorFrame frame = mFrame;
        if (frame != null) {
            frame.hide(false, null);
            frame.hideWallpaper();
            mHost.redressChrome();
            frame.repaintAll();
        } else {
            mHost.redressChrome();
        }
        if (mRaisedKeyboard)
            mHost.hideInAppKeyboardForEditor();
        mRaisedKeyboard = false;
        mHost.holdPaneWall(false);
        mHost.setTopStatusBarExpandedForEditor(false, false);
    }

    private void releaseIconsContent() {
        AppearanceSurfaceController.Page page = mIconsPage;
        mIconsPage = null;
        AppearanceEditorPanel panel = mPanel;
        if (panel != null)
            panel.setIconsContent(null);
        if (page != null)
            page.release();
    }

    /** The activity stopped with the surface up: the status pane's borrowed shape goes back. */
    @Override
    public void onStopWhileOpen() {
        // A drag the stop cut short (no lift will come) keeps what it showed.
        if (mSliderDragActive)
            endDrag();
        mHost.setTopStatusBarExpandedForEditor(false, false);
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

    /**
     * Whether a drag holds the re-blur and the keyboard's reload for its release: a slider's
     * ticks do; a Look drag's stops do not (see {@link #previewLook}).
     */
    private boolean defersHeavyPasses() {
        return mSliderDragActive && mLookDrag == null;
    }

    private void endDrag() {
        mSliderDragActive = false;
        settleLookDrag();
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
        if (defersHeavyPasses() && (scopes & SurfaceEditorProperties.PREVIEW_BLUR) != 0) {
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
            if (defersHeavyPasses()) mDragTouchedKeyboard = true;
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
        mHost.applyTerminalMotionLook();
        SoftWallpaper.apply(mHost.findView(R.id.wallpaper_backdrop), prefs);
        if (keyboard() != null)
            keyboard().onPreferencesReloaded();
        applyStructuralPreview();
        syncPanel();
        // An Undo can move Style or Corners under a showing canvas.
        syncLayoutCanvas();
        positionOutline();
    }
}
