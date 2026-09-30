# LauncherCtl Agent Platform Verification

This directory contains live-device verification notes and reusable example
clients for the `tai-ext` LauncherCtl agent platform.

## Scripts

- `scripts/launcherctl-agent-smoke.sh`
  - Exercises the installed `launcherctl` CLI, HTTP API, notification history,
    agent route/execute, MCP stdio bridge, and TAI status surfaces.
  - Posts and removes a temporary Termux notification when `termux-notification`
    and `termux-notification-remove` are available.
- `scripts/example-agentic-client.py`
  - Minimal example of how a local CLI agent can route natural language to
    LauncherCtl tools and execute confirmed tool calls.
- `scripts/notification-digest-example.py`
  - Example notification digest script that groups recent notification history
    by package and can optionally ask the local OpenAI-compatible TAI endpoint
    for a short summary.

All scripts read `~/.launcherctl/endpoint` and `~/.launcherctl/token`.
They do not print the bearer token.
