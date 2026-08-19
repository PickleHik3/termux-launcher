"""Generate the Nerd Font half of the glyph catalogue straight from the bundled symbols face.

Every row is a code point the shipped SymbolsNerdFontMono.ttf actually maps, named by the font's
own glyph name, so the catalogue can never offer a glyph the app cannot draw.
"""
import struct
import sys

FONT = "app/src/main/assets/fonts/SymbolsNerdFontMono.ttf"
OUT = "app/src/main/res/raw/nerd_font_glyphs.csv"

FAMILY_KEYWORDS = {
    "md": "material design",
    "fa": "font awesome",
    "cod": "codicon vscode",
    "dev": "devicons",
    "oct": "octicons github",
    "weather": "weather",
    "fae": "font awesome extension",
    "seti": "seti file",
    "linux": "linux distro",
    "ple": "powerline extra",
    "pl": "powerline",
    "custom": "custom",
    "extra": "extra",
    "pom": "pomicons",
    "iec": "iec power",
    "indentation": "indentation",
}


def tables(data):
    count = struct.unpack(">H", data[4:6])[0]
    found = {}
    for i in range(count):
        offset = 12 + 16 * i
        tag = data[offset:offset + 4].decode()
        start, length = struct.unpack(">II", data[offset + 8:offset + 16])
        found[tag] = (start, length)
    return found


def glyph_names(data, post_offset, post_length):
    count = struct.unpack(">H", data[post_offset + 32:post_offset + 34])[0]
    indices = struct.unpack(">%dH" % count, data[post_offset + 34:post_offset + 34 + 2 * count])
    cursor = post_offset + 34 + 2 * count
    end = post_offset + post_length
    names = []
    while cursor < end:
        length = data[cursor]
        names.append(data[cursor + 1:cursor + 1 + length].decode("latin-1"))
        cursor += 1 + length
    return indices, names


def cmap12(data, cmap_offset):
    subtables = struct.unpack(">H", data[cmap_offset + 2:cmap_offset + 4])[0]
    mapping = {}
    for i in range(subtables):
        _, _, offset = struct.unpack(">HHI", data[cmap_offset + 4 + 8 * i:cmap_offset + 12 + 8 * i])
        start = cmap_offset + offset
        if struct.unpack(">H", data[start:start + 2])[0] != 12:
            continue
        groups = struct.unpack(">I", data[start + 12:start + 16])[0]
        for g in range(groups):
            first, last, glyph = struct.unpack(">III", data[start + 16 + 12 * g:start + 28 + 12 * g])
            for code in range(first, last + 1):
                mapping[code] = glyph + (code - first)
    return mapping


def main():
    data = open(FONT, "rb").read()
    found = tables(data)
    post_offset, post_length = found["post"]
    indices, names = glyph_names(data, post_offset, post_length)
    mapping = cmap12(data, found["cmap"][0])

    def name_of(glyph):
        index = indices[glyph]
        return names[index - 258] if index >= 258 else ""

    rows = []
    for code in sorted(mapping):
        # parseCodePoint refuses anything outside 4-6 uppercase hex digits.
        if code <= 0x20 or code > 0xFFFFF:
            continue
        raw = name_of(mapping[code])
        if not raw or "-" not in raw:
            continue
        family, _, rest = raw.partition("-")
        words = rest.replace("_", " ").strip()
        if not words:
            continue
        name = words
        # The name already carries the words, so keywords only add what it cannot be searched by:
        # the family, the "nerd" bucket, and the exact Nerd Font name people copy from cheat sheets.
        keywords = " ".join([family, "nerd", "nf-" + raw])
        if "," in name or "," in keywords:
            continue
        rows.append("%04X,%s,%s,nerd_font" % (code, name, keywords))

    header = [
        "# schema=1",
        "# code_point,name,keywords,category",
        "# Generated from the bundled SymbolsNerdFontMono.ttf by its own glyph names, so every row",
        "# is a code point the shipped face maps — regenerate this file whenever that font is",
        "# updated rather than editing rows by hand.",
    ]
    with open(OUT, "w", encoding="utf-8") as out:
        out.write("\n".join(header) + "\n")
        out.write("\n".join(rows) + "\n")
    print("wrote", OUT, len(rows), "rows", file=sys.stderr)


main()
