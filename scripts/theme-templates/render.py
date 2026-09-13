#!/usr/bin/env python3
"""Dev-only stand-in for the launcher's Java ThemeTemplateRenderer.

Renders a theme-template input file against a fixture palette, so
check.sh can validate the shipped templates without building the app.
Implements exactly the placeholder grammar in project-docs/theme-templates/SPEC.md:

  {{ colors.<token>.<mode>.<format> }}   (spaces optional)
  {{ mode }}

Modes default/dark/light all resolve to the single active palette the
fixture provides. Formats: hex, hex_stripped, rgb, rgba, red, green, blue.
Any other `{{ ... }}` (for example oh-my-posh's own Go-template syntax) is
left completely untouched - only the two forms above are ever matched.

An unknown token, an unknown format, or a `<*` block makes the template
skipped-and-logged in the real renderer; here it is a hard error (nonzero
exit, message on stderr) so check.sh notices.
"""
import re
import sys

COLORS_RE = re.compile(
    r"\{\{\s*colors\.([A-Za-z0-9_]+)\.(default|dark|light)\.([A-Za-z_]+)\s*\}\}"
)
MODE_RE = re.compile(r"\{\{\s*mode\s*\}\}")
LT_STAR_RE = re.compile(r"<\*")

VALID_FORMATS = {"hex", "hex_stripped", "rgb", "rgba", "red", "green", "blue"}


def load_properties(path):
    props = {}
    with open(path, "r", encoding="utf-8") as fd:
        for raw_line in fd:
            line = raw_line.strip()
            if not line or line.startswith("#") or line.startswith("!"):
                continue
            if "=" not in line:
                continue
            key, _, value = line.partition("=")
            props[key.strip()] = value.strip()
    return props


def hex_to_rgb(hex_value):
    hex_value = hex_value.strip()
    if hex_value.startswith("#"):
        hex_value = hex_value[1:]
    if len(hex_value) != 6:
        raise ValueError(f"not a #rrggbb colour: {hex_value!r}")
    return tuple(int(hex_value[i : i + 2], 16) for i in (0, 2, 4))


def format_value(hex_value, fmt):
    if fmt == "hex":
        return hex_value if hex_value.startswith("#") else f"#{hex_value}"
    if fmt == "hex_stripped":
        return hex_value[1:] if hex_value.startswith("#") else hex_value
    r, g, b = hex_to_rgb(hex_value)
    if fmt == "rgb":
        return f"rgb({r}, {g}, {b})"
    if fmt == "rgba":
        return f"rgba({r}, {g}, {b}, 1.0)"
    if fmt == "red":
        return str(r)
    if fmt == "green":
        return str(g)
    if fmt == "blue":
        return str(b)
    raise ValueError(f"unknown format: {fmt}")


class RenderError(Exception):
    pass


def render(text, palette):
    if LT_STAR_RE.search(text):
        raise RenderError("template contains a `<*` block, which is out of scope")

    mode_value = palette.get("mode")
    if mode_value is None:
        raise RenderError("fixture palette has no 'mode' key")

    def replace_colors(match):
        token, _mode, fmt = match.group(1), match.group(2), match.group(3)
        if fmt not in VALID_FORMATS:
            raise RenderError(f"unknown format '{fmt}' for token '{token}'")
        if token not in palette:
            raise RenderError(f"unknown token '{token}'")
        return format_value(palette[token], fmt)

    text = MODE_RE.sub(lambda _m: mode_value, text)
    text = COLORS_RE.sub(replace_colors, text)
    return text


def main(argv):
    if len(argv) != 3:
        print(f"usage: {argv[0]} <fixture.properties> <template-input-file>", file=sys.stderr)
        return 2

    fixture_path, template_path = argv[1], argv[2]
    palette = load_properties(fixture_path)

    with open(template_path, "r", encoding="utf-8") as fd:
        text = fd.read()

    try:
        rendered = render(text, palette)
    except RenderError as exc:
        print(f"render error: {exc}", file=sys.stderr)
        return 1

    sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
