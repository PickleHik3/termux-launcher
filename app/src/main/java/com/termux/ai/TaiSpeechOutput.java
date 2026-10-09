package com.termux.ai;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speech output inside {@code :tai_runtime}: one utterance at a time, either played on the phone
 * ({@link #speak}) or handed back sentence by sentence as PCM16 files ({@link #synthesizeToFiles})
 * for the API to send. Both run on the service's TTS lane, so they queue behind each other and
 * never behind a chat generation; {@link #stop} runs on the control lane and reaches whichever is
 * speaking now.
 *
 * <p>Streaming is producer/consumer: the TTS runtime synthesises a sentence and hands it over,
 * then starts the next while the player (or the API) consumes the first. Time to first audio is
 * the model load (once) plus one sentence of G2P and three graphs.
 *
 * <p>{@link #pause} and {@link #resume} also run on the control lane and reach the phone's player
 * only ({@link #speak}): the speak call stays open while paused, its wait for the end leaving the
 * paused time out ({@link TaiTtsPlayer#awaitDone}). {@link #state} tells Read aloud when the first
 * sound has reached the speaker, so its card can stop showing that it is preparing.
 */
final class TaiSpeechOutput {
    /** Where the PCM files for the API go; the app process deletes each once it has sent it. */
    static final String FILE_PREFIX = "tts-";
    private static final long STALE_FILE_MS = 10L * 60L * 1000L;

    /** What is speaking now; {@link #stop} cancels it. */
    private static final class Session implements TtsRuntime.Cancellation {
        final AtomicBoolean cancelled = new AtomicBoolean();
        /** Played on the phone ({@link #speak}) rather than written to files: only these pause. */
        final boolean plays;
        @Nullable volatile TaiTtsPlayer player;
        /** A pause asked for; applied to the player as soon as there is one. */
        volatile boolean paused;

        Session(boolean plays) {
            this.plays = plays;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    @Nullable private static volatile Session current;

    private TaiSpeechOutput() {}

    /** Receives each sentence's PCM16 file; false to stop (the client went away). */
    interface FileSink {
        boolean onFile(@NonNull File pcm16, int samples, int sampleRate, int index);
    }

    /**
     * Speaks on the phone and returns when the last sentence has been heard, or when stopped.
     * The answer is the runtime's plus {@code played} and {@code firstSoundMs} (request to the first
     * sample reaching the speaker).
     */
    @NonNull
    static JSONObject speak(@NonNull Context context, @NonNull MultiBackendTaiRuntime router, @NonNull TaiModelSpec spec,
                            @NonNull TaiSpeechRequest request) throws JSONException {
        long started = SystemClock.elapsedRealtime();
        Session session = new Session(true);
        current = session;
        TaiTtsPlayer player = new TaiTtsPlayer(context, router.ttsSampleRate());
        try {
            if (!player.start()) {
                return error(409, "tts_audio_busy", "The phone's audio is busy (a call or another app has it); try again in a moment.");
            }
            session.player = player;
            // A pause that came before the player existed; pause() reads the player after setting
            // the flag, so between the two of them one applies it.
            if (session.paused) player.pause();
            JSONObject result = router.synthesizeSpeech(spec, request.text, request.voice, request.speed,
                (samples, count, index) -> player.enqueue(samples, count) && !session.isCancelled(), session);
            boolean failed = !result.optBoolean("ok", false);
            if (failed || session.isCancelled()) {
                player.stop();
            } else {
                player.finish();
                long audioMs = (long) (result.optDouble("audioSeconds", 0.0) * 1000.0);
                player.awaitDone(audioMs + 5_000L);
            }
            if (failed) return result;
            long firstSound = player.firstSoundAtMs();
            result.put("played", !session.isCancelled() && !player.isStopped());
            result.put("stopped", session.isCancelled() || player.isStopped());
            result.put("firstSoundMs", firstSound < 0 ? -1L : firstSound - started);
            return result;
        } finally {
            player.stop();
            if (current == session) current = null;
        }
    }

    /**
     * Synthesises without playing, writing each sentence as a PCM16 file under
     * {@code cacheDir/tai-ipc} and passing it to {@code sink} as soon as it is written.
     */
    @NonNull
    static JSONObject synthesizeToFiles(@NonNull Context context, @NonNull MultiBackendTaiRuntime router,
                                        @NonNull TaiModelSpec spec, @NonNull TaiSpeechRequest request,
                                        @NonNull FileSink sink) throws JSONException {
        File dir = new File(context.getCacheDir(), TaiManager.STT_IPC_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return error(500, "tts_output_failed", "Could not create " + dir);
        }
        deleteStaleFiles(dir);
        Session session = new Session(false);
        current = session;
        String batch = UUID.randomUUID().toString();
        int sampleRate = router.ttsSampleRate();
        try {
            return router.synthesizeSpeech(spec, request.text, request.voice, request.speed, (samples, count, index) -> {
                File out = new File(dir, FILE_PREFIX + batch + "-" + index + ".pcm");
                byte[] pcm = new byte[count * 2];
                TaiWav.floatToPcm16(samples, 0, count, pcm, 0);
                try (FileOutputStream stream = new FileOutputStream(out)) {
                    stream.write(pcm);
                } catch (IOException e) {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    return false;
                }
                boolean more = sink.onFile(out, count, sampleRate, index);
                return more && !session.isCancelled();
            }, session);
        } finally {
            if (current == session) current = null;
        }
    }

    /** Stops whatever is speaking: the player goes quiet now and synthesis ends at its next check. */
    @NonNull
    static JSONObject stop(@Nullable MultiBackendTaiRuntime router) throws JSONException {
        Session session = current;
        boolean wasSpeaking = session != null && !session.isCancelled();
        if (session != null) {
            session.cancelled.set(true);
            TaiTtsPlayer player = session.player;
            if (player != null) player.stop();
        }
        if (router != null) router.interruptSpeech();
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("stopped", wasSpeaking);
        return response;
    }

    static boolean isSpeaking() {
        Session session = current;
        return session != null && !session.isCancelled();
    }

    /**
     * Pauses the phone's speech mid-word; answers {@code {ok, paused}}, {@code paused} false when
     * nothing was being played (between sentences, or only files being written).
     */
    @NonNull
    static JSONObject pause() throws JSONException {
        Session session = current;
        boolean applied = session != null && session.plays && !session.isCancelled();
        if (applied) {
            session.paused = true;
            TaiTtsPlayer player = session.player;
            if (player != null) player.pause();
        }
        return new JSONObject().put("ok", true).put("paused", applied);
    }

    /** Carries the paused speech on; answers {@code {ok, resumed}}. */
    @NonNull
    static JSONObject resume() throws JSONException {
        Session session = current;
        boolean applied = session != null && session.plays && session.paused;
        if (session != null) {
            session.paused = false;
            TaiTtsPlayer player = session.player;
            if (player != null) player.resume();
        }
        return new JSONObject().put("ok", true).put("resumed", applied);
    }

    /**
     * What the phone is doing with speech: {@code speaking} (an utterance is under way),
     * {@code sounding} (its first sample has reached the speaker) and {@code paused}.
     */
    @NonNull
    static JSONObject state() throws JSONException {
        Session session = current;
        boolean speaking = session != null && !session.isCancelled();
        TaiTtsPlayer player = session == null ? null : session.player;
        return new JSONObject()
            .put("ok", true)
            .put("speaking", speaking)
            .put("sounding", speaking && player != null && player.firstSoundAtMs() >= 0L)
            .put("paused", speaking && session.paused);
    }

    /** Files a client never collected (it went away mid-stream) are cleared before the next batch. */
    private static void deleteStaleFiles(@NonNull File dir) {
        File[] files = dir.listFiles((parent, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(".pcm"));
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - STALE_FILE_MS;
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            if (file.lastModified() < cutoff) file.delete();
        }
    }

    @NonNull
    private static JSONObject error(int status, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", status >= 500 ? "server_error" : "invalid_request_error");
        error.put("code", code);
        JSONObject response = new JSONObject();
        response.put("ok", false);
        response.put("error", error);
        response.put("_statusCode", status);
        return response;
    }
}
