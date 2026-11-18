package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.scene.ShaderStore;

import org.junit.Test;

public class ImageRowLogicTest {

    private static ImageRowLogic.Row depth(boolean photo, String name, boolean changed, ImageRowLogic.Run run,
                                           int pct, boolean failed) {
        return ImageRowLogic.depth(photo, name, changed, run, pct, failed);
    }

    private static void actions(ImageRowLogic.Row r, ImageRowLogic.Action first, ImageRowLogic.Action second) {
        assertEquals(first, r.first);
        assertEquals(second, r.second);
    }

    @Test
    public void photoRow_noneOffersChooseOnly() {
        ImageRowLogic.Row r = ImageRowLogic.photo(null);
        assertEquals(ImageRowLogic.Label.PHOTO_NONE, r.label);
        assertFalse(r.showThumb);
        actions(r, ImageRowLogic.Action.CHOOSE, null);
    }

    @Test
    public void photoRow_namedShowsThumbWithChangeAndRemove() {
        ImageRowLogic.Row r = ImageRowLogic.photo("lotus.jpg");
        assertEquals(ImageRowLogic.Label.PHOTO_NAMED, r.label);
        assertTrue(r.showThumb);
        actions(r, ImageRowLogic.Action.CHANGE, ImageRowLogic.Action.REMOVE);
    }

    @Test
    public void depth_noPhotoSaysChooseAPhotoFirstAndMakeIsOff() {
        ImageRowLogic.Row r = depth(false, null, false, ImageRowLogic.Run.IDLE, 0, false);
        assertEquals(ImageRowLogic.Label.DEPTH_NONE, r.label);
        assertEquals(ImageRowLogic.Note.CHOOSE_PHOTO_FIRST, r.note);
        assertFalse(r.noteIsError);
        assertFalse(r.firstEnabled);
        actions(r, ImageRowLogic.Action.MAKE, null);
    }

    @Test
    public void depth_photoChosenOffersMakeAndChoose() {
        ImageRowLogic.Row r = depth(true, null, false, ImageRowLogic.Run.IDLE, 0, false);
        assertEquals(ImageRowLogic.Note.NONE, r.note);
        assertTrue(r.firstEnabled);
        assertFalse(r.showProgress);
        actions(r, ImageRowLogic.Action.MAKE, ImageRowLogic.Action.CHOOSE);
    }

    @Test
    public void depth_downloadingShowsPercentBarAndCancel() {
        ImageRowLogic.Row r = depth(true, null, false, ImageRowLogic.Run.DOWNLOADING, 42, false);
        assertEquals(ImageRowLogic.Label.DEPTH_DOWNLOADING, r.label);
        assertTrue(r.showProgress);
        assertEquals(42, r.percent);
        assertFalse(r.showThumb);
        actions(r, ImageRowLogic.Action.CANCEL, null);
    }

    @Test
    public void depth_makingShowsBarAndCancel_percentIsClamped() {
        ImageRowLogic.Row r = depth(true, null, false, ImageRowLogic.Run.MAKING, 250, false);
        assertEquals(ImageRowLogic.Label.DEPTH_MAKING, r.label);
        assertTrue(r.showProgress);
        assertEquals(100, r.percent);
        actions(r, ImageRowLogic.Action.CANCEL, null);
        assertEquals(0, depth(true, null, false, ImageRowLogic.Run.MAKING, -5, false).percent);
    }

    @Test
    public void depth_aRunWinsOverEverythingElse() {
        ImageRowLogic.Row r = depth(true, "d.png", true, ImageRowLogic.Run.MAKING, 10, true);
        assertEquals(ImageRowLogic.Label.DEPTH_MAKING, r.label);
        assertEquals(ImageRowLogic.Note.NONE, r.note);
    }

    @Test
    public void depth_madeHereShowsThumbAndRemoveOnly() {
        ImageRowLogic.Row r = depth(true, ShaderStore.MADE_DEPTH_FILE, false, ImageRowLogic.Run.IDLE, 0, false);
        assertEquals(ImageRowLogic.Label.DEPTH_MADE, r.label);
        assertTrue(r.showThumb);
        assertEquals(ImageRowLogic.Note.NONE, r.note);
        actions(r, ImageRowLogic.Action.REMOVE, null);
    }

    @Test
    public void depth_pickedByHandWithTheSamePhotoMatchesThePhotoRow() {
        ImageRowLogic.Row r = depth(true, "d.png", false, ImageRowLogic.Run.IDLE, 0, false);
        assertEquals(ImageRowLogic.Label.DEPTH_NAMED, r.label);
        assertTrue(r.showThumb);
        assertEquals(ImageRowLogic.Note.NONE, r.note);
        actions(r, ImageRowLogic.Action.CHANGE, ImageRowLogic.Action.REMOVE);
    }

    @Test
    public void depth_pickedByHandAndPhotoChangedWarnsAndOffersMake() {
        ImageRowLogic.Row r = depth(true, "d.png", true, ImageRowLogic.Run.IDLE, 0, false);
        assertEquals(ImageRowLogic.Label.DEPTH_NAMED, r.label);
        assertEquals(ImageRowLogic.Note.MAY_NOT_MATCH, r.note);
        assertFalse(r.noteIsError);
        assertTrue(r.firstEnabled);
        actions(r, ImageRowLogic.Action.MAKE, ImageRowLogic.Action.REMOVE);
    }

    @Test
    public void depth_pickedByHandAfterThePhotoWasRemovedCannotMake() {
        ImageRowLogic.Row r = depth(false, "d.png", true, ImageRowLogic.Run.IDLE, 0, false);
        assertEquals(ImageRowLogic.Note.MAY_NOT_MATCH, r.note);
        assertFalse(r.firstEnabled);
        actions(r, ImageRowLogic.Action.MAKE, ImageRowLogic.Action.REMOVE);
    }

    @Test
    public void depth_failedShowsTheErrorLineWithTryAgainAndChoose() {
        ImageRowLogic.Row r = depth(true, null, false, ImageRowLogic.Run.IDLE, 0, true);
        assertEquals(ImageRowLogic.Label.DEPTH_NONE, r.label);
        assertEquals(ImageRowLogic.Note.FAILED, r.note);
        assertTrue(r.noteIsError);
        actions(r, ImageRowLogic.Action.TRY_AGAIN, ImageRowLogic.Action.CHOOSE);
    }

    @Test
    public void depth_aSetDepthIgnoresAStaleFailure() {
        ImageRowLogic.Row r = depth(true, "d.png", false, ImageRowLogic.Run.IDLE, 0, true);
        assertEquals(ImageRowLogic.Note.NONE, r.note);
    }

    @Test
    public void everyStateHasAFirstActionAndOnlyAnImageRowShowsAThumb() {
        for (ImageRowLogic.Run run : ImageRowLogic.Run.values()) {
            for (boolean photo : new boolean[] {false, true}) {
                for (String name : new String[] {null, "d.png", ShaderStore.MADE_DEPTH_FILE}) {
                    for (boolean changed : new boolean[] {false, true}) {
                        for (boolean failed : new boolean[] {false, true}) {
                            ImageRowLogic.Row r = depth(photo, name, changed, run, 5, failed);
                            assertNotNull(r.first);
                            assertEquals(r.showThumb, r.label == ImageRowLogic.Label.DEPTH_MADE
                                || r.label == ImageRowLogic.Label.DEPTH_NAMED);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void formatSize_isWholeMegabytes() {
        assertEquals("28 MB", ImageRowLogic.formatSize(28L * 1024 * 1024));
        assertEquals("55 MB", ImageRowLogic.formatSize(55L * 1024 * 1024 + 300_000));
        assertEquals("1 MB", ImageRowLogic.formatSize(10_000));
        assertEquals("1.5 GB", ImageRowLogic.formatSize(1536L * 1024 * 1024));
        assertEquals("0 MB", ImageRowLogic.formatSize(0));
    }
}
