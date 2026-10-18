# Gestures and Style

The full behaviour of the border drag, the keyboard and status swipes, minimal mode and the
chrome Style, as built. `docs/GLOSSARY.md` holds the short definitions and points here. Keep the
two in step: a change in behaviour updates this file, and the glossary only if the short definition
stops being true.

## Border drag

The one finger gesture that pages the wall, on every place and in every mode, on the current
page's border — the frame line on any of its four sides and the air between it and the next thing
out (the status bar, the dock or keyboard, the screen's edge), less the corner squares. On the top
and bottom borders, out in that air, it is a plain sideways swipe: past the touch slop the wall is
claimed at once and follows the finger from there. On the left and right borders, which share
their edge with Android's back swipe, and wherever a border reaches into the page, it is a press
held for the same hold a corner takes, then dragged sideways; a hold works on the top and bottom
too. The top and bottom borders are one gesture: the finger lands and the edge lights, then
sideways pages, up or down is the keyboard swipe (bottom) or the status swipe (top), and holding
still pages. The border never reaches the page's text, so a tap, long press or mouse click on the
terminal is the terminal's right up to its edge: the left and right borders reach 2dp over the
page's edge, into the clearance before the text, and where Margin leaves less than 6dp of air a
border reaches in by the shortfall, whichever is further. Where a border reaches into the page, in
the boxes by the grabber pills, and on the left and right borders, a sideways drag before the hold
stays the page's (a scroll, a selection, a program's own drag); a second finger always gives the
border up. A swipe from Android's own strip at the very top of the screen is never taken. The border
is always drawn while the
border preference is on (the slab's rim on glass, the plain stroke otherwise), on the terminal,
the Widgets page and the Display page alike, so the gesture has a line to find. A finger that
lands where it can page lights that edge at once, in the accent the grabbers use: full at the
touch on the top and bottom air, where a swipe pages at once, and brightening until the hold takes
on the sides and inside the page. The glow stays full through the drag and fades if the press is
given up or handed to a grabber (full and gone at once when motion is reduced). The claim, by hold
or by swipe, sinks
the page away from the user until the finger lets go, in every mode unless motion is reduced;
where the extra glass is active (see **Fancier Glass**) the page held and the page arriving both tip
like planks toward the finger, as if it pressed its weight into them, and lie flat as the settle lands; the Display place does
too, on a still copy of its picture that stands in for the motion. The release's settle carries on at the finger's speed. The status bar does
not page; its own drag is the fold across it.

## Keyboard swipe

The one way to the keyboard every place and every mode shares: a swipe that sets off up or down
from the current page's bottom border — the frame line and the air below it down to the dock or
keyboard, less the corner squares — before the border drag's hold. It also starts near the grabber
pill, one row into the page; only the swipe is taken there, so a tap, long press or sideways drag
on that row stays the page's. Up opens the keyboard, down closes it; opening turns a keyboard
that was switched off back on, as the keyboard key does. It is claimed as soon as it moves more up
or down than across; a sideways start from the border's air is the border drag, and a hold still
pages. The border's glow, lit at the touch, hands over to the grabber when the swipe is claimed. A docked keyboard follows the finger and the release finishes it past a third of the way or on a
flick, and takes it back otherwise; a floating keyboard, Android's, and one switched off answer
the release alone. A small **grabber** pill on the bottom border marks it.
The keyboard itself carries no swipe of its own.

## Minimal mode

A second saved layout and nothing more, which starts with only each place's content: by default the
status bar, the apps bar, the A–Z index, the extra keys and the keyboard go away, and the widgets,
the panes or the display take the room in either orientation. It changes no behaviour: a split
shows all its panes and the bars it keeps answer the same gestures as in the normal layout. One mode for every place, not a state
of one: turned on and off only from the corner tab's minimal button, which every place carries, and
remembered until turned off; paging never leaves it. It has a layout of its own, the **minimal
layout**, beside the normal one, in the same element model: the Layout editor opened while the mode
is on edits it, and it says "Minimal layout", so users choose which elements minimal keeps. It
starts as the normal layout with every element put away. The keyboard can still be raised while it
is on, by the keyboard swipe or a tap. Paging is the border drag, as everywhere.

## Status swipe

The keyboard swipe's mirror on the current page's top border: a swipe that sets off down or up
from it — the frame line and the air above it up to the status bar — before the border drag's
hold. Like the keyboard swipe it also starts near its grabber pill, one row into the page, and
only the swipe is taken there; from the border's air a sideways start is the border drag instead. Down unfolds the status bar, up folds it: the bar's own
two forms, compact and open, driven by the same fold its drag across itself drives, following the
finger and landing by the keyboard swipe's rule. A grabber pill marks it. It is there only while
the status bar stands along the top and can unfold, minimal mode included; a bar on another edge
or put away leaves the top border to paging. A press in the phone's own strip at the top of the screen is
never taken, so the notification shade still pulls down.

## Style

How the chrome is shaped as a whole: **Docked** or **Floating**. It covers the status bar, the
dock's elements, the keyboard and the pane's opening, and one shape decides the fill, the
selection outline and the lifted copy in the Layout editor. In **Floating** each piece is a
rounded card with air around it: the status bar is always its own card, apps, A–Z index and extra
keys that stand next to each other on one edge share one card, and the keyboard is its own card;
Corners sets every card's radius and Margin the air. In **Docked** the bars join flush into one
frame of glass with no air between them: top and bottom bars run the full width and side bars stand
between them, and joins stay square. The frame is flush and square at the screen's edges (the
device's own screen rounds those corners), with its inner corners rounded; with no side bars there
is no frame, only two edge cards, the top stack square at the top with its bottom corners rounded
and the bottom stack square at the bottom with its top corners rounded, and the gutter between them
and the pane shows the wallpaper. The pane is a rounded **insert** over that glass, never a square window: Corners sets its
radius and Margin the gutter of frame glass around it, so both sliders mean the same in either
Style (wallpaper shows in Floating's gaps, and in Docked's when no bar stands at a side; glass in
Docked's gutter otherwise). The insert keeps the pane's own glass,
Darkness and Legibility veil: it is the frame's glass unless Darkness is raised, and reads as a
window by the line round it, the theme's outline at the divider's strength. Pieces that join draw
no line between them, and in Docked only the insert's own edge carries that line, rim light and
refraction. Fullscreen under Docked keeps a thin frame of glass, the gutter,
and with only the keyboard left it is a bottom card with rounded top corners and the pane still the
rounded insert above it. One Style holds for both
orientations and for the minimal layout. Style never changes the keyboard's form: a floating or split
keyboard keeps its own shape, and only a docked keyboard joins the frame or becomes a card.
