package io.github.c1921.wanpass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeEmptyStateTest {
    @Test
    fun `should expose restore entry only when vault is truly empty`() {
        val result = resolveHomeEmptyState(
            totalItemCount = 0,
            visibleItemCount = 0,
        )

        assertEquals(HomeEmptyState.EmptyVault, result)
    }

    @Test
    fun `should show search empty state when records exist but filter matches nothing`() {
        val result = resolveHomeEmptyState(
            totalItemCount = 2,
            visibleItemCount = 0,
        )

        assertEquals(HomeEmptyState.NoSearchResults, result)
    }

    @Test
    fun `should show normal list state when visible items exist`() {
        val result = resolveHomeEmptyState(
            totalItemCount = 2,
            visibleItemCount = 1,
        )

        assertEquals(HomeEmptyState.None, result)
    }
}
