package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.github.mmin18.widget.RealtimeBlurView;
import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
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
     * Every live blur view left in the layout has a surface that turns it on. The sessions
     * drawer's, the dock's and the bottom space's were retired: the drawer never blurred, the dock
     * moved to RenderEffect, the bottom space never had a reader, and each still cost a per-frame
     * pre-draw callback on the decor for nothing.
     */
    @Test
    public void everyRemainingLiveBlurViewHasAReader() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        java.util.Set<Integer> expected = new java.util.HashSet<>(java.util.Arrays.asList(
            R.id.terminal_status_bar_glass_blur, R.id.terminal_window_bar_blur,
            R.id.app_drawer_blur, R.id.command_palette_blur, R.id.terminal_sheet_blur));
        java.util.Set<Integer> found = new java.util.HashSet<>();
        collectBlurViews(activity.findViewById(R.id.activity_termux_root_view), found);
        assertEquals(expected, found);
    }

    private static void collectBlurViews(View view, java.util.Set<Integer> out) {
        if (view instanceof RealtimeBlurView) out.add(view.getId());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectBlurViews(group.getChildAt(i), out);
        }
    }

}
