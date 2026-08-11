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
    private final Rect mNotificationBounds = new Rect();
    private final Rect mMediaBounds = new Rect();

    @Nullable private TerminalClockWidget mClock;
    @Nullable private PinnedNotificationsView mNotifications;
    @Nullable private MediaWidgetView mMedia;
    @Nullable private PinnedNotificationIconCache mIcons;
    @Nullable private ViewPropertyAnimator mClockFade;

    private TopPaneSlotMode mMode = TopPaneSlotMode.CLOCK_ONLY;
    private int mPinnedCount;
    private float mFullExpansionProgress;

    public TopPaneWidgetSlot(Context context) {
        this(context, null);
    }

    public TopPaneWidgetSlot(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(true);
        setClipToPadding(true);
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
        applyFeed(false);
    }

    @NonNull
    public TopPaneSlotMode getSlotMode() {
        return mMode;
    }

    /** One controller-owned channel; child bounds are pure functions of this value. */
    public void setFullExpansionProgress(float progress) {
        float clamped = Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 0f;
        if (Math.abs(clamped - mFullExpansionProgress) < .0001f) return;
        mFullExpansionProgress = clamped;
        if (mClock != null) mClock.setFullPresentationProgress(clamped);
        requestLayout();
    }

    public float getFullExpansionProgress() { return mFullExpansionProgress; }

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
        if (mFullExpansionProgress > 0f) {
            // FULL owns every child bound. A feed update may change the mode, but no stale child
            // animator is allowed to keep writing position while the row policy recomputes it.
            if (mClockFade != null) mClockFade.cancel();
            mClockFade = null;
            mClock.animate().cancel();
            mClock.setAlpha(1f);
            mClock.setTranslationX(0f);
            mClock.setTranslationY(0f);
            if (mNotifications != null) mNotifications.animate().cancel();
            if (mMedia != null) mMedia.animate().cancel();
            animate = false;
        }
        mMode = mode;
        mPinnedCount = pinnedCount;
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
        mClockFade = clock.animate().alpha(0f).setDuration(MEDIA_TRANSITION_MS / 2)
            .setInterpolator(INTERPOLATOR)
            .withEndAction(() -> {
                clock.setForm(form);
                mClockFade = clock.animate().alpha(1f).setDuration(MEDIA_TRANSITION_MS / 2)
                    .setInterpolator(INTERPOLATOR);
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
        int available = Math.max(0, width - gutter * 2);
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
        mClockBounds.set(gutter, stacked ? 0 : (height - clockHeight) / 2,
            gutter + clockWidth, (stacked ? 0 : (height - clockHeight) / 2) + clockHeight);

        mNotificationBounds.setEmpty();
        mMediaBounds.setEmpty();
        if (mMode == TopPaneSlotMode.CLOCK_ONLY) {
            applyFullRowPolicy(width, height, gutter, gap);
            return;
        }

        if (stacked) {
            int stackHeight = Math.min(height, Math.round(dp(STACK_HEIGHT_DP)));
            mNotificationBounds.set(gutter, Math.max(0, (height - stackHeight) / 2),
                width - gutter, Math.max(0, (height - stackHeight) / 2) + stackHeight);
            if (mNotifications != null) {
                mNotifications.setHeaderInsetStart(clockWidth + gap);
                measureExact(mNotifications, mNotificationBounds);
            }
            applyFullRowPolicy(width, height, gutter, gap);
            return;
        }

        int contentLeft = gutter + clockWidth + gap;
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
            applyFullRowPolicy(width, height, gutter, gap);
            return;
        }
        if (mNotifications != null && !mNotificationBounds.isEmpty()) {
            measureExact(mNotifications, mNotificationBounds);
        }
        if (mMedia != null && !mMediaBounds.isEmpty()) measureExact(mMedia, mMediaBounds);
        applyFullRowPolicy(width, height, gutter, gap);
    }

    private void applyFullRowPolicy(int width, int height, int gutter, int gap) {
        if (mClock == null) return;
        boolean stacked = mMode.showsNotifications()
            && mPinnedCount >= TopPaneSlotMode.MAX_PINNED;
        int normalHeaderInset = stacked ? mClockBounds.width() + gap : 0;
        int clockDesired = Math.max(1, Math.round(mClock.contentWidth()));
        int notificationDesired = mNotificationBounds.isEmpty() ? 0
            : Math.max(Math.round(dp(112f)), Math.min(mNotificationBounds.width(),
                Math.round(width * .42f)));
        int mediaDesired = mMediaBounds.isEmpty() ? 0
            : Math.max(Math.round(dp(112f)), Math.min(mMediaBounds.width(),
                Math.round(width * .42f)));
        TopPaneFullRowPolicy.Result result = TopPaneFullRowPolicy.calculate(mMode, mPinnedCount,
            width, height, gutter, gap, clockDesired, notificationDesired, mediaDesired,
            new Rect(mClockBounds), new Rect(mNotificationBounds), new Rect(mMediaBounds),
            mFullExpansionProgress, getLayoutDirection() == LAYOUT_DIRECTION_RTL);
        mClockBounds.set(result.clock);
        mNotificationBounds.set(result.notifications);
        mMediaBounds.set(result.media);
        measureExact(mClock, mClockBounds);
        if (mNotifications != null && !mNotificationBounds.isEmpty()) {
            float p = Float.isFinite(mFullExpansionProgress)
                ? Math.max(0f, Math.min(1f, mFullExpansionProgress)) : 0f;
            mNotifications.setHeaderInsetStart(normalHeaderInset * (1f - p));
            measureExact(mNotifications, mNotificationBounds);
        }
        if (mMedia != null && !mMediaBounds.isEmpty()) measureExact(mMedia, mMediaBounds);
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
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
