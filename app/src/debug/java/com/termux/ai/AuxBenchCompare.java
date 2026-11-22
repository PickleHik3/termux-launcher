package com.termux.ai;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Debug-only comparer for two {@link AuxBench} output directories (for example one from a LiteRT 1.4.2
 * build and one from 2.3.2). Plain Java, so it runs on a host JVM or under {@code app_process}:
 * {@code java -cp <classes> com.termux.ai.AuxBenchCompare DIR_A DIR_B}. Per output it prints
 * TTS max abs diff and SNR (dB, A as the reference), embedding cosine similarity per vector with the
 * minimum, and VAD max abs diff with the count of frames whose decision at 0.5 flips.
 *
 * <p>File formats: {@code tts-N.f32} and {@code vad.f32} are raw little-endian float32;
 * {@code emb.f32} is the same with two leading values, the vector count and the dimension (floats
 * holding whole numbers), then the vectors back to back.
 */
public final class AuxBenchCompare {

    private AuxBenchCompare() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("usage: AuxBenchCompare DIR_A DIR_B");
            return;
        }
        File a = new File(args[0]), b = new File(args[1]);
        int compared = 0;
        for (int n = 1; n <= 99; n++) {
            File fa = new File(a, "tts-" + n + ".f32"), fb = new File(b, "tts-" + n + ".f32");
            if (!fa.isFile() || !fb.isFile()) continue;
            float[] x = readFloats(fa), y = readFloats(fb);
            int common = Math.min(x.length, y.length);
            System.out.println(SttBenchStats.line("AUXCOMPARE_TTS", "n", String.valueOf(n),
                "samplesA", String.valueOf(x.length), "samplesB", String.valueOf(y.length),
                "maxAbsDiff", sci(maxAbsDiff(x, y, common)), "snrDb", db(snrDb(x, y, common))));
            compared++;
        }
        File ea = new File(a, "emb.f32"), eb = new File(b, "emb.f32");
        if (ea.isFile() && eb.isFile()) {
            float[] x = readFloats(ea), y = readFloats(eb);
            int countA = (int) x[0], dimA = (int) x[1], countB = (int) y[0], dimB = (int) y[1];
            if (countA != countB || dimA != dimB) {
                System.out.println(SttBenchStats.line("AUXCOMPARE_EMB", "error",
                    "shape differs: " + countA + "x" + dimA + " vs " + countB + "x" + dimB));
            } else {
                double min = Double.POSITIVE_INFINITY;
                for (int i = 0; i < countA; i++) {
                    double cos = cosine(x, 2 + i * dimA, y, 2 + i * dimA, dimA);
                    min = Math.min(min, cos);
                    System.out.println(SttBenchStats.line("AUXCOMPARE_EMB", "vector", String.valueOf(i),
                        "cosine", SttBenchStats.num(cos, 6)));
                }
                System.out.println(SttBenchStats.line("AUXCOMPARE_EMB_SUMMARY", "vectors", String.valueOf(countA),
                    "dim", String.valueOf(dimA), "minCosine", SttBenchStats.num(min, 6)));
            }
            compared++;
        }
        File va = new File(a, "vad.f32"), vb = new File(b, "vad.f32");
        if (va.isFile() && vb.isFile()) {
            float[] x = readFloats(va), y = readFloats(vb);
            int common = Math.min(x.length, y.length);
            System.out.println(SttBenchStats.line("AUXCOMPARE_VAD", "framesA", String.valueOf(x.length),
                "framesB", String.valueOf(y.length), "maxAbsDiff", sci(maxAbsDiff(x, y, common)),
                "flipsAt0.5", String.valueOf(decisionFlips(x, y, common, 0.5f))));
            compared++;
        }
        if (compared == 0) {
            System.out.println("AUXCOMPARE_ERROR no matching tts-N.f32, emb.f32 or vad.f32 in both directories");
        }
    }

    /** Largest |a[i] - b[i]| over the first {@code n} values; 0 for none. */
    static double maxAbsDiff(float[] a, float[] b, int n) {
        double max = 0;
        for (int i = 0; i < n; i++) max = Math.max(max, Math.abs((double) a[i] - b[i]));
        return max;
    }

    /**
     * 10 log10(sum ref^2 / sum (ref - test)^2) over the first {@code n} values: +Infinity when the two
     * are identical, -Infinity when the reference is silent and the test is not, NaN when both are silent.
     */
    static double snrDb(float[] ref, float[] test, int n) {
        double signal = 0, noise = 0;
        for (int i = 0; i < n; i++) {
            double r = ref[i], d = r - test[i];
            signal += r * r;
            noise += d * d;
        }
        if (noise == 0) return signal == 0 ? Double.NaN : Double.POSITIVE_INFINITY;
        if (signal == 0) return Double.NEGATIVE_INFINITY;
        return 10.0 * Math.log10(signal / noise);
    }

    /** Cosine similarity of {@code length} values starting at the offsets; NaN when either vector is all zero. */
    static double cosine(float[] a, int offsetA, float[] b, int offsetB, int length) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < length; i++) {
            double x = a[offsetA + i], y = b[offsetB + i];
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        return na == 0 || nb == 0 ? Double.NaN : dot / Math.sqrt(na * nb);
    }

    /** Count of indices below {@code n} where one side is above {@code threshold} and the other is not. */
    static int decisionFlips(float[] a, float[] b, int n, float threshold) {
        int flips = 0;
        for (int i = 0; i < n; i++) if ((a[i] > threshold) != (b[i] > threshold)) flips++;
        return flips;
    }

    /** Raw little-endian float32 file. */
    static float[] readFloats(File file) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[buffer.remaining() / 4];
        buffer.asFloatBuffer().get(out);
        return out;
    }

    private static String db(double value) {
        if (value == Double.POSITIVE_INFINITY) return "inf";
        if (value == Double.NEGATIVE_INFINITY) return "-inf";
        return SttBenchStats.num(value, 2);
    }

    private static String sci(double value) {
        return String.format(Locale.ROOT, "%.3e", value);
    }
}
