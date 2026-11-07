package io.lldab.mrmphone.data

import io.lldab.mrmphone.Fixtures
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One fixture per contract type (spec 2026-11-07, "API contract"). */
class ContractParsingTest {
    private fun <T> parse(s: kotlinx.serialization.KSerializer<T>, name: String) =
        MrmJson.decodeFromString(s, Fixtures.read(name))

    @Test fun health() {
        val h = parse(Health.serializer(), "health.json")
        assertTrue(h.ok)
        assertEquals("0.9.0", h.version)
    }

    @Test fun library_is_a_list_of_series() {
        val l = parse(ListSerializer(Series.serializer()), "library.json")
        assertEquals(2, l.size)
        val a = l[0]
        assertEquals(12L, a.id)
        assertEquals("asura", a.source)
        assertEquals("reading", a.status)
        assertEquals("ongoing", a.pubStatus)
        assertEquals(3, a.unread)
        assertEquals(41, a.total)
        assertEquals("/api/series/12/cover", a.cover)
        assertFalse(a.failing)
        assertNull(a.note)
        assertFalse(a.warn)
        assertEquals("2026-11-06", a.latestRelease)
        val b = l[1]
        // Absent on servers older than the field.
        assertNull(b.latestRelease)
        assertTrue(b.failing)
        assertEquals("502 upstream: cloudflare challenge", b.note)
        assertTrue(b.warn)
    }

    @Test fun series_status_response() {
        val s = parse(Series.serializer(), "series.json")
        assertEquals("paused", s.status)
    }

    @Test fun series_detail_flattens_series_fields() {
        val d = parse(SeriesDetail.serializer(), "series_detail.json")
        assertEquals(12L, d.id)
        assertEquals("Suho, son of the Shadow Monarch.", d.description)
        assertEquals(listOf(101L, 102L, 103L, 104L), d.chapters.map { it.id })
        // number is a float on the wire; integers may come without ".0"
        assertEquals(listOf(1.0, 2.0, 2.5, 3.0), d.chapters.map { it.number })
        assertEquals("Prologue", d.chapters[0].title)
        assertNull(d.chapters[1].title)
        assertNull(d.chapters[1].releasedAt)
        assertEquals("2024-08-15T00:00:00Z", d.chapters[2].releasedAt)
        assertTrue(d.chapters[0].read)
        assertFalse(d.chapters[3].read)
        assertEquals(d.title, d.toSeries().title)
        assertEquals(2, d.toSeries().unread)
    }

    @Test fun series_detail_with_nulls_and_no_chapters() {
        val d = parse(SeriesDetail.serializer(), "series_detail_minimal.json")
        assertNull(d.description)
        assertTrue(d.chapters.isEmpty())
    }

    @Test fun read_response_carries_fresh_series() {
        val r = parse(ReadResponse.serializer(), "read_response.json")
        assertEquals(103L, r.chapterId)
        assertTrue(r.read)
        assertEquals(1, r.series.unread)
    }

    @Test fun pages_response() {
        val p = parse(PagesResponse.serializer(), "pages.json")
        assertEquals(103L, p.chapterId)
        assertEquals("/api/chapters/103/pages/2", p.pages[2])
        assertEquals(3, p.pages.size)
    }

    @Test fun search_is_an_object_with_hits_and_errors() {
        val s = parse(SearchResponse.serializer(), "search.json")
        assertEquals(2, s.hits.size)
        assertEquals("https://asuracomic.net/series/solo-leveling", s.hits[0].sourceUrl)
        assertEquals("completed", s.hits[0].pubStatus)
        assertNull(s.hits[1].coverUrl)
        assertEquals(listOf("mangack: 502 upstream timeout"), s.errors)
        val empty = parse(SearchResponse.serializer(), "search_empty.json")
        assertTrue(empty.hits.isEmpty() && empty.errors.isEmpty())
    }

    @Test fun error_body() {
        assertEquals("already in library", parse(ErrorBody.serializer(), "error.json").error)
    }

    @Test fun request_bodies_are_snake_case() {
        assertEquals("""{"status":"up_to_date"}""", MrmJson.encodeToString(StatusRequest.serializer(), StatusRequest("up_to_date")))
        assertEquals("""{"read":true}""", MrmJson.encodeToString(ReadRequest.serializer(), ReadRequest(true)))
        assertEquals(
            """{"source":"asura","source_url":"https://x/y"}""",
            MrmJson.encodeToString(AddSeriesRequest.serializer(), AddSeriesRequest("asura", "https://x/y")),
        )
    }

    @Test fun unknown_fields_are_ignored() {
        val s = MrmJson.decodeFromString(
            Series.serializer(),
            """{"id":1,"title":"t","source":"s","status":"reading","pub_status":"ongoing","unread":0,"total":0,
               "cover":"/c","failing":false,"note":null,"something_new":42}""",
        )
        assertEquals(1L, s.id)
    }

    @Test fun status_values_match_mrm() {
        assertEquals(
            listOf("reading", "up_to_date", "looked_into", "paused", "completed", "dropped"),
            SeriesStatus.entries.map { it.wire },
        )
        assertEquals("up to date", SeriesStatus.label("up_to_date"))
        assertEquals("weird thing", SeriesStatus.label("weird_thing"))
    }

    @Test fun chapter_numbers_format() {
        assertEquals("12", formatChapterNumber(12.0))
        assertEquals("12.5", formatChapterNumber(12.5))
    }
}
