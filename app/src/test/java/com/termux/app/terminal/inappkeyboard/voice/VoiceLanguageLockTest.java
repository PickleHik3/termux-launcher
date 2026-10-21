package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.ai.WhisperLanguageDetection;

import org.junit.Test;

/** What each phrase is sent with, and when a dictation's language settles and stays. */
public class VoiceLanguageLockTest {
    private static final String DETECTED = WhisperLanguageDetection.SOURCE_DETECTED;
    private static final String FALLBACK = WhisperLanguageDetection.SOURCE_FALLBACK;
    private static final String FORCED = WhisperLanguageDetection.SOURCE_FORCED;

    @Test
    public void aForcedLanguageIsSentEveryPhraseAndReportedOnce() {
        VoiceLanguageLock lock = new VoiceLanguageLock("de", null);
        assertEquals("de", lock.requestLanguage());
        assertNull(lock.requestHint());
        VoiceLanguageLock.Settled settled = lock.onAnswer("de", FORCED);
        assertNotNull(settled);
        assertEquals("de", settled.code);
        assertFalse(settled.differsFromHint);
        assertNull(lock.onAnswer("de", FORCED));
        assertEquals("de", lock.requestLanguage());
        assertNull(lock.requestHint());
    }

    @Test
    public void autoListensUntilAPhraseIsSureThenLocksIt() {
        VoiceLanguageLock lock = new VoiceLanguageLock(null, "en");
        // Listening: no language, the hint to fall back to.
        assertNull(lock.requestLanguage());
        assertEquals("en", lock.requestHint());
        // An unsure phrase was decoded in the hint; the next one asks again.
        assertNull(lock.onAnswer("en", FALLBACK));
        assertNull(lock.requestLanguage());
        assertEquals("en", lock.requestHint());
        // A sure one locks for the rest of the session.
        VoiceLanguageLock.Settled settled = lock.onAnswer("es", DETECTED);
        assertNotNull(settled);
        assertEquals("es", settled.code);
        assertTrue(settled.differsFromHint);
        assertEquals("es", lock.requestLanguage());
        assertNull(lock.requestHint());
        // Locked phrases come back forced and change nothing.
        assertNull(lock.onAnswer("es", FORCED));
        assertNull(lock.onAnswer("fr", DETECTED));
        assertEquals("es", lock.requestLanguage());
    }

    @Test
    public void hearingTheHintsOwnLanguageLocksItWithoutNamingIt() {
        VoiceLanguageLock lock = new VoiceLanguageLock(null, "en");
        VoiceLanguageLock.Settled settled = lock.onAnswer("en", DETECTED);
        assertNotNull(settled);
        assertEquals("en", settled.code);
        assertFalse(settled.differsFromHint);
        assertEquals("en", lock.requestLanguage());
        assertNull(lock.requestHint());
    }

    @Test
    public void anAnswerWithoutALanguageOrSourceSettlesNothing() {
        VoiceLanguageLock lock = new VoiceLanguageLock(null, "en");
        assertNull(lock.onAnswer(null, DETECTED));
        assertNull(lock.onAnswer("", DETECTED));
        assertNull(lock.onAnswer("es", null));
        assertNull(lock.onAnswer("es", ""));
        assertNull(lock.requestLanguage());
        assertEquals("en", lock.requestHint());
    }

    @Test
    public void anEngineThatDecidesAloneIsSentNothingAndNothingIsReported() {
        VoiceLanguageLock lock = new VoiceLanguageLock(null, null);
        assertNull(lock.requestLanguage());
        assertNull(lock.requestHint());
        // Parakeet echoes "auto"; a graph asked without a hint still says detected: neither settles.
        assertNull(lock.onAnswer("auto", null));
        assertNull(lock.onAnswer("es", DETECTED));
        assertNull(lock.requestLanguage());
    }
}
