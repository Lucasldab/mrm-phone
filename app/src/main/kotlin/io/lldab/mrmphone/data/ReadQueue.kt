package io.lldab.mrmphone.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException

/**
 * Read/unread marks made while hearth was unreachable. Persisted as a small
 * JSON file so they survive the process dying; flushed by [ReadQueueWorker]
 * once there is connectivity. One entry per chapter: a later mark replaces an
 * earlier one (read then unread offline sends only "unread").
 */
class ReadQueue(private val file: File) {

    @Serializable
    data class Entry(val chapterId: Long, val read: Boolean, val seq: Long)

    private val lock = Any()

    fun pending(): List<Entry> = synchronized(lock) { load() }

    fun isEmpty(): Boolean = pending().isEmpty()

    fun enqueue(chapterId: Long, read: Boolean): Entry = synchronized(lock) {
        val list = load()
        val seq = (list.maxOfOrNull { it.seq } ?: 0L) + 1
        val entry = Entry(chapterId, read, seq)
        save(list.filter { it.chapterId != chapterId } + entry)
        entry
    }

    /** Remove [entry] if it is still the newest mark for its chapter (it was sent). */
    fun remove(entry: Entry) = synchronized(lock) {
        val list = load()
        if (list.any { it == entry }) save(list.filter { it != entry })
    }

    /** Drop whatever is queued for a chapter (a direct send just superseded it). */
    fun removeChapter(chapterId: Long) = synchronized(lock) {
        val list = load()
        if (list.any { it.chapterId == chapterId }) save(list.filter { it.chapterId != chapterId })
    }

    /** Chapters with queued marks shown as they will be once flushed. */
    fun overlay(chapters: List<Chapter>): List<Chapter> {
        val marks = pending().associate { it.chapterId to it.read }
        if (marks.isEmpty()) return chapters
        return chapters.map { c -> marks[c.id]?.let { c.copy(read = it) } ?: c }
    }

    private fun load(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            MrmJson.decodeFromString(ListSerializer(Entry.serializer()), file.readText())
        } catch (e: Exception) {
            // A corrupt queue must not crash the app; the marks are lost, the
            // user can re-mark. Keep the broken file aside for inspection.
            file.renameTo(File(file.parentFile, file.name + ".corrupt"))
            emptyList()
        }
    }

    private fun save(list: List<Entry>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(MrmJson.encodeToString(ListSerializer(Entry.serializer()), list))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("could not write ${file.path}")
        }
    }
}

object ReadQueueFlusher {
    data class Result(val sent: Int, val dropped: Int, val retry: Boolean)

    /**
     * Send every queued mark in order. A network failure (or a 5xx) stops the
     * flush and asks for a retry, keeping the rest; a permanent 4xx (chapter
     * deleted on the server) drops that entry so it cannot block the queue.
     */
    suspend fun flush(queue: ReadQueue, send: suspend (chapterId: Long, read: Boolean) -> Unit): Result {
        var sent = 0
        var dropped = 0
        for (e in queue.pending()) {
            try {
                send(e.chapterId, e.read)
                queue.remove(e)
                sent++
            } catch (ex: ApiException) {
                if (ex.permanent) {
                    queue.remove(e)
                    dropped++
                } else {
                    return Result(sent, dropped, retry = true)
                }
            } catch (ex: IOException) {
                return Result(sent, dropped, retry = true)
            }
        }
        return Result(sent, dropped, retry = false)
    }
}
