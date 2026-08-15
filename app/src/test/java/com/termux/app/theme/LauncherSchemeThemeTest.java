package com.termux.app.theme;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The palette only reaches the chrome through Material's overridable colour resources, so a role
 * missing from that map is a role that silently keeps the wallpaper palette.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherSchemeThemeTest {

    private static LinkedHashMap<String, Integer> tokens() {
        Properties props = new Properties();
        props.setProperty("background", "#1D2021");
        props.setProperty("foreground", "#D4BE98");
        props.setProperty("color1", "#EA6962");
        props.setProperty("color4", "#7DAEA3");
        props.setProperty("color5", "#D3869B");
        props.setProperty("color6", "#89B482");
        props.setProperty("color8", "#504945");
        SchemeColors scheme = SchemeColors.from(props);
        assertNotNull(scheme);
        return LauncherThemeTokens.derive(scheme);
    }

    /** Every colour resource Material's personalized-colour overlay reads must be filled in. */
    @Test
    public void everyMaterialRoleResourceIsOverridden() {
        Map<Integer, Integer> overrides = LauncherSchemeTheme.resourceOverrides(tokens());
        int[] required = {
            com.google.android.material.R.color.material_personalized_color_primary,
            com.google.android.material.R.color.material_personalized_color_on_primary,
            com.google.android.material.R.color.material_personalized_color_primary_container,
            com.google.android.material.R.color.material_personalized_color_on_primary_container,
            com.google.android.material.R.color.material_personalized_color_primary_inverse,
            com.google.android.material.R.color.material_personalized_color_secondary,
            com.google.android.material.R.color.material_personalized_color_on_secondary,
            com.google.android.material.R.color.material_personalized_color_secondary_container,
            com.google.android.material.R.color.material_personalized_color_on_secondary_container,
            com.google.android.material.R.color.material_personalized_color_tertiary,
            com.google.android.material.R.color.material_personalized_color_on_tertiary,
            com.google.android.material.R.color.material_personalized_color_tertiary_container,
            com.google.android.material.R.color.material_personalized_color_on_tertiary_container,
            com.google.android.material.R.color.material_personalized_color_error,
            com.google.android.material.R.color.material_personalized_color_on_error,
            com.google.android.material.R.color.material_personalized_color_error_container,
            com.google.android.material.R.color.material_personalized_color_on_error_container,
            com.google.android.material.R.color.material_personalized_color_background,
            com.google.android.material.R.color.material_personalized_color_on_background,
            com.google.android.material.R.color.material_personalized_color_surface,
            com.google.android.material.R.color.material_personalized_color_on_surface,
            com.google.android.material.R.color.material_personalized_color_surface_variant,
            com.google.android.material.R.color.material_personalized_color_on_surface_variant,
            com.google.android.material.R.color.material_personalized_color_surface_inverse,
            com.google.android.material.R.color.material_personalized_color_on_surface_inverse,
            com.google.android.material.R.color.material_personalized_color_surface_bright,
            com.google.android.material.R.color.material_personalized_color_surface_dim,
            com.google.android.material.R.color.material_personalized_color_surface_container,
            com.google.android.material.R.color.material_personalized_color_surface_container_low,
            com.google.android.material.R.color.material_personalized_color_surface_container_lowest,
            com.google.android.material.R.color.material_personalized_color_surface_container_high,
            com.google.android.material.R.color.material_personalized_color_surface_container_highest,
            com.google.android.material.R.color.material_personalized_color_outline,
            com.google.android.material.R.color.material_personalized_color_outline_variant,
            com.google.android.material.R.color.material_personalized_color_control_activated,
            com.google.android.material.R.color.material_personalized_color_control_normal,
            com.google.android.material.R.color.material_personalized_color_control_highlight,
            com.google.android.material.R.color.material_personalized_color_text_primary_inverse,
            com.google.android.material.R.color.material_personalized_color_text_secondary_and_tertiary_inverse,
            com.google.android.material.R.color.material_personalized_color_text_hint_foreground_inverse,
            com.google.android.material.R.color.material_personalized_color_text_primary_inverse_disable_only,
            com.google.android.material.R.color.material_personalized_color_text_secondary_and_tertiary_inverse_disabled,
            com.termux.R.color.launcher_scheme_scrollbar,
        };
        for (int resourceId : required) {
            assertTrue("resource 0x" + Integer.toHexString(resourceId) + " is not overridden",
                overrides.containsKey(resourceId));
        }
        assertEquals(required.length, overrides.size());
    }

    @Test
    public void surfaceRoleCarriesTheSchemeBackground() {
        LinkedHashMap<String, Integer> tokens = tokens();
        Map<Integer, Integer> overrides = LauncherSchemeTheme.resourceOverrides(tokens);
        assertEquals(tokens.get(LauncherThemeTokens.SURFACE),
            overrides.get(com.google.android.material.R.color.material_personalized_color_surface));
        assertEquals(tokens.get(LauncherThemeTokens.SURFACE),
            overrides.get(com.google.android.material.R.color.material_personalized_color_background));
    }

    /** The exported file is what fish, tmux and Neovim read; it uses the wallpaper path's key names. */
    @Test
    public void exportUsesTheSharedKeyNames() {
        Properties exported = LauncherSchemeTheme.exportProperties(tokens());
        assertEquals("#1D2021", exported.getProperty("surface"));
        assertEquals("#D4BE98", exported.getProperty("on_surface"));
        assertNotNull(exported.getProperty("surface_container_high"));
        assertNotNull(exported.getProperty("on_primary_container"));
        // Same value under the name the wallpaper export uses for it.
        assertEquals(exported.getProperty("surface_container"), exported.getProperty("surface_variant"));
    }

    @Test
    public void schemeThemingIsOffWithoutAPreference() {
        assertFalse(LauncherSchemeTheme.isEnabled((com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences) null));
    }
}
