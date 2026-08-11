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
    private List<LauncherAppEntry> cachedApps = Collections.emptyList();
    private final Map<String, LauncherAppEntry> cachedById = new LinkedHashMap<>();
    private final Map<String, LauncherAppEntry> cachedFirstByPackage = new HashMap<>();
    private final Map<String, LauncherAppEntry> cachedDefaultByPackage = new HashMap<>();
    private final Map<Character, List<LauncherAppEntry>> letterBuckets = new HashMap<>();
    private final List<Runnable> pendingRefreshCallbacks = new ArrayList<>();
    private boolean loaded;
    private boolean loading;
    private int refreshGeneration;

    private LauncherAppDataProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.iconResolver = new LauncherIconResolver(this.context);
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
        cachedApps = Collections.emptyList();
        cachedById.clear();
        cachedFirstByPackage.clear();
        cachedDefaultByPackage.clear();
        letterBuckets.clear();
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
                dispatchRefreshCallbacksLocked();
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
                cachedApps = immutableEntryList(snapshot.apps);
                cachedById.clear();
                cachedById.putAll(snapshot.byId);
                cachedFirstByPackage.clear();
                cachedFirstByPackage.putAll(snapshot.firstByPackage);
                cachedDefaultByPackage.clear();
                cachedDefaultByPackage.putAll(snapshot.defaultByPackage);
                cacheLetterBuckets(snapshot.letterBuckets);
                loaded = true;
                loading = false;
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
            cachedApps = immutableEntryList(snapshot.apps);
            cachedById.clear();
            cachedById.putAll(snapshot.byId);
            cachedFirstByPackage.clear();
            cachedFirstByPackage.putAll(snapshot.firstByPackage);
            cachedDefaultByPackage.clear();
            cachedDefaultByPackage.putAll(snapshot.defaultByPackage);
            cacheLetterBuckets(snapshot.letterBuckets);
            loaded = true;
            loading = false;
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
        PackageManager packageManager = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables = packageManager.queryIntentActivities(main, 0);
        Collections.sort(launchables, new ResolveInfo.DisplayNameComparator(packageManager));
        Map<String, ComponentName> defaultComponentsByPackage = new HashMap<>();
        // One package lookup feeds every launcher activity in that package. This load runs on the
        // provider worker; category tiles never touch PackageManager while binding.
        FirstInstallTimeCache firstInstallTimesByPackage = new FirstInstallTimeCache();

        Snapshot snapshot = new Snapshot();
        for (ResolveInfo resolveInfo : launchables) {
            ActivityInfo info = resolveInfo.activityInfo;
            if (info == null || info.packageName == null || info.name == null) continue;
            CharSequence labelSequence = info.loadLabel(packageManager);
            String label = labelSequence != null ? labelSequence.toString() : info.packageName;
            AppRef ref = new AppRef(info.packageName, info.name);
            LauncherIconResolver.ResolvedIcon resolvedIcon = iconResolver.resolveDetailed(ref, null, null);
            Drawable icon = resolvedIcon.drawable;
            int category = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && info.applicationInfo != null
                ? info.applicationInfo.category : android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED;
            long firstInstallTime = firstInstallTimesByPackage.valueFor(ref.packageName,
                packageName -> readFirstInstallTime(packageManager, packageName));
            LauncherAppEntry entry = new LauncherAppEntry(ref, label, icon,
                resolvedIcon.iconPackArtwork, category, firstInstallTime);
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
        addProfileApps(snapshot, packageManager, defaultComponentsByPackage);
        return snapshot;
    }

    private void addProfileApps(@NonNull Snapshot snapshot,
                                @NonNull PackageManager packageManager,
                                @NonNull Map<String, ComponentName> defaultComponentsByPackage) {
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
                    profile, defaultComponentsByPackage);
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
                                       @NonNull Map<String, ComponentName> defaultComponentsByPackage) {
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
                Drawable icon = null;
                try {
                    icon = activity.getIcon(0);
                } catch (Throwable ignored) {
                }
                // Resolve icon-pack and per-app choices for the exact profile, while keeping the
                // LauncherApps-provided profile icon as the system fallback.
                LauncherIconResolver.ResolvedIcon resolvedIcon = iconResolver.resolveDetailed(ref, null, icon);
                icon = resolvedIcon.drawable;
                EntryMetadata metadata = readProfileMetadata(activity, Build.VERSION.SDK_INT);
                addEntry(snapshot, packageManager, defaultComponentsByPackage,
                    new LauncherAppEntry(ref, label, icon, resolvedIcon.iconPackArtwork,
                        metadata.applicationCategory, metadata.firstInstallTimeEpochMs));
            }
        } catch (SecurityException ignored) {
        } catch (Throwable ignored) {
        }
    }

    private static long readFirstInstallTime(@NonNull PackageManager packageManager,
                                             @NonNull String packageName) {
        try {
            PackageInfo info = packageManager.getPackageInfo(packageName, 0);
            return info == null ? 0L : Math.max(0L, info.firstInstallTime);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    @NonNull
    static EntryMetadata readProfileMetadata(@NonNull LauncherActivityInfo activity, int sdkInt) {
        int category = android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED;
        if (sdkInt >= Build.VERSION_CODES.O) {
            try {
                android.content.pm.ApplicationInfo applicationInfo =
                    activity.getApplicationInfo();
                if (applicationInfo != null) category = applicationInfo.category;
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

    interface InstallTimeReader { long read(@NonNull String packageName); }

    /** Worker-local package cache; multiple launcher activities pay one PackageInfo lookup. */
    static final class FirstInstallTimeCache {
        private final Map<String, Long> values = new HashMap<>();
        long valueFor(@NonNull String packageName, @NonNull InstallTimeReader reader) {
            Long value = values.get(packageName);
            if (value != null || values.containsKey(packageName)) return value == null ? 0L : value;
            long loaded;
            try {
                loaded = Math.max(0L, reader.read(packageName));
            } catch (Throwable ignored) {
                loaded = 0L;
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
    }
}
