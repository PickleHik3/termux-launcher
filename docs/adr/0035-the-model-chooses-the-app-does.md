---
status: accepted
date: 2026-11-25
amends: 0022 §3 (no model tool calls; nothing is pre-written)
---

# The model chooses, the app does

Spec: issue #311 (the Assistant that acts), ticket #377.

ADR 0022 §3 gave the Assistant page a chat that writes text only: "No model tool calls", and
"Nothing is pre-written". The launcher now has one settings registry (declarations with ranges,
titles, complaint phrases and an "Assistant may offer" flag), so a person can say "make the text
bigger" and the Assistant can answer with the change itself. Two lines of §3 stop being true. This
ADR replaces them and says what holds in their place.

## 1. The model may propose a settings change; the app checks it and shows a card

- The app looks up the settings nearest the message (keyword match over titles, summaries and
  complaint phrases; embeddings later) and sends the model only that shortlist of 8, with the
  Assistant's instructions, as tools. A setting the Assistant may not offer is never sent.
- The model may answer in text, propose up to two changes, ask one question, or decline.
- The app accepts a proper tool call and the same call written as text. A change must name an id from
  this turn's shortlist and a value its declaration allows, or `up` or `down`, which the app turns into
  one step. Anything else is dropped. With nothing valid and no words, the row reads "No matching
  setting" with a link that opens Settings search filled with the message. When the right answer is a
  setting the Assistant may not offer, the card links to it instead.
- The result is a card in the thread: each change as the setting's title and from and to, and one
  Apply. Nothing changes until Apply is tapped, on either Default model. Apply writes through the
  registry and the settings applier, so the launcher refreshes as it does from Settings.
- Card copy is product copy: titles, values and Apply. No ids and no model names.

## 2. The app may show its own offer cards

"Nothing is pre-written" stays for the model's words: no starter prompts, no suggestion chips. It no
longer bars the app from writing a card of its own, such as the housekeeping offers ("12 new apps
aren't sorted. Sort now"). Those are drawn from measured values by app code, never sent to the model
and never part of a chat.

## The rule

**The model chooses, the app does.** The model names what it would change; the app checks the choice
against the registry, shows it, and writes it only when the person taps. No model output reaches a
store without passing the registry's declaration for that setting.

## Consequences

- Personal data enters a prompt only as ADR 0022 allows. A setting's current value is launcher state,
  not personal data.
- A small model's mistakes cost nothing: a wrong or invented choice is dropped before it is shown, and
  a wrong one that is shown needs a tap.
- Undo and a card that survives a restart are the next ticket (#378): until then a card lives for the
  process, and a reopened chat shows the answer's stored line.
- Everything else in ADR 0022 stands.
