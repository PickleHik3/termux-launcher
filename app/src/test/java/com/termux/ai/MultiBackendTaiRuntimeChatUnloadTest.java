package com.termux.ai;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link MultiBackendTaiRuntime#unloadChatModel}: the app sort gives back the chat model it loaded and
 * nothing else. Speech in, speech out and embeddings stay resident, and a chat model that is no longer
 * the sort's is left alone.
 */
@RunWith(RobolectricTestRunner.class)
public class MultiBackendTaiRuntimeChatUnloadTest {
    private static final String SORT_MODEL = "sort-model";

    private RecordingRuntime liteRt;
    private RecordingRuntime mnn;
    private MultiBackendTaiRuntime router;
    private RecordingEmbeddings embeddings;

    @Before
    public void setUp() throws Exception {
        liteRt = new RecordingRuntime(loaded(SORT_MODEL, false));
        mnn = new RecordingRuntime(TaiRuntimeState.fromJson(null));
        router = new MultiBackendTaiRuntime(liteRt, mnn);
        embeddings = new RecordingEmbeddings("embed-model");
        setField(router, "litertLmEmbeddings", embeddings);
        holdSpeech("whisperStt", TaiResidency.Kind.STT, "whisper-model");
        holdSpeech("tts", TaiResidency.Kind.TTS, "kitten-model");
    }

    @Test
    public void theSortModelGoesAndSpeechAndEmbeddingsStay() throws Exception {
        JSONObject result = router.unloadChatModel(SORT_MODEL);

        assertTrue(result.getBoolean("ok"));
        assertTrue(liteRt.unloadCalled);
        assertEquals(0, embeddings.closes);
        assertTrue(router.residency().isResident(TaiResidency.Kind.STT, "whisper-model"));
        assertTrue(router.residency().isResident(TaiResidency.Kind.TTS, "kitten-model"));
        assertFalse("speech in progress is not interrupted", (Boolean) field(field(router, "tts"), "interruptRequested"));

        // The full unload, by contrast, closes all of them: the fixture holds what it says it holds.
        router.unload();
        assertEquals(1, embeddings.closes);
        assertFalse(router.residency().isResident(TaiResidency.Kind.STT, "whisper-model"));
        assertFalse(router.residency().isResident(TaiResidency.Kind.TTS, "kitten-model"));
    }

    @Test
    public void aDifferentChatModelLoadedSinceIsLeftAlone() throws Exception {
        liteRt.state = loaded("assistant-model", false);

        JSONObject result = router.unloadChatModel(SORT_MODEL);

        assertTrue(result.getBoolean("ok"));
        assertFalse(result.getBoolean("unloaded"));
        assertFalse(liteRt.unloadCalled);
        assertFalse(mnn.unloadCalled);
        assertEquals(0, embeddings.closes);
    }

    @Test
    public void aLoadInProgressIsNeitherCancelledNorUnloaded() throws Exception {
        liteRt.state = new TaiRuntimeState(false, null, "fake", "loading", "Loading.", "none", null, null,
            false, null, 0L, 0L, 0L, 0L, 0L);

        JSONObject result = router.unloadChatModel(SORT_MODEL);

        assertFalse(result.getBoolean("unloaded"));
        assertFalse(liteRt.cancelCalled);
        assertFalse(liteRt.unloadCalled);
    }

    @Test
    public void aGenerationOnTheModelIsLeftToFinish() throws Exception {
        liteRt.state = loaded(SORT_MODEL, true);

        assertFalse(router.unloadChatModel(SORT_MODEL).getBoolean("unloaded"));
        assertFalse(liteRt.unloadCalled);
        assertFalse(liteRt.cancelCalled);
    }

    /** Marks a speech runtime as holding {@code modelId}, as its own load would, so its close shows. */
    private void holdSpeech(String runtimeField, TaiResidency.Kind kind, String modelId) throws Exception {
        setField(field(router, runtimeField), "loadedModelId", modelId);
        router.residency().register(new TaiResidency.Entry(modelId, kind, TaiModelSpec.BACKEND_LITERT_LM, "cpu",
            0, 1024L, null, 0L, false));
    }

    private static TaiRuntimeState loaded(String modelId, boolean generating) {
        return new TaiRuntimeState(true, modelId, "fake", generating ? "generating" : "loaded", "Loaded.",
            TaiModelSpec.BACKEND_LITERT_LM, null, null, generating, generating ? "generation-1" : null,
            0L, 0L, 0L, 0L, 0L);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingEmbeddings extends LiteRtLmEmbeddingRuntime {
        int closes;
        private String held;

        RecordingEmbeddings(String held) {
            super(new TaiResidency());
            this.held = held;
        }

        @Override
        String loadedModelId() {
            return held;
        }

        @Override
        public void close() {
            closes++;
            held = null;
        }
    }

    private static final class RecordingRuntime implements TaiRuntime {
        volatile TaiRuntimeState state;
        boolean unloadCalled;
        boolean cancelCalled;

        RecordingRuntime(TaiRuntimeState state) {
            this.state = state;
        }

        @Override public TaiRuntimeState getState() { return state; }
        @Override public boolean isModelLoaded(String modelId) { return state.loaded && modelId.equals(state.loadedModelId); }
        @Override public JSONObject load(TaiModelSpec modelSpec, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject unload() throws JSONException {
            unloadCalled = true;
            state = TaiRuntimeState.fromJson(null);
            return ok();
        }
        @Override public JSONObject keepWarm(TaiModelSpec modelSpec, TaiRuntimeOptions options, int minutes) throws JSONException { return ok(); }
        @Override public JSONObject cancel() throws JSONException { cancelCalled = true; return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }

        private static JSONObject ok() throws JSONException {
            return new JSONObject().put("ok", true);
        }
    }
}
