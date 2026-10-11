package com.lashla.chillitest.core.data

import com.google.gson.stream.MalformedJsonException
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.domain.GifException
import java.io.EOFException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ApiErrorMapperTest {
    @Test fun `cancellation and domain failures keep their identity and cause`() {
        val cause = IOException("Original failure")
        val errors = listOf(
            CancellationException("Cancelled"),
            GifException(GifError.NOT_FOUND, cause),
        )
        for (error in errors) {
            assertSame(error, error.toRepositoryException(GifError.SERVICE_UNAVAILABLE))
        }
    }

    @Test fun `invalid payloads stay distinct from connection failures and retain their cause`() {
        val failures = listOf(
            MalformedJsonException("Invalid JSON") to GifError.UNKNOWN,
            EOFException("Truncated response") to GifError.UNKNOWN,
            IOException("No connection") to GifError.OFFLINE,
            IllegalStateException("Unexpected response") to GifError.UNKNOWN,
        )
        for ((error, reason) in failures) {
            val mapped = error.toRepositoryException(GifError.SERVICE_UNAVAILABLE) as GifException
            assertEquals(reason, mapped.error)
            assertSame(error, mapped.cause)
        }
    }
}
