# Rescued workspace state, 2026-08-18

Everything here lived outside any git repository in `~/termux-launcher/` on a
machine about to be wiped. It is dumped as-is; nothing was reviewed or edited.

## workspace-notes/

Loose planning and audit documents from the workspace root: the `features-*`
epic plans, `features-scope.md`, `plan.md`, `fix-list-0.2.35.md`, the
`categories-view-audit.md`, the Codex hand-off prompts, the
`kitty-to-termux-launcher-feasibility-study.md` and `skills-lock.json`.

Not copied: `termux-launcher-extra-keys-ui-audit.zip` (3MB binary, a handoff
artifact whose content already shipped — see the extra-keys editor work).

## mnn-local/

Local modifications to the `alibaba/MNN` checkout at `app/mnn-src`, which has
no fork to push to. `mnn-base-commit.txt` records the upstream commit they
apply to; `mnn-local-edits.patch` is `git diff` over the tracked files
(CMakeLists.txt, llm_session.cpp, utf8_stream_processor.hpp) and
`embedding_jni.cpp` is a new untracked file. `mnn-local-build.sh` and
`errors.txt` are the host build script and its last output.

To restore: clone MNN at the recorded commit, `git apply
mnn-local-edits.patch`, drop `embedding_jni.cpp` into
`apps/Android/MnnLlmChat/app/src/main/cpp/`.
