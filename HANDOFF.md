# HANDOFF — P5 motion (feat/tlstore-ui-motion, from dev 417141fa)
Goal: Flow's transitions through the Motion hook; TLSTORE_MOTION=0 turns them off.
State: done. src/store/motion.rs (Timeline, ease, timing, decode); Router::animated (main.rs),
  Router::set_clock, Motion::drawn redraw hook, Effect.line/value, Scene.cell, Frame::clear.
Tests: cd tools/tlstore-ui && ~/.cargo/bin/cargo test → 67 lib + 30 screens green; clippy clean.
Binaries rebuilt (build-ui.sh --install arm64-v8a x86_64), hash 135c8553…; checkTlstoreUiFresh passes.
Contract: tools/tlstore-ui/CONTRACT.md, P5 section at the end.
Launcher animations-off is not exported to shell programs (ReducedMotion.java is in-app only);
  only TLSTORE_MOTION=0 is honoured. Needs a launcher-side export to go further.
Unverified: anything on a real screen (no device/emulator used).
