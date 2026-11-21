package com.termux.app.chrome.scene;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Two shaders written to the photo-shader instructions compile on the device's own GL compiler
 * through the gate a pasted shader passes: one reading the photo and its depth image (with the
 * empty-depth fallback), the touch ease, the pointer and the page offset; one reading the photo only.
 * Both keep to the instructions' budget: at most 8 samples, loops of at most 4 steps.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class PhotoShaderContractInstrumentationTest {

    /** Photo and depth: 2 samples. */
    static final String WITH_DEPTH = ""
        + "// Sways the photo, near parts more than far ones; a touch pushes it and eases out.\n"
        + "vec2 coverUv(vec2 fragCoord) {\n"
        + "    vec2 res = iResolution.xy;\n"
        + "    vec2 img = iChannelResolution[0].xy;\n"
        + "    float k = max(res.x / img.x, res.y / img.y) * 1.04;\n"
        + "    return (fragCoord - 0.5 * res) / (img * k) + 0.5;\n"
        + "}\n"
        + "void mainImage(out vec4 fragColor, in vec2 fragCoord) {\n"
        + "    vec2 uv = coverUv(fragCoord);\n"
        + "    float near = 0.5;\n"
        + "    if (iChannelResolution[1].x != 0.0) near = texture(iChannel1, uv).r;\n"
        + "    float sway = sin(iTime * 0.6 + uv.y * 3.0) * 0.002;\n"
        + "    vec2 push = (fragCoord - iMouse.xy) / iResolution.y;\n"
        + "    float ease = exp(-tlTouchAge * 1.5) * smoothstep(0.4, 0.0, length(push));\n"
        + "    vec2 shift = vec2(sway + (tlPageOffset - 0.5) * 0.01, 0.0) * (0.3 + near)\n"
        + "        + push * ease * 0.02;\n"
        + "    fragColor = vec4(texture(iChannel0, uv + shift).rgb, 1.0);\n"
        + "}\n";

    /** Photo only: 3 samples in a loop of 3 steps. */
    static final String PHOTO_ONLY = ""
        + "// Sways the photo gently, in three slightly different phases.\n"
        + "void mainImage(out vec4 fragColor, in vec2 fragCoord) {\n"
        + "    vec2 res = iResolution.xy;\n"
        + "    vec2 img = iChannelResolution[0].xy;\n"
        + "    float k = max(res.x / img.x, res.y / img.y) * 1.04;\n"
        + "    vec2 uv = (fragCoord - 0.5 * res) / (img * k) + 0.5;\n"
        + "    vec3 sum = vec3(0.0);\n"
        + "    for (int i = 0; i < 3; i++) {\n"
        + "        float p = float(i) * 2.1;\n"
        + "        sum += texture(iChannel0, uv + vec2(sin(iTime * 0.5 + p) * 0.002, 0.0)).rgb;\n"
        + "    }\n"
        + "    fragColor = vec4(sum / 3.0, 1.0);\n"
        + "}\n";

    @Test
    public void theDepthShaderCompiles() {
        assertTrue(ShaderErrors.format(ShaderCheck.run(WITH_DEPTH)), ShaderCheck.passed(ShaderCheck.run(WITH_DEPTH)));
    }

    @Test
    public void thePhotoOnlyShaderCompiles() {
        assertTrue(ShaderErrors.format(ShaderCheck.run(PHOTO_ONLY)), ShaderCheck.passed(ShaderCheck.run(PHOTO_ONLY)));
    }
}
