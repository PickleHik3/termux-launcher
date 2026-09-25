package com.termux.app.terminal.inappkeyboard.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The cues around a {@link VoiceInputSession}: a light haptic and an "up" blip when the
 * microphone opens, a confirm haptic and a "down" blip when the session ends, a double haptic and
 * a double blip when it ends on a failure. Tones are {@link VoiceTone} PCM pushed through a static
 * {@link AudioTrack} on the assistance-sonification usage, so they follow the same volume as the
 * system's own touch and keyboard sounds rather than the media stream. Everything runs on one
 * {@code voice-feedback} thread; the main thread only queues.
 *
 * <p>Tones follow the keyboard's "Voice sounds" setting; haptics follow its key haptics
 * setting, because the voice key is a key and someone who has turned key ticks off has said what
 * they think of the phone buzzing at them.
 */
public final class VoiceFeedback {

    private static final String LOG_TAG = "VoiceFeedback";
    /** Kept playing this long past the tone's own length, for the audio path's start-up latency. */
    private static final long TONE_SETTLE_MS = 60L;

    private final Context appContext;
    private final boolean sounds;
    private final boolean haptics;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voice-feedback");
        thread.setDaemon(true);
        return thread;
    });

    public VoiceFeedback(@NonNull Context context, boolean sounds, boolean haptics) {
        this.appContext = context.getApplicationContext();
        this.sounds = sounds;
        this.haptics = haptics;
    }

    /** Whether a start tone will actually be played — the capture side discards its lead-in only then. */
    public boolean playsTones() {
        return sounds;
    }

    /** The microphone has just opened. */
    public void onStart() {
        cue(VoiceTone.start(), Haptic.LIGHT);
    }

    /** The session ended the ordinary way (tap, silence, keyboard down). */
    public void onStop() {
        cue(VoiceTone.stop(), Haptic.CONFIRM);
    }

    /** The session ended on a failure. */
    public void onError() {
        cue(VoiceTone.error(), Haptic.ERROR);
    }

    /** Lets whatever is queued play out, then lets the thread go. */
    public void release() {
        executor.shutdown();
    }

    private enum Haptic { LIGHT, CONFIRM, ERROR }

    private void cue(@NonNull short[] tone, @NonNull Haptic haptic) {
        if (!sounds && !haptics) return;
        try {
            executor.execute(() -> {
                if (haptics) vibrate(haptic);
                if (sounds) play(tone);
            });
        } catch (RuntimeException ignored) {
            // Already released: the session is over and a late cue is not worth a crash.
        }
    }

    private void vibrate(@NonNull Haptic haptic) {
        Vibrator vibrator = appContext.getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        VibrationEffect effect;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int predefined;
            switch (haptic) {
                case LIGHT: predefined = VibrationEffect.EFFECT_TICK; break;
                case CONFIRM: predefined = VibrationEffect.EFFECT_CLICK; break;
                default: predefined = VibrationEffect.EFFECT_DOUBLE_CLICK; break;
            }
            effect = VibrationEffect.createPredefined(predefined);
        } else {
            switch (haptic) {
                case LIGHT: effect = VibrationEffect.createOneShot(15L, VibrationEffect.DEFAULT_AMPLITUDE); break;
                case CONFIRM: effect = VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE); break;
                default: effect = VibrationEffect.createWaveform(new long[] {0L, 35L, 60L, 35L}, -1); break;
            }
        }
        try {
            vibrator.vibrate(effect);
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "vibrate failed: " + e.getMessage());
        }
    }

    /** Blocks the feedback thread for the tone's length: a static track is written, played and released. */
    private void play(@NonNull short[] pcm) {
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(VoiceTone.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2)
                .build();
            if (track.getState() == AudioTrack.STATE_UNINITIALIZED) {
                Logger.logWarn(LOG_TAG, "AudioTrack did not initialise");
                return;
            }
            int written = track.write(pcm, 0, pcm.length);
            if (written < pcm.length) {
                Logger.logWarn(LOG_TAG, "AudioTrack.write: " + written + " of " + pcm.length);
                return;
            }
            track.play();
            Thread.sleep(pcm.length * 1000L / VoiceTone.SAMPLE_RATE + TONE_SETTLE_MS);
            track.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "tone failed: " + e.getMessage());
        } finally {
            if (track != null) track.release();
        }
    }
}
