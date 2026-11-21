package org.piramalswasthya.stoptb.repositories

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadBatchingTest {

    @Test
    fun groupsAreBatchedUpToTheLimitWithoutSplittingAGroup() {
        val groups = listOf(40, 50, 30, 100, 5, 120, 1) // members per household group
        val batches = batchBySize(groups, 100) { size -> size }
        assertEquals(listOf(listOf(40, 50), listOf(30), listOf(100), listOf(5), listOf(120), listOf(1)), batches)
        assertEquals("every group is sent exactly once, in order", groups, batches.flatten())
    }

    @Test
    fun smallGroupsFillABatch() {
        val batches = batchBySize(List(250) { 1 }, 100) { size -> size }
        assertEquals(listOf(100, 100, 50), batches.map { batch -> batch.size })
    }

    @Test
    fun noGroupsGiveNoBatches() {
        assertEquals(emptyList<List<Int>>(), batchBySize(emptyList<Int>(), 100) { size -> size })
    }
}
