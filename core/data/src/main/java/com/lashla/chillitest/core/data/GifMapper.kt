package com.lashla.chillitest.core.data

import com.lashla.chillitest.core.data.dto.GifDto
import com.lashla.chillitest.core.data.dto.PageDto
import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.domain.GifException
import com.lashla.chillitest.core.domain.GifPage

internal fun PageDto.toGifPage(requestedOffset: Int, maxOffset: Int): GifPage {
    val gifs = data
    val responseOffset = pagination?.offset
    val returnedCount = pagination?.count
    val totalCount = pagination?.total
    if (gifs == null || responseOffset == null || returnedCount == null) {
        throw GifException(GifError.UNKNOWN)
    }

    val negativePagination = responseOffset < 0 || returnedCount < 0
    val negativeTotal = totalCount != null && totalCount < 0
    if (negativePagination || negativeTotal) {
        throw GifException(GifError.UNKNOWN)
    }

    val nextOffset = Math.addExact(responseOffset, returnedCount)
    val hasMore = when {
        gifs.isEmpty() -> false
        returnedCount == 0 -> false
        nextOffset <= requestedOffset -> false
        nextOffset > maxOffset -> false
        totalCount != null -> nextOffset < totalCount
        else -> true
    }

    return GifPage(
        items = gifs.mapNotNull { it?.toDomainOrNull() },
        nextOffset = nextOffset,
        hasMore = hasMore,
    )
}

@Suppress("ReturnCount")
internal fun GifDto.toDomainOrNull(): Gif? {
    val validId = id?.takeIf { it.isNotBlank() } ?: return null
    val preview = images?.fixedWidth ?: return null
    val previewUrl = preview.url?.takeIf { it.isNotBlank() } ?: return null
    val originalUrl = images.original?.url?.takeIf { it.isNotBlank() } ?: return null
    val width = preview.width?.toFloatOrNull()
        ?.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_DIMENSION
    val height = preview.height?.toFloatOrNull()
        ?.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_DIMENSION

    return Gif(
        id = validId,
        title = title.orEmpty(),
        previewUrl = previewUrl,
        originalUrl = originalUrl,
        aspectRatio = (width / height).coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO),
        sourceUrl = url.orEmpty(),
        creator = username.orEmpty(),
    )
}

private const val DEFAULT_DIMENSION = 200f
private const val MIN_ASPECT_RATIO = 0.45f
private const val MAX_ASPECT_RATIO = 2.2f
