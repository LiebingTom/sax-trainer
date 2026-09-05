// M3 PROBE - TEMPORARY. Delete with the probe/ directory.
//
// Firefox headless with a fake microphone, so getUserMedia can be exercised without anyone granting
// a permission, and with a long timeout, because the stability case runs for minutes rather than
// the seconds a unit test takes.

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
        mocha: { timeout: 240000 },
    },
    browserNoActivityTimeout: 300000,
    browserDisconnectTimeout: 120000,
    captureTimeout: 120000,
});
