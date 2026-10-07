package com.termux.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Scroller;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.termux.terminal.KeyHandler;
import com.termux.terminal.KittyKeyEncoder;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalLinks;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.textselection.TextSelectionCursorController;

import java.util.Objects;

/**
 * View displaying and interacting with a {@link TerminalSession}.
 */
public final class TerminalView extends View {

    /**
     * Log terminal view key and IME events.
     */
    private static boolean TERMINAL_VIEW_KEY_LOGGING_ENABLED = false;

    /**
     * The currently displayed terminal session, whose emulator is {@link #mEmulator}.
     */
    public TerminalSession mTermSession;

    /**
     * Our terminal emulator whose session is {@link #mTermSession}.
     */
    public TerminalEmulator mEmulator;

    public TerminalRenderer mRenderer;

    /**
     * Whether this pane's cursor trail listener is turned on: the preference and the device's
     * power state, folded together by whoever installed the listener. Kept here rather than only
     * forwarded, so a listener installed after the policy already ran (a pane created mid-session)
     * still learns the current state instead of defaulting to on.
     */
    private boolean mCursorTrailEnabled = true;

    /**
     * Told about this pane's cursor every frame it might have moved, and about the discontinuities
     * — session switch, resize, scroll or prompt jump — that must not be smeared across. Owned by
     * whoever composes the panes, since the trail itself is drawn above all of them, not by any one
     * view; see {@link com.termux.view.TerminalView.CursorTrailListener}.
     */
    @Nullable private CursorTrailListener mCursorTrailListener;

    /** Per-pane timing counters; recording uses only primitive fields and fixed arrays. */
    private final TerminalRenderMetrics mRenderMetrics = new TerminalRenderMetrics();

    /**
     * A sink for what key input actually reached the shell, for the app's key inspector.
     * <p>
     * The point of reporting it from here is that this is where the encoders are chosen, so a
     * diagnostic sees what was really written rather than a second guess at it.
     * </p>
     */
    public interface KeyInputProbe {

        /**
         * @param encoder which encoder produced the bytes: "kitty", "keyhandler" or "text".
         * @param bytes   what was written to the shell.
         */
        void onKeyBytesWritten(String encoder, String bytes);
    }

    /** Null in normal use; set only while the key inspector is open. */
    @Nullable
    private KeyInputProbe mKeyInputProbe;

    public TerminalViewClient mClient;

    private boolean mUseTransparentFrameClear;
    /**
     * A split-pane drag may change this view's Android bounds many times per second. Sending every
     * intermediate geometry to the PTY generates a SIGWINCH storm and makes interactive shells
     * repaint their prompt repeatedly. The pane controller pauses those updates for the duration
     * of the gesture and commits the final rows/columns once the drag is released.
     */
    private boolean mTerminalSizeUpdatesPaused;
    private boolean mTerminalSizeUpdatePending;
    /**
     * The pane wall's slide moves the keyboard over or off this view's bottom edge while the grid
     * keeps its rows until the settle (ADR 0003). From {@link #beginTravelDisplacement} to the
     * resize that ends it, the grid is drawn from where it stood when the travel began — the
     * view's top does not move during a slide, only its bottom — displaced toward where that
     * resize will put it, so the resize lands on rows that are already there.
     */
    private boolean mTravelActive;
    /** The vertical content offset and the height the grid was drawn at when the travel began. */
    private float mTravelAnchorOffsetPx;
    private int mTravelAnchorHeightPx;
    /** How far down the grid is drawn from the anchor this frame; negative is up. */
    private float mTravelDisplacementPx;
    /**
     * How many rows the settle's resize will bring into view above the rows on screen: the
     * transcript a growth reveals over a bottom-anchored grid, 0 for a shrink and on the
     * alternate screen. The travel draws them already, in the room the view is gaining.
     */
    private int mTravelRevealRows;
    /** What the centring slack above the grid changes by at the settle, and how far the travel is. */
    private float mTravelHeadroomChangePx;
    private float mTravelProgress;
    /** Whether the reflow that ends this travel is to be frosted; set by the settle. */
    private boolean mFrostOnTravelReflow;
    /** Whether this travel is frosted throughout ({@link #holdTravelFrost}), to thaw as it ends. */
    private boolean mTravelFrostHeld;
    /** The frost's change, while one runs (API 31+), and how deep it stands, 0 to 1. */
    @Nullable private ValueAnimator mReflowFrost;
    private float mReflowFrostLevel;
    /** How long the frost over a travel's reflow takes to thaw. */
    static final long REFLOW_FROST_MS = 180L;
    /** How long a held frost takes to come in. */
    static final long TRAVEL_FROST_IN_MS = 100L;
    /** Per-instance instrumentation seam; production leaves this null. */
    public interface SizeUpdateObserver { void onUpdateSize(TerminalView view); }
    private SizeUpdateObserver mSizeUpdateObserver;
    private int mTransparentFrameOverlayColor;

    /**
     * Ghostty's {@code window-padding-color = extend}, ported to a pane: whether the empty band
     * between the text grid and the view's own edge is painted with the background colour of the
     * nearest edge cell, rather than left to show whatever is behind the pane. Off leaves the band
     * fully transparent, which is what a plain shell wants (the glass or wallpaper behind the pane
     * keeps showing through); on, a full-screen app that paints its own background — opencode's
     * black, for instance — reaches all the way to the pane's rounded border.
     */
    private boolean mPaddingFillEnabled;
    /** Reused across frames; a fresh Paint per draw would be an allocation onDraw cannot afford. */
    private final Paint mPaddingFillPaint = new Paint();
    /** How many columns/rows the edge-colour arrays below currently describe; 0 while disabled or
     *  before the first frame with an emulator attached. */
    private int mEdgeColorsColumns;
    private int mEdgeColorsRows;
    /** One colour per column, taken from the top and bottom visible rows; 0 (transparent) wherever
     *  that cell's background is the terminal's own default. Reused across frames and only grown,
     *  never reallocated on every draw. */
    private int[] mEdgeTopColors = new int[0];
    private int[] mEdgeBottomColors = new int[0];
    /** One colour per row, taken from the first and last column of each visible row. */
    private int[] mEdgeLeftColors = new int[0];
    private int[] mEdgeRightColors = new int[0];
    /** The previous frame's arrays, kept only to tell whether this frame's colours actually moved —
     *  a blinking cursor or a spinner glyph redraws every frame without changing any of them, and
     *  the pane frame outside this view must not be asked to repaint its own band for that. */
    private int[] mPrevEdgeTopColors = new int[0];
    private int[] mPrevEdgeBottomColors = new int[0];
    private int[] mPrevEdgeLeftColors = new int[0];
    private int[] mPrevEdgeRightColors = new int[0];
    /** Told when this frame's edge colours differ from the last frame's. Null in normal use; the
     *  pane frame around this view sets itself once it knows its content is a terminal. */
    @Nullable
    private PaddingFillListener mPaddingFillListener;

    /** See {@link #mPaddingFillListener}. */
    public interface PaddingFillListener {
        void onPaddingFillColorsChanged();
    }

    private TextSelectionCursorController mTextSelectionCursorController;

    /**
     * Whether the pane wall keeps this view's page fully off screen (see
     * {@code PaneWallLayout#applyPagePositions}). That page's Terminal is never {@code INVISIBLE}
     * any more, only faded with {@code alpha}, so nothing in the view hierarchy notices it leaving
     * on its own — this view is told directly instead, and pauses everything INVISIBLE used to
     * pause for free: kitty animations, the cursor blinker's invalidates, focus and accessibility.
     */
    private boolean mWallOffScreen;

    private Handler mTerminalCursorBlinkerHandler;

    private TerminalCursorBlinkerRunnable mTerminalCursorBlinkerRunnable;

    private int mTerminalCursorBlinkerRate;

    private boolean mCursorInvisibleIgnoreOnce;

    public static final int TERMINAL_CURSOR_BLINK_RATE_MIN = 100;

    public static final int TERMINAL_CURSOR_BLINK_RATE_MAX = 2000;

    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0.
     */
    int mTopRow;

    int[] mDefaultSelectors = new int[] { -1, -1, -1, -1 };

    /** Find-session highlights, copy-mode cursor and selection, or null when no find is running. */
    @Nullable private TerminalFindOverlay mFindOverlay;

    float mScaleFactor = 1.f;

    /**
     * Per-event scale factor deviation from 1 below which {@link #mScaleFactor} is left alone.
     * A two-finger scroll drag has near-constant span between fingers, but hand tremor still
     * makes the scale detector report a scale factor that isn't exactly 1 each frame; without
     * this the jitter is read as a deliberate pinch and changes the font size while scrolling.
     * A real pinch changes span by much more than this per frame.
     */
    private static final float SCALE_JITTER_THRESHOLD = 0.015f;

    final GestureAndScaleRecognizer mGestureRecognizer;

    /**
     * Keep track of where mouse touch event started which we report as mouse scroll.
     */
    private int mMouseScrollStartX = -1, mMouseScrollStartY = -1;

    /**
     * Keep track of the time when a touch event leading to sending mouse scroll events started.
     */
    private long mMouseStartDownTime = -1;

    final Scroller mScroller;

    /**
     * What was left in from scrolling movement.
     */
    float mScrollRemainder;
    float mScrollXRemainder;

    /**
     * How far into {@link #mTopRow}'s row the transcript is scrolled, in pixels, in [0, line spacing).
     * The logical scroll position stays row based; this is only applied when drawing, which is what
     * makes transcript scrolling move per pixel instead of per row.
     */
    private float mScrollOffsetPixels;

    private boolean mSmoothFlingActive;

    private boolean mSmoothSettleActive;

    private static final int SCROLL_SETTLE_DURATION_MS = 120;

    /**
     * Set while a finger drag is being reported to an application which asked for motion reporting,
     * during which the drag must not also scroll the view.
     */
    private boolean mTouchMouseDragActive;

    /**
     * Set for the remainder of a gesture whose motion was reported as a mouse drag, so that the tap
     * and fling handling of that same gesture does not add events of its own.
     */
    private boolean mTouchMouseDragReported;

    /**
     * Shift was held (or latched) at this tap, so it is the app's own: a mouse-tracking program
     * gets no click for it and the link under the finger opens instead, the xterm convention.
     * Decided once at touch-up, because reading the latch consumes it.
     */
    private boolean mTapShiftBypass;
    /**
     * The link under the finger when it lifted for what may be a tap. Read at the lift, offered at
     * the confirmation 300 ms later, by which time a program may have redrawn the screen.
     */
    private TerminalLinks.Link mTapLink;
    /** Whether a tap reads addresses out of the text, or only OSC 8 hyperlinks. */
    private boolean mUrlTapEnabled;

    /** Modifier bits of the left press in flight, so its motion and release carry the same ones. */
    private int mMousePressModifiers;

    /** The axis a finger drag scrolls along, decided once it has travelled; reset at each down. */
    private int mScrollAxis;
    private static final int SCROLL_AXIS_UNDECIDED = 0;
    private static final int SCROLL_AXIS_VERTICAL = 1;
    private static final int SCROLL_AXIS_HORIZONTAL = 2;

    /**
     * The axis this drag scrolls along. A mouse's own scrolling is left alone; a finger's is locked
     * to whichever way it had travelled further when it first moved past the touch slop.
     */
    private int scrollAxisFor(MotionEvent event) {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return SCROLL_AXIS_UNDECIDED;
        if (mScrollAxis == SCROLL_AXIS_UNDECIDED) {
            float dx = Math.abs(event.getX() - mTouchDownX);
            float dy = Math.abs(event.getY() - mTouchDownY);
            if (dx * dx + dy * dy > (float) mTouchSlop * mTouchSlop)
                mScrollAxis = dx > dy ? SCROLL_AXIS_HORIZONTAL : SCROLL_AXIS_VERTICAL;
        }
        return mScrollAxis;
    }

    private int mTouchMouseDragLastCol, mTouchMouseDragLastRow;

    /**
     * Mouse mode: every touch is the mouse, for a program that asked for one. A finger down is
     * the left button down at its cell, a move is the button held and moved, a lift is the
     * release; two fingers turn the wheel, one notch per line of travel in the natural direction
     * - the content follows the fingers - and a fast lift lets it run on, as the transcript
     * does. A program that is not tracking the mouse gets none of this typed at it: a finger
     * does nothing and two fingers scroll the transcript instead. None of the view's own touch
     * behaviour - dragging the transcript, selecting text, the long-press menu - applies while
     * it is on, and it all comes back when it is off.
     */
    private boolean mTouchMouseMode;

    /** The pointer shape a running program asked for with OSC 22, or null for the usual one. */
    @Nullable private String mRequestedPointerShape;

    /**
     * Set by the chrome above this view while a finger that landed in one of its pane corner
     * squares is down: that hold belongs to the corner and opens its tab, so the view never starts
     * a hold of its own for it. Taps and drags there still reach the view as usual.
     * See {@link HoldTiming} for why the two holds must never race.
     */
    private boolean mHoldExempt;
    /** Mouse mode's left button, and whether its press is still waiting on a pane corner. */
    private final MouseModePress mTouchMouseModePress = new MouseModePress();
    private int mTouchMouseModeLastCol, mTouchMouseModeLastRow;
    /** Two fingers are down and their travel is the wheel. */
    private boolean mTouchMouseWheelActive;
    private float mTouchMouseWheelStartY;
    private int mTouchMouseWheelSent;
    private int mTouchMouseWheelCol, mTouchMouseWheelRow;
    private android.view.VelocityTracker mTouchMouseWheelVelocity;
    /** Carries the wheel on after a fast two-finger lift; its axis is finger travel in pixels. */
    private Scroller mTouchMouseWheelFling;
    private int mTouchMouseWheelFlingSent;
    private final Runnable mTouchMouseWheelFlingStep = new Runnable() {
        @Override
        public void run() {
            if (mTouchMouseWheelFling == null || mEmulator == null) return;
            if (!mTouchMouseWheelFling.computeScrollOffset()) return;
            int notches = Math.round(mTouchMouseWheelFling.getCurrY() / touchMouseWheelNotchPx());
            turnWheel(notches - mTouchMouseWheelFlingSent, mTouchMouseWheelCol, mTouchMouseWheelRow);
            mTouchMouseWheelFlingSent = notches;
            if (!mTouchMouseWheelFling.isFinished()) postOnAnimation(this);
        }
    };

    private final int mTouchSlop;

    /** Where the current gesture's finger landed, which is where a still finger clicks. */
    private float mTouchDownX, mTouchDownY;

    /** Whether this gesture has actually scrolled anything; see {@link TapPrecision.ScrollDelivery}. */
    private final TapPrecision.ScrollDelivery mScrollDelivery = new TapPrecision.ScrollDelivery();

    /**
     * The hold, which is this view's own and no longer the stock long press: see {@link HoldGesture}.
     * The gesture detector is left with taps, scrolls, flings, double taps and the pinch.
     */
    private final HoldGesture mHoldGesture = new HoldGesture();

    /** Set for the rest of a gesture the hold took over, so the tap and fling paths let it be. */
    private boolean mHoldConsumedGesture;

    /** The finger's landing, kept so the hold has an event to start text selection from. */
    private MotionEvent mHoldDownEvent;

    private final Runnable mHoldRunnable = new Runnable() {

        @Override
        public void run() {
            boolean mouseTracking = mEmulator != null && mRenderer != null
                && mEmulator.isMouseTrackingActive();
            applyHoldOutcome(mHoldGesture.holdElapsed(mouseTracking, isTouchMouseDragReportingEnabled()));
        }
    };

    /** The hold's second stage: a finger that never moved goes on to select text. */
    private final Runnable mHoldSelectRunnable = new Runnable() {

        @Override
        public void run() {
            applyHoldOutcome(mHoldGesture.selectElapsed());
        }
    };

    /**
     * If non-zero, this is the last unicode code point received if that was a combining character.
     */
    int mCombiningAccent;

    private char mSplitChar = ' ';

    /**
     * The current AutoFill type returned for {@link View#getAutofillType()} by {@link #getAutofillType()}.
     *
     * The default is {@link #AUTOFILL_TYPE_NONE} so that AutoFill UI, like toolbar above keyboard
     * is not shown automatically, like on Activity starts/View create. This value should be updated
     * to required value, like {@link #AUTOFILL_TYPE_TEXT} before calling
     * {@link AutofillManager#requestAutofill(View)} so that AutoFill UI shows. The updated value
     * set will automatically be restored to {@link #AUTOFILL_TYPE_NONE} in
     * {@link #autofill(AutofillValue)} so that AutoFill UI isn't shown anymore by calling
     * {@link #resetAutoFill()}.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private int mAutoFillType = AUTOFILL_TYPE_NONE;

    /**
     * The current AutoFill type returned for {@link View#getImportantForAutofill()} by
     * {@link #getImportantForAutofill()}.
     *
     * The default is {@link #IMPORTANT_FOR_AUTOFILL_NO} so that view is not considered important
     * for AutoFill. This value should be updated to required value, like
     * {@link #IMPORTANT_FOR_AUTOFILL_YES} before calling {@link AutofillManager#requestAutofill(View)}
     * so that Android and apps consider the view as important for AutoFill to process the request.
     * The updated value set will automatically be restored to {@link #IMPORTANT_FOR_AUTOFILL_NO} in
     * {@link #autofill(AutofillValue)} by calling {@link #resetAutoFill()}.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private int mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;

    /**
     * The current AutoFill hints returned for {@link View#getAutofillHints()} ()} by {@link #getAutofillHints()} ()}.
     *
     * The default is an empty `string[]`. This value should be updated to required value. The
     * updated value set will automatically be restored an empty `string[]` in
     * {@link #autofill(AutofillValue)} by calling {@link #resetAutoFill()}.
     */
    private String[] mAutoFillHints = new String[0];

    private final AccessibilityManager mAccessibilityManager;

    /** Text-changed events for a screen reader: only while one reads, and folded per burst. */
    private final AccessibilityTextUpdates mAccessibilityTextUpdates =
        new AccessibilityTextUpdates(new AccessibilityTextUpdates.Host() {
            @Override
            public void postDelayed(Runnable action, long delayMs) {
                TerminalView.this.postDelayed(action, delayMs);
            }

            @Override
            public void removeCallbacks(Runnable action) {
                TerminalView.this.removeCallbacks(action);
            }

            @Override
            public void sendTextChanged() {
                // The service reads the text it is handed in onPopulateAccessibilityEvent.
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
            }
        });

    private final AccessibilityManager.AccessibilityStateChangeListener mAccessibilityStateListener =
        enabled -> refreshAccessibilityServiceState();

    private final AccessibilityManager.TouchExplorationStateChangeListener mTouchExplorationListener =
        enabled -> refreshAccessibilityServiceState();

    /**
     * The {@link KeyEvent} is generated from a virtual keyboard, like manually with the {@link KeyEvent#KeyEvent(int, int)} constructor.
     */
    // -1
    public final static int KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD = KeyCharacterMap.VIRTUAL_KEYBOARD;

    /**
     * The {@link KeyEvent} is generated from a non-physical device, like if 0 value is returned by {@link KeyEvent#getDeviceId()}.
     */
    public final static int KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0;

    private static final String LOG_TAG = "TerminalView";

    public TerminalView(Context context, AttributeSet attributes) {
        // NO_UCD (unused code)
        super(context, attributes);
        // The grid is columns, not reading order: column 0 is on the left in every locale, and
        // right-to-left text inside it is the program's business (bidi is not done here).
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        mGestureRecognizer = new GestureAndScaleRecognizer(context, new GestureAndScaleRecognizer.Listener() {

            @Override
            public boolean onUp(MotionEvent event) {
                mScrollRemainder = 0.0f;
                mScrollXRemainder = 0.0f;
                if (mScroller.isFinished())
                    settleScrollOffset();
                mTapLink = null;
                if (mTouchMouseDragReported)
                    return true;
                if (mHoldConsumedGesture)
                    return true;
                if (isSelectingText() || mScrollDelivery.delivered())
                    return false;
                mTapLink = linkUnderTap(event);
                if (mEmulator != null && mRenderer != null && mEmulator.isMouseTrackingActive() && !event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    if (takeShiftForTap(event)) {
                        // Shift bypasses the program: no click goes to it, and the confirmed tap
                        // that follows offers whatever link is there.
                        mTapShiftBypass = true;
                        return false;
                    }
                    // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                    // for zooming.
                    float[] at = TapPrecision.clickPointFor(mTouchDownX, mTouchDownY, event.getX(),
                        event.getY(), mRenderer.mFontLineSpacing);
                    sendClickAt(getColumnForX(at[0]), getRowForY(at[1]), event);
                    mClient.onMouseTrackingTap(event);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (mEmulator == null)
                    return true;
                // A hold that started this very selection must not be read as the tap that ends it.
                if (mHoldConsumedGesture)
                    return true;
                if (isSelectingText()) {
                    stopTextSelectionMode();
                    return true;
                }
                requestFocus();
                TerminalLinks.Link link = mTapLink;
                mTapLink = null;
                if (mEmulator.isMouseTrackingActive()) {
                    // The program already got this tap as its click (see onUp), unless Shift held
                    // it back; either way a link under the finger is still offered, as in a shell.
                    mTapShiftBypass = false;
                    if (link != null) mClient.onLinkTap(link, event);
                    return true;
                }
                if (link != null) {
                    mClient.onLinkTap(link, event);
                    return true;
                }
                mClient.onSingleTapUp(event);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e, float distanceX, float distanceY) {
                if (mEmulator == null)
                    return true;
                if (mTouchMouseDragActive)
                    return true;
                // After a hold a drag is a mouse drag or a selection handle, never a scroll.
                if (mHoldConsumedGesture)
                    return true;
                if (mEmulator.isMouseTrackingActive() && e.isFromSource(InputDevice.SOURCE_MOUSE) && !mTapShiftBypass) {
                    // If moving with mouse pointer while pressing button, report that instead of scroll.
                    // This means that we never report moving with button press-events for touch input,
                    // since we cannot just start sending these events without a starting press event,
                    // which we do not do for touch input, only mouse in onTouchEvent().
                    sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                } else {
                    // A finger drag scrolls along one axis, the one it started out along. A thumb
                    // never travels straight, and without this every few pixels of sideways drift
                    // on a vertical drag went to a mouse-tracking program as a sideways wheel
                    // notch between the vertical ones - which many programs read as the wheel
                    // turning the other way, so the scroll stalled or jittered.
                    int axis = scrollAxisFor(e);
                    if (axis == SCROLL_AXIS_HORIZONTAL) distanceY = 0f;
                    if (axis == SCROLL_AXIS_VERTICAL) distanceX = 0f;
                    if (isSmoothScrollAllowed()) {
                        abortSmoothScroll();
                        float before = getScrollPixelPosition();
                        setScrollPixelPosition(before + distanceY);
                        mScrollDelivery.pixelsScrolled(before, getScrollPixelPosition());
                        invalidate();
                    } else {
                        distanceY += mScrollRemainder;
                        int deltaRows = (int) (distanceY / mRenderer.mFontLineSpacing);
                        mScrollRemainder = distanceY - deltaRows * mRenderer.mFontLineSpacing;
                        mScrollDelivery.rowsScrolled(deltaRows);
                        doScroll(e, deltaRows);
                    }

                    distanceX += mScrollXRemainder;
                    int deltaCols = (int) (distanceX / mRenderer.mFontWidth);
                    mScrollXRemainder = distanceX - deltaCols * mRenderer.mFontWidth;
//mClient.logError("scrolll", distanceY, distanceX);
                    mScrollDelivery.columnsScrolled(deltaCols);
                    doScrollX(e, deltaCols);
                }
                return true;
            }

            @Override
            public boolean onScale(float focusX, float focusY, float scale) {
                if (mEmulator == null || isSelectingText())
                    return true;
                if (Math.abs(scale - 1f) < SCALE_JITTER_THRESHOLD)
                    return true;
                mScaleFactor *= scale;
                mScaleFactor = mClient.onScale(mScaleFactor);
                return true;
            }

            @Override
            public boolean onFling(final MotionEvent e2, float velocityX, float velocityY) {
                if (mEmulator == null)
                    return true;
                if (mTouchMouseDragReported)
                    return true;
                if (mHoldConsumedGesture)
                    return true;
                // Do not start scrolling until last fling has been taken care of:
                if (!mScroller.isFinished())
                    return true;
                // A sideways swipe is not a vertical fling, however much it drifted.
                if (mScrollAxis == SCROLL_AXIS_HORIZONTAL && !e2.isFromSource(InputDevice.SOURCE_MOUSE))
                    return true;
                final boolean mouseTrackingAtStartOfFling = mEmulator.isMouseTrackingActive();
                if (isSmoothScrollAllowed()) {
                    mSmoothFlingActive = true;
                    mScroller.fling(0, Math.round(getScrollPixelPosition()), 0, -(int) velocityY, 0, 0,
                        getScrollPixelMinimum(), 0);
                    postOnAnimation(mSmoothScrollRunnable);
                    return true;
                }
                float SCALE = 0.25f;
                if (mouseTrackingAtStartOfFling) {
                    mScroller.fling(0, 0, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.mRows / 2, mEmulator.mRows / 2);
                } else {
                	//this doesn't fling in less
                    mScroller.fling(0, mTopRow, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.getScreen().getActiveTranscriptRows(), 0);
                }
                postOnAnimation(new Runnable() {

                    private int mLastY = 0;

                    @Override
                    public void run() {
                        if (mouseTrackingAtStartOfFling != mEmulator.isMouseTrackingActive()) {
                            mScroller.abortAnimation();
                            return;
                        }
                        if (mScroller.isFinished())
                            return;
                        boolean more = mScroller.computeScrollOffset();
                        int newY = mScroller.getCurrY();
                        int diff = mouseTrackingAtStartOfFling ? (newY - mLastY) : (newY - mTopRow);
                        doScroll(e2, diff);
                        mLastY = newY;
                        if (more)
                            postOnAnimation(this);
                    }
                });
                return true;
            }

            @Override
            public boolean onDown(float x, float y) {
                // Why is true not returned here?
                // https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures
                // Although setting this to true still does not solve the following errors when long pressing in terminal view text area
                // ViewDragHelper: Ignoring pointerId=0 because ACTION_DOWN was not received for this pointer before ACTION_MOVE
                // Commenting out the call to mGestureDetector.onTouchEvent(event) in GestureAndScaleRecognizer#onTouchEvent() removes
                // the error logging, so issue is related to GestureDetector
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                // Do not treat is as a single confirmed tap - it may be followed by zoom.
                return false;
            }

            @Override
            public void onLongPress(MotionEvent event) {
                // The hold is the view's own now, and fires earlier than this - see HoldTiming. The
                // detector's long press is kept only for what it does to the detector itself: it
                // stops the lift that ends a hold from arriving as a tap or a fling.
            }
        });
        mScroller = new Scroller(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mAccessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        refreshAccessibilityServiceState();

        // A view is important for accessibility if it fires accessibility events
        // and if it is reported to accessibility services that query the screen.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        // Off by default already, but stated explicitly: the padding fill's rects share edges that
        // are snapped to the same integer pixels the renderer's own cells are, and anti-aliasing
        // would blend a translucent hairline into exactly those shared edges, undoing the snap.
        mPaddingFillPaint.setAntiAlias(false);
    }

    /**
     * @param client The {@link TerminalViewClient} interface implementation to allow
     *                           for communication between {@link TerminalView} and its client.
     */
    public void setTerminalViewClient(TerminalViewClient client) {
        this.mClient = client;
    }

    /**
     * Sets whether terminal view key logging is enabled or not.
     *
     * @param value The boolean value that defines the state.
     */
    public void setIsTerminalViewKeyLoggingEnabled(boolean value) {
        TERMINAL_VIEW_KEY_LOGGING_ENABLED = value;
    }

    /** Show an emulator without a session behind it, for tests that drive the view's input. */
    @androidx.annotation.VisibleForTesting
    void setEmulatorForTest(TerminalEmulator emulator) {
        mEmulator = emulator;
    }

    /**
     * Attach a {@link TerminalSession} to this view.
     *
     * @param session The {@link TerminalSession} this view will be displaying.
     */
    public boolean attachSession(TerminalSession session) {
        if (session == mTermSession)
            return false;
        mTopRow = 0;
        updateKittyAnimationVisibility();
        mTermSession = session;
        mEmulator = null;
        updateKittyAnimationVisibility();
        mCombiningAccent = 0;
        // A different session's cursor is somewhere else entirely; do not streak across the switch.
        notifyCursorTrailSnap();
        updateSize(false, "attach");
        // Wait with enabling the scrollbar until we have a terminal to get scroll position from.
        setVerticalScrollBarEnabled(true);
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // Ensure that inputType is only set if TerminalView is selected view with the keyboard and
        // an alternate view is not selected, like an EditText. This is necessary if an activity is
        // initially started with the alternate view or if activity is returned to from another app
        // and the alternate view was the one selected the last time.
        if (mClient.isTerminalViewSelected()) {
            if (mClient.shouldEnforceCharBasedInput()) {
                // Some keyboards seems do not reset the internal state on TYPE_NULL.
                // Affects mostly Samsung stock keyboards.
                // https://github.com/termux/termux-app/issues/686
                // However, this is not a valid value as per AOSP since `InputType.TYPE_CLASS_*` is
                // not set and it logs a warning:
                // W/InputAttributes: Unexpected input class: inputType=0x00080090 imeOptions=0x02000000
                // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:packages/inputmethods/LatinIME/java/src/com/android/inputmethod/latin/InputAttributes.java;l=79
                outAttrs.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            } else {
                // Using InputType.NULL is the most correct input type and avoids issues with other hacks.
                //
                // Previous keyboard issues:
                // https://github.com/termux/termux-packages/issues/25
                // https://github.com/termux/termux-app/issues/87.
                // https://github.com/termux/termux-app/issues/126.
                // https://github.com/termux/termux-app/issues/137 (japanese chars and TYPE_NULL).
                outAttrs.inputType = InputType.TYPE_NULL;
            }
        } else {
            // Corresponds to android:inputType="text"
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        }
        // Note that IME_ACTION_NONE cannot be used as that makes it impossible to input newlines using the on-screen
        // keyboard on Android TV (see https://github.com/termux/termux-app/issues/221).
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new BaseInputConnection(this, true) {

            @Override
            public boolean finishComposingText() {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                    mClient.logInfo(LOG_TAG, "IME: finishComposingText()");
                super.finishComposingText();
                sendTextToTerminal(getEditable());
                getEditable().clear();
                return true;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logInfo(LOG_TAG, "IME: commitText(\"" + text + "\", " + newCursorPosition + ")");
                }
                super.commitText(text, newCursorPosition);
                if (mEmulator == null)
                    return true;
                Editable content = getEditable();
                sendTextToTerminal(content);
                content.clear();
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logInfo(LOG_TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");
                }
                // The stock Samsung keyboard with 'Auto check spelling' enabled sends leftLength > 1.
                KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                for (int i = 0; i < leftLength; i++) sendKeyEvent(deleteKey);
                return super.deleteSurroundingText(leftLength, rightLength);
            }

            void sendTextToTerminal(CharSequence text) {
                stopTextSelectionMode();
                final int textLengthInChars = text.length();
                for (int i = 0; i < textLengthInChars; i++) {
                    char firstChar = text.charAt(i);
                    int codePoint;
                    if (Character.isHighSurrogate(firstChar)) {
                        if (++i < textLengthInChars) {
                            codePoint = Character.toCodePoint(firstChar, text.charAt(i));
                        } else {
                            // At end of string, with no low surrogate following the high:
                            codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
                        }
                    } else {
                        codePoint = firstChar;
                    }
                    // Check onKeyDown() for details.
                    if (mClient.readShiftKey())
                        codePoint = Character.toUpperCase(codePoint);
                    boolean ctrlHeld = false;
                    if (codePoint <= 31 && codePoint != 27) {
                        if (codePoint == '\n') {
                            // The AOSP keyboard and descendants seems to send \n as text when the enter key is pressed,
                            // instead of a key event like most other keyboard apps. A terminal expects \r for the enter
                            // key (although when icrnl is enabled this doesn't make a difference - run 'stty -icrnl' to
                            // check the behaviour).
                            codePoint = '\r';
                        }
                        // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true;
                        switch(codePoint) {
                            case 31:
                                codePoint = '_';
                                break;
                            case 30:
                                codePoint = '^';
                                break;
                            case 29:
                                codePoint = ']';
                                break;
                            case 28:
                                codePoint = '\\';
                                break;
                            default:
                                codePoint += 96;
                                break;
                        }
                    }
                    inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false);
                }
            }
        };
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mEmulator == null ? 1 : mEmulator.mRows;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows() + mTopRow - mEmulator.mRows;
    }

    public void onScreenUpdated() {
        onScreenUpdated(false);
    }

    public void onScreenUpdated(boolean skipScrolling) {
        if (mEmulator == null)
            return;
        int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
        if (mTopRow < -rowsInHistory) {
            mTopRow = -rowsInHistory;
            clearScrollOffset();
        }
        if (isSelectingText() || mEmulator.isAutoScrollDisabled() || mTopRow < 0) {
            // Do not scroll when selecting text or when the user has scrolled up in the
            // transcript: keep the viewport pinned to the same content while output arrives.
            // The user gets back to following the output by scrolling to the bottom or by
            // typing (see snapToBottomForInput()).
            int rowShift = mEmulator.getScrollCounter();
            if (-mTopRow + rowShift > rowsInHistory) {
                // .. unless we're hitting the end of the history transcript, in which
                // case we abort text selection and stay pinned at the oldest line.
                if (isSelectingText())
                    stopTextSelectionMode();
                mTopRow = -rowsInHistory;
                clearScrollOffset();
            } else {
                mTopRow -= rowShift;
                decrementYTextSelectionCursors(rowShift);
            }
        }
        mEmulator.clearScrollCounter();
        invalidate();
        // Tell a screen reader the content of this control changed, so that it gets the updated text.
        mAccessibilityTextUpdates.onScreenUpdated();
    }

    private void refreshAccessibilityServiceState() {
        if (mAccessibilityManager == null) return;
        mAccessibilityTextUpdates.setServiceState(mAccessibilityManager.isEnabled(),
            mAccessibilityManager.isTouchExplorationEnabled());
    }

    // ultimately called as a result of the code in updateScreen
    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);

        // add our (most up to date) text
        final CharSequence text = getText();
        if (!TextUtils.isEmpty(text)) {
            event.getText().add(text);
        }
    }

    // called by accessibility service exploring what's available
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo node) {
        super.onInitializeAccessibilityNodeInfo(node);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            node.setImportantForAccessibility(true);
        }

        final CharSequence text = getText();
        node.setText(text);

        // why only if text is non-empty? cargo cult, core TextView does this check,
        // and the accessibility guide example also does this check, who am I to argue
        if (!TextUtils.isEmpty(text)) {
            // all granularities are valid, don't let the accessibility system guess;
            // this allows a TalkBack user to navigate by char/word/paragraph within
            // the TerminalView text only without accidently breaking out; other navigation
            // modes such as default/controls allow you to move to other controls
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
            node.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);

            // add more selection actions
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_SELECTION);
        }

        // behave more like a multiline text view
        node.setEditable(true);
        node.setMultiLine(true);
        node.setScrollable(true);
        node.setCanOpenPopup(true);

        // add actions that you can do on this thing
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_FOCUS);
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY);
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE);
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);

        // Add accessibility actions

        node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
            R.id.a11y_speak_cursor_position,
            getResources().getString(R.string.a11y_speak_cursor_position_text)));
        node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
            R.id.a11y_speak_cursor_line,
            getResources().getString(R.string.a11y_speak_cursor_line_text)));
        // Using a different to the Copy action in the popup, which you can technically
        // get to if someone tells you it's there. You can't have the same button label
        // do different things in different contexts; hence, different label
        node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
            R.id.a11y_copy_id,
            getResources().getString(R.string.a11y_copy_screen_text)));
        node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
            R.id.a11y_paste_id,
            getResources().getString(R.string.paste_text)));
        node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
            R.id.a11y_show_termux_menu_id,
            getResources().getString(R.string.a11y_termux_menu_text)));
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        // only handle custom actions here, the defaults implemented by super are good enough
        if (action == R.id.a11y_show_termux_menu_id) {
            showContextMenu();
            return true;
        } else if (action == R.id.a11y_paste_id) {
            doPaste();
            return true;
        } else if (action == R.id.a11y_copy_id) {
            // I can't quite figure out how to make TextSelectionHandleView and/or
            // TextSelectionCursor accessible; and I can't figure out how to hook up
            // with the Accessibility Selection (2 finger 2x tap & hold) either;
            // so at least give people the option to copy the screen; the whole
            // transcript might be too much, plus there's a share transcript option
            // in the More... menu;
            // 3 finger double tap works as copy, but editable fields elsewhere tend
            // to offer a Copy accessibility option
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("screen text", getText());
            clipboard.setPrimaryClip(clip);
            CharSequence copied = getResources().getText(R.string.copied_to_clipboard_text);
            if (mClient != null) mClient.onShowNotice(copied);
            else Toast.makeText(getContext(), copied, Toast.LENGTH_SHORT).show();

            return true;
        } else if (action == R.id.a11y_speak_cursor_position && mEmulator != null) {
            // because TalkBack might omit speaking out whitespace or punctuation,
            // get the character under cursor to get a better idea what "column 24" means...
            // in conjunction with "speak line", it should give you a good idea where you are
            Character charAtCursor = mEmulator.getChar(mEmulator.getCursorCol(), mTopRow + mEmulator.getCursorRow());
            // get the unicode name of the character; The screen reader may be configured to not
            // speak out punctuation, and it will probably not say " "
            String namedCharAtCursor = charAtCursor != null
                ? Character.getName(charAtCursor)
                : "";
            // Character.getName() is allowed to return null...
            // ...and it's easy to "accidently" your terminal with an unfortunate cat
            if (namedCharAtCursor == null)
                namedCharAtCursor = "unknown";
            // "line Y / nScreenLines column X / nScreenColumns. unicode_name_of_character"
            final String text = getResources().getString(R.string.a11y_line_text) +
                " " +
                (mEmulator.getCursorRow() + 1) +
                " / " +
                mEmulator.mRows +
                " " +
                getResources().getString(R.string.a11y_column_text) +
                " " +
                (mEmulator.getCursorCol() + 1) +
                " / " +
                mEmulator.mColumns +
                ". " +
                namedCharAtCursor;
            announceForAccessibility(text);
            return true;
        } else if (action == R.id.a11y_speak_cursor_line && mEmulator != null) {
            CharSequence lineText = mEmulator.getScreen().getSelectedText(0, mTopRow + mEmulator.getCursorRow(), mEmulator.mColumns, mTopRow + mEmulator.getCursorRow());
            announceForAccessibility(lineText);
            return true;
        }

        return super.performAccessibilityAction(action, args);
    }

    /** This must be called by the hosting activity in {@link Activity#onContextMenuClosed(Menu)}
     * when context menu for the {@link TerminalView} is started by
     * {@link TextSelectionCursorController#ACTION_MORE} is closed.
     */
    public void onContextMenuClosed(Menu menu) {
        // Unset the stored text since it shouldn't be used anymore and should be cleared from memory
        unsetStoredSelectedText();
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param textSize the new font size, in density-independent pixels.
     */
    public void setTextSize(int textSize) {
        // The same size again is the same renderer; every caller that stamps a default and then
        // the real value would otherwise build it twice.
        if (mRenderer != null && mRenderer.mTextSize == textSize) return;
        final TerminalRenderer replaced = mRenderer;
        mRenderer = replaced == null
            ? new TerminalRenderer(textSize, Typeface.MONOSPACE, null, null, null)
            : new TerminalRenderer(textSize, replaced.mTypeface, replaced.mBoldTypeface,
                replaced.mItalicTypeface, replaced.mBoldItalicTypeface, replaced.mSymbolMaps,
                replaced.mLigaturePolicy, replaced.mFontFeatures, replaced.mFontVariations,
                replaced.mFontMetricsAdjustments, replaced.mBoxDrawingPolicy,
                replaced.mFallbackTypefaces, replaced.mSymbolExpansion, replaced);
        mRenderer.setUrlUnderlineColor(mUrlUnderlineColor);
        relayoutIfFirstRowMoved(replaced);
        // The new renderer has taken everything worth inheriting; the old one's per-row recordings
        // are the size of the screen and will never be replayed again.
        if (replaced != null) replaced.release();
        updateSize();
    }

    /**
     * Gives a view that has no renderer yet the fonts and size of {@code sibling}, built on the
     * sibling's caches: the variable-font instances, the fallback memo and the measured ASCII
     * advances all carry over, so a new pane does not pay for what a pane beside it already has.
     * The host's own size and font pass follows and finds nothing to redo when they match.
     */
    public void adoptFontFrom(@Nullable TerminalView sibling) {
        if (mRenderer != null || sibling == null || sibling.mRenderer == null) return;
        TerminalRenderer r = sibling.mRenderer;
        mRenderer = new TerminalRenderer(r.mTextSize, r.mTypeface, r.mBoldTypeface,
            r.mItalicTypeface, r.mBoldItalicTypeface, r.mSymbolMaps, r.mLigaturePolicy,
            r.mFontFeatures, r.mFontVariations, r.mFontMetricsAdjustments, r.mBoxDrawingPolicy,
            r.mFallbackTypefaces, r.mSymbolExpansion, r);
        relayoutIfFirstRowMoved(null);
        updateSize();
    }

    /** See {@link TerminalRenderer#setRowCacheBypassed}: for a frozen copy of this view. */
    public void setRowCacheBypassed(boolean bypassed) {
        if (mRenderer != null) mRenderer.setRowCacheBypassed(bypassed);
    }

    private int mUrlUnderlineColor;

    /** See {@link TerminalRenderer#setUrlUnderlineColor}; survives a renderer rebuild. */
    public void setUrlUnderlineColor(int color) {
        if (mUrlUnderlineColor == color) return;
        mUrlUnderlineColor = color;
        if (mRenderer != null) mRenderer.setUrlUnderlineColor(color);
        invalidate();
    }

    public void setTypeface(Typeface newTypeface, Typeface newItalicTypeface) {
        setTypeface(newTypeface, null, newItalicTypeface, null);
    }

    /** Apply independent regular, bold, italic and bold-italic terminal faces. */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic) {
        setTypeface(regular, bold, italic, boldItalic, null);
    }

    /** Apply the primary faces and repeatable explicit symbol-font ranges atomically. */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic, TerminalRenderer.SymbolMap[] symbolMaps) {
        setTypeface(regular, bold, italic, boldItalic, symbolMaps,
            TerminalRenderer.LigaturePolicy.NEVER);
    }

    /** Apply all font sources and shaping policy as one renderer replacement. */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic, TerminalRenderer.SymbolMap[] symbolMaps,
                            TerminalRenderer.LigaturePolicy ligaturePolicy) {
        setTypeface(regular, bold, italic, boldItalic, symbolMaps, ligaturePolicy,
            TerminalRenderer.FontFeatures.NONE);
    }

    /** Apply all font sources and shaping settings as one renderer replacement. */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic, TerminalRenderer.SymbolMap[] symbolMaps,
                            TerminalRenderer.LigaturePolicy ligaturePolicy,
                            TerminalRenderer.FontFeatures fontFeatures) {
        setTypeface(regular, bold, italic, boldItalic, symbolMaps, ligaturePolicy, fontFeatures,
            TerminalRenderer.FontVariations.NONE);
    }

    /** Apply all font sources and per-run shaping settings as one renderer replacement. */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic, TerminalRenderer.SymbolMap[] symbolMaps,
                            TerminalRenderer.LigaturePolicy ligaturePolicy,
                            TerminalRenderer.FontFeatures fontFeatures,
                            TerminalRenderer.FontVariations fontVariations) {
        setTypeface(regular, bold, italic, boldItalic, symbolMaps, ligaturePolicy, fontFeatures,
            fontVariations, TerminalRenderer.FontMetricsAdjustments.NONE);
    }

    /** Apply all font sources, shaping settings, and bounded metrics atomically. */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic, TerminalRenderer.SymbolMap[] symbolMaps,
                            TerminalRenderer.LigaturePolicy ligaturePolicy,
                            TerminalRenderer.FontFeatures fontFeatures,
                            TerminalRenderer.FontVariations fontVariations,
                            TerminalRenderer.FontMetricsAdjustments fontMetricsAdjustments) {
        setTypeface(regular, bold, italic, boldItalic, symbolMaps, ligaturePolicy, fontFeatures,
            fontVariations, fontMetricsAdjustments, TerminalRenderer.BoxDrawingPolicy.DEFAULT);
    }

    /** Apply all font sources, shaping settings, metrics, and box-drawing policy atomically. */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic, TerminalRenderer.SymbolMap[] symbolMaps,
                            TerminalRenderer.LigaturePolicy ligaturePolicy,
                            TerminalRenderer.FontFeatures fontFeatures,
                            TerminalRenderer.FontVariations fontVariations,
                            TerminalRenderer.FontMetricsAdjustments fontMetricsAdjustments,
                            TerminalRenderer.BoxDrawingPolicy boxDrawingPolicy) {
        setTypeface(regular, bold, italic, boldItalic, symbolMaps, ligaturePolicy, fontFeatures,
            fontVariations, fontMetricsAdjustments, boxDrawingPolicy, null);
    }

    /**
     * Apply every font source, including the ordered fallback chain, as one renderer replacement.
     *
     * @param fallbackTypefaces faces tried in order for code points the face chosen for a run has
     *                          no glyph for, after any {@code symbol_map} match and before
     *                          Android's own platform fallback.
     */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic, TerminalRenderer.SymbolMap[] symbolMaps,
                            TerminalRenderer.LigaturePolicy ligaturePolicy,
                            TerminalRenderer.FontFeatures fontFeatures,
                            TerminalRenderer.FontVariations fontVariations,
                            TerminalRenderer.FontMetricsAdjustments fontMetricsAdjustments,
                            TerminalRenderer.BoxDrawingPolicy boxDrawingPolicy,
                            Typeface[] fallbackTypefaces) {
        setTypeface(regular, bold, italic, boldItalic, symbolMaps, ligaturePolicy, fontFeatures,
            fontVariations, fontMetricsAdjustments, boxDrawingPolicy, fallbackTypefaces,
            TerminalRenderer.SymbolExpansion.DEFAULT);
    }

    /**
     * As above, with the {@code narrow_symbols} ceilings on how far a private-use symbol may spread
     * into the blank cells after it.
     */
    public void setTypeface(Typeface regular, Typeface bold, Typeface italic,
                            Typeface boldItalic, TerminalRenderer.SymbolMap[] symbolMaps,
                            TerminalRenderer.LigaturePolicy ligaturePolicy,
                            TerminalRenderer.FontFeatures fontFeatures,
                            TerminalRenderer.FontVariations fontVariations,
                            TerminalRenderer.FontMetricsAdjustments fontMetricsAdjustments,
                            TerminalRenderer.BoxDrawingPolicy boxDrawingPolicy,
                            Typeface[] fallbackTypefaces,
                            TerminalRenderer.SymbolExpansion symbolExpansion) {
        final TerminalRenderer replaced = mRenderer;
        mRenderer = new TerminalRenderer(replaced.mTextSize, regular, bold, italic, boldItalic,
            symbolMaps, ligaturePolicy, fontFeatures, fontVariations, fontMetricsAdjustments,
            boxDrawingPolicy, fallbackTypefaces, symbolExpansion, replaced);
        mRenderer.setUrlUnderlineColor(mUrlUnderlineColor);
        relayoutIfFirstRowMoved(replaced);
        replaced.release();
        updateSize();
        invalidate();
    }

    /** Whether a renderer/font has been set (safe to call {@link #setTypeface}). */
    public boolean isFontInitialized() {
        return mRenderer != null;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    public void setSplitChar(char splitChar) {
        mSplitChar = splitChar;
    }

    public String getCurrentInput() {
        if (mEmulator == null) {
            return null;
        }
        int row = mEmulator.getCursorRow();
        String text = mEmulator.getScreen().getSelectedText(0, row, 99, row);
        if (text.indexOf(mSplitChar) >= 0) {
            text = text.substring(text.indexOf(mSplitChar) + 1);
            text = text.replaceAll("[^a-zA-Z ]", "");
            text = text.replaceAll(" {2,}", " ");
            return text.trim();
        }
        return null;
    }

    public String getCurrentInput(char currentChar) {
        if (mEmulator == null) {
            return null;
        }
        int row = mEmulator.getCursorRow();
        int cut = mEmulator.getCursorCol();
        String originalText = mEmulator.getScreen().getSelectedText(0, row, 99, row);
        if (originalText.indexOf(mSplitChar) >= 0) {
            if (cut >= originalText.length()) {
                originalText = originalText + currentChar;
            } else if (cut > 0) {
                originalText = originalText.substring(0, cut) + currentChar + originalText.substring(cut);
            } else if (cut == 0) {
                originalText = originalText + currentChar;
            }
            String text = originalText.substring(originalText.indexOf(mSplitChar) + 1);
            text = text.replaceAll("[^a-zA-Z ]", "");
            text = text.replaceAll(" {2,}", " ");
            return text.trim();
        }
        return null;
    }

    /**
     * Returns true only for a literal split-prefixed command at the cursor in the normal buffer.
     * This deliberately avoids the permissive space fallback used by legacy suggestion parsing.
     */
    public boolean isCurrentInputAppSearchMode() {
        if (mEmulator == null || isAlternateBufferActive()) return false;
        int row = mEmulator.getCursorRow();
        int cursor = mEmulator.getCursorCol();
        String line = mEmulator.getScreen().getSelectedText(0, row, 99, row);
        return hasAppSearchPrefixInLine(line, cursor, mSplitChar);
    }

    static boolean hasAppSearchPrefixInLine(String line, int cursor, char splitChar) {
        if (line == null || splitChar == ' ') return false;
        int end = Math.max(0, Math.min(cursor, line.length()));
        int split = line.lastIndexOf(splitChar, Math.max(0, end - 1));
        if (split < 0) return false;
        if (split > 0 && "$#>❯λ".indexOf(line.charAt(split - 1)) < 0) {
            int prompt = -1;
            for (int i = split - 1; i >= 0; i--) {
                if ("$#>❯λ".indexOf(line.charAt(i)) >= 0) {
                    prompt = i;
                    break;
                }
            }
            int commandStart = prompt + 1;
            for (int i = commandStart; i < split; i++) {
                if (!Character.isWhitespace(line.charAt(i))) return false;
            }
        }
        for (int i = split + 1; i < end; i++) {
            char c = line.charAt(i);
            if (!Character.isLetter(c) && c != ' ') return false;
        }
        return true;
    }

    public boolean isAlternateBufferActive() {
        return mEmulator != null && mEmulator.isAlternateBufferActive();
    }

    static String extractCurrentInputFromLine(String originalText, int cut, char splitChar, Character insertCharOrNull) {
        if (originalText == null) {
            return null;
        }
        String workingText = originalText;
        if (insertCharOrNull != null) {
            if (cut == 0 || cut >= workingText.length()) {
                workingText = workingText + insertCharOrNull.charValue();
            } else if (cut > 0) {
                StringBuilder builder = new StringBuilder(workingText.length() + 1);
                builder.append(workingText, 0, cut);
                builder.append(insertCharOrNull.charValue());
                builder.append(workingText.substring(cut));
                workingText = builder.toString();
            }
        }
        int splitIndex = workingText.indexOf(splitChar);
        if (splitIndex < 0 && splitChar != ' ') {
            splitIndex = workingText.indexOf(' ');
        }
        if (splitIndex < 0) {
            return null;
        }
        String text = workingText.substring(splitIndex + 1);
        text = text.replaceAll("[^a-zA-Z ]", "");
        text = text.replaceAll(" {2,}", " ");
        return text.trim();
    }

    public void clearInputLine() {
        KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
        String input = getCurrentInput();
        if (input != null) {
            int width = input.length() + 10;
            for (int i = 0; i < width; i++) {
                onKeyDown(KeyEvent.KEYCODE_DEL, deleteKey);
            }
        }
    }


    /**
     * Get the zero indexed column and row of the terminal view for the
     * position of the event.
     *
     * @param event The event with the position to get the column and row for.
     * @param relativeToScroll If true the column number will take the scroll
     * position into account. E.g. if scrolled 3 lines up and the event
     * position is in the top left, column will be -3 if relativeToScroll is
     * true and 0 if relativeToScroll is false.
     * @return Array with the column and row.
     */
    public int[] getColumnAndRow(MotionEvent event, boolean relativeToScroll) {
        int column = getColumnForX(event.getX());
        int row = getRowForY(event.getY());
        if (relativeToScroll) {
            row += mTopRow;
        }
        return new int[] { column, row };
    }

    /** The link under a lifted finger, or null; see {@link TerminalLinks#at}. */
    private TerminalLinks.Link linkUnderTap(MotionEvent event) {
        if (mEmulator == null || mRenderer == null) return null;
        int[] cell = getColumnAndRow(event, true);
        return TerminalLinks.at(mEmulator, cell[0], cell[1], mUrlTapEnabled);
    }

    /** Whether a tap reads addresses out of the text; OSC 8 hyperlinks are always offered. */
    public void setUrlTapEnabled(boolean enabled) {
        mUrlTapEnabled = enabled;
    }

    /** The renderer's line spacing in pixels, or 0 before a renderer exists. */
    public int getFontLineSpacing() {
        return mRenderer == null ? 0 : mRenderer.mFontLineSpacing;
    }

    /**
     * How far below this view's top edge the first row's cells start, in px, or 0 before a
     * renderer exists: the renderer's ascent allowance, which every row is drawn under and which
     * {@link #getVerticalContentOffset()} adds its own slack to. A frame laying this view out
     * inside a rounded corner counts it as clearance the top edge already has.
     */
    public int getFirstRowTopPx() {
        return mRenderer == null ? 0 : mRenderer.getFontLineSpacingAndAscent();
    }

    /**
     * A new renderer may start its first row a different distance down ({@link #getFirstRowTopPx}),
     * and the frame around this view sizes its top margin from that, so it has to measure again —
     * a font change on its own moves no view and would leave the old margin in place until the
     * next layout for some other reason.
     */
    private void relayoutIfFirstRowMoved(@Nullable TerminalRenderer replaced) {
        if (replaced == null
            || replaced.getFontLineSpacingAndAscent() != mRenderer.getFontLineSpacingAndAscent())
            requestLayout();
    }

    private int getColumnForX(float x) {
        return (int) ((x - getHorizontalContentOffset()) / mRenderer.mFontWidth);
    }

    private int getRowForY(float y) {
        // While a smooth fling/settle holds a fractional offset, drawn content sits that many
        // pixels above its nominal row position, so screen Y maps back by adding it; the
        // centring offset shifts it the other way.
        return (int) ((y - getVerticalContentOffset() + mScrollOffsetPixels
            - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);
    }

    /** Turn mouse mode on or off; see {@link #mTouchMouseMode}. */
    public void setTouchMouseMode(boolean enabled) {
        if (mTouchMouseMode == enabled) return;
        MouseModePress.Step step = mTouchMouseModePress.release();
        if (step == MouseModePress.Step.RELEASE && mEmulator != null) {
            sendMouseEventAt(TerminalEmulator.MOUSE_LEFT_BUTTON, mTouchMouseModeLastCol,
                mTouchMouseModeLastRow, false);
        }
        mTouchMouseWheelActive = false;
        stopTouchMouseWheelFling();
        mTouchMouseMode = enabled;
    }

    public boolean isTouchMouseMode() {
        return mTouchMouseMode;
    }

    /**
     * The pointer a running program asked for — a text bar in an editor, a busy pointer for a long
     * job — or null for the usual one.
     *
     * <p>Only a mouse or trackpad has a pointer to change. The two touch ways of driving a mouse
     * here draw nothing on screen, so there is nothing for them to show.
     */
    public void setRequestedPointerShape(@Nullable String shape) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (Objects.equals(mRequestedPointerShape, shape)) return;
        mRequestedPointerShape = shape;
        setPointerIcon(shape == null ? null : PointerIcon.getSystemIcon(getContext(),
            pointerIconType(shape)));
    }

    /** The pointer a running program last asked for, or null for the usual one. */
    @Nullable
    public String getRequestedPointerShape() {
        return mRequestedPointerShape;
    }

    /**
     * The CSS and X11 pointer names a program may ask for, mapped to what Android draws. Names
     * Android has no pointer for fall back to the arrow rather than being refused.
     */
    private static int pointerIconType(@NonNull String shape) {
        switch (shape) {
            case "text":
            case "xterm":
            case "ibeam":
                return PointerIcon.TYPE_TEXT;
            case "vertical-text":
                return PointerIcon.TYPE_VERTICAL_TEXT;
            case "pointer":
            case "hand":
            case "hand2":
            case "pointing-hand":
                return PointerIcon.TYPE_HAND;
            case "crosshair":
            case "cross":
                return PointerIcon.TYPE_CROSSHAIR;
            case "wait":
            case "watch":
                return PointerIcon.TYPE_WAIT;
            case "progress":
            case "left-ptr-watch":
                return PointerIcon.TYPE_CONTEXT_MENU;
            case "help":
            case "question-arrow":
                return PointerIcon.TYPE_HELP;
            case "move":
            case "fleur":
            case "all-scroll":
                return PointerIcon.TYPE_ALL_SCROLL;
            case "not-allowed":
            case "no-drop":
            case "forbidden":
                return PointerIcon.TYPE_NO_DROP;
            case "grab":
            case "openhand":
                return PointerIcon.TYPE_GRAB;
            case "grabbing":
            case "closedhand":
                return PointerIcon.TYPE_GRABBING;
            case "alias":
                return PointerIcon.TYPE_ALIAS;
            case "copy":
                return PointerIcon.TYPE_COPY;
            case "cell":
                return PointerIcon.TYPE_CROSSHAIR;
            case "zoom-in":
                return PointerIcon.TYPE_ZOOM_IN;
            case "zoom-out":
                return PointerIcon.TYPE_ZOOM_OUT;
            case "e-resize":
            case "w-resize":
            case "ew-resize":
            case "col-resize":
                return PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW;
            case "n-resize":
            case "s-resize":
            case "ns-resize":
            case "row-resize":
                return PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW;
            case "nwse-resize":
            case "nw-resize":
            case "se-resize":
                return PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW;
            case "nesw-resize":
            case "ne-resize":
            case "sw-resize":
                return PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW;
            case "none":
                return PointerIcon.TYPE_NULL;
            default:
                return PointerIcon.TYPE_ARROW;
        }
    }

    /**
     * The whole of a touch stream in mouse mode. One finger is the left button: down at the cell
     * under it, held while it moves cell to cell, released where it lifts. A second finger makes
     * the gesture the wheel instead - one notch per line of travel, from the fingers' midpoint -
     * and releases the button; lifting fast lets the wheel run on. Nothing is typed at a program
     * that did not ask for the mouse: see {@link #turnWheel}.
     */
    private void handleTouchMouseMode(MotionEvent event) {
        int action = event.getActionMasked();
        int column = getColumnForX(event.getX());
        int row = getRowForY(event.getY());
        boolean tracking = mEmulator.isMouseTrackingActive();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                stopTouchMouseWheelFling();
                if (mTouchMouseWheelVelocity == null) {
                    mTouchMouseWheelVelocity = android.view.VelocityTracker.obtain();
                } else {
                    mTouchMouseWheelVelocity.clear();
                }
                mTouchMouseWheelVelocity.addMovement(event);
                mTouchMouseModeLastCol = column;
                mTouchMouseModeLastRow = row;
                applyMouseModePress(mTouchMouseModePress.down(tracking, mHoldExempt));
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                applyMouseModePress(mTouchMouseModePress.pointerDown());
                if (mTouchMouseWheelVelocity != null) mTouchMouseWheelVelocity.addMovement(event);
                mTouchMouseWheelActive = true;
                mTouchMouseWheelStartY = meanY(event);
                mTouchMouseWheelSent = 0;
                mTouchMouseWheelCol = column;
                mTouchMouseWheelRow = row;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTouchMouseWheelVelocity != null) mTouchMouseWheelVelocity.addMovement(event);
                if (mTouchMouseWheelActive && event.getPointerCount() >= 2) {
                    // Fingers moving up bring the content up, which is the wheel turning down.
                    int notches = Math.round((mTouchMouseWheelStartY - meanY(event))
                        / touchMouseWheelNotchPx());
                    turnWheel(notches - mTouchMouseWheelSent, mTouchMouseWheelCol, mTouchMouseWheelRow);
                    mTouchMouseWheelSent = notches;
                    break;
                }
                // A press deferred for a pane corner goes out here, at the cell the finger landed
                // on, the moment the movement is real enough that no corner is going to claim it.
                applyMouseModePress(mTouchMouseModePress.move(movedPastSlop(event)));
                if (mTouchMouseModePress.isPressed()
                    && (column != mTouchMouseModeLastCol || row != mTouchMouseModeLastRow)) {
                    mTouchMouseModeLastCol = column;
                    mTouchMouseModeLastRow = row;
                    sendMouseEventAt(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, column, row, true);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (mTouchMouseWheelActive) {
                    mTouchMouseWheelActive = false;
                    flingTouchMouseWheel();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mTouchMouseWheelActive) {
                    mTouchMouseWheelActive = false;
                    if (action == MotionEvent.ACTION_UP) flingTouchMouseWheel();
                }
                applyMouseModePress(action == MotionEvent.ACTION_UP
                    ? mTouchMouseModePress.up() : mTouchMouseModePress.cancel());
                if (mTouchMouseWheelVelocity != null) {
                    mTouchMouseWheelVelocity.recycle();
                    mTouchMouseWheelVelocity = null;
                }
                break;
            default:
                break;
        }
    }

    /** Send what mouse mode's button owes the program, always at the cell it is tracking. */
    private void applyMouseModePress(MouseModePress.Step step) {
        switch (step) {
            case PRESS:
                sendMouseEventAt(TerminalEmulator.MOUSE_LEFT_BUTTON, mTouchMouseModeLastCol,
                    mTouchMouseModeLastRow, true);
                break;
            case CLICK:
                sendClickAt(mTouchMouseModeLastCol, mTouchMouseModeLastRow, null);
                break;
            case RELEASE:
                sendMouseEventAt(TerminalEmulator.MOUSE_LEFT_BUTTON, mTouchMouseModeLastCol,
                    mTouchMouseModeLastRow, false);
                break;
            default:
                break;
        }
    }

    /** Whether this gesture has travelled far enough to be a drag rather than a finger resting. */
    private boolean movedPastSlop(MotionEvent event) {
        float dx = event.getX() - mTouchDownX;
        float dy = event.getY() - mTouchDownY;
        return dx * dx + dy * dy > (float) mTouchSlop * mTouchSlop;
    }

    /** The midpoint of every finger down, so one finger drifting does not jolt the wheel. */
    private static float meanY(MotionEvent event) {
        int count = event.getPointerCount();
        float sum = 0f;
        for (int i = 0; i < count; i++) sum += event.getY(i);
        return count == 0 ? 0f : sum / count;
    }

    /** One wheel notch is one line of finger travel: content follows the fingers. */
    private float touchMouseWheelNotchPx() {
        float lineHeight = mRenderer == null ? 0f : mRenderer.mFontLineSpacing;
        return lineHeight > 0f ? lineHeight : 16f * getResources().getDisplayMetrics().density;
    }

    /**
     * Turn the wheel {@code notches} clicks, down for positive. A program tracking the mouse gets
     * wheel buttons at the gesture's cell; the transcript scrolls itself otherwise, or arrow keys
     * stand in on the alternate screen, exactly as a two-finger drag does outside mouse mode.
     */
    private void turnWheel(int notches, int column, int row) {
        if (notches == 0 || mEmulator == null) return;
        boolean down = notches > 0;
        int amount = Math.abs(notches);
        for (int i = 0; i < amount; i++) {
            if (mEmulator.isMouseTrackingActive()) {
                sendMouseEventAt(down ? TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
                    : TerminalEmulator.MOUSE_WHEELUP_BUTTON, column, row, true);
            } else if (mEmulator.isAlternateBufferActive()) {
                handleKeyCode(down ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP, 0);
            } else {
                mTopRow = Math.min(0, Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()),
                    mTopRow + (down ? 1 : -1)));
                if (!awakenScrollBars()) invalidate();
            }
        }
    }

    /** Let the wheel run on from the fingers' speed at the lift, slowing as a fling does. */
    private void flingTouchMouseWheel() {
        if (mTouchMouseWheelVelocity == null) return;
        mTouchMouseWheelVelocity.computeCurrentVelocity(1000);
        float velocityY = mTouchMouseWheelVelocity.getYVelocity();
        int minimum = ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity();
        if (Math.abs(velocityY) < minimum) return;
        if (mTouchMouseWheelFling == null) mTouchMouseWheelFling = new Scroller(getContext());
        // The fling's axis is finger travel upwards, the same sign as the notch count.
        mTouchMouseWheelFling.fling(0, 0, 0, Math.round(-velocityY), 0, 0,
            Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
        mTouchMouseWheelFlingSent = 0;
        postOnAnimation(mTouchMouseWheelFlingStep);
    }

    private void stopTouchMouseWheelFling() {
        removeCallbacks(mTouchMouseWheelFlingStep);
        if (mTouchMouseWheelFling != null) mTouchMouseWheelFling.abortAnimation();
    }

    /**
     * Send a single mouse event code to the terminal.
     */
    void sendMouseEventCode(MotionEvent e, int button, boolean pressed) {
        int modifiers = mouseModifiersFor(e, button, pressed);
        int[] columnAndRow = getColumnAndRow(e, false);
        int x = columnAndRow[0] + 1;
        int y = columnAndRow[1] + 1;
        if (pressed && (button >= TerminalEmulator.MOUSE_WHEELDOWN_BUTTON && button <= TerminalEmulator.MOUSE_WHEEL_RIGHT)) {
            if (mMouseStartDownTime == e.getDownTime()) {
                x = mMouseScrollStartX;
                y = mMouseScrollStartY;
            } else {
                mMouseStartDownTime = e.getDownTime();
                mMouseScrollStartX = x;
                mMouseScrollStartY = y;
            }
        }
        mEmulator.sendMouseEvent(button, x, y, pressed, modifiers);
    }

    private static boolean isWheelButton(int button) {
        return button >= TerminalEmulator.MOUSE_WHEELUP_BUTTON && button <= TerminalEmulator.MOUSE_WHEEL_RIGHT;
    }

    /**
     * The modifier bits a mouse report owes. A left press reads the keyboard and the extra keys row
     * (consuming a one-shot latch, which so covers the press and its release) and remembers the
     * bits; motion and release repeat them. A wheel notch takes only what the hardware keyboard
     * holds, so scrolling does not eat a latch meant for the next click.
     */
    private int mouseModifiersFor(MotionEvent e, int button, boolean pressed) {
        if (isWheelButton(button)) return e == null ? 0 : modifiersOf(e, false, false, false);
        if (button == TerminalEmulator.MOUSE_LEFT_BUTTON && pressed)
            return mMousePressModifiers = takeMouseModifiers(e);
        int modifiers = mMousePressModifiers;
        if (!pressed) mMousePressModifiers = 0;
        return modifiers;
    }

    /** Hardware keyboard modifiers on the event, plus any the extra keys row held. */
    private static int modifiersOf(MotionEvent e, boolean ctrlLatched, boolean altLatched, boolean shiftLatched) {
        int modifiers = 0;
        if (shiftLatched || (e != null && (e.getMetaState() & KeyEvent.META_SHIFT_ON) != 0)) modifiers |= TerminalEmulator.MOUSE_MODIFIER_SHIFT;
        if (altLatched || (e != null && (e.getMetaState() & KeyEvent.META_ALT_ON) != 0)) modifiers |= TerminalEmulator.MOUSE_MODIFIER_ALT;
        if (ctrlLatched || (e != null && (e.getMetaState() & KeyEvent.META_CTRL_ON) != 0)) modifiers |= TerminalEmulator.MOUSE_MODIFIER_CTRL;
        return modifiers;
    }

    /** Modifier bits for a click, reading (and so consuming) the extra keys row's latches. */
    private int takeMouseModifiers(MotionEvent e) {
        // Every read happens, so no latch is left over to leak into the next key.
        boolean ctrl = mClient.readControlKey();
        boolean alt = mClient.readAltKey();
        boolean shift = mClient.readShiftKey();
        return modifiersOf(e, ctrl, alt, shift);
    }

    /** Whether Shift is on for this tap, held on a keyboard or latched in the extra keys row. */
    private boolean takeShiftForTap(MotionEvent e) {
        boolean latched = mClient.readShiftKey();
        return latched || (e.getMetaState() & KeyEvent.META_SHIFT_ON) != 0;
    }

    /**
     * Perform a scroll, either from dragging the screen or by scrolling a mouse wheel.
     */
    void doScroll(MotionEvent event, int rowsDown) {
        boolean up = rowsDown < 0;
        int amount = Math.abs(rowsDown);
        for (int i = 0; i < amount; i++) {
            if (mEmulator.isMouseTrackingActive()) {
                sendMouseEventCode(event, up ? TerminalEmulator.MOUSE_WHEELUP_BUTTON : TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true);
            } else if (mEmulator.isAlternateBufferActive()) {
                // Send up and down key events for scrolling, which is what some terminals do to make scroll work in
                // e.g. less, which shifts to the alt screen without mouse handling.
                handleKeyCode(up ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, 0);
            } else {
                mTopRow = Math.min(0, Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1)));
                if (!awakenScrollBars())
                    invalidate();
            }
        }
    }
    
    void doScrollX(MotionEvent event, int cols) {
        boolean left = cols < 0;
        int amount = Math.abs(cols);
        for (int i = 0; i < amount; i++) {
            if (mEmulator.isMouseTrackingActive()) {
                sendMouseEventCode(event, left ? TerminalEmulator.MOUSE_WHEEL_LEFT : TerminalEmulator.MOUSE_WHEEL_RIGHT, true);
            } else if (mEmulator.isAlternateBufferActive()) {
            	/* less is broken let me know if it works elsewhere @john-peterson
                handleKeyCode(left ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT, 0);
                */
            } else {
            	/*
                mTopRow = Math.min(0, Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1)));
                if (!awakenScrollBars())
                    invalidate();
                    */
            }
        }
    }

    /**
     * Whether transcript scrolling may move by pixels rather than by whole rows. The alternate
     * screen and mouse tracking translate a scroll into keys and wheel events, which have no
     * fractional part, and selection coordinates are row based.
     */
    private boolean isSmoothScrollAllowed() {
        return mEmulator != null && mRenderer != null && !mEmulator.isAlternateBufferActive()
            && !mEmulator.isMouseTrackingActive() && !isSelectingText();
    }

    private float getScrollPixelPosition() {
        return mTopRow * (float) mRenderer.mFontLineSpacing + mScrollOffsetPixels;
    }

    private int getScrollPixelMinimum() {
        return -mEmulator.getScreen().getActiveTranscriptRows() * mRenderer.mFontLineSpacing;
    }

    /**
     * Move the transcript to a pixel position, clamped to the transcript, splitting it into the
     * row the view starts at and the pixel offset into that row.
     */
    private void setScrollPixelPosition(float position) {
        final int lineSpacing = mRenderer.mFontLineSpacing;
        position = Math.max(getScrollPixelMinimum(), Math.min(0f, position));
        int newTopRow = (int) Math.floor(position / lineSpacing);
        float offset = position - newTopRow * (float) lineSpacing;
        if (offset >= lineSpacing) {
            newTopRow++;
            offset = 0f;
        }
        mTopRow = newTopRow;
        mScrollOffsetPixels = offset;
        if (!awakenScrollBars())
            invalidate();
    }

    /**
     * Scroll to the bottom of the transcript on user-initiated input, so typed characters are
     * visible even when the user had scrolled up (matching desktop terminals' scroll-on-keystroke).
     */
    private void snapToBottomForInput() {
        if (mTopRow != 0) {
            mTopRow = 0;
            clearScrollOffset();
            invalidate();
        }
    }

    /** Drop any fractional offset, for the cases where the row position is set from elsewhere. */
    private void clearScrollOffset() {
        abortSmoothScroll();
        if (mScrollOffsetPixels != 0f) {
            mScrollOffsetPixels = 0f;
            invalidate();
        }
    }

    private void abortSmoothScroll() {
        if (mSmoothFlingActive || mSmoothSettleActive) {
            mSmoothFlingActive = false;
            mSmoothSettleActive = false;
            mScroller.abortAnimation();
        }
    }

    /**
     * Animate a resting fractional offset back onto a row boundary, so that everything working in
     * row coordinates - selection, taps, accessibility - agrees with what is drawn.
     */
    private void settleScrollOffset() {
        if (mRenderer == null || mEmulator == null || mScrollOffsetPixels == 0f)
            return;
        final int lineSpacing = mRenderer.mFontLineSpacing;
        int from = Math.round(getScrollPixelPosition());
        int to = Math.round(from / (float) lineSpacing) * lineSpacing;
        to = Math.max(getScrollPixelMinimum(), Math.min(0, to));
        if (from == to) {
            mScrollOffsetPixels = 0f;
            invalidate();
            return;
        }
        mSmoothSettleActive = true;
        mScroller.startScroll(0, from, 0, to - from, SCROLL_SETTLE_DURATION_MS);
        postOnAnimation(mSmoothScrollRunnable);
    }

    private final Runnable mSmoothScrollRunnable = new Runnable() {

        @Override
        public void run() {
            if (!mSmoothFlingActive && !mSmoothSettleActive)
                return;
            if (!isSmoothScrollAllowed()) {
                abortSmoothScroll();
                mScrollOffsetPixels = 0f;
                invalidate();
                return;
            }
            boolean more = mScroller.computeScrollOffset();
            setScrollPixelPosition(mScroller.getCurrY());
            if (more) {
                postOnAnimation(this);
                return;
            }
            boolean wasFling = mSmoothFlingActive;
            mSmoothFlingActive = false;
            mSmoothSettleActive = false;
            if (wasFling) {
                settleScrollOffset();
            } else {
                mScrollOffsetPixels = 0f;
                invalidate();
            }
        }
    };

    /**
     * Whether a finger drag should be reported to the application as a mouse drag, which is the
     * case when it asked for motion while a button is held down.
     */
    private boolean isTouchMouseDragReportingEnabled() {
        return mEmulator != null && mRenderer != null && mEmulator.isMouseTrackingMotionActive();
    }

    private void sendMouseEventAt(int button, int column, int row, boolean pressed) {
        mEmulator.sendMouseEvent(button, column + 1, row + 1, pressed, mouseModifiersFor(null, button, pressed));
    }

    /**
     * Press and release the left button on one cell, which is the whole of a finger click. The
     * modifiers are read once, so a latched one-shot Ctrl covers both halves and is then spent.
     */
    private void sendClickAt(int column, int row, MotionEvent e) {
        int modifiers = takeMouseModifiers(e);
        mEmulator.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, column + 1, row + 1, true, modifiers);
        mEmulator.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, column + 1, row + 1, false, modifiers);
    }

    /**
     * Tell the view whether the finger now down belongs to a pane corner. The pane chrome calls this
     * with {@code true} before forwarding a corner square's {@code ACTION_DOWN} and with
     * {@code false} once that finger lifts or the corner has claimed it, so the view never recognises
     * a hold for a touch the corner will take at {@link HoldTiming#holdTimeoutMs()}.
     */
    public void setHoldExempt(boolean exempt) {
        mHoldExempt = exempt;
        if (exempt) {
            cancelHoldTimers();
            applyHoldOutcome(mHoldGesture.cancel());
        }
    }

    /** Whether the finger now down belongs to a pane corner rather than to this view's holds. */
    public boolean isHoldExempt() {
        return mHoldExempt;
    }

    /**
     * A hold is offered to any finger on the terminal itself, whether or not a program is reading
     * the mouse - a plain shell's hold is text selection. Mouse mode is its own explicit mode, a
     * real pointer needs no hold, and a finger in a pane corner belongs to the corner's tab.
     */
    private boolean isHoldAvailable(MotionEvent event) {
        return mEmulator != null && mRenderer != null && !mTouchMouseMode
            && !event.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText() && !mHoldExempt;
    }

    /**
     * Feed the hold its side of the touch stream. It consumes nothing - the gesture recogniser
     * still sees every event - it only decides what the gesture turns out to have meant.
     */
    private void handleHoldTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mHoldConsumedGesture = false;
                cancelHoldTimers();
                releaseHoldDownEvent();
                mHoldGesture.reset();
                // Once the hold has handed the finger the mouse, one cell of travel is a drag; the
                // touch slop still decides everything before that.
                mHoldGesture.down(event.getX(), event.getY(), mTouchSlop,
                    mRenderer == null ? 0f : mRenderer.mFontWidth,
                    mRenderer == null ? 0f : mRenderer.mFontLineSpacing,
                    isHoldAvailable(event));
                if (mHoldGesture.isPending()) {
                    mHoldDownEvent = MotionEvent.obtain(event);
                    // Both stages are timed from the landing, so the second is not lengthened by
                    // however long the first took to be recognised.
                    postDelayed(mHoldRunnable, HoldTiming.holdTimeoutMs());
                    postDelayed(mHoldSelectRunnable, HoldTiming.selectTimeoutMs());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                applyHoldOutcome(mHoldGesture.move(event.getX(), event.getY()));
                if (!mHoldGesture.isPending())
                    removeCallbacks(mHoldRunnable);
                // A finger that has travelled has said what it wanted; only a still one goes on.
                if (!mHoldGesture.reachesSelect())
                    removeCallbacks(mHoldSelectRunnable);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                cancelHoldTimers();
                applyHoldOutcome(mHoldGesture.pointerDown());
                break;
            case MotionEvent.ACTION_UP:
                cancelHoldTimers();
                applyHoldOutcome(mHoldGesture.up(event.getX(), event.getY()));
                releaseHoldDownEvent();
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelHoldTimers();
                applyHoldOutcome(mHoldGesture.cancel());
                releaseHoldDownEvent();
                break;
        }
    }

    /** Do what one touch event meant to the hold. */
    private void applyHoldOutcome(HoldGesture.Outcome outcome) {
        switch (outcome) {
            case HOLD_SELECTED:
                // The second stage: a finger that already held and kept holding. Its own haptic,
                // so going further is felt as an answer and not as the first buzz again. A plain
                // shell has no mouse to offer, so its very first stage is this one.
                if (mHoldConsumedGesture) {
                    performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
                } else if (!recogniseHold()) {
                    break;
                }
                if (!isSelectingText())
                    startTextSelectionMode(mHoldDownEvent);
                break;
            case HOLD_MOUSE:
                recogniseHold();
                break;
            case DRAG_STARTED:
                mTouchMouseDragActive = true;
                mTouchMouseDragReported = true;
                mTouchMouseDragLastCol = getColumnForX(mHoldGesture.holdX());
                mTouchMouseDragLastRow = getRowForY(mHoldGesture.holdY());
                // Distinct from the hold's own haptic, so committing to a reported drag - as
                // opposed to the finger lifting into a click - is felt.
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                sendMouseEventAt(TerminalEmulator.MOUSE_LEFT_BUTTON, mTouchMouseDragLastCol,
                    mTouchMouseDragLastRow, true);
                // The move that committed the drag is also the first move to report.
                reportTouchMouseDragTo(mHoldGesture.x(), mHoldGesture.y());
                break;
            case DRAG_MOVED:
                reportTouchMouseDragTo(mHoldGesture.x(), mHoldGesture.y());
                break;
            case DRAG_ENDED:
                releaseTouchMouseDrag();
                break;
            case CLICK:
                // The lift clicks where a plain tap would: TapPrecision picks the cell from the
                // landing and the lift together, so a thumb that rolled does not click next door.
                if (mEmulator != null && mRenderer != null && mEmulator.isMouseTrackingActive()) {
                    float[] at = TapPrecision.clickPointFor(mHoldGesture.holdX(),
                        mHoldGesture.holdY(), mHoldGesture.x(), mHoldGesture.y(),
                        mRenderer.mFontLineSpacing);
                    sendClickAt(getColumnForX(at[0]), getRowForY(at[1]), null);
                }
                break;
            case ABANDONED:
                // The hold gives the gesture back rather than keeping it: two fingers that are both
                // moving are the wheel or the pinch, and those need the scroll path again.
                mHoldConsumedGesture = false;
                break;
            case NOTHING:
            default:
                break;
        }
    }

    /**
     * The hold is recognised: it owns this gesture, the client gets the point its action sheet
     * opens at, and the user is told with one buzz.
     *
     * @return false when the client took the hold for itself and the finger is no longer ours.
     */
    private boolean recogniseHold() {
        mHoldConsumedGesture = true;
        if (mHoldDownEvent == null || mClient.onLongPress(mHoldDownEvent)) {
            mHoldGesture.cancel();
            return false;
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        return true;
    }

    /** Tell a program that asked for motion where the held button has got to. */
    private void reportTouchMouseDragTo(float x, float y) {
        if (!mTouchMouseDragActive)
            return;
        int column = getColumnForX(x);
        int row = getRowForY(y);
        if (column == mTouchMouseDragLastCol && row == mTouchMouseDragLastRow)
            return;
        mTouchMouseDragLastCol = column;
        mTouchMouseDragLastRow = row;
        sendMouseEventAt(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, column, row, true);
    }

    /** Neither stage of the hold is owed any longer. */
    private void cancelHoldTimers() {
        removeCallbacks(mHoldRunnable);
        removeCallbacks(mHoldSelectRunnable);
    }

    private void releaseHoldDownEvent() {
        if (mHoldDownEvent != null) {
            mHoldDownEvent.recycle();
            mHoldDownEvent = null;
        }
    }

    private void releaseTouchMouseDrag() {
        if (!mTouchMouseDragActive)
            return;
        mTouchMouseDragActive = false;
        sendMouseEventAt(TerminalEmulator.MOUSE_LEFT_BUTTON, mTouchMouseDragLastCol, mTouchMouseDragLastRow, false);
    }

    /**
     * Overriding {@link View#onGenericMotionEvent(MotionEvent)}.
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mEmulator != null && event.isFromSource(InputDevice.SOURCE_MOUSE) && event.getAction() == MotionEvent.ACTION_SCROLL) {
            // Handle mouse wheel scrolling.
            boolean up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f;
            doScroll(event, up ? -3 : 3);
            return true;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @TargetApi(23)
    public boolean onTouchEvent(MotionEvent event) {
        // Translated fully off the wall's own width already keeps a touch from hitting this view
        // (see PaneWallLayout#applyPagePositions); this is only the belt to that braces, since the
        // page is never INVISIBLE any more to fall back on for the framework's own touch gating.
        if (mWallOffScreen)
            return false;
        if (mEmulator == null)
            return true;
        final int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            mTouchDownX = event.getX();
            mTouchDownY = event.getY();
            mScrollDelivery.reset();
            mTouchMouseDragActive = false;
            mTouchMouseDragReported = false;
            mTapShiftBypass = false;
            mScrollAxis = SCROLL_AXIS_UNDECIDED;
        }
        handleHoldTouch(event);
        if (mTouchMouseMode && !event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (isSelectingText()) stopTextSelectionMode();
            handleTouchMouseMode(event);
            return true;
        }
        if (isSelectingText()) {
            updateFloatingToolbarVisibility(event);
            mGestureRecognizer.onTouchEvent(event);
            return true;
        } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                if (action == MotionEvent.ACTION_DOWN)
                    showContextMenu();
                return true;
            } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                doPaste();
            } else if (mEmulator.isMouseTrackingActive()) { // BUTTON_PRIMARY.
                // A pointer held with Shift is the app's, as for a finger tap: nothing is reported
                // for the whole press, and the confirmed tap opens the link under it.
                if (action == MotionEvent.ACTION_DOWN) mTapShiftBypass = (event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0;
                if (!mTapShiftBypass) switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, event.getAction() == MotionEvent.ACTION_DOWN);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                        break;
                }
            }
        }
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    private void doPaste() {
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null) {
            ClipData.Item clipItem = clipData.getItemAt(0);
            if (clipItem != null) {
                CharSequence text = clipItem.coerceToText(getContext());
                if (!TextUtils.isEmpty(text)) {
                    snapToBottomForInput();
                    mEmulator.paste(text.toString());
                }
            }
        }
    }

    @Override
    public boolean showContextMenu() {
        if (mClient != null && mClient.onShowContextMenu(this)) {
            return true;
        }
        return super.showContextMenu();
    }

    @Override
    public boolean showContextMenu(float x, float y) {
        if (mClient != null && mClient.onShowContextMenu(this)) {
            return true;
        }
        return super.showContextMenu(x, y);
    }

    /** Whether a hardware Ctrl+Space is yielded to Android (for keyboard language switching). */
    private boolean isCtrlSpacePassThrough(int keyCode, KeyEvent event) {
        return isCtrlSpacePassThrough(mClient.shouldPassCtrlSpaceToAndroid(), keyCode, event.isCtrlPressed());
    }

    static boolean isCtrlSpacePassThrough(boolean enabled, int keyCode, boolean ctrlPressed) {
        return enabled && keyCode == KeyEvent.KEYCODE_SPACE && ctrlPressed;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient.logInfo(LOG_TAG, "onKeyPreIme(keyCode=" + keyCode + ", event=" + event + ")");
        if (isCtrlSpacePassThrough(keyCode, event)) {
            return super.onKeyPreIme(keyCode, event);
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            cancelRequestAutoFill();
            if (isSelectingText()) {
                stopTextSelectionMode();
                return true;
            } else if (mClient.shouldBackButtonBeMappedToEscape()) {
                // Intercept back button to treat it as escape:
                switch(event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        return onKeyDown(keyCode, event);
                    case KeyEvent.ACTION_UP:
                        return onKeyUp(keyCode, event);
                }
            }
        } else if (mClient.shouldUseCtrlSpaceWorkaround() && keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed()) {
            /* ctrl+space does not work on some ROMs without this workaround.
               However, this breaks it on devices where it works out of the box. */
            return onKeyDown(keyCode, event);
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /**
     * Key presses in software keyboards will generally NOT trigger this listener, although some
     * may elect to do so in some situations. Do not rely on this to catch software key presses.
     * Gboard calls this when shouldEnforceCharBasedInput() is disabled (InputType.TYPE_NULL) instead
     * of calling commitText(), with deviceId=-1. However, Hacker's Keyboard, OpenBoard, LG Keyboard
     * call commitText().
     *
     * This function may also be called directly without android calling it, like by
     * `TerminalExtraKeys` which generates a KeyEvent manually which uses {@link KeyCharacterMap#VIRTUAL_KEYBOARD}
     * as the device (deviceId=-1), as does Gboard. That would normally use mappings defined in
     * `/system/usr/keychars/Virtual.kcm`. You can run `dumpsys input` to find the `KeyCharacterMapFile`
     * used by virtual keyboard or hardware keyboard. Note that virtual keyboard device is not the
     * same as software keyboard, like Gboard, etc. Its a fake device used for generating events and
     * for testing.
     *
     * We handle shift key in `commitText()` to convert codepoint to uppercase case there with a
     * call to {@link Character#toUpperCase(int)}, but here we instead rely on getUnicodeChar() for
     * conversion of keyCode, for both hardware keyboard shift key (via effectiveMetaState) and
     * `mClient.readShiftKey()`, based on value in kcm files.
     * This may result in different behaviour depending on keyboard and android kcm files set for the
     * InputDevice for the event passed to this function. This will likely be an issue for non-english
     * languages since `Virtual.kcm` in english only by default or at least in AOSP. For both hardware
     * shift key (via effectiveMetaState) and `mClient.readShiftKey()`, `getUnicodeChar()` is used
     * for shift specific behaviour which usually is to uppercase.
     *
     * For fn key on hardware keyboard, android checks kcm files for hardware keyboards, which is
     * `Generic.kcm` by default, unless a vendor specific one is defined. The event passed will have
     * {@link KeyEvent#META_FUNCTION_ON} set. If the kcm file only defines a single character or unicode
     * code point `\\uxxxx`, then only one event is passed with that value. However, if kcm defines
     * a `fallback` key for fn or others, like `key DPAD_UP { ... fn: fallback PAGE_UP }`, then
     * android will first pass an event with original key `DPAD_UP` and {@link KeyEvent#META_FUNCTION_ON}
     * set. But this function will not consume it and android will pass another event with `PAGE_UP`
     * and {@link KeyEvent#META_FUNCTION_ON} not set, which will be consumed.
     *
     * Now there are some other issues as well, firstly ctrl and alt flags are not passed to
     * `getUnicodeChar()`, so modified key values in kcm are not used. Secondly, if the kcm file
     * for other modifiers like shift or fn define a non-alphabet, like { fn: '\u0015' } to act as
     * DPAD_LEFT, the `getUnicodeChar()` will correctly return `21` as the code point but action will
     * not happen because the `handleKeyCode()` function that transforms DPAD_LEFT to `\033[D`
     * escape sequence for the terminal to perform the left action would not be called since its
     * called before `getUnicodeChar()` and terminal will instead get `21 0x15 Negative Acknowledgement`.
     * The solution to such issues is calling `getUnicodeChar()` before the call to `handleKeyCode()`
     * if user has defined a custom kcm file, like done in POC mentioned in #2237. Note that
     * Hacker's Keyboard calls `commitText()` so don't test fn/shift with it for this function.
     * https://github.com/termux/termux-app/pull/2237
     * https://github.com/agnostic-apollo/termux-app/blob/terminal-code-point-custom-mapping/terminal-view/src/main/java/com/termux/view/TerminalView.java
     *
     * Key Character Map (kcm) and Key Layout (kl) files info:
     * https://source.android.com/devices/input/key-character-map-files
     * https://source.android.com/devices/input/key-layout-files
     * https://source.android.com/devices/input/keyboard-devices
     * AOSP kcm and kl files:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/data/keyboards
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/packages/InputDevices/res/raw
     *
     * KeyCodes:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/view/KeyEvent.java
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/native/include/android/keycodes.h
     *
     * `dumpsys input`:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputflinger/reader/EventHub.cpp;l=1917
     *
     * Loading of keymap:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputflinger/reader/EventHub.cpp;l=1644
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/Keyboard.cpp;l=41
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/InputDevice.cpp
     * OVERLAY keymaps for hardware keyboards may be combined as well:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=165
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=831
     *
     * Parse kcm file:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=727
     * Parse key value:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=981
     *
     * `KeyEvent.getUnicodeChar()`
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/view/KeyEvent.java;l=2716
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/view/KeyCharacterMap.java;l=368
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/jni/android_view_KeyCharacterMap.cpp;l=117
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=231
     *
     * Keyboard layouts advertised by applications, like for hardware keyboards via #ACTION_QUERY_KEYBOARD_LAYOUTS
     * Config is stored in `/data/system/input-manager-state.xml`
     * https://github.com/ris58h/custom-keyboard-layout
     * Loading from apps:
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=1221
     * Set:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/hardware/input/InputManager.java;l=89
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/hardware/input/InputManager.java;l=543
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:packages/apps/Settings/src/com/android/settings/inputmethod/KeyboardLayoutDialogFragment.java;l=167
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=1385
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/PersistentDataStore.java
     * Get overlay keyboard layout
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=2158
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/services/core/jni/com_android_server_input_InputManagerService.cpp;l=616
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient.logInfo(LOG_TAG, "onKeyDown(keyCode=" + keyCode + ", isSystem()=" + event.isSystem() + ", event=" + event + ")");
        if (mEmulator == null)
            return true;
        if (isCtrlSpacePassThrough(keyCode, event)) {
            return super.onKeyDown(keyCode, event);
        }
        if (isSelectingText()) {
            stopTextSelectionMode();
        }
        if (mClient.onKeyDown(keyCode, event, mTermSession)) {
            invalidate();
            return true;
        } else if (event.isSystem() && (!mClient.shouldBackButtonBeMappedToEscape() || keyCode != KeyEvent.KEYCODE_BACK)) {
            return super.onKeyDown(keyCode, event);
        } else if (event.getAction() == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            mTermSession.write(event.getCharacters());
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
            return super.onKeyDown(keyCode, event);
        }
        // The kitty keyboard protocol, when the program on this session asked for it. It runs after the
        // client's own bindings so app shortcuts keep winning, and before the legacy encoders so that
        // the program gets the unambiguous form it requested.
        if (handleKittyKeyEvent(keyCode, event, event.getRepeatCount() > 0 ? KittyKeyEncoder.EVENT_REPEAT : KittyKeyEncoder.EVENT_PRESS))
            return true;
        final int metaState = event.getMetaState();
        final boolean controlDown = event.isCtrlPressed() || mClient.readControlKey();
        final boolean leftAltDown = (metaState & KeyEvent.META_ALT_LEFT_ON) != 0 || mClient.readAltKey();
        final boolean shiftDown = event.isShiftPressed() || mClient.readShiftKey();
        final boolean rightAltDownFromEvent = (metaState & KeyEvent.META_ALT_RIGHT_ON) != 0;
        int keyMod = 0;
        if (controlDown)
            keyMod |= KeyHandler.KEYMOD_CTRL;
        if (event.isAltPressed() || leftAltDown)
            keyMod |= KeyHandler.KEYMOD_ALT;
        if (shiftDown)
            keyMod |= KeyHandler.KEYMOD_SHIFT;
        if (event.isNumLockOn())
            keyMod |= KeyHandler.KEYMOD_NUM_LOCK;
        // https://github.com/termux/termux-app/issues/731
        if (!event.isFunctionPressed() && handleKeyCode(keyCode, keyMod)) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                mClient.logInfo(LOG_TAG, "handleKeyCode() took key event");
            return true;
        }
        // Clear Ctrl since we handle that ourselves:
        int bitsToClear = KeyEvent.META_CTRL_MASK;
        if (rightAltDownFromEvent) {
            // Let right Alt/Alt Gr be used to compose characters.
        } else {
            // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
            bitsToClear |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        }
        int effectiveMetaState = event.getMetaState() & ~bitsToClear;
        if (shiftDown)
            effectiveMetaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (mClient.readFnKey())
            effectiveMetaState |= KeyEvent.META_FUNCTION_ON;
        int result = event.getUnicodeChar(effectiveMetaState);
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient.logInfo(LOG_TAG, "KeyEvent#getUnicodeChar(" + effectiveMetaState + ") returned: " + result);
        if (result == 0) {
            return false;
        }
        int oldCombiningAccent = mCombiningAccent;
        if ((result & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            // If entered combining accent previously, write it out:
            if (mCombiningAccent != 0)
                inputCodePoint(event.getDeviceId(), mCombiningAccent, controlDown, leftAltDown);
            mCombiningAccent = result & KeyCharacterMap.COMBINING_ACCENT_MASK;
        } else {
            if (mCombiningAccent != 0) {
                int combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result);
                if (combinedChar > 0)
                    result = combinedChar;
                mCombiningAccent = 0;
            }
            inputCodePoint(event.getDeviceId(), result, controlDown, leftAltDown);
        }
        if (mCombiningAccent != oldCombiningAccent)
            invalidate();
        return true;
    }

    public void inputCodePoint(int eventSource, int codePoint, boolean controlDownFromEvent, boolean leftAltDownFromEvent) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            mClient.logInfo(LOG_TAG, "inputCodePoint(eventSource=" + eventSource + ", codePoint=" + codePoint + ", controlDownFromEvent=" + controlDownFromEvent + ", leftAltDownFromEvent=" + leftAltDownFromEvent + ")");
        }
        if (mTermSession == null)
            return;
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        if (mEmulator != null)
            mEmulator.setCursorBlinkState(true);
        final boolean controlDown = controlDownFromEvent || mClient.readControlKey();
        final boolean altDown = leftAltDownFromEvent || mClient.readAltKey();
        if (mClient.onCodePoint(codePoint, controlDown, mTermSession))
            return;
        if (controlDown) {
            if (codePoint >= 'a' && codePoint <= 'z') {
                codePoint = codePoint - 'a' + 1;
            } else if (codePoint >= 'A' && codePoint <= 'Z') {
                codePoint = codePoint - 'A' + 1;
            } else if (codePoint == ' ' || codePoint == '2') {
                codePoint = 0;
            } else if (codePoint == '[' || codePoint == '3') {
                // ^[ (Esc)
                codePoint = 27;
            } else if (codePoint == '\\' || codePoint == '4') {
                codePoint = 28;
            } else if (codePoint == ']' || codePoint == '5') {
                codePoint = 29;
            } else if (codePoint == '^' || codePoint == '6') {
                // control-^
                codePoint = 30;
            } else if (codePoint == '_' || codePoint == '7' || codePoint == '/') {
                // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                codePoint = 31;
            } else if (codePoint == '8') {
                // DEL
                codePoint = 127;
            }
        }
        if (codePoint > -1) {
            // If not virtual or soft keyboard.
            if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
                // Work around bluetooth keyboards sending funny unicode characters instead
                // of the more normal ones from ASCII that terminal programs expect - the
                // desire to input the original characters should be low.
                switch(codePoint) {
                    case // SMALL TILDE.
                    0x02DC:
                        // TILDE (~).
                        codePoint = 0x007E;
                        break;
                    case // MODIFIER LETTER GRAVE ACCENT.
                    0x02CB:
                        // GRAVE ACCENT (`).
                        codePoint = 0x0060;
                        break;
                    case // MODIFIER LETTER CIRCUMFLEX ACCENT.
                    0x02C6:
                        // CIRCUMFLEX ACCENT (^).
                        codePoint = 0x005E;
                        break;
                }
            }
            snapToBottomForInput();
            // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            mTermSession.writeCodePoint(altDown, codePoint);
            if (mKeyInputProbe != null) {
                probeKeyBytes("text", (altDown ? "\033" : "") + new String(Character.toChars(codePoint)));
            }
        }
    }

    /**
     * Input the specified keyCode if applicable and return if the input was consumed.
     */
    public boolean handleKeyCode(int keyCode, int keyMod) {
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        if (mEmulator != null)
            mEmulator.setCursorBlinkState(true);
        if (handleKeyCodeAction(keyCode, keyMod))
            return true;
        TerminalEmulator term = mTermSession.getEmulator();
        String code = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode());
        if (code == null)
            return false;
        snapToBottomForInput();
        mTermSession.write(code);
        probeKeyBytes("keyhandler", code);
        return true;
    }

    public boolean handleKeyCodeAction(int keyCode, int keyMod) {
        boolean shiftDown = (keyMod & KeyHandler.KEYMOD_SHIFT) != 0;
        switch(keyCode) {
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                // shift+page_up and shift+page_down should scroll scrollback history instead of
                // scrolling command history or changing pages
                if (shiftDown) {
                    long time = SystemClock.uptimeMillis();
                    MotionEvent motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
                    doScroll(motionEvent, keyCode == KeyEvent.KEYCODE_PAGE_UP ? -mEmulator.mRows : mEmulator.mRows);
                    motionEvent.recycle();
                    return true;
                }
        }
        return false;
    }

    /**
     * Called when a key is released in the view.
     *
     * @param keyCode The keycode of the key which was released.
     * @param event   A {@link KeyEvent} describing the event.
     * @return Whether the event was handled.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient.logInfo(LOG_TAG, "onKeyUp(keyCode=" + keyCode + ", event=" + event + ")");
        // Do not return for KEYCODE_BACK and send it to the client since user may be trying
        // to exit the activity.
        if (mEmulator == null && keyCode != KeyEvent.KEYCODE_BACK)
            return true;
        if (isCtrlSpacePassThrough(keyCode, event)) {
            return super.onKeyUp(keyCode, event);
        }
        if (mClient.onKeyUp(keyCode, event)) {
            invalidate();
            return true;
        } else if (event.isSystem()) {
            // Let system key events through.
            return super.onKeyUp(keyCode, event);
        }
        // Only the kitty keyboard protocol has any use for a release event; legacy encoding has none.
        handleKittyKeyEvent(keyCode, event, KittyKeyEncoder.EVENT_RELEASE);
        return true;
    }

    /**
     * Encode a key event with the kitty keyboard protocol, if the program on this session has turned it
     * on for the current screen.
     *
     * @return true when the event was dealt with, either by writing bytes or by deliberately producing
     *         none. False means the caller should carry on with legacy encoding.
     */
    private boolean handleKittyKeyEvent(int keyCode, KeyEvent event, int eventType) {
        if (mEmulator == null || mTermSession == null)
            return false;
        final int flags = mEmulator.getKeyboardFlags();
        if (flags == 0)
            return false;
        int unshifted = event.getUnicodeChar(0);
        if ((unshifted & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            // A dead key. Composition happens in the legacy path, which owns mCombiningAccent.
            return false;
        }
        int modifiers = 0;
        if (event.isCtrlPressed() || mClient.readControlKey())
            modifiers |= KittyKeyEncoder.MOD_CTRL;
        if (event.isAltPressed() || mClient.readAltKey())
            modifiers |= KittyKeyEncoder.MOD_ALT;
        if (event.isShiftPressed() || mClient.readShiftKey())
            modifiers |= KittyKeyEncoder.MOD_SHIFT;
        // Android's META is the Windows/Command key, which the protocol calls super.
        if (event.isMetaPressed())
            modifiers |= KittyKeyEncoder.MOD_SUPER;
        if (event.isCapsLockOn())
            modifiers |= KittyKeyEncoder.MOD_CAPS_LOCK;
        if (event.isNumLockOn())
            modifiers |= KittyKeyEncoder.MOD_NUM_LOCK;
        int shifted = event.getUnicodeChar(KeyEvent.META_SHIFT_ON) & ~KeyCharacterMap.COMBINING_ACCENT;
        int text = event.getUnicodeChar(event.getMetaState() & ~KeyEvent.META_CTRL_MASK) & ~KeyCharacterMap.COMBINING_ACCENT;
        String encoded = KittyKeyEncoder.encode(keyCode, unshifted, shifted, text, modifiers, eventType, flags);
        if (encoded == null)
            return false;
        if (!encoded.isEmpty()) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                mClient.logInfo(LOG_TAG, "kitty keyboard flags=" + flags + " sent " + encoded.substring(1));
            mEmulator.setCursorBlinkState(true);
            mTermSession.write(encoded);
            probeKeyBytes("kitty", encoded);
        }
        return true;
    }

    /**
     * This is called during layout when the size of this view has changed. If you were just added to the view
     * hierarchy, you're called with the old values of 0.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mTerminalSizeUpdatesPaused) {
            mTerminalSizeUpdatePending = true;
            invalidate();
        } else {
            updateSize(false, "layout");
        }
    }

    /**
     * Check if the terminal size in rows and columns should be updated.
     */
    public void updateSize() {
        updateSize(false, "direct");
    }

    /**
     * @param cause what asked for this size, for the {@link #GEOMETRY_LOG_TAG} line written when
     *              the session is actually resized: {@code layout}, {@code attach}, {@code direct}
     *              (a caller's own re-measure), or the cause a paused span was resumed with
     */
    private void updateSize(boolean keepCursorAtBottom, @NonNull String cause) {
        if (mTerminalSizeUpdatesPaused) {
            mTerminalSizeUpdatePending = true;
            invalidate();
            return;
        }
        // A travel ends on the resize it was drawn toward, whether or not the grid moves: a slide
        // that sprang back finds the grid already fitting and the displacement already zero.
        boolean travelling = mTravelActive;
        boolean reflowed = false;
        if (mSizeUpdateObserver != null) mSizeUpdateObserver.onUpdateSize(this);
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        // mRenderer may be null if the view is laid out before its font/text size is set
        // (e.g. a split pane made visible before setTextSize()). Nothing to size yet.
        // A settled host takeover may intentionally leave the pane at zero height. It still owes
        // the PTY its single final (minimum-row) size; ordinary pre-layout zeroes remain ignored.
        if (viewWidth == 0 || (viewHeight == 0 && !keepCursorAtBottom)
            || mTermSession == null || mRenderer == null) {
            if (travelling) endTravelDisplacement(false);
            return;
        }
        // Set to 80 and 24 if you want to enable vttest.
        int newColumns = Math.max(4, (int) (viewWidth / mRenderer.mFontWidth));
        int newRows = Math.max(4, (viewHeight - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);
        if (mEmulator == null || (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows)) {
            reflowed = true;
            logSessionResize(newColumns, newRows, keepCursorAtBottom, cause);
            android.os.Trace.beginSection("Terminal.updateSize");
            try {
                mTermSession.updateSize(newColumns, newRows, (int) mRenderer.getFontWidth(),
                    mRenderer.getFontLineSpacing(), keepCursorAtBottom);
                mEmulator = mTermSession.getEmulator();
                updateKittyAnimationVisibility();
                mClient.onEmulatorSet();
                // Update mTerminalCursorBlinkerRunnable inner class mEmulator on session change
                if (mTerminalCursorBlinkerRunnable != null)
                    mTerminalCursorBlinkerRunnable.setEmulator(mEmulator);
                mTopRow = 0;
                clearScrollOffset();
                scrollTo(0, 0);
                // Reflow moved every cell, so the remembered cursor cell no longer means anything.
                notifyCursorTrailSnap();
                invalidate();
            } finally {
                android.os.Trace.endSection();
            }
        }
        if (travelling) endTravelDisplacement(reflowed);
    }

    /**
     * Tag of the one line written per PTY resize, kept for confirming on a device which path a
     * resize came through: {@code adb logcat -s TermGeom}.
     */
    public static final String GEOMETRY_LOG_TAG = "TermGeom";

    /** The cause a resume carries when a return hold covered the paused span it ends. */
    public static final String CAUSE_RETURN_HOLD = "return-hold";

    private void logSessionResize(int newColumns, int newRows, boolean keepCursorAtBottom,
                                  @NonNull String cause) {
        // The session's emulator, not this view's: a freshly attached session already has a size.
        TerminalEmulator prior = mTermSession.getEmulator();
        String from = prior == null ? "none" : prior.mColumns + "x" + prior.mRows;
        android.util.Log.d(GEOMETRY_LOG_TAG, "resize " + from + " -> " + newColumns + "x" + newRows
            + " keepBottom=" + keepCursorAtBottom + " cause=" + cause
            + " returnHold=" + CAUSE_RETURN_HOLD.equals(cause));
    }

    // ---- The place slide's travel (see mTravelActive) ------------------------------------------

    /**
     * Begin drawing the grid from where it stands now, ahead of the slide's first layout. Idempotent
     * while a travel runs; {@link #setTravelDisplacement} begins one itself when none has.
     */
    public void beginTravelDisplacement() {
        if (mTravelActive) return;
        mTravelActive = true;
        mTravelAnchorOffsetPx = getVerticalContentOffset();
        mTravelAnchorHeightPx = getHeight();
        mTravelDisplacementPx = 0f;
        mTravelRevealRows = 0;
        mTravelHeadroomChangePx = 0f;
        mTravelProgress = 0f;
        mFrostOnTravelReflow = false;
        mTravelFrostHeld = false;
    }

    /**
     * One frame of the slide: the grid is drawn {@code progress} of the way to where a resize from
     * the height the travel began at to that plus {@code futureHeightDeltaPx} will put its rows,
     * centred as the settle's layout will draw it, with the buffer's bottom-anchored shift on top
     * ({@link #predictResizeDisplacementPx}). The transcript that resize will pull in above the
     * rows is drawn above them already ({@link #mTravelRevealRows}), so a growing view fills with
     * the lines it will show instead of opening empty until the settle.
     * The cursor trail snaps to the rows rather than smearing along a whole slide.
     */
    public void setTravelDisplacement(int futureHeightDeltaPx, float progress) {
        beginTravelDisplacement();
        int toHeightPx = mTravelAnchorHeightPx + futureHeightDeltaPx;
        float target = predictResizeDisplacementPx(mTravelAnchorHeightPx, toHeightPx);
        float clamped = Math.max(0f, Math.min(1f, progress));
        float displacement = target * clamped;
        int revealRows = Math.max(0, predictResizeRowShift(toHeightPx));
        float headroomChange = predictResizeHeadroomChangePx(mTravelAnchorHeightPx, toHeightPx);
        mTravelProgress = clamped;
        if (displacement == mTravelDisplacementPx && revealRows == mTravelRevealRows
            && headroomChange == mTravelHeadroomChangePx) return;
        mTravelDisplacementPx = displacement;
        mTravelRevealRows = revealRows;
        mTravelHeadroomChangePx = headroomChange;
        notifyCursorTrailSnap();
        invalidate();
    }

    /** How far down from its laid-out place the grid is drawn right now; 0 outside a travel. */
    public float getTravelDisplacementPx() {
        return mTravelActive ? mTravelDisplacementPx : 0f;
    }

    /**
     * Whether the settle's resize can be drawn ahead of it: on the normal screen the rows land
     * where {@link TerminalEmulator#predictRowsOnlyResizeShift} says. The alternate screen's
     * full-screen program repaints itself after the resize, so nothing drawn early is what it
     * will show; that travel is better frosted throughout ({@link #holdTravelFrost}).
     */
    public boolean canPlaceTravelRows() {
        return mEmulator != null && !mEmulator.isAlternateBufferActive();
    }

    /**
     * Frosts the grid for the rest of this travel: the blur comes in over
     * {@link #TRAVEL_FROST_IN_MS} and stays until the travel ends, reflowed or sprung back, when
     * it thaws as a reflow's frost does. For rows the travel cannot place
     * ({@link #canPlaceTravelRows}); nothing outside a travel, and nothing below API 31. The
     * caller gates it on motion the way it gates the settle's frost.
     */
    public void holdTravelFrost() {
        if (!mTravelActive || mTravelFrostHeld) return;
        mTravelFrostHeld = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            animateReflowFrost(1f, TRAVEL_FROST_IN_MS);
    }

    /** Whether this travel is frosted throughout; see {@link #holdTravelFrost}. */
    boolean isTravelFrostHeld() {
        return mTravelActive && mTravelFrostHeld;
    }

    /**
     * The slide landed. A resize that is paused or pending ends the travel itself as it lands
     * ({@link #updateSize}), so the rows keep their place across the frames between the settle's
     * layout and the resize it posts; with none owed the travel ends now.
     *
     * @param frostReflow whether the reflow that ends the travel is drawn under a brief frost that
     *                    thaws to the sharp rows — the mask for whatever the prediction could not
     *                    place: a line that rewraps, a full-screen program's own repaint
     */
    public void settleTravelDisplacement(boolean frostReflow) {
        if (!mTravelActive) return;
        mFrostOnTravelReflow = frostReflow;
        if (mTerminalSizeUpdatesPaused || mTerminalSizeUpdatePending) return;
        endTravelDisplacement(false);
    }

    private void endTravelDisplacement(boolean reflowed) {
        if (!mTravelActive) return;
        mTravelActive = false;
        mTravelDisplacementPx = 0f;
        mTravelRevealRows = 0;
        mTravelHeadroomChangePx = 0f;
        mTravelProgress = 0f;
        // A held frost thaws whichever way the travel ends; otherwise only a reflow the settle
        // asked to frost is.
        boolean held = mTravelFrostHeld;
        boolean frost = reflowed && mFrostOnTravelReflow;
        mTravelFrostHeld = false;
        mFrostOnTravelReflow = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (held) animateReflowFrost(0f, REFLOW_FROST_MS);
            else if (frost) frostReflow();
        }
        notifyCursorTrailSnap();
        invalidate();
    }

    /**
     * How far, in pixels, the rows drawn now move when this view is resized from
     * {@code fromHeightPx} to {@code toHeightPx} at the same columns with the settle's bottom
     * anchor. Exact for a rows-only resize (see {@link TerminalEmulator#predictRowsOnlyResizeShift});
     * 0 before there is a grid to speak of.
     */
    float predictResizeDisplacementPx(int fromHeightPx, int toHeightPx) {
        if (!canPredictResize(fromHeightPx, toHeightPx)) return 0f;
        int toRows = rowsForHeight(toHeightPx);
        int rowShift = mEmulator.predictRowsOnlyResizeShift(toRows, true);
        return travelDisplacementPx(fromHeightPx, toHeightPx, mEmulator.mRows, toRows, rowShift,
            mRenderer.mFontLineSpacing, mRenderer.mFontLineSpacingAndAscent);
    }

    /** How many rows down the settle's resize to {@code toHeightPx} moves the rows on screen. */
    private int predictResizeRowShift(int toHeightPx) {
        if (!canPredictResize(mTravelAnchorHeightPx, toHeightPx)) return 0;
        return mEmulator.predictRowsOnlyResizeShift(rowsForHeight(toHeightPx), true);
    }

    /** What the centring slack above the grid changes by across that resize. */
    private float predictResizeHeadroomChangePx(int fromHeightPx, int toHeightPx) {
        if (!canPredictResize(fromHeightPx, toHeightPx)) return 0f;
        return travelHeadroomChangePx(fromHeightPx, toHeightPx, mEmulator.mRows,
            rowsForHeight(toHeightPx), mRenderer.mFontLineSpacing,
            mRenderer.mFontLineSpacingAndAscent);
    }

    private boolean canPredictResize(int fromHeightPx, int toHeightPx) {
        return mEmulator != null && mRenderer != null && fromHeightPx > 0 && toHeightPx > 0
            && mRenderer.mFontLineSpacing > 0;
    }

    /** The rows {@link #updateSize} gives a view this tall. */
    private int rowsForHeight(int heightPx) {
        return Math.max(4, (heightPx - mRenderer.mFontLineSpacingAndAscent)
            / mRenderer.mFontLineSpacing);
    }

    /**
     * The arithmetic behind {@link #predictResizeDisplacementPx}: the grid is drawn centred in
     * its view ({@link #getVerticalContentOffset}), so a row's move is the change in the slack
     * above the grid plus the rows the buffer shifts the screen by. The same on either buffer.
     *
     * @param rowShift how many rows down each surviving row lands, from the buffer's prediction
     */
    static float travelDisplacementPx(int fromHeightPx, int toHeightPx, int fromRows, int toRows,
                                      int rowShift, int lineSpacingPx, int ascentPx) {
        return travelHeadroomChangePx(fromHeightPx, toHeightPx, fromRows, toRows, lineSpacingPx,
            ascentPx) + rowShift * (float) lineSpacingPx;
    }

    /** The slack part of that move: how far the centred grid's top edge moves with the resize. */
    static float travelHeadroomChangePx(int fromHeightPx, int toHeightPx, int fromRows,
                                        int toRows, int lineSpacingPx, int ascentPx) {
        int fromHeadroom = centredSlackPx(fromHeightPx, fromRows * lineSpacingPx + ascentPx);
        int toHeadroom = centredSlackPx(toHeightPx, toRows * lineSpacingPx + ascentPx);
        return toHeadroom - fromHeadroom;
    }

    /**
     * How many rows above {@code topRow} a travel draws: the rows the settle's resize reveals
     * ({@code revealRows}), as far as the transcript reaches. Past the transcript's first line
     * the resize shows blank rows, which is what drawing nothing there shows too.
     */
    static int travelFillRows(int revealRows, int topRow, int activeTranscriptRows) {
        return Math.max(0, Math.min(revealRows, topRow + activeTranscriptRows));
    }

    /**
     * Where, in this view's pixels, a travel that reveals rows cuts its drawing off at the top:
     * the grid's top edge as the settle's layout will move it, {@code progress} of the way from
     * where it stood when the travel began. The revealed rows come out from under this edge as
     * the grid travels down, so at the travel's start none of them shows in the slack above the
     * grid, and at its end the edge is exactly where the resized grid begins.
     */
    static float travelFillClipTopPx(float anchorOffsetPx, float headroomChangePx, float progress,
                                     int ascentPx) {
        return anchorOffsetPx + headroomChangePx * progress + ascentPx;
    }

    /** The rows above {@link #mTopRow} this frame draws; 0 outside a travel that reveals any. */
    int currentTravelFillRows() {
        if (!mTravelActive || mTravelRevealRows <= 0 || mEmulator == null
            || mEmulator.isAlternateBufferActive())
            return 0;
        return travelFillRows(mTravelRevealRows, mTopRow,
            mEmulator.getScreen().getActiveTranscriptRows());
    }

    /**
     * M1's frost-on-reflow, in the default mode: the rows the reflow just laid are drawn under a
     * blur about half a row deep that thaws over {@link #REFLOW_FROST_MS}. A RenderEffect on this
     * view alone, so the glass behind the text stays sharp. Below API 31 the reflow lands plain.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private void frostReflow() {
        cancelReflowFrostAnimation();
        mReflowFrostLevel = 1f;
        applyReflowFrost(reflowFrostRadiusPx());
        animateReflowFrost(0f, REFLOW_FROST_MS);
    }

    /**
     * Carries the frost from the depth it stands at to {@code toLevel} (1 is the full blur, 0
     * none) over {@code durationMs}, on the reflow frost's decelerating curve. A frost already
     * changing is taken on from where it has got to.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private void animateReflowFrost(float toLevel, long durationMs) {
        cancelReflowFrostAnimation();
        final float radius = reflowFrostRadiusPx();
        if (mReflowFrostLevel == toLevel) {
            applyReflowFrost(radius * toLevel);
            return;
        }
        ValueAnimator frost = ValueAnimator.ofFloat(mReflowFrostLevel, toLevel);
        frost.setDuration(durationMs);
        frost.setInterpolator(new DecelerateInterpolator());
        frost.addUpdateListener(animation -> {
            if (mReflowFrost != animation) return;
            mReflowFrostLevel = (float) animation.getAnimatedValue();
            applyReflowFrost(radius * mReflowFrostLevel);
        });
        frost.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (mReflowFrost != animation) return;
                mReflowFrost = null;
                mReflowFrostLevel = toLevel;
                applyReflowFrost(radius * toLevel);
            }
        });
        mReflowFrost = frost;
        frost.start();
    }

    /** Stops a frost mid-change, leaving it at the depth it had reached. */
    private void cancelReflowFrostAnimation() {
        ValueAnimator running = mReflowFrost;
        mReflowFrost = null;
        if (running != null) running.cancel();
    }

    /** Takes any frost off at once: a view leaving the window keeps none. */
    private void clearReflowFrost() {
        cancelReflowFrostAnimation();
        mTravelFrostHeld = false;
        if (mReflowFrostLevel == 0f) return;
        mReflowFrostLevel = 0f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) applyReflowFrost(0f);
    }

    /** About half a row deep. */
    private float reflowFrostRadiusPx() {
        return Math.max(2f, mRenderer == null ? 8f : mRenderer.mFontLineSpacing * 0.45f);
    }

    /** The blur radius last handed to setRenderEffect, on the half-pixel grid; 0 for none. */
    private float mAppliedReflowFrostRadiusPx;

    /**
     * Sets the frost's blur. The radius is taken to the nearest half pixel, finer than the eye can
     * tell, and an animator tick that lands on the radius already set builds no new effect.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private void applyReflowFrost(float radiusPx) {
        float radius = radiusPx < 0.5f ? 0f : Math.round(radiusPx * 2f) / 2f;
        if (radius == mAppliedReflowFrostRadiusPx) return;
        mAppliedReflowFrostRadiusPx = radius;
        setRenderEffect(radius == 0f ? null
            : RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }

    /** Coalesce transient layout changes without forwarding every one to the attached PTY. */
    public void setTerminalSizeUpdatesPaused(boolean paused) {
        setTerminalSizeUpdatesPaused(paused, false);
    }

    /** Resume a coalesced resize with optional bottom anchoring after the final layout pass. */
    public void setTerminalSizeUpdatesPaused(boolean paused,
                                             boolean keepCursorAtBottomOnResume) {
        setTerminalSizeUpdatesPaused(paused, keepCursorAtBottomOnResume, "resume");
    }

    /**
     * As {@link #setTerminalSizeUpdatesPaused(boolean, boolean)}, naming what ended the pause for
     * the resize log line ({@link #CAUSE_RETURN_HOLD} when a return hold covered it).
     */
    public void setTerminalSizeUpdatesPaused(boolean paused, boolean keepCursorAtBottomOnResume,
                                             @NonNull String resumeCause) {
        if (mTerminalSizeUpdatesPaused == paused) return;
        mTerminalSizeUpdatesPaused = paused;
        if (!paused && mTerminalSizeUpdatePending) {
            mTerminalSizeUpdatePending = false;
            // Run after the final split layout pass so only the settled geometry reaches the PTY.
            new Handler(Looper.getMainLooper()).post(
                () -> updateSize(keepCursorAtBottomOnResume, resumeCause));
        }
    }

    /** Resume a cached, currently hidden pane without delivering its stale detached geometry. */
    public void resumeTerminalSizeUpdatesDiscardingPending() {
        mTerminalSizeUpdatePending = false;
        mTerminalSizeUpdatesPaused = false;
    }

    public void setSizeUpdateObserverForTests(SizeUpdateObserver observer) {
        mSizeUpdateObserver = observer;
    }

    /** Current rendered character-cell width, or zero until a renderer has been configured. */
    public float getTerminalCellWidthPixels() {
        return mRenderer == null ? 0f : mRenderer.getFontWidth();
    }

    /** Rendered text size in pixels, or zero until a renderer is configured. */
    public float getTerminalTextSizePixels() {
        return mRenderer == null ? 0f : mRenderer.mTextSize;
    }

    /** The face the transcript is drawn in, so a surface about the terminal can match it. */
    @Nullable
    public android.graphics.Typeface getTerminalTypeface() {
        return mRenderer == null ? null : mRenderer.mTypeface;
    }

    /** Current rendered character-cell line height, or zero until a renderer is configured. */
    public float getTerminalCellHeightPixels() {
        return mRenderer == null ? 0f : mRenderer.getFontLineSpacing();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mEmulator == null) {
            canvas.drawColor(0XFF000000);
        } else {
            long drawStartNanos = SystemClock.elapsedRealtimeNanos();
            // render the terminal view and highlight any selected text
            int[] sel = mDefaultSelectors;
            if (mTextSelectionCursorController != null) {
                mTextSelectionCursorController.getSelectors(sel);
            }
            final float scrollOffset = mScrollOffsetPixels;
            final float drawOffset = currentDrawOffset();
            computeEdgeColorsIfEnabled();
            final boolean paintingPaddingFill = mPaddingFillEnabled && mEdgeColorsColumns > 0;
            final boolean canvasTranslated = drawOffset != 0f || paintingPaddingFill
                || mTravelActive;
            // A travel toward a taller grid draws the transcript its resize will reveal above the
            // rows, where the resize will put it: drawing starts that many rows up and is moved up
            // by as many lines, so mTopRow, hit-testing and every row below stay where they were.
            final int fillRows = currentTravelFillRows();
            final float fillClipTop = fillRows > 0 ? travelFillClipTopPx(mTravelAnchorOffsetPx,
                mTravelHeadroomChangePx, mTravelProgress, mRenderer.mFontLineSpacingAndAscent) : 0f;
            final int drawTopRow = mTopRow - fillRows;
            final int extraRows = fillRows + (scrollOffset != 0f ? 1 : 0);
            if (canvasTranslated) {
                canvas.save();
                // Rows displaced past this view's edges stay inside it: the pane frame around
                // this view lets its children draw outside their bounds on purpose.
                if (mTravelActive) canvas.clipRect(0, 0, getWidth(), getHeight());
                canvas.translate(0f, drawOffset);
            }
            if (paintingPaddingFill)
                drawPaddingFill(canvas, getHorizontalContentOffset(), drawOffset,
                    fillRows > 0 ? fillClipTop - drawOffset : Float.NaN);
            if (fillRows > 0) {
                // The revealed rows come out from under the grid's moving top edge, so none of
                // them shows in the slack above the grid before the travel has made room for it.
                // The frame's ground is laid first, since the render's own would stop at the cut.
                mRenderer.clearFrame(mEmulator, canvas, mUseTransparentFrameClear,
                    mTransparentFrameOverlayColor);
                canvas.clipRect(0f, fillClipTop - drawOffset, getWidth(), getHeight() - drawOffset);
                canvas.translate(0f, -fillRows * (float) mRenderer.mFontLineSpacing);
            }
            mRenderer.render(mEmulator, canvas, drawTopRow, sel[0], sel[1], sel[2], sel[3], mUseTransparentFrameClear, mTransparentFrameOverlayColor, getHorizontalContentOffset(), extraRows);
            if (mFindOverlay != null) {
                mRenderer.renderFindOverlay(mEmulator, canvas, drawTopRow, mFindOverlay,
                    getHorizontalContentOffset(), extraRows);
            }
            // The trail itself is drawn by whoever composes the panes (it spans all of them, not
            // just this view); this pane only reports that its cursor may have moved, or — while
            // selecting text — that it should be hidden and not smeared into.
            if (mCursorTrailListener != null) {
                if (isSelectingText())
                    mCursorTrailListener.onCursorTrailSnap(this);
                else
                    mCursorTrailListener.onCursorMayHaveMoved(this);
            }
            if (canvasTranslated)
                canvas.restore();
            // render the text selection handles
            renderTextSelection();
            long drawEndNanos = SystemClock.elapsedRealtimeNanos();
            mRenderMetrics.recordDraw(drawStartNanos, drawEndNanos, mFrameBudgetNanos);
        }
    }

    /** One frame at the display's refresh rate, for the render metrics; read on attach and on display change. */
    private long mFrameBudgetNanos = 16_666_667L;

    private final android.hardware.display.DisplayManager.DisplayListener mDisplayListener =
        new android.hardware.display.DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int displayId) { }

            @Override public void onDisplayRemoved(int displayId) { }

            @Override public void onDisplayChanged(int displayId) {
                android.view.Display display = getDisplay();
                if (display != null && display.getDisplayId() == displayId) refreshFrameBudget();
            }
        };

    private void refreshFrameBudget() {
        android.view.Display display = getDisplay();
        float refreshRate = display == null ? 60f : display.getRefreshRate();
        mFrameBudgetNanos = refreshRate > 0f
            ? (long) (1_000_000_000d / refreshRate) : 16_666_667L;
    }

    /** Snapshot of this pane's renderer counters. Percentiles allocate only when queried. */
    public TerminalRenderMetrics.Snapshot getRenderMetricsSnapshot() {
        return mRenderMetrics.snapshot();
    }

    /** Starts a fresh benchmark window for this pane. */
    public void resetRenderMetrics() {
        mRenderMetrics.reset();
    }

    /** Install or remove the key input diagnostic sink. */
    public void setKeyInputProbe(@Nullable KeyInputProbe probe) {
        mKeyInputProbe = probe;
    }

    private void probeKeyBytes(String encoder, String bytes) {
        KeyInputProbe probe = mKeyInputProbe;
        if (probe != null)
            probe.onKeyBytesWritten(encoder, bytes);
    }

    /**
     * Whether the cursor animates between cells. Off by policy - power save, or the user's preference -
     * rather than by the view's own judgement.
     * <p>
     * The trail itself is owned by whoever composes the panes, not by this view, so this only
     * remembers the flag and forwards it — see {@link CursorTrailListener#onCursorTrailEnabledChanged}.
     */
    public void setCursorTrailEnabled(boolean enabled) {
        if (mCursorTrailEnabled == enabled)
            return;
        mCursorTrailEnabled = enabled;
        if (mCursorTrailListener != null)
            mCursorTrailListener.onCursorTrailEnabledChanged(this, enabled);
    }

    public boolean isCursorTrailEnabled() {
        return mCursorTrailEnabled;
    }

    /**
     * Installs the composer's cursor trail listener. Replays the current enabled state and, when a
     * pane is being attached fresh, asks for a snap rather than letting a stale cell smear in.
     */
    public void setCursorTrailListener(@Nullable CursorTrailListener listener) {
        mCursorTrailListener = listener;
        if (listener != null) listener.onCursorTrailEnabledChanged(this, mCursorTrailEnabled);
    }

    /**
     * Notifies the installed {@link CursorTrailListener}, if any, that this pane hit a
     * discontinuity — a session switch, a resize/reflow, or a scroll/prompt jump — across which the
     * trail must not smear.
     */
    private void notifyCursorTrailSnap() {
        if (mCursorTrailListener != null) mCursorTrailListener.onCursorTrailSnap(this);
    }

    /**
     * The pane composer's window onto one pane's cursor: whether it may have moved this frame, a
     * discontinuity it must not be smeared across, and the enabled policy — the app's
     * {@code TermuxTerminalViewClient#applyCursorTrailPolicy} — applied to it. The unified trail
     * itself lives above every pane, not inside any one {@link TerminalView}, which is why this is
     * a callback rather than something the view draws for itself.
     */
    public interface CursorTrailListener {
        /** This pane's cursor cell, shape or visibility may have changed since the last frame. */
        void onCursorMayHaveMoved(@NonNull TerminalView view);

        /** A discontinuity: forget any in-flight trail and snap to the current cell next frame. */
        void onCursorTrailSnap(@NonNull TerminalView view);

        /** The policy — preference and power state — changed for this pane. */
        void onCursorTrailEnabledChanged(@NonNull TerminalView view, boolean enabled);
    }

    public void setUseTransparentFrameClear(boolean useTransparentFrameClear) {
        if (mUseTransparentFrameClear == useTransparentFrameClear) return;
        mUseTransparentFrameClear = useTransparentFrameClear;
        invalidate();
    }

    public void setTransparentFrameOverlayColor(int transparentFrameOverlayColor) {
        if (mTransparentFrameOverlayColor == transparentFrameOverlayColor) return;
        mTransparentFrameOverlayColor = transparentFrameOverlayColor;
        invalidate();
    }

    /** See {@link #mPaddingFillEnabled}. */
    public void setPaddingFillEnabled(boolean enabled) {
        if (mPaddingFillEnabled == enabled) return;
        mPaddingFillEnabled = enabled;
        if (!enabled) clearEdgeColors();
        invalidate();
    }

    public boolean isPaddingFillEnabled() {
        return mPaddingFillEnabled;
    }

    /** The pane frame around this view calls this once it knows this view is its terminal child. */
    public void setPaddingFillListener(@Nullable PaddingFillListener listener) {
        mPaddingFillListener = listener;
    }

    /** How many columns/rows this frame's edge-colour arrays describe; 0 when there is nothing to
     *  paint (disabled, or no emulator attached yet). */
    public int getEdgeColumnCount() {
        return mEdgeColorsColumns;
    }

    public int getEdgeRowCount() {
        return mEdgeColorsRows;
    }

    /** The colour to extend into the gutter above/below column {@code column}; 0 for none. */
    public int getEdgeColumnColorTop(int column) {
        return column >= 0 && column < mEdgeColorsColumns ? mEdgeTopColors[column] : 0;
    }

    public int getEdgeColumnColorBottom(int column) {
        return column >= 0 && column < mEdgeColorsColumns ? mEdgeBottomColors[column] : 0;
    }

    /** The colour to extend into the gutter left/right of row {@code row}; 0 for none. */
    public int getEdgeRowColorLeft(int row) {
        return row >= 0 && row < mEdgeColorsRows ? mEdgeLeftColors[row] : 0;
    }

    public int getEdgeRowColorRight(int row) {
        return row >= 0 && row < mEdgeColorsRows ? mEdgeRightColors[row] : 0;
    }

    /**
     * This view's own left edge of column {@code column}, in this view's coordinate space, rounded
     * to the exact same device pixel {@link TerminalRenderer}'s {@code drawCellRect} snaps a cell's
     * own left edge to. {@code column} may run one past the last real column, to read the right edge
     * of the last one — the formula stays valid past the emulator's width, since it never indexes an
     * array.
     *
     * <p>Column widths are not all equal: {@code fontWidth} is rarely a whole number of pixels, so
     * rounding each column's edge independently — the way the grid itself is drawn — lets one column
     * in a run absorb an extra pixel rather than leaving every column a fraction short. Handing the
     * pane frame outside this view this same rounding, rather than a float it would round its own
     * way, is what keeps its band meeting the grid with no hairline gap at any column boundary.
     */
    public float getPaddingColumnLeft(int column) {
        return mRenderer == null ? 0f : Math.round(getHorizontalContentOffset() + column * mRenderer.getFontWidth());
    }

    /**
     * This view's own top edge of row {@code row}, in this view's coordinate space, rounded the same
     * way {@code drawCellRect} rounds a row's own top edge. {@code row} may run one past the last
     * real row, to read the bottom edge of the last one.
     */
    public float getPaddingRowTop(int row) {
        if (mRenderer == null) return 0f;
        float drawOffset = getVerticalContentOffset() - mScrollOffsetPixels;
        return Math.round(drawOffset + mRenderer.getFontLineSpacingAndAscent() + row * mRenderer.getFontLineSpacing());
    }

    /**
     * {@link #computeEdgeColors()} if padding fill is on, otherwise a no-op. Called both from this
     * view's own {@link #onDraw} and, before that, from {@code PaneContentFrame#dispatchDraw} — the
     * frame around this view draws its padding-fill band from these same colours just before this
     * view is drawn, and used to draw last frame's, because this view's own {@code onDraw} had not
     * run yet to refresh them for the frame under way. Calling it again from here costs one cheap,
     * allocation-free pass and changes nothing, since the colours calling it early already leaves
     * behind are this frame's.
     */
    public void computeEdgeColorsIfEnabled() {
        if (mPaddingFillEnabled) computeEdgeColors();
    }

    /**
     * Recompute this frame's edge colours from the current emulator screen, and tell
     * {@link #mPaddingFillListener} when they differ from last frame's — so the pane frame outside
     * this view knows to repaint its own band without being asked on every frame that redraws the
     * same background (a blinking cursor, a spinner glyph turning over).
     *
     * <p>O(rows + columns): one style lookup per edge cell, no allocation once the arrays have
     * grown to this screen's size.
     */
    private void computeEdgeColors() {
        if (mEmulator == null || mRenderer == null) {
            clearEdgeColors();
            return;
        }
        final int columns = mEmulator.mColumns;
        final int rows = mEmulator.mRows;
        if (columns <= 0 || rows <= 0) {
            clearEdgeColors();
            return;
        }
        if (mEdgeTopColors.length < columns) {
            mEdgeTopColors = new int[columns];
            mEdgeBottomColors = new int[columns];
        }
        if (mEdgeLeftColors.length < rows) {
            mEdgeLeftColors = new int[rows];
            mEdgeRightColors = new int[rows];
        }
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final boolean boldWithBright = mEmulator.isBoldWithBright();
        final boolean reverseVideo = mEmulator.isReverseVideo();
        // Global reverse video floods the whole canvas with the palette foreground colour
        // (TerminalRenderer#renderRows), so a "default" cell's resolved background is then that
        // foreground — and the gutter needs no fill of its own there either.
        final int defaultBack = reverseVideo
            ? palette[TextStyle.COLOR_INDEX_FOREGROUND] : palette[TextStyle.COLOR_INDEX_BACKGROUND];
        final TerminalBuffer screen = mEmulator.getScreen();
        final int topExternalRow = mTopRow;
        final int bottomExternalRow = mTopRow + rows - 1;
        for (int c = 0; c < columns; c++) {
            mEdgeTopColors[c] = edgeBackColor(screen, topExternalRow, c, palette, boldWithBright, reverseVideo, defaultBack);
            mEdgeBottomColors[c] = edgeBackColor(screen, bottomExternalRow, c, palette, boldWithBright, reverseVideo, defaultBack);
        }
        for (int r = 0; r < rows; r++) {
            final int externalRow = mTopRow + r;
            mEdgeLeftColors[r] = edgeBackColor(screen, externalRow, 0, palette, boldWithBright, reverseVideo, defaultBack);
            mEdgeRightColors[r] = edgeBackColor(screen, externalRow, columns - 1, palette, boldWithBright, reverseVideo, defaultBack);
        }
        final boolean changed = columns != mEdgeColorsColumns || rows != mEdgeColorsRows
            || !edgeColorsMatch(mEdgeTopColors, mPrevEdgeTopColors, columns)
            || !edgeColorsMatch(mEdgeBottomColors, mPrevEdgeBottomColors, columns)
            || !edgeColorsMatch(mEdgeLeftColors, mPrevEdgeLeftColors, rows)
            || !edgeColorsMatch(mEdgeRightColors, mPrevEdgeRightColors, rows);
        mEdgeColorsColumns = columns;
        mEdgeColorsRows = rows;
        if (changed) {
            if (mPrevEdgeTopColors.length < columns) {
                mPrevEdgeTopColors = new int[columns];
                mPrevEdgeBottomColors = new int[columns];
            }
            if (mPrevEdgeLeftColors.length < rows) {
                mPrevEdgeLeftColors = new int[rows];
                mPrevEdgeRightColors = new int[rows];
            }
            System.arraycopy(mEdgeTopColors, 0, mPrevEdgeTopColors, 0, columns);
            System.arraycopy(mEdgeBottomColors, 0, mPrevEdgeBottomColors, 0, columns);
            System.arraycopy(mEdgeLeftColors, 0, mPrevEdgeLeftColors, 0, rows);
            System.arraycopy(mEdgeRightColors, 0, mPrevEdgeRightColors, 0, rows);
            if (mPaddingFillListener != null) mPaddingFillListener.onPaddingFillColorsChanged();
        }
    }

    private static boolean edgeColorsMatch(int[] current, int[] previous, int count) {
        if (previous.length < count) return false;
        for (int i = 0; i < count; i++) if (current[i] != previous[i]) return false;
        return true;
    }

    /** A cell's resolved background colour, or 0 (transparent) when it is the default background —
     *  reusing {@link TerminalRenderer#resolveRunColors} so the gutter and the grid never disagree
     *  about what a cell's background actually is. */
    private static int edgeBackColor(TerminalBuffer screen, int externalRow, int column, int[] palette,
                                      boolean boldWithBright, boolean reverseVideo, int defaultBack) {
        final long style = screen.getStyleAt(externalRow, column);
        final int backColor = (int) TerminalRenderer.resolveRunColors(style, palette, boldWithBright, reverseVideo);
        return backColor == defaultBack ? 0 : backColor;
    }

    private void clearEdgeColors() {
        if (mEdgeColorsColumns == 0 && mEdgeColorsRows == 0) return;
        mEdgeColorsColumns = 0;
        mEdgeColorsRows = 0;
        if (mPaddingFillListener != null) mPaddingFillListener.onPaddingFillColorsChanged();
    }

    /**
     * Paint the in-view slack the centred grid leaves against this view's own edges: the headroom
     * above row 0 and the leftover below the last row, and the centring margin either side of the
     * grid when the view is wider than its columns need. The first and last run of every band
     * reach the view's own corner, so the four squares where a vertical band meets a horizontal
     * one are painted too — by both, in the one colour the corner cell has — and nothing behind
     * the grid shows through beside its slack. Called inside the same translate {@link #onDraw}
     * applies before rendering the grid, so row math lines up with the renderer's exactly, scroll
     * animation included.
     *
     * <p>Every rect below shares its edges with {@code getPaddingColumnLeft}/{@code getPaddingRowTop}
     * — the same {@code Math.round(offset + n * step)} {@code drawCellRect} snaps a cell's own edges
     * to — and runs of equal colour are coalesced into a single rect first, so two neighbouring
     * columns or rows of the same colour share no internal edge at all. That, together with the fill
     * paint's anti-aliasing being off, is what keeps a translucent hairline from ever forming at a
     * column or row boundary: there is nothing left to blend at a boundary between rects, and no
     * boundary at all between same-coloured ones.
     *
     * <p>The wider band outside this view — the pane's own rounded-corner clearance — is not this
     * view's to paint; {@code PaneContentFrame} reads these same colours through the accessors above
     * and extends them the rest of the way to the pane's border, using the same rounding.
     *
     * @param topBandBottom where the headroom band stops, in the translated canvas's coordinates:
     *                      NaN for row 0's top edge, or the cut a travel's revealed rows come out
     *                      from under, so the band never lies beneath them
     */
    private void drawPaddingFill(Canvas canvas, float horizontalOffset, float drawOffset,
                                 float topBandBottom) {
        final float fontWidth = mRenderer.getFontWidth();
        final float fontLineSpacing = mRenderer.getFontLineSpacing();
        final float firstRowTop = Math.round(mRenderer.getFontLineSpacingAndAscent());
        final float topBandEnd = Float.isNaN(topBandBottom) ? firstRowTop
            : Math.min(firstRowTop, Math.round(topBandBottom));
        final float viewWidth = getWidth();
        final float viewTop = -drawOffset;
        final float viewBottom = getHeight() - drawOffset;
        final float rowsBottom = Math.round(firstRowTop + mEdgeColorsRows * fontLineSpacing);
        int c = 0;
        while (c < mEdgeColorsColumns) {
            final int topColor = mEdgeTopColors[c];
            int runEnd = c + 1;
            while (runEnd < mEdgeColorsColumns && mEdgeTopColors[runEnd] == topColor) runEnd++;
            if (topColor != 0) {
                final float left = c == 0 ? 0f : Math.round(horizontalOffset + c * fontWidth);
                final float right = runEnd == mEdgeColorsColumns
                    ? viewWidth : Math.round(horizontalOffset + runEnd * fontWidth);
                mPaddingFillPaint.setColor(topColor);
                canvas.drawRect(left, viewTop, right, topBandEnd, mPaddingFillPaint);
            }
            c = runEnd;
        }
        c = 0;
        while (c < mEdgeColorsColumns) {
            final int bottomColor = mEdgeBottomColors[c];
            int runEnd = c + 1;
            while (runEnd < mEdgeColorsColumns && mEdgeBottomColors[runEnd] == bottomColor) runEnd++;
            if (bottomColor != 0) {
                final float left = c == 0 ? 0f : Math.round(horizontalOffset + c * fontWidth);
                final float right = runEnd == mEdgeColorsColumns
                    ? viewWidth : Math.round(horizontalOffset + runEnd * fontWidth);
                mPaddingFillPaint.setColor(bottomColor);
                canvas.drawRect(left, rowsBottom, right, viewBottom, mPaddingFillPaint);
            }
            c = runEnd;
        }
        if (horizontalOffset > 0f) {
            final float leftSlackRight = Math.round(horizontalOffset);
            final float rightSlackLeft = viewWidth - leftSlackRight;
            int r = 0;
            while (r < mEdgeColorsRows) {
                final int leftColor = mEdgeLeftColors[r];
                int runEnd = r + 1;
                while (runEnd < mEdgeColorsRows && mEdgeLeftColors[runEnd] == leftColor) runEnd++;
                if (leftColor != 0) {
                    final float top = r == 0 ? viewTop : Math.round(firstRowTop + r * fontLineSpacing);
                    final float bottom = runEnd == mEdgeColorsRows
                        ? viewBottom : Math.round(firstRowTop + runEnd * fontLineSpacing);
                    mPaddingFillPaint.setColor(leftColor);
                    canvas.drawRect(0f, top, leftSlackRight, bottom, mPaddingFillPaint);
                }
                r = runEnd;
            }
            r = 0;
            while (r < mEdgeColorsRows) {
                final int rightColor = mEdgeRightColors[r];
                int runEnd = r + 1;
                while (runEnd < mEdgeColorsRows && mEdgeRightColors[runEnd] == rightColor) runEnd++;
                if (rightColor != 0) {
                    final float top = r == 0 ? viewTop : Math.round(firstRowTop + r * fontLineSpacing);
                    final float bottom = runEnd == mEdgeColorsRows
                        ? viewBottom : Math.round(firstRowTop + runEnd * fontLineSpacing);
                    mPaddingFillPaint.setColor(rightColor);
                    canvas.drawRect(rightSlackLeft, top, viewWidth, bottom, mPaddingFillPaint);
                }
                r = runEnd;
            }
        }
    }

    public TerminalSession getCurrentSession() {
        return mTermSession;
    }

    private CharSequence getText() {
        if (mEmulator == null) return "";
        return mEmulator.getScreen().getSelectedText(0, mTopRow, mEmulator.mColumns, mTopRow + mEmulator.mRows);
    }

    public int getCursorX(float x) {
        return (int) ((x - getHorizontalContentOffset()) / mRenderer.mFontWidth);
    }

    public int getCursorY(float y) {
        return (int) (((y - getVerticalContentOffset() - mRenderer.mFontLineSpacingAndAscent)
            / mRenderer.mFontLineSpacing) + mTopRow);
    }

    public int getPointX(int cx) {
        if (cx > mEmulator.mColumns) {
            cx = mEmulator.mColumns;
        }
        return Math.round(getHorizontalContentOffset() + (cx * mRenderer.mFontWidth));
    }

    public int getPointY(int cy) {
        return Math.round((cy - mTopRow) * mRenderer.mFontLineSpacing + getVerticalContentOffset());
    }

    public float getHorizontalContentOffset() {
        if (mEmulator == null || mRenderer == null) {
            return 0f;
        }
        float contentWidth = mEmulator.mColumns * mRenderer.mFontWidth;
        return Math.max(0f, (getWidth() - contentWidth) / 2f);
    }

    /**
     * How far down the grid is drawn this frame. A travelling grid is drawn from where it stood
     * when the travel began, displaced toward where the settle's resize will put it (see
     * mTravelActive); a smooth scroll shifts it by its pixel offset.
     */
    private float currentDrawOffset() {
        return (mTravelActive
            ? mTravelAnchorOffsetPx + mTravelDisplacementPx : getVerticalContentOffset())
            - mScrollOffsetPixels;
    }

    /**
     * The top of screen row {@code screenRow} (0 is the top visible row) as this frame draws it,
     * in this view's pixels: with the grid's offset, a travel's displacement, a smooth scroll and
     * the renderer's ascent slack, so whatever tracks the cursor lands on the painted cell.
     */
    public float getRowTopPixels(int screenRow) {
        if (mRenderer == null) return 0f;
        return rowTop(currentDrawOffset(), mRenderer.mFontLineSpacingAndAscent,
            mRenderer.mFontLineSpacing, screenRow);
    }

    /** The renderer's row top: it starts rows {@code spacingAndAscent} down from the draw offset. */
    static float rowTop(float drawOffset, int spacingAndAscent, int lineSpacing, int screenRow) {
        return drawOffset + spacingAndAscent + (float) screenRow * lineSpacing;
    }

    /**
     * How far down the grid is drawn: centred in the view, on either buffer.
     *
     * <p>Rows are integral, so up to a line of the view's height is left over, and it has to sit
     * somewhere. It is split between the top and the bottom ({@link #centredSlackPx}), the way
     * the columns' leftover is already split between the sides
     * ({@link #getHorizontalContentOffset}), so the grid sits the same distance off every edge of
     * the pane and a full-screen program's frame floats no further below the top border than it
     * stands above the bottom one. The grid used to anchor to the bottom on the normal buffer, so
     * the prompt kept a constant distance off the border across resizes; with the leftover split
     * the prompt moves by at most half a row when the pane's height changes. Where the dock pads
     * the pane down to a whole number of rows ({@code TermuxActivity}'s flush padding) the
     * leftover is zero and nothing moves at all.
     *
     * <p>The first row's cells start a further {@code mFontLineSpacingAndAscent} down from here
     * (the renderer's ascent allowance); the frame around this view counts that as clearance the
     * top edge already has.
     */
    public float getVerticalContentOffset() {
        if (mEmulator == null || mRenderer == null) {
            return 0f;
        }
        int contentHeight = mEmulator.mRows * mRenderer.mFontLineSpacing
            + mRenderer.mFontLineSpacingAndAscent;
        return centredSlackPx(getHeight(), contentHeight);
    }

    /**
     * The slack a grid of {@code contentPx} leaves at the near edge of a view {@code extentPx}
     * long when it is centred: half the leftover, rounded down, so rows and columns keep landing
     * on whole pixels and the far edge takes the odd pixel. Never negative: a grid that overflows
     * its view starts at the edge.
     */
    static int centredSlackPx(int extentPx, int contentPx) {
        return Math.max(0, extentPx - contentPx) / 2;
    }

    /**
     * Hide or show this pane's own text cursor. Used by the split-pane layer so only the focused
     * pane carries one, and so both ends of a cursor smear can be dark while the smear itself is
     * the cursor in flight.
     */
    public void setCursorSuppressed(boolean suppressed) {
        if (mRenderer != null && mRenderer.setCursorSuppressed(suppressed)) invalidate();
    }

    public int getTopRow() {
        return mTopRow;
    }

    public void setTopRow(int mTopRow) {
        this.mTopRow = mTopRow;
        clearScrollOffset();
    }

    /** Jump to a row from the screen buffer's external coordinate system. */
    /**
     * Installs (or with null clears) the find session's highlights, copy-mode cursor and selection.
     * The view only draws them; every decision about what they contain lives above it.
     */
    public void setFindOverlay(@Nullable TerminalFindOverlay overlay) {
        mFindOverlay = overlay;
        invalidate();
    }

    @Nullable
    public TerminalFindOverlay getFindOverlay() {
        return mFindOverlay;
    }

    /**
     * Scrolls the least amount that brings {@code row} onto the screen, keeping a margin of context
     * around it where the transcript allows. Unlike {@link #jumpToBufferRow(int)} — which pins the
     * row to the top — this leaves the view alone when the row is already comfortably visible, so
     * walking matches inside one screenful does not make the transcript jump under the reader.
     */
    public boolean revealBufferRow(int row, int marginRows) {
        if (mEmulator == null) return false;
        int lowest = -mEmulator.getScreen().getActiveTranscriptRows();
        int margin = Math.max(0, Math.min(marginRows, Math.max(0, (mEmulator.mRows - 1) / 2)));
        int target = mTopRow;
        if (row < mTopRow + margin) target = row - margin;
        else if (row > mTopRow + mEmulator.mRows - 1 - margin)
            target = row - mEmulator.mRows + 1 + margin;
        target = Math.max(lowest, Math.min(0, target));
        if (target == mTopRow) return false;
        mTopRow = target;
        clearScrollOffset();
        notifyCursorTrailSnap();
        invalidate();
        return true;
    }

    public boolean jumpToBufferRow(int row) {
        if (mEmulator == null) return false;
        int lowest = -mEmulator.getScreen().getActiveTranscriptRows();
        int newTopRow = Math.max(lowest, Math.min(0, row));
        if (newTopRow == mTopRow) return false;
        mTopRow = newTopRow;
        clearScrollOffset();
        notifyCursorTrailSnap();
        if (isSelectingText()) stopTextSelectionMode();
        invalidate();
        return true;
    }

    /**
     * Scroll so that the closest shell prompt above or below the top of the view is the first row shown.
     * Needs the shell to emit OSC 133 marks; without them there is nothing to jump to.
     *
     * @return true if the view moved.
     */
    public boolean jumpToPrompt(boolean backwards) {
        if (mEmulator == null)
            return false;
        int row = mEmulator.findPromptRow(mTopRow, backwards);
        if (row == Integer.MIN_VALUE)
            return false;
        int lowest = -mEmulator.getScreen().getActiveTranscriptRows();
        int newTopRow = Math.max(lowest, Math.min(0, row));
        if (newTopRow == mTopRow) {
            // The prompt is already on screen: the view cannot scroll past its last row, so there is
            // nothing to move and reporting success would be a lie.
            return false;
        }
        mTopRow = newTopRow;
        clearScrollOffset();
        // A jump is a discontinuity, so do not streak the cursor across it.
        notifyCursorTrailSnap();
        if (isSelectingText())
            stopTextSelectionMode();
        invalidate();
        return true;
    }

    /**
     * Define functions required for AutoFill API
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void autofill(AutofillValue value) {
        if (value.isText()) {
            mTermSession.write(value.getTextValue().toString());
        }

        resetAutoFill();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return mAutoFillType;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public String[] getAutofillHints() {
        return mAutoFillHints;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public AutofillValue getAutofillValue() {
        return AutofillValue.forText("");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getImportantForAutofill() {
        return mAutoFillImportance;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private synchronized void resetAutoFill() {
        // Restore none type so that AutoFill UI isn't shown anymore.
        mAutoFillType = AUTOFILL_TYPE_NONE;
        mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;
        mAutoFillHints = new String[0];
    }

    public AutofillManager getAutoFillManagerService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null;

        try {
            Context context = getContext();
            if (context == null) return null;
            return context.getSystemService(AutofillManager.class);
        } catch (Exception e) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to get AutofillManager service", e);
            return null;
        }
    }

    public boolean isAutoFillEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;

        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            return autofillManager != null && autofillManager.isEnabled();
        } catch (Exception e) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to check if Autofill is enabled", e);
            return false;
        }
    }

    public synchronized void requestAutoFill(String[] autoFillHints) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (autoFillHints == null || autoFillHints.length < 1) return;

        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            if (autofillManager != null && autofillManager.isEnabled()) {
                // Update type that will be returned by `getAutofillType()` so that AutoFill UI is shown.
                mAutoFillType = AUTOFILL_TYPE_TEXT;
                // Update importance that will be returned by `getImportantForAutofill()` so that
                // AutoFill considers the view as important.
                mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_YES;
                // Update hints that will be returned by `getAutofillHints()` for which to show AutoFill UI.
                mAutoFillHints = autoFillHints;
                autofillManager.requestAutofill(this);
            }
        } catch (Exception e) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to request Autofill", e);
        }
    }

    public synchronized void cancelRequestAutoFill() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (mAutoFillType == AUTOFILL_TYPE_NONE) return;

        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            if (autofillManager != null && autofillManager.isEnabled()) {
                resetAutoFill();
                autofillManager.cancel();
            }
        } catch (Exception e) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to cancel Autofill request", e);
        }
    }





    /**
     * Set terminal cursor blinker rate. It must be between {@link #TERMINAL_CURSOR_BLINK_RATE_MIN}
     * and {@link #TERMINAL_CURSOR_BLINK_RATE_MAX}, otherwise it will be disabled.
     *
     * The {@link #setTerminalCursorBlinkerState(boolean, boolean)} must be called after this
     * for changes to take effect if not disabling.
     *
     * @param blinkRate The value to set.
     * @return Returns {@code true} if setting blinker rate was successfully set, otherwise [@code false}.
     */
    public synchronized boolean setTerminalCursorBlinkerRate(int blinkRate) {
        boolean result;
        // If cursor blinking rate is not valid
        if (blinkRate != 0 && (blinkRate < TERMINAL_CURSOR_BLINK_RATE_MIN || blinkRate > TERMINAL_CURSOR_BLINK_RATE_MAX)) {
            mClient.logError(LOG_TAG, "The cursor blink rate must be in between " + TERMINAL_CURSOR_BLINK_RATE_MIN + "-" + TERMINAL_CURSOR_BLINK_RATE_MAX + ": " + blinkRate);
            mTerminalCursorBlinkerRate = 0;
            result = false;
        } else {
            mClient.logVerbose(LOG_TAG, "Setting cursor blinker rate to " + blinkRate);
            mTerminalCursorBlinkerRate = blinkRate;
            result = true;
        }
        if (mTerminalCursorBlinkerRate == 0) {
            mClient.logVerbose(LOG_TAG, "Cursor blinker disabled");
            stopTerminalCursorBlinker();
        }
        return result;
    }

    /**
     * Sets whether cursor blinker should be started or stopped. Cursor blinker will only be
     * started if {@link #mTerminalCursorBlinkerRate} does not equal 0 and is between
     * {@link #TERMINAL_CURSOR_BLINK_RATE_MIN} and {@link #TERMINAL_CURSOR_BLINK_RATE_MAX}.
     *
     * This should be called when the view holding this activity is resumed or stopped so that
     * cursor blinker does not run when activity is not visible. If you call this on onResume()
     * to start cursor blinking, then ensure that {@link #mEmulator} is set, otherwise wait for the
     * {@link TerminalViewClient#onEmulatorSet()} event after calling {@link #attachSession(TerminalSession)}
     * for the first session added in the activity since blinking will not start if {@link #mEmulator}
     * is not set, like if activity is started again after exiting it with double back press. Do not
     * call this directly after {@link #attachSession(TerminalSession)} since {@link #updateSize()}
     * may return without setting {@link #mEmulator} since width/height may be 0. Its called again in
     * {@link #onSizeChanged(int, int, int, int)}. Calling on onResume() if emulator is already set
     * is necessary, since onEmulatorSet() may not be called after activity is started after device
     * display timeout with double tap and not power button.
     *
     * It should also be called on the
     * {@link com.termux.terminal.TerminalSessionClient#onTerminalCursorStateChange(boolean)}
     * callback when cursor is enabled or disabled so that blinker is disabled if cursor is not
     * to be shown. It should also be checked if activity is visible if blinker is to be started
     * before calling this.
     *
     * It should also be called after terminal is reset with {@link TerminalSession#reset()} in case
     * cursor blinker was disabled before reset due to call to
     * {@link com.termux.terminal.TerminalSessionClient#onTerminalCursorStateChange(boolean)}.
     *
     * How cursor blinker starting works is by registering a {@link Runnable} with the looper of
     * the main thread of the app which when run, toggles the cursor blinking state and re-registers
     * itself to be called with the delay set by {@link #mTerminalCursorBlinkerRate}. When cursor
     * blinking needs to be disabled, we just cancel any callbacks registered. We don't run our own
     * "thread" and let the thread for the main looper do the work for us, whose usage is also
     * required to update the UI, since it also handles other calls to update the UI as well based
     * on a queue.
     *
     * Note that when moving cursor in text editors like nano, the cursor state is quickly
     * toggled `-> off -> on`, which would call this very quickly sequentially. So that if cursor
     * is moved 2 or more times quickly, like long hold on arrow keys, it would trigger
     * `-> off -> on -> off -> on -> ...`, and the "on" callback at index 2 is automatically
     * cancelled by next "off" callback at index 3 before getting a chance to be run. For this case
     * we log only if {@link #TERMINAL_VIEW_KEY_LOGGING_ENABLED} is enabled, otherwise would clutter
     * the log. We don't start the blinking with a delay to immediately show cursor in case it was
     * previously not visible.
     *
     * @param start If cursor blinker should be started or stopped.
     * @param startOnlyIfCursorEnabled If set to {@code true}, then it will also be checked if the
     *                                 cursor is even enabled by {@link TerminalEmulator} before
     *                                 starting the cursor blinker.
     */
    public synchronized void setTerminalCursorBlinkerState(boolean start, boolean startOnlyIfCursorEnabled) {
        // Stop any existing cursor blinker callbacks
        stopTerminalCursorBlinker();
        if (mEmulator == null)
            return;
        mEmulator.setCursorBlinkingEnabled(false);
        if (start) {
            // If cursor blinker is not enabled or is not valid
            if (mTerminalCursorBlinkerRate < TERMINAL_CURSOR_BLINK_RATE_MIN || mTerminalCursorBlinkerRate > TERMINAL_CURSOR_BLINK_RATE_MAX)
                return;
            else // If cursor blinder is to be started only if cursor is enabled
            if (startOnlyIfCursorEnabled && !mEmulator.isCursorEnabled()) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                    mClient.logVerbose(LOG_TAG, "Ignoring call to start cursor blinker since cursor is not enabled");
                return;
            }
            // Start cursor blinker runnable
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                mClient.logVerbose(LOG_TAG, "Starting cursor blinker with the blink rate " + mTerminalCursorBlinkerRate);
            if (mTerminalCursorBlinkerHandler == null)
                mTerminalCursorBlinkerHandler = new Handler(Looper.getMainLooper());
            mTerminalCursorBlinkerRunnable = new TerminalCursorBlinkerRunnable(mEmulator, mTerminalCursorBlinkerRate);
            mEmulator.setCursorBlinkingEnabled(true);
            mTerminalCursorBlinkerRunnable.run();
        }
    }

    /**
     * Cancel the terminal cursor blinker callbacks
     */
    private void stopTerminalCursorBlinker() {
        if (mTerminalCursorBlinkerHandler != null && mTerminalCursorBlinkerRunnable != null) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                mClient.logVerbose(LOG_TAG, "Stopping cursor blinker");
            mTerminalCursorBlinkerHandler.removeCallbacks(mTerminalCursorBlinkerRunnable);
        }
    }

    private class TerminalCursorBlinkerRunnable implements Runnable {

        private TerminalEmulator mEmulator;

        private final int mBlinkRate;

        // Initialize with false so that initial blink state is visible after toggling
        boolean mCursorVisible = false;

        public TerminalCursorBlinkerRunnable(TerminalEmulator emulator, int blinkRate) {
            mEmulator = emulator;
            mBlinkRate = blinkRate;
        }

        public void setEmulator(TerminalEmulator emulator) {
            mEmulator = emulator;
        }

        public void run() {
            try {
                if (mEmulator != null) {
                    // Toggle the blink state and then invalidate() the view so
                    // that onDraw() is called, which then calls TerminalRenderer.render()
                    // which checks with TerminalEmulator.shouldCursorBeVisible() to decide whether
                    // to draw the cursor or not
                    mCursorVisible = !mCursorVisible;
                    //mClient.logVerbose(LOG_TAG, "Toggling cursor blink state to " + mCursorVisible);
                    mEmulator.setCursorBlinkState(mCursorVisible);
                    // The state above is kept current regardless, so the cursor reads right the
                    // instant the page returns; only the invalidate that would re-record this
                    // view's now-retained display list for nobody to see is skipped while away.
                    if (!mWallOffScreen) invalidate();
                }
            } finally {
                // Recall the Runnable after mBlinkRate milliseconds to toggle the blink state
                mTerminalCursorBlinkerHandler.postDelayed(this, mBlinkRate);
            }
        }
    }

    /**
     * Define functions required for text selection and its handles.
     */
    TextSelectionCursorController getTextSelectionCursorController() {
        if (mTextSelectionCursorController == null) {
            mTextSelectionCursorController = new TextSelectionCursorController(this);
            final ViewTreeObserver observer = getViewTreeObserver();
            if (observer != null) {
                observer.addOnTouchModeChangeListener(mTextSelectionCursorController);
            }
        }
        return mTextSelectionCursorController;
    }

    private void showTextSelectionCursors(MotionEvent event) {
        getTextSelectionCursorController().show(event);
    }

    private boolean hideTextSelectionCursors() {
        return getTextSelectionCursorController().hide();
    }

    private void renderTextSelection() {
        if (mTextSelectionCursorController != null)
            mTextSelectionCursorController.render();
    }

    public boolean isSelectingText() {
        if (mTextSelectionCursorController != null) {
            return mTextSelectionCursorController.isActive();
        } else {
            return false;
        }
    }

    /**
     * Get the currently selected text if selecting.
     */
    public String getSelectedText() {
        if (isSelectingText() && mTextSelectionCursorController != null)
            return mTextSelectionCursorController.getSelectedText();
        else
            return null;
    }

    /**
     * Get the selected text stored before "MORE" button was pressed on the context menu.
     */
    @Nullable
    public String getStoredSelectedText() {
        return mTextSelectionCursorController != null ? mTextSelectionCursorController.getStoredSelectedText() : null;
    }

    /**
     * Unset the selected text stored before "MORE" button was pressed on the context menu.
     */
    public void unsetStoredSelectedText() {
        if (mTextSelectionCursorController != null)
            mTextSelectionCursorController.unsetStoredSelectedText();
    }

    private ActionMode getTextSelectionActionMode() {
        if (mTextSelectionCursorController != null) {
            return mTextSelectionCursorController.getActionMode();
        } else {
            return null;
        }
    }

    public void startTextSelectionMode(MotionEvent event) {
        if (!requestFocus()) {
            return;
        }
        // Selection works in row coordinates, so it may not be started while a row is half scrolled.
        clearScrollOffset();
        showTextSelectionCursors(event);
        mClient.copyModeChanged(isSelectingText());
        invalidate();
    }

    /** Start selecting text at the shell cursor, expanded to the word under it. */
    public void startTextSelectionAtCursor() {
        if (mEmulator == null || !requestFocus())
            return;
        // Selection works in row coordinates, so it may not be started while a row is half scrolled.
        clearScrollOffset();
        getTextSelectionCursorController().selectAtCursor();
        mClient.copyModeChanged(isSelectingText());
        invalidate();
    }

    /** Select the complete active terminal buffer, including scrollback. */
    public void selectAllText() {
        if (mEmulator == null || !requestFocus())
            return;
        clearScrollOffset();
        getTextSelectionCursorController().selectAll();
        mClient.copyModeChanged(isSelectingText());
        invalidate();
    }

    public void stopTextSelectionMode() {
        if (hideTextSelectionCursors()) {
            mClient.copyModeChanged(isSelectingText());
            invalidate();
        }
    }

    private void decrementYTextSelectionCursors(int decrement) {
        if (mTextSelectionCursorController != null) {
            mTextSelectionCursorController.decrementYTextSelectionCursors(decrement);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateKittyAnimationVisibility();
        // A cached pane re-attached at the pixel size it left with gets no onSizeChanged, so a
        // resize it missed while hidden would never reach the PTY. The post runs after the
        // attaching layout pass; updateSize is a no-op when the grid already fits.
        post(this::updateSize);
        if (mTextSelectionCursorController != null) {
            getViewTreeObserver().addOnTouchModeChangeListener(mTextSelectionCursorController);
        }
        refreshFrameBudget();
        android.hardware.display.DisplayManager displayManager =
            getContext().getSystemService(android.hardware.display.DisplayManager.class);
        if (displayManager != null) displayManager.registerDisplayListener(mDisplayListener, null);
        if (mAccessibilityManager != null) {
            mAccessibilityManager.addAccessibilityStateChangeListener(mAccessibilityStateListener);
            mAccessibilityManager.addTouchExplorationStateChangeListener(mTouchExplorationListener);
            refreshAccessibilityServiceState();
        }
    }

    /**
     * The session this view has last reported as on screen, so the report can be withdrawn when it
     * stops being so. A kitty animation ticks and composites a frame at a time forever otherwise,
     * whether or not anything can see it — a hidden pane and a dark screen both keep it running.
     */
    private TerminalSession mKittyAnimatingSession;

    /**
     * Tell the emulator whether its output is on screen. Playback is only suspended, never thrown
     * away: the frames and the place in them survive, so scrolling an animation back into view or
     * coming back to its pane picks it up where it would have been.
     */
    private void updateKittyAnimationVisibility() {
        boolean onScreen = !mWallOffScreen && isShown() && getWindowVisibility() == View.VISIBLE;
        TerminalSession target = onScreen && mEmulator != null ? mTermSession : null;
        if (mKittyAnimatingSession != null && mKittyAnimatingSession != target) {
            TerminalEmulator emulator = mKittyAnimatingSession.getEmulator();
            if (emulator != null) {
                emulator.setKittyAnimationsVisible(false);
                // The emulator outlives this view, so the scroll-position callback has to go with
                // the view or it holds the whole activity through it.
                emulator.setTopRowProvider(null);
            }
        }
        mKittyAnimatingSession = target;
        if (target != null) {
            target.getEmulator().setTopRowProvider(() -> mTopRow);
            target.getEmulator().setKittyAnimationsVisible(true);
        }
    }

    /**
     * Told by the pane wall whenever this view's page crosses fully on or off screen. The wall
     * keeps the Terminal page {@code VISIBLE} throughout, alpha-faded rather than INVISIBLE while
     * away, so this is the only place any of the following actually changes, and it has to do by
     * hand everything {@code onVisibilityChanged}/{@code isShown()} used to give for free:
     * suspend kitty animation playback, stop the cursor blinker invalidating a hidden pane, drop
     * focus so a hardware keyboard cannot type into a place the user cannot see, and pull the pane
     * out of the accessibility tree.
     */
    public void setWallPageOffScreen(boolean offScreen) {
        if (mWallOffScreen == offScreen) return;
        mWallOffScreen = offScreen;
        if (offScreen && isFocused()) clearFocus();
        setImportantForAccessibility(offScreen
            ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        updateKittyAnimationVisibility();
        // The cursor blinker kept toggling its state while away, just without the invalidate that
        // would have drawn it (see TerminalCursorBlinkerRunnable#run); one is owed now so the
        // frame that brings the page back does not show a blink state a frame or two stale.
        if (!offScreen) invalidate();
    }

    /**
     * Refuse focus while the wall keeps this page off screen (see {@link #setWallPageOffScreen}),
     * whichever way it was asked for — a click elsewhere in the pane host, focus search moving
     * across a hardware Tab, or one of this view's own callers. Without this a hidden pane, which
     * is never actually INVISIBLE any more, would still happily take focus back and catch a
     * hardware keyboard's keystrokes nobody can see land.
     */
    @Override
    public boolean requestFocus(int direction, @Nullable android.graphics.Rect previouslyFocusedRect) {
        if (mWallOffScreen) return false;
        return super.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateKittyAnimationVisibility();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateKittyAnimationVisibility();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelHoldTimers();
        mHoldGesture.reset();
        releaseHoldDownEvent();
        updateKittyAnimationVisibility();
        clearReflowFrost();
        android.hardware.display.DisplayManager displayManager =
            getContext().getSystemService(android.hardware.display.DisplayManager.class);
        if (displayManager != null) displayManager.unregisterDisplayListener(mDisplayListener);
        if (mAccessibilityManager != null) {
            mAccessibilityManager.removeAccessibilityStateChangeListener(mAccessibilityStateListener);
            mAccessibilityManager.removeTouchExplorationStateChangeListener(mTouchExplorationListener);
        }
        mAccessibilityTextUpdates.cancel();
        if (mTextSelectionCursorController != null) {
            // Might solve the following exception
            // android.view.WindowLeaked: Activity com.termux.app.TermuxActivity has leaked window android.widget.PopupWindow
            stopTextSelectionMode();
            getViewTreeObserver().removeOnTouchModeChangeListener(mTextSelectionCursorController);
            mTextSelectionCursorController.onDetached();
        }
    }

    /**
     * Define functions required for long hold toolbar.
     */
    private final Runnable mShowFloatingToolbar = new Runnable() {

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void run() {
            if (getTextSelectionActionMode() != null) {
                // hide off.
                getTextSelectionActionMode().hide(0);
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            int delay = ViewConfiguration.getDoubleTapTimeout();
            postDelayed(mShowFloatingToolbar, delay);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void hideFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            removeCallbacks(mShowFloatingToolbar);
            getTextSelectionActionMode().hide(-1);
        }
    }

    public void updateFloatingToolbarVisibility(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getTextSelectionActionMode() != null) {
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    hideFloatingToolbar();
                    break;
                // fall through
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    showFloatingToolbar();
            }
        }
    }
}
