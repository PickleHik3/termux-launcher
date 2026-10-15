# project-docs

Developer specs, research and records: why things are the way they are, what is in flight, and what
is left. User guides live in [`../docs/en/`](../docs/en/), decisions in
[`../docs/adr/`](../docs/adr/), agent docs in [`../docs/agents/`](../docs/agents/), and the words the
project uses in [`../docs/GLOSSARY.md`](../docs/GLOSSARY.md). Ephemeral agent scratch (working notes,
todo checklists, research dumps) does not belong here (see AGENTS.md).

Three rules keep this directory from rotting:

- **A finished spec is deleted, not left ticked.** The merged commit and `git log` are the
  implementation record. What survives is *reasoning* git log cannot carry: research, comparisons,
  measurements and the delivered records below. Old specs are in git history.
- **A document that contradicts AGENTS.md is worse than no document.** Build commands live in
  AGENTS.md, and the branch model and release process in `docs/agents/releasing.md`; nothing here
  restates them.
- **Move a document when its state changes**: `active/` while the work is open, `reference/` when it
  is delivered or is research that will not change.

## Layout

| | |
|---|---|
| [`active/`](active/) | Work in flight or waiting: specs not yet built, parked work, open studies. |
| [`reference/`](reference/) | Research, comparisons, delivered records and decision evidence. Grouped by topic: `terminal/`, `voice-ai/`, `launcher/`. |
| [`verification/`](verification/) | Runnable probes, not prose. |
| [`backlog.md`](backlog.md) | The one authoritative list of unfinished terminal work, with a stated reason each deferred item waits. |
| [`release-notes.md`](release-notes.md) | The changelog. See below. |
| [`prerelease-notes.md`](prerelease-notes.md) | The rolling notes of the pre-release channel: what the channel carries beyond the latest stable, restated in full by every drop, one file for all three editions. See below. |

## Active

| | |
|---|---|
| [`active/fancier-glass/SPEC.md`](active/fancier-glass/SPEC.md) | Fancier Glass: refraction, motions and their bug list. Partly built. |
| [`active/display-fullscreen/PARKED.md`](active/display-fullscreen/PARKED.md) | Full-screen Display with edge slide-outs. Parked. |
| [`active/tai-memory-manager.md`](active/tai-memory-manager.md) | The "can this model load right now" owner. Built; device checks pending. |
| [`active/tai-feature-load-plan-2026-10-08.md`](active/tai-feature-load-plan-2026-10-08.md) | The feature load plan: how each on-device AI feature picks its model, accelerator and context for a phone. |
| [`active/input-latency-study.md`](active/input-latency-study.md) | Touch-to-intent latency measured on pong, 2026-09-08: twelve tactical fixes and seven architectural directions. Not started. |
| [`active/perf-home-terminal-idle-handoff-2026-09-27.md`](active/perf-home-terminal-idle-handoff-2026-09-27.md) | Home to Terminal slide cost and idle redraw: symptoms and a code map. Not started. |
| [`device-checks.md`](device-checks.md) | Device checks owed, rolling: Agent and Developer items, deleted once seen; read before every drop and cut. |
| [`active/release-promotion-heuristics-research.md`](active/release-promotion-heuristics-research.md) | When a pre-release feature is ready for a stable: the sources behind the promotion rules in `docs/agents/releasing.md`. Decided 2026-10-14. |
| [`active/obtainium-channels-research.md`](active/obtainium-channels-research.md) | How Obtainium picks a release and an APK from our one repo of three editions and two channels, what went wrong in the real history, and the fixes: draft-first publishing with Termux last, and six one-tap import links. Draft-first publishing and the import links adopted 2026-10-14. |

## Reference: delivered records

Design and rationale for things that shipped. Each is the authority for *why*; the public,
task-oriented guide for the terminal is
[`../docs/en/Terminal_Modernization.md`](../docs/en/Terminal_Modernization.md).

| | |
|---|---|
| [`reference/terminal/terminal-modernization-status.md`](reference/terminal/terminal-modernization-status.md) | Engineering overview of the whole terminal project: what is delivered, the user-file contracts, and links to every owning record. Start here. |
| [`reference/terminal/split-panes.md`](reference/terminal/split-panes.md) | Sessions, windows, recursive pane trees, the window strip, single-pane compatibility. |
| [`reference/terminal/automatic-pane-layouts.md`](reference/terminal/automatic-pane-layouts.md) | The six layout presets, equalize, rotate, move-to-edge. |
| [`reference/terminal/durable-workspaces.md`](reference/terminal/durable-workspaces.md) | `~/.termux/workspaces/*.json`: the format, atomic storage, safe restore. |
| [`reference/terminal/session-browser.md`](reference/terminal/session-browser.md) | The searchable session/window/pane browser and clone-with-CWD. |
| [`reference/terminal/action-registry-terminal-actions.md`](reference/terminal/action-registry-terminal-actions.md) | The terminal action registry, command palette, chords and user bindings. |
| [`reference/terminal/kitty-protocol-features.md`](reference/terminal/kitty-protocol-features.md) | Kitty graphics, keyboard protocol, OSC 8/133, underlines, cursor trail, parser hardening. |
| [`reference/terminal/fonts-and-shaping.md`](reference/terminal/fonts-and-shaping.md) | `fonts.conf`, the four faces, fixed-cell shaping, symbol maps, geometric drawing, the font picker. |
| [`reference/terminal/kitty-config-grammar.md`](reference/terminal/kitty-config-grammar.md) | The kitty.conf grammar the launcher reads. |
| [`reference/terminal/kitty-custom-shaders-research.md`](reference/terminal/kitty-custom-shaders-research.md) | Research: which of kitty's custom shaders could carry over, and under which licences. |
| [`reference/terminal/terminal-url-tap-research.md`](reference/terminal/terminal-url-tap-research.md) | Why tapping a link in the terminal often does nothing. Research, no decision recorded. |
| [`reference/launcher/keyboard-off.md`](reference/launcher/keyboard-off.md) | Keyboard on/off: switching the keyboard off so a tap no longer raises it, and what turns it back on. |
| [`reference/launcher/inapp-keyboard-design.md`](reference/launcher/inapp-keyboard-design.md) | The `:inapp-keyboard` module and its launcher host. Paired with [`../inapp-keyboard/UPSTREAM.md`](../inapp-keyboard/UPSTREAM.md), which owns the vendored deviations. |
| [`reference/voice-ai/launcherctl-agent-platform.md`](reference/voice-ai/launcherctl-agent-platform.md) | The LauncherCtl agent platform: tool registry, agent APIs, event storage, MCP bridge. |

## Reference: launcher studies and evidence

| | |
|---|---|
| [`reference/launcher/pane-wall-x11-study.md`](reference/launcher/pane-wall-x11-study.md) | The pane wall, home-screen pane and embedded X11 pane. Built; why termux-x11 is forked and bundled, and why the wall is an outer container. |
| [`reference/launcher/nix-edition-vanilla-study.md`](reference/launcher/nix-edition-vanilla-study.md) | Whether the Nix edition could track upstream nix-on-droid, and why it was declined on 2026-09-01. |
| [`reference/launcher/proot-gui-apps-research.md`](reference/launcher/proot-gui-apps-research.md), [`de-sessions-research.md`](reference/launcher/de-sessions-research.md) | Linux apps and desktop sessions from a proot distro. |
| [`reference/launcher/appwidget-host-research.md`](reference/launcher/appwidget-host-research.md), [`lawnchair-16-comparison.md`](reference/launcher/lawnchair-16-comparison.md) | The widget host and Lawnchair 16, compared. |
| [`reference/launcher/first-boot-tour.md`](reference/launcher/first-boot-tour.md), [`help-overlay.md`](reference/launcher/help-overlay.md), [`help-overlay-inventory.md`](reference/launcher/help-overlay-inventory.md), [`hold-gestures.md`](reference/launcher/hold-gestures.md), [`2026-09-15-corner-touch-targets.md`](reference/launcher/2026-09-15-corner-touch-targets.md) | The shipped first-boot tour, help overlay and hold gestures, with the corner touch-target measurements behind them. |
| [`reference/launcher/gestures-and-style.md`](reference/launcher/gestures-and-style.md) | The full behaviour of the border drag, the keyboard and status swipes, minimal mode and the chrome Style. The glossary has the short definitions. |
| [`reference/launcher/mist-preset-obsidian-values.md`](reference/launcher/mist-preset-obsidian-values.md) | The Obsidian-Music values the Mist look and motion are taken from. |
| [`reference/launcher/miniature-material/`](reference/launcher/miniature-material/) | The Material artwork for the Layout editor's miniature (SVG and vector XML, plus the build script). |
| [`reference/launcher/appearance-layout-editor/SPEC.md`](reference/launcher/appearance-layout-editor/SPEC.md) | The Appearance and Layout editor's spec (built): the launcher scaled into a frame, Looks, Custom, Layout by drag and handle, every surface a legibility band. |
| [`reference/launcher/first-party-widgets-SPEC.md`](reference/launcher/first-party-widgets-SPEC.md) | The twelve widgets the launcher draws itself on the Widgets page (delivered). |
| [`reference/launcher/pinned-notification-SPEC.md`](reference/launcher/pinned-notification-SPEC.md) | Pinned notifications: rules, cards and the strip (delivered). |
| [`reference/launcher/keyboard-mis-input-correction.md`](reference/launcher/keyboard-mis-input-correction.md) | Mis-tap correction on the in-app keyboard. Stage 1 built (`TapCorrectionController`); the rest is a study. |
| [`reference/launcher/release-build-type-2026-10-06.md`](reference/launcher/release-build-type-2026-10-06.md) | The release build type adopted for all editions. |
| [`reference/launcher/termux-api-consolidation-research.md`](reference/launcher/termux-api-consolidation-research.md) | Termux:API against the launcher: what to port, bridge, keep delegating or remove. |
| [`reference/launcher/termux-on-device-build.md`](reference/launcher/termux-on-device-build.md) | Building the launcher from a Termux session on the phone: the aarch64 NDK and build-tools, the three AGP workarounds, minimum specs. |

## Reference: voice and on-device AI

| | |
|---|---|
| [`reference/voice-ai/parakeet-stt-research.md`](reference/voice-ai/parakeet-stt-research.md) | Parakeet speech-to-text: research and replay-rig results. |
| [`reference/voice-ai/voice-vad-eval-2026-09-27.md`](reference/voice-ai/voice-vad-eval-2026-09-27.md) | The voice-activity-detector evaluation behind Silero and mic sensitivity. |
| [`reference/voice-ai/voice-cleanup-benchmark-2026-09-27.md`](reference/voice-ai/voice-cleanup-benchmark-2026-09-27.md) | The cleanup-level benchmark behind Light and Polished. |
| [`reference/voice-ai/freestyle-voice-comparison.md`](reference/voice-ai/freestyle-voice-comparison.md) | Freestyle's dictation compared with ours. |
| [`reference/voice-ai/gallery-gpu-loading-comparison.md`](reference/voice-ai/gallery-gpu-loading-comparison.md), [`litert-llm-opendroid-comparison.md`](reference/voice-ai/litert-llm-opendroid-comparison.md) | Model loading and runtime comparisons. |
| [`reference/voice-ai/tai-model-ecosystem-research.md`](reference/voice-ai/tai-model-ecosystem-research.md), [`tai-importer-user-review.md`](reference/voice-ai/tai-importer-user-review.md) | The model ecosystem and the importer, reviewed. |
| [`reference/voice-ai/local-llm-inference-speed-2026-09-30.md`](reference/voice-ai/local-llm-inference-speed-2026-09-30.md) | Ways to speed up local LLM inference on a phone (quantization, threads, GPU/NPU, speculative decoding, KV and prefix caching, thermal), ranked for TAI on pong, with MNN / LiteRT-LM / llama.cpp support. |
| [`reference/voice-ai/local-llm-speedups-verification-2026-09-30.md`](reference/voice-ai/local-llm-speedups-verification-2026-09-30.md) | The top five speed-ups checked against MNN 3.6.1 and LiteRT-LM 0.17.1 source and Hugging Face model files, set against TAI's code: verdicts, gaps with file:line fixes, which official models have 4-bit / `-gpu` / MTP / lookahead builds, and a bench plan. |
| [`reference/voice-ai/tai-device-tiers-2026-10-05.md`](reference/voice-ai/tai-device-tiers-2026-10-05.md) | The device tiers build spec: what each RAM tier offers and preselects, the welcome card and the function pickers. Rests on [`tai-device-classes-and-gates-2026-10-05.md`](reference/voice-ai/tai-device-classes-and-gates-2026-10-05.md) and [`tai-android-soc-support-2026-10-05.md`](reference/voice-ai/tai-android-soc-support-2026-10-05.md). |
| [`reference/voice-ai/benchmark/SPEC.md`](reference/voice-ai/benchmark/SPEC.md) | The model benchmark (bench_v2). `recommended-set.md` and the draft catalogue beside it are kept as reference; the publish is not planned. |
| [`reference/voice-ai/mnn-diffusion.md`](reference/voice-ai/mnn-diffusion.md) | MNN text-to-image (`tai image`): what is built and the decisions to know. |

## Release notes

[`release-notes.md`](release-notes.md) is the changelog: every shipped release, newest first, with
each version's edition-exclusive items folded into its own `Editions` list. Written from the commit
range, for someone holding the phone.

The release being written keeps its own `release-notes-v<version>.md` so `gh release create
--notes-file` can point at it; once every edition is published it moves into `release-notes.md` and
that file goes: one history, never two. Versions before v0.2.35 live on their GitHub releases only.
AGENTS.md has the full convention.

[`prerelease-notes.md`](prerelease-notes.md) is the pre-release channel's rolling list
(`vX.Y.Z-pre.N` with `nix-` and `vaj-` tags alongside, cut by `scripts/cut-prerelease.sh`): what is heading for
the next stable plus the experiments merged into the drop. Every drop's GitHub release body is this
whole file. A line lives here until it ships in a stable, then moves into that release's notes.

## Verification

[`verification/`](verification/) holds the runnable probes: on-device scripts for keybinds,
terminal actions, terminal protocols and OSC 133 shell integration, the JVM escape-parser fuzz
harness, the X11 display check, and the agent-platform smoke clients under `verification/tai-ext/`.
