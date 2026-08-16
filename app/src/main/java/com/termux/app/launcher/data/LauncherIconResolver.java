package com.termux.app.launcher.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedIconOverride;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Calendar;

public final class LauncherIconResolver {
    private static final int COMPOSE_SIZE_PX = 192;

    // Guarded by the synchronized composeUnmappedIcon() method; result bitmaps remain per call.
    private final Paint composePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect composeFullBounds = new Rect(0, 0, COMPOSE_SIZE_PX, COMPOSE_SIZE_PX);
    private final Rect composeTargetBounds = new Rect();
    private final Rect composeOldDrawableBounds = new Rect();

    public static final class ResolvedIcon {
        @Nullable public final Drawable drawable;
        public final boolean iconPackArtwork;

        ResolvedIcon(@Nullable Drawable drawable, boolean iconPackArtwork) {
            this.drawable = drawable;
            this.iconPackArtwork = iconPackArtwork;
        }
    }
    public interface SystemIconLoader {
        @Nullable Drawable load(@NonNull AppRef ref);
    }

    private final Context context;
    private final PackageManager packageManager;
    private final IconPackRepository iconPackRepository;
    @Nullable private final LauncherConfigRepository configRepository;
    @Nullable private final TermuxAppSharedPreferences preferences;
    @Nullable private final SystemIconLoader systemIconLoader;

    public LauncherIconResolver(@NonNull Context context) {
        this(context, new IconPackRepository(context), TermuxAppSharedPreferences.build(context, false),
            null, LauncherConfigRepository.getInstance(context));
    }

    public LauncherIconResolver(
        @NonNull Context context,
        @NonNull IconPackRepository iconPackRepository,
        @Nullable TermuxAppSharedPreferences preferences,
        @Nullable SystemIconLoader systemIconLoader
    ) {
        this(context, iconPackRepository, preferences, systemIconLoader,
            preferences == null ? null : new LauncherConfigRepository(preferences));
    }

    private LauncherIconResolver(
        @NonNull Context context,
        @NonNull IconPackRepository iconPackRepository,
        @Nullable TermuxAppSharedPreferences preferences,
        @Nullable SystemIconLoader systemIconLoader,
        @Nullable LauncherConfigRepository configRepository
    ) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
        this.iconPackRepository = iconPackRepository;
        this.preferences = preferences;
        this.systemIconLoader = systemIconLoader;
        this.configRepository = configRepository;
    }

    @Nullable
    public Drawable resolve(@NonNull AppRef ref) {
        return resolve(ref, null);
    }

    @Nullable
    public Drawable resolve(@NonNull AppRef ref, @Nullable PinnedIconOverride override) {
        return resolve(ref, override, null);
    }

    /** Resolves app-wide theming while retaining a profile-specific system icon as the fallback. */
    @Nullable
    public Drawable resolve(
        @NonNull AppRef ref,
        @Nullable PinnedIconOverride override,
        @Nullable Drawable systemFallback
    ) {
        return resolveDetailed(ref, override, systemFallback).drawable;
    }

    @NonNull
    public ResolvedIcon resolveDetailed(
        @NonNull AppRef ref,
        @Nullable PinnedIconOverride override,
        @Nullable Drawable systemFallback
    ) {
        Drawable icon = loadOverride(override);
        if (icon != null) return new ResolvedIcon(icon, true);

        if (configRepository != null) {
            icon = loadOverride(configRepository.loadAppIconOverride(ref));
            if (icon != null) return new ResolvedIcon(icon, true);
        }

        if (preferences != null) {
            icon = loadFromPack(preferences.getAppLauncherIconPackPackage(), ref, systemFallback);
            if (icon != null) return new ResolvedIcon(icon, true);
        }

        return new ResolvedIcon(systemFallback != null ? systemFallback : loadSystemIcon(ref), false);
    }

    @Nullable
    public Drawable resolvePinned(@NonNull AppRef ref, @Nullable PinnedIconOverride override) {
        return resolvePinned(ref, override, resolve(ref, null));
    }

    /**
     * Applies pinned-only choices over the already-resolved global icon. Keeping that baseline
     * explicit prevents pinned pages from drifting back to a system icon when no pinned pack is
     * selected.
     */
    @Nullable
    public Drawable resolvePinned(
        @NonNull AppRef ref,
        @Nullable PinnedIconOverride override,
        @Nullable Drawable globalIcon
    ) {
        Drawable icon = loadOverride(override);
        if (icon != null) return icon;

        if (preferences != null) {
            String pinnedPack = preferences.getAppLauncherPinnedIconPackPackage();
            if (pinnedPack != null && !pinnedPack.trim().isEmpty()) {
                icon = loadFromPack(pinnedPack, ref);
                if (icon != null) return icon;
            }
        }

        return globalIcon != null ? globalIcon : resolve(ref, null);
    }

    @NonNull
    public ResolvedIcon resolvePinnedDetailed(
        @NonNull AppRef ref,
        @Nullable PinnedIconOverride override,
        @Nullable Drawable globalIcon,
        boolean globalIconPackArtwork
    ) {
        Drawable icon = loadOverride(override);
        if (icon != null) return new ResolvedIcon(icon, true);
        if (preferences != null) {
            String pinnedPack = preferences.getAppLauncherPinnedIconPackPackage();
            if (pinnedPack != null && !pinnedPack.trim().isEmpty()) {
                icon = loadFromPack(pinnedPack, ref, globalIcon);
                if (icon != null) return new ResolvedIcon(icon, true);
            }
        }
        if (globalIcon != null) return new ResolvedIcon(globalIcon, globalIconPackArtwork);
        return resolveDetailed(ref, null, null);
    }

    public void clearCache() {
        iconPackRepository.clearCache();
    }

    @Nullable
    public Drawable loadOverride(@Nullable PinnedIconOverride override) {
        if (override == null || !override.isValid()) return null;
        return loadDrawableFromPack(override.iconPackPackage, override.drawableName);
    }

    @Nullable
    public Drawable loadDrawableFromPack(@Nullable String packageName, @Nullable String drawableName) {
        if (packageName == null || drawableName == null) return null;
        try {
            Context iconPackContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
            int id = iconPackContext.getResources().getIdentifier(drawableName, "drawable", packageName);
            if (id == 0) id = iconPackContext.getResources().getIdentifier(drawableName, "mipmap", packageName);
            if (id == 0) return null;
            return iconPackContext.getResources().getDrawable(id);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public Drawable loadFromPack(@Nullable String packageName, @NonNull AppRef ref) {
        return loadFromPack(packageName, ref, null);
    }

    @Nullable
    private Drawable loadFromPack(
        @Nullable String packageName,
        @NonNull AppRef ref,
        @Nullable Drawable systemFallback
    ) {
        IconPack pack = iconPackRepository.loadIconPack(packageName);
        if (pack == null) return null;
        String drawableName = pack.drawableForComponent(ref.packageName, ref.activityName, Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        if (drawableName != null) {
            Drawable mapped = loadDrawableFromPack(pack.info.packageName, drawableName);
            if (mapped != null) return mapped;
        }
        return composeUnmappedIcon(pack, ref, systemFallback);
    }

    @Nullable
    private synchronized Drawable composeUnmappedIcon(
        @NonNull IconPack pack,
        @NonNull AppRef ref,
        @Nullable Drawable systemFallback
    ) {
        if (pack.iconBacks.isEmpty() && pack.iconUpons.isEmpty() && pack.iconMasks.isEmpty()) return null;
        Drawable systemIcon = systemFallback != null ? systemFallback : loadSystemIcon(ref);
        if (systemIcon == null) return null;
        Drawable back = loadSelectedPackLayer(pack.info.packageName, pack.iconBacks, ref, 0x19);
        Drawable mask = loadSelectedPackLayer(pack.info.packageName, pack.iconMasks, ref, 0x2f);
        Drawable upon = loadSelectedPackLayer(pack.info.packageName, pack.iconUpons, ref, 0x43);
        if (back == null && mask == null && upon == null) return null;

        Bitmap result = Bitmap.createBitmap(COMPOSE_SIZE_PX, COMPOSE_SIZE_PX, Bitmap.Config.ARGB_8888);
        Canvas resultCanvas = new Canvas(result);
        drawLayer(resultCanvas, back, composeFullBounds);

        Bitmap foreground = Bitmap.createBitmap(COMPOSE_SIZE_PX, COMPOSE_SIZE_PX, Bitmap.Config.ARGB_8888);
        Canvas foregroundCanvas = new Canvas(foreground);
        float safeScale = Math.max(0.1f, Math.min(1.5f, pack.scale));
        int targetSize = Math.max(1, Math.round(COMPOSE_SIZE_PX * safeScale));
        int offset = (COMPOSE_SIZE_PX - targetSize) / 2;
        composeTargetBounds.set(offset, offset, offset + targetSize, offset + targetSize);
        drawLayer(foregroundCanvas, systemIcon, composeTargetBounds);
        if (mask != null) {
            Bitmap maskBitmap = Bitmap.createBitmap(COMPOSE_SIZE_PX, COMPOSE_SIZE_PX, Bitmap.Config.ARGB_8888);
            drawLayer(new Canvas(maskBitmap), mask, composeFullBounds);
            composePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            foregroundCanvas.drawBitmap(maskBitmap, 0f, 0f, composePaint);
            composePaint.setXfermode(null);
            maskBitmap.recycle();
        }
        resultCanvas.drawBitmap(foreground, 0f, 0f, composePaint);
        foreground.recycle();
        drawLayer(resultCanvas, upon, composeFullBounds);
        return new BitmapDrawable(context.getResources(), result);
    }

    @Nullable
    private Drawable loadSelectedPackLayer(
        @NonNull String packageName,
        @NonNull java.util.List<String> names,
        @NonNull AppRef ref,
        int salt
    ) {
        if (names.isEmpty()) return null;
        int index = Math.floorMod(ref.stableId().hashCode() ^ salt, names.size());
        return loadDrawableFromPack(packageName, names.get(index));
    }

    private void drawLayer(
        @NonNull Canvas canvas,
        @Nullable Drawable drawable,
        @NonNull Rect bounds
    ) {
        if (drawable == null) return;
        composeOldDrawableBounds.set(drawable.getBounds());
        drawable.setBounds(bounds);
        drawable.draw(canvas);
        drawable.setBounds(composeOldDrawableBounds);
    }

    @Nullable
    private Drawable loadSystemIcon(@NonNull AppRef ref) {
        if (systemIconLoader != null) {
            return systemIconLoader.load(ref);
        }
        try {
            ComponentName componentName = new ComponentName(ref.packageName, IconPackXmlParser.normalizeActivityName(ref.packageName, ref.activityName));
            return packageManager.getActivityIcon(componentName);
        } catch (Exception ignored) {
            try {
                return packageManager.getApplicationIcon(ref.packageName);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }
}
