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

import com.termux.app.launcher.icon.ArtworkFootprint;
import com.termux.app.launcher.icon.DrawablePixels;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedIconOverride;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;

public final class LauncherIconResolver {
    private static final int COMPOSE_SIZE_PX = 192;
    /**
     * How many of a pack's mapped drawables its scale is measured over: enough that a few odd
     * glyphs do not move the median, few enough to measure in tens of milliseconds.
     */
    private static final int PACK_SAMPLE_SIZE = 16;

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
        this(context, IconPackRepository.getInstance(context), TermuxAppSharedPreferences.build(context, false),
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
        Drawable icon = overrideArtwork(override);
        if (icon != null) return new ResolvedIcon(icon, true);

        if (configRepository != null) {
            icon = overrideArtwork(configRepository.loadAppIconOverride(ref));
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
        Drawable icon = overrideArtwork(override);
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
        Drawable icon = overrideArtwork(override);
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

    /** Clears the icon-pack repository, which every surface shares: parsed packs and resources. */
    public void clearCache() {
        iconPackRepository.clearCache();
    }

    /**
     * Parses {@code packageName} now if it is not parsed yet, so the first icon drawn from it
     * finds it cached. Slow: call it off the main thread.
     */
    public void warmPack(@Nullable String packageName) {
        IconPack pack = iconPackRepository.loadIconPack(packageName);
        if (pack != null) packScale(pack);
    }

    /**
     * The drawable an override names, as the pack ships it, which is all a check that the
     * override still exists needs. Resolving one for an icon goes through
     * {@link #overrideArtwork}, which draws it at its pack's scale.
     */
    @Nullable
    public Drawable loadOverride(@Nullable PinnedIconOverride override) {
        if (override == null || !override.isValid()) return null;
        return loadDrawableFromPack(override.iconPackPackage, override.drawableName);
    }

    @Nullable
    public Drawable loadDrawableFromPack(@Nullable String packageName, @Nullable String drawableName) {
        if (packageName == null || drawableName == null) return null;
        try {
            // The pack's resources are loaded once per installed APK, not once per drawable.
            android.content.res.Resources resources = iconPackRepository.packResources(packageName);
            if (resources == null) return null;
            int id = resources.getIdentifier(drawableName, "drawable", packageName);
            if (id == 0) id = resources.getIdentifier(drawableName, "mipmap", packageName);
            if (id == 0) return null;
            return resources.getDrawable(id);
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
            Drawable mapped = packArtwork(pack,
                loadDrawableFromPack(pack.info.packageName, drawableName));
            if (mapped != null) return mapped;
        }
        // A composed icon is the system icon drawn at the pack's own scale on its own back plate,
        // which is the size the pack asked for; it is not rescaled.
        return composeUnmappedIcon(pack, ref, systemFallback);
    }

    /** An override's drawable, drawn at the scale of the pack it was picked from. */
    @Nullable
    private Drawable overrideArtwork(@Nullable PinnedIconOverride override) {
        Drawable drawable = loadOverride(override);
        if (drawable == null) return null;
        return packArtwork(iconPackRepository.loadIconPack(override.iconPackPackage), drawable);
    }

    /**
     * One of a pack's own drawables, drawn at the pack's scale ({@link #packScale}) about the
     * centre of its box. Every surface draws every source into the same box, and pack PNGs
     * usually carry a transparent margin, so a pack icon came out smaller than the stock one
     * beside it at every size. One scale for the whole pack keeps the sizes and the placement its
     * designer gave its glyphs relative to one another. Done here, when the icon is resolved, so
     * every surface showing it benefits and no frame pays for it.
     */
    @Nullable
    private Drawable packArtwork(@Nullable IconPack pack, @Nullable Drawable drawable) {
        if (drawable == null || pack == null) return drawable;
        return DrawablePixels.scaleAboutCentre(context.getResources(), drawable, packScale(pack));
    }

    /**
     * How much {@code pack}'s drawables are enlarged: the median visible extent over a sample of
     * its mapped icons, taken to a stock icon's ({@link ArtworkFootprint#packScale}). Measured once
     * per parsed pack and kept on it, so an upgraded or reloaded pack is measured afresh. The
     * measure loads and draws up to {@link #PACK_SAMPLE_SIZE} of the pack's drawables, on whichever
     * thread first resolves an icon from the pack: the catalogue's worker for the global pack, the
     * Icons mode's warm-up for a newly chosen one ({@link #warmPack}). A pinned-only pack, or an
     * override's pack, that nothing warmed is measured on the main thread the first time the dock
     * binds it, where the pack is already parsed today.
     */
    private float packScale(@NonNull IconPack pack) {
        float known = pack.footprintScale();
        if (known > 0f) return known;
        List<String> names = new ArrayList<>(new LinkedHashSet<>(pack.componentDrawables().values()));
        int[] sample = ArtworkFootprint.sampleIndices(names.size(), PACK_SAMPLE_SIZE);
        float[] extents = new float[sample.length];
        int measured = 0;
        for (int index : sample) {
            Drawable drawable = loadDrawableFromPack(pack.info.packageName, names.get(index));
            if (drawable == null) continue;
            float extent = DrawablePixels.visibleExtent(drawable);
            if (extent > 0f) extents[measured++] = extent;
        }
        float scale = ArtworkFootprint.packScale(DrawablePixels.stockIconExtent(),
            ArtworkFootprint.median(extents, measured));
        pack.setFootprintScale(scale);
        return scale;
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
        @NonNull List<String> names,
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
