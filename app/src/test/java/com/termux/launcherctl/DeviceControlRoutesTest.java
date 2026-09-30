package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.WallpaperManager;
import android.os.BatteryManager;

import com.termux.app.chrome.ManagedWallpaper;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

public class DeviceControlRoutesTest {

    @Test
    public void handles_onlyTheMethodsEachRouteAnswers() {
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/vibrate"));
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/torch"));
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/toast"));
        assertTrue(DeviceControlRoutes.handles("GET", "/v1/battery"));
        assertTrue(DeviceControlRoutes.handles("GET", "/v1/volume"));
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/volume"));
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/wallpaper"));
        assertTrue(DeviceControlRoutes.handles("GET", "/v1/wallpaper"));
        assertTrue(DeviceControlRoutes.handles("GET", "/v1/wallpaper/builtins"));
        assertFalse(DeviceControlRoutes.handles("POST", "/v1/wallpaper/builtins"));
        assertFalse(DeviceControlRoutes.handles("DELETE", "/v1/wallpaper"));
        assertFalse(DeviceControlRoutes.handles("GET", "/v1/vibrate"));
        assertFalse(DeviceControlRoutes.handles("POST", "/v1/battery"));
        assertFalse(DeviceControlRoutes.handles("DELETE", "/v1/volume"));
        assertFalse(DeviceControlRoutes.handles("POST", "/v1/notify"));
    }

    @Test
    public void batteryNames_matchTermuxBatteryStatus() {
        assertEquals("GOOD", DeviceControlRoutes.healthName(BatteryManager.BATTERY_HEALTH_GOOD));
        assertEquals("OVER_VOLTAGE", DeviceControlRoutes.healthName(BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE));
        assertEquals("UNKNOWN", DeviceControlRoutes.healthName(0));
        assertEquals("PLUGGED_AC", DeviceControlRoutes.pluggedName(BatteryManager.BATTERY_PLUGGED_AC));
        assertEquals("UNPLUGGED", DeviceControlRoutes.pluggedName(0));
        assertEquals("DISCHARGING", DeviceControlRoutes.statusName(BatteryManager.BATTERY_STATUS_DISCHARGING));
        assertEquals("NOT_CHARGING", DeviceControlRoutes.statusName(BatteryManager.BATTERY_STATUS_NOT_CHARGING));
    }

    /** Every device route is rate limited under the key the server looks it up by. */
    @Test
    public void deviceRoutes_haveRateLimiters() throws Exception {
        LauncherCtlApiServer server = LauncherCtlApiServer.getInstance();
        Method init = LauncherCtlApiServer.class.getDeclaredMethod("initializeRateLimiters");
        init.setAccessible(true);
        init.invoke(server);
        Field field = LauncherCtlApiServer.class.getDeclaredField("rateLimiters");
        field.setAccessible(true);
        Map<?, ?> limiters = (Map<?, ?>) field.get(server);
        for (String key : new String[]{"POST:/v1/vibrate", "POST:/v1/torch", "GET:/v1/battery",
                "GET:/v1/volume", "POST:/v1/volume", "POST:/v1/toast", "POST:/v1/wallpaper",
                "GET:/v1/wallpaper", "GET:/v1/wallpaper/builtins"}) {
            assertTrue(key, limiters.containsKey(key));
        }
    }

    @Test
    public void wallpaperTarget_mapsToManagerFlags() {
        assertEquals(WallpaperManager.FLAG_SYSTEM, ManagedWallpaper.flagsForTarget("home"));
        assertEquals(WallpaperManager.FLAG_LOCK, ManagedWallpaper.flagsForTarget("lock"));
        assertEquals(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK,
            ManagedWallpaper.flagsForTarget(" Both "));
        assertEquals(0, ManagedWallpaper.flagsForTarget("system"));
        assertEquals(0, ManagedWallpaper.flagsForTarget(""));
        assertEquals(0, ManagedWallpaper.flagsForTarget(null));
    }

    @Test
    public void wallpaperTargetName_roundTrips() {
        for (String name : new String[]{"home", "lock", "both"}) {
            assertEquals(name, ManagedWallpaper.targetName(ManagedWallpaper.flagsForTarget(name)));
        }
    }

    @Test
    public void wallpaperPath_mustLieUnderAnAllowedRoot() {
        java.util.List<String> roots = Arrays.asList("/data/data/x/files/home", "/storage/emulated/0");
        assertTrue(DeviceControlRoutes.isUnderAnyRoot("/data/data/x/files/home/a.png", roots));
        assertTrue(DeviceControlRoutes.isUnderAnyRoot("/storage/emulated/0", roots));
        assertTrue(DeviceControlRoutes.isUnderAnyRoot("/storage/emulated/0/DCIM/a.jpg", roots));
        assertFalse(DeviceControlRoutes.isUnderAnyRoot("/storage/emulated/01/a.jpg", roots));
        assertFalse(DeviceControlRoutes.isUnderAnyRoot("/data/data/x/files/usr/a.png", roots));
        assertFalse(DeviceControlRoutes.isUnderAnyRoot("/etc/passwd", roots));
        assertFalse(DeviceControlRoutes.isUnderAnyRoot("/etc/passwd", java.util.Collections.<String>emptyList()));
    }

    @Test
    public void builtinWallpaper_rejectsBadRequestsBeforeTouchingTheSystem() throws Exception {
        org.json.JSONObject both = DeviceControlRoutes.handle(null, "POST", "/v1/wallpaper",
            "{\"path\":\"/sdcard/a.jpg\",\"builtin\":\"aurora\"}");
        assertEquals(400, both.getInt("_statusCode"));
        assertEquals("bad_request", both.getString("error"));
        org.json.JSONObject unknown = DeviceControlRoutes.handle(null, "POST", "/v1/wallpaper",
            "{\"builtin\":\"nope\"}");
        assertEquals(404, unknown.getInt("_statusCode"));
        assertEquals("not_found", unknown.getString("error"));
        org.json.JSONObject badPalette = DeviceControlRoutes.handle(null, "POST", "/v1/wallpaper",
            "{\"builtin\":\"aurora\",\"palette\":\"neon\"}");
        assertEquals(400, badPalette.getInt("_statusCode"));
        org.json.JSONObject badTarget = DeviceControlRoutes.handle(null, "POST", "/v1/wallpaper",
            "{\"builtin\":\"aurora\",\"target\":\"sideways\"}");
        assertEquals(400, badTarget.getInt("_statusCode"));
    }

    @Test
    public void builtinList_namesEveryBackgroundWithBothPalettes() throws Exception {
        org.json.JSONArray list = DeviceControlRoutes.handle(null, "GET", "/v1/wallpaper/builtins", null)
            .getJSONArray("builtins");
        assertEquals(4, list.length());
        assertEquals("material", list.getJSONObject(0).getJSONArray("palettes").getString(0));
        assertEquals("own", list.getJSONObject(0).getJSONArray("palettes").getString(1));
    }
}
