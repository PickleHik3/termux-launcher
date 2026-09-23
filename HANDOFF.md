# HANDOFF — feat/tlstore-r5-picture

Status: all three asks done, `scripts/tlstore/test.sh` green (662 passed, 0 failed,
1 skip = nvim, under sh + bash --posix; dash/busybox not installed on this box, not
run here — code is plain POSIX sh, no new bashisms).

1. `tlstore picture <name> [demo]` — app/src/main/assets/tlstore/tlstore (cmd_picture,
   cache_fetch, string_digest, near cmd_info). Digest-verified, cached under
   $CACHE_DIR/pictures, keyed by digest (demo has none, so keyed by URL hash instead —
   judgement call, see final report).
2. Safe cancel — CANCEL_TMP/CANCEL_EXTRA/CANCEL_ITEM + on_cancel() trap on TERM,
   wired through download() and do_npm_musl()'s .new dir, and the three --progress
   item loops (cmd_install/cmd_remove/update_items).
3. item-spread-rules.md slots 1-3 rewritten to match item.rs/paint.rs/layout.rs
   (hero word above the cover, lead line carries No. NN · CATEGORY, line under
   cover is installed-state/upstream).

Not signed (maintainer signs at merge): app/src/main/assets/tlstore/tlstore.
