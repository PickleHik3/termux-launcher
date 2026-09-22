# Help guide clips

Short, muted gesture clips for the entries in `HelpTopics.java`, recorded on Waydroid
(600×1300 @180 dpi, launcher 0.2.37 dev f2b7a2a8) on 2026-09-18. Each clip is a cropped strip
of the phone frame, not a full-screen grab; where a gesture and its effect sit far apart the strip
stacks two or three bands (status bar over the key row, for example). A translucent finger disk is
burnt in for every tap, hold and swipe.

`manifest.json` is the source of truth: every topic id with its status, the clip ids it uses, an
alt sentence, a note on what the clip shows, and per-clip width, height, duration, size, crop
bands (frame coordinates) and gesture timings. `preview.html` shows everything in a grid; open it
next to the media.

## Coverage (43 entries)

| status | count | meaning |
|---|---|---|
| covered | 34 | a clip made for that entry |
| partial | 2 | `az` (drag-up not shown), `touchpad` (one finger only) |
| shared | 4 | fixes reusing another clip: `fix_keyboard`, `fix_dock`, `fix_action`, `fix_display` |
| blocked | 3 | `move_panes`, `setup`, `display_apps` — see the notes in the manifest |

## Embedding

```html
<video class="help-clip" src="palette.mp4" poster="palette.jpg"
       width="600" height="350" autoplay muted loop playsinline
       aria-label="Swipe up on the space bar, type split, results appear."></video>
```

- Always `muted` + `playsinline`; iOS refuses autoplay otherwise. Keep `loop` so the reader can
  catch the gesture again.
- Respect reduced motion: with `@media (prefers-reduced-motion: reduce)` drop `autoplay` (show the
  poster and let the reader press play), or swap in the poster image.
- Width is 600 CSS px at 1×; render at 300 px for a phone-sized help column. Heights vary per clip,
  so read `width`/`height` from the manifest to reserve space and avoid layout shift.
- Use the manifest `alt` for `aria-label`; it is one plain sentence.
- Posters are taken just after the first gesture; a few open slowly (palette, drawer) so the poster
  shows the gesture rather than the result.

## Caveats

- Recorded on Waydroid with software GL; timing is real but the device was slower than a phone.
- Appearance was lightened for capture (base blur 0, grain 0, solid material); the theme is
  otherwise the default teal.
- `settings`, `themes`, `fix_*` settings pages and `start_stop` end on the opened screen rather than
  returning, because Back from Settings did not return in this Waydroid session.
