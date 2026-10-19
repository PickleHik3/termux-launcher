package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Home's pills as a phone lays them out, with real text metrics, at 1.0 and 1.3 font scale. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = android.app.Application.class,
    qualifiers = "w411dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomePillsViewTest {

    private static final List<HomePillsPolicy.Section> FLOATS = Arrays.asList(
        HomePillsPolicy.Section.of(" home", "home, Home float, page 1"),
        HomePillsPolicy.Section.of(" neovim", "neovim, Home float, page 1"),
        HomePillsPolicy.Section.of(" herdr", "herdr, Home float, page 2"));

    @After public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test public void theFocusedSectionIsWiderAndThePillsKeepApart() {
        HomePillsView pills = pills();
        HomePillsPolicy.Layout layout = pills.layoutNow();
        assertTrue(layout.windowsShown);
        assertTrue(layout.statsShown);
        assertTrue("the focused float shows its name", layout.nameRoom > 0f);
        assertTrue(width(layout, 1) > width(layout, 0));
        assertEquals(width(layout, 0), width(layout, 2), 0.5f);
        assertTrue(layout.windowsRight < layout.statsLeft);
        assertTrue(layout.pillTop >= 0 && layout.pillBottom <= pills.getHeight());
        assertTrue(layout.statsRight <= pills.getWidth());
    }

    @Test public void largeTextStillFitsTheBandAndTheScreen() {
        RuntimeEnvironment.setFontScale(1.3f);
        HomePillsView pills = pills();
        HomePillsPolicy.Layout layout = pills.layoutNow();
        assertTrue(layout.windowsShown);
        assertTrue(layout.windowsRight <= pills.getWidth());
        if (layout.statsShown) assertTrue(layout.windowsRight < layout.statsLeft);
        assertTrue(layout.pillBottom <= pills.getHeight());
    }

    @Test public void aTapOnASectionReportsItAndOffThePillsIsNotTaken() {
        HomePillsView pills = pills();
        List<Integer> taps = new ArrayList<>();
        pills.setListener(taps::add);
        HomePillsPolicy.Layout layout = pills.layoutNow();
        float y = (layout.pillTop + layout.pillBottom) / 2f;
        float x = (layout.sectionLeft[2] + layout.sectionRight[2]) / 2f;
        assertTrue(pills.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, x, y)));
        pills.dispatchTouchEvent(event(MotionEvent.ACTION_UP, x, y));
        assertEquals(Arrays.asList(2), taps);
        float gap = (layout.windowsRight + layout.statsLeft) / 2f;
        assertFalse(pills.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, gap, y)));
    }

    @Test public void theGlassClipIsThePillsAndEmptyWithNothingToShow() {
        HomePillsView pills = pills();
        Path path = new Path();
        pills.pillsPath(path);
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        HomePillsPolicy.Layout layout = pills.layoutNow();
        assertEquals(layout.windowsLeft, bounds.left, 0.5f);
        assertEquals(layout.statsRight, bounds.right, 0.5f);
        pills.setWindows(new ArrayList<>(), -1);
        pills.setStats(null, null, null, null, "");
        pills.pillsPath(path);
        assertTrue(path.isEmpty());
    }

    @Test public void aFloatClosingShrinksThePillAtOnce() {
        HomePillsView pills = pills();
        float before = pills.layoutNow().windowsRight;
        pills.setWindows(FLOATS.subList(0, 2), 1);
        assertEquals(2, pills.layoutNow().sectionCount());
        assertTrue(pills.layoutNow().windowsRight < before);
    }

    @Test public void eachPillStandsOnGlassOfItsOwnBuiltAtItsCornerAndNotPerFrame() {
        HomePillsView pills = pills();
        List<Float> radii = new ArrayList<>();
        pills.setGlassGround(radius -> {
            radii.add(radius);
            return new android.graphics.drawable.ColorDrawable(0x40ffffff);
        });
        assertTrue(pills.hasGlass());
        float radius = pills.layoutNow().radius;
        assertEquals("one glass per pill", Arrays.asList(radius, radius), radii);
        // A reading or a focus change moves the pills, not their corners: nothing is rebuilt.
        pills.setStats("13%", "48%", "", "21°", "CPU 13%, Memory 48%, Weather 21°");
        pills.setWindows(FLOATS, 0);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(pills.getWidth(),
            pills.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        pills.draw(new android.graphics.Canvas(bitmap));
        assertEquals(2, radii.size());
        pills.setGlassGround(null);
        assertFalse(pills.hasGlass());
    }

    private static HomePillsView pills() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        HomePillsView pills = new HomePillsView(activity);
        activity.setContentView(pills);
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(411 * density);
        int height = Math.round(32 * density);
        pills.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        pills.layout(0, 0, width, height);
        int inset = Math.round(16 * density);
        pills.setHorizontalInsets(inset, inset);
        pills.setAnimationsEnabled(false);
        pills.setWindows(FLOATS, 1);
        pills.setStats("12%", "48%", "", "21°", "CPU 12%, Memory 48%, Weather 21°");
        return pills;
    }

    private static MotionEvent event(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action, x, y, 0);
    }

    private static float width(HomePillsPolicy.Layout layout, int section) {
        return layout.sectionRight[section] - layout.sectionLeft[section];
    }
}
