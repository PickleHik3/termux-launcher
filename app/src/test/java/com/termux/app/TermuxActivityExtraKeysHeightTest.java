package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;

import com.termux.R;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.app.terminal.extrakeys.ExtraKeysBarView;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/**
 * The extra-keys row's height has one owner, the bar's committed page: a page of one row and a page
 * of two, flipped between, move the number the geometry pass reads.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityExtraKeysHeightTest {

    private static ExtraKeysInfo page(String rows) throws Exception {
        return new ExtraKeysInfo(rows, "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }

    @Test
    public void theRowHeightFollowsTheCommittedPage() throws Exception {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(activity);
        ReflectionHelpers.setField(activity, "mProperties", properties);
        ExtraKeysBarView bar = activity.getTerminalToolbarViewPager();
        int baseRowPx = bar.getLayoutParams().height;
        ReflectionHelpers.setField(activity, "mTerminalToolbarDefaultHeight", (float) baseRowPx);
        float scale = properties.getTerminalToolbarHeightScaleFactor();
        bar.setRowMetrics(baseRowPx, scale);
        bar.setKeySet(new ExtraKeysInfo[] {
            page("[['ESC','TAB']]"),
            page("[['ESC','TAB'],['CTRL','ALT']]")});
        bar.setTextInputPage(new View(activity));

        int oneRow = measured(activity);
        assertEquals(AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(baseRowPx, 1, scale),
            oneRow);

        bar.setCurrentItem(1, false);
        int twoRows = measured(activity);
        assertEquals(AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(baseRowPx, 2, scale),
            twoRows);
        assertTrue("two rows are taller than one", twoRows > oneRow);

        bar.setCurrentItem(2, false);
        assertEquals("the text input is one row", oneRow, measured(activity));

        bar.setCurrentItem(0, false);
        assertEquals("back on the one-row page", oneRow, measured(activity));
    }

    private static int measured(TermuxActivity activity) {
        return ReflectionHelpers.callInstanceMethod(activity, "measuredExtraKeysRowPx");
    }
}
