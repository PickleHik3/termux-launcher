package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * The one way to build an Intent that opens {@link SettingsActivity}. Settings runs in its own task
 * (the manifest's {@code ${TERMUX_PACKAGE_NAME}.settings} affinity), so a home press leaves it
 * behind rather than clearing it on screen. {@code NEW_TASK} puts the launch in that task;
 * {@code CLEAR_TOP} with {@code SINGLE_TOP} hands a launch into an open Settings to
 * {@code onNewIntent} even when a screen sits above it in that task (the activity is
 * {@code standard}, so {@code SINGLE_TOP} alone would stack a second one), which keeps the page for
 * a plain door and lands on the page a named door names. Every launch site goes through here;
 * {@code SettingsDoorSourceTest} fails on a direct one.
 */
public final class SettingsDoor {

    static final int FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
        | Intent.FLAG_ACTIVITY_SINGLE_TOP;

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
