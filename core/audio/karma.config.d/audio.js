// Firefox headless with a fake microphone, so getUserMedia can be exercised without anyone granting
// a permission, and with a long timeout, because the web audio cases wait on real time passing
// rather than on a virtual clock.

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
        mocha: { timeout: 120000 },
    },
    browserNoActivityTimeout: 180000,
    browserDisconnectTimeout: 120000,
    captureTimeout: 120000,
});
