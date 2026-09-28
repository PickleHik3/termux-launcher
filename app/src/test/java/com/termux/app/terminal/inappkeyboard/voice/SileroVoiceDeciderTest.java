package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/**
 * Silero's voiced decision without LiteRT: a fake {@link SileroVoiceDecider.ProbabilitySource}
 * stands in for the model, so the hysteresis, the 30 ms frame / 32 ms chunk alignment, the context
 * carried between chunks and the fall back to energy are all checked on the JVM.
 */
public class SileroVoiceDeciderTest {

    private static final int FRAME = VoiceActivityDetector.FRAME_SAMPLES;
    private static final int CHUNK = SileroVoiceDecider.CHUNK_SAMPLES;
    private static final int CONTEXT = SileroVoiceDecider.CONTEXT_SAMPLES;

    /** Probability per chunk index; records every window it is given and can throw at one chunk. */
    private static final class FakeSource implements SileroVoiceDecider.ProbabilitySource {
        final IntToDoubleFunction probabilities;
        final List<float[]> windows = new ArrayList<>();
        int throwAtChunk = -1;
        boolean closed;

        FakeSource(IntToDoubleFunction probabilities) {
            this.probabilities = probabilities;
        }

        @Override
        public float probability(@NonNull float[] window) {
            int chunk = windows.size();
            if (chunk == throwAtChunk) throw new IllegalStateException("interpreter died");
            windows.add(window.clone());
            return (float) probabilities.applyAsDouble(chunk);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final List<String> reports = new ArrayList<>();
    private final SileroVoiceDecider.FailureReporter reporter = (message, cause) -> reports.add(message);

    private static short[] frame(int index) {
        // A ramp keyed to the absolute sample number, so a window's contents say where they came from.
        short[] out = new short[FRAME];
        for (int i = 0; i < FRAME; i++) out[i] = sample(index * FRAME + i);
        return out;
    }

    private static short sample(int n) {
        return (short) (n % 20_000 - 10_000);
    }

    @Test
    public void onsetOpensAndTheLowerHoldKeepsASegmentOpen() {
        assertFalse(SileroVoiceDecider.voiced(0.5f, false));
        assertTrue(SileroVoiceDecider.voiced(0.51f, false));
        assertFalse(SileroVoiceDecider.voiced(0.4f, false));
        assertTrue(SileroVoiceDecider.voiced(0.4f, true));
        assertFalse(SileroVoiceDecider.voiced(0.35f, true));
        assertTrue(SileroVoiceDecider.voiced(0.36f, true));
        assertFalse(SileroVoiceDecider.voiced(0f, true));
    }

    @Test
    public void eachFrameReadsTheChunkThatHasFinishedByItsEnd() {
        FakeSource source = new FakeSource(chunk -> (chunk + 1) / 100.0);
        SileroVoiceDecider decider = new SileroVoiceDecider(source, reporter);
        for (int i = 0; i < 64; i++) {
            decider.decide(frame(i), false);
            // vads.py SileroDecider: chunk = (i + 1) * 480 // 512 - 1, probability 0 before any.
            int finished = (i + 1) * FRAME / CHUNK;
            assertEquals("chunks run by frame " + i, finished, source.windows.size());
            float expected = finished == 0 ? 0f : finished / 100f;
            assertEquals("frame " + i, expected, decider.lastProbability(), 1e-6f);
        }
    }

    @Test
    public void everyWindowIsTheLastChunksTailThenTheNewChunk() {
        FakeSource source = new FakeSource(chunk -> 0.0);
        SileroVoiceDecider decider = new SileroVoiceDecider(source, reporter);
        for (int i = 0; i < 20; i++) decider.decide(frame(i), false);
        assertTrue(source.windows.size() >= 3);
        for (int k = 0; k < source.windows.size(); k++) {
            float[] window = source.windows.get(k);
            assertEquals(SileroVoiceDecider.WINDOW_SAMPLES, window.length);
            for (int j = 0; j < CONTEXT; j++) {
                // The stream starts on zeros, as Silero's OnnxWrapper does.
                float expected = k == 0 ? 0f : sample(k * CHUNK - CONTEXT + j) / 32768f;
                assertEquals("chunk " + k + " context " + j, expected, window[j], 0f);
            }
            for (int j = 0; j < CHUNK; j++) {
                assertEquals("chunk " + k + " sample " + j, sample(k * CHUNK + j) / 32768f, window[CONTEXT + j], 0f);
            }
        }
    }

    @Test
    public void theModelsCopyIsLiftedByTheGainAndHeldInsideFullScale() {
        FakeSource source = new FakeSource(chunk -> 0.0);
        SileroVoiceDecider decider = new SileroVoiceDecider(source, reporter);
        for (int i = 0; i < 4; i++) decider.decide(frame(i), false, 4f);
        float[] window = source.windows.get(0);
        for (int j = 0; j < CHUNK; j++) {
            float expected = Math.max(-1f, Math.min(1f, sample(j) * 4f / 32768f));
            assertEquals("sample " + j, expected, window[CONTEXT + j], 1e-6f);
        }
        // The ramp reaches ±10 000, which four times over is past full scale: held at it.
        assertEquals(-1f, window[CONTEXT], 0f);
    }

    @Test
    public void aModelThatThrowsFailsTheDeciderAndReportsIt() {
        FakeSource source = new FakeSource(chunk -> 0.9);
        source.throwAtChunk = 2;
        SileroVoiceDecider decider = new SileroVoiceDecider(source, reporter);
        assertFalse(decider.decide(frame(0), false));
        assertTrue(decider.decide(frame(1), false));
        assertTrue(decider.decide(frame(2), false));
        // Frame 3 ends at sample 1920, completing chunk 2 (samples 1024-1536), which throws.
        assertFalse(decider.decide(frame(3), true));
        assertTrue(decider.failed());
        assertFalse(decider.decide(frame(4), true));
        assertEquals(1, reports.size());
        assertEquals(2, source.windows.size());
    }

    @Test
    public void aMissingModelFallsBackAndIsReportedOnce() {
        SileroVoiceDecider.FailureReporter once = SileroVoiceDecider.once(reporter);
        SileroVoiceDecider.Opener missing = () -> {
            throw new FileNotFoundException("vad/silero_vad_v5.tflite");
        };
        assertNull(SileroVoiceDecider.open(missing, once));
        assertNull(SileroVoiceDecider.open(missing, once));
        assertNull(SileroVoiceDecider.open(() -> {
            throw new UnsatisfiedLinkError("libtensorflowlite_jni.so");
        }, once));
        assertEquals(1, reports.size());
    }

    @Test
    public void anOpenedModelIsClosedThroughTheDecider() {
        FakeSource source = new FakeSource(chunk -> 0.0);
        SileroVoiceDecider decider = SileroVoiceDecider.open(() -> source, reporter);
        assertNotNull(decider);
        decider.close();
        assertTrue(source.closed);
        assertTrue(reports.isEmpty());
    }

    // ------------------------------------------------------------------ inside the detector

    private final List<Boolean> voicedFlags = new ArrayList<>();
    private final List<short[]> segments = new ArrayList<>();

    private VoiceActivityDetector detector(SileroVoiceDecider speech) {
        return detector(speech, VoiceMicSensitivity.NORMAL);
    }

    private VoiceActivityDetector detector(SileroVoiceDecider speech, VoiceMicSensitivity sensitivity) {
        return new VoiceActivityDetector(new VoiceActivityDetector.Listener() {
            @Override
            public void onLevel(float rms, boolean voiced, float noiseFloor) {
                voicedFlags.add(voiced);
            }

            @Override
            public void onSegment(@NonNull short[] pcm, int voicedFrames) {
                segments.add(pcm);
            }

            @Override
            public void onSilenceTimeout() {
            }
        }, 600, 10, VoiceSilenceTimeout.UNTIL_TAP, speech, sensitivity);
    }

    /** {@code frames} frames of a 440 Hz tone at about −12 dBFS: loud enough for the energy decision. */
    private static short[] tone(int frames) {
        short[] out = new short[frames * FRAME];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / VoiceActivityDetector.SAMPLE_RATE));
        }
        return out;
    }

    /** Faint noise at about −70 dBFS, which the energy decision never calls speech. */
    private static short[] quiet(int frames) {
        short[] out = new short[frames * FRAME];
        for (int i = 0; i < out.length; i++) out[i] = (short) ((i * 7919) % 17 - 8);
        return out;
    }

    @Test
    public void sileroVoicesFromTheFirstFinishedChunkWithNoWarmUp() {
        VoiceActivityDetector vad = detector(new SileroVoiceDecider(new FakeSource(chunk -> 0.9), reporter));
        short[] pcm = quiet(20);
        vad.feed(pcm, pcm.length);
        assertTrue(vad.decidesBySilero());
        // Frame 0 ends at sample 480, before the first chunk does; frame 1 reads it. The energy
        // decision would not voice anything before its 8-frame floor warm-up, and not this at all.
        assertFalse(voicedFlags.get(0));
        for (int i = 1; i < 20; i++) assertTrue("frame " + i, voicedFlags.get(i));
        vad.finish();
        assertEquals(1, segments.size());
    }

    @Test
    public void aLoudNonSpeechSoundIsNotASegmentForSilero() {
        VoiceActivityDetector vad = detector(new SileroVoiceDecider(new FakeSource(chunk -> 0.1), reporter));
        short[] pcm = tone(60);
        vad.feed(pcm, pcm.length);
        vad.finish();
        assertFalse(voicedFlags.contains(true));
        assertTrue(segments.isEmpty());
    }

    @Test
    public void theHoldThresholdKeepsSpeechOpenButCannotOpenIt() {
        // 0.8 for 10 chunks (opens), then 0.4: above the hold, below the onset.
        VoiceActivityDetector open = detector(new SileroVoiceDecider(
            new FakeSource(chunk -> chunk < 10 ? 0.8 : 0.4), reporter));
        short[] pcm = quiet(60);
        open.feed(pcm, pcm.length);
        for (int i = 1; i < 60; i++) assertTrue("frame " + i, voicedFlags.get(i));

        voicedFlags.clear();
        VoiceActivityDetector idle = detector(new SileroVoiceDecider(new FakeSource(chunk -> 0.4), reporter));
        idle.feed(pcm, pcm.length);
        assertFalse(voicedFlags.contains(true));
    }

    @Test
    public void aModelFailureMidStreamHandsTheSessionToTheEnergyDecision() {
        FakeSource source = new FakeSource(chunk -> 0.0);
        source.throwAtChunk = 5;
        VoiceActivityDetector vad = detector(new SileroVoiceDecider(source, reporter));
        short[] pcm = tone(80);
        vad.feed(pcm, pcm.length);
        short[] tail = quiet(40);
        vad.feed(tail, tail.length);
        assertFalse(vad.decidesBySilero());
        assertEquals(1, reports.size());
        // Silero said 0 until chunk 5 (samples 2560-3072) threw at frame 6; energy, whose floor
        // has been kept all along, voices the tone from then on and the pause closes the segment.
        for (int i = 0; i < 6; i++) assertFalse("frame " + i, voicedFlags.get(i));
        assertTrue(voicedFlags.subList(6, 80).contains(true));
        assertEquals(1, segments.size());
    }

    @Test
    public void highSensitivityNeedsMoreVoicedTimeBeforeAPhraseIsSent() {
        // Twelve chunks of speech (6 144 samples) voice 12 or 13 frames: over Normal's 300 ms
        // (10 frames), under High's 420 ms (14).
        short[] pcm = quiet(60);
        VoiceActivityDetector normal = detector(new SileroVoiceDecider(
            new FakeSource(chunk -> chunk >= 2 && chunk < 14 ? 0.9 : 0.0), reporter));
        normal.feed(pcm, pcm.length);
        normal.finish();
        int voiced = 0;
        for (boolean flag : voicedFlags) if (flag) voiced++;
        assertTrue("voiced " + voiced, voiced >= 10 && voiced < 14);
        assertEquals(1, segments.size());

        segments.clear();
        VoiceActivityDetector high = detector(new SileroVoiceDecider(
            new FakeSource(chunk -> chunk >= 2 && chunk < 14 ? 0.9 : 0.0), reporter), VoiceMicSensitivity.HIGH);
        high.feed(pcm, pcm.length);
        high.finish();
        assertTrue(segments.isEmpty());
    }

    @Test
    public void theDetectorLiftsSilerosCopyOfAQuietRoom() {
        // A room at about −75 dBFS is far under both targets, so every window after the first
        // frame is lifted by the most the setting allows: 4x (12 dB) for High.
        FakeSource source = new FakeSource(chunk -> 0.0);
        VoiceActivityDetector vad = detector(new SileroVoiceDecider(source, reporter), VoiceMicSensitivity.HIGH);
        short[] pcm = quiet(20);
        vad.feed(pcm, pcm.length);
        float[] window = source.windows.get(3);
        float gain = VoiceMicSensitivity.HIGH.sileroGain(0.0001f);
        assertEquals(12f, (float) (20 * Math.log10(gain)), 0.01f);
        for (int j = 0; j < CHUNK; j++) {
            assertEquals("sample " + j, pcm[3 * CHUNK + j] * gain / 32768f, window[CONTEXT + j], 1e-6f);
        }
    }

    @Test
    public void withoutSileroTheDetectorKeepsTheEnergyDecision() {
        VoiceActivityDetector vad = detector(null);
        assertFalse(vad.decidesBySilero());
        short[] pcm = tone(40);
        vad.feed(pcm, pcm.length);
        // The energy floor needs its warm-up before anything is voiced.
        assertFalse(voicedFlags.get(0));
        assertTrue(voicedFlags.contains(true));
    }
}
