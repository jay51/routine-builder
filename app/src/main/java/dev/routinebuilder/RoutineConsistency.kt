package dev.routinebuilder

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.json.*
import java.time.DayOfWeek
import java.time.LocalDate

@Entity(tableName = "routine_day_plans")
data class RoutineDayPlanEntity(@PrimaryKey val date: String, val taskIdsJson: String)

@Entity(tableName = "routine_tracking_plan")
data class RoutineTrackingPlanEntity(@PrimaryKey val id: Int = 1, val recordedDate: String, val tasksJson: String)

enum class ConsistencyStatus { COMPLETE, INCOMPLETE, IN_PROGRESS, UPCOMING, REST, UNTRACKED }

data class ConsistencyDay(val date: LocalDate, val completed: Int, val total: Int, val status: ConsistencyStatus)

internal object RoutineConsistency {
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    fun encodeIds(ids: Set<String>): String = JsonArray(ids.sorted().map(::JsonPrimitive)).toString()
    fun decodeIds(json: String): Set<String> = Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.content }.toSet()

    fun encodeSchedule(tasks: List<RoutineTaskEntity>): String = buildJsonObject {
        tasks.sortedBy { it.id }.forEach { task -> put(task.id, task.daysOfWeek) }
    }.toString()

    fun scheduledIds(json: String, date: LocalDate): Set<String> = Json.parseToJsonElement(json).jsonObject
        .filterValues { date.dayOfWeek.value in it.jsonPrimitive.content.split(',').mapNotNull(String::toIntOrNull) }.keys

    fun week(date: LocalDate, days: List<RoutineDayPlanEntity>, logs: List<TaskLogEntity>): List<ConsistencyDay> {
        val plans = days.associateBy { it.date }
        val completedByDate = logs.groupBy { it.completionDate }.mapValues { (_, entries) -> entries.map { it.taskId }.toSet() }
        return (0L..6L).map { offset ->
            val day = weekStart(date).plusDays(offset)
            val plan = plans[day.toString()]
            val expected = plan?.let { decodeIds(it.taskIdsJson) }.orEmpty()
            val completed = expected.count { it in completedByDate[day.toString()].orEmpty() }
            val status = when {
                day.isAfter(date) -> ConsistencyStatus.UPCOMING
                plan == null -> ConsistencyStatus.UNTRACKED
                expected.isEmpty() -> ConsistencyStatus.REST
                completed == expected.size -> ConsistencyStatus.COMPLETE
                day == date -> ConsistencyStatus.IN_PROGRESS
                else -> ConsistencyStatus.INCOMPLETE
            }
            ConsistencyDay(day, completed, expected.size, status)
        }
    }
}
