package com.lashla.chillitest.core.data.dto

import com.google.gson.annotations.SerializedName

internal data class ImageDto(
    @SerializedName("url") val url: String?,
    @SerializedName("width") val width: String?,
    @SerializedName("height") val height: String?,
)
