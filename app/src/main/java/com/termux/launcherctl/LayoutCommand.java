package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.layout.KindLayout;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.app.place.layout.Sizes;

import org.json.JSONException;

/**
 * {@code launcherctl layout get <kind>} and {@code layout set <kind> FILE} (ADR 0020 §10): one
 * kind's layout document, or the shared {@code sizes}, read whole and written whole through the
 * one {@link LayoutStore}, whose update asks for the one redress (ADR 0018 §2). A document handed
 * in is validated first and refused with the field named; nothing is written then.
 */
public final class LayoutCommand {

    /** The name {@code get} and {@code set} take for the shared sizes document. */
    public static final String SIZES = "sizes";

    private LayoutCommand() {}

    /** The names a command may take: every kind's key, then {@link #SIZES}. */
    @NonNull
    public static String names() {
        StringBuilder out = new StringBuilder();
        for (LayoutKind kind : LayoutKind.values()) out.append(kind.key).append(", ");
        return out.append(SIZES).toString();
    }

    /** Whether {@code name} names a document. */
    public static boolean isName(@Nullable String name) {
        return SIZES.equals(name) || LayoutKind.forKey(name) != null;
    }

    /** The document {@code name} names, as JSON; null for an unknown name. */
    @Nullable
    public static String get(@NonNull LayoutStore store, @Nullable String name) {
        if (SIZES.equals(name)) return LayoutsJson.write(store.current().sizes());
        LayoutKind kind = LayoutKind.forKey(name);
        return kind == null ? null : LayoutsJson.write(store.current().kind(kind));
    }

    /**
     * Why {@code json} may not replace the document {@code name} names, or null when it may. Run
     * before {@link #set}, off the main thread if need be: it writes nothing.
     */
    @Nullable
    public static String refusal(@Nullable String name, @NonNull String json) {
        if (!isName(name)) return "unknown layout \"" + name + "\"; one of: " + names();
        if (SIZES.equals(name)) {
            try {
                LayoutsJson.parseSizes(json);
                return null;
            } catch (JSONException e) {
                return "not a sizes document: " + e.getMessage();
            }
        }
        return LayoutsJson.validate(LayoutKind.forKey(name), json);
    }

    /**
     * Replaces the document {@code name} names with {@code json}, on the main thread (the store
     * asserts it). Returns the refusal and writes nothing when the document does not validate.
     */
    @Nullable
    public static String set(@NonNull LayoutStore store, @Nullable String name,
                             @NonNull String json) {
        String refused = refusal(name, json);
        if (refused != null) return refused;
        try {
            if (SIZES.equals(name)) {
                Sizes sizes = LayoutsJson.parseSizes(json);
                store.updateSizes(before -> sizes);
            } else {
                KindLayout layout = LayoutsJson.parseKind(json);
                store.update(LayoutKind.forKey(name), before -> layout);
            }
        } catch (JSONException e) {
            return "not a layout document: " + e.getMessage();
        }
        return null;
    }
}
