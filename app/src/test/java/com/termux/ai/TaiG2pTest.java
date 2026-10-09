package com.termux.ai;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The G2P against ids the PC prototype produced ({@code scripts/tts-eval/g2p.py}, Google frontend,
 * Kitten punctuation), with a fixture dictionary cut from {@code g2p_dict.txt.gz} for exactly the
 * words these sentences use, and a stub in place of the neural phonemizer. Where a sentence has
 * nothing the normaliser changes, the ids must match the prototype's exactly.
 */
public class TaiG2pTest {

    /** A neural phonemizer that records what it was asked and answers a fixed IPA. */
    private static final class StubNeural implements TaiG2p.NeuralPhonemizer {
        final List<String> asked = new ArrayList<>();

        @Override
        public String phonemize(String lowerWord) {
            asked.add(lowerWord);
            return "zˈɪk";
        }
    }

    private static TaiG2pDictionary fixture() throws IOException {
        try (InputStream in = TaiG2pTest.class.getResourceAsStream("/tts/g2p_dict_fixture.txt")) {
            assertNotNull("fixture dictionary on the test classpath", in);
            return TaiG2pDictionary.read(in, 0);
        }
    }

    private static TaiG2p g2p(StubNeural neural) throws IOException {
        TaiG2pDictionary dictionary = fixture();
        return new TaiG2p(() -> dictionary, neural);
    }

    @Test
    public void helloWorldMatchesThePrototype() throws IOException {
        TaiG2p.Result result = g2p(new StubNeural()).phonemize("Hello world,");
        assertEquals("həlˈoʊ wˈɜːld ,", result.ipa);
        assertArrayEquals(new int[] {50, 83, 54, 156, 57, 135, 16, 65, 156, 87, 158, 54, 46, 16, 3}, result.ids);
        assertEquals(2, result.dictionaryWords);
        assertEquals(0, result.neuralWords);
    }

    @Test
    public void questionKeepsItsMarkAsTheLastToken() throws IOException {
        TaiG2p.Result result = g2p(new StubNeural()).phonemize("Did you mean git status, or git stash?");
        assertArrayEquals(new int[] {46, 156, 102, 46, 16, 52, 63, 158, 16, 55, 156, 51, 158, 56, 16, 92, 156, 102, 62,
            16, 61, 62, 156, 72, 125, 83, 61, 16, 3, 16, 76, 158, 123, 16, 92, 156, 102, 62, 16, 61, 62, 156, 72, 131,
            16, 6}, result.ids);
        assertEquals(6, result.ids[result.ids.length - 1]); // "?"
    }

    @Test
    public void numbersAreReadAsWordsLikeTheKotlinSample() throws IOException {
        assertArrayEquals(new int[] {102, 62, 16, 62, 156, 135, 53, 16, 48, 156, 76, 158, 123, 125, 51, 16, 62, 156, 63,
            158, 16, 61, 156, 86, 53, 83, 56, 46, 68, 16, 3}, g2p(new StubNeural()).phonemize("It took 42 seconds,").ids);
        assertEquals("wˌʌn θˈaʊzənd tˈuː hˈʌndɹɪd θˈɜːɾi fˈoːɹ pˈɔɪnt fˈaɪv sˈɛkəndz ,",
            g2p(new StubNeural()).phonemize("1,234.5 seconds,").ipa);
    }

    @Test
    public void shortCapsRunsAreSpelledLetterByLetter() throws IOException {
        TaiG2p.Result result = g2p(new StubNeural()).phonemize("The BBC,");
        assertArrayEquals(new int[] {81, 83, 16, 44, 156, 51, 158, 44, 156, 51, 158, 61, 156, 51, 158, 16, 3}, result.ids);
    }

    @Test
    public void knownLongCapsWordsAreReadAsWords() throws IOException {
        StubNeural neural = new StubNeural();
        TaiG2p.Result result = g2p(neural).phonemize("RADAR is ready,");
        assertEquals("radar is ready,", result.text);
        assertTrue(neural.asked.isEmpty());
    }

    @Test
    public void wordsMissingFromTheDictionaryGoToTheNeuralPhonemizerOnceEach() throws IOException {
        StubNeural neural = new StubNeural();
        TaiG2p g2p = g2p(neural);
        TaiG2p.Result first = g2p.phonemize("Zyxquor world,");
        g2p.phonemize("Zyxquor again,");
        assertEquals("zˈɪk wˈɜːld ,", first.ipa);
        assertEquals(1, first.neuralWords);
        // "zyxquor" is asked once and cached; "again" is not in the fixture, so it is asked too.
        assertEquals(2, neural.asked.size());
        assertEquals("zyxquor", neural.asked.get(0));
    }

    @Test
    public void charactersOutsideTheTokenGrammarAreDropped() throws IOException {
        TaiG2p.Result result = g2p(new StubNeural()).phonemize("hello ~ # world,");
        assertEquals("həlˈoʊ wˈɜːld ,", result.ipa);
    }

    @Test
    public void symbolTableIsThe178SymbolKittenTableWithTheLaterApostropheWinning() {
        assertEquals(178, TaiG2p.SYMBOLS.length());
        assertArrayEquals(new int[] {16, 3, 6, 176}, TaiG2p.toIds(" ,?'"));
        // Characters the table lacks vanish rather than failing.
        assertArrayEquals(new int[] {43}, TaiG2p.toIds("a\u0000€"));
    }

    @Test
    public void dictionaryIsLoadedOnceAndOnlyWhenFirstNeeded() throws IOException {
        int[] loads = {0};
        TaiG2pDictionary dictionary = fixture();
        TaiG2p g2p = new TaiG2p(() -> {
            loads[0]++;
            return dictionary;
        }, new StubNeural());
        assertEquals(0, loads[0]);
        g2p.phonemize("Hello,");
        g2p.phonemize("world,");
        assertEquals(1, loads[0]);
    }
}
