package io.lldab.mrmphone.data

import kotlinx.serialization.Serializable
import java.io.File

/** One prefetched chapter as seen by the eviction policy. */
data class CachedChapter(
    val chapterId: Long,
    val bytes: Long,
    val read: Boolean,
    val lastAccess: Long,
)

object Eviction {
    /**
     * Chapters to delete so the cache fits in [capBytes]: read chapters first,
     * least recently used first, then unread ones the same way. Chapters in
     * [protect] (the one open in the reader, the ones being prefetched) are
     * never chosen, even if that leaves the cache over the cap.
     */
    fun choose(entries: List<CachedChapter>, capBytes: Long, protect: Set<Long> = emptySet()): List<CachedChapter> {
        var total = entries.sumOf { it.bytes }
        if (total <= capBytes) return emptyList()
        val order = entries
            .filter { it.chapterId !in protect }
            .sortedWith(compareBy<CachedChapter>({ !it.read }, { it.lastAccess }, { it.chapterId }))
        val out = mutableListOf<CachedChapter>()
        for (c in order) {
            if (total <= capBytes) break
            out += c
            total -= c.bytes
        }
        return out
    }
}

object Prefetch {
    const val COUNT = 2

    /** The next [count] unread chapters after [openedId], in reading order. */
    fun targets(chapters: List<Chapter>, openedId: Long, count: Int = COUNT): List<Chapter> {
        val opened = chapters.firstOrNull { it.id == openedId } ?: return emptyList()
        return chapters
            .filter { it.number > opened.number && !it.read && it.id != openedId }
            .sortedBy { it.number }
            .take(count)
    }
}

/**
 * Prefetched pages on disk: `<root>/<chapterId>/meta.json` plus one file per
 * page named by its index. Lives in the app cache dir, so Android may also
 * clear it; the reader falls back to the network when a page is missing.
 */
class PageCache(private val root: File, private val clock: () -> Long = System::currentTimeMillis) {

    @Serializable
    data class Meta(
        val chapterId: Long,
        val seriesId: Long,
        val pageCount: Int,
        val complete: Boolean = false,
        val read: Boolean = false,
        val lastAccess: Long = 0,
    )

    private fun dir(chapterId: Long) = File(root, chapterId.toString())
    private fun metaFile(chapterId: Long) = File(dir(chapterId), "meta.json")

    fun meta(chapterId: Long): Meta? = runCatching {
        MrmJson.decodeFromString(Meta.serializer(), metaFile(chapterId).readText())
    }.getOrNull()

    @Synchronized
    fun writeMeta(m: Meta) {
        dir(m.chapterId).mkdirs()
        val tmp = File(dir(m.chapterId), "meta.json.tmp")
        tmp.writeText(MrmJson.encodeToString(Meta.serializer(), m))
        tmp.renameTo(metaFile(m.chapterId))
    }

    fun pageFile(chapterId: Long, index: Int) = File(dir(chapterId), index.toString())

    /** A fully downloaded page, or null. */
    fun localPage(chapterId: Long, index: Int): File? =
        pageFile(chapterId, index).takeIf { it.isFile && it.length() > 0 }

    fun writePage(chapterId: Long, index: Int, bytes: ByteArray) {
        dir(chapterId).mkdirs()
        val tmp = File(dir(chapterId), "$index.part")
        tmp.writeBytes(bytes)
        tmp.renameTo(pageFile(chapterId, index))
    }

    fun isComplete(chapterId: Long): Boolean {
        val m = meta(chapterId) ?: return false
        return m.complete && (0 until m.pageCount).all { localPage(chapterId, it) != null }
    }

    fun touch(chapterId: Long) {
        meta(chapterId)?.let { writeMeta(it.copy(lastAccess = clock())) }
    }

    fun setRead(chapterId: Long, read: Boolean) {
        meta(chapterId)?.let { if (it.read != read) writeMeta(it.copy(read = read)) }
    }

    fun entries(): List<CachedChapter> =
        (root.listFiles() ?: emptyArray()).mapNotNull { d ->
            val id = d.name.toLongOrNull() ?: return@mapNotNull null
            val m = meta(id)
            CachedChapter(
                chapterId = id,
                bytes = d.walkBottomUp().filter { it.isFile }.sumOf { it.length() },
                read = m?.read ?: true, // no meta = half-written leftover, evict early
                lastAccess = m?.lastAccess ?: 0L,
            )
        }

    fun totalBytes(): Long = entries().sumOf { it.bytes }

    fun delete(chapterId: Long) {
        dir(chapterId).deleteRecursively()
    }

    /** Evict down to [capBytes]; returns the evicted chapter ids. */
    @Synchronized
    fun enforce(capBytes: Long, protect: Set<Long> = emptySet()): List<Long> {
        val victims = Eviction.choose(entries(), capBytes, protect)
        victims.forEach { delete(it.chapterId) }
        return victims.map { it.chapterId }
    }

    fun clear() {
        root.deleteRecursively()
    }
}
