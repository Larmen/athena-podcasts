package se.anders.athenapodcaster.domain

/** FR-100..102: the queue as a plain list of episode ids; every change returns a new list. */
object QueueOrder {
    /** Appends [id] unless it is already queued. */
    fun add(queue: List<Long>, id: Long): List<Long> = if (id in queue) queue else queue + id

    fun remove(queue: List<Long>, id: Long): List<Long> = queue - id

    fun moveUp(queue: List<Long>, id: Long): List<Long> = swap(queue, id, -1)

    fun moveDown(queue: List<Long>, id: Long): List<Long> = swap(queue, id, +1)

    private fun swap(queue: List<Long>, id: Long, by: Int): List<Long> {
        val i = queue.indexOf(id)
        val j = i + by
        if (i < 0 || j !in queue.indices) return queue
        return queue.toMutableList().also { it[i] = it[j]; it[j] = id }
    }
}
