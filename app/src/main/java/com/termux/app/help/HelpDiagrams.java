package com.termux.app.help;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import com.termux.R;

/**
 * Which drawn diagram belongs to which help topic.
 *
 * <p>Each topic's page shows one vector drawable, 360dp wide, drawn in the app's theme colours
 * and regenerated from {@code project-docs/reference/help-diagrams/generate.py}. The table is
 * written out rather than looked up by name, so resource shrinking keeps every drawable and a
 * topic with no diagram is a compile-time fact, not a runtime surprise. Only Mouse mode moves:
 * its drawable is an animated vector.
 */
public final class HelpDiagrams {
    private HelpDiagrams() {}

    /** The topic whose diagram is an animated vector drawable. */
    static final String ANIMATED_TOPIC = "mouse_mode";

    /** The drawable for this topic's page, or 0 when the topic has none. */
    @DrawableRes
    public static int forTopic(@Nullable String topicId) {
        if (topicId == null) return 0;
        switch (topicId) {
            case "places": return R.drawable.help_diagram_places;
            case "corners": return R.drawable.help_diagram_corners;
            case "minimal": return R.drawable.help_diagram_minimal;
            case "palette": return R.drawable.help_diagram_palette;
            case "status": return R.drawable.help_diagram_status;
            case "stats": return R.drawable.help_diagram_stats;
            case "pinned_notifications": return R.drawable.help_diagram_pinned_notifications;
            case "dock": return R.drawable.help_diagram_dock;
            case "az": return R.drawable.help_diagram_az;
            case "organize_apps": return R.drawable.help_diagram_organize_apps;
            case "widget": return R.drawable.help_diagram_widget;
            case "pages": return R.drawable.help_diagram_pages;
            case "copy_paste": return R.drawable.help_diagram_copy_paste;
            case "mouse_mode": return R.drawable.help_anim_mouse_mode;
            case "find_text": return R.drawable.help_diagram_find_text;
            case "hierarchy": return R.drawable.help_diagram_hierarchy;
            case "pictures": return R.drawable.help_diagram_pictures;
            case "text_size": return R.drawable.help_diagram_text_size;
            case "keyboard_swipe": return R.drawable.help_diagram_keyboard_swipe;
            case "keyboard": return R.drawable.help_diagram_keyboard;
            case "keyboard_layouts": return R.drawable.help_diagram_keyboard_layouts;
            case "keys": return R.drawable.help_diagram_keys;
            case "shortcuts": return R.drawable.help_diagram_shortcuts;
            case "space": return R.drawable.help_diagram_space;
            case "settings": return R.drawable.help_diagram_settings;
            case "voice": return R.drawable.help_diagram_voice;
            case "clipboard": return R.drawable.help_diagram_clipboard;
            case "panes": return R.drawable.help_diagram_panes;
            case "float_pane": return R.drawable.help_diagram_float_pane;
            case "move_panes": return R.drawable.help_diagram_move_panes;
            case "windows": return R.drawable.help_diagram_windows;
            case "workspaces": return R.drawable.help_diagram_workspaces;
            case "appearance_editor": return R.drawable.help_diagram_appearance_editor;
            case "layout_editor": return R.drawable.help_diagram_layout_editor;
            case "themes": return R.drawable.help_diagram_themes;
            case "tlstore": return R.drawable.help_diagram_tlstore;
            case "on_device_ai": return R.drawable.help_diagram_on_device_ai;
            case "scale": return R.drawable.help_diagram_scale;
            case "display_apps": return R.drawable.help_diagram_display_apps;
            case "display_keys": return R.drawable.help_diagram_display_keys;
            case "gui_apps": return R.drawable.help_diagram_gui_apps;
            case "touchpad": return R.drawable.help_diagram_touchpad;
            case "fix_keyboard": return R.drawable.help_diagram_fix_keyboard;
            case "fix_dock": return R.drawable.help_diagram_fix_dock;
            case "fix_shortcuts": return R.drawable.help_diagram_fix_shortcuts;
            case "fix_stats": return R.drawable.help_diagram_fix_stats;
            case "fix_support": return R.drawable.help_diagram_fix_support;
            case "fix_display": return R.drawable.help_diagram_fix_display;
            case "fix_action": return R.drawable.help_diagram_fix_action;
            default: return 0;
        }
    }

    /** Whether the topic's drawable plays: a loop while its page is on screen. */
    public static boolean isAnimated(@Nullable String topicId) {
        return ANIMATED_TOPIC.equals(topicId);
    }
}
