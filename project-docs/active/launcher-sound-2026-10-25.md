# Launcher-run sound and media controls (2026-10-25)

Status: agreed with the developer in a grilling session on 2026-10-25; building on `v2-redesign`
for the next pre-release, as one change.

## Why

A user on a moto g15 (Android 15, MediaTek mt6768) reports that PulseAudio "never works, and when
it works it is 5 minutes": closing kew, a terminal or xfce, or turning the phone off silences
everything — in the terminal, in kew and in Firefox. Reproduced on pong (stock pulseaudio 17.0):

- A server started with `pulseaudio --start` exits ~20 s after the last client stops
  (`exit-idle-time` default). Clients that autospawn (pactl) bring a stock one back; Chromium,
  Firefox and kew do not, and proot clients cannot.
- A server lives only as long as whatever started it; xfce starts its own; several servers can
  run at once and each app talks to whichever it finds.
- Android 15's phantom process killer and OEM battery management kill background children.
- Termux's default output is OpenSL ES (deprecated since Android 11); AAudio is the modern path.

Stable and the current pre-release manage no audio at all. `v2-redesign` (9e5e7c701) starts a
server in its own runtime dir (`X11LinuxAppRunner.PULSE_RUNTIME_DIR`) only for Linux apps opened
from the drawer, and forwards TCP to proot apps anonymously. This work replaces that with one
launcher-owned server for everything, and adds Android media integration on top.

## Decisions

### Sound server

1. **One server, started on first need.** When `pulseaudio` is installed and the Sound switch is
   on, the launcher starts the server the first time something may want sound: the first terminal
   session or the first Linux app. It is started by the launcher (not inside a terminal session),
   with `exit-idle-time = -1`, so closing a terminal, kew or a desktop never ends it.
2. **Output: AAudio, OpenSL fallback.** The launcher's own startup file (not Termux's
   `$PREFIX/etc/pulse/default.pa`, which is left untouched) loads `module-aaudio-sink`; only if
   that module fails to load does it load `module-sles-sink`. No setting.
3. **Restart at once, capped.** The launcher waits on the server process (no polling), and on
   exit restarts it immediately. After 3 exits within 60 s it stops retrying and shows a one-line
   notice on the Linux apps page; the next need tries again.
4. **Everyone finds the same server.**
   - Every terminal session and Linux app gets `PULSE_SERVER`.
   - Termux-side clients started outside a session (ssh, Termux:Widget, run-command, services) find
     it through a launcher-written `default-server` drop-in that libpulse reads.
   - proot distros get `/etc/pulse/client.conf.d/termux-launcher.conf`: `default-server =
     tcp:127.0.0.1:4713` plus the cookie. Written (or refreshed when stale) the next time the
     launcher starts anything in the distro; marked as the launcher's; never edits user files.
5. **TCP uses cookie auth.** `module-native-protocol-tcp` on 127.0.0.1:4713 without
   `auth-anonymous`; the cookie is copied into each distro's drop-in. Other Android apps share the
   loopback address and must not be able to use the server. (The default setup loads no input, so
   nothing can record either way.)
6. **ALSA bridge in distros.** The Set up Linux apps guide installs the ALSA→PulseAudio plugin in
   the distro and writes an `asound.conf` pointing the default device at PulseAudio.
7. **Step aside for a user's own setup.** If `~/.config/pulse` holds files the launcher did not
   write, or a server the user configured is already answering, the launcher starts nothing,
   writes nothing, and the Linux apps page says it is using the user's own setup.
8. **Replace a stock server.** A server some client autospawned, with no user configuration, is
   not a user's setup: it is stopped and the launcher's started in its place.

### Media

9. **One launcher session bus.** Started on first need like the server; its address goes to every
   terminal session, Linux app and proot distro (through the shared temp folder). The display
   keyboard keeps its own per-display bus (`x11-im-focus.sh`), untouched.
10. **Bridge as a shell helper.** Same pattern as `x11-im-focus.sh`: `dbus-monitor` watches MPRIS
    players (`org.mpris.MediaPlayer2.*`) appear, change and leave — event-driven, no polling — and
    reports to `launcherctl`; controls go back with `dbus-send`. No new Java dependency.
11. **Android media session.** The launcher mirrors the chosen player into a media session:
    the system media notification, lock-screen and Bluetooth controls.
    - Shows the player that most recently changed to Playing; when it stops, falls back to another
      that is playing or paused.
    - Controls: play/pause, previous, next — each only when the player reports it can — and a seek
      bar when it can seek. Position from MPRIS state plus Android's extrapolation and `Seeked`.
    - Artwork: local files only (`file://`, and proot paths mapped into the distro's rootfs);
      decoded straight to notification size into a small bounded cache. http art is skipped.
    - The launcher's own media widgets (status pane, home Media widget) show it like any session.
12. **Audio focus.** Held while any stream plays through the server, MPRIS or not (watched with
    `pactl subscribe`, event-driven).
    - Lasting loss: pause the MPRIS player.
    - Short loss: pause, and resume afterwards only what the launcher paused.
    - Duck allowed: lower the server's volume and restore it after.
    - Apps without MPRIS cannot be paused: they are ducked or muted instead.

### Settings

13. **Two switches** on the Linux apps page, both on by default:
    - **Sound** — the launcher-run server. Off: stop the server and bus at once, remove the
      launcher's drop-ins, stop handing addresses to new sessions.
    - **Media controls** — notification and audio focus; greyed out while Sound is off.
    The page also shows the step-aside state ("using your own setup") and the restart-cap notice.

## Out of scope

- Microphone input through the server.
- http artwork.
- Folding the display keyboard's bus into the launcher bus.

## Device checks (pong)

Listed in `project-docs/device-checks.md` when the work lands.
