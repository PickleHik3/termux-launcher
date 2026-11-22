package com.termux.terminal;

import junit.framework.TestCase;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Random;

/**
 * {@link GraphemeClusterer.ClusterText} stands in for the String and StringCharacterIterator the
 * clusterer used to hand to ICU, so it must answer every call the same way.
 */
public class GraphemeClusterTextTest extends TestCase {

    private static void assertSame(String label, CharacterIterator expected, CharacterIterator actual) {
        assertEquals(label + " index", expected.getIndex(), actual.getIndex());
        assertEquals(label + " begin", expected.getBeginIndex(), actual.getBeginIndex());
        assertEquals(label + " end", expected.getEndIndex(), actual.getEndIndex());
        assertEquals(label + " current", expected.current(), actual.current());
    }

    public void testMatchesStringCharacterIteratorOnRandomCalls() {
        Random random = new Random(7);
        StringBuilder builder = new StringBuilder();
        GraphemeClusterer.ClusterText actual = new GraphemeClusterer.ClusterText(builder);
        for (int c = 0; c < 3000; c++) {
            // The builder changes between rewinds, as the clusterer's cluster does.
            builder.setLength(0);
            int length = random.nextInt(12);
            for (int i = 0; i < length; i++) builder.append((char) (0x20 + random.nextInt(0x600)));
            if (random.nextInt(4) == 0) builder.appendCodePoint(0x1F468);
            actual.rewind();
            StringCharacterIterator expected = new StringCharacterIterator(builder.toString());
            assertSame("start#" + c, expected, actual);
            for (int step = 0; step < 40; step++) {
                String label = "case " + c + " step " + step;
                switch (random.nextInt(6)) {
                    case 0:
                        assertEquals(label, expected.first(), actual.first());
                        break;
                    case 1:
                        assertEquals(label, expected.last(), actual.last());
                        break;
                    case 2:
                        assertEquals(label, expected.next(), actual.next());
                        break;
                    case 3:
                        assertEquals(label, expected.previous(), actual.previous());
                        break;
                    case 4: {
                        int index = random.nextInt(length + 6) - 2;
                        Throwable expectedThrown = null;
                        Throwable actualThrown = null;
                        char expectedChar = 0;
                        char actualChar = 0;
                        try {
                            expectedChar = expected.setIndex(index);
                        } catch (IllegalArgumentException e) {
                            expectedThrown = e;
                        }
                        try {
                            actualChar = actual.setIndex(index);
                        } catch (IllegalArgumentException e) {
                            actualThrown = e;
                        }
                        assertEquals(label + " setIndex threw", expectedThrown != null, actualThrown != null);
                        assertEquals(label + " setIndex", expectedChar, actualChar);
                        break;
                    }
                    default:
                        assertEquals(label, expected.current(), actual.current());
                }
                assertSame(label, expected, actual);
            }
        }
    }

    public void testCloneIsIndependentSnapshot() {
        StringBuilder builder = new StringBuilder("abc");
        GraphemeClusterer.ClusterText text = new GraphemeClusterer.ClusterText(builder);
        text.rewind();
        text.next();
        CharacterIterator copy = (CharacterIterator) text.clone();
        builder.setLength(0);
        builder.append("zzzzzz");
        assertEquals(1, copy.getIndex());
        assertEquals('b', copy.current());
        assertEquals(3, copy.getEndIndex());
        assertEquals('c', copy.next());
        assertEquals(1, text.getIndex());
    }

    /** The clusterer hands ICU the shared iterator, not a String, and keeps one break iterator. */
    public void testClustererReadsTheClusterInPlace() {
        Icu4jBreakIterator iterator = new Icu4jBreakIterator();
        GraphemeClusterer clusterer = new GraphemeClusterer(iterator);
        clusterer.accept('a', false);
        assertTrue(clusterer.accept(0x0301, true));
        assertTrue(clusterer.accept(0x0308, true));
        assertEquals(2, iterator.mSetTextCharacterIterator);
        assertEquals(0, iterator.mSetTextString);
        assertEquals(0, iterator.mSetTextCharSequence);
    }
}
