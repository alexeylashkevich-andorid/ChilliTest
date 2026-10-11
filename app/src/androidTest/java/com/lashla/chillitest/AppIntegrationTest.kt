package com.lashla.chillitest

import android.content.pm.ActivityInfo
import androidx.lifecycle.Lifecycle
import android.graphics.drawable.Animatable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import javax.inject.Inject
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lashla.chillitest.core.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.lashla.chillitest.core.data.di.RepositoryModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
class AppIntegrationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @BindValue
    lateinit var repository: GifRepository

    @Inject lateinit var imageLoader: ImageLoader

    private val searches = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        val path = fixture().absolutePath
        val gifs = List(40) { index ->
            val title = when (index) {
                0 -> "Small joys"
                1 -> "Big energy"
                else -> "GIF $index"
            }
            Gif("$index", title, path, path, 1f, "https://giphy.com/gifs/$index", "studio")
        }
        repository = object : GifRepository {
            override suspend fun getCategories() = listOf("Animals", "Reactions")
            override suspend fun searchGifs(query: String, offset: Int): GifPage {
                searches += query
                return GifPage(gifs, gifs.size, false)
            }
            override suspend fun getGifDetails(id: String) = gifs.first { it.id == id }
        }
        hilt.inject()
    }
    private fun fixture(): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return File(instrumentation.targetContext.cacheDir, "animated-test.gif").also { file ->
            instrumentation.context.assets.open("animated.gif").use { input -> file.outputStream().use(input::copyTo) }
        }
    }
    @Test fun networkChangesUpdateBannerInForegroundAndAfterBackgrounding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun setNetwork(enabled: Boolean) {
            val action = if (enabled) "enable" else "disable"
            for (service in listOf("wifi", "data")) {
                instrumentation.uiAutomation.executeShellCommand("svc $service $action").use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                }
            }
        }
        fun waitForBanner(visible: Boolean) {
            compose.waitUntil(20000) {
                compose.onAllNodesWithText("You're offline · Check your internet connection")
                    .fetchSemanticsNodes().isNotEmpty() == visible
            }
        }

        try {
            setNetwork(true)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(10000) { compose.onAllNodesWithTag("gif_grid").fetchSemanticsNodes().isNotEmpty() }
                repeat(2) {
                    setNetwork(false)
                    waitForBanner(true)
                    compose.onNodeWithTag("gif_grid").assertIsDisplayed()
                    setNetwork(true)
                    waitForBanner(false)
                    compose.onNodeWithTag("gif_grid").assertIsDisplayed()
                }

                scenario.moveToState(Lifecycle.State.CREATED)
                setNetwork(false)
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitForBanner(true)

                scenario.moveToState(Lifecycle.State.CREATED)
                setNetwork(true)
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitForBanner(false)
            }
        } finally {
            setNetwork(true)
        }
    }

    @Test fun gifDecoderProducesAnimatedDrawable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = imageLoader.execute(ImageRequest.Builder(context).data(fixture()).build())
        assertTrue("Fixture must decode successfully: $result", result is SuccessResult)
        assertTrue("Expected animated drawable, got ${ (result as SuccessResult).drawable.javaClass.name }", result.drawable is Animatable)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val animation = result.drawable as Animatable
            animation.start()
            assertTrue("GIF animation should start", animation.isRunning)
            animation.stop()
        }
    }
    @Test(timeout = 60000) fun realNavigationAndRotationRestoreDetails() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Small joys").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Small joys").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Small joys").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Small joys").assertIsDisplayed()
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10000) {
                InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Share GIF").assertIsDisplayed()
            scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Small joys").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back to search").performClick()
            compose.onNodeWithText("Trending").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("gif_grid").assertIsDisplayed()
        }
    }

    @Test fun similarGifsReplaceDetailsAndBackRestoresSearch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10000) { compose.onAllNodesWithTag("gif_grid").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("search_field").performTextInput("cats")
            compose.waitUntil(10000) { searches.lastOrNull() == "cats" }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("gif_grid").performScrollToIndex(20)
            compose.onNodeWithContentDescription("GIF 20").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("GIF 20").fetchSemanticsNodes().isNotEmpty() }

            compose.onNodeWithContentDescription("Small joys").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Small joys").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Big energy").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Big energy").fetchSemanticsNodes().isNotEmpty() }
            scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Big energy").fetchSemanticsNodes().isNotEmpty() }

            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("search_field").assertTextContains("cats")
            compose.onNodeWithContentDescription("GIF 20").assertIsDisplayed()

            compose.onNodeWithContentDescription("GIF 20").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("GIF 20").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Small joys").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Small joys").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back to search").performClick()
            compose.onNodeWithTag("search_field").assertTextContains("cats")
            compose.onNodeWithContentDescription("GIF 20").assertIsDisplayed()
        }
    }

    @Test fun queryAndGridPositionSurviveDetailsAndActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10000) { compose.onAllNodesWithTag("gif_grid").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("search_field").performTextInput("cats")
            compose.waitUntil(10000) { searches.lastOrNull() == "cats" }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("gif_grid").performScrollToIndex(20)
            compose.onNodeWithContentDescription("GIF 20").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("GIF 20").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back to search").performClick()
            compose.onNodeWithContentDescription("GIF 20").assertIsDisplayed()
            compose.onNodeWithTag("search_field").assertTextContains("cats")

            scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("gif_grid").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("GIF 20").assertIsDisplayed()
            compose.onNodeWithTag("search_field").assertTextContains("cats")
        }
    }
}
