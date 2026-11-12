# OSC 7501 Program Status Protocol: embedder checklist for libghostty-vt 1.0.0

Spec read: https://www.superlogical.com/rex/docs/build/program-status, fetched 2026-11-11. Revision history on the page ends at **0.4, dated 2026-10-10** (0.3 2026-10-07, 0.2 2026-10-06, 0.1 2026-09-28). The page has no linked sub-pages for the protocol; it is a single page. Cited below as [S: section].

Library: `~/.cache/termux-launcher-ghostty/src/libghostty-vt-1.0.0/` (LIB_VERSION 1.0.0). Cited as [L: file:symbol]. Tag `vt-1.0.0` of ghostty-org/ghostty resolves to commit 0fa3d577a (2026-11-06). I compared the unpacked copy byte-for-byte with the tag for `src/terminal/osc/parsers/program_status.zig`, `src/terminal/c/terminal.zig`, `src/terminal/stream_terminal.zig`, `include/ghostty/vt/terminal.h`, `src/terminal/osc/kitty_metadata.zig`: **identical, no difference**.

History of the parser file (gh api commits): bae2c3cdb 2026-10-06 (initial, rev 0.2-era), b7207b2ef 2026-10-10 "update to revision 0.4", dcaf6984e 2026-10-21 "reject OSC 7501 reports with malformed ids" (this is later than the "two revisions of 2026-10-10 and 2026-10-20" in the brief; the 10-20 commits are treewide refactors only). 1.0.0 contains all of them. Parser header says it implements the spec at the URL above.

Related background (not repeated): project-docs/reference/terminal/ghostty-first-party-parity-research-2026-11-11.md

## 0. How the library is shaped (read first)

- The library **parses and validates one report at a time and calls you**. It stores **no records**. Everything stateful (records per id, replacement, clear subtree, app inheritance, lifetime, 256 cap/LRU, staleness, presentation) is host work. [L: terminal.h GhosttyTerminalProgramStatusFn: "The terminal doesn't store reports, so your application keeps them."]
- One callback: `GHOSTTY_TERMINAL_OPT_PROGRAM_STATUS` (= 47), type `GhosttyTerminalProgramStatusFn(GhosttyTerminal, void* userdata, const GhosttyTerminalProgramStatus*)`. Called synchronously from inside the write/parse call. [terminal.h:2644, 1469]
- **While the callback is NULL: reports are dropped and the support query gets no reply.** [terminal.h opt doc; stream_terminal.zig programStatus]
- The standalone OSC parser API (`osc.h`) only reports `GHOSTTY_OSC_COMMAND_PROGRAM_STATUS = 28` and gives no contents; contents only come through the terminal callback. [osc.h:107]
- Memory: the report struct **and every string in it are valid only during the callback**; they point into stack buffers/parser buffer (title/msg are decoded into fixed stack buffers of 192 / 2048 bytes in the trampoline). Copy everything you keep. Strings are never NULL; absent text is `{ptr, len=0}`. [terminal.h struct doc; c/terminal.zig programStatusTrampoline]
- Struct layout is frozen: 80 bytes on 64-bit, 44 on wasm32; will never gain fields. [terminal.h]

C definitions (terminal.h 1260-1391):

```c
GHOSTTY_PROGRAM_STATUS_STATE_{IDLE=0,WORKING=1,DONE=2,BLOCKED=3,ERROR=4,CLEAR=5}
GHOSTTY_PROGRAM_STATUS_KIND_{NONE=0,PERMISSION=1,QUESTION=2,AUTH=3}
typedef struct {
  GhosttyProgramStatusState state;
  GhosttyProgramStatusKind  kind;     // NONE unless state==BLOCKED and a known kind was sent
  int8_t progress;                    // 0..100, or -1 = none/indeterminate
  GhosttyString id;                   // empty = root
  GhosttyString app;                  // empty = absent (NOT inherited for you)
  GhosttyString title;                // decoded UTF-8, no control chars
  GhosttyString message;              // decoded msg
} GhosttyTerminalProgramStatus;       // GhosttyString = { const uint8_t* ptr; size_t len; }
```

## 1 and 2. Wire format, per item, with library behaviour

### 1.1 Framing and syntax

| Item | Spec [S: Syntax/Conventions/Limits] | Library 1.0.0 |
|---|---|---|
| Sequence | `OSC 7501 ; pairs ST`; ST = ESC \ or BEL | Parsed for both terminators (`.st` / `.bel`). Only the `ESC ] 7501 ;` form is verified (see 5). |
| Pair grammar | `pair := key "=" value`, `:` separated, `key := [a-z]+`, `value := [A-Za-z0-9_.,+/=-]*`; no escaping needed | Split on `:`; keys are matched by exact name, so non `[a-z]+` keys are just unknown. Value charset is enforced **only when reading** a known key via `lastValue`: a pair whose value has a byte outside the set is skipped (so an earlier valid pair of the same key can win). |
| Whitespace | "Whitespace around keys and values is removed" | Trimmed with `std.ascii.whitespace` (space, \t, \n, \v, \f, \r) on both. |
| Malformed pair (no `=`, empty key, bad byte) | skipped, rest still processed | No `=` skipped; empty key never matches; bad-byte value skipped on read. Matches. Exception: limit checks use raw values (see 1.5). |
| Unknown keys | MUST be ignored | Ignored (except key length cap, 1.5). |
| Repeated key | last value wins | Last valid pair wins. |
| Missing/unknown `state` | whole report ignored | Whole report discarded (parser returns null, so no callback). Case sensitive. |
| Discard whole | if limit exceeded, base64 does not decode, or decoded text has a control char | Implemented in `validate()`. Nothing is delivered for a discarded report. |
| Query | `OSC 7501 ; ? ST` | Matched only when the whole body is exactly `?` (no whitespace tolerance). Delivered internally as `query`, never to the host. |
| Body size | whole sequence 4096 bytes, OSC through ST | Library caps the **body after `7501;`** at 4096-7-2 = **4087 bytes**, assuming the 2-byte ST. A BEL-terminated sequence is therefore limited one byte lower than the spec; allowed ("MAY choose lower limits"). |

### 1.2 `state` (required)

| Value | Spec meaning and lifetime | Library | Host work |
|---|---|---|---|
| idle | At rest, waiting for next instruction. Until replaced or cleared. Terminal MAY drop on shell prompt/exit. | `STATE_IDLE` | Store; optionally drop at prompt/exit. |
| working | Running; may carry progress. Until replaced, cleared, process exit, or next shell prompt (OSC 133 A): MUST drop on process exit and prompt. | `STATE_WORKING`; `progress` set | Store; MUST drop on process exit and on OSC 133 A. |
| done | Finished a piece of work, result ready, user hasn't seen it. Survives process exit and prompt. | `STATE_DONE` | Store; keep until user returns (e.g. focus/keypress). |
| blocked | Cannot continue until user acts; `kind` says what, `msg` why; may carry progress. Same lifetime as working. | `STATE_BLOCKED`; `kind`, `progress` set | Store; MUST drop on process exit and OSC 133 A. |
| error | Failed and stopped. Same lifetime as done. | `STATE_ERROR` | Store; keep until seen. |
| clear | Not a state. Removes the addressed record **and every record beneath it**; with no id removes **every** record. | `STATE_CLEAR` delivered through the same callback with `id` (empty = all). Not stored by the library. | Subtree delete by `/` prefix (segment boundary: `build` removes `build` and `build/...`, not `build2`). Empty id = delete all. |

Spec: "A report with no state, or one the terminal does not recognize, is ignored" and an unrecognized value discards the whole report so a later state never degrades to idle. Library matches.

Program-side advice in spec (no terminal duty): report `idle` when the user interrupts/cancels; a program that exits right after finishing SHOULD send `done`/`error` just before exiting.

### 1.3 `id` and records

| Item | Spec | Library | Host work |
|---|---|---|---|
| Grammar | `id := segment ("/" segment)*`, `segment := [A-Za-z0-9_.+-]{1,32}`; 128 bytes total, 8 levels | Validated on **every** `id` pair (raw, not charset-filtered): too long, depth>8, segment>32, empty segment, bad byte => whole report discarded. | none |
| Bad id | "report whose id does not match this grammar is ignored" (do not fall back to root) | Whole report discarded (commit dcaf6984e). Includes `id=` (empty value): discarded, not treated as root, and `state=clear:id=` does not clear everything. | none |
| No id | addresses root record | `id` string empty | Treat empty as root. |
| Replacement | each report replaces its record completely; keys missing from the report are missing afterwards | Not stored; each callback carries only that report's fields. | Replace the whole record; never merge fields from the old one. |
| Concurrency | root and children coexist (root idle while child blocked); records are independent | n/a | Keep a map keyed by id string. |
| Parent/child | `/` gives parent relationship; parent need not exist; MUST be used for clearing and app inheritance; MAY be used for grouping | n/a | Implement prefix-by-segment clearing and ancestor app lookup. |
| Screen | records belong to the terminal, not the screen; primary/alternate switch has no effect | Library has no hook; nothing is reset on alternate screen. | Do nothing on alt-screen switch. |
| Capacity | 256 per terminal; MUST support at least 64; when over cap, remove least recently **updated** | None | Implement LRU by update time (update, not read). |

### 1.4 Other keys

| Key | Spec | Library | Host work |
|---|---|---|---|
| `kind` | `permission`/`question`/`auth`; only with `blocked`, ignored otherwise; unrecognized = absent | `kind` enum with NONE; only filled when state==blocked and value is known | none beyond display |
| `progress` | integer 0..100, only with working/blocked, ignored otherwise; absent = indeterminate; anything else = absent | `int8_t progress`; -1 for absent/out of range/non-working,blocked/non-digits. Accepts leading zeros (`007`=7); rejects signs, decimals, >100. | Treat -1 as indeterminate (for working/blocked only). |
| `app` | `[A-Za-z0-9_.+-]{1,32}`; outside set = absent; record without app inherits nearest ancestor's app | Returned if charset valid, else empty. >32 bytes => whole report discarded (spec: limit breach discards). **No inheritance**: empty app is delivered as empty. | Implement ancestor inheritance at display time (the header documents this). |
| `title` | base64 UTF-8, 256 encoded / 192 decoded; label; root's label normally the window title | Decoded to `title`; limits enforced; empty value reads as empty string | Show as label. Spec says root title "normally the window title": host decides whether to wire it to the title. |
| `msg` | base64 UTF-8, 2732 encoded / 2048 decoded; one line; MAY shorten, MUST NOT read meaning into it | Decoded to `message`; limits enforced | Optionally shorten for display. |
| Base64 | standard alphabet, padding optional; decoded text MUST NOT contain U+0000-001F, 007F, 0080-009F | Standard decoder if the value ends with `=`, else no-pad decoder; decode failure => discard. Text checked by `isSafeUtf8`: must be valid UTF-8 and free of exactly those code points. Matches. URL-safe alphabet (`-`,`_`) fails to decode and discards. | none |

### 1.5 Limits (all discard the whole report)

| Limit | Spec | Library constant | Note |
|---|---|---|---|
| Key length | 16 bytes | `max_key_bytes=16` | Checked for every pair that has an `=`, even unknown keys. |
| `msg` | 2732 enc / 2048 dec | same | Encoded length checked on every occurrence of `msg`, even superseded ones; decoding only done on the last valid one. |
| `title` | 256 enc / 192 dec | same | same |
| `app` | 32 | 32 | |
| `id` | 128 total, 32/segment, 8 deep | same | |
| Whole sequence | 4096 | body <= 4087 | see 1.1 |
| Records | 256 (support >= 64) | not applicable | host |

Differences from the spec text to be aware of: (a) limit checks run on raw values, ignoring the value charset, so a pair that the spec would call "malformed, skip" can still discard the report if its value is over the limit. Spec says "Check every pair before touching any stored record", so this is the strict reading. (b) A superseded duplicate `msg`/`title` with bad base64 or control characters is not decoded, so it does not discard the report, while an over-length duplicate does.

### 1.6 Lifecycle events

| Event | Spec | Library | Host work |
|---|---|---|---|
| Process exit | MUST drop working/blocked; MAY drop idle; keep done/error | Not signalled | The host owns the pty child: drop at child exit. |
| New prompt (OSC 133 A) | same as exit | Library does not touch records. It does deliver `GHOSTTY_TERMINAL_OPT_SEMANTIC_PROMPT` with `GHOSTTY_SEMANTIC_PROMPT_PROMPT_START` (terminal.h:1412). | Set that callback; on PROMPT_START drop working/blocked. |
| Heartbeat/timeout/staleness | "There is no heartbeat, and terminals MUST NOT require re-sending to keep a record alive" | none | Do **not** invent timeouts for working/blocked. Only lifetime events above. |
| RIS (`ESC c`) | removes every record | Calls program_status callback with `STATE_CLEAR`, empty id, **then** the RESET callback. | Handle clear-all (needs no extra code if clear is handled). |
| DECSTR (`CSI ! p`) | does not remove records | No callback | Do nothing. |
| Alt screen | no effect | no callback | Do nothing. |

### 1.7 Support query and reply

- Spec: `program → OSC 7501 ; ? ST`; `terminal → OSC 7501 ; ? : states=idle,working,done,blocked,error : kinds=permission,question,auth ST`. A supporting terminal MUST reply with `?`; MAY add `states` (not counting clear) and `kinds`. Absent lists mean exactly the revision-0.4 sets. Reply carries nothing from any report. [S: Feature detection, Security]
- Library reply bytes (exact, no spaces): `ESC ] 7501 ; ? : states=idle,working,done,blocked,error : kinds=permission,question,auth <ST>` written as `\x1b]7501;?:states=idle,working,done,blocked,error:kinds=permission,question,auth` + terminator. The terminator is **the same one the query used** (ESC \ for ESC \, BEL for BEL). [L: osc/parsers/program_status.zig query_reply; stream_terminal.zig programStatus]
- Condition: sent only if the `program_status` callback is non-NULL **and** the host set `GHOSTTY_TERMINAL_OPT_WRITE_PTY` (otherwise `writePty` returns silently). Reply goes through write_pty.
- The program is advised to send `CSI c` (DA1) after the query to detect no-reply; the host must answer DA1 (library does through write_pty if configured; not checked here).

## 3. Presentation, notifications, security

Presentation [S: intro, In Rex, Relationship]
- The protocol "shares state and leaves presentation to the terminal", with no terms like notification, attention, focus. No MUST about sound, focus stealing, badges. Records show "current picture of every program". Rex examples: session picker indicators, spinner and symbols on unfocused tab headers, Lua event `terminal.program_status_changed`; planned push notifications and inbox (non-normative).
- A terminal MAY produce a notification when a record changes. Notifications are not required. [S: Relationship, OSC 9/99/777]
- "done"/"error" are kept until the user sees them; spec suggests "when the user returns to the terminal and presses a key" (example only). Which of working/blocked/done to show when several records exist is left to the terminal ("the terminal decides which to show").
- OSC 9;4: terminal MAY map it to the root record; SHOULD stop mapping once any OSC 7501 report was received, until the next full reset (because a mapped 9;4 would wipe kind/msg). Library keeps OSC 9;4 in a separate callback (`GHOSTTY_TERMINAL_OPT_PROGRESS_REPORT`), no mapping. If you map, you implement the "stop after first 7501" latch and reset it on RIS.

Security [S: Security, Limits]
- Everything is untrusted. MUST refuse reports whose decoded msg/title contain a control char (library: done). MUST NOT treat msg/title as markup (host: render as plain text; on Android, no `Html.fromHtml`, no span parsing).
- SHOULD disarm text-direction overrides and other invisible formatting characters when shown **outside the terminal grid** (library does **not**; C1 and C0 are excluded but U+202A-202E, U+2066-2069, U+200B-200F, U+FEFF etc. pass through). Host work.
- Anything shown from a record SHOULD say which terminal it came from (host: label with the session name/id in tabs and notifications).
- Only bytes written back are the query reply; ids/titles/messages never echoed; no read API. (Library: honours; reply is a constant.)
- SHOULD rate-limit anything a record causes outside the terminal (notifications, sounds). Library has no rate limiter; callback fires on every valid report. Host work.
- Hard caps (limits above) exist to bound memory and work; library enforces per-report caps, the host must enforce the 256-record cap.

## 4. Checklist table

Legend: Lib = library support (Yes = parsed and delivered; Host = nothing in the library). Spec sections: Syntax, Records, States, Keys, Feature, Security, Limits, Rel = Relationship.

| # | Requirement | Spec section / quote | Library | Host work |
|---|---|---|---|---|
| 1 | Install program_status callback | Feature: "A supporting terminal MUST reply with ?" | Reply only when callback set | `ghostty_terminal_set(..., OPT_PROGRAM_STATUS, fn)` |
| 2 | Install write_pty so reply is sent | Feature | Reply goes through write_pty, silently nothing if unset | Set `OPT_WRITE_PTY` |
| 3 | Reply to `?` with `?` (+lists) | Feature: "MUST reply with ?" | Yes, fixed bytes, same terminator | none |
| 4 | Reply lists exactly rev-0.4 states and kinds | Feature | Yes, lists generated from enums, `clear` excluded | none |
| 5 | Never echo record contents | Security: "Ids, titles, and messages are never sent back" | Yes | none |
| 6 | Parse `key=value` pairs separated by `:` | Syntax | Yes | none |
| 7 | Trim whitespace around keys and values | Syntax | Yes | none |
| 8 | Skip malformed pair, keep rest | Syntax | Yes (with the raw-limit caveat 1.5a) | none |
| 9 | Ignore unknown keys | Syntax: "MUST be ignored" | Yes | none |
| 10 | Last value wins for repeated key | Syntax | Yes | none |
| 11 | Ignore report with no/unknown state | States | Yes (discard) | none |
| 12 | Discard whole report on any limit/decode/control-char failure | Syntax, Limits | Yes | none |
| 13 | Refuse msg/title with control chars | Security: "MUST refuse" | Yes (C0, DEL, C1) | none |
| 14 | Base64 standard, padding optional | Syntax | Yes | none |
| 15 | Limits: seq 4096, key 16, msg 2732/2048, title 256/192, app 32, id 128/32/8 | Limits | Yes (seq body 4087; stricter for BEL) | none |
| 16 | Ignore report with invalid id (no root fallback) | Records | Yes (discard) | none |
| 17 | One record per id, replace completely | Records: "Each report replaces its record completely" | Delivers one report, no store | Keep map id->record; replace whole |
| 18 | Root record for empty id; root and children coexist | Records | `id` empty string | Map key "" = root |
| 19 | `clear` with id removes record and all descendants | States | Delivered as STATE_CLEAR + id | Segment-prefix delete |
| 20 | `clear` without id removes all records | States | Delivered, empty id | Delete all |
| 21 | App inheritance from nearest ancestor | Keys: "takes it from its nearest ancestor that has one" (MUST per Records) | Not done; empty app delivered | Resolve at display time |
| 22 | `kind` only for blocked, unknown = absent | Keys | Yes (NONE) | none |
| 23 | `progress` 0-100 only for working/blocked, else indeterminate | Keys | Yes (-1) | Render indeterminate for -1 on working/blocked |
| 24 | Do not interpret msg meaning; MAY shorten | Keys | Delivered as decoded text | Truncate for UI only |
| 25 | Records independent of screen; alt screen no effect | Records | no hook | Don't clear on alt switch |
| 26 | RIS removes all records | Records | Yes: clear callback then reset callback | Handle clear-all |
| 27 | DECSTR keeps records | Records | Yes: no callback | none |
| 28 | Drop working/blocked on child exit | States: Lifetime "MUST drop" | No | Hook child exit |
| 29 | Drop working/blocked on OSC 133 A | States: Lifetime "MUST drop" | Delivers PROMPT_START via SEMANTIC_PROMPT callback | Set callback; drop |
| 30 | MAY drop idle at exit/prompt | States | No | optional |
| 31 | Keep done/error across exit and prompt | States | No | Don't drop; clear when user has seen |
| 32 | No heartbeat required; no timeout | States: "MUST NOT require re-sending" | n/a | No TTL on records |
| 33 | Support >= 64 records, cap 256, evict least recently updated | Limits: "MUST support at least 64" | No | LRU in store |
| 34 | Not render msg/title as markup | Security: MUST NOT | Plain bytes | Plain-text widgets only |
| 35 | Disarm bidi overrides and invisible formatting outside grid | Security: SHOULD | No | Strip U+200B-200F, 202A-202E, 2060-2069, FEFF etc. |
| 36 | Say which terminal a status came from | Security: SHOULD | No | Label with session in UI/notifications |
| 37 | Rate-limit external effects (notifications, sound) | Security: SHOULD | No | Throttle per terminal |
| 38 | OSC 9;4 mapping (optional) stops after first 7501 until RIS | Rel: SHOULD | No mapping | Only if you map |
| 39 | Copy strings before callback returns | n/a (library contract) | Strings valid only during callback | Copy id/app/title/message |
| 40 | Callback runs inside the VT write call (synchronous) | n/a (library contract) | Yes | Don't block; marshal to UI thread |
| 41 | Optional notification on record change | Rel: MAY | none | Decide; throttle |

**Count: 41 rows** (spec-derived MUST/SHOULD/MAY items: rows 1-38 less rows 25, 30 which are informational/optional; rows 39-41 are library or optional contracts). By keyword in the spec: MUST 11 (reply `?`; unknown keys ignored; refuse control chars; not treat as markup; drop working/blocked on exit and prompt; no heartbeat requirement; support >=64 records; use `/` for clearing and app inheritance; programs MUST ignore unknown reply pairs [program side]), SHOULD 5 (disarm invisible chars; say which terminal; rate-limit; stop OSC 9;4 mapping; program SHOULD send done/error before exit [program side]), MAY 7.

## 5. Unverified / ambiguous

1. **Terminator of the reply**: the spec does not say the reply must use the query's terminator; the library echoes it (ESC \ or BEL). Spec examples write `ST` generically.
2. **Spaces in the reply**: spec example shows `? : states=... : kinds=... ST` with spaces around `:`; the library emits none, which matches the grammar (whitespace is not meant to be required).
3. **C1 forms** (0x9D OSC / 0x9C ST), and whether a 7501 sequence without `;` or with a body so large the generic OSC capture limit is hit behaves per spec, were not read in the library; only the 7501 state in `osc.zig` (`7501` then `;`, else unknown/invalid) was checked.
4. **"Whole sequence 4096 bytes" vs lib body cap 4087**: fine under "MAY choose lower limits"; BEL-terminated reports of 4088-4089 body bytes that the spec allows are dropped.
5. **Report with a malformed-byte value over its limit**: spec says skip malformed pair, but also discard on limit breach; ambiguous which wins; the library discards.
6. **Duplicate keys and limits**: spec does not say whether a superseded duplicate counts toward limit/decoding checks. Library: length yes, decoding no (1.5b).
7. **Empty values** (`msg=`, `title=`, `app=`, `progress=`, `kind=`): spec grammar allows `*` (zero) chars; library treats as absent. `id=` is discarded, `state=` discarded. Spec does not describe empty values.
8. **`title` of the root record** "is normally the window title": non-normative; unclear whether a terminal must wire it to OSC 0/2 handling.
9. **Which record to show when several exist** ("the terminal decides"): no priority order specified; blocked vs working vs done precedence is host policy.
10. **When "the user has seen" done/error**: only an example (returning and pressing a key). No normative rule, no staleness bound for done/error. A done record with no viewer lives forever until replaced, cleared, RIS, or LRU-evicted.
11. **Process exit** for an embedder: the spec says "process attached to the terminal exits"; with a pty shell that is the shell, not the foreground program. Ambiguous whether a foreground program exit (shell still alive) counts; OSC 133 A covers it only with shell integration.
12. **LRU "updated least recently"**: whether `clear` of a subtree or a no-op replacement counts as update is not defined.
13. **Revision of the parser**: the parser comments claim conformance but name no revision number; commit b7207b2ef says 0.4. The id-rejection change (dcaf6984e, 2026-10-21) postdates 0.4 (2026-10-10) and the spec page I read already says "ignored" for bad ids, so I treat it as in line with 0.4; I did not check whether the page changed after 10-10 without a revision bump.
14. Not verified at runtime: I did not build or run the library; behaviours above come from reading source and its tests.
