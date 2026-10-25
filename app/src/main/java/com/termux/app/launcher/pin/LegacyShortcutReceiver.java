package com.termux.app.launcher.pin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.HomeShortcuts;

/**
 * The legacy {@code com.android.launcher.action.INSTALL_SHORTCUT} broadcast: an older app asking
 * for a shortcut with an intent, a name and an icon. It is placed like a pinned one, on the page
 * in front or the first with room or a new page, and without a card (that is how the stock
 * launcher treats it).
 *
 * <p>Only a sender holding {@code com.android.launcher.permission.INSTALL_SHORTCUT} reaches it:
 * the manifest names that permission on the receiver, so the platform refuses everyone else
 * before this runs. This app declares no such permission of its own (every edition would define
 * it, and two editions installed together cannot share a permission name).</p>
 */
public final class LegacyShortcutReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent broadcast) {
        if (broadcast == null
            || !"com.android.launcher.action.INSTALL_SHORTCUT".equals(broadcast.getAction())) {
            return;
        }
        Intent target = broadcast.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        String label = HomePinPolicy.legacyLabel(broadcast.getStringExtra(Intent.EXTRA_SHORTCUT_NAME));
        if (target == null || label.isEmpty()) return;
        String component = target.getComponent() == null ? null
            : target.getComponent().getPackageName();
        if (!HomePinPolicy.legacyIntentAllowed(component, target.getAction() != null,
            context.getPackageName())) {
            return;
        }
        // What is stored is the intent alone: a selector or a clip is the sender's to keep.
        Intent safe = new Intent(target);
        safe.setSelector(null);
        safe.setClipData(null);
        safe.setSourceBounds(null);
        final Bundle options = HomeShortcuts.legacyOptions(label, safe, iconOf(context, broadcast));
        final String owner = component != null ? component : HomeShortcuts.LEGACY_OWNER;
        HomePinHandoff.deliver(home -> home.placeShortcut(owner, 0L, options, new boolean[1]));
    }

    /** The icon the sender gave: a bitmap, or a drawable in the sender's own resources. */
    @Nullable
    private static Bitmap iconOf(@NonNull Context context, @NonNull Intent broadcast) {
        Object icon = broadcast.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        if (icon instanceof Bitmap) return (Bitmap) icon;
        Object resource = broadcast.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
        if (!(resource instanceof Intent.ShortcutIconResource)) return null;
        Intent.ShortcutIconResource named = (Intent.ShortcutIconResource) resource;
        try {
            PackageManager pm = context.getPackageManager();
            Resources theirs = pm.getResourcesForApplication(named.packageName);
            int id = theirs.getIdentifier(named.resourceName, null, null);
            if (id == 0) return null;
            Drawable drawable = theirs.getDrawable(id, null);
            if (drawable == null) return null;
            int width = Math.max(1, drawable.getIntrinsicWidth());
            int height = Math.max(1, drawable.getIntrinsicHeight());
            int longest = Math.max(width, height);
            if (longest > HomeShortcuts.LEGACY_ICON_PX_PUBLIC) {
                float factor = HomeShortcuts.LEGACY_ICON_PX_PUBLIC / (float) longest;
                width = Math.max(1, Math.round(width * factor));
                height = Math.max(1, Math.round(height * factor));
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            return bitmap;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }
}
