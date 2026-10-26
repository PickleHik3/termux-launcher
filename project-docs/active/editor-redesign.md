# Editor redesign (2026-10-26)

Status: spec, approved by the developer on 2026-10-26. The entry flow (§5) and the spec as a whole
were approved the same day. Nothing built; see the Status ledger at the end.

One spec combining the layout model (§1), the card review (§2), the long-press popup, the A–Z
redesign and the unification pass. It lives on its own branch and worktree off `v2-redesign`, because
another agent works on v2. We merge at the end.

Sources, kept as the review drawings (not embedded here):
- `.lavish/spec.html`, `.lavish/editor-cards.html` and `.lavish/layouts.html` in the main checkout.
- `~/reports/app-inventory-2026-10-26/`: about 115 emulator screens of v2-redesign `c9ae4486d`, used
  for §8.

Copy rules follow `AGENTS.md`: sentence case, "Position" not "Placement", product copy not
engineering notes. The sections keep the review's numbers; the review has no §7.

## 1. What changes, in one screen

### Model

- **Two screens, two layouts.** Home and the Workspace each keep their own arrangement and glass, and
  they start identical. There is no "Own layout" switch. **Match Workspace Styling** on the Home card
  (or **Match Home Styling** on the Workspace card) copies the other screen's layout and glass. Undo
  reverses the copy until Done.
- **Each screen stores its own glass.** Home and the Workspace keep separate values. Each screen's
  card carries its own Look tiles and glass strips. The two meet only when you tap a Match row.
- **Per screen and shared.** Per screen: where each bar sits, what is hidden, edge sizes, the glass,
  and the widget grid (Home only). Shared (Global): wallpaper, theme, contrast, tint colour, glass
  material, icon pack and tool colours. An edit changes only the screen you are on. On a fresh install
  the two arrangements are identical.
- **Global is one card.** The palette pill's card is renamed Global and becomes the master page for
  what both screens share: wallpaper first (tiles Change wallpaper, Show wallpaper, Wallpaper colours), then theme,
  contrast, tint colour, the glass material (Vibrancy, plus Edge and Depth where the phone draws
  them), full screen, minimal, icon pack and tool colours. No slider from today's card is lost: Look,
  Frost, Opacity and Tint move to each screen's card, and Text size to the Terminal card.
- **Use as: Home screen or Terminal only.** Asked on page 1 of the welcome sheet for fresh installs,
  and a setting afterwards. Upgrading users are not asked and stay on Home screen. Terminal only
  removes the Home page, Home's settings, Home from Start in, and the Home and Match cards. If the app
  is the default home, it offers to pick another.
- **Upgrade.** If Own layout was off, Home gets a copy of the Workspace layout, so nothing moves.
- **Welcome page 1.** Title "How will you use it?". Options: **Home screen** ("Your apps, widgets and
  a terminal, all in one place") and **Terminal only** ("Terminal is primary focus. Your home launcher
  unchanged."), then Continue. Choosing Home screen then offers to make it the phone's default home.
- **Settings > Launcher in Terminal only** shows "Use as: Terminal only" and "Start in: Terminal ·
  Display". Home's sections (widgets and grid, Home layout, Home gestures) are gone; Appearance and
  Keyboard remain.

### Editor

- **The shrunken Layout mode is retired** (about 7k lines). Its controls move into the cards:
  Position rows, Columns and Rows, Bottom padding, bar Height, and rotate-to-edit. Rotating the phone
  edits the other orientation; the editor stays open and its pill says which one is being edited.
- **One card skeleton:** header (eye, reset, ✕), Position and Style, the Appearance strips (glyph on
  top, value below, name shown while dragging), glyph choices and preview tiles, a grid of option
  tiles, and chips for other pages.
- **The Dock owns its bars.** The apps row, A–Z and extra keys are a list you can reorder, each with
  an eye and a sliders glyph that opens its own settings. Each bar has its own Position there (top,
  bottom, left, right, above the keyboard); there is no dock-wide Position. The list order stacks
  bars that share an edge.
- **Hidden bars wait in a tray** at the screen edge while you edit; drag one out to bring it back.
- **No ⋮ menu.** It only ever held "Reset" and "Copy to". Copy to goes (the Match rows replace it),
  so the header gets a reset button in its place.
- **Long-press** the dock, the status bar or the keyboard for a small popup with Edit and Hide. Their
  cards are two taps away (§3).
- **A–Z on Home, Niagara style:** the glass behind the letters can be turned off, and the strip can
  be Shown, Faint, or hidden behind a pull tab (§4).
- **Logging.** Every editor entry point logs one tagged line (`EditorDoor`, naming the door and the
  card), so the next stray open on pong names its door.

## 2. The cards

These are the final shapes from the review. The glyphs are placeholders: Codex is drawing the set,
for the developer's approval, as wave 0. The exception is the dock's Tray, Beads, Strip and Peek
glyphs, which are today's and stay.

Common to every card:
- Header: card glyph, title, then eye (visible), reset, ✕. Cards without an eye: Terminal, Workspace,
  Home, Global (none of these can be hidden).
- A "strip" is a vertical slider: glyph on top, value below, name shown while dragging. Cards keep no
  summaries; a glyph's name shows on long-press.
- Defaults below are the values the review drawings show.
- Shared strip sets. **Glass**: Frost 20%, Opacity 10%, Tint 0%. **Shape**: Corners 24dp, Margin 8dp.
- Direction glyphs (Position rows) show a phone outline with the bar on the edge it goes to: Top,
  Bottom, Left, Right, Above the keyboard, Rides the apps row. Style glyphs: Docked, Floating, Split.
  Clock alignment glyphs: Left, Center, Right. Folder density glyphs: Compact, Default, Roomy.

### Status bar

Header: eye, reset, ✕.

1. **Position** (one row, glyphs): Top (default), Bottom.
2. **Glass** (strips; section note "Shape"): Frost 20%, Opacity 10%, Tint 0% | Corners 24dp,
   Margin 8dp.
3. **Clock**: a strip of live face previews, all showing 07:48: Flip, LCD, Minimal, LED, Tape, Slab
   (default Slab). Below it **Clock position** (glyphs): Left (default), Center, Right.
4. **Options** (tiles): 12-hour off, Seconds off, °F weather off, Camera gap on.
5. **Indicators** (tiles, show/hide): CPU on, RAM on, Weather on, Notifications off, Media on.

Rationale:
- Position: one row, top or bottom.
- The clock face picker becomes a strip of live previews, so there is no popup to open.
- Clock alignment moves out of the popup into a glyph row.
- Options gain Seconds (new) and °F weather (exists in Settings today). Camera gap is a shortcut to
  the #163 cutout setting.
- Indicators open up into show/hide tiles: CPU, RAM, Weather and Media exist today; Notifications is
  new.
- The Visible switch becomes the eye in the header.

Today: three screens of scrolling.

### Dock (the header reads "Dock")

Header: eye, reset, ✕.

1. **Style** (words with glyphs): Docked (default), Floating.
2. **Bars** (section note "drag to reorder"): one rounded list. Each row has a drag handle, the bar's
   glyph and name, a sliders glyph that opens that bar's settings in place, and an eye (a hidden bar
   greys out). Rows follow the extra-keys editor's handle rows (§8). Default order: Apps, A–Z, Extra
   keys; Apps and A–Z shown, Extra keys hidden.
   - **Apps** settings:
     - Position: Top, Bottom (default), Left, Right, Above the keyboard.
     - App icons: stepper, 7.
     - Icon size: stepper, 100%.
     - Most used row: switch, on.
     - Icon pack: System, opens the sheet. Hint: "The icon pack here applies to the apps row only."
   - **A–Z** settings (drawn separately in the review):
     - Position: Rides the apps row, Top, Left, Right (default Right).
     - Glass background: switch, off.
     - Show: Shown (default), Faint, Pull tab.
     - Hint: "Faint keeps the letters scrubbable at low opacity. Pull tab frees the strip for
       widgets; scrubbing the tab brings the letters up over Home."
   - **Extra keys** settings (drawn separately, with Extra keys dragged up and A–Z hidden):
     - Position: Top, Bottom, Left, Right, Above the keyboard (default Above the keyboard).
     - Height: stepper, 36dp.
     - Chip: Extra Keys Editor.
3. **Appearance** (strips): Frost 20%, Opacity 10%, Tint 0% | Height 40dp, Corners 24dp, Margin 8dp.
   (The drawing labels this section "Global"; see the open points at the end.)
4. **Dock Type** (tiles with today's glyphs): Tray (default), Beads, Strip, Peek.
5. **Options** (tiles): Search field off, Drawer icon off, Command palette on, Notification dots on,
   Quick reply on, Monochrome icons off.

Rationale:
- Style (Docked or Floating) comes first. There is no dock-wide Position: each bar is placed on its
  own, so Position lives in each bar's settings.
- Bars is one rounded list: drag a row by its handle (or anywhere on it) to reorder. The eye shows or
  hides a bar, and a hidden one greys out. The sliders glyph opens that bar's own settings in place.
- Apps' settings: number of icons, icon size, and an icon pack for the apps row only. Extra keys:
  strip height and the Extra Keys Editor.
- Appearance holds the dock's glass and shape; App count moves into Apps.
- Dock Type (was Design) keeps today's glyphs.
- New options: Command palette (a palette button on the dock that opens the palette with the
  keyboard), Notification dots (show or hide), and Quick reply (swipe up on an app icon to reply to
  its notification).
- Apps' settings gain a Most used row switch.
- The Dock items link is gone: it only opened the Dock & drawer settings page, which Settings still
  has.

Today: one card for all three bars; none of them can move or be reordered from here.

### Keyboard

Header: eye, reset, ✕.

1. **Type** (glyphs): Docked (default), Floating, Split.
2. **Glass** (strips): Frost 20%, Tint 0%, Background opacity 10% (hollow-background glyph), a link
   that moves the two opacities together, Key opacity 71% (hollow-keys glyph).
3. **Shape** (strips): Key corners 11dp, Key spacing 3.0, Height 104%, Corners 24dp, Margin 8dp,
   Bottom padding 12dp (its glyph points up).
4. **More** (chips): Theme, Layouts.

Rationale:
- Type sits at the top as glyphs: Docked, Floating, Split.
- Glass reads Frost, Tint, then Background opacity and Key opacity, each with its own keyboard glyph
  (hollow keys, hollow background). A link between the two opacities moves them together.
- The Bottom padding glyph points up.
- The Size chip is removed: a floating keyboard is sized with the handles on the keyboard itself.
- Theme and Layouts stay as chips.

Today: nine strips, then Type sits two screens down.

### Terminal

Header: reset, ✕. No eye.

1. **Glass** (strips; section note "Shape"): Frost 20%, Opacity 10%, Tint 0% | Text size 100%,
   Corners 24dp, Margin 8dp, Split gap 8dp.
2. **Contrast** (words): Softer, Default (default), Harder.
3. **Cursor trail** (preview tiles): Off, Default (default), Motion, Railgun, Torpedo, Pixie, Comet.
4. **Screen effect** (preview tiles): None (default), CRT, Green, Amber, Material, TFT.
5. **More** (chip): Font.

Rationale:
- No eye button: the terminal cannot be hidden.
- Contrast stays as words (Softer, Default, Harder); no glyph read clearly enough. Contrast should
  also darken the grey text, which hardly changes today.
- Cursor trail and Screen effect become preview tiles you tap directly, with no popup. A new CRT
  colour, Material, takes the phone's system colours.
- Font becomes a chip.

Today: Cursor trail and Effect are popups listing names.

### Workspace (new card)

Header: reset, ✕. No eye. Opened by tapping the Workspace page label at the bottom of the editor.

1. **Look** (section note "Workspace only"): Clear, Mist (default), Tint, Solid, Custom.
2. **Glass** (strips; section note "status bar, dock, keyboard, terminal"): Frost 20%, Opacity 10%,
   Tint 0%.
3. **Match Home Styling** row, with a Copy action.
4. **More** (chip): Global.

Rationale:
- The Workspace's twin of the Home card. Its glass is what the bars on the Workspace wear while they
  follow it.
- Opened by tapping the Workspace page label at the bottom of the editor (the Home label opens the
  Home card the same way).
- Match Home Styling copies Home's layout and glass over this screen.

Today: no such card. The palette's glass strips quietly write the glass of whichever screen you are
on.

### Home

Header: reset, ✕. No eye.

1. **Grid**: Columns stepper 4, Rows stepper 6.
2. **Look** (section note "Home only"): Clear, Mist, Tint, Solid, Custom (default Custom).
3. **Glass** (strips; section note "widgets, status bar, dock"): Frost 20%, Opacity 10%, Tint 0%.
4. **Labels & folders** (glyph rows):
   - Label size: Small, Medium (default), Large.
   - Icon size: Small 48dp, Default 54dp (default), Large 60dp.
   - Folders: Compact, Default (default), Roomy.
   - Numerals: Sans (default), Mono, each written in its own font.
5. **Options** (tiles): Glass widgets on, Two-line labels off, Label shadow on, Page dots on,
   Wallpaper scroll off.
6. **Match Workspace Styling** row, with a Copy action.
7. **More** (chips): Global, Gestures, Edit pages.

Rationale:
- The Columns and Rows steppers move in from Layout mode, at the top.
- Glass is Home's own glass: the widgets, status bar and dock on Home wear it while they follow it.
  Icon size leaves this card: tap the dock to size its icons.
- Label size and Folders become glyph rows; Numerals keeps its words, each written in its own font.
- Five switches become one tile grid. Glass widgets' explanation moves to its long-press.
- The Own layout switch is gone; the Match Workspace Styling row replaces it.
- Gestures and Pages become chips.

Today: four screens of scrolling, Own layout switch at the very bottom.

### Global (the palette)

Header: palette glyph, title "Global", reset, ✕. No eye.

1. **Wallpaper**: tiles Change wallpaper (a shortcut that never reads on), Show wallpaper, Wallpaper colours.
2. **Colour**:
   - Theme (glyphs): System, Light, Dark. The drawing marks no default.
   - Contrast (words): Softer, Default (default), Harder.
   - Tint colour: three swatches, the first selected.
3. **Glass material** (strips; section note "both screens"): Vibrancy 1.00×, Edge 40%, Depth 30%.
   Hint: "Edge and Depth show on phones that draw the glass edge, as today."
4. **Options** (tiles): Full screen off, Minimal off.
5. **More** (chips): Icon pack, Tool colours.

Rationale:
- Becomes the Global card: everything both screens share, with Wallpaper first (tiles Change
  wallpaper, Show wallpaper, Wallpaper colours).
- Glass material keeps the strips that set how glass behaves rather than how much there is:
  Vibrancy, plus Edge and Depth where the phone draws the glass edge. None of today's strips are lost.
- Look and Frost, Opacity and Tint move to the Home and Workspace cards, because each screen stores
  its own glass.
- Text size leaves: it is the same setting as the Terminal card's.
- Theme as glyphs, Contrast as words, Tint colour as three swatches; Full screen and Minimal as
  tiles; Icon pack and Tool colours as chips.

Today: five screens of scrolling, and its glass strips quietly write whichever screen you are on.

## 3. Long-press: three targets

Only three long-press targets, each matching a card: the dock as a whole, the status bar and the
keyboard. A popup in the App menu's style appears beside the one you pressed and outlines it. Edit
opens its card and Hide hides it. The terminal keeps its existing corner long-press, and widgets keep
their resize mode.

- Status bar popup: Edit, Hide.
- Dock popup (the card header reads "Dock"): Edit, Hide.
- Keyboard popup: Edit, Hide, and Docked · Floating (no move icon).

| Target | Where a long-press opens the popup | Still its own long-press |
|---|---|---|
| Dock | Anywhere on the dock's frame: the apps row, A–Z and extra-keys strips, between items | App icons (App menu, which gains Edit dock), extra keys, and A–Z scrubbing (any movement scrubs) |
| Status bar | Anywhere on the bar | None |
| Keyboard | Its frame and top edge | The keys (alternates) |
| Terminal | No popup | The existing corner long-press stays the way in |
| Widgets, Home background | Unchanged | Resize mode and the Home sheet |

## 4. A–Z on Home, Niagara style

Lives in the Dock card under Bars > A–Z > sliders. Glass background off leaves just the letters over
the wallpaper. Show has three states:
- **Shown.**
- **Faint:** about 30%, still scrubbable.
- **Pull tab:** the strip disappears, widgets can claim it, and dragging a small tab on the edge
  brings the letters up over Home.

The scrub is today's A–Z scrub, with the same gesture, letter bubble and animation; the tab only
decides where the letters stand. (The review drawings show an app list behind the letters; ignore it.)

## 5. How users reach each card

### On Home
- Long-press Home > Appearance lands on the Home card: Home's own glass, grid, icons, labels and Match
  Workspace Styling.
- From there, the Global chip (or the palette pill) opens the Global card: wallpaper, theme, contrast,
  tint colour, glass material and icon pack, for both screens.
- Long-press Home > Wallpaper goes straight to the Global card.

### On the Workspace
- Long-press the terminal pane's border > Appearance (and the terminal's More sheet > Appearance)
  lands on the Workspace card: the Workspace's own glass and Match Home Styling.
- From there, the same Global chip or palette pill opens the Global card.
- The dock, status bar and keyboard are one long-press away (§3). The terminal's own card is a tap on
  the terminal inside the editor.

The rule: a screen's door opens that screen's card, and Global is always one tap further, the same
from either screen.

### The flow, from where users start to every card

On Home:
- Long-press the background > Home sheet > Appearance > Home card > Global chip > Global card.
- Long-press the background > Home sheet > Wallpaper > Global card.
- Pane or window controls > Edit > Home card.

On the Workspace:
- Long-press a pane's border > Pane menu > Appearance > Workspace card > Global chip > Global card.
- Terminal ⋯ More sheet > Appearance > Workspace card.
- Pane or window controls > Edit > Workspace card.

On either screen:
- Long-press the dock > popup (Edit · Hide) > Edit > Dock card, opened on the bar you pressed.
- Long-press the status bar > popup (Edit · Hide) > Edit > Status bar card.
- Long-press the keyboard's frame > popup (Edit · Hide · Docked/Floating) > Edit > Keyboard card.
- Long-press an app icon in the dock > App menu > Edit dock > Dock card.

Inside the editor:
- Editor at rest > tap an outline > Status bar, Dock, Keyboard or Terminal card.
- Editor at rest > tap the page label > Home or Workspace card.
- Editor at rest > palette pill > Global card.
- Editor at rest > drag a bar out of the edge tray > Dock card, on that bar.

From Settings and tools:
- Settings > Home · Terminal · Keyboard > Edit on screen > Home, Terminal or Keyboard card.
- Settings > Look > Global card.
- launcherctl, or an intent naming a card > editor > the named card.

Out of a card:
- Home > Settings > Home (Gestures tab; Pages section).
- Global > Icon pack sheet; Tool colours page.
- Dock > Extra Keys Editor.
- Keyboard > Keyboard theme; Layouts.
- Terminal > Font.
- Home ⇄ Workspace: Match Styling copies layout and glass, undoable until Done.

### Door table

| Door | Today | After |
|---|---|---|
| Long-press Home > Appearance | Home card | Home card (same) |
| Long-press Home > Wallpaper | Appearance card | Global card |
| Long-press the terminal pane's border > Appearance | Editor at rest, no card | Workspace card (new) |
| Pane and window controls > edit | The card for the current page | Same |
| In the editor: tap a surface outline | Status bar, Terminal, Dock (the A–Z row and extra keys too) or Keyboard card | Same, except tapping the A–Z row or extra keys opens the Dock card with that bar expanded |
| In the editor: tap the page label (Home / Workspace) | Nothing | That screen's card (new) |
| In the editor: the palette pill | Appearance card | Global card |
| In the editor: a hidden bar's pill | Its card; hidden extra keys open the Keyboard card | The edge tray; dragging a bar out brings it back, and tapping it opens the Dock card with that bar expanded |
| Settings > Home / Terminal / Keyboard > Layout editor | Home, Terminal or Keyboard card | Same cards; the row is renamed Edit on screen |
| Settings > Look | Editor at rest | Global card |
| Terminal More sheet > Appearance | The editor | Workspace card |
| Long-press the dock, status bar or keyboard > Edit | Nothing | That card (new) |
| launcherctl `appearance.surface_editor`, the section intent | The current page's card / a named card | Same; adds the workspace card name |
| The Layout-mode intent (`EXTRA_LAYOUT_EDITOR`) and `openLayoutEditor()` | The shrunken Layout mode | Deleted. Any leftover caller opens the live editor instead |

Every door logs one tagged line (`EditorDoor`, naming the door and the card).

## 6. Settings: which exist and which are new

| Control | Card | Status |
|---|---|---|
| Status: CPU, RAM, Weather, Media indicators; °F weather | Status bar | Exists in Settings, moves onto the card |
| Status: Notifications indicator | Status bar | New (the dock already badges notifications) |
| Status: Seconds on the clock | Status bar | New (only the clock widget has seconds) |
| Camera gap | Status bar | Exists (#163), shortcut tile |
| Notification dots, Quick reply | Dock | Exist in Settings; still to confirm that Quick reply means swiping up on an app icon |
| Most used row | Dock > Apps | Exists as a page in pinned apps; this makes it a switch |
| Command palette button | Dock | New button; the palette itself exists and opens with the keyboard |
| Icon pack for the apps row | Dock > Apps | Exists (the pinned-icon pack); the global pack stays in Global |
| Monochrome icons | Dock | Exists, already dock-only, so it moves there |
| CRT Material colour | Terminal | New effect colour from the system palette |
| Background / Key opacity link | Keyboard | New; the two opacities exist today |
| Bar order, per-bar eye | Dock > Bars | Order new; hiding exists (the Layout-mode hidden stack) |
| Home icon size: Small, Default, Large | Home | Exists as a percentage in Settings > Home; becomes presets on the same key, separate from the dock's icon size; sizes from Launcher3's grid profiles (48, 54 and 60 dp proposed, to confirm against AOSP) |
| A–Z glass off; Faint; Pull tab | Dock > A–Z | New |
| Long-press popup | Dock, status bar, keyboard | New |
| Workspace card glass | Workspace | Exists as the Workspace's base glass, which only the Appearance strips wrote until now |

## 8. Unify with the rest of the app

The whole app was gone through on the emulator (v2-redesign `c9ae4486d`, about 115 screens in
`~/reports/app-inventory-2026-10-26/`) and the spec checked against it. These changes keep the cards
in step with what is already there.

Findings that drove them:
- The extra-keys editor already has handle rows; the Bars list copies them.
- Settings > Home: rows with icon, title and summary, and Icon size as a percentage.
- Dark theme on a light wallpaper: the A–Z letters and the extra-keys icons almost vanish.
- The card's Gestures link lands at the top of Settings > Home.

Changed in this spec:
- The Bars list uses the extra-keys editor's rows: the same handle, row height and "Hold the handle to
  reorder" hint, with our sliders glyph and eye where its ⋮ sits.
- One Home icon size. Settings > Home already has Icon size as a percentage. The Small, Default and
  Large presets replace it in both places, on one key, and existing percentages map to the nearest
  preset.
- Card rows match Settings rows: a 16 sp title (cards use 22 sp today) and the same outline icons.
  Cards keep no summaries; each glyph's name shows on long-press.
- Links land where they say. Gestures opens the Gestures chip, Edit pages scrolls to Pages, and Tool
  colours opens its page (today it opens the editor). Settings > Home > Appearance stops reopening the
  editor it came from.
- One icon per idea. The palette glyph means Global everywhere (the grid-plus "Edit how it looks and
  sits" goes), Look and Monochrome stop sharing the half-circle, and Wallpaper colours gets its own
  glyph.
- Words. Look means a screen's glass preset, Theme means light or dark, Global means the shared card,
  and Appearance stays the editor's name. Settings' "Apps & dock" and the card's "Dock" name the same
  thing. Labels are sentence case (App drawer icon).
- Text over the wallpaper follows the wallpaper. Glass-off A–Z, Home labels and toolbar icons pick
  light or dark ink from the wallpaper under them, not from the theme. Without this, Niagara-style
  A–Z would vanish on half of all wallpapers.
- The terminal's More sheet has an Appearance item. It opens the Workspace card (§5).

Seen, but left for later (not this work):
- Four search field shapes: the Settings pill, the widget picker, Add an app, and the drawer's glass
  pill.
- Dialogs disagree on buttons: text, outlined plus filled, and the editor's Done pill.
- Add an app and the pinned-apps rows draw some icons small and misaligned (Calendar, Camera, Clock).
- Settings pages: some have chip tabs and some do not (Apps & dock, Terminal). Terminal fonts shows
  long disabled rows.
- With one pinned app, Peek, Beads and Floating look alike at rest. The dock styles need judging with
  a full dock.

## 9. Concerns

Settled in review:
- Bars and Position: every bar is placed on its own, so Position lives in each bar's settings. A bar
  on another edge (A–Z on the right of Home) stays in the list, and the order only stacks bars that
  share an edge.
- Nothing here is built yet. Every control marked new in §6 needs its code; the rest exist and only
  move. Each gets a test and an emulator check as it lands.

Risks to handle:
- The grey text and Contrast bug. The palette's grey hardly changes between contrast levels. It gets
  fixed, with a regression test, in this work.
- The hidden extra keys map to the wrong card. Today they open the Keyboard card, while their outline
  opens the Dock card. The Bars list ends that.
- Long-press conflicts. Only three targets, and icons, keys and scrubbing keep their own long-press
  (§3). Each gets a test that its old gesture still wins.
- The sliders scroll the card by mistake. A vertical swipe that starts on a slider changes it. Shorter
  cards help, and a touch-slop check stops it.
- Font scale 1.3x on the glyph rows and tile grids. Each gets a Robolectric layout test with native
  graphics.
- The pull tab must not fight the edge gestures (Android Back, our page swipe). It sits away from the
  bottom, inside the gesture-exclusion budget.
- Migration covers Own layout off, the hidden stack, and the keyboard chin, now Bottom padding.

## 10. Order of work

0. A branch and worktree off v2-redesign, because the other agent is on v2. Write this spec to
   `project-docs/active/`, and have Codex draw the glyph set for the developer's approval.
1. The layout model: remove the switch, add the upgrade copy, the Match rows and the Workspace card,
   and log every door.
2. The card skeleton and glyph rows, then card by card: Status, Dock (with Bars), Keyboard, Terminal,
   Home, Global.
3. Retire Layout mode once every control has a new home; delete its code and tests.
4. Use as and the welcome page.
   - 4b. Unify (§8): Settings-sized rows, deep links, one icon per idea, wallpaper-aware ink.
5. The long-press popup, then the A–Z glass, Faint and pull tab.
6. The new settings: seconds, the notifications indicator, the command palette button, CRT Material
   and the opacity link.

Each wave gets emulator before and after shots on a `~/reports` page. Pong only when asked.

## Decisions made during the build

- Change wallpaper is a tile on the Global card, not a chip on a Wallpaper row: the developer asked, 10-26.
- Pane controls open the Workspace card on the Workspace: the approved flow wins over the door table.
- Edge sizes (corners, margin) stay shared between the two screens, as the layouts page has it.
- Reset means the shipped defaults; Undo restores the as-opened state. Two different promises, two words.
- Stored defaults were kept over the mockup's selections: Apps above the keyboard, A–Z rides the apps
  row, Notification dots off, Most used off. Changing them would change every upgrader's launcher.
- No dock icon-size stepper: the dock's Height is its icon size, so a second control would lie.
- No extra-keys Height stepper: only a termux.properties scale exists. Strip height got a stepper
  instead (the Dock card's Strip type).
- Icon presets are Small 48 / Default 54 / Large 60 dp; unset and "100" both read as 48 dp, so
  nothing changes on upgrade.
- Seconds defaults on, because the bar always drew them.
- Start in moved to Settings › General › Launcher, beside Use as, since the two depend on each other.
- Settings search ranks an exact title first.
- The A–Z popup opens only from its host's margin: a hold on the letters is already a scrub.
- Glass-off A–Z under the Docked frame or a shared plank does nothing yet: the shared sheet stays.
- Hide has no Undo snackbar: the tray and the eye bring a bar back.
- The Camera gap tile stays a tile that never reads on, since the gap is not a setting.

## Open points

Carried from the review:

- Resolved: the Dock card's Appearance section annotated "Global" was a drawing slip; the strips are
  the dock's own and the card was built that way.
- Resolved: corners and margin stay shared between screens (edge sizes, above); glass is stored per
  screen. This spec's later statement (§1) held.

Raised by the build:

- The `az_show` pull tab is placed by `AzTabPolicy`: a 56 x 48 dp touch area, 48 dp from the
  leading corner.
- Glass-off A–Z on a shared plank (and under the Docked frame) keeps the shared sheet.
- Minimal clock weight is no longer settable in the editor (Settings still has it).
- The terminal corner-pill route to the editor.
- The Dock items link is removed.

## Status

- Wave 0, spec and glyphs: approved 2026-10-26; glyphs reused from the spec page, drawables `ic_ed_*`.
- Wave 1, layout model, Workspace card, Match rows, doors: merged d9f9e6368; fixes e178d0335.
- Wave 2a, skeleton, row kinds, one file per card: merged 414c3ebec; fixes af9dd69be; migration fix 3222f5f1a.
- Wave 2b, cards: f50465bbf Status bar and Terminal; cc85be9cd Dock; 5d9dd78fd Keyboard, Home, Global;
  review 889bd3a82; fixes d8f228463.
- Wave 3, Layout mode retired: merged 53384d4f7.
- Wave 4, Use as: merged c30d69792.
- Wave 4b, unify: a3a4cbf3c icons and words; 2ce41a5a7 ink.
- Wave 5, long-press popup and A-Z: merged 8b345e202.
- Wave 6, palette tile: merged 24be2c8e4; pinned during page swipes 5d3c12cca.
- Landed with the cards: seconds, the notifications indicator, Material CRT and the opacity link.
- Full suite at 2ce41a5a7: 11735 tests, 1 failure, fixed in 40b7874c4.
- Emulator rounds: `~/reports/editor-redesign-wave1/`, `-wave2a/`, `-wave2/`, `-waves3-6/` (running).
- Not yet done: merge of feat/editor-redesign into v2-redesign; device checks are in
  `project-docs/device-checks.md` under "Editor redesign (2026-10-26)".

### Build notes

- Wave 3, Layout mode retired. The layout canvas, its drags, handles and other-orientation drawing are deleted
  with `EXTRA_LAYOUT_EDITOR`, `openLayoutEditor()` and the `layout-editor` door (it had no live
  caller). Homes: Position rows (Dock card per bar, Status bar card), Columns and Rows (Home
  card), Bottom padding and Height (Keyboard card), dock Height (Dock card), the Strip type's row
  height (new Strip height stepper under the Dock card's Dock type, the only bar height Layout
  mode itself set), rotate to edit (turning the phone keeps the editor open; the chip names the
  orientation, a tap says to turn the phone), the hidden stack (tap opens the card, drag onto the
  frame brings the bar back). Not given a home: an extra-keys Height (§2 Dock, Extra keys) was
  never a Layout-mode control; the bar's height is still termux.properties
  `terminal-toolbar-height`. Holding a surface in the frame no longer drags it.
- Wave 5, long-press popup and A–Z. The popup is one window-level watcher (`SurfaceLongPress`, policy in
  `SurfaceLongPressRoute`) fed before any view; owners of their own long-press are found under the
  finger (anything long-clickable, the A–Z letters, keyboard keys, extra keys, pinned
  notifications). The A–Z letters keep the scrub on any press, since it starts on the touch: the
  A–Z popup opens from its host's margin. A hold between dock icons no longer opens the pin
  editor (Settings › Default apps still does). Hide writes through the editor's own plan.
  The A–Z's Show is folded into the resolved layout (`PlaceLayout.azTab`, never stored), so the
  pull tab claims no band and revives the existing tab layer (`AzPullTabLayer`); Faint is the
  letters' resting alpha; Glass background off hides the strip's own capsule and tells the ink
  there is no glass, so the existing wallpaper sampler (`GlassInk.bareBandInk`) picks the ink.
  Under the Docked frame, on the dock's row or on a shared plank the shared sheet stays.
