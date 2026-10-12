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
