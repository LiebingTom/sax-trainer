package com.example.saxtrainer.core.dsp.level

import com.example.saxtrainer.core.dsp.sineWave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.sqrt

class RmsCalculatorTest {

    @Test
    fun `rms of a constant signal is its amplitude`() {
        val samples = FloatArray(1000) { 0.5f }
        assertEquals(0.5f, RmsCalculator.rms(samples), 1e-6f)
    }

    @Test
    fun `rms of a full scale sine is one over root two`() {
        val samples = sineWave(1000.0, 48_000, 4800)
        assertEquals((1.0 / sqrt(2.0)).toFloat(), RmsCalculator.rms(samples), 1e-3f)
    }

    @Test
    fun `rms of silence is zero and reports the dbfs floor`() {
        val samples = FloatArray(512)
        assertEquals(0f, RmsCalculator.rms(samples), 0f)
        assertEquals(RmsCalculator.MIN_DBFS, RmsCalculator.toDbfs(0f), 0f)
    }

    @Test
    fun `peak reports the largest absolute sample`() {
        val samples = floatArrayOf(0.1f, -0.8f, 0.3f, 0.75f)
        assertEquals(0.8f, RmsCalculator.peak(samples), 1e-6f)
    }

    @Test
    fun `full scale is zero dbfs and half amplitude is about minus six`() {
        assertEquals(0f, RmsCalculator.toDbfs(1f), 1e-4f)
        assertEquals(-6.02f, RmsCalculator.toDbfs(0.5f), 0.01f)
    }

    @Test
    fun `normalized level spans the meter range`() {
        assertEquals(1f, RmsCalculator.normalizeDbfs(0f, floorDbfs = -60f), 1e-6f)
        assertEquals(0f, RmsCalculator.normalizeDbfs(-60f, floorDbfs = -60f), 1e-6f)
        assertEquals(0.5f, RmsCalculator.normalizeDbfs(-30f, floorDbfs = -60f), 1e-6f)
        assertEquals(0f, RmsCalculator.normalizeDbfs(-90f, floorDbfs = -60f), 1e-6f)
    }

    @Test
    fun `gate opens above the threshold and stays open through small dips`() {
        val gate = SignalGate(openThresholdDbfs = -40f, closeThresholdDbfs = -45f)

        assertFalse(gate.update(-50f))
        assertFalse(gate.update(-42f))
        assertTrue(gate.update(-30f))
        assertTrue(gate.update(-43f), "hysteresis should keep the gate open")
        assertFalse(gate.update(-46f))
    }
}
