package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;

import com.termux.R;
import com.termux.app.launcher.widget.WidgetPaneView;
import com.termux.app.page.PageKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class PageBarViewTest {

    private static PageBarModel model(int windows) {
        PageBarModel.Input in = new PageBarModel.Input();
        in.pages = Arrays.asList(
            new PageBarModel.Page(1, PageKind.HOME, null),
            new PageBarModel.Page(2, PageKind.TERMINAL, null),
            new PageBarModel.Page(3, PageKind.ASSISTANT, null));
        in.windowNames = new ArrayList<>();
        for (int i = 0; i < windows; i++) in.windowNames.add(null);
        in.currentWindow = windows == 0 ? -1 : 0;
        in.currentPageId = 1;
        return PageBarModel.build(in);
    }

    private static void layout(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static PageBarView bar(Activity activity) {
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        return new PageBarView(activity);
    }

    @Test public void oneLabelledButtonPerSlot() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PageBarView bar = bar(activity);
        layout(bar, 1080);
        bar.setModel(model(2));
        layout(bar, 1080);
        assertArrayEquals(new String[] {"Home 1", "Terminal 1", "Terminal 2", "Assistant"},
            bar.slotLabels());
    }

    @Test public void foldedSlotSpeaksKindAndCount() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PageBarView bar = bar(activity);
        // Room for three slots only, so the four windows fold into one.
        int narrow = Math.round((2 * PageBarLayout.SIDE_INSET_DP + 3 * PageBarLayout.MIN_SLOT_DP + 1)
            * activity.getResources().getDisplayMetrics().density);
        layout(bar, narrow);
        bar.setModel(model(4));
        layout(bar, narrow);
        assertArrayEquals(new String[] {"Home 1", "Terminal, 4 pages", "Assistant"},
            bar.slotLabels());
    }

    @Test public void paneBandIsThePageBarBand() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneView pane = new WidgetPaneView(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        View dots = pane.findViewById(R.id.widget_page_dots);
        // No dock reserve yet: the pill takes the dock's place, no air under it.
        assertEquals(PageBarBand.bandHeightPx(true, false, 0, density),
            dots.getLayoutParams().height);
        // With the dock away it keeps the dock's edge gap under the pill, and a gap alone moves it.
        pane.setDockReservePx(0, 17);
        assertEquals(PageBarBand.bandHeightPx(true, false, 17, density),
            dots.getLayoutParams().height);
        assertEquals(PageBarBand.bandHeightPx(true, false, 0, density) + 17,
            dots.getLayoutParams().height);
        pane.setDockReservePx(100, 17);
        assertEquals("over dock rows the air under the pill is its own",
            PageBarBand.bandHeightPx(true, true, 0, density), dots.getLayoutParams().height);
        assertEquals(100, ((android.widget.FrameLayout.LayoutParams) dots.getLayoutParams())
            .bottomMargin);
        pane.setKeyboardUp(true);
        assertEquals("a keyboard hides the pill, not the band", View.INVISIBLE,
            dots.getVisibility());
        assertEquals(PageBarBand.bandHeightPx(true, true, 0, density),
            dots.getLayoutParams().height);
        pane.setKeyboardUp(false);
        assertEquals(View.VISIBLE, dots.getVisibility());
        pane.setPageIndicator(com.termux.app.launcher.home.HomePageIndicator.OFF);
        assertEquals("off gives the band back", 0, dots.getLayoutParams().height);
    }
}
