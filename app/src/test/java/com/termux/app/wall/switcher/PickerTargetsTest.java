package com.termux.app.wall.switcher;

import static org.junit.Assert.assertEquals;

import com.termux.app.wall.switcher.PickerModel.Action;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PickerTargetsTest {

    /** Records the calls it gets, as text. */
    private static final class Recorder implements PickerTargets.Navigator {
        final List<String> calls = new ArrayList<>();
        @Override public void goToPage(int pageId) { calls.add("goToPage:" + pageId); }
        @Override public void goHome() { calls.add("goHome"); }
        @Override public void showTerminalWindow(int index) { calls.add("showTerminalWindow:" + index); }
        @Override public void goToTerminal() { calls.add("goToTerminal"); }
        @Override public void showDisplayApp(int pageId, int windowId) {
            calls.add("showDisplayApp:" + pageId + "," + windowId);
        }
        @Override public void startDisplay() { calls.add("startDisplay"); }
        @Override public void addAssistantPage() { calls.add("addAssistantPage"); }
        @Override public void addDisplayPage() { calls.add("addDisplayPage"); }
        @Override public void openSessionBrowser() { calls.add("openSessionBrowser"); }
        @Override public void openDisplayAppList() { calls.add("openDisplayAppList"); }
        @Override public void openPageOverview() { calls.add("openPageOverview"); }
    }

    private static List<String> run(Action action, int pageId, int index) {
        Recorder rec = new Recorder();
        PickerTargets.carryOut(new PickerModel.Target(action, pageId, index), rec);
        return rec.calls;
    }

    private static void expect(String call, Action action, int pageId, int index) {
        List<String> calls = run(action, pageId, index);
        assertEquals(1, calls.size());
        assertEquals(call, calls.get(0));
    }

    @Test public void closeDoesNothing() {
        assertEquals(0, run(Action.CLOSE, 3, 1).size());
    }

    @Test public void goPageGoesToThePage() {
        expect("goToPage:4", Action.GO_PAGE, 4, -1);
    }

    @Test public void goHomeGoesHome() {
        expect("goHome", Action.GO_HOME, -1, -1);
    }

    @Test public void goTerminalWithAWindowShowsIt() {
        expect("showTerminalWindow:2", Action.GO_TERMINAL, 3, 2);
    }

    @Test public void goTerminalWithoutAWindowGoesToTheTerminal() {
        expect("goToTerminal", Action.GO_TERMINAL, 3, -1);
    }

    @Test public void goDisplayAppShowsTheApp() {
        expect("showDisplayApp:4,200", Action.GO_DISPLAY_APP, 4, 200);
    }

    @Test public void startDisplayStartsIt() {
        expect("startDisplay", Action.START_DISPLAY, 4, -1);
    }

    @Test public void addAssistantAddsThePage() {
        expect("addAssistantPage", Action.ADD_ASSISTANT, -1, -1);
    }

    @Test public void addDisplayAddsThePage() {
        expect("addDisplayPage", Action.ADD_DISPLAY, -1, -1);
    }

    @Test public void terminalListOpensTheSessionBrowser() {
        expect("openSessionBrowser", Action.OPEN_TERMINAL_LIST, 3, -1);
    }

    @Test public void displayListOpensTheAppList() {
        expect("openDisplayAppList", Action.OPEN_DISPLAY_LIST, 4, -1);
    }

    @Test public void pageOverviewOpensTheOverview() {
        expect("openPageOverview", Action.OPEN_PAGE_OVERVIEW, -1, -1);
    }
}
