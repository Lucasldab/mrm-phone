package io.lldab.mrmphone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrefetchCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val MB = 1024L * 1024L

    private fun ch(id: Long, bytes: Long, read: Boolean, at: Long) = CachedChapter(id, bytes, read, at)

    @Test fun nothing_evicted_under_cap() {
        assertTrue(Eviction.choose(listOf(ch(1, 100, true, 1)), capBytes = 100).isEmpty())
    }

    @Test fun read_chapters_go_first_oldest_first() {
        val entries = listOf(
            ch(1, 200 * MB, read = false, at = 1), // oldest, but unread
            ch(2, 200 * MB, read = true, at = 5),
            ch(3, 200 * MB, read = true, at = 3),
        )
        val out = Eviction.choose(entries, capBytes = 500 * MB)
        assertEquals(listOf(3L), out.map { it.chapterId })
    }

    @Test fun unread_evicted_only_after_all_read_ones() {
        val entries = listOf(
            ch(1, 300 * MB, read = false, at = 1),
            ch(2, 300 * MB, read = false, at = 2),
            ch(3, 100 * MB, read = true, at = 9),
        )
        val out = Eviction.choose(entries, capBytes = 300 * MB)
        assertEquals(listOf(3L, 1L), out.map { it.chapterId })
    }

    @Test fun protected_chapters_are_never_evicted() {
        val entries = listOf(ch(1, 400 * MB, true, 1), ch(2, 400 * MB, false, 2))
        val out = Eviction.choose(entries, capBytes = 500 * MB, protect = setOf(1))
        assertEquals(listOf(2L), out.map { it.chapterId })
        assertTrue(Eviction.choose(entries, 100, protect = setOf(1, 2)).isEmpty())
    }

    @Test fun prefetch_targets_are_next_two_unread_after_opened() {
        val chapters = listOf(
            Chapter(1, 1.0, null, null, true),
            Chapter(2, 2.0, null, null, false),
            Chapter(3, 2.5, null, null, true),
            Chapter(4, 3.0, null, null, false),
            Chapter(5, 4.0, null, null, false),
            Chapter(6, 5.0, null, null, false),
        )
        assertEquals(listOf(4L, 5L), Prefetch.targets(chapters, openedId = 2).map { it.id })
        assertEquals(listOf(6L), Prefetch.targets(chapters, openedId = 5).map { it.id })
        assertTrue(Prefetch.targets(chapters, openedId = 6).isEmpty())
        assertTrue(Prefetch.targets(chapters, openedId = 99).isEmpty())
    }

    @Test fun page_cache_enforces_cap_on_disk() {
        var now = 0L
        val cache = PageCache(File(tmp.root, "pages")) { now }
        fun put(id: Long, read: Boolean, at: Long) {
            now = at
            cache.writeMeta(PageCache.Meta(id, seriesId = 1, pageCount = 1, complete = true, read = read, lastAccess = at))
            cache.writePage(id, 0, ByteArray(1000))
        }
        put(1, read = true, at = 1)
        put(2, read = false, at = 2)
        put(3, read = true, at = 3)
        assertTrue(cache.isComplete(1))
        val evicted = cache.enforce(capBytes = cache.totalBytes() - 1)
        assertEquals(listOf(1L), evicted)
        assertFalse(File(tmp.root, "pages/1").exists())
        assertTrue(cache.isComplete(2) && cache.isComplete(3))
    }

    @Test fun page_cache_tracks_read_and_completeness() {
        val cache = PageCache(File(tmp.root, "pages"))
        cache.writeMeta(PageCache.Meta(7, 1, pageCount = 2, complete = true))
        cache.writePage(7, 0, byteArrayOf(1))
        assertFalse("a missing page means incomplete", cache.isComplete(7))
        cache.writePage(7, 1, byteArrayOf(1))
        assertTrue(cache.isComplete(7))
        assertNull(cache.localPage(8, 0))
        cache.setRead(7, true)
        assertTrue(cache.meta(7)!!.read)
    }
}
