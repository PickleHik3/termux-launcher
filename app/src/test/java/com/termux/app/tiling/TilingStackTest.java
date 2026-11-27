package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The tiling desktop's stack (#415), through its public interface only. */
public class TilingStackTest {

    private static final WindowSpec SHELL = WindowSpec.shell("/home");

    // ---- places and the waiting Workspace -------------------------------------------------------

    @Test
    public void firstStartIsHomeThenWorkspaceOneWithOneTerminalAndTheWaitingOne() {
        TilingStack stack = TilingStack.firstStart(2, "/data/home");

        assertEquals(Arrays.asList(Place.home(0), Place.home(1), Place.workspace(1), Place.workspace(2)),
            stack.places());
        assertEquals(Place.home(0), stack.current());
        assertEquals(2, stack.workspaceCount());
        TilingWorkspace one = stack.workspace(1);
        assertEquals(1, one.windows().size());
        TilingWindow shell = one.windows().get(0);
        assertEquals(WindowKind.SHELL, shell.kind());
        assertEquals("/data/home", shell.workingDirectory());
        assertTrue(stack.workspace(2).isEmpty());
    }

    @Test
    public void withoutHomeTheStackStartsAtOne() {
        TilingStack stack = TilingStack.firstStart(0, "/home");

        assertEquals(Arrays.asList(Place.workspace(1), Place.workspace(2)), stack.places());
        assertEquals(Place.workspace(1), stack.current());
        assertFalse(stack.home());
        assertEquals(Place.workspace(1), stack.current());
    }

    @Test
    public void exactlyOneEmptyWorkspaceWaitsAtTheEndAsWindowsComeAndGo() {
        TilingStack stack = TilingStack.empty(1);
        assertWaitingAtEndOnly(stack);
        assertEquals(1, stack.workspaceCount());

        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        assertWaitingAtEndOnly(stack);
        assertEquals(2, stack.workspaceCount());

        stack.goTo(Place.workspace(2));
        long b = stack.open(SHELL);
        stack.open(SHELL);
        assertWaitingAtEndOnly(stack);
        assertEquals(3, stack.workspaceCount());

        stack.close(a);
        assertWaitingAtEndOnly(stack);
        assertEquals(2, stack.workspaceCount());
        assertEquals(2, stack.workspace(1).windows().size());

        stack.close(b);
        stack.close(stack.workspace(1).windows().get(0).id());
        assertWaitingAtEndOnly(stack);
        assertEquals(1, stack.workspaceCount());
    }

    @Test
    public void aWorkspaceThatEmptiesInTheMiddleGoesAndTheOnesBelowRenumber() {
        TilingStack stack = TilingStack.empty(1);
        long a = openInto(stack, 1);
        long b = openInto(stack, 2);
        long c = openInto(stack, 3);
        stack.renameWorkspace(3, "mail");

        stack.close(b);

        assertEquals(3, stack.workspaceCount());
        assertEquals(Place.workspace(1), stack.placeOf(a));
        assertEquals(Place.workspace(2), stack.placeOf(c));
        assertEquals("mail", stack.workspace(2).name());
        assertEquals(2, stack.workspace(2).number());
    }

    @Test
    public void closingTheLastWindowOfTheCurrentWorkspaceLandsOnTheOneThatTakesItsNumber() {
        TilingStack stack = TilingStack.empty(1);
        openInto(stack, 1);
        long b = openInto(stack, 2);
        long c = openInto(stack, 3);
        stack.goTo(Place.workspace(2));

        stack.close(b);

        assertEquals(Place.workspace(2), stack.current());
        assertEquals(c, stack.focusedWindowId());
    }

    @Test
    public void closingInAnotherWorkspaceKeepsTheCurrentOneInFront() {
        TilingStack stack = TilingStack.empty(1);
        long a = openInto(stack, 1);
        long b = openInto(stack, 2);
        stack.goTo(Place.workspace(2));

        stack.close(a);

        assertEquals(Place.workspace(1), stack.current());
        assertEquals(b, stack.focusedWindowId());
    }

    // ---- windows ---------------------------------------------------------------------------------

    @Test
    public void eachWindowHasAKindAndOneOwningPlace() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long shell = stack.open(WindowSpec.shell("/tmp"));
        long app = stack.open(WindowSpec.linuxApp("org.mozilla.firefox", "Firefox"));
        long display = stack.open(WindowSpec.display());
        long assistant = stack.open(WindowSpec.assistant());

        assertEquals(WindowKind.SHELL, stack.window(shell).kind());
        assertEquals(WindowKind.LINUX_APP, stack.window(app).kind());
        assertEquals("org.mozilla.firefox", stack.window(app).appId());
        assertEquals(WindowKind.DISPLAY, stack.window(display).kind());
        assertEquals(WindowKind.ASSISTANT, stack.window(assistant).kind());
        for (long id : new long[] {shell, app, display, assistant})
            assertEquals(Place.workspace(1), stack.placeOf(id));
        assertNull(stack.window(9999));
        assertNull(stack.placeOf(9999));
    }

    @Test
    public void aWindowOpensAfterTheFocusedOneAndTakesFocus() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long c = stack.open(SHELL);
        stack.focusWindow(a);

        long b = stack.open(SHELL);

        assertEquals(Arrays.asList(a, b, c), idsOf(stack.workspace(1).windows()));
        assertEquals(b, stack.focusedWindowId());
    }

    @Test
    public void openingFromHomeLandsInTheWaitingWorkspaceAndGoesThere() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        assertEquals(Place.home(0), stack.current());

        long id = stack.open(SHELL);

        assertEquals(Place.workspace(2), stack.placeOf(id));
        assertEquals(Place.workspace(2), stack.current());
        assertWaitingAtEndOnly(stack);
    }

    @Test
    public void aFloatOnAHomePageBelongsToThatPage() {
        TilingStack stack = TilingStack.empty(2);

        long id = stack.openIn(Place.home(1), WindowSpec.assistant(), true);

        assertEquals(Place.home(1), stack.placeOf(id));
        assertTrue(stack.window(id).isFloat());
        assertEquals(Arrays.asList(id), idsOf(stack.homeFloats(1)));
        assertTrue(stack.homeFloats(0).isEmpty());
        assertWaitingAtEndOnly(stack);
    }

    @Test
    public void aHomePageOnlyHoldsFloats() {
        TilingStack stack = TilingStack.empty(1);
        long id = stack.openIn(Place.home(0), SHELL, false);
        assertTrue(stack.window(id).isFloat());
    }

    @Test
    public void floatsOnAHomePageThatGoesMoveToTheLastPageLeft() {
        TilingStack stack = TilingStack.empty(3);
        long id = stack.openIn(Place.home(2), WindowSpec.assistant(), true);
        stack.goTo(Place.home(2));

        stack.setHomePageCount(2);
        assertEquals(Place.home(1), stack.placeOf(id));
        assertEquals(Place.home(1), stack.current());

        stack.setHomePageCount(0);
        assertEquals(Place.workspace(1), stack.placeOf(id));
        assertTrue(stack.window(id).isFloat());
        assertEquals(Place.workspace(1), stack.current());
        assertWaitingAtEndOnly(stack);
    }

    @Test
    public void aWindowMovesToAnotherPlace() {
        TilingStack stack = TilingStack.empty(1);
        long a = openInto(stack, 1);
        long b = openInto(stack, 1);

        stack.moveWindow(b, Place.workspace(2));

        assertEquals(Place.workspace(2), stack.placeOf(b));
        assertEquals(Arrays.asList(a), idsOf(stack.workspace(1).windows()));
        assertWaitingAtEndOnly(stack);
        assertEquals(3, stack.workspaceCount());

        stack.moveWindow(a, Place.home(0));
        assertEquals(Place.home(0), stack.placeOf(a));
        assertTrue(stack.window(a).isFloat());
        assertEquals(Place.workspace(1), stack.placeOf(b));
        assertWaitingAtEndOnly(stack);
    }

    @Test
    public void floatsSitInTheirWorkspaceAndPinIsAFlag() {
        TilingStack stack = TilingStack.empty(1);
        long a = openInto(stack, 1);
        long b = openInto(stack, 1);

        stack.setFloat(b, true);
        stack.setPinned(b, true);

        TilingWorkspace one = stack.workspace(1);
        assertEquals(Arrays.asList(a), idsOf(one.tiledWindows()));
        assertEquals(Arrays.asList(b), idsOf(one.floats()));
        assertTrue(stack.window(b).isPinned());
        assertEquals(Arrays.asList(b), idsOf(stack.pinnedFloats()));

        stack.setFloat(b, false);
        assertFalse(stack.window(b).isPinned());
        assertEquals(Arrays.asList(a, b), idsOf(stack.workspace(1).tiledWindows()));
    }

    // ---- focus -----------------------------------------------------------------------------------

    @Test
    public void focusMovesSidewaysWithinAWorkspace() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        long c = stack.open(SHELL);

        assertFalse(stack.focusRight());
        assertTrue(stack.focusLeft());
        assertEquals(b, stack.focusedWindowId());
        assertTrue(stack.focusLeft());
        assertEquals(a, stack.focusedWindowId());
        assertFalse(stack.focusLeft());
        assertEquals(a, stack.focusedWindowId());
        assertTrue(stack.focusRight());
        assertEquals(b, stack.focusedWindowId());
        assertEquals(Place.workspace(1), stack.current());
        assertNotNull(stack.window(c));
    }

    @Test
    public void focusMovesUpAndDownBetweenPlacesAndRemembersEachWorkspacesWindow() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long one = stack.workspace(1).windows().get(0).id();
        long two = openInto(stack, 2);
        long twoB = openInto(stack, 2);
        stack.focusWindow(two);

        stack.goTo(Place.home(0));
        assertTrue(stack.focusDown());
        assertEquals(Place.workspace(1), stack.current());
        assertEquals(one, stack.focusedWindowId());
        assertTrue(stack.focusDown());
        assertEquals(Place.workspace(2), stack.current());
        assertEquals(two, stack.focusedWindowId());
        assertTrue(stack.focusDown());
        assertEquals(Place.workspace(3), stack.current());
        assertEquals(TilingStack.NO_WINDOW, stack.focusedWindowId());
        assertFalse(stack.focusDown());
        assertTrue(stack.focusUp());
        assertTrue(stack.focusUp());
        assertTrue(stack.focusUp());
        assertEquals(Place.home(0), stack.current());
        assertFalse(stack.focusUp());
        assertNotNull(stack.window(twoB));
    }

    @Test
    public void upFromAWorkspaceReturnsToTheHomePageLastShown() {
        TilingStack stack = TilingStack.firstStart(3, "/home");
        assertTrue(stack.focusRight());
        assertTrue(stack.focusRight());
        assertEquals(Place.home(2), stack.current());
        assertFalse(stack.focusRight());

        stack.focusDown();
        stack.focusUp();

        assertEquals(Place.home(2), stack.current());
        assertTrue(stack.focusLeft());
        assertEquals(Place.home(1), stack.current());
    }

    @Test
    public void theHomePressGoesToHome() {
        TilingStack stack = TilingStack.firstStart(2, "/home");
        stack.goTo(Place.home(1));
        stack.goTo(Place.workspace(1));

        assertTrue(stack.home());

        assertEquals(Place.home(1), stack.current());
    }

    @Test
    public void upAndDownWalkAStackedColumnBeforeLeavingTheWorkspace() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long top = stack.workspace(1).windows().get(0).id();
        long bottom = openInto(stack, 1);
        stack.stack(bottom, top);
        stack.focusWindow(top);

        assertTrue(stack.focusDown());
        assertEquals(bottom, stack.focusedWindowId());
        assertEquals(Place.workspace(1), stack.current());
        assertTrue(stack.focusUp());
        assertEquals(top, stack.focusedWindowId());
        assertTrue(stack.focusUp());
        assertEquals(Place.home(0), stack.current());
    }

    @Test
    public void sidewaysLandsOnAGroupsActiveWindow() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        long c = stack.open(SHELL);
        stack.joinTab(c, b);
        assertEquals(c, stack.focusedWindowId());

        stack.focusLeft();
        assertEquals(a, stack.focusedWindowId());
        stack.focusRight();
        assertEquals(c, stack.focusedWindowId());
    }

    @Test
    public void floatsComeAfterTheTilesSideways() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long f = stack.open(SHELL);
        stack.setFloat(f, true);
        stack.focusWindow(a);

        assertTrue(stack.focusRight());
        assertEquals(f, stack.focusedWindowId());
    }

    @Test
    public void closingTheFocusedWindowFocusesTheOneToItsRightElseLeft() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        long c = stack.open(SHELL);
        stack.focusWindow(b);

        stack.close(b);
        assertEquals(c, stack.focusedWindowId());
        stack.close(c);
        assertEquals(a, stack.focusedWindowId());
    }

    @Test
    public void closingATabFocusesTheNextTabInItsGroup() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        long c = stack.open(SHELL);
        stack.joinTab(b, a);
        stack.joinTab(c, a);
        assertEquals(1, stack.workspace(1).groups().size());
        stack.focusWindow(b);

        stack.close(b);

        assertEquals(c, stack.focusedWindowId());
        TileGroup group = stack.workspace(1).groups().get(0);
        assertEquals(TileGroup.Mode.TABS, group.mode());
        assertEquals(Arrays.asList(a, c), idsOf(group.windows()));
    }

    // ---- main icon and name ----------------------------------------------------------------------

    @Test
    public void theMainWindowIsTheBiggestThenTheLongestOpen() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long first = stack.open(SHELL);
        long app = stack.open(WindowSpec.linuxApp("org.gimp", "GIMP"));

        assertEquals(first, stack.workspace(1).mainWindow().id());

        stack.reportArea(app, 2000);
        stack.reportArea(first, 1000);
        assertEquals(app, stack.workspace(1).mainWindow().id());

        stack.reportArea(first, 2000);
        assertEquals(first, stack.workspace(1).mainWindow().id());
        assertNull(stack.workspace(2).mainWindow());
    }

    @Test
    public void aRenameSticks() {
        TilingStack stack = TilingStack.empty(1);
        long a = openInto(stack, 1);
        openInto(stack, 2);
        assertNull(stack.workspace(1).name());

        stack.renameWorkspace(2, "build");
        stack.close(a);

        assertEquals("build", stack.workspace(1).name());
        stack.renameWorkspace(1, "  ");
        assertNull(stack.workspace(1).name());
    }

    // ---- layouts ---------------------------------------------------------------------------------

    @Test
    public void aNewWorkspaceStartsInScrollingAndCanSwitchToDwindle() {
        TilingStack stack = TilingStack.empty(1);
        openInto(stack, 1);
        assertEquals(WorkspaceLayout.SCROLLING, stack.workspace(1).layout());
        stack.setLayout(1, WorkspaceLayout.DWINDLE);
        assertEquals(WorkspaceLayout.DWINDLE, stack.workspace(1).layout());
        assertEquals(WorkspaceLayout.SCROLLING, stack.workspace(2).layout());
    }

    // ---- saved form ------------------------------------------------------------------------------

    @Test
    public void theSavedFormRoundTripsTheWholeStack() throws Exception {
        TilingStack stack = TilingStack.firstStart(2, "/home");
        long shell = stack.workspace(1).windows().get(0).id();
        stack.setWorkingDirectory(shell, "/home/src");
        stack.goTo(Place.workspace(1));
        long app = stack.open(WindowSpec.linuxApp("org.mozilla.firefox", "Firefox"));
        long tab = stack.open(WindowSpec.shell("/tmp"));
        stack.joinTab(tab, shell);
        long stacked = stack.open(WindowSpec.display());
        stack.stack(stacked, app);
        long floating = stack.open(WindowSpec.assistant());
        stack.setFloat(floating, true);
        stack.setPinned(floating, true);
        stack.setLayout(1, WorkspaceLayout.DWINDLE);
        stack.setLayoutState(1, "{\"split\":0.5}");
        stack.renameWorkspace(1, "work");
        stack.reportArea(app, 500);
        long homeFloat = stack.openIn(Place.home(1), WindowSpec.shell("/sd"), true);
        stack.goTo(Place.workspace(1));
        stack.focusWindow(tab);

        JSONObject saved = stack.toJson();
        TilingStack back = TilingStack.fromJson(new JSONObject(saved.toString()));

        assertEquals(saved.toString(), back.toJson().toString());
        assertEquals(stack.places(), back.places());
        assertEquals(Place.workspace(1), back.current());
        assertEquals(tab, back.focusedWindowId());
        TilingWorkspace one = back.workspace(1);
        assertEquals("work", one.name());
        assertEquals(WorkspaceLayout.DWINDLE, one.layout());
        assertEquals("{\"split\":0.5}", one.layoutState());
        assertEquals(2, one.groups().size());
        assertEquals(TileGroup.Mode.TABS, one.groups().get(0).mode());
        assertEquals(Arrays.asList(shell, tab), idsOf(one.groups().get(0).windows()));
        assertEquals(TileGroup.Mode.STACK, one.groups().get(1).mode());
        assertEquals(Arrays.asList(app, stacked), idsOf(one.groups().get(1).windows()));
        assertEquals(Arrays.asList(floating), idsOf(one.floats()));
        assertTrue(back.window(floating).isPinned());
        assertEquals("/home/src", back.window(shell).workingDirectory());
        assertEquals("/tmp", back.window(tab).workingDirectory());
        assertEquals("org.mozilla.firefox", back.window(app).appId());
        assertEquals("Firefox", back.window(app).name());
        assertEquals(app, one.mainWindow().id());
        assertEquals(Place.home(1), back.placeOf(homeFloat));
        assertEquals("/sd", back.window(homeFloat).workingDirectory());

        long next = back.open(SHELL);
        for (long old : new long[] {shell, app, tab, stacked, floating, homeFloat})
            assertTrue(next != old);
    }

    @Test
    public void aRestoredStackKeepsItsRules() throws Exception {
        JSONObject json = new JSONObject("{\"version\":1,\"homePages\":1,\"workspaces\":["
            + "{\"groups\":[]},"
            + "{\"groups\":[{\"mode\":\"stack\",\"windows\":[{\"id\":4,\"kind\":\"shell\",\"dir\":\"/a\"},"
            + "{\"id\":4,\"kind\":\"shell\"},{\"id\":5,\"kind\":\"teapot\"}]}]}],"
            + "\"current\":{\"workspace\":9}}");

        TilingStack stack = TilingStack.fromJson(json);

        assertEquals(2, stack.workspaceCount());
        assertEquals(1, stack.workspace(1).windows().size());
        assertEquals("/a", stack.workspace(1).windows().get(0).workingDirectory());
        assertWaitingAtEndOnly(stack);
        assertTrue(stack.places().contains(stack.current()));
    }

    @Test
    public void aSavedFormThatIsNotOneIsRefused() {
        try {
            TilingStack.fromJson(new JSONObject());
            fail();
        } catch (TilingStack.SavedFormException expected) {
            // the caller starts fresh
        }
    }

    // ---- review fixes ----------------------------------------------------------------------------

    @Test
    public void joiningATabOntoTheFocusedWindowFocusesTheShownTab() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        assertEquals(b, stack.focusedWindowId());

        assertTrue(stack.joinTab(a, b));

        TileGroup group = stack.workspace(1).groups().get(0);
        assertEquals(a, group.active().id());
        assertEquals(a, stack.focusedWindowId());
    }

    @Test
    public void aStackedColumnHoldsAtMostTwoWindowsWhileTabsAreUnlimited() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        long c = stack.open(SHELL);
        long d = stack.open(SHELL);
        assertTrue(stack.stack(b, a));

        assertFalse(stack.stack(c, a));
        assertEquals(Place.workspace(1), stack.placeOf(c));
        assertEquals(3, stack.workspace(1).groups().size());

        assertTrue(stack.joinTab(c, d));
        assertTrue(stack.joinTab(a, d));
        assertFalse(stack.stack(b, d));
    }

    @Test
    public void anOverFullStackInTheSavedFormIsSplitIntoColumnsOfTwo() throws Exception {
        JSONObject json = new JSONObject("{\"version\":1,\"homePages\":0,\"workspaces\":["
            + "{\"groups\":[{\"mode\":\"stack\",\"windows\":[{\"id\":1,\"kind\":\"shell\"},"
            + "{\"id\":2,\"kind\":\"shell\"},{\"id\":3,\"kind\":\"shell\"}]}]}]}");

        TilingStack stack = TilingStack.fromJson(json);

        List<TileGroup> groups = stack.workspace(1).groups();
        assertEquals(2, groups.size());
        assertEquals(Arrays.asList(1L, 2L), idsOf(groups.get(0).windows()));
        assertEquals(Arrays.asList(3L), idsOf(groups.get(1).windows()));
    }

    @Test
    public void countsInTheSavedFormAreBounded() throws Exception {
        for (String bad : new String[] {
            "{\"homePages\":2147483647,\"workspaces\":[]}",
            "{\"homePages\":-1,\"workspaces\":[]}"}) {
            try {
                TilingStack.fromJson(new JSONObject(bad));
                fail(bad);
            } catch (TilingStack.SavedFormException expected) {
                // the caller starts fresh
            }
        }
        StringBuilder many = new StringBuilder("{\"homePages\":1,\"workspaces\":[");
        for (int i = 0; i <= TilingStack.MAX_WORKSPACES; i++) many.append(i == 0 ? "" : ",").append("{}");
        try {
            TilingStack.fromJson(new JSONObject(many.append("]}").toString()));
            fail();
        } catch (TilingStack.SavedFormException expected) {
            // the caller starts fresh
        }
    }

    @Test
    public void aHomeFloatCanBePinnedAndThePinSurvivesTheSavedForm() throws Exception {
        TilingStack stack = TilingStack.empty(2);
        long id = stack.openIn(Place.home(1), WindowSpec.assistant(), true);

        assertTrue(stack.setPinned(id, true));
        assertEquals(Arrays.asList(id), idsOf(stack.pinnedFloats()));

        TilingStack back = TilingStack.fromJson(stack.toJson());
        assertTrue(back.window(id).isPinned());
        assertEquals(Place.home(1), back.placeOf(id));
    }

    @Test
    public void theSavedFormKeepsTheHomePageLastShownWhileAWorkspaceIsInFront() throws Exception {
        TilingStack stack = TilingStack.firstStart(3, "/home");
        stack.goTo(Place.home(2));
        stack.goTo(Place.workspace(1));

        TilingStack back = TilingStack.fromJson(stack.toJson());

        assertEquals(Place.workspace(1), back.current());
        assertTrue(back.home());
        assertEquals(Place.home(2), back.current());
    }

    @Test
    public void floatingAWindowThatIsNotFocusedLeavesFocusWhereItWas() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        stack.focusWindow(a);

        stack.setFloat(b, true);
        assertEquals(a, stack.focusedWindowId());
        stack.setFloat(b, false);
        assertEquals(a, stack.focusedWindowId());
    }

    @Test
    public void aFocusedFloatMovedOffARemovedHomePageKeepsFocus() {
        TilingStack stack = TilingStack.empty(3);
        long id = stack.openIn(Place.home(2), WindowSpec.assistant(), true);
        stack.focusWindow(id);

        stack.setHomePageCount(2);

        assertEquals(Place.home(1), stack.current());
        assertEquals(id, stack.focusedWindowId());
    }

    // ---- the front window's kind (#419) ----------------------------------------------------------

    @Test
    public void theFrontKindIsTheFocusedWindowsOfThePlaceInFront() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        assertNull("Home has no window in front", stack.frontKind());

        stack.goTo(Place.workspace(1));
        assertEquals(WindowKind.SHELL, stack.frontKind());
        stack.open(WindowSpec.linuxApp("firefox", "Firefox"));
        assertEquals(WindowKind.LINUX_APP, stack.frontKind());
        stack.open(WindowSpec.display());
        assertEquals(WindowKind.DISPLAY, stack.frontKind());
        stack.focusLeft();
        assertEquals(WindowKind.LINUX_APP, stack.frontKind());

        stack.goTo(Place.workspace(2));
        assertNull("the waiting Workspace is empty", stack.frontKind());
    }

    @Test
    public void firstOfKindFindsTheOneDisplayOrAssistantAnywhere() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        assertNull(stack.firstOfKind(WindowKind.ASSISTANT));

        long assistant = stack.openIn(Place.workspace(2), WindowSpec.assistant(), false);

        assertNotNull(stack.firstOfKind(WindowKind.ASSISTANT));
        assertEquals(assistant, stack.firstOfKind(WindowKind.ASSISTANT).id());
        assertEquals(1, stack.firstOfKind(WindowKind.SHELL).id());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static long openInto(TilingStack stack, int number) {
        return stack.openIn(Place.workspace(number), SHELL, false);
    }

    private static List<Long> idsOf(List<TilingWindow> windows) {
        Long[] ids = new Long[windows.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = windows.get(i).id();
        return Arrays.asList(ids);
    }

    private static void assertWaitingAtEndOnly(TilingStack stack) {
        int count = stack.workspaceCount();
        for (int n = 1; n <= count; n++) {
            assertEquals("Workspace " + n, n == count, stack.workspace(n).isEmpty());
        }
    }
}
