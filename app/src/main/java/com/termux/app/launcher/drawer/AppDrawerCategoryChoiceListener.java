package com.termux.app.launcher.drawer;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;

/**
 * Long-press seam for reassigning an app's category — fired from the "Category" row of the
 * drawer's app-context popup, whether that popup was opened from a collapsed tile's launch icon
 * or from a cell in the expanded category grid.
 */
public interface AppDrawerCategoryChoiceListener {
    void onCategoryChoiceRequested(@NonNull LauncherAppEntry entry, @NonNull View anchor);
}
