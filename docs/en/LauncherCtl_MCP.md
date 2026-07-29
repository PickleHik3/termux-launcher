# LauncherCtl MCP

LauncherCtl exposes Android launcher tools as a local stdio MCP server. Use it
when an MCP-capable agent should read launcher state, search apps, inspect
notifications, read media/system status, or launch Android intents with explicit
approval.

The server command is:

```sh
/data/data/com.termux/files/usr/bin/launcherctl mcp
```

Prefer the absolute path in agent configs. It avoids PATH and shebang issues in
Termux-based MCP clients.

## Requirements

Open Termux Launcher at least once after installing or updating the APK. The app
writes the runtime files used by the MCP bridge:

```text
~/.launcherctl/endpoint
~/.launcherctl/token
```

Install Python if it is missing:

```sh
pkg install python
```

Optional but useful for testing examples:

```sh
pkg install jq
```

Check the bridge before configuring a client:

```sh
launcherctl status
launcherctl capabilities
launcherctl mcp --help
```

`launcherctl status` should report a running localhost endpoint. If the endpoint
or token file is missing, open Termux Launcher and retry.

## Client Configs

Use this command and argument pair in clients that ask for a command:

```text
command: /data/data/com.termux/files/usr/bin/launcherctl
args: ["mcp"]
```

### Codex CLI

```sh
codex mcp add launcherctl-mcp -- /data/data/com.termux/files/usr/bin/launcherctl mcp
codex mcp list
```

Manual TOML equivalent:

```toml
[mcp_servers.launcherctl-mcp]
command = "/data/data/com.termux/files/usr/bin/launcherctl"
args = ["mcp"]
```

Codex may use `$CODEX_HOME/config.toml` instead of `~/.codex/config.toml`.
Restart Codex after adding the server.

### OpenCode

Add this under the top-level `mcp` object in `~/.config/opencode/opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "launcherctl-mcp": {
      "type": "local",
      "command": [
        "/data/data/com.termux/files/usr/bin/launcherctl",
        "mcp"
      ],
      "enabled": true
    }
  }
}
```

Verify with:

```sh
opencode mcp list
```

### Claude Code

Claude Code supports adding MCP servers from the CLI. The JSON form is explicit
about stdio transport:

```sh
claude mcp add-json launcherctl-mcp --scope user \
  '{"type":"stdio","command":"/data/data/com.termux/files/usr/bin/launcherctl","args":["mcp"]}'
```

Then start Claude Code and run:

```text
/mcp
```

### Gemini CLI

Add this to `~/.gemini/settings.json` for global use, or
`.gemini/settings.json` inside one project:

```json
{
  "mcpServers": {
    "launcherctl-mcp": {
      "command": "/data/data/com.termux/files/usr/bin/launcherctl",
      "args": ["mcp"]
    }
  }
}
```

Gemini CLI can also manage entries with `gemini mcp add`.

### Cursor, Cline, Roo Code, Claude Desktop, and Other MCP JSON Clients

Many MCP clients accept the common `mcpServers` JSON shape. Put this in the
client's MCP config file or MCP settings UI:

```json
{
  "mcpServers": {
    "launcherctl-mcp": {
      "command": "/data/data/com.termux/files/usr/bin/launcherctl",
      "args": ["mcp"]
    }
  }
}
```

Common locations:

| Client | Typical config location |
| --- | --- |
| Cursor | `~/.cursor/mcp.json` or `.cursor/mcp.json` |
| Windsurf / Devin Desktop | `~/.codeium/windsurf/mcp_config.json` |
| Claude Desktop | In-app Extensions/Developer settings; raw JSON clients use `mcpServers` |
| Cline / Roo Code | Extension MCP settings or imported `mcpServers` JSON |

If a client has an in-app "Add MCP server" button, use the same command and args
instead of editing JSON by hand.

## MCP Tool Names

MCP tool names use underscores. The HTTP and CLI agent registry uses dotted
names. Confirmation-gated tools accept `_confirm: true` only after the user has
approved the action.

| MCP tool | LauncherCtl tool | Risk | What it does |
| --- | --- | --- | --- |
| `capabilities_get` | `capabilities.get` | Low | Reads device, integration, and tool availability. |
| `apps_search` | `apps.search` | Low | Searches installed apps by label or package. |
| `apps_launch` | `apps.launch` | Medium | Opens an installed app by label, package, activity, or stable id. |
| `notifications_recent` | `notifications.recent` | Low | Reads recent notification history events. |
| `notifications_since` | `notifications.since` | Low | Reads notification history since an epoch-millisecond timestamp. |
| `notifications_search` | `notifications.search` | Low | Searches notification history text. |
| `notifications_stats` | `notifications.stats` | Low | Counts notification history events. |
| `notifications_pin_rules` | `notifications.pin_rules` | Low | Lists the status-bar notification pin rules. |
| `notifications_pin_rule_add` | `notifications.pin_rule_add` | Medium | Adds a status-bar notification pin rule. |
| `notifications_pin_rule_remove` | `notifications.pin_rule_remove` | Medium | Removes a status-bar notification pin rule by id. |
| `media_now_playing` | `media.now_playing` | Low | Reads the current media session. |
| `system_resources` | `system.resources` | Low | Reads CPU, memory, storage, battery, network, and backend state. |
| `intent_open` | `intent.open` | Medium | Opens an Android URI/action intent. |
| `memory_write` | `memory.write` | High | Stores a short key/value note in local agent memory. |
| `memory_search` | `memory.search` | Medium | Searches local agent memory. |
| `events_tail` | `events.tail` | Low | Reads recent LauncherCtl agent/system events. |
| `user_confirm` | `user.confirm` | Critical | Represents an explicit confirmation request for clients. |

Use `launcherctl tools` or MCP `tools/list` for the exact live schema.

## External MCP Servers for Agent Tools

The launcher app can also consume external stdio MCP servers and expose selected
tools through the HTTP agent API used by clients such as Tooie:

```text
GET  /v1/agent/tools
POST /v1/agent/execute
```

Configure inbound MCP servers at:

```text
~/.config/termux-launcher/mcp.json
```

Example web-search configuration:

```json
{
  "servers": {
    "web": {
      "transport": "stdio",
      "command": "npx",
      "args": ["-y", "some-web-search-mcp"],
      "env": {
        "SEARCH_API_KEY": "$SEARCH_API_KEY"
      },
      "tools": {
        "allow": ["web.search"],
        "deny": []
      },
      "timeout_ms": 10000
    }
  }
}
```

Termux Launcher can generate Brave Search and SearXNG presets from the app UI:

```text
Settings -> Launcher -> Agent web search
```

The preset stores secrets in app preferences and writes placeholders into
`mcp.json`, for example `$LAUNCHERCTL_BRAVE_API_KEY`. LauncherCtl resolves those
placeholders when it starts the stdio MCP process.

Keep allowlists narrow. LauncherCtl bounds search-style calls to a small result
count and compacts MCP responses to short rows such as `title`, `url`, and
`snippet`. Unknown or state-changing tool names are marked non-low-risk and
confirmation-required.

## Live-Verified Examples

These examples use newline-delimited JSON-RPC over stdio. They were verified
against a live Termux Launcher instance. The output snippets are intentionally
small and redacted so notification text, app lists, and local state are not
accidentally copied into logs.

List tools:

```sh
printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  | launcherctl mcp \
  | jq '{toolCount: (.result.tools | length), first: .result.tools[0].name}'
```

Example output shape (local values vary):

```json
{
  "toolCount": 14,
  "first": "capabilities_get"
}
```

Read capabilities:

```sh
printf '%s\n' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"capabilities_get","arguments":{}}}' \
  | launcherctl mcp \
  | jq '{isError: .result.isError, ok: (.result.content[0].text | fromjson | .ok), command: (.result.content[0].text | fromjson | .result.integrations.mcpCommand)}'
```

Example output shape (local values vary):

```json
{
  "isError": false,
  "ok": true,
  "command": "launcherctl mcp"
}
```

Search apps:

```sh
printf '%s\n' \
  '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"apps_search","arguments":{"query":"termux","limit":5}}}' \
  | launcherctl mcp \
  | jq '{isError: .result.isError, body: (.result.content[0].text | fromjson | {ok, count: .result.count, first: .result.apps[0]})}'
```

Example output shape (local values vary):

```json
{
  "isError": false,
  "body": {
    "ok": true,
    "count": 2,
    "first": {
      "label": "Termux:Launcher",
      "packageName": "com.termux",
      "activityName": "com.termux.app.TermuxActivity",
      "stableId": "com.termux/com.termux.app.TermuxActivity"
    }
  }
}
```

Launch an app after explicit approval:

```sh
printf '%s\n' \
  '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"apps_launch","arguments":{"query":"com.termux/com.termux.app.TermuxActivity","_confirm":true}}}' \
  | launcherctl mcp \
  | jq '{isError: .result.isError, body: (.result.content[0].text | fromjson | {ok, label: .result.label, packageName: .result.packageName})}'
```

Example output shape (local values vary):

```json
{
  "isError": false,
  "body": {
    "ok": true,
    "label": "Termux:Launcher",
    "packageName": "com.termux"
  }
}
```

Do not set `_confirm: true` automatically. The client should ask the user first,
or use a separate confirmation flow.

Read recent notifications:

```sh
printf '%s\n' \
  '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"notifications_recent","arguments":{"limit":5}}}' \
  | launcherctl mcp \
  | jq '{isError: .result.isError, body: (.result.content[0].text | fromjson | {ok, count: .result.count, firstEvent: (.result.events[0] | {eventType, packageName: .notification.packageName})})}'
```

Example output shape (local values vary):

```json
{
  "isError": false,
  "body": {
    "ok": true,
    "count": 5,
    "firstEvent": {
      "eventType": "posted",
      "packageName": "example.package"
    }
  }
}
```

Read system resources:

```sh
printf '%s\n' \
  '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"system_resources","arguments":{}}}' \
  | launcherctl mcp \
  | jq '{isError: .result.isError, body: (.result.content[0].text | fromjson | {ok, memory: .result.memory, battery: .result.battery})}'
```

## Troubleshooting

No endpoint or token:

- Open Termux Launcher once.
- Run `launcherctl status`.
- Check that `~/.launcherctl/endpoint` and `~/.launcherctl/token` exist.

`launcherctl mcp: missing required command: python3`:

```sh
pkg install python
```

MCP client shows no tools:

- Run the exact command directly: `/data/data/com.termux/files/usr/bin/launcherctl mcp`.
- Run a `tools/list` example above.
- Restart the MCP client after editing config.
- Prefer the absolute command path in Termux.

Action tool returns `confirmation_required`:

- The tool is working.
- Ask the user to approve the action.
- Retry with `_confirm: true` in the MCP tool arguments.

Notifications or media are empty:

- Grant Android notification listener access to Termux Launcher.
- Reopen the launcher and retry `launcherctl capabilities`.

Security notes:

- Treat `~/.launcherctl/token` like a password.
- Do not paste notification contents or bearer tokens into shared logs.
- Keep LauncherCtl bound to localhost unless you intentionally changed the bind mode.
