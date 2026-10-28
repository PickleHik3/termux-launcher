package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.settings.preferences.SharedPreferencesPreview;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.List;

/**
 * Everything the Appearance editor can write, captured raw so it can be put back exactly: the
 * editor's Undo and Discard (back to the state at open) and the Custom values of the session
 * (kept while a Look shows, so the Custom stop brings them back) are both one of these.
 *
 * <p>Raw values and the link shape rather than resolved numbers: a surface that was detached at
 * the same number as Base must come back detached, and a key cap radius that was the theme's
 * default (absent) must come back absent rather than written as today's default. The same fields,
 * folded to one string, are the editor's dirty signature, so Undo, Discard and "is anything
 * unsaved" cannot disagree about what the editor owns (SPEC §4: tint, rim, motion and the depth
 * keys included). The Custom row's Key spacing and App icons are here too. Dock size is the dock
 * height scale, which lives in the layout store per orientation: the layout session's own
 * snapshot ({@code PlaceArrangeSnapshot}, behind the editor's one Undo and dirty state) holds it,
 * so it is deliberately not restored here, where a turn of the phone could write it to the
 * wrong orientation.</p>
 */
final class AppearanceSnapshot {

    private final String mLinks;
    private final int[] mBase;
    private final int[] mRaws;
    private final String mMaterial;
    private final int mIntensity;
    private final String mDockStyle;
    private final boolean mBorder;
    private final int mTerminalRadius;
    private final int mPaneGap;
    private final int mWallpaperTerminalOpacity;
    private final String mTint;
    private final String mRim;
    private final String mMotion;
    private final String mTintHue;
    private final int mVibrancy;
    /** The shared Text size, in percent. */
    private final int mTextSize;
    /** Where Base's Glass slider and each surface's were left; -1 for none stored. */
    private final int[] mGlassPositions;
    private final int mBend;
    private final int mEdgeWidth;
    private final int mEdgeLight;
    private final int mSpecular;
    private final int mDispersion;
    private final int mClarity;
    private final int mKeyboardBlurRaw;
    private final int mKeyOpacity;
    /** Whether the keyboard's key and background opacity move together. */
    private final boolean mOpacityLinked;
    /** The stored radius, or NaN when the key is absent and the style's default applies. */
    private final float mKeyRadiusRaw;
    /** The stored key spacing, or NaN when the key is absent and the Style's default applies. */
    private final float mKeySpacingRaw;
    /** How many app buttons the dock shows. */
    private final int mDockButtonCount;
    private final int mDim;
    private final boolean mSoft;
    private final String mContrast;
    private final String mTrailStyle;
    private final boolean mTrailEnabled;
    private final String mRetroEffect;
    private final String mClockStyle;
    private final String mClockAlignment;
    /**
     * The status bar's switches the Status bar card writes, in {@link #statusSwitches} order:
     * 12-hour, seconds, °F weather, and the CPU, RAM, weather, media and notifications indicators.
     */
    private final boolean[] mStatusSwitches;
    /**
     * Every half's own key as stored (ADR 0013): a key absent here is absent in the store, so a
     * half that followed the shared value at open follows it again after a restore.
     */
    private final java.util.TreeMap<String, Object> mHalfKeys;

    private AppearanceSnapshot(@NonNull TermuxAppSharedPreferences prefs) {
        mLinks = linkSignature(prefs);
        SurfaceProperty[] properties = SurfaceProperty.values();
        mBase = new int[properties.length];
        for (SurfaceProperty property : properties)
            mBase[property.ordinal()] = prefs.getSurfaceBaseValue(property);
        List<SurfaceEditorRows.Row> rows = SurfaceEditorRows.rows();
        mRaws = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            // The keyboard's blur is the one cell whose getter resolves an absent key to the
            // shipped default while its setter stores the raw -1: read it raw here, so a value
            // that was "follow" at open still reads "follow" after restore.
            mRaws[i] = isKeyboardBlur(rows.get(i)) ? prefs.getInAppKeyboardBlurRadiusRaw()
                : prefs.getSurfaceOverrideValue(rows.get(i).slot, rows.get(i).property);
        }
        mMaterial = prefs.getSurfaceMaterial(TermuxAppSharedPreferences.terminalPageId());
        mIntensity = prefs.getSurfaceMaterialIntensity(TermuxAppSharedPreferences.terminalPageId());
        mDockStyle = prefs.getAppLauncherDockStyle();
        mBorder = prefs.isTerminalBorderEnabled();
        mTerminalRadius = prefs.getTerminalCornerRadius();
        mPaneGap = prefs.getTerminalPaneGap();
        mWallpaperTerminalOpacity = prefs.getWallpaperEnabledTerminalBackgroundOpacity();
        mTint = prefs.getSurfaceGlassTint();
        mRim = prefs.getSurfaceGlassRim();
        mMotion = prefs.getSurfaceGlassMotion();
        mTintHue = prefs.getSurfaceGlassTintHue();
        mVibrancy = prefs.getSurfaceGlassVibrancy();
        mTextSize = prefs.getTextSizePercent();
        SurfaceSlot[] slots = SurfaceSlot.values();
        mGlassPositions = new int[slots.length + 1];
        mGlassPositions[0] = prefs.getSurfaceGlassPosition(null);
        for (SurfaceSlot slot : slots)
            mGlassPositions[slot.ordinal() + 1] = prefs.getSurfaceGlassPosition(slot);
        mBend = prefs.getFancierGlassBendDp();
        mEdgeWidth = prefs.getFancierGlassEdgeWidthDp();
        mEdgeLight = prefs.getFancierGlassEdgeLightPercent();
        mSpecular = prefs.getFancierGlassSpecularPercent();
        mDispersion = prefs.getFancierGlassDispersionPercent();
        mClarity = prefs.getFancierGlassClarityPercent();
        mKeyboardBlurRaw = prefs.getInAppKeyboardBlurRadiusRaw();
        mKeyOpacity = prefs.getInAppKeyboardKeyOpacity();
        mOpacityLinked = prefs.isInAppKeyboardOpacityLinked();
        mKeyRadiusRaw = rawKeyRadius(prefs);
        mKeySpacingRaw = rawFloat(prefs, TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE);
        mDockButtonCount = prefs.getAppLauncherButtonCount();
        mDim = prefs.getWallpaperBackdropDim();
        mSoft = SoftWallpaper.isOn(prefs);
        mContrast = prefs.getTerminalContrastLevel().value;
        mTrailStyle = prefs.getTerminalCursorTrailStyle();
        mTrailEnabled = prefs.isTerminalCursorTrailEnabled();
        mRetroEffect = prefs.getTerminalRetroEffect();
        mClockStyle = prefs.getTopPaneClockStyle();
        mClockAlignment = prefs.getTopPaneClockAlignment();
        mStatusSwitches = statusSwitches(prefs);
        mHalfKeys = halfKeys(prefs);
    }

    /** The status bar's switches, as {@link #mStatusSwitches} keeps them. */
    @NonNull
    private static boolean[] statusSwitches(@NonNull TermuxAppSharedPreferences prefs) {
        return new boolean[] {prefs.isTopPaneClockAmPmEnabled(), prefs.isStatusClockSecondsEnabled(),
            prefs.isStatusWidgetWeatherFahrenheit(), prefs.isStatusWidgetCpuEnabled(),
            prefs.isStatusWidgetRamEnabled(), prefs.isStatusWidgetWeatherEnabled(),
            prefs.isStatusWidgetMediaEnabled(), prefs.isStatusWidgetNotificationsEnabled()};
    }

    /** Writes back the status bar's switches that differ from the store's now. */
    private static void restoreStatusSwitches(@NonNull TermuxAppSharedPreferences prefs,
                                              @NonNull boolean[] kept) {
        boolean[] now = statusSwitches(prefs);
        if (now[0] != kept[0]) prefs.setTopPaneClockAmPmEnabled(kept[0]);
        if (now[1] != kept[1]) prefs.setStatusClockSecondsEnabled(kept[1]);
        if (now[2] != kept[2]) prefs.setStatusWidgetWeatherFahrenheit(kept[2]);
        if (now[3] != kept[3]) prefs.setStatusWidgetCpuEnabled(kept[3]);
        if (now[4] != kept[4]) prefs.setStatusWidgetRamEnabled(kept[4]);
        if (now[5] != kept[5]) prefs.setStatusWidgetWeatherEnabled(kept[5]);
        if (now[6] != kept[6]) prefs.setStatusWidgetMediaEnabled(kept[6]);
        if (now[7] != kept[7]) prefs.setStatusWidgetNotificationsEnabled(kept[7]);
    }

    private static boolean isKeyboardBlur(@NonNull SurfaceEditorRows.Row row) {
        return row.slot == SurfaceSlot.KEYBOARD && row.property == SurfaceProperty.BLUR;
    }

    @NonNull
    static AppearanceSnapshot capture(@NonNull TermuxAppSharedPreferences prefs) {
        return new AppearanceSnapshot(prefs);
    }

    /** Writes every captured value back. The caller re-renders. */
    void restore(@NonNull TermuxAppSharedPreferences prefs) {
        for (SurfaceProperty property : SurfaceProperty.values())
            prefs.setSurfaceBaseValue(property, mBase[property.ordinal()]);
        List<SurfaceEditorRows.Row> rows = SurfaceEditorRows.rows();
        for (int i = 0; i < rows.size() && i < mRaws.length; i++) {
            if (isKeyboardBlur(rows.get(i))) continue; // mKeyboardBlurRaw restores it below
            prefs.setSurfaceRawValue(rows.get(i).slot, rows.get(i).property, mRaws[i]);
        }
        restoreLinks(prefs, mLinks);
        prefs.setSurfaceMaterial(mMaterial);
        prefs.setSurfaceMaterialIntensity(mIntensity);
        prefs.setAppLauncherDockStyle(mDockStyle);
        prefs.setTerminalBorderEnabled(mBorder);
        prefs.setTerminalCornerRadius(mTerminalRadius);
        prefs.setTerminalPaneGap(mPaneGap);
        prefs.setWallpaperEnabledTerminalBackgroundOpacity(mWallpaperTerminalOpacity);
        prefs.setSurfaceGlassTint(mTint);
        prefs.setSurfaceGlassRim(mRim);
        prefs.setSurfaceGlassMotion(mMotion);
        prefs.setSurfaceGlassTintHue(mTintHue);
        prefs.setSurfaceGlassVibrancy(mVibrancy);
        prefs.setTextSizePercent(mTextSize);
        prefs.setSurfaceGlassPosition(null, mGlassPositions[0]);
        for (SurfaceSlot slot : SurfaceSlot.values())
            prefs.setSurfaceGlassPosition(slot, mGlassPositions[slot.ordinal() + 1]);
        prefs.setFancierGlassBendDp(mBend);
        prefs.setFancierGlassEdgeWidthDp(mEdgeWidth);
        prefs.setFancierGlassEdgeLightPercent(mEdgeLight);
        prefs.setFancierGlassSpecularPercent(mSpecular);
        prefs.setFancierGlassDispersionPercent(mDispersion);
        prefs.setFancierGlassClarityPercent(mClarity);
        prefs.setInAppKeyboardBlurRadiusRaw(mKeyboardBlurRaw);
        prefs.setInAppKeyboardKeyOpacity(mKeyOpacity);
        prefs.setInAppKeyboardOpacityLinked(mOpacityLinked);
        restoreKeyRadius(prefs, mKeyRadiusRaw);
        restoreFloat(prefs, TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE, mKeySpacingRaw);
        prefs.setAppLauncherButtonCount(mDockButtonCount);
        prefs.setWallpaperBackdropDim(mDim);
        SoftWallpaper.set(prefs, mSoft);
        prefs.setTerminalContrastLevel(mContrast);
        prefs.setTerminalCursorTrailStyle(mTrailStyle);
        prefs.setTerminalCursorTrailEnabled(mTrailEnabled);
        prefs.setTerminalRetroEffect(mRetroEffect);
        prefs.setTopPaneClockStyle(mClockStyle);
        prefs.setTopPaneClockAlignment(mClockAlignment);
        restoreStatusSwitches(prefs, mStatusSwitches);
        // Last: every shared write above dropped the halves' own keys.
        restoreHalfKeys(prefs, mHalfKeys);
    }

    /** The captured state as one string; equal strings mean nothing to save or put back. */
    @NonNull
    String signature() {
        StringBuilder out = new StringBuilder(256).append(mLinks).append('|');
        for (int value : mBase) out.append(value).append(',');
        out.append('|');
        for (int value : mRaws) out.append(value).append(',');
        return out.append('|').append(mMaterial).append('|').append(mIntensity)
            .append('|').append(mDockStyle).append('|').append(mBorder)
            .append('|').append(mTerminalRadius).append('|').append(mPaneGap)
            .append('|').append(mWallpaperTerminalOpacity)
            .append('|').append(mTint).append('|').append(mRim).append('|').append(mMotion)
            .append('|').append(mTintHue).append('|').append(mVibrancy)
            .append('|').append(mTextSize)
            .append('|').append(java.util.Arrays.toString(mGlassPositions))
            .append('|').append(mBend).append('|').append(mEdgeWidth).append('|').append(mEdgeLight)
            .append('|').append(mSpecular).append('|').append(mDispersion)
            .append('|').append(mClarity)
            .append('|').append(mKeyboardBlurRaw).append('|').append(mKeyOpacity)
            .append('|').append(mOpacityLinked)
            .append('|').append(mKeyRadiusRaw).append('|').append(mKeySpacingRaw)
            .append('|').append(mDockButtonCount).append('|').append(mDim).append('|').append(mSoft)
            .append('|').append(mContrast).append('|').append(mTrailStyle).append('|').append(mTrailEnabled)
            .append('|').append(mRetroEffect).append('|').append(mClockStyle)
            .append('|').append(mClockAlignment)
            .append('|').append(java.util.Arrays.toString(mStatusSwitches))
            .append('|').append(mHalfKeys)
            .toString();
    }

    /** The live state's signature, for the dirty test, without holding a snapshot of it. */
    @NonNull
    static String signatureOf(@NonNull TermuxAppSharedPreferences prefs) {
        return capture(prefs).signature();
    }

    // ------------------------------------------------------------------------------ helpers

    /**
     * The shared keys a half may hold its own copy of: the per-half Base numbers, every per-half
     * surface cell and its link, the material point, and the keyboard's backdrop fade.
     */
    @NonNull
    static List<String> halfAbleKeys() {
        List<String> keys = new java.util.ArrayList<>();
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (TermuxAppSharedPreferences.isPerPageProperty(property))
                keys.add(property.baseKey);
        }
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (!TermuxAppSharedPreferences.isPerPage(slot, property))
                    continue;
                String own = TermuxAppSharedPreferences.surfaceOverrideKey(slot, property);
                if (own != null) keys.add(own);
                keys.add(TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX + slot.key + "_" + property.key);
            }
        }
        keys.add(TERMUX_APP.KEY_SURFACE_MATERIAL);
        keys.add(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY);
        keys.add(TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY);
        return keys;
    }

    /** Each half's own key that the store holds, by name, with its value. */
    @NonNull
    private static java.util.TreeMap<String, Object> halfKeys(
            @NonNull TermuxAppSharedPreferences prefs) {
        java.util.TreeMap<String, Object> out = new java.util.TreeMap<>();
        SharedPreferences store = SharedPreferencesPreview.readsFor(prefs.getSharedPreferences());
        if (store == null)
            return out;
        java.util.Map<String, ?> all = store.getAll();
        for (String key : halfAbleKeys()) {
            {
                int pageId = TermuxAppSharedPreferences.terminalPageId();
                String halfKey = TermuxAppSharedPreferences.surfacePageKey(key, pageId);
                // Only the Terminal page's own values are half keys now; the wall's are shared.
                if (halfKey.equals(key))
                    continue;
                Object value = all.get(halfKey);
                if (value != null)
                    out.put(halfKey, value);
            }
        }
        return out;
    }

    /**
     * Puts one half's Base back as captured for {@code properties}: the half's own value written
     * as it was, or dropped where the half followed the shared value then. Home's Reset uses it
     * to put Home's glass back on the Look it wore when the editor opened.
     */
    void restoreHalfBase(@NonNull TermuxAppSharedPreferences prefs,
                         int pageId,
                         @NonNull SurfaceProperty... properties) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null)
            return;
        SharedPreferences.Editor editor = store.edit();
        for (SurfaceProperty property : properties) {
            if (!TermuxAppSharedPreferences.isPerPageProperty(property))
                continue;
            String halfKey = TermuxAppSharedPreferences.surfacePageKey(property.baseKey, pageId);
            if (halfKey.equals(property.baseKey))
                continue; // Base is the wall's: the shared restore puts it back
            Object value = mHalfKeys.get(halfKey);
            if (value instanceof Integer) editor.putInt(halfKey, (Integer) value);
            else if (store.contains(halfKey)) editor.remove(halfKey);
        }
        editor.apply();
    }

    /** Puts every half's own key back as it was, and drops the ones that were absent. */
    private static void restoreHalfKeys(@NonNull TermuxAppSharedPreferences prefs,
                                        @NonNull java.util.Map<String, Object> halfKeys) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null)
            return;
        SharedPreferences.Editor editor = store.edit();
        for (String key : halfAbleKeys()) {
            {
                int pageId = TermuxAppSharedPreferences.terminalPageId();
                String halfKey = TermuxAppSharedPreferences.surfacePageKey(key, pageId);
                if (halfKey.equals(key))
                    continue;
                Object value = halfKeys.get(halfKey);
                if (value instanceof Integer) editor.putInt(halfKey, (Integer) value);
                else if (value instanceof Boolean) editor.putBoolean(halfKey, (Boolean) value);
                else if (value instanceof String) editor.putString(halfKey, (String) value);
                else if (value instanceof Float) editor.putFloat(halfKey, (Float) value);
                else if (value instanceof Long) editor.putLong(halfKey, (Long) value);
                else if (store.contains(halfKey)) editor.remove(halfKey);
            }
        }
        editor.apply();
    }

    @NonNull
    private static String linkSignature(@NonNull TermuxAppSharedPreferences prefs) {
        StringBuilder out = new StringBuilder(32);
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values())
                out.append(prefs.isSurfaceInheriting(slot, property) ? '1' : '0');
        }
        return out.toString();
    }

    private static void restoreLinks(@NonNull TermuxAppSharedPreferences prefs,
                                     @NonNull String signature) {
        int index = 0;
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (index >= signature.length())
                    return;
                prefs.setSurfaceInheriting(slot, property, signature.charAt(index++) == '1');
            }
        }
    }

    private static float rawKeyRadius(@NonNull TermuxAppSharedPreferences prefs) {
        return rawFloat(prefs, TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP);
    }

    /** The stored float, or NaN when the key is absent (or not a float). */
    private static float rawFloat(@NonNull TermuxAppSharedPreferences prefs, @NonNull String key) {
        // What the getters read: a Look previewed over the store, while one is.
        SharedPreferences store = SharedPreferencesPreview.readsFor(prefs.getSharedPreferences());
        if (store == null || !store.contains(key))
            return Float.NaN;
        try {
            return store.getFloat(key, Float.NaN);
        } catch (ClassCastException e) {
            return Float.NaN;
        }
    }

    private static void restoreKeyRadius(@NonNull TermuxAppSharedPreferences prefs, float raw) {
        restoreFloat(prefs, TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP, raw);
    }

    /** Puts a float key back: written as it was, or removed when it was absent. */
    private static void restoreFloat(@NonNull TermuxAppSharedPreferences prefs,
                                     @NonNull String key, float raw) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null)
            return;
        if (Float.isNaN(raw))
            store.edit().remove(key).apply();
        else
            store.edit().putFloat(key, raw).apply();
    }

    @NonNull
    @Override
    public String toString() {
        return signature();
    }
}
