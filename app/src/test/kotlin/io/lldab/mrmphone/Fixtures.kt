package io.lldab.mrmphone

object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()
}
