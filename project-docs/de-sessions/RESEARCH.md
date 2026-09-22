# Desktop-environment sessions in the drawer — research

Primary-source pass, 2026-09-22. Nothing here was measured on a device; everything is read from
the specification text, the published package contents, or the projects' own source. Where a
claim could not be traced to one of those it is in **Unverified** at the foot, not in the body.

Package evidence for Termux comes from the live x11 repository: the index
`https://packages.termux.dev/apt/termux-x11/dists/x11/Contents-aarch64.gz` for file lists, and
the debs themselves under `https://packages.termux.dev/apt/termux-x11/pool/main/…`, unpacked and
read. Every `.desktop` body quoted below is the literal file out of the deb that a phone would
install, not a reconstruction.

## What we found

### 1. `Type=XSession` is not in the spec, and most sessions do not use it

The Desktop Entry Specification defines three types and no more:

> This specification defines 3 types of desktop entries: `Application` (type 1), `Link` (type 2)
> and `Directory` (type 3). To allow the addition of new types in the future, implementations
> should ignore desktop entries with an unknown type.
> — <http://specifications.freedesktop.org/desktop-entry/latest/recognized-keys.html>

`XSession` appears nowhere in it. It is a display-manager convention, and an inconsistently
observed one. **The brief's premise is only half right**: of the nineteen session files in
Termux's x11 repository, seven use `Type=XSession` (i3, icewm's plain entry, plasmax11 and all
four e16 entries), one has no `Type` key at all (wmaker), and the other eleven — including xfce,
lxqt, mate and openbox — use `Type=Application`.

So the reason a whole desktop has no drawer entry today is **not** the `Type` value. It is the
**directory**: the launcher scans `share/applications`, and sessions live in `share/xsessions`
(§2). Today's filter would accept `xfce.desktop`, `lxqt.desktop`, `mate.desktop` and
`openbox.desktop` unchanged if it were ever handed them.

Keys a session entry actually carries, counted by opening all nineteen Termux files:

| Key | Present on |
|---|---|
| `Name` | 19 |
| `Exec` | 19 |
| `Type` | 18 — wmaker has none; 11 `Application`, 7 `XSession` |
| `Comment` | 18 (e16-gnome3 has none) |
| `TryExec` | 12 (xfce, bspwm, herbstluftwm, i3-with-shmlog and three of the four e16 entries have none) |
| `DesktopNames` | 9 |
| `Icon` | 10 — 4 of them `Icon=` with an empty value, 3 an absolute path, 3 a theme name (§7) |
| `X-LightDM-DesktopName` | 3 (i3, icewm, icewm-session) |
| `NoDisplay` / `Hidden` | 2 each, both `false` (the two icewm entries) |
| `StartupWMClass` | 0 |

`DesktopNames` is the session's contribution to `XDG_CURRENT_DESKTOP`; it is a real spec key
(`recognized-keys.html`), not a display-manager extension.

### 2. Where session files live

The Desktop Entry Spec places ordinary entries in `applications` and says nothing about sessions:

> If the desktop file is not installed in an `applications` subdirectory of one of the
> $XDG_DATA_DIRS components, it does not have an ID.
> — <http://specifications.freedesktop.org/desktop-entry/latest/file-naming.html>

`$XDG_DATA_DIRS` defaults to `/usr/local/share/:/usr/share/`
(<http://specifications.freedesktop.org/basedir/latest/>), which is where the `xsessions`
convention gets its two standard paths. The display managers own the convention:

- LightDM: `#sessions-directory=/usr/share/lightdm/sessions:/usr/share/xsessions:/usr/share/wayland-sessions`
  (<https://raw.githubusercontent.com/canonical/lightdm/main/data/lightdm.conf>).
- SDDM: `Entry(SessionDir, QStringList, {_S("/usr/local/share/xsessions"), _S("/usr/share/xsessions")}, …)`
  and the wayland twin (<https://raw.githubusercontent.com/sddm/sddm/develop/src/common/Configuration.h>).
- GDM builds `<dir>/xsessions` for every entry of `g_get_system_data_dirs()` — i.e. `XDG_DATA_DIRS`
  — plus three fixed GDM-private directories
  (<https://raw.githubusercontent.com/GNOME/gdm/main/libgdm/gdm-sessions.c>).

Concretely, for us:

- **Termux prefix**: `$PREFIX/share/xsessions`, i.e.
  `/data/data/com.termux/files/usr/share/xsessions`. Confirmed in the live index — nineteen
  `.desktop` files, listed in §3. There is also `$PREFIX/share/wayland-sessions` (labwc, lxqt-wayland,
  plasma, sway, weston, xfce-wayland).
- **proot container**: `/usr/share/xsessions` inside the rootfs. Confirmed against Debian trixie
  arm64 file lists for `xfce4-session`, `lxqt-session`, `mate-session-manager`, `openbox` and
  `i3-wm` (<https://packages.debian.org/trixie/arm64/xfce4-session/filelist> and siblings), and
  against Arch (<https://archlinux.org/packages/extra/x86_64/xfce4-session/files/>). Both distros
  ship exactly `xfce.desktop` in `xsessions` and `xfce-wayland.desktop` in `wayland-sessions`, the
  same split as Termux.
- `/usr/local/share/xsessions` is in SDDM's list and in `XDG_DATA_DIRS`, so a container scan should
  include it for symmetry with the existing `usr/local/share/applications` root.

### 3. Real package evidence — the literal session files

Termux x11 repository, aarch64, read out of the debs. `$P` below is
`/data/data/com.termux/files/usr`, written out in the files themselves.

**xfce4-session 4.20.4-1** → `$P/share/xsessions/xfce.desktop`:

```
Name=Xfce Session
Comment=Use this session to run Xfce as your desktop environment
Exec=startxfce4
Icon=
Type=Application
DesktopNames=XFCE
```

No `TryExec`. `startxfce4` is in the same package (`$P/bin/startxfce4`, confirmed in
`Contents-aarch64`). The metapackage `xfce4 4.20` pulls thunar, xfce4-panel, xfce4-session,
xfce4-settings, xfconf, xfwm4, xfce4-notifyd, xfce4-power-manager, xfce4-terminal
(`x11-packages/xfce4/build.sh`).

**lxqt-session 2.4.0-2** → `$P/share/xsessions/lxqt.desktop`:

```
Type=Application
Exec=startlxqt
TryExec=lxqt-session
DesktopNames=LXQt
Name=LXQt Desktop
Comment=Lightweight Qt Desktop
```

**mate-session-manager 1.28.0-2** → `$P/share/xsessions/mate.desktop`:

```
Name=MATE
Comment=This session logs you into MATE
Exec=mate-session
TryExec=mate-session
Icon=
Type=Application
DesktopNames=MATE
```

**openbox 3.6.1-62** → `$P/share/xsessions/openbox.desktop`:

```
Name=Openbox
Comment=Log in using the Openbox window manager (without a session manager)
Exec=/data/data/com.termux/files/usr/bin/openbox-session
TryExec=/data/data/com.termux/files/usr/bin/openbox-session
Icon=openbox
Type=Application
```

Absolute paths, because upstream's template is `Exec=@bindir@/openbox-session`
(<https://raw.githubusercontent.com/danakj/openbox/master/data/xsession/openbox.desktop.in>) and
Termux's `@bindir@` is the prefix. Termux ships its own `openbox-session`, which sources
`$P/etc/xdg/openbox/environment` and then `exec`s
`$P/bin/openbox --startup "$P/libexec/openbox-autostart OPENBOX"`
(`x11-packages/openbox/scripts/openbox-session` in the termux-packages checkout). The recipe
deletes `share/xsessions/openbox-gnome.desktop` and `openbox-kde.desktop`
(`TERMUX_PKG_RM_AFTER_INSTALL`), so `openbox.desktop` is the only one left.

**i3 4.25.1-1** → two files. `i3.desktop` is the one that uses the non-spec type:

```
Name=i3
Comment=improved dynamic tiling window manager
Exec=i3
TryExec=i3
Type=XSession
X-LightDM-DesktopName=i3
DesktopNames=i3
```

and `i3-with-shmlog.desktop` is `Exec=i3-with-shmlog`, `Type=Application`, no `TryExec`.

**icewm 4.1.0-1** → `icewm-session.desktop` (`Type=Application`, `Exec=$P/bin/icewm-session`,
`Icon=icewm`, `SessionManaged=true`) and `icewm.desktop` (`Type=XSession`, `Exec=$P/bin/icewm`,
`Icon=icewm`). Both carry a second group, `[Window Manager]`, which the launcher's parser already
ignores because it only reads `[Desktop Entry]` (`LinuxAppCatalog.java:236`).

**plasma-workspace 6.7.5** → `plasmax11.desktop`: `Type=XSession`,
`Exec=$P/bin/startplasma-x11`, `TryExec` the same, `DesktopNames=KDE`, no `Icon`.

**cinnamon 6.6.9** → `cinnamon.desktop` (`Exec=cinnamon-session-cinnamon`,
`TryExec=$P/bin/cinnamon`, `Icon=`, `Type=Application`) and `cinnamon2d.desktop`. Note the
mismatch: `TryExec` names `cinnamon` but `Exec` names `cinnamon-session-cinnamon`, and
`Contents-aarch64` lists neither `cinnamon-session-cinnamon` nor `cinnamon2d` under `bin` — only
`cinnamon-session`. This entry is a candidate for failing even though `TryExec` resolves.

Smaller managers, all `Type=Application` unless noted: `awesome` (`Exec=awesome`,
`TryExec=awesome`), `bspwm` (`Exec=bspwm`, no `TryExec`), `herbstluftwm` (`Exec=herbstluftwm`, no
`TryExec`), `wmaker` (`Exec=wmaker`, `TryExec=wmaker`, **no `Type` key**).

**e16 1.0.32** is the awkward one: four `Type=XSession` entries, three of them with an absolute
`Icon=/data/data/com.termux/files/usr/share/e16/icons/e16.png` and an `Exec` pointing into
`$P/share/e16/misc/` (`starte16`, `starte16-gnome`, `starte16-kde`). Two of the four start GNOME
or KDE sessions that Termux does not ship, and `e16-gnome2-session.desktop` and
`e16-kde-session.desktop` have no `TryExec` to screen them out; `e16-gnome3-session.desktop`'s
`TryExec=/usr/bin/gnome-session` is a Debian path that does not exist in the Termux prefix, so
that one at least would be filtered.

Nineteen files in all; six more live in `share/wayland-sessions` (labwc, lxqt-wayland, plasma,
sway, weston, xfce-wayland).

Debian trixie arm64 ships the same layout, verified by file list only: `xfce4-session` →
`/usr/share/xsessions/xfce.desktop` + `/usr/bin/startxfce4`; `lxqt-session` → `lxqt.desktop` +
`/usr/bin/startlxqt`; `mate-session-manager` → `mate.desktop` + `/usr/bin/mate-session`;
`openbox` → `openbox.desktop` + `/usr/bin/openbox-session`; `i3-wm` → `i3.desktop` and
`i3-with-shmlog.desktop` + `/usr/bin/i3`. The *contents* of the Debian files were not opened —
see **Unverified**.

### 4. What starting a DE on the display needs

The launcher's own project README for the X server is the closest thing to an owner's
instruction, and it is explicit
(<https://raw.githubusercontent.com/termux/termux-x11/master/README.md>):

```
termux-x11 :1 -xstartup "dbus-launch --exit-with-session xfce4-session"
termux-x11 :1 &
env DISPLAY=:1 dbus-launch --exit-with-session xfce4-session
```

and for a container:

```
termux-x11 :1 &
proot-distro login ubuntu --shared-tmp
# inside:
export DISPLAY=:1
dbus-launch --exit-with-session xfce4-session
```

The dbus wrapper is not folklore. Each session start command handles the bus differently, and
this was read out of the shipped artefacts:

- **`startxfce4` starts no bus on the X11 path.** The shipped script (extracted from the deb,
  and matching `https://gitlab.xfce.org/xfce/xfce4-session/-/raw/master/scripts/startxfce4.in`)
  adds `dbus-run-session` only inside the `--wayland` branch. On X11 it sees `DISPLAY` already
  set, prints `X server already running on display $DISPLAY`, and `exec`s
  `$P/etc/xdg/xfce4/xinitrc`, which ends in `exec ${XFCE4_SESSION_COMPOSITOR:-xfce4-session}`.
  That is why the README wraps it by hand. Without a bus, xfce4-session prints
  *"Unable to determine failsafe session name. Possible causes: xfconfd isn't running (D-Bus
  setup problem)…"* (string in the shipped `xfce4-session` binary).
- **`startlxqt` starts its own bus.** The shipped script: `if [ -z "$DBUS_SESSION_BUS_ADDRESS" ]`
  and no usable `$XDG_RUNTIME_DIR/bus`, then
  `eval "$(dbus-launch --sh-syntax --exit-with-session)"`. It also sets `XDG_DATA_HOME`,
  `XDG_CONFIG_HOME`, `XDG_DATA_DIRS`, `XDG_CONFIG_DIRS`, `XDG_CACHE_HOME` and creates `~/Desktop`.
- **`mate-session` starts its own bus.** Its binary contains `dbus-launch`,
  `DBUS_SESSION_BUS_ADDRESS`, `No session bus and could not exec dbus-launch: %s` and a guard
  `!g_str_has_prefix(argv[0], "dbus-launch")` — it re-execs itself under `dbus-launch` when there
  is no bus.
- **`openbox-session` and `i3` need no bus at all.** Neither script nor binary mentions one.

`dbus-launch`, `dbus-run-session` and `dbus-daemon` are all in Termux's **main** repository
(package `dbus`, confirmed in `termux-main`'s `Contents-aarch64`), not in x11 — so a DE session
started from the drawer can only be wrapped if `dbus` is installed, and today nothing in the
launcher installs or checks for it (repo-wide grep: no `dbus` outside two lines of
`docs/en/X11_Display.md` and one line of `docs/en/Linux_Apps_From_A_Distro.md:118`).

Environment, as things stand:

- `DISPLAY` — set by the launcher already (`X11LinuxAppRunner.java:273`), and crossed into a
  container by `ProotDistro.FORWARDED_ENV` (`ProotDistro.java:43-44`) via `-e`.
- `XDG_RUNTIME_DIR` — **not set anywhere**. Nothing in Termux's `profile.d` sets it (checked the
  whole `etc/profile.d/` list in `termux-main`'s `Contents-aarch64`), proot-distro does not set it
  (`project-docs/proot-gui-apps-research.md:345`), and the launcher sets it only inside the GPU
  probe script (`app/src/main/assets/x11/x11-gpu-setup.sh:338`, as `XDG_RUNTIME_DIR=/tmp`).
- `DBUS_SESSION_BUS_ADDRESS` — not set; see above.
- `XDG_SESSION_TYPE` / `XDG_CURRENT_DESKTOP` — not set by the launcher. `startxfce4`'s `xinitrc`
  defaults `XDG_CURRENT_DESKTOP=XFCE` itself when empty; `startlxqt` does the equivalent. The
  spec's own carrier for this is the session file's `DesktopNames` key, which eleven of the
  nineteen entries have.
- `XDG_DATA_DIRS` / `XDG_CONFIG_DIRS` — `startxfce4` and `startlxqt` both repair these themselves
  for the Termux prefix. A DE started *inside* a container does not need the repair: `/usr/share`
  is already a default.

Container-specific, all already handled by `ProotDistro.loginCommand`
(`ProotDistro.java:393-408`): `--shared-x11` is required, `DISPLAY` must be passed with `-e`, no
`-ac` and no `xauth` (`project-docs/distro-apps/SPEC.md:29-32`). The login runs as the container's
first non-root user (D3) when there is one.

### 5. Conflict with the window manager the launcher already runs

The launcher starts a window manager with the display — `openbox` by default
(`TermuxPreferenceConstants.java:255`), with the launcher's own `--config-file`
(`X11WindowManager.java:49-51`), handed to the server as `-xstartup`
(`TermuxActivity.java:15659`, `X11Autostart.java:39`). The setting is no longer a row; the Display
page just states it (`x11_display_preferences.xml:137-142`, `strings.xml:334`).

Only one window manager can hold a screen. The X protocol is the owner of that claim:

> Multiple clients can select input on the same window; their event-masks are disjoint. When an
> event is generated, it will be reported to all interested clients. However, only one client at a
> time can select for SubstructureRedirect, only one client at a time can select for
> ResizeRedirect, and only one client at a time can select for ButtonPress. An attempt to violate
> these restrictions results in an Access error.
> — ChangeWindowAttributes, <https://xorg.freedesktop.org/archive/X11R7.7/doc/xproto/x11protocol.html>

ICCCM adds the handshake on top of it — the `WM_Sn` manager selection:

> Before a manager takes ownership of a manager selection, it should use the GetSelectionOwner
> request to check whether the selection is already owned by another client, and, where
> appropriate, it should ask the user if the new manager should replace the old one.
> […] a window manager losing ownership of WM_S2 must deselect from SubstructureRedirect on the
> root window of screen 2 before destroying the window that owned WM_S2.
> — <https://xorg.freedesktop.org/archive/X11R7.7/doc/xorg-docs/icccm/icccm.html>

What each of ours actually does:

- **openbox** checks `WM_S<n>`; if owned and `--replace` was not given it prints *"A window
  manager is already running on screen %d"* and returns false. Even with the selection acquired
  it wraps `XSelectInput(root, ROOT_EVENTMASK)` — which includes `SubstructureRedirectMask` — in
  an error trap and prints the same message on `BadAccess`
  (<https://raw.githubusercontent.com/danakj/openbox/master/openbox/screen.c>, lines 89-112 and
  181-192). So both layers of the protocol are honoured, and both are fatal to the newcomer.
- **xfwm4** does the same and names the incumbent: *"Another Window Manager (%s) is already
  running on screen %s"* followed by *"To replace the current window manager, try \"--replace\""*
  (<https://gitlab.xfce.org/xfce/xfwm4/-/raw/master/src/screen.c>, `myScreenSetWMAtom`, lines
  92-114). `--replace` is a documented flag of xfwm4's own
  (`src/main.c:627`: `{ "replace", 'r', 0, …, N_("Replace the existing window manager") }`).
- **xfce4-session starts xfwm4 unconditionally.** Its shipped failsafe session is
  `Client0_Command=xfwm4`, then `xfsettingsd`, `xfce4-panel`, `Thunar --daemon`, `xfdesktop`
  (`$P/etc/xdg/xfce4/xfconf/xfce-perchannel-xml/xfce4-session.xml`, out of the deb). So with the
  launcher's openbox in place, xfwm4 prints its message and exits, and **the rest of XFCE still
  starts**: panel, settings daemon, desktop, file manager — managed by openbox, with openbox's
  decorations and the launcher's maximise-everything rule (`X11CliInstaller.java:518-540`).
- **LXQt stands down politely.** `lxqt-session` checks the running WM before starting one:
  *"if the WM is active do not run WM. all window managers must set their name according to the
  spec"* — it reads `NETRootInfo(…, NET::SupportingWMCheck).wmName()` and simply returns if it is
  non-empty
  (<https://raw.githubusercontent.com/lxqt/lxqt-session/master/lxqt-session/src/lxqtmodman.cpp>,
  ~line 195). Only when no WM is present does it start `window_manager` from its settings, or pop
  a `WmSelectDialog`. LXQt on top of the launcher's openbox is therefore the *intended* path, not
  a workaround.
- **MATE, i3, plasma** were not traced to source on this point; see **Unverified**.

### 6. One session at a time

Two sessions of the same DE on one display is refused at the session-manager level, not silently
tolerated:

- `xfce4-session` contains the string **"Another session manager is already running"**, and owns
  the D-Bus name `org.xfce.SessionManager` with the object path `/org/xfce/SessionManager`
  (strings in the shipped binary). A second instance on the same bus cannot take the name.
- `lxqt-session` owns `org.lxqt.session` (string in the shipped binary).

Two *different* DEs on one display is a different failure: each session manager comes up (they
use different bus names), but their panels, settings daemons and desktops all take the same
screen, and only the first WM holds `WM_S0`. Nothing refuses it; the result is a mess. The
practical rule is one session at a time, enforced by us, because no component enforces it for us.

Worth noting for the runner: `X11LinuxAppRunner` has no concept of a long-lived process. Its
quick-fail watch (`X11LinuxAppRunner.java:42`, `QUICK_FAIL_MS = 3000`) treats a non-zero exit
inside three seconds as the Electron sandbox crash and **retries with `--no-sandbox`**
(`:229-235`). A session command that refuses to start because a WM or a session manager is already
there exits non-zero, quickly — so today a DE session tapped twice would be retried with a flag it
has never heard of. That is a real bug waiting, not a theoretical one.

### 7. Icons and naming

Ten of the nineteen Termux session files carry an `Icon` key, and four of those ten are `Icon=`
with an empty value (xfce, mate, cinnamon, cinnamon2d) — a key that is present and says nothing.
Three name a theme icon (`openbox.desktop` → `openbox`, both icewm entries → `icewm`) — but only
openbox's is backed by a file. **Corrected while building P3 (2026-09-22):** icewm ships its
pictures in `share/icewm/icons/`, a private directory of its own, not a freedesktop theme path, so
`Icon=icewm` resolves nowhere and IceWM's tile is generic. Two real theme icons, not three.

Three give an absolute path into the prefix (the e16 entries → `$P/share/e16/icons/e16.png`,
which the existing rootfs-absolute icon handling already covers,
`project-docs/distro-apps/SPEC.md:34`). lxqt, i3 (both), plasmax11, awesome, bspwm, herbstluftwm
and wmaker have no `Icon` key at all — nine of the nineteen show nothing.

So the drawer cannot rely on the session file for a picture. `LinuxAppCatalog.parse` already
tolerates an empty `Icon` (it is not part of the null test at `LinuxAppCatalog.java:262`), and the
drawer already has to cope with a Linux app whose icon is missing. What a session tile shows is a
product decision, not a research finding — it is in **Open questions**.

Names are plain and mostly already good: "Xfce Session", "LXQt Desktop", "MATE", "Openbox", "i3",
"Plasma (X11)", "IceWM", "IceWM Session", "i3 (with debug log)". Two pairs collide in meaning if
not in string — icewm ships both a session and a bare WM entry, i3 ships a normal and a debug
entry — and D2 (`project-docs/distro-apps/SPEC.md:41`) says the tile is called what the app calls
itself, with the container shown elsewhere.

## What this means for the launcher

Six places, all small; the work is in the policy, not the code.

1. **`ProotDistro.Container.applicationDirs()` — `app/src/main/java/com/termux/app/x11/ProotDistro.java:123-144`.**
   The only hard-coded list of scan directories. Prefix branch (`:135-139`) would gain
   `share/xsessions` and `local/share/xsessions`; distro branch (`:140-143`) would gain
   `usr/share/xsessions` and `usr/local/share/xsessions`; nix branch (`:125-134`) would gain
   `share/xsessions` under the profile. The method's name and its one-line contract
   ("Where this one keeps its desktop files") stop being true — either it grows a parameter, or a
   sibling `sessionDirs()` appears beside it.

2. **`LinuxAppCatalog.Root` — `LinuxAppCatalog.java:29-37`.** A `Root` carries `dir` and
   `container` and nothing else. Whatever a session root is, the scan has to know a root is a
   session root, because §1 shows the `Type` value cannot be used to tell them apart: `xfce.desktop`
   and `firefox.desktop` are both `Type=Application`. The directory is the only reliable signal.
   `roots()` (`:134-142`) and `rootsOf()` (`:146-150`) build them; `signature()` (`:196-214`)
   hashes them and would pick up session directories for free.

3. **`LinuxAppCatalog.parse` — `LinuxAppCatalog.java:226-267`.** Three points:
   - `:262` `if (!"Application".equals(type) …)` rejects the seven `Type=XSession` entries (i3,
     icewm's plain entry, plasmax11, all four e16) and wmaker (no `Type`). A session root needs
     `XSession` accepted, and a missing `Type` decided one way or the other.
   - `:264` the `TryExec` test is worth keeping: seven of the nineteen have no `TryExec`, so it
     screens some but not all, and `cinnamon.desktop`'s `TryExec` points at a binary the package
     does not ship under `bin` (§3) — a resolving `TryExec` is not proof the `Exec` will run.
   - `LinuxApp` (`:40-124`) would need a session flag the way it needed `terminal` (D5), since the
     runner has to treat one differently.

4. **`LinuxAppCatalog.scan` — `LinuxAppCatalog.java:166-189`.** Ids are
   `X11Apps.qualify(container, desktopFileName)` (`:97`), so a session file and an application
   file of the same basename in the same container collide and the first root silently wins
   (`:176` `if (seen.contains(id)) continue;`). Nothing collides in today's Termux x11 repository,
   but the id scheme has no room for "which directory" and should gain it before this ships.

5. **`X11LinuxAppRunner` — `app/src/main/java/com/termux/app/x11/X11LinuxAppRunner.java`.**
   - `script()` (`:270-280`) exports `DISPLAY`, `TOUCH_ENV` and the GPU profile, `cd "$HOME"`,
     then `exec`s the command. A session needs at least `XDG_RUNTIME_DIR` and — for xfce, which
     starts no bus of its own (§4) — a `dbus-launch`/`dbus-run-session` wrapper. lxqt and mate do
     not need one and must not get a second.
   - The quick-fail retry (`:42`, `:221-236`) must not fire for a session: a refused session exits
     non-zero within the window and would be retried with `--no-sandbox` (§6).
   - `ProotDistro.FORWARDED_ENV` (`ProotDistro.java:43-44`) is where any extra `-e` variable for a
     container session goes.

6. **The window manager the launcher starts — `X11WindowManager.java:37-53`, called from
   `TermuxActivity.java:15659`, `X11Autostart.java:39` and `X11CliInstaller.java:257`.**
   If a session is to bring its own WM, this is the one place that decides whether openbox runs at
   all, and `X11CliInstaller.openboxRcContent()` (`:518-540`) is the maximise-everything rule that
   would have to stand down with it. There is no "stop the window manager" path today — the
   command is baked into the server's `-xstartup` when the display starts.

Two more, if sessions get their own shelf: `AppDrawerCategory.java:24` (`LINUX_APPS`) and
`LauncherCategorySortPrompt.java:59` (its description) are where a "Desktops" category would go.
Documentation lands in `docs/en/X11_Display.md:242-247` (the window-manager paragraph, which
currently promises openbox arranges everything) and `:256-279` ("A desktop in a proot", which today
tells the user to type `pacman -S xfce4 && startxfce4` themselves).

## Open questions for the user

1. **Does openbox stand down for a session, or stay?** LXQt detects it and skips its own WM, so it
   works either way. XFCE's session starts xfwm4 unconditionally and xfwm4 will refuse and exit —
   XFCE then runs under openbox with the launcher's maximise-everything rule, which is not what an
   XFCE desktop is supposed to look like. Options: (a) leave openbox up always, simplest, XFCE
   looks wrong; (b) stop openbox before a session and let the session bring its own; (c) pass
   `--replace` on the session's behalf where the DE supports it.
2. **One session at a time — do we police it?** Nothing in X or in the DEs prevents two different
   desktops on one display, and the result is two panels fighting for the same screen. Should the
   launcher refuse to start a second session while one is running, and what does it do with the
   first — offer to stop it, or just say no?
3. **Do sessions get their own place in the drawer?** They are not apps: one tap takes over the
   whole display for as long as it runs. A separate "Desktops" category, a badge on the tile in
   Linux Apps, or nothing at all?
4. **Are Wayland sessions in scope?** `share/wayland-sessions` holds six entries in Termux
   (labwc, lxqt-wayland, plasma, sway, weston, xfce-wayland). None of them can run on the
   launcher's X11 display as a session — `startxfce4 --wayland` wants `labwc` and exits if it is
   missing. Cataloguing them would put tiles in the drawer that cannot work.
5. **What does a session tile show?** Only openbox and icewm name an icon; xfce, mate and cinnamon
   ship `Icon=` empty and the rest have no `Icon` key. A generic desktop glyph for all of them, the
   DE's own application icon borrowed from elsewhere, or a letter tile?
6. **Do we install `dbus` for the user?** XFCE needs a session bus and starts none itself; `dbus`
   is in Termux's main repository, not x11. Offer it in the GUI-apps setup flow, or let the session
   fail with a bus error and say why?
7. **Both entries, or one, where a package ships several?** icewm ships `icewm.desktop` and
   `icewm-session.desktop`; i3 ships `i3.desktop` and `i3-with-shmlog.desktop` (a debug build);
   e16 ships four, two of which start GNOME or KDE sessions Termux does not have and have no
   `TryExec` to screen them out. Show everything the directory holds, or curate?

## Unverified

- **The literal contents of the Debian and Arch session files.** Their *file lists* were confirmed
  (§2, §3); the `.desktop` bodies were not opened. They are built from the same upstream templates
  as Termux's, and the only expected difference is `@bindir@` expanding to `/usr/bin` instead of
  the Termux prefix — but that is inference, not evidence. Before any `Exec` for a container
  session is hard-coded, open the Debian file.
- **What MATE, i3 and Plasma do when a window manager is already running.** openbox, xfwm4 and
  lxqt-session were read in source; these three were not. MATE starts `marco` through `mate-wm`,
  and marco is a Metacity fork, so it most likely behaves like the others — unconfirmed.
- **Whether `xfce4-session` survives its WM refusing to start.** The failsafe session's other four
  clients are independent processes and there is no dependency declared between them in
  `xfce4-session.xml`, so XFCE minus xfwm4 should come up — read from the config, not observed.
- **Anything about actual behaviour on a device.** No session was started on pong or anywhere else
  for this pass. Every §4 and §5 claim is from source, script or binary strings.
- **Termux's own wiki page on graphical environments** (`wiki.termux.com/wiki/Graphical_Environment`)
  could not be read — the site is behind an Anubis challenge. The termux-x11 README was used
  instead; it is the same project's own documentation, but the wiki may say more about proot and
  about `XDG_RUNTIME_DIR`.
- **`cinnamon.desktop`'s `Exec`.** `cinnamon-session-cinnamon` does not appear under `bin` in the
  x11 repository's `Contents-aarch64`. Either it is installed elsewhere or the entry is broken in
  Termux; not chased down.
