package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.wall.PaneWallLayout;
import com.termux.app.wall.WallPages;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What {@link DisplayPageBinding} asks of its Host: which page it binds, what it forwards on a
 * landing, and when it looks at the frame again. The frame is a bare {@link X11PaneFrame} (its
 * display view cannot exist on the JVM), so the still it would draw is not looked at here; the
 * Host's questions are.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DisplayPageBindingTest {

    private static final int PAGE = 4;
    private static final int OTHER_PAGE = 6;
    private static final long LIVE_DELAY_MS = 300L;

    private static final class FakeHost implements DisplayPageBinding.Host {
        X11PaneFrame frame;
        PaneWallLayout wall;
        boolean running;
        boolean gone;
        boolean buildsFrame;
        final List<WallPages.Page> pages = new ArrayList<>();
        int frameCalls;
        int pagesCalls;
        int buildCalls;
        final List<Integer> pageAsked = new ArrayList<>();

        @Nullable @Override public X11PaneFrame frame() {
            frameCalls++;
            return frame;
        }

        @Nullable @Override public WallPages.Page page(int pageId) {
            pageAsked.add(pageId);
            for (WallPages.Page p : pages) if (p.id == pageId) return p;
            return null;
        }

        @NonNull @Override public List<WallPages.Page> pages() {
            pagesCalls++;
            return pages;
        }

        @Nullable @Override public PaneWallLayout wall() {
            return wall;
        }

        @Override public boolean isRunning() {
            return running;
        }

        @Override public boolean isGone() {
            return gone;
        }

        @Override public boolean buildFrameIfMissing() {
            buildCalls++;
            return buildsFrame;
        }
    }

    private Activity mActivity;
    private FakeHost mHost;
    private final List<Integer> mLanded = new ArrayList<>();
    private DisplayPageBinding mBinding;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mHost = new FakeHost();
        mHost.pages.add(new WallPages.Page(PAGE, PageKind.DISPLAY, null, null));
        mHost.pages.add(new WallPages.Page(OTHER_PAGE, PageKind.DISPLAY, null, null));
        mBinding = new DisplayPageBinding(mActivity, mHost);
        mBinding.setLanding(mLanded::add);
    }

    private void withFrameAndWall() {
        mHost.frame = new X11PaneFrame(mActivity);
        mHost.wall = new PaneWallLayout(mActivity);
    }

    private static void idle() {
        ShadowLooper.idleMainLooper();
    }

    private static void advance(long ms) {
        ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    public void aBindingStartsOnNoPage() {
        assertEquals(-1, mBinding.boundPage());
    }

    @Test
    public void bindRemembersThePageTheWallBoundIt() {
        mBinding.bind(PAGE);
        assertEquals(PAGE, mBinding.boundPage());
        mBinding.bind(OTHER_PAGE);
        assertEquals(OTHER_PAGE, mBinding.boundPage());
        mBinding.bind(-1);
        assertEquals(-1, mBinding.boundPage());
    }

    @Test
    public void bindingAPageAsksTheHostToBuildTheFrameOnTheNextLoop() {
        mBinding.bind(PAGE);
        assertEquals("not before the loop turns", 0, mHost.buildCalls);
        idle();
        assertEquals(1, mHost.buildCalls);
    }

    @Test
    public void bindingNoPageNeverAsksForAFrame() {
        mBinding.bind(-1);
        idle();
        assertEquals(0, mHost.buildCalls);
    }

    @Test
    public void bindingWithoutAFrameOrWallAsksTheHostNothingAboutPages() {
        mBinding.bind(PAGE);
        assertTrue(mHost.pageAsked.isEmpty());
    }

    @Test
    public void leavingAPageWhileTheDisplayIsDownKeepsNoPictureAndTakesNoPageLookup() {
        withFrameAndWall();
        mHost.running = false;
        mBinding.bind(PAGE);
        mHost.pageAsked.clear();
        mBinding.bind(OTHER_PAGE);
        // Only the arriving page is looked up for its still; the left one is not read for a picture.
        assertEquals(Collections.singletonList(OTHER_PAGE), mHost.pageAsked);
    }

    @Test
    public void bindingTheSamePageAgainDoesNotRefreshTheStill() {
        withFrameAndWall();
        mBinding.bind(PAGE);
        int calls = mHost.frameCalls;
        mBinding.bind(PAGE);
        assertEquals(calls, mHost.frameCalls);
    }

    @Test
    public void landedAlwaysPassesThePageToTheHost() {
        mBinding.bind(PAGE);
        mBinding.landed(PAGE);
        mBinding.landed(OTHER_PAGE);
        assertEquals(Arrays.asList(PAGE, OTHER_PAGE), mLanded);
    }

    @Test
    public void landingOnTheBoundPageRefreshesTheStillAfterTheLiveDelay() {
        withFrameAndWall();
        mBinding.bind(PAGE);
        mBinding.landed(PAGE);
        int calls = mHost.frameCalls;
        advance(LIVE_DELAY_MS - 1);
        assertEquals("the page is still waiting for its app", calls, mHost.frameCalls);
        advance(1);
        assertEquals("the still is applied once the delay ran", calls + 1, mHost.frameCalls);
    }

    @Test
    public void landingOnAnotherPageSchedulesNoStillRefresh() {
        withFrameAndWall();
        mBinding.bind(PAGE);
        mBinding.landed(OTHER_PAGE);
        int calls = mHost.frameCalls;
        advance(LIVE_DELAY_MS * 2);
        assertEquals(calls, mHost.frameCalls);
    }

    @Test
    public void landingTwiceInARowRefreshesTheStillOnce() {
        withFrameAndWall();
        mBinding.bind(PAGE);
        mBinding.landed(PAGE);
        mBinding.landed(PAGE);
        int calls = mHost.frameCalls;
        advance(LIVE_DELAY_MS);
        assertEquals(calls + 1, mHost.frameCalls);
    }

    @Test
    public void applyStillWithoutAFrameOrWallAsksNoFurther() {
        mBinding.applyStill();
        assertEquals(1, mHost.frameCalls);
        assertTrue(mHost.pageAsked.isEmpty());

        mHost.frame = new X11PaneFrame(mActivity);
        mBinding.applyStill();
        assertTrue("no wall: nothing more is asked", mHost.pageAsked.isEmpty());
    }

    @Test
    public void applyStillOnNoPageDoesNotLookAPageUp() {
        withFrameAndWall();
        mBinding.applyStill();
        assertTrue(mHost.pageAsked.isEmpty());
    }

    @Test
    public void applyStillOnTheBoundPageLooksThatPageUp() {
        withFrameAndWall();
        mBinding.bind(PAGE);
        mHost.pageAsked.clear();
        mBinding.applyStill();
        assertEquals(Collections.singletonList(PAGE), mHost.pageAsked);
    }

    @Test
    public void refreshCardsReadsTheWallsPagesOnce() {
        mBinding.refreshCards();
        assertEquals(1, mHost.pagesCalls);
    }

    @Test
    public void aCatalogChangeBeforeAnyCardWasReadReadsNothing() {
        mBinding.onCatalogChanged();
        assertEquals(0, mHost.pagesCalls);
    }

    @Test
    public void aCatalogChangeAfterCardsWereReadReadsThemAgain() {
        mBinding.refreshCards();
        assertEquals(1, mHost.pagesCalls);
        mBinding.onCatalogChanged();
        assertEquals(2, mHost.pagesCalls);
    }

    @Test
    public void ensureFrameOnNoPageDoesNotAskTheHost() {
        mBinding.ensureFrame();
        assertEquals(0, mHost.buildCalls);
    }

    @Test
    public void ensureFrameOnABoundPageAsksTheHostToBuildIt() {
        mBinding.bind(PAGE);
        mBinding.ensureFrame();
        assertEquals(1, mHost.buildCalls);
    }

    @Test
    public void aFrameTheHostJustBuiltGetsItsStillApplied() {
        withFrameAndWall();
        mBinding.bind(PAGE);
        mHost.buildsFrame = true;
        int calls = mHost.frameCalls;
        mBinding.ensureFrame();
        assertEquals(calls + 1, mHost.frameCalls);
    }

    @Test
    public void aFrameThatWasAlreadyThereIsLeftAlone() {
        withFrameAndWall();
        mBinding.bind(PAGE);
        mHost.buildsFrame = false;
        int calls = mHost.frameCalls;
        mBinding.ensureFrame();
        assertEquals(calls, mHost.frameCalls);
    }
}
