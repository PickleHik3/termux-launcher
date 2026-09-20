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
    public void everyEditionGetsTheSameThreeSectionsWithAHeadingAndASentence() {
        for (TourEdition edition : TourEdition.values()) {
            List<TourClosingCard.Section> sections = TourClosingCard.sections(edition);
            assertEquals("three sections for " + edition, 3, sections.size());
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
    public void theMultitaskingSectionIsTheSameOneEverywhereAndCarriesNoCommand() {
        // None of the five lessons opens a shell, a window or a session, so the model is told
        // here and the rest is left to help.
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section multitasking = TourClosingCard.sections(edition).get(0);
            assertEquals(R.string.tour_closing_multitasking_heading, multitasking.headingRes);
            assertEquals(R.string.tour_closing_multitasking_copy, multitasking.copyRes);
            assertFalse("multitasking has nothing to run", multitasking.hasCommand());
        }
    }

    @Test
    public void makeItYoursSitsBetweenMultitaskingAndTheExtrasAndCarriesNoCommand() {
        // The run teaches no lesson about the editors, so the way to them is said here.
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section makeItYours = TourClosingCard.sections(edition).get(1);
            assertEquals(R.string.tour_closing_make_it_yours_heading, makeItYours.headingRes);
            assertEquals(R.string.tour_closing_make_it_yours_copy, makeItYours.copyRes);
            assertFalse("make it yours has nothing to run", makeItYours.hasCommand());
        }
    }

    @Test
    public void theExtrasSectionIsTheCardsOneCommandAndTheLastThingOnIt() {
        for (TourEdition edition : TourEdition.values()) {
            List<TourClosingCard.Section> sections = TourClosingCard.sections(edition);
            TourClosingCard.Section extras = sections.get(sections.size() - 1);
            assertEquals(R.string.tour_closing_extras_heading, extras.headingRes);
            assertTrue("no extras command for " + edition, extras.hasCommand());
            assertEquals("one command on the card for " + edition,
                java.util.Collections.singletonList(extras.commandRes),
                TourClosingCard.commandResources(edition));
        }
    }
}
