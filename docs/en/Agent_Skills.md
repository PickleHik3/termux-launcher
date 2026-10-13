# Agent skills

A coding agent in the terminal knows Termux, not Termux Launcher. Two skills from
[tlstore](Tlstore.md) teach it the rest, and Claude Code, Codex, OpenCode, pi or any agent that
reads skills picks them up on its own.

| Skill | What your agent can do with it |
| --- | --- |
| `termux-launcher` | Run its work in a pane or window beside you, notify you, show progress, and answer questions about the launcher from these pages. |
| `termux-launcher-speak` | Read its answers aloud on the phone when you ask. |

## Install them

```sh
tlstore install termux-launcher-skill
tlstore install termux-launcher-speak
```

Both go to `~/.agents/skills/`, where Codex, OpenCode and pi look, and are linked from
`~/.claude/skills/` for Claude Code. Start a new agent session afterwards.

## Ask for them

You never name a skill; say what you want. For example:

- "How do I change the terminal font?"
- "Read your answers aloud from now on."

## Reading aloud

The speech skill uses the phone's own voice, so it needs the **KittenTTS Nano** voice model:
**Settings → On-device AI → Model centre → Get models → Voice output**. Say "stop" to cut a
reading short, and tell the agent when you want written answers only again. See
[Text to speech](Text_To_Speech.md).
