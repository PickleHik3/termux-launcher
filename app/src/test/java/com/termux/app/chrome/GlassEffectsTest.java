package com.termux.app.chrome;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

/**
 * The effect builder's decisions: when it builds, when it remembers, when a lens rebuild may touch
 * the backdrop. The shader itself does not run on the JVM, so every build here ends where the
 * program would be made.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassEffectsTest {

    private static final class FakeHost implements GlassEffects.Host {
        int backdropReads;
        int peekReads;
        int surfaceGlassWrites;

        @Override public float density() { return 2f; }
        @Nullable @Override public View dockBackdrop() { backdropReads++; return null; }
        @Nullable @Override public float[] peekGlassRect() { peekReads++; return null; }
        @Nullable @Override public GlassRefraction.Look fancierLook() { return null; }
        @Override public float dockCover() { return 0f; }
        @Override public float navStripCover() { return 0f; }
        @Override public void applyDockSurfaceGlass(@NonNull GlassRefraction.Program program) {
            surfaceGlassWrites++;
        }
        @Override public boolean keyLensActive() { return false; }
        @Override public float keyLensIntensity() { return 0f; }
        @Override public void keyLens(@NonNull float[] out) { }
    }

    @Test
    public void belowAndroid13NothingIsBuiltOrRemembered() {
        FakeHost host = new FakeHost();
        GlassEffects effects = new GlassEffects(host);
        assertNull(effects.buildDockAndRemember(0f, 0f, 0f, 100f, 50f, 8f));
        assertNull(effects.buildNavStrip(0f, 0f, 100f, 50f));
        assertFalse(effects.paramsValid());
        assertNull(effects.dockProgram());
        assertEquals(0, host.peekReads);
        assertEquals(0, host.surfaceGlassWrites);
    }

    @Test
    public void lensRebuildDoesNotTouchTheBackdropWithoutRememberedParams() {
        FakeHost host = new FakeHost();
        GlassEffects effects = new GlassEffects(host);
        effects.rebuildForLens();
        assertEquals(0, host.backdropReads);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
    public void aFailedBuildLeavesParamsInvalidSoALensRebuildStaysOut() {
        FakeHost host = new FakeHost();
        GlassEffects effects = new GlassEffects(host);
        // No RuntimeShader on the JVM: the build falls back to null, the caller's plain blur.
        if (effects.buildDockAndRemember(0f, 0f, 0f, 100f, 50f, 8f) == null) {
            assertFalse(effects.paramsValid());
            effects.rebuildForLens();
            assertEquals(0, host.backdropReads);
        }
    }

    @Test
    public void invalidatingParamsClearsThem() {
        GlassEffects effects = new GlassEffects(new FakeHost());
        effects.invalidateParams();
        assertFalse(effects.paramsValid());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
    public void theNavStripNeverReadsThePeekCardOrTheDockSurface() {
        FakeHost host = new FakeHost();
        GlassEffects effects = new GlassEffects(host);
        effects.buildNavStrip(0f, 0f, 100f, 50f);
        assertEquals(0, host.peekReads);
        assertEquals(0, host.surfaceGlassWrites);
        assertNull(effects.dockProgram());
    }
}
