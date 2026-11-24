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
        assertEquals(com.termux.shared.termux.settings.registry.SettingDeclarations.ALL.size(),
            settings.length());
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
    public void listShowsEveryDeclaredSettingWithItsCurrentValue() throws Exception {
        store.putBoolean("live_blur", true);
        store.putString("idle_tear_rate", "often");
        store.putString("animation_speed", "off");
        JSONObject answer = handle("{\"op\":\"list\"}");
        java.util.Map<String, JSONObject> byId = new java.util.HashMap<>();
        JSONArray settings = answer.getJSONArray("settings");
        for (int i = 0; i < settings.length(); i++)
            byId.put(settings.getJSONObject(i).getString("id"), settings.getJSONObject(i));
        for (com.termux.shared.termux.settings.registry.SettingDeclaration each
                : com.termux.shared.termux.settings.registry.SettingDeclarations.ALL) {
            JSONObject listed = byId.get(each.id);
            assertTrue(each.id + " is not listed", listed != null);
            assertEquals(each.id, each.defaultValue(), listed.getString("default"));
        }
        assertEquals("true", byId.get("glass.live_blur").getString("value"));
        assertEquals("boolean", byId.get("glass.live_blur").getString("kind"));
        assertEquals("[\"false\",\"true\"]",
            byId.get("glass.live_blur").getJSONArray("choices").toString());
        assertEquals("true", byId.get("glass.refraction").getString("value"));
        assertEquals("often", byId.get("motion.idle_tear_rate").getString("value"));
        assertEquals("off", byId.get("motion.animation_speed").getString("value"));
    }

    @Test
    public void configGetReadsALegacyDockStyleAsTheLauncherDoes() throws Exception {
        store.putString("app_launcher_dock_style", "rounded");
        assertEquals("floating",
            handle("{\"op\":\"get\",\"id\":\"dock.style\"}").getString("value"));
        store.putString("app_launcher_dock_style", "valarie_capsule");
        assertEquals("floating",
            handle("{\"op\":\"get\",\"id\":\"dock.style\"}").getString("value"));
        store.putString("app_launcher_dock_style", "default");
        assertEquals("docked",
            handle("{\"op\":\"get\",\"id\":\"dock.style\"}").getString("value"));
    }

    @Test
    public void aBooleanSettingIsSetThroughConfigSet() throws Exception {
        JSONObject answer = handle("{\"op\":\"set\",\"id\":\"motion.cursor_trail\",\"value\":\"false\"}");
        assertTrue(answer.getBoolean("ok"));
        assertEquals("true", answer.getString("old"));
        assertEquals("false", answer.getString("value"));
        assertEquals(false, store.getBoolean("terminal_cursor_trail", true));
        JSONObject refused = handle("{\"op\":\"set\",\"id\":\"motion.cursor_trail\",\"value\":\"sometimes\"}");
        assertEquals("invalid_value", refused.getString("error"));
        assertEquals("false, true", refused.getString("allowed"));
    }

    @Test
    public void listAsTextIsOneLinePerSetting() throws Exception {
        JSONObject answer = handle("{\"op\":\"list\",\"format\":\"text\"}");
        // No last newline: the script prints the body with one, as it does a JSON answer.
        String text = answer.getString(ConfigCommand.TEXT);
        assertTrue(text, text.startsWith(
            "theme.mode = system\nterminal.contrast = default\ntext.size = 100\n"));
        assertTrue(text, text.contains("\nmotion.animation_speed = normal\n"));
        assertEquals(com.termux.shared.termux.settings.registry.SettingDeclarations.ALL.size(),
            text.split("\n").length);
    }

    /** An id whose check passes and whose save is then refused, as a store may. */
    private static final com.termux.shared.termux.settings.registry.SettingAdapter REFUSING =
        new com.termux.shared.termux.settings.registry.SettingAdapter() {
            private final com.termux.shared.termux.settings.registry.SettingDeclaration mDeclaration =
                com.termux.shared.termux.settings.registry.SettingDeclaration.text("late.refuse",
                    "", SettingRefresh.LISTENER);

            @androidx.annotation.Nullable @Override
            public com.termux.shared.termux.settings.registry.SettingDeclaration declaration(
                    @NonNull String id) {
                return id.equals("late.refuse") ? mDeclaration : null;
            }

            @NonNull @Override
            public List<com.termux.shared.termux.settings.registry.SettingDeclaration> declarations() {
                return Collections.singletonList(mDeclaration);
            }

            @NonNull @Override public String get(
                    @NonNull com.termux.shared.termux.settings.registry.SettingDeclaration d) {
                return "";
            }

            @androidx.annotation.Nullable @Override
            public SettingsRegistry.Refusal check(
                    @NonNull com.termux.shared.termux.settings.registry.SettingDeclaration d,
                    @NonNull String value) {
                return null;
            }

            @NonNull @Override
            public SettingsRegistry.Outcome set(
                    @NonNull com.termux.shared.termux.settings.registry.SettingDeclaration d,
                    @NonNull String value) {
                return new SettingsRegistry.Refusal("the store said no", null);
            }
        };

    @Test
    public void aLineRefusedOnSavingAfterTheCheckPassedIsAFailureWithItsLine() throws Exception {
        SettingsRegistry withLate = new SettingsRegistry(which -> store,
            com.termux.shared.termux.settings.registry.SettingDeclarations.ALL,
            Collections.singletonList(REFUSING));
        String file = "text.size = 120\nlate.refuse = 1\n";
        JSONObject answer = ConfigCommand.handle(withLate, applier,
            new JSONObject().put("op", "apply").put("text", file));
        assertFalse(answer.toString(), answer.getBoolean("ok"));
        assertEquals("apply_failed", answer.getString("error"));
        assertEquals("line 2: the store said no", answer.getString("message"));
        assertEquals(1, answer.getInt("changed"));
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
