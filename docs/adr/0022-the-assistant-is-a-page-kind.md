---
status: proposed
date: 2026-11-05
amends: 0015 §1 (a fourth page kind) and §3 (its dress), 0020 §1 and §5 (a fifth layout document)
amended-by: 0025 (§2 superseded: the Assistant page wears the Workspace's layout, dress and look)
---

# The Assistant is a page kind

The launcher has an on-device chat engine (`:tai_runtime`, Gemma E2B/E4B, the local OpenAI-style
API) but no place where a person talks to it; the docs send chat users to a terminal client. The
developer decided on 2026-11-03 that the launcher gets one chat that everything lands in, built
in native Views with the wallpaper's colour roles and no terminal identity, and on 2026-11-05, in
one grilling session over the OpenDesign "AI page and flows" design, how it sits on the wall.

We decided that the chat is a page of its own kind, **Assistant**, on the wall beside Home,
Terminal and Display pages.

## 1. The page

- At most one Assistant page. It is opt-in: new installs and upgrades do not get one. Edit pages'
  Add offers Assistant while there is none; it lands after the selected page.
- Any way into the chat (the drawer's Assistant entry, and later the share sheet, the palette's
  `ask`, the Ask widget, a finished-answer notification) that finds no Assistant page adds one
  after Home 1, lands on it and shows "Assistant page added" with Undo.
- Removing the page keeps the chats. The page can be the home page. It takes no Floats and no
  dropped items: a carry released over it flies back, as over a Display page.
- Its name is "Assistant". It has one stop.

## 2. Dress and layout (amends 0015 §3, 0020)

The Assistant kind shows the status bar; the dock, the extra keys and the window bar are off, so
the composer owns the bottom edge. Each element can be toggled per kind like any other. The
keyboard remembers open or closed for this kind on its own. Minimal mode does not apply.

The kind has its own layout document, `assistant`, in the `layouts` store and its own look cells
(`@kind:assistant`), seeded from Home's when first created. Shared sizes stay in `sizes`.
Surfaces join the Base look (glass, corners) and take colour roles from the wallpaper scheme.

## 3. The chat

- One chat at a time on the page, with a list of chats behind the top bar's Chats button. Chats
  live in an app-private SQLite database and are kept until deleted (per chat, or "Delete all
  chats" on the On-device AI settings page). Model requests cannot read them.
- The model is the launcher's default assistant model (`TaiSettings`
  `tai_role_default_assistant`, the same fallback the local API uses). The page's model sheet writes that
  role; there is no second, Assistant-only choice.
- An answer streams through the existing runtime client and keeps going when the page is left;
  it is saved as it arrives. Only the newest messages that fit the serving window are sent; a
  divider in the thread marks the cut.
- Nothing is pre-written: no starter prompts, no suggestion chips. The model writes text only; the
  app offers fixed actions (Copy, Share, Try again; Add to Tasks on list answers).
- Personal data enters a prompt only when the app puts it there and says so: notification
  history (opted-in apps only) when a question mentions notifications, shown as "Read N
  notifications from today". No calendar. No model tool calls.

## Considered options

- **A terminal client (aichat) as the front end**: the research note of 2026-11-03 recommended it;
  rejected by the developer, the audience is not only terminal users.
- **A standalone chat activity** opened by the ways in when no page exists: rejected; the chat
  always lives on the page, so there is one place to find it.
- **Several Assistant pages, one per chat**: rejected; the chat list does that job.
- **Sharing Home's layout and look**: rejected; the composer and the dock compete for the bottom
  edge, so the kind needs its own dress and layout.

## Consequences

- `WallPages.PageKind`, `DockSpec.PageKind`, `LayoutKind`, the look and layout stores and every
  switch over a page kind gain an `ASSISTANT` arm. An older build cannot parse a wall that holds
  an Assistant page; `WallPagesStore` treats it as unreadable and does not seed over it (W7), so
  no store version bump is needed, but a downgrade with an Assistant page on the wall is not
  supported.
- A new package holds the chat store, the engine client and the page's views; `TermuxActivity`
  only hosts the page through a Host interface.
- Build order: wave 1 the page, chat, model sheet, chats list, dictation and drawer entry;
  wave 2 share-anything, attachments, notifications in a question, Add to Tasks; wave 3 the
  palette's `ask`, the finished-answer notification and the Ask widget. Specs:
  OpenDesign project "Assistant page spec sheets".
