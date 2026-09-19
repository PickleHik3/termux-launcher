# Linux apps from a distro

The launcher already lists the Linux apps installed in Termux itself: install `firefox` with
`pkg`, and Firefox is in the app drawer the next time you return to the launcher, with its icon,
and a tap opens it on the [Display](X11_Display.md).

Apps installed **inside a proot distro** — Debian, Arch, Ubuntu — are not listed, because the
launcher only looks in Termux's own `$PREFIX`. This page shows how to put them in the drawer
yourself. It is one small file per app, and everything in it was checked on a phone against a
Debian 13 container and `proot-distro` 5.8.0.

## Before you start

1. The display is switched on and the keyboard layouts are installed — see
   [Turn it on](X11_Display.md#turn-it-on).
2. A distro is installed and the app is installed inside it:

   ```sh
   pkg install proot-distro
   proot-distro install debian
   proot-distro login debian
   # inside the container:
   apt update && apt install -y x11-apps
   exit
   ```

## Check it runs, first

Before writing anything, prove the app reaches the display. In a Termux shell:

```sh
termux-x11 :0 &
proot-distro login debian --shared-x11 -e DISPLAY=:0 -- xeyes
```

Swipe to the Display place and the eyes are there.

Three things in that line matter, and only those three:

- **`--shared-x11`** hands the container the display's socket directory. Without it the container
  has no `/tmp/.X11-unix` at all and every app says *unable to open display*. `--shared-tmp` also
  works — it shares the whole of Termux's temporary directory — but `--shared-x11` shares just the
  one thing the app needs.
- **`-e DISPLAY=:0`** puts `DISPLAY` inside the container. Exporting it in your Termux shell does
  not carry it across; nothing of your shell's environment does.
- **Nothing else.** You do not need `-ac` on the server, and you do not need `xauth` in the
  container. The display accepts the connection because both sides are the same Android app.

Sound needs no setup either: `proot-distro` already sets `PULSE_SERVER=127.0.0.1` inside the
container for you.

## Put it in the drawer

The launcher reads `.desktop` files from two directories, exactly the way a desktop menu does:

```
$PREFIX/share/applications          # apt's own, leave it alone
$PREFIX/local/share/applications    # yours
```

Write one file per app in the second one. For `xeyes`:

```sh
mkdir -p $PREFIX/local/share/applications
cat > $PREFIX/local/share/applications/debian-xeyes.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Eyes (Debian)
Comment=A pair of eyes that follow the pointer
Exec=proot-distro login debian --shared-x11 -e DISPLAY=${DISPLAY:-:0} -- /bin/sh -c 'xeyes'
Terminal=false
EOF
```

Return to the launcher and open the app drawer: **Eyes (Debian)** is there. A tap starts the
display if it is not already running, opens the app on it full size, and takes you to the Display
place. It can be pinned and searched like any other app.

That is the whole mechanism. The rest of this page is how to fill the file in for a real app.

### Name, comment and icon

Every distro app ships its own `.desktop` file inside the container. Read it and copy from it
rather than inventing the values. The container's files live under its rootfs:

```sh
# proot-distro 5.x and newer
ls $PREFIX/var/lib/proot-distro/containers/debian/rootfs/usr/share/applications

# older proot-distro
ls $PREFIX/var/lib/proot-distro/installed-rootfs/debian/usr/share/applications
```

`Name` and `Comment` can be copied across as they are. Adding the distro to the name — *Typora
(Debian)* — is worth it once you have the same app in two places.

`Icon` is the one that needs changing. The container's file names a theme icon, like
`Icon=typora`, and the launcher would look for that name in Termux's icons, not the container's.
Point it at the file instead, with a full path:

```sh
find $PREFIX/var/lib/proot-distro/containers/debian/rootfs/usr/share/icons \
     $PREFIX/var/lib/proot-distro/containers/debian/rootfs/usr/share/pixmaps \
     -name 'typora.*'
```

Take the largest PNG — the launcher uses **PNG only**, so an icon that exists only as `.svg`
cannot be used; leave `Icon` out and the app gets the default tile.

```
Icon=/data/data/com.termux/files/usr/var/lib/proot-distro/containers/debian/rootfs/usr/share/icons/hicolor/256x256/apps/typora.png
```

Write the path out in full. `$PREFIX` is not expanded inside a `.desktop` file.

### The Exec line

Take the container's own `Exec=`, drop the `%f`, `%u`, `%U` and other `%` codes from it — they
are placeholders for files you are not passing — and wrap what is left:

```
Exec=proot-distro login <container> --shared-x11 -e DISPLAY=${DISPLAY:-:0} -- /bin/sh -c '<command>'
```

So `Exec=typora %U` inside the container becomes:

```
Exec=proot-distro login debian --shared-x11 -e DISPLAY=${DISPLAY:-:0} -- /bin/sh -c 'typora'
```

If the command itself contains a single quote, double it up the usual shell way (`'\''`).

### StartupWMClass

If the container's file has a `StartupWMClass=` line, copy it across. It is how the launcher
matches a window on the display back to the app that opened it, so the window chip gets the right
name and icon.

## A worked example

Typora, installed in a Debian container. Its own file says:

```
[Desktop Entry]
Name=Typora
Comment=a minimal Markdown reading & writing app.
Exec=typora %U
Icon=typora
Type=Application
```

The launcher file:

```
[Desktop Entry]
Type=Application
Name=Typora (Debian)
Comment=a minimal Markdown reading & writing app.
Icon=/data/data/com.termux/files/usr/var/lib/proot-distro/containers/debian/rootfs/usr/share/icons/hicolor/256x256/apps/typora.png
Exec=proot-distro login debian --shared-x11 -e DISPLAY=${DISPLAY:-:0} -- /bin/sh -c 'typora --no-sandbox'
Terminal=false
```

The `--no-sandbox` is explained below.

## When something is off

- **"Running as root without --no-sandbox is not supported."** Everything built on Electron —
  Typora, VS Code, Obsidian, Discord — refuses to start as root, and `proot-distro login` makes
  you root. Add `--no-sandbox` to the command, or log in as an ordinary user of the container
  with `-u yourname` if it has one.
- **`Failed to connect to the bus: /run/dbus/system_bus_socket`**, over and over. Harmless. A
  proot has no system D-Bus; apps that print this still open. If an app genuinely needs a session
  bus, start it with `dbus-run-session -- <command>` inside the container.
- **Nothing happens, or the app exits at once.** Run the same `proot-distro login …` line by hand
  in a Termux shell and read what it prints. Most of the time it is the app's own complaint —
  a missing font, a missing library — not the display.
- **"unable to open display".** Either no display is running (`termux-x11 :0 &`), or
  `--shared-x11` is missing from the command.
- **The app is not in the drawer.** Check `Type=Application`, a `Name`, an `Exec`, and that the
  file does not say `NoDisplay=true`, `Hidden=true` or `Terminal=true` — the launcher skips all
  three, as any desktop menu does. The file must end in `.desktop`.
- **The icon is a blank tile.** The path is wrong, or the file is not a PNG.

## Removing them

They are ordinary files:

```sh
rm $PREFIX/local/share/applications/debian-*.desktop
```

Nothing else is written, and nothing inside the container is touched.
