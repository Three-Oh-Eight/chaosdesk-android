package eu.chaosdesk.sdk

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.view.PixelCopy
import android.view.Window
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Captures the current screen as PNG bytes.
 *
 * Unlike the web, no permission prompt is involved: the app is rendering the pixels already.
 * Capture still only happens when the user asks for it.
 */
object Screenshot {

  /** PNG is lossless, so the quality argument is ignored; 100 is the convention. */
  private const val PNG_QUALITY = 100

  class CaptureException(message: String) : Exception(message)

  suspend fun capture(activity: Activity): ByteArray {
    val window = activity.window ?: throw CaptureException("The activity has no window.")
    val view = window.decorView

    if (view.width <= 0 || view.height <= 0) {
      throw CaptureException("The window has not been laid out yet.")
    }

    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

    copyPixels(window, bitmap)

    return ByteArrayOutputStream().use { stream ->
      bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
      bitmap.recycle()
      stream.toByteArray()
    }
  }

  private suspend fun copyPixels(window: Window, bitmap: Bitmap) =
      suspendCancellableCoroutine { continuation ->
        val listener =
            PixelCopy.OnPixelCopyFinishedListener { result ->
              if (result == PixelCopy.SUCCESS) {
                continuation.resume(Unit)
              } else {
                continuation.resumeWithException(
                    CaptureException("PixelCopy failed with result $result."),
                )
              }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          val request =
              PixelCopy.Request.Builder.ofWindow(window).setDestinationBitmap(bitmap).build()

          PixelCopy.request(request, window.context.mainExecutor) { result ->
            listener.onPixelCopyFinished(result.status)
          }
        } else {
          @Suppress("DEPRECATION")
          PixelCopy.request(window, bitmap, listener, window.decorView.handler)
        }
      }
}
