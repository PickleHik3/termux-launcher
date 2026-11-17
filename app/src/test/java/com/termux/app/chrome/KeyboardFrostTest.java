package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.SurfaceView;
import android.view.View;

import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.FramePipeline;
import com.termux.app.window.FrameStep;
import com.termux.app.window.WindowLayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The keyboard's frost over the Display is the glass service's Compositor kind (ADR 0029 section
 * 2): granted from the policy (Android 12, the window blur switch, battery saver, the layer's
 * health), put up and taken down on policy events only, followed in the glass commit, and falling
 * back to Wallpaper on a failure.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class KeyboardFrostTest {

    /** A layer that records what it is asked. */
    private static final class FakeLayer implements KeyboardFrost.Layer {
        boolean blurEnabled = true;
        boolean unavailable;
        final List<String> calls = new ArrayList<>();
        int follows;

        @Override public void want() { calls.add("want"); }
        @Override public boolean crossWindowBlurEnabled() { return blurEnabled; }
        @Override public boolean unavailable() { return unavailable; }
        @Override public boolean show(View host, SurfaceView display, int radiusPx, float cornerPx) {
            calls.add("show " + radiusPx);
            return radiusPx > 0;
        }
        @Override public void follow() { follows++; }
        @Override public void release() { calls.add("release"); }
    }

    private FramePipeline frames;
    private GlassService glass;
    private FakeLayer layer;
    private boolean powerSave;
    private View host;
    private KeyboardFrost frost;

    @Before
    public void setUp() {
        frames = new FramePipeline();
        glass = new GlassService(frames);
        layer = new FakeLayer();
        host = new View(RuntimeEnvironment.getApplication());
        frost = new KeyboardFrost(glass, Build.VERSION_CODES.TIRAMISU, () -> powerSave, layer);
    }

    @Test
    public void theCompositorIsGrantedWhereAndroidBlursWindowsAndFrostsAtTheLooksRadius() {
        assertEquals(GlassKind.COMPOSITOR, frost.show(host, null, 12, 2f, 30f));
        assertEquals(WindowLayer.GROUND, glass.entry(KeyboardFrost.ELEMENT).layer);
        // 12 dp at 2x density.
        assertTrue(layer.calls.contains("show 24"));
        // The grant came before the registration: no frame had to pass for the layer to go up.
        assertEquals(0, glass.policyEvents());
    }

    @Test
    public void whereAndroidDoesNotBlurWindowsTheBedFallsBackToWallpaper() {
        layer.blurEnabled = false;
        assertEquals(GlassKind.WALLPAPER, frost.show(host, null, 12, 2f, 30f));
        // No layer: a radius of 0 takes any away and leaves the bed see-through.
        assertEquals("show 0", layer.calls.get(layer.calls.size() - 1));
        // Below Android 12 the same.
        KeyboardFrost old = new KeyboardFrost(new GlassService(null), Build.VERSION_CODES.R,
            () -> false, new FakeLayer());
        assertEquals(GlassKind.WALLPAPER, old.show(new View(RuntimeEnvironment.getApplication()),
            null, 12, 2f, 30f));
    }

    @Test
    public void batterySaverIsAPolicyEventAppliedAtOnceAndCounted() {
        frost.show(host, null, 12, 2f, 30f);
        int before = glass.policyEvents();
        powerSave = true;
        // The broadcast reaches the keyboard's next pass, which restates its frost.
        assertEquals(GlassKind.WALLPAPER, frost.show(host, null, 12, 2f, 30f));
        assertEquals(before + 1, glass.policyEvents());
        assertEquals("show 0", layer.calls.get(layer.calls.size() - 1));
        powerSave = false;
        assertEquals(GlassKind.COMPOSITOR, frost.show(host, null, 12, 2f, 30f));
        assertEquals(before + 2, glass.policyEvents());
        assertEquals("show 24", layer.calls.get(layer.calls.size() - 1));
    }

    @Test
    public void aLayerThatFailsGivesTheKeyboardBackAsWallpaper() {
        frost.show(host, null, 12, 2f, 30f);
        assertEquals(GlassKind.COMPOSITOR, frost.granted());
        // The hidden call failed in the layer: it says so, the service takes the kind away at once.
        layer.unavailable = true;
        layer.blurEnabled = true;
        frost.show(host, null, 12, 2f, 30f);
        assertEquals(GlassKind.WALLPAPER, frost.granted());
        assertEquals(GlassKind.WALLPAPER, glass.granted(KeyboardFrost.ELEMENT));
        assertEquals("show 0", layer.calls.get(layer.calls.size() - 1));
    }

    @Test
    public void theLayerFollowsInTheGlassCommitOnlyWhileUp() {
        assertFalse(frames.isArmed(FrameStep.GLASS_COMMIT));
        frost.show(host, null, 12, 2f, 30f);
        assertTrue("shown: the commit runs on every drawn frame", frames.isArmed(FrameStep.GLASS_COMMIT));
        frames.flush("frame");
        frames.flush("frame");
        assertEquals(2, layer.follows);
        // A policy event takes the Compositor away: the layer is down and nothing is followed.
        layer.blurEnabled = false;
        frost.show(host, null, 12, 2f, 30f);
        frames.flush("frame");
        assertEquals(2, layer.follows);
        frost.release();
        assertFalse(frames.isArmed(FrameStep.GLASS_COMMIT));
        assertEquals(0, glass.size());
        assertNull(frost.granted());
        assertEquals("release", layer.calls.get(layer.calls.size() - 1));
    }

    @Test
    public void aSwitchTurningWhileShownIsAPolicyEventThroughTheService() {
        frost.show(host, null, 12, 2f, 30f);
        layer.blurEnabled = false;
        glass.setCompositorOn(false);
        frames.flush("frame");
        // The registry moved the kind; the surface is told in the commit.
        assertEquals(GlassKind.WALLPAPER, frost.granted());
        assertEquals("show 0", layer.calls.get(layer.calls.size() - 1));
    }
}
