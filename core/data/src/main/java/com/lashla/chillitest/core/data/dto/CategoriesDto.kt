package com.lashla.chillitest.core.data.dto

import com.google.gson.annotations.SerializedName

internal data class CategoriesDto(
    @SerializedName("data") val data: List<CategoryDto?>?,
)
