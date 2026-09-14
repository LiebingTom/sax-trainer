# App colours

Every colour the app draws comes from one **seed** (the theme's main colour) and one **mode**
(light or dark). Change either and the whole app follows: accent, surfaces, cards, text, outlines,
meters, judgement colours, Android's system bars and window, the web page behind the canvas.

Sections: how it works (1), what each colour means (2), themes and the settings behind them (3),
rules (4), what is outside Compose (5), checks worth adding (6).

---

## 1. How it works

```
ThemeSeeds.Brass / NightBlue / Plum          theme/Color.kt — the only colour literals in the app
        │
        ▼
KeyPalettes(seed)                             theme/ColorSchemes.kt
  primary          seed hue, seed chroma (at least 0.10)
  secondary        seed hue, chroma 0.035        a quiet version of the accent
  tertiary         seed hue + 60°, chroma 0.07   set for Material components; the app gives it no meaning
  neutral          seed hue, chroma 0.008        surfaces, main text
  neutral variant  seed hue, chroma 0.016        secondary text, outlines
  error            fixed hue 25° (red)           app problems, wrong answers
  correct          fixed hue 145° (green)        right answers
        │   each palette = one hue at every tone 0 (black) … 100 (white)   theme/TonalPalette.kt
        ▼
lightColorScheme(...) / darkColorScheme(...)  every Material role set, none left to Material's purple default
SaxTrainerColors(correct, incorrect)          the app's own two, same mode
        │
        ▼
SaxTrainerTheme(darkTheme, seed)              theme/Theme.kt
        │
        ├─ MaterialTheme.colorScheme.<role>   what screens use
        ├─ FeedbackColors.correct / incorrect
        └─ ProgressColors.current / session / track
```

**Tone** is CIE L\*, the lightness contrast is measured by: roles ten tones apart are equally far
apart whatever the hue, so a theme that passes contrast in brass passes it in blue. **Hue and
chroma** are OKLCH, which keeps a hue looking like itself as it lightens. Where a tone can't carry
the chroma inside sRGB, chroma gives way; tone and hue stay.

The roles take the tones Material 3 assigns them (from `ColorLightTokens` / `ColorDarkTokens` in
material3 1.4.0, which `composeMultiplatformMaterial3 = "1.9.0"` resolves to on Android):

| Role                                    | Palette         | Light | Dark |
|-----------------------------------------|-----------------|-------|------|
| `primary` / `onPrimary`                 | primary         | 40 / 100 | 80 / 20 |
| `primaryContainer` / `onPrimaryContainer` | primary       | 90 / 10  | 30 / 90 |
| `secondary…`, `tertiary…`, `error…`     | their own       | same tones as primary      ||
| `background`, `surface`                 | neutral         | 98    | 6    |
| `surfaceContainerLowest … Highest`      | neutral         | 100, 96, 94, 92, 90 | 4, 10, 12, 17, 22 |
| `surfaceDim` / `surfaceBright`          | neutral         | 87 / 98 | 6 / 24 |
| `onBackground`, `onSurface`             | neutral         | 10    | 90   |
| `inverseSurface` / `inverseOnSurface`   | neutral         | 20 / 95 | 90 / 20 |
| `surfaceVariant` / `onSurfaceVariant`   | neutral variant | 90 / 30 | 30 / 80 |
| `outline` / `outlineVariant`            | neutral variant | 50 / 80 | 60 / 30 |
| `…Fixed`, `…FixedDim`, `on…Fixed`, `on…FixedVariant` | primary, secondary, tertiary | 90, 80, 10, 30 | same |
| `correct` (app)                         | correct         | 40    | 80   |
| `incorrect` (app)                       | error           | 40    | 80   |

The brass seed reproduces the palette the app had before themes were generated: its primary tone 40
is the old `Brass40`, its neutral tone 98 the old light surface, tone 10 the old text colour.

What each seed gives, for orientation (computed with the same algorithm in a script; the app's
values may differ by a step of rounding — `saxTrainerColorScheme(seed, darkTheme)` is the truth):

| Seed      | primary L / D       | container L / D     | surface L / D       | card (`sCHigh`) L / D | 2nd text L / D |
|-----------|---------------------|---------------------|---------------------|-----------------------|----------------|
| Brass     | `#7B5800` `#E8C17A` | `#FFDEA4` `#5D4200` | `#FCF9F3` `#15130F` | `#EBE8E2` `#2C2A26`   | `#4B463D` `#CCC6BB` |
| NightBlue | `#305EA0` `#A3C9FF` | `#D1E4FF` `#184685` | `#F6FAFF` `#111317` | `#E5E8EE` `#272A2E`   | `#41474F` `#C0C7D1` |
| Plum      | `#86497A` `#F6B1E6` | `#FFD7F5` `#6C3161` | `#FDF8FC` `#161215` | `#ECE7EB` `#2D292C`   | `#4D444B` `#CEC3CB` |
| (all)     | correct `#1C6D26` `#8AD88D` · incorrect `#B91B25` `#FFB3AD` |||||

---

## 2. What each colour means

One meaning, one colour. Screens pick by meaning:

| Meaning                               | Colour                                  |
|---------------------------------------|-----------------------------------------|
| accent: selected, current, section headers, spectrum bars | `primary`           |
| in tune (live, continuous)            | `primary`                               |
| icon tile / icon on it                | `primaryContainer` / `onPrimaryContainer` |
| metronome normal beat, count-in number | `secondary` (quiet accent)             |
| correct / done (a judged result)      | `FeedbackColors.correct`                |
| wrong answer, different note heard    | `FeedbackColors.incorrect`              |
| app problem: microphone, validation   | `error`, `errorContainer` / `onErrorContainer` |
| close but not in tune (CentsMeter marker) | `onSurfaceVariant`                  |
| close but not in tune (Long Tones pitch text) | `onSurface`                     |
| early / late (Sequence)               | `onSurface`; not played: `onSurfaceVariant` |
| main text / secondary text            | `onSurface` / `onSurfaceVariant`        |
| page                                  | `background`, `surface`                 |
| header and footer bands (ScreenScaffold) | `surfaceContainer`                   |
| cards                                 | `surfaceContainerHigh`                  |
| meter tracks, empty progress, spectrum background | `surfaceContainerHighest`   |
| timing bar / session bar              | `ProgressColors.current` (`primary`) / `.session` (`outline`) |
| idle marker, centre line, open keys   | `outline`                               |
| borders, ticks, grids                 | `outlineVariant`                        |

"Close", "early" and "late" are told apart from "in tune" and "correct" by lightness, not by a
second hue, so they never read as a judgement. `incorrect` and `error` are the same red today, from
the same palette; they keep separate names because they mean different things.

---

## 3. Themes and settings

**Mode** — `AppSettings.themeMode`: `SYSTEM`, `LIGHT`, `DARK`.
**Main colour** — `AppSettings.themeColor`: `BRASS` (default), `NIGHT_BLUE`, `PLUM`.

Both are stored like every other setting (`StoredSettings.KEY_THEME_MODE` / `KEY_THEME_COLOR`,
SharedPreferences on Android, `localStorage` on the web) and chosen in Settings → Appearance, where
each colour shows a `ThemeSwatch` drawn in the current mode.

```
AppSettings.themeColor (core:settings, a name only)
    └─ ThemeColor.seed   (feature:settings/ThemeColors.kt, name → ThemeSeeds.*)
AppSettings.themeMode
    └─ SaxTrainerApp     (shell) resolves SYSTEM with isSystemInDarkTheme()
          └─ SaxTrainerTheme(darkTheme, seed)
          └─ onAppearanceChanged(darkTheme, background) → platform (section 5)
```

`core:settings` knows colours only by name and `core:designsystem` only knows seeds; the mapping in
`feature:settings` is the one place they meet.

**Adding a theme colour**

1. `ThemeSeeds`: add the seed. Only its hue matters; lightness is ignored.
2. `ThemeColor`: add the entry with its display name.
3. `ThemeColor.seed`: map it. (The `when` is exhaustive, so the compiler points here.)

Avoid seed hues near green (~145°) or red (~25°): the accent would then look like a judgement.

**Changing what a whole theme looks like** is the constants at the bottom of `ColorSchemes.kt`
(chroma per palette, the two fixed hues). They apply to every seed.

---

## 4. Rules

- **No colour literals** outside `ThemeSeeds`. No `Color(0x…)`, no `Color.Red`, not in previews
  either. The one exception is the placeholder fill in `SaxTrainerIcons`, which is never drawn:
  `Icon(tint = …)` replaces it.
- **Dark or light is decided once**: `isSystemInDarkTheme()` appears only as the default of
  `SaxTrainerTheme(darkTheme = …)` and in `SaxTrainerApp` for `ThemeMode.SYSTEM`. Everything below
  reads the theme.
- **Pick by meaning** (section 2), not by what looks right in brass. Something that looks right only
  in one seed is wrong in the others.
- **`tertiary` carries no meaning in this app.** It is hue + 60°, which lands on green for brass.
- **New roles are free**: every Material role is already set for both modes.

---

## 5. Outside Compose

| Where | What | Follows the theme? |
|-------|------|--------------------|
| Android status bar icons, navigation scrim, window background | `MainActivity.applyAppearance`, called by `SaxTrainerApp` whenever mode or background changes | yes |
| Web page background and `color-scheme` | `Main.kt` `applyAppearance` | yes |
| Android window before the first frame | `app/src/main/res/values(-night)/colors.xml` `window_background` | no — default seed only (`#FCF9F3` / `#15130F`) |
| Web page before the Wasm module starts | `webApp/.../resources/index.html` | no — default seed only, same two values |
| Launcher icon | `app/src/main/res/drawable/ic_launcher_*.xml` | no — still the Android Studio template |

The two "before start" places run before any setting can be read. Update their hex values if the
default seed or the neutral chroma changes.

---

## 6. Checks worth adding (not written)

- Every role in `saxTrainerColorScheme(seed, dark)` differs from Material's baseline
  `lightColorScheme()` / `darkColorScheme()`, for every seed — catches a role added to Material but
  not to `ColorSchemes.kt`.
- `SaxTrainerTheme(darkTheme = false)` provides light `FeedbackColors` on a dark system.
- For every seed and mode: `onSurface` / `onSurfaceVariant` on `surface` and `surfaceContainerHigh`
  ≥ 4.5:1; `primary`, `correct`, `incorrect`, `outline` on `surface` ≥ 3:1.
- `TonalPalette`: tone *t* has CIE L\* within 0.5 of *t*; the seed's own tone round-trips to (nearly)
  the seed.
- The pre-start hex values in `colors.xml` and `index.html` equal the default seed's generated
  `background`.
- A source search for `Color(0x`, `Color\.(Black|White|Red|…)` outside `theme/Color.kt` finds nothing.
