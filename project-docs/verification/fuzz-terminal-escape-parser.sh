#!/usr/bin/env bash
# Run the reproducible JVM escape-parser mutation harness.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
seed="${1:-$(date +%s)}"
cases="${2:-5000}"
max_bytes="${3:-4096}"

printf 'escape fuzz: seed=%s cases=%s max_bytes=%s\n' "$seed" "$cases" "$max_bytes"
cd "$repo_root"
./gradlew :terminal-emulator:testDebugUnitTest \
    --tests com.termux.terminal.EscapeSequenceFuzzTest \
    -Dtermux.fuzz.seed="$seed" \
    -Dtermux.fuzz.cases="$cases" \
    -Dtermux.fuzz.maxBytes="$max_bytes"
