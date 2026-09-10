# `:feature:fingering` - deprecated

The Fingering Trainer is no longer part of the app. This directory is kept for reference and
possible reuse only.

## What that means

- **Not built.** The module is not included in `settings.gradle.kts`, so nothing here is compiled or
  tested, and nothing in the app depends on it.
- **Not reachable.** It has no Practice card, no navigation destination, no lesson steps and no
  saved-lesson support.
- **Not maintained.** Changes elsewhere are not carried over here, so it may stop compiling against
  the rest of the codebase.

## What is here

- `src/` - the trainer as it was: screen, ViewModel, setup, preset, exercise generator, session
  clock and its tests.
- `archive/shell/FingeringSteps.kt` - the shell's lesson-step adapter for this trainer
  (`fingeringStep`, `fingeringPresetOf`, `fingeringOutcomeOf`). It used to live in
  `shell/src/commonMain/.../learning/catalog/steps/`.

## What is not here, and stays in use

The shared fingering pieces are active and live elsewhere:

- `:core:fingering` - the fingering chart (`TenorSaxophoneFingerings`, `Fingering`, `SaxKey`,
  `NoteSets`), used by Note Quiz, Note Trainer and the design system.
- `SaxophoneFingeringView` and the key layout in `:core:designsystem`.

## Bringing it back

Re-including the module is not enough on its own. Removed along with it were:

- `include(":feature:fingering")` in `settings.gradle.kts`, and `api(project(":feature:fingering"))`
  in `shell/build.gradle.kts`
- `TrainerId.FINGERING` in `:core:learning` and `Exercise.FINGERING` in `:core:settings` (the trainer's
  own code uses `Exercise.FINGERING`)
- the `fingering` route and its lesson-step destination in the shell's navigation
- the "Fingering Trainer" Practice card, and saved lessons for this trainer in `UserLessons.kt`
- the curriculum lessons `fingering-1` to `fingering-4`, `octave-key-first` and `octave-key-notes`

`TrainingTool.FINGERING` in `:core:settings` was left in place.
