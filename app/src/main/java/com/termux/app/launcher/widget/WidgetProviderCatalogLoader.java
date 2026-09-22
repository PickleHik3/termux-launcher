package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.appwidget.AppWidgetHostView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.LruCache;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.launcher.icon.DockIconCache;
import com.termux.app.launcher.icon.DrawablePixels;
import com.termux.app.launcher.icon.HeapBudget;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Sheet-scoped, generation-tokened provider enumeration.
 *
 * <p>The catalog itself — labels and spans — is cached for the session so reopening the picker
 * costs nothing. Artwork is not part of it: {@code loadPreviewImage} and {@code loadIcon} both go
 * through {@code getResourcesForApplication}, which builds a fresh {@code Resources} per provider
 * and then decodes a full-density bitmap, so both are resolved when a row binds, shrunk to the
 * card's slot and held in a budgeted store that the picker empties when it closes.
 *
 * <p>The build reports twice. The app rows — label, icon, how many widgets — fall out of
 * enumeration alone and go first, so the sheet has a list to show while the per-provider pass is
 * still resolving labels and spans behind it.
 */
public final class WidgetProviderCatalogLoader implements WidgetPickerAdapter.PreviewLoader {
    public interface Callback {
        /**
         * The app rows alone: each group carries its real widget count with an empty
         * {@code providers} list. Never called for a cached catalog, which has nothing to wait for.
         */
        default void onCatalogSections(long generation, @NonNull List<WidgetAppGroup> sections) { }
        void onCatalog(long generation, @NonNull List<WidgetAppGroup> groups);
    }
    public interface PreviewCallback {
        void onPreview(@NonNull WidgetProviderItem item, @Nullable WidgetPreviewArtwork artwork);
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
        /** Whether the provider says it has generated a home-screen preview. API 35 and up. */
        default boolean offersGeneratedPreview(@NonNull AppWidgetProviderInfo info) {
            return false;
        }
        /** Tier 1: the provider's generated home-screen preview. Null where the platform has none. */
        @Nullable default RemoteViews generatedPreview(@NonNull AppWidgetProviderInfo info) {
            return null;
        }
        /** Tier 2: the provider's declared {@code previewLayout}. Null where it declares none. */
        @Nullable default RemoteViews previewLayout(@NonNull AppWidgetProviderInfo info) {
            return null;
        }
        boolean enabled(@NonNull AppWidgetProviderInfo info);
        @NonNull default Rect defaultPadding(@NonNull AppWidgetProviderInfo info) {
            return new Rect();
        }
    }

    private static final Executor CATALOG_EXECUTOR = Executors.newSingleThreadExecutor();
    /** Preview budget: one 32nd of the per-app heap, clamped into [2MB, 8MB]. */
    private static final int PREVIEW_HEAP_DIVISOR = 32;
    private static final int PREVIEW_MIN_BYTES = 2 * 1024 * 1024;
    private static final int PREVIEW_MAX_BYTES = 8 * 1024 * 1024;
    /** {@code AppWidgetManager.getWidgetPreview} and {@code generatedPreviewCategories}. */
    private static final int GENERATED_PREVIEW_SDK = 35;
    private final Boundary boundary;
    private final Executor worker;
    private final Handler main;
    private final Resources resources;
    private final int sdkInt;
    private final int previewExtentPx;
    // Main-thread only, like the catalog cache below.
    private final LruCache<String, WidgetPreviewArtwork> previews;
    // Providers whose live preview would not inflate: asked for a bitmap from here on.
    private final Set<String> demoted = new HashSet<>();
    private long generation;
    // Session-lifetime catalog cache: reopening the picker must not re-query AppWidgetManager.
    // All cache state is main-thread only; packageGeneration keeps a build that raced an
    // invalidation from repopulating the cache with pre-change providers.
    @Nullable private List<WidgetAppGroup> cachedGroups;
    @Nullable private WidgetGridMetrics cachedMetrics;
    private long cachedRevision;
    private long packageGeneration;

    public WidgetProviderCatalogLoader(@NonNull Context context) {
        this(new AndroidBoundary(context), CATALOG_EXECUTOR, new Handler(Looper.getMainLooper()),
            context.getResources(), previewBudgetBytes(DockIconCache.memoryClassMb(context)));
    }

    WidgetProviderCatalogLoader(Boundary boundary, Executor worker, Handler main,
                                Resources resources, int previewBudgetBytes) {
        this(boundary, worker, main, resources, previewBudgetBytes, Build.VERSION.SDK_INT);
    }

    WidgetProviderCatalogLoader(Boundary boundary, Executor worker, Handler main,
                                Resources resources, int previewBudgetBytes, int sdkInt) {
        this.boundary = boundary;
        this.worker = worker;
        this.main = main;
        this.resources = resources;
        this.sdkInt = sdkInt;
        this.previewExtentPx = WidgetPickerCardTemplate.largest()
            .extentPx(resources.getDisplayMetrics().density);
        this.previews = new LruCache<String, WidgetPreviewArtwork>(previewBudgetBytes) {
            @Override protected int sizeOf(String key, WidgetPreviewArtwork value) {
                return value.heldBytes();
            }
        };
    }

    static int previewBudgetBytes(int memoryClassMb) {
        return HeapBudget.of(memoryClassMb, PREVIEW_HEAP_DIVISOR, PREVIEW_MIN_BYTES,
            PREVIEW_MAX_BYTES);
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
            List<WidgetAppGroup> groups = build(metrics, sections -> main.post(() -> {
                if (token == generation) callback.onCatalogSections(token, sections);
            }));
            main.post(() -> {
                if (packageToken == packageGeneration) {
                    cachedGroups = groups; cachedMetrics = metrics; cachedRevision = metricsRevision;
                }
                if (token == generation) callback.onCatalog(token, groups);
            });
        });
        return token;
    }

    /** Drops the cached catalog and its previews; the next load re-queries AppWidgetManager. */
    public void invalidate() {
        packageGeneration++;
        cachedGroups = null; cachedMetrics = null;
        demoted.clear();
        releasePreviews();
    }

    /**
     * Resolves the item's artwork off the main thread when the store does not hold it, walking the
     * platform's ladder — generated preview, declared preview layout, then the bitmap this
     * launcher has always drawn — while a held one (including a remembered "nothing to show")
     * answers synchronously. Not generation-gated: a late arrival is still correct data and
     * callers guard their views by item identity.
     *
     * <p>Only the resolution runs off the main thread. Constructing {@link RemoteViews} inflates
     * nothing; the card applies them to a host view on the main thread, where views belong.
     */
    @Override
    public void loadPreview(@NonNull WidgetProviderItem item, @NonNull PreviewCallback callback) {
        String key = item.previewKey();
        WidgetPreviewArtwork held = previews.get(key);
        if (held != null) { callback.onPreview(item, held.isEmpty() ? null : held); return; }
        final boolean flatOnly = demoted.contains(key);
        final int extentPx = WidgetPickerCardTemplate.forSpan(item.columnSpan, item.rowSpan)
            .extentPx(resources.getDisplayMetrics().density);
        worker.execute(() -> {
            WidgetPreviewArtwork artwork = resolveArtwork(item.info, flatOnly, extentPx);
            main.post(() -> {
                previews.put(key, artwork);
                callback.onPreview(item, artwork.isEmpty() ? null : artwork);
            });
        });
    }

    /**
     * The card could not inflate what this provider offered. It is demoted to the bitmap tier for
     * the rest of the session and its held artwork dropped, so the next bind asks for something
     * that can be drawn instead of failing again.
     */
    @Override
    public void notePreviewRenderFailed(@NonNull WidgetProviderItem item) {
        String key = item.previewKey();
        if (demoted.add(key)) previews.remove(key);
    }

    /** The platform's documented precedence, each rung falling through to the next on any failure. */
    @NonNull private WidgetPreviewArtwork resolveArtwork(@NonNull AppWidgetProviderInfo info,
                                                         boolean flatOnly, int extentPx) {
        if (!flatOnly) {
            if (sdkInt >= GENERATED_PREVIEW_SDK && safeOffersGeneratedPreview(info)) {
                RemoteViews generated = safeGeneratedPreview(info);
                if (generated != null) {
                    return WidgetPreviewArtwork.live(WidgetPreviewArtwork.TIER_GENERATED, generated);
                }
            }
            if (sdkInt >= Build.VERSION_CODES.S) {
                RemoteViews declared = safePreviewLayout(info);
                if (declared != null) {
                    return WidgetPreviewArtwork.live(WidgetPreviewArtwork.TIER_PREVIEW_LAYOUT,
                        declared);
                }
            }
        }
        Drawable artwork = safePreview(info);
        if (artwork == null) artwork = safeProviderIcon(info);
        return WidgetPreviewArtwork.image(DrawablePixels.shrink(resources, artwork, extentPx));
    }

    @Override
    public void releasePreviews() { previews.evictAll(); }

    /** Preview bytes currently held. */
    @VisibleForTesting
    int previewBytes() { return previews.size(); }
    int previewBudgetBytes() { return previews.maxSize(); }
    int previewExtentPx() { return previewExtentPx; }

    public void cancel() { generation++; }
    public long generation() { return generation; }

    /** Receives the app rows as soon as enumeration has them, before the per-provider pass. */
    private interface SectionSink { void onSections(@NonNull List<WidgetAppGroup> sections); }

    /** Enumeration pass: which providers exist, grouped by app and already in display order. */
    @NonNull private ArrayList<MutableGroup> enumerate(@NonNull Collator collator) {
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
                    group.providers.add(info);
                } catch (RuntimeException ignored) {
                    // One broken provider must not suppress its profile or application peers.
                }
            }
        }
        ArrayList<MutableGroup> sorted = new ArrayList<>(groups.values());
        sorted.sort((a, b) -> {
            int label = collator.compare(a.label, b.label);
            if (label != 0) return label;
            int pkg = a.packageName.compareTo(b.packageName);
            return pkg != 0 ? pkg : Long.compare(a.serial, b.serial);
        });
        return sorted;
    }

    @NonNull private List<WidgetAppGroup> build(WidgetGridMetrics metrics,
                                                @NonNull SectionSink sections) {
        Collator collator = Collator.getInstance(Locale.getDefault());
        ArrayList<MutableGroup> sorted = enumerate(collator);
        ArrayList<WidgetAppGroup> appRows = new ArrayList<>();
        for (MutableGroup group : sorted) {
            appRows.add(new WidgetAppGroup(group.serial, group.packageName, group.label,
                group.icon, Collections.emptyList(), group.providers.size()));
        }
        sections.onSections(appRows);

        // Per provider from here down, and this is the pass that costs. The padding is asked for
        // once per package: it follows the application's target SDK, so every provider in a
        // package gets the same answer, and each getDefaultPaddingForWidget call is another
        // PackageManager round trip for it.
        ArrayList<WidgetAppGroup> out = new ArrayList<>();
        for (MutableGroup group : sorted) {
            Map<String, Rect> paddings = new LinkedHashMap<>();
            for (AppWidgetProviderInfo info : group.providers) {
                try {
                    String packageName = info.provider.getPackageName();
                    Rect padding = paddings.get(packageName);
                    if (padding == null) {
                        try { padding = boundary.defaultPadding(info); }
                        catch (RuntimeException exception) { padding = new Rect(); }
                        paddings.put(packageName, padding);
                    }
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
                    // Preview and icon both stay deferred to loadPreview(): resolving either per
                    // provider is what made a full catalog build slow.
                    group.items.add(new WidgetProviderItem(group.serial, info,
                        safeProviderLabel(info), columns, rows, minimum.columns, minimum.rows,
                        fits));
                } catch (RuntimeException ignored) {
                    // One broken provider must not suppress its application peers.
                }
            }
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
        try { return boundary.providerIcon(info); }
        catch (RuntimeException | LinkageError exception) { return null; }
    }
    @Nullable private Drawable safePreview(AppWidgetProviderInfo info) {
        try { return boundary.preview(info); }
        catch (RuntimeException | LinkageError exception) { return null; }
    }
    private boolean safeOffersGeneratedPreview(AppWidgetProviderInfo info) {
        try { return boundary.offersGeneratedPreview(info); }
        catch (RuntimeException | LinkageError exception) { return false; }
    }
    @Nullable private RemoteViews safeGeneratedPreview(AppWidgetProviderInfo info) {
        try { return boundary.generatedPreview(info); }
        catch (RuntimeException | LinkageError exception) { return null; }
    }
    @Nullable private RemoteViews safePreviewLayout(AppWidgetProviderInfo info) {
        try { return boundary.previewLayout(info); }
        catch (RuntimeException | LinkageError exception) { return null; }
    }

    private static final class MutableGroup {
        final long serial; final String packageName; final String label; final Drawable icon;
        final ArrayList<AppWidgetProviderInfo> providers = new ArrayList<>();
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
            return users == null ? Collections.singletonList(Process.myUserHandle())
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
        /**
         * {@code generatedPreviewCategories} arrived in API 35, so reading it off an older
         * platform is a {@code NoSuchFieldError} rather than a missing method; the loader catches
         * it along with everything else this rung can throw.
         */
        @Override public boolean offersGeneratedPreview(AppWidgetProviderInfo info) {
            return (info.generatedPreviewCategories
                & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0;
        }
        @Override public RemoteViews generatedPreview(AppWidgetProviderInfo info) {
            if (widgets == null) return null;
            return widgets.getWidgetPreview(info.provider, info.getProfile(),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        }
        /**
         * The layout is resolved in this process, which is the same hack every launcher makes: the
         * platform offers no way to render another profile's preview layout.
         */
        @Override public RemoteViews previewLayout(AppWidgetProviderInfo info) {
            if (info.previewLayout == 0) return null;
            return new RemoteViews(info.provider.getPackageName(), info.previewLayout);
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
