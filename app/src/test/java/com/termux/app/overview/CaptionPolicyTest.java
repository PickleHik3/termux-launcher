package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CaptionPolicyTest {

    /** Ten px a character. */
    private static final CaptionPolicy.Measure TEN = text -> text.length() * 10f;

    @Test public void aLabelThatFitsStaysWhole() {
        assertEquals("Terminal 4", CaptionPolicy.caption("Terminal 4", 100f, TEN));
    }

    @Test public void aCutNumberedLabelKeepsItsNumber() {
        assertEquals("4", CaptionPolicy.caption("Terminal 4", 60f, TEN));
        assertEquals("12", CaptionPolicy.caption("Terminal 12", 60f, TEN));
    }

    @Test public void twoCutLabelsStayDistinct() {
        String a = CaptionPolicy.caption("Terminal 4", 30f, TEN);
        String b = CaptionPolicy.caption("Terminal 5", 30f, TEN);
        assertEquals("4", a);
        assertEquals("5", b);
    }

    @Test public void aNameTheUserChoseIsKept() {
        assertEquals("build server", CaptionPolicy.caption("build server", 40f, TEN));
        assertEquals("Home", CaptionPolicy.caption("Home", 10f, TEN));
    }

    @Test public void numberReadsOnlyWordSpaceDigits() {
        assertEquals("7", CaptionPolicy.number("Assistant 7"));
        assertNull(CaptionPolicy.number("Terminal"));
        assertNull(CaptionPolicy.number("Terminal x"));
        assertNull(CaptionPolicy.number("Terminal "));
    }
}
