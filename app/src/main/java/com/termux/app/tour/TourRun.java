package com.termux.app.tour;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.place.PlaceLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The run, as data: six gestures in three chapters, then the closing card, taught on the wall
 * (ADR 0008).
 *
 * <p>Chapter one is Home: the plain swipe across the seam into the Workspace and the border drag
 * back, then the apps that float over Home. Chapter two is the Workspace: the keyboard swipe off a
 * window's bottom border, both ways, and the status bar's fold off its top border, both ways.
 * Chapter three works anywhere: the command palette, and the corner tab that holds help. The Home
 * float and the Layout editor are on the closing card; everything else is in help, and nothing here
 * opens a shell, a window or a session.
 *
 * <p>Every stage names the half of the wall its control stands on. Before each lesson the run puts
 * the wall on the half its first stage is taught on ({@link #startSide}): Home page 1, or the
 * first window. A user who wanders to the other half mid-lesson is asked the way back.
 *
 * <p>The setup sheet that comes before the first lesson is not a step: it is offered before the
 * run and is never stored, so a run picked up after a process death goes straight to its lesson.
 *
 * <p>Lesson ids are the ones help's "Try it" names, kept stable across versions of the run.
 */
public final class TourRun {

    /** Chapter one: across the seam into the Workspace, and back with a border drag. */
    public static final String PAGE_SWIPE = "page_swipe";
    /** Chapter one: open the app drawer off the dock's apps row on Home. */
    public static final String FIND_APPS = "find_apps";
    /** Chapter two: put the keyboard away and bring it back, off a window's bottom border. */
    public static final String KEYBOARD = "keyboard";
    /** Chapter two: pull the status bar open off a window's top border, then fold it again. */
    public static final String STATUS_SWIPE = "status_swipe";
    /** Chapter three: open the command palette, from Home's free area or a corner tab. */
    public static final String FIND_ACTION = "find_action";
    /** Chapter three: hold a window's corner, then tap the ? in the corner tab it raises. */
    public static final String FIND_HELP = "find_help";
    /** The last card. */
    public static final String CLOSING = "closing";

    /** The six lessons, in the order the run teaches them. */
    private static final List<String> LESSONS = Collections.unmodifiableList(Arrays.asList(
        PAGE_SWIPE, FIND_APPS, KEYBOARD, STATUS_SWIPE, FIND_ACTION, FIND_HELP));

    /** What the run has to know about the phone it is running on. */
    public static final class RunContext {

        /** The edge the apps row stands on; the bottom while the row is put away. */
        @NonNull public final PlaceLayout.Edge appsEdge;

        public RunContext(@NonNull PlaceLayout.Edge appsEdge) {
            this.appsEdge = appsEdge;
        }
    }

    /** The run, in order, for the phone described by {@code context}. */
    @NonNull
    public static List<TourStep> steps(@NonNull RunContext context) {
        return Collections.unmodifiableList(Arrays.asList(
            pageSwipe(), findApps(context), keyboard(), statusSwipe(), findAction(), findHelp(),
            TourStep.closing(CLOSING)));
    }

    /** The lessons, in order: what help's practice may name. */
    @NonNull
    public static List<String> lessons() {
        return LESSONS;
    }

    /**
     * Where the wall is put before {@code step} shows: the half its first stage is taught on.
     * A first stage read on either half starts on Home, where its glow is (the palette's free
     * area); the closing card, and no card at all, leave the wall where the last lesson ended.
     */
    @NonNull
    public static TourStep.Side startSide(@androidx.annotation.Nullable TourStep step) {
        if (step == null || step.isClosingCard()) return TourStep.Side.ANYWHERE;
        TourStep.Side first = step.sideAt(0);
        return first == TourStep.Side.ANYWHERE ? TourStep.Side.HOME : first;
    }

    /**
     * The lessons this wall has nothing for. Without a Workspace window to slide to there is no
     * seam to cross and no border to drag back; without Home there is neither the seam nor the
     * dock rows that float over Home.
     *
     * @param hasHome whether Home is on the wall
     * @param hasWorkspaceWindow whether the wall has a Workspace window beside Home
     */
    @NonNull
    public static List<String> droppedFor(boolean hasHome, boolean hasWorkspaceWindow) {
        List<String> dropped = new ArrayList<>();
        if (!hasHome || !hasWorkspaceWindow) dropped.add(PAGE_SWIPE);
        if (!hasHome) dropped.add(FIND_APPS);
        return Collections.unmodifiableList(dropped);
    }

    /**
     * From Home page 1 a plain swipe pages Home and, past the last Home page, crosses the seam
     * onto the first window; a sideways swipe along that window's border slides back. Each half is
     * cleared by the wall settling on the other side of the seam.
     */
    private static TourStep pageSwipe() {
        return TourStep.lesson(PAGE_SWIPE, 0, R.string.tour_chapter_home,
            TourStep.Placement.AUTO,
            new TourStep.Stage(TourStep.Side.HOME, R.string.tour_title_to_workspace,
                R.string.tour_body_to_workspace, TourTargets.HOME_PAGE,
                TourSignals.SEAM_TO_WORKSPACE, TourGesture.SWIPE_LEFT),
            new TourStep.Stage(TourStep.Side.WORKSPACE, R.string.tour_title_border_drag,
                R.string.tour_body_border_drag, TourTargets.PAGE_BORDER,
                TourSignals.SEAM_TO_HOME, TourGesture.SWIPE_RIGHT));
    }

    /**
     * The drawer is pulled down off a row along the top or bottom and inward off a rail, so the
     * sentence follows the edge the row stands on; the overlay turns the demonstration the same way.
     */
    private static TourStep findApps(@NonNull RunContext context) {
        PlaceLayout.Edge edge = context.appsEdge;
        int body = edge == PlaceLayout.Edge.LEFT ? R.string.tour_body_apps_left_rail
            : edge == PlaceLayout.Edge.RIGHT ? R.string.tour_body_apps_right_rail
            : R.string.tour_body_apps;
        return TourStep.lesson(FIND_APPS, 0, R.string.tour_chapter_home,
            TourStep.Placement.AUTO,
            new TourStep.Stage(TourStep.Side.HOME, R.string.tour_title_apps, body,
                TourTargets.DOCK, TourSignals.DRAWER_OPENED, TourGesture.DRAG_DOWN));
    }

    /**
     * The keyboard swipe on a window, both ways round. The run starts the lesson on a window with
     * the keyboard up, so the first half always has a keyboard to put away.
     */
    private static TourStep keyboard() {
        return TourStep.lesson(KEYBOARD, 1, R.string.tour_chapter_workspace,
            TourStep.Placement.AUTO,
            new TourStep.Stage(TourStep.Side.WORKSPACE, R.string.tour_title_keyboard_away,
                R.string.tour_body_keyboard_away, TourTargets.KEYBOARD_GRABBER,
                TourSignals.KEYBOARD_HIDDEN, TourGesture.DRAG_DOWN),
            new TourStep.Stage(TourStep.Side.WORKSPACE, R.string.tour_title_keyboard_back,
                R.string.tour_body_keyboard_back, TourTargets.KEYBOARD_GRABBER,
                TourSignals.KEYBOARD_SHOWN, TourGesture.SWIPE_UP));
    }

    /**
     * The status swipe off a window's top border: a slow pull lands the bar on the form nearest
     * where the finger lets go, so a long one opens it whole; a quick swipe up folds it one form.
     * The bar folding to the capsule on its own counts too: it is folded either way.
     */
    private static TourStep statusSwipe() {
        return TourStep.lesson(STATUS_SWIPE, 1, R.string.tour_chapter_workspace,
            TourStep.Placement.AUTO,
            new TourStep.Stage(TourStep.Side.WORKSPACE, R.string.tour_title_status,
                R.string.tour_body_status, TourTargets.STATUS_GRABBER,
                TourSignals.STATUS_BAR_EXPANDED, TourGesture.DRAG_DOWN),
            new TourStep.Stage(TourStep.Side.WORKSPACE, R.string.tour_title_status_fold,
                R.string.tour_body_status_fold, TourTargets.STATUS_GRABBER,
                TourSignals.STATUS_BAR_COLLAPSED, TourGesture.SWIPE_UP));
    }

    /**
     * The palette gesture: a swipe up on Home's free area raises the palette with the keyboard,
     * and a window's corner tab has it too, so the card reads on either half and any open clears
     * it. It glows Home's free area, and asks to stand above it, where the palette sprouts from.
     */
    private static TourStep findAction() {
        return TourStep.lesson(FIND_ACTION, 2, R.string.tour_chapter_anywhere,
            TourStep.Placement.ABOVE,
            new TourStep.Stage(TourStep.Side.ANYWHERE, R.string.tour_title_palette,
                R.string.tour_body_palette, TourTargets.HOME_PAGE, TourSignals.PALETTE_OPENED,
                TourGesture.SWIPE_UP));
    }

    /** The corner is held, not tapped; the ? the hold raises is tapped. Taught on a window. */
    private static TourStep findHelp() {
        return TourStep.lesson(FIND_HELP, 2, R.string.tour_chapter_anywhere,
            TourStep.Placement.AUTO,
            new TourStep.Stage(TourStep.Side.WORKSPACE, R.string.tour_title_corner,
                R.string.tour_body_corner, TourTargets.PANE_CORNER, TourSignals.PANE_CORNER_MENU,
                TourGesture.HOLD),
            new TourStep.Stage(TourStep.Side.WORKSPACE, R.string.tour_title_help,
                R.string.tour_body_help, TourTargets.HELP_BUTTON, TourSignals.HELP_OPENED,
                TourGesture.TAP));
    }

    private TourRun() {}
}
