package io.lldab.mrmphone.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.lldab.mrmphone.data.ApiException
import io.lldab.mrmphone.data.AppContainer
import io.lldab.mrmphone.data.SearchHit
import io.lldab.mrmphone.data.Urls
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

data class SearchState(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val sourceErrors: List<String> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val adding: String? = null,
    val error: String? = null,
)

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** cover_url may be a server path or an upstream URL; the token only goes to the server. */
    fun coverUrl(hit: SearchHit): String? = hit.coverUrl?.let { Urls.resolve(c.settings.value.serverUrl, it) }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty() || _state.value.searching) return
        _state.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            try {
                val r = c.repository.search(q)
                _state.update { it.copy(hits = r.hits, sourceErrors = r.errors, searched = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = errorText(e)) }
            } finally {
                _state.update { it.copy(searching = false) }
            }
        }
    }

    fun add(hit: SearchHit, onOpen: (Long) -> Unit) {
        if (_state.value.adding != null) return
        _state.update { it.copy(adding = hit.sourceUrl, error = null) }
        viewModelScope.launch {
            try {
                val d = c.repository.add(hit)
                onOpen(d.id)
            } catch (e: ApiException) {
                if (e.code == 409) {
                    // Already there: open it if it can be found by title + source.
                    val existing = runCatching { c.repository.library().value }.getOrNull()
                        ?.firstOrNull { it.source == hit.source && it.title.equals(hit.title, ignoreCase = true) }
                    if (existing != null) onOpen(existing.id)
                    else _state.update { it.copy(error = "already in library") }
                } else {
                    _state.update { it.copy(error = errorText(e)) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = errorText(e)) }
            } finally {
                _state.update { it.copy(adding = null) }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!state.searched) runCatching { focus.requestFocus() } }
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(c.background)) {
        MrmTopBar(title = "search", onBack = onBack)
        Row(
            Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("> ", style = MaterialTheme.typography.bodyMedium, color = c.primary)
            Box(Modifier.weight(1f)) {
                if (state.query.isEmpty()) Text("title", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                BasicTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.onBackground),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            MrmButton(if (state.searching) "…" else "search", { vm.search() }, enabled = !state.searching)
        }
        state.error?.let { Hint("! $it", Modifier.padding(12.dp), color = c.error) }
        state.sourceErrors.forEach { Hint("! $it", Modifier.padding(horizontal = 12.dp, vertical = 2.dp), color = c.error) }
        if (state.searched && state.hits.isEmpty() && !state.searching) Hint("no hits", Modifier.padding(12.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(state.hits, key = { it.source + "|" + it.sourceUrl }) { hit ->
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(vm.coverUrl(hit), Modifier.width(60.dp).height(90.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(hit.title, style = MaterialTheme.typography.titleMedium, color = c.onBackground,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            MonoBadge(hit.source)
                            Text(" ${hit.pubStatus ?: ""}", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                        }
                    }
                    MrmButton(
                        if (state.adding == hit.sourceUrl) "…" else "add",
                        { vm.add(hit, onOpen) },
                        enabled = state.adding == null,
                    )
                }
                HorizontalDivider(color = c.surfaceVariant)
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}
