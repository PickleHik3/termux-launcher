# Setup wizard (2026-10-29)

Decisions from a grilling on 2026-10-29 about a first-run setup wizard that pre-configures the app
from a few choices about how it will be used. It ships in the next pre-release and is shown to
updaters as well, for testing. This is the spec; no tickets yet. Implementation lands on
`v2-redesign`.

## What it replaces

The wizard is a new flow that retires three first-run systems:

- the Welcome sheet (`app/firstrun/WelcomeSheet.java`, `WelcomeSheetView`, `WelcomeSheetHost`): its
  "How will you use it?" page and its switches page (wallpaper colours, weather, contacts, Display,
  AI);
- the interactive first-boot tour (`app/tour/FirstBootTour.java`, `TourController`, `TourRun`:
  pageSwipe, findApps, keyboard, statusSwipe, findAction, findHelp, closing);
- the AI welcome card (`app/firstrun/TaiWelcomeCard.java`, `TaiWelcomeCardHost`,
  `TaiWelcomeDownloads`, prefs file `tai_welcome_card`).

Code and prefs go, not flags. One migration, run once: an install whose tour completed version
says it finished the old tour gets the guide chapters pre-marked watched; it still sees the wizard
once because the wizard is new. The legacy `termux_first_launch` file is read by that migration
only and never written again.

## Who sees it, and when

- Fresh installs: on first start, after the bootstrap is ready.
- Updaters: once after this pre-release, with their current settings pre-selected on every section
  and a **Keep my current setup** button in the sheet header on every section and as a second
  button on the Summary. It restores a snapshot taken when the wizard opened (Customize applies
  live, so the snapshot covers those keys too) and marks the wizard done with no cards.
- The wizard has its own version number (`setup_wizard_version`, completed version stored next to
  it). Bumping it re-shows the wizard to updaters, pre-selected. This pre bumps it. Later releases
  bump it only when the question set changes materially; pre-releases that merely fix the wizard do
  not re-show it.
- Skippable at any section (Skip moves on with the section's current value). Re-runnable from
  Settings.
- Finish writes concrete settings through the existing setters and records that the wizard ran.
  No persistent "profile" or mode: later changes in Settings are never fought by the wizard.

## Container

The Appearance editor's model (`surfaces/EditorFramePose`, `AppearancePreviewArea`,
`EditorCardSheet`): the live launcher scaled into a miniature frame above a card sheet, so the
dock, the status strip and the wall stay visible while the user picks. The wizard is one card sheet
with sections.

A stepper sits at the top of the sheet, one dot per section (`*--*--*--*`). Visited dots are
tappable to go back; forward goes only to the next section; dots past the furthest visited section
are inert. The Summary is the last dot and unlocks once every section was visited. Sections that
do not apply to the user are not drawn (none today: Home is a superset of Terminal).

## Phase one: the sections, in order

Every section is a single screen in the sheet. Unless a section says "live", nothing is written
until Finish.

### 1. Home screen?

"Will Termux Launcher be your home screen?" Yes / No.

- Writes the existing Use-as key (`UseAsSetting.write`, enum `wall/UseAs`) with today's
  semantics. No (Terminal) still removes the Home page from the wall, hides Home's settings
  section and drops Home from Start in; the wizard also sets Start in
  (`app_launcher_start_in`) to the Workspace. Yes (Home) writes Home and leaves Start in alone.
- Home gets the same sections as Terminal plus the default-launcher card in phase two.
- Two routes back from Terminal to Home: the Use-as row in Settings › General
  (`general_preferences.xml` key `use_as`) and **Run setup again**, which re-asks this question.

### 2. Customize (applies live)

Visual choices, previewed in the miniature as they are picked. Rows, top to bottom:

1. **Wallpaper.** "Set an in-app wallpaper" opens the wallpaper picker inside the card as a
   sub-step (the picker's logic and thumbnails from `chrome/wallpaper/WallpaperPickerLogic`,
   `WallpaperThumbs`; the miniature stays above); choosing a photo applies live and returns to
   the rows. "Use my system wallpaper" asks the storage permission so the launcher can read it and
   writes `use_system_wallpaper`. With no in-app wallpaper the legacy blur controls are offered and
   the modern (AGSL) glass controls are greyed out with a nudge to set an in-app wallpaper, exactly
   as the Appearance editor does today.
2. **Look preset.** Clear, Mist, Tint, Solid: the `SurfacePresets` ids `minimal`, `frost`,
   `stock`, `solid`. Pre-selected by the device tier (below).
3. **Colours from wallpaper** (Material theming): `terminal_dynamic_colors_enabled`.
4. **Cursor trail**: `terminal_cursor_trail`. Pre-selected by tier. This is the only place the
   wizard shows it; the Power section does not repeat it.
5. **Dock design**: chips for the existing designs in `dock/DockStyle` (glass tray, lens beads,
   taskbar, wall strip, peek glass), written to `dock_design` in `PlaceLayoutStore`. "Docked or
   floating" is expressed by the design picked; no new key.

No margins row (dropped: no page-margin key exists and the editor already tunes insets).

### 3. Multiplexer

One toggle, **Multiplexer**, mapped to `split_pane_controls` (app default on). When on, three
sub-toggles unfold under it:

| Sub-toggle | Key | Wizard default |
|---|---|---|
| Status strip shown | the status element's placement in `PlaceLayoutStore` (`Element.STATUS` slot; `hidden` vs its edge), the same switch the Appearance editor's hide uses | on |
| Autotiling | `pane_dwindle_default` (app default off) | on |
| Dynamic sizing of the focused pane | `pane_focus_grows` | off |

When Multiplexer is off, the sub-toggles are not shown and not written.

### 4. Linux desktop apps

"Will you run Linux desktop apps?" Yes / No. Yes creates the Display card in phase two; nothing
is written in phase one. There is no display enable/disable setting and the wizard adds none: the
in-app X11 display spawns on demand whenever the user installs and opens an X11 app, whatever they
answered here. The answer only decides whether the setup card (distro, setup run, GPU profile) is
offered up front. The autostart key `x11_display_autostart` stays untouched; the card's setup
decides it.

### 5. On-device AI

One toggle, pre-selected by tier. On creates the AI card in phase two.

### 6. Power and battery

The Performance page's knobs (`performance_preferences.xml`), pre-set by the device tier and
shown for the user to flip:

`fancier_glass`, `fancier_glass_motion_light`, `wallpaper_parallax`, `lazy_mode`,
`low_memory_preset`, and `animation_speed` (from `general_preferences.xml`). Cursor trail is in
Customize and is not repeated here.

The screen opens with a line **Detected: <model>, <RAM>, <GPU>, <tier>**.

### 7. Review setup

Heading **Review setup**, line "You can change these settings later." A grouped list of
everything Finish will write, compact enough to read without scrolling (the miniature is hidden
on this section): Use (Use-as, Start in, Multiplexer and its sub-toggles), Appearance tagged
"Already applied" (wallpaper, look, dock design, wallpaper colours, cursor trail), Power &
battery, then Setup cards (which cards will wait on the sheet), then **Finish**. Updaters also
see **Keep my current setup** here.

## Device detection and tiers

Runs once when the wizard opens, in code, in the APK (no remote table):

- inputs: total RAM, `Build.SOC_MODEL` / `Build.HARDWARE`, the GPU renderer string
  (`fragments/settings/termux/TaiGpuName`, `x11/X11GpuProbe`), and a short table of known models
  that overrides the tier (the Mali-G52 / MT6768 phones from the GPU freeze fix among them);
- tiers and what they pre-select (every screen still shows and can be flipped):

| Tier | Customize | AI | Power |
|---|---|---|---|
| low | Solid, cursor trail off | off | fancier glass off, motion light off, parallax off, lazy mode on, low-memory preset on, animation speed reduced |
| mid | Mist, cursor trail on | on | motion light off, parallax on, fancier glass on |
| high | app defaults (Tint) | on | app defaults |

The exact thresholds (RAM cut-offs, SoC families) are chosen during implementation and recorded
in the code next to the table.

## Phase two: the card sheet

After Finish, a sheet of cards for everything that takes more than one tap. A card exists only
because a phase-one answer created it. The sheet comes back on every launcher start until every
card is done or dismissed, and is reachable any time from Settings › General › Setup. Each card
lists its sub-steps with ticks and has a **Dismiss** button for a user who already knows the item;
a dismissed card can be reopened from the Setup row.

Cards are rebuilt flows inside the card, on the existing engines, with new card UI. The Settings
pages they mirror stay as the expert route and are unchanged.

### Display card (desktop apps: yes)

1. **Where do your desktop apps run?** Termux packages, or a Linux distro via proot (with the
   distro pick). Engines: `x11/GuiAppsSetup`, `DistroSetup`, `DistroSetupStore`.
2. **Setup run** with a progress line and a log peek.
3. **GPU profile**: runs the benchmark (`X11GpuBenchmarkRun`, `X11GpuBenchmarkStep`) and picks a
   profile (`X11GpuProfileStore`, `x11_gpu_profile_pick`). On the known-bad GPUs in the detection
   table the freeze-safe profile is forced and the benchmark is skipped with a note.

Window manager and resolution stay at their defaults. Done when all three steps completed.

### AI card (AI on)

Capability rows (chat, voice, vision and whatever the hub offers), each naming its model and size;
on the low tier the heavier rows start unticked with a note. Download with progress on
`ai/TaiDownloadHub` and the model store. Done when every ticked model is present.

### Default launcher card (Home only)

Opens the system home prompt through `HomeAppChooser`. Done when the home role is held.

### Permission cards (everyone, mirroring Settings › General › Permissions & services and Notifications)

One card per item, each with its own Open/Connect button that leads to the system or in-app
screen, and Done when the permission is held (re-checked on every sheet open):

- **Shizuku**: needed for the CPU widget in the status bar and double-tap to lock the screen;
  links to the Shizuku setup guide when it is not installed.
- **Accessibility service**: optional second way to double-tap to lock; not needed with Shizuku.
- **Notification permission**: the launcher's own notifications, including session controls.
- **Notification access**: read other apps' notifications for pinned cards and dots (optional).
- **Pinned notifications**: the essential-notification rules (app, keywords, or both); needs
  notification access first, the card says so and offers it.

### Dismissed cards

Dismissed cards drop to a **Dismissed** list at the bottom of the sheet in minimized form (name
plus **Reopen**), greyed, so an accidental dismiss is one tap away. The sheet keeps returning
while any non-dismissed card is open; it stops when every card is done or dismissed.

### Learn the basics card (everyone)

A chapter list filtered by the answers:

- Home answers: page swipe, find apps, the dock;
- all: keyboard, status strip swipe, find an action, help;
- Multiplexer on: panes and tiling;
- desktop apps yes: opening a desktop app window.

Chapters are in-app animations, no video files. The technique is decided during implementation
between hand-coded View animations on a stylised phone frame and Lottie assets. Each chapter marks
itself watched; the card settles when all are watched or on Dismiss. The same list is the guide,
reopenable from Settings.

## Settings entry

Two rows in Settings › General next to Use-as:

- **Run setup again**: reopens the wizard with the current settings pre-selected (the updater
  path, including Keep my current setup).
- **Setup**: the card list with each card's state, reopening dismissed ones. A badge counts the
  open cards; the row is hidden when no cards exist.

## Acceptance gate: the upgrade matrix

Every upgrade source opens the wizard without a crash, and Keep my current setup leaves the app
exactly as it was:

| Source | Use-as | Tour | Legacy onboarding prefs |
|---|---|---|---|
| v1.2.x release | Home / Terminal | done / not | present / absent |
| v1.3.0 pre | Home / Terminal | done / not | present / absent |
| v2.0.0-pre.1 | Home / Terminal | done / not | present / absent |
| v2.0.0-pre.2 | Home / Terminal | done / not | present / absent |

Delivered as an instrumented test over prefs snapshots plus an emulator pass with real prefs
snapshots captured from each prior build. Fresh install is the extra row.

## Out of scope

- An experience-level question (newcomer / power user) and an input-hardware question: rejected.
- Margins on Customize.
- A remote device table.
- Window manager and resolution in the Display card.
- Bootstrap progress, battery-optimisation exemption and notification permission as wizard steps
  (the wizard asks permissions only where a chosen item needs one: storage for the system
  wallpaper, the home role for the default-launcher card).

## Open points for implementation

- The status strip's hidden state: confirm that the `Element.STATUS` placement accepts `hidden`
  the way the extra keys' does (`PlaceLayoutStore.java:141-151`), and that the Appearance editor's
  hide writes that slot.
- The AI card's "model present" signal: read from `TaiDownloadHub.Snapshot` status values; confirm
  the store entry that survives restarts.
- Animation technique for the guide chapters.
- Tier thresholds and the known-model table.
