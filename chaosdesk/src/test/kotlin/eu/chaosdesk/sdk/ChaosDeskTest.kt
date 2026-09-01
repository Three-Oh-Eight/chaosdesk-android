package eu.chaosdesk.sdk

import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Captures the request instead of sending it. */
private class RecordingTransport(
    private val status: Int = 201,
    private val body: String = "{}",
) : Transport {
  var url: String? = null
  var headers: Map<String, String> = emptyMap()
  var json: String? = null

  val payload: JSONObject
    get() = JSONObject(requireNotNull(json))

  override suspend fun post(
      url: String,
      headers: Map<String, String>,
      json: String,
  ): Pair<Int, String> {
    this.url = url
    this.headers = headers
    this.json = json

    return status to body
  }
}

private class FailingTransport : Transport {
  override suspend fun post(
      url: String,
      headers: Map<String, String>,
      json: String,
  ): Pair<Int, String> = throw IOException("offline")
}

private fun TestScope.client(
    transport: Transport,
    logs: LogBuffer = LogBuffer(),
    includeLogs: Boolean = true,
) =
    ChaosDesk(
        configuration =
            ChaosDesk.Configuration(
                endpoint = "https://dialed.at/api/chaosdesk/tickets",
                headers = { mapOf("Authorization" to "Bearer host-app-token") },
                includeLogs = includeLogs,
                appContext = TicketContext.App(name = "Dialed", version = "2.4.1", build = "381"),
            ),
        transport = transport,
        logs = logs,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChaosDeskTest {

  @Test
  fun `posts to the configured endpoint on your own backend`() = runTest {
    val transport = RecordingTransport()

    client(transport).submit(ChaosDesk.Report("Fork sag not saving", "It reverts."))

    assertEquals("https://dialed.at/api/chaosdesk/tickets", transport.url)
  }

  @Test
  fun `sends the caller's own authentication headers`() = runTest {
    val transport = RecordingTransport()

    client(transport).submit(ChaosDesk.Report("Subject", "Body"))

    assertEquals("Bearer host-app-token", transport.headers["Authorization"])
  }

  @Test
  fun `never carries a ChaosDesk credential`() = runTest {
    val transport = RecordingTransport()

    client(transport).submit(ChaosDesk.Report("Subject", "Body"))

    assertFalse(transport.headers.containsKey("X-Site-Token"))
    assertFalse(requireNotNull(transport.json).contains("X-Site-Token"))
  }

  @Test
  fun `emits the documented context groups`() = runTest {
    val transport = RecordingTransport()
    val chaosDesk = client(transport)

    chaosDesk.identify(
        TicketContext.User(
            externalId = "4711", name = "Ada", email = "ada@dialed.at", plan = "pro"),
    )

    chaosDesk.submit(
        ChaosDesk.Report("Subject", "Body", extra = mapOf("setup_id" to 12)),
    )

    val payload = transport.payload
    val context = payload.getJSONObject("context")

    assertEquals("Subject", payload.getString("subject"))
    assertEquals("android", context.getString("source"))
    assertEquals("chaosdesk-android", context.getJSONObject("sdk").getString("name"))
    assertEquals("2.4.1", context.getJSONObject("app").getString("version"))
    assertEquals("381", context.getJSONObject("app").getString("build"))
    assertEquals("4711", context.getJSONObject("user").getString("external_id"))
    assertEquals("pro", context.getJSONObject("user").getString("plan"))
    assertEquals(12, context.getJSONObject("extra").getInt("setup_id"))
  }

  @Test
  fun `uses snake_case keys matching the server schema`() = runTest {
    val transport = RecordingTransport()
    val chaosDesk = client(transport)

    chaosDesk.identify(TicketContext.User(externalId = "4711"))
    chaosDesk.submit(ChaosDesk.Report("Subject", "Body"))

    val context = transport.payload.getJSONObject("context")

    assertTrue(context.getJSONObject("user").has("external_id"))
    assertFalse(context.getJSONObject("user").has("externalId"))
    assertTrue(context.getJSONObject("device").has("os_version"))
    assertFalse(context.getJSONObject("device").has("osVersion"))
  }

  @Test
  fun `omits the user when nobody is identified`() = runTest {
    val transport = RecordingTransport()

    client(transport).submit(ChaosDesk.Report("Subject", "Body"))

    assertFalse(transport.payload.getJSONObject("context").has("user"))
  }

  @Test
  fun `forgets the user on sign-out`() = runTest {
    val transport = RecordingTransport()
    val chaosDesk = client(transport)

    chaosDesk.identify(TicketContext.User(externalId = "4711"))
    chaosDesk.identify(null)
    chaosDesk.submit(ChaosDesk.Report("Subject", "Body"))

    assertFalse(transport.payload.getJSONObject("context").has("user"))
  }

  @Test
  fun `attaches recent log entries`() = runTest {
    val logs = LogBuffer()
    logs.record("Uncaught TypeError")

    val transport = RecordingTransport()
    client(transport, logs).submit(ChaosDesk.Report("Subject", "Body"))

    val console = transport.payload.getJSONObject("context").getJSONArray("console")

    assertEquals(1, console.length())
    assertEquals("Uncaught TypeError", console.getJSONObject(0).getString("message"))
    assertEquals("error", console.getJSONObject(0).getString("level"))
  }

  @Test
  fun `can leave logs out`() = runTest {
    val logs = LogBuffer()
    logs.record("noise")

    val transport = RecordingTransport()
    client(transport, logs, includeLogs = false).submit(ChaosDesk.Report("Subject", "Body"))

    assertFalse(transport.payload.getJSONObject("context").has("console"))
  }

  @Test
  fun `reports a server error with its status`() = runTest {
    val transport = RecordingTransport(status = 422, body = """{"message":"invalid"}""")

    val error =
        try {
          client(transport).submit(ChaosDesk.Report("", "Body"))
          null
        } catch (e: ChaosDesk.SubmitException.Server) {
          e
        }

    assertEquals(422, requireNotNull(error).status)
    assertTrue(error.body.contains("invalid"))
  }

  @Test
  fun `reports a transport failure`() = runTest {
    val error =
        try {
          client(FailingTransport()).submit(ChaosDesk.Report("Subject", "Body"))
          null
        } catch (e: ChaosDesk.SubmitException.Transport) {
          e
        }

    assertTrue(error is ChaosDesk.SubmitException.Transport)
  }

  @Test
  fun `omits an optional field rather than sending null`() = runTest {
    val transport = RecordingTransport()

    client(transport).submit(ChaosDesk.Report("Subject", "Body"))

    assertFalse(transport.payload.has("email"))
    assertFalse(transport.payload.has("name"))
    assertNull(transport.payload.optJSONObject("context")?.optJSONObject("extra"))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogBufferTest {

  @Test
  fun `keeps only the most recent entries`() = runTest {
    val buffer = LogBuffer(capacity = 3)

    repeat(10) { buffer.record("error ${it + 1}") }

    val entries = buffer.snapshot()

    assertEquals(3, entries.size)
    assertEquals("error 8", entries.first().message)
    assertEquals("error 10", entries.last().message)
  }

  @Test
  fun `caps an overlong message`() = runTest {
    val buffer = LogBuffer()
    buffer.record("x".repeat(5000))

    assertEquals(2000, buffer.snapshot().first().cappedMessage.length)
  }

  @Test
  fun `records a throwable with its stack trace`() = runTest {
    val buffer = LogBuffer()
    buffer.record(IllegalStateException("boom"))

    assertTrue(buffer.snapshot().first().message.contains("boom"))
  }

  @Test
  fun `clears`() = runTest {
    val buffer = LogBuffer()
    buffer.record("noise")
    buffer.clear()

    assertTrue(buffer.snapshot().isEmpty())
  }
}
