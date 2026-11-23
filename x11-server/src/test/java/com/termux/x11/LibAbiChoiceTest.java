package com.termux.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Which {@code lib/<abi>/libXlorie.so} the server loads: the one at the process's own bitness. */
public class LibAbiChoiceTest {

    private static final String[] ABIS_64 = {"x86_64"};
    private static final String[] ABIS_32 = {"x86", "armeabi-v7a"};
    private static final String[] ALL = {"x86_64", "x86", "armeabi-v7a"};

    private static String choose(boolean is64, String... present) {
        Set<String> have = new HashSet<>(Arrays.asList(present));
        return LibAbiChoice.choose(is64, ABIS_64, ABIS_32, ALL, have::contains);
    }

    @Test public void a32BitProcessOnA64BitDevicePicksTheX86Library() {
        assertEquals("x86", choose(false, "x86"));
    }

    @Test public void a64BitProcessPicksTheSixtyFourBitLibrary() {
        assertEquals("x86_64", choose(true, "x86_64", "x86"));
    }

    @Test public void a32BitProcessTakesItsOwnBitnessEvenWhenA64BitOneIsAlsoThere() {
        assertEquals("x86", choose(false, "x86_64", "x86"));
    }

    @Test public void theFirstPresentAbiOfTheBitnessWins() {
        assertEquals("armeabi-v7a", choose(false, "armeabi-v7a"));
    }

    @Test public void nothingPresentIsNull() {
        assertNull(choose(false));
        assertNull(choose(true));
    }

    @Test public void fallsBackToTheFullAbiOrder() {
        // A 64-bit process whose APK only carries a 32-bit library still finds it; the load
        // itself then fails visibly.
        assertEquals("x86", choose(true, "x86"));
    }
}
