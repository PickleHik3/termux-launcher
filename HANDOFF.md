# HANDOFF — phase 5, feat/linux-apps-category

Done. New `LINUX_APPS` category (slug `linux_apps`) added just before `OTHER` in
`AppDrawerCategory.java`. Classifier rule in `AppDrawerCategoryClassifier.java:187-193`
(`assignUnreported`) intercepts every `X11Apps.isLinuxApp` entry with a new
`Source.LINUX_APP`, placed right after `USER` in the precedence enum and the class javadoc —
user override still wins, curated/platform/fill/role/heuristics never see a Linux app.
`LauncherCategorySortPrompt.java` got one added description line (`linux_apps`) so its
"every non-synthetic slug has a description" test kept passing — the on-device sort model
never actually decides this bucket in practice, since the classifier intercepts before ROLE.
Label string `app_drawer_category_linux_apps` = "Linux Apps". Three new classifier tests
(Linux app -> LINUX_APPS despite a scoring name, user override beats it, Android app
unchanged). Full suite green: 5207 tests, 0 failures. Not yet committed — do that next.
