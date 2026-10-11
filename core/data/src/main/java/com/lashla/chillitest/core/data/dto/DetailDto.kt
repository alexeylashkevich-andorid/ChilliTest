package com.lashla.chillitest.core.data.dto

import com.google.gson.annotations.SerializedName

internal data class DetailDto(
    @SerializedName("data") val data: GifDto?,
)
