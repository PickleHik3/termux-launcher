#!/usr/bin/env bash
# Prints every rendition and protocol added by project-docs/plans/kitty-protocol-features.md,
# so they can be eyeballed in one screen. Run it *inside* the terminal under test.
#
# Read-only: it writes escape sequences to its own tty and touches nothing else. The keyboard
# protocol section leaves the flags as it found them.
#
# Usage:
#   sh project-docs/verification/test-terminal-protocols.sh          # renditions and links
#   sh project-docs/verification/test-terminal-protocols.sh --keys   # also read raw key bytes

set -u

esc=$(printf '\033')
st=$(printf '\033\\')

section() {
    printf '\n%s[1m== %s%s[0m\n' "$esc" "$1" "$esc"
}

section 'Underline styles (SGR 4:x, SGR 21)'
printf '  %s[4:1mSINGLE%s[0m  %s[4:2mDOUBLE%s[0m  %s[4:3mCURLY%s[0m  %s[4:4mDOTTED%s[0m  %s[4:5mDASHED%s[0m\n' \
    "$esc" "$esc" "$esc" "$esc" "$esc" "$esc" "$esc" "$esc" "$esc" "$esc"
printf '  %s[4mSGR-4-plain%s[0m  %s[21mSGR-21-double%s[0m  %s[4:9mUNKNOWN-4:9-is-single%s[0m\n' \
    "$esc" "$esc" "$esc" "$esc" "$esc" "$esc"
printf '  %s[4:3mCURLY%s[24m then SGR 24 clears it: NOT-UNDERLINED%s[0m\n' "$esc" "$esc" "$esc"

section 'Decoration colour (SGR 58 / 59)'
printf '  %s[58;5;9m%s[4:3mindexed-red-curly%s[0m  %s[58;2;0;180;255m%s[4:1mtruecolor-blue-line%s[0m' \
    "$esc" "$esc" "$esc" "$esc" "$esc" "$esc"
printf '  %s[58;5;9m%s[4m%s[59mSGR-59-back-to-text-colour%s[0m\n' "$esc" "$esc" "$esc" "$esc"
printf '  %s[31m%s[58;5;10mred-text-green-underline%s[0m\n' "$esc" "$esc" "$esc"

section 'Underline style survives a resize'
printf '  Resize the pane or rotate the device: the wave below must stay a wave.\n'
printf '  %s[4:3m' "$esc"
i=0
while [ $i -lt 60 ]; do printf 'reflow-me '; i=$((i + 1)); done
printf '%s[0m\n' "$esc"

section 'OSC 8 hyperlinks'
printf '  %s]8;;https://example.com/plain%sPLAIN-LINK%s]8;;%s  then-not-a-link\n' "$esc" "$st" "$esc" "$st"
printf '  %s]8;id=split;https://example.com/same%sONE%s]8;;%s gap %s]8;id=split;https://example.com/same%sLINK%s]8;;%s (one pool entry)\n' \
    "$esc" "$st" "$esc" "$st" "$esc" "$st" "$esc" "$st"
printf '  %s]8;;file:///etc/hosts%sfile-scheme-copy-only%s]8;;%s  (must offer Copy, no Open)\n' "$esc" "$st" "$esc" "$st"
printf '  %s]8;;https://example.com/\001bad%srejected-control-char%s]8;;%s  (must not be a link)\n' "$esc" "$st" "$esc" "$st"
printf '  Tap each: the dialog must show the full target before anything opens.\n'

section 'OSC 133 shell integration'
printf '  Marking the next 40 rows as prompts so some scroll into history.\n'
i=1
while [ $i -le 40 ]; do
    printf '%s]133;A%sprompt-mark-row-%s\n' "$esc" "$st" "$i"
    i=$((i + 1))
done
printf '  Now run terminal.jump_previous_prompt / _next_prompt from the palette or\n'
printf '  /v1/agent/execute. Previous must scroll up; at the top it must answer\n'
printf '  409 no_prompt_mark rather than pretending to move.\n'

section 'Cursor trail'
printf '  Watch the cursor as it jumps across this line and back:\n'
i=0
while [ $i -lt 6 ]; do
    printf '%s[999G|%s[1G|' "$esc" "$esc"
    sleep 1
    i=$((i + 1))
done
printf '\n  A streak should follow it. Turn it off with appearance.toggle_cursor_trail\n'
printf '  and repeat: the cursor must then jump with no streak.\n'

section 'Glyph overhang vs neighbour background fill'
printf '  Wide symbol glyphs (Nerd Font icons, powerline arrows) whose ink overhangs the\n'
printf '  cell must not be clipped by the next cell'"'"'s background. Each icon below sits\n'
printf '  directly against a filled cell; the icon'"'"'s right edge must stay intact.\n'
printf '  %s[44m\356\202\260%s[41m  %s[0m  %s[42m\356\202\262%s[45m  %s[0m  ' \
    "$esc" "$esc" "$esc" "$esc" "$esc" "$esc"
printf '%s[100m\356\230\215%s[101mX%s[0m  %s[7m\357\200\223%s[27m%s[46m  %s[0m\n' \
    "$esc" "$esc" "$esc" "$esc" "$esc" "$esc" "$esc"
printf '  Same icons with a default background to their right (must also stay intact):\n'
printf '  %s[44m\356\202\260%s[0m.  %s[100m\356\230\215%s[0mX  %s[7m\357\200\223%s[0m.\n' \
    "$esc" "$esc" "$esc" "$esc" "$esc" "$esc"

section 'Kitty graphics animation (terminal-driven)'
printf '  A block should cycle red, green, blue, yellow at ~3 flips per second,\n'
printf '  continuing after this script exits. Delete it with: printf "\\033_Ga=d,d=I,i=707\\033\\\\\\\\"\n'
kg_px() {
    i=0
    while [ $i -lt 64 ]; do printf "$1"; i=$((i + 1)); done
}
kg_red=$(kg_px '\377\000\000\377' | base64 -w0)
kg_green=$(kg_px '\000\377\000\377' | base64 -w0)
kg_blue=$(kg_px '\000\000\377\377' | base64 -w0)
kg_yellow=$(kg_px '\377\377\000\377' | base64 -w0)
printf '\033_Gi=707,a=t,q=2,f=32,s=8,v=8;%s\033\\' "$kg_red"
printf '\033_Gi=707,a=f,q=2,f=32,s=8,v=8,z=300;%s\033\\' "$kg_green"
printf '\033_Gi=707,a=f,q=2,f=32,s=8,v=8,z=300;%s\033\\' "$kg_blue"
printf '\033_Gi=707,a=f,q=2,f=32,s=8,v=8,z=300;%s\033\\' "$kg_yellow"
printf '\033_Gi=707,a=p,q=2,c=6,r=3\033\\'
printf '\033_Gi=707,a=a,r=1,z=300\033\\'
printf '\033_Gi=707,a=a,s=3,v=1\033\\'
printf '\n\n\n\n'

if [ "${1:-}" = "--keys" ]; then
    section 'Kitty keyboard protocol'
    printf '  Enabling disambiguate and querying. Expect the reply ^[[?1u, then one escape\n'
    printf '  code per key: ctrl+p -> ^[[112;5u, ctrl+a -> ^[[97;5u, F5 -> ^[[15~,\n'
    printf '  Escape -> ^[[27u. Enter, Tab and Backspace must stay legacy (^M, ^I, ^?).\n'
    printf '  Press ctrl+c when done; the flags are restored on the way out.\n\n'
    printf '%s[=1;1u%s[?u' "$esc" "$esc"
    # shellcheck disable=SC2064
    trap "printf '%s[=0;1u\n' '$esc'" EXIT INT
    cat -v
    printf '%s[=0;1u\n' "$esc"
fi

section 'Done'
printf '  Every section above is a rendering or protocol claim in\n'
printf '  project-docs/plans/kitty-protocol-features.md.\n'
