# Termux layout assets — proposal

24 surface SVGs plus 11 glyph SVGs. Transparent outside each surface.

Floating: separate rounded status, terminal and keyboard cards; dock, alphabet and extra keys form one shared rounded card.

Docked: edge pieces join without padding. Side bars touch top and bottom bars. The terminal is an unoutlined opening, with no separate glass container. Only exposed outer corners round; join edges stay square. Clip the assembled frame once, not each internal piece.

The same shape must drive fill, selection outline and drag preview. Symbols and keys retain their proportions when bounds change. Use runtime theme roles, not fixed preview colors. No dot-matrix grab handles.

This is a review pack, not an app implementation. Keyboard form (docked/floating/split) remains distinct from surface mode.
