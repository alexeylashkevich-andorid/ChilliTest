package com.lashla.chillitest

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import android.graphics.drawable.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import coil.EventListener
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import coil.size.Dimension
import coil.ImageLoader
import com.lashla.chillitest.core.common.image.LocalImageLoader
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lashla.chillitest.core.common.GifImage
import com.lashla.chillitest.core.common.LocalOnlineState
import com.lashla.chillitest.core.common.theme.ChilliTestTheme
import com.lashla.chillitest.core.domain.Gif
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@HiltAndroidTest
class GifImageTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Inject lateinit var imageLoader: ImageLoader

    @Before fun setUp() {
        hilt.inject()
    }

    @Test fun validGifLeavesLoadingState() {
        val file = newFixtureFile()
        try {
            writeGif(file)
            showGif(file)
            awaitLoadedGif()
        } finally {
            file.delete()
        }
    }

    @Test fun failedGifCanBeRetriedAfterItsSourceRecovers() {
        val file = newFixtureFile()
        try {
            file.writeText("Invalid GIF data")
            showGif(file)
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("Reload GIF").fetchSemanticsNodes().isNotEmpty()
            }
            writeGif(file)
            compose.onNodeWithText("Reload GIF").performClick()
            awaitLoadedGif()
        } finally {
            file.delete()
        }
    }

    @Test fun offscreenGifStopsAndDecodeSizeMatchesDisplay() {
        val drawable = AtomicReference<Animatable>()
        val resolvedSize = AtomicReference<Size>()
        val loader = imageLoader.newBuilder()
            .eventListener(object : EventListener {
                override fun resolveSizeEnd(request: ImageRequest, size: Size) { resolvedSize.set(size) }
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    drawable.set(result.drawable as Animatable)
                }
            }).build()
        val file = newFixtureFile()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        lateinit var scroll: ScrollState
        var displaySize = 0
        try {
            writeGif(file)
            val gif = Gif("viewport", "Viewport GIF", file.path, file.path, 1f, "", "")
            compose.setContent {
                displaySize = with(LocalDensity.current) { 160.dp.roundToPx() }
                scroll = rememberScrollState()
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalImageLoader provides loader,
                ) {
                    ChilliTestTheme {
                        Column(Modifier.size(160.dp).verticalScroll(scroll)) {
                            GifImage(gif, Modifier.size(160.dp), useOriginal = true)
                            Spacer(Modifier.height(400.dp))
                        }
                    }
                }
            }
            compose.waitUntil(5000) { drawable.get()?.isRunning == true }
            assertEquals(displaySize, (resolvedSize.get().width as Dimension.Pixels).px)
            assertEquals(displaySize, (resolvedSize.get().height as Dimension.Pixels).px)
            compose.runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }
            compose.waitUntil(5000) { drawable.get()?.isRunning == false }
            compose.runOnIdle { runBlocking { scroll.scrollTo(0) } }
            compose.waitUntil(5000) { drawable.get()?.isRunning == true }
            compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
            compose.waitUntil(5000) { drawable.get()?.isRunning == false }
            compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            compose.waitUntil(5000) { drawable.get()?.isRunning == true }
        } finally {
            loader.shutdown()
            file.delete()
        }
    }

    @Test fun reconnectionRetriesFailedGifOnceAndDoesNotReloadSuccessfulGif() {
        val online = mutableStateOf(false)
        val starts = AtomicInteger()
        val loader = imageLoader.newBuilder().eventListener(object : EventListener {
            override fun onStart(request: ImageRequest) { starts.incrementAndGet() }
        }).build()
        val file = newFixtureFile()
        try {
            file.writeText("Invalid GIF data")
            val gif = Gif("recovery", "Animated fixture", file.path, file.path, 1f, "", "")
            compose.setContent {
                CompositionLocalProvider(LocalOnlineState provides online.value, LocalImageLoader provides loader) {
                    ChilliTestTheme { GifImage(gif, Modifier.size(160.dp)) }
                }
            }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Reload GIF").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals(1, starts.get()); online.value = true }
            compose.waitUntil(5000) {
                starts.get() == 2 && compose.onAllNodesWithText("Reload GIF").fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(2000)
            compose.runOnIdle { assertEquals(2, starts.get()); online.value = false }
            compose.waitForIdle()
            writeGif(file)
            compose.runOnIdle { online.value = true }
            awaitLoadedGif()
            compose.runOnIdle { assertEquals(3, starts.get()); online.value = false }
            compose.waitForIdle()
            compose.runOnIdle { online.value = true }
            compose.mainClock.advanceTimeBy(2000)
            compose.runOnIdle { assertEquals(3, starts.get()) }
        } finally {
            loader.shutdown()
            file.delete()
        }
    }

    private fun showGif(file: File) {
        val gif = Gif("fixture", "Animated fixture", file.absolutePath, file.absolutePath, 1f, "", "")
        compose.setContent {
            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                ChilliTestTheme { GifImage(gif, Modifier.size(160.dp)) }
            }
        }
    }

    private fun awaitLoadedGif() {
        compose.waitUntil(5000) {
            compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("Reload GIF").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Animated fixture").assertIsDisplayed()
    }

    private fun newFixtureFile(): File = File.createTempFile(
        "gif-ui-test", ".gif", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
    )

    private fun writeGif(file: File) {
        InstrumentationRegistry.getInstrumentation().context.assets.open("animated.gif").use { input ->
            file.outputStream().use(input::copyTo)
        }
    }
}
