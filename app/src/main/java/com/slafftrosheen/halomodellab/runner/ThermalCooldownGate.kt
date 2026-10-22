package com.slafftrosheen.halomodellab.runner

import android.content.Context

/** Persistent monotonic thermal hysteresis. Unknown readings never clear a cooldown. */
class ThermalCooldownGate(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val store: ThermalCooldownStore = MemoryThermalCooldownStore(),
) {
    init { require(cooldownMs > 0L) }

    private val initialCheckpoint = runCatching(store::read)
        .getOrElse { ThermalCooldownCheckpoint(required = true, safeSinceElapsedMs = null) }
    private var safeSinceElapsedMs: Long? = initialCheckpoint.safeSinceElapsedMs
    private var cooldownRequired = initialCheckpoint.required

    @Synchronized
    fun sample(level: ThermalLevel, nowElapsedMs: Long): ThermalGateDecision {
        require(nowElapsedMs >= 0L)
        when (level) {
            ThermalLevel.SEVERE, ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY, ThermalLevel.SHUTDOWN -> {
                val checkpoint = ThermalCooldownCheckpoint(required = true, safeSinceElapsedMs = null)
                if (!persist(checkpoint)) {
                    cooldownRequired = true
                    safeSinceElapsedMs = null
                }
                return ThermalGateDecision.Blocked(ConditionBlockReason.THERMAL_CONSTRAINT)
            }
            ThermalLevel.UNKNOWN -> {
                if (cooldownRequired && !persist(ThermalCooldownCheckpoint(required = true, safeSinceElapsedMs = null))) {
                    safeSinceElapsedMs = null
                }
                return ThermalGateDecision.Blocked(ConditionBlockReason.THERMAL_STATUS_UNKNOWN)
            }
            ThermalLevel.MODERATE -> {
                if (cooldownRequired && !persist(ThermalCooldownCheckpoint(required = true, safeSinceElapsedMs = null))) {
                    safeSinceElapsedMs = null
                }
                return ThermalGateDecision.Blocked(ConditionBlockReason.THERMAL_CONSTRAINT)
            }
            ThermalLevel.NONE, ThermalLevel.LIGHT -> {
                if (!cooldownRequired) return ThermalGateDecision.Ready
                val priorSince = safeSinceElapsedMs
                val since = if (priorSince == null || nowElapsedMs < priorSince) {
                    val checkpoint = ThermalCooldownCheckpoint(required = true, safeSinceElapsedMs = nowElapsedMs)
                    if (persist(checkpoint)) nowElapsedMs else {
                        safeSinceElapsedMs = null
                        return ThermalGateDecision.CoolingDown(cooldownMs)
                    }
                } else priorSince
                if (nowElapsedMs - since < cooldownMs) {
                    return ThermalGateDecision.CoolingDown((cooldownMs - (nowElapsedMs - since)).coerceAtLeast(1L))
                }
                if (!persist(ThermalCooldownCheckpoint(required = false, safeSinceElapsedMs = null))) {
                    return ThermalGateDecision.CoolingDown(1L)
                }
                return ThermalGateDecision.Ready
            }
        }
    }

    private fun persist(checkpoint: ThermalCooldownCheckpoint): Boolean {
        if (checkpoint.required == cooldownRequired && checkpoint.safeSinceElapsedMs == safeSinceElapsedMs) return true
        if (!runCatching { store.write(checkpoint) }.isSuccess) return false
        cooldownRequired = checkpoint.required
        safeSinceElapsedMs = checkpoint.safeSinceElapsedMs
        return true
    }

    companion object { const val DEFAULT_COOLDOWN_MS = 60_000L }
}

data class ThermalCooldownCheckpoint(val required: Boolean, val safeSinceElapsedMs: Long?)

interface ThermalCooldownStore {
    fun read(): ThermalCooldownCheckpoint
    fun write(checkpoint: ThermalCooldownCheckpoint)
}

private class MemoryThermalCooldownStore : ThermalCooldownStore {
    @Volatile private var checkpoint = ThermalCooldownCheckpoint(false, null)
    override fun read(): ThermalCooldownCheckpoint = checkpoint
    override fun write(checkpoint: ThermalCooldownCheckpoint) { this.checkpoint = checkpoint }
}

/** Stores only a safety gate and monotonic timestamp in Lab-private preferences. */
class AndroidThermalCooldownStore(context: Context) : ThermalCooldownStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    override fun read(): ThermalCooldownCheckpoint {
        val required = preferences.getBoolean(KEY_REQUIRED, false)
        val since = if (preferences.contains(KEY_SAFE_SINCE)) preferences.getLong(KEY_SAFE_SINCE, 0L) else null
        return ThermalCooldownCheckpoint(required, since)
    }
    override fun write(checkpoint: ThermalCooldownCheckpoint) {
        val editor = preferences.edit().putBoolean(KEY_REQUIRED, checkpoint.required).apply {
            if (checkpoint.safeSinceElapsedMs == null) remove(KEY_SAFE_SINCE)
            else putLong(KEY_SAFE_SINCE, checkpoint.safeSinceElapsedMs)
        }
        if (!editor.commit()) throw IllegalStateException("THERMAL_COOLDOWN_PERSIST_FAILED")
    }

    private companion object {
        const val PREFERENCES = "hml_thermal_safety_v1"
        const val KEY_REQUIRED = "cooldown_required"
        const val KEY_SAFE_SINCE = "safe_since_elapsed_ms"
    }
}

sealed interface ThermalGateDecision {
    data object Ready : ThermalGateDecision
    data class Blocked(val reason: ConditionBlockReason) : ThermalGateDecision
    data class CoolingDown(val remainingMs: Long) : ThermalGateDecision
}
