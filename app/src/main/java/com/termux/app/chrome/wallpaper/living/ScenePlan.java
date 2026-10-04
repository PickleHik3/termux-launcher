package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The validated Scene Plan (v2) the director produces and {@code recipe.json} persists
 * (project-docs/active/animated-wallpaper/living-stills-part-c-v2.md). Pure Java: only org.json.
 * Instances come from {@link SceneReader#parse} or {@link #fromJson}; both leave only elements and
 * scene fields that passed validation, so everything downstream may trust the vocabulary.
 */
public final class ScenePlan {
    public static final List<String> KINDS = Arrays.asList("sky", "clouds", "water", "falling_water", "grass",
        "trees", "flowers", "mountain", "ground", "building", "lights", "figure", "animal", "vehicle", "other");
    public static final List<String> MOTIONS = Arrays.asList("none", "still", "drift", "sway", "wind_wave",
        "ripple", "flow", "glow", "flicker", "twinkle");
    public static final List<String> PARTICLES = Arrays.asList("none", "petals", "leaves", "dust", "fireflies",
        "snow", "rain", "embers", "glints", "sparks");
    public static final List<String> DEPTHS = Arrays.asList("near", "middle", "far");
    public static final List<String> TIMES = Arrays.asList("dawn", "day", "dusk", "night");
    public static final List<String> WEATHERS = Arrays.asList("clear", "cloudy", "misty", "rain", "snow");

    /** One element of the picture. */
    public static final class Element {
        @NonNull public final String name;
        @NonNull public final String kind;
        /** {top, left, bottom, right} on the 0..1000 grid, top &lt; bottom and left &lt; right. */
        @NonNull public final int[] box;
        @NonNull public final String depth;
        @NonNull public final String motion;
        /** True when the scene's {@code do_not_animate} names this element. */
        public final boolean still;

        Element(@NonNull String name, @NonNull String kind, @NonNull int[] box, @NonNull String depth,
                @NonNull String motion, boolean still) {
            this.name = name;
            this.kind = kind;
            this.box = box;
            this.depth = depth;
            this.motion = motion;
            this.still = still;
        }
    }

    /** The whole-picture description. */
    public static final class Scene {
        @Nullable public final String time;
        @Nullable public final String weather;
        @NonNull public final String lightDirection;
        @NonNull public final String particles;
        @NonNull public final String mood;
        @NonNull public final List<String> doNotAnimate;

        Scene(@Nullable String time, @Nullable String weather, @NonNull String lightDirection,
              @NonNull String particles, @NonNull String mood, @NonNull List<String> doNotAnimate) {
            this.time = time;
            this.weather = weather;
            this.lightDirection = lightDirection;
            this.particles = particles;
            this.mood = mood;
            this.doNotAnimate = doNotAnimate;
        }
    }

    @NonNull public final List<Element> elements;
    @NonNull public final Scene scene;
    /** The validated plan as JSON, exactly what {@link #toJson} returns. */
    @NonNull public final JSONObject raw;

    ScenePlan(@NonNull List<Element> elements, @NonNull Scene scene) {
        this.elements = Collections.unmodifiableList(elements);
        this.scene = scene;
        this.raw = build(this.elements, scene);
    }

    /** The validated plan (no {@code note}); a copy, so callers may not alter the plan. */
    @NonNull
    public JSONObject toJson() {
        try {
            return new JSONObject(raw.toString());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Rebuilds a plan from {@link #toJson} output (or a Gallery answer); {@code null} when it does not validate. */
    @Nullable
    public static ScenePlan fromJson(@Nullable JSONObject json) {
        return json == null ? null : SceneReader.parseObject(json);
    }

    /** The elements of one kind, in plan order. */
    @NonNull
    public List<Element> elementsOfKind(@NonNull String kind) {
        List<Element> out = new ArrayList<>();
        for (Element e : elements) if (e.kind.equals(kind)) out.add(e);
        return out;
    }

    public boolean has(@NonNull String kind) {
        for (Element e : elements) if (e.kind.equals(kind)) return true;
        return false;
    }

    /** The names of the elements that must stay still, in plan order. */
    @NonNull
    public List<String> stillNames() {
        List<String> out = new ArrayList<>();
        for (Element e : elements) if (e.still) out.add(e.name);
        return out;
    }

    @NonNull
    private static JSONObject build(List<Element> elements, Scene scene) {
        try {
            JSONArray els = new JSONArray();
            for (Element e : elements) {
                els.put(new JSONObject().put("name", e.name).put("kind", e.kind)
                    .put("box", new JSONArray().put(e.box[0]).put(e.box[1]).put(e.box[2]).put(e.box[3]))
                    .put("depth", e.depth).put("motion", e.motion));
            }
            JSONObject s = new JSONObject();
            if (scene.time != null) s.put("time", scene.time);
            if (scene.weather != null) s.put("weather", scene.weather);
            s.put("light_direction", scene.lightDirection).put("particles", scene.particles)
                .put("mood", scene.mood);
            JSONArray dna = new JSONArray();
            for (String n : scene.doNotAnimate) dna.put(n);
            s.put("do_not_animate", dna);
            return new JSONObject().put("elements", els).put("scene", s);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @NonNull
    static String lower(@NonNull String s) {
        return s.trim().toLowerCase(Locale.US);
    }
}
