package eu.chaosdesk.sdk

import org.json.JSONArray
import org.json.JSONObject

/**
 * Diagnostic context sent alongside a support ticket.
 *
 * The shape matches the ChaosDesk context schema, which the Laravel SDK and the Swift client also
 * emit, so a ticket renders identically whichever platform it came from. Every group is optional.
 */
data class TicketContext(
    val source: String = "android",
    val sdk: Sdk = Sdk(),
    val app: App? = null,
    val device: Device? = null,
    val user: User? = null,
    val console: List<LogEntry> = emptyList(),
    val extra: Map<String, Any?>? = null,
) {
  data class Sdk(
      val name: String = "chaosdesk-android",
      val version: String = ChaosDesk.VERSION,
  )

  data class App(
      val name: String? = null,
      val version: String? = null,
      val build: String? = null,
      val environment: String? = null,
  )

  data class Device(
      val platform: String = "android",
      val osVersion: String? = null,
      val model: String? = null,
      val locale: String? = null,
      val timezone: String? = null,
  )

  data class User(
      val externalId: String? = null,
      val name: String? = null,
      val email: String? = null,
      val plan: String? = null,
  )

  /** One captured log line. Messages are capped to the server-side limit. */
  data class LogEntry(
      val level: Level = Level.ERROR,
      val message: String,
      val at: String? = null,
  ) {
    val cappedMessage: String
      get() = message.take(MAX_MESSAGE_LENGTH)

    enum class Level(val wireValue: String) {
      DEBUG("debug"),
      INFO("info"),
      WARNING("warning"),
      ERROR("error"),
    }

    companion object {
      const val MAX_MESSAGE_LENGTH = 2000
    }
  }

  /** Serialise using the snake_case keys the server expects. */
  fun toJson(): JSONObject =
      JSONObject().apply {
        put("source", source)
        put("sdk", JSONObject().put("name", sdk.name).put("version", sdk.version))

        app?.let {
          put(
              "app",
              jsonOf(
                  "name" to it.name,
                  "version" to it.version,
                  "build" to it.build,
                  "environment" to it.environment,
              ),
          )
        }

        device?.let {
          put(
              "device",
              jsonOf(
                  "platform" to it.platform,
                  "os_version" to it.osVersion,
                  "model" to it.model,
                  "locale" to it.locale,
                  "timezone" to it.timezone,
              ),
          )
        }

        user?.let {
          put(
              "user",
              jsonOf(
                  "external_id" to it.externalId,
                  "name" to it.name,
                  "email" to it.email,
                  "plan" to it.plan,
              ),
          )
        }

        if (console.isNotEmpty()) {
          put(
              "console",
              JSONArray().apply {
                console.forEach { entry ->
                  put(
                      jsonOf(
                          "level" to entry.level.wireValue,
                          "message" to entry.cappedMessage,
                          "at" to entry.at,
                      ),
                  )
                }
              },
          )
        }

        extra?.takeIf { it.isNotEmpty() }?.let { put("extra", JSONObject(it)) }
      }

  private fun jsonOf(vararg pairs: Pair<String, String?>): JSONObject =
      JSONObject().apply { pairs.forEach { (key, value) -> if (value != null) put(key, value) } }
}
