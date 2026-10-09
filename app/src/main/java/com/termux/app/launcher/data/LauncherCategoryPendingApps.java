package com.termux.app.launcher.data;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.R;
import com.termux.app.launcher.drawer.AppDrawerCategory;
import com.termux.app.launcher.drawer.AppDrawerCategoryAssignment;
import com.termux.app.launcher.drawer.AppDrawerCategoryClassifier;
import com.termux.app.launcher.drawer.AppDrawerCuratedCategoryMap;
import com.termux.app.launcher.drawer.AppDrawerSystemRoleResolver;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.X11Apps;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one rule for which apps still need a model to sort them, used by the sort itself, its time
 * estimate, the settings row and the drawer's notice, so none of them can disagree.
 *
 * <p>An app needs sorting when nobody placed it (no drag, no line in {@code app-categories.conf},
 * whatever that line names) and the drawer's own classifier cannot place it without guessing. The
 * classifier places an app surely from a curated row, the app's declared platform category or a
 * default role (browser, dialer, camera…); its keyword guesses and its fallback to Other are the
 * apps a model is for. Surely placed apps are never written to the file: the drawer keeps placing
 * them itself, and a later curated row or platform category still reaches them.
 *
 * <p>Counted per package — a work-profile twin is the same app to the person sorting it — and
 * never for {@code x11:linux}, which the drawer files by identity.
 */
public final class LauncherCategoryPendingApps {

    /** What the rule reads about one app. */
    public interface Placement {
        /** A drag or a line in the config file names this package (lower-cased), whatever it says. */
        boolean placedByUser(@NonNull String packageLower);

        /** The drawer classifier's answer for the entry with the user stage left out. */
        @NonNull AppDrawerCategoryAssignment builtIn(@NonNull LauncherAppEntry entry);
    }

    @Nullable private static AppDrawerCuratedCategoryMap sCurated;

    private LauncherCategoryPendingApps() {}

    /**
     * True when the built-in classifier's answer is one the drawer can stand on: a curated row, the
     * platform category or a default role, never a keyword guess or the fall to Other.
     */
    public static boolean placedConfidently(@NonNull AppDrawerCategoryAssignment assignment) {
        if (assignment.category == AppDrawerCategory.OTHER) return false;
        switch (assignment.source) {
            case USER:
            case LINUX_APP:
            case CURATED_FORCE:
            case PLATFORM:
            case CURATED_FILL:
            case ROLE:
                return true;
            default:
                return false;
        }
    }

    /** The apps that need sorting: one entry per package, in catalogue order. */
    @NonNull
    public static List<LauncherAppEntry> pending(@NonNull List<LauncherAppEntry> catalogue,
                                                 @NonNull Placement placement) {
        Set<String> seen = new HashSet<>();
        List<LauncherAppEntry> pending = new ArrayList<>();
        for (LauncherAppEntry entry : catalogue) {
            if (entry == null || X11Apps.isLinuxApp(entry.appRef)) continue;
            if (!seen.add(entry.packageLower)) continue;
            if (placement.placedByUser(entry.packageLower)) continue;
            if (placedConfidently(placement.builtIn(entry))) continue;
            pending.add(entry);
        }
        return pending;
    }

    /**
     * How many apps the notice and the settings row call waiting: the pending ones the model has not
     * already answered {@code other} for ({@link LauncherCategorySortState#getAnsweredOther()}).
     */
    public static int waiting(@NonNull List<LauncherAppEntry> catalogue, @NonNull Placement placement,
                              @NonNull Set<String> answeredOther) {
        int waiting = 0;
        for (LauncherAppEntry entry : pending(catalogue, placement)) {
            if (!answeredOther.contains(entry.packageLower)) waiting++;
        }
        return waiting;
    }

    /**
     * The placement as the drawer sees it now: the drags, the config file, the curated map, the
     * platform categories and the default roles. Blocking — preferences, a file and package-manager
     * lookups — so never on the main thread.
     */
    @WorkerThread
    @NonNull
    public static Placement placement(@NonNull Context context) {
        LauncherCategoryOverrideStore overrides = new LauncherCategoryOverrideStore(context);
        LauncherCategoryFile file = parseQuietly(LauncherCategoryFile.defaultFile());
        AppDrawerCategoryClassifier classifier = new AppDrawerCategoryClassifier(curated(context.getResources()));
        Map<String, AppDrawerCategory> roles = AppDrawerSystemRoleResolver.resolve(context);
        int sdk = Build.VERSION.SDK_INT;
        return new Placement() {
            @Override
            public boolean placedByUser(@NonNull String packageLower) {
                return overrides.get(packageLower) != null || file.categoryForPackage(packageLower) != null;
            }

            @NonNull
            @Override
            public AppDrawerCategoryAssignment builtIn(@NonNull LauncherAppEntry entry) {
                return classifier.assign(entry, sdk, roles);
            }
        };
    }

    /** The drawer's old count: packages no drag and no file line names. Replaced by {@link #waiting}. */
    public static int count(@NonNull Context context, @NonNull List<LauncherAppEntry> catalogue) {
        LauncherCategoryAssignmentSource source = new LauncherCategoryAssignmentSource(
            new LauncherCategoryOverrideStore(context));
        Set<String> seen = new HashSet<>();
        int pending = 0;
        for (LauncherAppEntry entry : catalogue) {
            if (entry == null || !seen.add(entry.packageLower)) continue;
            if (source.categoryForPackage(entry.packageLower) == null) pending++;
        }
        return pending;
    }

    @NonNull
    private static LauncherCategoryFile parseQuietly(@NonNull File file) {
        try {
            return LauncherCategoryFile.parse(file);
        } catch (IOException | RuntimeException ignored) {
            return LauncherCategoryFile.empty();
        }
    }

    /**
     * The drawer's curated map, read once per process: it is a raw resource of the APK and cannot
     * change while the process lives.
     */
    @NonNull
    private static synchronized AppDrawerCuratedCategoryMap curated(@NonNull Resources resources) {
        if (sCurated != null) return sCurated;
        try (InputStream input = resources.openRawResource(R.raw.app_drawer_category_overrides)) {
            sCurated = AppDrawerCuratedCategoryMap.parse(input);
        } catch (IOException | Resources.NotFoundException ignored) {
            sCurated = AppDrawerCuratedCategoryMap.empty();
        }
        return sCurated;
    }
}
