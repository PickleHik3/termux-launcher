package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/** The reorderable list: where a drag lands, the order it shows, and the switches it holds. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, application = Application.class)
public class ReorderListTest {

    @Test
    public void aDragLandsOnThePlaceWhoseMiddleItPassed() {
        assertEquals(0, ReorderList.dropPlace(0, 20f, 100, 4));
        assertEquals(1, ReorderList.dropPlace(0, 60f, 100, 4));
        assertEquals(3, ReorderList.dropPlace(1, 240f, 100, 4));
        assertEquals("past the end stays on the last place", 3,
            ReorderList.dropPlace(1, 900f, 100, 4));
        assertEquals("past the top stays on the first", 0, ReorderList.dropPlace(2, -900f, 100, 4));
        assertEquals("unmeasured: where it was", 2, ReorderList.dropPlace(2, 60f, 0, 4));
    }

    @Test
    public void theListShowsItsOrderAndKeepsTheSwitchRoomForAnEntryWithout() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        List<ReorderList.Entry> entries = Arrays.asList(
            new ReorderList.Entry(R.drawable.ic_rl_cpu, "A", true),
            new ReorderList.Entry(R.drawable.ic_rl_cpu, "B", true),
            new ReorderList.Entry(R.drawable.ic_rl_cpu, "C", false));
        boolean[] toggled = new boolean[3];
        ReorderList list = ReorderList.build(context, entries, new int[] {2, 0, 1},
            new boolean[] {true, false, false}, (from, to) -> {},
            (entry, on) -> toggled[entry] = on);
        assertArrayEquals(new int[] {2, 0, 1}, list.shownOrder());
        assertTrue(list.hasSwitch(0));
        assertFalse("C shows on its own: its switch room stays empty", list.hasSwitch(2));

        list.setOrder(new int[] {0, 1, 2});
        assertArrayEquals(new int[] {0, 1, 2}, list.shownOrder());

        list.switchOf(1).performClick();
        assertTrue(toggled[1]);
        // A restate is not reported.
        list.setOn(0, false);
        assertFalse(list.switchOf(0).isChecked());
        assertFalse(toggled[0]);
    }
}
