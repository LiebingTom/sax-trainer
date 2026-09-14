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
| `transform`                          | Only a rotated or skewed one; a move or resize is baked in. Ungroup, `Path > Union` |
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
| `LongTone`                | Long Tones card                                               | ✅  | `practice_long_tones.svg` (Inkscape)      |
| `ChooseNotes`             | Not used any more (belonged to the Fingering Trainer)         | ✅  | Material `tune`; can be deleted           |
| `Favorite`                | Star on a starred tool (Practice, Home)                       | ✅  | Material `star`, Fill on (`star_filled.svg`) |
| `FavoriteOff`             | Star on a tool that is not starred                            | ✅  | Material `star`, Fill **off** (`star_no_fill.svg`) |
| `MoveUp`                  | My Lessons, managing: move a group or practice up             | ✅  | Material `arrow_drop_up`                  |
| `MoveDown`                | My Lessons, managing: move a group or practice down           | ✅  | Material `arrow_drop_down`                |
| `Delete`                  | My Lessons, managing: delete a group or practice              | ✅  | Material `delete`                         |
| `Rename`                  | My Lessons, managing: rename a group or practice              | ✅  | Material `keyboard`                       |
| `Edit`                    | My Lessons: edit a saved practice's setup                     | ✅  | Material `edit` (the pen)                 |
| `Metronome`               | Metronome card and title bar                                  | ✅  | `custom_metronome.svg` (Inkscape)         |
| `NoteQuiz`                | Note Quiz card                                                | ✅  | `practice_note_quiz.svg` (Inkscape)       |
| `NoteTrainer`             | Note Trainer card                                             | ✅  | `practice_note_tranier.svg` (Inkscape)    |
| `Sequence`                | Sequence Trainer card                                         | ✅  | `practice_sequences.svg` (Inkscape)       |
| `Practice`                | Practice card on Home                                         | ✅  | `custom_practices.svg` (Inkscape)         |
| `MyLessons`               | My Lessons card on Home                                       | ✅  | `book_ribbon_fill.svg` (Material `book_ribbon`) |
| `AddToLessons`            | A practice's title bar: keep its setup in My Lessons          | ✅  | `custom_my_lesson_add.svg` (Inkscape)     |
| `Fingering`               | "B flat and F sharp" unit                                     | ✏️  | Inkscape                                  |

Keep this table up to date when you replace an icon.

---

## 5. Icon plan: one icon, one meaning

### The rules

1. **Every icon means exactly one thing.** A practice's icon belongs to that practice. It appears
   only where the practice itself is shown: its card in Practice, and its card on Home once it is
   starred. That card *is* the practice, so this is not a reuse.
2. **Four families that never borrow from each other:**

   | Family         | Says                  | Members                                              |
   |----------------|-----------------------|------------------------------------------------------|
   | Practices      | what you *do*         | Note Quiz, Note Trainer, Long Tones, Sequence Trainer, Metronome |
   | Places         | where you *go*        | the Home cards: Practice, Lessons, My Lessons, Settings |
   | Lesson units   | what you *learn*      | one per unit in Lessons                              |
   | Controls       | what a button *does*  | back, star, move, rename, delete, edit (done)        |

3. **Practices show an action**, usually a note plus the thing you do with it. **Units show a musical
   subject** (a range, an accidental, a key), never an action, so a unit can never be mistaken for
   the practice it happens to use.
4. **One style throughout:** Material Symbols Rounded, filled, weight 400 (section 1), or drawn to
   match (section 2). Anything music-specific (notes, sharps, flats, a key signature) is not in
   Material, so draw it in Inkscape.
5. **A new unit gets its own mark.** Until it has one it uses a single neutral fallback that nothing
   else uses.

### What clashes today

| Icon          | Used for                                                                  |
|---------------|---------------------------------------------------------------------------|
| `NoteQuiz`    | Note Quiz **and** the unit "Your first five notes"                        |
| `NoteTrainer` | Note Trainer **and** the Practice card on Home **and** the unit "The whole octave" |
| `Sequence`    | Sequence Trainer **and** the unit "The octave key"                        |
| `Learning`    | Lessons **and** My Lessons **and** C major, G major, and any new unit      |
| `Fingering`   | only the unit "B flat and F sharp", left over from the removed Fingering Trainer |

### Icons to create

"✓" means the name exists in Google's Material icon list (checked 2026-09-11) and can be downloaded
as described in section 1. Without it, draw the icon, or search the Material Symbols site first.

**Practices** (replace the current drawings, same Kotlin names, so nothing else changes)

| Kotlin name   | For              | Idea                                                          | Source                                         |
|---------------|------------------|---------------------------------------------------------------|------------------------------------------------|
| `NoteQuiz`    | Note Quiz        | a note head with a question mark: "which note is this?"       | Inkscape; fallback `quiz` ✓                    |
| `NoteTrainer` | Note Trainer     | a note with a small microphone: read it, play it              | Inkscape; fallback `mic` ✓                     |
| `LongTone`    | Long Tones       | one whole note followed by a long straight line: a held tone  | Inkscape; the current `graphic_eq` ✓ can stay  |
| `Sequence`    | Sequence Trainer | three notes climbing like steps: a line in time               | Inkscape; fallback `stairs` ✓ or `queue_music` ✓ |
| `Metronome`   | Metronome        | the classic pyramid with its pendulum                         | Inkscape (`metronome` is not in the classic list); fallback `av_timer` ✓ |

**Places on Home** (two are new, so each Home card has its own)

| Kotlin name   | For              | Idea                                     | Source                                        |
|---------------|------------------|------------------------------------------|-----------------------------------------------|
| `Practice`    | Practice card    | a set of tools: a grid of four squares   | `apps` ✓ or `grid_view` ✓ (new)               |
| `Learning`    | Lessons card     | the school cap (keep, Lessons only)      | `school` ✓ (already there)                    |
| `MyLessons`   | My Lessons card  | lessons you kept: a stack of bookmarks   | `collections_bookmark` ✓ or `bookmarks` ✓ (new) |
| `Settings`    | Settings card    | the gear (keep)                          | `settings` ✓ (already there)                  |

**Lesson units** (all new, one per unit)

| Kotlin name       | Unit                   | Idea                                                  | Source                              |
|-------------------|------------------------|-------------------------------------------------------|-------------------------------------|
| `UnitFirstNotes`  | Your first five notes  | five note heads rising, C to G                        | Inkscape; fallback `looks_5` ✓      |
| `UnitOctave`      | The whole octave       | two notes an octave apart joined by a bracket         | Inkscape                            |
| `UnitAccidentals` | B flat and F sharp     | a flat and a sharp side by side                       | Inkscape (replaces `Fingering`)     |
| `UnitOctaveKey`   | The octave key         | the thumb's octave key, or an "8va" mark              | Inkscape                            |
| `UnitCMajor`      | C major                | a letter C on a short staff, no key signature         | Inkscape                            |
| `UnitGMajor`      | G major                | a short staff with one sharp: its key signature       | Inkscape                            |
| `UnitDefault`     | any future unit        | a neutral music mark, used by nothing else            | `library_music` ✓ or `auto_stories` ✓ |

Sharps and flats can be traced from the Bravura outlines the app already uses for notation (see
`tools/bravura_glyphs.py`), so the units match the notation on the staff.

**Can go afterwards:** `Fingering` (replaced by `UnitAccidentals`) and `ChooseNotes` (unused).

### Status (2026-09-11)

* Home: `Practice` is `apps`, `MyLessons` is `book_ribbon`, Lessons keeps `Learning` (the school
  cap). Done.
* Lesson units: all of them use `Assignment` (`assignment`) for now. The per-unit marks above wait
  until the lessons are final.
* Lessons in Lessons and in My Lessons show the icon of the practice they run, read from the
  practice's own card, so they change automatically when a practice icon is redrawn.
* The five practice icons are next, drawn in Inkscape.

### Putting them in

Replacing a **practice** icon is only section 3: same Kotlin name, the screens do not change. The new
names (`Practice`, `MyLessons` and the `Unit…` icons) also need a one-line change each in
`shell/.../home/HomeEntry.kt`, where the Home cards and the unit icons are chosen. Ask for that when
the icons exist, in any order and a few at a time.

---

## 6. The launcher icon

The app's launcher icon is drawn from `app_icons.svg`. Unlike the icons above it keeps its colours,
because a launcher icon is not tinted. It goes into the app with its own converter,
`tools/svg_to_launcher_icon.py`.

### Workflow

1. Change `additional/icons/app_icons.svg` in Inkscape. The drawing rules are those of section 2
   (24 × 24 px, paths only, no strokes), except that fill colours are kept. Use plain hex colours;
   a shape without a fill is black.
2. From the repository root, run:

   ```
   python tools/svg_to_launcher_icon.py
   ```

   This writes `app/src/main/res/drawable/ic_launcher_foreground.xml` (the drawing in colour),
   `ic_launcher_monochrome.xml` (the same shapes in one colour, for Android's themed icons) and the
   background colour in `app/src/main/res/values/colors.xml`. Never edit any of them by hand.
3. Install and look at it on the phone:

   ```
   gradlew.bat installDebug
   ```

   If the home screen still shows the old icon, the launcher is caching it: remove the icon from the
   home screen and add it again from the app drawer.

### The background colour

The ground behind the drawing is the SVG's page colour: in Inkscape, `File > Document Properties >
Background`. Its opacity (the checkerboard) does not matter; the launcher background is always
opaque. Save as **Inkscape SVG**, not Plain SVG: Plain SVG drops the page colour, and the converter
then stops with an error. The converter copies the colour into `ic_launcher_background` in
`app/src/main/res/values/colors.xml`.

Don't draw the background as a shape in the SVG. A full-size rectangle would end up in the
foreground, scaled to 60 dp instead of filling the icon, and would turn the themed icon into a
solid square.

### Size and safe area

The SVG's 24 px are drawn as 60 dp in the middle of the 108 dp launcher canvas. Launchers cut the
canvas to their own shape (circle, squircle and so on). Only a circle of 66 dp in the middle is
certain to stay visible, so keep the important parts away from the SVG's corners.

The themed (monochrome) icon is a silhouette: every shape is merged into one colour. Details that
exist only as a colour change, such as a yellow stripe on black, disappear there. Details that should
survive have to be gaps between shapes.
