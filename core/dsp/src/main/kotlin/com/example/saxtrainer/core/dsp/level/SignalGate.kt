package com.example.saxtrainer.core.dsp.level

/**
 * Decides whether a signal is present, with hysteresis so a note that dips slightly does not make
 * the indicator flicker.
 *
 * Thresholds live here (or in a feature's configuration), never inside a Composable.
 */
class SignalGate(
    private val openThresholdDbfs: Float = DEFAULT_OPEN_DBFS,
    private val closeThresholdDbfs: Float = DEFAULT_CLOSE_DBFS,
) {
    init {
        require(closeThresholdDbfs <= openThresholdDbfs) {
            "close threshold must not be above open threshold"
        }
    }

    var isOpen: Boolean = false
        private set

    /** Feeds one level measurement and returns whether the gate is open afterwards. */
    fun update(levelDbfs: Float): Boolean {
        isOpen = if (isOpen) levelDbfs > closeThresholdDbfs else levelDbfs > openThresholdDbfs
        return isOpen
    }

    fun reset() {
        isOpen = false
    }

    companion object {
        const val DEFAULT_OPEN_DBFS = -42f
        const val DEFAULT_CLOSE_DBFS = -48f
    }
}
