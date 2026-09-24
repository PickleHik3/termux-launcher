# HANDOFF — P polish (feat/tlstore-polish)
1. Script: update --check resolves latest-pinned npm items at the registry (69b1cc59).
2. Copy: kitten note shortened, notes ≤ 40 chars rule, catalog rebuilt unsigned (35c9a981).
3. UI: layout designed for 53×26 (layout.rs BASE_*), tiers Tall/Base/Compact, cover_rows().
4. UI: item name is OSC 66 text; script word only as exact-pixel picture when cell size known.
5. UI: no stand-in boxes; covers/demos only with ≥ 8 spare rows; wrap/fit_line helpers.
6. UI: pictures never move in the timeline (hidden until the screen settles).
7. UI: launch.rs moves the store into its own window below 53×26 (TLSTORE_NO_WINDOW=1).
8. Snapshots: 53x26, 53x40, 40x26 for plain and kitty.
9. Next: build-ui.sh --install, checkTlstoreUiFresh, final report.
10. Never push/merge; no devices.
