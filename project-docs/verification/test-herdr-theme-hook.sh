#!/bin/sh
# Runs the herdr theme template's apply.sh and undo.sh against fixture configs
# and checks what they own and what they leave alone:
#
#   1. a missing config gets the theme tables, auto_switch and the default
#      sidebar rows, each in a marker block;
#   2. a user's own sidebar row (claude, and spaces' rows) stays as written,
#      uncommented, with no row of ours beside it; the theme tables still
#      splice in and a user's auto_switch is commented out;
#   3. a config an earlier hook rewrote (our claude row in a marker block, the
#      user's commented out with the prefix) comes back: the user's row plain,
#      ours gone;
#   4. a second apply changes nothing;
#   5. undo puts the user's config back exactly.
#
# Usage: sh project-docs/verification/test-herdr-theme-hook.sh [awk]
# Pass `busybox` as the argument to run the hooks under busybox sh and awk,
# which is what a Termux without gawk has.
set -eu

here="$(cd "$(dirname "$0")/../.." && pwd)"
template="$here/app/src/main/assets/theme-templates/herdr"
flavour="${1:-gnu}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
export HOME="$work/home"
export XDG_CONFIG_HOME="$HOME/.config"
mkdir -p "$XDG_CONFIG_HOME/herdr"
config="$XDG_CONFIG_HOME/herdr/config.toml"
export TERMUX_THEME_OUTPUT="$XDG_CONFIG_HOME/herdr/launcher-material.toml"
export TERMUX_THEME_ID=herdr TERMUX_THEME_DIR="$template" TERMUX_THEME_MODE=dark

if [ "$flavour" = busybox ]; then
    mkdir -p "$work/bin"
    for tool in sh awk; do
        printf '#!/bin/sh\nexec busybox %s "$@"\n' "$tool" >"$work/bin/$tool"
        chmod +x "$work/bin/$tool"
    done
    PATH="$work/bin:$PATH"
fi
# herdr itself must not be reached from the probe.
PATH="$work/nobin:$PATH"; export PATH

# The rendered file, as the launcher writes it: one value per line. The
# launcher renders it before every apply, and undo removes it, so apply()
# copies it into place each time.
cat >"$work/rendered.toml" <<'EOF'
# rendered
[theme.custom.dark]
accent        = "#005eb1"
panel_bg      = "reset"

[theme.custom.light]
accent        = "#d5e3ff"
panel_bg      = "reset"

[ui.sidebar.spaces]
rows = [["state_icon", { token = "workspace", fg = "#a6c8ff", bold = true }], [{ token = "branch", fg = "#c6c0f9" }]]

[ui.sidebar.agents]
rows = [["state_icon", { token = "terminal_title_stripped", fg = "#a6c8ff", bold = true }], [{ token = "machine", fg = "#51a4b2" }, { token = "agent", fg = "#c6c0f9" }]]

[ui.sidebar.agents.rows_by_agent]
claude = [["state_icon", { token = "terminal_title_stripped", fg = "#a6c8ff", bold = true }], [{ token = "machine", fg = "#51a4b2" }, { token = "agent", fg = "#c6c0f9" }]]
EOF

fail() { echo "FAIL: $1"; echo "--- config.toml:"; cat "$config"; exit 1; }
has() { grep -qF -- "$1" "$config" || fail "expected: $1"; }
lacks() { if grep -qF -- "$1" "$config"; then fail "did not expect: $1"; fi; }
apply() { cp "$work/rendered.toml" "$TERMUX_THEME_OUTPUT"; sh "$template/apply.sh"; }
undo() { sh "$template/undo.sh"; }

# 1. missing config
rm -f "$config"
apply
has '[theme]'
has 'auto_switch = true'
has 'accent        = "#005eb1"'
has '[ui.sidebar.agents.rows_by_agent]'
has 'claude = [["state_icon", { token = "terminal_title_stripped"'
has 'rows = [["state_icon", { token = "workspace"'
echo "ok 1 missing config gets theme tables, auto_switch and default rows"

# 2. the user's own rows stay theirs
cat >"$config" <<'EOF'
onboarding = false

[ui]
accent = "blue"

[ui.sidebar.spaces]
rows = [["state_icon", "tab"]]

# Row two is the project folder in a muted teal.
[ui.sidebar.agents.rows_by_agent]
claude = [["state_icon", "tab"], [{ token = "workspace", fg = "#5fb3b3" }]]

[theme]
name = "catppuccin"
auto_switch = false

[experimental]
kitty_graphics = true
EOF
cp "$config" "$work/user.toml"
apply
has 'claude = [["state_icon", "tab"], [{ token = "workspace", fg = "#5fb3b3" }]]'
lacks '# launcher-material: claude'
lacks 'claude = [["state_icon", { token = "terminal_title_stripped"'
has 'rows = [["state_icon", "tab"]]'
lacks 'rows = [["state_icon", { token = "workspace"'
has 'rows = [["state_icon", { token = "terminal_title_stripped"'
has 'name = "catppuccin"'
has '# launcher-material: auto_switch = false'
has 'auto_switch = true'
has 'accent        = "#d5e3ff"'
[ "$(grep -c '^\[ui.sidebar.agents.rows_by_agent\]' "$config")" = 1 ] || fail "rows_by_agent declared more than once"
[ "$(grep -c '^\[ui.sidebar.spaces\]' "$config")" = 1 ] || fail "spaces declared more than once"
echo "ok 2 user rows stay, theme tables splice, auto_switch is ours"

# 4. idempotent
cp "$config" "$work/once.toml"
apply
cmp -s "$config" "$work/once.toml" || fail "second apply changed the config"
echo "ok 4 second apply changes nothing"

# 5. undo restores the user's config exactly
undo
cmp -s "$config" "$work/user.toml" || fail "undo did not restore the user's config"
echo "ok 5 undo restores the config"

# 3. a config an earlier hook rewrote
cat >"$config" <<'EOF'
onboarding = false

[ui.sidebar.agents.rows_by_agent]
# >>> launcher-material >>>
claude = [["state_icon", { token = "terminal_title_stripped", fg = "#a6c8ff", bold = true }], [{ token = "machine", fg = "#51a4b2" }, { token = "agent", fg = "#c6c0f9" }]]
# <<< launcher-material <<<
# launcher-material: claude = [["state_icon", "tab"], [{ token = "workspace", fg = "#5fb3b3" }]]

[experimental]
kitty_graphics = true
# >>> launcher-material >>>
[theme]
auto_switch = true
# <<< launcher-material <<<
EOF
apply
has 'claude = [["state_icon", "tab"], [{ token = "workspace", fg = "#5fb3b3" }]]'
lacks '# launcher-material: claude'
lacks 'claude = [["state_icon", { token = "terminal_title_stripped"'
has 'auto_switch = true'
has 'rows = [["state_icon", { token = "workspace"'
[ "$(grep -c '^\[ui.sidebar.agents.rows_by_agent\]' "$config")" = 1 ] || fail "rows_by_agent declared more than once"
echo "ok 3 a row an earlier hook commented out comes back, ours stands aside"

# 3b. a multi-line user row, commented out by the earlier hook, comes back whole
cat >"$config" <<'EOF'
[ui.sidebar.agents.rows_by_agent]
# >>> launcher-material >>>
claude = [["state_icon", { token = "terminal_title_stripped", fg = "#a6c8ff", bold = true }]]
# <<< launcher-material <<<
# launcher-material: claude = [
# launcher-material:   ["state_icon", "tab"],
# launcher-material:   [{ token = "workspace", fg = "#5fb3b3" }],
# launcher-material: ]
EOF
apply
has 'claude = ['
has '  [{ token = "workspace", fg = "#5fb3b3" }],'
lacks '# launcher-material:'
lacks 'claude = [["state_icon", { token = "terminal_title_stripped"'
has 'rows = [["state_icon", { token = "terminal_title_stripped"'
echo "ok 3b a multi-line row comes back whole"

echo "all herdr hook checks passed ($flavour)"
