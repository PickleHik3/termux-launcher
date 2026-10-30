package com.termux.app.surfaces;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.LookGrid;

/**
 * Where a row's value comes from, as its dot shows it (ADR 0017): the element on this page
 * (primary), this page (amber), the element everywhere (tertiary); a value every glass shares
 * shows no dot. The mapping is pure; {@link #ink} reads the theme.
 */
public enum RowSource {
    NONE, ELEMENT_ON_PAGE, PAGE, ELEMENT;

    /** The source of a value the grid resolved from {@code cell}. */
    @NonNull
    public static RowSource of(@Nullable LookGrid.Cell cell) {
        if (cell == null) return NONE;
        switch (cell) {
            case ELEMENT_ON_PAGE: return ELEMENT_ON_PAGE;
            case PAGE: return PAGE;
            case ELEMENT: return ELEMENT;
            case BASE:
            case SHIPPED:
            default:
                return NONE;
        }
    }

    /** Whether the row shows a dot. */
    public boolean marked() {
        return this != NONE;
    }

    /** The dot's colour; 0 for none. */
    @ColorInt
    public int ink(@NonNull Context context) {
        switch (this) {
            case ELEMENT_ON_PAGE:
                return EditorViews.color(context, androidx.appcompat.R.attr.colorPrimary);
            case PAGE:
                return context.getColor(R.color.editor_source_page);
            case ELEMENT:
                return EditorViews.color(context, com.google.android.material.R.attr.colorTertiary);
            case NONE:
            default:
                return 0;
        }
    }
}
