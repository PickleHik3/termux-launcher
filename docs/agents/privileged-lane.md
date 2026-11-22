# The privileged lane

`app/src/main/java/com/termux/privileged/lane/` lets a terminal program run as the shell uid
(2000). `PrivilegedLaneService` is a Shizuku UserService running as shell;
`PrivilegedLaneServer` listens on the abstract socket `@<packageName>.priv` and accepts only the
app's own uid. The client is `tl-priv` in the tlstore repo (PickleHik3/tlstore,
`recipes/cross/tl-priv/tl-priv.c`).

- **The wire protocol `tlpriv1` (`LaneRequest`) is a contract with `tl-priv`.** Change both sides
  in step, or neither.
- `LaneAllowlist` runs only catalog `binary` rows marked `priv=shizuku` whose sha256 matches the
  active tlstore catalog. It guards against accidents, not against code already running as this
  uid; do not describe it as a security boundary.
- Binaries are staged under `/data/local/tmp/tl`. The service runs with umask 0, so every
  directory there must be chmodded 0700 explicitly (`PrivilegedLaneService`), or the tree is
  world-writable.
- `LocalSocket` re-attaches queued fds to every later write. After handing over the pty master,
  call `setFileDescriptorsForSend(null)`, or the next write fails with EBADF and the client hangs.
- `getFilesDir()` answers `/data/user/0/<pkg>/files` while Termux paths say
  `/data/data/<pkg>/files`. They are one directory, not a symlink, so canonical paths never
  agree: compare by inode.
- The window chip shows `tl-priv run <path>` as the tool's name
  (`WindowForegroundResolver.unwrapLane`); a new way of launching through the lane needs it too.
- **The root lane** (`RootLane`) serves the same `IPrivilegedLane` on a rooted phone without
  Shizuku (or with Shizuku unusable). It lives in the launcher process, so calls are direct, and
  runs each tool through `su 2000 -c` on a pty the launcher allocates: uid 2000, never uid 0.
  The only uid 0 work is staging file operations (one root command: copy, `chown -R 2000:2000`
  on `/data/local/tmp/tl`, `chmod 0700` on the root and bin directories, `chmod 0755` on the
  binary). Full root for tools is not offered. Magisk and KernelSU keep `su 2000` in their own
  SELinux domain, so a tool may behave slightly differently than under Shizuku's shell domain.
  Shizuku is preferred whenever it is usable; when it binds later, new spawns go to it and the
  root lane's running children end on their own. The root lane cannot inject input, so the Home
  pull-down does not drive the shade through it. Build its command lines in `RootLaneCommands`
  (every value single-quoted) and keep them tested.
