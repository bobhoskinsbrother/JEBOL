plugins {
    id("jebol-extension")
}

val fetchSqliteWasm = tasks.register<FetchVerifiedArchive>("fetchSqliteWasm") {
    group = "build"
    description = "Fetch sqlite.org's WebAssembly build of SQLite 3.54.1 and check it against its pinned digest"
    address = "https://sqlite.org/2026/sqlite-wasm-3540100.zip"
    sha256 = "85ebce90b6d189442b29e11d193337b5e63203aae9b6590dbe89a601b9891da2"
    entries = listOf("sqlite-wasm-3540100/jswasm/sqlite3.js", "sqlite-wasm-3540100/jswasm/sqlite3.wasm")
    download = layout.buildDirectory.file("downloads/sqlite-wasm-3540100.zip")
    destination = layout.buildDirectory.dir("sqlite-wasm")
}

jebolExtension {
    name = "sqlite"
    version = "3.46.0.0"
    jvmProvider = "org.jebol.extension.sqlite.SqliteOnTheJvm"
    webProvider = "org.jebol.extension.sqlite.SqliteInTheBrowser"
    webAssets.from(fetchSqliteWasm, "src/web/jebol-sqlite.js")
}

dependencies {
    jvmLibraries("io.roastedroot:sqlite4j:3.54.1.0")
}
