package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The last card's six sections, and what its Copy button puts on the clipboard.
 *
 * <p>Six things are worth knowing on the way out — what holding on the terminal gives you, the key
 * every launcher shortcut starts with, where the launcher is made the user's own, the launcher's
 * own extras, that a pane can draw pictures, and where graphical Linux apps are set up — and the
 * first device pass showed that a paragraph carrying a command inside the prose is not a thing
 * anyone can act on from a phone. So each section is a heading, one sentence, and where there is
 * something to run, the command on its own line with its own Copy button.
 *
 * <p>The shortcuts and customize sections are the two the run never teaches: nothing in either is
 * a lesson, and a newcomer who does not know the shortcut key or the editors exist will not go
 * looking for them.
 *
 * <p>Graphical apps are a section again, but a sentence rather than a command: they are a Display
 * matter now and have a screen of their own in Settings, which asks what the user actually wants
 * rather than handing a newcomer one line to paste. Nix has no such screen — its apps come from
 * nixpkgs and {@code home.nix} — so its version of the sentence says that instead.
 *
 * <p>The extras section is a command everywhere except nix: {@code pkg} does not exist there, so
 * tlstore cannot either, and nix's version of the sentence points at {@code home.nix} instead of
 * handing over a command that would just fail.
 *
 * <p>Pure, so the table below is a unit test rather than four screenshots.
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

    /**
     * Decision (user, 2026-09-21): the hold was a shown-only card at the end of the Keyboard
     * lesson, which asked a new phone for something it cannot do — the mouse half needs a program
     * following the mouse, and a shell that has just been installed has none. It is a fact to read,
     * not a gesture to practise, so it comes first here: it is the one thing on this card the user
     * will meet by accident, the first time a finger rests on the text.
     */
    private static final Section HOLD = new Section(
        R.string.tour_closing_hold_heading, R.string.tour_closing_hold_copy, 0);
    private static final Section SHORTCUTS = new Section(
        R.string.tour_closing_shortcuts_heading, R.string.tour_closing_shortcuts_copy, 0);
    private static final Section CUSTOMIZE = new Section(
        R.string.tour_closing_customize_heading, R.string.tour_closing_customize_copy, 0);
    private static final Section EXTRAS = new Section(R.string.tour_closing_extras_heading,
        R.string.tour_closing_extras_copy, R.string.tour_closing_extras_command);
    /**
     * Decision (user, 2026-09-20/21): the nix edition has no {@code pkg}, so tlstore's install
     * command cannot run there either. Same heading, its own sentence pointing at
     * {@code home.nix} instead, and nothing to copy.
     */
    private static final Section EXTRAS_NIX = new Section(
        R.string.tour_closing_extras_heading, R.string.tour_closing_extras_copy_nix, 0);
    /**
     * Decision (user, 2026-09-21): a sentence, and no command. The terminal draws pictures without
     * being asked; only programs that decide by reading a name instead of asking the terminal need
     * anything, and what they need is a habit rather than a line pasted once. The recipe and its
     * caveats stay in Help, under "Pictures in the terminal", which is where they can be qualified.
     */
    private static final Section PICTURES = new Section(
        R.string.tour_closing_pictures_heading, R.string.tour_closing_pictures_copy, 0);
    private static final Section GUI_APPS = new Section(
        R.string.tour_closing_gui_apps_heading, R.string.tour_closing_gui_apps_copy, 0);
    /**
     * Decision (user, 2026-09-20): nix has no "Get GUI apps" screen at all — graphical apps there
     * come from nixpkgs and {@code home.nix} — so its card keeps the same heading but says that
     * instead of pointing at a screen it does not have.
     */
    private static final Section GUI_APPS_NIX = new Section(
        R.string.tour_closing_gui_apps_heading, R.string.tour_closing_gui_apps_copy_nix, 0);

    private static final List<Section> SECTIONS = Collections.unmodifiableList(
        Arrays.asList(HOLD, SHORTCUTS, CUSTOMIZE, EXTRAS, PICTURES, GUI_APPS));
    private static final List<Section> SECTIONS_NIX = Collections.unmodifiableList(
        Arrays.asList(HOLD, SHORTCUTS, CUSTOMIZE, EXTRAS_NIX, PICTURES, GUI_APPS_NIX));

    /**
     * The card's sections, in order. The same six in every edition, and the same heading for the
     * last one everywhere; nix's last section carries its own sentence, since it has no "Get GUI
     * apps" screen for the others' sentence to point at. The hold and the pictures sections are
     * the terminal's own behaviour, so they read the same in every edition.
     */
    @NonNull
    public static List<Section> sections(@NonNull TourEdition edition) {
        return edition == TourEdition.NIX ? SECTIONS_NIX : SECTIONS;
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
