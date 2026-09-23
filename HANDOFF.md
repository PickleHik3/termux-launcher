# P2 launcher — tlstore Revision 5

Goal: ship `tlstore-ui` from the APK and let it put the in-app keyboard away.
Done: `TlstoreInstaller` ships per-ABI assets (`tlstore/tlstore-ui-<abi>`) to
`$PREFIX/libexec/termux-launcher/tlstore/tlstore-ui`, foreign/no-asset/rewrite
handled like the existing files. `launcherctl keyboard hide --hold` records the
calling session (`KeyboardHoldTracker`); `show` always releases; a finished
session that was holding it gets the keyboard back automatically
(`TerminalActionDispatcher.onSessionFinished`, called from
`TermuxTerminalSessionActivityClient`). Doc: REVISION-5.md "Launcher hooks".
Next: nothing pending on this phase; P3 lands binaries at the documented asset
paths, P1/P4 call `keyboard hide --hold` / `keyboard show` from tlstore-ui.
Open questions: none.
Last test: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL.
