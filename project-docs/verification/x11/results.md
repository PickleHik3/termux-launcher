# Embedded X display — verification results

What `check-display.sh` has actually been run on, and what it reported. One row per device and
GPU profile. Add a row rather than editing one: an old row describes the build that produced it.

Nothing here is a benchmark of the launcher. `glmark2`'s numbers say what the device's own GPU
stack can do for an X client; the launcher's part is the row above them — that a client can open
the display, present frames and receive input at all.

| Date | Device | ABI | Profile | Renderer | glmark2 build/texture | Result |
|---|---|---|---|---|---|---|
| 2026-09-04 | emulator `tl_test`, Android 16 | x86_64 | software, page showing | `llvmpipe (LLVM 21.1.8)`, Mesa 26.0.6, OpenGL ES 3.2 | 7-10 / 7-13 FPS, score 6-10 | 6/6 pass |
| 2026-09-04 | emulator `tl_test`, Android 16 | x86_64 | software, page hidden | same | 81 / 116 FPS, score 97-103 | 6/6 pass |
| 2026-09-04 | emulator `tl_test`, Android 16 | x86_64 | software, no driver override | `zink Vulkan 1.4(llvmpipe)`, Mesa 26.0.6 | 6 / 10 FPS, score 7 | 5/5 pass |
| 2026-09-05 | emulator `tl_test`, Android 16 | x86_64 | none (`xeyes`, `xclock` only; `6054b0be`) | — | — | by eye: Turn on → Start display → clients on the page; three rotations keep the display attached with `Activities: 1`, `Death Recipients: 1`; `termux-x11-preference list` / `touchMode:2` and `launcherctl x11 gpu --env` answer from a shell |
| 2026-09-05 | **pong**, Nothing Phone 2 (A065), Adreno 730, Android 16 | arm64-v8a | software, page showing (`0b86a679`) | `llvmpipe (LLVM 21.1.8, 128 bits)`, Mesa 26.0.6, OpenGL ES 3.2 | 163 / 186 FPS, score 173 | 6/6 pass |
| 2026-09-05 | **pong**, as above | arm64-v8a | `turnip-zink`, page showing (`MESA_LOADER_DRIVER_OVERRIDE=zink TU_DEBUG=noconform`, `mesa-vulkan-icd-freedreno` from pacman's main repo, no separate loader) | `zink Vulkan 1.4(Turnip Adreno (TM) 725 (MESA_TURNIP))`, Mesa 26.0.6, OpenGL ES 3.2 | 91 / 91 FPS, score 90 | 6/6 pass |
| 2026-09-05 | **pong**, as above | arm64-v8a | phoc 0.4x from a Debian trixie proot (`proot-distro install debian:trixie`, `--shared-tmp`), `WLR_BACKEND=x11 WLR_X11_OUTPUTS=1 WLR_NO_HARDWARE_CURSORS=1 WLR_RENDERER=pixman` | pixman | — | compositor up: "Running compositor on wayland display 'wayland-0'", output `X11-1` added, ran until its 40 s timeout; `Failed to open DRI3` logged and harmless |
| 2026-09-05 | **pong**, as above | arm64-v8a | same, `WLR_RENDERER=vulkan` with Debian's `mesa-vulkan-drivers` | — | — | **fails as documented**: `Cannot create Vulkan renderer: no DRM FD available` (no DRI3), and the proot's Vulkan sees only `llvmpipe` — Debian's Turnip drives DRM/msm, not KGSL |

An emulator has no GPU: its host GL is swiftshader/swangle and its guest GL is llvmpipe, so single
digits are the expected floor and say nothing about a phone. The rows exist to prove the path, not
the speed.

**Run it with the Display page showing.** Those first two rows are the same build and the same
profile, ten times apart: with the page hidden the server has no surface to present to, the
client runs free, and the number measures the client alone. The script says which of the two you
got, by counting the server's shared-buffer hand-offs across the run — a grep would not do, since
`logcat -c` does not reliably clear while a reader is attached.

## What each run also showed by eye

Screenshots, not assertions — the script cannot see these, and they are the reason to look:

- `xeyes` and `xclock -digital` render inside the wall's Display page, with the pane's rounded
  corner and rim around them.
- The eyes track a finger dragged across the page, and the clock ticks: input reaches X and the
  page repaints without anything in the launcher driving it.
- `glmark2-es2 --benchmark "shading:shading=phong" --run-forever` shows its shaded model live on
  the page, so an EGL context on the X display presents through the same surface.

## The phone

The two pong rows are the first real device: the arm64 `libXlorie.so` loads, the server hands
`AHardwareBuffer`s to the page (step 4 counts them, and it passed), and a GPU client — Zink over
Turnip on the Adreno — presents through the display. The Zink row's flat 91 FPS on both scenes
against llvmpipe's 163–186 says the GPU path is paced by presentation, not by drawing; the
software row is the CPU running free. `launcherctl x11 gpu` on the phone named the GPU (from the
GL renderer string) and the `turnip-zink` profile before any package was installed.

Memory, `dumpsys meminfo com.termux` TOTAL PSS on pong, a live terminal session open throughout:
193 MB with no display; 189 MB with the server and `xeyes` up and the terminal showing; 196 MB
with the Display place showing; 184 MB and then 201 MB with the page hidden again, ten seconds
apart. The spread between readings of the same state (±10 MB, the terminal's own churn) is wider
than any difference between states: the server is its own process, and the launcher's cost for
the display is its surface while shown, which does not register against the noise.

## Phosh

phoc — Phosh's compositor — runs on the display as an X11 client with the pixman renderer, from
a stock Debian proot with nothing patched: that is the software path the plan asked to prove
first. The GPU path needs exactly what phosh-termux-gpu documents: a wlroots patched to render
with Vulkan into a `VkImage` and copy it into an XShm buffer (Xlorie has no DRI3 to hand a DRM FD
over), and a Turnip built for the Android kernel driver (`-Dfreedreno-kmds=kgsl`); a distro's
Turnip drives DRM/msm and falls back to llvmpipe. Neither is vendored here. `phosh` itself was
not started: it wants a logind session and D-Bus the proot does not have, and the compositor was
the question.

## Owed

- **Feel.** Nothing above says how a desktop feels to use on the phone.
- **The GPU profiles that need hardware**: `turnip-zink` (Adreno), `virgl`, `virgl-angle` and
  `vulkan-wrapper`. `GL_PROFILE=…` on the script takes the env from the profile table in
  `PLAN-embedded-x11.md`, so each is one run once the packages are installed.
- **Phosh in an Arch proot** — `phoc` with `WLR_BACKEND=x11`, pixman first and then Vulkan on a
  KGSL-built Turnip. Nothing in the launcher blocks it; it is a packages-in-the-proot exercise.

## Notes worth keeping

- Without `MESA_LOADER_DRIVER_OVERRIDE=llvmpipe`, Mesa 26 picks zink-over-lavapipe even when
  `LIBGL_ALWAYS_SOFTWARE=1` and `GALLIUM_DRIVER=llvmpipe` are both set. Any profile that means to
  pin a driver has to name all three, which is what the plan's table already does.
- `xkeyboard-config` is not optional: without its data the server exits before it opens a port.
  The launcher's `termux-x11` points `XKB_CONFIG_ROOT` at the edition's own prefix, because
  upstream only knows how to find it under `com.termux`.
- Termux's package is `xeyes`, not `xorg-xeyes`; `xclock` is `xorg-xclock`. `x11-repo` has to be
  installed *and* `apt update` run before either resolves.
- Taking the server's log pipe makes Android ask the user for access to all device logs, so the
  launcher only asks for it when the launcher's own log level is already verbose. A home screen
  must not put that dialog in front of someone who merely started a display.
