package io.lldab.mrmphone.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import io.lldab.mrmphone.data.AppContainer
import io.lldab.mrmphone.data.Chapter
import io.lldab.mrmphone.data.PageRef
import io.lldab.mrmphone.data.SeriesDetail
import io.lldab.mrmphone.data.formatChapterNumber
import io.lldab.mrmphone.ui.common.Hint
import io.lldab.mrmphone.ui.common.MrmButton
import io.lldab.mrmphone.ui.common.MrmOutlinedButton
import io.lldab.mrmphone.ui.errorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderState(
    val detail: SeriesDetail? = null,
    val pages: List<PageRef> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val markedRead: Boolean = false,
    val message: String? = null,
)

class ReaderViewModel(private val c: AppContainer, val seriesId: Long, val chapterId: Long) : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Series first (for title/prev/next/prefetch); a failure there must
            // not stop reading a prefetched chapter.
            val detail = runCatching { c.repository.series(seriesId).value }.getOrNull()
            _state.update { it.copy(detail = detail) }
            if (detail != null) c.repository.prefetchAfter(detail, chapterId)
            try {
                val pages = c.repository.pages(chapterId)
                _state.update { it.copy(pages = pages, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = errorText(e)) }
            }
        }
    }

    val chapter: Chapter? get() = _state.value.detail?.chapters?.firstOrNull { it.id == chapterId }

    /** The read rule fired: POST read=true once (queued when offline). */
    fun onLastPageSeen() {
        if (_state.value.markedRead) return
        _state.update { it.copy(markedRead = true) }
        if (chapter?.read == true) return
        viewModelScope.launch {
            val queued = runCatching { c.repository.markRead(chapterId, true) == null }
            _state.update {
                it.copy(message = when {
                    queued.isFailure -> "! ${errorText(queued.exceptionOrNull()!!)}"
                    queued.getOrDefault(false) -> "marked read · queued (offline)"
                    else -> "marked read"
                })
            }
        }
    }

    fun neighbour(offset: Int): Chapter? {
        val list = _state.value.detail?.chapters?.sortedBy { it.number } ?: return null
        val i = list.indexOfFirst { it.id == chapterId }
        if (i < 0) return null
        return list.getOrNull(i + offset)
    }
}

@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit, onChapter: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var barVisible by remember { mutableStateOf(true) }

    // Keep the screen on while the reader is open.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val ch = state.detail?.chapters?.firstOrNull { it.id == vm.chapterId }
    val title = ch?.let { "ch ${formatChapterNumber(it.number)}" + (it.title?.let { t -> " · $t" } ?: "") } ?: "chapter"
    val prev = state.detail?.let { vm.neighbour(-1) }
    val next = state.detail?.let { vm.neighbour(+1) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.loading -> Hint("loading pages…", Modifier.align(Alignment.Center))
            state.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                Hint("! ${state.error}", color = MaterialTheme.colorScheme.error)
                MrmButton("retry", { vm.load() }, Modifier.padding(top = 12.dp))
            }
            else -> PageStrip(
                pages = state.pages,
                onTap = { barVisible = !barVisible },
                onScrollDown = { barVisible = false },
                onLastPageSeen = vm::onLastPageSeen,
                content = {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp).windowInsetsPadding(WindowInsets.navigationBars),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Hint("end of $title")
                        state.message?.let { Hint(it, Modifier.padding(top = 4.dp)) }
                        Spacer(Modifier.height(16.dp))
                        if (next != null) {
                            MrmButton("next · ch ${formatChapterNumber(next.number)}", { onChapter(next.id) })
                        } else {
                            Hint("no next chapter")
                        }
                        MrmOutlinedButton("back to series", onBack, Modifier.padding(top = 8.dp))
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                BarText("‹ prev", enabled = prev != null) { prev?.let { onChapter(it.id) } }
                BarText("next ›", enabled = next != null) { next?.let { onChapter(it.id) } }
            }
        }
    }
}

@Composable
private fun BarText(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .then(if (enabled) Modifier.pointerInput(Unit) { detectTapGestures { onClick() } } else Modifier)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    )
}

/**
 * Continuous vertical strip. Pinch zooms (1–4x) the whole strip around the
 * gesture, a two-finger drag pans, double tap toggles 2x, single tap toggles
 * the bar, and scrolling down hides it. While zoomed, one-finger drags still
 * scroll the list vertically and move the strip sideways.
 */
@Composable
private fun PageStrip(
    pages: List<PageRef>,
    onTap: () -> Unit,
    onScrollDown: () -> Unit,
    onLastPageSeen: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOnScrollDown by rememberUpdatedState(onScrollDown)
    // Uses what the list consumed, so pulling at the end of the chapter does
    // not hide a bar the reader just tapped open.
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < 0f) currentOnScrollDown()
                return Offset.Zero
            }
        }
    }
    val listState = rememberLazyListState()
    val loaded = remember { mutableStateSetOf<Int>() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }

    fun clampX(x: Float): Float {
        val max = (width * (scale - 1f)) / 2f
        return x.coerceIn(-max, max)
    }

    // The read rule, evaluated on what is actually on screen and loaded.
    LaunchedEffect(pages) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .map { it.index }
                .filter { it < pages.size && it in loaded }
                .toSet()
        }.distinctUntilChanged().collect { visible ->
            if (ReadRule.shouldMarkRead(visible, pages.size)) onLastPageSeen()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width.toFloat() }
            .nestedScroll(hideOnScroll)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            offsetX = clampX(offsetX + pan.x)
                            listState.dispatchRawDelta(-pan.y / scale)
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && scale > 1f) {
                            // Sideways only; the list handles the vertical part.
                            val pan = event.calculatePan()
                            offsetX = clampX(offsetX + pan.x)
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offsetX = 0f } else scale = 2f
                    },
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            itemsIndexed(pages, key = { _, p -> p.index }) { i, page ->
                AsyncImage(
                    model = page.model,
                    contentDescription = "page ${i + 1}",
                    contentScale = ContentScale.FillWidth,
                    onSuccess = { loaded.add(i) },
                    // A full-screen placeholder until the image arrives, so
                    // unloaded pages cannot all fit on screen at once.
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (i in loaded) Modifier else Modifier.heightIn(min = screenHeight)),
                )
            }
            item(key = "footer") { content() }
        }
    }
}
