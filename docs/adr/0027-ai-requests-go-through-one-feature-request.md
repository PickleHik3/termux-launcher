---
status: accepted
date: 2026-11-11
amends: 0022 §3 (the model the page uses is called the Default model)
---

# AI requests go through one Feature request

A review on 2026-11-11 of On-device AI found that getting from "this Feature wants an answer" to
tokens was decided in many places. TaiManager had a streaming and a non-streaming chat path, each
with its own remote branch. The load decision lived in two places, and the Feature load plan was
worked out once in the app process and again in `:tai_runtime`. Every front end built its own
request: the Assistant sent no model, Cleanup loaded its model itself, and Launcherctl re-parsed
the stream. Twenty files rebuilt "which models are on this phone", and the Assistant page's cache of
that answer drifted (54495b6df). Cancel had no id, so stopping the Assistant also stopped a terminal
program's answer, and an HTTP client that disconnected left its generation running.

The developer decided the following in a grilling session on 2026-11-11.

## 1. Order

Five rounds: **Model availability** and the narrow model-runtime interface side by side, then the
typed runtime seam, then **Feature request**, then splitting what is left of TaiManager. Each round
merges into v2-redesign on its own, after its tests and the full suite pass, and gets an emulator
smoke check (an Assistant answer, a Cleanup, a `tai` chat). Pong checks are batched after Feature
request. Assistant waves 2-3 (ADR 0022) wait until Feature request has landed. Launcherctl's routes
move onto the new modules in the round that builds them.

## 2. Model availability

One module answers which models are on the phone and, for a Feature, which model serves it and that
model's state: missing, downloading, on the phone, loading, loaded, remote ready, remote not set up.

- "Loaded" comes from the runtime presence file and its broadcast, for every screen. The Model
  Centre's "In use" pill moves to it as well.
- `TaiModelStore`'s write methods (save, import, delete, finishing a download) tell it when the
  models change, and it tells its listeners. Screens keep no caches of their own.
- It works out which model serves a Feature through `TaiFunctionModels.resolve`. When a request
  names no model, the fallback is request handling and belongs to Feature request.
- The settings label "Default assistant model" becomes **Default model**. The preference key
  `tai_role_default_assistant` stays.

## 3. The model-runtime interface

The local backends (LiteRT, MNN) take one `generate(request, callback)` call. The version without a
callback is a helper that collects the answer. Remote stays out of this interface: it runs in the
app process, and `TaiRuntime` runs only in `:tai_runtime`.

## 4. The runtime seam

Generating answers and loading models (generate, load, unload, keep warm, cancel, status) cross
`:tai_runtime` through a typed interface. It has an in-process adapter and a Messenger adapter, and
the Messenger adapter keeps JSON on the wire inside. Speech, image, embeddings and bench keep their
op codes until the last round. An in-memory fake and a contract test, run against both the fake and
the in-process adapter, are the test surface.

Every generation carries an id and a priority. Cancelling an id stops that generation if it is
running, takes it out of the queue if it is waiting, and otherwise does nothing. The global cancel
stays for `/v1/ai/runtime/cancel`.

## 5. Feature request

A caller names a Feature, the messages and a sink. Feature request works out the Feature load plan
once, chooses local or remote, loads or reuses the model, streams typed events (Waiting, Loading,
Text, Done with usage, Error with a code), and returns a handle that cancels that request alone. A
remote request is cancelled by closing its connection.

- Launcherctl turns the events into OpenAI-compatible SSE or JSON at its own edge, and cancels a
  request whose client disconnects.
- One generation runs at a time and is never interrupted. While requests wait, interactive in-app
  requests (Assistant answers, Cleanup) go ahead of everything else. App sorting, feature checks
  and every HTTP request (Dawn included, even when it names a Feature) wait in arrival order. There
  is no guard against an HTTP request waiting a long time: interactive requests come one at a time
  from a person.
- An HTTP request that names no Feature is served as the Assistant Feature, with the Default model.

## Considered options

- **Build Feature request first, rework the seams under it later**: rejected. It would be written
  against today's TaiManager and then rewired twice.
- **"Loaded" read over IPC**: rejected. Model availability would then depend on the runtime seam
  and wake the runtime just to read status.
- **Remote as a `TaiRuntime` adapter in `:tai_runtime`**: rejected. Remote calls do not wake the
  native process (remote provider design §4.2).
- **Every op typed at once**: rejected. Feature request needs only generation and residency.
- **Any request that names a Feature jumps the queue**: rejected. Any program could tag itself to
  skip the queue.
- **A separate Feature for untagged HTTP requests**: rejected. It would add one more picker for
  little gain.

## Consequences

- Front ends stop parsing OpenAI JSON; the Assistant's status line reads its stage from events.
- Launcherctl needs a test that compares its output to today's OpenAI chunks.
- LiteRT and MNN cancel must check that the id matches the active generation.
- Documents that say "default assistant model" need a wording pass.
