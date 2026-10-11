package com.lashla.chillitest.feature.search

import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError

internal data class SearchState(
    val query: String = "",
    val categories: List<String> = emptyList(),
    val categoriesLoading: Boolean = false,
    val categoriesError: Boolean = false,
    val gifs: List<Gif> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: GifError? = null,
    val pageError: GifError? = null,
    val hasMore: Boolean = true,
    val paginationPaused: Boolean = false,
) {
    val canLoadMore: Boolean
        get() = !loading && !loadingMore && hasMore && error == null && pageError == null

    val canAutoLoadMore: Boolean
        get() = canLoadMore && !paginationPaused
}
