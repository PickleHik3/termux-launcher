package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.app.chrome.WallpaperPicture;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

/**
 * What a card carries now that the editor only edits how a place looks: its surface's look rows,
 * the shared strip only where the card is the shared layer's, and a Blur row's own-glass caption
 * (issue #37) only while the picture the glass is blurring may not match the screen.
 */
public class SurfaceEditorCardPlanTest {

    @Test
    public void theSharedLayersCardCarriesTheSharedStrip() {
        assertTrue(SurfaceEditorCardPlan.sharedStripShown(null));
    }

    @Test
    public void aSurfacesCardCarriesNoneOfIt() {
        for (SurfaceSlot slot : SurfaceSlot.values())
            assertFalse(slot.name(), SurfaceEditorCardPlan.sharedStripShown(slot));
    }

    @Test
    public void everySurfaceCardHasLookRowsToCarry() {
        for (SurfaceSlot slot : SurfaceSlot.values())
            assertFalse(slot.name(), SurfaceEditorProperties.rowsFor(slot).isEmpty());
    }

    @Test
    public void everyBlurRowIsRecognisedAsOne() {
        assertTrue(SurfaceEditorCardPlan.isBlurRow(SurfaceEditorProperties.ID_BLUR));
        assertTrue(SurfaceEditorCardPlan.isBlurRow(SurfaceEditorProperties.ID_ALL_BLUR));
        assertFalse(SurfaceEditorCardPlan.isBlurRow(SurfaceEditorProperties.ID_OPACITY));
        assertFalse(SurfaceEditorCardPlan.isBlurRow(SurfaceEditorProperties.ID_GRAIN));
    }

    @Test
    public void aBlurRowCarriesNoHintWhenTheGlassMatchesTheScreen() {
        assertNull(SurfaceEditorCardPlan.blurHintTextRes(
            SurfaceEditorProperties.ID_BLUR, WallpaperPicture.MATCHES_SCREEN));
        assertNull(SurfaceEditorCardPlan.blurHintTextRes(
            SurfaceEditorProperties.ID_ALL_BLUR, WallpaperPicture.MATCHES_SCREEN));
    }

    @Test
    public void aBlurRowNamesTheStillMayBeWrongWhenOneIsReadable() {
        assertEquals((Integer) R.string.termux_surface_editor_blur_hint_best_effort,
            SurfaceEditorCardPlan.blurHintTextRes(
                SurfaceEditorProperties.ID_BLUR, WallpaperPicture.BEST_EFFORT));
        assertEquals((Integer) R.string.termux_surface_editor_blur_hint_best_effort,
            SurfaceEditorCardPlan.blurHintTextRes(
                SurfaceEditorProperties.ID_ALL_BLUR, WallpaperPicture.BEST_EFFORT));
    }

    @Test
    public void aBlurRowAsksForAStillWhenNoneExists() {
        assertEquals((Integer) R.string.termux_surface_editor_blur_hint_no_still,
            SurfaceEditorCardPlan.blurHintTextRes(
                SurfaceEditorProperties.ID_BLUR, WallpaperPicture.NO_STILL));
        assertEquals((Integer) R.string.termux_surface_editor_blur_hint_no_still,
            SurfaceEditorCardPlan.blurHintTextRes(
                SurfaceEditorProperties.ID_ALL_BLUR, WallpaperPicture.NO_STILL));
    }

    @Test
    public void aNonBlurRowCarriesNoHintForAnyPicture() {
        for (WallpaperPicture picture : WallpaperPicture.values())
            assertNull(picture.name(), SurfaceEditorCardPlan.blurHintTextRes(
                SurfaceEditorProperties.ID_OPACITY, picture));
    }
}
