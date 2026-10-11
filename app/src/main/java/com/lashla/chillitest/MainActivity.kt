package com.lashla.chillitest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import coil.ImageLoader
import com.lashla.chillitest.core.common.LocalOnlineState
import com.lashla.chillitest.core.common.image.LocalImageLoader
import com.lashla.chillitest.core.common.theme.ChilliTestTheme
import com.lashla.chillitest.core.common.theme.Dimens
import com.lashla.chillitest.core.navigation.NavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val online by rememberOnlineState()
            CompositionLocalProvider(LocalImageLoader provides imageLoader, LocalOnlineState provides online) {
                ChilliTestTheme {
                    Scaffold(topBar = {
                        if (!online) {
                            Surface(modifier = Modifier.statusBarsPadding(), color = MaterialTheme.colorScheme.errorContainer) {
                                Text(
                                    text = stringResource(R.string.offline_message),
                                    modifier = Modifier.fillMaxWidth().padding(Dimens.smallSpacing)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                    }) { padding ->
                        NavGraph(modifier = Modifier.padding(padding).consumeWindowInsets(padding))
                    }
                }
            }
        }
    }
}
