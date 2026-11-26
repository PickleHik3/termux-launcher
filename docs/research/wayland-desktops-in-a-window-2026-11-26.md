# Wayland desktops in a Linux window

Research note for running a Wayland desktop (Hyprland, niri, Plasma's KWin, sway, labwc) under
Wayland Display, started the way each project's own documentation says. Researched 2026-11-26.

Method: every upstream claim was read from the project's own source, man pages, wiki or package
recipe, cloned or fetched on 2026-11-26 at these commits: Aquamarine `ca1a485aa90b`, Hyprland
`e537b2fce29e`, hyprland-wiki `bd104c02d476`, niri `6072f67d9fe0` (version 26.4.0), KWin `master`
`f1e7d1d44305` (6.8.80) plus three files from the `v6.8.3` tag, wlroots tag `0.20.2`
(`d783533489e1`), sway `9bb430936ae6`, labwc `4d5f9cb843c8`, weston `4095e2b5da1a`, wayland
(libwayland) `f2ecaa61a3a8`, Mesa `bb7494e71c6b` (27.0.0-devel), termux-packages `9f05b9e9e064`,
and smithay 0.7.0 / wayland-backend 0.3.17 / wayland-server 0.31.14 from the local cargo registry
(the versions `wayland-host/Cargo.lock` pins). Arch Linux ARM versions come from its aarch64 package
pages and Debian versions from the sources.debian.org API, both read on 2026-11-26. Our code is
cited as `path:line` on `v2-redesign` at `85835667a`. No blog posts, forum answers or other
secondary sources were used. Nothing was built or run: no compositor, no device, no emulator.
Anything I infer is marked **[inference]**; anything I could not confirm from a primary source is
marked **[unverified]**.

Terms used once and then freely: a **nested** compositor is a Wayland compositor that runs as a
client of another compositor and treats the window it gets as its monitor. The **host** is the
compositor it runs inside, here Wayland Display's compositor (`wayland-host/`). A **render node**
is a Linux DRM device file (`/dev/dri/renderD*`) through which a program allocates GPU buffers;
Android gives apps none (on the developer's phone `/dev/dri` is permission-denied, and the GPU
driver is KGSL, which has no DRM node). **GBM** is Mesa's buffer allocator, which needs a DRM fd.
**pixman** is a CPU rasteriser; a compositor that renders with it sends plain shared-memory frames
(`wl_shm`).

## 1. Summary

A nested Wayland desktop is just one more client of our compositor, and it becomes one Linux
window like any other app. Three things decide whether a project's documented command works in a
launcher terminal, unchanged:

1. **Its shell has `XDG_RUNTIME_DIR`.** Every one of the six compositors refuses to start without
   it, because each creates its own socket there for its own apps (section 4). This is the one
   gap that blocks every desktop today, and it sits against the decision that the launcher never
   sets that variable. **Conflict, flagged:** the brief's goal (upstream commands as written) and
   that decision cannot both hold; something has to put the variable into the shell.
2. **Our compositor offers the globals and versions its nested backend binds.** wlroots (sway,
   labwc), weston and niri need nothing we lack. KWin refuses to start without
   `wp_single_pixel_buffer_manager_v1` and `zwp_pointer_constraints_v1`. Aquamarine (Hyprland)
   binds `wl_compositor` at v6 and `zwp_linux_dmabuf_v1` at v4; we advertise v5 and v3, so the bind
   is a protocol error and the connection is cut.
3. **It can render without a render node.** wlroots and weston fall back to pixman and `wl_shm` by
   themselves. niri renders through an ordinary EGL window (`wl_egl_window`), so Mesa's own
   software fallback, or the launcher's patched Mesa, serves it. KWin needs a render device:
   either a real render node or `/dev/udmabuf` plus Mesa's software EGL device; on a phone the
   first does not exist and the second is unchecked. Hyprland needs a render node twice over (its
   buffer allocator is GBM-only and its renderer opens EGL on GBM or an EGL device from a DRM fd);
   nothing on our side can supply one.

What to do, ranked by what it unlocks:

1. **Put `XDG_RUNTIME_DIR=$TMPDIR` into every shell, the same value everywhere** (a prefix-wide
   `profile.d` default that only fills it when empty, so ssh and Termux:Boot shells agree with
   terminal sessions). This is a decision for the developer, since it revisits "the launcher never
   sets `XDG_RUNTIME_DIR`" (section 6 explains why this form avoids the split that decision was
   made for). Without it, every guide needs a launcher-specific extra line.
2. **Configure a nested desktop's window at scale 1** (its configure size in real pixels), the way
   the rootful Xwayland desktop is already handled (`WaylandDesktopServer.hostScale`). wlroots'
   nested backend ignores output and fractional scale and draws one buffer pixel per logical
   pixel, so at the phone's 2.5 it would be blurry and huge **[inference]**. With scale 1 the user
   sets the scale inside the desktop with its own documented command (`output * scale 2.5` in
   sway, `<scale>` in labwc's `rc.xml`).
3. **Add the cheap globals smithay 0.7 already has:** `wp_single_pixel_buffer_manager_v1`,
   `zwp_pointer_constraints_v1` with `zwp_relative_pointer_manager_v1` (already planned in the
   window policy, table 5), `zwp_keyboard_shortcuts_inhibit_manager_v1`, and `wl_compositor` v6.
   These remove KWin's and Aquamarine's start-up refusals, but do not make either draw.
4. **Do not chase Hyprland or KWin on the phone's GPU.** Hyprland cannot run without patching
   Aquamarine and Hyprland themselves. KWin can run only if `/dev/udmabuf` is open to an app
   (open question 3) and only in software; Plasma's Wayland session is not packaged in Termux.
5. **Offer niri through a container, or a Termux build through tlstore.** niri's nested mode is
   upstream-documented and needs nothing more from us than (1); it is not in Termux's repos.

Plainly, per desktop:

| Desktop | Upstream's documented start works unchanged? |
|---|---|
| **sway** (Termux 1.12) | Yes once the shell has `XDG_RUNTIME_DIR`. Our side only. Renders on the CPU (pixman). |
| **labwc** (Termux 0.20.3) | Same as sway. labwc documents nested use itself (`-t`, `WLR_WL_OUTPUTS`). |
| **weston** (Termux 16.0.2, reference) | Same, and its GL renderer can use the launcher's patched Mesa. |
| **niri** | Our side only (`XDG_RUNTIME_DIR`), but only from a container (glibc) or a build we would have to package. Upstream calls windowed mode "mainly meant for development". |
| **KWin / Plasma** | Needs two globals on our side, then a render device the phone probably cannot give (`/dev/udmabuf`, unchecked). Plasma in Termux is X11-only (`kwin-x11`). Treat as needing KWin patched. |
| **Hyprland** | Needs Aquamarine and Hyprland patched. Not possible from our side. |

## 2. Per-desktop findings

### 2.1 Hyprland (Aquamarine backend)

**What its docs tell a user.** "Hyprland can be executed by typing `start-hyprland` in your TTY"
([master-tutorial.md](https://github.com/hyprwm/hyprland-wiki/blob/bd104c02d476c726d665e4ee14e861b97c21b67d/content/getting-started/master-tutorial.md),
"Launching Hyprland"). `start-hyprland` is a watchdog that runs `Hyprland` and restarts it in safe
mode after a crash ([start/src/main.cpp](https://github.com/hyprwm/Hyprland/blob/e537b2fce29e4bac191e73fd3c77ec3de80b7ffe/start/src/main.cpp)).
Nested use is documented only for development: "Launch the output `Hyprland` binary in `./build/`
_when logged into a Hyprland session_. A new window should open with Hyprland running inside of it"
([contributing-and-debugging/_index.md](https://github.com/hyprwm/hyprland-wiki/blob/bd104c02d476c726d665e4ee14e861b97c21b67d/content/contributing-and-debugging/_index.md),
"Launch the dev env"). The same tutorial says that in a VM without 3D acceleration "Hyprland
_**will not work**_".

**Nested backend requirements.** Hyprland asks Aquamarine for DRM "if available", then Wayland as
the fallback ([src/Compositor.cpp:335-342](https://github.com/hyprwm/Hyprland/blob/e537b2fce29e4bac191e73fd3c77ec3de80b7ffe/src/Compositor.cpp)).
Aquamarine's Wayland backend
([src/backend/Wayland.cpp:115-150](https://github.com/hyprwm/aquamarine/blob/ca1a485aa90be48fad32a42160da7679d678ade3/src/backend/Wayland.cpp)):
- binds `wl_seat` v9, `xdg_wm_base` v6, `wl_compositor` **v6**, `wl_shm` v1 and
  `zwp_linux_dmabuf_v1` **v4**, all at fixed versions; start-up fails with "Missing protocols"
  without any of them;
- reads the dmabuf feedback's `main_device`, opens that node, and fails start-up if the open fails
  (`Wayland.cpp:389-468`); output frames go out as dmabufs only (`wl_shm` is used for the cursor,
  `Wayland.cpp:803-828`);
- has no `wl_touch` handling and no `wp_fractional_scale_v1`; the output follows the xdg_toplevel
  configure (read earlier in this session).

The open point from the brief is closed by
[src/backend/Backend.cpp:163-189](https://github.com/hyprwm/aquamarine/blob/ca1a485aa90be48fad32a42160da7679d678ade3/src/backend/Backend.cpp):
nested without DRM drops the session, then the only allocator is `CGBMAllocator` on a reopened DRM
fd; with no backend holding a DRM fd it logs "Cannot open backend: no allocator available" and
start fails. There is no shm or software allocator.

Hyprland's renderer
([src/render/OpenGL.cpp:305-391](https://github.com/hyprwm/Hyprland/blob/e537b2fce29e4bac191e73fd3c77ec3de80b7ffe/src/render/OpenGL.cpp)):
EGL display on `EGL_PLATFORM_DEVICE_EXT` (an EGL device found from the DRM fd) or else
`EGL_PLATFORM_GBM_KHR` (GBM on the render node), asserting if neither works; a GLES 3.2 context,
retrying 3.0 (`OpenGL.cpp:215-236`); it asserts the procs of `EGL_ANDROID_native_fence_sync`
(`eglCreateSyncKHR`, `eglDupNativeFenceFDANDROID`, `eglWaitSyncKHR`) and
`GL_EXT_texture_format_BGRA8888`. No software path is documented or coded.

**Operating system.** `XDG_RUNTIME_DIR` must be set and non-empty or Hyprland exits
([src/main.cpp:201-204](https://github.com/hyprwm/Hyprland/blob/e537b2fce29e4bac191e73fd3c77ec3de80b7ffe/src/main.cpp));
it keeps its data in `$XDG_RUNTIME_DIR/hypr` and warns when the path does not start with
`/run/user` (`Compositor.cpp:214-223`). It calls `dbus-update-activation-environment` and
`systemctl --user` when present (`Compositor.cpp:571-574`, `822-825`); neither is required
**[inference: the calls are shell commands whose failure is not checked here]**. Nested, Aquamarine
drops the libseat session, so no seatd or logind is needed. In Termux: not packaged. In a
container: the render-node requirement fails first.

**Packaging.** Not in Termux's repos (no `hyprland`, `aquamarine` or `hyprutils` under
`packages/`, `x11-packages/` or `root-packages/` at `9f05b9e`). Arch Linux ARM aarch64:
`hyprland 0.57.2-2`, `aquamarine 0.16.0-1`. Debian: source `hyprland 0.57.2+ds-1` in sid,
`0.57.0+ds-1` in forky, `0.55.2+ds-1~bpo13+1` in trixie-backports (arm64 build **[unverified]**).

### 2.2 niri

**What its docs tell a user.** "If you're not using a display manager, you should run
`niri-session` (systemd/dinit) or `niri --session` (others) from a TTY." Then: "You can also run
`niri` inside an existing desktop session. Then it will open as a window, where you can give it a
try. Note that this windowed mode is mainly meant for development, so it is a bit buggy (in
particular, there are issues with hotkeys)"
([docs/wiki/Getting-Started.md:46-58](https://github.com/YaLTeR/niri/blob/6072f67d9fe0f1c1182f72cc5d4f2ca5f047bf9e/docs/wiki/Getting-Started.md)).
So the documented nested command is plain `niri`, not `niri --session`: with `--session`, niri
removes `WAYLAND_DISPLAY` and `DISPLAY` from its environment and goes for a TTY
([src/main.rs:73-90](https://github.com/YaLTeR/niri/blob/6072f67d9fe0f1c1182f72cc5d4f2ca5f047bf9e/src/main.rs)).

**Nested backend requirements.** With `WAYLAND_DISPLAY` or `DISPLAY` set, niri picks its winit
backend ([src/niri.rs:800-806](https://github.com/YaLTeR/niri/blob/6072f67d9fe0f1c1182f72cc5d4f2ca5f047bf9e/src/niri.rs)).
That backend
([src/backend/winit.rs](https://github.com/YaLTeR/niri/blob/6072f67d9fe0f1c1182f72cc5d4f2ca5f047bf9e/src/backend/winit.rs))
opens a 1280x800 winit window and renders with smithay's `GlesRenderer` on an EGL surface made from
that window. In smithay 0.7.0's winit backend, that is EGL on the Wayland platform
(`EGL_KHR_platform_wayland`) with a `wl_egl_window`, asking for GLES 3.0
(`smithay-0.7.0/src/backend/winit/mod.rs:88-150`, `src/backend/egl/native.rs:194-208`). niri builds
smithay from git, not 0.7.0, so the exact lines may differ **[unverified for niri's pinned smithay]**.
A render node is optional: niri asks the EGL device for one and, when that fails, logs it and
advertises its own dmabuf global without feedback (`winit.rs:211-272`). So niri is an ordinary EGL
client of the host: whatever EGL path the host's clients get, niri gets. The globals are what winit
binds for any window **[inference: winit's Wayland needs are not read here; our compositor already
serves GTK and Qt windows, which need the same core set]**.

**Input and output.** Window resizes become output mode changes and winit's scale factor becomes
the output scale (`winit.rs:122-155`), so niri follows the host's configure and fractional scale.
One output. `Mod` is `Alt` when nested and `Super` on a TTY, configurable with `mod-key-nested`
([Configuration:-Input.md:394-411](https://github.com/YaLTeR/niri/blob/6072f67d9fe0f1c1182f72cc5d4f2ca5f047bf9e/docs/wiki/Configuration:-Input.md)).
Touch comes through winit's input events **[inference]**.

**Operating system.** niri binds its socket with smithay's `ListeningSocketSource::new_auto()` and
unwraps the result (`src/niri.rs:2841`); that binds under `XDG_RUNTIME_DIR` and returns
`RuntimeDirNotSet` without it (wayland-server 0.31.14, `src/socket.rs:32-36`), so niri panics with
no runtime directory **[inference from the two files]**. D-Bus services start only with `--session`
(or a debug option) ([src/dbus/mod.rs:51-80](https://github.com/YaLTeR/niri/blob/6072f67d9fe0f1c1182f72cc5d4f2ca5f047bf9e/src/dbus/mod.rs)),
so plain nested `niri` needs no session bus. X11 apps go through `xwayland-satellite` when it is
on `PATH` (docs/wiki/Xwayland.md).

**Packaging.** Not in Termux's repos. Arch Linux ARM aarch64: `niri 26.04-1`. Debian: no `niri`
source package in the sources.debian.org index.

### 2.3 KWin (Plasma)

**What its docs tell a user.** KWin's contributing guide shows the nested start:
`env QT_PLUGIN_PATH="$(pwd)":"$QT_PLUGIN_PATH" dbus-run-session ./kwin_wayland --xwayland konsole`,
and explains that `dbus-run-session` keeps the nested KWin from clashing with the session's own on
the bus ([CONTRIBUTING.md:88-105](https://invent.kde.org/plasma/kwin/-/blob/f1e7d1d4430586d818d9d893f40b157b0f682dbd/CONTRIBUTING.md)).
It is framed as a development set-up. With no backend option, KWin "automatically choos[es]
Wayland because WAYLAND_DISPLAY is set" (`src/main_wayland.cpp:520-521`). The nested backend takes
`--width`, `--height`, `--output-count`, `--scale` and `--fullscreen` (`main_wayland.cpp:348-406`).

**Nested backend requirements.** KWin's Wayland backend refuses to start unless the host offers
`wl_compositor` (bound at up to v4), `wl_subcompositor`, `xdg_wm_base`,
**`wp_single_pixel_buffer_manager_v1`**, `wp_viewporter`, `wl_seat` (up to v5),
**`zwp_pointer_constraints_v1`** and `wp_presentation`; `xdg_toplevel_icon_manager_v1` and (in
6.8.3) `zwp_keyboard_shortcuts_inhibit_manager_v1` are optional; `zwp_linux_dmabuf_v1` is taken
only at v4 or newer
([src/backends/wayland/wayland_display.cpp:220-275, 382-433](https://invent.kde.org/plasma/kwin/-/blob/f1e7d1d4430586d818d9d893f40b157b0f682dbd/src/backends/wayland/wayland_display.cpp);
the same checks are in `v6.8.3`). The output layer reads `linuxDmabuf()->formats()` without a
null check (`src/backends/wayland/wayland_layer.cpp:151, 210, 235`), so a host without dmabuf v4
would likely crash it at the first frame **[inference]**. Frames are dmabufs from KWin's own
swapchain, or `wl_shm` when the buffer is a shared-memory one (`wayland_backend.cpp:642-680`).

Rendering is OpenGL only. `Compositor::attemptOpenGLCompositing` needs a render device, and
`createRenderer` is fatal without one: "Couldn't create a renderer. We are going to quit KWin now"
([src/compositor.cpp:262-297](https://invent.kde.org/plasma/kwin/-/blob/f1e7d1d4430586d818d9d893f40b157b0f682dbd/src/compositor.cpp)).
In `v6.8.3`, the `QPainterCompositing` case is still listed but creates no backend, and KWin quits
with "The used windowing system requires compositing" (`v6.8.3 src/compositor.cpp:311-357`). A
render device is a DRM node found through udev, or the software device: KWin opens `/dev/udmabuf`
and, if that works, makes an EGL device from Mesa's `EGL_MESA_device_software` (llvmpipe)
([src/core/gpumanager.cpp:79-97](https://invent.kde.org/plasma/kwin/-/blob/f1e7d1d4430586d818d9d893f40b157b0f682dbd/src/core/gpumanager.cpp),
[src/core/renderdevice.cpp:369-389](https://invent.kde.org/plasma/kwin/-/blob/f1e7d1d4430586d818d9d893f40b157b0f682dbd/src/core/renderdevice.cpp)).
`KWIN_DRM_DEVICES` set to paths that do not resolve picks the software device
(`compositor.cpp:203-219`). No switch avoids needing one of the two.

**Input and output.** The nested backend handles pointer, keyboard and touch
(`wayland_backend.h:43, 70`), binds `wp_fractional_scale_v1` (`wayland_output.cpp:112-117`), locks
the pointer through pointer constraints and relative pointer (`wayland_backend.cpp:458-465`), and
can show several outputs (`--output-count`).

**Operating system.** Nested KWin uses its `Noop` session, so no logind or seatd
(`main_wayland.cpp:603-617`). It wants a session bus (the documented `dbus-run-session`). Its
`GpuManager` always creates a udev monitor (`gpumanager.cpp:82-95`); Termux's `kwin-x11` recipe
carries a `disable-udev.patch`, which suggests udev is a problem in Termux too **[inference]**. Its
socket comes from `wl_display_add_socket_auto`, which needs `XDG_RUNTIME_DIR` in upstream
libwayland (section 6). A whole Plasma session falls back to "classic boot" (no systemd) when the
`plasma-workspace.target` unit is absent (plasma-workspace `startkde/startplasma.cpp`,
`useSystemdBoot()`).

**Packaging.** Termux has `kwin-x11 6.7.6` only, and its `plasma` metapackage depends on
`kwin-x11`, so Plasma in Termux is an X11 session; there is no `kwin_wayland` package. Arch Linux
ARM aarch64: `kwin 6.8.3-3`. Debian: `kwin 4:6.7.4-2` (forky, sid), `4:6.3.6-1+deb13u1` (trixie).

### 2.4 sway (wlroots 0.20)

**What its docs tell a user.** "Run `sway` from a TTY or from a display manager"
([README.md:56](https://github.com/swaywm/sway/blob/9bb430936ae6e7a96fca8294645709263aac362a/README.md));
"You can run sway directly from a tty, or via a Wayland-compatible login manager" (`sway(1)`,
[sway/sway.1.scd:43](https://github.com/swaywm/sway/blob/9bb430936ae6e7a96fca8294645709263aac362a/sway/sway.1.scd)).
sway does not document nesting; wlroots does: `WAYLAND_DISPLAY`/`WAYLAND_SOCKET` "if set probe
Wayland backend in `wlr_backend_autocreate`"
([docs/env_vars.md](https://gitlab.freedesktop.org/wlroots/wlroots/-/blob/0.20.2/docs/env_vars.md)),
and `backend/backend.c:367-379` picks the Wayland backend first when either is set. So typing
`sway` in a shell that has `WAYLAND_DISPLAY` opens it as a window.

**Nested backend requirements** (wlroots 0.20.2,
[backend/wayland/backend.c:355-421, 600-700](https://gitlab.freedesktop.org/wlroots/wlroots/-/blob/0.20.2/backend/wayland/backend.c)):
required are `wl_compositor` (bound at v4) and `xdg_wm_base` (v1); everything else is optional:
`wl_seat`, `wl_shm`, `zwp_linux_dmabuf_v1` (v3 or newer), `wl_drm`, `wl_subcompositor`,
`wp_viewporter`, `wp_presentation`, `xdg_activation_v1`, `zxdg_decoration_manager_v1`,
`zwp_relative_pointer_manager_v1`, `zwp_pointer_gestures_v1`, `zwp_tablet_manager_v2`,
`wp_linux_drm_syncobj_manager_v1`. A render node is learned from dmabuf feedback (v4) or `wl_drm`;
with neither, the backend's DRM fd is -1.

Renderer choice ([render/wlr_renderer.c:142-280](https://gitlab.freedesktop.org/wlroots/wlroots/-/blob/0.20.2/render/wlr_renderer.c)):
GLES2 and Vulkan both need a DRM fd; with none from the backend, wlroots tries any render node
because the backend accepts dmabufs, and on failure skips them; then "pixman" is chosen
automatically when the backend has no render node. `WLR_RENDERER=pixman` forces it. The pixman
renderer is always built (`render/pixman/meson.build`). `WLR_RENDERER_FORCE_SOFTWARE=1` instead
runs GLES2 on Mesa's software EGL device, which then needs `/dev/udmabuf` for its buffers (section
7). Allocator
([render/allocator/allocator.c:98-165](https://gitlab.freedesktop.org/wlroots/wlroots/-/blob/0.20.2/render/allocator/allocator.c)):
GBM needs a DRM fd; next is the shm allocator when backend and renderer both take shm, which they
do here. So under a host with no render node, a nested wlroots compositor renders with pixman into
`wl_shm` buffers **[inference from the selection code; not run]**. There is no `wl_egl_window`
path, so no client-EGL GPU route.

**Input and output.** Touch is handled (`backend/wayland/seat.c:113-135`), with one output's
mapping ("TODO: multi-output touch support"). Relative pointer is used when offered; pointer
constraints and keyboard-shortcuts-inhibit are not bound. No fractional scale and no buffer scale
in the backend: the toplevel's configure size becomes the output's pixel mode
([backend/wayland/output.c:1034-1087](https://gitlab.freedesktop.org/wlroots/wlroots/-/blob/0.20.2/backend/wayland/output.c));
the default is 1280x720 until configured (`output.c:1113`). `WLR_WL_OUTPUTS=N` opens N windows as N
outputs.

**Operating system.** sway exits with "XDG_RUNTIME_DIR is not set in the environment. Aborting."
when it is unset ([sway/main.c:280-286](https://github.com/swaywm/sway/blob/9bb430936ae6e7a96fca8294645709263aac362a/sway/main.c));
its IPC socket also lives there (`sway/ipc-server.c:136`). Nested needs no session (libseat) and
no D-Bus. In Termux, wlroots is built with `-Dsession=disabled`, so the DRM path is gone anyway.

**Packaging.** Termux: `sway 1.12`, `wlroots 0.20.2` (`-Dbackends=x11,termuxgui
-Drenderers=gles2,vulkan -Dsession=disabled`; the Wayland backend is always built), `swaybg`,
`xwayland`. Termux patches sway's IPC fallback directory to `$PREFIX/tmp` but leaves the
`XDG_RUNTIME_DIR` check in `main.c` (`x11-packages/sway/0001-fix-hardcoded-paths.patch`). Arch
Linux ARM: `sway 1:1.12-4`. Debian: `1.12-2` (forky, sid), `1.10.1-2` (trixie).

### 2.5 labwc (wlroots 0.20)

**What its docs tell a user.** `labwc(1)` documents the nested case directly: `-t, --title`
"Set the window title for labwc to use when it is running in a window (i.e. nested in a
compositor) ... This is useful when simulating multiple screens, such as with running labwc with
the environment variable `WLR_WL_OUTPUTS=2`"; and `-S, --session <command>` to run a session
client that ends labwc on exit
([docs/labwc.1.scd:55-82](https://github.com/labwc/labwc/blob/4d5f9cb843c8f32d4b0b338d88fed6ba3b2a5a75/docs/labwc.1.scd)).
When nested, labwc does not update the D-Bus or systemd activation environment unless
`LABWC_UPDATE_ACTIVATION_ENV` says so (`labwc.1.scd:104-125`).

**Nested backend, input, output.** labwc builds against `wlroots-0.20` (`meson.build:58`), so
everything in 2.4 applies: pixman and `wl_shm` without a render node, touch, no fractional scale,
`WLR_WL_OUTPUTS`.

**Operating system.** "XDG_RUNTIME_DIR is unset" and exit
([src/main.c:276-279](https://github.com/labwc/labwc/blob/4d5f9cb843c8f32d4b0b338d88fed6ba3b2a5a75/src/main.c));
its socket comes from `wl_display_add_socket_auto` or `--socket` (`src/server.c:853-860`). No
session or bus needed nested.

**Packaging.** Termux: `labwc 0.20.3`. Arch Linux ARM: `labwc 0.20.3-2`. Debian: `0.20.3-1`
(forky, sid), `0.8.3-1` (trixie).

### 2.6 weston (reference)

**What its docs tell a user.** `weston(1)`: if `WAYLAND_DISPLAY` is set, "the default backend
becomes *wayland*. This allows launching Weston as a nested server", with the example
`WAYLAND_DISPLAY=wayland-0 weston -Swayland-1`; Wayland backend options `--display`,
`--fullscreen`, `--output-count=N`, `--width`, `--height`, `--scale=N`, and `--use-pixman`
(deprecated for `--renderer`): "By default weston will try to use EGL and GLES2 for rendering and
will fall back to the pixman-based renderer for software compositing if EGL cannot be used"
([man/weston.man](https://gitlab.freedesktop.org/wayland/weston/-/blob/4095e2b5da1a05f16789696868a2cb5d392cceb3/man/weston.man),
"Wayland backend options", "ENVIRONMENT", "EXAMPLES").

**Nested backend requirements.** `wl_compositor`, `xdg_wm_base`, `wl_seat`, `wl_output`, `wl_shm`;
`wl_shm` is mandatory
([libweston/backend-wayland/wayland.c:2628-2654, 2854](https://gitlab.freedesktop.org/wayland/weston/-/blob/4095e2b5da1a05f16789696868a2cb5d392cceb3/libweston/backend-wayland/wayland.c)).
The GL renderer draws into a `wl_egl_window` (`wayland.c:786-789`), so it is an ordinary EGL client
of the host; pixman uses `wl_shm`. It handles `wl_touch` (`wayland.c:2079-2306`) and follows the
toplevel configure (`wayland.c:1093-1130`).

**Operating system.** weston exits if `XDG_RUNTIME_DIR` is unset or not a directory, and warns
when it is not mode 0700 and owned by the user
([frontend/main.c:655-677](https://gitlab.freedesktop.org/wayland/weston/-/blob/4095e2b5da1a05f16789696868a2cb5d392cceb3/frontend/main.c)).

**Packaging.** Termux: `weston 16.0.2` with `-Dbackend-drm=false -Drenderer-gl=true
-Dbackend-default=headless` and no GBM; the Wayland backend is on by weston's default
(`meson_options.txt:36-38`). Arch Linux ARM: `weston 15.0.1-3`. Debian: `16.0.2-1` (forky, sid),
`14.0.2-1` (trixie).

**Upstream disagreement, noted.** The weston man page says `WAYLAND_DISPLAY` is "the name ...
without the path. The directory path is always taken from XDG_RUNTIME_DIR." libwayland's own
client code has taken an absolute path as-is since 1.15
([src/wayland-client.c:1158-1208, 1345-1355](https://gitlab.freedesktop.org/wayland/wayland/-/blob/f2ecaa61a3a82944ae069de5413104ab7ba2109f/src/wayland-client.c)).
The man page is stale; the code is what runs.

## 3. Our compositor today, and the gap

### 3.1 What we advertise (at `85835667a`)

Globals are created in `wayland-host/src/android/host.rs:706-726`; versions come from smithay
0.7.0's constructors (paths under `smithay-0.7.0/src/wayland/`):

| Global | Version | Source |
|---|---|---|
| `wl_compositor` / `wl_subcompositor` | 5 / 1 | `CompositorState::new` (`compositor/mod.rs:680-706`; `new_v6` exists) |
| `xdg_wm_base` | 6 | `shell/xdg/mod.rs:1252` |
| `wl_shm` | 2, ARGB8888 + XRGB8888 | `host.rs:708-709`, `shm/mod.rs:157` |
| `wl_output` / `zxdg_output_manager_v1` | 4 / 3 | `host.rs:710`, `output/mod.rs:132, 208` |
| `wp_fractional_scale_manager_v1` | 1 | `host.rs:711` |
| `wp_viewporter` | 1 | `host.rs:712` |
| `zwp_linux_dmabuf_v1` | **3**, no feedback, no main device | `host.rs:713-716`, `dmabuf/mod.rs:719` (v5 only with a default feedback) |
| `wl_seat` | 9 | `seat/mod.rs:176` |
| `wl_data_device_manager` | 3 | `selection/data_device/mod.rs:207` |
| `zxdg_decoration_manager_v1` | 1 (server-side asked) | `host.rs:719`, `shell/xdg/decoration.rs:146` |
| `xwayland_shell_v1` | (Xwayland only) | `host.rs:720-721` |
| `zwp_text_input_manager_v3` | 1 | `wayland-host/src/android/text_input.rs:46, 66` |
| `xdg_activation_v1` | 1 | `host.rs:723`, `xdg_activation/mod.rs:187` |
| `zwp_idle_inhibit_manager_v1` | 1 | `host.rs:724` |
| `wp_presentation` | 2 | `host.rs:726`, `presentation/mod.rs:105` |

dmabufs: four 32-bit formats (ARGB, XRGB, ABGR, XBGR 8888), linear modifier only
(`wayland-host/src/android/source.rs:63-68`); an import with another format, modifier or more than
one plane is refused (`source.rs:71-79`). No `wl_drm`.

Seat: pointer and touch always (`host.rs:728-730`); keyboard when the xkb data is usable
(`host.rs:731-741`). `wl_touch` is offered and Android touches are sent through it
(`host.rs:145`). Meta keys map to `KEY_LEFTMETA` (`wayland-host/src/keymap.rs:45`;
`LinuxWindowKeys.java:39`), so Super reaches clients.

Output: one, `TL-1`, at the page size, with a custom scale whose integer part is the ceiling of
the fractional one (`host.rs:743-757, 869-871`; `geometry.rs:154-156`); the screen density rounded
to a quarter is the default (2.5 on the developer's phone). Each toplevel can carry its own scale
(`host.rs:435-437`), and `wp_fractional_scale` answers with it (`host.rs:2668-2674`); the rootful
Xwayland desktop uses that to draw at a whole factor
(`app/src/main/java/com/termux/app/wayland/WaylandDesktopServer.java:42-50`).

Not offered: `wp_single_pixel_buffer_manager_v1`, `zwp_pointer_constraints_v1`,
`zwp_relative_pointer_manager_v1`, `zwp_keyboard_shortcuts_inhibit_manager_v1`, primary selection,
`wp_cursor_shape_v1`, layer-shell, `xdg_toplevel_icon_v1`, `linux-drm-syncobj`. smithay 0.7.0 has
all of these (`single_pixel_buffer/mod.rs:74`, `pointer_constraints.rs:220`,
`relative_pointer.rs:180`, `keyboard_shortcuts_inhibit/mod.rs:76`, ...).

Environment (`app/src/main/java/com/termux/app/wayland/LinuxWindowsEnv.java`): launched apps get
`WAYLAND_DISPLAY=<absolute socket path>`, `DISPLAY` while Xwayland runs, `GDK_BACKEND=wayland,x11`
and `QT_QPA_PLATFORM=wayland;xcb` (`:165-175`). New terminal sessions get `WAYLAND_DISPLAY` (and
`DISPLAY`) when Connect new shells is on (`:139-151`), from the moment the launcher listens on the
socket while Wayland Display is chosen, not only while the host runs
(`WaylandAppRunner.java:664-669`). `WAYLAND_DISPLAY` is the absolute path, because a new session
has no `XDG_RUNTIME_DIR` (`:115-121`). `XDG_RUNTIME_DIR` is never set (`:29-33`). Containers see the
same absolute path through proot-distro's default prefix bind (`:34-35`).

### 3.2 The gap table

"Ours" means closable in `wayland-host` or the launcher. "Not ours" means only a change in the
desktop, the OS or the hardware closes it.

| Desktop | Missing on our side for the documented start | Cannot be closed from our side |
|---|---|---|
| sway | `XDG_RUNTIME_DIR` in the shell; scale 1 for its window (for sharpness) | GPU: wlroots' GL and Vulkan need a DRM fd, so it stays on pixman |
| labwc | same as sway | same as sway |
| weston | `XDG_RUNTIME_DIR` (a 0700 directory, else a warning) | nothing; GL goes through the client EGL path |
| niri | `XDG_RUNTIME_DIR`; a build for Termux, or set-up in a container | not packaged in Termux; in a container no GPU (llvmpipe) |
| KWin / Plasma | `XDG_RUNTIME_DIR`; `wp_single_pixel_buffer_manager_v1`; `zwp_pointer_constraints_v1`; dmabuf v4 | a render device: a DRM node (none on the phone) or `/dev/udmabuf` + `EGL_MESA_device_software` (unchecked); udev; no Wayland KWin in Termux |
| Hyprland | `XDG_RUNTIME_DIR`; `wl_compositor` v6; dmabuf v4 with a feedback `main_device` | a DRM node to open for GBM and EGL; no shm allocator, no software renderer |

A dmabuf v4/v5 global with feedback needs a `main_device`. We have no DRM device to name, and
naming one that cannot be opened would make Mesa's own clients try and fail it (Mesa takes the
render node from that feedback, section 7). So dmabuf v4 is only honest for KWin's software device
case (open question 3) **[inference]**.

## 4. Decisions this research touches

- **Layer-shell.** A nested desktop's panels, docks and wallpapers (waybar, swaybg, plasmashell)
  attach to the nested compositor's own layer-shell, inside its window. Nothing here asks our
  compositor for background or bottom layers, so the decided limited layer-shell (overlay and top
  only) is unaffected.
- **No display manager.** Every route below starts the desktop from a shell or a drawer entry.
- **X11 Display** is not part of any route.
- **The spec's proposal for Wayland desktops.** `project-docs/active/linux-windows-wayland-2026-11-24.md:95-99`
  says "A Wayland-native desktop (labwc, KWin) would run nested the same way, as one client
  window." Upstream source agrees for labwc and disagrees for KWin: KWin needs globals we lack and
  a render device (2.3).
- **`XDG_RUNTIME_DIR`.** See section 6; the goal conflicts with the decision as written.

## 5. Shapes we could offer

**A. The desktop is a client (recommended).** The user opens a terminal and types the command
from the desktop's own guide (`sway`, `labwc`, `weston`, `niri`, inside a container for niri). Our
compositor gives it one Linux window, full screen in the row like any Main window
(`compositor-window-policy-2026-11-25.md`), and the desktop's own apps, panels and launcher live
inside it. The launcher provides only the environment (section 6) and protocols (3.2).
- Gain: no launcher-specific scripts; every upstream doc, config and troubleshooting page applies;
  closing the window ends the desktop; a crash takes down one window, not the home screen.
- Lose: wlroots desktops render on the CPU; one output unless `WLR_WL_OUTPUTS`; the launcher's
  border drag and rim tab sit around a desktop that also wants every edge; Super combinations need
  the launcher keyboard's Meta key or a hardware keyboard (open question 5).

**B. A drawer entry per desktop, on top of A.** The spec already proposes tlstore items for
desktop sessions. For a Wayland desktop the entry would only run the same upstream command
(`Exec=sway`) with the environment of A, so it adds one-tap starting without a wrapper script.
- Gain: discoverable; the drawer can show the desktop's icon.
- Lose: a second way in that must stay identical to the terminal path; an entry that quietly adds
  variables becomes the band-aid this note is meant to avoid.

**C. A full-screen state for a desktop window.** The window policy already plans `set_fullscreen`
handling (hide status bar, extra keys and rim; swipe up shows the extra keys; Back leaves). weston
and KWin can ask for it themselves (`--fullscreen`); wlroots' nested backend never asks
**[inference: no `set_fullscreen` in `backend/wayland/`]**, so the rim tab would need a Full screen
item.
- Gain: the desktop gets the whole screen and every edge.
- Lose: the launcher's gestures are hidden while it is up; one more state to get out of.

**D. GPU for nested desktops.** Only compositors that render through an ordinary client EGL window
(niri, weston's GL renderer) can use the launcher's patched Mesa (`LIBGL_KOPPER_DRI2=1`, Turnip +
Zink), and only when built for bionic (Termux). wlroots, KWin and Aquamarine allocate their own
buffers on a DRM fd; the AHardwareBuffer zero-copy route planned in the spec hands buffers to
clients through a new protocol and Mesa WSI patch, which none of these backends would speak.
- Gain (niri, weston): GPU rendering inside the desktop window.
- Lose: niri needs a Termux build we package; a container gets no GPU variables by design.

**E. Patch the desktops.** A shm allocator for Aquamarine plus a surfaceless or `wl_egl_window`
renderer path for Hyprland; restored QPainter compositing for KWin. Prior art for this kind of
change exists in Termux itself: its wlroots carries a `termuxgui` backend that draws straight into
an Android view through Termux:GUI (`x11-packages/wlroots/0003-impl-termux-gui-backend.patch`,
`src/backend/termuxgui/`).
- Gain: Hyprland and Plasma on the phone.
- Lose: forks of two fast-moving projects; users' upstream docs stop matching; it is exactly the
  launcher-specific system the brief rules out.

**Recommendation: A, with B as a thin convenience and C when the policy's full-screen slice
lands.** Do the environment work (6) and the scale-1 window first; they make sway, labwc and weston
work from their own docs with no other change. Add the cheap globals (3.2) next. State in
`docs/en/` that Hyprland and Plasma's Wayland session need a GPU device Android does not give
apps, rather than building E.

## 6. The environment contract

What a shell in the launcher must already have for the documented commands to work as written:

1. **`WAYLAND_DISPLAY` naming our socket.** Already given to new terminal sessions while Wayland
   Display is chosen and Connect new shells is on (3.1). Every compositor here connects with
   `wl_display_connect(NULL)` or its Rust equivalent, which takes an absolute path as it is
   (libwayland `wayland-client.c:1171-1197`). Termux's libwayland 1.26.0 also falls back to
   `$PREFIX/tmp` for a relative name when `XDG_RUNTIME_DIR` is unset
   (`packages/libwayland/xdg-runtime-dir.patch`), so in Termux `wayland-0` alone would work too.
   A container's glibc libwayland has no such fallback, so there the absolute path is needed
   **[inference]**.
2. **`XDG_RUNTIME_DIR`, set to a writable directory owned by the user (0700 for weston's
   liking).** Not because of our socket, but because each nested compositor makes its own socket
   for its own apps there:
   - upstream libwayland's server refuses a relative socket name without it
     ([wayland-server.c:1908-1921, 1996-2026](https://gitlab.freedesktop.org/wayland/wayland/-/blob/f2ecaa61a3a82944ae069de5413104ab7ba2109f/src/wayland-server.c));
     Termux's patch falls back to `$PREFIX/tmp` there too;
   - but sway (`main.c:282`), labwc (`main.c:276`), weston (`frontend/main.c:657-663`) and
     Hyprland (`main.cpp:201`) check `getenv("XDG_RUNTIME_DIR")` themselves before any socket and
     exit, and niri's Rust socket code has no fallback. Termux's patch does not help any of them.
   - KWin goes through `wl_display_add_socket_auto` (`src/wayland/display.cpp:88-98`), so in
     Termux it would get the fallback; in a container it needs the variable.
   With `XDG_RUNTIME_DIR=$PREFIX/tmp`, a nested compositor takes the next free name
   (`wayland-1`; our socket holds `wayland-0` and its lock, `host.rs:759-769`), exports it as
   `WAYLAND_DISPLAY` to its own children, and they find it under the same directory.
3. **A session bus, only for KWin** (`dbus-run-session`, which is in KWin's documented command).
   sway, labwc, weston and nested niri start without one. labwc and Hyprland only call
   `dbus-update-activation-environment` when available.
4. **`XDG_SESSION_TYPE` and `XDG_CURRENT_DESKTOP` are not needed to start any of them.** Each
   nested compositor sets what its children need (`WAYLAND_DISPLAY`, sometimes
   `XDG_CURRENT_DESKTOP`); Aquamarine only logs the host's `XDG_CURRENT_DESKTOP`.

How item 2 sits with the decision that the launcher never sets `XDG_RUNTIME_DIR`: the decision was
made because a value only some shells had split tools that keep sockets there (a desktop given its
own value made Herdr start a second core; ssh and Termux:Boot shells would disagree with
launcher-only values). Three ways forward, for the developer to choose:
- **Keep the decision.** The note in `docs/en/` tells users to add `export
  XDG_RUNTIME_DIR=$TMPDIR` to their shell profile once. Then every shell (terminal, ssh, Boot)
  agrees because the user's own profile sets it. Upstream commands then work unchanged, after one
  line that is not in upstream's docs.
- **A prefix-wide default the launcher writes** (a `$PREFIX/etc/profile.d` file with
  `: "${XDG_RUNTIME_DIR:=$TMPDIR}"; export XDG_RUNTIME_DIR`), like the PulseAudio client drop-in
  it already writes. Every login shell, including ssh and Boot, gets the same value, so the split
  the decision guards against does not arise; fish does not read `profile.d`
  (`docs/agents/linux-apps.md`). This changes the decision, so it is the developer's call.
- **Set it only for launched desktops.** This is the per-desktop value that caused the Herdr split;
  not recommended.

Inside a proot-distro container, the launcher already writes launcher-owned files into each distro
(display variables); the same choice applies there, with the container's own `/tmp` or a
directory under it. Whether a default proot-distro login sets `XDG_RUNTIME_DIR` is
**[unverified]**.

## 7. GPU for a nested compositor on a phone without a render node

**What Mesa's Wayland EGL platform needs from the host**
([platform_wayland.c:2674-2730, 2731-2770, 3382-3388](https://gitlab.freedesktop.org/mesa/mesa/-/blob/bb7494e71c6b6e277724c83648bbf2e4309335e0/src/egl/drivers/dri2/platform_wayland.c)):
the hardware path takes its render node from the default dmabuf feedback (`main_device`, or the
first sampling tranche for dmabuf v6), else from `wl_drm`; with neither it fails. `eglInitialize`
then retries with Zink, which on this platform goes through the same function and fails the same
way, then forces software: the swrast path, which presents through `wl_shm`
([eglapi.c:688-722](https://gitlab.freedesktop.org/mesa/mesa/-/blob/bb7494e71c6b6e277724c83648bbf2e4309335e0/src/egl/main/eglapi.c)).
Under our compositor (dmabuf v3, no feedback, no `wl_drm`) stock Mesa therefore lands on llvmpipe,
which matches the spec's measurement (`glmark2-es2-wayland` reported `llvmpipe`,
`linux-windows-wayland-2026-11-24.md:378-388`). The launcher's patched `libEGL_mesa.so`
(`LIBGL_KOPPER_DRI2=1`) takes the kopper path with Turnip instead (spec `:396-401`).

**Which compositors are ordinary EGL clients.**
- **niri** (winit: `wl_egl_window`) and **weston's GL renderer** (`wl_egl_window`): yes. In Termux
  (bionic) they could use the patched Mesa; in a container they get llvmpipe.
- **wlroots** (sway, labwc): no. In auto mode GLES2 and Vulkan need a DRM fd
  (`WLR_RENDER_DRM_DEVICE` must name a real render node), so auto-selection lands on pixman, which
  is also what `WLR_RENDERER=pixman` forces. `WLR_RENDERER_FORCE_SOFTWARE=1` is the one switch
  that runs GLES2 with no DRM fd: wlroots then opens Mesa's software EGL device
  (`EGL_MESA_device_software`, llvmpipe) (`wlr_renderer.c:142-153`, `render/egl.c:558-580`). The
  GLES2 renderer takes only dmabufs (`render/gles2/renderer.c:528`), so its buffers must come from
  the udmabuf allocator (`allocator.c:154-162`), which needs `/dev/udmabuf` (open question 3); our
  host would accept those, since they are linear single-plane dmabufs **[inference]**. Either way
  it is CPU rendering; no route reaches the phone's GPU.
- **KWin**: no. It allocates through its render device (GBM, or udmabuf for the software device)
  and sends dmabufs. `KWIN_DRM_DEVICES` pointing nowhere selects the udmabuf software device
  (2.3); nothing avoids both.
- **Aquamarine / Hyprland**: no. GBM on a DRM fd, and EGL on GBM or an EGL device from that fd.
  No switch.

## 8. Prior art

Termux's own wlroots package adds a `termuxgui` backend: a wlroots compositor run without
`WAYLAND_DISPLAY` or `DISPLAY` draws into an Android view through the Termux:GUI plugin, with
touch and keys from it (`WLR_TGUI_OUTPUTS` for the output count;
`x11-packages/wlroots/0003-impl-termux-gui-backend.patch`,
`src/backend/termuxgui/{backend,output,input}.c` at termux-packages `9f05b9e`). The patch adds it
to `wlr_backend_autocreate` after the Wayland and X11 checks, so with `WAYLAND_DISPLAY` set a sway
started in a launcher shell still picks the Wayland backend (wlroots `backend/backend.c:367-379`).
One consequence: in a launcher shell that has no `WAYLAND_DISPLAY`, Termux's sway and labwc try
Termux:GUI before failing **[inference]**. I found no primary source for Android's own Linux
terminal display path and leave it out.

## 9. Open questions (checks on a phone)

1. **sway as documented.** In a launcher terminal with Wayland Display chosen, run
   `XDG_RUNTIME_DIR=$TMPDIR sway -d 2>&1 | grep -iE 'backend|renderer|allocator'`. Pass: a Linux
   window opens, the log names the Wayland backend, a pixman renderer and an shm allocator.
2. **labwc and weston the same way.** `XDG_RUNTIME_DIR=$TMPDIR labwc -d`, then
   `XDG_RUNTIME_DIR=$TMPDIR weston` with the patched Mesa overlay on the library path; check
   weston's log for `GL renderer` and the `GL_RENDERER` string (Turnip or llvmpipe).
3. **`/dev/udmabuf`.** From a launcher terminal, `ls -l /dev/udmabuf` and
   `test -r /dev/udmabuf && test -w /dev/udmabuf && echo usable`. If it is not usable, KWin cannot
   render on the phone and Plasma's Wayland session is out.
4. **Cost of a CPU-rendered desktop.** With sway full screen and one terminal scrolling inside it,
   read the compositor process's CPU (`top -p <pid of :linuxwindows>`) and sway's own CPU, and note
   whether scrolling looks smooth.
5. **Super key.** In nested sway, press the launcher keyboard's Meta key with Enter (sway's default
   `$mod+Return` opens a terminal), then the same on a hardware keyboard; note whether Android
   takes any Meta combination first.
6. **Scale.** Open nested sway at the default scale and compare text sharpness with the same sway
   after its window is configured at scale 1 and `output * scale 2.5` is set in its config.
7. **Touch.** Tap a button and drag a scrollbar inside nested sway's apps; pass if both act where
   the finger is.
8. **`$PREFIX/tmp` mode.** `stat -c %a $PREFIX/tmp`; weston warns unless it is 700.
9. **niri in a container.** In an Arch Linux ARM container, with `XDG_RUNTIME_DIR` set to a 0700
   directory and `WAYLAND_DISPLAY` the absolute socket path, run `niri`. Pass: one Linux window;
   `RUST_LOG=debug` shows the winit backend; note the renderer string.
10. **Session variables before the host runs.** Open a new terminal session after a launcher
    restart, before any Linux app, and run `echo $WAYLAND_DISPLAY`; pass if it already names the
    socket (3.1 says it should while the launcher listens).
