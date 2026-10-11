package com.lashla.chillitest.core.common.image

import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader

val LocalImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("An ImageLoader must be provided to the composition")
}
