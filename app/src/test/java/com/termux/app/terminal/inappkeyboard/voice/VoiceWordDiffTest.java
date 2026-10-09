package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The word diff the panel marks a cleanup with. */
public class VoiceWordDiffTest {

    private static VoiceWordDiff.Op op(VoiceWordDiff.Kind kind, String word) {
        return new VoiceWordDiff.Op(kind, word);
    }

    @Test
    public void identicalTextIsAllSame() {
        List<VoiceWordDiff.Op> ops = VoiceWordDiff.diff("run the tests", "run the tests");
        assertEquals(Arrays.asList(
            op(VoiceWordDiff.Kind.SAME, "run"),
            op(VoiceWordDiff.Kind.SAME, "the"),
            op(VoiceWordDiff.Kind.SAME, "tests")), ops);
    }

    @Test
    public void caseAndPunctuationAreAChangeNotARemoval() {
        List<VoiceWordDiff.Op> ops = VoiceWordDiff.diff("please summarise the readme", "Please summarise the README.");
        assertEquals(Arrays.asList(
            op(VoiceWordDiff.Kind.CHANGED, "Please"),
            op(VoiceWordDiff.Kind.SAME, "summarise"),
            op(VoiceWordDiff.Kind.SAME, "the"),
            op(VoiceWordDiff.Kind.CHANGED, "README.")), ops);
    }

    @Test
    public void fillersShowAsRemovedWhereTheyStood() {
        List<VoiceWordDiff.Op> ops = VoiceWordDiff.diff("uh push to github and uh let the workflow run",
            "Push to GitHub and let the workflow run.");
        assertEquals(Arrays.asList(
            op(VoiceWordDiff.Kind.REMOVED, "uh"),
            op(VoiceWordDiff.Kind.CHANGED, "Push"),
            op(VoiceWordDiff.Kind.SAME, "to"),
            op(VoiceWordDiff.Kind.CHANGED, "GitHub"),
            op(VoiceWordDiff.Kind.SAME, "and"),
            op(VoiceWordDiff.Kind.REMOVED, "uh"),
            op(VoiceWordDiff.Kind.SAME, "let"),
            op(VoiceWordDiff.Kind.SAME, "the"),
            op(VoiceWordDiff.Kind.SAME, "workflow"),
            op(VoiceWordDiff.Kind.CHANGED, "run.")), ops);
    }

    @Test
    public void aReplacedWordIsRemovedThenAdded() {
        List<VoiceWordDiff.Op> ops = VoiceWordDiff.diff("the dogs are misplaced", "the docks are misplaced");
        assertEquals(Arrays.asList(
            op(VoiceWordDiff.Kind.SAME, "the"),
            op(VoiceWordDiff.Kind.REMOVED, "dogs"),
            op(VoiceWordDiff.Kind.ADDED, "docks"),
            op(VoiceWordDiff.Kind.SAME, "are"),
            op(VoiceWordDiff.Kind.SAME, "misplaced")), ops);
    }

    @Test
    public void emptySidesAreAllAddedOrAllRemoved() {
        assertEquals(Arrays.asList(op(VoiceWordDiff.Kind.ADDED, "Hi.")), VoiceWordDiff.diff("", "Hi."));
        assertEquals(Arrays.asList(op(VoiceWordDiff.Kind.REMOVED, "um")), VoiceWordDiff.diff("um", " "));
    }

    @Test
    public void anOversizedDiffIsSkippedAndShowsTheCleanedText() {
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i <= VoiceWordDiff.MAX_WORDS; i++) raw.append("word ");
        List<VoiceWordDiff.Op> ops = VoiceWordDiff.diff(raw.toString(), "Short.");
        assertEquals(1, ops.size());
        assertTrue(ops.get(0).kind == VoiceWordDiff.Kind.SAME);
    }
}
