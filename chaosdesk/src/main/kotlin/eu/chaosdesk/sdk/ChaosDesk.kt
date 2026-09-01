package eu.chaosdesk.sdk

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Submits support tickets to *your* backend.
 *
 * This client deliberately never talks to ChaosDesk. Your app authenticates against your own API
 * exactly as it already does; that backend forwards the ticket using its server-side ingest token.
 * No ChaosDesk credential is ever shipped inside the APK.
 */
class ChaosDesk(
    private val configuration: Configuration,
    private val transport: Transport = OkHttpTransport(),
    private val logs: LogBuffer = LogBuffer.shared,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  /**
   * @param endpoint the route on your own backend, for example
   *   `https://dialed.at/api/chaosdesk/tickets`.
   * @param headers called per request, so a rotating access token stays fresh.
   */
  data class Configuration(
      val endpoint: String,
      val headers: suspend () -> Map<String, String> = { emptyMap() },
      val includeLogs: Boolean = true,
      val appContext: TicketContext.App? = null,
  )

  /** What the user is reporting. */
  data class Report(
      val subject: String,
      val message: String,
      val email: String? = null,
      val name: String? = null,
      val extra: Map<String, Any?>? = null,
  )

  sealed class SubmitException(message: String) : Exception(message) {
    class Transport(cause: IOException) :
        SubmitException("Could not reach the server: ${cause.message}")

    class Server(val status: Int, val body: String) :
        SubmitException("Server returned $status: $body")
  }

  @Volatile private var user: TicketContext.User? = null

  /** Identify the reporter, for example after sign-in. Pass null on sign-out. */
  fun identify(user: TicketContext.User?) {
    this.user = user
  }

  /** Build the context this device would send right now. */
  suspend fun context(extra: Map<String, Any?>? = null): TicketContext =
      TicketContext(
          source = "android",
          app = configuration.appContext,
          device = DeviceContext.current(),
          user = user,
          console = if (configuration.includeLogs) logs.snapshot() else emptyList(),
          extra = extra,
      )

  /** Submit a report to your backend. Returns the response body. */
  @Throws(SubmitException::class)
  suspend fun submit(report: Report): String =
      withContext(ioDispatcher) {
        val payload =
            JSONObject().apply {
              put("subject", report.subject)
              put("message", report.message)
              report.email?.let { put("email", it) }
              report.name?.let { put("name", it) }
              put("context", context(report.extra).toJson())
            }

        val (status, body) =
            try {
              transport.post(configuration.endpoint, configuration.headers(), payload.toString())
            } catch (e: IOException) {
              throw SubmitException.Transport(e)
            }

        if (status !in SUCCESS_STATUSES) {
          throw SubmitException.Server(status, body)
        }

        body
      }

  companion object {
    const val VERSION = "1.0.0"

    /** HTTP statuses treated as a successful submission. */
    private val SUCCESS_STATUSES = 200..299

    /**
     * Build a client that reports the host application's own name, version and build automatically.
     */
    fun create(
        context: Context,
        endpoint: String,
        headers: suspend () -> Map<String, String> = { emptyMap() },
        includeLogs: Boolean = true,
    ): ChaosDesk =
        ChaosDesk(
            Configuration(
                endpoint = endpoint,
                headers = headers,
                includeLogs = includeLogs,
                appContext = DeviceContext.app(context.applicationContext),
            ),
        )
  }
}
