package com.termux.app.launcher.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Merges a categorization reply pasted back from an external AI chat into
 * {@code app-categories.conf}. It lives in the data package rather than next to the settings
 * dialog because two entry points feed it the same text now: the paste-back dialog, and the
 * notification's inline reply — the notification survives the user leaving Settings, which is the
 * whole point of that route.
 *
 * <p>Everything here is blocking disk I/O. Callers own the thread.
 */
public final class LauncherCategoryPasteImporter {


    private LauncherCategoryPasteImporter() {
    }

    /** Outcome of one merge, enough to render either a toast or a notification. */
    public static final class Result {
        /** Packages the reply assigned and that this device actually has installed. */
        public final int applied;
        /** Package lines the reply contained for packages this device does not have. */
        public final int ignored;
        /** Sections in the file after the merge. */
        public final int categories;
        /** Non-null when the file could not be written; nothing was changed in that case. */
        @Nullable public final String errorMessage;

        Result(int applied, int ignored, int categories, @Nullable String errorMessage) {
            this.applied = applied;
            this.ignored = ignored;
            this.categories = categories;
            this.errorMessage = errorMessage;
        }

        public boolean isFailure() {
            return errorMessage != null;
        }
    }

    /**
     * Collapses the launcher catalogue to the set of installed packages — a package appears once
     * per work/private profile, and the config file is package-keyed. Blocking.
     */
    @NonNull
    public static LinkedHashSet<String> knownPackages(@NonNull Context context) {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (LauncherAppEntry entry : LauncherAppDataProvider.getInstance(context).getAllAppsBlocking()) {
            if (entry == null) continue;
            packages.add(entry.appRef.packageName);
        }
        return packages;
    }

    /**
     * Merges {@code reply} into the config file. Pasted assignments win over what the file already
     * says for the same package — the user just asked for this answer — but packages the reply
     * never mentions keep their section. Records the run and invalidates the catalogue on success.
     */
    @NonNull
    public static Result apply(@NonNull Context context,
                               @NonNull Set<String> knownPackages,
                               @NonNull String reply) {
        Map<String, String> slugByPackage =
            LauncherCategorySortPrompt.parsePastedReply(reply, knownPackages);
        int applied = slugByPackage.size();
        // Every package line the reply's grammar yielded, minus the ones that survived the
        // known-package filter: dropping hallucinated packages silently would read as the feature
        // failing, so the count is reported.
        int ignored = Math.max(0, countPackageLines(reply) - applied);
        if (applied == 0) return new Result(0, ignored, 0, null);

        LinkedHashSet<String> reassigned = new LinkedHashSet<>();
        for (String packageName : slugByPackage.keySet())
            reassigned.add(packageName.toLowerCase(Locale.US));

        File file = LauncherCategoryFile.defaultFile();
        LauncherCategoryFile existing;
        try {
            existing = LauncherCategoryFile.parse(file);
        } catch (Exception ignoredError) {
            existing = LauncherCategoryFile.empty();
        }

        LinkedHashMap<String, List<String>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> section : existing.sections().entrySet()) {
            List<String> packages = new ArrayList<>();
            for (String packageName : section.getValue()) {
                if (reassigned.contains(packageName.toLowerCase(Locale.US))) continue;
                packages.add(packageName);
            }
            merged.put(section.getKey(), packages);
        }
        for (Map.Entry<String, String> assignment : slugByPackage.entrySet()) {
            List<String> packages = merged.get(assignment.getValue());
            if (packages == null) {
                packages = new ArrayList<>();
                merged.put(assignment.getValue(), packages);
            }
            packages.add(assignment.getKey());
        }

        LinkedHashMap<String, List<String>> written = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> section : merged.entrySet()) {
            if (section.getValue().isEmpty()) continue;
            written.put(section.getKey(), section.getValue());
        }

        try {
            LauncherCategoryFile.of(written).write(file);
        } catch (Exception error) {
            return new Result(0, ignored, 0,
                error.getMessage() == null ? error.toString() : error.getMessage());
        }

        LinkedHashSet<String> allPackages = new LinkedHashSet<>();
        for (List<String> packages : written.values()) allPackages.addAll(packages);
        new LauncherCategorySortState(context).recordRun(System.currentTimeMillis(),
            allPackages.size(), LauncherCategorySortState.SOURCE_PASTED, null);
        LauncherAppDataProvider.getInstance(context).invalidate();

        return new Result(applied, ignored, written.size(), null);
    }

    /** @return how many package lines the reply contained, before unknown packages are dropped. */
    private static int countPackageLines(@NonNull String reply) {
        try {
            int lines = 0;
            for (List<String> packages : LauncherCategoryFile.parse(new StringReader(reply))
                    .sections().values())
                lines += packages.size();
            return lines;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
