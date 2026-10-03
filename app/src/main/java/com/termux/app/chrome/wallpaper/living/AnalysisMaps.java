package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The raw model maps one analysis run leaves in its folder (living-stills.md, Part B.7), decoded
 * to float planes in 0..1: the depth map (near = 1), the subject saliency and one probability
 * plane per scene group. Group names come from {@code scene.json}, never from a fixed order.
 * The Bitmap decoding lives in {@link LivingBitmaps}; this class and its parsers are plain Java.
 */
public final class AnalysisMaps {
    public final float[] depth;
    public final int depthW;
    public final int depthH;
    public final float[] subject;
    public final int subjectW;
    public final int subjectH;
    /** Group name to probability plane, all {@code sceneW x sceneH}. */
    @NonNull public final Map<String, float[]> groups;
    public final int sceneW;
    public final int sceneH;
    /** Model ids the analysis reported, in the order it listed them. */
    @NonNull public final Map<String, String> models;

    public AnalysisMaps(@NonNull float[] depth, int depthW, int depthH,
                        @NonNull float[] subject, int subjectW, int subjectH,
                        @NonNull Map<String, float[]> groups, int sceneW, int sceneH,
                        @NonNull Map<String, String> models) {
        this.depth = depth;
        this.depthW = depthW;
        this.depthH = depthH;
        this.subject = subject;
        this.subjectW = subjectW;
        this.subjectH = subjectH;
        this.groups = groups;
        this.sceneW = sceneW;
        this.sceneH = sceneH;
        this.models = models;
    }

    /**
     * The group names in channel order (scene0 R, G, B, then scene1 ...). Accepts a root array, an
     * array under {@code groups}/{@code order}/{@code names}/{@code classes}/{@code channels}, one
     * array per {@code scene0..2} key, or else the first array of strings found. Blank entries are
     * padding and are dropped from the result only at the end, so channels still line up.
     */
    @NonNull
    public static List<String> parseGroupNames(@Nullable String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            String t = json.trim();
            if (t.startsWith("[")) {
                readStrings(new JSONArray(t), out);
                return out;
            }
            JSONObject root = new JSONObject(t);
            // The analysis job's own layout: one entry per scene image, its channels named in order
            // (a null channel is a gap, kept as "" so later names stay on their channel).
            JSONArray images = root.optJSONArray("images");
            if (images != null) {
                for (int i = 0; i < images.length(); i++) {
                    JSONObject image = images.optJSONObject(i);
                    JSONArray channels = image == null ? null : image.optJSONArray("channels");
                    for (int c = 0; c < 3; c++) {
                        out.add(channels == null || channels.isNull(c) ? "" : channels.optString(c, ""));
                    }
                }
                if (!out.isEmpty()) return out;
            }
            if (root.has("scene0")) {
                for (int i = 0; i < 3; i++) {
                    JSONArray a = root.optJSONArray("scene" + i);
                    if (a == null) break;
                    readStrings(a, out);
                }
                if (!out.isEmpty()) return out;
            }
            for (String key : new String[] {"groups", "order", "names", "classes", "channels"}) {
                JSONArray a = root.optJSONArray(key);
                if (a != null) {
                    readStrings(a, out);
                    return out;
                }
            }
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                JSONArray a = root.optJSONArray(keys.next());
                if (a != null && a.length() > 0 && a.opt(0) instanceof String) {
                    readStrings(a, out);
                    return out;
                }
            }
        } catch (JSONException ignored) {
            out.clear();
        }
        return out;
    }

    private static void readStrings(JSONArray a, List<String> out) {
        for (int i = 0; i < a.length(); i++) out.add(a.optString(i, ""));
    }

    /** Model ids from {@code analysis.json}: every string under {@code models}, keyed by role. */
    @NonNull
    public static Map<String, String> parseModels(@Nullable String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject m = root.optJSONObject("models");
            if (m != null) {
                Iterator<String> keys = m.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    Object v = m.opt(k);
                    if (v instanceof String) out.put(k, (String) v);
                    else if (v instanceof JSONObject) {
                        String id = ((JSONObject) v).optString("id", "");
                        if (!id.isEmpty()) out.put(k, id);
                    }
                }
            } else {
                JSONArray a = root.optJSONArray("models");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        String s = a.optString(i, "");
                        if (!s.isEmpty()) out.put("model" + i, s);
                    }
                }
            }
        } catch (JSONException ignored) {
            // the ids are a record, not an input: a malformed file just records none
        }
        return out;
    }
}
