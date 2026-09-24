package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Which vocabulary line a transcription request puts behind {@code <|startofprev|>}. */
public class TaiTranscribeRequestTest {

    @Test
    public void anExplicitPromptWinsOverTheTerminalMode() throws Exception {
        assertEquals("tlstore dawn pong", TaiManager.biasPromptFor(new JSONObject()
            .put("prompt", "  tlstore dawn pong ").put("prompt_mode", "terminal")));
    }

    @Test
    public void terminalModeGivesTheMeasuredShellVocabulary() throws Exception {
        assertEquals(WhisperDecoder.TERMINAL_VOCABULARY, TaiManager.biasPromptFor(new JSONObject().put("prompt_mode", "terminal")));
        assertEquals(WhisperDecoder.TERMINAL_VOCABULARY, TaiManager.biasPromptFor(new JSONObject().put("promptMode", "Terminal")));
    }

    @Test
    public void plainDictationHasNoPrompt() throws Exception {
        assertNull(TaiManager.biasPromptFor(new JSONObject()));
        assertNull(TaiManager.biasPromptFor(new JSONObject().put("prompt", "   ")));
        assertNull(TaiManager.biasPromptFor(new JSONObject().put("prompt_mode", "dictation")));
    }
}
