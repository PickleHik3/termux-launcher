# Home dock styles: ledger

Rolling status for [`home-dock-styles-2026-10-20.md`](home-dock-styles-2026-10-20.md). Newest
first within each section. Delete this file with the spec when the work is delivered.

## Status

| Step | State | Commit | Notes |
|---|---|---|---|
| 1 Dock style pref + picker | built, emulator-checked (passes 1–5), on pong for taste | f8ed2e66b | Picker in the Layout editor's Row B while Home's dock is selected; `DockStyle.BUILT` gates unbuilt styles to Glass tray. |
| 2 Glass tray + shared glass | built, emulator-checked (passes 1–5), on pong for taste | e2da84a2e | Shelf, soft rim, quarter-size icon reflection captured on change. Open: 9 dp slab jump at the Home/Workspace settle; reflection pops off/on during page swipes; no dash (cue) with one dock page. |
| 3 Lens beads | built, emulator-checked (passes 1–5), on pong for taste | eae04e066 | One glass program draws every bead (nearest-bead SDF from uniforms, no per-bead RenderNode); the host clips its children to the beads, so below Android 13 they are plain frost. Minimised Home float stays today's chip (not a bead yet). |
| Emulator pass 1 | done; polish round merged (beads ride their icons, reflection fades, one cue, editor Undo fixed, drawer regions), unchecked on device | 8c9ea69f9 | Pass: picker (Row B, font 1.3 tight), Undo/persist, tray padding, no slab jump at the seam, live tray⇄beads, smooth bead edges, no shader/capture errors. Fix: beads stay put while icons slide on a page swipe; dashes stacked on the pull handle; end beads touch the screen edge; drawer could not be opened under injection (cause unknown); editor pill shifts when Undo appears. | | |
| 4a Taskbar + run strip | built, emulator-checked (passes 1–5), on pong for taste | 0e19815bc | In `DockStyle.BUILT`. Home tile (drawer glyph, apps' size) takes one of the row's slots, start/middle/end by long-press drag (`layout.home.taskbar_home_tile`, absent = start), still during finger page swipes. Run strip on its own frost card above the dock and its dashes, where the float chips stood (they are off under this style); built on Home from the window chips; eases in and out. Reflection at 0.6 of the tray's. Prompt line's field hides under the open palette in bf8032e46. |
| 4b Prompt line | built, emulator-checked (passes 1–5), on pong for taste | ba9a96bf4 | In `DockStyle.BUILT`. One taller slab: apps row, then the 48 dp field on its well (14 dp side inset, air over the icons = air under the well, well radius = dock radius − that air); tap opens the palette with keyboard and caret; Enter runs the top result (read, unchanged); a style switch eases the slab 240 ms. Lens beads row inset 9 dp a side in a054ad056. |
| 4c Wall strip | built, emulator-checked (passes 1–5), on pong for taste | 2b9a5d86a | In `DockStyle.BUILT` (all five now). One taller slab: the 84 dp `WallStripRowView` over the apps row in the row's own host (dashes still above the slab), the air over it = the air under the icons, tiles nest by it; side inset read off the first laid-out icon (14 dp inside the slab without one). Fed from the same chips as the run strip (row windows and the display window, split shapes from `paneCellGeometry`); tiles slide/resize ~420 ms on open/close; tile tap = Workspace in on it, `+` = new row window, long-press = a small menu (no chip menu exists). Reflection lower bevel only at 0.8. The clock ticks alone (tiles in their own RenderNode on API 29+). Style switches ease both bands at once (240 ms). |
| Emulator pass 2 | done; fix round 2 merged, unchecked on device | 36700127f | Fixed: Lens beads fell back to the slab (row icons wider than a bead's slot, 4ec3b24b1); seam step of the Prompt line / Wall strip bands (36700127f); run strip square glass corners (2c699bc42); empty strip under the palette (b7bfbd122); Wall strip lists floats (622951639). Not changed: equal padding measures equal on pass 2's frames (21/21 px Floating); partial-page centring (see decisions); the landing's frosted frame is the terminal's reflow frost (predates). |
| Emulator pass 3 | done; final fix round merged (editor Done keyboard, chips, minimised mark, +), unchecked on device | 808f7778d | Pass: Lens beads render and ride their icons, no visible seam step (screenshot-rate), run strip corners and glyphs, Prompt line under the palette and light well, Wall strip floats/font 1.5/3–6 tiles/attention, tray air equal within 1 px (view bounds), live switching. Fix: keyboard flash and tall dock on editor Done; minimised float chip still above the dock under Taskbar and Wall strip; run strip minimised mark missing; Taskbar + tiny at font 1.5. Known: shells idle in ~ are all named "home" (numbers tell them apart); Lens beads draw the tray while the A–Z row is on (by design). |
| 5 Pong install | done 2026-10-21 06:2x, `1.1.0-pre.6+dev.120d6f511` (release), launcher up, crash buffer empty | 120d6f511 | Emulator checks after the last fixes (run strip placement, editor Done, chips, marks) all passed; full suite green at 120d6f511. Taste and frame-time checks owed on pong (device-checks.md, Developer). |

## Side fixes

| Fix | State | Commit |
|---|---|---|
| Drawer: pull down on Suggested's empty pane closed the drawer, now closes the folder | merged, unchecked on device | ba6144443 |
| Pong 10-21: Workspace dock malformed after Home → Workspace (a second, unfrosted frame along the slab's bottom). The settle's chrome pass moved the glass host's top up by the dashes band and cut the backdrop and the glass from the host's old height in the same pass; the host was not among the views whose resize re-runs the pass, so the glass stayed a band short. The host's resize now books the pass; leaving beads also drops the moving beads, sideways cut and shelf beads | built, unit-tested, unchecked on device | 2742a62bc |
| Pong 10-21: clunky Lens beads ⇄ Workspace hand-over. The beads now melt into the slab with Home's share of the slide (they swell under their icons, cut to the slab's outline, covering it by the last 15 %), and come back out of it; one effect rebuild a frame while the share moves | built, unit-tested, unchecked on device | 604fd8558 |
| Pong 10-21 / emulator pass 6: keyboard and dock sank and came back on a slow swipe to the previous Workspace window (not the next). The chrome's travel (PlaceChromeTravel.at) read the wall's offset against the row's places only, so the offset of a drag toward the previous window read as a slide toward Home, the place on the left; toward the next window there is no place on the right, so it was clean. Predates the dock work: since 3990085b4 (10-17, one row of Home pages and windows) windows are inner pages of the Workspace's page; a47df0ac3 reads the same. The frame now asks the wall whether the page coming in is a window of the current page's own (PaneWallController.showsInnerPage, the rule the parallax already used) and stays at rest then, so Home's share (bands, bead melt, dock rows) is 0 on every window-to-window frame | built, unit-tested (WindowSlideChromeTravelTest), unchecked on device | 2abea4147 |
| Emulator pass 6: Lens beads, Home → Workspace slowly: the beads sank still as beads and were cut by a flat line (the stack's travel clip at its laid-out bottom), then a faint pale line lingered there before the Workspace's dock rose. They melted by Home's share of the chrome's way, covered only by its last 15 %, long after Home's rows (a dip) had sunk. Where Home's rows leave the screen (a dip or a slide out) the beads now melt over the wall's first 8 % (PlaceChromeTravel.LANDING), while the chrome still rests, and hand over to the slab before the stack moves, so the slab sinks as it rises on the way back; the beads' own shadows and glow (DockShelfView) fade by the melt and are gone once they cover the slab. Where the rows stay on screen (same rows on both halves) and on the way back, the melt is unchanged, except that the bead shadows now fade in as the beads shrink out of the slab | built, unit-tested, unchecked on device | 49e41cd56 |

## Known issues

Open, newest first. Move an item out when it is fixed (with its commit) or decided.

- 2026-10-21 (emulator, 5568477bd) · Lens beads, Home → Workspace: faint ghost icons may linger
  in the slab in the last frames of the slide (seen at ~8 fps capture). To be judged on pong.

- Home ⇄ Workspace settle under a style with dashes above the slab: the slab's top still steps by
  the dashes band (6.5 dp) at the settle; the beads' melt hides the beads → slab change, not that.
- A dock page with fewer apps than slots stays left-aligned: the slot is the grid column in every
  drag, swipe and bead calculation, so centring needs an offset threaded through all of them.
- Idle shells are all titled "home", so run strip entries and Wall strip tiles tell them apart
  only by their numbers.
- Lens beads draw the Glass tray while the A–Z row shares the dock's glass (by design: another
  band on the bead glass would lose its glass).
- The drawer opens from the dock only past half its travel or on a fling; a slow finger pull
  cannot commit because Home's dock leaves too little screen below it. Predates the dock work.
- A "Windows restored from before the restart" toast overlaps the top of Home's clock after a
  relaunch. Predates the dock work.
- The ~110 ms frosted frame on the terminal at a Workspace landing (`TerminalView.frostReflow`)
  predates the dock work and shows under every style.

## Device checks

Owed checks also go into [`../device-checks.md`](../device-checks.md) in its usual form; this list
records what each pass found.

## Decisions taken without the developer

The developer delegated decisions for this run (2026-10-20, overnight). Each is recorded here with
its reason so it can be reversed.

- 2026-10-20 · The picker lives in the Layout editor's Row B, shown while Home's dock is selected
  (as the keyboard's tools are), rather than as a new settings row: it keeps the fragile settings
  pages untouched and puts the choice next to the dock it changes. Its name, "Dock style", sits
  near the older Docked/Floating "Style"; revisit if the two read as one.
- 2026-10-20 · Living glass does not tilt the dock, so the dock's icon reflection does not shift
  with tilt: wiring it would rebuild the dock's effect every frame of a press. Kept static.
- 2026-10-20 · Lens beads fall back to the tray's slab where the apps row is not alone on the
  dock's glass (the A–Z row on the dock, the keyboard sharing the glass, a joined Docked frame)
  or holds more than eight icons: the beads are one glass program over the row, and another band
  on the same glass would lose its glass.
- 2026-10-20 · A minimised Home float stays today's chip above the dock under Lens beads rather
  than a sixth bead at the row's end: the row's slots are the pinned apps' own, and a bead for
  the float needs a slot the row does not have. Revisit with the Taskbar's run strip.
- 2026-10-20 · Pass 1 fixes. Lens beads ride under their icons during a dock page swipe (and
  the rarer slide-and-fade switch, where they shrink with the fade) rather than easing into the
  slab: a bead carried with its icon reads as one physical lens, and a morph to the slab would
  cost the same per-frame work plus a shape change. Each frame of a swipe rebuilds the dock's
  effect once; the dock's chain has no blur of its own (the frost is pre-blurred), so that is one
  pass of the glass program over the dock, and the shadows' paints are kept. Nothing runs at
  rest. The program reads twelve beads so the neighbouring page's fit beside a full row.
- 2026-10-20 · Not done: beads kept 12 dp from the screen edges. At the default seven icons a
  page on a 411 dp phone the row's slots are 154 px apart with the first centre 77 px in; a 12 dp
  (31.5 px) margin leaves a 91 px bead under a 120 px icon (0.76x), under the 1.08x floor, so every
  default install would fall back to the slab. Doing it needs the row itself inset under Lens
  beads (about 9 dp a side fits seven at about 1.1x with 12 dp margins); the developer's call.
- 2026-10-20 · Home's grabber pill is not drawn while the dock's page dashes show (two or more
  pages): the dashes are the cue then, as the spec says. Its swipe zone is unchanged.
- 2026-10-20 · Lens beads: the apps row gets about 9 dp more side inset so seven beads fit at
  ~1.1x with 12 dp from the screen edges; without it every default install (7 icons on 411 dp)
  would fall back to the slab. Done in a054ad056 (`LensBeadGeometry.ROW_SIDE_INSET_DP`, applied
  only under Lens beads, also where they fall back to the slab): the row's slots are even from
  its own edges, so an end bead's margin is the inset plus half the 6 dp gap, 12 dp at least on
  every row; seven 46 dp icons on 411 dp get 50 dp beads (1.1x). No 12 dp clamp was added to
  `LensBeadGeometry.compute` itself, so a narrower phone keeps drawing beads rather than
  falling back. The "Not done" entry above is superseded.
- 2026-10-20 · Prompt line: the field stands in the apps row's own host, under the row, so the
  dock's one glass, shelf, rim and outline are the taller slab's with no second surface. It is
  carried only by the dock's own bottom row; a row moved to another edge or a rail has no field.
  It is not hidden while the palette it opened is up; whether the palette covers it or it reads
  as a second search field over the keyboard is for the emulator pass to judge.
- 2026-10-20 · Prompt line: the slab eases only for a style picked in the Layout editor (240 ms,
  one dock pass and one glass re-cut per frame, nothing at rest); a Home ⇄ Workspace settle
  lands the field at once, as every other piece of the arriving place's geometry (ADR 0003).
  The content's insets follow the eased height each frame. To be judged for jank on pong.
- 2026-10-20 · Prompt line: the icon reflection shows only in the lower bevel under the field,
  aimed as the tray's lower bevel would show it (the picture moved down by the field's band) at
  0.8 of the tray's strength; the faint face mirror and the squeezed top-bevel copy are not drawn,
  since over the field and the icons they would read as a ghost row. Under Docked the dock's
  corners are square, so the well's are too (the nesting rule gives 0).
- 2026-10-20 · The drawer opening from the dock needs half its travel or a fast fling; a slow
  real-finger pull on Home cannot commit because Home's dock leaves too little screen below it.
  Pre-existing, not from the dock work; left for the developer to judge (noted, not changed).
- 2026-10-20 · Lens beads rebuild the dock's effect once per frame during a dock page swipe so the
  beads ride their icons (one shader pass over the already-blurred frost, nothing at rest). To be
  judged for jank on pong.
- 2026-10-21 · Taskbar: the run strip floats above the dock's page dashes, not between them and
  the slab, and the dashes stay the cue. The dashes keep hugging the slab as every style's do (the
  shared rule), so the swipe-up cue sits where it sits on every other dock; the strip takes the
  minimised floats' chips' place and gives the floats' room the same way. Taking the dashes'
  place would hide the page count while anything runs, which is most of the time.
- 2026-10-21 · Taskbar: the Home tile takes one of the row's slots rather than adding one, so the
  tiles stay the tray's size (with 7 slots a page holds 6 apps). It stands at the same slot on
  every page and holds still while a finger swipes the pages; a committed page switch's slide is
  countered, its fade is not.
- 2026-10-21 · Taskbar: the lit entry on Home is the focused Home float, else the window the
  Workspace has in front; nothing on Home acknowledges a row window's attention, since nobody is
  looking at it from there.
- 2026-10-21 · Taskbar: the strip's glass is the minimised chips' recipe (the dock's material at
  the dock's values, a card all round) with its own ring and no foot, radius 16 dp (an entry's
  9 dp and the 7 dp around it); the "dim bevel" is the absence of the shelf, the lip and the soft
  rim band. The dock's icon reflection is scaled by 0.6 (face under 3 %, lower bevel rim ~18 %).
- 2026-10-21 · Taskbar: with no pins the row shows the "pin your apps" hint and no Home tile; a
  minimised float's flight under the Taskbar has no chip to land on (the chips are off), so it
  takes whatever the flight does without a target. Both left for the emulator pass.
- 2026-10-21 · Prompt line: the field fades out under the open palette (it was a second search
  box over the keyboard) and back when the palette closes; it keeps its band, so the slab does
  not move.
- 2026-10-21 · Wall strip: the window chips have no menu (a chip's long press lifts it to
  move), so a tile's long press opens a small menu of what the chips do: Switch to window, Move
  window left / right, Close window (existing `tool_window_*` strings). Close on Home calls the
  row's own close (`closeRowWindow`, asking first when something runs), which the window bar
  itself refuses on Home; to be judged on the emulator.
- 2026-10-21 · Wall strip: `+` opens a Workspace row window and brings the Workspace in on it,
  never a Home float (the Taskbar's `+` follows Home's float rule): the row shows the
  Workspace's windows, so a new one belongs there. The display window gets a tile at its place,
  as in the run strip, drawn as one whole pane.
- 2026-10-21 · Wall strip: the row's side inset is read off the apps row's first laid-out icon
  and its column (slots are equal shares of the row), so the clock lines up with the first icon
  and `+` ends where the last icon ends at every icon count and Style; the spec's 14 dp inside
  the slab is the fallback while no icon is laid out (an empty dock).
- 2026-10-21 · Wall strip: the slab eases with the Prompt line's 240 ms ease (one animator now
  carries both bands, so Prompt line ⇄ Wall strip changes the slab once); the row fades in once
  the slab has room, as the field does. A Home ⇄ Workspace settle lands the row at once (ADR
  0003), so the dock steps by the row's band (~100 dp) at the settle; to be judged on pong.
- 2026-10-21 · Wall strip: the clock's minute redraws the row view only; its tiles stand in a
  RenderNode of their own (API 29+) recorded again only when they change, so the tick re-records
  the clock alone. The dock's glass and reflection are not touched by it.
- 2026-10-21 · Pass 2, Lens beads: the cause was the row's icon size, not the A–Z row. Since
  the 9 dp inset the dock's own icons (about 51 dp at 411 dp, held only to the slot less 4 dp)
  were wider than a 56 dp slot leaves a bead with its 6 dp gap, so `LensBeadGeometry.compute`
  answered null on every default install. Under Lens beads the row now holds its icons to
  `LensBeadGeometry.fitIconPx` (a bead of 1.1x with its gap): the icons are about 45 dp, a little
  smaller than the tray's. The other way, keeping the icons and dropping the inset, puts the end
  beads on the screen's edge again (pass 1's finding).
- 2026-10-21 · Pass 2, seam: Home's own bands (the Prompt line's field, the Wall strip's row)
  follow the page across the seam rather than landing at the settle: their reveal is Home's share
  of the slide, so the slab eases with the page. This reverses the 10-20 note that a settle lands
  the field at once (ADR 0003): the band is Home's furniture leaving with Home, not the arriving
  place's geometry. One dock pass and one glass re-cut a frame while the share moves, the content
  held as the travel holds it; to be judged for jank on pong. The Taskbar's run strip already
  rides in Home's float layer, so it leaves and returns with the page; nothing changed there.
- 2026-10-21 · Pass 2, Prompt line under the palette: the slab eases to the tray's height (the
  field fading with its band) and back when the palette closes, rather than keeping an empty
  well. That is also what the Glass tray does under the palette (it stays the tray), so both
  readings of the finding give the same dock. Supersedes the 10-21 "keeps its band" note.
- 2026-10-21 · Pass 2, Wall strip floats: Home's floats are tiles after the row's windows, fed
  through the existing `RunStripModel` entries (the Home-float kind) and `WallStripRowView.tilesOf`,
  which takes every entry but `+`. A float tile's tap is `showHomeFloat`; its long press has no
  menu. Both halves merged on 10-21; the feed calls `tilesOf(RunStripModel.wallEntries(entries), shapes, keys)`.
- 2026-10-21 · Pass 2, not done: centring a partial dock page (six apps in seven slots). The
  row's slot is its grid column everywhere: the drag's hover and drop (x over the slot width),
  the swipe's staged page and the beads' neighbours (`drawnNeighbourIconCentersX`), the Taskbar
  tile's slots and the Wall strip's inset (read off the first icon's column). A centred page puts
  icons half a slot off their columns, so each of those needs the page's lead offset threaded
  through; not clean in this pass. The developer's call whether it is worth that change.
- 2026-10-21 · Pass 2, equal padding: pass 2's own frames measure equal, Floating tray 21 px
  above and below the icon (slab 2159–2321, disc 2180–2300 on the 2400 px screen), Docked 22 px
  above and 22 px to where a 24 dp gesture inset would start; the crop the finding was read from
  ends 3 px under the slab. No change; re-measured from `uiautomator` bounds as a device check.
- 2026-10-21 · Pass 2, the frosted frame at the landing: `TerminalView.frostReflow` (110 ms of
  blur over the grid when the settle's resize reflows it, 356e6ca81, 2026-09-27) predates the dock
  work and runs under every dock style. The dock's settle restyle (`restyleDockSurface`, from the
  page commit inside the settle) runs before the travel's displacement settles, so by code it
  joins the same reflow rather than adding one; not confirmed on a device. Not changed.
- 2026-10-21 · Pass 3, the Done flash predates the dock work: a Done on a directly opened
  editor page grew the frame back to full size (`AppearanceEditorFrame.hide`, the live root
  scaled) with the keyboard the editor raises for its session still up, and took it down only in
  `finishSession`, after the frame arrived. `SurfaceEditorController`, `AppearanceEditorFrame`
  and `HomeChromePolicy` are unchanged since a47df0ac3; no dock restyle or band ease runs on
  that path. The dock "going tall" was the dock standing over the keyboard (the Wall strip's
  band makes it read taller). Fixed small: the session's end lowers that keyboard as the exit
  starts (`lowerRaisedKeyboardAtEnd`, the same hide the Icons switch already does under a
  scaled frame), keeping one Home's always-on keyboard wants (`isEndingSession` lets the host's
  keep rule apply). The Overview path is unchanged.
- 2026-10-21 · Pass 3, float chips: by code the Taskbar's chips were already stood down on
  every path (the chips' one fill, `fillHomeFloatChips`, serves every sync and the minimise's
  target ask); no Taskbar path that re-adds one was found, and pass 3's Taskbar frame shows
  none. The Wall strip's were never stood down. Both now share one gate
  (`homeFloatChipsCarriedByDock`): the run strip or the Wall strip's row, each fed only with
  split panes on, so where neither feeds the floats the chips still do and no float is lost.
- 2026-10-21 · Pass 3, run strip: the minimised mark has fixed room on every minimised entry and
  draws after the badge on a numbers-only entry (it was tied to the label, which a strip of six
  drops). It is 8 dp and wears the badges' opaque ink. The `+` is a stroked cross at 0.7 of the
  label's text size, held to 0.6 of its badge, on a resting badge like a number's: it follows
  the font scale as the labels do (the numbers stay capped to their 20 dp box).
