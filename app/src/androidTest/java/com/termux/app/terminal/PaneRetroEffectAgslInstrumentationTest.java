package com.termux.app.terminal;

import static org.junit.Assert.assertNotNull;

import android.graphics.RuntimeShader;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The retro programs on a real device compiler: both compile, every uniform the binder writes is
 * one they declare (a missing name throws), and a pane's and the chrome's uniforms each make an
 * effect.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class PaneRetroEffectAgslInstrumentationTest {

    private static final String[] FLOAT_UNIFORMS = {
        "uDensity", "uRadius", "uBend", "uVignette", "uScreenRadius",
        "uAberr", "uAberrFlat", "uRecess", "uDim", "uEvent", "uEventT", "uSeed",
        "uFlicker", "uTime"
    };

    @Test
    public void bothProgramsCompileAndDeclareEveryUniformTheBinderWrites() {
        for (String src : new String[] {PaneRetroEffect.CRT_AGSL, PaneRetroEffect.TFT_AGSL}) {
            RuntimeShader shader = new RuntimeShader(src);
            assertNotNull(shader);
            shader.setFloatUniform("uSize", 240f, 320f);
            shader.setFloatUniform("uOrigin", 0f, 0f);
            shader.setFloatUniform("uCard", 0f, 0f, 240f, 320f);
            shader.setFloatUniform("uTint", 0f, 0f, 0f, 0f);
            shader.setFloatUniform("uScreen", 22f, 28f, 218f, 292f);
            shader.setFloatUniform("uBendBox", 22f, 28f, 218f, 320f);
            for (String name : FLOAT_UNIFORMS) shader.setFloatUniform(name, 1f);
        }
    }

    @Test
    public void aPaneAndTheChromeEachMakeAnEffect() {
        RetroUniforms pane = new RetroUniforms().set(240f, 320f, 0f, 0f, 1f, 1f, 2f,
                0f, 0f, 240f, 320f, 24f, PaneRetroEffect.CRT_BEND, PaneRetroEffect.PANE_VIGNETTE)
            .screen(16f, 20f, 224f, 300f, 20f, 300f)
            .surface(RetroUniforms.PANE_ABERR_PX, 0f, 1f, 0f)
            .event(PaneScreenEvent.Kind.BELL_TEAR.id, 0.5f, 0.3f);
        RetroUniforms ambient = new RetroUniforms().set(240f, 320f, 0f, 0f, 1f, 1f, 2f,
                0f, 0f, 240f, 320f, 24f, PaneRetroEffect.CRT_BEND, PaneRetroEffect.PANE_VIGNETTE)
            .screen(16f, 20f, 224f, 300f, 20f, 300f)
            .surface(RetroUniforms.PANE_ABERR_PX, 0f, 1f, 1f)
            .event(PaneScreenEvent.Kind.AMBIENT_TEAR.id, 0.5f, 0.3f)
            .ambient(PaneAmbient.FLICKER_MAX, 3.2f);
        RetroUniforms chrome = new RetroUniforms().set(240f, 120f, 0f, 0f, 1f, 1f, 2f,
                0f, 0f, 240f, 120f, 0f, 0f, 0f)
            .surface(RetroUniforms.CHROME_ABERR_PX, 1f, 0f, 0f);
        for (PaneRetroStyle style : PaneRetroStyle.values()) {
            if (style == PaneRetroStyle.NONE) continue;
            assertNotNull(style.name(), new PaneRetroEffect().effectFor(style, pane));
            assertNotNull(style.name(), new PaneRetroEffect().effectFor(style, chrome));
            assertNotNull(style.name(), new PaneRetroEffect().effectFor(style, ambient));
        }
    }
}
