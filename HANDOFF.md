# HANDOFF — P3 tlstore-ui core (feat/tlstore-ui-core)
Goal: Rust crate tools/tlstore-ui (terminal, renderer, pictures, layout, app loop) + <=40-line contract for P4.
Done: all six modules, demo, build-ui.sh (arm64 612784 B, x86_64 667576 B), pty smoke runs (quit, SIGTERM, Esc, resize) restore cleanly.
Next: hand off; P4 builds screens on the contract in the report.
Open: visuals unverified (no screen); launcher kitty placement X/Y/z/crop support assumed from the spec.
Last test: cargo test -> 58 passed; 0 failed
