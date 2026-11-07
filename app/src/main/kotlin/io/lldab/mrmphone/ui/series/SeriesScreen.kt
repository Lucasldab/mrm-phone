package io.lldab.mrmphone.ui.series

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.lldab.mrmphone.data.AppContainer
import io.lldab.mrmphone.data.Chapter
import io.lldab.mrmphone.data.SeriesDetail
import io.lldab.mrmphone.data.SeriesStatus
import io.lldab.mrmphone.data.Urls
import io.lldab.mrmphone.data.formatChapterNumber
import io.lldab.mrmphone.ui.common.BarIcon
import io.lldab.mrmphone.ui.common.Cover
import io.lldab.mrmphone.ui.common.Hint
import io.lldab.mrmphone.ui.common.MrmButton
import io.lldab.mrmphone.ui.common.MrmTopBar
import io.lldab.mrmphone.ui.errorText
import io.lldab.mrmphone.ui.theme.READ_ALPHA
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesState(
    val detail: SeriesDetail? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class SeriesViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state.asStateFlow()

    fun coverUrl(path: String) = Urls.resolve(c.settings.value.serverUrl, path)

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val r = c.repository.series(id)
                _state.update { it.copy(detail = r.value, offline = r.offline, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = errorText(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** One poll of this series on the server (TUI `R`). */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, message = "refreshing…") }
        viewModelScope.launch {
            try {
                val d = c.repository.refresh(id)
                val before = _state.value.detail?.chapters?.size ?: d.chapters.size
                val added = d.chapters.size - before
                _state.update {
                    it.copy(detail = d, error = null, offline = false,
                        message = if (added > 0) "+$added new" else "no new chapters")
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "! ${errorText(e)}") }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun setStatus(s: SeriesStatus) {
        viewModelScope.launch {
            try {
                val series = c.repository.setStatus(id, s.wire)
                _state.update { st -> st.copy(detail = st.detail?.withSeries(series)) }
                // up_to_date marks everything read on the server: reload chapters.
                if (s == SeriesStatus.UpToDate) load()
            } catch (e: Exception) {
                _state.update { it.copy(message = "! ${errorText(e)}") }
            }
        }
    }

    fun toggleRead(ch: Chapter) {
        val target = !ch.read
        // Optimistic: flip it now, the server (or the queue) catches up.
        _state.update { st ->
            st.copy(detail = st.detail?.let { d ->
                d.copy(chapters = d.chapters.map { if (it.id == ch.id) it.copy(read = target) else it })
            })
        }
        viewModelScope.launch {
            try {
                val series = c.repository.markRead(ch.id, target)
                val label = "ch ${formatChapterNumber(ch.number)} ${if (target) "read" else "unread"}"
                _state.update { st ->
                    st.copy(
                        detail = if (series != null) st.detail?.withSeries(series) else st.detail,
                        message = if (series == null) "$label · queued (offline)" else label,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "! ${errorText(e)}") }
                load()
            }
        }
    }
}

@Composable
fun SeriesScreen(vm: SeriesViewModel, onBack: () -> Unit, onRead: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val d = state.detail

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MrmTopBar(title = d?.title ?: "…", onBack = onBack) {
            BarIcon(Icons.Default.Refresh, "refresh from source") { vm.refresh() }
        }
        state.message?.let { Hint(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        if (state.offline) Hint("offline · showing the last copy", Modifier.padding(horizontal = 12.dp))
        state.error?.let {
            Hint("! $it", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        }
        if (d == null) {
            if (state.loading) Hint("loading…", Modifier.padding(12.dp))
            return@Column
        }
        // Newest first; "continue" is the oldest unread.
        val newestFirst = remember(d.chapters) { d.chapters.sortedByDescending { it.number } }
        val firstUnread = remember(d.chapters) { d.chapters.sortedBy { it.number }.firstOrNull { !it.read } }

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") { Header(d, vm, firstUnread, onRead) }
            item(key = "chapters-label") {
                Text(
                    "chapters  (${d.chapters.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(newestFirst, key = { it.id }) { ch ->
                ChapterRow(ch, onClick = { onRead(ch.id) }, onLongClick = { vm.toggleRead(ch) })
            }
            item(key = "bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun Header(d: SeriesDetail, vm: SeriesViewModel, firstUnread: Chapter?, onRead: (Long) -> Unit) {
    val c = MaterialTheme.colorScheme
    var picker by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(12.dp)) {
        Row {
            Cover(vm.coverUrl(d.cover), Modifier.width(110.dp).height(165.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(d.title, style = MaterialTheme.typography.titleLarge, color = c.onBackground)
                Spacer(Modifier.height(8.dp))
                Box {
                    Text(
                        "status  ${SeriesStatus.label(d.status)} ▾",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.primary,
                        modifier = Modifier.clickable { picker = true }.padding(vertical = 4.dp),
                    )
                    DropdownMenu(
                        expanded = picker,
                        onDismissRequest = { picker = false },
                        shape = RectangleShape,
                        containerColor = c.surfaceVariant,
                    ) {
                        SeriesStatus.entries.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (s.wire == d.status) "> " else "  ") + s.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = { picker = false; vm.setStatus(s) },
                            )
                        }
                    }
                }
                Meta("pub", d.pubStatus ?: "?")
                Meta("source", d.source)
                Meta("unread", "${d.unread} / ${d.total}")
            }
        }
        if (d.warn()) {
            Hint("⚠ ${d.note ?: "failing to poll"}", Modifier.padding(top = 8.dp), color = c.error)
        }
        d.description?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).clickable { expanded = !expanded },
            )
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (firstUnread != null) {
                MrmButton("continue · ch ${formatChapterNumber(firstUnread.number)}", { onRead(firstUnread.id) })
            } else {
                Hint("all caught up")
            }
        }
    }
    HorizontalDivider(color = c.surfaceVariant)
}

private fun SeriesDetail.warn() = failing || note != null

@Composable
private fun Meta(key: String, value: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(key.padEnd(7), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(ch: Chapter, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (ch.read) READ_ALPHA else 1f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(if (ch.read) c.surfaceVariant else c.primary))
        Text(
            formatChapterNumber(ch.number).padStart(5),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            ch.title ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        ch.releasedAt?.let {
            Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }
    }
}
