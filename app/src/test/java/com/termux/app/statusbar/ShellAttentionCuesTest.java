package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShellAttentionCuesTest {

    @Test
    public void agentChoicePromptsAreQuestions() {
        assertTrue(ShellAttentionCues.looksLikeQuestion(
            "Allow codex to run `rm -rf build`?\n\n❯ 1. Yes\n  2. Yes, and don't ask again\n  3. No\n"));
        assertTrue(ShellAttentionCues.looksLikeQuestion(
            "Do you want to proceed?\n › 1. Yes\n   2. No, and tell Claude what to do differently\n"));
        assertTrue(ShellAttentionCues.looksLikeQuestion("Overwrite existing file? [y/N] "));
        assertTrue(ShellAttentionCues.looksLikeQuestion("Continue (yes/no)?"));
        assertTrue(ShellAttentionCues.looksLikeQuestion("...\nPress Enter to continue"));
    }

    @Test
    public void finishedOutputIsNotAQuestion() {
        assertFalse(ShellAttentionCues.looksLikeQuestion(
            "Compiled 42 files.\nDone in 3.2s.\n\n› "));
        assertFalse(ShellAttentionCues.looksLikeQuestion(
            "1. Read the file\n2. Fixed the bug\n3. Ran the tests — all passing\n"));
        assertFalse(ShellAttentionCues.looksLikeQuestion("What would you like me to do next?\n"));
        assertFalse(ShellAttentionCues.looksLikeQuestion(""));
        assertFalse(ShellAttentionCues.looksLikeQuestion(null));
    }

    @Test
    public void onlyTheBottomOfTheScreenCounts() {
        StringBuilder screen = new StringBuilder("❯ 1. Yes\n");
        for (int i = 0; i < 20; i++) screen.append("log line ").append(i).append('\n');
        assertFalse(ShellAttentionCues.looksLikeQuestion(screen.toString()));
    }
}
