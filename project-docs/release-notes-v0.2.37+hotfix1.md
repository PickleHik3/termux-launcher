# Changelog — v0.2.37+hotfix1

A hotfix for installs that Android will not let run their own programs. Same
app as [v0.2.37](https://github.com/PickleHik3/termux-launcher/releases/tag/v0.2.37)
otherwise.

## Fixes

- When Android has put the app in a state where it is not allowed to run
  anything it installs, it now says so, tells you a full uninstall and reinstall
  is what clears it, and names any app sharing its user id that caused it.
  Before, this looked like a failed download and every launch fetched the
  bootstrap again to fail the same way.
- The bootstrap directory is kept when that happens instead of being deleted,
  so a working install is not thrown away.
