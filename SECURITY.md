# Security policy

## Supported versions

Only the latest release of each edition (Termux, Nix, VAJ) gets security fixes. Nightly builds
(`X.Y.Z+dev.<sha>`) are test builds; a fix lands there first and ships in the next release.

## Reporting a vulnerability

Report it privately through GitHub: open the repository's **Security** tab and choose
**Report a vulnerability**. Please do not open a public issue for it.

Include:
- the version and edition from Settings → About,
- the Android version and device model,
- the steps to reproduce, and what an attacker gains.

## Scope

In scope: this launcher, its `launcherctl` API and privileged lane, and the companion apps built
from the PickleHik3 forks.

Out of scope, report them upstream instead:
- Termux packages and the apt repository: [termux/termux-packages](https://github.com/termux/termux-packages).
- Bugs that also exist in upstream Termux: [termux.dev/security](https://termux.dev/security).

Known and intended: every published APK is signed with the repository's shared debug key
(`testkey_untrusted.jks`), so that the companion apps can share the launcher's `sharedUserId`.
Treat builds from this repository as unverified, and install them only from its GitHub releases.
