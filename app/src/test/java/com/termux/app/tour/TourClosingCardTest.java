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
    public void everyEditionGetsTheSameFourSectionsWithAHeadingAndASentence() {
        for (TourEdition edition : TourEdition.values()) {
            List<TourClosingCard.Section> sections = TourClosingCard.sections(edition);
            assertEquals("four sections for " + edition, 4, sections.size());
            for (TourClosingCard.Section section : sections) {
                assertNotEquals("no heading for " + edition, 0, section.headingRes);
                assertNotEquals("no copy for " + edition, 0, section.copyRes);
            }
            // Graphical apps were the one section an edition disagreed about, and they have a
            // screen of their own now; what is left reads the same everywhere.
            assertEquals(TourClosingCard.sections(TourEdition.TERMUX), sections);
            assertEquals(TourClosingCard.commandResources(TourEdition.TERMUX),
                TourClosingCard.commandResources(edition));
        }
    }

    @Test
    public void theShortcutsSectionComesFirstEverywhereAndCarriesNoCommand() {
        // No lesson teaches the shortcut key, so the card names it on the way out.
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section shortcuts = TourClosingCard.sections(edition).get(0);
            assertEquals(R.string.tour_closing_shortcuts_heading, shortcuts.headingRes);
            assertEquals(R.string.tour_closing_shortcuts_copy, shortcuts.copyRes);
            assertFalse("shortcuts has nothing to run", shortcuts.hasCommand());
        }
    }

    @Test
    public void customizeSitsBetweenTheShortcutsAndTheExtrasAndCarriesNoCommand() {
        // The run teaches no lesson about the editors, so the way to them is said here.
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section customize = TourClosingCard.sections(edition).get(1);
            assertEquals(R.string.tour_closing_customize_heading, customize.headingRes);
            assertEquals(R.string.tour_closing_customize_copy, customize.copyRes);
            assertFalse("customize has nothing to run", customize.hasCommand());
        }
    }

    @Test
    public void theExtrasSectionIsTheCardsOneCommand() {
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section extras = TourClosingCard.sections(edition).get(2);
            assertEquals(R.string.tour_closing_extras_heading, extras.headingRes);
            assertEquals(R.string.tour_closing_extras_copy, extras.copyRes);
            assertTrue("no extras command for " + edition, extras.hasCommand());
            assertEquals(R.string.tour_closing_extras_command, extras.commandRes);
            assertEquals("one command on the card for " + edition,
                java.util.Collections.singletonList(extras.commandRes),
                TourClosingCard.commandResources(edition));
        }
    }

    @Test
    public void graphicalAppsAreTheLastSectionAndAreASentenceRatherThanACommand() {
        // Graphical apps have a screen of their own in Settings now, so the card points at it
        // instead of handing a newcomer a line to paste.
        for (TourEdition edition : TourEdition.values()) {
            List<TourClosingCard.Section> sections = TourClosingCard.sections(edition);
            TourClosingCard.Section guiApps = sections.get(sections.size() - 1);
            assertEquals(R.string.tour_closing_gui_apps_heading, guiApps.headingRes);
            assertEquals(R.string.tour_closing_gui_apps_copy, guiApps.copyRes);
            assertFalse("graphical apps have nothing to run", guiApps.hasCommand());
        }
    }
}
