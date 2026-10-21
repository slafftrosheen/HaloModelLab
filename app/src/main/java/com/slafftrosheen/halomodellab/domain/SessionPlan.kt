package com.slafftrosheen.halomodellab.domain

/**
 * Limits for the v0 plan builder. Values are validated before anything is stored.
 */
object PlanLimits {
    val REPEATS: IntRange = 1..20
    val CASE_TIMEOUT_MS: LongRange = 1_000L..120_000L
    val BUDGET_MS: LongRange = 60_000L..3_600_000L

    const val DEFAULT_REPEATS: Int = 1
    const val DEFAULT_CASE_TIMEOUT_MS: Long = 10_000L
    const val DEFAULT_BUDGET_MS: Long = 5 * 60_000L
}

/**
 * One immutable queue entry: one scenario and one planned repetition.
 *
 * `subjectId` is the fixture subject; `scenarioId` is the controlled scenario executed by the
 * demo engine. A retry of an interrupted item is a new attempt of the same item, not a new item.
 */
data class PlannedItem(
    val subjectId: String,
    val scenarioId: String?,
    val role: ModelRole,
    val requestedBackend: ComputeBackend,
    val repeatIndex: Int,
) {
    init {
        require(subjectId.isNotBlank())
        require(repeatIndex >= 0)
    }
}

/**
 * Immutable plan snapshot, written before start and never edited afterwards.
 *
 * To change parameters, the user creates a new session with a new plan.
 */
data class SessionPlan(
    val profileId: String,
    val origin: EvidenceOrigin,
    val engineId: String,
    val caseTimeoutMs: Long,
    val budgetMs: Long,
    val repeatCount: Int,
    val requireCharging: Boolean,
    val items: List<PlannedItem>,
) {
    init {
        require(profileId.isNotBlank())
        require(engineId.isNotBlank())
        require(repeatCount in PlanLimits.REPEATS)
        require(caseTimeoutMs in PlanLimits.CASE_TIMEOUT_MS)
        require(budgetMs in PlanLimits.BUDGET_MS)
        require(items.isNotEmpty())
        require(items.all { it.repeatIndex in 0 until repeatCount })
    }

    fun encode(): String = SessionPlanCodec.encode(this)
}

/**
 * Canonical JSON writer for the plan snapshot.
 *
 * Field order and number formatting are fixed so two equal plans encode identically.
 * Decoding is intentionally absent in v0: the exporter rebuilds the plan from relational
 * rows and includes this string as an immutable snapshot.
 */
object SessionPlanCodec {
    fun encode(plan: SessionPlan): String = buildString {
        append('{')
        stringField("profileId", plan.profileId)
        append(',')
        stringField("origin", plan.origin.name)
        append(',')
        stringField("engineId", plan.engineId)
        append(',')
        append("\"caseTimeoutMs\":").append(plan.caseTimeoutMs)
        append(',')
        append("\"budgetMs\":").append(plan.budgetMs)
        append(',')
        append("\"repeatCount\":").append(plan.repeatCount)
        append(',')
        append("\"requireCharging\":").append(plan.requireCharging)
        append(',')
        append("\"items\":[")
        plan.items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append('{')
            stringField("subjectId", item.subjectId)
            append(',')
            append("\"scenarioId\":")
            if (item.scenarioId == null) {
                append("null")
            } else {
                appendJsonString(item.scenarioId)
            }
            append(',')
            stringField("role", item.role.name)
            append(',')
            stringField("requestedBackend", item.requestedBackend.name)
            append(',')
            append("\"repeatIndex\":").append(item.repeatIndex)
            append('}')
        }
        append("]}")
    }

    private fun StringBuilder.stringField(name: String, value: String) {
        appendJsonString(name)
        append(':')
        appendJsonString(value)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}
