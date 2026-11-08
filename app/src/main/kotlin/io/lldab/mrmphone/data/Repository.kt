package io.lldab.mrmphone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException

/** A value plus whether it came from the offline copy instead of the server. */
data class Loaded<T>(val value: T, val offline: Boolean = false)

/** Last good library / series responses, for reading with no connection. */
class ResponseCache(private val dir: File) {
    fun put(name: String, text: String) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }

    fun get(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()

    fun remove(name: String) {
        File(dir, name).delete()
    }
}

/** One page of a chapter for the reader: a cached [File] or a server URL. */
data class PageRef(val index: Int, val model: Any)

interface WorkScheduler {
    fun scheduleFlush()
    fun schedulePrefetch(seriesId: Long, chapterIds: List<Long>)
}

class Repository(
    private val api: ApiClient,
    val queue: ReadQueue,
    val pageCache: PageCache,
    private val responses: ResponseCache,
    private val scheduler: WorkScheduler,
    private val settings: () -> AppSettings,
) {
    private val seriesList = ListSerializer(Series.serializer())

    /** Network failure (not an HTTP error from a reachable server). */
    private fun isOffline(e: Throwable) = e is IOException && e !is ApiException

    suspend fun library(): Loaded<List<Series>> = withContext(Dispatchers.IO) {
        try {
            val list = api.library()
            responses.put("library.json", MrmJson.encodeToString(seriesList, list))
            Loaded(list)
        } catch (e: IOException) {
            val cached = if (isOffline(e)) responses.get("library.json") else null
            cached ?: throw e
            Loaded(MrmJson.decodeFromString(seriesList, cached), offline = true)
        }
    }

    suspend fun series(id: Long): Loaded<SeriesDetail> = withContext(Dispatchers.IO) {
        val loaded = try {
            val d = api.series(id)
            cacheDetail(d)
            Loaded(d)
        } catch (e: IOException) {
            val cached = if (isOffline(e)) responses.get("series-$id.json") else null
            cached ?: throw e
            Loaded(MrmJson.decodeFromString(SeriesDetail.serializer(), cached), offline = true)
        }
        loaded.copy(value = loaded.value.copy(chapters = queue.overlay(loaded.value.chapters)))
    }

    private fun cacheDetail(d: SeriesDetail) {
        responses.put("series-${d.id}.json", MrmJson.encodeToString(SeriesDetail.serializer(), d))
    }

    suspend fun refresh(id: Long): SeriesDetail = withContext(Dispatchers.IO) {
        api.refresh(id).also { cacheDetail(it) }.let { it.copy(chapters = queue.overlay(it.chapters)) }
    }

    suspend fun setStatus(id: Long, status: String): Series = api.setStatus(id, status)

    /**
     * Delete a series on the server, then drop what the phone holds for it:
     * the offline copy, prefetched pages and queued read marks. Online only:
     * an offline failure throws and leaves everything in place.
     */
    suspend fun deleteSeries(detail: SeriesDetail) = withContext(Dispatchers.IO) {
        api.deleteSeries(detail.id)
        responses.remove("series-${detail.id}.json")
        val chapterIds = detail.chapters.map { it.id }.toSet()
        chapterIds.forEach { queue.removeChapter(it) }
        pageCache.entries()
            .map { it.chapterId }
            .filter { it in chapterIds || pageCache.meta(it)?.seriesId == detail.id }
            .forEach { pageCache.delete(it) }
    }

    /**
     * Mark a chapter read/unread. Online: the server's answer (fresh Series).
     * Offline: queued for [ReadQueueWorker] and null is returned; the chapter
     * list shows the queued state through [ReadQueue.overlay].
     */
    suspend fun markRead(chapterId: Long, read: Boolean): Series? = withContext(Dispatchers.IO) {
        pageCache.setRead(chapterId, read)
        try {
            val r = api.markRead(chapterId, read)
            queue.removeChapter(chapterId)
            r.series
        } catch (e: IOException) {
            if (e is ApiException && e.permanent) throw e
            queue.enqueue(chapterId, read)
            scheduler.scheduleFlush()
            null
        }
    }

    /** Pages for the reader, preferring prefetched files. */
    suspend fun pages(chapterId: Long): List<PageRef> = withContext(Dispatchers.IO) {
        pageCache.touch(chapterId)
        val meta = pageCache.meta(chapterId)
        if (meta != null && pageCache.isComplete(chapterId)) {
            return@withContext (0 until meta.pageCount).map { PageRef(it, pageCache.pageFile(chapterId, it)) }
        }
        val paths = api.pages(chapterId).pages
        paths.mapIndexed { i, p -> PageRef(i, pageCache.localPage(chapterId, i) ?: api.url(p)) }
    }

    /** After opening [chapterId]: queue the next unread chapters for download. */
    fun prefetchAfter(detail: SeriesDetail, chapterId: Long) {
        if (!settings().prefetch) return
        val targets = Prefetch.targets(detail.chapters, chapterId)
            .filter { !pageCache.isComplete(it.id) }
        if (targets.isNotEmpty()) scheduler.schedulePrefetch(detail.id, targets.map { it.id })
    }

    /** Download one chapter into the page cache (worker side). */
    suspend fun downloadChapter(seriesId: Long, chapterId: Long, protect: Set<Long>) = withContext(Dispatchers.IO) {
        if (pageCache.isComplete(chapterId)) return@withContext
        val paths = api.pages(chapterId).pages
        val base = pageCache.meta(chapterId)
        pageCache.writeMeta(
            PageCache.Meta(chapterId, seriesId, paths.size, complete = false, read = base?.read ?: false,
                lastAccess = System.currentTimeMillis()),
        )
        paths.forEachIndexed { i, p ->
            if (pageCache.localPage(chapterId, i) == null) pageCache.writePage(chapterId, i, api.bytes(p))
        }
        pageCache.meta(chapterId)?.let { pageCache.writeMeta(it.copy(complete = true)) }
        pageCache.enforce(settings().cacheCapMb * 1024L * 1024L, protect)
    }

    suspend fun search(q: String): SearchResponse = api.search(q)

    suspend fun add(hit: SearchHit): SeriesDetail = api.addSeries(hit.source, hit.sourceUrl).also { cacheDetail(it) }

    suspend fun health(): Health = api.health()

    suspend fun flushQueue(): ReadQueueFlusher.Result =
        ReadQueueFlusher.flush(queue) { id, read -> api.markRead(id, read) }

    fun coverUrl(path: String): String = api.url(path)
}
