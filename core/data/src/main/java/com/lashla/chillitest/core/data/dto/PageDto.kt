package com.lashla.chillitest.core.data.dto

import com.google.gson.annotations.SerializedName

internal data class PageDto(
    @SerializedName("data") val data: List<GifDto?>?,
    @SerializedName("pagination") val pagination: PaginationDto?,
)
