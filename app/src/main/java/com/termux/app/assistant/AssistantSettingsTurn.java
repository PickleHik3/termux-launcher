package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One settings turn of the Assistant, as pure code (ADR 0035, "the model chooses, the app does"):
 * <ol>
 * <li>{@link #lookup} finds the settings nearest the person's words, from the ones the Assistant
 * may offer, and the ones it may not that match too;</li>
 * <li>{@link #instructions} and {@link #tools} are what the model is sent: only that shortlist;</li>
 * <li>{@link #interpret} reads the model's reply, a proper tool call or the same call written as
 * text, and keeps only a change that names a setting of this turn's shortlist with a value its
 * declaration allows (or up or down, which becomes one step). Anything else is dropped.</li>
 * </ol>
 * Nothing here writes a setting. The registry is only read; a step is previewed.
 */
final class AssistantSettingsTurn {
    static final int SHORTLIST = 8;
    static final int MAX_CHANGES = 2;
    private static final int MAX_LINKS = 2;
    static final String TOOL_SET = "set_setting";
    static final String TOOL_ASK = "ask_user";

    private static final String INSTRUCTIONS =
        "You change settings of the launcher for the user. Only use the settings listed below. "
            + "To change one, call set_setting with its id and the new value. For a relative request "
            + "(bigger, less, a bit more), use the value up or down: the app moves the setting one step. "
            + "Change at most two settings. If the request is unclear, call ask_user with one short "
            + "question. If the user is not asking to change a setting, or nothing listed fits, answer in "
            + "one short sentence and change nothing.";

    /** A setting of this turn's shortlist with the value it holds now. */
    static final class Candidate {
        @NonNull final SettingDeclaration declaration;
        @NonNull final String current;

        Candidate(@NonNull SettingDeclaration declaration, @NonNull String current) {
            this.declaration = declaration;
            this.current = current;
        }
    }

    /** The lookup's answer: what the model may use, and the settings it may not offer that fit. */
    static final class Shortlist {
        @NonNull final List<Candidate> offered;
        @NonNull final List<SettingDeclaration> linked;

        Shortlist(@NonNull List<Candidate> offered, @NonNull List<SettingDeclaration> linked) {
            this.offered = offered;
            this.linked = linked;
        }

        @Nullable
        Candidate find(@Nullable String id) {
            if (id == null) return null;
            for (Candidate each : offered) if (each.declaration.id.equals(id)) return each;
            return null;
        }
    }

    /** What the reply came to. */
    static final class Answer {
        /** The checked changes, at most {@link #MAX_CHANGES}. */
        @NonNull final List<SettingsRegistry.Change> changes;
        /** The model's one question, when it asked and changed nothing. */
        @Nullable final String question;
        /** The model's own words with any call written as text taken out. */
        @NonNull final String text;
        /** Calls that were dropped: unknown id, off the shortlist, a value out of range. */
        final int dropped;

        Answer(@NonNull List<SettingsRegistry.Change> changes, @Nullable String question,
               @NonNull String text, int dropped) {
            this.changes = changes;
            this.question = question;
            this.text = text;
            this.dropped = dropped;
        }
    }

    private AssistantSettingsTurn() {
    }

    // ------------------------------------------------------------------------ lookup

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "the", "a", "an", "my", "me", "i", "it", "is", "are", "to", "of", "on", "in", "for", "and", "or",
        "too", "so", "can", "you", "please", "make", "set", "change", "turn", "how", "do", "does", "this",
        "that", "with", "at", "be", "want", "would", "like", "just", "when", "up", "down"));

    /**
     * The 8 settings nearest {@code message}: a keyword match on titles, summaries and complaint
     * phrases, from those the Assistant may offer. Settings it may not offer are never in
     * {@link Shortlist#offered}; the best of them that match are in {@link Shortlist#linked}.
     */
    @NonNull
    static Shortlist lookup(@NonNull SettingsRegistry registry, @NonNull String message) {
        String lower = message.toLowerCase(Locale.ROOT).trim();
        Set<String> query = stems(lower);
        List<SettingsRegistry.Entry> all = registry.list();
        List<Scored> offered = new ArrayList<>();
        List<Scored> linked = new ArrayList<>();
        for (SettingsRegistry.Entry entry : all) {
            SettingDeclaration d = entry.declaration;
            if (d.kind == SettingDeclaration.Kind.TEXT || d.title.isEmpty()) continue;
            int score = score(d, lower, query);
            if (score <= 0) continue;
            (d.mayOffer ? offered : linked).add(new Scored(d, entry.value, score));
        }
        offered.sort((a, b) -> b.score - a.score);
        linked.sort((a, b) -> b.score - a.score);
        List<Candidate> picked = new ArrayList<>();
        for (int i = 0; i < offered.size() && i < SHORTLIST; i++) {
            picked.add(new Candidate(offered.get(i).declaration, offered.get(i).value));
        }
        List<SettingDeclaration> links = new ArrayList<>();
        for (int i = 0; i < linked.size() && i < MAX_LINKS; i++) links.add(linked.get(i).declaration);
        return new Shortlist(picked, links);
    }

    private static final class Scored {
        final SettingDeclaration declaration;
        final String value;
        final int score;

        Scored(SettingDeclaration declaration, String value, int score) {
            this.declaration = declaration;
            this.value = value;
            this.score = score;
        }
    }

    private static int score(@NonNull SettingDeclaration d, @NonNull String lowerMessage,
                             @NonNull Set<String> query) {
        int score = 0;
        Set<String> title = stems(d.title.toLowerCase(Locale.ROOT));
        Set<String> summary = stems(d.summary.toLowerCase(Locale.ROOT));
        Set<String> phraseWords = new HashSet<>();
        for (String phrase : d.phrases) {
            String p = phrase.toLowerCase(Locale.ROOT).trim();
            if (p.isEmpty()) continue;
            if (lowerMessage.contains(p) || (lowerMessage.length() > 3 && p.contains(lowerMessage))) score += 8;
            phraseWords.addAll(stems(p));
        }
        for (String word : query) {
            if (title.contains(word)) score += 3;
            if (phraseWords.contains(word)) score += 2;
            if (summary.contains(word)) score += 1;
        }
        return score;
    }

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

    private static Set<String> stems(@NonNull String lower) {
        Set<String> out = new HashSet<>();
        Matcher m = WORD.matcher(lower);
        while (m.find()) {
            String w = m.group();
            if (w.length() < 3 || STOP.contains(w)) continue;
            if (w.length() > 3 && w.endsWith("s")) w = w.substring(0, w.length() - 1);
            out.add(w.length() > 5 ? w.substring(0, 5) : w);
        }
        return out;
    }

    // ------------------------------------------------------------------------ request

    /** The instructions and the shortlist, one line a setting, as the model's system message. */
    @NonNull
    static String instructions(@NonNull Shortlist shortlist) {
        StringBuilder out = new StringBuilder(INSTRUCTIONS).append("\n\nSettings:");
        for (Candidate c : shortlist.offered) {
            SettingDeclaration d = c.declaration;
            out.append('\n').append(d.id).append(" | ").append(d.title);
            if (!d.summary.isEmpty()) out.append(" - ").append(d.summary);
            out.append(" | ").append(allowed(d)).append(" | now: ").append(c.current);
        }
        return out.toString();
    }

    /** Words for what a setting accepts, for the model. */
    @NonNull
    static String allowed(@NonNull SettingDeclaration d) {
        switch (d.kind) {
            case INTEGER:
                return "number " + d.min + " to " + d.max + " (up or down moves by " + d.step + ")";
            case BOOLEAN:
                return "true or false";
            default:
                return "one of: " + String.join(", ", d.choices)
                    + (d.ordered ? " (in that order; up or down moves one place)" : "");
        }
    }

    /** The tools sent beside the instructions: set_setting over this turn's ids, and ask_user. */
    @NonNull
    static JSONArray tools(@NonNull Shortlist shortlist) {
        try {
            JSONArray ids = new JSONArray();
            for (Candidate c : shortlist.offered) ids.put(c.declaration.id);
            JSONObject set = new JSONObject().put("type", "function").put("function", new JSONObject()
                .put("name", TOOL_SET).put("description", "Set one setting to a value.")
                .put("parameters", new JSONObject().put("type", "object")
                    .put("properties", new JSONObject()
                        .put("id", new JSONObject().put("type", "string").put("enum", ids)
                            .put("description", "setting id"))
                        .put("value", new JSONObject().put("type", "string").put("description",
                            "new value: true or false, one of the listed options, a number, or up or down")))
                    .put("required", new JSONArray().put("id").put("value"))));
            JSONObject ask = new JSONObject().put("type", "function").put("function", new JSONObject()
                .put("name", TOOL_ASK).put("description", "Ask the user one short clarifying question.")
                .put("parameters", new JSONObject().put("type", "object")
                    .put("properties", new JSONObject().put("question", new JSONObject().put("type", "string")))
                    .put("required", new JSONArray().put("question"))));
            return new JSONArray().put(set).put(ask);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A rough token count for the instructions and tools, so the chat history leaves room for them. */
    static int reserveTokens(@NonNull String instructions, @NonNull JSONArray tools) {
        return (instructions.length() + tools.toString().length()) / 3 + 16;
    }

    // ------------------------------------------------------------------------ reply

    private static final class Call {
        final String name;
        final Map<String, String> args = new LinkedHashMap<>();

        Call(String name) {
            this.name = name;
        }
    }

    /**
     * Reads a reply: {@code toolCalls} as the runtime sent them (OpenAI shape, may be null) and
     * {@code text}, which may hold the same calls written out.
     */
    @NonNull
    static Answer interpret(@NonNull SettingsRegistry registry, @NonNull Shortlist shortlist,
                            @Nullable String text, @Nullable JSONArray toolCalls) {
        List<Call> calls = new ArrayList<>();
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.length(); i++) {
                Call call = structured(toolCalls.optJSONObject(i));
                if (call != null) calls.add(call);
            }
        }
        String rest = text == null ? "" : text;
        if (calls.isEmpty()) rest = looseCalls(rest, calls);
        List<SettingsRegistry.Change> changes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String question = null;
        int dropped = 0;
        for (Call call : calls) {
            if (TOOL_ASK.equals(call.name)) {
                String q = call.args.get("question");
                if (question == null && q != null && !q.trim().isEmpty()) question = q.trim();
            } else if (TOOL_SET.equals(call.name)) {
                SettingsRegistry.Change change = check(registry, shortlist, call);
                if (change == null) dropped++;
                else if (changes.size() >= MAX_CHANGES || !seen.add(change.id)) dropped++;
                else changes.add(change);
            } else {
                dropped++;
            }
        }
        return new Answer(changes, changes.isEmpty() ? question : null, rest.trim(), dropped);
    }

    /** One call's change if it names a setting of the shortlist and a value that setting allows. */
    @Nullable
    private static SettingsRegistry.Change check(@NonNull SettingsRegistry registry,
                                                 @NonNull Shortlist shortlist, @NonNull Call call) {
        String id = call.args.get("id");
        String raw = call.args.get("value");
        Candidate candidate = shortlist.find(id == null ? null : id.trim());
        if (candidate == null || raw == null) return null;
        SettingDeclaration d = candidate.declaration;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        String next;
        if (!d.choices.contains(value) && ("up".equals(value) || "down".equals(value))) {
            SettingsRegistry.Change step = registry.previewStep(d.id, "up".equals(value)
                ? SettingsRegistry.Direction.UP : SettingsRegistry.Direction.DOWN).change();
            if (step == null) return null;
            next = step.newValue;
        } else {
            next = d.kind == SettingDeclaration.Kind.INTEGER ? wholeNumber(value) : value;
            if (next == null || registry.check(d.id, next) != null) return null;
        }
        String old = registry.get(d.id);
        if (old == null || old.equals(next)) return null;
        return new SettingsRegistry.Change(d, old, next);
    }

    /** "120", "120.0" and "120%" as 120; null for anything else. */
    @Nullable
    private static String wholeNumber(@NonNull String value) {
        String v = value.endsWith("%") ? value.substring(0, value.length() - 1).trim() : value;
        try {
            return String.valueOf(Integer.parseInt(v));
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(v);
                return d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE ? String.valueOf((int) d) : null;
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }

    @Nullable
    private static Call structured(@Nullable JSONObject source) {
        if (source == null) return null;
        JSONObject function = source.optJSONObject("function");
        if (function == null) function = source;
        String name = function.optString("name", "");
        if (name.isEmpty()) return null;
        Call call = new Call(name);
        Object arguments = function.opt("arguments");
        if (arguments instanceof String) {
            try {
                arguments = ((String) arguments).trim().isEmpty() ? new JSONObject()
                    : new JSONObject((String) arguments);
            } catch (JSONException e) {
                parsePairs((String) arguments, call.args);
                return call;
            }
        }
        if (arguments instanceof JSONObject) copy((JSONObject) arguments, call.args);
        return call;
    }

    private static void copy(@NonNull JSONObject from, @NonNull Map<String, String> into) {
        JSONArray names = from.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            Object value = from.opt(key);
            if (value != null && value != JSONObject.NULL) into.put(key, String.valueOf(value));
        }
    }

    // ---- loose text: what a model that does not return a proper call writes instead

    private static final Pattern CALL_START = Pattern.compile("\\b(set_setting|ask_user)\\s*([({])");
    private static final String QUOTE = "<|\"|>";

    /**
     * Finds calls written as text, in the forms the phone's models produce: {@code name{id:<|"|>x<|"|>,value:y}},
     * {@code name(id="x", value="y")} and {@code name {"id":"x","value":"y"}}, and a bare JSON object with
     * {@code name} and {@code arguments}. Adds them to {@code out}; returns the text without them.
     */
    @NonNull
    private static String looseCalls(@NonNull String text, @NonNull List<Call> out) {
        String t = text.replace("```json", "").replace("```", "");
        StringBuilder rest = new StringBuilder();
        int from = 0;
        Matcher m = CALL_START.matcher(t);
        while (m.find(from)) {
            int open = m.end() - 1;
            int close = matching(t, open);
            if (close < 0) break;
            Call call = new Call(m.group(1));
            parseBody(t.charAt(open) == '{' ? t.substring(open, close + 1) : t.substring(open + 1, close),
                call.args);
            out.add(call);
            rest.append(t, from, m.start());
            from = close + 1;
        }
        rest.append(t.substring(Math.min(from, t.length())));
        if (out.isEmpty()) {
            String trimmed = t.trim();
            if (trimmed.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(trimmed.replace(QUOTE, "\""));
                    Call call = structured(json);
                    if (call != null && (TOOL_SET.equals(call.name) || TOOL_ASK.equals(call.name))) {
                        out.add(call);
                        return "";
                    }
                } catch (JSONException ignored) {
                    // Not a call: it stays text.
                }
            }
            return text;
        }
        return rest.toString();
    }

    /** The index of the bracket closing the one at {@code open}, skipping quoted text; -1 when none. */
    private static int matching(@NonNull String t, int open) {
        char openChar = t.charAt(open);
        char closeChar = openChar == '(' ? ')' : '}';
        int depth = 0;
        char quote = 0;
        for (int i = open; i < t.length(); i++) {
            if (t.startsWith(QUOTE, i)) {
                int end = t.indexOf(QUOTE, i + QUOTE.length());
                if (end < 0) return -1;
                i = end + QUOTE.length() - 1;
                continue;
            }
            char c = t.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == openChar) depth++;
            else if (c == closeChar && --depth == 0) return i;
        }
        return -1;
    }

    private static void parseBody(@NonNull String body, @NonNull Map<String, String> into) {
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            try {
                copy(new JSONObject(trimmed.replace(QUOTE, "\"")), into);
                return;
            } catch (JSONException ignored) {
                // Pairs below.
            }
            parsePairs(trimmed.substring(1, trimmed.length() - 1), into);
            return;
        }
        parsePairs(trimmed, into);
    }

    private static final Pattern PAIR = Pattern.compile(
        "([A-Za-z_]+)\\s*[=:]\\s*(?:<\\|\"\\|>(.*?)<\\|\"\\|>|\"([^\"]*)\"|'([^']*)'|([^,)}]*))",
        Pattern.DOTALL);

    private static void parsePairs(@NonNull String body, @NonNull Map<String, String> into) {
        Matcher m = PAIR.matcher(body);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3)
                : m.group(4) != null ? m.group(4) : m.group(5);
            if (value != null) into.put(m.group(1), value.trim());
        }
    }

    // ------------------------------------------------------------------------ card

    /** The card for an answer: its changes, or the Settings links when only those fit; null for neither. */
    @Nullable
    static SettingsCard card(@NonNull Shortlist shortlist, @NonNull Answer answer) {
        List<SettingsCard.Link> links = new ArrayList<>();
        for (SettingDeclaration d : shortlist.linked) links.add(new SettingsCard.Link(d.title, d.title));
        if (!answer.changes.isEmpty()) {
            List<SettingsCard.Item> items = new ArrayList<>();
            for (SettingsRegistry.Change c : answer.changes) {
                items.add(new SettingsCard.Item(c.id, c.declaration.title, c.oldValue, c.newValue,
                    label(c.declaration, c.oldValue), label(c.declaration, c.newValue)));
            }
            return SettingsCard.changes(items, links);
        }
        if (answer.question == null && !links.isEmpty() && (answer.text.isEmpty() || shortlist.offered.isEmpty()))
            return SettingsCard.links(links);
        return null;
    }

    /** A value as a person reads it: On, Off, Large, 120. */
    @NonNull
    static String label(@NonNull SettingDeclaration d, @NonNull String value) {
        if (d.kind == SettingDeclaration.Kind.BOOLEAN) return Boolean.parseBoolean(value) ? "On" : "Off";
        if (d.kind != SettingDeclaration.Kind.CHOICE || value.isEmpty()) return value;
        String spaced = value.replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
