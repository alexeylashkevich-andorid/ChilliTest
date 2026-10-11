package com.lashla.chillitest.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.domain.GifException
import com.lashla.chillitest.core.domain.GifRepository
import com.lashla.chillitest.core.domain.SearchLimits.limitQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class DetailsViewModel @Inject constructor(
    private val repository: GifRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val gifId = savedStateHandle.get<String>("id")?.takeIf { it.isNotBlank() }
    private val _state = MutableStateFlow(DetailsState())
    val state = _state.asStateFlow()

    init {
        loadDetails()
    }

    fun retry() {
        if (state.value.loading || state.value.similarLoading) return
        loadDetails()
    }

    private fun loadDetails() {
        val id = gifId
        if (id == null) {
            _state.value = DetailsState(loading = false, error = GifError.NOT_FOUND)
            return
        }
        _state.value = DetailsState()

        viewModelScope.launch {
            try {
                val gif = repository.getGifDetails(id)
                _state.update { it.copy(gif = gif, loading = false) }
                loadSimilarGifs()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = (error as? GifException)?.error ?: GifError.UNKNOWN,
                    )
                }
            }
        }
    }

    fun loadSimilarGifs() {
        val gif = state.value.gif ?: return
        val query = limitQuery(gif.title.substringBeforeLast(" GIF", gif.title).trim())
        if (state.value.similarLoading || query.isBlank()) return
        _state.update { it.copy(similarLoading = true, similarError = null) }
        viewModelScope.launch {
            try {
                val results = repository.searchGifs(query, 0).items
                    .filter { it.id != gif.id }
                    .distinctBy { it.id }
                    .take(SIMILAR_GIF_LIMIT)
                _state.update { it.copy(similarGifs = results, similarLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        similarLoading = false,
                        similarError = (error as? GifException)?.error ?: GifError.UNKNOWN,
                    )
                }
            }
        }
    }

    private companion object {
        const val SIMILAR_GIF_LIMIT = 6
    }
}
