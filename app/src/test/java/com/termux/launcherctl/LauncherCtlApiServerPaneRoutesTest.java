package com.termux.launcherctl;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        assertNull(LauncherCtlApiServer.paneToolFor("GET", "/v1/panes/abc-1/write"));
        assertNull(LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1/kill"));
        assertNull(LauncherCtlApiServer.paneToolFor("POST", "/v1/panes//focus"));
        assertNull(LauncherCtlApiServer.paneToolFor("POST", "/v1/panes/abc-1"));
        assertEquals("abc-1", LauncherCtlApiServer.paneIdFrom("/v1/panes/abc-1/text"));
        assertNull(LauncherCtlApiServer.paneIdFrom("/v1/panes"));
    }

    @Test
    public void rateLimitKey_sharesOneBucketPerPaneAction() {
        assertEquals("POST:/v1/panes/*/write", LauncherCtlApiServer.rateLimitKey("POST", "/v1/panes/abc/write"));
        assertEquals("GET:/v1/panes/*/text", LauncherCtlApiServer.rateLimitKey("GET", "/v1/panes/xyz/text"));
        assertEquals("GET:/v1/panes", LauncherCtlApiServer.rateLimitKey("GET", "/v1/panes"));
        assertEquals("POST:/v1/apps/launch", LauncherCtlApiServer.rateLimitKey("POST", "/v1/apps/launch"));
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
