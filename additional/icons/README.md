# App icons

The SVG sources for the app's icons. Each one is converted into Kotlin by
`tools/svg_to_vector.py`, which writes one file per icon under
`core/designsystem/src/commonMain/kotlin/com/example/saxtrainer/core/designsystem/icon/`. Screens
only ever use `SaxTrainerIcons.<Name>`, so replacing an icon means replacing its SVG and running
the converter again. No screen changes.

There are two ways to get an icon: download it from Material Symbols (1), or draw it yourself in
Inkscape (2). Either way it goes into the app the same way (3). Section 4 lists which icons still
need a proper source.

---

## 1. Downloading from Material Symbols

This is the icon library at <https://fonts.google.com/icons>, the same set Angular Material's
`mat-icon` uses. Pick **Material Symbols** (not the older "Material Icons"), open an icon, and set
the panel on the right to:

| Setting       | Value                                                                    |
|---------------|--------------------------------------------------------------------------|
| Style         | **Rounded**                                                              |
| Fill          | **On** (filled). Only exception: an "off" state such as an empty star    |
| Weight        | **400**                                                                  |
| Grade         | **0**                                                                    |
| Optical size  | **24**                                                                   |
| Size          | **24 px**                                                                |
| Colour        | Anything. The app ignores it and tints icons with the theme colour       |
| Download as   | **SVG** (not PNG, not "Android")                                         |

These match every Material icon already in this folder: `settings`, `school`, `graphic_eq`,
`tune` and `arrow_back` are all Rounded and filled, at weight 400.

What a good download looks like when opened in a text editor:

```xml
<svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px" fill="#1f1f1f"><path d="..."/></svg>
```

The odd `viewBox="0 -960 960 960"` is normal for Material Symbols; the converter handles it.

---

## 2. Drawing your own in Inkscape

The aim is an icon that sits next to the Material ones without looking out of place: the same
grid, the same line weight, the same rounded corners.

### Document setup

`File > Document Properties`:

| Setting          | Value                                        |
|------------------|----------------------------------------------|
| Display units    | px                                           |
| Width × Height   | **24 × 24 px**                               |
| Scale            | 1 (so the viewBox is `0 0 24 24`)            |
| Grid             | rectangular, spacing **1 px**                |

### Design rules (in 24 px units)

These are read off the Material Symbols in this folder, so a custom icon matches them:

| Rule            | Value                                                                        |
|-----------------|------------------------------------------------------------------------------|
| Live area       | Keep everything between **2 and 22 px**, leaving a 2 px margin all round     |
| Line weight     | **2 px** (weight 400 is 80 of 960 units; the Long Tones bars are 2 px wide)  |
| Caps and joins  | **Round**                                                                    |
| Corner radius   | about **1 px** on outer corners (Rounded style); 0.5 px on small inner ones  |
| Style           | **Filled**: solid shapes, with details cut out as holes rather than drawn as thin outlines |
| Gaps            | at least **1 px** between separate shapes, or they merge at 24 px            |
| Colour          | plain black, no transparency, no gradients. The app tints it anyway          |

Draw with strokes if that's easier (2 px, round caps). The steps below turn them into shapes.

### Before saving

Do these every time, in this order. `Edit > Select All in All Layers` (Ctrl+Alt+A) first:

1. **`Path > Object to Path`** (Shift+Ctrl+C) turns rectangles, circles, ellipses and text into paths.
2. **`Path > Stroke to Path`** (Ctrl+Alt+C) turns every line into a filled outline. **Don't skip
   this one.** Inkscape stores the stroke inside `style="..."`, which the converter does not read,
   so a leftover stroke would come out as a wrongly filled shape without any error.
3. **`Path > Union`** (Ctrl++) merges everything into one clean shape. It also removes overlaps,
   so it doesn't matter whether Inkscape's fill rule is even-odd or non-zero.
4. **`File > Save As… > Plain SVG`**, into this folder, named after what the icon is *for* in
   `snake_case` (for example `metronome.svg`, `note_quiz.svg`).

Quick check in a text editor: one `<svg … viewBox="0 0 24 24">` containing only `<path d="…">`
elements. There should be no `<rect>`, `<circle>`, `<text>`, `<g transform=…>`, `<linearGradient>`
or `<mask>`.

If the converter complains about a `transform`, the drawing has a group or layer that was moved or
resized. Select everything, `Object > Ungroup` (Shift+Ctrl+G) until nothing is grouped, run
`Path > Union` again and save. Setting `Edit > Preferences > Behavior > Transforms > Store
transformation` to **Optimized** avoids it happening in the first place.

---

## 3. Putting an icon into the app

1. Save the SVG in this folder (`additional/icons/<name>.svg`).
2. Add or change its line in `tools/icons.map`:

   ```
   additional/icons/<name>.svg -> core/designsystem/src/commonMain/kotlin/com/example/saxtrainer/core/designsystem/icon/<Name>.kt
   ```

   * **Replacing** an icon: keep its existing `<Name>` (e.g. `Metronome`), and every screen using
     `SaxTrainerIcons.Metronome` gets the new drawing. Remove it from the "still hand-drawn" list
     at the bottom of the map.
   * **A new** icon: choose a new `<Name>`, then use `SaxTrainerIcons.<Name>` where it is needed.

3. From the repository root, run:

   ```
   python tools/svg_to_vector.py --map tools/icons.map
   ```

   This regenerates every icon in the map; the ones that didn't change come out identical. To
   convert just one:
   `python tools/svg_to_vector.py additional/icons/<name>.svg core/designsystem/src/commonMain/kotlin/com/example/saxtrainer/core/designsystem/icon/<Name>.kt`

4. `git diff` should show only the icon you changed. Never edit the generated `.kt` files by hand;
   change the SVG and run the converter again.

The converter refuses anything it would convert wrongly and says what it found. The usual fixes:

| Message mentions                     | Fix                                                        |
|--------------------------------------|------------------------------------------------------------|
| `<rect>`, `<circle>`, `<text>` …     | `Path > Object to Path`                                    |
| `transform`                          | Ungroup, `Path > Union` (see above)                        |
| `stroked path`                       | `Path > Stroke to Path`                                    |
| `even-odd fill`                      | `Path > Union`, or set the fill rule to non-zero            |
| `viewBox is not square`              | Document Properties: 24 × 24 px                            |
| gradient, mask, clip, filter, style  | Remove it; icons are a single flat shape                   |

---

## 4. Which icons still need a source

✅ = converted from an SVG in this folder. ✏️ = path data typed in by hand, waiting for a real
source. Replace them in any order; each one is independent.

| Icon (`SaxTrainerIcons.`) | Where it appears                                              | Now | Suggested source                          |
|---------------------------|---------------------------------------------------------------|-----|-------------------------------------------|
| `ArrowBack`               | Back button on every screen                                   | ✅  | Material `arrow_back`                     |
| `Settings`                | Settings card                                                 | ✅  | Material `settings`                       |
| `Learning`                | Lessons and My Lessons cards; C major and G major units       | ✅  | Material `school`                         |
| `LongTone`                | Long Tones card                                               | ✅  | Material `graphic_eq`                     |
| `ChooseNotes`             | Not used any more (belonged to the Fingering Trainer)         | ✅  | Material `tune`; can be deleted           |
| `Favorite`                | Star on a starred tool (Practice, Home)                       | ✅  | Material `star`, Fill on (`star_filled.svg`) |
| `FavoriteOff`             | Star on a tool that is not starred                            | ✅  | Material `star`, Fill **off** (`star_no_fill.svg`) |
| `MoveUp`                  | My Lessons, managing: move a group or practice up             | ✅  | Material `arrow_drop_up`                  |
| `MoveDown`                | My Lessons, managing: move a group or practice down           | ✅  | Material `arrow_drop_down`                |
| `Delete`                  | My Lessons, managing: delete a group or practice              | ✅  | Material `delete`                         |
| `Rename`                  | My Lessons, managing: rename a group                          | ✅  | Material `keyboard`                       |
| `Edit`                    | My Lessons: edit a saved practice's setup                     | ✅  | Material `edit` (the pen)                 |
| `Metronome`               | Metronome card                                                | ✏️  | Search Material for "metronome", else Inkscape |
| `NoteQuiz`                | Note Quiz card; "Your first five notes" unit                  | ✏️  | Inkscape                                  |
| `NoteTrainer`             | Note Trainer card; Practice card; "The whole octave" unit     | ✏️  | Inkscape                                  |
| `Sequence`                | Sequence Trainer card; "The octave key" unit                  | ✏️  | Inkscape                                  |
| `Fingering`               | "B flat and F sharp" unit                                     | ✏️  | Inkscape                                  |

Keep this table up to date when you replace an icon.
