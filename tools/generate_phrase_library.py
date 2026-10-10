#!/usr/bin/env python3
"""Turns the phrase and pattern JSON files into the Kotlin objects the app ships with.

Phrases and scale patterns are content, not code - see `content/phrases/*.json` and
`content/patterns/*.json`, edited by hand or with `tools/phrase_editor.py`. This app has no writable,
always-available filesystem on every target (Android, wasmJs), so there is no such thing as reading
those files at run time; instead, the same way `tools/svg_to_vector.py` turns an SVG into an icon,
this turns each JSON file into a Kotlin string constant, embedded once, at build time on a developer's
machine. `PhraseLibrary.kt` and `PatternLibrary.kt` are generated - never hand-edit them; edit the
JSON and run this script again.

Usage:
    python tools/generate_phrase_library.py

Validation happens here, not only in the app: a phrase or a pattern the app's own JSON reader cannot
parse is silently left out of the catalog at run time (see `core/phrase/.../ContentJson.kt`, "every
reader is total") - the right behaviour for a build that must not crash over one bad file, but the
wrong one for an author who would rather be told. So this script refuses to generate anything from a
file it cannot fully validate - bad JSON, a missing id, a bar whose steps do not add up to a full bar
- and says exactly which file and why, rather than shipping a catalog quietly missing an entry.
"""
from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PHRASES_DIR = ROOT / "content" / "phrases"
PATTERNS_DIR = ROOT / "content" / "patterns"
SCALES_DIR = ROOT / "content" / "scales"

PACKAGE_DIR = ROOT / "core/phrase/src/commonMain/kotlin/com/example/saxtrainer/core/phrase"
PHRASE_LIBRARY_KT = PACKAGE_DIR / "PhraseLibrary.kt"
PATTERN_LIBRARY_KT = PACKAGE_DIR / "PatternLibrary.kt"
SCALE_LIBRARY_KT = PACKAGE_DIR / "ScaleLibrary.kt"

NOTE_VALUE_QUARTERS = {
    "whole": 4.0,
    "half": 2.0,
    "quarter": 1.0,
    "eighth": 0.5,
    "sixteenth": 0.25,
}

DURATION_PATTERN = re.compile(r"^(whole|half|quarter|eighth|sixteenth)(\.{0,2})$")
NOTE_PATTERN = re.compile(r"^[A-Ga-g][#b]?-?\d+$")
ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]*$")


class ContentError(Exception):
    """A content file this script refuses to embed, with the file and the reason."""


def beats_for(duration: str, beat_unit: int) -> float:
    # Values joined by "+" are tied into one note: "half+eighth" lasts two and a half beats.
    if "+" in duration:
        return sum(beats_for(part.strip(), beat_unit) for part in duration.split("+"))
    match = DURATION_PATTERN.match(duration)
    if not match:
        raise ValueError(f"'{duration}' is not a note value this app draws")
    value, dots = match.group(1), len(match.group(2))
    dot_factor = 2.0 - 0.5 ** dots
    return NOTE_VALUE_QUARTERS[value] * dot_factor * beat_unit / 4.0


def time_signature_of(token: str | None) -> tuple[int, int]:
    if token is None:
        return 4, 4
    parts = token.split("/")
    if len(parts) != 2 or not all(p.isdigit() for p in parts):
        raise ValueError(f"'{token}' is not a time signature (expected e.g. '4/4')")
    return int(parts[0]), int(parts[1])


def validate_step(step: dict, path: str, is_phrase: bool) -> None:
    if "duration" not in step:
        raise ContentError(f"{path}: a step needs a duration")
    beats_for(step["duration"], 4)  # raises if the token itself is unrecognised

    note, degree = step.get("note"), step.get("degree")
    if is_phrase:
        if note is not None and not NOTE_PATTERN.match(note):
            raise ContentError(f"{path}: '{note}' is not a note like 'C4' or 'F#3'")
    else:
        # 1 is the root; past the scale wraps up an octave, 0 and below wrap down one.
        if degree is not None and not isinstance(degree, int):
            raise ContentError(f"{path}: degree must be a whole number, got {degree!r}")


def validate_bars(bars: list, beats_per_bar: int, beat_unit: int, path: str, is_phrase: bool) -> None:
    if not bars:
        raise ContentError(f"{path}: needs at least one bar")
    for index, bar in enumerate(bars, start=1):
        steps = bar.get("steps") or []
        if not steps:
            raise ContentError(f"{path}: bar {index} has no steps")
        for step in steps:
            validate_step(step, f"{path} bar {index}", is_phrase)
        total = sum(beats_for(step["duration"], beat_unit) for step in steps)
        if abs(total - beats_per_bar) > 1e-9:
            raise ContentError(
                f"{path}: bar {index} lasts {total:g} beats, but its time signature needs "
                f"{beats_per_bar} - fix the steps' durations or the bar count",
            )


def validate_run(doc: dict, beats_per_bar: int, beat_unit: int, path: str) -> None:
    """A pattern that is the scale up and down: one note value, no bars of its own."""
    if doc.get("bars"):
        raise ContentError(f"{path}: a pattern is either bars or a run, not both")
    run = doc["run"]
    if not isinstance(run, dict) or "duration" not in run:
        raise ContentError(f"{path}: a run needs a duration")
    try:
        step = beats_for(run["duration"], beat_unit)
    except ValueError as error:
        raise ContentError(f"{path}: {error}") from error
    steps_per_bar = beats_per_bar / step
    if abs(steps_per_bar - round(steps_per_bar)) > 1e-9:
        raise ContentError(f"{path}: {run['duration']} notes do not fill a bar of {beats_per_bar} beats evenly")


def validate_common(doc: dict, path: str) -> tuple[int, int]:
    if not doc.get("id") or not ID_PATTERN.match(doc["id"]):
        raise ContentError(f"{path}: id must be lowercase letters, digits and underscores, starting with a letter")
    if not doc.get("name"):
        raise ContentError(f"{path}: needs a name")
    training_bars = doc.get("trainingBars", 1)
    if not isinstance(training_bars, int) or training_bars < 1:
        raise ContentError(f"{path}: trainingBars must be a whole number of at least 1")
    return time_signature_of(doc.get("timeSignature"))


def load_content(directory: pathlib.Path, is_phrase: bool) -> list[tuple[str, str]]:
    """Every JSON file in [directory], validated, as (constant name, raw JSON text) pairs.

    Sorted by id so regenerating never reorders the catalog for a reason as arbitrary as filesystem
    listing order.
    """
    if not directory.is_dir():
        return []

    entries: list[tuple[str, str, str]] = []  # (id, constant name, raw text)
    seen_ids: set[str] = set()
    for file in sorted(directory.glob("*.json")):
        text = file.read_text(encoding="utf-8")
        try:
            doc = json.loads(text)
        except json.JSONDecodeError as error:
            raise ContentError(f"{file}: not valid JSON ({error})") from error

        beats_per_bar, beat_unit = validate_common(doc, str(file))
        if not is_phrase and "run" in doc:
            validate_run(doc, beats_per_bar, beat_unit, str(file))
        else:
            validate_bars(doc.get("bars", []), beats_per_bar, beat_unit, str(file), is_phrase)

        content_id = doc["id"]
        if content_id in seen_ids:
            raise ContentError(f"{file}: id '{content_id}' is already used by another file in {directory}")
        seen_ids.add(content_id)

        entries.append((content_id, kotlin_constant_name(content_id), text.strip()))

    return [(name, text) for _id, name, text in entries]


def load_scales(directory: pathlib.Path) -> list[tuple[str, str]]:
    """Every scale file in [directory], validated, as (constant name, raw JSON text) pairs, by id."""
    if not directory.is_dir():
        return []

    entries: list[tuple[str, str, str]] = []
    seen_ids: set[str] = set()
    for file in sorted(directory.glob("*.json")):
        path = str(file)
        text = file.read_text(encoding="utf-8")
        try:
            doc = json.loads(text)
        except json.JSONDecodeError as error:
            raise ContentError(f"{path}: not valid JSON ({error})") from error

        if not doc.get("id") or not ID_PATTERN.match(doc["id"]):
            raise ContentError(f"{path}: id must be lowercase letters, digits and underscores, starting with a letter")
        if not doc.get("name"):
            raise ContentError(f"{path}: needs a name")
        intervals = doc.get("intervals")
        if not isinstance(intervals, list) or not intervals or not all(isinstance(i, int) for i in intervals):
            raise ContentError(f"{path}: intervals must be a list of whole numbers")
        if intervals[0] != 0:
            raise ContentError(f"{path}: intervals must start on the root, 0")
        if any(b <= a for a, b in zip(intervals, intervals[1:])) or intervals[-1] > 11:
            raise ContentError(f"{path}: intervals must rise, each within the octave (0 to 11)")
        letters = doc.get("letters")
        if letters is None:
            if len(intervals) != 7:
                raise ContentError(f"{path}: a scale of {len(intervals)} notes needs 'letters' to say how it is written")
        else:
            if not isinstance(letters, list) or len(letters) != len(intervals):
                raise ContentError(f"{path}: letters must name one letter step per interval")
            if not all(isinstance(step, int) and 0 <= step <= 6 for step in letters):
                raise ContentError(f"{path}: every letter step must be 0 to 6")

        if doc["id"] in seen_ids:
            raise ContentError(f"{path}: id '{doc['id']}' is already used by another file in {directory}")
        seen_ids.add(doc["id"])
        entries.append((doc["id"], kotlin_constant_name(doc["id"]), text.strip()))

    return [(name, text) for _id, name, text in entries]


def kotlin_constant_name(content_id: str) -> str:
    return content_id.upper() + "_JSON"


def render_kotlin(
    object_name: str,
    catalog_type: str,
    in_memory_type: str,
    parse_function: str,
    source_glob: str,
    entries: list[tuple[str, str]],
) -> str:
    constants = "\n\n".join(
        f'@Suppress("ktlint")\nprivate val {name} = """\n{text}\n""".trimIndent()'
        for name, text in entries
    )
    names = ", ".join(name for name, _text in entries)
    lines = ", ".join(f'"{name}"' for name, _text in entries) if entries else ""
    # Kotlin nests block comments, so a literal "/*" inside this KDoc (as "dir/*.json" would be) would
    # leave the doc comment open past its own "*/" - the source glob is only ever named where that is
    # safe: in the "//" line comment below, and in generated prose with the slash spelled out.
    source_dir = source_glob.rsplit("/", 1)[0]
    header = f"""package com.example.saxtrainer.core.phrase

// GENERATED by tools/generate_phrase_library.py from {source_glob} - do not hand-edit.
// Edit the JSON there (or use tools/phrase_editor.py) and run the script again.

/**
 * The predefined content, embedded from the JSON files under `{source_dir}` at generation time.
 *
 * See `tools/generate_phrase_library.py` for why this is generated rather than hand-written, and
 * [{catalog_type}] for the seam a real file-backed catalog would use instead.
 */
object {object_name} : {catalog_type} by {in_memory_type}({parse_function}(listOf({names})))
"""
    return header + ("\n" + constants + "\n" if entries else "")


def write_if_changed(path: pathlib.Path, content: str) -> bool:
    if path.exists() and path.read_text(encoding="utf-8") == content:
        return False
    path.write_text(content, encoding="utf-8")
    return True


def main() -> int:
    try:
        phrases = load_content(PHRASES_DIR, is_phrase=True)
        patterns = load_content(PATTERNS_DIR, is_phrase=False)
        scales = load_scales(SCALES_DIR)
    except ContentError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    phrase_kt = render_kotlin(
        object_name="PhraseLibrary",
        catalog_type="PhraseCatalog",
        in_memory_type="InMemoryPhraseCatalog",
        parse_function="parsePhrases",
        source_glob="content/phrases/*.json",
        entries=phrases,
    )
    pattern_kt = render_kotlin(
        object_name="PatternLibrary",
        catalog_type="PatternCatalog",
        in_memory_type="InMemoryPatternCatalog",
        parse_function="parsePatterns",
        source_glob="content/patterns/*.json",
        entries=patterns,
    )
    scale_kt = render_kotlin(
        object_name="ScaleLibrary",
        catalog_type="ScaleCatalog",
        in_memory_type="InMemoryScaleCatalog",
        parse_function="parseScales",
        source_glob="content/scales/*.json",
        entries=scales,
    )

    changed = []
    if write_if_changed(PHRASE_LIBRARY_KT, phrase_kt):
        changed.append(PHRASE_LIBRARY_KT)
    if write_if_changed(PATTERN_LIBRARY_KT, pattern_kt):
        changed.append(PATTERN_LIBRARY_KT)
    if write_if_changed(SCALE_LIBRARY_KT, scale_kt):
        changed.append(SCALE_LIBRARY_KT)

    if changed:
        for path in changed:
            print(f"wrote {path.relative_to(ROOT)}")
    else:
        print("up to date")
    print(f"{len(phrases)} phrase(s), {len(patterns)} pattern(s), {len(scales)} scale(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
