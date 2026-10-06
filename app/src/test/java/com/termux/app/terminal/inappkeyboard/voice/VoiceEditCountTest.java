package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VoiceEditCountTest {

    private static VoiceWordDiff.Op op(VoiceWordDiff.Kind kind, String word) {
        return new VoiceWordDiff.Op(kind, word);
    }

    @Test
    public void emptyIsNoEdits() {
        assertEquals(0, VoiceEditCount.count(new ArrayList<>()));
    }

    @Test
    public void allSameIsNoEdits() {
        List<VoiceWordDiff.Op> ops = Arrays.asList(
            op(VoiceWordDiff.Kind.SAME, "so"), op(VoiceWordDiff.Kind.SAME, "it"), op(VoiceWordDiff.Kind.SAME, "goes"));
        assertEquals(0, VoiceEditCount.count(ops));
    }

    @Test
    public void aSingleRemovedWordIsOneEdit() {
        List<VoiceWordDiff.Op> ops = Arrays.asList(
            op(VoiceWordDiff.Kind.SAME, "so"), op(VoiceWordDiff.Kind.REMOVED, "uh"), op(VoiceWordDiff.Kind.SAME, "it"));
        assertEquals(1, VoiceEditCount.count(ops));
    }

    @Test
    public void addedNextToRemovedIsOneEdit() {
        List<VoiceWordDiff.Op> ops = Arrays.asList(
            op(VoiceWordDiff.Kind.SAME, "what"), op(VoiceWordDiff.Kind.ADDED, "do,"),
            op(VoiceWordDiff.Kind.REMOVED, "with"), op(VoiceWordDiff.Kind.SAME, "it"));
        assertEquals(1, VoiceEditCount.count(ops));
    }

    @Test
    public void runsApartAreSeparateEdits() {
        List<VoiceWordDiff.Op> ops = Arrays.asList(
            op(VoiceWordDiff.Kind.REMOVED, "uh"), op(VoiceWordDiff.Kind.SAME, "so"),
            op(VoiceWordDiff.Kind.CHANGED, "It"), op(VoiceWordDiff.Kind.SAME, "goes"));
        assertEquals(2, VoiceEditCount.count(ops));
    }
}
