package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;

import com.termux.app.surfaces.EditorCard;

import org.junit.Test;

public class SettingsEditorDoorsTest {

    @Test
    public void everyRowTheEditorHoldsOpensOnItsCard() {
        String[] appearance = {"theme_mode", "use_system_wallpaper", "terminal_dynamic_colors_enabled",
            "terminal_contrast_level", "theme_templates_enabled", "wallpaper_render_zoom",
            "wallpaper_alignment", "app_launcher_bw_icons", "app_launcher_icon_pack_package",
            "app_launcher_pinned_icon_pack_package", "fullscreen"};
        for (String key : appearance)
            assertEquals(key, EditorCard.APPEARANCE, SettingsEditorDoors.cardForSearchKey(key));
        assertEquals(EditorCard.HOME, SettingsEditorDoors.cardForSearchKey("widgets_on_glass"));
    }

    @Test
    public void anUnlistedKeyOpensTheAppearanceCard() {
        assertEquals(EditorCard.APPEARANCE, SettingsEditorDoors.cardForSearchKey(null));
        assertEquals(EditorCard.APPEARANCE, SettingsEditorDoors.cardForSearchKey("nope"));
    }
}
