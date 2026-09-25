# Tlstore

Tlstore is the launcher's own package manager for the tools and configs it shows off but does not
ship in the APK — a fish shell setup, a few terminal programs, and Claude Code, all installed and
kept up to date by one command. The app puts `tlstore` in place for you, along with the shorter
`tl` and `tls`, so all three names run the same store; on plain Termux (no launcher) one command
installs it the same way.

Tlstore's own docs — the full command list, what's in the store, how config files are handled, and
how it decides what to offer inside the launcher versus plain Termux — live with its source:

**[PickleHik3/tlstore: docs/user/Tlstore.md](https://github.com/PickleHik3/tlstore/blob/main/docs/user/Tlstore.md)**

This launcher only pins the tlstore release it ships (see `app/tlstore.lock` and `AGENTS.md`); the
store's design, catalog and code changes happen in that repository.
