package com.lashla.chillitest.core.domain

object SearchLimits {
    const val MAX_QUERY_LENGTH = 50

    fun limitQuery(query: String): String {
        val limited = query.take(MAX_QUERY_LENGTH)
        return if (limited.lastOrNull()?.isHighSurrogate() == true) limited.dropLast(1) else limited
    }
}
