package com.termux.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/**
 * Which targets a picture on the Android clipboard is offered to X as, when its bytes are
 * re-encoded, and how much of it may be sent.
 */
public class ClipboardImageTargetsTest {

    @Test
    public void aScreenshotIsOfferedAsPngOnly() {
        String[] types = {"image/png"};
        assertFalse(ClipboardImageTargets.hasText(types));
        assertEquals("image/png", ClipboardImageTargets.imageType(types));
        assertEquals(Collections.singletonList("image/png"),
                ClipboardImageTargets.targetsFor(ClipboardImageTargets.imageType(types)));
    }

    @Test
    public void aJpegIsOfferedInItsOwnTypeThenPng() {
        assertEquals(Arrays.asList("image/jpeg", "image/png"),
                ClipboardImageTargets.targetsFor(ClipboardImageTargets.imageType(new String[]{"IMAGE/JPEG"})));
    }

    @Test
    public void aWildcardPictureIsOfferedAsPngOnly() {
        String type = ClipboardImageTargets.imageType(new String[]{"image/*"});
        assertEquals(Collections.singletonList("image/png"), ClipboardImageTargets.targetsFor(type));
        // Its bytes may be anything, so PNG is made from them rather than copied.
        assertTrue(ClipboardImageTargets.needsConversion(type, "image/png"));
    }

    @Test
    public void textAloneOffersNoImageTargets() {
        String[] types = {"text/plain", "text/html"};
        assertTrue(ClipboardImageTargets.hasText(types));
        assertNull(ClipboardImageTargets.imageType(types));
        assertTrue(ClipboardImageTargets.targetsFor(null).isEmpty());
    }

    @Test
    public void aClipWithTextAndAPictureOffersBoth() {
        String[] types = {"text/plain", "image/webp"};
        assertTrue(ClipboardImageTargets.hasText(types));
        assertEquals(Arrays.asList("image/webp", "image/png"),
                ClipboardImageTargets.targetsFor(ClipboardImageTargets.imageType(types)));
    }

    @Test
    public void noClipOffersNothing() {
        assertFalse(ClipboardImageTargets.hasText(null));
        assertNull(ClipboardImageTargets.imageType(null));
        assertNull(ClipboardImageTargets.imageType(new String[]{"image/", null}));
    }

    @Test
    public void theAnnounceListIsOneTargetPerLine() {
        assertEquals("image/jpeg\nimage/png",
                ClipboardImageTargets.announceList(Arrays.asList("image/jpeg", "image/png")));
    }

    @Test
    public void onlyOfferedTargetsAreAnswered() {
        assertTrue(ClipboardImageTargets.offers("image/jpeg", "image/png"));
        assertTrue(ClipboardImageTargets.offers("image/jpeg", "image/jpeg"));
        assertFalse(ClipboardImageTargets.offers("image/jpeg", "image/gif"));
        assertFalse(ClipboardImageTargets.offers(null, "image/png"));
        assertFalse(ClipboardImageTargets.offers("image/png", null));
    }

    @Test
    public void theOwnTypeIsCopiedAndPngIsMadeOnlyFromAnotherType() {
        assertFalse(ClipboardImageTargets.needsConversion("image/png", "image/png"));
        assertFalse(ClipboardImageTargets.needsConversion("image/jpeg", "image/jpeg"));
        assertTrue(ClipboardImageTargets.needsConversion("image/jpeg", "image/png"));
    }

    @Test
    public void theCapIsThirtyTwoMegabytes() {
        assertEquals(32 * 1024 * 1024, ClipboardImageTargets.MAX_BYTES);
        assertTrue(ClipboardImageTargets.withinCap(0));
        assertTrue(ClipboardImageTargets.withinCap(ClipboardImageTargets.MAX_BYTES));
        assertFalse(ClipboardImageTargets.withinCap(ClipboardImageTargets.MAX_BYTES + 1L));
        assertFalse(ClipboardImageTargets.withinCap(-1));
    }

    @Test
    public void aPictureTooLargeToDecodeIsNotConverted() {
        assertTrue(ClipboardImageTargets.decodable(4096, 4096));
        assertFalse(ClipboardImageTargets.decodable(8192, 4096));
        assertFalse(ClipboardImageTargets.decodable(0, 10));
    }

    @Test
    public void readAllStopsPastTheCap() throws IOException {
        byte[] bytes = new byte[100];
        assertEquals(100, ClipboardImageTargets.readAll(new ByteArrayInputStream(bytes), 100).length);
        try {
            ClipboardImageTargets.readAll(new ByteArrayInputStream(bytes), 99);
            fail("read past the cap");
        } catch (IOException expected) {
            // refused
        }
    }
}
