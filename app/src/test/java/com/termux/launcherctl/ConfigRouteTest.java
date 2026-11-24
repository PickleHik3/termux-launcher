package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;

/** {@code POST /v1/config} from the route table to the wire: JSON, and text/plain for a text answer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class ConfigRouteTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void theRouteDispatchesToTheRegistryAndAnswersJson() throws Exception {
        JSONObject answer = DeviceControlRoutes.handle(context, "POST", "/v1/config",
            "{\"op\":\"get\",\"id\":\"text.size\"}");
        assertTrue(answer.toString(), answer.getBoolean("ok"));
        assertEquals("text.size", answer.getString("id"));
        assertEquals("100", answer.getString("value"));
        assertNull("a JSON answer is not sent as text", LauncherCtlApiServer.textAnswer(answer));

        JSONObject refused = DeviceControlRoutes.handle(context, "POST", "/v1/config",
            "{\"op\":\"set\",\"id\":\"text.size\",\"value\":\"400\"}");
        assertEquals(400, refused.getInt("_statusCode"));
        assertEquals("text.size is 80\u2013150", refused.getString("message"));
    }

    @Test
    public void aTextAnswerGoesOutAsPlainTextWithoutTheJsonEnvelope() throws Exception {
        JSONObject answer = DeviceControlRoutes.handle(context, "POST", "/v1/config",
            "{\"op\":\"list\",\"format\":\"text\"}");
        LauncherCtlApiServer.HttpResponse response = LauncherCtlApiServer.textAnswer(answer);
        assertNotNull(response);
        assertEquals(200, response.statusCode);
        assertEquals("text/plain; charset=utf-8", response.contentType);
        String body = new String(response.body, StandardCharsets.UTF_8);
        assertTrue(body, body.contains("text.size = 100"));
        assertFalse(body, body.startsWith("{"));
        assertFalse("the script adds the last newline", body.endsWith("\n"));
    }

    @Test
    public void anExportIsTheSameTextAnswer() throws Exception {
        JSONObject answer = DeviceControlRoutes.handle(context, "POST", "/v1/config",
            "{\"op\":\"export\"}");
        LauncherCtlApiServer.HttpResponse response = LauncherCtlApiServer.textAnswer(answer);
        assertNotNull(response);
        assertTrue(new String(response.body, StandardCharsets.UTF_8).startsWith("#"));
    }
}
