package com.lashla.chillitest.feature.search

import androidx.lifecycle.SavedStateHandle
import com.lashla.chillitest.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Test
    fun `category retry preserves successful feed and guards duplicate requests`() = runTest(dispatcher) {
        val repository = FakeRepository()
        var categoryCalls = 0
        repository.categoriesHandler = {
            categoryCalls++
            if (categoryCalls == 1) throw GifException(GifError.OFFLINE)
            delay(100)
            listOf("Animals")
        }
        val vm = SearchViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        val feed = vm.state.value.gifs
        vm.retryCategories()
        vm.retryCategories()
        advanceUntilIdle()

        assertEquals(2, categoryCalls)
        assertEquals(1, repository.calls.size)
        assertEquals(feed, vm.state.value.gifs)
        assertEquals(listOf("Animals"), vm.state.value.categories)
        assertFalse(vm.state.value.categoriesError)
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
    fun `duplicate page pauses automatic loading and new content resumes it`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { _, offset ->
            GifPage(listOf(gif(if (offset < 2) "same" else "new")), offset + 1, true)
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf("same"), viewModel.state.value.gifs.map { it.id })
        assertTrue(viewModel.state.value.canLoadMore)
        assertFalse(viewModel.state.value.canAutoLoadMore)
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(0, 1, 2), repository.calls.map { it.second })
        assertEquals(listOf("same", "new"), viewModel.state.value.gifs.map { it.id })
        assertTrue(viewModel.state.value.canAutoLoadMore)
    }

    @Test
    fun `all skipped page allows manual advance and failed advance retries same offset`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { _, offset -> GifPage(emptyList(), offset + 24, true) }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.state.value.paginationPaused)
        repository.searchHandler = { _, _ -> throw GifException(GifError.OFFLINE) }
        viewModel.loadNextPage()
        runCurrent()
        assertNotNull(viewModel.state.value.pageError)
        assertFalse(viewModel.state.value.canAutoLoadMore)
        repository.searchHandler = { _, offset -> GifPage(listOf(gif("valid")), offset + 24, false) }
        viewModel.retryNextPage()
        runCurrent()
        assertEquals(listOf(0, 24, 24), repository.calls.map { it.second })
        assertEquals("valid", viewModel.state.value.gifs.single().id)
        assertFalse(viewModel.state.value.hasMore)
        viewModel.setQuery("new")
        assertFalse(viewModel.state.value.paginationPaused)
    }

    @Test
    fun `query limit never splits an emoji surrogate pair`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val saved = SavedStateHandle()
        val viewModel = SearchViewModel(repository, saved)
        viewModel.setQuery("a".repeat(49) + "🐈")
        advanceUntilIdle()
        assertEquals("a".repeat(49), viewModel.state.value.query)
        assertEquals(viewModel.state.value.query, saved.get<String>("query"))
        assertEquals(viewModel.state.value.query, repository.calls.last().first)
    }

    @Test
    fun `restored query is limited before the first request`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val saved = SavedStateHandle(mapOf("query" to "a".repeat(80)))
        val viewModel = SearchViewModel(repository, saved)
        advanceUntilIdle()
        assertEquals("a".repeat(50), viewModel.state.value.query)
        assertEquals(viewModel.state.value.query, repository.calls.single().first)
        assertEquals(viewModel.state.value.query, saved.get<String>("query"))
    }

    @Test
    fun `clearing viewmodel prevents late category results`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.categoriesHandler = {
            withContext(NonCancellable) { delay(1000) }
            listOf("Late topic")
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        val store = androidx.lifecycle.ViewModelStore()
        store.put("search", viewModel)
        runCurrent()
        store.clear()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.categories.isEmpty())
    }

    @Test
    fun `manual retry recovers from an offline request`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { _, _ -> throw GifException(GifError.OFFLINE) }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        runCurrent()
        assertEquals(1, repository.calls.size)
        repository.searchHandler = { _, _ -> GifPage(listOf(gif("recovered")), 1, false) }
        viewModel.retry()
        advanceUntilIdle()
        assertNull(viewModel.state.value.error)
        assertEquals("recovered", viewModel.state.value.gifs.single().id)
    }

    @Test
    fun `categories survive query changes and do not block GIF loading`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.categoriesHandler = {
            delay(1000)
            listOf("Food & Drink")
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.categoriesLoading)
        viewModel.setQuery("cats")
        advanceUntilIdle()
        assertEquals(listOf("Food & Drink"), viewModel.state.value.categories)
        viewModel.setQuery("Food & Drink")
        advanceUntilIdle()
        assertEquals("Food & Drink" to 0, repository.calls.last())
        assertEquals(listOf("Food & Drink"), viewModel.state.value.categories)
        assertFalse(viewModel.state.value.categoriesLoading)
    }

    @Test
    fun `category failure is independent and retry guards duplicate requests`() = runTest(dispatcher) {
        val repository = FakeRepository()
        var attempts = 0
        repository.categoriesHandler = {
            attempts++
            delay(100)
            if (attempts == 1) throw GifException(GifError.OFFLINE)
            listOf("Animals")
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.categoriesError)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        viewModel.setQuery("cats")
        assertTrue(viewModel.state.value.categoriesError)
        viewModel.retry()
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertFalse(viewModel.state.value.categoriesError)
        assertEquals(listOf("Animals"), viewModel.state.value.categories)
    }

    @Test
    fun `feed retry recovers categories and GIFs together`() = runTest(dispatcher) {
        val repository = FakeRepository()
        var categoryCalls = 0
        var fail = true
        repository.categoriesHandler = {
            categoryCalls++
            if (fail) throw GifException(GifError.OFFLINE)
            listOf("Animals")
        }
        repository.searchHandler = { _, _ ->
            if (fail) throw GifException(GifError.OFFLINE)
            GifPage(listOf(gif("recovered")), 1, false)
        }
        val vm = SearchViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertTrue(vm.state.value.categoriesError)
        assertEquals(GifError.OFFLINE, vm.state.value.error)

        fail = false
        vm.retry()
        advanceUntilIdle()

        assertEquals(listOf("Animals"), vm.state.value.categories)
        assertEquals("recovered", vm.state.value.gifs.single().id)
        assertFalse(vm.state.value.categoriesError)
        assertNull(vm.state.value.error)
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, categoryCalls)
    }

    @Test
    fun `typing is debounced and only final query is requested`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.setQuery("c")
        advanceTimeBy(250)
        viewModel.setQuery("cat")
        advanceTimeBy(399)
        runCurrent()
        assertEquals(listOf("" to 0), repository.calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("cat" to 0, repository.calls.last())
    }

    @Test
    fun `new query cancels in flight work and ignores stale response`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { query, _ ->
            if (query == "old") withContext(NonCancellable) { delay(1000) }
            GifPage(listOf(gif(query)), 1, false)
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.setQuery("old")
        advanceTimeBy(400)
        runCurrent()
        viewModel.setQuery("new")
        advanceUntilIdle()
        assertEquals("new", viewModel.state.value.gifs.single().id)
    }

    @Test
    fun `pagination guards duplicate calls and stops at end`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.loadNextPage()
        viewModel.loadNextPage()
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(0, 24, 48), repository.calls.map { it.second })
        assertEquals(3, viewModel.state.value.gifs.size)
        assertFalse(viewModel.state.value.hasMore)
    }

    @Test
    fun `page failure preserves results and retry uses same offset`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        repository.searchHandler = { _, _ -> throw GifException(GifError.OFFLINE) }
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(1, viewModel.state.value.gifs.size)
        assertNotNull(viewModel.state.value.pageError)
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(2, repository.calls.size)
        repository.searchHandler = { _, offset -> GifPage(listOf(gif("next")), offset + 1, false) }
        viewModel.retryNextPage()
        runCurrent()
        assertEquals(24, repository.calls.last().second)
        assertEquals(2, viewModel.state.value.gifs.size)
    }

    @Test
    fun `query change resets pagination and persists input`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val saved = SavedStateHandle(mapOf("query" to "dogs"))
        val viewModel = SearchViewModel(repository, saved)
        runCurrent()
        assertEquals("dogs" to 0, repository.calls.first())
        viewModel.loadNextPage()
        runCurrent()
        viewModel.setQuery("cats")
        advanceUntilIdle()
        assertEquals("cats" to 0, repository.calls.last())
        assertEquals("cats", saved.get<String>("query"))
        assertEquals(1, viewModel.state.value.gifs.size)
    }

    @Test
    fun `initial error can be retried`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { _, _ -> throw GifException(GifError.MISSING_API_KEY) }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        assertEquals(GifError.MISSING_API_KEY, viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        runCurrent()
        repository.searchHandler = { _, _ -> GifPage(emptyList(), 0, false) }
        viewModel.retry()
        runCurrent()
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.hasMore)
    }

    @Test
    fun `identical input does not restart the request`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()

        viewModel.setQuery("cats")
        advanceUntilIdle()
        viewModel.setQuery("cats")
        advanceUntilIdle()

        assertEquals(listOf("" to 0, "cats" to 0), repository.calls)
    }

    @Test
    fun `query limit applies to both request and saved input`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val savedState = SavedStateHandle()
        val viewModel = SearchViewModel(repository, savedState)
        runCurrent()

        viewModel.setQuery("a".repeat(75))
        advanceUntilIdle()

        assertEquals("a".repeat(50), repository.calls.last().first)
        assertEquals("a".repeat(50), savedState.get<String>("query"))
    }

    @Test
    fun `clearing search requests trending from the first page`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.setQuery("cats")
        advanceUntilIdle()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.setQuery("")
        advanceUntilIdle()

        assertEquals("" to 0, repository.calls.last())
        assertEquals(1, viewModel.state.value.gifs.size)
    }

    @Test
    fun `duplicate gif ids do not crash the keyed grid or change API offsets`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { _, offset ->
            val gifs = if (offset == 0) listOf(gif("one"), gif("one")) else listOf(gif("one"), gif("two"))
            GifPage(gifs, offset + 2, offset == 0)
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        assertEquals(listOf("one", "two"), viewModel.state.value.gifs.map { it.id })
        assertEquals(listOf(0, 2), repository.calls.map { it.second })
    }

    @Test
    fun `late pagination response cannot append to a different query`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { query, offset ->
            if (offset > 0) withContext(NonCancellable) { delay(1000) }
            GifPage(listOf(gif("$query-$offset")), offset + 24, true)
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.setQuery("new")
        advanceUntilIdle()

        assertEquals(listOf("new-0"), viewModel.state.value.gifs.map { it.id })
        assertFalse(viewModel.state.value.loadingMore)
    }

    @Test
    fun `late error cannot replace a newer successful search`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { query, _ ->
            if (query == "old") {
                withContext(NonCancellable) { delay(1000) }
                throw GifException(GifError.OFFLINE)
            }
            GifPage(listOf(gif(query)), 1, false)
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.setQuery("old")
        advanceTimeBy(400)
        runCurrent()
        viewModel.setQuery("new")
        advanceUntilIdle()

        assertEquals("new", viewModel.state.value.gifs.single().id)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `loading stays visible while debounce is pending and blocks pagination`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.setQuery("cats")
        viewModel.loadNextPage()
        runCurrent()

        assertTrue(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.gifs.isEmpty())
        assertEquals(1, repository.calls.size)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `cleared viewmodel never publishes a late response`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.searchHandler = { _, _ ->
            withContext(NonCancellable) { delay(1000) }
            GifPage(listOf(gif("late")), 1, false)
        }
        val viewModel = SearchViewModel(repository, SavedStateHandle())
        val store = androidx.lifecycle.ViewModelStore()
        store.put("search", viewModel)
        runCurrent()
        store.clear()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.gifs.isEmpty())
    }

    private class FakeRepository : GifRepository {
        val calls = mutableListOf<Pair<String, Int>>()
        var categoriesHandler: suspend () -> List<String> = { listOf("Animals", "Reactions") }
        var searchHandler: suspend (String, Int) -> GifPage = { _, offset ->
            GifPage(listOf(gif("$offset")), offset + 24, offset < 48)
        }

        override suspend fun getCategories() = categoriesHandler()

        override suspend fun searchGifs(query: String, offset: Int): GifPage {
            calls += query to offset
            return searchHandler(query, offset)
        }

        override suspend fun getGifDetails(id: String): Gif = error("Not used")
    }

    private companion object {
        fun gif(id: String) = Gif(id, id, "preview.gif", "original.gif", 1f, "source", "creator")
    }
}
