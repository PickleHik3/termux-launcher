package com.termux.app.launcher.drawer;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;

public interface AppDrawerPickupDelegate {
    boolean claimContext(@NonNull View source, @NonNull LauncherAppEntry entry);
    boolean startPickup(@NonNull View source, @NonNull LauncherAppEntry entry);
}
