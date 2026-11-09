package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
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
 * A programmatic slide from the Workspace to Home ({@link PaneWallLayout#goTo}, what the home key
 * and a chip tap run), driven frame by frame by the wall's own slide animator under every page
 * transition: Home's page eases from its first pose to the identity, never holding one pose over
 * the slide and snapping at the settle, and the Workspace's page leaves the same way. The pure
 * pose is the identity at rest and moves monotonically with the page's distance from it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class WallArrivingPoseTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2300;
    private static final float EPS = 1e-4f;

    @Before
    public void freshVelocityTrackers() {
        FreshVelocityTrackers.drainPool();
    }

    // ---- The pure pose ----------------------------------------------------------------------

    @Test
    public void thePoseIsTheIdentityAtRestAndMonotoneAcrossTheSlide() {
        HomePageTransition.Transform t = new HomePageTransition.Transform();
        for (HomePageTransition effect : HomePageTransition.values()) {
            for (int side : new int[] {-1, 1}) {
                WallPageTransition.pose(effect, 0f, WIDTH, true, t);
                assertEquals(effect + " rest", 1f, t.scale, 0f);
                assertEquals(effect + " rest", 1f, t.alpha, 0f);
                assertEquals(effect + " rest", 0f, t.rotationY, 0f);
                float lastScale = 1f;
                float lastAlpha = 1f;
                float lastTurn = 0f;
                for (int i = 1; i < 100; i++) {
                    float slide = side * WIDTH * i / 100f;
                    WallPageTransition.pose(effect, slide, WIDTH, true, t);
                    String at = effect + " side " + side + " step " + i;
                    assertTrue(at, t.scale <= lastScale + EPS);
                    assertTrue(at, t.alpha <= lastAlpha + EPS);
                    assertTrue(at, Math.abs(t.rotationY) >= lastTurn - EPS);
                    lastScale = t.scale;
                    lastAlpha = t.alpha;
                    lastTurn = Math.abs(t.rotationY);
                }
            }
        }
    }

    // ---- On the wall, frame by frame -------------------------------------------------------

    private PaneWallLayout wall;
    private View widgets;
    private View terminal;

    private void build(@NonNull HomePageTransition effect) {
        // Animators at their real length, whatever the test runtime's developer setting is.
        try {
            android.animation.ValueAnimator.class.getMethod("setDurationScale", float.class)
                .invoke(null, 1f);
        } catch (ReflectiveOperationException ignored) {
            // Not there on this runtime: the stepping below still drives the ticker by hand.
        }
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        wall = new PaneWallLayout(activity);
        widgets = new FrameLayout(activity);
        terminal = new FrameLayout(activity);
        wall.addView(widgets);
        wall.addView(terminal);
        wall.setReducedMotion(false);
        wall.setPages(PaneWallPolicy.availablePages(false, true, false));
        wall.setPageView(PaneWallPage.WIDGETS, widgets);
        wall.setPageView(PaneWallPage.TERMINAL, terminal);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        wall.setPageTransition(effect);
        wall.goTo(PaneWallPage.TERMINAL, false);
    }

    /** The wall's running slide ticker, or null once it has ended. */
    private android.animation.ValueAnimator slideTicker() {
        try {
            java.lang.reflect.Field field = PaneWallLayout.class.getDeclaredField("mSlide");
            field.setAccessible(true);
            return (android.animation.ValueAnimator) field.get(wall);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** One page's drawn pose: scale, alpha, |turn|. */
    private static float[] poseOf(View page) {
        return new float[] {page.getScaleX(), page.getAlpha(), Math.abs(page.getRotationY())};
    }

    @Test
    public void homeEasesInFromTheWorkspaceUnderEveryEffect() {
        for (HomePageTransition effect : HomePageTransition.values()) {
            build(effect);
            wall.goTo(PaneWallPage.WIDGETS, true);
            List<float[]> home = new ArrayList<>();
            List<float[]> workspace = new ArrayList<>();
            // The slide's own ticker, stepped one 16 ms frame at a time: its update listener is
            // the wall's frame, exactly as Choreographer runs it on the phone.
            for (long playMs = 16L; playMs < 5000L && wall.isMoving(); playMs += 16L) {
                android.animation.ValueAnimator slide = slideTicker();
                if (slide == null) break;
                slide.setCurrentPlayTime(playMs);
                home.add(poseOf(widgets));
                workspace.add(poseOf(terminal));
            }
            String why = effect.name();
            assertTrue(why + " the slide ran over frames", home.size() > 5);
            assertTrue(why + " the slide ended", !wall.isMoving());
            // Home: scale and alpha never fall, the turn never grows, and it lands at the identity.
            for (int i = 1; i < home.size(); i++) {
                float[] a = home.get(i - 1);
                float[] b = home.get(i);
                String at = why + " home frame " + i;
                assertTrue(at + " scale " + a[0] + " -> " + b[0], b[0] >= a[0] - EPS);
                assertTrue(at + " alpha " + a[1] + " -> " + b[1], b[1] >= a[1] - EPS);
                assertTrue(at + " turn " + a[2] + " -> " + b[2], b[2] <= a[2] + EPS);
            }
            float[] landed = home.get(home.size() - 1);
            assertEquals(why, 1f, landed[0], 0f);
            assertEquals(why, 1f, landed[1], 0f);
            assertEquals(why, 0f, landed[2], 0f);
            assertEquals(why, 0f, widgets.getTranslationX(), 0f);
            // No pose is held across the slide: an effect that scales Home changes it between the
            // first frame and the last, and the step at the settle is no bigger than the slide's
            // largest step on the way.
            float largestStep = 0f;
            for (int i = 1; i < home.size() - 1; i++)
                largestStep = Math.max(largestStep, home.get(i)[0] - home.get(i - 1)[0]);
            float settleStep = landed[0] - home.get(home.size() - 2)[0];
            assertTrue(why + " settle step " + settleStep + " vs " + largestStep,
                settleStep <= largestStep + 0.02f);
            // The Workspace leaves the same way: never growing back, gone or flat at the end.
            for (int i = 1; i < workspace.size() - 1; i++) {
                float[] a = workspace.get(i - 1);
                float[] b = workspace.get(i);
                String at = why + " workspace frame " + i;
                // Parked off screen it is flat again, which is not a growth on screen.
                if (b[0] == 1f && b[2] == 0f) continue;
                assertTrue(at + " scale " + a[0] + " -> " + b[0], b[0] <= a[0] + EPS);
                assertTrue(at + " turn " + a[2] + " -> " + b[2], b[2] >= a[2] - EPS);
            }
        }
    }
}
