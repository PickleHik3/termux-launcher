# Termux Launcher — launcher-owned fish config. setup-launcher replaces this
# file on every run (after a timestamped .bak), so keep only what the launcher
# integration itself needs here: PATH, the wallpaper Material palette and its
# per-prompt refresh, the clear/cursor helpers, and the Oh My Posh prompt.
#
# YOUR OWN SETTINGS GO IN ~/.config/fish/conf.d/personal.fish (installed once
# from the conf.d-personal.fish example and never overwritten). Editor, aliases,
# and the ls/cd helpers all live there. Note fish loads conf.d/*.fish BEFORE
# this file.

set -g fish_greeting ""

# Non-login shells (sshd, scripts) arrive without the profile on PATH — everything
# below assumes coreutils. No-op where the profile does not exist (apt editions).
if test -d "$HOME/.nix-profile/bin"; and not contains -- "$HOME/.nix-profile/bin" $PATH
    fish_add_path --prepend "$HOME/.nix-profile/bin"
end

set -gx TMPDIR "$HOME/.tmp"
mkdir -p "$TMPDIR"

set -q COLORTERM; or set -gx COLORTERM truecolor

fish_add_path "$HOME/.local/bin" "$HOME/.termux/bin"

# Nix edition: launcherctl/tai live in the proot /bin, which the generated
# PATH does not include. Append (not prepend) so nix binaries keep priority.
test -d /nix; and fish_add_path --append /bin

# Load wallpaper-generated Material colors when available. The launcher writes
# them to ~/.termux/material-colors.sh (and .properties as a fallback).
function __load_termux_material_colors
    set -l shell_colors "$HOME/.termux/material-colors.sh"
    set -l colors "$HOME/.termux/material-colors.properties"

    if test -r "$shell_colors"
        source "$shell_colors"
        return
    end

    test -r "$colors"; or return

    while read -l line
        set line (string trim -- "$line")
        string match -qr '^(#|$)' -- "$line"; and continue

        set -l pair (string split -m 1 '=' -- "$line")
        test (count $pair) -eq 2; or continue

        set -l key (string upper (string replace -a '-' '_' -- $pair[1]))
        set -gx TERMUX_MATERIAL_$key $pair[2]
    end < "$colors"
end

__load_termux_material_colors

# Theme changes rewrite the Material exports while existing shells keep their
# old palette. Refresh before each prompt so open shells adopt new colors.
set -g __termux_material_colors_signature ""
function __refresh_termux_material_colors --on-event fish_prompt
    set -l colors "$HOME/.termux/material-colors.sh"
    test -r "$colors"; or set colors "$HOME/.termux/material-colors.properties"
    test -r "$colors"; or return

    set -l signature (command stat -c '%Y:%s' "$colors" 2>/dev/null)
    test -n "$signature"; or return
    test "$signature" = "$__termux_material_colors_signature"; and return

    __load_termux_material_colors
    set -g __termux_material_colors_signature "$signature"
end

# Fallback palette so the prompt still renders in plain Termux or before the
# launcher has exported wallpaper colors.
set -q TERMUX_MATERIAL_ERROR; or set -gx TERMUX_MATERIAL_ERROR "#F2B8B5"
set -q TERMUX_MATERIAL_ERROR_CONTAINER; or set -gx TERMUX_MATERIAL_ERROR_CONTAINER "#8C1D18"
set -q TERMUX_MATERIAL_ON_PRIMARY; or set -gx TERMUX_MATERIAL_ON_PRIMARY "#003826"
set -q TERMUX_MATERIAL_ON_SECONDARY; or set -gx TERMUX_MATERIAL_ON_SECONDARY "#1E3529"
set -q TERMUX_MATERIAL_ON_SURFACE; or set -gx TERMUX_MATERIAL_ON_SURFACE "#DEE4DE"
set -q TERMUX_MATERIAL_ON_SURFACE_VARIANT; or set -gx TERMUX_MATERIAL_ON_SURFACE_VARIANT "#C0C9C0"
set -q TERMUX_MATERIAL_PRIMARY; or set -gx TERMUX_MATERIAL_PRIMARY "#8CD5B3"
set -q TERMUX_MATERIAL_SECONDARY; or set -gx TERMUX_MATERIAL_SECONDARY "#B3CCBE"
set -q TERMUX_MATERIAL_SURFACE; or set -gx TERMUX_MATERIAL_SURFACE "#0F1512"
set -q TERMUX_MATERIAL_SURFACE_CONTAINER_HIGHEST; or set -gx TERMUX_MATERIAL_SURFACE_CONTAINER_HIGHEST "#303632"
set -q TERMUX_MATERIAL_SURFACE_VARIANT; or set -gx TERMUX_MATERIAL_SURFACE_VARIANT "#404943"
set -q TERMUX_MATERIAL_TERTIARY; or set -gx TERMUX_MATERIAL_TERTIARY "#A5CCDF"
set -q TERMUX_MATERIAL_TERTIARY_CONTAINER; or set -gx TERMUX_MATERIAL_TERTIARY_CONTAINER "#234C5E"
set -q TERMUX_MATERIAL_ON_TERTIARY_CONTAINER; or set -gx TERMUX_MATERIAL_ON_TERTIARY_CONTAINER "#C1E8FB"
set -q TERMUX_MATERIAL_ON_ERROR_CONTAINER; or set -gx TERMUX_MATERIAL_ON_ERROR_CONTAINER "#F9DEDC"

# Keep the prompt on the bottom of the screen after clearing. Parking on the
# last row works for any prompt height: a one-line prompt fills it exactly, and
# a taller one scrolls the freshly cleared (blank) screen up to fit. Parking a
# row higher assumed a two-line prompt and left a one-line theme hovering over
# a permanently blank bottom row.
function __move_cursor_to_bottom
    if type -q tput
        set -l lines (tput lines 2>/dev/null)

        if string match -rq '^[0-9]+$' -- "$lines"; and test "$lines" -gt 1
            command tput cup (math "$lines - 1") 0 2>/dev/null
        end
    end
end

function clear
    command clear
    __move_cursor_to_bottom
end

if status is-interactive
    # Mention the Neovim chooser once, only while no config exists. Not a prompt:
    # a question on every new shell would be worse than no question at all.
    set -l __tl_config_home (test -n "$XDG_CONFIG_HOME"; and echo "$XDG_CONFIG_HOME"; or echo "$HOME/.config")
    if type -q setup-nvim; and not test -e "$__tl_config_home/nvim"; and not test -e "$__tl_config_home/.setup-nvim-hinted"
        echo "Neovim has no config yet — run 'setup-nvim' to pick one (AstroNvim, NvChad, LazyVim, kickstart, or stock)."
        touch "$__tl_config_home/.setup-nvim-hinted"
    end
    set -e __tl_config_home

    function fish_greeting
        command clear
        __move_cursor_to_bottom
    end

    # Oh My Posh prompt. Keep this after the Material colors are sourced.
    # The compact Aliens-derived theme follows the launcher's Material palette.
    if type -q oh-my-posh
        set -l omp_theme "$HOME/.config/ohmyposh/aliens-material.omp.json"

        if test -f "$omp_theme"
            oh-my-posh --config "$omp_theme" init fish | source
        end
    end
end
