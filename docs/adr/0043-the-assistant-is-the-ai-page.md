---
status: accepted
date: 2026-11-28
amends: 0037 §1 (the Assistant is no window; Workspaces hold terminals and Linux apps), 0022 §1 (under Terl the Assistant page stands left of Home 1 and is added only by its setting)
---

# The Assistant is the AI page

Decided by the developer on 2026-11-28 (Terl decisions, round 3: `project-docs/active/terl-decisions-2026-11-28.md`).
Builds on ADR 0022 and ADR 0037; reverses the part of ADR 0025 and of #419 that made the Assistant
a window of the Workspace.

ADR 0037 made every shell a window and the X11 Display and the Assistant windows too, and #419 built
the Assistant as a tile that opened after the focused window. Used on pong, a chat among terminal
tiles reads as one more shell: it takes a column a terminal wanted, it has no fixed place to find it,
and a Workspace of tiles is where work happens, not where the user asks a question. Other launchers
put their assistant or feed on a page beside the first home page, where a swipe from Home always
finds it.

## Decision

1. **The AI page.** Under Terl the Assistant is a page to the left of Home 1, reached by a swipe right
   from the first Home page, while the Home setting **AI page** (`home.ai_page`) is on. The wall puts
   it first when it lays its cells out; the wall's pages are never changed for it, so v1's order stays
   the user's. When the wall already holds an Assistant page (ADR 0022) the AI page is that page.
2. **Off by default for now.** A one-time tip on Home invites the user to turn it on; a tap does.
   The welcome pages will offer it with the choice of an offline model or a free online key.
3. **Workspaces hold terminals and Linux apps only.** The Assistant is no window kind of a Workspace
   or a Home page: nothing opens one, and a saved Workspace or desktop from before this decision
   drops its Assistant window when read.
4. **Every way into the Assistant goes to the AI page**: search's Assistant row, the drawer's entry,
   a leader key bound to it and the open-Assistant intent. While the page is off they offer to turn it
   on first.
5. **Turning the page off keeps the chats** (ADR 0022 §3); the page leaves the wall's layout, and its
   view is not built.
6. With the Terl switch off nothing changes: v1's Assistant page stays where the user put it.

## Considered

- **Keep the Assistant as a window** (#419). One model for everything on the desktop, but the chat
  competes with terminals for room and has no place a thumb can always find.
- **A sheet over any place**, raised by a gesture or the bar. Reachable from everywhere, but a sheet
  is temporary, and a conversation the user returns to wants a place of its own.
- **On by default.** The Assistant is a core part of the launcher, but a page appearing beside Home
  before the user has a model or a key shows an empty chat; the tip and, later, the welcome pages
  bring the user to it when it can answer.

## Consequences

- `TilingDesktop.openAssistant` and the Assistant's window content are gone; `WindowKind.ASSISTANT`
  stays only so an older saved form can be read and its Assistant windows dropped.
- `TerlWall.laidOutIds` gives the layout's order: under Terl Home's pages, with the AI page first
  while it is on; nothing is written into the shared `wall_pages` store, so switching Terl off shows
  v1's wall exactly as the user left it.
- The control bar, Overview and the Workspace list keep their Assistant arms for that legacy kind;
  no window of it reaches them any more.
