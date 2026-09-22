package com.termux.app.launcher.drawer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Telephony;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the device's default-role packages into drawer categories. Every resolution is
 * individually guarded; a failing lookup contributes nothing rather than poisoning the map.
 *
 * <p>Each pass is seven synchronous PackageManager round trips, and the drawer used to make it on
 * every classification — two or three times per open, on the frame the pull gesture began. Defaults
 * change rarely, so the answer is held for {@link #CACHE_TTL_MS} and dropped early by
 * {@link #invalidate()} whenever the catalogue itself changes.
 */
public final class AppDrawerSystemRoleResolver {

    static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    @Nullable private static Map<String, AppDrawerCategory> sCached;
    private static long sCachedAtMs;

    private AppDrawerSystemRoleResolver() {}

    @NonNull
    public static synchronized Map<String, AppDrawerCategory> resolve(@NonNull Context context) {
        long now = System.currentTimeMillis();
        if (sCached != null && now - sCachedAtMs >= 0 && now - sCachedAtMs < CACHE_TTL_MS)
            return sCached;
        Map<String, AppDrawerCategory> roles = Collections.unmodifiableMap(resolveUncached(context));
        sCached = roles;
        sCachedAtMs = now;
        return roles;
    }

    /** Forgets the cached defaults; the next {@link #resolve} asks the PackageManager again. */
    public static synchronized void invalidate() {
        sCached = null;
    }

    @NonNull
    static Map<String, AppDrawerCategory> resolveUncached(@NonNull Context context) {
        Map<String, AppDrawerCategory> roles = new HashMap<>();
        PackageManager packageManager;
        try {
            packageManager = context.getPackageManager();
        } catch (Throwable ignored) {
            return roles;
        }
        put(roles, resolveDefault(packageManager,
            new Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))),
            AppDrawerCategory.UTILITIES);
        put(roles, resolveDefault(packageManager, new Intent(Intent.ACTION_DIAL)),
            AppDrawerCategory.SOCIAL);
        put(roles, defaultSmsPackage(context), AppDrawerCategory.SOCIAL);
        put(roles, resolveDefault(packageManager, new Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            AppDrawerCategory.PHOTO_VIDEO);
        Intent gallery = new Intent(Intent.ACTION_VIEW);
        gallery.setType("image/*");
        put(roles, resolveDefault(packageManager, gallery), AppDrawerCategory.PHOTO_VIDEO);
        put(roles, resolveDefault(packageManager,
            new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))), AppDrawerCategory.TRAVEL);
        put(roles, resolveDefault(packageManager,
            new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))),
            AppDrawerCategory.PRODUCTIVITY);
        return roles;
    }

    private static void put(@NonNull Map<String, AppDrawerCategory> roles,
                            @Nullable String packageName, @NonNull AppDrawerCategory category) {
        if (packageName != null) roles.put(packageName.toLowerCase(Locale.US), category);
    }

    @Nullable
    private static String resolveDefault(@NonNull PackageManager packageManager,
                                         @NonNull Intent intent) {
        try {
            ResolveInfo info = packageManager.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
            if (info == null || info.activityInfo == null) return null;
            String packageName = info.activityInfo.packageName;
            // The framework's disambiguation chooser answers when no default is set; it is not an
            // app and must never be classified as one.
            if (packageName == null || "android".equals(packageName)) return null;
            return packageName;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String defaultSmsPackage(@NonNull Context context) {
        try {
            return Telephony.Sms.getDefaultSmsPackage(context);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
