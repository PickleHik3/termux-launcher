#!/usr/bin/env bash
# launcher-media.sh — tell the launcher what Linux media players and sound streams are doing.
#
# Installed as $PREFIX/libexec/termux-launcher/sound/termux-launcher-media. The launcher starts
# it beside its own sound server and session bus while "Media controls" is on, and starts it
# again when it ends; it is not meant to be run by hand. The environment names the launcher's
# server (PULSE_SERVER) and bus (DBUS_SESSION_BUS_ADDRESS). It needs bash and launcherctl, and
# for each of its two parts the programs that part uses: pactl for the streams, dbus-monitor and
# dbus-send for the players. A part whose programs are missing is left out.
#
# What it does:
#   1. Streams: counts the streams playing through the server (not corked), once at the start
#      and again on every sink-input event `pactl subscribe` prints, and reports a changed count
#      as `launcherctl media streams N`. The launcher holds Android's audio focus while it is
#      above zero, MPRIS or not.
#   2. Players: watches MPRIS players (org.mpris.MediaPlayer2.*) come, change and go with
#      dbus-monitor. For each change it reports the player's whole state with
#      `launcherctl media player NAME PID`, sending dbus-send's printed reply to
#      Properties.GetAll as it is (the launcher reads it; nothing here parses D-Bus), a player
#      going with `launcherctl media gone NAME` and a jump with `launcherctl media seeked NAME US`.
#      PID is the process that owns the name, so the launcher can find a distro player's art.
#   3. Leaves as soon as either watcher ends — the server or the bus went away — and the
#      launcher starts it again.
#
# Event-driven throughout: it only ever waits on a pipe, apart from a bounded wait at the start
# for the server and the bus to answer. It writes what it did to stderr, which the launcher reads.

set -u

MPRIS=org.mpris.MediaPlayer2
MPRIS_PATH=/org/mpris/MediaPlayer2
BUS=org.freedesktop.DBus
BUS_PATH=/org/freedesktop/DBus

say() { echo "termux-launcher-media: $*" >&2; }
has() { command -v "$1" > /dev/null 2>&1; }

WATCHERS=()
cleanup() {
  local pid
  for pid in "${WATCHERS[@]}"; do kill "$pid" 2> /dev/null; done
}
trap cleanup EXIT
trap 'exit 0' TERM INT HUP

if ! has launcherctl; then
  say "launcherctl is not installed; nothing to report to"
  exit 0
fi

# Waits up to $1 tenths of a second for the test that follows; false if it never passes.
within() {
  local tries=$1 n=0
  shift
  until "$@"; do
    n=$((n + 1))
    [ "$n" -le "$tries" ] || return 1
    sleep 0.1
  done
}

# ---- 1. Streams ---------------------------------------------------------------------------------

server_answers() { pactl info > /dev/null 2>&1; }
streams_playing() { LC_ALL=C pactl list sink-inputs 2> /dev/null | grep -c 'Corked: no'; }

watch_streams() {
  local last="" now line
  while :; do
    now=$(streams_playing)
    if [ "$now" != "$last" ]; then
      last=$now
      launcherctl media streams "$now" > /dev/null 2>&1
    fi
    # The next sink-input event; the end of the subscription ends the watch.
    while :; do
      IFS= read -r line || return 0
      case "$line" in
        *"on sink-input"*) break ;;
      esac
    done
  done
}

if has pactl && [ -n "${PULSE_SERVER:-}" ] && within 100 server_answers; then
  exec 3< <(LC_ALL=C exec pactl subscribe 2> /dev/null)
  WATCHERS+=("$!")
  watch_streams <&3 &
  WATCHERS+=("$!")
  exec 3<&-
  say "watching streams"
else
  say "no sound server to watch"
fi

# ---- 2. Players ---------------------------------------------------------------------------------

bus_call() {
  dbus-send --print-reply=literal --reply-timeout=2000 --dest="$BUS" "$BUS_PATH" "$BUS.$1" "${@:2}" \
    2> /dev/null
}
bus_answers() { bus_call GetId > /dev/null; }

# NAME_OF[unique name] = the MPRIS name it owns.
declare -A NAME_OF

# The player's state, sent as dbus-send printed it.
report_player() {
  local name=$1 reply pid
  reply=$(dbus-send --print-reply --reply-timeout=2000 --dest="$name" "$MPRIS_PATH" \
    org.freedesktop.DBus.Properties.GetAll "string:$MPRIS.Player" 2> /dev/null) || return 0
  pid=$(bus_call GetConnectionUnixProcessID "string:$name")
  pid=${pid##* }
  case "$pid" in '' | *[!0-9]*) pid=0 ;; esac
  printf '%s\n' "$reply" | launcherctl media player "$name" "$pid" > /dev/null 2>&1
}

# Learns who owns $1 now and reports it.
track() {
  local owner
  owner=$(bus_call GetNameOwner "string:$1")
  owner=${owner//[[:space:]]/}
  [ -n "$owner" ] && NAME_OF[$owner]=$1
  report_player "$1"
}

# Every MPRIS name on the bus now: after a restart the launcher starts from nothing.
track_all() {
  local name
  for name in $(bus_call ListNames); do
    case "$name" in "$MPRIS".*) track "$name" ;; esac
  done
}

# The last word of a printed argument line, without its quotes: `string "x"` gives x.
arg_value() {
  local value=${1#"${1%%[![:space:]]*}"}
  value=${value#* }
  value=${value#\"}
  value=${value%\"}
  printf '%s' "$value"
}

watch_players() {
  local line member sender name args=()
  track_all
  while IFS= read -r line; do
    case "$line" in
      signal\ *)
        member=${line##*member=}
        sender=${line#* sender=}
        sender=${sender%% *}
        args=()
        if [ "$member" = PropertiesChanged ]; then
          name=${NAME_OF[$sender]:-}
          # A player that came before the watch did: look its name up again.
          [ -n "$name" ] || { track_all; continue; }
          report_player "$name"
        fi
        ;;
      *)
        args+=("$(arg_value "$line")")
        case "$member" in
          NameOwnerChanged)
            [ "${#args[@]}" -eq 3 ] || continue
            name=${args[0]}
            [ -n "${args[1]}" ] && unset "NAME_OF[${args[1]}]"
            if [ -n "${args[2]}" ]; then
              NAME_OF[${args[2]}]=$name
              report_player "$name"
            else
              launcherctl media gone "$name" > /dev/null 2>&1
            fi
            member=""
            ;;
          Seeked)
            name=${NAME_OF[$sender]:-}
            [ -n "$name" ] && launcherctl media seeked "$name" "${args[0]}" > /dev/null 2>&1
            member=""
            ;;
        esac
        ;;
    esac
  done
}

if has dbus-monitor && has dbus-send && [ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ] \
    && within 100 bus_answers; then
  # The watch starts before the first look, so nothing that happens in between is missed.
  exec 4< <(exec dbus-monitor --session \
    "type='signal',sender='$BUS',interface='$BUS',member='NameOwnerChanged',arg0namespace='$MPRIS'" \
    "type='signal',interface='org.freedesktop.DBus.Properties',member='PropertiesChanged',path='$MPRIS_PATH'" \
    "type='signal',interface='$MPRIS.Player',member='Seeked',path='$MPRIS_PATH'" 2> /dev/null)
  WATCHERS+=("$!")
  watch_players <&4 &
  WATCHERS+=("$!")
  exec 4<&-
  say "watching players"
else
  say "no session bus to watch"
fi

# ---- 3. Until a watcher ends ----------------------------------------------------------------------

if [ "${#WATCHERS[@]}" -eq 0 ]; then
  say "nothing to watch"
  exit 1
fi
wait -n
say "a watcher ended"
