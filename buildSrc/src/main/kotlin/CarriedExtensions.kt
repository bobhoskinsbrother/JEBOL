// R3's external extensions that JEBOL ports, each a jar of its own under
// extensions/: the desktop's shipped jar and the site carry them, the core and
// Android do not (goal 2a step 5). SQLite is not among them; a build takes it
// only when asked.
val CARRIED_EXTENSIONS = listOf(
    "easing", "blurhash", "mathpresso", "deflate", "zlib-ng", "zstd", "bzip2", "brotli", "blend2d",
)

val EVERY_EXTENSION = listOf("sqlite") + CARRIED_EXTENSIONS
