package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.DashPathEffect;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.chrome.GlassMotion;
import com.termux.app.dock.DockStyle;
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.fragments.settings.termux.AppearanceSettingsStore;
import com.termux.app.fragments.settings.termux.HomePreferencesFragment;
import com.termux.app.fragments.settings.termux.KeyboardColorSchemeFragment;
import com.termux.app.fragments.settings.termux.KeyboardLayoutsFragment;
import com.termux.app.fragments.settings.termux.KeyboardSizePreferencesFragment;
import com.termux.app.fragments.settings.termux.LauncherDockPreferencesFragment;
import com.termux.app.fragments.settings.termux.StatusBarPreferencesFragment;
import com.termux.app.fragments.settings.termux.TermuxFontsPreferencesFragment;
import com.termux.app.fragments.settings.termux.TermuxStylePreferencesFragment;
import com.termux.app.launcher.home.HomeSettings;
import com.termux.app.layouteditor.EditorM3;
import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.layouteditor.LayoutEditorPlan;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.SeamDress;
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.app.surfaces.AppearanceLooks.Control;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.terminal.Motion;
import com.termux.app.terminal.PaneRetroEffect;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.preferences.SharedPreferencesPreview;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Appearance editor (appearance-and-settings-rework §2, §3, §7, §8): the launcher itself,
 * live, scaled into a frame, on one rule: <b>tap the thing</b>. A tap on a surface in the frame
 * (Home, the status bar, the terminal, the dock, the keyboard) raises its card; the editor bar's
 * palette raises the Appearance card for what has no surface. A hold on a surface drags it to
 * another edge; a plain horizontal swipe slides the frame between Home and the Workspace, and a
 * card writes the half the frame shows.
 *
 * <p><b>The frame.</b> {@code terminal_root_container} scaled about a top-centre pivot
 * ({@link AppearanceEditorFrame}), at rest between the top line and the half line over the editor
 * bar ({@link EditorBar}); every surface wears a quiet outline, and a hidden one is a card in the stack
 * beside the frame ({@link HiddenStackView}). With a card up the frame shrinks and moves so the selected surface, which alone wears the
 * primary ring, stays in view ({@link EditorFramePose}). While the editor is up the terminal's own
 * touches are taken by the gesture overlay, the in-app keyboard is raised so it can be tapped, and
 * the pane wall's paging is held.</p>
 *
 * <p><b>The cards.</b> One sheet ({@link EditorCardSheet}) shows any card: its rows come from
 * {@link EditorRows}, its values and writes from this class ({@link CardModel}). A slider moved on
 * a card is the surface's own value on the half the frame shows (a dot after its label); Reset in
 * the card's menu puts the surface back on that half's Look, and Copy makes the other half look
 * the same. Moving a surface's slider at any Look stop leaves the half at Custom by itself.</p>
 *
 * <p><b>Commit on Done.</b> Everything writes through, so the frame is the real thing; Undo and
 * Discard put back the state at open: the look on both halves ({@link AppearanceSnapshot}), the
 * app-wide choices ({@link EditorSettingsSnapshot}), minimal mode, and the arrangement, whose
 * session ({@link LayoutEditorController}) opens and closes with the editor. One dirty state, one
 * Undo, one Done and one unsaved-changes question cover all of it.</p>
 */
public final class SurfaceEditorController implements AppearanceSurfaceController.Editor {

    private static final String LOG_TAG = "SurfaceEditor";

    /** What the editor needs from the activity: its views, its prefs, and its render pipeline. */
    public interface Host {
        @NonNull Context context();
        @Nullable <T extends View> T findView(int viewId);
        @Nullable TermuxAppSharedPreferences preferences();
        /** How the place on screen is arranged: which surfaces the frame has to offer. */
        @NonNull PlaceLayout placeLayout();
        /** The page the wall rests on, which the editor stands over. */
        @NonNull PaneWallPage pageOnScreen();
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
        /** The pinned-apps row shares its container with nothing; Height reads its icon off it. */
        boolean isAppsRowAlone();
        /** Lets the top status bar rest as it is stored (the editor never holds it open). */
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
        /** The session is over and the chrome is dressed again. */
        default void editorSessionEnded() { }
        void refreshTerminalWindowBar();
        /** Rebuilds the terminal palette if the contrast level moved. */
        void refreshTerminalPalette();
        /** Pushes the cursor trail and the terminal effect into the panes. */
        void applyTerminalMotionLook();
        /** Dock geometry changed; with {@code commit} the terminal is also resized. */
        void applyGeometryPreview(boolean commit);
        /** The coalesced glass re-render. */
        void applyGlassPreview();
        /** The shared Text size moved; with {@code terminal} the panes too. */
        default void applyTextSizePreview(boolean terminal) {}
        /** Holds the frame tint's re-bake for a drag's release while {@code held}. */
        default void holdGlassFrameBakes(boolean held) {}
        /** While {@code light}, the glass passes are a drag's ticks. */
        default void setGlassDragPreview(boolean light) {}
        /** Keeps the unblurred wallpaper capture while the editor is open. */
        default void retainGlassCapture(boolean retain) {}
        /** Whether the glass edge is drawn now: Depth and the glass edge are offered only then. */
        boolean isGlassRefractionActive();
        /** The terminal's own frame in {@code terminal_root_container}'s unscaled coordinates. */
        @Nullable int[] terminalFrameRectInRoot();
        /** The corner that frame actually draws with. */
        float terminalFrameCornerRadiusPx();
        /** The corner the keyboard surface itself is clipped to. */
        float keyboardSurfaceCornerRadiusPx();
        /** The move-and-size engine, which the editor lends its canvas to. */
        @Nullable LayoutEditorController layoutEditor();
        /** The wallpaper for the editor to paint inside the frame, or null. Off the main thread. */
        @Nullable Drawable readEditorWallpaper();
        /** The dim the window's root paints over the system wallpaper. */
        int editorWallpaperDimColor();
        /** Whether the frame has to paint the wallpaper itself (passthrough mode). */
        boolean editorPaintsWallpaper();
        /** The icon packs for the Appearance card's Icon pack row, built once per session. */
        @Nullable
        default AppearanceSurfaceController.Page createIconsContent() {
            return null;
        }
        /** Whether minimal mode is on. */
        default boolean isMinimalMode() {
            return false;
        }
        /** Turns minimal mode on or off, as the corner tab does. */
        default void setMinimalMode(boolean minimal) {}
        /** Home's rows were written: the live Home takes them now. */
        default void applyHomeSettings() {}
        /**
         * The "Glass widgets" switch moved: every built-in widget's own glass choice goes back to
         * following it. Returns what each cleared widget held, by id, for Undo.
         */
        @NonNull
        default java.util.Map<Integer, String> clearWidgetGlassChoices() {
            return java.util.Collections.emptyMap();
        }
        /** Undo of {@link #clearWidgetGlassChoices}: the widgets get their own choices back. */
        default void restoreWidgetGlassChoices(@NonNull java.util.Map<Integer, String> choices) {}
        /**
         * The window wears {@code mode} (a {@code night-mode} value) without storing it; the
         * activity relaunches into it when the day or night it shows changes.
         */
        default void previewTheme(@NonNull String mode) {}
        /** Stores {@code mode} as Settings' theme row does, for a window already wearing it. */
        default void commitTheme(@NonNull String mode) {}
    }

    @NonNull private final Host mHost;

    public SurfaceEditorController(@NonNull Host host) {
        mHost = host;
    }

    /** Whether the editor's session is open. */
    public boolean isActive() {
        return mSession;
    }

    /** Whether the editor's frame is on screen. */
    @Override
    public boolean isPresented() {
        return mOpen;
    }

    /** A view's top-left in {@code root}'s own unscaled coordinates. */
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

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------------------ session state

    private boolean mOpen;
    /** The state at open: what Undo and Discard return to, and what "unsaved" is measured against. */
    @Nullable private AppearanceSnapshot mEntry;
    @Nullable private String mEntrySignature;
    /** The app-wide choices at open, and their signature as it stands (refreshed on a write). */
    @Nullable private EditorSettingsSnapshot mEntrySettings;
    @Nullable private String mSettingsNow;
    /** Minimal mode at open. */
    private boolean mEntryMinimal;
    /** The saved Custom blob at open, which Undo and Discard put back; null while closed. */
    @Nullable private String mEntryCustom;
    /** The Custom values of this session, kept while a Look is showing. */
    @NonNull private final SessionCustom mSessionCustom = new SessionCustom();
    /**
     * The widgets' own glass choices the "Glass widgets" switch cleared this visit, by id, as
     * each first stood: what Undo gives back.
     */
    @NonNull private final java.util.Map<Integer, String> mClearedWidgetGlass =
        new java.util.LinkedHashMap<>();
    /**
     * The halves the Look slider was put on Custom this visit while their values still match a
     * Look: Custom then writes nothing (the Look on screen is its seed), so without this the
     * slider would read the Look again and the All surfaces strip would never show.
     */
    @NonNull private final java.util.EnumSet<WallHalf> mHeldCustom =
        java.util.EnumSet.noneOf(WallHalf.class);
    /** The last Depth position the user set this session ({@link GlassEdgeControls}). */
    private int mDepthSet = GlassEdgeControls.NONE;
    /** The half the frame shows: where a card writes. */
    @NonNull private WallHalf mHalf = WallHalf.WORKSPACE;
    /** The card that is up, or null. */
    @Nullable private EditorCard mCard;
    /** Whether the editor raised the in-app keyboard itself, and so owes taking it down. */
    private boolean mRaisedKeyboard;
    /** The session a relaunch handed over, continued by the next presentation. */
    @Nullable private CarriedSession mCarriedNext;

    @Nullable private AppearanceEditorFrame mFrame;
    @Nullable private EditorBar mBar;
    @Nullable private EditorCardSheet mSheet;
    @NonNull private final CardModel mModel = new CardModel();
    private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;
    private long mLayoutSignature = Long.MIN_VALUE;
    @Nullable private AppearanceSurfaceController.Page mIconsPage;

    /** The air the clock popup keeps from the window's edges. */
    private static final int FRAME_GAP_DP = EditorFramePose.GAP_DP;
    /** The selected surface's ring and every surface's quiet outline, on screen. */
    private static final int RING_DP = 2;
    private static final int QUIET_DP = 1;
    /** The quiet outline's share of colorOutline. */
    private static final float QUIET_ALPHA = 0.4f;
    private static final long POSE_MS = 240L;

    /** The activity resumed with the editor open: the status pane rests as it is stored. */
    public void collapseStatusPaneIfLeftExpanded() {
        mHost.setTopStatusBarExpandedForEditor(false, false);
    }

    /**
     * The activity came back to the front with the editor still open — from a Settings page a
     * card's row opened. The session is the same one; what that page changed is read again.
     */
    public void onActivityStarted() {
        if (!mOpen)
            return;
        TermuxInAppKeyboard keyboard = keyboard();
        if (keyboard != null)
            keyboard.onPreferencesReloaded();
        refreshSettingsSignature();
        restateCard();
        positionTargets();
    }

    // ----------------------------------------------------------------------------------- session

    private boolean mSession;
    @Nullable private Runnable mOnDone;
    private boolean mWallpaperSettled;
    @Nullable private Runnable mWallpaperWaiter;
    private int mPresentToken;

    @Override
    public void setOnDone(@Nullable Runnable onDone) {
        mOnDone = onDone;
    }

    /** Opens the session: holds the wall and starts reading the editor's wallpaper now. */
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
        if (mHost.editorPaintsWallpaper())
            loadEditorWallpaper(rootContainer);
        else
            mWallpaperSettled = true;
    }

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
            if (hasSize(root) && hasSize(content))
                ready.run();
            else
                failed.run();
        }, FRAME_WAIT_MS);
    }

    private static final long FRAME_WAIT_MS = 1000L;

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
     * Shows the editor over the launcher, with {@code card} up (none for null), on the half of
     * {@code place} (the page on screen for null). The frame grows from full size; the in-app
     * keyboard is raised once it has settled, and {@code onSettled} runs then. Already presented,
     * it slides to {@code place}'s half and raises {@code card} (or puts the card down). Either
     * way {@code card} is up when this returns, or a warning in the log says why it is not.
     */
    @Override
    public void present(@Nullable EditorCard card, @Nullable PaneWallPage place,
                        @Nullable Runnable onSettled) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        beginSession();
        if (!mSession)
            return;
        if (mOpen) {
            if (place != null)
                switchHalf(SeamDress.halfOf(place), true);
            if (card != null) raiseOrReport(card);
            else closeCard(true);
            if (onSettled != null)
                onSettled.run();
            return;
        }
        View rootContainer = mHost.findView(R.id.terminal_root_container);
        ViewGroup content = mHost.findView(android.R.id.content);
        if (rootContainer == null || content == null)
            return;
        final int token = ++mPresentToken;
        // A theme preview no carried session owns (its relaunch lost the handover) is kept, as
        // every other write of a session that ended without Discard is.
        if (mCarriedNext == null)
            commitThemePreview();
        mOpen = true;
        mHost.retainGlassCapture(true);
        if (mLookDrag == null)
            SharedPreferencesPreview.clear();
        mEntry = AppearanceSnapshot.capture(prefs);
        mEntrySignature = mEntry.signature();
        mEntryCustom = prefs.getSurfaceCustomPreset();
        mEntrySettings = EditorSettingsSnapshot.capture(settingsStore());
        mSettingsNow = mEntrySettings.signature();
        mEntryMinimal = mHost.isMinimalMode();
        mSessionCustom.clear();
        mHeldCustom.clear();
        mClearedWidgetGlass.clear();
        forgetGlassEdgePositions();
        CarriedSession carried = mCarriedNext;
        mCarriedNext = null;
        if (carried != null) {
            mEntry = carried.entry;
            mEntrySignature = carried.entrySignature;
            mEntryCustom = carried.entryCustom;
            mEntrySettings = carried.settings;
            mEntryMinimal = carried.minimal;
            mSessionCustom.copyFrom(carried.custom);
            refreshSettingsSignature();
        }
        PaneWallPage page = place != null ? place : mHost.pageOnScreen();
        mHalf = page == null ? WallHalf.WORKSPACE : SeamDress.halfOf(page);
        mCard = null;
        mRaisedKeyboard = false;

        if (mFrame == null || mFrame.root() != rootContainer)
            mFrame = new AppearanceEditorFrame(rootContainer);
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null) {
            attachLayoutViews(content, layout);
            layout.setOnChangedListener(this::onArrangementChanged);
            layout.setOnPlaceShownListener(() -> {
                pushLayoutShape(layout);
                restateCard();
                refreshStack();
            });
            layout.setOnTrayOfferListener((offered, hovered) -> {
                HiddenStackView stack = mStack;
                if (stack != null) stack.setZone(offered, hovered);
            });
            layout.setOnSelectionListener(this::onCanvasSelection);
            pushLayoutShape(layout);
            layout.begin(place, carried == null ? null : carried.arrangement);
        }
        attachStack(content);
        if (mBar == null) {
            mBar = new EditorBar(mHost.context());
            mBar.setListener(new BarListener());
        }
        mBar.attach(content);
        mBar.setShown(true);
        mBar.setBarShown(true, false);
        mBar.setHalf(mHalf);
        mBar.setOrientation(layout != null && layout.shownOrientation() == PlaceOrientation.LANDSCAPE);
        if (mSheet == null)
            mSheet = new EditorCardSheet(mHost.context(), mModel, new SheetListener());
        mSheet.attach(content);
        notifyDirty();

        bindTargets();
        setOverlayVisible(true);
        registerLayoutListener(content);
        mFramed = false;
        // The card goes up now, not in the frame pass below: that pass is a post, and a post can
        // wait behind a pending traversal, so a card raised there is not up when this returns.
        // The sheet waits below the screen for its detents and rises with the frame's first pose.
        if (card != null)
            raiseOrReport(card);
        content.post(() -> {
            if (!mOpen || token != mPresentToken)
                return;
            frameSizeChanged();
            mFramed = true;
            if (!layoutFrame(true))
                layoutFrameWhenReady(content, token, FRAME_RETRIES);
            positionTargets();
            long wait = ReducedMotion.isEnabled(mHost.context()) ? 0L
                : AppearanceEditorFrame.ENTER_MS + 20L;
            content.postDelayed(() -> {
                if (!mOpen || token != mPresentToken)
                    return;
                // Raised so it can be tapped, where the place shows it; a keyboard the place
                // keeps down stays down, and its card in the stack is the way to it.
                LayoutEditorController shown = mHost.layoutEditor();
                if (!mHost.isInAppKeyboardShown()
                    && (shown == null || shown.isKeyboardShown()))
                    mRaisedKeyboard = mHost.showInAppKeyboardForEditor();
                if (onSettled != null)
                    onSettled.run();
            }, wait);
        });
    }

    /** The card that is up, for the surface to remember. */
    @Nullable
    @Override
    public EditorCard shownCard() {
        return mOpen ? mCard : null;
    }

    /** The half the frame shows. */
    @NonNull
    @VisibleForTesting
    WallHalf half() {
        return mHalf;
    }

    // ------------------------------------------------------------------------- the canvas

    /** The layout canvas's host: the frame's rect, over the scaled launcher. */
    @Nullable private FrameLayout mLayoutFrame;
    @Nullable private LayoutCanvasView mCanvas;
    @Nullable private LayoutEditorController.Views mLayoutViews;
    /** The frame's rect in the content view, as {@code {left, top, right, bottom}}. */
    @Nullable private int[] mFrameRectInContent;
    private float mFrameCornerPx;
    /** The layout canvas reaches this far past the scaled launcher, so no seam shows. */
    private static final int RING_PX = 1;
    private static final long CANVAS_FADE_MS = 200L;

    /**
     * Lends the move-and-size engine its canvas, in a host of its own over the scaled launcher,
     * under the half line, the bar and the card. Laid out but not drawn until a drag or the other
     * orientation shows it.
     */
    private void attachLayoutViews(@NonNull ViewGroup content,
                                   @NonNull LayoutEditorController layout) {
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
            mCanvas = frame.findViewById(R.id.layout_editor_canvas);
        }
        if (frame.getParent() != content) {
            if (frame.getParent() instanceof ViewGroup)
                ((ViewGroup) frame.getParent()).removeView(frame);
            content.addView(frame, new FrameLayout.LayoutParams(0, 0, Gravity.TOP | Gravity.LEFT));
        }
        frame.animate().cancel();
        frame.setAlpha(1f);
        frame.setVisibility(View.INVISIBLE);
        LayoutCanvasView canvas = mCanvas;
        if (canvas == null)
            return;
        canvas.setLiftOverlayHost(content);
        canvas.setOnModelResizedListener(this::onModelShapeChanged);
        if (mLayoutViews == null)
            mLayoutViews = new LayoutEditorController.Views(frame, canvas);
        layout.attach(mLayoutViews);
        positionLayoutFrame();
    }

    /** Whether the canvas stands over the frame now: the other orientation, or a drag. */
    private boolean mCanvasShown;
    /** The canvas is up for a drag alone, and goes once the finger lifts. */
    private boolean mCanvasForDrag;

    private void showCanvas(boolean shown, boolean animate) {
        FrameLayout frame = mLayoutFrame;
        if (frame == null || shown == mCanvasShown)
            return;
        mCanvasShown = shown;
        frame.animate().cancel();
        boolean instant = !animate || ReducedMotion.isEnabled(mHost.context());
        if (shown) {
            positionLayoutFrame();
            frame.setVisibility(View.VISIBLE);
            if (instant) {
                frame.setAlpha(1f);
                return;
            }
            frame.setAlpha(0f);
            frame.animate().alpha(1f).setDuration(CANVAS_FADE_MS).setInterpolator(Motion.settle())
                .start();
            return;
        }
        applyModelShape(false, animate);
        if (instant) {
            frame.setAlpha(1f);
            frame.setVisibility(View.INVISIBLE);
            return;
        }
        frame.animate().alpha(0f).setDuration(CANVAS_FADE_MS).setInterpolator(Motion.settle())
            .withEndAction(() -> {
                if (mCanvasShown)
                    return;
                frame.setVisibility(View.INVISIBLE);
                frame.setAlpha(1f);
            }).start();
    }

    // ---- the other orientation's phone model ------------------------------------------------------

    private static final long MODEL_MS = 250L;
    private boolean mModelShape;
    private float mModelProgress;
    @Nullable private android.animation.ValueAnimator mModelAnimator;

    /**
     * The canvas draws a landscape phone of its own in a portrait editor: the live launcher under
     * it, which cannot show landscape, fades away beneath it.
     */
    private void applyModelShape(boolean resized, boolean animate) {
        if (resized == mModelShape && mModelAnimator == null && mModelProgress == (resized ? 1f : 0f))
            return;
        mModelShape = resized;
        if (mModelAnimator != null) {
            mModelAnimator.cancel();
            mModelAnimator = null;
        }
        final float to = resized ? 1f : 0f;
        if (!animate || ReducedMotion.isEnabled(mHost.context()) || mLayoutFrame == null || !mOpen) {
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
        LayoutCanvasView canvas = mCanvas;
        if (!mOpen || canvas == null || !mCanvasShown)
            return;
        applyModelShape(canvas.isModelResized(), true);
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
        // Laid out at the pose now; any carry by translation is spent.
        frame.setTranslationX(0f);
        frame.setTranslationY(0f);
        frame.setScaleX(1f);
        frame.setScaleY(1f);
        frame.invalidateOutline();
        if (mCanvas != null)
            mCanvas.setFrameCornerRadiusPx(mFrameCornerPx + RING_PX);
        updateCanvasTray();
    }

    /** A tap on an element of the canvas (the other orientation): its card. */
    private void onCanvasSelection(@Nullable LayoutCanvasView.Block block) {
        if (!mOpen || mCanvasForDrag || block == null)
            return;
        EditorCard card = cardForBlock(block, mHalf);
        if (card != null)
            openCard(card, true);
    }

    /** The card an element of the canvas opens. */
    @Nullable
    static EditorCard cardForBlock(@NonNull LayoutCanvasView.Block block, @NonNull WallHalf half) {
        switch (block) {
            case STATUS_BAR: return EditorCard.STATUS;
            case APPS_ROW:
            case ALPHABETS_ROW:
            case EXTRA_KEYS: return EditorCard.DOCK;
            case KEYBOARD: return EditorCard.KEYBOARD;
            case CANVAS:
            default: return half == WallHalf.HOME ? EditorCard.HOME : EditorCard.TERMINAL;
        }
    }

    // ---------------------------------------------------------------------- the halves and the bar

    /**
     * Slides the frame to {@code half} (the real wall slides inside the scaled container), and a
     * card up stays when its object stands there too; otherwise it goes down.
     */
    private void switchHalf(@NonNull WallHalf half, boolean animate) {
        if (!mOpen || half == mHalf)
            return;
        mHalf = half;
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null)
            layout.showHome(half == WallHalf.HOME, animate);
        if (mBar != null)
            mBar.setHalf(half);
        if (mCard != null && !mCard.standsOn(half))
            closeCard(true);
        else
            restateCard();
        positionTargets();
    }

    /** The orientation chip: the frame shows the other orientation on the canvas, or the phone's. */
    private void flipOrientation() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (!mOpen || layout == null || layout.shownOrientation() == null)
            return;
        PlaceOrientation next = layout.shownOrientation() == PlaceOrientation.LANDSCAPE
            ? PlaceOrientation.PORTRAIT : PlaceOrientation.LANDSCAPE;
        layout.showOrientation(next);
        if (mBar != null)
            mBar.setOrientation(next == PlaceOrientation.LANDSCAPE);
        boolean other = layout.showsOtherOrientation();
        mCanvasForDrag = false;
        showCanvas(other, true);
        if (other && mCanvas != null)
            applyModelShape(mCanvas.isModelResized(), true);
        restateCard();
        refreshStack();
    }

    private final class BarListener implements EditorBar.Listener {
        @Override public void onOrientation() {
            flipOrientation();
        }

        @Override public void onUndo() {
            undo();
        }

        @Override public void onDone() {
            done();
        }

        @Override public void onPalette() {
            openCard(EditorCard.APPEARANCE, true);
        }
    }

    // ------------------------------------------------------------------------------- the cards

    /** Raises the card {@link #present} was asked for, or logs why it cannot stand there. */
    private void raiseOrReport(@NonNull EditorCard card) {
        String refusal = cardRefusal(card);
        if (refusal == null)
            openCard(card, true);
        else
            Logger.logWarn(LOG_TAG, "The " + card + " card was not raised: " + refusal);
    }

    /** Why {@code card} cannot go up now, or null when it can. */
    @Nullable
    private String cardRefusal(@NonNull EditorCard card) {
        if (!mOpen)
            return "the editor is not presented";
        if (mSheet == null)
            return "the editor has no card sheet";
        if (!card.standsOn(mHalf))
            return "it does not stand on the " + mHalf + " half";
        return null;
    }

    /** Raises {@code card}, on the half the frame shows. */
    private void openCard(@NonNull EditorCard card, boolean animate) {
        EditorCardSheet sheet = mSheet;
        if (sheet == null || cardRefusal(card) != null)
            return;
        dismissClockDropdown();
        mCard = card;
        if (card == EditorCard.APPEARANCE)
            ensureIconsContent();
        sheet.show(card, EditorRows.rows(card, offers()), copyLabel(card), resetLabel(card),
            animate);
        if (mBar != null)
            mBar.setBarShown(false, animate);
        layoutSheet();
        applyPose(animate);
        positionOutline();
    }

    /** Puts the card down; the bar comes back. */
    private void closeCard(boolean animate) {
        EditorCardSheet sheet = mSheet;
        if (mCard == null)
            return;
        mCard = null;
        dismissClockDropdown();
        if (sheet != null)
            sheet.hide(animate, null);
        if (mBar != null && mOpen)
            mBar.setBarShown(true, animate);
        applyPose(animate);
        positionOutline();
    }

    @NonNull
    private EditorRows.Offers offers() {
        return new EditorRows.Offers(mHost.isGlassRefractionActive(), PaneRetroEffect.available());
    }

    /** The menu's copy item for a card held per half: the other half's name; null otherwise. */
    @Nullable
    private String copyLabel(@NonNull EditorCard card) {
        if (!card.perHalf())
            return null;
        return getString(mHalf == WallHalf.HOME ? R.string.appearance_copy_to_workspace
            : R.string.appearance_copy_to_home);
    }

    @NonNull
    private String resetLabel(@NonNull EditorCard card) {
        switch (card) {
            case HOME: return getString(R.string.appearance_reset_home);
            case STATUS: return getString(R.string.appearance_reset_status);
            case TERMINAL: return getString(R.string.appearance_reset_terminal);
            case DOCK: return getString(R.string.appearance_reset_dock);
            case KEYBOARD: return getString(R.string.appearance_reset_keyboard);
            case APPEARANCE:
            default: return getString(R.string.appearance_reset_look);
        }
    }

    /** The card's rows read again, the copy label with them (the half may have moved). */
    private void restateCard() {
        EditorCardSheet sheet = mSheet;
        EditorCard card = mCard;
        if (sheet == null || card == null || !sheet.isShown())
            return;
        sheet.show(card, EditorRows.rows(card, offers()), copyLabel(card), resetLabel(card), false);
        sheet.restate();
    }

    private final class SheetListener implements EditorCardSheet.Listener {
        @Override public void onClose() {
            closeCard(true);
        }

        @Override public void onReset() {
            resetCard();
        }

        @Override public void onCopyToOtherHalf() {
            copyCard();
        }

        @Override public void onSheetMoved(int detent, float topPx) {
            onCardMoved(detent, topPx);
        }

        @Override public void onSheetSettled(int detent, float topPx) {
            onCardSettled();
        }
    }

    /** Reset in the card's menu: the object back on the half's Look (Home: back as it shipped). */
    private void resetCard() {
        TermuxAppSharedPreferences prefs = prefs();
        EditorCard card = mCard;
        if (prefs == null || card == null)
            return;
        switch (card) {
            case HOME:
                resetHome();
                break;
            case APPEARANCE:
                for (SurfaceSlot slot : SurfaceSlot.values())
                    prefs.resetSurfaceToLook(slot, mHalf);
                break;
            default:
                if (card.slot != null)
                    prefs.resetSurfaceToLook(card.slot, mHalf);
                break;
        }
        syncAfterBulkWrite();
    }

    /**
     * Home's rows back to what they ship with, and Home's glass back on the Look the Home half
     * wore when the editor opened (its Base as captured on entry).
     */
    private void resetHome() {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs != null && mEntry != null)
            mEntry.restoreHalfBase(prefs, WallHalf.HOME, SurfaceProperty.BLUR,
                SurfaceProperty.GRAIN, SurfaceProperty.OPACITY, SurfaceProperty.TINT);
        Context context = mHost.context();
        for (String key : new String[] {HomeSettings.KEY_ICON_SCALE, HomeSettings.KEY_LABEL_SIZE,
            HomeSettings.KEY_FOLDER_DENSITY,
            HomeSettings.KEY_PAGE_INDICATOR})
            AppearanceSettingsStore.putString(context, key, null);
        AppearanceSettingsStore.putBoolean(context, HomeSettings.KEY_LABEL_TWO_LINES,
            EditorSettingsSnapshot.defaultOf(HomeSettings.KEY_LABEL_TWO_LINES));
        AppearanceSettingsStore.putBoolean(context, HomeSettings.KEY_LABEL_SHADOW,
            EditorSettingsSnapshot.defaultOf(HomeSettings.KEY_LABEL_SHADOW));
        AppearanceSettingsStore.putBoolean(context, "wallpaper_parallax",
            EditorSettingsSnapshot.defaultOf("wallpaper_parallax"));
        AppearanceSettingsStore.putString(context, "app_launcher_builtin_widget_style", null);
        settingsWritten();
        mHost.applyHomeSettings();
    }

    /** Copy in the card's menu: the other half made to look as this one does. */
    private void copyCard() {
        TermuxAppSharedPreferences prefs = prefs();
        EditorCard card = mCard;
        if (prefs == null || card == null || !card.perHalf())
            return;
        if (card == EditorCard.APPEARANCE)
            prefs.copyLookToOtherHalf(mHalf);
        else if (card.slot != null)
            prefs.copySurfaceToOtherHalf(card.slot, mHalf);
        syncAfterBulkWrite();
    }

    // ----------------------------------------------------------------------------- the frame

    private boolean mFrameCtxValid;
    private int mWindowHeightPx;
    private int mWindowWidthPx;
    private int mNavInsetPx;
    private int mFrameTopPx;
    private int mTopInsetPx;
    private int mContainerTopPx;
    private int mContainerLeftPx;
    /** The window every pose is computed from; null until the first measure. */
    @Nullable private EditorFramePose.Window mPoseWindow;
    private float mTargetScale = -1f;
    private float mTargetTx;
    private float mTargetTy;
    @Nullable private android.animation.ValueAnimator mFrameAnimator;

    /**
     * Measures the window round the frame (insets, the display's bands, the font scale), sizes
     * the card's two detents and puts the frame at its pose: at rest, or with the card that is
     * up. False when the window is not laid out yet; the caller tries again.
     */
    private boolean layoutFrame(boolean animate) {
        AppearanceEditorFrame frame = mFrame;
        ViewGroup content = mHost.findView(android.R.id.content);
        if (frame == null || content == null || content.getHeight() <= 0)
            return false;
        View root = frame.root();
        if (root.getHeight() <= 0 || !(root.getParent() instanceof View))
            return false;
        int windowHeight = content.getHeight();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        Insets bars = insets == null ? Insets.NONE
            : insets.getInsets(WindowInsetsCompat.Type.systemBars());
        int[] contentInWindow = new int[2];
        content.getLocationInWindow(contentInWindow);
        int decorHeight = content.getRootView().getHeight();
        mNavInsetPx = Math.max(0, contentInWindow[1] + windowHeight
            - (decorHeight - bars.bottom));
        Insets sides = insets == null ? Insets.NONE : insets.getInsets(
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        int topInset = AppearancePreviewArea.pageInsetTopPx(
            insets == null ? mHost.statusBarInsetTop() : sides.top, contentInWindow[1]);
        int[] parentOffset = new int[2];
        if (!AppearanceEditorFrame.offsetIn((View) root.getParent(), content, parentOffset))
            return false;
        mContainerTopPx = parentOffset[1] + root.getTop();
        mContainerLeftPx = parentOffset[0] + root.getLeft();
        mWindowHeightPx = windowHeight;
        mWindowWidthPx = content.getWidth();
        mTopInsetPx = topInset;
        pushDisplayInsets(content);
        refreshPoseWindow();
        mFrameCtxValid = true;
        layoutSheet();
        // The first pose already knows whether the rail stands (SPEC §3.6a item 3).
        readHidden();
        if (mTargetScale < 0f) {
            // The first pose: the frame grows into place from full size.
            EditorFramePose.Pose pose = targetPose();
            boolean grow = animate && !ReducedMotion.isEnabled(mHost.context());
            frame.show(pose.scale, pose.translationX, pose.translationY, grow);
            if (grow)
                markEntering();
            setTarget(pose.scale, pose.translationX, pose.translationY);
            placeBar();
        } else {
            applyPose(false);
        }
        if (mEditorWallpaper != null)
            showEditorWallpaper(mEditorWallpaper);
        refreshStack();
        return true;
    }

    /** The opening pass found no laid-out window: it tries again on the next frames, a few times. */
    private void layoutFrameWhenReady(@NonNull View content, int token, int attempts) {
        if (!mOpen || token != mPresentToken || mFrameCtxValid || attempts <= 0)
            return;
        content.postOnAnimation(() -> {
            if (!mOpen || token != mPresentToken || mFrameCtxValid)
                return;
            frameSizeChanged();
            if (layoutFrame(true))
                positionTargets();
            else
                layoutFrameWhenReady(content, token, attempts - 1);
        });
    }

    private static final int FRAME_RETRIES = 30;

    /** The window the poses are computed from, read again after a measure. */
    private void refreshPoseWindow() {
        mPoseWindow = new EditorFramePose.Window(mWindowHeightPx, mTopInsetPx, mNavInsetPx,
            density(), getResources().getConfiguration().fontScale, mContainerTopPx,
            visibleHeightPx(), mRevealTopPx, dp(EditorBar.BAR_DP), dp(EditorBar.FOOT_GAP_DP));
        mFrameTopPx = mPoseWindow.topPx();
        EditorBar bar = mBar;
        if (bar != null)
            bar.setHalfRowHeight(mPoseWindow.halfRowPx());
    }

    /** The launcher's container plus the bands of the display the frame shows round it. */
    private int visibleHeightPx() {
        AppearanceEditorFrame frame = mFrame;
        int root = frame == null ? 0 : frame.root().getHeight();
        return root + Math.max(0, mRevealTopPx) + Math.max(0, mDisplayInsetBottomPx);
    }

    /** The card's detents for the window and the card up now. */
    private void layoutSheet() {
        EditorCardSheet sheet = mSheet;
        EditorFramePose.Window window = mPoseWindow;
        if (sheet == null || !mFrameCtxValid || window == null)
            return;
        int full = EditorFramePose.fullSheetPx(window);
        int half = EditorFramePose.halfSheetPx(window, sheet.minHalfHeightPx(mWindowWidthPx), full);
        sheet.setGeometry(half, full, mNavInsetPx);
    }

    /**
     * The pose the frame is heading for: with a card up, over the card at the detent it is
     * settling on; at rest, over the editor bar.
     */
    @NonNull
    private EditorFramePose.Pose targetPose() {
        EditorFramePose.Window window = mPoseWindow;
        EditorCardSheet sheet = mSheet;
        if (window == null)
            return new EditorFramePose.Pose(1f, 0f, 0f);
        float cardTop = mCard != null && sheet != null
            ? window.heightPx - sheet.heightAt(sheet.detent()) : Float.MAX_VALUE;
        return EditorFramePose.railPose(window, cardTop, poseReserve(), mHidden.size(), mLifted);
    }

    /** An element of the frame is in the air: with nothing hidden, the frame stands off the rail. */
    private boolean mLifted;
    /** The slide to and from the rail's offset at a lift with nothing hidden (SPEC §3.6a). */
    private static final long LIFT_SLIDE_MS = 200L;

    /** The lift began or ended: the frame takes its rail pose, sliding across when it moves. */
    private void setLifted(boolean lifted) {
        if (mLifted == lifted)
            return;
        mLifted = lifted;
        applyPose(true, LIFT_SLIDE_MS);
    }

    /** The band on the frame's left that the stack of hidden items keeps clear. */
    @NonNull
    private EditorFramePose.Reserve poseReserve() {
        AppearanceEditorFrame frame = mFrame;
        int width = frame == null ? 0 : frame.root().getWidth();
        return new EditorFramePose.Reserve(
            mContainerLeftPx + HiddenStackPolicy.reservePx(density()), mContainerLeftPx, width,
            mWindowWidthPx, dp(HiddenStackPolicy.SIDE_DP));
    }

    /**
     * Where the frame stands now: at rest, or with the card that is up at its detent. The card
     * moves on the same clock and curve ({@link #POSE_MS}, {@link Motion#settle()}), so the frame
     * and the card's top edge keep their gap all the way.
     */
    private void applyPose(boolean animate) {
        applyPose(animate, POSE_MS);
    }

    private void applyPose(boolean animate, long durationMs) {
        // Before the first pose (a card raised while the window was being measured) the frame
        // pass takes the pose itself: it grows the frame in, clipped, straight to the card.
        if (mFrame == null || !mFrameCtxValid || !mOpen || mTargetScale < 0f)
            return;
        EditorFramePose.Pose pose = targetPose();
        moveFrame(pose.scale, pose.translationX, pose.translationY, animate, durationMs);
        placeBar();
    }

    /** Whether the frame stands where its last pose put it (or is on its way there). */
    private boolean poseHolds() {
        AppearanceEditorFrame frame = mFrame;
        if (frame == null || mTargetScale < 0f)
            return true;
        if (mFrameAnimator != null || mEntering || mFollowingSheet)
            return true;
        View root = frame.root();
        return root.getScaleX() == mTargetScale && root.getTranslationX() == mTargetTx
            && root.getTranslationY() == mTargetTy;
    }

    /** The frame's grow-in animation is running; a refit leaves it to land. */
    private boolean mEntering;

    private void markEntering() {
        mEntering = true;
        View root = mFrame == null ? null : mFrame.root();
        if (root != null)
            root.postDelayed(() -> mEntering = false, AppearanceEditorFrame.ENTER_MS + 20L);
    }

    private void setTarget(float scale, float tx, float ty) {
        mTargetScale = scale;
        mTargetTx = tx;
        mTargetTy = ty;
        setFrameRect(scale, tx, ty);
        positionLayoutFrame();
        restyleOutlines();
    }

    /** Moves the frame to its new pose on the card's clock; the canvas and outlines follow at the end. */
    private void moveFrame(float scale, float tx, float ty, boolean animate, long durationMs) {
        AppearanceEditorFrame frame = mFrame;
        if (frame == null)
            return;
        View root = frame.root();
        boolean onItsWay = mFrameAnimator != null || mEntering;
        if (scale == mTargetScale && tx == mTargetTx && ty == mTargetTy && !mFollowingSheet
            && (onItsWay || (root.getScaleX() == scale && root.getTranslationX() == tx
                && root.getTranslationY() == ty)))
            return;
        mFollowingSheet = false;
        cancelFrameAnimator();
        mEntering = false;
        if (!animate || ReducedMotion.isEnabled(mHost.context()) || !mFramed) {
            root.animate().cancel();
            frame.show(scale, tx, ty, false);
            setTarget(scale, tx, ty);
            return;
        }
        root.animate().cancel();
        final float fromScale = root.getScaleX();
        final float fromTx = root.getTranslationX();
        final float fromTy = root.getTranslationY();
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(durationMs);
        animator.setInterpolator(Motion.settle());
        // While a drag's pictures stand on the canvas (the slide to the rail's pose and back),
        // the canvas is carried over the frame with it, so its slots never leave the frame.
        FrameLayout carried = mLayoutFrame;
        final boolean carryCanvas = mLive != null || mLifted
            || (carried != null && !carried.getMatrix().isIdentity());
        animator.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            float nowScale = fromScale + (scale - fromScale) * f;
            float nowTx = fromTx + (tx - fromTx) * f;
            float nowTy = fromTy + (ty - fromTy) * f;
            frame.setPose(nowScale, nowTx, nowTy);
            if (carryCanvas)
                carryLayoutFrame(nowScale, nowTx, nowTy);
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
                frame.setPose(scale, tx, ty);
                // The drag's pictures stand in the canvas's px: it stays carried, and is laid
                // out at the new pose once they are gone (finishLive).
                if (mLive != null)
                    return;
                setTarget(scale, tx, ty);
            }
        });
        mFrameAnimator = animator;
        mTargetScale = scale;
        mTargetTx = tx;
        mTargetTy = ty;
        animator.start();
    }

    private void cancelFrameAnimator() {
        android.animation.ValueAnimator animator = mFrameAnimator;
        mFrameAnimator = null;
        if (animator != null)
            animator.cancel();
    }

    /** The pose {@link #mFrameRectInContent} was worked out for. */
    private float mFrameRectScale = 1f;
    private float mFrameRectTx;
    private float mFrameRectTy;
    /** The container's origin in the canvas's px at that pose: with the scale, the live map. */
    private float mRootInCanvasX;
    private float mRootInCanvasY;

    /**
     * The canvas's host moved with the frame while the frame slides: its laid-out rect is the
     * pose {@link #setFrameRect} last took, and the rest is a scale about the frame's own pivot
     * (top centre, {@link AppearanceEditorFrame#setPose}) and a translation.
     */
    private void carryLayoutFrame(float scale, float tx, float ty) {
        FrameLayout host = mLayoutFrame;
        AppearanceEditorFrame frame = mFrame;
        if (host == null || frame == null || mFrameRectScale <= 0f)
            return;
        float pivotX = mContainerLeftPx + frame.root().getWidth() / 2f + mFrameRectTx;
        float pivotY = mContainerTopPx + mFrameRectTy;
        float k = scale / mFrameRectScale;
        host.setPivotX(pivotX - host.getLeft());
        host.setPivotY(pivotY - host.getTop());
        host.setScaleX(k);
        host.setScaleY(k);
        host.setTranslationX(tx - mFrameRectTx);
        host.setTranslationY(ty - mFrameRectTy);
        updateCanvasTray();
    }

    /** The scaled container's rect in the content view: the canvas's host. */
    private void setFrameRect(float scale, float translationX, float translationY) {
        AppearanceEditorFrame frame = mFrame;
        if (frame == null)
            return;
        View root = frame.root();
        float scaledLeft = mContainerLeftPx + translationX + root.getWidth() * (1f - scale) / 2f;
        float top = mContainerTopPx + translationY;
        mFrameRectInContent = new int[] {(int) Math.floor(scaledLeft), (int) Math.floor(top),
            (int) Math.ceil(scaledLeft + root.getWidth() * scale),
            (int) Math.ceil(top + root.getHeight() * scale)};
        mFrameRectScale = scale;
        mFrameRectTx = translationX;
        mFrameRectTy = translationY;
        // Where the container's own (0, 0) stands in the canvas, at this pose.
        mRootInCanvasX = scaledLeft - (mFrameRectInContent[0] - RING_PX);
        mRootInCanvasY = top - (mFrameRectInContent[1] - RING_PX);
        mFrameCornerPx = AppearanceEditorFrame.visibleCornerRadiusPx(
            AppearanceEditorFrame.deviceCornerRadiusPx(root), mDisplayInsetLeftPx,
            mRevealTopPx) * scale;
    }

    /**
     * The bar over the navigation inset, and the half line: over the bar at rest, over the card
     * while one is up, the frame above it either way (the poses leave it the room). By
     * translation, every frame of the card's motion included.
     */
    private void placeBar() {
        EditorBar bar = mBar;
        EditorCardSheet sheet = mSheet;
        if (bar == null || !mFrameCtxValid)
            return;
        int gap = dp(EditorFramePose.GAP_DP);
        float barLift = mNavInsetPx + dp(EditorBar.FOOT_GAP_DP);
        float halfLift = mCard != null && sheet != null && sheet.isShown()
            ? mWindowHeightPx - sheet.sheetTopPx() + gap
            : barLift + dp(EditorBar.BAR_DP) + gap;
        bar.place(barLift, halfLift);
        placeStack();
    }

    // ------------------------------------------------------------------ the hidden items' stack

    @Nullable private HiddenStackView mStack;
    /** What the edited layout puts away now, in the stack's order. */
    private final List<HiddenItem> mHidden = new ArrayList<>();
    /** What the stack was last laid out for, so a pass that changes nothing touches nothing. */
    @Nullable private String mStackSignature;
    private int mLabelForHeightPx = -1;
    private float mLabelForFontScale = -1f;
    private float mLabelPx;
    /** The rail's border in the content view, {left, top, right, bottom}: the drop target. */
    @Nullable private int[] mStackColumn;

    private void attachStack(@NonNull ViewGroup content) {
        HiddenStackView stack = mStack;
        if (stack == null) {
            stack = new HiddenStackView(mHost.context());
            stack.setListener(new HiddenStackView.Listener() {
                @Override public void onShow(@NonNull HiddenItem item) {
                    showHidden(item, true);
                }

                @Override public void onDropped(@NonNull HiddenItem item, float rawX, float rawY) {
                    dropChip(item, rawX, rawY);
                }

                @Override public void onDragStarted(@NonNull HiddenItem item) {
                    beginChipDrag(item);
                }

                @Override public boolean onDragMoved(@NonNull HiddenItem item, float rawX,
                                                     float rawY) {
                    return moveChipDrag(rawX, rawY);
                }

                @Override public void onDragCancelled(@NonNull HiddenItem item) {
                    cancelChip();
                }
            });
            mStack = stack;
        }
        if (stack.getParent() != content) {
            if (stack.getParent() instanceof ViewGroup)
                ((ViewGroup) stack.getParent()).removeView(stack);
            content.addView(stack, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void detachStack() {
        HiddenStackView stack = mStack;
        if (stack != null && stack.getParent() instanceof ViewGroup)
            ((ViewGroup) stack.getParent()).removeView(stack);
        mHidden.clear();
        mStackSignature = null;
        mStackColumn = null;
    }

    /** The items the edited layout puts away, read again; the stack and the frame follow. */
    private void refreshStack() {
        boolean wasEmpty = mHidden.isEmpty();
        readHidden();
        placeStack();
        // The rail comes or goes with the first hidden item or the last: the frame's offset
        // follows, but never under live snapshots, which wait for it (see finishLive).
        if (wasEmpty != mHidden.isEmpty() && mLive == null)
            applyPose(true);
    }

    /** What the edited layout puts away, read into {@link #mHidden}. */
    private void readHidden() {
        LayoutEditorController layout = mHost.layoutEditor();
        mHidden.clear();
        if (mOpen && layout != null) {
            for (LayoutEditorPlan.TrayItem tray : layout.trayItems()) {
                HiddenItem item = HiddenItem.of(tray);
                if (item != null) mHidden.add(item);
            }
            if (layout.offers(Element.APPS)) {
                if (!layout.searchField()) mHidden.add(HiddenItem.SEARCH_FIELD);
            }
        }
    }

    /** Lays the cards out for the window and the card up now. */
    private void placeStack() {
        HiddenStackView stack = mStack;
        EditorFramePose.Window window = mPoseWindow;
        EditorCardSheet sheet = mSheet;
        if (stack == null || window == null || !mFrameCtxValid || !mOpen)
            return;
        float density = density();
        int top = window.topPx();
        float cardTop = mCard != null && sheet != null
            ? window.heightPx - sheet.heightAt(sheet.detent()) : Float.MAX_VALUE;
        int available = Math.max(0,
            Math.round(EditorFramePose.stackBottomPx(window, cardTop)) - top);
        // Room for one more card than there are: the empty slot a lifted element drops into.
        int cardHeight = HiddenStackPolicy.railCardHeightPx(mHidden.size(), available, density);
        float fontScale = getResources().getConfiguration().fontScale;
        if (cardHeight != mLabelForHeightPx || fontScale != mLabelForFontScale) {
            mLabelPx = HiddenStackView.fittedLabelPx(mHost.context(), cardHeight);
            mLabelForHeightPx = cardHeight;
            mLabelForFontScale = fontScale;
        }
        int left = mContainerLeftPx + dp(HiddenStackPolicy.SIDE_DP);
        HiddenStackPolicy.Rail rail = HiddenStackPolicy.rail(mHidden.size(), left, top, available,
            cardHeight, density);
        // The drop target is the border the finger sees, no more.
        mStackColumn = rail.border.clone();
        String signature = mHidden + "/" + left + "/" + top + "/" + cardHeight + "/" + mLabelPx
            + "/" + available;
        if (!signature.equals(mStackSignature)) {
            mStackSignature = signature;
            stack.set(mHidden, rail, mLabelPx);
        }
        updateCanvasTray();
    }

    /** The canvas is told where the stack is (in its own coordinates), the place to drop a lifted element. */
    private void updateCanvasTray() {
        LayoutCanvasView canvas = mCanvas;
        int[] column = mStackColumn;
        int[] frame = mFrameRectInContent;
        if (canvas == null || column == null || frame == null)
            return;
        float dx = frame[0] - RING_PX;
        float dy = frame[1] - RING_PX;
        RectF tray = new RectF(column[0] - dx, column[1] - dy, column[2] - dx, column[3] - dy);
        // Undo the host's carry (scale about its pivot, then translation), if any.
        FrameLayout host = mLayoutFrame;
        if (host != null) {
            android.graphics.Matrix inverse = new android.graphics.Matrix();
            if (host.getMatrix().invert(inverse))
                inverse.mapRect(tray);
        }
        canvas.setExternalTrayRect(tray);
    }

    /** Whether a raw screen point is over the frame. */
    private boolean isOverFrame(float rawX, float rawY) {
        int[] rect = mFrameRectInContent;
        View content = mHost.findView(android.R.id.content);
        if (rect == null || content == null)
            return false;
        int[] at = new int[2];
        content.getLocationOnScreen(at);
        float x = rawX - at[0];
        float y = rawY - at[1];
        return x >= rect[0] && x <= rect[2] && y >= rect[1] && y <= rect[3];
    }

    /**
     * Shows a hidden item again where it was (a tap on its card, or the card let go over the
     * frame), and after a tap raises the card the item belongs to.
     */
    private void showHidden(@NonNull HiddenItem item, boolean openItsCard) {
        LayoutEditorController layout = mHost.layoutEditor();
        if (!mOpen || layout == null)
            return;
        switch (item) {
            case KEYBOARD:
                layout.setKeyboardShown(true);
                if (!mHost.isInAppKeyboardShown())
                    mRaisedKeyboard = mHost.showInAppKeyboardForEditor() || mRaisedKeyboard;
                break;
            case SEARCH_FIELD:
                layout.setSearchField(true);
                break;
            default:
                if (item.tray != null && item.tray.element != null)
                    layout.setShown(item.tray.element, true);
                break;
        }
        refreshStack();
        positionTargets();
        if (openItsCard && item.card.standsOn(mHalf))
            openCard(item.card, true);
    }

    /** The card stands at a new top edge, or settled on a detent. */
    private int mLastDetent = -1;
    /** The frame follows a card the finger is dragging, pose by pose, with no animator. */
    private boolean mFollowingSheet;

    private void onCardMoved(int detent, float topPx) {
        placeBar();
        EditorCardSheet sheet = mSheet;
        if (sheet != null && sheet.isDragging()) {
            followSheet(topPx);
            return;
        }
        if (mFollowingSheet) {
            // The finger let go: the card settles, and the frame goes with it from where it is.
            mLastDetent = -1;
        }
        if (detent != mLastDetent) {
            mLastDetent = detent;
            applyPose(true);
        }
    }

    /** The card came to rest: the frame's pose, the canvas and the outlines are made final. */
    private void onCardSettled() {
        mLastDetent = -1;
        if (mFrameAnimator == null)
            applyPose(false);
    }

    /** Under a dragged card the frame takes the pose for the card's top edge at once. */
    private void followSheet(float cardTopPx) {
        AppearanceEditorFrame frame = mFrame;
        EditorFramePose.Window window = mPoseWindow;
        if (frame == null || window == null || !mFrameCtxValid || !mOpen)
            return;
        cancelFrameAnimator();
        mEntering = false;
        frame.root().animate().cancel();
        mFollowingSheet = true;
        EditorFramePose.Pose pose = EditorFramePose.railPose(window, cardTopPx, poseReserve(),
            mHidden.size(), mLifted);
        frame.setPose(pose.scale, pose.translationX, pose.translationY);
    }

    // ---- the display round the frame -----------------------------------------------------------

    private int mDisplayInsetLeftPx;
    private int mDisplayInsetTopPx;
    private int mDisplayInsetRightPx;
    private int mDisplayInsetBottomPx;
    /** How much of the band above the container the frame shows. */
    private int mRevealTopPx;
    private final int[] mTmpOffset = new int[2];

    /**
     * Tells the frame where its clip stands round the container, from the window's own geometry,
     * and how much of the status-bar band above it to show.
     */
    private void pushDisplayInsets(@NonNull ViewGroup content) {
        AppearanceEditorFrame frame = mFrame;
        if (frame == null)
            return;
        View root = frame.root();
        int[] parentOffset = new int[2];
        if (root.getWidth() <= 0 || !(root.getParent() instanceof View)
            || !AppearanceEditorFrame.offsetIn((View) root.getParent(), content, parentOffset)) {
            mDisplayInsetLeftPx = 0;
            mDisplayInsetRightPx = 0;
            mDisplayInsetBottomPx = 0;
            mDisplayInsetTopPx = 0;
            mRevealTopPx = 0;
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
        mDisplayInsetRightPx = Math.max(0, decor.getWidth() - (left + root.getWidth()));
        mDisplayInsetBottomPx = Math.max(0, decor.getHeight() - (top + root.getHeight()));
        mRevealTopPx = measureTopReveal(root);
        frame.setDisplayInsets(mDisplayInsetLeftPx, mRevealTopPx, mDisplayInsetRightPx,
            mDisplayInsetBottomPx);
    }

    private int measureTopReveal(@NonNull View root) {
        int[] element = null;
        float elementRadius = 0f;
        View status = mHost.findView(R.id.terminal_window_bar_host);
        if (status != null && status.getVisibility() == View.VISIBLE && status.getWidth() > 0
            && AppearanceEditorFrame.offsetIn(status, root, mTmpOffset)) {
            element = new int[] {mTmpOffset[0], mTmpOffset[1], mTmpOffset[0] + status.getWidth()};
            elementRadius = outlineRadiusPx(EditorCard.STATUS);
        }
        int[] terminal = mHost.terminalFrameRectInRoot();
        if (terminal != null && terminal[2] > terminal[0]
            && (element == null || terminal[1] < element[1])) {
            element = new int[] {terminal[0], terminal[1], terminal[2]};
            elementRadius = mHost.terminalFrameCornerRadiusPx();
        }
        return AppearancePreviewArea.topRevealPx(root.getWidth(), mDisplayInsetLeftPx,
            mDisplayInsetTopPx, mDisplayInsetRightPx,
            AppearanceEditorFrame.deviceCornerRadiusPx(root), element, elementRadius);
    }

    /**
     * The status bar or the terminal moved under the frame: the band above is measured again
     * and, when it changed, the frame takes its new pose. Never mid-drag.
     */
    private void refitTopReveal() {
        AppearanceEditorFrame frame = mFrame;
        ViewGroup content = mHost.findView(android.R.id.content);
        if (frame == null || content == null || !mFrameCtxValid || mSliderDragActive)
            return;
        if (measureTopReveal(frame.root()) == mRevealTopPx)
            return;
        pushDisplayInsets(content);
        refreshPoseWindow();
        layoutSheet();
        applyPose(true);
    }

    // ------------------------------------------------------------------ the editor's wallpaper

    private int mWallpaperToken;
    @Nullable private Drawable mEditorWallpaper;

    /** Passthrough mode: the picture is read off the main thread and painted inside the container. */
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

    // -------------------------------------------------------------- relaunch and the return state

    @Nullable
    @Override
    public AppearanceSurfaceController.EditorSession carrySession() {
        AppearanceSnapshot entry = mEntry;
        String signature = mEntrySignature;
        EditorSettingsSnapshot settings = mEntrySettings;
        if (!mOpen || entry == null || signature == null || settings == null)
            return mCarriedNext;
        LayoutEditorController layout = mHost.layoutEditor();
        return new CarriedSession(entry, signature, mEntryCustom, settings, mEntryMinimal,
            mSessionCustom.copy(), layout == null ? null : layout.entryArrangement());
    }

    @Override
    public void adoptSessionNext(@Nullable AppearanceSurfaceController.EditorSession session) {
        mCarriedNext = session instanceof CarriedSession ? (CarriedSession) session : null;
    }

    /** {@link #carrySession}'s content: immutable, and read only by {@link #present}. */
    static final class CarriedSession implements AppearanceSurfaceController.EditorSession {
        @NonNull final AppearanceSnapshot entry;
        @NonNull final String entrySignature;
        @Nullable final String entryCustom;
        @NonNull final EditorSettingsSnapshot settings;
        final boolean minimal;
        @NonNull final SessionCustom custom;
        @Nullable final List<com.termux.app.place.PlaceArrangeSnapshot> arrangement;

        CarriedSession(@NonNull AppearanceSnapshot entry, @NonNull String entrySignature,
                       @Nullable String entryCustom, @NonNull EditorSettingsSnapshot settings,
                       boolean minimal, @NonNull SessionCustom custom,
                       @Nullable List<com.termux.app.place.PlaceArrangeSnapshot> arrangement) {
            this.entry = entry;
            this.entrySignature = entrySignature;
            this.entryCustom = entryCustom;
            this.settings = settings;
            this.minimal = minimal;
            this.custom = custom;
            this.arrangement = arrangement;
        }
    }

    // ---------------------------------------------------------------------------- the dirty state

    @Nullable private AppearanceSurfaceController.PageListener mPageListener;

    @Override
    public void setPageListener(@Nullable AppearanceSurfaceController.PageListener listener) {
        mPageListener = listener;
    }

    private void syncDirty() {
        if (mSliderDragActive) {
            mDirtyDeferred = true;
            return;
        }
        notifyDirty();
    }

    private void notifyDirty() {
        EditorBar bar = mBar;
        if (bar != null)
            bar.setDirty(mOpen && isDirty());
    }

    /** The arrangement moved (a drop, a switch, a height): the dirty state and the card. */
    private void onArrangementChanged() {
        syncDirty();
        refreshStack();
        if (!mSliderDragActive && mSheet != null && mSheet.isShown())
            mSheet.restate();
    }

    /** Where the app-wide choices are read and written: Settings' own store. */
    @NonNull
    private EditorSettingsSnapshot.Store settingsStore() {
        return EditorSettingsSnapshot.settings(mHost.context());
    }

    /** A card wrote an app-wide choice: its signature is read again for the dirty state. */
    private void settingsWritten() {
        refreshSettingsSignature();
        syncDirty();
    }

    private void refreshSettingsSignature() {
        if (mEntrySettings != null)
            mSettingsNow = EditorSettingsSnapshot.signatureOf(settingsStore());
    }

    // ------------------------------------------------------------------------------- the theme
    //
    // The theme is the one choice a window cannot preview in place: its colours are fixed when
    // it is built. A pick is shown by relaunching into it unstored (ThemePreview); the relaunch
    // hands this session to the next activity's editor, and the stored theme changes on Done.

    @Nullable
    private String storedTheme() {
        return settingsStore().getString("theme_mode");
    }

    /** The Theme row picked {@code mode}: the window shows it, stored only on Done. */
    private void previewTheme(@NonNull String mode) {
        String stored = storedTheme();
        String shown = ThemePreview.effective(stored);
        ThemePreview.set(mode, stored);
        syncDirty();
        if (!mode.equals(shown))
            mHost.previewTheme(mode);
    }

    /** Undo and Discard: the window goes back to the stored theme. */
    private void dropThemePreview() {
        if (ThemePreview.pending() == null)
            return;
        String stored = storedTheme();
        ThemePreview.set(null, stored);
        mHost.previewTheme(ThemePreview.orSystem(stored));
    }

    /** Done and Save, or a session ending without Discard: the shown theme is stored. */
    private void commitThemePreview() {
        String pending = ThemePreview.pending();
        if (pending == null)
            return;
        ThemePreview.set(null, null);
        mHost.commitTheme(pending);
        refreshSettingsSignature();
    }

    /** Saves what the session changed: the look, and the theme it previewed. */
    private void commitSession() {
        commitLook();
        commitThemePreview();
    }

    @Override
    public void undo() {
        if (mOpen)
            revertToEntry();
    }

    @Override
    public void done() {
        if (mOpen)
            commitAndExit();
    }

    /** Back inside the editor: a lift cancelled, a popup shut, a card put down. */
    @Override
    public boolean consumeBack() {
        if (!mOpen)
            return false;
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null && layout.cancelGesture())
            return true;
        if (mClockDropdown != null) {
            dismissClockDropdown();
            return true;
        }
        if (mCard != null) {
            closeCard(true);
            return true;
        }
        return false;
    }

    /** The Keyboard theme page in Settings, over the editor; Back returns to the same session. */
    private void openKeyboardTheme() {
        openSettingsPage(KeyboardColorSchemeFragment.class, R.string.appearance_editor_keyboard_theme);
    }

    /** A Settings page over the editor; the session stays open under it. */
    private void openSettingsPage(@NonNull Class<? extends androidx.fragment.app.Fragment> page,
                                  @StringRes int title) {
        Context context = mHost.context();
        try {
            context.startActivity(SettingsActivity.createFragmentIntent(context, page, title));
        } catch (RuntimeException e) {
            // No Settings to open (a stripped build): the editor stays as it is.
        }
    }

    // ------------------------------------------------------------------------- the card's model

    /** The half a row of the card up writes, as {@link EditorRows#halfFor} says; null for both. */
    @Nullable
    private WallHalf writeHalf(@NonNull Row row) {
        EditorCard card = mCard;
        return card == null ? null : EditorRows.halfFor(card, row, mHalf);
    }

    /** The half a row of the card up reads: its write half, or the Workspace's for a shared one. */
    @NonNull
    private WallHalf readHalf(@NonNull Row row) {
        WallHalf half = writeHalf(row);
        return half == null ? WallHalf.WORKSPACE : half;
    }

    /** The Dock designs' names and glyphs, in {@link DockStyle#CHOICES} order. */
    private static final int[] DESIGN_LABELS = {R.string.layout_editor_dock_style_glass_tray,
        R.string.layout_editor_dock_style_lens_beads, R.string.layout_editor_dock_style_wall_strip,
        R.string.layout_editor_dock_style_peek_glass};
    private static final int[] DESIGN_ICONS = {R.drawable.ic_symbol_dock_glass_tray,
        R.drawable.ic_symbol_dock_lens_beads, R.drawable.ic_symbol_dock_wall_strip,
        R.drawable.ic_symbol_dock_peek_glass};

    /** The keyboard's forms, as the Type segments name them. */
    private static final PlaceLayout.KeyboardForm[] FORMS = {PlaceLayout.KeyboardForm.DOCKED,
        PlaceLayout.KeyboardForm.FLOATING, PlaceLayout.KeyboardForm.SPLIT};

    /** Home's stored list values, in segment order. */
    private static final String[] LABEL_SIZES = {"small", "default", "large"};
    private static final String[] FOLDER_DENSITIES = {"compact", "default", "comfortable"};
    private static final String[] NUMERALS = {"sans", "mono"};
    private static final String[] THEMES = {TermuxPropertyConstants.IVALUE_NIGHT_MODE_SYSTEM,
        TermuxPropertyConstants.IVALUE_NIGHT_MODE_FALSE,
        TermuxPropertyConstants.IVALUE_NIGHT_MODE_TRUE};

    /** The element a surface's Visible and Position move; null for the keyboard and the rest. */
    @Nullable
    private static Element elementOf(@Nullable EditorCard card) {
        if (card == EditorCard.STATUS) return Element.STATUS;
        if (card == EditorCard.DOCK) return Element.APPS;
        return null;
    }

    /** Index of {@code value} in {@code values}, or {@code fallback}. */
    private static int indexOf(@NonNull String[] values, @Nullable String value, int fallback) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value))
                return i;
        }
        return fallback;
    }

    @NonNull
    private String[] strings(@NonNull int... res) {
        String[] out = new String[res.length];
        for (int i = 0; i < res.length; i++)
            out[i] = getString(res[i]);
        return out;
    }

    /** Every card's values and writes, row by row. */
    private final class CardModel implements EditorCardSheet.Model {

        @Override public int value(@NonNull Row row) {
            TermuxAppSharedPreferences prefs = prefs();
            LayoutEditorController layout = mHost.layoutEditor();
            EditorCard card = mCard;
            Context context = mHost.context();
            if (prefs == null || card == null)
                return 0;
            switch (row) {
                case FROST: return frostValue(prefs, card);
                case OPACITY:
                case TINT: {
                    SurfaceProperty property = row.property();
                    if (property == null)
                        return 0;
                    // The Appearance card's are the shared glass of the half the frame shows.
                    int stored = card.slot == null
                        ? prefs.getSurfaceBaseValue(property, readHalf(row))
                        : prefs.getSurfaceValue(card.slot, property, readHalf(row));
                    return row == Row.OPACITY ? AppearanceLooks.opacityPercent(stored)
                        : AppearanceLooks.tintPercent(stored);
                }
                case HEIGHT:
                    if (card == EditorCard.KEYBOARD)
                        return layout == null ? 100 : Math.round(layout.keyboardHeightScale() * 100f);
                    return AppearanceLooks.iconSizeValueFor(mHost.isFloatingDock(),
                        mHost.isAppsRowAlone(), dockHeightScale(prefs), density());
                case VISIBLE: {
                    if (layout == null) return 1;
                    Element element = elementOf(card);
                    // The keyboard reads what the frame draws (the canvas, for the other
                    // orientation): the one answer the switch and the preview can share.
                    if (element == null)
                        return (layout.showsOtherOrientation() ? layout.isKeyboardShown()
                            : mHost.isInAppKeyboardShown()) ? 1 : 0;
                    return layout.isShown(element) ? 1 : 0;
                }
                case STYLE:
                    return prefs.getLayoutStyle() == TermuxAppSharedPreferences.LayoutStyle.FLOATING
                        ? 1 : 0;
                case DESIGN:
                    return layout == null ? 0 : Math.max(0, DockStyle.CHOICES.indexOf(layout.dockStyle()));
                case CORNERS: return AppearanceLooks.cornersDp(prefs.getLayoutCornersDp(false));
                case MARGIN: return AppearanceLooks.marginDp(prefs.getLayoutMarginDp(false));
                case DEPTH: return glassEdgeOf(prefs).depth(mDepthSet);
                case SEARCH_FIELD: return layout != null && layout.searchField() ? 1 : 0;
                case DRAWER_ICON: return layout != null && layout.drawerTile() ? 1 : 0;
                case APP_ICONS: return AppearanceLooks.appIconsValueFor(prefs.getAppLauncherButtonCount());
                case TEXT_SIZE:
                    return Control.TEXT_SIZE.clamp(Math.round(prefs.getTextSizePercent()
                        / (float) Control.TEXT_SIZE.step) * Control.TEXT_SIZE.step);
                case CONTRAST: return AppearanceLooks.legibilityIndex(prefs.getTerminalContrastLevel());
                case EDGE_COLOURS:
                    return AppearanceSettingsStore.getBoolean(context,
                        AppearanceSettingsStore.KEY_PADDING_FILL, false) ? 1 : 0;
                case SPLIT_GAP: return Control.SPLIT_GAP.clamp(prefs.getTerminalPaneGap());
                case ICON_SIZE:
                    return parseInt(AppearanceSettingsStore.getString(context,
                        HomeSettings.KEY_ICON_SCALE, null), 100);
                case LABEL_SIZE:
                    return indexOf(LABEL_SIZES, AppearanceSettingsStore.getString(context,
                        HomeSettings.KEY_LABEL_SIZE, null), 1);
                case GLASS_WIDGETS:
                    return AppearanceSettingsStore.getBoolean(context, "widgets_on_glass", false)
                        ? 1 : 0;
                case TWO_LINE:
                    return AppearanceSettingsStore.getBoolean(context,
                        HomeSettings.KEY_LABEL_TWO_LINES, false) ? 1 : 0;
                case LABEL_SHADOW:
                    return AppearanceSettingsStore.getBoolean(context,
                        HomeSettings.KEY_LABEL_SHADOW, true) ? 1 : 0;
                case FOLDERS:
                    return indexOf(FOLDER_DENSITIES, AppearanceSettingsStore.getString(context,
                        HomeSettings.KEY_FOLDER_DENSITY, null), 1);
                case NUMERALS:
                    return indexOf(NUMERALS, AppearanceSettingsStore.getString(context,
                        "app_launcher_builtin_widget_style", null), 0);
                case PAGE_DOTS:
                    return "off".equals(AppearanceSettingsStore.getString(context,
                        HomeSettings.KEY_PAGE_INDICATOR, null)) ? 0 : 1;
                case WALLPAPER_SCROLL:
                    return AppearanceSettingsStore.getBoolean(context, "wallpaper_parallax", true)
                        ? 1 : 0;
                case OWN_LAYOUT: return layout != null && layout.hasHomeLayout() ? 1 : 0;
                case CLOCK_12H: return prefs.isTopPaneClockAmPmEnabled() ? 1 : 0;
                case KEY_OPACITY: return keyOpacityValue(prefs);
                case KEY_CORNERS:
                    return AppearanceLooks.keyCornersValueFor(prefs.getInAppKeyboardKeyCornerRadiusDp());
                case KEY_SPACING:
                    return AppearanceLooks.keySpacingValueFor(prefs.getInAppKeyboardKeyMarginScale());
                case FORM: {
                    if (layout == null) return 0;
                    for (int i = 0; i < FORMS.length; i++)
                        if (FORMS[i] == layout.keyboardForm()) return i;
                    return 0;
                }
                case LOOK: return lookStop(mHalf);
                case WALLPAPER:
                    return AppearanceSettingsStore.getBoolean(context, "use_system_wallpaper", false)
                        ? 1 : 0;
                case THEME:
                    return indexOf(THEMES, ThemePreview.effective(storedTheme()), 0);
                case MONOCHROME:
                    return AppearanceSettingsStore.getBoolean(context, "app_launcher_bw_icons", false)
                        ? 1 : 0;
                case WALLPAPER_COLOURS:
                    return AppearanceSettingsStore.getBoolean(context,
                        "terminal_dynamic_colors_enabled", false) ? 1 : 0;
                case FULL_SCREEN:
                    return AppearanceSettingsStore.getBoolean(context,
                        AppearanceSettingsStore.KEY_FULLSCREEN, false) ? 1 : 0;
                case MINIMAL: return mHost.isMinimalMode() ? 1 : 0;
                case ALIGNMENT: return AppearanceSettingsStore.getInt(context, "wallpaper_render_zoom", 100);
                case VIBRANCY:
                    return Control.VIBRANCY.clamp(mHeldVibrancy != NO_HELD_VIBRANCY
                        ? mHeldVibrancy : prefs.getSurfaceGlassVibrancy());
                case GLASS_EDGE: return Control.EDGE.clamp(prefs.getFancierGlassDispersionPercent());
                default: return 0;
            }
        }

        @Override public int from(@NonNull Row row) {
            switch (row) {
                case HEIGHT:
                    if (mCard == EditorCard.KEYBOARD)
                        return Math.round(TermuxPreferenceConstants.TERMUX_APP
                            .MIN_IN_APP_KEYBOARD_HEIGHT_SCALE * 100f);
                    return AppearanceLooks.iconSizeRangeDp(mHost.isFloatingDock(),
                        mHost.isAppsRowAlone(), density())[0];
                case DEPTH: return GlassEdgeControls.MIN;
                case APP_ICONS: return AppearanceLooks.APP_ICONS_MIN;
                case TEXT_SIZE: return Control.TEXT_SIZE.min;
                case ICON_SIZE: return HOME_ICON_MIN;
                case KEY_OPACITY: return Control.KEY_OPACITY.min;
                case LOOK: return 0;
                case ALIGNMENT: return TermuxPreferenceConstants.TERMUX_APP.MIN_WALLPAPER_RENDER_ZOOM;
                case VIBRANCY: return Control.VIBRANCY.min;
                case GLASS_EDGE: return Control.EDGE.min;
                default: return 0;
            }
        }

        @Override public int to(@NonNull Row row) {
            switch (row) {
                case FROST: return Control.GLASS.max;
                case OPACITY: return AppearanceLooks.OPACITY_MAX;
                case TINT: return AppearanceLooks.TINT_MAX;
                case HEIGHT:
                    if (mCard == EditorCard.KEYBOARD)
                        return Math.round(TermuxPreferenceConstants.TERMUX_APP
                            .MAX_IN_APP_KEYBOARD_HEIGHT_SCALE * 100f);
                    return AppearanceLooks.iconSizeRangeDp(mHost.isFloatingDock(),
                        mHost.isAppsRowAlone(), density())[1];
                case CORNERS: return AppearanceLooks.CORNERS_MAX_DP;
                case MARGIN: return AppearanceLooks.MARGIN_MAX_DP;
                case DEPTH: return GlassEdgeControls.MAX;
                case APP_ICONS: return AppearanceLooks.APP_ICONS_MAX;
                case TEXT_SIZE: return Control.TEXT_SIZE.max;
                case SPLIT_GAP: return Control.SPLIT_GAP.max;
                case ICON_SIZE: return HOME_ICON_MAX;
                case KEY_OPACITY: return Control.KEY_OPACITY.max;
                case KEY_CORNERS: return AppearanceLooks.KEY_CORNERS_MAX_DP;
                case KEY_SPACING: return AppearanceLooks.KEY_SPACING_MAX_TENTHS;
                case LOOK: return AppearanceLooks.CUSTOM_STOP;
                case ALIGNMENT: return TermuxPreferenceConstants.TERMUX_APP.MAX_WALLPAPER_RENDER_ZOOM;
                case VIBRANCY: return Control.VIBRANCY.max;
                case GLASS_EDGE: return Control.EDGE.max;
                default: return 1;
            }
        }

        @Override public int step(@NonNull Row row) {
            switch (row) {
                case TEXT_SIZE: return Control.TEXT_SIZE.step;
                case ICON_SIZE: return HOME_ICON_STEP;
                default: return 1;
            }
        }

        @NonNull @Override public String readout(@NonNull Row row, int value) {
            switch (row) {
                case FROST:
                case OPACITY:
                case TINT:
                case DEPTH:
                case TEXT_SIZE:
                case ICON_SIZE:
                case KEY_OPACITY:
                case ALIGNMENT:
                case GLASS_EDGE:
                    return getString(R.string.appearance_readout_percent, value);
                case HEIGHT:
                    return mCard == EditorCard.KEYBOARD
                        ? getString(R.string.appearance_readout_percent, value)
                        : getString(R.string.appearance_readout_dp, value);
                case CORNERS:
                case MARGIN:
                case SPLIT_GAP:
                case KEY_CORNERS:
                    return getString(R.string.appearance_readout_dp, value);
                case KEY_SPACING:
                    return String.format(Locale.getDefault(), "%.1f", value / 10f);
                case VIBRANCY:
                    return getString(R.string.appearance_readout_times,
                        String.format(Locale.getDefault(), "%.2f", value / 100f));
                case APP_ICONS:
                    return String.valueOf(value);
                case LOOK:
                    return lookStopName(value);
                default:
                    return String.valueOf(value);
            }
        }

        @Override public boolean ownValue(@NonNull Row row) {
            TermuxAppSharedPreferences prefs = prefs();
            EditorCard card = mCard;
            if (prefs == null || card == null || card.slot == null
                || !EditorRows.marksOwnValue(card, row))
                return false;
            SurfaceProperty property = row.property();
            if (property == null)
                return false;
            WallHalf half = readHalf(row);
            // Frost is the surface's own while either of the pair it writes is.
            if (row == Row.FROST)
                return !prefs.isSurfaceInheriting(card.slot, SurfaceProperty.BLUR, half)
                    || !prefs.isSurfaceInheriting(card.slot, SurfaceProperty.GRAIN, half);
            return !prefs.isSurfaceInheriting(card.slot, property, half);
        }

        @Override public boolean enabled(@NonNull Row row) {
            return true;
        }

        @Nullable @Override public CharSequence summary(@NonNull Row row) {
            TermuxAppSharedPreferences prefs = prefs();
            if (prefs == null)
                return null;
            switch (row) {
                case CURSOR_TRAIL:
                    return optionLabel(R.array.appearance_editor_cursor_trail_entries,
                        R.array.appearance_editor_cursor_trail_values,
                        CursorTrailChoice.menuId(prefs.isTerminalCursorTrailEnabled(),
                            prefs.getTerminalCursorTrailStyle()));
                case EFFECT:
                    return optionLabel(R.array.settings_terminal_retro_effect_entries,
                        R.array.settings_terminal_retro_effect_values, prefs.getTerminalRetroEffect());
                case TINT_HUE:
                    return optionLabel(R.array.appearance_editor_tint_hue_entries,
                        R.array.appearance_editor_tint_hue_values, prefs.getSurfaceGlassTintHue());
                case CLOCK_FACE:
                    return getString(clockStyleLabel(prefs.getTopPaneClockStyle()));
                case GLASS_WIDGETS:
                    return getString(R.string.appearance_glass_widgets_note);
                case VISIBLE: {
                    LayoutEditorController layout = mHost.layoutEditor();
                    if (layout != null && layout.showsOtherOrientation())
                        return getString(R.string.appearance_other_orientation_note);
                    return null;
                }
                default:
                    return null;
            }
        }

        @NonNull @Override public String[] choices(@NonNull Row row) {
            switch (row) {
                case STYLE:
                    return strings(R.string.appearance_editor_style_docked,
                        R.string.appearance_editor_style_floating);
                case DESIGN: return strings(DESIGN_LABELS);
                case CONTRAST:
                    return strings(R.string.appearance_contrast_softer,
                        R.string.appearance_contrast_default, R.string.appearance_contrast_harder);
                case LABEL_SIZE:
                    return strings(R.string.appearance_size_small, R.string.appearance_size_medium,
                        R.string.appearance_size_large);
                case FOLDERS:
                    return strings(R.string.appearance_folders_compact,
                        R.string.appearance_folders_default, R.string.appearance_folders_roomy);
                case NUMERALS:
                    return strings(R.string.appearance_numerals_sans, R.string.appearance_numerals_mono);
                case FORM:
                    return strings(R.string.appearance_form_docked, R.string.appearance_form_floating,
                        R.string.appearance_form_split);
                case THEME:
                    return strings(R.string.appearance_theme_system, R.string.appearance_theme_light,
                        R.string.appearance_theme_dark);
                case LOOK: {
                    String[] out = new String[AppearanceLooks.STOP_COUNT];
                    for (int i = 0; i < out.length; i++)
                        out[i] = lookStopName(i);
                    return out;
                }
                default:
                    return new String[0];
            }
        }

        @Nullable @Override public int[] chipIcons(@NonNull Row row) {
            return row == Row.DESIGN ? DESIGN_ICONS.clone() : null;
        }

        @Nullable @Override public View expanderContent(@NonNull Row row) {
            if (row != Row.ICON_PACK)
                return null;
            ensureIconsContent();
            return mIconsPage == null ? null : mIconsPage.root();
        }

        @Override public void onValue(@NonNull Row row, int value, boolean dragging) {
            beginDrag(dragging);
            writeRow(row, value, dragging);
        }

        @Override public void onReleased(@NonNull Row row) {
            mLinkAnchor = null;
            endDrag();
            if (row == Row.HEIGHT && mCard == EditorCard.KEYBOARD) {
                LayoutEditorController layout = mHost.layoutEditor();
                if (layout != null) layout.relayHeldWrites();
            }
            if (mSheet != null) mSheet.restate();
        }

        @Override public void onAction(@NonNull Row row, @NonNull View anchor) {
            onRowAction(row, anchor);
        }

        @Override public boolean shown(@NonNull Row row) {
            // All surfaces is the Custom stop's, as the Look page's Custom row was; the
            // wallpaper's Alignment stands at every stop.
            if (mCard == EditorCard.APPEARANCE && EditorRows.isAllSurfaces(row))
                return AppearanceLooks.isCustomStop(lookStop(mHalf));
            return true;
        }

        @Nullable @Override public CharSequence stripHeading(@NonNull Row first) {
            return mCard == EditorCard.APPEARANCE && EditorRows.isAllSurfaces(first)
                ? getString(R.string.appearance_strip_all_surfaces) : null;
        }
    }

    /** Home's icon size, a percent of the dock's 48dp, as Settings › Home offers it. */
    private static final int HOME_ICON_MIN = 80;
    private static final int HOME_ICON_MAX = 120;
    private static final int HOME_ICON_STEP = 10;

    private static int parseInt(@Nullable String value, int fallback) {
        if (value == null)
            return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @NonNull
    private String lookStopName(int stop) {
        switch (AppearanceLooks.presetIdForStop(stop) == null ? "custom"
            : AppearanceLooks.presetIdForStop(stop)) {
            case "minimal": return getString(R.string.termux_surface_preset_minimal);
            case "frost": return getString(R.string.termux_surface_preset_frost);
            case "stock": return getString(R.string.termux_surface_preset_stock);
            case "solid": return getString(R.string.termux_surface_preset_solid);
            default: return getString(R.string.termux_surface_preset_custom);
        }
    }

    @NonNull
    private String optionLabel(int entriesRes, int valuesRes, @NonNull String id) {
        String[] entries = getResources().getStringArray(entriesRes);
        String[] values = getResources().getStringArray(valuesRes);
        for (int i = 0; i < values.length && i < entries.length; i++)
            if (values[i].equals(id)) return entries[i];
        return entries.length > 0 ? entries[0] : id;
    }

    /** One row moved: the write it stands for, on the half or the wall. */
    private void writeRow(@NonNull Row row, int value, boolean dragging) {
        TermuxAppSharedPreferences prefs = prefs();
        LayoutEditorController layout = mHost.layoutEditor();
        EditorCard card = mCard;
        Context context = mHost.context();
        if (prefs == null || card == null)
            return;
        switch (row) {
            case FROST:
                // Each radius re-blurs the frames: a drag holds that for its release.
                holdFrameBakesForDrag();
                writeFrost(card, value);
                break;
            case OPACITY:
            case TINT:
                // Each value re-bakes the blurred frames: a drag holds that for its release.
                holdFrameBakesForDrag();
                if (card == EditorCard.HOME)
                    writeHomeGlass(row, value);
                else if (card.slot == null)
                    writeShared(row, value);
                else if (!writeLinkedOpacity(row, value, dragging))
                    writeSurface(card, row, value);
                break;
            case HEIGHT:
                if (card == EditorCard.KEYBOARD) {
                    if (layout != null) layout.setKeyboardHeightScale(value / 100f, dragging);
                } else {
                    writeIconSize(value);
                }
                syncDirty();
                break;
            case VISIBLE: {
                if (layout == null) break;
                Element element = elementOf(card);
                if (element == null) {
                    layout.setKeyboardShown(value != 0);
                    if (value != 0 && !mHost.isInAppKeyboardShown()) {
                        mRaisedKeyboard = mHost.showInAppKeyboardForEditor() || mRaisedKeyboard;
                    } else if (value == 0 && mHost.isInAppKeyboardShown()) {
                        // The frame stops drawing it: off reads off, and its card joins the stack.
                        mHost.hideInAppKeyboardForEditor();
                        mRaisedKeyboard = false;
                    }
                } else {
                    layout.setShown(element, value != 0);
                }
                positionTargets();
                break;
            }
            case STYLE:
                writeStyle(value == 1);
                break;
            case DESIGN:
                if (layout != null && value >= 0 && value < DockStyle.CHOICES.size())
                    layout.setDockStyle(DockStyle.CHOICES.get(value));
                break;
            case CORNERS: writeCorners(value); break;
            case MARGIN: writeMargin(value); break;
            case DEPTH: writeGlassDepth(value); break;
            case SEARCH_FIELD: if (layout != null) layout.setSearchField(value != 0); break;
            case DRAWER_ICON: if (layout != null) layout.setDrawerTile(value != 0); break;
            case APP_ICONS: writeAppIcons(value); break;
            case TEXT_SIZE: writeTextSize(value); break;
            case CONTRAST: writeLegibility(value); break;
            case EDGE_COLOURS:
                AppearanceSettingsStore.putBoolean(context, AppearanceSettingsStore.KEY_PADDING_FILL,
                    value != 0);
                settingsWritten();
                break;
            case SPLIT_GAP: writeSplitGap(value); break;
            case ICON_SIZE:
                writeHomeString(HomeSettings.KEY_ICON_SCALE, String.valueOf(value));
                break;
            case LABEL_SIZE:
                writeHomeString(HomeSettings.KEY_LABEL_SIZE, LABEL_SIZES[clamp(value, 0, 2)]);
                break;
            case GLASS_WIDGETS:
                AppearanceSettingsStore.putBoolean(context, "widgets_on_glass", value != 0);
                // A widget's own On or Off overrode the switch, so on a phone whose widgets
                // predate it (they stored "on") it did nothing: the switch now applies to every
                // widget, and each can still choose its own look afterwards.
                for (java.util.Map.Entry<Integer, String> entry
                        : mHost.clearWidgetGlassChoices().entrySet())
                    mClearedWidgetGlass.putIfAbsent(entry.getKey(), entry.getValue());
                settingsWritten();
                break;
            case TWO_LINE:
                writeHomeBoolean(HomeSettings.KEY_LABEL_TWO_LINES, value != 0);
                break;
            case LABEL_SHADOW:
                writeHomeBoolean(HomeSettings.KEY_LABEL_SHADOW, value != 0);
                break;
            case FOLDERS:
                writeHomeString(HomeSettings.KEY_FOLDER_DENSITY, FOLDER_DENSITIES[clamp(value, 0, 2)]);
                break;
            case NUMERALS:
                AppearanceSettingsStore.putString(context, "app_launcher_builtin_widget_style",
                    NUMERALS[clamp(value, 0, 1)]);
                settingsWritten();
                break;
            case PAGE_DOTS:
                writeHomeString(HomeSettings.KEY_PAGE_INDICATOR, value != 0 ? "dots" : "off");
                break;
            case WALLPAPER_SCROLL:
                AppearanceSettingsStore.putBoolean(context, "wallpaper_parallax", value != 0);
                settingsWritten();
                break;
            case OWN_LAYOUT: if (layout != null) layout.setHomeLayout(value != 0); break;
            case CLOCK_12H:
                if (prefs.isTopPaneClockAmPmEnabled() != (value != 0)) {
                    prefs.setTopPaneClockAmPmEnabled(value != 0);
                    mHost.refreshTerminalWindowBar();
                    syncDirty();
                }
                break;
            case KEY_OPACITY:
                if (!writeLinkedOpacity(row, value, dragging))
                    writeKeyOpacity(value);
                break;
            case KEY_CORNERS: writeKeyCorners(value); break;
            case KEY_SPACING: writeKeySpacing(value); break;
            case FORM:
                if (layout != null && value >= 0 && value < FORMS.length)
                    layout.setKeyboardForm(FORMS[value]);
                break;
            case LOOK:
                writeLook(value, dragging);
                break;
            case WALLPAPER:
                AppearanceSettingsStore.putBoolean(context, "use_system_wallpaper", value != 0);
                settingsWritten();
                break;
            case THEME:
                previewTheme(THEMES[clamp(value, 0, 2)]);
                break;
            case MONOCHROME:
                AppearanceSettingsStore.putBoolean(context, "app_launcher_bw_icons", value != 0);
                settingsWritten();
                break;
            case WALLPAPER_COLOURS:
                AppearanceSettingsStore.putBoolean(context, "terminal_dynamic_colors_enabled",
                    value != 0);
                settingsWritten();
                break;
            case FULL_SCREEN:
                AppearanceSettingsStore.putBoolean(context, AppearanceSettingsStore.KEY_FULLSCREEN,
                    value != 0);
                settingsWritten();
                break;
            case MINIMAL:
                mHost.setMinimalMode(value != 0);
                syncDirty();
                break;
            case ALIGNMENT:
                if (!dragging) {
                    AppearanceSettingsStore.putInt(context, "wallpaper_render_zoom", value);
                    settingsWritten();
                }
                break;
            case VIBRANCY: writeVibrancy(value); break;
            case GLASS_EDGE: writeGlassEdge(value); break;
            default:
                break;
        }
    }

    /** A Home row stored as text: written as Settings writes it, and the live Home takes it. */
    private void writeHomeString(@NonNull String key, @NonNull String value) {
        Context context = mHost.context();
        if (value.equals(AppearanceSettingsStore.getString(context, key, null)))
            return;
        AppearanceSettingsStore.putString(context, key, value);
        settingsWritten();
        mHost.applyHomeSettings();
    }

    private void writeHomeBoolean(@NonNull String key, boolean value) {
        AppearanceSettingsStore.putBoolean(mHost.context(), key, value);
        settingsWritten();
        mHost.applyHomeSettings();
    }

    /** A row that opens something: a menu, a Settings page, the picker, the icon packs. */
    private void onRowAction(@NonNull Row row, @NonNull View anchor) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        switch (row) {
            case CURSOR_TRAIL:
                showChoiceMenu(anchor, R.array.appearance_editor_cursor_trail_entries,
                    R.array.appearance_editor_cursor_trail_values,
                    CursorTrailChoice.menuId(prefs.isTerminalCursorTrailEnabled(),
                        prefs.getTerminalCursorTrailStyle()), this::writeTrailStyle);
                break;
            case EFFECT:
                showChoiceMenu(anchor, R.array.settings_terminal_retro_effect_entries,
                    R.array.settings_terminal_retro_effect_values, prefs.getTerminalRetroEffect(),
                    this::writeRetroEffect);
                break;
            case TINT_HUE:
                showChoiceMenu(anchor, R.array.appearance_editor_tint_hue_entries,
                    R.array.appearance_editor_tint_hue_values, prefs.getSurfaceGlassTintHue(),
                    this::writeTintHue);
                break;
            case CLOCK_FACE:
                showClockDropdown(anchor);
                break;
            case KEYBOARD_THEME:
                openKeyboardTheme();
                break;
            case WALLPAPER:
                if (mPageListener != null) mPageListener.onOpenWallpaper();
                break;
            case ICON_PACK: {
                AppearanceSurfaceController.Page page = mIconsPage;
                if (page != null) {
                    boolean shown = page.root().isShown();
                    if (shown) page.onShown();
                    else page.onHidden();
                }
                break;
            }
            case TOOL_COLOURS:
                openSettingsPage(TermuxStylePreferencesFragment.class, R.string.appearance_row_tool_colours);
                break;
            case LINK_GESTURES:
                openSettingsPage(HomePreferencesFragment.class, R.string.appearance_row_gestures);
                break;
            case LINK_PAGES:
                openSettingsPage(HomePreferencesFragment.class, R.string.appearance_row_pages);
                break;
            case LINK_INDICATORS:
                openSettingsPage(StatusBarPreferencesFragment.class, R.string.appearance_row_indicators);
                break;
            case LINK_FONT:
                openSettingsPage(TermuxFontsPreferencesFragment.class, R.string.appearance_row_font);
                break;
            case LINK_DOCK_ITEMS:
                openSettingsPage(LauncherDockPreferencesFragment.class, R.string.appearance_row_dock_items);
                break;
            case LINK_KEYBOARD_SIZE:
                openSettingsPage(KeyboardSizePreferencesFragment.class,
                    R.string.appearance_row_keyboard_size);
                break;
            case LINK_LAYOUTS:
                openSettingsPage(KeyboardLayoutsFragment.class, R.string.appearance_row_layouts);
                break;
            default:
                break;
        }
    }

    private interface Choice {
        void chosen(@NonNull String id);
    }

    /** The entries/values arrays as a single-choice menu on {@code anchor}. */
    private void showChoiceMenu(@NonNull View anchor, int entriesRes, int valuesRes,
                                @NonNull String current, @NonNull Choice choice) {
        String[] entries = getResources().getStringArray(entriesRes);
        String[] values = getResources().getStringArray(valuesRes);
        PopupMenu menu = new PopupMenu(mHost.context(), anchor, Gravity.END);
        for (int i = 0; i < entries.length && i < values.length; i++) {
            MenuItem item = menu.getMenu().add(Menu.NONE, i, i, entries[i]);
            item.setCheckable(true);
            item.setChecked(values[i].equals(current));
        }
        menu.getMenu().setGroupCheckable(Menu.NONE, true, true);
        menu.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            if (index >= 0 && index < values.length && !values[index].equals(current)) {
                choice.chosen(values[index]);
                if (mSheet != null) mSheet.restate();
            }
            return true;
        });
        menu.show();
    }

    /** Builds the icon packs once per session for the Appearance card's Icon pack row. */
    private void ensureIconsContent() {
        if (mIconsPage != null)
            return;
        mIconsPage = mHost.createIconsContent();
    }

    private void releaseIconsContent() {
        AppearanceSurfaceController.Page page = mIconsPage;
        mIconsPage = null;
        if (page != null) {
            View root = page.root();
            if (root.getParent() instanceof ViewGroup)
                ((ViewGroup) root.getParent()).removeView(root);
            page.release();
        }
    }

    // ----------------------------------------------------------------------- the surface writes

    /**
     * One of a surface's glass numbers: the surface's own value on the half the frame shows (both
     * halves for the terminal), so the other surfaces and the other half keep theirs. The
     * terminal's opacity also keeps the copy the wallpaper mode restores from.
     */
    private void writeSurface(@NonNull EditorCard card, @NonNull Row row, int value) {
        TermuxAppSharedPreferences prefs = prefs();
        SurfaceProperty property = row.property();
        if (prefs == null || card.slot == null || property == null)
            return;
        int written;
        int scopes = SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD;
        switch (property) {
            case TINT:
                written = AppearanceLooks.tintPercent(value);
                break;
            default:
                written = AppearanceLooks.opacityPercent(value);
                break;
        }
        prefs.detachSurfaceValue(card.slot, property, writeHalf(row), written);
        if (card == EditorCard.TERMINAL && property == SurfaceProperty.OPACITY)
            prefs.setTerminalBackgroundOpacity(written);
        requestPreview(scopes);
        EditorCardSheet sheet = mSheet;
        if (sheet != null && !mSliderDragActive) {
            sheet.restate(row);
            sheet.restate(Row.LOOK);
        }
    }

    /**
     * Where Frost stands on {@code card}: the shared glass of the frame's half on the Appearance
     * card, else the surface's own pair. The position it was left at reads back while the pair is
     * still that position's ({@link GlassControls#readBack}).
     */
    private int frostValue(@NonNull TermuxAppSharedPreferences prefs, @NonNull EditorCard card) {
        WallHalf half = readHalf(Row.FROST);
        if (card.slot == null)
            return GlassControls.readBack(prefs.getSurfaceGlassPosition(null),
                prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, half),
                prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN, half));
        return GlassControls.readBack(prefs.getSurfaceGlassPosition(card.slot),
            prefs.getSurfaceValue(card.slot, SurfaceProperty.BLUR, half),
            prefs.getSurfaceValue(card.slot, SurfaceProperty.GRAIN, half));
    }

    /**
     * Frost: blur and grain together along {@link GlassControls}' curve, and the position it was
     * left at. On the Appearance card it is the shared glass of the frame's half, every surface on
     * that half put back on it ({@link GlobalGlass}); on a surface's card, that surface's own
     * pair on the half, as the previous editor's Frost wrote it for a tapped element.
     */
    private void writeFrost(@NonNull EditorCard card, int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int glass = Control.GLASS.clamp(value);
        int blur = GlassControls.blurForGlass(glass);
        int grain = GlassControls.grainForGlass(glass);
        WallHalf half = writeHalf(Row.FROST);
        prefs.setSurfaceGlassPosition(card.slot, glass);
        if (card == EditorCard.HOME) {
            // Home's Base alone: a surface that holds its own Home value keeps it.
            prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME,
                AppearanceLooks.blurDp(blur));
            prefs.setSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.HOME,
                AppearanceLooks.grainPercent(grain));
        } else if (card.slot == null) {
            GlobalGlass.write(prefs, SurfaceProperty.BLUR, half, blur);
            GlobalGlass.write(prefs, SurfaceProperty.GRAIN, half, grain);
        } else {
            prefs.detachSurfaceValue(card.slot, SurfaceProperty.BLUR, half, blur);
            prefs.detachSurfaceValue(card.slot, SurfaceProperty.GRAIN, half, grain);
        }
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD | SurfaceEditorProperties.PREVIEW_BLUR);
        restateGlassRow(Row.FROST);
    }

    /**
     * The Appearance card's Opacity or Tint: the shared value of the frame's half, every surface
     * on that half put back on it ({@link GlobalGlass}).
     */
    private void writeShared(@NonNull Row row, int value) {
        TermuxAppSharedPreferences prefs = prefs();
        SurfaceProperty property = row.property();
        if (prefs == null || property == null)
            return;
        GlobalGlass.write(prefs, property, writeHalf(row), value);
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD);
        restateGlassRow(row);
    }

    /**
     * Home's Opacity or Tint: the Home half's Base, which Home's widgets, status bar and dock
     * wear while they follow it. Unlike the Appearance card's shared glass it puts no surface
     * back on the Base: a dock or status bar given its own Home value on its card keeps it.
     */
    private void writeHomeGlass(@NonNull Row row, int value) {
        TermuxAppSharedPreferences prefs = prefs();
        SurfaceProperty property = row.property();
        if (prefs == null || property == null)
            return;
        int clamped = row == Row.OPACITY ? AppearanceLooks.opacityPercent(value)
            : AppearanceLooks.tintPercent(value);
        prefs.setSurfaceBaseValue(property, WallHalf.HOME, clamped);
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD);
        restateGlassRow(row);
    }

    /** A glass row and the Look slider read again, unless a drag holds restatement. */
    private void restateGlassRow(@NonNull Row row) {
        EditorCardSheet sheet = mSheet;
        if (sheet != null && !mSliderDragActive) {
            sheet.restate(row);
            sheet.restate(Row.LOOK);
        }
    }

    /** Style: one for both halves; the dress a launch gives, nothing carried over from the old one. */
    private void writeStyle(boolean floating) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        TermuxAppSharedPreferences.LayoutStyle style = floating
            ? TermuxAppSharedPreferences.LayoutStyle.FLOATING
            : TermuxAppSharedPreferences.LayoutStyle.DOCKED;
        if (style == prefs.getLayoutStyle())
            return;
        prefs.setLayoutStyle(style);
        mHost.redressChrome();
        applyStructuralPreview();
        syncLayoutCanvas();
        syncDirty();
        if (mSheet != null) mSheet.restate();
    }

    // ---- the Look slider ---------------------------------------------------------------------

    /** Where the Look slider stands for a half: Custom where it was put there, else the match. */
    private int lookStop(@NonNull WallHalf half) {
        return mHeldCustom.contains(half) ? AppearanceLooks.CUSTOM_STOP : matchingStop(half);
    }

    /** The stop a half wears: the Look it matches, or Custom. */
    private int matchingStop(@NonNull WallHalf half) {
        TermuxAppSharedPreferences prefs = prefs();
        return prefs == null ? AppearanceLooks.CUSTOM_STOP : matchingStop(prefs, half);
    }

    /** The stop a half wears in {@code prefs} (the store, or a drag's batch over it). */
    private static int matchingStop(@NonNull TermuxAppSharedPreferences prefs,
                                    @NonNull WallHalf half) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets(prefs)) {
            if (SurfacePresets.matches(prefs, preset, half))
                return AppearanceLooks.stopForPresetId(preset.id);
        }
        return AppearanceLooks.CUSTOM_STOP;
    }

    /** The Look slider moved: a drag previews each stop it crosses; a release or a tap stores it. */
    private void writeLook(int stop, boolean dragging) {
        if (dragging) {
            previewStop(stop);
            return;
        }
        settleLookDrag();
        TermuxAppSharedPreferences prefs = prefs();
        boolean custom = AppearanceLooks.isCustomStop(stop);
        // Leaving Custom for the Look the values still match writes nothing, but the slider
        // and the All surfaces strip follow the stop.
        boolean held = custom ? mHeldCustom.add(mHalf) : mHeldCustom.remove(mHalf);
        if (prefs != null && moveToStop(prefs, stop))
            syncAfterBulkWrite();
        else if (held && mSheet != null)
            mSheet.restate();
    }

    /**
     * The Look slider landed on {@code stop} for the half the frame shows, written into
     * {@code into} (the store, or a drag's batch over it). A Look resets every surface on that
     * half to it; leaving a Custom the user touched keeps it for this visit and stores it as the
     * saved Custom ({@link SessionCustom}). Custom brings back this visit's values, else the
     * saved Custom, else writes nothing: the Look on screen is its seed. False when the half
     * already stood there.
     */
    private boolean moveToStop(@NonNull TermuxAppSharedPreferences into, int stop) {
        int current = matchingStop(into, mHalf);
        if (stop == current)
            return false;
        SurfacePresets.Preset look = AppearanceLooks.presetForStop(SurfacePresets.presets(into),
            stop);
        if (look != null) {
            if (AppearanceLooks.isCustomStop(current))
                mSessionCustom.leave(into);
            SurfacePresets.applyLook(into, look, mHalf);
            forgetGlassEdgePositions();
            return true;
        }
        mSessionCustom.enter(into, mHalf);
        return true;
    }

    static final class SessionCustom {
        @Nullable private AppearanceSnapshot mKept;
        /** {@link #mKept} half by half: what the Custom stop on one half puts back on it alone. */
        @Nullable private java.util.Map<WallHalf, SurfacePresets.Preset> mKeptHalves;
        /**
         * What Custom stood at when the slider (or a tap at a Look) moved there, while nothing was
         * kept: the Look on screen, or the saved Custom just applied. Custom left exactly as it was
         * is not this visit's work, so it is neither kept nor saved, and the next move to Custom
         * starts over (Clear → Custom → Mist → Custom reads as Mist, not Clear, when nothing is
         * saved).
         */
        @Nullable private String mSeedSignature;

        /**
         * The slider leaves Custom: the values in {@code live} are this visit's Custom, and the
         * saved one, unless untouched.
         */
        void leave(@NonNull TermuxAppSharedPreferences live) {
            AppearanceSnapshot now = AppearanceSnapshot.capture(live);
            mKept = now.signature().equals(mSeedSignature) ? null : now;
            mKeptHalves = null;
            mSeedSignature = null;
            if (mKept != null) {
                mKeptHalves = new java.util.EnumMap<>(WallHalf.class);
                for (WallHalf half : WallHalf.values())
                    mKeptHalves.put(half, SurfacePresets.customOf(live, half));
                SurfacePresets.saveCustom(live);
            }
        }

        /**
         * The slider returns to Custom: the kept values come back, else the saved Custom, else
         * nothing is written.
         */
        void enter(@NonNull TermuxAppSharedPreferences into) {
            AppearanceSnapshot kept = mKept;
            if (kept != null) {
                kept.restore(into);
                return;
            }
            SurfacePresets.Preset saved = SurfacePresets.custom(into);
            if (saved != null)
                SurfacePresets.applyCustom(into, saved);
            mSeedSignature = AppearanceSnapshot.signatureOf(into);
        }

        /**
         * The slider returns to Custom on {@code half}, the half the frame shows: that half's kept
         * values come back, else its part of the saved Custom, else nothing is written. The other
         * half is never written: a saved Custom that names Home's own look once brought it back
         * on Home from the Workspace's slider (pong, 2026-10-25: Home stood on Solid's numbers
         * nobody chose there).
         */
        void enter(@NonNull TermuxAppSharedPreferences into, @NonNull WallHalf half) {
            java.util.Map<WallHalf, SurfacePresets.Preset> kept = mKeptHalves;
            if (mKept != null && kept != null && kept.get(half) != null) {
                SurfacePresets.applyCustom(into, kept.get(half), half);
                return;
            }
            SurfacePresets.Preset saved = SurfacePresets.custom(into);
            if (saved != null)
                SurfacePresets.applyCustom(into, saved, half);
            mSeedSignature = AppearanceSnapshot.signatureOf(into);
        }

        /**
         * The editor stands at Custom over a Look without writing (a tap at a Look stop): what
         * is on screen is the seed. Values kept
         * earlier this visit are already the saved Custom, so a seed left untouched gives them
         * back on the next move to Custom.
         */
        void seed(@NonNull TermuxAppSharedPreferences live) {
            mSeedSignature = AppearanceSnapshot.signatureOf(live);
        }

        /** Whether {@code live} is Custom as the user changed it, not as it was seeded. */
        boolean isTouched(@NonNull TermuxAppSharedPreferences live) {
            return mSeedSignature == null
                || !mSeedSignature.equals(AppearanceSnapshot.signatureOf(live));
        }

        void clear() {
            mKept = null;
            mKeptHalves = null;
            mSeedSignature = null;
        }

        /** A copy, for a session carried over a relaunch (the kept snapshot is never written). */
        @NonNull
        SessionCustom copy() {
            SessionCustom out = new SessionCustom();
            out.copyFrom(this);
            return out;
        }

        void copyFrom(@NonNull SessionCustom other) {
            mKept = other.mKept;
            mKeptHalves = other.mKeptHalves;
            mSeedSignature = other.mSeedSignature;
        }
    }

    /** The drag's writes, held over the store; null while no Look drag is under way. */
    @Nullable private SurfacePresets.Batch mLookDrag;
    @Nullable private TermuxAppSharedPreferences mLookDragPrefs;

    /** A finger on the Look slider crossed onto {@code stop}: shown, not stored. */
    private void previewStop(int stop) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null) {
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

    /** What {@link #syncAfterBulkWrite} restates, less the pane pass and the terminal's resize. */
    private void previewLook() {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        mHost.refreshTerminalPalette();
        mHost.applyTerminalMotionLook();
        SoftWallpaper.apply(mHost.findView(R.id.wallpaper_backdrop), prefs);
        requestPreview(SurfaceEditorProperties.PREVIEW_ALL);
        positionOutline();
    }

    /** The finger left the Look slider: what it showed is stored, once. */
    private void settleLookDrag() {
        SurfacePresets.Batch batch = mLookDrag;
        mLookDrag = null;
        mLookDragPrefs = null;
        if (batch == null)
            return;
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

    // ---- the keyboard's two opacities ----------------------------------------------------------

    @Nullable private int[] mLinkAnchor;
    @Nullable private Control mLinkMoved;

    /**
     * The keyboard's two opacities while its link is on (Settings › Keyboard keeps the switch):
     * a move of either moves the other by the same amount from where it stood when the move
     * began ({@link OpacityLink}). False when the link does not apply.
     */
    private boolean writeLinkedOpacity(@NonNull Row row, int value, boolean dragging) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || mCard != EditorCard.KEYBOARD || !prefs.isInAppKeyboardOpacityLinked()
            || (row != Row.OPACITY && row != Row.KEY_OPACITY))
            return false;
        Control moved = row == Row.KEY_OPACITY ? Control.KEY_OPACITY : Control.OPACITY;
        Control partner = OpacityLink.partner(moved);
        if (partner == null)
            return false;
        int background = AppearanceLooks.opacityPercent(prefs.getSurfaceValue(SurfaceSlot.KEYBOARD,
            SurfaceProperty.OPACITY, readHalf(Row.OPACITY)));
        int keys = keyOpacityValue(prefs);
        if (mLinkAnchor == null || mLinkMoved != moved) {
            mLinkAnchor = moved == Control.KEY_OPACITY ? new int[] {keys, background}
                : new int[] {background, keys};
            mLinkMoved = moved;
        }
        int follow = OpacityLink.follow(mLinkAnchor[0], value, mLinkAnchor[1], partner);
        if (moved == Control.KEY_OPACITY) {
            writeKeyOpacity(value);
            writeSurface(EditorCard.KEYBOARD, Row.OPACITY, follow);
        } else {
            writeSurface(EditorCard.KEYBOARD, Row.OPACITY, value);
            writeKeyOpacity(follow);
        }
        if (!dragging)
            mLinkAnchor = null;
        if (mSheet != null && !mSliderDragActive) {
            mSheet.restate(Row.OPACITY);
            mSheet.restate(Row.KEY_OPACITY);
        }
        return true;
    }

    // ---- the global writes ---------------------------------------------------------------------

    /** Split gap: the gutter between split panes; the release commits the resize once. */
    private void writeSplitGap(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int written = Control.SPLIT_GAP.clamp(value);
        if (prefs.getTerminalPaneGap() == written)
            return;
        prefs.setTerminalPaneGap(written);
        requestGeometryPreview();
    }

    /** The shared Text size: the labels and the legends each tick, the terminal on the release. */
    private void writeTextSize(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int written = Control.TEXT_SIZE.clamp(value);
        if (prefs.getTextSizePercent() == written)
            return;
        prefs.setTextSizePercent(written);
        requestPreview(SurfaceEditorProperties.PREVIEW_TEXT
            | SurfaceEditorProperties.PREVIEW_TEXT_TERMINAL);
    }

    /** The glass's Depth: one value for every glass surface ({@link GlassEdgeControls}). */
    private void writeGlassDepth(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int written = Control.DEPTH.clamp(value);
        GlassEdgeControls.Edge next = glassEdgeOf(prefs).withDepth(written);
        prefs.setFancierGlassBendDp(next.bend);
        prefs.setFancierGlassEdgeWidthDp(next.width);
        mDepthSet = written;
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    private void forgetGlassEdgePositions() {
        mDepthSet = GlassEdgeControls.NONE;
    }

    /** True while a slider drag holds the frame tint's re-bake for its release. */
    private boolean mFrameBakesHeld;

    private void holdFrameBakesForDrag() {
        if (!defersHeavyPasses() || mFrameBakesHeld)
            return;
        mFrameBakesHeld = true;
        mHost.holdGlassFrameBakes(true);
    }

    private void releaseFrameBakes() {
        if (!mFrameBakesHeld)
            return;
        mFrameBakesHeld = false;
        mHost.holdGlassFrameBakes(false);
        requestPreview(SurfaceEditorProperties.PREVIEW_BLUR);
    }

    private static final int NO_HELD_VIBRANCY = Integer.MIN_VALUE;
    /** The Vibrancy a slider drag stands at, written on release: every value re-bakes the frames. */
    private int mHeldVibrancy = NO_HELD_VIBRANCY;

    private void writeVibrancy(int value) {
        int clamped = Control.VIBRANCY.clamp(value);
        if (mSliderDragActive) {
            mHeldVibrancy = clamped;
            return;
        }
        mHeldVibrancy = NO_HELD_VIBRANCY;
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        prefs.setSurfaceGlassVibrancy(clamped);
        requestPreview(SurfaceEditorProperties.PREVIEW_BLUR
            | SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    /** Which accent Tint mixes in, global ({@link GlobalGlass#writeTintHue}). */
    private void writeTintHue(@NonNull String id) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        GlobalGlass.writeTintHue(prefs, id);
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES
            | SurfaceEditorProperties.PREVIEW_KEYBOARD);
    }

    /** The glass edge's colour split, global like Depth. */
    private void writeGlassEdge(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        prefs.setFancierGlassDispersionPercent(Control.EDGE.clamp(value));
        requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES);
    }

    @NonNull
    private static GlassEdgeControls.Edge glassEdgeOf(@NonNull TermuxAppSharedPreferences prefs) {
        return new GlassEdgeControls.Edge(prefs.getFancierGlassBendDp(),
            prefs.getFancierGlassEdgeWidthDp(), prefs.getFancierGlassEdgeLightPercent(),
            prefs.getFancierGlassSpecularPercent(), prefs.getFancierGlassDispersionPercent(),
            prefs.getFancierGlassClarityPercent());
    }

    /** Key spacing, previewed live on the keyboard. */
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

    /** The dock height scale in force: the layout session's, or the stored one without it. */
    private float dockHeightScale(@NonNull TermuxAppSharedPreferences prefs) {
        LayoutEditorController layout = mHost.layoutEditor();
        float scale = layout == null ? -1f : layout.dockHeightScale();
        return scale > 0f ? scale : prefs.getAppLauncherBarHeightScale();
    }

    /**
     * The dock's Height: the icon in dp, stored as the dock height scale that draws it, through
     * the layout session so its Undo and dirty state cover it. Mid-drag the dock follows on the
     * geometry preview and the place is re-laid once, on the release.
     */
    private void writeIconSize(int iconDp) {
        TermuxAppSharedPreferences prefs = prefs();
        boolean floating = mHost.isFloatingDock();
        boolean alone = mHost.isAppsRowAlone();
        if (prefs != null && AppearanceLooks.iconSizeValueFor(floating, alone,
                dockHeightScale(prefs), density()) == iconDp)
            return;
        float scale = AppearanceLooks.dockScaleForIconSize(floating, alone, iconDp, density());
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null && mSliderDragActive) {
            layout.holdDockHeightScale(scale);
            mDockSizeHeld = true;
            requestGeometryPreview();
        } else if (layout != null) {
            layout.setDockHeightScale(scale);
        } else if (prefs != null) {
            prefs.setAppLauncherBarHeightScale(scale);
            requestGeometryPreview();
        }
    }

    /** How many app buttons the dock shows, rebuilt live. */
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

    /** The terminal palette's contrast, rebuilt and repainted at once. */
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

    /** The cursor trail: Off or a style, the same preferences Settings writes. */
    private void writeTrailStyle(@NonNull String id) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        CursorTrailChoice.Resolved choice = CursorTrailChoice.resolve(id);
        boolean styleSame = choice.style == null
            || choice.style.equals(prefs.getTerminalCursorTrailStyle());
        if (choice.enabled == prefs.isTerminalCursorTrailEnabled() && styleSame)
            return;
        if (choice.style != null)
            prefs.setTerminalCursorTrailStyle(choice.style);
        prefs.setTerminalCursorTrailEnabled(choice.enabled);
        mHost.applyTerminalMotionLook();
        syncDirty();
    }

    /** The terminal effect, the same preference Settings writes. */
    private void writeRetroEffect(@NonNull String id) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || id.equals(prefs.getTerminalRetroEffect()))
            return;
        prefs.setTerminalRetroEffect(id);
        mHost.applyTerminalMotionLook();
        syncDirty();
    }

    /** Where Key opacity stands: the stored cap opacity, or the theme's while it owns it. */
    private int keyOpacityValue(@NonNull TermuxAppSharedPreferences prefs) {
        int stored = prefs.getInAppKeyboardKeyOpacity();
        if (stored >= 0)
            return Control.KEY_OPACITY.clamp(stored);
        TermuxInAppKeyboard keyboard = keyboard();
        return Control.KEY_OPACITY.clamp(keyboard != null
            ? keyboard.getEffectiveKeyOpacityPercent() : Control.KEY_OPACITY.max);
    }

    private void writeKeyOpacity(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int opacity = Control.KEY_OPACITY.clamp(value);
        prefs.setInAppKeyboardKeyOpacity(opacity);
        TermuxInAppKeyboard keyboard = keyboard();
        if (keyboard != null)
            keyboard.previewSurfaceEditorKeyOpacity(opacity);
        requestPreview(SurfaceEditorProperties.PREVIEW_KEYBOARD);
    }

    private void writeKeyCorners(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int radius = AppearanceLooks.keyCornersDp(value);
        prefs.setInAppKeyboardKeyCornerRadiusDp(radius);
        TermuxInAppKeyboard keyboard = keyboard();
        if (keyboard != null)
            keyboard.previewSurfaceEditorKeyCornerRadiusDp(radius);
        requestPreview(SurfaceEditorProperties.PREVIEW_KEYBOARD);
    }

    /** Corners: one radius for every surface and the terminal, on both halves. */
    private void writeCorners(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int corners = AppearanceLooks.cornersDp(value);
        prefs.setLayoutCornersDp(false, corners);
        prefs.setTerminalCornerRadius(corners);
        requestGeometryPreview();
        syncLayoutCanvas();
    }

    /** Margin: one number for all the air on screen, on both halves. */
    private void writeMargin(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int margin = AppearanceLooks.marginDp(value);
        prefs.setLayoutMarginDp(false, margin);
        requestGeometryPreview();
        syncLayoutCanvas();
    }

    /** The shape pass for Corners and Margin; mid-drag the terminal is not resized. */
    private void requestGeometryPreview() {
        int scopes = SurfaceEditorProperties.PREVIEW_ALL & ~SurfaceEditorProperties.PREVIEW_BLUR;
        if (mSliderDragActive) mDragTouchedGeometry = true;
        else scopes |= SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT;
        requestPreview(scopes);
    }

    /** The canvas re-read while it shows. */
    private void syncLayoutCanvas() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (mOpen && layout != null) {
            pushLayoutShape(layout);
            layout.sync();
        }
    }

    /** Tells the canvas the Style, Corners and Margin it draws every shape from. */
    private void pushLayoutShape(@NonNull LayoutEditorController layout) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        layout.setShape(prefs.getLayoutStyle(),
            AppearanceLooks.cornersDp(prefs.getLayoutCornersDp(false)),
            AppearanceLooks.marginDp(prefs.getLayoutMarginDp(false)));
    }

    // ---------------------------------------------------------------------- the frame's targets

    private static final int[] GROUP_IDS = {
        R.id.surface_tuning_wallpaper_gesture_group,
        R.id.surface_tuning_canvas_gesture_group,
        R.id.surface_tuning_status_gesture_group,
        R.id.surface_tuning_dock_gesture_group,
        R.id.surface_tuning_keyboard_gesture_group};

    /** The card each group opens; null is the bare frame, which is Home's on Home. */
    private static final EditorCard[] GROUP_CARDS = {
        null, EditorCard.TERMINAL, EditorCard.STATUS, EditorCard.DOCK, EditorCard.KEYBOARD};

    /** The card a group opens on the half the frame shows. */
    @Nullable
    private EditorCard cardOfGroup(int index) {
        EditorCard card = GROUP_CARDS[index];
        if (card == null)
            return mHalf == WallHalf.HOME ? EditorCard.HOME : null;
        return card;
    }

    /** Where a finger on a group stands: a tap, a slide between halves, or a hold that drags. */
    private float mDownRawX;
    private float mDownRawY;
    private boolean mSwiping;
    private boolean mForwarding;
    private long mDownTime;
    @Nullable private Runnable mHoldRunnable;

    /**
     * Every group takes its touches, so nothing under the frame receives them while the editor is
     * up. A tap raises the group's card; a plain horizontal swipe slides to the other half; a hold
     * on a surface that stands on an edge hands the finger to the layout canvas, whose lift moves
     * it.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void bindTargets() {
        final int slop = ViewConfiguration.get(mHost.context()).getScaledTouchSlop();
        for (int i = 0; i < GROUP_IDS.length; i++) {
            View group = mHost.findView(GROUP_IDS[i]);
            if (group == null)
                continue;
            final int index = i;
            group.setOnClickListener(view -> {
                EditorCard card = cardOfGroup(index);
                if (card != null) openCard(card, true);
                else closeCard(true);
            });
            group.setOnTouchListener((view, event) -> {
                if (!mOpen)
                    return false;
                if (mLiftPending) {
                    // The frame is being copied for the lift: the finger is kept for it.
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_MOVE) {
                        mPendingRawX = event.getRawX();
                        mPendingRawY = event.getRawY();
                        mPendingMoved = true;
                    } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        cancelPendingLift();
                    }
                    return true;
                }
                if (mForwarding) {
                    forwardToCanvas(event);
                    return true;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        mDownRawX = event.getRawX();
                        mDownRawY = event.getRawY();
                        mDownTime = event.getDownTime();
                        mSwiping = false;
                        scheduleHold(view, index);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - mDownRawX;
                        float dy = event.getRawY() - mDownRawY;
                        if (Math.hypot(dx, dy) > slop)
                            cancelHold(view);
                        if (!mSwiping && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy))
                            mSwiping = true;
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        cancelHold(view);
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        float dx = event.getRawX() - mDownRawX;
                        if (mSwiping) {
                            if (Math.abs(dx) >= dp(SWIPE_DP))
                                switchHalf(dx > 0 ? WallHalf.HOME : WallHalf.WORKSPACE, true);
                            return true;
                        }
                        float x = event.getX();
                        float y = event.getY();
                        if (x >= 0 && y >= 0 && x <= view.getWidth() && y <= view.getHeight())
                            view.performClick();
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        cancelHold(view);
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    default:
                        return true;
                }
            });
        }
    }

    /** A swipe this far across slides the frame to the other half. */
    private static final int SWIPE_DP = 48;

    private void scheduleHold(@NonNull View view, int index) {
        cancelHold(view);
        EditorCard card = GROUP_CARDS[index];
        if (elementOf(card) == null)
            return;
        Runnable hold = () -> {
            mHoldRunnable = null;
            startDrag();
        };
        mHoldRunnable = hold;
        view.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelHold(@NonNull View view) {
        Runnable hold = mHoldRunnable;
        mHoldRunnable = null;
        if (hold != null)
            view.removeCallbacks(hold);
    }

    /**
     * The hold landed: the canvas stands over the frame and takes the finger from here. The card
     * that is up stays up, so the frame keeps its pose from the hold through the drop: putting the
     * card down here moved the frame to its rest pose, larger and further across, under a finger
     * already on an element.
     */
    private void startDrag() {
        LayoutCanvasView canvas = mCanvas;
        if (!mOpen || canvas == null || mLayoutFrame == null)
            return;
        mCanvasForDrag = !mCanvasShown;
        // In the orientation on screen the drag is live: the frame is copied first, as it is
        // drawn now, and the lift starts once the pictures stand in for it.
        // Pending from here: the copy may come back at once (liftHeld then clears it).
        mLiftPending = true;
        mPendingMoved = false;
        if (mCanvasForDrag && beginLiveCapture(this::liftHeld))
            return;
        liftHeld();
    }

    /** The hold's finger handed to the canvas, whose lift starts under it. */
    private void liftHeld() {
        mLiftPending = false;
        LayoutCanvasView canvas = mCanvas;
        if (!mOpen || canvas == null)
            return;
        showCanvas(true, false);
        mForwarding = true;
        MotionEvent down = MotionEvent.obtain(mDownTime, SystemClock.uptimeMillis(),
            MotionEvent.ACTION_DOWN, mDownRawX, mDownRawY, 0);
        forwardToCanvas(down);
        down.recycle();
        if (!canvas.isLifting()) {
            // Nothing liftable under the finger: the pictures go at once.
            if (canvas.isLive()) {
                canvas.endLive();
                finishLive();
            }
            return;
        }
        if (mForwarding && mPendingMoved) {
            MotionEvent move = MotionEvent.obtain(mDownTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE, mPendingRawX, mPendingRawY, 0);
            forwardToCanvas(move);
            move.recycle();
        }
        setLifted(true);
    }

    // ---- the live drag (SPEC §3.6a items 1 and 2) -------------------------------------------

    /** The pictures that stand in for the frame's elements during a drag, or null. */
    @Nullable private LiveDragCapture mLive;
    /** A hold landed and the frame is being copied: the finger waits for the lift. */
    private boolean mLiftPending;
    private boolean mPendingMoved;
    private float mPendingRawX;
    private float mPendingRawY;
    /** A rail card carried through the canvas, once its lift has started; null otherwise. */
    @Nullable private LayoutCanvasView.Block mChipDrag;

    /** The container's px in the canvas's, at the pose the canvas was laid out for. */
    @NonNull
    private LiveDragCapture.FrameMap liveMap() {
        return new LiveDragCapture.FrameMap(mFrameRectScale, mRootInCanvasX, mRootInCanvasY);
    }

    /**
     * Copies the frame and, once the copy is in, lets the canvas draw the elements' pictures
     * over a transparent host, makes the real views transparent and runs {@code then}. False
     * where a live drag cannot start (the frame is moving, or not laid out): the caller goes on
     * without one.
     */
    private boolean beginLiveCapture(@NonNull Runnable then) {
        LayoutCanvasView canvas = mCanvas;
        AppearanceEditorFrame frame = mFrame;
        ViewGroup content = mHost.findView(android.R.id.content);
        int[] rect = mFrameRectInContent;
        if (canvas == null || frame == null || content == null || rect == null || mLive != null
            || !poseHolds() || mFrameAnimator != null)
            return false;
        LiveDragCapture capture = new LiveDragCapture(mHost::findView, frame.root());
        mLive = capture;
        int[] at = new int[2];
        content.getLocationInWindow(at);
        android.graphics.Rect inWindow = new android.graphics.Rect(at[0] + rect[0],
            at[1] + rect[1], at[0] + rect[2], at[1] + rect[3]);
        capture.capture(mHost.context(), inWindow, RING_PX, liveMap(), canvas::cornerRadiusOf,
            shots -> {
                if (mLive != capture || !mOpen)
                    return;
                canvas.beginLive(shots, new LayoutCanvasView.LiveHost() {
                    @NonNull @Override
                    public java.util.Map<LayoutCanvasView.Block, android.graphics.RectF>
                        realRects() {
                        return capture.canvasRects(liveMap());
                    }

                    @Override public void onLiveFinished(boolean dropped) {
                        if (mLive == capture)
                            finishLive();
                    }
                });
                setCanvasBackdrop(false);
                capture.hideReal();
                then.run();
            });
        return true;
    }

    /** The canvas's host shows its own ground (the schematic's), or none over the live frame. */
    private void setCanvasBackdrop(boolean shown) {
        FrameLayout host = mLayoutFrame;
        if (host == null || host.getBackground() == null)
            return;
        if (shown)
            setModelProgress(mModelProgress);
        else
            host.getBackground().mutate().setAlpha(0);
    }

    /**
     * The pictures are gone: the real views show, the canvas goes once nothing holds it, and the
     * frame takes the pose it waited for (a drop may have hidden the first element or shown the
     * last), sliding at one scale.
     */
    private void finishLive() {
        LiveDragCapture capture = mLive;
        mLive = null;
        mLiftPending = false;
        if (capture != null) {
            capture.cancel();
            capture.showReal();
        }
        setCanvasBackdrop(true);
        if (mCanvasForDrag && !mForwarding && mChipDrag == null) {
            mCanvasForDrag = false;
            showCanvas(false, false);
        }
        mLifted = false;
        applyPose(true, LIFT_SLIDE_MS);
        // No slide back (the pose holds): the carried canvas is laid out at the pose now.
        if (mFrameAnimator == null)
            setTarget(mTargetScale, mTargetTx, mTargetTy);
        positionTargets();
    }

    /** The editor is going: pictures, copies and lifts dropped at once. */
    private void abandonLive() {
        LayoutCanvasView canvas = mCanvas;
        if (canvas != null && canvas.isLive())
            canvas.endLive();
        LiveDragCapture capture = mLive;
        mLive = null;
        if (capture != null) {
            capture.cancel();
            capture.showReal();
        }
        setCanvasBackdrop(true);
        mLiftPending = false;
        mChipDrag = null;
        mLifted = false;
    }

    /** A hold still being copied was let go: no lift. */
    private void cancelPendingLift() {
        mLiftPending = false;
        LiveDragCapture capture = mLive;
        mLive = null;
        if (capture != null)
            capture.cancel();
        mCanvasForDrag = false;
    }

    /** The canvas's block a rail card stands for; null where the card is not carried through it. */
    @Nullable
    static LayoutCanvasView.Block blockOf(@NonNull HiddenItem item) {
        switch (item) {
            case STATUS_BAR: return LayoutCanvasView.Block.STATUS_BAR;
            case PINNED_APPS: return LayoutCanvasView.Block.APPS_ROW;
            case AZ_INDEX: return LayoutCanvasView.Block.ALPHABETS_ROW;
            case EXTRA_KEYS: return LayoutCanvasView.Block.EXTRA_KEYS;
            // The keyboard comes back raised by showHidden, and the Search field has no slot:
            // both stay cards and come back where they were.
            case KEYBOARD:
            case SEARCH_FIELD:
            default: return null;
        }
    }

    /** A rail card began to be dragged: in the orientation on screen, the frame is copied. */
    private void beginChipDrag(@NonNull HiddenItem item) {
        LayoutCanvasView.Block block = blockOf(item);
        if (block == null || !mOpen || mCanvas == null || mLayoutFrame == null || mCanvasShown
            || mForwarding || mLiftPending)
            return;
        mCanvasForDrag = true;
        mPendingMoved = false;
        if (!beginLiveCapture(() -> liftChip(block))) {
            mCanvasForDrag = false;
        }
    }

    /** The copy is in: the card's element is lifted in the canvas, from the rail. */
    private void liftChip(@NonNull LayoutCanvasView.Block block) {
        LayoutCanvasView canvas = mCanvas;
        if (canvas == null || !mOpen)
            return;
        showCanvas(true, false);
        if (!canvas.beginHiddenDrag(block)) {
            canvas.endLive();
            mCanvasForDrag = false;
            finishLive();
            return;
        }
        mChipDrag = block;
        if (mPendingMoved)
            moveChipDrag(mPendingRawX, mPendingRawY);
    }

    /** The card is at a raw point: the canvas's hover follows; true while it draws the element. */
    private boolean moveChipDrag(float rawX, float rawY) {
        mPendingRawX = rawX;
        mPendingRawY = rawY;
        mPendingMoved = true;
        LayoutCanvasView canvas = mCanvas;
        if (mChipDrag == null || canvas == null)
            return false;
        float[] point = canvasPoint(canvas, rawX, rawY);
        canvas.hoverHiddenDrag(point[0], point[1]);
        return canvas.drawsHeldOverFrame();
    }

    /**
     * The card let go: carried through the canvas, a drop over a slot puts its element there
     * (and anywhere else the pictures go back); still being copied, or never carried, it is
     * the card's own drop as before.
     */
    private void dropChip(@NonNull HiddenItem item, float rawX, float rawY) {
        LayoutCanvasView canvas = mCanvas;
        LayoutCanvasView.Block block = mChipDrag;
        mChipDrag = null;
        if (block == null || canvas == null) {
            if (mLive != null && (canvas == null || !canvas.isLive()))
                cancelPendingLift();
            if (isOverFrame(rawX, rawY))
                showHidden(item, false);
            return;
        }
        float[] point = canvasPoint(canvas, rawX, rawY);
        canvas.dropHiddenDrag(point[0], point[1]);
        if (!canvas.isLive() && mCanvasForDrag) {
            mCanvasForDrag = false;
            showCanvas(false, true);
        }
    }

    /** The card's drag was taken away: the lift ends with it. */
    private void cancelChip() {
        LayoutCanvasView canvas = mCanvas;
        LayoutCanvasView.Block block = mChipDrag;
        mChipDrag = null;
        if (block != null && canvas != null) {
            canvas.endHiddenDrag();
            return;
        }
        if (mLive != null && (canvas == null || !canvas.isLive()))
            cancelPendingLift();
    }

    /** One touch, in the canvas's own coordinates, to the canvas. */
    /** A raw screen point in the canvas's own px, through the host's carry (scale, translation). */
    @NonNull
    private float[] canvasPoint(@NonNull LayoutCanvasView canvas, float rawX, float rawY) {
        FrameLayout host = mLayoutFrame;
        View parent = host == null ? null : (View) host.getParent();
        if (host == null || parent == null || canvas.getParent() != host) {
            int[] at = new int[2];
            canvas.getLocationOnScreen(at);
            return new float[] {rawX - at[0], rawY - at[1]};
        }
        int[] at = new int[2];
        parent.getLocationOnScreen(at);
        float[] point = {rawX - at[0] - host.getLeft(), rawY - at[1] - host.getTop()};
        android.graphics.Matrix inverse = new android.graphics.Matrix();
        if (host.getMatrix().invert(inverse))
            inverse.mapPoints(point);
        point[0] -= canvas.getLeft();
        point[1] -= canvas.getTop();
        return point;
    }

    private void forwardToCanvas(@NonNull MotionEvent event) {
        LayoutCanvasView canvas = mCanvas;
        if (canvas == null) {
            mForwarding = false;
            return;
        }
        float[] point = canvasPoint(canvas, event.getRawX(), event.getRawY());
        MotionEvent local = MotionEvent.obtain(event);
        local.setLocation(point[0], point[1]);
        canvas.dispatchTouchEvent(local);
        local.recycle();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mForwarding = false;
            // Live, the pictures settle first; finishLive puts the canvas away after them.
            if (canvas.isLive())
                return;
            if (mCanvasForDrag) {
                mCanvasForDrag = false;
                showCanvas(false, true);
            }
            if (mLifted) {
                mLifted = false;
                applyPose(true, LIFT_SLIDE_MS);
            }
            positionTargets();
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

    /**
     * Where a card's surface stands in the overlay's space; null when the frame has no such
     * surface (a hidden one is a card in the stack beside the frame, {@link HiddenStackView}).
     * Home's is the whole frame.
     */
    @Nullable
    private int[] targetRect(@Nullable EditorCard card, @NonNull View overlay) {
        return surfaceRect(card, overlay);
    }

    /** Where a surface stands in the overlay, or null when it is not on screen. */
    @Nullable
    private int[] surfaceRect(@Nullable EditorCard card, @NonNull View overlay) {
        View root = mHost.findView(R.id.terminal_root_container);
        if (root == null)
            return null;
        int[] overlayOffset = new int[2];
        if (!AppearanceEditorFrame.offsetIn(overlay, root, overlayOffset))
            return null;
        if (card == null || card == EditorCard.HOME)
            return new int[] {0, 0, overlay.getWidth(), overlay.getHeight()};
        if (card == EditorCard.TERMINAL) {
            int[] frame = mHost.terminalFrameRectInRoot();
            if (frame == null || frame[2] - frame[0] < dp(24) || frame[3] - frame[1] < dp(24))
                return null;
            return new int[] {frame[0] - overlayOffset[0], frame[1] - overlayOffset[1],
                frame[2] - overlayOffset[0], frame[3] - overlayOffset[1]};
        }
        View surface = anchorViewFor(card.slot);
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

    /** Lays every tap target over its surface, dressed, then the ring. */
    private void positionTargets() {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null || !mOpen || overlay.getWidth() <= 0)
            return;
        for (int i = 0; i < GROUP_IDS.length; i++) {
            View group = mHost.findView(GROUP_IDS[i]);
            if (group == null)
                continue;
            EditorCard card = GROUP_CARDS[i];
            if (card != null && !card.standsOn(mHalf)) {
                EditorViews.setVisible(group, false);
                continue;
            }
            int[] rect = targetRect(card, overlay);
            if (rect == null) {
                EditorViews.setVisible(group, false);
                continue;
            }
            placeInOverlay(group, overlay, rect);
            group.setContentDescription(describeGroup(i));
            EditorViews.setVisible(group, true);
        }
        restyleOutlines();
    }

    @NonNull
    private String describeGroup(int index) {
        EditorCard card = cardOfGroup(index);
        if (card == null)
            return getString(R.string.appearance_half_workspace);
        return getString(R.string.appearance_target_description, getString(card.titleRes));
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
     * Every target's quiet outline (1dp on screen, 40% of colorOutline, along the surface's own
     * corner); Home's drawn just inside the frame. Rebuilt only when the
     * frame's scale or a target's state changes, never per frame.
     */
    private void restyleOutlines() {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        if (overlay == null || !mOpen)
            return;
        float scale = mTargetScale > 0f ? mTargetScale : 1f;
        int outline = mHost.themeColor(com.google.android.material.R.attr.colorOutline,
            R.color.termux_primary);
        int quiet = (Math.round(255 * QUIET_ALPHA) << 24) | (outline & 0x00FFFFFF);
        float stroke = Math.max(1f, dp(QUIET_DP) / scale);
        for (int i = 0; i < GROUP_IDS.length; i++) {
            View group = mHost.findView(GROUP_IDS[i]);
            if (group == null || group.getVisibility() != View.VISIBLE)
                continue;
            EditorCard card = cardOfGroup(i);
            if (card == null) {
                if (group.getBackground() != null) group.setBackground(null);
                continue;
            }
            boolean ghost = surfaceRect(card, overlay) == null;
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(0);
            if (ghost) {
                float dash = dp(4) / scale;
                ring.setStroke(Math.round(stroke), quiet, dash, dash);
            } else {
                ring.setStroke(Math.round(stroke), quiet);
            }
            ring.setCornerRadius(card == EditorCard.HOME ? 0f : outlineRadiusPx(card));
            group.setBackground(ring);
        }
        positionOutline();
    }

    /**
     * The selection: one 2dp primary ring round the card's surface, just
     * outside its edge, inside the frame for Home. Nothing else glows.
     */
    private void positionOutline() {
        View overlay = mHost.findView(R.id.surface_tuning_gesture_overlay);
        View outline = mHost.findView(R.id.surface_editor_selection_outline);
        if (overlay == null || outline == null)
            return;
        EditorCard card = mOpen ? mCard : null;
        int[] rect = card == null || card == EditorCard.APPEARANCE ? null
            : targetRect(card, overlay);
        if (rect == null) {
            EditorViews.setVisible(outline, false);
            return;
        }
        float scale = mTargetScale > 0f ? mTargetScale : 1f;
        int stroke = Math.max(1, Math.round(dp(RING_DP) / scale));
        int grow = card == EditorCard.HOME ? -stroke : stroke;
        int[] grown = {rect[0] - grow, rect[1] - grow, rect[2] + grow, rect[3] + grow};
        placeInOverlay(outline, overlay, grown);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0);
        ring.setStroke(stroke, mHost.themeColor(androidx.appcompat.R.attr.colorPrimary,
            R.color.termux_primary));
        ring.setCornerRadius(card == EditorCard.HOME ? 0f
            : Math.max(0f, outlineRadiusPx(card) + grow));
        outline.setBackground(ring);
        EditorViews.setVisible(outline, true);
    }

    /** The radius a surface's outline wears: the surface's own. */
    private float outlineRadiusPx(@NonNull EditorCard card) {
        TermuxAppSharedPreferences prefs = prefs();
        switch (card) {
            case STATUS:
                return prefs == null ? 0f : dpToPx(clamp(prefs.getStatusBarCornerRadius(), 0, 40));
            case DOCK:
                return prefs == null ? 0f
                    : dpToPx(clamp(prefs.getAppLauncherDockCornerRadius(), 0, 40));
            case KEYBOARD:
                return mHost.keyboardSurfaceCornerRadiusPx();
            case TERMINAL:
                return mHost.terminalFrameCornerRadiusPx();
            default:
                return 0f;
        }
    }

    private void registerLayoutListener(@NonNull View host) {
        if (mLayoutListener != null)
            return;
        mLayoutSignature = Long.MIN_VALUE;
        mLayoutListener = () -> {
            if (!mOpen)
                return;
            long signature = computeLayoutSignature();
            if (signature == mLayoutSignature)
                return;
            mLayoutSignature = signature;
            if (mFramed && (frameSizeChanged() || !mFrameCtxValid))
                layoutFrame(false);
            else if (mFramed)
                refitTopReveal();
            if (mFramed && mFrameCtxValid && !poseHolds())
                applyPose(false);
            positionTargets();
        };
        host.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
    }

    /** Whether the opening pass has placed the frame; the layout listener refits only after. */
    private boolean mFramed;
    private int mLastRootWidth;
    private int mLastRootHeight;
    private int mLastContentHeight;

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
        int[] frame = mHost.terminalFrameRectInRoot();
        for (int edge : frame == null ? new int[] {-1} : frame)
            signature = mix(signature, edge);
        signature = mix(signature, mHost.isFloatingDock() ? 1 : 0);
        signature = mix(signature, mHalf.ordinal());
        return mix(signature, scene().signature());
    }

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

    /**
     * Every stored clock style, Minimal's medium weight included. Package-private so a test can
     * hold it against the settings list's own entry values.
     */
    static final String[] CLOCK_STYLES = {
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB};

    /**
     * The faces the picker lists, one row each: every style but Minimal's medium weight, which is
     * Minimal's row with the Weight row under it ({@link #clockWeightRow}).
     */
    static final String[] CLOCK_FACES = {
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB};

    /** Whether {@code style} is Minimal, in either weight. */
    static boolean isMinimalStyle(@Nullable String style) {
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM.equals(style);
    }

    /**
     * The picker's rows for the bar's {@code current} style: the faces, then the Weight row while
     * Minimal is the face, then the position row. Public for the Clock widget's test.
     */
    @NonNull
    public static String[] clockDropdownRows(@Nullable String current) {
        java.util.List<String> rows = new java.util.ArrayList<>(java.util.Arrays.asList(CLOCK_FACES));
        if (isMinimalStyle(current)) rows.add(CLOCK_ROW_WEIGHT);
        rows.add(CLOCK_ROW_POSITION);
        return rows.toArray(new String[0]);
    }

    /** The picker's two rows that are not faces. */
    public static final String CLOCK_ROW_WEIGHT = "@weight";
    public static final String CLOCK_ROW_POSITION = "@position";

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
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM.equals(style))
            return R.string.termux_top_pane_clock_style_minimal_medium;
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
     * The drop-down's rows: the six faces, Minimal's Weight row while Minimal is the face, then one
     * row for the face's place in the pane ({@link #clockDropdownRows}). A real list popup, so the
     * theme's popup style gives it its surface, shape and elevation.
     */
    private final class ClockDropdownAdapter extends BaseAdapter {
        private final String mCurrent;
        private final String[] mRows;

        ClockDropdownAdapter(@NonNull String current) {
            mCurrent = current;
            mRows = clockDropdownRows(current);
        }

        @Override public int getCount() { return mRows.length; }

        @Override public Object getItem(int position) {
            return isFaceRow(position) ? mRows[position] : null;
        }

        @Override public long getItemId(int position) { return position; }

        @Override public int getViewTypeCount() { return 3; }

        @Override public int getItemViewType(int position) {
            if (isFaceRow(position)) return 0;
            return CLOCK_ROW_WEIGHT.equals(mRows[position]) ? 2 : 1;
        }

        @Override public boolean areAllItemsEnabled() { return false; }

        /** The weight and position rows are made of controls of their own; the list takes faces. */
        @Override public boolean isEnabled(int position) { return isFaceRow(position); }

        boolean isFaceRow(int position) {
            return position >= 0 && position < mRows.length
                && !mRows[position].startsWith("@");
        }

        @Override public View getView(int position, @Nullable View convertView,
                                      @NonNull ViewGroup parent) {
            Context context = parent.getContext();
            if (CLOCK_ROW_POSITION.equals(mRows[position]))
                return clockPositionRow(context);
            if (CLOCK_ROW_WEIGHT.equals(mRows[position]))
                return clockWeightRow(context);
            return clockFaceRow(context, mRows[position], mCurrent);
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
        // Beside the preview the sheet is the side pane: the popup takes its share of the pane,
        // not of the window, so it stands over the pane rather than across the preview.
        popup.setWidth(windowWidth * 3 / 4);
        popup.setDropDownGravity(Gravity.END);
        int[] anchorAt = new int[2];
        anchor.getLocationInWindow(anchorAt);
        // The bottom sheet's button is near the foot: the popup opens upward from its top edge.
        // The side pane's stands near the top of a tall pane: it opens downward under it.
        int roomAbove = anchorAt[1] - dp(FRAME_GAP_DP);
        int roomBelow = anchor.getRootView().getHeight() - mNavInsetPx
            - (anchorAt[1] + anchor.getHeight()) - dp(FRAME_GAP_DP);
        int room = Math.max(dp(CLOCK_POPUP_MIN_HEIGHT_DP), Math.max(roomAbove, roomBelow));
        int height = Math.min(dp(clockPopupHeightDp(prefs().getTopPaneClockStyle())), room);
        popup.setHeight(height);
        popup.setVerticalOffset(roomAbove >= roomBelow ? -(anchor.getHeight() + height) : 0);
        popup.setOnDismissListener(() -> mClockDropdown = null);
        ClockDropdownAdapter adapter = new ClockDropdownAdapter(prefs().getTopPaneClockStyle());
        popup.setAdapter(adapter);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            Object face = adapter.getItem(position);
            if (face instanceof String) {
                // Minimal's row keeps the weight it has; the Weight row moves between the two.
                if (!(isMinimalStyle((String) face)
                    && isMinimalStyle(prefs() == null ? null : prefs().getTopPaneClockStyle())))
                    pickClockStyle((String) face);
                popup.dismiss();
            }
        });
        popup.setBackgroundDrawable(EditorM3.surface(anchor,
            com.google.android.material.R.attr.shapeAppearanceCornerMedium,
            com.google.android.material.R.attr.colorSurfaceContainerHigh));
        mClockDropdown = popup;
        popup.show();
    }

    /**
     * The popup's height as it wants to stand: six 48dp face rows and the 56dp position row, and
     * the 56dp weight row only while Minimal is the face ({@link #clockDropdownRows}), so no
     * blank strip is left where it is not shown.
     */
    public static int clockPopupHeightDp(@Nullable String current) {
        return 6 * 48 + (isMinimalStyle(current) ? 56 : 0) + 56;
    }

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
        boolean selected = style.equals(current)
            || (isMinimalStyle(style) && isMinimalStyle(current));

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
        // Minimal's row draws the weight the bar wears.
        applyClockPreview(preview, selected && isMinimalStyle(style) ? current : style);
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

    /**
     * Minimal's weight, Thin or Medium, as the theme's segmented buttons under the faces while
     * Minimal is the face. Like the position row, the popup stays open for a second look.
     */
    @NonNull
    private View clockWeightRow(@NonNull Context context) {
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(context);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.setContentDescription(getString(R.string.termux_surface_tuning_clock_weight));
        String current = prefs() == null ? null : prefs().getTopPaneClockStyle();
        String[] styles = {TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL,
            TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM};
        int[] labels = {R.string.termux_surface_tuning_clock_weight_thin,
            R.string.termux_surface_tuning_clock_weight_medium};
        final java.util.Map<Integer, String> styleOf = new java.util.HashMap<>();
        int currentId = View.NO_ID;
        for (int i = 0; i < styles.length; i++) {
            MaterialButton button = (MaterialButton) android.view.LayoutInflater.from(context)
                .inflate(R.layout.segment_button, group, false);
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setMaxLines(1);
            button.setContentDescription(getString(
                R.string.termux_surface_tuning_clock_weight_description, getString(labels[i])));
            button.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            group.addView(button);
            styleOf.put(button.getId(), styles[i]);
            if (styles[i].equals(current))
                currentId = button.getId();
        }
        if (currentId != View.NO_ID)
            group.check(currentId);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            String style = styleOf.get(checkedId);
            if (isChecked && style != null)
                pickClockStyle(style);
        });
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(4), dp(16), dp(4));
        TextView name = new TextView(context);
        name.setText(R.string.termux_surface_tuning_clock_weight);
        applyTextAppearance(name, com.google.android.material.R.attr.textAppearanceLabelLarge);
        name.setTextColor(EditorM3.color(row, com.google.android.material.R.attr.colorOnSurface));
        name.setLayoutParams(new LinearLayout.LayoutParams(dp(84),
            ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(name);
        row.addView(group, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void pickClockStyle(@NonNull String style) {
        if (prefs() == null || style.equals(prefs().getTopPaneClockStyle()))
            return;
        prefs().setTopPaneClockStyle(style);
        mHost.refreshTerminalWindowBar();
        syncDirty();
    }

    // ------------------------------------------------------------------------- unsaved and exit

    /**
     * One dirty state for the whole editor: the look on either half, an app-wide choice, minimal
     * mode or the arrangement moved since open.
     */
    private boolean isDirty() {
        TermuxAppSharedPreferences prefs = prefs();
        boolean look = prefs != null && mEntrySignature != null
            && !mEntrySignature.equals(AppearanceSnapshot.signatureOf(prefs));
        boolean settings = mEntrySettings != null && mSettingsNow != null
            && !mEntrySettings.signature().equals(mSettingsNow);
        boolean minimal = mHost.isMinimalMode() != mEntryMinimal;
        boolean theme = ThemePreview.pending() != null;
        LayoutEditorController layout = mHost.layoutEditor();
        return look || settings || minimal || theme || (layout != null && layout.isDirty());
    }

    /** Undo: everything — both halves' look, the choices, the arrangement — back to the open. */
    private void revertToEntry() {
        dropLookDrag();
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null && layout.isDirty())
            layout.revert();
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null || mEntry == null)
            return;
        mEntry.restore(prefs);
        if (mEntryCustom != null)
            prefs.setSurfaceCustomPreset(mEntryCustom);
        if (mEntrySettings != null) {
            List<String> written = mEntrySettings.restore(settingsStore());
            refreshSettingsSignature();
            if (!written.isEmpty())
                mHost.applyHomeSettings();
        }
        dropThemePreview();
        if (mHost.isMinimalMode() != mEntryMinimal)
            mHost.setMinimalMode(mEntryMinimal);
        mSessionCustom.clear();
        mHeldCustom.clear();
        if (!mClearedWidgetGlass.isEmpty()) {
            mHost.restoreWidgetGlassChoices(new java.util.LinkedHashMap<>(mClearedWidgetGlass));
            mClearedWidgetGlass.clear();
        }
        forgetGlassEdgePositions();
        syncAfterBulkWrite();
    }

    /**
     * Saves the look. A half left at a Custom the user made stores it as the saved Custom; with
     * both halves on Looks the saved Custom goes, and the next Custom starts from those Looks. A
     * visit that changed no look leaves it alone.
     */
    private void commitLook() {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        // A visit that changed no look keeps the saved Custom as it found it.
        if (mEntrySignature != null
            && mEntrySignature.equals(AppearanceSnapshot.signatureOf(prefs)))
            return;
        boolean custom = AppearanceLooks.isCustomStop(matchingStop(WallHalf.HOME))
            || AppearanceLooks.isCustomStop(matchingStop(WallHalf.WORKSPACE));
        if (!custom)
            prefs.setSurfaceCustomPreset(null);
        else if (mSessionCustom.isTouched(prefs))
            SurfacePresets.saveCustom(prefs);
    }

    /** Done: the look is saved, and the surface takes the editor off screen. */
    private void commitAndExit() {
        if (mSliderDragActive)
            endDrag();
        commitSession();
        Runnable onDone = mOnDone;
        if (onDone != null)
            onDone.run();
    }

    /**
     * Leaving the editor. Nothing to lose: {@code proceed} runs at once. Otherwise it asks, once
     * for everything — Keep editing / Discard / Save. Back with an element in the air cancels the
     * lift and nothing else.
     */
    @Override
    public void requestLeave(@NonNull Runnable proceed) {
        if (!mOpen) {
            proceed.run();
            return;
        }
        LayoutEditorController layoutEditor = mHost.layoutEditor();
        if (layoutEditor != null && layoutEditor.cancelGesture())
            return;
        if (!isDirty()) {
            proceed.run();
            return;
        }
        showUnsavedDialog(() -> {
            commitSession();
            proceed.run();
        }, proceed);
    }

    /** Leaving with the picker's choice pending as well: the one question, always. */
    @Override
    public void confirmLeave(@NonNull Runnable onSave, @NonNull Runnable onDiscard) {
        LayoutEditorController layoutEditor = mHost.layoutEditor();
        if (mOpen && layoutEditor != null && layoutEditor.cancelGesture())
            return;
        showUnsavedDialog(() -> {
            if (mOpen)
                commitSession();
            onSave.run();
        }, onDiscard);
    }

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

    /** The phone turned: the canvas's default and the next write turn with it. */
    public void onPlaceOrientationChanged() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (mOpen && layout != null)
            layout.onPlaceOrientationChanged();
    }

    /** Takes the editor off screen: the card, the bar, the canvas, then the frame back to size. */
    private void dismiss(@Nullable Runnable onHidden) {
        if (!mOpen) {
            if (onHidden != null)
                onHidden.run();
            return;
        }
        dismissClockDropdown();
        if (mSliderDragActive)
            endDrag();
        mOpen = false;
        mHost.retainGlassCapture(false);
        mFramed = false;
        mPresentToken++;
        mSliderDragActive = false;
        mForwarding = false;
        mEntry = null;
        mEntryCustom = null;
        mEntrySignature = null;
        mEntrySettings = null;
        mSettingsNow = null;
        mCard = null;
        mLastDetent = -1;
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null) {
            layout.end();
            layout.setOnChangedListener(null);
            layout.setOnPlaceShownListener(null);
            layout.setOnSelectionListener(null);
            layout.setOnTrayOfferListener(null);
        }
        detachStack();
        cancelFrameAnimator();
        mFrameCtxValid = false;
        mTargetScale = -1f;
        mFollowingSheet = false;
        mEntering = false;
        abandonLive();
        mCanvasForDrag = false;
        showCanvas(false, false);
        setOverlayVisible(false);
        unregisterLayoutListener();
        View outline = mHost.findView(R.id.surface_editor_selection_outline);
        if (outline != null)
            outline.setVisibility(View.GONE);
        clearOutlines();
        EditorCardSheet sheet = mSheet;
        if (sheet != null)
            sheet.hide(true, sheet::detach);
        EditorBar bar = mBar;
        if (bar != null) {
            bar.setShown(false);
            bar.detach();
        }
        FrameLayout canvasHost = mLayoutFrame;
        if (canvasHost != null && canvasHost.getParent() instanceof ViewGroup)
            ((ViewGroup) canvasHost.getParent()).removeView(canvasHost);
        AppearanceEditorFrame frame = mFrame;
        if (frame == null) {
            if (onHidden != null)
                onHidden.run();
            return;
        }
        frame.hide(true, onHidden);
    }

    /** The targets' outlines taken off, so the launcher wears none after the editor. */
    private void clearOutlines() {
        for (int id : GROUP_IDS) {
            View group = mHost.findView(id);
            if (group != null && group.getBackground() != null)
                group.setBackground(null);
        }
    }

    /**
     * Ends the session: the editor goes down first when it is up, then the editor's wallpaper,
     * and the chrome is dressed as a launch would.
     */
    @Override
    public void endSession() {
        if (!mSession)
            return;
        if (mOpen) {
            dismiss(this::finishSession);
            lowerRaisedKeyboardAtEnd();
            return;
        }
        finishSession();
    }

    /** Set while {@link #lowerRaisedKeyboardAtEnd} asks the host: the session's end is under way. */
    private boolean mEndingSession;

    /** Whether the session is ending: the host keeps a keyboard the place wants at the end. */
    public boolean isEndingSession() {
        return mEndingSession;
    }

    private void lowerRaisedKeyboardAtEnd() {
        if (!mRaisedKeyboard || !mSession)
            return;
        mEndingSession = true;
        try {
            mHost.hideInAppKeyboardForEditor();
        } finally {
            mEndingSession = false;
        }
        mRaisedKeyboard = false;
    }

    private void finishSession() {
        if (mOpen || !mSession)
            return;
        // Every write here is kept unless Discard or Undo put it back, and so is the theme.
        commitThemePreview();
        mSession = false;
        mCarriedNext = null;
        mWallpaperToken++;
        mWallpaperSettled = false;
        mWallpaperWaiter = null;
        mEditorWallpaper = null;
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
        mHost.editorSessionEnded();
    }

    /** The activity stopped with the surface up. */
    @Override
    public void onStopWhileOpen() {
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
    /** A Text size drag moved the terminal's size; its panes take it on the release. */
    private boolean mDragTouchedTerminalText;
    /** A Corners or Margin drag moved the shape; the release resizes the terminal once. */
    private boolean mDragTouchedGeometry;
    /** An Icon size drag wrote through the layout session; the release re-lays the place once. */
    private boolean mDockSizeHeld;
    private boolean mDirtyDeferred;
    /** The host's glass passes are a drag's light ticks ({@link Host#setGlassDragPreview}). */
    private boolean mGlassDragPreview;
    /** A light tick ran this drag; the release owes the full pass it left out. */
    private boolean mDragRanLightPass;

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
        setGlassDragPreview(false);
        if (mDragRanLightPass) {
            // The full pass the ticks left out, once, with the values the drag settled on.
            mDragRanLightPass = false;
            requestPreview(SurfaceEditorProperties.PREVIEW_SURFACES);
        }
        if (mHeldVibrancy != NO_HELD_VIBRANCY)
            writeVibrancy(mHeldVibrancy);
        releaseFrameBakes();
        settleLookDrag();
        if (mDockSizeHeld) {
            mDockSizeHeld = false;
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout != null)
                layout.relayHeldWrites();
        }
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
        if (mDragTouchedTerminalText) {
            mDragTouchedTerminalText = false;
            requestPreview(SurfaceEditorProperties.PREVIEW_TEXT_TERMINAL);
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
        if (defersHeavyPasses()
                && (scopes & SurfaceEditorProperties.PREVIEW_TEXT_TERMINAL) != 0) {
            // The panes' resize waits for the release, like the geometry's.
            mDragTouchedTerminalText = true;
            scopes &= ~SurfaceEditorProperties.PREVIEW_TEXT_TERMINAL;
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
        // A Frost, Opacity or Tint tick (the drags that hold the frame bakes) moves no geometry,
        // so it can take the light pass; any shape in the same frame takes the full one.
        boolean light = mFrameBakesHeld && defersHeavyPasses()
            && (scopes & (SurfaceEditorProperties.PREVIEW_GEOMETRY
                | SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT)) == 0;
        setGlassDragPreview(light);
        if (light) mDragRanLightPass = true;
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
        if ((scopes & (SurfaceEditorProperties.PREVIEW_TEXT
                | SurfaceEditorProperties.PREVIEW_TEXT_TERMINAL)) != 0)
            mHost.applyTextSizePreview(
                (scopes & SurfaceEditorProperties.PREVIEW_TEXT_TERMINAL) != 0);
        mHost.applyGlassPreview();
        if (mOpen && !mSliderDragActive)
            positionOutline();
        syncDirty();
    }

    private void setGlassDragPreview(boolean light) {
        if (mGlassDragPreview == light)
            return;
        mGlassDragPreview = light;
        mHost.setGlassDragPreview(light);
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
        mHost.applyTextSizePreview(true);
        applyStructuralPreview();
        restateCard();
        // An Undo can move Style or Corners under a showing canvas.
        syncLayoutCanvas();
        positionOutline();
    }
}
