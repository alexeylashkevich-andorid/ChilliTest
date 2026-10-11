package com.lashla.chillitest.core.data

import com.lashla.chillitest.core.data.di.NetworkModule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLoggingTest {
    @Test
    fun `release client has no logging interceptor while debug client does`() {
        assertTrue(NetworkModule.provideOkHttpClient(loggingEnabled = false).interceptors.isEmpty())
        assertEquals(1, NetworkModule.provideOkHttpClient(loggingEnabled = true).interceptors.size)
    }

    @Test
    fun `logs request status and response body without exposing API key`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("response-body"))
            val messages = mutableListOf<String>()
            val logging = NetworkModule.createLoggingInterceptor { messages.add(it) }
            val client = OkHttpClient.Builder().addInterceptor(logging).build()
            val request = Request.Builder()
                .url(server.url("/v1/gifs/trending?api_key=secret-key&limit=24"))
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }

            assertTrue(messages.any { it.startsWith("--> GET") && it.contains("api_key=REDACTED&limit=24") })
            assertTrue(messages.any { it.startsWith("<-- 200") && it.contains("api_key=REDACTED&limit=24") })
            assertFalse(messages.any { it.contains("secret-key") })
            assertTrue(messages.any { it.contains("response-body") })
            assertTrue(server.takeRequest().path!!.contains("api_key=secret-key"))
        }
    }
}
