package com.lashla.chillitest.core.data.dto

import com.google.gson.annotations.SerializedName

internal data class ImagesDto(
    @SerializedName("fixed_width") val fixedWidth: ImageDto?,
    @SerializedName("original") val original: ImageDto?,
)
