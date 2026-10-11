package com.lashla.chillitest.core.data.dto

import com.google.gson.annotations.SerializedName

internal data class PaginationDto(
    @SerializedName("offset") val offset: Int?,
    @SerializedName("count") val count: Int?,
    @SerializedName("total_count") val total: Int?,
)
