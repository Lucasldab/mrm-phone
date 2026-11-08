package io.lldab.mrmphone.data

import io.lldab.mrmphone.Fixtures
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ApiClient
    private var settings = AppSettings()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        settings = AppSettings(serverUrl = server.url("/").toString().trimEnd('/'), token = "s3cret")
        api = ApiClient(buildHttpClient { settings }) { settings }
    }

    @After fun tearDown() = server.shutdown()

    private fun json(name: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(Fixtures.read(name))

    @Test fun library_sends_bearer_token() = runTest {
        server.enqueue(json("library.json"))
        val l = api.library()
        assertEquals(2, l.size)
        val req = server.takeRequest()
        assertEquals("/api/library", req.path)
        assertEquals("Bearer s3cret", req.getHeader("Authorization"))
    }

    @Test fun search_encodes_query_and_parses_object() = runTest {
        server.enqueue(json("search.json"))
        val r = api.search("solo leveling & co")
        assertEquals(2, r.hits.size)
        assertEquals(1, r.errors.size)
        val req = server.takeRequest()
        assertEquals("solo leveling & co", req.requestUrl!!.queryParameter("q"))
        assertTrue(req.path!!.startsWith("/api/search?"))
    }

    @Test fun mark_read_posts_json() = runTest {
        server.enqueue(json("read_response.json"))
        val r = api.markRead(103, true)
        assertEquals(1, r.series.unread)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/chapters/103/read", req.path)
        assertEquals("""{"read":true}""", req.body.readUtf8())
    }

    @Test fun mark_read_through_posts_to_the_chapter() = runTest {
        server.enqueue(json("read_through_response.json"))
        val r = api.markReadThrough(103)
        assertEquals(3, r.marked)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/chapters/103/read-through", req.path)
    }

    @Test fun set_status_and_add_series_bodies() = runTest {
        server.enqueue(json("series.json"))
        server.enqueue(json("series_detail.json"))
        api.setStatus(12, "paused")
        api.addSeries("asura", "https://asuracomic.net/series/x")
        val a = server.takeRequest()
        assertEquals("/api/series/12/status", a.path)
        assertEquals("""{"status":"paused"}""", a.body.readUtf8())
        val b = server.takeRequest()
        assertEquals("/api/series", b.path)
        assertEquals("""{"source":"asura","source_url":"https://asuracomic.net/series/x"}""", b.body.readUtf8())
    }

    @Test fun error_json_becomes_api_exception() = runTest {
        server.enqueue(json("error.json", code = 409))
        try {
            api.addSeries("asura", "u")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(409, e.code)
            assertEquals("already in library", e.message)
            assertTrue(e.permanent)
        }
    }

    @Test fun upstream_502_is_not_permanent() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"asura: timeout"}"""))
        try {
            api.refresh(12)
            fail()
        } catch (e: ApiException) {
            assertEquals(502, e.code)
            assertFalse(e.permanent)
        }
    }

    @Test fun token_only_goes_to_the_configured_server() {
        val s = AppSettings(serverUrl = "http://hearth.tailda34b8.ts.net:8102", token = "t")
        assertTrue(Urls.sameOrigin("http://hearth.tailda34b8.ts.net:8102/api/series/1/cover".toHttpUrl(), s.serverUrl))
        assertFalse(Urls.sameOrigin("https://gg.asuracomic.net/cover.webp".toHttpUrl(), s.serverUrl))
        assertFalse(Urls.sameOrigin("http://hearth.tailda34b8.ts.net:8080/x".toHttpUrl(), s.serverUrl))
    }

    @Test fun absolute_foreign_url_gets_no_token() = runTest {
        val other = MockWebServer().apply { start() }
        other.enqueue(MockResponse().setBody("img"))
        // Same host (localhost) but another port: a different origin.
        api.bytes(other.url("/cover.webp").toString())
        assertNull(other.takeRequest().getHeader("Authorization"))
        other.shutdown()
    }
}
