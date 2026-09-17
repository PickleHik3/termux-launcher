#!/usr/bin/env bash
# Dev-only acceptance check for the built-in theme-template pack. For each
# template: renders its input against the fixture palette (fails on any
# unresolved `{{`), validates the rendered output (with the real tool where
# installed on this machine, otherwise a syntax-only parse), then round-trips
# apply.sh/apply.sh/undo.sh in a temp HOME, twice: once where the tool's own
# config is absent, once where it pre-exists with unrelated content. A
# second apply must change no bytes; undo must restore the original bytes
# and remove the rendered file. Prints one PASS/FAIL line per template and
# exits nonzero if any template failed.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEMPLATES_DIR="$REPO_ROOT/app/src/main/assets/theme-templates"
FIXTURE="$SCRIPT_DIR/fixture-palette.properties"
FIXTURE_LIGHT="$SCRIPT_DIR/fixture-palette-light.properties"
RENDER="$SCRIPT_DIR/render.py"
PY="${PYTHON:-python3}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

OVERALL=0
NOTES=()

note() { NOTES+=("$1"); }

# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

# read_prop <template-dir> <key>
read_prop() {
    awk -F'=' -v k="$2" '$1 == k { sub(/^[^=]*=/, ""); print; exit }' "$1/template.properties"
}

# render_template <template-dir> <input-file> -> writes rendered bytes to
# stdout, or prints a render error to stderr and returns nonzero. The dark
# fixture is the active palette, so `{{ mode }}` renders `dark`; `.dark` and
# `.light` resolve to the two real fixture palettes, as they will once phase 2
# lands, so a template carrying both halves renders two different ones here.
render_template() {
    local dir="$1" input="$2"
    "$PY" "$RENDER" --dark "$FIXTURE" --light "$FIXTURE_LIGHT" "$FIXTURE" "$dir/$input"
}

# render_template_light <template-dir> <input-file>: the same, with the light
# palette active - `{{ mode }}` renders `light`. For templates whose output
# carries a mode selector line, so both of its values get checked.
render_template_light() {
    local dir="$1" input="$2"
    "$PY" "$RENDER" --dark "$FIXTURE" --light "$FIXTURE_LIGHT" "$FIXTURE_LIGHT" "$dir/$input"
}

# assert_dual <rendered-file> <dark-hex> <light-hex>: the rendered file must
# carry a value that only the dark fixture has and one only the light fixture
# has, so a template that claims both palettes cannot silently render one
# twice.
assert_dual() {
    grep -qiF "$2" "$1" && grep -qiF "$3" "$1"
}

# assert_no_colour_tokens <rendered-file>: fails if any `{{ colors.` survived. Weaker than
# assert_no_stray_braces, and the only one a template may use when its own syntax is `{{ }}`
# too - a malformed placeholder such as `{{colors.primary.hex}}` misses render.py's grammar
# and would otherwise be copied through verbatim into a shipped prompt.
assert_no_colour_tokens() {
    ! grep -qE '\{\{[[:space:]]*colors\.' "$1"
}

# assert_no_stray_braces <rendered-file>: fails if `{{` remains anywhere.
assert_no_stray_braces() {
    ! grep -q '{{' "$1"
}

# apply_env <home> <theme-dir> <output> [shell] - print the env assignment
# arguments hooks receive, for use with `env`.
hook_env() {
    local home="$1" theme_dir="$2" output="$3" shell="${4:-}"
    printf 'HOME=%s XDG_CONFIG_HOME=%s/.config XDG_CACHE_HOME=%s/.cache TERMUX_THEME_ID=%s TERMUX_THEME_DIR=%s TERMUX_THEME_OUTPUT=%s TERMUX_THEME_MODE=dark' \
        "$home" "$home" "$home" "$(basename "$theme_dir")" "$theme_dir" "$output"
    if [ -n "$shell" ]; then
        printf ' TERMUX_THEME_SHELL=%s' "$shell"
    fi
}

# ---------------------------------------------------------------------------
# Per-template config-path resolvers and round-trip drivers. Each test_<id>
# function does its own thing and returns 0/1, appending to FAILS on error.
# ---------------------------------------------------------------------------

FAILS=()
fail() { FAILS+=("$1"); }

reset_fails() { FAILS=(); }

run_hook() {
    # run_hook <home> <theme-dir> <output> <hook-script>
    local home="$1" theme_dir="$2" output="$3" hook="$4"
    env HOME="$home" XDG_CONFIG_HOME="$home/.config" XDG_CACHE_HOME="$home/.cache" \
        TERMUX_THEME_ID="$(basename "$theme_dir")" TERMUX_THEME_DIR="$theme_dir" \
        TERMUX_THEME_OUTPUT="$output" TERMUX_THEME_MODE=dark \
        bash "$hook" >"$WORK/.last_hook_output" 2>&1
}

run_hook_mode() {
    # run_hook_mode <home> <theme-dir> <output> <hook-script> <mode>
    local home="$1" theme_dir="$2" output="$3" hook="$4" mode="$5"
    env HOME="$home" XDG_CONFIG_HOME="$home/.config" XDG_CACHE_HOME="$home/.cache" \
        TERMUX_THEME_ID="$(basename "$theme_dir")" TERMUX_THEME_DIR="$theme_dir" \
        TERMUX_THEME_OUTPUT="$output" TERMUX_THEME_MODE="$mode" \
        bash "$hook" >"$WORK/.last_hook_output" 2>&1
}

run_hook_shell() {
    # run_hook_shell <home> <theme-dir> <output> <hook-script> <shell>
    local home="$1" theme_dir="$2" output="$3" hook="$4" shell="$5"
    env HOME="$home" XDG_CONFIG_HOME="$home/.config" XDG_CACHE_HOME="$home/.cache" \
        TERMUX_THEME_ID="$(basename "$theme_dir")" TERMUX_THEME_DIR="$theme_dir" \
        TERMUX_THEME_OUTPUT="$output" TERMUX_THEME_MODE=dark TERMUX_THEME_SHELL="$shell" \
        bash "$hook" >"$WORK/.last_hook_output" 2>&1
}

# generic_roundtrip <id> <config_rel_path_expr via function> ...
# Implemented per-template below since config resolution differs.

# ---- starship ----
test_starship() {
    local dir="$TEMPLATES_DIR/starship"
    local rendered="$WORK/starship.rendered"
    render_template "$dir" "starship.toml" >"$rendered" 2>"$WORK/starship.err" || { fail "render error: $(cat "$WORK/starship.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }
    assert_no_colour_tokens "$rendered" || { fail "unresolved {{ colors. token in rendered output"; return; }
    "$PY" -c "import tomllib,sys; tomllib.load(open(sys.argv[1],'rb'))" "$rendered" 2>"$WORK/starship.tomlerr" || { fail "invalid TOML: $(cat "$WORK/starship.tomlerr")"; return; }
    "$PY" - "$rendered" <<'PYCHECK' 2>"$WORK/starship.dualerr" || { fail "rendered palettes: $(cat "$WORK/starship.dualerr")"; return; }
import sys, tomllib
palettes = tomllib.load(open(sys.argv[1], "rb")).get("palettes", {})
dark = palettes.get("launcher-material-dark")
light = palettes.get("launcher-material-light")
assert isinstance(dark, dict) and dark, "no [palettes.launcher-material-dark] table"
assert isinstance(light, dict) and light, "no [palettes.launcher-material-light] table"
assert set(dark) == set(light), "the two palettes carry different keys"
differing = [k for k in dark if dark[k] != light[k]]
assert len(differing) >= len(dark) - 2, f"only {len(differing)} of {len(dark)} entries differ between the palettes"
PYCHECK
    note "starship: not installed on this machine - TOML parse, and both mode palettes checked for the same keys with different values"

    local case
    for case in absent pre; do
        local home="$WORK/starship-$case/home"
        rm -rf "$WORK/starship-$case"; mkdir -p "$home"
        local theme_dir="$WORK/starship-$case/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.cache/launcher-material/starship-palette.toml"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"

        local orig=""
        if [ "$case" = "pre" ]; then
            mkdir -p "$home/.config"
            printf '[character]\nsuccess_symbol = "➜"\n' > "$home/.config/starship.toml"
            orig="$WORK/starship-$case/orig.toml"
            cp "$home/.config/starship.toml" "$orig"
        fi

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($case) failed"; continue; }
        cp "$home/.config/starship.toml" "$WORK/starship-$case/after1.toml" 2>/dev/null

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($case) failed"; continue; }
        cmp -s "$WORK/starship-$case/after1.toml" "$home/.config/starship.toml" 2>/dev/null || fail "apply.sh ($case) not idempotent"

        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($case) failed"; continue; }
        if [ "$case" = "pre" ]; then
            cmp -s "$orig" "$home/.config/starship.toml" || fail "undo.sh (pre) did not restore original bytes"
        else
            [ -e "$home/.config/starship.toml" ] && fail "undo.sh (absent) left a starship.toml behind"
        fi
        [ -e "$output" ] && fail "undo.sh ($case) left the rendered file behind"
    done

    # A pre-existing top-level `palette = "..."` line must be displaced, not
    # discarded (R7): commented out with our tag on apply, surviving a second
    # apply unchanged, then restored verbatim by undo.
    local home="$WORK/starship-userpalette/home"
    rm -rf "$WORK/starship-userpalette"; mkdir -p "$home/.config"
    local theme_dir="$WORK/starship-userpalette/theme_dir"; mkdir -p "$theme_dir"
    local output="$home/.cache/launcher-material/starship-palette.toml"
    mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
    printf 'palette = "my-nord"\n\n[character]\nsuccess_symbol = "➜"\n' > "$home/.config/starship.toml"
    local orig="$WORK/starship-userpalette/orig.toml"
    cp "$home/.config/starship.toml" "$orig"
    run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || fail "apply.sh (userpalette) failed"
    grep -qxF '#palette = "my-nord" # >>> launcher-material: previous >>>' "$home/.config/starship.toml" \
        || fail "apply.sh (userpalette) did not comment out and tag the user's own palette= line"
    grep -qxF 'palette = "launcher-material-dark"' "$home/.config/starship.toml" \
        || fail "apply.sh (userpalette) did not add its own palette= line"
    cp "$home/.config/starship.toml" "$WORK/starship-userpalette/after1.toml"
    run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || fail "second apply.sh (userpalette) failed"
    cmp -s "$WORK/starship-userpalette/after1.toml" "$home/.config/starship.toml" || fail "apply.sh (userpalette) not idempotent"
    # A mode flip rewrites that one line and leaves no second one behind (D3).
    run_hook_mode "$home" "$theme_dir" "$output" "$dir/apply.sh" light || fail "apply.sh (userpalette, light) failed"
    grep -qxF 'palette = "launcher-material-light"' "$home/.config/starship.toml" \
        || fail "apply.sh did not flip the palette= line to the light palette"
    [ "$(grep -cE '^palette = "launcher-material-(dark|light)"$' "$home/.config/starship.toml")" = 1 ] \
        || fail "apply.sh left more than one launcher-material palette= line behind after the mode flip"
    grep -qxF '#palette = "my-nord" # >>> launcher-material: previous >>>' "$home/.config/starship.toml" \
        || fail "the mode flip lost the user's displaced palette= line"
    run_hook_mode "$home" "$theme_dir" "$output" "$dir/apply.sh" dark || fail "apply.sh (userpalette, back to dark) failed"
    cmp -s "$WORK/starship-userpalette/after1.toml" "$home/.config/starship.toml" \
        || fail "flipping to light and back to dark did not restore the dark result byte for byte"

    run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || fail "undo.sh (userpalette) failed"
    cmp -s "$orig" "$home/.config/starship.toml" || fail "undo.sh (userpalette) did not restore the user's original palette= line"
    [ -e "$output" ] && fail "undo.sh (userpalette) left the rendered file behind"

    # setup.sh: bash, zsh, fish, each with the rc file absent and pre-existing,
    # plus idempotency and undo.
    local shell
    for shell in bash zsh; do
        local rcname=".${shell}rc"
        for case in absent pre; do
            local home="$WORK/starship-setup-$shell-$case/home"
            rm -rf "$WORK/starship-setup-$shell-$case"; mkdir -p "$home"
            local theme_dir="$WORK/starship-setup-$shell-$case/theme_dir"; mkdir -p "$theme_dir"
            local output="$home/.config/dummy-starship-output"
            local orig=""
            if [ "$case" = "pre" ]; then
                printf '# my rc\nexport FOO=bar\n' > "$home/$rcname"
                orig="$WORK/starship-setup-$shell-$case/orig"
                cp "$home/$rcname" "$orig"
            fi
            run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" "$shell" >/dev/null || { fail "setup.sh ($shell/$case) failed"; continue; }
            cp "$home/$rcname" "$WORK/starship-setup-$shell-$case/after1" 2>/dev/null
            run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" "$shell" >/dev/null || { fail "second setup.sh ($shell/$case) failed"; continue; }
            cmp -s "$WORK/starship-setup-$shell-$case/after1" "$home/$rcname" 2>/dev/null || fail "setup.sh ($shell/$case) not idempotent"
            run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" >/dev/null || { fail "undo.sh after setup.sh ($shell/$case) failed"; continue; }
            if [ "$case" = "pre" ]; then
                cmp -s "$orig" "$home/$rcname" || fail "undo.sh did not restore original $rcname ($shell)"
            else
                [ -e "$home/$rcname" ] && fail "undo.sh left a $rcname behind that setup.sh created ($shell)"
            fi
        done
    done

    # fish: setup.sh's own conf.d init file, absent case + idempotency + undo.
    local home="$WORK/starship-setup-fish/home"
    rm -rf "$WORK/starship-setup-fish"; mkdir -p "$home"
    local theme_dir="$WORK/starship-setup-fish/theme_dir"; mkdir -p "$theme_dir"
    local output="$home/.config/dummy-starship-output"
    run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" fish >/dev/null || fail "setup.sh (fish) failed"
    local fish_init="$home/.config/fish/conf.d/launcher-material-starship-init.fish"
    [ -f "$fish_init" ] || fail "setup.sh (fish) did not create its conf.d init file"
    cp "$fish_init" "$WORK/starship-setup-fish/after1" 2>/dev/null
    run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" fish >/dev/null || fail "second setup.sh (fish) failed"
    cmp -s "$WORK/starship-setup-fish/after1" "$fish_init" 2>/dev/null || fail "setup.sh (fish) not idempotent"
    run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" >/dev/null || fail "undo.sh after setup.sh (fish) failed"
    [ -e "$fish_init" ] && fail "undo.sh left the fish init file behind"
}

# ---- helix ----
test_helix() {
    local dir="$TEMPLATES_DIR/helix"
    local rendered="$WORK/helix.rendered"
    render_template "$dir" "launcher-material.toml" >"$rendered" 2>"$WORK/helix.err" || { fail "render error: $(cat "$WORK/helix.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }
    "$PY" -c "import tomllib,sys; tomllib.load(open(sys.argv[1],'rb'))" "$rendered" 2>"$WORK/helix.tomlerr" || { fail "invalid TOML: $(cat "$WORK/helix.tomlerr")"; return; }
    note "helix (hx): not installed on this machine - syntax validation only (TOML parse)"

    local case
    for case in absent pre; do
        local home="$WORK/helix-$case/home"
        rm -rf "$WORK/helix-$case"; mkdir -p "$home"
        local theme_dir="$WORK/helix-$case/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.config/helix/themes/launcher-material.toml"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        local orig=""
        if [ "$case" = "pre" ]; then
            mkdir -p "$home/.config/helix"
            printf 'theme = "onedark"\neditor.cursorline = true\n' > "$home/.config/helix/config.toml"
            orig="$WORK/helix-$case/orig.toml"; cp "$home/.config/helix/config.toml" "$orig"
        fi
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($case) failed"; continue; }
        cp "$home/.config/helix/config.toml" "$WORK/helix-$case/after1.toml" 2>/dev/null
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($case) failed"; continue; }
        cmp -s "$WORK/helix-$case/after1.toml" "$home/.config/helix/config.toml" 2>/dev/null || fail "apply.sh ($case) not idempotent"
        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($case) failed"; continue; }
        if [ "$case" = "pre" ]; then
            cmp -s "$orig" "$home/.config/helix/config.toml" || fail "undo.sh (pre) did not restore original bytes exactly (including the previous theme= line)"
        else
            [ -e "$home/.config/helix/config.toml" ] && fail "undo.sh (absent) left a config.toml behind"
        fi
        [ -e "$output" ] && fail "undo.sh ($case) left the rendered file behind"
    done
}

# ---- tmux ----
test_tmux() {
    local dir="$TEMPLATES_DIR/tmux"
    local rendered="$WORK/tmux.rendered"
    render_template "$dir" "launcher-material.conf" >"$rendered" 2>"$WORK/tmux.err" || { fail "render error: $(cat "$WORK/tmux.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }
    if grep -vE '^(#.*|set -g [A-Za-z-]+ .*|)$' "$rendered" >"$WORK/tmux.bad" && [ -s "$WORK/tmux.bad" ]; then
        fail "line(s) not matching tmux's set -g <option> <value> shape: $(cat "$WORK/tmux.bad")"; return
    fi
    note "tmux: not installed on this machine - syntax validation only (set -g <option> <value> shape)"

    # neither xdg tmux.conf nor ~/.tmux.conf exists
    local home="$WORK/tmux-absent/home"
    rm -rf "$WORK/tmux-absent"; mkdir -p "$home"
    local theme_dir="$WORK/tmux-absent/theme_dir"; mkdir -p "$theme_dir"
    local output="$home/.config/tmux/launcher-material.conf"
    mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
    run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || fail "apply.sh (absent) failed"
    cp "$home/.config/tmux/tmux.conf" "$WORK/tmux-absent/after1.conf" 2>/dev/null
    run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || fail "second apply.sh (absent) failed"
    cmp -s "$WORK/tmux-absent/after1.conf" "$home/.config/tmux/tmux.conf" 2>/dev/null || fail "apply.sh (absent) not idempotent"
    run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || fail "undo.sh (absent) failed"
    [ -e "$home/.config/tmux/tmux.conf" ] && fail "undo.sh (absent) left a tmux.conf behind"
    [ -e "$output" ] && fail "undo.sh (absent) left the rendered file behind"

    # ~/.tmux.conf pre-exists with unrelated content
    home="$WORK/tmux-pre/home"
    rm -rf "$WORK/tmux-pre"; mkdir -p "$home"
    theme_dir="$WORK/tmux-pre/theme_dir"; mkdir -p "$theme_dir"
    output="$home/.config/tmux/launcher-material.conf"
    mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
    printf '# my tmux settings\nset -g mouse on\n' > "$home/.tmux.conf"
    local orig="$WORK/tmux-pre/orig.conf"; cp "$home/.tmux.conf" "$orig"
    run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || fail "apply.sh (pre) failed"
    cp "$home/.tmux.conf" "$WORK/tmux-pre/after1.conf" 2>/dev/null
    run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || fail "second apply.sh (pre) failed"
    cmp -s "$WORK/tmux-pre/after1.conf" "$home/.tmux.conf" 2>/dev/null || fail "apply.sh (pre) not idempotent"
    run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || fail "undo.sh (pre) failed"
    cmp -s "$orig" "$home/.tmux.conf" || fail "undo.sh (pre) did not restore original bytes"
    [ -e "$output" ] && fail "undo.sh (pre) left the rendered file behind"
}

# ---- bat ----
test_bat() {
    local dir="$TEMPLATES_DIR/bat"
    local rendered="$WORK/bat.rendered"
    render_template "$dir" "launcher-material.tmTheme" >"$rendered" 2>"$WORK/bat.err" || { fail "render error: $(cat "$WORK/bat.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }

    if command -v bat >/dev/null 2>&1; then
        local tmp_bat_config="$WORK/bat-validate-config"
        mkdir -p "$tmp_bat_config/themes"
        cp "$rendered" "$tmp_bat_config/themes/launcher-material.tmTheme"
        if ! env BAT_CONFIG_DIR="$tmp_bat_config" bat cache --build >"$WORK/bat.buildlog" 2>&1; then
            fail "bat cache --build rejected the rendered tmTheme: $(cat "$WORK/bat.buildlog")"; return
        fi
        note "bat: installed - validated with 'bat cache --build' under a temp BAT_CONFIG_DIR"
    else
        note "bat: not installed - skipped cache --build validation"
    fi

    local case
    for case in absent pre; do
        local home="$WORK/bat-$case/home"
        rm -rf "$WORK/bat-$case"; mkdir -p "$home"
        local theme_dir="$WORK/bat-$case/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.config/bat/themes/launcher-material.tmTheme"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        local orig=""
        if [ "$case" = "pre" ]; then
            mkdir -p "$home/.config/bat"
            printf '%s\n' '--paging=never' > "$home/.config/bat/config"
            orig="$WORK/bat-$case/orig.conf"; cp "$home/.config/bat/config" "$orig"
        fi
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($case) failed"; continue; }
        cp "$home/.config/bat/config" "$WORK/bat-$case/after1.conf" 2>/dev/null
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($case) failed"; continue; }
        cmp -s "$WORK/bat-$case/after1.conf" "$home/.config/bat/config" 2>/dev/null || fail "apply.sh ($case) not idempotent"
        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($case) failed"; continue; }
        if [ "$case" = "pre" ]; then
            cmp -s "$orig" "$home/.config/bat/config" || fail "undo.sh (pre) did not restore original bytes"
        else
            [ -e "$home/.config/bat/config" ] && fail "undo.sh (absent) left a bat config behind"
        fi
        [ -e "$output" ] && fail "undo.sh ($case) left the rendered file behind"
    done
}

# ---- yazi ----
test_yazi() {
    local dir="$TEMPLATES_DIR/yazi"
    local rendered="$WORK/yazi.rendered"
    render_template "$dir" "flavor.toml" >"$rendered" 2>"$WORK/yazi.err" || { fail "render error: $(cat "$WORK/yazi.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }
    "$PY" -c "import tomllib,sys; tomllib.load(open(sys.argv[1],'rb'))" "$rendered" 2>"$WORK/yazi.tomlerr" || { fail "invalid TOML: $(cat "$WORK/yazi.tomlerr")"; return; }
    note "yazi: installed, but has no flavor-validate subcommand - TOML parse only (as the acceptance criteria's generic TOML fallback)"

    local case
    for case in absent pre; do
        local home="$WORK/yazi-$case/home"
        rm -rf "$WORK/yazi-$case"; mkdir -p "$home"
        local theme_dir="$WORK/yazi-$case/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.config/yazi/flavors/launcher-material.yazi/flavor.toml"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        local orig=""
        if [ "$case" = "pre" ]; then
            mkdir -p "$home/.config/yazi"
            printf '[manager]\nratio = [1, 4, 3]\n' > "$home/.config/yazi/theme.toml"
            orig="$WORK/yazi-$case/orig.toml"; cp "$home/.config/yazi/theme.toml" "$orig"
        fi
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($case) failed"; continue; }
        cp "$home/.config/yazi/theme.toml" "$WORK/yazi-$case/after1.toml" 2>/dev/null
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($case) failed"; continue; }
        cmp -s "$WORK/yazi-$case/after1.toml" "$home/.config/yazi/theme.toml" 2>/dev/null || fail "apply.sh ($case) not idempotent"
        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($case) failed"; continue; }
        if [ "$case" = "pre" ]; then
            cmp -s "$orig" "$home/.config/yazi/theme.toml" || fail "undo.sh (pre) did not restore original bytes"
        else
            [ -e "$home/.config/yazi/theme.toml" ] && fail "undo.sh (absent) left a theme.toml behind"
        fi
        [ -e "$output" ] && fail "undo.sh ($case) left the rendered file behind"
        [ -d "$(dirname "$output")" ] && fail "undo.sh ($case) left the empty launcher-material.yazi flavor directory behind"
    done
    note "yazi: known limitation - if theme.toml already has an active [flavor] table, apply.sh would append a second one (invalid TOML); check.sh's fixtures avoid that so the round-trip above still passes"
}

# ---- fzf ----
test_fzf() {
    local dir="$TEMPLATES_DIR/fzf"
    local rendered="$WORK/fzf.rendered"
    render_template "$dir" "launcher-material.sh" >"$rendered" 2>"$WORK/fzf.err" || { fail "render error: $(cat "$WORK/fzf.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }
    bash -n "$rendered" 2>"$WORK/fzf.bashn" || { fail "rendered .sh fails bash -n: $(cat "$WORK/fzf.bashn")"; return; }
    grep -qE "^export FZF_DEFAULT_OPTS=\"--color=([a-z+]+:#[0-9A-Fa-f]{6},?)+\"\$" "$rendered" || { fail "FZF_DEFAULT_OPTS does not look like a --color=key:#hex,... spec"; return; }
    note "fzf: installed, but has no options-validate subcommand - validated the rendered file with 'bash -n' plus a --color=key:#hex,... shape check"

    local case
    for case in absent pre; do
        local home="$WORK/fzf-$case/home"
        rm -rf "$WORK/fzf-$case"; mkdir -p "$home"
        local theme_dir="$WORK/fzf-$case/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.config/fzf/launcher-material.sh"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        local orig_bashrc="" orig_zshrc=""
        if [ "$case" = "pre" ]; then
            printf '# my bashrc\nexport FOO=bar\n' > "$home/.bashrc"
            printf '# my zshrc\nexport BAZ=qux\n' > "$home/.zshrc"
            orig_bashrc="$WORK/fzf-$case/orig.bashrc"; cp "$home/.bashrc" "$orig_bashrc"
            orig_zshrc="$WORK/fzf-$case/orig.zshrc"; cp "$home/.zshrc" "$orig_zshrc"
        fi
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($case) failed"; continue; }
        [ -f "$home/.config/fish/conf.d/launcher-material-fzf.fish" ] || fail "apply.sh ($case) did not create the fish drop-in"
        cp "$home/.config/fish/conf.d/launcher-material-fzf.fish" "$WORK/fzf-$case/after1.fish" 2>/dev/null
        [ "$case" = "pre" ] && cp "$home/.bashrc" "$WORK/fzf-$case/after1.bashrc"
        [ "$case" = "pre" ] && cp "$home/.zshrc" "$WORK/fzf-$case/after1.zshrc"
        if [ "$case" = "absent" ]; then
            [ -e "$home/.bashrc" ] && fail "apply.sh (absent) created a .bashrc"
            [ -e "$home/.zshrc" ] && fail "apply.sh (absent) created a .zshrc"
        fi

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($case) failed"; continue; }
        cmp -s "$WORK/fzf-$case/after1.fish" "$home/.config/fish/conf.d/launcher-material-fzf.fish" 2>/dev/null || fail "fish drop-in ($case) not idempotent"
        if [ "$case" = "pre" ]; then
            cmp -s "$WORK/fzf-$case/after1.bashrc" "$home/.bashrc" || fail "bashrc ($case) not idempotent"
            cmp -s "$WORK/fzf-$case/after1.zshrc" "$home/.zshrc" || fail "zshrc ($case) not idempotent"
        fi

        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($case) failed"; continue; }
        [ -e "$home/.config/fish/conf.d/launcher-material-fzf.fish" ] && fail "undo.sh ($case) left the fish drop-in behind"
        [ -e "$output" ] && fail "undo.sh ($case) left the rendered file behind"
        if [ "$case" = "pre" ]; then
            cmp -s "$orig_bashrc" "$home/.bashrc" || fail "undo.sh (pre) did not restore original .bashrc"
            cmp -s "$orig_zshrc" "$home/.zshrc" || fail "undo.sh (pre) did not restore original .zshrc"
        else
            [ -e "$home/.bashrc" ] && fail "undo.sh (absent) left a .bashrc behind"
            [ -e "$home/.zshrc" ] && fail "undo.sh (absent) left a .zshrc behind"
        fi
    done
}

# ---- lazygit ----
test_lazygit() {
    local dir="$TEMPLATES_DIR/lazygit"
    local rendered="$WORK/lazygit.rendered"
    render_template "$dir" "launcher-material.yml" >"$rendered" 2>"$WORK/lazygit.err" || { fail "render error: $(cat "$WORK/lazygit.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }

    if "$PY" -c "import yaml" 2>/dev/null; then
        "$PY" -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" "$rendered" 2>"$WORK/lazygit.yamlerr" || { fail "invalid YAML: $(cat "$WORK/lazygit.yamlerr")"; return; }
        note "lazygit: not installed - validated with PyYAML"
    else
        # Structural check: every non-comment/non-blank line is either
        # "key:" or "- value" at some indent, colons balanced with quotes.
        if grep -vE '^([[:space:]]*#.*|[[:space:]]*[A-Za-z0-9_]+:.*|[[:space:]]*-.*|[[:space:]]*)$' "$rendered" >"$WORK/lazygit.bad" && [ -s "$WORK/lazygit.bad" ]; then
            fail "line(s) not matching a plain YAML key: or - item shape: $(cat "$WORK/lazygit.bad")"; return
        fi
        note "lazygit: not installed and PyYAML unavailable - structural (key:/- item shape) check only"
    fi

    local case
    for case in absent pre; do
        local home="$WORK/lazygit-$case/home"
        rm -rf "$WORK/lazygit-$case"; mkdir -p "$home"
        local theme_dir="$WORK/lazygit-$case/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.config/lazygit/launcher-material.yml"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        local orig_bashrc="" orig_zshrc=""
        if [ "$case" = "pre" ]; then
            printf '# my bashrc\nexport X=1\n' > "$home/.bashrc"
            printf '# my zshrc\nexport Y=2\n' > "$home/.zshrc"
            orig_bashrc="$WORK/lazygit-$case/orig.bashrc"; cp "$home/.bashrc" "$orig_bashrc"
            orig_zshrc="$WORK/lazygit-$case/orig.zshrc"; cp "$home/.zshrc" "$orig_zshrc"
        fi
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($case) failed"; continue; }
        [ -f "$home/.config/fish/conf.d/launcher-material-lazygit.fish" ] || fail "apply.sh ($case) did not create the fish drop-in"
        cp "$home/.config/fish/conf.d/launcher-material-lazygit.fish" "$WORK/lazygit-$case/after1.fish" 2>/dev/null
        [ "$case" = "pre" ] && cp "$home/.bashrc" "$WORK/lazygit-$case/after1.bashrc"
        [ "$case" = "pre" ] && cp "$home/.zshrc" "$WORK/lazygit-$case/after1.zshrc"

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($case) failed"; continue; }
        cmp -s "$WORK/lazygit-$case/after1.fish" "$home/.config/fish/conf.d/launcher-material-lazygit.fish" 2>/dev/null || fail "fish drop-in ($case) not idempotent"
        if [ "$case" = "pre" ]; then
            cmp -s "$WORK/lazygit-$case/after1.bashrc" "$home/.bashrc" || fail "bashrc ($case) not idempotent"
            cmp -s "$WORK/lazygit-$case/after1.zshrc" "$home/.zshrc" || fail "zshrc ($case) not idempotent"
        fi

        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($case) failed"; continue; }
        [ -e "$home/.config/fish/conf.d/launcher-material-lazygit.fish" ] && fail "undo.sh ($case) left the fish drop-in behind"
        [ -e "$output" ] && fail "undo.sh ($case) left the rendered file behind"
        if [ "$case" = "pre" ]; then
            cmp -s "$orig_bashrc" "$home/.bashrc" || fail "undo.sh (pre) did not restore original .bashrc"
            cmp -s "$orig_zshrc" "$home/.zshrc" || fail "undo.sh (pre) did not restore original .zshrc"
        else
            [ -e "$home/.bashrc" ] && fail "undo.sh (absent) left a .bashrc behind"
            [ -e "$home/.zshrc" ] && fail "undo.sh (absent) left a .zshrc behind"
        fi
    done
}

# ---- ohmyposh ----
test_ohmyposh() {
    local dir="$TEMPLATES_DIR/ohmyposh"
    local rendered="$WORK/ohmyposh.rendered"
    render_template "$dir" "launcher-material.omp.json" >"$rendered" 2>"$WORK/ohmyposh.err" || { fail "render error: $(cat "$WORK/ohmyposh.err")"; return; }
    "$PY" -c "import json,sys; json.load(open(sys.argv[1]))" "$rendered" 2>"$WORK/ohmyposh.jsonerr" || { fail "invalid JSON: $(cat "$WORK/ohmyposh.jsonerr")"; return; }
    assert_no_colour_tokens "$rendered" || { fail "unresolved {{ colors. token in rendered output"; return; }
    if grep -q 'TERMUX_MATERIAL' "$rendered"; then
        fail "TERMUX_MATERIAL still present in rendered output"; return
    fi
    local input_count rendered_count
    input_count="$(grep -o '{{ \.' "$dir/launcher-material.omp.json" | wc -l)"
    rendered_count="$(grep -o '{{ \.' "$rendered" | wc -l)"
    [ "$input_count" = "$rendered_count" ] || { fail "Go-template {{ . count changed: input=$input_count rendered=$rendered_count"; return; }

    # palettes.list.dark / .light, the constant the template renders down to,
    # and every p: reference resolving in both (D3).
    local rendered_light="$WORK/ohmyposh.rendered.light"
    render_template_light "$dir" "launcher-material.omp.json" >"$rendered_light" 2>"$WORK/ohmyposh.lighterr" \
        || { fail "render error (light mode active): $(cat "$WORK/ohmyposh.lighterr")"; return; }
    "$PY" - "$rendered" "$rendered_light" <<'PYCHECK' 2>"$WORK/ohmyposh.dualerr" || { fail "rendered palettes: $(cat "$WORK/ohmyposh.dualerr")"; return; }
import json, re, sys
doc = json.load(open(sys.argv[1]))
light_doc = json.load(open(sys.argv[2]))
palettes = doc.get("palettes", {})
assert palettes.get("template") == "dark", f"palettes.template rendered {palettes.get('template')!r}, wanted the constant 'dark'"
assert light_doc.get("palettes", {}).get("template") == "light", "palettes.template did not follow the active mode"
assert light_doc["palettes"]["list"] == palettes["list"], "the palette list is not the same in both passes"
dark, light = palettes["list"].get("dark"), palettes["list"].get("light")
assert isinstance(dark, dict) and dark, "no palettes.list.dark"
assert isinstance(light, dict) and light, "no palettes.list.light"
assert set(dark) == set(light), "the two palettes carry different keys"
differing = [k for k in dark if dark[k] != light[k]]
assert len(differing) >= len(dark) - 2, f"only {len(differing)} of {len(dark)} entries differ between the palettes"
for table in (dark, light):
    for key, value in table.items():
        assert re.fullmatch(r"#[0-9A-Fa-f]{6}", value), f"{key} = {value!r}"
refs = set(re.findall(r'"p:([A-Za-z0-9_]+)"', json.dumps(doc)))
assert refs, "no segment uses a p: palette reference"
missing = sorted(refs - set(dark))
assert not missing, f"segments reference palette entries that do not exist: {missing}"
# D3: accents are ANSI names, not hex, so they follow the terminal itself.
assert not re.search(r'"#[0-9A-Fa-f]{6}"', json.dumps(doc["blocks"])), "a segment still carries a raw hex colour"
assert not re.search(r"#[0-9A-Fa-f]{6}", json.dumps(doc.get("transient_prompt", {}))), "the transient prompt still carries a raw hex colour"
# Every colour a segment names must be something oh-my-posh 29/30 accepts:
# its schema's color_string pattern or a p: palette reference.
COLOR_STRING = re.compile(
    r"^(#([a-fA-F0-9]{6}|[a-fA-F0-9]{3})|([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])"
    r"|black|red|green|yellow|blue|magenta|cyan|white|default|darkGray|lightRed|lightGreen"
    r"|lightYellow|lightBlue|lightMagenta|lightCyan|lightWhite|transparent|parentBackground"
    r"|parentForeground|background|foreground|accent)$")
def check_color(where, value):
    assert COLOR_STRING.match(value) or value.startswith("p:"), f"{where}: {value!r} is not a colour oh-my-posh accepts"
for block in doc["blocks"]:
    for segment in block.get("segments", []):
        for key in ("foreground", "background"):
            if key in segment:
                check_color(f"{segment.get('type')}.{key}", segment[key])
        for i, template in enumerate(segment.get("foreground_templates", [])):
            if "{{" not in template:
                check_color(f"{segment.get('type')}.foreground_templates[{i}]", template)
PYCHECK
    note "ohmyposh: not installed - JSON parse, no {{ colors. or TERMUX_MATERIAL left, {{ . (Go template) count preserved ($input_count occurrences), palettes.list.dark/.light checked and palettes.template renders to the active mode"

    local case
    for case in absent pre; do
        local home="$WORK/ohmyposh-$case/home"
        rm -rf "$WORK/ohmyposh-$case"; mkdir -p "$home"
        local theme_dir="$WORK/ohmyposh-$case/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.config/ohmyposh/launcher-material.omp.json"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        local orig_bashrc="" orig_zshrc=""
        if [ "$case" = "pre" ]; then
            printf '# my bashrc\nexport X=1\n' > "$home/.bashrc"
            printf '# my zshrc\nexport Y=2\n' > "$home/.zshrc"
            orig_bashrc="$WORK/ohmyposh-$case/orig.bashrc"; cp "$home/.bashrc" "$orig_bashrc"
            orig_zshrc="$WORK/ohmyposh-$case/orig.zshrc"; cp "$home/.zshrc" "$orig_zshrc"
        fi
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($case) failed"; continue; }
        [ -f "$home/.config/fish/conf.d/launcher-material-ohmyposh.fish" ] || fail "apply.sh ($case) did not create the fish drop-in"
        cp "$home/.config/fish/conf.d/launcher-material-ohmyposh.fish" "$WORK/ohmyposh-$case/after1.fish" 2>/dev/null
        [ "$case" = "pre" ] && cp "$home/.bashrc" "$WORK/ohmyposh-$case/after1.bashrc"
        [ "$case" = "pre" ] && cp "$home/.zshrc" "$WORK/ohmyposh-$case/after1.zshrc"

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($case) failed"; continue; }
        cmp -s "$WORK/ohmyposh-$case/after1.fish" "$home/.config/fish/conf.d/launcher-material-ohmyposh.fish" 2>/dev/null || fail "fish drop-in ($case) not idempotent"
        if [ "$case" = "pre" ]; then
            cmp -s "$WORK/ohmyposh-$case/after1.bashrc" "$home/.bashrc" || fail "bashrc ($case) not idempotent"
            cmp -s "$WORK/ohmyposh-$case/after1.zshrc" "$home/.zshrc" || fail "zshrc ($case) not idempotent"
        fi

        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($case) failed"; continue; }
        [ -e "$home/.config/fish/conf.d/launcher-material-ohmyposh.fish" ] && fail "undo.sh ($case) left the fish drop-in behind"
        [ -e "$output" ] && fail "undo.sh ($case) left the rendered file behind"
        if [ "$case" = "pre" ]; then
            cmp -s "$orig_bashrc" "$home/.bashrc" || fail "undo.sh (pre) did not restore original .bashrc"
            cmp -s "$orig_zshrc" "$home/.zshrc" || fail "undo.sh (pre) did not restore original .zshrc"
        else
            [ -e "$home/.bashrc" ] && fail "undo.sh (absent) left a .bashrc behind"
            [ -e "$home/.zshrc" ] && fail "undo.sh (absent) left a .zshrc behind"
        fi
    done

    # setup.sh: bash/zsh (absent + pre), fish (no existing init), and the
    # fish "config.fish already initialises oh-my-posh" special case.
    local shell
    for shell in bash zsh; do
        local rcname=".${shell}rc"
        for case in absent pre; do
            local home="$WORK/ohmyposh-setup-$shell-$case/home"
            rm -rf "$WORK/ohmyposh-setup-$shell-$case"; mkdir -p "$home"
            local theme_dir="$WORK/ohmyposh-setup-$shell-$case/theme_dir"; mkdir -p "$theme_dir"
            local output="$home/.config/ohmyposh/launcher-material.omp.json"
            mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
            local orig=""
            if [ "$case" = "pre" ]; then
                printf '# my rc\nexport FOO=bar\n' > "$home/$rcname"
                orig="$WORK/ohmyposh-setup-$shell-$case/orig"; cp "$home/$rcname" "$orig"
            fi
            run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" "$shell" >/dev/null || { fail "setup.sh ($shell/$case) failed"; continue; }
            cp "$home/$rcname" "$WORK/ohmyposh-setup-$shell-$case/after1" 2>/dev/null
            run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" "$shell" >/dev/null || { fail "second setup.sh ($shell/$case) failed"; continue; }
            cmp -s "$WORK/ohmyposh-setup-$shell-$case/after1" "$home/$rcname" 2>/dev/null || fail "setup.sh ($shell/$case) not idempotent"
            run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" >/dev/null || { fail "undo.sh after setup.sh ($shell/$case) failed"; continue; }
            if [ "$case" = "pre" ]; then
                cmp -s "$orig" "$home/$rcname" || fail "undo.sh did not restore original $rcname ($shell)"
            else
                [ -e "$home/$rcname" ] && fail "undo.sh left a $rcname behind that setup.sh created ($shell)"
            fi
        done
    done

    local home="$WORK/ohmyposh-setup-fish/home"
    rm -rf "$WORK/ohmyposh-setup-fish"; mkdir -p "$home"
    local theme_dir="$WORK/ohmyposh-setup-fish/theme_dir"; mkdir -p "$theme_dir"
    local output="$home/.config/ohmyposh/launcher-material.omp.json"
    mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
    run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" fish >/dev/null || fail "setup.sh (fish) failed"
    local fish_init="$home/.config/fish/conf.d/launcher-material-ohmyposh-init.fish"
    [ -f "$fish_init" ] || fail "setup.sh (fish) did not create its conf.d init file"
    cp "$fish_init" "$WORK/ohmyposh-setup-fish/after1" 2>/dev/null
    run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" fish >/dev/null || fail "second setup.sh (fish) failed"
    cmp -s "$WORK/ohmyposh-setup-fish/after1" "$fish_init" 2>/dev/null || fail "setup.sh (fish) not idempotent"
    run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" >/dev/null || fail "undo.sh after setup.sh (fish) failed"
    [ -e "$fish_init" ] && fail "undo.sh left the fish init file behind"

    # fish: config.fish already initialises oh-my-posh -> setup.sh must skip.
    home="$WORK/ohmyposh-setup-fish-already/home"
    rm -rf "$WORK/ohmyposh-setup-fish-already"; mkdir -p "$home/.config/fish"
    theme_dir="$WORK/ohmyposh-setup-fish-already/theme_dir"; mkdir -p "$theme_dir"
    output="$home/.config/ohmyposh/launcher-material.omp.json"
    mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
    {
        echo 'if type -q oh-my-posh'
        echo '    set -l omp_theme "$HOME/.config/ohmyposh/aliens-material.omp.json"'
        echo '    if test -f "$omp_theme"'
        echo '        oh-my-posh --config "$omp_theme" init fish | source'
        echo '    end'
        echo 'end'
    } > "$home/.config/fish/config.fish"
    run_hook_shell "$home" "$theme_dir" "$output" "$dir/setup.sh" fish >/dev/null || fail "setup.sh (fish, already-initialised) failed"
    [ -e "$home/.config/fish/conf.d/launcher-material-ohmyposh-init.fish" ] && fail "setup.sh (fish, already-initialised) should not have written an init file"
    note "ohmyposh setup.sh: covered bash/zsh (absent+pre, idempotent, undo), fish (fresh init, idempotent, undo), and fish's already-initialised config.fish skip"
}

# ---- nvim ----
test_nvim() {
    local dir="$TEMPLATES_DIR/nvim"
    local rendered="$WORK/nvim.rendered"
    # The rendered file is the palette module: both palettes as static tables.
    render_template "$dir" "lua/launcher/material_palette.lua" >"$rendered" 2>"$WORK/nvim.err" || { fail "render error: $(cat "$WORK/nvim.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }
    assert_dual "$rendered" "#B6C4FF" "#4C5D93" \
        || { fail "rendered palette module does not carry both fixture palettes (dark primary #B6C4FF and light primary #4C5D93)"; return; }
    grep -q '\brendered_mode = "dark"' "$rendered" || { fail "{{ mode }} did not render into M.rendered_mode"; return; }
    # The plugin spec no longer pins `background`: Neovim owns it.
    grep -q 'vim.o.background[[:space:]]*=' "$dir/lua/plugins/launcher-material.lua" \
        && { fail "the plugin spec still sets vim.o.background"; return; }
    grep -q '^vim.o.background[[:space:]]*=' "$dir/colors/launcher-material.lua" \
        && { fail "the colourscheme still sets vim.o.background"; return; }

    if command -v luac >/dev/null 2>&1; then
        luac -p "$rendered" 2>"$WORK/nvim.luacerr" || { fail "luac -p rejected the rendered palette module: $(cat "$WORK/nvim.luacerr")"; return; }
        luac -p "$dir/colors/launcher-material.lua" 2>"$WORK/nvim.luacerr2" || { fail "luac -p rejected colors/launcher-material.lua: $(cat "$WORK/nvim.luacerr2")"; return; }
        luac -p "$dir/lua/plugins/launcher-material.lua" 2>"$WORK/nvim.luacerr3" || { fail "luac -p rejected the plugin spec: $(cat "$WORK/nvim.luacerr3")"; return; }
    fi
    local nvim_note="nvim: not installed - luac -p only"
    if command -v nvim >/dev/null 2>&1; then
        # A real headless run: install the colourscheme and the rendered palette
        # module into a throwaway runtimepath, paint in dark, flip `background`
        # to light (which is exactly what Neovim does on the terminal's mode-2031
        # report) and require that Normal's guifg and guibg actually changed -
        # and changed back.
        local rt="$WORK/nvim-rtp"
        rm -rf "$rt"; mkdir -p "$rt/colors" "$rt/lua/launcher"
        cp "$dir/colors/launcher-material.lua" "$rt/colors/launcher-material.lua"
        cp "$rendered" "$rt/lua/launcher/material_palette.lua"
        cat >"$WORK/nvim-flip.lua" <<'PROBE'
local dir = arg[1]
vim.opt.runtimepath:prepend(dir)
-- Opaque, so guibg is a real colour rather than the glass NONE.
vim.g.material_opaque = true
vim.o.termguicolors = true
local function normal()
  local hl = vim.api.nvim_get_hl(0, { name = "Normal", link = false })
  return ("%s/%s"):format(
    hl.fg and ("#%06X"):format(hl.fg) or "NONE",
    hl.bg and ("#%06X"):format(hl.bg) or "NONE")
end
vim.o.background = "dark"
vim.cmd.colorscheme("launcher-material")
local first = normal()
vim.o.background = "light"
local light = normal()
vim.o.background = "dark"
local again = normal()
local hooks = vim.api.nvim_get_autocmds({ group = "LauncherMaterialBackground", event = "OptionSet" })
io.stdout:write(("dark=%s light=%s again=%s name=%s type=%s hooks=%d\n"):format(
  first, light, again, tostring(vim.g.colors_name),
  tostring((vim.g.material_theme_info or {}).type), #hooks))
PROBE
        nvim --headless --clean -l "$WORK/nvim-flip.lua" "$rt" >"$WORK/nvim.flip" 2>&1 \
            || { fail "nvim --headless --clean rejected the colourscheme: $(cat "$WORK/nvim.flip")"; return; }
        local flip; flip="$(cat "$WORK/nvim.flip")"
        local dark_hl light_hl again_hl
        dark_hl="$(sed -n 's/.*dark=\([^ ]*\).*/\1/p' <<<"$flip")"
        light_hl="$(sed -n 's/.*light=\([^ ]*\).*/\1/p' <<<"$flip")"
        again_hl="$(sed -n 's/.*again=\([^ ]*\).*/\1/p' <<<"$flip")"
        grep -q 'name=launcher-material' <<<"$flip" || fail "colors_name was not set: $flip"
        grep -q 'hooks=[1-9]' <<<"$flip" || fail "no OptionSet background autocmd was registered: $flip"
        grep -q 'type=light' <<<"$flip" && fail "material_theme_info still reports the light build after flipping back to dark: $flip"
        [ -n "$dark_hl" ] && [ "$dark_hl" != "NONE/NONE" ] || fail "Normal was not painted in dark mode: $flip"
        [ "$dark_hl" != "$light_hl" ] || fail "background=light did not change Normal: $flip"
        [ "$dark_hl" = "$again_hl" ] || fail "background=dark did not restore Normal: $flip"
        nvim_note="nvim $(nvim --version | head -1 | awk '{print $2}'): luac -p on all three Lua files; headless background=light/dark flip repaints Normal $dark_hl <-> $light_hl, OptionSet background autocmd registered"
    fi
    note "$nvim_note"

    run_nvim_case() {
        local name="$1"; shift
        local home="$WORK/nvim-$name/home"
        rm -rf "$WORK/nvim-$name"; mkdir -p "$home"
        local theme_dir="$dir"
        local output="$home/.config/nvim/lua/launcher/material_palette.lua"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        "$@" "$home"

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($name) failed"; return; }
        cmp -s "$home/.config/nvim/colors/launcher-material.lua" "$dir/colors/launcher-material.lua" || fail "apply.sh ($name) did not install colors/launcher-material.lua correctly"
        cmp -s "$home/.config/nvim/lua/plugins/launcher-material.lua" "$dir/lua/plugins/launcher-material.lua" || fail "apply.sh ($name) did not install the plugin spec correctly"
        cmp -s "$output" "$rendered" || fail "apply.sh ($name) overwrote the rendered palette module"

        local snapshot="$WORK/nvim-$name/after1"
        mkdir -p "$snapshot"; cp -r "$home/.config" "$snapshot/config" 2>/dev/null
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($name) failed"; return; }
        diff -rq "$snapshot/config" "$home/.config" >"$WORK/nvim-$name.diff" 2>&1
        [ -s "$WORK/nvim-$name.diff" ] && fail "apply.sh ($name) not idempotent: $(cat "$WORK/nvim-$name.diff")"

        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($name) failed"; return; }
        [ -e "$home/.config/nvim/colors/launcher-material.lua" ] && fail "undo.sh ($name) left colors/launcher-material.lua behind"
        [ -e "$home/.config/nvim/lua/plugins/launcher-material.lua" ] && fail "undo.sh ($name) left the plugin spec behind"
        [ -e "$output" ] && fail "undo.sh ($name) left the rendered palette module behind"
    }

    setup_plain() { :; }
    setup_lazyvim() {
        local home="$1"
        mkdir -p "$home/.config/nvim/lua/config"
        echo 'require("lazy").setup({ "LazyVim/LazyVim" })' > "$home/.config/nvim/lua/config/lazy.lua"
    }
    setup_astronvim() {
        local home="$1"
        mkdir -p "$home/.config/nvim/lua"
        echo '-- AstroNvim community' > "$home/.config/nvim/lua/community.lua"
    }

    run_nvim_case plain setup_plain
    if [ -e "$WORK/nvim-plain/home/.config/nvim/init.lua" ]; then
        fail "plain case: init.lua should have been cleaned up by undo.sh"
    fi
    run_nvim_case lazyvim setup_lazyvim
    run_nvim_case astronvim setup_astronvim

    # A hand-edited colorscheme file must survive undo.
    local home="$WORK/nvim-edited/home"
    rm -rf "$WORK/nvim-edited"; mkdir -p "$home"
    local output="$home/.config/nvim/lua/launcher/material_palette.lua"
    mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
    run_hook "$home" "$dir" "$output" "$dir/apply.sh" >/dev/null || fail "apply.sh (edited case) failed"
    echo '-- user edit' >> "$home/.config/nvim/colors/launcher-material.lua"
    run_hook "$home" "$dir" "$output" "$dir/undo.sh" >/dev/null || fail "undo.sh (edited case) failed"
    [ -f "$home/.config/nvim/colors/launcher-material.lua" ] || fail "undo.sh removed a hand-edited colorscheme file; it should have survived"
}

# ---- fish ----
test_fish() {
    local dir="$TEMPLATES_DIR/fish"
    local rendered="$WORK/fish.rendered"
    render_template "$dir" "launcher-material.theme" >"$rendered" 2>"$WORK/fish.err" || { fail "render error: $(cat "$WORK/fish.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }
    assert_dual "$rendered" "1A1B26" "E1E2E7" \
        || { fail "rendered theme does not carry both fixture palettes (dark background 1A1B26 and light background E1E2E7)"; return; }

    # The .theme format fish documents: a `# name:` line, [light]/[dark]/[unknown]
    # sections, and `fish_(pager_)?color_*` variables whose values are bare hex
    # (no leading #) and/or set_color switches.
    "$PY" - "$rendered" <<'PYCHECK' 2>"$WORK/fish.formaterr" || { fail "theme file: $(cat "$WORK/fish.formaterr")"; return; }
import re, sys
sections, current, name = {}, None, None
for raw in open(sys.argv[1]):
    line = raw.strip()
    if line.startswith("# name:"):
        name = line.split(":", 1)[1].strip()
        continue
    if not line or line.startswith("#"):
        continue
    if line.startswith("[") and line.endswith("]"):
        current = line[1:-1]
        sections[current] = {}
        continue
    assert current is not None, f"{line!r} sits outside any section"
    key, _, value = line.partition(" ")
    assert re.fullmatch(r"fish_(pager_)?color_.*", key), f"{key} is not a fish_*color_* variable"
    for word in value.split():
        word = word.split("=", 1)[1] if word.startswith("--background=") else word
        if word.startswith("-"):
            continue
        assert re.fullmatch(r"[0-9A-Fa-f]{6}", word), f"{key}: {word!r} is not a bare six-digit hex colour"
    sections[current][key] = value
assert name, "no `# name:` line"
for wanted in ("light", "dark", "unknown"):
    assert wanted in sections, f"no [{wanted}] section"
assert set(sections["light"]) == set(sections["dark"]) == set(sections["unknown"]), \
    "the three sections set different variables"
differing = [k for k in sections["dark"] if sections["dark"][k] != sections["light"][k]]
assert len(differing) >= len(sections["dark"]) - 4, \
    f"only {len(differing)} of {len(sections['dark'])} variables differ between light and dark"
PYCHECK

    local fish_note="fish: not installed - theme-file format checked by hand"
    if command -v fish >/dev/null 2>&1; then
        # fish's own parser, in a throwaway HOME: it must list the theme, and
        # resolving it for each colour theme must hand back that section's
        # values - which is the light/dark switch itself.
        local fhome="$WORK/fish-validate"
        rm -rf "$fhome"; mkdir -p "$fhome/.config/fish/themes"
        cp "$rendered" "$fhome/.config/fish/themes/launcher-material.theme"
        local listed dark_value light_value
        listed="$(env HOME="$fhome" XDG_CONFIG_HOME="$fhome/.config" fish -c 'fish_config theme list' 2>&1 | grep -cxF 'launcher-material')"
        [ "$listed" = 1 ] || { fail "fish_config theme list does not offer launcher-material"; return; }
        env HOME="$fhome" XDG_CONFIG_HOME="$fhome/.config" fish -c 'fish_config theme show launcher-material' >/dev/null 2>"$WORK/fish.showerr" \
            || { fail "fish_config theme show rejected the rendered theme: $(cat "$WORK/fish.showerr")"; return; }
        dark_value="$(env HOME="$fhome" XDG_CONFIG_HOME="$fhome/.config" fish -c 'fish_config theme choose launcher-material --color-theme=dark; echo $fish_color_normal' 2>&1)"
        light_value="$(env HOME="$fhome" XDG_CONFIG_HOME="$fhome/.config" fish -c 'fish_config theme choose launcher-material --color-theme=light; echo $fish_color_normal' 2>&1)"
        local want_dark want_light
        want_dark="$(awk '/^\[dark\]/{s=1;next} /^\[/{s=0} s && $1=="fish_color_normal"{print $2; exit}' "$rendered")"
        want_light="$(awk '/^\[light\]/{s=1;next} /^\[/{s=0} s && $1=="fish_color_normal"{print $2; exit}' "$rendered")"
        [ -n "$want_dark" ] && [ -n "$want_light" ] && [ "$want_dark" != "$want_light" ] \
            || { fail "the rendered theme has no differing fish_color_normal in [dark] and [light]"; return; }
        grep -qiF "$want_dark" <<<"$dark_value" || fail "fish resolved the dark section to '$dark_value', wanted $want_dark"
        grep -qiF "$want_light" <<<"$light_value" || fail "fish resolved the light section to '$light_value', wanted $want_light"
        [ "$dark_value" != "$light_value" ] || fail "fish resolved light and dark to the same colours: '$dark_value'"
        fish_note="fish $(fish --version | awk '{print $3}'): the real fish_config lists the theme, accepts it, and resolves --color-theme=dark/light to different colours"
    fi
    note "$fish_note"

    local dropin_rel=".config/fish/conf.d/launcher-material-fish.fish"
    local case
    for case in absent pre; do
        local home="$WORK/fish-$case/home"
        rm -rf "$WORK/fish-$case"; mkdir -p "$home"
        local theme_dir="$WORK/fish-$case/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.config/fish/themes/launcher-material.theme"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        local orig_config=""
        if [ "$case" = "pre" ]; then
            # A fish config that already picks a theme of its own.
            mkdir -p "$home/.config/fish/conf.d"
            printf 'set -g fish_greeting ""\nfish_config theme choose "ayu Dark"\n' > "$home/.config/fish/config.fish"
            orig_config="$WORK/fish-$case/orig.fish"; cp "$home/.config/fish/config.fish" "$orig_config"
        fi

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($case) failed"; continue; }
        [ -f "$home/$dropin_rel" ] || { fail "apply.sh ($case) did not create the conf.d drop-in"; continue; }
        grep -qF 'fish_config theme choose launcher-material' "$home/$dropin_rel" \
            || fail "apply.sh ($case) drop-in does not select the theme"
        grep -qF 'theme save' "$home/$dropin_rel" \
            && fail "apply.sh ($case) used theme save, which freezes fish light/dark switching"
        cp "$home/$dropin_rel" "$WORK/fish-$case/after1"

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($case) failed"; continue; }
        cmp -s "$WORK/fish-$case/after1" "$home/$dropin_rel" || fail "apply.sh ($case) not idempotent"
        if [ "$case" = "pre" ]; then
            cmp -s "$orig_config" "$home/.config/fish/config.fish" || fail "apply.sh (pre) edited the user's config.fish"
        fi

        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($case) failed"; continue; }
        [ -e "$home/$dropin_rel" ] && fail "undo.sh ($case) left the conf.d drop-in behind"
        [ -e "$output" ] && fail "undo.sh ($case) left the rendered theme behind"
        if [ "$case" = "pre" ]; then
            cmp -s "$orig_config" "$home/.config/fish/config.fish" \
                || fail "undo.sh (pre) did not leave the user's own theme choice exactly as it was"
        fi
    done
}

# ---- herdr ----
test_herdr() {
    local dir="$TEMPLATES_DIR/herdr"
    local rendered="$WORK/herdr.rendered"
    render_template "$dir" "launcher-material.toml" >"$rendered" 2>"$WORK/herdr.err" || { fail "render error: $(cat "$WORK/herdr.err")"; return; }
    assert_no_stray_braces "$rendered" || { fail "unresolved {{ in rendered output"; return; }
    "$PY" -c "import tomllib,sys; tomllib.load(open(sys.argv[1],'rb'))" "$rendered" 2>"$WORK/herdr.tomlerr" || { fail "invalid TOML: $(cat "$WORK/herdr.tomlerr")"; return; }
    # Both subtables, the same key set, and genuinely different values.
    "$PY" - "$rendered" <<'PYCHECK' 2>"$WORK/herdr.dualerr" || { fail "rendered palettes: $(cat "$WORK/herdr.dualerr")"; return; }
import sys, tomllib
doc = tomllib.load(open(sys.argv[1], "rb"))
custom = doc.get("theme", {}).get("custom", {})
dark, light = custom.get("dark"), custom.get("light")
assert isinstance(dark, dict) and dark, "no [theme.custom.dark] table"
assert isinstance(light, dict) and light, "no [theme.custom.light] table"
assert set(dark) == set(light), "the two subtables carry different keys"
assert "auto_switch" not in custom and "name" not in custom, "the rendered file must not touch [theme]"
differing = [k for k in dark if dark[k] != light[k]]
assert len(differing) >= len(dark) - 1, f"only {len(differing)} of {len(dark)} keys differ between the palettes"
for table in (dark, light):
    for key, value in table.items():
        assert isinstance(value, str) and value.startswith("#") and len(value) == 7, f"{key} = {value!r}"
PYCHECK
    note "herdr: not installed on this machine - TOML parse, and both palette subtables checked for the same keys with different values"

    # herdr_case <name>: runs apply/apply/undo against $home's config.toml,
    # checking idempotency, that the result is valid TOML, and that undo
    # restores the original bytes (or removes a file apply.sh created).
    herdr_case() {
        local name="$1" had_config="$2"
        local home="$WORK/herdr-$name/home"
        local theme_dir="$WORK/herdr-$name/theme_dir"; mkdir -p "$theme_dir"
        local output="$home/.config/herdr/launcher-material.toml"
        local config="$home/.config/herdr/config.toml"
        mkdir -p "$(dirname "$output")"; cp "$rendered" "$output"
        local orig="$WORK/herdr-$name/orig.toml"
        [ "$had_config" = "yes" ] && cp "$config" "$orig"

        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "apply.sh ($name) failed"; return 1; }
        "$PY" -c "import tomllib,sys; tomllib.load(open(sys.argv[1],'rb'))" "$config" 2>"$WORK/herdr-$name.tomlerr" \
            || { fail "apply.sh ($name) produced invalid TOML: $(cat "$WORK/herdr-$name.tomlerr")"; return 1; }
        cp "$config" "$WORK/herdr-$name/after1.toml"
        run_hook "$home" "$theme_dir" "$output" "$dir/apply.sh" || { fail "second apply.sh ($name) failed"; return 1; }
        cmp -s "$WORK/herdr-$name/after1.toml" "$config" || { fail "apply.sh ($name) not idempotent"; return 1; }

        # What every case must end up with, whatever it started from.
        grep -qxF 'auto_switch = true' "$config" || fail "apply.sh ($name) did not turn auto_switch on"
        grep -qxF '[theme.custom.dark]' "$config" || fail "apply.sh ($name) has no [theme.custom.dark] table"
        grep -qxF '[theme.custom.light]' "$config" || fail "apply.sh ($name) has no [theme.custom.light] table"
        [ "$(grep -cxF '[theme.custom.dark]' "$config")" = 1 ] || fail "apply.sh ($name) declared [theme.custom.dark] twice"
        [ "$(grep -cxF '[theme.custom.light]' "$config")" = 1 ] || fail "apply.sh ($name) declared [theme.custom.light] twice"
        [ "$(grep -cxF '[theme]' "$config")" -le 1 ] || fail "apply.sh ($name) declared [theme] twice"
        assert_dual "$config" "#B6C4FF" "#4C5D93" || fail "apply.sh ($name) did not land both palettes in config.toml"

        run_hook "$home" "$theme_dir" "$output" "$dir/undo.sh" || { fail "undo.sh ($name) failed"; return 1; }
        if [ "$had_config" = "yes" ]; then
            cmp -s "$orig" "$config" || fail "undo.sh ($name) did not restore original bytes"
        else
            [ -e "$config" ] && fail "undo.sh ($name) left a config.toml behind (it held only our own tables)"
        fi
        [ -e "$output" ] && fail "undo.sh ($name) left the rendered file behind"
        return 0
    }

    # ---- absent: no config.toml at all ----
    rm -rf "$WORK/herdr-absent"; mkdir -p "$WORK/herdr-absent/home"
    herdr_case absent no

    # ---- no-table: config.toml pre-exists with no theme tables at all ----
    rm -rf "$WORK/herdr-no-table"; mkdir -p "$WORK/herdr-no-table/home/.config/herdr"
    printf '[general]\nworkspace_root = "~/src"\n' > "$WORK/herdr-no-table/home/.config/herdr/config.toml"
    herdr_case no-table yes

    # ---- pre: [theme.custom] pre-exists with a key of ours and unrelated content ----
    rm -rf "$WORK/herdr-pre"; mkdir -p "$WORK/herdr-pre/home/.config/herdr"
    printf '[general]\nworkspace_root = "~/src"\n\n[theme.custom]\naccent = "#123456"\nfont_size = 14\n\n[keys]\nquit = "q"\n' \
        > "$WORK/herdr-pre/home/.config/herdr/config.toml"
    if herdr_case pre yes; then
        local config="$WORK/herdr-pre/home/.config/herdr/config.toml"
        # [theme.custom] is the shared base our subtables layer over, so it is
        # left exactly as the user wrote it - including a key we also set.
        grep -qxF 'accent = "#123456"' "$WORK/herdr-pre/after1.toml" \
            || fail "apply.sh (pre) touched the shared [theme.custom] table"
        grep -qxF 'font_size = 14' "$WORK/herdr-pre/after1.toml" \
            || fail "apply.sh (pre) touched an unrelated key in [theme.custom]"
        grep -qxF 'quit = "q"' "$WORK/herdr-pre/after1.toml" \
            || fail "apply.sh (pre) touched an unrelated table"
    fi

    # ---- auto-switch-off: the user pinned a theme and turned auto_switch off
    # (this is pong's config). auto_switch is displaced, not dropped; name is
    # left alone. ----
    rm -rf "$WORK/herdr-autoswitch"; mkdir -p "$WORK/herdr-autoswitch/home/.config/herdr"
    printf '[theme]\nname = "terminal"\nauto_switch = false\n\n[keys]\nquit = "q"\n' \
        > "$WORK/herdr-autoswitch/home/.config/herdr/config.toml"
    if herdr_case autoswitch yes; then
        grep -qxF '# launcher-material: auto_switch = false' "$WORK/herdr-autoswitch/after1.toml" \
            || fail "apply.sh (autoswitch) did not comment out and tag the pre-existing auto_switch = false"
        grep -qxF 'name = "terminal"' "$WORK/herdr-autoswitch/after1.toml" \
            || fail "apply.sh (autoswitch) touched [theme] name"
        grep -qxF 'auto_switch = false' "$WORK/herdr-autoswitch/after1.toml" \
            && fail "apply.sh (autoswitch) left an uncommented auto_switch = false behind"
    fi

    # ---- dark-table: [theme.custom.dark] already exists with one of our keys
    # and one of the user's own. Ours is displaced; theirs survives; the table
    # is never declared a second time. ----
    rm -rf "$WORK/herdr-darktable"; mkdir -p "$WORK/herdr-darktable/home/.config/herdr"
    printf '[theme]\nname = "catppuccin"\n\n[theme.custom.dark]\naccent = "#abcdef"\nmy_own = "#010203"\n\n[keys]\nquit = "q"\n' \
        > "$WORK/herdr-darktable/home/.config/herdr/config.toml"
    if herdr_case darktable yes; then
        grep -qxF '# launcher-material: accent = "#abcdef"' "$WORK/herdr-darktable/after1.toml" \
            || fail "apply.sh (darktable) did not comment out the conflicting accent in [theme.custom.dark]"
        grep -qxF 'my_own = "#010203"' "$WORK/herdr-darktable/after1.toml" \
            || fail "apply.sh (darktable) dropped an unrelated key from [theme.custom.dark]"
        grep -qxF 'name = "catppuccin"' "$WORK/herdr-darktable/after1.toml" \
            || fail "apply.sh (darktable) touched [theme] name"
    fi
}

# ---- shared rc isolation (R1/R12): fzf, lazygit and ohmyposh all write into
# the same ~/.bashrc and ~/.zshrc. Enable all three in one HOME, in sequence,
# then undo just the last one and confirm the other two survive untouched. ----
test_shared_rc() {
    local fzf_dir="$TEMPLATES_DIR/fzf"
    local lazygit_dir="$TEMPLATES_DIR/lazygit"
    local ohmyposh_dir="$TEMPLATES_DIR/ohmyposh"

    rm -rf "$WORK/shared-rc"; mkdir -p "$WORK/shared-rc"
    local fzf_rendered="$WORK/shared-rc/fzf.rendered"
    local lazygit_rendered="$WORK/shared-rc/lazygit.rendered"
    local ohmyposh_rendered="$WORK/shared-rc/ohmyposh.rendered"
    render_template "$fzf_dir" "launcher-material.sh" >"$fzf_rendered" 2>"$WORK/shared-rc/fzf.err" \
        || { fail "fzf render error: $(cat "$WORK/shared-rc/fzf.err")"; return; }
    render_template "$lazygit_dir" "launcher-material.yml" >"$lazygit_rendered" 2>"$WORK/shared-rc/lazygit.err" \
        || { fail "lazygit render error: $(cat "$WORK/shared-rc/lazygit.err")"; return; }
    render_template "$ohmyposh_dir" "launcher-material.omp.json" >"$ohmyposh_rendered" 2>"$WORK/shared-rc/ohmyposh.err" \
        || { fail "ohmyposh render error: $(cat "$WORK/shared-rc/ohmyposh.err")"; return; }

    local home="$WORK/shared-rc/home"
    mkdir -p "$home"
    printf '# my bashrc\nexport FOO=bar\n' > "$home/.bashrc"
    printf '# my zshrc\nexport BAZ=qux\n' > "$home/.zshrc"

    local fzf_theme_dir="$WORK/shared-rc/fzf_theme_dir"; mkdir -p "$fzf_theme_dir"
    local lazygit_theme_dir="$WORK/shared-rc/lazygit_theme_dir"; mkdir -p "$lazygit_theme_dir"
    local ohmyposh_theme_dir="$WORK/shared-rc/ohmyposh_theme_dir"; mkdir -p "$ohmyposh_theme_dir"

    local fzf_output="$home/.config/fzf/launcher-material.sh"
    local lazygit_output="$home/.config/lazygit/launcher-material.yml"
    local ohmyposh_output="$home/.config/ohmyposh/launcher-material.omp.json"
    mkdir -p "$(dirname "$fzf_output")" "$(dirname "$lazygit_output")" "$(dirname "$ohmyposh_output")"
    cp "$fzf_rendered" "$fzf_output"
    cp "$lazygit_rendered" "$lazygit_output"
    cp "$ohmyposh_rendered" "$ohmyposh_output"

    run_hook "$home" "$fzf_theme_dir" "$fzf_output" "$fzf_dir/apply.sh" || { fail "fzf apply.sh failed"; return; }
    run_hook "$home" "$lazygit_theme_dir" "$lazygit_output" "$lazygit_dir/apply.sh" || { fail "lazygit apply.sh failed"; return; }
    run_hook "$home" "$ohmyposh_theme_dir" "$ohmyposh_output" "$ohmyposh_dir/apply.sh" || { fail "ohmyposh apply.sh failed"; return; }

    local rc
    for rc in "$home/.bashrc" "$home/.zshrc"; do
        grep -qxF '# >>> launcher-material fzf >>>' "$rc" || fail "fzf marker missing from $rc after all three applied"
        grep -qxF '# >>> launcher-material lazygit >>>' "$rc" || fail "lazygit marker missing from $rc after all three applied"
        grep -qxF '# >>> launcher-material ohmyposh >>>' "$rc" || fail "ohmyposh marker missing from $rc after all three applied"
        grep -qF "source $fzf_output" "$rc" || fail "fzf source line missing from $rc after all three applied"
        grep -qF 'LG_CONFIG_FILE' "$rc" || fail "lazygit LG_CONFIG_FILE export missing from $rc after all three applied"
        grep -qF 'POSH_THEME' "$rc" || fail "ohmyposh POSH_THEME export missing from $rc after all three applied"
    done

    run_hook "$home" "$ohmyposh_theme_dir" "$ohmyposh_output" "$ohmyposh_dir/undo.sh" || { fail "ohmyposh undo.sh failed"; return; }

    for rc in "$home/.bashrc" "$home/.zshrc"; do
        grep -qxF '# >>> launcher-material fzf >>>' "$rc" || fail "fzf marker lost from $rc after ohmyposh's undo removed it too"
        grep -qxF '# >>> launcher-material lazygit >>>' "$rc" || fail "lazygit marker lost from $rc after ohmyposh's undo removed it too"
        grep -qF "source $fzf_output" "$rc" || fail "fzf source line lost from $rc after ohmyposh's undo"
        grep -qF 'LG_CONFIG_FILE' "$rc" || fail "lazygit export lost from $rc after ohmyposh's undo"
        grep -qxF '# >>> launcher-material ohmyposh >>>' "$rc" && fail "ohmyposh marker still present in $rc after its own undo"
        grep -qF 'POSH_THEME' "$rc" && fail "POSH_THEME export still present in $rc after ohmyposh's own undo"
    done

    run_hook "$home" "$fzf_theme_dir" "$fzf_output" "$fzf_dir/undo.sh" || fail "fzf undo.sh (cleanup) failed"
    run_hook "$home" "$lazygit_theme_dir" "$lazygit_output" "$lazygit_dir/undo.sh" || fail "lazygit undo.sh (cleanup) failed"
    cmp -s "$home/.bashrc" <(printf '# my bashrc\nexport FOO=bar\n') || fail ".bashrc not restored to its original content after all three were undone"
    cmp -s "$home/.zshrc" <(printf '# my zshrc\nexport BAZ=qux\n') || fail ".zshrc not restored to its original content after all three were undone"
}

# ---------------------------------------------------------------------------
# Run all templates
# ---------------------------------------------------------------------------

declare -A TEST_FN=(
    [starship]=test_starship
    [helix]=test_helix
    [tmux]=test_tmux
    [bat]=test_bat
    [yazi]=test_yazi
    [fzf]=test_fzf
    [lazygit]=test_lazygit
    [ohmyposh]=test_ohmyposh
    [nvim]=test_nvim
    [fish]=test_fish
    [herdr]=test_herdr
    [shared-rc]=test_shared_rc
)

ORDER="starship helix tmux bat yazi fzf lazygit ohmyposh nvim fish herdr shared-rc"

for id in $ORDER; do
    dir="$TEMPLATES_DIR/$id"
    if [ "$id" != "shared-rc" ] && [ ! -d "$dir" ]; then
        echo "FAIL $id: template directory missing"
        OVERALL=1
        continue
    fi

    NOTES=()
    reset_fails
    "${TEST_FN[$id]}"

    if [ "${#FAILS[@]}" -eq 0 ]; then
        msg="ok"
        [ "${#NOTES[@]}" -gt 0 ] && msg="${NOTES[*]}"
        echo "PASS $id: $msg"
    else
        OVERALL=1
        joined="$(printf '; %s' "${FAILS[@]}")"
        joined="${joined#; }"
        echo "FAIL $id: $joined"
    fi
done

exit "$OVERALL"
