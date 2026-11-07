package io.lldab.mrmphone.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.lldab.mrmphone.BuildConfig
import io.lldab.mrmphone.data.AppContainer
import io.lldab.mrmphone.data.AppSettings
import io.lldab.mrmphone.data.Urls
import io.lldab.mrmphone.ui.common.ChipRow
import io.lldab.mrmphone.ui.common.Hint
import io.lldab.mrmphone.ui.common.MonoChip
import io.lldab.mrmphone.ui.common.MrmButton
import io.lldab.mrmphone.ui.common.MrmOutlinedButton
import io.lldab.mrmphone.ui.common.MrmTopBar
import io.lldab.mrmphone.ui.common.SectionLabel
import io.lldab.mrmphone.ui.errorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUi(
    val testing: Boolean = false,
    val testResult: String? = null,
    val testOk: Boolean = false,
    val cacheBytes: Long = 0,
    val queued: Int = 0,
    val saved: String? = null,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    fun refreshStats() {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { c.repository.pageCache.totalBytes() }
            val queued = withContext(Dispatchers.IO) { c.repository.queue.pending().size }
            _ui.update { it.copy(cacheBytes = bytes, queued = queued) }
        }
    }

    fun saveConnection(server: String, token: String) {
        val url = Urls.normaliseServer(server)
        if (url == null) {
            _ui.update { it.copy(saved = "! not an http(s) URL") }
            return
        }
        viewModelScope.launch {
            c.settingsStore.setServer(url)
            if (token.isNotBlank()) c.settingsStore.setToken(token)
            _ui.update { it.copy(saved = "saved") }
        }
    }

    fun setPrefetch(on: Boolean) = viewModelScope.launch { c.settingsStore.setPrefetch(on) }

    fun setCap(mb: Int) = viewModelScope.launch {
        c.settingsStore.setCacheCap(mb)
        withContext(Dispatchers.IO) { c.repository.pageCache.enforce(mb * 1024L * 1024L) }
        refreshStats()
    }

    fun clearCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) { c.repository.pageCache.clear() }
        refreshStats()
    }

    fun flushNow() {
        c.scheduler.scheduleFlush()
        _ui.update { it.copy(saved = "flush scheduled") }
    }

    /** Health (no auth) then the library (auth) so a wrong token shows as such. */
    fun test() {
        _ui.update { it.copy(testing = true, testResult = null) }
        viewModelScope.launch {
            val result = try {
                val h = c.repository.health()
                val n = c.repository.library().value.size
                true to "ok · mrm ${h.version ?: "?"} · $n series"
            } catch (e: Exception) {
                false to errorText(e)
            }
            _ui.update { it.copy(testing = false, testOk = result.first, testResult = result.second) }
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshStats() }
    var server by rememberSaveable { mutableStateOf(s.serverUrl) }
    var token by rememberSaveable { mutableStateOf("") }
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(c.background)) {
        MrmTopBar(title = "settings", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("server")
            Field("url", server, { server = it }, keyboard = KeyboardType.Uri)
            Field(
                "token",
                token,
                { token = it },
                placeholder = if (s.token.isBlank()) "not set" else "set · leave empty to keep",
                password = true,
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                MrmButton("save", { vm.saveConnection(server, token); token = "" })
                Spacer(Modifier.padding(4.dp))
                MrmOutlinedButton(if (ui.testing) "testing…" else "test connection", { vm.test() }, enabled = !ui.testing)
            }
            ui.saved?.let { Hint(it, Modifier.padding(top = 6.dp)) }
            ui.testResult?.let {
                Hint(
                    (if (ui.testOk) "" else "! ") + it,
                    Modifier.padding(top = 6.dp),
                    color = if (ui.testOk) c.primary else c.error,
                )
            }

            SectionLabel("prefetch")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "download the next ${io.lldab.mrmphone.data.Prefetch.COUNT} unread chapters",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = s.prefetch,
                    onCheckedChange = { vm.setPrefetch(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.primary,
                        checkedThumbColor = c.onBackground,
                        uncheckedTrackColor = c.surfaceVariant,
                        uncheckedThumbColor = c.onSurfaceVariant,
                        uncheckedBorderColor = c.surfaceVariant,
                    ),
                )
            }
            Hint("cache cap (MB)", Modifier.padding(top = 10.dp, bottom = 6.dp))
            ChipRow {
                AppSettings.CACHE_CHOICES_MB.forEach { mb ->
                    MonoChip("$mb", selected = s.cacheCapMb == mb) { vm.setCap(mb) }
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "used ${formatMb(ui.cacheBytes)} / ${s.cacheCapMb} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                MrmOutlinedButton("clear", { vm.clearCache() })
            }

            SectionLabel("offline queue")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ui.queued == 0) "no queued read marks" else "${ui.queued} read mark(s) waiting",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (ui.queued > 0) MrmOutlinedButton("send now", { vm.flushNow() })
            }

            SectionLabel("about")
            Hint("mrm-phone ${BuildConfig.VERSION_NAME} · io.lldab.mrmphone")
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.padding(12.dp))
        }
    }
}

private fun formatMb(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.padEnd(6), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        Box(
            Modifier
                .weight(1f)
                .border(1.dp, c.surfaceVariant, RectangleShape)
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = c.onBackground),
                cursorBrush = SolidColor(c.primary),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboard,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
