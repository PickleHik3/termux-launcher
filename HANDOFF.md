# HANDOFF — P1 engine (feat/tlstore-r5-engine)

Goal: catalog fields, --progress, bare-command routing, browse/fzf removed. Done, not
merged (worktree only; never merge here — orchestrator merges after everyone lands).

Done: 16 new catalog columns (items.tsv/catalog.tsv), real copy+pictures for the 7 visible
items, build-catalog.sh validates + computes picture digests, tlstore --progress and bare
exec-tlstore-ui routing, all fzf/browse code and docs removed, catalog.tsv regenerated
(unsigned — needs scripts/tlstore/sign.sh with the maintainer's key before it ships).

Next: nothing outstanding for P1. P4 (screens) depends on this; P6 (ship) signs the
catalog and re-documents docs/en/Tlstore.md fully.

Open questions: none blocking. Judgement calls (progress step granularity, human `info`
subset, picture choices) are in the commit message and final report.

Last test: sh scripts/tlstore/test.sh -> passed 616, failed 0, skipped 1 (nvim absent).

Commits: 5253e29a (pictures), 1527588a (engine changes).
