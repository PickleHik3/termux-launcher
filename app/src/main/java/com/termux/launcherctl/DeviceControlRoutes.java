package com.termux.launcherctl;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.app.WallpaperManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.app.chrome.ManagedWallpaper;
import com.termux.app.chrome.wallpaper.AnimatedWallpaper;
import com.termux.app.chrome.wallpaper.AnimatedWallpaperStatus;
import com.termux.app.chrome.wallpaper.AnimatedWallpapers;
import com.termux.app.chrome.wallpaper.GeneratedWallpaperApplier;
import com.termux.app.haptics.Haptics;
import com.termux.app.notice.AppNotice;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The device routes {@code launcherctl vibrate|torch|battery|volume|toast|wallpaper} sit on: small,
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
            case "/v1/wallpaper/builtins":
                return "GET".equals(method);
            case "/v1/volume":
            case "/v1/wallpaper":
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
            case "/v1/wallpaper":
                return "GET".equals(method) ? wallpaperGet(context) : wallpaperSet(context, arguments);
            case "/v1/wallpaper/builtins":
                return wallpaperBuiltins();
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

    // ---------------------------------------------------------------------------------- wallpaper

    /** A picture bigger than this is not a wallpaper, and the system re-encodes it in memory. */
    static final long MAX_WALLPAPER_BYTES = 48L * 1024 * 1024;

    /**
     * Whether {@code canonical} is {@code root} or lies under it. Both must already be canonical,
     * so a symlink out of an allowed folder does not pass; the trailing separator keeps
     * {@code /sdcard2} from matching {@code /sdcard}.
     */
    static boolean isUnderAnyRoot(@NonNull String canonical, @NonNull List<String> roots) {
        for (String root : roots) {
            if (canonical.equals(root) || canonical.startsWith(root.endsWith("/") ? root : root + "/")) {
                return true;
            }
        }
        return false;
    }

    /** Where a wallpaper picture may be read from: the Termux home and shared storage. */
    @NonNull
    private static List<String> wallpaperRoots() {
        List<String> roots = new ArrayList<>();
        String[] candidates = {TermuxConstants.TERMUX_HOME_DIR_PATH,
            Environment.getExternalStorageDirectory().getPath(), "/sdcard", "/storage/emulated"};
        for (String candidate : candidates) {
            try {
                roots.add(new File(candidate).getCanonicalPath());
            } catch (IOException ignored) {
            }
        }
        return roots;
    }

    /**
     * {@code {"path":"/sdcard/a.jpg","target":"home|lock|both"}} in, {@code target} defaulting to
     * {@code both}. It runs the same file-and-system half the in-app picker does
     * ({@link ManagedWallpaper#apply}), so the launcher's stored wallpaper id and exact-picture
     * copy follow; that half needs no Activity, so the wallpaper is always set. The launcher-side
     * re-dress goes through {@code setWallpaperModeEnabled}, a broadcast plus a next-resume flag:
     * {@code launcher_refresh} is {@code live} when the launcher is running and
     * {@code on_next_open} when it is not (the wallpaper itself is set either way). Blocks for as
     * long as {@code setStream} does, on the server's worker thread.
     */
    @NonNull
    private static JSONObject wallpaperSet(@NonNull Context context, @NonNull JSONObject arguments)
            throws JSONException {
        String path = arguments.optString("path", "").trim();
        String builtin = arguments.optString("builtin", "").trim();
        if (!path.isEmpty() && !builtin.isEmpty()) {
            return error(400, "bad_request", "'path' and 'builtin' are mutually exclusive");
        }
        if (!builtin.isEmpty()) return wallpaperSetBuiltin(context, arguments, builtin);
        if (path.isEmpty()) return error(400, "bad_request", "Missing 'path' or 'builtin'");
        int flags = ManagedWallpaper.flagsForTarget(arguments.optString("target", "both"));
        if (flags == 0) return error(400, "bad_request", "'target' must be home, lock or both");
        File file = new File(path);
        if (!file.isAbsolute()) return error(400, "bad_request", "'path' must be absolute");
        String canonical;
        try {
            canonical = file.getCanonicalPath();
        } catch (IOException e) {
            return error(400, "bad_request", "'path' could not be resolved");
        }
        if (!isUnderAnyRoot(canonical, wallpaperRoots())) {
            return error(403, "path_not_allowed",
                "The picture must be under the Termux home or shared storage");
        }
        File resolved = new File(canonical);
        if (!resolved.isFile()) return error(404, "not_found", "No such file: " + path);
        if (!resolved.canRead()) return error(403, "unreadable", "The file cannot be read: " + path);
        if (resolved.length() > MAX_WALLPAPER_BYTES) {
            return error(413, "too_large", "The picture is larger than "
                + (MAX_WALLPAPER_BYTES / (1024 * 1024)) + " MB");
        }
        Rect bounds = ManagedWallpaper.fullImageBounds(context, Uri.fromFile(resolved));
        if (bounds == null) return error(415, "not_an_image", "The file is not an image Android can decode");

        Context app = context.getApplicationContext();
        // The picker's cropper writes the pending file that apply() promotes to the exact copy;
        // staging the picture there makes this path end in the same state.
        Uri staged = ManagedWallpaper.stageSource(app, resolved);
        if (staged == null) return error(500, "stage_failed", "The picture could not be copied for applying");
        int[] portrait = ManagedWallpaper.portraitSize(app);
        boolean applied = ManagedWallpaper.apply(app, WallpaperManager.getInstance(app), staged, flags,
            portrait[0], portrait[1], TermuxAppSharedPreferences.build(app, false));
        if (!applied) return error(500, "wallpaper_failed", "Android refused the wallpaper");

        // A photo is the wallpaper now: forget any generated background.
        GeneratedWallpaperApplier.clear(app);
        boolean live = TerminalActionDispatcher.getInstance().isAttached();
        if ((flags & WallpaperManager.FLAG_SYSTEM) != 0) {
            // What the picker does once its apply finishes: the picture is ours again, so turn
            // wallpaper mode on and have the launcher re-dress (now if running, else on resume).
            TermuxActivity.setWallpaperModeEnabled(app, true);
        }
        return ok().put("target", ManagedWallpaper.targetName(flags))
            .put("width", bounds.width()).put("height", bounds.height())
            .put("launcher_refresh", live ? "live" : "on_next_open");
    }

    /** How long a generated background may take to render and be taken by Android. */
    static final long BUILTIN_TIMEOUT_SECONDS = 30;

    /**
     * {@code {"builtin":"aurora","palette":"material|own","target":"home|lock|both"}}. Unknown id
     * is 404; below API 34 the still cannot be rendered, so 409 with {@code reason: "api"}. The
     * still is set whenever the phone can render it; {@code animated} says whether the live frames
     * will play (API 34 and Fancier Glass active), else {@code reason} is {@code fancier_glass_off}.
     * Material colours come from the application context here (no Activity), so they follow the
     * launcher scheme only as far as the application theme does.
     */
    @NonNull
    private static JSONObject wallpaperSetBuiltin(@NonNull Context context, @NonNull JSONObject arguments,
                                                  @NonNull String builtin) throws JSONException {
        AnimatedWallpaper w = AnimatedWallpapers.byId(builtin);
        if (w == null) return error(404, "not_found", "Unknown built-in background: " + builtin);
        String palette = arguments.optString("palette", "material").trim().toLowerCase(java.util.Locale.ROOT);
        if (!"material".equals(palette) && !"own".equals(palette)) {
            return error(400, "bad_request", "'palette' must be material or own");
        }
        String target = arguments.optString("target", "both").trim().toLowerCase(java.util.Locale.ROOT);
        int flags = ManagedWallpaper.flagsForTarget(target);
        if (flags == 0) return error(400, "bad_request", "'target' must be home, lock or both");
        int sdk = android.os.Build.VERSION.SDK_INT;
        if (sdk < 34) {
            return error(409, "unsupported", "Generated backgrounds need Android 14 (API 34) to render")
                .put("reason", "api").put("animated", false);
        }
        Context app = context.getApplicationContext();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
        boolean switchOn = preferences != null && preferences.isFancierGlassEnabled();
        boolean animated = GeneratedWallpaperApplier.offeredFor(sdk, switchOn);
        if (preferences != null && preferences.isAnimatedWallpaperDisabled()) animated = false;

        final CountDownLatch done = new CountDownLatch(1);
        final boolean[] ok = {false};
        final String[] failure = {null};
        applyBuiltin(app, w, palette, ManagedWallpaper.targetName(flags), (success, message) -> {
            ok[0] = success;
            failure[0] = message;
            done.countDown();
        });
        try {
            if (!done.await(BUILTIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return error(500, "wallpaper_failed", "The background took too long to render");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(500, "wallpaper_failed", "Interrupted");
        }
        if (!ok[0]) return error(500, "wallpaper_failed", failure[0] == null ? "Android refused the wallpaper" : failure[0]);

        boolean live = TerminalActionDispatcher.getInstance().isAttached();
        if ((flags & WallpaperManager.FLAG_SYSTEM) != 0) TermuxActivity.setWallpaperModeEnabled(app, true);
        return ok().put("target", ManagedWallpaper.targetName(flags)).put("builtin", w.id())
            .put("palette", palette).put("animated", animated)
            .put("reason", animated ? JSONObject.NULL
                : (preferences != null && preferences.isAnimatedWallpaperDisabled() ? "killed" : "fancier_glass_off"))
            .put("launcher_refresh", live ? "live" : "on_next_open");
    }

    @android.annotation.SuppressLint("NewApi")
    private static void applyBuiltin(@NonNull Context app, @NonNull AnimatedWallpaper w, @NonNull String palette,
                                     @NonNull String target,
                                     @NonNull GeneratedWallpaperApplier.Callback callback) {
        GeneratedWallpaperApplier.apply(app, w, palette, target, callback);
    }

    /** {@code [{id,label,palettes:["material","own"]}]} under {@code builtins}. */
    @NonNull
    private static JSONObject wallpaperBuiltins() throws JSONException {
        JSONArray list = new JSONArray();
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
            list.put(new JSONObject().put("id", w.id()).put("label", w.label())
                .put("palettes", new JSONArray().put("material").put("own")));
        }
        return ok().put("builtins", list);
    }

    /** {@code {"ok":true,"home_id","lock_id","live","managed",...}}: what is on screen now. */
    @NonNull
    private static JSONObject wallpaperGet(@NonNull Context context) throws JSONException {
        Context app = context.getApplicationContext();
        WallpaperManager manager = WallpaperManager.getInstance(app);
        int homeId = -1;
        int lockId = -1;
        boolean live = false;
        try {
            homeId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
            lockId = manager.getWallpaperId(WallpaperManager.FLAG_LOCK);
            live = manager.getWallpaperInfo() != null;
        } catch (RuntimeException ignored) {
        }
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
        int storedId = preferences == null ? -1 : preferences.getManagedWallpaperSystemId();
        String animatedId = preferences == null ? null : preferences.getManagedWallpaperAnimatedId();
        String palette = preferences == null ? null : preferences.getManagedWallpaperAnimatedPalette();
        AnimatedWallpaperStatus status = GeneratedWallpaperApplier.statusProvider();
        boolean playing = status != null && status.playing();
        String reason = playing ? null : status == null ? "inactive" : status.reason();
        if (!playing && reason == null) reason = "inactive";
        return ok().put("home_id", homeId).put("lock_id", lockId).put("live", live)
            .put("managed", storedId > 0 && storedId == homeId)
            .put("animated", animatedId == null ? JSONObject.NULL : animatedId)
            .put("palette", animatedId == null ? JSONObject.NULL : palette)
            .put("playing", playing)
            .put("reason", reason == null ? JSONObject.NULL : reason)
            .put("desired_width", manager.getDesiredMinimumWidth())
            .put("desired_height", manager.getDesiredMinimumHeight());
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
