package com.lashla.chillitest.core.domain

interface GifRepository {
    suspend fun getCategories(): List<String>
    suspend fun searchGifs(query: String, offset: Int): GifPage
    suspend fun getGifDetails(id: String): Gif
}
