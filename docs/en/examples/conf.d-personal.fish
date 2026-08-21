# ~/.config/fish/conf.d/personal.fish — YOUR file. Writable, edit freely, takes
# effect in the next shell (`exec fish`).
#
# setup-launcher installs this once (from the conf.d-personal.fish example) and
# never overwrites it, so edits here survive re-runs. The launcher's own
# settings live in ~/.config/fish/config.fish, which the installer does replace
# (with a timestamped .bak). Delete this file and re-run setup-launcher to get
# a fresh copy.
#
# Load order: fish reads conf.d/*.fish (alphabetically) BEFORE config.fish. So
# PATH additions and Material colors from the launcher config are not in place
# yet when this runs. Anything you want to override in config.fish must go in
# a function or an event handler, not a bare `set`.

set -q EDITOR; or set -gx EDITOR nvim
set -q VISUAL; or set -gx VISUAL $EDITOR

## ---------------------------------------------------------------------------
## ls / cd helpers. Remove the block if you prefer plain ls and cd.
## ---------------------------------------------------------------------------
if status is-interactive
    # Nix edition only: start the declarative sshd when armed via
    # `sshd-autostart on`. A no-op everywhere else.
    if test -e ~/.config/sshd/autostart; and type -q sshd-start
        sshd-start --quiet
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
#   abbr -a fishy 'nvim ~/.config/fish/conf.d/personal.fish' # edit this file
#   abbr -a termuxy 'nvim ~/.termux/termux.properties' # edit terminal settings
#   abbr -a rfish 'exec fish'                          # reload the fish configs
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
