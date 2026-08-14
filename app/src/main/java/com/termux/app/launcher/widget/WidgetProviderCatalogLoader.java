package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.appwidget.AppWidgetHostView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Sheet-scoped, generation-tokened provider enumeration. */
public final class WidgetProviderCatalogLoader {
    public interface Callback {
        void onCatalog(long generation, @NonNull List<WidgetAppGroup> groups);
    }
    public interface PreviewCallback {
        void onPreview(@NonNull WidgetProviderItem item);
    }
    interface Boundary {
        @NonNull List<UserHandle> profiles();
        long serial(@NonNull UserHandle profile);
        @NonNull List<AppWidgetProviderInfo> providers(@NonNull UserHandle profile);
        @NonNull String providerLabel(@NonNull AppWidgetProviderInfo info);
        @NonNull String appLabel(@NonNull AppWidgetProviderInfo info);
        @Nullable Drawable appIcon(@NonNull AppWidgetProviderInfo info);
        @Nullable Drawable providerIcon(@NonNull AppWidgetProviderInfo info);
        @Nullable Drawable preview(@NonNull AppWidgetProviderInfo info);
        boolean enabled(@NonNull AppWidgetProviderInfo info);
        @NonNull default Rect defaultPadding(@NonNull AppWidgetProviderInfo info) {
            return new Rect();
        }
    }

    private static final Executor CATALOG_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Boundary boundary;
    private final Executor worker;
    private final Handler main;
    private long generation;
    // Session-lifetime catalog cache: reopening the picker must not re-query AppWidgetManager.
    // All cache state is main-thread only; packageGeneration keeps a build that raced an
    // invalidation from repopulating the cache with pre-change providers.
    @Nullable private List<WidgetAppGroup> cachedGroups;
    @Nullable private WidgetGridMetrics cachedMetrics;
    private long cachedRevision;
    private long packageGeneration;

    public WidgetProviderCatalogLoader(@NonNull Context context) {
        this(new AndroidBoundary(context), CATALOG_EXECUTOR,
            new Handler(Looper.getMainLooper()), context.getResources().getDisplayMetrics().density);
    }

    // Keep density injectable at this boundary so catalog tests can prove provider pixel fields
    // remain unchanged on a real high-density display.
    WidgetProviderCatalogLoader(Boundary boundary, Executor worker, Handler main, float density) {
        this.boundary = boundary;
        this.worker = worker;
        this.main = main;
    }

    public long load(@NonNull WidgetGridMetrics metrics, long metricsRevision,
                     @NonNull Callback callback) {
        final long token = ++generation;
        if (cachedGroups != null && cachedRevision == metricsRevision
            && metrics.equals(cachedMetrics)) {
            final List<WidgetAppGroup> groups = cachedGroups;
            main.post(() -> {
                if (token == generation) callback.onCatalog(token, groups);
            });
            return token;
        }
        final long packageToken = packageGeneration;
        worker.execute(() -> {
            List<WidgetAppGroup> groups = build(metrics);
            main.post(() -> {
                if (packageToken == packageGeneration) {
                    cachedGroups = groups; cachedMetrics = metrics; cachedRevision = metricsRevision;
                }
                if (token == generation) callback.onCatalog(token, groups);
            });
        });
        return token;
    }

    /** Drops the cached catalog; the next load re-queries AppWidgetManager. */
    public void invalidate() {
        packageGeneration++;
        cachedGroups = null; cachedMetrics = null;
    }

    /**
     * Resolves the item's preview off the main thread on its first bind. Already resolved items
     * answer synchronously. Not generation-gated: the result lands on the item itself, so a late
     * arrival is still correct data and callers guard their views by item identity.
     */
    public void loadPreview(@NonNull WidgetProviderItem item, @NonNull PreviewCallback callback) {
        if (item.previewResolved()) { callback.onPreview(item); return; }
        worker.execute(() -> {
            Drawable preview = safePreview(item.info);
            main.post(() -> {
                if (!item.previewResolved()) item.resolvePreview(preview);
                callback.onPreview(item);
            });
        });
    }

    public void cancel() { generation++; }
    public long generation() { return generation; }

    @NonNull private List<WidgetAppGroup> build(WidgetGridMetrics metrics) {
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        List<UserHandle> profiles;
        try { profiles = boundary.profiles(); }
        catch (RuntimeException exception) { return new ArrayList<>(); }
        for (UserHandle profile : profiles) {
            long serial;
            List<AppWidgetProviderInfo> providers;
            try {
                serial = boundary.serial(profile);
                providers = boundary.providers(profile);
            } catch (RuntimeException exception) { continue; }
            for (AppWidgetProviderInfo info : providers) {
                try {
                    if (!boundary.enabled(info)
                        || (info.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) {
                        continue;
                    }
                    String key = serial + "\u0000" + info.provider.getPackageName();
                    MutableGroup group = groups.get(key);
                    if (group == null) {
                        group = new MutableGroup(serial, info.provider.getPackageName(),
                            safeAppLabel(info), safeAppIcon(info));
                        groups.put(key, group);
                    }
                    Rect padding;
                    try { padding = boundary.defaultPadding(info); }
                    catch (RuntimeException exception) { padding = new Rect(); }
                    int desiredWidth = Math.max(1, info.minWidth
                        + padding.left + padding.right);
                    int desiredHeight = Math.max(1, info.minHeight
                        + padding.top + padding.bottom);
                    WidgetGridMetrics.Span span = metrics.spanForPixels(desiredWidth, desiredHeight);
                    int targetColumns = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? info.targetCellWidth : 0;
                    int targetRows = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? info.targetCellHeight : 0;
                    int columns = Math.max(span.columns, targetColumns);
                    int rows = Math.max(span.rows, targetRows);
                    boolean fits = span.fits && columns > 0 && rows > 0
                        && columns <= metrics.definition().columns
                        && rows <= metrics.definition().rows;
                    WidgetGridMetrics.Span minimum = metrics.spanForPixels(
                        Math.max(1, info.minResizeWidth), Math.max(1, info.minResizeHeight));
                    Drawable icon = safeProviderIcon(info);
                    // Previews stay deferred to loadPreview(): loadPreviewImage per provider is
                    // the dominant cost of a full catalog build.
                    group.items.add(new WidgetProviderItem(serial, info, safeProviderLabel(info),
                        icon, columns, rows, minimum.columns, minimum.rows, fits));
                } catch (RuntimeException ignored) {
                    // One broken provider must not suppress its profile or application peers.
                }
            }
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        ArrayList<MutableGroup> sorted = new ArrayList<>(groups.values());
        sorted.sort((a, b) -> {
            int label = collator.compare(a.label, b.label);
            if (label != 0) return label;
            int pkg = a.packageName.compareTo(b.packageName);
            return pkg != 0 ? pkg : Long.compare(a.serial, b.serial);
        });
        ArrayList<WidgetAppGroup> out = new ArrayList<>();
        for (MutableGroup group : sorted) {
            group.items.sort((a, b) -> {
                int label = collator.compare(a.label, b.label);
                return label != 0 ? label
                    : a.info.provider.flattenToString().compareTo(b.info.provider.flattenToString());
            });
            out.add(new WidgetAppGroup(group.serial, group.packageName, group.label,
                group.icon, group.items));
        }
        return out;
    }

    private String safeProviderLabel(AppWidgetProviderInfo info) {
        try { return boundary.providerLabel(info); }
        catch (RuntimeException exception) { return info.provider.getClassName(); }
    }
    private String safeAppLabel(AppWidgetProviderInfo info) {
        try { return boundary.appLabel(info); }
        catch (RuntimeException exception) { return info.provider.getPackageName(); }
    }
    @Nullable private Drawable safeAppIcon(AppWidgetProviderInfo info) {
        try { return boundary.appIcon(info); } catch (RuntimeException exception) { return null; }
    }
    @Nullable private Drawable safeProviderIcon(AppWidgetProviderInfo info) {
        try { return boundary.providerIcon(info); } catch (RuntimeException exception) { return null; }
    }
    @Nullable private Drawable safePreview(AppWidgetProviderInfo info) {
        try { return boundary.preview(info); } catch (RuntimeException exception) { return null; }
    }

    private static final class MutableGroup {
        final long serial; final String packageName; final String label; final Drawable icon;
        final ArrayList<WidgetProviderItem> items = new ArrayList<>();
        MutableGroup(long serial, String packageName, String label, Drawable icon) {
            this.serial = serial; this.packageName = packageName; this.label = label; this.icon = icon;
        }
    }

    private static final class AndroidBoundary implements Boundary {
        private final Context context; private final AppWidgetManager widgets;
        private final UserManager users; private final PackageManager packages;
        private final LauncherApps launcherApps;
        AndroidBoundary(Context context) {
            this.context = context; widgets = AppWidgetManager.getInstance(context);
            users = (UserManager) context.getSystemService(Context.USER_SERVICE);
            packages = context.getPackageManager();
            launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        }
        @Override public List<UserHandle> profiles() {
            return users == null ? java.util.Collections.singletonList(Process.myUserHandle())
                : users.getUserProfiles();
        }
        @Override public long serial(UserHandle profile) {
            return users == null ? 0L : users.getSerialNumberForUser(profile);
        }
        @Override public List<AppWidgetProviderInfo> providers(UserHandle profile) {
            return widgets.getInstalledProvidersForProfile(profile);
        }
        @Override public String providerLabel(AppWidgetProviderInfo info) {
            return String.valueOf(info.loadLabel(packages));
        }
        @Override public String appLabel(AppWidgetProviderInfo info) {
            try {
                ApplicationInfo app = applicationInfo(info);
                return String.valueOf(packages.getApplicationLabel(app));
            } catch (PackageManager.NameNotFoundException exception) {
                throw new IllegalStateException(exception);
            }
        }
        @Override public Drawable appIcon(AppWidgetProviderInfo info) {
            try {
                Drawable raw = packages.getApplicationIcon(applicationInfo(info));
                return packages.getUserBadgedIcon(raw, info.getProfile());
            } catch (PackageManager.NameNotFoundException exception) {
                throw new IllegalStateException(exception);
            }
        }
        @Override public Drawable providerIcon(AppWidgetProviderInfo info) {
            return info.loadIcon(context, context.getResources().getDisplayMetrics().densityDpi);
        }
        @Override public Drawable preview(AppWidgetProviderInfo info) {
            return info.loadPreviewImage(context,
                context.getResources().getDisplayMetrics().densityDpi);
        }
        @Override public boolean enabled(AppWidgetProviderInfo info) {
            try {
                if (launcherApps != null && info.getProfile() != null) {
                    return launcherApps.isPackageEnabled(info.provider.getPackageName(), info.getProfile());
                }
                ActivityInfo receiver = packages.getReceiverInfo(info.provider, 0);
                return receiver.enabled && receiver.applicationInfo != null
                    && receiver.applicationInfo.enabled;
            } catch (PackageManager.NameNotFoundException exception) {
                return false;
            }
        }
        @Override public Rect defaultPadding(AppWidgetProviderInfo info) {
            return AppWidgetHostView.getDefaultPaddingForWidget(context, info.provider, null);
        }
        private ApplicationInfo applicationInfo(AppWidgetProviderInfo info)
            throws PackageManager.NameNotFoundException {
            if (launcherApps != null && info.getProfile() != null) {
                return launcherApps.getApplicationInfo(info.provider.getPackageName(), 0,
                    info.getProfile());
            }
            return packages.getApplicationInfo(info.provider.getPackageName(), 0);
        }
    }
}
