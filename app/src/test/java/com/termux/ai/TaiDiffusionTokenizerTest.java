package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Installing the bundled CLIP tokenizer beside a Stable Diffusion package, and refusing any other. */
public class TaiDiffusionTokenizerTest {
    private static final byte[] VOCAB = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MERGES = "a b\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MTOK = {1, 2, 3, 4, 5};

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private static TaiDiffusionTokenizer.Source bundled(byte[] payload) {
        return () -> new java.io.ByteArrayInputStream(payload);
    }

    private File folder(byte[] vocab, byte[] merges) throws IOException {
        File dir = temp.newFolder();
        if (vocab != null) Files.write(new File(dir, "vocab.json").toPath(), vocab);
        if (merges != null) Files.write(new File(dir, "merges.txt").toPath(), merges);
        return dir;
    }

    @Test
    public void matchingRawFilesGetTheBundledTokenizerInstalled() throws Exception {
        File dir = folder(VOCAB, MERGES);
        TaiDiffusionTokenizer.Result result = TaiDiffusionTokenizer.ensure(dir, bundled(MTOK), sha(VOCAB), sha(MERGES), sha(MTOK));
        assertEquals(TaiDiffusionTokenizer.Outcome.INSTALLED, result.outcome);
        assertTrue(result.proceed());
        assertArrayEquals(MTOK, Files.readAllBytes(new File(dir, "tokenizer.mtok").toPath()));
        assertFalse(new File(dir, "tokenizer.mtok.part").exists());
    }

    @Test
    public void otherVocabularyIsRefusedWithAProductMessageAndNothingIsWritten() throws Exception {
        File dir = folder("{\"other\":2}".getBytes(StandardCharsets.UTF_8), MERGES);
        TaiDiffusionTokenizer.Result result = TaiDiffusionTokenizer.ensure(dir, bundled(MTOK), sha(VOCAB), sha(MERGES), sha(MTOK));
        assertEquals(TaiDiffusionTokenizer.Outcome.INCOMPATIBLE, result.outcome);
        assertFalse(result.proceed());
        assertTrue(result.message, result.message.contains("converted"));
        assertFalse(result.message.contains("HTTP"));
        assertFalse(new File(dir, "tokenizer.mtok").exists());
    }

    @Test
    public void otherMergesAreRefusedToo() throws Exception {
        File dir = folder(VOCAB, "x y\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(TaiDiffusionTokenizer.Outcome.INCOMPATIBLE,
            TaiDiffusionTokenizer.ensure(dir, bundled(MTOK), sha(VOCAB), sha(MERGES), sha(MTOK)).outcome);
    }

    @Test
    public void anExistingTokenizerIsLeftUntouched() throws Exception {
        File dir = folder("{\"other\":2}".getBytes(StandardCharsets.UTF_8), MERGES);
        byte[] own = {9, 9, 9};
        Files.write(new File(dir, "tokenizer.mtok").toPath(), own);
        TaiDiffusionTokenizer.Result result = TaiDiffusionTokenizer.ensure(dir, bundled(MTOK), sha(VOCAB), sha(MERGES), sha(MTOK));
        assertEquals(TaiDiffusionTokenizer.Outcome.PRESENT, result.outcome);
        assertTrue(result.proceed());
        assertArrayEquals(own, Files.readAllBytes(new File(dir, "tokenizer.mtok").toPath()));
    }

    @Test
    public void withoutRawFilesThereIsNothingToDecideFrom() throws Exception {
        File dir = folder(VOCAB, null);
        TaiDiffusionTokenizer.Result result = TaiDiffusionTokenizer.ensure(dir, bundled(MTOK), sha(VOCAB), sha(MERGES), sha(MTOK));
        assertEquals(TaiDiffusionTokenizer.Outcome.NO_SOURCE, result.outcome);
        assertTrue(result.proceed());
        assertFalse(new File(dir, "tokenizer.mtok").exists());
    }

    @Test
    public void aBundledTokenizerThatFailsItsCheckIsNotInstalled() throws Exception {
        File dir = folder(VOCAB, MERGES);
        TaiDiffusionTokenizer.Result result = TaiDiffusionTokenizer.ensure(dir, bundled(new byte[]{7, 7}), sha(VOCAB), sha(MERGES), sha(MTOK));
        assertEquals(TaiDiffusionTokenizer.Outcome.UNAVAILABLE, result.outcome);
        assertFalse(result.proceed());
        assertFalse(new File(dir, "tokenizer.mtok").exists());
    }

    @Test
    public void theBundledAssetHasTheRecordedHash() throws Exception {
        File asset = new File("src/main/assets/" + TaiDiffusionTokenizer.ASSET);
        assertTrue(asset.getAbsolutePath(), asset.isFile());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(asset)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        assertEquals(TaiDiffusionTokenizer.MTOK_SHA256, sha(out.toByteArray()));
    }

    @Test
    public void theAssetNameSurvivesPackaging() {
        // The Android build drops a ".gz" suffix and stores the asset decompressed, so a ".gz"
        // name is never found on the phone even though this module's tests can read the file.
        assertFalse(TaiDiffusionTokenizer.ASSET, TaiDiffusionTokenizer.ASSET.endsWith(".gz"));
    }
}
