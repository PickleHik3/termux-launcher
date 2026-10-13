package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.IconPackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IconPack {
    @NonNull public final IconPackInfo info;
    private final Map<String, String> componentDrawables;
    private final Map<String, String> calendarPrefixes;
    private final Map<String, IconPackDrawableItem> drawableItems;
    @NonNull public final List<String> iconBacks;
    @NonNull public final List<String> iconMasks;
    @NonNull public final List<String> iconUpons;
    public final float scale;
    /**
     * How much this pack's own drawables are enlarged so they read as big as stock icons
     * ({@code LauncherIconResolver}); 0 until measured. It rides on the parsed pack, so a pack that
     * is upgraded, replaced or dropped by a cache clear is measured again with its new parse.
     */
    private volatile float footprintScale;

    IconPack(
        @NonNull IconPackInfo info,
        @NonNull Map<String, String> componentDrawables,
        @NonNull Map<String, String> calendarPrefixes,
        @NonNull Map<String, IconPackDrawableItem> drawableItems,
        @NonNull List<String> iconBacks,
        @NonNull List<String> iconMasks,
        @NonNull List<String> iconUpons,
        float scale
    ) {
        this.info = info;
        this.componentDrawables = Collections.unmodifiableMap(new LinkedHashMap<>(componentDrawables));
        this.calendarPrefixes = Collections.unmodifiableMap(new LinkedHashMap<>(calendarPrefixes));
        this.drawableItems = Collections.unmodifiableMap(new LinkedHashMap<>(drawableItems));
        this.iconBacks = Collections.unmodifiableList(new ArrayList<>(iconBacks));
        this.iconMasks = Collections.unmodifiableList(new ArrayList<>(iconMasks));
        this.iconUpons = Collections.unmodifiableList(new ArrayList<>(iconUpons));
        this.scale = scale <= 0f ? 1f : scale;
    }

    @Nullable
    public String drawableForComponent(@NonNull String packageName, @NonNull String activityName, int dayOfMonth) {
        String component = IconPackXmlParser.componentKey(packageName, activityName);
        String direct = componentDrawables.get(component);
        if (direct != null) return direct;
        String prefix = calendarPrefixes.get(component);
        if (prefix == null) return null;
        return prefix + dayOfMonth;
    }

    @Nullable
    public IconPackDrawableItem drawableItem(@NonNull String drawableName) {
        return drawableItems.get(drawableName.toLowerCase(Locale.US));
    }

    @NonNull
    public List<IconPackDrawableItem> drawableItems() {
        return new ArrayList<>(drawableItems.values());
    }

    @NonNull
    public Map<String, String> componentDrawables() {
        return componentDrawables;
    }

    /** The measured scale for this pack's drawables, or 0 while it has not been measured. */
    float footprintScale() {
        return footprintScale;
    }

    void setFootprintScale(float footprintScale) {
        this.footprintScale = footprintScale;
    }
}
