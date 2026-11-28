# Omarchy and niri default keybindings, and a mapping onto the Terl leader

Accessed 2026-11-28. Sources (primary):
- Omarchy: raw files in github.com/basecamp/omarchy, branch master: `default/hypr/bindings/tiling-v2.conf` (read verbatim), `default/hypr/bindings/utilities.conf` and `config/hypr/bindings.conf` (read as summaries; chords below marked "summary" are less certain), `media.conf` (media keys only).
- niri: `resources/default-config.kdl` in github.com/YaLTeR/niri, main (the `binds` block; read via grep, so exact).
- Terl table: `app/src/main/java/com/termux/launcherctl/TerlDefaultBindings.java`.

Omarchy's "Super" is the leader below. Omarchy is dwindle-based Hyprland, with `omarchy-hyprland-workspace-layout-toggle` switching a workspace between dwindle and master (Super+L). Omarchy resizes mostly by mouse or the four keys below; it has no key chord for "move window left/right in a row" other than swap.

## 1. Omarchy defaults (Hyprland)

### Windows
| Chord | Action |
|---|---|
| Super+Return | Terminal in current working directory (summary) |
| Super+Alt+Return | tmux session "Work" (summary) |
| Super+Shift+Return / Super+Shift+B | Browser (summary); +Alt private window |
| Super+Shift+F | File manager (summary); +Alt in terminal's directory |
| Super+W | Close window |
| Ctrl+Alt+Delete | Close all windows |
| Super+Left/Right/Up/Down | Focus window in that direction |
| Super+Shift+Arrow | Swap active window with the neighbour in that direction (this is Omarchy's "move left/right") |
| Super+- (code 20) | Resize: expand window left (resizeactive -100 0) |
| Super+= (code 21) | Resize: shrink window left (resizeactive +100 0) |
| Super+Shift+- | Resize: shrink window up (0 -100) |
| Super+Shift+= | Resize: expand window down (0 +100) |
| Super+left mouse drag | Move window |
| Super+right mouse drag | Resize window |
| Super+T | Toggle floating / tiled |
| Super+O | Pop window out (float and pin) |
| Super+F | Full screen |
| Super+Ctrl+F | Tiled full screen (fullscreenstate 0 2) |
| Super+Alt+F | Full width (fullscreen 1, maximize) |
| Alt+Tab / Alt+Shift+Tab | Cycle windows on the workspace, raise active |
| Ctrl+Alt+Tab (+Shift) | Cycle monitors |

### Layout
| Chord | Action |
|---|---|
| Super+J | Toggle split direction (dwindle togglesplit) |
| Super+P | Pseudo-tile (dwindle) |
| Super+L | Toggle workspace layout (dwindle / master via omarchy script) |
| Super+/ , Super+Alt+/ | Cycle monitor scaling, forward / back |

### Groups (Hyprland tabs)
| Chord | Action |
|---|---|
| Super+G | Toggle window grouping |
| Super+Alt+G | Move active window out of group |
| Super+Alt+Arrow | Move window into the group in that direction |
| Super+Alt+Tab (+Shift) | Next / previous window in group |
| Super+Ctrl+Left/Right | Previous / next group member |
| Super+Alt+scroll | Cycle group members |
| Super+Alt+1..5 | Activate group member N |

### Workspaces
| Chord | Action |
|---|---|
| Super+1..9, 0 | Go to workspace 1..10 |
| Super+Shift+1..9, 0 | Move window to workspace (follows) |
| Super+Shift+Alt+1..9, 0 | Move window silently (does not follow) |
| Super+Tab / Super+Shift+Tab | Next / previous workspace |
| Super+Ctrl+Tab | Former (last used) workspace |
| Super+scroll | Next / previous workspace |
| Super+S | Toggle scratchpad (special workspace) |
| Super+Alt+S | Send window to scratchpad |
| Super+Shift+Alt+Arrow | Move workspace to a neighbouring monitor |

### Launchers, menus, system (summary of utilities.conf)
Super+Space app launcher; further Super+Alt+Space (Omarchy menu), emoji, capture, toggle, hardware, system and keybinding-reference menus; theme and background menus; bar toggle, window transparency, gaps; notification dismiss/silence/restore; idle-lock, nightlight, display toggles; screenshot, screen record, colour picker, OCR; share and transcode; reminders; time/battery/weather notifications; audio, Bluetooth, Wi-Fi panels, btop; dictation (F9 push to talk); zoom; Super+Ctrl+L lock; media keys on XF86 keys. Exact chords for these were not read verbatim.

## 2. niri defaults (scrolling columns), Mod = Super

| Chord | Action |
|---|---|
| Mod+T / Mod+D | Terminal / launcher (fuzzel) |
| Mod+Q (Alt+F4) | Close window |
| Mod+Left/Right (H/L) | Focus column left / right |
| Mod+Up/Down (K/J) | Focus window up / down within the column |
| Mod+Ctrl+Left/Right (H/L) | Move column left / right |
| Mod+Ctrl+Up/Down (K/J) | Move window up / down within the column |
| Mod+Home / Mod+End | Focus first / last column |
| Mod+Ctrl+Home / End | Move column to first / last |
| Mod+[ / Mod+] | Consume-or-expel window left / right (join the neighbouring column, or leave the column to the left/right) |
| Mod+, | Consume window into column (pull the next column's window in) |
| Mod+. | Expel window from column |
| Mod+R / Mod+Shift+R | Cycle preset column widths forward / back |
| Mod+Ctrl+Shift+R / Mod+Ctrl+R | Cycle preset window heights / reset height |
| Mod+- / Mod+= | Column width -10% / +10% |
| Mod+Shift+- / Mod+Shift+= | Window height -10% / +10% |
| Mod+F | Maximize column |
| Mod+Ctrl+F | Expand column to available width |
| Mod+M | Maximize window to edges |
| Mod+Shift+F | Fullscreen window |
| Mod+C / Mod+Ctrl+C | Center column / center visible columns |
| Mod+W | Toggle tabbed display of the column |
| Mod+V / Mod+Shift+V | Toggle floating / switch focus floating vs tiling |
| Mod+1..9 | Focus workspace N |
| Mod+Ctrl+1..9 | Move column to workspace N |
| Mod+Page_Up/Down (I/U) | Focus workspace up / down |
| Mod+Ctrl+Page_Up/Down (I/U) | Move column to workspace up / down |
| Mod+Ctrl+Shift+Page_Up/Down | Move column to a new workspace above / below |
| Mod+Shift+Page_Up/Down | Move workspace up / down |
| Mod (tap alone) | Overview |
| Mod+Shift+/ | Hotkey overlay |

## 3. Proposed mapping onto the Terl leader

Current Terl table (leader+): Enter new, Q close, arrows focus (left/right = window prev/next, up/down = session prev/next), Shift+arrows move, 1..9 Workspace, Shift+1..9 send, V float, F full screen, Space search, Tab Overview, W tab group, comma edit mode.

"Collides" = already in the Terl table with a different meaning. "Android" = taken by Android if the leader is a hardware Meta/Super, or by Ctrl+Space / Alt+Tab under a Custom leader holding Ctrl or Alt (see `TerlDefaultBindings.reservation`). Under the Terl key (swipe Ctrl) or Ctrl+Alt, Android takes nothing; only a Custom leader can be reserved.

| Action | Omarchy | niri | Proposed Terl | Flag |
|---|---|---|---|---|
| New window | Super+Return | Mod+T | leader+Enter | keep, matches |
| Close | Super+W | Mod+Q | leader+Q | keep; Omarchy's W is Terl's tab group |
| Focus | Super+arrows | Mod+arrows/hjkl | leader+arrows | keep; up/down are currently session prev/next, not "window above" inside a column: decide |
| Move window | Super+Shift+Arrow = swap | Mod+Ctrl+arrow | leader+Shift+arrows | keep; Shift+left/right should mean swap with neighbour column/window (Omarchy) or move column (niri): decide |
| Workspace go / send | Super+1..0 / +Shift | Mod+1..9 / +Ctrl | leader+1..9 / Shift+1..9 | keep; Terl has 9, Omarchy 10 (0 free) |
| Silent send | Super+Shift+Alt+N | none | leader+Shift+Alt+N (optional) | new |
| Next/prev Workspace | Super+Tab / Shift+Tab | Mod+PgUp/PgDn (I/U) | leader+PageUp/PageDown | collides: Tab is Overview in Terl; Alt+Tab and Ctrl+Alt+Tab are not leader chords |
| Float | Super+T | Mod+V | leader+V | keep; Omarchy's T is free |
| Pop out (float, pin) | Super+O | none | leader+O (optional) | new |
| Full screen | Super+F | Mod+Shift+F | leader+F | keep |
| Maximize column / full width | Super+Alt+F | Mod+F | leader+Alt+F | needs Alt in chord: fine under Terl key, with Ctrl+Alt leader Alt is already held |
| Tab group toggle | Super+G | Mod+W | leader+W | keep W (niri) or move to G (Omarchy): decide |
| Leave group | Super+Alt+G | Mod+. | leader+Alt+G or leader+. | new |
| Join neighbouring group / column | Super+Alt+Arrow | Mod+[ / Mod+] / Mod+, | leader+[ and leader+] | new; replaces the Omarchy Alt+arrow chords, which fail when the leader already holds Alt |
| Next/prev tab in group | Super+Alt+Tab, Super+Ctrl+arrows | (focus within tabbed column) | leader+Ctrl+Left/Right, or leader+. / , | new; Alt+Tab is Android's |
| Tab N in group | Super+Alt+1..5 | none | skip | collides with leader+Alt+N |
| Resize width | Super+- / Super+= (left edge) | Mod+- / Mod+= (width +-10%) | leader+- / leader+= | new; free in Terl |
| Resize height | Super+Shift+- / = | Mod+Shift+- / = | leader+Shift+- / Shift+= | new |
| Preset column widths | none | Mod+R / Shift+R | leader+R / Shift+R | new |
| Center column | none | Mod+C | leader+C | new |
| Focus first / last column | none | Mod+Home / End | leader+Home / End | new; on-screen keyboard may lack Home/End |
| Toggle split direction | Super+J | none | leader+J | new; Dwindle workspaces only, no-op in Scrolling |
| Pseudo-tile | Super+P | none | skip | dwindle-only, low value |
| Toggle Workspace layout | Super+L | none | leader+L | new; Scrolling vs Dwindle |
| Scratchpad | Super+S / Super+Alt+S | none | decide whether Terl has one; Overview or search may cover it | no equivalent |
| Search / launcher | Super+Space | Mod+D | leader+Space | keep; Android: Ctrl+Space under a Custom leader holding Ctrl |
| Overview | none (niri: tap Mod) | tap Mod | leader+Tab | keep; Android: Alt+Tab under a Custom leader holding Alt |
| Edit mode | none | none | leader+, | collides if comma is chosen for niri's "consume"; use [ ] and . instead |
| Window cycle | Alt+Tab | none | skip | Android's |
| Lock | Super+Ctrl+L | Super+Alt+L | not a Terl matter | Android owns lock |

### Android reservations to remember
- Hardware Meta/Super as a leader: Android takes Meta itself (launcher/search) and many Meta+letter chords, so Omarchy's Super chords cannot be copied literally.
- Alt+Tab, Ctrl+Space and Super alone are reserved; Ctrl+Alt+Tab and Alt+Shift+Tab may reach the app but Alt+Shift+Tab is a window-switch on some ROMs.
- Page_Up/Page_Down, Home, End, `[`, `]`, `.`, `-`, `=` are not reserved.

### Decisions for the developer
1. Resize by keys: Omarchy resizes the active edge by pixels (leader+-/= and with Shift for height); niri resizes in percentages and cycles presets. Terl columns suggest niri's model: width presets (leader+R) plus +-10% on leader+-/=; height on Shift. Pick one, and say what "-" means (shrink vs expand-left, which differs between the two).
2. Move left/right: Shift+arrows currently "move"; choose swap-with-neighbour (Omarchy) or move-the-column (niri). A window inside a stacked column needs both "move within column" and "move column", which niri splits as Ctrl+Up/Down vs Ctrl+Left/Right.
3. Join and leave a column: niri's [ ] consume-or-expel is the compact answer to "stack into the neighbour" and "leave it"; Omarchy's group chords need Alt, which a Ctrl+Alt leader already holds.
4. New window to the left vs the right: neither Omarchy nor niri has a chord (Hyprland opens after focus, niri opens to the right of the focused column). Options: leader+Enter right, leader+Shift+Enter left.
5. Split direction: leader+J toggles in Dwindle, nothing in Scrolling; accept a no-op, or hide the chord in Scrolling.
6. Group toggle: keep W (niri) or take G (Omarchy); Omarchy's W is close, so a wrong habit would close a window.
7. Up/Down meaning: focus-window-above (Omarchy, niri) versus the current session prev/next.
8. Next/previous Workspace: Tab is taken by Overview; choose PageUp/PageDown or leader+I/U.
9. Whether to add a scratchpad, silent send, pop-out, and the 10th Workspace.
