package com.termux.launcherctl;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.TaiChatRequest;
import com.termux.ai.TaiGenerationCallback;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiRuntime;
import com.termux.ai.TaiRuntimeOptions;
import com.termux.ai.TaiRuntimeState;
import com.termux.ai.TaiSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * Captures today's exact Launcherctl HTTP output for the OpenAI chat and completion routes, so a
 * later move of these routes can prove the bytes did not change (ADR 0027 round A).
 *
 * Every body is compared whole, as UTF-8, against a literal, after {@link #normalise} has replaced
 * only the clock-driven values.
 *
 * Not captured: tool calls (the model would need tool-use capability and a tools request) and the
 * remote happy path (no remote provider to talk to).
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class OpenAiRoutesCompatibilityTest {
    private static final String MODEL = "compat-chat";

    private Context context;
    private LauncherCtlApiServer server;
    private int port;
    private String token;
    private ScriptedRuntime runtime;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();

        Field taiInstance = TaiManager.class.getDeclaredField("instance");
        taiInstance.setAccessible(true);
        taiInstance.set(null, null);
        TaiManager manager = TaiManager.getInstance(context);

        runtime = new ScriptedRuntime();
        Field runtimeField = TaiManager.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        runtimeField.set(manager, runtime);

        TaiManager.setImportPathAllowedRootForTesting(System.getProperty("java.io.tmpdir"));
        File file = File.createTempFile("compat-chat", ".litertlm");
        file.deleteOnExit();
        JSONObject imported = manager.modelManagement().importModel(new JSONObject()
            .put("path", file.getAbsolutePath())
            .put("modelId", MODEL)
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_CHAT))
            .toString());
        assertEquals(imported.toString(), true, imported.optBoolean("ok", false));
        // The Default model, which an untagged request falls back to.
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, MODEL).commit();

        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
        server = LauncherCtlApiServer.getInstance();
        server.start(context);
        JSONObject endpoint = server.endpointSettings(context);
        port = endpoint.getInt("activePort");
        token = endpoint.getString("token");
        awaitAnswering(port);
    }

    @After
    public void tearDown() throws Exception {
        TaiManager.setImportPathAllowedRootForTesting(null);
        if (server != null) server.stop();
        Field taiInstance = TaiManager.class.getDeclaredField("instance");
        taiInstance.setAccessible(true);
        taiInstance.set(null, null);
        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
    }

    private static final String CHAT = "{\"model\":\"" + MODEL + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]";
    private static final String HI = "[{\"role\":\"user\",\"content\":\"hi\"}]";

    @Test public void chatStream() throws Exception {
        check("chatStream", 200, post("/v1/chat/completions", CHAT + ",\"stream\":true}"), EXPECTED_chatStream);
    }

    @Test public void chatStreamIncludeUsage() throws Exception {
        check("chatStreamIncludeUsage", 200, post("/v1/chat/completions",
            CHAT + ",\"stream\":true,\"stream_options\":{\"include_usage\":true}}"), EXPECTED_chatStreamIncludeUsage);
    }

    @Test public void chatStreamStopSequence() throws Exception {
        check("chatStreamStopSequence", 200, post("/v1/chat/completions",
            CHAT + ",\"stream\":true,\"stop\":\"wor\"}"), EXPECTED_chatStreamStopSequence);
    }

    @Test public void chatNonStream() throws Exception {
        check("chatNonStream", 200, post("/v1/chat/completions", CHAT + ",\"stream\":false}"), EXPECTED_chatNonStream);
    }

    @Test public void completionsStream() throws Exception {
        check("completionsStream", 200, post("/v1/completions",
            "{\"model\":\"" + MODEL + "\",\"prompt\":\"once\",\"stream\":true}"), EXPECTED_completionsStream);
    }

    @Test public void completionsNonStream() throws Exception {
        check("completionsNonStream", 200, post("/v1/completions",
            "{\"model\":\"" + MODEL + "\",\"prompt\":\"once\",\"stream\":false}"), EXPECTED_completionsNonStream);
    }

    @Test public void chatNoMessagesStream() throws Exception {
        check("chatNoMessagesStream", 200, post("/v1/chat/completions",
            "{\"model\":\"" + MODEL + "\",\"messages\":[],\"stream\":true}"), EXPECTED_chatNoMessagesStream);
    }

    @Test public void chatNoMessagesNonStream() throws Exception {
        check("chatNoMessagesNonStream", 400, post("/v1/chat/completions",
            "{\"model\":\"" + MODEL + "\",\"messages\":[],\"stream\":false}"), EXPECTED_chatNoMessagesNonStream);
    }

    @Test public void chatUnknownModelStream() throws Exception {
        check("chatUnknownModelStream", 200, post("/v1/chat/completions",
            "{\"model\":\"nope\",\"messages\":" + HI + ",\"stream\":true}"), EXPECTED_chatUnknownModelStream);
    }

    @Test public void chatUnknownModelNonStream() throws Exception {
        check("chatUnknownModelNonStream", 404, post("/v1/chat/completions",
            "{\"model\":\"nope\",\"messages\":" + HI + ",\"stream\":false}"), EXPECTED_chatUnknownModelNonStream);
    }

    @Test public void chatRemoteNotConfiguredStream() throws Exception {
        check("chatRemoteNotConfiguredStream", 200, post("/v1/chat/completions",
            "{\"model\":\"remote/x\",\"messages\":" + HI + ",\"stream\":true}"), EXPECTED_chatRemoteNotConfiguredStream);
    }

    @Test public void chatRemoteNotConfiguredNonStream() throws Exception {
        check("chatRemoteNotConfiguredNonStream", 503, post("/v1/chat/completions",
            "{\"model\":\"remote/x\",\"messages\":" + HI + ",\"stream\":false}"), EXPECTED_chatRemoteNotConfiguredNonStream);
    }

    @Test public void untaggedRequestUsesTheDefaultModel() throws Exception {
        Resp r = post("/v1/chat/completions", "{\"messages\":" + HI + ",\"stream\":false}");
        check("untagged", 200, r, EXPECTED_untagged);
        assertEquals(MODEL, runtime.lastGenerateModelId);
    }

    @Test public void ollamaChatNonStream() throws Exception {
        check("ollamaChatNonStream", 200, post("/api/chat",
            "{\"model\":\"" + MODEL + "\",\"messages\":" + HI + ",\"stream\":false}"), EXPECTED_ollamaChatNonStream);
    }

    @Test public void responsesNonStream() throws Exception {
        check("responsesNonStream", 200, post("/v1/responses",
            "{\"model\":\"" + MODEL + "\",\"input\":\"hi\",\"stream\":false}"), EXPECTED_responsesNonStream);
    }

    @Test public void ollamaChatStream() throws Exception {
        check("ollamaChatStream", 200, post("/api/chat",
            "{\"model\":\"" + MODEL + "\",\"messages\":" + HI + ",\"stream\":true}"), EXPECTED_ollamaChatStream);
    }

    @Test public void responsesStream() throws Exception {
        check("responsesStream", 200, post("/v1/responses",
            "{\"model\":\"" + MODEL + "\",\"input\":\"hi\",\"stream\":true}"), EXPECTED_responsesStream);
    }

    /** A client that goes away mid-stream cancels its generation instead of letting it run on. */
    @Test public void aClientThatDisconnectsCancelsItsGeneration() throws Exception {
        runtime.endless = true;
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/chat/completions").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write((CHAT + ",\"stream\":true}").getBytes(StandardCharsets.UTF_8));
        assertEquals(200, conn.getResponseCode());
        InputStream stream = conn.getInputStream();
        byte[] first = new byte[16];
        assertEquals(16, stream.read(first)); // the first SSE chunk is on the wire
        stream.close();
        conn.disconnect();
        long deadline = System.currentTimeMillis() + 5_000;
        while (!runtime.cancelled && System.currentTimeMillis() < deadline) Thread.sleep(20);
        org.junit.Assert.assertTrue("the runtime was told to cancel", runtime.cancelled);
    }

    // ---- expected output ----

    private static final String EXPECTED_ollamaChatStream =
            "{\"model\":\"compat-chat\",\"created_at\":\"<T>\",\"done\":false,\"message\":{\"role\":\"assistant\",\"content\":\"\"}}\n"
            + "{\"model\":\"compat-chat\",\"created_at\":\"<T>\",\"done\":false,\"message\":{\"role\":\"assistant\",\"content\":\"\"}}\n"
            + "{\"model\":\"compat-chat\",\"created_at\":\"<T>\",\"done\":false,\"message\":{\"role\":\"assistant\",\"content\":\"\"}}\n"
            + "{\"model\":\"compat-chat\",\"created_at\":\"<T>\",\"done\":false,\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}\n"
            + "{\"model\":\"compat-chat\",\"created_at\":\"<T>\",\"done\":false,\"message\":{\"role\":\"assistant\",\"content\":\" wor\"}}\n"
            + "{\"model\":\"compat-chat\",\"created_at\":\"<T>\",\"done\":false,\"message\":{\"role\":\"assistant\",\"content\":\"ld\"}}\n"
            + "{\"model\":\"compat-chat\",\"created_at\":\"<T>\",\"done\":true,\"done_reason\":\"stop\",\"total_duration\":0,\"load_duration\":0,\"prompt_eval_count\":0,\"prompt_eval_duration\":0,\"eval_count\":0,\"eval_duration\":0,\"message\":{\"role\":\"assistant\",\"content\":\"\"}}\n";
    private static final String EXPECTED_responsesStream =
            "data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_<ID>\",\"object\":\"response\",\"created_at\":<T>,\"status\":\"in_progress\",\"model\":\"compat-chat\",\"output\":[]}}\n"
            + "\n"
            + "data: {\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":{\"type\":\"message\",\"id\":\"msg_<ID>\",\"status\":\"in_progress\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"\",\"annotations\":[]}]}}\n"
            + "\n"
            + "data: {\"type\":\"response.content_part.added\",\"item_id\":\"msg_<ID>\",\"output_index\":0,\"content_index\":0,\"part\":{\"type\":\"output_text\",\"text\":\"\",\"annotations\":[]}}\n"
            + "\n"
            + "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_<ID>\",\"output_index\":0,\"content_index\":0,\"delta\":\"Hello\"}\n"
            + "\n"
            + "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_<ID>\",\"output_index\":0,\"content_index\":0,\"delta\":\" wor\"}\n"
            + "\n"
            + "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_<ID>\",\"output_index\":0,\"content_index\":0,\"delta\":\"ld\"}\n"
            + "\n"
            + "data: {\"type\":\"response.output_text.done\",\"item_id\":\"msg_<ID>\",\"output_index\":0,\"content_index\":0,\"text\":\"Hello world\"}\n"
            + "\n"
            + "data: {\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"type\":\"message\",\"id\":\"msg_<ID>\",\"status\":\"completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello world\",\"annotations\":[]}]}}\n"
            + "\n"
            + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_<ID>\",\"object\":\"response\",\"created_at\":<T>,\"status\":\"completed\",\"model\":\"compat-chat\",\"output\":[{\"type\":\"message\",\"id\":\"msg_<ID>\",\"status\":\"completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello world\",\"annotations\":[]}]}],\"usage\":{\"input_tokens\":0,\"output_tokens\":0,\"total_tokens\":0}}}\n"
            + "\n"
            + "data: [DONE]\n"
            + "\n";

    private static final String EXPECTED_chatStream =
            "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"Let me \"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"think.\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" wor\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ld\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n"
            + "\n"
            + "data: [DONE]\n"
            + "\n";
    private static final String EXPECTED_chatStreamIncludeUsage =
            "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"Let me \"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"think.\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" wor\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ld\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7}}\n"
            + "\n"
            + "data: [DONE]\n"
            + "\n";
    private static final String EXPECTED_chatStreamStopSequence =
            "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"Let me \"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"think.\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" \"},\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-chatcmpl-<ID>\",\"object\":\"chat.completion.chunk\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n"
            + "\n"
            + "data: [DONE]\n"
            + "\n";
    private static final String EXPECTED_chatNonStream =
            "{\"id\":\"tai-<ID>\",\"object\":\"chat.completion\",\"model\":\"compat-chat\",\"created\":<T>,\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello world\",\"reasoning_content\":\"Let me think.\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7},\"tai\":{\"ok\":true,\"model\":\"compat-chat\",\"response\":\"Hello world\",\"reasoning_content\":\"Let me think.\",\"finishReason\":\"stop\",\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4}}}";
    private static final String EXPECTED_completionsStream =
            "data: {\"id\":\"tai-cmpl-<ID>\",\"object\":\"text_completion\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"text\":\"Hello\",\"index\":0,\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-cmpl-<ID>\",\"object\":\"text_completion\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"text\":\" wor\",\"index\":0,\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-cmpl-<ID>\",\"object\":\"text_completion\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"text\":\"ld\",\"index\":0,\"finish_reason\":null}]}\n"
            + "\n"
            + "data: {\"id\":\"tai-cmpl-<ID>\",\"object\":\"text_completion\",\"created\":<T>,\"model\":\"compat-chat\",\"choices\":[{\"text\":\"\",\"index\":0,\"finish_reason\":\"stop\"}]}\n"
            + "\n"
            + "data: [DONE]\n"
            + "\n";
    private static final String EXPECTED_completionsNonStream =
            "{\"id\":\"tai-cmpl-<ID>\",\"object\":\"text_completion\",\"model\":\"compat-chat\",\"created\":<T>,\"choices\":[{\"text\":\"Hello world\",\"index\":0,\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7},\"tai\":{\"ok\":true,\"model\":\"compat-chat\",\"response\":\"Hello world\",\"reasoning_content\":\"Let me think.\",\"finishReason\":\"stop\",\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4}}}";
    private static final String EXPECTED_chatNoMessagesStream =
            "data: {\"ok\":false,\"message\":\"Missing messages\",\"code\":\"bad_request\",\"error_code\":\"bad_request\",\"error\":{\"message\":\"Missing messages\",\"type\":\"invalid_request_error\",\"code\":\"bad_request\"},\"tai\":{\"ok\":false,\"error\":\"bad_request\",\"message\":\"Missing messages\",\"_statusCode\":400},\"_statusCode\":400}\n"
            + "\n"
            + "data: [DONE]\n"
            + "\n";
    private static final String EXPECTED_chatNoMessagesNonStream =
            "{\"ok\":false,\"message\":\"Missing messages\",\"code\":\"bad_request\",\"error_code\":\"bad_request\",\"error\":{\"message\":\"Missing messages\",\"type\":\"invalid_request_error\",\"code\":\"bad_request\"},\"tai\":{\"ok\":false,\"error\":\"bad_request\",\"message\":\"Missing messages\",\"_statusCode\":400}}";
    private static final String EXPECTED_chatUnknownModelStream =
            "data: {\"ok\":false,\"message\":\"Unknown model: nope\",\"code\":\"model_not_found\",\"error_code\":\"model_not_found\",\"error\":{\"message\":\"Unknown model: nope\",\"type\":\"invalid_request_error\",\"code\":\"model_not_found\"},\"tai\":{\"ok\":false,\"error\":\"model_not_found\",\"message\":\"Unknown model: nope\",\"_statusCode\":404},\"_statusCode\":404}\n"
            + "\n"
            + "data: [DONE]\n"
            + "\n";
    private static final String EXPECTED_chatUnknownModelNonStream =
            "{\"ok\":false,\"message\":\"Unknown model: nope\",\"code\":\"model_not_found\",\"error_code\":\"model_not_found\",\"error\":{\"message\":\"Unknown model: nope\",\"type\":\"invalid_request_error\",\"code\":\"model_not_found\"},\"tai\":{\"ok\":false,\"error\":\"model_not_found\",\"message\":\"Unknown model: nope\",\"_statusCode\":404}}";
    private static final String EXPECTED_chatRemoteNotConfiguredStream =
            "data: {\"ok\":false,\"message\":\"No remote model is set up.\",\"code\":\"remote_not_configured\",\"error_code\":\"remote_not_configured\",\"error\":{\"message\":\"No remote model is set up.\",\"type\":\"invalid_request_error\",\"code\":\"remote_not_configured\"},\"tai\":{\"ok\":false,\"error\":\"remote_not_configured\",\"message\":\"No remote model is set up.\",\"_statusCode\":503},\"_statusCode\":503}\n"
            + "\n"
            + "data: [DONE]\n"
            + "\n";
    private static final String EXPECTED_chatRemoteNotConfiguredNonStream =
            "{\"ok\":false,\"message\":\"No remote model is set up.\",\"code\":\"remote_not_configured\",\"error_code\":\"remote_not_configured\",\"error\":{\"message\":\"No remote model is set up.\",\"type\":\"api_error\",\"code\":\"remote_not_configured\"},\"tai\":{\"ok\":false,\"error\":\"remote_not_configured\",\"message\":\"No remote model is set up.\",\"_statusCode\":503}}";
    private static final String EXPECTED_untagged =
            "{\"id\":\"tai-<ID>\",\"object\":\"chat.completion\",\"model\":\"compat-chat\",\"created\":<T>,\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello world\",\"reasoning_content\":\"Let me think.\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7},\"tai\":{\"ok\":true,\"model\":\"compat-chat\",\"response\":\"Hello world\",\"reasoning_content\":\"Let me think.\",\"finishReason\":\"stop\",\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4}}}";
    private static final String EXPECTED_ollamaChatNonStream =
            "{\"model\":\"compat-chat\",\"created_at\":\"<T>\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello world\",\"reasoning_content\":\"Let me think.\"},\"done\":true,\"done_reason\":\"stop\",\"total_duration\":0,\"load_duration\":0,\"prompt_eval_count\":0,\"prompt_eval_duration\":0,\"eval_count\":0,\"eval_duration\":0}";
    private static final String EXPECTED_responsesNonStream =
            "{\"id\":\"resp_<ID>\",\"object\":\"response\",\"created_at\":<T>,\"status\":\"completed\",\"model\":\"compat-chat\",\"output\":[{\"type\":\"message\",\"id\":\"msg_<ID>\",\"status\":\"completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello world\",\"annotations\":[]}]}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7}}";

    // ---- helpers ----

    private void check(String name, int status, Resp actual, String expected) throws Exception {
        String body = normalise(actual.body);
        assertEquals(name + " status", status, actual.status);
        assertEquals(name + " body", expected, body);
    }

    /**
     * Replaces only clock-driven values, each with a fixed placeholder:
     * - ids "tai-chatcmpl-DIGITS", "tai-cmpl-DIGITS" and "tai-DIGITS" (millisecond clock);
     * - "created":DIGITS (epoch seconds) in OpenAI bodies;
     * - Ollama "created_at":"iso time" and Responses "created_at":DIGITS (epoch seconds);
     * - Responses ids "resp_DIGITS" and "msg_DIGITS" (millisecond clock);
     * - elapsed-time numbers inside the "tai" object (keys ending Ms, Seconds, PerSecond, _ms).
     */
    static String normalise(String body) {
        String s = body;
        s = s.replaceAll("tai-chatcmpl-\\d+", "tai-chatcmpl-<ID>");
        s = s.replaceAll("tai-cmpl-\\d+", "tai-cmpl-<ID>");
        s = s.replaceAll("tai-\\d+", "tai-<ID>");
        s = s.replaceAll("\"created\":\\d+", "\"created\":<T>");
        s = s.replaceAll("\"created_at\":\"[^\"]*\"", "\"created_at\":\"<T>\"");
        s = s.replaceAll("\"created_at\":\\d+", "\"created_at\":<T>");
        s = s.replaceAll("(resp|msg)_\\d+", "$1_<ID>");
        s = s.replaceAll("\"(\\w*(?:Ms|Seconds|PerSecond|_ms))\":[0-9.eE+-]+", "\"$1\":<N>");
        return s;
    }

    private static final class Resp {
        final int status;
        final String body;
        Resp(int status, String body) { this.status = status; this.body = body; }
    }

    private Resp post(String path, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        int status = conn.getResponseCode();
        InputStream stream = status < 400 ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (stream != null) {
            byte[] b = new byte[4096];
            int n;
            while ((n = stream.read(b)) != -1) buf.write(b, 0, n);
        }
        return new Resp(status, buf.toString(StandardCharsets.UTF_8.name()));
    }

    private static void awaitAnswering(int targetPort) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + targetPort + "/v1/models").openConnection();
                conn.setConnectTimeout(250);
                conn.setReadTimeout(250);
                conn.getResponseCode();
                return;
            } catch (IOException e) {
                if (System.currentTimeMillis() >= deadline) throw e;
                Thread.sleep(20);
            }
        }
    }

    /** Scripts one generation: two thinking tokens, then the answer "Hello world" in three tokens. */
    private static final class ScriptedRuntime implements TaiRuntime {
        volatile String loadedModelId;
        volatile String lastGenerateModelId;
        /** Emits a token every 20 ms until {@link #cancel} (or ten seconds) instead of the script. */
        volatile boolean endless;
        volatile boolean cancelled;

        @NonNull @Override public TaiRuntimeState getState() {
            return new TaiRuntimeState(loadedModelId != null, loadedModelId, "stub", "Stub.");
        }
        @Override public boolean isModelLoaded(@NonNull String modelId) { return modelId.equals(loadedModelId); }
        @NonNull @Override public JSONObject load(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options) throws JSONException {
            loadedModelId = spec.id;
            return new JSONObject().put("ok", true);
        }
        @NonNull @Override public JSONObject unload() throws JSONException { loadedModelId = null; return new JSONObject().put("ok", true); }
        @NonNull @Override public JSONObject keepWarm(@NonNull TaiModelSpec spec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
            return new JSONObject().put("ok", true);
        }
        @NonNull @Override public JSONObject cancel() throws JSONException {
            cancelled = true;
            return new JSONObject().put("ok", true);
        }

        @NonNull @Override
        public JSONObject generate(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options,
                                   @Nullable TaiGenerationCallback callback) throws JSONException {
            lastGenerateModelId = modelId;
            String full = "Hello world";
            if (endless && callback != null) {
                for (int i = 0; i < 500 && !cancelled && !callback.shouldCancelGeneration(); i++) {
                    callback.onToken("tick ");
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                callback.onComplete("tick");
                return new JSONObject().put("ok", true).put("model", modelId).put("response", "tick").put("finishReason", "stop");
            }
            if (callback != null) {
                callback.onThinkingToken("Let me ");
                callback.onThinkingToken("think.");
                String[] tokens = {"Hello", " wor", "ld"};
                for (String t : tokens) {
                    callback.onToken(t);
                    if (callback.shouldCancelGeneration()) break;
                }
                callback.onComplete(full);
            }
            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("model", modelId);
            result.put("response", full);
            result.put("reasoning_content", "Let me think.");
            result.put("finishReason", "stop");
            result.put("usage", new JSONObject().put("prompt_tokens", 3).put("completion_tokens", 4));
            return result;
        }
    }
}
