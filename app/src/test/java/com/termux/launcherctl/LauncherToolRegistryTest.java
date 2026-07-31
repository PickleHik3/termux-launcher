package com.termux.launcherctl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LauncherToolRegistryTest {

    private LauncherToolRegistry registry;

    @Before
    public void setUp() {
        LauncherToolRegistry.resetForTesting();
        registry = LauncherToolRegistry.getInstance();
    }

    @Test
    public void agentOnlyTools_haveNoUiMetadata() {
        String[] agentOnly = {"workspace.save", "workspace.load", "workspace.list", "workspace.delete",
            "pane.layout", "pane.move_to_edge"};
        for (String name : agentOnly) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertNotNull(name, tool);
            assertFalse(name, tool.hasUiMetadata());
            assertNull(name, tool.category);
            assertEquals(name, 0, tool.titleRes);
            assertTrue(name, tool.defaultBindings.isEmpty());
        }
    }

    @Test
    public void terminalActions_areRegisteredWithUiMetadata() {
        String[] terminalTools = {"terminal.state", "pane.split_vertical", "pane.split_horizontal",
            "pane.focus_direction", "pane.resize", "pane.kill_focused", "window.new", "window.close",
            "window.next", "window.previous", "session.new", "session.next", "session.previous",
            "session.close_current", "session.browser", "session.panel", "session.clone_current",
            "pane.equalize", "pane.rotate", "pane.next_layout", "pane.toggle_float",
            "terminal.toggle_scratchpad", "workspace.picker", "workspace.save_prompt"};
        for (String name : terminalTools) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertNotNull(name, tool);
            assertEquals(name, LauncherToolRegistry.ToolExecutor.TERMINAL, tool.executor);
            assertNotNull(name, tool.category);
            assertTrue(name, tool.hasUiMetadata());
        }
        assertEquals(57, registry.getUiTools().size());
    }

    @Test
    public void conditionalBindings_coverTheModeDependentStrokes() {
        // Ctrl+Alt+V is claimed twice under conditions that cannot both hold.
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_ON,
            registry.getTool("pane.split_vertical").defaultBindings.get(0).condition);
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_OFF,
            registry.getTool("clipboard.paste").defaultBindings.get(0).condition);
        assertEquals("ctrl+alt+v", registry.getTool("clipboard.paste").defaultBindings.get(0).stroke);
        // The rename prompts split Ctrl+Alt+R the same way.
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_ON,
            registry.getTool("window.rename_prompt").defaultBindings.get(0).condition);
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_OFF,
            registry.getTool("session.rename_prompt").defaultBindings.get(0).condition);
    }

    @Test
    public void commandPaletteIsBoundAndPromptVariantsExist() {
        assertEquals("ctrl+alt+shift+p",
            registry.getTool("app.command_palette").defaultBindings.get(0).stroke);
        assertEquals("ctrl+alt+space>p",
            registry.getTool("app.command_palette").defaultBindings.get(1).stroke);
        assertNotNull(registry.getTool("window.rename_prompt"));
        assertNotNull(registry.getTool("session.rename_prompt"));
        // The argument-taking variants remain, for callers that know the name.
        assertNotNull(registry.getTool("window.rename").schema.optJSONArray("required"));
        assertNull(registry.getTool("window.rename_prompt").schema.optJSONArray("required"));
    }

    @Test
    public void uiProjectionCarriesBindingConditions() throws Exception {
        JSONObject ui = registry.getTool("clipboard.paste").toUiJson();
        JSONArray bindings = ui.getJSONArray("defaultBindings");
        assertEquals(1, bindings.length());
        assertEquals("ctrl+alt+v", bindings.getJSONObject(0).getString("stroke"));
        assertEquals("splits-off", bindings.getJSONObject(0).getString("condition"));
    }

    @Test
    public void terminalActions_followRiskConvention() {
        // Navigation is LOW and unconfirmed; spawning a shell is MEDIUM and
        // confirmed; terminating one is HIGH and confirmed.
        String[] navigation = {"terminal.state", "pane.focus_direction", "pane.resize",
            "window.next", "window.previous", "session.next", "session.previous",
            "pane.layout", "pane.equalize", "pane.rotate", "pane.move_to_edge",
            "pane.next_layout", "session.browser"};
        for (String name : navigation) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertEquals(name, LauncherToolRegistry.ToolRisk.LOW, tool.risk);
            assertFalse(name, tool.requiresConfirmation);
        }

        String[] spawning = {"pane.split_vertical", "pane.split_horizontal", "window.new", "session.new",
            "session.clone_current"};
        for (String name : spawning) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertEquals(name, LauncherToolRegistry.ToolRisk.MEDIUM, tool.risk);
            assertTrue(name, tool.requiresConfirmation);
        }

        String[] terminating = {"pane.kill_focused", "window.close", "session.close_current"};
        for (String name : terminating) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertEquals(name, LauncherToolRegistry.ToolRisk.HIGH, tool.risk);
            assertTrue(name, tool.requiresConfirmation);
        }
    }

    @Test
    public void workspaceActions_haveExplicitSchemasAndConservativeRisk() {
        LauncherToolRegistry.ToolMetadata save = registry.getTool("workspace.save");
        LauncherToolRegistry.ToolMetadata load = registry.getTool("workspace.load");
        LauncherToolRegistry.ToolMetadata list = registry.getTool("workspace.list");
        LauncherToolRegistry.ToolMetadata delete = registry.getTool("workspace.delete");
        assertNotNull(save);
        assertNotNull(load);
        assertNotNull(list);
        assertNotNull(delete);
        assertEquals(LauncherToolRegistry.ToolRisk.MEDIUM, save.risk);
        assertEquals(LauncherToolRegistry.ToolRisk.HIGH, load.risk);
        assertEquals(LauncherToolRegistry.ToolRisk.LOW, list.risk);
        assertEquals(LauncherToolRegistry.ToolRisk.HIGH, delete.risk);
        assertTrue(save.requiresConfirmation);
        assertTrue(load.requiresConfirmation);
        assertFalse(list.requiresConfirmation);
        assertTrue(delete.requiresConfirmation);
        assertEquals("name", save.schema.optJSONArray("required").optString(0));
        assertEquals(2, load.schema.optJSONObject("properties")
            .optJSONObject("mode").optJSONArray("enum").length());
    }

    @Test
    public void automaticLayoutActions_haveBoundedSchemasAndUiMetadataWhereUsable() {
        LauncherToolRegistry.ToolMetadata layout = registry.getTool("pane.layout");
        LauncherToolRegistry.ToolMetadata equalize = registry.getTool("pane.equalize");
        LauncherToolRegistry.ToolMetadata rotate = registry.getTool("pane.rotate");
        LauncherToolRegistry.ToolMetadata move = registry.getTool("pane.move_to_edge");
        assertNotNull(layout);
        assertNotNull(equalize);
        assertNotNull(rotate);
        assertNotNull(move);
        assertEquals(6, layout.schema.optJSONObject("properties")
            .optJSONObject("layout").optJSONArray("enum").length());
        assertEquals("layout", layout.schema.optJSONArray("required").optString(0));
        assertEquals(4, move.schema.optJSONObject("properties")
            .optJSONObject("edge").optJSONArray("enum").length());
        assertEquals("edge", move.schema.optJSONArray("required").optString(0));
        assertFalse(layout.hasUiMetadata());
        assertFalse(move.hasUiMetadata());
        assertTrue(equalize.hasUiMetadata());
        assertTrue(rotate.hasUiMetadata());
        assertFalse(equalize.requiresConfirmation);
        assertFalse(rotate.requiresConfirmation);

        // next_layout takes no argument, so unlike pane.layout it can carry UI metadata and a
        // binding. It is the only layout action bound by default.
        LauncherToolRegistry.ToolMetadata next = registry.getTool("pane.next_layout");
        assertNotNull(next);
        assertTrue(next.hasUiMetadata());
        assertFalse(next.requiresConfirmation);
        assertEquals(0, next.schema.optJSONObject("properties").length());
        assertEquals(1, next.defaultBindings.size());
        assertEquals("ctrl+alt+l", next.defaultBindings.get(0).stroke);
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_ON,
            next.defaultBindings.get(0).condition);
        assertTrue(equalize.defaultBindings.isEmpty());
        assertTrue(rotate.defaultBindings.isEmpty());
    }

    /**
     * Two tools may share a stroke only when their conditions cannot both be active — the
     * SPLITS_ON / SPLITS_OFF pairs are deliberate. Anything else means one binding shadows the
     * other and whichever the resolver happens to reach first wins.
     */
    @Test
    public void defaultBindings_neverCollideUnderSimultaneouslyActiveConditions() {
        Map<String, String> claims = new HashMap<>();
        for (LauncherToolRegistry.ToolMetadata tool : registry.getTools()) {
            for (LauncherToolRegistry.Binding binding : tool.defaultBindings) {
                for (LauncherToolRegistry.BindingCondition overlapping
                        : overlappingConditions(binding.condition)) {
                    String key = binding.stroke + "@" + overlapping;
                    String previous = claims.get(key);
                    assertNull(binding.stroke + " is claimed by both " + previous + " and "
                        + tool.name + " while " + overlapping + " is active", previous);
                }
                claims.put(binding.stroke + "@" + binding.condition, tool.name);
            }
        }
    }

    private static List<LauncherToolRegistry.BindingCondition> overlappingConditions(
            LauncherToolRegistry.BindingCondition condition) {
        if (condition == LauncherToolRegistry.BindingCondition.ALWAYS) {
            return Arrays.asList(LauncherToolRegistry.BindingCondition.ALWAYS,
                LauncherToolRegistry.BindingCondition.SPLITS_ON,
                LauncherToolRegistry.BindingCondition.SPLITS_OFF);
        }
        return Arrays.asList(condition, LauncherToolRegistry.BindingCondition.ALWAYS);
    }

    @Test
    public void paletteVisibleTools_haveTitleResources() {
        // A titleRes is what marks a tool user-facing. terminal.state is an
        // introspection tool and must stay out of the palette.
        String[] userFacing = {"pane.split_vertical", "pane.split_horizontal", "pane.focus_direction",
            "pane.resize", "pane.kill_focused", "window.new", "window.close", "window.next",
            "window.previous", "session.new", "session.next", "session.previous",
            "session.close_current", "session.browser", "session.clone_current"};
        for (String name : userFacing) {
            assertTrue(name + " needs a titleRes", registry.getTool(name).titleRes != 0);
        }
        assertEquals(0, registry.getTool("terminal.state").titleRes);
    }

    @Test
    public void toolsWithRequiredArguments_areKeyboardOnly() {
        // The palette cannot prompt for arguments, so anything with a required
        // field must be reachable another way (a directional keybind).
        for (String name : new String[]{"pane.focus_direction", "pane.resize"}) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertNotNull(tool.schema.optJSONArray("required"));
            assertFalse(name + " must keep a binding", tool.defaultBindings.isEmpty());
        }
    }

    /** Test double for availability predicates. */
    private static LauncherToolRegistry.ActionContext context(boolean splits, boolean session) {
        return context(splits, session, false);
    }

    private static LauncherToolRegistry.ActionContext context(boolean splits, boolean session,
                                                             boolean selection) {
        return new LauncherToolRegistry.ActionContext() {
            @Override public boolean isSplitPanesEnabled() { return splits; }
            @Override public boolean hasCurrentSession() { return session; }
            @Override public boolean hasSelectedText() { return selection; }
        };
    }

    @Test
    public void terminalViewActions_areRegistered() {
        String[] viewActions = {"terminal.toggle_soft_keyboard", "terminal.toggle_toolbar",
            "terminal.font_size_increase", "terminal.font_size_decrease", "terminal.select_url",
            "terminal.hints", "terminal.search_scrollback",
            "terminal.share_transcript", "clipboard.paste"};
        for (String name : viewActions) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertNotNull(name, tool);
            assertEquals(name, LauncherToolRegistry.ToolExecutor.TERMINAL, tool.executor);
            assertTrue(name + " needs a titleRes", tool.titleRes != 0);
            assertNotNull(name, tool.availability);
        }
        assertEquals("clipboard", registry.getTool("clipboard.paste").category);
        assertEquals("terminal", registry.getTool("terminal.select_url").category);
        assertEquals("terminal", registry.getTool("terminal.hints").category);
        assertEquals("terminal", registry.getTool("terminal.search_scrollback").category);
    }

    @Test
    public void dataEgressActions_areConfirmed() {
        // Sharing sends scrollback to another app; pasting writes clipboard text
        // into a live shell. Both must be confirmed.
        for (String name : new String[]{"terminal.share_transcript", "clipboard.paste"}) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertEquals(name, LauncherToolRegistry.ToolRisk.MEDIUM, tool.risk);
            assertTrue(name + " must require confirmation", tool.requiresConfirmation);
        }
    }

    @Test
    public void splitDependentActions_areUnavailableWithoutSplits() {
        LauncherToolRegistry.ActionContext off = context(false, true);
        LauncherToolRegistry.ActionContext on = context(true, true);
        String[] needSplits = {"pane.split_vertical", "pane.split_horizontal", "pane.focus_direction",
            "pane.resize", "window.new", "window.close", "window.next", "window.previous"};
        for (String name : needSplits) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertFalse(name + " must be unavailable in compatibility mode",
                tool.availabilityIn(off).available);
            assertTrue(name + " needs a reason string", tool.availabilityIn(off).reasonRes != 0);
            assertTrue(name + " must be available with splits on", tool.availabilityIn(on).available);
        }
    }

    @Test
    public void sessionDependentActions_areUnavailableWithoutSession() {
        LauncherToolRegistry.ActionContext none = context(true, false);
        LauncherToolRegistry.ActionContext present = context(true, true);
        String[] needSession = {"pane.kill_focused", "session.close_current",
            "terminal.toggle_soft_keyboard", "terminal.select_url", "terminal.hints",
            "terminal.search_scrollback", "terminal.share_transcript",
            "clipboard.paste"};
        for (String name : needSession) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertFalse(name + " must be unavailable without a session",
                tool.availabilityIn(none).available);
            assertTrue(name + " must be available with a session",
                tool.availabilityIn(present).available);
        }
    }

    @Test
    public void toolsWithoutPredicate_areAlwaysAvailable() {
        // Non-UI tools carry no predicate and must not be gated.
        LauncherToolRegistry.ToolMetadata tool = registry.getTool("workspace.list");
        assertNull(tool.availability);
        assertTrue(tool.availabilityIn(context(false, false)).available);
        assertEquals(0, tool.availabilityIn(context(false, false)).reasonRes);
    }

    @Test
    public void availabilityIsEvaluatedPerCall_notAtRegistration() {
        LauncherToolRegistry.ToolMetadata tool = registry.getTool("window.new");
        assertTrue(tool.availabilityIn(context(true, true)).available);
        assertFalse(tool.availabilityIn(context(false, true)).available);
        assertTrue(tool.availabilityIn(context(true, true)).available);
    }

    @Test
    public void argumentTakingActions_areRegisteredAndKeyboardOrRemoteOnly() {
        // window.select / window.rename / session.rename all need a value the
        // palette cannot prompt for, so they must declare it required.
        for (String name : new String[]{"window.select", "window.rename", "session.rename"}) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertNotNull(name, tool);
            assertEquals(name, LauncherToolRegistry.ToolExecutor.TERMINAL, tool.executor);
            assertNotNull(name + " must declare a required argument",
                tool.schema.optJSONArray("required"));
            assertTrue(name + " needs a titleRes", tool.titleRes != 0);
        }
        assertEquals("index",
            registry.getTool("window.select").schema.optJSONArray("required").optString(0));
        assertEquals("name",
            registry.getTool("window.rename").schema.optJSONArray("required").optString(0));
        assertEquals("name",
            registry.getTool("session.rename").schema.optJSONArray("required").optString(0));
    }

    @Test
    public void resetTerminal_isConfirmedAndTakesNoArguments() {
        LauncherToolRegistry.ToolMetadata tool = registry.getTool("terminal.reset");
        assertNotNull(tool);
        assertEquals(LauncherToolRegistry.ToolRisk.MEDIUM, tool.risk);
        assertTrue(tool.requiresConfirmation);
        assertNull(tool.schema.optJSONArray("required"));
        assertFalse(tool.availabilityIn(context(true, false)).available);
    }

    @Test
    public void appearanceAndAppActions_areRegisteredWithoutSessionRequirement() {
        // These act on the app, not a shell, so they must stay usable with no session.
        String[] appLevel = {"appearance.set_wallpaper", "appearance.toggle_wallpaper",
            "appearance.glass_lab", "app.open_settings", "app.open_look_and_feel",
            "app.open_apps_bar"};
        LauncherToolRegistry.ActionContext noSession = context(false, false);
        for (String name : appLevel) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertNotNull(name, tool);
            assertTrue(name + " needs a titleRes", tool.titleRes != 0);
            assertTrue(name + " must not require a session",
                tool.availabilityIn(noSession).available);
            assertNull(name + " takes no arguments", tool.schema.optJSONArray("required"));
        }
        assertEquals("appearance", registry.getTool("appearance.glass_lab").category);
        assertEquals("app", registry.getTool("app.open_settings").category);
    }

    @Test
    public void wallpaperToggle_isConfirmedBecauseItPersists() {
        LauncherToolRegistry.ToolMetadata tool = registry.getTool("appearance.toggle_wallpaper");
        assertEquals(LauncherToolRegistry.ToolRisk.MEDIUM, tool.risk);
        assertTrue(tool.requiresConfirmation);
        // Opening the picker changes nothing by itself.
        assertFalse(registry.getTool("appearance.set_wallpaper").requiresConfirmation);
    }

    @Test
    public void selectionActions_trackTheLiveSelection() {
        for (String name : new String[]{"terminal.share_selected", "clipboard.copy_selected"}) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertNotNull(name, tool);
            assertTrue(name + " needs a titleRes", tool.titleRes != 0);
            assertFalse(name + " must be unavailable with no selection",
                tool.availabilityIn(context(true, true, false)).available);
            assertTrue(name + " must be available with a selection",
                tool.availabilityIn(context(true, true, true)).available);
            assertTrue(name + " needs a reason string",
                tool.availabilityIn(context(true, true, false)).reasonRes != 0);
        }
        // Sharing leaves the device; copying stays on it.
        assertTrue(registry.getTool("terminal.share_selected").requiresConfirmation);
        assertFalse(registry.getTool("clipboard.copy_selected").requiresConfirmation);
    }

    @Test
    public void killAction_isNotDuplicated() {
        // pane.kill_focused already terminates the focused shell; a separate
        // session.kill would be the same call under a second name.
        assertNotNull(registry.getTool("pane.kill_focused"));
        assertNull(registry.getTool("session.kill"));
    }

    @Test
    public void terminalActions_groupByCategory() {
        java.util.Map<String, List<LauncherToolRegistry.ToolMetadata>> grouped = registry.getUiToolsByCategory();
        assertEquals(8, grouped.size());
        // Exact per-group counts churn with every added action; assert the
        // invariant instead: every grouped tool is a UI tool and vice versa.
        int total = 0;
        for (List<LauncherToolRegistry.ToolMetadata> group : grouped.values()) total += group.size();
        assertEquals(registry.getUiTools().size(), total);
    }

    @Test
    public void directionSchemas_constrainToFourDirections() {
        for (String name : new String[]{"pane.focus_direction", "pane.resize"}) {
            JSONObject schema = registry.getTool(name).schema;
            JSONObject direction = schema.optJSONObject("properties").optJSONObject("direction");
            assertNotNull(name, direction);
            assertEquals(name, 4, direction.optJSONArray("enum").length());
            assertTrue(name, schema.optJSONArray("required").toString().contains("direction"));
        }
    }

    @Test
    public void sessionNewSchema_hasOptionalNameAndFailsafe() {
        JSONObject schema = registry.getTool("session.new").schema;
        JSONObject properties = schema.optJSONObject("properties");
        assertNotNull(properties);
        assertTrue(properties.has("name"));
        assertEquals("boolean", properties.optJSONObject("failsafe").optString("type"));
        assertNull(schema.optJSONArray("required"));
    }

    @Test
    public void terminalStateSchema_canResetPerformanceCounters() {
        JSONObject schema = registry.getTool("terminal.state").schema;
        JSONObject reset = schema.optJSONObject("properties").optJSONObject("resetPerformance");
        assertNotNull(reset);
        assertEquals("boolean", reset.optString("type"));
        assertFalse(reset.optBoolean("default", true));
    }

    @Test
    public void terminalActions_carryDefaultBindings() {
        assertEquals(1, registry.getTool("pane.split_vertical").defaultBindings.size());
        assertEquals("ctrl+alt+v", registry.getTool("pane.split_vertical").defaultBindings.get(0).stroke);
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_ON,
            registry.getTool("pane.split_vertical").defaultBindings.get(0).condition);
        assertEquals(4, registry.getTool("pane.focus_direction").defaultBindings.size());
        assertEquals(4, registry.getTool("pane.resize").defaultBindings.size());
        // terminal.state has no keybind today.
        assertTrue(registry.getTool("terminal.state").defaultBindings.isEmpty());
        // Session switching records its legacy letter plus the compatibility-mode arrow, the
        // latter conditioned so it cannot lie. The space bar swipe is not here: swipes live in
        // the keyboard layout file as tool: keys, not as strokes in this table.
        assertEquals(2, registry.getTool("session.next").defaultBindings.size());
        assertEquals("ctrl+alt+n", registry.getTool("session.next").defaultBindings.get(0).stroke);
        assertEquals(LauncherToolRegistry.BindingCondition.ALWAYS,
            registry.getTool("session.next").defaultBindings.get(0).condition);
        assertEquals("ctrl+alt+down", registry.getTool("session.next").defaultBindings.get(1).stroke);
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_OFF,
            registry.getTool("session.next").defaultBindings.get(1).condition);
        // No default binding anywhere names a keyboard gesture any more.
        for (LauncherToolRegistry.ToolMetadata tool : registry.getTools())
            for (LauncherToolRegistry.Binding binding : tool.defaultBindings)
                assertFalse(binding.stroke, binding.stroke.contains("kbd:"));
    }

    @Test
    public void uiMetadata_isExposedThroughUiProjection() throws Exception {
        LauncherToolRegistry.ToolMetadata tool = new LauncherToolRegistry.ToolMetadata(
            "pane.split_vertical",
            "Split the focused pane into two side-by-side panes.",
            new JSONObject(),
            LauncherToolRegistry.ToolRisk.LOW,
            false,
            LauncherToolRegistry.ToolExecutor.TERMINAL,
            LauncherToolRegistry.CATEGORY_PANE,
            0x7f0a0001,
            0x7f0a0002,
            LauncherToolRegistry.Binding.all("ctrl+alt+v", "ctrl+alt+shift+v"));

        assertTrue(tool.hasUiMetadata());
        assertEquals("terminal", tool.executor.label);

        JSONObject ui = tool.toUiJson();
        assertEquals("pane", ui.getString("category"));
        assertEquals(0x7f0a0001, ui.getInt("titleRes"));
        assertEquals(0x7f0a0002, ui.getInt("descriptionRes"));
        assertEquals(2, ui.getJSONArray("defaultBindings").length());
        assertEquals("ctrl+alt+v",
            ui.getJSONArray("defaultBindings").getJSONObject(0).getString("stroke"));
    }

    @Test
    public void defaultBindings_areImmutableAndNeverNull() {
        LauncherToolRegistry.ToolMetadata noBindings = new LauncherToolRegistry.ToolMetadata(
            "session.rename", "Rename the current session.", new JSONObject(),
            LauncherToolRegistry.ToolRisk.LOW, false, LauncherToolRegistry.ToolExecutor.TERMINAL,
            LauncherToolRegistry.CATEGORY_SESSION, 0x7f0a0003, 0, null);
        assertNotNull(noBindings.defaultBindings);
        assertTrue(noBindings.defaultBindings.isEmpty());

        List<LauncherToolRegistry.Binding> mutable = new ArrayList<>();
        mutable.add(LauncherToolRegistry.Binding.of("ctrl+alt+r"));
        LauncherToolRegistry.ToolMetadata withBindings = new LauncherToolRegistry.ToolMetadata(
            "window.rename", "Rename the current window.", new JSONObject(),
            LauncherToolRegistry.ToolRisk.LOW, false, LauncherToolRegistry.ToolExecutor.TERMINAL,
            LauncherToolRegistry.CATEGORY_WINDOW, 0x7f0a0004, 0, mutable);
        mutable.add(LauncherToolRegistry.Binding.of("ctrl+alt+shift+r"));
        assertEquals(1, withBindings.defaultBindings.size());
        try {
            withBindings.defaultBindings.add(LauncherToolRegistry.Binding.of("ctrl+alt+q"));
            fail("defaultBindings must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void terminalExecutor_isRegisteredForHierarchyActions() {
        assertEquals("terminal", LauncherToolRegistry.ToolExecutor.TERMINAL.label);
        for (LauncherToolRegistry.ToolExecutor executor : LauncherToolRegistry.ToolExecutor.values()) {
            assertNotNull(executor.label);
            assertFalse(executor.label.isEmpty());
        }
    }
}
