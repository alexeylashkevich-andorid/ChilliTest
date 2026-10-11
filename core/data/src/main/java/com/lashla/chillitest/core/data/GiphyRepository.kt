package com.lashla.chillitest.core.data

import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.domain.GifException
import com.lashla.chillitest.core.domain.GifPage
import com.lashla.chillitest.core.domain.GifRepository
import com.lashla.chillitest.core.domain.SearchLimits
import java.util.Locale

internal class GiphyRepository(
    private val api: GiphyApi,
    private val apiKey: String,
) : GifRepository {

    override suspend fun getCategories(): List<String> = executeRequest {
        val categories = api.getCategories(apiKey)?.data ?: throw GifException(GifError.UNKNOWN)
        val names = categories.mapNotNull { it?.name?.trim() }
        names.filter { it.isNotEmpty() && it.length <= SearchLimits.MAX_QUERY_LENGTH }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    override suspend fun searchGifs(query: String, offset: Int): GifPage = executeRequest {
        val isTrending = query.isBlank()
        val response = if (isTrending) {
            api.getTrendingGifs(apiKey, offset)
        } else {
            api.searchGifs(apiKey, query, offset)
        }
        val offsetLimit = if (isTrending) TRENDING_OFFSET_LIMIT else SEARCH_OFFSET_LIMIT
        response?.toGifPage(offset, offsetLimit) ?: throw GifException(GifError.UNKNOWN)
    }

    override suspend fun getGifDetails(id: String): Gif = executeRequest(notFoundError = GifError.NOT_FOUND) {
        val response = api.getGifDetails(id, apiKey) ?: throw GifException(GifError.UNKNOWN)
        val gif = response.data ?: throw GifException(GifError.NOT_FOUND)
        gif.toDomainOrNull() ?: throw GifException(GifError.UNKNOWN)
    }

    private suspend fun <T> executeRequest(
        notFoundError: GifError = GifError.SERVICE_UNAVAILABLE,
        block: suspend () -> T,
    ): T {
        if (apiKey.isBlank()) throw GifException(GifError.MISSING_API_KEY)

        try {
            return block()
        } catch (error: Exception) {
            throw error.toRepositoryException(notFoundError)
        }
    }

    private companion object {
        const val TRENDING_OFFSET_LIMIT = 499
        const val SEARCH_OFFSET_LIMIT = 4999
    }
}
