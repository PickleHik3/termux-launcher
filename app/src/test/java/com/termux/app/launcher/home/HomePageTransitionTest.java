package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomePageTransitionTest {

    private static final float W = 1000f;
    private static final float EPS = 1e-4f;

    private static HomePageTransition.Transform at(HomePageTransition effect, float position,
                                                   boolean turning) {
        HomePageTransition.Transform out = new HomePageTransition.Transform();
        effect.transform(position, W, turning, out);
        return out;
    }

    private static void assertRest(HomePageTransition.Transform t) {
        assertEquals(0f, t.translationX, EPS);
        assertEquals(1f, t.scale, EPS);
        assertEquals(1f, t.alpha, EPS);
        assertEquals(0f, t.rotationY, EPS);
    }

    @Test
    public void everyEffectRestsWhereSlideDoes() {
        for (HomePageTransition effect : HomePageTransition.values()) {
            assertRest(at(effect, 0f, true));
            assertRest(at(effect, 0f, false));
        }
    }

    @Test
    public void slideIsTodaysMotion() {
        HomePageTransition.Transform t = at(HomePageTransition.SLIDE, -0.4f, true);
        assertEquals(-400f, t.translationX, EPS);
        assertEquals(1f, t.scale, EPS);
        assertEquals(1f, t.alpha, EPS);
        assertEquals(0f, t.rotationY, EPS);
    }

    @Test
    public void slideIsTheDefaultAndUnknownValuesFallBackToIt() {
        assertSame(HomePageTransition.SLIDE, HomePageTransition.DEFAULT);
        assertSame(HomePageTransition.SLIDE, HomePageTransition.fromValue(null));
        assertSame(HomePageTransition.SLIDE, HomePageTransition.fromValue("spin"));
        for (HomePageTransition effect : HomePageTransition.values()) {
            assertSame(effect, HomePageTransition.fromValue(effect.value));
        }
    }

    @Test
    public void aPullAgainstEitherEndOnlySlidesWhateverTheEffect() {
        for (HomePageTransition effect : HomePageTransition.values()) {
            HomePageTransition.Transform t = at(effect, 0.12f, false);
            assertEquals(effect.name(), 120f, t.translationX, EPS);
            assertEquals(1f, t.scale, EPS);
            assertEquals(1f, t.alpha, EPS);
            assertEquals(0f, t.rotationY, EPS);
        }
    }

    @Test
    public void fadeDriftsALittleAndFades() {
        HomePageTransition.Transform t = at(HomePageTransition.FADE, -0.5f, true);
        assertEquals(-0.5f * W * HomePageTransition.FADE_DRIFT, t.translationX, EPS);
        assertEquals(0.5f, t.alpha, EPS);
        assertEquals(0f, at(HomePageTransition.FADE, 1f, true).alpha, EPS);
    }

    @Test
    public void depthSinksThePageLeavingForwardAndSlidesTheOneComingIn() {
        HomePageTransition.Transform leaving = at(HomePageTransition.DEPTH, -1f, true);
        assertEquals(0f, leaving.translationX, EPS);
        assertEquals(1f - HomePageTransition.DEPTH_SCALE_DROP, leaving.scale, EPS);
        assertEquals(0f, leaving.alpha, EPS);
        HomePageTransition.Transform coming = at(HomePageTransition.DEPTH, 0.6f, true);
        assertEquals(600f, coming.translationX, EPS);
        assertEquals(1f, coming.scale, EPS);
        assertEquals(1f, coming.alpha, EPS);
    }

    @Test
    public void stackSlidesTheTopPageOffAndRaisesTheNextFromBeneath() {
        HomePageTransition.Transform leaving = at(HomePageTransition.STACK, -0.3f, true);
        assertEquals(-300f, leaving.translationX, EPS);
        assertEquals(1f, leaving.alpha, EPS);
        HomePageTransition.Transform beneath = at(HomePageTransition.STACK, 0.5f, true);
        assertEquals(0f, beneath.translationX, EPS);
        assertEquals(1f - HomePageTransition.STACK_SCALE_DROP * 0.5f, beneath.scale, EPS);
        assertEquals(0.5f, beneath.alpha, EPS);
    }

    @Test
    public void carouselTurnsAboutTheSharedEdge() {
        HomePageTransition.Transform leftward = at(HomePageTransition.CAROUSEL, -0.5f, true);
        assertEquals(-500f, leftward.translationX, EPS);
        assertEquals(-0.5f * HomePageTransition.CAROUSEL_DEGREES, leftward.rotationY, EPS);
        assertEquals(1f, leftward.pivotXFraction, EPS);
        HomePageTransition.Transform rightward = at(HomePageTransition.CAROUSEL, 0.25f, true);
        assertEquals(0f, rightward.pivotXFraction, EPS);
        assertTrue(Math.abs(rightward.rotationY) < 90f);
    }

    @Test
    public void positionsPastAWholePageAreHeldToOne() {
        HomePageTransition.Transform t = at(HomePageTransition.FADE, -1.6f, true);
        assertEquals(0f, t.alpha, EPS);
        assertEquals(-W * HomePageTransition.FADE_DRIFT, t.translationX, EPS);
    }
}
