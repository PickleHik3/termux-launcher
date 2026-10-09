A quick fix for 1.0.0: on-device AI features stopped working in the release build. This brings them back, and makes sorting apps into categories faster along the way.

## Fixes

- Voice typing cleanup works again. In 1.0.0 every dictation came back exactly as heard.
- The on-device assistant and on-device app sorting work again.
- Settings › Appearance opens its own page again, with terminal font, theme, icons and keyboard look. Wallpaper, look and layout are the first row on that page.

## Changes

- Sorting apps into categories is faster: only apps the drawer can't place on its own are sent to the model, several at a time.
- A sort keeps what it has done if it is interrupted, and stops by itself if the model stops answering.
- Apps the model can't place are left to the drawer and asked again next time, instead of landing in Other for good.

## Editions

Shipped as `nix-v1.0.1` and `vaj-v1.0.1`. Nothing was exclusive to an edition.
