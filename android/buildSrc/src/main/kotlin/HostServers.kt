import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

// The servers on this machine that the Android end-to-end suite reaches from
// the emulator at 10.0.2.2: one plain, one TLS with the leaf
// scripts/make-emulator-host-certificate.sh writes. Each reads a request's
// head and answers it as HTTP/1.0 with one line, then closes. Goal 2c step 6.
class HostServers(certificate: File, key: File) : AutoCloseable {

    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val plain = ServerSocket(0, 10, loopback)
    private val secure = secureContext(certificate, key).serverSocketFactory
        .createServerSocket(0, 10, loopback) as SSLServerSocket

    val plainPort: Int get() = plain.localPort
    val securePort: Int get() = secure.localPort

    init {
        secure.enabledProtocols = arrayOf("TLSv1.3")
        answerEach(plain, PLAIN_ANSWER)
        answerEach(secure, SECURE_ANSWER)
    }

    private fun answerEach(server: ServerSocket, answer: String) {
        val answering = Thread({
            while (!server.isClosed) {
                try {
                    server.accept().use { client ->
                        client.soTimeout = 20_000
                        readRequestHead(client.getInputStream())
                        client.getOutputStream().apply {
                            write(("HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${answer.length}\r\n" +
                                "Connection: close\r\n\r\n$answer").toByteArray(Charsets.US_ASCII))
                            flush()
                        }
                    }
                } catch (closedOrRefused: Exception) {
                    if (server.isClosed) return@Thread
                }
            }
        }, "host-server-${server.localPort}")
        answering.isDaemon = true
        answering.start()
    }

    private fun readRequestHead(input: java.io.InputStream) {
        val end = byteArrayOf(13, 10, 13, 10)
        var matched = 0
        while (matched < end.size) {
            val read = input.read()
            if (read < 0) return
            matched = when {
                read.toByte() == end[matched] -> matched + 1
                read.toByte() == end[0] -> 1
                else -> 0
            }
        }
    }

    override fun close() {
        plain.close()
        secure.close()
    }

    companion object {
        const val PLAIN_ANSWER = "plain from the host"
        const val SECURE_ANSWER = "secure from the host"

        private fun secureContext(certificate: File, key: File): SSLContext {
            val leaf = certificate.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            val pem = key.readText().lines().filter { !it.startsWith("-----") }.joinToString("")
            val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
            val store = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("host", privateKey, CharArray(0), arrayOf(leaf))
            }
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(store, CharArray(0))
            }
            return SSLContext.getInstance("TLSv1.3").apply { init(keys.keyManagers, null, null) }
        }
    }
}
