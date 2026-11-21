package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.launcher.paging.PageTicks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/** The apps row exposes its ticks as a value; it never touches a strip itself. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarPageTicksTest {

    private final Context context = RuntimeEnvironment.getApplication();

    private SuggestionBarView rowWith(int pins) {
        SuggestionBarView row = new SuggestionBarView(context, null);
        row.setDockIconSizePx(Math.round(24f * row.getResources().getDisplayMetrics().density));
        row.addView(new View(context), new ViewGroup.LayoutParams(360, 160));
        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < pins; i++) {
            items.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        }
        ReflectionHelpers.setField(row, "pinnedItems", items);
        ReflectionHelpers.setField(row, "maxButtonCount", 3);
        ReflectionHelpers.setField(row, "pinnedItemsPerPage", 3);
        return row;
    }

    @Test
    public void aRowWithOverflowPinnedPagesHandsItsListenerTicksThatAreShown() {
        SuggestionBarView row = rowWith(9);
        List<PageTicks> heard = new ArrayList<>();
        row.setPageTicksListener(heard::add);

        row.publishPageTicks();

        assertFalse(heard.isEmpty());
        PageTicks last = heard.get(heard.size() - 1);
        assertTrue("pages " + last.pages, last.pages > 1);
        assertTrue(last.shown);
        assertEquals(last, row.pageTicks());
    }

    @Test
    public void aRowWithOnePageHandsShownFalse() {
        SuggestionBarView row = rowWith(2);
        List<PageTicks> heard = new ArrayList<>();
        row.setPageTicksListener(heard::add);

        row.publishPageTicks();

        PageTicks last = heard.get(heard.size() - 1);
        assertNotNull(last);
        assertEquals(1, last.pages);
        assertFalse(last.shown);
    }
}
