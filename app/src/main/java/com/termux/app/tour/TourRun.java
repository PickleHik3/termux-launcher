package com.termux.app.tour;

import com.termux.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The run, as data: the usage question, seven lessons, two more questions and the closing card.
 *
 * <p>The lessons teach what a newcomer cannot look up without them — where help is, the three
 * border gestures every place and every mode shares (the border drag that changes place, the
 * keyboard swipe off the bottom border and the status swipe off the top one), how to put their own
 * apps in the dock, how to reach the rest of their Android apps, and how to find an action.
 * Everything else is offered on the way out or lives in help, and nothing here opens a shell, a
 * window or a session: the user's first terminal is exactly as they left it.
 *
 * <p>The usage question comes first, because its answer decides which places exist: a terminal on
 * its own has nothing to drag the border towards, and no home screen to be the phone's.
 *
 * <p>Some lessons read the phone rather than a fixed sentence, which is what {@link RunContext}
 * is: "press Home to come back" is wrong on a phone whose home screen is something else, and
 * "swipe up to open the keyboard" is wrong when the keyboard is already up.
 */
public final class TourRun {

    /** The card the run is offered on. Not a step: it is read before the run, and it starts it. */
    public static final String WELCOME = "welcome";
    /** Find help: a corner of the page, the ? behind it, and the way back out of help. */
    public static final String FIND_HELP = "find_help";
    /** Change place: hold the page's border, drag to the next place, and drag back. */
    public static final String BORDER_DRAG = "border_drag";
    /** Pin your apps: the apps row's hold, and an app chosen in the editor it raises. */
    public static final String PIN_APPS = "pin_apps";
    /** Find Android apps: the apps row's drawer, an app, and the way back to the launcher. */
    public static final String FIND_APPS = "find_apps";
    /** The key-row question, for someone who arrives with a row of keys of their own. */
    public static final String KEY_ROW = "key_row";
    /**
     * The keyboard swipe: off the bottom border, both ways. The id is the old keyboard lesson's,
     * kept on purpose: it names the same lesson (control the keyboard) taught the way every place
     * shares, so every stored card number and every older run's map still means this card.
     */
    public static final String KEYBOARD = "keyboard";
    /** The status swipe: off the top border, both ways. */
    public static final String STATUS_SWIPE = "status_swipe";
    /** Find an action: the command palette, and something to find in it. */
    public static final String FIND_ACTION = "find_action";
    /** The usage question: the terminal alone, with Home, or with the Display too. */
    public static final String USAGE_MODE = "usage_mode";
    /** The home-screen question, which is answered with a button and not a gesture. */
    public static final String HOME_CHOICE = "home_choice";
    /** The last card. */
    public static final String CLOSING = "closing";

    /** The seven lessons, in order; the cards between and after them are not lessons. */
    private static final List<String> LESSONS = Collections.unmodifiableList(
        Arrays.asList(FIND_HELP, BORDER_DRAG, KEYBOARD, STATUS_SWIPE, PIN_APPS, FIND_APPS,
            FIND_ACTION));

    /**
     * What the run has to know about the phone it is running on.
     *
     * <p>These are read when the run is built rather than when a card is shown: a lesson that
     * asked the user to put away a keyboard which is already down, or to press a Home button that
     * leads somewhere else, is asking for something that cannot be done.
     */
    public static final class RunContext {

        /** Whether the launcher is the phone's home app. */
        public final boolean launcherIsHome;

        /** Whether the keyboard is showing as the run is built. */
        public final boolean keyboardShown;

        /**
         * Whether this user arrives with a row of keys of their own, and has not been asked about
         * this release's row yet. Only they are asked; everybody else walks past the card.
         */
        public final boolean hasOwnKeyRow;

        /**
         * Whether this build carries the Linux display, which is what decides whether the usage
         * card offers it. On unless said otherwise: every edition but the ones built without the
         * server has it.
         */
        public final boolean displayOffered;

        /** Whether the status bar is unfolded as the run is built. */
        public final boolean statusBarOpen;

        public RunContext(boolean launcherIsHome, boolean keyboardShown) {
            this(launcherIsHome, keyboardShown, false);
        }

        public RunContext(boolean launcherIsHome, boolean keyboardShown, boolean hasOwnKeyRow) {
            this(launcherIsHome, keyboardShown, hasOwnKeyRow, true);
        }

        public RunContext(boolean launcherIsHome, boolean keyboardShown, boolean hasOwnKeyRow,
                          boolean displayOffered) {
            this(launcherIsHome, keyboardShown, hasOwnKeyRow, displayOffered, false);
        }

        public RunContext(boolean launcherIsHome, boolean keyboardShown, boolean hasOwnKeyRow,
                          boolean displayOffered, boolean statusBarOpen) {
            this.launcherIsHome = launcherIsHome;
            this.keyboardShown = keyboardShown;
            this.hasOwnKeyRow = hasOwnKeyRow;
            this.displayOffered = displayOffered;
            this.statusBarOpen = statusBarOpen;
        }
    }

    /**
     * The card the run opens on, for newcomers and for anyone who has not seen this run before.
     * It is not one of the steps below: it carries no lesson, it is not counted or stored, and a
     * run picked back up after a process death goes straight to the lesson it was on.
     */
    public static TourStep welcome() {
        return new TourStep(WELCOME, TourStep.Kind.WELCOME,
            R.string.tour_card_kicker, R.string.tour_card_welcome_title,
            new int[] {R.string.tour_card_welcome}, new String[] {TourTargets.NONE},
            new String[] {}, new TourGesture[] {TourGesture.NONE}, false, false, null, false);
    }

    /** The run, in order, for the phone described by {@code context}. */
    public static List<TourStep> steps(RunContext context) {
        return Collections.unmodifiableList(Arrays.asList(
            usageMode(context), findHelp(), borderDrag(), keyboard(context),
            statusSwipe(context), pinApps(), findApps(context), findAction(), keyRow(context),
            homeChoice(context), closing()));
    }

    /** The lessons, in order: what practice and Back may name, and the migration maps to. */
    public static List<String> lessons() {
        return LESSONS;
    }

    /**
     * The usage question, asked first and with one answer per button, the same three the settings
     * Mode row offers, and only two in a build without the display. Its answer decides which
     * places exist, and so whether the border drag and the home-screen question are worth asking
     * at all. The answer is applied by the launcher, which rebuilds itself around it; the run
     * carries on from its stored card.
     */
    private static TourStep usageMode(RunContext context) {
        return new TourStep(USAGE_MODE, TourStep.Kind.CHOICE,
            new int[] {context.displayOffered
                ? R.string.tour_card_usage_mode
                : R.string.tour_card_usage_mode_no_display},
            new String[] {TourTargets.NONE}, new String[] {},
            new TourGesture[] {TourGesture.NONE}, false, false,
            context.displayOffered
                ? new TourAction[] {TourAction.USE_TERMINAL, TourAction.USE_HOME,
                    TourAction.USE_DISPLAY}
                : new TourAction[] {TourAction.USE_TERMINAL, TourAction.USE_HOME});
    }

    /**
     * Lesson one. Help is where everything else in the launcher can be looked up, so the run
     * teaches the way to it first and does not let go until the user has been inside and come back
     * out: a lesson that ended on the ? would leave help covering the screen with no card to say
     * what to do about it. The corner is held, not tapped: the first stage's trace says so.
     */
    private static TourStep findHelp() {
        return new TourStep(FIND_HELP,
            new int[] {R.string.tour_card_find_help_corner, R.string.tour_card_find_help_open,
                R.string.tour_card_find_help_close},
            new String[] {TourTargets.PANE_CORNER, TourTargets.HELP_BUTTON, TourTargets.NONE},
            new String[] {TourSignals.PANE_CORNER_MENU, TourSignals.HELP_OPENED,
                TourSignals.HELP_CLOSED},
            new TourGesture[] {TourGesture.HOLD, TourGesture.TAP, TourGesture.TAP}, false, false);
    }

    /**
     * Lesson two: the border drag, the one gesture that changes place. The page's border is held
     * until the page sinks, then dragged sideways; the second stage asks for the way back, which
     * is the same drag, and waits for the terminal to be the place the wall rests on again. The
     * lesson is dropped from a run on a phone with one place, where there is nowhere to drag to.
     */
    private static TourStep borderDrag() {
        return new TourStep(BORDER_DRAG,
            new int[] {R.string.tour_card_border_hold, R.string.tour_card_border_back},
            new String[] {TourTargets.PAGE_BORDER, TourTargets.NONE},
            new String[] {TourSignals.PLACE_CHANGED, TourSignals.PLACE_RETURNED},
            new TourGesture[] {TourGesture.HOLD_DRAG, TourGesture.HOLD_DRAG}, false, false);
    }

    /**
     * Lesson three: the keyboard swipe, off the bottom border, both ways round. It is the one way
     * to the keyboard every place and every mode shares, so it is what the run teaches; the
     * keyboard key is a second path that help names.
     *
     * <p>Nothing is typed: the lesson is about getting the keyboard out of the way and back
     * again, and a shell command is practice for another day.
     */
    private static TourStep keyboard(RunContext context) {
        int[] copy = context.keyboardShown
            ? new int[] {R.string.tour_card_kswipe_close, R.string.tour_card_kswipe_open_again}
            : new int[] {R.string.tour_card_kswipe_open, R.string.tour_card_kswipe_close_again};
        String[] signals = context.keyboardShown
            ? new String[] {TourSignals.KEYBOARD_HIDDEN, TourSignals.KEYBOARD_SHOWN}
            : new String[] {TourSignals.KEYBOARD_SHOWN, TourSignals.KEYBOARD_HIDDEN};
        TourGesture[] gestures = context.keyboardShown
            ? new TourGesture[] {TourGesture.DRAG_DOWN, TourGesture.SWIPE_UP}
            : new TourGesture[] {TourGesture.SWIPE_UP, TourGesture.DRAG_DOWN};
        return new TourStep(KEYBOARD, copy,
            new String[] {TourTargets.KEYBOARD_GRABBER, TourTargets.KEYBOARD_GRABBER},
            signals, gestures, false, false);
    }

    /**
     * Lesson four: the status swipe, off the top border, both ways round. Everyone who sees this
     * run has the status bar along the top, which is the only place the swipe is on, so it is a
     * lesson for all of them. Which way it starts follows the bar as the run is built.
     */
    private static TourStep statusSwipe(RunContext context) {
        int[] copy = context.statusBarOpen
            ? new int[] {R.string.tour_card_status_fold, R.string.tour_card_status_open_again}
            : new int[] {R.string.tour_card_status_open, R.string.tour_card_status_fold_again};
        String[] signals = context.statusBarOpen
            ? new String[] {TourSignals.STATUS_BAR_COLLAPSED, TourSignals.STATUS_BAR_EXPANDED}
            : new String[] {TourSignals.STATUS_BAR_EXPANDED, TourSignals.STATUS_BAR_COLLAPSED};
        TourGesture[] gestures = context.statusBarOpen
            ? new TourGesture[] {TourGesture.SWIPE_UP, TourGesture.DRAG_DOWN}
            : new TourGesture[] {TourGesture.DRAG_DOWN, TourGesture.SWIPE_UP};
        return new TourStep(STATUS_SWIPE, copy,
            new String[] {TourTargets.STATUS_GRABBER, TourTargets.STATUS_GRABBER},
            signals, gestures, false, false);
    }

    /**
     * Lesson five, and the earliest the run can teach it once the border gestures are known: the
     * dock a new install shows is empty, and the one thing that fills it is a hold nobody
     * guesses, made on an empty spot of the apps row. The first stage is the only hold on the
     * dock; the second is performed inside the sheet the hold raises, which is a window of its
     * own over the dock, so it points at nothing and asks for the save rather than for a tap it
     * cannot see.
     *
     * <p>A sheet closed with an empty dock does not clear the card. The lesson is a pinned app, so
     * its second sentence is written to be true whether the sheet is open or has been closed
     * again: the way back into it is the hold the first stage just taught.
     */
    private static TourStep pinApps() {
        return new TourStep(PIN_APPS,
            new int[] {R.string.tour_card_pin_apps_hold, R.string.tour_card_pin_apps_choose},
            new String[] {TourTargets.DOCK, TourTargets.NONE},
            new String[] {TourSignals.PIN_EDITOR_OPENED, TourSignals.PINNED_APPS_SAVED},
            new TourGesture[] {TourGesture.HOLD, TourGesture.TAP}, false, false);
    }

    /**
     * Lesson six. The drawer covers the dock it was pulled off and the launched app covers the
     * launcher, so only the first stage has anything to point at. The drawer is pulled away from
     * the apps row's edge, so the first sentence says that rather than "down", and the overlay
     * turns the trace to the side the row is on. The last stage's sentence is the one thing in the
     * run that depends on a system setting: a phone whose home screen is another launcher has no
     * Home button that leads back here.
     */
    private static TourStep findApps(RunContext context) {
        return new TourStep(FIND_APPS,
            new int[] {R.string.tour_card_find_apps_dock, R.string.tour_card_find_apps_open,
                context.launcherIsHome
                    ? R.string.tour_card_find_apps_back_home
                    : R.string.tour_card_find_apps_back_switch},
            new String[] {TourTargets.DOCK, TourTargets.NONE, TourTargets.NONE},
            new String[] {TourSignals.DRAWER_OPENED, TourSignals.APP_LAUNCHED,
                TourSignals.LAUNCHER_RESUMED},
            new TourGesture[] {TourGesture.DRAG_DOWN, TourGesture.TAP, TourGesture.TAP},
            false, false);
    }

    /**
     * Lesson seven. The palette is a full-plane surface, so its second stage points at nothing and
     * is the card that asks for the way out of the surface it is drawn over. The user is asked to
     * find something rather than to run it: that actions are searchable is the whole lesson.
     */
    private static TourStep findAction() {
        return new TourStep(FIND_ACTION,
            new int[] {R.string.tour_card_find_action_palette,
                R.string.tour_card_find_action_close},
            new String[] {TourTargets.SPACE_BAR, TourTargets.COMMAND_PALETTE},
            new String[] {TourSignals.PALETTE_OPENED, TourSignals.PALETTE_CLOSED},
            new TourGesture[] {TourGesture.SWIPE_UP, TourGesture.TAP}, false, false,
            TourStep.Placement.ABOVE);
    }

    /**
     * The key-row question, asked of the one person it is a question for: someone updating who
     * long ago made a row of keys of their own. This release ships a different row, and a row
     * they wrote replaces it for good, so they are asked once, after the lessons: no lesson points
     * at a key of the shipped row any more, so the answer cannot take one away.
     *
     * <p>Everyone else — a fresh install, and anyone who already types the shipped row — has
     * nothing to decide, so the card is walked past exactly as the home-screen question is on a
     * phone that is already set up. It stays one of the run's steps either way, so every stored
     * card number means the card it has always meant.
     */
    private static TourStep keyRow(RunContext context) {
        return new TourStep(KEY_ROW, TourStep.Kind.CHOICE,
            context.hasOwnKeyRow ? R.string.tour_card_kicker : 0,
            context.hasOwnKeyRow ? R.string.extra_keys_default_offer_title : 0,
            context.hasOwnKeyRow ? R.drawable.tour_key_row : 0,
            new int[] {R.string.tour_card_key_row},
            new String[] {TourTargets.NONE}, new String[] {},
            new TourGesture[] {TourGesture.NONE}, false, false,
            context.hasOwnKeyRow
                ? new TourAction[] {TourAction.SWITCH_KEY_ROW, TourAction.KEEP_KEY_ROW}
                : new TourAction[] {TourAction.CONTINUE},
            false, TourStep.Placement.AUTO);
    }

    /**
     * The home-screen question, asked once the lessons are over so that the answer is an informed
     * one. A phone that is already set up this way has nothing to decide, so the card is not put
     * in front of the user at all: the run walks straight past it to the closing card, and Back
     * from that card lands on the last lesson.
     *
     * <p>The card is still one of the run's steps on such a phone — every stored card number, and
     * every number an older run is mapped onto, means the card it has always meant — and its one
     * Continue is what {@link TourController} reads it by.
     */
    private static TourStep homeChoice(RunContext context) {
        return new TourStep(HOME_CHOICE, TourStep.Kind.CHOICE,
            new int[] {context.launcherIsHome
                ? R.string.tour_card_home_choice_already
                : R.string.tour_card_home_choice},
            new String[] {TourTargets.NONE}, new String[] {},
            new TourGesture[] {TourGesture.NONE}, false, false,
            context.launcherIsHome
                ? new TourAction[] {TourAction.CONTINUE}
                : new TourAction[] {TourAction.USE_AS_HOME, TourAction.KEEP_TRYING});
    }

    /**
     * The last card: what is worth knowing on the way out, and one action to leave on. It wears
     * the welcome card's shell — the same small line, title and sentence — so the run opens and
     * closes on one object rather than on two unrelated ones.
     */
    private static TourStep closing() {
        return new TourStep(CLOSING, TourStep.Kind.CLOSING,
            R.string.tour_card_kicker, R.string.tour_card_closing_title,
            new int[] {R.string.tour_card_closing}, new String[] {TourTargets.NONE},
            new String[] {}, new TourGesture[] {TourGesture.NONE}, false, false, null, false);
    }

    private TourRun() {}
}
