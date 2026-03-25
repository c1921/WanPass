package io.github.c1921.wanpass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeListItemKeyTest {
    @Test
    fun `same item id uses different keys across sections`() {
        val recentKey = homeListItemKey(section = "recent", itemId = "item-1")
        val allKey = homeListItemKey(section = "all", itemId = "item-1")

        assertNotEquals(recentKey, allKey)
    }

    @Test
    fun `same section and item id produce stable key`() {
        val first = homeListItemKey(section = "recent", itemId = "item-1")
        val second = homeListItemKey(section = "recent", itemId = "item-1")

        assertEquals(first, second)
    }
}
