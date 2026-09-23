# P6a ship plumbing — HANDOFF

Status: done, committed, not merged/pushed.

1. Shape (a): committed binaries, staleness gated by a hash. `scripts/tlstore/ui-src-hash.sh`
   hashes tools/tlstore-ui's src+Cargo.{toml,lock}; `build.rs` bakes it into the binary as
   `TLSTORE_UI_SRC_HASH=<hex>` (one grep-able literal via `concat!`, since a device-ABI binary
   can't run on the build host); `app:checkTlstoreUiFresh` (app/build.gradle) recomputes and
   compares, wired into `check` and `testDebugUnitTest` via afterEvaluate.
2. `build-ui.sh --install` copies dist/<abi>/tlstore-ui into
   app/src/main/assets/tlstore/tlstore-ui-<abi>. Built + committed both ABIs.
3. TlstoreInstaller.install(): the UP_TO_DATE branch now also calls installTlstoreUi()
   (its own sha256 logic already tells changed/unchanged/foreign apart). Never overwrites the
   CLI/motd/catalog outside a marker mismatch, unchanged.
4. Bumped TLSTORE_VERSION 0.3 -> 0.4 in the tlstore script. tlstore.minisig now stale — needs
   re-signing with the maintainer's key (not mine to do). catalog.tsv/.minisig untouched (no
   version reference).

Verified: :app:testDebugUnitTest green (5713/0, incl. TlstoreInstallerTest 17/0),
:app:assembleDebug green, universal + arm64-v8a APKs both carry tlstore-ui-arm64-v8a and
tlstore-ui-x86_64. checkTlstoreUiFresh demonstrated failing on a source touch, then passing
again after revert.

Unverified: device install/refresh behavior (out of scope, no device testing).
