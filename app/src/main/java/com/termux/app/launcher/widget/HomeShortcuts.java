package com.termux.app.launcher.widget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a shortcut on Home looks like and does ({@link LauncherWidgetRecord.Kind#SHORTCUT}).
 *
 * <p>A pinned shortcut is the platform's: the app owns the intent and the artwork and this launcher
 * only holds the id, so a tap is {@code LauncherApps.startShortcut} and the icon is asked for, then
 * kept in a small cache of {@link #CACHE_LIMIT} icons so a redraw does not ask again. When the
 * platform no longer has it (the app was removed, or took the shortcut back) the icon stays,
 * faint, like an app that is gone.</p>
 */
public final class HomeShortcuts {
    private static final String TAG = "HomeShortcuts";

    /** The most icons held; one per shortcut on Home is far fewer in practice. */
    static final int CACHE_LIMIT = 32;

    /** One shortcut as it draws now. */
    public static final class Resolved {
        @Nullable public final Drawable icon;
        @NonNull public final String label;
        public final boolean unavailable;

        Resolved(@Nullable Drawable icon, @NonNull String label, boolean unavailable) {
            this.icon = icon;
            this.label = label;
            this.unavailable = unavailable;
        }
    }

    private static final Map<String, Drawable.ConstantState> CACHE = new HashMap<>();

    private HomeShortcuts() {}

    /** Drops the held icons: the launcher's views are going, or an icon pack changed. */
    public static synchronized void clearCache() {
        CACHE.clear();
    }

    @NonNull
    public static Resolved resolve(@NonNull Context context, @NonNull LauncherWidgetRecord record) {
        String stored = record.appLabel();
        String pinnedId = record.shortcutId();
        if (pinnedId == null) return new Resolved(null, stored, true);
        ShortcutInfo info = pinned(context, record, pinnedId);
        if (info == null || !info.isEnabled()) {
            return new Resolved(cached(context, record, null), stored, true);
        }
        CharSequence shortLabel = info.getShortLabel();
        String label = shortLabel == null || shortLabel.length() == 0 ? stored
            : shortLabel.toString();
        return new Resolved(cached(context, record, info), label, false);
    }

    /** A tap: starts the shortcut. Failure is logged; the icon simply does nothing. */
    public static void launch(@NonNull Context context, @NonNull LauncherWidgetRecord record) {
        String pinnedId = record.shortcutId();
        if (pinnedId == null) return;
        try {
            LauncherApps apps = context.getSystemService(LauncherApps.class);
            if (apps == null) return;
            apps.startShortcut(record.provider.getPackageName(), pinnedId, null, null,
                userOf(context, record));
        } catch (ActivityNotFoundException | SecurityException | IllegalStateException e) {
            Log.w(TAG, "shortcut did not start", e);
        }
    }

    /**
     * Keeps the shortcut behind {@code record} pinned for this launcher, as Launcher3 does for a
     * deep shortcut dragged out of its menu: a dynamic or manifest shortcut the app later drops
     * stays then, as one a pin request made does. {@code LauncherApps.pinShortcuts} replaces the
     * whole set this launcher has pinned for the package, so the set is read first and the id
     * added to it. False when it could not be pinned (no shortcut host permission: this launcher
     * is not the default home); the icon still works while the app offers the shortcut.
     */
    public static boolean keepPinned(@NonNull Context context,
                                     @NonNull LauncherWidgetRecord record) {
        String id = record.shortcutId();
        if (id == null) return false;
        String owner = record.provider.getPackageName();
        try {
            LauncherApps apps = context.getSystemService(LauncherApps.class);
            if (apps == null || !apps.hasShortcutHostPermission()) return false;
            UserHandle user = userOf(context, record);
            LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery()
                .setPackage(owner)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> pinned = apps.getShortcuts(query, user);
            List<String> ids = new java.util.ArrayList<>();
            if (pinned != null) {
                for (ShortcutInfo info : pinned) {
                    if (info.isPinned()) ids.add(info.getId());
                }
            }
            if (ids.contains(id)) return true;
            ids.add(id);
            apps.pinShortcuts(owner, ids, user);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "shortcut was not pinned", e);
            return false;
        }
    }

    @Nullable
    private static Drawable cached(@NonNull Context context, @NonNull LauncherWidgetRecord record,
                                   @Nullable ShortcutInfo info) {
        String key = record.provider.getPackageName() + "/" + record.shortcutId() + "#"
            + record.profileSerial;
        synchronized (HomeShortcuts.class) {
            Drawable.ConstantState held = CACHE.get(key);
            if (held != null) return held.newDrawable(context.getResources());
        }
        if (info == null) return null;
        try {
            LauncherApps apps = context.getSystemService(LauncherApps.class);
            if (apps == null) return null;
            Drawable icon = apps.getShortcutIconDrawable(info,
                context.getResources().getDisplayMetrics().densityDpi);
            if (icon == null) return null;
            remember(key, icon);
            return icon;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static synchronized void remember(@NonNull String key, @NonNull Drawable icon) {
        Drawable.ConstantState state = icon.getConstantState();
        if (state == null) return;
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(key, state);
    }

    @Nullable
    private static ShortcutInfo pinned(@NonNull Context context,
                                       @NonNull LauncherWidgetRecord record, @NonNull String id) {
        try {
            LauncherApps apps = context.getSystemService(LauncherApps.class);
            if (apps == null || !apps.hasShortcutHostPermission()) return null;
            LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery()
                .setPackage(record.provider.getPackageName())
                .setShortcutIds(Collections.singletonList(id))
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            List<ShortcutInfo> found = apps.getShortcuts(query, userOf(context, record));
            return found == null || found.isEmpty() ? null : found.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @NonNull
    private static UserHandle userOf(@NonNull Context context, @NonNull LauncherWidgetRecord record) {
        try {
            UserManager users = context.getSystemService(UserManager.class);
            UserHandle user = users == null ? null
                : users.getUserForSerialNumber(record.profileSerial);
            return user == null ? Process.myUserHandle() : user;
        } catch (RuntimeException e) {
            return Process.myUserHandle();
        }
    }
}
