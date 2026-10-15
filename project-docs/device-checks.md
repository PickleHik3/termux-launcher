# Device checks owed

Built, merged, not yet seen on a device. The list a drop or a cut is checked against first.

**Agent** items can be driven over adb: the emulator, or pong with the developer's go-ahead.
**Developer** items need a hand on the phone: feel, frame timing, a lock screen, taste.

One line per item: date · commit or PR · what to look at · what passes · how (device, route).
Verified items are **deleted**, never ticked; the PR, the commit or a bug is the record. An item
lands here in the same commit or PR as the change it belongs to, whenever a hand-over still owes
a device check. Before a drop or a cut, work through the Agent list and hand the Developer list
over; an item two releases old is re-judged and dropped if nothing rides on it. The file stays
under twenty items; past that, the oldest are re-judged first.

## Agent
- 2026-10-12 · PR #65 · The widget picker opens without closing the launcher on Android 10 and 11
  (#62) · an API 29 or 30 emulator, or the reporter on the next drop.
- 2026-10-12 · commit "feat(keyboard): paste a fresh screenshot from the paste key" · **Failed on
  the emulator 2026-10-15:** with a fresh screenshot waiting, paste with a latched or held Ctrl or
  Alt still typed the screenshot's path; plain paste should give the clipboard text while the photo
  keeps showing (`~/reports/devcheck-2026-10-15/k2g_row.png`, `k2h_term.png`). The glyph swap,
  the path paste and the return to the paste glyph passed · emulator.
- 2026-10-13 · commit "fix(launcher): scale an icon pack as one, not icon by icon" · Still owed:
  an app's notification icon with a pack (needs a real app posting one; `cmd notification post`
  shows a system icon); a pack with no margin looks as it always did · emulator, Arcticons from
  `https://mirror.cyberbits.eu/fdroid/repo/com.donnnno.arcticons_1570.apk`.
- 2026-10-13 · commit "feat(tai): plan a load on the typical measured drop, not the largest" · E4B
  GPU cleanup is admitted with about 3.8–4 GB free (`tai logs`: `load_ok … gpu ctx=2048`, no
  `load refused`), and any refusal reads `needs X MB + 768 MB floor = Y MB free, Z MB available`
  with the GPU's figure. Seen loading from 6.4 GB free on 2026-10-15 (`ctx=4096`); the low-memory
  case is still unseen · pong with the go-ahead, a 0.25 s MemAvailable watchdog that unloads
  below 2 GB.
- 2026-10-14 · commit "feat(keyboard): edit, reset and create layouts in Settings › Layouts" ·
  Still owed: TalkBack reads the 48dp icons (force-stop TalkBack's tutorial first, then relaunch
  the app) · emulator.
- 2026-10-14 · commit "fix(keyboard): readable rebuilt layouts, no fallback flash on a first
  swap" · A later swap to a new user layout showed it at once, 2026-10-15; still owed: the very
  first swap right after creating the layout and adding it to the cycle, captured from the tap ·
  emulator.
- 2026-10-15 · PR #75 · Touch light on a sheet card row. The terminal action sheet's Kill process
  row is a flat light list with a grey press highlight, no rim light or glow (2026-10-15,
  `~/reports/devcheck-2026-10-15/a9holdcrop.png`); find a sheet whose rows are centred glass
  cards (the `GlassPress` overload's callers) and hold one: the nearest rim lights, a faint glow
  sits under the finger, both fade within a quarter second. Nothing lights under Reduce motion,
  lazy mode or battery saver · emulator.
- 2026-10-15 · PR #75 · Edge clarity on the Widgets and Display pages under Mist, and with RTL on
  (`debug.force_rtl` did not mirror the app; use the developer option or an RTL locale): the bent
  edge shows a clearer wallpaper, the frost inside is unchanged, corners and hairlines clean. The
  pane slab under Floating at Clear passed, also at font scale 1.3 · emulator.
- 2026-10-15 · commit "feat(glass): draw the rim as a hairline and bend on Haze's edge profile" ·
  Clear, Mist and Tint at their shipped values (an install on the old Looks re-adopts them on
  launch) on the `glass-city` and `glass-flowers` wallpapers: crop a pane corner (one pane and
  split) and the dock's top edge (Docked and Floating, keyboard up and down). Passes when the rim
  is a faint hairline, lit along the top edge and fading round the corners; the picture is
  continuous with the wallpaper outside at the outline; the bend is a narrow ring and the face
  inside it stays flat; no milky band anywhere; Clear shows a faint red/blue glint at the corners
  only; a small pane shows no picture from its opposite edge; no line on a seam (keyboard over
  the strip under the pill, status over the window bar); Solid unchanged · emulator.
- 2026-10-15 · commits "fix(terminal-view): resolve symbol_map and fallback faces inside scaled
  text" and "fix(terminal-view): spread a wide icon across the blanks after it in scaled text" ·
  `/tmp/launcher-scaled-symbols-2026-10-15/scaled-icon-cases.sh` (run with `bash`): no tofu at
  any scale; a default-width (w=0) scaled icon, e.g. `66;s=2;<icon> `, stays one scaled column
  wide and centred in it (each character is its own block, so it does not spread); an explicit
  `66;s=2:w=2;<icon><space>` block, the form dawn's ribbon uses, draws the icon spread over both
  columns, centred, about letter height; plain-size icons and Latin in a scaled block are
  unchanged · emulator.
- 2026-10-15 · commits "feat(appearance-editor): Depth alone in the Glass group, Looks on the
  hairline" and "feat(appearance-editor): Depth as the global set's last slider, no Surface |
  Glass toggle" · Look › Custom with nothing tapped has no toggle in its heading row; Depth is the
  seventh, last slider after Corner radius, its legend whole and every column whole (also at font
  scale 1.3 on a 360dp screen). Depth is absent from every element's set, and absent from All
  surfaces in Lazy mode or with no wallpaper set from the launcher. After a drag the
  readout matches `fancier_glass_bend`/`_edge_width` (`run-as` the prefs) and no other key moves.
  Clear, Mist and Tint picked from the stops read Depth 90/80/70, and a Depth nudge and back lands
  on the Look's stop again. Drag Depth to 92 on Clear, tap an element and back to All surfaces: it
  still reads 92; pick a Look and it reads that Look's position. A Custom saved with the old six values (e.g.
  13/32/60/20/20/90) opens without changing any key until Depth moves · emulator, with a
  wallpaper set from the launcher.

## Developer

- 2026-10-15 · commit "feat(settings): make the Low-memory preset a switch" · Turning it on and off
  returns the look you had (material, opacity, effects, parallax), and Reduce idle activity follows
  the switch both ways without leaving the page · pong, Settings → App behavior.

- 2026-10-15 · stronger glass edges · The same three Looks on pong with a photo wallpaper: the
  edge reads at a glance on the dock, the keyboard and the panes, and is not too strong (no
  smeared or mirrored band that distracts, no glare on a pressed key or under a finger) · pong, by
  eye.
- 2026-10-15 · voice edit keeps the cleanup's marks · Dictate a sentence the cleanup changes, tap the text to edit: the changed and added words stay in the accent colour and the struck removed words are gone. Type inside a marked word, before it and after it, delete across a mark's edge, press ✓: the marks moved with the text (typing inside grows the mark, at its edges stays plain) and the committed text is exactly what was shown · pong, by voice.
- 2026-10-15 · Low-memory preset · Settings → App behavior → Low-memory preset, Apply: back on the launcher it is the Solid look with an opaque terminal, no cursor trail, no effect, and the Reduce idle activity switch is on; picking another look in Appearance brings the old look back · pong, by hand.
- 2026-10-15 · Cursor trail Off · Appearance editor → Terminal → Cursor trail: Off stops the trail at once, a style turns it back on, Cancel restores the earlier state, and Settings shows the same on/off · pong, by hand.
- 2026-10-12 · PR #64 · App chips on a light chip (light theme or a light Look): the icon's ring is
  the dark one, not the white one; on the emulator the light chip's ring was only faintly pale
  (2026-10-14) · pong, by hand, night mode off.
- 2026-09-27 · voice panel · Keyboard → Voice input → Mic sensitivity (Normal, High): soft speech at
  arm's length is picked up on both; High lets in more of a nearby voice or video, Normal fewer;
  keyboard clicks, a fan or air conditioning alone never open a phrase on either · pong, by hand.
- 2026-10-12 · fix(appearance): Custom starts from the Look on screen · On pong, apply Clear,
  Done, reopen the editor, tap Custom: the sliders show Clear's numbers and the look does not
  change. Tweak Blur, tap Mist, tap Custom: the tweak is back. Undo: Clear again · pong, by hand.
- 2026-10-12 · PR #63 · The resting look at Clear and Mist on pong: the outer ~2 dp of every rim
  now mirrors the wallpaper (quarter-circle bevel along the true normal); it should read as a
  thick glass edge, not a double line. If it reads wrong, the profile in `GlassRefraction` is the
  dial, not the Looks.
- 2026-10-12 · PR #63 · Frame timing with Bend 30 and Dispersion 100 on pong: page slides and
  keyboard up/down at 90 Hz, no frame over 11 ms · `scripts/perf-baseline.sh`.
- 2026-10-12 · PR #63 · Dispersion fringing shows on the city wallpaper between 50 and 100%; on
  a blue test picture it was only measurable, not visible.
- 2026-10-12 · PR #67 · Export settings, then Import settings on pong as the default home: the
  launcher comes back as home within a few seconds with the same look, layout and terminal font
  size, and nothing asks to pick a home app.
- 2026-10-12 · PR #64 · The chip strip with your real usage on pong: Shizuku, Solid Explorer and
  the keyboard types as named chips, and typing "s" pins Shizuku.
- 2026-10-12 · commit "feat(agent-skills): pack the launcher and speech skills for tlstore" · With
  both tarballs from `scripts/pack-agent-skills.sh` unpacked into `~/.agents/skills/` and each
  linked from `~/.claude/skills/`, Claude Code and Codex each: open a pane beside the shell and
  wait on it; answer "how do I change the terminal font" from the docs, naming the settings path;
  with the speech skill, read one answer aloud and stop when told · pong, by hand.
- 2026-10-13 · commit "fix(dock): let a lone pinned-apps row's icons fill its bar" · The pinned
  apps alone on their dock or plank (no A–Z row, no extra keys), Docked and Floating, Icon size at
  its smallest, the default and its largest, with 5 and 10 apps, keyboard up and down: the bar is
  as tall as before and the icons fill it, centred between the ticks and the far rim; 10 apps
  never touch; an A–Z scrub's matches in that row are the same size; the Icon size slider reads
  the bigger icon, and adding an A–Z row in Layout shrinks the icons back to today's size · pong,
  by hand.
- 2026-10-15 · commit "perf(terminal): replay the grid between row crossings of a smooth scroll"
  · Drag, fling and let settle a long transcript in the normal buffer, one pane and split, with a
  transparent frame and an opaque one, keyboard up and down: every frame looks as before (no
  blank or stale grid, the row coming in at the bottom and leaving at the top is whole, the find
  overlay and the padding fill track the text); output arriving and the cursor blinking mid-scroll
  show at once; a pane snapshot (split reveal) mid-fling is whole; on Android 10 the
  `Terminal.scrollCache.draw` frames in a trace cost one node draw, not a render · pong, by hand.
- 2026-10-15 · commit "perf(terminal): keep a scrolled row's recording, record only the rows that
  came in" · Fill the transcript with mixed content (bold, 256 colours, a URL with underlines on,
  a kitty image if easy, and a few text sizing blocks: `printf '\e]66;s=2;Big\a\n'`, no probe
  exists in project-docs/verification), then drag and fling through it, one pane and split: every
  row lands where it did before, rows under a text block and the block itself stay whole and in
  place (also with the block's anchor just above the top edge), the cursor row looks right when it
  scrolls back in, and the row entering at the bottom is whole · pong, by hand.
- 2026-10-15 · PR #75 · The natural glass on pong at Clear, Mist and Tint: the rim reads lit from
  above and softer below, the band brightens toward the lit rim and the edge shows the wallpaper
  more clearly than the frost; it should read as real glass, not a double line or a halo. Frame
  timing with the Looks' values: page slides, keyboard up/down and a dock tap at 90 Hz, no frame
  over 11 ms · `scripts/perf-baseline.sh`, pong, by hand.
- 2026-10-15 · voice, by voice only (nothing over adb can start a dictation; the agent routes
  through the API passed on 2026-10-15) · In one sitting on pong: tap the waiting text on the
  dictation card to edit it (caret where tapped, keyboard under the card, ✓ inserts the edit, Undo
  and Redo, a 7-line scroll does not open the editor); a Polished list shows on its own lines and
  pastes whole in a shell with bracketed paste; a 90-second dictation with E2B on the GPU shows
  `cleanup early` lines in logcat while listening and the cleaned text lands 1–2 s after Pause,
  with `lang=en sent=N/M` lines and no `fallback:context_echo`; with a multilingual Whisper model
  Voice language Auto-detect names a non-English language on the card; an "um" is struck through
  and changed words are in the accent, Undo gives the as-heard text; the screen stays on while
  listening and sleeps after about 3 minutes untouched (timeout 30 s); the grab handle moves pill
  and panel, a sideways swipe never discards, a double tap returns it to the top-right · pong, by
  hand.
- 2026-10-13 · commit "feat(tai): stop a model that runs the phone under 448 MB free" and "fix(tai):
  book a memory stop as a stop, not a crash" · Start an E4B GPU load at about 3.7 GB free with a
  memory hog beside it: `tai logs` shows `oom_guard … stopped the model to keep the phone
  responsive`, the runtime goes away, the launcher and Shizuku stay up, and the Model centre and
  `tai logs` book it as a stop, not a crash. Not run by the agent: the 2 GB watchdog would unload
  first and a load like this froze the phone once · pong, by hand, with everything saved.
- 2026-10-14 · commit "feat(keyboard): slide a layout row's buttons open beneath it" · On pong the
  row opened in about 0.3 s with animations on, ripple included (`~/reports/devcheck-2026-10-15-pong/03-row-open-anim1.mp4`);
  judge whether that reads as the intended ~200 ms. TalkBack reads "name, second line, actions
  hidden" and offers Show/Hide. Typing in the layout editor with the phone's keyboard shrinks the
  XML field above it · pong, by hand.
