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
        Drawable icon = load(packageName);
        mCache.put(packageName, icon);
        return icon;
    }

    @Nullable
    private Drawable load(@NonNull String packageName) {
        try {
            Intent launch = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
            ComponentName component = launch == null ? null : launch.getComponent();
            if (component != null) {
                Drawable resolved = mResolver.resolve(new AppRef(packageName, component.getClassName()));
                if (resolved != null) return resolved;
            }
            return mContext.getPackageManager().getApplicationIcon(packageName);
        } catch (Exception ignored) {
            return null;
        }
    }
}
