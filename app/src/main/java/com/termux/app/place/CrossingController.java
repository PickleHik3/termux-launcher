package com.termux.app.place;

import android.graphics.drawable.Drawable;
import android.os.Trace;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.MotionLog;
import com.termux.app.terminal.GeometryScheduler;

/**
 * The crossing's per-frame driver (ADR 0018 D17, step one), moved out of the activity line for
 * line: the chrome's travel with the wall, the chrome between two Home pages, the seam as a frame
 * reaches it, the look blend and the room the travel reads. It reaches the activity only through
 * {@link Host}; the {@link Crossing} it drives is handed in.
 */
public final class CrossingController {

    /** What only the activity gives the crossing's driver. */
    public interface Host {
        // ---- window and views ------------------------------------------------------

        /** The window's view of {@code id}, looked up afresh; null while it has none. */
        @Nullable <T extends View> T findViewById(int id);

        /** The window's view of {@code id}, found once per tree; null while it has none. */
        @Nullable @SuppressWarnings("unchecked") <T extends View> T hotView(int id);

        /** The activity, as the Context the animation speed is read in. */
        @NonNull android.content.Context context();

        /** Whether reduced motion is on. */
        boolean isReducedMotionEnabled();

        /** Whether the split panes are on. */
        boolean isSplitPanesEnabled();

        /** The preferences; null before they are read. */
        @Nullable com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences preferences();

        /** The chrome's renderer; null where the activity has none. */
        @Nullable com.termux.app.chrome.ChromeRenderer chrome();

        /** The terminal window bar, dressed and laid again. */
        void refreshTerminalWindowBar();

        // ---- wall and pages --------------------------------------------------------

        /** The page the wall is committed to. */
        @NonNull com.termux.app.wall.PaneWallPage lastWallPage();

        /** The place the wall has committed to, the Workspace's page read as the display's while it is in front. */
        @NonNull com.termux.app.wall.PaneWallPage currentWallPlace();

        /** The pane wall's controller; null before it is built. */
        @Nullable com.termux.app.wall.PaneWallController paneWallController();

        /** The cells' kinds as places. */
        @NonNull java.util.List<com.termux.app.wall.PaneWallPage> placesOf(@NonNull java.util.List<com.termux.app.wall.PaneWallPage> pages);

        /** The wall's pages. */
        com.termux.app.wall.WallPages wallPages();

        /** The id of the Home page in front. */
        int homePageInFront();

        /** The first Home page of the run Home's frame pages inside. */
        int homeRunFirst();

        /** The last Home page of that run; -1 for none. */
        int homeRunLast();

        /** Whether Home's pane has a controller yet. */
        boolean hasWidgetPaneController();

        /** How far Home's pager is shifted, as a fraction of its width. */
        float homePageShiftFraction();

        /** Whether Home's pager is settling. */
        boolean isHomePageSettling();

        /** The index of the Home page in front. */
        int homePageIndex();

        /** Runs {@code action} once Home's page settles. */
        void runWhenHomePageSettles(@NonNull Runnable action);

        /** Whether the app drawer is engaged. */
        boolean isAppDrawerEngaged();

        /** Whether a surface editor is active. */
        boolean surfaceEditorActive();

        // ---- arrangement and dress -------------------------------------------------

        /** The layout store; null before the preferences are read. */
        @Nullable PlaceLayoutStore placeLayoutStore();

        /** The orientation the place is arranged for now. */
        PlaceOrientation currentPlaceOrientation();

        /** The arrangement on screen. */
        ResolvedArrangement arrangement();

        /** The arrangement the committed place wears. */
        ResolvedArrangement committedArrangement();

        /** {@code page}'s arrangement, the crossing's held one while it runs. */
        ResolvedArrangement arrangementOf(@NonNull com.termux.app.wall.PageRef page);

        /** {@code page}'s arrangement, the crossing's held one while it runs. */
        ResolvedArrangement arrangementOf(@NonNull com.termux.app.wall.PaneWallPage page);

        /** The revision the arrangements were resolved at. */
        int arrangementRevision();

        /** The half of the wall {@code page} is arranged in. */
        com.termux.app.wall.PageRef arrangedPageOf(@NonNull com.termux.app.wall.PaneWallPage page);

        /** The page the crossing's end for {@code page} stands on. */
        com.termux.app.wall.PageRef endpointPageOf(@NonNull com.termux.app.wall.PaneWallPage page);

        /** The dress {@code page} wears. */
        @NonNull com.termux.app.chrome.Dress dressOf(@NonNull com.termux.app.wall.PaneWallPage page);

        /** The dress {@code page} wears. */
        @NonNull com.termux.app.chrome.Dress dressOf(@NonNull com.termux.app.wall.PageRef page);

        /** The layout variant the dresses give {@code page}. */
        com.termux.app.place.LayoutVariant dressLayoutOf(@NonNull com.termux.app.wall.PageRef page);

        /** The look id of {@code page}. */
        int lookIdOf(@NonNull com.termux.app.wall.PageRef page);

        /** The look {@code page} wears. */
        com.termux.app.look.PageLook.Snapshot pageLookOf(@NonNull com.termux.app.wall.PageRef page);

        /** The page the stack's rows stand for. */
        com.termux.app.wall.PaneWallPage stackDressPage();

        /** The stack as the geometry pass last applied it; null before one. */
        @Nullable com.termux.app.place.StackLayout.Result appliedStack();

        /** The stack dressed again for the half it now stands in. */
        void restyleStackForDress(boolean shapeDiffers);

        /** The extra keys row restated for the place on screen, where it changed. */
        void applyExtraKeysPlaceEligibility();

        /** The layout variant last applied to the chrome; set to null to apply the next again. */
        void setAppliedChromeVariant(@Nullable com.termux.app.place.LayoutVariant variant);

        /** The chrome's layout applied for the place on screen. */
        void syncChromeArrangement();

        // ---- keyboard --------------------------------------------------------------

        /** The in-app keyboard; null before it is built. */
        @Nullable com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard inAppKeyboard();

        /** Whether the in-app keyboard is shown. */
        boolean isInAppKeyboardShown();

        /** Whether the keyboard floats in a frame of its own. */
        boolean isKeyboardFloating();

        /** Whether the committed place has its keyboard up. */
        boolean committedKeyboardVisible();

        /** Whether {@code place} brings its keyboard up on arrival. */
        boolean wantsKeyboardOnEnter(@NonNull com.termux.app.wall.PaneWallPage place);

        /** The keyboard's height as the pass measures it. */
        int keyboardMeasureHeightPx();

        /** The keyboard's height as last measured. */
        int keyboardDesiredHeightPx();

        /** The keyboard's height as the stack lays it out; 0 while it is down or floating. */
        int travelKeyboardLaidOutPx();

        /** The IME's lift, px. */
        int imeLiftPx();

        /** The toolbar's height as applied. */
        int appliedToolbarHeightPx();

        /** The extra keys row's height for the device's rows and scale. */
        int measuredExtraKeysRowPx();

        /** The room the content is reserved as applied. */
        int appliedContentReservationPx();

        /** The terminal's flush padding it last rested with. */
        int terminalRestFlushPaddingPx();

        /** Whether the travel's pre-roll of the keyboard may run. */
        boolean canPreRollTravelKeyboard();

        /** The content held at the room it was committed with. */
        void beginTravelHold();

        /** The keyboard brought up below the screen for the frame's arriving place. */
        void preRollTravelKeyboard(@NonNull com.termux.app.place.PlaceChromeTravel.Frame frame);

        /** The content given {@code roomGivenBackPx} now. */
        void preRollTravelContent(int roomGivenBackPx);

        /** The keyboard put away with the picture of the stack. */
        void putKeyboardAwayWithPicture();

        /** Whether a pictured dip put the keyboard away. */
        boolean keyboardPicturedAway();

        /** Whether a keyboard swipe drives the travel. */
        boolean keyboardSwipeTravel();

        /** A keyboard up on both places crosses from one material to the other. */
        void syncKeyboardMaterialTravel(@NonNull com.termux.app.place.PlaceChromeTravel.Frame frame);

        /** The terminal's rows drawn where the settle's resize will put them. */
        void syncTerminalTravelDisplacement(@NonNull com.termux.app.place.PlaceChromeTravel.Frame frame, @Nullable com.termux.app.wall.PaneWallPage arriving);

        /** The cover under the stack's picture, px. */
        int stackPictureCoverPx();

        // ---- dock ------------------------------------------------------------------

        /** One snapshot of both halves for the dock's geometry, rows standing for {@code rowsPage}. */
        @NonNull com.termux.app.dock.DockGeometry.Snapshot dockSnapshot(@NonNull PlaceLayout arrangement, @NonNull com.termux.app.wall.PaneWallPage rowsPage);

        /** {@code page}'s half of the dock's geometry. */
        @NonNull com.termux.app.dock.DockGeometry.Half dockHalf(@NonNull com.termux.app.wall.PageRef page, @NonNull PlaceOrientation orientation, @NonNull PlaceLayout fallbackLayout);

        /** The beads as last drawn; null while the dock is a slab. */
        @Nullable com.termux.app.dock.LensBeadGeometry.Beads dockBeads();

        /** Whether the dock's band ease runs. */
        boolean dockBandEaseRunning();

        /** The beads' melt drawn at the crossing's share. */
        void applyDockBeadMelt(boolean settled);

        /** The dock's slab dressed for the half it stands in. */
        void restyleDockSurface();

        /** The Drawer tile laid in the dock's rows. */
        void syncDrawerTile();

        /** The dock's translation the wall's travel owns, px. */
        float dockTravelTranslationPx();

        /** The place the slab's Dock design last travelled to; null for none. */
        @Nullable com.termux.app.wall.PaneWallPage dockDesignTravelledTo();

        /** The place the slab's Dock design travelled to. */
        void setDockDesignTravelledTo(@Nullable com.termux.app.wall.PaneWallPage place);

        /** The Peek card's listeners set on the rows laid out. */
        void syncPeekListeners();

        /** Whether a Peek host is watched. */
        boolean hasPeekWatchedHost();

        /** The Peek card refreshed. */
        void schedulePeekRefresh();

        // ---- status bar ------------------------------------------------------------

        /** What the bottom status band takes of the stack at rest, px. */
        int bottomStatusBandAtRestPx(boolean floating);

        /** What the bottom status band takes of the stack now, px. */
        int bottomStatusBandPx();

        /** The status bar's travel for this frame; whether it rides. */
        boolean syncStatusTravel(@NonNull com.termux.app.place.PlaceChromeTravel.Frame frame, @NonNull com.termux.app.place.SeamCrossing.Answers seam);

        /** The status bar's Margin ease for this frame. */
        void syncStatusMarginEase(@NonNull com.termux.app.place.SeamCrossing.Answers seam);

        /** The strip under the pill fading with the finger. */
        void syncNavStripFade(@NonNull com.termux.app.place.PlaceChromeTravel.Frame wallFrame);

        /** The band behind the system bar fading with the bar's glass. */
        void syncTopBandFade(@NonNull com.termux.app.place.PlaceChromeTravel.Frame wallFrame);

        /** The strip under the pill begun fading between the ends. */
        void beginNavStripFade(@NonNull com.termux.app.wall.PageRef here, @NonNull com.termux.app.wall.PageRef there);

        /** The top band begun fading between the ends. */
        void beginTopBandFade(@NonNull com.termux.app.wall.PageRef here, @NonNull com.termux.app.wall.PageRef there);

        /** A band only one half's dock carries following its page across the seam. */
        void syncSeamBandShare(@NonNull com.termux.app.place.SeamCrossing.Answers seam);

        // ---- chrome travel state ---------------------------------------------------

        /** The rest {@code place}'s chrome leaves. */
        com.termux.app.place.PlaceChromeTravel.Rest chromeRestOf(@NonNull com.termux.app.wall.PaneWallPage place);

        /** The rests as the travel frame asks for them, bound once. */
        com.termux.app.place.PlaceChromeTravel.States chromeRestStates();

        /** Whether a frame stands down on a joined Docked frame, bound once. */
        com.termux.app.place.SeamCrossing.FrameGlass frameStandsDown();

        /** Whether something was moved or faded during this slide. */
        boolean chromeTravelMoved();

        /** Records that something was moved or faded during this slide. */
        void setChromeTravelMoved(boolean moved);

        /** Whether the prompt field stands under the palette. */
        boolean promptFieldUnderPalette();

        /** A landing's re-dress still waiting for its post, had now. */
        void flushLandingRestyle();

        // ---- settle and layout passes ----------------------------------------------

        /** Settles the chrome on {@code page}, landed on {@code landed}. */
        void settlePlaceChrome(@NonNull com.termux.app.wall.PaneWallPage page, @Nullable com.termux.app.wall.PageRef landed);

        /** The place's layout pass run now. */
        void syncPlaceLayout();

        /** A geometry pass requested in this frame. */
        void geometryRequestNow(@NonNull GeometryScheduler.Reason reason, @NonNull GeometryScheduler.ResizePolicy policy);
    }

    @NonNull private final Crossing mCrossing;
    @NonNull private final Host mHost;

    public CrossingController(@NonNull Crossing crossing, @NonNull Host host) {
        mCrossing = crossing;
        mHost = host;
    }

    /** The chrome's clock through a slide with no finger on it, under a turning transition. */
    private final com.termux.app.place.ChromeSettle mChromeSettle =
        new com.termux.app.place.ChromeSettle();

    // ---- The chrome travelling with the wall ------------------------------------------------

    /**
     * One frame of the chrome's travel between two places (PlaceChromeTravel). The dock rises or
     * falls and the keyboard slides in or out as a function of the wall's live offset, between the
     * state of the place being left and the place being arrived at — Home with the dock at the
     * bottom and the keyboard down, the terminal with the dock riding on its keyboard. Only
     * translationY and alpha change per frame; nothing is laid out.
     *
     * <p>What the frame needs that is not laid out yet is pre-rolled once, in the first frame
     * that asks for it: the keyboard is brought up at its full height below the screen, and the
     * content is held at the room it was committed with so it costs the terminal no resize. TermuxActivity#settlePlaceChrome makes the
     * outcome the layout, or undoes the pre-roll if the wall came back to where it started.
     */
    public void syncChromeTravel(float offsetPx) {
        Trace.beginSection("Wall.chromeTravel");
        try {
            doSyncChromeTravel(offsetPx);
        } finally {
            Trace.endSection();
        }
    }

    private void doSyncChromeTravel(float offsetPx) {
        if (mHost.paneWallController() == null) return;
        com.termux.app.wall.PaneWallLayout wall = mHost.paneWallController().wall();
        // With no finger on the wall, under a page transition that turns the pages, the chrome
        // keeps the slide's clock to its last frame (ChromeSettle). Fed every frame, rests and
        // finger frames included, so a slide never carries on from a stale one.
        mChromeSettle.frame(offsetPx, wall.slideFromPx(), wall.getWidth(),
            wall.pageTransition() != com.termux.app.launcher.home.HomePageTransition.SLIDE
                && !wall.slidesAcrossSeam(),
            mHost.currentWallPlace() == mHost.lastWallPage());
        // A crossing between two Home pages is Home's pager's (syncHomePagesTravel): a wall at
        // rest writes nothing over it. A gesture that carries on past the last Home page into the
        // wall lands it here, on the page in front, before the wall's own crossing begins.
        if (homePagesCrossing()) {
            if (!wall.isMoving() && offsetPx == 0f) return;
            landHomePagesCrossing();
        }
        // A keyboard swipe drives the same travel by its finger while the wall stands still; the
        // layout passes it causes must not write the wall's resting frame over it.
        if (mHost.keyboardSwipeTravel()) return;
        // At rest there is nothing to travel: the settle that follows the last frame owns the
        // chrome, and a wall that never moved has no transforms to put back.
        if (!wall.isMoving() && offsetPx == 0f && !mHost.chromeTravelMoved()) return;
        // Reduced motion snaps the wall, so the chrome snaps with it, at settle. The app drawer
        // and the editors own the stack while they are up and move it their own way.
        if (mHost.isReducedMotionEnabled() || mHost.isAppDrawerEngaged() || mHost.surfaceEditorActive()
            || mHost.appliedContentReservationPx() < 0) return;
        // The Workspace's page is the display's place while the display window is in front of it,
        // so the chrome travels to and from the state that place rests in.
        // The cells' kinds around the current cell, so a slide between two cells of a kind's
        // pages and the one beside reads the arriving cell's dress (ADR 0016 section 3).
        java.util.List<com.termux.app.wall.PaneWallPage> pages =
            mHost.placesOf(mHost.paneWallController().cellKinds());
        int cellAt = mHost.paneWallController().currentCellIndex();
        // The committed place is named so its keyboard, going away, is put away over the first
        // half of the way (PlaceChromeTravel.KEYBOARD_HIDE_END) while a rising one lands with the page.
        // A slide toward another window of the Workspace stays on its place: the wall's offset
        // alone reads the previous window as Home, the place on the left.
        com.termux.app.wall.PaneWallPage place = mHost.currentWallPlace();
        com.termux.app.place.PlaceChromeTravel.InnerPages inner =
            mHost.paneWallController()::showsInnerPage;
        // Each step of the frame is its own section (Travel.<step>), so a trace names what a slow
        // drag frame spent its time in.
        Trace.beginSection("Travel.frame");
        float chromeOffsetPx = mChromeSettle.chromeOffsetPx();
        com.termux.app.place.PlaceChromeTravel.Frame frame =
            com.termux.app.place.PlaceChromeTravel.at(pages, cellAt, chromeOffsetPx,
                mChromeSettle.keyboardOffsetPx(), wall.getWidth(), mHost.chromeRestStates(),
                mHost.lastWallPage(), inner);
        // The status bar rides with the Workspace's page, so it reads where the wall stands.
        com.termux.app.place.PlaceChromeTravel.Frame wallFrame = chromeOffsetPx == offsetPx
            ? frame : com.termux.app.place.PlaceChromeTravel.at(pages, cellAt, offsetPx,
                wall.getWidth(), mHost.chromeRestStates(), mHost.lastWallPage(), inner);
        Trace.endSection();
        // Across the seam, how the stack crosses and which half's dress it wears are read before
        // the keyboard's pre-roll, so a keyboard brought up for the other half is measured in it.
        Trace.beginSection("Travel.readSeam");
        readSeamTravel(frame);
        Trace.endSection();
        // Every answer the seam has for this frame, from what the crossing read as it began, and
        // the dock's dress at the chrome's progress into the other half. A pictured dip moves on
        // the wall's own way (SeamCrossing#pictured).
        Trace.beginSection("Travel.answers");
        com.termux.app.place.SeamCrossing.Answers seam = mCrossing.frame(frame, wallFrame,
            mHost.preferences() != null && mHost.chrome() != null, mHost.dockBandEaseRunning(),
            mHost.promptFieldUnderPalette());
        Trace.endSection();
        Trace.beginSection("Travel.blends");
        // The strip under the pill crosses from the one dress to the other with the finger.
        mHost.syncNavStripFade(wallFrame);
        // A stack whose halves differ only in look blends its glass with the wall.
        syncStackLookBlend(seam);
        // And the band behind the system bar with the bar's glass, by the same share.
        mHost.syncTopBandFade(wallFrame);
        // A band only one half's dock carries follows its page across the seam.
        mHost.syncSeamBandShare(seam);
        // And Lens beads only one half draws melt into the slab, or come out of it, the same way.
        syncSeamBeadMelt(seam);
        Trace.endSection();
        Trace.beginSection("Travel.preRolls");
        // The dock rows are always laid out by the layout on screen, the minimal one included
        // (chromeRestOf), so only the keyboard is ever pre-rolled.
        // A keyboard a pictured dip put away travels in the picture, never in the stack again.
        if (!mCrossing.keyboardPreRolled() && !mHost.keyboardPicturedAway()
            && com.termux.app.place.PlaceChromeTravel.needsKeyboardPreRoll(frame,
                mHost.isInAppKeyboardShown())
            && mHost.canPreRollTravelKeyboard()) {
            mHost.preRollTravelKeyboard(frame);
        }
        // Toward a place whose chrome takes less room from the content than this one's, the
        // content gets that room now rather than at settle, so no band of wallpaper opens between
        // the pane and the chrome sliding away from it (ADR 0003, amended).
        com.termux.app.wall.PaneWallPage arriving =
            com.termux.app.place.PlaceChromeTravel.arriving(frame, mHost.lastWallPage());
        if (!mCrossing.contentPreRolled() && arriving != null) {
            int givenBack = travelRoomChangePx(arriving);
            if (givenBack > 0) mHost.preRollTravelContent(givenBack);
        }
        // Toward the terminal, whose chrome takes more room than this place's (its keyboard), its
        // page is laid out at the size it lands at in this frame, while it is still arriving, and
        // its panes resize now rather than after the page has landed (Crossing#preSizeTerminal).
        if (arriving == com.termux.app.wall.PaneWallPage.TERMINAL
            && mCrossing.terminalPreSizePx() == 0) {
            int taken = -travelRoomChangePx(arriving);
            if (MotionLog.enabled) {
                MotionLog.log("presize", "taken=" + taken + " reservationPx=" + mHost.appliedContentReservationPx());
            }
            if (taken > 0) mCrossing.preSizeTerminal(taken, mHost.appliedContentReservationPx());
        }
        Trace.endSection();
        // Across the seam the dock's rows and the status bar are the halves' own: the stack is
        // given the other half's rows where none of them shows, and their reveal is the seam's.
        float dockReveal = syncSeamTravel(seam);
        // The key rows the stack stands are drawn for the half it wears, from the frame it stands
        // them: a toolbar brought up rises live, not dead until the settle. A comparison per frame;
        // the rows are restated only on the frame the dress changes.
        mHost.applyExtraKeysPlaceEligibility();
        Trace.beginSection("Travel.stack");
        int keyboardPx = mHost.travelKeyboardLaidOutPx();
        int dockPx = travelDockLaidOutPx(keyboardPx);
        // The stack as the crossing answers it (Crossing#stack): with a keyboard in the stack the
        // rows sink into its top edge, since the stack's own translation carries the keyboard
        // too; or only the extra keys fold, the rows on the canvas's side of them sinking by its
        // hidden part (#94); and between halves that dress the stack differently the keyboard
        // dips with the rows (SeamDress.wholeStack), both moved by the translation.
        boolean wasMoved = mHost.dockTravelTranslationPx() != 0f;
        float translation = mCrossing.stack(seam, dockReveal, keyboardPx, dockPx,
            travelDockRowsPx(keyboardPx));
        if (translation != 0f || wasMoved) mHost.setChromeTravelMoved(true);
        Trace.endSection();
        // A keyboard up on both places crosses from one material to the other with the wall.
        Trace.beginSection("Travel.material");
        mHost.syncKeyboardMaterialTravel(frame);
        Trace.endSection();
        // The terminal's rows are drawn where the settle's resize is going to put them, and the
        // pane's own edge ends where the chrome's live edge begins rather than under it.
        Trace.beginSection("Travel.content");
        if (mCrossing.holdsContent()) {
            mHost.syncTerminalTravelDisplacement(frame, arriving);
            // The cover the pane controller holds for the window in front, under whichever edge
            // stands higher: the stack's, or the picture of the committed half's stack.
            mCrossing.coverAt(translation, mHost.stackPictureCoverPx());
        }
        Trace.endSection();
        // The status bar travels with the Workspace's page wherever the frame reaches Home while
        // the pages slide; it fades in place where it cannot ride (a bar on a side or the bottom
        // edge, or pages that turn).
        Trace.beginSection("Travel.status");
        boolean barRides = mHost.syncStatusTravel(wallFrame, seam);
        // A bar that stands still eases from one page's Margin to the other's with the wall.
        mHost.syncStatusMarginEase(seam);
        // Under a transition that turns the pages, a bar one end wears apart fades in place.
        float statusFade = seam.statusFade();
        float statusAlpha = barRides ? 1f : !Float.isNaN(statusFade) ? statusFade
            : frame.statusReveal;
        // The status strip's content and the side and top bars fade as the places' rests say;
        // the minimal layout's bars fade like the normal one's.
        if (statusAlpha != 1f || frame.dockReveal != 1f) mHost.setChromeTravelMoved(true);
        mCrossing.fade(statusAlpha, frame.dockReveal);
        Trace.endSection();
    }

    // ---- The chrome between two Home pages (#173 slice E) -------------------------------------

    /**
     * The two Home pages a crossing on Home's own pager runs between, by id: the one in front as
     * it began (its committed end) and the neighbour the shift opened; -1 while none runs.
     */
    private int mHomeCrossHereId = -1;
    private int mHomeCrossThereId = -1;
    /** Home's rest as that crossing began, which both of its ends stand in; null while none runs. */
    @Nullable private com.termux.app.place.PlaceChromeTravel.Rest mHomeCrossRest;
    /** The crossing's landing waits on Home's page settle (runWhenHomePageSettles), once. */
    private boolean mHomeCrossLandingQueued;
    private final Runnable mLandHomePagesCrossingAfterSettle = () -> {
        mHomeCrossLandingQueued = false;
        landHomePagesCrossing();
    };
    /**
     * The pager's step as last read: the index of the Home page in front, the side its shift
     * opens (-1 left, 1 right, 0 at rest), and the pages and store revision it was read against.
     * A frame of the same step reads none of it again, so a slide between two Home pages that
     * dress alike builds nothing per frame.
     */
    private int mHomeStepIndex = -1;
    private int mHomeStepSide;
    private int mHomeStepRevision;
    @Nullable private com.termux.app.wall.WallPages mHomeStepPages;
    /** That step's pages by id: the one in front and its neighbour on the side; -1 for none. */
    private int mHomeStepFrontId = -1;
    private int mHomeStepTowardId = -1;
    /** Whether that step was already asked to begin a crossing, which it did or not, once. */
    private boolean mHomeStepTried;

    /** Whether the crossing that runs is one between two Home pages, on Home's own pager. */
    private boolean homePagesCrossing() {
        return mCrossing.crossing() && mCrossing.seam().oneKind();
    }

    /**
     * One frame of Home's own pager (onHomePageShifted): where it slides between two Home pages
     * whose chrome differs in the dock's rows or in a status bar only one of them wears, the
     * chrome morphs with the slide, through the same crossing the wall's travel runs
     * (doSyncChromeTravel) on a frame of the pager (PlaceChromeTravel#homePages). Begun on the
     * finger's first frame toward a neighbour that crosses, begun again when the shift opens the
     * other neighbour, landed where the pager's settle lands. Two Home pages that dress alike,
     * reduced motion and Animation speed Off begin nothing: the layout switches at the settle, as
     * before.
     */
    public void syncHomePagesTravel() {
        if (!mHost.hasWidgetPaneController() || mHost.paneWallController() == null) return;
        boolean running = homePagesCrossing();
        float fraction = mHost.homePageShiftFraction();
        // At rest with nothing running, or while the wall's own crossing owns the chrome, there is
        // nothing to read. At rest the next gesture may ask to begin again, whatever refused the
        // last one (the drawer, a keyboard swipe, reduced motion) having maybe let go.
        if (!running && fraction == 0f) {
            mHomeStepTried = false;
            return;
        }
        if (!running && mCrossing.crossing()) return;
        boolean settling = mHost.isHomePageSettling();
        // The shift came back to rest with no settle to wait for: the crossing lands here.
        if (running && fraction == 0f && !settling) {
            landHomePagesCrossing();
            return;
        }
        int side = fraction == 0f ? 0 : fraction > 0f ? -1 : 1;
        int index = mHost.homePageIndex();
        com.termux.app.wall.WallPages pages = mHost.wallPages();
        int revision = mHost.arrangementRevision();
        if (index != mHomeStepIndex || side != mHomeStepSide || pages != mHomeStepPages
            || revision != mHomeStepRevision) {
            readHomeStep(pages, index, side, revision);
        }
        int front = mHomeStepFrontId;
        int toward = mHomeStepTowardId;
        if (running) {
            // At rest mid-settle the page in front's partner is the crossing's other end.
            if (side == 0) toward = front == mHomeCrossHereId ? mHomeCrossThereId
                : front == mHomeCrossThereId ? mHomeCrossHereId : -1;
            // Either way round: after the pager's commit the arriving page is in front and the
            // committed one is its neighbour.
            boolean samePair = front >= 0 && toward >= 0
                && ((front == mHomeCrossHereId && toward == mHomeCrossThereId)
                    || (front == mHomeCrossThereId && toward == mHomeCrossHereId));
            if (!samePair) {
                // The shift opened another neighbour: this crossing lands on the page in front
                // and a crossing toward the new one may begin below.
                landHomePagesCrossing();
                running = false;
            }
        }
        if (!running) {
            // Begun only under the finger: a page change from elsewhere switches at its settle.
            if (side == 0 || front < 0 || toward < 0 || settling || mHomeStepTried) return;
            mHomeStepTried = true;
            if (!beginHomePagesCrossing(front, toward)) return;
        }
        com.termux.app.place.PlaceChromeTravel.Rest rest = mHomeCrossRest;
        if (rest == null) return;
        int widthPx = mHost.paneWallController().wall().getWidth();
        com.termux.app.place.PlaceChromeTravel.Frame frame =
            com.termux.app.place.PlaceChromeTravel.homePages(rest, front, toward,
                fraction * widthPx, widthPx);
        syncHomePagesFrame(frame);
        // The landing waits for the settle under way, the page that comes in or the one coming back.
        if (settling && !mHomeCrossLandingQueued) {
            mHomeCrossLandingQueued = true;
            mHost.runWhenHomePageSettles(mLandHomePagesCrossingAfterSettle);
        }
    }

    /** The pager's step read once (mHomeStep*): which Home pages its frames name. */
    private void readHomeStep(@NonNull com.termux.app.wall.WallPages pages, int index, int side,
                              int revision) {
        mHomeStepIndex = index;
        mHomeStepSide = side;
        mHomeStepPages = pages;
        mHomeStepRevision = revision;
        mHomeStepTried = false;
        java.util.List<Integer> ids = pages.homePageIdsForRepository();
        mHomeStepFrontId = index >= 0 && index < ids.size() ? ids.get(index) : -1;
        int neighbour = index + side;
        // A neighbour past the run Home's frame pages inside is never turned to (ADR 0015, A2a).
        boolean inRun = mHost.homeRunLast() < 0
            || (neighbour >= mHost.homeRunFirst() && neighbour <= mHost.homeRunLast());
        mHomeStepTowardId = side != 0 && inRun && neighbour >= 0 && neighbour < ids.size()
            ? ids.get(neighbour) : -1;
    }

    /**
     * A crossing between Home pages {@code hereId}, in front, and {@code thereId}, its neighbour,
     * modelled on the wall's (readSeamTravel): both ends with their dresses, arrangements resolved
     * for their own ids, and rests from one snapshot holding the second Home half
     * (DockGeometry.Snapshot#otherHome). Begun only where the two cross (SeamCrossing#crosses).
     * Whether it began.
     */
    private boolean beginHomePagesCrossing(int hereId, int thereId) {
        if (mHost.preferences() == null || mHost.chrome() == null || mHost.paneWallController() == null) return false;
        // Reduced motion and Animation speed Off land the page at once: the layout switches with it.
        if (mHost.isReducedMotionEnabled() || com.termux.app.AnimationSpeed.multiplier(mHost.context()) <= 0f)
            return false;
        com.termux.app.wall.PaneWallLayout wall = mHost.paneWallController().wall();
        if (mHost.lastWallPage() != com.termux.app.wall.PaneWallPage.WIDGETS || wall.isMoving()
            || mHost.keyboardSwipeTravel() || mHost.isAppDrawerEngaged() || mHost.surfaceEditorActive()
            || mHost.appliedContentReservationPx() < 0 || mCrossing.crossing()) return false;
        PlaceLayoutStore store = mHost.placeLayoutStore();
        if (store == null) return false;
        PlaceOrientation orientation = mHost.currentPlaceOrientation();
        com.termux.app.wall.PageRef herePage =
            com.termux.app.wall.PageRef.of(com.termux.app.wall.PaneWallPage.WIDGETS, hereId);
        com.termux.app.wall.PageRef therePage =
            com.termux.app.wall.PageRef.of(com.termux.app.wall.PaneWallPage.WIDGETS, thereId);
        // The cheap refusals first (SeamCrossing#crosses): two Home pages on one layout arrange
        // alike, and two that dress the dock or the search field apart flip; neither crosses.
        if (mHost.dressLayoutOf(herePage) == mHost.dressLayoutOf(therePage)) return false;
        com.termux.app.chrome.Dress hereDress = mHost.dressOf(herePage);
        com.termux.app.chrome.Dress thereDress = mHost.dressOf(therePage);
        if (hereDress.dockDesign() != thereDress.dockDesign()
            || hereDress.searchField() != thereDress.searchField()) return false;
        ResolvedArrangement hereArrangement = mHost.arrangementOf(herePage);
        ResolvedArrangement thereArrangement = mHost.arrangementOf(therePage);
        PlaceLayout hereLayout = hereArrangement.layout();
        PlaceLayout thereLayout = thereArrangement.layout();
        // One keyboard across Home's pages: up on both or on neither, as it is now.
        boolean keyboardUp = mHost.committedKeyboardVisible() && !mHost.isKeyboardFloating();
        // Both ends' docks at rest from one snapshot, the arriving page's as its second Home half.
        com.termux.app.dock.DockGeometry.Snapshot rests =
            mHost.dockSnapshot(hereLayout, com.termux.app.wall.PaneWallPage.WIDGETS).toBuilder()
                .otherHome(mHost.dockHalf(therePage, orientation, thereLayout))
                .build();
        com.termux.app.dock.DockLayout hereRest =
            com.termux.app.dock.DockGeometry.compute(rests.restOf(herePage));
        com.termux.app.dock.DockLayout thereRest =
            com.termux.app.dock.DockGeometry.compute(rests.restOf(therePage));
        com.termux.app.place.SeamCrossing.Endpoint here =
            com.termux.app.place.SeamCrossing.Endpoint.of(herePage, hereDress,
                mHost.pageLookOf(herePage), hereArrangement, hereLayout,
                stackDressOf(hereDress, herePage, orientation),
                keyboardUp, com.termux.app.dock.DockMorph.rowsPx(hereRest));
        com.termux.app.place.SeamCrossing.Endpoint there =
            com.termux.app.place.SeamCrossing.Endpoint.of(therePage, thereDress,
                mHost.pageLookOf(therePage), thereArrangement, thereLayout,
                stackDressOf(thereDress, therePage, orientation),
                keyboardUp, com.termux.app.dock.DockMorph.rowsPx(thereRest));
        // Two Home pages that dress alike are a plain flip.
        if (!com.termux.app.place.SeamCrossing.crosses(here, there)) return false;
        mHomeCrossRest = mHost.chromeRestOf(com.termux.app.wall.PaneWallPage.WIDGETS);
        mHomeCrossHereId = hereId;
        mHomeCrossThereId = thereId;
        // A bar only one of them wears rides with its page while Home's pages slide flat, and
        // fades in place under a transition that turns them.
        boolean pagesSlide = wall.pageTransition()
            == com.termux.app.launcher.home.HomePageTransition.SLIDE;
        mCrossing.begin(here, there,
            com.termux.app.place.SeamCrossing.Stage.of(mHost.dockBeads() != null, mHost.frameStandsDown(),
                mHost.isSplitPanesEnabled(), pagesSlide),
            hereRest, thereRest, mHost.arrangementRevision());
        return true;
    }

    /**
     * One frame of a crossing between two Home pages, the wall's travel pass (doSyncChromeTravel)
     * without what only the wall's slide has: no keyboard or content pre-roll, no terminal
     * displacement, no material blend, no band ease or bead melt (both ends dress the dock alike).
     * The dock's rows morph and rise or sink by the crossing's answers, and the bar rides with the
     * page that wears it or fades by the way toward it (SeamCrossing.Answers#statusFade).
     */
    private void syncHomePagesFrame(@NonNull com.termux.app.place.PlaceChromeTravel.Frame frame) {
        Trace.beginSection("Home.pagesTravel");
        try {
            com.termux.app.place.SeamCrossing.Answers seam = mCrossing.frame(frame,
                mHost.preferences() != null && mHost.chrome() != null, mHost.dockBandEaseRunning(),
                mHost.promptFieldUnderPalette());
            float dockReveal = syncSeamTravel(seam);
            int keyboardPx = mHost.travelKeyboardLaidOutPx();
            int dockPx = travelDockLaidOutPx(keyboardPx);
            boolean wasMoved = mHost.dockTravelTranslationPx() != 0f;
            float translation = mCrossing.stack(seam, dockReveal, keyboardPx, dockPx,
                travelDockRowsPx(keyboardPx));
            if (translation != 0f || wasMoved) mHost.setChromeTravelMoved(true);
            if (mCrossing.holdsContent()) mCrossing.coverAt(translation, mHost.stackPictureCoverPx());
            boolean barRides = mHost.syncStatusTravel(frame, seam);
            float fade = seam.statusFade();
            float statusAlpha = barRides ? 1f : !Float.isNaN(fade) ? fade : frame.statusReveal;
            if (statusAlpha != 1f || frame.dockReveal != 1f) mHost.setChromeTravelMoved(true);
            mCrossing.fade(statusAlpha, frame.dockReveal);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * The crossing between two Home pages lands on the Home page in front (the one the pager
     * settled on, or the committed one after a settle back) through the wall's own settle, the
     * wall resting on Home: transforms put back, the hold let go, the landed page's layout
     * applied. Nothing while none runs.
     */
    public void landHomePagesCrossing() {
        mHomeCrossLandingQueued = false;
        if (!homePagesCrossing()) return;
        int committedId = mHomeCrossHereId;
        int landedId = mHost.homePageInFront();
        mHomeCrossHereId = -1;
        mHomeCrossThereId = -1;
        mHomeCrossRest = null;
        mHost.settlePlaceChrome(com.termux.app.wall.PaneWallPage.WIDGETS,
            com.termux.app.wall.PageRef.of(com.termux.app.wall.PaneWallPage.WIDGETS, landedId));
        // Landed on the other page: its layout is applied now, even where a re-arrangement that
        // waited for the settle (onWidgetPageRendered) already ran under the crossing's hold and
        // noted the variant applied.
        if (landedId != committedId) {
            mHost.setAppliedChromeVariant(null);
            mHost.syncChromeArrangement();
        }
    }

    /**
     * The seam as this frame reaches it. Once per gesture, on the first frame that reaches the
     * other half, the crossing begins (SeamCrossing.begin): it is handed both halves' dresses, their
     * layouts and which of them keeps a keyboard in the stack, and reads from them how the dock's
     * rows cross, whether the halves dress the stack differently and how the bar crosses. The
     * status bar wears the Workspace's dress from then on (SeamDress), so a bar about to be laid
     * out over Home is dressed for it now. Every frame after is the crossing's to answer.
     */
    private void readSeamTravel(@NonNull com.termux.app.place.PlaceChromeTravel.Frame frame) {
        com.termux.app.wall.PaneWallPage other =
            com.termux.app.place.SeamTravel.otherHalfPage(frame, mHost.lastWallPage());
        PlaceLayoutStore store = mHost.placeLayoutStore();
        if (store == null) return;
        if (other == null || other == mCrossing.seam().other()) return;
        // A landing's re-dress still waiting for its post is had before a new crossing reads
        // the halves' dresses.
        if (!mCrossing.crossing()) mHost.flushLandingRestyle();
        PlaceOrientation orientation = mHost.currentPlaceOrientation();
        PlaceLayout committed = mHost.committedArrangement().layout();
        ResolvedArrangement otherArrangement = mHost.arrangementOf(other);
        PlaceLayout otherLayout = otherArrangement.layout();
        com.termux.app.chrome.Dress committedDress = mHost.dressOf(mHost.lastWallPage());
        com.termux.app.chrome.Dress otherDress = mHost.dressOf(other);
        boolean begins = !mCrossing.crossing();
        // The crossing's two ends: the committed page and the one this frame reaches.
        com.termux.app.wall.PageRef herePage = mHost.endpointPageOf(mHost.lastWallPage());
        com.termux.app.wall.PageRef therePage = mHost.endpointPageOf(other);
        // Both ends' docks at rest, worked out once as the crossing begins and held for its
        // life (ADR 0012): an end's rest is its own (Stack layout), its rows in its dress, its
        // Search field and Wall strip wholly in or out, nothing squeezed, whichever end is
        // committed, so one snapshot answers both. A frame that reaches another page of the same
        // half reads the ends' dresses again and the rests the crossing holds.
        com.termux.app.dock.DockLayout hereRest;
        com.termux.app.dock.DockLayout thereRest;
        com.termux.app.dock.DockGeometry.Snapshot rests = null;
        if (begins) {
            rests = mHost.dockSnapshot(committed, mHost.lastWallPage());
            hereRest = com.termux.app.dock.DockGeometry.compute(rests.restOf(herePage));
            thereRest = com.termux.app.dock.DockGeometry.compute(rests.restOf(therePage));
        } else {
            hereRest = mCrossing.heldRest(herePage);
            thereRest = mCrossing.heldRest(therePage);
            if (hereRest == null || thereRest == null) return;
        }
        boolean otherHome = therePage.isHome();
        // A keyboard in the stack, not one floating in a frame of its own: the committed half's
        // as it is up now, the other's as it wants one on arrival.
        // And each half's dock rows at rest, less the extra keys, so a fold travels from one
        // half's rest to the other's (SeamCrossing.foldRestGrowthPx); unknown before the
        // preferences are read.
        int committedRowsPx = mHost.preferences() == null ? -1
            : com.termux.app.dock.DockMorph.rowsPx(hereRest);
        int otherRowsPx = mHost.preferences() == null ? -1
            : com.termux.app.dock.DockMorph.rowsPx(thereRest);
        // Each end carries its page's arrangement, held for the crossing's life with the revision
        // it was resolved at: from here every chrome reader reads them (arrangementOf).
        com.termux.app.place.SeamCrossing.Endpoint here =
            com.termux.app.place.SeamCrossing.Endpoint.of(herePage, committedDress,
                mHost.pageLookOf(herePage), mHost.committedArrangement(), committed,
                stackDressOf(committedDress, herePage, orientation),
                mHost.committedKeyboardVisible() && !mHost.isKeyboardFloating(), committedRowsPx);
        com.termux.app.place.SeamCrossing.Endpoint there =
            com.termux.app.place.SeamCrossing.Endpoint.of(therePage, otherDress,
                mHost.pageLookOf(therePage), otherArrangement, otherLayout,
                stackDressOf(otherDress, therePage, orientation),
                mHost.inAppKeyboard() != null && mHost.inAppKeyboard().isEnabled() && mHost.wantsKeyboardOnEnter(other),
                otherRowsPx);
        // The pages turn across the seam by the page transition; a bar that cannot stand still
        // then fades in place rather than riding a turned page (SeamCrossing.Stage#pagesSlide).
        boolean pagesSlide = mHost.paneWallController() == null
            || mHost.paneWallController().wall().pageTransition()
                == com.termux.app.launcher.home.HomePageTransition.SLIDE;
        mCrossing.begin(here, there,
            com.termux.app.place.SeamCrossing.Stage.of(mHost.dockBeads() != null, mHost.frameStandsDown(),
                mHost.isSplitPanesEnabled(), pagesSlide),
            hereRest, thereRest, mHost.arrangementRevision());
        if (!begins) return;
        // A look apart alone blends the dock's glass from here to there (syncStackLookBlend).
        mLookBlendShare = Float.NaN;
        mLookBlendHereOpacity = mHost.preferences() == null ? 100
            : mHost.preferences().getAppBarOpacity(mHost.lookIdOf(herePage));
        mLookBlendThereOpacity = mHost.preferences() == null ? 100
            : mHost.preferences().getAppBarOpacity(mHost.lookIdOf(therePage));
        beginStackLookBlend(therePage);
        // The strip under the pill stands still through the crossing and crosses dresses by alpha.
        mHost.beginNavStripFade(herePage, therePage);
        mHost.beginTopBandFade(herePage, therePage);
        // From here to the landing the chrome travels over the wallpaper: its bands keep the
        // even-out they stand on, so the ground moving under them asks for no whole apply each
        // frame. The landing lets it go after its own frame (runLandingSettle).
        if (mHost.chrome() != null) mHost.chrome().holdEvenOut();
        // Toward Home, the grid is given the room Home's dock leaves it at rest now, so it is laid
        // out at its final size for the slide rather than re-measured at the landing.
        if (otherHome && rests != null)
            applyArrivingHomeGridReserve(rests, therePage, thereRest, otherArrangement);
        // From Home, the bar takes off Home's dress for the Workspace's while it is still hidden,
        // before any pass lays it out over Home.
        if (mCrossing.seam().barTakesWorkspaceDressFirst()) {
            mHost.refreshTerminalWindowBar();
            mHost.chrome().requestSync(ChromeRenderer.SCOPE_TOP_PANE_FROST);
        }
    }

    /**
     * The widget pane given the band Home's dock takes from it at rest, as a crossing toward Home
     * begins: Home's own rows in its own dress, its status band and whether its keyboard comes up
     * (Stack layout's rest), the figure Home's settle lays out. From here to the landing no pass
     * hands it another (syncAccessoryStack).
     */
    private void applyArrivingHomeGridReserve(
            @NonNull com.termux.app.dock.DockGeometry.Snapshot rests,
            @NonNull com.termux.app.wall.PageRef homePage,
            @NonNull com.termux.app.dock.DockLayout homeRest, @NonNull ResolvedArrangement home) {
        View widgetPane = mHost.findViewById(R.id.widget_pane);
        if (!(widgetPane instanceof com.termux.app.launcher.widget.WidgetPaneView)) return;
        com.termux.app.place.StackLayout.RestInput in =
            new com.termux.app.place.StackLayout.RestInput(rests, homePage);
        in.toolbarShown = home.dockShown();
        in.extraKeysRowEnabled = home.extraKeysRowShown();
        in.toolbarHeightPx = mHost.appliedToolbarHeightPx();
        in.statusBand = mHost::bottomStatusBandAtRestPx;
        in.imeLiftPx = mHost.imeLiftPx();
        in.keyboardShown = mHost.inAppKeyboard() != null && mHost.inAppKeyboard().isEnabled()
            && mHost.wantsKeyboardOnEnter(com.termux.app.wall.PaneWallPage.WIDGETS);
        ((com.termux.app.launcher.widget.WidgetPaneView) widgetPane).setDockReservePx(
            com.termux.app.place.StackLayout.restGridDockReservePx(in, homeRest));
    }

    /**
     * What the stack wears in {@code dress} on {@code page}: its Style, Corners, Margin, its
     * layout's sizes and the dock's and keyboard's look on the page's half.
     */
    @NonNull
    private com.termux.app.place.SeamDress.StackDress stackDressOf(
            @NonNull com.termux.app.chrome.Dress dress,
            @NonNull com.termux.app.wall.PageRef page, @NonNull PlaceOrientation orientation) {
        PlaceLayoutStore store = mHost.placeLayoutStore();
        com.termux.app.place.WallShape shape = dress.shape();
        if (store == null)
            return com.termux.app.place.SeamDress.StackDress.of(shape, Float.NaN, Float.NaN, -1);
        PlaceLayoutStore sizes = store.forVariant(dress.layout());
        return com.termux.app.place.SeamDress.StackDress.of(shape,
            sizes.dockHeightScale(orientation), sizes.keyboardHeightScale(orientation),
            sizes.keyboardChinDp(orientation), com.termux.app.place.HalfLook.read(mHost.preferences(),
                mHost.lookIdOf(page)));
    }

    /**
     * One frame of the dock's rows across the seam between Home and the Workspace, as the crossing
     * answers it: the stack stands the other half's rows, dressed for that half, from the frame
     * the plan says — laid out there once, while none of them shows, under the content's hold —
     * and from Home the Workspace's status bar is laid out as the slide starts, off screen with
     * its page. Returns the dock's reveal for this frame.
     */
    private float syncSeamTravel(@NonNull com.termux.app.place.SeamCrossing.Answers seam) {
        if (seam.relayout()) {
            Trace.beginSection("Wall.seamLayout");
            try {
                // The content keeps the room it was committed with: a stack that grows or shrinks
                // here, or a bar that comes, moves nothing it holds.
                mHost.beginTravelHold();
                mCrossing.seam().standLayout();
                // A pictured dip toward a half without a keyboard: the keyboard leaves with the
                // picture, so the stack is laid out in this pass as the other half rests — its
                // rows, its glass and its foot measured without a keyboard under them, as the
                // settle would lay them out — instead of over a keyboard pushed below the screen.
                mHost.putKeyboardAwayWithPicture();
                mHost.setChromeTravelMoved(true);
                // The stack changes halves with its rows, so it is dressed for the half it now
                // stands in this same pass, while none of it shows (SeamDress). The sizes follow
                // through the pass below (applyPlaceSizes).
                if (seam.restyle()) mHost.restyleStackForDress(seam.restyleShape());
                mHost.syncPlaceLayout();
                // The rows it now stands carry their own half's Drawer tile, laid in while none
                // of them shows.
                mHost.syncDrawerTile();
                // The slab's Dock design goes with the stack's dress, on the frame it changes
                // halves while none of it shows — a pictured dip's first, a dip's swap, either way
                // round — so the dock rises as it rests and the settle has nothing to re-dress
                // (SeamCrossing#stackDesign), whether a finger, a home press or a tap started it.
                if (seam.restyle()) {
                    com.termux.app.wall.PaneWallPage wears =
                        mCrossing.seam().stackDesignApart() ? mCrossing.seam().other() : null;
                    if (wears != mHost.dockDesignTravelledTo()) {
                        mHost.setDockDesignTravelledTo(wears);
                        mHost.restyleDockSurface();
                    }
                }
                // In this frame, so the stack's height the translation below reads is the new one.
                mHost.geometryRequestNow(GeometryScheduler.Reason.PLACE_TRAVEL,
                    GeometryScheduler.ResizePolicy.AT_SETTLE);
                // The Peek card is cut for the rows this pass stands in this frame's pre-draw,
                // after the traversal lays them out and before any of them shows. The listeners
                // are set here, once the pass above has applied the rows' design: until now only
                // the render pass's re-cut set them, and on pong the card then landed with the
                // dock already half risen.
                mHost.syncPeekListeners();
                if (mHost.hasPeekWatchedHost()) mHost.schedulePeekRefresh();
            } finally {
                Trace.endSection();
            }
        }
        if (seam.moved()) mHost.setChromeTravelMoved(true);
        return seam.dockReveal();
    }

    /** The dock's opacity at each end of a look blend, read as the crossing begins. */
    private int mLookBlendHereOpacity = 100, mLookBlendThereOpacity = 100;
    /** The blend's step last applied; NaN while none has been. */
    private float mLookBlendShare = Float.NaN;

    /**
     * One frame of a crossing between halves that dress the stack apart only in look
     * (SeamCrossing.Answers#lookShare): the dock's frost, which carries its opacity, is set at the
     * blend's step between the two ends' opacities, over the even-out matrix last measured. Set
     * only when the step changes (at most SeamCrossing.LOOK_STEPS times a crossing), never at
     * rest, and nothing is laid out; the RenderEffect (blur and lens) is left as it is. The
     * landing's re-dress then sets the arriving look, which the last step already showed.
     */
    private void syncStackLookBlend(@NonNull com.termux.app.place.SeamCrossing.Answers seam) {
        float share = seam.lookShare();
        if (Float.isNaN(share) || share == mLookBlendShare || mHost.chrome() == null) return;
        mLookBlendShare = share;
        // The dock's sheet, its opacity, tint and grain: a redraw at the step, nothing built.
        if (mLookBlendDock != null) mLookBlendDock.setShare(share);
        android.widget.ImageView backdrop = mHost.hotView(R.id.accessory_blur_backdrop);
        if (backdrop == null || backdrop.getVisibility() != View.VISIBLE) return;
        int opacity = com.termux.app.place.SeamCrossing.lookBlendValue(mLookBlendHereOpacity,
            mLookBlendThereOpacity, share);
        backdrop.setColorFilter(com.termux.app.chrome.GlassFilters.withEvenOut(
            mHost.chrome().ink().lastBandEvenOutMatrix(com.termux.app.chrome.GlassBackdropCache.Band.DOCK),
            com.termux.app.chrome.GlassFilters.frost(opacity)));
    }

    /**
     * The dock's sheet across a look-only crossing: the glass it wears beside the arriving end's,
     * built once as the crossing begins, crossed by the blend's step
     * ({@link #syncStackLookBlend}). The sheet carries the dock's opacity, tint and grain (the
     * frost filter alone carries only the Clear step), so this is the blend the eye sees. Null
     * while none runs.
     */
    @Nullable private com.termux.app.chrome.CrossfadeDrawable mLookBlendDock;

    private void beginStackLookBlend(@NonNull com.termux.app.wall.PageRef there) {
        endStackLookBlend();
        if (mHost.chrome() == null || !mCrossing.seam().lookBlends()) return;
        View sheet = mHost.findViewById(R.id.extrakeys_background);
        Drawable here = sheet == null ? null : sheet.getBackground();
        // Standing down on a joined Docked frame, the sheet draws nothing to blend.
        if (here == null) return;
        mLookBlendDock = new com.termux.app.chrome.CrossfadeDrawable(here,
            mHost.chrome().glass().dockSurfaceOf(mHost.lookIdOf(there), 0f, 1f, false), 0f);
        sheet.setBackground(mLookBlendDock);
    }

    /** The blend is over: the sheet keeps the end it reached, nothing rebuilt. */
    public void endStackLookBlend() {
        com.termux.app.chrome.CrossfadeDrawable blend = mLookBlendDock;
        mLookBlendDock = null;
        if (blend == null) return;
        View sheet = mHost.findViewById(R.id.extrakeys_background);
        if (sheet != null && sheet.getBackground() == blend) sheet.setBackground(blend.endpoint());
    }

    /**
     * How much more room the content has at {@code arriving} than at the place the chrome is
     * committed to: positive is room given back on the way there, negative is room the arriving
     * place's chrome takes. Both places are read off one model of a stack at rest
     * ({@link KeyboardOverlayPolicy#restReservationPx}), so the difference is exact where the
     * model is — the keyboard's room, the margin under the stack — and the settle's own pass finds
     * nothing left to move. The terminal's dock padding is known only for the place on screen and
     * is a fraction of a row; arriving on the terminal, the settle takes it as it always has.
     */
    public int travelRoomChangePx(@NonNull com.termux.app.wall.PaneWallPage arriving) {
        if (mCrossing.knowsRoomToward(arriving)) return mCrossing.roomChangePx();
        mCrossing.roomChange(arriving, travelRestReservationPx(mHost.lastWallPage())
            - travelRestReservationPx(arriving));
        return mCrossing.roomChangePx();
    }

    /** The room a place's chrome leaves the content at rest, from the stack as it stands. */
    public int travelRestReservationPx(@NonNull com.termux.app.wall.PaneWallPage place) {
        com.termux.app.place.PlaceChromeTravel.Rest rest = mHost.chromeRestOf(place);
        return travelRestReservationPx(place, rest.keyboardReveal() > 0f);
    }

    /**
     * The same, for the place with its keyboard up or down as {@code keyboardShown} says. Stack
     * layout answers it from the place's own rest: its own rows (its dock and extra-keys row, at
     * the height the geometry pass gives that row), Search field and Wall strip, whichever half is
     * committed. Read off the half on screen, the Terminal's rest from Home came out an extra-keys
     * row short, and its landing resized the grid a second time.
     */
    public int travelRestReservationPx(@NonNull com.termux.app.wall.PaneWallPage place,
                                        boolean keyboardShown) {
        com.termux.app.wall.PageRef side = mHost.arrangedPageOf(place);
        com.termux.app.dock.DockGeometry.Snapshot snapshot =
            mHost.dockSnapshot(mHost.arrangement().layout(), mHost.stackDressPage());
        com.termux.app.place.StackLayout.RestInput in =
            new com.termux.app.place.StackLayout.RestInput(snapshot, side)
                .ownRows(mHost.measuredExtraKeysRowPx());
        in.statusBand = mHost::bottomStatusBandAtRestPx;
        in.imeLiftPx = mHost.imeLiftPx();
        in.keyboardShown = keyboardShown;
        // The keyboard's height as the pass measures it while one shows (ChromeSpec), else the
        // last one measured.
        in.keyboardHeightPx = Math.max(0, mHost.isInAppKeyboardShown()
            ? mHost.keyboardMeasureHeightPx() : mHost.keyboardDesiredHeightPx());
        // The place's own arrangement decides it, like its Style: the one on screen when it is the
        // committed place, else its half's stored layout.
        PlaceLayout placeLayout = place == mHost.lastWallPage()
            ? mHost.arrangement().layout() : snapshot.half(side).layout;
        in.keyboardOverlays = KeyboardOverlayPolicy.overlays(place, placeLayout);
        in.contentUnderStack = KeyboardOverlayPolicy.contentUnderStack(place, placeLayout);
        // The Terminal's flush padding is part of its rest: the applied one while it is the place,
        // else the one it last rested with, so Home's terminal page and the slide back are laid
        // out at the height the landing settles at, not one padding taller.
        in.flushPaddingPx = place != com.termux.app.wall.PaneWallPage.TERMINAL || keyboardShown ? 0
            : place == mHost.lastWallPage()
                ? (mHost.appliedStack() != null ? mHost.appliedStack().flushPaddingPx : 0)
                : mHost.terminalRestFlushPaddingPx();
        return com.termux.app.place.StackLayout.restReservationPx(in);
    }

    /**
     * Everything else the stack lays out above the keyboard, and the margin under it: what has to
     * slide down for the dock to leave the screen. A status bar standing along the bottom is a
     * band of the same stack but not the dock's, so it is left out; it rides along with the
     * container.
     */
    private int travelDockLaidOutPx(int keyboardPx) {
        View stack = mHost.hotView(R.id.accessory_stack_container);
        if (stack == null) return 0;
        ViewGroup.LayoutParams params = stack.getLayoutParams();
        int margin = params instanceof ViewGroup.MarginLayoutParams
            ? Math.max(0, ((ViewGroup.MarginLayoutParams) params).bottomMargin) : 0;
        return com.termux.app.place.PlaceChromeTravel.dockLaidOutPx(travelDockRowsPx(keyboardPx),
            margin);
    }

    /** The dock's rows alone as the stack lays them out above the keyboard, without the margin. */
    private int travelDockRowsPx(int keyboardPx) {
        View stack = mHost.hotView(R.id.accessory_stack_container);
        if (stack == null) return 0;
        ViewGroup.LayoutParams params = stack.getLayoutParams();
        int height = params != null && params.height > 0 ? params.height : 0;
        return com.termux.app.place.PlaceChromeTravel.dockRowsLaidOutPx(height, keyboardPx,
            mHost.bottomStatusBandPx());
    }

    /**
     * One frame of a slide across the seam while one half's dock draws Lens beads and the
     * other's does not (SeamCrossing.Answers#beadShare): the beads melt into the slab as the beaded
     * half slides away, swelling under their icons until, cut to the slab's outline, they are the
     * slab, and come back out of it as that half returns, as the Search field's and the Wall
     * strip's bands follow their halves' shares (TermuxActivity#syncSeamBandShare). Where the beaded
     * half's rows leave the screen on the way out they melt over the wall's first stretch, while
     * the chrome still rests (LensBeadGeometry.leavingMeltShare), so they sink as the slab rather
     * than as beads cut flat by the stack's bottom edge; their shadows and glow fade as they
     * swell. Each frame the share moves re-cuts the host's clip and rebuilds the dock's effect
     * once; nothing where both halves draw the same (one layout, one design), and nothing at rest.
     */
    private void syncSeamBeadMelt(@NonNull com.termux.app.place.SeamCrossing.Answers seam) {
        // The crossing answers the share (NaN while nothing melts); this draws it.
        float share = seam.beadShare();
        if (Float.isNaN(share) || Float.compare(share, mCrossing.beadShare()) == 0) return;
        mCrossing.beadShare(share);
        mHost.applyDockBeadMelt(false);
    }
}
