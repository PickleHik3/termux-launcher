package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The runtime's own unload deadline for a momentary load (review T3): which requests arm it, when
 * it is due, and what it unloads. The scheduling is the service's; these are its decisions.
 */
public class TaiRuntimeServiceDeadlineTest {
    private static final String VISION = "gemma-4-e4b-it-litert-lm-vision";

    private static String body(String model, String loadClass) {
        return "{\"model\":\"" + model + "\",\"messages\":[],\"load_class\":\"" + loadClass + "\",\"context_window\":2048}";
    }

    @Test
    public void aMomentaryChatOrLoadNamesTheModelToUnload() {
        assertEquals(VISION, TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_CHAT, body(VISION, "momentary")));
        assertEquals(VISION, TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM, body(VISION, "momentary")));
        assertEquals(VISION, TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_LOAD_MODEL, body(VISION, " momentary ")));
        assertEquals(VISION, TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_COMPLETION, "{\"modelId\":\""
            + VISION + "\",\"load_class\":\"momentary\"}"));
    }

    @Test
    public void anythingElseArmsNoDeadline() {
        // Another load class, no class, no model, or not JSON: an ordinary load the caller keeps.
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_CHAT, body(VISION, "ordinary")));
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_CHAT, "{\"model\":\"" + VISION + "\"}"));
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_CHAT, "{\"load_class\":\"momentary\"}"));
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_CHAT, "{\"model\":\"  \",\"load_class\":\"momentary\"}"));
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_CHAT, "not json \"load_class\""));
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_OPENAI_CHAT, null));
        // Operations that load nothing never arm it, whatever the body says.
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_STATUS, body(VISION, "momentary")));
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_UNLOAD_MODEL, body(VISION, "momentary")));
        assertNull(TaiRuntimeService.momentaryModelId(TaiRuntimeIpc.OP_EMBEDDINGS, body(VISION, "momentary")));
    }

    @Test
    public void onlyLoadingOperationsCanArmIt() {
        assertTrue(TaiRuntimeService.isLoadingOperation(TaiRuntimeIpc.OP_LOAD_MODEL));
        assertTrue(TaiRuntimeService.isLoadingOperation(TaiRuntimeIpc.OP_OPENAI_CHAT));
        assertTrue(TaiRuntimeService.isLoadingOperation(TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM));
        assertTrue(TaiRuntimeService.isLoadingOperation(TaiRuntimeIpc.OP_OPENAI_COMPLETION));
        assertTrue(TaiRuntimeService.isLoadingOperation(TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM));
        assertFalse(TaiRuntimeService.isLoadingOperation(TaiRuntimeIpc.OP_UNLOAD_MODEL));
        assertFalse(TaiRuntimeService.isLoadingOperation(TaiRuntimeIpc.OP_CANCEL));
        assertFalse(TaiRuntimeService.isLoadingOperation(TaiRuntimeIpc.OP_STATUS));
    }

    /** Three minutes from the load's start, counted by the runtime, whatever the caller's own wait was. */
    @Test
    public void theDeadlineIsDueThreeMinutesAfterTheLoadStarted() {
        long armed = 1_000_000L;
        assertFalse(TaiRuntimeService.momentaryDeadlineDue(armed, armed));
        assertFalse(TaiRuntimeService.momentaryDeadlineDue(armed, armed + 179_999L));
        assertTrue(TaiRuntimeService.momentaryDeadlineDue(armed, armed + 180_000L));
        assertTrue(TaiRuntimeService.momentaryDeadlineDue(armed, armed + 10L * 60_000L));
        // The director's own wait is 180 s; the deadline does not depend on it.
        assertEquals(180_000L, TaiLoadBudget.MOMENTARY_DEADLINE_MS);
    }

    /** At the deadline: the armed model loaded, generating or still loading is unloaded; anything else is left. */
    @Test
    public void theDeadlineUnloadsTheArmedModelWhateverItIsDoing() {
        // Idle or generating: it is the loaded model.
        assertTrue(TaiRuntimeService.unloadAtDeadline(VISION, VISION, "loaded"));
        assertTrue(TaiRuntimeService.unloadAtDeadline(VISION, VISION, "generating"));
        // The 180 s ran out during the load: nothing is loaded yet, and the unload still has to happen.
        assertTrue(TaiRuntimeService.unloadAtDeadline(VISION, null, "loading"));
        // The caller already unloaded, or someone loaded another model: not this deadline's to touch.
        assertFalse(TaiRuntimeService.unloadAtDeadline(VISION, null, "unloaded"));
        assertFalse(TaiRuntimeService.unloadAtDeadline(VISION, null, null));
        assertFalse(TaiRuntimeService.unloadAtDeadline(VISION, "gemma-4-e2b-it-litert-lm", "loaded"));
        assertFalse(TaiRuntimeService.unloadAtDeadline(VISION, "gemma-4-e2b-it-litert-lm", "loading"));
    }
}
