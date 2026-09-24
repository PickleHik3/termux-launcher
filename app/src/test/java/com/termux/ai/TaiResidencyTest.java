package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The resident table and what the budget makes of it. Sizes are the catalog's: gemma-4-e4b on the
 * GPU at 4k, EmbeddingGemma 300M, Qwen3 Embedding 0.6B (MNN); the phone is pong (12 GB class).
 */
@RunWith(RobolectricTestRunner.class)
public class TaiResidencyTest {

    private static final long PONG_TOTAL = 11_530_736L * 1024L;
    private static final long E4B = 3_659_530_240L;
    private static final long EMBEDDING_GEMMA = 183_329_528L;
    private static final long QWEN3_EMBEDDING = 377_998_519L;

    @Test
    public void registerReplacesTheSameModelAndDeregisterForgetsIt() {
        TaiResidency residency = new TaiResidency();
        residency.register(chatEntry("e4b", "gpu", 4096));
        residency.register(chatEntry("e4b", "cpu", 8192));

        List<TaiResidency.Entry> chat = ofKind(residency, TaiResidency.Kind.CHAT);
        assertEquals(1, chat.size());
        assertEquals("cpu", chat.get(0).accelerator);
        assertEquals(8192, chat.get(0).window);

        residency.deregister(TaiResidency.Kind.CHAT, "e4b");
        assertTrue(ofKind(residency, TaiResidency.Kind.CHAT).isEmpty());
        assertFalse(residency.isResident(TaiResidency.Kind.CHAT, "e4b"));
        // Unknown ids are a no-op, not an error.
        residency.deregister(TaiResidency.Kind.EMBEDDING, "never-loaded");
    }

    @Test
    public void theSameIdUnderAnotherKindIsAnotherResident() {
        TaiResidency residency = new TaiResidency();
        residency.register(chatEntry("shared-id", "gpu", 4096));
        residency.register(TaiResidency.Entry.embedding(embeddingSpec("shared-id", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA), 1024));

        assertTrue(residency.isResident(TaiResidency.Kind.CHAT, "shared-id"));
        assertTrue(residency.isResident(TaiResidency.Kind.EMBEDDING, "shared-id"));
        residency.deregister(TaiResidency.Kind.CHAT, "shared-id");
        assertFalse(residency.isResident(TaiResidency.Kind.CHAT, "shared-id"));
        assertTrue(residency.isResident(TaiResidency.Kind.EMBEDDING, "shared-id"));
    }

    /** The process keeps ~330 MB after its first chat load; the table says so from then on. */
    @Test
    public void theFirstChatLoadBringsInTheRuntimeBaselineWhichNeverLeaves() {
        TaiResidency residency = new TaiResidency();
        residency.register(TaiResidency.Entry.embedding(embeddingSpec("emb", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA), 1024));
        assertTrue(ofKind(residency, TaiResidency.Kind.RUNTIME).isEmpty());

        residency.register(chatEntry("e4b", "gpu", 4096));
        List<TaiResidency.Entry> baseline = ofKind(residency, TaiResidency.Kind.RUNTIME);
        assertEquals(1, baseline.size());
        assertEquals(TaiResidency.RUNTIME_ID, baseline.get(0).modelId);
        assertEquals(TaiResidency.RUNTIME_BASELINE_BYTES, baseline.get(0).estimatedBytes);

        residency.deregister(TaiResidency.Kind.CHAT, "e4b");
        residency.register(chatEntry("e2b", "cpu", 4096));
        residency.deregister(TaiResidency.Kind.CHAT, "e2b");
        residency.deregister(TaiResidency.Kind.RUNTIME, TaiResidency.RUNTIME_ID);
        assertEquals(1, ofKind(residency, TaiResidency.Kind.RUNTIME).size());
    }

    @Test
    public void busyStampsLastUseAndLeavesOtherResidentsAlone() throws Exception {
        TaiResidency residency = new TaiResidency();
        residency.register(chatEntry("e4b", "gpu", 4096));
        residency.register(TaiResidency.Entry.embedding(embeddingSpec("emb", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA), 1024));
        long registeredAt = find(residency, TaiResidency.Kind.CHAT, "e4b").lastUsedMs;
        Thread.sleep(2L);

        residency.setBusy(TaiResidency.Kind.CHAT, "e4b", true);
        TaiResidency.Entry busy = find(residency, TaiResidency.Kind.CHAT, "e4b");
        assertTrue(busy.busy);
        assertTrue(busy.lastUsedMs > registeredAt);
        assertFalse(find(residency, TaiResidency.Kind.EMBEDDING, "emb").busy);

        residency.setBusy(TaiResidency.Kind.CHAT, "e4b", false);
        assertFalse(find(residency, TaiResidency.Kind.CHAT, "e4b").busy);
        // A null id (nothing loaded) and an unknown id are no-ops.
        residency.setBusy(TaiResidency.Kind.CHAT, null, true);
        residency.setBusy(TaiResidency.Kind.CHAT, "gone", true);
        assertEquals(3, residency.snapshot().size());
    }

    @Test
    public void aSnapshotIsImmutableAndDoesNotFollowLaterWrites() {
        TaiResidency residency = new TaiResidency();
        residency.register(chatEntry("e4b", "gpu", 4096));
        List<TaiResidency.Entry> before = residency.snapshot();
        assertSame(before, residency.snapshot());

        residency.register(TaiResidency.Entry.embedding(embeddingSpec("emb", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA), 1024));
        assertEquals(2, before.size());
        assertEquals(3, residency.snapshot().size());
        try {
            before.add(chatEntry("x", "cpu", 4096));
            throw new AssertionError("snapshot accepted a write");
        } catch (UnsupportedOperationException expected) {
        }
    }

    /** A chat load closes the resident chat model first, so that memory is credited back. */
    @Test
    public void aChatLoadIsCreditedTheResidentChatModelWhicheverBackendHoldsIt() {
        TaiResidency residency = new TaiResidency();
        TaiResidency.Entry mnnChat = TaiResidency.Entry.chat(chatSpec("qwen-mnn", TaiModelSpec.BACKEND_MNN_LLM, 971_254_765L), TaiModelSpec.BACKEND_MNN_LLM, "cpu", 4096);
        residency.register(mnnChat);

        long available = 3_000_000_000L;
        long credited = TaiResidency.creditedAvailable(available, residency.snapshot(), TaiResidency.Kind.CHAT, TaiModelSpec.BACKEND_LITERT_LM);
        assertEquals(available + mnnChat.estimatedBytes, credited);
        // Unknown free memory stays unknown: the plan must fall back to the unmeasured floor.
        assertEquals(0L, TaiResidency.creditedAvailable(0L, residency.snapshot(), TaiResidency.Kind.CHAT, TaiModelSpec.BACKEND_LITERT_LM));
    }

    @Test
    public void embeddingAndBaselineResidentsAreNotCreditedToAChatLoad() {
        TaiResidency residency = new TaiResidency();
        residency.register(chatEntry("e4b", "gpu", 4096));
        residency.register(TaiResidency.Entry.embedding(embeddingSpec("emb", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA), 1024));
        long chatBytes = find(residency, TaiResidency.Kind.CHAT, "e4b").estimatedBytes;

        long available = 3_000_000_000L;
        assertEquals(available + chatBytes,
            TaiResidency.creditedAvailable(available, residency.snapshot(), TaiResidency.Kind.CHAT, TaiModelSpec.BACKEND_LITERT_LM));
    }

    @Test
    public void anEmbeddingLoadIsCreditedOnlyItsOwnBackendsEmbeddingResident() {
        TaiResidency residency = new TaiResidency();
        residency.register(chatEntry("e4b", "gpu", 4096));
        TaiResidency.Entry liteRt = TaiResidency.Entry.embedding(embeddingSpec("emb", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA), 1024);
        TaiResidency.Entry mnn = TaiResidency.Entry.embedding(embeddingSpec("qwen-emb", TaiModelSpec.BACKEND_MNN_LLM, QWEN3_EMBEDDING), 0);
        residency.register(liteRt);
        residency.register(mnn);

        long available = 3_000_000_000L;
        assertEquals(available + liteRt.estimatedBytes,
            TaiResidency.creditedAvailable(available, residency.snapshot(), TaiResidency.Kind.EMBEDDING, TaiModelSpec.BACKEND_LITERT_LM));
        assertEquals(available + mnn.estimatedBytes,
            TaiResidency.creditedAvailable(available, residency.snapshot(), TaiResidency.Kind.EMBEDDING, TaiModelSpec.BACKEND_MNN_LLM));
        assertEquals(available,
            TaiResidency.creditedAvailable(available, residency.snapshot(), TaiResidency.Kind.STT, null));
    }

    /**
     * The budget with residents: E4B on the GPU at 4k fits with what is free plus the chat model
     * it replaces, and does not fit on the same free memory when the resident is an embedding
     * model instead. GPU only, so the CPU floor cannot rescue the uncredited case.
     */
    @Test
    public void thePlanCreditsAChatReplacementButNotAnEmbeddingResident() {
        long reserve = TaiLoadBudget.reserveBytes(PONG_TOTAL);
        long e4bGpu4k = TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B, false, 4096);
        long shortBy = 500_000_000L;
        long available = reserve + e4bGpu4k - shortBy;

        TaiResidency withChat = new TaiResidency();
        withChat.register(chatEntry("e2b", "gpu", 4096));
        assertTrue(find(withChat, TaiResidency.Kind.CHAT, "e2b").estimatedBytes > shortBy);
        TaiLoadBudget.Plan replacing = TaiLoadBudget.plan(request(
            TaiResidency.creditedAvailable(available, withChat.snapshot(), TaiResidency.Kind.CHAT, TaiModelSpec.BACKEND_LITERT_LM)));
        assertTrue(replacing.fits);
        assertEquals("gpu", replacing.accelerator);

        TaiResidency withEmbedding = new TaiResidency();
        withEmbedding.register(TaiResidency.Entry.embedding(embeddingSpec("emb", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA), 1024));
        TaiLoadBudget.Plan alongside = TaiLoadBudget.plan(request(
            TaiResidency.creditedAvailable(available, withEmbedding.snapshot(), TaiResidency.Kind.CHAT, TaiModelSpec.BACKEND_LITERT_LM)));
        assertFalse(alongside.fits);
    }

    @Test
    public void embeddingEstimatesAreTheFileTimesTheBackendsFactor() {
        assertEquals(EMBEDDING_GEMMA * 13L / 10L,
            TaiResidency.embeddingEstimateBytes(embeddingSpec("emb", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA)));
        assertEquals(QWEN3_EMBEDDING,
            TaiResidency.embeddingEstimateBytes(embeddingSpec("qwen-emb", TaiModelSpec.BACKEND_MNN_LLM, QWEN3_EMBEDDING)));
    }

    /** An embedding model that does not fit is refused the way a chat model is: 409 insufficient_memory. */
    @Test
    public void anEmbeddingThatDoesNotFitIsRefusedWithTheChatRefusal() throws Exception {
        TaiModelSpec spec = embeddingSpec("emb", TaiModelSpec.BACKEND_LITERT_LM, EMBEDDING_GEMMA);
        long reserve = TaiLoadBudget.reserveBytes(PONG_TOTAL);
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(TaiResidency.embeddingEstimateBytes(spec), "cpu",
            PONG_TOTAL, reserve + 100_000_000L);
        assertFalse(plan.fits);

        JSONObject refusal = TaiManager.openAiError(TaiManager.insufficientMemory(spec.displayName, plan));
        assertEquals(409, refusal.getInt("_statusCode"));
        assertEquals("insufficient_memory", refusal.getJSONObject("error").getString("code"));
        JSONObject budget = refusal.getJSONObject("tai").getJSONObject("memoryBudget");
        assertFalse(budget.getBoolean("fits"));
        assertEquals(EMBEDDING_GEMMA * 13L / 10L, budget.getLong("estimatedBytes"));
    }

    @Test
    public void theJsonTableCarriesWhatTheDeviceCheckReads() throws Exception {
        TaiResidency residency = new TaiResidency();
        residency.register(chatEntry("e4b", "gpu", 4096));
        residency.setBusy(TaiResidency.Kind.CHAT, "e4b", true);

        JSONArray json = residency.toJson();
        assertEquals(2, json.length());
        JSONObject chat = json.getJSONObject(0);
        assertEquals("e4b", chat.getString("id"));
        assertEquals("chat", chat.getString("kind"));
        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, chat.getString("backend"));
        assertEquals("gpu", chat.getString("accelerator"));
        assertTrue(chat.getBoolean("busy"));
        assertTrue(chat.getLong("lastUsedMs") > 0L);
        assertTrue(chat.isNull("measuredBytes"));
        assertEquals(TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B, false, 4096), chat.getLong("estimatedBytes"));
        assertEquals("runtime", json.getJSONObject(1).getString("kind"));
        assertNull(find(residency, TaiResidency.Kind.CHAT, "e4b").measuredBytes);
    }

    private static TaiLoadBudget.Request request(long available) {
        return new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, E4B, false, PONG_TOTAL, available,
            Collections.singletonList("gpu"), 4096, null, 0);
    }

    private static TaiResidency.Entry chatEntry(String id, String accelerator, int window) {
        long size = "e2b".equals(id) ? 2_588_147_712L : E4B;
        return TaiResidency.Entry.chat(chatSpec(id, TaiModelSpec.BACKEND_LITERT_LM, size), TaiModelSpec.BACKEND_LITERT_LM, accelerator, window);
    }

    private static List<TaiResidency.Entry> ofKind(TaiResidency residency, TaiResidency.Kind kind) {
        List<TaiResidency.Entry> matches = new ArrayList<>();
        for (TaiResidency.Entry entry : residency.snapshot()) {
            if (entry.kind == kind) matches.add(entry);
        }
        return matches;
    }

    private static TaiResidency.Entry find(TaiResidency residency, TaiResidency.Kind kind, String id) {
        for (TaiResidency.Entry entry : residency.snapshot()) {
            if (entry.kind == kind && entry.modelId.equals(id)) return entry;
        }
        throw new AssertionError("no " + kind + " resident " + id);
    }

    @Test
    public void fileBytes_sumsAnMnnPackageDirectory() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("mnn-embed").toFile();
        java.nio.file.Files.write(new java.io.File(dir, "config.json").toPath(), new byte[10]);
        java.nio.file.Files.write(new java.io.File(dir, "llm.mnn.weight").toPath(), new byte[1000]);

        assertEquals(1010L, TaiResidency.fileBytes(spec("dir-embed", TaiModelSpec.BACKEND_MNN_LLM,
            dir.getAbsolutePath(), 0L, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)));
        assertEquals(1010L, TaiResidency.fileBytes(spec("cfg-embed", TaiModelSpec.BACKEND_MNN_LLM,
            new java.io.File(dir, "config.json").getAbsolutePath(), 0L, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)));
        // A downloaded package's spec carries config.json's length as sizeBytes; the disk wins.
        assertEquals(1010L, TaiResidency.fileBytes(spec("dl-embed", TaiModelSpec.BACKEND_MNN_LLM,
            new java.io.File(dir, "config.json").getAbsolutePath(), 10L, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)));
    }

    @Test
    public void onlyAnSttLoadMayEvictIdleChat() {
        TaiResidency.Entry chat = new TaiResidency.Entry("e4b", TaiResidency.Kind.CHAT, "litert-lm", "gpu", 4096,
            3_000_000_000L, null, 1L, false);
        TaiResidency.Entry emb = new TaiResidency.Entry("emb", TaiResidency.Kind.EMBEDDING, "mnn-llm", "cpu", 0,
            300_000_000L, null, 2L, false);
        List<TaiResidency.Entry> residents = java.util.Arrays.asList(chat, emb);

        assertEquals(java.util.Arrays.asList(emb, chat),
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.STT, null));
        assertEquals(Collections.singletonList(emb),
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.EMBEDDING, "litert-lm"));
        assertTrue(TaiResidency.evictionCandidates(residents, TaiResidency.Kind.CHAT, null).contains(emb));
        assertFalse(TaiResidency.evictionCandidates(residents, TaiResidency.Kind.CHAT, null).contains(chat));
    }

    private static TaiModelSpec chatSpec(String id, String backend, long sizeBytes) {
        String path = TaiModelSpec.BACKEND_MNN_LLM.equals(backend) ? "/models/" + id + "/config.json" : "/models/" + id + "/model.litertlm";
        return spec(id, backend, path, sizeBytes, TaiModelSpec.CAPABILITY_TEXT_CHAT);
    }

    private static TaiModelSpec embeddingSpec(String id, String backend, long sizeBytes) {
        String path = TaiModelSpec.BACKEND_MNN_LLM.equals(backend) ? "/models/" + id + "/config.json" : "/models/" + id + "/model.tflite";
        return spec(id, backend, path, sizeBytes, TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
    }

    private static TaiModelSpec spec(String id, String backend, String path, long sizeBytes, String capability) {
        String format = TaiModelSpec.BACKEND_MNN_LLM.equals(backend) ? TaiModelSpec.FORMAT_MNN : TaiModelSpec.FORMAT_LITERTLM;
        return new TaiModelSpec(
            id,
            id,
            "Test model",
            "test",
            path,
            "test",
            sizeBytes,
            new LinkedHashSet<>(Collections.singleton(capability)),
            false,
            null,
            backend,
            format,
            null,
            null,
            4096,
            0,
            null
        );
    }
}
