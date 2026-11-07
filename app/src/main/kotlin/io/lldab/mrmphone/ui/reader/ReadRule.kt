package io.lldab.mrmphone.ui.reader

/**
 * The read rule (spec "Reader"): a chapter is read once its last page has
 * been displayed. [visiblePages] are the indices of pages currently on
 * screen whose image has actually loaded (a placeholder does not count, or a
 * slow connection would mark chapters read before anything was seen).
 * Leaving early marks nothing: the reader only ever calls this with what is
 * on screen, and nothing is sent on back.
 */
object ReadRule {
    fun shouldMarkRead(visiblePages: Collection<Int>, pageCount: Int): Boolean =
        pageCount > 0 && (pageCount - 1) in visiblePages
}
