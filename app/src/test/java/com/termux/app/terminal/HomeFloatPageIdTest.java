package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.os.Build;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A Home float names its page by id: the file (v5, and a v4 position read once) and the pager. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeFloatPageIdTest {

    private static String workspace(int version, String pageKey, int page) {
        return "{\"version\":" + version + ",\"name\":\"w\",\"savedAtEpochMs\":1,\"currentSession\":0,"
            + "\"sessions\":[{\"currentWindow\":0,\"windows\":[{\"activePane\":0,"
            + "\"root\":{\"type\":\"pane\"},\"homeFloat\":{\"" + pageKey + "\":" + page
            + ",\"minimised\":false}}]}]}";
    }

    private static int floatPage(TerminalWorkspace workspace) {
        TerminalWorkspace.HomeFloat homeFloat = workspace.sessions.get(0).windows.get(0).homeFloat;
        assertNotNull(homeFloat);
        return homeFloat.pageId;
    }

    private static TerminalWorkspace load(String json, TerminalWorkspace.HomePages pages)
        throws Exception {
        return TerminalWorkspace.fromJson(new JSONObject(json), pages);
    }

    @Test public void aV4PositionIsReadAsThePageAtThatPlaceInTheCurrentOrder() throws Exception {
        TerminalWorkspace.HomePages pages =
            new TerminalWorkspace.HomePages(Arrays.asList(7, 9, 4), 9);
        assertEquals(9, floatPage(load(workspace(4, "page", 1), pages)));
        assertEquals(4, floatPage(load(workspace(4, "page", 2), pages)));
    }

    @Test public void aV4PositionPastTheLastFallsBackToHomeThenToTheFirstPage() throws Exception {
        assertEquals("home", 9, floatPage(load(workspace(4, "page", 5),
            new TerminalWorkspace.HomePages(Arrays.asList(7, 9, 4), 9))));
        assertEquals("first Home page when home is not one", 7, floatPage(load(workspace(4, "page", 5),
            new TerminalWorkspace.HomePages(Arrays.asList(7, 9, 4), 100))));
    }

    @Test public void withoutPagesAV4PositionReadsAsTheIdAFreshWallGivesIt() throws Exception {
        assertEquals(3, floatPage(load(workspace(4, "page", 2), null)));
    }

    @Test public void aV5FileKeepsItsPageIdAndIsWrittenAsV5() throws Exception {
        TerminalWorkspace.HomePages pages = new TerminalWorkspace.HomePages(Arrays.asList(1, 2), 1);
        TerminalWorkspace loaded = load(workspace(5, "pageId", 42), pages);
        assertEquals("an id is not looked up", 42, floatPage(loaded));
        JSONObject written = loaded.toJson();
        assertEquals(5, written.getInt("version"));
        JSONObject homeFloat = written.getJSONArray("sessions").getJSONObject(0)
            .getJSONArray("windows").getJSONObject(0).getJSONObject("homeFloat");
        assertEquals(42, homeFloat.getInt("pageId"));
        assertEquals(false, homeFloat.has("page"));
        assertEquals(42, floatPage(TerminalWorkspace.fromJson(written, pages)));
    }

    @Test public void aV4FileIsWrittenBackAsV5ByPageId() throws Exception {
        TerminalWorkspace loaded = load(workspace(4, "page", 1),
            new TerminalWorkspace.HomePages(Arrays.asList(7, 9), 7));
        JSONObject written = loaded.toJson();
        assertEquals(5, written.getInt("version"));
        assertEquals(9, floatPage(TerminalWorkspace.fromJson(written, null)));
    }

    private static TerminalSession shell() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }

    private static TerminalPaneController controller(List<Integer> ids) {
        TerminalPaneController controller = new TerminalPaneController(new Host(),
            new FrameLayout(RuntimeEnvironment.getApplication()),
            LayoutInflater.from(RuntimeEnvironment.getApplication()));
        controller.setHomePageIds(() -> new ArrayList<>(ids));
        return controller;
    }

    @Test public void aFloatFollowsItsPageThroughAMove() {
        List<Integer> ids = new ArrayList<>(Arrays.asList(1, 2, 3));
        TerminalPaneController controller = controller(ids);
        TerminalPaneController.Window window = controller.openHomeFloat(shell(), 2);
        assertEquals(3, window.homePageId());
        assertEquals(2, window.homePage());

        Collections.swap(ids, 0, 2);
        controller.onHomePagesChanged();
        assertEquals("still page 3", 3, window.homePageId());
        assertEquals("now first in the order", 0, window.homePage());
        assertEquals(1, controller.homeFloatCountOn(0));
        assertEquals(0, controller.homeFloatCountOn(2));
    }

    @Test public void aFloatOnAPageRemovedForGoodStandsOnThePageNowInItsPlace() {
        List<Integer> ids = new ArrayList<>(Arrays.asList(1, 2, 3));
        TerminalPaneController controller = controller(ids);
        TerminalPaneController.Window middle = controller.openHomeFloat(shell(), 1);
        TerminalPaneController.Window last = controller.openHomeFloat(shell(), 2);

        ids.remove(Integer.valueOf(2));
        controller.onHomePagesChanged();
        controller.homePageRemoved(2);
        assertEquals("the page that slid into its place", 3, middle.homePageId());
        assertEquals(1, middle.homePage());
        assertEquals(3, last.homePageId());

        ids.remove(Integer.valueOf(3));
        controller.onHomePagesChanged();
        controller.homePageRemoved(3);
        assertEquals("the new last", 1, middle.homePageId());
        assertEquals(1, last.homePageId());
    }

    @Test public void aFloatWhosePageIsOnlySetAsideStaysOnItAndComesBack() {
        List<Integer> ids = new ArrayList<>(Arrays.asList(1, 2));
        TerminalPaneController controller = controller(ids);
        TerminalPaneController.Window window = controller.openHomeFloat(shell(), 1);
        ids.remove(Integer.valueOf(2));
        controller.onHomePagesChanged();
        assertEquals(2, window.homePageId());
        ids.add(2);
        controller.onHomePagesChanged();
        assertEquals(1, window.homePage());
    }

    private static final class Host implements TerminalPaneController.Host {
        @Override public TerminalSession createShell(String cwd) { return shell(); }
        @Override public void configurePaneView(TerminalView view) {}
        @Override public void removeShell(TerminalSession session) {}
        @Override public void onActivePaneChanged() {}
        @Override public void onTreesChanged() {}
        @Override public String defaultCwd() { return "/"; }
        @Override public void onHomeFloatsChanged() {}
        @Override public void onHomeFloatFocusReleased() {}
    }
}
