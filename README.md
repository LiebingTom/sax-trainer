# Sax Trainer

Practice tools for saxophone, running from **one shared codebase on Android and in the browser**.
Everything the app is - the screens, the ViewModels, the music model, the DSP, the exercise clocks,
the curriculum and the notation renderer - is Kotlin Multiplatform and Compose Multiplatform code
compiled twice. Only the microphone, the metronome transport, settings storage, permission and the
entry point differ per platform, and each of those is one interface with two implementations.

The tools are built around one ladder:

> **Note Quiz** (recognise the note) -> **Note Trainer** (play it on sight) ->
> **Sequence Trainer** (play a line in time)

Each practice tool answers one question, and the separation between them is deliberate:

| Tool | Question | Microphone |
|---|---|---|
| Note Quiz | "Which grip is this note?" / "Which note is this grip?" | never |
| Note Trainer | "Can you play this note on sight?" | always |
| Long Tones | "Can you hold it steady and in tune?" | always |
| Sequence Trainer | "Can you play this line in time?" | optional |
| Metronome | - | - |

All five, the guided lessons and the settings are available on both platforms.

## Platforms

| | Android | Web |
|---|---|---|
| Minimum | API 26 | a WasmGC browser: Chrome 119+, Firefox 120+, Safari 18.2+ |
| Entry point | `:app` - `MainActivity`, `Application`, manifest, launcher icons | `:webApp` - `main()`, `ComposeViewport`, `index.html` |
| Microphone | `AudioRecord` | `getUserMedia` -> `AudioWorklet` |
| Metronome | `AudioTrack` on a render thread | Web Audio lookahead scheduler |
| Settings | `SharedPreferences` | `localStorage` |

## Modules

```
app                    Android entry point only: MainActivity, Application, manifest, icons
webApp                 web entry point only: main(), ComposeViewport, index.html
shell                  the application itself: navigation graph, landing page with its
                       favorites, the Practice, Lessons and My Lessons lists, lesson catalog,
                       AppContainer. The one module allowed to depend on features.
core/designsystem      theme, typography, shapes, Dimens, reusable components, the staff and
                       fingering renderers - clef, heads, flags, accidentals and bar lines traced
                       from Bravura into BravuraGlyphs.kt, notes drawn or written by name -
                       the icons as ImageVector code
core/music             Note, NoteName, Transposition, Cents, TrebleStaff placement,
                       Beats/Tempo/TimeSignature, ToneStep/ToneSequence
core/exercise          SequenceRunner (passive cursor),
                       BeatClock, PauseSwitch, count-in, NoteOrder/practiceOrder
core/fingering         SaxKey, Fingering, FingeringChart, SaxophoneFingerings, NoteSet
core/learning          Curriculum, LearningUnit, Lesson, LessonStep, progress
core/dsp               WindowFunction, Fft, Spectrum, SpectrumAnalyzer, RmsCalculator,
                       SignalGate, YinPitchDetector
core/audio             AudioCapture, Metronome, MicrophoneAccess, NoteListener, ClickSynth,
                       plus the Android and Web implementation of each
core/settings          AppSettings + SettingsRepository, the setup each exercise was last used
                       with, and the practices the player saved - with the two stores behind them
core/testing           shared test fixtures: fake microphone, fake settings, silent metronome,
                       and the browser-test scaffolding
feature/metronome      timing engine, screen, ViewModel
feature/longtones      exercise state machine, DSP processing, screen, ViewModel
feature/notequiz       note <-> fingering multiple choice, no audio at all
feature/fingering      DEPRECATED, not built: the old Fingering Trainer, kept for reference
feature/notetrainer    read a note, play it, be told what was heard
feature/sequence       scales and patterns in time, engraved on a staff
feature/learning       the lesson list of one unit
feature/settings       instrument, naming, note display, count-in
probe                  developer page: the production web audio chain with its numbers on screen
```

Dependencies point one way: `app | webApp -> shell -> feature -> core`. Features never depend on
each other, and no core module knows about a feature.

### Shared and platform-specific

Every module is a KMP library with `commonMain` and, where it needs one, `androidMain` and
`wasmJsMain`. The source sets are `commonMain` / `androidMain` / `wasmJsMain` for code and
`commonTest` / `androidHostTest` / `wasmJsTest` for tests.

Nine core and feature modules are **`commonMain` only** - no platform code at all. Everything that
is platform-specific is one of these:

| Concern | Shared | Android | Web |
|---|---|---|---|
| Microphone capture | `interface AudioCapture` | `AudioRecordCapture` | `WebAudioCapture` + `capture-worklet.js` |
| Metronome transport | `interface Metronome`, `ClickSynth`, `clickCountIn` | `MetronomeEngine` | `WebMetronome` |
| Microphone permission | `interface MicrophoneAccess`, `expect rememberMicrophoneAccess()` | permission launcher | Permissions API / `getUserMedia` |
| Settings storage | `interface SettingsRepository` | `SharedPreferencesSettingsRepository` | `LocalStorageSettingsRepository` |
| Entry point | `SaxTrainerApp(container)` in `:shell` | `MainActivity` + `Application` | `main()` + `ComposeViewport` |

`MicrophoneAccess` answers exactly one question - *may this app record?* - and never hands anyone an
audio stream; `AudioCapture` owns the stream on both platforms, opens it when its `Flow` is
collected and closes it on cancellation. The web's `getUserMedia` is both the prompt and the stream
handout, and keeping the two apart is what gives both platforms the same rule: **permission is asked
for in the route, the stream is opened and closed by the capture.**

The only JavaScript in the project is `core/audio/src/wasmJsMain/worklet/capture-worklet.js`, which
Kotlin/Wasm cannot replace because it has to run inside an `AudioWorklet`. It does no analysis: it
gathers the browser's 128-sample render quanta into hop-sized batches and posts them to Kotlin. It
is compiled into a Kotlin constant at build time, so no consumer has to ship a copy of it.

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

- `shell/learning/catalog/steps/<Trainer>Steps.kt` - the launch type and the preset/outcome
  adapters, one file per trainer. The only place that sees both `core:learning` and a `feature:*`.
- `shell/learning/catalog/units/<Topic>Unit.kt` - the lessons of one topic, calling step builders
  and never touching a preset type.

Adding a trainer never edits a unit; adding a unit never edits a trainer.

**A practice the player saves is a unit like any other.** "Add to your lessons" writes down the
setup a trainer is currently configured with; `shell/learning/UserLessons.kt` turns each group of
those into a `LearningUnit` and each saved practice into a one-step `Lesson`, appended to the same
`Curriculum` the catalog builds. Starting one, walking it, scoring it and drawing its tile are
therefore the code the built-in lessons already use - there is one lesson system, and ownership is
the only difference: the catalog's units cannot be edited or deleted, the player's can.

Editing works the same way for both: it adjusts a lesson before playing it. The trainer opens its
own setup screen with the lesson's configuration loaded and is handed a store of edits held by the
navigation graph instead of the app's `ExerciseSetupRepository`, so changing a lesson your own way
changes neither the lesson nor what your trainer opens on next time. Keeping the edits is "Add to
your lessons", the same screen as for any practice, which reads them from that store and saves them
as a new lesson. While a trainer runs a lesson, its screens show where the lesson sits
(`My Lessons / group / name`) through `LocalScreenTrail`, so no trainer knows it is inside one.

### Adding a training tool

1. Create `feature/<tool>` as a KMP library with its preset, state, ViewModel, screen and route,
   all in `commonMain`.
2. Reuse `core/audio`, `core/dsp`, `core/exercise` and `core/designsystem` - do not open a second
   recorder, write a second FFT, or draw a second staff.
3. Add `TrainerId.<TOOL>`, which makes the compiler demand a branch in `routeForStep`.
4. Add `shell/learning/catalog/steps/<Tool>Steps.kt`, and use it from whichever units want it.
5. Add a `HomeEntry` to the right group in `shell/home/HomeEntry.kt`, naming its
   `trainerId` so it can be starred, plus a destination in
   `shell/navigation/SaxTrainerDestination.kt` and a `lessonStep(...)` registration in
   `shell/navigation/SaxTrainerNavHost.kt`.

Both platforms get it at once: there is no per-platform trainer registry.

## Build and run

### Android

```
gradlew.bat assembleDebug                     # build the APK
gradlew.bat installDebug                      # install it on a connected device
gradlew.bat lintDebug                         # lint
```

### Web

```
gradlew.bat :webApp:wasmJsBrowserDevelopmentRun   # serves on http://localhost:8080
gradlew.bat :webApp:wasmJsBrowserDistribution     # production bundle in webApp/build/dist
```

**The browser needs a secure context to give the page a microphone.** `getUserMedia` is refused over
plain HTTP unless the host is `localhost`, so the dev server is reachable as it stands and a LAN
address is not. The browser also needs a user gesture before an `AudioContext` may start - every
trainer starts from a Start button, which is where `resume()` happens.

Capture asks for `echoCancellation`, `noiseSuppression` and `autoGainControl` to be **off**: all
three mangle a sustained saxophone tone and would wreck both the pitch estimate and the cents meter.
The sample rate is the browser's to choose, so every `AudioFrame` carries the rate it was captured
at and the DSP configures itself from that rather than from the requested value.

### The web app on a physical Android phone

No certificates and no LAN address - `adb reverse` makes the phone's own `localhost` the tunnel,
and `localhost` **is** a secure context, so the microphone prompt appears normally:

```
gradlew.bat :webApp:wasmJsBrowserDevelopmentRun   # on the machine
adb reverse tcp:8080 tcp:8080                     # phone's localhost -> this machine
                                                  # then open http://localhost:8080 on the phone
adb reverse --remove tcp:8080                     # afterwards
```

This is also the most useful comparison available: the native app and the web app on the same
hardware, the same microphone and the same room.

Compose draws to a canvas, so no accessibility-tree automation can read the web app on a device.
Driving it with `adb shell input tap` against coordinates read off `adb exec-out screencap -p` is
the working method.

### Web audio diagnostics

`gradlew.bat :probe:wasmJsBrowserDevelopmentRun` serves a page that runs the **production**
`:core:audio` web chain and shows its numbers: detected note, frames per second, DSP milliseconds
per frame, dropped frames, and the context's sample rate and latency. The browser tests measure the
same things on every run against a headless Firefox's fake microphone; this page is for putting a
real instrument through the chain. It is not part of the app.

## Test

```
gradlew.bat testAndroidHostTest               # every module's shared tests, on the JVM
gradlew.bat wasmJsBrowserTest                 # the same tests plus the browser ones, in Firefox
gradlew.bat :core:dsp:allTests                # one module, both targets
```

Tests live in `commonTest` by default and therefore run **twice**: once on the Android host target
and once in a browser. That is the portability guard - a `java.*` slip or a `String.format` cannot
reach `main` unnoticed.

Two source sets are deliberately not common:

- `androidHostTest` - `FingeringChartExportTest`, which writes the fingering chart SVGs with
  `java.io.File`.
- `wasmJsTest` - the Compose UI tests. The Android half of these modules has no UI test host, and
  what these cases exist to prove is that a browser can run the shared screens for real: a real
  Skiko canvas, real layout, the real `AudioContext` and the browser's own microphone.

`core/settings/src/androidDeviceTest` holds the one instrumented test, over real
`SharedPreferences`. It needs a device or emulator: `gradlew.bat :core:settings:connectedAndroidDeviceTest`.

The browser tests need **Firefox** installed; Karma's Chrome default is not used. One shared
configuration in `gradle/karma/` gives every module the same headless launcher, the same fake
microphone and the same timeouts.

Waiting in a browser test is the one thing worth knowing about: `ComposeUiTest.waitUntil` is a
synchronous loop over composition frames, so while it runs the browser's single thread is held and
nothing on a `setTimeout` - which is every `delay` in the app - can fire. Anything driven by the
app's own clock is therefore waited for with `awaitInRealTime` from `:core:testing`, which suspends
on a real dispatcher and gives the thread back.

## Notable implementation details

- **Metronome timing** comes from the audio clock, not from coroutine delays. On Android a dedicated
  render thread streams continuous PCM and places clicks at exact sample positions, accumulating
  beat boundaries as fractional sample counts so long sessions do not drift. Kotlin/Wasm has no
  threads, so the browser schedules each click against `AudioContext.currentTime` instead - the
  scheduler can be late, the clicks cannot. Both publish a beat to the UI only once the playback
  head has passed it, which is why the count-in's fifth click is the downbeat on both platforms.
- **Capture and analysis** run off the UI thread on Android. On the web there is only one thread, so
  the worklet runs on the browser's audio thread and the analysis shares the main thread with
  drawing - measured at about 2 ms of DSP per 43 ms frame, and no dropped frames under any of the
  three microphone trainers. The window buffer is reused, and Compose state is refreshed about 25
  times per second rather than per audio frame.
- **Long tone timing is audio time**: `AudioFrame` carries sample counters, and durations are
  derived as samples / sample rate, so neither the displayed duration nor the point at which a note
  ends depends on scheduling or processing latency.
- **Pitch detection is time-domain YIN**, not the strongest FFT bin, so the fundamental is found
  even when a harmonic is louder. `core:dsp` reports frequency and confidence only; cent arithmetic
  lives in `core:music` and the two are combined in the feature. Estimates below the signal gate or
  below the confidence threshold are dropped rather than displayed or averaged.
- **Note practices share one note selection**: Note Trainer, Note Quiz and Long Tones choose their
  written notes in the same full-screen picker - ready-made sets, keys, library phrases and scale
  patterns to preselect from - so a shape like ascending thirds is a scale pattern, not a catalog of
  its own.
- **Fingerings are keyed by written note**, which is why one chart serves the whole saxophone family.
  `core:fingering` says which keys are pressed, `SaxophoneKeyLayout` says where they are drawn, and
  `SaxophoneFingeringView` draws them - no image per note, and no visual data in the model. No two
  notes share a set of pressed keys, which is what makes a fingering-to-note question answerable;
  a test holds the chart to it.
- **Silence and an unclear sound are different things.** `NoteListener.listen()` reports
  `Hearing.Silent`, `Hearing.Unclear` or `Hearing.Pitched`, so an exercise that judges what it hears
  never reports a wobbling attack as a wrong note.
- **The note trainer is driven by the microphone, not by a clock** - the one trainer that is - which
  is what lets it measure a reaction at all. Nothing times out: a note waits until it is played or
  skipped, and how long it took is scored rather than failed.
- **Sample rate and FFT size are configuration**, never constants inside the DSP code.
- **Microphone permission** is requested inside the tool that needs it, at the moment it needs it -
  never on the landing page, and never before a count-in has finished, so a click cannot be heard as
  a played note.
- **Written pitch is what the player reads.** Long Tones stores concert pitch; everything built
  around reading and fingering - the quiz, the note trainer, the sequence trainer - stores the
  written note, because a written G is the same grip on every saxophone.
  `Transposition` is applied at exactly one point, the comparison with the microphone, and nowhere
  else. The instrument is chosen in Settings (tenor Bb by default).
- **The theme is one file** and takes no dynamic colour, so both platforms draw the same palette - a
  browser test reads the pixels back off the Skiko surface to keep it that way. The one input that
  legitimately differs is which palette is chosen: `isSystemInDarkTheme()` reads the device setting
  on Android and `prefers-color-scheme` in a browser.

## Known platform differences

- **Fonts.** Nothing ships a font, so Android renders in Roboto and the browser in whatever Skiko
  falls back to. Text metrics therefore differ slightly; colours, spacing and layout do not.
- **Output latency is not compensated for** on either platform, so the audible click is behind the
  on-screen beat by whatever the device buffers - 34 ms in the browser on the development machine.
  It is a constant offset rather than drift.
- **Kotlin/Wasm is single-threaded**, so a slow frame in the browser is a UI hitch. It can never be
  a glitch in the recording: the worklet runs on the browser's own audio thread.
