package com.termux.launcherctl;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class LauncherCtlApiServerTest {

    @Test
    public void requestPathFromTarget_stripsClientQueryParameters() {
        assertEquals("/v1/models", LauncherCtlApiServer.requestPathFromTarget("/v1/models?client_version=0.142.0"));
        assertEquals("/api/tags", LauncherCtlApiServer.requestPathFromTarget("/api/tags"));
    }

    @Test
    public void unauthorizedResponse_hasOpenAiEnvelopeAndLegacyFields() throws Exception {
        LauncherCtlApiServer.HttpResponse response = LauncherCtlApiServer.unauthorizedResponse();
        JSONObject body = new JSONObject(new String(response.body, StandardCharsets.UTF_8));

        assertEquals(401, response.statusCode);
        assertEquals(false, body.getBoolean("ok"));
        assertEquals("unauthorized", body.getJSONObject("error").getString("code"));
        assertEquals("Missing or invalid token", body.getJSONObject("error").getString("message"));
        assertEquals("unauthorized", body.getJSONObject("tai").getString("error"));
        assertEquals("authentication_error", body.getJSONObject("error").getString("type"));
    }

    @Test
    public void ollamaApiUnauthorizedError_staysFlat() throws Exception {
        LauncherCtlApiServer.HttpResponse response = LauncherCtlApiServer.ollamaUnauthorizedResponse();
        JSONObject body = new JSONObject(new String(response.body, StandardCharsets.UTF_8));

        assertEquals(401, response.statusCode);
        assertEquals("Missing or invalid token", body.getString("error"));
        assertFalse(body.opt("error") instanceof JSONObject);
    }

    @Test
    public void ollamaApiConvertedOpenAiError_staysFlat() throws Exception {
        JSONObject openAiError = new JSONObject().put("_statusCode", 500)
            .put("error", new JSONObject().put("message", "Runtime failed")
                .put("type", "api_error").put("code", "runtime_failed"));

        LauncherCtlApiServer.HttpResponse response =
            LauncherCtlApiServer.ollamaJsonResponse(openAiError);
        JSONObject body = new JSONObject(new String(response.body, StandardCharsets.UTF_8));

        assertEquals(500, response.statusCode);
        assertEquals("Runtime failed", body.getString("error"));
        assertFalse(body.opt("error") instanceof JSONObject);
    }

    @Test
    public void openAiErrorEnvelope_mapsServerErrorsToApiError() throws Exception {
        JSONObject response = new JSONObject().put("error", "internal_error")
            .put("message", "Failed");

        LauncherCtlApiServer.withOpenAiErrorEnvelope(response, 500);

        assertEquals("api_error", response.getJSONObject("error").getString("type"));
    }

    @Test
    public void buildLaunchableAppsPayload_reportsLaunchableActivitiesAndUniquePackages() throws Exception {
        List<LauncherAppEntry> apps = Arrays.asList(
            entry("com.example.alpha", "com.example.alpha.MainActivity", "Alpha"),
            entry("com.example.alpha", "com.example.alpha.SettingsActivity", "Alpha Settings"),
            entry("com.example.beta", "com.example.beta.HomeActivity", "Beta")
        );

        JSONObject payload = LauncherCtlApiServer.buildLaunchableAppsPayload(apps, null);

        assertEquals(true, payload.getBoolean("ok"));
        assertEquals(3, payload.getInt("count"));
        assertEquals(2, payload.getInt("packageCount"));
        assertEquals("com.example.alpha", payload.getJSONArray("apps").getJSONObject(0).getString("packageName"));
        assertEquals("com.example.alpha.MainActivity", payload.getJSONArray("apps").getJSONObject(0).getString("activityName"));
        assertEquals("com.example.alpha/com.example.alpha.MainActivity",
            payload.getJSONArray("apps").getJSONObject(0).getString("stableId"));
        assertEquals(true, payload.getJSONArray("apps").getJSONObject(0).getBoolean("launchable"));
        assertEquals(false, payload.getJSONArray("apps").getJSONObject(0).getBoolean("clonedProfile"));
        assertEquals(-1, payload.getJSONArray("apps").getJSONObject(0).getInt("userId"));
    }

    @Test
    public void buildLaunchableAppsPayload_distinguishesClonedProfileApps() throws Exception {
        List<LauncherAppEntry> apps = Arrays.asList(
            entry("com.example.chat", "com.example.chat.MainActivity", "Chat"),
            new LauncherAppEntry(new AppRef("com.example.chat", "com.example.chat.MainActivity",
                10, 10L, true, "Clone 10"), "Chat · Clone 10", null)
        );

        JSONObject payload = LauncherCtlApiServer.buildLaunchableAppsPayload(apps, null);

        assertEquals(2, payload.getInt("count"));
        JSONObject clone = payload.getJSONArray("apps").getJSONObject(1);
        assertEquals("Chat · Clone 10", clone.getString("label"));
        assertEquals("com.example.chat/com.example.chat.MainActivity#user=10", clone.getString("stableId"));
        assertEquals(10, clone.getInt("userId"));
        assertEquals(true, clone.getBoolean("clonedProfile"));
    }

    @Test
    public void resolveLaunchMatch_returnsAmbiguousForSharedExactLabel() throws Exception {
        List<LauncherAppEntry> apps = Arrays.asList(
            entry("com.example.alpha", "com.example.alpha.MainActivity", "Maps"),
            entry("com.example.beta", "com.example.beta.MainActivity", "Maps")
        );

        LauncherCtlApiServer.AppLaunchMatch match = LauncherCtlApiServer.resolveLaunchMatch(apps, "Maps");

        assertNull(match.entry);
        assertEquals(409, match.statusCode);
        assertEquals("ambiguous", match.errorCode);
        assertEquals(2, match.candidates.length());
    }

    @Test
    public void resolveLaunchMatch_prefersExactPackageMatchOverLabelMatch() throws Exception {
        List<LauncherAppEntry> apps = Arrays.asList(
            entry("com.termux", "com.termux.app.TermuxActivity", "Termux"),
            entry("com.termux.api", "com.termux.api.MainActivity", "Termux:API")
        );

        LauncherCtlApiServer.AppLaunchMatch match = LauncherCtlApiServer.resolveLaunchMatch(apps, "com.termux.api");

        assertEquals("com.termux.api", match.entry.appRef.packageName);
        assertEquals("Termux:API", match.entry.label);
        assertEquals(200, match.statusCode);
    }

    @Test
    public void resolveLaunchMatch_normalizesPunctuationInLabels() throws Exception {
        List<LauncherAppEntry> apps = Arrays.asList(
            entry("com.termux.api", "com.termux.api.MainActivity", "Termux:API")
        );

        LauncherCtlApiServer.AppLaunchMatch match = LauncherCtlApiServer.resolveLaunchMatch(apps, "termux api");

        assertEquals("com.termux.api", match.entry.appRef.packageName);
        assertEquals(200, match.statusCode);
    }

    private static LauncherAppEntry entry(String packageName, String activityName, String label) {
        return new LauncherAppEntry(new AppRef(packageName, activityName), label, null);
    }
}
