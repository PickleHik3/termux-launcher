package com.termux.app.statusbar;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;

import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * The column throws every chip away and inflates a new one whenever it is handed a window list.
 * A shell producing output hands it one several times a second, and almost every one of those
 * carries the windows exactly as they already are — so an unchanged list is dropped instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarWindowColumnRebuildTest {

    @Test
    public void unchangedWindowsLeaveTheChipsExactlyWhereTheyWere() {
        StatusBarWindowColumn column = column();
        column.setWindows(windows(false), 0);
        LinearLayout stack = (LinearLayout) column.getChildAt(0);
        assertEquals(2, stack.getChildCount());
        View first = stack.getChildAt(0);
        View second = stack.getChildAt(1);

        // The same windows, as a fresh list of fresh items: this is what a burst of output sends.
        column.setWindows(windows(false), 0);

        assertSame(first, stack.getChildAt(0));
        assertSame(second, stack.getChildAt(1));
    }

    @Test
    public void aChangeTheUserCanSeeStillRebuilds() {
        StatusBarWindowColumn column = column();
        column.setWindows(windows(false), 0);
        LinearLayout stack = (LinearLayout) column.getChildAt(0);
        View second = stack.getChildAt(1);

        // Working: the chip's rim is the only thing carrying it, so it has to be re-dressed.
        column.setWindows(windows(true), 0);
        assertNotSame(second, stack.getChildAt(1));

        // The selection moving is a change too, even with the same windows.
        View reselected = stack.getChildAt(1);
        column.setWindows(windows(true), 1);
        assertNotSame(reselected, stack.getChildAt(1));

        // And so is a window opening.
        column.setWindows(Arrays.asList(new WindowItem("home", "home"),
            new WindowItem("work", "work").withBusy(true),
            new WindowItem("logs", "logs")), 1);
        assertEquals(3, stack.getChildCount());
    }

    /** The first list is never a repeat of the empty one the column starts with. */
    @Test
    public void theFirstListAlwaysLands() {
        StatusBarWindowColumn column = column();
        column.setWindows(Arrays.asList(new WindowItem("home", "home")), -1);
        LinearLayout stack = (LinearLayout) column.getChildAt(0);
        assertEquals(1, stack.getChildCount());
    }

    private static List<WindowItem> windows(boolean busy) {
        return Arrays.asList(new WindowItem("home", "home"),
            new WindowItem("work", "work").withBusy(busy));
    }

    private static StatusBarWindowColumn column() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        return new StatusBarWindowColumn(activity, null);
    }
}
