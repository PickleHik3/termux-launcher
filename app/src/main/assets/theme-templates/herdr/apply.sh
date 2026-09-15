#!/bin/sh
# Splices the rendered launcher-material palette into herdr's [theme.custom]
# table in config.toml (creating the table, inside a fresh marker block, if
# none exists yet), commenting out any pre-existing key of the same name
# with a "# launcher-material: " prefix so undo.sh can restore it exactly,
# then asks a running herdr server to reload. Pure POSIX sh/awk: herdr ships
# no Python, and this hook must not assume one either.
set -eu

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/herdr/config.toml"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"

mkdir -p "$(dirname "$config_file")"

tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

cat "$config_file" 2>/dev/null | awk -v begin="$marker_begin" -v end="$marker_end" -v keysfile="$rendered" '
    BEGIN {
        n = 0
        while ((getline kline < keysfile) > 0) {
            rendered_lines[n] = kline
            key = kline
            sub(/=.*/, "", key)
            gsub(/^[ \t]+/, "", key)
            gsub(/[ \t]+$/, "", key)
            if (key != "") keys[key] = 1
            n++
        }
        close(keysfile)
        in_table = 0
        in_marker = 0
        table_seen = 0
    }
    {
        line = $0
        if (line == begin) { in_marker = 1; next }
        if (in_marker) {
            if (line == end) in_marker = 0
            next
        }
        if (line ~ /^\[/) {
            in_table = (line ~ /^\[theme\.custom\][ \t]*$/)
            print line
            if (in_table) {
                table_seen = 1
                print begin
                for (i = 0; i < n; i++) print rendered_lines[i]
                print end
            }
            next
        }
        if (in_table) {
            key = line
            sub(/=.*/, "", key)
            gsub(/^[ \t]+/, "", key)
            gsub(/[ \t]+$/, "", key)
            if (key != "" && (key in keys)) {
                print "# launcher-material: " line
            } else {
                print line
            }
            next
        }
        print line
    }
    END {
        if (!table_seen) {
            print begin
            print "[theme.custom]"
            for (i = 0; i < n; i++) print rendered_lines[i]
            print end
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
