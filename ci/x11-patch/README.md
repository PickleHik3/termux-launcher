# Native patches applied to termux-x11 before building `libXlorie.so`

`.github/workflows/build_x11_native.yml` applies every `*.patch` in this directory to a fresh
checkout of `termux/termux-x11` (at the commit pinned in `x11-server/UPSTREAM.md`) with
`git apply`, in filename order, before running CMake. `../x11-local-build.sh` does the same.

Almost nothing needs to change in the X server core to run it inside the launcher. Add a patch
here only for something that genuinely has to, and say in its header why the Java side could not
do it instead. What is here now is one rename on the JNI surface, which names the host class the
launcher does not have.
