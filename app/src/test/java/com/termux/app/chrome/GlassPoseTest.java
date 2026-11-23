package com.termux.app.chrome;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.app.launcher.home.HomePageTransition;
import com.termux.app.wall.WallPageTransition;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A slab's pose (#318): the page transition's Transform is its one owner, and the pose a slab's
 * draw reads back off the views it was applied as is the same pose, for every transition. A slide
 * and a fade are no pose at all; a turn and a page's scale are.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassPoseTest {

    private static final int WIDTH = 1080;

    /** A page host that, like the wall and Home's widget pane, answers its pages' transition scale. */
    private static final class Pages extends FrameLayout implements GlassPose.Pages {
        Pages(Context context) {
            super(context);
        }

        @Override
        public float glassPoseScale(@NonNull View page) {
            return page.getScaleX();
        }
    }

    /** The Transform applied to a page as WidgetPaneView and the wall apply it. */
    private static void apply(@NonNull View page, @NonNull HomePageTransition.Transform t) {
        page.setTranslationX(t.translationX);
        page.setScaleX(t.scale);
        page.setScaleY(t.scale);
        page.setAlpha(t.alpha);
        page.setRotationY(t.rotationY);
    }

    @Test
    public void theTransformAndTheViewsItWasAppliedAsGiveTheSamePoseForEveryTransition() {
        Context context = RuntimeEnvironment.getApplication();
        Pages host = new Pages(context);
        FrameLayout page = new FrameLayout(context);
        FrameLayout inner = new FrameLayout(context);
        View slab = new View(context);
        host.addView(page);
        page.addView(inner);
        inner.addView(slab);
        HomePageTransition.Transform t = new HomePageTransition.Transform();
        GlassPose owned = new GlassPose();
        GlassPose read = new GlassPose();
        for (HomePageTransition effect : HomePageTransition.values()) {
            for (boolean turning : new boolean[] {true, false}) {
                for (int i = -20; i <= 20; i++) {
                    float position = i / 20f;
                    effect.transform(position, WIDTH, turning, t);
                    apply(page, t);
                    GlassPose.of(t, owned);
                    GlassPose.read(slab, read);
                    String at = effect + " at " + position + (turning ? "" : " (band)");
                    assertTrue(at, owned.same(read));
                    // The wall poses its pages through the same effect.
                    WallPageTransition.pose(effect, position * WIDTH, WIDTH, turning, t);
                    apply(page, t);
                    GlassPose.of(t, owned);
                    GlassPose.read(slab, read);
                    assertTrue("wall " + at, owned.same(read));
                }
            }
        }
    }

    @Test
    public void slideAndFadeAreNoPoseAndTheTurnsAndScalesAre() {
        HomePageTransition.Transform t = new HomePageTransition.Transform();
        GlassPose pose = new GlassPose();
        for (float position : new float[] {-0.7f, -0.3f, 0.3f, 0.7f}) {
            HomePageTransition.SLIDE.transform(position, WIDTH, true, t);
            GlassPose.of(t, pose);
            assertTrue("slide " + position, pose.isRest());
            HomePageTransition.FADE.transform(position, WIDTH, true, t);
            GlassPose.of(t, pose);
            assertTrue("fade " + position, pose.isRest());
            HomePageTransition.CAROUSEL.transform(position, WIDTH, true, t);
            GlassPose.of(t, pose);
            assertEquals((float) Math.sin(Math.toRadians(position * 60f)), pose.tiltX, 1e-6f);
            assertEquals(0f, pose.tiltY, 0f);
            assertEquals(1f, pose.scale, 0f);
        }
        HomePageTransition.DEPTH.transform(-1f, WIDTH, true, t);
        GlassPose.of(t, pose);
        assertEquals(0.75f, pose.scale, 1e-6f);
        assertEquals(0f, pose.tiltX, 0f);
        HomePageTransition.STACK.transform(1f, WIDTH, true, t);
        GlassPose.of(t, pose);
        assertEquals(0.92f, pose.scale, 1e-6f);
        // Every transition at rest is the rest pose.
        for (HomePageTransition effect : HomePageTransition.values()) {
            effect.transform(0f, WIDTH, true, t);
            GlassPose.of(t, pose);
            assertTrue(effect + " at rest", pose.isRest());
        }
    }

    @Test
    public void aScaleNoTransitionGaveIsNotAPose() {
        // The Appearance editor holds the whole window small at rest; the glass keeps its look.
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(context);
        FrameLayout page = new FrameLayout(context);
        View slab = new View(context);
        root.addView(page);
        page.addView(slab);
        root.setScaleX(0.8f);
        root.setScaleY(0.8f);
        page.setScaleX(0.8f);
        GlassPose pose = new GlassPose();
        GlassPose.read(slab, pose);
        assertTrue(pose.isRest());
        // A turn anywhere on the way up is one: the press rig on a pane's frame, the plank.
        slab.setRotationX(1.1f);
        page.setRotationY(6f);
        GlassPose.read(slab, pose);
        assertEquals((float) Math.sin(Math.toRadians(6f)), pose.tiltX, 1e-6f);
        assertEquals((float) -Math.sin(Math.toRadians(1.1f)), pose.tiltY, 1e-6f);
    }

    @Test
    public void theTiltPointsAtTheEdgeGoingAway() {
        float[] t = new float[2];
        // The plank's rotationY > 0 takes the right edge away.
        GlassPose.tiltVector(0f, 6f, t);
        assertEquals((float) Math.sin(Math.toRadians(6f)), t[0], 1e-6f);
        assertEquals(0f, t[1], 0f);
        // The press rig's rotationX > 0 takes the top edge away.
        GlassPose.tiltVector(6f, 0f, t);
        assertEquals(0f, t[0], 0f);
        assertTrue(t[1] < 0f);
    }

    @Test
    public void aTurnUnderTheRestOrAScaleNearOneIsExactlyRest() {
        float[] t = new float[2];
        GlassPose.tiltVector(0.02f, -0.03f, t);
        assertEquals(0f, t[0], 0f);
        assertEquals(0f, t[1], 0f);
        GlassPose.tiltVector(Float.NaN, 3f, t);
        assertEquals(0f, t[0], 0f);
        assertEquals(0f, t[1], 0f);
        GlassPose.tiltVector(0f, GlassPose.TILT_REST_DEG * 2f, t);
        assertTrue(t[0] > 0f);
        GlassPose pose = new GlassPose();
        GlassPose.fromViewProperties(0.02f, 0.03f, 1f + GlassPose.SCALE_REST / 2f, pose);
        assertTrue(pose.isRest());
        GlassPose.fromViewProperties(0f, 0f, Float.NaN, pose);
        assertTrue(pose.isRest());
        GlassPose.fromViewProperties(0f, 0f, 0.9f, pose);
        assertFalse(pose.isRest());
        pose.reset();
        assertTrue(pose.isRest());
    }
}
