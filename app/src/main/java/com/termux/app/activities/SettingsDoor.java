package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * The one way to build an Intent that opens {@link SettingsActivity}. Settings runs in its own task
 * (the manifest's {@code ${TERMUX_PACKAGE_NAME}.settings} affinity), so a home press leaves it
 * behind rather than clearing it on screen. {@code NEW_TASK} puts the launch in that task;
 * {@code SINGLE_TOP} hands a launch into an open Settings to {@code onNewIntent}, which keeps the
 * page for a plain door and lands on the page a named door names. Every launch site goes through
 * here; {@code SettingsDoorSourceTest} fails on a direct one.
 */
public final class SettingsDoor {

    static final int FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP;

    private SettingsDoor() {}

    /** A plain door: Settings reopens where it was left. */
    @NonNull
    public static Intent plain(@NonNull Context context) {
        return flag(new Intent(context, SettingsActivity.class));
    }

    /** Puts the task flags on an Intent for Settings; returns the same Intent. */
    @NonNull
    static Intent flag(@NonNull Intent intent) {
        return intent.addFlags(FLAGS);
    }
}
