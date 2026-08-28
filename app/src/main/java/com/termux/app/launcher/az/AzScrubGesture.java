package com.termux.app.launcher.az;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The A–Z scrub gesture's decision machine: one finger on the letter row, three things it can
 * mean, and the timers that decide when a dwell at the apps row's edge turns into a page flip.
 *
 * <p>Pure: no {@code View}, no {@code Context}, no {@code MotionEvent}, no {@code SystemClock} and
 * no {@code Handler}. {@code AzScrubRowView.ScrubCallback} feeds the activity a touch sample; the
 * activity snapshots the three row rectangles into a {@link Geometry} and asks this class what the
 * sample <em>means</em>. Everything that <em>happens</em> as a result — the FX layers, the
 * suggestion bar's preview and focus, the {@code Choreographer} frame loop that drives edge
 * paging — stays in the activity, which reads it off the returned {@link Decision}.
 *
 * <p>Three properties are worth more than the individual thresholds:
 *
 * <ul>
 *   <li><b>Intent comes from recent motion, not displacement.</b> {@link #mRecentMotionDx}/
 *       {@code Dy} is an exponentially smoothed pointer velocity with a fixed time constant, so
 *       the classification is independent of the touch controller's sampling rate, and a diagonal
 *       thumb arc out of a long horizontal scrub can lock upward without a vertical climb.
 *   <li><b>Locks are sticky.</b> Leaving {@link Mode#UPWARD_LOCKED} or
 *       {@link Mode#ICON_TRACKING_LOCKED} needs deliberate downward motion, not position drift
 *       near a row boundary while the thumb wanders sideways.
 *   <li><b>An edge page fires once per entry.</b> After a flip the edge is latched
 *       ({@code requiresReentry}) until the finger leaves the edge, on top of a cooldown window,
 *       so a parked thumb pages at the repeat cadence rather than every frame.
 * </ul>
 */
public final class AzScrubGesture {

    /** Which of the three things a touch on the letter row currently means. */
    public enum Mode {
        /** No gesture in flight. */
        IDLE,
        /** Scrubbing letters along the row; the preview follows the finger. */
        AZ_TRACKING,
        /** A letter is locked and the finger has climbed off the row. */
        UPWARD_LOCKED,
        /** A letter is locked and the finger is picking an icon out of the apps row. */
        ICON_TRACKING_LOCKED
    }

    /** The touch phase, mirroring {@code AzScrubRowView.GesturePhase}. */
    public enum Phase { DOWN, MOVE, UP }

    /** Which side of the apps row the finger is resting against. */
    public enum Edge { NONE, LEFT, RIGHT }

    /** How the letter row should render itself, mirroring {@code AzScrubRowView.InteractionMode}. */
    public enum Track { WAVE, INLINE_EMPHASIS }

    /** What the activity should do with its edge-paging frame loop. */
    public enum EdgeAction {
        /** Tear the loop down. */
        STOP,
        /** Keep the loop as it is but show no dwell progress — latched or cooling down. */
        SUPPRESS,
        /** The loop is already running on this edge; just render the dwell progress. */
        CONTINUE,
        /** Start dwelling on a new edge: install the frame callback. */
        START
    }

    /** What the activity should do on one edge-paging frame. */
    public enum FrameAction {
        /** The finger left the edge the loop was started for; re-resolve focus and re-plan. */
        REFOCUS,
        /** Not yet: render the progress and post another frame. */
        WAIT,
        /** Flip the page by {@link EdgeFrame#pageDelta}, then wait out the repeat interval. */
        PAGE
    }

    /** Monotonic time source; {@code SystemClock::uptimeMillis} in production. */
    public interface Clock {
        long uptimeMillis();
    }

    /** How long the finger must rest against an edge before the first page flip. */
    public static final long EDGE_PAGE_INITIAL_DELAY_MS = 560L;
    /** How long after a flip before the edge is re-examined, i.e. the repeat cadence. */
    public static final long EDGE_PAGE_REPEAT_INTERVAL_MS = 420L;
    /** Dead window after a flip in which no further flip may fire. */
    public static final long EDGE_PAGE_COOLDOWN_MS = 520L;
    /** How long after a release the overflow affordance is refreshed for the preview timeout. */
    public static final long PREVIEW_TIMEOUT_REFRESH_MS = 5200L;

    /** How high up the row the finger must be, as a fraction of row height, to lock upward. */
    public static final float UPWARD_LOCK_TOUCH_Y_RATIO = 0.60f;
    /** How far back down the row the finger must come, as a fraction of row height, to unlock. */
    public static final float RETURN_TOUCH_Y_RATIO = 0.55f;
    // Direction ratios compare against the smoothed RECENT motion vector, not displacement from
    // touch-down: after a long horizontal letter scrub the old cumulative test demanded a
    // near-vertical climb before the upward lock could engage.
    /** How much the recent motion must be dominated by its upward component to lock. */
    public static final float UPWARD_DIRECTION_RATIO = 0.45f;
    /** How much the recent motion must be dominated by its downward component to unlock. */
    public static final float RETURN_DIRECTION_RATIO = 0.5f;
    /** Time constant for recent pointer velocity; independent of touch sampling rate. */
    public static final float RECENT_MOTION_TAU_MS = 50f;
    /**
     * How much a scrub must be dominated by its horizontal component for the upward-travel
     * reference to keep re-anchoring, so the climb is measured from where the finger turned.
     */
    public static final float SCRUB_HORIZONTAL_DOMINANCE = 1.3f;

    /** The pinned-apps glyph that ends a scrub instead of selecting a letter. */
    public static final char PINNED_APPS_SYMBOL = '\u2606';
    /** The letter a cleared lock reads as. */
    public static final char NO_LETTER = '#';

    /** An immutable, {@code RectF}-shaped rectangle in raw (screen) coordinates. */
    public static final class Bounds {

        /** The "not laid out / not shown" rectangle, which every containment test rejects. */
        public static final Bounds EMPTY = new Bounds(0f, 0f, 0f, 0f);

        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        /** Matches {@code RectF.isEmpty()}. */
        public boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }
    }

    /**
     * The layout the gesture is being judged against, sampled once per touch sample.
     *
     * <p>The three rectangles are the {@code isShown()}-gated ones the activity already keeps for
     * its FX layers; {@link #azRowLeftRaw}/{@link #azRowTopRaw}/{@link #azRowHeightPx} are the
     * ungated view metrics the anchor arithmetic and the row-height thresholds use, which is why
     * they are passed separately rather than read off {@link #azRow}.
     */
    public static final class Geometry {

        /** The letter row's left edge on screen, whether or not the row is shown. */
        public final float azRowLeftRaw;
        /** The letter row's top edge on screen, whether or not the row is shown. */
        public final float azRowTopRaw;
        /** The letter row's height, whether or not the row is shown. */
        public final float azRowHeightPx;
        /** The extra-keys row's height, or 0 when there is none; a fallback is derived from it. */
        public final float extraKeysHeightPx;
        /** The letter row's shown bounds, source of the return band. */
        @NonNull public final Bounds azRow;
        /** The suggestion bar's shown bounds, source of the icon corridor and capture wedge. */
        @NonNull public final Bounds appsRow;
        /** The extra-keys row's shown bounds, which extends the return band downwards. */
        @NonNull public final Bounds extraKeys;
        /** {@code DisplayMetrics.density}, for the dp-sized tolerances. */
        public final float density;

        public Geometry(float azRowLeftRaw, float azRowTopRaw, float azRowHeightPx,
                        float extraKeysHeightPx, @NonNull Bounds azRow, @NonNull Bounds appsRow,
                        @NonNull Bounds extraKeys, float density) {
            this.azRowLeftRaw = azRowLeftRaw;
            this.azRowTopRaw = azRowTopRaw;
            this.azRowHeightPx = azRowHeightPx;
            this.extraKeysHeightPx = extraKeysHeightPx;
            this.azRow = azRow;
            this.appsRow = appsRow;
            this.extraKeys = extraKeys;
            this.density = density;
        }

        float dp(float dp) {
            return dp * density;
        }

        /** The row height every threshold is a fraction of; never zero. */
        float rowHeight() {
            return Math.max(1f, azRowHeightPx);
        }

        /** The extra-keys height, falling back to a row-and-a-bit when there is no toolbar. */
        float extraKeysHeight() {
            return extraKeysHeightPx > 0f ? extraKeysHeightPx : (rowHeight() * 1.2f);
        }
    }

    /**
     * What one touch sample means. Every field is something the activity applies; nothing here
     * remembers state, and the machine has already advanced by the time this is returned.
     */
    public static final class Decision {

        /**
         * The pinned-apps glyph: clear the focused entry and the preview, reset, and do nothing
         * else. Every other field is at its neutral value.
         */
        public final boolean pinnedSymbolReset;
        /** The mode the machine is in after this sample. */
        @NonNull public final Mode mode;
        /** How the letter row should render, or null to leave its current rendering alone. */
        @Nullable public final Track track;
        /** True when {@link #lockedInlineLetter} should be pushed to the letter row. */
        public final boolean applyLockedInline;
        /** The letter to emphasise inline, or {@link #NO_INLINE_LETTER} to clear it. */
        public final char lockedInlineLetter;
        /** True when the suggestion bar's focused entry should be cleared — an unlock. */
        public final boolean clearFocusedEntry;
        /** True when {@link #previewLetter}/{@link #previewSelectionIndex} should be persisted. */
        public final boolean persistPreview;
        /** The letter whose preview to persist. */
        public final char previewLetter;
        /** The selection index within that letter's entries. */
        public final int previewSelectionIndex;
        /** True when the activity should resolve drag focus against the apps row. */
        public final boolean requestFocusResolve;
        /** The letter the FX overlay should highlight — the locked one while locked. */
        public final char overlayLetter;
        /** True on {@code UP}: launch the focused entry if there is one, then reset. */
        public final boolean releasing;

        /** {@link #lockedInlineLetter}'s "clear it" value. */
        public static final char NO_INLINE_LETTER = '\0';

        Decision(boolean pinnedSymbolReset, @NonNull Mode mode, @Nullable Track track,
                 boolean applyLockedInline, char lockedInlineLetter, boolean clearFocusedEntry,
                 boolean persistPreview, char previewLetter, int previewSelectionIndex,
                 boolean requestFocusResolve, char overlayLetter, boolean releasing) {
            this.pinnedSymbolReset = pinnedSymbolReset;
            this.mode = mode;
            this.track = track;
            this.applyLockedInline = applyLockedInline;
            this.lockedInlineLetter = lockedInlineLetter;
            this.clearFocusedEntry = clearFocusedEntry;
            this.persistPreview = persistPreview;
            this.previewLetter = previewLetter;
            this.previewSelectionIndex = previewSelectionIndex;
            this.requestFocusResolve = requestFocusResolve;
            this.overlayLetter = overlayLetter;
            this.releasing = releasing;
        }

        static Decision pinnedReset() {
            return new Decision(true, Mode.IDLE, null, false, NO_INLINE_LETTER, false, false,
                NO_LETTER, 0, false, PINNED_APPS_SYMBOL, false);
        }
    }

    /** What to do with the edge-paging loop for the focus result just resolved. */
    public static final class EdgeIntake {

        @NonNull public final EdgeAction action;
        /** The dwell ring's progress to render, 0..1. */
        public final float dwellProgress;

        EdgeIntake(@NonNull EdgeAction action, float dwellProgress) {
            this.action = action;
            this.dwellProgress = dwellProgress;
        }
    }

    /** What to do on one frame of the edge-paging loop. */
    public static final class EdgeFrame {

        @NonNull public final FrameAction action;
        /** The dwell ring's progress to render before anything else, 0..1. */
        public final float dwellProgress;
        /** -1 or +1 on {@link FrameAction#PAGE}, 0 otherwise. */
        public final int pageDelta;

        EdgeFrame(@NonNull FrameAction action, float dwellProgress, int pageDelta) {
            this.action = action;
            this.dwellProgress = dwellProgress;
            this.pageDelta = pageDelta;
        }
    }

    @NonNull private final Clock mClock;

    @NonNull private Mode mMode = Mode.IDLE;
    private boolean mActive = false;

    private char mLockedLetter = NO_LETTER;
    private int mLockedSelectionIndex = 0;
    private boolean mHasLockedSelection = false;
    private boolean mHasPreviewAnchor = false;
    private char mPreviewAnchorLetter = NO_LETTER;
    private int mPreviewAnchorSelectionIndex = 0;

    private float mRecentMotionDx = 0f;
    private float mRecentMotionDy = 0f;
    private long mLastMotionEventTimeMs = 0L;
    private float mUpwardTravelRefY = 0f;
    private float mLastScrubTouchX = 0f;
    private float mLastScrubTouchY = 0f;

    private float mLastRawX = 0f;
    private float mLastRawY = 0f;
    private float mLastAnchorRawX = 0f;
    private float mLastAnchorRawY = 0f;
    private float mLockedAnchorRawX = 0f;
    private float mLockedAnchorRawY = 0f;

    @NonNull private Edge mEdgePagingEdge = Edge.NONE;
    private long mEdgeDwellStartUptimeMs = 0L;
    private long mEdgePageCooldownUntilUptimeMs = 0L;
    private boolean mEdgeRequiresReentry = false;

    public AzScrubGesture(@NonNull Clock clock) {
        mClock = clock;
    }

    /** @return the mode the machine is in. */
    @NonNull
    public Mode mode() {
        return mMode;
    }

    /** @return true between the first sample of a scrub and its reset. */
    public boolean isActive() {
        return mActive;
    }

    /** @return the letter the lock is holding, or {@link #NO_LETTER}. */
    public char lockedLetter() {
        return mLockedLetter;
    }

    /** @return the selection index the lock is holding. */
    public int lockedSelectionIndex() {
        return mLockedSelectionIndex;
    }

    /** @return true while a lock holds a selection. */
    public boolean hasLockedSelection() {
        return mHasLockedSelection;
    }

    /** @return the last sample's raw X, which the FX layers are drawn from. */
    public float lastRawX() {
        return mLastRawX;
    }

    /** @return the last sample's raw Y, which the FX layers are drawn from. */
    public float lastRawY() {
        return mLastRawY;
    }

    /** @return the edge the paging loop is dwelling on. */
    @NonNull
    public Edge edgePagingEdge() {
        return mEdgePagingEdge;
    }

    /** @return true while the finger must leave the edge before it may page again. */
    public boolean edgeRequiresReentry() {
        return mEdgeRequiresReentry;
    }

    /** The first sample of a scrub. */
    @NonNull
    public Decision onDown(char letter, int selectionIndex, float touchX, float touchY,
                           float rawX, float rawY, long eventTimeMs, @NonNull Geometry geometry) {
        return evaluate(letter, selectionIndex, touchX, touchY, rawX, rawY, eventTimeMs,
            Phase.DOWN, geometry);
    }

    /** A sample while the finger is down. */
    @NonNull
    public Decision onMove(char letter, int selectionIndex, float touchX, float touchY,
                           float rawX, float rawY, long eventTimeMs, @NonNull Geometry geometry) {
        return evaluate(letter, selectionIndex, touchX, touchY, rawX, rawY, eventTimeMs,
            Phase.MOVE, geometry);
    }

    /** The releasing sample; {@link Decision#releasing} is set on what comes back. */
    @NonNull
    public Decision onUp(char letter, int selectionIndex, float touchX, float touchY,
                         float rawX, float rawY, long eventTimeMs, @NonNull Geometry geometry) {
        return evaluate(letter, selectionIndex, touchX, touchY, rawX, rawY, eventTimeMs,
            Phase.UP, geometry);
    }

    /** {@code ACTION_CANCEL}: the stream was taken away, so drop everything. */
    public void onCancel() {
        reset();
    }

    /**
     * Drops the gesture back to {@link Mode#IDLE}.
     *
     * <p>Deliberately keeps two things: the last raw point, which the FX layers are still being
     * drawn from as they clear, and the edge cooldown deadline, so a release-and-regrab inside the
     * cooldown window cannot sneak a second page flip out of one dwell.
     */
    public void reset() {
        stopEdgePaging();
        mActive = false;
        mMode = Mode.IDLE;
        mLockedLetter = NO_LETTER;
        mLockedSelectionIndex = 0;
        mHasLockedSelection = false;
        mHasPreviewAnchor = false;
    }

    @NonNull
    private Decision evaluate(char letter, int selectionIndex, float touchX, float touchY,
                              float rawX, float rawY, long eventTimeMs, @NonNull Phase phase,
                              @NonNull Geometry g) {
        Track track = null;
        boolean applyLockedInline = false;
        char lockedInline = Decision.NO_INLINE_LETTER;

        if (phase == Phase.DOWN) {
            mMode = Mode.AZ_TRACKING;
            mHasLockedSelection = false;
            mHasPreviewAnchor = false;
            mRecentMotionDx = 0f;
            mRecentMotionDy = 0f;
            mLastMotionEventTimeMs = eventTimeMs;
            mUpwardTravelRefY = touchY;
            mLastScrubTouchX = touchX;
            mLastScrubTouchY = touchY;
            track = Track.WAVE;
            applyLockedInline = true;
        } else {
            // Smooth pointer velocity by elapsed event time: intent classification below reads its
            // direction, so behavior stays consistent across touch-controller sampling rates.
            long dtMs = Math.max(1L, eventTimeMs - mLastMotionEventTimeMs);
            float eventVelocityX = (touchX - mLastScrubTouchX) / dtMs;
            float eventVelocityY = (touchY - mLastScrubTouchY) / dtMs;
            float alpha = (float) (1d - Math.exp(-dtMs / RECENT_MOTION_TAU_MS));
            mRecentMotionDx += (eventVelocityX - mRecentMotionDx) * alpha;
            mRecentMotionDy += (eventVelocityY - mRecentMotionDy) * alpha;
            mLastMotionEventTimeMs = eventTimeMs;
            mLastScrubTouchX = touchX;
            mLastScrubTouchY = touchY;
            // While still letter-scrubbing horizontally, keep re-anchoring the upward-travel
            // reference so the climb is measured from where the finger actually turned upward.
            if (mMode == Mode.AZ_TRACKING
                && Math.abs(mRecentMotionDx) > Math.abs(mRecentMotionDy) * SCRUB_HORIZONTAL_DOMINANCE) {
                mUpwardTravelRefY = touchY;
            }
        }

        mLastRawX = rawX;
        mLastRawY = rawY;
        mLastAnchorRawX = g.azRowLeftRaw + touchX;
        mLastAnchorRawY = g.azRowTopRaw + (g.azRowHeightPx * 0.5f);

        if (letter == PINNED_APPS_SYMBOL) {
            return Decision.pinnedReset();
        }

        mActive = true;

        float rowHeight = g.rowHeight();
        float filterUpperBound = -(rowHeight * 0.10f);
        float filterLowerBound = rowHeight + g.extraKeysHeight() + (rowHeight * 0.25f);
        float unlockThreshold = rowHeight * RETURN_TOUCH_Y_RATIO;
        float unlockMaxBound = filterLowerBound + (rowHeight * 0.18f);
        float minUpwardTravel = Math.max(g.dp(10f), rowHeight * 0.22f);
        // Intent from the smoothed recent motion vector, travel from the rolling upward reference:
        // a diagonal thumb arc out of a horizontal scrub locks upward without a vertical climb.
        boolean recentUpwardDominant = -mRecentMotionDy
            >= Math.abs(mRecentMotionDx) * UPWARD_DIRECTION_RATIO;
        boolean upwardIntent = touchY <= (rowHeight * UPWARD_LOCK_TOUCH_Y_RATIO)
            && (mUpwardTravelRefY - touchY) >= minUpwardTravel
            && recentUpwardDominant;
        // Once the drag starts on the AZ row, keep horizontal letter filtering captured below it.
        // This matches the visual wave tracking and avoids requiring exact vertical placement.
        boolean withinAzFilterBand = touchY >= filterUpperBound;
        boolean enteringUpwardLock = upwardIntent;
        boolean enteringIconTrack = isInAppsRowCorridor(g, rawY) || isInCaptureWedge(g, rawX, rawY);
        // Locked states are sticky: releasing them needs deliberate downward motion, not mere
        // position drift near the row boundary while the thumb wanders sideways.
        boolean recentDownwardDominant = mRecentMotionDy > 0f
            && mRecentMotionDy >= Math.abs(mRecentMotionDx) * RETURN_DIRECTION_RATIO;
        boolean returningToUpwardTrack = recentDownwardDominant
            && touchY >= unlockThreshold && touchY <= unlockMaxBound;
        boolean returningToIconTrack = recentDownwardDominant
            && !isInAppsRowCorridor(g, rawY) && !isInCaptureWedge(g, rawX, rawY)
            && isInReturnBand(g, rawY);

        boolean persistPreview = false;
        boolean clearFocusedEntry = false;

        if (mMode == Mode.AZ_TRACKING) {
            if (enteringIconTrack && mHasPreviewAnchor && phase != Phase.UP) {
                lockAnchor(letter, selectionIndex, Mode.ICON_TRACKING_LOCKED);
                persistPreview = true;
                track = Track.INLINE_EMPHASIS;
                applyLockedInline = true;
                lockedInline = Character.toUpperCase(mLockedLetter);
            } else if (enteringUpwardLock) {
                lockAnchor(letter, selectionIndex, Mode.UPWARD_LOCKED);
                persistPreview = true;
                track = Track.INLINE_EMPHASIS;
                applyLockedInline = true;
                lockedInline = Character.toUpperCase(mLockedLetter);
            } else if (withinAzFilterBand || phase == Phase.DOWN) {
                recordPreviewAnchor(letter, selectionIndex);
                persistPreview = true;
            }
        } else if (mMode == Mode.UPWARD_LOCKED && mHasLockedSelection) {
            if (returningToUpwardTrack && phase != Phase.UP) {
                mMode = Mode.AZ_TRACKING;
                mHasLockedSelection = false;
                track = Track.WAVE;
                applyLockedInline = true;
                clearFocusedEntry = true;
                if (withinAzFilterBand) {
                    recordPreviewAnchor(letter, selectionIndex);
                } else {
                    recordPreviewAnchor(mLockedLetter, mLockedSelectionIndex);
                }
                persistPreview = true;
            } else {
                if (enteringIconTrack) {
                    mMode = Mode.ICON_TRACKING_LOCKED;
                }
                recordPreviewAnchor(mLockedLetter, mLockedSelectionIndex);
                persistPreview = true;
                applyLockedInline = true;
                lockedInline = Character.toUpperCase(mLockedLetter);
            }
        } else if (mMode == Mode.ICON_TRACKING_LOCKED && mHasLockedSelection) {
            if (returningToIconTrack && phase != Phase.UP) {
                mMode = Mode.AZ_TRACKING;
                mHasLockedSelection = false;
                track = Track.WAVE;
                applyLockedInline = true;
                clearFocusedEntry = true;
                recordPreviewAnchor(mLockedLetter, mLockedSelectionIndex);
                persistPreview = true;
            } else {
                recordPreviewAnchor(mLockedLetter, mLockedSelectionIndex);
                persistPreview = true;
                applyLockedInline = true;
                lockedInline = Character.toUpperCase(mLockedLetter);
            }
        }

        boolean requestFocusResolve = mMode == Mode.ICON_TRACKING_LOCKED;
        char overlayLetter =
            (mMode == Mode.UPWARD_LOCKED || mMode == Mode.ICON_TRACKING_LOCKED) && mHasLockedSelection
                ? mLockedLetter
                : letter;

        return new Decision(false, mMode, track, applyLockedInline, lockedInline, clearFocusedEntry,
            persistPreview, mPreviewAnchorLetter, mPreviewAnchorSelectionIndex, requestFocusResolve,
            overlayLetter, phase == Phase.UP);
    }

    private void recordPreviewAnchor(char letter, int selectionIndex) {
        mPreviewAnchorLetter = letter;
        mPreviewAnchorSelectionIndex = selectionIndex;
        mHasPreviewAnchor = true;
    }

    /**
     * Freezes the selection the lock will hold: the preview anchor if the scrub ever settled on
     * one, otherwise the letter under the finger right now.
     */
    private void lockAnchor(char fallbackLetter, int fallbackSelectionIndex, @NonNull Mode target) {
        if (mHasPreviewAnchor) {
            mLockedLetter = mPreviewAnchorLetter;
            mLockedSelectionIndex = mPreviewAnchorSelectionIndex;
        } else {
            mLockedLetter = fallbackLetter;
            mLockedSelectionIndex = fallbackSelectionIndex;
        }
        recordPreviewAnchor(mLockedLetter, mLockedSelectionIndex);
        mMode = target;
        mHasLockedSelection = true;
        mLockedAnchorRawX = mLastAnchorRawX;
        mLockedAnchorRawY = mLastAnchorRawY;
    }

    /** The band around the apps row inside which the finger is picking icons. */
    private static boolean isInAppsRowCorridor(@NonNull Geometry g, float rawY) {
        if (g.appsRow.isEmpty()) {
            return false;
        }
        float topTolerance = g.dp(2f);
        float bottomTolerance = g.dp(4f);
        return rawY >= (g.appsRow.top - topTolerance) && rawY <= (g.appsRow.bottom + bottomTolerance);
    }

    /**
     * The cone that carries a locked letter up into the apps row. Wide enough at the base for a
     * natural thumb arc (~±45°) instead of demanding a straight vertical rise out of the letter.
     */
    private boolean isInCaptureWedge(@NonNull Geometry g, float rawX, float rawY) {
        if (!mHasLockedSelection || g.appsRow.isEmpty()) {
            return false;
        }
        float startY = mLockedAnchorRawY - g.dp(4f);
        float topLimit = g.appsRow.top - g.dp(2f);
        float bottomLimit = g.appsRow.bottom + g.dp(4f);
        if (rawY > startY || rawY < topLimit || rawY > bottomLimit) {
            return false;
        }
        float wedgeTravel = Math.max(g.dp(24f), startY - topLimit);
        float progress = Math.max(0f, Math.min(1f, (startY - rawY) / wedgeTravel));
        float targetHalfWidth = Math.max(g.dp(40f), g.appsRow.width() * 0.18f);
        float halfWidth = g.dp(22f) + (targetHalfWidth * progress);
        return Math.abs(rawX - mLockedAnchorRawX) <= halfWidth;
    }

    /** The band around the letter row (and the extra keys under it) that releases an icon lock. */
    private static boolean isInReturnBand(@NonNull Geometry g, float rawY) {
        if (g.azRow.isEmpty()) {
            return false;
        }
        float top = g.azRow.top - g.dp(10f);
        float bottom = g.azRow.bottom + g.dp(12f);
        if (!g.extraKeys.isEmpty()) {
            bottom = Math.max(bottom, g.extraKeys.bottom + g.dp(10f));
        }
        return rawY >= top && rawY <= bottom;
    }

    /**
     * Plans the edge-paging loop for the edge the focus result just reported.
     *
     * @param edge         the edge the focus result is resting against
     * @param loopRunning  whether the activity's frame callback is currently installed
     */
    @NonNull
    public EdgeIntake onEdgeFocus(@NonNull Edge edge, boolean loopRunning) {
        if (!mActive || mMode != Mode.ICON_TRACKING_LOCKED) {
            return new EdgeIntake(EdgeAction.STOP, 0f);
        }
        if (edge == Edge.NONE) {
            // Leaving the edge is what clears the latch, which is what makes one dwell page once.
            mEdgeRequiresReentry = false;
            return new EdgeIntake(EdgeAction.STOP, 0f);
        }
        long now = mClock.uptimeMillis();
        if (mEdgeRequiresReentry || now < mEdgePageCooldownUntilUptimeMs) {
            return new EdgeIntake(EdgeAction.SUPPRESS, 0f);
        }
        if (loopRunning && mEdgePagingEdge == edge) {
            return new EdgeIntake(EdgeAction.CONTINUE, edgeDwellProgress(now));
        }
        stopEdgePaging();
        mEdgePagingEdge = edge;
        mEdgeDwellStartUptimeMs = now;
        return new EdgeIntake(EdgeAction.START, edgeDwellProgress(now));
    }

    /**
     * Advances the edge-paging loop by one frame.
     *
     * @param freshEdge the edge a freshly resolved focus result reports for the last raw point
     */
    @NonNull
    public EdgeFrame onEdgeFrame(@NonNull Edge freshEdge) {
        if (freshEdge != mEdgePagingEdge) {
            return new EdgeFrame(FrameAction.REFOCUS, 0f, 0);
        }
        long now = mClock.uptimeMillis();
        if (now < mEdgePageCooldownUntilUptimeMs || mEdgeRequiresReentry) {
            return new EdgeFrame(FrameAction.WAIT, 0f, 0);
        }
        long dwellMs = now - mEdgeDwellStartUptimeMs;
        float progress = edgeDwellProgress(now);
        if (dwellMs < EDGE_PAGE_INITIAL_DELAY_MS) {
            return new EdgeFrame(FrameAction.WAIT, progress, 0);
        }
        int pageDelta = mEdgePagingEdge == Edge.LEFT ? -1 : 1;
        mEdgePageCooldownUntilUptimeMs = now + EDGE_PAGE_COOLDOWN_MS;
        mEdgeRequiresReentry = true;
        return new EdgeFrame(FrameAction.PAGE, progress, pageDelta);
    }

    /** Forgets the edge being dwelled on; the cooldown deadline deliberately survives. */
    public void stopEdgePaging() {
        mEdgePagingEdge = Edge.NONE;
        mEdgeDwellStartUptimeMs = 0L;
        mEdgeRequiresReentry = false;
    }

    /** @return how far through the initial dwell delay the edge is, 0..1. */
    public float edgeDwellProgress(long nowMs) {
        if (mEdgeDwellStartUptimeMs <= 0L) {
            return 0f;
        }
        return Math.min(1f, (nowMs - mEdgeDwellStartUptimeMs) / (float) EDGE_PAGE_INITIAL_DELAY_MS);
    }
}
