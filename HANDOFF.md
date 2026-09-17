# fix/band-veil — one pane, one veil

## Done
- ChromeInk: `paneOf()` — STATUS_BAR is a co-tenant of WINDOW_BAR's pane; band rect and band
  glass now key on the pane, so the status content is measured on `terminal_window_bar_background`.
- ChromeInk: `resolveBand` settles the pane's one veil as the strongest co-tenant demand and
  re-resolves each band on it (`resolveAt`/`resolveAtStop` take a floor alpha).
- OnGlass.resolveUnder: resolve on a veil already decided elsewhere.
- GlassSurfaceFactory.statusBarExtensionSurface: pane glass, no question, optional borrowed veil.
- TermuxActivity: the inset strip uses it; `STATUS_INSET_STRIP_CONTINUES_PANE_VEIL` is D1's switch.
- Tests: three new in ChromeInkTest; FakeChromeSurfaces can resolve views now.

## In progress
- none.

## Next
- Device check of the strip under the system status bar (JVM cannot show it).

## Gotchas
- `joinsDock`/`onPlank` build the pane without a band, so no veil is drawn there — pre-existing.
