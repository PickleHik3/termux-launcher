package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code launcherctl config get|set|list} over the Settings registry on an in-memory store. */
public class ConfigCommandTest {

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store);
    private final List<SettingRefresh> refreshed = new ArrayList<>();
    private final SettingsApplier applier = new SettingsApplier(new SettingsApplier.Host() {
        @Override public boolean isLauncherResumed() { return true; }

        @Override public void run(@NonNull SettingRefresh refresh) { refreshed.add(refresh); }
    });

    @NonNull
    private JSONObject handle(@NonNull String json) throws Exception {
        return ConfigCommand.handle(registry, applier, new JSONObject(json));
    }

    @Test
    public void getAnswersTheValue() throws Exception {
        store.putString("terminal_contrast_level", "softer");
        JSONObject answer = handle("{\"op\":\"get\",\"id\":\"terminal.contrast\"}");
        assertTrue(answer.toString(), answer.getBoolean("ok"));
        assertEquals("terminal.contrast", answer.getString("id"));
        assertEquals("softer", answer.getString("value"));
    }

    @Test
    public void getOfAnUnknownIdIsA400() throws Exception {
        JSONObject answer = handle("{\"op\":\"get\",\"id\":\"terminal.sparkle\"}");
        assertFalse(answer.getBoolean("ok"));
        assertEquals(400, answer.getInt("_statusCode"));
        assertEquals("unknown_setting", answer.getString("error"));
        assertTrue(answer.getString("message"), answer.getString("message").contains("config list"));
    }

    @Test
    public void setSavesAppliesAndAnswersOldAndNew() throws Exception {
        JSONObject answer = handle("{\"op\":\"set\",\"id\":\"terminal.contrast\",\"value\":\"harder\"}");
        assertTrue(answer.toString(), answer.getBoolean("ok"));
        assertEquals("default", answer.getString("old"));
        assertEquals("harder", answer.getString("value"));
        assertEquals("harder", store.getString("terminal_contrast_level"));
        assertEquals(Collections.singletonList(SettingRefresh.RESTYLE), refreshed);
    }

    @Test
    public void setOfABadValueIsA400WithTheAllowedRangeAndWritesNothing() throws Exception {
        JSONObject answer = handle("{\"op\":\"set\",\"id\":\"text.size\",\"value\":\"400\"}");
        assertFalse(answer.getBoolean("ok"));
        assertEquals(400, answer.getInt("_statusCode"));
        assertEquals("invalid_value", answer.getString("error"));
        assertEquals("text.size is 80–150", answer.getString("message"));
        assertEquals("80–150", answer.getString("allowed"));
        assertEquals(-1, store.getInt("text_size_percent", -1));
        assertTrue(refreshed.isEmpty());

        JSONObject unknown = handle("{\"op\":\"set\",\"id\":\"nope\",\"value\":\"1\"}");
        assertEquals(400, unknown.getInt("_statusCode"));
        assertEquals("unknown_setting", unknown.getString("error"));
        JSONObject missing = handle("{\"op\":\"set\",\"id\":\"text.size\"}");
        assertEquals(400, missing.getInt("_statusCode"));
    }

    @Test
    public void listGivesEverySettingWithItsKindRangeDefaultAndValue() throws Exception {
        store.putInt("text_size_percent", 120);
        JSONObject answer = handle("{\"op\":\"list\"}");
        assertTrue(answer.getBoolean("ok"));
        JSONArray settings = answer.getJSONArray("settings");
        assertEquals(3, settings.length());
        JSONObject theme = settings.getJSONObject(0);
        assertEquals("theme.mode", theme.getString("id"));
        assertEquals("choice", theme.getString("kind"));
        assertEquals("system", theme.getString("default"));
        assertEquals("[\"system\",\"light\",\"dark\"]", theme.getJSONArray("choices").toString());
        JSONObject size = settings.getJSONObject(2);
        assertEquals("text.size", size.getString("id"));
        assertEquals("integer", size.getString("kind"));
        assertEquals("120", size.getString("value"));
        assertEquals("100", size.getString("default"));
        assertEquals(80, size.getInt("min"));
        assertEquals(150, size.getInt("max"));
        assertEquals(5, size.getInt("step"));
    }

    @Test
    public void listAsTextIsOneLinePerSetting() throws Exception {
        JSONObject answer = handle("{\"op\":\"list\",\"format\":\"text\"}");
        // No last newline: the script prints the body with one, as it does a JSON answer.
        assertEquals("theme.mode = system\nterminal.contrast = default\ntext.size = 100",
            answer.getString(ConfigCommand.TEXT));
    }

    @Test
    public void anUnknownOpIsA400() throws Exception {
        JSONObject answer = handle("{\"op\":\"frobnicate\"}");
        assertEquals(400, answer.getInt("_statusCode"));
        assertEquals("'op' must be get, set, list, export or apply", answer.getString("message"));
    }

    @Test
    public void exportAnswersTheFileAsTextAndApplyingItBackChangesNothing() throws Exception {
        store.putInt("text_size_percent", 120);
        String file = handle("{\"op\":\"export\"}").getString(ConfigCommand.TEXT);
        assertTrue(file, file.contains("text.size = 120"));

        store.putInt("text_size_percent", 90);
        JSONObject applied = handle(new JSONObject().put("op", "apply").put("text", file).toString());
        assertTrue(applied.toString(), applied.getBoolean("ok"));
        assertEquals("120", registry.get("text.size"));
        assertEquals(1, applied.getInt("changed"));
        assertTrue(refreshed.contains(SettingRefresh.TEXT_SIZE));
    }

    @Test
    public void applyWithOneBadLineChangesNothingAndNamesThatLine() throws Exception {
        String file = "# setup\ntext.size = 120\n\nterminal.contrast = harder\ntext.size = 400\n";
        JSONObject answer = handle(new JSONObject().put("op", "apply").put("text", file).toString());
        assertFalse(answer.getBoolean("ok"));
        assertEquals(400, answer.getInt("_statusCode"));
        assertEquals("invalid_file", answer.getString("error"));
        assertEquals("line 5: text.size is 80–150", answer.getString("message"));
        assertEquals("100", registry.get("text.size"));
        assertEquals("default", registry.get("terminal.contrast"));
        assertTrue(refreshed.isEmpty());
        assertEquals(400, handle("{\"op\":\"apply\"}").getInt("_statusCode"));
    }
}
