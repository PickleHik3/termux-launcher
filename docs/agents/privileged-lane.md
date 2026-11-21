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
