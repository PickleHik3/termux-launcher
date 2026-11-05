package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The voice benchmark as data: the three bundled clips with their reference transcripts, how many
 * runs a model does, the word error rate, the speed as times real time, and the voice leaderboard.
 * Plain Java so all of it is covered by JVM tests; {@link TaiVoiceBenchRunner} runs it.
 *
 * <p>Each installed speech model runs on the CPU, and on the GPU where the Speech accelerator row
 * offers it ({@link SttAcceleratorChoices#gpuOffered}). Per entry: a cold load, one warm-up
 * transcription of the shortest clip (discarded), then {@link #TIMED_RUNS} timed transcriptions of
 * every clip. A clip's time is the median of its runs; the entry's speed is the audio seconds over
 * the summed medians, and its word error rate is the summed word errors over the summed reference
 * words (each clip's first timed transcript). A GPU load that fell back to the CPU is recorded as
 * such ({@code ranOn: cpu}, {@code gpuFallback}), never as a GPU result.
 *
 * <p>Records go to the same {@code benchmarks.json} as text runs ({@link TaiBenchStore}) with
 * {@code kind: voice} and their own {@link #BENCH_VERSION}, so the text leaderboard never sees them.
 * Layout: {@code id, kind, benchVersion, preset, timestamp, durationMs, modelId, displayName,
 * sizeBytes, sha256, engine, backend, accelerator (asked for), ranOn, gpuFallback?, speculative,
 * runtimeVersion, appVersion, device, conditions, phases{load{ms, wallMs}, clips[{id, audioMs,
 * wallMs{med,min,max,runs}, words, errors, wer, text}], voice{audioMs, wallMs, realtime, words,
 * errors, wer}}, cpuMs?, status, verdict(null), skipReason?}.
 */
public final class TaiVoiceBench {
    public static final String KIND = "voice";
    public static final String BENCH_VERSION = "voice_v1";
    /** The preset id the Run screen and the Check sheet know a voice run by. */
    public static final String PRESET_ID = "voice";
    /** The backend part of a voice entry's key, so it never shares a key with a chat entry. */
    public static final String BACKEND = "stt";
    public static final String ENGINE_WHISPER = "whisper";
    public static final String ENGINE_PARAKEET = "parakeet";

    public static final String PHASE_LOAD = TaiBenchSuite.PHASE_LOAD;
    public static final String PHASE_WARMUP = TaiBenchSuite.PHASE_WARMUP;
    /** The timed transcriptions of every clip. */
    public static final String PHASE_VOICE = "voice";

    /**
     * Timed transcriptions per clip, after one warm-up. Three gives a real median. On a Nothing
     * A065 one pass of the three clips took 17.3 s with Whisper small.en and 3.8 s with Parakeet
     * on the CPU, so both models on CPU and GPU come to about three minutes before any cool-down.
     */
    public static final int TIMED_RUNS = 3;
    /**
     * A result whose words are this wrong or worse is "wrong text": listed apart, never ranked.
     * Half the words wrong is far past any accent or punctuation slip.
     */
    public static final double WRONG_TEXT_WER = 0.5;
    /**
     * Results within this many word errors of each other count as equally accurate, and speed
     * orders them; a result further behind the most accurate one ranks below every result in that
     * band. On pong, Whisper small.en (12%, it drops a phrase at a window boundary) was 0.1x faster
     * than the multilingual small (0.6%) and ranked first on speed alone.
     */
    static final double ACCURACY_BAND_WER = 0.03;

    /** Rough time per entry for the Choose screen; a GPU entry adds its slower load. */
    static final long ESTIMATE_WHISPER_MS = 60_000L;
    static final long ESTIMATE_PARAKEET_MS = 20_000L;
    static final long ESTIMATE_GPU_EXTRA_MS = 15_000L;

    static final String CLIP_ASSET_DIR = "tai/bench/voice/";

    /** One bundled clip: 16 kHz mono PCM16 WAV, public domain, with what is said in it. */
    public static final class Clip {
        @NonNull public final String id;
        @NonNull public final String asset;
        /** The clip's length as the reference names it; the real length is read from the file. */
        public final int nominalSeconds;
        @NonNull public final String reference;

        Clip(@NonNull String id, @NonNull String file, int nominalSeconds, @NonNull String reference) {
            this.id = id;
            this.asset = CLIP_ASSET_DIR + file;
            this.nominalSeconds = nominalSeconds;
            this.reference = reference;
        }
    }

    /**
     * The clips {@code scripts/stt-bench/fetch-clips.sh} makes, byte for byte (its sha256 list), so an
     * in-app run compares with the shell bench. The JFK lines are the inaugural address of 1961; the
     * Bush text is the official transcript of the radio address of 1 Nov 2008 up to where the 45 s
     * cut falls (a pause after "throughout the Nation").
     */
    public static final List<Clip> CLIPS = Collections.unmodifiableList(Arrays.asList(
        new Clip("jfk-3s", "stt-3s-jfk.wav", 3, "And so, my fellow Americans"),
        new Clip("jfk-11s", "stt-11s-jfk.wav", 11,
            "And so, my fellow Americans, ask not what your country can do for you, ask what you can do for your country."),
        new Clip("bush-45s", "stt-45s-bush.wav", 45,
            "Good morning. This Tuesday is Election Day. After months of spirited debate and vigorous campaigning, "
                + "the time has come for Americans to make important decisions about our Nation's future. I encourage "
                + "all Americans to go to the polls and vote. Election season brings out the spirit of competition "
                + "between our political parties, and that competition is an essential part of a healthy democracy. "
                + "But as the campaigns come to a close, Republicans, Democrats, and independents can find common "
                + "ground on at least one point: Our system of representative democracy is one of America's greatest "
                + "strengths. The United States was founded on the belief that all men are created equal. Every "
                + "Election Day, millions of Americans of all races, religions, and backgrounds step into voting "
                + "booths throughout the Nation.")
    ));

    private TaiVoiceBench() {
    }

    // ---- figures ----------------------------------------------------------------------------

    /** Lower case, apostrophes dropped, every other mark a space, split on whitespace. */
    @NonNull
    public static List<String> words(@Nullable String text) {
        List<String> words = new ArrayList<>();
        if (text == null) return words;
        String cleaned = text.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                words.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) words.add(current.toString());
        return words;
    }

    /** Word-level Levenshtein distance: substitutions, insertions and deletions. */
    public static int wordErrors(@NonNull List<String> reference, @NonNull List<String> hypothesis) {
        int[] previous = new int[hypothesis.size() + 1];
        int[] current = new int[hypothesis.size() + 1];
        for (int j = 0; j <= hypothesis.size(); j++) previous[j] = j;
        for (int i = 1; i <= reference.size(); i++) {
            current[0] = i;
            for (int j = 1; j <= hypothesis.size(); j++) {
                int cost = reference.get(i - 1).equals(hypothesis.get(j - 1)) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[hypothesis.size()];
    }

    /** Word errors over the reference's word count; {@code 0} for two empty texts, {@code 1} per word for an empty reference. */
    public static double wordErrorRate(@NonNull String reference, @Nullable String hypothesis) {
        List<String> ref = words(reference);
        List<String> hyp = words(hypothesis);
        int errors = wordErrors(ref, hyp);
        if (ref.isEmpty()) return hyp.isEmpty() ? 0.0 : hyp.size();
        return errors / (double) ref.size();
    }

    /** Audio seconds per wall second: {@code 4.2} reads "4.2× real time". {@code 0} when unmeasured. */
    public static double realtime(long audioMs, double wallMs) {
        return audioMs > 0L && wallMs > 0.0 ? audioMs / wallMs : 0.0;
    }

    /**
     * The length in milliseconds of a PCM WAV given its first bytes (at least the header up to the
     * {@code data} chunk's size); {@code -1} when it is not one.
     */
    public static long wavDurationMs(@NonNull byte[] head, long fileBytes) {
        if (head.length < 12 || head[0] != 'R' || head[1] != 'I' || head[2] != 'F' || head[3] != 'F'
            || head[8] != 'W' || head[9] != 'A' || head[10] != 'V' || head[11] != 'E') return -1L;
        ByteBuffer buffer = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 12;
        int byteRate = 0;
        while (offset + 8 <= head.length) {
            String id = new String(head, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = buffer.getInt(offset + 4) & 0xffffffffL;
            if ("fmt ".equals(id) && offset + 16 <= head.length) {
                byteRate = buffer.getInt(offset + 8 + 8);
            } else if ("data".equals(id)) {
                long available = Math.max(0L, fileBytes - offset - 8);
                long data = Math.min(size, available);
                return byteRate > 0 ? data * 1000L / byteRate : -1L;
            }
            offset += 8 + (int) size + (int) (size & 1);
        }
        return -1L;
    }

    /** The engine family of a speech model, as the store and the screens name it. */
    @NonNull
    public static String engineOf(@NonNull String modelId, @Nullable String path) {
        return TaiSpeechModels.isParakeet(modelId, path) ? ENGINE_PARAKEET : ENGINE_WHISPER;
    }

    /** The Choose screen's rough time for one model on {@code withGpu ? CPU and GPU : CPU}. */
    public static long estimateMs(@NonNull String engine, boolean withGpu) {
        long cpu = ENGINE_PARAKEET.equals(engine) ? ESTIMATE_PARAKEET_MS : ESTIMATE_WHISPER_MS;
        return withGpu ? 2 * cpu + ESTIMATE_GPU_EXTRA_MS : cpu;
    }

    /** {@code CPU}, then {@code GPU} when offered. */
    @NonNull
    public static List<String> accelerators(boolean gpuOffered) {
        List<String> out = new ArrayList<>(2);
        out.add(TaiBenchSuite.ACCELERATOR_CPU);
        if (gpuOffered) out.add(TaiBenchSuite.ACCELERATOR_GPU);
        return out;
    }

    /** Whether {@code record} is a voice record. */
    public static boolean isVoice(@Nullable JSONObject record) {
        return record != null && (KIND.equals(record.optString("kind", ""))
            || BENCH_VERSION.equals(record.optString("benchVersion", "")));
    }

    // ---- one entry's measurements -----------------------------------------------------------

    /** What one clip measured. */
    static final class ClipResult {
        @NonNull final Clip clip;
        final long audioMs;
        final TaiBenchStats.Series wallMs = new TaiBenchStats.Series();
        /** The first timed transcript; graded against the reference. */
        @Nullable String text;

        ClipResult(@NonNull Clip clip, long audioMs) {
            this.clip = clip;
            this.audioMs = audioMs;
        }

        int referenceWords() {
            return words(clip.reference).size();
        }

        int errors() {
            return wordErrors(words(clip.reference), words(text));
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            int words = referenceWords();
            int errors = errors();
            JSONObject json = new JSONObject().put("id", clip.id).put("audioMs", audioMs);
            JSONObject wall = wallMs.toJson();
            if (wall != null) json.put("wallMs", wall);
            return json.put("words", words).put("errors", errors)
                .put("wer", words > 0 ? errors / (double) words : 0.0)
                .put("text", text == null ? "" : text);
        }
    }

    /**
     * The whole entry's figures from its clips: {@code {audioMs, wallMs, realtime, words, errors, wer}},
     * or {@code null} when no clip was timed. Speed counts only clips that were timed, so a stop part
     * way leaves an honest figure for what ran.
     */
    @Nullable
    static JSONObject totals(@NonNull List<ClipResult> clips) throws JSONException {
        long audioMs = 0L;
        double wallMs = 0.0;
        int words = 0;
        int errors = 0;
        int timed = 0;
        for (ClipResult clip : clips) {
            if (clip.wallMs.isEmpty()) continue;
            timed++;
            audioMs += clip.audioMs;
            wallMs += clip.wallMs.median();
            words += clip.referenceWords();
            errors += clip.errors();
        }
        if (timed == 0) return null;
        return new JSONObject().put("audioMs", audioMs).put("wallMs", Math.round(wallMs))
            .put("realtime", realtime(audioMs, wallMs)).put("words", words).put("errors", errors)
            .put("wer", words > 0 ? errors / (double) words : 0.0).put("clips", timed);
    }

    // ---- the leaderboard --------------------------------------------------------------------

    /** The voice phases' headline: {@code phases.voice}, or {@code null}. */
    @Nullable
    public static JSONObject headline(@Nullable JSONObject record) {
        JSONObject phases = record == null ? null : record.optJSONObject("phases");
        return phases == null ? null : phases.optJSONObject("voice");
    }

    /** A record whose GPU load fell back to the CPU: it says nothing about the GPU's speed. */
    public static boolean fellBack(@NonNull JSONObject record) {
        return TaiBenchSuite.ACCELERATOR_GPU.equals(record.optString("accelerator", ""))
            && !TaiBenchSuite.ACCELERATOR_GPU.equals(record.optString("ranOn", TaiBenchSuite.ACCELERATOR_GPU));
    }

    /**
     * The voice leaderboard: one record per entry (model + processor), the latest clean complete one
     * (one that ran warm only stands in when there is no clean one), ranked by speed among the
     * results within {@link #ACCURACY_BAND_WER} of the fewest word errors, then the rest by speed. Listed apart and unranked: wrong text ({@link #WRONG_TEXT_WER} or worse), a GPU
     * entry that fell back to the CPU, and an entry whose latest record is a crash. Only records of
     * {@link #BENCH_VERSION} are looked at. Rows have the text board's keys where they mean the same
     * ({@code key, recordId, modelId, displayName, backend, accelerator, speculative, timestamp, preset,
     * loadMs, status, conditions, runtimeVersion, appVersion, rank}) plus {@code kind, engine, ranOn,
     * gpuFallback, realtime, wer, wrongText}.
     */
    @NonNull
    public static JSONObject leaderboard(@NonNull JSONArray records) throws JSONException {
        Map<String, JSONObject> clean = new LinkedHashMap<>();
        Map<String, JSONObject> warm = new LinkedHashMap<>();
        Map<String, JSONObject> crashed = new LinkedHashMap<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null || !BENCH_VERSION.equals(record.optString("benchVersion", ""))) continue;
            String key = TaiBenchStore.keyOf(record);
            String status = record.optString("status", "");
            if (TaiBenchStore.STATUS_CRASHED.equals(status)) {
                crashed.put(key, record);
                continue;
            }
            if (!TaiBenchStore.STATUS_COMPLETE.equals(status) || headline(record) == null) continue;
            crashed.remove(key);
            (TaiBenchStore.ranWarm(record) ? warm : clean).put(key, record);
        }
        Map<String, JSONObject> latest = new LinkedHashMap<>(clean);
        for (Map.Entry<String, JSONObject> entry : warm.entrySet()) {
            if (!latest.containsKey(entry.getKey())) latest.put(entry.getKey(), entry.getValue());
        }
        List<JSONObject> ranked = new ArrayList<>();
        List<JSONObject> apart = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : latest.entrySet()) {
            JSONObject row = row(entry.getKey(), entry.getValue());
            (row.optBoolean("wrongText", false) || row.optBoolean("gpuFallback", false) ? apart : ranked).add(row);
        }
        for (Map.Entry<String, JSONObject> entry : crashed.entrySet()) apart.add(row(entry.getKey(), entry.getValue()));
        double best = 1.0;
        for (JSONObject row : ranked) best = Math.min(best, row.optDouble("wer", 1.0));
        final double bandEdge = best + ACCURACY_BAND_WER;
        Collections.sort(ranked, (a, b) -> {
            boolean aInBand = a.optDouble("wer", 1.0) <= bandEdge;
            boolean bInBand = b.optDouble("wer", 1.0) <= bandEdge;
            if (aInBand != bInBand) return aInBand ? -1 : 1;
            int bySpeed = Double.compare(b.optDouble("realtime", 0.0), a.optDouble("realtime", 0.0));
            if (bySpeed != 0) return bySpeed;
            return Double.compare(a.optDouble("wer", 1.0), b.optDouble("wer", 1.0));
        });
        JSONArray rankedJson = new JSONArray();
        for (int i = 0; i < ranked.size(); i++) rankedJson.put(ranked.get(i).put("rank", i + 1));
        JSONArray apartJson = new JSONArray();
        for (JSONObject row : apart) apartJson.put(row);
        return new JSONObject().put("benchVersion", BENCH_VERSION).put("ranked", rankedJson).put("broken", apartJson);
    }

    @NonNull
    private static JSONObject row(@NonNull String key, @NonNull JSONObject record) throws JSONException {
        JSONObject voice = headline(record);
        JSONObject phases = record.optJSONObject("phases");
        JSONObject load = phases == null ? null : phases.optJSONObject("load");
        double wer = voice == null ? Double.NaN : voice.optDouble("wer", Double.NaN);
        JSONObject row = new JSONObject();
        row.put("key", key);
        row.put("kind", KIND);
        row.put("recordId", record.optString("id", ""));
        row.put("modelId", record.optString("modelId", ""));
        row.put("displayName", record.optString("displayName", record.optString("modelId", "")));
        row.put("engine", record.optString("engine", ""));
        row.put("backend", record.optString("backend", BACKEND));
        row.put("accelerator", record.optString("accelerator", ""));
        row.put("ranOn", record.optString("ranOn", record.optString("accelerator", "")));
        row.put("gpuFallback", fellBack(record));
        row.put("speculative", false);
        row.put("timestamp", record.optLong("timestamp", 0L));
        row.put("preset", record.optString("preset", PRESET_ID));
        row.put("realtime", voice == null ? 0.0 : voice.optDouble("realtime", 0.0));
        row.put("wer", Double.isNaN(wer) ? JSONObject.NULL : wer);
        row.put("wrongText", !Double.isNaN(wer) && wer >= WRONG_TEXT_WER);
        row.put("loadMs", load == null ? JSONObject.NULL : load.optLong("ms", 0L));
        row.put("status", record.optString("status", ""));
        row.put("conditions", record.opt("conditions") == null ? JSONObject.NULL : record.opt("conditions"));
        row.put("runtimeVersion", record.optString("runtimeVersion", ""));
        row.put("appVersion", record.optString("appVersion", ""));
        return row;
    }
}
