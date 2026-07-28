package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses the user-editable, kitty-inspired terminal binding file. */
public final class TerminalBindingConfig {

    public static final String FILE_NAME = "termux-launcher-bindings.conf";
    public static final String FILE_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/" + FILE_NAME;
    private static final long MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_LINES = 4096;
    private static final int MAX_LINE_CHARS = 4096;

    public enum ActionType { TOOL, SEND_TEXT, SEND_KEY, PUSH_MODE, POP_MODE }

    public enum UnknownKeyPolicy { BEEP, IGNORE, END, PASSTHROUGH }

    public static final class Mode {
        @NonNull public final String name;
        public final long timeoutMillis;
        @NonNull public final UnknownKeyPolicy onUnknown;
        public final boolean endOnAction;

        private Mode(@NonNull String name, long timeoutMillis,
                     @NonNull UnknownKeyPolicy onUnknown, boolean endOnAction) {
            this.name = name;
            this.timeoutMillis = timeoutMillis;
            this.onUnknown = onUnknown;
            this.endOnAction = endOnAction;
        }
    }

    public static final class Action {
        public final ActionType type;
        /** Tool ID, decoded text, or normalized key spec according to {@link #type}. */
        public final String value;
        @NonNull public final JSONObject arguments;

        private Action(@NonNull ActionType type, @NonNull String value,
                       @Nullable JSONObject arguments) {
            this.type = type;
            this.value = value;
            this.arguments = arguments == null ? new JSONObject() : arguments;
        }

        static Action tool(@NonNull String tool) {
            return new Action(ActionType.TOOL, tool, null);
        }

        static Action text(@NonNull String text) {
            return new Action(ActionType.SEND_TEXT, text, null);
        }

        static Action key(@NonNull String key) {
            return new Action(ActionType.SEND_KEY, key, null);
        }

        static Action pushMode(@NonNull String mode) {
            return new Action(ActionType.PUSH_MODE, mode, null);
        }

        static Action popMode() {
            return new Action(ActionType.POP_MODE, "", null);
        }

        @NonNull
        String diagnosticName() {
            return type == ActionType.TOOL ? value : type.name().toLowerCase(Locale.US);
        }
    }

    public static final class Mapping {
        /** Empty for the root keymap. */
        @NonNull public final String mode;
        public final String sequence;
        public final LauncherToolRegistry.BindingCondition condition;
        @NonNull public final List<Action> actions;

        private Mapping(@NonNull String mode, @NonNull String sequence,
                        @NonNull LauncherToolRegistry.BindingCondition condition,
                        @NonNull List<Action> actions) {
            this.mode = mode;
            this.sequence = sequence;
            this.condition = condition;
            this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
        }
    }

    public static final class Result {
        public final boolean filePresent;
        @NonNull public final List<Mapping> mappings;
        /** Normalized sequences explicitly mentioned by map or unmap. */
        @NonNull public final List<String> overriddenSequences;
        @NonNull public final Map<String, Mode> modes;
        @NonNull public final List<String> errors;

        private Result(boolean filePresent, @NonNull List<Mapping> mappings,
                       @NonNull List<String> overriddenSequences,
                       @NonNull Map<String, Mode> modes,
                       @NonNull List<String> errors) {
            this.filePresent = filePresent;
            this.mappings = Collections.unmodifiableList(new ArrayList<>(mappings));
            this.overriddenSequences = Collections.unmodifiableList(new ArrayList<>(overriddenSequences));
            this.modes = Collections.unmodifiableMap(new LinkedHashMap<>(modes));
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        static Result empty(boolean present, @Nullable String error) {
            List<String> errors = error == null ? Collections.<String>emptyList()
                : Collections.singletonList(error);
            return new Result(present, Collections.<Mapping>emptyList(),
                Collections.<String>emptyList(), Collections.<String, Mode>emptyMap(), errors);
        }
    }

    private TerminalBindingConfig() {}

    @NonNull
    public static Result load(@NonNull LauncherToolRegistry registry) {
        return load(new File(FILE_PATH), registry);
    }

    @NonNull
    static Result load(@NonNull File file, @NonNull LauncherToolRegistry registry) {
        if (!file.exists()) return Result.empty(false, null);
        if (!file.isFile()) return Result.empty(true, FILE_PATH + " is not a regular file");
        if (file.length() > MAX_FILE_BYTES) {
            return Result.empty(true, "binding file exceeds " + MAX_FILE_BYTES + " bytes");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder((int) Math.min(file.length(), MAX_FILE_BYTES));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (++count > MAX_LINES) {
                    return Result.empty(true, "binding file exceeds " + MAX_LINES + " lines");
                }
                if (line.length() > MAX_LINE_CHARS) {
                    return Result.empty(true, "line " + count + " exceeds " + MAX_LINE_CHARS + " characters");
                }
                content.append(line).append('\n');
            }
            return parse(content.toString(), registry, true);
        } catch (IOException e) {
            return Result.empty(true, "cannot read binding file: " + e.getMessage());
        }
    }

    @NonNull
    static Result parse(@NonNull String content, @NonNull LauncherToolRegistry registry,
                        boolean filePresent) {
        List<String> errors = new ArrayList<>();
        LinkedHashMap<String, MutableMapping> mappings = new LinkedHashMap<>();
        LinkedHashMap<String, Mode> modes = new LinkedHashMap<>();
        List<String> overridden = new ArrayList<>();
        String[] lines = content.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            List<String> words;
            try {
                words = words(lines[i]);
            } catch (IllegalArgumentException e) {
                errors.add("line " + lineNumber + ": " + e.getMessage());
                continue;
            }
            if (words.isEmpty()) continue;
            String directive = words.get(0).toLowerCase(Locale.US);
            if ("unmap".equals(directive)) {
                int cursor = 1;
                String mode = "";
                if (cursor < words.size() && words.get(cursor).startsWith("--mode="))
                    mode = words.get(cursor++).substring("--mode=".length());
                else if (cursor < words.size() && "--mode".equals(words.get(cursor))) {
                    if (++cursor >= words.size()) {
                        errors.add("line " + lineNumber + ": --mode needs a name");
                        continue;
                    }
                    mode = words.get(cursor++);
                }
                if (words.size() - cursor != 1) {
                    errors.add("line " + lineNumber + ": unmap needs one key sequence");
                    continue;
                }
                String sequence = validSequence(words.get(cursor), lineNumber, errors);
                if (sequence == null) continue;
                if (mode.isEmpty() && !overridden.contains(sequence)) overridden.add(sequence);
                removeSequence(mappings, mode, sequence);
                continue;
            }
            if (!"map".equals(directive)) {
                errors.add("line " + lineNumber + ": expected map or unmap");
                continue;
            }

            int cursor = 1;
            LauncherToolRegistry.BindingCondition condition = LauncherToolRegistry.BindingCondition.ALWAYS;
            String mode = "";
            String newMode = null;
            long timeoutMillis = 2_000L;
            UnknownKeyPolicy onUnknown = UnknownKeyPolicy.BEEP;
            boolean endOnAction = false;
            boolean badOption = false;
            while (cursor < words.size() && words.get(cursor).startsWith("--")) {
                String option = words.get(cursor++);
                String name;
                String value;
                int equals = option.indexOf('=');
                if (equals >= 0) {
                    name = option.substring(0, equals);
                    value = option.substring(equals + 1);
                } else {
                    name = option;
                    if (cursor >= words.size()) {
                        errors.add("line " + lineNumber + ": " + name + " needs a value");
                        badOption = true;
                        break;
                    }
                    value = words.get(cursor++);
                }
                switch (name) {
                    case "--when":
                        condition = condition(value);
                        if (condition == null) {
                            errors.add("line " + lineNumber + ": unknown condition '" + value + "'");
                            badOption = true;
                        }
                        break;
                    case "--mode": mode = value; break;
                    case "--new-mode": newMode = value; break;
                    case "--timeout":
                        try {
                            double seconds = Double.parseDouble(value);
                            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 3600) throw new NumberFormatException();
                            timeoutMillis = Math.round(seconds * 1000d);
                        } catch (NumberFormatException e) {
                            errors.add("line " + lineNumber + ": invalid timeout '" + value + "'");
                            badOption = true;
                        }
                        break;
                    case "--on-unknown":
                        try {
                            onUnknown = UnknownKeyPolicy.valueOf(value.toUpperCase(Locale.US));
                        } catch (IllegalArgumentException e) {
                            errors.add("line " + lineNumber + ": unknown key policy '" + value + "'");
                            badOption = true;
                        }
                        break;
                    case "--on-action":
                        if ("end".equals(value)) endOnAction = true;
                        else if ("keep".equals(value)) endOnAction = false;
                        else {
                            errors.add("line " + lineNumber + ": on-action must be keep or end");
                            badOption = true;
                        }
                        break;
                    default:
                        errors.add("line " + lineNumber + ": unknown option '" + name + "'");
                        badOption = true;
                }
            }
            if (badOption) continue;
            if (!modeNameValid(mode) || (newMode != null && !modeNameValid(newMode))) {
                errors.add("line " + lineNumber + ": invalid mode name");
                continue;
            }
            if (newMode != null && !mode.isEmpty()) {
                errors.add("line " + lineNumber + ": --mode and --new-mode cannot be combined");
                continue;
            }
            int minimum = newMode == null ? 2 : 1;
            if (words.size() - cursor < minimum) {
                errors.add("line " + lineNumber + ": map needs a key sequence and action");
                continue;
            }
            String sequence = validSequence(words.get(cursor++), lineNumber, errors);
            if (sequence == null) continue;
            Action action;
            if (newMode != null) {
                if (cursor != words.size()) {
                    errors.add("line " + lineNumber + ": a new-mode entry takes no action");
                    continue;
                }
                if (newMode.isEmpty() || modes.containsKey(newMode)) {
                    errors.add("line " + lineNumber + ": mode '" + newMode + "' is invalid or already defined");
                    continue;
                }
                modes.put(newMode, new Mode(newMode, timeoutMillis, onUnknown, endOnAction));
                action = Action.pushMode(newMode);
                mode = "";
            } else {
                String actionName = words.get(cursor++);
                if ("send-text".equals(actionName)) {
                if (cursor != words.size() - 1) {
                    errors.add("line " + lineNumber + ": send-text needs one quoted or unquoted value");
                    continue;
                }
                action = Action.text(words.get(cursor));
                } else if ("send-key".equals(actionName)) {
                if (cursor != words.size() - 1) {
                    errors.add("line " + lineNumber + ": send-key needs one key stroke");
                    continue;
                }
                String key = TerminalKeyBindingResolver.normalizeSequenceSpec(words.get(cursor));
                if (key.indexOf('>') >= 0 || !TerminalKeyBindingResolver.isValidStrokeSpec(key)) {
                    errors.add("line " + lineNumber + ": invalid send-key stroke '" + words.get(cursor) + "'");
                    continue;
                }
                action = Action.key(key);
                } else if ("pop-mode".equals(actionName) || "pop_keyboard_mode".equals(actionName)) {
                    if (cursor != words.size()) {
                        errors.add("line " + lineNumber + ": pop-mode takes no arguments");
                        continue;
                    }
                    action = Action.popMode();
                } else {
                if (cursor != words.size()) {
                    errors.add("line " + lineNumber + ": terminal actions do not take inline arguments");
                    continue;
                }
                LauncherToolRegistry.ToolMetadata tool = registry.getTool(actionName);
                if (tool == null || tool.executor != LauncherToolRegistry.ToolExecutor.TERMINAL) {
                    errors.add("line " + lineNumber + ": unknown terminal action '" + actionName + "'");
                    continue;
                }
                action = Action.tool(actionName);
                }
            }

            if (mode.isEmpty() && !overridden.contains(sequence)) overridden.add(sequence);
            String identity = mode + "\u0000" + sequence + "\u0000" + condition.name();
            MutableMapping target = mappings.get(identity);
            if (target == null) {
                target = new MutableMapping(mode, sequence, condition);
                mappings.put(identity, target);
            }
            target.actions.add(action);
        }

        List<Mapping> result = new ArrayList<>(mappings.size());
        for (MutableMapping mapping : mappings.values()) {
            if (!mapping.mode.isEmpty() && !modes.containsKey(mapping.mode)) {
                errors.add("mode '" + mapping.mode + "' is used but never defined");
                continue;
            }
            result.add(new Mapping(mapping.mode, mapping.sequence, mapping.condition, mapping.actions));
        }
        return new Result(filePresent, result, overridden, modes, errors);
    }

    private static boolean modeNameValid(@NonNull String mode) {
        return mode.isEmpty() || mode.matches("[A-Za-z0-9_.-]{1,32}");
    }

    @Nullable
    private static String validSequence(@NonNull String raw, int line, @NonNull List<String> errors) {
        String sequence = TerminalKeyBindingResolver.normalizeSequenceSpec(raw);
        if (!TerminalKeyBindingResolver.isValidSequenceSpec(sequence)) {
            errors.add("line " + line + ": invalid key sequence '" + raw + "'");
            return null;
        }
        return sequence;
    }

    @Nullable
    private static LauncherToolRegistry.BindingCondition condition(@NonNull String value) {
        switch (value.toLowerCase(Locale.US)) {
            case "always": return LauncherToolRegistry.BindingCondition.ALWAYS;
            case "splits-on": return LauncherToolRegistry.BindingCondition.SPLITS_ON;
            case "splits-off": return LauncherToolRegistry.BindingCondition.SPLITS_OFF;
            default: return null;
        }
    }

    private static void removeSequence(@NonNull Map<String, MutableMapping> mappings,
                                       @NonNull String mode, @NonNull String sequence) {
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, MutableMapping> entry : mappings.entrySet()) {
            if (entry.getValue().mode.equals(mode) && entry.getValue().sequence.equals(sequence))
                remove.add(entry.getKey());
        }
        for (String key : remove) mappings.remove(key);
    }

    /** Shell-like words with # comments and simple single/double quoted values. */
    @NonNull
    private static List<String> words(@NonNull String line) {
        List<String> result = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean started = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': word.append('\n'); break;
                    case 'r': word.append('\r'); break;
                    case 't': word.append('\t'); break;
                    case 'e': word.append('\033'); break;
                    default: word.append(c); break;
                }
                escaped = false;
                started = true;
            } else if (c == '\\' && quote != '\'') {
                escaped = true;
                started = true;
            } else if (quote != 0) {
                if (c == quote) quote = 0;
                else word.append(c);
                started = true;
            } else if (c == '\'' || c == '"') {
                quote = c;
                started = true;
            } else if (c == '#') {
                break;
            } else if (Character.isWhitespace(c)) {
                if (started) {
                    result.add(word.toString());
                    word.setLength(0);
                    started = false;
                }
            } else {
                word.append(c);
                started = true;
            }
        }
        if (escaped) throw new IllegalArgumentException("trailing escape");
        if (quote != 0) throw new IllegalArgumentException("unterminated quote");
        if (started) result.add(word.toString());
        return result;
    }

    private static final class MutableMapping {
        final String mode;
        final String sequence;
        final LauncherToolRegistry.BindingCondition condition;
        final List<Action> actions = new ArrayList<>();

        MutableMapping(String mode, String sequence, LauncherToolRegistry.BindingCondition condition) {
            this.mode = mode;
            this.sequence = sequence;
            this.condition = condition;
        }
    }
}
