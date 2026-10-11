package dev.routinebuilder

import java.time.DayOfWeek
import java.time.LocalTime

internal enum class DurationUnit { MINUTES, HOURS }

/** Capabilities are based on the saved task definition, including imported snapshots. */
internal data class TaskConfigurationCapabilities(val durationUnit: DurationUnit?, val activityTimer: Boolean, val checklist: Boolean = true)

private val timedTaskKinds = setOf(
    "run", "walk", "strength", "stretch", "yoga", "study", "read", "review", "language",
    "meditate", "journal", "breathe", "outside", "tidy"
)

internal fun taskConfigurationCapabilities(kind: String, isDurationTask: Boolean) = TaskConfigurationCapabilities(
    durationUnit = when {
        !isDurationTask -> null
        kind == "sleep" -> DurationUnit.HOURS
        else -> DurationUnit.MINUTES
    },
    activityTimer = isDurationTask && kind in timedTaskKinds
)

/** Raw inputs remain editable; only validated values are converted into a routine draft. */
internal data class TaskConfigurationState(
    val source: RoutineTaskDraft,
    val targetInput: String,
    val days: Set<DayOfWeek>,
    val time: LocalTime,
    val timerEnabled: Boolean,
    val timerInput: String,
    val checklistEnabled: Boolean,
    val checklistItems: List<ChecklistItem>
) {
    val capabilities get() = taskConfigurationCapabilities(source.kind, source.isDurationTask)
    val targetError: String? get() = if (capabilities.durationUnit != null && parseDurationInput(source.kind, targetInput) == null) {
        if (capabilities.durationUnit == DurationUnit.HOURS) "Enter a valid sleep target, up to 16.65 hours."
        else "Enter a valid duration."
    } else null
    val daysError: String? get() = if (days.isEmpty()) "Choose at least one day." else null
    val timerError: String? get() = if (capabilities.activityTimer && timerEnabled && timerInput.toIntOrNull()?.let { it in 1..999 } != true)
        "Enter a valid timer limit." else null
    val checklistError: String? get() = if (checklistEnabled) checklistError(true, checklistItems) else null
    val canSave get() = targetError == null && daysError == null && timerError == null && checklistError == null

    fun toDraft(): RoutineTaskDraft? {
        if (!canSave) return null
        val duration = if (source.isDurationTask) {
            parseDurationInput(source.kind, targetInput) ?: return null
        } else source.durationMinutes
        return source.copy(
            durationMinutes = duration,
            daysOfWeek = days,
            timeOfDay = time.toString(),
            timerEnabled = capabilities.activityTimer && timerEnabled,
            timerMinutes = timerInput.toIntOrNull()?.takeIf { it in 1..999 } ?: source.timerMinutes,
            checklistEnabled = checklistEnabled,
            checklistItems = checklistItems.filter { it.title.isNotBlank() }.map { it.copy(title = it.title.trim()) }
        )
    }

    companion object {
        fun from(draft: RoutineTaskDraft) = TaskConfigurationState(
            source = draft,
            targetInput = durationInput(draft.kind, draft.durationMinutes),
            days = draft.daysOfWeek,
            time = runCatching { LocalTime.parse(draft.timeOfDay) }.getOrDefault(LocalTime.of(9, 0)),
            timerEnabled = draft.timerEnabled && taskConfigurationCapabilities(draft.kind, draft.isDurationTask).activityTimer,
            timerInput = draft.timerMinutes.toString(),
            checklistEnabled = draft.checklistEnabled,
            checklistItems = draft.checklistItems
        )
    }
}

internal fun PredefinedTask.newRoutineDraft() = RoutineTaskDraft(
    templateId = id, title = title, icon = icon, category = category, kind = kind,
    instructions = instructions, durationMinutes = defaultDuration,
    timeOfDay = if (kind == "sleep") "22:00" else "09:00",
    daysOfWeek = defaultDays, isDurationTask = isDurationTask,
    timerMinutes = defaultDuration.coerceIn(1, 999)
)

internal fun RoutineTaskEntity.editableDraft() = RoutineTaskDraft(
    templateId = templateId, title = title, icon = icon, category = category, kind = kind,
    instructions = instructions, durationMinutes = durationMinutes, timeOfDay = timeOfDay,
    daysOfWeek = daysOfWeek.split(',').mapNotNull(String::toIntOrNull)
        .mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet(),
    isDurationTask = isDurationTask, existingTaskId = id, timerEnabled = timerEnabled, timerMinutes = timerMinutes,
    checklistEnabled = checklistEnabled, checklistItems = checklistItems
)
