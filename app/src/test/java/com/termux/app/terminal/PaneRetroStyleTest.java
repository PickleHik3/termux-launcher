package com.termux.app.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class PaneRetroStyleTest {

    @Test public void idsAreUniqueAndRoundTrip() {
        Set<String> ids = new HashSet<>();
        for (PaneRetroStyle s : PaneRetroStyle.values()) {
            assertTrue(s.id(), ids.add(s.id()));
            assertEquals(s, PaneRetroStyle.fromId(s.id()));
        }
        assertEquals(5, ids.size());
        assertTrue(ids.contains("none") && ids.contains("crt") && ids.contains("crt_green")
            && ids.contains("crt_amber") && ids.contains("tft"));
    }

    @Test public void unknownIdsAreNone() {
        assertEquals(PaneRetroStyle.NONE, PaneRetroStyle.fromId(null));
        assertEquals(PaneRetroStyle.NONE, PaneRetroStyle.fromId("nope"));
        assertEquals(PaneRetroStyle.NONE, PaneRetroStyle.fromId("CRT"));
    }

    @Test public void tints() {
        float[] zero = {0f, 0f, 0f, 0f};
        assertArrayEquals(zero, PaneRetroStyle.NONE.tint(), 0f);
        assertArrayEquals(zero, PaneRetroStyle.CRT.tint(), 0f);
        assertArrayEquals(zero, PaneRetroStyle.TFT.tint(), 0f);
        assertArrayEquals(new float[]{0.35f, 1.0f, 0.55f, 0.85f}, PaneRetroStyle.CRT_GREEN.tint(), 0f);
        assertArrayEquals(new float[]{1.0f, 0.68f, 0.25f, 0.85f}, PaneRetroStyle.CRT_AMBER.tint(), 0f);
    }

    @Test public void crtFamily() {
        assertTrue(PaneRetroStyle.CRT.usesCrt());
        assertTrue(PaneRetroStyle.CRT_GREEN.usesCrt());
        assertTrue(PaneRetroStyle.CRT_AMBER.usesCrt());
        assertFalse(PaneRetroStyle.TFT.usesCrt());
        assertFalse(PaneRetroStyle.NONE.usesCrt());
    }
}
