# Landscape round: five defects from the 17 Sep 2026 Waydroid review (2026-09-17)

Source review: `.lavish/landscape-review/` (7 items). Items 04 (split-pane corner tabs) and 06
(extra-key accessibility labels) were **excluded by the user**. Item 08 (Appearance/Layout editor
surfaces read as unfinished) was raised by the user on review, measured, and is **in the round**:
D6 = full redesign, one shared editor shell. Review page: `.lavish/landscape-spec/index.html`.

Every root cause below was read from source at `dev` @ f6ff2a97. Four were re-verified by hand:
`miniatureHeightPx`'s landscape branch, `memoryKey` vs `arrangementKey` for `status_compact`,
`isDockRailShown`'s item-count gate, and `syncAzStandaloneStrip`'s slot source.

## Decisions

| Item | Rule |
|---|---|
| 01 Layout card | `LayoutEditorPlan.miniatureHeightPx`'s landscape branch derives the frame from `screenWidthPx / frameAspect` with **no height bound** (:270–276), so at 1300×600 it claims ~600px of a 600px screen and the rows land off-card; the card root is a `wrap_content` `LinearLayout` with no outer scroller (`layout_editor.xml:12`), so `rowsHeightCapPx`'s documented 96dp floor is unreachable. **D1: ship all three** — bound the landscape frame by the room actually left, add an outer scroller to the card, and label the state where the toggle shows an orientation the device is not in (`liveFollows()` already computes it). The label is product copy. Toggle semantics (`mShownOrientation` vs `mDeviceOrientation`) do not change. |
| 02 A–Z empty pinned | **Cause not established.** Two candidates were checked and rejected: the strip path measures slots from the A–Z row and canvas, not the pinned row (`TermuxActivity.syncAzStandaloneStrip:7534–7551`), and every slot count is floored at `Math.max(1, maxButtonCount)` with a default of 7. **D5: reproduce first, report back, no fix until a failing test or device reproduction exists.** Separately confirmed and real regardless: "is the apps row shown?" has two answers — `PlaceChromePolicy.appsShown` is config-only while `isDockRailShown()`/`syncPinnedAppsHost` also require `hasPinnedItems()` (`TermuxActivity:9376–9380, 9452–9469`). That fully explains the miniature-vs-live mismatch and puts the app drawer's only pull gesture on the surface that disappears. |
| 03 Display setup | `X11CliInstaller.hasKeyboardData()` (:137–140) is called only from `X11PaneFrame.applyRunning()` (:611), which nothing on the resume or place-change path invokes — only a display running-state transition refreshes it. **D2: ship all three** — re-derive readiness on arrival (place-change and resume), name `xkeyboard-config` in the message, and make the guide reference tappable (precedent: `StatusWidgetPrivilegedGate.promptForShizuku:66–86`). Extract `DisplayEmptyStatePolicy.decide(enabled, hasKeyboardData)` as a pure sibling of `DisplayBackPolicy`, returning the message resource and start-button visibility; the filesystem probe stays outside the policy. Note the shipped string already says "the Linux display guide names it" — the gap is the package name and anything tappable. No in-app package install: no such mechanism exists and inventing one is out of scope. |
| 05 Place navigation | A fully-peeked neighbour draws at ~7.5% fill / ~20% stroke / ~62% glyph (`StatusBarLensView:295, 313–315`), ×0.6 more when Display is stopped (:296); its target is the visible half-tile plus `dp(8)` (:390–404). The sizes, ink and slop are private fields on the view (:52–66) and **`StatusBarLensView` has no test file**. **D3: brighten and enlarge the peek only** — no place label, no new switch. Lift `ICON_DP`, `PEEK_SHARE`, `COMPACT_ICON_DP`, `HOME_X_DP`, the ink multipliers and the slop into `StatusBarLensPolicy` (or a `StatusBarLensMetrics` beside it) taking bar size + compactness + axis + places + current, returning rect, ink and minimum target per place; the view only paints. Corrections to the review: weather is centred **only on Home** (`StatusStatsClusterPolicy.centeredReversed`), and no place *name* is rendered in status chrome in any state. |
| 07 Chrome budget | No class owns the vertical budget: `EdgeStackPolicy.contentInsets` is purely additive and never receives the container height (:349–371), and the keyboard is not an element in that stack. `StatusBarEdgeGeometry.thicknessDp(edge, capsule, compact)` (:76–83) takes no height, orientation or density. **D4: scope `status_compact` per orientation with a migration** — it moves from `memoryKey` (currently `place.<x>.status_compact`, no orientation segment) into the per-place-per-orientation scope beside the three sizes ADR-0001 migrated, with a `MIGRATION_VERSION` bump. The migration **seeds both orientations from the existing single value** so no current choice is lost; a landscape default applies only where nothing was ever stored. Also: add a canvas-floor policy beside `contentInsets` taking container height, orientation, keyboard height, layout and metrics; consolidate the compact height duplicated in `DockLayoutPolicy.compute:370`; make the Appearance preset header shrink before the rows are cut (it is fixed at `CARD_HEIGHT_DP=68` while `applyRowsCap` only ever shrinks rows). |
| 08 Editor shell | Measured on the review captures: slider rows sit on a **32 dp** pitch at both densities (48 dp is the platform minimum touch target); the Corners slider gives a 0–24 dp value an **~892 dp** track; Docked/Floating are 238 dp segments while Solid/Glass/Frost are 192 dp on the same line; preset thumbnails (`CARD_WIDTH_DP=42`, `CARD_HEIGHT_DP=68`) are near-identical dark outlines; content is sliced rather than scrolled; no grouping, and a ~150 px miniature sits in a 1300 px card between two ~550 px gutters. Through-line: **controls inherit the card's width instead of declaring their own.** **D6: full redesign** — one shared shell for Appearance and Layout, with a common header, section model and control kit, so a rule fixed once holds in both. Sizing rules live in the existing metrics classes (`SurfaceEditorPillMetrics`, the Layout editor's plan), not in view code, so they stay assertable. **Sacred:** the Appearance editor's commit-on-Done behaviour, the miniature's drag model and the layout store's schema — this is look and layout only, no behaviour rides along. |
| Out of scope | Items 04 and 06. An in-app package-install flow. Redesigning the lens or wall paging. Landscape resource qualifiers. Any portrait behaviour change. Performance/motion verdicts (Waydroid cannot judge jank). Clean-install comparison except where item 02's reproduction needs it. |

## Seams

| Item | Seam | Status |
|---|---|---|
| 01 | `LayoutEditorPlan.miniatureHeightPx` / `LayoutEditorPlanTest` | exists — the landscape case fed portrait dims (1080×2400), which is why it passed |
| 02 | `AzPreviewTargetPolicy` + a Robolectric case in `SuggestionBarAppDiscoveryTest` with `pinnedItems` empty | needs a content input; every existing test seeds pinned items first |
| 03 | `DisplayEmptyStatePolicy` (new, pure) | extract, shaped like `DisplayBackPolicy.decide` |
| 05 | `StatusBarLensPolicy` absorbing the view's constants | extend |
| 07 | a canvas-floor policy beside `EdgeStackPolicy.contentInsets` | extend; nothing today takes a container height |

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| P1 | `fix/layout-landscape-cap` | Item 01: landscape bound, card scroller, orientation-mismatch label, corrected + extended `LayoutEditorPlanTest` | — | `:app:testDebugUnitTest` green; rows reachable in landscape on all three places at both densities |
| P2 | `fix/az-empty-pinned` | Item 02 **reproduction only**: a failing test or a recorded device reproduction plus the failing profile's layout record, then stop and report | — | a test that fails for the reported reason, or a written statement of why it could not be reproduced |
| P3 | `fix/display-setup-refresh` | Item 03: `DisplayEmptyStatePolicy` + tests, readiness re-derived on arrival, package named, tappable guide route, help target | — | `:app:testDebugUnitTest` green; install the package in Terminal, return to Display, see it ready |
| P4 | `feat/place-nav-legible` | Item 05: lens sizes/ink/targets lifted into the pure policy with tests, peek brightened and enlarged | merge **after** P5 | `:app:testDebugUnitTest` green; marks legible at 1300×600 |
| P5 | `feat/landscape-chrome-budget` | Item 07: per-orientation `status_compact` + migration, canvas-floor policy, consolidated compact height, two-way Appearance header cap | merge **before** P4 | `:app:testDebugUnitTest` green; an explicitly expanded landscape bar survives a restart |
| P6a | — (docs) | Item 08 design: `project-docs/editor-shell/DESIGN.md` — the shared shell's header, section model and control kit, with every sizing rule as a number, and how both editors move onto it | — | agreed with the user on a review page before any code |
| P6b | `feat/editor-shell` | Item 08 build: the shell's rules in the metrics classes with tests, then Appearance and Layout moved onto it | P6a agreed; P1 and P5 merged | `:app:testDebugUnitTest` green; both editors at both densities, portrait and landscape, against the review captures |

P4 and P5 share no files (lens view/policy vs edge/geometry/store), so all five run in parallel;
P5 merges first because D4 moves the key P4's compact check reads. Conflicts are resolved by the
orchestrator, not the agents. Nothing here touches applicationId, bootstrap, manifests or signing,
so the three editions need no per-edition decision.

## Notes

- **Item 08 designs before it builds.** A redesign agreed as prose is a redesign nobody agreed to;
  P6a produces the shell as numbers and mockups and goes in front of the user first.
- **P6 overlaps P1 and P5** (the Layout card's scroller, the Appearance preset header). P6b builds
  on them rather than owning them — item 01 is the high-severity blocker and does not wait behind a
  redesign.
