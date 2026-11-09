package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
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
        assertEquals("peek_glass", DockStyle.PEEK_GLASS.storageValue());
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

    @Test
    public void lensBeadsAreBuilt() {
        assertEquals(DockStyle.LENS_BEADS, DockStyle.effectiveStyle(DockStyle.LENS_BEADS));
    }

    /** The Taskbar is the Drawer tile now (#97): a stray stored id draws as the tray. */
    @Test
    public void theTaskbarIsNoDesignAnyMore() {
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.effectiveStyle(DockStyle.TASKBAR));
        assertFalse(DockStyle.BUILT.contains(DockStyle.TASKBAR));
        assertFalse("the picker never offers it", DockStyle.CHOICES.contains(DockStyle.TASKBAR));
    }

    /** The Prompt line is the Search field now (#96): a stray stored id draws as the tray. */
    @Test
    public void thePromptLineIsNoDesignAnyMore() {
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.effectiveStyle(DockStyle.PROMPT_LINE));
        assertFalse(DockStyle.BUILT.contains(DockStyle.PROMPT_LINE));
        assertFalse("the picker never offers it", DockStyle.CHOICES.contains(DockStyle.PROMPT_LINE));
    }

    @Test
    public void theWallStripIsBuiltAndSoIsEveryChoice() {
        assertEquals(DockStyle.WALL_STRIP, DockStyle.effectiveStyle(DockStyle.WALL_STRIP));
        assertEquals("every choice draws", EnumSet.copyOf(DockStyle.CHOICES), DockStyle.BUILT);
        for (DockStyle style : DockStyle.CHOICES)
            assertEquals(style, DockStyle.effectiveStyle(style));
    }

    @Test
    public void thePickerOffersTheDesignsInOrder() {
        assertEquals(Arrays.asList(DockStyle.GLASS_TRAY, DockStyle.LENS_BEADS,
            DockStyle.WALL_STRIP, DockStyle.PEEK_GLASS), DockStyle.CHOICES);
    }

    /**
     * Peek glass is drawn and offered, and is what a new install starts on; the absent key stays
     * the tray, so an existing install that never chose is not switched.
     */
    @Test
    public void peekGlassIsBuiltAndIsTheNewInstallDefaultOnly() {
        assertEquals(DockStyle.PEEK_GLASS, DockStyle.effectiveStyle(DockStyle.PEEK_GLASS));
        assertEquals(DockStyle.PEEK_GLASS, DockStyle.NEW_INSTALL_DEFAULT);
        assertEquals(DockStyle.GLASS_TRAY, DockStyle.parse(null));
        assertEquals(DockStyle.PEEK_GLASS, DockStyle.parse("peek_glass"));
    }
}
