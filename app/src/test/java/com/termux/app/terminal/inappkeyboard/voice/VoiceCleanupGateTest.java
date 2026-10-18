package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Which sentences of a part the gate sends, and why. */
public class VoiceCleanupGateTest {

    private static final String LIGHT = VoicePolishRules.LEVEL_LIGHT;
    private static final String POLISHED = VoicePolishRules.LEVEL_POLISHED;

    private static List<String> flags(String sentence) {
        return VoiceCleanupGate.flags(sentence, false);
    }

    @Test
    public void cleanSentencesHaveNoSign() {
        assertEquals(Collections.emptyList(), flags("We should keep the old behaviour as an option, because some users rely on it."));
        assertEquals(Collections.emptyList(), flags("Summarize this: the meeting ran long, nobody agreed on the budget."));
        assertEquals(Collections.emptyList(), flags("I think we should move the meeting to Thursday."));
    }

    @Test
    public void eachSignIsNamed() {
        assertEquals(Collections.singletonList(VoiceCleanupGate.DISCOURSE), flags("The drain is, you know, way worse."));
        assertEquals(Collections.singletonList(VoiceCleanupGate.DISCOURSE), flags("Can you okay so the widget broke."));
        assertEquals(Collections.singletonList(VoiceCleanupGate.REPEAT), flags("I think the the issue is here."));
        // A repeated phrase, as a stripped filler leaves it.
        assertEquals(Collections.singletonList(VoiceCleanupGate.REPEAT), flags("I don't, I don't want the dock to hide."));
        assertEquals(Collections.singletonList(VoiceCleanupGate.CORRECTION), flags("Send it to Mark, actually no, to Priya."));
        assertEquals(Collections.singletonList(VoiceCleanupGate.CORRECTION), flags("Release on Monday, no, Tuesday."));
        assertEquals(Collections.singletonList(VoiceCleanupGate.SYMBOL), flags("Open the file at slash home and read it."));
        assertEquals(Collections.singletonList(VoiceCleanupGate.STRAY_CAPITAL), flags("Otherwise people will Lose the last word."));
        assertEquals(Collections.singletonList(VoiceCleanupGate.LOWERCASE_START), flags("sounds good to me then."));
        assertTrue(flags(words(VoiceCleanupGate.RUNON_WORDS + 1)).contains(VoiceCleanupGate.RUNON));
        assertFalse(flags(words(VoiceCleanupGate.RUNON_WORDS)).contains(VoiceCleanupGate.RUNON));
        // "I" is capitalised anywhere, and a capital after a full stop opens a sentence.
        assertFalse(flags("Then I said it works.").contains(VoiceCleanupGate.STRAY_CAPITAL));
    }

    @Test
    public void sentencesSplitAtAnyKindOfSpace() {
        // A no-break or thin space after the stop splits too, as plain spaces and a line break do.
        assertEquals(Arrays.asList("One.", "Two?", "Three!", "Four."),
            VoiceCleanupGate.sentences("One.\u00a0Two? Three!\n\u202fFour."));
    }

    @Test
    public void aPartWithNoSignMakesOneUnsentGroup() {
        String text = "The keyboard opens fine in portrait mode. In landscape it covers half the screen.";
        List<VoiceCleanupGate.Group> groups = VoiceCleanupGate.groups(text, LIGHT);
        assertEquals(1, groups.size());
        assertFalse(groups.get(0).send);
        assertEquals(text, groups.get(0).text);
    }

    @Test
    public void aCorrectionSendsTheSentenceItCorrectsAndAShortOneTheNext() {
        List<VoiceCleanupGate.Group> groups = VoiceCleanupGate.groups(
            "We meet at the cafe on Fifth Street. Wait, no. It's the one on Main Street. Bring the contract.", LIGHT);
        assertEquals(2, groups.size());
        assertTrue(groups.get(0).send);
        assertEquals("We meet at the cafe on Fifth Street. Wait, no. It's the one on Main Street.", groups.get(0).text);
        assertFalse(groups.get(1).send);
        assertEquals("Bring the contract.", groups.get(1).text);
    }

    @Test
    public void aSpokenListIsSentWholeForPolishedAndByItsItemsForLight() {
        String text = "For the release I need three things. First, update the changelog. Second, bump the version. "
            + "And third, tag the release.";
        assertTrue(VoiceCleanupGate.spokenList(text));
        List<VoiceCleanupGate.Group> polished = VoiceCleanupGate.groups(text, POLISHED);
        assertEquals(1, polished.size());
        assertTrue(polished.get(0).send);
        assertEquals(VoiceCleanupGate.LIST_PART, polished.get(0).flags.get(0));
        List<VoiceCleanupGate.Group> light = VoiceCleanupGate.groups(text, LIGHT);
        assertEquals(2, light.size());
        assertFalse(light.get(0).send);
        assertEquals("First, update the changelog. Second, bump the version. And third, tag the release.", light.get(1).text);
        assertEquals(Arrays.asList(VoiceCleanupGate.LIST, VoiceCleanupGate.LIST, VoiceCleanupGate.LIST),
            light.get(1).flags);
        // One ordinal is no list.
        assertFalse(VoiceCleanupGate.spokenList("First, update the changelog. Then tag it."));
        assertTrue(VoiceCleanupGate.spokenList("The following need fixing the clock and the card."));
    }

    @Test
    public void theContextIsTheLastSentenceOfTheLastLine() {
        assertEquals("Bring the contract.", VoiceCleanupGate.lastSentence("We meet at noon. Bring the contract."));
        assertEquals("Tag the release.", VoiceCleanupGate.lastSentence("Three things:\n1. Update it.\n2. Tag the release."));
        String unpunctuated = words(VoiceCleanupGate.CONTEXT_MAX_WORDS + 5);
        assertEquals(VoiceCleanupGate.CONTEXT_MAX_WORDS,
            VoicePolishRules.wordCount(VoiceCleanupGate.lastSentence(unpunctuated)));
        assertEquals("", VoiceCleanupGate.lastSentence(""));
    }

    private static String words(int count) {
        StringBuilder text = new StringBuilder("Word");
        for (int i = 1; i < count; i++) text.append(" word").append(i);
        return text.toString();
    }
}
