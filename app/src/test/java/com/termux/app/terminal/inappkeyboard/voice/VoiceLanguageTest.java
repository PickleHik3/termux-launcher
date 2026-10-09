package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

/** Layout ids and locales to Whisper language codes, and the precedence between them. */
public class VoiceLanguageTest {

    @Test
    public void layoutIdsNameTheirLanguage() {
        assertEquals("en", VoiceLanguage.fromLayoutId("latn_qwerty_us"));
        assertEquals("en", VoiceLanguage.fromLayoutId("latn_qwerty_gb"));
        assertEquals("en", VoiceLanguage.fromLayoutId("latn_qwerty_uk"));
        assertEquals("de", VoiceLanguage.fromLayoutId("latn_qwertz_de"));
        assertEquals("fr", VoiceLanguage.fromLayoutId("latn_azerty_fr"));
        assertEquals("fr", VoiceLanguage.fromLayoutId("latn_qwertz_fr_ch"));
        assertEquals("cs", VoiceLanguage.fromLayoutId("latn_qwertz_cz_diacritics"));
        assertEquals("pt", VoiceLanguage.fromLayoutId("latn_qwerty_br"));
        assertEquals("ja", VoiceLanguage.fromLayoutId("latn_qwerty_jp"));
        assertEquals("sv", VoiceLanguage.fromLayoutId("latn_qwerty_se"));
        assertEquals("haw", VoiceLanguage.fromLayoutId("latn_qwerty_haw"));
        assertEquals("uk", VoiceLanguage.fromLayoutId("cyrl_jcuken_uk"));
        assertEquals("ru", VoiceLanguage.fromLayoutId("cyrl_jcuken_ru"));
        assertEquals("tg", VoiceLanguage.fromLayoutId("cyrl_yqukeng_tj"));
        assertEquals("ko", VoiceLanguage.fromLayoutId("hang_dubeolsik_kr"));
        assertEquals("he", VoiceLanguage.fromLayoutId("hebr_1_il"));
        assertEquals("hi", VoiceLanguage.fromLayoutId("deva_phonetic_in"));
    }

    @Test
    public void scriptsWithOneCommonLanguageDecideWithoutAToken() {
        assertEquals("ar", VoiceLanguage.fromLayoutId("arab_pc"));
        assertEquals("el", VoiceLanguage.fromLayoutId("grek_qwerty"));
        assertEquals("ka", VoiceLanguage.fromLayoutId("georgian_mes"));
        assertEquals("ta", VoiceLanguage.fromLayoutId("tamil_default"));
        assertEquals("ur", VoiceLanguage.fromLayoutId("urdu_phonetic_ur"));
    }

    @Test
    public void layoutsThatNameNoLanguageGiveNothing() {
        assertNull(VoiceLanguage.fromLayoutId("latn_dvorak"));
        assertNull(VoiceLanguage.fromLayoutId("latn_colemak"));
        assertNull(VoiceLanguage.fromLayoutId("cyrl_jiuken"));
        assertNull(VoiceLanguage.fromLayoutId("latn_azerty_be"));
        assertNull(VoiceLanguage.fromLayoutId("latn_qwerty_tly"));
        assertNull(VoiceLanguage.fromLayoutId("termux_launcher_qwerty"));
        assertNull(VoiceLanguage.fromLayoutId(""));
        assertNull(VoiceLanguage.fromLayoutId(null));
    }

    @Test
    public void localesMapThroughJavasLegacyCodes() {
        assertEquals("en", VoiceLanguage.fromLocale(Locale.US));
        assertEquals("de", VoiceLanguage.fromLocale(Locale.GERMANY));
        assertEquals("he", VoiceLanguage.fromLocale(new Locale("iw")));
        assertEquals("id", VoiceLanguage.fromLocale(new Locale("in")));
        assertEquals("no", VoiceLanguage.fromLocale(new Locale("nb")));
        assertNull(VoiceLanguage.fromLocale(new Locale("tlh")));
        assertNull(VoiceLanguage.fromLocale(Locale.ROOT));
        assertNull(VoiceLanguage.fromLocale(null));
    }

    @Test
    public void anExplicitSettingWinsThenTheLayoutThenTheLocaleThenEnglish() {
        VoiceLanguage.Resolution explicit = VoiceLanguage.resolve("de", "latn_azerty_fr", Locale.US);
        assertEquals("de", explicit.code);
        assertFalse(explicit.fallback);

        VoiceLanguage.Resolution layout = VoiceLanguage.resolve("auto", "latn_azerty_fr", Locale.US);
        assertEquals("fr", layout.code);
        assertFalse(layout.fallback);

        VoiceLanguage.Resolution locale = VoiceLanguage.resolve(VoiceLanguage.AUTO, "latn_dvorak", Locale.JAPAN);
        assertEquals("ja", locale.code);
        assertFalse(locale.fallback);

        VoiceLanguage.Resolution fallback = VoiceLanguage.resolve(null, "latn_dvorak", new Locale("tlh"));
        assertEquals("en", fallback.code);
        assertTrue(fallback.fallback);

        VoiceLanguage.Resolution unknownSetting = VoiceLanguage.resolve("xx", "latn_azerty_fr", Locale.US);
        assertEquals("en", unknownSetting.code);
        assertTrue(unknownSetting.fallback);
    }
}
