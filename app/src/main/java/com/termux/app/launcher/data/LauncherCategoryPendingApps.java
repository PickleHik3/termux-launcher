package com.termux.app.launcher.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The apps a categorization run has not covered: installed since the last run, or never sorted at
 * all. An app the user placed by hand from the drawer counts as sorted.
 *
 * <p>Counted per package rather than per entry — a work-profile twin is the same app to the person
 * sorting it — and against the same two sources the drawer itself resolves a category from, so the
 * number never disagrees with what the tiles show.
 */
public final class LauncherCategoryPendingApps {

    /** Where an app's category comes from, if anywhere; null is "not sorted". */
    public interface Assignment {
        @Nullable Object categoryForPackage(@NonNull String packageName);
    }

    private LauncherCategoryPendingApps() {}

    public static int count(@NonNull Context context, @NonNull List<LauncherAppEntry> catalogue) {
        LauncherCategoryAssignmentSource source = new LauncherCategoryAssignmentSource(
            new LauncherCategoryOverrideStore(context));
        return count(catalogue, source::categoryForPackage);
    }

    public static int count(@NonNull List<LauncherAppEntry> catalogue,
                            @NonNull Assignment assignment) {
        Set<String> seen = new HashSet<>();
        int pending = 0;
        for (LauncherAppEntry entry : catalogue) {
            if (entry == null) continue;
            String packageName = entry.packageLower;
            if (!seen.add(packageName)) continue;
            if (assignment.categoryForPackage(packageName) == null) pending++;
        }
        return pending;
    }
}
