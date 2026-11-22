package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;

import com.termux.R;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The Status bar card's Clock toggle: the pref, shown by default, and the slot's clock view. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P)
public class TopPaneWidgetSlotClockTest {

    @Test
    public void theClockPrefIsShownByDefaultAndKeepsItsValue() {
        TermuxAppSharedPreferences prefs =
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication());
        assertTrue(prefs.isStatusClockShown());
        prefs.setStatusClockShown(false);
        assertFalse(prefs.isStatusClockShown());
        prefs.setStatusClockShown(true);
        assertTrue(prefs.isStatusClockShown());
    }

    @Test
    public void aHiddenClockIsGoneFromTheSlotAndComesBack() {
        TopPaneWidgetSlot slot = new TopPaneWidgetSlot(RuntimeEnvironment.getApplication());
        TerminalClockWidget clock = new TerminalClockWidget(RuntimeEnvironment.getApplication(),
            null);
        clock.setId(R.id.terminal_clock_widget);
        slot.addView(clock);
        slot.onFinishInflate();
        assertTrue(slot.isClockShown());
        assertEquals(View.VISIBLE, clock.getVisibility());
        slot.setClockShown(false);
        assertEquals(View.GONE, clock.getVisibility());
        slot.setClockShown(true);
        assertEquals(View.VISIBLE, clock.getVisibility());
    }
}
