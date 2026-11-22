package com.termux.app.sound;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * The launcherctl routes the media helper reports through ({@code launcherctl media …},
 * decision 10). Background-safe: nothing here needs an activity.
 * <ul>
 *   <li>{@code POST /v1/media/player?name=N&pid=P} — the body is dbus-send's printed reply to
 *       {@code Properties.GetAll} for the player.</li>
 *   <li>{@code POST /v1/media/gone?name=N}</li>
 *   <li>{@code POST /v1/media/seeked?name=N&position=MICROSECONDS}</li>
 *   <li>{@code POST /v1/media/streams?playing=COUNT}</li>
 * </ul>
 */
public final class SoundRoutes {

    private static final String PLAYER = "/v1/media/player";
    private static final String GONE = "/v1/media/gone";
    private static final String SEEKED = "/v1/media/seeked";
    private static final String STREAMS = "/v1/media/streams";

    private SoundRoutes() {}

    public static boolean handles(@NonNull String method, @NonNull String path) {
        if (!"POST".equals(method)) return false;
        return PLAYER.equals(path) || GONE.equals(path) || SEEKED.equals(path)
            || STREAMS.equals(path);
    }

    @NonNull
    public static JSONObject handle(@NonNull Context context, @NonNull String path,
                                    @NonNull Map<String, String> query, @Nullable String body)
            throws JSONException {
        LauncherSound sound = LauncherSound.get(context);
        if (STREAMS.equals(path)) {
            Long playing = number(query.get("playing"));
            if (playing == null || playing < 0) return badRequest("playing must be a count");
            sound.onStreams((int) Math.min(Integer.MAX_VALUE, playing));
            return ok();
        }
        String name = query.get("name");
        if (!isPlayerName(name)) return badRequest("name must be an MPRIS player's bus name");
        switch (path) {
            case PLAYER: {
                Long pid = number(query.get("pid"));
                sound.onPlayerReport(name, pid == null || pid > Integer.MAX_VALUE ? 0
                    : pid.intValue(), body);
                return ok();
            }
            case GONE:
                sound.onPlayerGone(name);
                return ok();
            default: {
                Long position = number(query.get("position"));
                if (position == null) return badRequest("position must be in microseconds");
                sound.onPlayerSeeked(name, position);
                return ok();
            }
        }
    }

    /** {@code org.mpris.MediaPlayer2.<something>}, in the characters a bus name may hold. */
    static boolean isPlayerName(@Nullable String name) {
        return name != null && name.startsWith(MprisReply.MPRIS_PREFIX)
            && name.length() > MprisReply.MPRIS_PREFIX.length() && name.length() <= 255
            && name.matches("[A-Za-z0-9._-]+");
    }

    @Nullable
    private static Long number(@Nullable String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private static JSONObject ok() throws JSONException {
        return new JSONObject().put("ok", true);
    }

    @NonNull
    private static JSONObject badRequest(@NonNull String message) throws JSONException {
        return new JSONObject().put("ok", false).put("error", "bad_request")
            .put("message", message).put("_statusCode", 400);
    }
}
