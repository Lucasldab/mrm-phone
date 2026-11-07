package io.lldab.mrmphone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlsTest {
    @Test fun resolves_relative_paths_against_server() {
        assertEquals(
            "http://hearth.tailda34b8.ts.net:8102/api/series/3/cover",
            Urls.resolve("http://hearth.tailda34b8.ts.net:8102", "/api/series/3/cover"),
        )
        assertEquals("http://h:1/api/x", Urls.resolve("http://h:1/", "/api/x"))
        assertEquals("http://h:1/api/x", Urls.resolve("http://h:1", "api/x"))
    }

    @Test fun keeps_a_path_prefix_on_the_server_url() {
        assertEquals("https://h/mrm/api/x", Urls.resolve("https://h/mrm", "/api/x"))
    }

    @Test fun absolute_urls_pass_through() {
        assertEquals("https://cdn/x.webp", Urls.resolve("http://h:1", "https://cdn/x.webp"))
    }

    @Test fun normalises_typed_server() {
        assertEquals("http://hearth:8102", Urls.normaliseServer(" hearth:8102/ "))
        assertEquals("https://x.y", Urls.normaliseServer("https://x.y"))
        assertNull(Urls.normaliseServer(""))
        assertNull(Urls.normaliseServer("ftp://x"))
    }
}
