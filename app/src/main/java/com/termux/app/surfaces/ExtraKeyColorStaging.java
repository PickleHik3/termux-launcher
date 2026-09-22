package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.extrakeys.ExtraKeyColorRole;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The colours picked on the live key row while the Appearance editor is open, before it commits.
 *
 * <p>The editor writes its other rows through to preferences and compares against a snapshot; the
 * key row cannot work that way, because a key's colour lives in {@code termux.properties} rather
 * than in preferences and writing it would rebuild the row under the user's finger. So the picks
 * are held here, previewed on the row, folded into the editor's dirty check, and written in one go
 * on Done — or dropped whole on Discard.
 *
 * <p>A key is named by its position in the row it was picked from, counting across the rows the
 * way the view builds its children. A staged {@code null} is a real choice: put this key back to
 * the row's own styling.
 */
public final class ExtraKeyColorStaging {

    private final Map<Integer, ExtraKeyColorRole> staged = new LinkedHashMap<>();

    /** Records a pick. A null role stages "no colour", which is not the same as no pick at all. */
    public void stage(int keyIndex, @Nullable ExtraKeyColorRole role) {
        staged.put(keyIndex, role);
    }

    /** Whether a key has been picked in this session at all. */
    public boolean hasPick(int keyIndex) {
        return staged.containsKey(keyIndex);
    }

    /**
     * The colour a key should be showing: the pick if there is one, otherwise what it is stored
     * with. This is what the swatch strip opens marked on.
     */
    @Nullable
    public ExtraKeyColorRole roleFor(int keyIndex, @Nullable ExtraKeyColorRole storedRole) {
        return staged.containsKey(keyIndex) ? staged.get(keyIndex) : storedRole;
    }

    public boolean isEmpty() {
        return staged.isEmpty();
    }

    public int size() {
        return staged.size();
    }

    /** Drops every pick. Nothing was written, so this is the whole of Discard for the key row. */
    public void clear() {
        staged.clear();
    }

    /** The picks to write, detached from this staging so clearing it cannot change them. */
    @NonNull
    public Map<Integer, ExtraKeyColorRole> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(staged));
    }

    /**
     * The picks folded into one string for the editor's dirty check. Empty while nothing has been
     * picked, so a session that never touched the row reads exactly as it did before.
     */
    @NonNull
    public String signature() {
        if (staged.isEmpty())
            return "";
        StringBuilder signature = new StringBuilder(32);
        for (Map.Entry<Integer, ExtraKeyColorRole> entry : staged.entrySet()) {
            signature.append(entry.getKey()).append(':')
                .append(ExtraKeyColorRole.tokenOf(entry.getValue())).append(',');
        }
        return signature.toString();
    }
}
