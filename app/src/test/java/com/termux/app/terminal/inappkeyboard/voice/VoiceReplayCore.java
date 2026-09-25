package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pure orchestration behind {@code VoiceReplayRig}, kept free of JUnit and of the Python
 * subprocess so it can be driven with a fake {@link Transcriber} in a plain unit test: reads a WAV,
 * feeds it through the real {@link VoiceActivityDetector} exactly as {@code VoiceInputSession.capture}
 * does, classifies each segment the way {@code VoiceInputSession} and the activity would (a spoken
 * key, terminal-cleaned text, or dropped), and formats the event log and expectation match.
 *
 * <p>Deliberate difference from the live capture path: {@link VoiceLeadInDiscard} is not applied
 * here. It exists only to drop the 125 ms start tone the microphone hears from the phone's own
 * speaker; a replay WAV is not guaranteed to open with that tone (a session recording is dumped
 * <em>after</em> the live discard already ran, and a hand-made test clip never had one), so
 * dropping a fixed 180 ms here would just cut real audio off the front of some clips.
 */
final class VoiceReplayCore {

    private VoiceReplayCore() {
    }

    /** What one segment is sent to for a transcript; the seam a unit test fakes. */
    interface Transcriber {
        @NonNull
        TranscriptResult transcribe(@NonNull short[] pcm, boolean terminalPrompt) throws IOException;
    }

    /** One answer from the transcriber: the text and the runtime's own timing breakdown. */
    static final class TranscriptResult {
        @NonNull final String text;
        final long encodeMs;
        final long decodeMs;
        final int steps;

        TranscriptResult(@NonNull String text, long encodeMs, long decodeMs, int steps) {
            this.text = text;
            this.encodeMs = encodeMs;
            this.decodeMs = decodeMs;
            this.steps = steps;
        }
    }

    /** The subset of {@code VoiceInputSession.Config} replay needs; silence auto-stop has no equivalent. */
    static final class ReplayConfig {
        final int pauseMs;
        final int windowSeconds;
        final boolean terminalPrompt;
        final boolean bareCommandWordsAllowed;

        ReplayConfig(int pauseMs, int windowSeconds, boolean terminalPrompt, boolean bareCommandWordsAllowed) {
            this.pauseMs = pauseMs;
            this.windowSeconds = windowSeconds;
            this.terminalPrompt = terminalPrompt;
            this.bareCommandWordsAllowed = bareCommandWordsAllowed;
        }
    }

    /** One VAD segment, classified: what the activity would have done with it and how long STT took. */
    static final class SegmentEvent {
        final double startSeconds;
        final double endSeconds;
        final double voicedSeconds;
        /** {@code "text"}, {@code "key"}, {@code "dropped"} or {@code "failed"}. */
        @NonNull final String outcome;
        /** The typed text, the {@link VoiceCommand} name, the heard-but-dropped text, or a failure message. */
        @NonNull final String content;
        final long encodeMs;
        final long decodeMs;
        final int steps;

        SegmentEvent(double startSeconds, double endSeconds, double voicedSeconds, @NonNull String outcome,
                     @NonNull String content, long encodeMs, long decodeMs, int steps) {
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.voicedSeconds = voicedSeconds;
            this.outcome = outcome;
            this.content = content;
            this.encodeMs = encodeMs;
            this.decodeMs = decodeMs;
            this.steps = steps;
        }

        /** One line, e.g. {@code "  [0.00-6.93s voiced 2.88s] text \"ls\"  (encode 210ms, decode 340ms, steps 6)"}. */
        @NonNull
        String toLogLine() {
            String description;
            switch (outcome) {
                case "key":
                    description = "KEY " + prettyKeyName(content);
                    break;
                case "text":
                    description = "text \"" + content + "\"";
                    break;
                case "dropped":
                    description = content.trim().isEmpty() ? "dropped" : "dropped (heard \"" + content + "\")";
                    break;
                default:
                    description = "failed: " + content;
            }
            return String.format(Locale.ROOT,
                "  [%.2f-%.2fs voiced %.2fs] %s  (encode %dms, decode %dms, steps %d)",
                startSeconds, endSeconds, voicedSeconds, description, encodeMs, decodeMs, steps);
        }

        @NonNull
        private static String prettyKeyName(@NonNull String enumName) {
            switch (enumName) {
                case "CTRL_C": return "Ctrl+C";
                default:
                    return enumName.isEmpty() ? enumName
                        : enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
            }
        }
    }

    /** Every segment's expected event mismatched against what actually happened. */
    static final class ExpectationResult {
        final boolean passed;
        @NonNull final List<String> mismatches;

        ExpectationResult(boolean passed, @NonNull List<String> mismatches) {
            this.passed = passed;
            this.mismatches = mismatches;
        }
    }

    /** One clip's whole replay: its segment events, and the expectation match when a {@code .expect} file exists. */
    static final class ClipResult {
        @NonNull final String clipName;
        @NonNull final List<SegmentEvent> events;
        @Nullable final ExpectationResult expectation;

        ClipResult(@NonNull String clipName, @NonNull List<SegmentEvent> events, @Nullable ExpectationResult expectation) {
            this.clipName = clipName;
            this.events = events;
            this.expectation = expectation;
        }

        @NonNull
        String toLog() {
            StringBuilder out = new StringBuilder("clip ").append(clipName).append('\n');
            if (events.isEmpty()) out.append("  (no voiced segments)\n");
            for (SegmentEvent event : events) out.append(event.toLogLine()).append('\n');
            if (expectation != null) {
                out.append(expectation.passed ? "  PASS" : "  FAIL");
                for (String mismatch : expectation.mismatches) out.append('\n').append("    ").append(mismatch);
                out.append('\n');
            }
            return out.toString();
        }
    }

    /**
     * Feeds {@code pcm} through the real {@link VoiceActivityDetector} in {@code FRAME_SAMPLES}
     * reads, exactly the chunk size {@code VoiceInputSession.capture} asks {@code AudioRecord.read}
     * for; {@link VoiceActivityDetector#feed(short[], int, int)} takes the source array with an
     * offset directly, so unlike the live capture loop no scratch buffer needs copying into first.
     * Silence auto-stop has no effect here: {@code sessionSilenceMs} is always
     * {@link VoiceSilenceTimeout#UNTIL_TAP}, so {@code onSilenceTimeout} never fires and is ignored
     * if it did.
     */
    @NonNull
    static ClipResult replay(@NonNull String clipName, @NonNull short[] pcm, @NonNull ReplayConfig config,
                             @NonNull Transcriber transcriber, @Nullable List<String> expectedLines) {
        List<SegmentEvent> events = new ArrayList<>();
        int frameSamples = VoiceActivityDetector.FRAME_SAMPLES;
        long[] frameCount = {0};
        VoiceActivityDetector.Listener listener = new VoiceActivityDetector.Listener() {
            @Override
            public void onLevel(float rms, boolean voiced, float noiseFloor) {
                frameCount[0]++;
            }

            @Override
            public void onSegment(@NonNull short[] segmentPcm, int voicedFrames) {
                double endSeconds = frameCount[0] * VoiceActivityDetector.FRAME_MS / 1000.0;
                double lengthSeconds = (segmentPcm.length / (double) frameSamples)
                    * VoiceActivityDetector.FRAME_MS / 1000.0;
                double voicedSeconds = voicedFrames * VoiceActivityDetector.FRAME_MS / 1000.0;
                events.add(classify(endSeconds - lengthSeconds, endSeconds, voicedSeconds, segmentPcm, config, transcriber));
            }

            @Override
            public void onSilenceTimeout() {
                // Ignored in replay: sessionSilenceMs is always UNTIL_TAP, so this never fires.
            }
        };
        VoiceActivityDetector detector = new VoiceActivityDetector(listener, config.pauseMs, config.windowSeconds,
            VoiceSilenceTimeout.UNTIL_TAP);
        int pos = 0;
        while (pos < pcm.length) {
            int take = Math.min(frameSamples, pcm.length - pos);
            detector.feed(pcm, pos, take);
            pos += take;
        }
        detector.finish();
        ExpectationResult expectation = expectedLines == null ? null : matchExpectations(events, expectedLines);
        return new ClipResult(clipName, events, expectation);
    }

    /**
     * The classification {@code VoiceInputSession.outcomeFor}/{@code route} apply, with commands
     * always enabled (replay has no "Voice commands" setting to turn them off): {@link VoiceGain}
     * levels the segment first, exactly as the live segment does before it is sent for
     * transcription; a transcriber failure becomes a {@code "failed"} event rather than aborting
     * the clip.
     */
    @NonNull
    private static SegmentEvent classify(double startSeconds, double endSeconds, double voicedSeconds,
                                         @NonNull short[] segmentPcm, @NonNull ReplayConfig config,
                                         @NonNull Transcriber transcriber) {
        VoiceGain.apply(segmentPcm);
        TranscriptResult result;
        try {
            result = transcriber.transcribe(segmentPcm, config.terminalPrompt);
        } catch (IOException e) {
            return new SegmentEvent(startSeconds, endSeconds, voicedSeconds, "failed",
                String.valueOf(e.getMessage()), 0, 0, 0);
        }
        String text = result.text;
        VoiceCommand command = VoiceCommand.classify(text, config.bareCommandWordsAllowed);
        if (command != null) {
            return new SegmentEvent(startSeconds, endSeconds, voicedSeconds, "key", command.name(),
                result.encodeMs, result.decodeMs, result.steps);
        }
        String effective = config.terminalPrompt ? VoiceTerminalCleanup.clean(text) : text;
        if (effective.trim().isEmpty()) {
            return new SegmentEvent(startSeconds, endSeconds, voicedSeconds, "dropped", text,
                result.encodeMs, result.decodeMs, result.steps);
        }
        return new SegmentEvent(startSeconds, endSeconds, voicedSeconds, "text", effective,
            result.encodeMs, result.decodeMs, result.steps);
    }

    // ------------------------------------------------------------------ expectations

    /** {@code <clip>.expect}: one event per line ({@code "text ls"}, {@code "key ENTER"}, {@code "dropped"}); blank lines and {@code #} comments ignored. */
    @NonNull
    static List<String> parseExpectations(@NonNull File expectFile) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(expectFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                lines.add(trimmed);
            }
        }
        return lines;
    }

    /**
     * {@code events} against {@code expectedLines} in order: {@code "text <words>"} compares after
     * {@link VoiceCommand#normalize}, {@code "key <NAME>"} against the classified command's name,
     * {@code "dropped"} against a dropped or failed event.
     */
    @NonNull
    static ExpectationResult matchExpectations(@NonNull List<SegmentEvent> events, @NonNull List<String> expectedLines) {
        List<String> mismatches = new ArrayList<>();
        int count = Math.max(events.size(), expectedLines.size());
        for (int i = 0; i < count; i++) {
            if (i >= events.size()) {
                mismatches.add("expected \"" + expectedLines.get(i) + "\" but the clip had no segment " + (i + 1));
                continue;
            }
            if (i >= expectedLines.size()) {
                mismatches.add("segment " + (i + 1) + " (" + events.get(i).outcome + ") was not expected");
                continue;
            }
            String mismatch = matchOne(events.get(i), expectedLines.get(i), i + 1);
            if (mismatch != null) mismatches.add(mismatch);
        }
        return new ExpectationResult(mismatches.isEmpty(), mismatches);
    }

    @Nullable
    private static String matchOne(@NonNull SegmentEvent event, @NonNull String expected, int index) {
        String[] parts = expected.split("\\s+", 2);
        String kind = parts[0].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "text": {
                String wantText = parts.length > 1 ? parts[1] : "";
                if (!"text".equals(event.outcome)) {
                    return "segment " + index + ": expected text \"" + wantText + "\" but got " + event.outcome + " \"" + event.content + "\"";
                }
                if (!VoiceCommand.normalize(wantText).equals(VoiceCommand.normalize(event.content))) {
                    return "segment " + index + ": expected text \"" + wantText + "\" but heard \"" + event.content + "\"";
                }
                return null;
            }
            case "key": {
                String wantKey = parts.length > 1 ? parts[1].trim().toUpperCase(Locale.ROOT) : "";
                if (!"key".equals(event.outcome)) {
                    return "segment " + index + ": expected key " + wantKey + " but got " + event.outcome + " \"" + event.content + "\"";
                }
                if (!wantKey.equals(event.content)) {
                    return "segment " + index + ": expected key " + wantKey + " but got key " + event.content;
                }
                return null;
            }
            case "dropped":
                if (!"dropped".equals(event.outcome) && !"failed".equals(event.outcome)) {
                    return "segment " + index + ": expected dropped but got " + event.outcome + " \"" + event.content + "\"";
                }
                return null;
            default:
                return "segment " + index + ": unrecognised expectation \"" + expected + "\"";
        }
    }

    // ------------------------------------------------------------------ WAV reading

    /** {@code file} as 16 kHz mono PCM16 samples; refuses anything else so a wrong-format clip fails loudly, not quietly. */
    @NonNull
    static short[] readWav16kMono(@NonNull File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[12];
            raf.readFully(header);
            if (!hasTag(header, 0, "RIFF") || !hasTag(header, 8, "WAVE")) {
                throw new IOException(file + " is not a RIFF/WAVE file");
            }
            int channels = -1;
            int sampleRate = -1;
            int bitsPerSample = -1;
            long dataStart = -1;
            long dataLength = -1;
            byte[] chunkHeader = new byte[8];
            while (raf.getFilePointer() + 8 <= raf.length()) {
                raf.readFully(chunkHeader);
                String id = new String(chunkHeader, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
                long size = le32(chunkHeader, 4) & 0xFFFFFFFFL;
                if ("fmt ".equals(id)) {
                    byte[] fmt = new byte[(int) size];
                    raf.readFully(fmt);
                    channels = le16(fmt, 2);
                    sampleRate = (int) le32(fmt, 4);
                    bitsPerSample = le16(fmt, 14);
                } else if ("data".equals(id)) {
                    dataStart = raf.getFilePointer();
                    dataLength = size;
                    raf.seek(raf.getFilePointer() + size + (size & 1));
                } else {
                    raf.seek(raf.getFilePointer() + size + (size & 1));
                }
            }
            if (dataStart < 0) throw new IOException(file + " has no data chunk");
            if (channels != 1 || sampleRate != VoiceActivityDetector.SAMPLE_RATE || bitsPerSample != 16) {
                throw new IOException(file + " must be 16 kHz mono PCM16, got " + channels + " channel(s), "
                    + sampleRate + " Hz, " + bitsPerSample + "-bit");
            }
            raf.seek(dataStart);
            byte[] bytes = new byte[(int) dataLength];
            raf.readFully(bytes);
            short[] pcm = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            return pcm;
        }
    }

    private static boolean hasTag(@NonNull byte[] header, int offset, @NonNull String tag) {
        for (int i = 0; i < tag.length(); i++) {
            if (header[offset + i] != (byte) tag.charAt(i)) return false;
        }
        return true;
    }

    private static int le16(@NonNull byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static long le32(@NonNull byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL) | ((bytes[offset + 1] & 0xFFL) << 8)
            | ((bytes[offset + 2] & 0xFFL) << 16) | ((bytes[offset + 3] & 0xFFL) << 24);
    }
}
