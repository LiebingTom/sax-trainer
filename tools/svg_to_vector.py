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

Anything it cannot convert faithfully is an error rather than a silently wrong icon: transforms,
gradients, strokes, and shapes that are not paths. Inkscape can remove all of those - see the notes
in `tools/icons.map`.

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

    for element in root.iter():
        name = local_name(element.tag)

        if name in UNSUPPORTED:
            raise ConversionError(f"<{name}> cannot be converted; flatten it in the editor first")
        if name in NON_PATH_SHAPES:
            raise ConversionError(
                f"<{name}> is not a path; in Inkscape use Path > Object to Path and export again"
            )
        if element is not root and element.get("transform"):
            raise ConversionError(
                f"<{name}> carries a transform; in Inkscape use Path > Object to Path, or "
                "flatten the transform, and export again"
            )
        if name != "path":
            continue

        data = element.get("d")
        if not data:
            continue
        if element.get("fill") == "none" and element.get("stroke"):
            raise ConversionError("stroked path; convert the stroke to a filled outline first")

        fill_rule = element.get("fill-rule") or element.get("clip-rule") or "nonzero"
        paths.append((data.strip(), "evenOdd" if fill_rule == "evenodd" else "nonZero"))

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
