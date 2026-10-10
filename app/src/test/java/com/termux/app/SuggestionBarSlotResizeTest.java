package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.termux.app.launcher.icon.AsyncIconBinder;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The dock's icons follow its size (issue #46). The row was built at whatever size it had when it
 * rendered, and a new size from the dock only invalidated caches: the slots on screen kept their
 * old boxes until an unrelated reload, so a size drag moved the band and left the icons behind.
 * A new size now resizes the slots already built, in place, without rebuilding the row.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarSlotResizeTest {

    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 200;
    private static final int SMALL_ICON_PX = 60;
    private static final int LARGE_ICON_PX = 110;

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        AsyncIconBinder.setSynchronousForTesting(true);
    }

    @After
    public void tearDown() {
        AsyncIconBinder.setSynchronousForTesting(false);
    }

    private static List<LauncherAppEntry> entries(int count) {
        List<LauncherAppEntry> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
            out.add(new LauncherAppEntry(new AppRef("com.example.app" + i, "Main"), "App " + i, null));
        return out;
    }

    private static void measureAndLayout(SuggestionBarView bar) {
        bar.measure(View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW_HEIGHT, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, ROW_WIDTH, ROW_HEIGHT);
    }

    /** A row of four apps rendered at {@code iconPx}, as the dock hands it over. */
    private SuggestionBarView rowAt(int iconPx) {
        SuggestionBarView bar = new SuggestionBarView(context, null);
        bar.setMaxButtonCount(4);
        bar.setDockIconSizePx(iconPx);
        measureAndLayout(bar);
        ReflectionHelpers.callInstanceMethod(bar, "renderButtons",
            ClassParameter.from(List.class, entries(4)),
            ClassParameter.from(boolean.class, false));
        measureAndLayout(bar);
        return bar;
    }

    private static ImageButton iconOf(SuggestionBarView bar, int slot) {
        return (ImageButton) ((ViewGroup) bar.getChildAt(slot)).getChildAt(0);
    }

    private static boolean resizePosted(SuggestionBarView bar) {
        return ReflectionHelpers.getField(bar, "slotIconResizePosted");
    }

    private static void runResize(SuggestionBarView bar) {
        ReflectionHelpers.callInstanceMethod(bar, "resizeSlotIcons");
    }

    @Test
    public void aNewIconSizeResizesTheSlotsAlreadyBuilt() {
        SuggestionBarView bar = rowAt(SMALL_ICON_PX);
        assertEquals(4, bar.getChildCount());
        assertEquals(SMALL_ICON_PX, iconOf(bar, 0).getLayoutParams().width);
        View firstSlot = bar.getChildAt(0);

        bar.setDockIconSizePx(LARGE_ICON_PX);
        assertTrue("the dock's new size asks for the slots to follow", resizePosted(bar));
        runResize(bar);
        measureAndLayout(bar);

        assertSame("resized in place, not rebuilt", firstSlot, bar.getChildAt(0));
        for (int i = 0; i < bar.getChildCount(); i++) {
            ImageButton icon = iconOf(bar, i);
            assertEquals("slot " + i + " box", LARGE_ICON_PX, icon.getLayoutParams().width);
            assertEquals("slot " + i + " box", LARGE_ICON_PX, icon.getLayoutParams().height);
            assertEquals("slot " + i + " laid out", LARGE_ICON_PX, icon.getWidth());
            assertEquals(LARGE_ICON_PX, icon.getMinimumWidth());
        }
    }

    @Test
    public void shrinkingFollowsTooAndTheSameSizeIsLeftAlone() {
        SuggestionBarView bar = rowAt(LARGE_ICON_PX);
        bar.setDockIconSizePx(SMALL_ICON_PX);
        runResize(bar);
        ViewGroup.LayoutParams params = iconOf(bar, 0).getLayoutParams();
        assertEquals(SMALL_ICON_PX, params.width);

        // The same size again is no change at all: nothing is posted for it.
        ReflectionHelpers.setField(bar, "slotIconResizePosted", false);
        bar.setDockIconSizePx(SMALL_ICON_PX);
        assertFalse(resizePosted(bar));
        runResize(bar);
        assertSame(params, iconOf(bar, 0).getLayoutParams());
        assertEquals(SMALL_ICON_PX, params.width);
    }

    /** A rail's icons are the rail's own size: the dock's figure is remembered, not applied. */
    @Test
    public void aRailIsNotResizedByTheDocksFigure() {
        SuggestionBarView bar = rowAt(SMALL_ICON_PX);
        bar.setVerticalForm(true);
        ReflectionHelpers.setField(bar, "slotIconResizePosted", false);
        bar.setDockIconSizePx(LARGE_ICON_PX);
        assertFalse(resizePosted(bar));
    }
}
