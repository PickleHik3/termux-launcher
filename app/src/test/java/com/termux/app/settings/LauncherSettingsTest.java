package com.termux.app.settings;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** The launcher's resume and pause reach the Settings applier without building anything. */
public class LauncherSettingsTest {

    @Test
    public void resumeAndPauseBuildNoRegistryOnTheStartUpPath() {
        LauncherSettings.onLauncherResumed(refresh -> { });
        LauncherSettings.onLauncherPaused();
        LauncherSettings.onLauncherResumed(refresh -> { });
        assertFalse(LauncherSettings.isBuilt());
        LauncherSettings.onLauncherPaused();
    }
}
