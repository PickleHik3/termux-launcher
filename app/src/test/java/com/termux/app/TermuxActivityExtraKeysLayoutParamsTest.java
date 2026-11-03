package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityExtraKeysLayoutParamsTest {

    @Test
    public void updateExtraKeysBackgroundHeight_keepsRelativeLayoutParams() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        View extraKeysBackground = activity.findViewById(R.id.extrakeys_background);
        RelativeLayout rootRelativeLayout = activity.findViewById(R.id.activity_termux_root_relative_layout);

        assertNotNull(extraKeysBackground);
        assertNotNull(rootRelativeLayout);

        ViewGroup.LayoutParams backgroundLpBefore = extraKeysBackground.getLayoutParams();

        assertNotNull(backgroundLpBefore);

        // The point of this test is that the height update mutates the existing params in place
        // instead of swapping in a fresh generic instance, which would silently drop whatever
        // parent-specific positioning the accessory stack relies on. Pin the concrete type these
        // views actually have rather than naming a parent, so relocating the views in the layout
        // does not turn into a false failure here.
        Class<?> backgroundLpClass = backgroundLpBefore.getClass();

        int expectedHeight = 123;

        ReflectionHelpers.callInstanceMethod(activity, "updateExtraKeysBackgroundHeight",
                ReflectionHelpers.ClassParameter.from(View.class, extraKeysBackground),
                ReflectionHelpers.ClassParameter.from(int.class, expectedHeight));

        ViewGroup.LayoutParams backgroundLpAfter = extraKeysBackground.getLayoutParams();

        assertEquals(backgroundLpClass, backgroundLpAfter.getClass());
        assertEquals(expectedHeight, backgroundLpAfter.height);
        assertSame(backgroundLpBefore, backgroundLpAfter);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        rootRelativeLayout.measure(widthSpec, heightSpec);
        rootRelativeLayout.layout(0, 0, 1080, 1920);
    }

    /**
     * The status strip, the window bar, the off-dock plank and the A–Z host wear the Wallpaper
     * backdrop kind and nothing else: each glass sheet holds its frost and no view of its own
     * that captures the window. A capturing blur view cost a per-frame pre-draw callback on the
     * decor and a whole-window software draw.
     */
    @Test
    public void wallpaperKindBarsCarryOnlyTheirFrost() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        int[][] sheets = {
            {R.id.terminal_status_bar_background, R.id.terminal_status_bar_wallpaper_backdrop},
            {R.id.terminal_window_bar_host, R.id.terminal_window_bar_wallpaper_backdrop},
            {R.id.place_az_bar_host_glass, R.id.place_az_bar_host_frost},
            {R.id.place_off_dock_plank_glass, R.id.place_off_dock_plank_frost},
        };
        for (int[] sheet : sheets) {
            ViewGroup glass = activity.findViewById(sheet[0]);
            View frost = activity.findViewById(sheet[1]);
            assertTrue(frost instanceof android.widget.ImageView);
            assertSame(glass, frost.getParent());
            for (int i = 0; i < glass.getChildCount(); i++) {
                String name = glass.getChildAt(i).getClass().getName();
                assertFalse(name, name.contains("Blur"));
            }
        }
    }

}
