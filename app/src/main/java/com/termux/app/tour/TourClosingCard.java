package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The last card's three sections, and what its Copy buttons put on the clipboard.
 *
 * <p>Four things are worth knowing on the way out — how the launcher stacks sessions, windows and
 * panes, where the launcher is made the user's own, the launcher's own extras, and graphical apps
 * — and the first device pass showed that a paragraph carrying two commands inside the prose is
 * not a thing anyone can act on from a phone. So each section is a heading, one sentence, and
 * where there is something to run, the command on its own line with its own Copy button.
 *
 * <p>The multitasking section is the one the run no longer teaches by hand: none of the four
 * lessons opens a shell, a window or a session, so the model is told here in three sentences and
 * the rest is left to help, which the first lesson has just taught the user to reach.
 *
 * <p>The customisation section is the one the run never teaches: nothing in it is a lesson, and a
 * newcomer who does not know the editors exist will not go looking for them.
 *
 * <p>The graphical section is the one that differs by edition: nixpkgs has no {@code x11-repo} to
 * add, so that edition gets a different sentence and no command at all. That is a different thing
 * to say rather than a different noun, which is why it is a row of this table and not a format
 * argument.
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

    /** How a caller turns a command's string resource into its text. */
    public interface CommandText {
        @NonNull
        String of(@StringRes int commandRes);
    }

    private static final Section MULTITASKING = new Section(
        R.string.tour_closing_multitasking_heading, R.string.tour_closing_multitasking_copy, 0);
    private static final Section CUSTOMISATION = new Section(
        R.string.tour_closing_customisation_heading, R.string.tour_closing_customisation_copy, 0);
    private static final Section EXTRAS = new Section(R.string.tour_closing_extras_heading,
        R.string.tour_closing_extras_copy, R.string.tour_closing_extras_command);
    private static final Section GRAPHICAL = new Section(R.string.tour_closing_graphical_heading,
        R.string.tour_closing_graphical_copy, R.string.tour_closing_graphical_command);
    private static final Section GRAPHICAL_NIX = new Section(
        R.string.tour_closing_graphical_heading, R.string.tour_closing_graphical_copy_nix, 0);

    private static final List<Section> PKG_SECTIONS = Collections.unmodifiableList(
        Arrays.asList(MULTITASKING, CUSTOMISATION, EXTRAS, GRAPHICAL));
    private static final List<Section> NIX_SECTIONS = Collections.unmodifiableList(
        Arrays.asList(MULTITASKING, CUSTOMISATION, EXTRAS, GRAPHICAL_NIX));

    /** The card's sections, in order, for the edition that is running. */
    @NonNull
    public static List<Section> sections(@NonNull TourEdition edition) {
        return edition.usesNixPackages() ? NIX_SECTIONS : PKG_SECTIONS;
    }

    /** Every command this edition offers, in the order the sections carry them. */
    @NonNull
    public static List<Integer> commandResources(@NonNull TourEdition edition) {
        List<Integer> commands = new ArrayList<>();
        for (Section section : sections(edition))
            if (section.hasCommand()) commands.add(section.commandRes);
        return commands;
    }

    /**
     * What Copy all puts on the clipboard: this edition's commands, one per line, in card order.
     * Empty when the edition has none to give — which is not the case today, and is still not a
     * reason for the button to put a blank line on the clipboard.
     */
    @NonNull
    public static String copyAllText(@NonNull TourEdition edition, @Nullable CommandText text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        for (int command : commandResources(edition)) {
            String line = text.of(command);
            if (line.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString();
    }

    private TourClosingCard() {}
}
