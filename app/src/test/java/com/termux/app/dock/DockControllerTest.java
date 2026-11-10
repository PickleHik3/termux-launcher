package com.termux.app.dock;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.robolectric.RuntimeEnvironment;

import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.chrome.ChromeRenderer;
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

    private static final class FakeHost implements DockController.Host {
        boolean prefs = true;
        float share = Float.NaN;
        @NonNull DockSurfaceSpec spec = DockSurfaceSpec.LENS_BEADS;
        @Nullable View surfaceHost;

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T extends View> T findViewById(int id) {
            return id == R.id.accessory_surface_host ? (T) surfaceHost : null;
        }

        @Nullable
        @Override
        public <T extends View> T hotView(int id) {
            return findViewById(id);
        }

        @Override public boolean hasPreferences() { return prefs; }
        @Override public float beadShare() { return share; }
        @NonNull @Override public DockSurfaceSpec shownDockSurfaceSpec() { return spec; }
        @Override public boolean dockFloats() { return false; }
        @Override public float resolveDockCapsuleCornerRadiusPx(int surfaceHeightPx) { return 0f; }
        @NonNull @Override public DockRows dockRows() { throw new AssertionError("not read"); }
        @Nullable @Override public ChromeRenderer chrome() { return null; }
        @Override public void rebuildBackdropGlassEffectForLens() { }
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
