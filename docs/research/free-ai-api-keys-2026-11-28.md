# Free AI API keys for first-run setup (research, 2026-11-28)

Question: which free cloud API should the welcome pages suggest to a user whose phone is too weak
for an offline model? Accessed 2026-11-28. Not committed.

Confidence key: **P** = provider's own page read today; **F** = NVIDIA forum / user reports only;
**T** = third-party blog (promotional or stale; treat as a lead). Several provider pages gave no
numbers (Google and Mistral publish limits only inside a logged-in console), and I say so.

## What the app needs (from the ADRs)

- ADR 0027: every AI request is one Feature request; remote models are a state ("remote ready /
  remote not set up") of Model availability. A cloud provider is therefore a model row, not a new
  path.
- ADR 0035: the model chooses, the app does. The Assistant sends a shortlist of 8 settings as
  **tools**, accepts a real tool call or the same call written as text, validates it, shows a card.
  So the provider must support OpenAI-style `tools` on `/chat/completions`, and ideally tolerate
  tool calls with `stream: true`. The text-form fallback means a model with weak tool calling still
  works.
- Request volume is small: one short chat turn per user message, a 8-tool shortlist, no long context.
  Low per-minute limits are fine; what matters is a daily or monthly ceiling that does not run out
  in a week, and a key that survives.
- I did not audit the remote-call code under `app/src/main/java/com/termux/ai/` beyond the file list
  and ADRs.

## Headline table

| Provider | Key friction | Free limit | Tool calling | OpenAI-compatible | Free-use terms | Verdict |
|---|---|---|---|---|---|---|
| **NVIDIA build.nvidia.com** | Developer account + **phone SMS verification**; no card. Many users in many countries cannot verify (code never arrives, country missing, "exceeded limits"); fix is an email to support (F) | ~1,000 credits at sign-up and **40 RPM**, described as "unlimited" but RPM-capped (F). No official page found | Yes on several (Nemotron 3 Ultra/Super, Mistral Large 4, GLM-5.3 Flash, Step 5) (P: catalog page) | Yes, `https://integrate.api.nvidia.com/v1` (known; not re-verified today) | Forum says dev/prototyping use, not production (F, unconfirmed). ToS not read | Best capability for free, worst reliability of getting a key |
| **Alibaba Model Studio (Qwen), Singapore** | Alibaba Cloud account, account-info step required since Sep 15, accept agreement in Singapore console. Card not mentioned on the page; I could not confirm none is needed | New-user quota, about 1M tokens **per model**, **90 days**, then pay-as-you-go unless "Free Quota Only" is switched on (off by default). qwen-max, qwen-plus, qwen3-max free quotas **already removed** for new users (P) | Yes (qwen-plus/max listed); **not together with `stream=true`** (P, Chinese docs) | Yes, `.../compatible-mode/v1` on a per-workspace Singapore domain; keys are region-bound (P) | Not read | Expires in 90 days and the flagship models lost their quota: poor as a permanent default; billing risk if the user does not enable Free Quota Only |
| **xAI Grok** | console.x.ai, email or X login. Card needed to buy credits | **No free tier** (docs show prepaid credits only, P). Promos: a one-time ~$25 signup credit and a $150/month data-sharing programme are claimed by promotional sites; one tracker says the $150 programme ended May 2025 (T, conflicting, unverified) | Yes (server-side tools documented; function calling not on the page fetched) | Yes (known, not re-verified) | Data-sharing credits mean sharing requests with xAI, cannot opt out (T) | Not free. Drop |
| **Google AI Studio (Gemini)** | Google account, no card, key in seconds in a browser: easiest on a phone | Limits per project, **not published in public docs** any more, only in a logged-in console (P). Third-party reports: Pro removed from free tier in 2025, Flash daily requests cut hard (T) | Yes | Yes, OpenAI-compat endpoint exists (known) | **Free content is used to improve Google products and read by human reviewers; not allowed for clients offered to users in the EEA, UK or Switzerland (must use Paid); 18+ only, no client likely used by under-18s** (P: terms) | Easy key, but the terms are the problem for a launcher shipped worldwide |
| **OpenRouter** | Email/GitHub/Google sign-in, no card | `:free` models: 20 RPM; **50 requests/day** until the account has bought $10 of credit, then 1000/day (P) | Free list filters by capability; `openrouter/free` router (200K ctx) picks a free model that supports tools/vision (P). Free list **changes often** | Yes, `https://openrouter.ai/api/v1`, streaming | Some free models' prompts may be retained or trained on, per model (P: listing) | Best variety and easiest key, but 50/day is thin for a home-screen assistant; pairs well as a fallback |
| **Groq** | Account, free plan key immediately; card not required (T) | Per org. Free Plan example gpt-oss-120b **30 RPM, 1K RPD, 8K TPM, 200K TPD** (P, table plan label ambiguous). Exceeding returns 429, not a charge | gpt-oss-120b/20b and Llama 3.3 70B listed; tool use supported on these (known) | Yes, `https://api.groq.com/openai/v1`, streaming | Training use not stated in a primary source I could read | Fast, no expiry, good tool calling. 8K TPM caps a long prompt but fits an 8-tool shortlist |
| **Mistral** | The API now needs **billing activation / pay-as-you-go**; Free mode "doesn't include API access" (P, docs today). Older guides describing a free Experiment API tier with phone verification and data-for-training opt-in look **out of date** (T) | None for the API | n/a | n/a | n/a | Drop |
| **Cerebras** | Account; free credits only **after adding a verified payment method** | $5 credits, **30 days**; gpt-oss-120b 5 RPM; "no permanently free tier" (P) | Yes | Yes | n/a | Drop: changed to a trial |
| **GitHub Models** | n/a | **Retired 2026-07-30**: playground, catalog, inference API and BYOK gone (P) | n/a | n/a | n/a | Gone |
| **Cloudflare Workers AI** | Cloudflare account; no card mentioned for the free allocation (P) | **10,000 Neurons/day**, reset 00:00 UTC; bigger models (Kimi K2.6, GLM-5.x, DeepSeek V4) need Paid or prepaid credits (P) | Yes on some models | Yes (`/ai/v1/chat/completions` on the account id URL, known) | Not read | Permanent, small but real, needs account id plus token (two values to paste, awkward). Third fallback |

## Notes per provider

**NVIDIA.** No official page states credits, limits or terms; everything above is from
forums.developer.nvidia.com threads (dozens of "raise 40 to 200 RPM" requests, and a staff-style
reply that increases are not granted there). The sign-up wall is a phone SMS check, and the forum
is full of reports of it failing by country. That contradicts a smooth first-run. Models:
the catalog page names six with tool calling, so the capability is real. Free hosted endpoints may
be rate-limited by overall traffic.

**Qwen.** The free quota is a trial, not a tier. 90 days from activation, one pool per model, the
big commercial Qwen models are excluded for new accounts, and the default after the quota is paid
billing. A launcher suggesting it must tell the user to turn on **Free Quota Only**. Function calling
works only without streaming, which fits ADR 0035's one-shot tool turn but not a streamed reply
in the same request.

**xAI.** The only reliable fact is that docs.x.ai sells prepaid credits (and invoiced billing is off
by default so requests stop at zero). Anything "free" is promotional and unconfirmed. Do not suggest.

**Google.** Technically the simplest and the most capable free option, but its own terms say free
traffic trains Google's products and humans may read it, and clients offered in the EEA, UK or
Switzerland must use paid. An assistant that sees a user's settings and spoken requests should not
default to that. The developer's instinct that the free tier is limiting is right: the limits are
no longer public and were cut in 2025 (T).

**OpenRouter.** Page shows 20 RPM / 50 per day under $10 lifetime purchase, 1000/day above. The
free model list rotates (examples seen: NVIDIA Nemotron 3 Ultra free with 1M context and tool
calling; Poolside Laguna XS 2.1; a Cohere model whose free listing says prompts may train Liquid
models). Beware: listing a paid fallback alongside a `:free` model bills the paid one. The app
must send only `:free` ids or `openrouter/free`.

**Groq.** The cleanest "free and stays free" among those with a published daily ceiling.

## Recommendation

For "just installed, wants a capable assistant for free":

1. **Primary: Groq** (`openai/gpt-oss-120b`, fallback `openai/gpt-oss-20b`). No expiry, no card
   (third-party, confirm in the console at sign-up), OpenAI-compatible with streaming and tools,
   fast replies, and 1K requests/day suits an assistant. Weak points: Groq's training and
   retention terms were not read (read them before shipping copy), 8K TPM limits large prompts, and a
   phone sign-up goes through a browser.
2. **Fallback A: OpenRouter** with `openrouter/free` (it filters for tool support), as the
   zero-friction key; tell the user it allows about 50 requests a day, 1000 after a one-off $10 top-up.
3. **Fallback B: NVIDIA build.nvidia.com**, offered as "if you have a phone number it accepts", for
   the strongest tool-calling models and 40 RPM, with the caveat that verification fails for many
   countries. Qwen only as an "I already have an Alibaba Cloud account" option (turn on Free Quota Only).

Do not suggest xAI (not free), Mistral (API needs billing), Cerebras (trial with card), GitHub Models
(retired). Do not default to Google AI Studio; list it last, with the warning that its free tier
shares prompts with Google and cannot be offered in the EEA, UK or Switzerland.

## What the welcome page should say and ask

- Detect hardware first: strong phone gets "Download an offline model" as the lead card (nothing
  leaves the phone, nothing expires); weak phone leads with the cloud card.
- Cloud card copy (product copy, no mechanism): title "Use a free online assistant", one sentence "Get
  a free key from Groq, paste it here, and the assistant works in seconds." One button "Get a free key"
  that opens the sign-up page in the browser, one paste field, one "Test" that makes a single request.
- Say plainly beside the field: "What you ask is sent to Groq. Your key stays on this phone." (A
  fact for the privacy line, not a mechanism.)
- Keep the key user-owned: the user signs up and pastes their own key; the app never ships a shared
  key, which would breach most of these terms and burn through the limits at once.
- Offer "Choose another service" below, listing OpenRouter, NVIDIA, Qwen (and Google with its
  warning), each a single row with its sign-up link.
- Plan for limits: on a 429, the Assistant row should say it is resting and when to retry, and
  offer the offline model; provider lists change, so keep the provider list and model ids in data the
  app can update, not hard-coded.

## Open items before building

- Read Groq's current terms and privacy page for free-plan data use, and confirm no card at sign-up.
- Confirm the NVIDIA ToS and endpoint URL from a signed-in account, and test tool calling with
  `stream: true` on Groq and OpenRouter (the Qwen docs say it fails there).
- Verify the actual remote request code builds a `tools` array and accepts `tool_calls` in streamed
  deltas; I only read the ADRs.

## Sources (accessed 2026-11-28)

- Google AI Studio terms, unpaid services: https://aistudio.google.com/docs/terms
- Google rate limits (public page, no per-model numbers): https://aistudio.google.com/docs/rate-limits
- OpenRouter limits: https://openrouter.ai/docs/api-reference/limits
- OpenRouter free router: https://openrouter.ai/docs/guides/routing/routers/free-router
- Groq rate limits: https://console.groq.com/docs/rate-limits ; models: https://console.groq.com/docs/models
- Cerebras rate limits: https://inference-docs.cerebras.ai/support/rate-limits
- Alibaba Model Studio free quota: https://www.alibabacloud.com/help/en/model-studio/new-free-quota
- Alibaba OpenAI compatibility: https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope
- xAI models: https://docs.x.ai/docs/models ; billing: https://docs.x.ai/console/billing
- Mistral tiers: https://docs.mistral.ai/deployment/ai-studio/tier
- Cloudflare Workers AI pricing: https://developers.cloudflare.com/workers-ai/platform/pricing/
- GitHub Models retirement notice: https://docs.github.com/en/github-models/prototyping-with-ai-models
- NVIDIA catalog: https://build.nvidia.com/models ; forums (credits, 40 RPM, phone verification):
  https://forums.developer.nvidia.com/t/is-nim-free/369368 and the "manual phone verification" threads
- Third-party (unverified leads only): getaiperks.com, aitoolsrecap.com, apidog.com, neuraplus-ai.github.io
