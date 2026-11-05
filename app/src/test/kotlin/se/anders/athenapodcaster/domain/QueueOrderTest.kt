package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** FR-100..102: queue edits. */
class QueueOrderTest {
    @Test fun `add appends once`() {
        assertEquals(listOf(1L, 2L), QueueOrder.add(listOf(1L), 2L))
        assertEquals(listOf(1L, 2L), QueueOrder.add(listOf(1L, 2L), 1L))
    }

    @Test fun `remove drops the id and ignores unknown ones`() {
        assertEquals(listOf(1L, 3L), QueueOrder.remove(listOf(1L, 2L, 3L), 2L))
        assertEquals(listOf(1L), QueueOrder.remove(listOf(1L), 9L))
    }

    @Test fun `move up and down swap neighbours`() {
        assertEquals(listOf(2L, 1L, 3L), QueueOrder.moveUp(listOf(1L, 2L, 3L), 2L))
        assertEquals(listOf(1L, 3L, 2L), QueueOrder.moveDown(listOf(1L, 2L, 3L), 2L))
    }

    @Test fun `moving past the ends or an unknown id changes nothing`() {
        val q = listOf(1L, 2L, 3L)
        assertEquals(q, QueueOrder.moveUp(q, 1L))
        assertEquals(q, QueueOrder.moveDown(q, 3L))
        assertEquals(q, QueueOrder.moveUp(q, 9L))
    }
}
