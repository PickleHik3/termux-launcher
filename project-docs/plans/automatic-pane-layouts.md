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

## Follow-up: persistent layout management

The presets currently transform the existing pane tree once. They do not yet retain a selected
layout as a per-window policy or automatically recompute it when panes are added or removed. Add a
`pane.next_layout` action and a default cycling keybind, persist the active layout identity with the
window, and make later split/close operations reapply that policy. This will complete the kitty-style
interaction where `next_layout` cycles enabled layouts and the chosen layout continues managing the
window set. Direct `goto_layout`/`toggle_layout` bindings can follow once user-editable bindings can
carry enum arguments.
