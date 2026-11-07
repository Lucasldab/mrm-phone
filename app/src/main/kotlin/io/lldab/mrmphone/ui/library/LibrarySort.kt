package io.lldab.mrmphone.ui.library

import io.lldab.mrmphone.data.Series
import io.lldab.mrmphone.data.SeriesStatus

/**
 * Library sort modes, in cycle order. New is the default; title, unread and
 * status are the TUI's modes (app.rs visible_manhwa).
 */
enum class SortMode(val label: String) {
    New("new"),
    Title("title"),
    Unread("unread"),
    Status("status"),
    ;

    fun next(): SortMode = entries[(ordinal + 1) % entries.size]
}

object LibrarySort {
    /** Filter by case-insensitive title substring, then sort. */
    fun apply(list: List<Series>, mode: SortMode, query: String): List<Series> {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) list else list.filter { q in it.title.lowercase() }
        return when (mode) {
            // Series with something to read first, then the most recent release
            // (ISO dates compare as text; undated last), then title.
            SortMode.New -> filtered.sortedWith(
                compareByDescending<Series> { it.unread > 0 }
                    .thenByDescending { it.latestRelease ?: "" }
                    .thenBy { it.title.lowercase() },
            )
            SortMode.Title -> filtered.sortedBy { it.title.lowercase() }
            SortMode.Unread -> filtered.sortedWith(compareByDescending<Series> { it.unread }.thenBy { it.title })
            SortMode.Status -> filtered.sortedWith(compareBy<Series> { SeriesStatus.rank(it.status) }.thenBy { it.title })
        }
    }
}
