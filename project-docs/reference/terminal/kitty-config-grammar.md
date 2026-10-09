# kitty.conf grammar — primary-source notes

Sources: kitty docs at https://sw.kovidgoyal.net/kitty/conf/ and
https://sw.kovidgoyal.net/kitty/kittens/choose-fonts/ ; kitty source on GitHub
(`kovidgoyal/kitty`, `master`, fetched 2026-09-15) via
`raw.githubusercontent.com/kovidgoyal/kitty/master/<path>`:
`kitty/options/definition.py`, `kitty/options/utils.py`, `kitty/options/parse.py`,
`kitty/conf/utils.py`, `kitty/constants.py`, `kitty/config.py`, `kitty/cli.py`,
`kitty/fonts/__init__.py`, `kitty/launcher/utils.h`, `kitty/launcher/main.c`.
Line numbers are from the fetched snapshot and may drift; function names are stable
enough to re-locate them.

## Summary table

| Directive / topic | Exact grammar | Notes |
|---|---|---|
| Line syntax | `^([a-zA-Z][a-zA-Z0-9_-]*)\s+(.+)$` after `line.strip()`; `#`-first-char and blank lines skipped; `\` at start of the *next* line continues the previous one | `conf/utils.py` `key_pat`, `parse_line`, `_parse` |
| Comments | Only a line whose first non-whitespace char is `#`; inline `#` after content is literal text, not stripped | `conf/utils.py:287-289` |
| `include` | `include path` (relative to the *including* file's directory, or `config_dir` at top level) | `conf/utils.py:342-357` |
| `globinclude` | `globinclude glob-pattern` (glob resolved relative to including file's dir, sorted) | `conf/utils.py:297-300` |
| `envinclude` | `envinclude NAME_PATTERN` (fnmatch against env-var **names**; each match's value is parsed as config text) | `conf/utils.py:301-316` |
| `geninclude` | `geninclude prog` (runs a program/`.py`, stdout parsed as config; disabled for remote-control/session includes) | `conf/utils.py:317-341` |
| Case sensitivity | Directive lookup is `getattr(parser, key, None)` — exact, effectively lowercase-only | `options/parse.py:1675-1680` |
| `symbol_map` | `symbol_map U+XXXX[-U+YYYY][,...] Family Name` — dict keyed by `(start,end)`, later same-range wins, different ranges accumulate; no clear/reset value | `options/utils.py:1229-1251`, `options/parse.py:1364-1366` |
| `narrow_symbols` | `narrow_symbols codepoints [cells]`, default `1` | `options/utils.py:1254-1256` |
| `font_family` etc. | `auto` \| bare name (rest of line as-is) \| `key=value ...` spec via `shlex` | `fonts/__init__.py:129-163` (`FontSpec.from_setting`) |
| `font_features` | `font_features <postscript_name> <feature> ...`; `none` disables the option but does not clear other names already set | `options/utils.py:1084-1098` |
| `font_variations` | Does not exist as a directive; variable-font axes are `axis=value` pairs inside the font-spec syntax | grep of `definition.py`/`options/utils.py`: no match |
| `disable_ligatures` | `never` \| `cursor` \| `always`, case-insensitive, default `never` | `options/utils.py:625-627` |
| `modify_font` | `modify_font <metric> [font_name] <value>[px|%]`; `font_name` only for the (undocumented) `size` metric | `options/utils.py:1101-1133`, `fonts/__init__.py:93-101` |
| `box_drawing_scale` | 4 comma-separated floats, default `0.001, 1, 1.5, 2` | `options/utils.py:630-634` (approx) |
| Config discovery | `KITTY_CONFIG_DIRECTORY` (used verbatim, no `/kitty` suffix) → else first of `XDG_CONFIG_HOME`, `~/.config`, (macOS) `~/Library/Preferences`, each `XDG_CONFIG_DIRS` entry that **already has** a writable `kitty/kitty.conf` → else create `$XDG_CONFIG_HOME/kitty` (default `~/.config/kitty`) | `kitty/launcher/utils.h:199-236` (`get_config_dir`) |
| Load order | `/etc/xdg/kitty/kitty.conf` then `defconf` unless `--config` given; any `--config NONE` anywhere disables **all** loading, even other `--config` paths given alongside it | `conf/utils.py:439-446` (`resolve_config`), `cli.py:716-720` |

---

## 1. Line tokenisation, comments, `include*`

**Tokeniser** (`kitty/conf/utils.py`):
```python
key_pat = re.compile(r'([a-zA-Z][a-zA-Z0-9_-]*)\s+(.+)$')
```
In `parse_line` (line ~277):
```python
line = line.strip()
if not line or line.startswith('#'):
    return
m = key_pat.match(line)
...
key, val = m.groups()
```
So: the line is stripped of leading/trailing whitespace first; if what's left is empty or starts with `#` it's dropped — **inline `#` is not a comment marker**, it becomes part of `val` verbatim (this matches the docs prose: "This works only if the `#` character is the first character in the line", https://sw.kovidgoyal.net/kitty/conf/). The key is `[a-zA-Z][a-zA-Z0-9_-]*` (letters/digits/`_`/`-`, must start with a letter); the value is everything after the first run of whitespace to end of line — no quoting or escaping is done at this layer; each directive's own parser decides how to interpret its value (e.g. `shlex` for font specs, plain `.split()` elsewhere).

**Line continuation** (`_parse`, `kitty/conf/utils.py` ~line 385-405): a following line that starts with `\` (after `lstrip()`) is appended (minus the leading `\`) to the current line, and this repeats for consecutive continuation lines. This is documented too: "Lines can be split by starting the next line with the `\` character. All leading whitespace and the `\` character are removed." (https://sw.kovidgoyal.net/kitty/conf/).

**Blank lines**: dropped by the same `if not line` check — no special handling needed.

**Case sensitivity**: the regex itself accepts mixed-case keys, but the actual dispatch in `kitty/options/parse.py`:
```python
def parse_conf_item(key: str, val: str, ans: dict) -> bool:
    func = getattr(parser, key, None)
    if func is not None:
        func(val, ans)
        return True
    return False
```
uses `getattr` against a `Parser` object whose methods are exactly the lowercase option names generated from `definition.py`. An unknown/mis-cased key falls through to `log_error(f'Ignoring unknown config key: {key}')` in `parse_line`. So directive names are **effectively case-sensitive** (must be lowercase) even though nothing stops the tokeniser from matching `Font_Family`.

**`include`** (`conf/utils.py:342-357`): `include other.conf` — if `val` isn't absolute it's joined to `base_path_for_includes`, which is the directory of the *file currently being parsed* (or `config_dir` for the top-level file, see `_parse` line ~377). Recursion is guarded by a `Memory.seen()` set (normalized path), so re-including the same resolved path is a no-op with a warning.

**`globinclude`** (`conf/utils.py:297-300`): `globinclude kitty.d/**/*.conf` — glob-expanded via `pathlib.Path(base_path_for_includes).glob(val)`, sorted, each match processed like `include`.

**`envinclude`** (`conf/utils.py:301-316`): `envinclude KITTY_CONF_*` — `fnmatch.fnmatchcase` is run against environment-variable **names**; for every matching var, its **value** is treated as a block of config-file lines (`os.environ[x].splitlines()`) and parsed in place.

**`geninclude`** (`conf/utils.py:317-341`): `geninclude script` — runs an executable (or a `.py` file via `pygeninclude`) and parses its stdout as config lines; gated by an `allow_geninclude` flag (off for some includers, e.g. remote control, "for security").

Before any include path/pattern is used, it goes through `expandvars(os.path.expanduser(val.strip()), {'KITTY_OS': os_name()})` (`conf/utils.py:296`) — so `~` and `$VAR`/`${VAR}` expand, plus a synthetic `KITTY_OS` var (`linux`/`macos`/`bsd`), matching docs at https://sw.kovidgoyal.net/kitty/conf/.

## 2. `symbol_map`

Definition (`kitty/options/definition.py`):
```
symbol_map U+E0A0-U+E0A3,U+E0C0-U+E0C7 PowerlineSymbols
```
"Each Unicode code point is specified in the form `U+<code point in hexadecimal>`. You can specify multiple code points, separated by commas and ranges separated by hyphens. This option can be specified multiple times." (long_text, same file)

Parser (`kitty/options/utils.py:1229-1251`, `symbol_map_parser`):
```python
parts = val.split()
if len(parts) < min_size: raise ValueError(...)
family = ' '.join(parts[1:])
def to_chr(x):
    if not x.startswith('U+'): raise ValueError(...)
    return int(x[2:], 16)
for x in parts[0].split(','):
    a_, b_ = x.replace('–', '-').partition('-')[::2]
    b_ = b_ or a_
    a, b = map(to_chr, (a_, b_))
    if b < a or max(a, b) > sys.maxunicode or min(a, b) < 1:
        raise ValueError(...)
    yield (a, b), family
```
Confirmed exact rules:
- `val.split()` splits on **any run of whitespace**; the family name is `' '.join(parts[1:])`, i.e. reconstructed with single spaces — internal multi-space/tab runs in the family name are **not** preserved verbatim, they collapse to one space each. Leading/trailing whitespace is already gone.
- The prefix must be literal `U+` (uppercase); the hex digits themselves are case-insensitive (`int(x[2:], 16)`).
- Ranges are `U+AAAA-U+BBBB`; a bare `U+AAAA` (no `-`) is a one-codepoint range (`b_ or a_`); an en-dash `–` is also accepted and normalized to `-`.
- Multiple ranges in one line are comma-separated (`parts[0].split(',')`).
- **Merging**: the option storage is a dict keyed by `(start, end)` tuples (`options/parse.py:1364-1366`, `ans["symbol_map"][k] = v`). So within one file, a later `symbol_map` line with the **same exact range** overwrites the family for that range; a line with a **different range** just adds another entry — mappings **accumulate**, they are not wholesale replaced by the last line. This also holds **across config files**: `merge_result_dicts` (`options/parse.py:1660-1669`) does, for every dict-typed option, `ans[k] = merge_dicts(v, vals.get(k, {}))`, and `merge_dicts` (`conf/utils.py`) is `defaults.copy(); ans.update(newvals); return ans` — again a per-key union, so a second config file's `symbol_map` entries are merged into, not substituted for, the first file's.
- **Clearing**: no special "none"/empty value is handled by `symbol_map_parser` or the `symbol_map` parser method — there is no way to reset the whole map from a later file; the only way to override a mapping is to re-declare the identical codepoint range.

## 3. `narrow_symbols`

```python
def narrow_symbols(val: str) -> Iterable[tuple[tuple[int, int], int]]:
    for x, y in symbol_map_parser(val, min_size=1):
        yield x, int(y or 1)
```
(`options/utils.py:1254-1256`). Syntax: `narrow_symbols codepoints [cells]` — same codepoint-range grammar as `symbol_map`, but the second field is optional (`min_size=1`) and is an integer cell count; **default is `1`** when omitted (`int(y or 1)`). Storage is again a dict by codepoint range (`options/parse.py:1186-1188`), so the same accumulate/overwrite-on-exact-range behavior as `symbol_map` applies. Docs example: `narrow_symbols U+E0A0-U+E0A3,U+E0C0-U+E0C7 1` (`options/definition.py`).

## 4. `font_family` / `bold_font` / `italic_font` / `bold_italic_font`

All four use `option_type='parse_font_spec'` → `FontSpec.from_setting` (`kitty/fonts/__init__.py:129-163`):
```python
@classmethod
def from_setting(cls, spec: str) -> 'FontSpec':
    if spec == 'auto':
        return FontSpec(system='auto', created_from_string=spec)
    items = tuple(shlex_split(spec))
    if '=' not in items[0]:
        return FontSpec(system=spec, created_from_string=spec)
    axes, defined, features = {}, {}, ()
    for item in items:
        k, sep, v = item.partition('=')
        if sep != '=':
            raise ValueError(...)
        if k in ('family', 'style', 'full_name', 'postscript_name', 'variable_name'):
            defined[k] = v
        elif k == 'features':
            features += tuple(ParsedFontFeature(x) for x in v.split())
        else:
            try: axes[k] = float(v)
            except Exception: raise ValueError(...)
    return FontSpec(axes=tuple(axes.items()), created_from_string=spec, features=features, **defined)
```
Confirmed:
- `auto` (exact string) is a distinct mode (`bold_font`/`italic_font`/`bold_italic_font` default to it; `font_family` defaults to `monospace`, `options/definition.py`).
- **Bare name mode**: triggered when `shlex_split(spec)[0]` contains no `=`. In that case the **entire original spec string** (not just the first token) is stored verbatim as `system=spec` — so `font_family Fira Code` (no quotes) works and keeps its exact spacing, because it never enters key=value parsing at all.
- **key=value mode**: triggered once the first shlex token contains `=`; every token in the line must then be `key=value` or it raises `ValueError`. Recognized keys copied straight onto `FontSpec` fields: `family`, `style`, `full_name`, `postscript_name`, `variable_name`. `features=...` is special-cased: its value is whitespace-split into individual OpenType feature tokens (same syntax as the standalone `font_features` directive). **Any other key is treated as a variable-font axis tag** and must parse as `float` (e.g. `wght=800`, `opsz=14`) — an unrecognized non-numeric key (including **`path=`**) raises `ValueError`. **`path=` is not a supported key** — confirmed absent from the docs example set (https://sw.kovidgoyal.net/kitty/kittens/choose-fonts/) and from this parser.
- **Quoting**: because tokenisation uses `shlex_split`, quotes are required only when a value itself contains whitespace or shell-meta characters, e.g. `family="Fira Code" style="Bold"` — without quotes, `Code` would become its own token lacking `=` and fail. Docs examples confirm this style: `font_family family="Fira Code"`, `font_family postscript_name=FiraCode`, `font_family family=SourceCodeVF variable_name=SourceCodeUpright features="+zero cv01=2" wght=380` (https://sw.kovidgoyal.net/kitty/kittens/choose-fonts/).

## 5. `font_features`

`font_features(val)` (`options/utils.py:1084-1098`): returns immediately (no entries) if `val == 'none'`; otherwise `parts = val.split()`, requires `len(parts) >= 2`, yields `(parts[0], tuple(ParsedFontFeature(f) for f in parts[1:]))`, logging and skipping any token that fails to parse. Syntax: `font_features <postscript_name> <feature> <feature> ...`, e.g. `font_features FiraCode-Retina +zero +onum`, `font_features TT2020StyleB-Regular -liga +calt` (docs). Individual feature tokens are validated by `ParsedFontFeature` (uses HarfBuzz feature-string syntax, per docs: `+tag` enable, `-tag` disable, `tag=N` set value, e.g. `cv05=2`). Storage: dict keyed by `postscript_name` (`options/parse.py:1029-1031`, `ans["font_features"][k] = v`) — a second line for the **same** postscript name **replaces** its whole feature tuple; different names accumulate (same merge semantics as `symbol_map` across files, since it's also dict-typed). `none` (the option's own default value) makes the parser yield nothing at all — it is a no-op, not a "clear everything" instruction; it cannot retract a `font_features` entry set for a specific name by another line/file.

## 6. `font_variations`

**Does not exist.** No such directive appears in `kitty/options/definition.py`, `kitty/options/utils.py`, or `kitty/options/parse.py` (checked by grep across all three). Variable-font axis values are set through the font-spec key=value syntax described in §4 — arbitrary `tag=value` pairs (e.g. `wght=800`) become `FontSpec.axes` entries (`kitty/fonts/__init__.py:135,147-163`). There is no separate `font_variations` option in current kitty.

## 7. `disable_ligatures`

`disable_ligatures(x)` (`options/utils.py:625-627`) does `{'never': 0, 'cursor': 1, 'always': 2}.get(x.lower(), 0)`. Accepted values: `never` (default), `cursor`, `always` — matched case-insensitively (`.lower()`); anything else silently maps to `never` (0). Docs (`options/definition.py` long_text): "always render them... `cursor`... `always`...". Also settable live per-window via `disable_ligatures_in` map actions, unrelated to the conf grammar itself.

## 8. `modify_font`

```python
def modify_font(val):
    parts = val.split()
    pos, plen = 0, len(parts)
    if plen < 2: log_error(...); return
    mtype = getattr(ModificationType, parts[pos], None)
    if mtype is None: log_error(...); return
    pos += 1
    font_name = ''
    if mtype is ModificationType.size:
        font_name = parts[pos]; pos += 1
    if plen - pos < 1: log_error(...); return
    sz = parts[pos]; pos += 1
    munit = ModificationUnit.pt
    if sz.endswith('%'): munit = ModificationUnit.percent; sz = sz[:-1]
    elif sz.endswith('px'): munit = ModificationUnit.pixel; sz = sz[:-2]
    mvalue = float(sz)
    key = mtype.name
    if font_name: key += f':{font_name}'
    yield key, FontModification(mtype, ModificationValue(mvalue, munit), font_name)
```
(`options/utils.py:1101-1133`). `ModificationType` enum (`kitty/fonts/__init__.py:93-101`): `underline_position`, `underline_thickness`, `strikethrough_position`, `strikethrough_thickness`, `cell_width`, `cell_height`, `baseline`, and an extra **`size`** member not mentioned in the `modify_font` docstring — `size` is the only type that also takes a `font_name` argument (`modify_font size <font_name> <value>`); all other metrics are global (no font name field) and a repeat line for the same metric simply overwrites the previous value (dict key is `mtype.name`, or `mtype.name:font_name` for `size`).

Units: no suffix → points (`ModificationUnit.pt`); trailing `%` → percentage of the original metric; trailing `px` → pixels. Documented examples: `modify_font underline_position -2`, `modify_font underline_thickness 150%`, `modify_font strikethrough_thickness 200%`, `modify_font strikethrough_position 2px`, `modify_font cell_width 80%`, `modify_font cell_height -2px`, `modify_font baseline 3` (`options/definition.py` long_text / https://sw.kovidgoyal.net/kitty/conf/). Docs note baseline changes auto-adjust underline/strikethrough by the same amount.

## 9. `box_drawing_scale`

```python
def box_drawing_scale(x: str) -> tuple[float, float, float, float]:
    ans = tuple(float(q.strip()) for q in x.split(','))
    if len(ans) != 4:
        raise ValueError(...)
    return ans
```
(`options/utils.py`, ~line 630, exact number drifted slightly between fetches but function body confirmed). Syntax: exactly four comma-separated numbers (pts), for thin/normal/thick/very-thick box-drawing line weights; default `0.001, 1, 1.5, 2` (`options/definition.py`). Values are DPI-scaled to pixels at draw time (docs, https://sw.kovidgoyal.net/kitty/conf/).

## 10. Config discovery

Directory resolution is **not** in Python — it happens in the C launcher, `kitty/launcher/utils.h`, function `get_config_dir` (~line 199-236):
```c
q = getenv("KITTY_CONFIG_DIRECTORY");
if (q && q[0]) { expand(q, output, outputsz); return true; }
q = getenv("XDG_CONFIG_HOME");
check_and_ret(q);
check_and_ret("~/.config");
#ifdef __APPLE__
check_and_ret("~/Library/Preferences");
#endif
q = getenv("XDG_CONFIG_DIRS");
if (q && q[0]) { /* split on ':' and check_and_ret each */ }
q = getenv("XDG_CONFIG_HOME"); if (!q||!q[0]) q = "~/.config";
expand(q, buf2, sizeof(buf2));
snprintf(output, outputsz, "%s/kitty", buf2);
if (makedirs(output, 0755)) return true;
```
where `check_and_ret(x)` only accepts a candidate directory if it **already** has a *writable* `kitty/kitty.conf` inside it (`is_dir_ok_for_config`, same file, ~line 189-197: appends `/kitty/kitty.conf`, requires `access(..., F_OK)` then `access(<dir>, W_OK)`).

So the resolution order is:
1. `KITTY_CONFIG_DIRECTORY` — if set and non-empty, used **directly** (tilde/`$VAR`-expanded, made absolute) as the config directory, with **no** `/kitty` suffix appended, and with **no** existence check. This means when `KITTY_CONFIG_DIRECTORY=/foo/bar` is set, kitty reads `/foo/bar/kitty.conf`, **not** `~/.config/kitty/kitty.conf` — the default file is not consulted at all in this case (confirmed: this branch `return true`s immediately, before any of the `~/.config`-style checks run).
2. Otherwise, in order: `$XDG_CONFIG_HOME`, then `~/.config`, then (macOS only) `~/Library/Preferences`, then each `:`-separated entry of `$XDG_CONFIG_DIRS` — the **first** of these that already contains a writable `<dir>/kitty/kitty.conf` wins.
3. If none qualify, kitty falls back to creating `$XDG_CONFIG_HOME/kitty` (defaulting to `~/.config/kitty` if that var is unset) via `makedirs`, and uses that going forward — this is the common case on a fresh install, matching the docs' "usually `~/.config/kitty/kitty.conf`" (https://sw.kovidgoyal.net/kitty/conf/).

`config_dir` computed above flows into Python via `kitty_run_data['config_dir']` (`kitty/launcher/main.c:107-116`), consumed by `kitty/constants.py:109-130` (`_get_config_dir`), which sets `config_dir` and `defconf = os.path.join(config_dir, 'kitty.conf')`.

**Load order and `--config`** (`kitty/conf/utils.py:439-446`, `resolve_config`; `kitty/cli.py:716,719-720`):
```python
SYSTEM_CONF = '/etc/xdg/kitty/kitty.conf'
def resolve_config(SYSTEM_CONF, defconf, config_files_on_cmd_line=()):
    if config_files_on_cmd_line:
        if 'NONE' not in config_files_on_cmd_line:
            yield SYSTEM_CONF
            yield from config_files_on_cmd_line
    else:
        yield SYSTEM_CONF
        yield defconf
```
- No `--config`/`-c` on the command line: kitty loads `/etc/xdg/kitty/kitty.conf` (if present) then `defconf` (the discovered directory's `kitty.conf`, created empty on first run by `kitty/config.py` if missing).
- One or more `--config path` given, **none** of them equal to the literal string `NONE`: `/etc/xdg/kitty/kitty.conf` is still loaded first, then each `--config` path in the order given on the command line — `defconf` itself is **not** auto-included unless the user also passes it explicitly.
- If the literal value `NONE` appears **anywhere** among the given `--config` values, the whole generator yields nothing — **no** system conf and **none** of the other `--config` paths are loaded either (the check is membership in the whole tuple, not per-item), matching docs: "Use the special value NONE to not load any config file" (https://sw.kovidgoyal.net/kitty/invocation/).
- Multiple files are **merged**, not last-one-wins-wholesale: `load_config` (`conf/utils.py:449` / `kitty/config.py:169`) folds each successfully-opened file's parsed dict onto the running result via `merge_configs` (`merge_result_dicts`, `options/parse.py:1660-1669`); for plain scalar/list options a later file's value overwrites the earlier one, but for dict-typed options (`symbol_map`, `narrow_symbols`, `font_features`, `modify_font`, `env`, etc.) the dicts are unioned key-by-key across files (see §2). `-o`/`--override name=value` overrides are applied last, after all files (`conf/utils.py:475-477`, https://sw.kovidgoyal.net/kitty/invocation/).

UNCONFIRMED: the exact precedence documented on the invocation page mixed a WebFetch paraphrase with a possibly-unrelated "files are searched for in the order …" sentence that could not be re-confirmed verbatim against the fetched HTML in this pass — the code-derived order above (`resolve_config`/`get_config_dir`) is the one to trust; treat the invocation-page prose as corroborating, not authoritative, until re-checked against the page source directly.
