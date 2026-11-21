package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;
import com.termux.app.place.PlaceKeyboard;
import com.termux.app.terminal.io.ExtraKeySetResolver;
import com.termux.app.terminal.io.ExtraKeysTarget;
import com.termux.app.wall.PaneWallPolicy;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

public class TerminalPageRestTest {

    @Test
    public void everyInputCombinationMatchesToday() {
        for (int bits = 0; bits < 64; bits++) {
            boolean remembered = (bits & 1) != 0;
            boolean minimal = (bits & 2) != 0;
            boolean alwaysOn = (bits & 4) != 0;
            boolean previewing = (bits & 8) != 0;
            boolean inApp = (bits & 16) != 0;
            boolean bar = (bits & 32) != 0;
            Rest.Inputs in = new Rest.Inputs.Builder().keyboardRemembered(remembered)
                .minimal(minimal).homeAlwaysOn(alwaysOn).overviewPreviewing(previewing)
                .inAppKeyboard(inApp).barShown(bar).build();
            Rest rest = TerminalRest.of(in);
            String at = in.toString();
            assertEquals(at, !previewing && PlaceKeyboard.keyboardOnEnter(
                PageKind.TERMINAL, remembered, minimal, alwaysOn), rest.keyboardUp);
            assertEquals(at,
                ExtraKeySetResolver.targetFor(PageKind.TERMINAL, false, false),
                ExtraKeysTarget.forPage(rest.keys));
            assertEquals(at, PageKind.TERMINAL, rest.keys);
            assertEquals(at, !inApp, rest.systemIme);
            assertEquals(at, bar, rest.statusBar);
            assertEquals(at, PaneWallPolicy.showsWindowChips(PageKind.TERMINAL),
                rest.windowChips);
            assertEquals(at, PageKind.TERMINAL, rest.look);
        }
    }

    @Test
    public void theLookIsTheTerminalsTerminalKind() {
        assertTrue(TerminalRest.of(new Rest.Inputs.Builder().build()).windowChips);
    }
}
