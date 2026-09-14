package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

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
    public void everyEditionGetsThreeSectionsWithAHeadingAndASentence() {
        for (TourEdition edition : TourEdition.values()) {
            List<TourClosingCard.Section> sections = TourClosingCard.sections(edition);
            assertEquals("three sections for " + edition, 3, sections.size());
            for (TourClosingCard.Section section : sections) {
                assertNotEquals("no heading for " + edition, 0, section.headingRes);
                assertNotEquals("no copy for " + edition, 0, section.copyRes);
            }
        }
    }

    @Test
    public void theKeyHintsSectionIsTheSameOneEverywhereAndCarriesNoCommand() {
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section hints = TourClosingCard.sections(edition).get(0);
            assertEquals(TourClosingCard.sections(TourEdition.TERMUX).get(0).headingRes,
                hints.headingRes);
            assertFalse("key hints has nothing to run", hints.hasCommand());
        }
    }

    @Test
    public void theVajEditionKeepsPkgAndOnlyNixDiffers() {
        assertEquals(TourClosingCard.sections(TourEdition.TERMUX),
            TourClosingCard.sections(TourEdition.VAJ));
        assertEquals(TourClosingCard.commandResources(TourEdition.TERMUX),
            TourClosingCard.commandResources(TourEdition.VAJ));

        TourClosingCard.Section termux = TourClosingCard.sections(TourEdition.TERMUX).get(2);
        TourClosingCard.Section nix = TourClosingCard.sections(TourEdition.NIX).get(2);
        // Same heading, different sentence: nixpkgs has no x11-repo to add, which is a different
        // thing to say rather than a different package name.
        assertEquals(termux.headingRes, nix.headingRes);
        assertNotEquals(termux.copyRes, nix.copyRes);
        assertTrue(termux.hasCommand());
        assertFalse(nix.hasCommand());
    }

    @Test
    public void theExtrasSectionIsTheOneCommandEveryEditionOffers() {
        for (TourEdition edition : TourEdition.values()) {
            TourClosingCard.Section extras = TourClosingCard.sections(edition).get(1);
            assertTrue("no extras command for " + edition, extras.hasCommand());
            assertTrue("extras missing from the copy-all list for " + edition,
                TourClosingCard.commandResources(edition).contains(extras.commandRes));
        }
    }

    @Test
    public void copyAllIsEverySectionsCommandInCardOrderAndNothingElse() {
        assertEquals("cmd-1\ncmd-2", TourClosingCard.copyAllText(TourEdition.TERMUX, numbered()));
        // The Nix edition has only the extras command, so Copy all is one line with no blank
        // second one where the graphical command would have been.
        assertEquals("cmd-1", TourClosingCard.copyAllText(TourEdition.NIX, numbered()));
    }

    @Test
    public void copyAllSkipsACommandThatResolvesToNothingAndSurvivesNoResolverAtAll() {
        assertEquals("", TourClosingCard.copyAllText(TourEdition.TERMUX, res -> ""));
        assertEquals("", TourClosingCard.copyAllText(TourEdition.TERMUX, null));
    }

    /** Names each command by its place in the edition's list, so the order is what is asserted. */
    private static TourClosingCard.CommandText numbered() {
        List<Integer> termux = TourClosingCard.commandResources(TourEdition.TERMUX);
        return res -> "cmd-" + (termux.indexOf(res) + 1);
    }
}
