package io.lldab.mrmphone.data

import io.lldab.mrmphone.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var repo: Repository
    private var settings = AppSettings()
    private val scheduled = mutableListOf<String>()

    private val scheduler = object : WorkScheduler {
        override fun scheduleFlush() { scheduled += "flush" }
        override fun schedulePrefetch(seriesId: Long, chapterIds: List<Long>) { scheduled += "prefetch:$chapterIds" }
    }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        settings = AppSettings(serverUrl = server.url("/").toString().trimEnd('/'), token = "t")
        repo = Repository(
            api = ApiClient(buildHttpClient { settings }) { settings },
            queue = ReadQueue(File(tmp.root, "q.json")),
            pageCache = PageCache(File(tmp.root, "pages")),
            responses = ResponseCache(File(tmp.root, "responses")),
            scheduler = scheduler,
            settings = { settings },
        )
    }

    @After fun tearDown() = runCatching { server.shutdown() }.let { }

    private fun json(name: String) = MockResponse().setBody(Fixtures.read(name))

    @Test fun mark_read_offline_is_queued_and_flush_scheduled() = runTest {
        server.shutdown() // hearth unreachable
        val result = repo.markRead(103, true)
        assertNull(result)
        assertEquals(listOf(103L to true), repo.queue.pending().map { it.chapterId to it.read })
        assertEquals(listOf("flush"), scheduled)
    }

    @Test fun mark_read_online_clears_a_stale_queued_mark() = runTest {
        repo.queue.enqueue(103, false)
        server.enqueue(json("read_response.json"))
        val s = repo.markRead(103, true)
        assertEquals(1, s!!.unread)
        assertTrue(repo.queue.isEmpty())
    }

    @Test fun series_falls_back_to_last_copy_offline_with_queue_overlay() = runTest {
        server.enqueue(json("series_detail.json"))
        assertEquals(false, repo.series(12).offline)
        server.shutdown()
        repo.queue.enqueue(104, true)
        val r = repo.series(12)
        assertTrue(r.offline)
        assertTrue(r.value.chapters.first { it.id == 104L }.read)
    }

    @Test fun prefetch_schedules_next_two_unread() = runTest {
        server.enqueue(json("series_detail.json"))
        val d = repo.series(12).value
        repo.prefetchAfter(d, 102)
        assertEquals(listOf("prefetch:[103, 104]"), scheduled)
        settings = settings.copy(prefetch = false)
        repo.prefetchAfter(d, 102)
        assertEquals(1, scheduled.size)
    }

    @Test fun download_chapter_fills_cache_and_pages_then_come_from_disk() = runTest {
        server.enqueue(json("pages.json"))
        repeat(3) { server.enqueue(MockResponse().setBody("page$it")) }
        repo.downloadChapter(seriesId = 12, chapterId = 103, protect = setOf(103))
        assertTrue(repo.pageCache.isComplete(103))
        server.shutdown()
        val pages = repo.pages(103)
        assertEquals(3, pages.size)
        assertTrue(pages.all { it.model is File })
        assertEquals("page2", (pages[2].model as File).readText())
    }

    @Test fun online_pages_are_absolute_server_urls() = runTest {
        server.enqueue(json("pages.json"))
        val pages = repo.pages(103)
        assertEquals(settings.serverUrl + "/api/chapters/103/pages/0", pages[0].model)
    }

    @Test fun delete_series_clears_its_local_state_only() = runTest {
        server.enqueue(json("series_detail.json"))
        val d = repo.series(12).value
        repo.queue.enqueue(104, true)
        repo.pageCache.writeMeta(PageCache.Meta(103, seriesId = 12, pageCount = 1, complete = true))
        repo.pageCache.writePage(103, 0, "p".toByteArray())
        repo.pageCache.writeMeta(PageCache.Meta(900, seriesId = 99, pageCount = 1, complete = true))
        repo.pageCache.writePage(900, 0, "q".toByteArray())

        server.enqueue(MockResponse().setResponseCode(204))
        repo.deleteSeries(d)

        val req = server.let { it.takeRequest(); it.takeRequest() }
        assertEquals("DELETE", req.method)
        assertEquals("/api/series/12", req.path)
        assertTrue(repo.queue.isEmpty())
        assertFalse(repo.pageCache.isComplete(103))
        assertTrue("other series keeps its pages", repo.pageCache.isComplete(900))
        server.shutdown()
        assertThrows(java.io.IOException::class.java) { kotlinx.coroutines.runBlocking { repo.series(12) } }
    }

    @Test fun delete_series_offline_fails_and_keeps_everything() = runTest {
        server.enqueue(json("series_detail.json"))
        val d = repo.series(12).value
        repo.queue.enqueue(104, true)
        server.shutdown()
        assertThrows(java.io.IOException::class.java) { kotlinx.coroutines.runBlocking { repo.deleteSeries(d) } }
        assertFalse(repo.queue.isEmpty())
        assertTrue(repo.series(12).offline)
    }
}
