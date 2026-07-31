package com.termux.app.terminal;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SessionBrowserModelTest {

    private static SessionBrowserModel.Session session(int index, String name, String cwd,
                                                       String foreground, int paneCount) {
        java.util.ArrayList<SessionBrowserModel.Pane> panes = new java.util.ArrayList<>();
        for (int i = 0; i < paneCount; i++) {
            panes.add(new SessionBrowserModel.Pane(cwd, foreground));
        }
        SessionBrowserModel.Window window = new SessionBrowserModel.Window(0, true, 0, panes);
        return new SessionBrowserModel.Session(index, index == 0, name,
            Collections.singletonList(window));
    }

    @Test
    public void filter_matchesNameCwdAndForegroundCaseInsensitively() {
        List<SessionBrowserModel.Session> sessions = Arrays.asList(
            session(0, "work", "/data/data/com.termux/files/home/project", "nvim · Main.java", 2),
            session(1, "logs", "/data/data/com.termux/files/home", "journalctl", 1),
            session(2, null, "/tmp/build-output", "bash", 1));

        assertEquals(0, SessionBrowserModel.filter(sessions, "WORK").get(0).index);
        assertEquals(0, SessionBrowserModel.filter(sessions, "project").get(0).index);
        assertEquals(0, SessionBrowserModel.filter(sessions, "main.java").get(0).index);
        assertEquals(1, SessionBrowserModel.filter(sessions, "JOURNAL").get(0).index);
        assertEquals(2, SessionBrowserModel.filter(sessions, "build-output").get(0).index);
        assertEquals(0, SessionBrowserModel.filter(sessions, "missing").size());
    }

    @Test
    public void filter_blankPreservesOrderAndPaneCountCoversAllWindows() {
        SessionBrowserModel.Session first = session(3, "one", "/one", "bash", 2);
        SessionBrowserModel.Window secondWindow = new SessionBrowserModel.Window(1, false, 0,
            Arrays.asList(new SessionBrowserModel.Pane("/two", "git"),
                new SessionBrowserModel.Pane("/three", "vim")));
        SessionBrowserModel.Session expanded = new SessionBrowserModel.Session(first.index,
            first.current, first.name, Arrays.asList(first.windows.get(0), secondWindow));
        List<SessionBrowserModel.Session> sessions = Arrays.asList(expanded,
            session(4, "two", "/four", "fish", 1));

        List<SessionBrowserModel.Session> filtered = SessionBrowserModel.filter(sessions, "  ");
        assertEquals(Arrays.asList(3, 4), Arrays.asList(filtered.get(0).index, filtered.get(1).index));
        assertEquals(4, filtered.get(0).paneCount());
    }
}
