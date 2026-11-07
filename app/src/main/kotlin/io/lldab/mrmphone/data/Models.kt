package io.lldab.mrmphone.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Wire types of `mrm serve` (spec 2026-11-07, "API contract"). snake_case on
// the wire; @SerialName keeps Kotlin camelCase.

/** Library row. `cover` is a server-relative path ("/api/series/ID/cover"). */
@Serializable
data class Series(
    val id: Long,
    val title: String,
    val source: String,
    val status: String,
    @SerialName("pub_status") val pubStatus: String? = null,
    val unread: Int,
    val total: Int,
    val cover: String,
    val failing: Boolean = false,
    /** last_error while failing, else null. */
    val note: String? = null,
) {
    /** The ⚠ marker in the library: failing, or the server left a note. */
    val warn: Boolean get() = failing || note != null
}

/** Series + description + chapters (ascending by number). */
@Serializable
data class SeriesDetail(
    val id: Long,
    val title: String,
    val source: String,
    val status: String,
    @SerialName("pub_status") val pubStatus: String? = null,
    val unread: Int,
    val total: Int,
    val cover: String,
    val failing: Boolean = false,
    val note: String? = null,
    val description: String? = null,
    val chapters: List<Chapter> = emptyList(),
) {
    fun toSeries() = Series(id, title, source, status, pubStatus, unread, total, cover, failing, note)

    /** Copy with the series fields replaced (after a status/read call returned a fresh Series). */
    fun withSeries(s: Series) = copy(
        title = s.title, source = s.source, status = s.status, pubStatus = s.pubStatus,
        unread = s.unread, total = s.total, cover = s.cover, failing = s.failing, note = s.note,
    )
}

@Serializable
data class Chapter(
    val id: Long,
    val number: Double,
    val title: String? = null,
    @SerialName("released_at") val releasedAt: String? = null,
    val read: Boolean,
)

@Serializable
data class SearchHit(
    val title: String,
    val source: String,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("pub_status") val pubStatus: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
)

/** GET /api/search: an object, not a bare list. */
@Serializable
data class SearchResponse(
    val hits: List<SearchHit> = emptyList(),
    val errors: List<String> = emptyList(),
)

@Serializable
data class Health(val ok: Boolean, val version: String? = null)

@Serializable
data class ReadResponse(
    @SerialName("chapter_id") val chapterId: Long,
    val read: Boolean,
    val series: Series,
)

@Serializable
data class PagesResponse(
    @SerialName("chapter_id") val chapterId: Long,
    val pages: List<String>,
)

@Serializable
data class ErrorBody(val error: String)

@Serializable
internal data class StatusRequest(val status: String)

@Serializable
internal data class ReadRequest(val read: Boolean)

@Serializable
internal data class AddSeriesRequest(
    val source: String,
    @SerialName("source_url") val sourceUrl: String,
)

/** `Status::as_str` values from mrm's types.rs, in the TUI's sort_rank order. */
enum class SeriesStatus(val wire: String, val label: String) {
    Reading("reading", "reading"),
    UpToDate("up_to_date", "up to date"),
    LookedInto("looked_into", "looked into"),
    Paused("paused", "paused"),
    Completed("completed", "completed"),
    Dropped("dropped", "dropped"),
    ;

    companion object {
        fun fromWire(s: String): SeriesStatus? = entries.firstOrNull { it.wire == s }
        fun label(s: String): String = fromWire(s)?.label ?: s.replace('_', ' ')
        /** TUI's Status::sort_rank; unknown values sort last. */
        fun rank(s: String): Int = fromWire(s)?.ordinal ?: entries.size
    }
}

/** One Json for the app: lenient on fields the server may add later. */
val MrmJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Chapter number for display: 12.0 -> "12", 12.5 -> "12.5". */
fun formatChapterNumber(n: Double): String =
    if (n == Math.floor(n) && !n.isInfinite()) n.toLong().toString() else n.toString()
