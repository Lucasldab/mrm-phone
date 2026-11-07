package io.lldab.mrmphone.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ReadQueueTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun queue(file: File = File(tmp.root, "q.json")) = ReadQueue(file)

    @Test fun persists_across_instances() {
        val f = File(tmp.root, "q.json")
        queue(f).enqueue(1, true)
        queue(f).enqueue(2, false)
        assertEquals(listOf(1L to true, 2L to false), queue(f).pending().map { it.chapterId to it.read })
    }

    @Test fun later_mark_replaces_earlier_one_for_same_chapter() {
        val q = queue()
        q.enqueue(1, true)
        q.enqueue(1, false)
        assertEquals(listOf(1L to false), q.pending().map { it.chapterId to it.read })
    }

    @Test fun remove_ignores_a_superseded_entry() {
        val q = queue()
        val old = q.enqueue(1, true)
        q.enqueue(1, false) // marked again while the old one was in flight
        q.remove(old)
        assertEquals(listOf(false), q.pending().map { it.read })
    }

    @Test fun overlay_shows_queued_state() {
        val q = queue()
        q.enqueue(2, true)
        val chapters = listOf(Chapter(1, 1.0, null, null, false), Chapter(2, 2.0, null, null, false))
        assertEquals(listOf(false, true), q.overlay(chapters).map { it.read })
    }

    @Test fun corrupt_file_is_set_aside_not_fatal() {
        val f = File(tmp.root, "q.json").apply { writeText("{not json") }
        assertTrue(queue(f).pending().isEmpty())
        assertTrue(File(tmp.root, "q.json.corrupt").exists())
    }

    @Test fun flush_sends_in_order_and_empties() = runTest {
        val q = queue()
        q.enqueue(1, true); q.enqueue(2, true); q.enqueue(3, false)
        val sent = mutableListOf<Pair<Long, Boolean>>()
        val r = ReadQueueFlusher.flush(q) { id, read -> sent += id to read }
        assertEquals(listOf(1L to true, 2L to true, 3L to false), sent)
        assertEquals(3, r.sent)
        assertFalse(r.retry)
        assertTrue(q.isEmpty())
    }

    @Test fun flush_stops_on_network_failure_and_keeps_the_rest() = runTest {
        val q = queue()
        q.enqueue(1, true); q.enqueue(2, true); q.enqueue(3, true)
        val r = ReadQueueFlusher.flush(q) { id, _ -> if (id == 2L) throw IOException("offline") }
        assertTrue(r.retry)
        assertEquals(1, r.sent)
        assertEquals(listOf(2L, 3L), q.pending().map { it.chapterId })
    }

    @Test fun flush_retries_on_5xx() = runTest {
        val q = queue()
        q.enqueue(1, true)
        val r = ReadQueueFlusher.flush(q) { _, _ -> throw ApiException(502, "upstream") }
        assertTrue(r.retry)
        assertEquals(1, q.pending().size)
    }

    @Test fun flush_drops_permanent_4xx_and_continues() = runTest {
        val q = queue()
        q.enqueue(1, true); q.enqueue(2, true)
        val sent = mutableListOf<Long>()
        val r = ReadQueueFlusher.flush(q) { id, _ ->
            if (id == 1L) throw ApiException(404, "no such chapter")
            sent += id
        }
        assertEquals(1, r.dropped)
        assertEquals(listOf(2L), sent)
        assertFalse(r.retry)
        assertTrue(q.isEmpty())
    }
}
