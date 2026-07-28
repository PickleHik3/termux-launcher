# Termux Launcher OSC 133 shell integration for bash.
#
# Enable it by adding this line to ~/.bashrc:
#   source ~/.termux/shell-integration/termux-launcher.bash
#
# This file is managed by Termux Launcher and may be replaced on app updates.

[[ $- == *i* ]] || return 0
[[ ${TERMUX_LAUNCHER_BASH_INTEGRATION_LOADED-} == 1 ]] && return 0
TERMUX_LAUNCHER_BASH_INTEGRATION_LOADED=1

__termux_launcher_bash_precmd() {
    local command_status=$?

    # Close the preceding command and mark the beginning of the next prompt.
    # Return the original status so existing PROMPT_COMMAND entries still see it.
    builtin printf '\e]133;D;%d\a\e]133;A\a' "$command_status"
    return "$command_status"
}

# Install our status-capturing hook first without discarding an existing string or
# array PROMPT_COMMAND. Bash passes the preceding command's status to the first hook.
case $(builtin declare -p PROMPT_COMMAND 2>/dev/null) in
    "declare -a "*)
        PROMPT_COMMAND=(__termux_launcher_bash_precmd "${PROMPT_COMMAND[@]}")
        ;;
    *)
        PROMPT_COMMAND="__termux_launcher_bash_precmd${PROMPT_COMMAND:+; $PROMPT_COMMAND}"
        ;;
esac

# PS0 is expanded after the user presses Enter and before the command executes, so
# this mark lands on the command/output row instead of replacing the prompt mark.
PS0='\[\e]133;C\a\]'"${PS0-}"
