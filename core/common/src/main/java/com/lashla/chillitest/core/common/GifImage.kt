package com.lashla.chillitest.core.common

import android.graphics.drawable.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.lashla.chillitest.core.common.image.LocalImageLoader
import com.lashla.chillitest.core.common.theme.Dimens
import com.lashla.chillitest.core.domain.Gif

@Composable
fun GifImage(
    gif: Gif,
    modifier: Modifier = Modifier,
    useOriginal: Boolean = false,
) {
    val title = gif.title.ifBlank { stringResource(R.string.untitled_gif) }
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.preview_gif, title), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        return
    }

    var attempt by remember(gif.id) { mutableIntStateOf(0) }
    key(attempt) {
        var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        var visible by remember { mutableStateOf(false) }
        val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val animation = (state as? AsyncImagePainter.State.Success)?.result?.drawable as? Animatable
        val online = LocalOnlineState.current
        var previouslyOnline by remember { mutableStateOf(online) }

        LaunchedEffect(online) {
            val canRetry = visible && lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
                state is AsyncImagePainter.State.Error
            if (online && !previouslyOnline && canRetry) attempt++
            previouslyOnline = online
        }

        LaunchedEffect(animation, visible, lifecycleState) {
            if (visible && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) animation?.start()
            else animation?.stop()
        }
        DisposableEffect(animation) {
            onDispose { animation?.stop() }
        }

        Box(modifier.onGloballyPositioned { visible = !it.boundsInWindow().isEmpty }) {
            AsyncImage(
                model = if (useOriginal) gif.originalUrl else gif.previewUrl,
                imageLoader = LocalImageLoader.current,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (useOriginal) ContentScale.Fit else ContentScale.Crop,
                onState = { state = it },
            )

            if (state !is AsyncImagePainter.State.Success) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state is AsyncImagePainter.State.Error) {
                        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.reload_gif)) }
                    } else {
                        CircularProgressIndicator(Modifier.size(ImageLoaderSize), strokeWidth = Dimens.loaderStroke)
                    }
                }
            }
        }
    }
}

private val ImageLoaderSize = 24.dp
