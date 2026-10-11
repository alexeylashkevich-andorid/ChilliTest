package com.lashla.chillitest.feature.details

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.common.theme.ChilliTestTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.content.ClipboardManager
import androidx.test.platform.app.InstrumentationRegistry
import com.lashla.chillitest.core.common.R as CommonR

class DetailsScreenTest {
    @get:Rule
    val compose = createComposeRule()


    @Test
    fun similarGifsOpenDetailsAndFailuresCanBeRetried() {
        var selected = ""
        var retries = 0
        val gif = Gif("main", "Main GIF", "", "", 1f, "", "")
        val related = gif.copy(id = "related", title = "Related GIF")
        val state = mutableStateOf(DetailsState(gif, false, similarGifs = listOf(related)))
        compose.setContent {
            ChilliTestTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    DetailsContent(state.value, {}, {}, { selected = it }, { retries++ })
                }
            }
        }
        compose.onNodeWithText("✦  Related GIF").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("related", selected)
            state.value = state.value.copy(similarGifs = emptyList(), similarError = GifError.OFFLINE)
        }
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Main GIF").assertExists()
        compose.runOnIdle { state.value = state.value.copy(similarError = null) }
        compose.onNodeWithText("No similar GIFs found").assertExists()
    }

    @Test
    fun largeTitleWrapsWithoutOverlappingActionsAtDoubleFontScale() {
        val title = "A long GIF title that needs several lines on a narrow screen"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalDensity provides Density(density.density, 2f),
            ) {
                ChilliTestTheme {
                    Box(Modifier.requiredSize(320.dp, 600.dp)) {
                        DetailsContent(DetailsState(Gif("large", title, "", "", 1f, "", ""), false), {}, {})
                    }
                }
            }
        }
        val titleNode = compose.onNodeWithText(title)
        titleNode.performScrollTo().assertIsDisplayed()
        val share = compose.onNodeWithContentDescription("Share GIF")
        val copy = compose.onNodeWithContentDescription("Copy link")
        assertTrue(titleNode.getUnclippedBoundsInRoot().right <= share.getUnclippedBoundsInRoot().left)
        share.assertIsDisplayed()
        copy.assertIsDisplayed()
        copy.assertWidthIsEqualTo(48.dp)
        copy.assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun blankTitleUsesLocalizedFallback() {
        compose.setContent {
            ChilliTestTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    DetailsContent(
                        DetailsState(Gif("untitled", "", "", "", 1f, "", ""), false), {}, {},
                    )
                }
            }
        }
        compose.onNodeWithText("Untitled GIF").assertIsDisplayed()
        compose.onNodeWithText("✦  Untitled GIF").assertIsDisplayed()
    }

    @Test
    fun deletedGifOffersBackInsteadOfRetry() {
        var backs = 0
        compose.setContent {
            ChilliTestTheme {
                Surface {
                    DetailsContent(
                        state = DetailsState(loading = false, error = GifError.NOT_FOUND),
                        onBack = { backs++ },
                        onRetry = { error("Deleted GIF should not offer retry") },
                    )
                }
            }
        }
        compose.onNodeWithText("This GIF is no longer available.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Back to search").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun detailsExposeTitleCreatorAndBack() {
        var backed = false
        compose.setContent {
            ChilliTestTheme(darkTheme = true) {
                Surface {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        DetailsContent(
                            state = DetailsState(Gif("1", "Big energy", "", "", 1.35f, "", "creator"), loading = false),
                            onBack = { backed = true },
                            onRetry = {},
                        )
                    }
                }
            }
        }
        compose.onNode(hasText("Big energy") and !hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("by @creator").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to search").performClick()
        assertTrue(backed)
    }

    @Test
    fun errorCanBeRetriedWithoutLosingBackNavigation() {
        var retries = 0
        var backs = 0
        compose.setContent {
            ChilliTestTheme {
                Surface {
                    DetailsContent(DetailsState(loading = false, error = GifError.SERVICE_UNAVAILABLE), { backs++ }, { retries++ })
                }
            }
        }
        val errorMessage = InstrumentationRegistry.getInstrumentation().targetContext.getString(CommonR.string.error_service_unavailable)
        compose.onNodeWithText(errorMessage).assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithContentDescription("Back to search").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun copyLinkUsesSourceAndFallsBackToOriginalMedia() {
        val gif = Gif("copy", "Copy test", "", "https://example.com/original.gif", 1.5f, "https://giphy.com/gifs/copy", "")
        val state = mutableStateOf(DetailsState(gif, false))
        compose.setContent {
            ChilliTestTheme {
                Surface {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        DetailsContent(state.value, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Copy link").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Copied!").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnIdle {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            assertEquals(gif.sourceUrl, clipboard.primaryClip!!.getItemAt(0).text.toString())
            state.value = DetailsState(gif.copy(id = "fallback", sourceUrl = ""), false)
        }
        compose.onNodeWithContentDescription("Copy link").performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            assertEquals(gif.originalUrl, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }
}
