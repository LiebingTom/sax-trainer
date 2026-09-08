"""Turns Bravura's music glyphs into Compose path data, and draws a sheet of what it produced.

The app draws notation without a font. It needs the shapes, not the typography: a note head is a
letterform that has to sit on a staff line at whatever size the staff happens to be, which is a
drawing problem, not a text one. So the outlines are lifted out of Bravura once, here, and checked
into `BravuraGlyphs.kt` as path data in staff spaces. Nothing at runtime knows a font exists.

    python tools/bravura_glyphs.py --download     # fetch Bravura once, into tools/.bravura
    python tools/bravura_glyphs.py --kotlin       # rewrite core/designsystem BravuraGlyphs.kt
    python tools/bravura_glyphs.py --svg          # rewrite tools/notation-glyphs.svg

Source and licence
------------------
Bravura, by Steinberg Media Technologies GmbH, under the SIL Open Font License 1.1, with Reserved
Font Name "Bravura". https://github.com/steinbergmedia/bravura

Only the outlines are used, as vector artwork. The OFL FAQ covers this directly: 1.1 allows using a
font's outlines to create graphics or "manufacture objects based on their outlines", and 1.1.1 says
the resulting artwork is the author's, with no further condition. The font itself is neither shipped
nor committed - `tools/.bravura/` is ignored - so none of the redistribution clauses apply. The
Reserved Font Name is untouched: nothing here is called Bravura except this attribution.

How the coordinates work
------------------------
SMuFL registers glyphs so that one staff space is 0.25 em, and so that the origin sits on the thing
the glyph refers to: the G line for a G clef, the notehead's own centre line for a note head or an
accidental, the bottom staff line for a bar line. Dividing by upm/4 and flipping y therefore lands
straight in the app's own coordinate system - staff spaces, y running down - with the anchoring
already correct. That is why the drawing code needs no magic numbers to place any of this.
"""
import argparse
import json
import pathlib
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / ".bravura"
OTF = CACHE / "Bravura.otf"
META = CACHE / "Bravura.json"
KOTLIN = (ROOT / "core/designsystem/src/commonMain/kotlin/com/example/saxtrainer/core"
                 "/designsystem/component/BravuraGlyphs.kt")
SHEET = ROOT / "tools" / "notation-glyphs.svg"

BASE = "https://raw.githubusercontent.com/steinbergmedia/bravura/HEAD/redist/"

# The glyphs the trainer draws, by their SMuFL code points.
CODEPOINTS = {
    "gClef": 0xE050,
    "noteheadWhole": 0xE0A2,
    "noteheadHalf": 0xE0A3,
    "noteheadBlack": 0xE0A4,
    "flag8thUp": 0xE240,
    "flag8thDown": 0xE241,
    "flag16thUp": 0xE242,
    "flag16thDown": 0xE243,
    "accidentalFlat": 0xE260,
    "accidentalNatural": 0xE261,
    "accidentalSharp": 0xE262,
    "augmentationDot": 0xE1E7,
    "barlineSingle": 0xE030,
    "barlineDouble": 0xE031,
    "barlineFinal": 0xE032,
}

# The ten digits a time signature is built from, in order, so 0 is at index 0.
TIME_SIG_CODEPOINTS = {"timeSig%d" % d: 0xE080 + d for d in range(10)}
CODEPOINTS.update(TIME_SIG_CODEPOINTS)

DECIMALS = 3
NL = chr(10)


def download():
    CACHE.mkdir(parents=True, exist_ok=True)
    for url, target in ((BASE + "otf/Bravura.otf", OTF), (BASE + "Bravura.json", META)):
        print("fetching", url)
        urllib.request.urlretrieve(url, target)
        print("  ->", target, target.stat().st_size, "bytes")


class Bravura:
    """The font, read once: outlines in staff spaces plus the metrics that place them."""

    def __init__(self):
        if not OTF.exists() or not META.exists():
            sys.exit("Bravura is not here yet. Run: python tools/bravura_glyphs.py --download")
        try:
            from fontTools.ttLib import TTFont
            from fontTools.pens.svgPathPen import SVGPathPen
            from fontTools.pens.transformPen import TransformPen
            from fontTools.misc.transform import Transform
        except ImportError:
            sys.exit("this script needs fontTools, at build time only: pip install fonttools")

        font = TTFont(str(OTF))
        self.meta = json.loads(META.read_text(encoding="utf-8"))
        self.version = self.meta.get("fontVersion")
        cmap = font.getBestCmap()
        glyphs = font.getGlyphSet()
        units = font["head"].unitsPerEm / 4.0        # SMuFL: one staff space is 0.25 em

        self.paths = {}
        for name, cp in CODEPOINTS.items():
            pen = SVGPathPen(glyphs, ntos=lambda v: format(round(v, DECIMALS)))
            glyphs[cmap[cp]].draw(
                TransformPen(pen, Transform(1 / units, 0, 0, -1 / units, 0, 0))
            )
            self.paths[name] = pen.getCommands()

        self.defaults = self.meta["engravingDefaults"]
        self.anchors = self.meta["glyphsWithAnchors"]

    def width(self, glyph):
        return self.meta["glyphBBoxes"][glyph]["bBoxNE"][0]

    def top(self, glyph):
        """Highest point, in staff spaces above the origin."""
        return self.meta["glyphBBoxes"][glyph]["bBoxNE"][1]

    def bottom(self, glyph):
        return self.meta["glyphBBoxes"][glyph]["bBoxSW"][1]

    def anchor_y(self, glyph, anchor):
        """An anchor's vertical offset, converted to y running down."""
        return -self.anchors[glyph][anchor][1]


# --- Kotlin -----------------------------------------------------------------------------------

def literal(data, indent="    ", width=96):
    """A Kotlin string constant. Adjacent literals do not concatenate in Kotlin, so join with +."""
    chunks = [data[i:i + width] for i in range(0, len(data), width)]
    return (NL + indent + '"' + ('" +' + NL + indent + '"').join(chunks) + '"')


def f(value):
    return "%gf" % round(value, DECIMALS)


def kotlin(b):
    steps_top = 2 + 2 * b.top("gClef")
    steps_bottom = 2 + 2 * b.bottom("gClef")
    lines = [
        "package com.example.saxtrainer.core.designsystem.component",
        "",
        "import androidx.compose.ui.graphics.Path",
        "import androidx.compose.ui.graphics.vector.PathParser",
        "",
        "/**",
        " * The notation glyphs, as outlines rather than as a font.",
        " *",
        " * Generated - do not edit by hand:",
        " *",
        " *     python tools/bravura_glyphs.py --kotlin",
        " *",
        " * The shapes come from Bravura %s by Steinberg Media Technologies GmbH, under the SIL"
        % b.version,
        " * Open Font License 1.1 with Reserved Font Name \"Bravura\". Only the outlines are used,"
        " as",
        " * vector artwork, which OFL-FAQ 1.1 allows without further condition; the font itself is",
        " * neither shipped nor depended on at runtime. See `tools/bravura_glyphs.py` for the",
        " * extraction, and `tools/notation-glyphs.svg` for what these shapes look like drawn.",
        " *",
        " * Everything is in staff spaces, with y running down from whatever the glyph is anchored",
        " * to - the G line for the clef, the note's own line or space for a head, an accidental or",
        " * a dot, the bottom staff line for a bar line. SMuFL registers glyphs that way, so a",
        " * shape drawn at the point it belongs to is already in the right place, at any size.",
        " */",
        "internal object BravuraGlyphs {",
        "",
    ]

    order = [
        ("gClef", "gClef", "The G clef, anchored on the G line."),
        ("noteheadWhole", "noteheadWhole", "A whole note's head: no stem, so no stem anchor."),
        ("noteheadHalf", "noteheadHalf", "A half note's head, hollow."),
        ("noteheadBlack", "noteheadBlack", "Every head shorter than a half, filled."),
        ("flag8thUp", "flag8thUp", "One flag, hanging from the top of an upward stem."),
        ("flag8thDown", "flag8thDown", "One flag, rising from the foot of a downward stem."),
        ("flag16thUp", "flag16thUp", "Two flags in one shape."),
        ("flag16thDown", "flag16thDown", "Two flags in one shape."),
        ("accidentalSharp", "accidentalSharp", "A sharp, centred on the note's own line."),
        ("accidentalFlat", "accidentalFlat", "A flat; it reaches well above the note and little"
                                             " below it."),
        ("accidentalNatural", "accidentalNatural", "A natural."),
        ("augmentationDot", "augmentationDot", "One augmentation dot."),
        ("barlineSingle", "barlineSingle", "A thin bar line, four spaces tall from the bottom"
                                           " line."),
        ("barlineDouble", "barlineDouble", "Two thin bar lines: the end of a section."),
        ("barlineFinal", "barlineFinal", "Thin then thick: the end of a piece."),
    ]

    for prop, glyph, doc in order:
        lines += ["    /** %s */" % doc,
                  "    val %s: Path by glyph(%s)" % (prop, camel_const(prop)),
                  ""]

    lines += [
        "    /**",
        "     * The ten digits a time signature is made of, indexed by the digit itself.",
        "     *",
        "     * A list rather than ten properties because a time signature is read off a number:",
        "     * nothing here knows or cares whether that number is 4, 6 or 12.",
        "     */",
        "    val timeSignatureDigits: List<Path> by lazy(LazyThreadSafetyMode.NONE) {",
        "        TIME_SIGNATURE_DATA.map { PathParser().parsePathString(it).toPath() }",
        "    }",
        "",
        "    /** How wide each of those digits is, in staff spaces. */",
        "    val timeSignatureDigitWidths: List<Float> = listOf(",
        "        " + ", ".join(f(b.width("timeSig%d" % d)) for d in range(10)) + ",",
        "    )",
        "",
        "    /** Half a digit's height: they are all two staff spaces tall, centred on a line. */",
        "    const val TIME_SIGNATURE_HALF_HEIGHT: Float = 1f",
        "",
        "    // --- metrics, from the font's own bounding boxes and anchors ---------------------",
        "",
        "    /** Head widths, so a head can be centred on the position the layout gives it. */",
        "    const val NOTEHEAD_WIDTH: Float = %s" % f(b.width("noteheadBlack")),
        "    const val NOTEHEAD_WHOLE_WIDTH: Float = %s" % f(b.width("noteheadWhole")),
        "",
        "    /**",
        "     * How far from the head's centre a stem meets it.",
        "     *",
        "     * A stem drawn from the centre would show through a hollow head; from here it only",
        "     * touches the outline, which is what the anchor is for.",
        "     */",
        "    const val STEM_ATTACH_SPACES: Float = %s"
        % f(-b.anchor_y("noteheadBlack", "stemUpSE")),
        "",
        "    /** Where a flag's own attachment point sits, relative to the end of the stem. */",
        "    const val FLAG_8_UP_ATTACH: Float = %s" % f(b.anchor_y("flag8thUp", "stemUpNW")),
        "    const val FLAG_8_DOWN_ATTACH: Float = %s" % f(b.anchor_y("flag8thDown", "stemDownSW")),
        "    const val FLAG_16_UP_ATTACH: Float = %s" % f(b.anchor_y("flag16thUp", "stemUpNW")),
        "    const val FLAG_16_DOWN_ATTACH: Float = %s"
        % f(b.anchor_y("flag16thDown", "stemDownSW")),
        "",
        "    /** Accidental widths, so they can be hung to the left of the head by their edge. */",
        "    const val SHARP_WIDTH: Float = %s" % f(b.width("accidentalSharp")),
        "    const val FLAT_WIDTH: Float = %s" % f(b.width("accidentalFlat")),
        "    const val NATURAL_WIDTH: Float = %s" % f(b.width("accidentalNatural")),
        "    const val DOT_WIDTH: Float = %s" % f(b.width("augmentationDot")),
        "",
        "    /** Bar line widths, which is how far each one reaches right of where it is drawn. */",
        "    const val BAR_LINE_SINGLE_WIDTH: Float = %s" % f(b.width("barlineSingle")),
        "    const val BAR_LINE_DOUBLE_WIDTH: Float = %s" % f(b.width("barlineDouble")),
        "    const val BAR_LINE_FINAL_WIDTH: Float = %s" % f(b.width("barlineFinal")),
        "",
        "    /** How far the clef reaches, in staff steps, so a staff can make room for it. */",
        "    const val CLEF_TOP_STEP: Int = %d" % int(steps_top + 0.999),
        "    const val CLEF_BOTTOM_STEP: Int = %d" % -int(-steps_bottom + 0.999),
        "    const val CLEF_WIDTH: Float = %s" % f(b.width("gClef")),
        "",
        "    // --- Bravura's engraving defaults, so the lines match the shapes -----------------",
        "",
        "    const val STAFF_LINE_THICKNESS: Float = %s" % f(b.defaults["staffLineThickness"]),
        "    const val STEM_THICKNESS: Float = %s" % f(b.defaults["stemThickness"]),
        "    const val LEDGER_LINE_THICKNESS: Float = %s" % f(b.defaults["legerLineThickness"]),
        "    const val LEDGER_LINE_EXTENSION: Float = %s" % f(b.defaults["legerLineExtension"]),
        "    const val BEAM_THICKNESS: Float = %s" % f(b.defaults["beamThickness"]),
        "    const val BEAM_SPACING: Float = %s" % f(b.defaults["beamSpacing"]),
        "    const val TIE_ENDPOINT_THICKNESS: Float = %s"
        % f(b.defaults["tieEndpointThickness"]),
        "    const val TIE_MIDPOINT_THICKNESS: Float = %s"
        % f(b.defaults["tieMidpointThickness"]),
        "",
        "    /** Parsed once each, on first use, and drawn through a transform ever after. */",
        "    private fun glyph(data: String): Lazy<Path> =",
        "        lazy(LazyThreadSafetyMode.NONE) { PathParser().parsePathString(data).toPath() }",
        "}",
        "",
    ]

    for prop, glyph, _doc in order:
        lines += ["private const val %s =%s" % (camel_const(prop), literal(b.paths[glyph])), ""]

    for d in range(10):
        lines += ["private const val TIME_SIGNATURE_%d =%s"
                  % (d, literal(b.paths["timeSig%d" % d])), ""]
    lines += ["private val TIME_SIGNATURE_DATA = listOf(",
              "    " + ", ".join("TIME_SIGNATURE_%d" % d for d in range(10)) + ",",
              ")", ""]

    KOTLIN.write_text(NL.join(lines), encoding="utf-8")
    print("wrote", KOTLIN, KOTLIN.stat().st_size, "bytes")


def camel_const(name):
    out = []
    for ch in name:
        if ch.isupper() or ch.isdigit():
            if out and not out[-1].endswith("_") and not (ch.isdigit() and out[-1][-1].isdigit()):
                out.append("_")
        out.append(ch.upper())
    return "".join(out).replace("__", "_")


# --- the preview sheet -------------------------------------------------------------------------

def svg(b):
    """The same sheet that was reviewed before any of this was integrated."""
    S = 30.0
    MARGIN = 28.0
    INK = "#111"
    LABEL = "#555"
    STAFF_LINE = b.defaults["staffLineThickness"]
    STEM_W = b.defaults["stemThickness"]
    STEM_ATTACH = -b.anchor_y("noteheadBlack", "stemUpSE")
    STEM_LENGTH = 3.5
    HEAD_W = b.width("noteheadBlack")
    FLAG_ATTACH = {
        "flag8thUp": b.anchor_y("flag8thUp", "stemUpNW"),
        "flag16thUp": b.anchor_y("flag16thUp", "stemUpNW"),
        "flag8thDown": b.anchor_y("flag8thDown", "stemDownSW"),
        "flag16thDown": b.anchor_y("flag16thDown", "stemDownSW"),
    }

    def path(name, x, y):
        return ('<g transform="translate(%.3f,%.3f) scale(%.3f)"><path d="%s" fill="%s"/></g>'
                % (x, y, S, b.paths[name], INK))

    def rect(x, y, w, h):
        return ('<rect x="%.3f" y="%.3f" width="%.3f" height="%.3f" fill="%s"/>'
                % (x, y, w * S, h * S, INK))

    def text(x, y, s, size=13, weight="400", colour=LABEL, anchor="start"):
        return ('<text x="%.1f" y="%.1f" text-anchor="%s" font-family="Segoe UI, Helvetica, '
                'sans-serif" font-size="%d" font-weight="%s" fill="%s">%s</text>'
                % (x, y, anchor, size, weight, colour, s))

    class Panel:
        def __init__(self, top_px, top_step, bottom_step, width):
            self.top_px, self.top_step, self.bottom_step = top_px, top_step, bottom_step
            self.width = width
            self.items = []

        @property
        def height_px(self):
            return (self.top_step - self.bottom_step) * 0.5 * S

        def px(self, x):
            return MARGIN + x * S

        def py(self, step):
            return self.top_px + (self.top_step - step) * 0.5 * S

        def staff(self):
            for step in range(0, 9, 2):
                self.items.append(rect(self.px(0.0), self.py(step) - STAFF_LINE * S / 2,
                                       self.width, STAFF_LINE))

        def glyph(self, name, x, step):
            self.items.append(path(name, self.px(x), self.py(step)))

        def ledgers(self, x, step):
            reach = HEAD_W / 2 + b.defaults["legerLineExtension"]
            thick = b.defaults["legerLineThickness"]
            steps = list(range(10, step + 1, 2)) + list(range(-2, step - 1, -2))
            for s in steps:
                self.items.append(rect(self.px(x - reach), self.py(s) - thick * S / 2,
                                       reach * 2, thick))

        def note(self, x, step, value, dots=0, accidental=None):
            stem_up = step < 4
            head = {1: "noteheadWhole", 2: "noteheadHalf"}.get(value, "noteheadBlack")
            width = b.width(head)
            self.ledgers(x, step)
            if accidental:
                self.glyph(accidental, x - width / 2 - 0.28 - b.width(accidental), step)
            self.glyph(head, x - width / 2, step)

            head_x, head_y = self.px(x - width / 2), self.py(step)
            if value > 1:
                if stem_up:
                    self.items.append(rect(head_x + (width - STEM_W) * S,
                                           head_y - STEM_LENGTH * S, STEM_W,
                                           STEM_LENGTH - STEM_ATTACH))
                else:
                    self.items.append(rect(head_x, head_y + STEM_ATTACH * S, STEM_W,
                                           STEM_LENGTH - STEM_ATTACH))
            if value in (8, 16):
                flag = ("flag8th" if value == 8 else "flag16th") + ("Up" if stem_up else "Down")
                if stem_up:
                    fx, fy = head_x + (width - STEM_W) * S, head_y - STEM_LENGTH * S
                else:
                    fx, fy = head_x, head_y + STEM_LENGTH * S
                self.items.append(path(flag, fx, fy - FLAG_ATTACH[flag] * S))
            for i in range(dots):
                dot_step = step + 1 if step % 2 == 0 else step
                self.glyph("augmentationDot", x + width / 2 + 0.32 + i * 0.5, dot_step)

        def label(self, x, s, dy=26):
            self.items.append(text(self.px(x), self.top_px + self.height_px + dy, s,
                                   13, "400", LABEL, "middle"))

        def render(self):
            return NL.join("  " + i for i in self.items)

    panels, heads = [], []
    state = {"y": MARGIN + 34}

    def section(title, subtitle, top_step, bottom_step, width=27):
        heads.append(text(MARGIN, state["y"] - 14, title, 15, "600", "#222"))
        heads.append(text(MARGIN + 8 + len(title) * 8.2, state["y"] - 14, subtitle,
                          12, "400", "#888"))
        p = Panel(state["y"], top_step, bottom_step, width)
        p.staff()
        panels.append(p)
        state["y"] += p.height_px + 56
        return p

    values = [(1, "whole"), (2, "half"), (4, "quarter"), (8, "eighth"), (16, "sixteenth")]

    p = section("Clef and bar lines", "the header block is 4 staff spaces wide", 12, -5)
    p.glyph("gClef", 0.35, 2)
    p.label(1.7, "gClef")
    p.glyph("barlineSingle", 4.0, 0)
    p.label(4.0, "closes the header", dy=44)
    p.glyph("barlineSingle", 12.0, 0)
    p.label(12.0, "barlineSingle")
    p.glyph("barlineDouble", 17.5, 0)
    p.label(17.9, "barlineDouble - thin, thin")
    p.glyph("barlineFinal", 23.0, 0)
    p.label(23.5, "barlineFinal - thin, thick")

    p = section("Note values, stem up", "head on G4, flags on Bravura's anchors", 11, -2)
    p.glyph("gClef", 0.35, 2)
    for i, (value, name) in enumerate(values):
        p.note(5.5 + i * 4.0, 2, value)
        p.label(5.5 + i * 4.0, name)

    p = section("Note values, stem down", "head on D5", 9, -3)
    p.glyph("gClef", 0.35, 2)
    for i, (value, name) in enumerate(values):
        p.note(5.5 + i * 4.0, 6, value)
        p.label(5.5 + i * 4.0, name)

    p = section("Accidentals", "isolated, then hung to the left of a head", 11, -2)
    p.glyph("gClef", 0.35, 2)
    for i, (name, label) in enumerate([("accidentalSharp", "sharp"), ("accidentalFlat", "flat"),
                                       ("accidentalNatural", "natural")]):
        p.glyph(name, 5.5 + i * 2.6, 4)
        p.label(5.5 + i * 2.6 + 0.4, label)
    p.note(15.5, 5, 4, accidental="accidentalSharp")
    p.label(15.5, "sharp + quarter")
    p.note(19.5, 3, 4, accidental="accidentalFlat")
    p.label(19.5, "flat + quarter")
    p.note(23.5, 4, 2, accidental="accidentalNatural")
    p.label(23.5, "natural + half")

    p = section("Augmentation dot", "on a line note the dot lifts into the space above", 11, -2)
    p.glyph("gClef", 0.35, 2)
    p.note(5.5, 5, 2, dots=1)
    p.label(6.0, "dotted half, in a space")
    p.note(11.0, 4, 2, dots=1)
    p.label(11.5, "dotted half, on a line")
    p.note(16.5, 3, 4, dots=1)
    p.label(17.0, "dotted quarter")
    p.note(21.5, 2, 8, dots=1)
    p.label(22.0, "dotted eighth")

    p = section("Ledger lines", "the written range runs past the staff both ways", 17, -8)
    p.glyph("gClef", 0.35, 2)
    for i, (step, label) in enumerate(
            [(-2, "C4"), (-4, "A3"), (-6, "F3"), (10, "A5"), (12, "C6"), (14, "E6")]):
        p.note(5.5 + i * 3.4, step, 4)
        p.label(5.5 + i * 3.4, label)

    p = section("Together", "the same glyphs as one line of music", 12, -4)
    p.glyph("gClef", 0.35, 2)
    p.glyph("barlineSingle", 4.0, 0)
    p.note(5.6, 2, 4)
    p.note(8.4, 4, 8)
    p.note(10.8, 5, 8)
    p.note(13.6, 3, 2, accidental="accidentalSharp")
    p.glyph("barlineSingle", 17.0, 0)
    p.note(18.4, 6, 4, dots=1)
    p.note(21.6, 7, 8, accidental="accidentalFlat")
    p.note(24.0, 4, 4, accidental="accidentalNatural")
    p.glyph("barlineFinal", 25.6, 0)

    width = MARGIN * 2 + 28.5 * S
    height = state["y"] + MARGIN
    doc = ['<svg xmlns="http://www.w3.org/2000/svg" width="%.0f" height="%.0f" '
           'viewBox="0 0 %.0f %.0f">' % (width, height, width, height),
           '  <rect width="100%" height="100%" fill="#fff"/>',
           "  " + text(MARGIN, MARGIN + 6, "Bravura %s at %d px per staff space"
                       % (b.version, int(S)), 17, "600", "#222"),
           "  " + text(MARGIN, MARGIN + 24, "the glyphs BravuraGlyphs.kt holds, assembled with "
                                            "the font's own anchors and engraving defaults",
                       12, "400", "#888")]
    doc += [p.render() for p in panels]
    doc += ["  " + h for h in heads]
    doc.append("</svg>")
    SHEET.write_text(NL.join(doc), encoding="utf-8")
    print("wrote", SHEET, SHEET.stat().st_size, "bytes")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--download", action="store_true", help="fetch Bravura into tools/.bravura")
    ap.add_argument("--kotlin", action="store_true", help="rewrite BravuraGlyphs.kt")
    ap.add_argument("--svg", action="store_true", help="rewrite tools/notation-glyphs.svg")
    args = ap.parse_args()

    if args.download:
        download()
    if not (args.kotlin or args.svg):
        if not args.download:
            ap.error("nothing to do: pass --kotlin, --svg or --download")
        return

    b = Bravura()
    if args.kotlin:
        kotlin(b)
    if args.svg:
        svg(b)


if __name__ == "__main__":
    main()
