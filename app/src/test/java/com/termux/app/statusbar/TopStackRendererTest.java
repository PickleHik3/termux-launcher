package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.CrossfadeDrawable;
import com.termux.app.page.Side;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.SeamFade;
import com.termux.app.place.StackScene;
import com.termux.app.window.Counters;
import com.termux.app.window.FrameGuards;
import com.termux.app.window.FramePipeline;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The top stack's one writer over fake views: a shared bar stays put while the chips slide with
 * the Workspace's rows and the glass crosses on {@link SeamFade}'s curve, a riding bar carries the
 * strip and the chips' counter-slide, a landing that stands the last frame writes nothing, the
 * rests put the chips a wall off on Home, and the three properties it owns are on the guards.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TopStackRendererTest {

    private static final int WIDTH = 1000;
    private static final int RIDE_PX = 300;
    private static final int MAX_FRAMES = 12;

    private final SparseArray<View> mViews = new SparseArray<>();
    private TopStackRenderer mRenderer;
    private FramePipeline mFrames;
    private View mHost;
    private View mStrip;
    private View mChips;
    private View mStripGlass;
    private View mBarGlass;
    private CrossfadeDrawable mStripBlend;
    private CrossfadeDrawable mBarBlend;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mHost = put(R.id.terminal_window_bar_host, new FrameLayout(context));
        mStrip = put(R.id.terminal_status_bar_background, new FrameLayout(context));
        mChips = put(R.id.terminal_status_place_content, new FrameLayout(context));
        mStripGlass = put(R.id.terminal_status_bar_glass_surface, new FrameLayout(context));
        mBarGlass = put(R.id.terminal_window_bar_background, new FrameLayout(context));
        mStripBlend = new CrossfadeDrawable(new ColorDrawable(Color.RED),
            new ColorDrawable(Color.BLUE), 0f);
        mBarBlend = new CrossfadeDrawable(new ColorDrawable(Color.RED),
            new ColorDrawable(Color.BLUE), 0f);
        mStripGlass.setBackground(mStripBlend);
        mBarGlass.setBackground(mBarBlend);
        mRenderer = new TopStackRenderer(new TopStackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
        });
        mFrames = new FramePipeline();
        mRenderer.attach(mFrames);
        mRenderer.setWidth(WIDTH);
        Counters.clear();
        Counters.enable(true, null);
    }

    @After public void tearDown() {
        Counters.enable(false, null);
        Counters.clear();
    }

    private View put(int id, View view) {
        mViews.put(id, view);
        return view;
    }

    private static TopStackScene.End end(Side side, PlaceLayout.Edge edge, int outline,
                                         boolean stripShown, boolean stripDressed) {
        TopStackScene.End e = new TopStackScene.End();
        e.barShown = true;
        e.edge = edge;
        e.side = side;
        e.lookId = side == Side.HOME ? 1 : 2;
        e.outlineKey = outline;
        e.chipsShown = true;
        e.stripShown = stripShown;
        e.stripDressedForBar = stripDressed;
        return e;
    }

    /** Flushes until a frame writes nothing more; fails if it never settles. */
    private void settle() {
        for (int i = 0; i < MAX_FRAMES; i++) {
            int before = mRenderer.mWriteCount;
            mFrames.flush("test");
            if (mRenderer.mWriteCount == before) return;
        }
        throw new AssertionError("the top stack did not settle in " + MAX_FRAMES + " frames");
    }

    private void frameAt(float p) {
        mRenderer.setProgress(p);
        settle();
    }

    @Test public void aSharedBarStaysPutWhileTheChipsSlideAndTheGlassCrosses() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 0, true, true), true);
        for (float p = 0f; p <= 1.0001f; p += 0.125f) {
            frameAt(Math.min(1f, p));
            float q = Math.min(1f, p);
            assertEquals(0f, mHost.getTranslationX(), 0f);
            assertEquals(0f, mStrip.getTranslationX(), 0f);
            assertEquals("chips: " + q, WIDTH * (1f - q), mChips.getTranslationX(), 0.001f);
            assertEquals(StackScene.sideSlide(Side.WORKSPACE, Side.HOME, Side.WORKSPACE, q, WIDTH),
                mChips.getTranslationX(), 0f);
            assertEquals(SeamFade.share(q), mStripBlend.share(), 0f);
            assertEquals(SeamFade.share(q), mBarBlend.share(), 0f);
            // The leaving end showed the strip's glass: it stays whole while its glass crosses.
            assertEquals(1f, mStripGlass.getAlpha(), 0f);
        }
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test public void aStripTheLeavingEndDidNotShowComesInByItsAlpha() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, false, false),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 0, true, true), false);
        frameAt(0f);
        assertEquals(SeamFade.presence(false, true, 0f), mStripGlass.getAlpha(), 0f);
        frameAt(0.5f);
        assertEquals(SeamFade.presence(false, true, SeamFade.share(0.5f)),
            mStripGlass.getAlpha(), 0f);
        frameAt(1f);
        assertEquals(1f, mStripGlass.getAlpha(), 0f);
    }

    @Test public void aLandingThatStandsTheLastFrameWritesNothing() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 0, true, true), true);
        frameAt(0.5f);
        frameAt(1f);
        float host = mHost.getTranslationX(), strip = mStrip.getTranslationX(),
            chips = mChips.getTranslationX(), share = mBarBlend.share();
        int writes = mRenderer.mWriteCount;
        mRenderer.onLanding();
        settle();
        assertEquals(writes, mRenderer.mWriteCount);
        assertEquals(host, mHost.getTranslationX(), 0f);
        assertEquals(strip, mStrip.getTranslationX(), 0f);
        assertEquals(chips, mChips.getTranslationX(), 0f);
        assertEquals(share, mBarBlend.share(), 0f);
        assertEquals(0f, chips, 0f);
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test public void aCrossingBackToItsStartLandsOnItsStartAsWell() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 0, true, true), true);
        frameAt(0.6f);
        frameAt(0f);
        int writes = mRenderer.mWriteCount;
        mRenderer.onLanding();
        settle();
        assertEquals(writes, mRenderer.mWriteCount);
        assertEquals(WIDTH, mChips.getTranslationX(), 0f);
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test public void atRestTheChipsStandAWallOffOnHomeAndInPlaceOnTheWorkspace() {
        mRenderer.rest(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(WIDTH, mChips.getTranslationX(), 0f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        mRenderer.rest(end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(0f, mChips.getTranslationX(), 0f);
        // The wall laid out at another width: the chips stand a whole of it off again.
        mRenderer.rest(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true));
        mRenderer.setWidth(720);
        settle();
        assertEquals(720f, mChips.getTranslationX(), 0f);
        // A rest asked during a crossing waits for it.
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 0, true, true), true);
        mRenderer.setProgress(0.5f);
        mRenderer.rest(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(360f, mChips.getTranslationX(), 0.001f);
    }

    @Test public void aRidingBarCarriesItsStripAndTheChipsSlideAgainstIt() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 1, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 2, true, true), true);
        mRenderer.setProgress(0.7f);
        mRenderer.setRide(RIDE_PX, true);
        settle();
        float slide = StackScene.sideSlide(Side.WORKSPACE, Side.HOME, Side.WORKSPACE, 0.7f, WIDTH);
        assertEquals(RIDE_PX, mHost.getTranslationX(), 0f);
        assertEquals(RIDE_PX, mStrip.getTranslationX(), 0f);
        assertEquals(slide - RIDE_PX, mChips.getTranslationX(), 0.001f);
        // The ride wears the arriving end: the glass is wholly its dress.
        assertEquals(1f, mBarBlend.share(), 0f);
        assertEquals(1f, mStripBlend.share(), 0f);
        mRenderer.setRide(RIDE_PX, false);
        settle();
        assertEquals(0f, mBarBlend.share(), 0f);
    }

    @Test public void aRidingBarsStripFollowsOnlyWhereItContinuesTheBarsGlass() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 1, true, false),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 2, true, false), true);
        mRenderer.setProgress(0.4f);
        mRenderer.setRide(RIDE_PX, true);
        settle();
        assertEquals(RIDE_PX, mHost.getTranslationX(), 0f);
        assertEquals(0f, mStrip.getTranslationX(), 0f);
    }

    @Test public void aBarOnTheBottomEdgeStaysPutAndTheChipsSlideTheSame() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.BOTTOM, 0, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.BOTTOM, 0, true, true), true);
        frameAt(0.25f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        assertEquals(WIDTH * 0.75f, mChips.getTranslationX(), 0.001f);
        // Apart in edge, with no ride the bar still stands put and its glass crosses.
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 1, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.BOTTOM, 2, true, true), true);
        frameAt(0.5f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        assertEquals(SeamFade.share(0.5f), mBarBlend.share(), 0f);
    }

    @Test public void theThreePropertiesAreTheTopWritersAndAForeignWriteIsHeard() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 0, true, true), true);
        frameAt(0.3f);
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        // The counter keeps the last detail of a kind, so each property is heard on its own.
        mHost.setTranslationX(9f);
        mRenderer.apply();
        assertTrue(Counters.lines().toString(),
            Counters.lines().toString().contains("top terminal_window_bar_host.translationX"));
        mStrip.setTranslationX(11f);
        mRenderer.apply();
        assertTrue(Counters.lines().toString(), Counters.lines().toString()
            .contains("top terminal_status_bar_background.translationX"));
        mChips.setTranslationX(5f);
        mRenderer.apply();
        assertTrue(Counters.lines().toString(), Counters.lines().toString()
            .contains("top terminal_status_place_content.translationX"));
        assertEquals(3, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test public void onAPipelineTheStepAloneWrites() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 0, true, true), true);
        mRenderer.setProgress(0.5f);
        assertEquals(0f, mChips.getTranslationX(), 0f);
        mFrames.flush("test");
        assertEquals(500f, mChips.getTranslationX(), 0.001f);
    }
}
