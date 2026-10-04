package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.List;

/**
 * Everything the Appearance editor can write, captured raw so it can be put back exactly: the
 * editor's Undo and Discard (back to the state at open) and the "Custom look replaced" notice's
 * Undo (back to the Custom values a Look just replaced) are both one of these.
 *
 * <p>Raw values and the link shape rather than resolved numbers: a surface that was detached at
 * the same number as Base must come back detached, and a key cap radius that was the theme's
 * default (absent) must come back absent rather than written as today's default. The same fields,
 * folded to one string, are the editor's dirty signature, so Undo, Discard and "is anything
 * unsaved" cannot disagree about what the editor owns (SPEC §4: tint, rim, motion and the depth
 * keys included).</p>
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
    private final int mBend;
    private final int mEdgeWidth;
    private final int mEdgeLight;
    private final int mSpecular;
    private final int mDispersion;
    private final int mKeyboardBlurRaw;
    private final int mKeyOpacity;
    /** The stored radius, or NaN when the key is absent and the style's default applies. */
    private final float mKeyRadiusRaw;
    private final int mDim;
    private final boolean mSoft;
    private final String mContrast;
    private final String mTrailStyle;
    private final String mRetroEffect;
    private final String mClockStyle;
    private final String mClockAlignment;

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
        mMaterial = prefs.getSurfaceMaterial();
        mIntensity = prefs.getSurfaceMaterialIntensity();
        mDockStyle = prefs.getAppLauncherDockStyle();
        mBorder = prefs.isTerminalBorderEnabled();
        mTerminalRadius = prefs.getTerminalCornerRadius();
        mPaneGap = prefs.getTerminalPaneGap();
        mWallpaperTerminalOpacity = prefs.getWallpaperEnabledTerminalBackgroundOpacity();
        mTint = prefs.getSurfaceGlassTint();
        mRim = prefs.getSurfaceGlassRim();
        mMotion = prefs.getSurfaceGlassMotion();
        mBend = prefs.getFancierGlassBendDp();
        mEdgeWidth = prefs.getFancierGlassEdgeWidthDp();
        mEdgeLight = prefs.getFancierGlassEdgeLightPercent();
        mSpecular = prefs.getFancierGlassSpecularPercent();
        mDispersion = prefs.getFancierGlassDispersionPercent();
        mKeyboardBlurRaw = prefs.getInAppKeyboardBlurRadiusRaw();
        mKeyOpacity = prefs.getInAppKeyboardKeyOpacity();
        mKeyRadiusRaw = rawKeyRadius(prefs);
        mDim = prefs.getWallpaperBackdropDim();
        mSoft = SoftWallpaper.isOn(prefs);
        mContrast = prefs.getTerminalContrastLevel().value;
        mTrailStyle = prefs.getTerminalCursorTrailStyle();
        mRetroEffect = prefs.getTerminalRetroEffect();
        mClockStyle = prefs.getTopPaneClockStyle();
        mClockAlignment = prefs.getTopPaneClockAlignment();
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
        prefs.setFancierGlassBendDp(mBend);
        prefs.setFancierGlassEdgeWidthDp(mEdgeWidth);
        prefs.setFancierGlassEdgeLightPercent(mEdgeLight);
        prefs.setFancierGlassSpecularPercent(mSpecular);
        prefs.setFancierGlassDispersionPercent(mDispersion);
        prefs.setInAppKeyboardBlurRadiusRaw(mKeyboardBlurRaw);
        prefs.setInAppKeyboardKeyOpacity(mKeyOpacity);
        restoreKeyRadius(prefs, mKeyRadiusRaw);
        prefs.setWallpaperBackdropDim(mDim);
        SoftWallpaper.set(prefs, mSoft);
        prefs.setTerminalContrastLevel(mContrast);
        prefs.setTerminalCursorTrailStyle(mTrailStyle);
        prefs.setTerminalRetroEffect(mRetroEffect);
        prefs.setTopPaneClockStyle(mClockStyle);
        prefs.setTopPaneClockAlignment(mClockAlignment);
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
            .append('|').append(mBend).append('|').append(mEdgeWidth).append('|').append(mEdgeLight)
            .append('|').append(mSpecular).append('|').append(mDispersion)
            .append('|').append(mKeyboardBlurRaw).append('|').append(mKeyOpacity)
            .append('|').append(mKeyRadiusRaw).append('|').append(mDim).append('|').append(mSoft)
            .append('|').append(mContrast).append('|').append(mTrailStyle)
            .append('|').append(mRetroEffect).append('|').append(mClockStyle)
            .append('|').append(mClockAlignment)
            .toString();
    }

    /** The live state's signature, for the dirty test, without holding a snapshot of it. */
    @NonNull
    static String signatureOf(@NonNull TermuxAppSharedPreferences prefs) {
        return capture(prefs).signature();
    }

    // ------------------------------------------------------------------------------ helpers

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
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null || !store.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP))
            return Float.NaN;
        try {
            return store.getFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP, Float.NaN);
        } catch (ClassCastException e) {
            return Float.NaN;
        }
    }

    private static void restoreKeyRadius(@NonNull TermuxAppSharedPreferences prefs, float raw) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null)
            return;
        if (Float.isNaN(raw))
            store.edit().remove(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP).apply();
        else
            store.edit().putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP, raw).apply();
    }

    @NonNull
    @Override
    public String toString() {
        return signature();
    }
}
