package com.slafftrosheen.halomodellab.qualification

import com.slafftrosheen.halomodellab.domain.ExecutionScope

/** Exact equality only. Missing source/backend/fixture identity can never act as a wildcard. */
object ScopeMatcher {
    fun matchesForReuse(
        recordedScopeDigestSha256: String?,
        recordedPolicyDigestSha256: String?,
        currentScope: ExecutionScope,
    ): Boolean = currentScope.mayReuseExactDeviceEvidence() &&
        recordedScopeDigestSha256 == currentScope.canonicalSha256() &&
        recordedPolicyDigestSha256 == currentScope.qualificationPolicySha256
}
