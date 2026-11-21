# Appearance editor: card order and sheet geometry (2026-11-01)

Settled with the developer on 2026-11-01 (grilled from the Lavish review
`.lavish/appearance-complete.html`, which stays the review artefact; this file is the record).
Built by the appearance session after the layouts-per-kind work (ADR 0020,
`project-docs/active/layouts-per-kind-2026-11-01.md`) is on `v2-redesign`. The pane-borders
subcard (#206) is a separate, earlier wave: `pane-borders-2026-11-01.md`.

## Decisions

1. **Every control stays.** No control is hidden, ranked by recency or moved behind a mode. Each
   card is one scroll in a stable order: *Frequent adjustments*, *Layout and behaviour*,
   *Look and material*, *Related settings*. A row of section chips at the top of the body scrolls
   to each group and scrolls away with the body (no sticky tabs, nothing collapsible).
2. **Elements** sits at the end of *Layout and behaviour*, one chip tap away.
3. **Names.** The terminal side's card stays `WorkspaceCard` (the glossary's Workspace: the
   session's windows and the display windows under the full chrome). Which kind it edits,
   Terminal page or Display page, is the Where chip. No "Window" wording anywhere.
4. **Display card** is the Workspace card projected onto the Display page kind with its own stored
   layout under ADR 0020. No Display-specific setting is invented, and the Terminal surface's text
   controls (text size, contrast, font) are not offered for a Linux app's pixels.
5. **Scales** 0.84 at peek, 0.50 at half, 0.28 at full (`EditorFramePose.HALF_SCALE`, `FULL_SCALE`;
   today 0.6 and 0.44), inside the existing fit-to-space rule with its 0.2 floor. The sheet chrome
   stays what the code has: 24 dp grab + 48 dp header row (72), the 56 dp chip row (4 + 40 + 12),
   the navigation inset, Undo and Done in the separate editor bar (48 dp, 8 dp foot gap, standing
   on the card's header at peek). The half line and its label between frame and card stay as they
   are (16 dp row, 20 sp label); no caption is added.
6. **Fallbacks.** Short screens fit first (the floor), then collapse half and full into one editing
   detent only when neither body can hold one complete control plus the chrome; never a negative
   body. Landscape reuses the height rule; a side-by-side arrangement is designed separately or not
   at all.
7. **Drag.** During a sheet drag the live preview is transformed (scale and translation) and the
   layout settles at the detent; no reflow per frame, no bitmap copy, no continuous animation.
   `EditorFramePose` stays the one owner of both the preview pose and the sheet height.
8. **Reset.** One Reset per card, acting on that card's layout and look for the current page kind,
   and the confirmation names both ("Reset the Home card for Home pages"). Row resets and source
   indication are unchanged. Reset never silently detaches or re-links surfaces.
9. **Gating.** A geometry-only skeleton (the two scales, the chip row, the recomputed bodies) goes
   on pong before any card is reordered. Every wave gets a 1.3× font scale and a 360 × 640 pass on
   the emulator; the phone is the truth for the rest.

## Geometry, reference 412 × 916 dp, top inset 52, navigation inset 24, visible launcher 840 dp

`sheet = H − (top inset + 16) − ceil(scale × L) − 8 − 16 − 8` (`EditorFramePose.sheetPxFor`);
`body = sheet − 72 − 56 − nav inset`. Peek is `header + nav inset`, capped at half.

| Detent | Scale | Frame height | Sheet | Body (header, chip row, nav inset off) |
|---|---|---|---|---|
| Peek | up to 0.84 | fits to the space above the 96 dp sheet | 96 | none |
| Half, today | 0.60 | 504 | 312 | 160 |
| Half, new | 0.50 | 420 | 396 | 244 |
| Full, today | 0.44 | 370 | 446 | 294 |
| Full, new | 0.28 | 236 | 580 | 428 |

Rows keep their measured heights (56 dp ordinary, 72 dp slider, 32 dp section heading, 48 dp
targets) and grow with the font scale; labels and controls are never scaled with the preview.

## Orders

**Home** — Frequent: Look, Icon size, Label size, Columns, Rows · Layout and behaviour: Folders,
Numerals, Glass widgets, Two-line labels, Label shadow, Page dots, Transition, Wallpaper scroll,
Elements · Look and material: Frost, Opacity, Tint, Colour, Corners, Grain, Rim · Related:
Widgets, Global, Gestures, Pages (when offered).

**Workspace (Terminal / Display page)** — Look, Opacity, Frost · Elements, the shared Minimal
switch · Tint, Colour, Corners, Grain, Rim · Global.

**Appearance (Global)** — Theme, Change wallpaper, Wallpaper colours, Contrast · then the
material-engine rows; colour matching stays next to wallpaper.

**Dock** — Dock type, Height, Bars · then the rest; style stays global, type is per kind.

**Keyboard** — Height, Type, Key opacity, Opacity, Move both opacities together · key geometry ·
Layouts and theme links stay reachable.

**Terminal surface** — Text size, Contrast, Opacity · Pane borders (#206 subcard) · Split gap,
Margins, Font · Cursor trail, Effect.

**Status bar** — Indicators, Clock face, Position · then glass detail.

**Widgets** — Look, Opacity, Frost · every glass control kept; widget content stays on Home.

**Menus and dialogs** — Look, Opacity, Frost · every material setting kept.

Within each card the remaining rows keep their current relative order inside the group they fall
into. Capability-gated rows stay gated.

## Out of scope here

The page's HTML browser, its union preview of capability-dependent controls and its slider limits
were illustration. The page-layout override and the "This page" scope go with ADR 0020, not with
this round.
