package io.lldab.mrmphone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val serverUrl: String = Urls.DEFAULT_SERVER,
    val token: String = "",
    val prefetch: Boolean = true,
    val cacheCapMb: Int = DEFAULT_CACHE_MB,
) {
    val configured: Boolean get() = token.isNotBlank()

    companion object {
        const val DEFAULT_CACHE_MB = 500
        val CACHE_CHOICES_MB = listOf(250, 500, 1000, 2000)
    }
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val server = stringPreferencesKey("server_url")
        val token = stringPreferencesKey("token")
        val prefetch = booleanPreferencesKey("prefetch")
        val cacheCap = intPreferencesKey("cache_cap_mb")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            serverUrl = p[Keys.server] ?: Urls.DEFAULT_SERVER,
            token = p[Keys.token] ?: "",
            prefetch = p[Keys.prefetch] ?: true,
            cacheCapMb = p[Keys.cacheCap] ?: AppSettings.DEFAULT_CACHE_MB,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setServer(url: String) = store.edit { it[Keys.server] = url }
    suspend fun setToken(token: String) = store.edit { it[Keys.token] = token.trim() }
    suspend fun setPrefetch(on: Boolean) = store.edit { it[Keys.prefetch] = on }
    suspend fun setCacheCap(mb: Int) = store.edit { it[Keys.cacheCap] = mb }
}
