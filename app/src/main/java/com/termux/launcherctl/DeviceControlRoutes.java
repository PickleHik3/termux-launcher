package com.termux.launcherctl;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.haptics.Haptics;
import com.termux.app.notice.AppNotice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The device routes {@code launcherctl vibrate|torch|battery|volume|toast} sit on: small,
 * self-contained answers about, or requests to, the phone itself, in the shapes the matching
 * {@code termux-*} commands print so a compatibility script can pass the JSON straight through.
 * None of them needs a terminal or the Activity, so unlike the signal routes they never go
 * through {@code TerminalActionDispatcher} and answer while the launcher is in the background.
 *
 * <p>The server's own token check and per-route rate limit have already run by the time
 * {@link #handle} is called; this class only maps a request to a result.
 */
final class DeviceControlRoutes {

    /** Default of {@code termux-vibrate}. */
    static final long DEFAULT_VIBRATE_MS = 1000L;
    /** A runaway script must not be able to hold the motor for minutes. */
    static final long MAX_VIBRATE_MS = 10_000L;

    private DeviceControlRoutes() {
    }

    /** Whether {@code method path} is one of the device routes. */
    static boolean handles(@NonNull String method, @NonNull String path) {
        switch (path) {
            case "/v1/vibrate":
            case "/v1/torch":
            case "/v1/toast":
                return "POST".equals(method);
            case "/v1/battery":
                return "GET".equals(method);
            case "/v1/volume":
                return "GET".equals(method) || "POST".equals(method);
            default:
                return false;
        }
    }

    @NonNull
    static JSONObject handle(@NonNull Context context, @NonNull String method, @NonNull String path,
                             @Nullable String body) throws JSONException {
        JSONObject arguments;
        if (body == null || body.trim().isEmpty()) {
            arguments = new JSONObject();
        } else {
            try {
                arguments = new JSONObject(body);
            } catch (JSONException e) {
                return error(400, "bad_request", "Request body must be a JSON object");
            }
        }
        switch (path) {
            case "/v1/vibrate":
                return vibrate(context, arguments);
            case "/v1/torch":
                return torch(context, arguments);
            case "/v1/battery":
                return battery(context);
            case "/v1/volume":
                return "GET".equals(method) ? volumeList(context) : volumeSet(context, arguments);
            case "/v1/toast":
                return toast(context, arguments);
            default:
                return error(404, "not_found", "Unknown endpoint");
        }
    }

    // ------------------------------------------------------------------------------------ vibrate

    /** {@code {"duration_ms":1000,"force":false}} in, {@code {"ok":true,"duration_ms":N}} out. */
    @NonNull
    private static JSONObject vibrate(@NonNull Context context, @NonNull JSONObject arguments)
            throws JSONException {
        long duration = arguments.optLong("duration_ms", DEFAULT_VIBRATE_MS);
        if (duration <= 0) return error(400, "bad_request", "'duration_ms' must be a positive number");
        duration = Math.min(duration, MAX_VIBRATE_MS);
        boolean force = arguments.optBoolean("force", false);
        // Haptics owns the silent-mode and user-setting decisions; force is its own override.
        Haptics.vibrate(context, duration, force);
        return ok().put("duration_ms", duration).put("force", force);
    }

    // -------------------------------------------------------------------------------------- torch

    /** {@code {"on":true}} in, {@code {"ok":true,"on":true,"camera":"0"}} out. */
    @NonNull
    private static JSONObject torch(@NonNull Context context, @NonNull JSONObject arguments)
            throws JSONException {
        if (!arguments.has("on") || arguments.isNull("on")) {
            return error(400, "bad_request", "Missing 'on' (true or false)");
        }
        boolean on = arguments.optBoolean("on", false);
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return error(404, "no_torch", "This device has no camera service");
        try {
            String cameraId = null;
            for (String id : manager.getCameraIdList()) {
                Boolean flash = manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) return error(404, "no_torch", "No camera on this device has a flash");
            // No permission is needed: setTorchMode is exempt from the CAMERA grant.
            manager.setTorchMode(cameraId, on);
            return ok().put("on", on).put("camera", cameraId);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            // In use by another app, or the camera went away between listing and switching.
            return error(409, "torch_unavailable", "The torch could not be switched: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------ battery

    /**
     * The fields and value names {@code termux-battery-status} prints. The sticky
     * {@code ACTION_BATTERY_CHANGED} read is the same one {@code TaiDeviceConditions} does for
     * its bench guards, but that reader folds it to a percentage and a boolean; this one keeps
     * the raw extras, so it reads the broadcast itself.
     */
    @NonNull
    private static JSONObject battery(@NonNull Context context) throws JSONException {
        Intent status = null;
        try {
            status = context.getApplicationContext()
                .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (RuntimeException ignored) {
        }
        if (status == null) return error(503, "battery_unavailable", "The battery state could not be read");
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percentage = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        int temperatureTenths = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        long currentMicroAmps = 0;
        try {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (manager != null) {
                currentMicroAmps = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            }
        } catch (RuntimeException ignored) {
        }
        JSONObject out = new JSONObject();
        out.put("health", healthName(status.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)));
        out.put("percentage", percentage);
        out.put("plugged", pluggedName(status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)));
        out.put("status", statusName(status.getIntExtra(BatteryManager.EXTRA_STATUS, 0)));
        out.put("temperature", temperatureTenths == Integer.MIN_VALUE ? JSONObject.NULL
            : (Object) Double.valueOf(temperatureTenths / 10.0));
        out.put("current", currentMicroAmps);
        return out;
    }

    @NonNull
    static String healthName(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_COLD: return "COLD";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "DEAD";
            case BatteryManager.BATTERY_HEALTH_GOOD: return "GOOD";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "OVERHEAT";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "OVER_VOLTAGE";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "UNSPECIFIED_FAILURE";
            default: return "UNKNOWN";
        }
    }

    @NonNull
    static String pluggedName(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "PLUGGED_AC";
            case BatteryManager.BATTERY_PLUGGED_USB: return "PLUGGED_USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "PLUGGED_WIRELESS";
            default: return "UNPLUGGED";
        }
    }

    @NonNull
    static String statusName(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "CHARGING";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "DISCHARGING";
            case BatteryManager.BATTERY_STATUS_FULL: return "FULL";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "NOT_CHARGING";
            default: return "UNKNOWN";
        }
    }

    // ------------------------------------------------------------------------------------ volume

    private static final String[] VOLUME_NAMES = {"alarm", "music", "notification", "ring", "system", "call"};
    private static final int[] VOLUME_STREAMS = {
        AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_RING, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_VOICE_CALL};

    private static JSONObject volumeEntry(@NonNull AudioManager audio, int index) throws JSONException {
        return new JSONObject().put("stream", VOLUME_NAMES[index])
            .put("volume", audio.getStreamVolume(VOLUME_STREAMS[index]))
            .put("max_volume", audio.getStreamMaxVolume(VOLUME_STREAMS[index]));
    }

    /** {@code {"ok":true,"streams":[{stream,volume,max_volume}...]}}, termux-volume's array wrapped. */
    @NonNull
    private static JSONObject volumeList(@NonNull Context context) throws JSONException {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return error(503, "audio_unavailable", "The audio service is not available");
        JSONArray streams = new JSONArray();
        for (int i = 0; i < VOLUME_NAMES.length; i++) streams.put(volumeEntry(audio, i));
        return ok().put("streams", streams);
    }

    /** {@code {"stream":"music","volume":7}} in, the stream's new entry out. */
    @NonNull
    private static JSONObject volumeSet(@NonNull Context context, @NonNull JSONObject arguments)
            throws JSONException {
        String name = arguments.optString("stream", "").trim().toLowerCase(java.util.Locale.ROOT);
        int index = -1;
        for (int i = 0; i < VOLUME_NAMES.length; i++) {
            if (VOLUME_NAMES[i].equals(name)) index = i;
        }
        if (index < 0) {
            return error(400, "bad_request",
                "'stream' must be one of alarm, music, notification, ring, system, call");
        }
        if (!arguments.has("volume") || !(arguments.opt("volume") instanceof Number)) {
            return error(400, "bad_request", "'volume' must be a whole number");
        }
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return error(503, "audio_unavailable", "The audio service is not available");
        int max = audio.getStreamMaxVolume(VOLUME_STREAMS[index]);
        int value = Math.max(0, Math.min(max, arguments.optInt("volume", 0)));
        try {
            audio.setStreamVolume(VOLUME_STREAMS[index], value, 0);
        } catch (SecurityException e) {
            // Ring and notification volume are refused while Do Not Disturb is on.
            return error(403, "volume_refused", "Android refused to change that stream: " + e.getMessage());
        }
        JSONObject entry = volumeEntry(audio, index);
        return ok().put("stream", entry.getString("stream")).put("volume", entry.getInt("volume"))
            .put("max_volume", entry.getInt("max_volume"));
    }

    // -------------------------------------------------------------------------------------- toast

    /**
     * {@code {"text":"hi","short":false}} in. This goes through {@link AppNotice}, the app's one
     * toast surface: it draws the themed in-app pill while the launcher is on screen and, when
     * there is no window of ours to draw into, falls back to a stock {@code Toast} by itself
     * (see {@code AppNotice.deliver}), so no visibility check is needed here. It is safe from
     * this worker thread because {@code AppNotice} hops to the main thread. Long is the default,
     * since a script's message is not something the person was waiting for; {@code short} is the
     * quick flash.
     */
    @NonNull
    private static JSONObject toast(@NonNull Context context, @NonNull JSONObject arguments)
            throws JSONException {
        String text = arguments.optString("text", "");
        if (text.trim().isEmpty()) return error(400, "bad_request", "Missing 'text'");
        boolean shortToast = arguments.optBoolean("short", false);
        AppNotice.show(context, text, !shortToast);
        return ok().put("length", text.length());
    }

    // ------------------------------------------------------------------------------------ helpers

    @NonNull
    private static JSONObject ok() throws JSONException {
        return new JSONObject().put("ok", true);
    }

    @NonNull
    private static JSONObject error(int status, @NonNull String code, @NonNull String message)
            throws JSONException {
        return new JSONObject().put("ok", false).put("error", code).put("message", message)
            .put("_statusCode", status);
    }
}
