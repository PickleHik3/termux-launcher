package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AssistantMessageErrorTest {
    @Test
    public void runtimeCodesMapToPageCodes() {
        assertEquals(AssistantMessage.ERROR_RUNTIME_DIED, AssistantMessage.errorFor("tai_runtime_crashed"));
        assertEquals(AssistantMessage.ERROR_MODEL_NOT_DOWNLOADED, AssistantMessage.errorFor("model_not_installed"));
        assertEquals(AssistantMessage.ERROR_NOT_ENOUGH_MEMORY, AssistantMessage.errorFor("insufficient_memory"));
        assertEquals(AssistantMessage.ERROR_TIMEOUT, AssistantMessage.errorFor("tai_runtime_timeout"));
        assertEquals(AssistantMessage.ERROR_MODEL_NOT_LOADED, AssistantMessage.errorFor("model_not_loaded"));
    }

    @Test
    public void unknownCodesFallBackByKeyword() {
        assertEquals(AssistantMessage.ERROR_NOT_ENOUGH_MEMORY, AssistantMessage.errorFor("gpu_memory_exhausted"));
        assertEquals(AssistantMessage.ERROR_OTHER, AssistantMessage.errorFor("weird"));
        assertEquals(AssistantMessage.ERROR_OTHER, AssistantMessage.errorFor(null));
    }
}
