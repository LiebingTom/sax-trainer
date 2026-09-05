// The shell's cases walk the landing page to a destination and back several times over, on the
// browser's real clock rather than a virtual one, so they outlast mocha's two-second default.

config.set({
    client: {
        mocha: { timeout: 60000 },
    },
    browserNoActivityTimeout: 120000,
});
