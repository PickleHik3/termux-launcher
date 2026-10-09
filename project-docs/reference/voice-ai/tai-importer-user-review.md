# TAI model importer: review from a non-technical user's seat

Reviewed 2026-09-25 on Waydroid (Android 13, x86_64), dev at `bf2a7f03`, by walking
Settings → Services & permissions → TAI → Import model as a first-time user would. The code
references come from `TaiPreferencesFragment.showImportFlowDialog` (:1738-1985),
`TaiImportProfileDialog`, and `TaiModelImporter` (:303-398). The companion technical study is
`tai-model-ecosystem-research.md`; this one only asks: *can someone who has never heard of
LiteRT add a model and know it worked?*

## The persona

Someone who installed the launcher for its looks, saw "AI" in settings, and has one of two
things: a link a friend or a video gave them ("get this model from Hugging Face"), or a file
they already downloaded in the browser. They don't know what a file extension means for a
model, what an accelerator is, or what "context" is. They want: *add it, see that it works,
chat with it.*

## What they meet today

One dialog, "Add a model", shows everything at once:

1. "Paste a Hugging Face repo URL or choose a local LiteRT package. Capabilities are guessed
   from known LiteRT/MNN model names and can be changed before adding."
2. A URL field: "Hugging Face repo URL (or direct file URL)".
3. "Capabilities (auto-filled; change if needed):" — eight checkboxes: Chat/completions, Text
   embeddings, Vision/image input, Audio input, Function tools, Code-specialized,
   Reasoning-specialized, Multilingual.
4. "Compatible accelerators" — "GPU compatible" checkbox and a "Default: CPU" spinner.
5. "MODEL FORMAT SETTINGS" — opens a second dialog asking for a "Thought opening marker",
   "Thought closing marker", "Maximum context supported by this file", and a thinking mode.
6. "Model id (optional)".
7. "Hugging Face token: not set · tap to add (for gated/private repos)".
8. "No local file selected." + "IMPORT AN MNN FOLDER" + "Choose file" + "Import & verify".

## Problems, in the order the user hits them

| # | Where | What the user experiences | Severity |
|---|---|---|---|
| 1 | Whole dialog | Two different jobs (paste a link / pick a file) and ~15 controls on one screen. There is no obvious first step. | High |
| 2 | Opening text | "LiteRT package", "MNN" — words they have never seen, in the first sentence. | High |
| 3 | Capabilities | Asked to *declare* what the model can do before anything is known. Unticking "Chat" by mistake gives a model that never shows up for chat; ticking "Vision" on a text model gives errors later. The app can mostly work this out itself. | High |
| 4 | Accelerators | "GPU compatible" / "Default: CPU" — a performance tuning choice presented as a required setting. A wrong GPU choice is the most likely cause of a failed first load. | High |
| 5 | Model format settings | "Thought opening marker", "Maximum context supported by this file": expert-only, and presented as part of the normal path. | Medium (it's behind a button, but the button is capitalised and prominent) |
| 6 | Model id | "id" is programmer language; "optional" but no hint what happens if empty. | Low |
| 7 | Token | Shown before any need for it. Most public models don't need it; a user may think they must get one. | Medium |
| 8 | "IMPORT AN MNN FOLDER" | A third import path, jargon label, and it contradicts the validator's own message ("Local folder import is not supported by this file picker yet"). | Medium |
| 9 | Errors | Written for developers: "Raw weight files are not supported. Select a LiteRT .litertlm/.task package or download an MNN config.json from the model market." A user who picked a `.gguf` from a Llama tutorial learns nothing actionable. | High |
| 10 | Progress | Local copy: "Copying selected model into app-private storage..." with no percentage for a multi-GB file; MNN folder: no progress at all. Looks frozen. | Medium |
| 11 | Result | "Model imported" toast, then nothing. The user isn't told whether it will fit in memory, isn't offered "Try it", and isn't told how to make it the default. | High |
| 12 | Fit | No warning before copying 4 GB that the phone has 6 GB RAM and the model won't load. The catalog shows RAM tiers ("12GB+"); the importer doesn't. | High |

## Recommended flow

Replace the single dialog with a short guided flow. Everything the app can detect, it detects;
everything else is optional and collapsed.

**Step 1 — "Where is the model?"** Two big choices, each with one line of help:
- *Paste a link* — "From Hugging Face or another website."
- *Choose a file on this phone* — "A model you already downloaded."
(MNN folders: fold into "Choose a file" when the user picks a folder-shaped package, or move to
Advanced; don't show as a third top-level path.)

**Step 2 — the app works it out.** For a link: resolve the repo, list the files it can
actually run with human labels ("Gemma 3 1B — 1.2 GB — recommended", "…int4 — smaller, a bit
less accurate"), pre-select the best fit for this phone's RAM, hide the rest behind "Show all
files". Ask for a token only when the server says the model is gated, with a one-line reason
and a "Get a token" link — which already exists, just move it.

**Step 3 — summary card before anything big happens.**
- Name (editable, pre-filled from the repo/file — this replaces "Model id").
- What it can do, as read-only chips detected from metadata/name ("Chat", "Understands
  images"), with "Change" under Advanced.
- Size and **"Will it run on this phone?"**: *Yes* / *Probably slow* / *Too big for this phone
  (needs about N GB of memory)* using the same RAM tiers as the catalog.
- One primary button: **Add model** (download/copy). Secondary: Cancel.
- Collapsed **Advanced**: capabilities checkboxes, processor preference ("Let the app decide"
  default; CPU; GPU), model format settings, id.

**Step 4 — progress that means something.** Percentage + size for copies and downloads, in
the dialog or the model row, with Cancel.

**Step 5 — "Ready".** "Gemma 3 1B is ready." Buttons: **Try it** (loads and opens a one-line
test chat or runs a short prompt and shows the reply), **Use as default**, Done. If the first
load fails, say what to try in plain words (e.g. "Try again using the processor instead of the
graphics chip" → retries CPU).

## Error messages, rewritten

| Today | Proposed |
|---|---|
| Raw weight files are not supported. Select a LiteRT .litertlm/.task package or download an MNN config.json from the model market. | This file can't run in this app. Look for a version of the model that ends in **.litertlm** or **.task** — the Browse catalog lists models that work. |
| Native libraries cannot be imported as models. | This is a program file, not a model. |
| LiteRT imports must be .litertlm, .task, or .tflite packages. | This app can run model files ending in .litertlm, .task or .tflite. This one ends in .%s. |
| Enter a Hugging Face repo URL (https://huggingface.co/<org>/<model>) or a direct …/resolve/main/<file> URL. | Paste a link to a model page, like huggingface.co/google/gemma-3-1b-it. |
| Not enough free storage to import this model. | Not enough space: this model needs %1$s and the phone has %2$s free. |
| The selected file does not look like a readable model package. | This file doesn't look like a model, or it didn't download completely. Try downloading it again. |
| This Hugging Face repo is gated or private. Add your access token after accepting the model's terms on huggingface.co, then retry. | This model needs you to sign in to Hugging Face and accept its terms first. [Open the model page] [Add my token] |
| Model import failed. | Couldn't add the model. [Show details] (details = the technical message, for bug reports) |

## Keep

- Guessing capabilities and accelerators from known names — just stop asking the user to
  confirm them up front.
- The artifact chooser for repos with several files — relabel it in plain words and
  pre-select.
- The gated-repo retry loop and "Get token" link.
- Copying into app-private storage (but say "Adding to the app…" not "app-private storage").

## Acceptance checks (for the implementation)

1. A public repo link with one runnable file reaches "Ready" with one tap after pasting.
2. A repo with several files pre-selects one that fits the device and explains the others.
3. A `.gguf` / `.safetensors` pick produces a plain message naming what to look for.
4. A model above the device's RAM tier shows "Too big for this phone" before copying.
5. No jargon from this list in the default path: LiteRT, MNN, accelerator, capability,
   context, token (until needed), id, artifact, graph, quantization.
6. "Try it" after import produces a reply or a plain-language failure with a retry.

## What this branch implemented

Added 2026-09-25, after the review above. The flow lives in `TaiImportFlow` (the fragment only
launches it, owns the two system pickers and redraws when told); the pure parts are unit-tested.

| Recommendation | Implemented | Still owed |
| --- | --- | --- |
| Step 1, two choices | "Paste a link" / "Choose a file on this phone", one line of help each. The MNN folder path is the small print under them ("Advanced: add a model folder"). | — |
| Step 2, the app works it out | A Hugging Face link is previewed (`downloadModel` with `previewOnly`) before anything downloads. One runnable file goes straight to the card; several show "Which version?" with the file name, size, a build hint (`int4` → "smaller, a bit less accurate") and the fit for this phone, pre-selecting the largest file that fits (`TaiImportFlow.preselect`). A gated repository asks for sign-in only then, with "Open the model page" and "Add my token". | Files are still listed by their published names; no "Text" / "Text and images" labels, since nothing in the metadata says which a file is. |
| Step 3, the card | Name (pre-filled by `TaiImportNames`), what it can do as read-only words, size, and "Will it run on this phone?" from `TaiImportFit`: the catalog's RAM tiers applied to the file size against the phone's RAM class — Yes, Probably slow (one class under), Too big (two under, with the tier named). Too big asks "Add it anyway?". Advanced (collapsed) keeps the eight checkboxes, "Runs on" (Let the app decide / Processor / Graphics chip), Model format settings and an internal name. | The tiers are inferred from size, not measured. |
| Step 4, progress | Copies report bytes and a percentage from `TaiModelImporter`'s copy loop (folders sum their files), with Cancel; downloads are followed in the same dialog from the store's transfer record, with Cancel and Hide. | — |
| Step 5, ready | "<Name> is ready" with Try it, Use as default, Done. Try it loads the model, asks "Say hello in five words." through the chat endpoint and shows the reply; a failed load says why in plain words and offers "Try with the processor" when the graphics chip was involved. Embedding models get a one-line explanation instead. | Not run on a device from this branch. |
| Error messages | Mapped in `TaiImportMessages` from the stable error codes; the technical message sits behind "Show details". The importer now names its storage figures and a `reason` (`cancelled`, `unreadable_model_file`) without changing its codes. The contradictory MNN folder message is gone. | Translations. |
