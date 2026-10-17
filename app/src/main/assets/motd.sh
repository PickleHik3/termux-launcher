#!/system/bin/sh
# Shown when a session opens. Edit freely; the launcher leaves an edited file alone.

# One row per line: "command|what it does", or "#Heading" to start a section. Rows stay ASCII so
# the padding can count them.
rows='#Apps
tlstore|browse and install terminal apps
tlstore display|set up graphics for Linux apps
tlstore update|keep your apps current'

# The Nix edition has no pkg; its packages come from nix-on-droid.
if command -v pkg >/dev/null 2>&1; then
  rows="$rows
#Packages
pkg update && pkg upgrade|update every package
pkg search <query>|find a package
pkg install <name>|install one
pkg uninstall <name>|remove one"
fi

setup=''
if command -v termux-setup-storage >/dev/null 2>&1; then
  setup="$setup
termux-setup-storage|reach your phone's storage"
fi
# Only the Termux edition: the mirrors it picks from serve com.termux packages, which every other
# edition's prefix cannot run.
case "$PREFIX" in
  */com.termux/*)
    if command -v termux-change-repo >/dev/null 2>&1; then
      setup="$setup
termux-change-repo|switch mirror if downloads fail"
    fi
    ;;
esac
[ -n "$setup" ] && rows="$rows
#Setup$setup"

guide='https://picklehik3.github.io/termux-launcher-site/docs/'

e=$(printf '\033')
# The launcher writes the wallpaper's Material palette here; without it, fall back to ANSI colours
# so the theme's own palette still applies.
[ -r "$HOME/.termux/material-colors.sh" ] && . "$HOME/.termux/material-colors.sh"
rgb() { h=${1#\#}; t=${h#??}; printf '%s' "$2;2;$((0x${h%????}));$((0x${t%??}));$((0x${h#????}))"; }
if [ -n "$NO_COLOR" ]; then
  P='' S='' O='' D='' T='' I='' PB='' R=''
elif [ -n "$TERMUX_MATERIAL_PRIMARY" ]; then
  P="$e[1;$(rgb "$TERMUX_MATERIAL_PRIMARY" 38)m"
  S="$e[1;$(rgb "$TERMUX_MATERIAL_TERTIARY" 38)m"
  O="$e[$(rgb "$TERMUX_MATERIAL_OUTLINE" 38)m"
  D="$e[$(rgb "$TERMUX_MATERIAL_ON_SURFACE_VARIANT" 38)m"
  T="$e[$(rgb "$TERMUX_MATERIAL_ON_SURFACE" 38)m"
  PB="$e[1;$(rgb "$TERMUX_MATERIAL_PRIMARY" 48);$(rgb "$TERMUX_MATERIAL_ON_PRIMARY" 38)m"
  I="$e[3m" R="$e[0m"
else
  P="$e[1;36m" S="$e[1;35m" O="$e[90m" D="$e[2m" T="$e[0m" I="$e[3m" PB="$e[1;46;30m" R="$e[0m"
fi
# The pill's round caps take the pill's colour as their foreground.
PC=$(printf '%s' "$P" | sed 's/\[1;/[/')

cols=${COLUMNS:-}
[ -n "$cols" ] || cols=$(stty size 2>/dev/null | cut -d' ' -f2)
[ -n "$cols" ] || cols=80

cw=0 dw=0
while IFS='|' read -r cmd desc; do
  case "$cmd" in '#'*) continue ;; esac
  [ ${#cmd} -gt $cw ] && cw=${#cmd}
  [ ${#desc} -gt $dw ] && dw=${#desc}
done <<EOF
$rows
EOF

# Side by side when it fits, stacked when it does not, and no box on a sliver of a pane.
wide=$((2 + cw + 3 + dw + 2))
tall=$((2 + dw + 4))
if [ $((wide + 2)) -le "$cols" ]; then inner=$wide
elif [ $((tall + 2)) -le "$cols" ]; then inner=$tall
else inner=0
fi

spaces() { [ "$1" -gt 0 ] && printf "%$1s" ''; }
line() { i=0 s=''; while [ $i -lt "$1" ]; do s="$s─"; i=$((i + 1)); done; printf '%s' "$s"; }

# A command in colour: tool names bold, arguments quiet, && dimmed.
styled() {
  set -f; out='' tool=1
  for w in $1; do
    case "$w" in
      '&&') out="$out$D$w$R "; tool=1 ;;
      '<'*) out="$out$D$I$w$R " ;;
      *) if [ $tool -eq 1 ]; then out="$out$P$w$R "; tool=0; else out="$out$T$w$R "; fi ;;
    esac
  done
  set +f; printf '%s' "${out% }"
}

hour=$(date +%H); hour=${hour#0}
if [ "$hour" -lt 12 ]; then greet='Good morning'
elif [ "$hour" -lt 18 ]; then greet='Good afternoon'
else greet='Good evening'
fi

title='Termux Launcher'
width=$((inner + 2))
[ $inner -eq 0 ] && width=$((${#title} + 4 + 2 + ${#greet}))
printf '%s%s%s %s %s%s%s' "$PC" '' "$PB" "$title" "$R$PC" '' "$R"
spaces $((width - ${#title} - 4 - ${#greet}))
printf '%s%s%s\n' "$D" "$greet" "$R"

if [ $inner -eq 0 ]; then
  while IFS='|' read -r cmd desc; do
    case "$cmd" in
      '#'*) printf '\n%s%s%s\n' "$S" "${cmd#\#}" "$R" ;;
      *) printf '  %s\n    %s%s%s\n' "$(styled "$cmd")" "$D" "$desc" "$R" ;;
    esac
  done <<EOF
$rows
EOF
else
  first=1
  while IFS='|' read -r cmd desc; do
    case "$cmd" in
      '#'*)
        name=${cmd#\#}
        if [ $first -eq 1 ]; then corner='╭' end='╮' first=0; else corner='├' end='┤'; fi
        printf '%s%s─ %s%s%s %s%s%s\n' "$O" "$corner" "$S" "$name" "$R$O" \
          "$(line $((inner - 3 - ${#name})))" "$end" "$R"
        ;;
      *)
        if [ $inner -eq $wide ]; then
          printf '%s│%s  %s' "$O" "$R" "$(styled "$cmd")"
          spaces $((cw - ${#cmd} + 3))
          printf '%s%s%s' "$D" "$desc" "$R"
          spaces $((dw - ${#desc} + 2))
        else
          printf '%s│%s  %s' "$O" "$R" "$(styled "$cmd")"
          spaces $((inner - 2 - ${#cmd}))
          printf '%s│%s\n%s│%s    %s%s%s' "$O" "$R" "$O" "$R" "$D" "$desc" "$R"
          spaces $((inner - 4 - ${#desc}))
        fi
        printf '%s│%s\n' "$O" "$R"
        ;;
    esac
  done <<EOF
$rows
EOF
  printf '%s╰%s╯%s\n' "$O" "$(line "$inner")" "$R"
fi

# The guide as a link the terminal opens on a tap, in its own card as wide as the one above: label
# beside the link, label in the border, or no card.
shown='Tap to read the docs'
U=''
[ -n "$R" ] && U="$e[4m"
link() { printf '%s]8;;%s%s\\%s%s%s%s%s]8;;%s\\' "$e" "$guide" "$e" "$D" "$U" "$shown" "$R" "$e" "$e"; }
gw=$inner
[ $gw -eq 0 ] && gw=$((${#shown} + 4))
if [ $((gw + 2)) -gt "$cols" ] || [ $gw -lt $((${#shown} + 4)) ]; then
  printf '\n%sGuide%s  %s\n' "$S" "$R" "$(link)"
elif [ $gw -ge $((2 + 5 + 2 + ${#shown} + 2)) ]; then
  printf '%s╭%s╮%s\n' "$O" "$(line "$gw")" "$R"
  printf '%s│%s  %sGuide%s  %s' "$O" "$R" "$S" "$R" "$(link)"
  spaces $((gw - 9 - ${#shown}))
  printf '%s│%s\n%s╰%s╯%s\n' "$O" "$R" "$O" "$(line "$gw")" "$R"
else
  printf '%s╭─ %sGuide%s %s╮%s\n' "$O" "$S" "$R$O" "$(line $((gw - 8)))" "$R"
  printf '%s│%s  %s' "$O" "$R" "$(link)"
  spaces $((gw - 2 - ${#shown}))
  printf '%s│%s\n%s╰%s╯%s\n' "$O" "$R" "$O" "$(line "$gw")" "$R"
fi
