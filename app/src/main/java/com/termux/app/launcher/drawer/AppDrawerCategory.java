package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/** Stable, fixed-order category identities. Labels are resources; slugs are never localized. */
public enum AppDrawerCategory {
    SUGGESTIONS("suggestions", R.string.app_drawer_category_suggestions, true),
    RECENTLY_ADDED("recently_added", R.string.app_drawer_category_recently_added, true),
    SOCIAL("social", R.string.app_drawer_category_social, false),
    PRODUCTIVITY("productivity", R.string.app_drawer_category_productivity, false),
    UTILITIES("utilities", R.string.app_drawer_category_utilities, false),
    GAMES("games", R.string.app_drawer_category_games, false),
    ENTERTAINMENT("entertainment", R.string.app_drawer_category_entertainment, false),
    SHOPPING_FOOD("shopping_food", R.string.app_drawer_category_shopping_food, false),
    FINANCE("finance", R.string.app_drawer_category_finance, false),
    HEALTH("health", R.string.app_drawer_category_health, false),
    PHOTO_VIDEO("photo_video", R.string.app_drawer_category_photo_video, false),
    TRAVEL("travel", R.string.app_drawer_category_travel, false),
    INFORMATION_READING("information_reading", R.string.app_drawer_category_information_reading,
        false),
    OTHER("other", R.string.app_drawer_category_other, false);

    @NonNull public final String slug;
    public final int labelRes;
    public final boolean synthetic;

    AppDrawerCategory(@NonNull String slug, int labelRes, boolean synthetic) {
        this.slug = slug;
        this.labelRes = labelRes;
        this.synthetic = synthetic;
    }

    @Nullable
    public static AppDrawerCategory fromSlug(@Nullable String slug) {
        if (slug == null) return null;
        for (AppDrawerCategory category : values())
            if (category.slug.equals(slug)) return category;
        return null;
    }
}
