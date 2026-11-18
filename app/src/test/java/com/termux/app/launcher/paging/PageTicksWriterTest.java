package com.termux.app.launcher.paging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** The strip's one writer: what it is told reaches the bound strip, and only the bound one. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PageTicksWriterTest {

    private final Context context = RuntimeEnvironment.getApplication();

    private static PageTicks ticks(int pages, float position, int warm, boolean shown) {
        return new PageTicks(pages, position, warm, shown, false, 0, 0, 0xFF112233);
    }

    @Test
    public void theBoundStripIsToldPagesPositionWarmIndexAndVisibility() {
        PageTickStripView strip = new PageTickStripView(context);
        PageTicksWriter writer = new PageTicksWriter();
        writer.bind(strip);

        writer.write(ticks(3, 1.5f, 2, true));

        assertSame(strip, writer.bound());
        assertEquals(3, strip.getPageCount());
        assertEquals(1.5f, strip.getPagePosition(), 0.001f);
        assertEquals(2, strip.getDynamicPageIndex());
        assertEquals(View.VISIBLE, strip.getVisibility());
        assertEquals(0xFF112233, strip.getAccentColor());

        writer.write(ticks(1, 0f, -1, false));
        assertEquals(View.INVISIBLE, strip.getVisibility());
    }

    @Test
    public void bindingASecondStripResetsTheFirstAndWritesTheLastTicksToTheSecond() {
        PageTickStripView first = new PageTickStripView(context);
        PageTickStripView second = new PageTickStripView(context);
        PageTicksWriter writer = new PageTicksWriter();
        writer.bind(first);
        writer.write(ticks(4, 2f, -1, true));

        writer.bind(second);

        assertSame(second, writer.bound());
        assertEquals(1, first.getPageCount());
        assertEquals(View.INVISIBLE, first.getVisibility());
        assertEquals(4, second.getPageCount());
        assertEquals(2f, second.getPagePosition(), 0.001f);
        assertEquals(View.VISIBLE, second.getVisibility());
    }

    @Test
    public void bindingNullPutsTheStripDownAndLeavesNothingBound() {
        PageTickStripView strip = new PageTickStripView(context);
        PageTicksWriter writer = new PageTicksWriter();
        writer.bind(strip);
        writer.write(ticks(3, 1f, -1, true));
        writer.bind(null);
        assertNull(writer.bound());
        assertEquals(1, strip.getPageCount());
        assertEquals(View.INVISIBLE, strip.getVisibility());
    }
}
