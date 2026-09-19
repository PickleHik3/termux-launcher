# Distro apps in the drawer — spec

Agreed with the user on a review page, 2026-09-19. Decisions here are settled; raise, do not
silently change. Device facts come from pong (Nothing A065, Android 16, `com.termux`,
proot-distro 5.8.0, Debian 13 trixie) and are recorded in
`project-docs/proot-gui-apps-research.md`. The end-user guide is `docs/en/Linux_Apps_From_A_Distro.md`.

## Goal

A GUI app installed inside a proot container appears in the app drawer with its own name and
icon, and a tap opens it on the Display — exactly as a Linux app installed in `$PREFIX` already
does. Verified working on the device through hand-written wrapper files before any code was
written: two tiles, tapped, up in about two seconds.

## What is already there

`LinuxAppCatalog` parses `.desktop` files, `LinuxAppIcons` does a cut-down hicolor lookup, the
entries reach the drawer under the reserved package `x11:linux` (`X11Apps.PACKAGE`), and
`X11LinuxAppRunner` starts the display and runs them. Both scan roots are already parameters —
`scan(List<File>)`, `find(icon, prefix)` — so only `LinuxAppCatalog.applicationDirs()` and
`LinuxAppIcons.prefix()` hard-code `$PREFIX`. `X11WindowIconResolver` calls both and must learn
the same thing.

## Device facts the build depends on

- Rootfs is `$PREFIX/var/lib/proot-distro/containers/<name>/rootfs`. **5.x only** (D10); the
  pre-5.x `installed-rootfs/<name>` layout is not scanned.
- `--shared-x11` is **required** — the tool's own `--help` claims it is on by default and it is
  not. Without it the container has no `/tmp/.X11-unix`.
- `-ac` on the server is **not** required, and the container needs no `xauth`.
- `DISPLAY` must be passed with `-e`; no host environment crosses. `PULSE_SERVER` is set free.
- An absolute `Icon=` path into the rootfs works as-is. PNG only, today.
- **Electron needs `--no-sandbox` whatever user it runs as.** A non-root user does not fix it and
  makes the failure silent (`Trace/breakpoint trap`).

## Decisions

| # | Decision |
|---|---|
| D1 | The launcher reads the containers itself — no wrapper files, no command for the user to run. Plus a new **Linux apps** drawer category. |
| D2 | The tile is called what the app calls itself: `Typora`, not `Typora (Debian)`. The container is shown elsewhere, not in the name. |
| D3 | Log in as the container's first non-root user when it has one. |
| D4 | Live. Install something in the container and it is in the drawer, the way a prefix app is. |
| D5 | `Terminal=true` entries are **shown**, and open in a terminal pane rather than on the display. This changes prefix apps too, which skip them today. |
| D6 | The launcher learns to load SVG icons. Costs the APK's first image dependency (AndroidSVG, ~100 KB). |
| D7 | First launch runs the command plain; if it dies at once, retry with `--no-sandbox` and remember that for the app. |
| D8 | Hiding is **per app**, not per container. There is no hide today, so build one: a list in Settings → Display of every drawer app, with a checkbox per app. |
| D9 | The setup flow lives in the **Display place**, offered when it finds a container that is not set up. It installs a distro if there is none, creates a non-root user and remembers it, installs the fonts GUI apps need, installs the distro's Mesa for the GPU profile, and offers a starter set of apps. |
| D10 | proot-distro 5.x only. |

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| 1 | `feat/distro-catalogue` | `LinuxAppCatalog` and `LinuxAppIcons` read containers: container-qualified ids, rootfs icon roots, the `proot-distro login` Exec wrapper, `X11WindowIconResolver` updated with them. Ends with a contract. | — |
| 2 | `feat/distro-runner` | D7's first-launch retry and its remembered state; D3's non-root user resolution from the container's `/etc/passwd`. | 1 |
| 3 | `feat/terminal-entries` | D5: `Terminal=true` entries shown and routed to a terminal pane, for prefix and container apps alike. | 1 |
| 4 | `feat/svg-icons` | D6: AndroidSVG, off-main-thread decode, hicolor `scalable` in the lookup order. | — |
| 5 | `feat/linux-apps-category` | D1's **Linux apps** category: the enum entry, its label, and the classifier rule that puts every `x11:linux` entry in it. | — |
| 6 | `feat/app-visibility` | D8: a per-app hide, stored, honoured by the drawer, edited from a list in Settings → Display. | 5 |
| 7 | `feat/distro-setup` | D9's setup flow in the Display place. | 1, 2 |
| 8 | `feat/distro-help` | X-3: the Help centre topic, and the docs updated to describe the shipped screens rather than the hand-written file. | 6, 7 |

Phases 1, 4 and 5 are independent and go in parallel. 2 and 3 wait on 1's contract. 6 waits on 5.
7 waits on 2. 8 is last because its copy describes what the others built.

## Gates

Per `AGENTS.md`. Every phase: the unit suites green on the merged state. Phases 1–3 and 7 also
need a device check on pong — a distro app tapped from the drawer and opening — because none of
this can be exercised on an emulator or in Waydroid (the container is aarch64 and the display is
the launcher's own). Phase 6's gate is that hiding an app removes it from the drawer and survives
a restart.

## Out of scope

- proot-distro 4.x (D10).
- GPU acceleration inside a container. The launcher exports its GPU profile before running a
  drawer app; whether that helps or hurts inside a proot is untested, and Zutty's log showed Mesa
  falling back to software. Measure it before deciding anything.
- Passing files to a container app (`%f`, `%U` field codes stay stripped).
- D-Bus. Apps that print `Failed to connect to the bus` still run; anything that genuinely needs a
  session bus is the user's `dbus-run-session`.

## Side queue (the user's)

- Whether the demo tiles left on pong (`$PREFIX/local/share/applications/debian-*.desktop`) come
  out when phase 1 lands — they would be duplicates of what the launcher then finds by itself.
