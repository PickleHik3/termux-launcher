package com.termux.app.dock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Build;
import android.widget.ImageView;

import com.termux.R;
import com.termux.app.chrome.GlassFilters;
import com.termux.app.place.SeamCrossing;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/**
 * The foot's own card (#307 views A): at a rest it wears the frost of the host that stands, and
 * across a Home to Terminal crossing its frost blends from the leaving host's to the arriving
 * host's on the crossing's share, every step built as the crossing begins and only picked per frame.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FootDressTest {

    private DockControllerTest.FakeHost mHost;
    private FootDress mFoot;
    private ImageView mHomeBackdrop;
    private ImageView mTerminalBackdrop;
    private ImageView mFootBackdrop;
    private ColorFilter mHomeFrost;
    private ColorFilter mTerminalFrost;

    @Before
    public void setUp() {
        mHost = new DockControllerTest.FakeHost();
        mHost.chrome = com.termux.app.chrome.TestChromeRenderers.overFakeSurfaces(
            RuntimeEnvironment.getApplication());
        DockController dock = new DockController(mHost);
        mFoot = new FootDress(dock, new DressSources.Foot());
        mHomeBackdrop = backdrop(R.id.accessory_blur_backdrop);
        mTerminalBackdrop = backdrop(R.id.terminal_dock_blur_backdrop);
        mFootBackdrop = backdrop(R.id.dock_foot_blur_backdrop);
        mHomeFrost = GlassFilters.frost();
        ColorMatrix evenOut = new ColorMatrix();
        evenOut.setScale(0.8f, 0.9f, 1.1f, 1f);
        mTerminalFrost = GlassFilters.withEvenOut(evenOut, mHomeFrost);
        assertNotSame(mHomeFrost, mTerminalFrost);
        mHomeBackdrop.setColorFilter(mHomeFrost);
        mTerminalBackdrop.setColorFilter(mTerminalFrost);
        mFootBackdrop.setColorFilter(mHomeFrost);
        // The foot's glass is up, as its rest pass leaves it.
        GlassBackdropDress backdrop = ReflectionHelpers.getField(mFoot, "mBackdrop");
        backdrop.setShown(true);
    }

    private ImageView backdrop(int id) {
        ImageView view = new ImageView(RuntimeEnvironment.getApplication());
        mHost.views.put(id, view);
        return view;
    }

    /** At a rest the foot wears the standing host's own filter object: Home's while it stands. */
    @Test public void atRestTheFootWearsTheStandingHostsFrost() {
        assertSame(mHomeFrost, mFoot.standingHostFrost());
    }

    /** Home to the Terminal: the leaving host's frost at 0, the arriving host's at 1, a blend between. */
    @Test public void theFrostBlendsOnTheCrossingsShare() {
        mFoot.onCrossingBegin(true, false);
        mFoot.blend(0f);
        assertSame(mHomeFrost, mFootBackdrop.getColorFilter());
        mFoot.blend(1f);
        assertSame(mTerminalFrost, mFootBackdrop.getColorFilter());

        mFoot.blend(0.5f);
        ColorFilter half = mFootBackdrop.getColorFilter();
        assertTrue(half instanceof ColorMatrixColorFilter);
        float[] from = GlassFilters.matrixOf(mHomeFrost).getArray();
        float[] to = GlassFilters.matrixOf(mTerminalFrost).getArray();
        int last = SeamCrossing.END_STEPS;
        int step = Math.round(0.5f * last);
        float share = step / (float) last;
        float[] want = new float[20];
        for (int i = 0; i < want.length; i++) want[i] = from[i] + (to[i] - from[i]) * share;
        ColorMatrix got = new ColorMatrix();
        ((ColorMatrixColorFilter) half).getColorMatrix(got);
        assertArrayEquals(want, got.getArray(), 1e-4f);
    }

    /** The Terminal to Home blends the other way. */
    @Test public void theTerminalToHomeBlendsFromTheTerminalsFrost() {
        mFoot.onCrossingBegin(false, true);
        mFoot.blend(0f);
        assertSame(mTerminalFrost, mFootBackdrop.getColorFilter());
        mFoot.blend(1f);
        assertSame(mHomeFrost, mFootBackdrop.getColorFilter());
    }

    /** The steps are built once as the crossing begins: a frame only picks one, allocating nothing. */
    @Test public void aFramePicksAStepBuiltAtTheBeginAndAllocatesNone() {
        mFoot.onCrossingBegin(true, false);
        ColorFilter[] steps = ReflectionHelpers.getField(mFoot, "mSteps");
        ColorFilter[] built = steps.clone();
        for (ColorFilter step : built) assertNotNull(step);
        for (int frame = 0; frame <= 40; frame++) {
            mFoot.blend(frame / 40f);
            ColorFilter worn = mFootBackdrop.getColorFilter();
            boolean known = false;
            for (ColorFilter step : built) known |= step == worn;
            assertTrue("frame " + frame + " wears a step built at the begin", known);
        }
        assertArrayEquals("no frame rebuilt a step", built, steps);
    }

    /** Between two Home pages, or with the foot down, nothing blends. */
    @Test public void nothingBlendsBetweenHomePagesOrWithTheFootDown() {
        mFoot.onCrossingBegin(true, true);
        mFoot.blend(1f);
        assertSame(mHomeFrost, mFootBackdrop.getColorFilter());

        GlassBackdropDress backdrop = ReflectionHelpers.getField(mFoot, "mBackdrop");
        backdrop.setShown(false);
        mFoot.onCrossingBegin(true, false);
        mFoot.blend(1f);
        assertSame(mHomeFrost, mFootBackdrop.getColorFilter());
        assertFalse(mFoot.shown());
    }

    /** Released, the foot holds no step of the blend. */
    @Test public void releaseDropsTheSteps() {
        mFoot.onCrossingBegin(true, false);
        mFoot.release();
        ColorFilter[] steps = ReflectionHelpers.getField(mFoot, "mSteps");
        for (ColorFilter step : steps) assertNull(step);
        mFoot.blend(1f);
        assertFalse(mFoot.shown());
    }
}
