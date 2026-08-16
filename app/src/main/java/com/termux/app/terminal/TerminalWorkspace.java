package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Versioned, declarative description of a terminal workspace. */
public final class TerminalWorkspace {

    public static final int VERSION = 3;
    /**
     * Oldest schema this build still loads. Version 2 added per-window floating panes, version 3
     * per-window names. Both are optional keys, so every older file still loads unchanged.
     */
    private static final int MIN_VERSION = 1;
    public static final int MAX_SESSIONS = 64;
    public static final int MAX_WINDOWS = 64;
    public static final int MAX_PANES = 64;
    public static final int MAX_TREE_DEPTH = 16;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_COMMAND_ARGUMENTS = 64;

    public final int version;
    @NonNull public final String name;
    public final long savedAtEpochMs;
    public final int currentSession;
    @NonNull public final List<Session> sessions;

    public TerminalWorkspace(@NonNull String name, long savedAtEpochMs, int currentSession,
                             @NonNull List<Session> sessions) {
        this(VERSION, name, savedAtEpochMs, currentSession, sessions);
    }

    private TerminalWorkspace(int version, @NonNull String name, long savedAtEpochMs,
                              int currentSession, @NonNull List<Session> sessions) {
        this.version = version;
        this.name = name;
        this.savedAtEpochMs = savedAtEpochMs;
        this.currentSession = currentSession;
        this.sessions = immutable(sessions);
    }

    public static final class Session {
        @Nullable public final String name;
        public final int currentWindow;
        @NonNull public final List<Window> windows;

        public Session(@Nullable String name, int currentWindow, @NonNull List<Window> windows) {
            this.name = name;
            this.currentWindow = currentWindow;
            this.windows = immutable(windows);
        }
    }

    public static final class Window {
        /** Index into the window's panes: tiled tree leaves in order, then floats in order. */
        public final int activePane;
        @NonNull public final Node root;
        @NonNull public final List<FloatingPane> floats;
        /** User-given tab name, or null while the tab labels itself from its foreground process. */
        @Nullable public final String name;

        public Window(int activePane, @NonNull Node root) {
            this(activePane, root, null, null);
        }

        public Window(int activePane, @NonNull Node root, @Nullable List<FloatingPane> floats) {
            this(activePane, root, floats, null);
        }

        public Window(int activePane, @NonNull Node root, @Nullable List<FloatingPane> floats,
                      @Nullable String name) {
            this.activePane = activePane;
            this.root = root;
            this.floats = floats == null ? Collections.emptyList() : immutable(floats);
            this.name = name;
        }
    }

    /** A pane detached from the tiled tree, positioned by fractions of the pane host. */
    public static final class FloatingPane {
        @NonNull public final Pane pane;
        public final float left;
        public final float top;
        public final float width;
        public final float height;

        public FloatingPane(@NonNull Pane pane, float left, float top, float width, float height) {
            this.pane = pane;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    public abstract static class Node {
        private Node() {}
    }

    public static final class Pane extends Node {
        @Nullable public final String cwd;
        @Nullable public final String title;
        /** Full argv, or an empty list when command capture was disabled/unavailable. */
        @NonNull public final List<String> command;

        public Pane(@Nullable String cwd, @Nullable String title, @Nullable List<String> command) {
            this.cwd = cwd;
            this.title = title;
            this.command = command == null ? Collections.emptyList() : immutable(command);
        }
    }

    public static final class Split extends Node {
        public static final String HORIZONTAL = "horizontal";
        public static final String VERTICAL = "vertical";

        @NonNull public final String orientation;
        public final float weightA;
        public final float weightB;
        @NonNull public final Node a;
        @NonNull public final Node b;

        public Split(@NonNull String orientation, float weightA, float weightB,
                     @NonNull Node a, @NonNull Node b) {
            this.orientation = orientation;
            this.weightA = weightA;
            this.weightB = weightB;
            this.a = a;
            this.b = b;
        }
    }

    /** Validate all structural and resource bounds before this definition reaches live sessions. */
    public void validate() throws WorkspaceException {
        if (version < MIN_VERSION || version > VERSION) {
            throw new WorkspaceException("unsupported_version",
                "Workspace version " + version + " is not supported; expected "
                    + MIN_VERSION + " to " + VERSION);
        }
        requireText(name, "name", false);
        if (savedAtEpochMs < 0) invalid("savedAtEpochMs must not be negative");
        if (sessions.isEmpty() || sessions.size() > MAX_SESSIONS)
            invalid("sessions must contain between 1 and " + MAX_SESSIONS + " entries");
        if (currentSession < 0 || currentSession >= sessions.size())
            invalid("currentSession is outside sessions");
        int panes = 0;
        for (Session session : sessions) {
            if (session.name != null) requireText(session.name, "session name", true);
            if (session.windows.isEmpty() || session.windows.size() > MAX_WINDOWS)
                invalid("each session must contain between 1 and " + MAX_WINDOWS + " windows");
            if (session.currentWindow < 0 || session.currentWindow >= session.windows.size())
                invalid("currentWindow is outside windows");
            for (Window window : session.windows) {
                if (window.name != null) requireText(window.name, "window name", true);
                int windowPanes = validateNode(window.root, 1);
                for (FloatingPane floating : window.floats) {
                    windowPanes += validateNode(floating.pane, 1);
                    if (!Float.isFinite(floating.left) || !Float.isFinite(floating.top)
                        || !Float.isFinite(floating.width) || !Float.isFinite(floating.height)
                        || floating.width <= 0f || floating.width > 1f
                        || floating.height <= 0f || floating.height > 1f)
                        invalid("floating pane bounds must be finite fractions with size in (0, 1]");
                }
                if (window.activePane < 0 || window.activePane >= windowPanes)
                    invalid("activePane is outside the window's panes");
                panes += windowPanes;
                if (panes > MAX_PANES) invalid("workspace has more than " + MAX_PANES + " panes");
            }
        }
    }

    public int paneCount() {
        int count = 0;
        for (Session session : sessions)
            for (Window window : session.windows)
                count += countPanes(window.root) + window.floats.size();
        return count;
    }

    public int commandCount() {
        int count = 0;
        for (Session session : sessions)
            for (Window window : session.windows) {
                count += countCommands(window.root);
                for (FloatingPane floating : window.floats)
                    if (!floating.pane.command.isEmpty()) count++;
            }
        return count;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("name", name);
        json.put("savedAtEpochMs", savedAtEpochMs);
        json.put("currentSession", currentSession);
        JSONArray sessionArray = new JSONArray();
        for (Session session : sessions) {
            JSONObject sessionJson = new JSONObject();
            if (session.name != null) sessionJson.put("name", session.name);
            sessionJson.put("currentWindow", session.currentWindow);
            JSONArray windowArray = new JSONArray();
            for (Window window : session.windows) {
                JSONObject windowJson = new JSONObject();
                windowJson.put("activePane", window.activePane);
                if (window.name != null) windowJson.put("name", window.name);
                windowJson.put("root", nodeToJson(window.root));
                if (!window.floats.isEmpty()) {
                    JSONArray floatArray = new JSONArray();
                    for (FloatingPane floating : window.floats) {
                        JSONObject floatJson = nodeToJson(floating.pane);
                        floatJson.put("left", floating.left);
                        floatJson.put("top", floating.top);
                        floatJson.put("width", floating.width);
                        floatJson.put("height", floating.height);
                        floatArray.put(floatJson);
                    }
                    windowJson.put("floats", floatArray);
                }
                windowArray.put(windowJson);
            }
            sessionJson.put("windows", windowArray);
            sessionArray.put(sessionJson);
        }
        json.put("sessions", sessionArray);
        return json;
    }

    @NonNull
    public static TerminalWorkspace fromJson(@NonNull JSONObject json) throws WorkspaceException {
        try {
            int version = requiredInt(json, "version");
            String name = requiredString(json, "name");
            long savedAt = requiredLong(json, "savedAtEpochMs");
            int currentSession = requiredInt(json, "currentSession");
            JSONArray sessionArray = requiredArray(json, "sessions");
            List<Session> sessions = new ArrayList<>();
            for (int i = 0; i < sessionArray.length(); i++) {
                JSONObject sessionJson = requiredObject(sessionArray, i, "session");
                String sessionName = optionalString(sessionJson, "name");
                int currentWindow = requiredInt(sessionJson, "currentWindow");
                JSONArray windowArray = requiredArray(sessionJson, "windows");
                List<Window> windows = new ArrayList<>();
                for (int j = 0; j < windowArray.length(); j++) {
                    JSONObject windowJson = requiredObject(windowArray, j, "window");
                    // Version 1 files carry no "floats"; the absent key must keep loading.
                    List<FloatingPane> floats = new ArrayList<>();
                    JSONArray floatArray = windowJson.optJSONArray("floats");
                    if (floatArray != null) {
                        for (int k = 0; k < floatArray.length(); k++) {
                            JSONObject floatJson = requiredObject(floatArray, k, "floating pane");
                            Node pane = nodeFromJson(floatJson, 1);
                            if (!(pane instanceof Pane)) invalid("floating pane must be a pane node");
                            floats.add(new FloatingPane((Pane) pane,
                                (float) requiredDouble(floatJson, "left"),
                                (float) requiredDouble(floatJson, "top"),
                                (float) requiredDouble(floatJson, "width"),
                                (float) requiredDouble(floatJson, "height")));
                        }
                    } else if (windowJson.has("floats") && !windowJson.isNull("floats")) {
                        invalid("floats must be an array");
                    }
                    windows.add(new Window(requiredInt(windowJson, "activePane"),
                        nodeFromJson(requiredObject(windowJson, "root"), 1), floats,
                        optionalString(windowJson, "name")));
                }
                sessions.add(new Session(sessionName, currentWindow, windows));
            }
            TerminalWorkspace workspace = new TerminalWorkspace(
                version, name, savedAt, currentSession, sessions);
            workspace.validate();
            return workspace;
        } catch (WorkspaceException e) {
            throw e;
        } catch (JSONException | ClassCastException e) {
            throw new WorkspaceException("invalid_workspace", "Invalid workspace JSON: " + e.getMessage(), e);
        }
    }

    private static int validateNode(@NonNull Node node, int depth) throws WorkspaceException {
        if (depth > MAX_TREE_DEPTH) invalid("pane tree exceeds depth " + MAX_TREE_DEPTH);
        if (node instanceof Pane) {
            Pane pane = (Pane) node;
            if (pane.cwd != null) requireText(pane.cwd, "cwd", true);
            if (pane.title != null) requireText(pane.title, "pane title", true);
            if (pane.command.size() > MAX_COMMAND_ARGUMENTS)
                invalid("command has more than " + MAX_COMMAND_ARGUMENTS + " arguments");
            for (String argument : pane.command) requireText(argument, "command argument", true);
            if (!pane.command.isEmpty() && pane.command.get(0).isEmpty())
                invalid("command executable must not be empty");
            return 1;
        }
        if (!(node instanceof Split)) invalid("unknown pane node type");
        Split split = (Split) node;
        if (!Split.HORIZONTAL.equals(split.orientation) && !Split.VERTICAL.equals(split.orientation))
            invalid("split orientation must be horizontal or vertical");
        if (!Float.isFinite(split.weightA) || !Float.isFinite(split.weightB)
            || split.weightA <= 0f || split.weightB <= 0f)
            invalid("split weights must be finite and positive");
        return validateNode(split.a, depth + 1) + validateNode(split.b, depth + 1);
    }

    private static int countPanes(Node node) {
        if (node instanceof Pane) return 1;
        Split split = (Split) node;
        return countPanes(split.a) + countPanes(split.b);
    }

    private static int countCommands(Node node) {
        if (node instanceof Pane) return ((Pane) node).command.isEmpty() ? 0 : 1;
        Split split = (Split) node;
        return countCommands(split.a) + countCommands(split.b);
    }

    private static JSONObject nodeToJson(Node node) throws JSONException {
        JSONObject json = new JSONObject();
        if (node instanceof Pane) {
            Pane pane = (Pane) node;
            json.put("type", "pane");
            if (pane.cwd != null) json.put("cwd", pane.cwd);
            if (pane.title != null) json.put("title", pane.title);
            if (!pane.command.isEmpty()) {
                JSONArray command = new JSONArray();
                for (String argument : pane.command) command.put(argument);
                json.put("command", command);
            }
            return json;
        }
        Split split = (Split) node;
        json.put("type", "split");
        json.put("orientation", split.orientation);
        json.put("weightA", split.weightA);
        json.put("weightB", split.weightB);
        json.put("a", nodeToJson(split.a));
        json.put("b", nodeToJson(split.b));
        return json;
    }

    private static Node nodeFromJson(JSONObject json, int depth) throws JSONException, WorkspaceException {
        if (depth > MAX_TREE_DEPTH) invalid("pane tree exceeds depth " + MAX_TREE_DEPTH);
        String type = requiredString(json, "type");
        if ("pane".equals(type)) {
            List<String> command = new ArrayList<>();
            JSONArray commandJson = json.optJSONArray("command");
            if (commandJson != null) {
                for (int i = 0; i < commandJson.length(); i++) {
                    Object argument = commandJson.get(i);
                    if (!(argument instanceof String)) invalid("command arguments must be strings");
                    command.add((String) argument);
                }
            } else if (json.has("command") && !json.isNull("command")) {
                invalid("command must be an array");
            }
            return new Pane(optionalString(json, "cwd"), optionalString(json, "title"), command);
        }
        if ("split".equals(type)) {
            return new Split(requiredString(json, "orientation"),
                (float) requiredDouble(json, "weightA"), (float) requiredDouble(json, "weightB"),
                nodeFromJson(requiredObject(json, "a"), depth + 1),
                nodeFromJson(requiredObject(json, "b"), depth + 1));
        }
        invalid("unknown pane node type '" + type + "'");
        throw new AssertionError();
    }

    private static void requireText(String value, String label, boolean allowEmpty) throws WorkspaceException {
        if ((!allowEmpty && value.isEmpty()) || value.length() > MAX_TEXT_LENGTH)
            invalid(label + " must be " + (allowEmpty ? "at most " : "between 1 and ")
                + MAX_TEXT_LENGTH + " characters");
        for (int i = 0; i < value.length(); i++)
            if (value.charAt(i) == 0) invalid(label + " must not contain NUL");
    }

    private static int requiredInt(JSONObject json, String key) throws JSONException, WorkspaceException {
        Object value = json.get(key);
        if (!(value instanceof Number)) invalid(key + " must be a number");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number)
            || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) invalid(key + " must be an integer");
        return (int) number;
    }

    private static long requiredLong(JSONObject json, String key) throws JSONException, WorkspaceException {
        Object value = json.get(key);
        if (!(value instanceof Number)) invalid(key + " must be a number");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number)) invalid(key + " must be an integer");
        return ((Number) value).longValue();
    }

    private static double requiredDouble(JSONObject json, String key) throws JSONException, WorkspaceException {
        Object value = json.get(key);
        if (!(value instanceof Number)) invalid(key + " must be a number");
        return ((Number) value).doubleValue();
    }

    private static String requiredString(JSONObject json, String key) throws JSONException, WorkspaceException {
        Object value = json.get(key);
        if (!(value instanceof String)) invalid(key + " must be a string");
        return (String) value;
    }

    @Nullable
    private static String optionalString(JSONObject json, String key) throws JSONException, WorkspaceException {
        if (!json.has(key) || json.isNull(key)) return null;
        Object value = json.get(key);
        if (!(value instanceof String)) invalid(key + " must be a string");
        return (String) value;
    }

    private static JSONArray requiredArray(JSONObject json, String key) throws JSONException, WorkspaceException {
        Object value = json.get(key);
        if (!(value instanceof JSONArray)) invalid(key + " must be an array");
        return (JSONArray) value;
    }

    private static JSONObject requiredObject(JSONObject json, String key) throws JSONException, WorkspaceException {
        Object value = json.get(key);
        if (!(value instanceof JSONObject)) invalid(key + " must be an object");
        return (JSONObject) value;
    }

    private static JSONObject requiredObject(JSONArray json, int index, String label)
        throws JSONException, WorkspaceException {
        Object value = json.get(index);
        if (!(value instanceof JSONObject)) invalid(label + " must be an object");
        return (JSONObject) value;
    }

    private static void invalid(String message) throws WorkspaceException {
        throw new WorkspaceException("invalid_workspace", message);
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static final class WorkspaceException extends Exception {
        @NonNull public final String code;

        public WorkspaceException(@NonNull String code, @NonNull String message) {
            super(message);
            this.code = code;
        }

        public WorkspaceException(@NonNull String code, @NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }
}
