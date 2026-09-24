# Fastfetch

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/fastfetch-cli/fastfetch/HEAD/screenshots/example1.png">
    <img src="https://raw.githubusercontent.com/fastfetch-cli/fastfetch/HEAD/screenshots/example1.png" alt="example output" width="700">
  </picture>
</p>

<div align="center">
  <a href="https://github.com/fastfetch-cli/fastfetch/releases"><img src="https://img.shields.io/github/v/release/fastfetch-cli/fastfetch" alt="release"></a>
  <a href="https://github.com/fastfetch-cli/fastfetch/blob/dev/LICENSE"><img src="https://img.shields.io/github/license/fastfetch-cli/fastfetch" alt="licence"></a>
</div>

## About

Fastfetch is a **neofetch-like** tool for fetching system information and displaying it in a *pretty* way. It is written mainly in `C`, with performance and customisability in mind. Read more at https://github.com/fastfetch-cli/fastfetch.

<details>
<summary>Dropped: an expandable block</summary>

Nothing in here reaches the page.

</details>

Line one of a paragraph,<br>line two after a raw line break.

## Building

```sh
mkdir -p build
cd build
cmake ..
cmake --build . --target fastfetch
```

Dropped as a stock section.

## Customisation

Fastfetch uses `JSONC` for configuration. The file is at `~/.config/fastfetch/config.jsonc`.

### Modules

| Module | Description | Default | Example | Notes | Since |
| --- | --- | --- | --- | --- | --- |
| Title | user@host | yes | `andrew@pong` | colours follow the terminal | 1.0 |
| OS | operating system | yes | `Android 16` | reads the build properties | 1.0 |
| Battery | charge and state | no | `84% [charging]` | needs `termux-api` on Android | 2.5 |

That table is too wide for the content width and becomes one dim line.

```jsonc
// ~/.config/fastfetch/config.jsonc
{
    "logo": {
        "type": "kitty",
        "source": "~/Pictures/gif/skel.gif",
        "width": 30
    },
    "display": {
        "separator": " → "
    },
    "modules": [
        "title",
        "separator",
        "os",
        "host",
        "kernel",
        "uptime",
        "packages",
        "shell",
        "display",
        "terminal",
        "cpu",
        "memory",
        "battery",
        "break",
        "colors"
    ]
}
```

That code block runs over 12 lines and is cut with a link.

## Star History

Dropped as a stock section.

## Contributing

Dropped as a stock section.
