package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.scene.WallpaperMotion;

import org.junit.Test;

public class LivePreviewLogicTest {

    private static final WallpaperMotion PHOTO = WallpaperMotion.photo();
    private static final WallpaperMotion A = WallpaperMotion.shader("a");
    private static final WallpaperMotion B = WallpaperMotion.shader("b");
    private static final WallpaperMotion V = WallpaperMotion.video("v.mp4");

    @Test
    public void commit_hasWorkOnlyForAMovingChoiceThatChanged() {
        assertFalse(LivePreviewLogic.commitHasWork(PHOTO, PHOTO, false));
        assertFalse(LivePreviewLogic.commitHasWork(A, A, false));
        assertTrue(LivePreviewLogic.commitHasWork(PHOTO, A, false));
        assertTrue(LivePreviewLogic.commitHasWork(A, B, false));
        assertTrue(LivePreviewLogic.commitHasWork(A, V, false));
        assertFalse(LivePreviewLogic.commitHasWork(A, PHOTO, false));
    }

    @Test
    public void commit_hasWorkForADraftAppliedUnderTheSameId() {
        assertTrue(LivePreviewLogic.commitHasWork(A, A, true));
        assertFalse(LivePreviewLogic.commitHasWork(PHOTO, PHOTO, true));
    }

    @Test
    public void release_revertsOnlyUncommittedMovedState() {
        assertFalse(LivePreviewLogic.mustRevert(false, A, A, 30, 30));
        assertTrue(LivePreviewLogic.mustRevert(false, A, B, 30, 30));
        assertTrue(LivePreviewLogic.mustRevert(false, V, V, 30, 60));
        assertFalse(LivePreviewLogic.mustRevert(true, PHOTO, A, 30, 60));
    }

    @Test
    public void videoImport_commitsAtOnceOnlyOverAVideo() {
        assertTrue(LivePreviewLogic.importCommitsAtOnce(V));
        assertFalse(LivePreviewLogic.importCommitsAtOnce(PHOTO));
        assertFalse(LivePreviewLogic.importCommitsAtOnce(A));
    }

    @Test
    public void copies_theChosenOneStays() {
        assertFalse(LivePreviewLogic.dropsCopy(A, "a"));
        assertTrue(LivePreviewLogic.dropsCopy(A, "b"));
        assertTrue(LivePreviewLogic.dropsCopy(PHOTO, "a"));
        assertTrue(LivePreviewLogic.dropsCopy(V, "a"));
    }
}
