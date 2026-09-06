"""Draws the treble clef this app engraves, and says what its outline is.

The clef is written here as a pen stroke rather than as an outline: a centreline of cubic Beziers
and a broad nib held at a fixed angle. That is how the letterform was made by hand for five hundred
years, and it is why the thick and thin parts fall where they do - the stroke is widest where it
runs across the nib and thinnest where it runs along it. Editing a centreline point is something a
person can reason about; editing 398 outline points is not.

Everything is in musical units: x in staff spaces, y in staff steps (two steps to a space, 0 is the
bottom line of the treble staff, so the G line is step 2).

    python tools/treble_clef.py            # SVG for inspection + the Kotlin path data
    python tools/treble_clef.py --png out.png

The SVG it writes is the one checked in at tools/treble-clef.svg. The Kotlin constant
it prints is TREBLE_CLEF_PATH_DATA in
core/designsystem/src/commonMain/.../component/TrebleClef.kt - regenerate rather than hand-edit.
"""

from __future__ import annotations

import argparse
import math
import struct
import zlib

# --- the stroke ----------------------------------------------------------------------------------

# The letter is drawn in the order a pen draws it: out of the spiral, round it, up the left, over
# the top, and back down the stem into the tail.
#
# The spiral round the G line, as a spiral rather than as guessed curves: an inner terminal that
# unwinds clockwise with a growing radius, which is the one part of the letter that cannot be faked
# with a handful of control points. Clockwise because the stroke has to leave the loop at its left
# travelling upwards, into the ascent; unwinding the other way meets the ascent head on and puts a
# cusp there. Angles in degrees, radius in staff spaces.
SPIRAL_CENTRE = (1.34, 2.30)
SPIRAL_FROM_DEGREES = 330.0
SPIRAL_TO_DEGREES = -180.0
SPIRAL_INNER_RADIUS = 0.22
SPIRAL_OUTER_RADIUS = 1.40
# The loop is a shade taller than it is wide, as an engraved one is.
SPIRAL_HEIGHT_RATIO = 1.0
SPIRAL_SAMPLES = 72

# The rest of the letter, from where the spiral leaves off: up the left, over the top, down the stem
# and into the tail. Each entry is (control1, control2, end) in (x in spaces, staff step).
CURVES = [
    # Up the left side and across the staff.
    ((0.14, 3.40), (0.80, 4.60), (1.30, 5.56)),
    ((1.76, 6.46), (2.04, 7.36), (1.94, 8.36)),
    # The curl over the top, which closes the narrow loop against the stem.
    ((1.88, 9.48), (1.52, 10.38), (1.12, 10.62)),
    ((0.82, 10.80), (0.62, 10.40), (0.68, 9.88)),
    ((0.74, 9.26), (1.10, 8.92), (1.48, 8.96)),
    # The stem, leaning very slightly, straight down through the spiral and out below the staff.
    ((1.56, 7.90), (1.60, 5.90), (1.58, 3.40)),
    ((1.56, 1.40), (1.52, -0.30), (1.50, -1.28)),
    # The tail, hooking left and finishing under the staff.
    ((1.48, -2.16), (1.14, -2.80), (0.54, -2.74)),
    ((0.18, -2.71), (0.00, -2.42), (0.02, -2.10)),
]

# The nib: a flat edge of this length, held at this angle above the horizontal. Together they decide
# every thick and every thin in the glyph, and they are the two numbers worth experimenting with.
NIB_LENGTH_SPACES = 0.30
NIB_ANGLE_DEGREES = 24.0

# The stroke never disappears entirely where it runs along the nib, and it comes to a point at the
# two ends - the spiral's terminal and the tip of the tail.
MIN_WIDTH_SPACES = 0.055
TAPER_START = 0.045
TAPER_END = 0.05

SAMPLES_PER_CURVE = 14

# Where the glyph sits: the spiral's terminal is drawn onto the G line.
G_LINE_STEP = 2


def bezier(p0, c1, c2, p3, t):
    u = 1.0 - t
    return (
        u * u * u * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * p3[0],
        u * u * u * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * p3[1],
    )


def spiral():
    """The loop round the G line, from its inner terminal outwards, in (x, step)."""
    cx, cstep = SPIRAL_CENTRE
    span = SPIRAL_TO_DEGREES - SPIRAL_FROM_DEGREES
    points = []
    for i in range(SPIRAL_SAMPLES + 1):
        degrees = SPIRAL_FROM_DEGREES + span * i / SPIRAL_SAMPLES
        radius = SPIRAL_INNER_RADIUS + (SPIRAL_OUTER_RADIUS - SPIRAL_INNER_RADIUS) * i / SPIRAL_SAMPLES
        angle = math.radians(degrees)
        points.append(
            (
                cx + radius * math.cos(angle),
                # Two steps to a space, so a radius in spaces is twice as many steps.
                cstep + radius * math.sin(angle) * 2.0 * SPIRAL_HEIGHT_RATIO,
            )
        )
    return points


def centreline():
    """The path, sampled, as (x in spaces, y in spaces) with y growing downwards from the G line."""
    steps = spiral()
    start = steps[-1]
    for c1, c2, end in CURVES:
        for i in range(1, SAMPLES_PER_CURVE + 1):
            steps.append(bezier(start, c1, c2, end, i / SAMPLES_PER_CURVE))
        start = end
    return [(x, (G_LINE_STEP - step) / 2.0) for x, step in steps]


def half_widths(points):
    """Half the stroke's width at each point: the nib's edge seen across the direction of travel."""
    nib = math.radians(NIB_ANGLE_DEGREES)
    # y grows downwards, so a nib rising to the right has a negative y component.
    nib_x, nib_y = math.cos(nib), -math.sin(nib)
    last = len(points) - 1

    widths = []
    for index, _ in enumerate(points):
        before = points[max(index - 1, 0)]
        after = points[min(index + 1, last)]
        dx, dy = after[0] - before[0], after[1] - before[1]
        length = math.hypot(dx, dy) or 1e-9
        tx, ty = dx / length, dy / length

        # How much of the nib's edge stands across the stroke: the cross product of the two.
        across = abs(nib_x * ty - nib_y * tx)
        half = max(NIB_LENGTH_SPACES / 2.0 * across, MIN_WIDTH_SPACES / 2.0)

        # Both terminals come to a point rather than stopping square.
        s = index / last
        if s < TAPER_START:
            half *= s / TAPER_START
        elif s > 1.0 - TAPER_END:
            half *= (1.0 - s) / TAPER_END
        widths.append(half)
    return widths


def outline():
    """The filled shape: up one side of the stroke and back down the other."""
    points = centreline()
    widths = half_widths(points)
    last = len(points) - 1

    left, right = [], []
    for index, (x, y) in enumerate(points):
        before = points[max(index - 1, 0)]
        after = points[min(index + 1, last)]
        dx, dy = after[0] - before[0], after[1] - before[1]
        length = math.hypot(dx, dy) or 1e-9
        # Perpendicular to the direction of travel.
        px, py = -dy / length, dx / length
        half = widths[index]
        left.append((x + px * half, y + py * half))
        right.append((x - px * half, y - py * half))

    shape = left + right[::-1]

    # Slide the glyph so it starts at x = 0. The height is left exactly where it is: the whole point
    # of the drawing is that the spiral's centre sits on the G line, and moving it would be a lie.
    shift = min(x for x, _ in shape)
    return [(x - shift, y) for x, y in shape]


def extent(shape):
    """What the glyph asks for: its width in spaces, and the staff steps it reaches."""
    width = max(x for x, _ in shape)
    lowest = G_LINE_STEP - max(y for _, y in shape) * 2.0
    highest = G_LINE_STEP - min(y for _, y in shape) * 2.0
    return width, lowest, highest


def path_data(shape, decimals=2):
    """The shape as SVG path data, which is also what Compose's PathParser reads."""
    parts = []
    for index, (x, y) in enumerate(shape):
        parts.append(f"{'M' if index == 0 else 'L'}{round(x, decimals)},{round(y, decimals)}")
    parts.append("Z")
    return "".join(parts)


# --- inspection ----------------------------------------------------------------------------------

STAFF_STEPS = range(0, 9, 2)
VIEW_STEPS = (-3, 11)
PREFIX_WIDTH_SPACES = 4.0
# The air the renderer leaves in front of the glyph, so this picture is what the app draws and not
# merely what the path data says. Kept in step with CLEF_INSET_SPACES in TrebleClef.kt.
GLYPH_INSET_SPACES = 0.35
STAFF_WIDTH_SPACES = 9.0


def placed():
    """The outline where the renderer puts it: the glyph plus the air in front of it."""
    return [(x + GLYPH_INSET_SPACES, y) for x, y in outline()]


def svg():
    """The glyph on a staff, at the size and place the app draws it."""
    top = (G_LINE_STEP - VIEW_STEPS[1]) / 2.0
    bottom = (G_LINE_STEP - VIEW_STEPS[0]) / 2.0
    height = bottom - top

    lines = []
    for step in STAFF_STEPS:
        y = (G_LINE_STEP - step) / 2.0
        lines.append(
            f'    <line x1="0" y1="{y:.3f}" x2="{STAFF_WIDTH_SPACES}" y2="{y:.3f}"'
            ' stroke="#222" stroke-width="0.08"/>'
        )
    # The line that closes the header block, where the music begins.
    lines.append(
        f'    <line x1="{PREFIX_WIDTH_SPACES}" y1="{(G_LINE_STEP - 8) / 2.0:.3f}"'
        f' x2="{PREFIX_WIDTH_SPACES}" y2="{(G_LINE_STEP - 0) / 2.0:.3f}"'
        ' stroke="#222" stroke-width="0.11"/>'
    )

    return "\n".join(
        [
            '<svg xmlns="http://www.w3.org/2000/svg"'
            f' viewBox="0 {top:.3f} {STAFF_WIDTH_SPACES} {height:.3f}"'
            f' width="{STAFF_WIDTH_SPACES * 40:.0f}" height="{height * 40:.0f}">',
            "  <!-- Generated by tools/treble_clef.py. One unit is one staff space. -->",
            '  <rect x="0" y="%.3f" width="%s" height="%.3f" fill="#fff"/>'
            % (top, STAFF_WIDTH_SPACES, height),
            "  <g>",
            *lines,
            "  </g>",
            f'  <path d="{path_data(placed())}" fill="#222"/>',
            "</svg>",
            "",
        ]
    )


def png(path, scale=60, supersample=3):
    """A picture of the same thing, because an SVG on disk is not something you can look at here."""
    top = (G_LINE_STEP - VIEW_STEPS[1]) / 2.0
    bottom = (G_LINE_STEP - VIEW_STEPS[0]) / 2.0
    width_px = int(STAFF_WIDTH_SPACES * scale)
    height_px = int((bottom - top) * scale)
    big_w, big_h = width_px * supersample, height_px * supersample

    coverage = bytearray(big_w * big_h)

    def to_px(point):
        return (point[0] * scale * supersample, (point[1] - top) * scale * supersample)

    def fill_polygon(shape):
        pts = [to_px(p) for p in shape]
        ys = [p[1] for p in pts]
        for y in range(max(int(min(ys)), 0), min(int(max(ys)) + 1, big_h)):
            centre = y + 0.5
            # Nonzero winding, which is what Compose fills with: where the stroke crosses itself
            # the shape stays solid instead of punching a hole in itself.
            crossings = []
            for i in range(len(pts)):
                x0, y0 = pts[i]
                x1, y1 = pts[(i + 1) % len(pts)]
                if y0 <= centre < y1:
                    crossings.append((x0 + (centre - y0) / (y1 - y0) * (x1 - x0), 1))
                elif y1 <= centre < y0:
                    crossings.append((x0 + (centre - y0) / (y1 - y0) * (x1 - x0), -1))
            crossings.sort()
            winding = 0
            for i in range(len(crossings) - 1):
                winding += crossings[i][1]
                if winding != 0:
                    for x in range(
                        max(int(crossings[i][0]), 0),
                        min(int(crossings[i + 1][0]) + 1, big_w),
                    ):
                        coverage[y * big_w + x] = 255

    def fill_rect(x0, y0, x1, y1):
        fill_polygon([(x0, y0), (x1, y0), (x1, y1), (x0, y1)])

    line_half = 0.04
    for step in STAFF_STEPS:
        y = (G_LINE_STEP - step) / 2.0
        fill_rect(0, y - line_half, STAFF_WIDTH_SPACES, y + line_half)
    fill_rect(
        PREFIX_WIDTH_SPACES - 0.055,
        (G_LINE_STEP - 8) / 2.0,
        PREFIX_WIDTH_SPACES + 0.055,
        (G_LINE_STEP - 0) / 2.0,
    )
    fill_polygon(placed())

    rows = []
    for y in range(height_px):
        row = bytearray([0])
        for x in range(width_px):
            total = 0
            for sy in range(supersample):
                base = (y * supersample + sy) * big_w + x * supersample
                for sx in range(supersample):
                    total += coverage[base + sx]
            ink = total // (supersample * supersample)
            row.append(255 - ink)
        rows.append(bytes(row))

    raw = b"".join(rows)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", width_px, height_px, 8, 0, 0, 0, 0)
    with open(path, "wb") as out:
        out.write(b"\x89PNG\r\n\x1a\n")
        out.write(chunk(b"IHDR", header))
        out.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        out.write(chunk(b"IEND", b""))


def kotlin(shape, columns=96, indent=0):
    """The path data as a Kotlin constant, wrapped to something a person can scroll past."""
    data = path_data(shape)
    room = columns - indent - 8
    chunks = [data[i : i + room] for i in range(0, len(data), room)]
    pad = " " * indent
    body = ('" +\n' + pad + '    "').join(chunks)
    return f'{pad}private const val TREBLE_CLEF_PATH_DATA =\n{pad}    "{body}"'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--svg", default="tools/treble-clef.svg")
    parser.add_argument("--png", help="also write a picture, for looking at")
    parser.add_argument("--kotlin", action="store_true", help="print the Kotlin constant")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    with open(args.svg, "w", encoding="utf-8", newline="\n") as out:
        out.write(svg())
    if args.png:
        png(args.png)

    shape = outline()
    if args.kotlin:
        print(kotlin(shape))
    elif not args.quiet:
        width, lowest, highest = extent(shape)
        print(f"{len(shape)} outline points")
        print(f"width {width:.3f} spaces, steps {lowest:.2f} to {highest:.2f}")
        print(path_data(shape))


if __name__ == "__main__":
    main()
