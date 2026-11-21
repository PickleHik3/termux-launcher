package com.termux.ai;

import android.os.Build;
import android.system.Os;
import android.system.OsConstants;

import com.termux.BuildConfig;
import com.termux.app.terminal.inappkeyboard.voice.SileroVadBench;
import com.termux.app.terminal.inappkeyboard.voice.SileroVoiceDecider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Debug-only bench for the features that still run on LiteRT's {@code Interpreter}: KittenTTS with
 * its phonemizer ({@link KittenTtsRuntime}), the Interpreter embedder ({@link LiteRtEmbeddingRuntime})
 * and Silero VAD. Like {@link SttBench} it runs from a shell under {@code app_process} with no UI and
 * no Context, so {@code /proc/self/stat} is the CPU of the process doing the inference, and it prints
 * one grep-able line per run, item and section. It also writes each section's output so two builds
 * (LiteRT 1.4.2 against 2.3.2) can be compared with {@link AuxBenchCompare}.
 *
 * <p>The runtimes are used exactly as the app builds them, so each uses the thread count and
 * delegate the build under test gives it; this class names no LiteRT API. Each section runs only when
 * its flag is given. Output lines all start {@code AUXBENCH}. See {@code scripts/stt-bench/README.md}.
 *
 * <p>Output files in {@code --out}: {@code tts-N.f32} (N = 1 short, 2 medium, 3 long; raw
 * little-endian float32 PCM at 24 kHz), {@code emb.f32} (float32 count, float32 dimension, then the
 * vectors), {@code vad.f32} (one speech probability per 512-sample chunk).
 */
public final class AuxBench {

    private AuxBench() {
    }

    static final String[] TTS_TEXTS = {
        "The weather is lovely today, friend.",
        "Yesterday afternoon we walked along the river, watched the boats drift slowly past, and talked about "
            + "where we might travel when the summer finally arrived.",
        "The old library stood at the end of a quiet street, its tall windows glowing softly in the evening "
            + "light. Every morning the keeper unlocked the heavy oak door, dusted the long wooden tables, and "
            + "arranged the newest books near the entrance. Visitors came in small groups, some looking for "
            + "stories, others for maps or recipes or the history of the town. By closing time the shelves were "
            + "neatly ordered again, and the keeper walked home slowly through the cooling air, already "
            + "thinking about tomorrow."
    };
    static final String[] TTS_LABELS = {"short", "medium", "long"};

    private static final String[] EMBED_SHORT = {
        "The cat sat on the warm windowsill.",
        "How do I reset my router?",
        "Meeting moved to Thursday at three.",
        "Fresh bread smells wonderful in the morning.",
        "The train to the coast leaves at noon.",
        "Add milk, eggs and flour to the list.",
        "She plays the violin in a small orchestra.",
        "Battery usage was higher than expected yesterday."
    };

    /** Eight short sentences, then one long text of about 400 tokens (the long TTS passage four times). */
    static String[] embedTexts() {
        String[] texts = new String[EMBED_SHORT.length + 1];
        System.arraycopy(EMBED_SHORT, 0, texts, 0, EMBED_SHORT.length);
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 4; i++) longText.append(i == 0 ? "" : " ").append(TTS_TEXTS[2]);
        texts[EMBED_SHORT.length] = longText.toString();
        return texts;
    }

    private interface Job {
        void run() throws Exception;
    }

    /** One measured call. */
    private static final class Run {
        double wallMs;
        double cpuS;
        double microamps;
        Boolean charging;
        String error;
    }

    /** Shared state of one invocation. */
    private static final class Ctx {
        int runs = 5, warmup = 1;
        String path = "default";
        File outDir;
        SttBenchBattery.Sampler sampler;
        long hz;
    }

    public static void main(String[] args) throws Exception {
        String ttsDir = null, embedFile = null, vad = null, clipsDir = null, out = null;
        Ctx ctx = new Ctx();
        long intervalMs = 200L;
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (i + 1 >= args.length) {
                usage();
                return;
            }
            String value = args[++i];
            switch (key) {
                case "--tts": ttsDir = value; break;
                case "--embed": embedFile = value; break;
                case "--vad": vad = value; break;
                case "--clips": clipsDir = value; break;
                case "--out": out = value; break;
                case "--runs": ctx.runs = Integer.parseInt(value); break;
                case "--warmup": ctx.warmup = Integer.parseInt(value); break;
                case "--path": ctx.path = value; break;
                case "--interval-ms": intervalMs = Long.parseLong(value); break;
                default:
                    usage();
                    return;
            }
        }
        if ((ttsDir == null && embedFile == null && vad == null) || ctx.runs < 1 || ctx.warmup < 0
            || (vad != null && clipsDir == null)) {
            usage();
            return;
        }
        if (out != null) {
            ctx.outDir = new File(out);
            if (!ctx.outDir.isDirectory() && !ctx.outDir.mkdirs()) {
                System.out.println("AUXBENCH_ERROR cannot create --out " + out);
                return;
            }
        }

        ctx.hz = Os.sysconf(OsConstants._SC_CLK_TCK);
        SttBenchBattery battery = SttBenchBattery.open();
        int cores = Runtime.getRuntime().availableProcessors();
        int appThreads = Math.max(1, Math.min(4, cores));
        System.out.println(SttBenchStats.line("AUXBENCH_ENV",
            "litert", BuildConfig.LITERT_VERSION,
            "path", ctx.path,
            "device", Build.MANUFACTURER + " " + Build.MODEL,
            "abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?",
            "cores", String.valueOf(cores),
            // What the app's own code asks for: KittenTtsRuntime.loadGraphs and LiteRtEmbeddingRuntime
            // DEFAULT_THREADS are min(4, cores); SileroVad.load is one thread.
            "threadsTts", String.valueOf(appThreads),
            "threadsEmbed", String.valueOf(appThreads),
            "threadsVad", "1",
            "runs", String.valueOf(ctx.runs),
            "warmup", String.valueOf(ctx.warmup),
            "battery", battery.source(),
            "charging", String.valueOf(battery.charging())));
        ctx.sampler = new SttBenchBattery.Sampler(battery, intervalMs);

        final String ttsFinal = ttsDir, embedFinal = embedFile, vadFinal = vad, clipsFinal = clipsDir;
        if (ttsFinal != null) section("tts", () -> benchTts(ctx, new File(ttsFinal)));
        if (embedFinal != null) section("embed", () -> benchEmbed(ctx, new File(embedFinal)));
        if (vadFinal != null) {
            section("vad", () -> benchVad(ctx, vadFinal, new File(clipsFinal, "stt-45s-bush.wav")));
        }
        System.out.println("AUXBENCH_DONE");
        // The runtimes hold native memory and daemon threads; leave without waiting for either.
        System.exit(0);
    }

    private static void section(String name, Job job) {
        try {
            job.run();
        } catch (Throwable t) {
            System.out.println(SttBenchStats.line("AUXBENCH_ERROR", "section", name, "error",
                t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
    }

    // ---- TTS ----

    private static void benchTts(Ctx ctx, File dir) throws Exception {
        File predictor = new File(dir, KittenTtsRuntime.PREDICTOR_FILE);
        JSONObject json = new JSONObject();
        json.put("id", TaiModelCatalog.KITTEN_TTS_NANO_ID);
        json.put("displayName", "KittenTTS Nano 0.8");
        json.put("localPath", predictor.getPath());
        json.put("sizeBytes", predictor.length());
        json.put("architecture", KittenTtsRuntime.ARCHITECTURE);
        json.put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH));
        TaiModelSpec spec = TaiModelSpec.fromJson(json);
        KittenTtsRuntime engine = new KittenTtsRuntime(new TaiResidency(), null);
        String voice = TaiTtsVoices.DEFAULT_VOICE;

        JSONObject[] loaded = new JSONObject[1];
        Run load = measure(ctx, () -> loaded[0] = engine.warm(spec));
        boolean ok = load.error == null && loaded[0].optBoolean("ok", false);
        System.out.println(SttBenchStats.line("AUXBENCH_LOAD", "section", "tts", "path", ctx.path,
            "model", spec.id, "ok", String.valueOf(ok), "loadMs", SttBenchStats.num(load.wallMs, 0),
            "cpuS", SttBenchStats.num(load.cpuS, 2), "error", ok ? "" : errorText(load, loaded[0])));
        if (!ok) {
            engine.close();
            return;
        }

        // Output of the current run; the sink appends, the buffer is reused across runs.
        float[][] pcm = {new float[KittenTtsRuntime.SAMPLE_RATE]};
        int[] filled = {0};
        TtsRuntime.Sink sink = (samples, count, index) -> {
            if (filled[0] + count > pcm[0].length) {
                pcm[0] = java.util.Arrays.copyOf(pcm[0], Math.max(pcm[0].length * 2, filled[0] + count));
            }
            System.arraycopy(samples, 0, pcm[0], filled[0], count);
            filled[0] += count;
            return true;
        };
        double sumWallMed = 0, sumCpuMed = 0, sumAudio = 0, sumWallMin = 0, sumCpuMin = 0;
        double[] itemCurrent = new double[TTS_TEXTS.length];
        int failed = 0;
        boolean anyCharging = false, chargingKnown = false;
        for (int n = 0; n < TTS_TEXTS.length; n++) {
            String text = TTS_TEXTS[n];
            int words = text.split("\\s+").length;
            String item = String.valueOf(n + 1);
            JSONObject[] answer = new JSONObject[1];
            for (int w = 0; w < ctx.warmup; w++) {
                filled[0] = 0;
                Run run = measure(ctx, () -> answer[0] = engine.synthesize(spec, text, voice,
                    TaiTtsVoices.DEFAULT_SPEED, sink, () -> false));
                System.out.println(ttsLine(ctx, TTS_LABELS[n], item, words, "warmup", run, answer[0], pcm[0], filled[0]));
            }
            double[] wall = new double[ctx.runs], cpu = new double[ctx.runs], current = new double[ctx.runs];
            String firstSha = null;
            boolean same = true, itemOk = true;
            double audioS = Double.NaN;
            for (int r = 0; r < ctx.runs; r++) {
                filled[0] = 0;
                Run run = measure(ctx, () -> answer[0] = engine.synthesize(spec, text, voice,
                    TaiTtsVoices.DEFAULT_SPEED, sink, () -> false));
                System.out.println(ttsLine(ctx, TTS_LABELS[n], item, words, String.valueOf(r + 1), run, answer[0],
                    pcm[0], filled[0]));
                if (run.error != null || answer[0] == null || !answer[0].optBoolean("ok", false)) {
                    itemOk = false;
                    continue;
                }
                wall[r] = run.wallMs;
                cpu[r] = run.cpuS;
                current[r] = run.microamps;
                if (run.charging != null) {
                    chargingKnown = true;
                    anyCharging |= run.charging;
                }
                audioS = filled[0] / (double) KittenTtsRuntime.SAMPLE_RATE;
                String sha = sha256(pcm[0], filled[0]);
                if (firstSha == null) {
                    firstSha = sha;
                    if (ctx.outDir != null) writeFloats(new File(ctx.outDir, "tts-" + item + ".f32"), pcm[0], 0, filled[0]);
                } else if (!firstSha.equals(sha)) {
                    same = false;
                }
            }
            if (!itemOk) {
                failed++;
                System.out.println(SttBenchStats.line("AUXBENCH_ITEM", "section", "tts", "path", ctx.path,
                    "item", item, "ok", "false"));
                continue;
            }
            double wallMed = SttBenchStats.median(wall), wallMin = SttBenchStats.min(wall);
            double cpuMed = SttBenchStats.median(cpu), cpuMin = SttBenchStats.min(cpu);
            itemCurrent[n] = SttBenchStats.meanOfFinite(current);
            sumWallMed += wallMed;
            sumWallMin += wallMin;
            sumCpuMed += cpuMed;
            sumCpuMin += cpuMin;
            sumAudio += audioS;
            System.out.println(SttBenchStats.line("AUXBENCH_ITEM", "section", "tts", "path", ctx.path,
                "item", item, "text", TTS_LABELS[n], "words", String.valueOf(words), "ok", "true",
                "audioS", SttBenchStats.num(audioS, 2),
                "wallMedMs", SttBenchStats.num(wallMed, 0), "wallMinMs", SttBenchStats.num(wallMin, 0),
                "cpuMedS", SttBenchStats.num(cpuMed, 2), "cpuMinS", SttBenchStats.num(cpuMin, 2),
                "rtfMed", SttBenchStats.num(wallMed / 1000.0 / audioS, 3),
                "mA", SttBenchStats.milliamps(itemCurrent[n]),
                "charging", chargingKnown ? String.valueOf(anyCharging) : "?",
                "sameAudio", String.valueOf(same), "sha256", firstSha == null ? "" : firstSha));
        }
        System.out.println(SttBenchStats.line("AUXBENCH_SUMMARY", "section", "tts", "path", ctx.path,
            "items", String.valueOf(TTS_TEXTS.length), "failed", String.valueOf(failed), "runs", String.valueOf(ctx.runs),
            "audioS", SttBenchStats.num(sumAudio, 2),
            "wallMedMs", SttBenchStats.num(sumWallMed, 0), "wallMinMs", SttBenchStats.num(sumWallMin, 0),
            "cpuMedS", SttBenchStats.num(sumCpuMed, 2), "cpuMinS", SttBenchStats.num(sumCpuMin, 2),
            "rtfMed", SttBenchStats.num(sumAudio > 0 ? sumWallMed / 1000.0 / sumAudio : Double.NaN, 3),
            "mA", SttBenchStats.milliamps(SttBenchStats.meanOfFinite(itemCurrent)),
            "charging", chargingKnown ? String.valueOf(anyCharging) : "?"));
        engine.close();
    }

    private static String ttsLine(Ctx ctx, String label, String item, int words, String run, Run result,
                                  JSONObject answer, float[] pcm, int count) {
        boolean ok = result.error == null && answer != null && answer.optBoolean("ok", false);
        if (!ok) {
            return SttBenchStats.line("AUXBENCH_RUN", "section", "tts", "path", ctx.path, "item", item,
                "text", label, "run", run, "error", errorText(result, answer));
        }
        JSONObject timings = answer.optJSONObject("timings");
        double audioS = count / (double) KittenTtsRuntime.SAMPLE_RATE;
        return SttBenchStats.line("AUXBENCH_RUN", "section", "tts", "path", ctx.path, "item", item,
            "text", label, "words", String.valueOf(words), "run", run,
            "wallMs", SttBenchStats.num(result.wallMs, 0),
            "g2pMs", timings == null ? "NA" : String.valueOf(timings.optLong("g2pMs", -1)),
            "synthMs", timings == null ? "NA" : String.valueOf(timings.optLong("synthMs", -1)),
            "samples", String.valueOf(count), "audioS", SttBenchStats.num(audioS, 2),
            "rtf", SttBenchStats.num(result.wallMs / 1000.0 / audioS, 3),
            "cpuS", SttBenchStats.num(result.cpuS, 2), "mA", SttBenchStats.milliamps(result.microamps),
            "charging", result.charging == null ? "?" : String.valueOf(result.charging),
            "sha256", sha256(pcm, count));
    }

    // ---- embeddings ----

    private static void benchEmbed(Ctx ctx, File model) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", TaiModelCatalog.EMBEDDING_GEMMA_300M_ID);
        json.put("displayName", model.getName());
        json.put("localPath", model.getPath());
        json.put("sizeBytes", model.length());
        json.put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        TaiModelSpec spec = TaiModelSpec.fromJson(json);
        LiteRtEmbeddingRuntime engine = new LiteRtEmbeddingRuntime(new TaiResidency());
        String[] texts = embedTexts();

        // The runtime loads on first use: the tokenizer, then one interpreter per window a text needs.
        // So "load" here is the first call, on the shortest text; the long text's larger window (if
        // the model has one) loads during the warm-up pass, which every text goes through.
        JSONObject[] answer = new JSONObject[1];
        Run load = measure(ctx, () -> answer[0] = engine.embed(spec, Collections.singletonList(texts[0]), 0));
        boolean ok = load.error == null && embedOk(answer[0]);
        System.out.println(SttBenchStats.line("AUXBENCH_LOAD", "section", "embed", "path", ctx.path,
            "model", model.getName(), "ok", String.valueOf(ok), "loadMs", SttBenchStats.num(load.wallMs, 0),
            "cpuS", SttBenchStats.num(load.cpuS, 2), "note", "first embed call, short text; includes tokenizer",
            "error", ok ? "" : errorText(load, answer[0])));
        if (!ok) {
            engine.close();
            return;
        }

        float[][] vectors = new float[texts.length][];
        double sumWallMed = 0, sumWallMin = 0, sumCpuMed = 0, sumCpuMin = 0;
        double[] itemCurrent = new double[texts.length];
        int failed = 0;
        boolean anyCharging = false, chargingKnown = false;
        for (int t = 0; t < texts.length; t++) {
            String text = texts[t];
            List1 one = new List1(text);
            for (int w = 0; w < ctx.warmup; w++) {
                Run run = measure(ctx, () -> answer[0] = engine.embed(spec, one, 0));
                System.out.println(embedLine(ctx, t, "warmup", run, answer[0]));
            }
            double[] wall = new double[ctx.runs], cpu = new double[ctx.runs], current = new double[ctx.runs];
            boolean itemOk = true, same = true;
            int tokens = -1;
            for (int r = 0; r < ctx.runs; r++) {
                Run run = measure(ctx, () -> answer[0] = engine.embed(spec, one, 0));
                System.out.println(embedLine(ctx, t, String.valueOf(r + 1), run, answer[0]));
                if (run.error != null || !embedOk(answer[0])) {
                    itemOk = false;
                    continue;
                }
                wall[r] = run.wallMs;
                cpu[r] = run.cpuS;
                current[r] = run.microamps;
                if (run.charging != null) {
                    chargingKnown = true;
                    anyCharging |= run.charging;
                }
                JSONObject item = answer[0].optJSONArray("data").optJSONObject(0);
                tokens = item.optInt("tokens", -1);
                float[] vector = vectorOf(item);
                if (vectors[t] == null) vectors[t] = vector;
                else if (!java.util.Arrays.equals(vectors[t], vector)) same = false;
            }
            if (!itemOk) {
                failed++;
                System.out.println(SttBenchStats.line("AUXBENCH_ITEM", "section", "embed", "path", ctx.path,
                    "item", String.valueOf(t + 1), "ok", "false"));
                continue;
            }
            double wallMed = SttBenchStats.median(wall), wallMin = SttBenchStats.min(wall);
            double cpuMed = SttBenchStats.median(cpu), cpuMin = SttBenchStats.min(cpu);
            itemCurrent[t] = SttBenchStats.meanOfFinite(current);
            sumWallMed += wallMed;
            sumWallMin += wallMin;
            sumCpuMed += cpuMed;
            sumCpuMin += cpuMin;
            System.out.println(SttBenchStats.line("AUXBENCH_ITEM", "section", "embed", "path", ctx.path,
                "item", String.valueOf(t + 1), "ok", "true", "tokens", String.valueOf(tokens),
                "dim", String.valueOf(vectors[t].length),
                "wallMedMs", SttBenchStats.num(wallMed, 0), "wallMinMs", SttBenchStats.num(wallMin, 0),
                "cpuMedS", SttBenchStats.num(cpuMed, 2), "cpuMinS", SttBenchStats.num(cpuMin, 2),
                "mA", SttBenchStats.milliamps(itemCurrent[t]),
                "charging", chargingKnown ? String.valueOf(anyCharging) : "?",
                "sameVector", String.valueOf(same)));
        }
        System.out.println(SttBenchStats.line("AUXBENCH_SUMMARY", "section", "embed", "path", ctx.path,
            "model", model.getName(), "items", String.valueOf(texts.length), "failed", String.valueOf(failed),
            "runs", String.valueOf(ctx.runs),
            "wallMedMs", SttBenchStats.num(sumWallMed, 0), "wallMinMs", SttBenchStats.num(sumWallMin, 0),
            "cpuMedS", SttBenchStats.num(sumCpuMed, 2), "cpuMinS", SttBenchStats.num(sumCpuMin, 2),
            "mA", SttBenchStats.milliamps(SttBenchStats.meanOfFinite(itemCurrent)),
            "charging", chargingKnown ? String.valueOf(anyCharging) : "?"));
        if (ctx.outDir != null && failed == 0) writeEmbeddings(new File(ctx.outDir, "emb.f32"), vectors);
        engine.close();
    }

    /** A one-element list without {@code Collections} allocating per call in the timed loop. */
    private static final class List1 extends java.util.AbstractList<String> {
        private final String value;

        List1(String value) {
            this.value = value;
        }

        @Override
        public String get(int index) {
            return value;
        }

        @Override
        public int size() {
            return 1;
        }
    }

    private static boolean embedOk(JSONObject answer) {
        return answer != null && answer.optJSONArray("data") != null && answer.optJSONArray("data").length() > 0;
    }

    private static float[] vectorOf(JSONObject item) {
        JSONArray array = item.optJSONArray("embedding");
        float[] vector = new float[array == null ? 0 : array.length()];
        for (int i = 0; i < vector.length; i++) vector[i] = (float) array.optDouble(i);
        return vector;
    }

    private static String embedLine(Ctx ctx, int index, String run, Run result, JSONObject answer) {
        if (result.error != null || !embedOk(answer)) {
            return SttBenchStats.line("AUXBENCH_RUN", "section", "embed", "path", ctx.path,
                "item", String.valueOf(index + 1), "run", run, "error", errorText(result, answer));
        }
        return SttBenchStats.line("AUXBENCH_RUN", "section", "embed", "path", ctx.path,
            "item", String.valueOf(index + 1), "run", run,
            "tokens", String.valueOf(answer.optJSONArray("data").optJSONObject(0).optInt("tokens", -1)),
            "wallMs", SttBenchStats.num(result.wallMs, 0), "cpuS", SttBenchStats.num(result.cpuS, 2),
            "mA", SttBenchStats.milliamps(result.microamps),
            "charging", result.charging == null ? "?" : String.valueOf(result.charging));
    }

    // ---- VAD ----

    private static void benchVad(Ctx ctx, String vad, File clip) throws Exception {
        float[] audio = WhisperAudio.read(clip);
        int chunks = audio.length / SileroVoiceDecider.CHUNK_SAMPLES;
        double audioS = audio.length / (double) WhisperAudio.SAMPLE_RATE;
        float[] probabilities = new float[chunks];
        float[] window = new float[SileroVoiceDecider.WINDOW_SAMPLES];

        SileroVoiceDecider.ProbabilitySource[] source = new SileroVoiceDecider.ProbabilitySource[1];
        Run load = measure(ctx, () -> source[0] = openVad(vad));
        boolean ok = load.error == null && source[0] != null;
        System.out.println(SttBenchStats.line("AUXBENCH_LOAD", "section", "vad", "path", ctx.path,
            "model", vad, "ok", String.valueOf(ok), "loadMs", SttBenchStats.num(load.wallMs, 0),
            "cpuS", SttBenchStats.num(load.cpuS, 2), "error", ok ? "" : String.valueOf(load.error)));
        if (!ok) return;
        source[0].close();

        double[] wall = new double[ctx.runs], cpu = new double[ctx.runs], current = new double[ctx.runs];
        boolean same = true, allOk = true, anyCharging = false, chargingKnown = false;
        int runsDone = 0;
        float[] first = null;
        for (int w = -ctx.warmup; w < ctx.runs; w++) {
            // A fresh instance per run, built outside the timed region: the LSTM state starts from zeros.
            SileroVoiceDecider.ProbabilitySource vadSource = openVad(vad);
            java.util.Arrays.fill(window, 0f);
            Run run = measure(ctx, () -> {
                for (int c = 0; c < chunks; c++) {
                    System.arraycopy(audio, c * SileroVoiceDecider.CHUNK_SAMPLES, window,
                        SileroVoiceDecider.CONTEXT_SAMPLES, SileroVoiceDecider.CHUNK_SAMPLES);
                    probabilities[c] = vadSource.probability(window);
                    System.arraycopy(window, SileroVoiceDecider.WINDOW_SAMPLES - SileroVoiceDecider.CONTEXT_SAMPLES,
                        window, 0, SileroVoiceDecider.CONTEXT_SAMPLES);
                }
            });
            vadSource.close();
            String label = w < 0 ? "warmup" : String.valueOf(w + 1);
            if (run.error != null) {
                allOk = false;
                System.out.println(SttBenchStats.line("AUXBENCH_RUN", "section", "vad", "path", ctx.path,
                    "run", label, "error", run.error));
                continue;
            }
            System.out.println(SttBenchStats.line("AUXBENCH_RUN", "section", "vad", "path", ctx.path,
                "run", label, "audioS", SttBenchStats.num(audioS, 1), "frames", String.valueOf(chunks),
                "wallMs", SttBenchStats.num(run.wallMs, 0), "cpuS", SttBenchStats.num(run.cpuS, 2),
                "framesPerS", SttBenchStats.num(chunks / (run.wallMs / 1000.0), 0),
                "mA", SttBenchStats.milliamps(run.microamps),
                "charging", run.charging == null ? "?" : String.valueOf(run.charging)));
            if (w < 0) continue;
            wall[runsDone] = run.wallMs;
            cpu[runsDone] = run.cpuS;
            current[runsDone] = run.microamps;
            runsDone++;
            if (run.charging != null) {
                chargingKnown = true;
                anyCharging |= run.charging;
            }
            if (first == null) first = probabilities.clone();
            else if (!java.util.Arrays.equals(first, probabilities)) same = false;
        }
        if (first != null && ctx.outDir != null) writeFloats(new File(ctx.outDir, "vad.f32"), first, 0, first.length);
        double wallMed = SttBenchStats.median(java.util.Arrays.copyOf(wall, runsDone));
        System.out.println(SttBenchStats.line("AUXBENCH_SUMMARY", "section", "vad", "path", ctx.path,
            "ok", String.valueOf(allOk), "audioS", SttBenchStats.num(audioS, 1), "frames", String.valueOf(chunks),
            "runs", String.valueOf(ctx.runs),
            "wallMedMs", SttBenchStats.num(wallMed, 0),
            "wallMinMs", SttBenchStats.num(SttBenchStats.min(java.util.Arrays.copyOf(wall, runsDone)), 0),
            "cpuMedS", SttBenchStats.num(SttBenchStats.median(java.util.Arrays.copyOf(cpu, runsDone)), 2),
            "cpuMinS", SttBenchStats.num(SttBenchStats.min(java.util.Arrays.copyOf(cpu, runsDone)), 2),
            "framesPerS", SttBenchStats.num(chunks / (wallMed / 1000.0), 0),
            "rtfMed", SttBenchStats.num(wallMed / 1000.0 / audioS, 4),
            "mA", SttBenchStats.milliamps(SttBenchStats.meanOfFinite(java.util.Arrays.copyOf(current, runsDone))),
            "charging", chargingKnown ? String.valueOf(anyCharging) : "?",
            "sameProbabilities", String.valueOf(same)));
    }

    /** The Silero model from a file, or from the APK on the class path when {@code vad} is {@code asset}. */
    private static SileroVoiceDecider.ProbabilitySource openVad(String vad) throws IOException {
        byte[] data;
        if ("asset".equals(vad)) {
            String apk = System.getProperty("java.class.path", "").split(File.pathSeparator)[0];
            try (ZipFile zip = new ZipFile(apk)) {
                ZipEntry entry = zip.getEntry("assets/vad/silero_vad_v5.tflite");
                if (entry == null) throw new IOException("no assets/vad/silero_vad_v5.tflite in " + apk);
                data = readAll(zip.getInputStream(entry));
            }
        } else {
            try (InputStream in = new FileInputStream(vad)) {
                data = readAll(in);
            }
        }
        ByteBuffer model = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
        model.put(data);
        model.rewind();
        return SileroVadBench.open(model);
    }

    // ---- shared ----

    private static Run measure(Ctx ctx, Job job) {
        Run run = new Run();
        long cpu0 = cpuTicks();
        ctx.sampler.start();
        long t0 = System.nanoTime();
        try {
            job.run();
        } catch (Throwable t) {
            run.error = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        run.wallMs = (System.nanoTime() - t0) / 1e6;
        SttBenchBattery.Sampler.Result battery = ctx.sampler.stop();
        run.cpuS = SttBenchStats.ticksToSeconds(cpuTicks() - cpu0, ctx.hz);
        run.microamps = battery.meanMicroamps;
        run.charging = battery.charging;
        return run;
    }

    private static String errorText(Run run, JSONObject answer) {
        if (run.error != null) return run.error;
        if (answer == null) return "no answer";
        JSONObject error = answer.optJSONObject("error");
        if (error != null) return error.optString("message", error.toString());
        return answer.optString("error", "failed");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 20);
        byte[] block = new byte[64 * 1024];
        int read;
        while ((read = in.read(block)) > 0) bytes.write(block, 0, read);
        return bytes.toByteArray();
    }

    private static ByteBuffer littleEndian(float[] values, int offset, int count) {
        ByteBuffer buffer = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(values, offset, count);
        return buffer;
    }

    private static void writeFloats(File file, float[] values, int offset, int count) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(littleEndian(values, offset, count).array());
        }
    }

    /** {@code emb.f32}: float32 count, float32 dimension, then the vectors. */
    private static void writeEmbeddings(File file, float[][] vectors) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(littleEndian(new float[] {vectors.length, vectors[0].length}, 0, 2).array());
            for (float[] vector : vectors) out.write(littleEndian(vector, 0, vector.length).array());
        }
    }

    private static String sha256(float[] values, int count) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(littleEndian(values, 0, count).array());
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "NA";
        }
    }

    /** utime + stime of this process in clock ticks, -1 when /proc cannot be read. */
    private static long cpuTicks() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/stat"))) {
            return SttBenchStats.parseCpuTicks(reader.readLine());
        } catch (IOException e) {
            return -1L;
        }
    }

    private static void usage() {
        System.out.println("usage: AuxBench [--tts DIR] [--embed FILE.tflite] [--vad FILE|asset --clips DIR] "
            + "[--out DIR] [--runs 5] [--warmup 1] [--path LABEL] [--interval-ms 200]");
    }
}
