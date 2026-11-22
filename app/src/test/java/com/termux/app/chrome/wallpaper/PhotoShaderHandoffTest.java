package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Who keeps a new photo shader's folder between the editor and Send to AI's notification. */
public class PhotoShaderHandoffTest {

    @Test
    public void sendShowsOnlyInThePhotoFlowWithAPhoto() {
        assertFalse(PhotoShaderHandoff.sendShows(false, "photo.jpg"));
        assertFalse(PhotoShaderHandoff.sendShows(true, null));
        assertTrue(PhotoShaderHandoff.sendShows(true, "photo.jpg"));
    }

    @Test
    public void theEditorClosingKeepsTheFolderWhileTheNotificationHoldsIt() {
        assertTrue("as before: an unsaved draft goes", PhotoShaderHandoff.editorDrops(false, false));
        assertFalse(PhotoShaderHandoff.editorDrops(false, true));
        assertFalse("saved: nothing to drop", PhotoShaderHandoff.editorDrops(true, false));
    }

    @Test
    public void theNotificationClosingDropsTheFolderOnlyWhenNothingElseHasIt() {
        assertTrue(PhotoShaderHandoff.notificationDrops(false, false));
        assertFalse("the editor still shows it", PhotoShaderHandoff.notificationDrops(false, true));
        assertFalse("a paste or Apply saved it", PhotoShaderHandoff.notificationDrops(true, false));
    }
}
