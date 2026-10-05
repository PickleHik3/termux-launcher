package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiFunctionModels.Resolution;
import com.termux.ai.TaiFunctionModels.Source;
import com.termux.ai.TaiTierPolicy;

import java.util.Collections;

import org.junit.Test;

/** Who reads the photo, from the WALLPAPER_READER resolution. */
public class LivingReaderTest {

    @Test
    public void aLocalVisionModelReadsOnItsAccelerator() {
        Resolution r = new Resolution("gemma-4-e4b-it-litert-lm-vision", "gpu", Source.AUTOMATIC,
            TaiTierPolicy.WithoutModel.NONE, Collections.emptyList(), true, null);
        LivingReader reader = LivingReader.of(r, null);
        assertEquals("gemma-4-e4b-it-litert-lm-vision", reader.model);
        assertEquals("gpu", reader.accelerator);
        assertFalse(reader.remote);
        assertTrue(reader.warnBackground);
        assertTrue(reader.usesModel());
    }

    @Test
    public void aRemotePickSendsTheRemoteModelNameAndNoAccelerator() {
        Resolution r = new Resolution(null, null, Source.PICK, TaiTierPolicy.WithoutModel.NONE,
            Collections.emptyList(), false, "remote/vision-1");
        LivingReader reader = LivingReader.of(r, "https://api.example.com/v1");
        assertEquals("remote/vision-1", reader.model);
        assertNull(reader.accelerator);
        assertTrue(reader.remote);
        assertEquals("api.example.com", reader.remoteHost);
        assertEquals("vision-1", reader.remoteDisplayName());
        assertFalse(reader.warnBackground);
    }

    @Test
    public void rulesOnlyTakesTheBuildersExistingNoModelPath() {
        Resolution r = new Resolution(null, null, Source.AUTOMATIC, TaiTierPolicy.WithoutModel.RULES_ONLY,
            Collections.emptyList(), false, null);
        LivingReader reader = LivingReader.of(r, null);
        assertSame(LivingReader.RULES_ONLY, reader);
        assertFalse(reader.usesModel());
        assertNull(reader.model);
    }

    @Test
    public void theConsentShowsOnceAndOnlyForARemoteReader() {
        assertTrue(LivingReader.needsConsent(true, false));
        assertFalse(LivingReader.needsConsent(true, true));
        assertFalse(LivingReader.needsConsent(false, false));
    }

    @Test
    public void theHostIsReadFromTheServerAddress() {
        assertEquals("api.example.com", LivingReader.hostOf("https://api.example.com/v1"));
        assertEquals("192.168.1.5", LivingReader.hostOf("http://192.168.1.5:8080/v1"));
        assertEquals("", LivingReader.hostOf(""));
        assertEquals("", LivingReader.hostOf(null));
        assertEquals("", LivingReader.hostOf("not a url"));
    }
}
