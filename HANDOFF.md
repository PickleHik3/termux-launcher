# feat/widget-resize-displace — HANDOFF

Done: WidgetEditPolicy.resize costs each candidate rect with the move path's displace(); a
candidate whose blockers all find a hole is taken and its map returned in Candidate.displaced
(the "always empty for a resize" doc at :27 is gone). The pane previews it with slideCell and
commits through commitMove's atomic putRecords batch. Four policy tests + four controller tests.
In progress: nothing.
Next: nothing; suite green, ready to merge.
Gotchas:
- Two old tests asserted the collision-only behaviour (resizeNeverDisplacesNeighbours,
  resizeStopsAtNeighborCollision); both replaced, since that behaviour is what the spec changes.
- endResizeDrag calls commitMove(record, candidate, record.page) rather than duplicating the
  batch; commitMove was NOT renamed (it sits in the move region the small-fixes phase owns).
- The full suite runs one test JVM for ~5,590 tests with no maxHeapSize in app/build.gradle, and
  on this shared box it sits right at the heap cliff: adding Robolectric classes can tip it into
  OutOfMemoryError in unrelated classes (wall/PaneControlsViewRenderTest). Trimmed the new class
  to four cases for that reason. `-I <init script with forkEvery = 150>` runs it green in 2m48.
- No update-deferral calls added anywhere: that is the main session's wiring after the merge.
