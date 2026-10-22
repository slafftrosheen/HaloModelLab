package com.slafftrosheen.halomodellab.runner

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Injectable clocks.
 *
 * Durations and timeouts use the monotonic clock; wall time is only for report dates.
 * Never subtract elapsedRealtime values across a device reboot.
 */
interface LabClock {
    fun wallTimeMs(): Long
    fun elapsedRealtimeMs(): Long
    /** Stable only within one boot. Unknown is never treated as comparable. */
    fun bootId(): String = UNKNOWN_BOOT_ID

    companion object { const val UNKNOWN_BOOT_ID = "unknown" }
}

class SystemLabClock(context: Context) : LabClock {
    private val bootIdentity = runCatching {
        Settings.Global.getInt(context.applicationContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
            .takeIf { it >= 0 }?.toString()
    }.getOrNull() ?: LabClock.UNKNOWN_BOOT_ID

    override fun wallTimeMs(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun bootId(): String = bootIdentity
}
