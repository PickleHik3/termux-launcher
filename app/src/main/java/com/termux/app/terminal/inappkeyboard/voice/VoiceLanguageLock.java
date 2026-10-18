package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.WhisperLanguageDetection;

/**
 * One dictation's spoken language. With a forced language every phrase is sent in it. With only a
 * hint (Voice language: Auto on a multilingual Whisper graph) each phrase is sent with no language
 * and the hint, so the graph listens: the first phrase it is sure of
 * ({@link WhisperLanguageDetection#SOURCE_DETECTED}; an unsure one was decoded in the hint and
 * asks again next phrase) locks that language, and every later phrase is sent in it, so one
 * dictation never changes language between phrases. With neither (an {@code .en} graph,
 * Parakeet) nothing is sent and nothing is reported.
 *
 * <p>Used from the session's single STT thread, one phrase at a time, so it needs no locking.
 */
final class VoiceLanguageLock {

    /** The session's language, reported once. */
    static final class Settled {
        @NonNull final String code;
        /** Heard, and not the layout's or the locale's language: the one case the card names it. */
        final boolean differsFromHint;

        Settled(@NonNull String code, boolean differsFromHint) {
            this.code = code;
            this.differsFromHint = differsFromHint;
        }
    }

    @Nullable private final String forced;
    @Nullable private final String hint;
    @Nullable private String locked;
    private boolean reported;

    VoiceLanguageLock(@Nullable String forced, @Nullable String hint) {
        this.forced = forced;
        this.hint = hint;
    }

    /** The {@code language} the next phrase is sent with, or {@code null} to let the graph listen (or to send none). */
    @Nullable
    String requestLanguage() {
        return forced != null ? forced : locked;
    }

    /** The {@code language_hint} the next phrase is sent with: only while still listening. */
    @Nullable
    String requestHint() {
        return forced == null && locked == null ? hint : null;
    }

    /**
     * One phrase's answer: its {@code language} and {@code languageSource}. The session's language
     * the first time it is known — the forced one at the first answer, or the first one the graph
     * was sure of — and {@code null} every other time.
     */
    @Nullable
    Settled onAnswer(@Nullable String language, @Nullable String source) {
        if (reported) return null;
        if (forced != null) {
            reported = true;
            return new Settled(forced, false);
        }
        if (hint == null || language == null || language.isEmpty()
            || !WhisperLanguageDetection.SOURCE_DETECTED.equals(source)) {
            return null;
        }
        locked = language;
        reported = true;
        return new Settled(language, !language.equals(hint));
    }
}
