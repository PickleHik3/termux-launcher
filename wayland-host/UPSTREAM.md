# Wayland host: vendored and patched upstream pieces

`wayland-host/` is the Wayland compositor behind Linux windows (`libtlwayland.so`, built by
`scripts/build-wayland-host.sh`, `-Pwayland=true`). Two upstream pieces are not taken as published.
The script fetches both, checks their sha256 and keeps them under `wayland-host/target/`; nothing
upstream is committed here.

## smithay 0.7.0

- Source: the crates.io tarball, pinned by `SMITHAY_VERSION` and `SMITHAY_SHA256` in the script.
- Patch: `patches/smithay-0.7.0-android-xwayland.patch`, applied into `target/patched/` and wired
  in by the `[patch]` entry in `Cargo.toml`.
  - `xwayland/x11_sockets.rs`: the X lock file and `.X11-unix/` socket paths came from a
    hard-coded `/tmp`, which Android does not have. They now live under `$SMITHAY_X11_TMPDIR`
    (the app sets it to `$PREFIX/tmp`, where Termux's X clients look), else `/tmp`.
  - `xwayland/xserver.rs`: the Xwayland child sets `PR_SET_PDEATHSIG` to SIGKILL before exec, so
    it ends with its parent. A compositor that stops or crashes must not leave Xwayland holding
    its display.
- Refresh: bump the version and sha256 in the script, re-cut the patch against the new tree
  (`diff -u` of the two files, paths `a/` and `b/`), rename it to the new version, run the script
  and `cargo test`. The `.patched` stamp re-applies the patch when its text changes.

## libxkbcommon 1.9.2

- smithay links it unconditionally (keyboard keymaps), and Android has none.
- Source: the `xkbcommon-1.9.2` GitHub tag tarball, pinned by `XKB_VERSION` and `XKB_SHA256`.
- Built by the script with meson as a static `libxkbcommon.a` per ABI (no tools, X11, Wayland or
  registry; `meson` from PATH, else `uvx`), into `target/xkbcommon/<abi>/`. The first build takes
  about ten minutes; later runs reuse it.
- The compiled-in config root is a dummy: the app sets `XKB_CONFIG_ROOT` at run time because the
  prefix differs per edition.
- Refresh: bump the version and sha256, delete `target/xkbcommon/`, run the script.
