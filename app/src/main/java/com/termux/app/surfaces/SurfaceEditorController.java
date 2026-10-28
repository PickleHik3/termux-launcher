package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.DashPathEffect;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.chrome.GlassMotion;
import com.termux.app.dock.DockStyle;
import com.termux.app.dock.PeekGlassGeometry;
import com.termux.app.dock.PeekGlassPreferences;
import com.termux.app.fragments.settings.termux.AppearanceSettingsStore;
import com.termux.app.fragments.settings.termux.HomePreferencesFragment;
import com.termux.app.fragments.settings.termux.KeyboardColorSchemeFragment;
import com.termux.app.fragments.settings.termux.KeyboardLayoutsFragment;
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
import com.termux.app.surfaces.AppearanceLooks.Control;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.terminal.Motion;
import com.termux.app.terminal.PaneRetroEffect;
import com.termux.app.surfaces.rows.EditorGlyphs;
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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Appearance editor (appearance-and-settings-rework §2, §3, §7, §8): the launcher itself,
 * live, scaled into a frame, on one rule: <b>tap the thing</b>. A tap on a surface in the frame
 * (Home, the status bar, the terminal, the dock, the keyboard) raises its card; the editor bar's
 * palette raises the Appearance card for what has no surface. A plain horizontal swipe slides
 * the frame between Home and the Workspace, and a card writes the half the frame shows. Where a
 * surface sits is its card's Position row (editor-redesign §1: the shrunken Layout mode, its
 * canvas and its drags, is retired); the editor edits the orientation the phone is in, and turning
 * the phone turns the editor with it.
 *
 * <p><b>The frame.</b> {@code terminal_root_container} scaled about a top-centre pivot
 * ({@link AppearanceEditorFrame}), at rest between the top line and the half line over the editor
 * bar ({@link EditorBar}); every surface wears a quiet outline, and a hidden one comes back from the
 * Elements list on its screen's card. With a card up the frame shrinks and moves so the selected surface, which alone wears the
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
        /** Whether the page overview (Edit pages) is up over Home. */
        default boolean isPageOverviewOpen() { return false; }
        /** Opens the page overview, the way Home's actions sheet does. */
        default void openPageOverview() {}
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
        /** The layout session: where each bar sits and the sizes the cards write. */
        @Nullable LayoutEditorController layoutEditor();
        /** The wallpaper for the editor to paint inside the frame, or null. Off the main thread. */
        @Nullable Drawable readEditorWallpaper();
        /** The dim the window's root paints over the system wallpaper. */
        int editorWallpaperDimColor();
        /**
         * The system's description of the wallpaper the screen shows, as one colour
         * ({@link com.termux.app.chrome.WallpaperInk#hintColor}); transparent for none. What the
         * page label stands on when the picture itself cannot be read (no storage permission).
         */
        default int editorWallpaperHintColor() {
            return 0;
        }
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

    /** Use as, read fresh: Terminal only has no Home half, no Home card and no Match rows. */
    @NonNull
    private com.termux.app.wall.UseAs useAs() {
        TermuxAppSharedPreferences prefs = prefs();
        return com.termux.app.wall.UseAs.read(prefs == null ? null : prefs.getUseAs());
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

    /** Whether the editor is open: its chrome then stands beside the page. */
    public boolean isOpen() {
        return mOpen;
    }
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
    /**
     * Each Match this visit that can still be taken back, by the screen it copied onto (the
     * Match rows' Undo). Dropped when the editor closes or its Undo puts everything back.
     */
    @NonNull private final java.util.EnumMap<WallHalf, ScreenMatch.Undo> mMatchUndos =
        new java.util.EnumMap<>(WallHalf.class);
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
        // Terminal only has no Home: a door to Home's card opens the editor at rest, and says so.
        com.termux.app.wall.UseAs useAs = useAs();
        if (!useAs.reaches(card)) {
            android.util.Log.i(EditorDoor.LOG_TAG, useAs.doorLine(card.key + "-card"));
            card = null;
        }
        // A card that stands on one screen opens on it: Home's card over a Workspace left on
        // screen would be refused by cardRefusal, leaving the editor with no card.
        place = useAs.editorPlace(EditorDoor.placeFor(card, place, mHost.pageOnScreen()));
        if (mOpen) {
            if (place != null)
                switchHalf(SeamDress.halfOf(place), true);
            if (card != null) {
                raiseOrReport(card);
                raiseFromPeek();
            } else {
                collapseCard(true);
            }
            if (mCard == null)
                raiseScreenCard();
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
        PaneWallPage page = useAs.editorPlace(place != null ? place : mHost.pageOnScreen());
        mHalf = page == null ? WallHalf.WORKSPACE : SeamDress.halfOf(page);
        mCard = null;
        mRaisedKeyboard = false;

        if (mFrame == null || mFrame.root() != rootContainer)
            mFrame = new AppearanceEditorFrame(rootContainer);
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null) {
            layout.setOnChangedListener(this::onArrangementChanged);
            layout.setOnPlaceShownListener(this::restateCard);
            layout.begin(place, carried == null ? null : carried.arrangement);
        }
        if (mBar == null) {
            mBar = new EditorBar(mHost.context());
            mBar.setListener(new BarListener());
        }
        mBar.attach(content);
        // A picture read before the bar existed is applied now; a new bar starts at the theme's.
        mLabelInk = 0;
        if (mLabelPicture == null)
            mBar.resetHalfNameInk();
        applyLabelInk();
        TermuxAppSharedPreferences hintPrefs = prefs();
        mBar.setThemeHintWanted(hintPrefs != null && !hintPrefs.isAppearanceThemeHintSeen());
        mBar.setShown(true);
        mBar.setBarShown(true, false);
        mBarUp = true;
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
        // With no card named (or one that cannot stand here) the screen's card stands at peek:
        // the sheet is never down while the editor is open.
        if (card != null)
            raiseOrReport(card);
        if (mCard == null)
            raiseScreenCard();
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

    // ---------------------------------------------------------------------- the halves and the bar

    /**
     * Slides the frame to {@code half} (the real wall slides inside the scaled container), and a
     * card up stays when its object stands there too; otherwise it goes down.
     */
    private void switchHalf(@NonNull WallHalf half, boolean animate) {
        switchHalf(half, animate, false);
    }

    /** {@code swiped}: the user swiped the preview, so a screen's card follows the page. */
    private void switchHalf(@NonNull WallHalf half, boolean animate, boolean swiped) {
        if (!mOpen || half == mHalf)
            return;
        if (half == WallHalf.HOME && !useAs().showsHomePage()) {
            android.util.Log.i(EditorDoor.LOG_TAG, useAs().doorLine("editor-home-half"));
            return;
        }
        mHalf = half;
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null)
            layout.showHome(half == WallHalf.HOME, animate);
        if (mBar != null)
            mBar.setHalf(half);
        EditorCard followed = EditorDoor.cardAfterSwipe(mCard, half, useAs().showsHomePage());
        if (followed != null && swiped) {
            // A screen's card follows the page: same detent, rows rebuilt and scrolled to the top.
            EditorCardSheet sheet = mSheet;
            int detent = sheet != null ? sheet.detent() : -1;
            EditorDoor.EDITOR_PAGE_SWIPE.log(followed);
            openCard(followed, false);
            if (sheet != null && detent >= 0 && mCard == followed)
                sheet.setDetent(detent, false);
        } else if (mCard != null && !mCard.standsOn(half)) {
            // The card's object is not on this screen: the screen's own card takes its place, at
            // the same detent.
            EditorCard screen = EditorCard.forScreen(half);
            EditorDoor.EDITOR_PAGE_SWIPE.log(screen);
            openCard(screen, animate);
        } else
            restateCard();
        positionTargets();
    }

    /**
     * The orientation chip names the orientation being edited, which is the phone's: the other
     * one is edited by turning the phone (editor-redesign §1), and a tap says so.
     */
    private void hintOtherOrientation() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (!mOpen || layout == null || layout.shownOrientation() == null)
            return;
        boolean landscape = layout.shownOrientation() == PlaceOrientation.LANDSCAPE;
        // In the theme hint's bubble over the page label, not a toast across it.
        if (mBar != null)
            mBar.showNote(landscape ? R.string.appearance_bar_turn_for_portrait
                : R.string.appearance_bar_turn_for_landscape);
    }

    /** The chip follows the orientation the session edits, which turns with the phone. */
    private void restateOrientation() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (mBar != null && layout != null && layout.shownOrientation() != null)
            mBar.setOrientation(layout.shownOrientation() == PlaceOrientation.LANDSCAPE);
    }

    private final class BarListener implements EditorBar.Listener {
        @Override public void onOrientation() {
            hintOtherOrientation();
        }

        @Override public void onUndo() {
            undo();
        }

        @Override public void onDone() {
            done();
        }

        @Override public void onPalette() {
            EditorCard card = EditorDoor.EDITOR_PALETTE.route(null, null);
            if (card == null)
                return;
            openCard(card, true);
            // The palette stands on the card's header at peek: its card rises to be seen.
            raiseFromPeek();
        }

        @Override public void onPageLabel() {
            if (!mOpen)
                return;
            EditorCard card = EditorDoor.EDITOR_PAGE_LABEL.route(mHalf == WallHalf.HOME
                ? com.termux.app.wall.PaneWallPage.WIDGETS
                : com.termux.app.wall.PaneWallPage.TERMINAL, null);
            if (card != null) openCard(card, true);
        }

        @Override public void onThemeHintSeen() {
            TermuxAppSharedPreferences prefs = prefs();
            if (prefs != null) prefs.setAppearanceThemeHintSeen(true);
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
        if (!useAs().reaches(card))
            return "Use as Terminal only has no Home card";
        if (!card.standsOn(mHalf))
            return "it does not stand on the " + mHalf + " half";
        return null;
    }

    /**
     * Raises {@code card}, on the half the frame shows: a sheet that is down rises to its half
     * detent, and a card already up gives way to it at the detent it stands at.
     */
    private void openCard(@NonNull EditorCard card, boolean animate) {
        openCard(card, animate, EditorCardSheet.DETENT_HALF);
    }

    /** {@link #openCard(EditorCard, boolean)}, a sheet that is down rising to {@code raiseTo}. */
    private void openCard(@NonNull EditorCard card, boolean animate, int raiseTo) {
        EditorCardSheet sheet = mSheet;
        if (sheet == null || cardRefusal(card) != null)
            return;
        mCard = card;
        if (card == EditorCard.APPEARANCE)
            ensureIconsContent();
        sheet.show(card, EditorRows.rows(card, offers()), resetLabel(card), animate, raiseTo);
        restateBar(animate);
        layoutSheet();
        applyPose(animate);
        positionOutline();
    }

    /** The card of the screen the frame shows, at peek: the editor's resting state. */
    private void raiseScreenCard() {
        openCard(EditorCard.forScreen(mHalf), true, EditorCardSheet.DETENT_PEEK);
    }

    /**
     * Takes the card to peek, the header alone over the navigation inset, with the editor bar on
     * it. The card is never put down while the editor is open.
     */
    private void collapseCard(boolean animate) {
        EditorCardSheet sheet = mSheet;
        if (!sheetUp() || sheet == null)
            return;
        sheet.setDetent(EditorCardSheet.DETENT_PEEK, animate);
        restateBar(animate);
        applyPose(animate);
    }

    /** A card at peek rises to its half detent: a door that names a card means to show it. */
    private void raiseFromPeek() {
        EditorCardSheet sheet = mSheet;
        if (sheetUp() && sheet != null && sheet.detent() == EditorCardSheet.DETENT_PEEK)
            sheet.setDetent(EditorCardSheet.DETENT_HALF, true);
    }

    /** Whether a card is up, at any detent. */
    private boolean sheetUp() {
        EditorCardSheet sheet = mSheet;
        return mCard != null && sheet != null && sheet.isShown();
    }

    /** Whether the card stands at peek with the editor bar on its header. */
    private boolean barOverSheet() {
        EditorCardSheet sheet = mSheet;
        return sheetUp() && sheet != null && sheet.detent() == EditorCardSheet.DETENT_PEEK;
    }

    /** Whether the editor bar is up: with no card, or on the card's header at peek. */
    private boolean mBarUp;

    /** The editor bar shows at peek and hides above it; changed only when that changes. */
    private void restateBar(boolean animate) {
        EditorBar bar = mBar;
        if (bar == null || !mOpen)
            return;
        boolean up = !sheetUp() || barOverSheet();
        if (up == mBarUp)
            return;
        mBarUp = up;
        bar.setBarShown(up, animate);
    }

    @NonNull
    private EditorRows.Offers offers() {
        return new EditorRows.Offers(mHost.isGlassRefractionActive(), PaneRetroEffect.available(),
            useAs().showsHomePage(), mHost.isPageOverviewOpen());
    }

    /** The page overview opened or closed: the card's Edit pages chip shows or hides with it. */
    public void onPageOverviewChanged() {
        if (mBar != null)
            mBar.setUnderOverview(mHost.isPageOverviewOpen());
        restateCard();
    }

    private boolean mFoldedByOverview;

    /**
     * The page overview centred a card over the open editor: the editor shows that page. Home and
     * terminal cards rebind the half (label, preview and the screen's card); a display card keeps
     * the card and folds the sheet to its header. Nothing is written: looking is not an edit.
     */
    public void onOverviewCard(@NonNull com.termux.app.wall.PageOverviewPolicy.Kind kind) {
        EditorCardSheet sheet = mSheet;
        if (!mOpen || sheet == null)
            return;
        OverviewFollow follow = OverviewFollow.of(kind);
        if (follow == OverviewFollow.FOLD) {
            if (sheet.isShown() && sheet.detent() != EditorCardSheet.DETENT_PEEK) {
                mFoldedByOverview = true;
                collapseCard(false);
            }
            return;
        }
        WallHalf half = follow == OverviewFollow.HOME ? WallHalf.HOME : WallHalf.WORKSPACE;
        if (half == WallHalf.HOME && !useAs().showsHomePage())
            return;
        boolean unfold = mFoldedByOverview;
        mFoldedByOverview = false;
        if (half != mHalf) {
            mHalf = half;
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout != null)
                layout.showHome(half == WallHalf.HOME, false);
            if (mBar != null)
                mBar.setHalf(half);
        }
        if (mCard == null || mCard == EditorCard.HOME || mCard == EditorCard.WORKSPACE
                || !mCard.standsOn(half)) {
            int detent = sheet.isShown() && !unfold ? sheet.detent() : EditorCardSheet.DETENT_HALF;
            openCard(follow.card, false, detent);
            if (sheet.isShown() && sheet.detent() != detent)
                sheet.setDetent(detent, false);
        } else
            restateCard();
        if (unfold && sheet.detent() == EditorCardSheet.DETENT_PEEK)
            sheet.setDetent(EditorCardSheet.DETENT_HALF, false);
        positionTargets();
    }

    @NonNull
    private String resetLabel(@NonNull EditorCard card) {
        switch (card) {
            case HOME: return getString(R.string.appearance_reset_home);
            case WORKSPACE: return getString(R.string.appearance_reset_workspace);
            case STATUS: return getString(R.string.appearance_reset_status);
            case TERMINAL: return getString(R.string.appearance_reset_terminal);
            case DOCK: return getString(R.string.appearance_reset_dock);
            case KEYBOARD: return getString(R.string.appearance_reset_keyboard);
            case APPEARANCE:
            default: return getString(R.string.appearance_reset_look);
        }
    }

    /** The card's rows read again (the half may have moved). */
    private void restateCard() {
        EditorCardSheet sheet = mSheet;
        EditorCard card = mCard;
        if (sheet == null || card == null || !sheet.isShown())
            return;
        sheet.show(card, EditorRows.rows(card, offers()), resetLabel(card), false);
        sheet.restate();
    }

    private final class SheetListener implements EditorCardSheet.Listener {
        @Override public void onReset() {
            resetCard();
        }

        @Override public void onSheetMoved(int detent, float topPx) {
            onCardMoved(detent, topPx);
        }

        @Override public void onSheetSettled(int detent, float topPx) {
            onCardSettled();
        }
    }

    /**
     * The card header's reset, with no question asked: the object's glass back on the half's Look
     * (a screen's own glass back as it shipped), and every other value the card's rows show back
     * as it ships ({@link EditorCardReset}); where bars stand stays. Undo, not Reset, returns to
     * the state the editor opened on.
     */
    private void resetCard() {
        TermuxAppSharedPreferences prefs = prefs();
        EditorCard card = mCard;
        if (prefs == null || card == null)
            return;
        EditorCardReset.resetStore(prefs, card, mHalf);
        mScreenFrostPosition.clear();
        mLinkAnchor = null;
        LayoutEditorController layout = mHost.layoutEditor();
        if (card == EditorCard.HOME)
            resetHome();
        if (card == EditorCard.DOCK) {
            // The dock's height, type and the Search field and Drawer icon live in the layout
            // session, so its Undo covers them.
            float scale = com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                .TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT;
            if (layout != null) {
                layout.setDockHeightScale(scale);
                layout.setDockStyle(DockStyle.GLASS_TRAY);
                layout.setSearchField(false);
                layout.setDrawerTile(false);
                layout.setWallStripHeightDp(
                    com.termux.app.dock.WallStripRowPolicy.DEFAULT_HEIGHT_DP);
            } else {
                prefs.setAppLauncherBarHeightScale(scale);
            }
            writeStyle(false);
            resetDockOptions();
        }
        if (card == EditorCard.KEYBOARD && layout != null) {
            layout.setKeyboardForm(PlaceLayout.KeyboardForm.DOCKED);
            layout.setKeyboardHeightScale(prefs.getDefaultInAppKeyboardHeightScale(), false);
            layout.setKeyboardChinDp(com.termux.shared.termux.settings.preferences
                .TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING, false);
        }
        syncAfterBulkWrite();
    }

    /** The Dock card's options and the apps row's Most used switch back as they ship. */
    private void resetDockOptions() {
        Context context = mHost.context();
        for (String key : new String[] {"app_launcher_notification_dots",
            "app_launcher_quick_reply_swipe", "app_launcher_bw_icons",
            "app_launcher_most_used_page"}) {
            boolean shipped = EditorSettingsSnapshot.defaultOf(key);
            if (AppearanceSettingsStore.getBoolean(context, key, shipped) != shipped)
                AppearanceSettingsStore.putBoolean(context, key, shipped);
        }
        settingsWritten();
    }

    /** Home's rows and widget grid back to what they ship with (its glass: EditorCardReset). */
    private void resetHome() {
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
        // The widget grid lives in the layout session, so its Undo covers it.
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout != null)
            layout.setWidgetGrid(com.termux.shared.termux.settings.preferences
                    .TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                    .TERMUX_APP.DEFAULT_APP_LAUNCHER_WIDGET_GRID_ROWS);
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
     * The pose the frame is heading for: over the card at the detent it is settling on, and at
     * peek over the editor bar on the card's header; with no card, over the editor bar.
     */
    @NonNull
    private EditorFramePose.Pose targetPose() {
        EditorFramePose.Window window = mPoseWindow;
        EditorCardSheet sheet = mSheet;
        if (window == null)
            return new EditorFramePose.Pose(1f, 0f, 0f);
        if (!sheetUp() || sheet == null)
            return EditorFramePose.pose(window, Float.MAX_VALUE);
        return EditorFramePose.pose(window,
            poseTop(window, window.heightPx - sheet.heightAt(sheet.detent())));
    }

    /** The top the poses read for a card whose top edge is at {@code sheetTopPx}. */
    private float poseTop(@NonNull EditorFramePose.Window window, float sheetTopPx) {
        return barOverSheet() ? EditorFramePose.topOverBar(window, sheetTopPx) : sheetTopPx;
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
        restyleOutlines();
    }

    /** Moves the frame to its new pose on the card's clock; the outlines follow at the end. */
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
        animator.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            float nowScale = fromScale + (scale - fromScale) * f;
            float nowTx = fromTx + (tx - fromTx) * f;
            float nowTy = fromTy + (ty - fromTy) * f;
            frame.setPose(nowScale, nowTx, nowTy);
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

    /** The scaled container's rect in the content view, as {@code {left, top, right, bottom}}. */
    @Nullable private int[] mFrameRectInContent;

    /** Works out {@link #mFrameRectInContent} for a pose. */
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
    }

    /**
     * The bar on the card's header at peek (over the navigation inset with no card), and the half
     * line over the bar, or over the card while it stands higher with the bar hidden; the frame
     * above it either way (the poses leave it the room). By translation, every frame of the
     * card's motion included.
     */
    private void placeBar() {
        EditorBar bar = mBar;
        EditorCardSheet sheet = mSheet;
        if (bar == null || !mFrameCtxValid)
            return;
        int gap = dp(EditorFramePose.GAP_DP);
        boolean up = sheetUp() && sheet != null;
        float under = up ? mWindowHeightPx - sheet.sheetTopPx() : mNavInsetPx;
        float barLift = under + dp(EditorBar.FOOT_GAP_DP);
        float halfLift = !up || barOverSheet() ? barLift + dp(EditorBar.BAR_DP) + gap
            : under + gap;
        bar.place(barLift, halfLift);
        applyLabelInk();
    }

    // ---------------------------------------------------------------------- the Elements list

    /** The Elements list on the screen card that is up, or null. */
    @Nullable private com.termux.app.surfaces.cards.ElementsList mElements;

    /** The Elements list for {@code screen}'s card, as the sheet builds it. */
    @NonNull
    private View elementsView(@NonNull WallHalf screen) {
        com.termux.app.surfaces.cards.ElementsList list =
            com.termux.app.surfaces.cards.ElementsList.build(mHost.context(), new ElementsHost(),
                com.termux.app.surfaces.cards.ElementRows.rows(screen), true);
        mElements = list;
        return list.root;
    }

    /**
     * Shows or puts away {@code element}, or the keyboard for null: what the card headers' eyes
     * write, and the Elements list's for the status bar and the keyboard. Through the layout
     * session, so Undo and Discard cover it.
     */
    private void writeShown(@Nullable Element element, boolean shown) {
        LayoutEditorController layout = mHost.layoutEditor();
        if (layout == null)
            return;
        if (element == null) {
            layout.setKeyboardShown(shown);
            if (shown && !mHost.isInAppKeyboardShown()) {
                mRaisedKeyboard = mHost.showInAppKeyboardForEditor() || mRaisedKeyboard;
            } else if (!shown && mHost.isInAppKeyboardShown()) {
                // The frame stops drawing it: off reads off, and its row in the Elements list
                // greys out.
                mHost.hideInAppKeyboardForEditor();
                mRaisedKeyboard = false;
            }
        } else {
            layout.setShown(element, shown);
        }
        positionTargets();
    }

    /**
     * What the Elements list reads and writes: the same values as each element's own card (the
     * header's eye, the Bars list's eye, the Dock card's Options tiles), so the two agree.
     */
    private final class ElementsHost implements com.termux.app.surfaces.cards.ElementsList.Host {
        @NonNull private final DockHost mDock = new DockHost();

        @Override public boolean offers(
                @NonNull com.termux.app.surfaces.cards.ElementRows.Item item) {
            LayoutEditorController layout = mHost.layoutEditor();
            if (item.element != null)
                return layout != null && layout.offers(item.element);
            if (item.ridesTheDock())
                return layout != null && layout.offers(Element.APPS);
            return true;
        }

        @Override public boolean shown(
                @NonNull com.termux.app.surfaces.cards.ElementRows.Item item) {
            LayoutEditorController layout = mHost.layoutEditor();
            switch (item.visibility) {
                case ELEMENT:
                    return layout != null && item.element != null && layout.isShown(item.element);
                case KEYBOARD:
                    // What the frame draws, as the Keyboard card's eye reads it.
                    return mHost.isInAppKeyboardShown();
                case SEARCH_FIELD:
                    return mDock.option(com.termux.app.surfaces.cards.DockCard.Option.SEARCH_FIELD);
                case DRAWER_TILE:
                    return mDock.option(com.termux.app.surfaces.cards.DockCard.Option.DRAWER_ICON);
                case COMMAND_PALETTE_BUTTON:
                    return mDock.option(
                        com.termux.app.surfaces.cards.DockCard.Option.COMMAND_PALETTE);
                case NONE:
                default:
                    return true;
            }
        }

        @Override public void setShown(
                @NonNull com.termux.app.surfaces.cards.ElementRows.Item item, boolean shown) {
            switch (item.visibility) {
                case ELEMENT:
                    if (item.element == null)
                        return;
                    if (item.element == Element.STATUS)
                        writeShown(Element.STATUS, shown);
                    else
                        mDock.setBarShown(item.element, shown);
                    break;
                case KEYBOARD:
                    writeShown(null, shown);
                    break;
                case SEARCH_FIELD:
                    mDock.setOption(com.termux.app.surfaces.cards.DockCard.Option.SEARCH_FIELD,
                        shown);
                    break;
                case DRAWER_TILE:
                    mDock.setOption(com.termux.app.surfaces.cards.DockCard.Option.DRAWER_ICON,
                        shown);
                    break;
                case COMMAND_PALETTE_BUTTON:
                    mDock.setOption(
                        com.termux.app.surfaces.cards.DockCard.Option.COMMAND_PALETTE, shown);
                    break;
                case NONE:
                default:
                    return;
            }
            positionTargets();
            syncDirty();
            EditorCardSheet sheet = mSheet;
            if (sheet != null && sheet.isShown())
                sheet.restate();
        }

        @Override public void open(@NonNull com.termux.app.surfaces.cards.ElementRows.Item item) {
            EditorCard card = EditorDoor.ELEMENTS_LIST.route(null, item.card);
            if (card == null || !mOpen)
                return;
            if (card == EditorCard.DOCK)
                dockCard().expand(item.expandedBar());
            openCard(card, true);
            EditorCardSheet sheet = mSheet;
            if (item.section != null && sheet != null && mCard == card)
                sheet.scrollTo(item.section);
        }
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
            restateBar(true);
            applyPose(true);
        }
    }

    /** The card came to rest: the frame's pose and the outlines are made final. */
    private void onCardSettled() {
        mLastDetent = -1;
        restateBar(true);
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
        EditorFramePose.Pose pose = EditorFramePose.pose(window, poseTop(window, cardTopPx));
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

    /**
     * The picture the page label stands on, as a small copy centre-cropped to the decor the way
     * the screen shows it ({@link EditorLabelInk#cropBounds}), for {@link #applyLabelInk}; null
     * while there is none. Kept for the session: the label moves with the card.
     */
    @Nullable private int[] mLabelPicture;
    private int mLabelPictureWidth;
    private int mLabelPictureHeight;
    /** The label's ink as last set, so a placement that did not move it costs nothing. */
    private int mLabelInk;

    /** Passthrough mode: the picture is read off the main thread and painted inside the container. */
    private void loadEditorWallpaper(@NonNull View rootContainer) {
        final int token = ++mWallpaperToken;
        View decor = rootContainer.getRootView();
        final int decorWidth = decor != null && decor.getWidth() > 0 ? decor.getWidth()
            : rootContainer.getResources().getDisplayMetrics().widthPixels;
        final int decorHeight = decor != null && decor.getHeight() > 0 ? decor.getHeight()
            : rootContainer.getResources().getDisplayMetrics().heightPixels;
        Thread reader = new Thread(() -> {
            Drawable wallpaper;
            try {
                wallpaper = mHost.readEditorWallpaper();
            } catch (RuntimeException e) {
                wallpaper = null;
            }
            final Drawable picture = wallpaper;
            // The label stands on the wallpaper the screen shows outside the scaled frame, which
            // is the picture the frame paints (the managed one or the system's), or the system's
            // still when the editor reads none. Its ink follows that picture, never the theme.
            final int[] sample = picture != null
                ? sampleLabelPicture(picture, decorWidth, decorHeight)
                : sampleSystemLabelPicture(rootContainer.getContext(), decorWidth, decorHeight);
            final int sampleHeight = EditorLabelInk.sampleHeight(decorWidth, decorHeight);
            rootContainer.post(() -> {
                if (!mSession || token != mWallpaperToken)
                    return;
                mLabelPicture = sample;
                mLabelPictureWidth = EditorLabelInk.SAMPLE_WIDTH;
                mLabelPictureHeight = sampleHeight;
                // The bar may not be up yet (the first open after a theme relaunch); present()
                // applies the ink again once it is.
                applyLabelInk();
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

    /**
     * The page label's ink from the picture under it: the sampled rows at the label's height on
     * the screen ({@link EditorLabelInk#band}), with no theme dim over them. Called when the
     * picture arrives, when the bar goes up and whenever the label moves; it sets the ink only
     * when it changed.
     */
    private void applyLabelInk() {
        EditorBar bar = mBar;
        if (bar == null)
            return;
        int[] picture = mLabelPicture;
        float fraction = Float.NaN;
        if (picture != null) {
            View label = bar.halfRow();
            View decor = label.getRootView();
            if (label.getParent() == null || decor == null || decor.getHeight() <= 0)
                return;
            label.getLocationOnScreen(mTmpLocation);
            float centre = mTmpLocation[1] + label.getHeight() / 2f;
            decor.getLocationOnScreen(mTmpDecorLocation);
            fraction = (centre - mTmpDecorLocation[1]) / decor.getHeight();
        }
        // No picture is what a launcher without storage permission gets for the system's
        // wallpaper: the system's own description of it stands in, as for Home's labels.
        int backdrop = EditorLabelInk.backdrop(picture, mLabelPictureWidth, mLabelPictureHeight,
            fraction, mHost.editorWallpaperHintColor());
        if (!EditorLabelInk.isKnown(backdrop))
            return;
        int[] inks = bar.halfNameInks();
        int ink = EditorLabelInk.pick(backdrop, inks[0], inks[1]);
        if (ink == mLabelInk)
            return;
        mLabelInk = ink;
        bar.setHalfNameInk(ink);
    }

    @NonNull private final int[] mTmpLocation = new int[2];
    @NonNull private final int[] mTmpDecorLocation = new int[2];

    /** The system's still wallpaper, sampled as {@link #sampleLabelPicture}; null for none. */
    @Nullable
    private static int[] sampleSystemLabelPicture(@NonNull android.content.Context context,
                                                  int decorWidth, int decorHeight) {
        try {
            android.app.WallpaperManager manager =
                android.app.WallpaperManager.getInstance(context);
            if (manager.getWallpaperInfo() != null)
                return null;
            Drawable system = manager.getDrawable();
            return system == null ? null : sampleLabelPicture(system, decorWidth, decorHeight);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * {@code picture} drawn into a {@link EditorLabelInk#SAMPLE_WIDTH}-wide bitmap of the decor's
     * shape, centre-cropped as the frame paints it, and its pixels; null when it cannot be drawn.
     * Read off the main thread; the bitmap is not kept.
     */
    @Nullable
    private static int[] sampleLabelPicture(@NonNull Drawable picture, int decorWidth,
                                            int decorHeight) {
        try {
            int w = EditorLabelInk.SAMPLE_WIDTH;
            int h = EditorLabelInk.sampleHeight(decorWidth, decorHeight);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h,
                android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Rect old = picture.copyBounds();
            int[] bounds = EditorLabelInk.cropBounds(picture.getIntrinsicWidth(),
                picture.getIntrinsicHeight(), w, h);
            picture.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
            picture.draw(new android.graphics.Canvas(bmp));
            picture.setBounds(old);
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            bmp.recycle();
            return px;
        } catch (RuntimeException e) {
            return null;
        }
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
        if (!mSliderDragActive && mSheet != null && mSheet.isShown())
            mSheet.restate();
    }

    /**
     * Where the app-wide choices are read and written: Settings' own store, and the app's store
     * for the Dock card's switches Settings has no row for.
     */
    @NonNull
    private EditorSettingsSnapshot.Store settingsStore() {
        return EditorSettingsSnapshot.settings(mHost.context(), dockPrefs());
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

    /**
     * Back inside the editor: a card above peek goes to peek. At peek Back is the surface's, which
     * leaves the editor as Done and Discard do.
     */
    @Override
    public boolean consumeBack() {
        EditorCardSheet sheet = mSheet;
        if (!mOpen || !sheetUp() || sheet == null
            || sheet.detent() == EditorCardSheet.DETENT_PEEK)
            return false;
        collapseCard(true);
        return true;
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
    private final class CardModel implements EditorCardSheet.Model, CardBody.Looks {

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
                    // The keyboard reads what the frame draws: the one answer the switch and the
                    // preview can share.
                    if (element == null)
                        return mHost.isInAppKeyboardShown() ? 1 : 0;
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
                case REFLECTION: return PeekGlassPreferences.reflection(prefs.getSharedPreferences());
                case TEXT_SIZE:
                    return Control.TEXT_SIZE.clamp(Math.round(prefs.getTextSizePercent()
                        / (float) Control.TEXT_SIZE.step) * Control.TEXT_SIZE.step);
                case CONTRAST: return AppearanceLooks.legibilityIndex(prefs.getTerminalContrastLevel());
                case SPLIT_GAP: return Control.SPLIT_GAP.clamp(prefs.getTerminalPaneGap());
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
                case PAGE_TRANSITION:
                    return com.termux.app.launcher.home.HomeSettings.pageTransition(context).ordinal();
                case NUMERALS:
                    return indexOf(NUMERALS, AppearanceSettingsStore.getString(context,
                        "app_launcher_builtin_widget_style", null), 0);
                case PAGE_DOTS:
                    return "off".equals(AppearanceSettingsStore.getString(context,
                        HomeSettings.KEY_PAGE_INDICATOR, null)) ? 0 : 1;
                case WALLPAPER_SCROLL:
                    return AppearanceSettingsStore.getBoolean(context, "wallpaper_parallax", true)
                        ? 1 : 0;
                case MATCH_WORKSPACE:
                case MATCH_HOME:
                    return mMatchUndos.containsKey(matchTarget(row)) ? 1 : 0;
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
                case LOOK: return lookStop(lookHalf());
                case CHANGE_WALLPAPER: return 0;
                case THEME:
                    return indexOf(THEMES, ThemePreview.effective(storedTheme()), 0);
                case WALLPAPER_COLOURS:
                    return AppearanceSettingsStore.getBoolean(context,
                        "terminal_dynamic_colors_enabled", false) ? 1 : 0;
                case FULL_SCREEN:
                    return AppearanceSettingsStore.getBoolean(context,
                        AppearanceSettingsStore.KEY_FULLSCREEN, false) ? 1 : 0;
                case MINIMAL: return mHost.isMinimalMode() ? 1 : 0;
                case VIBRANCY:
                    return Control.VIBRANCY.clamp(mHeldVibrancy != NO_HELD_VIBRANCY
                        ? mHeldVibrancy : prefs.getSurfaceGlassVibrancy());
                case GLASS_EDGE: return Control.EDGE.clamp(prefs.getFancierGlassDispersionPercent());
                default: return cardRowValue(row);
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
                case REFLECTION: return PeekGlassGeometry.REFLECTION_MIN;
                case TEXT_SIZE: return Control.TEXT_SIZE.min;
                case KEY_OPACITY: return Control.KEY_OPACITY.min;
                case LOOK: return 0;
                case VIBRANCY: return Control.VIBRANCY.min;
                case GLASS_EDGE: return Control.EDGE.min;
                default: return cardRowFrom(row);
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
                case REFLECTION: return PeekGlassGeometry.REFLECTION_MAX;
                case TEXT_SIZE: return Control.TEXT_SIZE.max;
                case SPLIT_GAP: return Control.SPLIT_GAP.max;
                case KEY_OPACITY: return Control.KEY_OPACITY.max;
                case KEY_CORNERS: return AppearanceLooks.KEY_CORNERS_MAX_DP;
                case KEY_SPACING: return AppearanceLooks.KEY_SPACING_MAX_TENTHS;
                case LOOK: return AppearanceLooks.CUSTOM_STOP;
                case VIBRANCY: return Control.VIBRANCY.max;
                case GLASS_EDGE: return Control.EDGE.max;
                default: return cardRowTo(row);
            }
        }

        @Override public int step(@NonNull Row row) {
            switch (row) {
                case TEXT_SIZE: return Control.TEXT_SIZE.step;
                case REFLECTION: return PeekGlassGeometry.REFLECTION_STEP;
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
                case KEY_OPACITY:
                case GLASS_EDGE:
                case REFLECTION:
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
                case LOOK:
                    return lookStopName(value);
                case MATCH_WORKSPACE:
                case MATCH_HOME:
                    return getString(value != 0 ? R.string.appearance_match_undo
                        : R.string.appearance_match_copy);
                default:
                    return cardRowReadout(row, value);
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
                case VISIBLE:
                    return null;
                default:
                    return cardRowSummary(row);
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
                case PAGE_TRANSITION:
                    return strings(R.string.settings_home_page_transition_slide,
                        R.string.settings_home_page_transition_depth,
                        R.string.settings_home_page_transition_fade,
                        R.string.settings_home_page_transition_carousel,
                        R.string.settings_home_page_transition_stack);
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
                    return cardRowChoices(row);
            }
        }

        @Nullable @Override public int[] chipIcons(@NonNull Row row) {
            return row == Row.DESIGN ? DESIGN_ICONS.clone() : cardRowGlyphs(row);
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
            if ((row == Row.HEIGHT || row == Row.BOTTOM_PADDING) && mCard == EditorCard.KEYBOARD) {
                LayoutEditorController layout = mHost.layoutEditor();
                if (layout != null) layout.relayHeldWrites();
            }
            if (mSheet != null) mSheet.restate();
        }

        @Override public void onAction(@NonNull Row row, @NonNull View anchor) {
            onRowAction(row, anchor);
        }

        @Override public boolean shown(@NonNull Row row) {
            // All surfaces is the Custom stop's, as the Look page's Custom row was.
            if (mCard == EditorCard.APPEARANCE && EditorRows.isAllSurfaces(row)
                && !com.termux.app.surfaces.cards.GlobalCard.isGlassMaterial(row))
                return AppearanceLooks.isCustomStop(lookStop(mHalf));
            // Reflection is Peek glass's own knob: it stands only while that is the design.
            if (row == Row.REFLECTION) {
                LayoutEditorController layout = mHost.layoutEditor();
                return layout != null && layout.dockStyle() == DockStyle.PEEK_GLASS;
            }
            return true;
        }

        @Nullable @Override public CharSequence stripHeading(@NonNull Row first) {
            if (mCard == EditorCard.DOCK)
                return com.termux.app.surfaces.cards.DockCard.stripHeading(mHost.context());
            return mCard == EditorCard.APPEARANCE && EditorRows.isAllSurfaces(first)
                ? getString(R.string.appearance_strip_all_surfaces) : null;
        }

        @Nullable @Override public View rowView(@NonNull Row row, int widthPx) {
            EditorCard card = mCard;
            if (row == Row.ELEMENTS && card != null) {
                WallHalf screen = com.termux.app.surfaces.cards.ElementRows.screenOf(card);
                if (screen != null)
                    return elementsView(screen);
            }
            if (card == EditorCard.DOCK)
                return dockCard().view(row, widthPx);
            if (card != null && CardBody.redesigned(card))
                return cardBody(card).rowView(row, widthPx);
            return statusTerminalRowView(row, widthPx);
        }

        @Override public void restateRowView(@NonNull Row row, @NonNull View view) {
            EditorCard card = mCard;
            com.termux.app.surfaces.cards.ElementsList elements = mElements;
            if (row == Row.ELEMENTS && elements != null && elements.root == view) {
                elements.restate();
                return;
            }
            if (card == EditorCard.DOCK)
                dockCard().restate(row);
            else if (card != null && CardBody.redesigned(card)) {
                CardBody body = mCardBody;
                if (body != null) body.restate(row, view);
            } else
                restateStatusTerminalRow(row, view);
        }

        @Nullable @Override public CharSequence name(@NonNull Row row) {
            return cardRowName(row);
        }

        @Override public int glyph(@NonNull Row row) {
            return cardRowGlyph(row);
        }

        @Nullable @Override public int[] swatches(@NonNull Row row) {
            return cardRowSwatches(row);
        }

        @Nullable @Override public android.graphics.Typeface choiceTypeface(@NonNull Row row,
                                                                          int index) {
            return cardRowTypeface(row, index);
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
                if (isScreenCard(card))
                    writeScreenGlass(row, value);
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
            case VISIBLE:
                writeShown(elementOf(card), value != 0);
                break;
            case STYLE:
                writeStyle(value == 1);
                break;
            case DESIGN:
                if (layout != null && value >= 0 && value < DockStyle.CHOICES.size())
                    layout.setDockStyle(DockStyle.CHOICES.get(value));
                // Peek glass's Reflection stands with that design alone.
                if (mSheet != null) mSheet.restate(Row.REFLECTION);
                break;
            case REFLECTION:
                PeekGlassPreferences.setReflection(prefs.getSharedPreferences(), value);
                break;
            case CORNERS: writeCorners(value); break;
            case MARGIN: writeMargin(value); break;
            case DEPTH: writeGlassDepth(value); break;
            case TEXT_SIZE: writeTextSize(value); break;
            case CONTRAST: writeLegibility(value); break;
            case SPLIT_GAP: writeSplitGap(value); break;
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
            case PAGE_TRANSITION: {
                com.termux.app.launcher.home.HomePageTransition[] effects =
                    com.termux.app.launcher.home.HomePageTransition.values();
                writeHomeString(HomeSettings.KEY_PAGE_TRANSITION,
                    effects[clamp(value, 0, effects.length - 1)].value);
                break;
            }
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
            case CHANGE_WALLPAPER:
                // Never written: the tile is a shortcut and its tap goes to onAction.
                break;
            case THEME:
                previewTheme(THEMES[clamp(value, 0, 2)]);
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
            case VIBRANCY: writeVibrancy(value); break;
            case GLASS_EDGE: writeGlassEdge(value); break;
            default:
                writeCardRow(row, value, dragging);
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
            case KEYBOARD_THEME:
                openKeyboardTheme();
                break;
            case CHANGE_WALLPAPER:
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
                openSettingsFromEditor(TermuxStylePreferencesFragment.class,
                    R.string.appearance_row_tool_colours, null, TOOL_COLOURS_KEY);
                break;
            case LINK_GESTURES:
                openSettingsFromEditor(HomePreferencesFragment.class,
                    R.string.settings_section_home, HOME_GESTURES_SECTION, null);
                break;
            case LINK_PAGES:
                mHost.openPageOverview();
                break;
            case LINK_LAYOUTS:
                openSettingsPage(KeyboardLayoutsFragment.class, R.string.appearance_row_layouts);
                break;
            case MATCH_WORKSPACE:
            case MATCH_HOME:
                toggleMatch(matchTarget(row));
                break;
            case LINK_GLOBAL: {
                EditorCard global = EditorDoor.EDITOR_GLOBAL_CHIP.route(null, null);
                if (global != null) openCard(global, true);
                break;
            }
            default:
                break;
        }
    }

    // ---- the Match rows ----------------------------------------------------------------------

    /** The screen a Match row copies onto: Home for Match Workspace styling, else the Workspace. */
    @NonNull
    private static WallHalf matchTarget(@NonNull Row row) {
        return row == Row.MATCH_WORKSPACE ? WallHalf.HOME : WallHalf.WORKSPACE;
    }

    /**
     * A Match row tapped: Copy takes the other screen's layout and glass ({@link ScreenMatch});
     * Undo, while the copy is still held this visit, puts this screen back as it was.
     */
    private void toggleMatch(@NonNull WallHalf target) {
        TermuxAppSharedPreferences prefs = prefs();
        LayoutEditorController layout = mHost.layoutEditor();
        com.termux.app.place.PlaceLayoutStore places = layout == null ? null : layout.places();
        if (prefs == null || layout == null || places == null)
            return;
        ScreenMatch.Undo undo = mMatchUndos.remove(target);
        if (undo != null) {
            undo.restore(prefs);
        } else {
            undo = ScreenMatch.match(prefs, places, target);
            if (undo != null) mMatchUndos.put(target, undo);
        }
        // The Look slider reads the half's values again rather than a Custom held this visit.
        mHeldCustom.remove(target);
        layout.applyStoreWrite();
        mHost.redressChrome();
        syncAfterBulkWrite();
        syncDirty();
        if (mSheet != null) mSheet.restate();
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
        if (isScreenCard(card) && half != null) {
            Integer held = mScreenFrostPosition.get(half);
            return GlassControls.readBack(held == null ? -1 : held,
                prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, half),
                prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN, half));
        }
        if (card.slot == null)
            return GlassControls.readBack(prefs.getSurfaceGlassPosition(null),
                prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, half),
                prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN, half));
        return GlassControls.readBack(prefs.getSurfaceGlassPosition(card.slot),
            prefs.getSurfaceValue(card.slot, SurfaceProperty.BLUR, half),
            prefs.getSurfaceValue(card.slot, SurfaceProperty.GRAIN, half));
    }

    /** Where Frost was left on a screen card this session, per half ({@link #writeFrost}). */
    private final java.util.EnumMap<WallHalf, Integer> mScreenFrostPosition =
        new java.util.EnumMap<>(WallHalf.class);

    /**
     * Frost: blur and grain together along {@link GlassControls}' curve, and the position it was
     * left at. On the Appearance card it is the shared glass of the frame's half, every surface on
     * that half put back on it ({@link GlobalGlass}); on a surface's card, that surface's own
     * pair on the half, as the previous editor's Frost wrote it for a tapped element.
     */
    /**
     * A screen card's Frost: that half's Base alone. A surface that holds its own value there
     * keeps it, and neither the other half nor the shared glass position is touched.
     */
    @VisibleForTesting
    static void writeScreenFrost(@NonNull TermuxAppSharedPreferences prefs, @NonNull WallHalf half,
                                 int glass) {
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, half,
            AppearanceLooks.blurDp(GlassControls.blurForGlass(glass)));
        prefs.setSurfaceBaseValue(SurfaceProperty.GRAIN, half,
            AppearanceLooks.grainPercent(GlassControls.grainForGlass(glass)));
    }

    private void writeFrost(@NonNull EditorCard card, int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int glass = Control.GLASS.clamp(value);
        int blur = GlassControls.blurForGlass(glass);
        int grain = GlassControls.grainForGlass(glass);
        WallHalf half = writeHalf(Row.FROST);
        // The position key is one for every card that holds none of its own (the Appearance
        // card's); a screen card keeps its position for the session, per half, and writes its
        // own half's Base only.
        if (isScreenCard(card) && half != null)
            mScreenFrostPosition.put(half, glass);
        else
            prefs.setSurfaceGlassPosition(card.slot, glass);
        if (isScreenCard(card) && half != null) {
            writeScreenFrost(prefs, half, glass);
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
     * A screen card's Opacity or Tint: that half's Base (Home's on the Home card, the
     * Workspace's on the Workspace card), which the surfaces on it wear while they follow it.
     * Unlike the Appearance card's shared glass it puts no surface back on the Base: a dock or
     * status bar given its own value there on its card keeps it.
     */
    private void writeScreenGlass(@NonNull Row row, int value) {
        TermuxAppSharedPreferences prefs = prefs();
        SurfaceProperty property = row.property();
        WallHalf half = writeHalf(row);
        if (prefs == null || property == null || half == null)
            return;
        int clamped = row == Row.OPACITY ? AppearanceLooks.opacityPercent(value)
            : AppearanceLooks.tintPercent(value);
        prefs.setSurfaceBaseValue(property, half, clamped);
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
        syncLayoutSession();
        syncDirty();
        if (mSheet != null) mSheet.restate();
    }

    // ---- the Look slider ---------------------------------------------------------------------

    /**
     * The half the Look row of the card up reads and writes, as {@link EditorRows#halfFor} says:
     * a screen card's own half (Home's, the Workspace's), else the half the frame shows.
     */
    @NonNull
    private WallHalf lookHalf() {
        return readHalf(Row.LOOK);
    }

    /**
     * Where the Look slider stands for a half: Custom where it was put there, else the Look the
     * half wears in its own values ({@link SurfacePresets#wornOn}), so a Look picked on the other
     * screen, which writes the values one for the wall, never moves it.
     */
    private int lookStop(@NonNull WallHalf half) {
        if (mHeldCustom.contains(half))
            return AppearanceLooks.CUSTOM_STOP;
        TermuxAppSharedPreferences prefs = prefs();
        return prefs == null ? AppearanceLooks.CUSTOM_STOP : wornStop(prefs, half);
    }

    /** The stop a half wears in its own values in {@code prefs} (the store, or a drag's batch). */
    private static int wornStop(@NonNull TermuxAppSharedPreferences prefs,
                                @NonNull WallHalf half) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets(prefs)) {
            if (SurfacePresets.wornOn(prefs, preset, half))
                return AppearanceLooks.stopForPresetId(preset.id);
        }
        return AppearanceLooks.CUSTOM_STOP;
    }

    /**
     * The stop a half wears with the values one for the wall compared too: the Look it matches
     * whole, or Custom. What decides whether Done keeps the saved Custom.
     */
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
        WallHalf half = lookHalf();
        boolean held = custom ? mHeldCustom.add(half) : mHeldCustom.remove(half);
        if (prefs != null && moveToStop(prefs, stop, half))
            syncAfterBulkWrite();
        else if (held && mSheet != null)
            mSheet.restate();
    }

    /**
     * The Look slider landed on {@code stop} for {@code half} (the Look row's, {@link #lookHalf}),
     * written into {@code into} (the store, or a drag's batch over it). A Look resets every
     * surface on that half to it; leaving a Custom the user touched keeps it for this visit and
     * stores it as the saved Custom ({@link SessionCustom}). Custom brings back this visit's
     * values, else the saved Custom, else writes nothing: the Look on screen is its seed. False
     * when the half already wears that stop in its own values ({@link #wornStop}).
     */
    private boolean moveToStop(@NonNull TermuxAppSharedPreferences into, int stop,
                               @NonNull WallHalf half) {
        int current = wornStop(into, half);
        if (stop == current)
            return false;
        SurfacePresets.Preset look = AppearanceLooks.presetForStop(SurfacePresets.presets(into),
            stop);
        if (look != null) {
            if (AppearanceLooks.isCustomStop(current))
                mSessionCustom.leave(into);
            SurfacePresets.applyLook(into, look, half);
            forgetGlassEdgePositions();
            return true;
        }
        mSessionCustom.enter(into, half);
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
            if (moveToStop(prefs, stop, lookHalf()))
                syncAfterBulkWrite();
            return;
        }
        beginDrag(true);
        if (mLookDrag == null || mLookDragPrefs == null) {
            mLookDrag = new SurfacePresets.Batch(store);
            mLookDragPrefs = SurfacePresets.writingInto(prefs, mLookDrag);
        }
        if (!moveToStop(mLookDragPrefs, stop, lookHalf()))
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
        syncLayoutSession();
    }

    /** Margin: one number for all the air on screen, on both halves. */
    private void writeMargin(int value) {
        TermuxAppSharedPreferences prefs = prefs();
        if (prefs == null)
            return;
        int margin = AppearanceLooks.marginDp(value);
        prefs.setLayoutMarginDp(false, margin);
        requestGeometryPreview();
        syncLayoutSession();
    }

    /** The shape pass for Corners and Margin; mid-drag the terminal is not resized. */
    private void requestGeometryPreview() {
        int scopes = SurfaceEditorProperties.PREVIEW_ALL & ~SurfaceEditorProperties.PREVIEW_BLUR;
        if (mSliderDragActive) mDragTouchedGeometry = true;
        else scopes |= SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT;
        requestPreview(scopes);
    }

    /** The layout session re-read: its Undo and the card follow a Style, Corners or Margin write. */
    private void syncLayoutSession() {
        LayoutEditorController layout = mHost.layoutEditor();
        if (mOpen && layout != null)
            layout.sync();
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

    /**
     * Every group takes its touches, so nothing under the frame receives them while the editor is
     * up. A tap raises the group's card; a plain horizontal swipe slides to the other half. Where a
     * surface sits is its card's Position row: nothing in the frame is dragged.
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
                if (card != null) {
                    EditorDoor.EDITOR_OUTLINE.log(card);
                    if (card == EditorCard.DOCK)
                        dockCard().expand(dockBarAt(mDownRawX, mDownRawY));
                    openCard(card, true);
                } else {
                    collapseCard(true);
                }
            });
            group.setOnTouchListener((view, event) -> {
                if (!mOpen)
                    return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        mDownRawX = event.getRawX();
                        mDownRawY = event.getRawY();
                        mSwiping = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - mDownRawX;
                        float dy = event.getRawY() - mDownRawY;
                        if (!mSwiping && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy))
                            mSwiping = true;
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        float dx = event.getRawX() - mDownRawX;
                        if (mSwiping) {
                            if (Math.abs(dx) >= dp(SWIPE_DP))
                                switchHalf(dx > 0 ? WallHalf.HOME : WallHalf.WORKSPACE, true, true);
                            return true;
                        }
                        float x = event.getX();
                        float y = event.getY();
                        if (x >= 0 && y >= 0 && x <= view.getWidth() && y <= view.getHeight())
                            view.performClick();
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
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
     * surface (a hidden one, which comes back from its screen card's Elements list).
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
        if (card == null || isScreenCard(card))
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
        int grow = isScreenCard(card) ? -stroke : stroke;
        int[] grown = {rect[0] - grow, rect[1] - grow, rect[2] + grow, rect[3] + grow};
        placeInOverlay(outline, overlay, grown);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0);
        ring.setStroke(stroke, mHost.themeColor(androidx.appcompat.R.attr.colorPrimary,
            R.color.termux_primary));
        ring.setCornerRadius(isScreenCard(card) ? 0f
            : Math.max(0f, outlineRadiusPx(card) + grow));
        outline.setBackground(ring);
        EditorViews.setVisible(outline, true);
    }

    /** Whether {@code card} is a screen's own (Home's or the Workspace's): the whole frame. */
    private static boolean isScreenCard(@Nullable EditorCard card) {
        return card == EditorCard.HOME || card == EditorCard.WORKSPACE;
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
    // The status bar's face and its place in the bar, as the Status bar card's face tiles and
    // Clock position row write them (cards/StatusBarCard). Live like every other editor control,
    // and gated by Done.

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
        // A Match onto a layout the session did not take on entry (Home's, opened in minimal
        // mode) is put back by its own Undo; the session's revert covers the rest.
        TermuxAppSharedPreferences matched = prefs();
        if (matched != null) {
            for (ScreenMatch.Undo undo : mMatchUndos.values())
                undo.restore(matched);
        }
        mMatchUndos.clear();
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

    /**
     * The phone turned: the editor stays open and edits the orientation the phone is in now
     * (editor-redesign §1, rotate to edit); the chip names it, and the open card reads it.
     */
    public void onPlaceOrientationChanged() {
        LayoutEditorController layout = mHost.layoutEditor();
        View content = mHost.findView(android.R.id.content);
        if (!mOpen || layout == null || content == null)
            return;
        // Posted: the configuration lands before the window is laid out in it.
        content.post(() -> {
            if (!mOpen)
                return;
            layout.onPlaceOrientationChanged();
            restateOrientation();
            restateCard();
        });
    }

    /** Takes the editor off screen: the card, the bar, then the frame back to size. */
    private void dismiss(@Nullable Runnable onHidden) {
        if (!mOpen) {
            if (onHidden != null)
                onHidden.run();
            return;
        }
        if (mSliderDragActive)
            endDrag();
        mOpen = false;
        mMatchUndos.clear();
        mHost.retainGlassCapture(false);
        mFramed = false;
        mPresentToken++;
        mSliderDragActive = false;
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
        }
        mElements = null;
        cancelFrameAnimator();
        mFrameCtxValid = false;
        mTargetScale = -1f;
        mFollowingSheet = false;
        mEntering = false;
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
        mLabelPicture = null;
        mLabelInk = 0;
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
        // An Undo can move Style or Corners: the layout session's dirty state follows.
        syncLayoutSession();
        positionOutline();
    }

    // ------------------------------------------------------- Status bar and Terminal cards

    /** The rows the Status bar and Terminal cards drew themselves, for their restates. */
    @NonNull private final java.util.Map<Row, com.termux.app.surfaces.cards.CardRowView> mOwnRows =
        new java.util.EnumMap<>(Row.class);

    /**
     * The Status bar or Terminal card's own row for {@code row} ({@link
     * EditorCardSheet.Model#rowView}), or null for a row the sheet draws.
     */
    @Nullable
    private View statusTerminalRowView(@NonNull Row row, int widthPx) {
        EditorCard card = mCard;
        Context context = mHost.context();
        com.termux.app.surfaces.cards.CardRowView built = null;
        if (card == EditorCard.STATUS)
            built = com.termux.app.surfaces.cards.StatusBarCard.view(context, row, widthPx,
                mStatusCardHost);
        else if (card == EditorCard.TERMINAL)
            built = com.termux.app.surfaces.cards.TerminalCard.view(context, row, widthPx,
                mTerminalCardHost);
        if (built == null) {
            mOwnRows.remove(row);
            return null;
        }
        mOwnRows.put(row, built);
        return built.view;
    }

    /**
     * The Keyboard, Home, Global or Workspace card's rows ({@link CardBody}), for the card being
     * built. Asked only while the sheet builds a card (through {@link EditorCardSheet.Model#rowView}),
     * so a restatement keeps reading the views it built; a card whose rows changed (the glass edge
     * came or went) gets a body of its own.
     */
    @Nullable private CardBody mCardBody;

    @NonNull
    private CardBody cardBody(@NonNull EditorCard card) {
        java.util.List<EditorRows.Spec> specs = EditorRows.rows(card, offers());
        CardBody body = mCardBody;
        if (body == null || !body.drawsFor(card, specs)) {
            body = new CardBody(mHost.context(), mModel, mModel, card, specs, () -> {
                EditorCardSheet sheet = mSheet;
                if (sheet != null) sheet.setDetent(EditorCardSheet.DETENT_FULL, true);
            });
            mCardBody = body;
        }
        return body;
    }

    /** Reads a row {@link #statusTerminalRowView} built again from the values. */
    private void restateStatusTerminalRow(@NonNull Row row, @NonNull View view) {
        com.termux.app.surfaces.cards.CardRowView built = mOwnRows.get(row);
        if (built != null && built.view == view)
            built.restate();
    }

    /** The Status bar card's reads and writes, through the paths its old rows and Settings take. */
    @NonNull private final com.termux.app.surfaces.cards.StatusBarCard.Host mStatusCardHost =
        new com.termux.app.surfaces.cards.StatusBarCard.Host() {
            @Override public int position() {
                LayoutEditorController layout = mHost.layoutEditor();
                return com.termux.app.surfaces.cards.StatusBarCard.positionIndex(
                    layout == null ? null : layout.edgeOf(Element.STATUS));
            }

            @Override public void setPosition(int index) {
                LayoutEditorController layout = mHost.layoutEditor();
                PlaceLayout.Edge[] edges = com.termux.app.surfaces.cards.StatusBarCard.POSITIONS;
                if (layout == null || index < 0 || index >= edges.length)
                    return;
                layout.moveTo(Element.STATUS, edges[index]);
                positionTargets();
                syncDirty();
            }

            @Override public int clockFace() {
                TermuxAppSharedPreferences prefs = prefs();
                return com.termux.app.surfaces.cards.StatusBarCard.faceIndex(
                    prefs == null ? null : prefs.getTopPaneClockStyle());
            }

            @Override public void setClockFace(int index) {
                TermuxAppSharedPreferences prefs = prefs();
                if (prefs == null)
                    return;
                pickClockStyle(com.termux.app.surfaces.cards.StatusBarCard.faceStyle(index,
                    prefs.getTopPaneClockStyle()));
            }

            @Override public int clockPosition() {
                TermuxAppSharedPreferences prefs = prefs();
                return com.termux.app.surfaces.cards.StatusBarCard.alignmentIndex(
                    prefs == null ? null : prefs.getTopPaneClockAlignment());
            }

            @Override public void setClockPosition(int index) {
                String[] alignments = com.termux.app.surfaces.cards.StatusBarCard.ALIGNMENTS;
                if (index >= 0 && index < alignments.length)
                    pickClockAlignment(alignments[index]);
            }

            @Override public boolean option(int index) {
                TermuxAppSharedPreferences prefs = prefs();
                if (prefs == null)
                    return false;
                switch (index) {
                    case com.termux.app.surfaces.cards.StatusBarCard.OPTION_12H:
                        return prefs.isTopPaneClockAmPmEnabled();
                    case com.termux.app.surfaces.cards.StatusBarCard.OPTION_SECONDS:
                        return prefs.isStatusClockSecondsEnabled();
                    case com.termux.app.surfaces.cards.StatusBarCard.OPTION_FAHRENHEIT:
                        return prefs.isStatusWidgetWeatherFahrenheit();
                    case com.termux.app.surfaces.cards.StatusBarCard.OPTION_CAMERA_GAP:
                        return com.termux.app.surfaces.cards.StatusBarCard.cameraGapOn(
                            prefs.getStatusBarCutoutMode());
                    default:
                        return false;
                }
            }

            @Override public void setOption(int index, boolean on) {
                TermuxAppSharedPreferences prefs = prefs();
                if (prefs == null || option(index) == on)
                    return;
                switch (index) {
                    case com.termux.app.surfaces.cards.StatusBarCard.OPTION_12H:
                        // The old 12-hour row's write, so the bar and the Wall strip follow.
                        mModel.onValue(Row.CLOCK_12H, on ? 1 : 0, false);
                        return;
                    case com.termux.app.surfaces.cards.StatusBarCard.OPTION_SECONDS:
                        prefs.setStatusClockSecondsEnabled(on);
                        break;
                    case com.termux.app.surfaces.cards.StatusBarCard.OPTION_FAHRENHEIT:
                        prefs.setStatusWidgetWeatherFahrenheit(on);
                        break;
                    default:
                        return;
                }
                mHost.refreshTerminalWindowBar();
                syncDirty();
            }

            @Override public boolean indicator(int index) {
                TermuxAppSharedPreferences prefs = prefs();
                if (prefs == null)
                    return false;
                switch (index) {
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_CPU:
                        return prefs.isStatusWidgetCpuEnabled();
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_RAM:
                        return prefs.isStatusWidgetRamEnabled();
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_WEATHER:
                        return prefs.isStatusWidgetWeatherEnabled();
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_NOTIFICATIONS:
                        return prefs.isStatusWidgetNotificationsEnabled();
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_MEDIA:
                        return prefs.isStatusWidgetMediaEnabled();
                    default:
                        return false;
                }
            }

            @Override public void setIndicator(int index, boolean on) {
                TermuxAppSharedPreferences prefs = prefs();
                if (prefs == null || indicator(index) == on)
                    return;
                switch (index) {
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_CPU:
                        prefs.setStatusWidgetCpuEnabled(on);
                        break;
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_RAM:
                        prefs.setStatusWidgetRamEnabled(on);
                        break;
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_WEATHER:
                        prefs.setStatusWidgetWeatherEnabled(on);
                        break;
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_NOTIFICATIONS:
                        prefs.setStatusWidgetNotificationsEnabled(on);
                        break;
                    case com.termux.app.surfaces.cards.StatusBarCard.INDICATOR_MEDIA:
                        prefs.setStatusWidgetMediaEnabled(on);
                        break;
                    default:
                        return;
                }
                // The bar's refresh ends in its widgets' pass, which reads every indicator.
                mHost.refreshTerminalWindowBar();
                syncDirty();
            }

            @Override public void openCameraGap() {
                Context context = mHost.context();
                try {
                    context.startActivity(SettingsActivity.createFragmentIntent(context,
                        StatusBarPreferencesFragment.class,
                        R.string.settings_destination_status_bar, null,
                        TermuxPreferenceConstants.TERMUX_APP.KEY_STATUS_BAR_CUTOUT_MODE));
                } catch (RuntimeException e) {
                    // No Settings to open (a stripped build): the editor stays as it is.
                }
            }
        };

    /** The Terminal card's reads and writes, through the paths its old rows and Settings take. */
    @NonNull private final com.termux.app.surfaces.cards.TerminalCard.Host mTerminalCardHost =
        new com.termux.app.surfaces.cards.TerminalCard.Host() {
            @Override public int contrast() {
                TermuxAppSharedPreferences prefs = prefs();
                return prefs == null ? 1
                    : AppearanceLooks.legibilityIndex(prefs.getTerminalContrastLevel());
            }

            @Override public void setContrast(int index) {
                // The old Contrast row's write: the palette rebuilds at the new level.
                mModel.onValue(Row.CONTRAST, index, false);
                syncDirty();
            }

            @Override public int trail() {
                TermuxAppSharedPreferences prefs = prefs();
                return prefs == null ? 1
                    : com.termux.app.surfaces.cards.TerminalCard.trailIndex(
                        prefs.isTerminalCursorTrailEnabled(), prefs.getTerminalCursorTrailStyle());
            }

            @Override public void setTrail(int index) {
                List<String> trails = com.termux.app.surfaces.cards.TerminalCard.TRAILS;
                if (index >= 0 && index < trails.size())
                    writeTrailStyle(trails.get(index));
            }

            @Override public int effect() {
                TermuxAppSharedPreferences prefs = prefs();
                return prefs == null ? 0
                    : com.termux.app.surfaces.cards.TerminalCard.effectIndex(
                        prefs.getTerminalRetroEffect());
            }

            @Override public void setEffect(int index) {
                List<String> effects = com.termux.app.surfaces.cards.TerminalCard.EFFECTS;
                if (index >= 0 && index < effects.size())
                    writeRetroEffect(effects.get(index));
            }

            @Override public void openFont() {
                // The old Font link's page.
                openSettingsPage(TermuxFontsPreferencesFragment.class, R.string.appearance_row_font);
            }
        };

    // ------------------------------------------------------------------------------ Dock card
    //
    // The Dock card's Style, Bars, Dock type and Options rows (editor-redesign §2 Dock), built by
    // cards/DockCard and read and written here.

    @Nullable private com.termux.app.surfaces.cards.DockCard mDockCard;

    @NonNull
    private com.termux.app.surfaces.cards.DockCard dockCard() {
        if (mDockCard == null)
            mDockCard = new com.termux.app.surfaces.cards.DockCard(new DockHost());
        return mDockCard;
    }

    /**
     * The Dock card opens next on {@code bar}'s settings, or with every bar's closed for null: a
     * door from outside the editor that names the bar pressed (the long-press popup's Edit).
     */
    public void expandDockBar(@Nullable Element bar) {
        dockCard().expand(bar);
    }

    /** The bar a tap at a raw screen point landed on: the A–Z strip, the extra keys, or neither. */
    @Nullable
    private Element dockBarAt(float rawX, float rawY) {
        if (dockViewHit(R.id.apps_bar_az_row, rawX, rawY)
            || dockViewHit(R.id.place_az_bar_host, rawX, rawY))
            return Element.AZ;
        if (dockViewHit(R.id.place_extra_keys_host, rawX, rawY)
            || dockViewHit(R.id.terminal_toolbar_view_pager, rawX, rawY))
            return Element.EXTRA_KEYS;
        return null;
    }

    private boolean dockViewHit(int id, float rawX, float rawY) {
        View view = mHost.findView(id);
        if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0)
            return false;
        android.graphics.Rect rect = new android.graphics.Rect();
        if (!view.getGlobalVisibleRect(rect))
            return false;
        int[] window = new int[2];
        view.getRootView().getLocationOnScreen(window);
        return rect.contains(Math.round(rawX) - window[0], Math.round(rawY) - window[1]);
    }

    /** Where the A–Z strip's and the dock's new switches are stored, beside the app's own. */
    @Nullable
    private SharedPreferences dockPrefs() {
        TermuxAppSharedPreferences prefs = prefs();
        return prefs == null ? null : prefs.getSharedPreferences();
    }

    /** The bars stacked in {@code order} where they share an edge, written when anything moved. */
    private void restackBars(@NonNull java.util.List<Element> order) {
        LayoutEditorController layout = mHost.layoutEditor();
        PlaceLayout arranged = layout == null ? null : layout.arrangedLayout();
        if (layout == null || arranged == null)
            return;
        PlaceLayout next = com.termux.app.surfaces.cards.BarOrder.restack(arranged, order);
        if (!next.equals(arranged)) {
            layout.writeSlots(next);
            positionTargets();
        }
    }

    private final class DockHost implements com.termux.app.surfaces.cards.DockCard.Host {
        @NonNull @Override public Context context() {
            return mHost.context();
        }

        @Override public boolean floating() {
            TermuxAppSharedPreferences prefs = prefs();
            return prefs != null
                && prefs.getLayoutStyle() == TermuxAppSharedPreferences.LayoutStyle.FLOATING;
        }

        @Override public void setFloating(boolean floating) {
            writeStyle(floating);
            // The Height strip's range follows the style.
            if (mSheet != null) mSheet.restate();
        }

        @NonNull @Override public java.util.List<Element> barOrder() {
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout == null)
                return com.termux.app.surfaces.cards.BarOrder.DEFAULT;
            java.util.List<Element> stored =
                com.termux.app.surfaces.cards.BarOrder.parse(layout.barOrder());
            PlaceLayout arranged = layout.arrangedLayout();
            return arranged == null ? stored
                : com.termux.app.surfaces.cards.BarOrder.reconcile(stored, arranged);
        }

        @Override public void setBarOrder(@NonNull java.util.List<Element> order) {
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout == null)
                return;
            layout.setBarOrder(com.termux.app.surfaces.cards.BarOrder.format(order));
            restackBars(order);
            // The order alone (bars on edges of their own) moves no slot: Undo still holds it.
            syncDirty();
        }

        @Override public boolean offers(@NonNull Element bar) {
            LayoutEditorController layout = mHost.layoutEditor();
            return layout != null && layout.offers(bar);
        }

        @Override public boolean barShown(@NonNull Element bar) {
            LayoutEditorController layout = mHost.layoutEditor();
            return layout != null && layout.isShown(bar);
        }

        @Override public void setBarShown(@NonNull Element bar, boolean shown) {
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout == null)
                return;
            java.util.List<Element> order = barOrder();
            layout.setShown(bar, shown);
            // Back in the list's place among the bars it now shares an edge with.
            if (shown)
                restackBars(order);
            positionTargets();
        }

        @NonNull @Override
        public com.termux.app.surfaces.cards.BarOrder.Position position(@NonNull Element bar) {
            LayoutEditorController layout = mHost.layoutEditor();
            PlaceLayout arranged = layout == null ? null : layout.arrangedLayout();
            if (arranged == null)
                return bar == Element.AZ
                    ? com.termux.app.surfaces.cards.BarOrder.Position.RIDES_APPS_ROW
                    : com.termux.app.surfaces.cards.BarOrder.Position.ABOVE_KEYBOARD;
            return com.termux.app.surfaces.cards.BarOrder.positionOf(bar, arranged.slot(bar));
        }

        @Override public void setPosition(@NonNull Element bar,
                @NonNull com.termux.app.surfaces.cards.BarOrder.Position position) {
            LayoutEditorController layout = mHost.layoutEditor();
            PlaceLayout arranged = layout == null ? null : layout.arrangedLayout();
            if (layout == null || arranged == null)
                return;
            java.util.List<Element> order = barOrder();
            PlaceLayout next = com.termux.app.surfaces.cards.BarOrder.restack(
                com.termux.app.surfaces.cards.BarOrder.withPosition(arranged, bar, position),
                order);
            layout.writeSlots(next);
            positionTargets();
        }

        @Override public int appIcons() {
            TermuxAppSharedPreferences prefs = prefs();
            return prefs == null ? AppearanceLooks.APP_ICONS_MIN
                : Control.APP_ICONS.clamp(prefs.getAppLauncherButtonCount());
        }

        @Override public int appIconsFrom() {
            return AppearanceLooks.APP_ICONS_MIN;
        }

        @Override public int appIconsTo() {
            return AppearanceLooks.APP_ICONS_MAX;
        }

        @Override public void setAppIcons(int count) {
            writeAppIcons(count);
        }

        @Override public boolean mostUsed() {
            return AppearanceSettingsStore.getBoolean(mHost.context(),
                "app_launcher_most_used_page", false);
        }

        @Override public void setMostUsed(boolean on) {
            AppearanceSettingsStore.putBoolean(mHost.context(), "app_launcher_most_used_page", on);
            settingsWritten();
        }

        @NonNull @Override public CharSequence iconPack() {
            String pack = com.termux.app.launcher.data.IconPackChoices.current(prefs(),
                com.termux.app.launcher.data.IconPackChoices.KEY_PINNED);
            if (pack.isEmpty())
                return getString(R.string.editor_dock_icon_pack_global);
            android.content.pm.PackageManager manager = mHost.context().getPackageManager();
            try {
                return manager.getApplicationLabel(manager.getApplicationInfo(pack, 0));
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return getString(R.string.editor_dock_icon_pack_global);
            }
        }

        @Override public void pickIconPack() {
            Context context = mHost.context();
            CharSequence global = getString(R.string.editor_dock_icon_pack_global);
            TermuxAppSharedPreferences prefs = prefs();
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            // Listing the packs asks the package manager: off the main thread.
            new Thread(() -> {
                com.termux.app.launcher.data.IconPackChoices.Listing listing =
                    com.termux.app.launcher.data.IconPackChoices.listing(context, prefs,
                        com.termux.app.launcher.data.IconPackChoices.KEY_PINNED, global);
                main.post(() -> showIconPacks(listing));
            }, "dock-icon-packs").start();
        }

        @Override public boolean azGlass() {
            SharedPreferences store = dockPrefs();
            return store == null
                ? com.termux.app.surfaces.cards.DockCard.DEFAULT_AZ_GLASS_BACKGROUND
                : store.getBoolean(com.termux.app.surfaces.cards.DockCard.KEY_AZ_GLASS_BACKGROUND,
                    com.termux.app.surfaces.cards.DockCard.DEFAULT_AZ_GLASS_BACKGROUND);
        }

        @Override public void setAzGlass(boolean on) {
            SharedPreferences store = dockPrefs();
            if (store != null)
                store.edit().putBoolean(
                    com.termux.app.surfaces.cards.DockCard.KEY_AZ_GLASS_BACKGROUND, on).apply();
            settingsWritten();
            azWritten();
        }

        @Override public int azShow() {
            SharedPreferences store = dockPrefs();
            String stored = store == null ? null
                : store.getString(com.termux.app.surfaces.cards.DockCard.KEY_AZ_SHOW, null);
            return indexOf(com.termux.app.surfaces.cards.DockCard.AZ_SHOW_VALUES, stored, 0);
        }

        @Override public void setAzShow(int index) {
            SharedPreferences store = dockPrefs();
            String[] values = com.termux.app.surfaces.cards.DockCard.AZ_SHOW_VALUES;
            if (store != null)
                store.edit().putString(com.termux.app.surfaces.cards.DockCard.KEY_AZ_SHOW,
                    values[Math.max(0, Math.min(values.length - 1, index))]).apply();
            settingsWritten();
            azWritten();
        }

        /**
         * The A–Z strip's look moved: the live chrome is arranged again, since the pull tab gives
         * the strip's band back and Faint and the glass are applied with the bar's host.
         */
        private void azWritten() {
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout != null) layout.applyStoreWrite();
        }

        @Override public void openExtraKeysEditor() {
            openSettingsPage(
                com.termux.app.fragments.settings.termux.ExtraKeysEditorFragment.class,
                R.string.settings_edit_extra_keys_title);
        }

        @Override public int stripHeightDp() {
            LayoutEditorController layout = mHost.layoutEditor();
            int dp = layout == null ? -1 : layout.wallStripHeightDp();
            return dp > 0 ? dp : com.termux.app.dock.WallStripRowPolicy.DEFAULT_HEIGHT_DP;
        }

        @Override public void setStripHeightDp(int dp) {
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout != null)
                layout.setWallStripHeightDp(dp);
        }

        @Override public int dockType() {
            LayoutEditorController layout = mHost.layoutEditor();
            return layout == null ? 0 : Math.max(0, DockStyle.CHOICES.indexOf(layout.dockStyle()));
        }

        @Override public void setDockType(int index) {
            LayoutEditorController layout = mHost.layoutEditor();
            if (layout != null && index >= 0 && index < DockStyle.CHOICES.size())
                layout.setDockStyle(DockStyle.CHOICES.get(index));
            // Peek's Reflection stands with that type alone.
            if (mSheet != null) mSheet.restate(Row.REFLECTION);
        }

        @Override public boolean option(
                @NonNull com.termux.app.surfaces.cards.DockCard.Option option) {
            LayoutEditorController layout = mHost.layoutEditor();
            Context context = mHost.context();
            switch (option) {
                case SEARCH_FIELD: return layout != null && layout.searchField();
                case DRAWER_ICON: return layout != null && layout.drawerTile();
                case COMMAND_PALETTE: {
                    SharedPreferences store = dockPrefs();
                    return store == null
                        ? com.termux.app.surfaces.cards.DockCard.DEFAULT_COMMAND_PALETTE_BUTTON
                        : store.getBoolean(
                            com.termux.app.surfaces.cards.DockCard.KEY_COMMAND_PALETTE_BUTTON,
                            com.termux.app.surfaces.cards.DockCard.DEFAULT_COMMAND_PALETTE_BUTTON);
                }
                case NOTIFICATION_DOTS:
                    return AppearanceSettingsStore.getBoolean(context,
                        "app_launcher_notification_dots", false);
                case QUICK_REPLY:
                    return AppearanceSettingsStore.getBoolean(context,
                        "app_launcher_quick_reply_swipe", true);
                case MONOCHROME:
                default:
                    return AppearanceSettingsStore.getBoolean(context, "app_launcher_bw_icons",
                        false);
            }
        }

        @Override public void setOption(
                @NonNull com.termux.app.surfaces.cards.DockCard.Option option, boolean on) {
            LayoutEditorController layout = mHost.layoutEditor();
            Context context = mHost.context();
            switch (option) {
                case SEARCH_FIELD:
                    if (layout != null) layout.setSearchField(on);
                    break;
                case DRAWER_ICON:
                    if (layout != null) layout.setDrawerTile(on);
                    break;
                case COMMAND_PALETTE: {
                    SharedPreferences store = dockPrefs();
                    if (store != null)
                        store.edit().putBoolean(
                            com.termux.app.surfaces.cards.DockCard.KEY_COMMAND_PALETTE_BUTTON, on)
                            .apply();
                    settingsWritten();
                    break;
                }
                case NOTIFICATION_DOTS:
                    AppearanceSettingsStore.putBoolean(context, "app_launcher_notification_dots",
                        on);
                    settingsWritten();
                    break;
                case QUICK_REPLY:
                    AppearanceSettingsStore.putBoolean(context, "app_launcher_quick_reply_swipe",
                        on);
                    settingsWritten();
                    break;
                case MONOCHROME:
                default:
                    AppearanceSettingsStore.putBoolean(context, "app_launcher_bw_icons", on);
                    settingsWritten();
                    break;
            }
        }
    }

    /** The apps row's icon packs to pick from; a pick lands at once, as Global's does. */
    private void showIconPacks(
            @NonNull com.termux.app.launcher.data.IconPackChoices.Listing listing) {
        if (!mOpen)
            return;
        Context context = mHost.context();
        CharSequence[] labels = new CharSequence[listing.entries.size()];
        for (int i = 0; i < labels.length; i++)
            labels[i] = listing.entries.get(i).label;
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.editor_dock_icon_pack)
            .setSingleChoiceItems(labels, listing.checked, (dialog, which) -> {
                if (which >= 0 && which < listing.entries.size()) {
                    com.termux.app.launcher.data.IconPackChoices.applyLive(context, prefs(),
                        com.termux.app.launcher.data.IconPackChoices.KEY_PINNED,
                        listing.entries.get(which).value);
                    settingsWritten();
                    if (mSheet != null) mSheet.restate();
                }
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // Keyboard, Home and Global cards ---------------------------------------------------------
    //
    // The rows those cards added (editor-redesign §2): read, ranged, named and written here, so
    // the card model's switches only hand their default case on.

    /** Settings › Home's Gestures section, which the Home card's Gestures chip opens on. */
    private static final String HOME_GESTURES_SECTION = "home_gestures";
    /** Settings › Home's Pages section, which the Home card's Edit pages chip scrolls to. */
    private static final String HOME_PAGES_SECTION = "home_pages";
    /** The tools that follow the terminal's colours, on the page the Tool colours chip opens. */
    private static final String TOOL_COLOURS_KEY = "theme_templates_enabled";

    /** The Tint colour swatches' stored values, in order. */
    private String[] tintHueValues() {
        return getResources().getStringArray(R.array.appearance_editor_tint_hue_values);
    }

    /** A value for a row the card model's own switch does not name; 0 for none. */
    private int cardRowValue(@NonNull Row row) {
        TermuxAppSharedPreferences prefs = prefs();
        LayoutEditorController layout = mHost.layoutEditor();
        Context context = mHost.context();
        if (prefs == null)
            return 0;
        switch (row) {
            case BOTTOM_PADDING: {
                int chin = layout == null ? -1 : layout.keyboardChinDp();
                return chin >= 0 ? chin : prefs.getSharedInAppKeyboardBottomPadding();
            }
            case OPACITY_LINK:
                return prefs.isInAppKeyboardOpacityLinked() ? 1 : 0;
            case HOME_COLUMNS:
            case HOME_ROWS: {
                int[] grid = layout == null ? null : layout.widgetGrid();
                if (grid == null)
                    return row == Row.HOME_COLUMNS ? prefs.getAppLauncherWidgetGridColumns()
                        : prefs.getAppLauncherWidgetGridRows();
                return row == Row.HOME_COLUMNS ? grid[0] : grid[1];
            }
            case HOME_ICON_SIZE:
                return com.termux.app.launcher.widget.HomeIconSize.fromStored(
                    AppearanceSettingsStore.getString(context, HomeSettings.KEY_ICON_SCALE, null))
                    .ordinal();
            case SHOW_WALLPAPER:
                return AppearanceSettingsStore.getBoolean(context, "use_system_wallpaper", false)
                    ? 1 : 0;
            case TINT_HUE:
                return indexOf(tintHueValues(), prefs.getSurfaceGlassTintHue(), 0);
            default:
                return 0;
        }
    }

    private int cardRowFrom(@NonNull Row row) {
        switch (row) {
            case BOTTOM_PADDING:
                return TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_BOTTOM_PADDING;
            case HOME_COLUMNS:
                return TermuxPreferenceConstants.TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS;
            case HOME_ROWS:
                return TermuxPreferenceConstants.TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS;
            default:
                return 0;
        }
    }

    private int cardRowTo(@NonNull Row row) {
        switch (row) {
            case BOTTOM_PADDING:
                return TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING;
            case HOME_COLUMNS:
                return TermuxPreferenceConstants.TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS;
            case HOME_ROWS:
                return TermuxPreferenceConstants.TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS;
            case HOME_ICON_SIZE:
            case TINT_HUE:
                return 2;
            default:
                return 1;
        }
    }

    @NonNull
    private String cardRowReadout(@NonNull Row row, int value) {
        if (row == Row.BOTTOM_PADDING)
            return getString(R.string.appearance_readout_dp, value);
        return String.valueOf(value);
    }

    /** A section's note, Glass widgets' explanation; null for the rest. */
    @Nullable
    private CharSequence cardRowSummary(@NonNull Row row) {
        EditorCard card = mCard;
        switch (row) {
            case SECTION_LOOK:
                if (card == EditorCard.HOME) return getString(R.string.editor_note_home_only);
                if (card == EditorCard.WORKSPACE)
                    return getString(R.string.editor_note_workspace_only);
                return null;
            case SECTION_GLASS:
                if (card == EditorCard.HOME) return getString(R.string.editor_note_home_glass);
                if (card == EditorCard.WORKSPACE)
                    return getString(R.string.editor_note_workspace_glass);
                return null;
            case SECTION_GLASS_MATERIAL:
                return getString(R.string.editor_note_both_screens);
            default:
                return null;
        }
    }

    @NonNull
    private String[] cardRowChoices(@NonNull Row row) {
        switch (row) {
            case HOME_ICON_SIZE:
                return strings(R.string.editor_home_icon_small, R.string.editor_home_icon_default,
                    R.string.editor_home_icon_large);
            case TINT_HUE:
                return getResources().getStringArray(R.array.appearance_editor_tint_hue_entries);
            default:
                return new String[0];
        }
    }

    /** The glyphs a choice row shows instead of its words; null for words. */
    @Nullable
    private int[] cardRowGlyphs(@NonNull Row row) {
        switch (row) {
            case FORM:
                return new int[] {EditorGlyphs.KB_DOCKED, EditorGlyphs.KB_FLOATING,
                    EditorGlyphs.KB_SPLIT};
            case LABEL_SIZE:
                return new int[] {EditorGlyphs.LABEL_SMALL, EditorGlyphs.LABEL_MEDIUM,
                    EditorGlyphs.LABEL_LARGE};
            case HOME_ICON_SIZE:
                return new int[] {EditorGlyphs.ICON_SMALL, EditorGlyphs.ICON_DEFAULT,
                    EditorGlyphs.ICON_LARGE};
            case FOLDERS:
                return new int[] {EditorGlyphs.FOLDER_COMPACT, EditorGlyphs.FOLDER_DEFAULT,
                    EditorGlyphs.FOLDER_ROOMY};
            case PAGE_TRANSITION:
                return new int[] {EditorGlyphs.TRANSITION_SLIDE, EditorGlyphs.TRANSITION_DEPTH,
                    EditorGlyphs.TRANSITION_FADE, EditorGlyphs.TRANSITION_CAROUSEL,
                    EditorGlyphs.TRANSITION_STACK};
            case THEME:
                return new int[] {R.drawable.ic_ed_theme_system, R.drawable.ic_ed_theme_light,
                    R.drawable.ic_ms_dark_mode};
            default:
                return null;
        }
    }

    /** The name a redesigned card gives a row where it is not the row's own. */
    @Nullable
    private CharSequence cardRowName(@NonNull Row row) {
        if (row == Row.OPACITY && mCard == EditorCard.KEYBOARD)
            return getString(R.string.editor_row_background_opacity);
        if (row == Row.KEYBOARD_THEME)
            return getString(R.string.editor_chip_keyboard_theme);
        if (row == Row.LINK_PAGES)
            return getString(R.string.editor_chip_edit_pages);
        return null;
    }

    /**
     * The glyph a redesigned card gives a row (editor-redesign §8, one icon per idea: the palette
     * is Global's alone, Wallpaper colours has its own); 0 keeps the row's own.
     */
    private int cardRowGlyph(@NonNull Row row) {
        switch (row) {
            case OPACITY:
                return mCard == EditorCard.KEYBOARD ? EditorGlyphs.KB_BACKGROUND_OPACITY : 0;
            case KEY_OPACITY: return EditorGlyphs.KB_KEY_OPACITY;
            case KEY_SPACING: return R.drawable.ic_ms_space_bar;
            case KEYBOARD_THEME: return R.drawable.ic_ms_brush;
            case LINK_LAYOUTS: return R.drawable.ic_ms_keyboard_alt;
            case LABEL_SIZE: return R.drawable.ic_ms_text_fields;
            case NUMERALS: return R.drawable.ic_ms_tag;
            case GLASS_WIDGETS: return R.drawable.ic_ms_widgets;
            case TWO_LINE: return R.drawable.ic_ms_wrap_text;
            case LABEL_SHADOW: return R.drawable.ic_ms_blur_on;
            case PAGE_DOTS: return R.drawable.ic_ms_more_horiz;
            case WALLPAPER_SCROLL: return R.drawable.ic_ms_wallpaper;
            case CHANGE_WALLPAPER: return R.drawable.ic_ms_wallpaper;
            case LINK_GESTURES: return R.drawable.ic_ms_swipe;
            case LINK_PAGES: return R.drawable.ic_ms_view_carousel;
            case WALLPAPER_COLOURS: return EditorGlyphs.WALLPAPER_COLOURS;
            case TINT_HUE: return R.drawable.ic_ms_format_color_fill;
            case VIBRANCY: return R.drawable.ic_ms_auto_awesome;
            case DEPTH: return R.drawable.ic_ms_layers;
            case FULL_SCREEN: return R.drawable.ic_ms_fullscreen;
            case MINIMAL: return R.drawable.ic_ms_crop_free;
            case TOOL_COLOURS: return R.drawable.ic_ms_format_paint;
            default: return 0;
        }
    }

    /** Tint colour's three swatches: the theme's primary, secondary and tertiary. */
    @Nullable
    private int[] cardRowSwatches(@NonNull Row row) {
        if (row != Row.TINT_HUE)
            return null;
        Context context = mHost.context();
        return new int[] {
            EditorViews.color(context, androidx.appcompat.R.attr.colorPrimary),
            EditorViews.color(context, com.google.android.material.R.attr.colorSecondary),
            EditorViews.color(context, com.google.android.material.R.attr.colorTertiary)};
    }

    /** Numerals' words, each written in its own font. */
    @Nullable
    private android.graphics.Typeface cardRowTypeface(@NonNull Row row, int index) {
        if (row != Row.NUMERALS)
            return null;
        return index == 1 ? android.graphics.Typeface.MONOSPACE
            : android.graphics.Typeface.SANS_SERIF;
    }

    /** A write for a row the card model's own switch does not name. */
    private void writeCardRow(@NonNull Row row, int value, boolean dragging) {
        TermuxAppSharedPreferences prefs = prefs();
        LayoutEditorController layout = mHost.layoutEditor();
        Context context = mHost.context();
        if (prefs == null)
            return;
        switch (row) {
            case BOTTOM_PADDING:
                if (layout != null) layout.setKeyboardChinDp(value, dragging);
                syncDirty();
                break;
            case OPACITY_LINK:
                if (prefs.isInAppKeyboardOpacityLinked() != (value != 0)) {
                    prefs.setInAppKeyboardOpacityLinked(value != 0);
                    mLinkAnchor = null;
                    syncDirty();
                }
                break;
            case HOME_COLUMNS:
            case HOME_ROWS: {
                int[] grid = layout == null ? null : layout.widgetGrid();
                if (grid == null)
                    break;
                if (row == Row.HOME_COLUMNS) grid[0] = value;
                else grid[1] = value;
                layout.setWidgetGrid(grid[0], grid[1]);
                syncDirty();
                break;
            }
            case HOME_ICON_SIZE: {
                com.termux.app.launcher.widget.HomeIconSize[] sizes =
                    com.termux.app.launcher.widget.HomeIconSize.values();
                writeHomeString(HomeSettings.KEY_ICON_SCALE,
                    sizes[clamp(value, 0, sizes.length - 1)].value);
                break;
            }
            case SHOW_WALLPAPER:
                AppearanceSettingsStore.putBoolean(context, "use_system_wallpaper", value != 0);
                settingsWritten();
                break;
            case TINT_HUE: {
                String[] values = tintHueValues();
                String id = values[clamp(value, 0, values.length - 1)];
                if (!id.equals(prefs.getSurfaceGlassTintHue()))
                    writeTintHue(id);
                break;
            }
            default:
                break;
        }
    }

    /**
     * A Settings page over the editor, opened on one of its sections or scrolled to a row, and
     * told the editor is under it (so its own door back into the editor returns here instead).
     */
    private void openSettingsFromEditor(@NonNull Class<? extends androidx.fragment.app.Fragment> page,
                                        @StringRes int title, @Nullable String section,
                                        @Nullable String scrollToKey) {
        Context context = mHost.context();
        try {
            android.content.Intent intent = SettingsActivity.createFragmentIntent(context, page,
                title, null, scrollToKey);
            intent.putExtra(SettingsActivity.EXTRA_FROM_EDITOR, true);
            if (section != null)
                intent.putExtra(SettingsActivity.EXTRA_SECTION_KEY, section);
            context.startActivity(intent);
        } catch (RuntimeException e) {
            // No Settings to open (a stripped build): the editor stays as it is.
        }
    }
}
