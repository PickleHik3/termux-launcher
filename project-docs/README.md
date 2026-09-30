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
- **A document that contradicts AGENTS.md is worse than no document.** Build commands, branch
  models and release process live in AGENTS.md alone; nothing here restates them.
- **Move a document when its state changes**: `active/` while the work is open, `reference/` when it
  is delivered or is research that will not change.

## Layout

| | |
|---|---|
| [`active/`](active/) | Work in flight or waiting: specs not yet built, parked work, open studies, pending device checks. |
| [`reference/`](reference/) | Research, comparisons, delivered records and decision evidence. Grouped by topic: `terminal/`, `voice-ai/`, `launcher/`. |
| [`verification/`](verification/) | Runnable probes, not prose. |
| [`backlog.md`](backlog.md) | The one authoritative list of unfinished terminal work, with a stated reason each deferred item waits. |
| [`release-notes.md`](release-notes.md), [`release-notes-v1.0.0.md`](release-notes-v1.0.0.md) | The changelog. See below. |

## Active

| | |
|---|---|
| [`active/animated-wallpaper/SPEC.md`](active/animated-wallpaper/SPEC.md) | In-app animated wallpapers over the shared frame: the pipeline and the phase 0 measurement. Draft; planned for the next release. |
| [`active/animated-wallpaper/generated-backgrounds-issue.md`](active/animated-wallpaper/generated-backgrounds-issue.md) | Copy of issue #41: generated backgrounds with moments and Material colour, gated behind Fancier Glass. Amends the SPEC. |
| [`active/animated-wallpaper/kitty-custom-shaders-research.md`](active/animated-wallpaper/kitty-custom-shaders-research.md) | Research: which of kitty's custom shaders can carry over, and under which licences. |
| [`active/fancier-glass/SPEC.md`](active/fancier-glass/SPEC.md) | Fancier Glass: refraction, motions and their bug list. Partly built; `evidence-*/` holds the frame measurements. |
| [`active/benchmark/SPEC.md`](active/benchmark/SPEC.md) | The model benchmark (bench_v2), with the recommended-set publish checklist and draft catalogue. |
| [`active/agent-skill/spec.md`](active/agent-skill/spec.md) | The shipped agent skill. Draft, not agreed. |
| [`active/display-fullscreen/PARKED.md`](active/display-fullscreen/PARKED.md) | Full-screen Display with edge slide-outs. Parked. |
| [`active/tai-memory-manager.md`](active/tai-memory-manager.md) | The "can this model load right now" owner. Built; device checks pending. |
| [`active/keyboard-mis-input-correction.md`](active/keyboard-mis-input-correction.md) | Mis-tap correction on the in-app keyboard. Stage 1 built (`TapCorrectionController`); the rest is a study. |
| [`active/input-latency-study.md`](active/input-latency-study.md) | Touch-to-intent latency measured on pong, 2026-09-08: twelve tactical fixes and seven architectural directions. Not started. |
| [`active/perf-home-terminal-idle-handoff-2026-09-27.md`](active/perf-home-terminal-idle-handoff-2026-09-27.md) | Home to Terminal slide cost and idle redraw: symptoms and a code map. Not started. |
| [`active/device-checks-pending-2026-09-27.md`](active/device-checks-pending-2026-09-27.md) | Device checks owed on pong. |
| [`active/termux-api-consolidation-research.md`](active/termux-api-consolidation-research.md) | Termux:API against the launcher: what to port, bridge, keep delegating or remove. |
| [`active/terminal-url-tap-research.md`](active/terminal-url-tap-research.md) | Why tapping a link in the terminal often does nothing. Research, no decision yet. |

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
| [`reference/launcher/mist-preset-obsidian-values.md`](reference/launcher/mist-preset-obsidian-values.md) | The Obsidian-Music values the Mist look and motion are taken from. |
| [`reference/launcher/miniature-material/`](reference/launcher/miniature-material/) | The Material artwork for the Layout editor's miniature (SVG and vector XML, plus the build script). |
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

## Release notes

[`release-notes.md`](release-notes.md) is the changelog: every shipped release, newest first, with
each version's edition-exclusive items folded into its own `Editions` list. Written from the commit
range, for someone holding the phone.

The release being written keeps its own `release-notes-v<version>.md` so `gh release create
--notes-file` can point at it; once every edition is published it moves into `release-notes.md` and
that file goes: one history, never two. Versions before v0.2.35 live on their GitHub releases only.
AGENTS.md has the full convention.

## Verification

[`verification/`](verification/) holds the runnable probes: on-device scripts for keybinds,
terminal actions, terminal protocols and OSC 133 shell integration, the JVM escape-parser fuzz
harness, the X11 display check, and the agent-platform smoke clients under `verification/tai-ext/`.
