#!/usr/bin/env python3
"""Turn an SVG into the app's Android launcher icon.

The launcher icon is an adaptive icon (`app/src/main/res/mipmap-anydpi/ic_launcher.xml`) made of
layers. All of them come from one SVG:

* `drawable/ic_launcher_foreground.xml` - the drawing in its own colours.
* `drawable/ic_launcher_monochrome.xml` - the same shapes in one colour, for themed icons
  (Android 13+). The launcher uses only its alpha, so every shape comes out opaque white.
* The background is a flat colour: the SVG's page colour (Inkscape: Document Properties >
  Background, stored as `pagecolor`), written into `ic_launcher_background` in
  `values/colors.xml`. `drawable/ic_launcher_background.xml` only points at that colour. The page's
  opacity is ignored - a launcher background is always opaque.

Unlike `svg_to_vector.py`, colour is kept - a launcher icon is not tinted. The rest follows the same
rules and reuses its parsing: moves and resizes are baked in, `display:none` shapes are left out, and
anything that would come out wrong (strokes, gradients, non-path shapes, rotations) is an error.

The drawing's viewBox is scaled to ART_SIZE dp and centred on the 108 dp canvas. Launchers mask the
canvas to their own shape and show roughly the middle 72 dp; only a 66 dp circle is guaranteed to be
visible, so keep the important parts near the middle of the SVG.

Usage (from the repository root; both arguments are optional):
    python tools/svg_to_launcher_icon.py [source.svg] [res-directory]
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

from svg_to_vector import (
    IDENTITY,
    NON_PATH_SHAPES,
    UNSUPPORTED,
    Affine,
    ConversionError,
    compose,
    is_hidden,
    local_name,
    parse_transform,
    parse_view_box,
    transform_path,
)

REPOSITORY = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = REPOSITORY / "additional" / "icons" / "app_icons.svg"
DEFAULT_RES = REPOSITORY / "app" / "src" / "main" / "res"

CANVAS_SIZE = 108.0
# 24 SVG units become 60 dp: the drawing's 2 px margin keeps it inside the part launchers show.
ART_SIZE = 60.0

MONOCHROME_COLOR = "#FFFFFFFF"

NAMED_COLORS = {"black": "000000", "white": "FFFFFF"}

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from {source} by tools/svg_to_launcher_icon.py. Do not edit; change the SVG and run it again. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{canvas:g}dp"
    android:height="{canvas:g}dp"
    android:viewportWidth="{canvas:g}"
    android:viewportHeight="{canvas:g}">
    <group
        android:scaleX="{scale:g}"
        android:scaleY="{scale:g}"
        android:translateX="{translate_x:g}"
        android:translateY="{translate_y:g}">
{paths}
    </group>
</vector>
"""

PATH_TEMPLATE = """        <path
            android:fillColor="{color}"{fill_type}
            android:pathData="{data}" />"""


class Paint:
    """The fill a path is drawn with, as inherited down the document."""

    def __init__(self, fill: str = "#000000", opacity: float = 1.0, rule: str = "nonzero") -> None:
        self.fill = fill
        self.opacity = opacity
        self.rule = rule


def properties_of(element: ElementTree.Element) -> dict[str, str]:
    """Presentation attributes, overridden by `style="..."` as in CSS."""
    result = {
        key: value
        for key in ("fill", "fill-opacity", "fill-rule", "opacity", "stroke", "stroke-width")
        if (value := element.get(key)) is not None
    }
    for declaration in (element.get("style") or "").split(";"):
        if ":" in declaration:
            key, value = (part.strip() for part in declaration.split(":", 1))
            result[key] = value
    return result


def argb_of(fill: str, opacity: float) -> str:
    """An SVG colour and opacity as Android's #AARRGGBB."""
    raw = fill.strip().lower()
    if raw in NAMED_COLORS:
        rgb = NAMED_COLORS[raw]
    elif re.fullmatch(r"#[0-9a-f]{6}", raw):
        rgb = raw[1:].upper()
    elif re.fullmatch(r"#[0-9a-f]{3}", raw):
        rgb = "".join(c * 2 for c in raw[1:]).upper()
    else:
        raise ConversionError(f'fill "{fill}" is not a plain colour; use a hex colour such as #e9c46a')
    alpha = round(max(0.0, min(1.0, opacity)) * 255)
    return f"#{alpha:02X}{rgb}"


def collect_paths(root: ElementTree.Element) -> list[tuple[str, str, str]]:
    """Every drawable path, as (path data, #AARRGGBB, fill type), in document order."""
    paths: list[tuple[str, str, str]] = []

    def visit(element: ElementTree.Element, inherited: Affine, paint: Paint) -> None:
        name = local_name(element.tag)
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

        props = properties_of(element)
        stroke = props.get("stroke", "none").strip()
        if stroke != "none" and float(re.sub(r"[a-z%]", "", props.get("stroke-width", "1")) or 0) > 0:
            raise ConversionError("stroked shape; in Inkscape use Path > Stroke to Path first")

        opacity = float(props.get("opacity", "1"))
        is_path = name == "path" and element.get("d")
        if opacity < 1 and not is_path:
            raise ConversionError(f"<{name}> has an opacity of its own; set it on the paths instead")

        paint = Paint(
            fill=props.get("fill", paint.fill),
            opacity=paint.opacity * float(props.get("fill-opacity", "1")),
            rule=props.get("fill-rule", paint.rule),
        )

        if is_path and paint.fill.strip() != "none":
            paths.append((
                transform_path(element.get("d").strip(), affine),
                argb_of(paint.fill, paint.opacity * opacity),
                "evenOdd" if paint.rule == "evenodd" else "nonZero",
            ))

        for child in element:
            visit(child, affine, paint)

    visit(root, IDENTITY, Paint())

    if not paths:
        raise ConversionError("no <path> to draw")
    return paths


def page_color_of(root: ElementTree.Element) -> str:
    """The Inkscape page colour, as an opaque #AARRGGBB."""
    for element in root.iter():
        if local_name(element.tag) == "namedview" and element.get("pagecolor"):
            return argb_of(element.get("pagecolor"), 1.0)
    raise ConversionError(
        "no page colour for the background; save as Inkscape SVG (not Plain SVG) and set "
        "Document Properties > Background"
    )


_BACKGROUND_COLOR = re.compile(r'(<color\s+name="ic_launcher_background"\s*>)[^<]*(</color>)')


def with_background(colors_xml: str, color: str) -> str:
    """`colors.xml` with `ic_launcher_background` set to [color], everything else untouched."""
    updated, count = _BACKGROUND_COLOR.subn(rf"\g<1>{color}\g<2>", colors_xml)
    if count != 1:
        raise ConversionError('values/colors.xml has no <color name="ic_launcher_background">')
    return updated


def vector_of(svg: str, source: str, monochrome: bool) -> str:
    root = ElementTree.fromstring(svg)
    if local_name(root.tag) != "svg":
        raise ConversionError("not an SVG document")

    min_x, min_y, width, height = parse_view_box(root)
    if width <= 0 or height <= 0:
        raise ConversionError(f"viewBox has no area ({width} x {height})")
    if width != height:
        raise ConversionError(f"viewBox is not square ({width} x {height})")

    scale = ART_SIZE / width
    offset = (CANVAS_SIZE - ART_SIZE) / 2
    return TEMPLATE.format(
        source=source,
        canvas=CANVAS_SIZE,
        scale=round(scale, 6),
        translate_x=round(offset - min_x * scale, 6),
        translate_y=round(offset - min_y * scale, 6),
        paths="\n".join(
            PATH_TEMPLATE.format(
                color=MONOCHROME_COLOR if monochrome else color,
                fill_type="" if fill_type == "nonZero" else f'\n            android:fillType="{fill_type}"',
                data=data,
            )
            for data, color, fill_type in collect_paths(root)
        ),
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", nargs="?", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("res", nargs="?", type=Path, default=DEFAULT_RES)
    arguments = parser.parse_args()

    source: Path = arguments.source.resolve()
    try:
        label = source.relative_to(REPOSITORY).as_posix()
    except ValueError:
        label = source.name

    res: Path = arguments.res.resolve()
    colors = res / "values" / "colors.xml"
    # Everything is converted before anything is written, so a failure leaves no half-updated icon.
    try:
        svg = source.read_text(encoding="utf-8")
        background = page_color_of(ElementTree.fromstring(svg))
        outputs = {
            res / "drawable" / "ic_launcher_foreground.xml": vector_of(svg, label, monochrome=False),
            res / "drawable" / "ic_launcher_monochrome.xml": vector_of(svg, label, monochrome=True),
            colors: with_background(colors.read_text(encoding="utf-8"), background),
        }
    except (ConversionError, ElementTree.ParseError, OSError) as failure:
        print(f"error: {source}: {failure}", file=sys.stderr)
        return 1

    for destination, content in outputs.items():
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8", newline="\n")
        shown = destination.relative_to(REPOSITORY) if destination.is_relative_to(REPOSITORY) else destination
        print(f"{label} -> {shown}")
    print(f"background {background}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
