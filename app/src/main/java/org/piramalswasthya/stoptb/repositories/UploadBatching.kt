package org.piramalswasthya.stoptb.repositories

/**
 * Splits upload groups into requests of at most [maxItems] items, never splitting a group
 * (a household's members always travel together). A group larger than [maxItems] goes alone.
 * Order is kept: groups appear in the batches in the order given.
 */
internal fun <G> batchBySize(groups: List<G>, maxItems: Int, sizeOf: (G) -> Int): List<List<G>> {
    val batches = mutableListOf<List<G>>()
    var current = mutableListOf<G>()
    var currentSize = 0
    for (group in groups) {
        val size = sizeOf(group)
        if (current.isNotEmpty() && currentSize + size > maxItems) {
            batches += current
            current = mutableListOf()
            currentSize = 0
        }
        current += group
        currentSize += size
    }
    if (current.isNotEmpty()) batches += current
    return batches
}
