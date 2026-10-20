package com.termux.app.terminal.inappkeyboard.voice;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * What a dictation says when the speech runtime goes away under a phrase: the client's crash answer
 * becomes one short line; every other failure keeps its own message.
 */
public class VoiceInputSessionFailureTest {

    private static final String CLOSED = "the speech model closed";

    /** The flat answer TaiRuntimeServiceClient gives every request the dead process was serving. */
    @Test
    public void aRuntimeThatDiedUnderAPhraseReadsAsOneShortLine() throws Exception {
        JSONObject crashed = new JSONObject().put("ok", false).put("error", "tai_runtime_crashed")
            .put("message", "AI runtime process died while running the model; the request was not retried. "
                + "Try again, on CPU or with a smaller model.").put("_statusCode", 503);
        VoiceInputSession.Failure failure = VoiceInputSession.Failure.of(crashed);
        assertNotNull(failure);
        VoiceInputSession.Failure shown = failure.forUser(CLOSED);
        assertEquals(VoiceInputSession.RUNTIME_CRASHED, shown.code);
        assertEquals(CLOSED, shown.message);
    }

    @Test
    public void otherFailuresKeepTheirMessage() throws Exception {
        VoiceInputSession.Failure noRoom = VoiceInputSession.Failure.of(new JSONObject()
            .put("error", "insufficient_memory").put("message", "Not enough free memory to load Whisper."));
        assertNotNull(noRoom);
        assertSame(noRoom, noRoom.forUser(CLOSED));
    }
}
