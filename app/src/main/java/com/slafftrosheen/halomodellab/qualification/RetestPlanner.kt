package com.slafftrosheen.halomodellab.qualification

enum class RetestDisposition { REUSE_CACHED_EVIDENCE, RETEST_FAILED_CHECKS, RETEST_FULL_PROFILE }

data class RetestPlan(val disposition: RetestDisposition, val checkIds: Set<String>, val reasonCode: String)

/** Plans work only from a current exact identity; it never turns cached evidence into a new run. */
object RetestPlanner {
    fun plan(
        currentScope: com.slafftrosheen.halomodellab.domain.ExecutionScope,
        previousScopeDigestSha256: String?,
        previousPolicyDigestSha256: String?,
        previousStatus: QualificationStatus?,
        requiredCheckIds: Set<String>,
        failedCheckIds: Set<String> = emptySet(),
        repeatFailedChecks: Boolean = false,
    ): RetestPlan {
        require(requiredCheckIds.isNotEmpty() && requiredCheckIds.size <= 64)
        if (!currentScope.mayReuseExactDeviceEvidence()) {
            return RetestPlan(
                RetestDisposition.RETEST_FULL_PROFILE,
                requiredCheckIds,
                "CURRENT_SCOPE_UNVERIFIED_FRESH_RUN_WILL_NOT_QUALIFY",
            )
        }
        val sameScope = ScopeMatcher.matchesForReuse(
            previousScopeDigestSha256,
            previousPolicyDigestSha256,
            currentScope,
        )
        if (sameScope && previousStatus == QualificationStatus.QUALIFIED && !repeatFailedChecks) {
            return RetestPlan(RetestDisposition.REUSE_CACHED_EVIDENCE, emptySet(), "EXACT_QUALIFIED_EVIDENCE_EXISTS")
        }
        val failed = failedCheckIds.intersect(requiredCheckIds)
        if (sameScope && repeatFailedChecks && failed.isNotEmpty()) {
            return RetestPlan(RetestDisposition.RETEST_FAILED_CHECKS, failed, "EXPLICIT_REPEAT_FAILED_CHECKS")
        }
        return RetestPlan(RetestDisposition.RETEST_FULL_PROFILE, requiredCheckIds, if (sameScope) {
            "PROFILE_INCOMPLETE_OR_EXPLICIT_FRESH_RUN"
        } else "SCOPE_OR_POLICY_CHANGED")
    }
}
