package com.example.saxtrainer.core.dsp.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WindowFunctionTest {

    private val size = 4096

    @Test
    fun `hann window has the requested length`() {
        assertEquals(size, HannWindow().coefficients(size).size)
    }

    @Test
    fun `hann window starts at zero and peaks in the middle`() {
        val window = HannWindow().coefficients(size)
        assertEquals(0f, window[0], 1e-6f)
        assertEquals(1f, window[size / 2], 1e-6f)
    }

    @Test
    fun `periodic hann window is symmetric around its centre`() {
        val window = HannWindow().coefficients(size)
        for (i in 1 until size / 2) {
            assertEquals(window[i], window[size - i], 1e-6f)
        }
    }

    @Test
    fun `hann coherent gain is one half`() {
        assertEquals(0.5f, HannWindow().coherentGain(size), 1e-4f)
    }

    @Test
    fun `rectangular window passes samples unchanged`() {
        val window = RectangularWindow().coefficients(8)
        assertTrue(window.all { it == 1f })
        assertEquals(1f, RectangularWindow().coherentGain(8), 1e-6f)
    }

    @Test
    fun `coefficients are cached per size`() {
        val hann = HannWindow()
        assertSame(hann.coefficients(size), hann.coefficients(size))
    }
}
