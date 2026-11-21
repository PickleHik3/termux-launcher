package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The user's order for the Categories tiles. Built from stored category slugs, it always holds
 * every category: unknown slugs are dropped, repeats keep the first, and a category the store
 * has not seen (added in a later version) sits right after its enum-order predecessor. With
 * nothing stored the order is the enum's declaration order. Categories that are empty right now
 * keep their slot, so they return where the user left them.
 */
public final class AppDrawerCategoryOrder {

    private final List<AppDrawerCategory> full = new ArrayList<>();

    public AppDrawerCategoryOrder(@Nullable List<String> storedSlugs) {
        if (storedSlugs != null) {
            for (String slug : storedSlugs) {
                AppDrawerCategory category = AppDrawerCategory.fromSlug(slug);
                if (category != null && !full.contains(category)) full.add(category);
            }
        }
        AppDrawerCategory[] all = AppDrawerCategory.values();
        for (int i = 0; i < all.length; i++) {
            if (full.contains(all[i])) continue;
            // Enum order is walked front to back, so the predecessor is already placed.
            full.add(i == 0 ? 0 : full.indexOf(all[i - 1]) + 1, all[i]);
        }
    }

    /** @return the present categories in the user's order; the argument is not modified. */
    @NonNull
    public List<AppDrawerCategory> apply(@NonNull List<AppDrawerCategory> present) {
        List<AppDrawerCategory> result = new ArrayList<>(present.size());
        for (AppDrawerCategory category : full)
            if (present.contains(category)) result.add(category);
        return result;
    }

    /**
     * @param before the category the moved one lands in front of, or null for the end
     * @return the new full order as slugs, ready for the store
     */
    @NonNull
    public List<String> moved(@NonNull AppDrawerCategory what, @Nullable AppDrawerCategory before) {
        List<AppDrawerCategory> next = new ArrayList<>(full);
        if (before != what) {
            next.remove(what);
            int index = before == null ? -1 : next.indexOf(before);
            if (index < 0) next.add(what);
            else next.add(index, what);
        }
        return slugs(next);
    }

    /** @return the full order as slugs. */
    @NonNull
    public List<String> toSlugs() {
        return slugs(full);
    }

    @NonNull
    private static List<String> slugs(@NonNull List<AppDrawerCategory> categories) {
        List<String> out = new ArrayList<>(categories.size());
        for (AppDrawerCategory category : categories) out.add(category.slug);
        return out;
    }
}
