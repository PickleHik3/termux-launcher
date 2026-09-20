# proot GUI apps in the drawer and on the Display — research

**Date:** 2026-09-19 · **Branch:** `dev` · Nothing implemented; nothing here is a decision.

**Question.** How can the user experience be streamlined for (a) adding GUI apps installed inside
a proot-distro Linux distribution to the launcher's app catalogue, and (b) launching them in the
in-app Display place?

## Executive summary

1. The launcher **already does the whole job for `$PREFIX`**: `LinuxAppCatalog` parses `.desktop`
   files, `LinuxAppIcons` does a cut-down hicolor lookup, the entries land in the app drawer under
   the reserved package `x11:linux`, and `X11LinuxAppRunner` starts the display and runs them.
   What is missing is *only* the distro dimension — the scan roots, the icon root, an id that says
   which container an app came from, and a `proot-distro login` wrapper around `Exec=`.
2. Both scan and icon code are **already parameterised by directory** (`scan(List<File>)`,
   `find(icon, prefix)`), so the work is much smaller than it looks. `applicationDirs()` and
   `LinuxAppIcons.prefix()` are the only hard-coded roots.
3. **proot-distro 5.x moved the rootfs.** The path is
   `$PREFIX/var/lib/proot-distro/containers/<name>/rootfs`, not `installed-rootfs/<name>` —
   the old path is legacy and is migrated on the container's *next login*. This repo's own
   `x11-gpu-setup.sh` still hard-codes the legacy path.
4. **proot-distro 5.x has `--shared-x11`**, which binds only `$PREFIX/tmp/.X11-unix` into the
   container — exactly the directory the launcher's display server opens its socket in. It is
   narrower and safer than the `--shared-tmp` this repo's docs recommend, and it is what a
   generated launch line should use.
5. Everything the rootfs holds sits inside the launcher's *own* app data directory, so reading it
   needs no root and no permission — but absolute symlinks and absolute `Icon=` paths inside the
   rootfs are container-absolute and must be re-rooted before Java touches them.
6. Three failure modes have no upstream answer and must be designed for: no session D-Bus in a
   proot, `Terminal=true` entries, and apps that fork away from their launcher.

---

## 1. What this repo already has (verified)

### 1.1 The Linux app catalogue

`LinuxAppCatalog` reads `.desktop` files the way a desktop menu does.

- Scan roots today: `$PREFIX/share/applications` and `$PREFIX/local/share/applications` —
  `app/src/main/java/com/termux/app/x11/LinuxAppCatalog.java:63-69`. `TERMUX_PREFIX_DIR_PATH` is
  `/data/data/<applicationId>/files/usr`.
- `scan(List<File> dirs)` takes the dirs as an argument and de-duplicates by desktop-file id
  (`LinuxAppCatalog.java:73-90`). **It is already distro-agnostic.**
- `parse()` reads only the `[Desktop Entry]` group and keeps `Type, Name, Exec, TryExec, Icon,
  Comment, StartupWMClass, NoDisplay, Hidden, Terminal` (`LinuxAppCatalog.java:112-151`).
- Filter today: drop unless `Type=Application`, and drop when `NoDisplay`, `Hidden` or
  `Terminal` is true, or `TryExec` names a binary that is not executable
  (`LinuxAppCatalog.java:147-158`).
- `stripFieldCodes()` removes `%f %F %u %U %d %D %n %N %i %c %k %v %m` and unescapes `%%`
  (`LinuxAppCatalog.java:165-182`).
- `signature(dirs)` is a cheap mtime/count fingerprint used to decide whether to re-list
  (`LinuxAppCatalog.java:96-104`), driven from `TermuxActivity.refreshLinuxApps`
  (`app/src/main/java/com/termux/app/TermuxActivity.java:14874-14883`).

### 1.2 Icons

`LinuxAppIcons.find(iconName, prefix)` — `app/src/main/java/com/termux/app/x11/LinuxAppIcons.java:378-391`:

- absolute `Icon=` → used as-is, PNG only;
- otherwise `prefix/share/icons/hicolor/<size>/apps/<name>.png` over
  `256x256, 192x192, 128x128, 96x96, 72x72, 64x64, 48x48, 32x32, scalable`, then
  `prefix/share/pixmaps/<name>.png`;
- SVG and XPM are not rendered; decode is capped at 192 px (`LinuxAppIcons.java:365, :399-410`).
- `prefix()` is the one hard-coded root (`LinuxAppIcons.java:414-416`). **`find()` itself is
  already parameterised.**

### 1.3 How an entry reaches the drawer

- `X11Apps.PACKAGE = "x11:linux"`; an entry is `AppRef("x11:linux", <desktop file id>)`
  (`app/src/main/java/com/termux/app/x11/X11Apps.java:433-450`). Ranking, pinning, folders and
  search all work off `stableId`, so nothing else had to change.
- `LauncherAppDataProvider.addLinuxApps()` adds them when the build has the server, the display
  is enabled and the "Linux apps in the drawer" preference is on
  (`app/src/main/java/com/termux/app/launcher/data/LauncherAppDataProvider.java:106-129`).
  Preference key `x11_drawer_apps`, default true
  (`termux-shared/.../TermuxPreferenceConstants.java:248-250`).
- Icon supply: `LauncherAppDataProvider.linuxAppIcon()` re-scans and falls back to
  `ic_symbol_terminal` (`LauncherAppDataProvider.java:82-100`).
- Tap: `LauncherAppLauncher.launchEntry()` short-circuits on `X11Apps.isLinuxApp` and hands the
  entry to the registered runner (`app/src/main/java/com/termux/app/launcher/LauncherAppLauncher.java:62-66`).
- The hook is installed in `TermuxActivity.installLinuxAppRunner()`
  (`TermuxActivity.java:14886-14930`).
- The same route serves `launcherctl launch <query>` (`docs/en/LauncherCtl_API.md:122-127`).

### 1.4 How an app is run on the Display

`X11LinuxAppRunner` (`app/src/main/java/com/termux/app/x11/X11LinuxAppRunner.java`):

- `run(app)`: turn the display on if disabled, show the Display place, run now if the server is
  up, otherwise start it and wait (15 s timeout) for `onDisplayRunningChanged`
  (`X11LinuxAppRunner.java:271-296`).
- `script(app, display, env)` composes the shell text (`X11LinuxAppRunner.java:330-339`):

  ```sh
  export DISPLAY=<:N>
  export MOZ_USE_XINPUT2=1
  <GPU profile exports>
  cd "$HOME"
  exec <Exec with field codes stripped>
  ```

- It is run as a background task: `TermuxService.createTermuxTask($PREFIX/bin/bash, ["-c", script],
  null, $HOME)` (`TermuxActivity.java:14894-14898`). The task inherits `TermuxShellEnvironment`,
  which exports `PREFIX`, `HOME`, `TMPDIR=$PREFIX/tmp`, `PATH=$PREFIX/bin` and the
  `TERMUX_APP__*` block including `TERMUX_APP__PACKAGE_NAME`
  (`termux-shared/.../TermuxShellEnvironment.java:95-134`;
  `termux-shared/.../TermuxAppShellEnvironment.java:48, :132`).
- `X11DisplayHostController.displayName()` reads the live socket from
  `$PREFIX/tmp/.X11-unix/X<N>`, defaulting to `:0`
  (`app/src/main/java/com/termux/app/x11/X11DisplayHostController.java:350-359`).
- Default display command is `termux-x11 :0`
  (`termux-shared/.../TermuxPreferenceConstants.java:204`); flags are appended by
  `X11StartCommand.argv()` (`app/src/main/java/com/termux/app/x11/X11StartCommand.java:485-504`);
  the window manager is passed as `-xstartup` (`X11WindowManager.xstartup`,
  `app/src/main/java/com/termux/app/x11/X11WindowManager.java:28-36`).
- Window chips get their icon by matching `WM_CLASS` back to a desktop file — also via
  `LinuxAppCatalog.scan(applicationDirs())` and `LinuxAppIcons.prefix()`
  (`app/src/main/java/com/termux/app/x11/X11WindowIconResolver.java:117-120`). **This is a second
  call site that must learn about containers, or proot windows will wear no icon.**

### 1.5 Prior art in the docs

- `docs/en/X11_Display.md:249-263` already documents the manual proot recipe
  (`proot-distro login archlinux --shared-tmp -e DISPLAY=:0`) and the GPU story inside a proot.
  `docs/en/X11_Display.md:229-248` documents the drawer feature for `$PREFIX` apps.
- `project-docs/plans/pane-wall-x11-study.md:152-157` records the decision that "Linux apps are
  drawer entries, and the display starts on demand for them" — the design this note extends.
- `app/src/main/assets/x11/x11-gpu-setup.sh:54` uses
  `$PREFIX/var/lib/proot-distro/installed-rootfs/debian` and
  `:320` runs `proot-distro login debian --shared-tmp -- "$@"`. **The path is the pre-5.x one
  (see §3.1) — worth fixing whatever else is decided.**
- No existing doc covers discovery of proot apps. `project-docs/display-fullscreen/PARKED.md` and
  `project-docs/display-tablet/SPEC.md` are about the Display place's chrome and input, not about
  the catalogue.

---

## 2. The freedesktop rules (verified against the specs)

Sources: Desktop Entry Specification (current URL
<https://specifications.freedesktop.org/desktop-entry/latest/>), Icon Theme Specification
<https://specifications.freedesktop.org/icon-theme-spec/latest/>, Base Directory Specification
<https://specifications.freedesktop.org/basedir-spec/latest/>.

### 2.1 Where entries live

- `XDG_DATA_DIRS` defaults to `/usr/local/share/:/usr/share/`; `XDG_DATA_HOME` defaults to
  `$HOME/.local/share` and outranks them (basedir spec, §3). Applications are in the
  `applications/` subdirectory of each.
- Desktop file ID = path relative to the `XDG_DATA_DIRS` component, minus the `applications/`
  prefix, with `/` turned into `-`; first match in `XDG_DATA_DIRS` order wins
  (desktop-entry spec, "File naming" §2.1). **Entries can be in sub-directories** — the current
  `listFiles` scan is flat, so `applications/foo/bar.desktop` is missed.

### 2.2 Parsing

- UTF-8, LF-separated, case significant; `#` and blank lines are comments; groups are `[name]`;
  entries are `Key=Value` with space around `=` ignored; key characters are `A-Za-z0-9-`
  (spec §3.1–3.3).
- `string`, `localestring` and `iconstring` values support the escapes `\s \n \t \r \\`
  (spec §4). **The current parser does not unescape these** (`LinuxAppCatalog.java:129`).
- Localised keys are `Name[lang_COUNTRY@MODIFIER]`; matching order is
  `lang_COUNTRY@MODIFIER → lang_COUNTRY → lang@MODIFIER → lang → unlocalised` (spec §5).
  The current parser only ever reads the unlocalised key — correct but never localised.
- `booleans` must be literally `true` or `false` (spec §4). The current parser accepts any case.

### 2.3 Which entries to show

| Key | What the spec says | What to do |
|---|---|---|
| `Type` | Three types; implementations *should ignore unknown types* | keep `Application` only |
| `NoDisplay` | "this application exists, but don't display it in the menus" | hide |
| `Hidden` | "should have been called Deleted… strictly equivalent to the file not existing" | hide |
| `OnlyShowIn` / `NotShowIn` | matched against the colon-separated `$XDG_CURRENT_DESKTOP`; default is *shown* unless `OnlyShowIn` is present, in which case the default is *not shown* | see below |
| `TryExec` | "path to an executable… if the file is not present or not executable, the entry **may** be ignored" | keep as a filter, but resolve inside the rootfs |
| `Terminal` | only "whether the program runs in a terminal window" — the spec does **not** say hide | a product decision, not a spec rule |
| `DBusActivatable` | if true, implementations *should ignore `Exec`* and send a D-Bus message | see §5 |
| `Path` | working directory for the program | should be honoured; currently ignored (`cd "$HOME"`) |

`OnlyShowIn`/`NotShowIn` is the one that changes behaviour most: with no `XDG_CURRENT_DESKTOP`
set, an entry carrying `OnlyShowIn=GNOME;` defaults to *not shown*, and a great many distro
entries carry one. The launcher sets no `XDG_CURRENT_DESKTOP` today (verified: no occurrence in
the repo), so implementing this key strictly would hide entries that are visible now.

### 2.4 `Exec` — the exact transformation

Spec, "The Exec key" (§7):

- A command line is a program plus arguments, separated by spaces. Arguments *may be quoted in
  whole*; quoting is **double quotes only**, with `"`, `` ` ``, `$` and `\` escaped by a preceding
  backslash. Reserved characters that force quoting: space, tab, newline, `"`, `'`, `\`, `>`,
  `<`, `~`, `|`, `&`, `;`, `$`, `*`, `?`, `#`, `(`, `)`, `` ` ``.
- **Implementations must undo quoting before expanding field codes and before passing the
  argument to the program.** The string escape rule (`\\` → `\`) is applied *before* the quoting
  rule, so a literal backslash in a quoted argument is four backslashes in the file and a literal
  `$` is `\\$`.
- Field codes are `%` + one alpha char; `%%` is a literal `%`. `%f %F %u %U` = files/URLs,
  `%i` = `--icon <Icon>` as *two* arguments (nothing if `Icon` is empty), `%c` = translated
  `Name`, `%k` = the desktop file's location, `%d %D %n %N %v %m` deprecated.
- "If the application should not open any file the `%f`, `%u`, `%F` and `%U` field codes **must be
  removed** from the command line and ignored." — which is what the launcher does.
- A command line with an unknown field code is **invalid and must not be processed**. The current
  `stripFieldCodes` keeps unknown codes verbatim instead of rejecting the entry.
- Field codes must not be used inside a quoted argument.

**Consequence for the launcher.** The spec's model is argv, not a shell line. The current code
strips field codes and hands the remaining text to `bash -c` (`X11LinuxAppRunner.java:337`), so
the desktop file's double-quote convention accidentally works, but `'`, `$`, `` ` ``, `\` and
`;` are then re-interpreted by bash — which the spec explicitly says must already have been
undone. The correct shape is: parse `Exec` into an argv list per the rules above, then re-quote
that argv for whatever shell you hand it to.

### 2.5 Icons

Icon Theme Spec:

- Base directories, in order: `$HOME/.icons`, `$XDG_DATA_DIRS/icons`, `/usr/share/pixmaps`
  (§3, "Directory Layout").
- Supported formats: **PNG, XPM, SVG**. "Support for SVGs is optional. Implementations that do not
  support SVGs should just ignore any `.svg` files" (§2, §3).
- There must always be a `hicolor` theme; "implementations are **required** to look in the
  `hicolor` theme if an icon was not found in the current theme" (§3, §5).
- Lookup order is: current theme → its parents → `hicolor` → unthemed fallback. Within a theme:
  exact size match first, then any size, then unthemed (§5).
- The recommended install location for an application icon is
  `$prefix/share/icons/hicolor/48x48/apps` with an optional SVG in `.../scalable/apps` (§7).
- Desktop-entry spec: an absolute `Icon=` value names the file directly; otherwise the Icon Theme
  algorithm is used ("Recognized keys", `Icon`).

**Practical shape for a distro rootfs:** `<rootfs>/usr/share/icons/hicolor/<size>/apps/<name>.png`
and `<rootfs>/usr/share/pixmaps/<name>.{png,xpm,svg}`. The launcher's PNG-only, hicolor-only
lookup is a legitimate simplification, but on a Debian/Arch rootfs the scalable-only SVG case is
common (GNOME apps ship SVG only), so the generic terminal mark would be shown for a visible
fraction of apps. *Reasoned, not measured.*

---

## 3. proot-distro (verified against the source, tag `v5.8.0`)

The version pinned by this tree's package recipe is 5.8.0
(`packages/vaj/termux-packages/packages/proot-distro/build.sh:5`). 5.x is a **Python package**,
not the old single shell script.

### 3.1 Where a container's filesystem is

- `RUNTIME_DIR = $TERMUX__PREFIX/var/lib/proot-distro` when running under Termux
  (<https://github.com/termux/proot-distro/blob/v5.8.0/proot_distro/constants.py> — `RUNTIME_DIR`,
  `CONTAINERS_DIR`, `LEGACY_ROOTFS_DIR`).
- `CONTAINERS_DIR = <RUNTIME_DIR>/containers`;
  `container_rootfs(name) = <CONTAINERS_DIR>/<name>/rootfs`;
  `container_manifest(name) = <CONTAINERS_DIR>/<name>/manifest.json`
  (<https://github.com/termux/proot-distro/blob/v5.8.0/proot_distro/paths.py> —
  `container_dir`, `container_rootfs`, `container_manifest`).
- `LEGACY_ROOTFS_DIR = <RUNTIME_DIR>/installed-rootfs` is "used only for migrating old
  installations" (`constants.py`), and the migration runs on the container's next `login`
  (<https://github.com/termux/proot-distro/blob/v5.8.0/proot_distro/commands/login/migrate.py> —
  `migrate_legacy_rootfs`).
- "Installed" means `containers/<name>/rootfs` exists as a directory (`paths.container_is_installed`).

So the concrete path from the Android side, for the launcher's own package, is:

```
/data/data/<applicationId>/files/usr/var/lib/proot-distro/containers/<distro>/rootfs/usr/share/applications
```

and for a container installed before 5.x and not yet logged into:

```
/data/data/<applicationId>/files/usr/var/lib/proot-distro/installed-rootfs/<distro>/usr/share/applications
```

**Readable without root:** yes — it is inside the launcher's own `/data/data/<pkg>/files` tree,
owned by the app's uid, and `proot-distro` sets `os.umask(0o022)` at import (`constants.py`), so
directories are world-traversable for the owner. *Verified by construction, not tested on device.*

**Prefix detection.** proot-distro takes `TERMUX__PREFIX` if set, else
`/data/data/${TERMUX_APP__PACKAGE_NAME:-com.termux}/files/usr` (`constants.py`). The launcher
exports `TERMUX_APP__PACKAGE_NAME` into every shell it starts
(`termux-shared/.../TermuxAppShellEnvironment.java:48, :132`), so the VAJ and Nix editions resolve
their own prefix correctly. A shell that does *not* carry that variable would look in
`com.termux` instead.

### 3.2 `login` — the real flags

Parser: <https://github.com/termux/proot-distro/blob/v5.8.0/proot_distro/parser.py>
(`_add_login_or_run_common`, `_login`). `login` (alias `sh`) takes
`CONTAINER [-- COMMAND ...]` and:

| Flag | Meaning (from `parser.py` / README) |
|---|---|
| `-u`, `--user NAME[:GROUP]` | resolved against the rootfs `/etc/passwd`; default `root` |
| `--isolated` / `--minimal` | mutually exclusive; bind nothing of the host / strip the environment |
| `--shared-home`, `--termux-home` | bind `$TERMUX__HOME` over the guest home |
| `--shared-tmp` | bind `$PREFIX/tmp` → `/tmp` |
| `--shared-x11` | bind `$PREFIX/tmp/.X11-unix` → `/tmp/.X11-unix` |
| `-b`, `--bind PATH[:PATH]` | repeatable |
| `-w`, `--work-dir PATH` | working directory |
| `-e`, `--env VAR=VALUE` | repeatable |
| `-d`, `--detach` | daemonise (double fork + `setsid`, stdio to `/dev/null`) |
| `--get-proot-cmd` | print the equivalent `proot` command instead of running it |
| `-P`, `--redirect-ports` / `--fix-low-ports` | port redirection |
| `--kernel`, `--hostname`, `--emulator` | cosmetic / qemu |

The two shared flags are applied only on Termux and only to non-termux-type containers:

```python
if IS_TERMUX and shared_tmp and dist_type != "termux":
    args.append(f"--bind={TERMUX_PREFIX}/tmp:/tmp")
if IS_TERMUX and shared_x11 and dist_type != "termux":
    args.append(f"--bind={TERMUX_PREFIX}/tmp/.X11-unix:/tmp/.X11-unix")
```

(<https://github.com/termux/proot-distro/blob/v5.8.0/proot_distro/commands/login/proot_cmd.py>,
`_add_non_minimal_binds`.)

**How a command after `--` is run.** `cli._split_separator` puts everything after the literal `--`
into `args.login_cmd`; `login` then builds
`inner = [login_shell, "-c", shlex.join(login_cmd)]` for a normal container
(<https://github.com/termux/proot-distro/blob/v5.8.0/proot_distro/commands/login/__init__.py>,
`_login_with_rootfs`). With no command it is `[login_shell, "-l"]`. Note: **a command is run by a
non-login shell**, so `/etc/profile` and the `profile.d` snippet are not sourced for it — the
environment comes straight from the exec instead.

### 3.3 How environment reaches the guest

`_build_normal_env` (same file) builds the child environment:

- `PATH = DEFAULT_PATH_ENV` (`constants.py`: the usual `/usr/local/sbin:…:/usr/games` plus
  `$TERMUX__PREFIX/bin:/system/bin:/system/xbin` on Termux);
- on Termux: `MOZ_FAKE_NO_SANDBOX=1` and **`PULSE_SERVER=127.0.0.1`** — set unconditionally in the
  default (non-`--minimal`) mode;
- the image manifest's own `Env` entries, filtered by `IMAGE_ENV_BLOCKED` and the `LD_*`/`PROOT_*`
  prefixes (`commands/login/env.py`);
- Android system vars (`ANDROID_ART_ROOT`, `BOOTCLASSPATH`, …) inherited from the host unless
  `--isolated`/`--minimal`;
- then **`--env` entries, which are applied last and therefore win**;
- `HOME`, `USER`, `TERM`, `COLORTERM`.

`inject_termux_profile()` additionally writes `<rootfs>/etc/profile.d/termux-profile.sh`
re-exporting these for login shells started later inside the container (`commands/login/env.py`).

So **`DISPLAY` reaches the app through `-e DISPLAY=:0`** — there is no other mechanism, and
nothing sets it by default. `XDG_RUNTIME_DIR` and `WAYLAND_DISPLAY` are likewise not set by
proot-distro; they would have to be `-e` flags too. `PULSE_SERVER` you get for free.

### 3.4 Enumerating containers

`proot-distro list --quiet` prints bare container names to stdout
(<https://github.com/termux/proot-distro/blob/v5.8.0/proot_distro/commands/list.py> — the module
header states "`--quiet` prints bare identifiers to stdout … container names"). The launcher can
equally just list `containers/*` and test for `rootfs/` — that is exactly what
`paths.container_is_installed` does, and it avoids spawning a process on every drawer refresh.

Related commands, all in v5.8.0: `proot-distro ps` (active sessions), `proot-distro kill`,
`proot-distro run` (uses the image's `Entrypoint`/`Cmd`; not useful for `.desktop` launching).

### 3.5 What proot-distro does *not* do

- It does not start a session D-Bus. Grepped the login/env/proot_cmd sources at v5.8.0: no `dbus`
  anywhere. The termux-x11 README's own recipe wraps the session in `dbus-launch`
  (<https://github.com/termux/termux-x11/blob/master/README.md>, "Running Graphical Applications").
- It refuses to run nested inside another proot (`cli._refuse_nested_proot`).
- It does not read or expose `.desktop` files in any way.

---

## 4. termux-x11 and the display socket (verified)

- The launcher owns the server: `x11-server/` is a vendored copy of termux-x11's `lorie` module,
  pinned at upstream `9df8b767…` (`x11-server/UPSTREAM.md:1-13`). The reason it is vendored rather
  than used as a second app is in `x11-server/UPSTREAM.md:19-23`.
- Clients connect over the ordinary X unix socket in `$TMPDIR/.X11-unix`. Upstream:
  "If you plan to use the program with proot… you need to launch proot/proot-distro with the
  `--shared-tmp` option. If passing this option is not possible, set the `TMPDIR` environment
  variable to point to the directory that corresponds to `/tmp` in the target container"
  (<https://github.com/termux/termux-x11/blob/master/README.md>, "Using with proot environment").
  Inside the container the recipe is `export DISPLAY=:1`.
- The launcher's `TMPDIR` is `$PREFIX/tmp` (`TermuxShellEnvironment.java:123`), and
  `displayName()` reads `$PREFIX/tmp/.X11-unix` (`X11DisplayHostController.java:350-359`). That is
  precisely the directory `--shared-x11` binds, so **`--shared-x11` is sufficient; `--shared-tmp`
  is not required** for a single GUI app. *Reasoned from the two sources above; not tested on a
  device.*
- There is no Android-side intent API for launching a client: the server is started by the
  `termux-x11` shell script (`app/src/main/java/com/termux/app/x11/X11CliInstaller.java:54, :226-254`),
  and clients are ordinary processes. Launching is therefore always "run a command line".
- **Unverified:** whether X access control lets a proot client in with the launcher's default
  `termux-x11 :0` (no `-ac`). This repo's own doc uses `termux-x11 :0 -ac` for the proot recipe
  (`docs/en/X11_Display.md:256`), while the drawer path for `$PREFIX` apps works without it. Same
  uid, same socket — it very likely works — but I did not read the server's access-control code
  and did not test it. **This needs a device check before any spec is written.**

---

## 5. Failure modes to design around

| Failure | Source | Note |
|---|---|---|
| **Distro not installed / removed** | `paths.container_is_installed` (v5.8.0) is `containers/<name>/rootfs` exists | the scan root simply disappears; entries must vanish from the drawer like `$PREFIX` ones do (`TermuxActivity.refreshLinuxApps`, `TermuxActivity.java:14874-14883`) |
| **App uninstalled inside the distro** | the `.desktop` file goes with the package; `TryExec` catches the rarer half-removal (desktop-entry spec, `TryExec`) | today the launcher toasts `termux_x11_app_gone` and re-lists (`TermuxActivity.java:14919-14923`) — same handling works |
| **`proot-distro` itself not installed** | it is a package, `TERMUX_PKG_DEPENDS="proot …"` (`packages/vaj/termux-packages/packages/proot-distro/build.sh:14`) | the whole feature has to no-op silently, not error |
| **Legacy rootfs path** | `LEGACY_ROOTFS_DIR`, `migrate_legacy_rootfs` (v5.8.0) | migration only happens at the container's next `login`, so both layouts can be live at once; scan both |
| **Display not running** | `X11LinuxAppRunner.run()` already starts it and waits 15 s (`X11LinuxAppRunner.java:276-287`) | a proot launch is slower to first window than a `$PREFIX` one (proot syscall interception + a cold container); the 15 s budget covers *display* start, not app start, so this is probably fine — *reasoned* |
| **App forks / daemonises** | `X11LinuxAppRunner.script` ends in `exec <cmd>` (`X11LinuxAppRunner.java:337`) | a forking app makes the `bash -c` task exit immediately; the window still appears. For proot, `proot-distro login -d` (double-fork + `setsid`, stdio to `/dev/null` — `commands/login/detach.py`) is the supported way to background a session, but it discards output, which kills diagnosis |
| **Apps that need D-Bus** | proot-distro starts none (§3.5); termux-x11 README's recipe is `dbus-launch --exit-with-session …` | GTK/GNOME apps mostly start without a session bus but lose features; some (anything `DBusActivatable`) may not start. Either wrap each launch in `dbus-launch`, or run one `dbus-daemon --session` per container and pass `DBUS_SESSION_BUS_ADDRESS` — a decision, not a fact |
| **`Terminal=true` entries** | spec says only "runs in a terminal window"; it is not a hide rule | these are real apps (`htop`, `vim`); the launcher has a terminal one pane away. Hiding them is today's behaviour (`LinuxAppCatalog.java:148`) and is a product decision to revisit |
| **`DBusActivatable=true`** | spec: implementations *should* ignore `Exec` and send a D-Bus message | with no session bus this cannot work; falling back to `Exec` (which the spec says must still be present "for compatibility") is the only option |
| **Absolute symlinks and absolute `Icon=` inside a rootfs** | rootfs content is container-absolute | `/usr/share/icons/...` read from Android resolves against Android's `/`, not the rootfs. Every path out of a `.desktop` file must be re-rooted at `<rootfs>`, and a symlink whose target starts with `/` must be re-rooted too before following. *Reasoned — this is how chroot-relative paths behave; not measured on a real rootfs* |
| **`Exec` quoting re-interpreted by bash** | desktop-entry spec §7: quoting "must be undone… before passing the argument to the executable program" | see §2.4; going through `bash -c` twice (host shell, then the container's `sh -c` built by proot-distro) compounds it |
| **Name collisions across distros** | desktop file IDs are only unique within a data-dir set | `firefox.desktop` in `$PREFIX`, in `debian` and in `archlinux` are three apps; `AppRef` ids and `WM_CLASS` matching both need the container in the key |
| **`WM_CLASS` ambiguity on the Display** | `X11WindowIconResolver.match` (`X11WindowIconResolver.java:139-155`) matches on desktop-file name / `StartupWMClass` / `Exec` basename | with several distros, two entries can claim the same class; the resolver needs a tiebreak or it will show the wrong icon |
| **Scan cost** | `LinuxAppCatalog.scan` re-reads every file, and `LauncherAppDataProvider.linuxAppIcon(ref)` re-scans **per icon** (`LauncherAppDataProvider.java:84-89`) | a full Debian `/usr/share/applications` is a few hundred files; multiplied by that per-icon re-scan this is the one real performance risk |

---

## 6. Recommendation (decision-shaped)

The technical distance is small: **`scan()` and `find()` already take their roots as arguments.**
What a spec must decide is the *product* shape. Three candidates.

### A. Live scan, distros treated like the prefix
Extend `applicationDirs()` to return, for each installed container, `<rootfs>/usr/share/applications`,
`<rootfs>/usr/local/share/applications` and `<rootfs>/root/.local/share/applications`;
extend `signature()` over the same dirs; namespace the id (`AppRef("x11:linux", "debian/gimp")`);
wrap `Exec` in `proot-distro login <distro> --shared-x11 -e DISPLAY=<:N> -- <argv>`.

- **For:** zero new UI, zero new state, matches the existing mental model exactly ("install it,
  it's in the drawer"), and the existing "app gone" handling keeps working.
- **Against:** a fresh Debian dumps ~100 entries into the drawer at once, with no user consent and
  no way to say "just GIMP"; scan cost grows with every container; a container the user keeps for
  headless work still contributes tiles.

### B. One-shot "Add apps from a distro" import
A picker: choose a container → see its apps with icons → tick the ones you want → they are stored
as catalogue entries (like pins) and never re-scanned except on an explicit refresh.

- **For:** the drawer stays the user's; scan cost paid once; entries can be renamed/re-iconed; a
  stored entry can record the exact launch line, so a hand-tuned one survives.
- **Against:** new persistent state and a new screen; an app the user installs later in the distro
  does not appear until they import again; stored entries go stale when the distro changes.

### C. Hybrid — auto-discover, opt in per distro
Containers are discovered automatically, but a container contributes to the drawer only after the
user turns it on (one row per container on the Display settings page, beside the existing
"Linux apps in the drawer" switch). Within an enabled container, the scan is live as in A.

- **For:** one tap to get everything from a distro, one tap to get nothing; no per-app state to go
  stale; the cost is bounded by what the user asked for.
- **Against:** still all-or-nothing per container; a user who wants two apps out of a hundred is
  not served.

**Reading of the evidence:** C is the smallest change that respects the existing design
(`pane-wall-x11-study.md:152-157` already settled that Linux apps are drawer entries and that the
display starts on demand), and it is the only one of the three that does not add either an
unasked-for flood of tiles or a new store of stale entries. B's picker is the right *second* step
if per-app control turns out to matter.

### Open questions a human must decide

1. **`OnlyShowIn`.** Implement it strictly (and hide entries visible today), ignore it as now, or
   set `XDG_CURRENT_DESKTOP` to something and match against it? Which value?
2. **`Terminal=true`.** Keep hiding them, or route them to a terminal pane instead of the Display?
   The launcher is the one place where "open it in a terminal" is a real option.
3. **D-Bus.** Wrap every launch in `dbus-launch --exit-with-session`, run one session bus per
   container, or ship nothing and accept degraded apps?
4. **`--shared-x11` vs `--shared-tmp`.** `--shared-x11` is narrower and enough for the socket.
   Does anything the launcher wants (a second app talking to the first, `VTEST_SOCKET_NAME` for
   the virgl GPU profiles — `docs/en/X11_Display.md:260-263`) need the whole `/tmp` shared?
5. **Which user.** `--user root` is proot-distro's default. Fine for a phone, but it is a choice.
6. **GPU environment inside the container.** The host's profile exports
   (`X11LinuxAppRunner.installedEnv`) name *Termux's* Mesa; inside a proot the distro's own Mesa is
   what loads. Passing the host's `MESA_*` values through `-e` may help or may break. Needs a
   device test before it is specified.
7. **Icons: SVG.** A rootfs-wide hicolor scan will find SVG-only apps. Render SVG, or accept the
   generic mark for them?
8. **Access control.** Does a proot client reach `termux-x11 :0` without `-ac`? (§4, unverified.)

---

## What I could not verify

- **Whether X access control admits a proot client** with the launcher's default `termux-x11 :0`
  (no `-ac`). The repo's doc uses `-ac` for the proot recipe but not for prefix apps. Not read in
  the server source, not tested.
- **Real rootfs layout on a device** — no proot container was available to inspect from this
  session, so the claims about absolute symlinks in `/usr/share/icons`, about how many entries a
  stock Debian install produces, and about file modes inside a proot-created rootfs are reasoned
  from the specs and the proot-distro source, not measured.
- **Whether proot-distro 5.8.0 is what is actually installed on the target phone.** 5.8.0 is what
  this tree's package recipe builds (`packages/vaj/termux-packages/packages/proot-distro/build.sh:5`);
  a device could still be on a 4.x shell-script build, whose layout is `installed-rootfs/<name>`
  and which has **no `--shared-x11`**. Scanning both layouts and preferring `--shared-tmp` when the
  installed version is old may be necessary; I did not establish which versions are in the wild.
- **Launch latency** of a proot GUI app against the runner's 15 s display-start budget.
- **The `.icon` metadata files** and `index.theme` parsing (theme inheritance, `Directories`,
  `MinSize`/`MaxSize`/`Scale`) were read in the spec but I made no judgement about whether a
  rootfs-wide implementation needs them; the launcher's hicolor-only shortcut sidesteps them.

---

## Verified on a device — 2026-09-19

Checked on pong (Nothing A065, Android 16) against `com.termux`, `proot-distro` 5.8.0 and a
Debian 13 (trixie) container. This section overrides anything above that contradicts it.

| Claim | Result |
|---|---|
| Rootfs is `containers/<name>/rootfs` | **Confirmed.** `installed-rootfs/` does not exist on this device. |
| `--shared-x11` / `--shared-tmp` are "already included in default mode" (the tool's own `--help`) | **False.** `login --get-proot-cmd` emits no `/tmp` bind at all in default mode, and `commands/login/__init__.py:338-339` reads both flags with `getattr(args, …, False)`. `proot_cmd.py:215` is the only `X11-unix` bind site and it is guarded by `shared_x11`. The flag is required. |
| A container reaches the launcher's default `termux-x11 :0` with no `-ac` | **Confirmed.** `xdpyinfo` inside the container returns the full display info against a server started as `termux-x11 :0` (the app's stored default, `x11_display_preferences.xml:114`). No `xauth` in the container. |
| `DISPLAY` crosses into the container by itself | **No.** `proot-distro login debian -- env` carries `PULSE_SERVER=127.0.0.1`, `HOME=/root`, `USER=root` and the Android variables, but no `DISPLAY`. `-e DISPLAY=:0` is required. |
| An absolute `Icon=` path into the rootfs works | **Confirmed by code path**; `LinuxAppIcons.find` takes absolute paths as they are and checks only that the name ends `.png`. The rootfs is inside the app's own data directory, so no permission is involved. |
| End-to-end launch | **Confirmed.** `xeyes` and Typora both map windows on the display through the exact wrapper `Exec=` line the guide gives. `xwininfo -root -children` showed `"termux-launcher changelog.md - Typora"  800x700`. |

Three failure modes were reproduced, not reasoned:

- **Electron refuses root.** Typora died with `Running as root without --no-sandbox is not
  supported`; `proot-distro login` is root by default. `--no-sandbox` fixed it. The container here
  does have a non-root user (`amalv`, uid 10331), so `-u` is the other way out.
- **D-Bus noise.** Typora printed `Failed to connect to the bus` repeatedly and ran anyway.
- **App-level failures look like plumbing failures.** `zutty` aborted on a missing `9x18` font.

Not verified: that a wrapper `.desktop` appears in the app drawer and launches on tap. The parse
and icon paths were read in Java and the generated command was run by hand, but no tap was
injected — pong is the user's daily phone ([[pong-is-in-the-users-hands]]).

Also found: `app/src/main/assets/x11/x11-gpu-setup.sh:54` hard-codes the pre-5.x
`installed-rootfs/debian` path, so it points at nothing on a current `proot-distro`.
