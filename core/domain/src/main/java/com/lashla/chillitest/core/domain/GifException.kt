package com.lashla.chillitest.core.domain

class GifException(
    val error: GifError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
