package com.termux.app.layouteditor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

/**
 * The editors' one door to the theme's Material 3 shape and colour roles, so no editor surface
 * names a radius or a colour of its own: a shape is a {@code shapeAppearanceCorner*} attribute, a
 * fill a colour role.
 */
public final class EditorM3 {

    private EditorM3() {}

    /** The theme's shape family behind {@code shapeAttr} (e.g. shapeAppearanceCornerMedium). */
    @NonNull
    public static ShapeAppearanceModel shape(@NonNull Context context, @AttrRes int shapeAttr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(shapeAttr, value, true) && value.resourceId != 0)
            return ShapeAppearanceModel.builder(context, value.resourceId, 0).build();
        return ShapeAppearanceModel.builder().build();
    }

    /** A colour role of the theme the view wears. */
    public static int color(@NonNull View view, @AttrRes int colorAttr) {
        return MaterialColors.getColor(view, colorAttr);
    }

    /** A surface: the shape family filled with a colour role. */
    @NonNull
    public static MaterialShapeDrawable surface(@NonNull View view, @AttrRes int shapeAttr,
                                                @AttrRes int fillAttr) {
        MaterialShapeDrawable drawable =
            new MaterialShapeDrawable(shape(view.getContext(), shapeAttr));
        drawable.setFillColor(ColorStateList.valueOf(color(view, fillAttr)));
        return drawable;
    }
}
