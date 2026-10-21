package com.slafftrosheen.halomodellab.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level checks for [SessionStateMachine].
 *
 * These tests are added in HML-L1 but were NOT executed: see docs/IMPLEMENTATION_STATUS.md.
 */
class SessionStateMachineTest {

    private fun next(
        current: SessionState,
        command: SessionCommand,
        allItemsResolved: Boolean = false,
    ): SessionState? = SessionStateMachine.next(current, command, allItemsResolved)

    @Test
    fun `draft becomes running only through an explicit start`() {
        assertEquals(SessionState.RUNNING, next(SessionState.DRAFT, SessionCommand.START))
        assertNull(next(SessionState.DRAFT, SessionCommand.PAUSE_REQUESTED))
        assertNull(next(SessionState.DRAFT, SessionCommand.PAUSE_APPLIED))
        assertNull(next(SessionState.DRAFT, SessionCommand.STOP_REQUESTED))
        assertNull(next(SessionState.DRAFT, SessionCommand.QUEUE_EXHAUSTED))
    }

    @Test
    fun `activity recreation is not a session command`() {
        val commandNames = SessionCommand.entries.map { it.name }

        assertFalse(
            commandNames.any { it == "ROTATE" || it == "RECREATE" || it == "RESUME_UI" },
        )
    }

    @Test
    fun `pause finishes the current attempt before the next one`() {
        assertEquals(SessionState.PAUSING, next(SessionState.RUNNING, SessionCommand.PAUSE_REQUESTED))
        assertEquals(
            SessionState.PAUSED,
            next(SessionState.PAUSING, SessionCommand.PAUSE_APPLIED, allItemsResolved = false),
        )
        assertEquals(SessionState.RUNNING, next(SessionState.PAUSED, SessionCommand.START))
    }

    @Test
    fun `pause after the last attempt completes the session`() {
        assertEquals(
            SessionState.COMPLETED,
            next(SessionState.PAUSING, SessionCommand.PAUSE_APPLIED, allItemsResolved = true),
        )
    }

    @Test
    fun `stop moves through stopping to cancelled`() {
        assertEquals(SessionState.STOPPING, next(SessionState.RUNNING, SessionCommand.STOP_REQUESTED))
        assertEquals(SessionState.STOPPING, next(SessionState.PAUSING, SessionCommand.STOP_REQUESTED))
        assertEquals(SessionState.CANCELLED, next(SessionState.STOPPING, SessionCommand.STOP_APPLIED))
        assertNull(next(SessionState.PAUSED, SessionCommand.STOP_APPLIED))
    }

    @Test
    fun `process loss interrupts live sessions and keeps paused sessions paused`() {
        assertEquals(
            SessionState.INTERRUPTED,
            next(SessionState.RUNNING, SessionCommand.PROCESS_LOST),
        )
        assertEquals(
            SessionState.INTERRUPTED,
            next(SessionState.PAUSING, SessionCommand.PROCESS_LOST),
        )
        assertEquals(
            SessionState.INTERRUPTED,
            next(SessionState.STOPPING, SessionCommand.PROCESS_LOST),
        )
        assertEquals(
            SessionState.PAUSED,
            next(SessionState.PAUSED, SessionCommand.PROCESS_LOST),
        )
        assertEquals(
            SessionState.DRAFT,
            next(SessionState.DRAFT, SessionCommand.PROCESS_LOST),
        )
    }

    @Test
    fun `interrupted session resumes only after an explicit decision`() {
        assertEquals(SessionState.RUNNING, next(SessionState.INTERRUPTED, SessionCommand.START))
        assertEquals(SessionState.CANCELLED, next(SessionState.INTERRUPTED, SessionCommand.CANCEL))
    }

    @Test
    fun `cancel is limited to draft paused and interrupted`() {
        assertEquals(SessionState.CANCELLED, next(SessionState.DRAFT, SessionCommand.CANCEL))
        assertEquals(SessionState.CANCELLED, next(SessionState.PAUSED, SessionCommand.CANCEL))
        assertNull(next(SessionState.RUNNING, SessionCommand.CANCEL))
        assertNull(next(SessionState.PAUSING, SessionCommand.CANCEL))
        assertNull(next(SessionState.STOPPING, SessionCommand.CANCEL))
    }

    @Test
    fun `terminal states are never rewritten`() {
        SessionState.entries.filter { it.isTerminal() }.forEach { terminal ->
            SessionCommand.entries.forEach { command ->
                assertNull(
                    "Command $command must not change terminal state $terminal",
                    next(terminal, command, allItemsResolved = true),
                )
            }
        }
    }

    @Test
    fun `infrastructure failure fails every open state`() {
        SessionState.entries.filter { it.isOpen() }.forEach { open ->
            assertEquals(
                SessionState.FAILED,
                next(open, SessionCommand.INFRASTRUCTURE_FAILURE),
            )
        }
    }

    @Test
    fun `completed session may contain failed attempts`() {
        // COMPLETED means the queue was processed, not that every check passed.
        assertEquals(SessionState.COMPLETED, next(SessionState.RUNNING, SessionCommand.QUEUE_EXHAUSTED))
        assertTrue(SessionState.COMPLETED.isTerminal())
        assertFalse(SessionState.INTERRUPTED.isTerminal())
    }

    @Test
    fun `queue item state follows the attempt outcome`() {
        assertEquals(
            QueueItemState.DONE,
            SessionStateMachine.queueItemStateFor(AttemptOutcome.PASSED),
        )
        assertEquals(
            QueueItemState.DONE,
            SessionStateMachine.queueItemStateFor(AttemptOutcome.FAILED),
        )
        assertEquals(
            QueueItemState.DONE,
            SessionStateMachine.queueItemStateFor(AttemptOutcome.TIMED_OUT),
        )
        assertEquals(
            QueueItemState.DONE,
            SessionStateMachine.queueItemStateFor(AttemptOutcome.CANCELLED),
        )
        assertEquals(
            QueueItemState.NEEDS_DECISION,
            SessionStateMachine.queueItemStateFor(AttemptOutcome.INTERRUPTED),
        )
        assertEquals(
            QueueItemState.SKIPPED,
            SessionStateMachine.queueItemStateFor(AttemptOutcome.SKIPPED),
        )
    }
}
