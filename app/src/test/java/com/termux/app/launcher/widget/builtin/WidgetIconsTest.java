package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.termux.R;

import org.junit.Test;

public class WidgetIconsTest {

    @Test
    public void everyGlyphStandsForItsRoundlineIcon() {
        assertEquals(R.drawable.ic_rl_calendar, WidgetIcons.res(WidgetIcons.CALENDAR));
        assertEquals(R.drawable.ic_rl_checkbox_checked, WidgetIcons.res(WidgetIcons.CHECKBOX_CHECKED));
        assertEquals(R.drawable.ic_rl_chevron_down, WidgetIcons.res(WidgetIcons.CHEVRON_DOWN));
        assertEquals(R.drawable.ic_rl_memory, WidgetIcons.res(StatGlyphs.MEMORY));
    }

    @Test
    public void ordinaryTextIsNotAnIcon() {
        assertEquals(0, WidgetIcons.res(""));
        assertEquals(0, WidgetIcons.res(null));
        assertEquals(0, WidgetIcons.res("A"));
        assertEquals(0, WidgetIcons.res(WidgetIcons.CLOCK + "x"));
    }

    @Test
    public void glyphsAreDistinct() {
        assertNotEquals(WidgetIcons.SUN, WidgetIcons.MOON);
        assertNotEquals(WidgetIcons.CHEVRON_LEFT, WidgetIcons.CHEVRON_RIGHT);
        assertNotEquals(WidgetIcons.PLAY, WidgetIcons.PAUSE);
    }
}
