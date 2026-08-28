# ~/.config/fish/conf.d/personal.fish — YOUR file. Writable, edit freely, takes
# effect in the next shell (`exec fish`).
#
# This is the Termux-edition copy (com.termux, io.vaj.tl). The Nix edition ships
# its own, with nix-on-droid shortcuts in place of the package ones and the sshd
# autostart hook — see the launcher template in the nix-on-droid flake.
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
## Package shortcuts. Uncomment what you want.
## ---------------------------------------------------------------------------
#
# Termux ships apt, driven through `pkg`; the termux-pacman variant ships pacman
# instead. Guarding on which one exists keeps the same abbreviations working on
# both, so muscle memory carries across a reinstall:
#
#   if type -q pacman
#       abbr -a ii 'pacman -S --needed --noconfirm'
#       abbr -a ss 'pacman -Ss'
#       abbr -a uu 'pacman -Syu --needed --noconfirm'
#   else
#       abbr -a ii 'pkg install -y'
#       abbr -a ss 'pkg search'
#       abbr -a uu 'pkg upgrade -y'
#   end
#
#   abbr -a bb 'pkg install -y build-essential'   # compiler, for building things

## ---------------------------------------------------------------------------
## General shortcuts. Abbreviations expand as you type, so history stays useful.
## ---------------------------------------------------------------------------
#
#   abbr -a cc clear
#   abbr -a ee exit
#   abbr -a cdd 'cd ..'
#   abbr -a nn nvim
#   abbr -a py python
#   abbr -a gitc 'git clone'
#   abbr -a fishy 'nvim ~/.config/fish/conf.d/personal.fish'   # edit this file
#   abbr -a termuxy 'nvim ~/.termux/termux.properties'         # terminal settings
#   abbr -a rfish 'exec fish'                                  # reload fish
#   abbr -a rr termux-reload-settings                          # reload ~/.termux configs
#
# Functions are for anything with logic (see `y` above). Bind one to a key:
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
