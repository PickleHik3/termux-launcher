# Desktop sessions in the drawer — spec

Install `xfce4` or `lxqt` and nothing appears in the app drawer. This makes a whole desktop
launchable from the drawer, on the in-app display, from the Termux X11 repo and from a proot
distro alike.

Research and sourcing: `RESEARCH.md` beside this file. Read it before touching anything here —
every fact below comes from it.

## The one-line reason it does not work today

`LinuxAppCatalog` scans `share/applications`. Desktops put their session file in
`share/xsessions`, which nothing has ever read. The `Type` value is **not** the blocker: eleven of
the nineteen session files in Termux's x11 repository say `Type=Application`, exactly like an app.

## Decisions (user, 2026-09-22, review page JaEkNTJLwwZo2H8KA8aRW6)

| | Decision | Taken |
|---|---|---|
| D1 | openbox stands down when a desktop starts; the desktop brings its own window manager | **b** |
| D2 | A second desktop offers to stop the one that is running | **a** |
| D3 | Desktops get their own drawer group, separate from Linux apps | **a** |
| D4 | Wayland sessions are out of scope — none can run on our X11 display | **a** |
| D5 | A desktop tile borrows the desktop's own icon | **b** |
| D6 | `dbus` is added to the Setup GUI Apps command | **a** |
| D7 | Entries whose command is not installed are hidden | **a** |

### One thing D6 does not finish

Installing `dbus` puts the package there; it does not start a bus. `startxfce4` starts none on the
X11 path (its `--wayland` branch is the only one that does), so XFCE without a bus does not come
up at all — which is why termux-x11's own README says
`dbus-launch --exit-with-session xfce4-session`.

So D6a is implemented as taken **and** the session script starts a bus for the desktops that are
known not to start one. That is not D6b chosen instead: it is what makes D6a have any effect.
LXQt and MATE start their own and must not be given a second, so the wrapper is an allowlist
(XFCE today), never a blanket.

## Contract

Everything downstream keys off two new facts. They are the whole interface between the phases.

```java
// LinuxAppCatalog.Root — a scanned directory now knows what kind it is.
public static final class Root {
    @NonNull public final File dir;
    @NonNull public final ProotDistro.Container container;
    /** This directory holds whole-desktop sessions (share/xsessions), not applications. */
    public final boolean sessions;
}

// LinuxAppCatalog.LinuxApp — one new final field, beside `terminal` (D5 of the distro round).
/** A whole desktop environment rather than an app: one tap takes the display for as long as it runs. */
public final boolean session;
```

Ids: a session's id carries a marker **inside** the desktop-file part, so `X11Apps.qualify`,
`containerOf` and every existing pin, ranking and folder are untouched:

```java
X11Apps.SESSION_PREFIX            // "session:"
X11Apps.qualifySession(container, desktopFile)   // qualify(container, "session:" + desktopFile)
X11Apps.isSessionId(id)                          // desktopFileOf(id).startsWith(SESSION_PREFIX)
X11Apps.desktopFileNameOf(id)                    // desktopFileOf(id) without the marker
```

Without this, `xfce.desktop` in `applications` and `xfce.desktop` in `xsessions` collide and
`scan`'s `seen` set silently drops the second (`LinuxAppCatalog.java:176`).

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| P1 | `feat/de-sessions-catalog` | Session directories scanned, parsed and catalogued; the contract above | — |
| P2 | `feat/de-sessions-runner` | Starting a desktop: env, the bus, openbox standing down (D1), one at a time (D2), no sandbox retry | P1 |
| P3 | `feat/de-sessions-drawer` | The "Desktops" group (D3) and the tile's icon (D5) | P1 |
| P4 | `feat/de-sessions-setup` | `dbus` in the Setup GUI Apps command (D6) and the documentation | — |

P4 runs alongside P1. P2 and P3 run in parallel once P1 is merged. Merging and the device gate
stay with the orchestrating session.

### P1 — the catalogue

- `ProotDistro.Container.sessionDirs()` beside `applicationDirs()`
  (`ProotDistro.java:123-144`): prefix → `share/xsessions`, `local/share/xsessions`; container →
  `usr/share/xsessions`, `usr/local/share/xsessions`; nix → `share/xsessions` under the profile.
  `applicationDirs()`'s contract stays true rather than growing a parameter.
- `Root` gains `sessions`; `roots()` and `rootsOf()` build both kinds; `signature()` picks the new
  directories up unchanged.
- `parse` takes the root's kind. In a session root, `Type=XSession` and a missing `Type` are both
  accepted as well as `Type=Application`; `DesktopNames` is read and carried (the session's
  `XDG_CURRENT_DESKTOP`).
- **D7**: in a session root, the `TryExec` test stays *and* the first word of `Exec` must resolve
  too — a resolving `TryExec` is not proof the `Exec` will run (cinnamon). Application roots keep
  today's rule exactly; nothing that shows today may stop showing.
- No Wayland (**D4**): `share/wayland-sessions` is not scanned and not mentioned.

### P2 — starting a desktop

- A session's script (`X11LinuxAppRunner.script`, `:270-280`) adds `XDG_RUNTIME_DIR` (created
  first), `XDG_SESSION_TYPE=x11`, and `XDG_CURRENT_DESKTOP` from `DesktopNames` when the file
  carried one.
- **D1**: before the session execs, our openbox is stopped — and only ours. It is identified by the
  configuration file the launcher gave it (`X11CliInstaller.OPENBOX_RC_PATH`), so a user's own
  openbox is never touched. When the session exits, the window manager is started again from
  `X11WindowManager.command(...)`.
- The bus: `dbus-launch --exit-with-session` for the desktops known to start none, and only when
  `DBUS_SESSION_BUS_ADDRESS` is unset and `dbus-launch` is installed.
- **D2**: while a session runs, tapping another asks before taking over, then stops the first.
- The quick-fail retry (`:42`, `:221-236`) never fires for a session: a desktop refused because
  something already holds the display exits non-zero inside the window and would otherwise be
  retried with `--no-sandbox`, a flag no desktop has heard of.

### P3 — the drawer

- **D3**: a `DESKTOPS` category beside `LINUX_APPS` (`AppDrawerCategory.java:24`,
  `AppDrawerCategoryClassifier.java:197`, `LauncherCategorySortPrompt.java`).
- **D5**: the tile borrows the desktop's own icon. Most session files name none —
  ten of nineteen have an `Icon` key and four of those are empty — so a session with no usable
  icon falls back to the desktop's own application icon by name before the generic path.

### P4 — setup and documentation

- **D6**: `dbus` joins the package list the Setup GUI Apps screen builds, on both routes
  (`GuiAppsSetup.x11Command`, `insideScript`).
- `docs/en/X11_Display.md:242-247` (the window-manager paragraph, which today promises openbox
  arranges everything) and `:256-279` ("A desktop in a proot", which today tells the user to type
  `pacman -S xfce4 && startxfce4` themselves); `docs/en/Linux_Apps_From_A_Distro.md`.

## Gates

Every phase: `./gradlew :app:testDebugUnitTest` green (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk`).
The catalogue and the script composition are pure and fixture-tested — that is where the coverage
goes, not into mocks of the display.

Device: nothing here has been run on a phone. Before this ships, one Waydroid or pong pass has to
show XFCE actually coming up, because four things are reasoned and not observed (see
`RESEARCH.md`, "Unverified"): whether XFCE survives xfwm4 refusing, what MATE and i3 do when a
window manager is already running, the Debian and Arch session file bodies, and cinnamon.
