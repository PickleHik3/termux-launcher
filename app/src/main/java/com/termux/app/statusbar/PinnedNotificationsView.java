package com.termux.app.statusbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders the pinned essential notifications inside the widget slot. One match keeps a full card to
 * itself; from two on, the slot holds two cards at a time and a vertical swipe on them moves
 * through the rest a card at a time, with a thin line down the right edge showing where in the run
 * the two on screen sit.
 *
 * <p>The swipe belongs to the cards alone. {@link #canScrollVertically(int)} is what tells the
 * status bar so: the bar's own fold reads it at DOWN ({@code isInsideFoldAxisOwner}) and stands
 * down for a finger that starts on a scrollable card, while a finger anywhere else on the bar
 * still folds it. Once a drag is under way the parent chain is asked not to intercept as well.
 */
public final class PinnedNotificationsView extends View {

    public interface DismissListener {
        void onDismissPinned(@NonNull PinnedNotification notification);
    }

    /** A tap anywhere on a pin other than its dismiss control. */
    public interface OpenListener {
        void onOpenPinned(@NonNull PinnedNotification notification);
    }

    /** Card height for the contention layout, where the media strip takes the rest of the slot. */
    public static final float CONTENTION_CARD_HEIGHT_DP = 40f;
    private static final float CARD_GAP_DP = 2f;
    /** The run kept clear of the cards for the scroll line, so the dismiss control never meets it. */
    private static final float SCROLL_LINE_GUTTER_DP = 6f;
    private static final float SCROLL_LINE_WIDTH_DP = 2f;
    private static final float SCROLL_LINE_INSET_DP = 3f;
    private static final float SCROLL_THUMB_MIN_DP = 8f;
    /** How far a drag must travel before it counts as asking for the next card. */
    @VisibleForTesting static final float ADVANCE_FRACTION = .25f;
    private static final long SETTLE_MS = 190L;
    private static final Interpolator INTERPOLATOR = new PathInterpolator(.16f, 1f, .3f, 1f);

    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    /** Where each pin's dismiss control was drawn, parallel to {@link #mItems}; empty when off screen. */
    private final List<Rect> mDismissRects = new ArrayList<>();
    /** Where each pin was drawn, parallel to {@link #mItems}, so a tap can find the one it hit. */
    private final List<Rect> mItemRects = new ArrayList<>();
    private final PinnedNotificationIconCache mIcons;
    private final int mTouchSlop;

    private List<PinnedNotification> mItems = Collections.emptyList();
    @Nullable private DismissListener mListener;
    @Nullable private OpenListener mOpenListener;
    private boolean mCompactCard;
    private int mPressedIndex = -1;
    private int mPressedOpenIndex = -1;

    /** How far the run of cards is scrolled, in px from the first card's top. */
    private float mScrollPx;
    private float mScrollTargetPx;
    private float mDownX;
    private float mDownY;
    private float mDownScrollPx;
    private boolean mDragging;
    @Nullable private ValueAnimator mSettle;

    private int mOnSurface;
    private int mOnSurfaceVariant;
    private int mTertiary;

    public PinnedNotificationsView(Context context) {
        this(context, null);
    }

    public PinnedNotificationsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(false);
        mIcons = new PinnedNotificationIconCache(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        resolveColors();
    }

    private void resolveColors() {
        Context context = getContext();
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mOnSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, mOnSurface);
        mTertiary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(context, R.color.termux_primary)));
    }

    public void setListener(@Nullable DismissListener listener) {
        mListener = listener;
    }

    public void setOpenListener(@Nullable OpenListener listener) {
        mOpenListener = listener;
    }

    /**
     * Every match is kept: two are on screen and the rest are a swipe away. A dismissal shortens
     * the run, so the offset is clamped back into range here rather than left pointing past the end.
     */
    public void setItems(@NonNull List<PinnedNotification> items) {
        // A refresh that changes only a card's text must not stop a settling swipe under the
        // finger, so the offset and the animation are only disturbed when the run itself moved.
        boolean sameRun = sameKeys(mItems, items);
        mItems = items;
        mPressedIndex = -1;
        mPressedOpenIndex = -1;
        if (!sameRun) {
            mDragging = false;
            cancelSettle();
            setScrollPx(mScrollPx);
            updateContentDescription();
        }
        invalidate();
    }

    private static boolean sameKeys(@NonNull List<PinnedNotification> a,
                                    @NonNull List<PinnedNotification> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).key.equals(b.get(i).key)) return false;
        }
        return true;
    }

    @NonNull
    public List<PinnedNotification> getItems() {
        return mItems;
    }

    /** The contention layout gives the card 40dp, so its body drops to a single line. */
    public void setCompactCard(boolean compact) {
        if (mCompactCard == compact) return;
        mCompactCard = compact;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cancelSettle();
        setScrollPx(mScrollPx);
        updateContentDescription();
    }

    // ---- Scrolling ---------------------------------------------------------

    /** One card plus the gap beneath it: what a single swipe moves the run by. */
    @VisibleForTesting
    static float stepPx(int count, float height, float gap) {
        return cardHeightPx(count, height, gap) + gap;
    }

    @VisibleForTesting
    static float cardHeightPx(int count, float height, float gap) {
        if (count <= 1) return Math.max(0f, height);
        return Math.max(0f, (height - gap) / 2f);
    }

    /** How far the run can travel: zero while everything matched is already on screen. */
    @VisibleForTesting
    static float maxScrollPx(int count, float height, float gap) {
        if (count <= TopPaneSlotMode.VISIBLE_PINNED || height <= 0f) return 0f;
        return Math.max(0f, (count - TopPaneSlotMode.VISIBLE_PINNED) * stepPx(count, height, gap));
    }

    /**
     * Where a released drag lands. A short flick takes the next card along, anything further snaps
     * to whichever card boundary it ended nearest — the cards never rest half shown.
     */
    @VisibleForTesting
    static float snapTargetPx(float fromPx, float currentPx, float stepPx, float maxPx) {
        if (stepPx <= 0f || maxPx <= 0f) return 0f;
        int from = Math.round(fromPx / stepPx);
        int target = Math.round(currentPx / stepPx);
        float travel = currentPx - fromPx;
        if (target == from && Math.abs(travel) >= stepPx * ADVANCE_FRACTION) {
            target += travel > 0f ? 1 : -1;
        }
        return Math.max(0f, Math.min(maxPx, target * stepPx));
    }

    private float gapPx() {
        return dp(CARD_GAP_DP);
    }

    private float stepPx() {
        return stepPx(mItems.size(), getHeight(), gapPx());
    }

    private float maxScrollPx() {
        return maxScrollPx(mItems.size(), getHeight(), gapPx());
    }

    @VisibleForTesting
    float scrollPx() {
        return mScrollPx;
    }

    /** Where a settling animation is headed, which is also where the cards already are at rest. */
    @VisibleForTesting
    float scrollTargetPx() {
        return mScrollTargetPx;
    }

    /** The first of the two cards on screen. */
    @VisibleForTesting
    int firstVisibleIndex() {
        float step = stepPx();
        if (step <= 0f) return 0;
        int index = Math.round(mScrollPx / step);
        return Math.max(0, Math.min(Math.max(0, mItems.size() - TopPaneSlotMode.VISIBLE_PINNED), index));
    }

    private void setScrollPx(float value) {
        float clamped = Math.max(0f, Math.min(maxScrollPx(), value));
        mScrollTargetPx = clamped;
        if (Math.abs(mScrollPx - clamped) < .01f) {
            mScrollPx = clamped;
            return;
        }
        int before = firstVisibleIndex();
        mScrollPx = clamped;
        if (firstVisibleIndex() != before) updateContentDescription();
        invalidate();
    }

    private void cancelSettle() {
        if (mSettle == null) return;
        mSettle.cancel();
        mSettle = null;
    }

    private void animateScrollTo(float target) {
        cancelSettle();
        float clamped = Math.max(0f, Math.min(maxScrollPx(), target));
        mScrollTargetPx = clamped;
        if (Math.abs(clamped - mScrollPx) < .5f) {
            setScrollPx(clamped);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mScrollPx, clamped);
        animator.setDuration(SETTLE_MS);
        animator.setInterpolator(INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            int before = firstVisibleIndex();
            mScrollPx = (Float) animation.getAnimatedValue();
            if (firstVisibleIndex() != before) updateContentDescription();
            invalidate();
        });
        mSettle = animator;
        animator.start();
    }

    /**
     * The bar's fold reads this at DOWN and stands down where it answers: a swipe that starts on a
     * scrollable card is the cards' own, everything else on the bar still folds it.
     */
    @Override
    public boolean canScrollVertically(int direction) {
        float max = maxScrollPx();
        if (max <= 0f) return false;
        return direction < 0 ? mScrollPx > 0f : mScrollPx < max;
    }

    // ---- Drawing ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        resetHitRects();
        if (mItems.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        float cardRight = cardRightPx();
        if (mItems.size() == 1) {
            drawCard(canvas, mItems.get(0), 0, 0f, getHeight(), mCompactCard, cardRight);
        } else {
            float gap = gapPx();
            float cardHeight = cardHeightPx(mItems.size(), getHeight(), gap);
            float step = cardHeight + gap;
            for (int i = 0; i < mItems.size(); i++) {
                float top = i * step - mScrollPx;
                if (top >= getHeight() || top + cardHeight <= 0f) continue;
                drawCard(canvas, mItems.get(i), i, top, cardHeight, true, cardRight);
            }
        }
        drawScrollLine(canvas);
        canvas.restore();
    }

    private void resetHitRects() {
        while (mItemRects.size() < mItems.size()) mItemRects.add(new Rect());
        while (mDismissRects.size() < mItems.size()) mDismissRects.add(new Rect());
        while (mItemRects.size() > mItems.size()) mItemRects.remove(mItemRects.size() - 1);
        while (mDismissRects.size() > mItems.size()) mDismissRects.remove(mDismissRects.size() - 1);
        for (Rect rect : mItemRects) rect.setEmpty();
        for (Rect rect : mDismissRects) rect.setEmpty();
    }

    /** Where the cards end: short of the scroll line whenever one is shown. */
    private float cardRightPx() {
        return getWidth() - (showsScrollLine() ? dp(SCROLL_LINE_GUTTER_DP) : 0f);
    }

    private boolean showsScrollLine() {
        return mItems.size() > TopPaneSlotMode.VISIBLE_PINNED && getWidth() > 0 && getHeight() > 0;
    }

    /** The scroll line's track, or {@code null} while everything matched is on screen. */
    @Nullable
    @VisibleForTesting
    RectF scrollLineTrack() {
        if (!showsScrollLine()) return null;
        float width = dp(SCROLL_LINE_WIDTH_DP);
        float right = getWidth() - dp(SCROLL_LINE_INSET_DP);
        float inset = dp(SCROLL_LINE_INSET_DP);
        return new RectF(right - width, inset, right, getHeight() - inset);
    }

    /** The lit run of the scroll line: as long as the share on screen, as far down as the offset. */
    @Nullable
    @VisibleForTesting
    RectF scrollLineThumb() {
        RectF track = scrollLineTrack();
        if (track == null) return null;
        float trackLength = track.height();
        if (trackLength <= 0f) return null;
        float share = (float) TopPaneSlotMode.VISIBLE_PINNED / mItems.size();
        float thumb = Math.min(trackLength, Math.max(dp(SCROLL_THUMB_MIN_DP), trackLength * share));
        float max = maxScrollPx();
        float fraction = max <= 0f ? 0f : Math.max(0f, Math.min(1f, mScrollPx / max));
        float top = track.top + fraction * (trackLength - thumb);
        return new RectF(track.left, top, track.right, top + thumb);
    }

    private void drawScrollLine(Canvas canvas) {
        RectF track = scrollLineTrack();
        RectF thumb = scrollLineThumb();
        if (track == null || thumb == null) return;
        float radius = track.width() / 2f;
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mTertiary, 38));
        canvas.drawRoundRect(track, radius, radius, mFillPaint);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mTertiary, 140));
        canvas.drawRoundRect(thumb, radius, radius, mFillPaint);
    }

    private void drawCard(Canvas canvas, @NonNull PinnedNotification item, int index, float top,
                          float height, boolean singleLineBody, float cardRight) {
        mRect.set(0f, top, cardRight, top + height);
        recordItemRect(index, mRect);
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mTertiary,
            mPressedOpenIndex == index ? 46 : 26));
        canvas.drawRoundRect(mRect, dp(8f), dp(8f), mFillPaint);
        mFillPaint.setStyle(Paint.Style.STROKE);
        mFillPaint.setStrokeWidth(dp(1f));
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mTertiary, 66));
        mRect.inset(dp(.5f), dp(.5f));
        canvas.drawRoundRect(mRect, dp(8f), dp(8f), mFillPaint);
        mFillPaint.setStyle(Paint.Style.FILL);
        float paddingStart = dp(6f);
        float paddingTop = dp(5f);
        float icon = dp(18f);
        float dismiss = dp(20f);
        Rect dismissRect = new Rect(Math.round(cardRight - dp(5f) - dismiss),
            Math.round(top + (height - dismiss) / 2f),
            Math.round(cardRight - dp(5f)), Math.round(top + (height + dismiss) / 2f));
        drawDismiss(canvas, dismissRect, index, dp(10f));
        float textLeft = paddingStart + icon + dp(6f);
        float textWidth = dismissRect.left - dp(4f) - textLeft;
        if (textWidth <= dp(16f)) {
            drawAppIcon(canvas, item.packageName, paddingStart, top + (height - icon) / 2f, icon, dp(5f));
            return;
        }
        // Measure the text block first, then seat icon and text together in the middle of the
        // card: a card taller than its two lines used to hang them from the top edge and leave
        // an empty band beneath.
        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setTextSize(sp(10f));
        float titleHeight = mTextPaint.descent() - mTextPaint.ascent();
        float titleAscent = mTextPaint.ascent();
        CharSequence title = TextUtils.ellipsize(item.title(), mTextPaint, textWidth,
            TextUtils.TruncateAt.END);
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(9f));
        float bodyGap = dp(2f);
        float bodyRoom = height - 2f * paddingTop - titleHeight - bodyGap;
        StaticLayout layout = null;
        if (bodyRoom >= -mTextPaint.ascent()) {
            int maxLines = singleLineBody ? 1 : 2;
            layout = StaticLayout.Builder
                .obtain(item.body, 0, item.body.length(), mTextPaint, Math.round(textWidth))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build();
            if (layout.getHeight() > bodyRoom) layout = null;
        }
        float block = titleHeight + (layout == null ? 0f : bodyGap + layout.getHeight());
        float blockTop = top + Math.max(paddingTop, (height - Math.max(block, icon)) / 2f);
        float iconTop = block >= icon ? blockTop : top + (height - icon) / 2f;
        drawAppIcon(canvas, item.packageName, paddingStart, iconTop, icon, dp(5f));
        float textTop = block >= icon ? blockTop : top + (height - block) / 2f;
        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setTextSize(sp(10f));
        mTextPaint.setColor(mOnSurface);
        mTextPaint.setAlpha(255);
        float titleBaseline = textTop - titleAscent;
        canvas.drawText(title, 0, title.length(), textLeft, titleBaseline, mTextPaint);
        if (layout == null) return;
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(9f));
        mTextPaint.setColor(mOnSurfaceVariant);
        mTextPaint.setAlpha(230);
        canvas.save();
        canvas.translate(textLeft, textTop + titleHeight + bodyGap);
        layout.draw(canvas);
        canvas.restore();
    }

    private void drawAppIcon(Canvas canvas, String packageName, float left, float top, float size,
                             float radius) {
        Drawable icon = mIcons.get(packageName);
        if (icon == null) {
            mRect.set(left, top, left + size, top + size);
            mFillPaint.setShader(null);
            mFillPaint.setStyle(Paint.Style.FILL);
            mFillPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, 26));
            canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
            return;
        }
        canvas.save();
        mRect.set(left, top, left + size, top + size);
        canvas.clipRect(mRect);
        icon.setBounds(Math.round(left), Math.round(top), Math.round(left + size),
            Math.round(top + size));
        icon.draw(canvas);
        canvas.restore();
    }

    private void drawDismiss(Canvas canvas, Rect box, int index, float glyph) {
        recordDismissRect(index, box);
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface,
            mPressedIndex == index ? 41 : 20));
        canvas.drawCircle(box.centerX(), box.centerY(), box.width() / 2f, mFillPaint);
        Drawable cross = AppCompatResources.getDrawable(getContext(),
            R.drawable.ic_pinned_notification_dismiss);
        if (cross == null) return;
        cross = cross.mutate();
        cross.setTint(mOnSurface);
        cross.setAlpha(191);
        int half = Math.round(glyph / 2f);
        cross.setBounds(box.centerX() - half, box.centerY() - half, box.centerX() + half,
            box.centerY() + half);
        cross.draw(canvas);
    }

    // ---- Touch ------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mItems.isEmpty()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSettle();
                mDownX = event.getX();
                mDownY = event.getY();
                mDownScrollPx = mScrollPx;
                mDragging = false;
                mPressedIndex = hitDismiss(event.getX(), event.getY());
                // The dismiss control wins the overlap; the rest of the pin opens it.
                mPressedOpenIndex = mPressedIndex >= 0
                    ? -1 : hitItem(event.getX(), event.getY());
                if (mPressedIndex < 0 && mPressedOpenIndex < 0 && maxScrollPx() <= 0f) return false;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                if (!mDragging && maxScrollPx() > 0f && Math.abs(dy) > mTouchSlop
                    && Math.abs(dy) > Math.abs(dx)) {
                    // A vertical drag on the cards is theirs from here: the press states drop and
                    // the parent chain is told to keep its hands off the rest of the stream.
                    mDragging = true;
                    mPressedIndex = -1;
                    mPressedOpenIndex = -1;
                    claimGestureFromParent();
                }
                if (mDragging) {
                    setScrollPx(mDownScrollPx - dy);
                    return true;
                }
                if (mPressedOpenIndex >= 0
                    && hitItem(event.getX(), event.getY()) != mPressedOpenIndex) {
                    // A finger that slid off the pin is a scroll or a swipe, not a tap on it.
                    mPressedOpenIndex = -1;
                    invalidate();
                }
                return mPressedIndex >= 0 || mPressedOpenIndex >= 0;
            }
            case MotionEvent.ACTION_UP: {
                if (mDragging) {
                    mDragging = false;
                    settle();
                    return true;
                }
                int dismissIndex = mPressedIndex;
                int openIndex = mPressedOpenIndex;
                mPressedIndex = -1;
                mPressedOpenIndex = -1;
                invalidate();
                if (dismissIndex >= 0) {
                    if (hitDismiss(event.getX(), event.getY()) != dismissIndex) return true;
                    if (mListener != null && dismissIndex < mItems.size()) {
                        mListener.onDismissPinned(mItems.get(dismissIndex));
                    }
                    return true;
                }
                if (openIndex < 0 || hitItem(event.getX(), event.getY()) != openIndex) return true;
                if (mOpenListener != null && openIndex < mItems.size()) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    mOpenListener.onOpenPinned(mItems.get(openIndex));
                }
                return true;
            }
            default:
                mPressedIndex = -1;
                mPressedOpenIndex = -1;
                if (mDragging) {
                    mDragging = false;
                    settle();
                    invalidate();
                    return true;
                }
                invalidate();
                return false;
        }
    }

    private void settle() {
        animateScrollTo(snapTargetPx(mDownScrollPx, mScrollPx, stepPx(), maxScrollPx()));
    }

    private void claimGestureFromParent() {
        ViewParent parent = getParent();
        // One call is enough: a ViewGroup passes the flag up its own parent chain.
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    private void recordItemRect(int index, @NonNull RectF bounds) {
        while (mItemRects.size() <= index) mItemRects.add(new Rect());
        bounds.round(mItemRects.get(index));
    }

    private void recordDismissRect(int index, @NonNull Rect bounds) {
        while (mDismissRects.size() <= index) mDismissRects.add(new Rect());
        mDismissRects.get(index).set(bounds);
    }

    /** Which pin contains the point, or -1. Exact bounds: the pins tile, so nothing may grow. */
    private int hitItem(float x, float y) {
        for (int i = 0; i < mItemRects.size(); i++) {
            Rect rect = mItemRects.get(i);
            if (rect.isEmpty()) continue;
            if (rect.contains(Math.round(x), Math.round(y))) return i;
        }
        return -1;
    }

    /** The visual glyphs are 20dp; hit rects grow to 40dp, nearest center wins on overlap. */
    private int hitDismiss(float x, float y) {
        float minimum = dp(40f);
        int nearest = -1;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < mDismissRects.size(); i++) {
            Rect rect = mDismissRects.get(i);
            // A pin scrolled off screen drew nothing, so it owns no hit area at all.
            if (rect.isEmpty()) continue;
            float growX = Math.max(0f, (minimum - rect.width()) / 2f);
            float growY = Math.max(0f, (minimum - rect.height()) / 2f);
            if (x < rect.left - growX || x > rect.right + growX
                || y < rect.top - growY || y > rect.bottom + growY) continue;
            float dx = x - rect.exactCenterX();
            float dy = y - rect.exactCenterY();
            float distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    // ---- Accessibility ----------------------------------------------------

    private void updateContentDescription() {
        if (mItems.size() <= TopPaneSlotMode.VISIBLE_PINNED) {
            setContentDescription(getResources()
                .getString(R.string.termux_top_pane_pinned_notifications_content_description));
            return;
        }
        int first = firstVisibleIndex();
        setContentDescription(getResources().getString(
            R.string.termux_top_pane_pinned_notifications_scroll_content_description,
            first + 1, first + TopPaneSlotMode.VISIBLE_PINNED, mItems.size()));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        boolean scrollable = maxScrollPx() > 0f;
        info.setScrollable(scrollable);
        if (!scrollable) return;
        if (canScrollVertically(1)) info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        if (canScrollVertically(-1)) info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable android.os.Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && canScrollVertically(1)) {
            animateScrollTo(mScrollTargetPx + stepPx());
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && canScrollVertically(-1)) {
            animateScrollTo(mScrollTargetPx - stepPx());
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private static Typeface mediumTypeface() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
