# Native patches applied to termux-x11 before building `libXlorie.so`

`.github/workflows/build_x11_native.yml` applies every `*.patch` in this directory to a fresh
checkout of `termux/termux-x11` (at the commit pinned in `x11-server/UPSTREAM.md`) with
`git apply`, in filename order, before running CMake. `../x11-local-build.sh` does the same.

Almost nothing needs to change in the X server core to run it inside the launcher. Add a patch
here only for something that genuinely has to, and say in its header why the Java side could not
do it instead. Two things are here:

- `0001-look-up-the-host-class-as-LorieHost.patch` — a rename on the JNI surface, which names a
  host class the launcher does not have.
- `0002-forward-the-cursor-name-to-the-host.patch` — a new event carrying the name of the cursor
  the X pointer is showing. The server keeps that name in its own atom table and publishes it only
  to X clients; the host is not one.

Order matters: 0002 extends the same `nativeInit` block 0001 renames, so it applies only after it.
A patch that adds a JNI call also needs its Java method in the same commit — the native side
resolves methods with `FindMethodOrDie`, which kills the process when one is missing.
