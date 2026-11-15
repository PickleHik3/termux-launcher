package com.termux.app.wall.switcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * The page switcher: a dot against the screen edge and the picker that grows out of it. This
 * controller owns the layer's two views, runs {@link DotGesture} over every touch they forward
 * and applies the answer; geometry is {@link DotPlacement} and {@link PickerLayout}, what the
 * picker lists is {@link PickerModel}, and what a chosen cell does is {@link PickerTargets}.
 *
 * <p>Nothing here runs at rest. One pre-draw listener re-reads where the dot belongs on each frame
 * the window draws anyway, and moves it by translation only; the dot is laid out once. It never
 * gates a draw.
 */
public final class PageSwitcher {

    /** What the switcher needs from the activity. Chosen cells go through the Navigator half. */
    public interface Host extends PickerTargets.Navigator {
        /** Whether the user has the switcher turned on. */
        boolean isEnabled();

        /** Whether something is covering the wall (an editor, the drawer, a sheet). */
        boolean isSuppressed();

        /** The top of the bottom chrome (keyboard, accessory stack) in the layer's pixels. */
        int bottomChromeTopPx(@NonNull View layer);

        /**
         * Whether the bottom chrome stands at its resting height: no keyboard up, no page slide
         * or crossing under way.
         */
        boolean chromeAtRest();

        /**
         * The system's gesture inset on the given edge, in px, as last dispatched with the window
         * insets (0 with button navigation). Cached by the activity; cheap to call per frame.
         */
        int gestureInsetPx(@NonNull Edge edge);

        /** The height of the keyboard in front (in-app or system), 0 when none is up. */
        int keyboardHeightPx();

        boolean isReducedMotionEnabled();

        boolean hapticsEnabled();

        @NonNull PickerView.Palette palette();

        /** What the wall holds right now. */
        @NonNull PickerModel.Input snapshot();

        @NonNull Edge savedEdge();

        float savedHeightFraction();

        void savePosition(@NonNull Edge edge, float heightFraction);

        boolean hintShown();

        void markHintShown();

        /**
         * Whether the first-run setup (the wizard or one of its sheets) covers the launcher, so
         * the hint would open under it.
         */
        boolean isFirstRunCovering();
    }

    static final long SNAP_MS = 220L;
    private static final long TOUCH_FADE_MS = 80L;
    private static final long REST_DELAY_MS = 2000L;
    private static final long REST_FADE_MS = 400L;

    @NonNull private final Host mHost;
    @Nullable private FrameLayout mLayer;
    @Nullable private DotView mDot;
    @Nullable private PickerView mPicker;
    @Nullable private PickerHaptics mHaptics;
    @Nullable private DotGesture mGesture;
    private float mDensity = 1f;
    private float mDiameter;
    private int mDotSize;

    @NonNull private Edge mEdge = DotPlacement.DEFAULT_EDGE;
    private float mFraction = DotPlacement.DEFAULT_HEIGHT_FRACTION;
    /** The dot's centre in the layer, as last applied. */
    private float mCx = -1f;
    private float mCy = -1f;
    private float mBandTop;
    private float mBandBottom;
    private boolean mMoving;
    private float mMoveX;
    private float mMoveY;
    @Nullable private ObjectAnimator mSnap;
    private boolean mHintPending;
    /** Set once the hint is known to be shown, so a frame does not ask the preferences again. */
    private boolean mHintDone;
    private boolean mHintChecked;
    private boolean mClaiming;
    private float mLastBandBottom = -1f;
    /** The bottom chrome's top on the last frame it stood at rest; the keyboard's rise reads off it. */
    private float mRestChromeTop = -1f;
    /** The keyboard's reveal on the last frame, 0..1. */
    private float mKeyboardProgress;
    private int mStableFrames;
    private final int[] mLocation = new int[2];

    private final ViewTreeObserver.OnPreDrawListener mPreDraw = () -> {
        syncFrame();
        return true;
    };
    private final Runnable mHold = this::onHoldElapsed;

    public PageSwitcher(@NonNull Host host) {
        mHost = host;
    }

    // ---- lifecycle --------------------------------------------------------------------------

    /** Adds the dot to the layer (the picker joins on its first open) and starts following frames. */
    public void attach(@NonNull FrameLayout layer) {
        if (mLayer != null) return;
        mLayer = layer;
        Context context = layer.getContext();
        mDensity = context.getResources().getDisplayMetrics().density;
        mDiameter = DotPlacement.DIAMETER_DP * mDensity;
        mGesture = new DotGesture(ViewConfiguration.get(context).getScaledTouchSlop());
        mHaptics = new PickerHaptics(context, mHost::hapticsEnabled);
        DotView dot = new DotView(context);
        dot.setVisibility(View.INVISIBLE);
        dot.setListener(new DotView.Listener() {
            @Override public boolean onDotTouch(@NonNull MotionEvent event) {
                return handleTouch(event);
            }

            @Override public void onDotClick() {
                if (!isPickerShowing()) openPicker(true, false);
            }
        });
        mDotSize = dot.sizePx();
        layer.addView(dot, new FrameLayout.LayoutParams(mDotSize, mDotSize));
        mDot = dot;
        readSavedPosition();
        layer.getViewTreeObserver().addOnPreDrawListener(mPreDraw);
        layer.invalidate();
    }

    public void detach() {
        FrameLayout layer = mLayer;
        if (layer == null) return;
        dropAll();
        ViewTreeObserver observer = layer.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(mPreDraw);
        layer.removeCallbacks(mHold);
        if (mPicker != null) layer.removeView(mPicker);
        if (mDot != null) layer.removeView(mDot);
        mPicker = null;
        mDot = null;
        mLayer = null;
    }

    /** Re-reads the switch and the saved position; the next frame applies them. */
    public void onResume() {
        mHintChecked = false;
        readSavedPosition();
        FrameLayout layer = mLayer;
        if (layer != null) layer.invalidate();
    }

    private void readSavedPosition() {
        if (mMoving || mSnap != null) return;
        Edge edge = mHost.savedEdge();
        mEdge = edge == null ? DotPlacement.DEFAULT_EDGE : edge;
        float f = mHost.savedHeightFraction();
        mFraction = f >= 0f && f <= 1f ? f : DotPlacement.DEFAULT_HEIGHT_FRACTION;
    }

    public boolean isPickerShowing() {
        PickerView picker = mPicker;
        return picker != null && picker.isShowing();
    }

    /** Back closes the picker when it is up. */
    public boolean onBack() {
        PickerView picker = mPicker;
        if (picker == null || !picker.isShowing()) return false;
        picker.close(!mHost.isReducedMotionEnabled());
        updateBrightness();
        return true;
    }

    /** Ends everything at once: the gesture, the picker and a lifted dot. */
    public void dropAll() {
        FrameLayout layer = mLayer;
        if (layer != null) layer.removeCallbacks(mHold);
        DotGesture gesture = mGesture;
        if (gesture != null && gesture.isLive()) gesture.cancel();
        endMove();
        PickerView picker = mPicker;
        if (picker != null) picker.close(false);
        updateBrightness();
    }

    // ---- one frame --------------------------------------------------------------------------

    /**
     * Where the dot belongs on a frame the window draws. The bottom chrome's drawn top already
     * carries every keyboard reveal, so the keyboard is read here, never predicted.
     */
    void syncFrame() {
        FrameLayout layer = mLayer;
        DotView dot = mDot;
        if (layer == null || dot == null) return;
        boolean pickerUp = isPickerShowing();
        boolean shown = mHost.isEnabled() && !(mHost.isSuppressed() && !pickerUp);
        if (!shown) {
            hide(dot);
            return;
        }
        int w = layer.getWidth();
        int h = layer.getHeight();
        if (w <= 0 || h <= 0) return;

        mBandTop = DotPlacement.bandTop(mDiameter, mDensity);
        int chromeTop = mHost.bottomChromeTopPx(layer);
        mBandBottom = DotPlacement.bandBottom(h, chromeTop, mDiameter, mDensity);
        if (mRestChromeTop < 0f || mHost.chromeAtRest()) mRestChromeTop = chromeTop;
        mKeyboardProgress = DotPlacement.keyboardProgress(mRestChromeTop, chromeTop,
            mHost.keyboardHeightPx());
        boolean becameVisible = dot.getVisibility() != View.VISIBLE;
        if (becameVisible) {
            dot.setColors(mHost.palette().glass, mHost.palette().ink);
            dot.setVisibility(View.VISIBLE);
        }
        DotGesture live = mGesture;
        boolean fingerDown = live != null && live.isLive();
        if (!mMoving && DotPlacement.frameMayMove(fingerDown)) {
            float y = DotPlacement.restY(mFraction, h, mBandTop, mBandBottom, mDiameter);
            if (Math.abs(mBandBottom - mLastBandBottom) > 0.5f) mStableFrames = 0;
            else if (mStableFrames < DotPlacement.STABLE_FRAMES) mStableFrames++;
            mLastBandBottom = mBandBottom;
            if (mSnap == null && DotPlacement.adoptsClamp(
                    DotPlacement.clampedUp(y, mFraction, h, mBandTop),
                    fingerDown, pickerUp, mStableFrames,
                    mStableFrames >= DotPlacement.STABLE_FRAMES && mHost.chromeAtRest())) {
                mFraction = DotPlacement.fractionOf(y, h);
                mHost.savePosition(mEdge, mFraction);
            }
            float x = mSnap != null ? mCx : restX(mEdge, w);
            if (DotPlacement.closesPicker(pickerUp, fingerDown, y, mCy)) {
                PickerView picker = mPicker;
                if (picker != null) picker.close(false);
            }
            placeDot(x, y);
        }
        if (becameVisible) {
            dot.updateGestureExclusion();
            updateBrightness();
        }
        maybeShowHint(layer);
    }

    private void hide(@NonNull DotView dot) {
        FrameLayout layer = mLayer;
        if (layer != null) layer.removeCallbacks(mHold);
        DotGesture gesture = mGesture;
        if (gesture != null && gesture.isLive()) gesture.cancel();
        endMove();
        PickerView picker = mPicker;
        if (picker != null && picker.getVisibility() == View.VISIBLE) picker.close(false);
        if (dot.getVisibility() != View.INVISIBLE) {
            dot.setVisibility(View.INVISIBLE);
            dot.updateGestureExclusion();
        }
    }

    /** Where the dot rests on an edge now: at the edge, or stepped in while a keyboard is up. */
    private float restX(@NonNull Edge edge, float width) {
        return DotPlacement.restX(edge, width, mDiameter, mDensity, mHost.gestureInsetPx(edge),
            mKeyboardProgress);
    }

    /** Applies the centre as translation only, and only when it changed. */
    private void placeDot(float cx, float cy) {
        DotView dot = mDot;
        if (dot == null) return;
        mCx = cx;
        mCy = cy;
        float tx = cx - mDotSize / 2f;
        float ty = cy - mDotSize / 2f;
        if (dot.getTranslationX() != tx) dot.setTranslationX(tx);
        if (dot.getTranslationY() != ty) dot.setTranslationY(ty);
    }

    /**
     * The first time the dot is visible with nothing covering the launcher, the picker opens once
     * with a line saying how to use it. The hint is marked shown only once that picker is up, so
     * a wizard in front only delays it.
     */
    private void maybeShowHint(@NonNull FrameLayout layer) {
        if (mHintDone || mHintPending || mCx < 0f) return;
        if (!mHintChecked) {
            // The preference is asked once, not on every frame.
            mHintChecked = true;
            if (mHost.hintShown()) {
                mHintDone = true;
                return;
            }
        }
        DotView dot = mDot;
        if (!HintGate.opensNow(mHost.isEnabled(),
                dot != null && dot.getVisibility() == View.VISIBLE, false,
                mHost.isFirstRunCovering())) return;
        mHintPending = true;
        layer.post(() -> {
            mHintPending = false;
            DotView d = mDot;
            if (mLayer == null || d == null || d.getVisibility() != View.VISIBLE
                || isPickerShowing() || mHost.isFirstRunCovering()) return;
            openPicker(true, true);
            if (isPickerShowing()) {
                mHintDone = true;
                mHost.markHintShown();
            }
        });
    }

    // ---- the picker -------------------------------------------------------------------------

    private void openPicker(boolean animate, boolean withHint) {
        FrameLayout layer = mLayer;
        DotView dot = mDot;
        if (layer == null || dot == null || dot.getVisibility() != View.VISIBLE) return;
        int w = layer.getWidth();
        int h = layer.getHeight();
        if (w <= 0 || h <= 0) return;
        PickerModel.Input in = mHost.snapshot();
        if (in == null) return;
        PickerView picker = ensurePicker(layer);
        boolean reduced = mHost.isReducedMotionEnabled();
        picker.setReducedMotion(reduced);
        picker.setPalette(mHost.palette());

        in.capacity = Integer.MAX_VALUE;
        PickerModel sizing = PickerModel.build(in);
        int wanted = sizing.tallestColumn();
        float bottomLimit = Math.min(h, mHost.bottomChromeTopPx(layer));
        PickerLayout layout = new PickerLayout(w, h, mDensity, mCx, mCy, mDiameter, mEdge,
            PickerLayout.PAD_DP * mDensity, bottomLimit, wanted,
            sizing.columns().size());
        in.capacity = layout.capacity();
        PickerModel model = PickerModel.build(in);
        picker.show(model, layout, animate && !reduced);
        picker.setHint(withHint ? layer.getContext().getString(R.string.page_switcher_hint) : null);
        dot.animate().cancel();
        dot.setAlpha(1f);
    }

    @NonNull
    private PickerView ensurePicker(@NonNull FrameLayout layer) {
        PickerView picker = mPicker;
        if (picker != null) return picker;
        picker = new PickerView(layer.getContext(), mHost.palette(), new PickerView.Listener() {
            @Override public boolean onPickerTouch(@NonNull MotionEvent event) {
                return handleTouch(event);
            }

            @Override public void onCellActivated(@NonNull PickerModel.Cell cell) {
                choose(cell);
            }
        });
        // Below the dot, which stays reachable over it.
        layer.addView(picker, 0, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mPicker = picker;
        return picker;
    }

    private void choose(@NonNull PickerModel.Cell cell) {
        PickerHaptics haptics = mHaptics;
        if (haptics != null) haptics.committed();
        PickerView picker = mPicker;
        if (picker != null) picker.close(!mHost.isReducedMotionEnabled());
        PickerTargets.carryOut(cell.target, mHost);
        updateBrightness();
    }

    // ---- touch ------------------------------------------------------------------------------

    /**
     * Latches on a touch's DOWN whether the point is on the dot's hit circle (dot visible) or the
     * picker is showing, and answers that latch for every event of the stream; the activity sends
     * a claimed stream straight to the views.
     */
    public boolean claimsTouch(@NonNull MotionEvent ev) {
        FrameLayout layer = mLayer;
        DotView dot = mDot;
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mClaiming = false;
            if (layer != null && dot != null) {
                layer.getLocationOnScreen(mLocation);
                mClaiming = isPickerShowing() || onDot(ev.getRawX() - mLocation[0],
                    ev.getRawY() - mLocation[1]);
            }
        }
        boolean claimed = mClaiming;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mClaiming = false;
        }
        return claimed;
    }

    private boolean onDot(float x, float y) {
        DotView dot = mDot;
        return dot != null && dot.getVisibility() == View.VISIBLE && DotPlacement.onDot(x, y, mCx,
            mCy, DotPlacement.TOUCH_RADIUS_DP * mDensity);
    }

    /** Both views forward here; the points are the raw screen point less the layer's origin. */
    private boolean handleTouch(@NonNull MotionEvent ev) {
        FrameLayout layer = mLayer;
        DotGesture gesture = mGesture;
        if (layer == null || gesture == null) return false;
        int action = ev.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE
            && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }
        layer.getLocationOnScreen(mLocation);
        float x = ev.getRawX() - mLocation[0];
        float y = ev.getRawY() - mLocation[1];
        PickerView picker = mPicker;
        PickerModel.Cell under = picker == null ? null : picker.cellAt(x, y);
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                brighten();
                if (mSnap != null) mSnap.cancel();
                boolean pickerOpen = isPickerShowing();
                DotGesture.Action a = gesture.down(x, y, onDot(x, y), pickerOpen, under);
                apply(a, x, y, null);
                if (gesture.phase() == DotGesture.Phase.PRESS) {
                    layer.postDelayed(mHold, DotGesture.HOLD_MS);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                DotGesture.Action a = gesture.move(x, y, under);
                if (gesture.phase() != DotGesture.Phase.PRESS) layer.removeCallbacks(mHold);
                apply(a, x, y, under);
                break;
            }
            case MotionEvent.ACTION_UP: {
                layer.removeCallbacks(mHold);
                DotGesture.Action a = gesture.up(x, y, under);
                apply(a, x, y, under);
                break;
            }
            default: {
                layer.removeCallbacks(mHold);
                apply(gesture.cancel(), x, y, null);
                break;
            }
        }
        if (!gesture.isLive()) updateBrightness();
        return true;
    }

    private void onHoldElapsed() {
        DotGesture gesture = mGesture;
        if (gesture == null) return;
        apply(gesture.holdElapsed(mCx, mCy), mCx, mCy, null);
    }

    private void apply(@NonNull DotGesture.Action action, float x, float y,
                       @Nullable PickerModel.Cell under) {
        DotGesture gesture = mGesture;
        PickerView picker = mPicker;
        boolean reduced = mHost.isReducedMotionEnabled();
        switch (action) {
            case OPEN:
                openPicker(true, false);
                break;
            case TRACK:
                if (picker != null) {
                    if (under != null && under != picker.hotCell() && mHaptics != null) {
                        mHaptics.crossed();
                    }
                    picker.setHot(under, x, y);
                }
                break;
            case CHOOSE: {
                Object chosen = gesture == null ? null : gesture.chosen();
                if (chosen instanceof PickerModel.Cell) {
                    choose((PickerModel.Cell) chosen);
                } else if (picker != null) {
                    picker.close(!reduced);
                }
                break;
            }
            case CLOSE:
                if (picker != null) picker.close(!reduced);
                break;
            case STAY_OPEN:
                if (picker != null) picker.setHot(null, x, y);
                break;
            case PICK_UP: {
                if (picker != null) picker.close(!reduced);
                if (mHaptics != null) mHaptics.pickedUp();
                DotView dot = mDot;
                if (dot != null) {
                    dot.setReducedMotion(reduced);
                    dot.setLifted(true);
                }
                mMoving = true;
                mMoveX = mCx;
                mMoveY = mCy;
                break;
            }
            case DRAG: {
                FrameLayout layer = mLayer;
                if (gesture == null || layer == null || !mMoving) break;
                mMoveX = DotPlacement.dragX(gesture.dragX(), layer.getWidth(), mDiameter);
                mMoveY = DotPlacement.dragY(gesture.dragY(), mBandTop, mBandBottom);
                placeDot(mMoveX, mMoveY);
                break;
            }
            case SNAP:
                snap(reduced);
                break;
            default:
                break;
        }
    }

    // ---- moving and snapping ----------------------------------------------------------------

    /** The dot was dropped: it settles on the nearer edge and the position is saved. */
    private void snap(boolean reduced) {
        FrameLayout layer = mLayer;
        DotView dot = mDot;
        if (layer == null || dot == null || !mMoving) {
            endMove();
            return;
        }
        mMoving = false;
        dot.setReducedMotion(reduced);
        dot.setLifted(false);
        final Edge edge = DotPlacement.nearerEdge(mMoveX, layer.getWidth());
        mEdge = edge;
        mFraction = DotPlacement.fractionOf(mMoveY, layer.getHeight());
        final float fraction = mFraction;
        float toX = restX(edge, layer.getWidth());
        if (reduced || !dot.isAttachedToWindow()) {
            placeDot(toX, mMoveY);
            dot.updateGestureExclusion();
            mHost.savePosition(edge, fraction);
            return;
        }
        float fromTx = dot.getTranslationX();
        float toTx = toX - mDotSize / 2f;
        ObjectAnimator animator = ObjectAnimator.ofFloat(dot, View.TRANSLATION_X, fromTx, toTx);
        animator.setDuration(SNAP_MS);
        animator.setInterpolator(new PathInterpolator(.3f, 1.5f, .5f, 1f));
        animator.addUpdateListener(a -> mCx = dot.getTranslationX() + mDotSize / 2f);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (mSnap == animation) mSnap = null;
                mCx = dot.getTranslationX() + mDotSize / 2f;
                dot.updateGestureExclusion();
                mHost.savePosition(edge, fraction);
            }
        });
        mSnap = animator;
        animator.start();
    }

    /** Stops a drag or a snap without saving: the next frame puts the dot back where it belongs. */
    private void endMove() {
        ObjectAnimator snap = mSnap;
        mSnap = null;
        if (snap != null) snap.cancel();
        if (mMoving) {
            mMoving = false;
            DotView dot = mDot;
            if (dot != null) dot.setLifted(false);
        }
    }

    // ---- brightness -------------------------------------------------------------------------

    /** Any touch brings the dot to full strength at once. */
    private void brighten() {
        DotView dot = mDot;
        if (dot == null) return;
        // Also drops a rest fade still waiting out its delay.
        dot.animate().cancel();
        if (dot.getAlpha() >= 1f) return;
        dot.animate().setStartDelay(0L).alpha(1f).setDuration(TOUCH_FADE_MS).start();
    }

    /** With no gesture and no picker, the dot fades back to its resting strength after a pause. */
    private void updateBrightness() {
        DotView dot = mDot;
        DotGesture gesture = mGesture;
        if (dot == null || dot.getVisibility() != View.VISIBLE) return;
        if ((gesture != null && gesture.isLive()) || isPickerShowing()) return;
        dot.animate().cancel();
        if (mHost.isReducedMotionEnabled()) {
            dot.setAlpha(DotPlacement.REST_ALPHA);
            return;
        }
        dot.animate().setStartDelay(REST_DELAY_MS).alpha(DotPlacement.REST_ALPHA)
            .setDuration(REST_FADE_MS).start();
    }
}
