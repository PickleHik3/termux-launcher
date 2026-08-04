package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;

/**
 * The one place that decides which legend group a bound action belongs to and what colour that
 * group wears. The keybind hint slab, the lit caps on the in-app keyboard and the palette's
 * category tint all read from here, so a stroke has one identity wherever it is shown.
 *
 * <p>Groups come from the action id's own namespace ({@code pane.*}, {@code window.*}, …), which
 * is what {@code termux-launcher-bindings.conf} names too: rebinding a stroke to another action
 * moves its cap into that action's group colour with no table to keep in sync.
 *
 * <p>Colours are hue rotations of the live Material primary rather than fixed literals, so the
 * groups stay distinguishable while still tracking the user's theme. Each group's rotation is far
 * enough from its neighbours' (40° minimum) to be told apart on a small cap, and the result is
 * pushed through {@link InAppKeyboardPaletteFactory#ensureContrast} against the surface it is
 * drawn on so a dark theme never yields an unreadable cap.
 */
public final class KeybindGroupPalette {

    /** Legend groups, in the order the slab stacks them. */
    public enum Group {
        PANES(0f),
        WINDOWS(160f),
        SESSION(80f),
        WORKSPACE(240f),
        TERMINAL(40f),
        CLIPBOARD(200f),
        APPEARANCE(120f),
        APP(280f),
        /** Anything outside a known namespace. */
        VIEW(320f);

        /** Degrees this group's colour sits from the theme primary's hue. */
        public final float hueOffset;

        Group(float hueOffset) {
            this.hueOffset = hueOffset;
        }

        /** Section title as the legend prints it. */
        @NonNull
        public String title() {
            return name();
        }
    }

    private KeybindGroupPalette() {}

    /** The group an action id belongs to; never null, so every bound key can be coloured. */
    @NonNull
    public static Group groupFor(@NonNull String toolName) {
        int dot = toolName.indexOf('.');
        String namespace = dot < 0 ? toolName : toolName.substring(0, dot);
        switch (namespace) {
            case "pane": return Group.PANES;
            case "window": return Group.WINDOWS;
            case "session": return Group.SESSION;
            case "workspace": return Group.WORKSPACE;
            case "terminal": return Group.TERMINAL;
            case "clipboard": return Group.CLIPBOARD;
            // Fonts are an appearance control by any user's reading of the word, and two groups
            // one stroke apart would only cost the legend a second header.
            case "appearance": case "fonts": return Group.APPEARANCE;
            case "app": return Group.APP;
            default: return Group.VIEW;
        }
    }

    /**
     * The group's colour against {@code surfaceColor}.
     *
     * @param primary theme primary, the hue every group rotates from
     * @param surfaceColor what the colour is drawn on, for the contrast pass
     */
    public static int colorFor(@NonNull Group group, int primary, int surfaceColor) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(primary, hsl);
        hsl[0] = (hsl[0] + group.hueOffset) % 360f;
        // A desaturated or near-black/white primary rotates into nothing visible, so floor the
        // saturation and hold lightness in the band where a 6.5dp legend title and a lit keycap
        // both stay legible. PANES keeps the theme's own primary untouched below.
        hsl[1] = Math.max(0.42f, hsl[1]);
        hsl[2] = Math.min(0.74f, Math.max(0.56f, hsl[2]));
        int rotated = group.hueOffset == 0f ? primary : ColorUtils.HSLToColor(hsl);
        return InAppKeyboardPaletteFactory.ensureContrast(rotated, surfaceColor);
    }
}
