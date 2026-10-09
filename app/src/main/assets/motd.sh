#!/system/bin/sh
# Shown when a session opens. Edit freely; the launcher leaves an edited file alone.
printf '%s\n' \
  'Welcome to Termux Launcher.' \
  '' \
  '  tlstore                    Termux Launcher terminal apps' \
  '  tlstore display            set up graphics for Linux apps' \
  '  tlstore update             keep them current'

# The Nix edition has no pkg; its packages come from nix-on-droid.
if command -v pkg >/dev/null 2>&1; then
  printf '%s\n' \
    '' \
    '  pkg update && pkg upgrade  bring every package up to date' \
    '  pkg search <query>         find a package' \
    '  pkg install <package>      install one' \
    '  pkg uninstall <package>    remove one'
fi

if command -v termux-setup-storage >/dev/null 2>&1; then
  printf '%s\n' '  termux-setup-storage       reach your phone'"'"'s storage from ~/storage'
fi

# Only the Termux edition: the mirrors it picks from serve com.termux packages, which every other
# edition's prefix cannot run.
case "$PREFIX" in
  */com.termux/*)
    if command -v termux-change-repo >/dev/null 2>&1; then
      printf '%s\n' '  termux-change-repo         pick another mirror if downloads fail'
    fi
    ;;
esac

printf '%s\n' \
  '' \
  'Guide: https://picklehik3.github.io/termux-launcher-site/docs/'
