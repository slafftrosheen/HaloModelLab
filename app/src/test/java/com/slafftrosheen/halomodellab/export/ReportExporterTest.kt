package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.data.BeginAttempt
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptResult
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.EvidenceConfidence
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.PlannedItem
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.SessionPlan
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.TestStage
import com.slafftrosheen.halomodellab.runner.FakeSessionRepository
import com.slafftrosheen.halomodellab.runner.LabClock
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val clock = object : LabClock {
        override fun wallTimeMs(): Long = 1_790_000_000_000L
        override fun elapsedRealtimeMs(): Long = 0L
    }

    private lateinit var repository: FakeSessionRepository
    private lateinit var exporter: ReportExporter
    private lateinit var exportRoot: File
    private lateinit var sessionId: String

    @Before
    fun setUp() {
        sessionId = UUID.randomUUID().toString()
        repository = FakeSessionRepository().apply { this.sessionId = this@ReportExporterTest.sessionId }
        exportRoot = File(tempFolder.newFolder(), "exports")
        exporter = ReportExporter(
            repository = repository,
            exportRoot = exportRoot,
            clock = clock,
            packageName = "com.slafftrosheen.halomodellab",
            appVersion = "0.1.0-test",
        )
    }

    @Test
    fun archiveContainsEveryRequiredEntryInOrder() = runTest {
        completedSessionWithFailure()

        val file = export()

        ZipFile(file).use { zip ->
            val names = zip.entries().toList().map { it.name }
            val failureNames = names.filter { it.startsWith("failures/") &&
                it != "failures/index.json" && it != "failures/clusters.json" }
            assertEquals("report.md", names.first())
            assertEquals(1, failureNames.size)
            assertEquals(REQUIRED_ENTRIES + failureNames, names.toSet())
            assertEquals(1, names.count { it == ReportExporter.CHECKSUMS_ENTRY })
            assertTrue(failureNames.all { entry ->
                UUID_PATTERN.matches(entry.removePrefix("failures/").removeSuffix(".json"))
            })
        }

        assertFalse(File(exportRoot, file.name + ".part").exists())
    }

    @Test
    fun manifestCopiesOriginAndKeepsRuntimeHonest() = runTest {
        completedSessionWithFailure()

        val manifest = String(entryBytes(export(), ReportExporter.MANIFEST_ENTRY), Charsets.UTF_8)

        assertTrue(manifest.contains("\"schemaVersion\": 2"))
        assertTrue(manifest.contains("\"exportVersion\": 2"))
        assertTrue(manifest.contains("\"sessionOrigin\": \"SYNTHETIC\""))
        assertTrue(manifest.contains("\"evidenceOrigin\": \"SYNTHETIC\""))
        assertTrue(manifest.contains("\"synthetic\": true"))
        assertTrue(manifest.contains("\"bridgeStatus\": \"NOT_APPLICABLE_SYNTHETIC\""))
        assertTrue(manifest.contains("\"inferenceRuntime\": null"))
        assertTrue(manifest.contains("\"packageName\": \"com.slafftrosheen.halomodellab\""))
        assertTrue(manifest.contains("\"versionName\": \"0.1.0-test\""))
        assertTrue(manifest.contains("\"sessionState\": \"COMPLETED\""))
        assertTrue(manifest.contains("\"snapshotEventSeq\":"))
        assertTrue(manifest.contains("\"privacyMode\": \"local_only_no_network\""))
        // The manifest never hashes itself and checksums.sha256 is not a payload entry.
        assertFalse(manifest.contains("\"name\": \"manifest.json\""))
        assertFalse(manifest.contains("\"name\": \"checksums.sha256\""))
    }

    @Test
    fun resultsRetainOriginValidityAndFailureCode() = runTest {
        completedSessionWithFailure()

        val results = String(entryBytes(export(), "results.json"), Charsets.UTF_8)

        assertTrue(results.contains("\"origin\": \"SYNTHETIC\""))
        assertTrue(results.contains("\"validity\": \"VALID\""))
        assertTrue(results.contains("\"outcome\": \"FAILED\""))
        assertTrue(results.contains("SIMULATED_ADAPTER_LOAD_FAILURE"))
        assertTrue(results.contains("\"code\": \"SIMULATED_ADAPTER_LOAD_FAILURE\""))
        assertFalse(results.contains(exportRoot.absolutePath))
    }

    @Test
    fun newHandoffSectionsKeepSyntheticEvidenceAndUnavailableComparisonExplicit() = runTest {
        completedSessionWithFailure()
        val file = export()

        val coverage = String(entryBytes(file, "coverage.json"), Charsets.UTF_8)
        val comparison = String(entryBytes(file, "comparison.json"), Charsets.UTF_8)
        val failures = String(entryBytes(file, "failures/index.json"), Charsets.UTF_8)
        val clusters = String(entryBytes(file, "failures/clusters.json"), Charsets.UTF_8)

        assertTrue(coverage.contains("\"coverageStatus\": \"SYNTHETIC_ONLY\""))
        assertTrue(comparison.contains("NO_COMPATIBLE_PREVIOUS_SESSION_SELECTED"))
        assertTrue(failures.contains("SIMULATED_ADAPTER_LOAD_FAILURE"))
        assertTrue(failures.contains("failures/"))
        assertTrue(clusters.contains("current session only"))
        assertTrue(clusters.contains("SIMULATED_ADAPTER_LOAD_FAILURE"))
    }

    @Test
    fun selectedMissingPreviousSessionIsRecordedAsUnavailable() = runTest {
        completedSessionWithFailure()
        val result = exporter.export(sessionId, "previous-session-missing")
        assertTrue(result is ExportResult.Success)
        val comparison = String(entryBytes((result as ExportResult.Success).file, "comparison.json"), Charsets.UTF_8)
        assertTrue(comparison.contains("PREVIOUS_SESSION_MISSING"))
        assertTrue(comparison.contains("previous-session-missing"))
    }

    @Test
    fun checksumsCoverEveryPayloadEntryAndMatchBytes() = runTest {
        completedSessionWithFailure()

        val file = export()
        ZipFile(file).use { zip ->
            val checksumsEntry = requireNotNull(zip.getEntry(ReportExporter.CHECKSUMS_ENTRY))
            val checksums = zip.getInputStream(checksumsEntry)
                .readBytes()
                .toString(Charsets.UTF_8)
                .trim()
                .lines()
            val allNames = zip.entries().toList().map { it.name }
            val declared = checksums.map { line ->
                val parts = line.split("  ", limit = 2)
                assertEquals(64, parts[0].length)
                parts[1]
            }.toSet()

            assertEquals(allNames.toSet() - ReportExporter.CHECKSUMS_ENTRY, declared)
            checksums.forEach { line ->
                val (hash, name) = line.split("  ", limit = 2)
                val entry = requireNotNull(zip.getEntry(name)) { "missing checksum entry $name" }
                val bytes = zip.getInputStream(entry).readBytes()
                assertEquals("checksum mismatch for $name", hash, sha256(bytes))
            }
        }
    }

    @Test
    fun eventsKeepStoredOrderAndSequences() = runTest {
        completedSessionWithFailure()

        val lines = String(entryBytes(export(), "events.jsonl"), Charsets.UTF_8)
            .trim()
            .lines()

        assertEquals(repository.events.size, lines.size)
        lines.forEachIndexed { index, line ->
            assertEquals(repository.events[index].seq, SEQ_PATTERN.find(line)!!.groupValues[1].toLong())
            assertTrue(line.contains(repository.events[index].code))
        }
    }

    @Test
    fun telemetryCsvEscapesAndNeutralizesFormulas() = runTest {
        completedSessionWithFailure()

        val csv = String(entryBytes(export(), "telemetry.csv"), Charsets.UTF_8)

        assertTrue(csv.startsWith("name,attemptId,value,unit,scope,origin,unavailableReason,wallTimeMs"))
        // Formula neutralization plus quoting of comma and quote characters.
        assertTrue(csv.contains("\"'=no,sensor \"\"x\"\"\""))
        assertTrue(csv.contains("free_storage"))
        // A missing measurement stays empty, never zero. Session-level samples carry no
        // attemptId, so both the attemptId and the value columns are empty.
        assertTrue(csv.contains("battery_temp,,,celsius"))
    }

    @Test
    fun entryNamesAreSafeAndNoPrivatePathLeaks() = runTest {
        completedSessionWithFailure()

        val file = export()
        ZipFile(file).use { zip ->
            zip.entries().toList().forEach { entry ->
                val name = entry.name
                assertFalse(name.contains(".."))
                assertFalse(name.startsWith("/"))
                assertFalse(name.contains("\\"))
            }
            zip.entries().toList().forEach { entry ->
                val text = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                assertFalse(text.contains(exportRoot.absolutePath))
                assertFalse(text.contains("filesDir"))
                assertFalse(text.contains("Authorization"))
            }
        }
    }

    @Test
    fun runningSessionIsNotExportable() = runTest {
        repository.createSession(plan())
        assertTrue(repository.claimSession(sessionId, OWNER))

        val result = exporter.export(sessionId)

        assertEquals(
            ExportResult.NotExportable(SessionState.RUNNING),
            result,
        )
        assertTrue(exportRoot.listFiles().isNullOrEmpty())
    }

    @Test
    fun missingSessionIsReported() = runTest {
        assertEquals(ExportResult.MissingSession("nope"), exporter.export("nope"))
    }

    @Test
    fun tooMuchTelemetryIsTruncatedWithAnOmission() = runTest {
        repository.createSession(plan())
        repository.claimSession(sessionId, OWNER)
        val ready = repository.beginAttempt(sessionId, OWNER) as BeginAttempt.Ready
        repository.finishAttempt(
            ready.request.attemptId,
            AttemptResult(
                outcome = AttemptOutcome.PASSED,
                validity = ResultValidity.VALID,
                origin = EvidenceOrigin.SYNTHETIC,
            ),
        )
        repository.enterCompleted(sessionId, OWNER)

        repeat(5_100) { index ->
            repository.recordTelemetry(
                sessionId = sessionId,
                attemptId = null,
                name = "sample_$index",
                value = index.toDouble(),
                unit = "ms",
                scope = "device",
                origin = EvidenceOrigin.DEVICE_MEASUREMENT,
                unavailableReason = null,
            )
        }

        val manifest = String(entryBytes(export(), ReportExporter.MANIFEST_ENTRY), Charsets.UTF_8)

        assertTrue(manifest.contains("telemetry.csv truncated: included 5000 of 5100 samples."))
    }

    private suspend fun completedSessionWithFailure() {
        repository.createSession(plan())
        assertTrue(repository.claimSession(sessionId, OWNER))
        repository.recordTelemetry(
            sessionId = sessionId,
            attemptId = null,
            name = "free_storage",
            value = 4_096.0,
            unit = "bytes",
            scope = "device",
            origin = EvidenceOrigin.DEVICE_MEASUREMENT,
            unavailableReason = null,
        )
        repository.recordTelemetry(
            sessionId = sessionId,
            attemptId = null,
            name = "battery_temp",
            value = null,
            unit = "celsius",
            scope = "device",
            origin = EvidenceOrigin.DEVICE_MEASUREMENT,
            unavailableReason = "=no,sensor \"x\"",
        )
        val ready = repository.beginAttempt(sessionId, OWNER) as BeginAttempt.Ready
        repository.recordStage(ready.request.attemptId, TestStage.LOAD, "LOAD_STARTED")
        repository.finishAttempt(
            ready.request.attemptId,
            AttemptResult(
                outcome = AttemptOutcome.FAILED,
                validity = ResultValidity.VALID,
                origin = EvidenceOrigin.SYNTHETIC,
                failure = FailureInfo(
                    code = "SIMULATED_ADAPTER_LOAD_FAILURE",
                    domain = FailureDomain.ADAPTER,
                    confidence = EvidenceConfidence.OBSERVED,
                    detail = "Synthetic adapter refused to load.",
                ),
            ),
        )
        repository.enterCompleted(sessionId, OWNER)
    }

    private fun plan(): SessionPlan = SessionPlan(
        profileId = "demo-profile",
        origin = EvidenceOrigin.SYNTHETIC,
        engineId = "demo-engine",
        caseTimeoutMs = 10_000L,
        budgetMs = 300_000L,
        repeatCount = 1,
        requireCharging = false,
        items = listOf(
            PlannedItem(
                subjectId = "demo/success",
                scenarioId = "demo/success",
                role = ModelRole.CHAT,
                requestedBackend = ComputeBackend.CPU,
                repeatIndex = 0,
            ),
        ),
    )

    private suspend fun export(): File {
        val result = exporter.export(sessionId)
        assertTrue("unexpected export result: $result", result is ExportResult.Success)
        return (result as ExportResult.Success).file
    }

    private fun entryBytes(file: File, name: String): ByteArray = ZipFile(file).use { zip ->
        val entry = requireNotNull(zip.getEntry(name)) { "missing entry $name" }
        zip.getInputStream(entry).readBytes()
    }

    companion object {
        private const val OWNER = "owner-1"

        private val REQUIRED_ENTRIES = setOf(
            "report.md",
            "manifest.json",
            "results.json",
            "events.jsonl",
            "telemetry.csv",
            "coverage.json",
            "qualification.json",
            "comparison.json",
            "failures/index.json",
            "failures/clusters.json",
            "checksums.sha256",
        )

        private val SEQ_PATTERN = Regex("\"seq\":\\s*(\\d+)")
        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { "%02x".format(it) }
    }
}
