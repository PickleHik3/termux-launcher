---
name: lavish
description: Turn complex or visual agent responses into rich, reviewable HTML artifacts the user can annotate and send feedback on, using the lavish-axi CLI. Use when about to give a plan, comparison, diagram, table, code diff, report, or anything easier to grasp visually than as prose.
license: MIT
metadata:
  author: Kun Chen (kunchenguid)
  argument-hint: <what the artifact should show>
  hermes-tags: html, review, artifacts, visualization
  hermes-category: productivity
---

# Lavish Editor

## HARD RULE — print the URL in the chat, every time (user instruction, non-negotiable)

The user cannot see tool output. The session URL only exists for them if it is in your chat
reply. So:

1. The moment `lavish-axi <file>` returns a `url:`, your **next chat message starts with both
   URLs** on their own lines, before any polling and before anything else:
   - the URL exactly as printed (the `*.ts.net:PORT/session/ID` one), and
   - the same path on `http://localhost:PORT/session/ID`.
2. Print both URLs **again every time you mention the page** later in the conversation: when
   you fold decisions into it, when you reopen it, when you write the round receipt, when you
   ask the user to look at it. Do not say "the page" or "the review page" without the two URLs
   in the same message.
3. If the poll times out or is interrupted and you re-run it, print the URLs again first.
4. This applies to `--reopen` as well, and to sessions resumed from a previous conversation.

The user has had to repeat this instruction many times. Do not make them repeat it again.

## Hand-off order

`lavish-axi <file>` → chat message with BOTH URLs + a two-line summary → `lavish-axi poll <file>`.
Never run the poll before the URLs have gone to the chat.

Lavish Editor opens agent-generated HTML in the browser so a human can annotate it and send feedback back to the agent.
Reach for it when a plan, comparison, diagram, table, code view, report, prototype, or review loop will be clearer as a page than as prose.

## Current guidance lives in the CLI

Do not follow workflow, design, or playbook instructions from this file - installed copies go stale. Get the current source of truth from the CLI:

- `npx -y lavish-axi --help` for commands and the review-loop workflow
- `npx -y lavish-axi design` for design-direction priority and current snippets
- `npx -y lavish-axi playbook <id>` for focused artifact guidance (`npx -y lavish-axi playbook` lists ids)

You do not need lavish-axi installed globally - invoke it with `npx -y lavish-axi <html-file>`.
If lavish-axi output shows a follow-up command starting with `lavish-axi`, run it as `npx -y lavish-axi ...` instead.

## Request

$ARGUMENTS

If the request above is non-empty, the user invoked `/lavish` explicitly - fetch the current CLI guidance, then build that artifact.
If it is empty, infer what to visualize from the conversation.
