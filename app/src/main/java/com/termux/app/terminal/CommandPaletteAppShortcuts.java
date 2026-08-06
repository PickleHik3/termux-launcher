package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the {@code app.launch} bindings from the config file into a stable-id keyed index the Apps
 * section can read per row, and decides which spelling of an app's identity to write back into the
 * file. Free of Android imports so both halves are unit-testable.
 */
public final class CommandPaletteAppShortcuts {

    /**
     * Resolves an {@code app.launch} query the way the dispatcher does. The palette passes its warm
     * app cache; anything that would need a PackageManager sweep resolves to {@code null} and the
     * row simply shows no chord.
     */
    public interface Lookup {
        /** Stable id of the app {@code query} launches, or {@code null} when nothing matches. */
        @Nullable
        String stableIdFor(@NonNull String query);
    }

    private CommandPaletteAppShortcuts() {}

    /**
     * Indexes {@code argumentToStroke} — the config's {@code app.launch} query to first stroke —
     * by the stable id each query actually resolves to, so a row can look up its own chord. Queries
     * that resolve to nothing are dropped rather than guessed at: a row must not advertise a stroke
     * that launches something else. The first stroke wins per app, matching what the rest of the
     * palette shows when a tool has several.
     */
    @NonNull
    public static Map<String, String> index(@NonNull Map<String, String> argumentToStroke,
                                            @NonNull Lookup lookup) {
        Map<String, String> byStableId = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : argumentToStroke.entrySet()) {
            String query = entry.getKey();
            String stroke = entry.getValue();
            if (query.isEmpty() || stroke.isEmpty()) continue;
            String stableId = lookup.stableIdFor(query);
            if (stableId == null || byStableId.containsKey(stableId)) continue;
            byStableId.put(stableId, stroke);
        }
        return byStableId;
    }

    /**
     * What to write as an {@code app.launch} query when binding a key to the row for
     * {@code stableId}.
     *
     * <p>The bare package name when this row is that package's default launch target: it is what a
     * user reading their own config would expect to see, and it survives the app being reinstalled
     * with a renamed launcher activity. Otherwise the full stable id, which is the only thing that
     * distinguishes a work-profile or secondary activity from the default one.
     *
     * <p>This matters beyond readability. A stable id can contain {@code #userSerial=} or
     * {@code #user=}, and the config tokenizer treats {@code #} as the start of a comment — such a
     * value has to be written quoted, which {@code TerminalBindingConfigWriter} handles, but the
     * bare package name avoids the question entirely for the common case.
     */
    @NonNull
    public static String bindingArgumentFor(@NonNull String stableId,
                                           @Nullable String defaultStableIdForPackage) {
        if (defaultStableIdForPackage != null && defaultStableIdForPackage.equals(stableId)) {
            String packageName = packageOf(stableId);
            if (!packageName.isEmpty()) return packageName;
        }
        return stableId;
    }

    /** Package part of a stable id: everything before the activity separator. */
    @NonNull
    public static String packageOf(@NonNull String stableId) {
        int slash = stableId.indexOf('/');
        return slash <= 0 ? "" : stableId.substring(0, slash);
    }
}
