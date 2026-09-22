package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.termux.R;

import org.junit.Test;

import java.util.List;

/** Which edition the closing card is talking to, and which sections it therefore shows. */
public class TourClosingCardTest {

    @Test
    public void theEditionIsTheApplicationIdAndNothingElse() {
        assertEquals(TourEdition.NIX, TourEdition.of("com.termux.launcher.nix"));
        assertEquals(TourEdition.VAJ, TourEdition.of("io.vaj.tl"));
        assertEquals(TourEdition.TERMUX, TourEdition.of("com.termux"));
    }

    @Test
    public void anUnknownOrMissingPackageIsTreatedAsTheTermuxEdition() {
        assertEquals(TourEdition.TERMUX, TourEdition.of(null));
        assertEquals(TourEdition.TERMUX, TourEdition.of(""));
        assertEquals(TourEdition.TERMUX, TourEdition.of("com.termux.debug"));
    }

    @Test
    public void onlyTheNixEditionInstallsWithNix() {
        assertTrue(TourEdition.NIX.usesNixPackages());
        assertFalse(TourEdition.TERMUX.usesNixPackages());
        assertFalse(TourEdition.VAJ.usesNixPackages());
    }

    @Test
    public void everyEditionGetsTheSameSixSectionsWithAHeadingAndASentence() {
        for (TourEdition edition : TourEdition.values()) {
            List<TourClosingCard.Section> sections = TourClosingCard.sections(edition);
            assertEquals("six sections for " + edition, 6, sections.size());
            for (TourClosingCard.Section section : sections) {
                assertNotEquals("no heading for " + edition, 0, section.headingRes);
                assertNotEquals("no copy for " + edition, 0, section.copyRes);
            }
            // The first three sections read the same everywhere; nix has its own sentence for
            // extras (no pkg, so no tlstore) and for graphical apps (no "Get GUI apps" screen).
            assertEquals(TourClosingCard.sections(TourEdition.TERMUX).subList(0, 3),
                sections.subList(0, 3));
            if (edition == TourEdition.NIX) {
                assertTrue("nix has no pkg, so no command on the card",
                    TourClosingCard.commandResources(edition).isEmpty());
            } else {
                assertEquals(TourClosingCard.commandResources(TourEdition.TERMUX),
                    TourClosingCard.commandResources(edition));
            }
        }
    }

    @Test
    public void vajReadsTheSameGraphicalAppsSentenceAsTermux() {
        assertEquals(TourClosingCard.sections(TourEdition.TERMUX),
            TourClosingCard.sections(TourEdition.VAJ));
    }

    @Test
    public void nixHasItsOwnGraphicalAppsSentenceWithTheSameHeading() {
        TourClosingCard.Section termuxGuiApps = TourClosingCard.sections(TourEdition.TERMUX)
            .get(TourClosingCard.sections(TourEdition.TERMUX).size() - 1);
        TourClosingCard.Section nixGuiApps = TourClosingCard.sections(TourEdition.NIX)
            .get(TourClosingCard.sections(TourEdition.NIX).size() - 1);

        assertEquals(termuxGuiApps.headingRes, nixGuiApps.headingRes);
        assertNotEquals(termuxGuiApps.copyRes, nixGuiApps.copyRes);
        assertEquals(R.string.tour_closing_gui_apps_copy_nix, nixGuiApps.copyRes);
        assertFalse("nix's graphical apps sentence has nothing to run", nixGuiApps.hasCommand());
    }

    @Test
    public void theHoldComesFirstEverywhereAndCarriesNoCommand() {
        // The tour used to end the Keyboard lesson on a hold a fresh phone cannot perform: its
        // mouse half needs a program that follows the mouse. It is read here instead, and first,
        // because it is the one thing on this card a finger will meet by accident.
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section hold = TourClosingCard.sections(edition).get(0);
            assertEquals(R.string.tour_closing_hold_heading, hold.headingRes);
            assertEquals(R.string.tour_closing_hold_copy, hold.copyRes);
            assertFalse("the hold has nothing to run", hold.hasCommand());
        }
    }

    @Test
    public void picturesSitBeforeTheGraphicalAppsAndPointAtHelpRatherThanACommand() {
        // Decision (user, 2026-09-21): a sentence, not a command. The terminal draws pictures
        // without being asked; the one line some older programs need is a habit, not a paste, and
        // it keeps its caveats in Help.
        for (TourEdition edition : TourEdition.values()) {
            List<TourClosingCard.Section> sections = TourClosingCard.sections(edition);
            TourClosingCard.Section pictures = sections.get(sections.size() - 2);
            assertEquals(R.string.tour_closing_pictures_heading, pictures.headingRes);
            assertEquals(R.string.tour_closing_pictures_copy, pictures.copyRes);
            assertFalse("pictures have nothing to run", pictures.hasCommand());
        }
    }

    @Test
    public void theShortcutsSectionComesSecondEverywhereAndCarriesNoCommand() {
        // No lesson teaches the shortcut key, so the card names it on the way out.
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section shortcuts = TourClosingCard.sections(edition).get(1);
            assertEquals(R.string.tour_closing_shortcuts_heading, shortcuts.headingRes);
            assertEquals(R.string.tour_closing_shortcuts_copy, shortcuts.copyRes);
            assertFalse("shortcuts has nothing to run", shortcuts.hasCommand());
        }
    }

    @Test
    public void customizeSitsBetweenTheShortcutsAndTheExtrasAndCarriesNoCommand() {
        // The run teaches no lesson about the editors, so the way to them is said here.
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section customize = TourClosingCard.sections(edition).get(2);
            assertEquals(R.string.tour_closing_customize_heading, customize.headingRes);
            assertEquals(R.string.tour_closing_customize_copy, customize.copyRes);
            assertFalse("customize has nothing to run", customize.hasCommand());
        }
    }

    @Test
    public void theExtrasSectionIsTheCardsOneCommandExceptOnNix() {
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section extras = TourClosingCard.sections(edition).get(3);
            assertEquals(R.string.tour_closing_extras_heading, extras.headingRes);
            if (edition == TourEdition.NIX) {
                assertEquals(R.string.tour_closing_extras_copy_nix, extras.copyRes);
                assertFalse("nix has no pkg, so tlstore has nothing to install",
                    extras.hasCommand());
                assertTrue(TourClosingCard.commandResources(edition).isEmpty());
            } else {
                assertEquals(R.string.tour_closing_extras_copy, extras.copyRes);
                assertTrue("no extras command for " + edition, extras.hasCommand());
                assertEquals(R.string.tour_closing_extras_command, extras.commandRes);
                assertEquals("one command on the card for " + edition,
                    java.util.Collections.singletonList(extras.commandRes),
                    TourClosingCard.commandResources(edition));
            }
        }
    }

    @Test
    public void graphicalAppsAreTheLastSectionAndAreASentenceRatherThanACommand() {
        // Graphical apps have a screen of their own in Settings for Termux and VAJ, so the card
        // points at it instead of handing a newcomer a line to paste; nix has no such screen and
        // gets its own sentence instead (checked in nixHasItsOwnGraphicalAppsSentenceWithTheSameHeading).
        for (TourEdition edition : TourEdition.values()) {
            List<TourClosingCard.Section> sections = TourClosingCard.sections(edition);
            TourClosingCard.Section guiApps = sections.get(sections.size() - 1);
            assertEquals(R.string.tour_closing_gui_apps_heading, guiApps.headingRes);
            assertEquals(edition.name(), edition == TourEdition.NIX
                ? R.string.tour_closing_gui_apps_copy_nix : R.string.tour_closing_gui_apps_copy,
                guiApps.copyRes);
            assertFalse("graphical apps have nothing to run", guiApps.hasCommand());
        }
    }
}
