// The one Karma configuration for every module's browser tests.
//
// Each module used to carry its own copy of this, which drifted: two different mocha timeouts and
// two different launchers for the same kind of test. The Kotlin Gradle plugin lets a module point
// at any directory instead of its own `karma.config.d`, so every wasmJs test task points here.
//
// Two things are set, and both are about the browser rather than about any one module:
//
//  - a headless Firefox with a fake microphone, so `getUserMedia` resolves and the permission a
//    trainer asks for at Start is answered without a person granting it. Modules with no audio are
//    unaffected by it;
//  - timeouts far above mocha's two-second default, because these cases wait on the browser's own
//    clock - exercise ticks, count-ins, audio frames - and the machine running the whole suite is
//    not a quick one. They are deadlines against a hang, not statements about speed: the longest
//    wait any test actually performs is 30 s.

config.set({
    customLaunchers: {
        FirefoxHeadlessFakeAudio: {
            base: 'FirefoxHeadless',
            prefs: {
                'media.navigator.streams.fake': true,
                'media.navigator.permission.disabled': true,
                'media.autoplay.default': 0,
                'media.autoplay.blocking_policy': 0,
            },
        },
    },
    browsers: ['FirefoxHeadlessFakeAudio'],
    client: {
        mocha: { timeout: 60000 },
    },
    browserNoActivityTimeout: 120000,
    browserDisconnectTimeout: 120000,
    captureTimeout: 120000,
});
