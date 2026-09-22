package com.termux.app;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The dock's glass is as wide as the dock, in the frame the dock changes width.
 *
 * <p>Moving the apps row from a side back to the bottom gives the dock the rail's band back, and
 * the glass pass runs in that same frame — before the re-layout. Frozen to the width the parent
 * happened to measure last, the sheet stayed short by the band, and because it is left-aligned the
 * whole deficit showed on the right: a bare strip about an icon and a half wide.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityDockGlassWidthTest {

    private static final int WIDE = 1080;
    /** A rail's band, which is what the dock loses to an apps row standing on a side. */
    private static final int RAIL_BAND = 160;
    private static final int NARROW = WIDE - RAIL_BAND;
    private static final int STACK_HEIGHT = 220;

    private TermuxActivity activity;
    private ViewGroup container;
    private View glass;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity, false);
        assertNotNull(preferences);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);
        container = activity.findViewById(R.id.accessory_stack_container);
        glass = activity.findViewById(R.id.accessory_surface_host);
        assertNotNull(container);
        assertNotNull(glass);
        container.setVisibility(View.VISIBLE);
        glass.setVisibility(View.VISIBLE);
    }

    @Test
    public void theGlassSpansTheDockAfterTheAppsRowComesBackFromASide() {
        // The apps row is standing on a side: the dock is narrower by the rail's band.
        layoutContainer(NARROW);
        applyGlassBounds();
        layoutContainer(NARROW);
        assertSpansTheDock("with the row on a side");

        // Dropped back on the bottom. The glass pass runs in the same frame as the move, while
        // the container is still measured narrow; the re-layout that hands the dock its width
        // back comes after it.
        applyGlassBounds();
        layoutContainer(WIDE);
        assertSpansTheDock("after the row came back to the bottom");
    }

    @Test
    public void theGlassStillKeepsTheDocksSideMarginsAndTheBoundsItIsGiven() {
        layoutContainer(WIDE);
        Rect toolbarOnly = new Rect(0, 40, WIDE, 40 + 120);
        ReflectionHelpers.callInstanceMethod(activity, "applyAccessoryLayerBounds",
            ClassParameter.from(int.class, R.id.accessory_surface_host),
            ClassParameter.from(Rect.class, toolbarOnly));
        layoutContainer(WIDE);

        ViewGroup.MarginLayoutParams params = marginParams();
        assertEquals("the keyboard's toolbar-only band", 120, glass.getHeight());
        assertEquals(40, params.topMargin);
        assertEquals("the same margin on both sides", params.leftMargin, params.rightMargin);
        assertSpansTheDock("with explicit bounds");
    }

    private void layoutContainer(int width) {
        container.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(STACK_HEIGHT, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, width, STACK_HEIGHT);
    }

    private void applyGlassBounds() {
        ReflectionHelpers.callInstanceMethod(activity, "applyAccessoryLayerBounds",
            ClassParameter.from(int.class, R.id.accessory_surface_host),
            ClassParameter.from(Rect.class, null));
    }

    private ViewGroup.MarginLayoutParams marginParams() {
        return (ViewGroup.MarginLayoutParams) glass.getLayoutParams();
    }

    private void assertSpansTheDock(String when) {
        ViewGroup.MarginLayoutParams params = marginParams();
        assertEquals(when, container.getWidth() - params.leftMargin - params.rightMargin,
            glass.getWidth());
    }
}
