# Sax Trainer

Android practice tools for saxophone, built around one ladder:

> **Note Quiz** (recognise the note) -> **Fingering Trainer** (learn the grip) ->
> **Note Trainer** (play it on sight) -> **Sequence Trainer** (play a line in time)

Each practice tool answers one question, and the separation between them is deliberate:

| Tool | Question | Microphone |
|---|---|---|
| Note Quiz | "Which grip is this note?" / "Which note is this grip?" | never |
| Fingering Trainer | "How is this note fingered?" | optional |
| Note Trainer | "Can you play this note on sight?" | always |
| Long Tones | "Can you hold it steady and in tune?" | always |
| Sequence Trainer | "Can you play this line in time?" | optional |
| Metronome | - | - |

## Modules

```
app                    MainActivity, AppContainer, navigation graph, landing page
core/designsystem      theme, typography, shapes, Dimens, reusable components (FeatureCard, FeatureIcon, ...)
core/music             pure Kotlin: Note, NoteName, Transposition, Cents, TrebleStaff placement,
                       Beats/Tempo/TimeSignature, ToneStep/ToneSequence
core/exercise          pure Kotlin: SequenceRunner (passive cursor), ExercisePattern, SequenceCatalog,
                       BeatClock, PauseSwitch, count-in, NoteOrder/practiceOrder
core/fingering         pure Kotlin: SaxKey, Fingering, FingeringChart, SaxophoneFingerings, NoteSet
core/learning          pure Kotlin: Curriculum, LearningUnit, Lesson, LessonStep, progress
core/dsp               pure Kotlin: WindowFunction, Fft, Spectrum, SpectrumAnalyzer, RmsCalculator, SignalGate, YinPitchDetector
core/audio             AudioRecord capture, AudioTrack playback, permission helper, error types
core/settings          AppSettings + SettingsRepository (SharedPreferences backed)
feature/metronome      timing engine, screen, ViewModel
feature/longtones      exercise state machine, DSP processing, screen, ViewModel
feature/notequiz       note <-> fingering multiple choice, no audio at all
feature/fingering      written note to fingering, optionally played and checked
feature/notetrainer    read a note, play it, be told what was heard
feature/sequence       scales and patterns in time, engraved on a staff
feature/learning       the lesson list of one unit
feature/settings       instrument, naming, note display, count-in
```

Dependencies point one way: `app -> feature -> core`. Features never depend on each other, and no
core module knows about a feature. `core/dsp` and `core/music` contain no Android code so they can be
unit tested on the JVM.

### Learning: units, lessons, steps

The curriculum is organised by **what is being learnt**, never by which trainer teaches it:

```
LearningUnit   a topic          "Your first five notes"
  Lesson       one sitting      "Play them"
    LessonStep one exercise     note quiz, then note trainer
```

A lesson's steps may run different trainers in turn, and the navigation layer walks them: a step
reports its result, the graph either starts the next step or writes the lesson's combined outcome.
**No trainer knows it was launched from a lesson** - each receives a preset and reports a result.

The catalog splits along that seam:

- `app/learning/catalog/steps/<Trainer>Steps.kt` - the launch type and the preset/outcome adapters,
  one file per trainer. The only place that sees both `core:learning` and a `feature:*`.
- `app/learning/catalog/units/<Topic>Unit.kt` - the lessons of one topic, calling step builders and
  never touching a preset type.

Adding a trainer never edits a unit; adding a unit never edits a trainer.

### Adding a training tool

1. Create `feature/<tool>` with its preset, state, ViewModel, screen and route.
2. Reuse `core/audio`, `core/dsp`, `core/exercise` and `core/designsystem` - do not open a second
   recorder, write a second FFT, or draw a second staff.
3. Add `TrainerId.<TOOL>`, which makes the compiler demand a branch in `routeForStep`.
4. Add `app/learning/catalog/steps/<Tool>Steps.kt`, and use it from whichever units want it.
5. Add a `HomeEntry` to the right group in `app/home/HomeEntry.kt`, plus a destination in
   `app/navigation/SaxTrainerDestination.kt` and a `lessonStep(...)` registration in
   `app/navigation/SaxTrainerNavHost.kt`.

The landing page renders whatever is in the entry lists, so no layout changes are needed. Icons are
supplied as an `IconSource` (Material vector or a drawable resource), so a custom icon never requires
touching a card implementation.

## Notable implementation details

- **Metronome timing** comes from the audio clock, not from coroutine delays: a dedicated render
  thread streams continuous PCM and places clicks at exact sample positions, accumulating beat
  boundaries as fractional sample counts so long sessions do not drift. Beats reach the UI when the
  playback head passes them.
- **Capture and analysis** run off the UI thread. The window buffer is reused, and Compose state is
  refreshed about 25 times per second rather than per audio frame.
- **Long tone timing is audio time**: `AudioFrame` carries sample counters, and durations are derived
  as samples / sample rate, so neither the displayed duration nor the point at which a note ends
  depends on scheduling or processing latency. A note ends when its beats have passed in audio time;
  the gated-on portion is tracked separately as `soundingSeconds`, ready to become validated held
  time if a note should later have to be played correctly to count.
- **Pitch detection is time-domain YIN**, not the strongest FFT bin, so the fundamental is found even
  when a harmonic is louder. `core:dsp` reports frequency and confidence only; cent arithmetic lives
  in `core:music` and the two are combined in the feature. Estimates below the signal gate or below
  the confidence threshold are dropped rather than displayed or averaged.
- **Exercises are relative patterns**: a `SequenceCatalog` entry is a list of semitone offsets from a
  root note, so transposing is a different root rather than a second definition, and "repeat" is
  simply ten identical steps rather than a special kind of sequence. `ToneSequence` carries no tempo;
  `Tempo` converts beats to seconds only where the exercise meets the clock.
- **Fingerings are keyed by written note**, which is why one chart serves the whole saxophone family.
  `core:fingering` says which keys are pressed, `SaxophoneKeyLayout` says where they are drawn, and
  `SaxophoneFingeringView` draws them - no image per note, and no visual data in the model. No two
  notes share a set of pressed keys, which is what makes a fingering-to-note question answerable;
  a test holds the chart to it.
- **Silence and an unclear sound are different things.** `NoteListener.listen()` reports
  `Hearing.Silent`, `Hearing.Unclear` or `Hearing.Pitched`, so an exercise that judges what it hears
  never reports a wobbling attack as a wrong note. `detectedNotes()` is the plain form, built on the
  same pipeline.
- **The note trainer is driven by the microphone, not by a clock** - the one trainer that is - which
  is what lets it measure a reaction at all. Nothing times out: a note waits until it is played or
  skipped, and how long it took is scored rather than failed.
- **Sample rate and FFT size are configuration**, never constants inside the DSP code.
- **Microphone permission** is requested inside the tool that needs it, at the moment it needs it -
  never on the landing page, and never before a count-in has finished, so a click cannot be heard as
  a played note.
- **Written pitch is what the player reads.** Long Tones stores concert pitch; everything built
  around reading and fingering - the quiz, the fingering trainer, the note trainer, the sequence
  trainer - stores the written note, because a written G is the same grip on every saxophone.
  `Transposition` is applied at exactly one point, the comparison with the microphone, and nowhere
  else. The instrument is chosen in Settings (tenor Bb by default).

## Build and test

```
gradlew.bat assembleDebug                     # build the app
gradlew.bat :core:dsp:test :core:music:test :core:exercise:test   # pure Kotlin unit tests
gradlew.bat testDebugUnitTest                 # all unit tests
gradlew.bat lintDebug                         # lint
```
