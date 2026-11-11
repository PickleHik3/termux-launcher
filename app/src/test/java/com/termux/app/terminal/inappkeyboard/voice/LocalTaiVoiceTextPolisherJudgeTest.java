package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocalTaiVoiceTextPolisherJudgeTest {

    private static final String RAW = "so I was thinking we could maybe refactor the session and rerun the replay rig";

    @Test
    public void theGuardFlagsARefusalShapedAnswer() {
        assertTrue(LocalTaiVoiceTextPolisher.rejectsAnswer("refactor the session", "",
            "Sure! Here is how to refactor the session: first split it into two parts."));
    }

    @Test
    public void theGuardLeavesACleanedUpAnswer() {
        assertFalse(LocalTaiVoiceTextPolisher.rejectsAnswer(RAW, "",
            "So I was thinking we could maybe refactor the session and rerun the replay rig."));
    }

    @Test
    public void theGuardsVerdictEndsItAsHeard() {
        VoiceTextPolisher.Result r = LocalTaiVoiceTextPolisher.judge(RAW, "", null, "refusal_or_answer",
            "Sure! Here is how to refactor the session.");
        assertEquals(RAW, r.text);
        assertEquals("fallback:refusal_or_answer", r.outcome);
    }

    @Test
    public void anEmptyReplyIsUnusableOutput() {
        VoiceTextPolisher.Result r = LocalTaiVoiceTextPolisher.judge(RAW, "", null, null, "");
        assertEquals(RAW, r.text);
        assertEquals("fallback:unusable_output", r.outcome);
    }

    @Test
    public void aGoodAnswerIsPolished() {
        VoiceTextPolisher.Result r = LocalTaiVoiceTextPolisher.judge(RAW, "", null, null,
            "So I was thinking we could maybe refactor the session and rerun the replay rig.");
        assertTrue(r.isPolished());
    }
}
