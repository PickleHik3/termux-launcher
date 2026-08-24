package com.termux.app.notice;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.termux.R;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The launcher's in-app notice surface: a Material pill that appears at the top of the screen,
 * centred, holds one message, and fades away.
 *
 * <p>Replaces the stock Android {@code Toast} everywhere in the app. A toast is bottom-centre, sits
 * over the shell prompt and the keyboard, cannot be themed and cannot be positioned at all from
 * Android 11 onward. The pill sits in the row just under whatever chrome the screen has
 * ({@link AppNoticePlacement} keeps it there) where nothing else competes for space.
 *
 * <p>Nothing here animates by drawing. The pill is a {@link MaterialShapeDrawable} background — one
 * display list, recorded once — and appearing or leaving is alpha and translation on the view
 * itself, which the render thread owns. Earlier versions animated a height fraction through
 * {@code requestLayout()} (a layout pass of the whole activity, per frame), then a draw-time clip
 * with a per-frame {@code invalidate()} and outline update, and drew a hold countdown that
 * re-recorded the pill sixty times a second for up to nine seconds. All of that is gone: between
 * appearing and leaving, a notice costs nothing per frame.
 *
 * <p>Anything raised while a message is up is queued (most recent four) and the pill shows a
 * {@code +N} counter, so a loop that raises twenty notices still resolves in bounded time.
 */
public final class AppNoticeHostView extends LinearLayout {

    /** Design durations, in ms. */
    private static final long IN_MS = 200L;
    private static final long OUT_MS = 160L;
    private static final long SWAP_OUT_MS = 120L;

    /** Hold times, mapped from the {@code Toast.LENGTH_*} the call sites used to pass. */
    public static final long HOLD_SHORT_MS = 2600L;
    public static final long HOLD_LONG_MS = 3800L;
    /**
     * For a notice whose tap is the only way back — a bulk write with an Undo. A confirmation is
     * gone in a few seconds, which is less time than the surfaces take to finish re-rendering, let
     * alone than deciding the old look was better.
     */
    public static final long HOLD_UNDO_MS = 9000L;

    /** Beyond this the oldest queued notices are dropped — a burst must still drain. */
    private static final int MAX_QUEUED = 4;

    private static final float MIN_HEIGHT_DP = 36f;
    private static final float MAX_WIDTH_DP = 300f;
    /** How far above its resting place the pill starts, so it drops in rather than blinking on. */
    private static final float RISE_DP = 8f;

    /**
     * How much vertical room the pill is taking at the top of the screen, so the background-process
     * stack below it can follow it down and back up rather than reserve a permanent gap.
     */
    public interface OccupancyListener {
        void onNoticeOccupancyChanged(int heightPx);
    }

    private final Deque<AppNoticeItem> mQueue = new ArrayDeque<>();

    private final AppCompatTextView mGlyph;
    private final AppCompatTextView mTitle;
    private final AppCompatTextView mSub;
    private final AppCompatTextView mCount;

    private final Interpolator mInInterpolator;
    private final Interpolator mOutInterpolator;

    private final int mAccentInfo;
    private final int mAccentError;
    private final int mAccentAttention;

    @Nullable private OccupancyListener mOccupancyListener;
    private int mReportedHeightPx = -1;

    /** Recomputes where the pill sits; run in the frame before one becomes visible. */
    @Nullable private Runnable mPlacementRefresh;

    @Nullable private Runnable mHoldRunnable;
    @Nullable private AppNoticeItem mActive;
    private int mNaturalHeightPx;
    /** Last text cap pushed into the labels, so measuring never re-triggers their layout. */
    private int mAppliedTextCapPx = -1;

    private final int mTouchSlopPx;
    private final int mSwipeDismissDistancePx;
    @Nullable private VelocityTracker mVelocityTracker;
    private float mTouchDownX;
    private float mTouchDownY;
    private boolean mSwiping;
    private boolean mSwipeDismissed;

    public AppNoticeHostView(@NonNull Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setFocusable(false);
        setVisibility(GONE);
        setMinimumHeight(Math.round(dp(MIN_HEIGHT_DP)));
        setPadding(Math.round(dp(14f)), Math.round(dp(8f)),
            Math.round(dp(14f)), Math.round(dp(8f)));

        mInInterpolator = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        mOutInterpolator = new PathInterpolator(0.4f, 0f, 1f, 1f);

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
        mAccentAttention = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary,
            MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSecondary, mAccentError));

        // Fully rounded whatever the pill's height turns out to be — a message that wraps to two
        // lines is still a pill and not a rounded rectangle. The drawable supplies the view's
        // outline too, so the shadow follows the shape without anything being recomputed per frame.
        MaterialShapeDrawable pill = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
            .setAllCornerSizes(new RelativeCornerSize(0.5f))
            .build());
        pill.setFillColor(ColorStateList.valueOf(surface));
        pill.setStroke(Math.max(1f, dp(1f) * 0.9f), ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(outline, 128)));
        setBackground(pill);
        setElevation(dp(3f));

        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mSwipeDismissDistancePx = Math.round(dp(56f));

        mGlyph = new AppCompatTextView(context);
        mGlyph.setGravity(Gravity.CENTER);
        mGlyph.setIncludeFontPadding(false);
        mGlyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mGlyph.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glyphParams.setMarginEnd(Math.round(dp(9f)));
        addView(mGlyph, glyphParams);

        mTitle = new AppCompatTextView(context);
        mTitle.setIncludeFontPadding(false);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mTitle.setTextColor(onSurface);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        addView(mTitle, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mSub = new AppCompatTextView(context);
        mSub.setIncludeFontPadding(false);
        mSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mSub.setTextColor(ColorUtils.setAlphaComponent(onSurface, 122));
        mSub.setSingleLine(true);
        mSub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.setMarginStart(Math.round(dp(8f)));
        addView(mSub, subParams);

        mCount = new AppCompatTextView(context);
        mCount.setIncludeFontPadding(false);
        mCount.setGravity(Gravity.CENTER);
        mCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        mCount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mCount.setPadding(Math.round(dp(5f)), Math.round(dp(1f)),
            Math.round(dp(5f)), Math.round(dp(1f)));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.setMarginStart(Math.round(dp(8f)));
        addView(mCount, countParams);
    }

    public void setOccupancyListener(@Nullable OccupancyListener listener) {
        mOccupancyListener = listener;
        mReportedHeightPx = -1;
        notifyOccupancy();
    }

    /** Set by {@link AppNoticePlacement}: recompute the offset before a pill is seen. */
    void setPlacementRefresh(@Nullable Runnable refresh) {
        mPlacementRefresh = refresh;
    }

    /** Queue a notice, showing it immediately when the pill is idle. */
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
        animate().cancel();
        mActive = null;
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
        setVisibility(GONE);
        notifyOccupancy();
    }

    private void showNext() {
        AppNoticeItem item = mQueue.pollFirst();
        if (item == null) {
            hide();
            return;
        }
        mActive = item;
        bind(item);
        // Where the chrome's bottom edge is, as of this frame — not as of whenever the host was
        // attached, which may have been before that bar was ever laid out.
        if (mPlacementRefresh != null) mPlacementRefresh.run();
        // Above anything added to the content root after the pill was: an onboarding sheet, a
        // transition overlay. A notice nobody can see is worse than no notice.
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent != null && parent.getChildAt(parent.getChildCount() - 1) != this) bringToFront();
        setVisibility(VISIBLE);
        appear();
        cancelHold();
        mHoldRunnable = this::leaveAndAdvance;
        postDelayed(mHoldRunnable, item.durationMs);
        notifyOccupancy();
    }

    /**
     * The accent a notice is drawn in. Attention outranks severity: a shell that has rung its bell
     * in a window the user is not looking at is the one thing on this surface that is waiting for
     * them, and it has to be tellable apart from the run of confirmations at a glance.
     */
    private int accentFor(@NonNull AppNoticeItem item) {
        if (item.attention) return mAccentAttention;
        return item.kind == AppNoticeItem.Kind.ERROR || item.kind == AppNoticeItem.Kind.WARNING
            ? mAccentError : mAccentInfo;
    }

    private void bind(@NonNull AppNoticeItem item) {
        int accent = accentFor(item);

        // A mark only where it means something: a confirmation, a warning, a failure, an undo. The
        // plain-message case — most of the app's notices — is just the sentence, and a decorative
        // chevron in front of it made a simple pill look like a widget.
        boolean marked = item.attention || item.kind != AppNoticeItem.Kind.INFO
            || !TextUtils.isEmpty(item.glyph);
        mGlyph.setVisibility(marked ? VISIBLE : GONE);
        if (marked) {
            // Spanned so a nerd-symbol glyph (the attention bell) renders from the bundled symbol
            // face; plain glyphs pass through untouched.
            mGlyph.setText(com.termux.shared.termux.font.NerdFontSpans.span(
                getContext(), item.resolvedGlyph()));
            mGlyph.setTextColor(accent);
        }

        mTitle.setText(item.title);
        boolean hasSub = !TextUtils.isEmpty(item.sub);
        mSub.setText(hasSub ? item.sub : "");
        mSub.setVisibility(hasSub ? VISIBLE : GONE);
        // A bare message has the whole pill to itself and may wrap; paired with a subtitle the two
        // share one line and the title is the half that must stay readable, so it holds its width
        // and the subtitle is what gets clipped.
        mTitle.setSingleLine(hasSub);
        mTitle.setMaxLines(hasSub ? 1 : 2);

        // A notice you can act on says so: the pointer cursor equivalent here is the tap target
        // being announced, since the pill looks identical either way.
        setContentDescription(item.onActivate == null ? item.title
            : item.title + " — " + (TextUtils.isEmpty(item.actionHint)
                ? getContext().getString(R.string.notice_tap_to_open) : item.actionHint));
        updateCount();
    }

    private void updateCount() {
        int queued = mQueue.size();
        if (queued <= 0) {
            mCount.setVisibility(GONE);
            return;
        }
        int accent = mActive == null ? mAccentInfo : accentFor(mActive);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(7f));
        pill.setColor(ColorUtils.setAlphaComponent(accent, 51));
        mCount.setBackground(pill);
        mCount.setTextColor(accent);
        mCount.setText("+" + queued);
        mCount.setVisibility(VISIBLE);
    }

    /** Drops in from just above its resting place. One property animation, no drawing of our own. */
    private void appear() {
        animate().cancel();
        setTranslationX(0f);
        setAlpha(0f);
        setTranslationY(-dp(RISE_DP));
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(IN_MS)
            .setInterpolator(mInInterpolator)
            .withLayer()
            .start();
    }

    /** Fades the current message out, then shows the next one or leaves the corner empty. */
    private void leaveAndAdvance() {
        mHoldRunnable = null;
        if (mActive == null) return;
        boolean more = !mQueue.isEmpty();
        animate().cancel();
        animate()
            .alpha(0f)
            .translationY(-dp(RISE_DP * 0.5f))
            .setDuration(more ? SWAP_OUT_MS : OUT_MS)
            .setInterpolator(mOutInterpolator)
            .withLayer()
            .withEndAction(() -> {
                if (mQueue.isEmpty()) hide();
                else showNext();
            })
            .start();
    }

    private void hide() {
        mActive = null;
        cancelHold();
        setVisibility(GONE);
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
        notifyOccupancy();
    }

    /**
     * A tap takes the user to whatever the notice is about — the pane or window it came from — and
     * then gets out of the way. With nowhere to go, the tap is just an early dismiss.
     */
    private void activateOrDismiss() {
        AppNoticeItem active = mActive;
        if (active == null) return;
        cancelHold();
        Runnable action = active.onActivate;
        leaveAndAdvance();
        if (action != null) action.run();
    }

    /**
     * Swipe to dismiss, either way: the pill is centred, so neither direction is "off the edge it
     * came from". Interactive rather than only tap-and-wait because these are frequent and one may
     * well be covering the top of a shell's output at the moment the user wants to read it. Only
     * horizontal travel counts — a vertical drag here belongs to the status bar's own pull-down.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownX = event.getRawX();
                mTouchDownY = event.getRawY();
                mSwiping = false;
                mSwipeDismissed = false;
                if (mVelocityTracker != null) mVelocityTracker.recycle();
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(event);
                // The hold must not expire mid-drag and yank the pill out from under the finger.
                cancelHold();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
                float dx = event.getRawX() - mTouchDownX;
                float dy = event.getRawY() - mTouchDownY;
                if (!mSwiping && Math.abs(dx) > mTouchSlopPx && Math.abs(dx) > Math.abs(dy)) {
                    mSwiping = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (mSwiping) {
                    setTranslationX(dx);
                    setAlpha(Math.max(0.2f, 1f - Math.abs(dx) / (mSwipeDismissDistancePx * 2f)));
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float dx = event.getRawX() - mTouchDownX;
                if (mSwiping) {
                    float velocity = 0f;
                    if (mVelocityTracker != null) {
                        mVelocityTracker.addMovement(event);
                        mVelocityTracker.computeCurrentVelocity(1000);
                        velocity = mVelocityTracker.getXVelocity();
                    }
                    if (Math.abs(dx) > mSwipeDismissDistancePx
                        || Math.abs(velocity) > mSwipeDismissDistancePx * 8f) {
                        swipeOut(dx >= 0 ? 1f : -1f);
                    } else {
                        settleBack();
                    }
                } else if (Math.abs(dx) <= mTouchSlopPx) {
                    activateOrDismiss();
                } else {
                    settleBack();
                }
                releaseVelocityTracker();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (mSwiping) settleBack(); else resumeHold();
                releaseVelocityTracker();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /** Finish the throw, then drop the notice and whatever is behind it. */
    private void swipeOut(float direction) {
        if (mSwipeDismissed) return;
        mSwipeDismissed = true;
        animate().cancel();
        animate()
            .translationX(direction * (getWidth() + mSwipeDismissDistancePx))
            .alpha(0f)
            .setDuration(OUT_MS)
            .setInterpolator(mOutInterpolator)
            .withLayer()
            .withEndAction(() -> {
                // A swipe dismisses the whole burst, not just the message on top: the user has said
                // they are done with it, and popping the queue one swipe at a time would fight them.
                mQueue.clear();
                hide();
            })
            .start();
    }

    private void settleBack() {
        mSwiping = false;
        animate().cancel();
        animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(OUT_MS)
            .setInterpolator(mInInterpolator)
            .withLayer()
            .withEndAction(this::resumeHold)
            .start();
    }

    /** Restart the hold after a touch that did not dismiss. */
    private void resumeHold() {
        if (mActive == null) return;
        cancelHold();
        mHoldRunnable = this::leaveAndAdvance;
        postDelayed(mHoldRunnable, mActive.durationMs);
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
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mNaturalHeightPx = getMeasuredHeight();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // The height the column below has to make room for is known once, here, rather than being
        // recomputed on every animation frame: the consumer animates its own slide, and handing it
        // a new target sixty times a second only cancelled and restarted that animation.
        notifyOccupancy();
    }

    private void notifyOccupancy() {
        if (mOccupancyListener == null) return;
        int height = mActive != null && getVisibility() == VISIBLE ? mNaturalHeightPx : 0;
        if (height == mReportedHeightPx) return;
        mReportedHeightPx = height;
        mOccupancyListener.onNoticeOccupancyChanged(height);
    }

    /**
     * Where the pill sits in its host: centred at the top, in the row {@link AppNoticePlacement}
     * puts just under the screen's own chrome. Side margins so a long message on a narrow screen
     * still reads as a pill with air around it rather than a bar.
     */
    @NonNull
    public static FrameLayout.LayoutParams buildHostLayoutParams(@NonNull Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        float density = context.getResources().getDisplayMetrics().density;
        params.leftMargin = Math.round(16 * density);
        params.rightMargin = Math.round(16 * density);
        return params;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
