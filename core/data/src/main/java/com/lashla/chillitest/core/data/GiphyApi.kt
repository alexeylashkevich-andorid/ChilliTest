package com.lashla.chillitest.core.data

import com.lashla.chillitest.core.data.dto.CategoriesDto
import com.lashla.chillitest.core.data.dto.DetailDto
import com.lashla.chillitest.core.data.dto.PageDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal interface GiphyApi {
    @GET("gifs/categories")
    suspend fun getCategories(@Query("api_key") key: String): CategoriesDto?

    @GET("gifs/trending")
    suspend fun getTrendingGifs(
        @Query("api_key") key: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("rating") rating: String = CONTENT_RATING,
    ): PageDto?

    @GET("gifs/search")
    suspend fun searchGifs(
        @Query("api_key") key: String,
        @Query("q") query: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("rating") rating: String = CONTENT_RATING,
    ): PageDto?

    @GET("gifs/{id}")
    suspend fun getGifDetails(
        @Path("id") id: String,
        @Query("api_key") key: String,
    ): DetailDto?

    companion object {
        const val PAGE_SIZE = 24
        const val CONTENT_RATING = "pg-13"
    }
}
