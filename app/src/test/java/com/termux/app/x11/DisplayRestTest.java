package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;
import com.termux.app.place.PlaceKeyboard;
import com.termux.app.terminal.io.ExtraKeySetResolver;
import com.termux.app.terminal.io.ExtraKeysTarget;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.PaneWallPolicy;
import com.termux.app.wall.WallLookKinds;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

public class DisplayRestTest {

    @Test
    public void everyInputCombinationMatchesToday() {
        for (int bits = 0; bits < 256; bits++) {
            boolean remembered = (bits & 1) != 0;
            boolean minimal = (bits & 2) != 0;
            boolean previewing = (bits & 4) != 0;
            boolean inApp = (bits & 8) != 0;
            boolean android = (bits & 16) != 0;
            boolean running = (bits & 32) != 0;
            boolean mouse = (bits & 64) != 0;
            boolean bar = (bits & 128) != 0;
            Rest.Inputs in = new Rest.Inputs.Builder().keyboardRemembered(remembered)
                .minimal(minimal).overviewPreviewing(previewing).inAppKeyboard(inApp)
                .androidKeyboard(android).displayRunning(running).mouseMode(mouse)
                .barShown(bar).build();
            Rest rest = DisplayRest.of(in);
            String at = in.toString();
            boolean systemIme = DisplayKeyboardRoute.decide(android, true, running, inApp)
                == DisplayKeyboardRoute.Target.SYSTEM_IME;
            assertEquals(at, systemIme, rest.systemIme);
            boolean today = !previewing
                && PlaceKeyboard.keyboardOnEnter(PaneWallPage.DISPLAY, remembered, minimal, false)
                && !(systemIme && !(mouse && running && inApp));
            assertEquals(at, today, rest.keyboardUp);
            assertEquals(at,
                ExtraKeySetResolver.targetFor(PaneWallPage.DISPLAY, false, false),
                ExtraKeysTarget.forPage(rest.keys));
            assertEquals(at, bar, rest.statusBar);
            assertEquals(at, PaneWallPolicy.showsWindowChips(PaneWallPage.DISPLAY),
                rest.windowChips);
            assertEquals(at, PageKind.DISPLAY, rest.keys);
            assertEquals(at, PageKind.TERMINAL, rest.look);
        }
    }

    @Test
    public void theLookIsTheWorkspacesTerminalKind() {
        assertEquals(LookGrid.Kind.TERMINAL, WallLookKinds.of(WallPages.PageKind.DISPLAY));
        assertTrue(DisplayRest.of(new Rest.Inputs.Builder().build()).windowChips);
    }
}
