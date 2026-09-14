package com.termux.app.tour;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.FocusOutlineRenderer;
import com.termux.app.notice.TerminalDress;

/**
 * The run's only view: a glow around the control the card is about, a finger tracing the gesture
 * once, and the card itself.
 *
 * <p>It never takes a touch. The launcher is the home screen and the tour is teaching gestures on
 * live chrome, so the user has to be able to actually perform the gesture while the card is up —
 * the overlay is not clickable, not focusable, and {@link #onTouchEvent} refuses every event so
 * the sibling below it gets the stream. The only thing that does take a touch is the card's own
 * text button, which is a child view and so is reached before this view is consulted.
 *
 * <p>The glow is {@link FocusOutlineRenderer}'s rounded-rect focus treatment, the same one the
 * dock and terminal search wear, and the card is the notice chip's dress — fill, hairline and the
 * terminal's own radius, flat, from {@link TerminalDress}.
 *
 * <p>A target that cannot be measured is normal: the card shows without a glow rather than
 * pointing somewhere wrong.
 */
public final class TourOverlayView extends FrameLayout {

    /** The card's buttons. Every card but the last one carries Skip alone. */
    public interface Callbacks {
        void onTourSkipTapped();

        void onTourFinishTapped();

        /** The closing card's Copy commands: put its shell lines on the clipboard. */
        void onTourCopyCommandsTapped();
    }

    private static final long CARD_IN_MS = 200L;
    private static final float CARD_RISE_DP = 8f;
    private static final float CARD_MAX_WIDTH_DP = 300f;
    private static final float CARD_SIDE_MARGIN_DP = 16f;
    /** The gap between the control and the card's pointer. */
    private static final float CARD_GAP_DP = 8f;
    private static final float POINTER_HEIGHT_DP = 7f;
    private static final float POINTER_HALF_WIDTH_DP = 9f;
    private static final float GLOW_PADDING_DP = 4f;
    private static final float GLOW_RADIUS_DP = 12f;
    private static final float FINGER_RADIUS_DP = 9f;
    private static final float FINGER_TRAIL_WIDTH_DP = 3f;

    private final float mDensity;
    private final TerminalDress mDress;
    private final Paint mFingerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPointerFill = new Path();
    private final Path mPointerEdges = new Path();
    private final RectF mGlowRect = new RectF();
    private final float[] mFingerPoint = new float[2];
    private final float[] mTrailPoint = new float[2];

    private final LinearLayout mCard;
    private final TextView mCopy;
    private final TextView mBody;
    private final LinearLayout mButtonRow;
    private final TextView mCopyCommands;
    private final TextView mButton;

    @Nullable private Callbacks mCallbacks;
    @Nullable private TourTargets mTargets;
    @Nullable private TourStep mStep;
    @Nullable private Rect mTargetRect;
    @Nullable private ValueAnimator mTrace;
    @Nullable private TourCardPlacement mPlacement;
    private int mSystemInsetTop;
    private int mSystemInsetBottom;
    /** Why the last measurement found no target, kept for the log rather than for the drawing. */
    @NonNull private String mMissReason = "none";

    private int mStage;
    private int mAccent;
    private float mTraceProgress = 1f;

    public TourOverlayView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        // Passive by construction: the gesture the card is asking for belongs to the chrome below.
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mAccent = FocusOutlineRenderer.resolveAccent(this);

        mDress = TerminalDress.stored(context);
        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.VERTICAL);
        mCard.setBackground(mDress.background(0));
        mCard.setElevation(0f);
        mCard.setPadding(dp(14), dp(10), dp(14), dp(8));
        mCard.setClickable(false);
        mCard.setFocusable(false);

        mCopy = new TextView(context);
        mCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mCopy.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mCopy.setTextColor(mDress.textColor);
        mCopy.setLineSpacing(dp(2), 1f);
        mCard.addView(mCopy, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The closing card's three lines. Secondary to the sentence above them, and the only card
        // that has any, so it is gone for the other eight rather than empty.
        mBody = new TextView(context);
        mBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mBody.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 204));
        mBody.setLineSpacing(dp(2), 1f);
        mBody.setVisibility(GONE);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(8);
        mCard.addView(mBody, bodyParams);

        mButtonRow = new LinearLayout(context);
        mButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        mButtonRow.setGravity(Gravity.END);

        mCopyCommands = textButton(context, view -> onCopyCommandsTapped());
        mCopyCommands.setVisibility(GONE);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyParams.rightMargin = dp(6);
        mButtonRow.addView(mCopyCommands, copyParams);

        mButton = textButton(context, view -> onButtonTapped());
        mButtonRow.addView(mButton, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.gravity = Gravity.END;
        buttonParams.topMargin = dp(2);
        buttonParams.rightMargin = -dp(4);
        mCard.addView(mButtonRow, buttonParams);

        addView(mCard, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** One of the card's text buttons: the only children of this view that take a touch. */
    @NonNull
    private TextView textButton(@NonNull Context context, @NonNull OnClickListener onClick) {
        TextView button = new TextView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(mAccent);
        button.setAllCaps(false);
        button.setPadding(dp(8), dp(6), dp(8), dp(6));
        button.setBackground(buttonBackground());
        button.setOnClickListener(onClick);
        return button;
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    public void setTargets(@Nullable TourTargets targets) {
        mTargets = targets;
    }

    /**
     * The system bars' keep-out, so a card that has nothing to anchor to, or that is clamped to an
     * end of the overlay, does not come to rest under the status bar or the gesture bar. The
     * overlay fills the whole window, so without this the two ends of it are the wrong place.
     */
    public void setSystemBarInsets(int top, int bottom) {
        if (mSystemInsetTop == top && mSystemInsetBottom == bottom) return;
        mSystemInsetTop = Math.max(0, top);
        mSystemInsetBottom = Math.max(0, bottom);
        requestLayout();
        invalidate();
    }

    /** Shows a card, and traces its gesture once. */
    public void showStep(@NonNull TourStep step, int stage) {
        boolean sameCard = mStep != null && mStep.id.equals(step.id) && mStage == stage;
        mStep = step;
        mStage = stage;
        mCopy.setText(step.showsSecondLineAt(stage) ? step.secondLineRes : step.copyRes);
        boolean closing = step.signalCount() == 0;
        mButton.setText(closing ? R.string.tour_done : R.string.tour_skip);
        if (closing) showClosingBody();
        else {
            mBody.setVisibility(GONE);
            mCopyCommands.setVisibility(GONE);
        }
        setVisibility(VISIBLE);
        if (!sameCard) animateCardIn();
        refreshTarget();
        startTrace();
    }

    /** Re-measures the control the card points at; cheap enough for every layout pass. */
    public void refreshTarget() {
        if (mStep == null) return;
        String targetId = mStep.targetIdAt(mStage);
        Rect updated = null;
        String reason = "no targets host";
        if (mTargets != null) {
            updated = mTargets.rectFor(targetId);
            reason = updated == null ? mTargets.lastMissReason() : "none";
        }
        boolean moved = updated == null ? mTargetRect != null : !updated.equals(mTargetRect);
        boolean reasonChanged = !reason.equals(mMissReason);
        mTargetRect = updated;
        mMissReason = reason;
        // Logged on the edge, not per layout pass: this runs on every global layout, and the
        // keyboard alone produces dozens of them.
        if (updated == null && (moved || reasonChanged) && TourLog.enabled()) {
            TourLog.d("card " + mStep.id + ":" + mStage + " has no glow — target \"" + targetId
                + "\": " + reason);
        }
        if (moved) {
            requestLayout();
            invalidate();
        }
    }

    /** The control the card is glowing right now, for the log. Null when it has none. */
    @Nullable
    Rect currentTargetRect() {
        return mTargetRect;
    }

    /** Why {@link #currentTargetRect()} is null, for the log. */
    @NonNull
    String currentMissReason() {
        return mMissReason;
    }

    /** Takes the card down and stops the trace. */
    public void dismiss() {
        stopTrace();
        mStep = null;
        mTargetRect = null;
        mPlacement = null;
        mMissReason = "none";
        setVisibility(GONE);
    }

    /** The whole point: every touch belongs to the chrome under the overlay. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutCard();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mStep == null) return;
        drawCardPointer(canvas);
        if (mTargetRect == null || mTargetRect.isEmpty()) return;
        drawGlow(canvas);
        drawFinger(canvas);
    }

    private void drawGlow(@NonNull Canvas canvas) {
        float padding = GLOW_PADDING_DP * mDensity;
        mGlowRect.set(mTargetRect.left - padding, mTargetRect.top - padding,
            mTargetRect.right + padding, mTargetRect.bottom + padding);
        FocusOutlineRenderer.drawRoundRectFallback(canvas, mGlowRect, GLOW_RADIUS_DP * mDensity,
            mAccent, 1f, 1f, mDensity);
    }

    private void drawFinger(@NonNull Canvas canvas) {
        TourGesture gesture = mStep.gestureAt(mStage);
        if (gesture == TourGesture.NONE || mTraceProgress >= 1f) return;
        TourFingerTrace.pointAt(gesture, mTargetRect.left, mTargetRect.top, mTargetRect.right,
            mTargetRect.bottom, mDensity, mTraceProgress, mFingerPoint);
        float radius = FINGER_RADIUS_DP * mDensity;
        int opaque = Color.rgb(Color.red(mAccent), Color.green(mAccent), Color.blue(mAccent));

        if (gesture != TourGesture.TAP) {
            TourFingerTrace.pointAt(gesture, mTargetRect.left, mTargetRect.top, mTargetRect.right,
                mTargetRect.bottom, mDensity, 0f, mTrailPoint);
            mFingerPaint.setStyle(Paint.Style.STROKE);
            mFingerPaint.setStrokeCap(Paint.Cap.ROUND);
            mFingerPaint.setStrokeWidth(FINGER_TRAIL_WIDTH_DP * mDensity);
            mFingerPaint.setColor(ColorUtils.setAlphaComponent(opaque, 46));
            canvas.drawLine(mTrailPoint[0], mTrailPoint[1], mFingerPoint[0], mFingerPoint[1],
                mFingerPaint);
        } else {
            float pulse = TourFingerTrace.tapPulse(mTraceProgress);
            mFingerPaint.setStyle(Paint.Style.STROKE);
            mFingerPaint.setStrokeWidth(Math.max(1f, 1.5f * mDensity));
            mFingerPaint.setColor(ColorUtils.setAlphaComponent(opaque,
                Math.round(90f * (1f - pulse))));
            canvas.drawCircle(mFingerPoint[0], mFingerPoint[1], radius + (radius * pulse),
                mFingerPaint);
        }

        mFingerPaint.setStyle(Paint.Style.FILL);
        mFingerPaint.setColor(ColorUtils.setAlphaComponent(opaque, 56));
        canvas.drawCircle(mFingerPoint[0], mFingerPoint[1], radius, mFingerPaint);
        mFingerPaint.setStyle(Paint.Style.STROKE);
        mFingerPaint.setStrokeWidth(Math.max(1f, 1.5f * mDensity));
        mFingerPaint.setColor(ColorUtils.setAlphaComponent(opaque, 199));
        canvas.drawCircle(mFingerPoint[0], mFingerPoint[1], radius, mFingerPaint);
    }

    /**
     * Against the control: centred on it, below it when it is in the top half of the overlay and
     * above it otherwise, flipped when that side has no room, and clamped inside the margins. The
     * arithmetic is {@link TourCardPlacement}'s; this only applies the answer.
     */
    private void layoutCard() {
        if (mStep == null || mCard.getVisibility() == GONE) return;
        int width = mCard.getMeasuredWidth();
        int height = mCard.getMeasuredHeight();
        if (width <= 0 || height <= 0) return;
        int margin = dp(CARD_SIDE_MARGIN_DP);
        TourCardPlacement placement = TourCardPlacement.place(getWidth(), getHeight(),
            width, height, mTargetRect, margin, margin + mSystemInsetTop,
            margin + mSystemInsetBottom, dp(CARD_GAP_DP), dp(POINTER_HEIGHT_DP),
            dp(POINTER_HALF_WIDTH_DP));
        mPlacement = placement;
        mCard.layout(placement.left, placement.top, placement.left + width,
            placement.top + height);
        GradientDrawable background = mCard.getBackground() instanceof GradientDrawable
            ? (GradientDrawable) mCard.getBackground() : null;
        if (background != null)
            background.setCornerRadius(mDress.cornerRadiusPx(height));
    }

    /**
     * The card's pointer: the same fill and the same hairline the card itself wears, its base
     * tucked a pixel under the card so the two share no visible seam.
     */
    private void drawCardPointer(@NonNull Canvas canvas) {
        TourCardPlacement placement = mPlacement;
        if (placement == null || !placement.hasPointer() || mCard.getVisibility() == GONE) return;
        float height = POINTER_HEIGHT_DP * mDensity;
        float halfWidth = POINTER_HALF_WIDTH_DP * mDensity;
        boolean up = placement.pointerEdge == TourCardPlacement.POINTER_TOP;
        // The card rises into place; the pointer travels and fades with it rather than sitting
        // detached under a card that has not arrived yet.
        float offset = mCard.getTranslationY();
        float alpha = mCard.getAlpha();
        if (alpha <= 0.01f) return;
        float base = (up ? mCard.getTop() + 1f : mCard.getBottom() - 1f) + offset;
        float tip = up ? base - height : base + height;
        float centerX = placement.pointerCenterX;

        mPointerFill.reset();
        mPointerFill.moveTo(centerX - halfWidth, base);
        mPointerFill.lineTo(centerX, tip);
        mPointerFill.lineTo(centerX + halfWidth, base);
        mPointerFill.close();
        mPointerPaint.setStyle(Paint.Style.FILL);
        mPointerPaint.setColor(mDress.fillColor);
        mPointerPaint.setAlpha(Math.round(Color.alpha(mDress.fillColor) * alpha));
        canvas.drawPath(mPointerFill, mPointerPaint);

        // Only the two slanted sides: the base is inside the card, where there is no edge to draw.
        mPointerEdges.reset();
        mPointerEdges.moveTo(centerX - halfWidth, base);
        mPointerEdges.lineTo(centerX, tip);
        mPointerEdges.lineTo(centerX + halfWidth, base);
        mPointerPaint.setStyle(Paint.Style.STROKE);
        mPointerPaint.setStrokeJoin(Paint.Join.ROUND);
        mPointerPaint.setStrokeWidth(mDress.strokeWidthPx);
        mPointerPaint.setColor(mDress.strokeColor);
        mPointerPaint.setAlpha(Math.round(Color.alpha(mDress.strokeColor) * alpha));
        canvas.drawPath(mPointerEdges, mPointerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int available = MeasureSpec.getSize(widthMeasureSpec) - (2 * dp(CARD_SIDE_MARGIN_DP));
        int max = Math.min(dp(CARD_MAX_WIDTH_DP), Math.max(dp(120f), available));
        measureChild(mCard,
            MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec),
                MeasureSpec.AT_MOST));
    }

    private void animateCardIn() {
        mCard.animate().cancel();
        if (!FocusOutlineRenderer.animationsEnabled(getContext())) {
            mCard.setAlpha(1f);
            mCard.setTranslationY(0f);
            return;
        }
        mCard.setAlpha(0f);
        mCard.setTranslationY(-CARD_RISE_DP * mDensity);
        // The pointer is drawn by this view, not by the card, so every frame of the card's rise
        // has to be a frame of this view too or the two would arrive separately.
        mCard.animate().alpha(1f).translationY(0f).setDuration(CARD_IN_MS)
            .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).withLayer()
            .setUpdateListener(animation -> invalidate()).start();
    }

    /** One pass of the gesture per card. Nothing here loops. */
    private void startTrace() {
        stopTrace();
        if (mStep == null || mStep.gestureAt(mStage) == TourGesture.NONE
            || !FocusOutlineRenderer.animationsEnabled(getContext())) {
            mTraceProgress = 1f;
            invalidate();
            return;
        }
        mTraceProgress = 0f;
        mTrace = ValueAnimator.ofFloat(0f, 1f);
        mTrace.setDuration(TourFingerTrace.TRACE_MS);
        mTrace.addUpdateListener(animator -> {
            mTraceProgress = (float) animator.getAnimatedValue();
            invalidate();
        });
        mTrace.start();
    }

    private void stopTrace() {
        if (mTrace != null) {
            mTrace.cancel();
            mTrace = null;
        }
        mTraceProgress = 1f;
    }

    /** The three lines the running edition installs things with, plus their Copy button. */
    private void showClosingBody() {
        TourEdition edition = TourEdition.of(getContext().getPackageName());
        StringBuilder text = new StringBuilder();
        for (int line : TourClosingCard.bodyLines(edition)) {
            if (text.length() > 0) text.append("\n\n");
            text.append(getContext().getString(line));
        }
        mBody.setText(text);
        mBody.setVisibility(VISIBLE);
        mCopyCommands.setText(R.string.tour_copy_commands);
        mCopyCommands.setVisibility(VISIBLE);
    }

    private void onCopyCommandsTapped() {
        if (mCallbacks == null) return;
        mCallbacks.onTourCopyCommandsTapped();
        // The run draws no toasts, so the button itself is the acknowledgement.
        mCopyCommands.setText(R.string.tour_copied_commands);
    }

    private void onButtonTapped() {
        if (mCallbacks == null || mStep == null) return;
        if (mStep.signalCount() == 0) mCallbacks.onTourFinishTapped();
        else mCallbacks.onTourSkipTapped();
    }

    @NonNull
    private GradientDrawable buttonBackground() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(mAccent, 20));
        shape.setCornerRadius(dp(8f));
        return shape;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTrace();
        super.onDetachedFromWindow();
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    /** Layout params for the content root: the whole window, under nothing. */
    @NonNull
    public static FrameLayout.LayoutParams buildLayoutParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.START);
    }

    /** Kept for the host, which re-resolves the accent when the theme changes under the run. */
    public void refreshAccent() {
        mAccent = FocusOutlineRenderer.resolveAccent(this);
        mButton.setTextColor(mAccent);
        mButton.setBackground(buttonBackground());
        mCopyCommands.setTextColor(mAccent);
        mCopyCommands.setBackground(buttonBackground());
        invalidate();
    }
}
