package com.termux.app.chrome.scene;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The example shaders bundled in {@code assets/shaders/examples}: an {@code index.json} naming
 * each ({@code slug}, {@code name}, {@code quality}, {@code rate}) and one {@code <slug>.glsl}
 * per entry. Read-only: opening one in the editor and applying saves a copy as the user's own.
 */
public final class ShaderExamples {

    public static final String DIR = "shaders/examples";

    /** One example: its slug (the file name), name and the knobs it ships with. */
    public static final class Example {
        @NonNull public final String slug;
        @NonNull public final String name;
        @NonNull public final ShaderSafety.Quality quality;
        public final int rateHz;
        /** The shader text, or null before it is read. */
        @Nullable public final String source;

        public Example(@NonNull String slug, @NonNull String name, @NonNull ShaderSafety.Quality quality,
                       int rateHz, @Nullable String source) {
            this.slug = slug;
            this.name = name;
            this.quality = quality;
            this.rateHz = rateHz;
            this.source = source;
        }

        /** The knobs as a saved shader's settings (no texture). */
        @NonNull
        public ShaderStore.Meta meta() {
            return new ShaderStore.Meta(name, quality, rateHz, null);
        }
    }

    private ShaderExamples() {}

    /**
     * The entries of an {@code index.json}, in file order; entries without a safe slug or a name
     * are skipped, and so is everything when the text is not a JSON array. Sources are left null.
     */
    @NonNull
    public static List<Example> parseIndex(@Nullable String json) {
        List<Example> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String slug = o.optString("slug", "");
                String name = o.optString("name", "").trim();
                if (!WallpaperMotion.safeName(slug) || name.isEmpty()) continue;
                out.add(new Example(slug, name, ShaderSafety.Quality.parse(o.optString("quality", "half")),
                    o.optInt("rate", 30), null));
            }
        } catch (JSONException e) {
            out.clear();
        }
        return out;
    }

    /** Every example with its source; those whose file is missing are left out. */
    @WorkerThread
    @NonNull
    public static List<Example> load(@NonNull Context context) {
        AssetManager assets = context.getAssets();
        List<Example> out = new ArrayList<>();
        for (Example e : parseIndex(readAsset(assets, DIR + "/index.json"))) {
            String source = readAsset(assets, DIR + "/" + e.slug + ".glsl");
            if (source == null || source.trim().isEmpty()) continue;
            out.add(new Example(e.slug, e.name, e.quality, e.rateHz, source));
        }
        return out;
    }

    /** One example by slug, or null. */
    @WorkerThread
    @Nullable
    public static Example find(@NonNull Context context, @NonNull String slug) {
        for (Example e : load(context)) if (e.slug.equals(slug)) return e;
        return null;
    }

    @Nullable
    private static String readAsset(@NonNull AssetManager assets, @NonNull String path) {
        try (InputStream in = assets.open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
