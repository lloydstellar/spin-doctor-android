package nl.lloydstellar.spindoctor.model

/**
 * A record that starts from standstill, like a turntable switched on: the
 * speed rises and lands on full speed exactly after [duration] seconds, with
 * no overshoot and no long tail (a tail would leave the music audibly flat).
 *
 *   speed(x) = 1 - (1 - x)^2   for x = t / duration in 0..1, then 1
 *
 * The record and the music use the same curve, so the sound slides up from
 * a rumble to normal pitch while the record comes up to speed.
 */
object SpinUp {

    /** Speed at [t] seconds as a fraction of full speed, 0..1. */
    fun speed(t: Double, duration: Double): Double {
        if (duration <= 0 || t >= duration) return 1.0
        if (t <= 0) return 0.0
        val x = t / duration
        return 1 - (1 - x) * (1 - x)
    }

    /** Seconds of music played (and of record turned at full speed) by [t]: the integral of [speed]. */
    fun played(t: Double, duration: Double): Double {
        if (duration <= 0) return t
        if (t <= 0) return 0.0
        if (t >= duration) return duration * 2 / 3 + (t - duration)
        val x = t / duration
        val rest = 1 - x
        return duration * (x - (1 - rest * rest * rest) / 3)
    }
}
