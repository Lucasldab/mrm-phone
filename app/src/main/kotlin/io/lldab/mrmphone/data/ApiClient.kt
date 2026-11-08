package io.lldab.mrmphone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** An HTTP error from mrm serve: status code + the `{"error": …}` text. */
class ApiException(val code: Int, message: String) : IOException(message) {
    /** 4xx other than 408/429 will not get better by retrying. */
    val permanent: Boolean get() = code in 400..499 && code != 408 && code != 429
}

/**
 * Adds `Authorization: Bearer <token>` to requests for the configured server
 * only, so the token never leaves for another host (e.g. a search hit's
 * absolute cover_url). Shared by the API client and Coil.
 */
class AuthInterceptor(private val settings: () -> AppSettings) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val s = settings()
        if (s.token.isBlank() || !Urls.sameOrigin(req.url, s.serverUrl)) {
            return chain.proceed(req)
        }
        return chain.proceed(
            req.newBuilder().header("Authorization", "Bearer ${s.token}").build(),
        )
    }
}

fun buildHttpClient(settings: () -> AppSettings): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(settings))
        .connectTimeout(10, TimeUnit.SECONDS)
        // refresh and search scrape upstream on the server: allow for it
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

class ApiClient(
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun url(path: String): String = Urls.resolve(settings().serverUrl, path)

    suspend fun health(): Health = get("/api/health", Health.serializer())

    suspend fun library(): List<Series> = get("/api/library", ListSerializer(Series.serializer()))

    suspend fun series(id: Long): SeriesDetail = get("/api/series/$id", SeriesDetail.serializer())

    suspend fun setStatus(id: Long, status: String): Series =
        post("/api/series/$id/status", body(StatusRequest(status), StatusRequest.serializer()), Series.serializer())

    suspend fun refresh(id: Long): SeriesDetail =
        post("/api/series/$id/refresh", ByteArray(0).toRequestBody(null), SeriesDetail.serializer())

    suspend fun markRead(chapterId: Long, read: Boolean): ReadResponse =
        post("/api/chapters/$chapterId/read", body(ReadRequest(read), ReadRequest.serializer()), ReadResponse.serializer())

    /** Mark every chapter numbered up to this one read. */
    suspend fun markReadThrough(chapterId: Long): ReadThroughResponse =
        post("/api/chapters/$chapterId/read-through", ByteArray(0).toRequestBody(null), ReadThroughResponse.serializer())

    suspend fun pages(chapterId: Long): PagesResponse =
        get("/api/chapters/$chapterId/pages", PagesResponse.serializer())

    suspend fun search(q: String): SearchResponse {
        val full = url("/api/search").toHttpUrlOrNull()
            ?.newBuilder()?.addQueryParameter("q", q)?.build()
            ?: throw IOException("bad server URL")
        return execute(Request.Builder().url(full).get().build(), SearchResponse.serializer())
    }

    suspend fun addSeries(source: String, sourceUrl: String): SeriesDetail =
        post("/api/series", body(AddSeriesRequest(source, sourceUrl), AddSeriesRequest.serializer()), SeriesDetail.serializer())

    /** Remove a series from the library (TUI `d`); its chapters go with it. */
    suspend fun deleteSeries(id: Long): Unit = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url("/api/series/$id")).delete().build()).execute().use { r ->
            if (!r.isSuccessful) throw errorOf(r)
        }
    }

    /** Raw bytes of a server path (page image), for prefetch. */
    suspend fun bytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url(path)).get().build()).execute().use { r ->
            if (!r.isSuccessful) throw errorOf(r)
            r.body?.bytes() ?: throw IOException("empty body")
        }
    }

    private suspend fun <T> get(path: String, s: KSerializer<T>): T =
        execute(Request.Builder().url(url(path)).get().build(), s)

    private suspend fun <T> post(path: String, body: RequestBody, s: KSerializer<T>): T =
        execute(Request.Builder().url(url(path)).post(body).build(), s)

    private fun <B> body(value: B, s: KSerializer<B>): RequestBody =
        MrmJson.encodeToString(s, value).toRequestBody(jsonType)

    private suspend fun <T> execute(req: Request, s: KSerializer<T>): T = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw errorOf(r)
            val text = r.body?.string() ?: throw IOException("empty body")
            try {
                MrmJson.decodeFromString(s, text)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw ApiException(r.code, "unexpected response: ${e.message}")
            } catch (e: IllegalArgumentException) {
                throw ApiException(r.code, "unexpected response: ${e.message}")
            }
        }
    }

    private fun errorOf(r: Response): ApiException {
        val text = runCatching { r.body?.string() }.getOrNull().orEmpty()
        val msg = runCatching { MrmJson.decodeFromString(ErrorBody.serializer(), text).error }
            .getOrNull() ?: text.take(200).ifBlank { "HTTP ${r.code}" }
        return ApiException(r.code, msg)
    }
}
