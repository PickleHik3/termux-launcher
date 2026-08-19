package com.termux.app.notice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The launcher's in-app notice surface: a monospace chip that extends downward out of the
 * top-trailing chrome edge, holds one message, then retracts.
 *
 * <p>Replaces the stock Android {@code Toast} everywhere in the app. A toast is bottom-centre, sits
 * over the shell prompt and the keyboard, cannot be themed and cannot be positioned at all from
 * Android 11 onward; this chip lives in the one corner nothing else competes for.
 *
 * <p>Shape follows the handoff: square top corners, rounded bottom ones, and a hairline drawn only
 * down the leading edge and across the bottom, so the panel reads as something that pulled out of
 * the bar above it rather than as a free-floating card. The container extends and retracts by
 * height; each individual message folds in and out around the top edge, so a burst of notices reads
 * as one surface swapping its contents instead of several cards fighting for the corner.
 *
 * <p>Anything raised while a message is up is queued (most recent four) and the chip shows a
 * {@code +N} counter, so a loop that raises twenty notices still resolves in bounded time.
 */
public final class AppNoticeHostView extends FrameLayout {

    /** Design durations, in ms. */
    private static final long EXTEND_MS = 340L;
    private static final long RETRACT_MS = 280L;
    private static final long FOLD_IN_MS = 340L;
    private static final long FOLD_OUT_MS = 240L;
    private static final long PULSE_MS = 900L;

    /** Hold times, mapped from the {@code Toast.LENGTH_*} the call sites used to pass. */
    public static final long HOLD_SHORT_MS = 2600L;
    public static final long HOLD_LONG_MS = 3800L;

    /** Beyond this the oldest queued notices are dropped — a burst must still drain. */
    private static final int MAX_QUEUED = 4;

    private static final int SURFACE_ALPHA = 225;
    private static final int OUTLINE_ALPHA = 92;
    private static final float CORNER_DP = 12f;
    private static final float ROW_MIN_HEIGHT_DP = 30f;
    private static final float MAX_WIDTH_DP = 280f;
    /** Perspective depth for the fold, matching the prototype's {@code perspective(420px)}. */
    private static final float CAMERA_DISTANCE_DP = 420f;

    /**
     * How much vertical room the chip is taking in the top-trailing corner, so the session-switch
     * chip and the background-process stack below it can follow it down and back up rather than
     * reserve a permanent gap.
     */
    public interface OccupancyListener {
        void onNoticeOccupancyChanged(int heightPx);
    }

    private final Deque<AppNoticeItem> mQueue = new ArrayDeque<>();

    private final LinearLayout mRow;
    private final FrameLayout mGlyphSlot;
    private final AppCompatTextView mGlyph;
    private final View mPulse;
    private final AppCompatTextView mTitle;
    private final AppCompatTextView mSub;
    private final AppCompatTextView mCount;
    private final View mBar;

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mFillPath = new Path();
    private final Path mEdgePath = new Path();
    private final RectF mBounds = new RectF();

    private final Interpolator mExtendInterpolator;
    private final Interpolator mRetractInterpolator;
    private final Interpolator mFoldInInterpolator;
    private final Interpolator mFoldOutInterpolator;

    private final int mAccentInfo;
    private final int mAccentError;

    @Nullable private OccupancyListener mOccupancyListener;
    private int mReportedHeightPx = -1;

    @Nullable private ValueAnimator mHeightAnimator;
    @Nullable private Runnable mHoldRunnable;
    @Nullable private AppNoticeItem mActive;
    /** 0 while retracted, 1 while fully extended; drives {@link #onMeasure}. */
    private float mHeightFraction;
    private int mNaturalHeightPx;
    /** Last text cap pushed into the labels, so measuring never re-triggers their layout. */
    private int mAppliedTextCapPx = -1;
    /**
     * Alternates per message so two notices in a row still play the fold: an identical animation
     * restarted on the same view is a no-op in the eyes of the property animator.
     */
    private boolean mFoldParity;

    public AppNoticeHostView(@NonNull Context context) {
        super(context);
        setClickable(true);
        setFocusable(false);
        setWillNotDraw(false);
        setClipChildren(true);
        setClipToPadding(true);
        setVisibility(GONE);
        setOnClickListener(v -> dismissActive());

        mExtendInterpolator = interpolator(0.16f, 1.05f, 0.3f, 1f, true);
        mRetractInterpolator = interpolator(0.4f, 0f, 1f, 1f, false);
        mFoldInInterpolator = interpolator(0.2f, 0.85f, 0.25f, 1f, true);
        mFoldOutInterpolator = interpolator(0.4f, 0f, 1f, 1f, false);

        int surface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                ContextCompat.getColor(context, R.color.termux_surface_panel_high)));
        int outline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));
        int onSurface = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mAccentInfo = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorPrimary, onSurface);
        mAccentError = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorError, mAccentInfo);

        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(surface, SURFACE_ALPHA));
        mEdgePaint.setStyle(Paint.Style.STROKE);
        mEdgePaint.setStrokeWidth(Math.max(1f, dp(1f) * 0.9f));
        mEdgePaint.setColor(ColorUtils.setAlphaComponent(outline, OUTLINE_ALPHA));

        mRow = new LinearLayout(context);
        mRow.setOrientation(LinearLayout.HORIZONTAL);
        mRow.setGravity(Gravity.CENTER_VERTICAL);
        mRow.setMinimumHeight(Math.round(dp(ROW_MIN_HEIGHT_DP)));
        mRow.setPadding(Math.round(dp(11f)), Math.round(dp(5f)),
            Math.round(dp(11f)), Math.round(dp(6f)));
        addView(mRow, new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));

        mGlyphSlot = new FrameLayout(context);
        mGlyphSlot.setClipChildren(false);
        mGlyphSlot.setClipToPadding(false);
        LinearLayout.LayoutParams glyphSlotParams = new LinearLayout.LayoutParams(
            Math.round(dp(18f)), Math.round(dp(18f)));
        glyphSlotParams.setMarginEnd(Math.round(dp(7f)));
        mRow.addView(mGlyphSlot, glyphSlotParams);

        mPulse = new View(context);
        GradientDrawable pulse = new GradientDrawable();
        pulse.setShape(GradientDrawable.OVAL);
        pulse.setColor(Color.TRANSPARENT);
        pulse.setStroke(Math.max(1, Math.round(dp(1f))), mAccentInfo);
        mPulse.setBackground(pulse);
        mPulse.setAlpha(0f);
        mGlyphSlot.addView(mPulse, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mGlyph = new AppCompatTextView(context);
        mGlyph.setGravity(Gravity.CENTER);
        mGlyph.setIncludeFontPadding(false);
        mGlyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mGlyph.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mGlyphSlot.addView(mGlyph, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mTitle = new AppCompatTextView(context);
        mTitle.setIncludeFontPadding(false);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        mTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mTitle.setTextColor(onSurface);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mRow.addView(mTitle, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mSub = new AppCompatTextView(context);
        mSub.setIncludeFontPadding(false);
        mSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        mSub.setTypeface(Typeface.MONOSPACE);
        mSub.setTextColor(ColorUtils.setAlphaComponent(onSurface, 107));
        mSub.setSingleLine(true);
        mSub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.setMarginStart(Math.round(dp(7f)));
        mRow.addView(mSub, subParams);

        mCount = new AppCompatTextView(context);
        mCount.setIncludeFontPadding(false);
        mCount.setGravity(Gravity.CENTER);
        mCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        mCount.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mCount.setPadding(Math.round(dp(5f)), Math.round(dp(1f)),
            Math.round(dp(5f)), Math.round(dp(1f)));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.setMarginStart(Math.round(dp(7f)));
        mRow.addView(mCount, countParams);

        mBar = new View(context);
        addView(mBar, new LayoutParams(LayoutParams.MATCH_PARENT,
            Math.max(1, Math.round(dp(1.5f))), Gravity.BOTTOM));
        mBar.setPivotX(0f);
        mBar.setVisibility(INVISIBLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setElevation(dp(3f));
            mRow.setCameraDistance(dp(CAMERA_DISTANCE_DP));
        }
    }

    public void setOccupancyListener(@Nullable OccupancyListener listener) {
        mOccupancyListener = listener;
        mReportedHeightPx = -1;
        notifyOccupancy();
    }

    /** Queue a notice, showing it immediately when the chip is idle. */
    public void enqueue(@NonNull AppNoticeItem item) {
        mQueue.addLast(item);
        while (mQueue.size() > MAX_QUEUED) mQueue.removeFirst();
        if (mActive == null) showNext();
        else updateCount();
    }

    /** Drop everything, without animation. Used when the host activity goes away. */
    public void clear() {
        mQueue.clear();
        cancelHold();
        if (mHeightAnimator != null) {
            mHeightAnimator.cancel();
            mHeightAnimator = null;
        }
        mRow.animate().cancel();
        mBar.animate().cancel();
        mPulse.animate().cancel();
        mActive = null;
        mHeightFraction = 0f;
        setVisibility(GONE);
        requestLayout();
        notifyOccupancy();
    }

    private void showNext() {
        AppNoticeItem item = mQueue.pollFirst();
        if (item == null) {
            retract();
            return;
        }
        boolean firstOfBurst = mActive == null;
        mActive = item;
        bind(item);
        setVisibility(VISIBLE);
        if (firstOfBurst) extend();
        else animateHeightTo(1f, EXTEND_MS, mExtendInterpolator);
        foldIn();
        runBar(item.durationMs);
        pulse();
        cancelHold();
        mHoldRunnable = this::foldOutActive;
        postDelayed(mHoldRunnable, item.durationMs);
    }

    private void bind(@NonNull AppNoticeItem item) {
        int accent = item.kind == AppNoticeItem.Kind.ERROR
            || item.kind == AppNoticeItem.Kind.WARNING ? mAccentError : mAccentInfo;

        GradientDrawable glyphBackground = new GradientDrawable();
        glyphBackground.setShape(GradientDrawable.OVAL);
        glyphBackground.setColor(ColorUtils.setAlphaComponent(accent, 46));
        mGlyph.setBackground(glyphBackground);
        mGlyph.setText(item.resolvedGlyph());
        mGlyph.setTextColor(accent);

        GradientDrawable pulse = new GradientDrawable();
        pulse.setShape(GradientDrawable.OVAL);
        pulse.setColor(Color.TRANSPARENT);
        pulse.setStroke(Math.max(1, Math.round(dp(1f))),
            ColorUtils.setAlphaComponent(accent, 178));
        mPulse.setBackground(pulse);

        mTitle.setText(item.title);
        boolean hasSub = !TextUtils.isEmpty(item.sub);
        mSub.setText(hasSub ? item.sub : "");
        mSub.setVisibility(hasSub ? VISIBLE : GONE);
        // A bare message has the whole chip to itself and may wrap; paired with a subtitle the two
        // share one line and the title is the half that must stay readable, so it holds its width
        // and the subtitle is what gets clipped.
        mTitle.setSingleLine(hasSub);
        mTitle.setMaxLines(hasSub ? 1 : 3);

        mBar.setBackgroundColor(ColorUtils.setAlphaComponent(accent, 178));
        updateCount();
    }

    private void updateCount() {
        int queued = mQueue.size();
        if (queued <= 0) {
            mCount.setVisibility(GONE);
            return;
        }
        int accent = mActive != null && (mActive.kind == AppNoticeItem.Kind.ERROR
            || mActive.kind == AppNoticeItem.Kind.WARNING) ? mAccentError : mAccentInfo;
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(5f));
        pill.setColor(ColorUtils.setAlphaComponent(accent, 51));
        mCount.setBackground(pill);
        mCount.setTextColor(accent);
        mCount.setText("+" + queued);
        mCount.setVisibility(VISIBLE);
    }

    /**
     * Grows the panel to its full height. Deliberately animates from wherever the height currently
     * is rather than snapping to zero first: a notice raised while the last one is still retracting
     * should catch the panel on the way down, not restart it.
     */
    private void extend() {
        animateHeightTo(1f, EXTEND_MS, mExtendInterpolator);
    }

    private void retract() {
        mActive = null;
        cancelHold();
        mBar.setVisibility(INVISIBLE);
        animateHeightTo(0f, RETRACT_MS, mRetractInterpolator);
    }

    private void animateHeightTo(float target, long duration, @NonNull Interpolator interpolator) {
        if (mHeightAnimator != null) mHeightAnimator.cancel();
        if (mHeightFraction == target) {
            if (target <= 0f) setVisibility(GONE);
            notifyOccupancy();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mHeightFraction, target);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> {
            mHeightFraction = (float) a.getAnimatedValue();
            requestLayout();
            notifyOccupancy();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                mHeightAnimator = null;
                if (target <= 0f && mActive == null) {
                    setVisibility(GONE);
                    notifyOccupancy();
                }
            }
        });
        mHeightAnimator = animator;
        animator.start();
    }

    private void foldIn() {
        mFoldParity = !mFoldParity;
        mRow.animate().cancel();
        mRow.setAlpha(0f);
        mRow.setPivotX(mRow.getWidth() * 0.5f);
        mRow.setPivotY(0f);
        mRow.setRotationX(-78f);
        mRow.setTranslationY(-dp(8f));
        mRow.animate()
            .alpha(1f)
            .rotationX(0f)
            .translationY(0f)
            .setDuration(FOLD_IN_MS)
            .setInterpolator(mFoldInInterpolator)
            .start();
    }

    private void foldOutActive() {
        mHoldRunnable = null;
        if (mActive == null) return;
        mRow.animate().cancel();
        mRow.setPivotX(mRow.getWidth() * 0.5f);
        mRow.setPivotY(0f);
        mRow.animate()
            .alpha(0f)
            .rotationX(72f)
            .translationY(-dp(6f))
            .setDuration(FOLD_OUT_MS)
            .setInterpolator(mFoldOutInterpolator)
            .withEndAction(() -> {
                if (mQueue.isEmpty()) {
                    retract();
                } else {
                    showNext();
                }
            })
            .start();
    }

    /** Tap anywhere on the chip: cut the hold short rather than wait it out. */
    private void dismissActive() {
        if (mActive == null) return;
        cancelHold();
        foldOutActive();
    }

    private void runBar(long durationMs) {
        mBar.animate().cancel();
        mBar.setVisibility(VISIBLE);
        mBar.setScaleX(1f);
        mBar.animate()
            .scaleX(0f)
            .setDuration(durationMs)
            .setInterpolator(new LinearInterpolator())
            .start();
    }

    private void pulse() {
        mPulse.animate().cancel();
        mPulse.setAlpha(0.55f);
        mPulse.setScaleX(1f);
        mPulse.setScaleY(1f);
        mPulse.animate()
            .alpha(0f)
            .scaleX(2.2f)
            .scaleY(2.2f)
            .setDuration(PULSE_MS)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void cancelHold() {
        if (mHoldRunnable != null) {
            removeCallbacks(mHoldRunnable);
            mHoldRunnable = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec);
        int cap = Math.round(dp(MAX_WIDTH_DP));
        if (available > 0) cap = Math.min(cap, Math.round(available * 0.82f));
        // Only when it actually changes: setMaxWidth requests a layout, and doing that from inside
        // a measure pass is the "requestLayout() improperly called during layout" warning.
        if (cap != mAppliedTextCapPx) {
            mAppliedTextCapPx = cap;
            mTitle.setMaxWidth(cap);
            mSub.setMaxWidth(cap);
        }
        // Measure at the natural height first: the container's own height is a fraction of that,
        // and the row has to keep its full size so the reveal wipes it in from the top rather than
        // squashing the text.
        super.onMeasure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        mNaturalHeightPx = getMeasuredHeight();
        setMeasuredDimension(getMeasuredWidth(),
            Math.max(0, Math.round(mNaturalHeightPx * mHeightFraction)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;
        float radius = Math.min(dp(CORNER_DP), height);
        mBounds.set(0f, -radius, width, height);

        mFillPath.reset();
        mFillPath.addRoundRect(mBounds,
            new float[] {0f, 0f, 0f, 0f, radius, radius, radius, radius}, Path.Direction.CW);
        canvas.drawPath(mFillPath, mFillPaint);

        // Leading edge and bottom only. The top edge is the bar the chip pulled out of and the
        // trailing edge is the screen edge, so drawing either would outline a card that is not there.
        float inset = mEdgePaint.getStrokeWidth() * 0.5f;
        mEdgePath.reset();
        mEdgePath.moveTo(inset, 0f);
        mEdgePath.lineTo(inset, height - radius - inset);
        mEdgePath.quadTo(inset, height - inset, inset + radius, height - inset);
        mEdgePath.lineTo(width - radius - inset, height - inset);
        mEdgePath.quadTo(width - inset, height - inset, width - inset, height - radius - inset);
        canvas.drawPath(mEdgePath, mEdgePaint);
        super.onDraw(canvas);
    }

    private void notifyOccupancy() {
        if (mOccupancyListener == null) return;
        int height = getVisibility() == VISIBLE ? getMeasuredHeight() : 0;
        if (height == mReportedHeightPx) return;
        mReportedHeightPx = height;
        mOccupancyListener.onNoticeOccupancyChanged(height);
    }

    /**
     * Where the chip sits in its host: hard against the top-trailing corner, with no top margin —
     * it is meant to look continuous with whatever chrome is above it.
     */
    @NonNull
    public static FrameLayout.LayoutParams buildHostLayoutParams(@NonNull Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.END);
        float density = context.getResources().getDisplayMetrics().density;
        params.setMarginEnd(Math.round(8 * density));
        return params;
    }

    private static Interpolator interpolator(float x1, float y1, float x2, float y2,
                                             boolean decelerateFallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new PathInterpolator(x1, y1, x2, y2);
        }
        return decelerateFallback ? new DecelerateInterpolator(1.8f) : new DecelerateInterpolator();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
