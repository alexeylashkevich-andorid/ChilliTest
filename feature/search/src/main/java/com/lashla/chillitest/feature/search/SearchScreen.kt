package com.lashla.chillitest.feature.search

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SearchScreen(onGifSelected: (String) -> Unit) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SearchContent(
        state = state,
        onQuery = viewModel::setQuery,
        onGif = { onGifSelected(it.id) },
        onLoadMore = viewModel::loadNextPage,
        onRetry = viewModel::retry,
        onRetryPage = viewModel::retryNextPage,
        onRetryCategories = viewModel::retryCategories,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchContent(
    state: SearchState,
    onQuery: (String) -> Unit,
    onGif: (Gif) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRetryPage: () -> Unit,
    onRetryCategories: () -> Unit = {},
) {
    val gridState = rememberLazyStaggeredGridState()
    var previousQuery by rememberSaveable { mutableStateOf(state.query) }

    LaunchedEffect(state.query) {
        if (previousQuery != state.query) {
            gridState.scrollToItem(0)
            previousQuery = state.query
        }
    }

    val landscapeTyping = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        WindowInsets.isImeVisible
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val headerMaxHeight = if (landscapeTyping) maxHeight else maxHeight * HEADER_HEIGHT_FRACTION
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState())) {
                SearchHeader(state, onQuery, onRetryCategories)
            }
            if (!landscapeTyping) {
                Box(Modifier.weight(1f)) {
                    SearchResults(
                        state = state,
                        gridState = gridState,
                        onGif = onGif,
                        onLoadMore = onLoadMore,
                        onRetry = onRetry,
                        onRetryPage = onRetryPage,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(state: SearchState, onQueryChange: (String) -> Unit, onRetryCategories: () -> Unit) {
    val query = state.query
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (!isLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.sectionPadding, vertical = Dimens.mediumSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.app_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.giphy_attribution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Text(
            text = stringResource(R.string.giphy_attribution),
            modifier = Modifier.padding(horizontal = Dimens.contentPadding),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = { onQueryChange("") }) { Text(stringResource(R.string.clear_search)) }
            }
        },
        shape = RoundedCornerShape(SearchFieldRadius),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.contentPadding).testTag("search_field"),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant),
    )
    CategoryChips(state, onQueryChange, onRetryCategories)
}

@Composable
private fun CategoryChips(state: SearchState, onQueryChange: (String) -> Unit, onRetry: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.contentPadding, vertical = Dimens.itemSpacing),
        horizontalArrangement = Arrangement.spacedBy(Dimens.smallSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("category_chips"),
    ) {
        item(key = "trending") {
            FilterChip(
                selected = state.query.isBlank(),
                onClick = { onQueryChange("") },
                label = { Text(stringResource(R.string.category_for_you)) },
                shape = RoundedCornerShape(ChipRadius),
            )
        }
        items(state.categories, key = { "category:$it" }) { category ->
            FilterChip(
                selected = state.query.equals(category, ignoreCase = true),
                onClick = { onQueryChange(category) },
                label = { Text(category) },
                shape = RoundedCornerShape(ChipRadius),
            )
        }
        if (state.categoriesLoading) {
            item(key = "loading") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.smallSpacing)) {
                    CircularProgressIndicator(Modifier.size(TopicLoaderSize), strokeWidth = Dimens.loaderStroke)
                    Text(stringResource(R.string.categories_loading), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (state.categoriesError && state.error == null && state.pageError == null) {
            item(key = "retry_categories") {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.categories_retry)) }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchState,
    gridState: LazyStaggeredGridState,
    onGif: (Gif) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRetryPage: () -> Unit,
) {
    when {
        state.loading -> LoadingIndicator()
        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorContent(state.error.userMessage(), onRetry)
        }
        state.gifs.isEmpty() -> Column(
            modifier = Modifier.fillMaxSize().padding(Dimens.sectionPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.empty_message),
                modifier = Modifier.padding(top = Dimens.smallSpacing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.hasMore) PaginationFooter(state, onRetryPage, onLoadMore)
        }
        else -> GifGrid(state, gridState, onGif, onLoadMore, onRetryPage)
    }
}

@Composable
private fun GifGrid(
    state: SearchState,
    gridState: LazyStaggeredGridState,
    onGif: (Gif) -> Unit,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit,
) {
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState, state.gifs.size, state.canAutoLoadMore) {
        if (!state.canAutoLoadMore) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index }
            lastVisible != null && lastVisible >= state.gifs.size - PREFETCH_DISTANCE
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) loadMore()
        }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(MinimumGridColumnWidth),
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("gif_grid"),
        contentPadding = PaddingValues(start = Dimens.contentPadding, end = Dimens.contentPadding, bottom = Dimens.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
        verticalItemSpacing = Dimens.itemSpacing,
    ) {
        items(state.gifs, key = { it.id }) { gif ->
            val actionLabel = stringResource(R.string.open_gif, gif.title)
            GifImage(
                gif = gif,
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(gif.aspectRatio)
                    .clip(RoundedCornerShape(GifCardRadius))
                    .clickable(onClickLabel = actionLabel) { onGif(gif) },
            )
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            PaginationFooter(state, onRetryPage, onLoadMore)
        }
    }
}

private const val PREFETCH_DISTANCE = 6
private const val HEADER_HEIGHT_FRACTION = 0.6f
private val MinimumGridColumnWidth = 155.dp
private val PaginationHeight = 68.dp
private val TopicLoaderSize = 16.dp
private val GifCardRadius = 16.dp
private val ChipRadius = 12.dp
private val SearchFieldRadius = 20.dp

@Composable
private fun PaginationFooter(state: SearchState, onRetry: () -> Unit, onLoadMore: () -> Unit) {
    when {
        state.loadingMore -> LoadingIndicator(Modifier.fillMaxWidth().height(PaginationHeight))
        state.pageError != null -> ErrorContent(state.pageError.userMessage(), onRetry)
        state.hasMore && state.paginationPaused -> TextButton(onClick = onLoadMore) {
            Text(stringResource(R.string.load_more))
        }
        !state.hasMore -> Text(
            text = stringResource(R.string.pagination_end),
            modifier = Modifier.fillMaxWidth().padding(Dimens.contentPadding),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchPreview() {
    ChilliTestTheme {
        Surface {
            SearchContent(
                state = SearchState(categories = listOf("Animals", "Reactions", "Food & Drink"), gifs = previewGifs, loading = false),
                onQuery = {},
                onGif = {},
                onLoadMore = {},
                onRetry = {},
                onRetryPage = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400)
@Composable
private fun SearchLandscapePreview() {
    ChilliTestTheme(darkTheme = true) {
        Surface {
            SearchContent(
                state = SearchState(categories = listOf("Animals", "Reactions", "Food & Drink"), gifs = previewGifs, loading = false),
                onQuery = {},
                onGif = {},
                onLoadMore = {},
                onRetry = {},
                onRetryPage = {},
            )
        }
    }
}

private val previewGifs = listOf(
    Gif("1", "Small joys", "", "", .72f, "", "studio"),
    Gif("2", "Big energy", "", "", 1.35f, "", "creator"),
    Gif("3", "Happy dance", "", "", .8f, "", "studio"),
    Gif("4", "Good vibes", "", "", .95f, "", "creator"),
)
