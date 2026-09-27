package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/** Written English to spoken English before the G2P: numbers, years, dates, times, money and acronyms. */
public class TaiTextNormalizerTest {
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList("radar", "laser", "warning", "hello"));

    private static String say(String text) {
        return TaiTextNormalizer.normalize(text, KNOWN::contains);
    }

    @Test
    public void numbersReadAsTheKotlinSampleReadsThem() {
        assertEquals(Arrays.asList("four", "thousand", "ninety"), TaiTextNormalizer.numberToWords("4090"));
        assertEquals(Arrays.asList("one", "thousand", "two", "hundred", "thirty", "four", "point", "five"),
            TaiTextNormalizer.numberToWords("1,234.5"));
        assertEquals(Arrays.asList("zero", "point", "five"), TaiTextNormalizer.numberToWords(".5"));
        assertEquals(Arrays.asList("zero"), TaiTextNormalizer.numberToWords("0"));
        assertEquals(Arrays.asList("minus", "seven"), TaiTextNormalizer.integerToWords(-7));
        assertEquals(Arrays.asList("two", "million", "five"), TaiTextNormalizer.integerToWords(2_000_005));
        // Past trillions: digit by digit.
        assertEquals(16, TaiTextNormalizer.numberToWords("1234567890123456").size());
        assertEquals(20, TaiTextNormalizer.numberToWords("12345678901234567890").size());
    }

    @Test
    public void yearsReadAsYearsOnlyWhereTheyAreYears() {
        assertEquals("twenty twenty six", TaiTextNormalizer.yearWords(2026));
        assertEquals("two thousand five", TaiTextNormalizer.yearWords(2005));
        assertEquals("nineteen oh five", TaiTextNormalizer.yearWords(1905));
        assertEquals("nineteen hundred", TaiTextNormalizer.yearWords(1900));
        assertEquals("twenty ten", TaiTextNormalizer.yearWords(2010));
        assertEquals("It shipped in twenty twenty six.", say("It shipped in 2026."));
        assertEquals("since nineteen ninety nine", say("since 1999"));
        assertEquals("nineteen eighty four AD", say("1984 AD"));
        // A plain count stays a number for the G2P's own reader.
        assertEquals("2026 files", say("2026 files"));
        assertEquals("the nineteen nineties and the twenty tens", say("the 1990s and the 2010s"));
        assertEquals("the two thousands", say("the 2000s"));
    }

    @Test
    public void datesReadTheWayTheyAreSaid() {
        assertEquals("on the twenty seventh of September, twenty twenty six", say("on 27 September 2026"));
        assertEquals("September twenty seventh, twenty twenty six", say("September 27, 2026"));
        assertEquals("the first of March", say("1st March"));
        assertEquals("the third of September, twenty twenty six", say("2026-09-03"));
        assertEquals("December twenty twenty five", say("Dec 2025"));
        assertEquals("the second of January", say("2 Jan"));
        // Not a date: a day that cannot exist (left to the ordinal reader), and "may" the verb.
        assertEquals("forty second June", say("42nd June"));
        assertEquals("you may 3 times", say("you may 3 times"));
    }

    @Test
    public void timesReadAsClockTimes() {
        assertEquals("at ten thirty", say("at 10:30"));
        assertEquals("nine oh five PM", say("9:05 pm"));
        assertEquals("ten o'clock", say("10:00"));
        assertEquals("seven AM", say("7:00 a.m."));
        assertEquals("meet at five PM", say("meet at 5pm"));
        assertEquals("twenty three fifty nine", say("23:59"));
    }

    @Test
    public void ordinalsMoneyAndPercentages() {
        assertEquals("the twenty first try", say("the 21st try"));
        assertEquals("fortieth", TaiTextNormalizer.ordinalWords(40));
        assertEquals("one hundred twelfth", TaiTextNormalizer.ordinalWords(112));
        assertEquals("5 dollars and 50 cents", say("$5.50"));
        assertEquals("1 dollar", say("$1"));
        assertEquals("1,000 euros", say("€1,000"));
        assertEquals("20 pounds and 5 pence", say("£20.05"));
        assertEquals("50 percent done", say("50% done"));
        assertEquals("salt and pepper", say("salt & pepper"));
    }

    @Test
    public void acronymsSaidAsWordsAreWordsAndTheRestStaySpelled() {
        assertEquals("nasa and the BBC", say("NASA and the BBC"));
        assertEquals("jason", say("JSON"));
        assertEquals("okay", say("OK"));
        // Four letters and up that the dictionary knows are read as words.
        assertEquals("radar warning", say("RADAR WARNING"));
        // Unknown or short runs stay in capitals for the G2P to spell.
        assertEquals("the GPU and US", say("the GPU and US"));
        assertEquals("ABCD", say("ABCD"));
        // Without a dictionary only the fixed table applies.
        assertEquals("RADAR nasa", TaiTextNormalizer.normalize("RADAR NASA", null));
    }

    @Test
    public void plainTextPassesThroughApartFromSpacing() {
        assertEquals("Did you mean git status, or git stash?", say("Did you mean git status, or git stash?"));
        assertEquals("a b", say("a    b"));
        assertEquals("it's done", say("it’s done"));
    }
}
