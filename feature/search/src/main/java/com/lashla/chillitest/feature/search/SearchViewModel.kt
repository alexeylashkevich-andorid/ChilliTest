package com.lashla.chillitest.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.domain.GifException
import com.lashla.chillitest.core.domain.GifPage
import com.lashla.chillitest.core.domain.GifRepository
import com.lashla.chillitest.core.domain.SearchLimits.limitQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
internal class SearchViewModel @Inject constructor(
    private val repository: GifRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SearchState(query = limitQuery(savedStateHandle[QUERY_KEY] ?: "")),
    )
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    private var nextPageOffset = 0

    init {
        savedStateHandle[QUERY_KEY] = state.value.query
        loadFirstPage()
        loadCategories()
    }

    @Suppress("SwallowedException")
    private fun loadCategories() {
        if (state.value.categoriesLoading) return
        _state.update { it.copy(categoriesLoading = true, categoriesError = false) }
        viewModelScope.launch {
            try {
                val categories = repository.getCategories()
                ensureActive()
                _state.update { it.copy(categories = categories, categoriesLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                _state.update { it.copy(categoriesLoading = false, categoriesError = true) }
            }
        }
    }

    fun retryCategories() {
        if (state.value.categoriesError) loadCategories()
    }

    fun setQuery(query: String) {
        val newQuery = limitQuery(query)
        if (newQuery == state.value.query) return

        savedStateHandle[QUERY_KEY] = newQuery
        _state.update { it.copy(query = newQuery) }
        loadFirstPage(debounceMillis = SEARCH_DEBOUNCE_MILLIS)
    }

    fun retry() {
        if (state.value.categoriesError) loadCategories()
        loadFirstPage()
    }

    fun loadNextPage() {
        if (!state.value.canLoadMore) return

        _state.update { it.copy(loadingMore = true) }
        searchJob = viewModelScope.launch {
            fetchPage(PageLoad.NEXT)
        }
    }

    fun retryNextPage() {
        if (state.value.categoriesError) loadCategories()
        _state.update { it.copy(pageError = null) }
        loadNextPage()
    }

    private fun loadFirstPage(debounceMillis: Long = 0) {
        searchJob?.cancel()
        nextPageOffset = 0

        _state.update { current ->
            current.copy(
                gifs = emptyList(),
                loading = true,
                loadingMore = false,
                error = null,
                pageError = null,
                hasMore = true,
                paginationPaused = false,
            )
        }

        searchJob = viewModelScope.launch {
            delay(debounceMillis.milliseconds)
            fetchPage(PageLoad.FIRST)
        }
    }

    private suspend fun fetchPage(load: PageLoad) {
        try {
            val page = repository.searchGifs(state.value.query, nextPageOffset)
            currentCoroutineContext().ensureActive()
            updateResults(page, load)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            updateError(error, load)
        }
    }

    private fun updateResults(page: GifPage, load: PageLoad) {
        nextPageOffset = page.nextOffset
        _state.update { current ->
            val results = when (load) {
                PageLoad.FIRST -> page.items
                PageLoad.NEXT -> current.gifs + page.items
            }.distinctBy(Gif::id)
            val previousCount = if (load == PageLoad.FIRST) 0 else current.gifs.size
            val addedItems = results.size - previousCount
            current.copy(
                gifs = results,
                loading = false,
                loadingMore = false,
                hasMore = page.hasMore,
                paginationPaused = page.hasMore && addedItems == 0,
            )
        }
    }

    private fun updateError(error: Exception, load: PageLoad) {
        val reason = (error as? GifException)?.error ?: GifError.UNKNOWN
        _state.update { current ->
            when (load) {
                PageLoad.FIRST -> current.copy(loading = false, error = reason)
                PageLoad.NEXT -> current.copy(loadingMore = false, pageError = reason)
            }
        }
    }

    private enum class PageLoad { FIRST, NEXT }

    private companion object {
        const val QUERY_KEY = "query"
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
