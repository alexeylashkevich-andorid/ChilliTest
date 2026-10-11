package com.lashla.chillitest.core.navigation

import android.net.Uri

internal object Routes {
    const val SEARCH = "search"
    const val DETAILS = "gif/{id}"

    fun details(gifId: String): String = "gif/${Uri.encode(gifId)}"
}
