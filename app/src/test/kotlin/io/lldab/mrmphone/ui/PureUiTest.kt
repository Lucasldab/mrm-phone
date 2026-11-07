package io.lldab.mrmphone.ui

import io.lldab.mrmphone.DeepLink
import io.lldab.mrmphone.data.Series
import io.lldab.mrmphone.ui.library.LibrarySort
import io.lldab.mrmphone.ui.library.LibraryState
import io.lldab.mrmphone.ui.library.SortMode
import io.lldab.mrmphone.ui.reader.ReadRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadRuleTest {
    @Test fun last_page_on_screen_marks_read() {
        assertTrue(ReadRule.shouldMarkRead(listOf(8, 9), pageCount = 10))
    }

    @Test fun anything_short_of_the_last_page_does_not() {
        assertFalse(ReadRule.shouldMarkRead(listOf(7, 8), pageCount = 10))
        assertFalse(ReadRule.shouldMarkRead(emptyList(), pageCount = 10))
    }

    @Test fun single_page_chapter_is_read_when_shown() {
        assertTrue(ReadRule.shouldMarkRead(listOf(0), pageCount = 1))
    }

    @Test fun no_pages_never_marks() {
        assertFalse(ReadRule.shouldMarkRead(listOf(0), pageCount = 0))
    }
}

class DeepLinkTest {
    @Test fun parses_series_and_library() {
        assertEquals(DeepLink.Series(42), DeepLink.parse("mrmphone://series/42"))
        assertEquals(DeepLink.Library, DeepLink.parse("mrmphone://library"))
        assertEquals(DeepLink.Library, DeepLink.parse("mrmphone://library/"))
    }

    @Test fun rejects_everything_else() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse("mrmphone://series/abc"))
        assertNull(DeepLink.parse("mrmphone://series/-1"))
        assertNull(DeepLink.parse("mrmphone://series/1/2"))
        assertNull(DeepLink.parse("https://series/1"))
        assertNull(DeepLink.parse("mrmphone://admin"))
    }
}

class LibrarySortTest {
    private fun s(id: Long, title: String, status: String, unread: Int) =
        Series(id, title, "asura", status, "ongoing", unread, 10, "/c", false, null)

    private val list = listOf(
        s(1, "beta", "paused", 2),
        s(2, "Alpha", "dropped", 0),
        s(3, "gamma", "reading", 5),
        s(4, "Delta", "up_to_date", 2),
    )

    @Test fun title_sort_is_case_insensitive() {
        assertEquals(listOf(2L, 1L, 4L, 3L), LibrarySort.apply(list, SortMode.Title, "").map { it.id })
    }

    @Test fun unread_sort_desc_then_title() {
        assertEquals(listOf(3L, 4L, 1L, 2L), LibrarySort.apply(list, SortMode.Unread, "").map { it.id })
    }

    @Test fun status_sort_uses_tui_rank() {
        assertEquals(listOf(3L, 4L, 1L, 2L), LibrarySort.apply(list, SortMode.Status, "").map { it.id })
    }

    @Test fun filter_is_case_insensitive_substring() {
        assertEquals(listOf(3L), LibrarySort.apply(list, SortMode.Title, "MM").map { it.id })
        assertEquals(listOf(4L), LibrarySort.apply(list, SortMode.Title, " delta ").map { it.id })
    }

    @Test fun sort_cycles_from_new_through_the_tui_modes() {
        assertEquals(SortMode.Title, SortMode.New.next())
        assertEquals(SortMode.Unread, SortMode.Title.next())
        assertEquals(SortMode.New, SortMode.Status.next())
    }

    @Test fun new_sort_puts_unread_first_then_latest_release_then_title() {
        fun r(id: Long, title: String, unread: Int, latest: String?) =
            Series(id, title, "asura", "reading", "ongoing", unread, 10, "/c", false, null, latest)
        val shelf = listOf(
            r(1, "old unread", 1, "2026-09-01"),
            r(2, "new, all read", 0, "2026-11-07"),
            r(3, "new unread", 4, "2026-11-06"),
            r(4, "undated unread", 2, null),
            r(5, "b same day", 1, "2026-11-06"),
            r(6, "undated read", 0, null),
        )
        assertEquals(
            listOf(5L, 3L, 1L, 4L, 2L, 6L),
            LibrarySort.apply(shelf, SortMode.New, "").map { it.id },
        )
    }

    @Test fun library_starts_on_the_new_sort() {
        assertEquals(SortMode.New, LibraryState().sort)
    }
}
