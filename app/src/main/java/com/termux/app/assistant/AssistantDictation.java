package com.termux.app.assistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;
import com.termux.app.terminal.inappkeyboard.voice.VoiceInputSession;
import com.termux.app.terminal.inappkeyboard.voice.VoiceLanguage;
import com.termux.app.terminal.inappkeyboard.voice.VoiceMicSensitivity;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Locale;

/**
 * Dictation for the composer (sheet 02): one {@link VoiceInputSession} with the voice-typing model
 * the launcher already uses. The session hands over each phrase as its pause closes, so the
 * Listening row shows the words heard so far phrase by phrase, not word by word; Done keeps what
 * was heard and Cancel throws it away. Nothing is sent. After {@link #SILENCE_MS} with nothing
 * heard, listening ends on its own.
 */
final class AssistantDictation {

    /** Told on the main thread. */
    interface Callback {
        void onListening();

        /** The level in [0, 1] over the room noise. */
        void onLevel(float level);

        /** Everything heard so far. */
        void onText(@NonNull String heard);

        /** Dictation ended; {@code text} is what to put in the field (empty when nothing was heard). */
        void onFinished(@NonNull String text, boolean cancelled, boolean failed);
    }

    /** Why a start did not happen. */
    enum Start { STARTED, NO_SPEECH_MODEL, NO_PERMISSION, FAILED }

    static final long SILENCE_MS = 8_000L;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable private VoiceInputSession session;
    @Nullable private Callback callback;
    private final StringBuilder heard = new StringBuilder();
    private boolean cancelled;
    private boolean finished;
    private final Runnable silence = () -> {
        if (session != null && heard.length() == 0) stop(false);
    };

    AssistantDictation(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    boolean isActive() {
        return session != null;
    }

    /** Whether the phone has a speech model the voice-typing setting can use. */
    boolean hasSpeechModel() {
        return TaiSpeechModels.resolveActive(context) != null;
    }

    boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    Start start(@NonNull Callback callback) {
        if (session != null) return Start.STARTED;
        TaiModelSpec model = TaiSpeechModels.resolveActive(context);
        if (model == null) return Start.NO_SPEECH_MODEL;
        if (!hasPermission()) return Start.NO_PERMISSION;
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
        if (prefs == null) return Start.FAILED;
        int windowSeconds = TaiSpeechModels.windowSeconds(model);
        if (windowSeconds <= 0) windowSeconds = new TaiSettings(context).getSttWindowSeconds();
        VoiceLanguage.Choice language = languageFor(model, prefs);
        VoiceInputSession.Config config = new VoiceInputSession.Config(model.id, language.forced, language.hint,
            prefs.getInAppKeyboardVoicePauseMs(), windowSeconds, prefs.getInAppKeyboardVoiceSilenceTimeoutMs(),
            prefs.isInAppKeyboardVoiceSoundsEnabled(), prefs.isInAppKeyboardHapticsEnabled(),
            VoiceMicSensitivity.fromStorage(prefs.getInAppKeyboardVoiceMicSensitivity()));
        this.callback = callback;
        heard.setLength(0);
        cancelled = false;
        finished = false;
        VoiceInputSession created = new VoiceInputSession(context, config, new HostImpl());
        session = created;
        if (!created.start()) {
            session = null;
            this.callback = null;
            return Start.FAILED;
        }
        main.postDelayed(silence, SILENCE_MS);
        return Start.STARTED;
    }

    /** Done: keeps what was heard; phrases still being transcribed arrive before it ends. */
    void stop(boolean ignored) {
        VoiceInputSession s = session;
        if (s != null) s.stop(VoiceInputSession.EndReason.USER);
    }

    /** Cancel: the field returns as it was. */
    void cancel() {
        VoiceInputSession s = session;
        cancelled = true;
        if (s != null) s.cancel(VoiceInputSession.EndReason.USER);
    }

    @NonNull
    private static VoiceLanguage.Choice languageFor(@NonNull TaiModelSpec model, @NonNull TermuxAppSharedPreferences prefs) {
        if (TaiSpeechModels.isParakeet(model.id, model.localPath) || TaiSpeechModels.isEnglishOnly(model)) {
            return VoiceLanguage.Choice.NONE;
        }
        Locale locale = Locale.getDefault();
        return VoiceLanguage.Choice.forWhisper(VoiceLanguage.resolve(prefs.getInAppKeyboardVoiceLanguage(), null, locale));
    }

    private void finish(boolean failed) {
        if (finished) return;
        finished = true;
        main.removeCallbacks(silence);
        session = null;
        Callback c = callback;
        callback = null;
        if (c != null) c.onFinished(cancelled ? "" : heard.toString().trim(), cancelled, failed);
    }

    private final class HostImpl implements VoiceInputSession.Host {
        @Override public void onListening() {
            if (callback != null) callback.onListening();
        }

        @Override public void onSegmentCaptured() { }

        @Override public void onSegmentSettled() { }

        @Override public void onSpeechReady() { }

        @Override public void onLevel(float rms, boolean voiced, float noiseFloor) {
            if (callback != null) callback.onLevel(levelOf(rms, noiseFloor));
        }

        @Override public void onSpeechLanguage(@NonNull String iso, boolean differsFromHint) { }

        @Override public void onTranscript(@NonNull String text) {
            if (cancelled) return;
            if (heard.length() > 0) heard.append(' ');
            heard.append(text.trim());
            main.removeCallbacks(silence);
            if (callback != null) callback.onText(heard.toString());
        }

        @Override public void onDraining() { }

        @Override public void onFailure(@NonNull String code, @NonNull String message, boolean anyTranscriptDelivered) {
            finish(!anyTranscriptDelivered);
        }

        @Override public void onEnded(@NonNull VoiceInputSession.EndReason reason) {
            finish(reason == VoiceInputSession.EndReason.FAILED && heard.length() == 0);
        }
    }

    /** How far above the room noise the level is, 0 to 1: the keyboard's pill meter's curve. */
    static float levelOf(float rms, float noiseFloor) {
        if (rms <= 0f || noiseFloor <= 0f) return 0f;
        double db = 20.0 * Math.log10(rms / (double) noiseFloor) - 6.0;
        if (db <= 0.0) return 0f;
        return (float) Math.min(1.0, 1.0 - Math.exp(-0.11512925464970229 * db));
    }
}
