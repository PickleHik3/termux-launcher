package com.termux.app.x11;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.CornerHold;
import com.termux.app.chrome.CornerHoldArbiter;
import com.termux.app.chrome.CornerTabGlyphs;
import com.termux.app.chrome.CornerZones;
import com.termux.app.fragments.settings.termux.GuiAppsSetupPreferencesFragment;
import com.termux.app.terminal.PaneContentFrame;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneRim;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.app.tour.TourEdition;
import com.termux.app.wall.PaneControlsView;
import com.termux.view.HoldTiming;
import com.termux.x11.LorieView;

/**
 * The wall's Display page: the embedded X server's surface, wearing a terminal pane's rim, radius
 * and gap.
 *
 * <p>It carries the same glass slab the panes and the Widgets page do, taken from the same
 * {@link PaneSurfaceStyle}, so the Display place follows the Canvas surface with the rest of the
 * wall. The slab sits behind the display: a {@code SurfaceView} punches its own rect out of the
 * window while its surface is on screen, so the glass reads through the empty state and in the
 * band the corners leave, and a running display covers it.
 *
 * <p>The rounded corners are painted rather than clipped: a {@code SurfaceView}'s surface is
 * composited outside the view hierarchy, so no parent outline reaches it, and there is no public
 * {@code SurfaceView} corner radius at compileSdk 36 (checked, not assumed). The mask paints what
 * sits behind the page over the four arcs instead — which is also what rounds the slab, since
 * this frame cannot clip — and the surface fills the frame flush to its rim so a maximised X
 * window meets the same corners the panes have.
 *
 * <p>While no server is running the page shows its empty state, which is where a home screen
 * rests: the launcher never starts a display on its own. A hold on one of the page's four corners
 * drops the same tab a pane's corner does, out of the corner that was held: power, the
 * display's settings, and the Appearance, Layout and Wallpaper doors every place carries — and, while a
 * display runs, the scale rail along the page's leading
 * edge, which is out only while that tab is. Between the corners the edges are X's: a maximised
 * window is touchable to its rim.
 */
public final class X11PaneFrame extends PaneContentFrame {

    /**
     * Where the empty state's guide button sends the user when the message names a missing
     * package: the setup section of the Linux display guide, on GitHub rather than a wiki page,
     * since that is where the doc actually lives.
     */
    private static final String KEYBOARD_DATA_GUIDE_URL =
        "https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/X11_Display.md#turn-it-on";

    private static final int ACTION_HELP = 2;
    /** The sliders, which open Appearance; package-private so a test can find the button. */
    @androidx.annotation.VisibleForTesting static final int ACTION_EDITOR = 3;
    /** The grid beside them, which opens Layout. */
    @androidx.annotation.VisibleForTesting static final int ACTION_LAYOUT = 4;
    /** The wallpaper glyph, which opens the in-app wallpaper picker. */
    @androidx.annotation.VisibleForTesting static final int ACTION_WALLPAPER = 5;
    /** Minimal mode on or off for the Display place; the glyph shows which. */
    @androidx.annotation.VisibleForTesting static final int ACTION_MINIMAL = 6;

    /** What the page needs from the launcher. */
    public interface Host {
        /** Run the configured start command — the page's "Start display" button. */
        void startDisplay();
        /** Switch the Linux display on — the page's button while the setting is off. */
        default void turnOnDisplay() { }
        /** The power button: turn the display on, start one, or stop the one running. */
        default void toggleDisplayPower() { }
        /** The cog: open the display's settings. */
        default void openDisplaySettings() { }
        default void showHelpOverlay() {}
        /** The sliders: open the Appearance editor on this place, as every corner tab does. */
        default void openSurfaceEditor() {}
        /** The grid: open the Layout editor on this place, as every corner tab does. */
        default void openLayoutEditor() {}
        /** The wallpaper glyph: open the in-app wallpaper picker, as every corner tab does. */
        default void openWallpaperPicker() {}
        /** Whether the Display place is in minimal mode, which the tab's glyph shows. */
        default boolean isMinimalMode() { return false; }
        /** The minimal-mode glyph: turn the Display place's minimal mode on or off. */
        default void toggleMinimalMode() {}
        /**
         * True when one of the launcher's own chords claimed this key, in which case X must not
         * see it. Everything else is the display's.
         */
        default boolean consumeLauncherKey(@NonNull android.view.KeyEvent event) { return false; }
    }

    /** Told about a tap that landed on the display's own picture, or a touch that became a gesture. */
    public interface TapListener {
        /** A finger went down and came up on the display without drifting into a gesture. */
        void onDisplayTap();

        /**
         * A finger that landed on the display's picture turned into a gesture — it drifted past
         * touch slop, or a second finger joined it. Reported once per touch, the moment it does.
         */
        default void onDisplayDrag() {}
    }

    /** The touch being watched is a gesture from here: not a tap, and said so once, on the display. */
    private void noteGesture() {
        if (mWatchMoved) return;
        mWatchMoved = true;
        if (mWatchIsDisplays && mTapListener != null) mTapListener.onDisplayDrag();
    }

    /** The rest of the buttons the page's border tab carries. */
    private static final int ACTION_POWER = 0;
    private static final int ACTION_SETTINGS = 1;

    private final PaneRim mRim = new PaneRim();
    @Nullable private PaneControlsView mControls;
    @Nullable private DisplayScaleRailView mRail;
    private int mPressedAction = PaneControlsView.ACTION_NONE;
    /** A finger on the rail's thumb, from its landing to its lift. */
    private boolean mRailPressed;
    /** The corner the finger landed in, from its landing to its lift, held or not. */
    private int mPressedCorner = CornerZones.NONE;
    /** Who owns a finger down in a corner square: the display under it, or this corner. */
    private final CornerHoldArbiter mHold = new CornerHoldArbiter();
    /** True only inside {@link #cancelDisplayGesture()}: that cancel is X's, not this frame's. */
    private boolean mCancellingDisplay;
    /**
     * Its own handler rather than {@link View#postDelayed}: a detached view queues those until it
     * is attached, and the hold has to fire whether or not this page is on screen yet.
     */
    private final Handler mHoldHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHoldElapsed = this::onHoldElapsed;
    /** Whether the tab was out when the finger landed: a corner hold puts it away, or moves it. */
    private boolean mShownAtDown;
    /** And which corner it was out of, so a hold on another corner moves it rather than closing it. */
    private int mShownCornerAtDown = CornerZones.NONE;
    private boolean mTouchMoved;
    private float mDownX, mDownY;
    @Nullable private LorieView mDisplay;
    @Nullable private PaneGlassBackdropView mGlass;
    @Nullable private PaneGlassBackdropView mCornerMask;
    @Nullable private View mEmptyState;
    @Nullable private Host mHost;
    @Nullable private PaneSurfaceStyle mStyle;
    @Nullable private TapListener mTapListener;
    /** Where the finger the tap listener is watching went down, and whether it has drifted. */
    private float mWatchDownX, mWatchDownY;
    private boolean mWatchMoved;
    private boolean mWatchIsDisplays;
    private boolean mRunning;
    /** True while the wall is showing this place; the place it has left is INVISIBLE. */
    private boolean mPageOnScreen = true;
    /** The Linux display setting. Off, the page is still a place — it just says so. */
    private boolean mEnabled = true;
    /** The display's size while the frame around it animates; unset when it follows the frame. */
    private int mFrozenWidth = -1, mFrozenHeight = -1;

    public X11PaneFrame(Context context) {
        super(context);
    }

    public X11PaneFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // The display view reads its preferences while it is measured, host or no host; the
        // page is measured whether or not a display can run on it.
        com.termux.x11.LorieHost.primePrefs(getContext());
        mDisplay = findViewById(R.id.x11_display_view);
        mGlass = findViewById(R.id.x11_pane_glass);
        mCornerMask = findViewById(R.id.x11_pane_corner_mask);
        mEmptyState = findViewById(R.id.x11_pane_empty);
        // The display is not registered as the pane's content on purpose: that clearance keeps a
        // terminal's text out of the arcs, but an X screen wants to fill the frame to its rim,
        // with the corner mask painting the arcs over it - not sit as a square inside a rounded
        // one.
        PaneGlass.followLayout(mGlass);
        PaneGlass.followLayout(mCornerMask);
        View start = findViewById(R.id.x11_pane_start);
        if (start != null) start.setOnClickListener(v -> {
            if (mHost == null) return;
            if (mEnabled) mHost.startDisplay();
            else mHost.turnOnDisplay();
        });
        View guide = findViewById(R.id.x11_pane_guide);
        if (guide != null) guide.setOnClickListener(v -> openKeyboardDataGuide());
        View setup = findViewById(R.id.x11_pane_setup);
        if (setup != null) setup.setOnClickListener(v -> openDistroSetup());
        // The controls tab sits above everything, drawn only while shown; the frame itself
        // answers the taps, so the view never stands between a finger and X.
        mControls = new PaneControlsView(getContext());
        mControls.setActions(PaneControlsView.Action.glyph(ACTION_POWER, CornerTabGlyphs.POWER),
            // Minimal mode gives the display the whole screen; the mark reads the state as it
            // draws, so the same button is the way back.
            PaneControlsView.Action.drawn(ACTION_MINIMAL, com.termux.app.chrome.MinimalModeGlyph
                .mark(getContext(), () -> mHost != null && mHost.isMinimalMode())),
            PaneControlsView.Action.glyph(ACTION_SETTINGS, CornerTabGlyphs.SETTINGS),
            PaneControlsView.Action.glyph(ACTION_EDITOR, CornerTabGlyphs.APPEARANCE),
            PaneControlsView.Action.glyph(ACTION_LAYOUT, CornerTabGlyphs.LAYOUT),
            PaneControlsView.Action.glyph(ACTION_WALLPAPER, CornerTabGlyphs.WALLPAPER),
            PaneControlsView.Action.label(ACTION_HELP, CornerTabGlyphs.help(getContext())));
        mControls.setListener(id -> {
            if (mHost == null) return;
            // Help first, the tab second: help reads the ? it was opened from while it is still
            // out, and puts the tab away itself.
            if (id == ACTION_HELP) { mHost.showHelpOverlay(); dismissControls(); }
            else if (id == ACTION_EDITOR) { dismissControls(); mHost.openSurfaceEditor(); }
            else if (id == ACTION_LAYOUT) { dismissControls(); mHost.openLayoutEditor(); }
            else if (id == ACTION_WALLPAPER) { dismissControls(); mHost.openWallpaperPicker(); }
            else if (id == ACTION_POWER) mHost.toggleDisplayPower();
            else if (id == ACTION_MINIMAL) { dismissControls(); mHost.toggleMinimalMode(); }
            else if (id == ACTION_SETTINGS) mHost.openDisplaySettings();
        });
        // The scale rail comes out with the tab, along the leading edge, while a display runs.
        mRail = new DisplayScaleRailView(getContext());
        addView(mRail, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(mControls, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        applyRunning(false);
    }

    /**
     * A hold on one of the page's corners drops the controls out of it, as a hold on a pane's
     * corner does; a tap on one of them runs it, and a touch anywhere else puts them away and goes
     * on to X.
     *
     * <p>A corner square is not a button. The finger that lands in one is X's until it has rested
     * for {@link HoldTiming#holdTimeoutMs()}, so a maximised window's own corner controls answer a
     * tap there; only when the hold fires does this frame take the gesture, which is where X is
     * told to forget it.
     */
    @Override
    public boolean onInterceptTouchEvent(@NonNull android.view.MotionEvent event) {
        // The cancel the corner sends X when it claims the hold passes back through here: it is
        // for the child, and reading it as the end of the gesture would undo the claim.
        if (mCancellingDisplay) return false;
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
            return onFrameDown(event);
        // Read before the event is played: the hold is claimed by its timer rather than by a
        // touch, so an event arriving after it is the first X must not see.
        boolean claimed = mHold.isClaimed();
        onHoldEvent(event);
        return mPressedAction != PaneControlsView.ACTION_NONE || claimed || mRailPressed;
    }

    /**
     * The finger landed. The rail and the tab's own buttons are taken at once — both are controls,
     * and both are tapped — and a corner only starts its timer, leaving the gesture with X.
     *
     * @return whether this frame takes the touch from the display.
     */
    private boolean onFrameDown(@NonNull android.view.MotionEvent event) {
        cancelHold();
        mPressedAction = PaneControlsView.ACTION_NONE;
        mRailPressed = false;
        mTouchMoved = false;
        mDownX = event.getX();
        mDownY = event.getY();
        // The rail counts as out too: a touch anywhere else puts it away with the tab, and a tap
        // on the tab alone must not leave it standing.
        mShownAtDown = (mControls != null && mControls.isControlsShown())
            || (mRail != null && mRail.isRailShown());
        mShownCornerAtDown = mControls != null && mControls.isControlsShown()
            ? mControls.corner() : CornerZones.NONE;
        if (mRail != null && mRail.hits(mDownX, mDownY)) {
            mRailPressed = true;
            mRail.beginDrag(mDownY);
            return true;
        }
        if (mShownAtDown) {
            int action = mControls.actionAt(mDownX, mDownY);
            if (action != PaneControlsView.ACTION_NONE) {
                mPressedAction = action;
                return true;
            }
            dismissControls();
        }
        float slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        int corner = mHold.down(mDownX, mDownY, getWidth(), getHeight(),
            getResources().getDisplayMetrics().density, false, slop);
        if (corner == CornerZones.NONE) return false;
        mPressedCorner = corner;
        mHoldHandler.postDelayed(mHoldElapsed, HoldTiming.holdTimeoutMs());
        return false;
    }

    /**
     * One event of a gesture that started in a corner square. It arrives here from
     * {@link #onInterceptTouchEvent} while X is holding the gesture and from
     * {@link #onTouchEvent} once nothing else wants it — never both, since a frame that is
     * intercepting has no child left to dispatch to.
     */
    private void onHoldEvent(@NonNull android.view.MotionEvent event) {
        if (!mHold.isTracking()) return;
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_MOVE:
                if (mHold.move(event.getX(), event.getY()) == CornerHold.Move.ABANDONED)
                    mHoldHandler.removeCallbacks(mHoldElapsed);
                break;
            case android.view.MotionEvent.ACTION_POINTER_DOWN:
                // Two fingers on a page are a scroll or a pinch, never a hold.
                if (mHold.secondFinger()) mHoldHandler.removeCallbacks(mHoldElapsed);
                break;
            case android.view.MotionEvent.ACTION_UP:
                mHoldHandler.removeCallbacks(mHoldElapsed);
                if (mHold.lift() == CornerHold.Lift.OPEN_TAB) openHeldCornerTab();
                mPressedCorner = CornerZones.NONE;
                break;
            case android.view.MotionEvent.ACTION_CANCEL:
                cancelHold();
                break;
            default:
                break;
        }
    }

    /**
     * The hold time passed with the finger still in the square. The corner takes the gesture from
     * here: the hand is told the hold was heard, and every event from now on is this frame's.
     */
    private void onHoldElapsed() {
        if (!mHold.holdElapsed()) return;
        cancelDisplayGesture();
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    /**
     * Tell X the touch it has been tracking is over, the moment the corner claims it. A cancel is
     * the one ending that leaves nothing behind — no tap, no press of its own — which is what a
     * finger that turned out to be a corner hold owes the display. Waiting for the next event to
     * intercept is not the same thing: a finger holding still sends none.
     */
    private void cancelDisplayGesture() {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent cancel = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_CANCEL, mDownX, mDownY, 0);
        mCancellingDisplay = true;
        try {
            // Down the frame's own dispatch, so whichever child is holding the stream is the one
            // told to forget it — and the frame stops being a touch target for it in the bargain.
            dispatchTouchEvent(cancel);
        } finally {
            mCancellingDisplay = false;
            cancel.recycle();
        }
    }

    /** Out of the corner that was held; the corner it is already out of puts it away again. */
    private void openHeldCornerTab() {
        if (mControls == null || mPressedCorner == CornerZones.NONE) return;
        if (!mShownAtDown || mShownCornerAtDown != mPressedCorner) showControls(mPressedCorner);
        else dismissControls();
    }

    private void cancelHold() {
        mHoldHandler.removeCallbacks(mHoldElapsed);
        mHold.reset();
        mPressedCorner = CornerZones.NONE;
    }

    /** The square a touch may be held in, or {@link CornerZones#NONE} when it is the display's. */
    private int cornerAt(float x, float y) {
        return CornerZones.cornerAt(x, y, getWidth(), getHeight(),
            CornerZones.paneSizePx(getResources().getDisplayMetrics().density));
    }

    /**
     * What a gesture X did not want does next. The rail and the tab's buttons are pressed and
     * released here as they always were; a corner is only ever the hold's, and its events are
     * played into the same state machine {@link #onInterceptTouchEvent} feeds.
     */
    @Override
    public boolean onTouchEvent(@NonNull android.view.MotionEvent event) {
        if (mCancellingDisplay) return false;
        if (mRailPressed) return onRailTouch(event);
        if (mPressedAction == PaneControlsView.ACTION_NONE && !mHold.isTracking()) {
            return super.onTouchEvent(event);
        }
        // The down was read in onInterceptTouchEvent, which every gesture passes through first.
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) return true;
        if (mPressedAction == PaneControlsView.ACTION_NONE) {
            onHoldEvent(event);
            return true;
        }
        float slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - mDownX, event.getY() - mDownY) > slop) mTouchMoved = true;
                return true;
            case android.view.MotionEvent.ACTION_UP:
                if (mControls != null && !mTouchMoved
                    && mControls.actionAt(event.getX(), event.getY()) == mPressedAction) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                    // Help runs while the tab is still out — it reads the ? to hang its own
                    // buttons beside it — and puts the tab away itself.
                    if (mPressedAction != ACTION_HELP) dismissControls();
                    mControls.activate(mPressedAction);
                }
                mPressedAction = PaneControlsView.ACTION_NONE;
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                mPressedAction = PaneControlsView.ACTION_NONE;
                return true;
            default:
                return true;
        }
    }

    /** The thumb follows the finger stop by stop, with a tick at each; the lift applies the stop. */
    private boolean onRailTouch(@NonNull android.view.MotionEvent event) {
        if (mRail == null) {
            mRailPressed = false;
            return true;
        }
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_MOVE:
                if (mRail.dragTo(event.getY())) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
                return true;
            case android.view.MotionEvent.ACTION_UP:
                mRailPressed = false;
                mRail.dragTo(event.getY());
                applyScaleStep(mRail.endDrag());
                // Used, the rail goes away with the tab, as a tapped button does.
                dismissControls();
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                mRailPressed = false;
                mRail.cancelDrag();
                return true;
            default:
                return true;
        }
    }

    /**
     * Write the stop into the display's own store — the one its settings page and
     * {@code termux-x11-preference} write — and tell the running display, which re-lays its
     * screen at the new size at once. 100 is the screen's own pixels; the rest is the scaled mode.
     */
    private void applyScaleStep(int step) {
        com.termux.x11.Prefs prefs = prefsOrNull();
        if (prefs == null) return;
        String mode = DisplayScaleSteps.modeFor(step);
        if (DisplayScaleSteps.MODE_SCALED.equals(mode)) prefs.displayScale.put(step);
        prefs.displayResolutionMode.put(mode);
        android.content.Intent intent =
            new android.content.Intent(com.termux.x11.LoriePreferences.ACTION_PREFERENCES_CHANGED);
        intent.putExtra("key", "displayResolutionMode");
        intent.setPackage(getContext().getPackageName());
        getContext().sendBroadcast(intent);
    }

    /** The tab out of one corner, and the rail with it while a display runs. */
    private void showControls(int corner) {
        if (mControls != null) mControls.show(corner);
        if (mRail == null || !mRunning) return;
        com.termux.x11.Prefs prefs = prefsOrNull();
        if (prefs != null) {
            mRail.setStep(DisplayScaleSteps.fromPreferences(
                prefs.displayResolutionMode.get(), prefs.displayScale.get()));
        }
        mRail.show();
    }

    /** The display's store, or null on a page no host has attached to yet. */
    @Nullable
    private static com.termux.x11.Prefs prefsOrNull() {
        try {
            return com.termux.x11.LorieHost.getPrefs();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Whether the border tab is out (or coming out). */
    @androidx.annotation.VisibleForTesting
    boolean isControlsTabShown() {
        return mControls != null && mControls.isControlsShown();
    }

    /** Whether the scale rail is out (or coming out). */
    @androidx.annotation.VisibleForTesting
    boolean isScaleRailShown() {
        return mRail != null && mRail.isRailShown();
    }

    /** The tab's buttons, for a test that has to find one to tap. */
    @androidx.annotation.VisibleForTesting
    @Nullable
    PaneControlsView controlsTab() {
        return mControls;
    }

    /** The rail, for a test that has to find its track. */
    @androidx.annotation.VisibleForTesting
    @Nullable
    DisplayScaleRailView scaleRail() {
        return mRail;
    }

    /** Put the controls away, for a host that moved the wall on. */
    /**
     * The ? of the tab that is up, in screen coordinates; false when no tab is showing. The tab
     * draws its buttons rather than laying them out as views, so nothing outside can find that one.
     */
    public boolean helpButtonRectOnScreen(@NonNull android.graphics.Rect out) {
        if (mControls == null || !mControls.actionBounds(ACTION_HELP, mHelpButtonBounds)) return false;
        int[] origin = new int[2];
        mControls.getLocationOnScreen(origin);
        out.set(Math.round(mHelpButtonBounds.left) + origin[0],
            Math.round(mHelpButtonBounds.top) + origin[1],
            Math.round(mHelpButtonBounds.right) + origin[0],
            Math.round(mHelpButtonBounds.bottom) + origin[1]);
        return !out.isEmpty();
    }
    private final android.graphics.RectF mHelpButtonBounds = new android.graphics.RectF();

    /** Redraws the tab, whose minimal-mode glyph follows the place's state. */
    public void invalidateControls() {
        if (mControls != null) mControls.invalidate();
    }

    public void dismissControls() {
        if (mControls != null) mControls.dismiss();
        if (mRail != null) mRail.dismiss();
    }

    public void setHost(@Nullable Host host) {
        mHost = host;
    }

    /**
     * Watch the taps that reach the display. Nothing is taken from X: this only reports what went
     * past, so a policy on the launcher's side can tell a tap from a drag.
     */
    public void setTapListener(@Nullable TapListener listener) {
        mTapListener = listener;
    }

    /**
     * Every touch aimed at the page passes through here, so this is where a tap is recognised —
     * before the page's own controls take theirs and before the rest goes to X, neither of which
     * is changed by the watching.
     */
    @Override
    public boolean dispatchTouchEvent(@NonNull android.view.MotionEvent event) {
        watchForTap(event);
        return super.dispatchTouchEvent(event);
    }

    /**
     * A tap is one finger down and up on the display's own picture with no drift: not a drag, not
     * a two-finger gesture, and not one of the page's controls or one of its corners. The edges
     * are the display's, so a maximised window's own edge controls answer.
     */
    private void watchForTap(@NonNull android.view.MotionEvent event) {
        if (mTapListener == null) return;
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                mWatchDownX = event.getX();
                mWatchDownY = event.getY();
                mWatchMoved = false;
                boolean onControl = mControls != null && mControls.isControlsShown()
                    && mControls.actionAt(mWatchDownX, mWatchDownY) != PaneControlsView.ACTION_NONE;
                onControl |= mRail != null && mRail.hits(mWatchDownX, mWatchDownY);
                mWatchIsDisplays = touchIsTheDisplays(mRunning, onControl,
                    cornerAt(mWatchDownX, mWatchDownY));
                break;
            case android.view.MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - mWatchDownX, event.getY() - mWatchDownY)
                        > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    noteGesture();
                }
                break;
            case android.view.MotionEvent.ACTION_POINTER_DOWN:
                // A second finger makes this a gesture — a scroll, a pinch — and never a tap.
                noteGesture();
                break;
            case android.view.MotionEvent.ACTION_UP:
                boolean tap = mWatchIsDisplays && !mWatchMoved;
                mWatchIsDisplays = false;
                if (tap) mTapListener.onDisplayTap();
                break;
            case android.view.MotionEvent.ACTION_CANCEL:
                mWatchIsDisplays = false;
                break;
            default:
                break;
        }
    }

    /**
     * Whether a touch down belongs to the display's own picture: a display has to be running, the
     * page's own chrome must not want it, and it must not be in a corner. Everything else — every
     * pixel of every edge — is X's.
     */
    @androidx.annotation.VisibleForTesting
    static boolean touchIsTheDisplays(boolean running, boolean onPageChrome, int corner) {
        return running && !onPageChrome && corner == CornerZones.NONE;
    }

    @NonNull
    public LorieView display() {
        if (mDisplay == null) throw new IllegalStateException("Display page is not inflated");
        return mDisplay;
    }

    /**
     * Whether the Linux display is switched on. The page is a place on the wall either way — the
     * wall always has its three places — but off, it offers to turn the display on instead of to
     * start one.
     */
    public void applyEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!mRunning) applyRunning(false);
    }

    /**
     * Show the surface or the empty state. A server that exits leaves the page empty; it never
     * leaves a dead screen on the home screen.
     */
    public void applyRunning(boolean running) {
        mRunning = running;
        // The power glyph turns to the error colour while a display is running, as a close does.
        if (mControls != null) mControls.setActionAlert(ACTION_POWER, running);
        applyDisplaySurfaceVisibility();
        if (mEmptyState != null) mEmptyState.setVisibility(running ? GONE : VISIBLE);
        if (running) return;
        // Nothing to scale once the display is gone.
        if (mRail != null) mRail.dismiss();
        applyEmptyState();
    }

    /**
     * Re-read the keyboard-data probe and refresh the empty state from it, without waiting for a
     * running-state change. Nothing on the resume or place-change path reaches
     * {@link #applyRunning} on its own — only a display starting or stopping does — so a package
     * installed in the shell while the user was elsewhere left the message stale until the next
     * such transition. The host calls this instead, every time the user arrives at the place: on
     * resume, and when the wall settles here. A no-op while a server is running, since there is
     * no empty state to refresh.
     */
    public void refreshEmptyStateReadiness() {
        if (!mRunning) applyEmptyState();
    }

    /**
     * Apply {@link DisplayEmptyStatePolicy}'s answer to the message and the start control. The
     * probe behind it is disk I/O, so this must only ever run off a user arrival or a
     * running-state change — never a hot path.
     */
    private void applyEmptyState() {
        DisplayEmptyStatePolicy.State state = DisplayEmptyStatePolicy.decide(mEnabled,
            X11CliInstaller.hasKeyboardData(), TourEdition.of(getContext().getPackageName()));
        View message = findViewById(R.id.x11_pane_empty_message);
        if (message instanceof android.widget.TextView) {
            ((android.widget.TextView) message).setText(state.messageRes);
        }
        View start = findViewById(R.id.x11_pane_start);
        if (start instanceof android.widget.TextView) {
            ((android.widget.TextView) start).setText(mEnabled
                ? R.string.termux_x11_start_display : R.string.termux_x11_turn_on);
        }
        if (start != null) start.setVisibility(state.startVisible ? VISIBLE : GONE);
        View guide = findViewById(R.id.x11_pane_guide);
        if (guide != null) guide.setVisibility(state.guideVisible() ? VISIBLE : GONE);
        applySetupOffer(state);
    }

    /**
     * D9's offer, decided from the same arrival this method already runs on. The reading behind it
     * is a directory listing and a handful of stats per container ({@link DistroSetup}), which is
     * the same order of cost as the keyboard-data probe beside it — and it is deliberately not
     * cached, because the one moment it must be right is the arrival straight after a setup run
     * finished in a pane.
     */
    private void applySetupOffer(@NonNull DisplayEmptyStatePolicy.State state) {
        View setup = findViewById(R.id.x11_pane_setup);
        if (setup == null) return;
        boolean offer;
        try {
            offer = DistroSetupStore.shouldOffer(DistroSetup.read(),
                new DistroSetupStore(getContext()).dismissed(), state.resting());
        } catch (RuntimeException e) {
            // Nothing about a home screen's empty state is worth a crash; no offer is the safe
            // answer, and the next arrival reads again.
            offer = false;
        }
        setup.setVisibility(offer ? VISIBLE : GONE);
    }

    /**
     * The offer's own tap: the Get GUI apps screen in Settings, which is where every choice about
     * this now lives.
     *
     * <p>Opening the screen is deliberately <em>not</em> remembered as an answer. A user who looks
     * at it and comes back without copying anything has done nothing about their Linux, and an
     * offer that vanished on the way to the screen would have taken itself away at the one moment
     * it was still needed. The screen records the dismissal itself when the command is copied,
     * which is the moment the user actually has what the offer was for.
     */
    private void openDistroSetup() {
        try {
            getContext().startActivity(GuiAppsSetupPreferencesFragment.intent(getContext())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            // A home screen's empty state is not worth a crash; the row in Settings is still there.
            return;
        }
        applyEmptyState();
    }

    /** The empty state's guide button: the setup section of the Linux display guide. */
    private void openKeyboardDataGuide() {
        try {
            android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_VIEW, android.net.Uri.parse(KEYBOARD_DATA_GUIDE_URL));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            com.termux.app.notice.AppNotice.show(getContext(), KEYBOARD_DATA_GUIDE_URL, true);
        }
    }

    /**
     * The display's surface is only ever on screen while a display runs <em>and</em> the wall is
     * showing this place.
     *
     * <p>A {@code SurfaceView}'s surface is composited outside the view hierarchy, and it follows
     * that view's own visibility alone: an {@code INVISIBLE} ancestor — which is exactly how the
     * wall parks the place it is not showing — never reaches it. The surface is then kept off
     * screen only by the render thread, which moves and hides it as the page's render node is
     * drawn; a page that is not drawn at all has no such frame, so any surface pass that runs
     * while the place is parked (the page is re-laid-out behind the wall, the server comes up, a
     * geometry pass resizes the stack) puts the surface back on screen at the page's own layout
     * position — which is the terminal pane's rect, since every place is laid out in the same
     * frame. Handing the place's visibility down to the display view instead tears the surface
     * down for real, which is what {@code syncDisplayPageAttachment} means by a hidden page
     * holding no screen-sized buffer.</p>
     */
    private void applyDisplaySurfaceVisibility() {
        if (mDisplay != null) mDisplay.setVisibility(displaySurfaceVisibility());
    }

    /** The visibility the display view carries for the page's current state. */
    int displaySurfaceVisibility() {
        return mRunning && mPageOnScreen ? VISIBLE : INVISIBLE;
    }

    /**
     * The wall's own visibility for this place reaches the frame here — its pages are moved and
     * hidden, never re-attached — and the window's is left to the display view, which follows
     * that one itself.
     */
    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncPageOnScreen();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // A place can be added to the wall while the wall is resting on another one.
        syncPageOnScreen();
    }

    private void syncPageOnScreen() {
        // Before the wall has placed the frame there is nothing to hide from.
        boolean onScreen = !isAttachedToWindow() || isShown();
        if (mPageOnScreen == onScreen) return;
        mPageOnScreen = onScreen;
        applyDisplaySurfaceVisibility();
    }

    public boolean isRunning() {
        return mRunning;
    }

    /**
     * The frame is about to change height over several frames (the status bar expanding or
     * folding). The display keeps the size it has, pinned to the page's bottom edge, so the X
     * screen is resized once at the end instead of on every frame; until then the bar simply
     * covers it.
     */
    public void beginHostResize() {
        if (mDisplay == null || mDisplay.getWidth() <= 0 || mDisplay.getHeight() <= 0) return;
        if (mFrozenWidth < 0) {
            mFrozenWidth = mDisplay.getWidth();
            mFrozenHeight = mDisplay.getHeight();
        }
    }

    /** The frame has its final height: the display takes it, in one resize. */
    public void finishHostResize() {
        if (mFrozenWidth < 0) return;
        mFrozenWidth = -1;
        mFrozenHeight = -1;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mFrozenWidth > 0 && mDisplay != null) {
            mDisplay.measure(MeasureSpec.makeMeasureSpec(mFrozenWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mFrozenHeight, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mFrozenWidth > 0 && mDisplay != null) {
            // Where the frame put it, but at its frozen size and hanging from the bottom edge:
            // the picture stays exactly where it was on screen while the bar moves over it.
            int l = mDisplay.getLeft();
            int b = mDisplay.getBottom();
            mDisplay.layout(l, b - mFrozenHeight, l + mFrozenWidth, b);
        }
    }

    /**
     * The wall moved this page, or the wallpaper panned under it: the slab and the corner arcs
     * re-aim at whatever is behind them now. Per frame of a slide, so nothing here but invalidates.
     */
    public void onWallMoved() {
        if (mGlass != null) mGlass.invalidateGlassPosition();
        if (mCornerMask != null) mCornerMask.invalidateGlassPosition();
        if (mControls != null && mControls.hasPaneGlass()) mControls.invalidate();
    }

    /**
     * Dress the page from the surface style — the same values the panes beside it read, so the
     * Display page follows the Canvas surface with the rest of the wall.
     */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        mStyle = style;
        boolean glass = PaneGlass.isActive(style);
        float radiusPx = glass
            ? PaneGlass.radiusPx(style, getResources().getDisplayMetrics().density) : 0f;
        // The page's own slab, fed exactly as a pane's and the Widgets page's are. It stays
        // dressed while a display runs — the surface simply covers it — so the page has the glass
        // it should the moment the display stops, without a pass of its own to run then.
        PaneGlass.apply(style, this, mGlass, radiusPx);
        // The tab is part of the page's own outline, exactly as the Widgets page's is: flush in the
        // corner inside the rim's line, in the tab's own fixed material. It stays inside that line
        // rather than against the bounding box, where the page's own corner mask would paint the
        // wall over its outer corner and it would read as hanging out past the rounded edge.
        if (mControls != null) {
            mControls.setPaneBorder(radiusPx, glass
                ? com.termux.app.GlassRimRenderer.strokePx(
                    getResources().getDisplayMetrics().density) : 0f);
            PaneGlass.dressTab(style, mControls);
        }
        // The frame must not clip to its shape here: the mask's arcs lie exactly outside the
        // rounded outline, so a clipping frame cut away the very paint that rounds the surface,
        // which no clip reaches. The slab is square for the same reason and the mask rounds it,
        // and the rim draws the rounded edge whichever way the frame is set.
        setPaneShape(radiusPx, false);
        // Nothing keeps X windows out of the arcs: like any rounded desktop, the display clips
        // and a terminal that minds its corners pads its own window (kitty's
        // window_padding_width). Reserving the clearance in the window manager instead cost
        // every GUI app its flush edges for the sake of an unpadded terminal's corner glyphs.
        if (mCornerMask != null) {
            // The arcs are the page's corners in both states — over the display's surface and
            // over the slab behind its empty state — so the mask follows the radius, not the
            // server. With no radius there is no arc to paint and a mask left showing would
            // paint the wall over the whole page.
            mCornerMask.setVisibility(radiusPx > 0f ? VISIBLE : GONE);
            mCornerMask.setCornerMaskRadius(radiusPx);
            // Behind the page is the wallpaper (or the surface base colour), never a pane's own
            // frost, so the mask is fed the frame and nothing else.
            if (style != null) {
                // What the wall shows between its panes: the wallpaper itself, unblurred and
                // unfrosted - the panes' frost belongs to their slabs, and in a small arc it read
                // as a flat swatch of colour - or the flat base colour when no wallpaper is
                // behind. The arcs are never left open, or the surface would show square.
                // Only a mask that actually took new paint needs re-aiming: every chrome apply
                // comes through here, and the page's own layout listener re-aims it when it moves.
                if (mCornerMask.setGlass(style.wallBehindFrame(), style.paneGlassBlurFrameRect(),
                    android.graphics.Color.TRANSPARENT, null, 0, radiusPx, null)) {
                    mCornerMask.invalidateGlassPosition();
                }
                // The arcs stand in for the wall behind the page, and the wall pans.
                mCornerMask.setParallax(style.wallpaperParallax());
                mCornerMask.setCornerMaskFallbackColor(style.wallBehindColor());
            }
        }
        if (glass) mRim.apply(this, true, radiusPx, true);
        else mRim.clear(this);
    }

    /**
     * Every key aimed at the page passes through here on its way to the display's own view, so
     * this is where the launcher gets first refusal. It takes only its own chords; the rest of
     * the keyboard belongs to whatever is running on the display.
     */
    @Override
    public boolean dispatchKeyEvent(@NonNull android.view.KeyEvent event) {
        if (mHost != null && mHost.consumeLauncherKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelHold();
        mRim.cancel();
    }
}
