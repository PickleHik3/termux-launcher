package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The compact dictionary: binary search over raw bytes, sorting and duplicates, and the gzip loader. */
public class TaiG2pDictionaryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static TaiG2pDictionary of(String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return TaiG2pDictionary.read(new ByteArrayInputStream(bytes), bytes.length);
    }

    @Test
    public void findsEveryWordOfASortedFileAndNothingElse() throws IOException {
        TaiG2pDictionary dictionary = of("a\tɐ\naa\tˈɑː\nhello\thəlˈoʊ\nworld\twˈɜːld\n");
        assertEquals(4, dictionary.size());
        assertEquals("ɐ", dictionary.get("a"));
        assertEquals("ˈɑː", dictionary.get("aa"));
        assertEquals("həlˈoʊ", dictionary.get("hello"));
        assertEquals("wˈɜːld", dictionary.get("world"));
        assertNull(dictionary.get("hell"));
        assertNull(dictionary.get("helloo"));
        assertNull(dictionary.get("b"));
        assertNull(dictionary.get(""));
        assertFalse(dictionary.contains("zebra"));
    }

    @Test
    public void unsortedInputIsSortedAndTheLaterDuplicateWins() throws IOException {
        TaiG2pDictionary dictionary = of("world\twˈɜːld\nhello\tfirst\napple\tˈæpəl\nhello\tsecond\n");
        assertEquals(3, dictionary.size());
        assertEquals("second", dictionary.get("hello"));
        assertEquals("ˈæpəl", dictionary.get("apple"));
        assertEquals("wˈɜːld", dictionary.get("world"));
    }

    @Test
    public void toleratesCrlfBlankLinesLinesWithoutATabAndNoFinalNewline() throws IOException {
        TaiG2pDictionary dictionary = of("alpha\tˈælfə\r\n\nnot a pair\r\nbeta\tbˈeɪtə");
        assertEquals(2, dictionary.size());
        assertEquals("ˈælfə", dictionary.get("alpha"));
        assertEquals("bˈeɪtə", dictionary.get("beta"));
        assertNull(dictionary.get("not a pair"));
    }

    @Test
    public void readsAStreamLongerThanItsSizeHint() throws IOException {
        // Past the 4 KB minimum buffer, so the grow path runs.
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1000; i++) text.append(String.format(java.util.Locale.ROOT, "w%04d\tipa%d\n", i, i));
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        TaiG2pDictionary dictionary = TaiG2pDictionary.read(new ByteArrayInputStream(bytes), 3);
        assertEquals(1000, dictionary.size());
        assertEquals("ipa0", dictionary.get("w0000"));
        assertEquals("ipa999", dictionary.get("w0999"));
    }

    @Test
    public void loadsAGzipFileSizedFromItsTrailer() throws IOException {
        File gz = tmp.newFile("dict.txt.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(gz))) {
            out.write("git\tɡˈɪt\nstash\tstˈæʃ\nstatus\tstˈæɾəs\n".getBytes(StandardCharsets.UTF_8));
        }
        TaiG2pDictionary dictionary = TaiG2pDictionary.loadGzip(gz);
        assertEquals(3, dictionary.size());
        assertEquals("stˈæʃ", dictionary.get("stash"));
        assertTrue(dictionary.contains("status"));
    }
}
