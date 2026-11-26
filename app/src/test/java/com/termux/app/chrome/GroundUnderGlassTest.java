package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * #283 round 3: a glass stack's ground finds the view it is drawn on even when the stack is worn
 * through a wrapper that passes no callbacks on (the keyboard's layout-neutral background, the
 * under-keyboard card's rounded sheet): without it the keyboard's own stack drew no ground, and
 * the landing's hand-off to the unified backdrop flashed lighter for two frames.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GroundUnderGlassTest {

    @Test
    public void aStackWornThroughAMuteWrapperFindsItsViewOnceBound() {
        View host = new View(RuntimeEnvironment.getApplication());
        GroundUnderGlass ground = new GroundUnderGlass("keyboard");
        LayerDrawable stack = new LayerDrawable(new Drawable[] {
            new ColorDrawable(0xFF808080), ground, new ColorDrawable(0x66202020)});
        // A wrapper that never sets the stack's callback, as LayoutNeutralDrawable does not.
        RoundedSheetDrawable wrapper = new RoundedSheetDrawable(stack, 0, 0f);
        host.setBackground(wrapper);
        assertNull(ground.host());
        if (!GlassGround.ON) return;
        GroundUnderGlass.bind(stack, host);
        assertSame(host, ground.host());
    }

    @Test
    public void aStackSetStraightOnAViewFindsItThroughItsCallbacks() {
        View host = new View(RuntimeEnvironment.getApplication());
        GroundUnderGlass ground = new GroundUnderGlass("frame");
        LayerDrawable stack = new LayerDrawable(new Drawable[] {ground});
        host.setBackground(stack);
        assertSame(host, ground.host());
    }

    @Test
    public void roundingReachesTheGroundInAStackAndNothingElse() {
        GroundUnderGlass ground = new GroundUnderGlass("stack");
        LayerDrawable stack = new LayerDrawable(new Drawable[] {
            new ColorDrawable(0xFF808080), ground, new ColorDrawable(0x66202020)});
        assertEquals(0f, ground.cornerRadiusPx(), 0f);
        GroundUnderGlass.round(stack, 21f);
        assertEquals(21f, ground.cornerRadiusPx(), 0f);
        GroundUnderGlass.round(ground, 8f);
        assertEquals(8f, ground.cornerRadiusPx(), 0f);
    }

    @Test
    public void theRadiusIsHeldToHalfTheSmallerSide() {
        // A capsule 42 tall and 200 wide rounds to 21 whatever it was dressed with.
        assertEquals(21f, GroundUnderGlass.fittedRadiusPx(100f, 200f, 42f), 0f);
        assertEquals(21f, GroundUnderGlass.fittedRadiusPx(21f, 200f, 42f), 0f);
        assertEquals(10f, GroundUnderGlass.fittedRadiusPx(10f, 200f, 42f), 0f);
        assertEquals(0f, GroundUnderGlass.fittedRadiusPx(10f, 0f, 42f), 0f);
    }
}
