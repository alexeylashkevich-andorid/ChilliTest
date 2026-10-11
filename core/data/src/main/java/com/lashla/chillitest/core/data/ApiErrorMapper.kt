package com.lashla.chillitest.core.data

import com.google.gson.stream.MalformedJsonException
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.domain.GifException
import java.io.EOFException
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

internal fun Exception.toRepositoryException(notFoundError: GifError): Exception {
    if (this is CancellationException || this is GifException) return this

    val reason = when (this) {
        is MalformedJsonException, is EOFException -> GifError.UNKNOWN
        is IOException -> GifError.OFFLINE
        is HttpException -> when (code()) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> GifError.UNAUTHORIZED
            HTTP_NOT_FOUND -> notFoundError
            HTTP_TOO_MANY_REQUESTS -> GifError.RATE_LIMITED
            else -> GifError.SERVICE_UNAVAILABLE
        }
        else -> GifError.UNKNOWN
    }
    return GifException(reason, this)
}

private const val HTTP_TOO_MANY_REQUESTS = 429
