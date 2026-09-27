package com.termux.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Plays synthesised speech as it is produced: the synthesiser {@link #enqueue enqueues} each
 * sentence the moment it exists and a writer thread feeds a streaming float {@link AudioTrack}, so
 * sentence two is computed while sentence one is heard. One player speaks one utterance and is
 * then released; nothing here outlives the request that made it.
 *
 * <p>Audio focus: a transient, duckable request, so music and videos dip under the voice rather
 * than stopping. The player pauses (never ducks itself — half-volume speech is hard to follow) on a
 * transient loss such as a notification sound or a call ringing, resumes when focus comes back, and
 * stops for good on a permanent loss (another app starts playing). Usage is
 * {@link AudioAttributes#USAGE_ASSISTANT} with {@link AudioAttributes#CONTENT_TYPE_SPEECH}: this is
 * the phone's assistant talking, and it follows the media volume.
 */
final class TaiTtsPlayer {
    private static final String TAG = "TaiTts";
    private static final float[] END = new float[0];
    /** How much audio the track buffers ahead: enough to ride out a scheduling hiccup, little enough that Stop is immediate. */
    private static final int BUFFER_MS = 250;
    /**
     * How far synthesis may run ahead of playback. The phone synthesises faster than it speaks, and
     * a long text queued whole would hold minutes of float audio (about 96 KB a second); past this
     * the synthesiser waits for the speaker to catch up.
     */
    private static final int MAX_QUEUED_SECONDS = 30;

    private final Context appContext;
    private final int sampleRate;
    private final LinkedBlockingQueue<float[]> queue = new LinkedBlockingQueue<>();
    private final Object pauseLock = new Object();
    private final Object queueLock = new Object();
    /** Frames enqueued and not yet written to the track; guarded by {@link #queueLock}. */
    private long queuedFrames;
    @Nullable private AudioManager audioManager;
    @Nullable private AudioFocusRequest focusRequest;
    @Nullable private volatile AudioTrack track;
    @Nullable private Thread writer;
    private volatile boolean stopped;
    private volatile boolean paused;
    private volatile long framesWritten;
    private volatile long firstSoundAtMs = -1L;
    private volatile boolean done;

    TaiTtsPlayer(@NonNull Context context, int sampleRate) {
        this.appContext = context.getApplicationContext();
        this.sampleRate = sampleRate;
    }

    /**
     * Takes audio focus and opens the track. False when focus is refused (a call is in progress,
     * say): the caller should not synthesise into a player that cannot be heard.
     */
    boolean start() {
        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
        audioManager = appContext.getSystemService(AudioManager.class);
        if (audioManager != null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(this::onFocusChange, new Handler(Looper.getMainLooper()))
                .build();
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = null;
                return false;
            }
        }
        int minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        int bufferBytes = Math.max(minBuffer, sampleRate * 4 * BUFFER_MS / 1000);
        AudioTrack created;
        try {
            created = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build();
        } catch (RuntimeException e) {
            Log.w(TAG, "AudioTrack could not be created: " + e.getMessage());
            abandonFocus();
            return false;
        }
        track = created;
        created.play();
        writer = new Thread(this::writeLoop, "tai-tts-audio");
        writer.setDaemon(true);
        writer.start();
        return true;
    }

    /**
     * Queues one sentence, waiting while more than {@link #MAX_QUEUED_SECONDS} are already queued;
     * false once the player has stopped (the synthesiser should stop too).
     */
    boolean enqueue(@NonNull float[] samples, int count) {
        if (stopped) return false;
        float[] copy = count == samples.length ? samples : java.util.Arrays.copyOf(samples, count);
        synchronized (queueLock) {
            while (!stopped && queuedFrames > (long) sampleRate * MAX_QUEUED_SECONDS) {
                try {
                    queueLock.wait(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (stopped) return false;
            queuedFrames += copy.length;
        }
        queue.offer(copy);
        return !stopped;
    }

    /** No more sentences are coming; playback continues to the end of what is queued. */
    void finish() {
        queue.offer(END);
    }

    /**
     * Blocks until everything queued has been heard (or the player stopped), at most
     * {@code timeoutMs}. Returns whether playback reached its end.
     */
    boolean awaitDone(long timeoutMs) {
        Thread thread = writer;
        if (thread == null) return true;
        try {
            thread.join(Math.max(1L, timeoutMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return done && !stopped;
    }

    /** Stops now: drops what is queued, silences the track and gives focus back. Safe from any thread, idempotent. */
    void stop() {
        stopped = true;
        queue.clear();
        queue.offer(END);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        synchronized (queueLock) {
            queueLock.notifyAll();
        }
        AudioTrack current = track;
        if (current != null) {
            try {
                current.pause();
                current.flush();
            } catch (IllegalStateException ignored) {
                // Already released by the writer.
            }
        }
    }

    boolean isStopped() {
        return stopped;
    }

    /** When the first sample went to the track ({@link SystemClock#elapsedRealtime} ms), or -1. */
    long firstSoundAtMs() {
        return firstSoundAtMs;
    }

    private void writeLoop() {
        AudioTrack current = track;
        if (current == null) return;
        try {
            while (!stopped) {
                float[] sentence = queue.poll(1, TimeUnit.SECONDS);
                if (sentence == null) continue;
                if (sentence == END) break;
                int offset = 0;
                while (offset < sentence.length && !stopped) {
                    waitWhilePaused();
                    if (stopped) break;
                    int written = current.write(sentence, offset, sentence.length - offset, AudioTrack.WRITE_BLOCKING);
                    if (written < 0) {
                        Log.w(TAG, "AudioTrack write failed: " + written);
                        stopped = true;
                        break;
                    }
                    if (written > 0 && firstSoundAtMs < 0) firstSoundAtMs = SystemClock.elapsedRealtime();
                    offset += written;
                    framesWritten += written;
                }
                synchronized (queueLock) {
                    queuedFrames -= sentence.length;
                    queueLock.notifyAll();
                }
            }
            if (!stopped) drain(current);
            done = !stopped;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                current.stop();
            } catch (IllegalStateException ignored) {
            }
            current.release();
            track = null;
            abandonFocus();
        }
    }

    /** Waits for the playback head to reach the last frame written, so the tail is heard before the track stops. */
    private void drain(@NonNull AudioTrack current) throws InterruptedException {
        long total = framesWritten;
        long deadline = SystemClock.elapsedRealtime() + total * 1000L / sampleRate + 2_000L;
        while (!stopped && SystemClock.elapsedRealtime() < deadline) {
            if (paused) {
                waitWhilePaused();
                deadline = SystemClock.elapsedRealtime() + total * 1000L / sampleRate + 2_000L;
                continue;
            }
            long head = current.getPlaybackHeadPosition() & 0xffffffffL;
            if (head >= total) return;
            Thread.sleep(20L);
        }
    }

    private void waitWhilePaused() {
        synchronized (pauseLock) {
            while (paused && !stopped) {
                try {
                    pauseLock.wait(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void onFocusChange(int change) {
        AudioTrack current = track;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                stop();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                paused = true;
                if (current != null) {
                    try {
                        current.pause();
                    } catch (IllegalStateException ignored) {
                    }
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (paused && current != null && !stopped) {
                    try {
                        current.play();
                    } catch (IllegalStateException ignored) {
                    }
                }
                synchronized (pauseLock) {
                    paused = false;
                    pauseLock.notifyAll();
                }
                break;
            default:
                break;
        }
    }

    private void abandonFocus() {
        AudioManager manager = audioManager;
        AudioFocusRequest request = focusRequest;
        focusRequest = null;
        if (manager != null && request != null) manager.abandonAudioFocusRequest(request);
    }
}
