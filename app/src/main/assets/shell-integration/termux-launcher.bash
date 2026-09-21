# Termux Launcher OSC 133 and OSC 7 shell integration for bash.
#
# Enable it by adding this line to ~/.bashrc:
#   source ~/.termux/shell-integration/termux-launcher.bash
#
# This file is managed by Termux Launcher and may be replaced on app updates.

[[ $- == *i* ]] || return 0
[[ ${TERMUX_LAUNCHER_BASH_INTEGRATION_LOADED-} == 1 ]] && return 0
TERMUX_LAUNCHER_BASH_INTEGRATION_LOADED=1

# Percent-encode a path, byte by byte, so a folder with a space or a non-ASCII name
# still names itself correctly. Runs in a subshell, which keeps the C locale local.
__termux_launcher_bash_urlencode() (
    LC_ALL=C
    local rest=$1 safe
    while [[ -n $rest ]]; do
        safe=${rest%%[!a-zA-Z0-9/:_.~-]*}
        builtin printf '%s' "$safe"
        rest=${rest#"$safe"}
        if [[ -n $rest ]]; then
            builtin printf '%%%02X' "'$rest"
            rest=${rest#?}
        fi
    done
)

# Tell the terminal which folder this shell is in, so a new pane can open in the same
# place. The plain path is sent as is; only an unusual one pays for the subshell.
__termux_launcher_bash_report_cwd() {
    local path=$PWD
    case $path in
        *[!a-zA-Z0-9/:_.~-]*) path=$(__termux_launcher_bash_urlencode "$path") ;;
    esac
    builtin printf '\e]7;file://%s\a' "$path"
}

__termux_launcher_bash_precmd() {
    local command_status=$?

    # Close the preceding command, report the folder, and mark the beginning of the next
    # prompt. Return the original status so existing PROMPT_COMMAND entries still see it.
    builtin printf '\e]133;D;%d\a' "$command_status"
    __termux_launcher_bash_report_cwd
    builtin printf '\e]133;A\a'
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
