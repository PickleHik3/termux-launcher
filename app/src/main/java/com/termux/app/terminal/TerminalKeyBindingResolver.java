package com.termux.app.terminal;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a hardware {@link KeyEvent} to a registry action ID using the
 * {@code defaultBindings} declared in {@link LauncherToolRegistry}.
 *
 * <p>This replaces the hard-coded multiplexer {@code switch} and the legacy
 * {@code Ctrl+Alt}+character chain, so a keystroke and a palette entry name
 * the same action. The registry is the single source of truth for which stroke
 * means what.
 *
 * <p>Strokes match on <b>key code</b>, not on the produced character, which keeps
 * binds on physical US key positions and reachable from non-Latin layouts. The
 * previous legacy chain matched {@code getUnicodeChar}; moving to key codes is the
 * one intentional behavior change, and it makes the binds work on layouts where
 * the old chain silently did nothing.
 *
 * <p>A stroke may be claimed by several tools under
 * {@link LauncherToolRegistry.BindingCondition}s that cannot both hold —
 * {@code Ctrl+Alt+V} splits a pane with split panes on and pastes with them off.
 * Resolution picks the first binding whose condition holds. Two claims on the same
 * stroke with overlapping conditions are a real conflict: the first registration
 * wins and the clash is recorded in {@link #getConflicts()}.
 */
public final class TerminalKeyBindingResolver {

    private static final String LOG_TAG = "TerminalKeyBindingResolver";

    /** A resolved action plus the arguments derived from the stroke itself. */
    public static final class Match {
        public final String toolName;
        @NonNull public final List<TerminalBindingConfig.Action> actions;
        public final JSONObject arguments;
        /** The normalized stroke that matched, e.g. {@code ctrl+alt+shift+left}. */
        public final String stroke;
        public final LauncherToolRegistry.BindingCondition condition;
        /** Empty for a root-keymap match. */
        @NonNull public final String mode;

        Match(@NonNull Claim claim, @NonNull JSONObject arguments, @NonNull String stroke,
              @NonNull String mode) {
            this.actions = claim.actions;
            this.toolName = claim.toolName;
            this.arguments = arguments;
            this.stroke = stroke;
            this.condition = claim.condition;
            this.mode = mode;
        }
    }

    /** Result of feeding one key-down event into the sequence state machine. */
    public static final class Step {
        public enum Kind { NONE, PENDING, MATCH, CANCELLED, IGNORED, PASSTHROUGH }

        public final Kind kind;
        @Nullable public final Match match;
        /** Human-readable normalized sequence accumulated so far. */
        @Nullable public final String pendingSequence;

        private Step(@NonNull Kind kind, @Nullable Match match, @Nullable String pendingSequence) {
            this.kind = kind;
            this.match = match;
            this.pendingSequence = pendingSequence;
        }

        static Step none() { return new Step(Kind.NONE, null, null); }
        static Step pending(@NonNull String sequence) {
            return new Step(Kind.PENDING, null, sequence);
        }
        static Step match(@NonNull Match match) { return new Step(Kind.MATCH, match, null); }
        static Step cancelled() { return new Step(Kind.CANCELLED, null, null); }
        static Step ignored() { return new Step(Kind.IGNORED, null, null); }
        static Step passthrough() { return new Step(Kind.PASSTHROUGH, null, null); }
    }

    /** One claim on a stroke. */
    static final class Claim {
        final String toolName;
        final LauncherToolRegistry.BindingCondition condition;
        @NonNull final List<TerminalBindingConfig.Action> actions;
        /** Display name the user gave this binding with {@code --label}, else null. */
        @Nullable final String label;

        Claim(@NonNull String toolName, @NonNull LauncherToolRegistry.BindingCondition condition) {
            this(Collections.singletonList(TerminalBindingConfig.Action.tool(toolName)), condition,
                null);
        }

        Claim(@NonNull List<TerminalBindingConfig.Action> actions,
              @NonNull LauncherToolRegistry.BindingCondition condition, @Nullable String label) {
            this.toolName = actions.isEmpty() ? "unmap" : actions.get(0).diagnosticName();
            this.condition = condition;
            this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
            this.label = label;
        }
    }

    /** What a stroke under a latched prefix does, as the keybind hint legend needs to print it. */
    public static final class Hint {
        @NonNull public final String toolName;
        /** The binding's {@code --label}, or null to fall back to the action's own title. */
        @Nullable public final String label;

        Hint(@NonNull String toolName, @Nullable String label) {
            this.toolName = toolName;
            this.label = label;
        }

        /** Part of the popup's repopulate signature, so a renamed binding redraws the legend. */
        @NonNull
        @Override
        public String toString() {
            return label == null ? toolName : toolName + "=" + label;
        }
    }

    private static TerminalKeyBindingResolver instance;

    /** stroke -> claims, in registration order. */
    private final Map<String, List<Claim>> bindings;
    /** Sequence prefixes that can lead to at least one full binding. */
    private final Map<String, List<Claim>> prefixes;
    private final Map<String, Map<String, List<Claim>>> modalBindings;
    private final Map<String, Map<String, List<Claim>>> modalPrefixes;
    @NonNull private final Map<String, TerminalBindingConfig.Mode> modes;
    /** Strokes claimed twice under conditions that can both hold. */
    private final Map<String, List<String>> conflicts;
    @NonNull private final List<String> configErrors;
    private final List<String> pendingStrokes = new ArrayList<>();
    private final List<String> modeStack = new ArrayList<>();

    private TerminalKeyBindingResolver(@NonNull LauncherToolRegistry registry,
                                       @NonNull TerminalBindingConfig.Result config) {
        Map<String, List<Claim>> map = new LinkedHashMap<>();
        Map<String, List<String>> clashes = new LinkedHashMap<>();
        for (LauncherToolRegistry.ToolMetadata tool : registry.getUiTools()) {
            for (LauncherToolRegistry.Binding binding : tool.defaultBindings) {
                String stroke = normalizeSequenceSpec(binding.stroke);
                List<Claim> claims = map.get(stroke);
                if (claims == null) {
                    claims = new ArrayList<>(2);
                    map.put(stroke, claims);
                }
                Claim clashing = null;
                for (Claim existing : claims) {
                    if (existing.condition.overlaps(binding.condition)) {
                        clashing = existing;
                        break;
                    }
                }
                if (clashing != null) {
                    List<String> claimants = clashes.get(stroke);
                    if (claimants == null) {
                        claimants = new ArrayList<>();
                        claimants.add(clashing.toolName);
                        clashes.put(stroke, claimants);
                    }
                    claimants.add(tool.name);
                    Logger.logWarn(LOG_TAG, "Binding '" + stroke + "' claimed by both '"
                        + clashing.toolName + "' (" + clashing.condition.label + ") and '"
                        + tool.name + "' (" + binding.condition.label + "); keeping the first");
                    continue;
                }
                claims.add(new Claim(tool.name, binding.condition));

            }
        }

        // Mentioning a sequence in the user file replaces all defaults for that
        // exact sequence. Repeating map lines for the same condition creates one
        // ordered action list; unmap leaves the sequence absent.
        for (String sequence : config.overriddenSequences) map.remove(sequence);
        for (TerminalBindingConfig.Mapping mapping : config.mappings) {
            if (!mapping.mode.isEmpty()) continue;
            List<Claim> claims = map.get(mapping.sequence);
            if (claims == null) {
                claims = new ArrayList<>(2);
                map.put(mapping.sequence, claims);
            }
            Claim configured = new Claim(mapping.actions, mapping.condition, mapping.label);
            Claim clashing = null;
            for (Claim existing : claims) {
                if (existing.condition.overlaps(configured.condition)) {
                    clashing = existing;
                    break;
                }
            }
            if (clashing != null) {
                List<String> claimants = clashes.get(mapping.sequence);
                if (claimants == null) {
                    claimants = new ArrayList<>();
                    claimants.add(clashing.toolName);
                    clashes.put(mapping.sequence, claimants);
                }
                claimants.add(configured.toolName);
                continue;
            }
            claims.add(configured);
        }

        Map<String, List<Claim>> prefixMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<Claim>> entry : map.entrySet()) {
            List<String> sequence = splitSequence(entry.getKey());
            for (int i = 1; i < sequence.size(); i++) {
                String prefix = joinSequence(sequence.subList(0, i));
                List<Claim> prefixClaims = prefixMap.get(prefix);
                if (prefixClaims == null) {
                    prefixClaims = new ArrayList<>();
                    prefixMap.put(prefix, prefixClaims);
                }
                prefixClaims.addAll(entry.getValue());
            }
        }
        Map<String, List<Claim>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<Claim>> entry : map.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        bindings = Collections.unmodifiableMap(frozen);
        Map<String, List<Claim>> frozenPrefixes = new LinkedHashMap<>();
        for (Map.Entry<String, List<Claim>> entry : prefixMap.entrySet()) {
            frozenPrefixes.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        prefixes = Collections.unmodifiableMap(frozenPrefixes);

        Map<String, Map<String, List<Claim>>> modeTables = new LinkedHashMap<>();
        for (String mode : config.modes.keySet()) modeTables.put(mode, new LinkedHashMap<>());
        for (TerminalBindingConfig.Mapping mapping : config.mappings) {
            if (mapping.mode.isEmpty()) continue;
            Map<String, List<Claim>> table = modeTables.get(mapping.mode);
            if (table == null) continue;
            List<Claim> claims = table.get(mapping.sequence);
            if (claims == null) {
                claims = new ArrayList<>(2);
                table.put(mapping.sequence, claims);
            }
            Claim configured = new Claim(mapping.actions, mapping.condition, mapping.label);
            Claim clashing = null;
            for (Claim existing : claims) {
                if (existing.condition.overlaps(configured.condition)) {
                    clashing = existing;
                    break;
                }
            }
            if (clashing != null) {
                String conflictKey = mapping.mode + ":" + mapping.sequence;
                List<String> claimants = clashes.get(conflictKey);
                if (claimants == null) {
                    claimants = new ArrayList<>();
                    claimants.add(clashing.toolName);
                    clashes.put(conflictKey, claimants);
                }
                claimants.add(configured.toolName);
            } else {
                claims.add(configured);
            }
        }
        Map<String, Map<String, List<Claim>>> frozenModes = new LinkedHashMap<>();
        Map<String, Map<String, List<Claim>>> frozenModePrefixes = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<Claim>>> entry : modeTables.entrySet()) {
            Map<String, List<Claim>> frozenTable = freezeTable(entry.getValue());
            frozenModes.put(entry.getKey(), frozenTable);
            frozenModePrefixes.put(entry.getKey(), freezeTable(buildPrefixes(entry.getValue())));
        }
        modalBindings = Collections.unmodifiableMap(frozenModes);
        modalPrefixes = Collections.unmodifiableMap(frozenModePrefixes);
        modes = config.modes;
        conflicts = Collections.unmodifiableMap(clashes);
        configErrors = config.errors;
    }

    @NonNull
    private static Map<String, List<Claim>> buildPrefixes(@NonNull Map<String, List<Claim>> table) {
        Map<String, List<Claim>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Claim>> entry : table.entrySet()) {
            List<String> sequence = splitSequence(entry.getKey());
            for (int i = 1; i < sequence.size(); i++) {
                String prefix = joinSequence(sequence.subList(0, i));
                List<Claim> claims = result.get(prefix);
                if (claims == null) {
                    claims = new ArrayList<>();
                    result.put(prefix, claims);
                }
                claims.addAll(entry.getValue());
            }
        }
        return result;
    }

    @NonNull
    private static Map<String, List<Claim>> freezeTable(@NonNull Map<String, List<Claim>> table) {
        Map<String, List<Claim>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Claim>> entry : table.entrySet())
            result.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        return Collections.unmodifiableMap(result);
    }

    @NonNull
    public static synchronized TerminalKeyBindingResolver getInstance() {
        if (instance == null) {
            LauncherToolRegistry registry = LauncherToolRegistry.getInstance();
            instance = new TerminalKeyBindingResolver(registry, TerminalBindingConfig.load(registry));
        }
        return instance;
    }

    /** Atomically reparses the user file; existing callers see old or new, never a partial table. */
    @NonNull
    public static synchronized TerminalKeyBindingResolver reloadUserBindings() {
        LauncherToolRegistry registry = LauncherToolRegistry.getInstance();
        instance = new TerminalKeyBindingResolver(registry, TerminalBindingConfig.load(registry));
        return instance;
    }

    /** Resets the singleton for unit tests. */
    static synchronized void resetForTesting() {
        instance = null;
    }

    static synchronized void installConfigForTesting(@NonNull TerminalBindingConfig.Result config) {
        instance = new TerminalKeyBindingResolver(LauncherToolRegistry.getInstance(), config);
    }

    /** Immutable stroke -> claims view, for the binding editor and diagnostics. */
    @NonNull
    public Map<String, List<Claim>> getBindings() {
        return bindings;
    }

    /**
     * Root-keymap bindings exactly one key beyond {@code prefix} (e.g. {@code "ctrl+alt+"}), as
     * suffix key -> claiming tool name and display name under {@code context}, in registration
     * order. Suffixes that add another modifier or start a sequence are excluded. Drives the
     * in-app keyboard's modifier hint popup.
     */
    @NonNull
    public Map<String, Hint> hintsForPrefix(@NonNull String prefix,
                                            @NonNull LauncherToolRegistry.ActionContext context) {
        Map<String, Hint> hints = new LinkedHashMap<>();
        for (Map.Entry<String, List<Claim>> entry : bindings.entrySet()) {
            String stroke = entry.getKey();
            if (!stroke.startsWith(prefix)) continue;
            String suffix = stroke.substring(prefix.length());
            if (suffix.isEmpty() || suffix.indexOf('+') >= 0 || suffix.indexOf(' ') >= 0) continue;
            // Prefer the claim whose condition holds right now, but keep showing a configured
            // binding whose condition doesn't (splits off, nothing selected): the hint map
            // documents everything the config binds under the prefix, not just what would fire.
            Claim claim = firstHolding(entry.getValue(), context);
            if (claim == null) {
                for (Claim candidate : entry.getValue()) {
                    if (!"unmap".equals(candidate.toolName)) { claim = candidate; break; }
                }
            }
            if (claim == null || "unmap".equals(claim.toolName)) continue;
            hints.put(suffix, new Hint(claim.toolName, claim.label));
        }
        return hints;
    }

    /** Strokes claimed twice under conditions that can both hold. */
    @NonNull
    public Map<String, List<String>> getConflicts() {
        return conflicts;
    }

    /** Non-fatal user-file diagnostics from the most recent reload. */
    @NonNull
    public List<String> getConfigErrors() {
        return configErrors;
    }

    /** Effective (default plus user overrides) sequences that invoke a tool now. */
    @NonNull
    public List<String> getStrokesForTool(@NonNull String toolName,
                                          @NonNull LauncherToolRegistry.ActionContext context) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<Claim>> entry : bindings.entrySet()) {
            Claim claim = firstHolding(entry.getValue(), context);
            if (claim == null) continue;
            for (TerminalBindingConfig.Action action : claim.actions) {
                if (action.type == TerminalBindingConfig.ActionType.TOOL
                    && toolName.equals(action.value)) {
                    result.add(entry.getKey());
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Strokes bound to {@code toolName}, keyed by the value each one passes for
     * {@code argumentName}. {@link #getStrokesForTool} cannot answer this: one tool backs many
     * rows — every app row runs {@code app.launch} — and only the argument tells them apart, so a
     * row that advertised the tool's first stroke could promise a chord that launches a different
     * app. The first stroke wins per value, matching what the rest of the palette shows.
     */
    @NonNull
    public Map<String, String> getArgumentStrokesForTool(
            @NonNull String toolName, @NonNull String argumentName,
            @NonNull LauncherToolRegistry.ActionContext context) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<Claim>> entry : bindings.entrySet()) {
            Claim claim = firstHolding(entry.getValue(), context);
            if (claim == null) continue;
            for (TerminalBindingConfig.Action action : claim.actions) {
                if (action.type != TerminalBindingConfig.ActionType.TOOL
                    || !toolName.equals(action.value)) continue;
                String value = action.arguments.optString(argumentName, "");
                if (value.isEmpty() || result.containsKey(value)) continue;
                result.put(value, entry.getKey());
            }
        }
        return result;
    }

    /** Whether a multi-stroke binding is currently waiting for another key. */
    public synchronized boolean hasPendingSequence() {
        return !pendingStrokes.isEmpty();
    }

    /** Cancels a pending sequence. Returns true when there was one to cancel. */
    public synchronized boolean cancelPendingSequence() {
        if (pendingStrokes.isEmpty()) return false;
        pendingStrokes.clear();
        return true;
    }

    @NonNull
    public synchronized String getCurrentMode() {
        return modeStack.isEmpty() ? "" : modeStack.get(modeStack.size() - 1);
    }

    public synchronized long getCurrentModeTimeoutMillis() {
        TerminalBindingConfig.Mode mode = modes.get(getCurrentMode());
        return mode == null ? 0 : mode.timeoutMillis;
    }

    public synchronized boolean pushMode(@NonNull String mode) {
        if (!modes.containsKey(mode)) return false;
        pendingStrokes.clear();
        modeStack.add(mode);
        return true;
    }

    public synchronized boolean popMode() {
        pendingStrokes.clear();
        if (modeStack.isEmpty()) return false;
        modeStack.remove(modeStack.size() - 1);
        return true;
    }

    public synchronized boolean popCurrentModeOnTimeout() {
        return popMode();
    }

    public synchronized boolean clearModes() {
        pendingStrokes.clear();
        if (modeStack.isEmpty()) return false;
        modeStack.clear();
        return true;
    }

    /** Applies a mode's post-action policy without accidentally popping a newly pushed mode. */
    public synchronized boolean afterMatch(@NonNull Match match) {
        TerminalBindingConfig.Mode mode = modes.get(match.mode);
        if (mode == null || !mode.endOnAction || !match.mode.equals(getCurrentMode())) return false;
        return popMode();
    }

    /**
     * Feeds one key-down event to the multi-stroke state machine.
     *
     * <p>The caller owns the timeout so it can also update UI. A valid prefix is
     * consumed and reported as {@link Step.Kind#PENDING}. Escape and an unknown
     * continuation cancel and consume the sequence, matching kitty's default
     * {@code on_unknown=beep} behavior. Timeout cancellation discards buffered
     * keys, also matching kitty.
     */
    @NonNull
    public synchronized Step advance(@NonNull KeyEvent event,
                                     @NonNull LauncherToolRegistry.ActionContext context) {
        if (!pendingStrokes.isEmpty() && event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
            pendingStrokes.clear();
            return Step.cancelled();
        }

        String stroke = strokeFor(event);
        if (stroke == null) {
            if (!pendingStrokes.isEmpty()) {
                pendingStrokes.clear();
                return Step.cancelled();
            }
            return Step.none();
        }

        List<String> candidateStrokes = new ArrayList<>(pendingStrokes.size() + 1);
        candidateStrokes.addAll(pendingStrokes);
        candidateStrokes.add(stroke);
        String candidate = joinSequence(candidateStrokes);

        String modeName = getCurrentMode();
        Map<String, List<Claim>> activeBindings = modeName.isEmpty()
            ? bindings : modalBindings.get(modeName);
        Map<String, List<Claim>> activePrefixes = modeName.isEmpty()
            ? prefixes : modalPrefixes.get(modeName);

        Claim exact = firstHolding(activeBindings == null ? null : activeBindings.get(candidate), context);
        Claim prefix = firstHolding(activePrefixes == null ? null : activePrefixes.get(candidate), context);
        if (prefix != null) {
            // A longer sequence wins over a same-prefix single action. This is
            // how a leader key remains useful; config validation reports the
            // shadowed exact mapping later rather than making the sequence dead.
            pendingStrokes.clear();
            pendingStrokes.addAll(candidateStrokes);
            return Step.pending(candidate);
        }
        if (exact != null) {
            pendingStrokes.clear();
            return Step.match(new Match(exact, argumentsFor(event.getKeyCode()), candidate, modeName));
        }
        if (!pendingStrokes.isEmpty()) {
            pendingStrokes.clear();
            return Step.cancelled();
        }
        if (!modeName.isEmpty()) {
            TerminalBindingConfig.Mode mode = modes.get(modeName);
            if (mode == null) return Step.passthrough();
            switch (mode.onUnknown) {
                case IGNORE: return Step.ignored();
                case PASSTHROUGH: return Step.passthrough();
                case END:
                    popMode();
                    return Step.cancelled();
                case BEEP:
                default: return Step.cancelled();
            }
        }
        return Step.none();
    }

    @Nullable
    private static Claim firstHolding(@Nullable List<Claim> claims,
                                      @NonNull LauncherToolRegistry.ActionContext context) {
        if (claims == null) return null;
        for (Claim claim : claims) {
            if (claim.condition.holds(context)) return claim;
        }
        return null;
    }

    /**
     * Resolves a key event against the current mode, or returns {@code null} when
     * nothing matches so the caller can pass the event through untouched.
     *
     * <p>Returns {@code null} immediately unless Ctrl and Alt are both held, so
     * ordinary typing never pays for a table lookup.
     */
    @Nullable
    public Match resolve(@NonNull KeyEvent event, @NonNull LauncherToolRegistry.ActionContext context) {
        String stroke = strokeFor(event);
        if (stroke == null) {
            return null;
        }
        String modeName = getCurrentMode();
        Map<String, List<Claim>> table = modeName.isEmpty() ? bindings : modalBindings.get(modeName);
        List<Claim> claims = table == null ? null : table.get(stroke);
        if (claims == null) {
            return null;
        }
        for (Claim claim : claims) {
            if (claim.condition.holds(context)) {
                return new Match(claim, argumentsFor(event.getKeyCode()), stroke, modeName);
            }
        }
        return null;
    }

    /** Builds the normalized stroke for an event, or null for an unmappable key code. */
    @Nullable
    static String strokeFor(@NonNull KeyEvent event) {
        String key = keyToken(event.getKeyCode());
        if (key == null) {
            return null;
        }
        StringBuilder stroke = new StringBuilder();
        if (event.isCtrlPressed()) stroke.append("ctrl+");
        if (event.isAltPressed()) stroke.append("alt+");
        if (event.isShiftPressed()) stroke.append("shift+");
        return stroke.append(key).toString();
    }

    /**
     * Arguments a stroke implies on its own: a direction for the arrow binds and a
     * zero-based index for the digit binds. One tool therefore covers a whole row
     * of keys instead of needing near-duplicate entries per key.
     */
    @NonNull
    private static JSONObject argumentsFor(int keyCode) {
        JSONObject arguments = new JSONObject();
        try {
            String direction = directionToken(keyCode);
            if (direction != null) {
                arguments.put("direction", direction);
            } else if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
                arguments.put("index", keyCode - KeyEvent.KEYCODE_1);
            }
        } catch (JSONException ignored) {
        }
        return arguments;
    }

    @Nullable
    private static String directionToken(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            default: return null;
        }
    }

    /**
     * Key code -> stroke token. Letters use physical US positions. Public so the keybind hint
     * board can name each drawn key the way a binding suffix would.
     */
    @Nullable
    public static String keyToken(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return String.valueOf((char) ('a' + (keyCode - KeyEvent.KEYCODE_A)));
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return String.valueOf((char) ('0' + (keyCode - KeyEvent.KEYCODE_0)));
        }
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            return "f" + (keyCode - KeyEvent.KEYCODE_F1 + 1);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "[";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "]";
            // Spelled out because '+' is the stroke separator and '=' / '-' read
            // badly in a config file.
            case KeyEvent.KEYCODE_MINUS: return "minus";
            case KeyEvent.KEYCODE_EQUALS: return "equals";
            case KeyEvent.KEYCODE_PLUS: return "plus";
            case KeyEvent.KEYCODE_SLASH: return "/";
            case KeyEvent.KEYCODE_BACKSLASH: return "\\";
            case KeyEvent.KEYCODE_SEMICOLON: return ";";
            case KeyEvent.KEYCODE_APOSTROPHE: return "'";
            case KeyEvent.KEYCODE_COMMA: return ",";
            case KeyEvent.KEYCODE_PERIOD: return ".";
            case KeyEvent.KEYCODE_GRAVE: return "`";
            case KeyEvent.KEYCODE_SPACE: return "space";
            case KeyEvent.KEYCODE_TAB: return "tab";
            case KeyEvent.KEYCODE_ENTER: return "enter";
            case KeyEvent.KEYCODE_ESCAPE: return "escape";
            case KeyEvent.KEYCODE_DEL: return "backspace";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "delete";
            case KeyEvent.KEYCODE_PAGE_UP: return "pageup";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "pagedown";
            case KeyEvent.KEYCODE_MOVE_HOME: return "home";
            case KeyEvent.KEYCODE_MOVE_END: return "end";
            default: return null;
        }
    }

    /**
     * The stroke token a printable character would carry, so a soft key that only knows the
     * character it types can still be matched against a binding. Public because the in-app
     * keyboard uses it for both jobs it has here: lighting the caps a prefix binds, and turning a
     * pressed cap into the key event the resolver reads.
     */
    @NonNull
    public static String tokenForChar(char c) {
        char lower = Character.toLowerCase(c);
        switch (lower) {
            // Spelled out for the same reason keyToken spells them out.
            case '-': return "minus";
            case '=': return "equals";
            case '+': return "plus";
            case ' ': return "space";
            default: return String.valueOf(lower);
        }
    }

    @Nullable
    public static Integer keyCodeForToken(@NonNull String token) {
        for (int keyCode = KeyEvent.KEYCODE_A; keyCode <= KeyEvent.KEYCODE_Z; keyCode++)
            if (token.equals(keyToken(keyCode))) return keyCode;
        for (int keyCode = KeyEvent.KEYCODE_0; keyCode <= KeyEvent.KEYCODE_9; keyCode++)
            if (token.equals(keyToken(keyCode))) return keyCode;
        for (int keyCode = KeyEvent.KEYCODE_F1; keyCode <= KeyEvent.KEYCODE_F12; keyCode++)
            if (token.equals(keyToken(keyCode))) return keyCode;
        int[] supported = {KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_LEFT_BRACKET,
            KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_BACKSLASH,
            KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END};
        for (int keyCode : supported) if (token.equals(keyToken(keyCode))) return keyCode;
        return null;
    }

    /**
     * Canonicalizes a declared binding string: modifier names are case-insensitive and always
     * appear in ctrl, alt, shift order, while <b>an upper-case letter is itself the Shift
     * modifier</b> — {@code Ctrl+Alt+R} is {@code ctrl+alt+shift+r} and {@code Ctrl+Alt+r} is
     * {@code ctrl+alt+r}, two different strokes that can hold two different actions.
     *
     * <p>Case used to be discarded wholesale, which made those two lines the same binding and left
     * a config file no way to say "the shifted one" other than spelling {@code shift+} out. Only a
     * single-character key is read this way; multi-character tokens ({@code Left}, {@code PageUp})
     * have no shifted spelling and keep folding to lower case.
     */
    @NonNull
    static String normalizeStrokeSpec(@NonNull String spec) {
        boolean ctrl = false, alt = false, shift = false;
        String key = "";
        for (String part : spec.trim().split("\\+")) {
            switch (part.toLowerCase(Locale.US)) {
                case "ctrl": case "control": ctrl = true; break;
                case "alt": alt = true; break;
                case "shift": shift = true; break;
                case "": break;
                default: key = part;
            }
        }
        if (key.length() == 1 && Character.isUpperCase(key.charAt(0))) shift = true;
        key = key.toLowerCase(Locale.US);
        StringBuilder normalized = new StringBuilder();
        if (ctrl) normalized.append("ctrl+");
        if (alt) normalized.append("alt+");
        if (shift) normalized.append("shift+");
        return normalized.append(key).toString();
    }

    /** Canonicalizes every stroke in a {@code key1>key2} binding. */
    @NonNull
    static String normalizeSequenceSpec(@NonNull String spec) {
        String[] raw = spec.trim().split(">", -1);
        List<String> normalized = new ArrayList<>(raw.length);
        for (String stroke : raw) {
            normalized.add(normalizeStrokeSpec(stroke));
        }
        return joinSequence(normalized);
    }

    static boolean isValidSequenceSpec(@NonNull String sequence) {
        List<String> strokes = splitSequence(sequence);
        if (strokes.isEmpty() || strokes.size() > 8) return false;
        for (String stroke : strokes) {
            if (!isValidStrokeSpec(stroke)) return false;
        }
        return true;
    }

    static boolean isValidStrokeSpec(@NonNull String stroke) {
        if (stroke.isEmpty() || stroke.indexOf('>') >= 0) return false;
        int separator = stroke.lastIndexOf('+');
        String key = separator >= 0 ? stroke.substring(separator + 1) : stroke;
        if (key.isEmpty()) return false;
        return keyCodeForToken(key) != null;
    }

    @NonNull
    private static List<String> splitSequence(@NonNull String sequence) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, sequence.split(">", -1));
        return result;
    }

    @NonNull
    private static String joinSequence(@NonNull List<String> strokes) {
        StringBuilder result = new StringBuilder();
        for (String stroke : strokes) {
            if (result.length() > 0) result.append('>');
            result.append(stroke);
        }
        return result.toString();
    }
}
