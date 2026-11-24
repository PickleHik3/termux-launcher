# Wayland host: vendored and patched upstream pieces

`wayland-host/` is the Wayland compositor behind Linux windows (`libtlwayland.so`, built by
`scripts/build-wayland-host.sh`, `-Pwayland=true`). Two upstream pieces are not taken as published.
The script fetches both, checks their sha256 and keeps them under `wayland-host/target/`; nothing
upstream is committed here.

## smithay 0.7.0

- Source: the crates.io tarball, pinned by `SMITHAY_VERSION` and `SMITHAY_SHA256` in the script.
- Patch: `patches/smithay-0.7.0-android-xwayland.patch` (each `patches/smithay-<version>-*.patch` is applied in turn), into `target/patched/` and wired
  in by the `[patch]` entry in `Cargo.toml`.
  - `xwayland/x11_sockets.rs`: the X lock file and `.X11-unix/` socket paths came from a
    hard-coded `/tmp`, which Android does not have. They now live under `$SMITHAY_X11_TMPDIR`
    (the app sets it to `$PREFIX/tmp`, where Termux's X clients look), else `/tmp`.
  - `xwayland/xserver.rs`: the Xwayland child sets `PR_SET_PDEATHSIG` to SIGKILL before exec, so
    it ends with its parent. A compositor that stops or crashes must not leave Xwayland holding
    its display.
- Patch: `patches/smithay-0.7.0-shm-resize.patch`.
  - `wayland/shm/handlers.rs`: `wl_shm_pool.resize` with a size of 0 or less posted a protocol
    error and then fell through to `NonZeroUsize::try_from(0).unwrap()`, which panicked the
    compositor thread: one client could end every Linux window. It now returns after the error.
    On a version bump, check upstream's `Request::Resize` before dropping it.
- Patch: `patches/smithay-0.7.0-xwayland-reap.patch` (applies after the Xwayland one).
  - `xwayland/xserver.rs`: `XWaylandClientData::reap_child()` kills and waits for Xwayland
    unless `disconnected` already took it, and `disconnected` no longer unwraps a child that is
    gone. A stopping compositor drops its display without dispatching Xwayland's disconnect, so
    the un-waited child would stay a zombie for the life of the process; `xwayland::stop` reaps it.
- Refresh: bump the version and sha256 in the script, re-cut the patch against the new tree
  (`diff -u` of the two files, paths `a/` and `b/`), rename it to the new version, run the script
  and `cargo test`. The `.patched` stamp re-applies the patch when its text changes.

## TODO (host review 2026-11-24, left open)

- **13, title and size callbacks are not merged.** Each title, app id or shown-size change is one
  JNI call, one `Handler.post` and one oneway transaction to the launcher. Titles are now cut at
  256 characters (`MAX_NAME_CHARS` in `host.rs`); still to do: keep only the latest pending
  change per window and send it on the frame tick, so a client retitling in a loop cannot flood
  the launcher's async binder space.
- **15, no per-client resource limits.** Each `wl_shm_pool` keeps an fd and a mapping of up to
  2 GB. Cap pools per client and total mapped bytes (an `ShmHandler`-side count, or a smithay
  patch in `wayland/shm/handlers.rs`), and kill the client over the cap: a client can otherwise
  use up the process's fds, or the address space of a 32-bit process.

## libxkbcommon 1.9.2

- smithay links it unconditionally (keyboard keymaps), and Android has none.
- Source: the `xkbcommon-1.9.2` GitHub tag tarball, pinned by `XKB_VERSION` and `XKB_SHA256`.
- Built by the script with meson as a static `libxkbcommon.a` per ABI (no tools, X11, Wayland or
  registry; `meson` from PATH, else `uvx`), into `target/xkbcommon/<abi>/`. The first build takes
  about ten minutes; later runs reuse it.
- The compiled-in config root is a dummy: the app sets `XKB_CONFIG_ROOT` at run time because the
  prefix differs per edition.
- The host needs the prefix's `xkeyboard-config` data (`share/X11/xkb`) for a keyboard and runs
  without one when it is absent: libxkbcommon then has a NULL context that smithay dereferences.
- Refresh: bump the version and sha256, delete `target/xkbcommon/`, run the script.
