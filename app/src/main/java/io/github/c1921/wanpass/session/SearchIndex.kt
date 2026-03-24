package io.github.c1921.wanpass.session

import io.github.c1921.wanpass.core.SearchNormalizer
import io.github.c1921.wanpass.domain.model.SearchEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SearchIndex @Inject constructor() {
    private val _entries = MutableStateFlow<List<SearchEntry>>(emptyList())
    val entries: StateFlow<List<SearchEntry>> = _entries.asStateFlow()

    fun replace(entries: List<SearchEntry>) {
        _entries.value = entries
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun matches(query: String): Set<String> {
        val normalized = SearchNormalizer.normalize(query)
        if (normalized.isBlank()) return _entries.value.mapTo(mutableSetOf()) { it.id }
        return _entries.value.filter { it.normalizedText.contains(normalized) }.mapTo(mutableSetOf()) { it.id }
    }
}
