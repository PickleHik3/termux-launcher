package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared launcher tool registry for the in-app UI surfaces: the command palette,
 * the curated action sheet, and the key binding editor.
 *
 * <p>Core metadata per tool: name, description, JSON schema, risk level,
 * confirmation requirement, and executor classification. Execution happens in
 * {@code com.termux.app.terminal.TerminalActionDispatcher}.
 *
 * <p>The UI projection is {@link ToolMetadata#toUiJson()} plus the
 * category/title/binding accessors. UI metadata is optional: a tool without it
 * (workspace save/load, parameterized pane actions) stays reachable from custom
 * key bindings but never appears in the palette. Terminal, session, window, and
 * pane actions belong here under dotted lowercase IDs
 * ({@code pane.split_vertical}, {@code session.rename}) rather than in a
 * separate registry.
 */
public final class LauncherToolRegistry {
    private static final long JSON_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;

    /** Risk classification used for confirmation gating and UI hints. */
    public enum ToolRisk {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        CRITICAL("critical");

        public final String label;

        ToolRisk(String label) {
            this.label = label;
        }
    }

    /** Executor classification for dispatch wiring. */
    public enum ToolExecutor {
        /** Terminal hierarchy: sessions, windows, panes, emulator state. */
        TERMINAL("terminal");

        public final String label;

        ToolExecutor(String label) {
            this.label = label;
        }
    }

    /**
     * Palette/action-sheet grouping keys. These are stable identifiers, not
     * display strings; the UI resolves them to localized section headers.
     */
    public static final String CATEGORY_SESSION = "session";
    public static final String CATEGORY_WINDOW = "window";
    public static final String CATEGORY_PANE = "pane";
    public static final String CATEGORY_TERMINAL = "terminal";
    public static final String CATEGORY_CLIPBOARD = "clipboard";
    public static final String CATEGORY_APPEARANCE = "appearance";
    public static final String CATEGORY_APP = "app";
    /** Installed Android apps, contributed by the palette rather than by tools. */
    public static final String CATEGORY_APPS = "apps";

    /**
     * The app state an availability predicate is allowed to see.
     *
     * <p>Kept deliberately narrow so the registry stays platform-neutral: it
     * describes conditions, not Android objects. Implemented by
     * {@code TerminalActionDispatcher} against the live Activity.
     */
    public interface ActionContext {
        boolean isSplitPanesEnabled();

        boolean hasCurrentSession();

        /** Whether the terminal currently holds selected text. */
        boolean hasSelectedText();
    }

    /** Whether an action can run right now, and why not when it cannot. */
    public static final class Availability {
        private static final Availability AVAILABLE = new Availability(true, 0);

        public final boolean available;
        /** Localized reason shown on a disabled palette row; {@code 0} when available. */
        @StringRes
        public final int reasonRes;

        private Availability(boolean available, @StringRes int reasonRes) {
            this.available = available;
            this.reasonRes = reasonRes;
        }

        @NonNull
        public static Availability available() {
            return AVAILABLE;
        }

        @NonNull
        public static Availability unavailable(@StringRes int reasonRes) {
            return new Availability(false, reasonRes);
        }
    }

    /** Evaluated at display/execution time, never at registration time. */
    public interface AvailabilityPredicate {
        @NonNull
        Availability evaluate(@NonNull ActionContext context);
    }

    /**
     * When a binding applies.
     *
     * <p>Deliberately an enum rather than a lambda: conditions must be printable
     * for the binding diagnostics screen and expressible in a future
     * {@code map --when} config syntax, neither of which works with an opaque
     * predicate.
     */
    public enum BindingCondition {
        /** Applies in every mode. */
        ALWAYS("always"),
        /** Only while split panes are enabled. */
        SPLITS_ON("splits-on"),
        /** Only while compatibility mode has split panes disabled. */
        SPLITS_OFF("splits-off");

        public final String label;

        BindingCondition(String label) {
            this.label = label;
        }

        public boolean holds(@NonNull ActionContext context) {
            switch (this) {
                case SPLITS_ON: return context.isSplitPanesEnabled();
                case SPLITS_OFF: return !context.isSplitPanesEnabled();
                default: return true;
            }
        }

        /** Whether two conditions can ever both hold, i.e. whether they conflict. */
        public boolean overlaps(@NonNull BindingCondition other) {
            if (this == ALWAYS || other == ALWAYS) return true;
            return this == other;
        }
    }

    /**
     * One key stroke, plus the mode in which it applies.
     *
     * <p>Conditions are what let a stroke mean different things in different
     * modes — {@code Ctrl+Alt+V} splits a pane with split panes on and pastes with
     * them off — without either meaning being a lie.
     */
    public static final class Binding {
        public final String stroke;
        public final BindingCondition condition;

        private Binding(@NonNull String stroke, @NonNull BindingCondition condition) {
            this.stroke = stroke;
            this.condition = condition;
        }

        @NonNull
        public static Binding of(@NonNull String stroke) {
            return new Binding(stroke, BindingCondition.ALWAYS);
        }

        @NonNull
        public static Binding of(@NonNull String stroke, @NonNull BindingCondition condition) {
            return new Binding(stroke, condition);
        }

        /** Builds unconditional bindings for the common case. */
        @NonNull
        public static List<Binding> all(@NonNull String... strokes) {
            List<Binding> bindings = new ArrayList<>(strokes.length);
            for (String stroke : strokes) {
                bindings.add(of(stroke));
            }
            return bindings;
        }

        @NonNull
        @Override
        public String toString() {
            return condition == BindingCondition.ALWAYS
                ? stroke
                : stroke + " (" + condition.label + ")";
        }
    }

    public static final class ToolMetadata {
        public final String name;
        public final String description;
        public final JSONObject schema;
        public final ToolRisk risk;
        public final boolean requiresConfirmation;
        public final ToolExecutor executor;

        /** Palette grouping key, or {@code null} for agent-only tools. */
        @Nullable
        public final String category;
        /** Localized short label resource, or {@code 0} when unset. */
        @StringRes
        public final int titleRes;
        /** Localized help text resource, or {@code 0} when unset. */
        @StringRes
        public final int descriptionRes;
        /**
         * Default key strokes for this action, in the binding syntax consumed by
         * the key binding resolver. Never null; empty means unbound.
         */
        @NonNull
        public final List<Binding> defaultBindings;
        /** Null means unconditionally available. */
        @Nullable
        public final AvailabilityPredicate availability;

        /** Agent-only tool: no UI metadata. */
        public ToolMetadata(
            @NonNull String name,
            @NonNull String description,
            @NonNull JSONObject schema,
            @NonNull ToolRisk risk,
            boolean requiresConfirmation,
            @NonNull ToolExecutor executor
        ) {
            this(name, description, schema, risk, requiresConfirmation, executor, null, 0, 0, null, null);
        }

        public ToolMetadata(
            @NonNull String name,
            @NonNull String description,
            @NonNull JSONObject schema,
            @NonNull ToolRisk risk,
            boolean requiresConfirmation,
            @NonNull ToolExecutor executor,
            @Nullable String category,
            @StringRes int titleRes,
            @StringRes int descriptionRes,
            @Nullable List<Binding> defaultBindings
        ) {
            this(name, description, schema, risk, requiresConfirmation, executor,
                category, titleRes, descriptionRes, defaultBindings, null);
        }

        public ToolMetadata(
            @NonNull String name,
            @NonNull String description,
            @NonNull JSONObject schema,
            @NonNull ToolRisk risk,
            boolean requiresConfirmation,
            @NonNull ToolExecutor executor,
            @Nullable String category,
            @StringRes int titleRes,
            @StringRes int descriptionRes,
            @Nullable List<Binding> defaultBindings,
            @Nullable AvailabilityPredicate availability
        ) {
            this.availability = availability;
            this.name = name;
            this.description = description;
            this.schema = schema;
            this.risk = risk;
            this.requiresConfirmation = requiresConfirmation;
            this.executor = executor;
            this.category = category;
            this.titleRes = titleRes;
            this.descriptionRes = descriptionRes;
            this.defaultBindings = defaultBindings == null || defaultBindings.isEmpty()
                ? Collections.<Binding>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(defaultBindings));
        }

        /** Whether this tool carries enough metadata to appear in the command palette. */
        public boolean hasUiMetadata() {
            return category != null || titleRes != 0;
        }

        /** Evaluates the availability predicate, treating "no predicate" as available. */
        @NonNull
        public Availability availabilityIn(@NonNull ActionContext context) {
            return availability == null ? Availability.available() : availability.evaluate(context);
        }

        /**
         * UI-facing projection for the command palette, action sheet, and binding
         * editor.
         */
        @NonNull
        public JSONObject toUiJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("description", description);
            json.put("risk", risk.label);
            json.put("requiresConfirmation", requiresConfirmation);
            json.put("executor", executor.label);
            if (category != null) json.put("category", category);
            if (titleRes != 0) json.put("titleRes", titleRes);
            if (descriptionRes != 0) json.put("descriptionRes", descriptionRes);
            JSONArray bindings = new JSONArray();
            for (Binding binding : defaultBindings) {
                JSONObject entry = new JSONObject();
                entry.put("stroke", binding.stroke);
                entry.put("condition", binding.condition.label);
                bindings.put(entry);
            }
            json.put("defaultBindings", bindings);
            return json;
        }

    }

    /**
     * Terminal hierarchy actions. Executed by
     * {@code com.termux.app.terminal.TerminalActionDispatcher}, which requires a
     * foreground Activity and answers {@code 409 activity_not_running} otherwise.
     */
    public static final String TOOL_TERMINAL_STATE = "terminal.state";
    public static final String TOOL_WORKSPACE_SAVE = "workspace.save";
    public static final String TOOL_WORKSPACE_LOAD = "workspace.load";
    public static final String TOOL_WORKSPACE_LIST = "workspace.list";
    public static final String TOOL_WORKSPACE_DELETE = "workspace.delete";
    public static final String TOOL_WORKSPACE_PICKER = "workspace.picker";
    public static final String TOOL_WORKSPACE_SAVE_PROMPT = "workspace.save_prompt";
    public static final String TOOL_TERMINAL_TOGGLE_SCRATCHPAD = "terminal.toggle_scratchpad";
    public static final String TOOL_EXTRA_KEYS_EDIT = "extrakeys.edit";
    public static final String TOOL_PANE_SPLIT_VERTICAL = "pane.split_vertical";
    public static final String TOOL_PANE_SPLIT_HORIZONTAL = "pane.split_horizontal";
    public static final String TOOL_PANE_FOCUS_DIRECTION = "pane.focus_direction";
    public static final String TOOL_PANE_RESIZE = "pane.resize";
    public static final String TOOL_PANE_KILL_FOCUSED = "pane.kill_focused";
    public static final String TOOL_PANE_LAYOUT = "pane.layout";
    public static final String TOOL_PANE_EQUALIZE = "pane.equalize";
    public static final String TOOL_PANE_ROTATE = "pane.rotate";
    public static final String TOOL_PANE_MOVE_TO_EDGE = "pane.move_to_edge";
    public static final String TOOL_PANE_NEXT_LAYOUT = "pane.next_layout";
    public static final String TOOL_PANE_TOGGLE_FLOAT = "pane.toggle_float";
    public static final String TOOL_WINDOW_NEW = "window.new";
    public static final String TOOL_WINDOW_CLOSE = "window.close";
    public static final String TOOL_WINDOW_NEXT = "window.next";
    public static final String TOOL_WINDOW_PREVIOUS = "window.previous";
    public static final String TOOL_SESSION_NEW = "session.new";
    public static final String TOOL_SESSION_BROWSER = "session.browser";
    public static final String TOOL_SESSION_PANEL = "session.panel";
    public static final String TOOL_SESSION_CLONE_CURRENT = "session.clone_current";
    public static final String TOOL_SESSION_NEXT = "session.next";
    public static final String TOOL_SESSION_PREVIOUS = "session.previous";
    public static final String TOOL_SESSION_CLOSE_CURRENT = "session.close_current";
    public static final String TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD = "terminal.toggle_soft_keyboard";
    public static final String TOOL_TERMINAL_TOGGLE_TOOLBAR = "terminal.toggle_toolbar";
    public static final String TOOL_TERMINAL_FONT_SIZE_INCREASE = "terminal.font_size_increase";
    public static final String TOOL_TERMINAL_FONT_SIZE_DECREASE = "terminal.font_size_decrease";
    public static final String TOOL_TERMINAL_SELECT_URL = "terminal.select_url";
    public static final String TOOL_TERMINAL_HINTS = "terminal.hints";
    public static final String TOOL_TERMINAL_SELECT_TEXT = "terminal.select_text";
    public static final String TOOL_TERMINAL_SEARCH_SCROLLBACK = "terminal.search_scrollback";
    public static final String TOOL_TERMINAL_SHARE_TRANSCRIPT = "terminal.share_transcript";
    public static final String TOOL_CLIPBOARD_PASTE = "clipboard.paste";
    public static final String TOOL_WINDOW_SELECT = "window.select";
    public static final String TOOL_WINDOW_RENAME = "window.rename";
    public static final String TOOL_SESSION_RENAME = "session.rename";
    public static final String TOOL_SESSION_RENAME_AT_INDEX = "session.rename_at_index";
    public static final String TOOL_PANE_RENAME = "pane.rename";
    public static final String TOOL_TERMINAL_RESET = "terminal.reset";
    public static final String TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT = "terminal.jump_previous_prompt";
    public static final String TOOL_TERMINAL_JUMP_NEXT_PROMPT = "terminal.jump_next_prompt";
    public static final String TOOL_APPEARANCE_SET_WALLPAPER = "appearance.set_wallpaper";
    public static final String TOOL_APPEARANCE_TOGGLE_WALLPAPER = "appearance.toggle_wallpaper";
    public static final String TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL = "appearance.toggle_cursor_trail";
    public static final String TOOL_APPEARANCE_GLASS_LAB = "appearance.glass_lab";
    public static final String TOOL_APP_OPEN_SETTINGS = "app.open_settings";
    public static final String TOOL_APP_OPEN_LOOK_AND_FEEL = "app.open_look_and_feel";
    public static final String TOOL_APP_OPEN_APPS_BAR = "app.open_apps_bar";
    public static final String TOOL_APP_COMMAND_PALETTE = "app.command_palette";
    public static final String TOOL_APP_LAUNCH = "app.launch";
    public static final String TOOL_APP_KEY_INSPECTOR = "app.key_inspector";
    public static final String TOOL_APP_OPEN_DRAWER = "app.open_drawer";
    public static final String TOOL_APP_CLOSE_DRAWER = "app.close_drawer";
    public static final String TOOL_TERMINAL_ACTION_SHEET = "terminal.action_sheet";
    public static final String TOOL_SESSION_ACTIVATE_BY_INDEX = "session.activate_by_index";
    public static final String TOOL_WINDOW_RENAME_PROMPT = "window.rename_prompt";
    public static final String TOOL_SESSION_RENAME_PROMPT = "session.rename_prompt";
    public static final String TOOL_PANE_RENAME_PROMPT = "pane.rename_prompt";
    public static final String TOOL_TERMINAL_SHARE_SELECTED = "terminal.share_selected";
    public static final String TOOL_CLIPBOARD_COPY_SELECTED = "clipboard.copy_selected";
    public static final String TOOL_FONTS_PICK = "fonts.pick";
    public static final String TOOL_FONTS_INSTALL = "fonts.install";

    private static LauncherToolRegistry instance;

    private final Map<String, ToolMetadata> tools;

    private LauncherToolRegistry() {
        Map<String, ToolMetadata> map = new LinkedHashMap<>();
        registerTerminalTools(map);
        tools = Collections.unmodifiableMap(map);
    }

    private static void registerTerminalTools(Map<String, ToolMetadata> map) {
        // Risk convention: navigation is LOW, anything spawning a shell is MEDIUM and
        // confirmed, anything terminating one is HIGH and confirmed.
        addUi(map, TOOL_TERMINAL_STATE,
            "Return the current terminal split, window, and session state.",
            schemaObject()
                .withBoolean("resetPerformance", "Reset performance counters before returning state", false, false)
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, 0, 0, null);
        // Workspace tools stay agent/CLI-only: they have no title or description resource, which
        // is what marks a tool user-facing. The palette can prompt for a single string argument
        // now, so promoting them is only a matter of adding those strings. Loading is HIGH because
        // runCommands may execute user-owned JSON and replace mode terminates the live workspace.
        add(map, TOOL_WORKSPACE_SAVE,
            "Save the live session, window, and pane topology to ~/.termux/workspaces/<name>.json.",
            schemaObject()
                .withString("name", "Workspace file name without .json", true)
                .withBoolean("overwrite", "Replace an existing workspace file", false, false)
                .withBoolean("captureCommands", "Best-effort capture of foreground process argv", false, false)
                .build(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL);
        add(map, TOOL_WORKSPACE_LOAD,
            "Recreate a saved workspace; commands run only with an explicit opt-in.",
            schemaObject()
                .withString("name", "Workspace file name without .json", true)
                .withEnum("mode", new String[]{"append", "replace"}, false, "append")
                .withBoolean("runCommands", "Execute captured argv instead of starting login shells", false, false)
                .build(),
            ToolRisk.HIGH, true, ToolExecutor.TERMINAL);
        add(map, TOOL_WORKSPACE_LIST,
            "List durable terminal workspace files.",
            schemaEmpty(), ToolRisk.LOW, false, ToolExecutor.TERMINAL);
        add(map, TOOL_WORKSPACE_DELETE,
            "Delete a durable terminal workspace file.",
            schemaObject().withString("name", "Workspace file name without .json", true).build(),
            ToolRisk.HIGH, true, ToolExecutor.TERMINAL);
        // Interactive front doors for the two workspace flows the extra-keys row and palette
        // need: a picker dialog that loads a saved workspace, and the save-name prompt. The
        // dialogs themselves confirm the destructive choices, so the tools are LOW/no-confirm.
        addUi(map, TOOL_WORKSPACE_PICKER,
            "Show the saved-workspace picker and load the chosen workspace.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_workspace_picker, R.string.tool_desc_workspace_picker,
            null, REQUIRES_SPLITS);
        // The row editor's in-terminal entry: bindable, palette-visible, and usable as a
        // "tool:extrakeys.edit" key in the row itself. There is no long-press entry on the row
        // because every key already owns long press for auto-repeat and modifier toggles.
        addUi(map, TOOL_EXTRA_KEYS_EDIT,
            "Open the extra-keys row editor.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_extra_keys_edit, R.string.tool_desc_extra_keys_edit,
            null);
        addUi(map, TOOL_WORKSPACE_SAVE_PROMPT,
            "Prompt for a name and save the live session, window, and pane topology.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_workspace_save_prompt,
            R.string.tool_desc_workspace_save_prompt, null, REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_SPLIT_VERTICAL,
            "Split the focused pane into two panes side by side.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_split_vertical, R.string.tool_desc_pane_split_vertical, Collections.singletonList(Binding.of("ctrl+alt+v", BindingCondition.SPLITS_ON)), REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_SPLIT_HORIZONTAL,
            "Split the focused pane into two stacked panes.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_split_horizontal, R.string.tool_desc_pane_split_horizontal, Collections.singletonList(Binding.of("ctrl+alt+h", BindingCondition.SPLITS_ON)), REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_FOCUS_DIRECTION,
            "Move pane focus in a direction.",
            schemaObject()
                .withEnum("direction", new String[]{"left", "right", "up", "down"}, true, "left")
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_focus_direction, 0, Arrays.asList(
                Binding.of("ctrl+alt+left", BindingCondition.SPLITS_ON),
                Binding.of("ctrl+alt+right", BindingCondition.SPLITS_ON),
                Binding.of("ctrl+alt+up", BindingCondition.SPLITS_ON),
                Binding.of("ctrl+alt+down", BindingCondition.SPLITS_ON)), REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_RESIZE,
            "Grow the focused pane toward a direction.",
            schemaObject()
                .withEnum("direction", new String[]{"left", "right", "up", "down"}, true, "left")
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_resize, 0, Arrays.asList(
                Binding.of("ctrl+alt+shift+left", BindingCondition.SPLITS_ON),
                Binding.of("ctrl+alt+shift+right", BindingCondition.SPLITS_ON),
                Binding.of("ctrl+alt+shift+up", BindingCondition.SPLITS_ON),
                Binding.of("ctrl+alt+shift+down", BindingCondition.SPLITS_ON)), REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_KILL_FOCUSED,
            "Terminate the shell running in the focused pane.",
            schemaEmpty(),
            ToolRisk.HIGH, true, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_kill_focused, R.string.tool_desc_pane_kill_focused, null, REQUIRES_SESSION);
        // The palette cannot prompt for a preset or edge. Keep those two parameterized actions
        // agent/CLI-only; equalize and clockwise rotate remain directly useful palette actions.
        add(map, TOOL_PANE_LAYOUT,
            "Arrange the current window using an automatic pane layout.",
            schemaObject()
                .withEnum("layout", new String[]{"stack", "grid", "tall", "fat", "horizontal", "vertical"},
                    true, "grid")
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL);
        addUi(map, TOOL_PANE_EQUALIZE,
            "Reset every divider in the current window to an equal ratio.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_equalize, R.string.tool_desc_pane_equalize, null,
            REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_ROTATE,
            "Rotate the current pane layout clockwise or counterclockwise.",
            schemaObject()
                .withEnum("direction", new String[]{"clockwise", "counterclockwise"}, false, "clockwise")
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_rotate, R.string.tool_desc_pane_rotate, null,
            REQUIRES_SPLITS);
        add(map, TOOL_PANE_MOVE_TO_EDGE,
            "Move the focused pane to an outer edge of the current window.",
            schemaObject()
                .withEnum("edge", new String[]{"left", "right", "up", "down"}, true, "left")
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL);
        // Takes no argument, so unlike pane.layout this one can live in the palette and on a
        // binding. It also retains the layout it lands on, which is what makes later splits re-tile.
        addUi(map, TOOL_PANE_NEXT_LAYOUT,
            "Switch the current window to the next automatic pane layout and keep managing it.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_next_layout, R.string.tool_desc_pane_next_layout,
            Collections.singletonList(Binding.of("ctrl+alt+l", BindingCondition.SPLITS_ON)),
            REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_TOGGLE_FLOAT,
            "Detach the focused pane into a movable floating window, or dock a floating pane back into the tiled layout.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_toggle_float, R.string.tool_desc_pane_toggle_float,
            Collections.singletonList(Binding.of("ctrl+alt+f", BindingCondition.SPLITS_ON)),
            REQUIRES_SPLITS);
        addUi(map, TOOL_TERMINAL_TOGGLE_SCRATCHPAD,
            "Show or hide the scratchpad: a dedicated floating terminal whose shell keeps running while hidden.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_toggle_scratchpad,
            R.string.tool_desc_terminal_toggle_scratchpad,
            Collections.singletonList(Binding.of("ctrl+alt+`", BindingCondition.SPLITS_ON)),
            REQUIRES_SPLITS);
        addUi(map, TOOL_WINDOW_NEW,
            "Create a new window with a fresh shell in the current session.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_WINDOW, R.string.tool_window_new, R.string.tool_desc_window_new, Collections.singletonList(Binding.of("ctrl+alt+c", BindingCondition.SPLITS_ON)), REQUIRES_SPLITS);
        addUi(map, TOOL_WINDOW_CLOSE,
            "Close the current window and every pane in it.",
            schemaEmpty(),
            ToolRisk.HIGH, true, ToolExecutor.TERMINAL,
            CATEGORY_WINDOW, R.string.tool_window_close, R.string.tool_desc_window_close, Collections.singletonList(Binding.of("ctrl+alt+x", BindingCondition.SPLITS_ON)), REQUIRES_SPLITS);
        addUi(map, TOOL_WINDOW_NEXT,
            "Switch to the next window in the current session.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_WINDOW, R.string.tool_window_next, R.string.tool_desc_window_next,
            Collections.singletonList(
                Binding.of("ctrl+alt+]", BindingCondition.SPLITS_ON)), REQUIRES_SPLITS);
        addUi(map, TOOL_WINDOW_PREVIOUS,
            "Switch to the previous window in the current session.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_WINDOW, R.string.tool_window_previous, R.string.tool_desc_window_previous,
            Collections.singletonList(
                Binding.of("ctrl+alt+[", BindingCondition.SPLITS_ON)), REQUIRES_SPLITS);
        addUi(map, TOOL_SESSION_NEW,
            "Create a new terminal session, optionally named or fail-safe.",
            schemaObject()
                .withString("name", "Optional session name", false)
                .withBoolean("failsafe", "Start a fail-safe shell instead of the default login shell", false, false)
                .build(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_new, R.string.tool_desc_session_new, Arrays.asList(
                Binding.of("ctrl+alt+shift+c"),
                Binding.of("ctrl+alt+c", BindingCondition.SPLITS_OFF)));
        addUi(map, TOOL_SESSION_BROWSER,
            "Open the searchable session, window, and pane browser.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_browser, R.string.tool_desc_session_browser,
            null, REQUIRES_SESSION);
        addUi(map, TOOL_SESSION_PANEL,
            "Open or close the sessions panel under the status row.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_panel, R.string.tool_desc_session_panel,
            Binding.all("ctrl+alt+shift+s"), REQUIRES_SESSION);
        addUi(map, TOOL_SESSION_CLONE_CURRENT,
            "Create a fresh terminal session at the current pane's working directory.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_clone_current,
            R.string.tool_desc_session_clone_current, null, REQUIRES_SESSION);
        // Ctrl+Alt+Down/Up is deliberately absent here. Today it means "next/previous
        // session" only while split panes are off; with splits on the multiplexer
        // claims it for pane focus first. Until defaultBindings can express that
        // condition, recording it would advertise a binding that often does
        // something else.
        addUi(map, TOOL_SESSION_NEXT,
            "Switch to the next terminal session.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_next, R.string.tool_desc_session_next, Arrays.asList(
                Binding.of("ctrl+alt+n"),
                Binding.of("ctrl+alt+down", BindingCondition.SPLITS_OFF)));
        addUi(map, TOOL_SESSION_PREVIOUS,
            "Switch to the previous terminal session.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_previous, R.string.tool_desc_session_previous, Arrays.asList(
                Binding.of("ctrl+alt+p"),
                Binding.of("ctrl+alt+up", BindingCondition.SPLITS_OFF)));
        addUi(map, TOOL_SESSION_CLOSE_CURRENT,
            "Close the current session, including all of its windows and panes.",
            schemaEmpty(),
            ToolRisk.HIGH, true, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_close_current, R.string.tool_desc_session_close_current, Collections.singletonList(Binding.of("ctrl+alt+shift+x", BindingCondition.SPLITS_ON)), REQUIRES_SESSION);

        // Terminal view actions. These act on the focused shell or its view, so
        // they all require a current session.
        addUi(map, TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD,
            "Show or hide the keyboard.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_toggle_soft_keyboard, R.string.tool_desc_terminal_toggle_soft_keyboard,
            Binding.all("ctrl+alt+k"), REQUIRES_SESSION);
        addUi(map, TOOL_TERMINAL_TOGGLE_TOOLBAR,
            "Show or hide the dock.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_toggle_toolbar, R.string.tool_desc_terminal_toggle_toolbar, null, REQUIRES_SESSION);
        addUi(map, TOOL_TERMINAL_FONT_SIZE_INCREASE,
            "Increase the terminal font size.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_font_size_increase, R.string.tool_desc_terminal_font_size_increase,
            Binding.all("ctrl+alt+plus", "ctrl+alt+shift+equals"), REQUIRES_SESSION);
        addUi(map, TOOL_TERMINAL_FONT_SIZE_DECREASE,
            "Decrease the terminal font size.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_font_size_decrease, R.string.tool_desc_terminal_font_size_decrease,
            Binding.all("ctrl+alt+minus"), REQUIRES_SESSION);
        addUi(map, TOOL_TERMINAL_SELECT_URL,
            "Open the URL picker for links in the terminal scrollback.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_select_url, R.string.tool_desc_terminal_select_url,
            null, REQUIRES_SESSION);
        // The long press opens the action menu, so the gesture no longer starts selection itself;
        // this is how selection is reached, from the menu, a keybinding or the palette alike.
        addUi(map, TOOL_TERMINAL_SELECT_TEXT,
            "Start text selection in the focused pane.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_select_text,
            R.string.tool_desc_terminal_select_text,
            null, REQUIRES_SESSION);
        addUi(map, TOOL_TERMINAL_HINTS,
            "Show keyboard labels for URLs, paths, hashes, and source line references in scrollback.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_hints, R.string.tool_desc_terminal_hints,
            Binding.all("ctrl+alt+u"), REQUIRES_SESSION);
        addUi(map, TOOL_TERMINAL_SEARCH_SCROLLBACK,
            "Search the focused session's scrollback and jump to a matching row.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_search_scrollback,
            R.string.tool_desc_terminal_search_scrollback,
            Binding.all("ctrl+alt+s"), REQUIRES_SESSION);
        // Sharing sends scrollback contents to another app, so it is confirmed.
        addUi(map, TOOL_TERMINAL_SHARE_TRANSCRIPT,
            "Share the terminal transcript with another app.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_share_transcript, R.string.tool_desc_terminal_share_transcript, null, REQUIRES_SESSION);
        // Pasting writes clipboard contents into the shell's stdin, where a stray
        // newline runs it. Always confirmed.
        addUi(map, TOOL_CLIPBOARD_PASTE,
            "Paste the clipboard contents into the terminal.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_CLIPBOARD, R.string.tool_clipboard_paste, R.string.tool_desc_clipboard_paste,
            Collections.singletonList(Binding.of("ctrl+alt+v", BindingCondition.SPLITS_OFF)),
            REQUIRES_SESSION);

        // Actions carrying a required argument. The palette skips these because it
        // has nowhere to prompt; they are reachable from keybinds and remotely.
        addUi(map, TOOL_WINDOW_SELECT,
            "Switch to a window by its zero-based index in the current session.",
            schemaObject()
                .withInteger("index", "Zero-based window index", 0, 64, 0, true)
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_WINDOW, R.string.tool_window_select, 0, null, REQUIRES_SPLITS);
        // The three rename tools name the three things the UI names, and nothing else:
        // session.rename a drawer row, window.rename a window-bar tab, pane.rename one shell. The
        // ids used to be rotated one step — window.rename renamed the session and session.rename
        // renamed the shell — which made every stroke and every palette row read as a lie and left
        // the window itself unnameable.
        addUi(map, TOOL_WINDOW_RENAME,
            "Rename the current window, the tab it occupies in the window bar. Names are capped"
                + " at 14 characters; an empty name restores the automatic process/directory label.",
            schemaObject()
                .withString("name",
                    "New name, capped at 14 characters. Empty restores the automatic label.", true)
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_WINDOW, R.string.tool_window_rename, 0, null, REQUIRES_SPLITS);
        addUi(map, TOOL_SESSION_RENAME,
            "Rename the current session, the drawer row that holds the windows. Names are capped"
                + " at 8 characters; an empty name clears the label.",
            schemaObject()
                .withString("name",
                    "New name, capped at 8 characters. Empty clears the label.", true)
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_rename, 0, null, REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_RENAME,
            "Rename the focused pane's shell. An empty name restores the unnamed default.",
            schemaObject()
                .withString("name",
                    "New name for the shell. Empty restores the default.", true)
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_rename, 0, null, REQUIRES_SESSION);
        // A separate tool rather than an optional index on session.rename, because it names a
        // session the user is not currently in — the panel's and the palette's session rows can
        // point at any of them.
        //
        // index is declared before name so a positional invocation
        // (`map … session.rename_at_index 1 work`) fills them in the order a reader expects.
        addUi(map, TOOL_SESSION_RENAME_AT_INDEX,
            "Rename a session by its zero-based index. Names are capped at 8"
                + " characters; an empty name clears the label.",
            schemaObject()
                .withInteger("index", "Zero-based session index", 0, 64, 0, true)
                .withString("name",
                    "New name, capped at 8 characters. Empty clears the label.", true)
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_rename_at_index, 0, null, REQUIRES_SPLITS);
        // Reset clears the emulator state and scrollback; the shell survives.
        // The space bar's north swipe opens this, but that lives in the keyboard layout file
        // as a tool: key rather than in a binding here — see bottom_row.xml.
        addUi(map, TOOL_APP_COMMAND_PALETTE,
            "Open the searchable command palette.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APP, R.string.tool_app_command_palette, R.string.tool_desc_app_command_palette,
            Binding.all("ctrl+alt+shift+p", "ctrl+alt+space>p"));
        // Required 'query' keeps this out of the palette's tool rows; the palette
        // contributes one Apps row per installed app instead, and each row runs
        // this tool. Unbound by default: which app deserves a stroke is personal.
        addUi(map, TOOL_APP_LAUNCH,
            "Launch an installed app by package name, label, or stable id.",
            schemaObject()
                .withString("query", "Package name, app label, or stableId to launch", true)
                .build(),
            ToolRisk.MEDIUM, false, ToolExecutor.TERMINAL,
            CATEGORY_APPS, R.string.tool_app_launch, R.string.tool_desc_app_launch, null);
        addUi(map, TOOL_TERMINAL_ACTION_SHEET,
            "Open the terminal action sheet.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_action_sheet, R.string.tool_desc_terminal_action_sheet,
            Binding.all("ctrl+alt+m"), REQUIRES_SESSION);
        // Ctrl+Alt+left/right reach the drawer only with split panes off; with them
        // on the multiplexer claims the arrows for pane focus.
        addUi(map, TOOL_APP_OPEN_DRAWER,
            "Open the sessions drawer.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APP, R.string.tool_app_open_drawer, R.string.tool_desc_app_open_drawer,
            Collections.singletonList(Binding.of("ctrl+alt+right", BindingCondition.SPLITS_OFF)));
        addUi(map, TOOL_APP_CLOSE_DRAWER,
            "Close the sessions drawer.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APP, R.string.tool_app_close_drawer, R.string.tool_desc_app_close_drawer,
            Collections.singletonList(Binding.of("ctrl+alt+left", BindingCondition.SPLITS_OFF)));
        // The digit strokes supply the index, so this needs no palette entry of its
        // own; the resolver derives the argument from the key.
        addUi(map, TOOL_SESSION_ACTIVATE_BY_INDEX,
            "Switch to a session by its one-based position in the drawer.",
            schemaObject()
                .withInteger("index", "Zero-based session index", 0, 64, 0, true)
                .build(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_activate_by_index, 0,
            Binding.all("ctrl+alt+1", "ctrl+alt+2", "ctrl+alt+3", "ctrl+alt+4", "ctrl+alt+5",
                "ctrl+alt+6", "ctrl+alt+7", "ctrl+alt+8", "ctrl+alt+9"));
        // Prompt variants open the anchored rename editor; the argument-taking window.rename /
        // session.rename / pane.rename remain the remote and scripted path.
        //
        // Case is the split, and each case names a different thing rather than the same thing twice:
        // Ctrl+Alt+r renames the window (the tab), Ctrl+Alt+R (shifted) renames the session (the
        // drawer row). With splits off there is no window and no session, so Ctrl+Alt+r names the
        // pane instead of being dead. The pane keeps no shifted stroke: renaming individual shells
        // belongs to the palette and the drawer, and a third stroke here would be one nobody
        // remembers.
        addUi(map, TOOL_WINDOW_RENAME_PROMPT,
            "Open the rename editor for the current window.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_WINDOW, R.string.tool_window_rename_prompt, R.string.tool_desc_window_rename_prompt,
            Collections.singletonList(Binding.of("ctrl+alt+r", BindingCondition.SPLITS_ON)),
            REQUIRES_SPLITS);
        addUi(map, TOOL_SESSION_RENAME_PROMPT,
            "Open the rename editor for the current session.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_SESSION, R.string.tool_session_rename_prompt, R.string.tool_desc_session_rename_prompt,
            Collections.singletonList(Binding.of("ctrl+alt+shift+r", BindingCondition.SPLITS_ON)),
            REQUIRES_SPLITS);
        addUi(map, TOOL_PANE_RENAME_PROMPT,
            "Open the rename editor for the focused pane's shell.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_PANE, R.string.tool_pane_rename_prompt, R.string.tool_desc_pane_rename_prompt,
            Collections.singletonList(Binding.of("ctrl+alt+r", BindingCondition.SPLITS_OFF)),
            REQUIRES_SESSION);

        // Selection-dependent actions. Availability tracks the live selection, so
        // these grey out with a reason instead of silently doing nothing.
        addUi(map, TOOL_TERMINAL_SHARE_SELECTED,
            "Share the currently selected terminal text with another app.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_share_selected,
            R.string.tool_desc_terminal_share_selected, null, REQUIRES_SELECTION);
        addUi(map, TOOL_CLIPBOARD_COPY_SELECTED,
            "Copy the currently selected terminal text to the clipboard.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_CLIPBOARD, R.string.tool_clipboard_copy_selected,
            R.string.tool_desc_clipboard_copy_selected, null, REQUIRES_SELECTION);

        // Appearance and settings navigation. These act on the app rather than a
        // shell, so they carry no session requirement.
        addUi(map, TOOL_APPEARANCE_SET_WALLPAPER,
            "Open the wallpaper picker.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APPEARANCE, R.string.tool_appearance_set_wallpaper, R.string.tool_desc_appearance_set_wallpaper, null);
        // Persists an appearance change, so it is confirmed for remote callers.
        addUi(map, TOOL_APPEARANCE_TOGGLE_WALLPAPER,
            "Turn the terminal background wallpaper on or off.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_APPEARANCE, R.string.tool_appearance_toggle_wallpaper, R.string.tool_desc_appearance_toggle_wallpaper, null);
        // Persists an appearance change, so it is confirmed for remote callers.
        addUi(map, TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL,
            "Turn the animated cursor trail on or off.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_APPEARANCE, R.string.tool_appearance_toggle_cursor_trail, R.string.tool_desc_appearance_toggle_cursor_trail, null);
        addUi(map, TOOL_APPEARANCE_GLASS_LAB,
            "Enter the dock and surface tuning mode.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APPEARANCE, R.string.tool_appearance_glass_lab, R.string.tool_desc_appearance_glass_lab, null);
        addUi(map, TOOL_APP_OPEN_SETTINGS,
            "Open the launcher settings screen.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APP, R.string.tool_app_open_settings, R.string.tool_desc_app_open_settings, null);
        addUi(map, TOOL_APP_OPEN_LOOK_AND_FEEL,
            "Open the look and feel settings.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APP, R.string.tool_app_open_look_and_feel, R.string.tool_desc_app_open_look_and_feel, null);
        addUi(map, TOOL_APP_OPEN_APPS_BAR,
            "Open the apps bar settings.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APP, R.string.tool_app_open_apps_bar, R.string.tool_desc_app_open_apps_bar, null);
        addUi(map, TOOL_TERMINAL_RESET,
            "Reset the terminal emulator state for the focused shell.",
            schemaEmpty(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_reset, R.string.tool_desc_terminal_reset, null, REQUIRES_SESSION);
        // Both need the shell to emit OSC 133 marks, which REQUIRES_SESSION cannot express; they report
        // "no prompt marks" at execution time rather than being greyed out.
        // A diagnostic, so it is bound to nothing by default: an inspector that needs a chord to open
        // cannot report that chord's own events.
        addUi(map, TOOL_APP_KEY_INSPECTOR,
            "Open or close the key inspector overlay, which reports each key event and the bytes it sends.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APP, R.string.tool_app_key_inspector, R.string.tool_desc_app_key_inspector, null);
        addUi(map, TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT,
            "Scroll the terminal to the previous shell prompt. Needs OSC 133 shell integration.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_jump_previous_prompt, R.string.tool_desc_terminal_jump_previous_prompt,
            null, REQUIRES_SESSION);
        addUi(map, TOOL_TERMINAL_JUMP_NEXT_PROMPT,
            "Scroll the terminal to the next shell prompt. Needs OSC 133 shell integration.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_TERMINAL, R.string.tool_terminal_jump_next_prompt, R.string.tool_desc_terminal_jump_next_prompt,
            null, REQUIRES_SESSION);

        // Terminal fonts. Appearance rather than a category of their own: a font is what the
        // terminal looks like, and a one-tool section reads as a mistake in the palette.
        addUi(map, TOOL_FONTS_PICK,
            "Open the terminal font picker: bundled catalog of installable families, and the"
                + " Nerd icon, ligature and weight toggles.",
            schemaEmpty(),
            ToolRisk.LOW, false, ToolExecutor.TERMINAL,
            CATEGORY_APPEARANCE, R.string.tool_fonts_pick, R.string.tool_desc_fonts_pick, null);
        // Required 'id' keeps this out of the palette's tool rows, the same way app.launch's
        // 'query' does; the picker screen is the interactive front door. MEDIUM and confirmed
        // because it spends multiple megabytes of the user's data and then changes every glyph
        // on screen.
        addUi(map, TOOL_FONTS_INSTALL,
            "Download, verify and activate a font family from the bundled catalog, writing"
                + " ~/.termux/fonts.d/10-launcher.conf. Toggles default to the family's own"
                + " recommended values when omitted.",
            schemaObject()
                .withString("id", "Catalog family id, for example maple-mono", true)
                .withBoolean("nerd_icons", "Map U+E000-U+F8FF to the bundled Nerd Font symbols face",
                    false, true)
                .withEnum("ligatures", new String[]{"never", "cursor", "always"}, false, "cursor")
                .withInteger("weight",
                    "Regular-face wght for a variable family; 0 keeps the family default",
                    0, 1000, 0, false)
                .build(),
            ToolRisk.MEDIUM, true, ToolExecutor.TERMINAL,
            CATEGORY_APPEARANCE, R.string.tool_fonts_install, 0, null);
    }

    @NonNull
    public static synchronized LauncherToolRegistry getInstance() {
        if (instance == null) {
            instance = new LauncherToolRegistry();
        }
        return instance;
    }

    /** Resets the singleton for unit tests. */
    static synchronized void resetForTesting() {
        instance = null;
    }

    @NonNull
    public List<ToolMetadata> getTools() {
        return new ArrayList<>(tools.values());
    }

    @Nullable
    public ToolMetadata getTool(@Nullable String name) {
        return name == null ? null : tools.get(name);
    }

    /** Tools carrying UI metadata, in registration order. */
    @NonNull
    public List<ToolMetadata> getUiTools() {
        List<ToolMetadata> out = new ArrayList<>();
        for (ToolMetadata tool : tools.values()) {
            if (tool.hasUiMetadata()) {
                out.add(tool);
            }
        }
        return out;
    }

    /**
     * UI tools grouped by {@link ToolMetadata#category}, preserving registration
     * order both between and within groups. Tools without a category are omitted.
     */
    @NonNull
    public Map<String, List<ToolMetadata>> getUiToolsByCategory() {
        Map<String, List<ToolMetadata>> grouped = new LinkedHashMap<>();
        for (ToolMetadata tool : tools.values()) {
            if (tool.category == null) continue;
            List<ToolMetadata> group = grouped.get(tool.category);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(tool.category, group);
            }
            group.add(tool);
        }
        return grouped;
    }

    private static void add(
        @NonNull Map<String, ToolMetadata> map,
        @NonNull String name,
        @NonNull String description,
        @NonNull JSONObject schema,
        @NonNull ToolRisk risk,
        boolean requiresConfirmation,
        @NonNull ToolExecutor executor
    ) {
        map.put(name, new ToolMetadata(name, description, schema, risk, requiresConfirmation, executor));
    }

    /**
     * Registers a tool that is also exposed in the command palette / action sheet.
     *
     * <p>Only add a tool here once {@code TerminalActionDispatcher} can execute
     * it, otherwise the palette advertises an action that answers 501.
     */
    private static void addUi(
        @NonNull Map<String, ToolMetadata> map,
        @NonNull String name,
        @NonNull String description,
        @NonNull JSONObject schema,
        @NonNull ToolRisk risk,
        boolean requiresConfirmation,
        @NonNull ToolExecutor executor,
        @NonNull String category,
        @StringRes int titleRes,
        @StringRes int descriptionRes,
        @Nullable List<Binding> defaultBindings
    ) {
        addUi(map, name, description, schema, risk, requiresConfirmation, executor,
            category, titleRes, descriptionRes, defaultBindings, null);
    }

    private static void addUi(
        @NonNull Map<String, ToolMetadata> map,
        @NonNull String name,
        @NonNull String description,
        @NonNull JSONObject schema,
        @NonNull ToolRisk risk,
        boolean requiresConfirmation,
        @NonNull ToolExecutor executor,
        @NonNull String category,
        @StringRes int titleRes,
        @StringRes int descriptionRes,
        @Nullable List<Binding> defaultBindings,
        @Nullable AvailabilityPredicate availability
    ) {
        map.put(name, new ToolMetadata(name, description, schema, risk, requiresConfirmation, executor,
            category, titleRes, descriptionRes, defaultBindings, availability));
    }

    /** Pane and window actions are meaningless while compatibility mode is on. */
    private static final AvailabilityPredicate REQUIRES_SPLITS = context ->
        context.isSplitPanesEnabled()
            ? Availability.available()
            : Availability.unavailable(R.string.palette_unavailable_splits_disabled);

    /** Actions that operate on the current text selection. */
    private static final AvailabilityPredicate REQUIRES_SELECTION = context ->
        context.hasSelectedText()
            ? Availability.available()
            : Availability.unavailable(R.string.palette_unavailable_no_selection);

    /** Actions that act on the focused shell. */
    private static final AvailabilityPredicate REQUIRES_SESSION = context ->
        context.hasCurrentSession()
            ? Availability.available()
            : Availability.unavailable(R.string.palette_unavailable_no_session);

    private static JSONObject schemaEmpty() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
            schema.put("additionalProperties", false);
        } catch (JSONException ignored) {
        }
        return schema;
    }

    private static SchemaBuilder schemaObject() {
        return new SchemaBuilder();
    }

    private static final class SchemaBuilder {
        private final JSONObject properties = new JSONObject();
        private final JSONArray required = new JSONArray();

        SchemaBuilder withString(@NonNull String name, @NonNull String description, boolean required) {
            return withString(name, description, required, null);
        }

        SchemaBuilder withString(@NonNull String name, @NonNull String description, boolean required, @Nullable String defaultValue) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "string");
                prop.put("description", description);
                if (defaultValue != null) {
                    prop.put("default", defaultValue);
                }
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withInteger(@NonNull String name, @NonNull String description, boolean required) {
            return withInteger(name, description, 0, Integer.MAX_VALUE, 0, required);
        }

        SchemaBuilder withInteger(@NonNull String name, int minimum, int maximum, int defaultValue, boolean required) {
            return withInteger(name, "", minimum, maximum, defaultValue, required);
        }

        SchemaBuilder withInteger(@NonNull String name, @NonNull String description, int minimum, int maximum, int defaultValue, boolean required) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "integer");
                prop.put("minimum", minimum);
                prop.put("maximum", maximum);
                prop.put("default", defaultValue);
                if (!description.isEmpty()) {
                    prop.put("description", description);
                }
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withLong(@NonNull String name, @NonNull String description, long minimum, long maximum, long defaultValue, boolean required) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "integer");
                prop.put("minimum", minimum);
                prop.put("maximum", maximum);
                prop.put("default", defaultValue);
                if (!description.isEmpty()) {
                    prop.put("description", description);
                }
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withBoolean(@NonNull String name, @NonNull String description, boolean required, boolean defaultValue) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "boolean");
                prop.put("description", description);
                prop.put("default", defaultValue);
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withObject(@NonNull String name, @NonNull String description, boolean required) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "object");
                prop.put("description", description);
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withEnum(@NonNull String name, @NonNull String[] values, boolean required, @NonNull String defaultValue) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "string");
                JSONArray enumArray = new JSONArray();
                for (String value : values) {
                    enumArray.put(value);
                }
                prop.put("enum", enumArray);
                prop.put("default", defaultValue);
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        JSONObject build() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                schema.put("properties", properties);
                if (required.length() > 0) {
                    schema.put("required", required);
                }
                schema.put("additionalProperties", false);
            } catch (JSONException ignored) {
            }
            return schema;
        }
    }

}
