package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link TaiRuntimeLinkContractTest} against {@link InProcessTaiRuntimeLink} over a fake
 * {@link TaiRuntime}: the host hands the body's script to the runtime, streams its tokens as chunks
 * and ends the stream as the chat routes do; the runtime's {@link TaiRuntime#cancel} stops the
 * generation it is running.
 */
public class InProcessTaiRuntimeLinkContractTest extends TaiRuntimeLinkContractTest {
    @Override
    protected TaiRuntimeLink newLink(@NonNull FakeTaiRuntimeLink.Gates gates) {
        return new InProcessTaiRuntimeLink(new Host(new ScriptedRuntime(gates)));
    }

    private static final TaiRuntimeOptions OPTIONS = new TaiRuntimeOptions(null, null, null, null, null, null, null, null);

    /** Plays the script it was handed, one generation at a time; {@link #cancel} stops the active one. */
    private static final class ScriptedRuntime implements TaiRuntime {
        private final FakeTaiRuntimeLink.Gates gates;
        @Nullable volatile String script;
        @Nullable private volatile AtomicBoolean active;

        ScriptedRuntime(@NonNull FakeTaiRuntimeLink.Gates gates) {
            this.gates = gates;
        }

        @NonNull @Override public TaiRuntimeState getState() {
            return new TaiRuntimeState(false, null, "scripted", "Scripted.");
        }
        @Override public boolean isModelLoaded(@NonNull String modelId) { return false; }
        @NonNull @Override public JSONObject load(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options) throws JSONException { return ok(); }
        @NonNull @Override public JSONObject unload() throws JSONException { return ok(); }
        @NonNull @Override public JSONObject keepWarm(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException { return ok(); }

        @NonNull
        @Override
        public JSONObject cancel() throws JSONException {
            AtomicBoolean flag = active;
            if (flag != null) flag.set(true);
            return ok().put("cancelled", flag != null);
        }

        @NonNull
        @Override
        public JSONObject generate(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options,
                                   @Nullable TaiGenerationCallback callback) throws JSONException {
            AtomicBoolean cancelled = new AtomicBoolean();
            active = cancelled;
            try {
                return FakeTaiRuntimeLink.play(script, gates, cancelled::get, text -> {
                    if (callback != null) callback.onToken(text);
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                active = null;
            }
        }

        private static JSONObject ok() throws JSONException {
            return new JSONObject().put("ok", true);
        }
    }

    /** The runtime half of TaiManager, reduced to handing the script over and shaping the answer. */
    private static final class Host implements InProcessTaiRuntimeLink.Host {
        private final ScriptedRuntime runtime;

        Host(@NonNull ScriptedRuntime runtime) {
            this.runtime = runtime;
        }

        @NonNull
        @Override
        public JSONObject generateHere(@NonNull TaiRuntimeLink.Kind kind, @NonNull String body) throws JSONException {
            runtime.script = body;
            return FakeTaiRuntimeLink.completionResult(
                TaiRuntime.generate(runtime, "scripted", TaiChatRequest.simple("", "prompt"), OPTIONS));
        }

        @Override
        public void streamHere(@NonNull TaiRuntimeLink.Kind kind, @NonNull String body, @NonNull TaiManager.OpenAiStreamSink sink)
            throws JSONException, IOException {
            runtime.script = body;
            JSONObject result;
            try {
                result = runtime.generate("scripted", TaiChatRequest.simple("", "prompt"), OPTIONS, new TaiGenerationCallback() {
                    @Override
                    public void onToken(@NonNull String text) {
                        try {
                            sink.onEvent(FakeTaiRuntimeLink.chunkEvent(text));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        } catch (JSONException e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    @Override
                    public void onComplete(@NonNull String fullText) {
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            FakeTaiRuntimeLink.finishStream(sink, result);
        }

        @Override
        public void cancelActiveGeneration() {
            try {
                runtime.cancel();
            } catch (JSONException ignored) {
            }
        }

        @NonNull
        @Override
        public JSONObject cancelAllHere(@NonNull String stopReason) throws JSONException {
            return runtime.cancel();
        }

        @NonNull @Override public JSONObject loadHere(@NonNull String body) throws JSONException { return new JSONObject().put("ok", true); }
        @NonNull @Override public JSONObject unloadHere() throws JSONException { return runtime.unload(); }
        @NonNull @Override public JSONObject unloadChatHere(@NonNull String modelId) throws JSONException { return runtime.unloadChatModel(modelId); }
        @NonNull @Override public JSONObject keepWarmHere(@NonNull String body) throws JSONException { return new JSONObject().put("ok", true); }
        @NonNull @Override public JSONObject statusHere() throws JSONException { return new JSONObject().put("ok", true); }
        @NonNull @Override public JSONArray residentsHere() { return new JSONArray(); }
        @NonNull @Override public TaiRuntimeState stateHere() { return runtime.getState(); }
    }
}
