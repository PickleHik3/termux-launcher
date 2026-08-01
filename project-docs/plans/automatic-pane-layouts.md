# Automatic pane layouts

Status: implemented July 2026. This completes the automatic-layout part of Phase 3 in the terminal
modernization study.

User workflows and API examples are in
[`../../docs/en/Terminal_Modernization.md`](../../docs/en/Terminal_Modernization.md). The engineering
status map is [`../terminal-modernization-status.md`](../terminal-modernization-status.md).

## Runtime semantics

Every operation acts only on the active window. It reuses the existing `Leaf` objects and
`TerminalSession` instances, preserves the focused PTY, and never starts or terminates a shell.
Changing layout therefore affects measurement/reflow but not process identity, CWD, scrollback, or
the session/window list.

The six presets are:

| Preset | Tree produced |
|---|---|
| `stack` | Temporarily maximize the focused pane while every other pane remains alive and hidden. |
| `grid` | Near-square rows; panes are distributed evenly between rows and within each row. |
| `tall` | First pane is a half-width left master; remaining panes form an even vertical stack on the right. |
| `fat` | First pane is a half-height top master; remaining panes form an even horizontal row below. |
| `horizontal` | Every pane appears side by side at equal width. |
| `vertical` | Every pane appears in one top-to-bottom column at equal height. |

The original left-to-right leaf order is stable across every preset. Applying a non-stack preset,
equalize, rotation, moving a pane, or splitting clears stack maximization. Stack uses the existing
temporary maximize state, so a durable workspace records its underlying tree rather than restoring
the temporary maximized presentation.

Three topology operations complement the presets:

- **Equalize** recursively resets every split's two weights to `1:1`.
- **Rotate** geometrically rotates the complete tree 90 degrees clockwise or counterclockwise.
  Orientation changes and child swaps are paired so clockwise followed by counterclockwise restores
  the original topology, ratios, and pane order.
- **Move to edge** extracts the focused leaf, prunes its old parent without killing anything, and
  attaches the leaf at a new root split on `left`, `right`, `up`, or `down`. A single pane reports a
  conflict because it is already every edge.

## Registry surface

Four low-risk, unconfirmed terminal actions expose the feature:

| Tool | Arguments | UI exposure |
|---|---|---|
| `pane.layout` | required `layout`: `stack`, `grid`, `tall`, `fat`, `horizontal`, `vertical` | agent/CLI |
| `pane.equalize` | none | command palette |
| `pane.rotate` | optional `direction`, default `clockwise` | command palette (clockwise default) |
| `pane.move_to_edge` | required `edge`: `left`, `right`, `up`, `down` | agent/CLI |

The two enum-required tools stay out of the palette because it cannot prompt for arguments. All
four report `splits_disabled` in compatibility mode and use the same Activity/controller path as
touch and key-driven pane operations.

## Verification

`TerminalPaneControllerTest` covers all six transforms, equal-width/height tree construction,
stable PTY order and focus, stack visibility, equalized ratios, inverse rotations, four-pane edge
movement without loss/duplication, and the single-pane rejection. Registry and dispatcher tests
pin schemas, risk, UI projection, counts, and routing.

The Pong device pass used the installed debug APK and the authenticated launcher API. It created a
disposable window, split it into three live panes, exercised all six presets, equalize, clockwise and
counterclockwise rotation, and moves to all four edges, then closed the disposable window. `stack`
reported one visible pane and every tiled layout reported three. Cleanup restored the original one
session, one window, and one visible pane, and a bounded scan of 500 app-process log lines found no
fatal exception or dispatcher/layout error.

## Retained layout policy

Applying a layout now records it on the window as `Window.layoutPolicy`, and the layout keeps
managing that window's pane set rather than transforming it once.

- `split()` and `onSessionFinished()` call `reapplyLayoutPolicy(window)`, so adding or closing a pane
  re-tiles the survivors into the retained layout instead of leaving the binary split that the
  mutation produced. Reapply runs for background windows too, so a window that lost a pane while
  hidden is already correct the next time it is shown.
- `transformToLayout(window, layout)` is the shared pure-topology transform. It performs no render,
  no host notification, and no policy bookkeeping, which is what lets the public entry point and the
  automatic reapply path use one implementation. Only the active window can enter `stack`, because
  stack reuses the temporary-maximize state, which is a property of the foreground presentation
  rather than of the tree.
- Hand-shaping releases the policy. `rotateLayout`, `moveActivePaneToEdge`, and `resizeActive` clear
  it, because retaining it would mean the next split silently discarded the user's shaping.
  `equalizeLayout` keeps it: resetting ratios to 1:1 is consistent with the managed shape.
- `saveWindow`/`restoreWindow` carry the layout through Activity recreation. Restore accepts only a
  name this build still knows, so a stale or hand-edited value leaves the window manually managed
  instead of wedging reapply on every later split.

### Cycling

`nextLayout()` advances the active window through `LAYOUT_CYCLE` and retains what it lands on. The
cycle order is `grid`, `tall`, `fat`, `horizontal`, `vertical`, `stack` — deliberately not the
presentation order used in the table above. `stack` hides every unfocused pane, so it must not be
where a single press from an unmanaged window lands; it sits last instead. An unmanaged or
unrecognized policy resolves to the first entry, so one press always yields a managed tiling.

`pane.next_layout` takes no argument, so unlike `pane.layout` it carries UI metadata and a default
binding: `Ctrl+Alt+L` under `SPLITS_ON`. It is the only layout action bound by default.

## Verification

`TerminalPaneControllerTest` adds coverage for the cycle order and its stack-last rule, `isKnownLayout`
rejection, apply-and-retain through `nextLayout`, re-tiling on both split and close, each
hand-shaping operation releasing the policy while equalize keeps it, and the save/restore round trip
including the unknown-name rejection. `LauncherToolRegistryTest` pins the new tool's empty schema, UI
metadata, and binding, and adds `defaultBindings_neverCollideUnderSimultaneouslyActiveConditions`,
which asserts no two tools claim one stroke under conditions that can be active together — the
`SPLITS_ON`/`SPLITS_OFF` pairs are legal, anything else is one binding shadowing another.

`terminal.state` reports the retained layout as `paneLayout`, omitted when the window is manually
managed. It was added for this slice: without it the policy is invisible to agents and to any device
check, which made the behaviour unverifiable from outside the process.

### Device pass

Run on `Pong` (A065, Android 16) against the installed universal debug APK and the authenticated
launcher API. A disposable window was created and split to three panes, then:

- `pane.next_layout` seven times returned `grid`, `tall`, `fat`, `horizontal`, `vertical`, `stack`,
  `grid`, with `terminal.state.paneLayout` agreeing at each step. `visiblePanes` was 3 for every
  tiling and 1 for `stack`, confirming stack still hides rather than closes.
- Splitting under a `vertical` policy left `paneLayout` at `vertical` and `visiblePanes` at 4.
  Closing a pane with `pane.kill_focused` left `paneLayout` at `vertical` and `visiblePanes` at 3.
  Screenshots confirmed the geometry rather than just the label: three *equal-height* panes in one
  column, which is the re-tiled tree. A one-shot transform would instead have left the survivor
  sharing the closed pane's slot, giving unequal heights.
- `pane.rotate`, `pane.move_to_edge`, and `pane.resize` each dropped `paneLayout` from the response.
  The release is decisive rather than incidental: the following `pane.next_layout` returned `grid`,
  which is `nextLayoutAfter(null)`, so the policy really was cleared and not merely hidden.
  `pane.equalize` after `pane.layout grid` kept `paneLayout` at `grid`.
- Activity recreation was forced with a `font_scale` change, which `TermuxActivity` does not declare
  in `configChanges`. The event log shows `handleRelaunchActivity` → `wm_on_stop_called` →
  `wm_on_destroy_called` (`performDestroy`) → `wm_on_create_called` (`performCreate`), and
  `paneLayout` survived both that change and the revert.
- Cleanup restored the original one session, one window, one visible pane. A 500-line log scan found
  no fatal exception and no dispatcher/layout error.

Two false starts are worth recording, because both produce a *passing-looking* result that proves
nothing. `always_finish_activities` does not destroy this Activity — `com.termux` is the device's
HOME, so leaving it does not background it, and the event log showed only `performRestart`. And on
this Android version the activity lifecycle event tags are `wm_on_*`, not `am_*`; grepping for
`am_relaunch` silently matches nothing, which reads identically to "no recreation happened". Confirm
recreation from the `wm_on_destroy_called`/`wm_on_create_called` pair, never from the absence of a
pattern.

## Follow-up

Workspace *files* still do not record the retained layout, so a window restored by `workspace.load`
starts manually managed. That needs a `TerminalWorkspace` format decision — the parser rejects any
`version` other than `VERSION`, so adding the field is owned by `durable-workspaces.md` rather than
by this slice. Direct `goto_layout`/`toggle_layout` bindings still wait on user-editable bindings
being able to carry enum arguments.
