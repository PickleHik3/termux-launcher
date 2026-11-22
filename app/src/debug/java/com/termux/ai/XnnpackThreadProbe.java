package com.termux.ai;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Debug-only probe: does the stock LiteRT {@link Interpreter} XNNPACK path
 * ({@code setNumThreads(n).setUseXNNPACK(true)}) actually spread inference over worker threads?
 * It loads a model, runs it on zero inputs and, while the timed runs execute, samples
 * {@code /proc/self/task} for the peak thread count and compares each thread's CPU time
 * (utime+stime from {@code /proc/self/task/<tid>/stat}) across those runs.
 *
 * <p>Run from a shell with the installed APK on the class path, e.g.
 * {@code CLASSPATH=$(pm path com.termux | cut -d: -f2) app_process /system/bin
 * com.termux.ai.XnnpackThreadProbe <model.tflite> [threads=4] [runs=5]} with
 * {@code LD_LIBRARY_PATH} pointing at the APK's native libraries. The last line starts with
 * {@code PROBE} and is easy to grep.
 */
public final class XnnpackThreadProbe {

    private static final double BUSY_SHARE = 0.05;

    private XnnpackThreadProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: XnnpackThreadProbe <model.tflite> [threads=4] [runs=5]");
            return;
        }
        File model = new File(args[0]);
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int runs = Math.max(2, args.length > 2 ? Integer.parseInt(args[2]) : 5);

        Interpreter.Options options = new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true);
        Interpreter interpreter;
        try {
            interpreter = new Interpreter(model, options);
        } catch (RuntimeException e) {
            System.out.println("LOAD FAILED: " + e);
            return;
        }

        Runnable run = buildRun(interpreter);
        long firstStart = System.nanoTime();
        run.run();
        double firstMs = (System.nanoTime() - firstStart) / 1e6;

        Sampler sampler = new Sampler();
        sampler.start();
        Map<String, Long> before = cpuTicks();
        long start = System.nanoTime();
        for (int i = 1; i < runs; i++) run.run();
        double meanMs = (System.nanoTime() - start) / 1e6 / (runs - 1);
        Map<String, Long> after = cpuTicks();
        sampler.finish();

        List<Long> deltas = new ArrayList<>();
        long total = 0L;
        for (Map.Entry<String, Long> e : after.entrySet()) {
            Long b = before.get(e.getKey());
            long d = e.getValue() - (b == null ? 0L : b);
            if (d <= 0L) continue;
            deltas.add(d);
            total += d;
        }
        deltas.sort((x, y) -> Long.compare(y, x));
        int busy = 0;
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < deltas.size(); i++) {
            double share = total == 0L ? 0.0 : (double) deltas.get(i) / total;
            if (share > BUSY_SHARE) busy++;
            if (i < 3) {
                if (i > 0) top.append(',');
                top.append(String.format(Locale.ROOT, "%.1f%%", share * 100.0));
            }
        }
        System.out.println(String.format(Locale.ROOT, "first_run_ms=%.1f cpu_ticks=%d tasks_with_cpu=%d",
            firstMs, total, deltas.size()));
        System.out.println(String.format(Locale.ROOT,
            "PROBE threads=%d mean_ms=%.1f peak_threads=%d busy_threads=%d top=%s",
            threads, meanMs, sampler.peak, busy, top));
        interpreter.close();
    }

    /** A run of the model on zero inputs: plain inputs when it has them, else its first signature. */
    private static Runnable buildRun(Interpreter interpreter) {
        int inputCount = interpreter.getInputTensorCount();
        if (inputCount > 0) {
            Object[] inputs = new Object[inputCount];
            for (int i = 0; i < inputCount; i++) inputs[i] = zeros(interpreter.getInputTensor(i));
            Map<Integer, Object> outputs = new HashMap<>();
            for (int i = 0; i < interpreter.getOutputTensorCount(); i++) {
                outputs.put(i, direct(interpreter.getOutputTensor(i).numBytes()));
            }
            return () -> interpreter.runForMultipleInputsOutputs(inputs, outputs);
        }
        String[] keys = interpreter.getSignatureKeys();
        if (keys.length == 0) throw new IllegalStateException("model has no inputs and no signatures");
        String key = keys[0];
        Map<String, Object> in = new HashMap<>();
        for (String name : interpreter.getSignatureInputs(key)) {
            in.put(name, zeros(interpreter.getInputTensorFromSignature(name, key)));
        }
        Map<String, Object> out = new HashMap<>();
        for (String name : interpreter.getSignatureOutputs(key)) {
            out.put(name, direct(interpreter.getOutputTensorFromSignature(name, key).numBytes()));
        }
        return () -> interpreter.runSignature(in, out, key);
    }

    private static ByteBuffer zeros(Tensor tensor) {
        return direct(tensor.numBytes());
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    /** tid to utime+stime ticks for every task of this process right now. */
    private static Map<String, Long> cpuTicks() {
        Map<String, Long> ticks = new HashMap<>();
        String[] tids = new File("/proc/self/task").list();
        if (tids == null) return ticks;
        for (String tid : tids) {
            try {
                String stat = new String(Files.readAllBytes(new File("/proc/self/task/" + tid + "/stat").toPath()),
                    StandardCharsets.UTF_8);
                // Fields after the ")" that closes the (possibly spaced) comm: state is field 3,
                // utime and stime are fields 14 and 15.
                String[] f = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
                ticks.put(tid, Long.parseLong(f[11]) + Long.parseLong(f[12]));
            } catch (IOException | RuntimeException ignored) {
                // The thread exited between the listing and the read.
            }
        }
        return ticks;
    }

    /** Polls the task count until {@link #finish}. */
    private static final class Sampler extends Thread {
        volatile int peak;
        private volatile boolean done;

        Sampler() {
            setDaemon(true);
        }

        @Override
        public void run() {
            while (!done) {
                String[] tids = new File("/proc/self/task").list();
                if (tids != null && tids.length > peak) peak = tids.length;
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        void finish() throws InterruptedException {
            done = true;
            join();
        }
    }
}
