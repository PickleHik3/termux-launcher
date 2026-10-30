package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.app.chrome.ChromeInk;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stills (ADR 0016 section 2): one page-still policy for every kind. A page is live while its
 * kind's view is bound to it; otherwise its cell shows its last picture, else its card. A
 * binding that moves takes the leaving page's picture first (W5), at half the wall's size; the
 * picture goes when the page is live again or leaves the wall.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PageStillsTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1800;

    private static final class Host implements PaneWallController.Host {
        WallPages pages;
        int homePage;
        int boundDisplay = -1;

        @Override public boolean reducedMotion() { return true; }
        @Override public boolean hasWindow() { return true; }
        @Override public WallPages wallPages() { return pages; }
        @Override public void bindDisplayPage(int pageId) { boundDisplay = pageId; }
        @Override public int innerCount(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? Math.max(1, pages.homePages().size()) : 1;
        }
        @Override public int innerIndex(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? homePage : 0;
        }
        @Override public void showInnerPage(PaneWallPage page, int index) {
            if (page == PaneWallPage.WIDGETS) homePage = index;
        }
    }

    private Activity activity;
    private Host host;
    private PaneWallController controller;
    private PaneWallLayout wall;
    /** The Home page the frame showed each time it was drawn into a picture. */
    private final List<Integer> drawnOnHomePage = new ArrayList<>();

    @Before public void build() {
        FreshVelocityTrackers.drainPool();
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        host = new Host();
    }

    /** [Home 1][Terminal 2][Home 3]: a Home run on each side of the Terminal page. */
    private static WallPages homeTerminalHome() {
        return WallPagesMigration.seed("home", 0, "home", 1)
            .add(WallPages.PageKind.HOME, 2, null).pages;
    }

    /** [Home 1][Terminal 2][Display 3][Display 4]. */
    private static WallPages twoDisplays() {
        WallPages pages = WallPagesMigration.seed("home", 0, "home", 1)
            .add(WallPages.PageKind.DISPLAY, 2, null).pages;
        return pages.add(WallPages.PageKind.DISPLAY, 3, null).pages;
    }

    private void start(WallPages pages) {
        host.pages = pages;
        PaneWallLayout layout = new PaneWallLayout(activity);
        View terminal = new FrameLayout(activity);
        View widgets = new View(activity) {
            @Override protected void onDraw(Canvas canvas) {
                drawnOnHomePage.add(host.homePage);
            }
        };
        View display = new FrameLayout(activity);
        layout.addView(widgets);
        layout.addView(terminal);
        layout.addView(display);
        controller = new PaneWallController(layout, host);
        controller.attachTerminalPage(terminal);
        layout.setPageView(PaneWallPage.WIDGETS, widgets);
        layout.setPageView(PaneWallPage.DISPLAY, display);
        layout.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, WIDTH, HEIGHT);
        wall = controller.wall();
    }

    private static void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(3000L));
    }

    private void goRight() {
        assertTrue(controller.goTo("right"));
        idle();
    }

    private void goLeft() {
        assertTrue(controller.goTo("left"));
        idle();
    }

    // ---- the policy, the same for every kind ------------------------------------------------

    @Test public void everyKindIsLiveWhereBoundElseItsPictureElseItsCard() {
        for (PaneWallPage kind : PaneWallPage.values()) {
            String k = kind.name();
            assertEquals(k, PageStillPolicy.Shows.LIVE, PageStillPolicy.shows(4, 4, true, true));
            assertEquals(k, PageStillPolicy.Shows.PICTURE,
                PageStillPolicy.shows(5, 4, true, true));
            assertEquals(k, PageStillPolicy.Shows.CARD, PageStillPolicy.shows(5, 4, true, false));
            // A bound page whose view is not ready (the display down) is a still too.
            assertEquals(k, PageStillPolicy.Shows.PICTURE,
                PageStillPolicy.shows(4, 4, false, true));
            assertEquals(k, PageStillPolicy.Shows.CARD, PageStillPolicy.shows(4, -1, true, false));
        }
    }

    @Test public void aMovingBindingTakesTheLeavingPagesPictureOnly() {
        assertEquals(1, PageStillPolicy.pictureOnRebind(1, 3));
        assertEquals(-1, PageStillPolicy.pictureOnRebind(3, 3));
        assertEquals(-1, PageStillPolicy.pictureOnRebind(-1, 3));
    }

    @Test public void aPictureIsHalfTheWallsSide() {
        assertEquals(540, PageStillPolicy.pictureSide(1080));
        assertEquals(900, PageStillPolicy.pictureSide(1800));
        assertEquals(1, PageStillPolicy.pictureSide(1));
    }

    @Test public void aPictureLivesUntilThePageIsLiveAgainOrRemoved() {
        PageStillPolicy.Pictures<String> pictures = new PageStillPolicy.Pictures<>();
        assertNull(pictures.put(1, "a"));
        assertNull(pictures.put(3, "b"));
        assertEquals("a", pictures.put(1, "a2"));
        // Live again: forgotten.
        assertEquals("a2", pictures.put(1, null));
        assertFalse(pictures.has(1));
        // Removed: let go.
        assertEquals(Collections.singletonList("b"), pictures.retain(Arrays.asList(1, 2)));
        assertEquals(0, pictures.size());
    }

    // ---- the layout ---------------------------------------------------------------------------

    @Test public void leavingAHomeRunKeepsItsLastGridAsAHalfSizePictureTakenBeforeTheFrameTurns() {
        start(homeTerminalHome());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertEquals(1, controller.currentPageId());
        goRight();
        goRight();
        assertEquals(3, controller.currentPageId());
        assertTrue(wall.isLive(3));
        // Taken once, while the frame still showed Home page 1 (index 0), not the arriving one.
        assertEquals(Collections.singletonList(0), drawnOnHomePage);
        assertEquals(1, host.homePage);
        Bitmap picture = wall.pictureOf(1);
        assertNotNull(picture);
        assertEquals(WIDTH / 2, picture.getWidth());
        assertEquals(HEIGHT / 2, picture.getHeight());
        PageCardView still = wall.cardOf(1);
        assertNotNull(still);
        assertSame(picture, still.picture());
        assertEquals(PageStillPolicy.Shows.PICTURE, still.shows());
        assertNull(wall.pictureOf(3));
    }

    @Test public void landingSwapsTheStillForTheViewAndDropsItsPicture() {
        start(homeTerminalHome());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        goRight();
        goRight();
        Bitmap first = wall.pictureOf(1);
        assertNotNull(first);
        goLeft();
        goLeft();
        assertEquals(1, controller.currentPageId());
        assertTrue(wall.isLive(1));
        // Page 1 is live again: its picture went, and its still shows nothing of it.
        assertNull(wall.pictureOf(1));
        assertTrue(first.isRecycled());
        assertNull(wall.cardOf(1).picture());
        // Page 3 left: its still is its picture now.
        PageCardView still = wall.cardOf(3);
        assertNotNull(still);
        assertEquals(PageStillPolicy.Shows.PICTURE, still.shows());
        assertFalse(wall.isLive(3));
    }

    @Test public void aRemovedPageTakesItsPictureWithIt() {
        start(homeTerminalHome());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        goRight();
        goRight();
        Bitmap first = wall.pictureOf(1);
        assertNotNull(first);
        host.pages = host.pages.remove(1).pages;
        controller.refreshPages();
        assertNull(wall.pictureOf(1));
        assertTrue(first.isRecycled());
    }

    @Test public void aStillWithNoPictureShowsItsCard() {
        start(homeTerminalHome());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        PageCardView still = wall.cardOf(3);
        assertNotNull(still);
        assertEquals(PageStillPolicy.Shows.CARD, still.shows());
    }

    @Test public void aDisplayPagesPictureFromTheFrameShowsInItsStillButNeverOnTheLivePage() {
        start(twoDisplays());
        controller.landOnColdStart(WallRow.START_IN_HOME);
        assertTrue(controller.goTo("display"));
        idle();
        assertEquals(3, host.boundDisplay);
        Bitmap kept = Bitmap.createBitmap(WIDTH / 2, HEIGHT / 2, Bitmap.Config.ARGB_8888);
        wall.keepPicture(4, kept);
        assertEquals(PageStillPolicy.Shows.PICTURE, wall.cardOf(4).shows());
        Bitmap onLive = Bitmap.createBitmap(WIDTH / 2, HEIGHT / 2, Bitmap.Config.ARGB_8888);
        wall.keepPicture(3, onLive);
        assertNull(wall.pictureOf(3));
        assertTrue(onLive.isRecycled());
    }

    // ---- the card's ink and type --------------------------------------------------------------

    @Test public void theCardWearsTheChromesInkPolarity() {
        int pale = PageCardView.inkFor(ChromeInk.Polarity.PALE_INK);
        int dark = PageCardView.inkFor(ChromeInk.Polarity.DARK_INK);
        assertTrue(Color.red(pale) > 200 && Color.green(pale) > 200 && Color.blue(pale) > 200);
        assertTrue(Color.red(dark) < 50 && Color.green(dark) < 50 && Color.blue(dark) < 50);
        assertEquals(pale, PageCardView.inkFor(null));
    }

    @Test public void theCardsNameFollowsTheSharedTextSize() {
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        float at = PageCardView.nameTextPx(metrics);
        assertEquals(16f * com.termux.app.theme.TextScale.get() * metrics.scaledDensity, at, 0.5f);
    }
}
