"""Build the Strideway logo set (SVG + transparent PNG) from the font's glyph outlines.

Icon concept: octagonal tyre ring with a chamfered S inside, slanted 12 degrees.
The wordmark carries an overline (right side) and an underline (left side).
Shapes carry a role, "accent" (tyre ring, lines) or "ink" (S, letters), so each
colour variant just maps the two roles to colours.

Usage:  python tools/branding/build_logo.py
Output: assets/branding/
"""

import math
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "assets" / "branding"
SLANT = math.radians(12)
SK = math.tan(SLANT)
SS = 4  # supersampling factor for PNG output

GLYPHS = {
    "S": (72, "M14 0H72V22H22V39H58L72 53V86L58 100H0V78H50V61H14L0 47V14Z"),
    "T": (70, "M0 0H70V22H46V100H24V22H0Z"),
    "R": (74, "M0 0H58L72 14V47L62 57L74 100H51L42 61H22V100H0ZM22 22V39H50V22Z"),
    "I": (22, "M0 0H22V100H0Z"),
    "D": (72, "M0 0H52L72 20V80L52 100H0ZM22 22V78H45L50 73V27L45 22Z"),
    "E": (62, "M0 0H62V22H22V39H54V61H22V78H62V100H0Z"),
    "W": (116, "M0 0H23L34 64L46 0H70L82 64L93 0H116L96 100H72L58 40L44 100H20Z"),
    "A": (80, "M0 100L28 0H52L80 100H57L52 80H28L23 100ZM33 62L40 36L47 62Z"),
    "Y": (76, "M0 0H24L38 34L52 0H76L49 58V100H27V58Z"),
}
KERN = {"WA": -4, "AY": -10}
TRACK = 5

RING = [(30, 4), (70, 4), (96, 30), (96, 70), (70, 96), (30, 96), (4, 70), (4, 30)]
RING_W = 8
S_CENTER = [(56, 7), (29, 7), (7, 29), (7, 50), (93, 50), (93, 71), (71, 93), (44, 93)]
S_SCALE = 0.5
S_W = 24 * S_SCALE


def parse_path(d):
    contours, cur, x, y = [], [], 0.0, 0.0
    for cmd, args in re.findall(r"([MHVLZ])([^MHVLZ]*)", d):
        n = [float(v) for v in args.split()]
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


def unit(a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    n = math.hypot(dx, dy)
    return dx / n, dy / n


def miter(p, d0, d1, h):
    n0, n1 = (-d0[1], d0[0]), (-d1[1], d1[0])
    rx, ry = (n1[0] - n0[0]) * h, (n1[1] - n0[1]) * h
    det = -d0[0] * d1[1] + d0[1] * d1[0]
    t = (-rx * d1[1] + ry * d1[0]) / det
    return (p[0] + n0[0] * h + t * d0[0], p[1] + n0[1] * h + t * d0[1])


def stroke_open(pts, width):
    h = width / 2
    dirs = [unit(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
    sides = []
    for s in (1, -1):
        side = [(pts[0][0] - dirs[0][1] * h * s, pts[0][1] + dirs[0][0] * h * s)]
        for i in range(1, len(pts) - 1):
            side.append(miter(pts[i], dirs[i - 1], dirs[i], h * s))
        side.append((pts[-1][0] - dirs[-1][1] * h * s, pts[-1][1] + dirs[-1][0] * h * s))
        sides.append(side)
    return sides[0] + sides[1][::-1]


def stroke_closed(pts, width):
    h = width / 2
    n = len(pts)
    rings = []
    for s in (1, -1):
        ring = []
        for i in range(n):
            d0 = unit(pts[i - 1], pts[i])
            d1 = unit(pts[i], pts[(i + 1) % n])
            ring.append(miter(pts[i], d0, d1, h * s))
        rings.append(ring)
    return rings


def place(contours, sx=1.0, dx=0.0, dy=0.0):
    """Slant about the baseline (y=100), then scale and translate."""
    out = []
    for c in contours:
        out.append([((x + SK * (100 - y)) * sx + dx, y * sx + dy) for x, y in c])
    return out


def s_inner():
    return [(50 + (x - 50) * S_SCALE, 50 + (y - 50) * S_SCALE) for x, y in S_CENTER]


def icon_shapes(sc=1.0, dx=0.0, dy=0.0):
    return [
        ("accent", place(stroke_closed(RING, RING_W), sc, dx, dy)),
        ("ink", place([stroke_open(s_inner(), S_W)], sc, dx, dy)),
    ]


def rect(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


def wordmark_shapes(dx=0.0, dy=0.0):
    x, contours = 0.0, []
    text = "STRIDEWAY"
    for i, ch in enumerate(text):
        w, d = GLYPHS[ch]
        for c in parse_path(d):
            contours.append([(px + x, py) for px, py in c])
        x += w + TRACK + KERN.get(ch + (text[i + 1] if i + 1 < len(text) else ""), 0)
    width = x - TRACK
    lines = [
        rect(width * 0.36, -10, width, -5),
        rect(0, 109, width * 0.64, 114),
    ]
    return [
        ("ink", place(contours, 1.0, dx, dy)),
        ("accent", place(lines, 1.0, dx, dy)),
    ], width


def lockup_shapes():
    sc, icon_w, gap = 1.2, 100, 34
    wm, _ = wordmark_shapes(icon_w * sc + gap, 20)
    return icon_shapes(sc, 0, 70 - 50 * sc) + wm


def bbox(shapes):
    pts = [p for _, g in shapes for c in g for p in c]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


def write_svg(path, shapes, colors, margin=4):
    x0, y0, x1, y1 = bbox(shapes)
    x0, y0, x1, y1 = x0 - margin, y0 - margin, x1 + margin, y1 + margin
    w, h = x1 - x0, y1 - y0
    paths = []
    for role, g in shapes:
        d = "".join(
            "M" + "L".join(f"{x:.2f} {y:.2f}" for x, y in c) + "Z" for c in g
        )
        paths.append(f'<path fill="{colors[role]}" fill-rule="evenodd" d="{d}"/>')
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x0:.2f} {y0:.2f} {w:.2f} {h:.2f}" '
        f'width="{w * 10:.0f}" height="{h * 10:.0f}">'
        f'<title>Strideway</title>{"".join(paths)}</svg>\n'
    )
    path.write_text(svg, encoding="utf-8")


def write_png(path, shapes, colors, width_px=None, square=None, margin_px=0):
    x0, y0, x1, y1 = bbox(shapes)
    w, h = x1 - x0, y1 - y0
    if square:
        scale = square * 0.84 / max(w, h)
        cw = ch = square
    else:
        scale = (width_px - 2 * margin_px) / w
        cw, ch = width_px, round(h * scale + 2 * margin_px)
    ox = (cw - w * scale) / 2 - x0 * scale
    oy = (ch - h * scale) / 2 - y0 * scale
    img = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    for role, g in shapes:
        acc = Image.new("1", (cw * SS, ch * SS), 0)
        for c in g:
            m = Image.new("1", acc.size, 0)
            ImageDraw.Draw(m).polygon(
                [((x * scale + ox) * SS, (y * scale + oy) * SS) for x, y in c], fill=1
            )
            acc = ImageChops.logical_xor(acc, m)
        alpha = acc.convert("L").resize((cw, ch), Image.LANCZOS)
        layer = Image.new("RGBA", (cw, ch), colors[role])
        layer.putalpha(alpha)
        img = Image.alpha_composite(img, layer)
    img.save(path)


INK, SIGNAL = "#0A0A0B", "#FF4F00"
VARIANTS = {
    "black": {"ink": "#000000", "accent": "#000000"},
    "white": {"ink": "#FFFFFF", "accent": "#FFFFFF"},
    "color-light": {"ink": INK, "accent": SIGNAL},
    "color-dark": {"ink": "#FFFFFF", "accent": SIGNAL},
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    wm, _ = wordmark_shapes()
    sets = {
        "icon": (icon_shapes(), {"square": 1024}),
        "wordmark": (wm, {"width_px": 2400, "margin_px": 40}),
        "lockup": (lockup_shapes(), {"width_px": 2400, "margin_px": 40}),
    }
    for name, (shapes, png_args) in sets.items():
        for tone, colors in VARIANTS.items():
            stem = f"strideway-{name}-{tone}"
            write_svg(OUT / f"{stem}.svg", shapes, colors)
            write_png(OUT / f"{stem}.png", shapes, colors, **png_args)
            print("wrote", stem)


if __name__ == "__main__":
    main()
