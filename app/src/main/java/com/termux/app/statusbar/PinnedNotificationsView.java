package com.termux.app.statusbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.IconColor;
import com.termux.app.chrome.OnGlass;
import com.termux.app.haptics.Haptics;
import com.termux.app.launcher.popup.AnchoredMenu;
import com.termux.app.launcher.popup.AnchoredMenuTheme;
import com.termux.app.launcher.popup.MenuRowFactory;
import com.termux.app.launcher.popup.MenuSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the pinned essential notifications inside the widget slot as tinted conversation cards.
 * One match keeps a full card to itself; from two on, the slot holds two cards at a time and a
 * vertical swipe on them moves through the rest a card at a time, with a thin line down the right
 * edge showing where in the run the two on screen sit.
 *
 * <h3>A card</h3>
 * <p>The sender's picture leads — the MessagingStyle person's icon or the notification's large
 * icon, a circle with the app's icon as a badge at its foot; with no picture, the app icon alone.
 * Beside it the sender in bold (the app's name only when the notification has no title), a count
 * chip when several messages are folded into the card, the age at the top right, and the message
 * beneath (on the same line, after a dot, in the two-card rows). The card is washed in the app
 * icon's own colour ({@link IconColor#tint}), a horizontal gradient over the glass with a hairline
 * of the same colour; a neutral icon falls back to the theme's tertiary. The inks are checked
 * against that tinted surface ({@link GlassInk#legible}).</p>
 *
 * <h3>Gestures</h3>
 * <p>A tap opens the notification. A sideways drag takes the card under the finger with it; past
 * {@link PinnedSwipe#COMMIT_FRACTION} of the width or on a fling it is dismissed, otherwise it
 * springs back. A dismissal is held for {@link #UNDO_MS}: the card's place shows "Dismissed ·
 * Undo", a tap there brings it back, and only when the time runs out (or another dismissal, or the
 * cards leaving the screen, forces it) is the listener told. A long press opens the launcher's
 * anchored glass menu with the whole message and Open, Dismiss and Mute this rule.</p>
 *
 * <p>The axis of a drag is decided once, at the touch slop ({@link PinnedSwipe#decide}). Sideways
 * is the card's and the parent chain is asked not to intercept, so neither the bar's fold nor the
 * wall takes it. Up or down belongs to the cards only while there is a run to scroll, which
 * {@link #canScrollVertically(int)} tells the status bar: the bar's own fold reads it at DOWN
 * ({@code isInsideFoldAxisOwner}) and stands down for a finger that starts on a scrollable card,
 * while a finger anywhere else on the bar, or on cards with nothing to scroll, still folds it.</p>
 */
public final class PinnedNotificationsView extends View {

    public interface DismissListener {
        void onDismissPinned(@NonNull PinnedNotification notification);
    }

    /** A tap anywhere on a card. */
    public interface OpenListener {
        void onOpenPinned(@NonNull PinnedNotification notification);
    }

    /** "Mute this rule" on a card's long-press menu. */
    public interface MuteListener {
        void onMuteRule(@NonNull PinnedNotification notification);
    }

    /** Card height for the contention layout, where the media strip takes the rest of the slot. */
    public static final float CONTENTION_CARD_HEIGHT_DP = 40f;
    /** How long a dismissed card offers its undo before the dismissal is carried out. */
    public static final long UNDO_MS = 4000L;
    private static final float CARD_GAP_DP = 2f;
    /** The run kept clear of the cards for the scroll line. */
    private static final float SCROLL_LINE_GUTTER_DP = 6f;
    private static final float SCROLL_LINE_WIDTH_DP = 2f;
    private static final float SCROLL_LINE_INSET_DP = 3f;
    private static final float SCROLL_THUMB_MIN_DP = 8f;
    private static final float CARD_RADIUS_DP = 8f;
    private static final float PAD_START_DP = 6f;
    private static final float PAD_END_DP = 8f;
    private static final float AVATAR_DP = 26f;
    private static final float AVATAR_ROW_DP = 22f;
    private static final float BADGE_DP = 12f;
    private static final float BADGE_RING_DP = 1.75f;
    private static final float AVATAR_GAP_DP = 7f;
    private static final float TITLE_SP = 10f;
    private static final float BODY_SP = 9.5f;
    private static final float TIME_SP = 8.5f;
    private static final float CHIP_SP = 8f;
    /** Wash alphas: ~22% at the leading edge to ~8% at the trailing one, ~30% for the hairline. */
    private static final int WASH_START_ALPHA = 56;
    private static final int WASH_END_ALPHA = 20;
    private static final int STROKE_ALPHA = 77;
    /** What the wash averages to, for the ink check. */
    private static final int WASH_MEAN_ALPHA = 38;
    private static final int CHIP_ALPHA = 72;
    /** How far a drag must travel before it counts as asking for the next card. */
    @VisibleForTesting static final float ADVANCE_FRACTION = .25f;
    private static final long SETTLE_MS = 190L;
    private static final long SWIPE_SETTLE_MS = 180L;
    private static final long REVEAL_MS = 150L;
    private static final Interpolator INTERPOLATOR = new PathInterpolator(.16f, 1f, .3f, 1f);

    /**
     * Where a dismissal forced by the cards leaving the screen is carried out: a moment later, on
     * the main thread, rather than inside the visibility or detach pass that forced it, since the
     * dismissal changes the feed and the feed lays the slot out again.
     */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF mRect = new RectF();
    private final RectF mBounds = new RectF();
    private final Path mClip = new Path();
    private final PinnedNotificationIconCache mIcons;
    private final PinnedAvatarCache mAvatars;
    /** App-icon tint per package; see {@link IconColor#tint}. */
    private final Map<String, Integer> mTints = new HashMap<>();
    /** Inks resolved against a tint's surface: title, body, time, chip fill, chip text, ring. */
    private final Map<Integer, int[]> mInks = new HashMap<>();
    /** The card wash per tint, built once from 0 to 1 and stretched across each card it fills. */
    private final SparseArray<LinearGradient> mWashShaders = new SparseArray<>();
    private final Matrix mWashMatrix = new Matrix();
    /** Senders and bodies fitted to their line, kept while the text and its room hold. */
    private final FitMemo mFits = new FitMemo();
    private final FitMemo.Fitter mFitter =
        (text, room) -> TextUtils.ellipsize(text, mTextPaint, room, TextUtils.TruncateAt.END);
    /** The undo row's two inks, resolved for the surface and roles they were last asked on. */
    private int mUndoInkSurface;
    private int mUndoInkVariantSeed;
    private int mUndoInkTertiarySeed;
    private int mUndoInkVariant;
    private int mUndoInkTertiary;
    private boolean mUndoInksValid;
    private final int mTouchSlop;
    private final int mLongPressTimeout;
    private final float mMinFlingVelocity;
    private final float mMaxFlingVelocity;
    private final CardAccessibility mAccessibility;
    @Nullable private AnchoredMenu mMenu;

    private List<PinnedNotification> mItems = Collections.emptyList();
    @Nullable private DismissListener mListener;
    @Nullable private OpenListener mOpenListener;
    @Nullable private MuteListener mMuteListener;
    private boolean mCompactCard;
    private int mPressedOpenIndex = -1;

    /** How far the run of cards is scrolled, in px from the first card's top. */
    private float mScrollPx;
    private float mScrollTargetPx;
    private float mDownX;
    private float mDownY;
    private float mDownScrollPx;
    private int mTouchIndex = -1;
    private PinnedSwipe.Axis mAxis = PinnedSwipe.Axis.UNDECIDED;
    private boolean mDragging;
    private boolean mLongPressFired;
    @Nullable private VelocityTracker mVelocity;
    @Nullable private ValueAnimator mSettle;

    /** The card following the finger sideways, by conversation, and how far it has gone. */
    @Nullable private String mSwipeId;
    private float mSwipeOffsetPx;
    @Nullable private ValueAnimator mSwipeAnimator;

    /** The card dismissed but still offering its undo, or null. */
    @Nullable private PinnedNotification mPending;
    /** The row that just changed face (card to undo row or back), fading in. */
    @Nullable private String mRevealId;
    private float mRevealAlpha = 1f;
    @Nullable private ValueAnimator mRevealAnimator;

    private final Runnable mUndoTimeout = this::commitPendingDismiss;
    private final Runnable mLongPress = this::onLongPress;
    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            scheduleTick();
        }
    };

    private int mOnSurface;
    private int mOnSurfaceVariant;
    private int mTertiary;
    /** The STATUS_BAR band's measured surface; null until the bar has been measured. */
    @Nullable private Integer mBandSurface;
    private int mSurface;

    public PinnedNotificationsView(Context context) {
        this(context, null);
    }

    public PinnedNotificationsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        // Keyboard focus stays with the terminal; TalkBack reaches each card as a virtual view.
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setScreenReaderFocusable(true);
        mIcons = new PinnedNotificationIconCache(context);
        mAvatars = new PinnedAvatarCache(context);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        mMinFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaxFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        mAccessibility = new CardAccessibility(this);
        ViewCompat.setAccessibilityDelegate(this, mAccessibility);
        resolveColors();
    }

    private void resolveColors() {
        Context context = getContext();
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mOnSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, mOnSurface);
        mTertiary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorTertiary,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(context, R.color.termux_primary)));
        // What the cards' inks are measured against: the band the chrome resolved once it has,
        // the nominal panel colour only until then.
        mSurface = mBandSurface != null ? mBandSurface
            : MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
                ContextCompat.getColor(context, R.color.termux_surface_panel));
    }

    /**
     * The STATUS_BAR band's resolved surface (the activity's status-ink pass reads it once), so
     * the card inks are measured on what the cards really stand on.
     */
    public void setBandSurface(@androidx.annotation.ColorInt int bandSurface) {
        if (mBandSurface != null && mBandSurface == bandSurface) return;
        mBandSurface = bandSurface;
        resolveColors();
        mInks.clear();
        invalidate();
    }

    /** The scheme changed: resolve the roles again. */
    public void onThemeChanged() {
        mTints.clear();
        mWashShaders.clear();
        resolveColors();
        mInks.clear();
        invalidate();
    }

    public void setListener(@Nullable DismissListener listener) {
        mListener = listener;
    }

    public void setOpenListener(@Nullable OpenListener listener) {
        mOpenListener = listener;
    }

    public void setMuteListener(@Nullable MuteListener listener) {
        mMuteListener = listener;
    }

    /**
     * Every match is kept: two are on screen and the rest are a swipe away. A dismissal shortens
     * the run, so the offset is clamped back into range here rather than left pointing past the end.
     */
    public void setItems(@NonNull List<PinnedNotification> items) {
        // A refresh that changes only a card's text must not stop a settling swipe under the
        // finger, so the offset and the animation are only disturbed when the run itself moved.
        boolean sameRun = sameConversations(mItems, items);
        mItems = items;
        mPressedOpenIndex = -1;
        reconcilePending(items);
        if (mSwipeId != null && indexOf(mSwipeId) < 0) cancelSwipe();
        if (!sameRun) {
            mDragging = false;
            cancelSettle();
            setScrollPx(mScrollPx);
            updateContentDescription();
        }
        scheduleTick();
        mAccessibility.invalidateRoot();
        invalidate();
    }

    /**
     * The whole feed as it stands, told while the cards are leaving the slot: an undo whose card
     * is gone from it is dropped rather than carried out.
     */
    public void dropPendingMissingFrom(@NonNull List<PinnedNotification> feed) {
        reconcilePending(feed);
    }

    private void reconcilePending(@NonNull List<PinnedNotification> items) {
        PinnedNotification pending = mPending;
        if (pending == null) return;
        PinnedNotification current = find(items, pending.conversationId);
        if (current == null) {
            // Removed meanwhile (read elsewhere, cancelled by its app): nothing left to dismiss.
            removeCallbacks(mUndoTimeout);
            mPending = null;
        } else {
            // A new message folded in: the dismissal, when it comes, takes its keys too.
            mPending = current;
        }
    }

    private static boolean sameConversations(@NonNull List<PinnedNotification> a,
                                             @NonNull List<PinnedNotification> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).conversationId.equals(b.get(i).conversationId)) return false;
        }
        return true;
    }

    @Nullable
    private static PinnedNotification find(@NonNull List<PinnedNotification> items,
                                           @NonNull String conversationId) {
        for (PinnedNotification item : items) {
            if (item.conversationId.equals(conversationId)) return item;
        }
        return null;
    }

    private int indexOf(@NonNull String conversationId) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).conversationId.equals(conversationId)) return i;
        }
        return -1;
    }

    @NonNull
    public List<PinnedNotification> getItems() {
        return mItems;
    }

    /** The contention layout gives the card less room, so its body drops to a single line. */
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
        mAccessibility.invalidateRoot();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleTick();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mTick);
        removeCallbacks(mLongPress);
        forceCommitSoon();
        if (mMenu != null) mMenu.dismiss();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            scheduleTick();
            return;
        }
        // Off screen, an undo cannot be reached: the dismissal is carried out now.
        removeCallbacks(mTick);
        forceCommitSoon();
    }

    /** The undo can no longer be reached: carry the dismissal out, just after this pass. */
    private void forceCommitSoon() {
        PinnedNotification pending = mPending;
        if (pending == null) return;
        removeCallbacks(mUndoTimeout);
        MAIN.post(() -> {
            if (mPending == pending) commitPendingDismiss();
        });
    }

    // ---- Relative time ----------------------------------------------------

    /** Refresh the age labels when the soonest one next changes, never more than once a minute. */
    private void scheduleTick() {
        removeCallbacks(mTick);
        if (mItems.isEmpty() || !isAttachedToWindow()) return;
        long now = System.currentTimeMillis();
        long delay = Long.MAX_VALUE;
        for (PinnedNotification item : mItems) {
            delay = Math.min(delay, PinnedRelativeTime.msUntilChange(now, item.postTime));
        }
        if (delay != Long.MAX_VALUE) postDelayed(mTick, delay);
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
        if (firstVisibleIndex() != before) {
            updateContentDescription();
            mAccessibility.invalidateRoot();
        }
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
        if (Math.abs(clamped - mScrollPx) < .5f || reducedMotion()) {
            setScrollPx(clamped);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mScrollPx, clamped);
        animator.setDuration(SETTLE_MS);
        animator.setInterpolator(INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            int before = firstVisibleIndex();
            mScrollPx = (Float) animation.getAnimatedValue();
            if (firstVisibleIndex() != before) {
                updateContentDescription();
                mAccessibility.invalidateRoot();
            }
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

    /**
     * A sideways drag on a card is its dismissal, so any ancestor that pages sideways and asks its
     * children first (the fold of a bar standing in a column, a pager) leaves it to the card.
     */
    @Override
    public boolean canScrollHorizontally(int direction) {
        return !mItems.isEmpty();
    }

    // ---- Geometry ---------------------------------------------------------

    /** Where the cards end: short of the scroll line whenever one is shown. */
    private float cardRightPx() {
        return getWidth() - (showsScrollLine() ? dp(SCROLL_LINE_GUTTER_DP) : 0f);
    }

    /** Card {@code index}'s bounds at rest (no swipe offset); false when it is off screen. */
    private boolean cardBounds(int index, @NonNull RectF out) {
        if (index < 0 || index >= mItems.size() || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        float right = cardRightPx();
        if (mItems.size() == 1) {
            out.set(0f, 0f, right, getHeight());
            return true;
        }
        float gap = gapPx();
        float cardHeight = cardHeightPx(mItems.size(), getHeight(), gap);
        float top = index * (cardHeight + gap) - mScrollPx;
        if (top >= getHeight() || top + cardHeight <= 0f) return false;
        out.set(0f, top, right, top + cardHeight);
        return true;
    }

    /** Which card contains the point, or -1. Exact bounds: the cards tile, so nothing may grow. */
    private int hitItem(float x, float y) {
        RectF bounds = new RectF();
        for (int i = 0; i < mItems.size(); i++) {
            if (!cardBounds(i, bounds)) continue;
            if (x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom) {
                return i;
            }
        }
        return -1;
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

    // ---- Colour -----------------------------------------------------------

    /** The card colour for {@code packageName}: its icon's tint, or the theme's tertiary. */
    @VisibleForTesting
    int tintFor(@NonNull String packageName) {
        Integer cached = mTints.get(packageName);
        if (cached != null) return cached;
        int color = IconColor.tint(mIcons.get(packageName), mTertiary);
        mTints.put(packageName, color);
        return color;
    }

    /** Inks resolved against the glass washed in {@code tint}; see the constants for the slots. */
    @NonNull
    private int[] inksFor(int tint) {
        int[] cached = mInks.get(tint);
        if (cached != null) return cached;
        int surface = OnGlass.opaque(mSurface);
        int washed = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(tint, WASH_MEAN_ALPHA),
            surface);
        int chipFill = ColorUtils.setAlphaComponent(tint, CHIP_ALPHA);
        int chipSurface = ColorUtils.compositeColors(chipFill, washed);
        int[] inks = new int[] {
            GlassInk.legible(washed, mOnSurface, OnGlass.TARGET_BODY_TEXT),
            GlassInk.legible(washed, mOnSurfaceVariant, OnGlass.TARGET_BODY_TEXT, 230),
            GlassInk.legible(washed, mOnSurfaceVariant, OnGlass.TARGET_LARGE_TEXT, 200),
            chipFill,
            GlassInk.legible(chipSurface, mOnSurface, OnGlass.TARGET_BODY_TEXT),
            // The badge ring: the card's own colour, opaque, so it cuts the badge out of the
            // avatar the way a gap in the card would.
            ColorUtils.compositeColors(ColorUtils.setAlphaComponent(tint, WASH_START_ALPHA), surface),
        };
        mInks.put(tint, inks);
        return inks;
    }

    // ---- Drawing ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mItems.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        CardForm form = mItems.size() >= TopPaneSlotMode.VISIBLE_PINNED ? CardForm.ROW
            : mCompactCard ? CardForm.CONTENTION : CardForm.SINGLE;
        long now = System.currentTimeMillis();
        for (int i = 0; i < mItems.size(); i++) {
            if (!cardBounds(i, mBounds)) continue;
            PinnedNotification item = mItems.get(i);
            float alpha = item.conversationId.equals(mRevealId) ? mRevealAlpha : 1f;
            if (mPending != null && mPending.conversationId.equals(item.conversationId)) {
                drawUndoRow(canvas, mBounds, alpha);
                continue;
            }
            float offset = item.conversationId.equals(mSwipeId) ? mSwipeOffsetPx : 0f;
            alpha *= PinnedSwipe.alphaFor(offset, mBounds.width());
            if (alpha <= 0f) continue;
            int layer = alpha < 1f
                ? canvas.saveLayerAlpha(mBounds.left + offset, mBounds.top,
                    mBounds.right + offset, mBounds.bottom, Math.round(alpha * 255))
                : canvas.save();
            canvas.translate(offset, 0f);
            drawCard(canvas, item, i, mBounds, form, now);
            canvas.restoreToCount(layer);
        }
        drawScrollLine(canvas);
        canvas.restore();
    }

    private enum CardForm {
        /** One card alone: title line and two body lines. */
        SINGLE,
        /** One card over the media strip: title line and one body line. */
        CONTENTION,
        /** Two or more: "Sender · message" on one line. */
        ROW
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

    private void drawCardGround(Canvas canvas, @NonNull RectF bounds, int tint, boolean pressed) {
        float radius = dp(CARD_RADIUS_DP);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setShader(washShader(tint, bounds.left, bounds.right));
        canvas.drawRoundRect(bounds, radius, radius, mFillPaint);
        mFillPaint.setShader(null);
        if (pressed) {
            mFillPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, 18));
            canvas.drawRoundRect(bounds, radius, radius, mFillPaint);
        }
        mFillPaint.setStyle(Paint.Style.STROKE);
        mFillPaint.setStrokeWidth(dp(1f));
        mFillPaint.setColor(ColorUtils.setAlphaComponent(tint, STROKE_ALPHA));
        mRect.set(bounds);
        mRect.inset(dp(.5f), dp(.5f));
        canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
        mFillPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * The tint's wash from {@code left} to {@code right}: one unit gradient per tint, aimed at the
     * card with a local matrix. A card of no width has no unit to stretch and is built in place.
     */
    @NonNull
    private Shader washShader(int tint, float left, float right) {
        int start = ColorUtils.setAlphaComponent(tint, WASH_START_ALPHA);
        int end = ColorUtils.setAlphaComponent(tint, WASH_END_ALPHA);
        if (right == left) {
            return new LinearGradient(left, 0f, right, 0f, start, end, Shader.TileMode.CLAMP);
        }
        LinearGradient shader = mWashShaders.get(tint);
        if (shader == null) {
            shader = new LinearGradient(0f, 0f, 1f, 0f, start, end, Shader.TileMode.CLAMP);
            mWashShaders.put(tint, shader);
        }
        mWashMatrix.setScale(right - left, 1f);
        mWashMatrix.postTranslate(left, 0f);
        shader.setLocalMatrix(mWashMatrix);
        return shader;
    }

    private void drawCard(Canvas canvas, @NonNull PinnedNotification item, int index,
                          @NonNull RectF bounds, @NonNull CardForm form, long now) {
        int tint = tintFor(item.packageName);
        int[] inks = inksFor(tint);
        drawCardGround(canvas, bounds, tint, mPressedOpenIndex == index);

        float height = bounds.height();
        float avatar = Math.min(dp(form == CardForm.ROW ? AVATAR_ROW_DP : AVATAR_DP),
            Math.max(0f, height - dp(6f)));
        float avatarLeft = bounds.left + dp(PAD_START_DP);
        float avatarTop = bounds.top + (height - avatar) / 2f;
        drawAvatar(canvas, item, avatarLeft, avatarTop, avatar, inks[5]);

        float textLeft = avatarLeft + avatar + dp(AVATAR_GAP_DP);
        float textRight = bounds.right - dp(PAD_END_DP);
        float textWidth = textRight - textLeft;
        if (textWidth <= dp(16f)) return;

        // The age label, measured first: it takes its width off the title line only.
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(TIME_SP));
        String time = PinnedRelativeTime.format(now, item.postTime);
        float timeWidth = mTextPaint.measureText(time);

        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setTextSize(sp(TITLE_SP));
        float titleAscent = mTextPaint.ascent();
        float titleHeight = mTextPaint.descent() - titleAscent;

        String chip = item.count >= 2 ? String.valueOf(item.count) : null;
        float chipWidth = 0f;
        float chipHeight = 0f;
        if (chip != null) {
            mTextPaint.setTextSize(sp(CHIP_SP));
            chipWidth = mTextPaint.measureText(chip) + dp(8f);
            chipHeight = Math.min(titleHeight, mTextPaint.descent() - mTextPaint.ascent() + dp(2f));
            chipWidth = Math.max(chipWidth, chipHeight);
        }
        float titleRoom = textWidth - timeWidth - dp(6f) - (chip == null ? 0f : chipWidth + dp(4f));
        if (titleRoom <= dp(12f)) {
            chip = null;
            titleRoom = textWidth - timeWidth - dp(6f);
        }

        if (form == CardForm.ROW) {
            drawRowText(canvas, item, bounds, textLeft, titleRoom, titleAscent, titleHeight,
                chip, chipWidth, chipHeight, time, textRight, inks);
            return;
        }

        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setTextSize(sp(TITLE_SP));
        CharSequence sender = mFits.fit(item.senderOrApp(), false, Math.max(0f, titleRoom),
            mTextPaint.getTypeface(), mTextPaint.getTextSize(), mFitter);
        float senderWidth = mTextPaint.measureText(sender, 0, sender.length());

        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(BODY_SP));
        float bodyGap = dp(2f);
        float bodyRoom = height - dp(6f) - titleHeight - bodyGap;
        StaticLayout layout = null;
        if (!item.body.isEmpty() && bodyRoom >= -mTextPaint.ascent()) {
            layout = StaticLayout.Builder
                .obtain(item.body, 0, item.body.length(), mTextPaint, Math.round(textWidth))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(form == CardForm.SINGLE ? 2 : 1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build();
            if (layout.getHeight() > bodyRoom) {
                layout = form == CardForm.SINGLE ? singleLine(item.body, textWidth) : null;
                if (layout != null && layout.getHeight() > bodyRoom) layout = null;
            }
        }
        // The text block is centred in the card as a whole; the avatar is centred on its own.
        float block = titleHeight + (layout == null ? 0f : bodyGap + layout.getHeight());
        float blockTop = bounds.top + (height - block) / 2f;
        float titleBaseline = blockTop - titleAscent;
        drawSenderLine(canvas, sender, senderWidth, textLeft, titleBaseline, titleHeight, blockTop,
            chip, chipWidth, chipHeight, inks);
        drawTime(canvas, time, textRight, titleBaseline, inks);
        if (layout == null) return;
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(BODY_SP));
        mTextPaint.setColor(inks[1]);
        canvas.save();
        canvas.translate(textLeft, blockTop + titleHeight + bodyGap);
        layout.draw(canvas);
        canvas.restore();
    }

    @Nullable
    private StaticLayout singleLine(@NonNull String body, float width) {
        return StaticLayout.Builder.obtain(body, 0, body.length(), mTextPaint, Math.round(width))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build();
    }

    /** "Sender · message" on the one line a two-card row has, the age at its end. */
    private void drawRowText(Canvas canvas, @NonNull PinnedNotification item, @NonNull RectF bounds,
                             float textLeft, float titleRoom, float titleAscent, float titleHeight,
                             @Nullable String chip, float chipWidth, float chipHeight,
                             @NonNull String time, float textRight, @NonNull int[] inks) {
        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setTextSize(sp(TITLE_SP));
        // The sender keeps at most two thirds of the line, so some of the message always shows.
        float senderRoom = Math.max(0f, titleRoom * (item.body.isEmpty() ? 1f : .66f));
        CharSequence sender = mFits.fit(item.senderOrApp(), false, senderRoom,
            mTextPaint.getTypeface(), mTextPaint.getTextSize(), mFitter);
        float senderWidth = mTextPaint.measureText(sender, 0, sender.length());
        float lineTop = bounds.top + (bounds.height() - titleHeight) / 2f;
        float baseline = lineTop - titleAscent;
        drawSenderLine(canvas, sender, senderWidth, textLeft, baseline, titleHeight, lineTop,
            chip, chipWidth, chipHeight, inks);
        drawTime(canvas, time, textRight, baseline, inks);
        if (item.body.isEmpty()) return;
        float used = senderWidth + (chip == null ? 0f : dp(4f) + chipWidth);
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(BODY_SP));
        float room = titleRoom + (chip == null ? 0f : chipWidth + dp(4f)) - used;
        String lead = " · ";
        float leadWidth = mTextPaint.measureText(lead);
        if (room <= leadWidth + dp(8f)) return;
        CharSequence body = mFits.fit(item.body, true, room - leadWidth,
            mTextPaint.getTypeface(), mTextPaint.getTextSize(), mFitter);
        mTextPaint.setColor(inks[1]);
        float x = textLeft + used;
        canvas.drawText(lead, x, baseline, mTextPaint);
        canvas.drawText(body, 0, body.length(), x + leadWidth, baseline, mTextPaint);
    }

    private void drawSenderLine(Canvas canvas, @NonNull CharSequence sender, float senderWidth,
                                float left, float baseline, float lineHeight, float lineTop,
                                @Nullable String chip, float chipWidth, float chipHeight,
                                @NonNull int[] inks) {
        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setTextSize(sp(TITLE_SP));
        mTextPaint.setColor(inks[0]);
        canvas.drawText(sender, 0, sender.length(), left, baseline, mTextPaint);
        if (chip == null) return;
        float chipLeft = left + senderWidth + dp(4f);
        float chipTop = lineTop + (lineHeight - chipHeight) / 2f;
        mRect.set(chipLeft, chipTop, chipLeft + chipWidth, chipTop + chipHeight);
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(inks[3]);
        canvas.drawRoundRect(mRect, chipHeight / 2f, chipHeight / 2f, mFillPaint);
        mTextPaint.setTextSize(sp(CHIP_SP));
        mTextPaint.setColor(inks[4]);
        float chipBaseline = mRect.centerY() - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        float chipText = mTextPaint.measureText(chip);
        canvas.drawText(chip, mRect.centerX() - chipText / 2f, chipBaseline, mTextPaint);
    }

    private void drawTime(Canvas canvas, @NonNull String time, float right, float baseline,
                          @NonNull int[] inks) {
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(TIME_SP));
        mTextPaint.setColor(inks[2]);
        canvas.drawText(time, right - mTextPaint.measureText(time), baseline, mTextPaint);
    }

    /**
     * The sender's picture as a circle with the app's icon as a badge at its foot; with no picture
     * (none posted, or not loaded yet), the app icon alone at the picture's size.
     */
    private void drawAvatar(Canvas canvas, @NonNull PinnedNotification item, float left, float top,
                            float size, int ringColor) {
        if (size <= 0f) return;
        Bitmap picture = mAvatars.get(item, Math.round(size), this::invalidate);
        if (picture == null) {
            drawAppIcon(canvas, item.packageName, left, top, size);
            return;
        }
        mRect.set(left, top, left + size, top + size);
        canvas.drawBitmap(picture, null, mRect, mFillPaint);
        float badge = Math.min(dp(BADGE_DP), size * .5f);
        float ring = dp(BADGE_RING_DP);
        float cx = left + size - badge / 2f + ring / 2f;
        float cy = top + size - badge / 2f + ring / 2f;
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ringColor);
        canvas.drawCircle(cx, cy, badge / 2f + ring, mFillPaint);
        canvas.save();
        mClip.reset();
        mClip.addCircle(cx, cy, badge / 2f, Path.Direction.CW);
        canvas.clipPath(mClip);
        drawAppIcon(canvas, item.packageName, cx - badge / 2f, cy - badge / 2f, badge);
        canvas.restore();
    }

    private void drawAppIcon(Canvas canvas, String packageName, float left, float top, float size) {
        Drawable icon = mIcons.get(packageName);
        if (icon == null) {
            mRect.set(left, top, left + size, top + size);
            mFillPaint.setShader(null);
            mFillPaint.setStyle(Paint.Style.FILL);
            mFillPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, 26));
            canvas.drawCircle(mRect.centerX(), mRect.centerY(), size / 2f, mFillPaint);
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

    /** A dismissed card's place while its undo runs: neutral glass, "Dismissed · Undo". */
    private void drawUndoRow(Canvas canvas, @NonNull RectF bounds, float alpha) {
        int layer = canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom,
            Math.round(alpha * 255));
        float radius = dp(CARD_RADIUS_DP);
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, 16));
        canvas.drawRoundRect(bounds, radius, radius, mFillPaint);
        mFillPaint.setStyle(Paint.Style.STROKE);
        mFillPaint.setStrokeWidth(dp(1f));
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, 38));
        mRect.set(bounds);
        mRect.inset(dp(.5f), dp(.5f));
        canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
        mFillPaint.setStyle(Paint.Style.FILL);

        int surface = OnGlass.opaque(mSurface);
        String dismissed = getResources().getString(R.string.pinned_notification_dismissed);
        String undo = getResources().getString(R.string.pinned_notification_undo);
        String lead = " · ";
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(TITLE_SP));
        float baseline = bounds.centerY() - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        float x = bounds.left + dp(10f);
        resolveUndoInks(surface);
        mTextPaint.setColor(mUndoInkVariant);
        canvas.drawText(dismissed, x, baseline, mTextPaint);
        x += mTextPaint.measureText(dismissed);
        canvas.drawText(lead, x, baseline, mTextPaint);
        x += mTextPaint.measureText(lead);
        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setColor(mUndoInkTertiary);
        canvas.drawText(undo, x, baseline, mTextPaint);
        canvas.restoreToCount(layer);
    }

    /** The undo row's inks on {@code surface}, re-resolved only when it or the roles move. */
    private void resolveUndoInks(int surface) {
        if (mUndoInksValid && mUndoInkSurface == surface
            && mUndoInkVariantSeed == mOnSurfaceVariant && mUndoInkTertiarySeed == mTertiary) {
            return;
        }
        mUndoInkSurface = surface;
        mUndoInkVariantSeed = mOnSurfaceVariant;
        mUndoInkTertiarySeed = mTertiary;
        mUndoInkVariant = GlassInk.legible(surface, mOnSurfaceVariant, OnGlass.TARGET_BODY_TEXT);
        mUndoInkTertiary = GlassInk.legible(surface, mTertiary, OnGlass.TARGET_BODY_TEXT);
        mUndoInksValid = true;
    }

    /**
     * Text fitted to the room its line leaves, remembered by everything the fit depends on: the
     * text, whether its line breaks are flattened to spaces first, the room, and the paint's face
     * and size (the only paint state this view ever changes besides colour, which a fit ignores).
     * The cards redraw on every frame of a swipe or a reveal; their text and widths do not move.
     */
    static final class FitMemo {

        /** Fits {@code text} into {@code room} on a paint set up for the key it is filed under. */
        interface Fitter {
            @NonNull
            CharSequence fit(@NonNull String text, float room);
        }

        static final int CAPACITY = 16;
        private final String[] mTexts = new String[CAPACITY];
        private final boolean[] mFlattened = new boolean[CAPACITY];
        private final float[] mRooms = new float[CAPACITY];
        private final Object[] mFaces = new Object[CAPACITY];
        private final float[] mSizes = new float[CAPACITY];
        private final CharSequence[] mResults = new CharSequence[CAPACITY];
        private int mNext;

        @NonNull
        CharSequence fit(@NonNull String text, boolean flattenLines, float room,
                         @Nullable Object face, float size, @NonNull Fitter fitter) {
            for (int i = 0; i < CAPACITY; i++) {
                if (mResults[i] != null && mRooms[i] == room && mSizes[i] == size
                    && java.util.Objects.equals(mFaces[i], face) && mFlattened[i] == flattenLines
                    && text.equals(mTexts[i])) return mResults[i];
            }
            CharSequence result = fitter.fit(flattenLines ? text.replace('\n', ' ') : text, room);
            int slot = mNext;
            mNext = (slot + 1) % CAPACITY;
            mTexts[slot] = text;
            mFlattened[slot] = flattenLines;
            mRooms[slot] = room;
            mFaces[slot] = face;
            mSizes[slot] = size;
            mResults[slot] = result;
            return result;
        }
    }

    // ---- Touch ------------------------------------------------------------

    private boolean isPendingAt(int index) {
        return mPending != null && index >= 0 && index < mItems.size()
            && mItems.get(index).conversationId.equals(mPending.conversationId);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mItems.isEmpty()) return false;
        if (mVelocity == null) mVelocity = VelocityTracker.obtain();
        mVelocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                cancelSettle();
                mDownX = event.getX();
                mDownY = event.getY();
                mDownScrollPx = mScrollPx;
                mDragging = false;
                mLongPressFired = false;
                mAxis = PinnedSwipe.Axis.UNDECIDED;
                mTouchIndex = hitItem(event.getX(), event.getY());
                mPressedOpenIndex = mTouchIndex;
                if (mTouchIndex >= 0 && !isPendingAt(mTouchIndex)) {
                    postDelayed(mLongPress, mLongPressTimeout);
                }
                if (mTouchIndex < 0 && maxScrollPx() <= 0f) {
                    recycleVelocity();
                    return false;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mLongPressFired) return true;
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                if (mAxis == PinnedSwipe.Axis.UNDECIDED) {
                    mAxis = PinnedSwipe.decide(dx, dy, mTouchSlop);
                    if (mAxis != PinnedSwipe.Axis.UNDECIDED) {
                        removeCallbacks(mLongPress);
                        mPressedOpenIndex = -1;
                        invalidate();
                        if (!beginAxis()) return false;
                    }
                }
                if (mSwipeId != null && mAxis == PinnedSwipe.Axis.HORIZONTAL) {
                    mSwipeOffsetPx = dx;
                    invalidate();
                    return true;
                }
                if (mDragging) {
                    setScrollPx(mDownScrollPx - dy);
                    return true;
                }
                return mAxis == PinnedSwipe.Axis.UNDECIDED && mTouchIndex >= 0;
            }
            case MotionEvent.ACTION_UP: {
                removeCallbacks(mLongPress);
                int index = mTouchIndex;
                mTouchIndex = -1;
                mPressedOpenIndex = -1;
                invalidate();
                if (mLongPressFired) {
                    recycleVelocity();
                    return true;
                }
                if (mSwipeId != null) {
                    mVelocity.computeCurrentVelocity(1000, mMaxFlingVelocity);
                    float width = cardRightPx();
                    boolean commit = PinnedSwipe.commits(mSwipeOffsetPx, width,
                        mVelocity.getXVelocity(), mVelocity.getYVelocity(), mMinFlingVelocity);
                    recycleVelocity();
                    finishSwipe(commit);
                    return true;
                }
                recycleVelocity();
                if (mDragging) {
                    mDragging = false;
                    settle();
                    return true;
                }
                if (mAxis != PinnedSwipe.Axis.UNDECIDED || index < 0
                    || hitItem(event.getX(), event.getY()) != index) {
                    return true;
                }
                if (isPendingAt(index)) {
                    undoPendingDismiss();
                    return true;
                }
                if (mOpenListener != null && index < mItems.size()) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    mOpenListener.onOpenPinned(mItems.get(index));
                }
                return true;
            }
            default: {
                removeCallbacks(mLongPress);
                recycleVelocity();
                mTouchIndex = -1;
                mPressedOpenIndex = -1;
                if (mSwipeId != null) {
                    finishSwipe(false);
                    return true;
                }
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
    }

    /**
     * The drag has chosen its axis. Sideways on a card is that card's swipe; up or down is the
     * run's scroll while there is one. Returns false when the stream is nobody's here.
     */
    private boolean beginAxis() {
        if (mAxis == PinnedSwipe.Axis.HORIZONTAL) {
            if (mTouchIndex < 0 || isPendingAt(mTouchIndex)) return false;
            cancelSwipeAnimator();
            mSwipeId = mItems.get(mTouchIndex).conversationId;
            mSwipeOffsetPx = 0f;
            claimGestureFromParent();
            return true;
        }
        if (maxScrollPx() > 0f) {
            // A vertical drag on the cards is theirs from here: the press states drop and the
            // parent chain is told to keep its hands off the rest of the stream.
            mDragging = true;
            claimGestureFromParent();
            return true;
        }
        return false;
    }

    private void settle() {
        animateScrollTo(snapTargetPx(mDownScrollPx, mScrollPx, stepPx(), maxScrollPx()));
    }

    private void claimGestureFromParent() {
        ViewParent parent = getParent();
        // One call is enough: a ViewGroup passes the flag up its own parent chain.
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    private void recycleVelocity() {
        if (mVelocity == null) return;
        mVelocity.recycle();
        mVelocity = null;
    }

    private void onLongPress() {
        if (mTouchIndex < 0 || mTouchIndex >= mItems.size() || isPendingAt(mTouchIndex)) return;
        mLongPressFired = true;
        mPressedOpenIndex = -1;
        invalidate();
        Haptics.tick(this, HapticFeedbackConstants.LONG_PRESS);
        showMenu(mItems.get(mTouchIndex));
    }

    // ---- Swipe to dismiss -------------------------------------------------

    private void cancelSwipeAnimator() {
        if (mSwipeAnimator == null) return;
        mSwipeAnimator.cancel();
        mSwipeAnimator = null;
    }

    private void cancelSwipe() {
        cancelSwipeAnimator();
        mSwipeId = null;
        mSwipeOffsetPx = 0f;
    }

    /** The finger let go of a sideways card: off the edge into its undo, or back into place. */
    private void finishSwipe(boolean commit) {
        String id = mSwipeId;
        if (id == null) return;
        float width = Math.max(1f, cardRightPx());
        float target = commit ? Math.signum(mSwipeOffsetPx == 0f ? 1f : mSwipeOffsetPx) * width : 0f;
        cancelSwipeAnimator();
        if (reducedMotion() || Math.abs(target - mSwipeOffsetPx) < .5f) {
            endSwipe(id, commit);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mSwipeOffsetPx, target);
        animator.setDuration(SWIPE_SETTLE_MS);
        animator.setInterpolator(INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            mSwipeOffsetPx = (Float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mSwipeAnimator == animation) mSwipeAnimator = null;
                if (!mCancelled) endSwipe(id, commit);
            }
        });
        mSwipeAnimator = animator;
        animator.start();
    }

    private void endSwipe(@NonNull String id, boolean commit) {
        mSwipeId = null;
        mSwipeOffsetPx = 0f;
        int index = indexOf(id);
        if (commit && index >= 0) beginPendingDismiss(mItems.get(index));
        invalidate();
    }

    // ---- Undo -------------------------------------------------------------

    /**
     * {@code item} is dismissed but not yet gone: its place offers the undo for {@link #UNDO_MS}.
     * A dismissal already waiting is carried out first — only one undo is on offer at a time.
     */
    @VisibleForTesting
    void beginPendingDismiss(@NonNull PinnedNotification item) {
        if (mPending != null && !mPending.conversationId.equals(item.conversationId)) {
            commitPendingDismiss();
        }
        mPending = item;
        removeCallbacks(mUndoTimeout);
        postDelayed(mUndoTimeout, UNDO_MS);
        reveal(item.conversationId);
        announceForAccessibility(getResources().getString(R.string.pinned_notification_dismissed));
        mAccessibility.invalidateRoot();
        invalidate();
    }

    /** Carries out the waiting dismissal now, if there is one. */
    public void commitPendingDismiss() {
        PinnedNotification pending = mPending;
        if (pending == null) return;
        removeCallbacks(mUndoTimeout);
        mPending = null;
        mAccessibility.invalidateRoot();
        invalidate();
        if (mListener != null) mListener.onDismissPinned(pending);
    }

    /** The undo was taken: the card comes back, nothing was dismissed. */
    @VisibleForTesting
    void undoPendingDismiss() {
        PinnedNotification pending = mPending;
        if (pending == null) return;
        removeCallbacks(mUndoTimeout);
        mPending = null;
        reveal(pending.conversationId);
        mAccessibility.invalidateRoot();
        invalidate();
    }

    @VisibleForTesting
    @Nullable
    PinnedNotification pendingDismiss() {
        return mPending;
    }

    /** A short fade-in of the row that just changed face; instant under reduced motion. */
    private void reveal(@NonNull String id) {
        if (mRevealAnimator != null) mRevealAnimator.cancel();
        mRevealAnimator = null;
        if (reducedMotion()) {
            mRevealId = null;
            mRevealAlpha = 1f;
            return;
        }
        mRevealId = id;
        mRevealAlpha = 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(REVEAL_MS);
        animator.addUpdateListener(animation -> {
            mRevealAlpha = (Float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mRevealAnimator != animation) return;
                mRevealAnimator = null;
                mRevealId = null;
                mRevealAlpha = 1f;
                invalidate();
            }
        });
        mRevealAnimator = animator;
        animator.start();
    }

    private boolean reducedMotion() {
        return ReducedMotion.isEnabled(getContext());
    }

    // ---- Long-press menu --------------------------------------------------

    /**
     * The launcher's anchored glass menu ({@link AnchoredMenu}, the dock's and the drawer's): the
     * sender, the age and the message in full (up to four lines), then Open, Dismiss and Mute this
     * rule.
     */
    private void showMenu(@NonNull PinnedNotification item) {
        if (!isAttachedToWindow()) return;
        Context context = getContext();
        AnchoredMenuTheme theme = new AnchoredMenuTheme() {
            @Override public int textColor() { return GlassInk.legible(mSurface, mOnSurface, OnGlass.TARGET_BODY_TEXT); }
            @Override public int selectedTextColor() { return textColor(); }
            @Override public int opacityPercent() { return 0; }
            @Override public boolean blurEnabled() { return false; }
            @Override public int blurRadiusDp() { return 0; }
        };
        if (mMenu != null) mMenu.dismiss();
        AnchoredMenu menu = new AnchoredMenu(this, theme);
        mMenu = menu;
        MenuRowFactory rows = new MenuRowFactory(context, theme);
        LinearLayout shell = rows.newShell();
        int tintBase = mSurface & 0x00FFFFFF;
        int pad = Math.round(dp(8f));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(pad, pad, pad, Math.round(dp(4f)));
        LinearLayout titleLine = new LinearLayout(context);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView sender = new TextView(context);
        sender.setText(item.senderOrApp());
        sender.setTypeface(mediumTypeface());
        sender.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        sender.setTextColor(theme.textColor());
        sender.setSingleLine(false);
        titleLine.addView(sender, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView time = new TextView(context);
        time.setText(PinnedRelativeTime.format(System.currentTimeMillis(), item.postTime));
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        time.setTextColor(GlassInk.legible(mSurface, mOnSurfaceVariant, OnGlass.TARGET_BODY_TEXT));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.setMarginStart(pad);
        titleLine.addView(time, timeParams);
        header.addView(titleLine);
        TextView app = new TextView(context);
        app.setText(item.count >= 2
            ? item.appLabel + " · " + getResources().getQuantityString(
                R.plurals.pinned_notification_messages, item.count, item.count)
            : item.appLabel);
        app.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        app.setTextColor(time.getCurrentTextColor());
        header.addView(app);
        if (!item.body.isEmpty()) {
            TextView body = new TextView(context);
            body.setText(item.body);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            body.setTextColor(theme.textColor());
            body.setMaxLines(4);
            body.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyParams.topMargin = Math.round(dp(4f));
            header.addView(body, bodyParams);
        }
        shell.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rows.addDivider(shell);
        String conversation = item.conversationId;
        rows.addActionRow(shell, getResources().getString(R.string.pinned_notification_open),
            tintBase, () -> {
                menu.dismiss();
                PinnedNotification current = currentFor(conversation);
                if (current != null && mOpenListener != null) mOpenListener.onOpenPinned(current);
            });
        rows.addActionRow(shell, getResources().getString(R.string.pinned_notification_dismiss),
            tintBase, () -> {
                menu.dismiss();
                PinnedNotification current = currentFor(conversation);
                if (current != null) beginPendingDismiss(current);
            });
        if (item.matchedRuleId != null && !item.matchedRuleId.isEmpty()) {
            rows.addActionRow(shell, getResources().getString(R.string.pinned_notification_mute_rule),
                tintBase, () -> {
                    menu.dismiss();
                    PinnedNotification current = currentFor(conversation);
                    if (current != null && mMuteListener != null) mMuteListener.onMuteRule(current);
                });
        }
        int width = Math.round(Math.max(getWidth(), dp(220f)));
        menu.show(MenuSpec.of(shell, tintBase)
            .tightWrap(false)
            .width(width)
            .minimumOpacityPercent(90)
            .verticalScrollbar(false)
            .animateEntry(!reducedMotion())
            .build(), this);
    }

    @Nullable
    private PinnedNotification currentFor(@NonNull String conversationId) {
        return find(mItems, conversationId);
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

    /** What TalkBack reads for a card: "Sender, message, App, 4m[, 3 messages]". */
    @VisibleForTesting
    @NonNull
    String accessibilityLabel(@NonNull PinnedNotification item) {
        StringBuilder label = new StringBuilder(item.senderOrApp());
        if (!item.body.isEmpty()) label.append(", ").append(item.body);
        if (!item.sender.isEmpty() && !item.appLabel.isEmpty()) {
            label.append(", ").append(item.appLabel);
        }
        label.append(", ").append(PinnedRelativeTime.format(System.currentTimeMillis(),
            item.postTime));
        if (item.count >= 2) {
            label.append(", ").append(getResources().getQuantityString(
                R.plurals.pinned_notification_messages, item.count, item.count));
        }
        return label.toString();
    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        return mAccessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
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
    public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
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

    /** Each card on screen is a virtual view with Open, Dismiss and Mute this rule. */
    private final class CardAccessibility extends ExploreByTouchHelper {

        CardAccessibility(@NonNull View host) {
            super(host);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            int index = hitItem(x, y);
            return index >= 0 ? index : INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            RectF bounds = new RectF();
            for (int i = 0; i < mItems.size(); i++) {
                if (cardBounds(i, bounds)) virtualViewIds.add(i);
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId,
                                                    @NonNull AccessibilityNodeInfoCompat node) {
            RectF bounds = new RectF();
            if (virtualViewId >= mItems.size() || !cardBounds(virtualViewId, bounds)) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect(0, 0, 1, 1));
                return;
            }
            Rect rect = new Rect();
            bounds.round(rect);
            node.setBoundsInParent(rect);
            node.setClassName(android.widget.Button.class.getName());
            if (isPendingAt(virtualViewId)) {
                node.setContentDescription(getResources()
                    .getString(R.string.pinned_notification_undo_description));
                node.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_CLICK,
                    getResources().getString(R.string.pinned_notification_undo)));
                return;
            }
            PinnedNotification item = mItems.get(virtualViewId);
            node.setContentDescription(accessibilityLabel(item));
            node.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                AccessibilityNodeInfoCompat.ACTION_CLICK,
                getResources().getString(R.string.pinned_notification_open)));
            node.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                R.id.pinned_notification_action_dismiss,
                getResources().getString(R.string.pinned_notification_dismiss)));
            if (item.matchedRuleId != null && !item.matchedRuleId.isEmpty()) {
                node.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.pinned_notification_action_mute,
                    getResources().getString(R.string.pinned_notification_mute_rule)));
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action,
                                                        @Nullable Bundle arguments) {
            if (virtualViewId < 0 || virtualViewId >= mItems.size()) return false;
            PinnedNotification item = mItems.get(virtualViewId);
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                if (isPendingAt(virtualViewId)) {
                    undoPendingDismiss();
                } else if (mOpenListener != null) {
                    mOpenListener.onOpenPinned(item);
                }
                return true;
            }
            if (action == R.id.pinned_notification_action_dismiss && !isPendingAt(virtualViewId)) {
                beginPendingDismiss(item);
                return true;
            }
            if (action == R.id.pinned_notification_action_mute && mMuteListener != null) {
                mMuteListener.onMuteRule(item);
                return true;
            }
            return false;
        }
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
