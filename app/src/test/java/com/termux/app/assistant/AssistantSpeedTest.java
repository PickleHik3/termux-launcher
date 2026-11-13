package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AssistantSpeedTest {
    private Locale saved;

    @Before
    public void setUp() {
        saved = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void tearDown() {
        Locale.setDefault(saved);
    }

    @Test
    public void timeFormats() {
        assertEquals("4.1", AssistantSpeed.time(4100));
        assertEquals("12", AssistantSpeed.time(12000));
        assertEquals("10", AssistantSpeed.time(9960));
        assertEquals("1:05", AssistantSpeed.time(65000));
        assertEquals("1:00", AssistantSpeed.time(59600));
        assertTrue(AssistantSpeed.isClock(65000));
        assertFalse(AssistantSpeed.isClock(12000));
        assertEquals("65", AssistantSpeed.spokenSeconds(65000));
        assertEquals("4.1", AssistantSpeed.spokenSeconds(4100));
    }

    @Test
    public void tokensPerSecondIsDecodeSpeed() {
        // 51 tokens over 3.6 s after the first token.
        assertEquals(14, AssistantSpeed.tokensPerSecond(4100, 500, 51));
        assertEquals(17, AssistantSpeed.tokensPerSecond(3000, 0, 51));
    }

    @Test
    public void tooLittleToTellGivesNoSpeed() {
        assertEquals(0, AssistantSpeed.tokensPerSecond(4100, 4000, 50));
        assertEquals(0, AssistantSpeed.tokensPerSecond(4100, 500, 1));
        assertEquals(0, AssistantSpeed.tokensPerSecond(4100, 500, -1));
    }

    @Test
    public void timeWithUnitWritesSecondsOrAClock() {
        assertEquals("4.1 s", AssistantSpeed.timeWithUnit(4100, "%1$s s"));
        assertEquals("12 s", AssistantSpeed.timeWithUnit(12000, "%1$s s"));
        assertEquals("1:05", AssistantSpeed.timeWithUnit(65000, "%1$s s"));
    }
}
