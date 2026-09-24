package com.termux.launcherctl;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
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
