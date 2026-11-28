package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Review P2: v1's Appearance editor (Terl off) lists Black beside System, Light and Dark, so a
 * Black set elsewhere shows as Black there and can be picked back after another theme.
 */
public class SurfaceEditorThemeRowTest {

    @Test public void theThemeRowListsBlack() {
        assertEquals(0, SurfaceEditorController.themeIndex("system"));
        assertEquals(1, SurfaceEditorController.themeIndex("false"));
        assertEquals(2, SurfaceEditorController.themeIndex("true"));
        assertEquals("Black shows as Black, not System", 3, SurfaceEditorController.themeIndex("black"));
        assertEquals("black", SurfaceEditorController.themeAt(3));
        assertEquals("true", SurfaceEditorController.themeAt(2));
        assertEquals("system", SurfaceEditorController.themeAt(-1));
    }
}
