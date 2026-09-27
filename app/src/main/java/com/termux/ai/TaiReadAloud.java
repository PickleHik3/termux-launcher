package com.termux.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The app process's side of speaking aloud: "Read aloud" on a terminal selection and the voice
 * settings' "Play sample". Speech itself runs in {@code :tai_runtime} ({@link TaiManager#speak});
 * this keeps the blocking call off the main thread, remembers whether something is being read so
 * the same action can offer to stop it, and reports a failure back on the main thread.
 *
 * <p>Stop never queues behind the speak it stops: it goes out on a thread of its own and reaches
 * the runtime's control lane, while the speak call is still waiting for its answer.
 */
public final class TaiReadAloud {
    /** Called on the main thread when reading ends; {@code error} is null for a normal end or a stop. */
    public interface Listener {
        void onFinished(@Nullable String error);
    }

    private static final long AVAILABILITY_CACHE_MS = 10_000L;
    private static final AtomicBoolean SPEAKING = new AtomicBoolean();
    /** Bumped by every speak, so an older call that ends late does not clear a newer one's flag. */
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final ExecutorService SPEAKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-read-aloud");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService STOPPER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-read-aloud-stop");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile long availabilityCheckedAt = -AVAILABILITY_CACHE_MS;
    private static volatile boolean available;

    private TaiReadAloud() {}

    /** Whether something started here is being read now. */
    public static boolean isSpeaking() {
        return SPEAKING.get();
    }

    /**
     * Whether a voice model is installed, so Read aloud is worth offering. Read from the model
     * store at most every ten seconds: the selection toolbar asks on every long press.
     */
    public static boolean isAvailable(@NonNull Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now - availabilityCheckedAt >= AVAILABILITY_CACHE_MS) {
            available = TaiTtsModels.resolveActive(new TaiModelStore(context.getApplicationContext())) != null;
            availabilityCheckedAt = now;
        }
        return available;
    }

    /** Forgets the cached answer, after a voice model was installed or deleted. */
    public static void invalidateAvailability() {
        availabilityCheckedAt = -AVAILABILITY_CACHE_MS;
    }

    /** Reads {@code text}, or stops if something is being read already: the one action a toolbar needs. */
    @MainThread
    public static void toggle(@NonNull Context context, @NonNull String text, @Nullable Listener listener) {
        if (isSpeaking()) stop(context);
        else speak(context, text, null, null, listener);
    }

    /**
     * Reads {@code text} with {@code voice} and {@code speed}, or the settings' when null.
     * Something already being read is stopped first, so a new sample or selection replaces it.
     */
    public static void speak(@NonNull Context context, @NonNull String text, @Nullable String voice,
                             @Nullable Float speed, @Nullable Listener listener) {
        Context app = context.getApplicationContext();
        if (isSpeaking()) stop(app);
        int generation = GENERATION.incrementAndGet();
        SPEAKING.set(true);
        SPEAKER.execute(() -> {
            String error = null;
            try {
                JSONObject body = new JSONObject().put("input", text);
                if (voice != null) body.put("voice", voice);
                if (speed != null) body.put("speed", (double) speed);
                JSONObject result = TaiManager.getInstance(app).speak(body.toString());
                if (result.has("error") && !result.optBoolean("ok", false)) error = messageOf(result);
            } catch (JSONException | RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                if (GENERATION.get() == generation) SPEAKING.set(false);
            }
            String finalError = error;
            if (listener != null) MAIN.post(() -> listener.onFinished(finalError));
        });
    }

    /** Stops what is being read; returns at once. */
    public static void stop(@NonNull Context context) {
        Context app = context.getApplicationContext();
        STOPPER.execute(() -> {
            try {
                TaiManager.getInstance(app).stopSpeaking();
            } catch (JSONException | RuntimeException ignored) {
                // The runtime is gone, and with it the voice.
            }
        });
    }

    /** The user-facing message of a refusal or failure, in either of the two error shapes. */
    @NonNull
    static String messageOf(@NonNull JSONObject result) {
        JSONObject nested = result.optJSONObject("error");
        String message = nested != null ? nested.optString("message", "") : result.optString("message", "");
        return message.isEmpty() ? "Reading aloud failed." : message;
    }
}
