# Native patches applied to termux-x11 before building `libXlorie.so`

`.github/workflows/build_x11_native.yml` applies every `*.patch` in this directory to a fresh
checkout of `termux/termux-x11` (at the commit pinned in `x11-server/UPSTREAM.md`) with
`git apply`, in filename order, before running CMake. `../x11-local-build.sh` does the same.

Almost nothing needs to change in the X server core to run it inside the launcher. Add a patch
here only for something that genuinely has to, and say in its header why the Java side could not
do it instead. Three things are here:

- `0001-look-up-the-host-class-as-LorieHost.patch` — a rename on the JNI surface, which names a
  host class the launcher does not have.
- `0002-forward-the-cursor-name-to-the-host.patch` — a new event carrying the name of the cursor
  the X pointer is showing. The server keeps that name in its own atom table and publishes it only
  to X clients; the host is not one.
- `0003-name-core-glyph-cursors.patch` — names the cursors a client without an Xcursor theme makes
  from the core "cursor" font, which otherwise reach 0002 with no name at all. The names are the
  shapes of `X11/cursorfont.h`, the same ones libXcursor uses, so `xterm` means a text caret
  whether or not a theme is installed.

A patch belongs in lorie's own sources. The sixteen freedesktop trees under
`lorie/src/main/cpp/` are submodules that termux-x11's CMake patches itself at configure time, so
a hunk of ours in one of them is a second uncontrolled edit, re-fetched away on the next bump —
0003's header shows what avoiding that costs: a dispatch-table wrapper instead of three lines in
dix.

Order matters: 0002 extends the same `nativeInit` block 0001 renames, so it applies only after it,
and 0003 edits the file 0002 has just added to.

A patch that adds a JNI call also needs its Java method in the same commit — the native side
resolves methods with `FindMethodOrDie`, which kills the process when one is missing.
