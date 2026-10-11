package com.lashla.chillitest.core.common.image

import android.content.Context
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy

fun createGifImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .components { add(ImageDecoderDecoder.Factory()) }
    .memoryCachePolicy(CachePolicy.DISABLED)
    .diskCachePolicy(CachePolicy.DISABLED)
    .build()
