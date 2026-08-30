# Changelog — v0.2.38

Automatic tiling for panes, a pane API so the agents running in your shell can show
their work, and a shortcut that opens a pane without asking which way to split.

## New

### Terminal

- **Automatic tiling, the Hyprland way.** A new `dwindle` layout: each new pane
  halves the pane you are in along its longer side — on a phone that means the
  first split stacks, the next goes side by side, and so on — whichever split
  key you press. Drag a pane's move handle onto another pane and it takes the
  half you drop it on, highlighted while you drag, instead of swapping the two.
  Dividers you drag stay where you put them, and a closed pane hands its space
  back to its neighbour. Reach it with `Ctrl+Alt+L` (it sits after `grid`), or
  make it the default for new windows under **Settings → Terminal & Status →
  Sessions and panes → Automatic tiling**.
- **Focused pane grows.** Turn it on in the same section and the pane you tap
  takes most of the room while the others slide aside — tap between an agent's
  pane and the one it is driving to watch either up close.
- **Panes for the programs in your shell.** `launcherctl pane open -- htop`
  opens a pane running `htop`; an AI coding agent, a build or a script can do
  the same to show what it is working on, then `write` to it, `read` it back,
  `focus` it and `close` it. A program may only type into, read or close panes
  it opened itself; your own shells are out of its reach. `launcherctl pane
  list` shows every pane and which were opened this way. **Let scripts open
  panes** in Sessions and panes switches the whole thing off.

### Keyboard shortcuts

- **`Ctrl+Alt+Enter` opens a new pane** without choosing a direction: the
  focused pane splits along its longer side. `Ctrl+Alt+V` and `Ctrl+Alt+H`
  still split the way you say. The Ctrl+Alt hint strip shows it as `⏎ new pane`.
- **`Alt+Arrow` moves between panes** (it was `Ctrl+Arrow`, which stole word
  jumps from the shell and editors). When there is no pane in that direction
  the key goes to the shell as usual, and `Ctrl+Arrow` is the shell's again.
