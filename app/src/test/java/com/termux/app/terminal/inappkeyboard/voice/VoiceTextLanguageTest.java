package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Reading a dictation's language from its text, and naming it for the prompt. */
public class VoiceTextLanguageTest {

    private static void assertLanguage(String expected, String text) {
        assertEquals(text, expected, VoiceTextLanguage.guess(text));
    }

    @Test
    public void latinScriptLanguagesAreToldApartByTheirCommonWords() {
        assertLanguage("en", "Let's do the release on Monday, no, Tuesday, after the standup.");
        assertLanguage("en", "I want to go to my house.");
        assertLanguage("es", "Mañana no puedo ir a la reunión porque tengo que llevar a mi hija al médico.");
        assertLanguage("es", "¿Puedes enviarme el archivo antes del viernes?");
        assertLanguage("de", "Er kommt um sieben Uhr nach Hause und ist dann müde.");
        assertLanguage("de", "Was ist das für ein Fehler?");
        assertLanguage("fr", "Je pense que le bouton est trop petit sur la page d'accueil.");
        assertLanguage("it", "Il tasto è troppo piccolo e non si vede bene.");
        assertLanguage("pt", "Eu vou chegar um pouco atrasado porque o ônibus está parado.");
        assertLanguage("nl", "Het werkt niet op mijn telefoon, maar wel op de tablet.");
        assertLanguage("pl", "Nie mogę przyjść na spotkanie, bo muszę zawieźć córkę do lekarza.");
    }

    @Test
    public void otherScriptsNameTheirLanguage() {
        assertLanguage("ru", "Я не могу прийти на встречу, потому что мне нужно к врачу.");
        assertLanguage("uk", "Я не можу прийти на зустріч, бо мені треба до лікаря.");
        assertLanguage("ar", "لا أستطيع الحضور إلى الاجتماع غدا");
        assertLanguage("hi", "मैं कल मीटिंग में नहीं आ सकता क्योंकि मुझे डॉक्टर के पास जाना है।");
        assertLanguage("zh", "我明天不能去开会，因为我要带女儿去看医生。");
        assertLanguage("ja", "明日の会議には行けません。娘を病院に連れて行かなければなりません。");
        assertLanguage("ko", "내일 회의에 갈 수 없어요. 딸을 병원에 데려가야 해요.");
    }

    @Test
    public void unclearOrMixedTextHasNoLanguage() {
        // Too few common words to tell.
        assertLanguage(null, "sounds good to me");
        assertLanguage(null, "Merci beaucoup Monsieur Dupont");
        // Half English, half German.
        assertLanguage(null, "The meeting ist um sieben");
        // Persian is written in Arabic script, but is not Arabic.
        assertLanguage(null, "من نمی‌توانم فردا به جلسه بیایم");
        assertLanguage(null, "");
        assertLanguage(null, "1 2 3 ...");
    }

    @Test
    public void hintsAreReducedToTheBareCode() {
        assertEquals("en", VoiceTextLanguage.normalize("en-US"));
        assertEquals("pt", VoiceTextLanguage.normalize(" PT_br "));
        assertEquals("fil", VoiceTextLanguage.normalize("fil"));
        assertNull(VoiceTextLanguage.normalize(null));
        assertNull(VoiceTextLanguage.normalize(""));
        assertNull(VoiceTextLanguage.normalize("english"));
    }

    @Test
    public void thePromptNamesTheLanguageInEnglish() {
        assertEquals("Spanish", VoiceTextLanguage.displayName("es"));
        assertEquals("Ukrainian", VoiceTextLanguage.displayName("uk-UA"));
        // A language the speech model knows and this class does not read is still named.
        assertEquals("Swedish", VoiceTextLanguage.displayName("sv"));
        assertNull(VoiceTextLanguage.displayName("zz"));
        assertNull(VoiceTextLanguage.displayName(null));
    }
}
