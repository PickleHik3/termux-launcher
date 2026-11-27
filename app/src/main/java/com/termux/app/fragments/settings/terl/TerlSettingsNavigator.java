package com.termux.app.fragments.settings.terl;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.SettingsTree;

/**
 * Opens a place in Terl's Settings inside the Settings window: a group's page, landed on a row, or
 * a v1 page a group opens, with the group's page under it so Back walks up as if the reader had
 * come down the tree.
 */
final class TerlSettingsNavigator {

    private TerlSettingsNavigator() {}

    /** Opens what a search hit names. */
    static void open(@Nullable FragmentActivity activity, @NonNull TerlSettingsSearch.Entry entry) {
        switch (entry.kind) {
            case GROUP:
                openGroup(activity, entry.group, null);
                break;
            case SETTING:
                openGroup(activity, entry.group, entry.id);
                break;
            default:
                if (entry.page != null) openPage(activity, entry.page, entry.key);
        }
    }

    /** Opens {@code group}'s page, landed on the row keyed {@code key} when not null. */
    static void openGroup(@Nullable FragmentActivity activity, @NonNull TerlSettingsTree.Group group,
                          @Nullable String key) {
        if (!(activity instanceof SettingsActivity)) return;
        ((SettingsActivity) activity).openScreen(screenClass(activity, TerlSettingsTree.pageOf(group)),
            group.titleRes, landedOn(key));
    }

    /** Opens v1's {@code page} under its group, landed on {@code key} when not null. */
    static void openPage(@Nullable FragmentActivity activity, @NonNull String page, @Nullable String key) {
        if (!(activity instanceof SettingsActivity)) return;
        TerlSettingsTree.Route route = TerlSettingsTree.routeTo(page);
        if (route == null) return;
        SettingsActivity settings = (SettingsActivity) activity;
        if (!route.startsAtHostedPage()) {
            settings.openScreen(screenClass(activity, TerlSettingsTree.pageOf(route.group)),
                route.group.titleRes, null);
        }
        for (int i = 0; i < route.pages.size(); i++) {
            SettingsTree.Screen screen = route.pages.get(i);
            boolean last = i == route.pages.size() - 1;
            settings.openScreen(screenClass(activity, screen.className), screen.titleRes,
                last ? landedOn(key) : null);
        }
    }

    @Nullable
    private static Bundle landedOn(@Nullable String key) {
        if (key == null) return null;
        Bundle args = new Bundle();
        args.putString(SettingsActivity.EXTRA_SCROLL_TO_KEY, key);
        return args;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static Class<? extends Fragment> screenClass(@NonNull FragmentActivity activity,
                                                         @NonNull String className) {
        try {
            return (Class<? extends Fragment>) activity.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            // TerlSettingsTreeTest holds every page the tree names to an existing class.
            throw new IllegalStateException(className, e);
        }
    }
}
