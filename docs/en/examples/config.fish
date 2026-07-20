set -g fish_greeting ""

# Set to 1 to auto-start or attach tmux when fish opens.
set -g fish_auto_tmux 0

set -gx TMPDIR "$HOME/.tmp"
mkdir -p "$TMPDIR"

set -q COLORTERM; or set -gx COLORTERM truecolor
set -q EDITOR; or set -gx EDITOR nvim

fish_add_path "$HOME/.local/bin" "$HOME/.termux/bin"

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

# The launcher rewrites its Material exports when light/dark mode or wallpaper
# colors change. Existing shells should adopt them without being restarted.
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

    if set -q TMUX
        set -l tmux_theme "$HOME/.tmux/plugins/termux-launcher-tmux/scripts/material-theme.tmux"
        test -x "$tmux_theme"; and command "$tmux_theme" >/dev/null 2>&1
    end
end

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

if status is-interactive
    if test "$fish_auto_tmux" = 1; and type -q tmux; and not set -q TMUX
        exec tmux new-session -A -s main
    end

    if type -q oh-my-posh
        set -l omp_theme "$HOME/.config/ohmyposh/termux-launcher.omp.json"
        test -f "$omp_theme"; and oh-my-posh --config "$omp_theme" init fish | source
    end

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

    if type -q zoxide
        zoxide init --cmd cd fish | source
    end
end
