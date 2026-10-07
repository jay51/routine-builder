package dev.routinebuilder

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val active: Boolean = true
)

@Entity(tableName = "routine_tasks", indices = [Index(value = ["routineId"])])
data class RoutineTaskEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val templateId: String,
    val title: String,
    val icon: String,
    val category: String,
    val kind: String,
    val instructions: String,
    val durationMinutes: Int,
    val timeOfDay: String,
    val daysOfWeek: String,
    val orderIndex: Int,
    val isDurationTask: Boolean
)

@Entity(tableName = "task_logs", indices = [Index(value = ["completionDate"]), Index(value = ["taskId"])])
data class TaskLogEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val icon: String,
    val category: String,
    val kind: String,
    val instructions: String,
    val targetDurationMinutes: Int,
    val completionDate: String,
    val actualDurationMinutes: Int?,
    val notes: String
)

data class RoutineTaskDraft(
    val templateId: String,
    val title: String,
    val icon: String,
    val category: String,
    val kind: String,
    val instructions: String,
    val durationMinutes: Int,
    val timeOfDay: String,
    val daysOfWeek: Set<DayOfWeek>,
    val isDurationTask: Boolean,
    val existingTaskId: String? = null
)

data class TodayRoutineTask(
    val task: RoutineTaskEntity,
    val routineName: String,
    val log: TaskLogEntity?
)

data class RoutineSnapshot(val routine: RoutineEntity, val tasks: List<RoutineTaskEntity>)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY name COLLATE NOCASE, id")
    fun routines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :routineId LIMIT 1")
    suspend fun routine(routineId: String): RoutineEntity?

    @Query("SELECT * FROM routine_tasks WHERE routineId = :routineId ORDER BY orderIndex, id")
    suspend fun tasksForRoutine(routineId: String): List<RoutineTaskEntity>

    @Transaction
    suspend fun routineSnapshot(routineId: String): RoutineSnapshot? {
        val routine = routine(routineId) ?: return null
        return RoutineSnapshot(routine, tasksForRoutine(routineId))
    }

    @Query("SELECT EXISTS(SELECT 1 FROM routines WHERE active = 1)")
    suspend fun hasActiveRoutine(): Boolean

    @Query("SELECT id FROM routines WHERE active = 1 ORDER BY name, id")
    suspend fun activeRoutineIds(): List<String>

    @Query("UPDATE routines SET active = 0 WHERE active = 1")
    suspend fun deactivateAllRoutines()

    @Query("UPDATE routines SET active = 0 WHERE active = 1 AND id != :routineId")
    suspend fun deactivateOtherRoutines(routineId: String)

    @Query("UPDATE routines SET active = :active WHERE id = :routineId")
    suspend fun updateRoutineActive(routineId: String, active: Boolean)

    @Query("SELECT * FROM routine_tasks ORDER BY orderIndex, title")
    fun routineTasks(): Flow<List<RoutineTaskEntity>>

    @Query("SELECT routine_tasks.* FROM routine_tasks INNER JOIN routines ON routines.id = routine_tasks.routineId WHERE routines.active = 1")
    suspend fun activeRoutineTasks(): List<RoutineTaskEntity>

    @Query("SELECT routine_tasks.* FROM routine_tasks INNER JOIN routines ON routines.id = routine_tasks.routineId WHERE routines.active = 1 AND routine_tasks.id = :taskId LIMIT 1")
    suspend fun activeRoutineTask(taskId: String): RoutineTaskEntity?

    @Query("SELECT * FROM task_logs WHERE completionDate = :date")
    fun taskLogs(date: String): Flow<List<TaskLogEntity>>

    @Query("SELECT * FROM task_logs WHERE completionDate BETWEEN :start AND :end")
    fun taskLogsBetween(start: String, end: String): Flow<List<TaskLogEntity>>

    @Query("SELECT * FROM routine_day_plans WHERE date BETWEEN :start AND :end")
    fun dayPlans(start: String, end: String): Flow<List<RoutineDayPlanEntity>>

    @Query("SELECT * FROM routine_day_plans WHERE date = :date")
    suspend fun dayPlan(date: String): RoutineDayPlanEntity?

    @Query("SELECT * FROM routine_tracking_plan WHERE id = 1")
    suspend fun trackingPlan(): RoutineTrackingPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDayPlan(plan: RoutineDayPlanEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPastDayPlan(plan: RoutineDayPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrackingPlan(plan: RoutineTrackingPlanEntity)

    @Transaction
    suspend fun syncConsistency(date: LocalDate = LocalDate.now()) {
        val previous = trackingPlan()
        if (previous != null) {
            var day = LocalDate.parse(previous.recordedDate)
            while (day.isBefore(date)) {
                insertPastDayPlan(RoutineDayPlanEntity(day.toString(), RoutineConsistency.encodeIds(RoutineConsistency.scheduledIds(previous.tasksJson, day))))
                day = day.plusDays(1)
            }
        }
        val schedule = RoutineConsistency.encodeSchedule(activeRoutineTasks())
        val existing = dayPlan(date.toString())
        val expected = existing?.let { RoutineConsistency.decodeIds(it.taskIdsJson) }.orEmpty() +
            (previous?.takeIf { !LocalDate.parse(it.recordedDate).isAfter(date) }?.let { RoutineConsistency.scheduledIds(it.tasksJson, date) }.orEmpty()) +
            RoutineConsistency.scheduledIds(schedule, date)
        val today = RoutineDayPlanEntity(date.toString(), RoutineConsistency.encodeIds(expected))
        if (existing != today) saveDayPlan(today)
        val next = RoutineTrackingPlanEntity(recordedDate = date.toString(), tasksJson = schedule)
        if (previous != next) saveTrackingPlan(next)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM task_logs WHERE taskId = :taskId AND completionDate = :date)")
    suspend fun hasTaskLog(taskId: String, date: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineTasks(tasks: List<RoutineTaskEntity>)

    @Query("DELETE FROM routine_tasks WHERE routineId = :routineId")
    suspend fun deleteRoutineTasks(routineId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTaskLog(log: TaskLogEntity)

    @Query("DELETE FROM routine_tasks WHERE id = :taskId")
    suspend fun deleteRoutineTask(taskId: String)

    @Transaction
    suspend fun removeTask(taskId: String) {
        syncConsistency()
        deleteRoutineTask(taskId)
        syncConsistency()
    }

    @Transaction
    suspend fun saveRoutine(routine: RoutineEntity, tasks: List<RoutineTaskEntity>) {
        syncConsistency()
        insertRoutine(routine.copy(active = !hasActiveRoutine()))
        insertRoutineTasks(tasks)
        syncConsistency()
    }

    @Transaction
    suspend fun importRoutine(routine: RoutineEntity, tasks: List<RoutineTaskEntity>) {
        insertRoutine(routine.copy(active = false))
        insertRoutineTasks(tasks)
    }

    @Transaction
    suspend fun replaceRoutine(routine: RoutineEntity, tasks: List<RoutineTaskEntity>) {
        val existing = routine(routine.id) ?: return
        syncConsistency()
        insertRoutine(routine.copy(active = existing.active))
        deleteRoutineTasks(routine.id)
        insertRoutineTasks(tasks)
        syncConsistency()
    }

    @Transaction
    suspend fun setRoutineActive(routineId: String, active: Boolean) {
        if (routine(routineId) == null) return
        syncConsistency()
        if (active) deactivateAllRoutines()
        updateRoutineActive(routineId, active)
        syncConsistency()
    }

    @Transaction
    suspend fun ensureSingleActiveRoutine() {
        val activeIds = activeRoutineIds()
        if (activeIds.size > 1) deactivateOtherRoutines(activeIds.first())
        syncConsistency()
    }
}

@Database(
    entities = [RoutineEntity::class, RoutineTaskEntity::class, TaskLogEntity::class, RoutineDayPlanEntity::class, RoutineTrackingPlanEntity::class],
    version = 3,
    exportSchema = false
)
abstract class RoutineDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile private var instance: RoutineDatabase? = null

        fun get(context: Context): RoutineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RoutineDatabase::class.java, "routine-builder-dev.db")
                .addMigrations(object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS routine_day_plans (date TEXT NOT NULL PRIMARY KEY, taskIdsJson TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS routine_tracking_plan (id INTEGER NOT NULL PRIMARY KEY, recordedDate TEXT NOT NULL, tasksJson TEXT NOT NULL)")
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}

class RoutineRepository(private val dao: RoutineDao) {
    val routines = dao.routines()
    val routineTasks = dao.routineTasks()

    fun consistencyWeek(date: LocalDate): Flow<List<ConsistencyDay>> {
        val start = RoutineConsistency.weekStart(date).toString()
        val end = RoutineConsistency.weekStart(date).plusDays(6).toString()
        return combine(dao.dayPlans(start, end), dao.taskLogsBetween(start, end)) { days, logs ->
            RoutineConsistency.week(date, days, logs)
        }.distinctUntilChanged()
    }

    suspend fun syncConsistency() = dao.syncConsistency()

    fun today(date: LocalDate): Flow<List<TodayRoutineTask>> = combine(
        dao.routineTasks(),
        dao.routines(),
        dao.taskLogs(date.toString())
    ) { tasks, routines, logs ->
        val names = routines.filter { it.active }.associate { it.id to it.name }
        val logsByTask = logs.associateBy { it.taskId }
        tasks.mapNotNull { task ->
            val routineName = names[task.routineId] ?: return@mapNotNull null
            if (DayOfWeek.from(date).value !in task.daysOfWeek.toDayNumbers()) return@mapNotNull null
            TodayRoutineTask(task, routineName, logsByTask[task.id])
        }.sortedBy { runCatching { LocalTime.parse(it.task.timeOfDay) }.getOrDefault(LocalTime.MAX) }
    }.distinctUntilChanged()

    suspend fun addRoutine(name: String, drafts: List<RoutineTaskDraft>) {
        val routineId = UUID.randomUUID().toString()
        val routine = routineEntity(routineId, name, drafts.size)
        val tasks = drafts.toEntities(routineId)
        dao.saveRoutine(routine, tasks)
    }

    suspend fun updateRoutine(routineId: String, name: String, drafts: List<RoutineTaskDraft>) {
        val routine = routineEntity(routineId, name, drafts.size)
        dao.replaceRoutine(routine, drafts.toEntities(routineId))
    }

    suspend fun importRoutine(name: String, drafts: List<RoutineTaskDraft>) {
        val id = UUID.randomUUID().toString()
        dao.importRoutine(routineEntity(id, name, drafts.size), drafts.toEntities(id))
    }

    suspend fun exportRoutine(routineId: String): String {
        val snapshot = dao.routineSnapshot(routineId) ?: throw RoutineFileException("This routine no longer exists.")
        return RoutineFiles.encode(snapshot.routine, snapshot.tasks)
    }

    suspend fun removeTask(taskId: String) = dao.removeTask(taskId)

    suspend fun setRoutineActive(routineId: String, active: Boolean) = dao.setRoutineActive(routineId, active)

    suspend fun ensureSingleActiveRoutine() = dao.ensureSingleActiveRoutine()

    suspend fun saveTaskLog(task: RoutineTaskEntity, actualDuration: Int?, notes: String, date: LocalDate = LocalDate.now()) {
        dao.saveTaskLog(
            TaskLogEntity(
                id = "${date}_${task.id}",
                taskId = task.id,
                title = task.title,
                icon = task.icon,
                category = task.category,
                kind = task.kind,
                instructions = task.instructions,
                targetDurationMinutes = task.durationMinutes,
                completionDate = date.toString(),
                actualDurationMinutes = actualDuration,
                notes = notes.trim()
            )
        )
    }
}

private fun routineEntity(id: String, name: String, taskCount: Int) =
    RoutineEntity(id, name.trim(), "$taskCount activities in your routine.")

private fun List<RoutineTaskDraft>.toEntities(routineId: String) = mapIndexed { index, draft ->
    RoutineTaskEntity(
        id = draft.existingTaskId ?: UUID.randomUUID().toString(),
        routineId = routineId,
        templateId = draft.templateId,
        title = draft.title,
        icon = draft.icon,
        category = draft.category,
        kind = draft.kind,
        instructions = draft.instructions,
        durationMinutes = draft.durationMinutes,
        timeOfDay = draft.timeOfDay,
        daysOfWeek = draft.daysOfWeek.sortedBy { it.value }.joinToString(",") { it.value.toString() },
        orderIndex = index,
        isDurationTask = draft.isDurationTask
    )
}

private fun String.toDayNumbers(): Set<Int> = split(',').mapNotNull(String::toIntOrNull).toSet()
