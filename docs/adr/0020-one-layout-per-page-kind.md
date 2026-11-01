---
status: proposed
date: 2026-11-01
supersedes: 0015 §3's page layouts ("any page may opt into a layout of its own") and the shared NORMAL layout
amends: 0018 §4 (the Home kind's layout is one document, so "a Home page's own layout never carries a dock" becomes a fact of the store, not a rule), 0017 (per-page looks `@<page>` fold into the kind's look)
---

# One layout per page kind

On 2026-11-01 two dock bugs came from the same place. The dock snapped after a swipe between
Home pages, because Home 1 had a page layout with its own dock and Home 2 did not. Then the
Appearance editor, opened on Home 2, edited the shared NORMAL layout, which the Home dock never
reads. Each fix patched one read path. The cause is that "which layout is this page wearing" has
five answers.

Today `PlaceLayoutStore` (about 2,700 lines) keeps layouts as flat SharedPreferences keys,
`layout.<scope><orientation>.<key>`, in four scopes: NORMAL (`""`), HOME (`home.`), MINIMAL
(`minimal.`) and one per page (`page<id>.`, behind `place.page_layout.<id>`). NORMAL is worn by
Home pages with no layout of their own, and also by the Terminal and the Display. Four sizes are
linked across scopes by `LINKED_KEYS`. The widget grid always reads the Home scope. The dock has
its own rule (`DressResolver.dockLayoutOf`). A read falls back from page to Home. Sixteen
migration steps sit on top. About 120 call sites reach the store directly, 56 of them in
`TermuxActivity`.

We decided the following.

## 1. One layout per page kind

There are four layouts: **Home**, **Terminal**, **Display**, and **Terminal-minimal**. A page
wears the layout of its kind. The Terminal and Display windows wear Terminal-minimal while
minimal mode is on, and it is edited on its own. Home pages ignore minimal mode (ADR 0015 §3).
The shared NORMAL layout is retired.

## 2. No page layouts

Every page of a kind looks the same. Only content differs between Home pages: the widgets.
`setPageLayout`, `dropPageLayout` and the page-layout switch go. Per-page looks (`look.…@<page>`)
fold into the kind's look cells (`@kind:<kind>`, ADR 0018 §3); the `@<page>` tier is retired.

## 3. Shared sizes

Keyboard height, keyboard chin, dock height and strip height live in one `sizes` document, per
orientation, shared by every kind. A page change never resizes them. This is what `LINKED_KEYS`
did through NORMAL; it is now the plain shape of the data.

## 4. Display gets its own layout

The Display layout starts as a copy of the Terminal's at migration, so nothing changes on the
first day. After that it is edited on its own.

## 5. Storage

One SharedPreferences file, `layouts`, with five keys: `home`, `terminal`, `terminal_minimal`,
`display`, `sizes`. Each value is a JSON document with a `version`. A kind document holds a
`portrait` and a `landscape` section, and its layout-wide values (dock design, search field,
drawer tile) once. The store reads the five documents once into an immutable `Layouts` object
and writes a document whole. No reader holds a half-written layout, and no code builds a key
name by string.

## 6. What stays global

The status bar's compact or expanded state belongs to each kind (ADR 0018 §4). The floating
keyboard position, the voice pill position and `app_launcher_dock_style` stay global: they are
about the device and the hand, not about a page.

## 7. Migration

Steps 9 to 16 stay as they are. A new step 17 builds the JSON from the migrated flat keys. For
each setting of each kind the fold rule picks, in order: the kind's own stored value; else the
Start-in Home page's page layout (for the Terminal, the Terminal page's own); else the shared
NORMAL value. Page looks fold by the same rule. An upgrade-matrix test migrates fixtures from
v8 (stable 1.2.2), v14 (1.3.0-pre.4) and v16 (v2 pre-releases) end to end and asserts the JSON.

## 8. The old keys are left alone

The flat keys are never read or written after step 17. They stay until the first stable v2
release has shipped; a migration step in the release after that deletes them. A downgrade to
1.2.x then finds its old values, provided 1.2.x leaves a store at version 16 or higher alone (to
verify, see the spec).

## 9. The editor edits the layout it was opened on

The Appearance editor edits the layout of the kind of page it was opened on, Terminal-minimal
when minimal mode is on, and names it in its title: "Home layout". The page-layout switch goes.

## 10. `launcherctl layout`

`launcherctl layout get <kind>` prints the JSON. `launcherctl layout set <kind> FILE` validates
it and applies it live, through the one redress request of ADR 0018 §2.

## 11. Delivery

This ADR and its spec come first. After approval, one worker builds one branch, and it is
verified on pong at the end. It lands before v2.0.0-pre.3, which waits for it.

## Considered options

- **Fix the dock reads one more time.** Rejected: it is the third such patch, and the next bug
  sits in another key.
- **Keep page layouts as an opt-in.** Rejected: an opt-in is a second answer to "which layout";
  the dock bug was exactly a page that had opted in.
- **A layout per page.** Rejected: pages of a kind differ by content, and a swipe must not move
  the chrome.
- **Keep flat keys, add a resolver.** Rejected: the resolver already exists in four places.
- **Delete the old keys at migration.** Rejected: a downgrade would lose the user's layout.

## Consequences

- `PlaceLayoutStore` shrinks to a loader and writer of five documents. The cross-layout rules
  (`LINKED_KEYS`, `gridKey`, `readKey`, the dock rule, `kindShows`) become a lookup by kind.
- About 120 call sites change from key reads to field reads on `Layouts`.
- A Home page that wore its own layout now wears the Home one. Where Home holds its own value
  (as the dock does on any install that ran the 2026-11-01 one-dock fix), that value wins; the
  Start-in page's layout fills only what Home never stored.
- Display and Terminal diverge on purpose from the first edit after upgrade.
- Preference storage doubles until the old keys are deleted.
- The spec, fixtures and checks are in `project-docs/active/layouts-per-kind-2026-11-01.md`.
