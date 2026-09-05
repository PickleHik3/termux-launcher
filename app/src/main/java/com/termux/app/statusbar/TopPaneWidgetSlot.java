package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.TerminalClockWidget;

import java.util.List;

/**
 * The 68dp widget slot above the status row. It owns the {@code slotMode} state machine: pinned
 * notifications outrank media, media outranks the clock at full size, and the clock compresses
 * through its grid forms rather than the pane ever changing height.
 *
 * <p>Everything is fed by {@link TopPaneFeed}, which is empty unless the notification listener has
 * been granted access — so without that permission the slot simply stays on the full clock.
 */
public final class TopPaneWidgetSlot extends ViewGroup implements TopPaneFeed.Observer {

    private static final float GUTTER_DP = 12f;
    private static final float GAP_DP = 12f;
    private static final long MEDIA_TRANSITION_MS = 180L;
    private static final long PINNED_TRANSITION_MS = 200L;
    private static final float STACK_HEIGHT_DP = 66f;

    private static final Interpolator INTERPOLATOR = new PathInterpolator(.16f, 1f, .3f, 1f);

    private final Rect mClockBounds = new Rect();
    private final Rect mSummaryBounds = new Rect();
    private final Rect mNotificationBounds = new Rect();
    private final Rect mMediaBounds = new Rect();

    @Nullable private TerminalClockWidget mClock;
    @Nullable private PinnedNotificationsView mNotifications;
    @Nullable private MediaWidgetView mMedia;
    @Nullable private PinnedNotificationIconCache mIcons;
    @Nullable private ViewPropertyAnimator mClockFade;
    @Nullable private TopPanePlaceSummaryView mSummary;

    private TopPaneSlotMode mMode = TopPaneSlotMode.CLOCK_ONLY;
    @Nullable private Runnable mModeListener;
    private int mPinnedCount;
    @Nullable private String mClockAlignment;

    public TopPaneWidgetSlot(Context context) {
        this(context, null);
    }

    public TopPaneWidgetSlot(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(true);
        setClipToPadding(true);
        setWillNotDraw(false);
    }

    private final android.graphics.Paint mRuleExtensionPaint = new android.graphics.Paint();

    /**
     * The clock's own date hairline stops at its view edges, one gutter short of the pane. These
     * two segments carry it to the slot's edges so the line reads as spanning the whole plane.
     */
    @Override
    protected void dispatchDraw(@NonNull android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);
        TerminalClockWidget clock = mClock;
        if (clock == null || clock.getVisibility() != VISIBLE || clock.getAlpha() <= 0f) return;
        // With the place summary sharing the slot there is no plane left for the line to span:
        // carrying it under the summary would draw the clock's own detail across it.
        if (!mSummaryBounds.isEmpty()) return;
        float ruleY = clock.fullRuleCenterYPx();
        if (ruleY < 0f) return;
        float y = clock.getTop() + clock.getTranslationY() + ruleY;
        float half = clock.fullRuleHalfThicknessPx();
        int color = clock.fullRuleColor();
        mRuleExtensionPaint.setColor(color);
        mRuleExtensionPaint.setAlpha(Math.round(android.graphics.Color.alpha(color)
            * clock.getAlpha()));
        float left = clock.getLeft() + clock.getTranslationX();
        float right = left + clock.getWidth();
        if (left > 0f) canvas.drawRect(0f, y - half, left, y + half, mRuleExtensionPaint);
        if (right < getWidth()) {
            canvas.drawRect(right, y - half, getWidth(), y + half, mRuleExtensionPaint);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mClock = findViewById(R.id.terminal_clock_widget);
        mNotifications = findViewById(R.id.terminal_pinned_notifications);
        mMedia = findViewById(R.id.terminal_media_widget);
        mIcons = new PinnedNotificationIconCache(getContext());
        if (mNotifications != null) {
            mNotifications.setVisibility(GONE);
            mNotifications.setListener(this::dismissPinned);
            mNotifications.setOpenListener(this::openPinned);
        }
        if (mMedia != null) mMedia.setVisibility(GONE);
        mSummary = findViewById(R.id.terminal_place_summary);
        applyFeed(false);
    }

    @Nullable
    public TopPanePlaceSummaryView placeSummary() {
        return mSummary;
    }

    /**
     * What the place on screen holds, beside the clock; empty takes the line away and gives the
     * clock the slot. The summary slides with the wall like the rest of the place's content.
     */
    public void setPlaceSummary(@Nullable CharSequence text, int accent) {
        if (mSummary == null) return;
        boolean had = mSummary.hasSummary();
        mSummary.setSummary(text, accent);
        if (had != mSummary.hasSummary()) requestLayout();
    }

    /** The wall moved; the summary goes with it. */
    public void setPlaceOffset(float offsetPx) {
        if (mSummary != null) mSummary.setTranslationX(offsetPx);
    }

    /** The clock alignment decides the cell order; the tiles read the same preference it does. */
    public void setClockAlignment(@Nullable String alignment) {
        if (alignment == null ? mClockAlignment == null : alignment.equals(mClockAlignment)) return;
        mClockAlignment = alignment;
        requestLayout();
    }

    @NonNull
    public TopPaneSlotMode getSlotMode() {
        return mMode;
    }

    /** Told whenever the slot's mode changes: the bar's place icons keep clear of the cards. */
    public void setModeListener(@Nullable Runnable listener) {
        mModeListener = listener;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        TopPaneFeed.addObserver(this);
        applyFeed(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        TopPaneFeed.removeObserver(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onTopPaneFeedChanged() {
        applyFeed(true);
    }

    private void dismissPinned(@NonNull PinnedNotification notification) {
        TopPaneFeed.dismissPinned(notification.key, notification.clearOnDismiss);
    }

    private void openPinned(@NonNull PinnedNotification notification) {
        TopPaneFeed.openPinned(notification.key);
    }

    private void applyFeed(boolean animate) {
        if (mClock == null) return;
        List<PinnedNotification> pinned = TopPaneFeed.getPinned();
        TopPaneMediaState media = TopPaneFeed.getMedia();
        TopPaneSlotMode mode = TopPaneSlotMode.derive(pinned.size(), media != null);
        int pinnedCount = mode.showsNotifications()
            ? Math.min(pinned.size(), TopPaneSlotMode.MAX_PINNED) : 0;

        // Content is only refreshed while the view is claiming the slot: a view on its way out keeps
        // its last frame so the fade has something to fade.
        if (mNotifications != null && mode.showsNotifications()) {
            mNotifications.setItems(pinned);
            mNotifications.setCompactCard(mode == TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA
                || pinnedCount == 2);
        }
        if (mMedia != null && mode.showsMedia() && media != null) {
            mMedia.setForm(mode == TopPaneSlotMode.NOTIFICATIONS_AND_MEDIA
                ? MediaWidgetView.Form.STRIP : MediaWidgetView.Form.FULL);
            mMedia.setState(media, mIcons == null ? null : mIcons.get(media.packageName));
        }

        boolean modeChanged = mode != mMode || pinnedCount != mPinnedCount;
        boolean cardsChanged = (mode == TopPaneSlotMode.CLOCK_ONLY) != (mMode == TopPaneSlotMode.CLOCK_ONLY);
        mMode = mode;
        mPinnedCount = pinnedCount;
        if (cardsChanged && mModeListener != null) mModeListener.run();
        applyClockForm(mode.clockForm(pinnedCount), animate);
        applyChildVisibility(mNotifications, mode.showsNotifications(), animate,
            PINNED_TRANSITION_MS, 0f, 6f);
        applyChildVisibility(mMedia, mode.showsMedia(), animate, MEDIA_TRANSITION_MS, 8f, 0f);
        if (modeChanged) requestLayout();
    }

    private void applyClockForm(@NonNull TopPaneClockForm form, boolean animate) {
        TerminalClockWidget clock = mClock;
        if (clock == null || clock.getForm() == form) return;
        if (mClockFade != null) {
            mClockFade.cancel();
            mClockFade = null;
        }
        if (!animate) {
            clock.setAlpha(1f);
            clock.setForm(form);
            return;
        }
        // The update listeners keep the slot's hairline extensions tracking the clock's alpha.
        mClockFade = clock.animate().alpha(0f).setDuration(MEDIA_TRANSITION_MS / 2)
            .setInterpolator(INTERPOLATOR)
            .setUpdateListener(animation -> invalidate())
            .withEndAction(() -> {
                clock.setForm(form);
                mClockFade = clock.animate().alpha(1f).setDuration(MEDIA_TRANSITION_MS / 2)
                    .setInterpolator(INTERPOLATOR)
                    .setUpdateListener(animation -> invalidate());
                mClockFade.start();
            });
        mClockFade.start();
    }

    /**
     * Enter and exit keep the leaving view laid out at its last bounds until the fade finishes, so a
     * mode change never snaps content out from under the animation.
     */
    private void applyChildVisibility(@Nullable View view, boolean visible, boolean animate,
                                      long duration, float slideXDp, float slideYDp) {
        if (view == null) return;
        boolean shown = view.getVisibility() == VISIBLE;
        if (visible == shown && (!visible || view.getAlpha() >= 1f)) {
            if (visible) {
                view.setTranslationX(0f);
                view.setTranslationY(0f);
            }
            return;
        }
        view.animate().cancel();
        if (visible) {
            view.setVisibility(VISIBLE);
            if (!animate) {
                view.setAlpha(1f);
                view.setTranslationX(0f);
                view.setTranslationY(0f);
                return;
            }
            view.setAlpha(0f);
            view.setTranslationX(dp(slideXDp));
            view.setTranslationY(dp(slideYDp));
            view.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(duration)
                .setInterpolator(INTERPOLATOR).start();
            return;
        }
        if (!animate) {
            view.setVisibility(GONE);
            view.setAlpha(1f);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            return;
        }
        view.animate().alpha(0f).translationX(dp(slideXDp)).translationY(dp(slideYDp))
            .setDuration(duration).setInterpolator(INTERPOLATOR)
            .withEndAction(() -> {
                view.setVisibility(GONE);
                view.setAlpha(1f);
                view.setTranslationX(0f);
                view.setTranslationY(0f);
            }).start();
    }

    // ---- Layout -----------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        if (mClock == null) return;

        int gutter = Math.round(dp(GUTTER_DP));
        int gap = Math.round(dp(GAP_DP));
        // The bar's home place icon sits at the slot's start, beside the clock, whatever the
        // clock's alignment; the clock and everything else lay out after it.
        int homeCell = StatusBarLensView.homeCellWidthPx(getContext());
        int contentStart = gutter + homeCell;
        int available = Math.max(0, width - contentStart - gutter);
        boolean stacked = mMode.showsNotifications() && mPinnedCount >= TopPaneSlotMode.MAX_PINNED;

        int clockWidth;
        int clockHeight;
        if (mMode == TopPaneSlotMode.CLOCK_ONLY) {
            clockWidth = available;
            clockHeight = height;
        } else {
            clockHeight = stacked ? Math.round(dp(14f)) : height;
            mClock.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(clockHeight, MeasureSpec.EXACTLY));
            clockWidth = Math.min(mClock.getMeasuredWidth(), Math.round(available * .55f));
        }
        mClock.measure(MeasureSpec.makeMeasureSpec(clockWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(clockHeight, MeasureSpec.EXACTLY));
        mClockBounds.set(contentStart, stacked ? 0 : (height - clockHeight) / 2,
            contentStart + clockWidth, (stacked ? 0 : (height - clockHeight) / 2) + clockHeight);

        mNotificationBounds.setEmpty();
        mMediaBounds.setEmpty();
        applyPlaceSummary(width, height, gutter, gap, homeCell);
        if (mMode == TopPaneSlotMode.CLOCK_ONLY) return;

        if (stacked) {
            int stackHeight = Math.min(height, Math.round(dp(STACK_HEIGHT_DP)));
            mNotificationBounds.set(contentStart, Math.max(0, (height - stackHeight) / 2),
                width - gutter, Math.max(0, (height - stackHeight) / 2) + stackHeight);
            if (mNotifications != null) {
                mNotifications.setHeaderInsetStart(clockWidth + gap);
                measureExact(mNotifications, mNotificationBounds);
            }
            return;
        }

        int contentLeft = contentStart + clockWidth + gap;
        int contentRight = width - gutter;
        int contentWidth = Math.max(0, contentRight - contentLeft);
        if (mNotifications != null) mNotifications.setHeaderInsetStart(0f);

        switch (mMode) {
            case NOTIFICATIONS_AND_MEDIA: {
                int cardHeight = Math.round(dp(PinnedNotificationsView.CONTENTION_CARD_HEIGHT_DP));
                int stripHeight = Math.round(dp(MediaWidgetView.STRIP_HEIGHT_DP));
                int columnGap = Math.round(dp(6f));
                int columnHeight = Math.min(height, cardHeight + columnGap + stripHeight);
                int top = Math.max(0, (height - columnHeight) / 2);
                cardHeight = Math.max(0, columnHeight - columnGap - stripHeight);
                mNotificationBounds.set(contentLeft, top, contentRight, top + cardHeight);
                mMediaBounds.set(contentLeft, top + cardHeight + columnGap, contentRight,
                    top + columnHeight);
                break;
            }
            case NOTIFICATIONS: {
                // One card gets two body lines; two share the slot at one line each.
                int desired = Math.round(dp(mPinnedCount == 1 ? 48f : 68f));
                int cardsHeight = Math.min(height, desired);
                int top = Math.max(0, (height - cardsHeight) / 2);
                mNotificationBounds.set(contentLeft, top, contentRight, top + cardsHeight);
                break;
            }
            case MEDIA: {
                int mediaHeight = Math.min(height, Math.round(dp(MediaWidgetView.FULL_HEIGHT_DP)));
                int top = Math.max(0, (height - mediaHeight) / 2);
                mMediaBounds.set(contentLeft, top, contentRight, top + mediaHeight);
                break;
            }
            default:
                break;
        }
        if (contentWidth <= 0) {
            mNotificationBounds.setEmpty();
            mMediaBounds.setEmpty();
            return;
        }
        if (mNotifications != null && !mNotificationBounds.isEmpty()) {
            measureExact(mNotifications, mNotificationBounds);
        }
        if (mMedia != null && !mMediaBounds.isEmpty()) measureExact(mMedia, mMediaBounds);
    }

    /** Height of the place summary's line. */
    private static final float PLACE_SUMMARY_HEIGHT_DP = 20f;
    /** The compact clock's face; less than this beside the summary and the summary stands down. */
    private static final float PLACE_SUMMARY_MIN_CLOCK_DP = 96f;
    /** The summary never takes more than this much of the slot from the clock. */
    private static final float PLACE_SUMMARY_MAX_FRACTION = .5f;

    /**
     * Give the place summary its hugging spot beside the clock, or take it away. The clock drops
     * to its compact face when what the summary leaves cannot hold its full one — at a large font
     * scale on a narrow screen it cannot, which is why the fit is measured.
     */
    private void applyPlaceSummary(int width, int height, int gutter, int gap, int homeCell) {
        boolean shown = mMode.showsTiles(true) && mSummary != null && mSummary.hasSummary();
        if (!shown) {
            mSummaryBounds.setEmpty();
            if (mSummary != null) mSummary.setVisibility(GONE);
            return;
        }
        int summaryHeight = Math.min(height, Math.round(dp(PLACE_SUMMARY_HEIGHT_DP)));
        // The clock and the summary share what is left after the home icon's cell.
        int shared = Math.max(0, width - homeCell);
        int maxWidth = Math.max(0, Math.round((shared - gutter * 2) * PLACE_SUMMARY_MAX_FRACTION));
        mSummary.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(summaryHeight, MeasureSpec.EXACTLY));
        int clockDesired = mClock == null ? 0 : Math.max(1, Math.round(mClock.contentWidth()));
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        TopPaneAsideLayoutPolicy.Result result = TopPaneAsideLayoutPolicy.calculate(shared, height,
            gutter, gap, mClockAlignment, mSummary.getMeasuredWidth(), summaryHeight,
            clockDesired, Math.round(dp(PLACE_SUMMARY_MIN_CLOCK_DP)), rtl);
        mClockBounds.set(result.clock);
        mSummaryBounds.set(result.place);
        // Back into the slot's own coordinates: the shared area starts after the home cell.
        int shift = rtl ? 0 : homeCell;
        mClockBounds.offset(shift, 0);
        if (!mSummaryBounds.isEmpty()) mSummaryBounds.offset(shift, 0);
        applyClockForm(result.clockCompact ? TopPaneClockForm.COMPACT : TopPaneClockForm.FULL,
            false);
        if (mClock != null && !mClockBounds.isEmpty()) measureExact(mClock, mClockBounds);
        if (mSummaryBounds.isEmpty()) {
            mSummary.setVisibility(GONE);
        } else {
            mSummary.setVisibility(VISIBLE);
            measureExact(mSummary, mSummaryBounds);
        }
    }

    private void measureExact(@NonNull View view, @NonNull Rect bounds) {
        view.measure(MeasureSpec.makeMeasureSpec(bounds.width(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(bounds.height(), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mClock != null) {
            mClock.layout(mClockBounds.left, mClockBounds.top, mClockBounds.right, mClockBounds.bottom);
        }
        if (mNotifications != null && !mNotificationBounds.isEmpty()) {
            mNotifications.layout(mNotificationBounds.left, mNotificationBounds.top,
                mNotificationBounds.right, mNotificationBounds.bottom);
        }
        if (mMedia != null && !mMediaBounds.isEmpty()) {
            mMedia.layout(mMediaBounds.left, mMediaBounds.top, mMediaBounds.right, mMediaBounds.bottom);
        }
        if (mSummary != null && !mSummaryBounds.isEmpty()) {
            mSummary.layout(mSummaryBounds.left, mSummaryBounds.top,
                mSummaryBounds.right, mSummaryBounds.bottom);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
