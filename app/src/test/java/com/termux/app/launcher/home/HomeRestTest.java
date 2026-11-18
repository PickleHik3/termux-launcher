package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;
import com.termux.app.place.PlaceKeyboard;
import com.termux.app.terminal.io.ExtraKeySetResolver;
import com.termux.app.terminal.io.ExtraKeysTarget;
import com.termux.app.wall.PaneWallPolicy;
import com.termux.app.wall.WallLookKinds;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

public class HomeRestTest {

    private static Rest.Inputs inputs(int bits) {
        return new Rest.Inputs.Builder()
            .keyboardRemembered((bits & 1) != 0)
            .minimal((bits & 2) != 0)
            .homeAlwaysOn((bits & 4) != 0)
            .overviewPreviewing((bits & 8) != 0)
            .inAppKeyboard((bits & 16) != 0)
            .floatAsksKeyboard((bits & 32) != 0)
            .floatFocused((bits & 64) != 0)
            .floatIsDisplay((bits & 128) != 0)
            .barShown((bits & 256) != 0)
            .build();
    }

    @Test
    public void everyInputCombinationMatchesToday() {
        for (int bits = 0; bits < 512; bits++) {
            Rest.Inputs in = inputs(bits);
            Rest rest = HomeRest.of(in);
            String at = in.toString();
            boolean today = !in.overviewPreviewing && (in.floatAsksKeyboard
                || PlaceKeyboard.keyboardOnEnter(PageKind.HOME, in.keyboardRemembered,
                    in.minimal, in.homeAlwaysOn));
            assertEquals(at, today, rest.keyboardUp);
            assertEquals(at,
                ExtraKeySetResolver.targetFor(PageKind.HOME, in.floatFocused,
                    in.floatIsDisplay),
                ExtraKeysTarget.forPage(rest.keys));
            assertTrue(at, rest.systemIme);
            assertEquals(at, in.barShown, rest.statusBar);
            assertEquals(at, PaneWallPolicy.showsWindowChips(PageKind.HOME),
                rest.windowChips);
            assertEquals(at, PageKind.HOME, rest.look);
        }
    }

    @Test
    public void theLookIsHomes() {
        assertEquals(LookGrid.Kind.HOME, WallLookKinds.of(PageKind.HOME));
    }

    @Test
    public void alwaysOnRaisesTheKeyboardWithAFloatFocusedOrNot() {
        for (boolean focused : new boolean[] {false, true}) {
            Rest on = HomeRest.of(new Rest.Inputs.Builder().homeAlwaysOn(true)
                .floatFocused(focused).build());
            Rest off = HomeRest.of(new Rest.Inputs.Builder().homeAlwaysOn(false)
                .floatFocused(focused).build());
            assertTrue(on.keyboardUp);
            assertFalse(off.keyboardUp);
            assertEquals(focused ? PageKind.TERMINAL : PageKind.HOME, on.keys);
        }
    }

    @Test
    public void theOverviewPreviewKeepsTheKeyboardDown() {
        Rest rest = HomeRest.of(new Rest.Inputs.Builder().homeAlwaysOn(true)
            .floatAsksKeyboard(true).overviewPreviewing(true).build());
        assertFalse(rest.keyboardUp);
    }
}
