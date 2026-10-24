package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockMorph;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.terminal.GeometryScheduler;
import com.termux.app.wall.PaneWallPage;

/**
 * A crossing (ADR 0012): one slide of the wall between Home and the Workspace, from the first frame
 * the wall moves to the frame it lands, owned here from begin to land. The activity hands it the
 * wall's frames and applies nothing of a crossing that this does not answer.
 *
 * <p>It holds, for the crossing's life:
 * <ul>
 *   <li>both halves' resolved arrangements (#141) and the store revision they were resolved at,
 *       taken at {@link #begin}, so every reader asks {@link #arrangement} or
 *       {@link #heldArrangement} and no one reads a changing layout mid-slide; a store that moved
 *       under the slide is the landing's to apply ({@link Landing#storeMoved});</li>
 *   <li>both halves' docks at rest, worked out once at {@link #begin}, and the dock's dress as a
 *       {@link DockMorph} between them at the chrome's progress into the other half each frame,
 *       handed to the {@link Dock} to draw, so the last frame already is the arriving dress;</li>
 *   <li>the hold the slide keeps on the content: the room it was committed with, the band the
 *       chrome's live edge rises through, the keyboard and the content pre-rolled for it, the
 *       grid's paused resize, and the travel cover the pane controller applies to the window in
 *       front;</li>
 *   <li>{@link SeamCrossing}, which stays the tested answerer of what each frame shows.</li>
 * </ul>
 *
 * <p>A frame is three calls, in the order the activity applies them: {@link #frame} for the seam's
 * answers and the dock's dress, {@link #stack} once the frame's pre-rolls and the seam's layout
 * pass have run (the stack's sink or fold and its translation), and {@link #rideBar} and
 * {@link #fade} once the bar's host is laid out. A reversal, or a window chip tapped mid-slide,
 * needs nothing of its own: the rests stay and the morph reads the wall's position, so no
 * measurement jumps. {@link #land} clears everything the crossing held in one step; the stack's
 * dress, which the page's commit still reads, goes at {@link #end}.
 *
 * <p>Its adapter is only what moves: the {@link Stack}, the {@link Bar}, the {@link Cover}, the
 * {@link Keyboard} and the {@link Dock}'s row. Frames read and write fields only; nothing here
 * allocates after {@link #begin}.
 */
public final class Crossing {

    /** The accessory stack: drawn at a translation, its rows sunk or the extra keys folded. */
    public interface Stack {
        /** The stack drawn {@code dockPx} lower, of which {@code keyboardPx} is the keyboard's. */
        void translate(float dockPx, float keyboardPx);
        /** The rows' sink into the keyboard's top edge (0 puts every layer back). */
        void sink(float px);
        /** The extra keys folded by {@link #foldShare()}, the rows around them as it holds them. */
        void fold();
        /** The status content's and the side bars' travel alpha (1 puts them back). */
        void alpha(float status, float side);
    }

    /** The status bar, riding with the Workspace's page. */
    public interface Bar {
        /** The bar drawn {@code px} from its rest (0 puts it back). */
        void rideX(float px);
    }

    /** The travel cover, which the pane controller holds and applies to the window in front. */
    public interface Cover {
        void cover(int px);
    }

    /** The in-app keyboard, brought up for a slide. */
    public interface Keyboard {
        /** Bring the keyboard up below the screen for {@code target}'s half, quietly. */
        void preRoll(@NonNull PaneWallPage target);
    }

    /** The dock's pinned row, which draws the morph rather than laying it out. */
    public interface Dock {
        /** The crossing begins between these rests: once, before the first frame's dress. */
        void beginMorph(@NonNull DockLayout leaving, @NonNull DockLayout arriving);
        /** One frame's dress, drawn without a layout. The same object every frame. */
        void dress(@NonNull DockMorph morph);
        /** The crossing landed on {@code landed}'s rest: the row lays it out, once. */
        void endMorph(@NonNull DockLayout landed);
    }

    /** What a landing has left to do, read before the next crossing. The same object each time. */
    public static final class Landing {
        boolean held;
        boolean keyboardPreRolled;
        boolean contentPreRolled;
        boolean beadsMelted;
        boolean storeMoved;
        @Nullable GeometryScheduler.Transition gridHold;
        @NonNull SeamCrossing.Landing seam;

        Landing(@NonNull SeamCrossing.Landing seam) {
            this.seam = seam;
        }

        /** Whether the slide held the content: one geometry pass gives it its room now. */
        public boolean held() { return held; }
        /** Whether the keyboard was brought up for the slide rather than by the page. */
        public boolean keyboardPreRolled() { return keyboardPreRolled; }
        /** Whether the content was given the arriving room for the slide, under a paused grid. */
        public boolean contentPreRolled() { return contentPreRolled; }
        /** Whether Lens beads melted on the way; they land as the page did. */
        public boolean beadsMelted() { return beadsMelted; }
        /**
         * Whether the arrangements' store moved while the crossing held them: the chrome reads the
         * store again from here, and the landing lays it out whole.
         */
        public boolean storeMoved() { return storeMoved; }
        /** The grid's paused resize, for the settle to end after its layout; null if none. */
        @Nullable public GeometryScheduler.Transition gridHold() { return gridHold; }
        /** What the seam's crossing has left to do; nothing for a hold let go on its own. */
        @NonNull public SeamCrossing.Landing seam() { return seam; }
    }

    @NonNull private final Stack mStack;
    @NonNull private final Bar mBar;
    @NonNull private final Cover mCover;
    @NonNull private final Keyboard mKeyboard;
    @NonNull private final Dock mDock;

    private final SeamCrossing mSeam = new SeamCrossing();
    private final DockMorph mMorph = new DockMorph();

    // ---- What a crossing holds from begin ----
    @Nullable private PaneWallPage mCommitted;
    @Nullable private ResolvedArrangement mHome;
    @Nullable private ResolvedArrangement mWorkspace;
    @Nullable private DockLayout mHomeRest;
    @Nullable private DockLayout mWorkspaceRest;
    private int mRevision;
    /** The committed half standing the seam's layout, made once per layout the seam stands. */
    @Nullable private ResolvedArrangement mStanding;
    @Nullable private ResolvedArrangement mStandingFrom;

    // ---- The hold on the content ----
    private boolean mHoldsContent;
    private int mHeldReservationPx;
    private int mHeldOverlapPx;
    private boolean mKeyboardPreRolled;
    @Nullable private PaneWallPage mKeyboardPlace;
    private boolean mContentPreRolled;
    @Nullable private GeometryScheduler.Transition mGridHold;
    @Nullable private PaneWallPage mRoomArriving;
    private int mRoomChangePx;
    private int mCoverPx;

    // ---- What the frames ease ----
    private float mFoldShare = Float.NaN;
    private float mBeadShare = Float.NaN;

    private final Landing mLanding = new Landing(new SeamCrossing.Landing());
    /** What a hold let go on its own hands back for the seam: nothing to do. Never written. */
    private final SeamCrossing.Landing mNoSeam = new SeamCrossing.Landing();

    public Crossing(@NonNull Stack stack, @NonNull Bar bar, @NonNull Cover cover,
                    @NonNull Keyboard keyboard, @NonNull Dock dock) {
        mStack = stack;
        mBar = bar;
        mCover = cover;
        mKeyboard = keyboard;
        mDock = dock;
    }

    // ---- Begin and frame ----

    /**
     * The crossing begins on the first frame that reaches {@code there}'s half: both halves'
     * arrangements and their docks at rest are held from here to the landing, and the row is told
     * both rests once. Called again for a frame that reaches another page of the other half, the
     * seam reads the halves again and everything else stays as it is held (the rests passed are
     * then not read: {@link #heldRest} answers them).
     *
     * @param homeRest      Home's dock at rest, as Dock geometry answers it
     * @param homeTile      whether Home's rows carry the Drawer tile
     * @param workspaceRest the Workspace's dock at rest
     * @param workspaceTile whether the Workspace's rows carry the Drawer tile
     * @param revision      the store revision the arrangements were resolved at
     */
    public void begin(@NonNull SeamCrossing.Half here, @NonNull SeamCrossing.Half there,
                      @NonNull SeamCrossing.Stage stage,
                      @NonNull ResolvedArrangement home, @NonNull ResolvedArrangement workspace,
                      @NonNull DockLayout homeRest, boolean homeTile,
                      @NonNull DockLayout workspaceRest, boolean workspaceTile, int revision) {
        boolean begins = !crossing();
        mSeam.begin(here, there, stage);
        if (!begins) return;
        mCommitted = here.page;
        mHome = home;
        mWorkspace = workspace;
        mHomeRest = homeRest;
        mWorkspaceRest = workspaceRest;
        mRevision = revision;
        boolean fromHome = here.page == PaneWallPage.WIDGETS;
        DockLayout leaving = fromHome ? homeRest : workspaceRest;
        DockLayout arriving = fromHome ? workspaceRest : homeRest;
        mMorph.between(leaving, fromHome ? homeTile : workspaceTile, arriving,
            fromHome ? workspaceTile : homeTile);
        mDock.beginMorph(leaving, arriving);
    }

    /**
     * One frame of the crossing: the seam's answers for it ({@link SeamCrossing#frame}), and the
     * dock's dress at the chrome's progress into the other half, handed to the row to draw.
     */
    @NonNull
    public SeamCrossing.Answers frame(@NonNull Frame frame, boolean dressable,
                                      boolean bandEaseRuns, boolean fieldUnderPalette) {
        SeamCrossing.Answers answers = mSeam.frame(frame, dressable, bandEaseRuns,
            fieldUnderPalette);
        if (mCommitted != null && mMorph.active()) {
            mMorph.at(SeamTravel.otherHalfProgress(frame, mCommitted));
            mDock.dress(mMorph);
        }
        return answers;
    }

    /**
     * The stack for this frame, once its pre-rolls and the seam's layout pass have run: the rows
     * sink into the keyboard or the extra keys fold, as the seam answered, and the stack is drawn
     * at its translation, which is returned (the cover reads it, {@link #coverAt}).
     *
     * @param dockReveal how much of the dock rows the stack stands shows (the seam's)
     * @param keyboardPx the keyboard's height as the stack lays it out, 0 while it is down
     * @param dockPx     everything the stack lays out above the keyboard and the margin under it
     * @param rowsPx     the dock's rows alone above the keyboard
     */
    public float stack(@NonNull SeamCrossing.Answers seam, float dockReveal, int keyboardPx,
                       int dockPx, int rowsPx) {
        float keyboardReveal = seam.keyboardReveal();
        boolean rowsSink = seam.rowsSink(keyboardPx);
        if (seam.folds()) {
            // Only the extra keys fold: the rows on the canvas's side of them sink by their hidden
            // part, the rows between them and the screen's edge stand still (#94).
            mFoldShare = seam.extraKeysShare();
            mStack.fold();
        } else {
            mFoldShare = Float.NaN;
            mStack.sink(rowsSink ? rowsPx * (1f - clamp01(dockReveal)) : 0f);
        }
        float translation = PlaceChromeTravel.stackTranslationPx(keyboardReveal,
            rowsSink ? 1f : dockReveal, keyboardPx, dockPx);
        mStack.translate(translation, keyboardPx * (1f - clamp01(keyboardReveal)));
        return translation;
    }

    /**
     * The status bar for this frame: drawn where the Workspace's page stands whenever the seam
     * says it rides ({@link SeamCrossing#rideBar}), else at its rest. Whether it rides.
     */
    public boolean rideBar(@NonNull Frame frame, boolean hostCanRide, int wallWidthPx) {
        boolean rides = mSeam.rideBar(frame, hostCanRide);
        mBar.rideX(rides ? SeamTravel.workspaceScreenXPx(frame, wallWidthPx) : 0f);
        return rides;
    }

    /** The status content's and the side bars' alpha for this frame. */
    public void fade(float status, float side) {
        mStack.alpha(status, side);
    }

    /**
     * One frame of a keyboard swipe, which drives the same travel by its finger while the wall
     * stands still: the stack drawn {@code translationPx} down, all of it the keyboard's, and the
     * cover for it under the hold.
     */
    public void keyboardSwipe(float translationPx) {
        mStack.translate(translationPx, translationPx);
        coverAt(translationPx);
    }

    /** Whether a crossing has begun and not yet landed. */
    public boolean crossing() {
        return mCommitted != null;
    }

    /** The seam's answerer, for the chrome's reads between frames. */
    @NonNull
    public SeamCrossing seam() {
        return mSeam;
    }

    /**
     * The arrangement the chrome stands in: the committed half's, held from the crossing's begin,
     * standing the layout the seam composes from both halves while it stands one
     * ({@link SeamCrossing#layout}). At rest, {@code committed} itself.
     */
    @NonNull
    public ResolvedArrangement arrangement(@NonNull ResolvedArrangement committed) {
        ResolvedArrangement held = mCommitted == null ? committed
            : mCommitted == PaneWallPage.WIDGETS ? mHome : mWorkspace;
        if (held == null) held = committed;
        PlaceLayout seam = mSeam.layout();
        if (seam == null) return held;
        if (mStanding == null || mStandingFrom != held || mStanding.layout() != seam) {
            mStanding = held.standing(seam);
            mStandingFrom = held;
        }
        return mStanding;
    }

    /**
     * {@code home}'s half's arrangement as the crossing holds it, for every chrome reader while
     * one runs; null while none runs, and the store answers.
     */
    @Nullable
    public ResolvedArrangement heldArrangement(boolean home) {
        return home ? mHome : mWorkspace;
    }

    /** {@code home}'s half's dock at rest as the crossing worked it out; null while none runs. */
    @Nullable
    public DockLayout heldRest(boolean home) {
        return home ? mHomeRest : mWorkspaceRest;
    }

    // ---- The hold on the content ----

    /** From here to the landing, the content keeps the room it was committed with. */
    public void hold(int reservationPx) {
        if (mHoldsContent) return;
        mHoldsContent = true;
        mHeldReservationPx = reservationPx;
    }

    public boolean holdsContent() {
        return mHoldsContent;
    }

    /** The content reservation the hold keeps. */
    public int heldReservationPx() {
        return mHeldReservationPx;
    }

    /**
     * How far the content reaches under the stack as the hold's pass laid it out
     * ({@link PlaceChromeTravel#heldOverlapPx}); ignored while nothing holds.
     */
    public void heldOverlap(int px) {
        if (mHoldsContent) mHeldOverlapPx = px;
    }

    /**
     * The keyboard brought up for {@code target}'s half for the rest of the slide, under the hold
     * ({@code reservationPx} is the room it is taken at, if it begins the hold).
     */
    public void preRollKeyboard(@NonNull PaneWallPage target, int reservationPx) {
        hold(reservationPx);
        mKeyboardPreRolled = true;
        mKeyboardPlace = target;
        mKeyboard.preRoll(target);
    }

    public boolean keyboardPreRolled() {
        return mKeyboardPreRolled;
    }

    /** The page the pre-rolled keyboard travels toward; null while nothing is pre-rolled. */
    @Nullable
    public PaneWallPage keyboardPlace() {
        return mKeyboardPlace;
    }

    /**
     * The content given {@code roomGivenBackPx} more room for the rest of the slide (ADR 0003,
     * amended): the hold's reservation drops by it. Answers whether the grid's resize still has to
     * be paused ({@link #gridHold(GeometryScheduler.Transition)}).
     */
    public boolean giveRoomBack(int roomGivenBackPx, int reservationPx) {
        hold(reservationPx);
        mContentPreRolled = true;
        mHeldReservationPx = Math.max(0, mHeldReservationPx - roomGivenBackPx);
        return mGridHold == null;
    }

    public boolean contentPreRolled() {
        return mContentPreRolled;
    }

    /** The grid's paused resize under a pre-rolled content, ended after the landing's layout. */
    public void gridHold(@Nullable GeometryScheduler.Transition hold) {
        mGridHold = hold;
    }

    /** Whether the room change toward {@code arriving} is known for this crossing. */
    public boolean knowsRoomToward(@NonNull PaneWallPage arriving) {
        return arriving == mRoomArriving;
    }

    /** The room change toward the page it was last worked out for. */
    public int roomChangePx() {
        return mRoomChangePx;
    }

    /** Notes the room change toward {@code arriving}, worked out once per crossing. */
    public void roomChange(@NonNull PaneWallPage arriving, int px) {
        mRoomArriving = arriving;
        mRoomChangePx = px;
    }

    /**
     * The travel cover for a frame whose stack is drawn {@code stackTranslationPx} down: however far
     * its live top stands up into the room the content holds ({@link
     * PlaceChromeTravel#coveredRoomPx}). Only while the content is held.
     */
    public void coverAt(float stackTranslationPx) {
        if (!mHoldsContent) return;
        int px = PlaceChromeTravel.coveredRoomPx(mHeldOverlapPx, stackTranslationPx);
        mCoverPx = px;
        mCover.cover(px);
    }

    // ---- What the frames ease ----

    /** How much of the extra keys a fold shows ({@link #stack}); NaN while none runs. */
    public float foldShare() {
        return mFoldShare;
    }

    /** The beaded half's share while beads melt; NaN while nothing melts. */
    public void beadShare(float share) {
        mBeadShare = share;
    }

    public float beadShare() {
        return mBeadShare;
    }

    // ---- Land ----

    /**
     * The wall landed on {@code landed}: everything the crossing held is cleared in one step (the
     * content hold, the keyboard and content pre-rolls, the cover, the stack's translation, sink and
     * alphas, the bar's ride, the morph, the rests and the held arrangements), the row lays out the
     * landed half's rest, which its last frame already drew, and the seam's crossing lands.
     * Answers what the settle has left to do; {@code revision} is the store's now, and a store
     * that moved under the slide is the landing's to lay out ({@link Landing#storeMoved}).
     */
    @NonNull
    public Landing land(@NonNull PaneWallPage landed, int revision) {
        Landing landing = releaseInto(mLanding);
        landing.beadsMelted = !Float.isNaN(mBeadShare);
        landing.storeMoved = mCommitted != null && revision != mRevision;
        landing.seam = mSeam.land();
        mBeadShare = Float.NaN;
        mFoldShare = Float.NaN;
        mBar.rideX(0f);
        mStack.sink(0f);
        mStack.alpha(1f, 1f);
        DockLayout landedRest = landed == PaneWallPage.WIDGETS ? mHomeRest : mWorkspaceRest;
        boolean morphed = mMorph.active();
        mMorph.clear();
        if (morphed && landedRest != null) mDock.endMorph(landedRest);
        mCommitted = null;
        mHome = null;
        mWorkspace = null;
        mHomeRest = null;
        mWorkspaceRest = null;
        mRevision = 0;
        mStanding = null;
        mStandingFrom = null;
        return landing;
    }

    /**
     * The hold alone lets go, for a keyboard swipe that drove the travel while the wall stood still:
     * the content hold, the pre-rolls, the cover and the stack's translation, in one step. No seam
     * crossed, so the seam has nothing left to do.
     */
    @NonNull
    public Landing releaseHold() {
        Landing landing = releaseInto(mLanding);
        landing.beadsMelted = false;
        landing.storeMoved = false;
        landing.seam = mNoSeam;
        return landing;
    }

    @NonNull
    private Landing releaseInto(@NonNull Landing landing) {
        landing.held = mHoldsContent;
        landing.keyboardPreRolled = mKeyboardPreRolled;
        landing.contentPreRolled = mContentPreRolled;
        landing.gridHold = mGridHold;
        boolean covered = mHoldsContent || mCoverPx != 0;
        mHoldsContent = false;
        mHeldReservationPx = 0;
        mHeldOverlapPx = 0;
        mKeyboardPreRolled = false;
        mKeyboardPlace = null;
        mContentPreRolled = false;
        mRoomArriving = null;
        mRoomChangePx = 0;
        // The grid's hold is handed to the settle, which ends it after its layout.
        mGridHold = null;
        mStack.translate(0f, 0f);
        mCoverPx = 0;
        if (covered) mCover.cover(0);
        return landing;
    }

    /** After the page's commit, the stack's dress the seam kept through it lets go. */
    public void end() {
        mSeam.end();
    }

    /**
     * Whether nothing of a crossing is held: no hold, no pre-roll, no cover, no ease, no morph, no
     * rest, no arrangement, and the seam standing nothing. True between crossings.
     */
    public boolean idle() {
        return !mHoldsContent && mHeldReservationPx == 0 && mHeldOverlapPx == 0
            && !mKeyboardPreRolled && mKeyboardPlace == null && !mContentPreRolled
            && mGridHold == null && mRoomArriving == null && mRoomChangePx == 0 && mCoverPx == 0
            && Float.isNaN(mFoldShare) && Float.isNaN(mBeadShare) && !mMorph.active()
            && mCommitted == null && mHome == null && mWorkspace == null
            && mHomeRest == null && mWorkspaceRest == null && mRevision == 0
            && mSeam.layout() == null && mSeam.other() == null && mSeam.stackDressPage() == null;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
