package com.wasimaster.wmkeyboard.core.tools

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a streaming request stops being "connecting" and starts being "waiting
 * for the model".
 *
 * A streaming AI endpoint does not send its response headers until the model
 * has produced something, so reading the status code blocks for the whole of
 * the model's thinking time. Reporting the change of phase after that read
 * meant the panel showed "Connecting" for the entire wait and "Waiting for the
 * model" for the instant before the first token landed, which is the opposite
 * of what those two words mean.
 */
class ToolHttpStreamPhaseTest {

    /** How long the fake service holds its headers back, as a model would. */
    private val thinkingMs = 400L

    private fun withServer(block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/stream") { exchange ->
            exchange.requestBody.readBytes()
            // The part that matters: nothing at all goes back until the fake
            // model has "thought", so the client is blocked inside the status
            // read for this whole stretch.
            Thread.sleep(thinkingMs)
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                out.write("data: {\"n\":1}\n".toByteArray())
                out.flush()
                out.write("data: {\"n\":2}\n".toByteArray())
                out.flush()
            }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/stream")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `the wait is reported before the model answers, not after`() {
        withServer { url ->
            val startedAt = System.currentTimeMillis()
            var sentAt = -1L
            val lineTimes = CopyOnWriteArrayList<Long>()

            ToolHttp.postJsonStream(
                url = url,
                body = "{}",
                onRequestSent = { sentAt = System.currentTimeMillis() - startedAt },
            ) { line ->
                if (line.isNotBlank()) lineTimes += System.currentTimeMillis() - startedAt
                true
            }

            assertTrue("onRequestSent never fired", sentAt >= 0)
            assertEquals(2, lineTimes.size)
            // The whole point: the caller learns it is waiting well before the
            // service says anything. Half the thinking time is a wide margin
            // around "immediately" that still fails if the callback moves back
            // behind the status read.
            assertTrue(
                "reported the wait at ${sentAt}ms, after the model had already " +
                    "started answering at ${lineTimes.first()}ms",
                sentAt < thinkingMs / 2,
            )
            assertTrue(lineTimes.first() >= thinkingMs)
        }
    }

    @Test
    fun `every response line still arrives in order`() {
        withServer { url ->
            val lines = CopyOnWriteArrayList<String>()
            ToolHttp.postJsonStream(url = url, body = "{}") { line ->
                if (line.isNotBlank()) lines += line
                true
            }
            assertEquals(listOf("data: {\"n\":1}", "data: {\"n\":2}"), lines)
        }
    }

    @Test
    fun `returning false stops the read where it stands`() {
        withServer { url ->
            val lines = CopyOnWriteArrayList<String>()
            ToolHttp.postJsonStream(url = url, body = "{}") { line ->
                if (line.isNotBlank()) lines += line
                // An abandoned run must stop holding the socket, and stop
                // paying for tokens nobody will see.
                false
            }
            assertEquals(1, lines.size)
        }
    }
}
