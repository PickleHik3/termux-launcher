package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The last card's three sections, and what its Copy button puts on the clipboard.
 *
 * <p>Three things are worth knowing on the way out — how the launcher stacks sessions, windows and
 * panes, where the launcher is made the user's own, and the launcher's own extras — and the first
 * device pass showed that a paragraph carrying a command inside the prose is not a thing anyone
 * can act on from a phone. So each section is a heading, one sentence, and where there is
 * something to run, the command on its own line with its own Copy button.
 *
 * <p>The multitasking section is the one the run no longer teaches by hand: none of the five
 * lessons opens a shell, a window or a session, so the model is told here in three sentences and
 * the rest is left to help, which the first lesson has just taught the user to reach.
 *
 * <p>The "make it yours" section is the one the run never teaches: nothing in it is a lesson, and
 * a newcomer who does not know the editors exist will not go looking for them.
 *
 * <p>Graphical apps used to be a fourth section, with a command and an edition of its own. They
 * are a Display matter now and have a screen of their own in Settings, which can ask what the
 * user actually wants rather than handing a newcomer one line to paste.
 *
 * <p>Pure, so the table below is a unit test rather than three screenshots.
 */
public final class TourClosingCard {

    /** One block of the card: a heading, a sentence, and at most one command. */
    public static final class Section {

        @StringRes public final int headingRes;
        @StringRes public final int copyRes;
        /** The command to run, or 0 for a section that only has something to say. */
        @StringRes public final int commandRes;

        Section(@StringRes int headingRes, @StringRes int copyRes, @StringRes int commandRes) {
            this.headingRes = headingRes;
            this.copyRes = copyRes;
            this.commandRes = commandRes;
        }

        public boolean hasCommand() {
            return commandRes != 0;
        }
    }

    private static final Section MULTITASKING = new Section(
        R.string.tour_closing_multitasking_heading, R.string.tour_closing_multitasking_copy, 0);
    private static final Section MAKE_IT_YOURS = new Section(
        R.string.tour_closing_make_it_yours_heading, R.string.tour_closing_make_it_yours_copy, 0);
    private static final Section EXTRAS = new Section(R.string.tour_closing_extras_heading,
        R.string.tour_closing_extras_copy, R.string.tour_closing_extras_command);

    private static final List<Section> SECTIONS = Collections.unmodifiableList(
        Arrays.asList(MULTITASKING, MAKE_IT_YOURS, EXTRAS));

    /**
     * The card's sections, in order. The same three in every edition: what was left of the card
     * once graphical apps moved to their own screen says nothing an edition disagrees with.
     */
    @NonNull
    public static List<Section> sections(@NonNull TourEdition edition) {
        return SECTIONS;
    }

    /** Every command this edition offers, in the order the sections carry them. */
    @NonNull
    public static List<Integer> commandResources(@NonNull TourEdition edition) {
        List<Integer> commands = new ArrayList<>();
        for (Section section : sections(edition))
            if (section.hasCommand()) commands.add(section.commandRes);
        return commands;
    }

    private TourClosingCard() {}
}
