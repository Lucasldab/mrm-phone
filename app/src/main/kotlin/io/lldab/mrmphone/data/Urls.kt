package io.lldab.mrmphone.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Urls {
    const val DEFAULT_SERVER = "http://hearth.tailda34b8.ts.net:8102"

    /**
     * Resolve a server path like "/api/series/3/cover" against the server URL.
     * Absolute http(s) URLs pass through. A path prefix on the server URL
     * (e.g. behind a reverse proxy at /mrm) is kept, unlike RFC 3986 resolution.
     */
    fun resolve(server: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return server.trim().trimEnd('/') + "/" + path.trimStart('/')
    }

    /** Normalise what the user typed in Settings; null if it is not an http(s) URL. */
    fun normaliseServer(input: String): String? {
        val t = input.trim().trimEnd('/')
        if (t.isEmpty()) return null
        val withScheme = if ("://" in t) t else "http://$t"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        return withScheme
            .takeIf { url.scheme == "http" || url.scheme == "https" }
    }

    /** True when [url] points at the configured server, i.e. may carry the token. */
    fun sameOrigin(url: HttpUrl, server: String): Boolean {
        val s = server.trim().toHttpUrlOrNull() ?: return false
        return url.scheme == s.scheme && url.host == s.host && url.port == s.port
    }
}
