package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class StreamEventTest {
    private static StreamEvent parse(String json) throws Exception {
        return StreamEvent.parse(new JSONObject(json));
    }

    @Test
    public void contentDelta() throws Exception {
        StreamEvent e = parse("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hel\"},\"finish_reason\":null}]}");
        assertEquals("Hel", e.delta);
        assertFalse(e.finished);
        assertFalse(e.isError());
    }

    @Test
    public void roleOnlyChunkHasNoText() throws Exception {
        StreamEvent e = parse("{\"choices\":[{\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}");
        assertEquals("", e.delta);
        assertFalse(e.finished);
    }

    @Test
    public void finishReasonEndsTheAnswer() throws Exception {
        StreamEvent e = parse("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
        assertTrue(e.finished);
        assertEquals("", e.delta);
    }

    @Test
    public void usageOnlyChunkWithNoChoicesIsEmpty() throws Exception {
        StreamEvent e = parse("{\"choices\":[],\"usage\":{\"total_tokens\":3}}");
        assertEquals("", e.delta);
        assertFalse(e.finished);
        assertFalse(e.isError());
    }

    @Test
    public void runtimeErrorEvents() throws Exception {
        StreamEvent crashed = parse("{\"error\":{\"message\":\"x\",\"code\":\"tai_runtime_crashed\"}}");
        assertEquals(AssistantMessage.ERROR_RUNTIME_DIED, crashed.errorCode);
        assertEquals("tai_runtime_crashed", crashed.rawErrorCode);
        // The shape TaiManager.openAiError builds.
        StreamEvent notInstalled = parse("{\"ok\":false,\"message\":\"m\",\"code\":\"model_not_installed\","
            + "\"error\":{\"message\":\"m\",\"code\":\"model_not_installed\"},\"_statusCode\":404}");
        assertEquals(AssistantMessage.ERROR_MODEL_NOT_DOWNLOADED, notInstalled.errorCode);
        assertEquals(AssistantMessage.ERROR_NOT_ENOUGH_MEMORY,
            parse("{\"error\":{\"code\":\"insufficient_memory\"}}").errorCode);
        assertEquals(AssistantMessage.ERROR_TIMEOUT,
            parse("{\"error\":{\"code\":\"tai_runtime_timeout\"}}").errorCode);
        assertEquals(AssistantMessage.ERROR_MODEL_NOT_LOADED,
            parse("{\"error\":{\"code\":\"model_not_loaded\"}}").errorCode);
        assertEquals(AssistantMessage.ERROR_OTHER, parse("{\"ok\":false}").errorCode);
        assertNull(parse("{\"ok\":false}").rawErrorCode);
    }

    @Test
    public void unknownCodesFallBackByKeyword() {
        assertEquals(AssistantMessage.ERROR_NOT_ENOUGH_MEMORY, StreamEvent.mapErrorCode("gpu_memory_exhausted"));
        assertEquals(AssistantMessage.ERROR_OTHER, StreamEvent.mapErrorCode("weird"));
        assertEquals(AssistantMessage.ERROR_OTHER, StreamEvent.mapErrorCode(null));
    }
}
