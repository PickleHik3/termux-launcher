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
        assertFalse(DeviceControlRoutes.handles("GET", "/v1/wallpaper/builtins"));
        assertFalse(DeviceControlRoutes.handles("DELETE", "/v1/wallpaper"));
        assertFalse(DeviceControlRoutes.handles("GET", "/v1/vibrate"));
        assertFalse(DeviceControlRoutes.handles("POST", "/v1/battery"));
        assertFalse(DeviceControlRoutes.handles("DELETE", "/v1/volume"));
        assertFalse(DeviceControlRoutes.handles("POST", "/v1/notify"));
        assertTrue(DeviceControlRoutes.handles("GET", "/v1/motion-log"));
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/motion-log/clear"));
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/look"));
        assertFalse(DeviceControlRoutes.handles("GET", "/v1/look"));
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/config"));
        assertFalse(DeviceControlRoutes.handles("GET", "/v1/config"));
        assertFalse(DeviceControlRoutes.handles("POST", "/v1/motion-log"));
        assertFalse(DeviceControlRoutes.handles("GET", "/v1/motion-log/clear"));
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
                "GET:/v1/wallpaper", "GET:/v1/pagebar-glass", "POST:/v1/pagebar-glass",
                "POST:/v1/config"}) {
            assertTrue(key, limiters.containsKey(key));
        }
        assertFalse(limiters.containsKey("GET:/v1/wallpaper/builtins"));
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
    public void wallpaperBuiltin_isRemoved() throws Exception {
        org.json.JSONObject both = DeviceControlRoutes.handle(null, "POST", "/v1/wallpaper",
            "{\"path\":\"/sdcard/a.jpg\",\"builtin\":\"aurora\"}");
        assertEquals(400, both.getInt("_statusCode"));
        assertEquals("bad_request", both.getString("error"));
        org.json.JSONObject alone = DeviceControlRoutes.handle(null, "POST", "/v1/wallpaper",
            "{\"builtin\":\"aurora\",\"target\":\"home\"}");
        assertEquals(400, alone.getInt("_statusCode"));
        assertEquals("bad_request", alone.getString("error"));
        org.json.JSONObject none = DeviceControlRoutes.handle(null, "POST", "/v1/wallpaper", "{}");
        assertEquals(400, none.getInt("_statusCode"));
    }

    // ------------------------------------------------------------------------------- root routes

    /** A fake phone: root on or off, restarts allowed or not, and the commands it was asked to run. */
    private static final class FakeRoot implements DeviceControlRoutes.RootAccess {
        final boolean active;
        final boolean rebootAllowed;
        final java.util.List<String> ran = new java.util.ArrayList<>();
        String output = "";

        FakeRoot(boolean active, boolean rebootAllowed) {
            this.active = active;
            this.rebootAllowed = rebootAllowed;
        }

        @Override public boolean isActive() { return active; }
        @Override public boolean rebootAllowed(android.content.Context context) { return rebootAllowed; }
        @Override public String run(String command, long timeoutMs) {
            ran.add(command);
            return output;
        }
    }

    private static org.json.JSONObject root(String path, String body, FakeRoot fake) throws Exception {
        DeviceControlRoutes.RootAccess before = DeviceControlRoutes.rootAccess;
        DeviceControlRoutes.rootAccess = fake;
        try {
            return DeviceControlRoutes.handle(null, "POST", path, body);
        } finally {
            DeviceControlRoutes.rootAccess = before;
        }
    }

    @Test
    public void rootRoutes_arePostOnly() {
        for (String path : new String[]{"/v1/root/lock", "/v1/root/reboot", "/v1/root/wifi",
                "/v1/root/data", "/v1/root/airplane", "/v1/root/dnd"}) {
            assertTrue(path, DeviceControlRoutes.handles("POST", path));
            assertFalse(path, DeviceControlRoutes.handles("GET", path));
        }
        assertFalse(DeviceControlRoutes.handles("POST", "/v1/root/shell"));
    }

    @Test
    public void rootToggles_needABooleanOn() throws Exception {
        for (String path : new String[]{"/v1/root/wifi", "/v1/root/data", "/v1/root/airplane",
                "/v1/root/dnd"}) {
            FakeRoot fake = new FakeRoot(true, true);
            assertEquals(path, 400, root(path, "{}", fake).getInt("_statusCode"));
            assertEquals(path, 400, root(path, "{\"on\":\"yes\"}", fake).getInt("_statusCode"));
            assertTrue(path, fake.ran.isEmpty());
        }
    }

    @Test
    public void rootRoutes_answer409WithoutRoot() throws Exception {
        FakeRoot fake = new FakeRoot(false, true);
        org.json.JSONObject answer = root("/v1/root/lock", "", fake);
        assertEquals(409, answer.getInt("_statusCode"));
        assertEquals("root_unavailable", answer.getString("error"));
        assertEquals(409, root("/v1/root/wifi", "{\"on\":true}", fake).getInt("_statusCode"));
        assertTrue(fake.ran.isEmpty());
    }

    @Test
    public void reboot_needsTheOptIn() throws Exception {
        FakeRoot fake = new FakeRoot(true, false);
        org.json.JSONObject answer = root("/v1/root/reboot", "", fake);
        assertEquals(403, answer.getInt("_statusCode"));
        assertEquals("reboot_not_allowed", answer.getString("error"));
        assertTrue(fake.ran.isEmpty());
    }

    @Test
    public void reboot_rejectsAnUnknownTarget() throws Exception {
        FakeRoot fake = new FakeRoot(true, true);
        assertEquals(400, root("/v1/root/reboot", "{\"target\":\"fastboot\"}", fake)
            .getInt("_statusCode"));
        assertTrue(fake.ran.isEmpty());
    }

    @Test
    public void rootRoutes_runTheirCommand() throws Exception {
        FakeRoot fake = new FakeRoot(true, true);
        assertTrue(root("/v1/root/reboot", "{\"target\":\"recovery\"}", fake).getBoolean("ok"));
        assertTrue(root("/v1/root/reboot", "", fake).getBoolean("ok"));
        assertTrue(root("/v1/root/dnd", "{\"on\":false}", fake).getBoolean("ok"));
        assertEquals(Arrays.asList("svc power reboot recovery || reboot recovery",
            "svc power reboot || reboot", "cmd notification set_dnd off"), fake.ran);
        fake.output = "Error: su died";
        assertEquals(500, root("/v1/root/lock", "", fake).getInt("_statusCode"));
    }
}
