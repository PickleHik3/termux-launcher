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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
 * <p>Folders on Home ({@link HomeFolderView}) are drawn here too, from the same catalogue: the
 * first four apps' artwork in the miniature, an app that is gone faint, and a tap opens the
 * folder ({@link Listener#onFolderTapped}).</p>
 *
 * <p>Nothing here touches the catalogue until the first icon is drawn, so a Home with no icons
 * costs nothing.</p>
 */
public final class HomeAppIconFactory implements WidgetGridView.AppIconFactory,
    LauncherAppDataProvider.IconArtworkListener {

    public interface Listener {
        /** A tap on an app that is gone: the page's edit mode on it, for its remove chip. */
        void onUnavailableAppTapped(int id);

        /** A tap on a folder: its popup, anchored on the folder's view. */
        void onFolderTapped(int id, @NonNull View anchor);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, HomeAppIconView> views = new HashMap<>();
    private final Map<Integer, HomeFolderView> folders = new HashMap<>();
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
        if (record.isFolder()) return folderViewFor(record);
        AppRef ref = record.appRef();
        if (ref == null) return null;
        watch();
        // A key that was a folder a moment ago (one app left) is an icon now.
        folders.remove(record.appWidgetId);
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

    @NonNull private View folderViewFor(@NonNull LauncherWidgetRecord record) {
        watch();
        // A key that was an icon a moment ago (another dropped on it) is a folder now.
        views.remove(record.appWidgetId);
        HomeFolderView view = folders.get(record.appWidgetId);
        if (view == null) {
            view = new HomeFolderView(context);
            folders.put(record.appWidgetId, view);
        }
        bound.put(record.appWidgetId, record);
        bindFolder(view, record);
        return view;
    }

    private void bindFolder(@NonNull HomeFolderView view, @NonNull LauncherWidgetRecord record) {
        LauncherAppDataProvider apps = LauncherAppDataProvider.getInstance(context);
        boolean loaded = apps.hasLoadedApps();
        if (!loaded) warm(apps);
        List<Drawable> artwork = new ArrayList<>();
        List<Boolean> unavailable = new ArrayList<>();
        for (LauncherWidgetRecord.FolderApp member : record.folderApps()) {
            if (artwork.size() >= HomeFolderView.SHOWN) break;
            LauncherAppEntry entry = apps.findByRef(member.app());
            artwork.add(entry == null ? defaultIcon()
                : ownCopy(LauncherAppDataProvider.artworkFor(context, entry)));
            unavailable.add(entry == null && loaded);
        }
        view.bind(record.folderName(), artwork, unavailable);
        final int id = record.appWidgetId;
        view.setOnClickListener(v -> listener.onFolderTapped(id, v));
    }

    /**
     * The catalogue's entry for one app of a folder, or null while the catalogue is loading or
     * when the app is gone.
     */
    @Nullable LauncherAppEntry entryFor(@NonNull LauncherWidgetRecord.FolderApp member) {
        return LauncherAppDataProvider.getInstance(context).findByRef(member.app());
    }

    /** Whether the catalogue has loaded, so a missing entry means the app is gone. */
    boolean catalogueLoaded() {
        return LauncherAppDataProvider.getInstance(context).hasLoadedApps();
    }

    /** The artwork for {@code entry} as a drawable of its own, or the default icon for null. */
    @Nullable Drawable artworkFor(@Nullable LauncherAppEntry entry) {
        return entry == null ? defaultIcon()
            : ownCopy(LauncherAppDataProvider.artworkFor(context, entry));
    }

    /** Drops the views of icons that are no longer anywhere on Home. */
    public void retainOnly(@NonNull Set<Integer> ids) {
        views.keySet().retainAll(ids);
        folders.keySet().retainAll(ids);
        bound.keySet().retainAll(ids);
    }

    /** The live view for {@code id}, while it is on Home. */
    @Nullable HomeAppIconView viewOf(int id) { return views.get(id); }

    /** The live folder view for {@code id}, while it is on Home. */
    @Nullable HomeFolderView folderViewOf(int id) { return folders.get(id); }

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
        for (Map.Entry<Integer, HomeFolderView> held : folders.entrySet()) {
            LauncherWidgetRecord record = bound.get(held.getKey());
            if (record != null && record.isFolder()) bindFolder(held.getValue(), record);
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
            List<String> packages = new ArrayList<>();
            AppRef ref = record.appRef();
            if (ref != null) packages.add(ref.packageName);
            for (LauncherWidgetRecord.FolderApp member : record.folderApps()) {
                packages.add(member.app().packageName);
            }
            for (String name : packageNames) if (packages.contains(name)) ours.add(name);
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
        folders.clear();
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
