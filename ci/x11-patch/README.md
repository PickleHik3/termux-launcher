# Native patches applied to termux-x11 before building `libXlorie.so`

`.github/workflows/build_x11_native.yml` applies every `*.patch` in this directory to a fresh
checkout of `termux/termux-x11` (at the commit pinned in `x11-server/UPSTREAM.md`) with
`git apply`, in filename order, before running CMake. `../x11-local-build.sh` does the same.

The directory is empty on purpose: the X server core needs no changes to run inside the
launcher. Everything the launcher does differently lives in the vendored Java under
`x11-server/`, which is where deviations are recorded. Add a patch here only for something that
genuinely has to change inside the C server, and say in its header why the Java side could not
do it.
