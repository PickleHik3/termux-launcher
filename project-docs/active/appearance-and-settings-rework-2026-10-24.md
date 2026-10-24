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
| 1 | Touched often: opacity, tint, size, place, shown or hidden | first, always open |
| 2 | Tuned once: blur, corner radius, margin, style | below |
| 3 | Rare: grain, depth, edge colours, alignment, effects | collapsed under **More** |

Labels are one or two words. Icons come from Material Symbols; glyphs this launcher alone needs
(the Look stops, Tray / Beads / Strip) are drawn by Codex. Commit on Done stays (ADR 0003's
editor rule): sliders and drags preview live, ✓ writes, Undo reverts the session, Back from a
dirty editor asks.

## 3. The cards

Per-half marks (½) say the value is held per half. Links (→) jump to a Settings page for
behaviour that is not appearance.

**Pill (global).** Orientation chip, Undo, Done in the pill itself.
1. Look slider ½ (Clear · Mist · Tint · Solid · Custom) · Wallpaper (shown, pick) · Theme (System ·
   Light · Dark, Black)
2. Icon pack · Monochrome · Text size · Wallpaper colours · Contrast (terminal) · Full screen ·
   Minimal
3. More: Alignment (wallpaper) · Terminal palette for tools · Presets (save, load) · Depth

**Home.** 1. Icon size · Label size · Glass widgets (global switch; a widget's own long-press
choice Default · Glass · Plain overrides it) 2. Two-line labels · Label shadow · Folders (density) ·
Numerals (widgets) · Page dots 3. More: Folder limit · Wallpaper scroll · → Gestures · → Pages

**Status bar** ½. 1. Opacity · Tint · Shown · Place (top/bottom) 2. Blur · Corners · Margin ·
12-hour 3. More: Grain · Depth · → Indicators and weather

**Terminal.** 1. Opacity · Tint · Text size 2. Blur · Corners · Margin · Contrast 3. More: Grain ·
Cursor trail · Effect · Edge colours · → Font

**Dock** ½. 1. Opacity · Tint · Height · Shown · Place (edge) 2. Style (Docked · Floating) ·
Design (Tray · Beads · Strip) · Blur · Corners · Margin 3. More: Grain · Depth · Search field ·
→ Dock items (#156)

**Keyboard** ½. 1. Opacity · Tint · Height · Shown 2. Theme (colours, typeface) · Blur · Corners ·
Margin 3. More: Grain · → Size (floating, split) · → Layouts

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
