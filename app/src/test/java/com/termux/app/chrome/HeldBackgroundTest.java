package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HeldBackgroundTest {

    private static final class Spec {
        final int blur;
        Spec(int blur) { this.blur = blur; }
        @Override public boolean equals(Object o) { return o instanceof Spec && ((Spec) o).blur == blur; }
        @Override public int hashCode() { return blur; }
    }

    @Test
    public void theSameInputsGiveBackTheDrawableHeld() {
        HeldBackground<Object> held = new HeldBackground<>();
        Object backdrop = new Object();
        Object drawable = new Object();
        held.hold(HeldBackground.key("glass", new Spec(20), backdrop, 0x33000000, 7), drawable);

        assertSame(drawable,
            held.reuse(HeldBackground.key("glass", new Spec(20), backdrop, 0x33000000, 7)));
    }

    @Test
    public void anyInputThatMovedBuildsAfresh() {
        HeldBackground<Object> held = new HeldBackground<>();
        Object backdrop = new Object();
        held.hold(HeldBackground.key("glass", new Spec(20), backdrop, 0x33000000, 7), new Object());

        assertNull("the path", held.reuse(HeldBackground.key("veil", new Spec(20), backdrop, 0x33000000, 7)));
        assertNull("the spec", held.reuse(HeldBackground.key("glass", new Spec(21), backdrop, 0x33000000, 7)));
        assertNull("the backdrop instance",
            held.reuse(HeldBackground.key("glass", new Spec(20), new Object(), 0x33000000, 7)));
        assertNull("the baked veil", held.reuse(HeldBackground.key("glass", new Spec(20), backdrop, 0x44000000, 7)));
        assertNull("the even-out", held.reuse(HeldBackground.key("glass", new Spec(20), backdrop, 0x33000000, 8)));
    }

    @Test
    public void aReleasedDrawableIsNotKeptAndNothingHeldReusesNothing() {
        HeldBackground<Object> held = new HeldBackground<>();
        Object drawable = new Object();
        Object[] inputs = {"glass", 1};
        assertNull(held.reuse(HeldBackground.key(inputs)));

        held.hold(HeldBackground.key(inputs), drawable);
        assertTrue(held.holds(drawable));
        held.release();

        assertFalse(held.holds(drawable));
        assertNull(held.reuse(HeldBackground.key(inputs)));
    }
}
