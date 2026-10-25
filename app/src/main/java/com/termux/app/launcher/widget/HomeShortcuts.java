package com.termux.app.launcher.widget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a shortcut on Home looks like and does ({@link LauncherWidgetRecord.Kind#SHORTCUT}).
 *
 * <p>A pinned one is the platform's: the app owns the intent and the artwork and this launcher
 * only holds the id, so a tap is {@code LauncherApps.startShortcut} and the icon is asked for, then
 * kept in a small cache of {@link #CACHE_LIMIT} icons so a redraw does not ask again. When the
 * platform no longer has it (the app was removed, or took the shortcut back) the icon stays,
 * faint, like an app that is gone. A legacy one carries its own intent and a small icon in its
 * record and is launched like any intent a user tapped.</p>
 */
public final class HomeShortcuts {
    private static final String TAG = "HomeShortcuts";

    /** The most icons held; one per shortcut on Home is far fewer in practice. */
    static final int CACHE_LIMIT = 32;
    /** A legacy icon is scaled to fit this many pixels a side before it is stored. */
    static final int LEGACY_ICON_PX = 128;
    /** {@link #LEGACY_ICON_PX} for the receiver that draws a resource icon at that size. */
    public static final int LEGACY_ICON_PX_PUBLIC = LEGACY_ICON_PX;
    /** The package a legacy shortcut is recorded under when its intent names none; never real. */
    public static final String LEGACY_OWNER = "com.termux.launcher.shortcut";
    /** A legacy icon whose base64 is longer than this is not stored: the default icon stands. */
    static final int LEGACY_ICON_MAX_CHARS = 40_000;

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
        if (pinnedId == null) {
            return new Resolved(legacyIcon(context, record), stored, false);
        }
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
        try {
            if (pinnedId != null) {
                LauncherApps apps = context.getSystemService(LauncherApps.class);
                if (apps == null) return;
                apps.startShortcut(record.provider.getPackageName(), pinnedId, null, null,
                    userOf(context, record));
                return;
            }
            String uri = record.sizeOptions().getString(LauncherWidgetRecord.SHORTCUT_INTENT);
            if (uri == null) return;
            Intent intent = Intent.parseUri(uri, 0);
            intent.setSelector(null);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException | URISyntaxException
                 | IllegalStateException e) {
            Log.w(TAG, "shortcut did not start", e);
        }
    }

    /**
     * The options a legacy shortcut record is stored with: its label, the intent as a URI and the
     * icon as a small PNG, or no icon when it would not stay small.
     */
    @NonNull
    public static Bundle legacyOptions(@NonNull String label, @NonNull Intent intent,
                                       @Nullable Bitmap icon) {
        Bundle options = new Bundle();
        options.putString(LauncherWidgetRecord.APP_LABEL, label);
        options.putString(LauncherWidgetRecord.SHORTCUT_INTENT, intent.toUri(0));
        if (icon != null) {
            String encoded = encodeIcon(icon);
            if (encoded != null) options.putString(LauncherWidgetRecord.SHORTCUT_ICON, encoded);
        }
        return options;
    }

    /** {@code icon} scaled down to {@link #LEGACY_ICON_PX} and encoded; null when too large. */
    @Nullable
    static String encodeIcon(@NonNull Bitmap icon) {
        int longest = Math.max(icon.getWidth(), icon.getHeight());
        if (longest <= 0) return null;
        Bitmap scaled = icon;
        if (longest > LEGACY_ICON_PX) {
            float factor = LEGACY_ICON_PX / (float) longest;
            scaled = Bitmap.createScaledBitmap(icon,
                Math.max(1, Math.round(icon.getWidth() * factor)),
                Math.max(1, Math.round(icon.getHeight() * factor)), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
        if (scaled != icon) scaled.recycle();
        String encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        return encoded.length() > LEGACY_ICON_MAX_CHARS ? null : encoded;
    }

    @Nullable
    private static Drawable legacyIcon(@NonNull Context context, @NonNull LauncherWidgetRecord record) {
        String encoded = record.sizeOptions().getString(LauncherWidgetRecord.SHORTCUT_ICON);
        if (encoded == null) return null;
        String key = "legacy:" + record.appWidgetId;
        synchronized (HomeShortcuts.class) {
            Drawable.ConstantState held = CACHE.get(key);
            if (held != null) return held.newDrawable(context.getResources());
        }
        try {
            byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return null;
            BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            remember(key, drawable);
            return drawable;
        } catch (IllegalArgumentException e) {
            return null;
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
