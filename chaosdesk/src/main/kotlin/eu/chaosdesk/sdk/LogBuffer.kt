package eu.chaosdesk.sdk

import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A bounded, thread-safe ring buffer of recent log lines.
 *
 * Record whatever your logging already produces and the last entries travel with the next ticket,
 * so a bug report arrives with its trail attached.
 */
class LogBuffer(private val capacity: Int = DEFAULT_CAPACITY) {

  private val mutex = Mutex()
  private val entries = ArrayDeque<TicketContext.LogEntry>()

  suspend fun record(
      message: String,
      level: TicketContext.LogEntry.Level = TicketContext.LogEntry.Level.ERROR,
      at: Instant = Instant.now(),
  ) {
    mutex.withLock {
      entries.addLast(TicketContext.LogEntry(level, message, at.toString()))

      while (entries.size > capacity) {
        entries.removeFirst()
      }
    }
  }

  suspend fun record(throwable: Throwable, at: Instant = Instant.now()) {
    record(throwable.stackTraceToString(), TicketContext.LogEntry.Level.ERROR, at)
  }

  suspend fun snapshot(): List<TicketContext.LogEntry> = mutex.withLock { entries.toList() }

  suspend fun clear() {
    mutex.withLock { entries.clear() }
  }

  companion object {
    const val DEFAULT_CAPACITY = 50

    val shared = LogBuffer()
  }
}
