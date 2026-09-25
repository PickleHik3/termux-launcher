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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One press of the voice key with the on-device engine: the microphone is read on a capture
 * thread, {@link VoiceActivityDetector} cuts the stream into segments, each segment is written
 * under {@code cacheDir/tai-ipc} and sent through {@link TaiManager#transcribe} on a single
 * {@code voice-stt} thread — in order, so results come back in order — and every result reaches
 * the {@link Host} on the main thread. {@code sttWarm} goes out as the microphone opens so the
 * model loads while the user speaks.
 *
 * <p>The session ends on 2.5 s of silence, a second tap, the keyboard going down, the activity
 * pausing, or the first failure; {@link #stop} releases the microphone at once and lets segments
 * already captured finish, {@link #cancel} drops them too. The activity never blocks on it: the
 * only main-thread work is the callbacks.
 */
public final class VoiceInputSession {

    private static final String LOG_TAG = "VoiceInputSession";

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

        public Config(@NonNull String modelId, @Nullable String language, boolean terminalPrompt,
                      int pauseMs, int windowSeconds) {
            this.modelId = modelId;
            this.language = language;
            this.terminalPrompt = terminalPrompt;
            this.pauseMs = pauseMs;
            this.windowSeconds = windowSeconds;
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
        this.appContext = context.getApplicationContext();
        this.config = config;
        this.host = host;
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
        // The model loads while the first words are spoken; a refusal here ends the session
        // before any segment is sent, which is the fast path to the fallback.
        sttExecutor.execute(this::warm);
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

    /** Releases the microphone and drops every result still in flight. */
    public void cancel(@NonNull EndReason reason) {
        cancelled = true;
        mainHandler.post(() -> discardResults = true);
        stop(reason);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    // ------------------------------------------------------------------ capture thread

    private void capture(@NonNull AudioRecord recorder) {
        VoiceActivityDetector detector = new VoiceActivityDetector(new VoiceActivityDetector.Listener() {
            @Override
            public void onLevel(float rms, boolean voiced, float noiseFloor) {
                mainHandler.post(() -> {
                    if (!ended) host.onLevel(rms, voiced, noiseFloor);
                });
            }

            @Override
            public void onSegment(@NonNull short[] pcm) {
                submitSegment(nextSequence++, pcm);
            }

            @Override
            public void onSilenceTimeout() {
                stop(EndReason.SILENCE);
            }
        }, config.pauseMs, config.windowSeconds);
        short[] buffer = new short[VoiceActivityDetector.FRAME_SAMPLES];
        try {
            while (!stopRequested.get()) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    if (read < 0) Logger.logWarn(LOG_TAG, "AudioRecord.read: " + read);
                    break;
                }
                detector.feed(buffer, read);
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
                maybeEnd();
            });
        }
    }

    private void submitSegment(int sequence, @NonNull short[] pcm) {
        submitted.incrementAndGet();
        sttExecutor.execute(() -> transcribe(sequence, pcm));
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

    private void transcribe(int sequence, @NonNull short[] pcm) {
        if (failed.get() || cancelled) {
            deliver(sequence, "");
            return;
        }
        File audio = null;
        try {
            audio = writePcm(pcm);
            JSONObject request = new JSONObject();
            request.put("file", audio.getAbsolutePath());
            if (!config.modelId.isEmpty()) request.put("model", config.modelId);
            if (config.language != null) request.put("language", config.language);
            if (config.terminalPrompt) request.put("prompt_mode", "terminal");
            JSONObject result = TaiManager.getInstance(appContext).transcribe(request.toString());
            Failure failure = Failure.of(result);
            if (failure != null) {
                fail(failure);
                deliver(sequence, "");
                return;
            }
            deliver(sequence, result.optString("text", ""));
        } catch (IOException | JSONException | RuntimeException e) {
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
        host.onEnded(endReason == null ? EndReason.FAILED : endReason);
    }

    /** An error answer from the runtime, in either of its shapes: flat {@code {error, message}} or OpenAI's nested one. */
    private static final class Failure {
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
