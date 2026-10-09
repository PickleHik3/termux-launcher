package com.termux.app.tour;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.place.PlaceLayout;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The run, as data: six gestures in three chapters, then the closing card.
 *
 * <p>Chapter one gets the user around — the border drag that changes place, and the keyboard
 * swipe off the bottom border, both ways. Chapter two is the status bar and the apps. Chapter
 * three is the command palette and the corner menu that holds help. Everything else is on the
 * closing card or in help, and nothing here opens a shell, a window or a session.
 *
 * <p>The setup sheet that comes before the first lesson is not a step: it is offered before the
 * run and is never stored, so a run picked up after a process death goes straight to its lesson.
 *
 * <p>Lesson ids are the ones help's "Try it" names, kept stable across versions of the run.
 */
public final class TourRun {

    /** Chapter one: change place with a border drag. */
    public static final String BORDER_DRAG = "border_drag";
    /** Chapter one: put the keyboard away and bring it back, off the bottom border. */
    public static final String KEYBOARD = "keyboard";
    /** Chapter two: pull the status bar down off the top border. */
    public static final String STATUS_SWIPE = "status_swipe";
    /** Chapter two: open the app drawer off the apps row. */
    public static final String FIND_APPS = "find_apps";
    /** Chapter three: open the command palette off the space bar. */
    public static final String FIND_ACTION = "find_action";
    /** Chapter three: hold a corner, then tap the ? in the menu it raises. */
    public static final String FIND_HELP = "find_help";
    /** The last card. */
    public static final String CLOSING = "closing";

    /** The six lessons, in the order the run teaches them. */
    private static final List<String> LESSONS = Collections.unmodifiableList(Arrays.asList(
        BORDER_DRAG, KEYBOARD, STATUS_SWIPE, FIND_APPS, FIND_ACTION, FIND_HELP));

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
            borderDrag(), keyboard(), statusSwipe(), findApps(context), findAction(), findHelp(),
            TourStep.closing(CLOSING)));
    }

    /** The lessons, in order: what help's practice may name. */
    @NonNull
    public static List<String> lessons() {
        return LESSONS;
    }

    private static TourStep borderDrag() {
        return TourStep.lesson(BORDER_DRAG, 0, R.string.tour_chapter_getting_around,
            TourStep.Placement.AUTO,
            new TourStep.Stage(R.string.tour_title_places, R.string.tour_body_places,
                TourTargets.PAGE_BORDER, TourSignals.PLACE_CHANGED, TourGesture.HOLD_DRAG));
    }

    /**
     * The keyboard swipe, both ways round. The run puts the keyboard up before every lesson, so the
     * first half always has a keyboard to put away.
     */
    private static TourStep keyboard() {
        return TourStep.lesson(KEYBOARD, 0, R.string.tour_chapter_getting_around,
            TourStep.Placement.AUTO,
            new TourStep.Stage(R.string.tour_title_keyboard_away, R.string.tour_body_keyboard_away,
                TourTargets.KEYBOARD_GRABBER, TourSignals.KEYBOARD_HIDDEN, TourGesture.DRAG_DOWN),
            new TourStep.Stage(R.string.tour_title_keyboard_back, R.string.tour_body_keyboard_back,
                TourTargets.KEYBOARD_GRABBER, TourSignals.KEYBOARD_SHOWN, TourGesture.SWIPE_UP));
    }

    private static TourStep statusSwipe() {
        return TourStep.lesson(STATUS_SWIPE, 1, R.string.tour_chapter_status_and_apps,
            TourStep.Placement.AUTO,
            new TourStep.Stage(R.string.tour_title_status, R.string.tour_body_status,
                TourTargets.STATUS_GRABBER, TourSignals.STATUS_BAR_EXPANDED, TourGesture.DRAG_DOWN));
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
        return TourStep.lesson(FIND_APPS, 1, R.string.tour_chapter_status_and_apps,
            TourStep.Placement.AUTO,
            new TourStep.Stage(R.string.tour_title_apps, body, TourTargets.DOCK,
                TourSignals.DRAWER_OPENED, TourGesture.DRAG_DOWN));
    }

    /** The palette sprouts from the bottom of the screen, so the card asks to stand above it. */
    private static TourStep findAction() {
        return TourStep.lesson(FIND_ACTION, 2, R.string.tour_chapter_commands_and_help,
            TourStep.Placement.ABOVE,
            new TourStep.Stage(R.string.tour_title_palette, R.string.tour_body_palette,
                TourTargets.SPACE_BAR, TourSignals.PALETTE_OPENED, TourGesture.SWIPE_UP));
    }

    /** The corner is held, not tapped; the ? the hold raises is tapped. */
    private static TourStep findHelp() {
        return TourStep.lesson(FIND_HELP, 2, R.string.tour_chapter_commands_and_help,
            TourStep.Placement.AUTO,
            new TourStep.Stage(R.string.tour_title_corner, R.string.tour_body_corner,
                TourTargets.PANE_CORNER, TourSignals.PANE_CORNER_MENU, TourGesture.HOLD),
            new TourStep.Stage(R.string.tour_title_help, R.string.tour_body_help,
                TourTargets.HELP_BUTTON, TourSignals.HELP_OPENED, TourGesture.TAP));
    }

    private TourRun() {}
}
