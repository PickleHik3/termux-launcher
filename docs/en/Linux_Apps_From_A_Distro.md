# Linux apps from a distro

A Linux system installed inside Termux — Debian, Ubuntu, Arch — can hold ordinary desktop apps,
and the launcher puts them in the app drawer for you. Install one inside the distro and it is
there when you come back, with its own name and its own icon. There is no file to write and no
command to run.

Everything here was checked on a phone, against a Debian 13 container and `proot-distro` 5.8.0.

**On the nix edition** none of this applies: there is no `proot-distro` and no apt repository, and
graphical apps come from nixpkgs instead. Add the app and `xkeyboard-config` to your config, run
`nix-on-droid switch`, and the drawer picks them up — see
[Graphical apps](Nix_Package_Management.md#graphical-apps).

**On the VAJ edition** the screen looks the same, with two differences: its own repository carries
the graphical packages directly, so the Termux's own apps route needs no repository step and lists
no browser (none is built for it), and the window manager is `pkg install openbox`.

## Set it up in one go

**Settings → Display → Apps → Get GUI apps** builds the command that does this. Choose where the
apps come from — Termux's own, or a full Linux inside it — tick the ones you want, then tap
**Copy the command**: it goes on the clipboard, a notice tells you it is there, and Settings closes
so the terminal is in front. Paste it and press Enter.

For a full Linux, pick a distro first — Debian, Ubuntu or Arch Linux, Debian recommended if you
are not sure. The pasted command installs the distro if you have none, then asks you, in the
terminal, for a username and a password for your account inside it. The password is only ever
typed there; the launcher never stores it. The username is kept so the launcher knows which
account your apps belong to — it is what lets a tap on a Linux app open as you, not as whoever the
distro happens to pick. The same command adds the fonts a fresh distro does not ship, adds its
graphics support, and installs the apps you ticked. It finishes with a line telling you it is
done.

The Display place offers this same screen by itself when it finds a distro that is not ready.
Copy a command from it and it stops asking about that distro.

A first run downloads a few hundred megabytes and takes several minutes. If it stops partway,
paste the command again — nothing is lost and it carries on from where it stopped.

The Display place's offer is for distros that install their packages with `apt` — Debian, Ubuntu
and their relatives. An Arch or Alpine distro is left alone; its apps are still listed in the
drawer, you just install them yourself from the terminal.

## Install an app, and it is in the drawer

Anything with a window will do:

```sh
proot-distro login debian
apt install -y mousepad
exit
```

Go back to the launcher and open the app drawer. **Mousepad** is there under **Linux Apps**, with
its icon. The drawer looks the distro over again every time you return to the launcher, so an app
installed a minute ago is already waiting.

The tile is called what the app calls itself — *Typora*, not *Typora (Debian)*. Distro apps can be
pinned and searched like any other app.

## Opening one

A tap opens the app on the [Display](X11_Display.md), full size, and takes you to the Display
place. The display starts itself if it was not running. An app whose menu entry asks for a
terminal — a text editor, a file manager — opens in a terminal pane instead.

Some apps need a small allowance before they will run inside a distro. The launcher works that out
by itself the first time you open one, and remembers it; all you notice is a slightly slower first
start.

## Hiding the ones you do not want

A distro installs menu entries for helpers and viewers you may never open by hand.
**Settings → Display → Apps → GUI apps** lists the Linux apps it found; untick one and it
leaves the drawer. It is one app at a time, not a whole distro, and it holds across restarts. The
list holds Linux apps only — your Android apps are not in it.

There is also a **Linux apps in the drawer** switch on the same page, if you would rather have
none of them and start everything from a shell.

Only distros installed by `proot-distro` **5.x** are read. An older installation keeps its distros
in a different place and is not listed; upgrade `proot-distro` or reinstall the distro under it.

## Running one from a shell

If you start a distro yourself instead of tapping a tile, two things matter and only those two:

```sh
termux-x11 :0 &
proot-distro login debian --shared-x11 -e DISPLAY=:0 -- xeyes
```

- **`--shared-x11`** lets the distro reach the display. Without it every app says *unable to open
  display*.
- **`-e DISPLAY=:0`** passes the display in. Exporting it in your Termux shell does not carry it
  across; nothing of your shell's environment does.
- **Nothing else.** You do not need `-ac` on the server, and you do not need `xauth` in the
  distro. The display accepts the connection because both sides are the same Android app.

Sound needs no setup either: `proot-distro` already sets `PULSE_SERVER=127.0.0.1` for you.

Apps built on Electron — Typora, VS Code, Obsidian — need `--no-sandbox` on the end of their
command when you start them this way. From the drawer the launcher takes care of it.

## When something is off

- **No Linux apps in the drawer at all.** Either nothing is installed yet — use **Get GUI
  apps** — or the **Linux apps in the drawer** switch is off. Check too that the distro was
  installed by `proot-distro` 5.x.
- **One app is missing.** Look in **GUI apps** first. Otherwise the app has no menu entry
  of its own: command-line programs usually have none, and the launcher lists what the distro's own
  menu lists. Write the entry yourself, below.
- **An app opens and closes again, or nothing happens.** Most often a fresh distro missing fonts;
  **Get GUI apps** installs them. To see the app's own complaint, run it by hand:
  `proot-distro login debian --shared-x11 -e DISPLAY=:0 -- <command>`.
- **`Failed to connect to the bus`, over and over.** Harmless — apps that print it still open. One
  that genuinely needs a session bus wants `dbus-run-session -- <command>` inside the distro.
- **The display never comes up.** That is the display itself, not the app: see
  [when something is off](X11_Display.md#when-something-is-off) on the display page.
- **The tile is blank.** The app's icon is in a format the launcher cannot read. PNG and SVG icons
  are used; anything else gets the default tile.

## Writing an entry yourself

Only for an app the distro's menu does not know about — your own script, or a command you want a
tile for. Everything else is found on its own, and an entry written by hand for an app that is
already listed simply shows up twice.

The launcher reads these files from `$PREFIX/local/share/applications`:

```sh
mkdir -p $PREFIX/local/share/applications
cat > $PREFIX/local/share/applications/debian-xeyes.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Eyes
Exec=proot-distro login debian --shared-x11 -e DISPLAY=${DISPLAY:-:0} -- /bin/sh -c 'xeyes'
Terminal=false
EOF
```

Return to the launcher and **Eyes** is in the drawer. `Icon=` takes a full path to a PNG or SVG
file — `$PREFIX` is not expanded inside one of these files — and `StartupWMClass=`, if the app has
one, is what names its window on the display. Delete the file to remove the tile; nothing inside
the distro is touched.
