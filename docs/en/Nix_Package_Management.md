# Nix Package Management

The **Nix edition** (`com.termux.launcher.nix`) replaces the APT package
manager with [Nix](https://nixos.org) in a
[Nix-on-Droid](https://github.com/nix-community/nix-on-droid)-style
environment. Instead of `pkg install`, packages come from the official
`nixpkgs` collection — tens of thousands of prebuilt packages served from
`cache.nixos.org`, independent of any Termux repository.

The launcher itself is unchanged: same terminal, palette, panes,
keyboard, and `launcherctl`/`tai` tooling as the Termux edition.

## Coming from `pkg`? Start here

One idea to get used to, and the rest follows: **there is no install
command.** You keep a list of the programs you want, and rebuild. The
list is a file, `~/.config/nix-on-droid/home.nix`; the rebuild is
`nix-on-droid switch`. That is why you get rollbacks and why a fresh
phone can be made identical to this one — the list *is* the system.

Two files and one command cover almost everything:

| | |
|---|---|
| `~/.config/nix-on-droid/home.nix` | your list of programs |
| `~/.config/nix-on-droid/flake.lock` | which exact versions (updated on demand) |
| `nix-on-droid switch --flake ~/.config/nix-on-droid` | make it so |

### Command translation

| Termux | Nix edition |
|---|---|
| `pkg search foo` | `nix search nixpkgs foo` |
| `pkg show foo` | `nix eval nixpkgs#foo.meta.description` |
| `pkg install foo` | add `foo` to `home.packages` in `home.nix`, then switch |
| `pkg uninstall foo` | remove it from `home.nix`, then switch |
| `pkg update` | `nix flake update` (refreshes the pinned versions) |
| `pkg upgrade` | `nix flake update` **then** switch |
| `pkg list-installed` | read `home.nix`, or `ls ~/.nix-profile/bin` |
| `apt clean` / `autoremove` | `nix-collect-garbage -d` |
| *(no equivalent)* | `nix-on-droid rollback` — undo the last switch |
| *(no equivalent)* | `nix run nixpkgs#foo` — run once, install nothing |

`pkg update` and `pkg upgrade` are one operation in Termux and two here,
and the split is the useful part: updating only moves the pins in
`flake.lock`, and nothing on your phone changes until you switch. So you
can check what an upgrade would do before accepting it:

```sh
cd ~/.config/nix-on-droid
nix flake update                     # bump the pins
nix-on-droid build --flake .         # build only, no activation
nix-on-droid switch --flake .        # accept
```

### Installing something, start to finish

```sh
nix search nixpkgs ripgrep           # 1. find the name
nix run nixpkgs#ripgrep -- --help    # 2. optional: try it without installing
nvim ~/.config/nix-on-droid/home.nix # 3. add `ripgrep` to home.packages
cd ~/.config/nix-on-droid && nix-on-droid switch --flake .
```

```nix
home.packages = with pkgs; [
  # ... what is already there
  ripgrep
];
```

If a new binary is not found afterwards, start a new shell (`exec fish`)
— a running session is still pointing at the old generation.

### The one rule

Do **not** use `nix-env -i` or `nix profile install` on this setup.
They write to the same user profile that home-manager manages, so your
next switch removes whatever they installed, and version conflicts
between the two are confusing to unpick. List plus switch, always.
`nix run` and `nix shell` are the safe imperative escape hatches: they
install nothing.

## How it works

On first launch the app downloads a bootstrap that contains the Nix
package manager and a minimal shell. Every session then runs inside a
[proot](https://proot-me.github.io/) that maps the app's private
directory to `/nix`, so unmodified upstream binaries (glibc, not Android
libc) run as-is and the official binary cache applies.

**No emulation is involved** — this is not QEMU. Every binary is native
`aarch64` code executing directly on the CPU. proot is a
ptrace-based supervisor that intercepts syscalls only to rewrite
*paths*: nixpkgs binaries hardcode `/nix/store/...` locations that an
Android app cannot own, so proot translates `/nix`, `/bin`, `/etc` and
`/usr` to the app's private directory on the fly (the real Android
system stays visible at `/android`). The cost is a small
syscall-interception overhead — noticeable in `fork`/`exec`-heavy
workloads, irrelevant for compute — not the instruction-translation
cost of an emulator.

## Getting started (first launch)

1. Install the nix-edition APK and open it. The app downloads and
   unpacks the bootstrap (~38 MB) on its own — no interaction needed.
2. The first terminal session asks:

   ```
   Do you want to set it up with flakes? (y/N)
   ```

   Answer **`y`**. Flakes are what the shell template below uses, and
   the flake setup is the tested path.
3. The first generation now builds on the device. Expect minutes on a
   recent phone and up to ~35 minutes on an old one. The
   `evaluating derivation ...` phase prints nothing for a long time —
   that is normal, it is not stuck. Keep the app in the foreground
   (Android cuts the app's network in the background).
4. You have a `bash-5.3$` prompt when it finishes. That is the stock
   environment; continue below to get the full shell setup.

## Shell environment (fish, oh-my-posh, eza/zoxide/yazi, Neovim, fastfetch)

The fork ships a ready-made flake template that recreates the launcher's
reference shell — the same fish config, oh-my-posh Material themes, and
CLI stack the examples in this wiki use. Replace the minimal first-boot
files with it:

```sh
cd ~/.config/nix-on-droid
rm flake.nix nix-on-droid.nix        # the minimal files from first boot
nix flake init -t github:PickleHik3/nix-on-droid/launcher-nix#launcher
nix-on-droid switch --flake ~/.config/nix-on-droid
```

Open a new session afterwards: fish is the login shell, prompt themed,
`eza`/`zoxide` wired in.

The template's layout — **which file owns what matters**:

| File | Module type | What goes here |
|---|---|---|
| `flake.nix` | flake wiring | inputs (nixpkgs, home-manager, the fork), overlays |
| `nix-on-droid.nix` | system module | `environment.packages`, `user.shell`, `android-integration` |
| `home.nix` | home-manager module | `home.packages`, dotfiles (`xdg.configFile`), activation hooks |
| `toolkits.nix` | plain booleans | which groups `home.nix` installs — see below |
| `config/` | plain files | the actual `config.fish`, oh-my-posh themes, `fastfetch/config.jsonc`, the logo GIF |

The split exists because two different configuration systems are wired
together. `nix-on-droid.nix` is evaluated by nix-on-droid's own module
set and manages the *environment* — everything outside your home
directory: the login shell, `/etc`, base packages every script assumes,
Android glue like `termux-setup-storage`. `home.nix` is evaluated by
[home-manager](https://github.com/nix-community/home-manager) (embedded
via the `home-manager.config` bridge in `flake.nix`) and manages *your
user*: dotfiles, per-user packages, activation hooks, session
variables.

Rule of thumb: a file under `~/.config` or a tool only you invoke →
`home.nix`; the login shell, a base CLI everything expects (git, curl,
sed), or Android integration → `nix-on-droid.nix`. `flake.nix` is where
the two meet — it owns the inputs, the overlays, and the wiring.

Do **not** paste `home.nix` contents into `nix-on-droid.nix` or vice
versa: `home.*` options and `lib.hm` only exist inside home-manager, so
the switch fails with `error: attribute 'hm' missing` /
"option `home' does not exist". System options and home options live in
different files by design.

### Choosing what gets installed: `setup-toolkits`

The template groups its packages into toolkits, and `toolkits.nix` is the
file that says which ones you want. `setup-toolkits` is a checklist over
that file — it flips the booleans and runs the switch, which is exactly
what you would do by hand:

```sh
setup-toolkits
```

```
  Toolkits — /data/data/com.termux.launcher.nix/files/home/.config/nix-on-droid/toolkits.nix

    ✓  shell         fish, oh-my-posh, eza, zoxide, yazi, fd, ripgrep, fzf
    ✓  eye-candy     fastfetch with the animated GIF logo, timg, chafa
    ✓  editor        neovim + setup-nvim (implies build tools)
    ✓  build         cc, make, cmake, autotools, pkg-config, binutils — build from source
    ✗  node          nodejs, npm, npx (npm -g installs into ~/.npm-global)
    ✗  go            go (go install writes ~/go/bin)
    ✗  python        python3, uv, uvx (uv tool install writes ~/.local/bin)
    ✗  animated-logo patched fastfetch so the GIF animates — compiles ~20 min on device

      1) everything            every toolkit above except the animated logo
      2) shell essentials      shell + eye candy, nothing else
      3) pick one by one
      4) quit, change nothing
```

Option 3 walks the list and Enter keeps whatever a toolkit is set to
now, so it doubles as a way to change one thing without answering for
the rest.

Non-interactive forms:

```sh
setup-toolkits --list                    # current selection, no changes
setup-toolkits --all                     # everything prebuilt (not the animated logo)
setup-toolkits --essentials              # shell + eye candy only
setup-toolkits --enable node,go          # leaves every other toolkit as it is
setup-toolkits --disable eyeCandy
setup-toolkits --enable python --no-switch   # edit the file, switch later yourself
```

The defaults are shell, eye-candy, editor and build on; node, go and
python off, because a toolchain nobody asked for is a few hundred MB of
download. `editor` implies `build`: a Neovim distro compiles treesitter
grammars on first launch.

Editing `toolkits.nix` in `nvim` is equally valid, and the two cannot
disagree — the script reads the same file it writes. Only the booleans
matter to `home.nix` and `flake.nix`; comments are yours.

Adding packages that are not in any toolkit stays a `home.nix` edit, as
always — the toolkits are a starting set, not a boundary. That edit has a
place waiting for it: `extraPackages`, an empty commented list near the top
of `home.nix`, already wired into `home.packages`.

```nix
  extraPackages = with pkgs; [
    htop
    jq
  ];
```

Then switch. Names are nixpkgs attribute names — `nix search nixpkgs htop`,
or <https://search.nixos.org/packages>. A name that does not exist fails the
switch by name and leaves the running environment alone, so a typo costs the
error message and nothing else.

Nothing you write takes effect on save, and no shell restart applies it
either: the switch is what builds and activates. Because that is easy to
learn the hard way, the shipped fish greeting compares the flake directory's
`.nix` files against the last switch and says so when they are ahead:

```
~/.config/nix-on-droid has changes that are not applied yet — run: nix-on-droid switch --flake ~/.config/nix-on-droid
```

### Global installs: `npm -g`, `go install`, `uv tool`

The one place where "the file is the system" needs help is the *other*
package managers. Their default global prefix is the nix profile, which
is a read-only store path that the next switch replaces — `npm install
-g` fails outright there, and anything that did land would disappear at
the next `nix-collect-garbage`.

So the template redirects each of them into your home directory and puts
the result on `PATH`:

| Tool | Global installs land in | Set by |
|---|---|---|
| `npm install -g`, `npx` | `~/.npm-global` (`bin/` on PATH) | `NPM_CONFIG_PREFIX` |
| `go install` | `~/go/bin` | `GOPATH`, `GOBIN` |
| `uv tool install`, `uvx` | `~/.local/bin` | uv's own default |

Those directories are outside nix's bookkeeping, which is the point:
they survive switches, rollbacks and garbage collection. The flip side
is that nix cannot reproduce them on another phone — if you want a tool
on every device, put it in `home.nix` instead, and use the global
installs for the long tail nixpkgs does not carry.

Two device-specific notes:

- **Native npm modules** (`node-gyp`) need a compiler: keep the `build`
  toolkit on, or `npm install -g` fails on any package with a C
  addon.
- **uv does not manage Pythons here.** Its downloaded interpreters are
  `python-build-standalone` builds that expect a distro loader at
  `/lib/ld-linux-aarch64.so.1`, which does not exist inside the proot —
  the failure looks absurd ("no such file or directory" for a file that
  is plainly there). The template therefore sets
  `UV_PYTHON_DOWNLOADS=never` and `UV_PYTHON_PREFERENCE=only-system`, so
  uv uses the nix `python3` from the `python` toolkit. `uv venv`,
  `uv pip`, `uv tool install` and `uvx` all work against it; only
  `uv python install` is off the table. For a different Python version,
  add it to `home.nix` (`python312`, `python313`) and point `uv --python`
  at it.

### Building from source

The `build` toolkit is the equivalent of a `base-devel` group: `gcc`,
`make`, `cmake`, `autoconf`, `automake`, `libtool`, `pkg-config`,
`binutils`, `patch`, `gettext`, `file`, and the `tar`/`gzip`/`xz`/`unzip`
archivers. That is enough to `./configure && make && make install` an
ordinary source tree on the phone.

Libraries to build *against* deliberately stay out of the global
profile: they are per-project, and a global `zlib` does not give you the
headers anyway. Ask for them per shell:

```sh
nix shell nixpkgs#zlib.dev nixpkgs#openssl.dev nixpkgs#sqlite.dev
```

Inside that shell `pkg-config --cflags zlib` resolves, and the shell ends
when you type `exit` — nothing is installed. `nix develop` does the same
thing from a project's own flake, and is the better habit for anything
you build more than once.

Expect compilation to be slower than the CPU suggests: proot intercepts
syscalls, and `configure` scripts are thousands of tiny `fork`/`exec`
pairs. `make -j$(nproc)` still helps; a serial `configure` is just slow.

### Neovim: `setup-nvim`

Neovim starts with no configuration, and the template does not pick a distro
for you. Run the chooser when you want one:

```sh
setup-nvim
```

```
  1) AstroNvim  + wallpaper-matched colourscheme, which-key hints
  2) NvChad     + wallpaper-matched colourscheme, searchable cheatsheet (<leader>ch)
  3) LazyVim    batteries included, many plugins
  4) kickstart  one readable init.lua you own and edit
  5) stock      no distro; just clipboard + line wrap
  6) quit
```

Enter takes AstroNvim.

Whatever you choose also gets the launcher integrations: OSC 52 clipboard so
yanks reach the Android clipboard, and always-on line wrap for a
phone-width screen. They land in `lua/launcher/` inside your config,
copied once and yours to edit or delete afterwards.

Non-interactive forms, for a scripted setup:

```sh
setup-nvim --distro astronvim
setup-nvim --distro astronvim --appname astro   # side by side; NVIM_APPNAME=astro nvim
setup-nvim --integrations-only                # add clipboard + wrap to a config you already have
```

**An existing `~/.config/nvim` is never overwritten.** If one exists,
`setup-nvim` offers to install alongside it under `NVIM_APPNAME`, to add
only the integrations, or to replace it — and replacing needs you to type
`REPLACE`. Earlier revisions of the template cloned the LazyVim starter
automatically on first activation; if that is what you have, it stays exactly
as it is, and you can try something else side by side without losing it.

On AstroNvim and NvChad, the colourscheme is generated from the launcher's
wallpaper palette (`~/.termux/material-colors.sh`) and retints itself when you
change wallpaper. Syntax colours come from the terminal's ANSI set so code keeps
its hue separation, UI chrome comes from the Material surface roles, and contrast
is clamped so comments stay legible — including a fallback to a fixed palette
when a greyscale wallpaper cannot supply usable hues. Diagnostics are pinned to
Material's `ERROR` role and pushed apart in chroma when a red-tinted wallpaper
would otherwise make a warning look like a keyword. `:MaterialThemeInfo` reports
what it decided.

`:MaterialTransparent` toggles a transparent background (off by default). A
terminal cell has no alpha channel, so this is on/off in Neovim: it makes the
buffer inherit the terminal's own background, and the *degree* then comes from
the launcher's terminal opacity setting. Chrome — statusline, sidebar, floats,
pickers — deliberately stays solid, because that is where small dense text over
a wallpaper stops being readable.

If your config predates the toolchain packages — `nvim` reporting a missing
`tree-sitter (CLI)`, `fzf` or `lazygit` — add them to `home.nix` yourself
and switch:

```nix
home.packages = with pkgs; [
  tree-sitter   # error without it: treesitter cannot install grammars
  fzf           # pickers
  lazygit       # the <leader>gg keymap
  python3       # lazy.nvim's hererocks builds luarocks with it
  imagemagick   # snacks.image renders more than plain PNGs; optional
];
```

Two environment fixes belong with it. Nothing in the bootstrap sets a
locale, so glibc runs in the `C` locale and neovim reports "Locale does not
support UTF-8" — which also mangles box-drawing glyphs everywhere else in
the shell. And `xdg-open` does not exist inside the proot, so `gx` and
anything else opening a URL silently fails, even though Android can handle
it:

```nix
home.sessionVariables.LANG = "C.UTF-8";   # built into glibc, no locale archive needed

home.file.".local/bin/xdg-open" = {
  executable = true;
  text = ''
    #!/bin/sh
    exec termux-open "$@"
  '';
};
home.sessionPath = [ "$HOME/.local/bin" ];
```

One more, for LazyVim specifically, in `~/.config/nvim/lua/config/lazy.lua` —
that file is yours, not the template's, so an existing install needs it by hand
(`setup-nvim` applies it for you on a fresh LazyVim install):

```lua
require("lazy").setup({
  rocks = { enabled = false },
  -- ...
})
```

lazy.nvim's own health check says "no plugins require `luarocks`, so you can
ignore any warnings below" and then reports an error anyway, because with
rocks on it still wants to build hererocks on the phone. Turning them off
removes both.

Clipboard integration has no fix here: no clipboard tool exists inside the
proot, so neovim's `"+` and `"*` registers stay unavailable. `trash`, `gs`,
`tectonic` and `mmdc` are likewise absent — snacks.image degrades to the
formats it can render itself, and deletions are permanent.

### Animated fastfetch logo

The template ships the GIF: the first switch copies a small animated
launcher logo to `~/Pictures/gif/skel.gif`, which is the path
`config/fastfetch/config.jsonc` points at. The file is a plain writable
copy — replace it with any GIF of your own (`cp yours.gif
~/Pictures/gif/skel.gif`, no switch needed) and an existing file is
never overwritten. fastfetch falls back to text output if the path goes
missing.

Stock nixpkgs fastfetch draws only the first frame. Full animation over
the kitty graphics protocol needs the patched build, which the template
carries as `overlays.nix` and turns on from `toolkits.nix`:

```sh
setup-toolkits --animated-logo
```

or, by hand, `animatedFastfetchLogo = true;` and a switch. There is no
binary cache entry for a patched fastfetch, so it compiles on the device
— serial build, roughly 20–50 minutes depending on the phone, app
foregrounded. Everything else about the logo works without it.

### SSH into the phone

The template ships a small sshd toolset (`sshd-tools.nix`), declarative
where it matters and manual where you want control:

```sh
sshd-start            # start (idempotent); generates the host key on first use
sshd-stop             # stop
sshd-status           # running state, port, autostart arming
sshd-autostart on|off # arm/disarm starting it with new interactive sessions
```

Nothing starts unless you run `sshd-start` yourself or explicitly arm
`sshd-autostart on`. The autostart check itself lives in
`~/.config/fish/conf.d/personal.fish`, so you can delete or reshape it
there without a switch. Details:

- Port **8023** by default (the Termux edition's sshd conventionally
  owns 8022 on the same device); override by writing a number to
  `~/.config/sshd/port`.
- Key-only auth. Append your client's public key to
  `~/.ssh/authorized_keys`, then `ssh -p 8023 nix-on-droid@<phone-ip>`.
- The ed25519 host key lives in `~/.ssh/hostkeys` — generated on
  device, never part of the nix store.
- Server log: `~/.config/sshd/log`. The server does not survive a
  reboot or an app force-stop; reopen the app and it comes back with
  the next session if autostart is armed.
- If you run sshd by hand instead: it must be invoked by absolute path
  (it re-execs itself), needs `-o StrictModes=no` on Android, and a
  port above 1024.

### After a switch

- Open a **new session** to pick up the new login shell and PATH.
- `launcherctl` and `tai` live in the proot's `/bin`; the template's
  `config.fish` puts that on the PATH for fish sessions.
- Something broke? `nix-on-droid rollback` restores the previous
  generation.

## Everyday commands

Looking things up and trying things out — none of these change your
system:

```sh
nix search nixpkgs ripgrep         # find a package name
nix eval nixpkgs#ripgrep.version   # what version you would get
nix run nixpkgs#cowsay -- moo       # run once, install nothing
nix shell nixpkgs#nodejs            # temporary shell with node on PATH
nix build --dry-run nixpkgs#foo     # would it be fetched from cache, or built here?
```

That last one is worth a habit. Read its output for "will be fetched"
(prebuilt, seconds) versus "will be built" (compiles on the phone, which
for anything large means a very long time). Checking first is cheaper
than interrupting a switch halfway.

Note that `nixpkgs#...` in these commands resolves through the flake
registry, **not** the version pinned in your `flake.lock` — so a version
you see here can differ from what a switch installs.

## Declarative setup

Two files, two scopes:

- `home.nix` — your user: `home.packages`, dotfiles, activation hooks.
  This is where nearly everything you install goes.
- `nix-on-droid.nix` — the environment outside your home: the login
  shell, `/etc`, base packages every script assumes, Android glue.

```nix
# home.nix
home.packages = with pkgs; [
  fish
  neovim
  ripgrep
  eza
];
```

```nix
# nix-on-droid.nix
environment.packages = with pkgs; [ git curl ];
user.shell = "${pkgs.fish}/bin/fish";
```

Apply either with:

```sh
nix-on-droid switch --flake ~/.config/nix-on-droid
```

Roll back to the previous generation any time:

```sh
nix-on-droid rollback
```

## Shell config: which file to edit

`~/.config/fish/config.fish` is **read-only on purpose** — it is a
symlink into the nix store, owned by home-manager, and it carries only
what the launcher needs (PATH to `launcherctl`/`tai`, the wallpaper
Material palette, the themed prompt). Trying to edit it gives you a
read-only error.

Your settings go in `~/.config/fish/conf.d/personal.fish`, a normal
writable file the template drops in on first activation. Editor,
aliases, `sshd-autostart`, and the `ls`/`cd` helpers all live there, with
commented-out nix shortcuts ready to uncomment. Edit it and run
`exec fish` — no switch needed.

| Want to change | Edit | Then |
|---|---|---|
| your aliases, editor, keybinds | `~/.config/fish/conf.d/personal.fish` | `exec fish` |
| the launcher's own shell setup | `~/.config/nix-on-droid/config/config.fish` | switch |
| installed programs | `~/.config/nix-on-droid/home.nix` | switch |

fish reads `conf.d/*.fish` **before** `config.fish`, so a bare `set` in
`personal.fish` cannot override something `config.fish` sets afterwards
— use a function or an event handler for that.

Deleting `personal.fish` and switching again restores a fresh copy;
switches never overwrite it while it exists.

## Housekeeping

```sh
# reclaim disk from old generations and unused store paths
nix-collect-garbage --delete-old
```

## Differences from the Termux edition

| | Termux edition (`com.termux`) | Nix edition (`com.termux.launcher.nix`) |
|---|---|---|
| Package manager | `pkg` / APT | `nix` / `nix-on-droid` |
| Package source | Termux repos (bionic builds) | official `nixpkgs` (glibc builds, via proot) |
| Install command | `pkg install foo` | add to `home.nix`, then switch |
| Config-as-code | — | `home.nix` / `nix-on-droid.nix` + flakes |
| Rollbacks | — | `nix-on-droid rollback` |
| Termux:API tools | native | available; some tools need the `/android` prefix for system binaries |

## Tips and caveats

- Everything runs under proot: a small syscall-translation overhead
  applies, mostly noticeable in process-heavy workloads.
- Compiling large packages on-device works but is slow — prefer cached
  binaries, and keep the app in the foreground during long downloads or
  builds (Android cuts background network for the app).
- The Android system is visible at `/android` inside the environment.
- First-run bootstrap and the environment defaults come from this
  project's [nix-on-droid fork](https://github.com/PickleHik3/nix-on-droid).

Nix is a deep tool — the [official Nix manual](https://nix.dev) and the
[nix-on-droid wiki](https://github.com/nix-community/nix-on-droid/wiki)
cover the rest.
