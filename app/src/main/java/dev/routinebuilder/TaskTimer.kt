package dev.routinebuilder

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

internal fun supportsTaskTimer(kind: String, isDurationTask: Boolean): Boolean = isDurationTask && kind in setOf(
    "run", "walk", "strength", "stretch", "yoga", "study", "read", "review", "language",
    "meditate", "journal", "breathe", "outside", "tidy"
)

internal data class TaskTimerSession(
    val date: LocalDate,
    val limitMinutes: Int,
    val remainingMillis: Long,
    val deadlineMillis: Long? = null,
    val started: Boolean = false
) {
    val running: Boolean get() = deadlineMillis != null
    val finished: Boolean get() = started && remainingMillis == 0L
}

/** Screen-independent sessions survive navigation/rotation; elapsed time includes screen-off time. */
internal class TaskTimers(private val scope: CoroutineScope) {
    private val sessions = MutableStateFlow<Map<String, TaskTimerSession>>(emptyMap())
    val state = sessions.asStateFlow()
    private var ticker: Job? = null

    fun start(task: RoutineTaskEntity) {
        if (!task.timerEnabled || !supportsTaskTimer(task.kind, task.isDurationTask)) return
        val date = LocalDate.now()
        val existing = sessions.value[task.id]?.takeIf { it.date == date && it.limitMinutes == task.timerMinutes }
        if (existing?.running == true || existing?.finished == true) return
        val remaining = existing?.remainingMillis ?: task.timerMinutes * 60_000L
        sessions.value = sessions.value + (task.id to TaskTimerSession(
            date, task.timerMinutes, remaining, SystemClock.elapsedRealtime() + remaining, started = true
        ))
        if (ticker?.isActive != true) ticker = scope.launch {
            while (sessions.value.values.any { it.running }) {
                tick()
                delay(250)
            }
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        sessions.value = sessions.value.filterValues { it.date == LocalDate.now() }.mapValues { (_, session) ->
            val deadline = session.deadlineMillis ?: return@mapValues session
            val remaining = (deadline - now).coerceAtLeast(0L)
            session.copy(remainingMillis = remaining, deadlineMillis = deadline.takeIf { remaining > 0 })
        }
    }

    fun pause(taskId: String) {
        tick()
        val session = sessions.value[taskId] ?: return
        sessions.value = sessions.value + (taskId to session.copy(deadlineMillis = null))
    }

    fun reset(taskId: String) { sessions.value = sessions.value - taskId }

    fun retain(tasks: List<TodayRoutineTask>) {
        val eligible = tasks.filter { it.log == null && it.task.timerEnabled && supportsTaskTimer(it.task.kind, it.task.isDurationTask) }
            .associateBy { it.task.id }
        sessions.value = sessions.value.filter { (id, session) ->
            session.date == LocalDate.now() && eligible[id]?.task?.timerMinutes == session.limitMinutes
        }
    }
}

@Composable
internal fun TaskTimerCard(task: RoutineTaskEntity, session: TaskTimerSession?, onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit) {
    val remaining = session?.remainingMillis ?: task.timerMinutes * 60_000L
    val seconds = (remaining + 999L) / 1_000L
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .08f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Activity timer · ${task.timerMinutes} min", fontWeight = FontWeight.SemiBold)
            Text(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(when {
                session?.finished == true -> "Time's up! Mark complete when you're ready."
                session?.running == true -> "Timer running"
                session?.started == true -> "Paused"
                else -> "Start when you're ready."
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session?.finished != true) Button(onClick = if (session?.running == true) onPause else onStart) {
                    Text(if (session?.running == true) "Pause" else if (session?.started == true) "Resume" else "Start timer")
                }
                if (session?.started == true) OutlinedButton(onClick = onReset) { Text("Reset") }
            }
        }
    }
}
