package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The editor's preview scopes. The row tables this used to hold went with the per-surface cards
 * (appearance-layout-editor SPEC §6); what is left is the coalescing contract: every scope its own
 * bit, and ALL covering every scope but the commit.
 */
public class SurfaceEditorPropertiesTest {

    private static final int[] SCOPES = {
        SurfaceEditorProperties.PREVIEW_GLASS,
        SurfaceEditorProperties.PREVIEW_BLUR,
        SurfaceEditorProperties.PREVIEW_GEOMETRY,
        SurfaceEditorProperties.PREVIEW_SURFACES,
        SurfaceEditorProperties.PREVIEW_KEYBOARD,
        SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT};

    @Test
    public void everyScopeIsItsOwnBit() {
        int seen = 0;
        for (int scope : SCOPES) {
            assertEquals("one bit", 1, Integer.bitCount(scope));
            assertEquals("not shared with another scope", 0, seen & scope);
            seen |= scope;
        }
    }

    @Test
    public void allCoversEveryScopeButTheCommit() {
        for (int scope : SCOPES) {
            boolean covered = (SurfaceEditorProperties.PREVIEW_ALL & scope) != 0;
            assertEquals(scope != SurfaceEditorProperties.PREVIEW_GEOMETRY_COMMIT, covered);
        }
        assertTrue((SurfaceEditorProperties.PREVIEW_ALL & SurfaceEditorProperties.PREVIEW_BLUR)
            != 0);
    }
}
