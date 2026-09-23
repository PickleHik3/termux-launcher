# HANDOFF — P4 screens (feat/tlstore-ui-screens)
1. Goal: `tlstore-ui` with no args opens the real store (Apps, Item, Updates, Installing, NoGh), wired to the script, gh, launcherctl.
2. Code: tools/tlstore-ui/src/store/ (mod.rs Router+Store+Job, scene.rs motion hooks, paint.rs shared frame, apps/item/updates/installing/nogh.rs, data.rs TSV, proc.rs tasks).
3. Core touched: render OSC 8 links (Buffer::link, Frame::link, diff link pass) — needed for the upstream link.
4. Tests: tests/screens.rs + stubs in tests/fixtures/store/bin; snapshots tests/snapshots (UPDATE_SNAPSHOTS=1 rewrites).
5. Command: cd tools/tlstore-ui && ~/.cargo/bin/cargo test && ~/.cargo/bin/cargo clippy --all-targets
6. Script gap: no `tlstore picture <name> [demo]` yet; UI calls it and falls back to text stand-ins.
7. Done: all screens, stars, fullscreen, progress, OSC 99, snapshots at 52x45/52x23/40x34.
8. Next: build-ui.sh sizes, P5 contract in report.
9. Base: dev 56e9bd98. Never push/merge.
10. Status: see git log.
