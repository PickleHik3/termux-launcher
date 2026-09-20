package com.termux.launcherctl;

import com.termux.app.terminal.TerminalActionDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The font tools have to be reachable through the one dispatcher, not a second registry, so
 * both halves are pinned: the registry advertises them with usable schemas, and
 * {@code TerminalActionDispatcher} claims them — a tool the palette shows but the dispatcher
 * refuses would answer 501 at the moment a user tapped it.
 */
public class FontToolsRegistryTest {

    private LauncherToolRegistry registry;

    @Before
    public void setUp() {
        LauncherToolRegistry.resetForTesting();
        registry = LauncherToolRegistry.getInstance();
    }

    @Test
    public void fontsPick_isAPaletteActionUnderAppearance() {
        LauncherToolRegistry.ToolMetadata tool = registry.getTool("fonts.pick");
        assertNotNull(tool);
        assertEquals(LauncherToolRegistry.ToolExecutor.TERMINAL, tool.executor);
        assertEquals(LauncherToolRegistry.CATEGORY_APPEARANCE, tool.category);
        assertEquals(LauncherToolRegistry.ToolRisk.LOW, tool.risk);
        assertFalse(tool.requiresConfirmation);
        assertTrue(tool.hasUiMetadata());
        // Opening a settings screen needs no arguments and claims no stroke.
        assertEquals(0, tool.schema.optJSONObject("properties").length());
        assertTrue(tool.defaultBindings.isEmpty());
    }

    @Test
    public void fontsInstall_requiresAnIdAndTakesTheTogglesAsArguments() {
        LauncherToolRegistry.ToolMetadata tool = registry.getTool("fonts.install");
        assertNotNull(tool);
        assertEquals(LauncherToolRegistry.ToolExecutor.TERMINAL, tool.executor);
        assertEquals(LauncherToolRegistry.CATEGORY_APPEARANCE, tool.category);
        // Spends the user's data and then changes every glyph on screen.
        assertEquals(LauncherToolRegistry.ToolRisk.MEDIUM, tool.risk);
        assertTrue(tool.requiresConfirmation);

        JSONArray required = tool.schema.optJSONArray("required");
        assertNotNull(required);
        assertEquals(1, required.length());
        assertEquals("id", required.optString(0));

        JSONObject properties = tool.schema.optJSONObject("properties");
        assertNotNull(properties);
        assertEquals("string", properties.optJSONObject("id").optString("type"));
        assertEquals("boolean", properties.optJSONObject("nerd_icons").optString("type"));
        assertTrue(properties.optJSONObject("nerd_icons").optBoolean("default"));
        JSONArray policies = properties.optJSONObject("ligatures").optJSONArray("enum");
        assertNotNull(policies);
        assertEquals(3, policies.length());
        assertEquals("never", policies.optString(0));
        assertEquals("cursor", policies.optString(1));
        assertEquals("always", policies.optString(2));
        JSONObject weight = properties.optJSONObject("weight");
        assertEquals("integer", weight.optString("type"));
        assertEquals(0, weight.optInt("minimum"));
        assertEquals(1000, weight.optInt("maximum"));
        assertEquals(0, weight.optInt("default"));

        // Extra keys are rejected, so a typo'd toggle fails loudly instead of being ignored.
        assertFalse(tool.schema.optBoolean("additionalProperties", true));
    }

    @Test
    public void bothFontTools_areClaimedByTheTerminalDispatcher() {
        assertTrue(TerminalActionDispatcher.handles("fonts.pick"));
        assertTrue(TerminalActionDispatcher.handles("fonts.install"));
        assertFalse(TerminalActionDispatcher.handles("fonts.nope"));
    }

    @Test
    public void fontTools_doNotCreateACategoryOfTheirOwn() {
        // Appearance already exists; a one-tool section would read as a bug in the palette.
        assertTrue(registry.getUiToolsByCategory()
            .containsKey(LauncherToolRegistry.CATEGORY_APPEARANCE));
        assertEquals(10, registry.getUiToolsByCategory().size());
    }
}
