"""Build Strideway Sans Bold Italic (.ttf and .otf) from polygon glyph outlines.

Glyphs are drawn upright on a grid where cap height = 100 units, y grows
downward, baseline sits at y=100, x-height at y=26 and descenders reach y=128.
The build scales the grid to a 1000 UPM em, flips y, slants everything 12
degrees and fixes contour direction per format.

Usage:  python tools/font/build_font.py
Output: assets/fonts/StridewaySans-BoldItalic.ttf and .otf
"""

import math
import re
from pathlib import Path

from fontTools.feaLib.builder import addOpenTypeFeaturesFromString
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.ttGlyphPen import TTGlyphPen

FAMILY = "Strideway Sans"
STYLE = "Bold Italic"
PS_NAME = "StridewaySans-BoldItalic"
VERSION = "1.000"

UNIT = 7  # grid unit -> font units (cap 100 -> 700)
SLANT = 12  # degrees
SIDEBEARING = 4.5  # grid units on each side
OUT_DIR = Path(__file__).resolve().parents[2] / "assets" / "fonts"

# name: (codepoints, width in grid units, outline path using M H V L Z only)
GLYPHS = {
    ".notdef": ((), 60, "M0 0H60V100H0ZM12 12V88H48V12Z"),
    "space": ((0x20,), 21, ""),
    # Uppercase
    "A": ((0x41,), 80, "M0 100L28 0H52L80 100H57L52 80H28L23 100ZM33 62L40 36L47 62Z"),
    "B": ((0x42,), 70, "M0 0H56L70 14V40L62 50L70 60V86L56 100H0ZM22 22V39H48V22ZM22 61V78H48V61Z"),
    "C": ((0x43,), 66, "M14 0H66V22H22V78H66V100H14L0 86V14Z"),
    "D": ((0x44,), 72, "M0 0H52L72 20V80L52 100H0ZM22 22V78H45L50 73V27L45 22Z"),
    "E": ((0x45,), 62, "M0 0H62V22H22V39H54V61H22V78H62V100H0Z"),
    "F": ((0x46,), 60, "M0 0H60V22H22V40H52V62H22V100H0Z"),
    "G": ((0x47,), 70, "M14 0H70V22H22V78H48V64H36V44H70V100H14L0 86V14Z"),
    "H": ((0x48,), 72, "M0 0H22V39H50V0H72V100H50V61H22V100H0Z"),
    "I": ((0x49,), 22, "M0 0H22V100H0Z"),
    "J": ((0x4A,), 62, "M40 0H62V86L48 100H0V78H40Z"),
    "K": ((0x4B,), 76, "M0 0H22V38L48 0H74L42 48L76 100H50L22 58V100H0Z"),
    "L": ((0x4C,), 58, "M0 0H22V78H58V100H0Z"),
    "M": ((0x4D,), 92, "M0 0H24L46 40L68 0H92V100H70V44L52 74H40L22 44V100H0Z"),
    "N": ((0x4E,), 74, "M0 0H24L52 58V0H74V100H50L22 42V100H0Z"),
    "O": ((0x4F,), 72, "M14 0H58L72 14V86L58 100H14L0 86V14ZM22 22V78H50V22Z"),
    "P": ((0x50,), 70, "M0 0H56L70 14V48L56 62H22V100H0ZM22 22V40H48V22Z"),
    "Q": ((0x51,), 74, "M14 0H58L72 14V78L62 88L74 106H50L46 100H14L0 86V14ZM22 22V78H50V22Z"),
    "R": ((0x52,), 74, "M0 0H58L72 14V47L62 57L74 100H51L42 61H22V100H0ZM22 22V39H50V22Z"),
    "S": ((0x53,), 72, "M14 0H72V22H22V39H58L72 53V86L58 100H0V78H50V61H14L0 47V14Z"),
    "T": ((0x54,), 70, "M0 0H70V22H46V100H24V22H0Z"),
    "U": ((0x55,), 72, "M0 0H22V78H50V0H72V86L58 100H14L0 86Z"),
    "V": ((0x56,), 78, "M0 0H24L39 68L54 0H78L52 100H26Z"),
    "W": ((0x57,), 116, "M0 0H23L34 64L46 0H70L82 64L93 0H116L96 100H72L58 40L44 100H20Z"),
    "X": ((0x58,), 76, "M0 0H25L38 32L51 0H76L51 50L76 100H51L38 68L25 100H0L25 50Z"),
    "Y": ((0x59,), 76, "M0 0H24L38 34L52 0H76L49 58V100H27V58Z"),
    "Z": ((0x5A,), 66, "M0 0H66V20L26 78H66V100H0V80L40 22H0Z"),
    # Lowercase
    "a": ((0x61,), 64, "M6 26H52L64 38V100H12L0 88V64L12 52H42V42H6ZM22 66V82H42V66Z"),
    "b": ((0x62,), 66, "M0 0H22V26H54L66 38V88L54 100H0ZM22 46V80H44V46Z"),
    "c": ((0x63,), 58, "M12 26H58V46H22V80H58V100H12L0 88V38Z"),
    "d": ((0x64,), 66, "M44 0H66V100H12L0 88V38L12 26H44ZM22 46V80H44V46Z"),
    "e": ((0x65,), 64, "M12 26H52L64 38V66H22V80H64V100H12L0 88V38ZM22 42V52H42V42Z"),
    "f": ((0x66,), 50, "M20 0H50V20H30V26H46V46H30V100H8V46H0V26H8V12Z"),
    "g": ((0x67,), 66, "M12 26H66V116L54 128H4V108H44V100H12L0 88V38ZM22 46V80H44V46Z"),
    "h": ((0x68,), 66, "M0 0H22V26H54L66 38V100H44V46H22V100H0Z"),
    "i": ((0x69,), 22, "M0 0H22V18H0ZM0 26H22V100H0Z"),
    # Dotless i: the splash wordmark draws the dot of "Strideway" separately so it can animate it.
    "dotlessi": ((0x131,), 22, "M0 26H22V100H0Z"),
    "j": ((0x6A,), 40, "M18 0H40V18H18ZM18 26H40V116L28 128H0V108H18Z"),
    "k": ((0x6B,), 66, "M0 0H22V56L40 26H64L42 62L66 100H42L22 70V100H0Z"),
    "l": ((0x6C,), 30, "M0 0H22V80H30V100H12L0 88Z"),
    "m": ((0x6D,), 100, "M0 26H88L100 38V100H78V46H61V100H39V46H22V100H0Z"),
    "n": ((0x6E,), 66, "M0 26H54L66 38V100H44V46H22V100H0Z"),
    "o": ((0x6F,), 66, "M12 26H54L66 38V88L54 100H12L0 88V38ZM22 46V80H44V46Z"),
    "p": ((0x70,), 66, "M0 26H54L66 38V88L54 100H22V128H0ZM22 46V80H44V46Z"),
    "q": ((0x71,), 66, "M12 26H66V128H44V100H12L0 88V38ZM22 46V80H44V46Z"),
    "r": ((0x72,), 52, "M0 26H42L52 36V52H30V48H22V100H0Z"),
    "s": ((0x73,), 62, "M12 26H62V44H22V54H50L62 66V88L50 100H0V82H40V72H12L0 60V38Z"),
    "t": ((0x74,), 46, "M8 6H30V26H46V46H30V78H46V100H22L8 86V46H0V26H8Z"),
    "u": ((0x75,), 66, "M0 26H22V80H44V26H66V100H12L0 88Z"),
    "v": ((0x76,), 66, "M0 26H22L33 76L44 26H66L46 100H20Z"),
    "w": ((0x77,), 96, "M0 26H21L29 74L38 26H58L67 74L75 26H96L80 100H58L48 54L38 100H16Z"),
    "x": ((0x78,), 66, "M0 26H23L33 50L43 26H66L46 63L66 100H43L33 76L23 100H0L20 63Z"),
    "y": ((0x79,), 68, "M0 26H22V80H46V26H68V116L56 128H4V108H46V100H12L0 88Z"),
    "z": ((0x7A,), 58, "M0 26H58V44L24 82H58V100H0V82L34 44H0Z"),
    # Digits, all on a 64-unit tabular width
    "zero": ((0x30,), 64, "M14 0H50L64 14V86L50 100H14L0 86V14ZM22 22V78H42V22Z"),
    "one": ((0x31,), 64, "M26 0H52V100H30V26H16V14Z"),
    "two": ((0x32,), 64, "M0 0H50L64 14V47L50 61H22V78H64V100H0V53L14 39H42V22H0Z"),
    "three": ((0x33,), 64, "M0 0H50L64 14V40L56 50L64 60V86L50 100H0V78H42V61H14V39H42V22H0Z"),
    "four": ((0x34,), 64, "M0 0H22V52H36V0H58V52H64V72H58V100H36V72H0Z"),
    "five": ((0x35,), 64, "M0 0H64V22H22V39H50L64 53V86L50 100H0V78H42V61H0Z"),
    "six": ((0x36,), 64, "M14 0H64V22H22V39H50L64 53V86L50 100H14L0 86V14ZM22 61V78H42V61Z"),
    "seven": ((0x37,), 64, "M0 0H64V20L36 100H12L38 22H0Z"),
    "eight": ((0x38,), 64, "M14 0H50L64 14V40L56 50L64 60V86L50 100H14L0 86V60L8 50L0 40V14ZM22 20V40H42V20ZM22 60V80H42V60Z"),
    "nine": ((0x39,), 64, "M14 0H50L64 14V86L50 100H0V78H42V61H14L0 47V14ZM22 22V39H42V22Z"),
    # Punctuation and symbols
    "period": ((0x2E,), 22, "M0 78H22V100H0Z"),
    "comma": ((0x2C,), 22, "M0 78H22V104L10 118H0L6 100H0Z"),
    "colon": ((0x3A,), 22, "M0 26H22V48H0ZM0 78H22V100H0Z"),
    "semicolon": ((0x3B,), 22, "M0 26H22V48H0ZM0 78H22V104L10 118H0L6 100H0Z"),
    "exclam": ((0x21,), 22, "M0 0H22V64H0ZM0 78H22V100H0Z"),
    "question": ((0x3F,), 64, "M0 0H50L64 14V44L50 58H42V64H20V40H42V22H0ZM20 78H42V100H20Z"),
    "hyphen": ((0x2D, 0x2010), 40, "M0 52H40V68H0Z"),
    "endash": ((0x2013,), 70, "M0 52H70V68H0Z"),
    "quotesingle": ((0x27, 0x2019), 18, "M0 0H18V34H0Z"),
    "quotedbl": ((0x22,), 46, "M0 0H18V34H0ZM28 0H46V34H28Z"),
    "slash": ((0x2F,), 66, "M44 0H66L22 100H0Z"),
    "ampersand": ((0x26,), 90, "M12 0H48L60 12V34L48 46L66 64V50H84V64L74 74L90 100H64L58 92L50 100H12L0 88V60L12 48L4 40V12ZM22 20V32H42V20ZM22 64V80H42L48 74L38 64Z"),
    "parenleft": ((0x28,), 42, "M24 0H42L30 20V80L42 100H24L10 86V14Z"),
    "parenright": ((0x29,), 42, "M0 0H18L32 14V86L18 100H0L12 80V20Z"),
    "numbersign": ((0x23,), 76, "M18 10H32V28H44V10H58V28H76V42H58V60H76V74H58V92H44V74H32V92H18V74H0V60H18V42H0V28H18ZM32 42V60H44V42Z"),
    "percent": ((0x25,), 80, "M0 0H30V36H0ZM10 10V26H20V10ZM58 0H80L22 100H0ZM50 64H80V100H50ZM60 74V90H70V74Z"),
    "plus": ((0x2B,), 62, "M22 34H40V52H62V70H40V88H22V70H0V52H22Z"),
    "equal": ((0x3D,), 56, "M0 44H56V60H0ZM0 72H56V88H0Z"),
    "underscore": ((0x5F,), 60, "M0 110H60V124H0Z"),
    "degree": ((0xB0,), 32, "M6 0H26L32 6V26L26 32H6L0 26V6ZM10 10V22H22V10Z"),
}

# Kerning in grid units, applied through a GPOS kern feature
KERNING = {
    ("A", "V"): -8, ("A", "W"): -6, ("A", "Y"): -12, ("A", "T"): -8,
    ("V", "A"): -8, ("W", "A"): -6, ("Y", "A"): -12, ("T", "A"): -8,
    ("L", "T"): -10, ("L", "V"): -8, ("L", "W"): -6, ("L", "Y"): -10,
    ("F", "A"): -6, ("P", "A"): -6,
    ("T", "a"): -8, ("T", "e"): -8, ("T", "o"): -8, ("T", "y"): -6,
    ("Y", "a"): -8, ("Y", "e"): -8, ("Y", "o"): -8,
    ("V", "a"): -6, ("V", "e"): -6, ("V", "o"): -6,
    ("W", "a"): -4, ("W", "e"): -4, ("W", "o"): -4,
    ("r", "period"): -6, ("r", "comma"): -6,
}


def parse_path(d):
    contours, cur, x, y = [], [], 0.0, 0.0
    for cmd, args in re.findall(r"([MHVLZ])([^MHVLZ]*)", d):
        n = [float(v) for v in args.replace(",", " ").split()]
        if cmd == "M":
            if cur:
                contours.append(cur)
            x, y = n
            cur = [(x, y)]
        elif cmd == "H":
            x = n[0]
            cur.append((x, y))
        elif cmd == "V":
            y = n[0]
            cur.append((x, y))
        elif cmd == "L":
            for i in range(0, len(n), 2):
                x, y = n[i], n[i + 1]
                cur.append((x, y))
        elif cmd == "Z":
            contours.append(cur)
            cur = []
    if cur:
        contours.append(cur)
    return contours


def to_font_space(contour, lsb):
    k = math.tan(math.radians(SLANT))
    out = []
    for gx, gy in contour:
        fy = (100 - gy) * UNIT
        fx = (gx + lsb) * UNIT + fy * k
        out.append((round(fx), round(fy)))
    if out[0] == out[-1]:
        out.pop()
    return out


def signed_area(pts):
    return sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1])) / 2


def inside(pt, poly):
    x, y = pt
    hit = False
    for (x0, y0), (x1, y1) in zip(poly, poly[1:] + poly[:1]):
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            hit = not hit
    return hit


def oriented(contours, outer_clockwise):
    """Give outer contours and holes opposite directions so nonzero fill works."""
    result = []
    for i, c in enumerate(contours):
        depth = sum(inside(c[0], o) for j, o in enumerate(contours) if j != i)
        is_hole = depth % 2 == 1
        clockwise = signed_area(c) < 0
        want_clockwise = outer_clockwise != is_hole
        result.append(c if clockwise == want_clockwise else c[::-1])
    return result


def draw(pen, contours):
    for c in contours:
        pen.moveTo(c[0])
        for p in c[1:]:
            pen.lineTo(p)
        pen.closePath()


def build(flavor):
    order = list(GLYPHS)
    cmap, metrics, outlines = {}, {}, {}
    for name, (cps, width, d) in GLYPHS.items():
        for cp in cps:
            cmap[cp] = name
        advance = round((width + 2 * SIDEBEARING) * UNIT)
        contours = [to_font_space(c, SIDEBEARING) for c in parse_path(d)]
        outlines[name] = contours
        xs = [x for c in contours for x, _ in c]
        metrics[name] = (advance, min(xs) if xs else 0)

    fb = FontBuilder(1000, isTTF=(flavor == "ttf"))
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)

    if flavor == "ttf":
        glyphs = {}
        for name in order:
            pen = TTGlyphPen(None)
            draw(pen, oriented(outlines[name], outer_clockwise=True))
            glyphs[name] = pen.glyph()
        fb.setupGlyf(glyphs)
    else:
        charstrings = {}
        for name in order:
            pen = T2CharStringPen(metrics[name][0], None)
            draw(pen, oriented(outlines[name], outer_clockwise=False))
            charstrings[name] = pen.getCharString()
        fb.setupCFF(PS_NAME, {"FullName": f"{FAMILY} {STYLE}", "ItalicAngle": -SLANT}, charstrings, {})

    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=900, descent=-250)
    fb.setupNameTable({
        "familyName": FAMILY,
        "styleName": STYLE,
        "uniqueFontIdentifier": f"{PS_NAME};{VERSION}",
        "fullName": f"{FAMILY} {STYLE}",
        "psName": PS_NAME,
        "version": f"Version {VERSION}",
    })
    fb.setupOS2(
        sTypoAscender=900, sTypoDescender=-250, sTypoLineGap=0,
        usWinAscent=900, usWinDescent=250,
        sxHeight=round(74 * UNIT), sCapHeight=100 * UNIT,
        usWeightClass=700,
        fsSelection=0b0000_0000_0010_0001,  # ITALIC | BOLD
        achVendID="STRW",
    )
    fb.setupPost(italicAngle=-SLANT, isFixedPitch=0)
    fb.font["head"].macStyle = 0b11  # bold + italic

    pairs = "\n".join(
        f"    pos {a} {b} {round(v * UNIT)};" for (a, b), v in KERNING.items()
    )
    addOpenTypeFeaturesFromString(fb.font, f"feature kern {{\n{pairs}\n}} kern;\n")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / f"{PS_NAME}.{flavor}"
    fb.save(path)
    return path


if __name__ == "__main__":
    for flavor in ("ttf", "otf"):
        print("wrote", build(flavor))
