package com.lashla.chillitest.feature.details

import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError

internal data class DetailsState(
    val gif: Gif? = null,
    val loading: Boolean = true,
    val error: GifError? = null,
    val similarGifs: List<Gif> = emptyList(),
    val similarLoading: Boolean = false,
    val similarError: GifError? = null
)
