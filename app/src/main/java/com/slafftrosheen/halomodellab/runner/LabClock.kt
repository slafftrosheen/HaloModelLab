package com.slafftrosheen.halomodellab.runner

import android.os.SystemClock

/**
 * Injectable clocks.
 *
 * Durations and timeouts use the monotonic clock; wall time is only for report dates.
 * Never subtract elapsedRealtime values across a device reboot.
 */
interface LabClock {
    fun wallTimeMs(): Long
    fun elapsedRealtimeMs(): Long
}

class SystemLabClock : LabClock {
    override fun wallTimeMs(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
