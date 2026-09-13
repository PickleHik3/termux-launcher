package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which edition the closing card is talking to, and which package line it therefore shows. */
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
    public void everyEditionGetsThreeLinesAndTheFirstIsTheSameOne() {
        for (TourEdition edition : TourEdition.values()) {
            int[] lines = TourClosingCard.bodyLines(edition);
            assertEquals("three lines for " + edition, 3, lines.length);
            assertEquals(TourClosingCard.bodyLines(TourEdition.TERMUX)[0], lines[0]);
            for (int line : lines) assertNotEquals(0, line);
        }
    }

    @Test
    public void theVajEditionKeepsPkgAndOnlyNixDiffers() {
        assertArrayEqualsInts(TourClosingCard.bodyLines(TourEdition.TERMUX),
            TourClosingCard.bodyLines(TourEdition.VAJ));
        assertEquals(TourClosingCard.commands(TourEdition.TERMUX),
            TourClosingCard.commands(TourEdition.VAJ));

        int[] termux = TourClosingCard.bodyLines(TourEdition.TERMUX);
        int[] nix = TourClosingCard.bodyLines(TourEdition.NIX);
        assertNotEquals(termux[1], nix[1]);
        assertNotEquals(termux[2], nix[2]);
        assertNotEquals(TourClosingCard.commands(TourEdition.TERMUX),
            TourClosingCard.commands(TourEdition.NIX));
    }

    @Test
    public void everyEditionHasCommandsToCopy() {
        for (TourEdition edition : TourEdition.values())
            assertNotEquals("no commands for " + edition, 0, TourClosingCard.commands(edition));
    }

    private static void assertArrayEqualsInts(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i]);
    }
}
