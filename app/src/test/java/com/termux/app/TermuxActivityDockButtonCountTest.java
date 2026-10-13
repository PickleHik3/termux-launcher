package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * A count of apps picked in the editor shows on the dock at once. The row used to keep its old
 * slots until something else rendered it, so the new count appeared only after the next swipe.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityDockButtonCountTest {

    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 160;

    private ActivityController<Activity> window;
    private SuggestionBarView row;

    @Before
    public void setUp() {
        // In a window, so the row's renders are not queued behind an attach that never comes.
        window = Robolectric.buildActivity(Activity.class).setup();
        FrameLayout host = new FrameLayout(window.get());
        window.get().setContentView(host);
        row = new SuggestionBarView(window.get(), null);
        host.addView(row, new ViewGroup.LayoutParams(ROW_WIDTH, ROW_HEIGHT));
        row.measure(
            View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW_HEIGHT, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, ROW_WIDTH, ROW_HEIGHT);
        assertTrue(row.isAttachedToWindow());

        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < 9; i++)
            items.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        ReflectionHelpers.setField(row, "pinnedItems", items);
        row.setMaxButtonCount(3);
        row.reloadWithInput("", null);
        assertEquals("the row starts on three slots", 3, row.getColumnCount());
    }

    @After
    public void tearDown() {
        if (window != null) window.pause().stop().destroy();
    }

    @Test
    public void aNewCountRendersTheRowAtOnce() {
        TermuxActivity screen = Robolectric.buildActivity(TermuxActivity.class).get();
        ReflectionHelpers.setField(screen, "mSuggestionBarView", row);

        ReflectionHelpers.callInstanceMethod(screen, "applyDockButtonCountNow",
            ClassParameter.from(int.class, 5));

        assertEquals(5, row.getColumnCount());
    }
}
