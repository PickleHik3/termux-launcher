# The launcher idiom: what users already know (2026-10-23, #149)

Decisions from the grilling round of 2026-10-23 over
`project-docs/reference/launcher/lawnchair-16-idiom-comparison-2026-10-23.md`. The reference
is Lawnchair 16 / AOSP Launcher3, because that is what thumbs are trained on. Vocabulary as in
`docs/GLOSSARY.md`: **App menu**, **Dock item**, **Drawer tile**, **Dock**, **Home float**,
**Home layout**, **Page**, **Crossing**.

## The principle

Where a convention is the same job in every launcher (open an app's menu, turn a page, remove by
dragging), this launcher does it the Launcher3 way, with Launcher3's numbers. Where this launcher
has more to offer than Lawnchair (Home's long press, the dock's launcher items), it keeps the
surface that shows more (a sheet, an editor) and gives it the Material 3 look and motion. The
developer's words: the feature set is wider, so a sheet exposes the controls a menu would hide.

## Decided

### 1. App menu (#151, mockup first)

- Form: grouped rows with a 12×10 dp arrow to the icon. Outer corners the dialog radius, 4 dp
  joins between rows of a group, a lone row a 100 dp pill, rows 216×52 dp.
- Surface: Material You shade steps per group (the dynamic palette's first/second/third shades),
  2 dp elevation, no blur plate.
- Rows, in order: notifications (only when badged) · the app's shortcuts (collapse past 6) ·
  Widgets · App info · Add to home · Pin or Unpin · Change icon (Reset icon beside it once a
  custom icon is set) · Uninstall. The category row goes.
- Motion: open 200 ms with a slight overshoot, rows fade 83 ms; close 233 ms; emphasized
  decelerate in, emphasized accelerate out. A long-press haptic on open.
- Same card on the dock, on Home and in the drawer.

### 2. One page rule (#152)

- Wall, widget pages and the drawer: commit past 0.4 of a page, return past 0.33, fling at
  500 dp/s (easy fling 400 dp/s, minimum 250 dp/s), all in dp. The drawer's px threshold goes.
- Touch slop damped four-fold when the swipe runs more than 30° off its axis.
- One pure `PagePolicy` answers all three; the existing per-pane constants become reads of it.

### 3. One settle (#153)

Widget pages ride the wall's `SettleSpring`; the fixed 160 ms widget settle goes. Launcher3's
distance-over-velocity tween is not adopted: the spring already settles by distance and speed.

### 4. Drop targets (#154)

While an icon or folder is dragged on Home, Remove and Uninstall targets stand along the top
(56 dp targets, 28 dp apart, 80 dp frame radius), sliding in with the drag and out with it.
Remove for Home icons and folders, Uninstall for apps. The menu's rows stay as the non-drag route.

Landed 2026-10-24 (beside #154, not the targets themselves): an app picked up in the drawer is
carried to Home the Launcher3 way. The drawer closes under the finger, the wall turns to Home and
the icon drops into the free cell nearest the finger, page turns at the edges included
(`HomeAppDrop`, `HomeAppDropPolicy`, the pane's carry path). The Remove and Uninstall targets
are still to build.

### 5. Live dock reorder (#155)

A drag over the dock parts the other icons after a 650 ms hover (150 ms slide) and drops into the
slot; resting on an icon still previews and makes a folder. The pinned apps editor stays for bulk
changes. Dock slots stay size-driven (the four size presets); no count setting.

Landed 2026-10-24 (#155 partial): the dock still takes drops only from its own pins, so an app
dragged out of the drawer and let go over the dock is not put down. Dock pinning by drag from the
drawer and the live reorder are still open.

### 6. Dock items (#156)

- The Drawer tile, New terminal, New float terminal and Command palette stand in the apps row
  like pins: added, removed and reordered in the pinned apps editor's new "Launcher items"
  section and by live drag (#155). One list per layout, so a Home layout may hold its own.
- The Layout editor's Drawer tile control (on/off, start/middle/end) goes; a migration carries
  the stored toggle and position into the dock items so no install loses its tile.
- Glyphs: Codex draws the three terminal items' glyphs (see the codex notes in the agent memory);
  the drawer keeps its universal glyph.

### 7. Sheets and folders (#157)

- Widget picker: 267 ms open and close, 28 dp top corners.
- Folders expand in place from their icon in 200 ms with no dim; rename by tapping the title.
- Home's long-press sheet stays and takes the same sheet numbers and the Material 3 look.

### 8. Haptics (#158)

One rule: LONG_PRESS on the app menu's open and on every drag pickup; CLOCK_TICK on slot and page
changes as today. Nothing else new.

## Deferred

- The A–Z bubble's form (Launcher3: 75×62 dp bubble, 32 dp letter, 52 dp thumb): a later visual
  round with crops.
- The Appearance editor's Look and Layout on one page, one rule to learn: its own spec and
  grilling round after #151 lands (#159). The editor's commit-on-Done design stays unless that
  round re-decides it.

## Already in place (no ticket)

Tap-to-add in the widget picker, resize handles on a placed widget, drop-icon-on-icon folders
with a 300 ms rest, folder rename by tapping the title.

## Order

#151 (mockup, then code) → #152, #153 (ride with the crossing, #142) → #154, #155, #156 →
#157, #158 → #159's grilling round.

## Testing

Pure policies with JVM tests (`PagePolicy`, the slop damping, the menu's row order and group
shapes, dock items' per-layout list and migration). Motion and the look on the emulator, then on
pong; every ticket adds its owed checks to `project-docs/device-checks.md`.
