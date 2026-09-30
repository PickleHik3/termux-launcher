# Shipped agent skill — spec draft (2026-09-15)

Base: dev 4ab57586. Status: draft, not agreed.

## Problem

An agent harness running inside this launcher has no idea which app it is in. Asked about "the
terminal", "the dock" or "my keyboard", it answers from upstream Termux — the popular app whose
docs, wiki and package advice describe a different program with no home screen, no dock, no
widgets and no `launcherctl`. Today the only fix is to point the model at this repository, which
a user who merely installed the app cannot do.

Goal: install the app, install any harness, ask a question about the launcher, get an answer
grounded in this launcher — with no repository, no clone and no configuration.

## Shape

Three layers, each useful without the ones above it.

**1 · Identity, always loaded.** Two sentences in `$HOME/AGENTS.md`, with `$HOME/CLAUDE.md` a
symlink to it: this terminal is Termux Launcher, a fork of Termux that is also the Android home
screen, and upstream Termux documentation describes a different app. Claude Code reads `CLAUDE.md`
from the cwd recursing up to root, so it loads for any session under `~`; Codex reads `AGENTS.md`
at the cwd when there is no git root. Neither file lives in a harness configuration directory —
we never create `~/.claude` or `~/.codex` uninvited.

**2 · The skill, readable from day one.** `SKILL.md` plus reference files at
`$HOME/.termux-launcher/agent-skills/termux-launcher/`, app-owned and rewritten on upgrade. The
`AGENTS.md` block names that path, so an agent can read the skill before anything is installed.
Installation buys automatic loading, not access.

**3 · Installation, only on the user's word.** `launcherctl agent skill install` links the skill
into whichever harness skill directories are appropriate (`~/.claude/skills/`, `~/.codex/skills/`,
`~/.agents/skills/`, `~/.gemini/config/skills/`). That command is the first thing that creates a
harness directory, and it runs only after the user answers.

### Session env marker

Inside a git project Codex starts at the repository root and never sees `$HOME/AGENTS.md`. The
session exports `TERMUX_LAUNCHER_VERSION` and `TERMUX_LAUNCHER_AGENT_SKILL=<path to SKILL.md>`, so
an agent that runs `env` or any shell script still finds the identity and the payload.

## The offer, and its three answers

The `AGENTS.md` block is fenced by `<!-- termux-launcher:begin -->` / `:end` markers and rewritten
on app start from stored state; anything the user wrote outside the markers survives untouched.

While pending, the block carries the identity lines, the skill path, and an instruction: the first
time the skill would be useful, tell the user it exists and ask which they want, then run their
answer — never without it.

| Answer | Command | Effect |
|---|---|---|
| Install | `launcherctl agent skill install` | Creates the harness links; block shrinks to identity plus where the skill landed. |
| Later | `launcherctl agent skill later` | Offer suppressed until the next app version. |
| Never | `launcherctl agent skill never` | Block reduced to the two identity lines, permanently. |

`launcherctl agent skill status` reports the state; `launcherctl agent skill uninstall` removes the
links. Whether a model surfaces the offer is its own judgement, so the same choice appears in the
first-boot tour and in Settings, and the state is shared between both routes.

## Skill content

Audience: someone using the app, not someone hacking on it. Name `termux-launcher`, never
`termux`. The `description` carries the words users type — termux, terminal, launcher, home
screen, dock, widget, keyboard, theme, split, session, wallpaper — plus the disambiguation
sentence, because the description is all a harness sees when deciding to load it.

Body: a verification step first (`launcherctl --version` present means this skill applies, absent
means upstream Termux and it does not), then a feature index in `CONTEXT.md` vocabulary — place,
surface, pane, the two editors — each feature pointing at the `launcherctl` command or the
Settings path that changes it. Reference files per cluster, loaded on demand. The skill is an
agent that can act, not a manual.

## Mechanics

- Payload ships as assets under `app/src/main/assets/agent-skills/termux-launcher/`.
- `AgentSkillInstaller.ensureInstalled(context)` mirrors `TermuxShellIntegrationInstaller`: called
  from `TermuxApplication` and `TermuxActivity`, writes only when bytes changed, so an app update
  refreshes the skill without touching the links or the user's answer.
- The `AGENTS.md` merge is additive and idempotent in the manner of `ClaudeHooksInstaller`.
- Links are created with `Os.symlink`, so upgrades flow through them.

## Out of scope

- The contributor-facing skill for this repository (`.agents/skills/…`, symlinked per clone). Same
  idea, different audience; specified separately.
- Distributing the skill as a package from the VAJ repository. Assets keep the skill and the app at
  one version, which is the point.
- Any write to `~/.claude/settings.json`; `launcherctl agent install-hooks` keeps that job.

## Open questions

- Does `$HOME/CLAUDE.md` as a symlink to `AGENTS.md` confuse a harness that writes memory back to
  it? If so, ship two files with identical managed blocks.
- Which harness directories to link on install: all four, or only those that already exist?
- Does "later" mean the next app version, or a number of days?
- Where the env marker is exported from, given shell rc files stay untouched.

## Build plan

| Phase | Branch | Deliverable | Depends on | Gate |
|---|---|---|---|---|
| 1 | `feat/agent-skill-payload` | Asset skill (`SKILL.md` + references), `AgentSkillInstaller`, identity block merge, unit tests for merge idempotency | — | Install on pong, read `~/AGENTS.md`, ask an agent a launcher question cold |
| 2 | `feat/agent-skill-offer` | `launcherctl agent skill install/later/never/status/uninstall`, state storage, block rewriting, env marker | 1 | Each answer changes the block exactly once; links appear only after `install` |
| 3 | `feat/agent-skill-ui` | Tour card and Settings entry sharing the same state | 2 | Answer in the app, verify from the shell, and the reverse |
