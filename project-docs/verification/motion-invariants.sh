#!/usr/bin/env bash
# Fails when the motion log recorded a broken invariant.
# Usage: motion-invariants.sh <adb serial>
# Turn Settings > Debugging > Motion log on, drive the page changes, then run this.
set -u
serial="${1:?usage: motion-invariants.sh <adb serial>}"
lines=$(adb -s "$serial" logcat -d -s TermuxMotion | grep ' INVARIANT ' || true)
if [ -n "$lines" ]; then
    echo "$lines"
    echo "motion-invariants: $(echo "$lines" | wc -l) violation(s)" >&2
    exit 1
fi
echo "motion-invariants: none"
