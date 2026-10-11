package com.lashla.chillitest.core.domain

data class GifPage(
    val items: List<Gif>,
    val nextOffset: Int,
    val hasMore: Boolean,
)
