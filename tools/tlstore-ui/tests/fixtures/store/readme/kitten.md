# kitty — the fast, feature-rich, GPU based terminal emulator

![kitty logo](logo/kitty.png)

[Documentation](https://sw.kovidgoyal.net/kitty/) · [Changelog](https://sw.kovidgoyal.net/kitty/changelog/)

## Features

- Offloads rendering to the GPU for **lower system load**
- Supports *all* modern terminal features: [graphics](https://sw.kovidgoyal.net/kitty/graphics-protocol/), unicode, ligatures
- Scriptable with `kitten @`
  - Send text, open windows, resize
  - Deeper levels are flattened

## kittens

The `kitten` command carries a set of small programs:

| Kitten | What it does |
| --- | --- |
| `icat` | shows a picture in the terminal |
| `diff` | compares two files side by side |
| `transfer` | sends files between machines |

### icat

```sh
kitten icat picture.png
```

An animated demonstration: ![icat in motion](https://github.com/kovidgoyal/kitty/raw/master/docs/screenshots/icat.gif)

> kittens are cats too.

***

## Requirements

Dropped as a stock section: a terminal that speaks the kitty protocols.

## Sponsors

Dropped as a stock section.
