package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;
import com.termux.app.place.PlaceKeyboard;
import com.termux.app.terminal.io.ExtraKeySetResolver;
import com.termux.app.terminal.io.ExtraKeysTarget;
import com.termux.app.wall.WallLookKinds;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

public class AssistantRestTest {

    @Test
    public void everyInputCombinationMatchesToday() {
        for (int bits = 0; bits < 32; bits++) {
            boolean remembered = (bits & 1) != 0;
            boolean minimal = (bits & 2) != 0;
            boolean alwaysOn = (bits & 4) != 0;
            boolean previewing = (bits & 8) != 0;
            boolean inApp = (bits & 16) != 0;
            Rest rest = AssistantRest.of(new Rest.Inputs(remembered, minimal, alwaysOn, previewing, inApp));
            String at = "bits=" + bits;
            boolean today = !previewing
                && PlaceKeyboard.keyboardOnEnter(PageKind.ASSISTANT, remembered, minimal, alwaysOn);
            assertEquals(at, today, rest.keyboardUp);
            assertEquals(at, !inApp, rest.systemIme);
            assertEquals(at, PageKind.ASSISTANT, rest.keys);
            assertEquals(at,
                ExtraKeySetResolver.targetFor(PageKind.ASSISTANT, false, false),
                ExtraKeysTarget.forPage(rest.keys));
            assertTrue(at, rest.statusBar);
            assertTrue(at, rest.windowChips);
            assertEquals(at, PageKind.TERMINAL, rest.look);
        }
    }

    @Test
    public void theLookIsTheWorkspacesTerminalKind() {
        assertEquals(LookGrid.Kind.TERMINAL, WallLookKinds.of(PageKind.ASSISTANT));
        assertFalse(AssistantRest.of(new Rest.Inputs(true, false, false, false, true)).systemIme);
    }
}
