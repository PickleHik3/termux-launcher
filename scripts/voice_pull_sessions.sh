#!/usr/bin/env bash
# Pulls the voice-debug recordings VoiceInputSession keeps in debug builds
# (cache/voice-debug/session-*.pcm, the whole session as the VAD sees it; segment-*.pcm, one
# segment each, 0-7 cycling) off the phone and wraps each in a WAV header at
# ~/.cache/termux-launcher/voice-clips/, ready for VoiceReplayRig (project-docs/plans/
# whisper-voice-input.md "Replay rig"). These recordings are the developer's own voice: never
# committed, never copied into the repo.
set -euo pipefail

: "${ANDROID_ADB_SERVER_PORT:=5038}"
: "${SERIAL:=100.101.173.85:5555}"
export ANDROID_ADB_SERVER_PORT

dst="${1:-$HOME/.cache/termux-launcher/voice-clips}"
mkdir -p "$dst"

adb() { command adb -s "$SERIAL" "$@"; }

files=$(adb exec-out run-as com.termux sh -c 'ls cache/voice-debug 2>/dev/null' | tr -d '\r' | grep -E '^(session|segment)-.*\.pcm$' || true)
if [ -z "$files" ]; then
  echo "no session-*.pcm or segment-*.pcm under cache/voice-debug on $SERIAL" >&2
  exit 1
fi

echo "$files" | while IFS= read -r f; do
  [ -z "$f" ] && continue
  name="${f%.pcm}"
  pcm="$dst/$name.pcm"
  wav="$dst/$name.wav"
  echo "pulling $f"
  adb exec-out run-as com.termux cat "cache/voice-debug/$f" > "$pcm"
  python3 - "$pcm" "$wav" <<'PY'
import struct, sys
pcm_path, wav_path = sys.argv[1], sys.argv[2]
with open(pcm_path, "rb") as f:
    data = f.read()
sample_rate, channels, bits = 16000, 1, 16
byte_rate = sample_rate * channels * bits // 8
block_align = channels * bits // 8
with open(wav_path, "wb") as f:
    f.write(b"RIFF")
    f.write(struct.pack("<I", 36 + len(data)))
    f.write(b"WAVEfmt ")
    f.write(struct.pack("<IHHIIHH", 16, 1, channels, sample_rate, byte_rate, block_align, bits))
    f.write(b"data")
    f.write(struct.pack("<I", len(data)))
    f.write(data)
PY
  rm -f "$pcm"
done

echo "wrote WAVs to $dst"
