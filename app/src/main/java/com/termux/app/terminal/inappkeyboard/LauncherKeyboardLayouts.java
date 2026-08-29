package com.termux.app.terminal.inappkeyboard;

import android.content.res.Resources;
import android.content.res.TypedArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The catalogue of text layouts the in-app keyboard can hot-swap between, and the parsing of
 * the user's chosen ring.
 *
 * <p>Every bundled layout is a resource in the keyboard module, listed by
 * {@code inapp-keyboard/tools/gen_layouts.py} into {@code res/values/layouts.xml}. On top of
 * those sits one pseudo entry, {@link #LAYOUT_MAIN}: the launcher's own layout, which is the
 * user's {@code ~/.termux/keyboard/layout.xml} when that file exists and the bundled QWERTY
 * otherwise. It is loaded by {@link TermuxInAppKeyboardLayoutLoader} rather than from a
 * resource id, which is why its {@link Layout#xmlResId} is {@code 0}.
 *
 * <p>The modal pads — numeric and Greek/math — are deliberately not in here. They are reached
 * by their own keys and are not part of the ring, so cycling never lands on a pad the user
 * cannot type prose on.
 *
 * <p>Free of Android views and preferences so the ring arithmetic stays unit-testable.
 */
public final class LauncherKeyboardLayouts {

    /** The launcher's own layout: {@code ~/.termux/keyboard/layout.xml}, else bundled QWERTY. */
    public static final String LAYOUT_MAIN = "main";

    /** Upper bound on ring size, so a hand-edited preference cannot make cycling useless. */
    public static final int MAX_SELECTION = 16;

    /** One selectable layout. */
    public static final class Layout {
        public final String id;
        public final String label;
        /** Keyboard-module {@code R.xml} id, or {@code 0} for {@link #LAYOUT_MAIN}. */
        public final int xmlResId;

        Layout(@NonNull String id, @NonNull String label, int xmlResId) {
            this.id = id;
            this.label = label;
            this.xmlResId = xmlResId;
        }
    }

    /**
     * Built once per process. The catalogue is generated at build time and its labels are the
     * layout authors' own names rather than translated strings, so nothing in it moves with a
     * configuration change.
     */
    @Nullable
    private static volatile List<Layout> sCatalog;

    private LauncherKeyboardLayouts() {
    }

    /** Every selectable layout, ring pseudo entry first, then the bundled catalogue. */
    @NonNull
    public static List<Layout> catalog(@NonNull Resources resources) {
        List<Layout> cached = sCatalog;
        if (cached != null) return cached;
        List<Layout> layouts = new ArrayList<>();
        layouts.add(new Layout(LAYOUT_MAIN,
            resources.getString(R.string.settings_keyboard_layout_main), 0));
        String[] ids = resources.getStringArray(juloo.keyboard2.R.array.inapp_layout_values);
        String[] labels = resources.getStringArray(juloo.keyboard2.R.array.inapp_layout_entries);
        TypedArray resIds =
            resources.obtainTypedArray(juloo.keyboard2.R.array.inapp_layout_ids);
        try {
            int count = Math.min(ids.length, Math.min(labels.length, resIds.length()));
            for (int i = 0; i < count; i++) {
                int xmlResId = resIds.getResourceId(i, 0);
                if (xmlResId == 0) continue;
                layouts.add(new Layout(ids[i], labels[i], xmlResId));
            }
        } finally {
            resIds.recycle();
        }
        List<Layout> catalog = Collections.unmodifiableList(layouts);
        sCatalog = catalog;
        return catalog;
    }

    /** The catalogue entry for {@code id}, or null when nothing carries that id any more. */
    @Nullable
    public static Layout find(@NonNull Resources resources, @Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        for (Layout layout : catalog(resources)) {
            if (layout.id.equals(id)) return layout;
        }
        return null;
    }

    /** The layout's own name, or the id itself when it is no longer in the catalogue. */
    @NonNull
    public static String labelFor(@NonNull Resources resources, @NonNull String id) {
        Layout layout = find(resources, id);
        return layout == null ? id : layout.label;
    }

    /**
     * The ring the preference describes: known ids only, in the stored order, deduplicated and
     * capped. Never empty — a preference that survives none of that resolves to the launcher's
     * own layout, which is what an unconfigured keyboard already uses.
     */
    @NonNull
    public static List<String> parseSelection(@NonNull Resources resources,
                                              @Nullable String stored) {
        List<String> ids = new ArrayList<>();
        if (stored != null) {
            Set<String> seen = new LinkedHashSet<>();
            for (String raw : stored.split(",")) {
                String id = raw.trim();
                if (id.isEmpty() || !seen.add(id)) continue;
                if (!LAYOUT_MAIN.equals(id) && find(resources, id) == null) continue;
                ids.add(id);
                if (ids.size() == MAX_SELECTION) break;
            }
        }
        if (ids.isEmpty()) ids.add(LAYOUT_MAIN);
        return ids;
    }

    /** Inverse of {@link #parseSelection}. */
    @NonNull
    public static String joinSelection(@NonNull List<String> ids) {
        StringBuilder joined = new StringBuilder();
        for (String id : ids) {
            if (joined.length() > 0) joined.append(',');
            joined.append(id);
        }
        return joined.length() == 0 ? LAYOUT_MAIN : joined.toString();
    }

    /**
     * The id {@code delta} steps along the ring from {@code activeId}, wrapping in both
     * directions. An active layout that has since left the ring restarts it from the front, so
     * removing the layout you are typing on cannot strand the keyboard outside its own list.
     */
    @NonNull
    public static String cycle(@NonNull List<String> selection, @Nullable String activeId,
                               int delta) {
        if (selection.isEmpty()) return LAYOUT_MAIN;
        int index = activeId == null ? -1 : selection.indexOf(activeId);
        if (index < 0) return selection.get(0);
        int size = selection.size();
        int next = ((index + delta) % size + size) % size;
        return selection.get(next);
    }
}
