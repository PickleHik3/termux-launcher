package com.termux.app.statusbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.data.LauncherIconResolver;
import com.termux.app.launcher.model.AppRef;

import java.util.HashMap;
import java.util.Map;

/**
 * App icons for pinned notification rows, resolved through the launcher's icon pipeline so a themed
 * icon pack applies here too. Falls back to the raw application icon when the package has no
 * launchable activity.
 */
final class PinnedNotificationIconCache {

    private final Context mContext;
    private final LauncherIconResolver mResolver;
    private final Map<String, Drawable> mCache = new HashMap<>();

    PinnedNotificationIconCache(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mResolver = new LauncherIconResolver(mContext);
    }

    @Nullable
    Drawable get(@Nullable String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        if (mCache.containsKey(packageName)) return mCache.get(packageName);
        LauncherIconResolver.ResolvedIcon icon = load(packageName);
        // A stand-in, drawn while the icon pack is read, is asked again next time.
        if (!icon.provisional) mCache.put(packageName, icon.drawable);
        return icon.drawable;
    }

    @NonNull
    private LauncherIconResolver.ResolvedIcon load(@NonNull String packageName) {
        boolean provisional = false;
        try {
            Intent launch = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
            ComponentName component = launch == null ? null : launch.getComponent();
            if (component != null) {
                LauncherIconResolver.ResolvedIcon resolved = mResolver.resolveDetailed(
                    new AppRef(packageName, component.getClassName()), null, null);
                if (resolved.drawable != null) return resolved;
                provisional = resolved.provisional;
            }
            return new LauncherIconResolver.ResolvedIcon(
                mContext.getPackageManager().getApplicationIcon(packageName), false, provisional);
        } catch (Exception ignored) {
            return new LauncherIconResolver.ResolvedIcon(null, false, provisional);
        }
    }
}
