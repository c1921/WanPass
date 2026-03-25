package io.github.c1921.wanpass.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SortOrderSupportTest {
    @Test
    fun `new items should be inserted above current max sort order`() {
        assertEquals(3_072L, nextCreatedSortOrder(2_048L))
    }

    @Test
    fun `reordered item should land halfway between neighbors when gap exists`() {
        assertEquals(1_536L, calculateReorderedSortOrder(previousSortOrder = 2_048L, nextSortOrder = 1_024L))
    }

    @Test
    fun `reordered item should extend above first item when moved to top`() {
        assertEquals(3_072L, calculateReorderedSortOrder(previousSortOrder = null, nextSortOrder = 2_048L))
    }

    @Test
    fun `reordered item should extend below last item when moved to bottom`() {
        assertEquals(0L, calculateReorderedSortOrder(previousSortOrder = 1_024L, nextSortOrder = null))
    }

    @Test
    fun `reordered item should request normalization when no gap remains`() {
        assertNull(calculateReorderedSortOrder(previousSortOrder = 1_025L, nextSortOrder = 1_024L))
    }

    @Test
    fun `normalized sort orders should keep list in descending display order`() {
        assertEquals(
            mapOf(
                "first" to 3_072L,
                "second" to 2_048L,
                "third" to 1_024L,
            ),
            normalizedSortOrders(listOf("first", "second", "third"))
        )
    }
}
