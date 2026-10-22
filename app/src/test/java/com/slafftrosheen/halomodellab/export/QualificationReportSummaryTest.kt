package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.data.QualificationEntity
import com.slafftrosheen.halomodellab.data.RealEvidenceExportSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualificationReportSummaryTest {
    @Test
    fun onlyExplicitRejectedStatusIsCountedAsAnExactScopeExclusion() {
        val summary = qualificationSummary(snapshot(
            qualification("REJECTED_FOR_PROFILE", "session-rejected", "a"),
            qualification("QUALIFIED", "session-qualified", "b"),
            qualification("FUTURE_STATUS", "session-unknown", "c"),
        ))

        assertTrue(summary.contains("Exact-scope exclusions: 1"))
        assertTrue(summary.contains("REJECTED_FOR_PROFILE only; unknown statuses are not exclusions"))
        assertTrue(summary.contains("QUALIFIED=1"))
        assertTrue(summary.contains("UNKNOWN_STATUS=1"))
        assertTrue(summary.contains("retained session session-rejected"))
        assertFalse(summary.contains("retained session session-unknown"))
        assertFalse(summary.contains("retained session session-qualified"))
    }

    private fun snapshot(vararg rows: QualificationEntity) = RealEvidenceExportSnapshot(
        plan = null,
        models = emptyList(),
        configurations = emptyList(),
        binding = null,
        events = emptyList(),
        artifacts = emptyList(),
        qualifications = rows.toList(),
        truncatedSections = emptySet(),
    )

    private fun qualification(status: String, sessionId: String, digestSeed: String) = QualificationEntity(
        id = "projection-$sessionId",
        sessionId = sessionId,
        scopeDigestSha256 = digestSeed.repeat(64),
        policyDigestSha256 = "d".repeat(64),
        status = status,
        evidenceJson = "{}",
        updatedAtWallTimeMs = 1L,
    )
}
