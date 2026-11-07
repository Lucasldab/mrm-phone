package io.lldab.mrmphone.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.lldab.mrmphone.data.AppContainer
import io.lldab.mrmphone.data.Series
import io.lldab.mrmphone.ui.common.BarIcon
import io.lldab.mrmphone.ui.common.Cover
import io.lldab.mrmphone.ui.common.Hint
import io.lldab.mrmphone.ui.common.MonoBadge
import io.lldab.mrmphone.ui.common.MrmButton
import io.lldab.mrmphone.ui.common.MrmTopBar
import io.lldab.mrmphone.ui.errorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryState(
    val all: List<Series> = emptyList(),
    val query: String = "",
    val sort: SortMode = SortMode.New,
    val loading: Boolean = false,
    val loadedOnce: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
) {
    val visible: List<Series> get() = LibrarySort.apply(all, sort, query)
}

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    val settings = c.settings

    fun load() {
        if (_state.value.loading) return
        if (!c.settings.value.configured) {
            _state.update { it.copy(loadedOnce = true, error = null) }
            return
        }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val r = c.repository.library()
                _state.update { it.copy(all = r.value, offline = r.offline, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = errorText(e)) }
            } finally {
                _state.update { it.copy(loading = false, loadedOnce = true) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun cycleSort() = _state.update { it.copy(sort = it.sort.next()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpen: (Long) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    // Every time the library comes back into view (unread counts move while reading).
    LaunchedEffect(settings.token, settings.serverUrl) { vm.load() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MrmTopBar(title = "mrm") {
            Text(
                text = "sort:${state.sort.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { vm.cycleSort() }.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            BarIcon(Icons.Default.Add, "search and add") { onSearch() }
            BarIcon(Icons.Default.Settings, "settings") { onSettings() }
        }
        FilterLine(state.query, vm::setQuery, count = state.visible.size, total = state.all.size)
        if (state.offline) Hint("offline · showing the last copy", Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        state.error?.let {
            Hint("! $it", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
        }

        PullToRefreshBox(
            isRefreshing = state.loading && state.loadedOnce,
            onRefresh = { vm.load() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !settings.configured -> NotConfigured(onSettings)
                state.all.isEmpty() && state.loadedOnce && state.error == null ->
                    Hint("library is empty · + to search and add", Modifier.padding(16.dp))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    items(state.visible, key = { it.id }) { s ->
                        SeriesCell(s, vm, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterLine(query: String, onChange: (String) -> Unit, count: Int, total: Int) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("/ ", style = MaterialTheme.typography.bodyMedium, color = c.primary)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("filter", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.onBackground),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            if (query.isBlank()) "$total" else "$count/$total",
            style = MaterialTheme.typography.labelMedium,
            color = c.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeriesCell(s: Series, vm: LibraryViewModel, onOpen: (Long) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(4.dp).clickable { onOpen(s.id) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Cover(vmCover(vm, s), Modifier.fillMaxSize())
            if (s.unread > 0) MonoBadge("${s.unread}", Modifier.align(Alignment.TopEnd).padding(3.dp))
            if (s.warn) MonoBadge("⚠", Modifier.align(Alignment.TopStart).padding(3.dp), color = c.error)
        }
        Text(
            text = s.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (s.unread > 0) c.onBackground else c.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
    }
}

private fun vmCover(vm: LibraryViewModel, s: Series): String =
    io.lldab.mrmphone.data.Urls.resolve(vm.settings.value.serverUrl, s.cover)

@Composable
private fun NotConfigured(onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Hint("no token set · enter the server token in settings")
        MrmButton("settings", onSettings, Modifier.padding(top = 12.dp))
    }
}
