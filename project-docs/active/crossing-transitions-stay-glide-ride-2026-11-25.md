# Crossing transitions: Stay, Glide or Ride (proposal, 2026-11-25)

Status: **approved 2026-11-25 (section 6); steps 1–6 of the migration order built**
(`place/CrossingPath`: rows, keyboard, glass cards by element, the status bar's verb, two Home
ends) and Home's keyboard as an overlay for the side A–Z bar (`HomeKeyboardCover.sideRailKeyboardPx`).
Not built yet: the per-Home-page keyboard memory and its feed into the two Home ends
(`StackRenderer.setHomePageKeyboards` has no caller), the status bar riding on an edge change off
the top edge, and the Terminal-only rows under the keyboard riding with their page.
Base: v2-redesign 3f0366d37.
Paths are under `app/src/main/java/com/termux/app/` unless they say otherwise.

## The problem

The layout editor (#326) lets each page kind place the status bar, apps row, A–Z bar and extra
keys on any edge, in any order, over or under the keyboard, and the keyboard can be up on one
side and down on the other. So one element can stand at a different rect on each side of a
crossing, or on one side only. Today each case has its own answer (glass cards per rest, the
keyboard's column, the A–Z bar's per-side cards, the shared row's vertical blend). The
bands-under case has none: with Home's keyboard down the apps row stands at the screen's bottom,
on the Terminal it stands under the keyboard above the extra keys, and it is one view. The
developer wants one rule that every element uses.

## 1. Inventory (what moves today)

`Element` holds only STATUS, APPS, AZ, EXTRA_KEYS (`place/Element.java:16-28`). The dock hosts,
keyboard, bands, foot and Peek card are `RestCards` pieces (`place/RestCards.java:94-142`). The
scene pairs the two rests' cards **by view**, not by element (`StackEnd.sameCard`,
`place/StackEnd.java:156-160`). A pair is shared when the card id matches and the rect agrees
within 0.5 px.

| Element | Views | Per-rest answer | Crossing today |
|---|---|---|---|
| Status bar | one (status row + top stack) | `CardId.STATUS`, top only (`RestCards.java:1225-1238`) | slab stays when both ends share edge and outline, otherwise rides a page (`statusbar/TopStackScene.java:126-175`); content alpha fade (`place/CrossingViews.java:105-111`) |
| Apps row | **one** `SuggestionBarView` in `place_apps_bar_host` | rides on the DOCK/PEEK card (`RestCards.java:1252-1282`) | lane: tx = side slide, ty = `SideCards.laneTyPx` (`StackRenderer.java:641-681`); a row both show eases its ty (`place/SideCards.java:109-120`) |
| A–Z bar | `AZ_EDGE` (Home), `TERMINAL_AZ_EDGE` | one shared card, or one per side when the rects differ (`splitAz`, `RestCards.java:497-513`) | tx from `cardTx` (`StackRenderer.java:771-780`), ty 0 |
| Extra keys | `KEYS_CARD` / `TERMINAL_HOST` | Terminal's card (`RestCards.java:116-123`) | tx, ty from the card (`StackRenderer.java:565-575`) |
| Dock glass host | `accessory_surface_host` | `HOST`, Home's card | `cardTy` + plank, tx via plank (`StackRenderer.java:530-553`) |
| Terminal host (Docked) | `terminal_dock_host` | `TERMINAL_HOST` | slides with the Terminal (`StackRenderer.java:735-752`) |
| Foot | `dock_foot_host` | shared foot (`RestCards.java:439-493`), column foot (`:630-646`) | ty only, never slides (`StackRenderer.java:755-759`) |
| Docked keyboard | `accessory_keyboard_column` | `KEYBOARD_COLUMN` / `TERMINAL_KEYBOARD` (`RestCards.java:523-620`) | `ownPlace`: ty lift only, no tx (`StackRenderer.java:583-589`); `KeyboardColumn.leavingTxPx` has no caller (`place/KeyboardColumn.java:83-88`) |
| Floating keyboard | `floating_keyboard_host` | `FLOATING_KEYBOARD` (`RestCards.java:1415-1431`) | `cardTx` computed (`StackScene.java:424`), no writer reads it |
| Bands under keyboard | `accessory_under_keyboard_stack` | `UNDER` (`RestCards.java:1349-1360`) | ty = −share, no slide (`StackScene.java:579`); Home stands the Terminal's bands when both are up (`KeyboardColumn.java:44-50`) |
| Peek card | on HOST or the Terminal's host | shared when at one rect (`RestCards.java:689-700`) | slides with its side |
| Page bar, side edge stacks | `page_switch_layer`, `place_edge_stack_*` | none | the content column, not moved by a crossing |

Findings that shape the proposal:

- **The scene is already pure in p.** `StackScene.at(from, to, p, …)` (`StackScene.java:312-321`)
  frames p = 0 as the leaving rest and p = 1 as the arriving rest, so a turned-back swipe simply
  runs p backward and a landing writes nothing. Nothing in the renderer allocates
  (`StackRenderer.java:459-614`).
- **"Side" means page kind, not crossing end.** `StackRenderer.end()` maps a page to Home's or the
  Terminal's `StackEnd` (`StackRenderer.java:380-382`). Home ⇄ Home and Terminal ⇄ Terminal pass
  the same end twice, so every card counts as shared and nothing slides
  (`StackScene.java:404, 470-483`). The A–Z bar on a side edge with Home's keyboard up at one end
  re-sizes for this reason (ADR 0032, 11-24 round 3).
- **The apps row's content is global.** `SuggestionBarView.refreshPinnedItemsFromRepository`
  reads `loadPinnedItems()` with no page (`SuggestionBarView.java:7247-7249`). The 11-25 ADR line
  "its content crossfades between the two pages' rows" therefore has nothing to fade today. Only
  its place differs.
- **Three answers already exist under different names:** the shared card that stays (ADR 0032
  §3), the shared row's vertical ease (`SideCards.laneTyPx`), and the side card that slides
  (`sideSlide`). They are the three verbs below, applied element by element.

## 2. The classes a rule must cover

| # | Class | Example |
|---|---|---|
| C1 | Both sides, same rect | status bar on top; keyboard up at both rests |
| C2 | Both sides, same size, different position on the same edge | apps row in the bands-under case; a shared row with Home's 16 px drop |
| C3 | Both sides, different size | A–Z on a side edge (different length); Peek against the keys card |
| C4 | Both sides, different edge | status bar on top on Home, at the bottom on the Terminal |
| C5 | One side only | extra keys (Terminal only); keyboard up on one side |
| C6 | Content differs | status content (Home wears none); per-page apps lists if they ever come |
| C7 | Order changes | apps above the keys on one side, below on the other |
| C8 | Keyboard up on one side only | Terminal up, Home down |
| C9 | Docked vs Floating | different formulas, the same verbs |
| C10 | Home ⇄ Home, Terminal ⇄ Terminal vs Home ⇄ Terminal | both ends the same kind |
| C11 | Turned back halfway | the drag reverses at p = 0.5 |

## 3. Candidate rules

### A. Shared-element morph

Every element on both sides moves its rect from rest to rest along p: translation for position,
a clip or outline reveal for size. Content crossfades when it differs, and one-side elements ride
their page.

- C1, C2: correct. C2 is exactly the bands-under case.
- **C3 breaks ADR 0032 §1.** A glass card cannot reveal a size by clipping: its cap, overscan and
  anchor belong to the bigger layout (0032, rejected option "union and clip"). For rows, a size
  difference means different content layout (A–Z letters spaced to length, a different icon
  count), so a clip cuts letters instead of respacing them.
- C4: the status bar flies across the terminal. C7: two opaque rows pass through each other at
  p = 0.5.
- Cost is low (transforms only), but it brings back the morph that ADR 0032 removed after 26
  commits.

### B. Everything rides with its page

Every element has an instance per crossing end, and each slides in or out with its page.

- C1 looks wrong: a status bar that is identical on both sides slides out and back in. The
  developer has already decided that shared cards stay (0032 §3) and that the keyboard is one card
  (11-25).
- Cost: a second instance of every row and card. A second keyboard is refused outright. A second
  glass card per element would spend the 8-slot Live budget (performance.md rule 3).

### C. Stay, Glide or Ride (recommended)

**Rule.** Each element's rect at each rest comes from the one per-rest answer. When a crossing
begins, one pure function reads the two rests and gives each element one verb:

```
path(element, from, to):
  same rect                                     -> STAY
  same size, same edge, order kept among shared -> GLIDE  (translate rest A -> rest B on materialShare(p))
  anything else (size, edge, order, one side)   -> RIDE   (each end's instance slides with its page)
content: STAY or GLIDE with different content   -> two content layers, alpha on materialShare(p)
```

- **STAY.** Writes nothing. Its rest offset at both ends is the same.
- **GLIDE.** The view is laid out once at its size. Each rest is an *offset* from that layout,
  never a new layout, the way the docked keyboard is down by translation
  (`StackScene.keyboardTranslationPx`). The frame is `blend(offsetA, offsetB, materialShare(p))`.
  The axis is the edge's normal, so a bottom-row element glides vertically only and never
  sideways.
- **RIDE.** Each end's card is laid out at its own rest and slides with its **end** (leaving or
  arriving) by `sideSlide`. An element on one side only is RIDE with its single instance. RIDE
  never re-sizes anything (ADR 0032 §1).

```
 Home (kbd down)        p=0.5              Terminal (kbd up)
 |  page        |   | ... | ...   |      |  terminal     |
 |              |   |     |       |      |[keyboard]  <- RIDE in from the right
 |              |   |     |[keys] <- RIDE|[extra keys]   |
 |[apps row]    |   |[apps row] ^ GLIDE  |[apps row]     |   (one view, ty eases)
```

What the user sees, per class:

| Class | Under C |
|---|---|
| C1 | STAY: nothing moves |
| C2 | GLIDE: the row eases vertically into its new slot while the side's other cards slide past it; bands-under apps row included |
| C3 | RIDE: two cards, one leaving and one arriving (as 0032 already accepted for Peek against the keys card and for A–Z) |
| C4 | RIDE: the top bar leaves with Home and the bottom bar arrives with the Terminal |
| C5 | RIDE with the page that has it |
| C6 | content alpha on the material curve; no fade when the content is equal (the apps row today) |
| C7 | RIDE for the rows whose order flips; the others still GLIDE or STAY |
| C8 | the keyboard RIDEs with its page (the 11-25 decision "leaves with its page"); with both up, `homeStandsTerminalsBands` makes it STAY |
| C9 | the verbs are identical; Docked and Floating differ only inside the per-rest answer (AGENTS.md "Dock styles") |
| C10 | works once "side" becomes "crossing end": Home ⇄ Home RIDEs the leaving Home's cards out and the arriving Home's in. This needs an instance per end for C3 and C4 elements (see Q1) |
| C11 | the path is chosen once at crossing begin from (from, to) and is symmetric, so p runs backward and lands exactly on the leaving rest |

**One owner, two phases.** The per-rest answer gives each element `(laidOutRect, restOffset)`.
The rest frame reads `restOffset`, and the crossing frame reads `path` plus the same two offsets.
`StackScene.rest(end)` stays `at(end, end, 0)`, so rest and crossing share one function (the
motion-invariants rule).

**Per frame.** `setTranslationX/Y`, a clip and an alpha per element, written from preallocated
frame arrays by the one writer (ADR 0028). No layout, no allocation. At rest nothing animates.

**Memory.** STAY and GLIDE add nothing. RIDE adds a second instance only for elements that both
ends draw differently. Rows reuse bounded icon stores (`LauncherIconStore`, `DockIconCache`), so a
second row holds views and RenderNodes, not new bitmaps. A second glass card takes one Live slot
within ADR 0029's 8-slot budget, about 1.1 MiB, only while on screen. The keyboard never gets a
second instance. Content crossfade needs a second content layer laid out at rest; for rows it
draws from the same icon store.

**Reverse states.** `path(e, a, b)` equals `path(e, b, a)`, and GLIDE's offsets swap. A way in is
its own way out.

**Replaces:** the per-view pairing in `StackScene.cards` (`StackScene.java:399-436`, by element
instead), the `ownPlace` and `cardMorphs` branches, `SideCards.laneTyPx`'s shared-row ease
(becomes GLIDE), `KeyboardColumn.leavingTxPx` (becomes RIDE), the status content fade (becomes
content crossfade), and `StackRenderer.end()`'s kind-to-side map (becomes end-to-side).

**Keeps:** the per-rest answer's own policies, because they choose rests and are not crossings:
`takeTerminalsColumn` / `homeStandsTerminalsBands`, the two-card rule, `splitAz`, the shared foot,
and the Docked and Floating formulas.

## 4. Recommendation

**C.** It is ADR 0032 generalised: 0032 already says "same rect stays, anything else rides", and
C adds the one verb 0032 lacked, a same-size translation, which is what the shared row and the
keyboard's down already do. Every verb is a transform, so nothing lays out in a crossing. RIDE is
spent only where the two rests truly differ.

**Premise challenge.** One rule covers every class except **C3/C4 in Home ⇄ Home**. When both
ends are Home and draw an element at different sizes, RIDE needs a second Home instance of it.
No transform of one view can do it without the clip that 0032 rejects. That case needs a decision
(Q1), not a cleverer rule.

**Migration order** (each step passes the property test before the next):

1. **The bands-under apps row → GLIDE.** Add `restOffset` for APPS to the per-rest answer (its
   y at Home's bottom minus its y under the keyboard). Read it in `writeLanes` through `path`. No
   crossfade, because the content is global. This also settles the 11-25 ADR line.
2. **The shared row's ease** (`SideCards.laneTyPx`) → GLIDE through the same path. This is a
   behaviour-preserving rewrite.
3. **The keyboard leaving with its page** → RIDE, with one writer for the column's tx (fixes the
   uncalled `leavingTxPx` and the unread floating `cardTx`).
4. **Pairing by element instead of view** in `StackScene.cards`. The glass cards (HOST,
   TERMINAL_HOST, KEYS_CARD, FOOT, AZ) route through `path` and keep today's answers.
5. **Status bar** (`TopStackScene`): edge change → RIDE; the content fade → content crossfade.
6. **Side → crossing end** (`StackRenderer.end()`, `StackEnd` per page), which unlocks Home ⇄ Home
   per Q1.

**Tests that pin it:**

- `CrossingPathTest`: the classifier over the classes table, including symmetry (`path(a, b) ==
  path(b, a)`).
- A property test extending `RestCardsCrossingTest` over `LayoutFixtures` × dock designs × Style
  × keyboard at each end × both directions × page pairs. It checks:
  - p = 0 and p = 1 frames equal each rest's;
  - no laid-out size differs between the last crossing frame and the landing;
  - GLIDE only when the sizes are equal;
  - no two GLIDE rows overlap at any sampled p (this guards C7);
  - frames sampled forward and backward agree at equal p (C11).
- `StackSceneParityTest` gains rest = `at(end, end, 0)` for every element's offset.

## 5. Open questions for the developer

1. **Home ⇄ Home with different rests** (keyboard up on one Home page only, so the A–Z length or
   bands differ). Choose one:
   - a second Home instance per such element (RIDE by end; costs one more row or card while
     crossing);
   - one keyboard state for every Home page, so Home ⇄ Home is all STAY;
   - accept a GLIDE with a clip for non-glass rows only.
2. **Order swaps (C7):** RIDE the swapped rows (recommended), or let them GLIDE through each other?
3. **Edge changes (C4):** RIDE (recommended), or GLIDE the bar across the screen?
4. **GLIDE's curve:** the material curve (`SeamFade.share`, smoothstep) like the keyboard and the
   looks (recommended), or linear with the wall?
5. **The apps row's content is global today.** Is the 11-25 crossfade line meant for a planned
   per-page app list? If so, approve a second content layer laid out at rest.
6. **The keyboard up on one side:** confirm RIDE (sideways with its page) everywhere,
   Terminal ⇄ Terminal included, replacing today's vertical sink in a crossing.

## 6. Decisions (the developer, 2026-11-25)

- **Rule:** C, Stay, Glide or Ride, as recommended.
- **Home ⇄ Home (Q1):** on Home the keyboard is an overlay owned by one page. It covers the dock and
  whatever stands under it; the other elements keep their rest, so the keyboard never changes
  another element's rest on Home (the A–Z bar keeps its length under it). Keyboard state is saved
  per Home page, and only a page with a floating or widget terminal ever keeps it up (until Back or a
  tap outside the terminal). So Home ⇄ Home is: the keyboard RIDES with its page, everything else
  STAYS. No second Home instance, no clip.
- **Order swaps (Q2):** RIDE (recommended default, not asked).
- **Edge changes (Q3):** RIDE (recommended default, not asked).
- **Glide's curve (Q4):** the material curve, `SeamFade.share` (recommended default, not asked).
- **Per-page apps list (Q5):** planned. Build the apps row's content crossfade now: a second content
  layer laid out at rest, only while the two ends' lists differ, inside the icon budget
  (`LauncherIconStore`).
- **Keyboard up on one side (Q6):** RIDE everywhere, Terminal ⇄ Terminal included; today's vertical
  sink goes.
