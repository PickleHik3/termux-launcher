---
status: accepted
date: 2026-10-30
supersedes: 0015 §6 (Looks: Base, then the page); 0013 (each half wears its own Look)
amends: 0007 and 0011 (corner radius joins the look values and may differ per element; margin stays one for the wall), 0009 (the chrome's glass is dressed per element and per page), 0004 (every surface still samples the shared frame, at one of eight radii)
---

# One grid for every look value

The review of 2026-10-30 (`project-docs/active/surface-look-grid-2026-10-30.md`) found that what a
user can set on a piece of glass, and where, differed per element and per property: Depth, Edge,
tint colour and corners were wall-only although the glass stack takes them per surface; the
keyboard had no Corners and the terminal no Margin; the drawer, folders, menus, the command
palette, notices and the Home float wore the dock's glass with no card of their own; Grain was
stored but unreachable; the Home, Workspace and Terminal cards had no scope chip while three rows
of the Appearance card ignored the chip they had. Underneath, three mechanisms decided one number:
an own key with a historical name, an inherit flag per (surface, property), and an `@<page>`
suffix with a flag of its own, with setters writing *through* the flag so that Settings sliders
would not detach a surface by accident, and three healing migrations to keep the three in step.
None of the wall-only limits was forced by the renderer: only Vibrancy and the frame-level tint
are baked into the shared blurred frame, and blur is one baked frame per distinct radius.

We decided that **every look value lives in one grid**, the same for every property and every
element.

## 1. Properties and elements

The look properties are Frost (blur and grain on one curve), Grain, Opacity, Tint, Tint colour,
Depth, Edge, Corners and Rim, plus Vibrancy and Margin, which stay at the *Where* level (§2). The
elements are the **status bar**, the **dock**, the **keyboard**, the **terminal** (the canvas,
which exists only on the Terminal page), the **widgets** (the Card form's glass on a Home page) and
the **sheets** (the app drawer, folders, anchored menus, the command palette, notices and the Home
float). Every element carries every property it has a shape for; a property that cannot apply has
no row rather than an inherited row that controls nothing. No element is "retired" from a property.

## 2. The grid

Two axes, four cells. **Where**: everywhere, or this page. **What**: all glass, or this element.

| | All glass | This element |
|---|---|---|
| **Everywhere** | Base: `look.<property>` | `look.<property>.<element>` |
| **This page** | `look.<property>@<page>` | `look.<property>.<element>@<page>` |

A value is read from the first cell that holds one, in this order: the element on this page, all
glass on this page, the element everywhere, Base, then the shipped Look. The page beats the
element's everywhere-value because detaching a page is the user saying "this page differs"; the
element's everywhere-value still holds on every other page. The Terminal page counts as one page
for all its windows. Vibrancy and Margin have only the left column.

## 3. Presence is the override

A key present at a cell is that cell's value; an absent key follows. Reset at a cell deletes its
key. There are no inherit flags, no write-through, no sentinel values and no healing migrations.
Every writer names its cell: the Appearance editor writes the cell its two chips name; Settings
rows, the wallpaper policy, presets, launcherctl and the setup wizard write Base unless they say
otherwise. A Look (Clear, Mist, Tint, Solid) applies at any cell, so "the dock Solid, the rest
Clear" is one tap on the dock's card.

## 4. Bounded, not forbidden

Blur is one baked shared frame per distinct radius. Frost therefore snaps to eight stops, with the
Looks' own positions among them, so six elements on the two pages visible during a crossing can
never ask for more frames than the cache holds; the cache is sized to the stop count. Vibrancy and
the frame-level tint stay at the Where level because they are baked into the frame.

## 5. Upgrading

One-way. The migration reads every old key through the old resolver, writes the resolved number
into the cell the old mechanism meant (a detached surface becomes the element's everywhere-cell, a
page override the page's cell, Base stays Base), and deletes the old keys and flags. Nothing
changes on screen. The fresh-install rule of `adoptShippedSurfaceDefaults()` is unchanged.

## Consequences

- `SurfaceSlot` gains `WIDGETS` and `SHEETS`; `hasSurfaceProperty`'s per-slot exceptions, the
  retired set, `KEY_SURFACE_INHERIT_PREFIX` and the write-through in `writeSurfaceValue` go.
- The named getters keep answering resolved numbers, so renderers change only where they gain a
  per-element value (Depth, Edge, tint colour, corners) or stop borrowing the dock's.
- The editor shows two chips on every card and a dot coloured by the cell a row's value comes from.
- Presets and `launcherctl` address cells by the key grammar of §2.
