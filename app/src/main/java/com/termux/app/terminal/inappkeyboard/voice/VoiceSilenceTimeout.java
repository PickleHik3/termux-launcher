package com.termux.app.terminal.inappkeyboard.voice;

/**
 * The keyboard's "Silence auto-stop" setting, as milliseconds for {@link VoiceActivityDetector}: a
 * pure mapping, kept free of the store so it can be tested without one. 2.5 s ended a session
 * before a slow speaker finished a sentence; the offered choices are 5 s, 10 s (the default), 30 s,
 * and "Until tap" — the session then only ends on a tap, hiding the keyboard, or a failure.
 */
public final class VoiceSilenceTimeout {

    /** No timeout: the session waits for a tap (or another end cause) no matter how long it is quiet. */
    public static final int UNTIL_TAP = 0;

    public static final int[] CHOICES_MS = {5_000, 10_000, 30_000, UNTIL_TAP};

    public static final int DEFAULT_MS = 10_000;

    private VoiceSilenceTimeout() {
    }

    /** {@code storedMs} when it is one of {@link #CHOICES_MS}, {@link #DEFAULT_MS} otherwise. */
    public static int normalize(int storedMs) {
        for (int choice : CHOICES_MS) {
            if (choice == storedMs) return storedMs;
        }
        return DEFAULT_MS;
    }
}
