package com.termux.app.launcher.data;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.X11Apps;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * The one dedupe every call site that hands the drawer's catalogue to something outside the
 * drawer needs: collapse a work/private twin to a single entry, keyed by package, in first-seen
 * order — and leave the reserved Linux-apps package ({@link X11Apps#PACKAGE}) out entirely.
 *
 * <p>Every Linux app shares that one package; the per-app identity lives in the activity half of
 * its {@code AppRef} instead. Collapsing the ordinary way would hand whatever reads this map a
 * single fake "app" standing in for all of them — which is exactly how an on-device categorizer
 * run once wrote one machine-made guess that filed every Linux app under the same category
 * (see {@code project-docs/distro-apps/SPEC.md}). The drawer already has its own rule for these
 * ({@code AppDrawerCategoryClassifier}'s {@code LINUX_APP} source); nothing that reads this map
 * should ever get a turn to guess on their behalf.
 *
 * <p>Used by {@link LauncherCategorySortService}, the paste-back category dialogs, and
 * {@link LauncherCategoryPasteImporter#knownPackages}.
 */
public final class LauncherCategoryCatalogue {

    private LauncherCategoryCatalogue() {}

    /** @return package to first-seen label, in catalogue order, minus duplicates and Linux apps. */
    @NonNull
    public static LinkedHashMap<String, String> labelByPackage(
        @NonNull List<LauncherAppEntry> catalogue) {
        LinkedHashMap<String, String> labelByPackage = new LinkedHashMap<>();
        for (LauncherAppEntry entry : catalogue) {
            if (entry == null) continue;
            if (X11Apps.isLinuxApp(entry.appRef)) continue;
            if (labelByPackage.containsKey(entry.appRef.packageName)) continue;
            labelByPackage.put(entry.appRef.packageName, entry.label);
        }
        return labelByPackage;
    }
}
