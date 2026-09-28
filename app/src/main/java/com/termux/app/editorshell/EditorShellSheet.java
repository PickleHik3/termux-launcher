package com.termux.app.editorshell;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.Spring;

/**
 * Both editors' cards: a handle, the shared header, and one scrolling body, as a sheet a finger
 * can pull.
 *
 * <p>The header stays where it is and everything under it — the chooser, the miniature, every row
 * — scrolls as one list in {@code editor_shell_sheet_body}. The card is as tall as what it holds,
 * up to its resting height; pulled by the handle, the header or the body itself it grows toward
 * its expanded height, and pushed down past rest it asks its {@link Callback} to close. Which drag
 * is the sheet's and which is the body's, and where a pull settles, is
 * {@link EditorShellSheetPolicy}'s: the rule is Material's bottom sheet, so a collapsed card grows
 * before its body scrolls and a body at its top hands a downward drag to the card.
 *
 * <p>The card's height is the channel the pull drives, not a translation: the Appearance card
 * floats above the dock with all four corners, and a card slid part-way off its own bottom edge
 * would hang over the surface it is not allowed to cover. Only a pull below rest translates, and
 * that is handed to the host, which owns the card's position and its own entry and exit motion.
 *
 * <p>A child that must not lose its drag to the sheet — the miniature's grips, a slider's thumb —
 * says so with {@link ViewParent#requestDisallowInterceptTouchEvent}, which this honours the way
 * every platform scroller does.
 */
public class EditorShellSheet extends LinearLayout {

    /** What the host does with a pull the sheet cannot answer by itself. */
    public interface Callback {
        /**
         * How far the card is pushed down past its resting height, in px; 0 once it is back. The
         * host translates the card by it, on top of whatever its own motion is doing.
         */
        void onSheetOvershoot(float overshootPx);

        /**
         * The card was pulled far enough past rest to close. The host closes it the way its own
         * close gesture does and answers true once the card is leaving — from then on the card's
         * motion is the host's, starting from {@code overshootPx}. Answering false (a question is
         * up, say) springs the card back to rest.
         */
        boolean onSheetDismissRequested(float overshootPx);
    }

    /** Both channels, on the launcher's own spring: critically damped, so neither overshoots. */
    private static final float STIFFNESS = 420f;
    private static final float DAMPING = 41f;

    /** 0 at the resting height, 1 at the expanded one. */
    @NonNull private final Spring mExpansion = new Spring(0f, STIFFNESS, DAMPING);
    /** How far below rest the card is pushed, as a share of its resting height. */
    @NonNull private final Spring mOvershoot = new Spring(0f, STIFFNESS, DAMPING);

    @Nullable private View mHandle;
    @Nullable private View mBody;
    @Nullable private Callback mCallback;
    private int mRestPx;
    private int mExpandedPx;
    /** What the last measure found: the card's whole height uncut, and its parent's room. */
    private int mNaturalPx;
    private int mCeilingPx;
    /** The overshoot last handed to the host, so an unchanged frame hands nothing. */
    private float mReportedOvershootPx;

    private boolean mAnimating;
    private long mLastFrameNanos;

    private final int mTouchSlop;
    private float mDownRawX;
    private float mDownRawY;
    private boolean mDownOnBody;
    private boolean mDragging;
    private float mDragStartRawY;
    private float mOffsetAtDragStart;
    @Nullable private VelocityTracker mVelocity;

    public EditorShellSheet(@NonNull Context context) {
        this(context, null);
    }

    public EditorShellSheet(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EditorShellSheet(@NonNull Context context, @Nullable AttributeSet attrs,
                            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBody = findViewById(R.id.editor_shell_sheet_body);
        mHandle = findViewById(R.id.editor_shell_sheet_handle);
        if (mHandle != null) {
            // The pull's short form, and the one a screen reader can reach.
            mHandle.setContentDescription(
                getContext().getString(R.string.termux_layout_editor_sheet_handle));
            mHandle.setOnClickListener(view -> toggle());
        }
    }

    public void setCallback(@Nullable Callback callback) {
        mCallback = callback;
    }

    /** The handle's touch slot, which the host paints; null on a card built without one. */
    @Nullable
    public View handle() {
        return mHandle;
    }

    /** The scrolling body, the one scroller the card has. */
    @Nullable
    public View body() {
        return mBody;
    }

    /**
     * The two heights the card stands at: at rest, and pulled all the way up. Either at 0 or less
     * is "as tall as the parent allows". A card whose content is shorter than either is simply as
     * tall as its content.
     */
    public void setHeights(int restPx, int expandedPx) {
        if (restPx == mRestPx && expandedPx == mExpandedPx)
            return;
        mRestPx = restPx;
        mExpandedPx = expandedPx;
        requestLayout();
    }

    /** 0 at rest, 1 pulled all the way up. */
    public float expansion() {
        return mExpansion.value;
    }

    /** Back at rest, at once, with nothing in flight: how every visit to the card starts. */
    public void snapToRest() {
        stopAnimation();
        boolean moved = mExpansion.value != 0f;
        mExpansion.reset(0f);
        mOvershoot.reset(0f);
        if (moved)
            requestLayout();
        reportOvershoot();
    }

    /** A tap on the handle: the far end of the pull from wherever the card is. */
    public void toggle() {
        if (mDragging)
            return;
        settleTo(EditorShellSheetPolicy.toggled(mExpansion.target, travelPx()));
    }

    // ------------------------------------------------------------------------------ the heights

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        mCeilingPx = mode == MeasureSpec.UNSPECIFIED ? 0 : MeasureSpec.getSize(heightMeasureSpec);
        // As tall as the card could ever be first, which is also what says how tall its content
        // is; then, if the pull has it shorter than that, at exactly the height the pull asks.
        int tallest = mExpandedPx > 0
            ? (mCeilingPx > 0 ? Math.min(mExpandedPx, mCeilingPx) : mExpandedPx) : mCeilingPx;
        super.onMeasure(widthMeasureSpec, tallest > 0
            ? MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.AT_MOST) : heightMeasureSpec);
        mNaturalPx = naturalHeightPx();
        int wanted = EditorShellSheetPolicy.heightPx(mNaturalPx, mRestPx, mExpandedPx,
            mCeilingPx, mExpansion.value);
        if (wanted != getMeasuredHeight())
            super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(wanted, MeasureSpec.EXACTLY));
    }

    /** The card's height with its body uncut: what was measured, plus what the body hides. */
    private int naturalHeightPx() {
        int measured = getMeasuredHeight();
        View body = mBody;
        if (!(body instanceof android.view.ViewGroup) || body.getVisibility() == GONE
            || ((android.view.ViewGroup) body).getChildCount() == 0)
            return measured;
        View content = ((android.view.ViewGroup) body).getChildAt(0);
        int whole = content.getMeasuredHeight() + body.getPaddingTop() + body.getPaddingBottom();
        return measured + Math.max(0, whole - body.getMeasuredHeight());
    }

    private int restHeightPx() {
        return EditorShellSheetPolicy.restHeightPx(mNaturalPx, mRestPx, mExpandedPx, mCeilingPx);
    }

    /** How far the card can grow, from what the last measure found; 0 where it already fits. */
    public int travelPx() {
        return EditorShellSheetPolicy.expandedHeightPx(mNaturalPx, mRestPx, mExpandedPx,
            mCeilingPx) - restHeightPx();
    }

    // -------------------------------------------------------------------------------- the touch

    /**
     * Every touch on the card passes here first, whoever ends up taking it: the down is where a
     * drag is measured from, and the way up has to be seen even when a child that asked the sheet
     * to keep out — a grip, a thumb — held the whole gesture.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN)
            beginTouch(event);
        track(event);
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
            endTouch();
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return event.getActionMasked() == MotionEvent.ACTION_MOVE && claimDrag(event);
    }

    /**
     * The touches no child took — the card's own air — and every touch of a drag the sheet has
     * claimed. A touch-down is kept either way: it landed on the card, and must not fall through
     * to the place behind it.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (claimDrag(event))
                    moveDrag(event);
                return true;
            case MotionEvent.ACTION_UP:
                if (mDragging) endDrag(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) endDrag(true);
                return true;
            default:
                return true;
        }
    }

    private void beginTouch(@NonNull MotionEvent event) {
        mDownRawX = event.getRawX();
        mDownRawY = event.getRawY();
        View body = mBody;
        mDownOnBody = body != null && body.getVisibility() == VISIBLE
            && event.getY() >= body.getTop() && event.getY() < body.getBottom();
        mDragging = false;
        if (mVelocity == null) mVelocity = VelocityTracker.obtain();
        else mVelocity.clear();
    }

    /** Whether this move makes the gesture the sheet's; the answer stands for the rest of it. */
    private boolean claimDrag(@NonNull MotionEvent event) {
        if (mDragging)
            return true;
        float pulledUp = mDownRawY - event.getRawY();
        float across = event.getRawX() - mDownRawX;
        if (Math.abs(pulledUp) <= mTouchSlop || Math.abs(pulledUp) < Math.abs(across))
            return false;
        View body = mBody;
        boolean bodyAtTop = body == null || !body.canScrollVertically(-1);
        if (!EditorShellSheetPolicy.claimsDrag(mDownOnBody, pulledUp, mExpansion.value,
                travelPx(), bodyAtTop))
            return false;
        mDragging = true;
        // Whatever the springs were doing, the finger has them now, where they are; and it has
        // them from where it is now, so the card does not jump by the slop.
        stopAnimation();
        mDragStartRawY = event.getRawY();
        mOffsetAtDragStart = offsetPx();
        ViewParent parent = getParent();
        if (parent != null)
            parent.requestDisallowInterceptTouchEvent(true);
        return true;
    }

    private void moveDrag(@NonNull MotionEvent event) {
        float travel = travelPx();
        float rest = restHeightPx();
        float offset = EditorShellSheetPolicy.clampOffsetPx(
            mOffsetAtDragStart + (mDragStartRawY - event.getRawY()), travel, rest);
        float expansion = EditorShellSheetPolicy.expansionOf(offset, travel);
        if (Math.abs(expansion - mExpansion.value) > 0.0005f) {
            mExpansion.reset(expansion);
            requestLayout();
        }
        mOvershoot.reset(rest > 0f ? EditorShellSheetPolicy.overshootOf(offset) / rest : 0f);
        reportOvershoot();
    }

    /** The finger let go, or the system took the touch: the sheet settles from where it is. */
    private void endDrag(boolean cancelled) {
        float velocityUp = 0f;
        if (mVelocity != null) {
            mVelocity.computeCurrentVelocity(1000);
            velocityUp = -mVelocity.getYVelocity();
        }
        mDragging = false;
        float density = getResources().getDisplayMetrics().density;
        float offset = offsetPx();
        float travel = travelPx();
        EditorShellSheetPolicy.Snap snap = cancelled
            ? EditorShellSheetPolicy.settle(Math.max(0f, offset), travel, 0f, 0f, 0f)
            : EditorShellSheetPolicy.settle(offset, travel, velocityUp,
                EditorShellSheetPolicy.FLING_DP_PER_S * density,
                Math.max(EditorShellSheetPolicy.DISMISS_MIN_DP * density,
                    EditorShellSheetPolicy.DISMISS_FRACTION * restHeightPx()));
        settleTo(snap);
    }

    private void endTouch() {
        if (mVelocity != null) {
            mVelocity.recycle();
            mVelocity = null;
        }
        // A drag whose up never reached onTouchEvent still settles rather than hanging part-way.
        if (mDragging)
            endDrag(true);
    }

    /** The velocity tracker is fed raw coordinates: the card moves under the finger. */
    private void track(@NonNull MotionEvent event) {
        if (mVelocity == null)
            return;
        MotionEvent raw = MotionEvent.obtain(event);
        raw.setLocation(event.getRawX(), event.getRawY());
        mVelocity.addMovement(raw);
        raw.recycle();
    }

    /** The pull as one number: grown above rest, or pushed below it. */
    private float offsetPx() {
        return mExpansion.value * travelPx() - mOvershoot.value * restHeightPx();
    }

    // ------------------------------------------------------------------------------ the settle

    private void settleTo(@NonNull EditorShellSheetPolicy.Snap snap) {
        if (snap == EditorShellSheetPolicy.Snap.DISMISS) {
            float overshoot = Math.max(0f, mOvershoot.value) * restHeightPx();
            if (mCallback != null && mCallback.onSheetDismissRequested(overshoot)) {
                // The host has the card now, from where the finger left it.
                stopAnimation();
                mExpansion.reset(0f);
                mOvershoot.reset(0f);
                mReportedOvershootPx = 0f;
                return;
            }
            snap = EditorShellSheetPolicy.Snap.COLLAPSED;
        }
        mExpansion.target = snap == EditorShellSheetPolicy.Snap.EXPANDED ? 1f : 0f;
        mOvershoot.target = 0f;
        startAnimation();
    }

    private void startAnimation() {
        removeCallbacks(mFrame);
        if (ReducedMotion.isEnabled(getContext())) {
            mAnimating = false;
            boolean moved = mExpansion.value != mExpansion.target;
            mExpansion.reset(mExpansion.target);
            mOvershoot.reset(mOvershoot.target);
            if (moved)
                requestLayout();
            reportOvershoot();
            return;
        }
        mAnimating = true;
        mLastFrameNanos = 0L;
        postOnAnimation(mFrame);
    }

    private void stopAnimation() {
        removeCallbacks(mFrame);
        mAnimating = false;
    }

    private final Runnable mFrame = new Runnable() {
        @Override
        public void run() {
            if (!mAnimating)
                return;
            long now = System.nanoTime();
            float dt = mLastFrameNanos == 0L ? Spring.MIN_DT
                : Spring.clampDelta((now - mLastFrameNanos) / 1_000_000_000f);
            mLastFrameNanos = now;
            float before = mExpansion.value;
            boolean growing = mExpansion.tick(false, dt);
            boolean sinking = mOvershoot.tick(false, dt);
            if (!growing) mExpansion.reset(mExpansion.target);
            if (!sinking) mOvershoot.reset(mOvershoot.target);
            if (mExpansion.value != before)
                requestLayout();
            reportOvershoot();
            if (growing || sinking) {
                postOnAnimation(this);
                return;
            }
            mAnimating = false;
        }
    };

    private void reportOvershoot() {
        float overshoot = Math.max(0f, mOvershoot.value) * restHeightPx();
        if (overshoot == mReportedOvershootPx)
            return;
        mReportedOvershootPx = overshoot;
        if (mCallback != null)
            mCallback.onSheetOvershoot(overshoot);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        if (mVelocity != null) {
            mVelocity.recycle();
            mVelocity = null;
        }
        mDragging = false;
        super.onDetachedFromWindow();
    }
}
