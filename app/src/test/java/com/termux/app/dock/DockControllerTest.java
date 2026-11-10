package com.termux.app.dock;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.robolectric.RuntimeEnvironment;

import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.place.PlaceLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The dock's controller driven through a fake {@link DockController.Host}: which beads it wants,
 * shadows and draws for the crossing's bead share and the design shown.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DockControllerTest {

    private FakeHost mHost;
    private DockController mDock;

    @Before
    public void setUp() {
        mHost = new FakeHost();
        mDock = new DockController(mHost);
    }

    @Test
    public void beadsAreWantedOnlyWithPreferencesAndAShareOfTheBeadedHalf() {
        mHost.prefs = false;
        mHost.spec = DockSurfaceSpec.LENS_BEADS;
        assertFalse("no preferences, no beads", mDock.dockBeadsWanted());

        mHost.prefs = true;
        mHost.share = Float.NaN;
        assertTrue("Lens beads at rest", mDock.dockBeadsWanted());
        mHost.share = 0.4f;
        assertTrue("Lens beads while some of their half shows", mDock.dockBeadsWanted());
        mHost.share = 0f;
        assertFalse("Lens beads carried wholly off", mDock.dockBeadsWanted());

        mHost.spec = DockSurfaceSpec.GLASS_TRAY;
        mHost.share = Float.NaN;
        assertFalse("a slab at rest", mDock.dockBeadsWanted());
        mHost.share = 0.3f;
        assertTrue("a slab across the seam from the beaded half", mDock.dockBeadsWanted());
        mHost.share = 0f;
        assertFalse("a slab with none of the beaded half showing", mDock.dockBeadsWanted());
    }

    @Test
    public void theShadowsFollowTheMovingBeadsOnlyWhileThereAreBeads() {
        LensBeadGeometry.Beads rest = sevenBeads();
        LensBeadGeometry.Beads moving = rest.offset(-40f, 0f);
        assertNull(mDock.dockBeadsShadowed());

        ReflectionHelpers.setField(mDock, "mDockBeadsMoving", moving);
        assertNull("moving beads with no beads at rest shadow nothing", mDock.dockBeadsShadowed());

        ReflectionHelpers.setField(mDock, "mDockBeads", rest);
        assertSame(moving, mDock.dockBeadsShadowed());

        ReflectionHelpers.setField(mDock, "mDockBeadsMoving", null);
        assertSame(rest, mDock.beads());
        assertSame(rest, mDock.dockBeadsShadowed());
    }

    @Test
    public void theGlassDrawsTheShadowedBeadsUnlessAMeltSwellsThem() {
        LensBeadGeometry.Beads rest = sevenBeads();
        ReflectionHelpers.setField(mDock, "mDockBeads", rest);

        mHost.share = Float.NaN;
        assertSame("at rest the glass draws the beads themselves", rest, mDock.dockBeadsDrawn());
        mHost.share = 1f;
        assertSame("all of the beaded half: no melt", rest, mDock.dockBeadsDrawn());

        mHost.share = 0.5f;
        mHost.surfaceHost = null;
        assertSame("no host to measure the cover: unswollen", rest, mDock.dockBeadsDrawn());

        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        host.layout(0, 0, 1080, 200);
        mHost.surfaceHost = host;
        LensBeadGeometry.Beads drawn = mDock.dockBeadsDrawn();
        assertNotNull(drawn);
        float cover = LensBeadGeometry.coverRadiusPx(rest, 1080f, 200f);
        assertEquals(LensBeadGeometry.meltRadiusPx(rest.radiusPx, cover, 0.5f), drawn.radiusPx,
            0.001f);
        assertTrue("a melt swells the beads", drawn.radiusPx > rest.radiusPx);
        assertEquals(LensBeadGeometry.beadShelfAlpha(0.5f), mDock.dockBeadShelfAlpha(), 0f);
    }

    /** Seven 120 px icons, 154 px apart, on a 1080 x 200 host (as DockBeadsLeaveTest). */
    private static LensBeadGeometry.Beads sevenBeads() {
        float[] boxes = new float[7 * 4];
        for (int i = 0; i < 7; i++) {
            float cx = 77f + 154f * i;
            boxes[i * 4] = cx - 60f;
            boxes[i * 4 + 1] = 40f;
            boxes[i * 4 + 2] = cx + 60f;
            boxes[i * 4 + 3] = 160f;
        }
        LensBeadGeometry.Beads beads = LensBeadGeometry.compute(boxes, 7, 0f, 0f, 1080f, 200f, 2.625f);
        assertNotNull("the fixture row must wear beads", beads);
        return beads;
    }

    // ------------------------------------------------------------------ Plank effects and touch light

    /** A 400 x 100 dock stack at the window's origin, the plank the springs are wired to. */
    private View plankAtOrigin() {
        FrameLayout stack = new FrameLayout(RuntimeEnvironment.getApplication());
        stack.layout(0, 0, 400, 100);
        mHost.views.put(R.id.accessory_stack_container, stack);
        return stack;
    }

    private static MotionEvent touch(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private boolean plankTouchInside() {
        return ReflectionHelpers.getField(mDock, "mDockPlankTouchInside");
    }

    @Test
    public void aPressOnThePlankDrivesItsSpringsAndAPressOffItDoesNot() {
        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 50f));
        assertFalse("no springs before the dock's views are wired", plankTouchInside());

        View plank = plankAtOrigin();
        mDock.setupDockPlankFx();
        assertNotNull(mDock.dockPlankController());
        assertSame(plank, ReflectionHelpers.getField(mDock, "mDockPlankTarget"));

        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 50f));
        assertTrue("a press on the plank", plankTouchInside());
        mDock.feedDockPlank(touch(MotionEvent.ACTION_UP, 200f, 50f));
        assertFalse("the lift lets go", plankTouchInside());

        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 300f));
        assertFalse("a press below the plank", plankTouchInside());
    }

    @Test
    public void aPressOnAKeySurfaceRidingThePlankLeavesItsSpringsAlone() {
        plankAtOrigin();
        FrameLayout keys = new FrameLayout(RuntimeEnvironment.getApplication());
        keys.layout(0, 0, 400, 60);
        mHost.views.put(R.id.inapp_keyboard_container, keys);
        mDock.setupDockPlankFx();

        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 30f));
        assertFalse("a key under the finger", plankTouchInside());
        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 80f));
        assertTrue("the dock below the keys", plankTouchInside());
    }

    @Test
    public void theDrawerTakingTheGestureHandsThePlankBack() {
        plankAtOrigin();
        mDock.setupDockPlankFx();
        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 50f));
        assertTrue(plankTouchInside());
        mHost.drawerEngaged = true;
        mDock.feedDockPlank(touch(MotionEvent.ACTION_MOVE, 200f, 40f));
        assertFalse("the drawer's drag owns the views now", plankTouchInside());
    }

    @Test
    public void reducedMotionReachesTheSprings() {
        plankAtOrigin();
        mHost.reducedMotion = true;
        mDock.setupDockPlankFx();
        assertSpringsReduced(true);
        mHost.reducedMotion = false;
        mDock.setupDockPlankFx();
        assertSpringsReduced(false);
    }

    private void assertSpringsReduced(boolean reduced) {
        boolean held = ReflectionHelpers.getField(mDock.dockPlankController(), "mReducedMotion");
        assertEquals(reduced, held);
    }

    @Test
    public void belowApi33APressLightsNothingAndBuildsNoFade() {
        plankAtOrigin();
        mDock.feedDockTouchLight(touch(MotionEvent.ACTION_DOWN, 200f, 50f));
        assertFalse(ReflectionHelpers.<Boolean>getField(mDock, "mDockTouchLightDown"));
        assertNull(ReflectionHelpers.getField(mDock, "mDockTouchFade"));
    }

    static class FakeHost implements DockController.Host {
        boolean prefs = true;
        float share = Float.NaN;
        @NonNull DockSurfaceSpec spec = DockSurfaceSpec.LENS_BEADS;
        @Nullable View surfaceHost;
        final SparseArray<View> views = new SparseArray<>();
        boolean reducedMotion;
        boolean drawerEngaged;
        boolean keyboardShown;

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T extends View> T findViewById(int id) {
            if (id == R.id.accessory_surface_host && surfaceHost != null) return (T) surfaceHost;
            return (T) views.get(id);
        }

        @Nullable
        @Override
        public <T extends View> T hotView(int id) {
            return findViewById(id);
        }

        @NonNull @Override public Context context() { return RuntimeEnvironment.getApplication(); }
        @NonNull @Override public View decorView() { return new View(context()); }
        @Override public float dpToPx(float dp) { return dp * density(); }
        @Override public int withAlphaComponent(int color, int alpha) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
        }
        @Override public boolean viewContainsScreenPoint(@Nullable View view, float x, float y) {
            if (view == null || !view.isShown()) return false;
            int[] at = new int[2];
            view.getLocationOnScreen(at);
            return x >= at[0] && x < at[0] + view.getWidth()
                && y >= at[1] && y < at[1] + view.getHeight();
        }
        @Override public boolean hasPreferences() { return prefs; }
        @Override public boolean isReducedMotionEnabled() { return reducedMotion; }
        @Override public boolean isInAppKeyboardShown() { return keyboardShown; }
        @Override public boolean isAppDrawerEngaged() { return drawerEngaged; }
        @Override public float beadShare() { return share; }
        @NonNull @Override public DockSurfaceSpec shownDockSurfaceSpec() { return spec; }
        @Override public boolean dockFloats() { return false; }
        @Override public float resolveDockCapsuleCornerRadiusPx(int surfaceHeightPx) { return 0f; }
        @NonNull @Override public DockRows dockRows() { throw new AssertionError("not read"); }
        @Nullable @Override public ChromeRenderer chrome() { return null; }
        @Override public void rebuildBackdropGlassEffectForLens() { }
        @Override public boolean glassParamsValid() { return true; }
        @Nullable @Override public GlassRefraction.Program dockGlassProgram() { return null; }
        @Override public boolean keyLensActive() { return false; }
        @Override public float glassOriginLeft() { return 0f; }
        @Override public float glassOriginTop() { return 0f; }
        @Override public boolean isGlassTouchLightAllowed() { return true; }
        @Override public int resolveDockAccentColor() { return 0xFF3366CC; }
        @Nullable @Override public SuggestionBarView dockRow() { return null; }
        @NonNull @Override public PlaceLayout placeLayout() { throw new AssertionError("not read"); }
        @Override public boolean stackStandsDownOnFrame() { return false; }
        @Override public boolean keyboardSharesDockGlass() { return false; }
        @Nullable @Override public PromptLineFieldView promptFieldInRowBand() { return null; }
        @Override public float density() { return 2.625f; }
        @Override public void syncFrameBeadCut() { }
        @Override public void onDockBeadsReplaced(@Nullable View host, boolean modeChanged) { }
    }
}
