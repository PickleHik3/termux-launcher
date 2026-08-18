## Main findings

1. The shipped curated map contains only placeholders.
   app/termux-launcher/app/src/main/res/raw/
   app_drawer_category_overrides.csv:1 has two com.example.* entries.
   Consequently, Shopping & Food, Finance, and Health are effectively
   unreachable for real applications.

2. Android supplies only a coarse, optional category.
   app/termux-launcher/app/src/main/java/com/termux/app/launcher/
   drawer/AppDrawerCategoryClassifier.java:82 maps the available
   platform constants correctly, but undefined apps—and every app
   below API 26—go directly to Other. Many apps do not declare useful
   category metadata.

3. A stale curated entry overrides better platform metadata.
   Exact package mappings currently win unconditionally at app/
   termux-launcher/app/src/main/java/com/termux/app/launcher/drawer/
   AppDrawerCategoryClassifier.java:82. Curated “fill missing
   metadata” entries and intentional “correct Android” overrides
   should be distinct.

4. Category previews are alphabetical, not representative.
   The catalogue is alphabetized at app/termux-launcher/app/src/main/
   java/com/termux/app/launcher/drawer/AppDrawerContentView.java:645,
   taxonomy buckets preserve that ordering, and previews take the
   first seven at app/termux-launcher/app/src/main/java/com/termux/
   app/launcher/drawer/AppDrawerCategoryBucket.java:21. Thus every
   preview favors apps near the start of the alphabet.

5. Suggestions eventually becomes a duplicate app drawer.
   Every app ever launched is retained, with lifetime launch count
   dominating ranking and no limit or decay at app/termux-launcher/
   app/src/main/java/com/termux/app/launcher/data/
   LauncherUsageStatsStore.java:114. The classifier places the entire
   list into Suggestions at app/termux-launcher/app/src/main/java/
   com/termux/app/launcher/drawer/
   AppDrawerCategoryClassifier.java:38.

6. The taxonomy discards a particularly reliable distinction.
   Android explicitly distinguishes games, audio, and video, but all
   three become Entertainment. Games should be separate; it is both
   meaningful to users and one of the strongest platform signals.

## Recommended classification policy

Use an assignment carrying category, source, and confidence, with
this precedence:

Priority Signal Rule
━━━━━━━━━━ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ━━━━━━━━━━━━━━━━━━━━━
1 User assignment Always
authoritative;
provide “Move to
category” and “Use
automatic category”
────────── ────────────────────────────────── ─────────────────────
2 Curated force correction Exact package only;
reserved for known
incorrect platform
declarations
────────── ────────────────────────────────── ─────────────────────
3 Android ApplicationInfo.category Primary automatic
signal
────────── ────────────────────────────────── ─────────────────────
4 Curated fill mapping Used only when
Android reports
undefined
────────── ────────────────────────────────── ─────────────────────
5 Default system roles Browser, dialer/
SMS, camera/
gallery, maps, file
manager, etc.
────────── ────────────────────────────────── ─────────────────────
6 Scored metadata heuristics Assign only when
evidence passes a
confidence and
margin threshold
────────── ────────────────────────────────── ─────────────────────
7 None/ambiguous Keep in Other; a
cautious Other is
preferable to
confident-looking
errors

For scored fallback rules:

- Match whole normalized words or package segments, never arbitrary
  substrings.

- Require either one high-specificity signal or two independent
  weaker signals.

- Examples: banking/wallet plus payment capability → Finance;
  fitness/workout plus health integration → Health; navigation plus
  geo: handling → Travel.

- Do not classify from permissions alone. Camera, location, contacts,
  storage, and microphone permissions are far too common.

- Do not query a network service at runtime. Classification should
  remain private, deterministic, and available offline.

- Apply semantic classification at package level so multiple launcher
  activities and profiles do not scatter across categories.

I would evolve the CSV schema to something such as:

# schema=2

package_name,category_slug,mode
com.example.realbank,finance,fill
com.example.misdeclared,photo_video,force

The parser should reject an unsupported schema instead of currently
treating every comment as ignorable.

## Recommended taxonomy

Keep the number of tiles controlled, but split the strongest
distinction:

- Communication & Social
- Work & School
- Utilities
- Games
- Media & Entertainment
- Shopping & Food
- Finance
- Health & Fitness
- Photo & Creativity
- Travel & Local
- News & Reading
- Other

That is close to the existing model and avoids a large UI rewrite.

## Bucket behavior

- Keep expanded semantic categories alphabetical.
- Rank only the seven preview icons by recent local usage, then
  alphabetically for unused apps.

- Cap Suggestions at seven or eight apps and use recency decay rather
  than lifetime count alone.

- Keep Recently Added’s 30-day window, but cap it around twelve
  entries.

- Omit empty categories as today.
- Fold a weakly inferred singleton into Other; retain high-confidence
  and user-created singleton categories.

- Keep semantic categories exclusive while allowing Suggestions and
  Recently Added to overlap.

## Release-quality gates

Before calling this generally usable:

- Replace all placeholder curated rows with reviewed real mappings.
- Add a representative, manually labeled corpus covering Google,
  major OEM, F-Droid, work-profile, and regional apps.

- Target at least 90% precision; optimize coverage second. Other
  should remain the deliberate ambiguity bucket.

- Test conflicting signals, locale-independent matching, multi-
  activity packages, profile duplicates, and stale overrides.

- Add a debug-only classification report showing package, chosen
  category, source, confidence, and rejected candidates.

- Add a user correction path; no static heuristic set will cover
  regional and newly released apps indefinitely.

The existing focused tests all pass:

:app:testDebugUnitTest
AppDrawerCategoryClassifierTest
AppDrawerCuratedCategoryMapTest
LauncherAppDataProviderCategoryMetadataTest
