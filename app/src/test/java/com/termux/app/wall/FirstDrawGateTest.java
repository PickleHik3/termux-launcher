package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A settle's clock starts only after the arriving content has drawn once (#173): the frame the
 * new page first draws in no longer eats the settle.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class FirstDrawGateTest {

    private View view;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        view = new View(activity);
        root.addView(view);
        activity.setContentView(root);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void theClockStartsAfterTheDrawNotBeforeIt() {
        FirstDrawGate gate = FirstDrawGate.afterNextDraw(view);
        List<String> ran = new ArrayList<>();
        ValueAnimator settle = ValueAnimator.ofFloat(1f, 0f);
        settle.setDuration(300L);
        gate.then(() -> { ran.add("nudge"); settle.start(); });
        gate.then(() -> ran.add("ghost"));
        assertFalse("held until the arriving content draws", gate.isOpen());
        assertFalse(settle.isStarted());

        gate.onDraw();
        assertFalse("not inside the draw: on the frame after it", gate.isOpen());
        assertTrue(ran.isEmpty());

        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(gate.isOpen());
        assertEquals("both sheets start together, in order", List.of("nudge", "ghost"), ran);
        // Started then (it may have run its course already as the looper idled).
        assertTrue(settle.isStarted() || settle.getAnimatedFraction() == 1f);
    }

    @Test
    public void noDrawStillOpensTheGateAfterTheTimeout() {
        FirstDrawGate gate = FirstDrawGate.afterNextDraw(view);
        List<String> ran = new ArrayList<>();
        gate.then(() -> ran.add("settle"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FirstDrawGate.TIMEOUT_MS + 1));
        assertTrue(gate.isOpen());
        assertEquals(List.of("settle"), ran);
        // It opens once.
        gate.onDraw();
        gate.open();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, ran.size());
    }

    @Test
    public void aViewWithNoWindowAndAnOpenedGateRunAtOnce() {
        View loose = new View(view.getContext());
        List<String> ran = new ArrayList<>();
        FirstDrawGate.afterNextDraw(loose).then(() -> ran.add("loose"));
        FirstDrawGate.opened().then(() -> ran.add("opened"));
        assertEquals(List.of("loose", "opened"), ran);
    }
}
