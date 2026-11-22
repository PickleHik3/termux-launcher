package com.termux.terminal;

import junit.framework.TestCase;

import java.util.Random;

/**
 * {@link GraphemeClusterer} must give the same answers as the frozen
 * {@link LegacyGraphemeClusterer} for every sequence of code points. Run twice: with the
 * android.jar stubs, where ICU is missing and the zero-width fallback decides, and with real ICU4J
 * break rules.
 */
public class GraphemeClustererDifferentialTest extends TestCase {

    private static final int[] SPECIAL = {
        0x200D, // ZWJ
        0xFE0F, 0xFE0E, // variation selectors
        0x1F3FB, 0x1F3FC, 0x1F3FF, // skin tones
        0x1F1E6, 0x1F1E7, 0x1F1FA, 0x1F1F8, 0x1F1FF, // regional indicators
        0x0300, 0x0301, 0x0308, 0x036F, 0x20D0, // combining marks
        0x0E31, 0x0E33, // Thai
        0x1100, 0x1161, 0x11A8, 0xAC00, 0xAC01, // Hangul jamo and syllables
        0x0915, 0x094D, 0x0937, // Devanagari conjunct
        0x1F600, 0x1F468, 0x1F469, 0x1F467, 0x1F9D1, 0x1F44D, 0x2764, 0x1F525, // emoji
        0x4E2D, 0x6587, 0x30A2, 0xFF21, // CJK, kana, fullwidth
        0x2500, 0x253C, 0x2588, // box drawing
        0xD800, 0xDFFF, // lone surrogates
        0x0000, 0x0007, 0x001B, 0x007F, 0x0085, 0x009F, 0x00A0, 0x00AD, // controls and edges
        0x2028, 0x2029, 0x200B, 0x2060, 0xFEFF, 0xFFFD,
        0x10FFFF, 0xE0001, 0xE0020, 0xE007F, // tags
        0x20, 0x7E, 0x41, 0x7A, 0x30, // ASCII
    };

    private static int pick(Random random) {
        switch (random.nextInt(8)) {
            case 0:
            case 1:
            case 2:
                return 0x20 + random.nextInt(0x5F); // printable ASCII
            case 3:
                return random.nextInt(0x20); // C0
            case 4:
                return 0x80 + random.nextInt(0x700);
            case 5:
                return 0x1F000 + random.nextInt(0x1000);
            default:
                return SPECIAL[random.nextInt(SPECIAL.length)];
        }
    }

    private static void run(GraphemeClusterer actual, LegacyGraphemeClusterer legacy, Random random,
                            int length, String label) {
        StringBuilder trace = new StringBuilder();
        for (int step = 0; step < length; step++) {
            int roll = random.nextInt(100);
            if (roll < 3) {
                actual.reset();
                legacy.reset();
                trace.append(" R");
            } else {
                int cp = pick(random);
                // Mostly the preceding cell is current; sometimes the cursor moved away.
                boolean current = random.nextInt(12) != 0;
                trace.append(' ').append(Integer.toHexString(cp)).append(current ? "" : "!");
                boolean expected = legacy.accept(cp, current);
                boolean got = actual.accept(cp, current);
                assertEquals(label + " accept at step " + step + ":" + trace, expected, got);
            }
            assertEquals(label + " shouldWidenCell at step " + step + ":" + trace,
                legacy.shouldWidenCell(), actual.shouldWidenCell());
        }
    }

    /** Runs of combining marks and joiners long enough to hit the 64 unit cluster cap. */
    private static void runLongClusters(GraphemeClusterer actual, LegacyGraphemeClusterer legacy,
                                        Random random, String label) {
        int[] tail = {0x0301, 0x0308, 0x200D, 0xFE0F, 0x1F3FB, 0x1F600, 0x0E31, 0x20D0};
        for (int rep = 0; rep < 6; rep++) {
            int base = random.nextBoolean() ? 'e' : 0x1F468;
            assertEquals(label, legacy.accept(base, false), actual.accept(base, false));
            for (int i = 0; i < 80; i++) {
                int cp = tail[random.nextInt(tail.length)];
                assertEquals(label + " long cluster " + i, legacy.accept(cp, true), actual.accept(cp, true));
                assertEquals(label + " long widen " + i, legacy.shouldWidenCell(), actual.shouldWidenCell());
            }
        }
    }

    public void testFallbackPathMatchesLegacy() {
        Random random = new Random(0x6C057E7L);
        for (int c = 0; c < 4000; c++) {
            run(new GraphemeClusterer(), new LegacyGraphemeClusterer(), random, 10 + random.nextInt(120),
                "stub#" + c);
        }
        runLongClusters(new GraphemeClusterer(), new LegacyGraphemeClusterer(), random, "stub-long");
    }

    public void testIcuPathMatchesLegacy() {
        Random random = new Random(0x1C04L);
        for (int c = 0; c < 4000; c++) {
            run(new GraphemeClusterer(new Icu4jBreakIterator()),
                new LegacyGraphemeClusterer(new Icu4jBreakIterator()), random, 10 + random.nextInt(120),
                "icu#" + c);
        }
        runLongClusters(new GraphemeClusterer(new Icu4jBreakIterator()),
            new LegacyGraphemeClusterer(new Icu4jBreakIterator()), random, "icu-long");
    }

    /** One long-lived instance, as the emulator uses it, across many sequences. */
    public void testReusedInstanceMatchesLegacy() {
        Random random = new Random(42);
        GraphemeClusterer actual = new GraphemeClusterer(new Icu4jBreakIterator());
        LegacyGraphemeClusterer legacy = new LegacyGraphemeClusterer(new Icu4jBreakIterator());
        run(actual, legacy, random, 200000, "reused");
    }

    public void testEmojiSequencesJoinLikeLegacy() {
        int[][] sequences = {
            {0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467},
            {0x1F44D, 0x1F3FD},
            {0x1F1FA, 0x1F1F8, 0x1F1E9, 0x1F1EA},
            {0x2764, 0xFE0F},
            {'e', 0x0301, 0x0308},
            {'a', 0x0301, 'b'},
            {0x1100, 0x1161, 0x11A8},
        };
        for (int[] sequence : sequences) {
            GraphemeClusterer actual = new GraphemeClusterer(new Icu4jBreakIterator());
            LegacyGraphemeClusterer legacy = new LegacyGraphemeClusterer(new Icu4jBreakIterator());
            for (int i = 0; i < sequence.length; i++) {
                assertEquals(legacy.accept(sequence[i], i > 0), actual.accept(sequence[i], i > 0));
                assertEquals(legacy.shouldWidenCell(), actual.shouldWidenCell());
            }
        }
    }
}
