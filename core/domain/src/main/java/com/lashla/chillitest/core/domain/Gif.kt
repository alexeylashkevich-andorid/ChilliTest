package com.lashla.chillitest.core.domain

data class Gif(
    val id: String,
    val title: String,
    val previewUrl: String,
    val originalUrl: String,
    val aspectRatio: Float,
    val sourceUrl: String,
    val creator: String,
)
