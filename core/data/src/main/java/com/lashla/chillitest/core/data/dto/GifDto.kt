package com.lashla.chillitest.core.data.dto

import com.google.gson.annotations.SerializedName

internal data class GifDto(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("images") val images: ImagesDto?,
)
