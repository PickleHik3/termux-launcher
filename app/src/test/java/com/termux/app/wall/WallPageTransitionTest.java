package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.app.launcher.home.HomePageTransition;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings' Page transition on the wall: every page is posed by where its slide puts it, as Home
 * poses its pages; the slide stays exactly what it was; a live surface slides; the chrome, which
 * follows the wall's offset, sees the same offsets whatever the effect.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class WallPageTransitionTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1800;
    private static final float EPS = 1e-3f;

    // ---- The policy ----------------------------------------------------------------------------

    private static HomePageTransition.Transform pose(HomePageTransition effect, float slidePx,
                                                     boolean turning) {
        HomePageTransition.Transform out = new HomePageTransition.Transform();
        WallPageTransition.pose(effect, slidePx, WIDTH, turning, out);
        return out;
    }

    private static void assertFlatAt(String why, float slidePx, HomePageTransition.Transform t) {
        // Bit for bit: the slide writes the very float it always wrote.
        assertEquals(why, Float.floatToIntBits(slidePx), Float.floatToIntBits(t.translationX));
        assertEquals(why, 1f, t.scale, 0f);
        assertEquals(why, 1f, t.alpha, 0f);
        assertEquals(why, 0f, t.rotationY, 0f);
        assertTrue(why, WallPageTransition.isFlat(t));
    }

    @Test
    public void theSlideIsExactlyTheSlide() {
        float[] slides = {0f, -1f / 3f * WIDTH, 0.4321f * WIDTH, -WIDTH + 0.1f, 2f * WIDTH,
            -0.7777f * WIDTH};
        for (float slide : slides) {
            assertFlatAt("turning " + slide, slide, pose(HomePageTransition.SLIDE, slide, true));
            assertFlatAt("alone " + slide, slide, pose(HomePageTransition.SLIDE, slide, false));
        }
    }

    @Test
    public void everyEffectPosesAWallPageAsItPosesAHomePage() {
        float[] positions = {-0.9f, -0.5f, -0.2f, 0.2f, 0.5f, 0.9f};
        for (HomePageTransition effect : HomePageTransition.values()) {
            for (float p : positions) {
                HomePageTransition.Transform home = new HomePageTransition.Transform();
                effect.transform(p, WIDTH, true, home);
                HomePageTransition.Transform wall = pose(effect, p * WIDTH, true);
                String why = effect + " at " + p;
                assertEquals(why, home.translationX, wall.translationX, 0.01f);
                assertEquals(why, home.scale, wall.scale, EPS);
                assertEquals(why, home.alpha, wall.alpha, EPS);
                assertEquals(why, home.rotationY, wall.rotationY, EPS);
                assertEquals(why, home.pivotXFraction, wall.pivotXFraction, EPS);
            }
        }
    }

    @Test
    public void aPageAtRestOrParkedIsFlatUnderEveryEffect() {
        for (HomePageTransition effect : HomePageTransition.values()) {
            assertFlatAt(effect + " at rest", 0f, pose(effect, 0f, true));
            assertFlatAt(effect + " a width left", -WIDTH, pose(effect, -WIDTH, true));
            assertFlatAt(effect + " a width right", WIDTH, pose(effect, WIDTH, true));
            assertFlatAt(effect + " parked", 2f * WIDTH, pose(effect, 2f * WIDTH, true));
        }
    }

    @Test
    public void aPullIntoEitherEndOnlySlides() {
        for (HomePageTransition effect : HomePageTransition.values()) {
            assertFlatAt(effect.name(), 140f, pose(effect, 140f, false));
            assertFlatAt(effect.name(), -140f, pose(effect, -140f, false));
        }
    }

    @Test
    public void aLiveSurfaceSlidesWhateverIsChosen() {
        for (HomePageTransition effect : HomePageTransition.values()) {
            assertSame(HomePageTransition.SLIDE, WallPageTransition.effectFor(effect, true));
            assertSame(effect, WallPageTransition.effectFor(effect, false));
        }
    }

    @Test
    public void depthLaysTheRightPageOverAndStackTheLeft() {
        assertEquals(1, WallPageTransition.topSide(HomePageTransition.DEPTH));
        assertEquals(-1, WallPageTransition.topSide(HomePageTransition.STACK));
        assertEquals(0, WallPageTransition.topSide(HomePageTransition.SLIDE));
        assertEquals(0, WallPageTransition.topSide(HomePageTransition.FADE));
        assertEquals(0, WallPageTransition.topSide(HomePageTransition.CAROUSEL));
    }

    @Test
    public void aLeavingWindowsCardGoesUnderTheWallWhereTheEffectSinksIt() {
        // Depth, forward: the window leaves to the left and sinks; the next slides over it.
        assertTrue(WallPageTransition.departureBeneath(HomePageTransition.DEPTH, 1));
        assertFalse(WallPageTransition.departureBeneath(HomePageTransition.DEPTH, -1));
        // Stack, forward: the window lifts off the top; the next rises from beneath.
        assertFalse(WallPageTransition.departureBeneath(HomePageTransition.STACK, 1));
        assertTrue(WallPageTransition.departureBeneath(HomePageTransition.STACK, -1));
        for (HomePageTransition effect : new HomePageTransition[] {HomePageTransition.SLIDE,
                HomePageTransition.FADE, HomePageTransition.CAROUSEL}) {
            assertFalse(effect.name(), WallPageTransition.departureBeneath(effect, 1));
            assertFalse(effect.name(), WallPageTransition.departureBeneath(effect, -1));
        }
    }

    @Test
    public void aLeavingWindowIsPicturedWhereItIsDrawnUnlessItTurns() {
        float slide = -0.4f * WIDTH;
        assertEquals(slide, WallPageTransition.departureCaptureSlidePx(
            HomePageTransition.SLIDE, slide, WIDTH), 0f);
        // Depth sinks a page leaving forward in place.
        assertEquals(0f, WallPageTransition.departureCaptureSlidePx(
            HomePageTransition.DEPTH, slide, WIDTH), EPS);
        assertEquals(0.2f * slide, WallPageTransition.departureCaptureSlidePx(
            HomePageTransition.FADE, slide, WIDTH), 0.01f);
        assertEquals(slide, WallPageTransition.departureCaptureSlidePx(
            HomePageTransition.STACK, slide, WIDTH), 0.01f);
        // A turn is pictured whole, at rest.
        assertEquals(0f, WallPageTransition.departureCaptureSlidePx(
            HomePageTransition.CAROUSEL, slide, WIDTH), 0f);
        for (HomePageTransition effect : HomePageTransition.values()) {
            assertEquals(effect.name(), 0f,
                WallPageTransition.departureCaptureSlidePx(effect, 0f, WIDTH), 0f);
        }
    }

    // ---- On the wall ---------------------------------------------------------------------------

    private PaneWallLayout wall;
    private View widgets;
    private View terminal;
    private View display;
    private final List<Float> offsets = new ArrayList<>();

    @Before
    public void freshVelocityTrackers() {
        FreshVelocityTrackers.drainPool();
    }

    /** A display page whose picture is a live surface that never stands still. */
    private static final class LiveSurfacePage extends FrameLayout implements SurfacePage {
        LiveSurfacePage(android.content.Context context) {
            super(context);
        }
        @Override public void holdStill(@NonNull Runnable onReady) { }
        @Override public boolean isStill() { return false; }
        @Override public void releaseStill() { }
        @Override public void dropStill() { }
    }

    private void build(@NonNull HomePageTransition effect, boolean liveDisplay) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        wall = new PaneWallLayout(activity);
        widgets = new FrameLayout(activity);
        terminal = new FrameLayout(activity);
        display = liveDisplay ? new LiveSurfacePage(activity) : new FrameLayout(activity);
        wall.addView(widgets);
        wall.addView(terminal);
        wall.addView(display);
        wall.setReducedMotion(true);
        wall.setPages(PaneWallPolicy.availablePages(false, true, true));
        wall.setPageView(PaneWallPage.TERMINAL, terminal);
        wall.setPageView(PaneWallPage.WIDGETS, widgets);
        wall.setPageView(PaneWallPage.DISPLAY, display);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        wall.setPageTransition(effect);
        offsets.clear();
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public void onWallOffsetChanged(float offsetPx) {
                offsets.add(offsetPx);
            }
        });
    }

    @Test
    public void underTheSlideADragOnlyMovesThePages() {
        build(HomePageTransition.SLIDE, false);
        wall.beginDrag();
        wall.dragTo(-300f);
        assertEquals(-300f, terminal.getTranslationX(), 0f);
        assertEquals(WIDTH - 300f, display.getTranslationX(), 0f);
        for (View page : new View[] {terminal, display}) {
            assertEquals(1f, page.getScaleX(), 0f);
            assertEquals(1f, page.getAlpha(), 0f);
            assertEquals(0f, page.getRotationY(), 0f);
            assertEquals(View.LAYER_TYPE_NONE, page.getLayerType());
        }
        assertTrue(wall.posedPages().isEmpty());
    }

    @Test
    public void depthSinksThePageLeavingAndSlidesTheNextOverIt() {
        build(HomePageTransition.DEPTH, false);
        wall.beginDrag();
        wall.dragTo(-300f);
        float p = -300f / WIDTH;
        // The terminal leaves forward: in place, smaller and fainter, on a layer.
        assertEquals(0f, terminal.getTranslationX(), EPS);
        assertEquals(1f + 0.25f * p, terminal.getScaleX(), EPS);
        assertEquals(1f + p, terminal.getAlpha(), EPS);
        assertEquals(View.LAYER_TYPE_HARDWARE, terminal.getLayerType());
        // Its slide is still where the drag put it: what the outline and the frame line read.
        assertEquals(-300f, wall.slidePx(terminal), 0f);
        // The display page comes in from the right, sliding, and is drawn over it (its child
        // order already is).
        assertEquals(WIDTH - 300f, display.getTranslationX(), EPS);
        assertEquals(1f, display.getScaleX(), 0f);
        assertEquals(2, wall.getChildDrawingOrder(3, 2));

        // Back at rest, nothing is posed and no layer is kept.
        wall.cancelDrag();
        assertEquals(0f, terminal.getTranslationX(), 0f);
        assertEquals(1f, terminal.getScaleX(), 0f);
        assertEquals(1f, terminal.getAlpha(), 0f);
        assertEquals(View.LAYER_TYPE_NONE, terminal.getLayerType());
        assertTrue(wall.posedPages().isEmpty());
    }

    @Test
    public void stackDrawsTheLeftPageOverTheRight() {
        build(HomePageTransition.STACK, false);
        wall.beginDrag();
        wall.dragTo(-300f);
        // The terminal (child 1) lifts off the top; the display page (child 2) rises beneath.
        assertEquals(-300f, terminal.getTranslationX(), EPS);
        assertTrue(display.getScaleX() < 1f);
        assertEquals(2, wall.getChildDrawingOrder(3, 1));
        assertEquals(1, wall.getChildDrawingOrder(3, 2));
        wall.cancelDrag();
        assertEquals(1, wall.getChildDrawingOrder(3, 1));
    }

    @Test
    public void aLiveDisplayPageSlidesWhileThePageBesideItTakesTheEffect() {
        build(HomePageTransition.FADE, true);
        wall.goTo(PaneWallPage.DISPLAY, false);
        wall.beginDrag();
        wall.dragTo(300f);
        assertEquals(300f, display.getTranslationX(), 0f);
        assertEquals(1f, display.getAlpha(), 0f);
        assertEquals(View.LAYER_TYPE_NONE, display.getLayerType());
        float p = (300f - WIDTH) / WIDTH;
        assertEquals(p * WIDTH * 0.2f, terminal.getTranslationX(), 0.01f);
        assertEquals(1f + p, terminal.getAlpha(), EPS);
    }

    @Test
    public void aPullIntoTheEndOfTheRowOnlySlides() {
        build(HomePageTransition.DEPTH, false);
        wall.goTo(PaneWallPage.WIDGETS, false);
        wall.beginDrag();
        wall.dragTo(200f);
        assertTrue(wall.offsetPx() > 0f);
        assertEquals(wall.offsetPx(), widgets.getTranslationX(), 0f);
        assertEquals(1f, widgets.getScaleX(), 0f);
        assertEquals(1f, widgets.getAlpha(), 0f);
        assertTrue(wall.posedPages().isEmpty());
    }

    @Test
    public void theChromeSeesTheSameOffsetsWhateverTheEffect() {
        // The dock, the status bar and the keyboard travel by the wall's offset alone
        // (PlaceChromeTravel), so an effect that poses the pages leaves their path untouched.
        List<List<Float>> runs = new ArrayList<>();
        for (HomePageTransition effect : HomePageTransition.values()) {
            build(effect, false);
            wall.beginDrag();
            wall.dragTo(-120f);
            wall.dragTo(-480f);
            wall.dragTo(-860f);
            wall.cancelDrag();
            runs.add(new ArrayList<>(offsets));
        }
        for (List<Float> run : runs) assertEquals(runs.get(0), run);
    }
}
