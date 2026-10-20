package com.termux.app.dock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.EnumSet;

/**
 * Home's dock style: the stored ids are a contract, anything unreadable is the glass tray (the
 * migration), and a style that is not built yet draws as the glass tray.
 */
public class DockStyleTest {

    @Test
    public void theStoredIdsNeverChange() {
        assertEquals("glass_tray", DockStyle.GLASS_TRAY.storageValue());
        assertEquals("lens_beads", DockStyle.LENS_BEADS.storageValue());
        assertEquals("taskbar", DockStyle.TASKBAR.storageValue());
        assertEquals("prompt_line", DockStyle.PROMPT_LINE.storageValue());
        assertEquals("wall_strip", DockStyle.WALL_STRIP.storageValue());
    }

    @Test
    public void everyIdReadsBackAsItsStyle() {
        for (DockStyle style : DockStyle.values())
            assertEquals(style, DockStyle.parse(style.storageValue()));
    }

    @Test
    public void anAbsentOrUnknownIdIsTheGlassTray() {
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.parse(null));
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.parse(""));
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.parse("ask_bar"));
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.parse("TASKBAR"));
    }

    @Test
    public void aStyleNotBuiltYetDrawsAsTheGlassTray() {
        EnumSet<DockStyle> built = EnumSet.of(DockStyle.GLASS_TRAY, DockStyle.LENS_BEADS);
        assertEquals(DockStyle.LENS_BEADS, DockStyle.effectiveStyle(DockStyle.LENS_BEADS, built));
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.effectiveStyle(DockStyle.TASKBAR, built));
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.effectiveStyle(null, built));
        assertEquals("nothing built still draws a dock", DockStyle.GLASS_TRAY,
            DockStyle.effectiveStyle(DockStyle.WALL_STRIP, EnumSet.noneOf(DockStyle.class)));
    }

    @Test
    public void theGlassTrayIsAlwaysBuilt() {
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.effectiveStyle(DockStyle.GLASS_TRAY));
    }
}
