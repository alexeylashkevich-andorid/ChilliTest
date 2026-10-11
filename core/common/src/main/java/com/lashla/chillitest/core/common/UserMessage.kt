package com.lashla.chillitest.core.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lashla.chillitest.core.domain.GifError

@Composable
fun GifError.userMessage(): String = stringResource(
    when (this) {
        GifError.MISSING_API_KEY -> R.string.error_missing_key
        GifError.OFFLINE -> R.string.error_offline
        GifError.UNAUTHORIZED -> R.string.error_unauthorized
        GifError.RATE_LIMITED -> R.string.error_rate_limited
        GifError.NOT_FOUND -> R.string.error_not_found
        GifError.SERVICE_UNAVAILABLE -> R.string.error_service_unavailable
        GifError.UNKNOWN -> R.string.error_unknown
    },
)
