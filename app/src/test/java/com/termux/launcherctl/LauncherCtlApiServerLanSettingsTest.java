package com.termux.launcherctl;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.TaiSettings;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherCtlApiServerLanSettingsTest {

    @Test
    public void settings_defaultBindModeIsLocalhostAndJsonHasNoLanWarning() throws Exception {
        TaiSettings settings = freshSettings();

        JSONObject json = settings.toJson();

        assertEquals(TaiSettings.BIND_MODE_LOCALHOST, settings.getApiBindMode());
        assertEquals(TaiSettings.BIND_MODE_LOCALHOST, json.getString("bindMode"));
        assertFalse(json.has("lanWarning"));
    }

    @Test
    public void settings_rejectsInvalidBindModeBackToLocalhost() throws Exception {
        TaiSettings settings = freshSettings();

        settings.setApiBindMode("wan");

        assertEquals(TaiSettings.BIND_MODE_LOCALHOST, settings.getApiBindMode());
        assertEquals(TaiSettings.BIND_MODE_LOCALHOST, settings.toJson().getString("bindMode"));
    }

    @Test
    public void settings_lanModeAddsWarningTextToJson() throws Exception {
        TaiSettings settings = freshSettings();

        settings.setApiBindMode(TaiSettings.BIND_MODE_LAN);
        JSONObject json = settings.toJson();

        assertEquals(TaiSettings.BIND_MODE_LAN, json.getString("bindMode"));
        assertTrue(json.getString("lanWarning").contains("LAN exposure"));
    }

    @Test
    public void createLoopbackServerSocket_defaultModeBindsLoopback() throws Exception {
        try (ServerSocket socket = LauncherCtlApiServer.createLoopbackServerSocket(0, TaiSettings.BIND_MODE_LOCALHOST)) {
            assertTrue(socket.getInetAddress().isLoopbackAddress());
            assertEquals("127.0.0.1", LauncherCtlApiServer.bindAddressForMode(TaiSettings.BIND_MODE_LOCALHOST));
        }
    }

    @Test
    public void createLoopbackServerSocket_lanModeBindsAnyLocalAddress() throws Exception {
        try (ServerSocket socket = LauncherCtlApiServer.createLoopbackServerSocket(0, TaiSettings.BIND_MODE_LAN)) {
            assertTrue(socket.getInetAddress().isAnyLocalAddress());
            assertEquals("0.0.0.0", LauncherCtlApiServer.bindAddressForMode(TaiSettings.BIND_MODE_LAN));
        }
    }

    @Test
    public void unauthorizedResponse_localhostModeReturns401WithoutCorsHeaders() throws Exception {
        assertUnauthorizedResponseHasNoCors(TaiSettings.BIND_MODE_LOCALHOST);
    }

    @Test
    public void unauthorizedResponse_lanModeReturns401WithoutCorsHeaders() throws Exception {
        assertUnauthorizedResponseHasNoCors(TaiSettings.BIND_MODE_LAN);
    }

    private static TaiSettings freshSettings() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        return new TaiSettings(context);
    }

    private static void assertUnauthorizedResponseHasNoCors(String bindMode) throws Exception {
        assertFalse(LauncherCtlApiServer.isAuthorized("1234567890abcdef", null));
        assertTrue(LauncherCtlApiServer.bindAddressForMode(bindMode).length() > 0);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LauncherCtlApiServer.writeResponse(output, LauncherCtlApiServer.unauthorizedResponse());
        String response = output.toString(StandardCharsets.UTF_8.name());

        assertTrue(response.startsWith("HTTP/1.1 401 Unauthorized"));
        assertTrue(response.contains("\"code\":\"unauthorized\""));
        String body = response.substring(response.indexOf("\r\n\r\n") + 4);
        JSONObject json = new JSONObject(body);
        assertEquals("unauthorized", json.getJSONObject("error").getString("code"));
        assertEquals("unauthorized", json.getJSONObject("tai").getString("error"));
        assertFalse(response.toLowerCase().contains("access-control-allow-origin"));
        assertFalse(response.toLowerCase().contains("access-control-allow-headers"));
        assertFalse(response.toLowerCase().contains("access-control-allow-methods"));
    }
}
