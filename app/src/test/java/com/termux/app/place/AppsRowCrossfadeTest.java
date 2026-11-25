package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The apps row's second content layer (Q5): used only while the two ends' lists differ. */
public class AppsRowCrossfadeTest {

    private static final float[] SLOTS = {0, 0, 98, 98, 98, 0, 196, 98, 196, 0, 294, 98};

    @Test public void oneGlobalListDrawsNoSecondLayer() {
        AppsRowCrossfade fade = new AppsRowCrossfade();
        List<String> pinned = Arrays.asList("a", "b", "c");
        assertFalse(fade.prepare(pinned, Arrays.asList("a", "b", "c"), SLOTS, 3));
        assertFalse(fade.active());
        assertEquals(1f, fade.leavingAlpha(0.5f), 0f);
        assertEquals(0f, fade.arrivingAlpha(0.5f), 0f);
    }

    @Test public void differentListsLayTheArrivingListOutAtRestAndTradeAlpha() {
        AppsRowCrossfade fade = new AppsRowCrossfade();
        assertTrue(fade.prepare(Arrays.asList("a", "b"), Arrays.asList("c", "d", "e"), SLOTS, 3));
        assertEquals(3, fade.count());
        assertEquals("e", fade.key(2));
        assertEquals(196f, fade.box(2, 0), 0f);
        assertEquals(98f, fade.box(2, 3), 0f);
        // The rests: the leaving list alone at p = 0, the arriving alone at p = 1.
        assertEquals(1f, fade.leavingAlpha(0f), 0f);
        assertEquals(0f, fade.arrivingAlpha(0f), 0f);
        assertEquals(0f, fade.leavingAlpha(1f), 0f);
        assertEquals(1f, fade.arrivingAlpha(1f), 0f);
        for (int k = 0; k <= 8; k++)
            assertEquals(1f, fade.leavingAlpha(k / 8f) + fade.arrivingAlpha(k / 8f), 1e-6f);
        fade.clear();
        assertFalse(fade.active());
        assertNull(fade.key(0));
    }

    @Test public void theLayerKeepsNoPixelsAndNoMoreThanItsSlots() {
        AppsRowCrossfade fade = new AppsRowCrossfade();
        String[] many = new String[AppsRowCrossfade.MAX_SLOTS + 4];
        Arrays.fill(many, "x");
        float[] boxes = new float[many.length * 4];
        assertTrue(fade.prepare(Collections.<String>emptyList(), Arrays.asList(many), boxes,
            many.length));
        assertEquals(AppsRowCrossfade.MAX_SLOTS, fade.count());
        assertEquals(0, fade.retainedBitmapBytes());
    }
}
