package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link VoiceReplayCore} driven with a fake {@link VoiceReplayCore.Transcriber} and synthetic
 * PCM (no WAV file, no subprocess): the VAD really runs, only the transcript is scripted.
 */
public class VoiceReplayCoreTest {

    private static final int FRAME = VoiceActivityDetector.FRAME_SAMPLES;

    /** {@code frames} frames of a 440 Hz tone at about -12 dBFS: what the VAD hears as speech. */
    private static short[] tone(int frames) {
        short[] out = new short[frames * FRAME];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / VoiceActivityDetector.SAMPLE_RATE));
        }
        return out;
    }

    /** {@code frames} frames of faint noise, well under the absolute floor: a quiet room. */
    private static short[] quiet(int frames) {
        short[] out = new short[frames * FRAME];
        for (int i = 0; i < out.length; i++) out[i] = (short) ((i * 7919) % 17 - 8);
        return out;
    }

    private static short[] concat(short[]... parts) {
        int total = 0;
        for (short[] part : parts) total += part.length;
        short[] out = new short[total];
        int at = 0;
        for (short[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    /** Scripts one transcript per call, in order; records whether each segment was gained (peak-scaled). */
    private static final class ScriptedTranscriber implements VoiceReplayCore.Transcriber {
        private final List<String> scripted;
        private int calls;
        final List<Boolean> terminalFlags = new ArrayList<>();

        ScriptedTranscriber(String... scripted) {
            this.scripted = Arrays.asList(scripted);
        }

        @NonNull
        @Override
        public VoiceReplayCore.TranscriptResult transcribe(@NonNull short[] pcm, boolean terminalPrompt) throws IOException {
            terminalFlags.add(terminalPrompt);
            if (calls >= scripted.size()) throw new IOException("no more scripted transcripts");
            String text = scripted.get(calls++);
            return new VoiceReplayCore.TranscriptResult(text, 12, 34, 5);
        }
    }

    private static VoiceReplayCore.ReplayConfig config(boolean terminal, boolean bare) {
        return new VoiceReplayCore.ReplayConfig(600, 10, terminal, bare);
    }

    @Test
    public void oneSpokenPhraseComesBackAsTextWithATimeRange() {
        short[] pcm = concat(quiet(30), tone(20), quiet(40));
        ScriptedTranscriber transcriber = new ScriptedTranscriber("ls");
        VoiceReplayCore.ClipResult result = VoiceReplayCore.replay("clip.wav", pcm, config(true, false), transcriber, null);

        assertEquals(1, result.events.size());
        VoiceReplayCore.SegmentEvent event = result.events.get(0);
        assertEquals("text", event.outcome);
        assertEquals("ls", event.content);
        assertTrue(event.startSeconds < event.endSeconds);
        assertTrue("segment carries a plausible voiced duration", event.voicedSeconds > 0.5 && event.voicedSeconds < 0.7);
        assertTrue("the terminal flag reached the transcriber", transcriber.terminalFlags.get(0));
    }

    @Test
    public void aCommandWordBecomesAKeyEvent() {
        short[] pcm = concat(quiet(30), tone(20), quiet(40));
        VoiceReplayCore.ClipResult result = VoiceReplayCore.replay("clip.wav", pcm,
            config(true, false), new ScriptedTranscriber("enter key"), null);

        assertEquals(1, result.events.size());
        assertEquals("key", result.events.get(0).outcome);
        assertEquals("ENTER", result.events.get(0).content);
    }

    @Test
    public void nonSpeechIsDroppedByTerminalCleanupWhenTerminalPromptIsOn() {
        short[] pcm = concat(quiet(30), tone(20), quiet(40));
        VoiceReplayCore.ClipResult result = VoiceReplayCore.replay("clip.wav", pcm,
            config(true, false), new ScriptedTranscriber("[Music]"), null);

        assertEquals(1, result.events.size());
        assertEquals("dropped", result.events.get(0).outcome);
    }

    @Test
    public void twoPhrasesSeparatedByAPauseAreTwoOrderedSegments() {
        short[] pcm = concat(quiet(30), tone(20), quiet(30), tone(20), quiet(40));
        VoiceReplayCore.ClipResult result = VoiceReplayCore.replay("clip.wav", pcm,
            config(true, false), new ScriptedTranscriber("ls", "enter key"), null);

        assertEquals(2, result.events.size());
        assertEquals("text", result.events.get(0).outcome);
        assertEquals("ls", result.events.get(0).content);
        assertEquals("key", result.events.get(1).outcome);
        assertTrue("segments are in spoken order", result.events.get(0).endSeconds <= result.events.get(1).startSeconds);
    }

    @Test
    public void aTranscriberFailureIsReportedNotThrown() {
        short[] pcm = concat(quiet(30), tone(20), quiet(40));
        VoiceReplayCore.Transcriber failing = (pcm1, terminalPrompt) -> {
            throw new IOException("server not running");
        };
        VoiceReplayCore.ClipResult result = VoiceReplayCore.replay("clip.wav", pcm, config(true, false), failing, null);

        assertEquals(1, result.events.size());
        assertEquals("failed", result.events.get(0).outcome);
        assertEquals("server not running", result.events.get(0).content);
    }

    @Test
    public void expectationsMatchInOrderAndReportEachMismatch() {
        short[] pcm = concat(quiet(30), tone(20), quiet(30), tone(20), quiet(40));
        VoiceReplayCore.ClipResult passing = VoiceReplayCore.replay("clip.wav", pcm, config(true, false),
            new ScriptedTranscriber("ls", "enter key"), Arrays.asList("text ls", "key ENTER"));
        assertTrue(passing.expectation.passed);
        assertTrue(passing.expectation.mismatches.isEmpty());

        VoiceReplayCore.ClipResult failing = VoiceReplayCore.replay("clip.wav", pcm, config(true, false),
            new ScriptedTranscriber("ls", "enter key"), Arrays.asList("text cd", "key TAB"));
        assertFalse(failing.expectation.passed);
        assertEquals(2, failing.expectation.mismatches.size());
    }

    @Test
    public void textExpectationsNormalizeBeforeComparing() {
        short[] pcm = concat(quiet(30), tone(20), quiet(40));
        VoiceReplayCore.ClipResult result = VoiceReplayCore.replay("clip.wav", pcm, config(true, false),
            new ScriptedTranscriber("LS."), Arrays.asList("text ls"));
        assertTrue(result.expectation.passed);
    }
}
