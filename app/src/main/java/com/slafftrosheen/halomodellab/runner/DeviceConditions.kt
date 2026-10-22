package com.slafftrosheen.halomodellab.runner

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/** Severity ordering for thermal status. UNKNOWN is not zero and never proves a safe device. */
enum class ThermalLevel {
    UNKNOWN,
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    ;

    val isBlocking: Boolean
        get() = this == SEVERE || this == CRITICAL || this == EMERGENCY || this == SHUTDOWN

    val isModerateOrWorse: Boolean
        get() = this == MODERATE || isBlocking
}

data class ConditionSnapshot(
    val charging: Boolean?,
    val thermalLevel: ThermalLevel,
    val freeStorageBytes: Long?,
    val evidenceStoredBytes: Long? = null,
)

enum class ConditionBlockReason {
    POWER_CONSTRAINT,
    THERMAL_CONSTRAINT,
    THERMAL_STATUS_UNKNOWN,
    INSUFFICIENT_STORAGE,
    STORAGE_STATUS_UNKNOWN,
    EVIDENCE_QUOTA_EXCEEDED,
    EVIDENCE_SIZE_UNKNOWN,
}

/** Released in a finally block; the lock never outlives the attempt. */
interface WakeLockHandle {
    fun release()
}

interface DeviceConditions {
    fun snapshot(): ConditionSnapshot
    fun acquireAttemptWakeLock(timeoutMs: Long): WakeLockHandle
}

/**
 * Android-backed conditions.
 *
 * A missing sensor or an unavailable value is reported as null/UNKNOWN, never as a false
 * "everything is fine" measurement or as a zero.
 */
class AndroidDeviceConditions(
    context: Context,
    private val minimumFreeBytes: Long = MINIMUM_FREE_BYTES_V0,
) : DeviceConditions {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    override fun snapshot(): ConditionSnapshot = ConditionSnapshot(
        charging = readCharging(),
        thermalLevel = readThermalLevel(),
        freeStorageBytes = readFreeStorageBytes(),
        evidenceStoredBytes = readEvidenceBytes(),
    )

    override fun acquireAttemptWakeLock(timeoutMs: Long): WakeLockHandle {
        val lock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HaloModelLab:attempt",
        )
        lock.setReferenceCounted(false)
        lock.acquire(timeoutMs.coerceAtLeast(1_000L))
        return object : WakeLockHandle {
            override fun release() {
                if (lock.isHeld) lock.release()
            }
        }
    }

    private fun readCharging(): Boolean? {
        val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL,
            -> true

            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            -> false

            else -> null
        }
    }

    private fun readThermalLevel(): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalLevel.UNKNOWN
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
            else -> ThermalLevel.UNKNOWN
        }
    }

    private fun readFreeStorageBytes(): Long? = try {
        StatFs(appContext.cacheDir.absolutePath).availableBytes
    } catch (failure: Exception) {
        null
    }

    /** Counts only Lab-owned database journals and report cache; Halo model storage is excluded. */
    private fun readEvidenceBytes(): Long? = try {
        val database = appContext.getDatabasePath(com.slafftrosheen.halomodellab.data.LabDatabase.FILE_NAME)
        val files = listOf(database, java.io.File(database.path + "-wal"), java.io.File(database.path + "-shm")) +
            java.io.File(appContext.cacheDir, "exports").listFiles().orEmpty()
        files.sumOf { if (it.isFile) it.length().coerceAtLeast(0L) else 0L }
    } catch (failure: Exception) {
        null
    }

    companion object {
        /** Project reserve, not a hardware guarantee. */
        const val MINIMUM_FREE_BYTES_V0: Long = 128L * 1024L * 1024L
        const val MAX_EVIDENCE_BYTES_V1: Long = 64L * 1024L * 1024L
    }
}
