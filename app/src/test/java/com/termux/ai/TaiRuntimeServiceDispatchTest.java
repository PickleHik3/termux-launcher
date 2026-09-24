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
