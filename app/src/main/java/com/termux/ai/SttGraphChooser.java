package com.termux.ai;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litert.Accelerator;

import java.io.File;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.CancellationException;

/**
 * Opens a speech graph on the accelerator asked for, with the GPU never able to break a
 * transcription.
 *
 * <p>{@link SttAccelerator#AUTO} and {@link SttAccelerator#CPU} load CompiledModel on the CPU and
 * never touch the GPU or {@link Memory}: on the phones measured the GPU was slower or wrong for the
 * shipped speech models. {@link SttAccelerator#GPU} is the only way onto the GPU: the graph loads,
 * the caller's {@link Prepare} binds it and runs a fixed input through it ({@code verify}), and any
 * throw from either (a load the accelerator refuses, an output that is not sane) closes the GPU
 * graph, records the failure and loads the CPU graph instead. A GPU that worked is recorded as such.
 * An ordinary failure on record does not stop a {@code GPU} request, which tries again.
 *
 * <p>Two things are not verdicts on the GPU:
 * <ul>
 *   <li>A cancelled load (the thread interrupted, or a cancellation in the cause chain) records
 *       nothing and is rethrown; the transcription was abandoned, not the GPU.</li>
 *   <li>A process that dies during the GPU attempt (a native crash in the GPU compiler, but also
 *       a kill or an eviction) leaves its attempt unfinished in {@link Memory}. One unfinished
 *       attempt is tried again; the second one in a row is recorded as a GPU crash and the load
 *       goes to CPU, so a GPU that kills the process cannot do it forever. A crash record
 *       stops a {@code GPU} request, which would otherwise crash every time.</li>
 * </ul>
 */
final class SttGraphChooser {
    /** Unfinished GPU attempts in a row after which the GPU is taken to crash the process. */
    static final int MAX_UNFINISHED_GPU_ATTEMPTS = 2;
    static final String CRASH_REASON_PREFIX = "crash:";

    /** What this phone remembers about one model's GPU. */
    interface Memory {
        /** The reason of an unexpired GPU failure on record, or {@code null}. */
        @Nullable
        String gpuFailure();

        /** Marks one more GPU attempt as begun; returns how many were unfinished before it. */
        int beginGpuAttempt();

        /** The GPU attempt ended in this process (either way): no attempt is unfinished. */
        void endGpuAttempt();

        void recordGpu(boolean worked, @NonNull String reason);
    }

    /** Remembers nothing: the bench, and the runtime's test seam with no Context. */
    static final Memory NO_MEMORY = new Memory() {
        @Nullable
        @Override
        public String gpuFailure() {
            return null;
        }

        @Override
        public int beginGpuAttempt() {
            return 0;
        }

        @Override
        public void endGpuAttempt() {
        }

        @Override
        public void recordGpu(boolean worked, @NonNull String reason) {
        }
    };

    /** Binds the freshly loaded graph; with {@code verify}, also proves its output sane or throws. */
    interface Prepare {
        void apply(@NonNull LiteRtGraph graph, boolean verify) throws Exception;
    }

    /** The graph opened and how it came to be on its accelerator. */
    static final class Opened {
        @NonNull final LiteRtGraph graph;
        /** {@code "gpu"}, {@code "cpu"} or {@code "interp"}: where it runs. */
        @NonNull final String accelerator;
        /** Why it is not on the GPU when the GPU was asked for or tried; empty otherwise. */
        @NonNull final String fallbackReason;

        Opened(@NonNull LiteRtGraph graph, @NonNull String accelerator, @NonNull String fallbackReason) {
            this.graph = graph;
            this.accelerator = accelerator;
            this.fallbackReason = fallbackReason;
        }
    }

    @NonNull private final LiteRtGraph.Loader interpreter;
    @NonNull private final LiteRtGraph.Loader cpu;
    @NonNull private final LiteRtGraph.Loader gpu;

    SttGraphChooser(@NonNull LiteRtGraph.Loader interpreter, @NonNull LiteRtGraph.Loader cpu, @NonNull LiteRtGraph.Loader gpu) {
        this.interpreter = interpreter;
        this.cpu = cpu;
        this.gpu = gpu;
    }

    /** The real loaders: InterpreterGraph, and CompiledModelGraph on CPU and GPU. */
    @NonNull
    static SttGraphChooser standard() {
        return new SttGraphChooser(InterpreterGraph.LOADER,
            CompiledModelGraph.loader(Accelerator.CPU), CompiledModelGraph.loader(Accelerator.GPU));
    }

    @NonNull
    Opened open(@NonNull File model, int threads, @NonNull SttAccelerator requested, @NonNull Memory memory,
                @NonNull Prepare prepare) throws Exception {
        if (requested == SttAccelerator.INTERPRETER) return load(interpreter, model, threads, prepare, "interp", "");
        if (requested == SttAccelerator.CPU || requested == SttAccelerator.AUTO) return load(cpu, model, threads, prepare, "cpu", "");
        String failure = memory.gpuFailure();
        if (failure != null && failure.startsWith(CRASH_REASON_PREFIX)) {
            return load(cpu, model, threads, prepare, "cpu", "gpu failed before: " + failure);
        }
        int unfinished = memory.beginGpuAttempt();
        if (unfinished >= MAX_UNFINISHED_GPU_ATTEMPTS) {
            memory.endGpuAttempt();
            String reason = CRASH_REASON_PREFIX + " the process died during " + unfinished + " GPU loads in a row";
            memory.recordGpu(false, reason);
            return load(cpu, model, threads, prepare, "cpu", reason);
        }
        LiteRtGraph graph = null;
        try {
            graph = gpu.load(model, threads);
            prepare.apply(graph, true);
        } catch (Throwable t) {
            closeQuietly(graph);
            memory.endGpuAttempt();
            if (isCancellation(t)) throw asException(t);
            String reason = message(t);
            memory.recordGpu(false, reason);
            return load(cpu, model, threads, prepare, "cpu", reason);
        }
        memory.endGpuAttempt();
        memory.recordGpu(true, "");
        return new Opened(graph, "gpu", "");
    }

    @NonNull
    private static Opened load(@NonNull LiteRtGraph.Loader loader, @NonNull File model, int threads,
                               @NonNull Prepare prepare, @NonNull String accelerator, @NonNull String reason) throws Exception {
        LiteRtGraph graph = loader.load(model, threads);
        try {
            prepare.apply(graph, false);
        } catch (Throwable t) {
            closeQuietly(graph);
            throw asException(t);
        }
        return new Opened(graph, accelerator, reason);
    }

    /** A cancelled load: the thread was interrupted, or a cancellation is somewhere in the chain. */
    static boolean isCancellation(@NonNull Throwable t) {
        if (Thread.currentThread().isInterrupted()) return true;
        for (Throwable at = t; at != null; at = at.getCause() == at ? null : at.getCause()) {
            if (at instanceof InterruptedException || at instanceof CancellationException
                || at instanceof ClosedByInterruptException || at instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    private static void closeQuietly(@Nullable LiteRtGraph graph) {
        if (graph == null) return;
        try {
            graph.close();
        } catch (Throwable ignored) {
            // The GPU graph is being dropped either way.
        }
    }

    @NonNull
    private static Exception asException(@NonNull Throwable t) {
        if (t instanceof Exception) return (Exception) t;
        return new IllegalStateException(message(t), t);
    }

    @NonNull
    static String message(@NonNull Throwable t) {
        String text = t.getMessage();
        String name = t.getClass().getSimpleName();
        return text == null || text.trim().isEmpty() ? name : name + ": " + text.trim();
    }
}
