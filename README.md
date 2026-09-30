# ChaosDesk for Android

Kotlin client for reporting support tickets from Android apps.

## What it does, and what it deliberately does not

The library never talks to ChaosDesk. Your app authenticates against **your own** backend, exactly as it already does; that backend forwards the ticket using its server-side ingest token.

```
your app  --(your own auth)-->  your backend  --(X-Site-Token)-->  ChaosDesk
```

No ChaosDesk credential is ever shipped inside the APK. What the library does is collect everything needed to solve the problem: app version and build, OS version, device model, locale, timezone, recent log lines, and optionally a screenshot.

## Requirements

- `minSdk` 31, `compileSdk` 36
- Kotlin 2.1, coroutines

## Installation

The library is published via [JitPack](https://jitpack.io/#Three-Oh-Eight/chaosdesk-android). Add the JitPack repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the dependency to your app module:

```kotlin
dependencies {
    implementation("com.github.Three-Oh-Eight:chaosdesk-android:1.0.0")
}
```

## Configure

Point it at the endpoint the Laravel SDK registers on your backend (`ChaosDesk::routes()`), and supply your own authentication.

```kotlin
val chaosDesk = ChaosDesk.create(
    context = applicationContext,
    endpoint = "https://dialed.at/api/chaosdesk/tickets",
    headers = { mapOf("Authorization" to "Bearer ${tokenStore.accessToken()}") },
)
```

`headers` is a suspend lambda called per request, so a rotating access token stays fresh. `create` reads your app name, version and build from the package automatically.

```kotlin
chaosDesk.identify(
    TicketContext.User(externalId = user.id.toString(), plan = user.plan)
)
```

Call `identify(null)` on sign-out.

## Submit

```kotlin
viewModelScope.launch {
    try {
        chaosDesk.submit(
            ChaosDesk.Report(
                subject = "Fork sag not saving",
                message = "It reverts every time I hit save.",
                extra = mapOf("setup_id" to 12),
            )
        )
    } catch (e: ChaosDesk.SubmitException) {
        // Server carries the status and body; Transport means the network failed.
    }
}
```

`submit` is main-safe: it moves to the injected IO dispatcher itself.

## Logs

```kotlin
LogBuffer.shared.record(throwable)
LogBuffer.shared.record("Sync failed for setup 12", TicketContext.LogEntry.Level.WARNING)
```

The last 50 entries travel with the next ticket. Turn it off with `includeLogs = false`.

## Screenshots

```kotlin
val png: ByteArray = Screenshot.capture(activity)
```

Uses `PixelCopy`, so no permission prompt is involved. Upload the bytes to your backend alongside the ticket.

## Context emitted

Matches the ChaosDesk context schema, so tickets from web, iOS and Android render identically for agents:

| Group | Contents |
| --- | --- |
| `sdk` | name, version |
| `app` | name, version, build, environment |
| `device` | platform, os_version, model, locale, timezone |
| `user` | external_id, name, email, plan |
| `console` | recent log entries |
| `extra` | anything you attach |

Inspect what would be sent without sending it:

```kotlin
val context = chaosDesk.context()
```

## Testing

```bash
./gradlew :chaosdesk:testDebugUnitTest
./gradlew :chaosdesk:lintDebug
```

Substitute the network by implementing `Transport`, and inject a test dispatcher via the `ChaosDesk` constructor.

## License

MIT.
