# Welcome card, tour offer for updaters, GUI apps setup in Display

Agreed 2026-09-20 on the review page `.lavish/welcome-gui-setup-20260920/index.html`.
All eight decisions took the recommended option.

## Goal

1. The first-run tour is offered to anyone whose completed tour version is older than the
   current one, so people who update meet the new cards too.
2. The tour opens with a welcome card, the same for new and updating users: a two-sentence
   introduction and the choice "Take the tour" / "Not now".
3. The closing card takes the welcome card's shell. Its Graphical section (`pkg install
   x11-repo`) is removed; GUI apps are a Display matter.
4. Settings → Display's "Set up Linux apps" becomes a "Get GUI apps" screen with two routes and a
   distro choice. It builds one command, copies it to the clipboard with a notice, and the user
   pastes it into the terminal. The script asks for the container account's name and password in
   the terminal and records the name where the launcher reads it.

## Today (verified 2026-09-20)

- `TourController.isFinished()` is `completedVersion >= 1` (TourController.java:227). `RUN_VERSION
  = 3` (line 29). Version numbers only steer `resumeIfInProgress()`.
- No welcome card. First card is `find_help` ("Hold a pane corner."). Steps: TourRun.java:66-70.
- Closing card: `TourClosingCard` sections MULTITASKING, CUSTOMISATION, EXTRAS (`tlstore install
  fastfetch sigye claude-code`), GRAPHICAL (`pkg install x11-repo`), GRAPHICAL_NIX. Strings
  strings.xml:1874-1893.
- Legacy onboarding migration (FirstBootTour.java:142-163) writes `RUN_VERSION` as completed.
- `x11_distro_setup` row → `DistroSetupDialog.show` → `DistroSetupRunner` runs `DistroSetup.script()`
  in a terminal pane. Fixed Debian, fixed account `user` (DistroSetup.java:64), fonts, Mesa, starter
  packages `x11-apps mousepad`. `X11PaneFrame.applySetupOffer` (661-685) shows the same dialog
  when a container is not ready. The account is never recorded: `ProotDistro.parsePasswd` picks
  the first ordinary login account each time.
- Clipboard helper: `ShareUtils.copyTextToClipboard(Context, label, text, toast)`
  (termux-shared ShareUtils.java:101). In-app notice: `AppNotice.show(context, resId)`.

## Decisions

| ID | Decision | Not taken |
|----|----------|-----------|
| D1 | Offer the tour to anyone whose completed version < current, once per tour change | every update; new installs only + notice |
| D2 | "Not now" records the current version; Replay stays in Settings → About & support | ask again, at most twice |
| D3 | Closing card: three pointers (Multitasking, Make it yours, Extras with its one copyable command), Read the docs + Start using | no commands; a Get GUI apps button |
| D4 | Distros: Debian (recommended), Ubuntu, Arch Linux | Debian+Ubuntu only; add Fedora, Alpine |
| D5 | Copy the command to the clipboard with a notice; the user pastes. The pane runner is removed | secondary "Run it here" |
| D6 | The script writes the account name to a file in the container folder; the launcher reads it, `parsePasswd` discovery as fallback | launcherctl; discovery only |
| D7 | Starter apps: a short checkbox list per route (browser, file manager, text editor, terminal), sane defaults ticked | fixed set |
| D8 | The Display place's not-ready offer opens the new screen | small copy-only dialog |

The password is typed in the terminal only and never stored anywhere by the launcher.

## Behaviour

### Tour
- `RUN_VERSION` → 4. "Offered" = `completedVersion < RUN_VERSION`. A completed run, or "Not now",
  records `RUN_VERSION`. The legacy migration records `VERSION_BEFORE_THE_WELCOME_CARD = 3`, so a
  migrated old install is offered the welcome card once.
- New card kind WELCOME, before the first lesson, not a lesson (saved step indexes of lessons are
  unchanged; a run in progress resumes at its lesson as today). Buttons: "Take the tour" starts
  `find_help`; "Not now" ends the run, records the version, sets the skipped flag.
- Replay (`EXTRA_SHOW_ONBOARDING`) starts at the welcome card too.
- Closing card: same visual shell as the welcome card (kicker, title, body, buttons). Sections
  reduced to three; GRAPHICAL and GRAPHICAL_NIX deleted with their strings. Extras keeps Copy.
- Product copy, one pass at build time; no mechanism words. Draft: "Welcome" / "A terminal, your
  apps and a Linux display on one home screen. A two-minute tour shows you the basics." /
  "That is the tour".

### Get GUI apps screen (Settings → Display → Apps)
- Row title "Get GUI apps". Screen: route choice (single), then per route:
  - Route 1 "Termux's X11 apps": starter checkboxes. Command:
    `pkg install -y x11-repo && pkg install -y <ticked packages>`.
    Package names for browser / file manager / text editor / terminal are looked up against the
    live x11-repo index at build time and recorded in the code with the date checked.
  - Route 2 "A full Linux inside": distro radio (Debian recommended, Ubuntu, Arch Linux), starter
    checkboxes. Command = `pkg install -y proot-distro`, `proot-distro install <alias>` (skipped
    if present), then one `proot-distro login <alias> -- sh -c '…'` that: asks `read -p` for a
    username, `useradd -m -s <shell>` (adduser on Debian/Ubuntu if simpler), `passwd`, writes the
    name to the record file, installs fonts + Mesa + ticked apps with the distro's package manager
    (apt for Debian/Ubuntu, pacman for Arch; a per-distro package table), and prints one closing
    line: "Done. Your apps appear in the app drawer under Linux apps."
- "Copy the command" copies via `ShareUtils.copyTextToClipboard`, shows the notice "Command
  copied. Paste it into the terminal and press Enter.", and finishes Settings so the terminal is
  in front.
- `DistroSetupDialog`, `DistroSetupRunner` and their tests go; `DistroSetupStore` (dismissal) stays
  and keys the Display place offer, which now opens the screen (D8).

### Account record (D6)
- File: `$PREFIX/var/lib/proot-distro/containers/<alias>/launcher-user` (one line, the name),
  written from the Termux side: the script asks for the name before the login step and passes it
  into the container with `-e`, then writes the file after login returns. Phase 3 confirms this is
  robust on pong and says so.
- `ProotDistro` reads the file first; if absent or the account does not exist in `/etc/passwd`,
  today's discovery applies.

## Build plan

| Phase | Branch / worktree | Deliverable | Depends on | Gate |
|-------|-------------------|-------------|------------|------|
| 1 Tour | `feat/tour-welcome` / `../tl-wt-tour-welcome` | Welcome card kind, version-aware offer, Not now, migration version, closing card redesign, strings, tests | — | `:app:testDebugUnitTest` green; Waydroid: fresh install shows Welcome, an install with completed=3 shows Welcome once |
| 2 Setup screen | `feat/gui-apps-setup` / `../tl-wt-gui-apps-setup` | `GuiAppsSetup` command builder (routes, distros, package tables, tests), the settings screen, copy + notice, Display place offer → screen, old dialog/runner removed | — | Builder unit tests for every route × distro; screen on Waydroid |
| 3 Account record | `feat/distro-user-record` | Script prompts + record file; `ProotDistro` reads it | 2 | Pong: route 2 end to end, app opens as the chosen user |
| 4 Copy and docs | `chore/gui-setup-copy` | Help topic line, `docs/en/Linux_Apps_From_A_Distro.md`, string pass | 1, 2 | Review |

Phases 1 and 2 in parallel (opus). 3 and 4 sonnet. Merge, full suite, build and install from the
main session; pong install only on the user's word.

## Out of scope
- GPU inside a container, Electron sandbox handling (unchanged), Fedora/Alpine, launcherctl
  routes, storing any password.
