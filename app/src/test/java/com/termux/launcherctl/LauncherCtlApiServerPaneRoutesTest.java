package com.termux.launcherctl;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LauncherCtlApiServerPaneRoutesTest {

    @Test
    public void paneRoutes_mapToTheirActions() {
        assertEquals("pane.list", LauncherCtlApiServer.paneToolFor("GET", "/v1/panes"));
        assertEquals("pane.open", LauncherCtlApiServer.paneToolFor("POST", "/v1/panes"));
        assertNull(LauncherCtlApiServer.paneToolFor("DELETE", "/v1/panes"));
        assertEquals("pane.focus", LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1/focus"));
        assertEquals("pane.close", LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1/close"));
        assertEquals("pane.write", LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1/write"));
        assertEquals("pane.read", LauncherCtlApiServer.paneToolFor("GET", "/v1/panes/abc-1/text"));
        assertEquals("agent.status", LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1/agent"));
        assertNull(LauncherCtlApiServer.paneToolFor("GET", "/v1/panes/abc-1/agent"));
        assertNull(LauncherCtlApiServer.paneToolFor("GET", "/v1/panes/abc-1/write"));
        assertNull(LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1/kill"));
        assertNull(LauncherCtlApiServer.paneToolFor("POST", "/v1/panes//focus"));
        assertNull(LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1"));
        assertEquals("abc-1", LauncherCtlApiServer.paneIdFrom("/v1/panes/abc-1/text"));
        assertNull(LauncherCtlApiServer.paneIdFrom("/v1/panes"));
        assertEquals("pane.set_size", LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1/resize"));
        assertNull(LauncherCtlApiServer.paneToolFor("GET", "/v1/panes/abc-1/resize"));
    }

    /** GET /v1/panes/{id} is the pane itself; wait has no dispatcher tool, the server holds it. */
    @Test
    public void paneInfoAndWaitRoutes() {
        assertEquals("pane.info", LauncherCtlApiServer.paneToolFor("GET", "/v1/panes/abc-1"));
        assertNull(LauncherCtlApiServer.paneToolFor("DELETE", "/v1/panes/abc-1"));
        assertEquals("abc-1", LauncherCtlApiServer.paneIdFrom("/v1/panes/abc-1"));
        assertNull(LauncherCtlApiServer.paneIdFrom("/v1/windows/3"));
        assertNull(LauncherCtlApiServer.windowToolFor("POST", "/v1/windows/3"));

        assertTrue(LauncherCtlApiServer.isPaneWaitRoute("POST", "/v1/panes/abc-1/wait"));
        assertFalse(LauncherCtlApiServer.isPaneWaitRoute("GET", "/v1/panes/abc-1/wait"));
        assertFalse(LauncherCtlApiServer.isPaneWaitRoute("POST", "/v1/panes//wait"));
        assertFalse(LauncherCtlApiServer.isPaneWaitRoute("POST", "/v1/windows/3/wait"));
        assertFalse(LauncherCtlApiServer.isPaneWaitRoute("POST", "/v1/panes/abc-1/text"));
        assertNull(LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1/wait"));
        assertEquals("abc-1", LauncherCtlApiServer.paneIdFrom("/v1/panes/abc-1/wait"));
    }

    /** Pane info buckets like the list, a wait like a read; each across every id. */
    @Test
    public void paneInfoAndWait_haveTheirOwnRateLimiters() throws Exception {
        LauncherCtlApiServer server = LauncherCtlApiServer.getInstance();
        Method init = LauncherCtlApiServer.class.getDeclaredMethod("initializeRateLimiters");
        init.setAccessible(true);
        init.invoke(server);
        Field field = LauncherCtlApiServer.class.getDeclaredField("rateLimiters");
        field.setAccessible(true);
        Map<?, ?> limiters = (Map<?, ?>) field.get(server);
        assertEquals("GET:/v1/panes/*", LauncherCtlApiServer.rateLimitKey("GET", "/v1/panes/abc"));
        assertEquals("POST:/v1/panes/*/wait", LauncherCtlApiServer.rateLimitKey("POST", "/v1/panes/abc/wait"));
        assertEquals("GET:/v1/panes/*/text", LauncherCtlApiServer.rateLimitKey("GET", "/v1/panes/a/text"));
        assertEquals("POST:/v1/windows/3", LauncherCtlApiServer.rateLimitKey("POST", "/v1/windows/3"));
        for (String key : new String[]{"GET:/v1/panes/*", "POST:/v1/panes/*/wait"}) {
            assertTrue(key, limiters.containsKey(key));
        }
    }

    /** {@code "print":"id"} on an open: the field, and where the new pane's id sits in each answer. */
    @Test
    public void printIdFindsTheFieldAndTheOpenedPane() throws Exception {
        assertEquals("id", LauncherCtlApiServer.printField("{\"focus\":false,\"print\":\"id\"}"));
        assertNull(LauncherCtlApiServer.printField("{\"focus\":false}"));
        assertNull(LauncherCtlApiServer.printField("{\"print\":true}"));
        assertNull(LauncherCtlApiServer.printField("not json"));
        assertNull(LauncherCtlApiServer.printField(null));

        org.json.JSONObject paneOpened = new org.json.JSONObject()
            .put("ok", true).put("pane", new org.json.JSONObject().put("id", "p-1"));
        assertEquals("p-1", LauncherCtlApiServer.openedPaneId(paneOpened));
        org.json.JSONObject windowOpened = new org.json.JSONObject()
            .put("ok", true).put("id", "p-2").put("pane", "p-2").put("windowId", 7);
        assertEquals("p-2", LauncherCtlApiServer.openedPaneId(windowOpened));
        assertNull(LauncherCtlApiServer.openedPaneId(new org.json.JSONObject().put("ok", true)));
    }

    @Test
    public void windowRoutes_mapToTheirActions() {
        assertEquals("window.focus", LauncherCtlApiServer.windowToolFor("POST", "/v1/windows/3/focus"));
        assertEquals("window.close_by_id", LauncherCtlApiServer.windowToolFor("POST", "/v1/windows/3/close"));
        assertNull(LauncherCtlApiServer.windowToolFor("GET", "/v1/windows/3/focus"));
        assertNull(LauncherCtlApiServer.windowToolFor("POST", "/v1/windows/3/kill"));
        assertNull(LauncherCtlApiServer.windowToolFor("POST", "/v1/windows//focus"));
        assertNull(LauncherCtlApiServer.windowToolFor("POST", "/v1/windows"));
        assertNull(LauncherCtlApiServer.windowToolFor("POST", "/v1/panes/3/focus"));
        assertEquals("3", LauncherCtlApiServer.paneIdFrom("/v1/windows/3/close"));
    }

    /** Resize buckets like focus, window focus/close like pane focus/close; one bucket per action. */
    @Test
    public void resizeAndWindowRoutes_haveTheirOwnRateLimiters() throws Exception {
        LauncherCtlApiServer server = LauncherCtlApiServer.getInstance();
        Method init = LauncherCtlApiServer.class.getDeclaredMethod("initializeRateLimiters");
        init.setAccessible(true);
        init.invoke(server);
        Field field = LauncherCtlApiServer.class.getDeclaredField("rateLimiters");
        field.setAccessible(true);
        Map<?, ?> limiters = (Map<?, ?>) field.get(server);
        assertEquals("POST:/v1/panes/*/resize", LauncherCtlApiServer.rateLimitKey("POST", "/v1/panes/abc/resize"));
        assertEquals("POST:/v1/windows/*/focus", LauncherCtlApiServer.rateLimitKey("POST", "/v1/windows/7/focus"));
        assertEquals("POST:/v1/windows/*/close", LauncherCtlApiServer.rateLimitKey("POST", "/v1/windows/7/close"));
        for (String key : new String[]{"POST:/v1/panes/*/resize", "POST:/v1/windows/*/focus",
                "POST:/v1/windows/*/close"}) {
            assertTrue(key, limiters.containsKey(key));
        }
    }

    @Test
    public void keyboardRoutes_mapToTheirActions() {
        assertEquals("keyboard.show", LauncherCtlApiServer.keyboardToolFor("POST", "/v1/keyboard/show"));
        assertEquals("keyboard.hide", LauncherCtlApiServer.keyboardToolFor("POST", "/v1/keyboard/hide"));
        assertNull(LauncherCtlApiServer.keyboardToolFor("GET", "/v1/keyboard/show"));
        assertNull(LauncherCtlApiServer.keyboardToolFor("POST", "/v1/keyboard/toggle"));
        assertNull(LauncherCtlApiServer.keyboardToolFor("POST", "/v1/keyboard/"));
    }

    @Test
    public void signalRoutes_mapToTheirActions() {
        assertEquals("shell.notify", LauncherCtlApiServer.signalToolFor("POST", "/v1/notify"));
        assertEquals("shell.progress", LauncherCtlApiServer.signalToolFor("POST", "/v1/progress"));
        assertEquals("clipboard.write", LauncherCtlApiServer.signalToolFor("POST", "/v1/clipboard"));
        assertEquals("clipboard.read", LauncherCtlApiServer.signalToolFor("GET", "/v1/clipboard"));
        assertNull(LauncherCtlApiServer.signalToolFor("GET", "/v1/notify"));
        assertNull(LauncherCtlApiServer.signalToolFor("DELETE", "/v1/clipboard"));
        assertNull(LauncherCtlApiServer.signalToolFor("POST", "/v1/notify/"));
        assertNull(LauncherCtlApiServer.signalToolFor("POST", "/v1/panes"));
    }

    /** Each signal route has its own bucket, so a chatty progress loop cannot starve a notification. */
    @Test
    public void signalRoutes_haveTheirOwnRateLimiters() throws Exception {
        LauncherCtlApiServer server = LauncherCtlApiServer.getInstance();
        Method init = LauncherCtlApiServer.class.getDeclaredMethod("initializeRateLimiters");
        init.setAccessible(true);
        init.invoke(server);
        Field field = LauncherCtlApiServer.class.getDeclaredField("rateLimiters");
        field.setAccessible(true);
        Map<?, ?> limiters = (Map<?, ?>) field.get(server);
        for (String key : new String[]{"POST:/v1/notify", "POST:/v1/progress", "POST:/v1/clipboard", "GET:/v1/clipboard"}) {
            assertTrue(key, limiters.containsKey(key));
            assertEquals(key, key, LauncherCtlApiServer.rateLimitKey(key.substring(0, key.indexOf(':')), key.substring(key.indexOf(':') + 1)));
        }
    }

    @Test
    public void rateLimitKey_sharesOneBucketPerPaneAction() {
        assertEquals("POST:/v1/panes/*/write", LauncherCtlApiServer.rateLimitKey("POST", "/v1/panes/abc/write"));
        assertEquals("GET:/v1/panes/*/text", LauncherCtlApiServer.rateLimitKey("GET", "/v1/panes/xyz/text"));
        assertEquals("POST:/v1/panes/*/agent", LauncherCtlApiServer.rateLimitKey("POST", "/v1/panes/abc/agent"));
        assertEquals("GET:/v1/panes", LauncherCtlApiServer.rateLimitKey("GET", "/v1/panes"));
        assertEquals("POST:/v1/apps/launch", LauncherCtlApiServer.rateLimitKey("POST", "/v1/apps/launch"));
        assertEquals("POST:/v1/windows", LauncherCtlApiServer.rateLimitKey("POST", "/v1/windows"));
    }

    /** {@code POST /v1/windows} shares the rate-limiter machinery every other protected route does. */
    @Test
    public void windowsRoute_hasItsOwnRateLimiterRegistered() throws Exception {
        LauncherCtlApiServer server = LauncherCtlApiServer.getInstance();
        Method init = LauncherCtlApiServer.class.getDeclaredMethod("initializeRateLimiters");
        init.setAccessible(true);
        init.invoke(server);
        Field field = LauncherCtlApiServer.class.getDeclaredField("rateLimiters");
        field.setAccessible(true);
        Map<?, ?> limiters = (Map<?, ?>) field.get(server);
        assertTrue(limiters.containsKey("POST:/v1/windows"));
        assertTrue(limiters.containsKey("POST:/v1/panes"));
    }

    @Test
    public void queryParameters_decodeAndTolerateJunk() {
        Map<String, String> parameters = LauncherCtlApiServer.queryParameters("lines=40&x=a%20b&flag&&=v");
        assertEquals("40", parameters.get("lines"));
        assertEquals("a b", parameters.get("x"));
        assertEquals("", parameters.get("flag"));
        assertEquals(0, LauncherCtlApiServer.queryParameters(null).size());
        assertEquals(0, LauncherCtlApiServer.queryParameters("").size());
    }
}
