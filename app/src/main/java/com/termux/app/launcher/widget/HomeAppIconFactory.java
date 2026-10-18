package com.termux.app.launcher.widget;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The views of the app icons on Home. Each is drawn from the launcher's app catalogue
 * ({@link LauncherAppDataProvider}), so it wears the same artwork as the drawer and the dock, the
 * icon pack included, and a live icon-pack change ({@code IconPackChoices.applyLive}) redraws it
 * through the catalogue's artwork listener.
 *
 * <p>An app that is gone (uninstalled, disabled, its profile switched off) is not removed: the
 * icon stays where it was, faint, under the label it was added with, and a tap opens the page's
 * edit mode on it so the remove chip is one press away. It comes back to life by itself when the
 * app does. That is the widgets' rule (a lost provider leaves its cell as "Widget unavailable"
 * until the user removes it), except that an app can return, so the state is read from the
 * catalogue at each draw rather than written down.</p>
 *
 * <p>Nothing here touches the catalogue until the first icon is drawn, so a Home with no icons
 * costs nothing.</p>
 */
public final class HomeAppIconFactory implements WidgetGridView.AppIconFactory,
    LauncherAppDataProvider.IconArtworkListener {

    public interface Listener {
        /** A tap on an app that is gone: the page's edit mode on it, for its remove chip. */
        void onUnavailableAppTapped(int id);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, HomeAppIconView> views = new HashMap<>();
    private final Map<Integer, LauncherWidgetRecord> bound = new HashMap<>();
    @Nullable private LauncherApps launcherApps;
    @Nullable private LauncherApps.Callback packageWatch;
    private boolean watching;
    private boolean warming;
    private boolean destroyed;

    public HomeAppIconFactory(@NonNull Context context, @NonNull Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    @Override @Nullable public View viewFor(@NonNull LauncherWidgetRecord record) {
        AppRef ref = record.appRef();
        if (ref == null) return null;
        watch();
        HomeAppIconView view = views.get(record.appWidgetId);
        if (view == null) {
            view = new HomeAppIconView(context);
            views.put(record.appWidgetId, view);
        }
        bound.put(record.appWidgetId, record);
        bind(view, record, ref);
        return view;
    }

    @Override public void release(int id) {
        // Kept, like a built-in's view: the cell left this page, not Home. retainOnly prunes.
    }

    /** Drops the views of icons that are no longer anywhere on Home. */
    public void retainOnly(@NonNull Set<Integer> ids) {
        views.keySet().retainAll(ids);
        bound.keySet().retainAll(ids);
    }

    /** The live view for {@code id}, while it is on Home. */
    @Nullable HomeAppIconView viewOf(int id) { return views.get(id); }

    private void bind(@NonNull HomeAppIconView view, @NonNull LauncherWidgetRecord record,
                      @NonNull AppRef ref) {
        LauncherAppDataProvider apps = LauncherAppDataProvider.getInstance(context);
        LauncherAppEntry entry = apps.findByRef(ref);
        boolean loaded = apps.hasLoadedApps();
        if (!loaded) warm(apps);
        String label = entry != null ? entry.label : record.appLabel();
        if (entry == null && !loaded) {
            // The catalogue is still loading: the icon is drawn plain and opens all the same, by
            // its reference; the artwork lands when the catalogue does.
            LauncherAppEntry stored = new LauncherAppEntry(ref, label, null);
            view.bind(defaultIcon(), label, false);
            view.setOnClickListener(v -> LauncherAppLauncher.launchEntry(v.getContext(), stored));
            return;
        }
        if (entry == null) {
            final int id = record.appWidgetId;
            view.bind(defaultIcon(), label, true);
            view.setOnClickListener(v -> listener.onUnavailableAppTapped(id));
            return;
        }
        view.bind(ownCopy(LauncherAppDataProvider.artworkFor(context, entry)), label, false);
        view.setOnClickListener(v -> LauncherAppLauncher.launchEntry(v.getContext(), entry));
    }

    /** Draws every live icon again from the catalogue as it stands now. */
    void rebindAll() {
        if (destroyed) return;
        for (Map.Entry<Integer, HomeAppIconView> held : views.entrySet()) {
            LauncherWidgetRecord record = bound.get(held.getKey());
            AppRef ref = record == null ? null : record.appRef();
            if (ref != null) bind(held.getValue(), record, ref);
        }
    }

    /** The catalogue rebuilt its artwork (an icon pack applied live, a theme): redraw. */
    @Override public void onIconArtworkInvalidated() {
        main.post(this::rebindAll);
    }

    private void warm(@NonNull LauncherAppDataProvider apps) {
        if (warming) return;
        warming = true;
        apps.warmAsync(() -> main.post(() -> {
            warming = false;
            rebindAll();
        }));
    }

    /**
     * Listens, once an icon is on Home, for the catalogue's artwork and for packages coming and
     * going. A package change that touches an app on Home asks the catalogue to rebuild that
     * package and redraws when it has, so an uninstall greys the icon and a reinstall restores
     * it without waiting for the next render.
     */
    private void watch() {
        if (watching || destroyed) return;
        watching = true;
        LauncherAppDataProvider.getInstance(context).addIconArtworkListener(this);
        try {
            launcherApps = context.getSystemService(LauncherApps.class);
            if (launcherApps == null) return;
            packageWatch = new LauncherApps.Callback() {
                @Override public void onPackageRemoved(String packageName, UserHandle user) {
                    onPackagesChanged(packageName);
                }
                @Override public void onPackageAdded(String packageName, UserHandle user) {
                    onPackagesChanged(packageName);
                }
                @Override public void onPackageChanged(String packageName, UserHandle user) {
                    onPackagesChanged(packageName);
                }
                @Override public void onPackagesAvailable(String[] packageNames, UserHandle user,
                                                          boolean replacing) {
                    onPackagesChanged(packageNames);
                }
                @Override public void onPackagesUnavailable(String[] packageNames,
                                                            UserHandle user, boolean replacing) {
                    onPackagesChanged(packageNames);
                }
            };
            launcherApps.registerCallback(packageWatch, main);
        } catch (RuntimeException ignored) {
            // No package callbacks here: icons still catch up on the next render.
            packageWatch = null;
        }
    }

    private void onPackagesChanged(@Nullable String... packageNames) {
        if (destroyed || packageNames == null) return;
        Set<String> ours = new HashSet<>();
        for (LauncherWidgetRecord record : bound.values()) {
            AppRef ref = record.appRef();
            if (ref == null) continue;
            for (String name : packageNames) if (ref.packageName.equals(name)) ours.add(name);
        }
        if (ours.isEmpty()) return;
        LauncherAppDataProvider.getInstance(context).refreshAsync(
            Collections.unmodifiableSet(ours), this::rebindAll);
    }

    public void destroy() {
        destroyed = true;
        if (watching) LauncherAppDataProvider.getInstance(context).removeIconArtworkListener(this);
        if (launcherApps != null && packageWatch != null) {
            try { launcherApps.unregisterCallback(packageWatch); }
            catch (RuntimeException ignored) { }
        }
        packageWatch = null;
        views.clear();
        bound.clear();
    }

    @Nullable private Drawable defaultIcon() {
        try {
            return context.getPackageManager().getDefaultActivityIcon();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * The icon as this view's own drawable: the catalogue hands one instance to every surface
     * that draws the app, and a drawable that is in two views at once answers only one of them.
     */
    @Nullable private Drawable ownCopy(@Nullable Drawable artwork) {
        if (artwork == null) return null;
        Drawable.ConstantState state = artwork.getConstantState();
        return state == null ? artwork : state.newDrawable(context.getResources()).mutate();
    }
}
