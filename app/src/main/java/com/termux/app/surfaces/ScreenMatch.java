package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Match rows (editor-redesign §1): Match Workspace styling on the Home card and Match Home
 * styling on the Workspace card copy the other screen's layout and glass over this one. Home and
 * the Workspace each keep their own of both, and these rows are the only place they meet.
 *
 * <ul>
 *   <li>The layout: the other screen's arrangement ({@link PlaceLayoutStore#copyArrangement}),
 *   the Workspace's being the one it stands in now, normal or minimal.</li>
 *   <li>The glass: the other half's Look, its Base, material and every surface's own value and
 *   link ({@link TermuxAppSharedPreferences#copyLookToOtherHalf}).</li>
 * </ul>
 *
 * <p>Every key a copy writes is taken first, as stored or as absent, into the {@link Undo} it
 * returns, so the row's Undo puts this screen back exactly as it was until Done.</p>
 *
 * <pre>
 *   ScreenMatch.Undo undo = ScreenMatch.match(prefs, places, WallHalf.HOME);
 *   ...
 *   if (undo != null) undo.restore(prefs);
 * </pre>
 *
 * <p>No views: a store in, a store out, held by JVM tests.</p>
 */
public final class ScreenMatch {

    private ScreenMatch() {}

    /** What one Match wrote over: every key it touched, as it stood before. */
    public static final class Undo {
        @NonNull private final WallHalf mTarget;
        @NonNull private final List<String> mKeys;
        @NonNull private final Map<String, Object> mBefore;

        Undo(@NonNull WallHalf target, @NonNull List<String> keys,
             @NonNull Map<String, Object> before) {
            mTarget = target;
            mKeys = Collections.unmodifiableList(keys);
            mBefore = before;
        }

        /** The screen the Match copied onto. */
        @NonNull
        public WallHalf target() {
            return mTarget;
        }

        /** Every key the Match may have written. */
        @NonNull
        public List<String> keys() {
            return mKeys;
        }

        /** Puts every key back as it was, removing the ones that were absent. */
        public void restore(@NonNull TermuxAppSharedPreferences prefs) {
            SharedPreferences store = prefs.getSharedPreferences();
            if (store == null)
                return;
            SharedPreferences.Editor editor = store.edit();
            for (String key : mKeys) {
                Object value = mBefore.get(key);
                if (value == null) editor.remove(key);
                else put(editor, key, value);
            }
            editor.apply();
        }
    }

    /**
     * The layout a screen stands in: Home's own on Home; on the Workspace the normal layout, or
     * the minimal one while minimal mode is on.
     */
    @NonNull
    public static LayoutVariant layoutOf(@NonNull WallHalf half, boolean minimal) {
        // Since page layouts (ADR 0015 §3) no page wears the old Home layout: Home's half is the
        // normal layout unless the Home page in front holds its own ({@link #layoutOf(WallHalf,
        // PlaceLayoutStore)}).
        return half == WallHalf.HOME ? LayoutVariant.NORMAL : LayoutVariant.of(minimal);
    }

    /** The layout a screen stands in, as the store dresses it: the Home page in front's on Home. */
    @NonNull
    public static LayoutVariant layoutOf(@NonNull WallHalf half, @NonNull PlaceLayoutStore places) {
        return half == WallHalf.HOME
            ? places.variantFor(com.termux.app.wall.PaneWallPage.WIDGETS)
            : LayoutVariant.of(places.isMinimal());
    }

    /**
     * Copies the other screen's layout and glass over {@code target}'s, and returns what it
     * wrote over; null where there is no store to write.
     */
    @Nullable
    public static Undo match(@NonNull TermuxAppSharedPreferences prefs,
                             @NonNull PlaceLayoutStore places, @NonNull WallHalf target) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null)
            return null;
        boolean minimal = places.isMinimal();
        LayoutVariant from = layoutOf(target.other(), places);
        LayoutVariant to = layoutOf(target, places);
        // Both layouts seeded before the snapshot: an orientation not used yet would otherwise be
        // taken as empty, and Undo would leave it empty rather than on its seed.
        places.seed(from);
        places.seed(to);
        List<String> keys = keysWritten(places, target, from, to);
        Map<String, ?> all = store.getAll();
        Map<String, Object> before = new HashMap<>();
        for (String key : keys) {
            Object value = all.get(key);
            if (value != null) before.put(key, value);
        }
        places.copyArrangement(from, to);
        prefs.copyLookToOtherHalf(target.other());
        return new Undo(target, keys, before);
    }

    /** Every key a Match onto {@code target} may write: the layout's, then the half's glass. */
    @NonNull
    static List<String> keysWritten(@NonNull PlaceLayoutStore places, @NonNull WallHalf target,
                                    @NonNull LayoutVariant from, @NonNull LayoutVariant to) {
        List<String> keys = new ArrayList<>(places.arrangementKeys(from, to));
        for (String key : AppearanceSnapshot.halfAbleKeys())
            keys.add(TermuxAppSharedPreferences.surfaceHalfKey(key, target));
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static void put(@NonNull SharedPreferences.Editor editor, @NonNull String key,
                            @NonNull Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }
}
