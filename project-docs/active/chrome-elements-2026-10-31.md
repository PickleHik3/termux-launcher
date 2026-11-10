# Chrome elements as modules

The spec behind ADR 0018. Decided 2026-10-31 with the developer: a page-kind tier in the look
grid (D1), the keyboard shipped lifted on Home pages and Clear on Display pages (D2), the dock's
foot as one glass to the screen's bottom (D3), the command palette and the drawer on the shared
frame as the Sheets element (D4), old Home-only cells re-homed into the Home kind on upgrade (D5),
waves in the order grid → dock → stack → sheets → status bar, each on pong before the next (D6).

## 1. Where the chrome lives today (the map of 2026-10-31)

Read at `9195e7edd`. Line numbers are that commit's.

| Element | Pure policy | Views | Still in `TermuxActivity` | Second copies | Look read at |
|---|---|---|---|---|---|
| Dock | `dock/DockLayoutPolicy` (76 methods), `DockGeometry`, `DockLayout`, `DockPlacement`, `DockRows`, `DockMorph`, `LensBeadGeometry`, `PeekGlassGeometry`; `DockGlassRendering`, `DockStyle`, `PeekGlassReflection`, `PeekRimDrawable` | `DockShelfView`, `DockSurfaceHostLayout`, `dock/WallStripRowView`, `PeekReflectionView`, `DockEdgeGlowView`, `DockLaunchRippleView` | 22 methods: `dockGlassCover` :4852, `buildGlassRefractionEffect` :4866, `peekCardWanted` :5517, `dockFloats` :5544, `resolvePeekCard` :5559, `syncPeekReflection` :5728, `applyDockSurfaceShape` :7283, `shouldShowDecorNavBarSurface` :7703, `ensureDecorNavBarSurfaceOverlay` :7806, `buildDecorNavBarTint` :7995, `resolveDecorNavBarSurfaceHeightPx` :8068, `updateDecorNavBarBackdrop` :8906, `updateAccessoryRenderEffectBackdrop` :9555, `applyDockLayout` :18579, six bead methods :5096–5330 | the decor nav-bar overlay (`mDecorNavBarSurfaceOverlay` :1630, its blur `ImageView` and tint view; kept in step by `SCOPE_NAV_STRIP_BACKDROP` requests and its own ledger); the stack picture (`place/StackPicture`) | `Element.DOCK` at `stackLookPage()` :6421 and `restingPage()` :3655 (one page today, :6589) |
| Status bar | `statusbar/StatusBarEdgeGeometry`, `StatusBarEdgeArrangement`, `StatusRowKeepOut`, `StatusBarResizeGeometry`, `StatusBarFoldMotion`, `StatusBarLensPolicy`, `StatusBarLensMetrics`, `StatusBarInk` | `StatusBarSwipeLayout`, `StatusBarLensView`, `StatusRowLayout`, `StatusBarWidgetView`, `StatusBarStackedClockView`, `PlaceContentStrip`, `StatusBarWindowColumn` | about 30: `applyStatusBarStyle` :6852, `applyStatusBarOutline` :7120, `targetStatusBarHeightPx` :7229, `resolveStatusBarCapsuleCornerRadiusPx` :7221, `applyStatusBarEdge` :16917, `applyTerminalStatusBarSurfaceColor` :4561, `syncStatusBarInk` :31168, the top-bar drag and collapse methods :30000–30343 | the wallpaper twin `terminal_status_bar_wallpaper_backdrop` and its frost twin (`activity_termux.xml:70`, `:132`) | `Element.STATUS` at `statusLookPage()` (`WallpaperFrostPainter:363`; `SeamDress.Surface.STATUS`: the Terminal's look through a slide, its own page at rest) |
| Strip row | `place/StackLayout.RestInput.ownRows` :309, `ExtraKeysColumnGeometry`, `AccessoryStackLayoutPolicy` | `place/EdgeStackView` (`accessory_row_stack`), the row inside the dock's host | `measuredExtraKeysRowPx` :24445, `maybeOfferExtraKeysDefaultRow` :2806, `takeDefaultExtraKeysRow` :2872, `enforceAccessoryFxInvariants` :1831, `resolveAccessoryGlassBaseColor` :4763, `resolveAccessorySurfaceColor` :6216, `applyAccessoryAmbientVeil` :6246 | the stack picture | the dock's glass host (no element of its own) |
| Keyboard | `place/KeyboardOverlayPolicy`, `StackLayout` (`keyboardHeightPx` :58, `keyboardOverlays` :66), `chrome/KeyboardMaterialPolicy`, `chrome/GlassStack.keyboard` :174–188, `terminal/inappkeyboard/KeyboardGeometryChoreographer` (the reveal gate: frame cap :42, 160 ms backstop :48, blocked-frame fail-safe :514) | `terminal/inappkeyboard/TermuxInAppKeyboard` (2,354 lines), `KeyPopupOverlayView`, `FloatingKeyboardController`; the vendored `inapp-keyboard/` module | about 200 declarations mention the keyboard; owners among them: `onSystemImeRequested` :10687, `applyKeyboardSurfaceState` :9992, `completePendingOpenReveal` :9809, `setKeyboardSwipeTravel` :25653, `onKeyboardRevealBegin` :25849 | the stack picture | `Element.KEYBOARD` at `stackLookPage()` (`GlassStack:184`, :8664); `KeyboardMaterialPolicy.hostMaterial` makes an overlay keyboard solid over terminal windows |
| Sheets | `terminal/CommandPaletteHomeOverlay` :82–126 (pure: where the palette sits on Home), `dock/PaletteTilePolicy`; `AppDrawerController`'s policies | `terminal/CommandPaletteView`, `command_palette_glass` / `command_palette_blur` (`RealtimeBlurView`, `activity_termux.xml:1231`) / `command_palette_wallpaper_backdrop`; `app_drawer_glass` / `app_drawer_blur`; `terminal_sheet_glass` | about 20: `openHomePalette` :25426, `applyHomeKeyboardSwipe` :25407, `applyCommandPaletteWallpaperFrost` :10468, `paletteContextHere` :32970, `PaletteHomeHost` :32976 | the live window capture (`RealtimeBlurView`) beside the wallpaper frost still | drawer, terminal sheet, rename chip and bar menus read `Element.SHEETS`; the palette reads none: its colour is the dock's base (`CommandPaletteView:510`), its blur the live window or the frost still by wallpaper mode |

The seams between elements: the bottom stack is dock + strip row + keyboard laid out by
`StackLayout` in one pass (one `DockLayout` per pass; `RestInput.ownRows` reserves the strip row;
the one-resize invariant of `motion-invariants-2026-10-30.md`), translated as one by
`place/Crossing` during a crossing, pictured as one by `StackPicture`, and dressed per half by
`SeamDress` (the stack wears Home's dress while `stackPage == WIDGETS`; since `f2b4cc385` the
Terminal's stack waits below the screen on a Home → Terminal slide and rises after landing). The
status bar wears the Terminal's look through a slide and its own page's at rest. The palette on
Home sits in the room above the dock and raises the keyboard; the keyboard under it wears the
Keyboard element at the Home page's id.

## 2. The target shape

Five packages, one per element: today's `app/dock/` and `app/statusbar/` stay where they are
(D7: no rename wave; the module boundary is the four parts below, not the path), and three new
ones: `app/chrome/strip/`, `app/chrome/keyboard/` (the launcher side; the vendored
`inapp-keyboard/` module stays where it is and keeps its `UPSTREAM.md`), `app/chrome/sheets/`.
Each holds:

- `<Element>Spec`: a value type built from a `<Element>Spec.Input` (insets, the page kind and id
  in front, the crossing state, the look cells resolved by `ElementGlass`, the wallpaper frame's
  rect, the style and design choices) answering geometry, glass and motion. Pure, with a test
  class that pins the numbers the device checks measure.
- `<Element>View`: the one view hierarchy. It takes a Spec and applies it; it never reads a
  preference, never computes a number the Spec answers, never requests a layout from inside
  measure, layout or draw.
- `<Element>Host`: the interface the activity implements. It is the only way the element reaches
  the activity.
- `<Element>Controller`: owns the element's events (taps, drags, reveals), its state across
  recreation, and the redress on a look change.

`ChromeRenderer` becomes the one place that, on a redress request, builds every Spec from the
same input and hands each View its Spec. `SeamDress` and `Crossing` stay in `place/` and answer
the stack's motion; the stack's Specs take their answers as input.

### The dock as one surface (D3)

`DockView` spans from the top of the pinned-apps row (or the Peek card's top) to the screen's
bottom edge, under the navigation inset. Its glass is one `SharedFrameDrawable` of the shared
frame at the Dock element's frost, one tint at the Dock element's opacity, one refraction program
whose rect is the card's with `SEAM_BOTTOM` whenever the surface continues under the pill, and
the Peek reflection above that. The gesture pill draws over it. The decor overlay, its blur
`ImageView`, its tint view, `ensureDecorNavBarSurfaceOverlay`, `updateDecorNavBarBackdrop`,
`buildDecorNavBarTint`, `resolveDecorNavBarSurfaceHeightPx` and `shouldShowDecorNavBarSurface`
are deleted. `DockSpec` answers whether the foot exists, its height (the navigation inset plus the capsule
gap), and the seam flags. The rule for the foot is one for every element (D8): **whoever stands
on the screen's bottom edge extends its own surface under the navigation inset.** With the in-app
keyboard down the dock stands there and owns the foot; with the keyboard up the keyboard does,
and `KeyboardView` extends under the inset with the Keyboard element's glass; the dock's foot is
then not drawn. Nothing owns the band while the system IME is visible. Docked and Floating keep their own formulas (`DockLayoutPolicy`, `DockPlacement`,
`PeekGlassGeometry`); they are inputs to one Spec, not two Specs.

The check that closes this wave: `scratchpad`'s `dockfoot-check.py` step at row 2349 reads under
4 at every opacity on pong (today: +12 at 34).

### The stack (strip row and keyboard)

`StripSpec` owns the row's height (today `measuredExtraKeysRowPx`) and hands it to `StackLayout`
as the one owner; the activity stops measuring it. `KeyboardSpec` wraps `KeyboardOverlayPolicy`,
`KeyboardMaterialPolicy` and `GlassStack.keyboard` into one answer (place, height, material,
glass, reveal), with the choreographer's gate unchanged. The 200 activity declarations that
mention the keyboard are triaged into the host (insets, IME requests, the view it hosts) and the
controller (reveal, swipe, form changes); what is neither is deleted.

### The sheets (D4)

`SheetsSpec` resolves the Sheets element's cells once per redress; the palette, the drawer, the
terminal sheet, the rename chip and the bar menus take their glass from it. `command_palette_blur`
and `app_drawer_blur` (`RealtimeBlurView`) are deleted; `command_palette_wallpaper_backdrop`
becomes the palette's only backdrop, a `SharedFrameDrawable` like every other sheet's. The
palette's colour stops being the dock's base. On Home the palette keeps
`CommandPaletteHomeOverlay`'s geometry.

### The status bar

`StatusBarSpec` gathers the eleven policies' answers for one state; `StatusBarView` is
`StatusBarSwipeLayout` with the lens, the row and the backdrop inside it; the wallpaper twin and
its frost twin in `activity_termux.xml` are deleted and the bar's backdrop is its own
`SharedFrameDrawable`. The thirty activity methods become the host (insets, the camera keep-out,
the page in front) and the controller (drag, collapse, Expand/Compact).

## 3. The look grid's kind tier (D1, D2, D5)

Keys: `look.<property>[.<element>][@<page>|@kind:<home|terminal|display>]`. Cells:
`ELEMENT_ON_PAGE`, `PAGE`, `ELEMENT_ON_KIND`, `KIND`, `ELEMENT`, `BASE`, `SHIPPED`. Read order
element@page → all@page → element@kind → all@kind → element → Base → shipped(kind).

`LookGrid.resolve(property, element, page)` takes the page's kind from the wall store
(`WallPages.kindOf(page)`); `PAGE_WALL` resolves with no kind (Base, then the shipped look for
the Terminal kind, which is today's shipped look). A Display page with no bound window is a
Display-kind page.

Shipped per kind (`SurfacePresets.shippedLook(element, kind)`, the Mist and Clear presets as
they ship, one owner): the keyboard on Home pages is Mist (frost stop 5, opacity 60, grain
follows); on Display pages Clear (frost stop 2, opacity 10); on the Terminal page unchanged.
Every other element and every other property ships the same for every kind. Vibrancy and Margin
stay wall-wide (no kind cell).

A Base cell beats the shipped step, and every install that ever picked a Look holds Base cells,
so a kind's shipped look would never show through (found building wave K). The per-kind looks
are therefore **seeded as real kind cells** (`LookGridKindSeed`, flag `look_grid_kind_seeded`,
the last step of the startup chain, fresh and upgraded installs alike): where the keyboard's
`@kind:home` or `@kind:display` holds no Frost, Opacity or Tint cell, the seed writes the kind's
shipped values; a cell the user holds is never overwritten. The seeded cells show in the editor
with the kind's dot and reset like any other; `Reset` makes the row follow the element and Base
again. The grid's shipped step keeps the same numbers as the last fallback.

The editor's Where chip: Everywhere · Home pages / Terminal / Display pages (the kind of the page
in front) · This page. The row's source dot gains a colour for a kind cell. `Reset this row` at a
kind cell deletes the kind key. `launcherctl look get|set|reset` gain `--kind home|terminal|display`.

Upgrade (`LookGridKindMigration`, flag `look_grid_kind_migrated`, runs after `migrateLookGrid`):
for each element cell at everywhere whose value equals the `_home` entry of the same property
and element in the saved custom preset (`surface_custom_preset`, which the 10-30 fold left in
place), move it to `@kind:home` and delete the everywhere key. Cells with no `_home` twin stay.
On pong this moves the keyboard's opacity 0 / frost 0 / grain 0 / tint 0 to the Home kind; the
Terminal page's `@4` cells are page cells and stay.

## 4. One rest per page kind, held across every crossing (D11, 2026-10-31 evening)

The developer's words: "store the layout of the terminal, keyboard and dock all the time, so
that if the keyboard was up the layout is held even when I swipe to Home and the terminal never
resizes during swipes; the same for the status bar between windows; everything seamlessly
animated, no stutters or snapping; and the dock over the keyboard on Home must be Home's dock."

Today the bottom stack is laid out for the page the wall is moving toward (`StackLayout`'s one
pass per crossing), the keyboard's shown state is one flag for the whole wall, and the terminal
is pre-sized to its rest on every Home → Terminal crossing (7ad038290). That is one resize per
crossing, masked by a frost; the 2026-10-31 recording still shows the stack being rebuilt as the
page changes, and the dock over the keyboard on Home wears the Workspace's design.

**The rule.** Every page kind owns a **rest**: the terminal's grid (rows × columns), whether its
keyboard is up and in which form, the dock's rows and design, the strip row, and the status bar's
state (edge, compact or expanded). A rest changes only by an act on that page (the keyboard
toggled there, a layout edit, a rotation), never by a page change. A crossing animates between
two rests with pictures and translations only; nothing is laid out and nothing is resized while
the wall moves, and the landing finds every element already at its rest. Leaving the Terminal
with its keyboard up and coming back finds it up, the grid unchanged, with no PTY resize at any
point of the round trip.

- `StackLayout.Rest` becomes a stored value per page kind (`RestStore`, in the layout store under
  the page kind), read by the Specs of the dock, the strip row and the keyboard; `StackLayout`
  computes it when a rest input changes and never during a crossing.
- The keyboard's shown state moves from one wall-wide flag into each kind's rest. Home's rest
  carries its own (the palette raises it; a swipe to Terminal does not lower Terminal's).
- The terminal's PTY size is settled from the Terminal kind's rest alone. The motion invariant
  `one-resize-per-crossing` becomes **`no-resize-per-crossing`**: a `pty-resize` line between a
  `crossing-begin` and its `land` is a violation.
- The status bar's state is part of its kind's rest; a crossing between kinds whose bars differ
  morphs the bar along the slide (`StatusBarFoldMotion`), never at the landing.
- The dock over the keyboard on Home is Home's dock: `DockSpec` takes the page kind and the
  keyboard state as inputs and answers Home's design compacted, never the Workspace's tray. The
  same holds for every (kind × keyboard up/down × style × design × orientation) combination.

Two more sightings from pong (2026-10-31 night) belong to this rule: on every page change, Home 1
↔ Home 2 included, the widgets move a few dp down and back (a recording shows the arriving page's
widget cards about 5 px off their rest mid-slide, the status bar still: `~/reports/device-checks-2026-10-31/`,
`tl-h12.mp4` in the session scratchpad); and a Home page with no layout keys of its own wore the
shipped tray instead of Home's Peek (a page variant's absent key fell to the default, not to the
Home kind's layout; fixed as its own commit). A crossing between two Home pages moves nothing
vertically: that is a fixture pair too.

**How it is kept true.** `LayoutFixtures` (wave M) grows a matrix over page kind × keyboard
up/down × keyboard form × dock style × design × status state × orientation; for each cell the
parity tests assert that the rest is the only source of the stack's height, that every crossing
between two cells answers zero resizes and zero layouts, and that each element's dress is its
kind's. On the device, `motion-invariants.sh` with the Motion log on over a round trip of every
kind pair must report none.

**Waves.** This is wave S's scope (the stack) plus a slice of wave B (the bar's state in the
rest). Wave D's `DockSpec` takes the kind and keyboard inputs from the start so the dock over the
keyboard on Home comes right with it.

## 5. The redress path (ADR 0018 §2)

One request: `ChromeRenderer.requestRedress(reason)`; one pass: every Spec rebuilt from one
input, every View handed its Spec. Writers: the editor (every row write and Done), Settings
rows, `launcherctl look` (today's `TerminalActionDispatcher.redressChrome` becomes this
request), the migrations at startup, a wallpaper change. No View caches a resolved look across
requests; a cache of baked pixels (the blur frames, a still) is keyed by the Spec values it was
baked from.

## 6. Waves (D6)

Each wave is one worker on one seam, cut from the previous wave's merge, with its Spec tests, and
lands on pong before the next starts. Device checks go on `project-docs/device-checks.md`; hand
checks on `developer-checks.md`.

| Wave | Delivers | Closes |
|---|---|---|
| K (grid kind tier) | `LookGrid` cells and read order, `WallPages.kindOf`, `SurfacePresets.shippedLook(stop, kind)` with the keyboard's Home and Display looks, `LookGridKindMigration`, `ElementGlass` taking the kind, the Where chip's kind option, `launcherctl look --kind`, docs | the transparent keyboard under the palette on Home; a Display page reading the Terminal's cells |
| D (dock) | `app/chrome/dock/` with `DockSpec`, `DockView` spanning to the screen's bottom, `DockHost`, `DockController`; the decor overlay deleted; the dock's 22 activity methods gone | the seam at row 2349 (`dockfoot-check.py` green); one owner for the dock's look |
| S (stack) | `StripSpec`, `KeyboardSpec`, the keyboard's host and controller; `StackLayout` fed by the Specs; the keyboard's activity declarations triaged | one owner for the strip row's height; the keyboard's glass per kind on screen |
| H (sheets) | `SheetsSpec`; the palette and drawer on the shared frame; the live blur views deleted; the palette's colour from the Sheets cells | one blur source; the palette's look editable as a sheet |
| B (status bar) | `StatusBarSpec`, `StatusBarView` with its own backdrop, host and controller; the twins deleted; the thirty activity methods gone | one surface for the bar |
| R (redress) | `ChromeRenderer.requestRedress` as the one path; the dispatcher's `redressChrome` routed through it; `MotionLog` lines for a redress | no reader caches a look |

Not in this round: the terminal canvas and the widgets (already elements with one owner each),
the editor's cards (they read the Specs' inputs, unchanged), the wall and its crossings
(`place/`, ADR 0016), the keyboard-over-display override of 2026-10-29 (it becomes a Display-kind
keyboard cell set by the editor, which wave K makes possible; its PixelCopy still of the display
is a later wave).

## 7. Open points settled on 2026-10-31

- D1 the kind tier, over seeding page cells at creation: a kind-wide change is one write.
- D2 keyboard Home lifted (Mist) / Display Clear (the Clear preset, frost stop 2); other elements one look per kind for now.
- D10 per-kind shipped looks are seeded as kind cells (a Base cell would otherwise hide them).
- D11 one rest per page kind, held across every crossing; the invariant becomes no-resize-per-crossing; the dock over the keyboard on Home is Home's dock.
- D12 (wave D, 2026-10-31 night) the activity's root stops fitting the bottom inset; the content keeps the band clear through its own margin and the dock's surface alone reaches the screen's bottom edge; one pure owner answers how the inset splits, with a parity test. No negative margins.
- D13 the decor strip overlay survives wave D for the keyboard-up band only (the keyboard's foot, D8) and is deleted in wave S when `KeyboardView` reaches under the inset itself.
- D14 Floating + Peek on a Home page with the keyboard up: the Peek card stands on the keyboard (floats=false, its bottom edge a seam into the keyboard's glass, the capsule gap gone there); Home's keyboard-up rest height changes by the gap, the Terminal page's rest does not.
- D3 one glass to the bottom; the Floating no-foot rule withdrawn.
- D4 sheets on the shared frame; the live window is no longer seen through a sheet.
- D5 re-home: everywhere cells that equal the saved `_home` values move to the Home kind.
- D6 waves K, D, S, H, B, R in that order (K cut 2026-10-31 evening).
- D7 existing packages stay; no rename wave.
- D8 the foot belongs to whichever element stands on the screen's bottom edge.
- D9 the 10-29 "Over the display" switch and its display-scoped keys are not built: the
  keyboard's look over a display, overlay and resize alike, is the Keyboard card with Where =
  Display pages. The blurred still of the display behind the keyboard (that spec's decision 5)
  stays a later wave.
- D15 (2026-11-10, amends D6) the remaining waves run in the order D part two, B, H, R, S. The
  stack wave goes last because `feat/space-places-dock` rewrites the same keyboard code and
  merges on the developer's go before S is cut; until S, the keyboard joins the redress path
  through today's route.
- D16 (2026-11-10, amends D6) a wave is gated by the unit suite and an emulator check, not by
  pong; pong sees the stacked waves once, at the end, with every owed check in one session.
- Wave H is smaller than written above: ADR 0021 (2026-11-03) already deleted the live blur
  views and put panels on a snapshot or a live recording. What is left for H is `SheetsSpec` and
  the palette's colour from the Sheets cells.
