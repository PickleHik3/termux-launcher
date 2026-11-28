package com.termux.app.upgrade;

import android.app.Application;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.settings.LauncherSettingsAdapters;
import com.termux.app.upgrade.TerlUpgradeFates.Fate;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every key and registry id v1 knows has a fate, so a ticket that adds one decides what the
 * upgrade to Terl does with it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerlUpgradeFatesTest {

    @Test
    public void everyV1PreferenceKeyHasAFate() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Field field : TERMUX_APP.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class
                || !field.getName().startsWith("KEY_")) continue;
            String key = (String) field.get(null);
            if (key != null && TerlUpgradeFates.ofKey(key) == null)
                missing.add(field.getName() + " (" + key + ")");
        }
        assertTrue("no fate in TerlUpgradeFates: " + missing, missing.isEmpty());
    }

    @Test
    public void everyRegistryIdHasAFate() {
        List<String> missing = new ArrayList<>();
        for (SettingDeclaration each : SettingDeclarations.ALL)
            if (TerlUpgradeFates.ofSetting(each.id) == null) missing.add(each.id);
        Application app = ApplicationProvider.getApplicationContext();
        for (SettingAdapter adapter : LauncherSettingsAdapters.forDevice(app, () -> {})) {
            for (SettingDeclaration each : adapter.declarations())
                if (TerlUpgradeFates.ofSetting(each.id) == null) missing.add(each.id);
        }
        assertTrue("no fate in TerlUpgradeFates: " + missing, missing.isEmpty());
    }

    @Test
    public void aDeclaredPreferenceKeyHasItsDeclarationsFate() {
        for (SettingDeclaration each : SettingDeclarations.ALL) {
            if (each.store != SettingDeclaration.Store.APP_PREFERENCES) continue;
            assertEquals(each.id, TerlUpgradeFates.ofSetting(each.id),
                TerlUpgradeFates.ofKey(each.key));
        }
    }

    @Test
    public void termuxPropertiesIsCarried() {
        for (SettingDeclaration each : SettingDeclarations.ALL) {
            if (each.store == SettingDeclaration.Store.PROPERTIES)
                assertEquals(each.id, Fate.CARRY, TerlUpgradeFates.ofSetting(each.id));
        }
    }

    @Test
    public void theRowsThatRetireAreDropped() {
        for (SettingDeclaration each : new SettingDeclaration[] {
            SettingDeclarations.PAGE_TRANSITION, SettingDeclarations.DOCK_STYLE,
            SettingDeclarations.STATUS_BATTERY, SettingDeclarations.STATUS_MEDIA,
            SettingDeclarations.STATUS_WEATHER, SettingDeclarations.STATUS_CLOCK_AM_PM,
            SettingDeclarations.NOTIFICATION_BADGE_STYLE, SettingDeclarations.QUICK_REPLY_SWIPE}) {
            assertEquals(each.id, Fate.DROP, TerlUpgradeFates.ofSetting(each.id));
        }
    }

    @Test
    public void editModesSettingsStay() {
        for (SettingDeclaration each : new SettingDeclaration[] {
            SettingDeclarations.THEME_MODE, SettingDeclarations.TERMINAL_CONTRAST,
            SettingDeclarations.TEXT_SIZE, SettingDeclarations.ANIMATION_SPEED,
            SettingDeclarations.IDLE_ANIMATIONS, SettingDeclarations.IDLE_TEAR_RATE,
            SettingDeclarations.CURSOR_TRAIL, SettingDeclarations.WALLPAPER_PARALLAX,
            SettingDeclarations.TERMINAL_EFFECT, SettingDeclarations.TERMINAL_SCREEN_EVENTS,
            SettingDeclarations.TERMINAL_EFFECT_COMPOSITE}) {
            assertNotEquals(each.id, Fate.DROP, TerlUpgradeFates.ofSetting(each.id));
        }
    }

    /**
     * The round-2 decision (terl-decisions 2026-11-28): Wallpaper colours, the keyboard theme, its
     * per-key colours and its typeface are carried, though the Appearance editor's set holds the
     * first three; the colour scheme setting is carried with them.
     */
    @Test
    public void wallpaperColoursAndTheKeyboardsStyleAreCarried() {
        assertEquals(Fate.CARRY, TerlUpgradeFates.ofKey(TERMUX_APP.KEY_TERMINAL_DYNAMIC_COLORS_ENABLED));
        assertEquals(Fate.CARRY, TerlUpgradeFates.ofKey(TERMUX_APP.KEY_IN_APP_KEYBOARD_THEME));
        assertEquals(Fate.CARRY, TerlUpgradeFates.ofKey(TERMUX_APP.KEY_IN_APP_KEYBOARD_COLOR_SCHEME));
        assertEquals(Fate.CARRY, TerlUpgradeFates.ofKey(TERMUX_APP.KEY_IN_APP_KEYBOARD_FONT_PATH));
        assertEquals(Fate.CARRY, TerlUpgradeFates.ofSetting(
            com.termux.app.settings.TerminalColoursAdapter.ID));
        // The keyboard's sizes stay with the look: fresh.
        assertEquals(Fate.FRESH, TerlUpgradeFates.ofKey(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
    }

    @Test
    public void terlsOwnLookStartsFresh() {
        assertEquals(Fate.FRESH, TerlUpgradeFates.ofKey("terl.look.own.bar.opacity"));
        assertEquals(Fate.CARRY, TerlUpgradeFates.ofKey(TERMUX_APP.KEY_TERL_DESKTOP));
    }
}
