package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The run strip's minimised mark and its {@code +} (pass 3): a minimised float is marked whether
 * its entry shows a label or its number alone, and the {@code +} grows with the font scale inside
 * its badge.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class RunStripViewTest {

    private static final float EPS = 0.01f;

    /** Six row windows, then one Home float, minimised; {@code +} last (index 7). */
    private static List<RunStripModel.Entry> sixWindowsAndAMinimisedFloat() {
        List<WindowItem> chips = new ArrayList<>();
        for (int i = 0; i < 7; i++) chips.add(new WindowItem("home" + i, "home" + i));
        return RunStripModel.build(chips, 2, 6, -1, new boolean[]{true}, "New window");
    }

    private static RunStripView laidOut(int width) {
        RunStripView strip = new RunStripView(RuntimeEnvironment.getApplication());
        strip.setEntries(sixWindowsAndAMinimisedFloat());
        strip.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        strip.layout(0, 0, width, strip.getMeasuredHeight());
        return strip;
    }

    @Test
    public void numbersAlone_theMinimisedFloatStillCarriesItsMark() {
        RunStripView strip = laidOut(320);
        float density = RuntimeEnvironment.getApplication().getResources()
            .getDisplayMetrics().density;
        float bare = (RunStripView.ENTRY_START_PAD_DP * 2 + RunStripView.BADGE_DP) * density;
        float mark = (RunStripView.MINIMISED_MARK_DP + RunStripView.INNER_GAP_DP) * density;
        assertEquals(0, strip.labelOf(6).length());
        assertTrue(strip.marks(6));
        assertEquals(bare + mark, strip.entryWidth(6), EPS);
        assertFalse(strip.marks(0));
        assertEquals(bare, strip.entryWidth(0), EPS);
        assertFalse(strip.marks(7));
        assertEquals(bare, strip.entryWidth(7), EPS);
    }

    @Test
    public void withLabels_theMarkFollowsTheLabel() {
        RunStripView strip = laidOut(4000);
        assertTrue(strip.labelOf(6).length() > 0);
        assertTrue(strip.marks(6));
        assertFalse(strip.marks(5));
    }

    @Test
    public void plus_growsWithTheFontScale_heldInItsBadge() {
        float density = 2.625f;
        float badge = RunStripView.BADGE_DP * density;
        float label = RunStripView.LABEL_SP * density;
        float atOne = RunStripView.plusSizePx(label, badge);
        float atOneAndAHalf = RunStripView.plusSizePx(label * 1.5f, badge);
        assertEquals(label * RunStripView.PLUS_TO_LABEL, atOne, EPS);
        assertTrue(atOneAndAHalf > atOne);
        assertEquals(badge * RunStripView.PLUS_MAX_OF_BADGE, atOneAndAHalf, EPS);
        // Bigger than the old + character, which the badge face capped at 0.6 of the box's
        // number size (11 sp) and drew at well under its em.
        assertTrue(atOne > 11f * density * 0.6f);
    }
}
