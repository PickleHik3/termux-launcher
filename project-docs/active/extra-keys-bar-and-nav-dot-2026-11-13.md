# Extra-keys bar per target, Home pill and the navigation dot (2026-11-13)

Status: designed, not built. Grilled with the developer 2026-11-12/13. Motion is settled on a touch
prototype first (`~/reports/extra-keys-proto/`, served at
`https://amal-build.taild9c5dc.ts.net/reports/extra-keys-proto/`); the native build starts after
the developer has picked the variants. Everything here reaches testers in one drop.

Replaces the space-bar places dock (`feat/space-places-dock`, spec
`space-places-dock-2026-11-09.md` on that branch): the space-bar hold is dropped, its picker moves
to the dot.

## Why

- Reports in #general, 2026-11-12 (sexsev7n): key pages with different row counts overlap; no way
  to change key size any more; wants the bar to change by itself between Terminal and Display
  (they use Page 2 as their Display keys).
- Page navigation has no single answer. A running Display pages only from its edge, the dock has no
  page icons, the page ticks take no touches, and the only jump is a `wall.*` key that costs a slot
  on the bar. The space-bar hold fails on Home (no keyboard there) and takes the space bar away
  from typing. A long press on content is taken: the action sheet on Terminal, mouse buttons on
  Display, the Home sheet on Home.
- The 2026-11-09 navigation designs were turned down for three reasons, which this design has to
  pass: permanent screen space, needing to look or reach, not being the same on every page.
- The bar view is upstream Termux code (`ExtraKeysView.java`, 2,067 lines) that the launcher has
  outgrown.

## What was found in the code (read, not run)

- Overlap: the bar's height is taken from Page 1's row count only
  (`TermuxActivity.java:23215-23224`, `AccessoryStackLayoutPolicy.java:200-205`), and every page is
  loaded with that height (`TerminalToolbarViewPager.java:79-81`). A two-row Page 2 splits one row's
  height. Not patched on the current bar (the developer's choice); the new bar's height rule
  removes it.
- Key size: `terminal-toolbar-height` (0.4 to 3, default 1, `TermuxPropertyConstants.java:322-328`)
  still scales the bar; no Settings row shows it.
- Storage: no app-owned store. The editor reads and writes `extra-keys` and `extra-keys2` in
  `termux.properties` (`ExtraKeysEditorFragment.java:185,228-234`); `TermuxPropertiesFile.write`
  keeps comments and unrelated lines.
- Where the bar shows: off on Home (`PlaceLayoutStore.java:908-912`, `DressResolver.java:96`); on
  Terminal, Display and Assistant, keyboard up or down; Display and Assistant wear the Terminal dress
  (`DressResolver.java:104-106`).
- Which keys act where: `ExtraKeyEligibility.java` (bands by page kind). Terminal input keys and
  multiplex keys do not act on Home.
- A held key today: UP, DOWN, LEFT, RIGHT, BKSP, DEL, PGUP, PGDN repeat
  (`ExtraKeysConstants.java:14`); CTRL, ALT, SHIFT, FN lock; other keys show a pressed look.
- Focus: `TerminalPaneController` (`mActiveWindow`, `focusedHomeFloat()`).

## The navigation dot

One floating object whose only job is changing page.

- **Rest**: a small dim dot snapped to the left or right screen edge. One position for the whole
  launcher: the same spot on Home, Terminal, Display and Assistant. When a keyboard would cover it,
  it hops to just above the keyboard and returns when the keyboard goes. It stays in Display's
  minimal mode. It exists only inside the launcher.
- **Slide** (the main gesture): touch the dot and slide; the picker opens under the finger and
  releasing on a cell goes there. One motion, no hold.
- **Tap**: opens the picker and leaves it open for a second tap. Back, or a tap outside, closes it.
- **Hold, then drag**: picks the dot up and moves it; on release it snaps to the nearer edge.
- **Picker**: the one built on `feat/space-places-dock`. A row of the four kinds (Home, Terminal,
  Display, Assistant); a kind with several pages or windows shows them stacked above its glyph.
  Targets, missing places, overflow ("+N"), the lit current place, glow, lift, label and haptics are
  as in that spec's Layout, Targets and Interaction sections. One change: Home has a stack when
  there is more than one Home page.
- **Default**: on for everyone. The first time, it opens itself once with a short label. One
  setting turns it off.
- **System Back**: a slide inward from the edge is Android's Back swipe. The dot's own rectangle is
  excluded from it (`View.setSystemGestureExclusionRects`; the allowance per edge is far larger than
  the dot).
- Open for the prototype to settle: rest size and dimness; whether the picker opens at touch-down or
  when the slide starts; the row's order relative to the dot's edge; the hold time before a move.

## The bar

- A new view replaces `ExtraKeysView`. The parser for the Termux key syntax (`ExtraKeysInfo`,
  `ExtraKeysConstants`) and the editor's model stay.
- Behaviour that carries over unchanged: repeat keys, modifier one-shot and lock, the swipe-up
  second key, combinations and macros, tool keys, labels, glyphs and colours, the sideways swipe
  between key pages, `extra-keys-style`, `extra-keys-text-all-caps`.
- **Key sets per target**: Global, Terminal, Display, Home, Assistant. A target without a set of its
  own shows Global.
- **The set follows what the keys go to**, not the page in front: a terminal window shows Terminal
  keys and a display window shows Display keys wherever it sits (its own page, a window in the
  Workspace, a float on Home). The bar can therefore change without a page change.
- **Key pages** stay, two per set as today.
- **Height**: the bar is as tall as the page in front. Flipping between pages of different row
  counts resizes the terminal or the display; accepted.
- **Key size**: one Global size (today's `terminal-toolbar-height`, given a control in the editor),
  and each set may carry its own.
- **Page keys** (`wall.*`) leave the shipped default rows and stay in the key picker. Rows a user
  has saved are not touched.
- **Motion**: key press, lock, repeat, swipe-up, the set changing, the height changing and the
  pill's flight are chosen on the prototype.

## The Home pill

- Home's bar is a small floating pill. Off by default, as today. Switching Home's Extra keys on
  writes a starter set of three launcher keys for Home instead of showing Global.
- It floats above Home's content, anywhere the user drops it, in the same spot on every Home page.
- It carries launcher actions; keys that do not act on Home stay marked as they are today.
- **Moving it**: a hold anywhere on the floating pill picks it up, as with Home icons and the dot.
  Picking it up shows an "Edit keys" chip that opens the editor on the Home set.
- **Flight**: when a keyboard comes up on Home the pill flies to the docked place above the
  keyboard; when the page changes, the one bar object flies between its Home spot and its docked
  place on Terminal, Display or Assistant. Docked, a hold on a key means repeat or lock again.

## Settings and storage

- **Editor**: the existing "Keyboard & extra keys" editor gets a row of targets (Global, Terminal,
  Display, Home, Assistant) above the Page tabs. A target without its own set shows "Using Global"
  and a button that gives it one. Each target has its Key size, defaulting to Global. Settings pages
  are fragile: this is the only settings change besides the dot's switch.
- No hold-to-edit on a docked bar (a hold there repeats or locks). The editor is reached from
  Settings, from `extrakeys.edit`, and from the Home pill's "Edit keys" chip.
- **Storage** stays in `termux.properties`, written by the editor as today:

  | Line | Set |
  |---|---|
  | `extra-keys`, `extra-keys2` | Global, pages 1 and 2 (unchanged) |
  | `extra-keys-terminal`, `extra-keys-terminal2` | Terminal |
  | `extra-keys-display`, `extra-keys-display2` | Display |
  | `extra-keys-home`, `extra-keys-home2` | Home |
  | `extra-keys-assistant`, `extra-keys-assistant2` | Assistant |
  | `terminal-toolbar-height` | Global key size (unchanged) |
  | `extra-keys-<target>-height` | that set's key size |

  A target has its own set when its page-1 line is present. Line names are a proposal.
- The dot's switch and position are app preferences.

## Default sets (proposal, for the developer to edit)

| Set | Keys |
|---|---|
| Global | KEYBOARD (swipe up: `keyboard.cycle_form`), `mouse.toggle`, `pane.split` (swipe up: `window.new`), `session.browser` (swipe up: `session.new`). Today's row without the three page keys. |
| Terminal | none of its own; shows Global |
| Display | KEYBOARD, `mouse.toggle`, ESC, TAB, CTRL, ALT. Today the Display page shows two keys that do nothing there (`pane.split`, `session.browser`). |
| Home (starter, written when switched on) | `app.command_palette`, `app.open_app_drawer`, `app.open_settings` |
| Assistant | KEYBOARD, `voice.dictate`, `clipboard.paste` |

## Not in this spec

- The settings file (one commented file listing every option, in the manner of oh-my-tmux). Its own
  spec later. Two answers from this grilling are parked with it: the file would be the truth with
  Settings editing it, and `termux.properties` would become a read-only fallback.
- Hardware-keyboard chords for page jumps (`wall.*` have none today).
- A patch for the overlap on the current bar.

## Still to name

- The dot in Settings and in the hint. Proposed row: "Page switcher", description "A dot on the
  screen edge that jumps to any page."

## Build notes

- The dot and the bar do not depend on each other in code; they ship together because the default
  sets assume the dot.
- The picker comes from `feat/space-places-dock` (head `06dfb3c88`, 15 commits not on v2-redesign,
  worktree removed). Port the picker and its haptics; leave the space-bar trigger, its fill and the
  "Hold space for places" setting behind. Delete the branch once ported.
- Motion on the emulator cannot be judged (CPU glass); the prototype and then pong decide it.
