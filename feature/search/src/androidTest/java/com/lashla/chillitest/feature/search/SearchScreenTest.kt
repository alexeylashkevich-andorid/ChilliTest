package com.lashla.chillitest.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.lashla.chillitest.core.domain.GifPage
import com.lashla.chillitest.core.domain.GifRepository
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.common.theme.ChilliTestTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import com.lashla.chillitest.core.common.R as CommonR
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

class SearchScreenTest {
    @Test fun landscapeSearchKeepsAttributionAndResultsVisible() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalInspectionMode provides true) {
                ChilliTestTheme { Surface(Modifier.requiredSize(320.dp, 320.dp)) {
                    SearchContent(SearchState(gifs = previewGifs, loading = false, hasMore = false), {}, {}, {}, {}, {})
                } }
            }
        }
        compose.onNodeWithText("Powered by GIPHY").assertIsDisplayed()
        compose.onNodeWithTag("gif_grid").assertIsDisplayed()
    }

    @Test fun categoryOnlyFailureCanRetryWithoutReloadingTheFeed() {
        val state = mutableStateOf(SearchState(gifs = previewGifs, loading = false, hasMore = false, categoriesError = true))
        var categoryRetries = 0
        var feedRetries = 0
        compose.setContent { ChilliTestTheme { Surface {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                SearchContent(state.value, {}, {}, {}, { feedRetries++ }, {}, onRetryCategories = { categoryRetries++ })
            }
        } } }
        compose.onNodeWithText("Topics unavailable · Retry").performClick()
        compose.onNodeWithTag("gif_grid").assertIsDisplayed()
        compose.onNodeWithText("✦  Small joys").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, categoryRetries)
            assertEquals(0, feedRetries)
        }
    }

    @Test fun trendingIsSelectedOnLaunchAndAfterClearingSearch() {
        val state = mutableStateOf(SearchState(categories = listOf("Animals")))
        compose.setContent {
            ChilliTestTheme {
                SearchContent(state.value, { state.value = state.value.copy(query = it) }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Trending").assertIsSelected()
        val animalsChip = hasText("Animals") and hasAnyAncestor(hasTestTag("category_chips"))
        compose.onNode(animalsChip).assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithText("Trending").assertIsNotSelected()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Trending").assertIsSelected()
        compose.onNode(animalsChip).assertIsNotSelected()
        compose.onNodeWithTag("search_field").performTextInput("cats")
        compose.onNodeWithText("Trending").assertIsNotSelected()
        compose.onNode(animalsChip).assertIsNotSelected()
    }

    @Test fun duplicateOnlyPageStopsAutomaticRequestsUntilLoadMoreIsTapped() {
        val calls = mutableListOf<Int>()
        val gifs = List(40) { Gif("$it", "GIF $it", "", "", 1f, "", "") }
        val repository = object : GifRepository {
            override suspend fun getCategories() = emptyList<String>()
            override suspend fun getGifDetails(id: String): Gif = error("Not used")
            override suspend fun searchGifs(query: String, offset: Int): GifPage {
                calls += offset
                val items = if (offset < 80) gifs else listOf(gifs.first().copy(id = "new", title = "New GIF"))
                return GifPage(items, offset + 40, offset < 80)
            }
        }
        val store = ViewModelStore()
        lateinit var vm: SearchViewModel
        compose.runOnUiThread {
            vm = SearchViewModel(repository, SavedStateHandle())
            store.put("search", vm)
        }
        try {
            compose.setContent { ChilliTestTheme { Surface {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    SearchContent(vm.state.collectAsState().value, vm::setQuery, {}, vm::loadNextPage, vm::retry, vm::retryNextPage)
                }
            } } }
            compose.onNodeWithTag("gif_grid").performScrollToIndex(39)
            compose.onNodeWithTag("gif_grid").performScrollToIndex(40)
            compose.onNodeWithText("Load more").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(2000)
            compose.runOnIdle { assertEquals(listOf(0, 40), calls) }
            compose.onNodeWithText("Load more").performClick()
            compose.runOnIdle {
                assertEquals(listOf(0, 40, 80), calls)
                assertEquals("new", vm.state.value.gifs.last().id)
            }
        } finally {
            compose.runOnUiThread { store.clear() }
        }
    }

    @Test fun skippedFirstPageHasReachableLoadMoreAndRetry() {
        val state = mutableStateOf(SearchState(loading = false, paginationPaused = true))
        var loads = 0
        var retries = 0
        compose.setContent { ChilliTestTheme { Surface {
            SearchContent(state.value, {}, {}, { loads++ }, {}, { retries++ })
        } } }
        compose.onNodeWithText("Load more").performClick()
        compose.runOnIdle { state.value = state.value.copy(pageError = GifError.SERVICE_UNAVAILABLE) }
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle {
            assertEquals(1, loads)
            assertEquals(1, retries)
        }
    }

    @Test fun largeTextInSmallWindowKeepsResultsAndSearchReachable() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale = 2f),
                LocalInspectionMode provides true,
            ) {
                ChilliTestTheme {
                    Surface(Modifier.requiredSize(320.dp, 320.dp)) {
                        SearchContent(
                            SearchState(gifs = previewGifs, loading = false, hasMore = false),
                            {}, {}, {}, {}, {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("gif_grid").assertIsDisplayed()
        compose.onNodeWithText("✦  Small joys").assertIsDisplayed()
        compose.onNodeWithTag("search_field").performScrollTo().assertIsDisplayed()
    }

    private val previewGifs = listOf(Gif("1", "Small joys", "", "", .72f, "", "studio"))
    @get:Rule val compose = createComposeRule()
    @Test fun searchInputCategoriesAndGridAreInteractive() {
        var query = ""; var selected = ""
        compose.setContent { ChilliTestTheme { Surface {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                SearchContent(SearchState(categories = listOf("Animals", "Reactions"), gifs = previewGifs, loading = false, hasMore = false),
                    { query = it }, { selected = it.id }, {}, {}, {})
            }
        } } }
        compose.onNodeWithText("Search GIFs…").performTextInput("cats")
        assertEquals("cats", query)
        compose.onNodeWithText("Animals").performClick(); assertEquals("Animals", query)
        compose.onNodeWithText("✦  Small joys").performClick(); assertEquals("1", selected)
    }
    @Test fun categoryLoadingSelectionAndSharedRetryAreInteractive() {
        val state = mutableStateOf(SearchState(loading = false, hasMore = false, categoriesLoading = true))
        var retries = 0
        compose.setContent { ChilliTestTheme { Surface {
            SearchContent(state.value, { state.value = state.value.copy(query = it) }, {}, {}, { retries++ }, {})
        } } }
        compose.onNodeWithText("Loading topics…").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(categoriesLoading = false, categoriesError = true, error = GifError.OFFLINE) }
        compose.onNodeWithText("Topics unavailable · Retry").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state.value = state.value.copy(error = null, categoriesError = false, categories = List(15) { "Topic $it" })
        }
        compose.onNodeWithTag("category_chips").performScrollToNode(hasText("Topic 14"))
        compose.onNode(hasText("Topic 14") and hasAnyAncestor(hasTestTag("category_chips")))
            .performClick().assertIsSelected()
        compose.onNodeWithTag("search_field").assertTextContains("Topic 14")
        compose.onNodeWithTag("category_chips").performScrollToNode(hasText("Trending"))
        compose.onNodeWithText("Trending").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("", state.value.query) }
    }

    @Test fun emptyStateIsVisible() {
        compose.setContent { ChilliTestTheme { Surface {
            SearchContent(SearchState(loading = false), {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("No GIFs found").assertIsDisplayed()
    }

    @Test fun loadingAndInitialErrorHaveDistinctContentAndRetry() {
        val state = mutableStateOf(SearchState())
        var retries = 0
        compose.setContent {
            ChilliTestTheme {
                Surface {
                    SearchContent(state.value, {}, {}, {}, { retries++ }, {})
                }
            }
        }
        compose.onNodeWithTag("loading_indicator").assertIsDisplayed()
        compose.onNodeWithText("No GIFs found").assertDoesNotExist()

        compose.runOnIdle { state.value = SearchState(loading = false, error = GifError.SERVICE_UNAVAILABLE) }
        val errorMessage = InstrumentationRegistry.getInstrumentation().targetContext.getString(CommonR.string.error_service_unavailable)
        compose.onNodeWithText(errorMessage).assertIsDisplayed()
        compose.onNodeWithTag("loading_indicator").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test fun paginationLoadsNearEndAndFailureRequiresExplicitRetry() {
        val gifs = List(40) { Gif("$it", "GIF $it", "", "", 1f, "", "") }
        val state = mutableStateOf(SearchState(gifs = gifs, loading = false))
        var loads = 0
        var retries = 0
        compose.setContent {
            ChilliTestTheme {
                Surface {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        SearchContent(state.value, {}, {}, { loads++ }, {}, { retries++ })
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(0, loads) }
        compose.onNodeWithTag("gif_grid").performScrollToIndex(39)
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(loads > 0)
            state.value = state.value.copy(pageError = GifError.SERVICE_UNAVAILABLE)
        }
        compose.waitForIdle()
        val loadsAfterFailure = loads
        compose.onNodeWithTag("gif_grid").performScrollToIndex(40)
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle {
            assertEquals(loadsAfterFailure, loads)
            assertEquals(1, retries)
        }
    }

    @Test fun scrollRestoresAndChangingQueryResetsIt() {
        val gifs = List(40) { Gif("$it", "GIF $it", "", "", 1f, "", "") }
        val state = mutableStateOf(SearchState(gifs = gifs, loading = false, hasMore = false))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ChilliTestTheme {
                Surface {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        SearchContent(state.value, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("gif_grid").performScrollToIndex(20)
        compose.onNodeWithText("✦  GIF 20").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("✦  GIF 20").assertIsDisplayed()

        compose.runOnIdle { state.value = state.value.copy(query = "new") }
        compose.onNodeWithText("✦  GIF 0").assertIsDisplayed()
    }

    @Test fun clearButtonClearsInputAndEndStateDoesNotRequestAnotherPage() {
        var query = "not cleared"
        var loads = 0
        compose.setContent {
            ChilliTestTheme {
                Surface {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        SearchContent(
                            SearchState(query = "cats", gifs = previewGifs, loading = false, hasMore = false),
                            { query = it }, {}, { loads++ }, {}, {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("No more GIFs").assertExists()
        compose.runOnIdle {
            assertEquals("", query)
            assertEquals(0, loads)
        }
    }
}
