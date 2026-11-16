// R3's Bulk compressors, which its Core product leaves out, each a project of
// its own under optional/ (goal 2a step 6, decision 74). Nothing imports one:
// on the classpath, its method is in system/catalog/compressions; off it, the
// method is an invalid argument. The desktop's shipped jar and the site carry
// them all; the core and Android carry none.
val OPTIONAL_PARTS = listOf("lzw", "crush", "lzav", "lz4", "lzma")

const val OPTIONAL_COMPRESSION_SERVICES = "META-INF/services/org.jebol.domain.eval.OptionalCompression"
