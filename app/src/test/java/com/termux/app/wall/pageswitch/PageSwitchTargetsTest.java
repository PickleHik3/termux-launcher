package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;

import com.termux.app.wall.pageswitch.PageBarModel.Action;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PageSwitchTargetsTest {

    /** Records the calls it gets, as text. */
    private static final class Recorder implements PageSwitchTargets.Navigator {
        final List<String> calls = new ArrayList<>();
        @Override public void goToPage(int pageId) { calls.add("goToPage:" + pageId); }
        @Override public void showTerminalWindow(int index) { calls.add("showTerminalWindow:" + index); }
        @Override public void goToTerminal() { calls.add("goToTerminal"); }
        @Override public void openOverview() { calls.add("openOverview"); }
    }

    private static List<String> run(Action action, int pageId, int index) {
        Recorder rec = new Recorder();
        PageSwitchTargets.carryOut(new PageBarModel.Target(action, pageId, index), rec);
        return rec.calls;
    }

    @Test public void closeDoesNothing() {
        assertEquals(0, run(Action.CLOSE, 3, 1).size());
    }

    @Test public void goPageGoesToThePage() {
        assertEquals(1, run(Action.GO_PAGE, 7, -1).size());
        assertEquals("goToPage:7", run(Action.GO_PAGE, 7, -1).get(0));
    }

    @Test public void goTerminalWithAWindowShowsIt() {
        List<String> calls = run(Action.GO_TERMINAL, 3, 2);
        assertEquals(1, calls.size());
        assertEquals("showTerminalWindow:2", calls.get(0));
    }

    @Test public void goTerminalWithoutAWindowGoesToTheTerminal() {
        List<String> calls = run(Action.GO_TERMINAL, 3, -1);
        assertEquals(1, calls.size());
        assertEquals("goToTerminal", calls.get(0));
    }
}
