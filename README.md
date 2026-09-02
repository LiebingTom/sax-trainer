# Sax Trainer

Android practice tools for saxophone. The first version ships two training tools - a metronome and a
long tone exercise with live level and spectrum feedback - on an architecture built for adding more.

## Modules

```
app                    MainActivity, AppContainer, navigation graph, landing page
core/designsystem      theme, typography, shapes, Dimens, reusable components (FeatureCard, FeatureIcon, ...)
core/music             pure Kotlin: Note, NoteName, Transposition, frequency maths
core/dsp               pure Kotlin: WindowFunction, Fft, Spectrum, SpectrumAnalyzer, RmsCalculator, SignalGate
core/audio             AudioRecord capture, AudioTrack playback, permission helper, error types
core/settings          AppSettings + SettingsRepository (SharedPreferences backed)
feature/metronome      timing engine, click synthesis, screen, ViewModel
feature/longtones      exercise state machine, DSP processing, screen, ViewModel
feature/settings       instrument selection screen
```

Dependencies point one way: `app -> feature -> core`. Features never depend on each other, and no
core module knows about a feature. `core/dsp` and `core/music` contain no Android code so they can be
unit tested on the JVM.

### Adding a training tool

1. Create `feature/<tool>` with its screen, ViewModel and state.
2. Reuse `core/audio`, `core/dsp` and `core/designsystem` - do not open a second recorder or write a
   second FFT.
3. Add a `HomeEntry` in `app/home/HomeEntry.kt` and a destination in
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
  as samples / sample rate, so neither the displayed duration nor the completion point depends on
  scheduling or processing latency. The target duration is the length of the *attempt*; the gated-on
  portion is tracked separately as `soundingSeconds`, ready to become validated held time once pitch
  detection exists.
- **Sample rate and FFT size are configuration**, never constants inside the DSP code.
- **Microphone permission** is requested inside Long Tones on first start, never on the landing page.
- **Transposition is presentation only** - pitches are stored as concert pitch and labelled according
  to the instrument chosen in Settings (tenor Bb by default).

## Build and test

```
gradlew.bat assembleDebug                     # build the app
gradlew.bat :core:dsp:test :core:music:test   # DSP and music theory unit tests
gradlew.bat testDebugUnitTest                 # all unit tests
gradlew.bat lintDebug                         # lint
```
