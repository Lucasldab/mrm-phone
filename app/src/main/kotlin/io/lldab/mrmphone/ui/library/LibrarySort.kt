package io.lldab.mrmphone.ui.library

import io.lldab.mrmphone.data.Series
import io.lldab.mrmphone.data.SeriesStatus

/** The TUI's sort modes (app.rs visible_manhwa), cycled in the same order. */
enum class SortMode(val label: String) {
    Title("title"),
    Unread("unread"),
    Status("status"),
    ;

    fun next(): SortMode = entries[(ordinal + 1) % entries.size]
}

object LibrarySort {
    /** Filter by case-insensitive title substring, then sort like the TUI. */
    fun apply(list: List<Series>, mode: SortMode, query: String): List<Series> {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) list else list.filter { q in it.title.lowercase() }
        return when (mode) {
            SortMode.Title -> filtered.sortedBy { it.title.lowercase() }
            SortMode.Unread -> filtered.sortedWith(compareByDescending<Series> { it.unread }.thenBy { it.title })
            SortMode.Status -> filtered.sortedWith(compareBy<Series> { SeriesStatus.rank(it.status) }.thenBy { it.title })
        }
    }
}
