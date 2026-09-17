# fix/help-guide

Done: overlay on the decor view at 56dp (A); windows box = chip strip bounds + a leader
rule so an off-band card never lands bare (B, D-fact4); settings cog card now visible
because the overlay covers the keyboard (C); a card per extra key in two staggered rows
above the row (D); light-mode wash + deepened boxes (E); card views reused on re-measure
and new children laid out in the same pass, no blank frame (F).

In progress: nothing.

Next: run the JVM suite (never run here - the orchestrator owns gradlew), then check on
the phone: cards above the keyboard fit with nothing left out, the decor attach still
lets the x and catalogue buttons work, the light wash reads.

Gotchas: gradlew was forbidden for this agent, so nothing was compiled or run; javac
against android.jar was the only check. The key cards fall back to the old per-cap pills
when a key's slot is under 56dp.
