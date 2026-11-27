package com.termux.app.surfaces;

import android.content.Context;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;

/**
 * The Appearance editor's sheet colours for cards drawn outside this package (edit mode's card and
 * strip), so they follow light, dark, black, Material You and the launcher's scheme exactly as the
 * editor's own sheet does.
 */
public final class EditorSheetFill {

    private EditorSheetFill() { }

    /** Whether the theme's surface is dark. */
    public static boolean dark(@NonNull Context context) {
        return EditorCardSheet.darkSurface(context);
    }

    /** The sheet's fill role for that theme. */
    @AttrRes
    public static int fill(boolean dark) {
        return EditorCardSheet.sheetFill(dark);
    }
}
