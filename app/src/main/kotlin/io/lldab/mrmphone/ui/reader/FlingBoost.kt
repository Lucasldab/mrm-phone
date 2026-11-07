package io.lldab.mrmphone.ui.reader

import android.os.SystemClock
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/**
 * Repeated fast flings in one direction get faster, so a long chapter can be
 * crossed with a few quick swipes. Android caps a single fling's velocity, so
 * without this every swipe moves at the same top speed.
 *
 * A fling is chained when it goes the same way as the previous one, is at
 * least [minVelocity] (px/s), and starts while the previous fling is still
 * moving or within [windowMs] of it stopping (a new touch stops it, so the
 * window covers the swipe itself). Each chained fling multiplies the speed by
 * [factor] again; there is deliberately no upper limit. Anything else (first
 * fling, slow swipe, other direction, a pause) starts over at 1x.
 */
class FlingBoost(
    private val factor: Float = 1.6f,
    private val windowMs: Long = 300,
    private val minVelocity: Float,
) {
    private var multiplier = 1f
    private var direction = 0f
    private var running = false
    private var endedAt = Long.MIN_VALUE

    /** The velocity to fling with for a fling of [velocity] starting at [nowMs]. */
    fun start(velocity: Float, nowMs: Long): Float {
        val dir = sign(velocity)
        val recent = running || (endedAt != Long.MIN_VALUE && nowMs - endedAt <= windowMs)
        val chained = dir != 0f && dir == direction && abs(velocity) >= minVelocity && recent
        multiplier = if (chained) multiplier * factor else 1f
        direction = if (abs(velocity) >= minVelocity) dir else 0f
        running = true
        return velocity * multiplier
    }

    /** The fling finished or was interrupted at [nowMs]. */
    fun end(nowMs: Long) {
        running = false
        endedAt = nowMs
    }
}

/** The default list fling, sped up by [FlingBoost] for repeated fast swipes. */
@Composable
fun rememberBoostedFlingBehavior(): FlingBehavior {
    val base = ScrollableDefaults.flingBehavior()
    val density = LocalDensity.current
    val boost = remember(density) { FlingBoost(minVelocity = with(density) { 2000.dp.toPx() }) }
    return remember(base, boost) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val velocity = boost.start(initialVelocity, SystemClock.uptimeMillis())
                try {
                    return with(base) { performFling(velocity) }
                } finally {
                    boost.end(SystemClock.uptimeMillis())
                }
            }
        }
    }
}
