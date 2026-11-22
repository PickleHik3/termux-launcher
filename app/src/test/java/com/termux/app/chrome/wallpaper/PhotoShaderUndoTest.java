package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What Send to AI's Undo puts back, and when the source it puts back is dropped. */
public class PhotoShaderUndoTest {

    @Test
    public void aPasteOverASavedSourceKeepsIt_aPasteIntoANewShaderKeepsNothing() {
        assertTrue(PhotoShaderUndo.pasteKeeps(true));
        assertFalse(PhotoShaderUndo.pasteKeeps(false));
    }

    @Test
    public void undoRestoresTheKeptSourceAndTheWallpaper_orTheWallpaperAloneForANewShader() {
        assertEquals(PhotoShaderUndo.Restores.SOURCE_AND_WALLPAPER, PhotoShaderUndo.undoRestores(true));
        assertEquals("the new shader stays saved in the list",
            PhotoShaderUndo.Restores.WALLPAPER, PhotoShaderUndo.undoRestores(false));
    }

    @Test
    public void doneCloseAndANewAskDropTheKeptSource_undoHasUsedItUp() {
        assertTrue(PhotoShaderUndo.dropsKept(PhotoShaderUndo.End.DONE));
        assertTrue(PhotoShaderUndo.dropsKept(PhotoShaderUndo.End.CLOSE));
        assertTrue(PhotoShaderUndo.dropsKept(PhotoShaderUndo.End.NEW_ASK));
        assertFalse(PhotoShaderUndo.dropsKept(PhotoShaderUndo.End.UNDO));
    }
}
