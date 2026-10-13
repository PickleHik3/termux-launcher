package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.LauncherToolRegistry;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which rows the keycap strip under the search box offers: the rows run from the palette most,
 * by decayed usage, whatever they are (an action, an app, a contact), then the default actions
 * for any slot usage leaves empty.
 *
 * <p>Pure, like {@link CommandPaletteRanking}: the controller supplies the usage snapshot taken
 * when the palette opened and says which keys it can still turn into a row (an installed app, a
 * contact in the loaded cache, an enabled action), then resolves the answer into entries, labels
 * and icons. Because the snapshot is fixed for the show, the strip never reshuffles under the
 * user; it only fills in when a cold cache lands.
 */
final class CommandPaletteStrip {

    /**
     * Tools the strip never offers. A "search" chip under a search box reads as the box itself;
     * scrollback search stays one query away in the palette.
     */
    static final Set<String> EXCLUDED_TOOLS =
        Collections.singleton(LauncherToolRegistry.TOOL_TERMINAL_SEARCH_SCROLLBACK);

    /** Longest argument value used as a chip label as it stands; anything longer uses the title. */
    private static final int MAX_VALUE_LABEL = 12;

    private CommandPaletteStrip() {
    }

    /**
     * The strip's row keys, best first: keys with usage by decayed score (the key itself breaking
     * a tie, so the order is stable), then {@code fallbacks} in their order, each identity at most
     * once, excluded tools and keys {@code resolvable} rejects skipped, at most {@code limit}.
     *
     * @param usage      decayed palette runs by {@link CommandPaletteActionStats#keyFor row key}
     * @param resolvable whether a key still names a row that can be shown and run
     * @param fallbacks  default row keys for slots usage leaves empty
     */
    @NonNull
    static List<String> pickKeys(@NonNull Map<String, Double> usage,
                                 @NonNull Predicate<String> resolvable,
                                 @NonNull List<String> fallbacks, int limit) {
        List<Map.Entry<String, Double>> used = new ArrayList<>(usage.entrySet());
        used.sort((a, b) -> {
            int byScore = Double.compare(b.getValue(), a.getValue());
            return byScore != 0 ? byScore : a.getKey().compareTo(b.getKey());
        });
        List<String> keys = new ArrayList<>(limit);
        Set<String> identities = new HashSet<>();
        for (Map.Entry<String, Double> entry : used) {
            if (keys.size() >= limit) return keys;
            if (entry.getValue() == null || entry.getValue() <= 0.0) continue;
            offer(entry.getKey(), resolvable, keys, identities);
        }
        for (String fallback : fallbacks) {
            if (keys.size() >= limit) return keys;
            offer(fallback, resolvable, keys, identities);
        }
        return keys;
    }

    private static void offer(@NonNull String key, @NonNull Predicate<String> resolvable,
                              @NonNull List<String> keys, @NonNull Set<String> identities) {
        if (EXCLUDED_TOOLS.contains(toolOf(key))) return;
        String identity = identityOf(key);
        if (identities.contains(identity) || !resolvable.test(key)) return;
        identities.add(identity);
        keys.add(key);
    }

    /** The tool a row key runs: everything before the arguments. */
    @NonNull
    static String toolOf(@NonNull String key) {
        int space = key.indexOf(' ');
        return space < 0 ? key : key.substring(0, space);
    }

    /** A row key's arguments, or null for a bare tool or arguments that do not parse. */
    @Nullable
    static JSONObject argumentsOf(@NonNull String key) {
        int space = key.indexOf(' ');
        if (space < 0) return null;
        try {
            return new JSONObject(key.substring(space + 1));
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * What makes two keys the same chip. A contact row's key carries the number its search matched,
     * so one contact found by name and by number is two keys; the strip names it once.
     */
    @NonNull
    static String identityOf(@NonNull String key) {
        if (!LauncherToolRegistry.TOOL_CONTACT_OPEN.equals(toolOf(key))) return key;
        JSONObject arguments = argumentsOf(key);
        String lookupKey = arguments == null ? "" : arguments.optString("lookup_key", "");
        return lookupKey.isEmpty() ? key : LauncherToolRegistry.TOOL_CONTACT_OPEN + " " + lookupKey;
    }

    /**
     * An action chip's label. A row that supplies its own argument is named by it, so the three
     * keyboard types read split, docked and floating rather than "set" three times: a short plain
     * word as it stands, otherwise the row's title without its marker or its prefix
     * ({@code Layout: Dvorak} reads Dvorak). A bare tool takes {@code bareLabel}.
     */
    @NonNull
    static String actionLabel(@NonNull CommandPaletteFilter.Entry entry,
                              @NonNull String bareLabel) {
        JSONObject arguments = entry.arguments;
        if (arguments == null || arguments.length() == 0) return bareLabel;
        if (arguments.length() == 1) {
            Iterator<String> names = arguments.keys();
            Object value = arguments.opt(names.next());
            if (value instanceof String && isPlainWord((String) value)) return (String) value;
        }
        String title = entry.title;
        int marker = title.indexOf(" · ");
        if (marker > 0) title = title.substring(0, marker);
        int colon = title.indexOf(": ");
        if (colon > 0 && colon + 2 < title.length()) title = title.substring(colon + 2);
        return title.trim().isEmpty() ? bareLabel : title.trim();
    }

    /** A contact chip's label: the first name, or the whole name when it has no first word. */
    @NonNull
    static String firstName(@NonNull String name) {
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private static boolean isPlainWord(@NonNull String value) {
        if (value.isEmpty() || value.length() > MAX_VALUE_LABEL) return false;
        for (int i = 0; i < value.length(); i++)
            if (!Character.isLetter(value.charAt(i))) return false;
        return true;
    }
}
