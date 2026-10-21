# A minimal terminal-aware character prior for key disambiguation

Status: research note, 2026-10-16. Read-only study; no code changed. Written at the developer's
request. Scope is narrow on purpose: a character-level prior whose only output is a per-key bias
applied at hit-test time. No correction after commit, no completion, no suggestion UI, no
prediction strip. It complements `TapModel`'s per-user touch offsets
([`adaptive-touch-targets.md`](adaptive-touch-targets.md)).

Verification legend, as in the sibling note. **[V]** read in a primary source or in this repo's
code during this study. **[A]** abstract or record only. **[U]** unverified. **[M]** measured here in
a small offline simulation (section 2.4; the sim is a sanity check, not evidence about real
thumbs). Repo line numbers are from the working tree on `pre` at `efaaace61` and drift.

## Summary

- The idea is well founded. Goodman et al. (2002) combined a per-key Gaussian touch model with a
  character n-gram prior and cut soft-keyboard error rates by a factor of 1.67 to 1.87 in an
  8-person study; Gunawardana et al. (2010) did the same as hit-region resizing with a Kneser-Ney
  key 8-gram trained on 8.5M characters. **[V]**
- The documented failure is exactly the one the developer fears. Unanchored resizing was **10%
  worse than static targets** on two of four test sets when the prior's training text did not match
  what was typed; adding a central anchor that always belongs to its own key fixed that without
  losing most of the gain (17%, 6%, 8%, 13% better than static on four sets). **[V]**
- Shell input is unusually predictable at the character level and unusually unlike prose. On a
  held-out slice of tldr-pages command lines, a pruned 4-gram reaches 2.5 bits per character
  versus 4.8 for a unigram. But the developer's own fish history scores about 5 bits per character
  against the same model (255 commands, noisy), so a corpus-only prior will be weak on exactly the
  commands this user types; a small personal layer is what closes that gap. **[M]**
- Order 4 (three previous characters) is the sweet spot: in the sim it roughly halves the residual
  errors that a trigram leaves, and order 5 buys little in bits per character (2.37 versus 2.50)
  for twice the entries. A pruned 4-gram fits in about 220 KB. **[M]**
- The anchor is a mitigation for a deliberate centre tap, not for a noisy border tap. In the sim,
  random text (a password-like stream) is made **worse** by any prior, anchored or not, because
  the damage lands on border taps that the anchor does not protect. The real safety is the gates
  (off when the text is not language) plus a small bias cap, not the anchor alone. **[M]**
- The context is cheaper than it looks. The keyboard already knows every character it sent. A
  "typed since the last line reset" buffer needs no screen read, no shell integration and no
  `$HISTFILE`. The terminal can confirm the line is the shell's and not a program's: OSC 133 is
  parsed, and the foreground-process resolver names `nvim`, `ssh` and the like. **[V]**
- Gaps found in the repo: the shipped bash and zsh integration scripts emit OSC 133 `A`, `C` and
  `D` but **never `B`** (end of prompt, start of the typed line), so the screen cannot yet say where
  the typed text begins; and nothing reads the pty's echo flag, so a password prompt cannot be
  detected from termios today. **[V]**
- One correction to the brief: a tap on the q/w border cannot become `t`; `t` is four keys away. A
  prior only arbitrates between keys whose hit regions the tap actually touches, so after `gi` the
  case it fixes is a tap on the r/t border. Anything farther is refused on purpose (section 6).
- Recommended: ship it in **shadow mode first** (compute, count, never act), then on behind the
  existing "Learn where you tap" setting. Model: pruned 4-gram, about 54K entries, 4 bytes each,
  about 220 KB resident, plus a bounded personal layer of at most 64 KB and a command-name trie of
  about 20-40 KB. Under 0.2% of the 185-200 MB idle PSS. Roughly 8-9 days including the offline
  harness.

## 1. Combining a spatial model with a character prior

### 1.1 The formulation

For a tap at position `t` and typed history `h`, choose the key `k` maximising
`P(t | k) * P(k | h)^w`, where `P(t | k)` is the touch model (per-key bivariate Gaussian) and
`P(k | h)` the character language model.

- Goodman, Venolia, Steury, Parker, "Language modeling for soft keyboards", IUI 2002, pp. 194-195
  (short paper; extended report MSR-TR-2001-118). They decode the most likely *letter sequence*
  given the observed pen-down positions: `argmax P(letters) * P(positions | letters)`, with the
  letter model approximated from the previous 6 letters and smoothed, and the position model a
  bivariate Gaussian per key with its own mean, variances and covariance. Three touch-model
  refinements helped: key means shifted from centre (down and toward the keyboard centre),
  different x and y variances, and rotated distributions. Modelling correlation with the previous
  tap, the pen-up position, or typing speed did *not* help. **[V]**
  - Result: 8 participants, 1,000 net characters per condition; error-rate ratios of 1.87
    (p<0.014) and 1.87 (p<0.028) for the two full sets, 1.72 pooled (p<0.005), and "as low as
    1.67" by other error measures; throughput was marginally faster but not significantly. **[V]**
  - Their model is a whole-sequence decode: a later tap can change an earlier letter. We cannot do
    that (bytes are already sent), so we only get the causal half: the prior sees the committed
    history and decides one tap at a time. Expect less than their ratio. **[V]** for what they
    did; the "less" is a judgement.
- Gunawardana, Paek, Meek, "Usability guided key-target resizing for soft keyboards", IUI 2010
  (read in the sibling note and re-read here). Target `T_k(h) = { l : p_L(k|h) p_T(l|k) > ... }`,
  i.e. the same product, applied as resized invisible hit regions. The language model is a
  Kneser-Ney smoothed interpolated key **8-gram** trained on 8.5M characters of USENET text, with
  the probabilities raised to an optimised exponent `w` to balance the dynamic ranges of prior and
  touch model; smoothness (`p_L(k|h) > 0` for every `k`) is required so no key is ever ruled out.
  **[V]**
  - Anchor: each key has a central anchor `A_k` always inside its target (Proposition 1:
    `A_k` is within `T_k(h)` for any smooth model and history). The best anchor was small: 6 x 60
    px against 46 x 100 px keys, an anchor *area* of 360 against 4,700 for the whole static key.
    **[V]**
  - Gains over static targets, average keystroke error: 17%, 6%, 8%, 13% relative on four test
    sets (standard, emails, queries, URLs), all p<.01. The *unanchored* resizer was about 10% worse
    than static on two sets, which the authors attribute to a mismatch between the test text and
    the USENET training corpus. Error analysis: anchored resizing fixed 22% of the cases the
    unanchored one broke, kept 96% of its wins, made no new errors. **[V]**
  - The URLs set gained least; the authors say the training corpus already contained URLs. The
    lesson for us is the mirror image: the prior is only as good as its match to the text.
- Apple US 8,232,973 B2 (read in the sibling note): a hidden hit region added around each key,
  scaled by the key's next-letter probability from the typed prefix, **never smaller than the
  visible key**. Apple's shipped behaviour beyond the patent is **[U]**.
- Sivek & Riley, Gboard (2022): personalisation is applied inside the decoder's spatial score and
  trained only on touches from committed words that matched the final text. The language side is
  the decoder; it is not separable. **[V]** (sibling note).

### 1.2 Reported gains and what they mean here

The headline numbers are all measured with a **dictionary-grade** prior on **prose-like** text
(1.67-1.87x; 6-17%). Two things shrink them for us: no whole-sequence decode, and a smaller model.
One thing could grow them: shell text is far more predictable than prose at the character level
(section 2.4). I know of no published measurement of this technique on shell input. **[U]**

### 1.3 Failure modes

1. **Prior overrides a deliberate tap.** The user types something the model finds unlikely (a
   password, a new tool name, a hex string). Unanchored resizing in Gunawardana made things worse
   than doing nothing on mismatched text. **[V]**
2. **Self-reinforcement.** The prior steers taps to the likely key, and a learning touch model that
   trains on the *resolved* key would learn the steering. `TapModel` already records against the
   raw grid key, never the resolved one, which is the right rule and must stay. **[V]** (repo,
   `TapModel.observe` javadoc).
3. **Exposure bias in the history.** The prior conditions on the characters *as committed*. If an
   earlier tap was wrong, the context is wrong and the next resolution is biased the wrong way.
   Whole-sequence decoding (Goodman) repairs this; we cannot. A weaker bias for shorter confidence
   is the only lever. **[U]** how often this bites in practice.
4. **Domain mismatch**, above.

### 1.4 Mitigations in the literature

- Smooth model, so nothing is ever impossible. **[V]** (Gunawardana).
- Anchors. **[V]**
- Exponent `w` below 1 to flatten the prior against the touch model. **[V]** (both).
- Never shrink below the visible key (Apple). **[V]** via patent text in the sibling note.
- Posture or user touch models make the spatial side sharper so the prior has to do less. **[V]**
  (Yin 2013, sibling note).

## 2. Small character n-gram models

### 2.1 What the literature gives

- Goodman used 6 characters of history; Gunawardana used 8 (order 8 key grams, 8.5M training
  characters, Kneser-Ney). Both are large for a phone. **[V]** Neither paper reports a size,
  order, or accuracy sweep, so the trade-off here has to be measured on our own corpus.
- Smoothing: Chen & Goodman, "An empirical study of smoothing techniques for language modeling"
  (ACL 1996; CSL 1999; TR-10-98) found modified Kneser-Ney (interpolated, three discounts for
  counts 1, 2, 3+) consistently best among the methods they compared. **[A]** (record and
  secondary description; full text not read). For a pruned, quantised table the cheap choice is
  interpolated absolute discounting or Witten-Bell; the difference matters less than the corpus
  match and the pruning. **[U]** for the claim that the difference is small on this task; the sim
  below uses Witten-Bell.

### 2.2 Memory footprint

An n-gram table stores one entry per distinct (context, next-char) pair. Counted on the corpus in
2.4 (order-N model, all lower orders included):

| Order | Distinct entries, no pruning | min count 2 | min count 3 | min count 5 |
|---|---|---|---|---|
| 3 (two chars of history) | 33,375 | 22,641 | 18,465 | 14,472 |
| 4 (three chars of history) | 112,607 | 69,303 | 54,238 | 39,679 |
| 5 | 255,273 | not run | not run | not run |

Packed as a 4-byte entry (16-bit context hash, 8-bit next-char index, 8-bit quantised
log-probability) in an open-addressed array: order 3 at min count 3 is about **74 KB**, order 4 at
min count 3 is about **217 KB**. Hash collisions on a 16-bit tag cost a small false-hit rate;
because the output is only a bounded bias, a false hit is bounded harm. **[M]** for the counts; the
byte sizes are arithmetic, not a built artefact.

### 2.3 Cost per tap

One tap needs `P(k | h)` for the raw key and its at most 4-6 character neighbours: that is at most
about 8 hash lookups per backoff level, so under 40 lookups, a few microseconds. It runs once at
touch-down, on the main thread, no allocation. It is not a per-frame cost, which fits the
repo's "no layout work per frame" rule. **[U]** until timed on pong; the arithmetic is what
supports it.

### 2.4 A small experiment

Done offline in the scratchpad (not committed; Python, Witten-Bell interpolated character n-gram).

- Corpus: the command lines (backticked examples) of `tldr-pages` `common`, `linux` and `android`,
  `{{placeholders}}` replaced by their inner text. 6,814 files split 90/10 by *file*: 27,279
  training commands (984,475 characters) and 3,055 test commands (110,986 characters). tldr is
  CC BY 4.0 (the repo's `LICENSE.md` names the licence; the full text was not read in full) and
  the corpus is English-description plus idiomatic examples, so it is friendlier than real use.
- Bits per character on the held-out tldr commands: unigram 4.82, bigram 3.94, trigram 3.05,
  4-gram 2.50, 5-gram 2.37. **[M]**
- Out of domain: the developer's own fish history (255 commands, 9,061 characters; read for
  aggregate numbers only, nothing stored): 5.14 (2-gram), 5.01 (3-gram), 5.05 (4-gram) bits per
  character. The model is *worse than its own unigram* on this history, so command idiom in tldr is
  a poor guide to what this user types (long paths, tool names, URLs). Higher orders stop
  helping. **[M]**
- Tap simulation: a QWERTY letter grid (key pitch 1 wide, rows 1.4 tall, row offsets 0 / 0.25 /
  0.75), the intended key's centre plus Gaussian noise (sigma in key widths, 1.4x on y), static =
  nearest key, prior = Gaussian log-likelihood + `w` x ln P(k | true history). Letters only, true
  history (oracle context). Candidates limited to keys within 1.2 key widths of the touch.
  Error rate is per letter tap.

| Test text | sigma | static error | prior, order, w | error with prior | fixed / hurt (taps) |
|---|---|---|---|---|---|
| tldr commands | 0.20 | 1.73% | 3, 0.6 | 0.91% | 772 / 82 |
| tldr commands | 0.20 | 1.73% | 4, 0.6 | 0.67% | 978 / 92 |
| tldr commands | 0.25 | 6.46% | 3, 0.6 | 3.30% | 2,929 / 288 |
| tldr commands | 0.25 | 6.46% | 4, 0.6 | 2.58% | 3,596 / 346 |
| tldr commands | 0.30 | 13.5% | 2, 1.0 | 8.70% | 5,327 / 1,308 |
| tldr commands | 0.30 | 13.5% | 3, 1.0 | 6.13% | 7,314 / 1,140 |
| tldr commands | 0.30 | 13.5% | 4, 1.0 | 5.01% | 8,410 / 1,296 |
| developer's fish history (prior trained on tldr) | 0.30 | 14.19% | 4, 0.6 | 7.95% | 460 / 106 |
| tldr description prose, lowercased | 0.25 | 6.65% | 4, 0.6 | 3.01% | 5,398 / 585 |
| **random 12-letter strings** | 0.25 | 6.77% | 4, 0.3 | **8.01%** | 156 / 380 |
| **random 12-letter strings** | 0.25 | 6.77% | 4, 0.6 | **11.24%** | 245 / 1,050 |

"Hurt" means the static grid was right and the prior changed it to wrong; all taps in this sim are
intended, so every "hurt" comes from a noisy tap, not a deliberate one.

Anchors (inner fraction of each key, per axis, that always keeps its own key), order 4, sigma 0.25:

| Text | w | no anchor | anchor 0.5 | anchor 0.7 |
|---|---|---|---|---|
| tldr commands | 0.6 | 2.58% | 2.63% | 2.94% |
| random strings | 0.6 | 11.24% | 10.52% | 9.42% |
| random strings | 0.3 | 8.01% | 7.99% | 7.87% |

What this supports:

- **Order**: 3 to 4 is the knee. At sigma 0.25, w 0.6, going from order 3 to 4 takes the error
  from 3.30% to 2.58% (22% fewer remaining errors) for 2.9x the entries; order 5 is not worth it
  (2.37 versus 2.50 bits per character for 2.3x the entries). Order 2 is too weak (8.7% versus 6.1%
  at sigma 0.30, w 1.0).
- **Weight**: `w` 0.3 already captures about half the gain at about half the damage. A cap on the
  bias (section 6) is worth more than tuning `w`.
- **Static error rate sets the ceiling.** At sigma 0.20 (1.7% static error) the whole prize is
  about one point. Per-user touch offsets and the prior stack, but a user who already types
  cleanly gains little. Measure the real tap error rate on pong first (section 8).
- **Mismatch hurts less than feared but helps less**: on the developer's own history the gain
  survives (14.2% to 8.0%) with tldr as the only training text, but it is a 255-command sample
  with an oracle history. **[M]**, treat as unconfirmed on real input.
- **Random text is the killer**, and the anchor does not save it. Gate it off.

Not modelled: deliberate taps, Backspace-retype, burst timing, digits and punctuation, uppercase,
real thumb noise (not Gaussian, posture dependent), whether the history is right (oracle used).
Every one of those makes the real number worse than the table.

## 3. Terminal and shell input specifics

### 3.1 Corpora

| Source | What it is | Use here | Notes |
|---|---|---|---|
| tldr-pages (github.com/tldr-pages/tldr) | ~6.8K pages in `common`, `linux`, `android` and others; each page has short example command lines with `{{placeholders}}` | Base prior (used in 2.4) | CC BY 4.0 per its `LICENSE.md` **[V]**; attribution needed in the app's licences screen. Includes an `android` folder, which suits this app |
| NL2Bash (Lin et al., LREC 2018, github.com/TellinaTool/nl2bash) | 9,305 English-to-Bash pairs after filtering, 102 utilities, 206 flags, `find` alone 6,268 times; 12,609 collected before cleaning | Cross-check; too skewed toward `find` and `xargs` to be the base | **[V]** (paper text). Distribution is long-tailed: the 52 rarest utilities have 984 uses in total. Licence not confirmed **[U]** |
| Completion specs (bash-completion, zsh `_` functions, fish `completions/`) | Per-command option lists | Flag vocabulary (`--long` names) per command | Text-only extraction possible; licences differ per project **[U]** |
| `compgen` | Bash builtin that lists matches: `-A command` / `-c` command names, `-A builtin` / `-b`, `-A function`, `-A file` / `-f` | Cheap way to read the *installed* command and builtin names on this very device | **[V]** (bash manual, Programmable Completion Builtins) |
| `$PREFIX/bin` | The directory itself | Command-name trie, no shell needed | Public information, a directory listing; see section 4.3 |
| Man pages | Option names in SYNOPSIS | Heavy to parse; skip | **[U]** |
| `$HISTFILE` | The user's own commands | Personal layer, opt-in only | Privacy, section 4.3 |

### 3.2 Token classes that differ from prose

These shift character statistics a lot, so a model that does not know which class it is inside
will mispredict. (Class list built from fish's highlighting vocabulary and zsh's highlighters, not
from a spec.)

- Command word at line start, or after `|`, `&&`, `||`, `;`, `$(`: the most predictable class,
  drawn from a closed set of installed names.
- Flags: `-x` is a letter or digit after `-`; `--long-name` is hyphenated lowercase words. The
  first letter after `-` is near-uniform over a small set per command; after `--` it is word-like.
- Paths: `/`, `.`, `..`, `~`, `/data/data/...`, mixed case, digits, underscores: closer to random
  than to prose, and the biggest source of prior-versus-intent conflicts. The prior should weaken
  or turn off inside a path token. **[U]** for how much, no measurement.
- Quoted strings: free text in an unknown language (commit messages, grep patterns). English
  prose model or nothing; weaken.
- `$VAR` and `${VAR}`: upper snake case after `$`.
- Pipes, redirects, `&`, `;`, glob `*` `?` `[`: punctuation, on the symbol layer; outside v1.
- Heredocs and `--` separators: ignore.

How existing tools classify a line cheaply:

- fish colours these classes: command (with builtins, functions and keywords as sub-classes),
  param, option (words starting with `-`), valid-path, redirection, quote, operator (`*`, `~`),
  escape, end (`;`, `&`), error, comment. Its autosuggestions come from history, completions and
  valid paths, shown after the cursor and never run unless accepted. **[V]** (fish docs,
  Interactive use). We take only the tokeniser idea, none of the suggestion.
- zsh-syntax-highlighting ships separate highlighters (`main`, `brackets`, `pattern`, `cursor`,
  `root`, `line`; `main` is the only one on by default). **[V]** (its `highlighters.md`); the token
  list inside `main` was not read.
- tree-sitter-bash is a full Bash grammar (MIT) with bindings for Rust, npm, PyPI, Go and Swift.
  **[V]** (README). Far too heavy for a per-tap lookup; and no Java binding was found **[U]**.
- bash readline completion decides the completion kind (command / file / variable) by a few
  character rules, not a parse. **[U]** (not read this session); the same cheap rules are enough
  here.

A four-state scanner is enough: `cmd-position`, `arg`, `flag` (since a `-`), `quote` (inside an
unclosed `'` or `"`). It needs no grammar, runs on the keyboard's own typed buffer in O(1) per
character, and its state can be hashed into the n-gram context. Whether the state is worth its
table cost is **[U]**; test it in the harness before adopting it.

## 4. Context available without a text buffer

### 4.1 What the keyboard knows by itself

Every character the in-app keyboard sends passes through `TerminalKeyEventHandler` and
`TerminalModifiers` (`app/.../terminal/inappkeyboard/`). A typed buffer, "characters I sent since
the last reset", is therefore free, and independent of the shell. Resets: Enter, Ctrl-C/D/U/L/Z,
Escape, arrows, Tab (the shell rewrote the line), Home/End, any modifier chord, paste, voice
dictation, a hardware key, a pane switch, the keyboard hiding. After a reset the prior starts at
`BOS`, exactly as the sim's `\x02` start. **[V]** for the hook points existing; the list of resets
is a proposal.

Drift is the risk: a line recalled with Up arrow, completed with Tab, or edited with a hardware
key makes the buffer wrong. Resetting on all of those makes it conservative: a wrong buffer only
ever weakens the prior (it falls back to short contexts), it cannot create a long wrong context.

### 4.2 What the terminal emulator knows

- `terminal-emulator/.../TerminalEmulator.java` parses OSC 133 (`case 133`, line ~3958, handler
  `doShellIntegration`, lines ~3761-3820). It keeps `A` (prompt start), `B` (command start), `C`
  (output start) as row marks (`TerminalRow.MARK_PROMPT_START`, `MARK_COMMAND_START`,
  `MARK_OUTPUT_START`), `D` plus the exit code, a "seen any mark" flag
  (`mShellIntegrationSeen`) and a "command running" flag (`isShellIntegrationCommandRunning()`).
  **[V]**
- **The shipped scripts never emit `B`.** `app/src/main/assets/shell-integration/termux-launcher.bash`
  sends `D;<status>`, an OSC 7 cwd report and `A` from `PROMPT_COMMAND`, and `C` from `PS0`; there
  are zero `133;B` in either the bash or the zsh script (`grep -c` returns 0). The marks that
  exist say where the prompt starts and where output starts, not where the typed text starts.
  **[V]** The spec defines `B` as "the start of a commandline (READ: the end of the prompt)"
  and the reference bash recipe appends it to `PS1`; Windows Terminal's own guide warns that
  pre-existing `PS0`/`PS1`/`PS2`/`PROMPT_COMMAND` values "may lead to unpredictable results".
  **[V]** (Microsoft Learn, Shell integration in the Windows Terminal; kitty's page says its zsh
  integration leaves `B` commented out because kitty does not use it). So `B` is cheap to add in
  principle but touches the user's prompt string; do not add it for this feature unless a second
  reason appears. The typed buffer in 4.1 makes it unnecessary.
- Cursor and screen: `getCursorRow()` / `getCursorCol()` (lines 1245-1249) and
  `TerminalBuffer.getSelectedText(...)` (line 94) give the line the cursor is on. Without `B` the
  prompt width is unknown, so the typed text cannot be split from the prompt by position alone.
  **[V]** Use screen text only as a *check* on the typed buffer (does the cursor row end with the
  characters I think I sent?), never as the source.
- Alternate screen: `isAlternateBufferActive()` (line 952) is true in vim, less, htop and most
  TUIs (DECSET 47, 1047 and 1049 select the alternate buffer, per xterm's control sequences
  reference). **[V]** Also `isMouseTrackingActive()` (1367), `isKeypadApplicationMode()` (1356),
  `isCursorKeysApplicationMode()` (1360), `isBracketedPasteMode()` (1282; shells that use
  readline set DECSET 2004, programs usually do not, so it is a weak shell-at-prompt signal;
  **[U]** for zsh without ZLE edge cases).
- Foreground process: `WindowForegroundResolver.ForegroundInfo` has `idle` (the shell itself is
  foreground), `processName` (e.g. `nvim`), `command` (argv from procfs). **[V]** It refreshes at
  most every 1.5 s (`MIN_INTERVAL_MS = 1500L`), so it is a slow hint and can be stale by a command
  start; use it to *disable*, never to enable on its own.
- Echo flag: `terminal-emulator/src/main/jni/termux.c` calls `tcgetattr` only in
  `setPtyUTF8Mode` (line ~193); nothing reads `ECHO` or `ICANON`. Reading them would need a new JNI
  call on the pty master fd. Whether `tcgetattr` on a master returns the slave's flags on Android's
  kernel is **[U]**: `pty(7)` was read and does not say. Check on a device before relying on it.
  A shell at a password prompt (sudo, ssh, passwd, gpg) turns ECHO off, but so do `read -s` and any
  TUI in raw mode, which is fine here: both mean "not shell language".

### 4.3 Personal dictionary, and what it costs in privacy

- **Command names from `$PREFIX/bin`**: a directory listing, names only, no arguments, nothing the
  user typed. A trie over ~1-3K names is roughly 20-40 KB. It is the strongest cheap signal for the
  command position: after `gi` at line start, the trie says which next letters complete an
  installed name (`git`, `gifsicle`, ...), so a tap on the r/t border resolves to `t`. Cache by
  directory mtime so a package install refreshes it. Aliases and shell functions are invisible to it; `compgen -A alias`
  could fetch them but needs a shell round trip **[U]**.
- **`$HISTFILE`**: tempting and I recommend against reading it. Shell histories contain pasted
  tokens, `export KEY=...`, `curl -u user:pass`, and commands typed by other tools. The file is in
  the user's home and readable by the app, so it is technically possible. A personal layer built
  from what the keyboard saw (4.1), gated by section 5 (no passwords, no non-language), has the
  same benefit with a clearer boundary. If the developer wants a history seed anyway: opt-in,
  on-device parse once, store only hashed n-gram counts, drop any line containing `=` followed by
  a long token, `://`, `Authorization`, `-p`/`--password`, and never write the lines.
- **Storage**: counts live in the app's private files directory (next to
  `keyboard-tap-model.json`), in the existing backup policy; **verify the manifest's
  `allowBackup` / data-extraction rules** before shipping, since a prior file is a typing profile
  **[U]**. Hashing n-gram contexts is not secrecy (short n-grams are guessable); say so, and offer
  the same reset the tap model has ("Forget learned taps").
- **Nothing leaves the device.** No network path is involved; the model is an asset plus a local
  file. Gboard's published design likewise keeps touch data on device in coarse buckets
  (sibling note, section 5). **[V]**

## 5. Where the prior must be off or weakened

| Situation | Why | Detect | Cost | Confidence |
|---|---|---|---|---|
| Password prompt, `read -s`, `gpg`, `sudo` | Random text; sim shows the prior makes random text 1.2x to 1.7x worse | ECHO flag off (needs JNI, **[U]**); else prompt text before the cursor ends in `:` with a word like `password` or `passphrase` or `PIN` on the cursor row; else foreground process name in a small list (`sudo`, `ssh`, `passwd`, `gpg`, `su`, `pinentry`) | Cheap | Medium. The echo flag is the only reliable one; the text rule has false positives and negatives |
| Editors and pagers (vim, nvim, nano, less, man) | Letters are commands in normal mode; text is not shell text | `isAlternateBufferActive()` | Free | High |
| Other TUIs (htop, btop, fzf, tmux, claude/codex style apps) | Letters are hotkeys or free text; mouse mode on | `isAlternateBufferActive()`; `isMouseTrackingActive()`; foreground `idle == false` | Free | High for alt-screen programs; inline TUIs that stay on the main screen (fzf in a non-fullscreen mode) are missed unless `idle` is false, which the 1.5 s resolver may lag **[U]** |
| Not at the shell's prompt (a running command reading stdin: `cat`, a REPL) | The line is not shell syntax | `isShellIntegrationCommandRunning()` when OSC 133 is on (C seen, D not yet); otherwise `ForegroundInfo.idle` | Free | Medium-high when integration is installed; weaker without |
| Non-English or non-Latin layouts | The prior is an English-letter shell model | Layout id of the active in-app layout; any non-ASCII character in the typed buffer resets and disables for the line | Free | High |
| After Ctrl, Alt, Meta, or any chord | The key is a command (Ctrl-R, Alt-b) | `TerminalModifiers` state at touch-down; also reset the buffer | Free | High |
| Fast burst | At high speed taps are noisier and the user is not looking; the right answer may be to trust the touch model more, or the prior more, and no published study says which | Inter-tap interval from the keyboard's own touch timestamps below a threshold; unspecified | Free | **[U]** which direction. Goodman found typing speed did *not* improve the touch model **[V]**. Start by leaving the prior unchanged in bursts and measure |
| Voice dictation active | Not typed | The dictation marks (OSC 7727, `isDictationMarksEnabled()`), and a buffer reset on dictation | Free | High |
| Pasting | Not typed | Paste action resets the buffer | Free | High |
| Path token, quote, `$VAR` | Near-random or free text (section 3.2) | The four-state scanner in 3.2 | O(1) per char | Medium; weights by class are **[U]** |
| Non-letter keys, Space, Enter, Backspace, Shift, symbols | Not what the prior models | `TapGeometry.isChar` plus a letters-only filter on both source and target | Free | High |
| Hardware keyboard also in use | The buffer will be incomplete | Reset on any hardware key event | Free | High |

A final gate is a per-line *surprise* test: if the last `m` resolutions (or raw taps, in shadow
mode) were each in the prior's bottom decile, the user is typing something the model does not
know (a URL, a name). Switch the prior off until the next reset. This also covers Gunawardana's
unanchored failure on mismatched text. **[U]** for the right `m` and decile; tune in shadow mode.

## 6. Safety rules

1. **Never move a tap outside its neighbourhood.** The prior picks only among the raw key and the
   character keys whose rectangle is within a small distance of the touch (the sim used 1.2 key
   widths; a stricter 0.3 key-width displacement, the repo's `MAX_SHIFT_FRACTION`, would be
   safer). Geometrically, a tap on q/w can become `q` or `w`, never `t`.
2. **Bound the bias, not just the weight.** Clamp `w * ln P(k|h)` to within a fixed window, for
   example +/- 1.0 nat, so a near-zero probability can only lose a border by a bounded amount. The
   sim shows `w` 0.3 gets about half the gain at about half the damage; the clamp is what keeps a
   badly wrong prior from stealing a clearly aimed tap.
3. **Anchors, honestly.** Gunawardana's anchor guarantees a tap near a key's centre is that key,
   and in their data the best anchor was *small* (area 8% of the key). In the repo's sim a 0.5
   anchor costs almost nothing on in-domain text (2.58% to 2.63%) and a 0.7 anchor costs 0.36
   points; neither helps random text. Choose 0.5 per axis as the default. A "never below the
   visible key" rule read strictly (Apple) means a tap inside the visible rectangle always keeps
   its key; on a gapless keyboard that removes the whole effect, since every border tap is inside
   some key. State this decision to the developer: the prior can only act in the band between two
   anchors.
4. **Smooth floor.** Every key keeps a non-zero probability so no letter is ever impossible
   (Gunawardana's smoothness condition). **[V]**
5. **Pair-consistency.** The existing `TapModel` makes the boundary between two keys one line seen
   from either side, so a press cannot cross at a spot where a press from the other key would
   cross back. A prior-driven shift must keep that: apply the same bias to both sides of a pair, or
   resolve once on the combined score and not twice.
6. **Raw-key observation only.** Keep recording against the raw grid key so neither the touch
   model nor the personal prior learns from its own output. **[V]** (existing rule).
7. **Reversibility.** The Backspace-retype signal in the sibling note (a char key, Backspace, an
   adjacent char key within a short window, nothing else) is the reversal. Use it three ways: (a)
   as a veto: if the last resolution flipped a key and the user immediately backspaced and
   re-tapped the raw key, set a per-pair penalty and count it; (b) as the metric (section 8); (c)
   as the label source for tuning `w`. A flip followed by Backspace twice in a row in a line turns
   the prior off for the rest of the line. **[U]** thresholds; tune on pong traces.
8. **Off by default, a single switch, resettable**, as `TapModel` already is. Keep it under the
   same "Learn where you tap" setting or a sibling toggle; the user copy must not say "language
   model" or "prediction", just what they get. (AGENTS.md "User-facing text".)
9. **No UI.** Nothing is shown. The only visible effect is that the key you meant is the key you got.
   (This also keeps the prior out of the "predictive keyboard" category the developer ruled out.)

## 7. Design sketch for this repo

### 7.1 Data sources

1. **Base table (build time)**: a Gradle or script step builds a binary asset from tldr-pages
   command lines (CC BY 4.0, attribution in licences) plus the names in `compgen -c`-style lists
   gathered on a reference device. Done offline, checked in as one small asset
   (`assets/keyboard-prior-v1.bin`), reproducible, no network at build. Never read at runtime
   from the internet.
2. **Command-name trie (runtime, per install)**: `$PREFIX/bin` names, rebuilt when the directory's
   mtime changes, on the I/O executor.
3. **Personal layer (runtime)**: counts from what the user types with the gates on, never from
   history files by default.
4. **No `$HISTFILE` by default.**

### 7.2 Model shape and size budget

Resident memory, all bounded and fixed at load:

| Part | Shape | Resident |
|---|---|---|
| Base 4-gram | Open-addressed int array, 4 bytes per entry (16-bit context hash, 8-bit char index, 8-bit quantised log-prob), pruned at min count 3, about 54K entries at 80% load, plus a ~40-entry unigram/backoff vector | about 270 KB, or 220 KB at full load. **Budget ceiling: 256 KB.** If it must be smaller, order 3 at min count 3 is about 74 KB at about 1.4 pts higher residual error at sigma 0.25 w 0.6 (3.30% versus 2.58%) |
| Command-name trie | Packed trie, names from `$PREFIX/bin`, capped at 4,096 names | 20-40 KB |
| Personal layer | Hash table of at most 16K counts, 4 bytes each, halved at a fixed total (same forgetting idea as `FORGET_AT`), never grows | 64 KB |
| Context state | Last 3 typed chars, scanner state, last resolutions ring (8 entries) | under 1 KB |
| Total | | **about 350-400 KB worst case, ~0.2% of the 185-200 MB idle PSS** |

This is two orders of magnitude below a kitty-image frame budget and holds no pixels. It loads
lazily on first use of the in-app keyboard and is dropped on `onTrimMemory` at the same level the
other stores use (size-class to check; **[U]**), then reloaded on demand. The repo's rule is that
memory comes back under pressure; a 350 KB table that is dropped and re-read from an asset in a
few milliseconds meets it. No per-tap allocation. No new thread.

Backoff and smoothing are baked into the quantised probabilities at build time (interpolated, so
a missing n-gram backs off to a shorter hash at lookup). The runtime does lookups only; it never
trains the base table.

### 7.3 Composition with `TapModel`

Today `TapModel.resolve(geometry, rawIndex, x, y)` moves the decision boundary between raw key
`r` and neighbour `n` by the shrunk, capped mean offset of the pair and picks the neighbour with
the greatest depth. A prior composes as one extra term on the same pair decision, inside the same
cap:

- For each candidate key `c` (raw plus character neighbours whose shifted rectangle contains the
  corrected point, as now), compute `s(c) = spatial(c) + clamp(w * g * ln P(c | h), -B, +B)`.
- `spatial(c)` is the existing depth-into-rectangle measure (`TapModel` uses depth, not a
  Gaussian), expressed as a margin in key widths; `g` converts nats to key widths. This is a
  design question: the repo has no Gaussian likelihood, so either convert the prior to a
  *boundary shift* (bias each pair's boundary by `delta = g * (lnP(n|h) - lnP(r|h))`, capped at
  0.3 of the narrower key, the same cap as the touch bias) or add a Gaussian per-key score. The
  boundary-shift form needs no new geometry, keeps the "one line seen from either side" property
  because it is antisymmetric in `(r, n)`, and reuses `MAX_SHIFT_FRACTION`. **Recommended.**
- Total shift for a pair is `clamp(touchBias + priorShift, +/- cap)`, so the prior can never push
  a boundary further than the touch model's own cap allows. The anchor then excludes the inner 50%
  per axis of the raw key.
- The prior needs the typed history, which `TapModel` does not have; add a `PriorContext` seam
  the controller owns and passes in (`TapResolver.resolveTap` gets no new arguments, the
  controller holds the context), so `inapp-keyboard/` upstream diffability is untouched. Record in
  `inapp-keyboard/UPSTREAM.md` if the interface ever has to change.
- Keep the policy pure and tested (`TapPrior`, no Android types), like `TapModel`, `PaneShape`
  and `DockLayoutPolicy`; the controller is the thin host.
- `TapModelStore` persistence is unchanged for the touch model; the personal layer gets its own
  small file and the same reset.

### 7.4 Gates

Section 5, collected into a single `PriorGate` the controller asks before every resolution. It
returns off for: alternate buffer; mouse tracking; shell command running (when integration is
seen); foreground not idle; password-like prompt or ECHO off; non-Latin or non-ASCII; modifiers
held or a chord pending; dictation or paste; hardware key since the last reset; non-letter source
or target; path, quote or `$VAR` token (until measured); and the surprise test. It returns
weakened (half `w`) for the first character after a reset (no context) and for a burst once
measured. Every gate fails *off*: unknown state means no prior, which is the existing behaviour.

### 7.5 What to measure

1. **Baseline first** on pong: the real tap error rate. The sim says that at 1.7% static error the
   prize is about a point; at 6.5% it is about four points. Without a measured baseline the
   expected gain is a guess. Count Backspace-retype pairs per thousand keystrokes (already
   proposed in the sibling note) with the feature off.
2. **Shadow mode** (compute, count, never act): per thousand keystrokes, how often the prior would
   have flipped a tap; of those, how often the user's next key was Backspace (would-have-been
   wrong) versus how often the raw key was followed by a Backspace-retype of the proposed key
   (would-have-been right). This estimates fixed/hurt on real input with no risk. Counters only,
   in a debug-visible store, no text.
3. **Offline harness** (unit test with recorded traces, JVM only): replay recorded raw touch
   positions with their intended keys (from committed lines that were not backspaced) through
   `TapModel` alone, then with the prior, report fixed/hurt per class (command position, flag,
   path). Include the random-string and password-shaped set to track the damage.
4. **Order and scanner ablation** in the harness: order 3 versus 4, with and without the
   four-state scanner class in the context, with and without the command-name trie.
5. **Latency** on pong: median and p99 for `resolveTap` with the prior on; the budget is under
   200 microseconds, since it runs at touch-down.
6. **PSS** on pong at idle with the prior loaded and dropped (against the 185-200 MB baseline in
   `docs/agents/performance.md`; the memory doc must be read before this change ships).

### 7.6 Effort (rough)

| Step | Days |
|---|---|
| Build script for the base asset (tldr extraction, pruning, quantisation, hash packing) and a test that loads it | 1.5 |
| `TapPrior` pure model: lookup, backoff, clamp, boundary-shift composition; JVM tests | 2 |
| Typed-context tracker and reset rules in the controller; scanner | 1.5 |
| `PriorGate`, plus the pty echo JNI call if it works | 1.5 |
| Command-name trie and the personal layer, with reset and persistence | 1.5 |
| Shadow-mode counters and the offline replay harness | 1.5 |
| Settings entry and copy, licences entry (tldr CC BY 4.0), `UPSTREAM.md` note if the hook changes | 0.5 |
| Total | **about 8-10** |

Parts are separable: the base asset, `TapPrior` and the typed context alone (about 5 days) are
enough for shadow mode; the personal layer and trie can follow.

### 7.7 Device checks owed (record in `project-docs/device-checks.md` under Agent or Developer)

Nothing here is device-verified. The emulator can check the structure; the rest needs pong.

- **Agent (drivable over adb, emulator `tl16`, `-s emulator-5554`)**: gates switch the prior off
  in vim, in `sudo` / `read -s` prompts, after Ctrl, in a non-shell REPL; the typed buffer resets
  on Enter, arrows, Tab and paste; the model drops under `onTrimMemory` and the keyboard still
  types; layout switch to a non-Latin layout disables it. The emulator's injected taps are exact,
  so it cannot tell whether the prior helps.
- **Developer (a hand on pong)**: whether `tcgetattr` on the pty master reports `ECHO` off at a
  `sudo` prompt (the echo gate depends on it); shadow-mode counters over a day of normal use;
  then a week on, comparing Backspace-retype pairs per thousand keystrokes with the feature off;
  fast-burst typing and whether the prior helps or hurts there; paths and long flags;
  whether a corrected key ever feels wrong ("that was the key I hit"). Latency and PSS from 7.5.
- Jank is not at issue (touch-down, no per-frame work), but the emulator cannot judge feel.

## 8. Open questions for the developer

1. Anchor size, 0.5 per axis versus 0.7: the first gives more fixes; the second is safer for odd
   text. The visible-key reading of the rule (Apple) would turn the feature off on a gapless grid,
   which is probably not wanted.
2. Whether the personal layer is worth its privacy surface, or the base table plus the command-name
   trie is enough. I would ship without it first and let shadow mode say.
3. Whether to add OSC 133 `B` to the shipped scripts for a second reason (clickable prompts,
   command-start selection). Not needed for the prior.
4. Real tap noise on pong: nothing here replaces measuring it.

## Sources

Read in full or in relevant part this session:

- Goodman, Venolia, Steury, Parker, "Language modeling for soft keyboards", IUI 2002, pp. 194-195.
  https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/p194-goodman.pdf (DOI
  10.1145/502716.502753; extended report MSR-TR-2001-118
  https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/tr-2001-118.pdf, not read)
- Gunawardana, Paek, Meek, "Usability guided key-target resizing for soft keyboards", IUI 2010.
  https://microsoft.com/en-us/research/wp-content/uploads/2016/02/paper-final.pdf (DOI
  10.1145/1719970.1719986)
- Lin, Wang, Zettlemoyer, Ernst, "NL2Bash: A Corpus and Semantic Parser for Natural Language
  Interface to the Linux Operating System", LREC 2018. https://aclanthology.org/L18-1491.pdf ;
  repository https://github.com/TellinaTool/nl2bash (not read)
- tldr-pages. https://github.com/tldr-pages/tldr (cloned to a scratchpad directory; `LICENSE.md`
  names CC BY 4.0)
- Microsoft Learn, "Shell integration in the Windows Terminal" (OSC 133 A/B/C/D and the bash
  recipe). https://learn.microsoft.com/en-us/windows/terminal/tutorials/shell-integration
- kitty shell integration. https://sw.kovidgoyal.net/kitty/shell-integration/ (first 100K
  characters of 135K read)
- GNU Bash manual, Programmable Completion Builtins (`compgen`).
  https://www.gnu.org/software/bash/manual/html_node/Programmable-Completion-Builtins.html
- fish shell docs, Interactive use. https://fishshell.com/docs/current/interactive.html
- zsh-syntax-highlighting highlighters.
  https://github.com/zsh-users/zsh-syntax-highlighting/blob/master/docs/highlighters.md (the
  `main` token list is on a separate page, not read)
- tree-sitter-bash README. https://github.com/tree-sitter/tree-sitter-bash
- XTerm Control Sequences (DECSET 47, 1047, 1049, 2004).
  https://invisible-island.net/xterm/ctlseqs/ctlseqs.html (first 100K of 189K characters read)
- pty(7). https://man7.org/linux/man-pages/man7/pty.7.html (says nothing about termios on the
  master)
- This repo: `app/src/main/java/com/termux/app/terminal/inappkeyboard/{TapModel,TapCorrectionController,TapModelStore}.java`;
  `inapp-keyboard/src/main/java/juloo/keyboard2/{Keyboard2View,TapGeometry}.java`;
  `terminal-emulator/src/main/java/com/termux/terminal/{TerminalEmulator,TerminalBuffer,TerminalRow}.java`;
  `terminal-emulator/src/main/jni/termux.c`;
  `app/src/main/java/com/termux/app/statusbar/{WindowForegroundResolver,ShellActivityTracker}.java`;
  `app/src/main/assets/shell-integration/termux-launcher.{bash,zsh}`;
  [`adaptive-touch-targets.md`](adaptive-touch-targets.md);
  `project-docs/reference/launcher/keyboard-mis-input-correction.md`.

Abstract, record or secondary only:

- Chen & Goodman, smoothing study (ACL 1996; CSL 1999; TR-10-98), via search results.
  https://arxiv.org/abs/cmp-lg/9606011
- Apple US 8,232,973 B2, Sivek & Riley 2022 and Yin et al. 2013: as read and recorded in the
  sibling note; not re-read.

Unverified, not found in a primary source: any published measurement of a character prior on shell
input; how real thumb noise compares with the Gaussian used in 2.4; `tcgetattr` on a pty master on
Android; the right surprise-test thresholds; the effect of per-token-class contexts.

The simulation scripts live in the session scratchpad
(`/tmp/claude-1000/-home-amalvajdan-Projects-termux-launcher-app-termux-launcher/447ac5f1-edb8-47b1-9308-cb2a650c05c8/scratchpad/exp*.py`)
and are not part of the repo; the parameters needed to rerun are in section 2.4.
