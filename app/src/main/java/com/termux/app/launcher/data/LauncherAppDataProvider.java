package com.termux.app.launcher.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.icon.DockIconCache;
import com.termux.app.launcher.icon.LauncherIconStore;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class LauncherAppDataProvider {

    private static LauncherAppDataProvider instance;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = newIdleFriendlyExecutor();
    private final LauncherIconResolver iconResolver;
    private final LauncherIconStore iconStore;
    private List<LauncherAppEntry> cachedApps = Collections.emptyList();
    private final Map<String, LauncherAppEntry> cachedById = new LinkedHashMap<>();
    private final Map<String, LauncherAppEntry> cachedFirstByPackage = new HashMap<>();
    private final Map<String, LauncherAppEntry> cachedDefaultByPackage = new HashMap<>();
    private final Map<Character, List<LauncherAppEntry>> letterBuckets = new HashMap<>();
    private final Map<String, Long> cachedLastUpdateByPackage = new HashMap<>();
    private final List<Runnable> pendingRefreshCallbacks = new ArrayList<>();
    private boolean loaded;
    private boolean loading;
    private boolean refreshing;
    private int refreshGeneration;

    private LauncherAppDataProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.iconResolver = new LauncherIconResolver(this.context);
        this.iconStore = new LauncherIconStore(
            this.context.getResources(),
            DockIconCache.memoryClassMb(this.context),
            ref -> iconResolver.resolveDetailed(ref, null, null).drawable);
    }

    /**
     * Where an app's raw artwork lives. Catalogue entries carry identity, not pixels — see
     * {@link LauncherIconStore} — so anything that wants to draw an app's own icon asks here.
     */
    @NonNull
    public LauncherIconStore icons() {
        return iconStore;
    }

    /** Shorthand for {@code getInstance(context).icons().artwork(entry)}. */
    @Nullable
    public static Drawable artworkFor(@NonNull Context context,
                                      @Nullable LauncherAppEntry entry) {
        return getInstance(context).icons().artwork(entry);
    }

    @NonNull
    private static ExecutorService newIdleFriendlyExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            0, 1, 15L, TimeUnit.SECONDS, new LinkedBlockingQueue<>()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @NonNull
    public static synchronized LauncherAppDataProvider getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new LauncherAppDataProvider(context);
        }
        return instance;
    }

    public synchronized void invalidate() {
        refreshGeneration++;
        loading = false;
        loaded = false;
        refreshing = false;
        cachedApps = Collections.emptyList();
        cachedById.clear();
        cachedFirstByPackage.clear();
        cachedDefaultByPackage.clear();
        letterBuckets.clear();
        cachedLastUpdateByPackage.clear();
        pendingRefreshCallbacks.clear();
    }

    public synchronized boolean hasLoadedApps() {
        return loaded;
    }

    public void warmAsync(@Nullable Runnable callback) {
        boolean shouldStartLoad = false;
        int generationToLoad = -1;
        synchronized (this) {
            if (callback != null) {
                pendingRefreshCallbacks.add(callback);
            }
            if (loaded) {
                // While a refresh is in flight the current snapshot is about to be replaced;
                // hold the callbacks so they fire once against the fresh data instead of now
                // against the stale one.
                if (!refreshing) {
                    dispatchRefreshCallbacksLocked();
                }
                return;
            }
            if (!loading) {
                loading = true;
                generationToLoad = ++refreshGeneration;
                shouldStartLoad = true;
            }
        }
        if (!shouldStartLoad) {
            return;
        }

        final int capturedGeneration = generationToLoad;
        executor.execute(() -> {
            Snapshot snapshot = loadSnapshot();
            List<Runnable> callbacks;
            synchronized (LauncherAppDataProvider.this) {
                if (capturedGeneration != refreshGeneration) {
                    return;
                }
                applySnapshotLocked(snapshot);
                callbacks = new ArrayList<>(pendingRefreshCallbacks);
                pendingRefreshCallbacks.clear();
            }
            for (Runnable pending : callbacks) {
                if (pending != null) {
                    mainHandler.post(pending);
                }
            }
        });
    }

    /**
     * Reloads the catalogue in the background while the current snapshot keeps serving reads —
     * unlike {@link #invalidate()} + {@link #warmAsync}, callers never observe an empty list, so
     * the drawer grid and dock stay populated across a package change instead of blanking for the
     * whole rebuild.
     *
     * <p>{@code changedPackages} names the packages a broadcast reported as touched: entries from
     * any other package are reused from the previous snapshot (same object, icon resolution
     * skipped) when their label and package update time are unchanged. Pass {@code null} when the
     * change scope is unknown (or icons must re-render, e.g. dynamic calendar day flips) to force
     * a full rebuild of every entry.
     */
    public void refreshAsync(@Nullable Set<String> changedPackages, @Nullable Runnable callback) {
        boolean cold;
        Map<String, LauncherAppEntry> previousById = null;
        Map<String, Long> previousLastUpdate = null;
        int generationToLoad = -1;
        synchronized (this) {
            if (callback != null) {
                pendingRefreshCallbacks.add(callback);
            }
            cold = !loaded;
            if (!cold) {
                generationToLoad = ++refreshGeneration;
                refreshing = true;
                if (changedPackages != null) {
                    previousById = new LinkedHashMap<>(cachedById);
                    previousLastUpdate = new HashMap<>(cachedLastUpdateByPackage);
                }
            }
        }
        if (cold) {
            // Nothing on screen to preserve; the plain warm-up path already serves this case and
            // will drain the callback queued above.
            warmAsync(null);
            return;
        }
        final int capturedGeneration = generationToLoad;
        final Map<String, LauncherAppEntry> reusableById = previousById;
        final Map<String, Long> reusableLastUpdate = previousLastUpdate;
        final Set<String> changed = changedPackages;
        executor.execute(() -> {
            Snapshot snapshot = loadSnapshot(reusableById, reusableLastUpdate, changed);
            List<Runnable> callbacks;
            synchronized (LauncherAppDataProvider.this) {
                if (capturedGeneration != refreshGeneration) {
                    return;
                }
                applySnapshotLocked(snapshot);
                callbacks = new ArrayList<>(pendingRefreshCallbacks);
                pendingRefreshCallbacks.clear();
            }
            for (Runnable pending : callbacks) {
                if (pending != null) {
                    mainHandler.post(pending);
                }
            }
        });
    }

    private void applySnapshotLocked(@NonNull Snapshot snapshot) {
        cachedApps = immutableEntryList(snapshot.apps);
        cachedById.clear();
        cachedById.putAll(snapshot.byId);
        cachedFirstByPackage.clear();
        cachedFirstByPackage.putAll(snapshot.firstByPackage);
        cachedDefaultByPackage.clear();
        cachedDefaultByPackage.putAll(snapshot.defaultByPackage);
        cacheLetterBuckets(snapshot.letterBuckets);
        cachedLastUpdateByPackage.clear();
        cachedLastUpdateByPackage.putAll(snapshot.lastUpdateByPackage);
        loaded = true;
        loading = false;
        refreshing = false;
    }

    @NonNull
    public synchronized List<LauncherAppEntry> getAllApps() {
        return cachedApps;
    }

    @NonNull
    public List<LauncherAppEntry> getAllAppsBlocking() {
        synchronized (this) {
            if (loaded) {
                return cachedApps;
            }
        }

        Snapshot snapshot = loadSnapshot();
        synchronized (this) {
            applySnapshotLocked(snapshot);
            return cachedApps;
        }
    }

    @Nullable
    public synchronized LauncherAppEntry findByRef(@NonNull AppRef ref) {
        LauncherAppEntry entry = cachedById.get(ref.stableId());
        return entry;
    }

    @Nullable
    public synchronized LauncherAppEntry findDefaultByPackage(@NonNull String packageName) {
        LauncherAppEntry entry = cachedDefaultByPackage.get(packageName);
        if (entry == null) {
            entry = cachedFirstByPackage.get(packageName);
        }
        return entry;
    }

    @Nullable
    public synchronized LauncherAppEntry findFirstByPackage(@NonNull String packageName) {
        LauncherAppEntry entry = cachedFirstByPackage.get(packageName);
        return entry;
    }

    @NonNull
    public synchronized List<LauncherAppEntry> getAppsForLetter(char letter) {
        List<LauncherAppEntry> bucket = letterBuckets.get(normalizeLetter(letter));
        return bucket == null ? Collections.emptyList() : bucket;
    }

    private void dispatchRefreshCallbacksLocked() {
        List<Runnable> callbacks = new ArrayList<>(pendingRefreshCallbacks);
        pendingRefreshCallbacks.clear();
        for (Runnable callback : callbacks) {
            if (callback != null) {
                mainHandler.post(callback);
            }
        }
    }

    @NonNull
    private Snapshot loadSnapshot() {
        return loadSnapshot(null, null, null);
    }

    @NonNull
    private Snapshot loadSnapshot(@Nullable Map<String, LauncherAppEntry> previousById,
                                  @Nullable Map<String, Long> previousLastUpdate,
                                  @Nullable Set<String> changedPackages) {
        PackageManager packageManager = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables = packageManager.queryIntentActivities(main, 0);
        Collections.sort(launchables, new ResolveInfo.DisplayNameComparator(packageManager));
        Map<String, ComponentName> defaultComponentsByPackage = new HashMap<>();
        // One package lookup feeds every launcher activity in that package. This load runs on the
        // provider worker; category tiles never touch PackageManager while binding.
        PackageTimesCache packageTimesByPackage = new PackageTimesCache();

        Snapshot snapshot = new Snapshot();
        for (ResolveInfo resolveInfo : launchables) {
            ActivityInfo info = resolveInfo.activityInfo;
            if (info == null || info.packageName == null || info.name == null) continue;
            CharSequence labelSequence = info.loadLabel(packageManager);
            String label = labelSequence != null ? labelSequence.toString() : info.packageName;
            AppRef ref = new AppRef(info.packageName, info.name);
            PackageTimes times = packageTimesByPackage.valueFor(ref.packageName,
                packageName -> readPackageTimes(packageManager, packageName));
            snapshot.lastUpdateByPackage.put(ref.packageName, times.lastUpdateEpochMs);
            LauncherAppEntry entry = reusableEntry(previousById, previousLastUpdate,
                changedPackages, ref, label, times.lastUpdateEpochMs);
            if (entry == null) {
                LauncherIconResolver.ResolvedIcon resolvedIcon = iconResolver.resolveDetailed(ref, null, null);
                // Resolved on this worker, so the first paint is as warm as it ever was — but the
                // pixels go to the budgeted store rather than onto the entry, which would keep one
                // icon per installed app alive for the life of the process.
                iconStore.prime(ref, resolvedIcon.drawable);
                int category = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && info.applicationInfo != null
                    ? gameNormalizedCategory(info.applicationInfo)
                    : android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED;
                entry = new LauncherAppEntry(ref, label, null,
                    resolvedIcon.iconPackArtwork, category, times.firstInstallEpochMs);
            }
            snapshot.apps.add(entry);
            snapshot.byId.put(ref.stableId(), entry);
            if (!snapshot.firstByPackage.containsKey(ref.packageName)) {
                snapshot.firstByPackage.put(ref.packageName, entry);
            }
            ComponentName defaultComponent = defaultComponentsByPackage.get(ref.packageName);
            if (!defaultComponentsByPackage.containsKey(ref.packageName)) {
                Intent defaultIntent = packageManager.getLaunchIntentForPackage(ref.packageName);
                defaultComponent = defaultIntent == null ? null : defaultIntent.getComponent();
                defaultComponentsByPackage.put(ref.packageName, defaultComponent);
            }
            if (defaultComponent != null
                && ref.packageName.equals(defaultComponent.getPackageName())
                && normalizeActivityName(ref).equals(defaultComponent.getClassName())) {
                snapshot.defaultByPackage.put(ref.packageName, entry);
            }
            char key = normalizeLetter(label.isEmpty() ? '#' : label.charAt(0));
            List<LauncherAppEntry> bucket = snapshot.letterBuckets.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                snapshot.letterBuckets.put(key, bucket);
            }
            bucket.add(entry);
        }
        addProfileApps(snapshot, packageManager, defaultComponentsByPackage,
            previousById, changedPackages);
        return snapshot;
    }

    /**
     * The previous snapshot's entry for {@code ref}, if the package broadcast scope and the
     * package's update time both say its label and icon cannot have changed. Reuse skips icon-pack
     * resolution and drawable loading — the dominant cost of a snapshot build.
     */
    @Nullable
    private static LauncherAppEntry reusableEntry(@Nullable Map<String, LauncherAppEntry> previousById,
                                                  @Nullable Map<String, Long> previousLastUpdate,
                                                  @Nullable Set<String> changedPackages,
                                                  @NonNull AppRef ref,
                                                  @NonNull String label,
                                                  long lastUpdateEpochMs) {
        if (previousById == null || changedPackages == null) return null;
        if (changedPackages.contains(ref.packageName)) return null;
        LauncherAppEntry previous = previousById.get(ref.stableId());
        if (previous == null || !previous.label.equals(label)) return null;
        Long previousUpdate = previousLastUpdate == null ? null : previousLastUpdate.get(ref.packageName);
        if (previousUpdate == null || previousUpdate != lastUpdateEpochMs) return null;
        return previous;
    }

    private void addProfileApps(@NonNull Snapshot snapshot,
                                @NonNull PackageManager packageManager,
                                @NonNull Map<String, ComponentName> defaultComponentsByPackage,
                                @Nullable Map<String, LauncherAppEntry> previousById,
                                @Nullable Set<String> changedPackages) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
            if (launcherApps == null || userManager == null) {
                return;
            }
            // Some OEM/private-space profiles are launcher-visible before (or without) appearing
            // in UserManager#getUserProfiles. Use the union so every profile Android exposes to a
            // launcher gets the same identity treatment.
            LinkedHashSet<UserHandle> profiles = new LinkedHashSet<>();
            List<UserHandle> userManagerProfiles = userManager.getUserProfiles();
            if (userManagerProfiles != null) profiles.addAll(userManagerProfiles);
            List<UserHandle> launcherProfiles = launcherApps.getProfiles();
            if (launcherProfiles != null) profiles.addAll(launcherProfiles);
            if (profiles.isEmpty()) {
                return;
            }
            UserHandle currentUser = Process.myUserHandle();
            for (UserHandle profile : profiles) {
                if (profile == null || profile.equals(currentUser)) {
                    continue;
                }
                addProfileAppsForUser(snapshot, packageManager, launcherApps, userManager,
                    profile, defaultComponentsByPackage, previousById, changedPackages);
            }
        } catch (Throwable ignored) {
            // Profile access varies by Android build. Primary-user discovery above remains valid.
        }
    }

    private void addProfileAppsForUser(@NonNull Snapshot snapshot,
                                       @NonNull PackageManager packageManager,
                                       @NonNull LauncherApps launcherApps,
                                       @NonNull UserManager userManager,
                                       @NonNull UserHandle profile,
                                       @NonNull Map<String, ComponentName> defaultComponentsByPackage,
                                       @Nullable Map<String, LauncherAppEntry> previousById,
                                       @Nullable Set<String> changedPackages) {
        try {
            List<LauncherActivityInfo> activities = launcherApps.getActivityList(null, profile);
            if (activities == null || activities.isEmpty()) {
                return;
            }
            int userId = userIdOf(profile);
            long serial = userManager.getSerialNumberForUser(profile);
            String suffix = profileSuffix(userId, serial);
            for (LauncherActivityInfo activity : activities) {
                if (activity == null || activity.getComponentName() == null) continue;
                ComponentName component = activity.getComponentName();
                String packageName = component.getPackageName();
                String activityName = component.getClassName();
                if (packageName == null || packageName.isEmpty()
                    || activityName == null || activityName.isEmpty()) {
                    continue;
                }
                String rawLabel = activity.getLabel() != null
                    ? activity.getLabel().toString() : packageName;
                String label = rawLabel + suffix;
                AppRef ref = new AppRef(packageName, activityName, userId, serial, true, suffix.trim());
                // Cross-user package update times are not readable from here, so profile reuse
                // leans on the broadcast scope plus the label alone; a profile app update reaches
                // us as a LauncherApps callback naming its package, which lands it in
                // changedPackages and forces a rebuild of exactly its entries.
                LauncherAppEntry reused = null;
                if (previousById != null && changedPackages != null
                    && !changedPackages.contains(packageName)) {
                    LauncherAppEntry previous = previousById.get(ref.stableId());
                    if (previous != null && previous.label.equals(label)) {
                        reused = previous;
                    }
                }
                if (reused != null) {
                    addEntry(snapshot, packageManager, defaultComponentsByPackage, reused);
                    continue;
                }
                Drawable icon = null;
                try {
                    icon = activity.getIcon(0);
                } catch (Throwable ignored) {
                }
                // Resolve icon-pack and per-app choices for the exact profile, while keeping the
                // LauncherApps-provided profile icon as the system fallback.
                LauncherIconResolver.ResolvedIcon resolvedIcon = iconResolver.resolveDetailed(ref, null, icon);
                iconStore.prime(ref, resolvedIcon.drawable);
                EntryMetadata metadata = readProfileMetadata(activity, Build.VERSION.SDK_INT);
                addEntry(snapshot, packageManager, defaultComponentsByPackage,
                    new LauncherAppEntry(ref, label, null, resolvedIcon.iconPackArtwork,
                        metadata.applicationCategory, metadata.firstInstallTimeEpochMs));
            }
        } catch (SecurityException ignored) {
        } catch (Throwable ignored) {
        }
    }

    private static PackageTimes readPackageTimes(@NonNull PackageManager packageManager,
                                                 @NonNull String packageName) {
        try {
            PackageInfo info = packageManager.getPackageInfo(packageName, 0);
            return info == null ? PackageTimes.UNKNOWN
                : new PackageTimes(Math.max(0L, info.firstInstallTime), Math.max(0L, info.lastUpdateTime));
        } catch (Throwable ignored) {
            return PackageTimes.UNKNOWN;
        }
    }

    static final class PackageTimes {
        static final PackageTimes UNKNOWN = new PackageTimes(0L, 0L);
        final long firstInstallEpochMs;
        final long lastUpdateEpochMs;
        PackageTimes(long firstInstallEpochMs, long lastUpdateEpochMs) {
            this.firstInstallEpochMs = firstInstallEpochMs;
            this.lastUpdateEpochMs = lastUpdateEpochMs;
        }
    }

    @NonNull
    static EntryMetadata readProfileMetadata(@NonNull LauncherActivityInfo activity, int sdkInt) {
        int category = android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED;
        if (sdkInt >= Build.VERSION_CODES.O) {
            try {
                android.content.pm.ApplicationInfo applicationInfo =
                    activity.getApplicationInfo();
                if (applicationInfo != null) category = gameNormalizedCategory(applicationInfo);
            } catch (Throwable ignored) {
            }
        }
        long firstInstallTime = 0L;
        try {
            firstInstallTime = Math.max(0L, activity.getFirstInstallTime());
        } catch (Throwable ignored) {
        }
        return new EntryMetadata(category, firstInstallTime);
    }

    static final class EntryMetadata {
        final int applicationCategory;
        final long firstInstallTimeEpochMs;
        EntryMetadata(int applicationCategory, long firstInstallTimeEpochMs) {
            this.applicationCategory = applicationCategory;
            this.firstInstallTimeEpochMs = firstInstallTimeEpochMs;
        }
    }

    /**
     * Pre-category-API games declare {@code FLAG_IS_GAME} instead of {@code CATEGORY_GAME}; the
     * flag is the same signal, so it fills in only when the declared category is undefined.
     */
    static int gameNormalizedCategory(@NonNull android.content.pm.ApplicationInfo applicationInfo) {
        int category = applicationInfo.category;
        if (category == android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED
            && (applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0)
            return android.content.pm.ApplicationInfo.CATEGORY_GAME;
        return category;
    }

    interface PackageTimesReader { @NonNull PackageTimes read(@NonNull String packageName); }

    /** Worker-local package cache; multiple launcher activities pay one PackageInfo lookup. */
    static final class PackageTimesCache {
        private final Map<String, PackageTimes> values = new HashMap<>();
        @NonNull PackageTimes valueFor(@NonNull String packageName, @NonNull PackageTimesReader reader) {
            PackageTimes value = values.get(packageName);
            if (value != null) return value;
            PackageTimes loaded;
            try {
                loaded = reader.read(packageName);
            } catch (Throwable ignored) {
                loaded = PackageTimes.UNKNOWN;
            }
            values.put(packageName, loaded);
            return loaded;
        }
    }

    @NonNull
    private static String profileSuffix(int userId, long serial) {
        if (userId >= 0) {
            return " · Clone " + userId;
        }
        if (serial >= 0) {
            return " · Clone " + serial;
        }
        return " · Clone";
    }

    // UserHandle.getIdentifier() is @SystemApi — reachable only via reflection from app code.
    // Resolve once; any failure (hidden-API policy, vendor mismatch) degrades to -1 forever.
    @Nullable private static java.lang.reflect.Method sGetIdentifierMethod;
    private static boolean sGetIdentifierResolved;

    public static int userIdOf(@NonNull UserHandle userHandle) {
        try {
            if (!sGetIdentifierResolved) {
                sGetIdentifierResolved = true;
                sGetIdentifierMethod = UserHandle.class.getMethod("getIdentifier");
            }
            if (sGetIdentifierMethod == null) return -1;
            Object result = sGetIdentifierMethod.invoke(userHandle);
            return result instanceof Integer ? (Integer) result : -1;
        } catch (Throwable ignored) {
            sGetIdentifierMethod = null;
            return -1;
        }
    }

    private void addEntry(@NonNull Snapshot snapshot,
                          @NonNull PackageManager packageManager,
                          @NonNull Map<String, ComponentName> defaultComponentsByPackage,
                          @NonNull LauncherAppEntry entry) {
        AppRef ref = entry.appRef;
        snapshot.apps.add(entry);
        snapshot.byId.put(ref.stableId(), entry);
        if (!snapshot.firstByPackage.containsKey(ref.packageName)) {
            snapshot.firstByPackage.put(ref.packageName, entry);
        }
        ComponentName defaultComponent = defaultComponentsByPackage.get(ref.packageName);
        if (!defaultComponentsByPackage.containsKey(ref.packageName)) {
            Intent defaultIntent = packageManager.getLaunchIntentForPackage(ref.packageName);
            defaultComponent = defaultIntent == null ? null : defaultIntent.getComponent();
            defaultComponentsByPackage.put(ref.packageName, defaultComponent);
        }
        if (!ref.clonedProfile && defaultComponent != null
            && ref.packageName.equals(defaultComponent.getPackageName())
            && normalizeActivityName(ref).equals(defaultComponent.getClassName())) {
            snapshot.defaultByPackage.put(ref.packageName, entry);
        }
        char key = normalizeLetter(entry.label.isEmpty() ? '#' : entry.label.charAt(0));
        List<LauncherAppEntry> bucket = snapshot.letterBuckets.get(key);
        if (bucket == null) {
            bucket = new ArrayList<>();
            snapshot.letterBuckets.put(key, bucket);
        }
        bucket.add(entry);
    }

    @NonNull
    private String normalizeActivityName(@NonNull AppRef ref) {
        if (ref.activityName.startsWith(".")) {
            return ref.packageName + ref.activityName;
        }
        return ref.activityName;
    }

    private void cacheLetterBuckets(@NonNull Map<Character, List<LauncherAppEntry>> source) {
        letterBuckets.clear();
        for (Map.Entry<Character, List<LauncherAppEntry>> entry : source.entrySet()) {
            letterBuckets.put(entry.getKey(), immutableEntryList(entry.getValue()));
        }
    }

    @NonNull
    private static List<LauncherAppEntry> immutableEntryList(@NonNull List<LauncherAppEntry> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static char normalizeLetter(char c) {
        char upper = Character.toUpperCase(c);
        if (upper >= 'A' && upper <= 'Z') {
            return upper;
        }
        return '#';
    }

    public static char normalizeLetter(@NonNull String label) {
        if (label.isEmpty()) return '#';
        return normalizeLetter(label.toUpperCase(Locale.US).charAt(0));
    }

    private static final class Snapshot {
        final List<LauncherAppEntry> apps = new ArrayList<>();
        final Map<String, LauncherAppEntry> byId = new LinkedHashMap<>();
        final Map<String, LauncherAppEntry> firstByPackage = new HashMap<>();
        final Map<String, LauncherAppEntry> defaultByPackage = new HashMap<>();
        final Map<Character, List<LauncherAppEntry>> letterBuckets = new HashMap<>();
        final Map<String, Long> lastUpdateByPackage = new HashMap<>();
    }
}
