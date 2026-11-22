# The window restructure: one writer, one frame order

Decided 2026-11-17. This is the index; the decisions are in the ADRs and the spec.

- Map: [Map: one writer and one frame order for the launcher's window](https://github.com/PickleHik3/termux-launcher/issues/236)
- Spec: [Spec: one writer and one frame order for the launcher's window](https://github.com/PickleHik3/termux-launcher/issues/253)
- [ADR 0028](../../docs/adr/0028-one-frame-pipeline-a-view-property-has-one-writer.md): the frame order, Holds, guards, the layer table, packages, the Appearance editor's draft.
- [ADR 0029](../../docs/adr/0029-each-glass-surface-keeps-one-picture.md): the glass service, one picture per surface, the bottom edge.
- [ADR 0030](../../docs/adr/0030-pages-sides-and-the-bottom-stack.md): pages, sides, rows, the keyboard's numbers, the top stack.

What it is: one writer per view property and one order per frame, without moving the view tree.
At rest the launcher looks the same. Crossings blend look and ink on one curve, rows travel with
their side, the keyboard follows the finger, and nothing steps at the landing.

## Steps and checks

Every step ends with its scene parity tests, the source-scan test (allow-lists only shrinking) and
the full unit suite. Steps that change device behaviour add the emulator sweep with every guard
silent. Steps 4 to 6 add the pong screenshot check (no pop at a crossing's first or landed frame;
every bottom surface to the physical edge, rims on top and sides; light and dark). Side by side
only where packages are disjoint: step 1 with step 2, and the Assistant's page with step 5.

| Step | What | Extra check | Docs owed when it lands |
|---|---|---|---|
| 0 | Counters on pong (done); the ADRs and glossary commit | | |
| 1 | The Appearance editor's draft over the store | outside writes survive Done and Discard | `AGENTS.md`: the line "dirtiness compares against the entry snapshot" becomes "draft against store" |
| 2 | `window`: pipeline table, snapshot, Holds, guards, first-layout helper, layer hosts, the source-scan test | guards silent in the sweep | `AGENTS.md`: the line "views raised over the editor need a Z above the sheet's elevation" is replaced by the layer table |
| 3 | Bottom stack: scene and writer, rows to sides, keyboard numbers, no crossing resize, keyboard gates deleted, the IME's animation, the apps row's edges | Home 2 to Terminal with a float on Home: no keyboard snap, no second resize | `motion-invariants-2026-10-30.md`: its pairs rewritten against ADR 0030 |
| 4 | Glass service: registry, Live layers-always core, pane slabs, panels; See through removed | pong screenshots | glossary: Glass service, Backdrop layers lines folded in |
| 5 | Top stack: status bar and window chips, the bars' twins in | pong screenshots | glossary: Window layer |
| 6 | Bottom edge: slab to the screen's edge, decor strip deleted, bottom menus, drawer and palette | pong screenshots at the edge | glossary: Dock, Dock rows |
| 7 | Pages: Assistant, Home, Display, Terminal, each opening with a split plan the developer checks; kind enums folded; the activity's wall host gone; the remaining Holds | per-kind split plan | glossary: Side, Page, Hold, Gate, Backstop, Layer |

The glossary's ADR 0028 to 0030 lines are italic `_ADR ... (accepted, not yet built)_` notes;
fold each into its entry as the step that makes it true lands.

## Drops

One pre-release drop after step 7, cut by merging `v2-redesign` into `pre`. 2.0 stable after a day
of normal use of that drop on pong with every guard and counter at zero, every pong screenshot
check passed in light and dark, the full unit suite green and no open tester regression. Step 0's
counters ([the results](https://github.com/PickleHik3/termux-launcher/issues/249)) ran on
`diag/motion-counters`, which stays unmerged.

## Step 2, the layer hosts (#258)

Built: a window root holds the launcher's shell, the editor host and the notices host; the shell's
container holds ground, the body (pages, plus the chrome still laid out against them until steps 3,
5 and 6), the chrome host (Home pill, page switch) and the sheets host (drawer, palette, terminal
sheet, rename chip). The editor and notices stand beside the shell because the Appearance editor
and the setup sheet scale the shell to a miniature (until the editor's frame is snapshot state,
step 1's amendment). Left for later steps: the editor's tap layer and the editor's scrim and halo
(they must sit in or behind the scaled shell), the decor nav strip (step 6), elevation that still
orders children inside the body and pages (A-Z row, widget pane cells, pane floats, window bar),
and the debug-only invalidation recorder in `SceneController`.
