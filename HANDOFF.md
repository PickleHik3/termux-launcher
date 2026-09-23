# HANDOFF — P5 motion (feat/tlstore-ui-motion, from dev 417141fa)
Goal: Flow's transitions through the Motion hook; TLSTORE_MOTION=0 turns them off.
Done: src/store/motion.rs (Timeline + ease::{spring,out,dram} + timing), Router::animated
  (main.rs uses it), Router::set_clock, Motion::drawn (post-draw redraw hook), Effect.line/value,
  Scene.cell, hidden pictures still recorded, installing count-up reads Effect.value.
Leaving view's scene no longer overwrites Router.scene (draw_view `leaving`).
Next: tests (easing, Flow samples, leave drop, motion off, frame bytes, plain path), clippy,
  CONTRACT.md P5 notes, build-ui.sh --install, checkTlstoreUiFresh.
Launcher animations-off: not exported to the shell (ReducedMotion.java reads
  ANIMATOR_DURATION_SCALE in-app only) — report, rely on TLSTORE_MOTION.
Tests: cd tools/tlstore-ui && ~/.cargo/bin/cargo test  (83 existing green at WIP 1)
