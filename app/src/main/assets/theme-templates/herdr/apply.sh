#!/bin/sh
# Splices the rendered launcher-material tables into herdr's config.toml and
# turns theme auto-switching on, so herdr follows the host terminal's light/dark
# report instead of a pinned theme.
#
# Marker blocks, all self-healing: `auto_switch = true` inside [theme], the
# rendered [theme.custom.dark] and [theme.custom.light] palettes, and the
# sidebar's rows in [ui.sidebar.spaces], [ui.sidebar.agents] and
# [ui.sidebar.agents.rows_by_agent]. A table that already exists gets our block
# spliced in right after its header, so no table is ever declared twice; one
# that does not is created, header and all, inside the block. Any pre-existing
# key of the same name in the same table - including an `auto_switch` of the
# user's own, and every line of a value that runs over several lines - is
# commented out with a "# launcher-material: " prefix rather than dropped, so
# undo.sh restores it exactly and TOML never sees a duplicate key. [theme] name
# is left alone, and so is the shared [theme.custom] table: our two palette
# subtables layer over it.
#
# The rendered file keeps every value on one line, so a key is read whole.
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
    # Which spliced table a header line opens, or "" for any other table.
    function table_of(line) {
        if (line ~ /^\[[ \t]*theme[ \t]*\]/) return "theme"
        if (line ~ /^\[[ \t]*theme\.custom\.dark[ \t]*\]/) return "dark"
        if (line ~ /^\[[ \t]*theme\.custom\.light[ \t]*\]/) return "light"
        if (line ~ /^\[[ \t]*ui\.sidebar\.spaces[ \t]*\]/) return "spaces"
        if (line ~ /^\[[ \t]*ui\.sidebar\.agents[ \t]*\]/) return "agents"
        if (line ~ /^\[[ \t]*ui\.sidebar\.agents\.rows_by_agent[ \t]*\]/) return "byagent"
        return ""
    }
    # How many brackets a line leaves open, strings skipped: a value that ends
    # above zero carries on over the next lines.
    function depth_of(line,   i, c, d, q) {
        d = 0; q = ""
        for (i = 1; i <= length(line); i++) {
            c = substr(line, i, 1)
            if (q != "") { if (c == "\\") i++; else if (c == q) q = ""; continue }
            if (c == "\"" || c == "\047") q = c
            else if (c == "#") break
            else if (c == "[" || c == "{") d++
            else if (c == "]" || c == "}") d--
        }
        return d
    }
    function emit(t,   i) {
        if (t == "theme") { print "auto_switch = true"; return }
        for (i = 0; i < count[t]; i++) print body[t, i]
    }
    BEGIN {
        n_tables = split("theme dark light spaces agents byagent", tables, " ")
        keys["theme", "auto_switch"] = 1
        header["theme"] = "[theme]"
        header["dark"] = "[theme.custom.dark]"
        header["light"] = "[theme.custom.light]"
        header["spaces"] = "[ui.sidebar.spaces]"
        header["agents"] = "[ui.sidebar.agents]"
        header["byagent"] = "[ui.sidebar.agents.rows_by_agent]"
        section = ""
        while ((getline kline < keysfile) > 0) {
            if (kline ~ /^\[/) { section = table_of(kline); continue }
            if (section == "" || section == "theme") continue
            if (kline ~ /^[ \t]*#/ || kline ~ /^[ \t]*$/) continue
            key = trimmed_key(kline)
            if (key == "") continue
            body[section, count[section]++] = kline
            keys[section, key] = 1
        }
        close(keysfile)
        table = ""
        in_marker = 0
        open_depth = 0
    }
    {
        line = $0
        if (line == begin) { in_marker = 1; next }
        if (in_marker) {
            if (line == end) in_marker = 0
            next
        }
        # The rest of a value already being commented out.
        if (open_depth > 0) {
            open_depth += depth_of(line)
            print "# launcher-material: " line
            next
        }
        if (line ~ /^\[/) {
            table = table_of(line)
            print line
            if (table != "") {
                seen[table] = 1
                print begin; emit(table); print end
            }
            next
        }
        if (table != "") {
            key = trimmed_key(line)
            if (key != "" && line !~ /^[ \t]*#/ && ((table, key) in keys)) {
                open_depth = depth_of(line)
                print "# launcher-material: " line
                next
            }
        }
        print line
    }
    END {
        for (i = 1; i <= n_tables; i++) {
            t = tables[i]
            if (seen[t] || (t != "theme" && !count[t])) continue
            print begin; print header[t]; emit(t); print end
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
