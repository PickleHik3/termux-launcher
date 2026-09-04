package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TaiConversationTranscriptTest {

    @Test
    public void fingerprints_skipSystemAndDeveloperMessages() throws Exception {
        JSONArray messages = new JSONArray()
            .put(new JSONObject().put("role", "system").put("content", "rules"))
            .put(new JSONObject().put("role", "developer").put("content", "more rules"))
            .put(new JSONObject().put("role", "user").put("content", "hi"));

        List<String> fingerprints = TaiConversationTranscript.fingerprints(messages);

        assertEquals(1, fingerprints.size());
        assertTrue(fingerprints.get(0).startsWith("user"));
    }

    @Test
    public void secondTurn_continuesTheFirstTurnPlusTheRuntimesOwnReply() throws Exception {
        JSONArray first = new JSONArray()
            .put(new JSONObject().put("role", "system").put("content", "rules"))
            .put(new JSONObject().put("role", "user").put("content", "hi"));
        List<String> held = TaiConversationTranscript.extend(
            TaiConversationTranscript.fingerprints(first),
            TaiConversationTranscript.assistantFingerprint("Hello! How can I help?\n", new JSONArray()));

        // Clients echo the reply with extra fields and without the trailing newline.
        JSONArray second = new JSONArray()
            .put(new JSONObject().put("role", "system").put("content", "rules"))
            .put(new JSONObject().put("role", "user").put("content", "hi"))
            .put(new JSONObject().put("role", "assistant").put("content", "Hello! How can I help?")
                .put("refusal", JSONObject.NULL).put("annotations", new JSONArray()))
            .put(new JSONObject().put("role", "user").put("content", "what is 2+2"));

        assertTrue(TaiConversationTranscript.continuesFrom(held, TaiConversationTranscript.fingerprints(second)));
    }

    @Test
    public void editedOrTrimmedHistory_doesNotContinue() throws Exception {
        List<String> held = Arrays.asList(
            TaiConversationTranscript.fingerprints(new JSONArray()
                .put(new JSONObject().put("role", "user").put("content", "hi"))).get(0),
            TaiConversationTranscript.assistantFingerprint("Hello", null));

        JSONArray edited = new JSONArray()
            .put(new JSONObject().put("role", "user").put("content", "hello there"))
            .put(new JSONObject().put("role", "assistant").put("content", "Hello"))
            .put(new JSONObject().put("role", "user").put("content", "next"));
        assertFalse(TaiConversationTranscript.continuesFrom(held, TaiConversationTranscript.fingerprints(edited)));

        JSONArray trimmed = new JSONArray().put(new JSONObject().put("role", "user").put("content", "next"));
        assertFalse(TaiConversationTranscript.continuesFrom(held, TaiConversationTranscript.fingerprints(trimmed)));

        JSONArray twoNew = new JSONArray()
            .put(new JSONObject().put("role", "user").put("content", "hi"))
            .put(new JSONObject().put("role", "assistant").put("content", "Hello"))
            .put(new JSONObject().put("role", "user").put("content", "a"))
            .put(new JSONObject().put("role", "user").put("content", "b"));
        assertFalse(TaiConversationTranscript.continuesFrom(held, TaiConversationTranscript.fingerprints(twoNew)));

        JSONArray same = new JSONArray()
            .put(new JSONObject().put("role", "user").put("content", "hi"))
            .put(new JSONObject().put("role", "assistant").put("content", "Hello"));
        assertFalse(TaiConversationTranscript.continuesFrom(held, TaiConversationTranscript.fingerprints(same)));
    }

    @Test
    public void legacyConversation_neverContinuesATranscript() throws Exception {
        JSONArray one = new JSONArray().put(new JSONObject().put("role", "user").put("content", "hi"));
        assertFalse(TaiConversationTranscript.continuesFrom(null, TaiConversationTranscript.fingerprints(one)));
        assertTrue(TaiConversationTranscript.continuesFrom(Collections.emptyList(), TaiConversationTranscript.fingerprints(one)));
    }

    @Test
    public void toolCalls_matchByNameAndCanonicalArgumentsNotByIdOrKeyOrder() throws Exception {
        JSONArray produced = new JSONArray().put(new JSONObject()
            .put("id", "tai-gen-1-call-1").put("type", "function")
            .put("function", new JSONObject().put("name", "get_weather")
                .put("arguments", "{\"city\":\"Kuwait City\",\"units\":\"c\"}")));
        String reply = TaiConversationTranscript.assistantFingerprint(null, produced);

        JSONObject echoed = new JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
            .put("tool_calls", new JSONArray().put(new JSONObject()
                .put("id", "call_abc").put("type", "function")
                .put("function", new JSONObject().put("name", "get_weather")
                    .put("arguments", "{\"units\": \"c\", \"city\": \"Kuwait City\"}"))));
        assertEquals(reply, TaiConversationTranscript.fingerprints(new JSONArray().put(echoed)).get(0));

        JSONObject different = new JSONObject(echoed.toString());
        different.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function")
            .put("arguments", "{\"city\":\"Dubai\"}");
        assertNotEquals(reply, TaiConversationTranscript.fingerprints(new JSONArray().put(different)).get(0));
    }

    @Test
    public void toolResultsAndMediaParts_areDistinguished() throws Exception {
        JSONArray a = new JSONArray().put(new JSONObject().put("role", "tool").put("tool_call_id", "1").put("content", "{\"temp\":41}"));
        JSONArray b = new JSONArray().put(new JSONObject().put("role", "tool").put("tool_call_id", "2").put("content", "{\"temp\":41}"));
        JSONArray c = new JSONArray().put(new JSONObject().put("role", "tool").put("tool_call_id", "1").put("content", "{\"temp\":12}"));
        assertEquals(TaiConversationTranscript.fingerprints(a), TaiConversationTranscript.fingerprints(b));
        assertNotEquals(TaiConversationTranscript.fingerprints(a), TaiConversationTranscript.fingerprints(c));

        JSONArray image1 = new JSONArray().put(new JSONObject().put("role", "user").put("content", new JSONArray()
            .put(new JSONObject().put("type", "text").put("text", "what is this"))
            .put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:image/png;base64,AAAA")))));
        JSONArray image2 = new JSONArray().put(new JSONObject().put("role", "user").put("content", new JSONArray()
            .put(new JSONObject().put("type", "text").put("text", "what is this"))
            .put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:image/png;base64,BBBB")))));
        assertNotEquals(TaiConversationTranscript.fingerprints(image1), TaiConversationTranscript.fingerprints(image2));
    }
}
