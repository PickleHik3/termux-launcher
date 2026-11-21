# How people start Termux:X11, what upstream recommends, and whether several servers may run — research

**Date:** 2026-10-27 · **Branch:** `v2-redesign` · Nothing implemented; nothing here is a decision.

**Question.** How is Termux:X11 started in each setup (Termux-native, proot-distro, chroot, window
manager/desktop), what does the official documentation recommend, how is audio usually wired up, and
may several display servers run at once? A user reports: the window manager does not start, windows
open huge, and audio does not work in a proot distro.

This note does **not** repeat the proot-distro layout, `--shared-x11` and `.desktop` material in
[proot-gui-apps-research.md](proot-gui-apps-research.md) (§3 and §4 there); it links to it where
needed.

## Sources and how they were read

| Short name | What | Pinned at |
|---|---|---|
| **tx11** | <https://github.com/termux/termux-x11>, cloned and read locally | `29d4460b26e69b39af023d255a255e2778251952` |
| **pd** | <https://github.com/termux/proot-distro>, cloned and read locally | `737306f53fc86213db0ce0ac3e76aa3fda8dcb90` |
| **tpkg** | <https://github.com/termux/termux-packages> `packages/pulseaudio/` | `719f1abc2448d82170104a17fccb7760fb1959e5` (last commit touching `build.sh`) |
| community | `LinuxDroidMaster/Termux-Desktops` `6db0b2d`, `sabamdarif/termux-desktop` `59f6696`, `phoenixbyrd/Termux_XFCE` `828b7c5` | shallow clones, 2026-10-27 |

Permalink form: `https://github.com/termux/termux-x11/blob/29d4460b/<path>#L<n>`.

**Not reachable:** the Termux wiki (`wiki.termux.com/wiki/Graphical_Environment`, `/wiki/Audio`)
answers every automated fetch with an Anubis "Access Denied" page, so **nothing in this note is
quoted from the wiki**. The only wiki reference found in primary sources is a maintainer pointing at
it (tx11 issue [#715](https://github.com/termux/termux-x11/issues/715), Biswa96, 2024-08-30). The
termux-x11 README is therefore the official source used throughout. Anyone with a browser should
read the wiki's Graphical_Environment and Audio pages before treating this note as complete.

---

## Part A — Official guidance

### A.1 What starting the server means

- `termux-x11` is a thin shell script. It exports `CLASSPATH` pointing at `loader.apk`, saves and
  clears `LD_LIBRARY_PATH` / `LD_PRELOAD` (as `XSTARTUP_*`), then `exec`s
  `/system/bin/app_process … com.termux.x11.Loader "$@"`
  ([`shell-loader/scripts/termux-x11.in:16-22`](https://github.com/termux/termux-x11/blob/29d4460b/shell-loader/scripts/termux-x11.in#L16-L22)).
  The process is the X server; it does not return until the server stops.
- The server does **not** start a session by itself: "Termux:X11 does not start any apps by itself.
  It is responsibility of your environment." (twaik,
  [#1038](https://github.com/termux/termux-x11/issues/1038), 2026-06-13); earlier,
  "termux-x11 does not start any session so it can not export variables"
  ([#138](https://github.com/termux/termux-x11/issues/138), 2022-07-28).
- Installing: Android APK from the `nightly` release plus `pkg i x11-repo && pkg i termux-x11-nightly`
  ([README:23-24](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L23-L24)).

### A.2 The three documented ways to run a session (README "Running Graphical Applications")

[README:46-74](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L46-L74):

```
termux-x11 :1 -xstartup "dbus-launch --exit-with-session xfce4-session"
termux-x11 :1 -- dbus-launch --exit-with-session xfce4-session
termux-x11 :1 &   then   env DISPLAY=:1 dbus-launch --exit-with-session xfce4-session
termux-x11 :1 -xstartup "xfce4-session"          # "dbus-launch does not work for some users"
export TERMUX_X11_XSTARTUP="xfce4-session"; termux-x11 :1
```

Note the README's own examples use `:1`, not `:0` (the display number is a free choice; nothing in
the source treats `:0` specially).

What `-xstartup` really does, from the source:

- `-xstartup "cmd"` and `-- cmd args…` are mutually exclusive
  ([`InitOutput.c:339-358`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/InitOutput.c#L339-L358));
  `-xstartup ""` overrides `$TERMUX_X11_XSTARTUP` with nothing
  ([`InitOutput.c:299-306`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/InitOutput.c#L299-L306)).
- When the server is ready it forks; the child sets `DISPLAY=:<n>`, restores the saved
  `LD_*`/`CLASSPATH`, then `execlp(xstartup)` and falls back to `sh -c xstartup`
  ([`InitOutput.c:223-247`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/InitOutput.c#L223-L247)).
- **The server then `waitpid`s that child and, when it exits for any reason, calls
  `GiveUp(SIGINT)` — the whole X server stops**
  ([`InitOutput.c:248-267`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/InitOutput.c#L248-L267)).
  So the `-xstartup` process is the session leader: it must stay alive for as long as the display
  should. README's `--exit-with-session` pairs with this: dbus ends when the session ends, which
  ends the server.
- The README does not list `-dpi` in the option help block that was read, but `-dpi N` is shown in
  its DPI section: `termux-x11 :1 -xstartup "xfce4-session" -dpi 120`
  ([README:230-233](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L230-L233)).
- `-legacy-drawing` (black screen with cursor) and `-force-bgra` (swapped colours) are documented as
  device workarounds ([README:79-87](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L79-L87)).

### A.3 Display numbers and the socket

- The socket is `$TMPDIR/.X11-unix/X<n>`: Termux:X11 patches Xtrans so `UNIX_PATH` is a runtime
  variable (`app/…/patches/Xtrans.patch` → `lorie/src/main/cpp/patches/Xtrans.patch:1-30`), set in
  [`cmdentrypoint.cpp:151-152`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/cmdentrypoint.cpp#L151-L152)
  as `"%s/.X11-unix/X"` with `tmp = getenv("TMPDIR")`. The lock file directory is the same:
  `#define LOCK_DIR (getenv("TMPDIR") ?: "/tmp")`
  ([`patches/xserver.patch:196-197`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/patches/xserver.patch#L196-L197)).
- If `TMPDIR` is `/data/local/tmp` (adb) it is dropped; if unset, `/tmp` is used when it exists,
  else the hard-coded `/data/data/com.termux/files/usr/tmp`; otherwise the server refuses to start:
  "$TMPDIR is not set. Normally it is pointing to /tmp of a container."
  ([`cmdentrypoint.cpp:127-142`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/cmdentrypoint.cpp#L127-L142)).
  *Relevant for an embedded fork: the `com.termux` path is hard-coded here.*
- **Abstract sockets are re-enabled** upstream (commit
  [`072b6bf`](https://github.com/termux/termux-x11/commit/072b6bf), 2025-04-10, "reenable abstract
  sockets and prevent socket leaking to xstartup session"; it deletes the `#undef
  HAVE_ABSTRACT_SOCKETS` hunk from `Xtrans.patch` and adds `TRANS_CLOSEONEXEC` to the listen
  sockets). *Reasoned, not tested:* an abstract `@…/.X11-unix/X<n>` socket lives in the network
  namespace, which proot shares, so a libxcb client in a container that tries the abstract name
  first may connect even **without** `--shared-tmp`. This is not documented anywhere; the README
  still says `--shared-tmp` is required (A.4). Treat it as a hypothesis to test, not as a fallback
  to rely on.
- **On exit:** `ddxGiveUp` calls `CloseWellKnownConnections()` and `UnlockServer()` then `exit()`
  ([`InitOutput.c:216-221`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/InitOutput.c#L216-L221)),
  which is the normal Xorg path that unlinks the socket and lock. **On SIGTERM it is different:**
  the Android patch makes `OsSignal()` return `SIG_DFL` without installing a handler
  ([`patches/xserver.patch:186-188`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/patches/xserver.patch#L186-L188)),
  and trims the fatal-signal list. *Reasoned, not tested:* a SIGTERM/`pkill` therefore kills the
  process with the default action and does not run `ddxGiveUp`, so `X<n>` and `.X<n>-lock` can
  remain. The README's own stop recipe is `pkill -f termux-x11`
  ([README:138-141](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L138-L141)) and the
  maintainer's answer to "Xserver not exit completely" was to kill the `app_process`
  ([#398](https://github.com/termux/termux-x11/issues/398), twaik 2023-07-28). Xorg's own lock
  logic ("Server is already active for display N") tolerates stale locks whose PID is dead, so a
  stale socket is normally harmless; the case to test is a **stale socket after SIGKILL** with the
  same display number.
- Closing the Android activity does not stop the server: README says to exit via the notification,
  "But you should pay attention that `termux-x11` command is still running and can not be killed
  this way" ([README:76-77](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L76-L77)).

### A.4 proot-distro, chroot (official)

README "Using with proot environment"
([README:101-118](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L101-L118)):

- "you need to launch proot/proot-distro with the `--shared-tmp` option. If passing this option is
  not possible, set the `TMPDIR` environment variable to point to the directory that corresponds to
  `/tmp` in the target container."
- Recipe: in a Termux shell `termux-x11 :1 &` then `proot-distro login ubuntu --shared-tmp`; inside:
  `export DISPLAY=:1` and `dbus-launch --exit-with-session xfce4-session`.
- `--shared-x11` exists in proot-distro 5.x and binds only `.X11-unix`
  ([pd README:701](https://github.com/termux/proot-distro/blob/737306f5/README.md#L701)), but the
  termux-x11 README does not mention it. See the sibling note §3.2 and its device verification.
- The README says it is possible to start `termux-x11` **from inside** the container; it gives no
  command line for that.

README "Using with chroot environment"
([README:120-136](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L120-L136)): run as
root, `setenforce 0`, `export TMPDIR=/path/to/chroot/container/tmp`, set `XKB_CONFIG_ROOT` to the
container's `/usr/share/X11/xkb`, `export CLASSPATH=$(pm path com.termux.x11 | cut -d: -f2)`, then
`app_process / --nice-name=termux-x11 com.termux.x11.CmdEntryPoint :0`. That directory "must be in
the same SELinux context, same mount namespace".

### A.5 DPI and "everything is huge"

README "Font or scaling is too big!"
([README:223-230](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L223-L230)): "Some apps
may have issues with X server regarding DPI … You can fix this in your window manager settings (in
the case of xfce4 and lxqt via Applications Menu > Settings > Appearance). Look for the DPI value …
and adjust its value until the fonts are the appropriate size." and `-dpi 120` on the command line.
Upstream therefore treats DPI as per-desktop-environment configuration plus the server `-dpi`
flag; there is no mention of `xrdb`/`Xft.dpi` for a bare window manager. (Reasoned: a bare openbox
runs no settings daemon, so GTK/Qt clients in a container read `Xft.dpi`/the server DPI only.)

### A.6 Several servers at once — what upstream says

- **Maintainer position, 2024–2025: not supported.** "Is it possible to run multiple Termux X11
  Windows on Android (via cli probably) and set a custom DISPLAY for every client?" — twaik: "It is
  not…" ([#715](https://github.com/termux/termux-x11/issues/715), 2024-09-09); "Termux GUI can
  handle multiple instances somehow. Maybe TX11 can also somehow?" — twaik: "No." (2025-03-15).
  On multi-monitor: "The current design is strictly single-instance. It assumes one X server and one
  Activity that connect to each other through a fixed connection and reconnection mechanism … does
  not scale to multiple concurrent instances without a major redesign."
  ([#953](https://github.com/termux/termux-x11/issues/953), twaik 2025-12-12). In the same thread he
  sketched the idea that became `-tag`: an activity-alias answering to a "display tag".
- **That changed in 2026-08.** Commit
  [`50af52a`](https://github.com/termux/termux-x11/commit/50af52a) (twaik, 2026-08-27, "support
  additional X displays as separate Android windows") adds `-tag <tag>`; the README section
  ["Running multiple displays as separate windows"](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L89-L100)
  says: "Each display normally shares the single Termux:X11 window. Pass `-tag <tag>` to open a
  display as its own Android window instead", with
  `termux-x11 :1 -tag main …` and `termux-x11 :2 -tag second …`; re-running a tag "brings its window
  back to front"; windows are launched in freeform, drop from Recents once closed; a "Displays"
  button lists them. Stop one with `am broadcast -a com.termux.x11.ACTION_STOP -p com.termux.x11
  --es document_tag main` ([README:149-151](https://github.com/termux/termux-x11/blob/29d4460b/README.md#L149-L151)).
- Mechanically: untagged servers knock on TCP port 7892; tagged ones on `7900 + display number`
  ([`InitOutput.c:171`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/InitOutput.c#L171),
  [`lorie.h:18-19`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/lorie.h#L18-L19)).
  The knock socket sets `SO_REUSEADDR` and `SO_REUSEPORT`
  ([`cmdentrypoint.cpp:634-635`](https://github.com/termux/termux-x11/blob/29d4460b/lorie/src/main/cpp/lorie/cmdentrypoint.cpp#L634-L635)),
  so two **untagged** servers do not fail at bind, but only one Activity window exists for them; the
  second has nothing to show on (reasoned; not tested). X-level coexistence is ordinary Xorg:
  separate `X<n>` sockets and `.X<n>-lock` files per number.
- **Answer to "one server per app?"** Upstream does not recommend it. It documents one display
  (`:1`) for a session, and since 2026-08 offers `-tag` for secondary displays as *separate
  Android windows*, i.e. for multi-monitor/multi-window use, not as a per-app isolation scheme.
  Whether a given installed `termux-x11-nightly` package carries `-tag` depends on its build date
  (after 2026-08-27). The vendored `x11-server/` copy in this repo is pinned at `9df8b767` (per the
  sibling note §4), which **is** a descendant of `50af52a`, so `-tag` exists in our server (checked
  with `git merge-base --is-ancestor`).

### A.7 Audio — upstream's position

Termux:X11 has no audio. twaik, on a Debian proot with no sound ([#651](https://github.com/termux/termux-x11/issues/651), 2024-06-06):
"Not related to Termux:X11 … Pulseaudio inside proot-distro will not work. It simply does not have
the module required for android audio output … You should run pulseaudio **outside** of proot-distro
with SLES+TCP module and export `PULSE_SERVER` variable in proot-distro."

- The Termux `pulseaudio` package ships Android-only sink modules `module-sles-sink`,
  `module-sles-source` and `module-aaudio-sink`
  ([tpkg `meson.patch:29-44`](https://github.com/termux/termux-packages/blob/719f1abc/packages/pulseaudio/meson.patch#L29-L44);
  aaudio needs a later Android API, `build.sh:23-36`). Upstream termux-x11 never documents which to
  load; aaudio is a newer addition than the SLES advice above.
- **proot-distro sets `PULSE_SERVER=127.0.0.1` itself** on Termux in default mode
  ([pd `commands/login/__init__.py:255`](https://github.com/termux/proot-distro/blob/737306f5/proot_distro/commands/login/__init__.py#L255);
  [pd README:761](https://github.com/termux/proot-distro/blob/737306f5/README.md#L761)); the image's
  own `Env` and the host's `PULSE_SERVER` cannot override it (README:762-766), but a `--env` flag
  can (README:770). A bare host `127.0.0.1` means TCP port 4713, the PulseAudio default.

---

## Part B — Community practice (not official)

Evidence of what people actually do; none of this is endorsed by the termux-x11 maintainers.

| Setup | Practice | Source |
|---|---|---|
| Termux-native | `termux-x11 :0 >/dev/null &`, `sleep`, `am start … MainActivity`, then `env DISPLAY=:0 dbus-launch --exit-with-session xfce4-session &`. **Server first, session afterwards** (README's third form), not `-xstartup`. | [phoenixbyrd `install_xfce_native.sh:671-706`](https://github.com/phoenixbyrd/Termux_XFCE/blob/828b7c5/install_xfce_native.sh#L671-L706) |
| Termux-native | Same, generated: `x11_cmd="… termux-x11 :0"` then `env DISPLAY=:${display_number} … dbus-launch --exit-with-session $de_startup &`; display number is a variable but defaults to `0`. | sabamdarif `setup-termux-desktop:3938, :3985` (`59f6696`) |
| proot-distro | `termux-x11 :0 &`, then `proot-distro login <distro> --shared-tmp -- /bin/bash -c 'export PULSE_SERVER=127.0.0.1 && export XDG_RUNTIME_DIR=${TMPDIR} && su - <user> -c "env DISPLAY=:0 startxfce4"'`. | LinuxDroidMaster `scripts/proot_arch/startxfce4_arch.sh:7-25` |
| proot-distro | Every command prefixed `pd login debian --user U --shared-tmp -- env DISPLAY=:0 …` (including `apt`), plus `echo "export DISPLAY=:0" >> …/.bashrc`. | phoenixbyrd `install_xfce_native.sh:746-828` |
| proot-distro | `--shared-tmp` is universal; **nobody uses `--shared-x11`** in these three repos (grep over the clones: zero hits). `XDG_RUNTIME_DIR=${TMPDIR}` is exported in the container so PulseAudio/dbus find a runtime dir. | sabamdarif `distro-container-setup:612, :1869`; LinuxDroidMaster as above |
| proot-distro | `PULSE_SERVER=127.0.0.1` appended to the distro's `/etc/profile` (redundant with proot-distro 5.x's own baseline). | sabamdarif `distro-container-setup:416-418` |
| chroot/root | `XDG_RUNTIME_DIR=${TMPDIR} termux-x11 :0 -ac &`, then `busybox chroot $mnt /bin/su - user -c "export DISPLAY=:0 PULSE_SERVER=tcp:127.0.0.1:4713 && dbus-launch --exit-with-session startxfce4"`. `-ac` (disable access control) is a community habit, not in upstream's README. | LinuxDroidMaster `scripts/chroot/arch/startxfce4_chrootArch.sh:9-13`, `Documentation/chroot/arch_chroot.md:194` |
| Native app, one display per app | `exec bash -c "termux-x11 :$$ -xstartup \"…app…\" ; pkill -9 pulseaudio"`: **display number = shell PID**, one server per app, pulseaudio started only if `pulseaudio --check` fails ("In case we start multiple x11 instances; gedit for example"). The only multi-server pattern found; it kills PulseAudio when its app exits, which breaks siblings. | [LinuxDroidMaster `scripts/x11-app.sh:4-20`](https://github.com/LinuxDroidMaster/Termux-Desktops/blob/6db0b2d/scripts/x11-app.sh#L4-L20) |
| Audio (all three) | `pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1`, sometimes followed by `pacmd load-module module-native-protocol-tcp …` as a second attempt. **`auth-anonymous=1` + `auth-ip-acl=127.0.0.1` is the universal incantation.** They rely on Termux's stock `default.pa` for the sink; none of the clones names `module-sles-sink` or `module-aaudio-sink` explicitly (grep: zero hits). | e.g. [LinuxDroidMaster `startxfce4_arch.sh:7`](https://github.com/LinuxDroidMaster/Termux-Desktops/blob/6db0b2d/scripts/proot_arch/startxfce4_arch.sh#L7); phoenixbyrd `install_xfce_native.sh:661-663` |
| Audio, native | `export PULSE_SERVER=127.0.0.1` before starting the session. | phoenixbyrd `install_xfce_native.sh:667` |
| Audio, stale state | sabamdarif deletes `~/.config/pulse/*-runtime/` and `$TMPDIR/pulse/` before `pulseaudio --start`, then polls `pulseaudio --check` up to five times because `--start` forks. | sabamdarif `setup-termux-desktop:3880-3900` |

Observations from the table: **session after server, not `-xstartup`, is by far the commonest form**;
`:0` and a single server are universal; `--shared-tmp` plus `-e DISPLAY` (via `env`) are universal for
proot.

---

## Part C — Each case, in one place

| Case | Official | Community |
|---|---|---|
| Termux-native x11-repo app | `termux-x11 :N &` then `DISPLAY=:N app`, or `-xstartup`/`--` with the command (A.2) | server in background, then `env DISPLAY=:0 dbus-launch --exit-with-session …` |
| proot-distro **with** `--shared-tmp` | the README recipe (A.4) | universal |
| proot-distro **without** it | set `TMPDIR` to the container's `/tmp`, i.e. start the *server* with that `TMPDIR` (A.4); `--shared-x11` is the narrower proot-distro 5.x flag (pd README:701) | not used |
| chroot / root | `TMPDIR` into the chroot, `XKB_CONFIG_ROOT`, SELinux permissive, `app_process … CmdEntryPoint` (A.4) | `termux-x11 :0 -ac` under root, then `busybox chroot … su -c "export DISPLAY=:0 …"` |
| Window manager / desktop | `-xstartup "dbus-launch --exit-with-session xfce4-session"` or `-xstartup "xfce4-session"` if dbus-launch misbehaves (A.2) | run `startxfce4`/`xfce4-session` as a separate command after the server |
| Audio | PulseAudio runs **outside** the container with SLES + TCP; `PULSE_SERVER` in the container (A.7) | the `module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1` line |
| Several servers | one display for a session; `-tag` for extra Android windows since 2026-08 (A.6) | `termux-x11 :$$` per app (x11-app.sh) |

---

## What this means for us

Our line is `termux-x11 :0 -dpi N -xstartup $PREFIX/bin/termux-x11-wm`, with the wrapper starting
openbox "beside itself". Against the sources above:

1. **The wrapper must outlive the display.** `-xstartup` is the session leader; when it exits the
   server calls `GiveUp(SIGINT)` (A.2). A wrapper that backgrounds openbox and returns takes the
   whole display down. A wrapper that `exec`s openbox, or waits on it, is correct. "Window manager
   does not start" and "display vanishes right after start" are the same symptom here — worth
   reading the wrapper before anything else. (I did not read the wrapper; this is what the upstream
   code requires of it.)
2. **Windows opening huge is a DPI/scaling matter, not a start-up bug.** Upstream's answer is
   `-dpi` plus DE-level DPI settings (A.5). A bare openbox sets neither. Container GTK/Qt apps
   typically read `Xft.dpi` (from `xrdb`) or XSETTINGS rather than the X server's reported DPI —
   *reasoned, not measured*. Candidates to test: `xrdb -merge <<< "Xft.dpi: N"` from the wrapper
   before openbox, and `GDK_SCALE`/`QT_SCALE_FACTOR` via `-e` for proot launches (see the sibling
   note's §5 for how env reaches the guest). Also confirm that `-dpi N` actually reaches the server
   (our command line is assembled elsewhere).
3. **Audio in proot is most probably an auth or address mismatch, not a missing module.** The
   container gets `PULSE_SERVER=127.0.0.1` for free (pd `__init__.py:255`), i.e. TCP 4713 — exactly
   what we bind. Differences from community practice: they load
   `module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1`. If our TCP listener is
   loaded without `auth-anonymous=1` (or with an ACL that does not cover the address a proot
   process presents), the client is rejected even though the port is open. Second suspect: the user
   set `PULSE_SERVER` to our **unix socket** path, which is outside the container unless bound. A
   one-line check inside the container: `pactl info` (needs `pulseaudio-utils` in the guest). Also
   twaik's rule: never run PulseAudio inside proot (A.7).
4. **Do not rely on abstract sockets, but test `--shared-x11` against our own server.** Official
   guidance is `--shared-tmp` (README) or matching `TMPDIR`; the narrower `--shared-x11` was device-
   verified in the sibling note. The abstract-socket path (A.3) could mean a container with no
   shared tmp still connects; if true it would explain "some users work without any flag", and it
   would be fragile.
5. **Our embedded server inherits upstream's hard-coded `com.termux` fallback and SIGTERM
   behaviour** (A.3). Stop the display with the app's own path where possible, remove stale
   `$TMPDIR/.X11-unix/X0` and `.X0-lock` when starting if no server is alive, and expect
   `pkill`-style stops to leave them behind.
6. **Several servers: upstream does not suggest one per app.** Since `-tag` (in our pinned server)
   a second display can be a separate Android window, but the Activity-per-display model is the
   whole point of that feature; a launcher showing one Display place should keep one server and
   run every client on it. If per-app servers are ever wanted, community practice (`:$$`) shares
   one PulseAudio daemon and does not clean up after itself — do not copy its `pkill -9 pulseaudio`.

## What I could not verify

- The Termux wiki pages (blocked by Anubis for automated fetches); no wiki claim is made.
- The behaviour of our wrapper script, our PulseAudio module line, and how `-dpi` is built; I did
  not read the launcher source, per the brief.
- Every "reasoned, not tested" item: abstract-socket connection from proot, stale socket after
  SIGTERM/SIGKILL, two untagged servers, `Xft.dpi` as the cause of huge windows, and the
  `auth-anonymous` hypothesis for audio. None was run on a device.
- Whether the user's installed `termux-x11-nightly` package carries `-tag`.
- Line numbers are at the pinned commits above; later upstream commits will shift them.
