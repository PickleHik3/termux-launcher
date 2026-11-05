package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The accelerator choice: Auto's GPU try, its CPU fallback, and what is and is not remembered. */
public class SttGraphChooserTest {
    private static final File MODEL = new File("model.tflite");

    private final FakeLiteRtGraph interpGraph = new FakeLiteRtGraph();
    private final FakeLiteRtGraph cpuGraph = new FakeLiteRtGraph();
    private final FakeLiteRtGraph gpuGraph = new FakeLiteRtGraph();
    private final FakeMemory memory = new FakeMemory();
    @Nullable private RuntimeException gpuLoadThrows;
    private final List<String> prepared = new ArrayList<>();

    @After
    public void clearInterrupt() {
        Thread.interrupted();
    }

    private SttGraphChooser chooser() {
        LiteRtGraph.Loader gpu = (model, threads) -> {
            gpuGraph.loads++;
            if (gpuLoadThrows != null) throw gpuLoadThrows;
            return gpuGraph;
        };
        return new SttGraphChooser(interpGraph.loader(), cpuGraph.loader(), gpu);
    }

    /** Binds every graph; with {@code verify}, rejects the GPU graph when {@code gpuSane} is false. */
    private SttGraphChooser.Prepare prepare(boolean gpuSane) {
        return (graph, verify) -> {
            prepared.add((graph == gpuGraph ? "gpu" : graph == cpuGraph ? "cpu" : "interp") + (verify ? "+verify" : ""));
            if (verify && !gpuSane) throw new IllegalStateException("GPU encoder output is not sane: NaN");
        };
    }

    @Test
    public void autoKeepsAGpuThatLoadsAndIsSaneAndRemembersIt() throws Exception {
        SttGraphChooser.Opened opened = chooser().open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(true));
        assertSame(gpuGraph, opened.graph);
        assertEquals("gpu", opened.accelerator);
        assertEquals("", opened.fallbackReason);
        assertEquals(0, cpuGraph.loads);
        assertEquals(Arrays.asList("gpu+verify"), prepared);
        assertEquals(Arrays.asList("gpu:true:"), memory.records);
        assertEquals(0, memory.unfinished);
    }

    @Test
    public void autoFallsBackToCpuWhenTheGpuLoadThrowsAndRecordsIt() throws Exception {
        gpuLoadThrows = new IllegalStateException("no OpenCL");
        SttGraphChooser.Opened opened = chooser().open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(true));
        assertSame(cpuGraph, opened.graph);
        assertEquals("cpu", opened.accelerator);
        assertTrue(opened.fallbackReason, opened.fallbackReason.contains("no OpenCL"));
        assertEquals(1, cpuGraph.loads);
        assertEquals(Arrays.asList("cpu"), prepared);
        assertEquals(1, memory.records.size());
        assertTrue(memory.records.get(0), memory.records.get(0).startsWith("gpu:false:"));
        assertEquals(0, memory.unfinished);
    }

    @Test
    public void autoFallsBackToCpuWhenTheGpuOutputIsNotSaneAndClosesTheGpuGraph() throws Exception {
        SttGraphChooser.Opened opened = chooser().open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(false));
        assertSame(cpuGraph, opened.graph);
        assertEquals(1, gpuGraph.closes);
        assertEquals(Arrays.asList("gpu+verify", "cpu"), prepared);
        assertTrue(memory.records.get(0), memory.records.get(0).contains("not sane"));
    }

    @Test
    public void autoGoesStraightToCpuOnARememberedGpuFailure() throws Exception {
        memory.failure = "no OpenCL";
        SttGraphChooser.Opened opened = chooser().open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(true));
        assertSame(cpuGraph, opened.graph);
        assertEquals(0, gpuGraph.loads);
        assertEquals(0, memory.begun);
        assertTrue(memory.records.isEmpty());
        assertTrue(opened.fallbackReason, opened.fallbackReason.contains("no OpenCL"));
    }

    @Test
    public void anExplicitGpuAskRetriesPastAnOrdinaryFailureButNotPastACrash() throws Exception {
        memory.failure = "no OpenCL";
        assertSame(gpuGraph, chooser().open(MODEL, 4, SttAccelerator.GPU, memory, prepare(true)).graph);
        memory.failure = SttGraphChooser.CRASH_REASON_PREFIX + " the process died";
        assertSame(cpuGraph, chooser().open(MODEL, 4, SttAccelerator.GPU, memory, prepare(true)).graph);
        assertEquals(1, gpuGraph.loads);
    }

    @Test
    public void aCancelledGpuLoadIsRethrownAndNotRecorded() throws Exception {
        gpuLoadThrows = new CancellationException("Model load cancelled.");
        try {
            chooser().open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(true));
            fail("a cancelled load must not fall back");
        } catch (CancellationException expected) {
            // The transcription was abandoned; the GPU was not judged.
        }
        assertTrue(memory.records.isEmpty());
        assertEquals(0, memory.unfinished);
        assertEquals(0, cpuGraph.loads);
    }

    @Test
    public void anInterruptedThreadIsACancellationNotAGpuFailure() throws Exception {
        gpuLoadThrows = new IllegalStateException("native load aborted");
        Thread.currentThread().interrupt();
        try {
            chooser().open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(true));
            fail("an interrupted load must not fall back");
        } catch (IllegalStateException expected) {
            assertEquals("native load aborted", expected.getMessage());
        }
        assertTrue(memory.records.isEmpty());
    }

    @Test
    public void oneUnfinishedAttemptIsRetriedAndTheSecondInARowIsACrash() throws Exception {
        memory.unfinished = 1;
        assertSame(gpuGraph, chooser().open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(true)).graph);

        memory.records.clear();
        memory.unfinished = SttGraphChooser.MAX_UNFINISHED_GPU_ATTEMPTS;
        SttGraphChooser.Opened opened = chooser().open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(true));
        assertSame(cpuGraph, opened.graph);
        assertEquals(1, gpuGraph.loads);
        assertEquals(0, memory.unfinished);
        assertEquals(1, memory.records.size());
        assertTrue(memory.records.get(0), memory.records.get(0).startsWith("gpu:false:" + SttGraphChooser.CRASH_REASON_PREFIX));
    }

    @Test
    public void cpuAndInterpreterAsksNeverTouchTheGpuOrTheRecord() throws Exception {
        assertSame(cpuGraph, chooser().open(MODEL, 4, SttAccelerator.CPU, memory, prepare(true)).graph);
        SttGraphChooser.Opened interp = chooser().open(MODEL, 4, SttAccelerator.INTERPRETER, memory, prepare(true));
        assertSame(interpGraph, interp.graph);
        assertEquals("interp", interp.accelerator);
        assertEquals(0, gpuGraph.loads);
        assertEquals(0, memory.begun);
        assertTrue(memory.records.isEmpty());
        assertEquals(Arrays.asList("cpu", "interp"), prepared);
    }

    @Test
    public void aCpuFallbackThatAlsoFailsFailsTheLoad() throws Exception {
        gpuLoadThrows = new IllegalStateException("no OpenCL");
        SttGraphChooser chooser = new SttGraphChooser(interpGraph.loader(), (model, threads) -> {
            throw new IllegalStateException("bad file");
        }, (model, threads) -> {
            throw gpuLoadThrows;
        });
        try {
            chooser.open(MODEL, 4, SttAccelerator.AUTO, memory, prepare(true));
            fail();
        } catch (IllegalStateException expected) {
            assertEquals("bad file", expected.getMessage());
        }
    }

    @Test
    public void acceleratorIdsParseAndUnknownIsAuto() {
        assertEquals(SttAccelerator.GPU, SttAccelerator.parse(" GPU "));
        assertEquals(SttAccelerator.CPU, SttAccelerator.parse("cpu"));
        assertEquals(SttAccelerator.INTERPRETER, SttAccelerator.parse("interp"));
        assertEquals(SttAccelerator.AUTO, SttAccelerator.parse("npu"));
        assertEquals(SttAccelerator.AUTO, SttAccelerator.parse(null));
        assertFalse(SttGraphChooser.isCancellation(new IllegalStateException("x")));
        assertTrue(SttGraphChooser.isCancellation(new IllegalStateException("x", new InterruptedException())));
    }

    /** A per-model record in memory, counting what the chooser asked of it. */
    static final class FakeMemory implements SttGraphChooser.Memory {
        @Nullable String failure;
        int unfinished;
        int begun;
        final List<String> records = new ArrayList<>();

        @Nullable
        @Override
        public String gpuFailure() {
            return failure;
        }

        @Override
        public int beginGpuAttempt() {
            begun++;
            return unfinished++;
        }

        @Override
        public void endGpuAttempt() {
            unfinished = 0;
        }

        @Override
        public void recordGpu(boolean worked, @NonNull String reason) {
            records.add("gpu:" + worked + ":" + reason);
            if (!worked) failure = reason;
        }
    }
}
