package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A part through the gate: what is sent, with what context and language, and what comes back. */
public class VoiceGatedCleanupTest {

    private static final String LIGHT = VoicePolishRules.LEVEL_LIGHT;
    private static final String POLISHED = VoicePolishRules.LEVEL_POLISHED;

    /** Records each call and answers with the next of {@link #answers}, or the text in capitals. */
    private static final class Recorder implements VoiceGatedCleanup.Calls {
        final List<String> calls = new ArrayList<>();
        final List<VoiceTextPolisher.Result> answers = new ArrayList<>();

        @NonNull
        @Override
        public VoiceTextPolisher.Result clean(@NonNull String text, @NonNull String earlier, @Nullable String language) {
            calls.add(text + "|after:" + earlier + "|" + language);
            if (!answers.isEmpty()) return answers.remove(0);
            return VoiceTextPolisher.Result.polished(text.toUpperCase(java.util.Locale.ROOT));
        }
    }

    @Test
    public void cleanTextMakesNoCallAndStaysAsHeard() {
        Recorder calls = new Recorder();
        String raw = "The keyboard opens fine in portrait mode. In landscape it covers half the screen.";
        VoiceGatedCleanup.Outcome outcome = VoiceGatedCleanup.clean(raw, "", LIGHT, null, calls);
        assertTrue(calls.calls.isEmpty());
        assertEquals(raw, outcome.text);
        assertEquals("en", outcome.language);
        VoiceTextPolisher.Result result = outcome.result(raw, false);
        assertEquals(raw, result.text);
        assertEquals("fallback:unchanged", result.outcome);
    }

    @Test
    public void aSentenceWhoseOnlyTroubleWasAFillerIsNotSent() {
        Recorder calls = new Recorder();
        String raw = "So um, I think we should uh move the meeting to Thursday because, uh, half the team is out.";
        VoiceGatedCleanup.Outcome outcome = VoiceGatedCleanup.clean(raw, "", LIGHT, null, calls);
        assertTrue(calls.calls.isEmpty());
        assertEquals("So I think we should move the meeting to Thursday because half the team is out.", outcome.text);
        VoiceTextPolisher.Result result = outcome.result(raw, false);
        assertTrue(result.isPolished());
        assertEquals(outcome.text, result.text);
    }

    @Test
    public void onlyFlaggedSentencesAreSentWithTheSentenceBeforeThemAsContext() {
        Recorder calls = new Recorder();
        String raw = "We tried the new release today. Um, the the dock is too tall. Everything else looks right.";
        VoiceGatedCleanup.Outcome outcome = VoiceGatedCleanup.clean(raw, "Earlier part. It was cleaned.", LIGHT, null, calls);
        assertEquals(Collections.singletonList("The the dock is too tall.|after:We tried the new release today.|en"),
            calls.calls);
        assertEquals("We tried the new release today. THE THE DOCK IS TOO TALL. Everything else looks right.", outcome.text);
        assertEquals(3, outcome.groups);
        assertEquals(1, outcome.calls);
    }

    @Test
    public void theFirstGroupOfAPartReadsThePartsContext() {
        Recorder calls = new Recorder();
        VoiceGatedCleanup.clean("I think the the issue is here.", "We looked at the logs. It was cleaned.", LIGHT,
            null, calls);
        assertEquals(Collections.singletonList("I think the the issue is here.|after:It was cleaned.|en"), calls.calls);
    }

    @Test
    public void theLanguageComesFromTheHintOrTheText() {
        Recorder calls = new Recorder();
        String spanish = "Mañana no puedo ir a la reunión porque tengo que llevar a mi hija al al médico.";
        VoiceGatedCleanup.clean(spanish, "", LIGHT, null, calls);
        assertTrue(calls.calls.get(0).endsWith("|es"));
        VoiceGatedCleanup.clean(spanish, "", LIGHT, "pt-BR", calls);
        assertTrue(calls.calls.get(1).endsWith("|pt"));
    }

    @Test
    public void theFallbackLanguageIsTakenOnlyWhenTheTextIsUnclear() {
        Recorder calls = new Recorder();
        String spanish = "Mañana no puedo ir a la reunión porque tengo que llevar a mi hija al al médico.";
        // The text says Spanish, so the keyboard's English does not override it.
        VoiceGatedCleanup.clean(spanish, "", LIGHT, null, "en", calls);
        assertTrue(calls.calls.get(0).endsWith("|es"));
        // Text that reads as no language in particular takes the fallback and is still sent.
        VoiceGatedCleanup.Outcome outcome = VoiceGatedCleanup.clean("uh the meeting ist um sieben", "", LIGHT,
            null, "de", calls);
        assertEquals("de", outcome.language);
    }

    @Test
    public void unclearLanguageOnlyStripsFillers() {
        Recorder calls = new Recorder();
        String raw = "uh the meeting ist um sieben";
        VoiceGatedCleanup.Outcome outcome = VoiceGatedCleanup.clean(raw, "", LIGHT, null, calls);
        assertTrue(calls.calls.isEmpty());
        assertNull(outcome.language);
        // "uh" is no word anywhere; "um" is German for "at", so it stays while the language is unknown.
        assertEquals("the meeting ist um sieben", outcome.text);
        assertTrue(outcome.result(raw, false).isPolished());
    }

    @Test
    public void polishedSendsAPartWithASpokenListWhole() {
        Recorder calls = new Recorder();
        calls.answers.add(VoiceTextPolisher.Result.polished("Two things:\n1. Call the landlord.\n2. Get the parcel."));
        String raw = "Two things for tomorrow. One, call the landlord. Two, get the parcel.";
        VoiceGatedCleanup.Outcome outcome = VoiceGatedCleanup.clean(raw, "", POLISHED, null, calls);
        assertEquals(Collections.singletonList(raw + "|after:|en"), calls.calls);
        assertEquals("Two things:\n1. Call the landlord.\n2. Get the parcel.", outcome.text);
    }

    @Test
    public void aFailedCallKeepsItsGroupAsHeardAndAStuckRuntimeIsNotAskedAgain() {
        Recorder calls = new Recorder();
        calls.answers.add(VoiceTextPolisher.Result.fallback("x", "tai_runtime_timeout"));
        String raw = "I think the the issue is here. The rest is fine today. And and the second one too.";
        VoiceGatedCleanup.Outcome outcome = VoiceGatedCleanup.clean(raw, "", LIGHT, null, calls);
        assertEquals(1, calls.calls.size());
        assertEquals(raw, outcome.text);
        assertEquals("fallback:tai_runtime_timeout", outcome.failure);
        // An early pass hands the failure to the plan, which then cleans everything once at the end.
        assertEquals("fallback:tai_runtime_timeout", outcome.result(raw, true).outcome);
        assertEquals("fallback:tai_runtime_timeout", outcome.result(raw, false).outcome);

        // A refusal for one group is no reason to stop: the next is still sent, and the part is cleaned.
        Recorder refused = new Recorder();
        refused.answers.add(VoiceTextPolisher.Result.fallback("x", "refusal_or_answer"));
        VoiceGatedCleanup.Outcome partly = VoiceGatedCleanup.clean(raw, "", LIGHT, null, refused);
        assertEquals(2, refused.calls.size());
        assertEquals("I think the the issue is here. The rest is fine today. AND AND THE SECOND ONE TOO.", partly.text);
        assertTrue(partly.result(raw, true).isPolished());
    }

    @Test
    public void polishedKeepsALaidOutListOnItsOwnLines() {
        assertEquals("Intro.\n1. A\n2. B\nAfter.",
            VoiceGatedCleanup.join(Arrays.asList("Intro.", "1. A\n2. B", "After."), POLISHED));
        assertEquals("Intro. Middle. After.", VoiceGatedCleanup.join(Arrays.asList("Intro.", "Middle.", " ", "After."), POLISHED));
        assertEquals("Intro. 1. A\n2. B After.",
            VoiceGatedCleanup.join(Arrays.asList("Intro.", "1. A\n2. B", "After."), LIGHT));
    }
}
