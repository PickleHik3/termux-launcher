# Device checks owed

Built, merged, not yet seen on a device. The list a drop or a cut is checked against first.

**Agent** items can be driven over adb: Waydroid, or pong with the developer's go-ahead.
**Developer** items need a hand on the phone: feel, frame timing, a lock screen, taste.

One line per item: date · commit or PR · what to look at · what passes · how (device, route).
Verified items are **deleted**, never ticked; the PR, the commit or a bug is the record. An item
lands here in the same commit or PR as the change it belongs to, whenever a hand-over still owes
a device check. Before a drop or a cut, work through the Agent list and hand the Developer list
over; an item two releases old is re-judged and dropped if nothing rides on it. The file stays
under twenty items; past that, the oldest are re-judged first.

## Agent

- 2026-10-12 · PR #63 · Floating style: the keyboard capsule and the dock card catch the Shine
  light on their top-left and bottom-right corners and not the other two, at Shine 100% · Waydroid,
  managed wallpaper set; Layout tab → Floating, then Look → Custom → Edge.
- 2026-10-12 · PR #64 · A contact chip with a photo set in Contacts: the initial swaps to the photo
  with no shift, and a reopened palette shows the photo at once · Waydroid; set the photo in the
  Contacts app, open the contact twice from the palette.
- 2026-10-12 · PR #64 · App chips on a light chip (light theme or a light Look): the icon's ring is
  the dark one, not the white one · Waydroid, night mode off.
- 2026-10-12 · PR #65 · The widget picker opens without closing the launcher on Android 10 and 11
  (#62) · an API 29 or 30 emulator, or the reporter on the next drop.
- 2026-10-12 · PR #67 · A restored model works and reads like a fresh download: `tai speak` with
  KittenTTS after Import AI models, and its Model centre row shows the same size a fresh download
  shows (Waydroid read "32.3 MB" after a restore; the catalog says 90 MB) · Waydroid.
- 2026-10-12 · PR #67 · Export AI models with a model over 1 GB: leave Settings mid-copy, Cancel
  from the notification removes the half-written file and keeps finished models; Export again
  skips them · Waydroid.
- 2026-10-12 · commit "feat(keyboard): paste a fresh screenshot from the paste key" · With the
  storage permission granted and text copied first: take a screenshot, open the keyboard; the "v"
  key's paste corner shows the photo where the paste glyph sat; paste types a path to a copy of
  the screenshot under `$PREFIX/tmp/clipboard`; paste again gives the clipboard text and the glyph
  is back to paste; take another screenshot and paste with a modifier: the clipboard text, while
  the photo still shows · pong with the go-ahead.
- 2026-10-13 · commit "feat(voice): edit the dictation card's text in place" · Tap the waiting
  text: a blinking caret where tapped, the keyboard up under the card and the card above it
  (docked and floating); type, backspace, arrows; ✓ inserts the edit into the shell, never into
  the card; Undo shows the text before the edit, Redo the edit. Tap while listening pauses; tap
  while "Cleaning up…" keeps the text as heard and the cleanup never lands. A finger scroll of 7
  lines and a sideways swipe do not open the editor. On the Display place ✓ types into the
  display afterwards. With the in-app keyboard off the phone's keyboard comes up and types; at
  font scale 1.3 the caret sits on the line · Waydroid.
- 2026-10-13 · commit "fix(voice): keep a Polished cleanup's line breaks to the card and the
  insert" · A Polished list or two paragraphs show on their own lines on the card; ✓ in a shell
  with bracketed paste keeps them, in a bare shell they arrive on one line and nothing runs ·
  Waydroid, Polished cleanup on.
- 2026-10-13 · commit "feat(dock): let the largest pinned icon reach about 56dp" · Appearance →
  Icon size at its top reads about 56 dp on Docked and 55 dp on Floating, the dock grows with it
  and lifts over the keyboard without clipping; the shipped size and the old tops (47 / 45 dp) draw
  as before · Waydroid, both Styles, keyboard up and down.
- 2026-10-13 · commit "fix(dock): never draw a pinned icon wider than its slot" · Ten apps per
  page at the top Icon size on a 360dp-wide screen: the icons shrink to fit their slots and never
  touch or overlap, on the pinned row, the A–Z preview and a page swipe; seven apps keep the
  slider's size · Waydroid, `wm size` for 360dp, both Styles.
- 2026-10-13 · commit "fix(launcher): scale an icon pack as one, not icon by icon" · With a PNG
  pack that leaves a margin: its icons in the dock, the drawer, a pinned override and a
  notification look as big as stock icons beside them, not cropped and not blurry. With a line
  or glyph pack (Whicons, Lines): glyphs keep their sizes and placement relative to one another.
  A pack with no margin looks as it always did; an app the pack does not map keeps the pack's own
  back plate · Waydroid, icon packs from F-Droid.
- 2026-10-13 · `eff06de21`, `c111b7ed5` · Dictation cleanup from context on the phone's E2B: the
  anchor dictation and the "translate this into French" case from
  `project-docs/reference/voice-ai/voice-cleanup-context-rework-2026-10-13.md`, Light and Polished;
  no "pinned app console" or "100%", the French request stays English, nothing falls back as
  `refusal_or_answer`; cleanup's feature check passes its 500-word dictation; first-token time
  within about 0.2 s of the old prompt's · pong with the go-ahead, the same request through
  `/v1/chat/completions`.
- 2026-10-13 · commit "feat(voice): clean dictation at phrase pauses while the user speaks" · A
  90-second dictation with E2B on the GPU, Polished then Light: logcat `VoiceSessionCleanup` shows
  `cleanup early` lines while listening and a `cleanup session` line with `cleanupMs` about 1–2 s
  after Pause (was about 8 s); phrases keep transcribing at their usual speed while a pass runs; the
  card shows the text as heard until it swaps once to the cleaned text with marks; the joined text
  reads as one passage (no repeated sentence at a part boundary, a counted list on its own lines);
  no `fallback:context_echo`. Then: edit the text mid-dictation (no cleanup lands), Pause and resume
  (only the new speech is cleaned), and a CPU-only cleanup model (no `cleanup early` lines) · pong
  with the go-ahead.
- 2026-10-13 · commit "test(voice): keep a loading cleanup model off the main thread" · Start a
  dictation while E4B loads on the GPU (11–19 s): the card and the launcher stay responsive. If
  they freeze, take the launcher's Java stacks mid-freeze (`debuggerd -j <pid>`) and read
  `/proc/pressure/memory`: a `main` thread parked in `nativePollOnce` means the stall is GPU or
  memory contention from the load, not a wait on the runtime · pong with the go-ahead.
- 2026-10-13 · commit "feat(tai): plan a load on the typical measured drop, not the largest" · E4B
  dictation cleanup on the GPU at 2048 is admitted with about 3.8–4 GB free (`tai logs`: a
  `load_ok … gpu ctx=2048`, no `load refused`), both with this version's own samples and right
  after an install that cleared them (the prior); any refusal reads `needs X MB + 768 MB floor = Y
  MB free, Z MB available` with the GPU's figure, not the CPU seed · pong with the go-ahead.
- 2026-10-13 · commit "feat(tai): stop a model that runs the phone under 448 MB free" · Start an
  E4B GPU load at about 3.7 GB free with a memory hog opened beside it: `tai logs` shows an
  `oom_guard … stopped the model to keep the phone responsive` line, the runtime goes away, the
  launcher keeps answering throughout, and dictation keeps the text as heard; the same while a
  long chat answer generates (cancelled and unloaded, the runtime stays) · pong with the go-ahead,
  `adb logcat -s TaiMemory`.
- 2026-10-13 · commit "fix(tai): book a memory stop as a stop, not a crash" · After the guard ends
  an E4B GPU load mid-dictation: the card keeps the phrases already heard and says "Voice input
  stopped: the speech model closed", the pill settles; within 2 min the next dictation's cleanup
  logs `load held back on gpu` and keeps the text, `tai load gemma-4-e4b` goes ahead at its window;
  after 2 min cleanup loads E4B on the GPU at 2048 again (no half window) · pong with the go-ahead.
- 2026-10-13 · commit "feat(voice): detect the spoken language with Voice language on Auto" ·
  Multilingual Whisper (Base, then Small), Voice language Auto-detect, an English keyboard layout:
  dictate a Spanish clip through the fake mic; the strip reads "Listening… · Spanish" (or
  "Transcribing… · Spanish") and the text is Spanish, not English; `logcat -s VoiceInputSession`
  shows `language=es/detected heard=es@…` on the first kept phrase and `language=es/forced` after.
  A fresh dictation of an English clip: no language on the strip, `language=en/detected`. Note each
  phrase's `detectMs` against its `decodeMs`/`decodeSteps`, and the probability a one-word command
  scores (the 0.8 bar) · Waydroid, fake PipeWire mic.
- 2026-10-13 · commit "feat(voice): send only the sentences that need it to the cleanup model" ·
  A real English dictation with "uh"/"um" in clean sentences and one stutter or "no wait": no
  filler is left, the clean sentences come back word for word, and logcat `VoiceSessionCleanup`
  shows `lang=en sent=N/M` with N below M (0 for a part with nothing to fix, `fallback:unchanged`
  for a clean dictation). Then a Spanish (or German) dictation with "eh"/"äh": the fillers go,
  the text stays in that language (`lang=es`), never English · pong with the go-ahead, Light and
  Polished.

## Developer

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
- 2026-09-27 · parked list `active/device-checks-pending-2026-09-27.md` (voice redesign, keyboard
  off, tai speak): fold what still applies into this file, drop the rest.
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
