package com.lashla.chillitest.core.data

import com.lashla.chillitest.core.data.*
import com.lashla.chillitest.core.domain.GifException
import com.lashla.chillitest.core.domain.GifError
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GiphyRepositoryTest {
    @Test fun `null or absent response body is a controlled failure for every endpoint`() = runTest {
        val requests: List<suspend () -> Any> = listOf(
            { repo.searchGifs("", 0) }, { repo.getCategories() }, { repo.getGifDetails("abc") },
        )
        for (request in requests) {
            for (response in listOf(
                MockResponse().setBody("null"),
                MockResponse().setResponseCode(204),
                MockResponse().setBody(""),
            )) {
                server.enqueue(response)
                try {
                    request()
                    fail("Expected a domain failure for an absent body")
                } catch (error: GifException) {
                    assertEquals(GifError.UNKNOWN, error.error)
                }
            }
        }
    }

    @Test fun `missing total permits pagination until an empty page`() = runTest {
        for (body in listOf(
            response().replace(",\"total_count\":2", ""),
            response().replace("\"total_count\":2", "\"total_count\":null"),
        )) {
            server.enqueue(MockResponse().setBody(body))
            val page = repo.searchGifs("", 0)
            assertEquals(1, page.nextOffset)
            assertTrue(page.hasMore)
        }
        server.enqueue(MockResponse().setBody("""{"data":[],"pagination":{"offset":1,"count":0}}"""))
        assertFalse(repo.searchGifs("", 1).hasMore)
    }

    @Test fun `null data and incomplete pagination are failures rather than empty results`() = runTest {
        for (body in listOf(
            """{"data":null,"pagination":{"offset":0,"count":0}}""",
            """{"data":[],"pagination":null}""",
            """{"data":[],"pagination":{"offset":0}}""",
            """{"data":[],"pagination":{"count":0}}""",
            response(offset = -1), response(count = -1), response(total = -1),
            response(offset = Int.MAX_VALUE, count = 1),
        )) {
            server.enqueue(MockResponse().setBody(body))
            try {
                repo.searchGifs("cats", 0)
                fail("Expected invalid-response failure for $body")
            } catch (error: GifException) {
                assertEquals(GifError.UNKNOWN, error.error)
            }
        }
    }

    @Test fun `null details data is unavailable while malformed GIF is invalid`() = runTest {
        for ((body, expected) in listOf(
            """{"data":null}""" to GifError.NOT_FOUND,
            """{"data":{}}""" to GifError.UNKNOWN,
        )) {
            server.enqueue(MockResponse().setBody(body))
            try {
                repo.getGifDetails("abc")
                fail("Expected details failure")
            } catch (error: GifException) {
                assertEquals(expected, error.error)
            }
        }
    }

    @Test fun `last trending page respects server total instead of hardcoded result count`() = runTest {
        server.enqueue(MockResponse().setBody(response(120, 20, 140)))
        val page = repo.searchGifs("", 120)
        assertEquals(140, page.nextOffset)
        assertFalse(page.hasMore)
    }

    @Test fun `null category entries do not discard valid topics`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[null,{"name":"Animals"}]}"""))
        assertEquals(listOf("Animals"), repo.getCategories())
    }

    @Test fun `blank required GIF fields are skipped without changing server offsets`() = runTest {
        for (body in listOf(
            response().replace("\"id\":\"abc\"", "\"id\":\" \""),
            response().replace("https://media.giphy.com/preview.gif", ""),
            response().replace("https://media.giphy.com/original.gif", ""),
        )) {
            server.enqueue(MockResponse().setBody(body))
            val page = repo.searchGifs("cats", 0)
            assertTrue(page.items.isEmpty())
            assertEquals(1, page.nextOffset)
            assertTrue(page.hasMore)
        }
    }

    @Test fun `mixed invalid GIFs preserve valid entries and server pagination`() = runTest {
        val body = response(offset = 24, count = 5, total = 60)
            .replace("\"data\":[", "\"data\":[null,{}, {\"id\":\"broken\",\"images\":{}}, {\"id\":\"no-images\"},")
        server.enqueue(MockResponse().setBody(body))
        val page = repo.searchGifs("cats", 24)
        assertEquals(listOf("abc"), page.items.map { it.id })
        assertEquals(29, page.nextOffset)
        assertTrue(page.hasMore)
    }

    @Test fun `categories exceeding query limit are excluded without truncation`() = runTest {
        val accepted = "a".repeat(50)
        val rejected = "a".repeat(51)
        server.enqueue(MockResponse().setBody("""{"data":[{"name":"$accepted"},{"name":"$rejected"},{"name":"Animals"}]}"""))
        assertEquals(listOf(accepted, "Animals"), repo.getCategories())
    }

    @Test fun `only details 404 is mapped to missing GIF`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        try { repo.getGifDetails("deleted"); fail("Expected missing GIF") }
        catch (error: GifException) { assertEquals(GifError.NOT_FOUND, error.error) }
        server.enqueue(MockResponse().setResponseCode(404))
        try { repo.searchGifs("cats", 0); fail("Expected service failure") }
        catch (error: GifException) { assertEquals(GifError.SERVICE_UNAVAILABLE, error.error) }
    }

    @Test fun `broken JSON is not reported as no internet`() = runTest {
        server.enqueue(MockResponse().setBody("{\"data\":["))
        try { repo.getCategories(); fail("Expected invalid JSON error") }
        catch (error: GifException) { assertEquals(GifError.UNKNOWN, error.error) }
    }

    @Test fun `whitespace query uses trending and unicode query is preserved`() = runTest {
        server.enqueue(MockResponse().setBody(response()))
        repo.searchGifs("  \n\t", 0)
        assertEquals("/v1/gifs/trending", server.takeRequest().requestUrl!!.encodedPath)
        server.enqueue(MockResponse().setBody(response()))
        val query = "🐈 café + #猫 & dogs"
        repo.searchGifs(query, 0)
        assertEquals(query, server.takeRequest().requestUrl!!.queryParameter("q"))
    }

    @Test fun `categories use authenticated endpoint and keep unique nonblank names`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"name":"Animals"},{"name":" Food & Drink "},{"name":"animals"},{"name":" "},{}]}"""))
        assertEquals(listOf("Animals", "Food & Drink"), repo.getCategories())
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/gifs/categories", url.encodedPath)
        assertEquals("test-key", url.queryParameter("api_key"))
    }

    @Test fun `empty categories are valid but missing data and HTTP failures are errors`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        assertTrue(repo.getCategories().isEmpty())
        server.enqueue(MockResponse().setBody("{}"))
        try { repo.getCategories(); fail("Expected invalid response error") }
        catch (error: GifException) { assertEquals(GifError.UNKNOWN, error.error) }
        server.enqueue(MockResponse().setResponseCode(429))
        try { repo.getCategories(); fail("Expected rate limit error") }
        catch (error: GifException) { assertEquals(GifError.RATE_LIMITED, error.error) }
    }

    private lateinit var server: MockWebServer
    private lateinit var repo: GiphyRepository
    @Before fun setup() {
        server = MockWebServer(); server.start()
        repo = GiphyRepository(Retrofit.Builder().baseUrl(server.url("/v1/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(GiphyApi::class.java), "test-key")
    }
    @After fun tearDown() { server.shutdown() }
    private fun response(offset: Int = 0, count: Int = 1, total: Int = 2) = """
        {"data":[{"id":"abc","title":"Cat GIF","url":"https://giphy.com/gifs/abc","username":"artist",
        "images":{"fixed_width":{"url":"https://media.giphy.com/preview.gif","width":"200","height":"300"},
        "original":{"url":"https://media.giphy.com/original.gif","width":"400","height":"600"}}}],
        "pagination":{"offset":$offset,"count":$count,"total_count":$total}}
    """.trimIndent()
    @Test fun `blank search routes to trending with animated rendition`() = runTest {
        server.enqueue(MockResponse().setBody(response()))
        val page = repo.searchGifs("", 0); val request = server.takeRequest()
        assertEquals("/v1/gifs/trending", request.requestUrl!!.encodedPath)
        assertEquals("test-key", request.requestUrl!!.queryParameter("api_key"))
        assertEquals("https://media.giphy.com/preview.gif", page.items.single().previewUrl)
        assertEquals(2f / 3, page.items.single().aspectRatio, .001f)
        assertEquals(1, page.nextOffset); assertTrue(page.hasMore)
    }
    @Test fun `search encodes query and honors server pagination`() = runTest {
        server.enqueue(MockResponse().setBody(response(24, 1, 25)))
        val page = repo.searchGifs("cats & dogs", 24); val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/gifs/search", url.encodedPath)
        assertEquals("cats & dogs", url.queryParameter("q")); assertEquals("24", url.queryParameter("offset"))
        assertEquals(25, page.nextOffset); assertFalse(page.hasMore)
    }
    @Test fun `trending stops before API offset ceiling`() = runTest {
        server.enqueue(MockResponse().setBody(response(480, 24, 1000)))
        assertFalse(repo.searchGifs("", 480).hasMore)
    }
    @Test fun `HTTP errors become domain failures`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        try { repo.searchGifs("cats", 0); fail("Expected HTTP error") }
        catch (error: GifException) { assertEquals(GifError.RATE_LIMITED, error.error) }
    }
    @Test fun `missing key fails before network request`() = runTest {
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(GiphyApi::class.java)
        try { GiphyRepository(api, "").searchGifs("", 0); fail("Expected missing key") }
        catch (error: GifException) {
            assertEquals(GifError.MISSING_API_KEY, error.error)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `authorization and server failures expose domain reasons`() = runTest {
        for ((status, expected) in listOf(
            401 to GifError.UNAUTHORIZED,
            403 to GifError.UNAUTHORIZED,
            503 to GifError.SERVICE_UNAVAILABLE,
        )) {
            server.enqueue(MockResponse().setResponseCode(status))
            try {
                repo.searchGifs("cats", 0)
                fail("Expected failure for HTTP $status")
            } catch (error: GifException) {
                assertEquals(expected, error.error)
            }
        }
    }

    @Test fun `connection failure becomes offline domain error`() = runTest {
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(GiphyApi::class.java)
        val offlineApi = object : GiphyApi by api {
            override suspend fun getGifDetails(id: String, key: String): com.lashla.chillitest.core.data.dto.DetailDto {
                throw java.io.IOException("No connection")
            }
        }
        try {
            GiphyRepository(offlineApi, "test-key").getGifDetails("abc")
            fail("Expected offline failure")
        } catch (error: GifException) {
            assertEquals(GifError.OFFLINE, error.error)
        }
    }

    @Test fun `cancellation is never converted to a domain failure`() = runTest {
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(GiphyApi::class.java)
        val cancelledApi = object : GiphyApi by api {
            override suspend fun getGifDetails(id: String, key: String): com.lashla.chillitest.core.data.dto.DetailDto {
                throw kotlinx.coroutines.CancellationException("Cancelled search")
            }
        }
        try {
            GiphyRepository(cancelledApi, "test-key").getGifDetails("abc")
            fail("Expected cancellation")
        } catch (error: kotlinx.coroutines.CancellationException) {
            assertEquals("Cancelled search", error.message)
        }
    }

    @Test fun `non advancing pagination stops instead of requesting the same page forever`() = runTest {
        server.enqueue(MockResponse().setBody(response(offset = 0, count = 24, total = 100)))
        assertFalse(repo.searchGifs("cats", 24).hasMore)
    }

    @Test fun `empty data stops pagination even if metadata claims more items`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[],"pagination":{"offset":0,"count":24,"total_count":100}}"""))
        assertFalse(repo.searchGifs("cats", 0).hasMore)
    }

    @Test fun `details maps original animation and optional metadata`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{
            "id":"cat","images":{
                "fixed_width":{"url":"https://example.com/small.gif","width":"200","height":"300"},
                "original":{"url":"https://example.com/full.gif","width":"400","height":"600"}
            }}}
        """))
        val gif = repo.getGifDetails("cat")
        val request = server.takeRequest()

        assertEquals("/v1/gifs/cat", request.requestUrl!!.encodedPath)
        assertEquals("test-key", request.requestUrl!!.queryParameter("api_key"))
        assertEquals("https://example.com/full.gif", gif.originalUrl)
        assertEquals("", gif.title)
    }

    @Test fun `zero count and search offset ceiling stop pagination`() = runTest {
        server.enqueue(MockResponse().setBody(response(0, 0, 100)))
        assertFalse(repo.searchGifs("cats", 0).hasMore)
        server.enqueue(MockResponse().setBody(response(4992, 24, 10000)))
        assertFalse(repo.searchGifs("cats", 4992).hasMore)
    }

    @Test fun `invalid response becomes a controlled domain failure`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        try {
            repo.searchGifs("cats", 0)
            fail("Expected invalid-response failure")
        } catch (error: GifException) {
            assertEquals(GifError.UNKNOWN, error.error)
        }
    }
}
