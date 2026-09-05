// Firefox headless with a fake microphone, because the page builds the real capture and the real
// permission query, and with a long timeout, because these cases walk the app on the browser's real
// clock. Same launcher as :core:audio.

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
