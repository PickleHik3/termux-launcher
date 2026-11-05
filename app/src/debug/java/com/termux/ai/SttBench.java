package com.termux.ai;

import android.os.Build;
import android.system.Os;
import android.system.OsConstants;

import com.termux.BuildConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Debug-only speech-to-text bench, run from a shell with no UI. It drives {@link WhisperSttRuntime}
 * and {@link ParakeetSttRuntime} in its own {@code app_process} (the same engines the app's
 * {@code :tai_runtime} process runs, built with a null Context), so {@code /proc/self/stat} is the
 * CPU of the process that does the inference. Per clip and runtime it reports wall time, CPU seconds,
 * mean battery current and the transcript, after one excluded warm-up run, over N timed runs.
 *
 * <p>Run (as the Termux app user, the APK on the class path, its native libs on LD_LIBRARY_PATH):
 * {@code CLASSPATH=$(pm path com.termux | cut -d: -f2) LD_LIBRARY_PATH=<apk>!/lib/arm64-v8a
 * app_process /system/bin com.termux.ai.SttBench --clips DIR --whisper W.tflite --parakeet P.tflite
 * [--runs 5] [--warmup 1] [--path LABEL] [--language en] [--interval-ms 200]}. See
 * {@code scripts/stt-bench/README.md}. Output lines all start {@code STTBENCH}.
 */
public final class SttBench {

    private SttBench() {
    }

    /** One engine under test and what its result lines are labelled with. */
    private static final class Target {
        final String runtime;
        final TaiModelSpec spec;
        final SttRuntime engine;

        Target(String runtime, TaiModelSpec spec, SttRuntime engine) {
            this.runtime = runtime;
            this.spec = spec;
            this.engine = engine;
        }
    }

    /** One timed run. */
    private static final class Run {
        double wallMs;
        double cpuS;
        double microamps;
        Boolean charging;
        String text = "";
        String error;
    }

    public static void main(String[] args) throws Exception {
        String clipsDir = null, whisper = null, parakeet = null, language = null;
        String path = "default";
        int runs = 5, warmup = 1;
        long intervalMs = 200L;
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (i + 1 >= args.length) {
                usage();
                return;
            }
            String value = args[++i];
            switch (key) {
                case "--clips": clipsDir = value; break;
                case "--whisper": whisper = value; break;
                case "--parakeet": parakeet = value; break;
                case "--runs": runs = Integer.parseInt(value); break;
                case "--warmup": warmup = Integer.parseInt(value); break;
                case "--path": path = value; break;
                case "--language": language = value; break;
                case "--interval-ms": intervalMs = Long.parseLong(value); break;
                default:
                    usage();
                    return;
            }
        }
        if (clipsDir == null || (whisper == null && parakeet == null) || runs < 1 || warmup < 0) {
            usage();
            return;
        }
        File[] clips = new File(clipsDir).listFiles((dir, name) -> name.endsWith(".wav"));
        if (clips == null || clips.length == 0) {
            System.out.println("STTBENCH_ERROR no .wav clips in " + clipsDir + " (run scripts/stt-bench/fetch-clips.sh)");
            return;
        }
        Arrays.sort(clips);

        long ticksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK);
        SttBenchBattery battery = SttBenchBattery.open();
        System.out.println(SttBenchStats.line("STTBENCH_ENV",
            "litert", BuildConfig.LITERT_VERSION,
            "litertlm", BuildConfig.LITERT_LM_VERSION,
            "path", path,
            "device", Build.MANUFACTURER + " " + Build.MODEL,
            "abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?",
            "cores", String.valueOf(Runtime.getRuntime().availableProcessors()),
            "runs", String.valueOf(runs),
            "warmup", String.valueOf(warmup),
            "battery", battery.source(),
            "charging", String.valueOf(battery.charging()),
            "clips", String.valueOf(clips.length)));

        List<Target> targets = new ArrayList<>();
        if (whisper != null) {
            targets.add(new Target("whisper", spec("bench-whisper", whisper, null),
                new WhisperSttRuntime(new TaiResidency(), null)));
        }
        if (parakeet != null) {
            targets.add(new Target("parakeet", spec(TaiModelCatalog.PARAKEET_TDT_V3_ID, parakeet, "parakeet-tdt"),
                new ParakeetSttRuntime(new TaiResidency(), null)));
        }
        SttBenchBattery.Sampler sampler = new SttBenchBattery.Sampler(battery, intervalMs);
        for (Target target : targets) {
            runTarget(target, clips, path, runs, warmup, language, sampler, ticksPerSecond);
        }
        System.out.println("STTBENCH_DONE");
        // The runtimes hold native memory and daemon threads; leave without waiting for either.
        System.exit(0);
    }

    private static void runTarget(Target target, File[] clips, String path, int runs, int warmup,
                                  String language, SttBenchBattery.Sampler sampler, long hz) throws Exception {
        String model = new File(target.spec.localPath).getName();
        // Load on its own, so no clip's first run carries it.
        long cpu0 = cpuTicks();
        long t0 = System.nanoTime();
        JSONObject loaded = target.engine.warm(target.spec);
        double loadMs = (System.nanoTime() - t0) / 1e6;
        double loadCpu = SttBenchStats.ticksToSeconds(cpuTicks() - cpu0, hz);
        System.out.println(SttBenchStats.line("STTBENCH_LOAD", "runtime", target.runtime, "path", path,
            "model", model, "ok", String.valueOf(loaded.optBoolean("ok", false)),
            "loadMs", SttBenchStats.num(loadMs, 0), "cpuS", SttBenchStats.num(loadCpu, 2),
            "error", loaded.optString("error", "")));
        if (!loaded.optBoolean("ok", false)) {
            target.engine.close();
            return;
        }

        double sumWallMed = 0, sumWallMin = 0, sumCpuMed = 0, sumCpuMin = 0, sumAudio = 0;
        double[] clipCurrent = new double[clips.length];
        boolean anyCharging = false, chargingKnown = false;
        int failed = 0;
        for (int c = 0; c < clips.length; c++) {
            File clip = clips[c];
            double audioS = WhisperAudio.read(clip).length / 16000.0;
            for (int w = 0; w < warmup; w++) {
                Run run = timedRun(target, clip, language, sampler, hz);
                System.out.println(runLine(target, path, clip, audioS, "warmup", run));
            }
            double[] wall = new double[runs], cpu = new double[runs], current = new double[runs];
            String text = null;
            boolean same = true, ok = true;
            for (int r = 0; r < runs; r++) {
                Run run = timedRun(target, clip, language, sampler, hz);
                System.out.println(runLine(target, path, clip, audioS, String.valueOf(r + 1), run));
                if (run.error != null) {
                    ok = false;
                    continue;
                }
                wall[r] = run.wallMs;
                cpu[r] = run.cpuS;
                current[r] = run.microamps;
                if (run.charging != null) {
                    chargingKnown = true;
                    anyCharging |= run.charging;
                }
                if (text == null) text = run.text;
                else if (!text.equals(run.text)) same = false;
            }
            if (!ok) {
                failed++;
                System.out.println(SttBenchStats.line("STTBENCH_CLIP", "runtime", target.runtime, "path", path,
                    "clip", clip.getName(), "ok", "false"));
                continue;
            }
            double wallMed = SttBenchStats.median(wall), wallMin = SttBenchStats.min(wall);
            double cpuMed = SttBenchStats.median(cpu), cpuMin = SttBenchStats.min(cpu);
            clipCurrent[c] = SttBenchStats.meanOfFinite(current);
            sumWallMed += wallMed;
            sumWallMin += wallMin;
            sumCpuMed += cpuMed;
            sumCpuMin += cpuMin;
            sumAudio += audioS;
            System.out.println(SttBenchStats.line("STTBENCH_CLIP", "runtime", target.runtime, "path", path,
                "clip", clip.getName(), "ok", "true", "audioS", SttBenchStats.num(audioS, 1),
                "wallMedMs", SttBenchStats.num(wallMed, 0), "wallMinMs", SttBenchStats.num(wallMin, 0),
                "cpuMedS", SttBenchStats.num(cpuMed, 2), "cpuMinS", SttBenchStats.num(cpuMin, 2),
                "mA", SttBenchStats.milliamps(clipCurrent[c]),
                "charging", chargingKnown ? String.valueOf(anyCharging) : "?",
                "sameText", String.valueOf(same), "text", text == null ? "" : text));
        }
        System.out.println(SttBenchStats.line("STTBENCH_SUMMARY", "runtime", target.runtime, "path", path,
            "model", model, "clips", String.valueOf(clips.length), "failed", String.valueOf(failed),
            "audioS", SttBenchStats.num(sumAudio, 1), "runs", String.valueOf(runs),
            "wallMedMs", SttBenchStats.num(sumWallMed, 0), "wallMinMs", SttBenchStats.num(sumWallMin, 0),
            "cpuMedS", SttBenchStats.num(sumCpuMed, 2), "cpuMinS", SttBenchStats.num(sumCpuMin, 2),
            "rtfMed", SttBenchStats.num(sumAudio > 0 ? sumWallMed / 1000.0 / sumAudio : Double.NaN, 3),
            "mA", SttBenchStats.milliamps(SttBenchStats.meanOfFinite(clipCurrent)),
            "charging", chargingKnown ? String.valueOf(anyCharging) : "?"));
        target.engine.close();
    }

    private static Run timedRun(Target target, File clip, String language,
                                SttBenchBattery.Sampler sampler, long hz) throws Exception {
        Run run = new Run();
        long cpu0 = cpuTicks();
        sampler.start();
        long t0 = System.nanoTime();
        JSONObject answer = target.engine.transcribe(target.spec, clip, language, null);
        run.wallMs = (System.nanoTime() - t0) / 1e6;
        SttBenchBattery.Sampler.Result battery = sampler.stop();
        run.cpuS = SttBenchStats.ticksToSeconds(cpuTicks() - cpu0, hz);
        run.microamps = battery.meanMicroamps;
        run.charging = battery.charging;
        if (answer.optBoolean("ok", false)) run.text = answer.optString("text", "").trim().replaceAll("\\s+", " ");
        else run.error = answer.optString("error", "failed");
        return run;
    }

    private static String runLine(Target target, String path, File clip, double audioS, String run, Run result) {
        return SttBenchStats.line("STTBENCH_RUN", "runtime", target.runtime, "path", path,
            "clip", clip.getName(), "audioS", SttBenchStats.num(audioS, 1), "run", run,
            "wallMs", SttBenchStats.num(result.wallMs, 0), "cpuS", SttBenchStats.num(result.cpuS, 2),
            "mA", SttBenchStats.milliamps(result.microamps),
            "charging", result.charging == null ? "?" : String.valueOf(result.charging),
            result.error == null ? "text" : "error", result.error == null ? result.text : result.error);
    }

    private static TaiModelSpec spec(String id, String modelPath, String architecture) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("displayName", id);
        json.put("localPath", modelPath);
        json.put("sizeBytes", new File(modelPath).length());
        json.put("capabilities", new org.json.JSONArray().put(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        if (architecture != null) json.put("architecture", architecture);
        return TaiModelSpec.fromJson(json);
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
        System.out.println("usage: SttBench --clips DIR [--whisper W.tflite] [--parakeet P.tflite] [--runs 5] "
            + "[--warmup 1] [--path LABEL] [--language en] [--interval-ms 200]");
    }
}
