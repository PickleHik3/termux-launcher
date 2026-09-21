Done: sign-catalog.sh -> sign.sh (git mv), now signs tlstore script too
  (tlstore.minisig, trusted comment "tlstore <version>"); SPEC.md + Tlstore.md
  references updated.
In progress: writing scripts/tlstore/install.sh (standalone curl-pipe installer).
Next: install.sh, then scripts/tlstore/test-install.sh, then docs/en/Tlstore.md
  "On official Termux" section.
Gotchas: build-catalog.sh:22,147 still say sign-catalog.sh (left alone, not in
  my file list, owned by core phase). Do not touch tlstore/test.sh/items.tsv/
  catalog.tsv/motd.sh (sibling agent). sign.sh untested against real minisign
  yet (test-install.sh will exercise it via a throwaway key).
