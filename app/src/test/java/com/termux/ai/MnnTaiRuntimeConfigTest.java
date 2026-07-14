package com.termux.ai;

import android.content.Context;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.google.ai.edge.litertlm.Message;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MnnTaiRuntimeConfigTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void mergedConfig_preservesMnnConfigDefaultsWhenOptionsAreAuto() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-config-preserve");
        dir.mkdirs();
        File config = configFile(dir);
        touch(new File(dir, "llm.mnn"));
        touch(new File(dir, "llm.mnn.weight"));
        touch(new File(dir, "tokenizer.mtok"));

        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        TaiRuntimeOptions options = new TaiRuntimeOptions(null, null, null, null,
            null, null, null, null, null, null, null, null);
        JSONObject merged = new JSONObject((String) invokeMergedConfig(runtime, config, model(config), options));

        assertEquals("cpu", merged.getString("backend_type"));
        assertEquals(4, merged.getInt("thread_num"));
        assertEquals("low", merged.getString("precision"));
        assertEquals("low", merged.getString("memory"));
        assertEquals(0.8d, merged.getDouble("temperature"), 0.0d);
        assertEquals(40, merged.getInt("top_k"));
        assertEquals(0.9d, merged.getDouble("top_p"), 0.0d);
        assertEquals("mixed", merged.getString("sampler_type"));
        assertEquals(0.05d, merged.getDouble("min_p"), 0.0d);
        assertEquals("tokenizer.mtok", merged.getString("tokenizer_file"));
        assertFalse(merged.has("system_prompt"));
        assertTrue(merged.getBoolean("use_mmap"));
        assertTrue(merged.getString("tmp_path").startsWith(context.getCacheDir().getAbsolutePath()));
        assertTrue(new File(merged.getString("tmp_path")).isDirectory());
    }

    @Test
    public void loadMnnModel_reportsMissingTokenizerSidecar() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-config-missing-tokenizer");
        dir.mkdirs();
        File config = configFile(dir);
        touch(new File(dir, "llm.mnn"));
        touch(new File(dir, "llm.mnn.weight"));

        JSONObject result = new MnnTaiRuntime(context).load(model(config),
            new TaiRuntimeOptions(null, null, null, null, null, null, null, null));

        assertFalse(result.getBoolean("ok"));
        assertEquals("model_file_not_readable", result.getString("error"));
        assertEquals("tokenizer.mtok", result.getString("missingFilename"));
    }

    @Test
    public void mergedConfig_injectsMaxContextAndMaxNewTokenDefaultsFromModelSpec() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-config-defaults");
        dir.mkdirs();
        File config = new File(dir, "config.json");
        // The obsolete max_context_len key must not leak into the emitted engine config.
        write(config, "{\"llm_model\":\"llm.mnn\",\"llm_weight\":\"llm.mnn.weight\","
            + "\"tokenizer_file\":\"tokenizer.mtok\",\"max_context_len\":32768,"
            + "\"prompt_cache\":false,\"jinja\":{\"chat_template\":\"template\"}}");
        touch(new File(dir, "llm.mnn"));
        touch(new File(dir, "llm.mnn.weight"));
        touch(new File(dir, "tokenizer.mtok"));

        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        TaiRuntimeOptions options = new TaiRuntimeOptions(null, null, null, null,
            null, null, null, null, null, null, null, null);
        TaiModelSpec spec = model(config);
        JSONObject merged = new JSONObject((String) invokeMergedConfig(runtime, config, spec, options));

        assertEquals("cpu", merged.getString("backend_type"));
        assertEquals(4, merged.getInt("thread_num"));
        assertEquals("low", merged.getString("precision"));
        assertEquals("low", merged.getString("memory"));
        assertEquals(spec.endpointContextWindow, merged.getInt("max_all_tokens"));
        assertFalse(merged.has("max_context_len"));
        assertTrue(merged.getBoolean("prompt_cache"));
        assertEquals(spec.defaultMaxOutputTokens, merged.getInt("max_new_tokens"));
        assertEquals(0.8d, merged.getDouble("temperature"), 0.0d);
        assertEquals(40, merged.getInt("top_k"));
        assertEquals(0.9d, merged.getDouble("top_p"), 0.0d);
    }

    @Test
    public void mergedConfig_clampsConfigMaxContextToEndpointContextWindow() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-config-clamp");
        dir.mkdirs();
        File config = new File(dir, "config.json");
        // Upstream config advertises a larger context than the endpoint cap.
        write(config, "{\"llm_model\":\"llm.mnn\",\"llm_weight\":\"llm.mnn.weight\","
            + "\"tokenizer_file\":\"tokenizer.mtok\",\"max_all_tokens\":32768,"
            + "\"max_new_tokens\":8192,\"jinja\":{\"chat_template\":\"template\"}}");
        touch(new File(dir, "llm.mnn"));
        touch(new File(dir, "llm.mnn.weight"));
        touch(new File(dir, "tokenizer.mtok"));

        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        TaiRuntimeOptions options = new TaiRuntimeOptions(null, null, null, null,
            null, null, null, null, null, null, null, null);
        TaiModelSpec spec = model(config);
        JSONObject merged = new JSONObject((String) invokeMergedConfig(runtime, config, spec, options));

        assertEquals(spec.endpointContextWindow, merged.getInt("max_all_tokens"));
        assertTrue(merged.getInt("max_all_tokens") <= spec.endpointContextWindow);
        assertEquals(8192, merged.getInt("max_new_tokens"));
    }

    @Test
    public void settingsAutoLeavesMnnConfigValuesNull() {
        TaiRuntimeOptions options = new TaiSettings(context).getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, "mnn-auto");

        assertNull(options.accelerator);
        assertNull(options.threadCount);
        assertNull(options.precision);
        assertNull(options.memoryMode);
        assertNull(options.temperature);
        assertNull(options.topK);
        assertNull(options.topP);
    }

    @Test
    public void parseToolCalls_convertsMnnToolBlocksToOpenAiShape() throws Exception {
        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        Method method = MnnTaiRuntime.class.getDeclaredMethod("parseToolCalls", String.class, String.class);
        method.setAccessible(true);

        org.json.JSONArray calls = (org.json.JSONArray) method.invoke(runtime,
            "<tool_call>\n{\"name\":\"lookup\",\"arguments\":{\"query\":\"termux\"}}\n</tool_call>",
            "gen-1");

        assertEquals(1, calls.length());
        assertEquals("gen-1-call-1", calls.getJSONObject(0).getString("id"));
        assertEquals("function", calls.getJSONObject(0).getString("type"));
        assertEquals("lookup", calls.getJSONObject(0).getJSONObject("function").getString("name"));
        assertEquals("{\"query\":\"termux\"}", calls.getJSONObject(0).getJSONObject("function").getString("arguments"));
    }

    @Test
    public void mnnHistory_emitsOpenAiSystemAndDeveloperContentAsFirstSystemRole() throws Exception {
        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        org.json.JSONArray messages = new org.json.JSONArray()
            .put(new JSONObject().put("role", "system").put("content", "System rules"))
            .put(new JSONObject().put("role", "developer").put("content", "Developer rules"))
            .put(new JSONObject().put("role", "user").put("content", "Hello"));
        TaiChatRequest request = new TaiChatRequest(
            "", Collections.emptyList(), Message.Companion.user("Hello"), Collections.emptyList(),
            false, messages, new org.json.JSONArray(), null);

        Method method = MnnTaiRuntime.class.getDeclaredMethod("mnnHistory", TaiChatRequest.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Pair<String, String>> history = (List<Pair<String, String>>) method.invoke(runtime, request);

        assertEquals("system", history.get(0).first);
        assertEquals("System rules\nDeveloper rules", history.get(0).second);
        assertEquals("user", history.get(1).first);
        assertEquals("Hello", history.get(1).second);
    }

    @Test
    public void mnnHistory_materializesOpenAiAudioAndInjectsMarkup() throws Exception {
        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        org.json.JSONArray messages = new org.json.JSONArray().put(new JSONObject()
            .put("role", "user")
            .put("content", new org.json.JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "transcribe "))
                .put(new JSONObject().put("type", "input_audio").put("input_audio",
                    new JSONObject().put("data", "AQID").put("format", "wav")))));
        TaiChatRequest request = new TaiChatRequest(
            "", Collections.emptyList(), Message.Companion.user(""), Collections.emptyList(),
            false, messages, new org.json.JSONArray(), null);

        Method method = MnnTaiRuntime.class.getDeclaredMethod("mnnHistory", TaiChatRequest.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Pair<String, String>> history = (List<Pair<String, String>>) method.invoke(runtime, request);

        String content = history.get(0).second;
        assertTrue(content.startsWith("transcribe <audio>"));
        assertTrue(content.endsWith("</audio>"));
        String path = content.substring(content.indexOf("<audio>") + 7, content.indexOf("</audio>"));
        assertTrue(path.endsWith(".wav"));
        assertEquals(3, Files.readAllBytes(new File(path).toPath()).length);
    }

    @Test
    public void mnnHistory_injectsAudioMarkupForFileUrl() throws Exception {
        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        File audio = new File(context.getCacheDir(), "sample.wav");
        touch(audio);
        org.json.JSONArray messages = new org.json.JSONArray().put(new JSONObject()
            .put("role", "user")
            .put("content", new org.json.JSONArray().put(new JSONObject()
                .put("type", "audio")
                .put("audio", new JSONObject().put("url", "file://" + audio.getAbsolutePath())))));
        TaiChatRequest request = new TaiChatRequest(
            "", Collections.emptyList(), Message.Companion.user(""), Collections.emptyList(),
            false, messages, new org.json.JSONArray(), null);

        Method method = MnnTaiRuntime.class.getDeclaredMethod("mnnHistory", TaiChatRequest.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Pair<String, String>> history = (List<Pair<String, String>>) method.invoke(runtime, request);

        assertEquals("<audio>" + audio.getAbsolutePath() + "</audio>", history.get(0).second);
    }

    @Test
    public void extraConfig_enablesPromptCacheWithStatelessHistory() throws Exception {
        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        Method method = MnnTaiRuntime.class.getDeclaredMethod("extraConfigJson", TaiModelSpec.class);
        method.setAccessible(true);
        File config = new File(context.getCacheDir(), "mnn-extra-config/config.json");
        JSONObject extra = new JSONObject((String) method.invoke(runtime, model(config)));

        assertFalse(extra.getBoolean("keep_history"));
        assertTrue(extra.getBoolean("prompt_cache"));
    }

    @Test
    public void runtimeOverrides_useMaxAllTokensForContextWindow() throws Exception {
        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        TaiRuntimeOptions options = new TaiRuntimeOptions(null, null, null, null,
            null, 3072, null, null, null, null, null, null);
        Method method = MnnTaiRuntime.class.getDeclaredMethod("overridesJson", TaiRuntimeOptions.class);
        method.setAccessible(true);
        JSONObject overrides = (JSONObject) method.invoke(runtime, options);

        assertEquals(3072, overrides.getInt("max_all_tokens"));
        assertFalse(overrides.has("max_context_len"));
    }

    @Test
    public void thinkingOption_emitsJinjaContextInMergedAndRuntimeOverrides() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-thinking-config");
        dir.mkdirs();
        File config = configFile(dir);
        TaiRuntimeOptions options = new TaiRuntimeOptions(null, null, null, null,
            null, null, null, null, null, false, null, null);
        MnnTaiRuntime runtime = new MnnTaiRuntime(context);

        JSONObject merged = new JSONObject((String) invokeMergedConfig(runtime, config, model(config), options));
        Method method = MnnTaiRuntime.class.getDeclaredMethod("overridesJson", TaiRuntimeOptions.class);
        method.setAccessible(true);
        JSONObject overrides = (JSONObject) method.invoke(runtime, options);

        assertEquals("template", merged.getJSONObject("jinja").getString("chat_template"));
        assertFalse(merged.getJSONObject("jinja").getJSONObject("context").getBoolean("enable_thinking"));
        assertFalse(overrides.getJSONObject("jinja").getJSONObject("context").getBoolean("enable_thinking"));
    }

    private static Object invokeMergedConfig(MnnTaiRuntime runtime, File config, TaiModelSpec model, TaiRuntimeOptions options) throws Exception {
        Method method = MnnTaiRuntime.class.getDeclaredMethod("mergedConfigJson", File.class, TaiModelSpec.class, TaiRuntimeOptions.class);
        method.setAccessible(true);
        return method.invoke(runtime, config, model, options);
    }

    private static File configFile(File dir) throws Exception {
        File config = new File(dir, "config.json");
        String json = "{\"llm_model\":\"llm.mnn\",\"llm_weight\":\"llm.mnn.weight\",\"backend_type\":\"cpu\","
            + "\"thread_num\":4,\"precision\":\"low\",\"memory\":\"low\",\"sampler_type\":\"mixed\","
            + "\"temperature\":0.8,\"top_k\":40,\"top_p\":0.9,\"min_p\":0.05,"
            + "\"tokenizer_file\":\"tokenizer.mtok\",\"jinja\":{\"chat_template\":\"template\"}}";
        write(config, json);
        return config;
    }

    private static void touch(File file) throws Exception {
        write(file, "x");
    }

    private static void write(File file, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static TaiModelSpec model(File config) {
        return new TaiModelSpec(
            "mnn-test",
            "MNN Test",
            "test",
            "test",
            config.getAbsolutePath(),
            "test",
            1L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            TaiModelSpec.BACKEND_MNN_LLM,
            TaiModelSpec.FORMAT_MNN,
            "qwen2.5",
            "int4",
            4096,
            0,
            null
        );
    }
}
