package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.Dress;
import com.termux.app.dock.DockStyle;
import com.termux.app.dock.LensBeadGeometry;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.SeamTravel.DockPlan;
import com.termux.app.wall.PaneWallPage;

/**
 * One crossing of the seam between the wall's halves, Home and the Workspace's window beside it,
 * from the first frame of a slide that reaches the other half to the settle (#94). It is fed the
 * two halves' {@link Dress}es and layouts once, as it begins, and answers every frame from what
 * it read then: how much of the dock shows, whether the rows sink into a keyboard or the extra
 * keys fold, how much of the keyboard shows, which half's dress the stack wears and whether it is
 * dressed again on this frame, whether the seam stands another arrangement, how the halves' own
 * bands and beads ease, and whether the status bar rides with the Workspace's page and for which
 * page it is dressed. The activity only begins a crossing, applies the answers to its views, and
 * lands and ends it at settle; how each answer is worked out is {@link SeamTravel}'s and
 * {@link SeamDress}'s arithmetic, which this composes.
 *
 * <p>The frames ask nothing of the store or the preferences and build nothing: what a frame needs
 * is read and laid out at {@link #begin}, and the answers are one object reused frame to frame.
 *
 * <p>A frame is answered in three calls, in the order the activity's frame applies them:
 * {@link #frame} for everything the frame decides, {@link #standLayout} where the activity runs
 * the seam's layout pass (the arrangement {@link #layout} answers changes there and not before, so
 * the passes the frame runs first read the arrangement they did), and {@link #rideBar} once the
 * pass has laid the status bar out, whose host only then says whether it can ride.
 *
 * <p>The settle is two calls too: {@link #land} puts down everything but the stack's dress and
 * says what the settle has left to do, and {@link #end}, after the page's commit, lets the dress
 * go; the commit dresses everything for the page it lands on, and the stack keeps the dress it
 * travelled in until then.
 */
public final class SeamCrossing {

    /** One half as a crossing reads it, once, as it begins. */
    public static final class Half {
        @NonNull final PaneWallPage page;
        @NonNull final Dress dress;
        @NonNull final PlaceLayout layout;
        @NonNull final SeamDress.StackDress stack;
        final boolean keyboardUp;
        final int restRowsPx;

        private Half(@NonNull PaneWallPage page, @NonNull Dress dress, @NonNull PlaceLayout layout,
                     @NonNull SeamDress.StackDress stack, boolean keyboardUp, int restRowsPx) {
            this.page = page;
            this.dress = dress;
            this.layout = layout;
            this.stack = stack;
            this.keyboardUp = keyboardUp;
            this.restRowsPx = restRowsPx;
        }

        /**
         * @param dress      what the page wears ({@code DressResolver.dressOf})
         * @param layout     the arrangement the page's half stands its chrome in
         * @param stack      what the stack wears there: the dress's shape and its layout's sizes
         * @param keyboardUp whether the half keeps the in-app keyboard up, docked or not: up as it
         *                   rests for the committed half, wanted on arrival for the other; a
         *                   floating layout's keyboard is a frame of its own and not the stack's
         */
        @NonNull
        public static Half of(@NonNull PaneWallPage page, @NonNull Dress dress,
                              @NonNull PlaceLayout layout, @NonNull SeamDress.StackDress stack,
                              boolean keyboardUp) {
            return new Half(page, dress, layout, stack, keyboardUp, -1);
        }

        /**
         * @param restRowsPx the dock rows' height as the half's layout lays them out at rest, less
         *                   the extra keys row itself: each row's own air as its neighbours there
         *                   give it (the A-Z row's chin and crown, the apps row's air alone or
         *                   shared). Negative while unknown, and a fold then reads no difference.
         * @see #of(PaneWallPage, Dress, PlaceLayout, SeamDress.StackDress, boolean)
         */
        @NonNull
        public static Half of(@NonNull PaneWallPage page, @NonNull Dress dress,
                              @NonNull PlaceLayout layout, @NonNull SeamDress.StackDress stack,
                              boolean keyboardUp, int restRowsPx) {
            return new Half(page, dress, layout, stack, keyboardUp, restRowsPx);
        }
    }

    /**
     * Whether a joined Docked frame's glass is the one sheet the stack's sheets stand down on, as
     * the chrome reads it live. Asked during {@link #begin}, once the crossing has noted the other
     * half (the status bar then wears the Workspace's dress), and only where the other half's dock
     * could draw beads.
     */
    public interface FrameGlass {
        boolean standsDown();
    }

    /** What the screen stands as a crossing begins, beside the halves: read once, then held. */
    public static final class Stage {
        final boolean beadsDrawn;
        @NonNull final FrameGlass frameGlass;
        final boolean splitPanes;

        private Stage(boolean beadsDrawn, @NonNull FrameGlass frameGlass, boolean splitPanes) {
            this.beadsDrawn = beadsDrawn;
            this.frameGlass = frameGlass;
            this.splitPanes = splitPanes;
        }

        /**
         * @param beadsDrawn whether the dock draws Lens beads as the committed half lies now
         * @param frameGlass whether the stack's sheets stand down on a joined Docked frame
         * @param splitPanes whether the Workspace splits its panes, which a bar laid out over Home
         *                   for the slide needs
         */
        @NonNull
        public static Stage of(boolean beadsDrawn, @NonNull FrameGlass frameGlass,
                               boolean splitPanes) {
            return new Stage(beadsDrawn, frameGlass, splitPanes);
        }
    }

    /** One frame's answers. The same object every frame: read it before the next frame. */
    public static final class Answers {
        float dockReveal;
        float keyboardReveal;
        boolean folds;
        float extraKeysShare;
        boolean rowsSink;
        boolean relayout;
        boolean restyle;
        boolean restyleShape;
        boolean bandsMoved;
        float beadShare;
        boolean moved;
        boolean barRedressed;

        Answers() {
            clear(1f, 0f);
        }

        void clear(float dock, float keyboard) {
            dockReveal = dock;
            keyboardReveal = keyboard;
            folds = false;
            extraKeysShare = Float.NaN;
            rowsSink = false;
            relayout = false;
            restyle = false;
            restyleShape = false;
            bandsMoved = false;
            beadShare = Float.NaN;
            moved = false;
            barRedressed = false;
        }

        /** How much of the dock rows the stack stands shows: the seam's reveal, else the frame's. */
        public float dockReveal() {
            return dockReveal;
        }

        /** How much of the keyboard shows: a whole-stack dip's, else the frame's. */
        public float keyboardReveal() {
            return keyboardReveal;
        }

        /** Whether only the extra keys fold on this crossing, the rows above them standing still. */
        public boolean folds() {
            return folds;
        }

        /** How much of the extra keys row a fold shows, 0 to 1; NaN while nothing folds. */
        public float extraKeysShare() {
            return extraKeysShare;
        }

        /**
         * Whether the dock's rows sink into the keyboard's top edge instead of leaving with the
         * stack's translation, which carries the keyboard too, for a keyboard laid out
         * {@code keyboardPx} tall in the stack.
         */
        public boolean rowsSink(int keyboardPx) {
            return rowsSink && keyboardPx > 0;
        }

        /** Whether the seam stands another arrangement on this frame: the layout pass runs. */
        public boolean relayout() {
            return relayout;
        }

        /** Whether that pass dresses the stack again, for the half whose rows it now stands. */
        public boolean restyle() {
            return restyle;
        }

        /** Whether that dress changes the shape too (Style, Corners, Margin), not the sizes alone. */
        public boolean restyleShape() {
            return restyleShape;
        }

        /** Whether a band only one half's dock carries moved: the dock is laid out again. */
        public boolean bandsMoved() {
            return bandsMoved;
        }

        /**
         * The beaded half's share while one half's dock draws Lens beads and the other's does not
         * (1 on the beaded half, 0 on the other), which the beads melt into the slab by; NaN while
         * nothing melts.
         */
        public float beadShare() {
            return beadShare;
        }

        /** Whether the seam moved anything on this frame that the settle has to put back. */
        public boolean moved() {
            return moved;
        }

        /** Whether the bar is dressed for another page from this frame ({@link #rideBar}). */
        public boolean barRedressed() {
            return barRedressed;
        }
    }

    /** What a settle has left to do for the crossing ({@link #land}). The same object each time. */
    public static final class Landing {
        boolean bandsMoved;
        boolean laidOut;
        boolean barTravelled;
        @Nullable PaneWallPage dressedFor;
        boolean dressDiffered;
        boolean shapeDiffered;

        /** Whether a half's own band was eased: a slide that came back lays the dock out whole. */
        public boolean bandsMoved() {
            return bandsMoved;
        }

        /** Whether the seam stood another arrangement: the committed one is laid out again. */
        public boolean laidOut() {
            return laidOut;
        }

        /** Whether the bar was dressed for a page it rode with: it is dressed for its own again. */
        public boolean barTravelled() {
            return barTravelled;
        }

        /**
         * Whether the stack is left dressed for the half the wall did not land on ({@code landed}):
         * a slide that came back past the swap without a frame there to undo it (the drawer or
         * reduced motion took the last frames). It is dressed back as the settle puts it on screen.
         */
        public boolean redressesBack(@NonNull PaneWallPage landed) {
            return dressDiffered && dressedFor != null && SeamTravel.crosses(dressedFor, landed);
        }

        /** Whether that dress back changes the shape too. */
        public boolean shapeDiffered() {
            return shapeDiffered;
        }
    }

    /**
     * Everything one crossing holds, from its begin to its settle. A new one is taken at each
     * settle instead of every field being put back by hand: {@link #land} keeps only what the
     * page's commit still reads ({@link Run#landed}), and {@link #end} starts afresh.
     */
    private static final class Run {
        // ---- What a crossing reads once, as it begins ----

        @Nullable PaneWallPage committed;
        /** The other half's page, its layout, and the dock's plan for the crossing. */
        @Nullable PaneWallPage other;
        @Nullable PlaceLayout otherLayout;
        @NonNull DockPlan plan = DockPlan.NONE;
        /** The dock rows a fold leaves standing, outside the extra keys; empty unless it folds. */
        @NonNull java.util.List<Element> foldHeld = java.util.Collections.emptyList();
        /**
         * How much taller the keyless half's rows rest than the other half's less the extra keys,
         * which a fold travels too ({@link SeamTravel#foldRowsSinkPx}); 0 unless it folds.
         */
        int foldRestGrowthPx;
        /**
         * Whether the halves dress the stack differently, and so whether it is dressed again when it
         * changes halves; whether it then dips whole, keyboard included; which half keeps a keyboard
         * in the stack.
         */
        boolean dressDiffers, shapeDiffers, wholeStack;
        boolean committedKeyboard, otherKeyboard;
        /**
         * Whether both halves stand the bar alike, in one shape, so it stands still (#90); otherwise
         * whether the Workspace's bar rides with its page; and whether a slide from Home may lay the
         * Workspace's bar out over Home.
         */
        boolean barStill, barRides, statusMayShow;
        /** Whether beads melt on this crossing, and whether the committed half is the beaded one. */
        boolean beadMelt, beadsCommitted;
        /** The halves' Dock designs and Search fields; the designs null while no crossing holds them. */
        @Nullable DockStyle committedDesign, otherDesign;
        boolean committedSearchField, otherSearchField;
        /** The arrangements the seam can stand, laid out at begin: the other half's rows, bar, both. */
        @Nullable PlaceLayout dockLayout, statusLayout, dockStatusLayout;

        // ---- What the frames move ----

        /** The arrangement the chrome stands in, null while it stands in the committed one. */
        @Nullable PlaceLayout layout;
        /** Whether the arrangement stands the other half's dock rows, and its bar. */
        boolean dockShown, statusShown;
        /** The page whose half the stack wears; null while it wears the committed page's. */
        @Nullable PaneWallPage stackDressPage;
        /** The stack changed halves while they dress it differently: the next pass dresses it. */
        boolean restylePending;
        /** The page the bar is dressed for while it rides; null otherwise. */
        @Nullable PaneWallPage barPage;
        /** How far the slide has come toward the other half while the halves' bands differ; NaN otherwise. */
        float bandProgress = Float.NaN;
        /** Whether the wall has come to rest: the crossing is held only for the page's commit. */
        boolean landed;

        /** What a landed crossing keeps through the page's commit: the stack's dress. */
        @NonNull
        Run landed() {
            Run kept = new Run();
            kept.committed = committed;
            kept.other = other;
            kept.stackDressPage = stackDressPage;
            kept.dressDiffers = dressDiffers;
            kept.shapeDiffers = shapeDiffers;
            kept.landed = true;
            return kept;
        }
    }

    @NonNull private Run mRun = new Run();

    private final Answers mAnswers = new Answers();
    private final Landing mLanding = new Landing();

    /**
     * The crossing begins on the first frame that reaches {@code other}'s half, or reads the
     * halves again for a frame that reaches another page of it; what the frames have laid out
     * stays as it is. Reads and lays out everything the frames will need.
     */
    public void begin(@NonNull Half committed, @NonNull Half other, @NonNull Stage stage) {
        PlaceLayout here = committed.layout;
        PlaceLayout there = other.layout;
        mRun.committed = committed.page;
        mRun.other = other.page;
        mRun.otherLayout = there;
        // Each half's Dock design, read once here so the frames read no store: one layout gives
        // both halves one design, and then no band eases and no bead melts (#93).
        mRun.committedDesign = committed.dress.dockDesign();
        mRun.otherDesign = other.dress.dockDesign();
        mRun.committedSearchField = committed.dress.searchField();
        mRun.otherSearchField = other.dress.searchField();
        mRun.dressDiffers = !committed.stack.equals(other.stack);
        mRun.shapeDiffers = !committed.stack.shape.equals(other.stack.shape);
        // One bar across the wall (#90): where both halves stand it alike, in one shape, it
        // stands still on the crossing instead of riding with the Workspace's page.
        mRun.barStill = here.slot(Element.STATUS).equals(there.slot(Element.STATUS)) && !mRun.shapeDiffers;
        PlaceLayout workspaceSide = committed.page == PaneWallPage.WIDGETS ? there : here;
        mRun.barRides = !mRun.barStill && !workspaceSide.slot(Element.STATUS).hidden;
        // A keyboard in the stack, not one floating in a frame of its own.
        mRun.committedKeyboard = committed.keyboardUp
            && here.keyboardForm != PlaceLayout.KeyboardForm.FLOATING;
        mRun.otherKeyboard = other.keyboardUp
            && there.keyboardForm != PlaceLayout.KeyboardForm.FLOATING;
        // Beads melt only where one half's dock draws them and the other's does not: the
        // committed half's as they lie now, the other's as its layout lays its row out (the
        // Workspace's extra keys, or a keyboard in the stack, keep the slab there). The Search
        // field keeps the beads, in a capsule of its own under them (#84).
        boolean otherBeads = mRun.otherDesign == DockStyle.LENS_BEADS
            && LensBeadGeometry.rowWearsBeads(there,
                !stackDressedApart(committed.page) && stage.frameGlass.standsDown(),
                mRun.otherKeyboard);
        mRun.beadMelt = stage.beadsDrawn != otherBeads;
        mRun.beadsCommitted = stage.beadsDrawn;
        boolean exchangeable = SeamTravel.dockExchangeable(here)
            && SeamTravel.dockExchangeable(there);
        mRun.plan = SeamDress.plan(SeamTravel.dockPlan(here, there), exchangeable, mRun.dressDiffers,
            mRun.committedKeyboard || !EdgeStackPolicy.stack(here, Edge.BOTTOM).isEmpty(),
            mRun.otherKeyboard || !EdgeStackPolicy.stack(there, Edge.BOTTOM).isEmpty());
        mRun.wholeStack = SeamDress.wholeStack(mRun.plan, mRun.dressDiffers);
        // The layout a fold runs in stands the extra keys: the committed one folding out, the
        // other half's folding in.
        mRun.foldHeld = mRun.plan == DockPlan.FOLD_OUT ? SeamTravel.rowsOutsideExtraKeys(here)
            : mRun.plan == DockPlan.FOLD_IN ? SeamTravel.rowsOutsideExtraKeys(there)
            : java.util.Collections.emptyList();
        // Its rows travel from one half's rest to the other's: the keyless half's may stand
        // taller than the keyed half's less the extra keys (the A-Z row's chin on the rim).
        mRun.foldRestGrowthPx = foldRestGrowthPx(mRun.plan, committed.restRowsPx,
            other.restRowsPx);
        // Only from Home: the Workspace's bar is the one that travels with its page, unless the
        // bar is one across the wall and stands still.
        mRun.statusMayShow = !mRun.barStill && committed.page == PaneWallPage.WIDGETS
            && other.page != PaneWallPage.WIDGETS && SeamTravel.statusRides(there)
            && SeamTravel.statusCanArriveOver(here) && stage.splitPanes;
        boolean dockMayShow = mRun.plan == DockPlan.SLIDE_IN || mRun.plan == DockPlan.FOLD_IN
            || mRun.plan == DockPlan.DIP;
        mRun.dockLayout = dockMayShow ? SeamTravel.withDockOf(here, there) : null;
        mRun.statusLayout = mRun.statusMayShow ? SeamTravel.withStatusOf(here, there) : null;
        mRun.dockStatusLayout = dockMayShow && mRun.statusMayShow
            ? SeamTravel.withStatusOf(mRun.dockLayout, there) : null;
    }

    /**
     * Whether the status bar takes off Home's dress for the Workspace's as a crossing begins,
     * while it is still hidden, before any pass lays it out over Home: from Home, toward a half
     * that shapes the stack otherwise. Asked once, right after the {@link #begin} that started the
     * crossing.
     */
    public boolean barTakesWorkspaceDressFirst() {
        return mRun.shapeDiffers && mRun.committed == PaneWallPage.WIDGETS;
    }

    /**
     * One frame of the wall's travel: every answer the seam has for it. A frame of a gesture that
     * has not reached the other half answers the travel's own reveals and nothing else.
     *
     * @param dressable         whether the chrome can be dressed yet (the preferences are read)
     * @param bandEaseRuns      whether a Dock design switch is easing the dock's bands, which own
     *                          them while it runs
     * @param fieldUnderPalette whether the command palette is up over the Search field, which then
     *                          stays the design's own
     */
    @NonNull
    public Answers frame(@NonNull Frame frame, boolean dressable, boolean bandEaseRuns,
                         boolean fieldUnderPalette) {
        Answers answers = mAnswers;
        answers.clear(frame.dockReveal, frame.keyboardReveal);
        PaneWallPage committed = mRun.committed;
        PaneWallPage other = mRun.other;
        if (committed == null || other == null) return answers;
        float progress = SeamTravel.otherHalfProgress(frame, committed);
        // Which half's dress the stack wears, noting a change for this frame's pass.
        PaneWallPage stackPage = SeamDress.stackPage(committed, other, mRun.plan, progress);
        PaneWallPage dressPage = stackPage == committed ? null : stackPage;
        if (dressPage != mRun.stackDressPage) {
            mRun.stackDressPage = dressPage;
            if (mRun.dressDiffers) mRun.restylePending = true;
        }
        boolean crosses = SeamTravel.crosses(committed, other);
        // A band only one half's dock carries follows its page across the seam.
        if (dressable && crosses && !bandEaseRuns && mRun.committedDesign != null
            && mRun.otherDesign != null) {
            boolean stripMoves = (mRun.committedDesign == DockStyle.WALL_STRIP)
                != (mRun.otherDesign == DockStyle.WALL_STRIP);
            boolean fieldMoves = !fieldUnderPalette && mRun.committedSearchField != mRun.otherSearchField;
            float share = clamp01(progress);
            if ((stripMoves || fieldMoves) && Float.compare(share, mRun.bandProgress) != 0) {
                mRun.bandProgress = share;
                answers.bandsMoved = true;
            }
        }
        // Lens beads only one half draws melt into the slab, or come out of it, the same way.
        if (dressable && crosses && mRun.beadMelt) {
            if (mRun.beadsCommitted && SeamTravel.committedRowsLeave(mRun.plan)) {
                // The beaded half's rows sink below the stack's bottom edge, which cuts them flat:
                // the beads melt while the chrome still rests and sink as the slab.
                answers.beadShare = LensBeadGeometry.leavingMeltShare(
                    SeamTravel.otherHalfFraction(frame, committed), PlaceChromeTravel.LANDING);
            } else {
                float share = clamp01(progress);
                answers.beadShare = mRun.beadsCommitted ? 1f - share : share;
            }
        }
        // The stack stands the other half's rows, dressed for that half, from the frame the plan
        // says; from Home the Workspace's bar is laid out as the slide starts.
        boolean dockShown = SeamTravel.showsOtherDock(mRun.plan, progress);
        boolean statusShown = progress > 0f && mRun.statusMayShow;
        if (dockShown != mRun.dockShown || statusShown != mRun.statusShown || mRun.restylePending) {
            mRun.dockShown = dockShown;
            mRun.statusShown = statusShown;
            answers.relayout = true;
            answers.restyle = mRun.restylePending;
            answers.restyleShape = mRun.shapeDiffers;
            mRun.restylePending = false;
        }
        if (mRun.plan != DockPlan.NONE) answers.dockReveal = SeamTravel.dockReveal(mRun.plan, progress);
        answers.moved = answers.relayout || mRun.plan != DockPlan.NONE;
        // Between halves that dress the stack differently the keyboard dips with the rows, so it
        // is below the screen where the stack changes its dress.
        if (mRun.wholeStack) {
            answers.keyboardReveal = SeamDress.wholeStackKeyboardReveal(progress,
                mRun.committedKeyboard, mRun.otherKeyboard);
        }
        answers.folds = SeamTravel.folds(mRun.plan);
        if (answers.folds) answers.extraKeysShare = SeamTravel.extraKeysShare(mRun.plan, progress);
        // With a keyboard in the stack the rows sink into its top edge; a whole-stack dip moves
        // both by the translation.
        answers.rowsSink = !answers.folds && mRun.plan != DockPlan.NONE && !mRun.wholeStack;
        return answers;
    }

    /**
     * The arrangement this frame's answers stand, as the activity runs the seam's layout pass
     * ({@link Answers#relayout}): {@link #layout} answers it from here on.
     */
    public void standLayout() {
        mRun.layout = mRun.dockShown ? (mRun.statusShown ? mRun.dockStatusLayout : mRun.dockLayout)
            : mRun.statusShown ? mRun.statusLayout : null;
    }

    /**
     * Whether the status bar rides with the Workspace's page on this frame, which crosses the seam
     * to a Home page while the halves wear the bar apart (a Home layout that places or shapes it
     * otherwise): a bar both halves wear alike stands still (#90), and so does every bar on a
     * slide between Home's own pages. Notes the page it is dressed for, so its chips do not
     * change while it is on screen ({@link Answers#barRedressed}).
     *
     * @param hostCanRide whether the bar's host is laid out, along the top, on a wall
     */
    public boolean rideBar(@NonNull Frame frame, boolean hostCanRide) {
        boolean rides = hostCanRide && mRun.other != null && mRun.barRides
            && (frame.from == PaneWallPage.WIDGETS || frame.toward == PaneWallPage.WIDGETS);
        PaneWallPage barPage = null;
        if (rides) {
            barPage = frame.toward != PaneWallPage.WIDGETS ? frame.toward
                : frame.from != PaneWallPage.WIDGETS ? frame.from
                : mRun.committed == PaneWallPage.WIDGETS ? null : mRun.committed;
        }
        mAnswers.barRedressed = barPage != mRun.barPage;
        mRun.barPage = barPage;
        return rides;
    }

    /**
     * The settle's first half: everything the crossing moved is put down but the stack's dress,
     * which it keeps through the page's commit ({@link #end}). Says what the settle has left to
     * do; at rest, nothing.
     */
    @NonNull
    public Landing land() {
        Landing landing = mLanding;
        landing.bandsMoved = !Float.isNaN(mRun.bandProgress);
        landing.laidOut = mRun.layout != null;
        landing.barTravelled = mRun.barPage != null;
        landing.dressedFor = mRun.stackDressPage;
        landing.dressDiffered = mRun.dressDiffers;
        landing.shapeDiffered = mRun.shapeDiffers;
        mRun = mRun.landed();
        return landing;
    }

    /** The settle's second half, after the page's commit: the stack's dress lets go. */
    public void end() {
        mRun = new Run();
    }

    // ---- What the chrome reads of a crossing between frames ----

    /** The other half's page the crossing was begun for; null while none runs. */
    @Nullable
    public PaneWallPage other() {
        return mRun.other;
    }

    /**
     * Whether the wall is sliding across the seam: a crossing has begun and has not landed. This
     * is what the status bar's dress reads ({@link SeamDress#wearsHome}): the Workspace's while
     * the wall slides, its own half's from the moment it lands. A landed crossing still names its
     * {@link #other} page through the page's commit, but only for the stack, which keeps the dress
     * it travelled in; the status bar does not, so the commit dresses it for the page it lands on
     * and every pass after the settle finds it in that same dress.
     */
    public boolean sliding() {
        return mRun.other != null && !mRun.landed;
    }

    /** The other half's layout as the crossing read it; null while none runs and once landed. */
    @Nullable
    public PlaceLayout otherLayout() {
        return mRun.otherLayout;
    }

    /**
     * The arrangement the chrome stands in while the seam stands the other half's dock rows or the
     * Workspace's bar: the committed layout with those slots taken from the other half's. Null
     * whenever the chrome stands in the committed layout itself.
     */
    @Nullable
    public PlaceLayout layout() {
        return mRun.layout;
    }

    /**
     * Whether a fold leaves {@code row} where it stands: a dock row between the extra keys and the
     * screen's bottom edge ({@link SeamTravel#rowsOutsideExtraKeys}). False for every row while
     * nothing folds.
     */
    public boolean foldHolds(@NonNull Element row) {
        return mRun.foldHeld.contains(row);
    }

    /**
     * How much taller the keyless half's dock rows rest than the keyed half's less the extra keys
     * row, which a fold's rows travel on top of the extra keys' height so its end at Home's side
     * is Home's rest ({@link SeamTravel#foldRowsSinkPx}). 0 while nothing folds, and wherever
     * either half's rows were not known at begin.
     */
    public int foldRestGrowthPx() {
        return mRun.foldRestGrowthPx;
    }

    /** The keyless half's rest rows less the keyed half's, for a fold; 0 otherwise or unknown. */
    private static int foldRestGrowthPx(@NonNull DockPlan plan, int committedRowsPx,
                                        int otherRowsPx) {
        if (committedRowsPx < 0 || otherRowsPx < 0) return 0;
        switch (plan) {
            case FOLD_OUT: return otherRowsPx - committedRowsPx;
            case FOLD_IN: return committedRowsPx - otherRowsPx;
            default: return 0;
        }
    }

    /**
     * The page whose half the stack wears while the seam stands the other half's rows
     * ({@link SeamDress#stackPage}); null while it wears the committed page's, always at rest.
     */
    @Nullable
    public PaneWallPage stackDressPage() {
        return mRun.stackDressPage;
    }

    /**
     * Whether the stack wears a dress apart from the frame's, which stays {@code committed}'s:
     * mid-slide, once it stands the rows of a half that shapes it differently.
     */
    public boolean stackDressedApart(@NonNull PaneWallPage committed) {
        return mRun.shapeDiffers && mRun.stackDressPage != null
            && SeamTravel.crosses(mRun.stackDressPage, committed);
    }

    /** The committed half's Dock design as the crossing read it; null while none holds one. */
    @Nullable
    public DockStyle committedDesign() {
        return mRun.committedDesign;
    }

    /**
     * The Dock design of the half whose dock rows the stack stands on this frame: the other
     * half's from the frame the crossing stands its rows ({@link #layout}), the committed half's
     * before that. It is the design the rows are laid out in (Lens beads' fit and side inset), so
     * the rows a slide brings in wear their own half's geometry from their first frame rather than
     * the committed half's until the settle. The slab and the beads themselves keep
     * {@link #committedDesign} and their melt. Null while no crossing holds the designs.
     */
    @Nullable
    public DockStyle rowsDesign() {
        return mRun.dockShown ? mRun.otherDesign : mRun.committedDesign;
    }

    /** The committed half's Search field as the crossing read it, while it holds a design. */
    public boolean committedSearchField() {
        return mRun.committedSearchField;
    }

    /**
     * How much of the Search field the dock carries, from {@code here} on the committed half
     * toward the other half's as the slide eases the bands; {@code here} while nothing eases.
     */
    public float searchFieldShare(float here) {
        if (Float.isNaN(mRun.bandProgress) || mRun.otherDesign == null) return here;
        float there = mRun.otherSearchField ? 1f : 0f;
        return here + (there - here) * mRun.bandProgress;
    }

    /**
     * How much of a band only {@code design} carries the dock is laid out with, from {@code here}
     * on the committed half toward the other half's as the slide eases the bands; {@code here}
     * while nothing eases.
     */
    public float designShare(@NonNull DockStyle design, float here) {
        DockStyle other = mRun.otherDesign;
        if (Float.isNaN(mRun.bandProgress) || other == null) return here;
        float there = other == design ? 1f : 0f;
        return here + (there - here) * mRun.bandProgress;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
