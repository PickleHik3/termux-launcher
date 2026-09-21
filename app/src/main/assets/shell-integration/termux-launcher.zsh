# Termux Launcher OSC 133 and OSC 7 shell integration for zsh.
#
# Enable it by adding this line to ~/.zshrc:
#   source ~/.termux/shell-integration/termux-launcher.zsh
#
# This file is managed by Termux Launcher and may be replaced on app updates.

[[ -o interactive ]] || return 0
[[ ${TERMUX_LAUNCHER_ZSH_INTEGRATION_LOADED-} == 1 ]] && return 0
typeset -g TERMUX_LAUNCHER_ZSH_INTEGRATION_LOADED=1

# Percent-encode a path, byte by byte, so a folder with a space or a non-ASCII name
# still names itself correctly. Runs in a subshell, which keeps the C locale local.
__termux_launcher_zsh_urlencode() (
    emulate -L zsh -o no_aliases
    LC_ALL=C
    local rest=$1 safe
    while [[ -n $rest ]]; do
        safe=${rest%%[^a-zA-Z0-9/:_.~-]*}
        printf '%s' "$safe"
        rest=${rest#$safe}
        if [[ -n $rest ]]; then
            printf '%%%02X' "'$rest"
            rest=${rest#?}
        fi
    done
)

# Tell the terminal which folder this shell is in, so a new pane can open in the same
# place. The plain path is sent as is; only an unusual one pays for the subshell.
__termux_launcher_zsh_report_cwd() {
    emulate -L zsh -o no_aliases
    local path=$PWD
    case $path in
        (*[^a-zA-Z0-9/:_.~-]*) path=$(__termux_launcher_zsh_urlencode "$path") ;;
    esac
    print -n -- $'\e]7;file://'${path}$'\a'
}

__termux_launcher_zsh_precmd() {
    local -i command_status=$?
    emulate -L zsh -o no_aliases

    # Close the preceding command, report the folder, and mark the beginning of the next prompt.
    print -n -- $'\e]133;D;'${command_status}$'\a'
    __termux_launcher_zsh_report_cwd
    print -n -- $'\e]133;A\a'
    return $command_status
}

__termux_launcher_zsh_preexec() {
    emulate -L zsh -o no_aliases
    print -n -- $'\e]133;C\a'
}

typeset -ga precmd_functions preexec_functions

# Run precmd last so prompt-framework output remains outside the prompt mark. Remove
# an existing entry first to make re-sourcing idempotent even if the guard is unset.
precmd_functions=(${precmd_functions:#__termux_launcher_zsh_precmd} __termux_launcher_zsh_precmd)
preexec_functions=(${preexec_functions:#__termux_launcher_zsh_preexec} __termux_launcher_zsh_preexec)

# Mark the initial prompt when this file is sourced from an already running shell.
__termux_launcher_zsh_precmd
