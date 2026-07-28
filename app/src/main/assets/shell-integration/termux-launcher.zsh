# Termux Launcher OSC 133 shell integration for zsh.
#
# Enable it by adding this line to ~/.zshrc:
#   source ~/.termux/shell-integration/termux-launcher.zsh
#
# This file is managed by Termux Launcher and may be replaced on app updates.

[[ -o interactive ]] || return 0
[[ ${TERMUX_LAUNCHER_ZSH_INTEGRATION_LOADED-} == 1 ]] && return 0
typeset -g TERMUX_LAUNCHER_ZSH_INTEGRATION_LOADED=1

__termux_launcher_zsh_precmd() {
    local -i command_status=$?
    emulate -L zsh -o no_aliases

    # Close the preceding command and mark the beginning of the next prompt.
    print -n -- $'\e]133;D;'${command_status}$'\a\e]133;A\a'
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
