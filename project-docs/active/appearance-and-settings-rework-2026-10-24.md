# The Appearance editor on one rule, and Settings filed the way users think

Decided with the developer on 2026-10-24 (grilling round for #159, widened to the Settings tree).
Parent: #149. Supersedes the Look/Layout split of ADR 0003's editor and amends the 2026-10-19
Settings restructure where it says so below. Status: specified; wireframes and tickets follow.

## 1. The complaint

Two editors (Look, Layout) act on the same four objects with two grammars, and share two controls
(corner radius, margin). Seven doors with five names (Look, Looks, Surface style, Appearance,
Layout) open them. Theme, wallpaper, icon pack and widget glass sit in Settings instead. In
Settings, Linux apps hides four levels under Terminal, vibration appears three times, gestures
sit on three pages, and the pages are outlined rows rather than Material 3.

The developer's goal, in their words: both on one page, controlled from one place, and one rule
(muscle memory) for users to learn on how to change stuff.

## 2. The rule

**Tap the thing.** The editor shows the live launcher in a frame. Two targets exist:

- **A surface.** Tap it and its card rises with everything about it: how it looks, how big it is,
  where it sits, whether it shows. Drag it to move it. Five objects: Home, Status bar, Terminal,
  Dock, Keyboard.
- **The pill.** Tap it for everything that has no single surface: the Look slider, wallpaper,
  theme, icon pack, text size, full screen, minimal mode, the orientation chip, Undo, Done.

No tabs. No mode. Nothing about appearance lives anywhere else: Settings keeps one door row.

### 2.1 You edit the half you see

The frame slides between Home and the Workspace like the wall does. A value set while the frame
shows the Workspace is the Workspace's; set on Home, it is Home's. This holds for the Look slider
and for the dock, status bar and keyboard (the terminal exists only on the Workspace, Home only on
Home). The two values cross with the slide the way the dock's dress already does (ADR 0012 §3).
Each card's menu offers **Also on Home** / **Also on Workspace**, a one-tap copy. New installs and
upgrades start with both halves equal, so nothing changes until a user wants it to. ADR 0013.

### 2.2 No inheritance vocabulary

Users never see Base, inherit, detach, follow, link. A slider moved on a card is that surface's
own value and stays. The card menu has **Reset**, which puts the surface back on the shared Look.
A small dot on a slider's label marks an own value. Moving a surface's slider at any Look stop
moves the Look slider to Custom by itself; Custom is a consequence, never a gate.

### 2.3 Frequency decides order

Every card orders its controls in three tiers, so position teaches frequency:

| Tier | What | Shown |
|---|---|---|
| 1 | Touched often: the glass strip (Frost, Opacity, Tint), size, place, shown or hidden | first, always open |
| 2 | Tuned once: corner radius, margin, style | below |
| 3 | Rare: edge colours, alignment, effects, links | collapsed under **More** |

The glass is the previous editor's control set, exactly (2026-10-25): one **Frost** slider moves
blur and grain together along `GlassControls`' curve (blur = R(4+32t^1.5), grain = R(2+6t),
t = Frost/100; label "Frost · 20%"); BLUR and GRAIN are still the stored surface properties
underneath, and nothing in the editor offers them apart. The glass rows are a strip of vertical
sliders (`LegendSlider`, the old Look page's Custom row), each its name and value beside the track
and the "set here" dot after it; only sizes (Height, Corners, Margin and the like) are horizontal
sliders.

Labels are one or two words. Icons come from Material Symbols; glyphs this launcher alone needs
(the Look stops, Tray / Beads / Strip) are drawn by Codex. Commit on Done stays (ADR 0003's
editor rule): sliders and drags preview live, ✓ writes, Undo reverts the session, Back from a
dirty editor asks.

## 3. The cards

Per-half marks (½) say the value is held per half. Links (→) jump to a Settings page for
behaviour that is not appearance.

**Pill (global).** Orientation chip, Undo, Done in the pill itself.
1. Look slider ½ (Clear · Mist · Tint · Solid · Custom) · at the Custom stop the **All surfaces**
   strip ½: Frost · Opacity · Tint · Edge · Vibrancy · Depth · Text size (Edge and Depth only where
   the glass edge is drawn), the shared values every surface on the half follows · Wallpaper
   (shown, Change: the only way to the picker) · Theme (System · Light · Dark; the app has no black
   theme, so the wireframe's Black switch is not built)
2. Icon pack · Monochrome · Wallpaper colours · Contrast (terminal) · Full screen · Minimal
3. More: Alignment (wallpaper) · Tint colour · Terminal palette for tools

**Home.** 1. Icon size · Label size · Glass widgets (global switch; a widget's own long-press
choice Default · Glass · Plain overrides it) 2. Two-line labels · Label shadow · Folders (density) ·
Numerals (widgets) · Page dots 3. More: Folder limit · Wallpaper scroll · → Gestures · → Pages

**Status bar** ½. 1. Frost · Opacity · Tint · Shown · Place (top/bottom) 2. Corners · Margin ·
12-hour · Clock face 3. More: → Indicators and weather

**Terminal.** 1. Frost · Opacity · Tint · Text size 2. Corners · Margin · Contrast 3. More:
Cursor trail · Effect · Edge colours · Split gap · → Font

**Dock** ½. 1. Opacity · Tint · Height · Shown · Place (edge) 2. Style (Docked · Floating) ·
Design (Tray · Beads · Strip) · Corners · Margin 3. More: Search field ·
→ Dock items (#156)

**Keyboard** ½. 1. Frost · Opacity · Key opacity · Tint · Key corners · Key spacing · Height ·
Shown 2. Theme (colours, typeface) · Corners · Margin 3. More: Type · → Size (floating, split) ·
→ Layouts

**Doors.** Every door opens the editor on its object: a corner tab on a terminal opens the
Terminal card, on Home the Home card, on the display window the editor at rest; the doors that
named the wallpaper (the terminal menu's Set wallpaper, Home's Wallpaper, appearance.set_wallpaper)
open the Appearance card with its Wallpaper row in view. The picker is no door of its own.

The Drawer tile control leaves the editor with #156. "Icon size" in the dock card (which today
writes dock height) is renamed Height, since that is what it does.

## 4. Settings

The root keeps its shape. Changes, keys unchanged throughout:

- Group labels: Your launcher · **Everywhere** (was Shared) · System.
- **Appearance** (was Look): one row, "Wallpaper, glass, theme, icons and layout", opens the
  editor. Theme, wallpaper, contrast, widgets on glass and icon rows move into the pill card and
  stay searchable; a search hit opens the editor on its card.
- **Linux apps** returns to the root beside Terminal (amends the 10-19 "Display section retired"
  decision; the word Display stays retired). It holds Set up Linux apps, Show in drawer, Hidden
  apps, Input, Resolution, Startup, Fixes (was Troubleshooting), Window manager.
- **Apps & dock** (was Dock & drawer) gains one link row, Linux apps in the drawer, and its Drawer
  page's first row is renamed Grid.
- **Home › Gestures** gathers every gesture: the five Home ones, Double-tap A–Z to lock, Swipe down
  for the drawer, Swipe on the terminal to switch windows.
- One home per setting. Vibration lives under General only; Voice input under Keyboard only
  (On-device AI links); Notification access under Permissions only; the three logging pages become
  one Diagnostics page. The Performance page's "Smoother motion" shortcuts are the one sanctioned
  set of copies, because that page is a triage tool.
- Misfiles: Hardware keyboard gets its own group on the Keyboard page; Full screen leaves Text &
  display for the pill card; General's Help row folds into About & help.
- **Set up Linux apps** becomes a stepped guide: a full-screen sheet, one decision per step (source,
  distro, starter apps), the command copied at the end, graphics acceleration as a skippable last
  step; the row afterwards says what is installed.
- Every door into the editor is named Appearance and opens on that page's object: Home › Appearance
  opens the Home card, Terminal › Appearance the Terminal card, Keyboard › Appearance the Keyboard
  card; the Home action, the terminal long-press item and the corner tab too.

### 4.1 Material 3 restyle, every page in one pass

The developer's reference (a music app's Appearance page): grouped tonal cards per section (rows
share one rounded container, `colorSurfaceContainerHigh`, a hairline between rows, 24dp group
corners), a leading Material Symbols icon on every row in a 24dp slot, filled segmented controls
(selected segment `colorPrimaryContainer`, unselected tonal, no outline) in place of outlined pills
and chips, section headers in `colorPrimary` title-small. The root rows already use this card
(`preference_settings_root_row`); the inner row layout and the segmented and chip styles change, so
one layout change restyles every page. The editor's cards use the same components.

## 5. Two fixes that ride along

- **Darker Workspace, normal Home.** Falls out of §2.1; the developer hit this on 2026-10-24.
- **Clock AM/PM.** The launcher's clock widgets put AM/PM beside the seconds; the expanded status
  bar stacks it above them. Widgets adopt the stacked column.
- **Widget glass default.** The global switch exists (`widgets_on_glass`, default off), so widgets
  look per-widget-only. Default becomes on; existing per-widget choices stay as overrides.

## 6. Work breakdown

In landing order, each its own ticket under #159 and its own worker; disjoint file sets where
they run in parallel.

1. Model: per-half look values in `termux-shared` (override keys gain a half; resolver takes the
   half; Base and the old keys read as "both"), with tests. ADR 0013.
2. Fixes: clock widget meta column; widget glass default.
3. Settings restyle: row layout with icon slot and grouped cards, filled segmented control, chip
   row, section header; icon per row (a map from key to Material Symbol).
4. Settings filing: XML trees, `SettingsTree`, strings, search index, docs/en.
5. Set up Linux apps as a stepped guide.
6. The editor: one card per object from `SurfaceEditorRows` plus the Layout session's controls,
   pill card, half slide in the frame, Reset and Also-on, dots, three tiers, More. Entry points
   open on an object. The Layout editor's canvas session stays the move/size engine underneath.
7. Device checks on three emulators, before/after page, then a staging install on pong.

Risks the workers must respect: `SurfaceEditorController` is 4300 lines and already hosts both
sessions; settings pages have been broken repeatedly, so the row layout change is one worker
alone; per-half values touch every `resolveSurfaceValue` caller and the crossing (ADR 0012).

## 7. Decisions from the wireframe pass (2026-10-24, night)

Wireframes: `project-docs/reference/launcher/appearance-rework-wireframes/` (README lists the
measurements and the questions they raised). Resolved as follows:

- **Names.** The glossary's Pill is the dictation card, so the editor's bottom bar is the
  **editor bar** (orientation chip · Undo · Done · palette). The palette's card is the
  **Appearance card**. Users see neither name.
- **A card up hides the editor bar.** ✕, a tap on the frame, or Back puts the card down; Done is
  on the bar. The card is a bottom sheet with two detents: half (tier 1 visible, the frame at about
  0.6) and full (the frame at about 0.44). The frame is a live preview, legibility at 0.44 is not
  required.
- **Move vs slide.** A plain horizontal swipe on the frame slides between halves (dots and the
  half's name under the frame). A surface moves by hold-and-drag, the Launcher3 idiom. A tap opens
  the card.
- **Custom is a stop** the thumb can land on, and the stop any surface edit sets.
- **A hidden surface keeps a dashed ghost outline** in the frame so it can still be tapped and shown.
- **Per half, precisely:** opacity, tint, frost (blur and grain), and the Look stop; the Custom
  stop on one half restores that half alone. Corners and margin stay shared. Dock
  Style and Design, heights and places stay shared (ADR 0013 §4). The ½ mark in §3 means that.
- **A tap on a Home icon or widget opens the Home card.** A widget's own settings stay on its
  long-press on the live Home.
- **Place** offers the edges the layout engine supports for that element (the dock: top or bottom
  today; left and right when the engine does).
- **Landscape** is edited through the orientation chip, which flips the frame.
- **Font scale 1.3.** Segment labels shorten: Trackpad · Touch · Direct. Segments keep
  `minWidth=0` and autosize. The half detent may show fewer rows; the sheet scrolls.
- **Linux apps › Input copy:** "OSK auto-show" becomes "Keyboard pops up :: Opens when you tap a
  text field and closes when you tap elsewhere"; "Keyboard over display" becomes "Keyboard over the
  window". Done with the stepped guide work.
- **The guide's steps vary by edition**: the nix edition has no install route, so it shows its
  advice and the docs link with no steps; elsewhere the progress line counts the steps that exist.
- The wireframes' icons are placeholders; the build uses Material Symbols Rounded vectors.

## 8. From the blind review (Codex, no context, 2026-10-24)

The review is kept beside the wireframes (`REVIEW-codex-blind-2026-10-24.md`). Adopted:

- **Selectable vs selected.** At rest every surface wears a quiet 1dp outline at 40% of the
  outline colour; the selected surface alone wears the 2dp primary ring, and its card header names
  it. A hidden surface's ghost outline is dashed.
- **Scoped menu labels.** "Reset dock" (the card's object), "Copy to Home" / "Copy to Workspace"
  (a one-time copy, never a link). The ⋮ menu anchors so it never covers a slider's value.
- **Every slider shows its value** (Opacity 72%, Height 56dp, Text size 100%) and announces it;
  a strip slider shows it in its legend ("Frost · 20%").
- **Labels.** "Visible" (switch) and "Position" (segments) replace Shown and Place. The dot after
  a label means "set here"; its content description says so.
- **Black**: dropped; there is no black app theme to switch on.
- **Reachability.** The sheet's last row clears the navigation inset by 16dp at both detents; a
  partially visible control is a bug. Rows use content-driven height; at font scale 1.3 and 2.0
  nothing clips and segments reflow to a vertical radio list when their labels no longer fit.
- **Previews demonstrate the control.** The Home card's preview carries a long app name and a
  folder; the Dock card's frame scrolls so the dock is in view at the half detent.
- **Plain words in Linux apps.** "Pick a Linux" (not distro) with one-line consequences per
  choice; touch modes get one-line summaries ("Trackpad: drag to move the pointer, tap to click").

Not adopted: replacing the Look slider with a mode picker (the five-stop slider is a decided
element; it snaps to stops and Custom is both a stop and a consequence), and the suggestion to
make the editor's scope explicit with text (the frame, the ring and the card title carry it).

## 9. The developer's review on pong (2026-10-25 morning)

Decided and built the same morning:

- **Glass controls are the previous editor's, exactly.** Frost (one slider; blur and grain follow it
  through `GlassControls`), Opacity, Tint, and for all surfaces Edge, Vibrancy, Depth, Text size;
  the keyboard adds Key opacity, Key corners, Key spacing. Separate Blur and Grain rows were an old
  heuristic and must not come back; the stored BLUR and GRAIN properties remain underneath.
- **Vertical sliders everywhere.** Glass values are the previous version's vertical legend sliders
  in every card; the Appearance card's Custom stop shows the "All surfaces" strip writing the
  shared values. Horizontal sliders only for sizes, plus the Look stop slider.
- **One door.** The terminal's corner tab and every other door open the one editor on their object;
  the old in-app wallpaper page is gone as a destination and lives only behind Wallpaper › Change.
- **Terminal effects wear a bezel.** 12dp sides, 16dp top and bottom, 10dp inner corners, the
  terminal's own glass (opaque at glass 0); the effect is clipped inside it and the grid shrinks to
  fit. The effect never dresses Home's chrome; it switches at the landing of a crossing.
- **A lone floating band is a whole card.** The refraction program's radius is held to half the
  rect's shorter side (the notches and the seam were a rounded-rect distance field with r > h/2).
- **Glass widgets.** The switch clears every widget's own On/Off back to Default so it always has a
  visible effect; Bare is kept; the row says so in one line.
- **Per half, strictly.** Moving to Custom restores the shown half only; binding and sliding never
  write a stop to a half (pong had acquired Solid values on Home this way).
- **Drawer.** Long-press is the drawer's own drag (folders, reorder) until the finger leaves through
  the top of the list (250 ms hold or 24dp past it); then the drawer dismisses and Home's drag takes
  the icon into the nearest free cell. The built-in keyboard, when selected, is what the drawer's
  search raises; the keyboard slides down with the list's scroll and is dismissed past a threshold.
