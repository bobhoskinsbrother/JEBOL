import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress

class HostSite(private val webBuild: File, private val runnerPage: File, private val app: File) : AutoCloseable {

    private val serving = HttpServer.create(
        InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0), 0)

    val port: Int get() = serving.address.port

    init {
        serving.createContext("/") { exchange -> answer(exchange) }
        serving.start()
    }

    private fun answer(exchange: HttpExchange) {
        exchange.use {
            val asked = exchange.requestURI.path
            when {
                asked == "/" || asked == "/index.html" -> respond(exchange, runnerPage.readBytes(), "text/html; charset=utf-8")
                asked == "/app.json" -> respond(exchange, APP_JSON.toByteArray(Charsets.UTF_8), "application/json")
                asked == "/main.reb" -> respond(exchange, File(app, "main.reb").readBytes(), "text/plain; charset=utf-8")
                else -> {
                    val file = File(webBuild, asked.removePrefix("/")).canonicalFile
                    if (file.path.startsWith(webBuild.canonicalPath) && file.isFile) {
                        respond(exchange, file.readBytes(), contentTypeFor(asked))
                    } else {
                        exchange.sendResponseHeaders(404, -1)
                    }
                }
            }
        }
    }

    private fun respond(exchange: HttpExchange, body: ByteArray, type: String) {
        exchange.responseHeaders.add("Content-Type", type)
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.write(body)
    }

    private fun contentTypeFor(asked: String) = when {
        asked.endsWith(".wasm") -> "application/wasm"
        asked.endsWith(".ttf") -> "font/ttf"
        else -> "text/javascript"
    }

    override fun close() {
        serving.stop(0)
    }

    companion object {
        const val APP_JSON = """{"files":[],"entry":"main.reb"}"""
    }
}
