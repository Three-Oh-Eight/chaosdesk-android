package eu.chaosdesk.sdk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.util.Locale
import java.util.TimeZone

/** Collects what the device and the installed package can tell us. */
object DeviceContext {

  fun current(): TicketContext.Device =
      TicketContext.Device(
          platform = "android",
          osVersion = Build.VERSION.RELEASE,
          model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
          locale = Locale.getDefault().toLanguageTag(),
          timezone = TimeZone.getDefault().id,
      )

  fun app(context: Context, debuggable: Boolean = context.isDebuggable()): TicketContext.App {
    val packageInfo =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()

    return TicketContext.App(
        name = context.applicationInfo.loadLabel(context.packageManager).toString(),
        version = packageInfo?.versionName,
        build =
            packageInfo?.let { info ->
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toString()
              } else {
                @Suppress("DEPRECATION") info.versionCode.toString()
              }
            },
        environment = if (debuggable) "debug" else "production",
    )
  }

  private fun Context.isDebuggable(): Boolean =
      applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
