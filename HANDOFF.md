# feat/kitty-file-medium

Goal: kitty graphics accepts t=f (file) and t=t (temp file) so plugins that transmit images
by path (md-render.nvim) render in Termux Launcher.

Done: KittyGraphicsProtocol reads the named file (O=/S= window), on the transmit and the query
path; t=t is deleted after reading when kitty's temp rule allows, otherwise read and left alone;
t=s still ENOSYS. 7 new unit tests.
Test: :terminal-emulator:testDebugUnitTest = 369 tests, 0 failures (52 in KittyGraphicsProtocolTest).
Device: Waydroid, md-render.nvim pager draws the PNG inline (screenshot md-render-waydroid.png);
a hand probe shows both mediums and the t=t deletion; logcat clean.

Next: orchestrator merges; consider whether TERM_PROGRAM should be exported by the launcher.

Blockers: none.

Hashes: dev 1cb6a06a -> 4ab388e2, 5320558b.
