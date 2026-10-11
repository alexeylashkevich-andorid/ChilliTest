package com.lashla.chillitest.feature.details

import androidx.lifecycle.SavedStateHandle
import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.domain.GifException
import com.lashla.chillitest.core.domain.GifPage
import com.lashla.chillitest.core.domain.GifRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    @Test
    fun `long similar queries are limited without splitting emoji on load and retry`() = runTest(dispatcher) {
        for ((title, expected) in listOf(
            "a".repeat(60) + " GIF by Studio" to "a".repeat(50),
            "a".repeat(49) + "🐈 GIF by Studio" to "a".repeat(49),
        )) {
            val queries = mutableListOf<String>()
            val repository = object : GifRepository {
                override suspend fun getCategories() = emptyList<String>()
                override suspend fun getGifDetails(id: String) = gif(id, title)
                override suspend fun searchGifs(query: String, offset: Int): GifPage {
                    queries += query
                    if (queries.size == 1) throw GifException(GifError.OFFLINE)
                    return GifPage(listOf(gif("similar")), 1, false)
                }
            }
            val vm = DetailsViewModel(repository, SavedStateHandle(mapOf("id" to "main")))
            advanceUntilIdle()
            vm.loadSimilarGifs()
            advanceUntilIdle()

            assertEquals(listOf(expected, expected), queries)
            assertEquals("similar", vm.state.value.similarGifs.single().id)
            assertNull(vm.state.value.similarError)
        }
    }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleted GIF exposes not found error`() = runTest(dispatcher) {
        val repository = FakeRepository { throw GifException(GifError.NOT_FOUND) }
        val viewModel = DetailsViewModel(repository, SavedStateHandle(mapOf("id" to "gone")))

        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals(GifError.NOT_FOUND, viewModel.state.value.error)
    }

    @Test
    fun `details load from saved navigation id and recover from failure`() = runTest(dispatcher) {
        var failRequest = true
        val repository = FakeRepository { id ->
            if (failRequest) throw GifException(GifError.OFFLINE)
            gif(id)
        }
        val savedState = SavedStateHandle(mapOf("id" to "abc"))
        val viewModel = DetailsViewModel(repository, savedState)
        runCurrent()
        assertNotNull(viewModel.state.value.error)

        failRequest = false
        viewModel.retry()
        runCurrent()

        assertEquals("abc", viewModel.state.value.gif!!.id)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `repeated retries during details loading keep one request`() = runTest(dispatcher) {
        var calls = 0
        val repository = FakeRepository { id ->
            calls++
            delay(1000.milliseconds)
            gif(id, title = "")
        }
        val vm = DetailsViewModel(repository, SavedStateHandle(mapOf("id" to "abc")))
        vm.retry()
        runCurrent()
        vm.retry()
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals("abc", vm.state.value.gif?.id)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `loading is visible until the repository responds`() = runTest(dispatcher) {
        val repository = FakeRepository { id ->
            delay(1000.milliseconds)
            gif(id)
        }
        val viewModel = DetailsViewModel(repository, SavedStateHandle(mapOf("id" to "abc")))
        runCurrent()

        assertTrue(viewModel.state.value.loading)
        assertNull(viewModel.state.value.gif)

        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `similar results exclude current GIF and duplicates and retry independently`() = runTest(dispatcher) {
        var fail = true
        var detailCalls = 0
        val repository = object : GifRepository {
            override suspend fun getCategories() = emptyList<String>()
            override suspend fun getGifDetails(id: String): Gif {
                detailCalls++
                return gif(id, "Swimming Cat GIF by Studio")
            }
            override suspend fun searchGifs(query: String, offset: Int): GifPage {
                assertEquals("Swimming Cat", query)
                assertEquals(0, offset)
                if (fail) throw GifException(GifError.OFFLINE)
                return GifPage(listOf(gif("main"), gif("other"), gif("other")) + List(10) { gif("item$it") }, 24, true)
            }
        }
        val vm = DetailsViewModel(repository, SavedStateHandle(mapOf("id" to "main")))
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(GifError.OFFLINE, vm.state.value.similarError)
        fail = false
        vm.loadSimilarGifs()
        advanceUntilIdle()
        assertEquals(1, detailCalls)
        assertEquals(6, vm.state.value.similarGifs.size)
        assertFalse(vm.state.value.similarGifs.any { it.id == "main" })
        assertEquals(6, vm.state.value.similarGifs.map { it.id }.distinct().size)
        assertNull(vm.state.value.similarError)
    }

    @Test
    fun `repeated retries during similar loading keep one request`() = runTest(dispatcher) {
        var searches = 0
        var details = 0
        val repository = object : GifRepository {
            override suspend fun getCategories() = emptyList<String>()
            override suspend fun getGifDetails(id: String): Gif {
                details++
                return gif(id)
            }
            override suspend fun searchGifs(query: String, offset: Int): GifPage {
                val call = ++searches
                delay(1000.milliseconds)
                return GifPage(listOf(gif("similar-$call")), 1, false)
            }
        }
        val vm = DetailsViewModel(repository, SavedStateHandle(mapOf("id" to "main")))
        runCurrent()
        assertTrue(vm.state.value.similarLoading)

        vm.retry()
        vm.loadSimilarGifs()
        vm.loadSimilarGifs()
        advanceUntilIdle()

        assertEquals(1, details)
        assertEquals(1, searches)
        assertEquals(listOf("similar-1"), vm.state.value.similarGifs.map { it.id })
        assertFalse(vm.state.value.similarLoading)
        assertNull(vm.state.value.similarError)
    }

    @Test
    fun `blank title does not request trending as similar GIFs`() = runTest(dispatcher) {
        val vm = DetailsViewModel(FakeRepository { gif(it, "") }, SavedStateHandle(mapOf("id" to "main")))
        advanceUntilIdle()
        assertTrue(vm.state.value.similarGifs.isEmpty())
        assertNull(vm.state.value.similarError)
        assertFalse(vm.state.value.similarLoading)
    }

    @Test
    fun `missing or blank restored id shows not found without requesting details`() = runTest(dispatcher) {
        val repository = FakeRepository { error("Invalid id must not reach the repository") }
        for (arguments in listOf(emptyMap(), mapOf("id" to ""), mapOf("id" to " "))) {
            val viewModel = DetailsViewModel(repository, SavedStateHandle(arguments))
            runCurrent()

            assertFalse(viewModel.state.value.loading)
            assertEquals(GifError.NOT_FOUND, viewModel.state.value.error)
            assertNull(viewModel.state.value.gif)
        }
    }

    @Test
    fun `new viewmodel reloads details using only the restored id`() = runTest(dispatcher) {
        val requests = mutableListOf<String>()
        val repository = FakeRepository { id ->
            requests += id
            delay(1000.milliseconds)
            gif(id, title = "")
        }
        val original = DetailsViewModel(repository, SavedStateHandle(mapOf("id" to "abc")))
        advanceUntilIdle()
        assertNotNull(original.state.value.gif)

        val restored = DetailsViewModel(repository, SavedStateHandle(mapOf("id" to "abc")))
        runCurrent()
        assertTrue(restored.state.value.loading)
        assertNull(restored.state.value.gif)
        advanceUntilIdle()

        assertEquals(listOf("abc", "abc"), requests)
        assertEquals("abc", restored.state.value.gif?.id)
        assertFalse(restored.state.value.loading)
        assertNull(restored.state.value.error)
    }

    private class FakeRepository(private val loadDetails: suspend (String) -> Gif) : GifRepository {
        override suspend fun getCategories(): List<String> = error("Not used")
        override suspend fun searchGifs(query: String, offset: Int): GifPage = error("Not used")
        override suspend fun getGifDetails(id: String): Gif = loadDetails(id)
    }

    private fun gif(id: String, title: String = "Cat") =
        Gif(id, title, "preview.gif", "original.gif", 1f, "source", "creator")
}
