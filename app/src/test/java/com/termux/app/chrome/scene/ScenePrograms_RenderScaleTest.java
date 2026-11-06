package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScenePrograms_RenderScaleTest {

    @Test
    public void softScenesRenderAtHalf() {
        assertEquals(0.5f, ScenePrograms.renderScale(ScenePrograms.AURORA), 0f);
        assertEquals(0.5f, ScenePrograms.renderScale(ScenePrograms.NEBULA), 0f);
        assertEquals(0.5f, ScenePrograms.renderScale(ScenePrograms.BLOOM), 0f);
    }

    @Test
    public void lineAndPointScenesStayFull() {
        assertEquals(1f, ScenePrograms.renderScale(ScenePrograms.CONTOUR_WAVES), 0f);
        assertEquals(1f, ScenePrograms.renderScale(ScenePrograms.PARTICLES), 0f);
        assertEquals(1f, ScenePrograms.renderScale(ScenePrograms.COASTAL_SUNSET), 0f);
        assertEquals(1f, ScenePrograms.renderScale(ScenePrograms.CONTOUR), 0f);
        assertEquals(1f, ScenePrograms.renderScale(ScenePrograms.FILAMENT), 0f);
    }

    @Test
    public void overrideWinsOnlyForKnownValues() {
        assertEquals(1f, ScenePrograms.resolveScale(ScenePrograms.AURORA, "1"), 0f);
        assertEquals(0.5f, ScenePrograms.resolveScale(ScenePrograms.PARTICLES, "0.5"), 0f);
        assertEquals(0.5f, ScenePrograms.resolveScale(ScenePrograms.BLOOM, null), 0f);
        assertEquals(1f, ScenePrograms.resolveScale(ScenePrograms.PARTICLES, ""), 0f);
        assertEquals(0.5f, ScenePrograms.resolveScale(ScenePrograms.AURORA, "0.3"), 0f);
    }

    @Test
    public void bufferSizeRoundsAndNeverZero() {
        assertEquals(540, ScenePrograms.bufferSize(1080, 0.5f));
        assertEquals(1206, ScenePrograms.bufferSize(2412, 0.5f));
        assertEquals(1, ScenePrograms.bufferSize(1, 0.5f));
    }
}
