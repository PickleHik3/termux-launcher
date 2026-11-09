package com.termux.app.x11;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the user chose for Linux apps' graphics (#162): the profile every app is started with
 * ({@link #AUTO} keeps the probe's ranking), the apps that draw in software whatever that profile
 * is, and the last benchmark's result per profile. The launcher no longer decides alone; the
 * Graphics acceleration page writes here and {@link X11LinuxAppRunner#installedEnv} reads it on
 * every launch.
 *
 * <p>Kept in the launcher's own preferences file, read fresh on every call rather than cached:
 * the page and the runner each hold an instance, and a pick made on the page must reach the very
 * next tap in the drawer. {@link SharedPreferences} is already the process's one in-memory copy.
 */
public final class X11GpuProfileStore {

    /** The pick that follows the probe's ranking: the launcher's choice, as it was before the page. */
    public static final String AUTO = "auto";

    private static final String PREFS_KEY_PICK = "x11_gpu_profile_pick";
    private static final String PREFS_KEY_SOFTWARE_APPS_V1 = "x11_gpu_software_apps_v1";
    private static final String PREFS_KEY_RESULTS_V1 = "x11_gpu_benchmark_v1";

    /**
     * Where {@code termux-x11-gpu-setup} leaves its per-profile results, one tab-separated line
     * each ({@link Results#parse}). The script owns the measuring, this store the keeping.
     */
    public static final String RESULTS_FILE_PATH = TermuxConstants.TERMUX_HOME_DIR_PATH
        + "/.config/termux-launcher/x11-gpu-results.tsv";

    /** How one profile's test ended, in the script's words. */
    public enum Outcome {
        OK("ok"),
        CRASHED("crashed"),
        TIMED_OUT("timed-out"),
        NOT_ACCELERATED("not-accelerated"),
        SKIPPED("skipped"),
        UNAVAILABLE("unavailable");

        @NonNull public final String id;

        Outcome(@NonNull String id) { this.id = id; }

        @Nullable
        public static Outcome of(@Nullable String id) {
            for (Outcome outcome : values()) if (outcome.id.equals(id)) return outcome;
            return null;
        }
    }

    /** One profile's last result. */
    public static final class Score {
        @NonNull public final String profile;
        @NonNull public final Outcome outcome;
        /** glmark2's score; 0 when the test did not finish. */
        public final int score;
        /** The GL renderer the test drew with, as glmark2 named it; empty when unknown. */
        @NonNull public final String renderer;

        public Score(@NonNull String profile, @NonNull Outcome outcome, int score, @NonNull String renderer) {
            this.profile = profile;
            this.outcome = outcome;
            this.score = score;
            this.renderer = renderer;
        }
    }

    /** One benchmark run: when it finished and each profile's {@link Score}, in the order tested. */
    public static final class Results {
        public final long timestamp;
        @NonNull public final Map<String, Score> byProfile;

        public Results(long timestamp, @NonNull Map<String, Score> byProfile) {
            this.timestamp = timestamp;
            this.byProfile = Collections.unmodifiableMap(new LinkedHashMap<>(byProfile));
        }

        @Nullable
        public Score get(@NonNull String profile) {
            return byProfile.get(profile);
        }

        /**
         * The script's file: {@code profile <tab> outcome <tab> score <tab> renderer} per line,
         * {@code #} lines ignored, unknown profiles or outcomes skipped. Null when no line reads.
         */
        @Nullable
        public static Results parse(@NonNull String text, long timestamp) {
            Map<String, Score> scores = new LinkedHashMap<>();
            for (String line : text.split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length < 3) continue;
                X11GpuProbe.Profile profile = X11GpuProbe.Profile.of(fields[0].trim());
                Outcome outcome = Outcome.of(fields[1].trim());
                if (profile == null || outcome == null) continue;
                int score;
                try {
                    score = Integer.parseInt(fields[2].trim());
                } catch (NumberFormatException e) {
                    score = 0;
                }
                String renderer = fields.length > 3 ? fields[3].trim() : "";
                scores.put(profile.id, new Score(profile.id, outcome, Math.max(0, score), renderer));
            }
            return scores.isEmpty() ? null : new Results(timestamp, scores);
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            JSONArray array = new JSONArray();
            for (Score score : byProfile.values()) {
                array.put(new JSONObject().put("profile", score.profile).put("outcome", score.outcome.id)
                    .put("score", score.score).put("renderer", score.renderer));
            }
            return new JSONObject().put("timestamp", timestamp).put("profiles", array);
        }

        @Nullable
        static Results fromJson(@NonNull JSONObject json) {
            JSONArray array = json.optJSONArray("profiles");
            if (array == null) return null;
            Map<String, Score> scores = new LinkedHashMap<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                X11GpuProbe.Profile profile = X11GpuProbe.Profile.of(item.optString("profile", ""));
                Outcome outcome = Outcome.of(item.optString("outcome", ""));
                if (profile == null || outcome == null) continue;
                scores.put(profile.id, new Score(profile.id, outcome, item.optInt("score", 0),
                    item.optString("renderer", "")));
            }
            return scores.isEmpty() ? null : new Results(json.optLong("timestamp", 0L), scores);
        }
    }

    @NonNull private final SharedPreferences sharedPreferences;

    public X11GpuProfileStore(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
    }

    /** {@link #AUTO}, or a {@link X11GpuProbe.Profile#id}. Anything else stored reads as {@link #AUTO}. */
    @NonNull
    public String pick() {
        String stored = sharedPreferences.getString(PREFS_KEY_PICK, AUTO);
        return stored != null && X11GpuProbe.Profile.of(stored) != null ? stored : AUTO;
    }

    /** Stores {@code pick}; {@link #AUTO} or an unknown id clears it back to following the ranking. */
    public void setPick(@NonNull String pick) {
        if (X11GpuProbe.Profile.of(pick) == null) {
            sharedPreferences.edit().remove(PREFS_KEY_PICK).apply();
        } else {
            sharedPreferences.edit().putString(PREFS_KEY_PICK, pick).apply();
        }
    }

    /** Whether {@code appId} ({@link LinuxAppCatalog.LinuxApp#id}) draws in software whatever the pick. */
    public boolean drawsInSoftware(@NonNull String appId) {
        return softwareApps().contains(appId);
    }

    public void setDrawsInSoftware(@NonNull String appId, boolean software) {
        Set<String> ids = softwareApps();
        boolean changed = software ? ids.add(appId) : ids.remove(appId);
        if (!changed) return;
        JSONArray array = new JSONArray();
        for (String id : ids) array.put(id);
        sharedPreferences.edit().putString(PREFS_KEY_SOFTWARE_APPS_V1, array.toString()).apply();
    }

    /** The apps set to draw in software, sorted; a copy. */
    @NonNull
    public Set<String> softwareApps() {
        Set<String> ids = new TreeSet<>();
        String raw = sharedPreferences.getString(PREFS_KEY_SOFTWARE_APPS_V1, "");
        if (raw == null || raw.trim().isEmpty()) return ids;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "");
                if (!id.isEmpty()) ids.add(id);
            }
        } catch (JSONException ignored) {
            // A damaged value is no override at all, never a crash on the way to an app.
        }
        return ids;
    }

    /** The last benchmark, or null when none has been read. */
    @Nullable
    public Results results() {
        String raw = sharedPreferences.getString(PREFS_KEY_RESULTS_V1, "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Results.fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            return null;
        }
    }

    public void saveResults(@NonNull Results results) {
        try {
            sharedPreferences.edit().putString(PREFS_KEY_RESULTS_V1, results.toJson().toString()).apply();
        } catch (JSONException ignored) {
            // Nothing above throws for these types.
        }
    }

    /**
     * Adds the user's side to the probe's JSON ({@code launcherctl x11 gpu}): {@code pick}, and
     * {@code in_use} / {@code in_use_env}, what an app without its own switch is started with now.
     * Fields are only ever added here, so older readers of the JSON keep working.
     */
    public void putChoice(@NonNull JSONObject json, @NonNull X11GpuProbe.Result gpu) {
        String pick = pick();
        X11LinuxAppRunner.GpuChoice choice = X11LinuxAppRunner.installedEnv(gpu, pick, false);
        try {
            json.put("pick", pick);
            json.put("in_use", choice.profile == null ? JSONObject.NULL : choice.profile.id);
            json.put("in_use_env", new JSONArray(choice.env));
            json.put("software_apps", new JSONArray(softwareApps()));
        } catch (JSONException ignored) {
            // Nothing above throws for these types.
        }
    }

    /**
     * Takes in the script's results file when it is newer than what is kept. Returns whether
     * anything changed. File work: off the main thread.
     */
    public boolean importResults(@NonNull File file) {
        if (!file.isFile()) return false;
        long modified = file.lastModified();
        Results kept = results();
        if (kept != null && kept.timestamp >= modified) return false;
        String text;
        try {
            text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return false;
        }
        Results read = Results.parse(text, modified);
        if (read == null) return false;
        saveResults(read);
        return true;
    }
}
