#!/usr/bin/env python3
"""Turn an SVG icon into a Compose `ImageVector`.

Icons used to ship as Android `<vector>` resources. They are Kotlin now, because a resource is
reachable from Android only and the app's UI is shared - see `core/designsystem/.../icon/`. The path
data is identical either way; only the wrapper changed, so this converter did too.

Two things this does that a copy and paste would get wrong:

* Material Symbols export with `viewBox="0 -960 960 960"`, i.e. the drawing sits above the origin.
  A vector's viewport always starts at 0,0, so the paths go inside a group that shifts them back
  down. The same handling covers any other offset viewBox.
* Colour is dropped. Icons are drawn through `Icon(imageVector, tint = ...)`, which replaces the
  fill with the theme colour, so whatever colour the SVG was exported in is noise.

Two things it fixes up that Inkscape tends to leave behind:

* A `transform` that only moves and resizes - `translate`, `scale`, or a `matrix` with no rotation
  or skew, on a path or on any group around it - is baked into the path data. That is exact, and it
  is what Inkscape leaves on a path that was pasted in or resized.
* Anything hidden with `display:none` - a template frame, a guide shape - is left out, as it is when
  the SVG is drawn.

Anything it cannot convert faithfully is an error rather than a silently wrong icon: rotated or
skewed transforms, gradients, strokes, and shapes that are not paths. Inkscape can remove all of
those - see the notes in `tools/icons.map`.

Usage:
    python tools/svg_to_vector.py <input.svg> <Output.kt>
    python tools/svg_to_vector.py --map tools/icons.map
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

SVG_NS = "http://www.w3.org/2000/svg"

# Shapes that carry geometry but are not paths. Inkscape turns them into paths with
# Path > Object to Path, which is the fix rather than reimplementing each one here.
NON_PATH_SHAPES = {"rect", "circle", "ellipse", "line", "polyline", "polygon", "text", "use"}

# Constructs a vector drawable cannot represent the way an SVG means them.
UNSUPPORTED = {"linearGradient", "radialGradient", "mask", "clipPath", "pattern", "filter", "style"}

TEMPLATE = """package com.example.saxtrainer.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** Generated from `{source}` - see `tools/icons.map`. */
val SaxTrainerIcons.{name}: ImageVector get() = {lower}

private val {lower}: ImageVector by lazy(LazyThreadSafetyMode.NONE) {{
    saxIcon(
        "{name}",
        viewport = {viewport:g}f,
{paths}{translate}
    )
}}
"""


class ConversionError(Exception):
    """Something in the SVG would come out wrong, so nothing is written."""


# An axis-aligned affine transform: x' = sx * x + tx, y' = sy * y + ty. Rotation and skew are
# refused rather than approximated.
Affine = tuple[float, float, float, float]
IDENTITY: Affine = (1.0, 1.0, 0.0, 0.0)


def parse_transform(raw: str | None) -> Affine:
    """The transform attribute as one axis-aligned affine, or an error if it rotates or skews."""
    if not raw:
        return IDENTITY
    result = IDENTITY
    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", raw):
        values = [float(v) for v in re.split(r"[\s,]+", args.strip()) if v]
        if name == "matrix" and len(values) == 6:
            a, b, c, d, e, f = values
            if abs(b) > 1e-9 or abs(c) > 1e-9:
                raise ConversionError(f'transform "{raw}" rotates or skews; flatten it in the editor')
            step = (a, d, e, f)
        elif name == "translate" and values:
            step = (1.0, 1.0, values[0], values[1] if len(values) > 1 else 0.0)
        elif name == "scale" and values:
            step = (values[0], values[1] if len(values) > 1 else values[0], 0.0, 0.0)
        else:
            raise ConversionError(f'transform "{raw}" is not a move or a resize; flatten it first')
        result = compose(result, step)
    return result


def compose(outer: Affine, inner: Affine) -> Affine:
    """`inner` first, then `outer` - the order nested SVG transforms apply in."""
    osx, osy, otx, oty = outer
    isx, isy, itx, ity = inner
    return (osx * isx, osy * isy, osx * itx + otx, osy * ity + oty)


_NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_ARG_COUNT = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def read_path(data: str) -> list[tuple[str, list[float]]]:
    """Path data as (command, arguments), implicit repeats split out and arc flags read one digit each."""
    commands: list[tuple[str, list[float]]] = []
    position = 0
    command = None

    def skip() -> None:
        nonlocal position
        while position < len(data) and (data[position].isspace() or data[position] == ","):
            position += 1

    def number() -> float:
        nonlocal position
        skip()
        match = _NUMBER.match(data, position)
        if not match:
            raise ConversionError(f"unreadable path data near {data[position:position + 20]!r}")
        position = match.end()
        return float(match.group())

    def flag() -> float:
        nonlocal position
        skip()
        if position >= len(data) or data[position] not in "01":
            raise ConversionError(f"unreadable arc flag near {data[position:position + 20]!r}")
        position += 1
        return float(data[position - 1])

    while True:
        skip()
        if position >= len(data):
            break
        if data[position].isalpha():
            command = data[position]
            position += 1
            if command.upper() not in _ARG_COUNT:
                raise ConversionError(f"path command {command!r} is not supported")
            if command.upper() == "Z":
                commands.append((command, []))
                continue
        elif command is None or command.upper() == "Z":
            raise ConversionError(f"numbers without a command in path data near {data[position:position + 20]!r}")
        if command.upper() == "A":
            args = [number(), number(), number(), flag(), flag(), number(), number()]
        else:
            args = [number() for _ in range(_ARG_COUNT[command.upper()])]
        commands.append((command, args))
        # Coordinate pairs after a moveto are linetos of the same kind.
        if command == "M":
            command = "L"
        elif command == "m":
            command = "l"
    return commands


def _format(value: float) -> str:
    text = f"{value:.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def transform_path(data: str, affine: Affine) -> str:
    """`data` with `affine` baked in, written out as absolute commands. Unchanged for the identity."""
    if affine == IDENTITY:
        return data
    sx, sy, tx, ty = affine

    def point(x: float, y: float) -> str:
        return f"{_format(sx * x + tx)} {_format(sy * y + ty)}"

    out: list[str] = []
    x = y = start_x = start_y = 0.0
    for command, args in read_path(data):
        relative = command.islower()
        kind = command.upper()
        if kind == "Z":
            out.append("Z")
            x, y = start_x, start_y
        elif kind == "H":
            x = args[0] + (x if relative else 0.0)
            out.append(f"H{_format(sx * x + tx)}")
        elif kind == "V":
            y = args[0] + (y if relative else 0.0)
            out.append(f"V{_format(sy * y + ty)}")
        elif kind == "A":
            rx, ry, angle, large, sweep, end_x, end_y = args
            if relative:
                end_x, end_y = end_x + x, end_y + y
            if abs(angle) > 1e-9 and abs(abs(sx) - abs(sy)) > 1e-6 * max(abs(sx), abs(sy)):
                raise ConversionError("a rotated arc under an uneven scale cannot be baked in")
            if sx * sy < 0:
                sweep = 1.0 - sweep
            out.append(
                f"A{_format(abs(rx * sx))} {_format(abs(ry * sy))} {_format(angle)} "
                f"{int(large)} {int(sweep)} {point(end_x, end_y)}"
            )
            x, y = end_x, end_y
        else:
            points = []
            for k in range(0, len(args), 2):
                px, py = args[k], args[k + 1]
                if relative:
                    px, py = px + x, py + y
                points.append((px, py))
            out.append(kind + " ".join(point(px, py) for px, py in points))
            x, y = points[-1]
            if kind == "M":
                start_x, start_y = x, y
    return " ".join(out)


def is_hidden(element: ElementTree.Element) -> bool:
    style = element.get("style") or ""
    return element.get("display") == "none" or re.search(r"display\s*:\s*none", style) is not None


def local_name(tag: str) -> str:
    return tag.split("}", 1)[-1]


def parse_view_box(root: ElementTree.Element) -> tuple[float, float, float, float]:
    raw = root.get("viewBox")
    if raw is None:
        width, height = root.get("width"), root.get("height")
        if width is None or height is None:
            raise ConversionError("no viewBox and no width/height to fall back on")
        return 0.0, 0.0, float(re.sub(r"[a-z%]", "", width)), float(re.sub(r"[a-z%]", "", height))

    parts = [float(value) for value in re.split(r"[\s,]+", raw.strip())]
    if len(parts) != 4:
        raise ConversionError(f'viewBox "{raw}" does not have four numbers')
    return parts[0], parts[1], parts[2], parts[3]


def collect_paths(root: ElementTree.Element) -> list[tuple[str, str]]:
    """Every drawable path, as (path data, fill type), in document order."""
    paths: list[tuple[str, str]] = []

    def visit(element: ElementTree.Element, inherited: Affine) -> None:
        name = local_name(element.tag)
        # Hidden is hidden, whatever it holds: a template layer switched off in the editor.
        if element is not root and is_hidden(element):
            return
        if name in UNSUPPORTED:
            raise ConversionError(f"<{name}> cannot be converted; flatten it in the editor first")
        if name in NON_PATH_SHAPES:
            raise ConversionError(
                f"<{name}> is not a path; in Inkscape use Path > Object to Path and export again"
            )
        affine = inherited
        if element is not root:
            affine = compose(inherited, parse_transform(element.get("transform")))

        data = element.get("d") if name == "path" else None
        if data:
            if element.get("fill") == "none" and element.get("stroke"):
                raise ConversionError("stroked path; convert the stroke to a filled outline first")
            fill_rule = element.get("fill-rule") or element.get("clip-rule") or "nonzero"
            paths.append((
                transform_path(data.strip(), affine),
                "evenOdd" if fill_rule == "evenodd" else "nonZero",
            ))

        for child in element:
            visit(child, affine)

    visit(root, IDENTITY)

    if not paths:
        raise ConversionError("no <path> to draw")
    return paths


def kotlin_of(svg: str, name: str, source: str) -> str:
    root = ElementTree.fromstring(svg)
    if local_name(root.tag) != "svg":
        raise ConversionError("not an SVG document")

    min_x, min_y, width, height = parse_view_box(root)
    if width <= 0 or height <= 0:
        raise ConversionError(f"viewBox has no area ({width} x {height})")
    if width != height:
        raise ConversionError(f"viewBox is not square ({width} x {height})")

    paths = collect_paths(root)
    if any(fill_type != "nonZero" for _, fill_type in paths):
        # Representable, just not needed yet - saxIcon takes no per-path fill type.
        raise ConversionError("an even-odd fill would be dropped")
    if min_x != 0:
        raise ConversionError(f"a horizontal viewBox offset ({min_x:g}) is not handled")

    return TEMPLATE.format(
        source=source,
        name=name,
        lower=name[0].lower() + name[1:],
        viewport=width,
        # A vector's viewport starts at 0,0, so an offset viewBox is undone by a group.
        translate=f"\n        translateY = {-min_y:g}f," if min_y else "",
        paths="\n".join(f'        "{data}",' for data, _ in paths),
    )


def convert(source: Path, destination: Path) -> None:
    if destination.suffix != ".kt":
        raise ConversionError(f"{destination}: destination must be a .kt file")
    try:
        kotlin = kotlin_of(source.read_text(encoding="utf-8"), destination.stem, source.as_posix())
    except (ConversionError, ElementTree.ParseError) as failure:
        raise ConversionError(f"{source}: {failure}") from failure

    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(kotlin, encoding="utf-8", newline="\n")
    print(f"{source} -> {destination}")


def read_map(path: Path) -> list[tuple[Path, Path]]:
    """`source.svg -> module/.../icon/Name.kt` per line, # for comments."""
    root = path.parent.parent
    entries = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if "->" not in line:
            raise ConversionError(f"{path}:{number}: expected 'source.svg -> Destination.kt'")
        source, destination = (part.strip() for part in line.split("->", 1))
        entries.append((root / source, root / destination))
    return entries


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", type=Path)
    parser.add_argument("destination", nargs="?", type=Path)
    parser.add_argument("--map", type=Path, help="convert every icon listed in a map file")
    arguments = parser.parse_args()

    try:
        if arguments.map:
            for source, destination in read_map(arguments.map):
                convert(source, destination)
        elif arguments.source and arguments.destination:
            convert(arguments.source, arguments.destination)
        else:
            parser.error("give a source and a destination, or --map")
    except ConversionError as failure:
        print(f"error: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
