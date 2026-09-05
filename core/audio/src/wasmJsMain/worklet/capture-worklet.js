// The only JavaScript in the capture chain, because Kotlin/Wasm cannot run inside an AudioWorklet.
//
// It does no analysis: it gathers the browser's 128-sample render quanta into hopSize batches and
// hands each one to the Kotlin side. Everything that looks at the audio lives in Kotlin, shared with
// Android.
//
// Buffers are pooled rather than allocated per batch. A transferred ArrayBuffer is detached from
// this side, so the Kotlin side posts each one back once it has copied the samples out, and this
// processor puts it back in the pool. `poolMisses` counts the times the pool was empty and a batch
// had to allocate; the M3 probe measured zero over 67,488 render quanta.

// M3 finding, and the reason this constant exists: Firefox hands `process` an *empty* input array
// whenever nothing upstream is producing sound, rather than a quantum of zeros. A capture that
// simply returned there would stop delivering frames during a rest - and every exercise clock
// downstream is driven by frames arriving, so a long tone's timer, a sequence's beat matching and
// the note trainer's reaction window would all silently stall the moment the player stopped playing.
// A silent quantum is substituted instead, so silence is delivered as audio and reads as
// `Hearing.Silent` rather than as nothing at all. One render quantum is 128 frames, fixed by the
// specification.
const SILENT_QUANTUM = new Float32Array(128);

class SaxCaptureProcessor extends AudioWorkletProcessor {
    constructor(options) {
        super();
        this.hopSize = options.processorOptions.hopSize;
        this.pool = [];
        for (let i = 0; i < options.processorOptions.poolSize; i++) {
            this.pool.push(new Float32Array(this.hopSize));
        }
        this.batch = this.take();
        this.filled = 0;
        this.posted = 0;
        this.poolMisses = 0;
        this.quanta = 0;
        // Returned buffers come back here to be used again.
        this.port.onmessage = (event) => {
            if (event.data && event.data.buffer) {
                this.pool.push(new Float32Array(event.data.buffer));
            }
        };
    }

    take() {
        const buffer = this.pool.pop();
        if (buffer) return buffer;
        this.poolMisses++;
        return new Float32Array(this.hopSize);
    }

    process(inputs) {
        const channel = (inputs[0] && inputs[0][0]) || SILENT_QUANTUM;
        this.quanta++;

        let offset = 0;
        while (offset < channel.length) {
            const take = Math.min(this.hopSize - this.filled, channel.length - offset);
            this.batch.set(channel.subarray(offset, offset + take), this.filled);
            this.filled += take;
            offset += take;

            if (this.filled === this.hopSize) {
                this.posted++;
                this.port.postMessage(
                    {
                        samples: this.batch,
                        // currentTime is the AudioContext clock, so a latency measured against it
                        // is free of any scheduling delay on the way out of here.
                        capturedAt: currentTime,
                        posted: this.posted,
                        poolMisses: this.poolMisses,
                        quanta: this.quanta,
                    },
                    [this.batch.buffer],
                );
                this.batch = this.take();
                this.filled = 0;
            }
        }
        return true;
    }
}

registerProcessor('sax-capture', SaxCaptureProcessor);
