package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Which vocabulary line a transcription request puts behind {@code <|startofprev|>}: its own {@code prompt}, or none. */
public class TaiTranscribeRequestTest {

    @Test
    public void anExplicitPromptIsTrimmed() throws Exception {
        assertEquals("tlstore dawn pong", TaiManager.biasPromptFor(new JSONObject().put("prompt", "  tlstore dawn pong ")));
    }

    @Test
    public void plainDictationHasNoPrompt() throws Exception {
        assertNull(TaiManager.biasPromptFor(new JSONObject()));
        assertNull(TaiManager.biasPromptFor(new JSONObject().put("prompt", "   ")));
    }

    @Test
    public void theRetiredTerminalModeIsPlainDictationNow() throws Exception {
        // prompt_mode: "terminal" used to add a built-in shell vocabulary; voice input is dictation
        // only, so the field is ignored and only an explicit prompt biases the decoder.
        assertNull(TaiManager.biasPromptFor(new JSONObject().put("prompt_mode", "terminal")));
        assertNull(TaiManager.biasPromptFor(new JSONObject().put("promptMode", "Terminal")));
        assertEquals("git status", TaiManager.biasPromptFor(new JSONObject().put("prompt", "git status").put("prompt_mode", "terminal")));
    }
}
