#!/usr/bin/env bash
# Fetches the STT bench's fixed clips, converts them to 16 kHz mono PCM16 WAV and checks sha256
# against the committed manifests. Usage: fetch-clips.sh [out-dir]   (default: ./clips)
# Needs curl, ffmpeg, sha256sum. See README.md.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out=${1:-clips}
mkdir -p "$out/src"
ua="stt-bench/1.0 (termux-launcher)"

fetch() { # url file
  [ -s "$out/src/$2" ] || curl -fsSL -A "$ua" -o "$out/src/$2" "$1"
}
# whisper.cpp commit 4da27c41, samples/jfk.wav (JFK, US government work)
fetch "https://raw.githubusercontent.com/ggml-org/whisper.cpp/4da27c41cebadb3cbbcd2563e9c7cbc8d84bdcad/samples/jfk.wav" jfk.wav
# Wikimedia Commons, George W. Bush's weekly radio address, 1 Nov 2008 (US government work)
fetch "https://upload.wikimedia.org/wikipedia/commons/2/22/George_W._Bush%27s_weekly_radio_address_%28November_1%2C_2008%29.oga" bush-radio-address-2008-11-01.oga

grep -v '^#' "$here/sources.sha256" | (cd "$out/src" && sha256sum -c -) \
  || { echo "source audio changed upstream; clips would differ" >&2; exit 1; }

conv() { # src dst [ffmpeg trim args...]
  local src=$1 dst=$2; shift 2
  ffmpeg -v error -y -i "$out/src/$src" "$@" -ac 1 -ar 16000 -sample_fmt s16 \
    -fflags +bitexact -flags:a +bitexact -map_metadata -1 "$out/$dst"
}
conv jfk.wav stt-3s-jfk.wav -ss 0 -t 3
conv jfk.wav stt-11s-jfk.wav
conv bush-radio-address-2008-11-01.oga stt-45s-bush.wav -ss 0 -t 45

if grep -v '^#' "$here/clips.sha256" | (cd "$out" && sha256sum -c -); then
  echo "clips OK in $out"
else
  echo "converted clips differ from the manifest (sources matched, so this ffmpeg/resampler differs);" >&2
  echo "the bench still runs, but compare numbers only against runs on the same clip hashes." >&2
  exit 2
fi
