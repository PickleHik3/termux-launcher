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
 * {@code Ctrl+Alt}+character chain, so a keystroke, a palette entry, and
 * {@code /v1/agent/execute} all name the same action. The registry is the single
 * source of truth for which stroke means what.
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
        public final JSONObject arguments;
        /** The normalized stroke that matched, e.g. {@code ctrl+alt+shift+left}. */
        public final String stroke;
        public final LauncherToolRegistry.BindingCondition condition;

        Match(@NonNull String toolName, @NonNull JSONObject arguments, @NonNull String stroke,
              @NonNull LauncherToolRegistry.BindingCondition condition) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.stroke = stroke;
            this.condition = condition;
        }
    }

    /** One claim on a stroke. */
    static final class Claim {
        final String toolName;
        final LauncherToolRegistry.BindingCondition condition;

        Claim(@NonNull String toolName, @NonNull LauncherToolRegistry.BindingCondition condition) {
            this.toolName = toolName;
            this.condition = condition;
        }
    }

    private static TerminalKeyBindingResolver instance;

    /** stroke -> claims, in registration order. */
    private final Map<String, List<Claim>> bindings;
    /** Strokes claimed twice under conditions that can both hold. */
    private final Map<String, List<String>> conflicts;

    private TerminalKeyBindingResolver(@NonNull LauncherToolRegistry registry) {
        Map<String, List<Claim>> map = new LinkedHashMap<>();
        Map<String, List<String>> clashes = new LinkedHashMap<>();
        for (LauncherToolRegistry.ToolMetadata tool : registry.getUiTools()) {
            for (LauncherToolRegistry.Binding binding : tool.defaultBindings) {
                String stroke = normalizeStrokeSpec(binding.stroke);
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
        Map<String, List<Claim>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<Claim>> entry : map.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        bindings = Collections.unmodifiableMap(frozen);
        conflicts = Collections.unmodifiableMap(clashes);
    }

    @NonNull
    public static synchronized TerminalKeyBindingResolver getInstance() {
        if (instance == null) {
            instance = new TerminalKeyBindingResolver(LauncherToolRegistry.getInstance());
        }
        return instance;
    }

    /** Resets the singleton for unit tests. */
    static synchronized void resetForTesting() {
        instance = null;
    }

    /** Immutable stroke -> claims view, for the binding editor and diagnostics. */
    @NonNull
    public Map<String, List<Claim>> getBindings() {
        return bindings;
    }

    /** Strokes claimed twice under conditions that can both hold. */
    @NonNull
    public Map<String, List<String>> getConflicts() {
        return conflicts;
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
        if (!event.isCtrlPressed() || !event.isAltPressed()) {
            return null;
        }
        String stroke = strokeFor(event);
        if (stroke == null) {
            return null;
        }
        List<Claim> claims = bindings.get(stroke);
        if (claims == null) {
            return null;
        }
        for (Claim claim : claims) {
            if (claim.condition.holds(context)) {
                return new Match(claim.toolName, argumentsFor(event.getKeyCode()), stroke, claim.condition);
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

    /** Key code -> stroke token. Letters use physical US positions. */
    @Nullable
    static String keyToken(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return String.valueOf((char) ('a' + (keyCode - KeyEvent.KEYCODE_A)));
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return String.valueOf((char) ('0' + (keyCode - KeyEvent.KEYCODE_0)));
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
     * Canonicalizes a declared binding string so {@code Ctrl+Alt+V} and
     * {@code ctrl+alt+v} are the same stroke, and modifiers always appear in
     * ctrl, alt, shift order.
     */
    @NonNull
    static String normalizeStrokeSpec(@NonNull String spec) {
        boolean ctrl = false, alt = false, shift = false;
        String key = "";
        for (String part : spec.toLowerCase(Locale.US).trim().split("\\+")) {
            switch (part) {
                case "ctrl": case "control": ctrl = true; break;
                case "alt": alt = true; break;
                case "shift": shift = true; break;
                case "": break;
                default: key = part;
            }
        }
        StringBuilder normalized = new StringBuilder();
        if (ctrl) normalized.append("ctrl+");
        if (alt) normalized.append("alt+");
        if (shift) normalized.append("shift+");
        return normalized.append(key).toString();
    }
}
