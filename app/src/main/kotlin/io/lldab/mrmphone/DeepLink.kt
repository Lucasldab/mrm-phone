package io.lldab.mrmphone

/** Where an ntfy tap (or any mrmphone:// link) should land. */
sealed interface DeepLink {
    data object Library : DeepLink
    data class Series(val id: Long) : DeepLink

    companion object {
        /** mrmphone://series/{id} and mrmphone://library; anything else is null. */
        fun parse(uri: String?): DeepLink? {
            if (uri == null) return null
            val rest = uri.trim().removePrefix("mrmphone://").takeIf { it != uri.trim() } ?: return null
            val parts = rest.substringBefore('?').substringBefore('#').trim('/').split('/')
            return when {
                parts.size == 1 && parts[0] == "library" -> Library
                parts.size == 2 && parts[0] == "series" -> parts[1].toLongOrNull()?.takeIf { it > 0 }?.let { Series(it) }
                else -> null
            }
        }
    }
}
