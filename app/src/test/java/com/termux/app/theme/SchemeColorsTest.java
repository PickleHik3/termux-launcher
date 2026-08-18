package com.termux.app.theme;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code colors.properties} is a hand-editable file, and half the schemes in the wild were written
 * by hand. Parsing has to survive their spellings without throwing away the whole theme.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SchemeColorsTest {

    @Test
    public void parsesEverySpellingSeenInTheWild() {
        assertEquals(Color.parseColor("#FF112233"), (int) SchemeColors.parse("#112233"));
        assertEquals(Color.parseColor("#FF112233"), (int) SchemeColors.parse("112233"));
        assertEquals(Color.parseColor("#FF112233"), (int) SchemeColors.parse("  #112233  "));
        assertEquals(Color.parseColor("#FF112233"), (int) SchemeColors.parse("#112233 # comment"));
        assertEquals(Color.parseColor("#FF112233"), (int) SchemeColors.parse("#123"));
        assertEquals(Color.parseColor("#80112233"), (int) SchemeColors.parse("#80112233"));
    }

    @Test
    public void rejectsGarbageWithoutThrowing() {
        assertNull(SchemeColors.parse("not a colour"));
        assertNull(SchemeColors.parse(""));
        assertNull(SchemeColors.parse(null));
        assertNull(SchemeColors.parse("#12345"));
    }

    /** No background and no foreground means no anchor, and guessing one repaints the whole app. */
    @Test
    public void refusesASchemeWithNoAnchor() {
        Properties props = new Properties();
        props.setProperty("color4", "#268BD2");
        assertNull(SchemeColors.from(props));
        assertNull(SchemeColors.from(null));
    }

    @Test
    public void fillsInMissingAnsiSlots() {
        Properties props = new Properties();
        props.setProperty("background", "#1D2021");
        props.setProperty("foreground", "#D4BE98");
        SchemeColors scheme = SchemeColors.from(props);
        assertNotNull(scheme);
        assertTrue(scheme.isDark());
        for (int i = 0; i < 16; i++) {
            assertTrue("slot " + i + " must be visible on the background",
                SchemeTone.contrastRatio(scheme.ansi(i), scheme.background) > 1.0d);
        }
        // With no cursor of its own, the scheme's text colour stands in.
        assertEquals(Color.parseColor("#D4BE98"), scheme.cursor);
    }

    @Test
    public void brightSlotsMirrorTheirNormalCounterparts() {
        Properties props = new Properties();
        props.setProperty("background", "#1D2021");
        props.setProperty("foreground", "#D4BE98");
        props.setProperty("color1", "#EA6962");
        SchemeColors scheme = SchemeColors.from(props);
        assertNotNull(scheme);
        assertTrue("bright red should be a lift of red, not an unrelated colour",
            SchemeTone.tone(scheme.ansi(9)) > SchemeTone.tone(scheme.ansi(1)));
    }

    @Test
    public void ansiIndexClamps() {
        Properties props = new Properties();
        props.setProperty("background", "#000000");
        props.setProperty("foreground", "#FFFFFF");
        SchemeColors scheme = SchemeColors.from(props);
        assertNotNull(scheme);
        assertEquals(scheme.ansi(0), scheme.ansi(-3));
        assertEquals(scheme.ansi(15), scheme.ansi(99));
    }
}
