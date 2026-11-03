# tlstore in the launcher

The store's own sources (its CLI, `tlstore-ui`, the catalog and its build pipeline) live in
[PickleHik3/tlstore](https://github.com/PickleHik3/tlstore). Send catalog and store-code changes
there; see that repo's `docs/maintainer/catalog.md` and `AGENTS.md`.

This launcher only pins the release it ships:

- `app/tlstore.lock` names a tlstore release tag and the sha256 of each `dist/` file. Gradle's
  `fetchTlstore` task downloads them into the APK's assets and verifies every hash.
- Bump the lock with the block tlstore's `scripts/release.sh` prints after a release there, and
  bump it whole: the engine and `tlstore-ui` are a pair from the same release.
- To build against a local tlstore checkout while developing tlstore itself, pass
  `-Ptlstore.local=<dir>` (or set `TLSTORE_LOCAL`) pointing at that checkout's `dist/`. This
  skips the lock check and prints a warning.

On a new APK, `TlstoreInstaller` writes the bundled engine and `tlstore-ui`, but never over a
tlstore the user has self-updated to something newer (it compares `TLSTORE_VERSION`). Testing a
bundled tlstore on a device that has self-updated therefore shows the newer one.

A store item that needs a launcher feature gates on `min-launcher=X.Y.Z` in the catalog, naming
the **release** the feature first ships in. The engine compares it with the running app's
`versionName`, so a `dev` build reports `dev`'s stale version and can hide an item the next
release will show.
