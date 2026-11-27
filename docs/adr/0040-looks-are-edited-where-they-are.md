---
status: accepted
date: 2026-11-27
amends: 0031 §2 (the surfaces that may own Opacity and Blur are the control bar, the keyboard and windows; the status bar and the dock retire with ADR 0038)
replaces: the rule in AGENTS.md "Things already decided" that the Appearance editor commits only on Done, its card sheet and its unsaved-changes dialog (for Terl)
---

# Looks are edited where they are; Settings holds behaviour

Spec: `project-docs/active/tiling-desktop-2026-11-27.md`, section "Edit mode and Settings".
Builds on ADR 0037 and ADR 0038.

v1's Appearance editor draws the launcher scaled into a frame, keeps every edit in a draft and saves
it only on Done, behind an unsaved-changes dialog. Settings carries 51 screens, many of them for
surfaces the tiling desktop retires, and several visual choices live both there and in the editor.
Terl starts every user's look and layout fresh (behaviour settings that still have a row carry
over; spec, "Upgrade from v1"), so the editing model can be chosen for the new desktop rather than
carried over.

## Decision

1. **Edit mode** on the live desktop replaces the Appearance editor. Nothing is scaled: every
   surface shows a faint outline, a tap raises its card low on the screen, and the stack still
   moves while editing. Its doors open on the card of the thing they came from.
2. **Every change applies at once and stays.** Undo steps back, Revert drops everything since edit
   mode opened, Done and Back only leave. There is no draft and no unsaved-changes dialog. One
   slider drag, a Revert and a Look pick are one Undo step each; a setting written from outside
   after the session last changed it is skipped by Undo and Revert (amended 2026-11-27).
3. **Anything that can be seen is changed where it is**, in edit mode: glass, the bar's tools and
   rail side, the keyboard's height, a terminal's text. **Settings holds behaviour only**, in seven
   groups no deeper than two levels, opening on search, with a Changed filter.
4. **One Look**, picked from a filmstrip of the shipped Looks, three made from the wallpaper and a
   Custom Look that sets Opacity and Blur for all glass and remembers its values. Only the control
   bar, the keyboard and windows may hold their own Opacity and Blur; picking any Look puts every
   surface back on it, after asking if any holds its own value.
5. **terl.conf** is the text form of every setting, built from the settings registry and applied
   when saved; the settings store stays the truth (ADR 0041).

## Considered

- **Keep the scaled frame.** The whole screen stays visible above the card, but text, gaps and
  corners are judged at the wrong size, and the frame would have to fake a tiling Workspace.
- **Keep the draft and Done.** Free experiments, at the cost of a dialog on every Back and a draft
  that edits from launcherctl or the Assistant must merge into. Undo and Revert give the same
  safety without the dialog.
- **Settings holds everything, edit mode a shortcut.** One complete list, but every visual control
  in two places, which is how v1's pages grew.
- **No Settings at all.** Radically small, but backup, permissions and models then have no home.
- **Copying one surface's look to another.** Declined; the shipped Looks are the way back to a
  coherent look.

## Consequences

- The Appearance editor's frame, card sheet detents, draft and dialog, the Layout editor and the
  pages for retired surfaces go; Settings is rebuilt as about fifteen pages.
- Own values are marked by a dot (a hollow ring when a row follows Global); the reset arrow retires.
- A setting changed from terl.conf, launcherctl or the Assistant shows in edit mode at once, since
  there is no draft to merge with, and Revert never reverses it.
- The edit session keeps grouped steps (a Revert or a Look pick changes many values in one step), so
  a stray tap on Revert, which nothing confirms, is one Undo away from undone.
- A legibility meter on Opacity and Blur shows the measured contrast and offers Dim wallpaper;
  nothing is blocked.
