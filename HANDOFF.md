# feat/widget-picker-previews — P1, spec items 1 + 2 — done

## Done
- `WidgetPreviewArtwork`: the three forms a card can show; `WidgetPickerCardTemplate`: six shapes
  and the smallest-containing rule that picks one.
- `WidgetProviderCatalogLoader`: the ladder (generated → previewLayout → bitmap) behind three new
  `Boundary` methods, each rung caught for `RuntimeException | LinkageError`; per-item shrink
  extent; `notePreviewRenderFailed` demotes a provider for the session.
- `WidgetPickerAdapter`: slot sized from the template, live artwork into an unbound
  `AppWidgetHostView` scaled into it, touches intercepted so the card keeps the tap.

## Next
Nothing in this phase. The spec's own gate for P1 is a device count of how many installed
providers actually reach tier 1 or 2 — not run here.

## Gotchas
- The suite runs ~5,000 Robolectric tests in one 512 MB worker and is at the ceiling: five extra
  `Robolectric.buildActivity` calls in a new test OOM'd six unrelated tests. Use the application
  context unless a test needs an Activity.
- `new RemoteViews(pkg, id)` validates the package at construction; a fake must use the test
  application's own package.
- The API 35 field is read in `AndroidBoundary` only, so the ladder is driven by an injected
  `sdkInt` — Robolectric 4.13 has no SDK 35 image to run it on.
