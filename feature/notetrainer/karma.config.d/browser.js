// Firefox headless with a fake microphone, so the permission the trainer asks for at Start is
// answered without anyone granting it, and with a long timeout, because these cases run a real
// exercise on the browser's own clock rather than on a virtual one. Same launcher as :core:audio.

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
