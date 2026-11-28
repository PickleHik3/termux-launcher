package com.termux.app.colours;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in colour schemes the Windows card's Text tab offers beside Wallpaper colours. Each is a
 * well-known terminal palette published under a licence that lets the app ship its colours (all MIT
 * except Tokyo Night, Apache-2.0); the licence and its home are kept with each, for the notices.
 * Picking one writes it to {@code ~/.termux/colors.properties} ({@link ColourSchemeFile}).
 */
public final class TerminalSchemes {

    private TerminalSchemes() {}

    /** One scheme: its id (what terl.conf names), its name on the chip, and its colours. */
    public static final class Scheme {
        @NonNull public final String id;
        @NonNull public final String name;
        /** The licence its palette is published under, and where. */
        @NonNull public final String licence;
        @NonNull public final String home;
        /** {@code foreground}, {@code background}, {@code cursor}, {@code color0}..{@code color15}, in order. */
        @NonNull public final Map<String, String> colours;

        Scheme(@NonNull String id, @NonNull String name, @NonNull String licence,
               @NonNull String home, @NonNull String foreground, @NonNull String background,
               @NonNull String cursor, @NonNull String... ansi) {
            if (ansi.length != 16) throw new IllegalArgumentException(id + ": 16 colours");
            this.id = id;
            this.name = name;
            this.licence = licence;
            this.home = home;
            Map<String, String> map = new LinkedHashMap<>();
            map.put("foreground", foreground);
            map.put("background", background);
            map.put("cursor", cursor);
            for (int i = 0; i < ansi.length; i++) map.put("color" + i, ansi[i]);
            this.colours = Collections.unmodifiableMap(map);
        }
    }

    private static final List<Scheme> ALL = Collections.unmodifiableList(build());

    @NonNull
    private static List<Scheme> build() {
        List<Scheme> list = new ArrayList<>();
        list.add(new Scheme("dracula", "Dracula", "MIT", "https://draculatheme.com",
            "#f8f8f2", "#282a36", "#f8f8f2",
            "#21222c", "#ff5555", "#50fa7b", "#f1fa8c", "#bd93f9", "#ff79c6", "#8be9fd", "#f8f8f2",
            "#6272a4", "#ff6e6e", "#69ff94", "#ffffa5", "#d6acff", "#ff92df", "#a4ffff", "#ffffff"));
        list.add(new Scheme("nord", "Nord", "MIT", "https://www.nordtheme.com",
            "#d8dee9", "#2e3440", "#d8dee9",
            "#3b4252", "#bf616a", "#a3be8c", "#ebcb8b", "#81a1c1", "#b48ead", "#88c0d0", "#e5e9f0",
            "#4c566a", "#bf616a", "#a3be8c", "#ebcb8b", "#81a1c1", "#b48ead", "#8fbcbb", "#eceff4"));
        list.add(new Scheme("gruvbox_dark", "Gruvbox", "MIT", "https://github.com/morhetz/gruvbox",
            "#ebdbb2", "#282828", "#ebdbb2",
            "#282828", "#cc241d", "#98971a", "#d79921", "#458588", "#b16286", "#689d6a", "#a89984",
            "#928374", "#fb4934", "#b8bb26", "#fabd2f", "#83a598", "#d3869b", "#8ec07c", "#ebdbb2"));
        list.add(new Scheme("solarized_dark", "Solarized Dark", "MIT",
            "https://ethanschoonover.com/solarized", "#839496", "#002b36", "#93a1a1",
            "#073642", "#dc322f", "#859900", "#b58900", "#268bd2", "#d33682", "#2aa198", "#eee8d5",
            "#002b36", "#cb4b16", "#586e75", "#657b83", "#839496", "#6c71c4", "#93a1a1", "#fdf6e3"));
        list.add(new Scheme("solarized_light", "Solarized Light", "MIT",
            "https://ethanschoonover.com/solarized", "#657b83", "#fdf6e3", "#586e75",
            "#073642", "#dc322f", "#859900", "#b58900", "#268bd2", "#d33682", "#2aa198", "#eee8d5",
            "#002b36", "#cb4b16", "#586e75", "#657b83", "#839496", "#6c71c4", "#93a1a1", "#fdf6e3"));
        list.add(new Scheme("catppuccin_mocha", "Catppuccin Mocha", "MIT", "https://catppuccin.com",
            "#cdd6f4", "#1e1e2e", "#f5e0dc",
            "#45475a", "#f38ba8", "#a6e3a1", "#f9e2af", "#89b4fa", "#f5c2e7", "#94e2d5", "#bac2de",
            "#585b70", "#f38ba8", "#a6e3a1", "#f9e2af", "#89b4fa", "#f5c2e7", "#94e2d5", "#a6adc8"));
        list.add(new Scheme("catppuccin_latte", "Catppuccin Latte", "MIT", "https://catppuccin.com",
            "#4c4f69", "#eff1f5", "#dc8a78",
            "#5c5f77", "#d20f39", "#40a02b", "#df8e1d", "#1e66f5", "#ea76cb", "#179299", "#acb0be",
            "#6c6f85", "#d20f39", "#40a02b", "#df8e1d", "#1e66f5", "#ea76cb", "#179299", "#bcc0cc"));
        list.add(new Scheme("tokyo_night", "Tokyo Night", "Apache-2.0",
            "https://github.com/folke/tokyonight.nvim", "#c0caf5", "#1a1b26", "#c0caf5",
            "#15161e", "#f7768e", "#9ece6a", "#e0af68", "#7aa2f7", "#bb9af7", "#7dcfff", "#a9b1d6",
            "#414868", "#f7768e", "#9ece6a", "#e0af68", "#7aa2f7", "#bb9af7", "#7dcfff", "#c0caf5"));
        return list;
    }

    /** Every built-in scheme, in the order the card shows them. */
    @NonNull
    public static List<Scheme> all() {
        return ALL;
    }

    /** The scheme named {@code id}, or null. */
    @Nullable
    public static Scheme of(@Nullable String id) {
        if (id == null) return null;
        for (Scheme scheme : ALL) if (scheme.id.equals(id)) return scheme;
        return null;
    }
}
