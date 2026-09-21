# tlstore core phase — handoff

Done: host gating (rows(), TLSTORE_HOST/TERM_PROGRAM/.installed), min-launcher,
  --tsv on list/search/info/update --check, browse + fallbacks, self-update hook,
  doctor App line, TLSTORE_VERSION 0.2, tests (194 passed under /bin/sh).
In progress: items.tsv host=launcher on both fastfetch rows, motd.sh, docs/en/Tlstore.md.
Next: rebuild catalog.tsv if build-catalog.sh runs offline; final commit; drop this file.
Gotchas: no shellcheck and no pip on this host; only /bin/sh (bash) — no dash/busybox.
  The test knobs reset after every tl() call; set them again for each call.
  RAW_BASE default is $LAUNCHER_RAW/main/app/src/main/assets/tlstore (where tlstore,
  catalog.tsv and trusted.pub actually live) — the installer phase must match it.
