// The browser cases wait on the browser rather than on a virtual clock, and a machine running the
// whole suite is not a quick one, so they outlast mocha's two-second default.

config.set({
    client: {
        mocha: { timeout: 60000 },
    },
    browserNoActivityTimeout: 120000,
    browserDisconnectTimeout: 120000,
    captureTimeout: 120000,
});
