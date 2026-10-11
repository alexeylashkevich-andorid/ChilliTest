package com.lashla.chillitest.feature.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lashla.chillitest.core.common.ErrorContent
import com.lashla.chillitest.core.common.GifImage
import com.lashla.chillitest.core.common.LoadingIndicator
import com.lashla.chillitest.core.common.theme.ChilliTestTheme
import com.lashla.chillitest.core.common.theme.Dimens
import com.lashla.chillitest.core.common.userMessage
import com.lashla.chillitest.core.domain.Gif
import com.lashla.chillitest.core.domain.GifError
import com.lashla.chillitest.core.common.R as CommonR

@Composable
fun DetailsScreen(onBack: () -> Unit, onGifSelected: (String) -> Unit) {
    val viewModel: DetailsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onGifSelected = onGifSelected,
        onRetrySimilar = viewModel::loadSimilarGifs,
    )
}

@Composable
internal fun DetailsContent(
    state: DetailsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onGifSelected: (String) -> Unit = {},
    onRetrySimilar: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(horizontal = Dimens.controlSpacing, vertical = Dimens.smallSpacing).size(ActionButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back_to_search),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        when {
            state.loading -> LoadingIndicator()
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.error == GifError.NOT_FOUND) {
                    Column(Modifier.padding(Dimens.sectionPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.userMessage())
                        TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_search)) }
                    }
                } else {
                    ErrorContent(state.error.userMessage(), onRetry)
                }
            }
            state.gif != null -> BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Dimens.contentPadding)) {
                val gif = state.gif
                if (maxWidth > WideScreenBreakpoint) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DetailsColumnSpacing)) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .padding(bottom = Dimens.contentPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            GifImage(
                                gif = gif,
                                modifier = Modifier.aspectRatio(gif.aspectRatio, matchHeightConstraintsFirst = true)
                                    .clip(RoundedCornerShape(GifCornerRadius)),
                                useOriginal = true,
                            )
                        }
                        GifInfo(gif, Modifier.weight(1f).verticalScroll(rememberScrollState()), state, onGifSelected, onRetrySimilar)
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        GifImage(
                            gif = gif,
                            modifier = Modifier.fillMaxWidth()
                                .aspectRatio(gif.aspectRatio)
                                .clip(RoundedCornerShape(GifCornerRadius)),
                            useOriginal = true,
                        )
                        GifInfo(gif, Modifier.fillMaxWidth(), state, onGifSelected, onRetrySimilar)
                    }
                }
            }
        }
    }
}

@Composable
private fun GifInfo(
    gif: Gif,
    modifier: Modifier,
    state: DetailsState,
    onGifSelected: (String) -> Unit,
    onRetrySimilar: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(gif.id) { mutableStateOf(false) }
    val shareUrl = gif.sourceUrl.ifBlank { gif.originalUrl }
    val shareLabel = stringResource(R.string.share_gif)
    val clipboardLabel = stringResource(R.string.clipboard_label)
    val title = gif.title.ifBlank { stringResource(CommonR.string.untitled_gif) }
    Column(
        modifier = modifier.padding(vertical = Dimens.sectionPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.mediumSpacing),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = { shareGif(context, shareUrl, shareLabel) },
                modifier = Modifier.size(ActionButtonSize),
                shape = RoundedCornerShape(ButtonRadius),
            ) {
                Icon(painter = painterResource(R.drawable.ic_share), contentDescription = shareLabel, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(Dimens.itemSpacing))
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, shareUrl))
                    copied = true
                },
                modifier = Modifier.size(ActionButtonSize),
                shape = RoundedCornerShape(ButtonRadius),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(if (copied) R.string.copied else R.string.copy_link),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (gif.creator.isNotBlank()) {
            Text(stringResource(R.string.gif_creator, gif.creator), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(
            text = stringResource(R.string.giphy_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SimilarGifs(state, onGifSelected, onRetrySimilar)
    }
}

@Composable
private fun SimilarGifs(state: DetailsState, onGifSelected: (String) -> Unit, onRetry: () -> Unit) {
    Text(
        stringResource(R.string.similar_gifs),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
    when {
        state.similarLoading -> LoadingIndicator(Modifier.fillMaxWidth().height(SimilarLoadingHeight))
        state.similarError != null -> ErrorContent(state.similarError.userMessage(), onRetry)
        state.similarGifs.isEmpty() -> Text(
            stringResource(R.string.no_similar_gifs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            state.similarGifs.chunked(SIMILAR_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
                    row.forEach { gif ->
                        val openLabel = stringResource(R.string.open_similar_gif, gif.title)
                        GifImage(
                            gif,
                            Modifier.weight(1f).aspectRatio(1f)
                                .clip(RoundedCornerShape(ButtonRadius))
                                .clickable(role = Role.Button, onClickLabel = openLabel) { onGifSelected(gif.id) },
                        )
                    }
                    if (row.size < SIMILAR_COLUMNS) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun shareGif(context: Context, url: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private const val SIMILAR_COLUMNS = 2
private val SimilarLoadingHeight = 64.dp
private val WideScreenBreakpoint = 600.dp
private val DetailsColumnSpacing = 28.dp
private val GifCornerRadius = 24.dp
private val ButtonRadius = 14.dp
private val ActionButtonSize = 48.dp

@Preview(showBackground = true)
@Composable
private fun DetailsPreview() {
    ChilliTestTheme {
        Surface { DetailsContent(previewState, onBack = {}, onRetry = {}) }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400)
@Composable
private fun DetailsLandscapePreview() {
    ChilliTestTheme(darkTheme = true) {
        Surface { DetailsContent(previewState, onBack = {}, onRetry = {}) }
    }
}

private val previewGif = Gif("1", "Small joys", "", "", .72f, "", "studio")

private val previewState = DetailsState(
    gif = previewGif,
    loading = false,
    similarGifs = listOf(
        previewGif.copy(id = "2", title = "Happy dance"),
        previewGif.copy(id = "3", title = "Weekend mood"),
        previewGif.copy(id = "4", title = "Little celebration"),
        previewGif.copy(id = "5", title = "Good vibes"),
    ),
)
