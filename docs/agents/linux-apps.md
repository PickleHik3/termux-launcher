# Linux apps: what the code does not tell you

Read this before proposing or briefing work on Linux GUI apps, either display, sound for Linux
programs, or proot-distro containers. It holds only what was measured or decided and cannot be
found by reading one file. The design is in
[`project-docs/active/linux-windows-wayland-2026-11-24.md`](../../project-docs/active/linux-windows-wayland-2026-11-24.md).

## Where each part lives

| Part | Code |
|---|---|
| The catalogue of Linux apps, set-up, launching, X11 Display | `app/src/main/java/com/termux/app/x11/` (`LinuxAppCatalog`, `GuiAppsSetup`, `ProotDistro`, `X11LinuxAppRunner`) |
| Wayland Display: the screen, the runner, the socket, scale, clients' environment | `app/src/main/java/com/termux/app/wayland/` |
| The compositor | `wayland-host/` (Rust; local patches in `wayland-host/UPSTREAM.md`) |
| Sound for Linux programs | `app/src/main/java/com/termux/app/sound/` |
| Per-app settings (graphics, scale, hide) | `X11AppSettingsStore`, `X11AppPreferencesFragment` |

## Sound is already findable without variables

The launcher runs its own PulseAudio and writes a client drop-in
(`$PREFIX/etc/pulse/client.conf.d/termux-launcher.conf`) at every server start, so any shell
reaches it. Each distro gets a matching drop-in pointing at `127.0.0.1:4713` with a copy of the
cookie, which also works in an isolated login. Read `SoundFiles` before proposing sound work.
Termux's PulseAudio has no `module-x11-publish`; PipeWire is not used.

## proot-distro behaves differently from its help text

Measured on 5.10.3. Check the tool, not `--help`:

- A default login binds the whole Termux prefix at its own path, so a socket or file under
  `$PREFIX` has the same path inside a container.
- `/tmp` and `/tmp/.X11-unix` are the host's only with `--shared-tmp` or `--shared-x11`.
- A login starts from a clean environment: variables do not survive it. What a hand-opened shell
  needs comes from files the launcher writes into the container (`/etc/profile.d`, the pulse
  drop-in). fish does not read `profile.d`.
- Containers get no GPU variables and no prefix library path: the prefix's libraries are bionic.

## Package state: apt and pacman

Pong's Termux is pacman-based: there is no `$PREFIX/var/lib/dpkg/status`, and the same package
carries a different revision than under apt (`mesa 26.3.1-0` against `26.3.1-1`). Decide
"installed" from a binary or data path where possible; where a version is needed, read both
databases and compare the upstream release only (`MesaWaylandOverlay`).

## The display's address

- The socket is `$PREFIX/tmp/wayland-0`, owned by the launcher from process start while Wayland
  Display is chosen; the compositor starts on the first connection and its process exits after
  30 s with no window.
- `XDG_RUNTIME_DIR` defaults to the prefix's tmp folder in every shell (ADR 0036): terminal windows
  and ssh logins through `etc/profile.d/termux-launcher-runtime.sh`, fish through
  `etc/fish/conf.d/termux-launcher-runtime.fish`, and the scripts the launcher starts itself
  (`RuntimeFolderProfile`). It fills in only when empty; a value the user exports stays. Do not
  give any group of shells another value: that split Herdr (a second, empty core). A container's
  login shell gets a private folder of its own from the same-named file in its `etc/profile.d`,
  and a container desktop is started with that folder.
- `DISPLAY` exists only while an X server runs. The shared Xwayland takes the lowest free number.

## Things that start late

After the launcher restarts in the background, the sound server and the files written into
distros come up when the home screen is next shown. A check run over ssh before that sees
"connection refused" and missing files; show the home screen first.

## Checking on a device

Open a Linux app for a check with `launcherctl`, not through the drawer (see "The app drawer's
search" in [device-map.md](device-map.md)): `ssh termux launcherctl linux-apps` lists
`id<TAB>name<TAB>source[<TAB>desktop]`, and `ssh termux launcherctl linux-app open <id>` opens
one as a drawer tap does (display choice, second tap raising the window). It needs the home screen
shown since the launcher started. Install with `scripts/pong-install.sh`.
