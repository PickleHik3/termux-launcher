package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The last card's four sections, and what its Copy button puts on the clipboard.
 *
 * <p>Four things are worth knowing on the way out — the key every launcher shortcut starts with,
 * where the launcher is made the user's own, the launcher's own extras, and where graphical Linux
 * apps are set up — and the first device pass showed that a paragraph carrying a command inside
 * the prose is not a thing anyone can act on from a phone. So each section is a heading, one
 * sentence, and where there is something to run, the command on its own line with its own Copy
 * button.
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
        Arrays.asList(SHORTCUTS, CUSTOMIZE, EXTRAS, GUI_APPS));
    private static final List<Section> SECTIONS_NIX = Collections.unmodifiableList(
        Arrays.asList(SHORTCUTS, CUSTOMIZE, EXTRAS_NIX, GUI_APPS_NIX));

    /**
     * The card's sections, in order. The same four in every edition, and the same heading for the
     * last one everywhere; nix's last section carries its own sentence, since it has no "Get GUI
     * apps" screen for the others' sentence to point at.
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
