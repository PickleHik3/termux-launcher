#!/bin/sh
# Terminal reply probe for Fast-engine parity with first-party Ghostty.
#
# Run it inside the terminal under test:  sh ghostty-parity-probe.sh [output-file]
# It sends each query on its own, then a DA1 request as a sentinel, and reads the
# terminal's reply until the sentinel's answer arrives or a timeout passes. One line per
# probe goes to the output file:
#
#   <probe id> TAB <bytes sent, escaped> TAB <reply, escaped, or <none>> [TAB <note>]
#
# Escaping: ESC is \e, BEL \a, other control bytes and bytes >= 0x7f \xNN, backslash \\.
# Notes: "timeout" (the sentinel never answered; the reply column holds what did arrive),
# "consent" marks probes a terminal may hold behind a permission dialog.
#
# Needs only sh, stty, dd, od and awk (all in the Termux bootstrap). The tty state is
# saved first and restored on exit, interrupt or error.

set -u

OUT=${1:-./ghostty-parity-probe-$(date +%Y%m%d-%H%M%S).txt}
TTY=/dev/tty
# Read slices are 0.1 s; a probe waits at most SLICES slices for its sentinel.
SLICES=${PROBE_SLICES:-30}
CONSENT_SLICES=${PROBE_CONSENT_SLICES:-30}

SAVED=$(stty -g <"$TTY")
restore() {
    stty "$SAVED" <"$TTY" 2>/dev/null
}
trap 'restore' EXIT
trap 'restore; exit 130' INT TERM HUP

stty raw -echo min 0 time 1 <"$TTY"

# Hex of everything waiting on the tty, as one string of lowercase pairs, no spaces.
read_hex() {
    dd bs=4096 count=1 <"$TTY" 2>/dev/null | od -An -v -tx1 | tr -d ' \n'
}

# Drain anything left over from an earlier probe.
drain() {
    n=0
    while [ $n -lt 5 ]; do
        h=$(read_hex)
        [ -z "$h" ] && n=$((n + 1)) || n=0
    done
}

# Escape a hex string (pairs) for the result file.
escape_hex() {
    printf '%s' "$1" | awk '{
        s = ""
        for (i = 1; i <= length($0); i += 2) {
            b = substr($0, i, 2)
            v = 0
            for (j = 1; j <= 2; j++) {
                c = index("0123456789abcdef", substr(b, j, 1)) - 1
                v = v * 16 + c
            }
            if (v == 27) s = s "\\e"
            else if (v == 7) s = s "\\a"
            else if (v == 92) s = s "\\\\"
            else if (v >= 32 && v < 127) s = s sprintf("%c", v)
            else s = s "\\x" b
        }
        print s
    }'
}

# Hex of a printf-format string.
to_hex() {
    printf "$1" | od -An -v -tx1 | tr -d ' \n'
}

# Strip the last DA1 reply (ESC [ ? digits/semicolons c) from a hex string. Prints the rest,
# or nothing when no DA1 reply is present (the caller tells the two cases apart).
SENTINEL_RE='1b5b3f(3[0-9b])+63'
has_sentinel() {
    printf '%s' "$1" | grep -Eq "$SENTINEL_RE"
}
strip_sentinel() {
    # The sentinel is the last DA1 reply in the buffer.
    printf '%s' "$1" | awk -v re="$SENTINEL_RE" '{
        s = $0; last = 0; pos = 0
        while (match(substr(s, pos + 1), re)) {
            # Only accept matches on a byte boundary.
            start = pos + RSTART
            if ((start - 1) % 2 == 0) { last = start; lastlen = RLENGTH }
            pos = pos + RSTART
        }
        if (last == 0) { print s; exit }
        print substr(s, 1, last - 1) substr(s, last + lastlen)
    }'
}

# probe <id> <printf-format of bytes to send> [consent|nosentinel]
probe() {
    id=$1
    fmt=$2
    kind=${3:-}
    drain
    sent_hex=$(to_hex "$fmt")
    if [ "$kind" = nosentinel ]; then
        printf "$fmt" >"$TTY"
    else
        printf "$fmt"'\033[c' >"$TTY"
    fi
    limit=$SLICES
    [ "$kind" = consent ] && limit=$CONSENT_SLICES
    acc=""
    i=0
    q=0
    got=0
    while [ $i -lt "$limit" ]; do
        h=$(read_hex)
        acc="$acc$h"
        if [ "$kind" = nosentinel ]; then
            # No sentinel: stop after 5 quiet slices once something arrived, else run out the clock.
            if [ -z "$h" ] && [ -n "$acc" ]; then
                q=$((q + 1)); [ $q -ge 5 ] && { got=1; break; }
            else
                q=0
            fi
        elif has_sentinel "$acc"; then
            got=1
            # Collect whatever trails the sentinel for a moment.
            h=$(read_hex); acc="$acc$h"
            break
        fi
        i=$((i + 1))
    done
    note=""
    if [ "$kind" = nosentinel ]; then
        reply=$acc
        [ $got -eq 0 ] && [ -n "$acc" ] && note="timeout"
    elif [ $got -eq 1 ]; then
        reply=$(strip_sentinel "$acc")
    else
        reply=$acc
        note="timeout"
    fi
    [ "$kind" = consent ] && note="${note:+$note,}consent"
    if [ -z "$reply" ]; then
        rtext="<none>"
    else
        rtext=$(escape_hex "$reply")
    fi
    line=$(printf '%s\t%s\t%s' "$id" "$(escape_hex "$sent_hex")" "$rtext")
    [ -n "$note" ] && line="$line	$note"
    printf '%s\n' "$line" >>"$OUT"
}
q=0

hexname() {
    printf '%s' "$1" | od -An -v -tx1 | tr -d ' \n' | tr 'a-f' 'A-F'
}

: >"$OUT"
printf '# ghostty-parity-probe v1  TERM=%s  date=%s\n' "${TERM:-}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >>"$OUT"

# --- Identity -------------------------------------------------------------------------
probe da1 '\033[c' nosentinel
probe da2 '\033[>c'
probe da3 '\033[=c'
probe xtversion '\033[>q'

# --- DECRQM: every mode Ghostty 1.4.0 knows (src/terminal/modes.zig), plus an unknown one.
for m in 2 4 12 20; do
    probe "decrqm-ansi-$m" "\\033[$m\$p"
done
for m in 1 3 4 5 6 7 8 9 12 25 40 45 47 66 67 69 1000 1002 1003 1004 1005 1006 1007 \
         1015 1016 1035 1036 1039 1045 1047 1048 1049 2004 2026 2027 2031 2033 2048 5522 9999; do
    probe "decrqm-dec-$m" "\\033[?$m\$p"
done

# --- DECRQSS --------------------------------------------------------------------------
probe decrqss-sgr '\033P$qm\033\\'
probe decrqss-decscusr '\033P$q q\033\\'
probe decrqss-decstbm '\033P$qr\033\\'
probe decrqss-decslrm '\033P$qs\033\\'

# --- XTGETTCAP ------------------------------------------------------------------------
for cap in TN Co colors RGB Tc Smulx Setulc Ss Se Ms Sync kbs smcup cnorm setrgbf fsl XX; do
    probe "xtgettcap-$cap" "\\033P+q$(hexname "$cap")\\033\\\\"
done

# --- DSR ------------------------------------------------------------------------------
probe dsr-5 '\033[5n'
probe dsr-6 '\033[6n'
probe dsr-dec-6 '\033[?6n'
probe dsr-996 '\033[?996n'
probe dsr-998 '\033[?998n'

# --- Kitty keyboard -------------------------------------------------------------------
probe kitty-kbd-query '\033[?u'
probe kitty-kbd-push1-query '\033[>1u\033[?u'
probe kitty-kbd-pop-query '\033[<u\033[?u'

# --- Colours: OSC 4/5/10/11/12 queries and resets -------------------------------------
probe osc4-0 '\033]4;0;?\033\\'
probe osc4-1 '\033]4;1;?\007'
probe osc4-255 '\033]4;255;?\033\\'
probe osc5-0 '\033]5;0;?\033\\'
probe osc10 '\033]10;?\033\\'
probe osc11 '\033]11;?\033\\'
probe osc12 '\033]12;?\033\\'
probe osc10-bel '\033]10;?\007'
probe osc104 '\033]104\033\\'
probe osc110 '\033]110\033\\'
probe osc111 '\033]111\033\\'
probe osc112 '\033]112\033\\'

# --- OSC 21 kitty colour stack, OSC 22 pointer shape ----------------------------------
probe osc21-fg-bg-cursor '\033]21;foreground=?;background=?;cursor=?\033\\'
probe osc21-palette '\033]21;0=?;15=?;selection_background=?\033\\'
probe osc22-query '\033]22;?__current__\033\\'

# --- Clipboard reads (may wait on a consent dialog) -----------------------------------
probe osc52-read '\033]52;c;?\033\\' consent
probe osc5522-read '\033]5522;type=read;Lg==\033\\' consent

# --- Program status, semantic prompt --------------------------------------------------
probe osc7501-query '\033]7501;?\033\\'
probe osc133-a '\033]133;A\033\\'
probe osc133-b '\033]133;B\033\\'
probe osc133-d '\033]133;D;0\033\\'

# --- Kitty graphics query --------------------------------------------------------------
probe kitty-gfx-query '\033_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA\033\\'

# --- Size reports ---------------------------------------------------------------------
probe csi-14t '\033[14t'
probe csi-16t '\033[16t'
probe csi-18t '\033[18t'
probe csi-21t '\033[21t'

# --- In-band size report and colour-scheme report on enabling the mode -----------------
probe mode2048-enable '\033[?2048h'
probe mode2048-disable '\033[?2048l'
probe mode2031-enable '\033[?2031h'
probe mode2031-dsr996 '\033[?996n'
probe mode2031-disable '\033[?2031l'

# --- ENQ ------------------------------------------------------------------------------
probe enq '\005'

printf '# done\n' >>"$OUT"
restore
printf 'probe results written to %s\n' "$OUT"
