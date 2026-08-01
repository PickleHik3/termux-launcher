set -g fish_greeting ""

set -gx TMPDIR "$HOME/.tmp"
mkdir -p "$TMPDIR"

set -q COLORTERM; or set -gx COLORTERM truecolor
set -q EDITOR; or set -gx EDITOR nvim

fish_add_path "$HOME/.local/bin" "$HOME/.termux/bin"

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
set -q TERMUX_MATERIAL_ON_PRIMARY; or set -gx TERMUX_MATERIAL_ON_PRIMARY "#003826"
set -q TERMUX_MATERIAL_ON_SECONDARY; or set -gx TERMUX_MATERIAL_ON_SECONDARY "#1E3529"
set -q TERMUX_MATERIAL_ON_SURFACE; or set -gx TERMUX_MATERIAL_ON_SURFACE "#DEE4DE"
set -q TERMUX_MATERIAL_ON_SURFACE_VARIANT; or set -gx TERMUX_MATERIAL_ON_SURFACE_VARIANT "#C0C9C0"
set -q TERMUX_MATERIAL_PRIMARY; or set -gx TERMUX_MATERIAL_PRIMARY "#8CD5B3"
set -q TERMUX_MATERIAL_SECONDARY; or set -gx TERMUX_MATERIAL_SECONDARY "#B3CCBE"
set -q TERMUX_MATERIAL_SURFACE; or set -gx TERMUX_MATERIAL_SURFACE "#0F1512"
set -q TERMUX_MATERIAL_SURFACE_CONTAINER_HIGHEST; or set -gx TERMUX_MATERIAL_SURFACE_CONTAINER_HIGHEST "#303632"
set -q TERMUX_MATERIAL_TERTIARY; or set -gx TERMUX_MATERIAL_TERTIARY "#A5CCDF"

# Keep the prompt near the bottom of the screen after clearing.
function __move_cursor_to_bottom
    if type -q tput
        set -l lines (tput lines 2>/dev/null)

        if string match -rq '^[0-9]+$' -- "$lines"; and test "$lines" -gt 1
            command tput cup (math "$lines - 2") 0 2>/dev/null
        end
    end
end

function clear
    command clear
    __move_cursor_to_bottom
end

# yazi helper: exit yazi into the directory it was viewing.
function y
    set -l tmp (mktemp -t "yazi-cwd.XXXXXX")

    command yazi $argv --cwd-file="$tmp"

    if read -l cwd <"$tmp"; and test "$cwd" != "$PWD"; and test -d "$cwd"
        builtin cd -- "$cwd"
    end

    rm -f -- "$tmp"
end

## ---------------------------------------------------------------------------
## Quick guide: make this config yours
## ---------------------------------------------------------------------------
#
# Abbreviations expand as you type (like typing `cc` + space becoming `clear`).
# They live in your history as the expanded command, which keeps history
# useful. Uncomment any of these or add your own:
#
#   abbr -a cc clear
#   abbr -a ee exit
#   abbr -a cdd 'cd ..'
#   abbr -a nn nvim
#   abbr -a mm mkdir
#   abbr -a py python
#   abbr -a gitc 'git clone'
#   abbr -a fishy 'nvim ~/.config/fish/config.fish'    # edit this file
#   abbr -a termuxy 'nvim ~/.termux/termux.properties' # edit terminal settings
#   abbr -a rfish 'exec fish'                          # reload this config
#   abbr -a rr termux-reload-settings                  # reload ~/.termux configs
#
# Package-manager shortcuts, guarded so they only exist where pacman does:
#
#   if type -q pacman
#       abbr -a ii 'pacman -S --needed --noconfirm'
#       abbr -a ss 'pacman -Ss'
#       abbr -a uu 'pacman -Syu --needed --noconfirm'
#   end
#
# Functions are for anything with logic (see `y` above). Key bindings attach
# to any function; this one runs it on Alt+G in an interactive shell:
#
#   function __git_status
#       git status
#       commandline -f repaint
#   end
#   bind \eg __git_status
#
# Environment variables for tools you install (API keys and the like) belong
# in a separate un-shared file; source it here if it exists:
#
#   test -r ~/.config/fish/secrets.fish; and source ~/.config/fish/secrets.fish

if status is-interactive
    function fish_greeting
        command clear
        __move_cursor_to_bottom
    end

    # Oh My Posh prompt. Keep this after the Material colors are sourced.
    # Both themes ship with the launcher docs: pure-material (minimal, no
    # backgrounds) and termux-launcher (full segments).
    if type -q oh-my-posh
        set -l omp_theme "$HOME/.config/ohmyposh/pure-material.omp.json"
        test -f "$omp_theme"; or set omp_theme "$HOME/.config/ohmyposh/termux-launcher.omp.json"

        if test -f "$omp_theme"
            oh-my-posh --config "$omp_theme" init fish | source
        end
    end

    # eza replaces ls-style commands when installed.
    if type -q eza
        function ls
            command eza --group-directories-first --icons=auto $argv
        end

        function l
            command eza --group-directories-first --icons=auto $argv
        end

        function la
            command eza --all --group-directories-first --icons=auto $argv
        end

        function ll
            command eza --long --all --header --git --group-directories-first --icons=auto $argv
        end

        function lt
            command eza --tree --level=2 --group-directories-first --icons=auto $argv
        end
    end

    # zoxide powers cd; the wrapper also lists the destination after moving.
    if type -q zoxide
        zoxide init --cmd cd fish | source

        functions --erase cd
        function cd --wraps=__zoxide_z
            __zoxide_z $argv
            and ls
        end
    else
        function cd --wraps=cd
            builtin cd $argv
            and ls
        end
    end
end
