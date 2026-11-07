package io.lldab.mrmphone.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import java.io.File

class AppContainer(context: Context, scope: CoroutineScope) {
    private val app = context.applicationContext
    val settingsStore = SettingsStore(app)

    // Interceptors read settings synchronously; one blocking read at start-up
    // (a small preferences file) so the first request already has the token.
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        scope, SharingStarted.Eagerly, runBlocking { settingsStore.current() },
    )

    val http = buildHttpClient { settings.value }
    val api = ApiClient(http) { settings.value }
    val scheduler = WorkManagerScheduler(app)
    val repository = Repository(
        api = api,
        queue = ReadQueue(File(app.filesDir, "read-queue.json")),
        pageCache = PageCache(File(app.cacheDir, "pages")),
        responses = ResponseCache(File(app.filesDir, "responses")),
        scheduler = scheduler,
        settings = { settings.value },
    )
}
