package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import com.termux.app.wall.PagePolicy;
import com.termux.app.wall.SettleSpring;

import java.util.ArrayList;

/**
 * The one owner of where the Default drawer stands between its two pages: a single float
 * {@link #position()} in [0, 1], 0 on Apps and 1 on Categories, and the section that last settled.
 *
 * <p>Pure: no {@code View}; the settle's clock is a seam ({@link Clock}). The pages, and the
 * section switch, read this number and nothing else stores a section position.
 *
 * <p>Direction is the caller's: {@code dxPx} and velocities are positive <em>towards Categories</em>
 * (a finger moving towards the start side in LTR, the end side in RTL), so the pager is
 * layout-direction free.
 *
 * <p>A release settles as Home's pages do: {@link PagePolicy#turn} picks the page, a
 * {@link SettleSpring#page} spring carries the number there, landed with
 * {@code durationMs(SettleSpring.PAGE_REST_PX)}.
 */
public final class AppDrawerSectionPager {

    /** Told every position change, and each landing on a section. */
    public interface Listener {
        void onPositionChanged(float position);

        /**
         * The pager came to rest on {@code section}.
         *
         * @param changed true when a settle landed on a different section from the one settled
         *                before; never true for {@link #jumpTo} and {@link #cancel}, which are silent
         */
        void onSettled(@NonNull AppDrawerSection section, boolean changed);
    }

    /** Receives the clock's progress, 0 to 1; the final report is exactly 1. */
    public interface Tick {
        void onFraction(float fraction);
    }

    /** The settle's clock: runs {@code tick} for {@code durationMs} then reports 1 and stops. */
    public interface Clock {
        void start(long durationMs, @NonNull Tick tick);

        /** Stops without a final report. */
        void cancel();

        /** General > Animation speed's stretch on the spring. */
        float springTime();
    }

    private static final float EPS = 1e-4f;

    @NonNull private final Clock mClock;
    private final ArrayList<Listener> mListeners = new ArrayList<>(2);
    private final Tick mTick = this::onTick;
    private float mPosition;
    @NonNull private AppDrawerSection mSettled = AppDrawerSection.APPS;
    private boolean mDragging;
    private boolean mSettling;
    private float mDragStartPosition;
    private int mDragStartIndex;
    private float mPageWidthPx;
    @NonNull private SettleSpring mSpring = SettleSpring.page(0f, 0f, 1f);
    private long mDurationMs;
    private int mTargetIndex;

    public AppDrawerSectionPager(@NonNull Clock clock) {
        mClock = clock;
    }

    public void addListener(@NonNull Listener listener) {
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    /** 0 on Apps, 1 on Categories, between while dragged or settling. */
    public float position() {
        return mPosition;
    }

    /** The section that last settled; it changes only when a settle lands. */
    @NonNull
    public AppDrawerSection settledSection() {
        return mSettled;
    }

    public boolean isDragging() {
        return mDragging;
    }

    public boolean isSettling() {
        return mSettling;
    }

    /** True when nothing is dragged or settling and the number sits exactly on a page. */
    public boolean isAtRest() {
        return !mDragging && !mSettling && (mPosition == 0f || mPosition == 1f);
    }

    /** The page width the settle's arithmetic reads when the caller does not pass one. */
    public void setPageWidthPx(float pageWidthPx) {
        mPageWidthPx = Math.max(0f, pageWidthPx);
    }

    /** A finger takes the pager, stopping a settle where it stands. */
    public void beginDrag() {
        stopSettle();
        mDragging = true;
        mDragStartIndex = nearestIndex(mPosition);
        mDragStartPosition = mPosition;
    }

    /**
     * Puts the number {@code travelPx} from where the drag began (positive towards Categories),
     * clamped to the two pages. Absolute, so a lift that lands after a jump of the finger (moves
     * the system batched, or the lift's own position) still counts all of the way travelled.
     */
    public void dragTo(float travelPx, float pageWidthPx) {
        if (!mDragging) beginDrag();
        if (!(pageWidthPx > 0f) || Float.isNaN(travelPx)) return;
        mPageWidthPx = pageWidthPx;
        setPosition(clamp(mDragStartPosition + travelPx / pageWidthPx));
    }

    /**
     * The travel a drag that was claimed past {@code slopPx} moves the pages by: the finger's own
     * travel less the slop it spent being decided, so the claim starts the pages from rest with no
     * jump and nothing after it is lost.
     */
    public static float travelPastSlop(float fingerTravelPx, float slopPx) {
        float slop = Math.max(0f, slopPx);
        float abs = Math.abs(fingerTravelPx);
        return abs <= slop ? 0f : Math.signum(fingerTravelPx) * (abs - slop);
    }

    /** Follows the finger by {@code dxPx}, clamped to the two pages: no rubber band past an end. */
    public void dragBy(float dxPx, float pageWidthPx) {
        if (!mDragging) beginDrag();
        if (!(pageWidthPx > 0f) || Float.isNaN(dxPx)) return;
        mPageWidthPx = pageWidthPx;
        setPosition(clamp(mPosition + dxPx / pageWidthPx));
    }

    /** The finger lifts: Home's turn picks the page and the spring carries the number there. */
    public void release(float velocityPxPerSec, float pageWidthPx, float density) {
        if (!mDragging) return;
        mDragging = false;
        if (pageWidthPx > 0f) mPageWidthPx = pageWidthPx;
        int target = targetIndex(mDragStartIndex, mPosition, velocityPxPerSec, mPageWidthPx,
            density);
        startSettle(target, velocityPxPerSec);
    }

    /** Animates to {@code section} from wherever the number stands, with no throw. */
    public void settleTo(@NonNull AppDrawerSection section) {
        mDragging = false;
        startSettle(section.index(), 0f);
    }

    /** Puts the number on {@code section} at once, cancelling any drag or settle. Silent. */
    public void jumpTo(@NonNull AppDrawerSection section) {
        stopSettle();
        mDragging = false;
        boolean moved = mPosition != section.index();
        boolean sectionChanged = mSettled != section;
        mSettled = section;
        if (moved) setPosition(section.index());
        if (moved || sectionChanged) notifySettled(section, false);
    }

    /** Drops a drag or settle and goes back to the settled section, without animation. */
    public void cancel() {
        jumpTo(mSettled);
    }

    // ---------------------------------------------------------------------------------- pure

    /**
     * The page index a release lands on: {@link PagePolicy#turn} on the travel from
     * {@code startIndex} (positive towards Categories), clamped to the two pages.
     */
    public static int targetIndex(int startIndex, float position, float velocityPxPerSec,
                                  float pageWidthPx, float density) {
        float travel = (position - startIndex) * pageWidthPx;
        int turn = PagePolicy.turn(travel, velocityPxPerSec, pageWidthPx, density);
        return Math.max(0, Math.min(1, startIndex + turn));
    }

    /** The settle's spring: the number's distance from the target, in px, thrown at the release. */
    @NonNull
    public static SettleSpring settleSpring(float position, int targetIndex, float pageWidthPx,
                                            float velocityPxPerSec, float springTime) {
        return SettleSpring.page((position - targetIndex) * pageWidthPx, velocityPxPerSec,
            springTime);
    }

    /** Where the number stands {@code elapsedSeconds} into a settle to {@code targetIndex}. */
    public static float positionAt(@NonNull SettleSpring spring, int targetIndex,
                                   float pageWidthPx, float elapsedSeconds) {
        if (!(pageWidthPx > 0f)) return targetIndex;
        return clamp(targetIndex + spring.displacementAt(elapsedSeconds) / pageWidthPx);
    }

    public static float clamp(float position) {
        if (Float.isNaN(position)) return 0f;
        return Math.max(0f, Math.min(1f, position));
    }

    private static int nearestIndex(float position) {
        return position < 0.5f ? 0 : 1;
    }

    // ------------------------------------------------------------------------------- private

    private void startSettle(int target, float velocityPxPerSec) {
        stopSettle();
        mTargetIndex = target;
        float disp = (mPosition - target) * mPageWidthPx;
        if (!(mPageWidthPx > 0f) || Math.abs(disp) < EPS) {
            land();
            return;
        }
        mSpring = settleSpring(mPosition, target, mPageWidthPx, velocityPxPerSec,
            mClock.springTime());
        mDurationMs = mSpring.durationMs(SettleSpring.PAGE_REST_PX);
        mSettling = true;
        mClock.start(mDurationMs, mTick);
    }

    private void stopSettle() {
        if (!mSettling) return;
        mSettling = false;
        mClock.cancel();
    }

    private void onTick(float fraction) {
        if (!mSettling) return;
        if (fraction >= 1f) {
            mSettling = false;
            land();
            return;
        }
        setPosition(positionAt(mSpring, mTargetIndex, mPageWidthPx,
            fraction * mDurationMs / 1000f));
    }

    private void land() {
        AppDrawerSection section = AppDrawerSection.values()[mTargetIndex];
        setPosition(mTargetIndex);
        boolean changed = mSettled != section;
        mSettled = section;
        notifySettled(section, changed);
    }

    private void setPosition(float position) {
        if (position == mPosition) return;
        mPosition = position;
        for (int i = 0; i < mListeners.size(); i++) mListeners.get(i).onPositionChanged(position);
    }

    private void notifySettled(@NonNull AppDrawerSection section, boolean changed) {
        for (int i = 0; i < mListeners.size(); i++) mListeners.get(i).onSettled(section, changed);
    }
}
