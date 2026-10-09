package com.termux.ai;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The router's load lock must be the only lock that spans a native load: state reads and cancel go
 * straight to the backend while a load is blocked, and an embedding batch never holds anything a
 * status poll would wait on.
 */
@RunWith(RobolectricTestRunner.class)
public class MultiBackendTaiRuntimeLockingTest {
    private static final long PROMPT_MS = 2_000L;

    @Test
    public void stateAndCancel_returnWhileLoadIsBlocked() throws Exception {
        BlockingLoadRuntime liteRt = new BlockingLoadRuntime();
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(liteRt, new IdleRuntime());
        Thread load = startLoad(runtime, chatModel("blocked-load", TaiModelSpec.BACKEND_LITERT_LM));
        assertTrue(liteRt.loadEntered.await(PROMPT_MS, TimeUnit.MILLISECONDS));

        TaiRuntimeState state = promptly(runtime::getState);
        assertEquals("loading", state.state);
        assertFalse(promptly(() -> runtime.isModelLoaded("blocked-load")));
        JSONObject cancelled = promptly(runtime::cancel);
        assertTrue(cancelled.getBoolean("ok"));
        assertTrue(liteRt.cancelCalled);
        assertTrue(load.isAlive());

        liteRt.release.countDown();
        load.join(PROMPT_MS);
        assertFalse(load.isAlive());
    }

    /** The resident table is read lock-free: a status poll mid-load sees it without queuing behind the load. */
    @Test
    public void residencySnapshot_returnsWhileLoadIsBlocked() throws Exception {
        BlockingLoadRuntime liteRt = new BlockingLoadRuntime();
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(liteRt, new IdleRuntime());
        Thread load = startLoad(runtime, chatModel("blocked-load", TaiModelSpec.BACKEND_LITERT_LM));
        assertTrue(liteRt.loadEntered.await(PROMPT_MS, TimeUnit.MILLISECONDS));

        assertTrue(promptly(() -> runtime.residency().snapshot()).isEmpty());
        assertTrue(load.isAlive());

        liteRt.release.countDown();
        load.join(PROMPT_MS);
        assertFalse(load.isAlive());
    }

    @Test
    public void unloadDuringLoad_cancelsTheLoadBeforeWaitingForIt() throws Exception {
        BlockingLoadRuntime liteRt = new BlockingLoadRuntime();
        liteRt.releaseOnCancel = true;
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(liteRt, new IdleRuntime());
        Thread load = startLoad(runtime, chatModel("unload-mid-load", TaiModelSpec.BACKEND_LITERT_LM));
        assertTrue(liteRt.loadEntered.await(PROMPT_MS, TimeUnit.MILLISECONDS));

        // The fake only lets the load return once cancel() has reached it, so an unload that
        // waited for the load lock first would never get through.
        JSONObject unloaded = promptly(runtime::unload);

        assertTrue(unloaded.getBoolean("ok"));
        assertTrue(liteRt.cancelCalled);
        assertTrue(liteRt.unloadCalled);
        load.join(PROMPT_MS);
        assertFalse(load.isAlive());
    }

    @Test
    public void embedInProgress_doesNotBlockGetState() throws Exception {
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(new IdleRuntime(), new IdleRuntime());
        Object embeddingRuntime = field(runtime, "embeddings");
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // Stand in for a long embedding batch: the LiteRT embedding runtime serializes embed() on
        // its own monitor, so holding that monitor keeps embed() inside the router until released.
        Thread holder = new Thread(() -> {
            synchronized (embeddingRuntime) {
                held.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        holder.start();
        assertTrue(held.await(PROMPT_MS, TimeUnit.MILLISECONDS));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread embed = new Thread(() -> {
            try {
                runtime.embed(embeddingModel("embed-blocked"), Collections.singletonList("hello"), 0);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        embed.start();
        assertTrue(awaitBlocked(embed));

        TaiRuntimeState state = promptly(runtime::getState);

        assertNotNull(state);
        assertTrue(embed.isAlive());
        release.countDown();
        embed.join(PROMPT_MS);
        holder.join(PROMPT_MS);
        assertFalse(embed.isAlive());
        assertTrue(failure.get() == null);
    }

    private static Thread startLoad(MultiBackendTaiRuntime runtime, TaiModelSpec model) {
        Thread load = new Thread(() -> {
            try {
                runtime.load(model, options());
            } catch (JSONException ignored) {
            }
        });
        load.start();
        return load;
    }

    /** Runs the call on its own thread and fails if it has not returned within {@link #PROMPT_MS}. */
    private static <T> T promptly(Callable<T> call) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(call.call());
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        thread.start();
        thread.join(PROMPT_MS);
        assertFalse("call did not return within " + PROMPT_MS + " ms", thread.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static boolean awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.currentTimeMillis() + PROMPT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (thread.getState() == Thread.State.BLOCKED) return true;
            if (!thread.isAlive()) return false;
            Thread.sleep(10L);
        }
        return false;
    }

    private static TaiModelSpec chatModel(String id, String backend) {
        return spec(id, backend, TaiModelSpec.FORMAT_LITERTLM, "/models/" + id + "/model.litertlm", TaiModelSpec.CAPABILITY_TEXT_CHAT);
    }

    private static TaiModelSpec embeddingModel(String id) {
        return spec(id, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "/models/" + id + "/model.tflite", TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
    }

    private static TaiModelSpec spec(String id, String backend, String format, String path, String capability) {
        return new TaiModelSpec(
            id,
            id,
            "Test model",
            "test",
            path,
            "test",
            123L,
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

    private static TaiRuntimeOptions options() {
        return new TaiRuntimeOptions(null, null, null, null, null, null, null, null);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static TaiRuntimeState state(String state, boolean loaded, String modelId) {
        return new TaiRuntimeState(loaded, modelId, "fake", state, state, TaiModelSpec.BACKEND_LITERT_LM, null, null,
            false, null, 0L, 0L, 0L, 0L, 0L);
    }

    /** Sits in load() until released; reports "loading" and records cancel/unload like LiteRT would. */
    private static final class BlockingLoadRuntime implements TaiRuntime {
        final CountDownLatch loadEntered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean releaseOnCancel;
        volatile boolean cancelCalled;
        volatile boolean unloadCalled;
        private volatile boolean loading;

        @Override public TaiRuntimeState getState() { return state(loading ? "loading" : "unloaded", false, null); }
        @Override public boolean isModelLoaded(String modelId) { return false; }
        @Override public JSONObject load(TaiModelSpec modelSpec, TaiRuntimeOptions options) throws JSONException {
            loading = true;
            loadEntered.countDown();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            loading = false;
            return ok();
        }
        @Override public JSONObject unload() throws JSONException { unloadCalled = true; return ok(); }
        @Override public JSONObject keepWarm(TaiModelSpec modelSpec, TaiRuntimeOptions options, int minutes) throws JSONException { return load(modelSpec, options); }
        @Override public JSONObject cancel() throws JSONException {
            cancelCalled = true;
            if (releaseOnCancel) release.countDown();
            return ok();
        }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }

        private JSONObject ok() throws JSONException { return new JSONObject().put("ok", true); }
    }

    /** An unloaded backend that answers everything immediately. */
    private static final class IdleRuntime implements TaiRuntime {
        @Override public TaiRuntimeState getState() { return state("unloaded", false, null); }
        @Override public boolean isModelLoaded(String modelId) { return false; }
        @Override public JSONObject load(TaiModelSpec modelSpec, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject unload() throws JSONException { return ok(); }
        @Override public JSONObject keepWarm(TaiModelSpec modelSpec, TaiRuntimeOptions options, int minutes) throws JSONException { return ok(); }
        @Override public JSONObject cancel() throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }

        private JSONObject ok() throws JSONException { return new JSONObject().put("ok", true); }
    }
}
