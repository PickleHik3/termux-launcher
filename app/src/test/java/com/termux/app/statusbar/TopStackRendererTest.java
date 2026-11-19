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
 * the Terminal's rows and the glass crosses on {@link SeamFade}'s curve, a riding bar carries the
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
    private View mHomeChips;
    private View mStripGlass;
    private View mBarGlass;
    private CrossfadeDrawable mStripBlend;
    private CrossfadeDrawable mBarBlend;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mHost = put(R.id.terminal_window_bar_host, new FrameLayout(context));
        mStrip = put(R.id.terminal_status_bar_background, new FrameLayout(context));
        mChips = put(R.id.terminal_status_place_content, new FrameLayout(context));
        mHomeChips = put(R.id.terminal_status_home_chips, new FrameLayout(context));
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
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        for (float p = 0f; p <= 1.0001f; p += 0.125f) {
            frameAt(Math.min(1f, p));
            float q = Math.min(1f, p);
            assertEquals(0f, mHost.getTranslationX(), 0f);
            assertEquals(0f, mStrip.getTranslationX(), 0f);
            assertEquals("chips: " + q, WIDTH * (1f - q), mChips.getTranslationX(), 0.001f);
            assertEquals(StackScene.sideSlide(Side.TERMINAL, Side.HOME, Side.TERMINAL, q, WIDTH),
                mChips.getTranslationX(), 0f);
            assertEquals(SeamFade.share(q), mStripBlend.share(), 0f);
            assertEquals(SeamFade.share(q), mBarBlend.share(), 0f);
            // The leaving end showed the strip's glass: it stays whole while its glass crosses.
            assertEquals(1f, mStripGlass.getAlpha(), 0f);
        }
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test public void homesFloatChipsSlideOutWithHomeAWallFromTheWindowChips() {
        mRenderer.rest(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(0f, mHomeChips.getTranslationX(), 0f);
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        for (float p = 0f; p <= 1.0001f; p += 0.125f) {
            float q = Math.min(1f, p);
            frameAt(q);
            assertEquals(StackScene.sideSlide(Side.HOME, Side.HOME, Side.TERMINAL, q, WIDTH),
                mHomeChips.getTranslationX(), 0f);
            assertEquals(WIDTH, mChips.getTranslationX() - mHomeChips.getTranslationX(), 0.001f);
        }
        int writes = mRenderer.mWriteCount;
        mRenderer.onLanding();
        settle();
        assertEquals(writes, mRenderer.mWriteCount);
        assertEquals(-WIDTH, mHomeChips.getTranslationX(), 0f);
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test public void aStripTheLeavingEndDidNotShowComesInByItsAlpha() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, false, false),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), false);
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
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
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

    @Test public void theChipsFollowThePagesFractionWhileTheGlassFollowsTheChrome() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        mRenderer.setProgress(0.2f, 0.6f);
        settle();
        assertEquals(WIDTH * 0.4f, mChips.getTranslationX(), 0.001f);
        assertEquals(SeamFade.share(0.2f), mBarBlend.share(), 0f);
    }

    @Test public void theChipsStayInsideTheirSlotWhileTheySlideAndTheClipIsGoneAtRest() {
        mChips.layout(0, 0, 400, 60);
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        frameAt(0.5f);
        assertEquals(new android.graphics.Rect(-500, 0, -100, 60), mChips.getClipBounds());
        frameAt(0.9f);
        assertEquals(new android.graphics.Rect(-100, 0, 300, 60), mChips.getClipBounds());
        int writes = mRenderer.mWriteCount;
        mRenderer.requestApply();
        settle();
        assertEquals(writes, mRenderer.mWriteCount);
        frameAt(1f);
        assertEquals(null, mChips.getClipBounds());
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        // The guard owns the clip: another writer is heard.
        mRenderer.setProgress(0.5f);
        settle();
        mChips.setClipBounds(new android.graphics.Rect(1, 0, 2, 3));
        mRenderer.apply();
        assertTrue(Counters.lines().toString(), Counters.lines().toString()
            .contains("top terminal_status_place_content.clipLeft"));
    }

    @Test public void aLandingWritesNoClipEither() {
        mChips.layout(0, 0, 400, 60);
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        frameAt(0.6f);
        frameAt(1f);
        int writes = mRenderer.mWriteCount;
        mRenderer.onLanding();
        settle();
        assertEquals(writes, mRenderer.mWriteCount);
        assertEquals(null, mChips.getClipBounds());
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test public void aCrossingBackToItsStartLandsOnItsStartAsWell() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        frameAt(0.6f);
        frameAt(0f);
        int writes = mRenderer.mWriteCount;
        mRenderer.onLanding();
        settle();
        assertEquals(writes, mRenderer.mWriteCount);
        assertEquals(WIDTH, mChips.getTranslationX(), 0f);
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test public void atRestTheChipsStandAWallOffOnHomeAndInPlaceOnTheTerminal() {
        mRenderer.rest(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(WIDTH, mChips.getTranslationX(), 0f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        mRenderer.rest(end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(0f, mChips.getTranslationX(), 0f);
        // The wall laid out at another width: the chips stand a whole of it off again.
        mRenderer.rest(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true));
        mRenderer.setWidth(720);
        settle();
        assertEquals(720f, mChips.getTranslationX(), 0f);
        // A rest asked during a crossing waits for it.
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        mRenderer.setProgress(0.5f);
        mRenderer.rest(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(360f, mChips.getTranslationX(), 0.001f);
    }

    @Test public void aRidingBarCarriesItsStripAndTheChipsSlideAgainstIt() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 1, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 2, true, true), true);
        mRenderer.setProgress(0.7f);
        mRenderer.setRide(RIDE_PX, true);
        settle();
        float slide = StackScene.sideSlide(Side.TERMINAL, Side.HOME, Side.TERMINAL, 0.7f, WIDTH);
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
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 2, true, false), true);
        mRenderer.setProgress(0.4f);
        mRenderer.setRide(RIDE_PX, true);
        settle();
        assertEquals(RIDE_PX, mHost.getTranslationX(), 0f);
        assertEquals(0f, mStrip.getTranslationX(), 0f);
    }

    @Test public void aBarOnTheBottomEdgeStaysPutAndTheChipsSlideTheSame() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.BOTTOM, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.BOTTOM, 0, true, true), true);
        frameAt(0.25f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        assertEquals(WIDTH * 0.75f, mChips.getTranslationX(), 0.001f);
        // Apart in edge, with no ride the bar still stands put and its glass crosses.
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 1, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.BOTTOM, 2, true, true), true);
        frameAt(0.5f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        assertEquals(SeamFade.share(0.5f), mBarBlend.share(), 0f);
    }

    @Test public void theThreePropertiesAreTheTopWritersAndAForeignWriteIsHeard() {
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
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
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        mRenderer.setProgress(0.5f);
        assertEquals(0f, mChips.getTranslationX(), 0f);
        mFrames.flush("test");
        assertEquals(500f, mChips.getTranslationX(), 0.001f);
    }

    // ---- The bar's ink ----------------------------------------------------------------------

    private static final int DARK_INK = 0xFF202020;
    private static final int PALE_INK = 0xFFE0E0E0;

    /** Every array the sink was handed, copied: one entry per apply. */
    private final java.util.ArrayList<int[]> mApplied = new java.util.ArrayList<>();

    private TopStackScene.End inked(TopStackScene.End e, int ink) {
        java.util.Arrays.fill(e.inks, ink);
        e.inksResolved = true;
        return e;
    }

    private void sink() {
        mRenderer.setInks(inks -> mApplied.add(inks.clone()));
    }

    private void beginInked() {
        mRenderer.begin(
            inked(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true), PALE_INK),
            inked(end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), DARK_INK), true);
    }

    @Test public void theInksCrossBlendedAndStandExactlyOnEachEndsAtItsEnds() {
        sink();
        beginInked();
        frameAt(0f);
        assertEquals(PALE_INK, mApplied.get(mApplied.size() - 1)[0]);
        frameAt(0.5f);
        int mid = mApplied.get(mApplied.size() - 1)[StatusInkSlots.COUNT - 1];
        assertTrue("strictly between: " + Integer.toHexString(mid),
            Color.red(mid) > Color.red(DARK_INK) && Color.red(mid) < Color.red(PALE_INK));
        frameAt(1f);
        for (int ink : mApplied.get(mApplied.size() - 1)) assertEquals(DARK_INK, ink);
    }

    @Test public void inksThatDidNotChangeAreNotAppliedAgain() {
        sink();
        beginInked();
        frameAt(0.5f);
        int applied = mApplied.size();
        mRenderer.requestApply();
        settle();
        mRenderer.setWidth(WIDTH);
        mRenderer.setProgress(0.5f);
        settle();
        assertEquals(applied, mApplied.size());
    }

    @Test public void theLandingAppliesNothingNewAndTheRestKeepsTheLandedInks() {
        sink();
        beginInked();
        frameAt(0.5f);
        frameAt(1f);
        int applied = mApplied.size();
        mRenderer.onLanding();
        settle();
        assertEquals(applied, mApplied.size());
        // A rest asked for the page it settled on does not wipe the inks it landed with.
        mRenderer.rest(end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(applied, mApplied.size());
    }

    @Test public void aRestAppliesItsInksOnlyWhenTheyChanged() {
        sink();
        int[] pale = new int[StatusInkSlots.COUNT];
        java.util.Arrays.fill(pale, PALE_INK);
        // The caller wrote these to the views itself: the renderer takes them as the standing ink.
        mRenderer.restInks(pale);
        mRenderer.rest(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true));
        settle();
        assertEquals(0, mApplied.size());
        mRenderer.requestApply();
        settle();
        assertEquals(0, mApplied.size());
    }

    @Test public void aCrossingWithUnresolvedEndsWritesNoInks() {
        sink();
        mRenderer.begin(end(Side.HOME, PlaceLayout.Edge.TOP, 0, true, true),
            end(Side.TERMINAL, PlaceLayout.Edge.TOP, 0, true, true), true);
        frameAt(0.5f);
        assertEquals(0, mApplied.size());
    }

    @Test public void theRendererSaysWhileItCrosses() {
        assertEquals(false, mRenderer.crossing());
        beginInked();
        assertEquals(true, mRenderer.crossing());
        frameAt(1f);
        mRenderer.onLanding();
        assertEquals(false, mRenderer.crossing());
    }
}
