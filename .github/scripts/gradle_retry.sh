#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: gradle_retry.sh <command> [args...]" >&2
  exit 64
fi

max_attempts="${GRADLE_MAX_ATTEMPTS:-5}"
base_sleep_seconds="${GRADLE_RETRY_SLEEP_SECONDS:-20}"
refresh_from_attempt="${GRADLE_REFRESH_FROM_ATTEMPT:-2}"

attempt=1
last_exit_code=1

while [ "$attempt" -le "$max_attempts" ]; do
  echo "Gradle attempt ${attempt}/${max_attempts}"

  cmd=("$@")
  if [ "$attempt" -ge "$refresh_from_attempt" ]; then
    cmd+=(--refresh-dependencies)
  fi

  # Read the status on the failing branch itself: after `if ...; fi` with no else, $? is the
  # if-statement's own 0, which made a run whose every attempt failed exit green.
  if "${cmd[@]}"; then
    exit 0
  else
    last_exit_code=$?
  fi
  if [ "$attempt" -ge "$max_attempts" ]; then
    break
  fi

  sleep_for=$((base_sleep_seconds * attempt))
  echo "Gradle command failed (exit ${last_exit_code}); retrying in ${sleep_for}s"
  sleep "$sleep_for"
  attempt=$((attempt + 1))
done

echo "Gradle command failed after ${max_attempts} attempts." >&2
exit "$last_exit_code"
