package com.termux.app.material;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

/**
 * The one place hand-built views read the Material 3 theme: colour roles with an M3 baseline
 * fallback, shape tokens, type-scale text appearances, state-layer opacities and the plain popup
 * surface. Nothing here owns a literal radius, colour, alpha or size; it only resolves the theme's
 * own tokens so code-built views and XML-built views stay the same system.
 */
public final class M3 {

    /** M3 state-layer opacities (hover 8%, focus/pressed 12%, dragged 16%). */
    public static final float STATE_HOVER = 0.08f;
    public static final float STATE_PRESSED = 0.12f;
    public static final float STATE_DRAGGED = 0.16f;
    /** M3 disabled opacities: content 38%, container 12%. */
    public static final float DISABLED_CONTENT = 0.38f;
    public static final float DISABLED_CONTAINER = 0.12f;

    private M3() {}

    // ------------------------------------------------------------------ colour

    /** A colour role from the theme; {@code fallbackRes} is the M3 baseline token used if absent. */
    @ColorInt
    public static int color(@NonNull Context context, @AttrRes int attr, @ColorRes int fallbackRes) {
        return MaterialColors.getColor(context, attr, ContextCompat.getColor(context, fallbackRes));
    }

    @ColorInt
    public static int color(@NonNull View view, @AttrRes int attr, @ColorRes int fallbackRes) {
        return MaterialColors.getColor(view, attr,
            ContextCompat.getColor(view.getContext(), fallbackRes));
    }

    @ColorInt public static int surfaceContainer(@NonNull Context c) {
        return color(c, com.google.android.material.R.attr.colorSurfaceContainer,
            com.google.android.material.R.color.m3_sys_color_dark_surface_container);
    }

    @ColorInt public static int onSurface(@NonNull Context c) {
        return color(c, com.google.android.material.R.attr.colorOnSurface,
            com.google.android.material.R.color.m3_sys_color_dark_on_surface);
    }

    @ColorInt public static int onSurfaceVariant(@NonNull Context c) {
        return color(c, com.google.android.material.R.attr.colorOnSurfaceVariant,
            com.google.android.material.R.color.m3_sys_color_dark_on_surface_variant);
    }

    @ColorInt public static int outlineVariant(@NonNull Context c) {
        return color(c, com.google.android.material.R.attr.colorOutlineVariant,
            com.google.android.material.R.color.m3_sys_color_dark_outline_variant);
    }

    @ColorInt public static int primary(@NonNull Context c) {
        return color(c, androidx.appcompat.R.attr.colorPrimary,
            com.google.android.material.R.color.m3_sys_color_dark_primary);
    }

    @ColorInt public static int error(@NonNull Context c) {
        return color(c, androidx.appcompat.R.attr.colorError,
            com.google.android.material.R.color.m3_sys_color_dark_error);
    }

    /** The theme's modal scrim, the dim a sheet or dialog puts behind itself. */
    @ColorInt public static int scrim(@NonNull Context c) {
        return color(c, com.google.android.material.R.attr.scrimBackground,
            com.google.android.material.R.color.mtrl_scrim_color);
    }

    /** A named surface role by attribute, for the container tiers. */
    @ColorInt public static int surfaceContainerLow(@NonNull Context c) {
        return color(c, com.google.android.material.R.attr.colorSurfaceContainerLow,
            com.google.android.material.R.color.m3_sys_color_dark_surface_container_low);
    }

    @ColorInt public static int surfaceContainerHigh(@NonNull Context c) {
        return color(c, com.google.android.material.R.attr.colorSurfaceContainerHigh,
            com.google.android.material.R.color.m3_sys_color_dark_surface_container_high);
    }

    /** {@code base} at an M3 state-layer opacity (see {@link #STATE_PRESSED} and friends). */
    @ColorInt
    public static int stateLayer(@ColorInt int base, float opacity) {
        return MaterialColors.compositeARGBWithAlpha(base, Math.round(255f * opacity));
    }

    // ------------------------------------------------------------------ shape

    /** The theme's shape token, e.g. {@code shapeAppearanceCornerMedium}. */
    @NonNull
    public static ShapeAppearanceModel shape(@NonNull Context context, @AttrRes int shapeAttr) {
        TypedValue value = new TypedValue();
        int style = context.getTheme().resolveAttribute(shapeAttr, value, true)
            && value.resourceId != 0 ? value.resourceId
            : com.google.android.material.R.style.ShapeAppearance_Material3_Corner_Medium;
        return ShapeAppearanceModel.builder(context, style, 0).build();
    }

    /** A filled, elevation-aware surface of the given shape token. */
    @NonNull
    public static MaterialShapeDrawable surface(@NonNull Context context, @AttrRes int shapeAttr,
                                                @ColorInt int fill) {
        MaterialShapeDrawable drawable = new MaterialShapeDrawable(shape(context, shapeAttr));
        drawable.initializeElevationOverlay(context);
        drawable.setFillColor(ColorStateList.valueOf(fill));
        return drawable;
    }

    // ------------------------------------------------------------------ type

    /** Applies a type-scale role, e.g. {@code textAppearanceLabelLarge}, to {@code view}. */
    public static void textAppearance(@NonNull TextView view, @AttrRes int attr) {
        @StyleRes int style = textAppearanceRes(view.getContext(), attr);
        if (style != 0) view.setTextAppearance(style);
    }

    @StyleRes
    public static int textAppearanceRes(@NonNull Context context, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        return context.getTheme().resolveAttribute(attr, value, true) ? value.resourceId : 0;
    }

    // ------------------------------------------------------------------ popup

    /**
     * Gives {@code popup} the theme's plain menu surface: medium corners on
     * {@code colorSurfaceContainer}, the menu elevation level, and the theme's own window
     * transitions. The popup must have been created with {@code new PopupWindow(context)} so it
     * reads the theme's popup style.
     */
    public static void styleMenuPopup(@NonNull Context context, @NonNull PopupWindow popup) {
        float elevation = context.getResources().getDimension(
            com.google.android.material.R.dimen.m3_comp_menu_container_elevation);
        MaterialShapeDrawable surface = surface(context,
            com.google.android.material.R.attr.shapeAppearanceCornerMedium,
            surfaceContainer(context));
        surface.setElevation(elevation);
        popup.setBackgroundDrawable(surface);
        popup.setElevation(elevation);
        popup.setClippingEnabled(true);
    }

    // ------------------------------------------------------------------ cards

    /** A clickable M3 card row: outlined, or filled when {@code outlined} is false. */
    @NonNull
    public static MaterialCardView clickableCard(@NonNull Context context, boolean outlined) {
        MaterialCardView card = new MaterialCardView(context, null, outlined
            ? com.google.android.material.R.attr.materialCardViewOutlinedStyle
            : com.google.android.material.R.attr.materialCardViewFilledStyle);
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }
}
