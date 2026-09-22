# feat/widget-picker-previews — P1, spec items 1 + 2

Done: tiered previews (generated → previewLayout → bitmap) through the loader's Boundary; card
shapes snapped to six templates; live previews render into an unbound AppWidgetHostView scaled
into the card; failed inflation demotes the provider to the bitmap tier for the session.
In progress: nothing.
Next: nothing in this phase. Item 2's "clip to the widget radius" is the slot's outline provider,
capped at a quarter of the card — worth an eye on a device.
Gotchas:
- `generatedPreviewCategories` (API 35) is read in `AndroidBoundary.offersGeneratedPreview` only,
  so tests drive the ladder through the fake and one injected `sdkInt`; Robolectric 4.13 has no
  SDK 35 to run it on.
- `new RemoteViews(pkg, id)` validates the package at construction, not at apply; a test fake must
  use the test application's own package.
- `WidgetPickerAdapter.PREVIEW_DP` is gone; the store now budgets for the largest template.
