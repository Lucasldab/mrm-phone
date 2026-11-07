package io.lldab.mrmphone.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class FlingBoostTest {
    private val fast = 5000f
    private fun boost() = FlingBoost(factor = 1.6f, windowMs = 300, minVelocity = 2000f)

    /** A fling that starts at [at] and is interrupted or finishes at [endAt]. */
    private fun FlingBoost.fling(v: Float, at: Long, endAt: Long): Float =
        start(v, at).also { end(endAt) }

    @Test fun first_fling_is_unchanged() {
        assertEquals(fast, boost().start(fast, 0), 0f)
    }

    @Test fun quick_repeated_flings_in_the_same_direction_speed_up() {
        val b = boost()
        b.fling(fast, at = 0, endAt = 400)
        assertEquals(fast * 1.6f, b.fling(fast, at = 500, endAt = 900), 0.5f)
        assertEquals(fast * 1.6f * 1.6f, b.start(fast, 1000), 0.5f)
    }

    @Test fun no_cap_on_the_multiplier() {
        val b = boost()
        var t = 0L
        var v = 0f
        repeat(12) { v = b.fling(fast, at = t, endAt = t + 100); t += 200 }
        // 1.6^11 ≈ 176
        assertEquals(fast * Math.pow(1.6, 11.0).toFloat(), v, fast)
    }

    @Test fun a_fling_started_while_the_previous_one_still_runs_counts() {
        val b = boost()
        b.start(fast, 0)                       // still running, no end yet
        assertEquals(fast * 1.6f, b.start(fast, 5000), 0.5f)
    }

    @Test fun direction_change_resets() {
        val b = boost()
        b.fling(fast, at = 0, endAt = 100)
        b.fling(fast, at = 200, endAt = 300)
        assertEquals(-fast, b.start(-fast, 400), 0f)
    }

    @Test fun a_pause_resets() {
        val b = boost()
        b.fling(fast, at = 0, endAt = 100)
        b.fling(fast, at = 200, endAt = 300)
        assertEquals(fast, b.start(fast, 300 + 301), 0f)
    }

    @Test fun a_slow_swipe_is_not_boosted_and_resets_the_chain() {
        val b = boost()
        b.fling(fast, at = 0, endAt = 100)
        b.fling(fast, at = 200, endAt = 300)
        assertEquals(1500f, b.fling(1500f, at = 400, endAt = 500), 0f)
        assertEquals(fast, b.start(fast, 600), 0f)
    }

    @Test fun zero_velocity_passes_through() {
        val b = boost()
        b.fling(fast, at = 0, endAt = 100)
        assertEquals(0f, b.start(0f, 200), 0f)
    }
}
