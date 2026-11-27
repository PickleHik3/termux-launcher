package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ReadingFormatTest {

    @Test
    public void aPercentShowsItsNumberAloneInTheCompactFormsAndWithItsUnitInTheTwoRows() {
        ReadingValue cpu = ReadingFormat.percent(ReadingKind.CPU, 42);
        assertEquals("42", cpu.compact());
        assertEquals("42%", cpu.full());
    }

    @Test
    public void anUnknownLoadIsNotOffered() {
        assertNull(ReadingFormat.percent(ReadingKind.GPU, -1));
    }

    @Test
    public void theBatteryIsReadBackFromTheChipsText() {
        assertEquals("87%", ReadingFormat.battery("87%").full());
        assertNull("the chip before its first reading", ReadingFormat.battery("--"));
    }

    @Test
    public void theWeatherIsWholeDegreesInTheChosenScale() {
        assertEquals("21°", ReadingFormat.weather(true, 21.4, false).full());
        assertEquals("21", ReadingFormat.weather(true, 21.4, false).compact());
        assertEquals("71°", ReadingFormat.weather(true, 21.4, true).full());
        assertNull(ReadingFormat.weather(false, 21.4, false));
    }

    @Test
    public void mediaIsAnIconAlone() {
        ReadingValue media = ReadingFormat.media();
        assertEquals("", media.compact());
        assertEquals("", media.full());
    }
}
