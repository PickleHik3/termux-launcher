# HANDOFF — P3 tlstore-ui core (feat/tlstore-ui-core)
Goal: Rust crate tools/tlstore-ui (terminal, renderer, pictures, layout, app loop) + <=40-line contract for P4.
Done: all six modules, demo screen (`cargo run -- --demo`), 58 unit tests, clippy clean.
Next: scripts/tlstore/build-ui.sh (NDK 29, aarch64 + x86_64 android API 26), sizes, final report + contract.
Open: visuals unverified (no screen); launcher kitty placement X/Y/z/crop support assumed from the spec.
Last test: cargo test -> 58 passed; 0 failed
