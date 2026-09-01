package eu.chaosdesk.sdk

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Performs the HTTP request, so tests can substitute a stub. */
interface Transport {
  /** @return the response status paired with its body. */
  @Throws(IOException::class)
  suspend fun post(url: String, headers: Map<String, String>, json: String): Pair<Int, String>
}

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : Transport {

  override suspend fun post(
      url: String,
      headers: Map<String, String>,
      json: String,
  ): Pair<Int, String> {
    val request =
        Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON))
            .apply {
              header("Content-Type", "application/json")
              header("Accept", "application/json")
              headers.forEach { (name, value) -> header(name, value) }
            }
            .build()

    client.newCall(request).execute().use { response ->
      return response.code to (response.body?.string() ?: "")
    }
  }

  private companion object {
    val JSON = "application/json; charset=utf-8".toMediaType()
  }
}
