package com.termux.app.chrome;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import com.termux.app.DockGlassRendering;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The glass stack: the Floating keyboard capsule and the dock resolve to one material for the same
 * Base, and the builder lays the layers the same way for every surface.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassStackTest {

    private static final float RADIUS_PX = 42f;
    private static final GlassRefraction.Look FANCIER = new GlassRefraction.Look(9, 14, 60);

    private TermuxAppSharedPreferences preferences;
    private FakeChromeSurfaces surfaces;
    private GlassSurfaceFactory glass;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store = context.getSharedPreferences("glass-stack-test",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        preferences.migrateSurfaceInheritance();
        for (SurfaceSlot slot : SurfaceSlot.values())
            preferences.reattachSurface(slot);
        surfaces = new FakeChromeSurfaces(context);
        glass = new GlassSurfaceFactory(surfaces);
    }

    /** What the dock draws with the same Base: its own getters, its rim, its refraction rule. */
    private GlassStack.Spec dockSpec(GlassRefraction.Look fancier) {
        return GlassStack.Spec.of(preferences.getExtraKeysBlurRadius(),
            preferences.getAppBarOpacity() / 100f, preferences.getDockGlassGrain(), RADIUS_PX,
            fancier).withRim(true);
    }

    private GlassStack.Spec keyboardSpec(GlassRefraction.Look fancier) {
        return GlassStack.keyboard(preferences, preferences.getAppBarOpacity() / 100f, RADIUS_PX,
            fancier).withRim(true);
    }

    @Test
    public void theKeyboardCapsuleAndTheDockResolveToOneSpecForTheSameBase() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 61);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 43);

        for (GlassRefraction.Look fancier : new GlassRefraction.Look[] {null, FANCIER}) {
            GlassStack.Spec keyboard = keyboardSpec(fancier);
            GlassStack.Spec dock = dockSpec(fancier);
            assertEquals(17, keyboard.blurRadiusDp);
            assertEquals(0.61f, keyboard.tintAlpha, 0f);
            assertEquals(43, keyboard.grainPercent);
            assertEquals(RADIUS_PX, keyboard.cornerRadiusPx, 0f);
            assertTrue(keyboard.rim);
            assertEquals(100, keyboard.stackAlphaPercent);
            assertEquals("fancier " + fancier, dock, keyboard);
        }
    }

    @Test
    public void theDefaultModeRefractsWithTheDocksLookNotNone() {
        assertSame(GlassRefraction.Look.DEFAULT, GlassStack.lookFor(null));
        assertSame(GlassRefraction.Look.DEFAULT, keyboardSpec(null).look);
        assertSame(FANCIER, GlassStack.lookFor(FANCIER));
        assertSame(FANCIER, keyboardSpec(FANCIER).look);
    }

    @Test
    public void aDetachedKeyboardRowLeavesTheDockAlone() {
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.BLUR, 3);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN, 80);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY, 90);
        preferences.setInAppKeyboardBackdropOpacity(70);

        GlassStack.Spec keyboard = keyboardSpec(null);
        assertEquals(3, keyboard.blurRadiusDp);
        assertEquals(80, keyboard.grainPercent);
        assertEquals(0.9f, keyboard.tintAlpha, 0f);
        assertEquals("the whole-stack alpha is the keyboard's own row", 70,
            keyboard.stackAlphaPercent);
        assertNotEquals(dockSpec(null), keyboard);
    }

    @Test
    public void theStackIsFrameThenTintGrainAndOneRimUnderTheStackAlpha() {
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 30);
        Bitmap frame = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        SharedFrameDrawable backdrop = new SharedFrameDrawable(frame, new Rect(0, 0, 40, 40), null,
            out -> { out[0] = 0; out[1] = 0; });
        GlassStack.Spec spec = keyboardSpec(FANCIER).withStackAlpha(50);

        LayerDrawable stack = (LayerDrawable) GlassStack.build(glass, spec, 2f, backdrop);

        assertEquals(2, stack.getNumberOfLayers());
        assertSame(backdrop, stack.getDrawable(0));
        // The look as stored, raised by what the tint over it covers (its opacity, no Shade).
        assertEquals(FANCIER.withCover(spec.tintAlpha), backdrop.refractionLook());
        assertEquals(FANCIER, backdrop.refractionLook().withCover(0f));
        assertEquals(RADIUS_PX, backdrop.refractionRadiusPx(), 0f);
        LayerDrawable tint = (LayerDrawable) stack.getDrawable(1);
        // base, light, grain, rim: the one rim the factory draws.
        assertEquals(4, tint.getNumberOfLayers());
        GradientRimDrawable rim = (GradientRimDrawable) tint.getDrawable(3);
        assertEquals(RADIUS_PX, rim.radiusPx(), 0f);
        assertEquals(Math.round(255f * 50 / 100f), backdrop.getAlpha());
    }

    @Test
    public void theRimIsDecidedPerStackByWhetherItsOwnBackdropDrawsTheEdge() {
        // The gathered light is the edge wherever the program draws the glass; a stack whose own
        // backdrop is plain, or that has none, keeps its rim.
        assertFalse("its backdrop runs the program", GlassStack.wearsRim(true, true));
        assertTrue("its backdrop is plain", GlassStack.wearsRim(true, false));
        assertFalse("the spec asked for none", GlassStack.wearsRim(false, false));
        assertFalse(GlassStack.wearsRim(false, true));
    }

    @Test
    public void aStackWhoseBackdropDrawsPlainKeepsItsRimWhateverTheLook() {
        // Below Android 13 (this runner) the frame never runs the program, so a stack keeps its
        // rim even while Fancier Glass answers a look: the gate is the backdrop, not the look.
        surfaces.look = FANCIER;
        Bitmap frame = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        SharedFrameDrawable backdrop = new SharedFrameDrawable(frame, new Rect(0, 0, 40, 40), null,
            out -> { out[0] = 0; out[1] = 0; });

        Drawable stack = GlassStack.build(glass, keyboardSpec(FANCIER), 2f, backdrop);

        assertFalse(backdrop.refracts());
        assertEquals(1, rimsIn(stack));
        assertEquals("the look itself is what is stored, with the tint's cover", FANCIER,
            backdrop.refractionLook().withCover(0f));
        // A stack with no backdrop at all, the scheme-colour one, the same way.
        assertEquals(1, rimsIn(GlassStack.build(glass,
            keyboardSpec(null).withTintColor(0x80112233), 2f, null)));
    }

    @Test
    public void aStackWhoseBackdropRefractsLaysNoRim() {
        // The JVM runs no program, so a refracting frame is stood in through the probe: what
        // build() does with the answer is what is pinned here.
        Bitmap frame = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        SharedFrameDrawable backdrop = new SharedFrameDrawable(frame, new Rect(0, 0, 40, 40), null,
            out -> { out[0] = 0; out[1] = 0; });
        boolean[] asked = {false};

        Drawable stack = GlassStack.build(glass, keyboardSpec(FANCIER), 2f, backdrop, b -> {
            asked[0] = true;
            assertEquals("asked after the look is handed over", FANCIER,
                b.refractionLook().withCover(0f));
            return true;
        });

        assertTrue(asked[0]);
        assertEquals("the gathered light is the edge; no rim over it", 0, rimsIn(stack));
        // The same spec over a frame drawn plain keeps its rim, and the tint stack is otherwise
        // the same: only the rim layer differs.
        Drawable plain = GlassStack.build(glass, keyboardSpec(FANCIER), 2f, backdrop, b -> false);
        assertEquals(1, rimsIn(plain));
        assertEquals(layerCount(plain) - 1, layerCount(stack));
        // A scheme-colour stack the same way.
        assertEquals(0, rimsIn(GlassStack.build(glass,
            keyboardSpec(FANCIER).withTintColor(0x80112233), 2f, backdrop, b -> true)));
    }

    @Test
    public void theWindowBarOverAFrostThatDrawsTheEdgeLaysNoStrokeButKeepsItsCorner() {
        Drawable stroked = glass.statusBarSurface(0.4f, 0f, 1f, ChromeEdgeRule.ALL, null, false);
        Drawable bare = glass.statusBarSurface(0.4f, 0f, 1f, ChromeEdgeRule.ALL, null, true);
        assertEquals(1, rimsIn(stroked));
        assertEquals(0, rimsIn(bare));
        assertEquals(layerCount(stroked) - 1, layerCount(bare));
        // The five-argument form is the stroked one, as before.
        assertEquals(1, rimsIn(glass.statusBarSurface(0.4f, 0f, 1f, ChromeEdgeRule.ALL, null)));
    }

    /** How many leaf layers a stack holds, at any depth. */
    private static int layerCount(Drawable drawable) {
        if (!(drawable instanceof LayerDrawable)) return 1;
        LayerDrawable layers = (LayerDrawable) drawable;
        int count = 0;
        for (int i = 0; i < layers.getNumberOfLayers(); i++) count += layerCount(layers.getDrawable(i));
        return count;
    }

    /** How many {@link GradientRimDrawable} layers a stack holds, at any depth. */
    private static int rimsIn(Drawable drawable) {
        if (drawable instanceof GradientRimDrawable) return 1;
        if (!(drawable instanceof LayerDrawable)) return 0;
        LayerDrawable layers = (LayerDrawable) drawable;
        int rims = 0;
        for (int i = 0; i < layers.getNumberOfLayers(); i++) rims += rimsIn(layers.getDrawable(i));
        return rims;
    }

    @Test
    public void theStackAlphaScalesEachLayerRatherThanReplacingIt() {
        GradientDrawable tint = new GradientDrawable();
        GradientDrawable grain = new GradientDrawable();
        grain.setAlpha(40);
        LayerDrawable stack = new LayerDrawable(new android.graphics.drawable.Drawable[] {
            tint, new LayerDrawable(new android.graphics.drawable.Drawable[] {grain})});

        GlassStack.applyStackAlpha(stack, 128);

        assertEquals(128, tint.getAlpha());
        assertEquals("the faint grain stays faint", Math.round(40 * 128 / 255f), grain.getAlpha());
    }

    @Test
    public void anOpaqueStackAlphaLeavesTheGrainAtItsOwnAlpha() {
        GradientDrawable grain = new GradientDrawable();
        grain.setAlpha(40);
        GlassStack.applyStackAlpha(new LayerDrawable(
            new android.graphics.drawable.Drawable[] {grain}), 255);
        assertEquals(40, grain.getAlpha());
    }

    @Test
    public void aRimAlongSomeEdgesLeavesTheOthersOpen() {
        assertSame(null, glass.rimDrawable(0f, ChromeEdgeRule.NONE));
        assertTrue(glass.rimDrawable(0f, ChromeEdgeRule.ALL) instanceof GradientRimDrawable);
        OpenEdgeDrawable bottomOnly =
            (OpenEdgeDrawable) glass.rimDrawable(0f, ChromeEdgeRule.BOTTOM);
        assertEquals(ChromeEdgeRule.LEFT | ChromeEdgeRule.TOP | ChromeEdgeRule.RIGHT,
            bottomOnly.openEdges());
        bottomOnly.setBounds(0, 0, 100, 40);
        Rect rim = bottomOnly.rim().getBounds();
        assertTrue("carried off the top", rim.top < 0);
        assertTrue(rim.left < 0);
        assertTrue(rim.right > 100);
        assertEquals("the inner edge stays on the surface", 40, rim.bottom);
    }

    @Test
    public void withoutAFrameTheTintIsTheWholeStack() {
        GlassStack.Spec spec = keyboardSpec(null).withRim(false);

        LayerDrawable stack = (LayerDrawable) GlassStack.build(glass, spec, 2f, null);

        int grainLayers = spec.grainPercent > 0 ? 1 : 0;
        assertEquals(2 + grainLayers, stack.getNumberOfLayers());
        assertNotNull(stack.getDrawable(0));
    }

    @Test
    public void aSchemeColourStandsInForTheTintAndKeepsTheRim() {
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 0);
        GlassStack.Spec spec = keyboardSpec(null).withTintColor(0x80112233);

        LayerDrawable stack = (LayerDrawable) GlassStack.build(glass, spec, 2f, null);

        assertEquals(2, stack.getNumberOfLayers());
        assertTrue(stack.getDrawable(0) instanceof android.graphics.drawable.ColorDrawable);
        assertTrue(stack.getDrawable(1) instanceof GradientRimDrawable);
    }

    @Test
    public void aSchemeColourStackWearsTheSurfacesGrainBetweenTheTintAndTheRim() {
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 14);
        GlassStack.Spec spec = keyboardSpec(null).withTintColor(0x80112233);

        LayerDrawable stack = (LayerDrawable) GlassStack.build(glass, spec, 2f, null);

        assertEquals(3, stack.getNumberOfLayers());
        assertTrue(stack.getDrawable(0) instanceof android.graphics.drawable.ColorDrawable);
        assertTrue("grain is a tiled bitmap",
            stack.getDrawable(1) instanceof android.graphics.drawable.BitmapDrawable);
        assertEquals(DockGlassRendering.grainAlpha(14), stack.getDrawable(1).getAlpha());
        assertTrue(stack.getDrawable(2) instanceof GradientRimDrawable);
    }

    @Test
    public void overGlassTheRimIsTheDocksVisibleStrengthAndUnderItTheFullGradient() {
        // The dock's rim sits under its blurred backdrop, which lets about 0.4 of it through; a rim
        // drawn over glass is matched to that, under every look.
        assertEquals(GlassTokens.RIM_OVER_GLASS_START,
            ((GradientRimDrawable) glass.rimDrawable(8f)).startColor());
        assertEquals(GlassTokens.RIM_START,
            ((GradientRimDrawable) glass.rimUnderGlass(8f)).startColor());
    }

    @Test
    public void whileTheKeyboardFollowsBaseAStaleBackdropOpacityDoesNotFadeIt() {
        preferences.setInAppKeyboardBackdropOpacityRaw(35);

        assertEquals(100, GlassStack.keyboardStackAlphaPercent(preferences));
        assertEquals("inheriting, the keyboard is the dock's spec", dockSpec(FANCIER),
            keyboardSpec(FANCIER));

        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY, 60);
        assertEquals("detached, its own Opacity row applies", 35,
            GlassStack.keyboardStackAlphaPercent(preferences));
    }

    @Test
    public void underMistTheKeyboardStackWearsTheDocksTintAndRim() {
        GlassSurfaceFactory mist = glass.withLook(new GlassLook(true, true));
        preferences.setInAppKeyboardBackdropOpacityRaw(35);

        LayerDrawable dock = (LayerDrawable) GlassStack.build(mist, dockSpec(null), 2f, null);
        LayerDrawable keyboard = (LayerDrawable) GlassStack.build(mist, keyboardSpec(null), 2f, null);

        assertEquals(dock.getNumberOfLayers(), keyboard.getNumberOfLayers());
        int last = dock.getNumberOfLayers() - 1;
        assertTrue(dock.getDrawable(last) instanceof GradientRimDrawable);
        assertTrue(keyboard.getDrawable(last) instanceof GradientRimDrawable);
        assertEquals(((GradientDrawable) dock.getDrawable(0)).getColor().getDefaultColor(),
            ((GradientDrawable) keyboard.getDrawable(0)).getColor().getDefaultColor());
        assertEquals(255, keyboard.getAlpha());
    }

    @Test
    public void theKeyboardSpecWearsItsOwnTintStrength() {
        assertEquals("no accent ships", 0, keyboardSpec(null).tintStrengthPercent);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 60);
        assertEquals(60, keyboardSpec(null).tintStrengthPercent);

        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, 25);
        assertEquals(25, keyboardSpec(null).tintStrengthPercent);
        assertEquals("the dock keeps following Base", 60, preferences.getDockTintStrength());
        assertNotEquals(keyboardSpec(null).withTintStrength(60), keyboardSpec(null));
        assertEquals("the plain spec mixes in no accent", 0, dockSpec(null).tintStrengthPercent);
    }
}
