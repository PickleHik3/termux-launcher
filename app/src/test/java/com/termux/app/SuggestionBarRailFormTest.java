package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

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
import static org.junit.Assert.assertTrue;

/**
 * The rail is the pinned-apps row standing up: the same view, the same entries, turned on its
 * side. What that has to produce is a column of icons at a fixed pitch — the one
 * {@code DockLayoutPolicy} gives the rail — rather than a row of slots sharing a width.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarRailFormTest {

    private static final int RAIL_WIDTH = 160;
    private static final int RAIL_HEIGHT = 1200;
    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 160;

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
    }

    private static List<LauncherAppEntry> entries(int count) {
        List<LauncherAppEntry> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
            out.add(new LauncherAppEntry(new AppRef("com.example.app" + i, "Main"), "App " + i, null));
        return out;
    }

    private SuggestionBarView render(boolean vertical, int count, int width, int height) {
        SuggestionBarView bar = new SuggestionBarView(context, null);
        bar.setVerticalForm(vertical);
        bar.setMaxButtonCount(count);
        bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, width, height);
        ReflectionHelpers.callInstanceMethod(bar, "renderButtons",
            ClassParameter.from(List.class, entries(count)),
            ClassParameter.from(boolean.class, false));
        bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, width, height);
        return bar;
    }

    @Test
    public void theRailIsOneColumnOfSlots() {
        SuggestionBarView rail = render(true, 4, RAIL_WIDTH, RAIL_HEIGHT);
        assertEquals(1, rail.getColumnCount());
        assertEquals(4, rail.getRowCount());
        assertEquals(4, rail.getChildCount());
    }

    @Test
    public void railIconsStandAtTheRailsOwnPitchDownTheColumn() {
        SuggestionBarView rail = render(true, 4, RAIL_WIDTH, RAIL_HEIGHT);
        float density = context.getResources().getDisplayMetrics().density;
        int pitch = DockLayoutPolicy.railSlotLengthPx(density);
        assertTrue("a slot has to be worth measuring", pitch > 0);
        int firstTop = rail.getChildAt(0).getTop();
        for (int i = 0; i < rail.getChildCount(); i++) {
            View slot = rail.getChildAt(i);
            assertEquals("slot " + i + " top", firstTop + i * pitch, slot.getTop());
            assertEquals("slot " + i + " height", pitch, slot.getHeight());
            // One column: every icon starts at the same offset across the rail.
            assertEquals("slot " + i + " left", rail.getChildAt(0).getLeft(), slot.getLeft());
        }
    }

    @Test
    public void theRowStillLiesDownAndSharesItsWidth() {
        SuggestionBarView row = render(false, 4, ROW_WIDTH, ROW_HEIGHT);
        assertEquals(4, row.getColumnCount());
        assertEquals(1, row.getRowCount());
        int firstTop = row.getChildAt(0).getTop();
        for (int i = 1; i < row.getChildCount(); i++) {
            assertEquals("slot " + i + " top", firstTop, row.getChildAt(i).getTop());
            assertTrue("slot " + i + " is further along the row",
                row.getChildAt(i).getLeft() > row.getChildAt(i - 1).getLeft());
        }
    }

    @Test
    public void theFormIsTheSameViewTurned() {
        SuggestionBarView bar = new SuggestionBarView(context, null);
        bar.setVerticalForm(true);
        assertTrue(bar.isVerticalForm());
        bar.setVerticalForm(false);
        assertEquals(false, bar.isVerticalForm());
    }

    @Test
    public void aRailWithNothingPinnedClaimsNothing() {
        SuggestionBarView bar = new SuggestionBarView(context, null);
        ViewGroup.LayoutParams unused = bar.getLayoutParams();
        assertEquals(null, unused);
        assertEquals(false, bar.hasPinnedItems());
    }
}
