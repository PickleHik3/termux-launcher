package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class PaneRetroEffectTest {

    private static final String[] REQUIRED = {
        "uniform shader content", "uniform float2 uSize", "uniform float2 uOrigin", "uniform float uDensity",
        "uniform float4 uCard", "uniform float uRadius", "uniform half4 uTint", "uniform float uBend",
        "uniform float uVignette", "uniform float uAberr", "uniform float uAberrFlat",
        "uniform float uRecess", "uniform float uDim", "half4 main(float2 "
    };
    private static final String[] FORBIDDEN = {"uint", "<<", ">>", "#define", "^", "fwidth", "dFdx"};

    @Test public void bothSourcesDeclareTheContractAndAvoidUnsupportedSyntax() {
        for (String src : new String[]{PaneRetroEffect.CRT_AGSL, PaneRetroEffect.TFT_AGSL}) {
            for (String r : REQUIRED) assertTrue(r, src.contains(r));
            for (String f : FORBIDDEN) assertFalse(f, src.contains(f));
        }
    }

    @Test public void belowApi33NothingIsAvailableAndNoEffectIsMade() {
        assertFalse(PaneRetroEffect.available());
        RetroUniforms uniforms = new RetroUniforms().set(100f, 100f, 0f, 0f, 1f, 1f, 2f,
            0f, 0f, 100f, 100f, 8f, PaneRetroEffect.CRT_BEND, PaneRetroEffect.PANE_VIGNETTE);
        assertNull(new PaneRetroEffect().effectFor(PaneRetroStyle.CRT, uniforms));
    }

    @Test public void displayedPointInvertsTheCrtBendAndLeavesOtherStylesAlone() {
        float w = 1016f, h = 2048f, bend = PaneRetroEffect.CRT_BEND;
        float[] out = new float[2];
        for (float[] p : new float[][] {{0f, 0f}, {w, h}, {w / 2f, h / 2f}, {40f, 1990f}, {700f, 300f}}) {
            PaneRetroEffect.displayedPoint(PaneRetroStyle.CRT_AMBER, w, h, p[0], p[1], out);
            // The shader samples content at bend(displayed); it must be the point we started from.
            float cx = out[0] / w * 2f - 1f, cy = out[1] / h * 2f - 1f;
            float f = (1f + bend * (cx * cx + cy * cy)) / (1f + bend);
            assertEquals(p[0], (cx * f + 1f) * 0.5f * w, 0.5f);
            assertEquals(p[1], (cy * f + 1f) * 0.5f * h, 0.5f);
        }
        PaneRetroEffect.displayedPoint(PaneRetroStyle.TFT, w, h, 123f, 456f, out);
        assertEquals(123f, out[0], 0f);
        assertEquals(456f, out[1], 0f);
        assertTrue("the shader bends by the uniform the pane sets to CRT_BEND",
            PaneRetroEffect.CRT_AGSL.contains("c * (1.0 + uBend * dot(c, c)) / (1.0 + uBend)"));
        // Pinned at the middle of each edge: the content's edge middle shows at the screen's,
        // and its corner lands inside the screen, where the tube face fills the gap.
        PaneRetroEffect.displayedPoint(PaneRetroStyle.CRT, w, h, w, h / 2f, out);
        assertEquals(w, out[0], 0.5f);
        PaneRetroEffect.displayedPoint(PaneRetroStyle.CRT, w, h, w, h, out);
        // 1 - 1/f at the corner, f = (1 + 2b)/(1 + b): 3.7 % of the half-extent, 1.8 % of the whole.
        assertTrue(out[0] < w - 0.015f * w && out[1] < h - 0.015f * h);
    }

    @Test public void thePictureSitsInADarkTubeFaceUnderASteepVignetteAndACentreHaze() {
        String crt = PaneRetroEffect.CRT_AGSL;
        // The barrel outline is the screen's rounded rect in the bent space, anti-aliased.
        assertTrue(crt.contains("float pic = 1.0 - smoothstep(-0.5, 0.5, roundDist(wp, uScreen, uScreenRadius));"));
        assertTrue(crt.contains("rgb = mix(float3(" + PaneRetroEffect.TUBE_RGB + ") * "
            + PaneRetroEffect.TUBE_ALPHA + ", rgb, pic);"));
        assertTrue(crt.contains("a = mix(" + PaneRetroEffect.TUBE_ALPHA + ", a, pic);"));
        assertTrue(PaneRetroEffect.TUBE_ALPHA > 0.8f && PaneRetroEffect.TUBE_ALPHA < 1f);
        // Vignette in the picture's own space, full across its middle, uVignette lost at the edge.
        assertTrue(crt.contains("float vig = mix(1.0 - uVignette, 1.0, pow(clamp(vv.x * vv.y * 18.0, 0.0, 1.0), 0.4));"));
        assertEquals(0.30f, PaneRetroEffect.PANE_VIGNETTE, 0f);
        // The haze is additive at the middle, in the tint's colour, inside the picture's alpha.
        assertTrue(crt.contains("haze * " + PaneRetroEffect.AMBIENT + " * exp(-6.0 * dot(c, c)) * a"));
        assertTrue(PaneRetroEffect.AMBIENT <= 0.06f);
    }

    @Test public void scanlinesAndCellsAreTheScreensRowsNotTheBentPicture() {
        // Straight rows, as kitty draws them, offset by the view's place on the screen: the bend
        // moves the picture and never the scanlines, which run on into the next surface.
        assertTrue(PaneRetroEffect.CRT_AGSL.contains("cos(2.0 * PI * (p.y + uOrigin.y) / (3.0 * uDensity))"));
        assertFalse(PaneRetroEffect.CRT_AGSL.contains("sp.y"));
        assertTrue(PaneRetroEffect.TFT_AGSL.contains("fract((p + uOrigin) / (3.0 * uDensity))"));
        assertEquals(3f, RetroUniforms.PERIOD_DP, 0f);
    }

    @Test public void theRicherCrtSplitsColourMasksTriadsAndRecessesOnlyThePane() {
        String crt = PaneRetroEffect.CRT_AGSL;
        // The split follows the bent radius on a pane and is constant on the flat chrome.
        assertTrue(crt.contains("mix(w, float2(1.0, 0.0), uAberrFlat) * uAberr"));
        assertTrue(crt.contains("clamp(at + split, inMin, inMax)"));
        assertTrue(crt.contains("clamp(at - split, inMin, inMax)"));
        // The triad runs on the screen's columns, like the scanlines run on its rows.
        assertTrue(crt.contains("fract((p.x + uOrigin.x) / (3.0 * uDensity))"));
        assertEquals(0.10f, PaneRetroEffect.TRIAD_DEPTH, 0f);
        // Recess and sheen are gated by uRecess; no time uniform, nothing moves.
        assertTrue(crt.contains("uRecess * " + PaneRetroEffect.RECESS_SHADE));
        assertTrue(crt.contains("uRecess * " + PaneRetroEffect.SHEEN_PEAK));
        assertEquals(0.25f, PaneRetroEffect.RECESS_SHADE, 0f);
        assertEquals(6f, PaneRetroEffect.RECESS_DP, 0f);
        assertTrue(PaneRetroEffect.SHEEN_PEAK <= 0.06f);
        // Time reaches the picture only through the ambient flicker (Idle animations, wave 4),
        // whose depth uFlicker is 0 unless its loop runs: the static pass itself never moves.
        String flickLine = "float flick = sin(uTime * 2.0 * PI * 1.3) * 0.6"
            + " + sin(uTime * 2.0 * PI * 0.37 + 1.7) * 0.4;";
        assertTrue(crt.contains(flickLine));
        assertTrue(crt.contains("rgb *= 1.0 - uFlicker * (0.5 + 0.5 * flick);"));
        String main = crt.substring(crt.indexOf("half4 main(")).replace(flickLine, "");
        assertFalse(main.contains("uTime"));
        // Bend, vignette, glow radius and scanline depth stay as they were.
        assertTrue(crt.contains("float g = 1.5 * uDensity;"));
        assertTrue(crt.contains("rgb = rgb + 0.35 * bloom;"));
        assertTrue(crt.contains("float scan = mix(0.70, 1.0,"));
    }

    @Test public void textIsSharpenedOnTheBentScreenAndTheGlowBloomsFromBrightPixelsOnly() {
        String crt = PaneRetroEffect.CRT_AGSL;
        // A 1 px unsharp mask, gated by the bend: the flat chrome (uBend 0) gets none.
        assertTrue(crt.contains("float k = " + PaneRetroEffect.SHARPEN + " * step(0.001, uBend);"));
        assertTrue(crt.contains("clamp(float3(col.rgb) * (1.0 + 4.0 * k) - k * nb, 0.0, a)"));
        assertTrue(crt.contains("content.eval(clamp(at + float2(1.0, 0.0), inMin, inMax))"));
        assertTrue(crt.contains("content.eval(clamp(at - float2(0.0, 1.0), inMin, inMax))"));
        assertTrue(PaneRetroEffect.SHARPEN > 0f && PaneRetroEffect.SHARPEN <= 0.3f);
        // The glow's floor: anti-aliased edges and faint pixels add nothing.
        assertTrue(crt.contains("max(float3(glow.rgb) - " + PaneRetroEffect.BLOOM_FLOOR + ", 0.0)"));
        assertTrue(PaneRetroEffect.BLOOM_FLOOR > 0.3f && PaneRetroEffect.BLOOM_FLOOR < 0.6f);
        // The Material CRT keeps a pixel's own colour by its saturation; the others tint all.
        assertTrue(crt.contains("uniform float uTintKeep;"));
        assertTrue(crt.contains("float tw = float(uTint.a) * (1.0 - uTintKeep * smoothstep(0.15, 0.5, sat));"));
        assertTrue(crt.contains("rgb = mix(rgb, lum * float3(uTint.rgb) * 1.3, tw);"));
    }

    @Test public void thePaneAndTheChromeEachSetTheirOwnSurfaceParts() {
        View view = new View(RuntimeEnvironment.getApplication());
        RetroEffectBinder chrome = RetroEffectBinder.flat(view);
        assertEquals(0.3f, chrome.mAberr, 0f);
        assertEquals(1f, chrome.mAberrFlat, 0f);
        assertEquals(0f, chrome.mRecess, 0f);
        RetroEffectBinder pane = RetroEffectBinder.pane(view, new RetroEffectBinder.ScreenShape() {
            @Override public float card(View v, float[] rect) { return 0f; }
            @Override public float screen(View v, float[] rect) { return 0f; }
        });
        assertEquals(0.6f, pane.mAberr, 0f);
        assertEquals(0f, pane.mAberrFlat, 0f);
        assertEquals(1f, pane.mRecess, 0f);
        chrome.release();
        pane.release();
    }

    @Test public void setRetroStyleOnOldApiIsHarmless() {
        PaneContentFrame frame = new PaneContentFrame(RuntimeEnvironment.getApplication());
        for (PaneRetroStyle s : PaneRetroStyle.values()) {
            frame.setRetroStyle(s);
            // Nothing is drawn below API 33, so nothing re-aims the cursor trail through a bend.
            assertEquals(PaneRetroStyle.NONE, frame.retroStyle());
        }
        frame.setRetroStyle(null);
        frame.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, 300, 300);
        assertEquals(300, frame.getWidth());
    }
}
