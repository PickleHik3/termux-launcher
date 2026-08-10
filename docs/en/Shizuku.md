# Shizuku

Shizuku is optional. The launcher, terminal, dock, panes, workspaces, and app launching all work
without it.

## What the launcher uses Shizuku for

The launcher prefers Shizuku when it needs to read system information that Android normally hides
from apps:

- **CPU and process details:** the CPU/RAM values in the top status pane open a mini system-monitor
  card. Shizuku lets it read CPU and per-core activity, load average, detailed memory fields, and a
  real list of processes with CPU and memory use.
- **Window pill details:** Shizuku lets the launcher inspect the foreground process in each terminal
  pane. Window pills can then show a process name or an editor's open file and use measured CPU
  activity for the working indicator.
- **A-Z row screen lock:** when **Double tap A-Z Row to lock screen** uses the **Shizuku** method,
  the launcher sends a system sleep key event. This keeps the device's normal screen-off animation.

These features use the launcher's privileged backend. If configured, `su` or `rish` can provide a
shell fallback for the system monitor and window-pill inspection when Shizuku is unavailable. The
Shizuku A-Z lock method itself does not use that fallback; choose the Accessibility lock method if
you want an alternative.

Without any privileged backend, basic memory use still comes from Android and continues to update.
The launcher also tries to read world-readable `/proc` files directly. On devices that block those
reads, CPU can show `--`, old readings are marked `stale`, and the process list is absent. Window
pills fall back to their normal title or working-directory label, while the working indicator uses
sustained terminal output when possible.

## What Shizuku is

Shizuku lets an app use selected system APIs with the privileges of ADB or root, after you approve
that app. Root is not required: on supported Android versions, Shizuku can start through Wireless
debugging instead.

## Setting it up

1. Install the Shizuku app.
2. Start its service through Wireless debugging or root. Follow the
   [official Shizuku setup guide](https://shizuku.rikka.app/guide/setup/) for the current steps.
3. In Termux Launcher, open **Settings → Services & permissions → Shizuku**.
4. Choose **Request Shizuku permission** and approve the Shizuku dialog that appears over the
   launcher.

Opening the launcher's Shizuku settings page checks the connection and shows its backend state. To
request the dialog again, return to that page and choose **Request Shizuku permission**. If you
previously denied the request and no dialog appears, grant or reset Termux Launcher's authorization
in the Shizuku app, then return to the launcher and request it again.

## rish in the terminal

`rish` is Shizuku's shell helper. It gives commands inside the terminal an ADB-privileged shell,
which can read system process data and run tools outside Termux's app sandbox. The repository's
[`setup-btop-rish`](examples/setup-btop-rish) example installs `btop` under `/data/local/tmp` and
creates the `btop-shizuku` and `mini-btop-shizuku` wrapper commands.

## Troubleshooting

### Shizuku is unavailable after a reboot

A Wireless debugging start normally does not survive a device reboot. Start Shizuku again, then
open **Settings → Services & permissions → Shizuku** so the launcher re-checks the service. Until it
returns, the launcher uses `su` or `rish` if shell fallback is enabled; otherwise it uses the
unprivileged behavior described above, and the Shizuku A-Z lock method does nothing.

### Permission is denied or the prompt does not appear

Open the Shizuku app and check Termux Launcher's authorization. Grant it there, or reset a previous
denial, then use **Settings → Services & permissions → Shizuku → Request Shizuku permission**. Also
make sure **Enable privileged features** and **Prefer Shizuku backend** are enabled on that page.

### The stats card shows `--`, `stale`, or no processes

CPU percentages need two successful samples to calculate a change, and the process list is sampled
only while the CPU/RAM detail card is open, so first leave the card open for a few seconds. If it
does not recover, check that the Shizuku page reports `SHIZUKU · READY`. A missing privileged read
leaves basic Android memory available but may leave CPU as `--`, mark retained values `stale`, and
hide an empty process section. Restart Shizuku, re-check permission, then use **Settings → Advanced
& diagnostics → Privileged-backend test** if the page says it is ready but reads still fail.
