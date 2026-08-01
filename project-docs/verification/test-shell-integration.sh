#!/bin/sh
# Verify the installed Termux Launcher OSC 133 integration with real bash and zsh.

set -eu

script_dir=${1:-"$HOME/.termux/shell-integration"}
export TERMUX_LAUNCHER_TEST_SCRIPT_DIR=$script_dir

command -v bash >/dev/null 2>&1 || { echo "missing bash" >&2; exit 1; }
command -v zsh >/dev/null 2>&1 || { echo "missing zsh" >&2; exit 1; }
bash -n "$script_dir/termux-launcher.bash"
zsh -n "$script_dir/termux-launcher.zsh"

bash_output=$(bash --noprofile --norc -ic '
existing_hook() { builtin printf existing-hook; }
PROMPT_COMMAND=existing_hook
source "$TERMUX_LAUNCHER_TEST_SCRIPT_DIR/termux-launcher.bash"
source "$TERMUX_LAUNCHER_TEST_SCRIPT_DIR/termux-launcher.bash"
false
eval "$PROMPT_COMMAND"
printf "\nPROMPT_COMMAND=%s\nPS0=%s\n" "$PROMPT_COMMAND" "$PS0"
' 2>/dev/null)
bash_mark=$(printf '\033]133;D;1\a\033]133;A\a')
case "$bash_output" in
    *"$bash_mark"*existing-hook*) ;;
    *) echo "bash OSC/status test failed" >&2; exit 1 ;;
esac
case "$bash_output" in
    *'PROMPT_COMMAND=__termux_launcher_bash_precmd; existing_hook'*) ;;
    *) echo "bash PROMPT_COMMAND preservation/idempotence failed" >&2; exit 1 ;;
esac
case "$bash_output" in
    *'PS0=\[\e]133;C\a\]'*) ;;
    *) echo "bash command-start mark test failed" >&2; exit 1 ;;
esac
echo "bash: OSC 133 status/prompt/command marks and hook preservation passed"

zsh_output=$(zsh -f -ic '
existing_precmd() { :; }
existing_preexec() { :; }
precmd_functions=(existing_precmd)
preexec_functions=(existing_preexec)
source "$TERMUX_LAUNCHER_TEST_SCRIPT_DIR/termux-launcher.zsh"
source "$TERMUX_LAUNCHER_TEST_SCRIPT_DIR/termux-launcher.zsh"
false
__termux_launcher_zsh_precmd
__termux_launcher_zsh_preexec
print -r -- "precmd=${(j:,:)precmd_functions}"
print -r -- "preexec=${(j:,:)preexec_functions}"
' 2>/dev/null)
zsh_prompt_mark=$(printf '\033]133;D;1\a\033]133;A\a')
zsh_command_mark=$(printf '\033]133;C\a')
case "$zsh_output" in
    *"$zsh_prompt_mark"*"$zsh_command_mark"*) ;;
    *) echo "zsh OSC/status test failed" >&2; exit 1 ;;
esac
case "$zsh_output" in
    *'precmd=existing_precmd,__termux_launcher_zsh_precmd'*) ;;
    *) echo "zsh precmd preservation/idempotence failed" >&2; exit 1 ;;
esac
case "$zsh_output" in
    *'preexec=existing_preexec,__termux_launcher_zsh_preexec'*) ;;
    *) echo "zsh preexec preservation/idempotence failed" >&2; exit 1 ;;
esac
echo "zsh: OSC 133 status/prompt/command marks and hook preservation passed"
