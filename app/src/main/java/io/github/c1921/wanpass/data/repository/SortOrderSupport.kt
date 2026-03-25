package io.github.c1921.wanpass.data.repository

internal const val SortOrderStep = 1024L

internal fun nextCreatedSortOrder(currentMaxSortOrder: Long?): Long =
    (currentMaxSortOrder ?: 0L) + SortOrderStep

internal fun calculateReorderedSortOrder(
    previousSortOrder: Long?,
    nextSortOrder: Long?,
): Long? = when {
    previousSortOrder == null && nextSortOrder == null -> SortOrderStep
    previousSortOrder == null -> nextSortOrder?.plus(SortOrderStep)
    nextSortOrder == null -> previousSortOrder - SortOrderStep
    previousSortOrder <= nextSortOrder -> null
    previousSortOrder - nextSortOrder <= 1L -> null
    else -> nextSortOrder + ((previousSortOrder - nextSortOrder) / 2L)
}

internal fun normalizedSortOrders(itemIdsInOrder: List<String>): Map<String, Long> =
    itemIdsInOrder.mapIndexed { index, itemId ->
        itemId to ((itemIdsInOrder.size - index).toLong() * SortOrderStep)
    }.toMap()
