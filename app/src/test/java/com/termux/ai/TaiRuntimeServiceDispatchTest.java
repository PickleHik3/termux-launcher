package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiRuntimeServiceDispatchTest {

    @Test
    public void cancelAndUnload_useConcurrentControlLane() {
        assertTrue(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_CANCEL));
        assertTrue(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_UNLOAD_MODEL));
        assertFalse(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_OPENAI_CHAT));
        assertFalse(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_LOAD_MODEL));
    }

    /** Skip-wait reaches an active bench the same way cancel reaches an active generation. */
    @Test
    public void benchSkipWait_usesConcurrentControlLaneAndIsNeverRefusedDuringBench() {
        assertTrue(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_BENCH_SKIP_WAIT));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_BENCH_SKIP_WAIT));
    }

    /**
     * The busy rule for a running bench: chat-lane work is refused, control and status pass, and
     * speech keeps its own lanes.
     */
    @Test
    public void aRunningBench_refusesChatLaneWorkAndLetsControlThrough() {
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_LOAD_MODEL));
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_KEEP_WARM));
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_OPENAI_CHAT));
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM));
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_OPENAI_COMPLETION));
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM));
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_EMBEDDINGS));
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_BENCHMARK));
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_BENCH_RUN));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_CANCEL));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_UNLOAD_MODEL));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_STATUS));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_RUNTIME_STATUS));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_PREFLIGHT));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_TRANSCRIBE));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_TTS_SPEAK));
        // The bench itself is neither a control nor a speech operation: it runs on the serial lane.
        assertFalse(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_BENCH_RUN));
        assertFalse(TaiRuntimeService.isSttOperation(TaiRuntimeIpc.OP_BENCH_RUN));
        assertFalse(TaiRuntimeService.isTtsOperation(TaiRuntimeIpc.OP_BENCH_RUN));
    }

    /** Speech-to-text has its own lane: it must never queue behind a chat generation. */
    @Test
    public void transcribeAndSttWarm_useTheSttLane() {
        assertTrue(TaiRuntimeService.isSttOperation(TaiRuntimeIpc.OP_TRANSCRIBE));
        assertTrue(TaiRuntimeService.isSttOperation(TaiRuntimeIpc.OP_STT_WARM));
        assertFalse(TaiRuntimeService.isSttOperation(TaiRuntimeIpc.OP_OPENAI_CHAT));
        assertFalse(TaiRuntimeService.isSttOperation(TaiRuntimeIpc.OP_EMBEDDINGS));
        assertFalse(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_TRANSCRIBE));
        assertFalse(TaiRuntimeService.isStatusOperation(TaiRuntimeIpc.OP_TRANSCRIBE));
    }
}
