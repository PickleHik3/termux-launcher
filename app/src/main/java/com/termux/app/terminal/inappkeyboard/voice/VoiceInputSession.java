package com.termux.app.terminal.inappkeyboard.voice;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiManager;
import com.termux.shared.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One press of the voice key with the on-device engine: the microphone is read on a capture
 * thread, {@link VoiceActivityDetector} cuts the stream into segments, each segment is written
 * under {@code cacheDir/tai-ipc} and sent through {@link TaiManager#transcribe} on a single
 * {@code voice-stt} thread — in order, so results come back in order — and every result reaches
 * the {@link Host} on the main thread. {@code sttWarm} goes out as the microphone opens so the
 * model loads while the user speaks. {@link VoiceFeedback} marks the open, the end and a failure
 * with a blip and a haptic; the first {@link VoiceLeadInDiscard#START_TONE_MS} of capture are
 * dropped so the start blip is never transcribed.
 *
 * <p>The session ends on the configured silence timeout ({@link VoiceSilenceTimeout}, or never for
 * "Until tap"), a second tap, the keyboard going down, the activity
 * pausing, or the first failure; {@link #stop} releases the microphone at once and lets segments
 * already captured finish, {@link #cancel} drops them too. The activity never blocks on it: the
 * only main-thread work is the callbacks.
 */
public final class VoiceInputSession {

    private static final String LOG_TAG = "VoiceInputSession";
    /** Per-segment STT deadline: {@code base + multiplier × the segment's own audio length}. */
    private static final long SEGMENT_TIMEOUT_BASE_MS = 5_000L;
    private static final long SEGMENT_TIMEOUT_AUDIO_MULTIPLIER = 3L;

    /** Why the microphone was released. */
    public enum EndReason { SILENCE, USER, HIDDEN, PAUSED, DESTROYED, FAILED }

    /** Everything the activity decides per session, read once at the start. */
    public static final class Config {
        /** The installed speech model id; empty lets the runtime pick the settings' one. */
        @NonNull public final String modelId;
        /** The forced language for a multilingual graph, or {@code null} for an {@code .en} one. */
        @Nullable public final String language;
        /** Whether the shell-vocabulary bias prompt is sent (typing goes to a terminal). */
        public final boolean terminalPrompt;
        public final int pauseMs;
        public final int windowSeconds;
        /** {@link VoiceSilenceTimeout#UNTIL_TAP} disables the timeout ("Until tap"). */
        public final int silenceTimeoutMs;
        /**
         * These three mirror the keyboard settings the activity applies to the same transcript
         * (commands, "Bare command words", terminal cleanup); they are carried here only so the
         * per-phrase log's "outcome" (command / text / dropped) matches what actually happens to it.
         */
        public final boolean commandsEnabled;
        public final boolean bareCommandWordsAllowed;
        public final boolean terminalCleanupEnabled;
        /** The keyboard's "Voice sounds" setting: start/stop/error blips ({@link VoiceFeedback}). */
        public final boolean soundsEnabled;
        /** The keyboard's key-haptics setting, which the voice cues follow. */
        public final boolean hapticsEnabled;

        public Config(@NonNull String modelId, @Nullable String language, boolean terminalPrompt,
                      int pauseMs, int windowSeconds, int silenceTimeoutMs, boolean commandsEnabled,
                      boolean bareCommandWordsAllowed, boolean terminalCleanupEnabled,
                      boolean soundsEnabled, boolean hapticsEnabled) {
            this.modelId = modelId;
            this.language = language;
            this.terminalPrompt = terminalPrompt;
            this.pauseMs = pauseMs;
            this.windowSeconds = windowSeconds;
            this.silenceTimeoutMs = silenceTimeoutMs;
            this.commandsEnabled = commandsEnabled;
            this.bareCommandWordsAllowed = bareCommandWordsAllowed;
            this.terminalCleanupEnabled = terminalCleanupEnabled;
            this.soundsEnabled = soundsEnabled;
            this.hapticsEnabled = hapticsEnabled;
        }
    }

    /** Called on the main thread. */
    public interface Host {
        /** The microphone is open and being read. */
        void onListening();

        /**
         * A level sample, roughly every 30 ms while listening, with the VAD's noise floor at that
         * moment (the level meter measures from it, not an absolute dBFS scale).
         */
        void onLevel(float rms, boolean voiced, float noiseFloor);

        /** A non-empty transcript, in the order its segment was spoken. */
        void onTranscript(@NonNull String text);

        /**
         * The microphone has closed but at least one captured segment is still transcribing —
         * shown as a "Transcribing…" pill, since {@link #onListening}'s "Listening…" no longer fits.
         */
        void onDraining();

        /**
         * The runtime refused or failed ({@code stt_model_not_configured}, {@code insufficient_memory},
         * a transcription error, or the microphone could not be opened). The session has already
         * stopped; {@code anyTranscriptDelivered} says whether text got through before it did.
         */
        void onFailure(@NonNull String code, @NonNull String message, boolean anyTranscriptDelivered);

        /** The microphone is released and no more transcripts will come. */
        void onEnded(@NonNull EndReason reason);
    }

    private final Context appContext;
    private final Config config;
    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sttExecutor = Executors.newSingleThreadExecutor(
        runnable -> new Thread(runnable, "voice-stt"));
    private final VoiceResultSequencer<String> sequencer = new VoiceResultSequencer<>();
    private final VoiceFeedback feedback;
    /** Null when the "Polish dictation" setting is off; otherwise every text segment goes through it. */
    @Nullable private final VoiceTextPolisher polisher;
    /**
     * Rewrites run here, not on {@code voice-stt}, so a slow rewrite never delays the next
     * segment's transcription; the sequencer still types results in spoken order. Null without a
     * polisher.
     */
    @Nullable private final ExecutorService polishExecutor;
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicBoolean failed = new AtomicBoolean();
    /** Segments handed to the STT thread, counted on the capture thread before the microphone is released. */
    private final AtomicInteger submitted = new AtomicInteger();

    /** Null once the capture thread has released it; the main thread reads it to know the session is over. */
    @Nullable private volatile AudioRecord record;
    @Nullable private Thread captureThread;
    /** Results still queued after {@link #cancel} are not worth an IPC round trip. */
    private volatile boolean cancelled;
    /** Capture-thread state: the next segment's number. */
    private int nextSequence;
    /** Main-thread state. */
    private int delivered;
    private boolean anyTranscript;
    private boolean ended;
    private boolean discardResults;
    @Nullable private EndReason endReason;

    public VoiceInputSession(@NonNull Context context, @NonNull Config config, @NonNull Host host) {
        this(context, config, host, null);
    }

    /** @param polisher rewrites text segments before they are typed, or null to type them as heard. */
    public VoiceInputSession(@NonNull Context context, @NonNull Config config, @NonNull Host host,
                             @Nullable VoiceTextPolisher polisher) {
        this.appContext = context.getApplicationContext();
        this.config = config;
        this.host = host;
        this.feedback = new VoiceFeedback(appContext, config.soundsEnabled, config.hapticsEnabled);
        this.polisher = polisher;
        this.polishExecutor = polisher == null ? null
            : Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "voice-polish"));
    }

    /**
     * Opens the microphone and starts listening; false when it could not be opened (the host is
     * not told separately). Needs {@code RECORD_AUDIO}, which the caller checks.
     */
    public boolean start() {
        int minBytes = AudioRecord.getMinBufferSize(VoiceActivityDetector.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minBytes, VoiceActivityDetector.FRAME_SAMPLES * 2 * 8);
        AudioRecord recorder;
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                VoiceActivityDetector.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
        } catch (IllegalArgumentException | SecurityException e) {
            Logger.logError(LOG_TAG, "AudioRecord refused: " + e.getMessage());
            return false;
        }
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            Logger.logError(LOG_TAG, "AudioRecord did not initialise");
            return false;
        }
        try {
            recorder.startRecording();
        } catch (IllegalStateException e) {
            recorder.release();
            Logger.logError(LOG_TAG, "AudioRecord.startRecording failed: " + e.getMessage());
            return false;
        }
        record = recorder;
        // The cue goes out the moment the microphone is live; the capture thread drops the
        // lead-in the tone occupies so the blip never becomes the first "phrase".
        feedback.onStart();
        // The model loads while the first words are spoken; a refusal here ends the session
        // before any segment is sent, which is the fast path to the fallback.
        sttExecutor.execute(this::warm);
        // The chat model's load (~12 s cold) overlaps the first phrase the same way; it queues
        // ahead of the first rewrite on the polish thread, so that rewrite waits for it rather
        // than racing it.
        if (polishExecutor != null) polishExecutor.execute(this::warmPolisher);
        Thread thread = new Thread(() -> capture(recorder), "voice-capture");
        captureThread = thread;
        thread.start();
        mainHandler.post(host::onListening);
        return true;
    }

    /** Releases the microphone; segments already captured still transcribe and deliver. */
    public void stop(@NonNull EndReason reason) {
        if (!stopRequested.compareAndSet(false, true)) return;
        mainHandler.post(() -> {
            if (endReason == null) endReason = reason;
            maybeEnd();
        });
        AudioRecord recorder = record;
        if (recorder != null) {
            // read() returns once the recorder stops, so the capture thread exits within a frame
            // and releases it; stopping from here means the microphone closes even if that thread
            // is slow to come around.
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    /**
     * Releases the microphone and drops every result still in flight — the undelivered segments
     * finish transcribing (or time out) on their own but are never typed. Ends the session at once
     * even when {@link #stop} has already been called and is only waiting on those segments, which
     * is exactly the tap {@link #isStopRequested} is for: a hung or slow drain no longer leaves the
     * pill and the pressed key up until the last segment comes back.
     */
    public void cancel(@NonNull EndReason reason) {
        cancelled = true;
        mainHandler.post(() -> {
            discardResults = true;
            if (endReason == null) endReason = reason;
            maybeEnd();
        });
        stop(reason);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    // ------------------------------------------------------------------ capture thread

    private void capture(@NonNull AudioRecord recorder) {
        VoiceActivityDetector detector = new VoiceActivityDetector(new VoiceActivityDetector.Listener() {
            // One debug line per second of what the detector saw, for tuning it on a device.
            private int statFrames, statVoiced;
            private float statPeak, statSum;

            @Override
            public void onLevel(float rms, boolean voiced, float noiseFloor) {
                statFrames++;
                if (voiced) statVoiced++;
                statPeak = Math.max(statPeak, rms);
                statSum += rms;
                if (statFrames == 1000 / VoiceActivityDetector.FRAME_MS) {
                    Logger.logDebug(LOG_TAG, String.format(Locale.ROOT,
                        "level: peak=%.1f mean=%.1f floor=%.1f dBFS voiced=%d/%d",
                        dbfs(statPeak), dbfs(statSum / statFrames), dbfs(noiseFloor), statVoiced, statFrames));
                    statFrames = 0;
                    statVoiced = 0;
                    statPeak = 0f;
                    statSum = 0f;
                }
                mainHandler.post(() -> {
                    if (!ended) host.onLevel(rms, voiced, noiseFloor);
                });
            }

            @Override
            public void onSegment(@NonNull short[] pcm, int voicedFrames) {
                submitSegment(nextSequence++, pcm, voicedFrames);
            }

            @Override
            public void onSilenceTimeout() {
                stop(EndReason.SILENCE);
            }
        }, config.pauseMs, config.windowSeconds, config.silenceTimeoutMs);
        // In front of the detector, not inside it: frames dropped here never reach the pre-roll.
        VoiceLeadInDiscard leadIn = new VoiceLeadInDiscard(
            feedback.playsTones() ? VoiceLeadInDiscard.START_TONE_MS : 0, VoiceActivityDetector.SAMPLE_RATE);
        short[] buffer = new short[VoiceActivityDetector.FRAME_SAMPLES];
        try {
            while (!stopRequested.get()) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    if (read < 0) Logger.logWarn(LOG_TAG, "AudioRecord.read: " + read);
                    break;
                }
                int drop = leadIn.take(read);
                if (drop < read) detector.feed(buffer, drop, read - drop);
            }
            // A tap in the middle of a phrase still sends what was said.
            if (!failed.get()) detector.finish();
        } finally {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
            }
            recorder.release();
            record = null;
            stopRequested.set(true);
            mainHandler.post(() -> {
                if (endReason == null) endReason = EndReason.FAILED;
                // The mic has just closed; a segment already sent for transcription still has to
                // come back (or time out) before the session actually ends.
                if (!ended && !discardResults && submitted.get() > delivered) host.onDraining();
                maybeEnd();
            });
        }
    }

    private void submitSegment(int sequence, @NonNull short[] pcm, int voicedFrames) {
        submitted.incrementAndGet();
        sttExecutor.execute(() -> transcribe(sequence, pcm, voicedFrames));
    }

    // ------------------------------------------------------------------ STT thread

    private void warm() {
        if (failed.get() || stopRequested.get()) return;
        try {
            JSONObject request = new JSONObject();
            if (!config.modelId.isEmpty()) request.put("model", config.modelId);
            JSONObject result = TaiManager.getInstance(appContext).sttWarm(request.toString());
            Failure failure = Failure.of(result);
            if (failure != null) fail(failure);
        } catch (JSONException | RuntimeException e) {
            fail(new Failure("stt_warm_failed", String.valueOf(e.getMessage())));
        }
    }

    private void transcribe(int sequence, @NonNull short[] pcm, int voicedFrames) {
        long segmentMs = pcm.length * 1000L / VoiceActivityDetector.SAMPLE_RATE;
        long voicedMs = voicedFrames * (long) VoiceActivityDetector.FRAME_MS;
        if (failed.get() || cancelled) {
            logPhrase(segmentMs, voicedMs, 0, null, 0, "dropped");
            deliver(sequence, "");
            return;
        }
        long start = System.nanoTime();
        File audio = null;
        try {
            // Levelled first: see VoiceGain. The segment is ours alone, so scaling in place is safe.
            VoiceGain.apply(pcm);
            audio = writePcm(pcm);
            JSONObject request = new JSONObject();
            request.put("file", audio.getAbsolutePath());
            if (!config.modelId.isEmpty()) request.put("model", config.modelId);
            if (config.language != null) request.put("language", config.language);
            if (config.terminalPrompt) request.put("prompt_mode", "terminal");
            // A deadline per segment, not the IPC client's flat 120 s: a hung runtime would
            // otherwise leave the pill and the pressed key up for minutes with nothing to show for
            // it. base.en does a short phrase in well under a second, so this has plenty of room.
            long timeoutMs = SEGMENT_TIMEOUT_BASE_MS + SEGMENT_TIMEOUT_AUDIO_MULTIPLIER * segmentMs;
            JSONObject result = TaiManager.getInstance(appContext).transcribe(request.toString(), timeoutMs);
            long transcribeMs = (System.nanoTime() - start) / 1_000_000L;
            Failure failure = Failure.of(result);
            if (failure != null) {
                logPhrase(segmentMs, voicedMs, transcribeMs, result, 0, "failed");
                fail(failure);
                deliver(sequence, "");
                return;
            }
            String text = result.optString("text", "");
            logPhrase(segmentMs, voicedMs, transcribeMs, result, text.length(), outcomeFor(text));
            route(sequence, text);
        } catch (IOException | JSONException | RuntimeException e) {
            long transcribeMs = (System.nanoTime() - start) / 1_000_000L;
            logPhrase(segmentMs, voicedMs, transcribeMs, null, 0, "failed");
            fail(new Failure("stt_failed", String.valueOf(e.getMessage())));
            deliver(sequence, "");
        } finally {
            // The runtime process deletes the file once read; this covers every other exit.
            if (audio != null) {
                //noinspection ResultOfMethodCallIgnored
                audio.delete();
            }
        }
    }

    /**
     * A transcript on its way to the host: straight through, or by way of the polisher when there
     * is one and {@link VoicePolishRules} lets this segment through (never a spoken key, never a
     * short command, never once cancelled). Either way {@link #deliver} runs exactly once for the
     * segment, so the "ends when everything is delivered" accounting is untouched.
     */
    private void route(int sequence, @NonNull String text) {
        if (polisher == null || polishExecutor == null) {
            deliver(sequence, text);
            return;
        }
        String skip = cancelled ? "cancelled" : VoicePolishRules.skipReason(text, config.commandsEnabled,
            config.bareCommandWordsAllowed, config.terminalPrompt && config.terminalCleanupEnabled);
        if (skip != null) {
            logPolish(0, text.length(), text.length(), "skipped:" + skip);
            deliver(sequence, text);
            return;
        }
        try {
            polishExecutor.execute(() -> polish(sequence, text));
        } catch (RejectedExecutionException e) {
            // A cancel ended the session between the check above and here; the result is
            // discarded anyway, but the accounting still wants its delivery.
            logPolish(0, text.length(), text.length(), "skipped:cancelled");
            deliver(sequence, text);
        }
    }

    // ------------------------------------------------------------------ polish thread

    private void warmPolisher() {
        if (polisher == null || cancelled || failed.get()) return;
        try {
            polisher.warm();
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "polish warm failed: " + e.getMessage());
        }
    }

    private void polish(int sequence, @NonNull String text) {
        if (polisher == null || cancelled) {
            logPolish(0, text.length(), text.length(), "skipped:cancelled");
            deliver(sequence, text);
            return;
        }
        long start = System.nanoTime();
        VoiceTextPolisher.Result result;
        try {
            result = polisher.polish(text, VoicePolishRules.timeoutMs(text));
        } catch (RuntimeException e) {
            result = VoiceTextPolisher.Result.fallback(text, "exception");
        }
        long polishMs = (System.nanoTime() - start) / 1_000_000L;
        logPolish(polishMs, text.length(), result.text.length(), result.outcome);
        deliver(sequence, result.text);
    }

    /** One line per phrase that reached the polish step: timing, lengths and outcome — never the text. */
    private static void logPolish(long polishMs, int inLength, int outLength, @NonNull String outcome) {
        Logger.logInfo(LOG_TAG, "polish: polishMs=" + polishMs + " inLength=" + inLength
            + " outLength=" + outLength + " outcome=" + outcome);
    }

    /**
     * The same classification the activity is about to apply to {@code text} — a spoken key, a
     * terminal-dropped non-speech segment, or ordinary text — purely so the log's "outcome" field
     * matches what actually happens to it.
     */
    @NonNull
    private String outcomeFor(@NonNull String text) {
        if (config.commandsEnabled && VoiceCommand.classify(text, config.bareCommandWordsAllowed) != null) {
            return "command";
        }
        String effective = text;
        if (config.terminalPrompt && config.terminalCleanupEnabled) {
            effective = VoiceTerminalCleanup.clean(text);
        }
        return effective.trim().isEmpty() ? "dropped" : "text";
    }

    /**
     * One line per phrase: durations and the runtime's own timing breakdown when it sent one, the
     * transcript's length and what became of it — never the transcript itself.
     */
    private static void logPhrase(long segmentMs, long voicedMs, long transcribeMs,
                                  @Nullable JSONObject result, int textLength, @NonNull String outcome) {
        StringBuilder message = new StringBuilder("phrase: segmentMs=").append(segmentMs)
            .append(" voicedMs=").append(voicedMs)
            .append(" transcribeMs=").append(transcribeMs);
        JSONObject timings = result == null ? null : result.optJSONObject("timings");
        if (timings != null) {
            message.append(" melMs=").append(timings.optLong("melMs"))
                .append(" encodeMs=").append(timings.optLong("encodeMs"))
                .append(" decodeMs=").append(timings.optLong("decodeMs"))
                .append(" decodeSteps=").append(timings.optLong("decodeSteps"));
        }
        message.append(" textLength=").append(textLength).append(" outcome=").append(outcome);
        Logger.logInfo(LOG_TAG, message.toString());
    }

    /** Little-endian PCM16 at 16 kHz mono under {@code cacheDir/tai-ipc}, the form the runtime reads raw. */
    @NonNull
    private File writePcm(@NonNull short[] pcm) throws IOException {
        File dir = new File(appContext.getCacheDir(), TaiManager.STT_IPC_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, "stt-" + UUID.randomUUID() + ".pcm");
        ByteBuffer bytes = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asShortBuffer().put(pcm);
        try (FileOutputStream stream = new FileOutputStream(out)) {
            stream.write(bytes.array());
        }
        return out;
    }

    private void fail(@NonNull Failure failure) {
        if (!failed.compareAndSet(false, true)) return;
        Logger.logWarn(LOG_TAG, "voice input failed: " + failure.code + ": " + failure.message);
        mainHandler.post(() -> {
            if (endReason == null) endReason = EndReason.FAILED;
            if (!discardResults) host.onFailure(failure.code, failure.message, anyTranscript);
        });
        stop(EndReason.FAILED);
    }

    // ------------------------------------------------------------------ main thread

    private void deliver(int sequence, @NonNull String text) {
        mainHandler.post(() -> {
            delivered++;
            List<String> ready = sequencer.offer(sequence, text);
            if (!discardResults && !ended) {
                for (String item : ready) {
                    if (item.trim().isEmpty()) continue;
                    anyTranscript = true;
                    host.onTranscript(item);
                }
            }
            maybeEnd();
        });
    }

    /** Ends once the microphone is released and every captured segment has come back. */
    private void maybeEnd() {
        if (ended || !stopRequested.get() || record != null) return;
        if (!discardResults && delivered < submitted.get()) return;
        ended = true;
        sttExecutor.shutdown();
        if (polishExecutor != null) polishExecutor.shutdown();
        EndReason reason = endReason == null ? EndReason.FAILED : endReason;
        Logger.logInfo(LOG_TAG, "session ended: " + reason + ", segments=" + submitted.get()
            + " delivered=" + delivered);
        // The activity going away is not something to chime about; every other end is.
        if (reason == EndReason.FAILED) feedback.onError();
        else if (reason != EndReason.DESTROYED) feedback.onStop();
        feedback.release();
        host.onEnded(reason);
    }

    private static float dbfs(float rms) {
        return rms <= 0f ? -100f : (float) (20.0 * Math.log10(rms));
    }

    /**
     * An error answer from the runtime, in either of its shapes: flat {@code {error, message}} or
     * OpenAI's nested one. Package-private so {@link LocalTaiVoiceTextPolisher} reads chat answers
     * the same way.
     */
    static final class Failure {
        final String code;
        final String message;

        Failure(@NonNull String code, @NonNull String message) {
            this.code = code;
            this.message = message;
        }

        @Nullable
        static Failure of(@Nullable JSONObject result) {
            if (result == null) return new Failure("stt_no_answer", "No answer from the speech runtime.");
            JSONObject nested = result.optJSONObject("error");
            if (nested != null) {
                return new Failure(nested.optString("code", "tai_error"), nested.optString("message", ""));
            }
            String flat = result.optString("error", "");
            if (!flat.isEmpty()) return new Failure(flat, result.optString("message", ""));
            if (result.optInt("_statusCode", 200) >= 400) {
                return new Failure("http_" + result.optInt("_statusCode"), result.optString("message", ""));
            }
            return null;
        }
    }
}
