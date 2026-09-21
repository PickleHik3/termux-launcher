# feat/kitty-file-medium

Goal: kitty graphics accepts t=f (file) and t=t (temp file) transmission so plugins that
transmit images by path (md-render.nvim) render in Termux Launcher.

Done: KittyGraphicsProtocol reads the named file (O=/S= window), t=t validated against kitty's
temp rule and deleted after reading, query path too; t=s still ENOSYS. 7 new unit tests.
Test: :terminal-emulator:testDebugUnitTest = 369 tests, 0 failures (52 in KittyGraphicsProtocolTest).

Next: build :app:assembleDebug, install on Waydroid, prove md-render.nvim draws a PNG.

Blockers: none.

Hashes: built on dev 1cb6a06a.
