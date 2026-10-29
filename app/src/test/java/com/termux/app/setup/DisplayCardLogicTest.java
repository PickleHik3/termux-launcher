package com.termux.app.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.tour.TourEdition;
import com.termux.app.x11.GuiAppsSetup;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class DisplayCardLogicTest {

    @Test
    public void nixOffersNoRoute() {
        assertTrue(DisplayCardLogic.offeredRoutes(TourEdition.NIX).isEmpty());
        assertNull(DisplayCardLogic.resolveRoute("x11", Collections.<GuiAppsSetup.Route>emptyList()));
    }

    @Test
    public void distroIsHeldBackWhileItAsksQuestions() {
        List<GuiAppsSetup.Route> routes = DisplayCardLogic.offeredRoutes(TourEdition.TERMUX);
        assertEquals(DisplayCardLogic.DISTRO_RUNNABLE, routes.contains(GuiAppsSetup.Route.DISTRO));
        assertTrue(routes.contains(GuiAppsSetup.Route.X11_REPO));
    }

    @Test
    public void storedRouteFallsBackToFirstOffered() {
        List<GuiAppsSetup.Route> offered = Collections.singletonList(GuiAppsSetup.Route.X11_REPO);
        assertEquals(GuiAppsSetup.Route.X11_REPO, DisplayCardLogic.resolveRoute("distro", offered));
        assertEquals(GuiAppsSetup.Route.X11_REPO, DisplayCardLogic.resolveRoute(null, offered));
    }

    @Test
    public void commandIsTheGuidesCommandWithDefaultStarters() {
        String command = DisplayCardLogic.command(GuiAppsSetup.Route.X11_REPO,
            GuiAppsSetup.Distro.DEBIAN, TourEdition.TERMUX);
        assertEquals(GuiAppsSetup.command(GuiAppsSetup.Route.X11_REPO, GuiAppsSetup.Distro.DEBIAN,
            GuiAppsSetup.defaultStarters(), TourEdition.TERMUX), command);
        assertTrue(command.contains("pkg install -y x11-repo"));
        assertTrue(DisplayCardLogic.script(command).startsWith("#!/bin/bash\npkg install"));
        assertTrue(DisplayCardLogic.script(command).endsWith("\n"));
    }

    @Test
    public void successNeedsExitZeroAndTheMarker() {
        assertTrue(DisplayCardLogic.succeeded(0, true));
        assertFalse(DisplayCardLogic.succeeded(0, false));
        assertFalse(DisplayCardLogic.succeeded(1, true));
    }

    @Test
    public void progressLineIsTheLastNonEmptyLineTrimmed() {
        assertEquals("Setting up xfce4 (4.18)", DisplayCardLogic.progressLine(
            "Reading package lists\n  Setting up xfce4 (4.18)  \n\n\n"));
        assertEquals("", DisplayCardLogic.progressLine("\n  \n"));
        assertEquals("", DisplayCardLogic.progressLine(null));
    }

    @Test
    public void progressLineKeepsWhatAReturnLeftLast() {
        assertEquals("50%", DisplayCardLogic.progressLine("10%\r30%\r50%"));
        assertEquals("100%", DisplayCardLogic.progressLine("10%\r100%\r"));
    }

    @Test
    public void longProgressLineEndsInAnEllipsis() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 200; i++) text.append('a');
        String line = DisplayCardLogic.progressLine(text.toString());
        assertEquals(DisplayCardLogic.PROGRESS_MAX_CHARS, line.length());
        assertTrue(line.endsWith("…"));
    }

    @Test
    public void lastLinesDropsThePaddingAndKeepsTheTail() {
        assertEquals("c\nd", DisplayCardLogic.lastLines("a\nb\nc\nd\n\n\n", 2));
        assertEquals("a\nb", DisplayCardLogic.lastLines("a\nb\n", 10));
        assertEquals("", DisplayCardLogic.lastLines("", 5));
    }
}
