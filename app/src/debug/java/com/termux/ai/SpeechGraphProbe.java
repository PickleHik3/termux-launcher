package com.termux.ai;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Debug-only bench for a speech graph on the device, outside the app: does it load in the app's
 * LiteRT {@link Interpreter} with the same XNNPACK setup as {@link WhisperSttRuntime}, and what do
 * its signatures cost on this CPU? Written for NVIDIA Parakeet TDT v3 (project-docs/
 * parakeet-stt-research.md, "loading it in 1.4.2 is test #1"), which has {@code encode},
 * {@code decode} and {@code decode_1}. Inputs are zeros: the timings do not depend on the audio.
 *
 * <p>Run from a shell with the installed APK on the class path, e.g.
 * {@code CLASSPATH=$(pm path com.termux | cut -d: -f2) app_process /system/bin
 * com.termux.ai.SpeechGraphProbe <model.tflite> [threads] [decode_1 runs]} with
 * {@code LD_LIBRARY_PATH} pointing at the APK's native libraries.
 */
public final class SpeechGraphProbe {

    private SpeechGraphProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: SpeechGraphProbe <model.tflite> [threads=4] [decode_1 runs=30]");
            return;
        }
        File model = new File(args[0]);
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int runs = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        long rssBefore = rssKb();
        long t0 = System.nanoTime();
        TaiXnnpackDelegate delegate = TaiXnnpackDelegate.create(threads);
        Interpreter.Options options = delegate != null
            ? new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(false).addDelegate(delegate)
            : new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true);
        Interpreter interpreter;
        try {
            interpreter = new Interpreter(model, options);
        } catch (RuntimeException e) {
            System.out.println("LOAD FAILED: " + e);
            return;
        }
        long loadMs = (System.nanoTime() - t0) / 1_000_000L;
        System.out.println(String.format(Locale.ROOT, "loaded in %d ms, xnnpack shim %s, threads %d, rss +%d MB",
            loadMs, delegate != null ? "yes" : "no (stock XNNPACK)", threads, (rssKb() - rssBefore) / 1024));
        StringBuilder keys = new StringBuilder();
        for (String key : interpreter.getSignatureKeys()) keys.append(key).append(' ');
        System.out.println("signatures: " + keys.toString().trim());

        float[][][] features = new float[1][128][500];
        Map<String, Object> in = new HashMap<>();
        in.put("args_0", features);
        float[][][] encoded = new float[1][1024][63];
        Map<String, Object> out = new HashMap<>();
        out.put("output_0", encoded);
        long encodeMs = 0L;
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            interpreter.runSignature(in, out, "encode");
            long ms = (System.nanoTime() - start) / 1_000_000L;
            if (i == 0) System.out.println("encode (first run) " + ms + " ms");
            else encodeMs += ms;
        }
        System.out.println("encode (mean of 2) " + encodeMs / 2 + " ms");

        float[][][] h = new float[2][1][640];
        float[][][] c = new float[2][1][640];
        long decodeMs = timeDecode(interpreter, "decode", encoded, new int[1][4], h, c, 4, 3);
        System.out.println("decode (4 tokens, mean of 3) " + decodeMs + " ms");
        long decode1Ms = timeDecode(interpreter, "decode_1", encoded, new int[1][1], h, c, 1, runs);
        System.out.println(String.format(Locale.ROOT, "decode_1 (mean of %d) %d ms", runs, decode1Ms));
        System.out.println("peak rss " + rssKb() / 1024 + " MB");
        interpreter.close();
        if (delegate != null) delegate.close();
    }

    private static long timeDecode(Interpreter interpreter, String signature, float[][][] encoded, int[][] tokens,
                                   float[][][] h, float[][][] c, int slots, int runs) {
        Map<String, Object> in = new HashMap<>();
        in.put("args_0", encoded);
        in.put("args_1", tokens);
        in.put("args_2", h);
        in.put("args_3", c);
        Map<String, Object> out = new HashMap<>();
        out.put("output_0", new float[1][63][slots][8198]);
        out.put("output_1", new float[2][1][640]);
        out.put("output_2", new float[2][1][640]);
        interpreter.runSignature(in, out, signature);
        long total = 0L;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            interpreter.runSignature(in, out, signature);
            total += System.nanoTime() - start;
        }
        return total / runs / 1_000_000L;
    }

    private static long rssKb() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) return Long.parseLong(line.replaceAll("[^0-9]", ""));
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return 0L;
    }
}
