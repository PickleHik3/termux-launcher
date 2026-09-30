package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** File-layout detection for the three MNN diffusion families, from the names MNN 3.6.1 opens. */
public class TaiDiffusionPackageTest {
    private static final String LLM_CONFIG = "{\"llm_model\":\"llm.mnn\",\"llm_weight\":\"llm.mnn.weight\"}";

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void stableDiffusionLayoutIsDetectedAndSized() throws Exception {
        File dir = sdPackage();
        TaiDiffusionPackage.Result result = TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_AUTO);
        assertTrue(result.message, result.ok());
        assertEquals(TaiDiffusionPackage.TYPE_SD15, result.type);
        assertEquals(10L + 20L + 30L + 4L, result.totalBytes);
        assertEquals(result.totalBytes, result.peakBytes);
        assertFalse(result.supportsImageInput);
    }

    @Test
    public void externalWeightSidecarsAreCounted() throws Exception {
        File dir = sdPackage();
        write(new File(dir, "unet.mnn.weight"), 100);
        assertEquals(10L + 20L + 30L + 4L + 100L, TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_AUTO).totalBytes);
    }

    @Test
    public void taiyiSharesTheSdLayoutAndNeedsTheHint() throws Exception {
        File dir = sdPackage();
        assertEquals(TaiDiffusionPackage.TYPE_SD15, TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_AUTO).type);
        TaiDiffusionPackage.Result taiyi = TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_TAIYI);
        assertTrue(taiyi.ok());
        assertEquals(TaiDiffusionPackage.TYPE_TAIYI, taiyi.type);
    }

    @Test
    public void aMissingOrEmptyRequiredFileFailsWithItsName() throws Exception {
        File dir = sdPackage();
        assertTrue(new File(dir, "unet.mnn").delete());
        TaiDiffusionPackage.Result missing = TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_AUTO);
        assertFalse(missing.ok());
        assertEquals(TaiDiffusionPackage.FAIL_MISSING_FILE, missing.failure);
        assertTrue(missing.missing.contains("unet.mnn"));

        File dir2 = sdPackage();
        write(new File(dir2, "vae_decoder.mnn"), 0);
        TaiDiffusionPackage.Result empty = TaiDiffusionPackage.inspect(dir2, TaiDiffusionPackage.TYPE_AUTO);
        assertEquals(TaiDiffusionPackage.FAIL_MISSING_FILE, empty.failure);
        assertTrue(empty.missing.contains("vae_decoder.mnn"));
    }

    @Test
    public void aRawTokenizerPackageIsToldItNeedsConverting() throws Exception {
        File dir = sdPackage();
        assertTrue(new File(dir, "tokenizer.mtok").delete());
        write(new File(dir, "vocab.json"), 5);
        write(new File(dir, "merges.txt"), 5);
        TaiDiffusionPackage.Result result = TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_AUTO);
        assertFalse(result.ok());
        assertEquals(TaiDiffusionPackage.FAIL_TOKENIZER_MTOK_MISSING, result.failure);
        assertTrue(result.message.contains("tokenizer"));
    }

    @Test
    public void aMissingTokenizerWithoutRawFilesIsAnOrdinaryMissingFile() throws Exception {
        File dir = sdPackage();
        assertTrue(new File(dir, "tokenizer.mtok").delete());
        TaiDiffusionPackage.Result result = TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_AUTO);
        assertEquals(TaiDiffusionPackage.FAIL_MISSING_FILE, result.failure);
        assertTrue(result.missing.contains("tokenizer.mtok"));
    }

    @Test
    public void sanaLayoutIsDetectedWithItsPeakBeingTheLargerStage() throws Exception {
        File dir = sanaPackage(true);
        TaiDiffusionPackage.Result result = TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_AUTO);
        assertTrue(result.message, result.ok());
        assertEquals(TaiDiffusionPackage.TYPE_SANA, result.type);
        assertTrue(result.supportsImageInput);
        // llm/: config + meta 3 + llm.mnn 500 + weight 600 + tokenizer 7; the diffusion graphs are smaller.
        assertEquals(LLM_CONFIG.length() + 3L + 500L + 600L + 7L, result.peakBytes);
        assertTrue(result.totalBytes > result.peakBytes);
    }

    @Test
    public void sanaWithoutAVaeEncoderIsValidButHasNoImageInput() throws Exception {
        TaiDiffusionPackage.Result result = TaiDiffusionPackage.inspect(sanaPackage(false), TaiDiffusionPackage.TYPE_AUTO);
        assertTrue(result.ok());
        assertFalse(result.supportsImageInput);
    }

    @Test
    public void sanaNeedsItsLlmFiles() throws Exception {
        File dir = sanaPackage(true);
        assertTrue(new File(dir, "llm/meta_queries.mnn").delete());
        TaiDiffusionPackage.Result result = TaiDiffusionPackage.inspect(dir, TaiDiffusionPackage.TYPE_AUTO);
        assertEquals(TaiDiffusionPackage.FAIL_MISSING_FILE, result.failure);
        assertTrue(result.missing.contains("llm/meta_queries.mnn"));
    }

    @Test
    public void aHintThatContradictsTheFilesIsRefused() throws Exception {
        assertEquals(TaiDiffusionPackage.FAIL_TYPE_MISMATCH,
            TaiDiffusionPackage.inspect(sdPackage(), TaiDiffusionPackage.TYPE_SANA).failure);
        assertEquals(TaiDiffusionPackage.FAIL_TYPE_MISMATCH,
            TaiDiffusionPackage.inspect(sanaPackage(true), TaiDiffusionPackage.TYPE_SD15).failure);
    }

    @Test
    public void notADirectoryAndAnUnrelatedDirectoryAreRefused() throws Exception {
        assertEquals(TaiDiffusionPackage.FAIL_NOT_A_DIRECTORY,
            TaiDiffusionPackage.inspect(new File(temp.getRoot(), "nope"), TaiDiffusionPackage.TYPE_AUTO).failure);
        assertEquals(TaiDiffusionPackage.FAIL_UNRECOGNISED,
            TaiDiffusionPackage.inspect(temp.newFolder("empty"), TaiDiffusionPackage.TYPE_AUTO).failure);
    }

    @Test
    public void typeNamesRoundTrip() {
        assertEquals(TaiDiffusionPackage.TYPE_SD15, TaiDiffusionPackage.parseType("SD15"));
        assertEquals(TaiDiffusionPackage.TYPE_TAIYI, TaiDiffusionPackage.parseType("taiyi"));
        assertEquals(TaiDiffusionPackage.TYPE_SANA, TaiDiffusionPackage.parseType(" sana "));
        assertEquals(TaiDiffusionPackage.TYPE_AUTO, TaiDiffusionPackage.parseType(""));
        assertEquals(-2, TaiDiffusionPackage.parseType("sd35"));
        assertEquals("taiyi", TaiDiffusionPackage.typeName(TaiDiffusionPackage.TYPE_TAIYI));
    }

    @Test
    public void aSyntheticSpecIsAnImageModelNotAChatModel() throws Exception {
        File dir = sdPackage();
        TaiModelSpec spec = TaiDiffusionPackage.syntheticSpec(dir.getAbsolutePath(), TaiDiffusionPackage.TYPE_TAIYI, 64L);
        assertNotNull(spec);
        assertTrue(spec.isImageGeneration());
        assertEquals(TaiModelSpec.BACKEND_MNN_DIFFUSION, spec.backend);
        assertEquals("taiyi", spec.architecture);
        assertFalse(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    private File sdPackage() throws IOException {
        File dir = temp.newFolder();
        write(new File(dir, "text_encoder.mnn"), 10);
        write(new File(dir, "unet.mnn"), 20);
        write(new File(dir, "vae_decoder.mnn"), 30);
        write(new File(dir, "tokenizer.mtok"), 4);
        return dir;
    }

    private File sanaPackage(boolean encoder) throws IOException {
        File dir = temp.newFolder();
        write(new File(dir, "connector.mnn"), 10);
        write(new File(dir, "projector.mnn"), 10);
        write(new File(dir, "transformer.mnn"), 100);
        write(new File(dir, "vae_decoder.mnn"), 30);
        if (encoder) write(new File(dir, "vae_encoder.mnn"), 30);
        File llm = new File(dir, "llm");
        assertTrue(llm.mkdirs());
        write(new File(llm, "config.json"), 0, LLM_CONFIG);
        write(new File(llm, "meta_queries.mnn"), 3);
        write(new File(llm, "llm.mnn"), 500);
        write(new File(llm, "llm.mnn.weight"), 600);
        write(new File(llm, "tokenizer.txt"), 7);
        return dir;
    }

    private static void write(File file, int bytes) throws IOException {
        write(file, bytes, null);
    }

    private static void write(File file, int bytes, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (text != null) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            } else {
                out.write(new byte[bytes]);
            }
        }
    }
}
