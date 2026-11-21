package com.termux.app.terminal;

import static org.junit.Assert.assertNotNull;

import android.graphics.RuntimeShader;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

/** The cursor-trail halo program compiles on a real device compiler and declares every uniform. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class CursorTrailHaloAgslInstrumentationTest {

    @Test
    public void compilesAndDeclaresEveryUniform() {
        RuntimeShader shader = new RuntimeShader(CursorTrailHalo.AGSL);
        assertNotNull(shader);
        shader.setFloatUniform("uP0", 0f, 0f);
        shader.setFloatUniform("uP1", 100f, 0f);
        shader.setFloatUniform("uP2", 100f, 20f);
        shader.setFloatUniform("uP3", 0f, 20f);
        shader.setFloatUniform("uHead", 95f, 10f);
        shader.setFloatUniform("uTail", 0f, 10f);
        shader.setFloatUniform("uWidth", 24f);
        shader.setFloatUniform("uColor", 1f, 1f, 1f, 0.55f);
        shader.setFloatUniform("uOpacity", 1f);
    }
}
