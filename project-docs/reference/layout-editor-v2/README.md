# Layout editor assets v2

Design proposal derived from pong's Screenshot_20261002-161646.png. No app behavior is changed.

Includes 22 surface SVGs covering statusbar, terminal, app icons, alphabets, extra keys and keyboard; eight control SVGs and eight Android VectorDrawables. Bars have horizontal and vertical variants; both surface modes have compact/expanded status and full/split keyboards. `editor-preview.png` is a deterministic assembled asset preview. `design-concept.png` is a built-in imagegen visual exploration, not an exact implementation specification. Its sample logos and layout should not be extracted as production artwork.

## Integration

Resolve the roles in `tokens.json` from the active launcher scheme; preview colors are illustrative. Import the control XML files into drawable resources and tint per state. Surface SVGs are design sources for the existing Canvas painters, not Android drawable XML. Keep live pinned icons and extra-key content in the miniature. Derive geometry from actual bounds rather than stretching SVGs or storing raster previews.

Docked pieces join with square internal corners under one outer clip. Floating surfaces use the existing per-surface shape policy. Draw the selection outline and lifted preview from that same shape. Separate selection outlines distinguish the app bar, alphabet strip and extra keys even where they share a material background.

No visible hint is required: selecting a movable bar reveals a move control and valid edge destinations; lifting it highlights those destinations and the hide button. Highlight only accepted drops, commit on release, and cancel on back or outside release. These are proposed interaction states, not implemented behavior. The terminal remains the canvas anchor unless existing placement policy permits a move.

The eye-off control opens hidden elements; each hidden-element tile carries a restore arrow and restores on tap. A count badge exposes hidden state. Hide/restore are reversible; trash is unnecessary because this action does not delete data. Empty hidden state has no badge. Use primaryContainer/onPrimaryContainer for selected controls, and expose selected state and descriptive accessibility labels without visible hint text.

Control glyphs are 24dp within at least 48dp touch targets. This follows [Android touch-target guidance](https://support.google.com/accessibility/android/answer/7101858). Color roles and interaction states follow [Material foundations](https://m3.material.io/foundations/).

## Verification boundary

All SVG and Android XML files are parsed by the generator; the SVG preview is rendered with librsvg and inspected. No Android build or device interaction test was run because this deliverable changes artwork sources only. Production readiness still requires integration, drag/cancel/restore tests, accessibility alternatives to dragging, and checks in portrait/landscape, Docked/Floating, keyboard up/down, split panes, inherited/detached surfaces, light/dark/black and launcher schemes, and large font/density configurations. Package/bootstrap/signing identity is unaffected for all three editions.

Regenerate with `python3 project-docs/reference/layout-editor-v2/generate.py`, then render using `rsvg-convert project-docs/reference/layout-editor-v2/editor-preview.svg -o project-docs/reference/layout-editor-v2/editor-preview.png`. The generator also writes a download archive (`layout-editor-v2.zip`), which is not committed. Generation uses the existing reference pack's glyph grammar.
