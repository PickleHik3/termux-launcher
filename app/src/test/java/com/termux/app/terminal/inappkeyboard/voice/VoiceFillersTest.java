package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The deterministic filler strip: which spellings go, in which languages, and the punctuation around them. */
public class VoiceFillersTest {

    @Test
    public void aFillerThatOpensASentenceHandsItsCapitalOn() {
        assertEquals("So the build failed.", VoiceFillers.strip("Um, so the build failed.", "en"));
        assertEquals("It works. Next we ship.", VoiceFillers.strip("It works. Um. Next we ship.", "en"));
        assertEquals("Line one.\nLine two", VoiceFillers.strip("Line one.\nUm, line two", "en"));
        // The recogniser wrote it in lower case: the sentence keeps its own case.
        assertEquals("sounds good", VoiceFillers.strip("uh sounds good", "en"));
    }

    @Test
    public void aFillerThatClosesASentenceLeavesItsFullStop() {
        assertEquals("Okay.", VoiceFillers.strip("Okay, um.", "en"));
        assertEquals("and then", VoiceFillers.strip("and then um", "en"));
        assertEquals("", VoiceFillers.strip("Um.", "en"));
    }

    @Test
    public void midSentenceTheCommasAroundItBecomeOneOrNone() {
        assertEquals("I think we should move it", VoiceFillers.strip("I think we should uh move it", "en"));
        assertEquals("Shopping list, eggs", VoiceFillers.strip("Shopping list, um, eggs", "en"));
        // After a word a comma never follows, the comma was the filler's pause.
        assertEquals("because half the team", VoiceFillers.strip("because, uh, half the team", "en"));
        assertEquals("The thing is it broke", VoiceFillers.strip("The thing is uh... it broke", "en"));
        assertEquals("We need, more time", VoiceFillers.strip("We erm need, er, more time", "en"));
    }

    @Test
    public void stretchedSpellingsGoToo() {
        assertEquals("Can you okay so", VoiceFillers.strip("Uhhhhh, can you, hmm, okay so", "en"));
        assertEquals("Je pense que, oui.", VoiceFillers.strip("Euh, je pense que, euh, oui.", "fr"));
        assertEquals("Я, не знаю, что сказать.", VoiceFillers.strip("Я, э, не знаю, эм, что сказать.", "ru"));
    }

    @Test
    public void shortSpellingsThatAreWordsStayInTheLanguagesWhereTheyAre() {
        // "er" is "he" and "um" is "at" in German, "eh" is "anyway"; "äh" and "ähm" are fillers.
        assertEquals("Er kommt um sieben, nach Hause.", VoiceFillers.strip("Er kommt um sieben, äh, nach Hause.", "de"));
        assertEquals("Das ist eh egal, oder?", VoiceFillers.strip("Das ist eh egal, ähm, oder?", "de"));
        // "um" is "a" in Portuguese.
        assertEquals("Eu tenho um carro, novo.", VoiceFillers.strip("Eu tenho um carro, eh, novo.", "pt"));
        // Not knowing the language, only the spellings that are no word anywhere go.
        assertEquals("Eu tenho um carro, novo.", VoiceFillers.strip("Eu tenho um carro, uh, novo.", null));
        assertEquals("um", VoiceFillers.strip("um", null));
    }

    @Test
    public void acronymsAndWordsThatContainAFillerStay() {
        assertEquals("Take him to the ER now", VoiceFillers.strip("Take him to the ER now", "en"));
        assertEquals("uh-huh that works", VoiceFillers.strip("uh-huh that works", "en"));
        assertEquals("The umbrella and the hummus", VoiceFillers.strip("The umbrella and the hummus", "en"));
    }
}
