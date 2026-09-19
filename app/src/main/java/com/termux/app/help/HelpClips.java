package com.termux.app.help;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Which recorded clip belongs to which help topic, read once from the guide manifest shipped in
 * the APK's assets.
 *
 * <p>The manifest is the recording run's own record: a list of topics, each with the clip ids that
 * cover it and one sentence saying what the clip shows, and a table of clips with the pixel size
 * the strip was cropped to. A topic the run could not capture lists no clips and gets none here; a
 * topic that borrows a neighbour's recording already names that clip, so borrowing needs no rule
 * of its own. Where a topic lists several clips the page shows the first.
 *
 * <p>The file is a couple of thousand lines, so it is parsed once per process and kept. {@link
 * #prime(Context)} does that on a thread of its own when the help centre is built, long before a
 * topic page asks for a clip; a reader who arrives first simply waits for the same parse.
 */
public final class HelpClips {

    /** One recorded strip: where it lives in the assets, how big it was cropped, what it shows. */
    public static final class Clip {
        /** The clip's id in the manifest, which is also its file name without the extension. */
        public final String id;
        /** The path to open on the asset manager. */
        public final String assetPath;
        /** The crop's pixel size, or 0 when the manifest did not say; the aspect ratio comes from it. */
        public final int width;
        public final int height;
        /** The one sentence describing the gesture, for a reader who cannot see it. */
        public final String alt;

        Clip(String id, String assetPath, int width, int height, String alt) {
            this.id = id;
            this.assetPath = assetPath;
            this.width = width;
            this.height = height;
            this.alt = alt;
        }
    }

    @VisibleForTesting
    static final String DIRECTORY = "help-guide";
    @VisibleForTesting
    static final String MANIFEST = DIRECTORY + "/manifest.json";

    private static final Object LOCK = new Object();
    private static HelpClips cached;

    private final Map<String, Clip> byTopic;

    private HelpClips(Map<String, Clip> byTopic) {
        this.byTopic = byTopic;
    }

    /** Read the manifest off the main thread, so the first topic page finds it already parsed. */
    static void prime(Context context) {
        synchronized (LOCK) {
            if (cached != null) return;
        }
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> of(app), "help-clips");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The parsed manifest. Empty, never null, when the assets carry no guide or the file cannot be
     * read: a page with no clip is the page as it read before.
     */
    public static HelpClips of(Context context) {
        synchronized (LOCK) {
            if (cached != null) return cached;
            HelpClips clips = read(context);
            cached = clips;
            return clips;
        }
    }

    private static HelpClips read(Context context) {
        try (InputStream input = context.getApplicationContext().getAssets().open(MANIFEST)) {
            return parse(text(input));
        } catch (IOException | RuntimeException failure) {
            HelpLog.d("help clips: no guide manifest (" + failure + ")");
            return new HelpClips(Collections.<String, Clip>emptyMap());
        }
    }

    /** The clip to show on a topic's page, or null when the run captured none for it. */
    @Nullable
    public Clip forTopic(@Nullable String topicId) {
        return topicId == null ? null : byTopic.get(topicId);
    }

    /** How many topics the manifest has a clip for. */
    public int size() {
        return byTopic.size();
    }

    /** The topics the manifest names, for a test that checks they are all reachable. */
    @VisibleForTesting
    java.util.Set<String> topicIds() {
        return Collections.unmodifiableSet(byTopic.keySet());
    }

    @VisibleForTesting
    static HelpClips parse(String json) {
        JSONObject root = jsonObject(json);
        Map<String, Clip> byTopic = new LinkedHashMap<>();
        JSONObject sizes = root.optJSONObject("clips");
        JSONArray topics = root.optJSONArray("topics");
        for (int i = 0; topics != null && i < topics.length(); i++) {
            JSONObject topic = topics.optJSONObject(i);
            if (topic == null) continue;
            String topicId = topic.optString("topic", "");
            JSONArray clips = topic.optJSONArray("clips");
            if (topicId.isEmpty() || clips == null || clips.length() == 0) continue;
            // Several recordings of one topic: the page shows the first, which is the one the run
            // listed as the gesture itself.
            String clipId = clips.optString(0, "");
            if (clipId.isEmpty()) continue;
            JSONObject size = sizes == null ? null : sizes.optJSONObject(clipId);
            String file = size == null ? clipId + ".mp4" : size.optString("video", clipId + ".mp4");
            byTopic.put(topicId, new Clip(clipId, DIRECTORY + "/" + file,
                size == null ? 0 : size.optInt("width", 0),
                size == null ? 0 : size.optInt("height", 0),
                topic.optString("alt", "")));
        }
        return new HelpClips(byTopic);
    }

    private static JSONObject jsonObject(String json) {
        try {
            return new JSONObject(json);
        } catch (org.json.JSONException malformed) {
            throw new IllegalArgumentException("help guide manifest is not an object", malformed);
        }
    }

    private static String text(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[8192];
        for (int read = input.read(buffer); read > 0; read = input.read(buffer))
            out.write(buffer, 0, read);
        return out.toString("UTF-8");
    }

    @VisibleForTesting
    static void forget() {
        synchronized (LOCK) {
            cached = null;
        }
    }
}
