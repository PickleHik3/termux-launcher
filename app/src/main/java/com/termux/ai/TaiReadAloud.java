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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The app process's side of speaking aloud: "Read aloud" on a terminal selection and the voice
 * settings' "Play sample". Speech itself runs in {@code :tai_runtime} ({@link TaiManager#speak});
 * this keeps the blocking calls off the main thread, remembers whether something is being read so
 * the same action can offer to stop it, and reports progress and a failure back on the main thread.
 *
 * <p>The text goes out a sentence at a time ({@link TaiTtsChunker#spans}), one speak call each,
 * as dawn and {@code tai speak} do, so the runtime's contract stays as it is and the reading card
 * can mark the sentence being heard ({@link Listener#onSentence}). The voice is read from the
 * settings at the start of every sentence, so one picked on the card is heard from the next.
 * {@link #isSpeaking} holds from the first sentence to the end of the last, pauses included.
 *
 * <p>Stop never queues behind the speak it stops: it goes out on a thread of its own and reaches
 * the runtime's control lane, while the speak call is still waiting for its answer. Pause and
 * resume are the same kind of call, made by a watcher thread that follows the reading: a pause
 * holds the sentence under way in the runtime mid-word (the speak call stays open, its deadline
 * leaving the pause out) and keeps the next sentence from going out. A pause pressed while a
 * sentence is still on its way to the runtime, before anything plays, is sent again until it
 * holds. The watcher also asks the runtime when the first sound has reached the speaker
 * ({@link Listener#onSounding}), which is when the card stops showing that it is preparing.
 */
public final class TaiReadAloud {
    /** Called on the main thread. Only {@link #onFinished} is required; the rest is for the reading card. */
    public interface Listener {
        /** Reading has ended; {@code error} is null for a normal end or a stop. */
        void onFinished(@Nullable String error);

        /**
         * Sentence {@code index} of {@code count} is going out now: {@code [start, end)} of the text
         * that was passed to {@link #speak}.
         */
        default void onSentence(int index, int count, int start, int end) {}

        /** The first sound has reached the speaker: the model and the first sentence are ready. */
        default void onSounding() {}

        /** Every sentence was heard to its end; comes just before {@code onFinished(null)}, never after a stop. */
        default void onCompleted() {}
    }

    /** How often the watcher asks again while it waits on the runtime: the first sound, a pause that has not held yet. */
    private static final long WATCH_MS = 150L;
    /** How long a new reading waits for the stop of the one before to reach the runtime. */
    private static final long STOP_WAIT_MS = 10_000L;

    /** One reading, from {@link #speak} to its last sentence; stop and pause act on the current one. */
    private static final class Reading {
        final Object lock = new Object();
        final AtomicBoolean sounding = new AtomicBoolean();
        volatile boolean stopped;
        volatile boolean ended;
        /** A sentence's speak call is waiting on the runtime. */
        volatile boolean inFlight;
        /** Guarded by {@link #lock}. */
        private boolean paused;
        /** Bumped under {@link #lock} on every change, so a waiter never misses one. */
        private int version;

        boolean isPaused() {
            synchronized (lock) {
                return paused;
            }
        }

        /** False when it was so already, or the reading is over. */
        boolean setPaused(boolean pause) {
            synchronized (lock) {
                if (stopped || ended || paused == pause) return false;
                paused = pause;
                changed();
                return true;
            }
        }

        void setInFlight(boolean flight) {
            synchronized (lock) {
                inFlight = flight;
                changed();
            }
        }

        void stop() {
            synchronized (lock) {
                stopped = true;
                changed();
            }
        }

        void end() {
            synchronized (lock) {
                ended = true;
                changed();
            }
        }

        int version() {
            synchronized (lock) {
                return version;
            }
        }

        /** Waits while paused; false once stopped. */
        boolean awaitPlaying() throws InterruptedException {
            synchronized (lock) {
                while (paused && !stopped) lock.wait();
                return !stopped;
            }
        }

        /** Waits until something changes after {@code seen}, at most {@code timeoutMs} (0: no limit). */
        void awaitChange(int seen, long timeoutMs) throws InterruptedException {
            synchronized (lock) {
                if (version == seen && !ended && !stopped) lock.wait(timeoutMs);
            }
        }

        private void changed() {
            version++;
            lock.notifyAll();
        }
    }

    @Nullable private static volatile Reading current;
    /** The last stop sent; a new reading waits for it so it cannot land on the new first sentence. */
    @Nullable private static volatile Future<?> lastStop;

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
    /** Sends pause and resume and asks for the first sound; one per reading, ending with it. */
    private static final ExecutorService WATCHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-read-aloud-watch");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Re-reads the model store for {@link #isAvailable}; one at a time, never on the main thread. */
    private static final ExecutorService CHECKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-read-aloud-check");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean CHECKING = new AtomicBoolean();
    /** Bumped by {@link #invalidateAvailability}, so a check that began before it is not kept. */
    private static final AtomicInteger AVAILABILITY_GENERATION = new AtomicInteger();
    private static volatile long availabilityCheckedAt = -AVAILABILITY_CACHE_MS;
    private static volatile boolean availabilityKnown;
    private static volatile boolean available;

    private TaiReadAloud() {}

    /** Whether something started here is being read now. */
    public static boolean isSpeaking() {
        return SPEAKING.get();
    }

    /**
     * Whether a voice model is installed, so Read aloud is worth offering. The selection toolbar
     * asks on every long press, so this answers from memory: the first answer in a process, and
     * the first after {@link #invalidateAvailability}, is worked out here; from then on an answer
     * older than ten seconds is returned as it is while a background check refreshes it for the
     * next press.
     */
    public static boolean isAvailable(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (!availabilityKnown) {
            // Nothing to answer from yet; a guess would hide Read aloud on the first long press,
            // or keep offering it after its model was deleted.
            checkAvailability(app);
            return available;
        }
        if (SystemClock.elapsedRealtime() - availabilityCheckedAt >= AVAILABILITY_CACHE_MS)
            refreshAvailabilityAsync(app);
        return available;
    }

    /**
     * Works out the first answer on a background thread, so the first long press in a process
     * does not wait on the model store's preferences and file checks (about a second on a phone).
     */
    public static void prewarmAvailability(@NonNull Context context) {
        if (!availabilityKnown) refreshAvailabilityAsync(context.getApplicationContext());
    }

    /** Forgets the cached answer, after a voice model was installed or deleted. */
    public static void invalidateAvailability() {
        AVAILABILITY_GENERATION.incrementAndGet();
        availabilityKnown = false;
        availabilityCheckedAt = -AVAILABILITY_CACHE_MS;
    }

    private static void refreshAvailabilityAsync(@NonNull Context app) {
        if (!CHECKING.compareAndSet(false, true)) return;
        try {
            CHECKER.execute(() -> {
                try {
                    checkAvailability(app);
                } finally {
                    CHECKING.set(false);
                }
            });
        } catch (RuntimeException e) {
            CHECKING.set(false);
        }
    }

    private static synchronized void checkAvailability(@NonNull Context app) {
        int generation = AVAILABILITY_GENERATION.get();
        long startedAt = SystemClock.elapsedRealtime();
        boolean answer = TaiTtsModels.resolveActive(app, new TaiModelStore(app)) != null;
        if (generation != AVAILABILITY_GENERATION.get()) return;
        available = answer;
        availabilityKnown = true;
        availabilityCheckedAt = startedAt;
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
        Future<?> pendingStop = lastStop;
        int generation = GENERATION.incrementAndGet();
        Reading reading = new Reading();
        current = reading;
        SPEAKING.set(true);
        SPEAKER.execute(() -> read(app, reading, generation, text, voice, speed, listener, pendingStop));
        WATCHER.execute(() -> watch(app, reading, listener));
    }

    /** The sentence loop, on {@link #SPEAKER}: one speak call per sentence until the last, a stop or a failure. */
    private static void read(@NonNull Context app, @NonNull Reading reading, int generation, @NonNull String text,
                             @Nullable String voice, @Nullable Float speed, @Nullable Listener listener,
                             @Nullable Future<?> pendingStop) {
        String error = null;
        boolean completed = false;
        try {
            awaitStop(pendingStop);
            List<TaiTtsChunker.Span> sentences = TaiTtsChunker.spans(text);
            TaiManager manager = TaiManager.getInstance(app);
            TaiSettings settings = voice == null ? new TaiSettings(app) : null;
            int count = sentences.size();
            // Nothing to say (punctuation only) ends as a reading that was heard through.
            completed = count == 0;
            for (int i = 0; i < count; i++) {
                if (!reading.awaitPlaying()) break;
                TaiTtsChunker.Span sentence = sentences.get(i);
                if (listener != null) {
                    int index = i;
                    MAIN.post(() -> listener.onSentence(index, count, sentence.start, sentence.end));
                }
                JSONObject body = new JSONObject().put("input", sentence.text);
                // Read now rather than once: a voice picked on the card is heard from this sentence.
                body.put("voice", voice != null ? voice : settings.getTtsVoice());
                if (speed != null) body.put("speed", (double) speed);
                JSONObject result;
                reading.setInFlight(true);
                try {
                    result = manager.speak(body.toString());
                } finally {
                    reading.setInFlight(false);
                }
                if (result.has("error") && !result.optBoolean("ok", false)) {
                    error = messageOf(result);
                    break;
                }
                markSounding(reading, listener);
                // Stopped here, or anywhere else (the toolbar, tai speak --stop, dawn): the reading ends.
                if (reading.stopped || result.optBoolean("stopped", false)) break;
                if (i == count - 1) completed = true;
            }
        } catch (JSONException | RuntimeException e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            reading.end();
            if (current == reading) current = null;
            if (GENERATION.get() == generation) SPEAKING.set(false);
        }
        if (listener == null) return;
        String finalError = error;
        boolean heardThrough = completed && error == null && !reading.stopped;
        MAIN.post(() -> {
            if (heardThrough) listener.onCompleted();
            listener.onFinished(finalError);
        });
    }

    /**
     * The watcher, on {@link #WATCHER}, for as long as {@code reading} lasts: carries the pause the
     * user asked for to the runtime (again while a sentence is on its way and nothing plays yet,
     * so it holds once it does), resumes it, and asks for the first sound until it has come.
     */
    private static void watch(@NonNull Context app, @NonNull Reading reading, @Nullable Listener listener) {
        TaiManager manager = TaiManager.getInstance(app);
        boolean runtimePaused = false;
        while (!reading.ended && !reading.stopped) {
            int seen = reading.version();
            boolean again = false;
            try {
                boolean wantPaused = reading.isPaused();
                if (wantPaused && !runtimePaused) {
                    runtimePaused = manager.pauseSpeaking().optBoolean("paused", false);
                    again = !runtimePaused && reading.inFlight;
                } else if (!wantPaused && runtimePaused) {
                    manager.resumeSpeaking();
                    runtimePaused = false;
                }
                if (!wantPaused && reading.inFlight && !reading.sounding.get()) {
                    if (manager.speechState().optBoolean("sounding", false)) markSounding(reading, listener);
                    else again = true;
                }
            } catch (JSONException | RuntimeException e) {
                // The runtime is unreachable; the speak call will say so. Try again shortly.
                again = true;
            }
            try {
                reading.awaitChange(seen, again ? WATCH_MS : 0L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void markSounding(@NonNull Reading reading, @Nullable Listener listener) {
        if (reading.sounding.compareAndSet(false, true) && listener != null) MAIN.post(listener::onSounding);
    }

    /** Lets the stop sent for the reading before this one reach the runtime first. */
    private static void awaitStop(@Nullable Future<?> pendingStop) throws InterruptedException {
        if (pendingStop == null) return;
        try {
            pendingStop.get(STOP_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException ignored) {
            // Sent or not, the new reading goes ahead.
        }
    }

    /**
     * Holds the reading where it is: the voice stops mid-word and the next sentence waits. False
     * when nothing started here is being read, or it is paused already.
     */
    public static boolean pause() {
        Reading reading = current;
        return reading != null && reading.setPaused(true);
    }

    /** Carries a paused reading on from where it stopped; false when there is none. */
    public static boolean resume() {
        Reading reading = current;
        return reading != null && reading.setPaused(false);
    }

    /** Whether the reading started here is paused. */
    public static boolean isPaused() {
        Reading reading = current;
        return reading != null && reading.isPaused();
    }

    /**
     * Stops what is being read, wherever it was started (the phone has one voice); returns at
     * once. The reading started here, paused or not, sends no further sentence.
     */
    public static void stop(@NonNull Context context) {
        Context app = context.getApplicationContext();
        Reading reading = current;
        if (reading != null) reading.stop();
        lastStop = STOPPER.submit(() -> {
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
