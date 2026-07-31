package com.termux.launcherctl;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void modelRetrievePath_matchesSingleSegmentIdsOnly() {
        assertTrue(LauncherCtlApiServer.isModelRetrievePath("/v1/models/gemma-3n-e2b"));
        assertEquals("gemma-3n-e2b",
            LauncherCtlApiServer.modelIdFromRetrievePath("/v1/models/gemma-3n-e2b"));
        assertFalse(LauncherCtlApiServer.isModelRetrievePath("/v1/models"));
        assertFalse(LauncherCtlApiServer.isModelRetrievePath("/v1/models/"));
        assertFalse(LauncherCtlApiServer.isModelRetrievePath("/v1/models/a/b"));
    }
}
