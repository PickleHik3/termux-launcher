package com.termux.app.tour;

import com.termux.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The run, as data: four lessons, the home-screen question and the closing card.
 *
 * <p>The lessons teach the four things a newcomer cannot look up without them — where help is, how
 * to reach their Android apps, how to get the keyboard out of the way, and how to find an action.
 * Everything else is offered on the way out or lives in help, and nothing here opens a shell, a
 * window or a session: the user's first terminal is exactly as they left it.
 *
 * <p>Two lessons read the phone rather than a fixed sentence, which is what {@link RunContext} is:
 * "press Home to come back" is wrong on a phone whose home screen is something else, and "hide the
 * keyboard" is wrong when the keyboard is already down.
 */
public final class TourRun {

    /** Find help: a pane corner, the ? behind it, and the way back out of help. */
    public static final String FIND_HELP = "find_help";
    /** Find Android apps: the dock's drawer, an app, and the way back to the launcher. */
    public static final String FIND_APPS = "find_apps";
    /** Control the keyboard: the keyboard button, both ways. */
    public static final String KEYBOARD = "keyboard";
    /** Find an action: the command palette, and something to find in it. */
    public static final String FIND_ACTION = "find_action";
    /** The home-screen question, which is answered with a button and not a gesture. */
    public static final String HOME_CHOICE = "home_choice";
    /** The last card. */
    public static final String CLOSING = "closing";

    /** The four lessons, in order; the two cards after them are not lessons. */
    private static final List<String> LESSONS = Collections.unmodifiableList(
        Arrays.asList(FIND_HELP, FIND_APPS, KEYBOARD, FIND_ACTION));

    /**
     * What the run has to know about the phone it is running on.
     *
     * <p>Both of these are read when the run is built rather than when a card is shown: a lesson
     * that asked the user to hide a keyboard which is already down, or to press a Home button that
     * leads somewhere else, is asking for something that cannot be done.
     */
    public static final class RunContext {

        /** Whether the launcher is the phone's home app. */
        public final boolean launcherIsHome;

        /** Whether the keyboard is showing as the run is built. */
        public final boolean keyboardShown;

        public RunContext(boolean launcherIsHome, boolean keyboardShown) {
            this.launcherIsHome = launcherIsHome;
            this.keyboardShown = keyboardShown;
        }
    }

    /** The run, in order, for the phone described by {@code context}. */
    public static List<TourStep> steps(RunContext context) {
        return Collections.unmodifiableList(Arrays.asList(
            findHelp(), findApps(context), keyboard(context), findAction(), homeChoice(context),
            closing()));
    }

    /** The lessons, in order: what practice and Back may name, and the migration maps to. */
    public static List<String> lessons() {
        return LESSONS;
    }

    /**
     * Lesson one. Help is where everything else in the launcher can be looked up, so the run
     * teaches the way to it first and does not let go until the user has been inside and come back
     * out: a lesson that ended on the ? would leave help covering the screen with no card to say
     * what to do about it.
     */
    private static TourStep findHelp() {
        return new TourStep(FIND_HELP,
            new int[] {R.string.tour_card_find_help_corner, R.string.tour_card_find_help_open,
                R.string.tour_card_find_help_close},
            new String[] {TourTargets.PANE_CORNER, TourTargets.HELP_BUTTON, TourTargets.NONE},
            new String[] {TourSignals.PANE_CORNER_MENU, TourSignals.HELP_OPENED,
                TourSignals.HELP_CLOSED},
            new TourGesture[] {TourGesture.TAP, TourGesture.TAP, TourGesture.TAP}, false, false);
    }

    /**
     * Lesson two. The drawer covers the dock it was pulled off and the launched app covers the
     * launcher, so only the first stage has anything to point at. The last stage's sentence is the
     * one thing in the run that depends on a system setting: a phone whose home screen is another
     * launcher has no Home button that leads back here.
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
     * Lesson three, both ways round the same button. Nothing is typed: the lesson is about getting
     * the keyboard out of the way and back again, and a shell command is practice for another day.
     */
    private static TourStep keyboard(RunContext context) {
        int[] copy = context.keyboardShown
            ? new int[] {R.string.tour_card_keyboard_hide, R.string.tour_card_keyboard_show_again}
            : new int[] {R.string.tour_card_keyboard_show, R.string.tour_card_keyboard_hide_again};
        String[] signals = context.keyboardShown
            ? new String[] {TourSignals.KEYBOARD_HIDDEN, TourSignals.KEYBOARD_SHOWN}
            : new String[] {TourSignals.KEYBOARD_SHOWN, TourSignals.KEYBOARD_HIDDEN};
        return new TourStep(KEYBOARD, copy,
            new String[] {TourTargets.KEYBOARD_TOGGLE_KEY, TourTargets.KEYBOARD_TOGGLE_KEY},
            signals, new TourGesture[] {TourGesture.TAP, TourGesture.TAP}, false, false);
    }

    /**
     * Lesson four. The palette is a full-plane surface, so its second stage points at nothing and
     * is the card that asks for the way out of the surface it is drawn over. The user is asked to
     * find something rather than to run it: that actions are searchable is the whole lesson.
     */
    private static TourStep findAction() {
        return new TourStep(FIND_ACTION,
            new int[] {R.string.tour_card_find_action_palette,
                R.string.tour_card_find_action_close},
            new String[] {TourTargets.SPACE_BAR, TourTargets.NONE},
            new String[] {TourSignals.PALETTE_OPENED, TourSignals.PALETTE_CLOSED},
            new TourGesture[] {TourGesture.SWIPE_UP, TourGesture.TAP}, false, false);
    }

    /**
     * The home-screen question, asked once the lessons are over so that the answer is an informed
     * one. A phone that is already set up this way has nothing to decide, and is told so rather
     * than asked again.
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

    /** The last card: what is worth knowing on the way out, and one action to leave on. */
    private static TourStep closing() {
        return new TourStep(CLOSING, TourStep.Kind.CLOSING,
            new int[] {R.string.tour_card_closing}, new String[] {TourTargets.NONE},
            new String[] {}, new TourGesture[] {TourGesture.NONE}, false, false, null);
    }

    private TourRun() {}
}
