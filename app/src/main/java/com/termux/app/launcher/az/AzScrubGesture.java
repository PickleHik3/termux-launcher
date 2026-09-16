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
     *
     * <p>{@link #trackSign} is the one thing that was an assumption rather than a measurement: the
     * matches used to be <em>always</em> on the away side of the letters, because the letters were
     * always the dock's row with the pinned apps directly above them. It is
     * {@code AzPreviewTargetPolicy.Side.sign} now, so the row may stand anywhere in the edge's
     * stack, on either side of the letters, and every threshold below still points at it.
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
        /** The extra-keys row's shown bounds; they extend the return band while they lie beyond
         *  the letters, on the far side from the matches. */
        @NonNull public final Bounds extraKeys;
        /** {@code DisplayMetrics.density}, for the dp-sized tolerances. */
        public final float density;
        /**
         * Which way the icon track lies from the letters in the canonical frame: {@code +1} away
         * from the screen edge the bar stands on — the arrangement the scrub was written for — and
         * {@code -1} past the letters towards it.
         */
        public final float trackSign;

        /** The shipped arrangement: the track on the away side of the letters. */
        public Geometry(float azRowLeftRaw, float azRowTopRaw, float azRowHeightPx,
                        float extraKeysHeightPx, @NonNull Bounds azRow, @NonNull Bounds appsRow,
                        @NonNull Bounds extraKeys, float density) {
            this(azRowLeftRaw, azRowTopRaw, azRowHeightPx, extraKeysHeightPx, azRow, appsRow,
                extraKeys, density, 1f);
        }

        public Geometry(float azRowLeftRaw, float azRowTopRaw, float azRowHeightPx,
                        float extraKeysHeightPx, @NonNull Bounds azRow, @NonNull Bounds appsRow,
                        @NonNull Bounds extraKeys, float density, float trackSign) {
            this.azRowLeftRaw = azRowLeftRaw;
            this.azRowTopRaw = azRowTopRaw;
            this.azRowHeightPx = azRowHeightPx;
            this.extraKeysHeightPx = extraKeysHeightPx;
            this.azRow = azRow;
            this.appsRow = appsRow;
            this.extraKeys = extraKeys;
            this.density = density;
            this.trackSign = trackSign < 0f ? -1f : 1f;
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

        /**
         * A touch's depth into the bar measured <em>from the face the matches are on</em>, growing
         * away from them. It is the letter row's own {@code touchY} in the shipped arrangement and
         * its mirror when the matches are on the other side, which is what lets one set of
         * thresholds read both.
         */
        float trackDepth(float touchY) {
            return trackSign > 0f ? touchY : rowHeight() - touchY;
        }

        /** How far a canonical displacement carries towards the matches. */
        float towardsTrack(float canonicalDy) {
            return -canonicalDy * trackSign;
        }

        /** Whether a band lies beyond the letters on the far side from the matches. */
        boolean beyondLetters(@NonNull Bounds band) {
            if (band.isEmpty() || azRow.isEmpty()) return false;
            float bandCentre = (band.top + band.bottom) * 0.5f;
            float lettersCentre = (azRow.top + azRow.bottom) * 0.5f;
            return trackSign * (bandCentre - lettersCentre) > 0f;
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
    private float mTrackTravelRefDepth = 0f;
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
            mTrackTravelRefDepth = g.trackDepth(touchY);
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
            // While still letter-scrubbing horizontally, keep re-anchoring the travel reference so
            // the run at the matches is measured from where the finger actually turned towards them.
            if (mMode == Mode.AZ_TRACKING
                && Math.abs(mRecentMotionDx) > Math.abs(mRecentMotionDy) * SCRUB_HORIZONTAL_DOMINANCE) {
                mTrackTravelRefDepth = g.trackDepth(touchY);
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
        // Everything below is in track depth: distance into the bar from the face the matches are
        // on. For the shipped arrangement that is the letter row's own touchY, so these are the
        // numbers they always were; mirrored, the same thresholds point the other way.
        float depth = g.trackDepth(touchY);
        float filterUpperBound = -(rowHeight * 0.10f);
        float filterLowerBound = rowHeight + g.extraKeysHeight() + (rowHeight * 0.25f);
        float unlockThreshold = rowHeight * RETURN_TOUCH_Y_RATIO;
        float unlockMaxBound = filterLowerBound + (rowHeight * 0.18f);
        float minUpwardTravel = Math.max(g.dp(10f), rowHeight * 0.22f);
        // Intent from the smoothed recent motion vector, travel from the rolling reference: a
        // diagonal thumb arc out of a horizontal scrub locks without a straight run at the row.
        boolean recentUpwardDominant = g.towardsTrack(mRecentMotionDy)
            >= Math.abs(mRecentMotionDx) * UPWARD_DIRECTION_RATIO;
        boolean upwardIntent = depth <= (rowHeight * UPWARD_LOCK_TOUCH_Y_RATIO)
            && (mTrackTravelRefDepth - depth) >= minUpwardTravel
            && recentUpwardDominant;
        // Once the drag starts on the AZ row, keep horizontal letter filtering captured past its
        // far face. This matches the visual wave tracking and avoids requiring exact placement.
        boolean withinAzFilterBand = depth >= filterUpperBound;
        boolean enteringUpwardLock = upwardIntent;
        boolean enteringIconTrack = isInAppsRowCorridor(g, rawY) || isInCaptureWedge(g, rawX, rawY);
        // Locked states are sticky: releasing them needs deliberate motion back off the matches,
        // not mere position drift near the row boundary while the thumb wanders sideways.
        boolean recentDownwardDominant = g.towardsTrack(mRecentMotionDy) < 0f
            && -g.towardsTrack(mRecentMotionDy) >= Math.abs(mRecentMotionDx) * RETURN_DIRECTION_RATIO;
        boolean returningToUpwardTrack = recentDownwardDominant
            && depth >= unlockThreshold && depth <= unlockMaxBound;
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

    /**
     * The band around the icon track inside which the finger is picking icons. The tolerance is
     * wider on the side the letters are on — that is where the thumb arrives from — so it is the
     * track's near face that is generous, whichever face of it that is.
     */
    private static boolean isInAppsRowCorridor(@NonNull Geometry g, float rawY) {
        if (g.appsRow.isEmpty()) {
            return false;
        }
        float nearTolerance = g.dp(4f);
        float farTolerance = g.dp(2f);
        float top = g.appsRow.top - (g.trackSign > 0f ? farTolerance : nearTolerance);
        float bottom = g.appsRow.bottom + (g.trackSign > 0f ? nearTolerance : farTolerance);
        return rawY >= top && rawY <= bottom;
    }

    /**
     * The cone that carries a locked letter into the icon track. Wide enough at the base for a
     * natural thumb arc (~±45°) instead of demanding a straight run out of the letter, and opened
     * along {@code trackSign} so it points at the row wherever the stack put it.
     */
    private boolean isInCaptureWedge(@NonNull Geometry g, float rawX, float rawY) {
        if (!mHasLockedSelection || g.appsRow.isEmpty()) {
            return false;
        }
        float sign = g.trackSign;
        float startY = mLockedAnchorRawY - (sign * g.dp(4f));
        float farLimit = sign > 0f ? g.appsRow.top - g.dp(2f) : g.appsRow.bottom + g.dp(2f);
        float nearLimit = sign > 0f ? g.appsRow.bottom + g.dp(4f) : g.appsRow.top - g.dp(4f);
        float travel = sign * (startY - rawY);
        if (travel < 0f) {
            return false;
        }
        if (rawY < Math.min(farLimit, nearLimit) || rawY > Math.max(farLimit, nearLimit)) {
            return false;
        }
        float wedgeTravel = Math.max(g.dp(24f), sign * (startY - farLimit));
        float progress = Math.max(0f, Math.min(1f, travel / wedgeTravel));
        float targetHalfWidth = Math.max(g.dp(40f), g.appsRow.width() * 0.18f);
        float halfWidth = g.dp(22f) + (targetHalfWidth * progress);
        return Math.abs(rawX - mLockedAnchorRawX) <= halfWidth;
    }

    /**
     * The band around the letter row — and anything standing beyond it, on the far side from the
     * matches — that releases an icon lock. The extra keys under a bottom bar are that "anything"
     * in the shipped arrangement. Keys ordered <em>between</em> the letters and the row are not:
     * a return band stretched over them would drop the lock as the finger set out across them.
     */
    private static boolean isInReturnBand(@NonNull Geometry g, float rawY) {
        if (g.azRow.isEmpty()) {
            return false;
        }
        float sign = g.trackSign;
        float near = sign > 0f ? g.azRow.top - g.dp(10f) : g.azRow.bottom + g.dp(10f);
        float far = sign > 0f ? g.azRow.bottom + g.dp(12f) : g.azRow.top - g.dp(12f);
        if (g.beyondLetters(g.extraKeys)) {
            float keysFar = sign > 0f
                ? g.extraKeys.bottom + g.dp(10f)
                : g.extraKeys.top - g.dp(10f);
            far = sign > 0f ? Math.max(far, keysFar) : Math.min(far, keysFar);
        }
        return rawY >= Math.min(near, far) && rawY <= Math.max(near, far);
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
