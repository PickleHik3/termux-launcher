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
# that does not is created, header and all, inside the block.
#
# The theme tables are ours: a pre-existing key of the same name there -
# including an `auto_switch` of the user's own, and every line of a value that
# runs over several lines - is commented out with a "# launcher-material: "
# prefix rather than dropped, so undo.sh restores it exactly and TOML never
# sees a duplicate key. [theme] name is left alone, and so is the shared
# [theme.custom] table: our two palette subtables layer over it.
#
# The sidebar rows are the user's: ours are defaults for a config that has no
# row of that name, and a row the user wrote keeps its content and its colours
# across every re-theme. A row an earlier version of this hook commented out is
# put back as it was, and ours stands aside for it.
#
# The rendered file keeps every value on one line, so a key is read whole.
#
# Pure POSIX sh/awk: herdr ships no Python, and this hook must not assume one.
set -eu

config_file="${XDG_CONFIG_HOME:-$HOME/.config}/herdr/config.toml"
rendered="${TERMUX_THEME_OUTPUT:?TERMUX_THEME_OUTPUT not set}"
marker_begin="# >>> launcher-material >>>"
marker_end="# <<< launcher-material <<<"
restore_prefix="# launcher-material: "

mkdir -p "$(dirname "$config_file")"

tmp_file="$(mktemp "${config_file}.tmp.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT

# The config is read twice: once to learn which sidebar rows the user owns,
# once to write it out. A missing config reads as empty both times.
existing="$config_file"
if [ ! -f "$existing" ]; then
    existing=/dev/null
fi

awk -v begin="$marker_begin" -v end="$marker_end" -v prefix="$restore_prefix" -v keysfile="$rendered" '
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
    # The line without our comment prefix, or the line itself.
    function unprefixed(line) {
        return substr(line, 1, plen) == prefix ? substr(line, plen + 1) : line
    }
    # How many of our lines a table has to say, the user-owned ones left out.
    function pending(t,   i, n) {
        if (t == "theme") return 1
        n = 0
        for (i = 0; i < count[t]; i++) if (!((t, bodykey[t, i]) in user)) n++
        return n
    }
    function emit(t,   i) {
        if (t == "theme") { print "auto_switch = true"; return }
        for (i = 0; i < count[t]; i++) if (!((t, bodykey[t, i]) in user)) print body[t, i]
    }
    BEGIN {
        plen = length(prefix)
        n_tables = split("theme dark light spaces agents byagent", tables, " ")
        sidebar["spaces"] = 1; sidebar["agents"] = 1; sidebar["byagent"] = 1
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
            # count[] starts unset, which reads as "" in a subscript: number it first.
            idx = count[section] + 0
            bodykey[section, idx] = key
            body[section, idx] = kline
            count[section] = idx + 1
            keys[section, key] = 1
        }
        close(keysfile)
        table = ""
        in_marker = 0
        open_depth = 0
        restore_depth = 0
        scan_table = ""
        scan_marker = 0
        scan_depth = 0
    }
    # First read: the sidebar rows the user owns, written plainly or commented
    # out by an earlier version of this hook. Our own marker blocks are not theirs.
    FNR == NR {
        line = $0
        if (line == begin) { scan_marker = 1; next }
        if (scan_marker) { if (line == end) scan_marker = 0; next }
        line = unprefixed(line)
        if (scan_depth > 0) { scan_depth += depth_of(line); next }
        if (line ~ /^\[/) { scan_table = table_of(line); next }
        if (scan_table in sidebar && line !~ /^[ \t]*#/) {
            key = trimmed_key(line)
            if (key != "" && (scan_table, key) in keys) {
                user[scan_table, key] = 1
                scan_depth = depth_of(line)
            }
        }
        next
    }
    # Second read: the config written out.
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
            print prefix line
            next
        }
        # The rest of a sidebar row being put back.
        if (restore_depth > 0) {
            line = unprefixed(line)
            restore_depth += depth_of(line)
            print line
            next
        }
        if (line ~ /^\[/) {
            table = table_of(line)
            print line
            if (table != "" && pending(table) > 0) {
                seen[table] = 1
                print begin; emit(table); print end
            } else if (table != "") {
                seen[table] = 1
            }
            next
        }
        if (table in sidebar) {
            # A row the user wrote that an earlier hook commented out comes back whole.
            if (substr(line, 1, plen) == prefix) {
                line = unprefixed(line)
                restore_depth = depth_of(line)
                print line
                next
            }
        } else if (table != "") {
            key = trimmed_key(line)
            if (key != "" && line !~ /^[ \t]*#/ && ((table, key) in keys)) {
                open_depth = depth_of(line)
                print prefix line
                next
            }
        }
        print line
    }
    END {
        for (i = 1; i <= n_tables; i++) {
            t = tables[i]
            if (seen[t] || pending(t) == 0) continue
            print begin; print header[t]; emit(t); print end
        }
    }
' "$existing" "$existing" >"$tmp_file"

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
