package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

public class CoastalSunsetTest {

    @Test
    public void isAKindAndAUserKind() {
        assertTrue(ScenePrograms.isKind("coastal-sunset"));
        assertEquals(ScenePrograms.COASTAL_SUNSET,
            com.termux.app.chrome.scene.LiveWallpaperChoice.parseScene(" coastal-sunset "));
        assertFalse(LiveWallpaperChoice.supportsDensity(ScenePrograms.COASTAL_SUNSET));
        assertFalse(LiveWallpaperChoice.supportsPalette(ScenePrograms.COASTAL_SUNSET));
    }

    /** The decoded photo is its own pixels at 4 bytes each, about 4.4 MiB, and never more than 5 MiB. */
    @Test
    public void artworkCostsItsOwnPixelsAndFitsItsBudget() {
        assertEquals(746L * 1536L * 4L, SceneArtwork.BUDGET_BYTES);
        assertTrue(SceneArtwork.BUDGET_BYTES <= 5L * 1024 * 1024);
    }

    @Test
    public void motionFadesInFromTheStillPhoto() {
        assertEquals(0f, CoastalSunsetProgram.motionFor(0d), 0f);
        assertEquals(0.5f, CoastalSunsetProgram.motionFor(CoastalSunsetProgram.MOTION_RAMP_MS / 2d), 1e-6f);
        assertEquals(1f, CoastalSunsetProgram.motionFor(60_000d), 0f);
    }

    @Test
    public void theBundledPhotoIsAJpegNotAZip() throws Exception {
        File f = new File("src/main/assets/scenes/coastal_sunset.jpg");
        assertTrue(f.getAbsolutePath(), f.isFile());
        assertTrue(f.length() < 200_000L);
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            assertEquals(0xFF, in.read());
            assertEquals(0xD8, in.read());
        }
    }
}
