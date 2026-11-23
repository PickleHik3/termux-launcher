package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.regex.Pattern;

/**
 * The pure half of the glass: how the stored knobs become uniforms, the Java mirrors of the
 * program's pull, hairline and colour split, and where a surface's rim runs.
 * The program itself compiles only on a phone; nothing here needs one, though its text is read to
 * check it declares every uniform the Java side writes and runs the expressions the mirrors do.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassRefractionTest {

    private static final float DENSITY = 2.625f; // pong, 420 dpi

    @Test
    public void theDefaultLookIsTheDocksOwnDepth() {
        // The dock's pull was a 9 dp literal in the activity's shader setup; it is the slab's
        // thickness now.
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(9f * DENSITY, look.thicknessPx(DENSITY), 1e-4f);
    }

    @Test
    public void theKnobsMapToUniformsAtTheScreensDensity() {
        GlassRefraction.Look look = new GlassRefraction.Look(12, 30, 50);
        assertEquals(12f * DENSITY, look.thicknessPx(DENSITY), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0).thicknessPx(DENSITY), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(-3, 1, 0).thicknessPx(DENSITY), 1e-4f);
    }

    @Test
    public void theEdgeTermsAreOffByDefaultSoExistingLooksAreUnchanged() {
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(0, look.specularPercent);
        assertEquals(0, look.dispersionPercent);
        assertEquals(0f, look.dispersion(), 0f);
        assertEquals("the three-knob constructor leaves them off",
            new GlassRefraction.Look(12, 30, 50), new GlassRefraction.Look(12, 30, 50, 0, 0));
        assertEquals(0, look.clarityPercent);
        assertEquals("the five-knob constructor leaves clarity off",
            new GlassRefraction.Look(12, 30, 50, 3, 4), new GlassRefraction.Look(12, 30, 50, 3, 4, 0));
    }

    @Test
    public void theEdgeReadsTheSharpPictureWhollyWhereOneIsBoundAndTheFaceKeepsItsShare() {
        // A clearer picture of its own bound: the rim shows it whole, whatever the stored clarity.
        float edge = GlassRefraction.sharpEdge(true, true);
        assertEquals(1f, edge, 0f);
        for (boolean soft : new boolean[] {false, true}) {
            assertEquals(1f, GlassRefraction.sharpMix(0f, soft, edge, 0f), 0f);
            // Half way at the band's middle, and none at its inner edge or on the face.
            assertEquals(0.5f, GlassRefraction.sharpMix(0.5f, soft, edge, 0f), 1e-6f);
            assertEquals(0f, GlassRefraction.sharpMix(1f, soft, edge, 0f), 0f);
        }
        // The frost bound in its place, or nothing bound: no sharp share, no second sample.
        assertEquals(0f, GlassRefraction.sharpEdge(true, false), 0f);
        assertEquals(0f, GlassRefraction.sharpEdge(false, false), 0f);
        assertEquals(0f, GlassRefraction.sharpMix(0f, false, GlassRefraction.sharpEdge(true, false), 0f), 0f);
        // The face's share is Clear's face clarity, unchanged: 0 for a look without it, its own
        // share for Clear, and only where a picture of its own is bound.
        GlassRefraction.Look frosted = new GlassRefraction.Look(24, 16, 10, 0, 0, 0);
        GlassRefraction.Look clear = new GlassRefraction.Look(27, 18, 10, 0, 40, 76)
            .withFaceTerms(100, 0, 100);
        assertEquals(0f, GlassRefraction.faceClarity(frosted, true, true), 0f);
        assertEquals(1f, GlassRefraction.faceClarity(clear, true, true), 0f);
        assertEquals(0f, GlassRefraction.faceClarity(clear, true, false), 0f);
        assertEquals(0f, GlassRefraction.faceClarity(null, true, true), 0f);
        assertEquals(0.3f, GlassRefraction.sharpMix(1f, false, edge, 0.3f), 1e-6f);
        assertEquals(1f, GlassRefraction.sharpMix(0f, false, edge, 0.3f), 1e-6f);
        // The stored clarity no longer reaches the program: two looks apart only in it draw alike.
        assertEquals(GlassRefraction.faceClarity(new GlassRefraction.Look(9, 20, 18, 0, 0, 0), true, true),
            GlassRefraction.faceClarity(new GlassRefraction.Look(9, 20, 18, 0, 0, 100), true, true), 0f);
    }

    @Test
    public void theProgramMixesTheSharpPictureByTheEdgeShareAndTheFaceShareAlone() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("float edgeK = x > 0.0 ? (uSoftRim > 0.0 ? x * x * x * (x * (x * 6.0 - 15.0) + 10.0)"));
        // e runs 0 at the outline to 1 on the face, and stays 1 on every face pixel.
        assertTrue(agsl.contains("    float x = 1.0 - e;\n"));
        assertTrue(agsl.contains(": x * x * (3.0 - 2.0 * x)) : 0.0;"));
        assertTrue(agsl.contains("float clarityK = edgeK * uSharpEdge;"));
        assertTrue(agsl.contains("clarityK = mix(clarityK, 1.0, faceMix);"));
        // No knob scales the edge, and nothing raises it while the slab moves.
        assertFalse(agsl.contains("uClarity"));
        assertFalse(agsl.contains("uLiveClarity"));
        assertFalse(agsl.contains("kLiveReach"));
    }

    @Test
    public void dispersionIsAShareOfThePullAndIsClamped() {
        // 100% is the whole spread of red's and blue's index (0.08 as the program's uDisp);
        // Clear's 40% is 0.02, and up to it every value is what it was (0.05 per whole), Edge 80
        // is 0.06.
        assertEquals(0.08f, GlassRefraction.Look.MAX_DISPERSION_SHARE, 0f);
        assertEquals(0.02f, new GlassRefraction.Look(27, 18, 10, 0, 40).dispersion(), 1e-6f);
        assertEquals("Glint exactly as it was", 0.4f * 0.05f,
            new GlassRefraction.Look(27, 18, 10, 0, 40).dispersion(), 0f);
        assertEquals(0.25f * 0.05f, new GlassRefraction.Look(27, 18, 10, 0, 25).dispersion(), 0f);
        assertEquals(0.06f, new GlassRefraction.Look(27, 18, 10, 0, 80).dispersion(), 1e-6f);
        assertEquals(GlassRefraction.Look.MAX_DISPERSION_SHARE,
            new GlassRefraction.Look(0, 1, 0, 0, 100).dispersion(), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_DISPERSION_SHARE,
            new GlassRefraction.Look(0, 1, 0, 0, 400).dispersion(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0, 0, -5).dispersion(), 0f);
    }

    @Test
    public void theColourSplitIsEvenRoundTheOutlineAndFollowsTheBend() {
        // Red and blue refract at their own index, so the split depends only on how far in a
        // pixel is: the same on a straight and round a corner, and none on the face.
        assertFalse(GlassRefraction.AGSL.contains("cornerWeight"));
        assertFalse(GlassRefraction.AGSL.contains("kSplitWeight"));
        assertTrue(GlassRefraction.AGSL.contains(
            "shiftR = slabT * snellTan(slope, kIor - kDispRed * dk);"));
        assertTrue(GlassRefraction.AGSL.contains(
            "shiftB = slabT * snellTan(slope, kIor + kDispBlue * dk);"));
        assertTrue(GlassRefraction.AGSL.contains("    if (max(dot(oR, oR), dot(oB, oB)) > 0.25) {\n"));
        GlassSlab slab = GlassSlab.DEFAULT;
        GlassRefraction.Look clear = new GlassRefraction.Look(27, 18, 10, 0, 40, 76);
        float share = clear.dispersion() / GlassRefraction.Look.MAX_DISPERSION_SHARE;
        float w = slab.lensPx(DENSITY);
        float t = clear.thicknessPx(DENSITY);
        // A px and more at the outline on Clear's Edge, so three taps there; under half a px
        // half way in, so one.
        float atOutline = slab.shiftPx(0f, w, t, slab.iorBlue(share)) - slab.shiftPx(0f, w, t, slab.iorRed(share));
        assertTrue("at the outline: " + atOutline, atOutline > 0.5f);
        float halfWay = slab.shiftPx(w / 2f, w, t, slab.iorBlue(share)) - slab.shiftPx(w / 2f, w, t, slab.iorRed(share));
        assertTrue("half way in: " + halfWay, halfWay < atOutline);
        assertEquals(0f, slab.shiftPx(w, w, t, slab.iorBlue(share)) - slab.shiftPx(w, w, t, slab.iorRed(share)), 0f);
        // Edge 0 parts nothing.
        assertEquals(slab.ior, slab.iorRed(0f), 0f);
    }

    @Test
    public void theEdgeTermsAreInTheLooksValueIdentity() {
        GlassRefraction.Look base = new GlassRefraction.Look(9, 20, 18, 0, 0);
        assertEquals(base, GlassRefraction.Look.DEFAULT);
        assertNotEquals(base, new GlassRefraction.Look(9, 20, 18, 1, 0));
        assertNotEquals(base, new GlassRefraction.Look(9, 20, 18, 0, 1));
        assertNotEquals(new GlassRefraction.Look(9, 20, 18, 1, 0).hashCode(),
            new GlassRefraction.Look(9, 20, 18, 0, 1).hashCode());
    }

    @Test
    public void theProgramDeclaresEveryUniformTheJavaSideWrites() {
        // A uniform the program does not declare throws on the write, and the surface drops to plain.
        String[] written = {
            "uFrameScale", "uFrameOffset", "uLocalX", "uLocalY", "uLocalW", "uRectMin", "uRectMax",
            "uRadius", "uDepth", "uDisp", "uDensity",
            "uLensActive", "uLensCenter", "uLensHalf", "uLensRadius", "uLensStrength", "uLensFeather",
            "uSharpEdge", "uFaceClarity", "uTouchActive", "uTouch", "uTouchRadius",
            "uTouchLens", "uPose", "uPoseLight", "uPoseHalf", "uGlint", "uSoftRim", "uShelf",
            "uBead", "uBeadXA", "uBeadXB", "uBeadXC", "uBeadCap", "uConcave",
        };
        for (String name : written) {
            assertTrue(name + " is declared",
                Pattern.compile("uniform \\w+ " + name + ";").matcher(GlassRefraction.AGSL).find());
        }
        assertTrue(GlassRefraction.AGSL.contains("uniform shader content;"));
        assertTrue("the clearer picture is a second input", GlassRefraction.AGSL.contains("uniform shader sharp;"));
        // The knobs the hairline no longer takes, and the slab lens and thickness shading neither
        // reference draws, are gone from the program.
        // The white hairline the gathered light replaced, and the switch that chose between them,
        // are gone as well.
        for (String gone : new String[] {"uRim", "uLobeK", "uShade", "uThick", "uSlab", "uRimW",
            "slabOffset", "softClamp", "kKnee", "uGathered", "kRim", "kShade", "kLobeK", "kLight",
            // The gathered light, its gain and every compensation for it (#314).
            "uEdgeGain", "kGain", "kLift", "kPaleCut", "kEdgeShade", "kLiveEdgeShade", "heldK",
            "kGlowDp", "kGlowReachDp", "kAmbient", "gatherShare", "liveGatherShare",
            "liveShadeShare", "kBoldLobeDp", "boldS", "rimSplit", "kRimSplitDp", "kSharpClarity",
            "kGlintDisp", "kMaxDisp",
            // The narrow ring and its band, pull and caps (#317: the slab's lens took its place).
            "uBand", "uStrength", "edgeProfile", "kDecay", "softProfile", "kSoftPull",
            "kSplitWeight", "sBand", "sStrength", "softB", "softS", "pull"}) {
            assertFalse(gone + " is gone", Pattern.compile("\\b" + gone + "\\b").matcher(GlassRefraction.AGSL).find());
        }
    }

    @Test
    public void lensBeadsAreEachTheirOwnSurface() {
        String agsl = GlassRefraction.AGSL;
        // The nearest bead is the surface: a circle about it, its rect, its rim and its normal.
        assertTrue(agsl.contains("bool beads = uBead.y > 0.0;"));
        assertTrue(agsl.contains("center = float2(nearestBead(fragCoord.x), uBead.x);"));
        assertTrue(agsl.contains("float inside = -sdRoundRect(p, b, cr);"));
        assertTrue(agsl.contains("float2 n = rectNormal(p, b, min(cr, min(b.x, b.y)));"));
        // A pixel clear of every bead reads nothing; a bead's outline is anti-aliased.
        assertTrue(agsl.contains("if (beads && inside < -1.0) {"));
        assertTrue(agsl.contains("col = col * half(clamp(inside + 0.5, 0.0, 1.0));"));
        // No loop and no array: one compare a slot.
        assertFalse(agsl.contains("for ("));
        assertFalse(Pattern.compile("uniform \\w+ \\w+\\[").matcher(agsl).find());
        assertEquals(GlassRefraction.BEAD_SLOTS, count(agsl, "if (d < bd)") + 1);
        assertTrue(agsl.contains("const float kBeadBandShare = " + GlassRefraction.BEAD_BAND_SHARE + ";"));
    }

    @Test
    public void theSearchFieldsCapsuleIsOneMoreBeadShape() {
        String agsl = GlassRefraction.AGSL;
        // A stadium from its rect, read only under beads, and only where it is set.
        assertTrue(agsl.contains("if (uBeadCap.z > uBeadCap.x) {"));
        assertTrue(agsl.contains("float capR = min(capB.x, capB.y);"));
        // A pixel is the capsule's where it stands deeper in it than in its nearest bead, so the
        // melt's swelling beads take it over; one more distance, no pass and no sample of its own.
        assertTrue(agsl.contains(
            "if (sdRoundRect(fragCoord - capC, capB, capR) < sdRoundRect(fragCoord - center, b, cr)) {"));
        // Its rim band is held by its own corner, as a bead's is by its radius.
        assertTrue(agsl.contains("kBeadBandShare * cr)"));
    }

    @Test
    public void theProgramReadsTwelveBeads() {
        String agsl = GlassRefraction.AGSL;
        // Twelve slots: a swipe carries the neighbouring page's beads in beside a full row.
        assertTrue(agsl.contains("if (d < bd) { bd = d; bx = uBeadXC.w; }"));
        // Every slot beyond the beads given is parked, never nearer than a real bead.
        float[] twelve = new float[12];
        for (int i = 0; i < twelve.length; i++) twelve[i] = 50f + i * 100f;
        float[] slots = GlassRefraction.beadSlots(twelve, 14);
        assertEquals(1150f, slots[11], 0f);
        assertEquals(1150f, GlassRefraction.nearestBeadX(5000f, slots), 0f);
    }

    @Test
    public void theNearestBeadIsTheOneWhoseCentreIsClosest() {
        float[] centers = {100f, 300f, 500f};
        assertEquals(100f, GlassRefraction.nearestBeadX(0f, centers), 0f);
        assertEquals(100f, GlassRefraction.nearestBeadX(199f, centers), 0f);
        assertEquals(300f, GlassRefraction.nearestBeadX(201f, centers), 0f);
        assertEquals(500f, GlassRefraction.nearestBeadX(10000f, centers), 0f);
        assertEquals("a tie is the first", 100f, GlassRefraction.nearestBeadX(200f, centers), 0f);
        // The program's twelve slots: the beads in order, the rest parked where nothing is nearer.
        float[] slots = GlassRefraction.beadSlots(centers, 3);
        assertEquals(12, slots.length);
        assertEquals(GlassRefraction.BEAD_SLOTS, slots.length);
        assertEquals(100f, slots[0], 0f);
        assertEquals(500f, slots[2], 0f);
        for (int i = 3; i < slots.length; i++)
            assertEquals(GlassRefraction.BEAD_UNUSED_X, slots[i], 0f);
        for (float x = -50f; x < 2000f; x += 7f)
            assertEquals(GlassRefraction.nearestBeadX(x, centers),
                GlassRefraction.nearestBeadX(x, slots), 0f);
    }

    @Test
    public void aBeadsLensIsTightAndItsSlopeSteeper() {
        GlassSlab slab = GlassSlab.DEFAULT;
        float density = DENSITY;
        float bead = 35f * density;
        float soft = GlassRefraction.BEAD_SOFT_RIM_DP * density;
        float zone = GlassRefraction.lensZonePx(slab, bead, density, 1f, soft, bead, 0f);
        // Never past 0.4 of the radius: 14 dp on a 70 dp bead.
        assertEquals(0.4f * bead, zone, 1e-3f);
        assertTrue(zone < GlassRefraction.SOFT_RIM_DP * density);
        // A small bead takes its own soft band, held the same way.
        float small = 12f * density;
        assertEquals(0.4f * small, GlassRefraction.lensZonePx(slab, small, density, 1f, soft, small, 0f), 1e-3f);
        // The same thickness over a narrower zone than the tray's: a steeper, stronger lens.
        float depth = GlassRefraction.Look.DEFAULT.thicknessPx(density);
        float tray = GlassRefraction.lensZonePx(slab, 200f * density, density, 1f,
            GlassRefraction.SOFT_RIM_DP * density, 0f, 0f);
        float beadT = GlassRefraction.lensThicknessPx(slab, depth, bead, density, 1f, 0f);
        float trayT = GlassRefraction.lensThicknessPx(slab, depth, 200f * density, density, 1f, 0f);
        assertTrue(beadT / zone > trayT / tray);
    }

    @Test
    public void noPixelReadsMoreThanSixSamples() {
        // 3 + 3 where the colours part, 1 + 1 elsewhere in the lens zone and 1 on the face. Four
        // of each in the text: three on the split path, one plain; and the concave lens's one,
        // which reads that sample and returns.
        assertEquals(5, count(GlassRefraction.AGSL, "content.eval("));
        assertEquals(4, count(GlassRefraction.AGSL, "sharp.eval("));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    @Test
    public void theDepthHasNoCapOfItsOwn() {
        // Snell's law bounds the bend (GlassSlab.snellTan), so the Depth goes to the program as
        // it is; only the half short side holds the slab (lensThicknessPx).
        // Past the realistic slab the higher index is folded in (GlassMaterial.thicknessBoost).
        assertEquals(30f * DENSITY * com.termux.shared.termux.settings.preferences.GlassMaterial
            .thicknessBoost(30), new GlassRefraction.Look(30, 10, 30).thicknessPx(DENSITY), 1e-3f);
        assertEquals(6f * DENSITY, new GlassRefraction.Look(6, 28, 30).thicknessPx(DENSITY), 1e-4f);
        int[] shipped = {27, 24, 21, 9};
        for (int depth : shipped)
            assertEquals(depth * DENSITY, new GlassRefraction.Look(depth, 18, 10).thicknessPx(DENSITY), 1e-4f);
    }

    @Test
    public void theProgramRunsTheSlabsProfileAndSnell() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("    float u = 1.0 - max(e, kSlabEMin);\n"));
        assertTrue(agsl.contains(
            "    return pow(u, kSlabP - 1.0) * pow(1.0 - pow(u, kSlabP), 1.0 / kSlabP - 1.0);\n"));
        assertTrue(agsl.contains("    float si = s * inversesqrt(1.0 + s * s);\n"));
        assertTrue(agsl.contains("    return (s - t2) / (1.0 + s * t2);\n"));
        assertTrue(agsl.contains("            slope = slabT / wr * slabSlope(e);\n"));
        assertTrue(agsl.contains("const float kSlabEMin = 0.0001;"));
        assertEquals(1.0e-4f, GlassSlab.E_MIN, 0f);
    }

    @Test
    public void theBendLivesInTheLensZoneAndTheFaceStaysWhereItIs() {
        GlassSlab slab = GlassSlab.DEFAULT;
        float w = slab.lensPx(DENSITY), t = 27f * DENSITY;
        // Strong at the border: about the whole Snell bound a px in.
        float bound = t * (float) Math.sqrt(slab.ior * slab.ior - 1f);
        assertTrue(slab.shiftPx(1f, w, t) > 0.5f * bound);
        // Zero where the zone meets the face and everywhere past it.
        assertEquals(0f, slab.shiftPx(w, w, t), 0f);
        assertEquals(0f, slab.shiftPx(w * 3f, w, t), 0f);
        // Falling from the outline all the way to the face.
        float last = Float.MAX_VALUE;
        for (int i = 0; i <= 20; i++) {
            float shift = slab.shiftPx(w * i / 20f, w, t);
            assertTrue("falls toward the face at " + i, shift <= last);
            last = shift;
        }
        // The program skips every lens term on the face and samples exactly fragCoord there.
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("    if (inside < reach && uDepth > 0.0) {\n"));
        assertTrue(agsl.contains("    float shiftG = 0.0;\n"));
        assertTrue(agsl.contains("float2 sampleCoord = fragCoord - nb * shiftG;"));
    }

    @Test
    public void theOutlineShowsThePictureFromDeepestIn() {
        // A thick slab's edge stands up, so the outline's own pixel shows the picture bent the
        // furthest, about T sqrt(n² - 1) in (#317; the narrow ring eased it to nothing there).
        // The rim's line is drawn over it.
        GlassSlab slab = GlassSlab.DEFAULT;
        float w = slab.lensPx(DENSITY), t = 9f * DENSITY;
        float bound = t * (float) Math.sqrt(slab.ior * slab.ior - 1f);
        assertEquals(bound, slab.shiftPx(0f, w, t), 0.1f * bound);
        assertEquals("outside the outline it reads as the outline", slab.shiftPx(0f, w, t),
            slab.shiftPx(-3f, w, t), 0f);
        // The tilted face's soft shift is gone (#318): a turn shifts the whole picture alike.
        assertFalse(GlassRefraction.AGSL.contains("rimW"));
        assertFalse(GlassRefraction.AGSL.contains("tiltOffset"));
    }

    @Test
    public void theBendIsHeldAtHalfTheShortSide() {
        // A small pane (half its short side 30 px) under a deep Depth reaches in at most to its
        // middle line, at the outline, and the profile only shortens that inward.
        GlassSlab slab = GlassSlab.DEFAULT;
        float half = 30f;
        float w = GlassRefraction.lensZonePx(slab, half, DENSITY, 1f, 0f, 0f, 0f);
        float t = GlassRefraction.lensThicknessPx(slab, 60f * DENSITY, half, DENSITY, 1f, 0f);
        for (int i = 0; i <= 60; i++) {
            float inside = i * 0.5f;
            float shift = slab.shiftPx(inside, w, t);
            assertTrue("at " + inside + ": " + shift, shift <= half + 1e-3f);
        }
        assertTrue(GlassRefraction.AGSL.contains(
            "        float slabT = min(uDepth * sizeK * sizeShare * thick, hs * kSlabHold);\n"));
        // A large surface is not held.
        assertEquals(60f * DENSITY, GlassRefraction.lensThicknessPx(slab, 60f * DENSITY,
            1000f * DENSITY, DENSITY, 1f, 0f), 1e-3f);
    }

    @Test
    public void theDepthMovesTheBendOneWay() {
        GlassSlab slab = GlassSlab.DEFAULT;
        float w = slab.lensPx(DENSITY);
        float lastShift = -1f, lastDisp = -1f;
        for (int v = 0; v <= 100; v++) {
            GlassRefraction.Look look = new GlassRefraction.Look(v * 30 / 100, 18, 10, 0, v);
            float shift = slab.shiftPx(w / 4f, w, look.thicknessPx(DENSITY));
            assertTrue("depth", shift >= lastShift);
            assertTrue("dispersion", look.dispersion() >= lastDisp);
            lastShift = shift;
            lastDisp = look.dispersion();
        }
    }

    @Test
    public void theEdgeIsFixedMaterialWhateverTheStoredLightSays() {
        // Two looks that differ only in the stored light and specular draw the same uniforms,
        // and so do two apart only in the stored Edge width: the lens zone is the slab's.
        GlassRefraction.Look faint = new GlassRefraction.Look(24, 16, 10, 0, 0, 0);
        GlassRefraction.Look loud = new GlassRefraction.Look(24, 16, 90, 80, 0, 0);
        GlassRefraction.Look wide = new GlassRefraction.Look(24, 40, 10, 0, 0, 0);
        assertEquals(faint.thicknessPx(DENSITY), loud.thicknessPx(DENSITY), 0f);
        assertEquals(faint.thicknessPx(DENSITY), wide.thicknessPx(DENSITY), 0f);
        assertEquals(faint.dispersion(), loud.dispersion(), 0f);
    }

    @Test
    public void theEasedPullIsADpAndNeverUnderTwoPixels() {
        assertEquals(DENSITY, GlassRefraction.Look.rimWidthPx(DENSITY), 1e-4f);
        assertEquals("never under two pixels", 2f, GlassRefraction.Look.rimWidthPx(1f), 1e-4f);
        assertEquals(2f, GlassRefraction.Look.rimWidthPx(0.4f), 1e-4f);
        assertEquals(1f, GlassRefraction.Look.RIM_WIDTH_DP, 0f);
        assertEquals(2f, GlassRefraction.Look.MIN_RIM_WIDTH_PX, 0f);
    }

    @Test
    public void thePressedKeyGlowIsItsOwnConstant() {
        assertTrue(GlassRefraction.AGSL.contains("const float kKeyGlow = 0.15;"));
        assertEquals(0.15f, GlassRefraction.KEY_GLOW, 0f);
        // Additive and unmultiplied, so it shows over a transparent key cell.
        assertTrue(GlassRefraction.AGSL.contains(
            "col.rgb = col.rgb + half3(lensGlow * kKeyGlow + 0.045 * touchG * touchG);"));
    }

    @Test
    public void aSeamClearsTheGlowOnANarrowBand() {
        // Solid's 1 dp edge ends well inside the light's reach; the seam is pushed past the light.
        GlassRefraction.Look narrow = new GlassRefraction.Look(0, 1, 100);
        assertTrue(GlassRefraction.seamReachPx(narrow, DENSITY, 0f) > GlassRefraction.Look.rimReachPx(DENSITY));
        assertTrue(GlassRefraction.seamReachPx(narrow, 1f, 0f) > GlassRefraction.Look.rimReachPx(1f));
        assertTrue(GlassRefraction.seamReachPx(narrow, DENSITY, 0f)
            > GlassRim.DEFAULT.reachPx(DENSITY));
    }

    @Test
    public void aZoneNeverCollapsesToNothing() {
        // The shader divides by the zone; a zero would push every pixel to the outline.
        assertTrue(GlassRefraction.AGSL.contains("        e = clamp(inside / max(wr, 0.001), 0.0, 1.0);\n"));
        assertTrue(GlassRefraction.AGSL.contains("    float lensMax = max(kLensDp * uDensity, 0.001);\n"));
        assertEquals(0f, GlassSlab.parse("lens=0").shiftPx(0f, 0f, 9f), 0f);
    }

    @Test
    public void aLookIsAValue() {
        GlassRefraction.Look one = new GlassRefraction.Look(9, 20, 18);
        assertEquals(one, GlassRefraction.Look.DEFAULT);
        assertEquals(one.hashCode(), GlassRefraction.Look.DEFAULT.hashCode());
        assertNotEquals(one, new GlassRefraction.Look(10, 20, 18));
        assertNotEquals(one, new GlassRefraction.Look(9, 21, 18));
        assertNotEquals(one, new GlassRefraction.Look(9, 20, 19));
        assertNotEquals(one, new GlassRefraction.Look(9, 20, 18, 0, 0, 1));
    }

    @Test
    public void theRimRunsAlongTheBoundsUnlessAnEdgeIsASeam() {
        Rect bounds = new Rect(0, 0, 1080, 300);
        float[] rim = new float[4];
        GlassRefraction.rimRect(rim, bounds, 100f, 0);
        assertEquals(0f, rim[0], 0f);
        assertEquals(0f, rim[1], 0f);
        assertEquals(1080f, rim[2], 0f);
        assertEquals(300f, rim[3], 0f);

        // The keyboard host over the under-pill strip: its bottom edge is interior glass.
        GlassRefraction.rimRect(rim, bounds, 100f, GlassRefraction.SEAM_BOTTOM);
        assertEquals(0f, rim[1], 0f);
        assertEquals(400f, rim[3], 0f);

        // The window bar under the status band: its top edge is.
        GlassRefraction.rimRect(rim, bounds, 100f, GlassRefraction.SEAM_TOP);
        assertEquals(-100f, rim[1], 0f);
        assertEquals(300f, rim[3], 0f);

        GlassRefraction.rimRect(rim, bounds, 40f,
            GlassRefraction.SEAM_LEFT | GlassRefraction.SEAM_RIGHT);
        assertEquals(-40f, rim[0], 0f);
        assertEquals(1120f, rim[2], 0f);
    }

    @Test
    public void aFractionalRimIsTheSameRuleAsTheBoundsOne() {
        // A corner tab mid-slide stands at fractional bounds; its rim follows the one rule.
        float[] fromRect = new float[4];
        float[] fromFloats = new float[4];
        int seams = GlassRefraction.SEAM_TOP | GlassRefraction.SEAM_RIGHT;
        GlassRefraction.rimRect(fromRect, new Rect(10, 20, 110, 52), 30f, seams);
        GlassRefraction.rimRect(fromFloats, 10f, 20f, 110f, 52f, 30f, seams);
        for (int i = 0; i < 4; i++) assertEquals(fromRect[i], fromFloats[i], 0f);

        GlassRefraction.rimRect(fromFloats, 10.5f, -12.25f, 110.5f, 19.75f, 30f, seams);
        assertEquals(10.5f, fromFloats[0], 0f);
        assertEquals(-42.25f, fromFloats[1], 0f);
        assertEquals(140.5f, fromFloats[2], 0f);
        assertEquals(19.75f, fromFloats[3], 0f);
    }

    // --------------------------------------------- the gathered light (glass plan 2026-10-16, E-1)

    private static void assertConst(String name, float java) {
        java.util.regex.Matcher m = Pattern.compile("const float " + name + " = ([0-9.]+);")
            .matcher(GlassRefraction.AGSL);
        assertTrue(name + " is a constant", m.find());
        assertEquals(name, java, Float.parseFloat(m.group(1)), 0f);
    }

    @Test
    public void aSeamIsPushedPastTheGlow() {
        GlassRefraction.Look[] looks = {GlassRefraction.Look.DEFAULT,
            new GlassRefraction.Look(27, 18, 10, 0, 40, 76), new GlassRefraction.Look(0, 1, 100),
            new GlassRefraction.Look(6, 4, 10)};
        for (GlassRefraction.Look look : looks) {
            for (float radius : new float[] {0f, 60f}) {
                float reach = GlassRefraction.seamReachPx(look, DENSITY, radius);
                assertTrue(look + " at " + radius, reach >= GlassRefraction.Look.rimReachPx(DENSITY)
                    + radius + 2f * DENSITY - 1e-3f);
            }
        }
        // The rim's reach is the line's or the shelf lip's, at every density, and never short of
        // the tilted face's ease.
        for (float density : new float[] {0.4f, 1f, DENSITY, 4f}) {
            assertEquals(Math.max(Math.max(GlassRefraction.Look.rimWidthPx(density),
                    GlassRim.DEFAULT.reachPx(density)),
                    GlassRefraction.shelfLipReachDp(GlassRim.DEFAULT) * density),
                GlassRefraction.Look.rimReachPx(density), 1e-4f);
            assertTrue(GlassRefraction.Look.rimReachPx(density) >= GlassRim.DEFAULT.reachPx(density));
            assertTrue(GlassRefraction.Look.rimReachPx(density)
                >= GlassRefraction.Look.rimWidthPx(density));
        }
    }

    @Test
    public void theShippedLooksSeamsClearTheWholeLensZone() {
        // Every look's seam sits past the largest lens zone any surface takes (a whole shelf's),
        // the corner and a little to spare; the Depth no longer moves it, as nothing bends past
        // the zone.
        GlassSlab slab = GlassSlab.DEFAULT;
        GlassRefraction.Look[] shipped = {GlassRefraction.Look.DEFAULT,
            new GlassRefraction.Look(27, 18, 10), new GlassRefraction.Look(24, 16, 10),
            new GlassRefraction.Look(21, 14, 10)};
        for (GlassRefraction.Look look : shipped) {
            float zone = slab.lensPx(DENSITY) * (1f + GlassRefraction.SHELF_THICKEN);
            assertEquals(look.toString(), zone + 40f + 2f * DENSITY,
                GlassRefraction.seamReachPx(look, DENSITY, 40f), 1e-3f);
        }
        // Clear's size lens grows the zone, and the seam with it.
        GlassRefraction.Look clear = new GlassRefraction.Look(27, 18, 10).withFaceTerms(100, 100, 100);
        assertEquals(slab.lensPx(DENSITY) * GlassRefraction.Look.SIZE_MAX * (1f + GlassRefraction.SHELF_THICKEN)
            + 2f * DENSITY, GlassRefraction.seamReachPx(clear, DENSITY, 0f), 1e-3f);
    }

    @Test
    public void aSeamIsPushedPastEverythingTheRimCouldTouch() {
        // Past the lens zone and the corner arc, so neither the light nor the bend nor a rounded
        // corner shows along the shared edge.
        GlassSlab slab = GlassSlab.DEFAULT;
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        float reach = GlassRefraction.seamReachPx(look, DENSITY, 60f);
        assertTrue(reach > slab.lensPx(DENSITY) + 60f);
        float square = GlassRefraction.seamReachPx(look, DENSITY, 0f);
        assertTrue("a square seam still clears the zone", square > slab.lensPx(DENSITY));
        assertEquals("nothing bends there", 0f, slab.shiftPx(square, slab.lensPx(DENSITY),
            look.thicknessPx(DENSITY)), 0f);
    }

    // ------------------------------------------------------------- the edge follows the cover

    // ------------------------------- dispersion on the gathered light (glass tuning 2026-10-17)

    private static String f3(float[] c) {
        return c[0] + ", " + c[1] + ", " + c[2];
    }

    // ---------------------------------------------------------- the Edge slider (2026-10-17)

    private static GlassRefraction.Look clearAt(int edge) {
        return new GlassRefraction.Look(27, 18, 10, 0, edge, 76);
    }

    private static GlassRefraction.Look mistAt(int edge) {
        return new GlassRefraction.Look(24, 16, 10, 0, edge, 0);
    }

    /** The four sides' outward normals and the top-left corner's. */
    private static final float D2 = (float) Math.sqrt(0.5);
    private static final float[][] SIDES = {{0f, -1f}, {1f, 0f}, {0f, 1f}, {-1f, 0f}, {-D2, -D2}};

    /** Backdrops the edge is checked over: greys, a warm brown (pong's cat), a yellow, a sky. */
    private static final float[][] BACKDROPS = {{0.02f, 0.02f, 0.02f}, {0.05f, 0.05f, 0.05f},
        {0.3f, 0.3f, 0.3f}, {0.8f, 0.8f, 0.8f}, {1f, 1f, 1f}, {0.45f, 0.35f, 0.25f},
        {0.9f, 0.8f, 0.2f}, {0.3f, 0.6f, 0.95f}};

    private static float luma(float[] c) {
        return c[0] * GlassRefraction.LUMA_R + c[1] * GlassRefraction.LUMA_G
            + c[2] * GlassRefraction.LUMA_B;
    }

    /** The hue of an opaque colour, in degrees, 0..360. */
    private static float hue(float[] c) {
        float max = Math.max(c[0], Math.max(c[1], c[2]));
        float min = Math.min(c[0], Math.min(c[1], c[2]));
        float d = max - min;
        if (d <= 0f) return 0f;
        float h;
        if (max == c[0]) h = (c[1] - c[2]) / d;
        else if (max == c[1]) h = 2f + (c[2] - c[0]) / d;
        else h = 4f + (c[0] - c[1]) / d;
        h *= 60f;
        return h < 0f ? h + 360f : h;
    }

    private static float[] rgb(int color) {
        return new float[] {((color >> 16) & 0xff) / 255f, ((color >> 8) & 0xff) / 255f,
            (color & 0xff) / 255f};
    }

    // ------------------------------------------------------------ the dock surface's glass

    @Test
    public void theDockSurfaceTermsAreGatedSoEveryOtherSurfaceIsUnchanged() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("    if (uSoftRim > 0.0) {\n"));
        assertTrue(agsl.contains("    if (uShelf > 0.0 && n.y > 0.0 && inside < kShelfLipReachDp * uDensity) {\n"));
        // The slab's own zone is the first word; the soft band widens it only behind its gate.
        int zone = agsl.indexOf("    float w = lensMax * sizeShare * sizeK;\n");
        int soft = agsl.indexOf("if (uSoftRim > 0.0) {");
        assertTrue(zone > 0 && soft > zone);
        assertTrue(agsl.contains(
            "        w = beads ? min(max(sSoft, w), kBeadBandShare * cr) : max(w, sSoft);\n"));
    }


    @Test
    public void theSlabFoldsThePictureOnlyUnderTheRimAtTheDefaultDepth() {
        // The sample's depth is inside + shift. A thick slab's edge stands up and mirrors what is
        // just inside it across its outermost px; at the default Depth that fold ends inside the
        // rim line's reach, which is drawn over it, on the narrow zone and the dock's soft band
        // alike and under the whole shelf. From there in, walking in by a px never walks the
        // sample back. A deeper Depth widens the mirrored band (a dramatic look, tuned on pong).
        GlassSlab slab = GlassSlab.DEFAULT;
        float softPx = GlassRefraction.SOFT_RIM_DP * DENSITY;
        float half = 200f * DENSITY;
        float rimReach = GlassRefraction.Look.rimReachPx(DENSITY);
        for (float soft : new float[] {0f, softPx}) {
            for (float lower : new float[] {0f, 0.5f, 1f}) {
                float w = GlassRefraction.lensZonePx(slab, half, DENSITY, 1f, soft, 0f, lower);
                for (int depthDp : new int[] {0, 9}) {
                    float t = GlassRefraction.lensThicknessPx(slab, depthDp * DENSITY, half, DENSITY, 1f, lower);
                    float previous = -1f;
                    for (float in = rimReach; in < 2f * w; in += 0.25f) {
                        float depth = in + slab.shiftPx(in, w, t);
                        assertTrue(depthDp + "dp soft " + soft + " lower " + lower + " at " + in,
                            depth > previous);
                        previous = depth;
                    }
                }
            }
        }
    }

    @Test
    public void theSoftBandReachesItsWidthAndTheShelfThickensTheLowerEdge() {
        GlassSlab slab = GlassSlab.DEFAULT;
        float softPx = GlassRefraction.SOFT_RIM_DP * DENSITY;
        float half = 200f * DENSITY;
        float depth = 9f * DENSITY;
        float w = GlassRefraction.lensZonePx(slab, half, DENSITY, 1f, softPx, 0f, 0f);
        float t = GlassRefraction.lensThicknessPx(slab, depth, half, DENSITY, 1f, 0f);
        assertEquals(softPx, w, 1e-3f);
        assertTrue(slab.shiftPx(softPx * 0.5f, w, t) > 0f);
        assertEquals(0f, slab.shiftPx(softPx, w, t), 0f);
        // The lower edge is the thicker one under the shelf.
        float wl = GlassRefraction.lensZonePx(slab, half, DENSITY, 1f, softPx, 0f, 1f);
        float tl = GlassRefraction.lensThicknessPx(slab, depth, half, DENSITY, 1f, 1f);
        assertTrue(slab.shiftPx(softPx * 0.6f, wl, tl) > slab.shiftPx(softPx * 0.6f, w, t));
    }

    @Test
    public void theLipLightsOnlyTheLowerEdge() {
        assertEquals(0f, GlassRefraction.shelfLipK(-1f, 0f, DENSITY, 1f), 0f);
        assertEquals(0f, GlassRefraction.shelfLipK(0f, 0f, DENSITY, 1f), 0f);
        assertEquals(0f, GlassRefraction.shelfLipK(1f, 0f, DENSITY, 0f), 0f);
        float lip = GlassRefraction.shelfLipK(1f, 0f, DENSITY, 1f);
        assertEquals(GlassRefraction.SHELF_LIP, lip, 1e-6f);
        assertTrue(GlassRefraction.shelfLipK(1f, 3f * DENSITY, DENSITY, 1f) < lip * 0.5f);
        // Nothing past its reach is worth an 8-bit step of the rim's white.
        float reach = GlassRefraction.shelfLipReachDp(GlassRim.DEFAULT) * DENSITY;
        assertTrue(GlassRefraction.shelfLipK(1f, reach, DENSITY, 1f) * GlassRim.DEFAULT.spec
            <= 1f / 255f + 1e-5f);
    }

    @Test
    public void theProgramHoldsNoIconReflection() {
        assertFalse(GlassRefraction.AGSL.contains("reflection"));
        assertFalse(GlassRefraction.AGSL.contains("uReflect"));
    }

    @Test
    public void theProgramNeverDrawsARadiusPastHalfTheRect() {
        // A one-row band 40 px tall handed the dock's 56 px Corners before its height was known:
        // the program draws it as the capsule its clip is, not with ends folding in.
        assertEquals(20f, GlassRefraction.rectRadiusPx(0f, 0f, 300f, 40f, 56f), 0.001f);
        assertEquals(12f, GlassRefraction.rectRadiusPx(0f, 0f, 300f, 40f, 12f), 0.001f);
        // A column is held by its width.
        assertEquals(15f, GlassRefraction.rectRadiusPx(10f, 0f, 40f, 400f, 99f), 0.001f);
        // A seam pushes the rect out, and the hold is the pushed rect's, as the program sees it.
        float[] rim = new float[4];
        GlassRefraction.rimRect(rim, new Rect(0, 0, 300, 40), 30f, GlassRefraction.SEAM_TOP);
        assertEquals(35f, GlassRefraction.rectRadiusPx(rim[0], rim[1], rim[2], rim[3], 56f), 0.001f);
        assertEquals(0f, GlassRefraction.rectRadiusPx(0f, 0f, 300f, 40f, -5f), 0.001f);
        assertEquals(0f, GlassRefraction.rectRadiusPx(0f, 0f, 0f, 0f, 12f), 0.001f);
    }

    // ---------------------------------------------------------------- Clear's face terms

    @Test
    public void theFaceTermsAreInTheLooksEqualityAndHashAndDefaultToNone() {
        GlassRefraction.Look plain = new GlassRefraction.Look(27, 18, 10, 0, 40, 76);
        assertEquals(0, plain.faceClarityPercent);
        assertEquals(0, plain.dimPercent);
        assertEquals(0, plain.sizeLensPercent);
        assertEquals(0f, plain.faceClarity(), 0f);
        assertEquals(0f, plain.dim(), 0f);
        assertEquals(0f, plain.sizeLens(), 0f);
        assertEquals(plain, plain.withFaceTerms(0, 0, 0));
        GlassRefraction.Look clear = plain.withFaceTerms(100, 100, 100);
        assertNotEquals(plain, clear);
        assertNotEquals(plain.hashCode(), clear.hashCode());
        assertEquals(clear, new GlassRefraction.Look(27, 18, 10, 0, 40, 76).withFaceTerms(100, 100, 100));
        assertEquals(clear.hashCode(),
            new GlassRefraction.Look(27, 18, 10, 0, 40, 76).withFaceTerms(100, 100, 100).hashCode());
        assertNotEquals(clear, plain.withFaceTerms(0, 100, 100));
        assertNotEquals(clear, plain.withFaceTerms(100, 0, 100));
        assertNotEquals(clear, plain.withFaceTerms(100, 100, 0));
        // Each term alone moves the hash.
        assertNotEquals(plain.withFaceTerms(0, 100, 0).hashCode(), plain.withFaceTerms(0, 0, 100).hashCode());
        assertEquals(100, clear.dimPercent);
        assertEquals(1f, clear.faceClarity(), 0f);
        // Clamped to a percentage.
        assertEquals(100, plain.withFaceTerms(400, -3, 7).faceClarityPercent);
        assertEquals(0, plain.withFaceTerms(400, -3, 7).dimPercent);
    }

    @Test
    public void theSizeLensScalesBySideAndIsHeldToItsRange() {
        // 72 dp short side scales nothing; a dock is about that.
        assertEquals(1f, GlassRefraction.sizeLensK(72f * DENSITY, DENSITY, 1f), 1e-6f);
        // Smaller surfaces take less, to three quarters; larger take more, to one and a half.
        assertEquals(0.75f, GlassRefraction.sizeLensK(24f * DENSITY, DENSITY, 1f), 1e-6f);
        assertEquals(0.75f, GlassRefraction.sizeLensK(0f, DENSITY, 1f), 1e-6f);
        assertEquals(1.5f, GlassRefraction.sizeLensK(400f * DENSITY, DENSITY, 1f), 1e-6f);
        assertEquals(1.25f, GlassRefraction.sizeLensK(90f * DENSITY, DENSITY, 1f), 1e-6f);
        // Off it is exactly 1, whatever the size.
        assertEquals(1f, GlassRefraction.sizeLensK(400f * DENSITY, DENSITY, 0f), 0f);
        assertEquals(1f, GlassRefraction.sizeLensK(10f, DENSITY, 0f), 0f);
        // Half on is half way.
        assertEquals(1.25f, GlassRefraction.sizeLensK(400f * DENSITY, DENSITY, 0.5f), 1e-6f);
    }

    @Test
    public void theFaceFadesOutWithSizeSoLargePanesKeepTheirFrost() {
        // Full at 200 dp of short side or under, none at 320 or over, smoothstep between.
        assertEquals(1f, GlassRefraction.faceFade(40f * DENSITY, DENSITY), 0f);
        assertEquals(1f, GlassRefraction.faceFade(200f * DENSITY, DENSITY), 1e-6f);
        assertEquals(0.5f, GlassRefraction.faceFade(260f * DENSITY, DENSITY), 1e-6f);
        assertEquals(0f, GlassRefraction.faceFade(320f * DENSITY, DENSITY), 1e-6f);
        // The terminal pane, about 396 dp, keeps its frost; a widget does not.
        assertEquals(0f, GlassRefraction.faceFade(396f * DENSITY, DENSITY), 0f);
        assertEquals(1f, GlassRefraction.faceFade(150f * DENSITY, DENSITY), 0f);
        float last = 1f;
        for (float dp = 150f; dp <= 400f; dp += 10f) {
            float f = GlassRefraction.faceFade(dp * DENSITY, DENSITY);
            assertTrue("falls with size at " + dp, f <= last + 1e-6f);
            last = f;
        }
    }

    @Test
    public void theFaceToneDimsBrightAndLiftsDarkAndIsNothingAtZero() {
        float[] tone = new float[2];
        GlassRefraction.faceTone(1f, 1f, tone);
        assertEquals(0.35f, tone[0], 1e-6f);
        assertEquals(0f, tone[1], 1e-6f);
        GlassRefraction.faceTone(0.3f, 1f, tone);
        assertEquals(0f, tone[0], 0f);
        assertEquals(0.06f * 0.7f, tone[1], 1e-6f);
        GlassRefraction.faceTone(0f, 1f, tone);
        assertEquals(0.06f, tone[1], 1e-6f);
        GlassRefraction.faceTone(0.675f, 1f, tone);
        assertEquals(0.35f * 0.5f, tone[0], 1e-6f);
        GlassRefraction.faceTone(0.8f, 0f, tone);
        assertEquals(0f, tone[0], 0f);
        assertEquals(0f, tone[1], 0f);
    }

    @Test
    public void theProgramTakesTheFaceTermsOnlyBehindTheirGates() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("uniform float uFaceClarity;"));
        assertTrue(agsl.contains("uniform float uDim;"));
        assertTrue(agsl.contains("uniform float uSizeLens;"));
        assertTrue(agsl.contains("    if (faceMix > 0.0) {\n"));
        assertTrue(agsl.contains("    if (dimK > 0.0) {\n"));
        assertTrue(agsl.contains("float faceMix = uFaceClarity * faceFade;"));
        assertTrue(agsl.contains("float dimK = uDim * faceFade;"));
        assertEquals(GlassRefraction.Look.FACE_FADE_TO_DP + "",
            agsl.split("const float kFadeToDp = ")[1].split(";")[0]);
        assertTrue(agsl.contains("float sizeK = uSizeLens > 0.0\n"));
        assertTrue(agsl.contains(": 1.0;\n"));
        // The tone comes after the picture is chosen and before the light.
        int mix = agsl.indexOf("col = mix(col, sharp.eval(f), half(clarityK));");
        int tone = agsl.indexOf("if (dimK > 0.0) {");
        int light = agsl.indexOf("if ((inside < lineReach || shelfW > 0.0 || slope > 0.0 || turned) && col.a > 0.0) {");
        assertTrue(mix > 0 && tone > mix && light > tone);
        assertEquals(GlassRefraction.Look.DIM_MAX + "",
            agsl.split("const float kDimMax = ")[1].split(";")[0]);
        assertEquals(GlassRefraction.Look.SIZE_MAX + "",
            agsl.split("const float kSizeMax = ")[1].split(";")[0]);
        // No raw uDepth or uSoftRim in the lens's arithmetic: it takes the scaled ones.
        assertTrue(agsl.contains("uDepth * sizeK"));
        assertTrue(agsl.contains("float sSoft = uSoftRim * sizeK;"));
    }

    // --------------------------------------------------------------- the rim (#314, GlassRim)

    @Test
    public void theRimIsTheOnlyEdgeAndItsConstantsAreGlassRims() {
        String agsl = GlassRefraction.AGSL;
        GlassRim rim = GlassRim.DEFAULT;
        assertConst("kRimLineDp", rim.lineDp);
        assertConst("kRimSoftDp", rim.softDp);
        assertConst("kRimHalo", rim.halo);
        assertConst("kRimHaloDp", rim.haloDp);
        assertConst("kRimHaloSpan", rim.haloSpan());
        assertConst("kRimBase", rim.base);
        assertConst("kRimSpec", rim.spec);
        assertConst("kRimDark", rim.dark);
        assertConst("kRimDarkAll", rim.darkAll);
        assertConst("kRimMinLinePx", GlassRim.MIN_LINE_PX);
        assertConst("kRimMinSoftPx", GlassRim.MIN_SOFT_PX);
        assertConst("kShelfLipReachDp", GlassRefraction.shelfLipReachDp(rim));
        assertConst("kKeyExp", GlassRefraction.Look.KEY_EXPONENT);
        assertConst("kTouchRim", GlassRefraction.TOUCH_RIM);
        assertTrue(agsl.contains("const float2 kKeyLight = float2("
            + GlassRefraction.KEY_LIGHT_X + ", " + GlassRefraction.KEY_LIGHT_Y + ");"));
        float length = (float) Math.hypot(GlassRefraction.KEY_LIGHT_X, GlassRefraction.KEY_LIGHT_Y);
        assertEquals("the key light is a unit vector", 1f, length, 1e-3f);
        assertTrue("up and to the left", GlassRefraction.KEY_LIGHT_X < 0f && GlassRefraction.KEY_LIGHT_Y < 0f);
        // One rim block, its profile GlassRim.weight's, lit in linear light with no gain.
        String gate = "    if ((inside < lineReach || shelfW > 0.0 || slope > 0.0 || turned) && col.a > 0.0) {\n";
        assertTrue(agsl.contains(gate));
        assertEquals(agsl.indexOf(gate), agsl.lastIndexOf(gate));
        assertTrue(agsl.contains("lineW = min(1.0 - smoothstep(lineCore, lineCore + lineSoft, inside)\n"
            + "                + kRimHalo * exp(-max(inside - lineCore, 0.0) / lineHalo), 1.0);"));
        assertTrue(agsl.contains("float white = (kRimBase + kRimSpec * lobe) * lineW + kRimSpec * shelfW;"));
        assertTrue(agsl.contains(
            "float keep = 1.0 - kRimDark * (kRimDarkAll + (1.0 - kRimDarkAll) * away) * lineW;"));
        assertTrue(agsl.contains("float3 lin = float3(toLinearSrgb(half3(float3(col.rgb) / ca)));"));
        assertTrue(agsl.contains("col.rgb = half3(float3(fromLinearSrgb(half3(lin))) * ca);"));
        assertEquals("one linear round trip", agsl.indexOf("toLinearSrgb"), agsl.lastIndexOf("toLinearSrgb"));
        // The rim's light is fixed in the room: only a finger moves its lobe (#318 removed the
        // live glint, its key light and the tilt's lean).
        assertTrue(agsl.contains("            lobe = lobe + kTouchRim * touchG;\n"
            + "            float white = (kRimBase + kRimSpec * lobe) * lineW + kRimSpec * shelfW;"));
        for (String gone : new String[] {"uKeyLight", "glintW", "liveKeyLobe", "kTiltLight", "tiltLean"}) {
            assertFalse(gone, agsl.contains(gone));
        }
        // The twin's expression.
        assertTrue(agsl.contains("    return c > 0.0 ? pow(c, kKeyExp) : 0.0;\n"));
    }

    @Test
    public void aRimOverrideBuildsTheSameProgramWithItsNumbers() {
        GlassRim rim = GlassRim.parse("line=0.25,soft=0.0001,base=0.1");
        String agsl = GlassRefraction.agsl(rim, GlassSlab.DEFAULT);
        assertTrue(agsl.contains("const float kRimLineDp = 0.25;\n"));
        assertTrue("no exponent form", agsl.contains("const float kRimSoftDp = 0.0001;\n"));
        assertTrue(agsl.contains("const float kRimBase = 0.1;\n"));
        assertEquals(GlassRefraction.AGSL, GlassRefraction.agsl(GlassRim.DEFAULT, GlassSlab.DEFAULT));
        // Everything but the rim's constants is the shipped program.
        assertEquals(GlassRefraction.AGSL.replace(GlassRefraction.rimConstants(GlassRim.DEFAULT), ""),
            agsl.replace(GlassRefraction.rimConstants(rim), ""));
        assertEquals("1.0", GlassRefraction.agslFloat(1f));
        assertEquals("0.0", GlassRefraction.agslFloat(0f));
        assertEquals("0.75", GlassRefraction.agslFloat(0.75f));
    }

    @Test
    public void theRimsLobeFacesTheKeyLight() {
        float kx = GlassRefraction.KEY_LIGHT_X, ky = GlassRefraction.KEY_LIGHT_Y;
        assertEquals(1f, GlassRefraction.rimLobe(kx, ky), 1e-3f);
        assertEquals(0f, GlassRefraction.rimLobe(-kx, -ky), 0f);
        assertEquals(0f, GlassRefraction.rimAway(kx, ky), 0f);
        assertEquals(1f, GlassRefraction.rimAway(-kx, -ky), 1e-3f);
        float d = (float) Math.sqrt(0.5);
        assertTrue(GlassRefraction.rimLobe(-d, -d) > GlassRefraction.rimLobe(0f, -1f));
        assertTrue(GlassRefraction.rimLobe(0f, -1f) > GlassRefraction.rimLobe(-1f, 0f));
    }
}
