# Terl: surfaces, controls and what the redesign does to them (2026-11-27)

Research note for redefining Settings and the Appearance editor under Terl. Facts only; recommendations are
absent on purpose. Nothing was built or changed.

## Sources and short forms

| Short | File |
|---|---|
| SPEC | `/home/amalvajdan/Projects/termux-launcher/app/wt-tiling-docs/project-docs/active/tiling-desktop-2026-11-27.md` |
| A37 / A38 / A39 | `/home/amalvajdan/Projects/termux-launcher/app/wt-tiling-docs/docs/adr/0037-every-shell-is-a-window.md`, `0038-one-control-bar.md`, `0039-terl.md` |
| GL | `/home/amalvajdan/Projects/termux-launcher/app/wt-tiling-docs/docs/GLOSSARY.md` ("Tiling desktop" notes) |
| PROTO | `/home/amalvajdan/Projects/termux-launcher/app/termux-launcher/.lavish/terl-prototype/index.html` (1108 lines; CSS 8-281, markup 283-390, script 391-1108) |
| INV | `/home/amalvajdan/Projects/termux-launcher/app/termux-launcher/docs/research/settings-and-appearance-inventory.md` (based on `a3d964e74`) |
| GLO | `/home/amalvajdan/Projects/termux-launcher/app/termux-launcher/docs/GLOSSARY.md` (today's words) |

Status of the design inputs: SPEC and ADRs are "decided 2026-11-27, not built"; SPEC lists as still open the Super
glyph, the look of the Workspace list / navigation mode / Overview, and gap/resize hit sizes and the border-hold delay
(SPEC:162-167). The prototype is a throwaway HTML mock (phone 412x915 CSS px, PROTO:393) and fixes numbers the spec does
not (cited below as "PROTO draws").

---

## 1. Today's surfaces

"Controlled by" = the Appearance card (INV 2.1-2.3) and/or Settings page (INV 1.2, 1.5) that holds its looks and
options today. "Look properties" = glass/colour/geometry values that exist for it. The Look grid properties are Opacity,
Frost (blur), Grain, Tint (+hue), Corners, Margin (edge gap), Depth, Shine, Vibrancy; only Opacity and Frost may be a
surface's own value, everything else follows Global (INV:702-705, 779-790).

### 1a. Chrome around the page

| # | Surface | What it is | Controlled by today | Look properties |
|---|---|---|---|---|
| 1 | Status bar (strip) | Launcher's top bar of clock, readings, pinned notifications, marks line, window chips (GLO "Chrome", "Marks line", "Pinned notification") | Appearance > Status bar card; Settings > Terminal > Status bar (`status_bar_preferences.xml`, INV:220-232) | own Opacity + Frost; Global Tint/Corners/Margin; slot STATUS (INV:704, 785); stored corner -1 = follow style, 26 dp cap (INV:708) |
| 2 | Status bar expanded form | Second form unfolded by the status swipe on the top border (GLO "Status swipe") | same card; layout `status_compact`, `status_edge` top/bottom/left/right/hidden (INV:994-995) | as row 1 |
| 3 | Status clock | Clock in the bar: faces Analog, Numerals, Slab, Flip, LCD, LED matrix, Tape, Minimal (GLO "Clock face") | Status card: face `top_pane_clock_style`, position `top_pane_clock_alignment`, 12h `top_pane_clock_am_pm`, seconds `status_clock_seconds`, shown `status_clock_shown` (INV:773-778) | face, alignment, format |
| 4 | Status readings | CPU, GPU, RAM, battery, media, weather (+Fahrenheit, location), notifications, text-size button; order `status_row_order` | Status card nine "Elements" tiles (INV:769-772); Settings > Status bar > Weather location (INV:221-224) | per-reading on/off, order |
| 5 | Pinned notifications | Notification held as a card in the status bar's widget slot by user rules (GLO "Pinned notification") | Settings > Notifications > Pinned notifications, Notification history (INV:473-484) | none of its own (wears status glass) |
| 6 | Marks line / page bar look | Page marks (dot, numbered square, screen, spark, Overview two cards, app icons) in the status bar on Terminal pages; tap = go, slide = scrub, title at row end (GLO "Marks", "Title") | Status card "Page bar" Glyphs/Marks `page_bar_look` (INV:769) | accent for current, dim for others; no glass of its own |
| 7 | Window chips | Status bar chip per window / Home float (GLO "Window chip") | none separate (status card) | follows status |
| 8 | Camera cutout handling | Status bar keeps clear of the camera: mode, Margin, Move sideways/down, Size | Settings > Terminal > Status bar "Camera cutout" (INV:225-232, rule INV:96) | geometry only |
| 9 | Dock: Docked | Bars join flush into one frame of glass, pane a rounded insert over it; square at rest (GLO "Style"; AGENTS.md) | Appearance Global "Layout style" `app_launcher_dock_style`, Dock card (INV:733, 757) | own Opacity + Frost; Corners auto = 0 when Docked (INV:708) |
| 10 | Dock: Floating | Each piece a rounded card with air (GLO "Style") | same | Corners auto = 20 dp Floating (INV:708) |
| 11 | Dock designs | Tray (default), Beads, Strip; also `peek_glass`, `lens_beads`, `taskbar`, `prompt_line`, `wall_strip` in code; Reflection slider for Peek glass | Dock card "Dock type" `dock_design`, Reflection `dock_peek_reflection` (INV:759-760, 995); GLO "Dock design" | design-specific (bead glass, strip height 56-128 dp) |
| 12 | Apps row / dock items | Pinned apps (count 7) plus Drawer tile, Search field, New terminal, Float terminal, Command palette, Most-used page | Dock card chips (INV:759-762); Settings > Apps > Pinned apps, Most-used apps page (INV:160-170) | dock glass; icon size s/m/l/xl; monochrome icons, icon pack |
| 13 | A-Z strip / index | Letter scrubber on an edge or in tray; jumps app list | Dock card sub-card "A-Z strip" (Glass background switch, Show, Opacity/Frost, `az_show`, `az_glass_background`); Settings > Home > Double-tap-to-lock A-Z row (INV:763, 153-158) | own glass option |
| 14 | Extra keys row | Esc/Tab/Ctrl/arrows row in the dock, Terminal only | Dock card "Extra keys" sub-card; Settings > Keyboard > Extra keys editor, Uppercase labels (INV:764, 388-405) | Opacity/Frost (`extrakeys_blur_radius`) |
| 15 | Home pill | Home's extra keys as a floating glass pill (GLO "Home pill") | Extra keys editor; layout keys `bar_order` | glass of its own unclear |
| 16 | In-app keyboard | Built-in keyboard (Unexpected-Keyboard fork), forms docked, floating, split; voice dictation pill | Appearance Keyboard card (INV:765-768, 2.3 row "Keyboard"); Settings > Keyboard (layouts, typing, voice, hardware) (INV:388-472) | own Opacity + Frost, key opacity, key spacing/corners, bottom padding, floating width/height, split gap, theme; Corners follow Global |
| 17 | Page switcher key | Optional extra-keys key whose cap shows the current page glyph and pops the page bar (GLO "Page switcher") | Extra keys editor | glyph |
| 18 | Nav strip | The under-pill strip, mirrors the dock or keyboard under the gesture line (`ChromeRenderer.java:24`; `TermuxActivity.java:25091`: "always takes the default" frost; AGENTS.md "which surface the nav strip mirrors") | no card or page; renders from `ChromeRenderer` | takes default frost |
| 19 | Corner tab | Control strip revealed by holding a page's corner (Appearance, Layout, Wallpaper, Minimal, Settings, help, Home; on a split pane Close, Move, Maximise) (GLO "Corner tab") | none | glass of sheets |
| 20 | Grabber | Small pill on a page's top/bottom border marking keyboard swipe / status swipe (GLO "Grabber") | none | accent glow |
| 21 | Minimal mode | Second saved Terminal layout hiding bars, A-Z, extra keys, keyboard (GLO "Minimal mode") | Appearance Terminal card chip `place.minimal`; corner tab; palette | none |
| 22 | Notices | One transient message pill at top centre, inside the terminal rim; read-outs (GLO "Notice", "Read-out") | none (wears Sheets glass) | Sheets look |

### 1b. Terminal, windows and Linux apps

| # | Surface | What it is | Controlled by today | Look properties |
|---|---|---|---|---|
| 23 | Terminal panes (canvas) | Text view in a window; slot CANVAS | Appearance Terminal card; Settings > Terminal (text, fonts, TUI themes, clipboard, engine) (INV:199-276) | own Opacity `terminal_bg_opacity` (40), blur `terminal_bg_blur`, grain `terminal_glass_grain`, Wallpaper dim `terminal_wallpaper_dim`, corner radius `terminal_corner_radius` 24, inner gap `terminal_pane_gap` 4 (INV:2.2, 2.3) |
| 24 | Pane borders, focus rim, glow | Shared rim; focused pane in active colour; attention glow (GLO "Pane") | Terminal card > Pane borders sub-card: border/focus/glow colours and custom (INV:754) | `terminal_pane_border_colour` outline, `_focus_colour` primary, `_glow` false (INV:2.4 table) |
| 25 | Split and dividers | Panes inside a window: divider drag, auto tiling (dwindle), focus grows, multiplexer chips; Windows & panes page | Terminal card chips `pane_dwindle_default`, `pane_focus_grows`, `compatibility_mode`; Settings > Windows & panes (Shortcut hints, agent control) (INV:211-219) | pane clamp radius |
| 26 | Terminal effects | CRT/TFT shader, screen events, composite signal; cursor trail; idle animations | Terminal card; Settings > Battery & performance (INV:566-585) | effect tiles |
| 27 | Titlebar / Home floats / Scratchpad | Glass strip on floating panes with Minimise/Maximise/Close; floats over a Home page; toggled scratchpad (GLO "Titlebar", "Home float", "Float") | none separate; Dock chip "Float terminal" | Sheets look (GLO "Surface") |
| 28 | Display (X11) window/page | Embedded X server picture as a window; Display page kind; touchpad/direct touch, keyboard over display | Settings > Linux apps (setup, desktops, per-app, display backend, input, resolution, startup, fixes) (INV:277-344) | none of its own (picture to the rim; border drag stops at its edge, GLO "Border drag") |
| 29 | Wayland Linux windows | Compositor windows, one per app, a window of the row (GLO "Linux window") | Settings > Linux apps > Display backend; per-app Scale | none; rim tab retired, controls on the mark |
| 30 | Assistant page | Chat page kind with composer, chats, dictation (`app/assistant/`) | Settings > On-device AI (INV:485-565) | follows Global |
| 31 | Overview (today) | One sideways strip of page cards in wall order, rows of sessions per Workspace, Saved row (GLO "Overview") | Settings > Home > Overview door (INV:132-152) | Global glass at the sheets' floor |
| 32 | Sessions / Saved sessions | Session = one split layout; Workspace page kind holds several; Saved row (GLO "Workspace", "Saved sessions") | Settings > Home > Pages | none |

### 1c. Home

| # | Surface | What it is | Controlled by today | Look properties |
|---|---|---|---|---|
| 33 | Home pages + widget grid | Widget pages left of Terminal; Clock, Glance, Weather, Device, Notes; 4x5 grid, locked layout option | Appearance Home card (columns x rows, label size, folders, numerals, two-line labels, shadow, icon size, Glass widgets, wallpaper scrolling); Settings > Home (INV:132-152, 2.2) | Widgets slot: follows Global while `widgets_on_glass`; Card or Bare per widget |
| 34 | Home icons and labels, folders | App icons on Home, folders (density) | Home card, icon pack, monochrome | icon size, label shadow |
| 35 | Home page bar | Glass pill above the dock listing pages; Glyphs/Marks looks (GLO "Page bar") | `page_bar_look`, `page_bar_glass_style` concave/well (INV:3.1) | pill glass |
| 36 | Home gestures | Swipe up (palette), swipe down, double tap, two-finger up/down, quick reply swipe, double-tap lock | Settings > Home > Gestures (INV:134-152) | none |
| 37 | Page transition / Crossing | Slide of wall between pages: slide, depth, fade, carousel, stack; animation speed | Settings > General > Motion, Appearance Global "Transition" (INV:586-615, 2.2) | motion |

### 1d. Sheets and global

| # | Surface | What it is | Controlled by today | Look properties |
|---|---|---|---|---|
| 38 | App drawer | Default (Apps + Categories sections), Vertical, Horizontal, Categories; A-Z; search; Linux apps listed (GLO "Default drawer") | Settings > Apps > Drawer layout (grid, icon size, columns/rows, A-Z side, search bar bottom, remember position, lower start, category sort) (INV:172-189) | Sheets glass = Global with a code floor (INV:793) |
| 39 | Folders | Home folder sheet | Home card "Folders" density | Sheets glass |
| 40 | App menu, Home long-press sheet, anchored menus | Card from long press on an icon (notifications, shortcuts, Widgets, App info, Add to home, Pin, Change icon, Uninstall) (GLO "App menu") | none | Sheets glass |
| 41 | Command palette | Search/command sheet opened by Search field, dock item or gesture (GLO "Search field") | Settings > Apps > App search (prefix, open only match, reset ranking) (INV:190-198) | Sheets glass |
| 42 | Wallpaper / backdrop / ground | Managed wallpaper, dim, parallax, ground between wallpaper and page (GLO "Managed wallpaper", "Ground", "Parallax") | Appearance Global Wallpaper, Dynamic colour; Terminal card Wallpaper dim; Settings > Battery & performance "Wallpaper scrolls with pages" (INV:2.2) | dim 0-100, parallax on/off |
| 43 | Body swipe / border drag / keyboard swipe | Gestures that page the wall or raise the keyboard from window borders (GLO "Body swipe", "Border drag", "Keyboard swipe") | Settings > Home > Gestures "Swipe on the terminal to switch windows" `terminal_body_swipe` (INV:144) | border lights in accent |
| 44 | Notification dots and badges | Dots/counts on dock/home icons | Settings > Notifications (INV:473-484), Dock chip | style dot/count |
| 45 | Launcher modes | "Use as" (Launcher / Terminal), Start in, setup rerun | Settings > General > Launcher (INV:586-615, `general_preferences.xml` L15-31) | none |
| 46 | Appearance editor | Scaled launcher in a frame; six cards (Home, Status bar, Terminal, Dock, Keyboard, Global); Look presets Clear/Frosted/Tinted/Solid/Liquid; Done/Undo/Discard (INV:709-714) | itself | draft model |
| 47 | Layout editor (session) | Move/size controls inside the Appearance editor: elements, edges, order, tray (GLO "Layout editor") | Appearance cards; layout keys (INV:3.5) | geometry per orientation |
| 48 | Settings app | 11 roots over 51 screens (INV:1.2) | `SettingsActivity` | n/a |
| 49 | Tour, setup sheet, quick toggles, help | First-run setup cards, tour, help (INV:1009-1011, 3.4 "Not delivered": **not inventoried**) | Settings > General > Setup | not researched |

Total surfaces listed today: 49.

---

## 2. Fate in Terl

Fate vocabulary: **Kept** (as is), **Merged** (into another surface), **Changed** (survives, meaning/placement
differs), **Retired**, **Silent** (spec/prototype do not address; becomes a question). Row numbers refer to section 1.

| # | Surface | Fate | Evidence |
|---|---|---|---|
| 1 | Status bar strip | **Retired** (Android's own status bar stays shown) | A38:87, SPEC:78-80 |
| 2 | Status bar expanded form | **Retired** | SPEC:78-79, A38:87 |
| 3 | Status clock | **Changed**: clock moves to Home widgets | SPEC:80 (Clock "lives in Home widgets"); PROTO draws a 76 px clock on Home, PROTO:134 |
| 4 | Status readings (CPU/GPU/RAM/battery/media/weather/text-size) | **Silent** (Android bar keeps clock and notifications only; readings not placed) | SPEC:78-80 lists nothing about readings; PROTO draws only Home weather card, PROTO:137 |
| 5 | Pinned notifications | **Changed**: become a Home widget of their own, "separate work" | SPEC:80, SPEC:156 |
| 6 | Marks line / page bar look | **Merged** into the control bar's marks | SPEC:60-62; PROTO:152 `.mark` |
| 7 | Window chips | **Retired** (the mark is the window's handle) | SPEC:60-62, GL:469 |
| 8 | Camera cutout handling | **Silent** (launcher bar gone; Android bar stays; the bar and Float/Overview placement near a cutout not mentioned) | SPEC:78 |
| 9 | Dock Docked | **Retired** | A38:102 |
| 10 | Dock Floating | **Retired** as dock; but PROTO's bar is a floating rounded card (inset 6 px, radius 26, 12 px above the bottom), so "Docked vs Floating" for the bar is **Silent** | A38:102; PROTO:144 |
| 11 | Dock designs (Tray/Beads/Strip...) | **Retired** | A38:102 |
| 12 | Apps row / dock items | **Merged**: pinned apps are Home's bottom row of the bar; Drawer tile/Search field/Command palette -> bar search; Float terminal -> window menu "Float"/Super+V; New terminal -> bar tool on a terminal; Most-used page **Silent** | SPEC:62-65, 74-77, 71; PROTO:672-678 `ctxTools` |
| 13 | A-Z strip / index | **Silent** (belongs to drawer/dock; neither is addressed) | no mention in SPEC; PROTO has no drawer |
| 14 | Extra keys row | **Changed**: off by default, an optional row above the keyboard in Settings | SPEC:81-82, A38:75-76, A38:103-104 |
| 15 | Home pill | **Silent** | no mention |
| 16 | In-app keyboard | **Changed**: gains a Super key with hint sheet; docked/floating/split forms **Silent**; PROTO draws docked only, 232 px tall, glass | SPEC:110-112; PROTO:251, 262-264, 239 |
| 17 | Page switcher key | **Silent** (the Workspace list is the bar's slide-up) | SPEC:69-70 |
| 18 | Nav strip | **Silent** (bar rides above Android's gesture line; PROTO draws a gesture pill at PROTO:174 `.gest`) | SPEC:60 "above Android's gesture area" |
| 19 | Corner tab | **Retired** | A38:102-103 |
| 20 | Grabber | **Silent** (keyboard swipe/status swipe retire with body swipes and edge drags; grabber not named) | A38:19-21 context ("body swipes, edge drags"), A38:102 |
| 21 | Minimal mode | **Silent** (full-screen window and Terminal launcher mode overlap) | SPEC:55-57, 66 |
| 22 | Notices | **Silent** in spec; PROTO draws a toast pill at top (PROTO:265) used for messages such as "Moved to Workspace N" | PROTO:711 |
| 23 | Terminal panes (canvas) | **Changed**: a window is one shell; the glass card, one radius, gap shared with Linux windows | A37:20-22, SPEC:116 |
| 24 | Pane borders/focus rim/glow | **Changed**: focused and unfocused each get rim (accent, on by default for focused), dim (0), opacity (window's own); colour options and glow **Silent** | SPEC:120-121; PROTO:71-72 rim `inset 1.5px var(--accent)` |
| 25 | Split and dividers | **Changed**: no panes; windows in scrolling columns (full/half, thirds in landscape) or dwindle per Workspace; resize by gap drag; double tap snaps; stacked column; tabs when too small | SPEC:42-52, A37:20-31 |
| 26 | Terminal effects | **Silent** | no mention |
| 27 | Titlebar / Home floats / Scratchpad | Titlebar **Retired** (no title bars or handles); Float **Changed** (belongs to a Workspace or Home page, Pin = every Workspace); Scratchpad **Silent** | A38:81-82, SPEC:53-54, GL:631; PROTO:75 `.win.float` |
| 28 | Display (X11) window | **Changed**: an ordinary window, usually full screen in its own Workspace; per-app Opens as / Opens on; mouse mode on the bar | A37:20-21, SPEC:65, 126-127; GL:626 |
| 29 | Wayland Linux windows | **Kept** as a window; **Changed** by own Opacity (default 100%) and per-app Opacity beside Scale | SPEC:117-119 |
| 30 | Assistant page | **Changed**: a window | A37:20-21 |
| 31 | Overview | **Changed**: one row per Home and Workspace, schematic cards, drag to move/tab, Saved row; opened by holding the bar | SPEC:97-100; PROTO:223-236 |
| 32 | Sessions / Saved sessions | **Retired**; saved Workspace keeps windows, layout, working directories | SPEC:33-34, GL:459 |
| 33 | Home pages + widgets | **Kept**; Home is the top of the stack, sideways flips pages | SPEC:28-30; PROTO:134-141 draws clock + glass weather card |
| 34 | Home icons/labels, folders | **Silent** (pinned apps live in the bar; Home grid icons not drawn) | PROTO:499 draws only clock and weather |
| 35 | Home page bar | **Silent** (how Home pages are indicated is not drawn; the bar's badge shows "Home") | SPEC:30; PROTO:679-682 |
| 36 | Home gestures | **Silent** except that body swipes retire and sideways flips Home pages | SPEC:84-87, 30 |
| 37 | Page transition / Crossing | **Silent**; PROTO moves the stack with one 0.3 s ease (PROTO:41,56 `.stack`) | - |
| 38 | App drawer | **Silent** (search lists Android apps, Linux apps, Terminal; no drawer in PROTO) | SPEC:74-77 |
| 39 | Folders | **Silent** | - |
| 40 | App menu / long-press sheet | **Silent**; held search entry offers "New window here" (a new item) | SPEC:76-77 |
| 41 | Command palette | **Silent**; search is the sibling and the Super key opens it | SPEC:74-77, 108 |
| 42 | Wallpaper / backdrop / ground | **Changed**: one wallpaper behind the whole stack, drifting as you move; per-page dim **Silent** | SPEC:122; PROTO:49, 600-606 (`wall` moved 1.8% of horizontal pan, 16 px over four places vertically) |
| 43 | Body swipe / border drag / keyboard swipe | Body swipe and border drag **Retired**, replaced by Border hold; keyboard swipe **Silent** | SPEC:84-90, A38:102-103 |
| 44 | Notification dots and badges | **Silent** | - |
| 45 | Launcher modes | **Changed**: Terminal mode has no Home and starts at Workspace 1; Launcher mode starts on Home; one-row bar always under Terminal mode | SPEC:35-36, 66-67 |
| 46 | Appearance editor | **Silent** (the thing being redefined; spec Looks section gives only the controls in 3a below) | SPEC:114-122 |
| 47 | Layout editor | **Silent** (element positions are fixed by the bar; per-orientation bar/rail is built-in) | SPEC:66-68 |
| 48 | Settings app | **Silent** except the extra-keys toggle, Opens as/Opens on, Opacity, launcherctl | SPEC:81-82, 114-127 |
| 49 | Tour, setup, quick toggles, help | **Silent** (not inventoried today either) | INV:1009 |

### Fate counts (49 rows, each counted once by its dominant fate)

| Fate | Count | Rows |
|---|---|---|
| Kept | 2 | 29, 33 |
| Merged | 2 | 6, 12 |
| Changed | 13 | 3, 5, 14, 16, 23, 24, 25, 27, 28, 30, 31, 42, 45 |
| Retired | 8 | 1, 2, 7, 9, 11, 19, 32, 43 |
| Silent | 24 | 4, 8, 10, 13, 15, 17, 18, 20, 21, 22, 26, 34, 35, 36, 37, 38, 39, 40, 41, 44, 46, 47, 48, 49 |

Rows with two fates: 10 (dock Retired, the bar's own style Silent: counted Silent), 27 (titlebar Retired, Float Changed,
Scratchpad Silent: counted Changed), 29 (Kept, plus Opacity Changed: counted Kept), 43 (body swipe and border drag
Retired, keyboard swipe Silent: counted Retired).

---

## 3. New surfaces Terl introduces

What each needs in looks or settings. "Spec/PROTO" shows what is already fixed; "Needs" is what the redesign has to
decide, drawn from the spec's own wording (Looks: SPEC:114-122) and the prototype's hard-coded numbers.

| # | New surface | What spec / prototype fixes | Look and settings it needs (no choice made here) |
|---|---|---|---|
| N1 | Control bar, two rows (portrait) | Bottom of every place; top row = Workspace badge (number + main-window icon), marks, window name; bottom row = tools for what is in front, then navigation mode and search (SPEC:60-65; A38:75-79). PROTO: 96 px tall, inset 6, 12 px above bottom, radius 26, glass `rgba(32,40,55,.42)` + blur 20 px + saturate 1.3 + 1 px inner rim + shadow (PROTO:144) | glass slot of its own (Opacity, Frost, Corners, edge gap), height/density, which tools show, pinned-app list and count on Home, name label on/off |
| N2 | Control bar, one row | While typing and always under Terminal mode; rides on the keyboard (SPEC:66-67). PROTO: 46 px, name and badge text hidden (PROTO:164-170, 171) | same glass; whether Terminal-mode one row shows marks |
| N3 | Control bar rail (landscape) | Down one side, right by default, same zones top to bottom (SPEC:67-68) | side setting (left/right), rail width, glass; not drawn in PROTO |
| N4 | Marks | One per window, focused one lit; tap goes; hold opens window menu; drag sideways reorders; drag up into the Workspace list moves the window (SPEC:69-73). PROTO: 32x36 tiles, 17 px icon, accent for focused plus 3 px underline (PROTO:152-156) | mark size/spacing, icon vs number, accent colour, attention burst (today's "wants you" burst, GLO "Marks") |
| N5 | Workspace badge and name | Number + icon of main window; long press renames (SPEC:31-32); PROTO badge 32 px high, accent tint (PROTO:148-150) | name/rename, icon source, show name on bar |
| N6 | Workspace list | Slide up on the bar, let go on a Workspace to go there (SPEC:69-70). PROTO: 224 px-wide popover left of the bar, rows 50 px with number chip, window count, "Home" row (PROTO:214-221, 763-771) | popover glass (sheet-class), row content (count, icon), Saved entries |
| N7 | Navigation mode | Four-arrow button; windows take no input; one finger pans the whole stack; long press picks a window up; drop on a side, centre (tab) or gap (column); edge hold keeps panning (SPEC:91-96). PROTO: windows dim to 82% brightness and rims go white (PROTO:78-79), a hint pill "Drag to move around · hold a window to move it" (PROTO:267), a drop preview with label (Left/Right/Above/Below/Tab/New column) (PROTO:276, 944-959) | dim amount, rim, drop-zone colour, hint on/off, edge-hold speed |
| N8 | Overview rows | One row per Home and Workspace with a mini layout map, number, main icon, and one 54 px card per window (icon, name, shape of window); Saved row; drag card to another row (move) or onto another card (tab) (SPEC:97-100). PROTO:223-236, 776-811 | sheet glass; card size; whether cards are schematic only (SPEC:157: live pictures out of scope); a current-row accent |
| N9 | Tab strip | Thin glass strip on a tabbed tile with each window's icon and short name (SPEC:50-52). PROTO: 26 px, `rgba(255,255,255,.07)` strip, active tab `.14` (PROTO:82-86) | strip height, label, glass, where active tab shows rim |
| N10 | Full-screen handle | Full screen hides the bar to a thin glass handle above Android's gesture line; slide up raises the bar over the window (SPEC:55-56). PROTO: 110x22 touch area, 6 px glass pill (PROTO:175-178, 1056-1058) | handle visibility/fade, auto-hide, status bar hidden in full screen (PROTO:54 hides it) |
| N11 | Border-hold arrow | Hold still on any edge (incl. the gap between tiles); tick and small arrow at the edge; repeats after a beat (SPEC:88-90). PROTO: 36 px circle with progress ring, 450 ms first, 600 ms repeat (PROTO:269-275, 879-890) | hold delay (spec: 1.5 s today is too long, SPEC:166-167), edge width (PROTO 16 px, PROTO:872 `m=16`), on/off, haptic |
| N12 | Super key and hints | Launcher keyboard key with Terl glyph; hold slides out live hints (SPEC:110-112). PROTO: 3 glyph candidates A/B/C (PROTO:309-338 symbols), hints card with 10 rows (PROTO:984-993), key accent tint (PROTO:262-264) | glyph choice, hints on/off, whether key is remappable |
| N13 | Search sheet | Terminal first, then Linux apps, then Android apps; typing filters; entry already open goes to its window; hold offers New window here; Super+Space opens (SPEC:74-77). PROTO: sheet over bar, field 46 px, 44 px rows, group headings (PROTO:187-202, 718-735) | sheet glass; groups order; Android-app icon source (icon pack); search prefix setting (today `app_launcher_input_char`) |
| N14 | Window menu | Held mark opens: float or tile, full screen, move to Workspace, tab, stack, close; plus Minimise, Maximise, Scale where they apply (SPEC:70-73). PROTO: 236 px anchored card, items Float/Tile, Full screen, Move to workspace (chips 1..n), Tab with..., Stack into column, Close (danger) (PROTO:203-213, 736-752) | menu glass; mixes Linux-only items |
| N15 | Focused / unfocused window states | Each gets rim (accent, on by default for focused), dim (0 default), opacity (window's own default) (SPEC:120-121). PROTO: focused = inset 1.5 px accent + 0.5 px halo; unfocused = 1 px white-18% rim; Linux "opq" windows solid `#1a2030` (PROTO:65-73) | six controls (rim/dim/opacity x focused/unfocused); rim colour (today's `terminal_pane_focus_colour`) |
| N16 | Floats and Pin | Float above tiles of one Workspace or Home page; Pin shows on every Workspace (SPEC:53-54; GL:631). PROTO: floats drawn 250 wide, drag by hold 450 ms, shadow `0 14px 36px` (PROTO:75, 655-660) | float default size/position, shadow, Pin entry (not in PROTO menu), Float titlebar retired so move is by long hold |
| N17 | Gap resize handle | Drag the gap between columns resizes; double tap snaps to preset (SPEC:46-47) | gap hit size (SPEC:166), preset widths, gap thickness (PROTO GP = 6 px, PROTO:395) |
| N18 | Window opening and closing motion | PROTO: `.win.fresh` scale .96 and fade (PROTO:74); stack/wall transition .3 s (PROTO:56) | animation speed (today's `animation_speed`), reduced motion |
| N19 | Drift wallpaper | One wallpaper behind the stack, drifting a little (SPEC:122). PROTO: wall inset -24 px, translate 1.8% of horizontal pan, up to 16 px vertically (PROTO:49, 600-606) | drift on/off (today's Parallax), amount |
| N20 | Per-app rules | Each app page gains Opens as (tiled/floating/full screen), Opens on (current Workspace or fixed) (SPEC:126-127) | rows on the per-app page; Linux apps' own Opacity beside Scale (SPEC:117-119) |
| N21 | Workspace layout choice | New starts scrolling; dwindle per Workspace from its menu (SPEC:44-52). The "Workspace menu" itself is not drawn in PROTO | per-Workspace layout switch, a default layout for new Workspaces, column presets (full/half, thirds in landscape) |
| N22 | Saved Workspaces | Saved row in Overview; keeps windows, layout, working dirs (SPEC:33-34, 100); PROTO shows "Dev setup" and "Notes" cards that toast "Saved Workspaces open when you pick them" (PROTO:~790, 806) | save/rename/delete, autosave |
| N23 | Back behaviour | Closes anything over the windows, otherwise does nothing (SPEC:101-102). PROTO:813-826 | none; but Back-at-peek rule of today's editor interacts |
| N24 | Mouse-mode tool | Linux app in front: touchpad vs direct touch button on the bar (SPEC:65). PROTO toggles icon, toast "Direct touch"/"Touchpad" (PROTO:703) | default mode (today `touchMode`) |
| N25 | launcherctl surface | Full control plus event stream (SPEC:129-134) | settings registry ids for Workspaces/windows; events setting |
| N26 | Keys/hardware bindings | Super-based set replaces Ctrl+Alt (SPEC:104-109) | binding list editor, conflict with Android's Super |

---

## 4. Settings today that become moot, change meaning, or are newly needed

Today's Settings roots: Your launcher (Home, Apps, Terminal, Linux apps), Everywhere (Appearance door, Keyboard,
Notifications, On-device AI), System (Battery & performance, General) (INV:19, `root_preferences.xml`). Classes: **Moot**,
**Meaning**, **Keep**, **Silent** (spec says nothing; question), **New**.

### 4a. Home (`home_preferences.xml`, INV:132-152)

| Setting (key) | Class | Why |
|---|---|---|
| Edit on screen (`home_layout_editor`) | Silent | opens the Layout editor; fate of the editor unaddressed (row 47) |
| Gestures: swipe up / down / double tap / two-finger up/down | Silent | Home still exists; palette gesture opens a command palette that spec does not mention |
| Double-tap to lock (`app_launcher_sub_lock`, `app_launcher_az_lock_method`) | Silent / Meaning | the "A-Z Row" it double-taps is gone if A-Z is gone (row 13) |
| Swipe up on app icons for quick reply (`app_launcher_quick_reply_swipe`) | Meaning | its trigger "from the dock" ends with the dock (A38:102); pinned apps now sit in the bar |
| Swipe on the terminal to switch windows (`terminal_body_swipe`) | Moot | "Nothing on a window's body changes the page" (SPEC:86-87); replaced by Border hold |
| Home widgets (`app_launcher_widget_pane_enabled`) | Silent | Home is always the top of the stack (SPEC:28-30); toggle may be moot |
| Overview / pages (`home_edit_pages`) | Meaning | pages -> Workspaces list and Overview rows (SPEC:97-100) |
| Lock Home layout | Keep | Home still holds widgets |

### 4b. Apps (`launcher_preferences.xml`, `app_drawer_preferences.xml`, `launcher_search_preferences.xml`, INV:160-198)

| Setting | Class | Why |
|---|---|---|
| Pinned apps (`app_launcher_default_buttons`) | Meaning | pinned apps are now the bottom row of the bar on Home only (SPEC:63) |
| Most-used apps page | Silent | dock page capsules gone with dock |
| Drawer layout (grid, columns, rows, A-Z side, search bar bottom, remember position, lower start, category sort...) | Silent | the drawer is not in SPEC or PROTO |
| App search (prefix, only match, reset ranking) | Meaning | search is the bar's; ordering Terminal, Linux, Android (SPEC:74) |
| Show Linux apps in drawer (`x11_drawer_apps`) | Meaning | Linux apps appear in search (SPEC:74) |

### 4c. Terminal (`terminal_*`, `status_bar_preferences.xml`, INV:199-276)

| Setting | Class | Why |
|---|---|---|
| Windows & panes: Shortcut hints (`show_key_hints`), agents control panes (`pane_agent_api`) | Meaning | panes retire (A37:20-22); "Shortcut hints" overlaps Super hints; API -> launcherctl (SPEC:129-134) |
| Sessions, session rows | Moot | sessions retire (SPEC:33, A37:20-22) |
| Multiplexer / Auto tiling (`pane_dwindle_default`) / Focus active pane (`pane_focus_grows`) / Split-pane controls (`split_pane_controls`) | Meaning | dwindle is a per-Workspace layout (SPEC:49); "focus grows" has no tiling meaning in scrolling columns; Silent |
| Stack layout / Stack redesign | Moot | no Stack layout mentioned; the stacked Workspaces replace it; (GLO "Stack layout" is bottom-stack geometry, a different thing) |
| Status bar page: Weather location, camera cutout (Keep clear/Margin/Move/Size) | Moot | launcher status bar retires (SPEC:78-79); weather location moves to the Home weather widget (Silent) |
| Status elements tiles (clock, CPU, GPU, RAM, weather, notifications, media, battery, font toggle), status row order, clock face/position/12h/seconds | Meaning | clock face -> Home widget; the rest Silent (row 4) |
| Page bar Glyphs / Marks (`page_bar_look`) | Meaning | Marks become the bar's marks (row 6); Glyphs look Silent |
| Text & display (font, ligatures, weight, Nerd icons, compact size), TUI app themes, clipboard, engine | Keep | window content settings unchanged |
| Terminal opacity / frost / grain / Wallpaper dim / corner radius / inner gap | Meaning | become the one window glass card shared with Linux windows (SPEC:116) |
| Pane borders (colours, glow) | Meaning | replaced by focused/unfocused rim/dim/opacity (SPEC:120-121); glow Silent |
| Cursor trail, retro effect, screen events, composite signal, idle animations | Silent | not mentioned |
| Minimal mode (`place.minimal`) | Silent | overlaps full screen and Terminal mode |
| Terminal notifications (`terminal_notifications`) | Silent | - |

### 4d. Linux apps (`x11_display_*`, INV:277-344)

| Setting | Class | Why |
|---|---|---|
| Per-app settings page (Scale, resolution...) | Meaning | gains Opens as, Opens on, per-app Opacity (SPEC:117-119, 126-127) |
| Display backend X11 / Wayland, Start with, Start command, Desktops, Window manager | Keep (Silent on a backend change) | the Display and Linux apps are windows (A37:20-21); backend choice not discussed |
| Input: Touch mode, Android keyboard, Auto keyboard, Keyboard behaviour (Overlay/Resize), clipboard, paste shortcut | Meaning | touchpad/direct touch moves to the bar (SPEC:65); "keyboard over display" interacts with the bar-rides-keyboard rule (SPEC:66-67) |
| Resolution and scaling, Startup, Fixes, Graphics acceleration | Keep | content settings |
| Linux window Opacity | New | SPEC:117-119 |

### 4e. Appearance editor (INV 2.1-2.3)

| Today | Class | Why |
|---|---|---|
| Six cards (Home, Status bar, Terminal, Dock, Keyboard, Global) | Meaning | Status bar and Dock cards have no referent; bar card is new; Terminal card becomes a Window card (A38:75; SPEC:116) |
| Layout style Docked/Floating (`app_launcher_dock_style`) | Silent | dock retired; whether the bar has a style (PROTO: floating card) is open |
| Dock type, Apps in the dock, Dock options chips, Reflection, A-Z sub-card, Extra keys sub-card | Moot (extra keys option: Keep, SPEC:81-82) | dock retired |
| Look presets Clear/Frosted/Tinted/Solid/Liquid, Opacity, Frost, Tint, Accent, Corner radius, Margin, Depth, Shine | Keep / Silent | SPEC:116 keeps "one radius, small tiles clamp it" and gap; presets and Depth/Shine unaddressed |
| Transition (slide/depth/fade/carousel/stack) | Silent | wall is now a vertical stack |
| Wallpaper, Dynamic colour, Theme, Text size, Contrast, Full screen, Adaptive legibility | Keep / Silent | Full screen becomes per-window (SPEC:55); the global `fullscreen` switch Silent |
| Home card (columns x rows, labels, folders, numerals, glass widgets, wallpaper scrolling) | Keep (Home stays) | SPEC:28-30 |
| Keyboard card (form, spacing, corners, padding, width, height, split gap, opacity, frost, theme) | Silent | keyboard forms not addressed |
| Terminal card: Multiplexer, Auto tiling, Enlarge focused pane, Minimal | Moot / Meaning | see 4c |
| Done-commits draft model, reset arrows, Back at peek | Silent | editor flow not addressed |
| Window rim/dim/opacity (focused and unfocused), Linux window Opacity | New | SPEC:117-121 |
| Appearance reset `AppearanceReset.NUMBER` | Silent | AGENTS.md ties it to 2.0; A37:46-47 says upgrade starts fresh |

### 4f. Keyboard (`termux_keyboard_*`, INV:388-472)

| Setting | Class | Why |
|---|---|---|
| Extra keys editor, Uppercase key labels, `extra_keys_*` | Meaning | row is off by default (SPEC:81-82) |
| Layouts, typing/feedback, voice, hardware keyboard (`pass_ctrl_space_to_android` etc.) | Keep (Hardware Silent) | Ctrl+Space exemption vs Super binding set not discussed |
| Keyboard theme, Keyboard extra keys (in-app) | Keep | - |
| Super key | New | SPEC:110-112 |
| Ctrl+Alt bindings (`show_key_hints`, GLO "Ctrl+Alt+C" etc.) | Moot | replaced by Super-based set (SPEC:104-109, A38:88-89) |

### 4g. Notifications, On-device AI

| Setting | Class | Why |
|---|---|---|
| Notification dots, badge style | Silent | dock/Home icons; "important notifications get a Home widget" (SPEC:79-80) |
| Pinned notifications rules, Notification history | Meaning | pinned cards leave the status bar for a Home widget (SPEC:79-80) |
| On-device AI runtime / API / remote / read aloud / model centre | Keep | Assistant becomes a window; its settings unchanged (A37:20-21) |

### 4h. Battery & performance, General (INV:566-679)

| Setting | Class | Why |
|---|---|---|
| Reduce idle, Low-memory preset, Glass refraction, Live blur, Live windows while swiping, Wallpaper scrolls, Idle animations | Meaning | Live windows follows `LiveNeighbourPolicy` (SPEC:145-146): neighbours in two directions; "while swiping" has no body swipe |
| Use as (Launcher / Terminal), Start in, Setup rerun | Meaning | Terminal mode has no Home and one-row bar (SPEC:35-36, 66); Start in values Home/Terminal Workspace |
| Animation speed, Page transition | Meaning | page transition between Home and Workspaces is vertical; transition options Silent |
| Vibration (launcher, keyboard), Sound, Media controls | Keep (+ Silent: border-hold haptic tick) | PROTO:879-890 ticks |
| Backup & restore, Permissions & services, Privileged access, Termux:API, Diagnostics, About & help | Keep | not touched |
| Motion log, process limit | Keep | AGENTS.md pong rounds |
| Window / Workspace controls (default layout, column presets, border hold delay, nav mode, Super hints, search order) | New | see section 3 |

---

## 5. Open questions

1. Does the control bar keep a Docked versus Floating style, or is it always the floating glass card the prototype draws?
2. Does the bar get its own glass slot (Opacity, Frost, Corners, edge gap), or does it wear Global like the Sheets?
3. Which glass properties may a window kind own (the spec says one glass card for every window, but also Linux Opacity and per-state opacity)?
4. How do Opacity, dim and rim relate to the existing Look grid (Opacity, Frost, Grain, Tint, Depth, Shine, Vibrancy) and to the Look presets Clear, Frosted, Tinted, Solid, Liquid?
5. Are pane border colours, custom colours and glow kept alongside the new focused and unfocused rim, dim and opacity controls?
6. Where do the rim, dim and opacity controls live (Appearance only, or per window, or per app)?
7. Does the Appearance editor survive in its present form (scaled launcher in a frame, six cards, Done and Undo), or is it replaced?
8. Which cards does the redesigned Appearance have, now that Dock and Status bar disappear?
9. Does the Layout editor (element edges, order, tray, per-orientation layouts) retire with the bar, or what replaces it?
10. Where do the status readings (CPU, GPU, RAM, battery, media, weather, text-size button) go once the launcher's status bar retires?
11. Which Home widgets replace the status clock faces, and does the widget set grow (readings, notifications)?
12. What happens to the app drawer, its A-Z strip, categories and drawer settings now that search lists apps?
13. Do Home icons, folders, the Home pill and Home's gesture set stay, and with which gestures now that body swipes retire?
14. What happens to the command palette, the dock items and the Home swipe-up palette gesture next to the bar's search?
15. Does the "Most-used apps" page, notification dots, badges and quick reply survive without a dock?
16. How are Home pages indicated now that the page bar and marks pill are not drawn on Home?
17. Is there a Home press behaviour and "Start in" choice between Home and a Workspace in Launcher mode, and what does Terminal mode's Start in offer?
18. What does Minimal mode become next to full screen and Terminal mode?
19. Do the keyboard forms (docked, floating, split), voice pill and keyboard layout setting stay, and how does the one-row bar sit with a floating or split keyboard?
20. Which Super bindings are user-editable, and what happens to the Ctrl+Space pass-through to Android?
21. Is the Super key hint card configurable (on/off) and does the glyph choice stay fixed once picked?
22. What are the settings and defaults for the border hold (delay, edge width, haptic tick, on or off)?
23. Are gap hit size, column presets and the double-tap snap configurable?
24. Is there a setting for the bar's rail side (left or right) and which tools show per context?
25. Which Workspace setting is global (default layout, auto-empty Workspace, naming) and which is per Workspace?
26. How are saved Workspaces created, renamed, deleted and autoloaded, and where is that managed (Overview or Settings)?
27. Do Pin, Float default size and a Scratchpad terminal remain as settings or only as window menu items?
28. Does the window menu offer Pin, and are Minimise, Maximise and Scale shown only for Linux windows?
29. Does the camera cutout handling retire entirely, or must the bar, rail and Floats avoid a cutout in landscape?
30. What replaces the nav strip and gesture-line behaviour of the bar under three-button navigation?
31. Do terminal effects (CRT shaders, cursor trail, idle animations) stay as per-window or global looks?
32. Is the wallpaper drift a setting, and does the per-page wallpaper dim return as a per-place dim?
33. Does Page transition (slide, depth, fade, carousel, stack) survive for vertical Workspace moves and Home page flips?
34. Do notices stay a top pill (PROTO draws a toast at the top) and does the Sheets glass floor still apply?
35. Does the Appearance reset (`AppearanceReset.NUMBER`) cover the new Terl look keys, and what does an upgrade from v1 keep of Appearance given the "starts fresh" rule?
36. Which settings does the Terminal launcher mode override (one-row bar, no Home), and are those visible in Settings?
37. Do the tour, setup cards and help (not inventoried today) need redesigning for the new model?
