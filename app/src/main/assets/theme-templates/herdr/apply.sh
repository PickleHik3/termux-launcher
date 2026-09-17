#!/bin/sh
# Splices the rendered launcher-material palettes into herdr's config.toml and
# turns theme auto-switching on, so herdr follows the host terminal's light/dark
# report instead of a pinned theme.
#
# Three marker blocks, all self-healing: `auto_switch = true` inside [theme],
# and the rendered [theme.custom.dark] and [theme.custom.light] tables. A table
# that already exists gets our block spliced in right after its header, so no
# table is ever declared twice; one that does not is created, header and all,
# inside the block. Any pre-existing key of the same name in the same table -
# including an `auto_switch` of the user's own - is commented out with a
# "# launcher-material: " prefix rather than dropped, so undo.sh restores it
# exactly and TOML never sees a duplicate key. [theme] name is left alone, and
# so is the shared [theme.custom] table: our two subtables layer over it.
#
# Pure POSIX sh/awk: herdr ships no Python, and this hook must not assume one.
set -eu

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/herdr/config.toml"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"

mkdir -p "$(dirname "$config_file")"

tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

cat "$config_file" 2>/dev/null | awk -v begin="$marker_begin" -v end="$marker_end" -v keysfile="$rendered" '
    function trimmed_key(line,   key) {
        key = line
        sub(/=.*/, "", key)
        gsub(/^[ \t]+/, "", key)
        gsub(/[ \t]+$/, "", key)
        return key
    }
    function emit_dark(   i) { for (i = 0; i < n_dark; i++) print dark[i] }
    function emit_light(   i) { for (i = 0; i < n_light; i++) print light[i] }
    BEGIN {
        section = ""
        n_dark = 0
        n_light = 0
        while ((getline kline < keysfile) > 0) {
            if (kline ~ /^\[/) {
                if (kline ~ /^\[[ \t]*theme\.custom\.dark[ \t]*\]/) section = "dark"
                else if (kline ~ /^\[[ \t]*theme\.custom\.light[ \t]*\]/) section = "light"
                else section = ""
                continue
            }
            if (kline ~ /^[ \t]*#/ || kline ~ /^[ \t]*$/) continue
            key = trimmed_key(kline)
            if (key == "") continue
            if (section == "dark") { dark[n_dark++] = kline; dark_keys[key] = 1 }
            else if (section == "light") { light[n_light++] = kline; light_keys[key] = 1 }
        }
        close(keysfile)
        table = ""
        in_marker = 0
    }
    {
        line = $0
        if (line == begin) { in_marker = 1; next }
        if (in_marker) {
            if (line == end) in_marker = 0
            next
        }
        if (line ~ /^\[/) {
            table = ""
            if (line ~ /^\[[ \t]*theme[ \t]*\]/) table = "theme"
            else if (line ~ /^\[[ \t]*theme\.custom\.dark[ \t]*\]/) table = "dark"
            else if (line ~ /^\[[ \t]*theme\.custom\.light[ \t]*\]/) table = "light"
            print line
            if (table == "theme") {
                theme_seen = 1
                print begin; print "auto_switch = true"; print end
            } else if (table == "dark") {
                dark_seen = 1
                print begin; emit_dark(); print end
            } else if (table == "light") {
                light_seen = 1
                print begin; emit_light(); print end
            }
            next
        }
        if (table != "") {
            key = trimmed_key(line)
            if (key != "" \
                && ((table == "theme" && key == "auto_switch") \
                    || (table == "dark" && (key in dark_keys)) \
                    || (table == "light" && (key in light_keys)))) {
                print "# launcher-material: " line
                next
            }
        }
        print line
    }
    END {
        if (!theme_seen) {
            print begin; print "[theme]"; print "auto_switch = true"; print end
        }
        if (!dark_seen) {
            print begin; print "[theme.custom.dark]"; emit_dark(); print end
        }
        if (!light_seen) {
            print begin; print "[theme.custom.light]"; emit_light(); print end
        }
    }
' >"$tmp_file"

if [ ! -e "$config_file" ] && [ ! -L "$config_file" ]; then
    mv "$tmp_file" "$config_file"
    trap - EXIT
elif ! cmp -s "$config_file" "$tmp_file"; then
    cat "$tmp_file" >"$config_file"
fi
rm -f "$tmp_file"

if command -v herdr >/dev/null 2>&1; then
    herdr server reload-config >/dev/null 2>&1 || true
fi
